package com.linkvault.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import com.linkvault.app.App;
import com.linkvault.app.R;
import com.linkvault.app.data.repo.IndexRepository;
import com.linkvault.app.domain.model.VaultItem;
import com.linkvault.app.ui.MainActivity;

import java.util.List;

/**
 * Home-screen widget showing the 5 most recent links.
 * Rows open in the browser; header opens LinkVault.
 * Refreshed via updateAll() after every data change.
 */
public class LinkVaultWidgetProvider extends AppWidgetProvider {

    public static final int MAX_ROWS = 5;

    private static final int REQ_OPEN_APP = 1;
    private static final int REQ_ROW_BASE = 100;

    private static final int[] ROW_IDS = {
            R.id.widgetRow0, R.id.widgetRow1, R.id.widgetRow2,
            R.id.widgetRow3, R.id.widgetRow4
    };
    private static final int[] TITLE_IDS = {
            R.id.widgetTitle0, R.id.widgetTitle1, R.id.widgetTitle2,
            R.id.widgetTitle3, R.id.widgetTitle4
    };
    private static final int[] URL_IDS = {
            R.id.widgetUrl0, R.id.widgetUrl1, R.id.widgetUrl2,
            R.id.widgetUrl3, R.id.widgetUrl4
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        RemoteViews views = buildViews(context);
        for (int id : appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views);
        }
    }

    /** Rebuilds and pushes the widget to every placed instance (no-op if none). */
    public static void updateAll(Context context) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(
                    new ComponentName(context.getApplicationContext(), LinkVaultWidgetProvider.class));
            if (ids.length == 0) return;
            RemoteViews views = buildViews(context.getApplicationContext());
            for (int id : ids) {
                mgr.updateAppWidget(id, views);
            }
        } catch (RuntimeException ignored) {
            // Widget update must never crash the app.
        }
    }

    static RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_linkvault);

        Intent openApp = new Intent(context, MainActivity.class);
        views.setOnClickPendingIntent(R.id.widgetHeader, PendingIntent.getActivity(
                context, REQ_OPEN_APP, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        List<VaultItem> recent = loadRecent(context);
        for (int i = 0; i < MAX_ROWS; i++) {
            VaultItem item = i < recent.size() ? recent.get(i) : null;
            if (item != null && item.isLink() && item.getLink() != null
                    && item.getLink().getUrl() != null
                    && !item.getLink().getUrl().isEmpty()) {
                CharSequence title = item.getTitle() == null || item.getTitle().isEmpty()
                        ? item.getLink().getUrl() : item.getTitle();
                views.setTextViewText(TITLE_IDS[i], title);
                views.setTextViewText(URL_IDS[i], item.getLink().getUrl());
                views.setViewVisibility(ROW_IDS[i], View.VISIBLE);

                Intent open = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(item.getLink().getUrl()));
                views.setOnClickPendingIntent(ROW_IDS[i], PendingIntent.getActivity(
                        context, REQ_ROW_BASE + i, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            } else {
                views.setViewVisibility(ROW_IDS[i], View.GONE);
            }
        }
        return views;
    }

    private static List<VaultItem> loadRecent(Context context) {
        try {
            App app = (App) context.getApplicationContext();
            List<VaultItem> links = app.getContainer().indexRepository().query(
                    null,
                    null,
                    VaultItem.ItemType.LINK,
                    IndexRepository.SortOrder.NEWEST,
                    null);
            return links.size() > MAX_ROWS
                    ? links.subList(0, MAX_ROWS)
                    : links;
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
