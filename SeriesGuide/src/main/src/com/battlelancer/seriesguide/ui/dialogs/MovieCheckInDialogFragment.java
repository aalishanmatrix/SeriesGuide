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

package com.battlelancer.seriesguide.ui.dialogs;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.battlelancer.seriesguide.getglueapi.GetGlue;
import com.battlelancer.seriesguide.getglueapi.GetGlue.CheckInTask;
import com.battlelancer.seriesguide.util.TraktTask;
import com.battlelancer.seriesguide.util.TraktTask.OnTraktActionCompleteListener;
import com.google.analytics.tracking.android.EasyTracker;
import com.uwetrottmann.androidutils.AndroidUtils;

/**
 * Allows to check into movies on trakt or GetGlue. Launching activities must
 * implement {@link OnTraktActionCompleteListener}.
 */
public class MovieCheckInDialogFragment extends GenericCheckInDialogFragment {

    public static MovieCheckInDialogFragment newInstance(String imdbid, String movieTitle) {
        MovieCheckInDialogFragment f = new MovieCheckInDialogFragment();

        Bundle args = new Bundle();
        args.putString(InitBundle.IMDB_ID, imdbid);
        args.putString(InitBundle.ITEM_TITLE, movieTitle);
        f.setArguments(args);

        return f;
    }

    protected final static String TAG = "Movie Check-In Dialog";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = super.onCreateView(inflater, container, savedInstanceState);

        setupFixGetGlueButton(layout, false, 0);

        return layout;
    }

    @Override
    public void onStart() {
        super.onStart();
        EasyTracker.getTracker().sendView("Movie Check-In Dialog");
    }

    @Override
    protected void onGetGlueCheckin(final SharedPreferences prefs, final String imdbid,
            final String message) {
        boolean isAbortingCheckIn = false;
        String objectId = null;

        // require GetGlue authentication
        if (!GetGlue.isAuthenticated(prefs)) {
            isAbortingCheckIn = true;
        } else {
            // fall back to IMDb id
            if (TextUtils.isEmpty(imdbid)) {
                // cancel if we don't know what to check into
                isAbortingCheckIn = true;
            } else {
                objectId = imdbid;
            }
        }

        if (isAbortingCheckIn) {
            mToggleGetGlueButton.setChecked(false);
            mGetGlueChecked = false;
            updateCheckInButtonState();
            return;
        } else {
            // check in, use task on thread pool
            AndroidUtils.executeAsyncTask(new CheckInTask(objectId, message,
                    getActivity()), new Void[] {});
        }
    }

    /**
     * Start the trakt check in task.
     */
    @Override
    protected void onTraktCheckIn(String message) {
        final String imdbId = getArguments().getString(InitBundle.IMDB_ID);
        AndroidUtils.executeAsyncTask(
                new TraktTask(getActivity(), mListener).checkInMovie(imdbId, message),
                new Void[] {
                    null
                });
    }

    @Override
    protected void handleGetGlueToggle(final SharedPreferences prefs, final String imdbid,
            boolean isChecked) {
        if (isChecked) {
            if (!GetGlue.isAuthenticated(prefs)) {
                ensureGetGlueAuthAndConnection();
            } else if (TextUtils.isEmpty(imdbid)) {
                // no IMDb id, no action
                mToggleGetGlueButton.setChecked(false);
            }
        }
    }

}
