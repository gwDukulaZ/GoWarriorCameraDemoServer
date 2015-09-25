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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.gowarrior.camera.server.models.DownloadModel;
import com.gowarrior.camera.server.models.TransferModel;
import com.gowarrior.camera.server.models.UploadModel;


/*
 * This view handles user interaction with a single transfer, such as giving the
 * option to pause and abort, and also showing the user the progress of the transfer.
 */
public class TransferView extends LinearLayout  {
    private static final String TAG = "GoWarriorCameraServer";
    private Context mContext;
    private TransferModel mModel;
    private TextView mText;

    private ProgressBar mProgress;
    private Handler mHandle = null;
    private boolean alreadydone = false;

    public TransferView(Context context, TransferModel model) {
        super(context);
        LayoutInflater.from(context).inflate(
                com.gowarrior.camera.server.R.layout.transfer_view,
                this,
                true);

        mContext = context;
        mModel = model;

        mText = ((TextView) findViewById(com.gowarrior.camera.server.R.id.text));

        mProgress = ((ProgressBar) findViewById(com.gowarrior.camera.server.R.id.progress));





        refresh();
    }

    public void setHandle(Handler hd) {mHandle = hd;};

    /* refresh method for public use */
    public void refresh() {
        refresh(mModel.getStatus());
    }

    /*
     * We use this method within the class so that we can have the UI update
     * quickly when the user selects something
     */
    private void refresh(String status) {
        Log.d(TAG,"!!!!!!!the status is "+ status);

        if (status != null) {
            int progress = 0;
            if (status.equals("IN_PROGRESS")) {
                progress = mModel.getProgress();

                mText.setText(mModel.getFileName());
                mProgress.setProgress(progress);

            } else if (status.equals("COMPLETE")) {
                progress = 100;




                if (mModel instanceof DownloadModel) {
                    // if download completed, show option to open file
                        Button button = (Button) findViewById(R.id.open);
                        button.setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // get the file extension
                                viewIt();
                            }
                        });
                        button.setVisibility(View.VISIBLE);
                        if (!alreadydone) {
                            alreadydone = true;
                            //viewIt();
                        }
                } else if (mModel instanceof UploadModel) {
                    if (!mModel.getdone()) {
                        String fpath = mModel.getUri().toString().substring(7);
                        if (mHandle != null) {
                            Message msg = mHandle.obtainMessage(Constants.Snapshot_UploadDone, 0, 0, fpath);
                            mHandle.sendMessage(msg);
                            Log.v(TAG, "send msg to notify upload " + fpath + " finish");
                            mModel.setdone(true);
                        }
                    }
                }
                mText.setText(mModel.getFileName());
                mProgress.setProgress(progress);


            }
        }


    }

    private void viewIt() {
        MimeTypeMap m = MimeTypeMap.getSingleton();
        String mimeType = m.getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(mModel.getUri().toString()));
        try {
            // try opening activity to open file
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(mModel.getUri(), mimeType);
            Log.v(TAG, "mimetype="+mimeType+" file="+mModel.getUri().toString());
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.d(TAG, "", e);
            // if file fails to be opened, show error
            // message
            Toast.makeText(mContext, R.string.nothing_found_to_open_file, Toast.LENGTH_SHORT).show();
        }
    }









}
