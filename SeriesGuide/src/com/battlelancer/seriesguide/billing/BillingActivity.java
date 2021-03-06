
package com.battlelancer.seriesguide.billing;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.battlelancer.seriesguide.settings.AdvancedSettings;
import com.battlelancer.seriesguide.ui.BaseActivity;
import com.battlelancer.seriesguide.util.Utils;
import com.uwetrottmann.seriesguide.R;

public class BillingActivity extends BaseActivity {

    public static final String TAG = "BillingActivity";

    // enable debug logging, disable for production!
    public static final boolean DEBUG = false;

    // The SKU product id as set in the Developer Console
    public static final String SKU_X = "x_upgrade";

    // (arbitrary) request code for the purchase flow
    private static final int RC_REQUEST = 749758;

    private static final String SOME_STRING = "SURPTk9UQ0FSRUlGWU9VUElSQVRFVEhJUw==";

    private IabHelper mHelper;

    private View mProgressScreen;

    private View mContentContainer;

    private Button mUpgradeButton;

    private View mTextHasUpgrade;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.billing);

        setupViews();

        // do not set up in-app billing if already qualified for X upgrade
        if (updateUi()) {
            setWaitMode(false);
            return;
        }

        String key = getString(R.string.key_a) + getString(R.string.key_b)
                + getString(R.string.key_c) + getString(R.string.key_d);
        mHelper = new IabHelper(this, key);

        // enable debug logging (for a production application, you should set
        // this to false).
        mHelper.enableDebugLogging(DEBUG);

        Log.d(TAG, "Starting setup.");
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                Log.d(TAG, "Setup finished.");

                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    complain("Problem setting up In-app Billing: " + result);
                    return;
                }

                // Hooray, IAB is fully set up. Now, let's get an inventory of
                // stuff we own.
                Log.d(TAG, "Setup successful. Querying inventory.");
                mHelper.queryInventoryAsync(mGotInventoryListener);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);

        // Pass on the activity result to the helper for handling
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        }
        else {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mHelper != null) {
            mHelper.dispose();
        }
        mHelper = null;
    }

    // Listener that's called when we finish querying the items and
    // subscriptions we own
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d(TAG, "Query inventory finished.");
            if (result.isFailure()) {
                complain("Could not query inventory: " + result);
                return;
            }

            Log.d(TAG, "Query inventory was successful.");

            /*
             * Check for items we own. Notice that for each purchase, we check
             * the developer payload to see if it's correct! See
             * verifyDeveloperPayload().
             */

            // Do we have the premium upgrade?
            Purchase premiumPurchase = inventory.getPurchase(SKU_X);
            boolean hasXUpgrade = (premiumPurchase != null && verifyDeveloperPayload(premiumPurchase));
            Log.d(TAG, "User has " + (hasXUpgrade ? "X UPGRADE" : "NOT X UPGRADE"));

            // Save current state until we query again
            AdvancedSettings.setLastUpgradeState(BillingActivity.this, hasXUpgrade);

            updateUi();
            setWaitMode(false);
            Log.d(TAG, "Initial inventory query finished; enabling main UI.");
        }
    };

    /** Verifies the developer payload of a purchase. */
    public static boolean verifyDeveloperPayload(Purchase p) {
        String payload = p.getDeveloperPayload();

        /*
         * TODO: verify that the developer payload of the purchase is correct.
         * It will be the same one that you sent when initiating the purchase.
         * WARNING: Locally generating a random string when starting a purchase
         * and verifying it here might seem like a good approach, but this will
         * fail in the case where the user purchases an item on one device and
         * then uses your app on a different device, because on the other device
         * you will not have access to the random string you originally
         * generated. So a good developer payload has these characteristics: 1.
         * If two different users purchase an item, the payload is different
         * between them, so that one user's purchase can't be replayed to
         * another user. 2. The payload must be such that you can verify it even
         * when the app wasn't the one who initiated the purchase flow (so that
         * items purchased by the user on one device work on other devices owned
         * by the user). Using your own server to store and verify developer
         * payloads across app installations is recommended.
         */

        return SOME_STRING.equals(payload);
    }

    // User clicked the "Upgrade to Premium" button.
    private void onUpgradeAppButtonClicked(View button) {
        Log.d(TAG, "Upgrade button clicked; launching purchase flow for upgrade.");

        /*
         * TODO: for security, generate your payload here for verification. See
         * the comments on verifyDeveloperPayload() for more info.
         */
        String payload = SOME_STRING;

        setWaitMode(true);

        mHelper.launchPurchaseFlow(this, SKU_X, RC_REQUEST, mPurchaseFinishedListener, payload);
    }

    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);
            if (result.isFailure()) {
                complain("Error purchasing: " + result);
                setWaitMode(false);
                return;
            }
            if (!verifyDeveloperPayload(purchase)) {
                complain("Error purchasing. Authenticity verification failed.");
                setWaitMode(false);
                return;
            }

            Log.d(TAG, "Purchase successful.");

            if (purchase.getSku().equals(SKU_X)) {
                // bought the upgrade!
                Log.d(TAG, "Purchased X upgrade. Congratulating user.");
                // Save current state until we query again
                AdvancedSettings.setLastUpgradeState(BillingActivity.this, true);
                updateUi();
                setWaitMode(false);
            }
        }
    };

    private void setupViews() {
        mUpgradeButton = (Button) findViewById(R.id.buttonBillingGetUpgrade);
        mUpgradeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onUpgradeAppButtonClicked(v);
            }
        });

        mTextHasUpgrade = findViewById(R.id.textViewBillingExisting);

        mProgressScreen = findViewById(R.id.progressBarBilling);
        mContentContainer = findViewById(R.id.containerBilling);
    }

    private boolean updateUi() {
        // Only enable purchase button if the user does not have the upgrade yet
        boolean hasXupgrade = Utils.isSupporterChannel(this);
        mUpgradeButton.setEnabled(!hasXupgrade);
        mTextHasUpgrade.setVisibility(hasXupgrade ? View.VISIBLE : View.GONE);
        return hasXupgrade;
    }

    private void setWaitMode(boolean isActive) {
        mProgressScreen.setVisibility(isActive ? View.VISIBLE : View.GONE);
        mContentContainer.setVisibility(isActive ? View.GONE : View.VISIBLE);
    }

    private void complain(String message) {
        Log.e(TAG, message);
        alert("Error: " + message);
    }

    private void alert(String message) {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setMessage(message);
        bld.setNeutralButton(android.R.string.ok, null);
        Log.d(TAG, "Showing alert dialog: " + message);
        bld.create().show();
    }

}
