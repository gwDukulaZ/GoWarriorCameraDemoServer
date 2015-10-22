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

import android.os.Environment;

public class Constants {
    public static final String UPLOAD_FROM = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/webcam";
    public static final String DOWNLOAD_TO = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/snapshot";

    public static final int Snapshot_Auto = 1;
    public static final int Snapshot_Active = 2;
    public static final int Snapshot_PIR = 3;
    public static final int Snapshot_Remote = 4;
    public static final int Snapshot_FaceDetect = 5;
    public static final int Snapshot_UploadStart = 6;
    public static final int Snapshot_UploadDone = 7;
    public static final int Snapshot_RemoteControl = 8;
    public static final int Snapshot_RemoteConnect = 9;
    public static final int Snapshot_UserKey1 = 10;
    public static final int Snapshot_UserKey2 = 11;
}
