package com.battlelancer.seriesguide.migration;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.MenuItem;
import com.battlelancer.seriesguide.dataliberation.JsonExportTask;
import com.battlelancer.seriesguide.dataliberation.JsonImportTask;
import com.battlelancer.seriesguide.dataliberation.OnTaskFinishedListener;
import com.battlelancer.seriesguide.sync.SgSyncAdapter;
import com.battlelancer.seriesguide.ui.BaseActivity;
import com.battlelancer.seriesguide.util.TaskManager;
import com.battlelancer.seriesguide.util.Utils;
import com.uwetrottmann.androidutils.AndroidUtils;
import com.uwetrottmann.seriesguide.R;

import java.io.File;

/**
 * Helps users migrate their show database to the free version of SeriesGuide. When using
 * SeriesGuide X a backup assistant and install+launch the free version assistant is shown.
 * When using any other version an import assistant is shown.
 */
public class MigrationActivity extends BaseActivity implements JsonExportTask.OnTaskProgressListener, OnTaskFinishedListener {

    private static final String KEY_MIGRATION_OPT_OUT = "com.battlelancer.seriesguide.migration.optout";
    private static final String MARKETLINK_HTTP = "http://play.google.com/store/apps/details?id=com.battlelancer.seriesguide";
    private static final String PACKAGE_SERIESGUIDE = "com.battlelancer.seriesguide";
    private boolean mIsX;
    private ProgressBar mProgressBar;
    private Button mButtonBackup;
    private Button mButtonLaunch;
    private TextView mTextViewLaunchInstructions;
    private AsyncTask<Void, Integer, Integer> mTask;
    private Intent mLaunchIntentForPackage;
    private View.OnClickListener mSeriesGuideLaunchListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mLaunchIntentForPackage != null) {
                startActivity(mLaunchIntentForPackage);
            }
        }
    };
    private View.OnClickListener mSeriesGuideInstallListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            // launch SeriesGuide Play Store page
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(MARKETLINK_HTTP));
            Utils.tryStartActivity(MigrationActivity.this, intent, true);
        }
    };

    public static boolean isQualifiedForMigration(Context context) {
        if (Utils.getChannel(context) != Utils.SGChannel.X) {
            return false;
        }
        return !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_MIGRATION_OPT_OUT,
                false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_migration);

        mIsX = Utils.getChannel(this) == Utils.SGChannel.X;

        setupActionBar();
        setupViews();

        // do not show the migration activity by force again
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean(KEY_MIGRATION_OPT_OUT, true)
                .commit();
    }

    private void setupActionBar() {
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    private void setupViews() {
        mProgressBar = (ProgressBar) findViewById(R.id.progressBarMigration);

        /**
         * Change from backup to import tool whether we use X or any other version (internal beta,
         * free).
         */
        ((TextView) findViewById(R.id.textViewMigrationBackupInstructions))
                .setText(mIsX ? R.string.migration_backup : R.string.migration_import);

        mButtonBackup = (Button) findViewById(R.id.buttonMigrationExport);
        mButtonBackup.setText(mIsX ? R.string.migration_action_backup
                : R.string.migration_action_import);
        mButtonBackup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mIsX) {
                    // backup shows
                    mTask = new JsonExportTask(MigrationActivity.this, MigrationActivity.this,
                            MigrationActivity.this,
                            true, false);
                } else {
                    // import shows
                    mTask = new JsonImportTask(MigrationActivity.this, MigrationActivity.this,
                            false);
                }
                mTask.execute();

                mProgressBar.setVisibility(View.VISIBLE);

                preventUserInput(true);
            }
        });

        mTextViewLaunchInstructions = (TextView) findViewById(R.id.textViewMigrationLaunchInstructions);
        mButtonLaunch = (Button) findViewById(R.id.buttonMigrationLaunch);
    }

    @Override
    protected void onStart() {
        super.onStart();
        validateLaunchStep();
    }

    @Override
    protected void onDestroy() {
        // clean up backup task
        if (mTask != null && mTask.getStatus() != AsyncTask.Status.FINISHED) {
            mTask.cancel(true);
        }
        mTask = null;

        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void validateLaunchStep() {
        if (!mIsX) {
            // don't show launcher if importing
            setLauncherVisibility(false);
            return;
        }

        // check if SeriesGuide is already installed
        mLaunchIntentForPackage = getPackageManager().getLaunchIntentForPackage(PACKAGE_SERIESGUIDE);
        boolean isSeriesGuideInstalled = mLaunchIntentForPackage != null;

        // prepare next step
        mTextViewLaunchInstructions.setText(isSeriesGuideInstalled ? R.string.migration_launch : R.string.migration_install);
        mButtonLaunch.setText(isSeriesGuideInstalled ? R.string.migration_action_launch : R.string.migration_action_install);
        mButtonLaunch.setOnClickListener(isSeriesGuideInstalled ? mSeriesGuideLaunchListener : mSeriesGuideInstallListener);

        // decide whether to show next step
        setLauncherVisibility(hasRecentBackup());
    }

    private void preventUserInput(boolean isLockdown) {
        // toggle buttons enabled state
        mButtonBackup.setEnabled(!isLockdown);
        mButtonLaunch.setEnabled(!isLockdown);
    }

    private void setLauncherVisibility(boolean isVisible) {
        mTextViewLaunchInstructions.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        mButtonLaunch.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onProgressUpdate(Integer... values) {
        if (mProgressBar == null) {
            return;
        }
        mProgressBar.setIndeterminate(values[0] == values[1]);
        mProgressBar.setMax(values[0]);
        mProgressBar.setProgress(values[1]);
    }

    @Override
    public void onTaskFinished() {
        mProgressBar.setVisibility(View.GONE);
        preventUserInput(false);
        validateLaunchStep();
    }

    private boolean hasRecentBackup() {
        if (AndroidUtils.isExtStorageAvailable()) {
            // ensure at least show JSON is available
            File path = JsonExportTask.getExportPath(false);
            File backup = new File(path, JsonExportTask.EXPORT_JSON_FILE_SHOWS);
            if (backup.exists() && backup.canRead()) {
                // not older than 24 hours?
                long lastModified = backup.lastModified();
                long now = System.currentTimeMillis();
                if (lastModified - now < 24 * DateUtils.HOUR_IN_MILLIS) {
                    return true;
                }
            }
        }

        return false;
    }
}
