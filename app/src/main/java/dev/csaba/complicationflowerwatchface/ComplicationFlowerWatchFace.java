package dev.csaba.complicationflowerwatchface;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.rendering.ComplicationDrawable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class ComplicationFlowerWatchFace extends CanvasWatchFaceService {

    private static final String TAG = "ComplicationFlowerWatchFace";

    /*
     * Update rate in milliseconds for interactive mode. Updating once a second to advance seconds.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<ComplicationFlowerWatchFace.Engine> mWeakReference;

        EngineHandler(ComplicationFlowerWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            ComplicationFlowerWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                if (msg.what == MSG_UPDATE_TIME) {
                    engine.handleUpdateTimeMessage();
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        /* Handler to update the time once a second in interactive mode. */
        private final Handler updateTimeHandler = new EngineHandler(this);
        private Calendar calendar;
        private final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean registeredTimeZoneReceiver = false;
        private boolean muteMode;

        private Paint signPaint;

        private boolean ambient;

        /* Maps active complication ids to the data for that complication. Note: Data will only be
         * present if the user has chosen a provider via the settings activity for the watch face.
         */
        private SparseArray<ComplicationData> activeComplicationDataSparseArray;

        /* Maps complication ids to corresponding ComplicationDrawable that renders the
         * the complication data on the watch face.
         */
        private SparseArray<ComplicationDrawable> complicationDrawableSparseArray;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(ComplicationFlowerWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .build());

            calendar = Calendar.getInstance();

            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

            initializeComplications(displayMetrics);

            initializeWatchFace(displayMetrics);
        }

        private void initializeComplications(DisplayMetrics displayMetrics) {
            Log.d(TAG, "initializeComplications()");

            activeComplicationDataSparseArray =
                    new SparseArray<>(ComplicationConfigActivity.LOCATION_INDEXES.length);
            complicationDrawableSparseArray =
                    new SparseArray<>(ComplicationConfigActivity.LOCATION_INDEXES.length);

            // Creates a ComplicationDrawable for each location where the user can render a
            // complication on the watch face. In this watch face, we only create left and right,
            // but you could add many more.
            // All styles for the complications are defined in
            // drawable/custom_complication_styles.xml.
            Context appContext = getApplicationContext();
            for (int complicationId : ComplicationConfigActivity.LOCATION_INDEXES) {
                ComplicationDrawable complicationDrawable =
                        (ComplicationDrawable) getDrawable(R.drawable.custom_complication_styles);

                if (complicationDrawable != null) {
                    complicationDrawable.setContext(appContext);
                }

                // Adds new complications to a SparseArray to simplify setting styles and ambient
                // properties for all complications, i.e., iterate over them all.
                complicationDrawableSparseArray.put(complicationId, complicationDrawable);
            }

            setActiveComplications(ComplicationConfigActivity.LOCATION_INDEXES);
        }

        private void initializeWatchFace(DisplayMetrics displayMetrics) {
            // The sign paint
            signPaint = new Paint();
            signPaint.setColor(Color.YELLOW);
            signPaint.setStrokeWidth(15f);
            signPaint.setAntiAlias(true);
            signPaint.setStrokeCap(Paint.Cap.SQUARE);
        }

        @Override
        public void onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            /*
             * Whether the display supports fewer bits for each color in ambient mode.
             * When true, we disable anti-aliasing in ambient mode.
             */
            boolean lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            /*
             * Whether the display supports burn in protection in ambient mode.
             * When true, remove the background in ambient mode.
             */
            boolean mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

            // Updates complications to properly render in ambient mode based on the
            // screen's capabilities.
            ComplicationDrawable complicationDrawable;

            for (int complicationIndex : ComplicationConfigActivity.LOCATION_INDEXES) {
                complicationDrawable = complicationDrawableSparseArray.get(complicationIndex);

                if (complicationDrawable != null) {
                    complicationDrawable.setLowBitAmbient(lowBitAmbient);
                    complicationDrawable.setBurnInProtection(mBurnInProtection);
                }
            }
        }

        @Override
        public void onComplicationDataUpdate(
                int complicationId, ComplicationData complicationData) {
            Log.d(TAG, "onComplicationDataUpdate() id: " + complicationId);

            // Adds/updates active complication data in the array.
            activeComplicationDataSparseArray.put(complicationId, complicationData);

            // Updates correct ComplicationDrawable with updated data.
            ComplicationDrawable complicationDrawable =
                    complicationDrawableSparseArray.get(complicationId);
            complicationDrawable.setComplicationData(complicationData);

            invalidate();
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Log.d(TAG, String.format("OnTapCommand(%d, %d, %d)", tapType, x, y));
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    int tappedComplicationIndex = getTappedComplicationIndex(x, y);
                    if (tappedComplicationIndex != -1) {
                        onComplicationTap(tappedComplicationIndex);
                    }
                    break;
            }
            if (tapType != TAP_TYPE_TAP) {
                invalidate();
            }
        }

        /*
         * Determines if tap inside a complication area or returns -1.
         */
        private int getTappedComplicationIndex(int x, int y) {

            long currentTimeMillis = System.currentTimeMillis();

            for (int complicationIndex : ComplicationConfigActivity.LOCATION_INDEXES) {
                ComplicationData complicationData =
                        activeComplicationDataSparseArray.get(complicationIndex);

                if (complicationData != null
                    && complicationData.isActive(currentTimeMillis)
                    && complicationData.getType() != ComplicationData.TYPE_NOT_CONFIGURED
                    && complicationData.getType() != ComplicationData.TYPE_EMPTY)
                {
                    ComplicationDrawable complicationDrawable =
                            complicationDrawableSparseArray.get(complicationIndex);
                    Rect complicationBoundingRect = complicationDrawable.getBounds();

                    if (complicationBoundingRect.width() > 0) {
                        if (complicationBoundingRect.contains(x, y)) {
                            return complicationIndex;
                        }
                    } else {
                        Log.e(TAG, "Not a recognized complication id.");
                    }
                }
            }
            return -1;
        }

        private boolean hasAnyComplicationConfigured() {
            for (int complicationIndex : ComplicationConfigActivity.LOCATION_INDEXES) {
                ComplicationData complicationData =
                        activeComplicationDataSparseArray.get(complicationIndex);

                if (complicationData != null
                    && complicationData.getType() != ComplicationData.TYPE_NOT_CONFIGURED
                    && complicationData.getType() != ComplicationData.TYPE_EMPTY)
                {
                    return true;
                }
            }
            return false;
        }

        // Fires PendingIntent associated with complication (if it has one).
        private void onComplicationTap(int complicationIndex) {
            Log.d(TAG, "onComplicationTap()");

            ComplicationData complicationData =
                    activeComplicationDataSparseArray.get(complicationIndex);

            if (complicationData != null) {

                if (complicationData.getTapAction() != null) {
                    try {
                        complicationData.getTapAction().send();
                    } catch (PendingIntent.CanceledException e) {
                        Log.e(TAG, "onComplicationTap() tap action error: " + e);
                    }

                } else if (complicationData.getType() == ComplicationData.TYPE_NO_PERMISSION) {

                    // Watch face does not have permission to receive complication data, so launch
                    // permission request.
                    ComponentName componentName =
                            new ComponentName(
                                    getApplicationContext(), CanvasWatchFaceService.class);

                    Intent permissionRequestIntent =
                            ComplicationHelperActivity.createPermissionRequestHelperIntent(
                                    getApplicationContext(), componentName);

                    startActivity(permissionRequestIntent);
                }

            } else {
                Log.d(TAG, "No PendingIntent for complication " + complicationIndex + ".");
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            ambient = inAmbientMode;

            updateWatchHandStyle();

            // Update drawable complications' ambient state.
            // Note: ComplicationDrawable handles switching between active/ambient colors, we just
            // have to inform it to enter ambient mode.

            for (int complicationIndex : ComplicationConfigActivity.LOCATION_INDEXES) {
                ComplicationDrawable complicationDrawable =
                    complicationDrawableSparseArray.get(complicationIndex);
                complicationDrawable.setInAmbientMode(ambient);
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (ambient) {
                signPaint.setAntiAlias(false);
            } else {
                signPaint.setAntiAlias(true);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (muteMode != inMuteMode) {
                muteMode = inMuteMode;
                signPaint.setAlpha(inMuteMode ? 100 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Calculates location bounds for top and bottom circular complication rows.
             * Please note, no long text complications in this watch face.
             *
             * We suggest using at least 1/4 of the screen width for circular (or squared)
             * complications and 2/3 of the screen width for wide rectangular complications for
             * better readability.
             */

            // For most Wear devices, width and height are the same, so we just chose one (width).
            int diameter = width / 3;
            int radius = diameter / 2;
            int gap = (int)(0.267949192 * radius);

            for (int complicationIndex : ComplicationConfigActivity.LOCATION_INDEXES) {
                int verticalOffset;
                switch (complicationIndex) {
                    case 0:
                    case 5:
                        verticalOffset = gap;
                        break;
                    case 1:
                    case 4:
                    case 6:
                        verticalOffset = diameter;
                        break;
                    case 2:
                    case 3:
                    default:
                        verticalOffset = width - diameter - gap;
                        break;
                }
                int horizontalOffset;
                switch (complicationIndex) {
                    case 0:
                    case 2:
                        horizontalOffset = radius + diameter;
                        break;
                    case 1:
                        horizontalOffset = diameter * 2;
                        break;
                    case 3:
                    case 5:
                        horizontalOffset = radius;
                        break;
                    case 4:
                        horizontalOffset = 0;
                        break;
                    case 6:
                    default:
                        horizontalOffset = diameter;
                        break;
                }

                Rect complicationBounds =
                    // Left, Top, Right, Bottom
                    new Rect(horizontalOffset, verticalOffset,
                        horizontalOffset + diameter,verticalOffset + diameter);

                Log.d(TAG, String.format("Complication %d bounds, %d x %d %d x %d",
                        complicationIndex, complicationBounds.left, complicationBounds.top,
                        complicationBounds.width(), complicationBounds.height()));

                ComplicationDrawable complicationDrawable =
                        complicationDrawableSparseArray.get(complicationIndex);
                complicationDrawable.setBounds(complicationBounds);
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            super.onDraw(canvas, bounds);

            long now = System.currentTimeMillis();
            calendar.setTimeInMillis(now);

            drawBackground(canvas);
            drawComplications(canvas, now);
            if (!hasAnyComplicationConfigured())
                drawSign(canvas, bounds);
        }

        private void drawBackground(Canvas canvas) {
            canvas.drawColor(Color.BLACK);
        }

        private void drawComplications(Canvas canvas, long currentTimeMillis) {
            for (int complicationIndex : ComplicationConfigActivity.LOCATION_INDEXES) {
                ComplicationDrawable complicationDrawable =
                        complicationDrawableSparseArray.get(complicationIndex);

                complicationDrawable.draw(canvas, currentTimeMillis);
            }
        }

        private void drawSign(Canvas canvas, Rect bounds) {
            int signSize = bounds.centerX() / 10;
            canvas.drawLine(
                    bounds.centerX() - signSize,
                    bounds.centerY(),
                    bounds.centerX() + signSize,
                    bounds.centerY(),
                    signPaint);
            canvas.drawLine(
                    bounds.centerX(),
                    bounds.centerY() - signSize,
                    bounds.centerX(),
                    bounds.centerY() + signSize,
                    signPaint);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            ComplicationFlowerWatchFace.this.registerReceiver(timeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = false;
            ComplicationFlowerWatchFace.this.unregisterReceiver(timeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #updateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #updateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !ambient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
