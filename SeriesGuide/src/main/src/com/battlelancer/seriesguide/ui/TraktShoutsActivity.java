/*
 * Copyright 2011 Uwe Trottmann
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

package com.battlelancer.seriesguide.ui;

import android.os.Bundle;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragment;
import com.battlelancer.seriesguide.util.ShareUtils.ShareItems;
import com.google.analytics.tracking.android.EasyTracker;

public class TraktShoutsActivity extends BaseNavDrawerActivity {

    public static Bundle createInitBundleEpisode(int showTvdbid, int seasonNumber,
            int episodeNumber, String title) {
        Bundle extras = new Bundle();
        extras.putInt(ShareItems.TVDBID, showTvdbid);
        extras.putInt(ShareItems.SEASON, seasonNumber);
        extras.putInt(ShareItems.EPISODE, episodeNumber);
        extras.putString(ShareItems.SHARESTRING, title);
        return extras;
    }

    public static Bundle createInitBundleShow(String title, int tvdbId) {
        Bundle extras = new Bundle();
        extras.putInt(ShareItems.TVDBID, tvdbId);
        extras.putString(ShareItems.SHARESTRING, title);
        return extras;
    }

    public static Bundle createInitBundleMovie(String title, int tmdbId) {
        Bundle extras = new Bundle();
        extras.putInt(ShareItems.TMDBID, tmdbId);
        extras.putString(ShareItems.SHARESTRING, title);
        return extras;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getIntent().getExtras();
        String title = args.getString(ShareItems.SHARESTRING);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        if (savedInstanceState == null) {
            // embed the shouts fragment dialog
            SherlockFragment f;
            int tvdbId = args.getInt(ShareItems.TVDBID);
            int episode = args.getInt(ShareItems.EPISODE);
            if (tvdbId == 0) {
                int tmdbId = args.getInt(ShareItems.TMDBID);
                f = TraktShoutsFragment.newInstanceMovie(title, tmdbId);
            } else if (episode == 0) {
                f = TraktShoutsFragment.newInstanceShow(title, tvdbId);
            } else {
                int season = args.getInt(ShareItems.SEASON);
                f = TraktShoutsFragment
                        .newInstanceEpisode(title, tvdbId, season, episode);
            }
            getSupportFragmentManager().beginTransaction().add(android.R.id.content, f)
                    .commit();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        EasyTracker.getInstance(this).activityStart(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        EasyTracker.getInstance(this).activityStop(this);
    }
}
