package com.battlelancer.seriesguide.ui.dialogs;

import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.enums.TraktStatus;
import com.battlelancer.seriesguide.util.ShareUtils.ProgressDialog;
import com.battlelancer.seriesguide.util.TraktTask;
import com.battlelancer.seriesguide.util.Utils;
import com.jakewharton.apibuilder.ApiException;
import com.jakewharton.trakt.ServiceManager;
import com.jakewharton.trakt.TraktException;
import com.jakewharton.trakt.entities.Response;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.format.DateUtils;
import android.widget.Toast;

public class TraktCancelCheckinDialogFragment extends DialogFragment {

    private int mWait;

    public static TraktCancelCheckinDialogFragment newInstance(Bundle traktData, int wait) {
        TraktCancelCheckinDialogFragment f = new TraktCancelCheckinDialogFragment();
        f.setArguments(traktData);
        f.mWait = wait;
        return f;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Context context = getActivity().getApplicationContext();
        final FragmentManager fm = getFragmentManager();
        final Bundle args = getArguments();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setMessage(context.getString(R.string.traktcheckin_inprogress,
                DateUtils.formatElapsedTime(mWait)));

        builder.setPositiveButton(R.string.traktcheckin_cancel, new OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                FragmentTransaction ft = fm.beginTransaction();
                Fragment prev = fm.findFragmentByTag("progress-dialog");
                if (prev != null) {
                    ft.remove(prev);
                }
                ProgressDialog newFragment = ProgressDialog.newInstance();
                newFragment.show(ft, "progress-dialog");

                AsyncTask<String, Void, Response> cancelCheckinTask = new AsyncTask<String, Void, Response>() {

                    @Override
                    protected Response doInBackground(String... params) {

                        ServiceManager manager;
                        try {
                            manager = Utils.getServiceManagerWithAuth(context, false);
                        } catch (Exception e) {
                            // password could not be decrypted
                            Response r = new Response();
                            r.status = TraktStatus.FAILURE;
                            r.error = context.getString(R.string.trakt_decryptfail);
                            return r;
                        }

                        Response response;
                        try {
                            response = manager.showService().cancelCheckin().fire();
                        } catch (TraktException te) {
                            Response r = new Response();
                            r.status = TraktStatus.FAILURE;
                            r.error = te.getMessage();
                            return r;
                        } catch (ApiException e) {
                            Response r = new Response();
                            r.status = TraktStatus.FAILURE;
                            r.error = e.getMessage();
                            return r;
                        }
                        return response;
                    }

                    @Override
                    protected void onPostExecute(Response r) {
                        if (r.status.equalsIgnoreCase(TraktStatus.SUCCESS)) {
                            // all good
                            Toast.makeText(
                                    context,
                                    context.getString(R.string.trakt_success) + ": "
                                            + r.message, Toast.LENGTH_SHORT).show();

                            // relaunch the trakt task which called us to
                            // try the check in again
                            new TraktTask(context, fm, args, null).execute();
                        } else if (r.status.equalsIgnoreCase(TraktStatus.FAILURE)) {
                            // well, something went wrong
                            Toast.makeText(context,
                                    context.getString(R.string.trakt_error) + ": " + r.error,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                };

                cancelCheckinTask.execute();
            }
        });
        builder.setNegativeButton(R.string.traktcheckin_wait, null);

        return builder.create();
    }
}