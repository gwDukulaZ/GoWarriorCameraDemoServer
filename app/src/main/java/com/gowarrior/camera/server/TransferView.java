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

package com.gowarrior.camera.server;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.gowarrior.camera.server.utils.CloudTool;


/*
 * This view handles user interaction with a single transfer, such as giving the
 * option to pause and abort, and also showing the user the progress of the transfer.
 */
public class TransferView extends LinearLayout implements CloudTool.CloudToolListener {
    private static final String TAG = "GoWarriorCameraServer";
    private Context mContext;
    private String mFileName;

    private TextView mText;

    private ProgressBar mProgress;








    public TransferView(Context context,String filename) {
        super(context);
        LayoutInflater.from(context).inflate(
                com.gowarrior.camera.server.R.layout.transfer_view,
                this,
                true);

        mContext = context;
        mFileName = filename;


        mText = ((TextView) findViewById(com.gowarrior.camera.server.R.id.text));

        mProgress = ((ProgressBar) findViewById(com.gowarrior.camera.server.R.id.progress));


      //TODO
        onProgress(mFileName,"START",0);
    }



    @Override
    public void onProgress(String filename, String state, int percent) {
        if("IN_PROGRESS".equalsIgnoreCase(state)){
            updateProcSate(filename,percent);
        }else if("COMPLETE".equalsIgnoreCase(state)){
            percent = 100;
            updateProcSate(filename,percent);
        }
    }

    public void updateProcSate(String fileName,int percent){

        mText.setText(fileName);
        mProgress.setProgress(percent);
    }
}
