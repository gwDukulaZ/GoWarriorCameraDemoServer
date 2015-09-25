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

import com.gowarrior.camera.server.MainActivity;

/* UploadModel handles the interaction between the Upload and TransferManager.
 * This also makes sure that the file that is uploaded has the same file extension
 *
 * One thing to note is that we always create a copy of the file we are given. This
 * is because we wanted to demonstrate pause/resume which is only possible with a
 * File parameter, but there is no reliable way to get a File from a Uri(mainly
 * because there is no guarantee that the Uri has an associated File).
 *
 * You can easily avoid this by directly using an InputStream instead of a Uri.
 */
public class UploadModel extends TransferModel {
    private static final String TAG = "GoWarriorCameraServer";

    private String mFileName;
    private String mStatus;
    private int mPrecent;
    private Uri mUri;

    public UploadModel(Context context, Uri uri) {
        super(context, uri);
        mUri = uri;
        Log.d(TAG,"the upload uri is "+ mUri.toString());

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


    public Runnable getUploadRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                upload();
            }
        };
    }






    public void upload() {




                try {


                    MainActivity.cloudTool.uploadFile(getUri());

                } catch (Exception e) {
                    Log.e(TAG, "Iwrong", e);
                }




    }

    @Override
    public void onProgress(String filename, String state, int percent) {

        mStatus = state;
        mFileName = filename;
        mPrecent = percent;
        Log.d(TAG,"uploadonprocess mStatus="+mStatus);
        Log.d(TAG,"uploadonprocess filename="+filename);
        Log.d(TAG,"uploadonprocess percent="+percent);

    }



}


