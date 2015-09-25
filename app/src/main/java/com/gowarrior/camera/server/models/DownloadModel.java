/*
 * Copyright 2010-2014 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.gowarrior.camera.server.models;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

/*
 * Class that encapsulates downloads, handling all the interaction with the
 * underlying Download and TransferManager
 */
public class DownloadModel extends TransferModel {
    private static final String TAG = "GoWarriorCameraServer";

    private String mFileName;
    private String mStatus;
    private int mPrecent;
    private Uri mUri;


    public DownloadModel(Context context, Uri uri) {
        super(context, uri);
        mUri = uri;

    }

    public void download() {

    }


    @Override
    public void onProgress(String filename, String state, int percent) {
        mStatus = state;
        mFileName = filename;
        mPrecent = percent;

        Log.d(TAG, "downloadonprocess mStatus=" + mStatus);
        Log.d(TAG,"downloadonprocess filename="+filename);
        Log.d(TAG,"downloadonprocess percent="+percent);
    }


    @Override
    public String getFileName() {
        return mFileName;
    }

    @Override
    public String getStatus() {
        return mStatus;
    }

    @Override
    public int getProgress() {
        return mPrecent;
    }

    @Override
    public Uri getUri() {
        return mUri;
    }
}
