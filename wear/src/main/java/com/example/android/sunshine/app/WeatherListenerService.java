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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * A {@link WearableListenerService} listening for data items from handheld for sunshine app
 */
public class WeatherListenerService extends WearableListenerService {
    private static final String TAG = "DigitalListenerService";

    private GoogleApiClient mGoogleApiClient;

    private static final String HIGH_TEMP_KEY = "hightemp";
    private static final String LOW_TEMP_KEY = "lowtemp";
    private static final String IMAGE_TEMP_KEY = "imagetemp";

    private static final String CURRENT_TEMP_PATH = "/currenttemp";

    @Override
    public void onCreate() {
        super.onCreate();
        LOGD(TAG, "onCreate: " );
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    /*
    Called when data item objects are created, changed, or deleted.
    An event on one side of a connection triggers this callback on both sides.
     */
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        LOGD(TAG, "onDataChanged: " + dataEvents);
//        if (mGoogleApiClient == null) {
//            mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
//                    .addOnConnectionFailedListener(this).addApi(Wearable.API).build();
//        }
        if (!mGoogleApiClient.isConnected()) {
            ConnectionResult connectionResult =
                    mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);

            if (!connectionResult.isSuccess()) {
                Log.e(TAG, "Failed to connect to GoogleApiClient.");
                return;
            }
        }
        // Loop through the events and send a message back to the node that created the data item.
        for (DataEvent event : dataEvents) {
            Uri uri = event.getDataItem().getUri();
            String path = uri.getPath();
            if (CURRENT_TEMP_PATH.equals(path)) {
                DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                String highTemp = dataMap.getString(HIGH_TEMP_KEY);
                String lowTemp = dataMap.getString(LOW_TEMP_KEY);
                new LoadBitmapAsyncTask().execute(dataMap.getAsset(IMAGE_TEMP_KEY));
                LOGD(TAG, "Max Temp=" + highTemp + "; Min Temp=" + lowTemp);
            }
        }

    }
    /*
    Called when the connection with the handheld or wearable is connected
     */
    @Override
    public void onPeerConnected(Node peer) {
        LOGD(TAG, "onPeerConnected: " + peer);
    }

    /*
    Called when the connection with the handheld or wearable is disconnected
     */
    @Override
    public void onPeerDisconnected(Node peer) {
        LOGD(TAG, "onPeerDisconnected: " + peer);
    }

    /*
   * Extracts {@link android.graphics.Bitmap} data from the
   * {@link com.google.android.gms.wearable.Asset}
   */
    private class LoadBitmapAsyncTask extends AsyncTask<Asset, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Asset... params) {

            if(params.length > 0) {

                Asset asset = params[0];

                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                        mGoogleApiClient, asset).await().getInputStream();

                if (assetInputStream == null) {
                    Log.w(TAG, "Requested an unknown Asset.");
                    return null;
                }
                return BitmapFactory.decodeStream(assetInputStream);

            } else {
                Log.e(TAG, "Asset must be non-null");
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {

            if(bitmap != null) {
                LOGD(TAG, "ASSET -> BITMAP done");
            }
        }
    }

    private static void LOGD(final String tag, String message) {
        Log.d(tag, message);
    }
}
