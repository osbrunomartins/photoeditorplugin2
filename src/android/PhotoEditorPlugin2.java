package com.outsystems.photoeditorplugin2;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.content.PermissionChecker;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.ahmedadeltito.photoeditor.GalleryUtils;
import com.ahmedadeltito.photoeditor.PhotoEditorActivity;
import com.ahmedadeltito.photoeditor.UtilFunctions;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.ahmedadeltito.photoeditor.GalleryUtils.getDataColumn;

/**
 * This class echoes a string called from JavaScript.
 */
public class PhotoEditorPlugin2 extends CordovaPlugin {

    private Activity cordovaActivity;

    private static final int GALLERY_INTENT_CALLED = 0x1;
    private static final int GALLERY_KITKAT_INTENT_CALLED = 0x2;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE_GALLERY = 0x3;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE_CAMERA = 0x4;
    private static final int EDITING_IMAGE = 0x5;
    private final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

    protected static final int CAMERA_CODE = 0x0;

    protected Bitmap bitmap;
    protected boolean _taken;
    protected String selectedImagePath;
    protected String selectedOutputPath;

    private Uri selectedImageUri;

    private static final String PHOTO_PATH = "PhotoEditor";

    private CallbackContext callBack;

    private static final String VALID_ACTION = "edit";
    private static final String FROM = "from";
    private static final String FROM_GALLERY = "Gallery";
    private static final String FROM_CAMERA = "Camera";
    private static final String FROM_BASE64 = "Base64";

