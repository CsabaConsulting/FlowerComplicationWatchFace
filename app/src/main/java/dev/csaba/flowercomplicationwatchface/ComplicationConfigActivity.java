package dev.csaba.flowercomplicationwatchface;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.Nullable;

import android.preference.PreferenceManager;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.ComplicationProviderInfo;
import android.support.wearable.complications.ProviderChooserIntent;
import android.support.wearable.complications.ProviderInfoRetriever;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 * The watch-side config activity for {@link FlowerComplicationWatchFace}, which allows for setting
 * the left and right complications of watch face.
 */
public class ComplicationConfigActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "ComplicationConfigActivity";

    static final int COMPLICATION_CONFIG_REQUEST_CODE = 1001;

    private static final int[] SMALL_COMPLICATION_SUPPORTED_TYPES = {
        ComplicationData.TYPE_RANGED_VALUE,
        ComplicationData.TYPE_ICON,
        ComplicationData.TYPE_SHORT_TEXT,
        ComplicationData.TYPE_SMALL_IMAGE,
        ComplicationData.TYPE_LARGE_IMAGE,
    };

    private static final int MIN_LOCATION_INDEX = 0;
    private static final int MAX_LOCATION_INDEX = 6;
    public static final int[] LOCATION_INDEXES =
            IntStream.rangeClosed(MIN_LOCATION_INDEX, MAX_LOCATION_INDEX).toArray();
    private static final int[] BACKGROUND_RESOURCE_IDS = {
        R.id.complication_ne_background,
        R.id.complication_e_background,
        R.id.complication_se_background,
        R.id.complication_sw_background,
        R.id.complication_w_background,
        R.id.complication_nw_background,
        R.id.complication_center_background
    };
    private static final int[] COMPLICATION_RESOURCE_IDS = {
        R.id.complication_ne,
        R.id.complication_e,
        R.id.complication_se,
        R.id.complication_sw,
        R.id.complication_w,
        R.id.complication_nw,
        R.id.complication_center
    };

    public static final String COLOR_SCHEME_TAG = "colorScheme";
    private static final int MAX_COLOR_SCHEME = 3;
    private static final int[] COLOR_SELECTOR_BG_RESOURCE_IDS = {
        R.id.red_color_scheme_background1,
        R.id.red_color_scheme_background2,
        R.id.green_color_scheme_background1,
        R.id.green_color_scheme_background2,
        R.id.blue_color_scheme_background1,
        R.id.blue_color_scheme_background2
    };
    private static final int[] COLOR_SELECTOR_RESOURCE_IDS = {
        R.id.red_color_scheme1,
        R.id.red_color_scheme2,
        R.id.green_color_scheme1,
        R.id.green_color_scheme2,
        R.id.blue_color_scheme1,
        R.id.blue_color_scheme2
    };
    private static final String[] COLOR_SCHEME_IDS = { "r", "g", "b" };
    private static final Map<String, Integer> COLOR_SCHEME_REVERSE_INDEX =
        new HashMap<String, Integer>() {{
            put("r", 0);
            put("g", 1);
            put("b", 2);
        }};
    private static final int[] UNSELECTED_RESOURCE_IDS = {
        R.drawable.red_color_scheme,
        R.drawable.green_color_scheme,
        R.drawable.blue_color_scheme,
    };
    private static final int[] SELECTED_RESOURCE_IDS = {
        R.drawable.red_color_scheme_selected,
        R.drawable.green_color_scheme_selected,
        R.drawable.blue_color_scheme_selected,
    };

    // Selected complication id by user.
    private int selectedComplicationId;

    // ComponentName used to identify a specific service that renders the watch face.
    private ComponentName watchFaceComponentName;

    // Required to retrieve complication data from watch face for preview.
    private ProviderInfoRetriever providerInfoRetriever;

    private ImageView[] complicationBackgrounds;
    private ImageButton[] complications;

    private ImageView[] colorSelectorBackgrounds = new ImageView[MAX_COLOR_SCHEME * 2];
    private ImageButton[] colorSelectors = new ImageButton[MAX_COLOR_SCHEME * 2];

    private Drawable defaultAddComplicationDrawable;

    private String colorScheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_config);

        defaultAddComplicationDrawable = getDrawable(R.drawable.add_round_complication);

        selectedComplicationId = -1;

        Context appContext = getApplicationContext();
        watchFaceComponentName = new ComponentName(appContext, FlowerComplicationWatchFace.class);

        complicationBackgrounds = new ImageView[MAX_LOCATION_INDEX + 1];
        complications = new ImageButton[MAX_LOCATION_INDEX + 1];

        // Sets up left complication preview.
        for (int locationIndex : LOCATION_INDEXES) {
            complicationBackgrounds[locationIndex] =
                    findViewById(BACKGROUND_RESOURCE_IDS[locationIndex]);
            complications[locationIndex] =
                    findViewById(COMPLICATION_RESOURCE_IDS[locationIndex]);
            complications[locationIndex].setOnClickListener(this);

            // Sets default as "Add Complication" icon.
            complications[locationIndex].setImageDrawable(defaultAddComplicationDrawable);
            complicationBackgrounds[locationIndex].setVisibility(View.INVISIBLE);
        }

        colorScheme = getColorScheme();
        for (int selectorIndex = 0; selectorIndex < MAX_COLOR_SCHEME * 2; selectorIndex++) {
            colorSelectorBackgrounds[selectorIndex] =
                    findViewById(COLOR_SELECTOR_BG_RESOURCE_IDS[selectorIndex]);
            int colorSchemeIndex = selectorIndex / 2;
            if (colorScheme.equals(COLOR_SCHEME_IDS[colorSchemeIndex])) {
                int selectedResourceId = SELECTED_RESOURCE_IDS[colorSchemeIndex];
                colorSelectorBackgrounds[selectorIndex].setImageDrawable(
                        getResources().getDrawable(selectedResourceId, appContext.getTheme()));
            }
            colorSelectors[selectorIndex] =
                    findViewById(COLOR_SELECTOR_RESOURCE_IDS[selectorIndex]);
            colorSelectors[selectorIndex].setOnClickListener(this);
        }

        // Initialization of code to retrieve active complication data for the watch face.
        providerInfoRetriever =
                new ProviderInfoRetriever(appContext, Executors.newCachedThreadPool());
        providerInfoRetriever.init();

        retrieveInitialComplicationsData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Required to release retriever for active complication data.
        providerInfoRetriever.release();
    }

    public void retrieveInitialComplicationsData() {
        providerInfoRetriever.retrieveProviderInfo(
                new ProviderInfoRetriever.OnProviderInfoReceivedCallback() {
                    @Override
                    public void onProviderInfoReceived(
                            int watchFaceComplicationId,
                            @Nullable ComplicationProviderInfo complicationProviderInfo) {

                        Log.d(TAG, "onProviderInfoReceived: " + complicationProviderInfo);

                        updateComplicationViews(watchFaceComplicationId, complicationProviderInfo);
                    }
                },
                watchFaceComponentName,
                LOCATION_INDEXES);
    }

    @Override
    public void onClick(View view) {
        int complicationIndex = 0;
        for (ImageButton complication: complications) {
            if (view.equals(complication)) {
                launchComplicationHelperActivity(complicationIndex);
                break;
            }
            complicationIndex++;
        }
        int colorSelectorIndex = 0;
        for (ImageButton colorSelectorButton: colorSelectors) {
            if (view.equals(colorSelectorButton)) {
                int colorSchemeIndex = colorSelectorIndex / 2;
                if (!colorScheme.equals(COLOR_SCHEME_IDS[colorSchemeIndex])) {
                    int currentIndex = COLOR_SCHEME_REVERSE_INDEX.get(colorScheme);
                    Resources.Theme appTheme = getApplicationContext().getTheme();
                    int unselectedResourceId = UNSELECTED_RESOURCE_IDS[currentIndex];
                    colorSelectorBackgrounds[currentIndex * 2].setImageDrawable(
                            getResources().getDrawable(unselectedResourceId, appTheme));
                    colorSelectorBackgrounds[currentIndex * 2 + 1].setImageDrawable(
                            getResources().getDrawable(unselectedResourceId, appTheme));

                    int selectedResourceId = SELECTED_RESOURCE_IDS[colorSchemeIndex];
                    colorSelectorBackgrounds[colorSchemeIndex * 2].setImageDrawable(
                            getResources().getDrawable(selectedResourceId, appTheme));
                    colorSelectorBackgrounds[colorSchemeIndex * 2 + 1].setImageDrawable(
                            getResources().getDrawable(selectedResourceId, appTheme));

                    colorScheme = COLOR_SCHEME_IDS[colorSchemeIndex];
                    setColorScheme(COLOR_SCHEME_IDS[colorSchemeIndex]);
                    Log.d(TAG, "Set color scheme to " + COLOR_SCHEME_IDS[colorSchemeIndex]);
                }
                break;
            }
            colorSelectorIndex++;
        }
    }

    private SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    private String getColorScheme() {
        return getPreferences().getString(ComplicationConfigActivity.COLOR_SCHEME_TAG, "r");
    }

    private void setColorScheme(String colorScheme) {
        getPreferences().edit().putString(COLOR_SCHEME_TAG, colorScheme).apply();
    }

    // Verifies the watch face supports the complication location, then launches the helper
    // class, so user can choose their complication data provider.
    private void launchComplicationHelperActivity(int complicationIndex) {

        selectedComplicationId = complicationIndex;

        if (selectedComplicationId >= MIN_LOCATION_INDEX && selectedComplicationId <= MAX_LOCATION_INDEX) {
            Log.d(TAG, "launchComplicationHelperActivity for " + selectedComplicationId);

            startActivityForResult(
                    ComplicationHelperActivity.createProviderChooserHelperIntent(
                            getApplicationContext(),
                            watchFaceComponentName,
                            selectedComplicationId,
                            SMALL_COMPLICATION_SUPPORTED_TYPES),
                    ComplicationConfigActivity.COMPLICATION_CONFIG_REQUEST_CODE);

        } else {
            Log.d(TAG, "Complication not supported by watch face.");
        }
    }

    public void updateComplicationViews(
            int watchFaceComplicationId, ComplicationProviderInfo complicationProviderInfo) {
        Log.d(TAG, "updateComplicationViews(): id: " + watchFaceComplicationId);
        Log.d(TAG, "\tinfo: " + complicationProviderInfo);

        if (watchFaceComplicationId >= MIN_LOCATION_INDEX && watchFaceComplicationId <= MAX_LOCATION_INDEX) {
            if (complicationProviderInfo != null) {
                complications[watchFaceComplicationId].setImageIcon(complicationProviderInfo.providerIcon);
                complicationBackgrounds[watchFaceComplicationId].setVisibility(View.VISIBLE);
            } else {
                complications[watchFaceComplicationId].setImageDrawable(defaultAddComplicationDrawable);
                complicationBackgrounds[watchFaceComplicationId].setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == COMPLICATION_CONFIG_REQUEST_CODE && resultCode == RESULT_OK) {
            // Retrieves information for selected Complication provider.
            ComplicationProviderInfo complicationProviderInfo =
                    data.getParcelableExtra(ProviderChooserIntent.EXTRA_PROVIDER_INFO);
            Log.d(TAG, "Provider: " + complicationProviderInfo);

            if (selectedComplicationId >= 0) {
                updateComplicationViews(selectedComplicationId, complicationProviderInfo);
            }
        }
    }
}
