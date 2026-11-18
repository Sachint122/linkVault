package com.linkvault.app.sidebar;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.linkvault.app.App;
import com.linkvault.app.R;
import com.linkvault.app.ai.AiContract;
import com.linkvault.app.ai.SidebarSettings;
import com.linkvault.app.ai.WritingAction;
import com.linkvault.app.ai.WritingHistory;
import com.linkvault.app.ai.WritingPrompt;
import com.linkvault.app.ai.WritingTone;
import com.linkvault.app.ai.WritingValidator;
import com.linkvault.app.di.AppContainer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * The floating AI Sidebar overlay (spec §2–§8).
 *
 * ONE window, two states:
 * - collapsed: WRAP_CONTENT pill (NOT_FOCUSABLE) — tap to expand, drag to move,
 *   edge + vertical fraction persisted in {@link SidebarSettings};
 * - expanded: MATCH_PARENT — transparent root, dim layer, panel at 87% width
 *   with input, tones, actions, result, history and settings sub-views.
 *
 * Generation always goes through the shared {@code AppContainer.inferenceQueue()}
 * (spec §6): FIFO with the link-summarizer, exactly-once callbacks marshalled
 * to this thread via {@code runSeq} staleness guards. LiteRT-LM has no cancel
 * API — cancelling mid-run discards the output (documented in InferenceQueue).
 *
 * Clipboard (spec §9): read ONLY on expand when the autofill toggle is on
 * (one-shot try/catch — Android 10+ focus rules can throw) or via the explicit
 * Paste button. Never monitored.
 */
public class SidebarOverlay {

    private static final float PANEL_WIDTH_FRACTION = 0.90f;
    private static final long ANIM_MS = 80L;

    private static final int VIEW_MAIN = 0;
    private static final int VIEW_HISTORY = 1;
    private static final int VIEW_SETTINGS = 2;

    private final SidebarService service;
    private final Context themed;
    private final AppContainer container;
    private final WindowManager windowManager;
    private final SidebarSettings settings;
    private final WritingHistory history;
    private final LayoutInflater inflater;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final float density;
    private final int screenW;
    private final int screenH;
    private final int statusBarH;
    private int navBarH;
    private final int touchSlop;

    // Views
    private View root;
    private View dim;
    private View panel;
    private View pill;
    private View dot;
    private View mainView;
    private View historyView;
    private View settingsView;
    private ScrollView scroll;
    private View resultCard;
    private EditText input;
    private EditText customInput;
    private TextView errorText;
    private TextView resultText;
    private TextView historyEmpty;
    private ChipGroup toneGroup;
    private ChipGroup resultToneGroup;
    private final Chip[] mainChips = new Chip[WritingTone.values().length];
    private final Chip[] resultChips = new Chip[WritingTone.values().length];
    private ViewGroup progressRow;
    private ViewGroup historyList;
    private View customToggle;
    private View customChevron;
    private View regenBtn;
    private View runActionBtn;
    private TextView ctaText;
    private TextView charCountText;
    private TextView badgeStatusText;
    private final View[] actionButtons = new View[WritingAction.values().length];
    private final ImageView[] actionIcons = new ImageView[WritingAction.values().length];
    private final TextView[] actionTexts = new TextView[WritingAction.values().length];
    private MaterialSwitch enableSwitch;
    private MaterialSwitch autoCollapseSwitch;
    private MaterialSwitch clipAutofillSwitch;
    private MaterialSwitch backgroundSwitch;
    private MaterialSwitch hapticSwitch;

    // State
    private WindowManager.LayoutParams rootParams;
    private boolean attached;
    private boolean expanded;
    private boolean destroyed;
    private boolean generating;
    private boolean dragging;
    private boolean syncingChips;
    private boolean renderingSettings;
    private boolean displayedIsHistoryEntry;
    private long runSeq;
    private long activeRequestId;
    private float downRawX;
    private float downRawY;
    private WritingTone selectedTone = WritingTone.DEFAULT;
    private WritingAction lastAction = WritingAction.REWRITE;
    private String activeCustom = "";

    public SidebarOverlay(SidebarService service) {
        this.service = service;
        this.container = ((App) service.getApplication()).getContainer();
        this.windowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        this.settings = new SidebarSettings(service);
        this.history = new WritingHistory(service);
        // Service contexts on some OEM builds don't inherit the application
        // Material theme → Material widgets (Button/Chip/Switch) throw
        // "requires Theme.MaterialComponents". Force the theme explicitly.
        this.themed = new ContextThemeWrapper(service, R.style.Theme_LinkVault);
        this.inflater = LayoutInflater.from(themed);
        this.density = service.getResources().getDisplayMetrics().density;
        this.screenW = service.getResources().getDisplayMetrics().widthPixels;
        this.screenH = service.getResources().getDisplayMetrics().heightPixels;
        this.statusBarH = statusBarHeight();
        this.navBarH = navBarHeight();
        this.touchSlop = ViewConfiguration.get(service).getScaledTouchSlop();
    }