    private static final int INDEX_FROM = 0;
    private static final int INDEX_SOURCE = 1;
    private static final int INDEX_BASE64_FILE = 2;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals(VALID_ACTION)) {
            cordova.setActivityResultCallback(this);
            cordovaActivity = this.cordova.getActivity();
            callBack = callbackContext;

            String from = args.getString(INDEX_FROM);
            if(from.equals(FROM)){
                String source = args.getString(INDEX_SOURCE);
                if(source.equals(FROM_GALLERY)) {
                    this.openGalery();
                }else if(source.equals(FROM_CAMERA)) {
                    this.openCamera();
                }else if(source.equals(FROM_BASE64)){
                    String base64File = args.getString(INDEX_BASE64_FILE);
                    if(base64File.equals("")){
                        Log.d("ERROR", "Invalid Base64 File: File can not be empty.");
                        return false;
                    }
                    this.editFromBase64(base64File);
                }else{
                    Log.d("ERROR", "Invalid Source '"+source+"': Use 'Gallery', 'Camera' or 'Base64'.");
                    return false;
                }
            }else{
                Log.d("ERROR", "Invalid FROM '"+from+"': Use 'from'.");
                return false;
            }

            return true;
        }
        return false;
    }

    private void editFromBase64(String base64File){
        //TODO: Implementation missing...
        Log.d("ERROR", "NOT SUPPORTED");
    }

    private void openGalery(){
        int permissionCheck = PermissionChecker.checkCallingOrSelfPermission(cordovaActivity,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            if (!isKitKat) {
                Intent intent = new Intent();
                intent.setType("image/jpeg");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                //intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                cordovaActivity.startActivityForResult(
                        Intent.createChooser(intent, cordovaActivity.getString(com.ahmedadeltito.photoeditor.R.string.upload_picker_title)),
                        GALLERY_INTENT_CALLED);
            } else {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/jpeg");
                //intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                cordovaActivity.startActivityForResult(intent, GALLERY_KITKAT_INTENT_CALLED);
            }
        } else {
            showMenu(2);
        }
    }

    private void openCamera() {
        int permissionCheck = PermissionChecker.checkCallingOrSelfPermission(cordovaActivity,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            Intent photoPickerIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            photoPickerIntent.putExtra(MediaStore.EXTRA_OUTPUT, getOutputMediaFile());
            photoPickerIntent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
            photoPickerIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cordovaActivity.startActivityForResult(
                    Intent.createChooser(photoPickerIntent, "Select Picture"),
                    CAMERA_CODE);
        } else {
            showMenu(1);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (resultCode) {
            case Activity.RESULT_CANCELED:
                break;
            case Activity.RESULT_OK:
                if (requestCode == GALLERY_INTENT_CALLED || requestCode == GALLERY_KITKAT_INTENT_CALLED) {
                    processResultGallery(requestCode, intent);
                    /*
                    if (UtilFunctions.stringIsNotEmpty(selectedImagePath)) {
                        // decode image size
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(selectedImagePath, o);
                        // Find the correct scale value. It should be the power of
                        // 2.
                        int width_tmp = o.outWidth, height_tmp = o.outHeight;
                        Log.d("MediaActivity", "MediaActivity : image size : "
                                + width_tmp + " ; " + height_tmp);
                        final int MAX_SIZE = cordovaActivity.getResources().getDimensionPixelSize(
                                com.ahmedadeltito.photoeditor.R.dimen.image_loader_post_width);
                        int scale = 1;
                        // while (true) {
                        // if (width_tmp / 2 < MAX_SIZE
                        // || height_tmp / 2 < MAX_SIZE)
                        // break;
                        // width_tmp /= 2;
                        // height_tmp /= 2;
                        // scale *= 2;
                        // }
                        if (height_tmp > MAX_SIZE || width_tmp > MAX_SIZE) {
                            if (width_tmp > height_tmp) {
                                scale = Math.round((float) height_tmp
                                        / (float) MAX_SIZE);
                            } else {
                                scale = Math.round((float) width_tmp
                                        / (float) MAX_SIZE);
                            }
                        }
                        Log.d("MediaActivity", "MediaActivity : scaling image by factor : " + scale);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = scale;
                        bitmap = BitmapFactory.decodeFile(selectedImagePath, options);
                        //try( InputStream is = new URL(selectedImagePath).openStream() ) {

                            //bitmap = BitmapFactory.decodeStream(is);
                        //} catch (MalformedURLException e) {
                            //e.printStackTrace();
                        //} catch (IOException e) {
                            //e.printStackTrace();
                        //}

                        _taken = true;
                        onPhotoTaken();
                        System.gc();
                    }
                    */
                }else if(requestCode == CAMERA_CODE){
                    processResultCamera();
                }else if(requestCode == EDITING_IMAGE){
                    processResultEditor(intent);
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    private void processResultGallery(int requestCode, Intent intent){
        if (requestCode == GALLERY_INTENT_CALLED) {
            selectedImageUri = intent.getData();
            selectedImagePath = getPath(selectedImageUri);
        } else if (requestCode == GALLERY_KITKAT_INTENT_CALLED) {
            selectedImageUri = intent.getData();
            final int takeFlags = intent.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            // Check for the freshest data.
            if (selectedImageUri != null) {
                cordovaActivity.getContentResolver().takePersistableUriPermission(selectedImageUri, takeFlags);
                selectedImagePath = getPath(selectedImageUri);
            }
            onPhotoTaken();
        }
    }

    private void processResultCamera(){
        cordova.setActivityResultCallback(this);
        selectedImagePath = "";
        onPhotoTaken();
    }

    private void processResultEditor(Intent intent){
        String imagePath = intent.getStringExtra("imagePath");
        if(imagePath != null && imagePath.isEmpty()){
            Toast.makeText(cordovaActivity, "Image path is null or empty", Toast.LENGTH_LONG).show();
        }
        String base64 = getBase64FromPath(imagePath);
        if(base64 != null && !base64.isEmpty()) {
            callBack.success(base64);
        }else{
            Toast.makeText(cordovaActivity, "Invalid base64", Toast.LENGTH_LONG).show();
        }
    }


    @TargetApi(Build.VERSION_CODES.KITKAT)
    private String getPath(final Uri uri) {
        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(cordovaActivity, uri)) {
            // ExternalStorageProvider
            if (GalleryUtils.isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/"
                            + split[1];
                }
            }
            // DownloadsProvider
            else if (GalleryUtils.isDownloadsDocument(uri)) {
                String id = DocumentsContract.getDocumentId(uri);

                final Uri contentUri = ContentUris.withAppendedId( Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return GalleryUtils.getDataColumn(cordovaActivity, contentUri, null, null);
            }
            // MediaProvider
            else if (GalleryUtils.isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};

                return GalleryUtils.getDataColumn(cordovaActivity, contentUri, selection,
                        selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return GalleryUtils.getDataColumn(cordovaActivity, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    public void showMenu(final int caller) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this.cordova.getActivity());
        builder.setMessage( cordovaActivity.getString(com.ahmedadeltito.photoeditor.R.string.access_media_permissions_msg) );
        builder.setPositiveButton( cordovaActivity.getString(com.ahmedadeltito.photoeditor.R.string.continue_txt), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (caller == 1) {
                    ActivityCompat.requestPermissions(cordovaActivity,
                            new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE_CAMERA);
                    openCamera();
                } else {
                    ActivityCompat.requestPermissions(cordovaActivity,
                            new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE_GALLERY);
                    openGalery();
                }
            }
        });
        builder.setNegativeButton( cordovaActivity.getString(com.ahmedadeltito.photoeditor.R.string.not_now), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Toast.makeText(cordovaActivity, cordovaActivity.getString(com.ahmedadeltito.photoeditor.R.string.media_access_denied_msg), Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    private void onPhotoTaken() {
        Intent intent = new Intent(cordovaActivity, PhotoEditorActivity.class);
        intent.putExtra("selectedImagePath", selectedImagePath);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        cordova.setActivityResultCallback(this);
        cordovaActivity.startActivityForResult(intent, EDITING_IMAGE);
    }

    private boolean isSDCARDMounted() {
        String status = Environment.getExternalStorageState();
        return status.equals(Environment.MEDIA_MOUNTED);

    }

    private Uri getOutputMediaFile() {

        if (isSDCARDMounted()) {
            File mediaStorageDir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), PHOTO_PATH);
            // Create a storage directory if it does not exist
            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs()) {
                    Log.d("MediaAbstractActivity", cordovaActivity.getString(com.ahmedadeltito.photoeditor.R.string.directory_create_fail));
                    return null;
                }
            }
            // Create a media file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File mediaFile;
            selectedOutputPath = mediaStorageDir.getPath() + File.separator
                    + "IMG_" + timeStamp + ".jpg";
            Log.d("MediaAbstractActivity", "selected camera path "
                    + selectedOutputPath);
            mediaFile = new File(selectedOutputPath);
            Uri newMethod = FileProvider.getUriForFile(cordovaActivity, cordovaActivity.getApplicationContext().getPackageName()+".provider", mediaFile);

            return newMethod;//Uri.fromFile(mediaFile);
        } else {
            return null;
        }
    }

    private String getBase64FromPath(String path) {
        String base64 = "";
        try {/*from   w w w .  ja  va  2s  .  c om*/
            File file = new File(path);
            byte[] buffer = new byte[(int) file.length() + 100];
            @SuppressWarnings("resource")
            int length = new FileInputStream(file).read(buffer);
            base64 = Base64.encodeToString(buffer, 0, length,
                    Base64.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return base64;
    }
}
