/*
 * Copyright (C) The Android Open Source Project
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

package com.google.android.gms.samples.vision.barcodereader;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.app.Activity;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.vision.barcode.Barcode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.drive.Drive;

import static com.google.android.gms.drive.Drive.getDriveResourceClient;

/**
 * Main activity demonstrating how to pass extra parameters to an activity that
 * reads barcodes.
 */
public class MainActivity extends Activity implements View.OnClickListener {

    // use a compound button so either checkbox or switch widgets work.
    private CompoundButton autoFocus;
    private CompoundButton useFlash;
    private TextView statusMessage;
    private TextView barcodeValue;
    private TextView personnelID, personnelName, timeView;
    private ImageView personnelPhoto;
    //private TextView barcodeMulti;

    private static final int RC_BARCODE_CAPTURE = 9001;
    private static final String TAG = "BarcodeMain";

    private OutputStreamWriter csvWriter;
    private String outputFilename;

    private static final int REQUEST_CODE_SIGN_IN = 0;
    private DriveClient mDriveClient;
    private DriveResourceClient mDriveResourceClient;

    private String CSVText;
    private String driveFileName;

    private Personnel currentPerson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        signIn();

        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                1);

        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck== PackageManager.PERMISSION_GRANTED){
            //this means permission is granted and you can do read and write
        }else{
            //requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_PERMISSION);
        }


        statusMessage = (TextView) findViewById(R.id.status_message);
        barcodeValue = (TextView) findViewById(R.id.barcode_value);
        personnelID = (TextView) findViewById(R.id.ID);
        personnelName = (TextView) findViewById(R.id.Name) ;
        personnelPhoto = (ImageView) findViewById(R.id.portrait);
        timeView = (TextView) findViewById(R.id.Time);
        //testView = (TextView) findViewById(R.id.textView);
        /*barcodeMulti = (TextView) findViewById(R.id.barcode_collection);
        barcodeMulti.setScroller(new Scroller(this));
        barcodeMulti.setMaxLines(4);
        barcodeMulti.setVerticalScrollBarEnabled(true);
        barcodeMulti.setMovementMethod(new ScrollingMovementMethod());
        */

        //autoFocus = (CompoundButton) findViewById(R.id.auto_focus);
        //useFlash = (CompoundButton) findViewById(R.id.use_flash);

        findViewById(R.id.read_barcode).setOnClickListener(this);
        findViewById(R.id.upload).setOnClickListener(this);

        createCSVWriter();
        /*try {
            csvWriter.close();
        }
        catch (IOException e){
            Log.e(TAG,e.getMessage());
        }*/
    }

    private GoogleSignInClient buildGoogleSignInClient() {
        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestScopes(Drive.SCOPE_FILE)
                        .build();
        return GoogleSignIn.getClient(this, signInOptions);
    }

    private boolean findPersonID(String ID){
        String infoFilePath = getPublicAlbumStorageDir("").getAbsolutePath()+"/personnel info/info.csv";
        File file = new File(infoFilePath);


        StringBuilder text = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                int index = line.indexOf(ID);
                if (index>=0){
                    currentPerson = new Personnel(ID, line.substring(1+index+ID.length()), getPublicAlbumStorageDir("").getAbsolutePath());
                    return true;
                }
            }
            br.close();
        }
        catch (IOException e) {
            //You'll need to add proper error handling here
        }
        return false;

    }

    private void displayPersonInfo(){
        personnelName.setText("NAME: "+ currentPerson.name);
        personnelID.setText("ID: "+ currentPerson.ID);
        personnelPhoto.setImageBitmap(BitmapFactory.decodeFile(currentPerson.photoPath));
        SimpleDateFormat s = new SimpleDateFormat("HH:mm");
        String currentTime = s.format(Calendar.getInstance().getTime());
        timeView.setText(currentTime);
    }

    private void signIn() {
        Log.i(TAG, "Start sign in");
        GoogleSignInClient GoogleSignInClient = buildGoogleSignInClient();
        startActivityForResult(GoogleSignInClient.getSignInIntent(), REQUEST_CODE_SIGN_IN);
    }

    public void createCSVWriter() {
        String CSVHEADER = "DATE,TIME,ID\n";
        SimpleDateFormat s = new SimpleDateFormat("yyyyMMdd_HHmmss");
        if (!isExternalStorageWritable())
            Toast.makeText(getApplicationContext(), "External storage NOT ready", Toast.LENGTH_LONG).show();

        File externalDir = getPublicAlbumStorageDir("");
        outputFilename = externalDir.getAbsolutePath().toString() + '/' + s.format(Calendar.getInstance().getTime())+ ".csv";
        //outputFilename = externalDir.getAbsolutePath().toString() + '/' +"1.csv";


        File cvsOutput = new File(outputFilename);

        try {
            csvWriter = new OutputStreamWriter(new FileOutputStream(cvsOutput));
            csvWriter.write(CSVHEADER);
            csvWriter.flush();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public String readCSVFile(String filePath){
        File file = new File(filePath);


        StringBuilder text = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
        }
        catch (IOException e) {
            //You'll need to add proper error handling here
        }

        return text.toString();
    }

    public void writeRecord(String s0) {
        SimpleDateFormat s = new SimpleDateFormat("yyyy/MM/dd,HH:mm:ss,");
        String currentTime = s.format(Calendar.getInstance().getTime());
        try {
            csvWriter.write(currentTime + s0 + '\n');
            csvWriter.flush();
            //   csvWriter.close();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public  File getPublicAlbumStorageDir(String albumName) {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
               Environment.DIRECTORY_DOWNLOADS), albumName);

        //File file = new File (getExternalStoragePath(this,true)+"/Download",albumName);

        //if (!file.mkdirs()) {
        //Log.e(LOG_TAG, "Directory not created"); Log
        //}
        return file;
    }

    private static String getExternalStoragePath(Context mContext, boolean is_removable) {

        StorageManager mStorageManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
        Class<?> storageVolumeClazz = null;
        try {
            storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isRemovable = storageVolumeClazz.getMethod("isRemovable");
            Object result = getVolumeList.invoke(mStorageManager);
            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String path = (String) getPath.invoke(storageVolumeElement);
                boolean removable = (Boolean) isRemovable.invoke(storageVolumeElement);
                if (is_removable == removable) {
                    return path;
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void createDriveFile(String filePath) {

        CSVText = readCSVFile(filePath);
        File f = new File(filePath);
        driveFileName = f.getName();

        // [START drive_android_create_file]
        final Task<DriveFolder> rootFolderTask = mDriveResourceClient.getRootFolder();
        final Task<DriveContents> createContentsTask = mDriveResourceClient.createContents();
        Tasks.whenAll(rootFolderTask, createContentsTask)
                .continueWithTask(task -> {
                    DriveFolder parent = rootFolderTask.getResult();
                    DriveContents contents = createContentsTask.getResult();
                    OutputStream outputStream = contents.getOutputStream();
                    try (Writer writer = new OutputStreamWriter(outputStream)) {
                        writer.write(CSVText);

                    }

                    MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                            .setTitle(driveFileName)
                            .setMimeType("text/csv")
                            .setStarred(true)
                            .build();

                    return mDriveResourceClient.createFile(parent, changeSet, contents);
                })
                .addOnSuccessListener(this,
                        driveFile -> {
                            //showMessage(getString(R.string.file_created,
                            //        driveFile.getDriveId().encodeToString()));
                            //finish();
                        })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Unable to create file", e);
                    //showMessage(getString(R.string.file_create_error));
                    //finish();
                });
        // [END drive_android_create_file]
    }


    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.read_barcode) {
            // launch barcode activity.
            Intent intent = new Intent(this, BarcodeCaptureActivity.class);
            //intent.putExtra(BarcodeCaptureActivity.AutoFocus, autoFocus.isChecked());
            intent.putExtra(BarcodeCaptureActivity.AutoFocus, true);
            //intent.putExtra(BarcodeCaptureActivity.UseFlash, useFlash.isChecked());
            intent.putExtra(BarcodeCaptureActivity.UseFlash, false);

            startActivityForResult(intent, RC_BARCODE_CAPTURE);
        } else if (v.getId() == R.id.upload) {
            try {
                csvWriter.close();


                createDriveFile(outputFilename);

                createCSVWriter();

            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }

        }

    }

    /**
     * Called when an activity you launched exits, giving you the requestCode
     * you started it with, the resultCode it returned, and any additional
     * data from it.  The <var>resultCode</var> will be
     * {@link #RESULT_CANCELED} if the activity explicitly returned that,
     * didn't return any result, or crashed during its operation.
     * <p/>
     * <p>You will receive this call immediately before onResume() when your
     * activity is re-starting.
     * <p/>
     *
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode  The integer result code returned by the child activity
     *                    through its setResult().
     * @param data        An Intent, which can return result data to the caller
     *                    (various data can be attached to Intent "extras").
     * @see #startActivityForResult
     * @see #createPendingResult
     * @see #setResult(int)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    //statusMessage.setText(R.string.barcode_success);
                    statusMessage.setText("");
                    //barcodeValue.setText(barcode.displayValue);
                    //barcodeMulti.setText(barcode.displayValue);
                    //barcodeMulti.setText(barcode.displayValue, TextView.BufferType.NORMAL);
                    //barcodeMulti.setText(barcode.displayValue);
                    //barcodeMulti.append('\n'+barcode.displayValue);
                    if (findPersonID(barcode.displayValue)){
                        displayPersonInfo();
                    }
                    writeRecord(barcode.displayValue);
                    Log.d(TAG, "Barcode read: " + barcode.displayValue);
                } else {
                    statusMessage.setText(R.string.barcode_failure);
                    Log.d(TAG, "No barcode captured, intent data is null");
                }
            } else {
                statusMessage.setText(String.format(getString(R.string.barcode_error),
                        CommonStatusCodes.getStatusCodeString(resultCode)));
            }
        } else {
            if (requestCode == REQUEST_CODE_SIGN_IN) {
                Log.i(TAG, "Sign in request code");
                // Called after user is signed in.
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "Signed in successfully.");
                    // Use the last signed in account here since it already have a Drive scope.
                    mDriveClient = Drive.getDriveClient(this, GoogleSignIn.getLastSignedInAccount(this));
                    // Build a drive resource client.
                    mDriveResourceClient =
                            getDriveResourceClient(this, GoogleSignIn.getLastSignedInAccount(this));
                    //createDriveFile();
                } else {
                    Log.e(TAG, "Sign in error");
                }
            } else {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }
}