    // ------------------------------------------------------------- lifecycle

    /** Inflates + attaches the overlay window. False = no permission/token. */
    public boolean attach() {
        if (destroyed) return false;
        if (attached) return true;
        if (!Settings.canDrawOverlays(service)) return false;

        root = inflater.inflate(R.layout.sidebar_overlay, null, false);
        dim = root.findViewById(R.id.sidebarDim);
        panel = root.findViewById(R.id.sidebarPanel);
        pill = root.findViewById(R.id.sidebarPill);
        dot = root.findViewById(R.id.sidebarDot);
        mainView = root.findViewById(R.id.sidebarMainView);
        historyView = root.findViewById(R.id.sidebarHistoryView);
        settingsView = root.findViewById(R.id.sidebarSettingsView);
        scroll = root.findViewById(R.id.sidebarScroll);
        resultCard = root.findViewById(R.id.sidebarResultCard);
        input = root.findViewById(R.id.sidebarInput);
        customInput = root.findViewById(R.id.sidebarCustom);
        errorText = root.findViewById(R.id.sidebarError);
        resultText = root.findViewById(R.id.sidebarResultText);
        historyEmpty = root.findViewById(R.id.historyEmpty);
        toneGroup = root.findViewById(R.id.sidebarToneGroup);
        resultToneGroup = root.findViewById(R.id.sidebarResultToneGroup);
        progressRow = root.findViewById(R.id.sidebarProgressRow);
        historyList = root.findViewById(R.id.historyList);
        customToggle = root.findViewById(R.id.btnSidebarCustomToggle);
        customChevron = root.findViewById(R.id.customChevron);
        regenBtn = root.findViewById(R.id.btnSidebarRegen);
        runActionBtn = root.findViewById(R.id.btnSidebarRunAction);
        ctaText = root.findViewById(R.id.textSidebarCta);
        charCountText = root.findViewById(R.id.sidebarCharCount);
        badgeStatusText = root.findViewById(R.id.textResultBadge);

        actionButtons[WritingAction.REWRITE.ordinal()] = root.findViewById(R.id.btnActionRewrite);
        actionButtons[WritingAction.IMPROVE.ordinal()] = root.findViewById(R.id.btnActionImprove);
        actionButtons[WritingAction.SHORTEN.ordinal()] = root.findViewById(R.id.btnActionShorten);
        actionButtons[WritingAction.EXPAND.ordinal()] = root.findViewById(R.id.btnActionExpand);
        actionButtons[WritingAction.TRANSLATE.ordinal()] = root.findViewById(R.id.btnActionTranslate);

        actionIcons[WritingAction.REWRITE.ordinal()] = root.findViewById(R.id.iconActionRewrite);
        actionIcons[WritingAction.IMPROVE.ordinal()] = root.findViewById(R.id.iconActionImprove);
        actionIcons[WritingAction.SHORTEN.ordinal()] = root.findViewById(R.id.iconActionShorten);
        actionIcons[WritingAction.EXPAND.ordinal()] = root.findViewById(R.id.iconActionExpand);
        actionIcons[WritingAction.TRANSLATE.ordinal()] = root.findViewById(R.id.iconActionTranslate);

        actionTexts[WritingAction.REWRITE.ordinal()] = root.findViewById(R.id.textActionRewrite);
        actionTexts[WritingAction.IMPROVE.ordinal()] = root.findViewById(R.id.textActionImprove);
        actionTexts[WritingAction.SHORTEN.ordinal()] = root.findViewById(R.id.textActionShorten);
        actionTexts[WritingAction.EXPAND.ordinal()] = root.findViewById(R.id.textActionExpand);
        actionTexts[WritingAction.TRANSLATE.ordinal()] = root.findViewById(R.id.textActionTranslate);

        enableSwitch = root.findViewById(R.id.sidebarEnableSwitch);
        autoCollapseSwitch = root.findViewById(R.id.sidebarAutoCollapseSwitch);
        clipAutofillSwitch = root.findViewById(R.id.sidebarClipAutofillSwitch);
        backgroundSwitch = root.findViewById(R.id.sidebarBackgroundSwitch);
        hapticSwitch = root.findViewById(R.id.sidebarHapticSwitch);

        buildChips();
        wire();

        // Panel box: 90% width, top-anchored with bottom clearance for nav bar.
        FrameLayout.LayoutParams plp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        plp.width = (int) (screenW * PANEL_WIDTH_FRACTION);
        int initBottomClearance = Math.max(navBarH, dp(32)) + dp(16);
        plp.topMargin = statusBarH + dp(8);
        plp.bottomMargin = initBottomClearance;
        plp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        plp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        panel.setLayoutParams(plp);

        // Collapsed: dim GONE so the WRAP_CONTENT window hugs the pill only
        // (a visible match_parent dim would force a near-fullscreen window,
        // clamping drag range and breaking edge snap).
        dim.setVisibility(View.GONE);
        rootParams = pillParams();
        try {
            windowManager.addView(root, rootParams);
        } catch (RuntimeException e) {
            root = null;
            return false;
        }
        attached = true;
        // Spec §7: a request interrupted by process death is cleared on start.
        settings.clearPendingRequest();
        applyPillPosition();
        updateGestureExclusion();
        updateDot();
        return true;
    }

