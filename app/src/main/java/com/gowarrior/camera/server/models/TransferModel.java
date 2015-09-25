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
import com.gowarrior.camera.server.utils.CloudTool.CloudToolListener;

import java.util.LinkedHashMap;


public abstract class TransferModel implements CloudToolListener {
    private static final String TAG = "GoWarriorCameraServer";



    // all TransferModels have associated id which is their key to sModels
    private static LinkedHashMap<Integer, TransferModel> sModels =
            new LinkedHashMap<Integer, TransferModel>();
    private static int sNextId = 1;


    private Context mContext;
    private Uri mUri;
    private int mId;
    private boolean done = false; // used for transfer done flag






    public static TransferModel getTransferModel(int id) {
        return sModels.get(id);
    }

    public static TransferModel[] getAllTransfers() {
        TransferModel[] models = new TransferModel[sModels.size()];
        TransferModel[] ret;
        try {
            ret = sModels.values().toArray(models);
        } catch (Exception e) {
            Log.e(TAG,"TransferModel getAllTransfers exception");
            e.printStackTrace();
            ret = null;
        }
        return ret;
    }

    public TransferModel(Context context, Uri uri ) {
        mContext = context;
        mUri = uri;
        MainActivity.cloudTool.setListener(this);

        mId = sNextId++;
        sModels.put(mId, this);

    }



    public int getId() {
        return mId;
    }





    public void setdone(boolean flag) {
        done = flag ;
    }

    public boolean getdone() {
        return done;
    }


    public abstract String getFileName();

    public abstract String getStatus();

    public abstract int getProgress();
    public  Uri getUri(){
        return mUri;
    }




    protected Context getContext() {
        return mContext;
    }


}
