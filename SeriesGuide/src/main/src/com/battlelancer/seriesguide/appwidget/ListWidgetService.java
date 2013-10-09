/*
 * Copyright 2012 Uwe Trottmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.battlelancer.seriesguide.appwidget;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.battlelancer.seriesguide.provider.SeriesContract.Episodes;
import com.battlelancer.seriesguide.provider.SeriesContract.Shows;
import com.battlelancer.seriesguide.provider.SeriesGuideDatabase.Qualified;
import com.battlelancer.seriesguide.settings.WidgetSettings;
import com.battlelancer.seriesguide.ui.EpisodesActivity;
import com.battlelancer.seriesguide.ui.UpcomingFragment.UpcomingQuery;
import com.battlelancer.seriesguide.util.DBUtils;
import com.battlelancer.seriesguide.util.ImageProvider;
import com.battlelancer.seriesguide.util.Utils;
import com.uwetrottmann.seriesguide.R;

@TargetApi(11)
public class ListWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new ListRemoteViewsFactory(this.getApplicationContext(), intent);
    }

    class ListRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
        private Context mContext;

        private int mAppWidgetId;

        private Cursor mDataCursor;

        private int mTypeIndex;

        public ListRemoteViewsFactory(Context context, Intent intent) {
            mContext = context;
            mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        @Override
        public void onCreate() {
            // In onCreate() you setup any connections / cursors to your data
            // source. Heavy lifting, for example downloading or creating
            // content etc, should be deferred to onDataSetChanged() or
            // getViewAt(). Taking more than 20 seconds in this call will result
            // in an ANR.
            onQueryForData();
        }

        private void onQueryForData() {
            boolean isHideWatched = WidgetSettings.getWidgetHidesWatched(mContext, mAppWidgetId);
            mTypeIndex = WidgetSettings.getWidgetListType(mContext, mAppWidgetId);

            switch (mTypeIndex) {
                case WidgetSettings.Type.RECENT:
                    // Recent episodes
                    mDataCursor = DBUtils.getRecentEpisodes(isHideWatched, mContext);
                    break;
                case WidgetSettings.Type.FAVORITES:
                    // Favorite shows + next episodes, exclude those without
                    // episode
                    mDataCursor = getContentResolver().query(
                            Shows.CONTENT_URI_WITH_EPISODE,
                            ShowsQuery.PROJECTION,
                            Shows.HIDDEN + "=0" + Shows.SELECTION_FAVORITES
                                    + Shows.SELECTION_WITH_NEXT_EPISODE, null,
                            Shows.DEFAULT_SORT);
                    break;
                default:
                    // Upcoming episodes
                    mDataCursor = DBUtils.getUpcomingEpisodes(isHideWatched, mContext);
                    break;
            }
        }

        @Override
        public void onDestroy() {
            // In onDestroy() you should tear down anything that was setup for
            // your data source, eg. cursors, connections, etc.
            mDataCursor.close();
        }

        @Override
        public int getCount() {
            if (mDataCursor != null) {
                return mDataCursor.getCount();
            } else {
                return 0;
            }
        }

        @Override
        public RemoteViews getViewAt(int position) {
            final boolean isShowQuery = mTypeIndex == WidgetSettings.Type.FAVORITES;

            // We construct a remote views item based on our widget item xml
            // file, and set the text based on the position.
            RemoteViews rv = new RemoteViews(mContext.getPackageName(), R.layout.appwidget_row);

            if (mDataCursor.isClosed()) {
                return rv;
            }
            // position will always range from 0 to getCount() - 1.
            mDataCursor.moveToPosition(position);

            // episode description
            int seasonNumber = mDataCursor.getInt(isShowQuery ?
                    ShowsQuery.EPISODE_SEASON : UpcomingQuery.SEASON);
            int episodeNumber = mDataCursor.getInt(isShowQuery ?
                    ShowsQuery.EPISODE_NUMBER : UpcomingQuery.NUMBER);
            String title = mDataCursor.getString(isShowQuery ?
                    ShowsQuery.EPISODE_TITLE : UpcomingQuery.TITLE);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            rv.setTextViewText(R.id.textViewWidgetEpisode,
                    Utils.getNextEpisodeString(prefs, seasonNumber, episodeNumber, title));

            // relative airtime
            long airtime = mDataCursor.getLong(isShowQuery ?
                    ShowsQuery.EPISODE_FIRSTAIRED_MS : UpcomingQuery.FIRSTAIREDMS);
            String[] dayAndTime = Utils.formatToTimeAndDay(airtime, mContext);
            String value = dayAndTime[2] + " (" + dayAndTime[1] + ")";
            rv.setTextViewText(R.id.widgetAirtime, value);

            // absolute airtime and network (if any)
            value = dayAndTime[0];
            String network = mDataCursor.getString(isShowQuery ?
                    ShowsQuery.SHOW_NETWORK : UpcomingQuery.SHOW_NETWORK);
            if (network.length() != 0) {
                value += " " + network;
            }
            rv.setTextViewText(R.id.widgetNetwork, value);

            // show name
            value = mDataCursor.getString(isShowQuery ?
                    ShowsQuery.SHOW_TITLE : UpcomingQuery.SHOW_TITLE);
            rv.setTextViewText(R.id.textViewWidgetShow, value);

            // show poster
            value = mDataCursor.getString(isShowQuery
                    ? ShowsQuery.SHOW_POSTER : UpcomingQuery.SHOW_POSTER);
            final Bitmap poster = ImageProvider.getInstance(mContext).getImage(value, true);
            if (poster != null) {
                rv.setImageViewBitmap(R.id.widgetPoster, poster);
            } else {
                rv.setImageViewResource(R.id.widgetPoster, R.drawable.show_generic);
            }

            // Set the fill-in intent for the list items
            Bundle extras = new Bundle();
            extras.putInt(EpisodesActivity.InitBundle.EPISODE_TVDBID,
                    mDataCursor.getInt(isShowQuery ?
                            ShowsQuery.SHOW_NEXT_EPISODE_ID : UpcomingQuery._ID));
            Intent fillInIntent = new Intent();
            fillInIntent.putExtras(extras);
            rv.setOnClickFillInIntent(R.id.appwidget_row, fillInIntent);

            // Return the remote views object.
            return rv;
        }

        @Override
        public RemoteViews getLoadingView() {
            // You can create a custom loading view (for instance when
            // getViewAt() is slow.) If you return null here, you will get the
            // default loading view.
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public void onDataSetChanged() {
            // This is triggered when you call AppWidgetManager
            // notifyAppWidgetViewDataChanged
            // on the collection view corresponding to this factory. You can do
            // heaving lifting in
            // here, synchronously. For example, if you need to process an
            // image, fetch something
            // from the network, etc., it is ok to do it here, synchronously.
            // The widget will remain
            // in its current state while work is being done here, so you don't
            // need to worry about locking up the widget.
            if (mDataCursor != null) {
                mDataCursor.close();
            }
            onQueryForData();
        }
    }

    interface ShowsQuery {
        String[] PROJECTION = {
                Qualified.SHOWS_ID, Shows.TITLE, Shows.NETWORK, Shows.POSTER, Shows.STATUS,
                Shows.NEXTEPISODE, Episodes.TITLE, Episodes.NUMBER, Episodes.SEASON,
                Episodes.FIRSTAIREDMS
        };

        int SHOW_ID = 0;

        int SHOW_TITLE = 1;

        int SHOW_NETWORK = 2;

        int SHOW_POSTER = 3;

        int SHOW_STATUS = 4;

        int SHOW_NEXT_EPISODE_ID = 5;

        int EPISODE_TITLE = 6;

        int EPISODE_NUMBER = 7;

        int EPISODE_SEASON = 8;

        int EPISODE_FIRSTAIRED_MS = 9;

    }
}