    /** Tears the window down; cancels any request this overlay owns. */
    public void destroy() {
        if (destroyed) return;
        destroyed = true;           // first: invalidates all pending callbacks
        runSeq++;
        if (activeRequestId != 0) {
            try {
                container.inferenceQueue().cancel(activeRequestId);
            } catch (RuntimeException ignored) {
                // queue already torn down with the process — nothing to cancel
            }
            activeRequestId = 0;
        }
        generating = false;
        if (attached) {
            try {
                windowManager.removeViewImmediate(root);
            } catch (RuntimeException ignored) {
                // window already gone
            }
            attached = false;
        }
        expanded = false;
    }

    // ---------------------------------------------------------- window params

    private WindowManager.LayoutParams pillParams() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.windowAnimations = 0;
        p.setTitle(AiContract.SIDEBAR_WINDOW_TAG);
        return p;
    }

    private WindowManager.LayoutParams fullParams() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = 0;
        p.y = 0;
        p.setTitle(AiContract.SIDEBAR_WINDOW_TAG);
        return p;
    }

    private void applyPillPosition() {
        if (!attached || expanded || rootParams == null) return;
        int pw = pill.getWidth() > 0 ? pill.getWidth() : dp(10);
        int ph = pill.getHeight() > 0 ? pill.getHeight() : dp(88);
        boolean left = SidebarSettings.EDGE_LEFT.equals(settings.getPillEdge());
        int nx = left ? 0 : Math.max(0, screenW - pw);
        float yFrac = Math.max(0f, Math.min(1f, settings.getPillYFraction()));
        int ny = (int) ((screenH - ph) * yFrac);
        rootParams.gravity = Gravity.TOP | Gravity.START;
        if (rootParams.x == nx && rootParams.y == ny) return;
        rootParams.x = nx;
        rootParams.y = ny;
        windowManager.updateViewLayout(root, rootParams);
        updateGestureExclusion();
    }

    // ------------------------------------------------------------ expand / collapse

    public void expand() {
        if (destroyed || expanded) return;
        if (!attached && !attach()) {
            toast("Overlay permission nahi mili — Settings se dein.");
            return;
        }
        expanded = true;
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                if (pill != null) pill.setSystemGestureExclusionRects(Collections.emptyList());
                if (root != null) root.setSystemGestureExclusionRects(Collections.emptyList());
            } catch (Exception ignored) {}
        }
        if (settings.isUnviewed()) {
            settings.setUnviewed(false);
            updateDot();
        }
        rootParams = fullParams();
        windowManager.updateViewLayout(root, rootParams);

        showContent(VIEW_MAIN);
        restoreCurrentResult();
        applyImeHeight(0);
        root.requestApplyInsets();

        dim.setVisibility(View.VISIBLE);
        dim.setClickable(true);
        panel.setVisibility(View.VISIBLE);
        pill.setVisibility(View.GONE);
        float sign = SidebarSettings.EDGE_LEFT.equals(settings.getPillEdge()) ? -1f : 1f;
        panel.setTranslationX(sign * screenW * PANEL_WIDTH_FRACTION);
        panel.setAlpha(0.7f);
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(ANIM_MS).start();
        panel.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(ANIM_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        // Clipboard autofill: post so the window already has focus (Android 10+).
        root.post(() -> {
            if (expanded && !destroyed) tryClipboardAutofill();
        });
        haptic(12);
    }

    public void collapse() {
        if (destroyed || !expanded) return;
        expanded = false;
        hideKeyboard();
        if (!settings.isBackgroundProcessing()) cancelGeneration();
        float sign = SidebarSettings.EDGE_LEFT.equals(settings.getPillEdge()) ? -1f : 1f;
        dim.animate().alpha(0f).setDuration(ANIM_MS).start();
        panel.animate()
                .translationX(sign * screenW * PANEL_WIDTH_FRACTION)
                .alpha(0.7f)
                .setDuration(ANIM_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (destroyed || expanded || !attached) return;
                    panel.setVisibility(View.GONE);
                    dim.setClickable(false);
                    dim.setVisibility(View.GONE);
                    pill.setVisibility(View.VISIBLE);
                    rootParams = pillParams();
                    applyPillPosition();
                    windowManager.updateViewLayout(root, rootParams);
                    updateGestureExclusion();
                })
                .start();
        updateDot();
        haptic(10);
    }

    // ------------------------------------------------------------------ wiring

    private void wire() {
        // Pill: slide inward to open (mis-touch safe), drag vertically to reposition.
        pill.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.performClick();
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    dragging = false;
                    return true;
                case android.view.MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    boolean isLeft = SidebarSettings.EDGE_LEFT.equals(settings.getPillEdge());

                    if (!dragging) {
                        // Check for inward horizontal slide gesture
                        boolean inward = isLeft ? (dx > dp(16)) : (dx < -dp(16));
                        if (inward && Math.abs(dx) > Math.abs(dy) * 0.6f) {
                            expand();
                            return true;
                        }
                        // Vertical drag to reposition pill along edge
                        if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                            dragging = true;
                            haptic(8);
                        }
                    }

                    if (dragging && rootParams != null) {
                        int pw = pill.getWidth() > 0 ? pill.getWidth() : dp(10);
                        int ph = pill.getHeight() > 0 ? pill.getHeight() : dp(88);
                        int nx = clamp((int) (event.getRawX() - pw / 2f), 0, screenW - pw);
                        int ny = clamp((int) (event.getRawY() - ph / 2f),
                                statusBarH, Math.max(statusBarH, screenH - ph - Math.max(navBarH, dp(24))));
                        rootParams.gravity = Gravity.TOP | Gravity.START;
                        rootParams.x = nx;
                        rootParams.y = ny;
                        windowManager.updateViewLayout(root, rootParams);
                    }
                    return true;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL: {
                    boolean wasDragging = dragging;
                    dragging = false;
                    if (wasDragging && rootParams != null) {
                        int pw = pill.getWidth() > 0 ? pill.getWidth() : dp(10);
                        int ph = pill.getHeight() > 0 ? pill.getHeight() : dp(88);
                        int center = rootParams.x + pw / 2;
                        String edge = center < screenW / 2
                                ? SidebarSettings.EDGE_LEFT : SidebarSettings.EDGE_RIGHT;
                        float yFrac = screenH > ph ? rootParams.y / (float) (screenH - ph) : 0f;
                        settings.setPillPosition(edge, yFrac);
                        applyPillPosition();
                        updateGestureExclusion();
                        haptic(8);
                    } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                        // Quick flick inward gesture check
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        boolean isLeft = SidebarSettings.EDGE_LEFT.equals(settings.getPillEdge());
                        boolean inward = isLeft ? (dx > dp(16)) : (dx < -dp(16));
                        if (inward && Math.abs(dx) > Math.abs(dy) * 0.6f) {
                            expand();
                        }
                        // Tapping without inward slide is safely ignored (prevents accidental mis-touch).
                    }
                    return true;
                }
                default:
                    return false;
            }
        });

        dim.setOnClickListener(v -> {
            if (expanded) collapse();
        });

        root.findViewById(R.id.btnSidebarClose).setOnClickListener(v -> collapse());
        root.findViewById(R.id.btnSidebarHistory).setOnClickListener(v -> {
            renderHistory();
            showContent(VIEW_HISTORY);
        });
        root.findViewById(R.id.btnSidebarSettings).setOnClickListener(v -> {
            renderSettings();
            showContent(VIEW_SETTINGS);
        });
        root.findViewById(R.id.btnHistoryBack).setOnClickListener(v -> showContent(VIEW_MAIN));
        root.findViewById(R.id.btnSettingsBack).setOnClickListener(v -> showContent(VIEW_MAIN));

        root.findViewById(R.id.btnSidebarPaste).setOnClickListener(v -> {
            String clip = readClipboard();
            if (clip == null || clip.trim().isEmpty()) {
                toast("Clipboard khali hai.");
                return;
            }
            input.setText(clip);
            input.setSelection(input.getText() == null ? 0 : input.getText().length());
        });

        if (customToggle != null) {
            customToggle.setOnClickListener(v -> {
                boolean show = customInput.getVisibility() != View.VISIBLE;
                customInput.setVisibility(show ? View.VISIBLE : View.GONE);
                if (customChevron != null) {
                    customChevron.setRotation(show ? 180f : 0f);
                }
            });
        }

        // Input character counter
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                int len = s == null ? 0 : s.length();
                if (charCountText != null) charCountText.setText(len + " / 500");
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        for (WritingAction a : WritingAction.values()) {
            View b = actionButtons[a.ordinal()];
            final WritingAction action = a;
            if (b != null) {
                b.setOnClickListener(v -> {
                    selectAction(action);
                    if (!generating) runAction(action);
                });
            }
        }
        selectAction(WritingAction.REWRITE);

        if (runActionBtn != null) {
            runActionBtn.setOnClickListener(v -> {
                if (!generating) runAction(lastAction);
            });
        }

        root.findViewById(R.id.btnSidebarCancel).setOnClickListener(v -> cancelGeneration());

        root.findViewById(R.id.btnSidebarCopy).setOnClickListener(v -> copyResult());
        if (regenBtn != null) {
            regenBtn.setOnClickListener(v -> {
                if (generating) return;
                if (resultText.getText() == null || resultText.getText().length() == 0) return;
                runAction(lastAction);
            });
        }
        View resultClear = root.findViewById(R.id.btnSidebarResultClear);
        if (resultClear != null) {
            resultClear.setOnClickListener(v -> {
                resultText.setText("");
                settings.setCurrentResult(null);
            });
        }
        root.findViewById(R.id.btnSidebarClearInput).setOnClickListener(v -> {
            input.setText("");
            customInput.setText("");
            hideError();
        });

        root.findViewById(R.id.btnHistoryClear).setOnClickListener(v -> {
            history.clear();
            renderHistory();
        });

        // Sidebar settings switches (guarded against render-triggered fires).
        enableSwitch.setOnCheckedChangeListener((btn, on) -> {
            if (renderingSettings) return;
            if (on) {
                if (!Settings.canDrawOverlays(service)) {
                    settings.setEnabled(false);
                    renderingSettings = true;
                    btn.setChecked(false);
                    renderingSettings = false;
                    toast("Pehle 'Display over other apps' permission dein.");
                    return;
                }
                settings.setEnabled(true);
                SidebarService.start(service);
            } else {
                settings.setEnabled(false);
                SidebarService.stop(service);
            }
        });
        autoCollapseSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!renderingSettings) settings.setAutoCollapseCopy(on);
        });
        clipAutofillSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!renderingSettings) settings.setClipboardAutofill(on);
        });
        backgroundSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!renderingSettings) settings.setBackgroundProcessing(on);
        });
        hapticSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!renderingSettings) settings.setHaptic(on);
        });

        // IME & Insets: shrink the panel so input + result stay visible (API 20+ listener).
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int imeH;
            if (Build.VERSION.SDK_INT >= 30) {
                imeH = insets.getInsets(WindowInsets.Type.ime()).bottom;
                int nb = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                if (nb > 0) navBarH = nb;
            } else {
                imeH = insets.getSystemWindowInsetBottom();
            }
            applyImeHeight(imeH);
            return insets;
        });

        pill.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (!expanded) {
                applyPillPosition();
                updateGestureExclusion();
            }
        });
    }

    private void buildChips() {
        final boolean[] expandedMore = {false};
        Chip moreChip = new Chip(themed);
        moreChip.setText("More ∨");
        moreChip.setCheckable(false);
        moreChip.setClickable(true);
        moreChip.setEnsureMinTouchTargetSize(false);
        moreChip.setTextSize(11f);
        moreChip.setChipBackgroundColorResource(R.color.chip_bg_cyber);
        moreChip.setChipStrokeColorResource(R.color.chip_stroke_cyber);
        moreChip.setChipStrokeWidth(3f);
        moreChip.setTextColor(androidx.core.content.ContextCompat.getColorStateList(service, R.color.chip_text_cyber));

        for (WritingTone tone : WritingTone.values()) {
            final WritingTone t = tone;
            int ord = tone.ordinal();

            Chip main = new Chip(themed);
            main.setText(tone.label);
            main.setCheckable(true);
            main.setEnsureMinTouchTargetSize(false);
            main.setTextSize(11f);
            main.setChipBackgroundColorResource(R.color.chip_bg_cyber);
            main.setChipStrokeColorResource(R.color.chip_stroke_cyber);
            main.setChipStrokeWidth(3f);
            main.setTextColor(androidx.core.content.ContextCompat.getColorStateList(service, R.color.chip_text_cyber));
            main.setCheckedIconResource(R.drawable.ic_check);
            main.setCheckedIconVisible(true);
            main.setCheckedIconTint(androidx.core.content.ContextCompat.getColorStateList(service, R.color.cyber_cyan));
            main.setOnCheckedChangeListener((btn, checked) -> {
                if (syncingChips || !checked) return;
                selectedTone = t;
            });
            mainChips[ord] = main;
            if (ord >= 7) {
                main.setVisibility(View.GONE);
            }
            toneGroup.addView(main);

            Chip rc = new Chip(themed);
            rc.setText(tone.label);
            rc.setCheckable(true);
            rc.setEnsureMinTouchTargetSize(false);
            rc.setTextSize(11f);
            rc.setChipBackgroundColorResource(R.color.chip_bg_cyber);
            rc.setChipStrokeColorResource(R.color.chip_stroke_cyber);
            rc.setChipStrokeWidth(3f);
            rc.setTextColor(androidx.core.content.ContextCompat.getColorStateList(service, R.color.chip_text_cyber));
            rc.setCheckedIconVisible(false);
            rc.setOnCheckedChangeListener((btn, checked) -> {
                if (syncingChips || !checked) return;
                if (generating) return;
                if (resultText.getText() == null || resultText.getText().length() == 0) return;
                selectedTone = t;
                syncingChips = true;
                toneGroup.check(mainChips[t.ordinal()].getId());
                if (t.ordinal() >= 7 && !expandedMore[0]) {
                    expandedMore[0] = true;
                    moreChip.setText("Less ∧");
                    for (int i = 7; i < WritingTone.values().length; i++) {
                        mainChips[i].setVisibility(View.VISIBLE);
                    }
                }
                syncingChips = false;
                runAction(lastAction);
            });
            resultChips[ord] = rc;
            resultToneGroup.addView(rc);
        }

        moreChip.setOnClickListener(v -> {
            expandedMore[0] = !expandedMore[0];
            moreChip.setText(expandedMore[0] ? "Less ∧" : "More ∨");
            for (int i = 7; i < WritingTone.values().length; i++) {
                mainChips[i].setVisibility(expandedMore[0] ? View.VISIBLE : View.GONE);
            }
        });
        toneGroup.addView(moreChip, 7);

        syncingChips = true;
        toneGroup.check(mainChips[WritingTone.DEFAULT.ordinal()].getId());
        resultToneGroup.check(resultChips[WritingTone.DEFAULT.ordinal()].getId());
        syncingChips = false;
    }

    private void showContent(int which) {
        mainView.setVisibility(which == VIEW_MAIN ? View.VISIBLE : View.GONE);
        historyView.setVisibility(which == VIEW_HISTORY ? View.VISIBLE : View.GONE);
        settingsView.setVisibility(which == VIEW_SETTINGS ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------- generation

    private void runAction(WritingAction action) {
        if (generating || destroyed || !attached) return;
        String text = input.getText() == null ? "" : input.getText().toString().trim();
        if (text.isEmpty()) {
            showError("Pehle kuch text likhein ya paste karein.");
            return;
        }
        if (!container.localAiManager().isModelReady()) {
            showError("Model ready nahi hai — Settings me check karein.");
            return;
        }
        hideError();
        lastAction = action;
        String custom = (customInput.getVisibility() == View.VISIBLE
                && customInput.getText() != null)
                ? customInput.getText().toString().trim() : "";
        activeCustom = custom;
        final long seq = ++runSeq;
        final String inputSnapshot = text;
        final WritingTone tone = selectedTone;
        settings.setPendingRequest(new SidebarSettings.PendingRequest(
                inputSnapshot, tone.label, action.name(), custom, System.currentTimeMillis()));
        setGeneratingUI(true);
        activeRequestId = container.inferenceQueue().submit(
                WritingPrompt.system(action, tone, custom),
                WritingPrompt.user(inputSnapshot),
                AiContract.MAX_WRITE_TOKENS,
                (output, cancelled) -> main.post(
                        () -> handleDone(seq, output, cancelled, inputSnapshot, tone, action)));
    }

    private void handleDone(long seq, @Nullable String raw, boolean cancelled,
                            String inputSnapshot, WritingTone tone, WritingAction action) {
        if (destroyed) return;
        if (seq != runSeq) return;   // superseded by cancel()/new run — ignore
        activeRequestId = 0;
        if (cancelled) {
            setGeneratingUI(false);
            settings.clearPendingRequest();
            return;
        }
        setGeneratingUI(false);
        settings.clearPendingRequest();
        String output = WritingValidator.sanitize(raw);
        if (output == null) {
            showError("Jawab khali/faulty aaya — dobara try karein.");
            return;
        }
        long now = System.currentTimeMillis();
        history.add(new WritingHistory.Entry(inputSnapshot, output, tone.label, now));
        settings.setCurrentResult(new SidebarSettings.Result(
                inputSnapshot, output, tone.label, action.name(), activeCustom, now));
        displayedIsHistoryEntry = false;
        if (expanded) {
            settings.setUnviewed(false);
            renderResult(output, tone);
        } else {
            settings.setUnviewed(true);
        }
        updateDot();
        haptic(30);
    }

    private void cancelGeneration() {
        if (!generating) return;
        runSeq++;                    // stale-delivery guard for the in-flight run
        if (activeRequestId != 0) {
            try {
                container.inferenceQueue().cancel(activeRequestId);
            } catch (RuntimeException ignored) {
                // queue gone with process
            }
            activeRequestId = 0;
        }
        setGeneratingUI(false);
        settings.clearPendingRequest();
    }

    private void setGeneratingUI(boolean on) {
        generating = on;
        progressRow.setVisibility(on ? View.VISIBLE : View.GONE);
        for (View b : actionButtons) {
            if (b != null) b.setEnabled(!on);
        }
        if (runActionBtn != null) runActionBtn.setEnabled(!on);
        if (regenBtn != null) regenBtn.setEnabled(!on);
        if (badgeStatusText != null) {
            badgeStatusText.setText(on ? "Likh raha hai…" : "Ready");
        }
    }

    private void selectAction(WritingAction action) {
        lastAction = action;
        for (WritingAction a : WritingAction.values()) {
            int ord = a.ordinal();
            boolean isSelected = a == action;
            if (actionButtons[ord] != null) {
                actionButtons[ord].setBackgroundResource(isSelected
                        ? R.drawable.bg_action_tile_selected
                        : R.drawable.bg_action_tile);
            }
            if (actionIcons[ord] != null) {
                actionIcons[ord].setColorFilter(isSelected
                        ? Color.BLACK
                        : androidx.core.content.ContextCompat.getColor(service, R.color.cyber_text_primary));
            }
            if (actionTexts[ord] != null) {
                actionTexts[ord].setTextColor(isSelected
                        ? Color.BLACK
                        : androidx.core.content.ContextCompat.getColor(service, R.color.cyber_text_primary));
            }
        }
        if (ctaText != null) {
            ctaText.setText(action.label + " message");
        }
    }

    private void renderResult(String output, WritingTone tone) {
        resultText.setText(output);
        selectTone(tone);
        scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, resultCard.getTop())));
    }

    private void restoreCurrentResult() {
        if (displayedIsHistoryEntry) return;   // keep a history-loaded entry visible
        SidebarSettings.Result r = settings.getCurrentResult();
        if (r == null) return;
        if (input.getText() == null || input.getText().length() == 0) {
            input.setText(r.input);
        }
        resultText.setText(r.output);
        selectTone(WritingTone.fromLabel(r.tone));
        try {
            lastAction = WritingAction.valueOf(r.action);
        } catch (Exception e) {
            lastAction = WritingAction.REWRITE;
        }
        selectAction(lastAction);
        if (r.custom != null && !r.custom.isEmpty()) {
            customInput.setText(r.custom);
            if (customInput.getVisibility() != View.VISIBLE) {
                customInput.setVisibility(View.VISIBLE);
                if (customChevron != null) customChevron.setRotation(180f);
            }
        }
    }

    private void selectTone(WritingTone tone) {
        selectedTone = tone;
        syncingChips = true;
        toneGroup.check(mainChips[tone.ordinal()].getId());
        resultToneGroup.check(resultChips[tone.ordinal()].getId());
        syncingChips = false;
    }

    private void copyResult() {
        CharSequence out = resultText.getText();
        if (out == null || out.length() == 0) return;
        ClipboardManager cm = (ClipboardManager) service.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("LinkVault", out.toString()));
        toast("Copied");
        haptic(20);
        if (settings.isAutoCollapseCopy()) collapse();
    }

    // ---------------------------------------------------------------- history

    private void renderHistory() {
        historyList.removeAllViews();
        List<WritingHistory.Entry> entries = history.list();
        historyEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < entries.size(); i++) {
            final int index = i;
            final WritingHistory.Entry e = entries.get(i);
            View row = inflater.inflate(R.layout.sidebar_history_item, historyList, false);
            ((TextView) row.findViewById(R.id.itemTone)).setText(e.tone);
            ((TextView) row.findViewById(R.id.itemTime)).setText(formatTime(e.timestamp));
            ((TextView) row.findViewById(R.id.itemInput)).setText(e.input);
            ((TextView) row.findViewById(R.id.itemOutput)).setText(e.output);
            row.findViewById(R.id.itemDelete).setOnClickListener(v -> {
                history.remove(index);
                renderHistory();
            });
            row.setOnClickListener(v -> loadEntry(e));
            historyList.addView(row);
        }
    }

    private void loadEntry(WritingHistory.Entry e) {
        input.setText(e.input);
        resultText.setText(e.output);
        selectTone(WritingTone.fromLabel(e.tone));
        displayedIsHistoryEntry = true;
        showContent(VIEW_MAIN);
        scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, resultCard.getTop())));
    }

    private static String formatTime(long ts) {
        try {
            return Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd MMM, HH:mm"));
        } catch (Exception e) {
            return "";
        }
    }

    // ---------------------------------------------------------------- settings

    private void renderSettings() {
        renderingSettings = true;
        enableSwitch.setChecked(settings.isEnabled());
        autoCollapseSwitch.setChecked(settings.isAutoCollapseCopy());
        clipAutofillSwitch.setChecked(settings.isClipboardAutofill());
        backgroundSwitch.setChecked(settings.isBackgroundProcessing());
        hapticSwitch.setChecked(settings.isHaptic());
        renderingSettings = false;
    }

    // --------------------------------------------------------------- clipboard

    private void tryClipboardAutofill() {
        if (!settings.isClipboardAutofill()) return;
        if (input.getText() != null && input.getText().length() > 0) return;
        String clip = readClipboard();
        if (clip != null && !clip.trim().isEmpty()) input.setText(clip);
    }

    @Nullable
    private String readClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) service.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return null;
            ClipData cd = cm.getPrimaryClip();
            if (cd == null || cd.getItemCount() == 0) return null;
            CharSequence t = cd.getItemAt(0).coerceToText(service);
            return t == null ? null : t.toString();
        } catch (SecurityException e) {
            return null;   // Android 10+ focus rules — manual Paste button still works
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------- IME / misc

    private void applyImeHeight(int imeH) {
        if (panel == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        int top = imeH > 0 ? dp(8) : statusBarH + dp(8);
        int bottomClearance = imeH > 0 ? (imeH + dp(8)) : (Math.max(navBarH, dp(32)) + dp(16));
        lp.width = (int) (screenW * PANEL_WIDTH_FRACTION);
        lp.topMargin = top;
        lp.bottomMargin = bottomClearance;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        panel.setLayoutParams(lp);
        if (imeH > 0 && expanded) {
            input.post(() -> input.requestRectangleOnScreen(
                    new Rect(0, 0, input.getWidth(), input.getHeight() + dp(24)), true));
        }
    }

    private void updateDot() {
        if (dot == null || pill == null) return;
        boolean hasUnviewed = !expanded && settings.isUnviewed();
        dot.setVisibility(hasUnviewed ? View.VISIBLE : View.GONE);
        pill.setBackgroundResource(hasUnviewed
                ? R.drawable.bg_sidebar_pill_highlight
                : R.drawable.bg_sidebar_pill);
    }

    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            service.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && root != null) {
                imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
            // token gone during teardown
        }
    }

    private void showError(String msg) {
        errorText.setText(msg);
        errorText.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorText.setVisibility(View.GONE);
    }

    private void haptic(int ms) {
        if (!settings.isHaptic()) return;
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager)
                        service.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm == null ? null : vm.getDefaultVibrator();
            } else {
                v = (Vibrator) service.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v == null || !v.hasVibrator()) return;
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {
            // OEMs can throw on exotic builds — haptics are best-effort
        }
    }

    private void toast(String msg) {
        main.post(() -> {
            if (destroyed) return;
            Toast.makeText(service, msg, Toast.LENGTH_SHORT).show();
        });
    }

    private int statusBarHeight() {
        int id = service.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) return service.getResources().getDimensionPixelSize(id);
        return dp(24);
    }

    private int navBarHeight() {
        int id = service.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (id > 0) return service.getResources().getDimensionPixelSize(id);
        return dp(36);
    }

    /**
     * Excludes the collapsed pill area from Android 10+ system gesture navigation
     * so inward edge swipes activate the sidebar instead of triggering system Back.
     */
    private void updateGestureExclusion() {
        if (Build.VERSION.SDK_INT < 29 || pill == null || root == null) return;
        pill.post(() -> {
            if (pill == null || root == null || !attached || expanded) return;
            int pw = pill.getWidth() > 0 ? pill.getWidth() : dp(10);
            int ph = pill.getHeight() > 0 ? pill.getHeight() : dp(88);
            try {
                Rect pillRect = new Rect(0, 0, pw, ph);
                pill.setSystemGestureExclusionRects(Collections.singletonList(pillRect));
                if (root.getWidth() > 0 && root.getHeight() > 0) {
                    Rect rootRect = new Rect(0, 0, root.getWidth(), root.getHeight());
                    root.setSystemGestureExclusionRects(Collections.singletonList(rootRect));
                }
            } catch (Exception ignored) {
                // Best-effort for custom OEM gesture navigation implementations
            }
        });
    }

    private int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
