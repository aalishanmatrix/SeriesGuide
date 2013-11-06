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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.battlelancer.seriesguide.provider.SeriesContract.ListItemTypes;
import com.battlelancer.seriesguide.provider.SeriesContract.ListItems;
import com.battlelancer.seriesguide.provider.SeriesContract.Shows;
import com.battlelancer.seriesguide.util.DBUtils;
import com.google.analytics.tracking.android.EasyTracker;

import com.battlelancer.seriesguide.util.Utils;
import com.uwetrottmann.seriesguide.R;

public class ConfirmDeleteDialogFragment extends DialogFragment {

    /**
     * Dialog to confirm the removal of a show from the database.
     * 
     * @param showId The show to remove.
     * @return
     */
    public static ConfirmDeleteDialogFragment newInstance(String showId) {
        ConfirmDeleteDialogFragment f = new ConfirmDeleteDialogFragment();

        Bundle args = new Bundle();
        args.putString("showid", showId);
        f.setArguments(args);

        return f;
    }

    @Override
    public void onStart() {
        super.onStart();
        Utils.trackView(getActivity(), "Delete Dialog");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final String showId = getArguments().getString("showid");

        // make sure this show isn't added to any lists
        boolean hasListItems = true;
        /*
         * Filter for type when looking for show list items as it looks like the
         * where is pushed down as far as possible excluding all shows in the
         * original list items query.
         */
        final Cursor itemsInLists = getActivity().getContentResolver().query(
                ListItems.CONTENT_WITH_DETAILS_URI,
                new String[] {
                    ListItems.LIST_ITEM_ID
                },
                Shows.REF_SHOW_ID + "=? OR (" + ListItems.TYPE + "=" + ListItemTypes.SHOW + " AND "
                        + ListItems.ITEM_REF_ID
                        + "=?)", new String[] {
                        showId, showId
                }, null);
        if (itemsInLists != null) {
            hasListItems = itemsInLists.getCount() > 0;
            itemsInLists.close();
        }

        final Cursor show = getActivity().getContentResolver().query(Shows.buildShowUri(showId),
                new String[] {
                    Shows.TITLE
                }, null, null, null);

        String showName = getString(R.string.unknown);
        if (show != null && show.moveToFirst()) {
            showName = show.getString(0);
        }
        if (show != null) {
            show.close();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setNegativeButton(getString(R.string.dontdelete_show), null);
        if (hasListItems) {
            // Prevent deletion, tell user there are still list items
            builder.setMessage(getString(R.string.delete_has_list_items, showName));
        } else {
            builder.setMessage(getString(R.string.confirm_delete, showName)).setPositiveButton(
                    getString(R.string.delete_show), new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final ProgressDialog progress = new ProgressDialog(getActivity());
                            progress.setCancelable(false);
                            progress.show();

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    DBUtils.deleteShow(getActivity(), getArguments()
                                            .getString("showid"), progress);
                                }
                            }).start();
                        }
                    });
        }

        return builder.create();
    }
}
