/*
 * Copyright (C) 2014 The Android Open Source Project
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
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.io.FileInputStream;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {

    private static final String TAG = "CanvasWatchFaceService";

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /** Alpha value for drawing time when in mute mode. */
    static final int MUTE_ALPHA = 100;

    /** Alpha value for drawing time when not in mute mode. */
    static final int NORMAL_ALPHA = 255;

    /**
     * Update rate in milliseconds for interactive mode. We update twice a second (for colons)
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = 500;

    /**
     * Update rate in milliseconds for mute mode. We update every minute, like in ambient mode.
     */
    private static final long MUTE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;


    @Override
    public Engine onCreateEngine() {
        Log.d(TAG, "onCreateEngine");
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        /** How often {@link #mUpdateTimeHandler} ticks in milliseconds.
         * May need to change in mute mode, so have a global here
         * */
        long mInteractiveUpdateRateMs = INTERACTIVE_UPDATE_RATE_MS; // 0.5 seconds, declared on top

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        // Graphic objects
        Paint mBackgroundPaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mAmPmPaint;
        Paint mColonPaint;
        float mColonWidth;
        boolean mMute;
        //Periodic timer
        boolean mAmbient;
//        Time mTime;
        Calendar mCalendar;
        //Time zone change receiver
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
            }
        };
        float mXOffset;
        float mYOffset;
        float mLineHeight;
        boolean mShouldDrawColons;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        static final String AM_STRING = "AM";
        static final String PM_STRING = "PM";
        static final String COLON_STRING = ":";

        // BEGIN vars for sunshine

        String mHighTemp = "";
        String mLowTemp = "";
        Bitmap mWeatherBitmap = null;

        private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Global var weather - HighTemp: " + mHighTemp
                        + " LowTemp: " + mLowTemp);
                // Get extra data included in the Intent
                String bitmapFilename = intent.getStringExtra("bitmapFilename");
                mHighTemp = intent.getStringExtra("highTemp");
                mLowTemp = intent.getStringExtra("lowTemp");
                Log.d(TAG, "Received weather - HighTemp: " + mHighTemp
                        + " LowTemp: " + mLowTemp + "bitmapFile: " + bitmapFilename);
                // Load bitmap from local-file
                try {
                    if(bitmapFilename.equals("bitmap.png")) {
                        FileInputStream is = SunshineWatchFace.this.openFileInput(bitmapFilename);
                        mWeatherBitmap = BitmapFactory.decodeStream(is);
                        is.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        Paint mHighTempPaint;
        Paint mLowTempPaint;
        float mBitmapBoundExtraX;
        float mBitmapBoundExtraY;
        // END vars for sunshine

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE) // variable size peek cards
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE) // only show for interruptive notifs
                    .setShowSystemUiTime(false) // disable the systemUItime since watch is doing its own time representation for us
                    .build());
            // initialise your resources
            // very important to do as much of your allocation and initialisation here as possible
            // so setting paint styles and loading and reloading large bitmaps must be done here
            // you definitely dont want to be doing expensive operations in the main draw code for your watch face,
            // no allocating or freeing objects since that will cause the refresh performance to suffer
            Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mHourPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mHourPaint.setTypeface(BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(resources.getColor(R.color.digital_text));
            mSecondPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mAmPmPaint = createTextPaint(resources.getColor(R.color.digital_am_pm));
            mColonPaint = createTextPaint(resources.getColor(R.color.digital_colons));
            mHighTempPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mLowTempPaint = createTextPaint(resources.getColor(R.color.digital_temperature_low));;

            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);

            // We are registering an observer (mMessageReceiver) to receive Intents
            // with actions named "custom-event-name".
            LocalBroadcastManager.getInstance(SunshineWatchFace.this).registerReceiver(
                    mMessageReceiver, new IntentFilter("weatherProcessed"));
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);

            // unregister listener
            LocalBroadcastManager.getInstance(SunshineWatchFace.this).unregisterReceiver(
                    mMessageReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float temperatureTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round_temperature : R.dimen.digital_text_size_temperature);
            float amPmSize = resources.getDimension(isRound
                    ? R.dimen.digital_am_pm_size_round : R.dimen.digital_am_pm_size);
            mBitmapBoundExtraX = resources.getDimension(isRound
                    ? R.dimen.digital_bitmap_bound_extrax_round : R.dimen.digital_bitmap_bound_extrax);
            mBitmapBoundExtraY = resources.getDimension(isRound
                    ? R.dimen.digital_bitmap_bound_extray_round : R.dimen.digital_bitmap_bound_extray);

            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mSecondPaint.setTextSize(textSize);
            mAmPmPaint.setTextSize(amPmSize);
            mColonPaint.setTextSize(textSize);
            mLowTempPaint.setTextSize(temperatureTextSize);
            mHighTempPaint.setTextSize(temperatureTextSize);

            mColonWidth = mColonPaint.measureText(COLON_STRING);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            // do not use bold font on display with burn in issues
            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mHourPaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        // called whenever a switch is made between modes
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            // changing the paint styles so that they have different colors depending on the mode
            if (inAmbientMode) {
                mBackgroundPaint.setColor(Color.BLACK);
                mHourPaint.setColor(Color.WHITE);
                mMinutePaint.setColor(Color.WHITE);
                mSecondPaint.setColor(Color.GRAY); // in Ambient, seconds dont show up, can be ignored
                mAmPmPaint.setColor(Color.WHITE);
                mColonPaint.setColor(Color.GRAY);
                mHighTempPaint.setColor(Color.GRAY);
                mLowTempPaint.setColor(Color.GRAY);
            } else {
                Resources resources = SunshineWatchFace.this.getResources();
                mBackgroundPaint.setColor(resources.getColor(R.color.background));
                mHourPaint.setColor(resources.getColor(R.color.digital_text));
                mMinutePaint.setColor(resources.getColor(R.color.digital_text));
                mSecondPaint.setColor(resources.getColor(R.color.digital_text));
                mAmPmPaint.setColor(resources.getColor(R.color.digital_am_pm));
                mColonPaint.setColor(resources.getColor(R.color.digital_colons));
                mHighTempPaint.setColor(resources.getColor(R.color.digital_text));
                mLowTempPaint.setColor(resources.getColor(R.color.digital_temperature_low));;
            }

            // detect if watch supports low bit mode
            // if it does we toggle the anti aliasing flags since that is not allowed on these watches in ambient mode
            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mSecondPaint.setAntiAlias(antiAlias);
                mAmPmPaint.setAntiAlias(antiAlias);
                mColonPaint.setAntiAlias(antiAlias);
            }

            invalidate();

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onInterruptionFilterChanged: " + interruptionFilter);
            }
            super.onInterruptionFilterChanged(interruptionFilter);

            // If user doesn't want any notifications to be displayed
            boolean inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE;
            // We only need to update once a minute in mute mode.
            setInteractiveUpdateRateMs(inMuteMode ? MUTE_UPDATE_RATE_MS : INTERACTIVE_UPDATE_RATE_MS);

            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                int alpha = inMuteMode ? MUTE_ALPHA : NORMAL_ALPHA;
                mHourPaint.setAlpha(alpha);
                mMinutePaint.setAlpha(alpha);
                mColonPaint.setAlpha(alpha);
                mAmPmPaint.setAlpha(alpha);
                invalidate();
            }
        }

        public void setInteractiveUpdateRateMs(long updateRateMs) {
            if (updateRateMs == mInteractiveUpdateRateMs) {
                return;
            }
            mInteractiveUpdateRateMs = updateRateMs;

            // Stop and restart the timer so the new update rate takes effect immediately.
            if (shouldTimerBeRunning()) {
                updateTimer();
            }
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        private String getAmPmString(int amPm) {
            return amPm == Calendar.AM ? AM_STRING : PM_STRING;
        }
        // where all the drawing happens for your watch face
        // need to run this very quickly since it will be called a lot
        // so hopefully you have done all your prep work in onCreate
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now); // set pre-created calender obj to current system time
//            boolean is24Hour = DateFormat.is24HourFormat(SunshineWatchFace.this);
            boolean is24Hour = false;
            // Show colons for the first half of each second so the colons blink on when the time
            // updates.
            mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500;

            // Draw the background.
            /*
             fill the background with solid color,
             and use the bounds obj to provide the dimensions
             always use the bounds obj to calculate the width, height and center of the display
             it ll work properly even on devices with an inset chin at the bottom
             important in analog watch faces where hands must come from center of the display
             canvas obj has many methods for drawing, drawRect draws rectangles,
             draw text draws text for you*
             */
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw the hours.
            float x = mXOffset;
            String hourString;
            if (is24Hour) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }
            // control how this drawwing is done, using the paint obj provided as last argument
            // these were configured in onCreate
            canvas.drawText(hourString, x, mYOffset, mHourPaint);
            x += mHourPaint.measureText(hourString);

            // In ambient and mute modes, always draw the first colon. Otherwise, draw the
            // first colon for the first half of each second.
            if (isInAmbientMode() || mMute || mShouldDrawColons) {
                canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);
            }
            x += mColonWidth;

            // Draw the minutes.
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            canvas.drawText(minuteString, x, mYOffset, mMinutePaint);
            x += mMinutePaint.measureText(minuteString);

            // In unmuted interactive mode, draw a second blinking colon followed by the seconds.
            // Otherwise, if we're in 12-hour mode, draw AM/PM
            if (!isInAmbientMode() && !mMute) {
                if (mShouldDrawColons) {
                    canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);
                }
                x += mColonWidth;
                canvas.drawText(formatTwoDigitNumber(
                        mCalendar.get(Calendar.SECOND)), x, mYOffset, mSecondPaint);
            } else if (!is24Hour) {
                x += mColonWidth;
                canvas.drawText(getAmPmString(
                        mCalendar.get(Calendar.AM_PM)), x, mYOffset, mAmPmPaint);
            }

            if(mHighTemp.length() > 0 && mLowTemp.length() > 0 && mWeatherBitmap != null) {
                x = bounds.centerX();
                x -= mHighTempPaint.measureText(mHighTemp);
                canvas.drawText(mHighTemp, x, mYOffset + mLineHeight, mHighTempPaint);
                x += mHighTempPaint.measureText(mHighTemp);
                x += mColonWidth; // just a small arbitrary spacing
                canvas.drawText(mLowTemp, x, mYOffset + mLineHeight, mLowTempPaint);
                x += mLowTempPaint.measureText(mLowTemp);
                x += mColonWidth;

                canvas.drawBitmap(mWeatherBitmap, bounds.centerX()-mWeatherBitmap.getWidth() + mBitmapBoundExtraX, bounds.centerY() + mBitmapBoundExtraY, null);
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate(); // Schedules a call to onDraw
            // call invalidate to force a refresh
            // and then decide if we should schedule another update later on
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                // if we decide, we set an update to occur in a fixed number of milliseconds
                long delayMs = mInteractiveUpdateRateMs
                        - (timeMs % mInteractiveUpdateRateMs);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

    }

}
