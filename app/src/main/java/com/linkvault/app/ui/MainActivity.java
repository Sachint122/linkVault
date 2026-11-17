package com.linkvault.app.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.linkvault.app.App;
import com.linkvault.app.R;
import com.linkvault.app.data.saf.StoragePermissionManager;
import com.linkvault.app.data.share.ShareIntentParser;
import com.linkvault.app.ui.save.SaveLinkFragment;

/**
 * Single host activity: toolbar + NavHost (home <-> save / categories / settings).
 * Handles ACTION_SEND shares (singleTop: cold start and while-running both land here).
 */
public class MainActivity extends AppCompatActivity {

    /** Pending share payload when the user still needs to pick a folder. */
    @Nullable
    private static volatile Bundle pendingShare;

    private NavController navController;
    private AppBarConfiguration appBarConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        if (navHost == null) return;
        navController = navHost.getNavController();

        appBarConfig = new AppBarConfiguration.Builder(
                R.id.homeFragment, R.id.onboardingFragment).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig);

        // Clean Back button behavior: exit app from Home, never loop
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (navController != null) {
                    NavDestination dest = navController.getCurrentDestination();
                    if (dest != null && (dest.getId() == R.id.homeFragment || dest.getId() == R.id.onboardingFragment)) {
                        finish();
                        return;
                    }
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });

        if (savedInstanceState == null) {
            // First run without folder: navigate to onboarding
            if (container().permissionManager().getAccessState()
                    != StoragePermissionManager.AccessState.GRANTED) {
                navController.navigate(R.id.onboardingFragment);
            }
            handleShareIntent(getIntent());
        }
        if (pendingShare != null
                && container().permissionManager().getAccessState()
                == StoragePermissionManager.AccessState.GRANTED) {
            consumePendingShare();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShareIntent(intent);
    }

    /**
     * Parses an ACTION_SEND payload and routes to the review screen.
     * Nothing is persisted automatically - the review screen is mandatory
     * (FR-01). If no folder is selected yet, the payload is parked until
     * onboarding completes (process-death caveat: static holder, documented).
     */
    private void handleShareIntent(@Nullable Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) return;

        Bundle args = new Bundle();
        boolean validShare = false;

        if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            java.util.ArrayList<android.net.Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null && !uris.isEmpty()) {
                android.net.Uri firstUri = uris.get(0);
                com.linkvault.app.data.share.SharedFilePayload fp =
                        com.linkvault.app.data.share.SharedFilePayload.fromUri(getContentResolver(), firstUri, intent.getType());
                args.putBoolean(SaveLinkFragment.ARG_IS_FILE, true);
                args.putString(SaveLinkFragment.ARG_FILE_URI, firstUri.toString());
                args.putString(SaveLinkFragment.ARG_TITLE, fp.displayName);
                args.putString(SaveLinkFragment.ARG_FILE_NAME, fp.displayName);
                args.putString(SaveLinkFragment.ARG_FILE_MIME, fp.mimeType);
                args.putLong(SaveLinkFragment.ARG_FILE_SIZE, fp.sizeBytes);
                args.putString(SaveLinkFragment.ARG_SOURCE_PKG, referrerOf(intent));
                if (uris.size() > 1) {
                    java.util.ArrayList<String> strUris = new java.util.ArrayList<>();
                    for (android.net.Uri u : uris) strUris.add(u.toString());
                    args.putStringArrayList(SaveLinkFragment.ARG_BATCH_URIS, strUris);
                }
                validShare = true;
            }
        } else if (Intent.ACTION_SEND.equals(action)) {
            android.net.Uri streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            // Check text first so URL shares with secondary stream previews are parsed as links
            String text = extractTextFromIntent(intent);
            if (text != null) {
                String titleHint = extractTitleHint(intent);
                ShareIntentParser.SharePayload payload = ShareIntentParser.parse(text, titleHint, referrerOf(intent));
                if (payload.hasUrl()) {
                    args.putAll(toBundle(payload));
                    validShare = true;
                }
            }

            if (!validShare && streamUri != null) {
                // File share!
                com.linkvault.app.data.share.SharedFilePayload fp =
                        com.linkvault.app.data.share.SharedFilePayload.fromUri(getContentResolver(), streamUri, intent.getType());
                args.putBoolean(SaveLinkFragment.ARG_IS_FILE, true);
                args.putString(SaveLinkFragment.ARG_FILE_URI, streamUri.toString());
                args.putString(SaveLinkFragment.ARG_TITLE, fp.displayName);
                args.putString(SaveLinkFragment.ARG_FILE_NAME, fp.displayName);
                args.putString(SaveLinkFragment.ARG_FILE_MIME, fp.mimeType);
                args.putLong(SaveLinkFragment.ARG_FILE_SIZE, fp.sizeBytes);
                args.putString(SaveLinkFragment.ARG_SOURCE_PKG, referrerOf(intent));
                validShare = true;
            }
        }

        // Clear intent from activity so it doesn't re-trigger on orientation or lifecycle events
        setIntent(new Intent(this, MainActivity.class));

        if (!validShare) return;

        if (container().permissionManager().getAccessState()
                != StoragePermissionManager.AccessState.GRANTED) {
            pendingShare = args;
            return;
        }
        navigateToSave(args);
    }

    @Nullable
    private String extractTextFromIntent(Intent intent) {
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text != null && !text.trim().isEmpty()) return text;

        CharSequence cs = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (cs != null && !cs.toString().trim().isEmpty()) return cs.toString();

        android.content.ClipData clipData = intent.getClipData();
        if (clipData != null && clipData.getItemCount() > 0) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                android.content.ClipData.Item item = clipData.getItemAt(i);
                if (item != null) {
                    if (item.getText() != null && !item.getText().toString().trim().isEmpty()) {
                        return item.getText().toString();
                    }
                    if (item.getUri() != null) {
                        String uriStr = item.getUri().toString();
                        if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
                            return uriStr;
                        }
                    }
                }
            }
        }

        if (intent.getData() != null) {
            String dataStr = intent.getData().toString();
            if (dataStr.startsWith("http://") || dataStr.startsWith("https://")) {
                return dataStr;
            }
        }
        return null;
    }

    @Nullable
    private String extractTitleHint(Intent intent) {
        String title = firstNonEmpty(
                intent.getStringExtra(Intent.EXTRA_TITLE),
                intent.getStringExtra(Intent.EXTRA_SUBJECT));
        if (title != null) return title;

        CharSequence cs = intent.getCharSequenceExtra(Intent.EXTRA_TITLE);
        if (cs != null && !cs.toString().trim().isEmpty()) return cs.toString();

        cs = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
        if (cs != null && !cs.toString().trim().isEmpty()) return cs.toString();

        return null;
    }

    /** Called by onboarding after a folder was successfully selected. */
    public void consumePendingShare() {
        if (pendingShare != null) {
            Bundle b = pendingShare;
            pendingShare = null;
            navigateToSave(b);
        }
    }

    private void navigateToSave(Bundle args) {
        if (navController == null) return;
        args.putBoolean(SaveLinkFragment.ARG_FROM_SHARE, true);
        // Replace any existing review screen so repeated shares don't stack.
        navController.navigate(R.id.saveLinkFragment, args,
                new androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.saveLinkFragment, true)
                        .build());
    }

    private com.linkvault.app.di.AppContainer container() {
        return ((App) getApplication()).getContainer();
    }

    private static Bundle toBundle(ShareIntentParser.SharePayload p) {
        Bundle b = new Bundle();
        b.putString(SaveLinkFragment.ARG_URL, p.singleUrl());
        b.putString(SaveLinkFragment.ARG_TITLE, p.title);
        b.putString(SaveLinkFragment.ARG_SOURCE_PKG, p.sourcePackage);
        b.putString(SaveLinkFragment.ARG_SHARED_TEXT, p.sharedText);
        if (p.urls.size() > 1) {
            // Multiple candidates: review screen shows the first; user can edit.
            b.putString(SaveLinkFragment.ARG_URL, p.urls.get(0));
            b.putString(SaveLinkFragment.ARG_OTHER_URLS,
                    String.join("\n", p.urls.subList(1, p.urls.size())));
        }
        return b;
    }

    @Nullable
    private static String referrerOf(Intent intent) {
        android.os.Bundle extras = intent.getExtras();
        if (extras == null) return null;
        Object ref = extras.get(Intent.EXTRA_REFERRER);
        if (ref instanceof android.net.Uri) return ref.toString();
        if (ref instanceof String) return (String) ref;
        return null;
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String a, @Nullable String b) {
        if (a != null && !a.trim().isEmpty()) return a;
        if (b != null && !b.trim().isEmpty()) return b;
        return null;
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (navController != null) {
            NavDestination dest = navController.getCurrentDestination();
            if (dest != null && dest.getId() == R.id.saveLinkFragment) {
                androidx.navigation.NavBackStackEntry entry = navController.getCurrentBackStackEntry();
                Bundle args = entry != null ? entry.getArguments() : null;
                if (args != null && args.getBoolean(SaveLinkFragment.ARG_FROM_SHARE, false)) {
                    finish();
                    return true;
                }
            }
            if (appBarConfig != null) {
                return NavigationUI.navigateUp(navController, appBarConfig) || super.onSupportNavigateUp();
            }
        }
        return super.onSupportNavigateUp();
    }
}
