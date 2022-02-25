package com.davidread.photoexpresslegacy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * {@link MainActivity} represents a user interface where a snapped image's brightness may be
 * modified and saved to the device's external storage.
 */
public class MainActivity extends AppCompatActivity {

    /**
     * Int request code for the write to external storage permission request.
     */
    private final int REQUEST_EXTERNAL_WRITE_PERMISSIONS = 0;

    /**
     * Int request code for the take a photo request.
     */
    private final int REQUEST_TAKE_PHOTO = 1;

    /**
     * {@link String} path referencing the external storage image taken using the camera.
     */
    private String mPhotoPath;

    /**
     * {@link ImageView} to display an image preview to the user.
     */
    private ImageView mPhotoImageView;

    /**
     * {@link SeekBar} for modifying the brightness of the image.
     */
    private SeekBar mSeekBar;

    /**
     * {@link Button} for saving the altered image to the device's external storage.
     */
    private Button mSaveButton;

    /**
     * Int color value to multiply against RGB values when changing the image's brightness.
     */
    private int mMultColor = 0xffffffff;

    /**
     * Int color value to add to RGB values when changing the image's brightness.
     */
    private int mAddColor = 0;

    /**
     * Invoked once when this activity is created. It initializes member variables.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPhotoImageView = findViewById(R.id.photo);

        mSaveButton = findViewById(R.id.saveButton);
        mSaveButton.setEnabled(false);

        mSeekBar = findViewById(R.id.brightnessSeekBar);
        mSeekBar.setVisibility(View.INVISIBLE);

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                changeBrightness(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    /**
     * Invoked when an activity this {@link MainActivity} started finishes. It specifies what to do
     * after the camera intent started in {@link #takePhotoClick(View)} finishes. For successful
     * finishes, it displays the snapped photo thumbnail in {@link #mPhotoImageView}.
     *
     * @param requestCode Int request code supplied to
     *                    {@link #startActivityForResult(Intent, int)}, allowing you to identify
     *                    where this result originated from.
     * @param resultCode  Int result code returned by the finishing activity through its
     *                    {@link #setResult(int)}.
     * @param data        {@link Intent} that may contain data from the finishing activity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            displayPhoto();
            addPhotoToGallery();

            // Center seekbar at default lighting.
            mSeekBar.setProgress(100);
            mSeekBar.setVisibility(View.VISIBLE);
            mSaveButton.setEnabled(true);
        }
    }

    /**
     * Invoked when a request for permissions finishes. It specifies what to do after a permission
     * to write to external storage finishes. For permission grants, it starts a camera intent to
     * snap a photo.
     *
     * @param requestCode  Int request code supplied to {@link #requestPermissions(String[], int)},
     *                     allowing you to identify where this result originated from.
     * @param permissions  {@link String} array of permissions that were requested.
     * @param grantResults Int array of grant results for the corresponding permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_EXTERNAL_WRITE_PERMISSIONS: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhotoClick(null);
                }
                return;
            }
        }
    }

    /**
     * Checks if the write to external storage permission has been granted. If not, it requests this
     * permission.
     *
     * @return Whether the write to external storage permission has been granted.
     */
    private boolean hasExternalWritePermission() {
        String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this,
                permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission}, REQUEST_EXTERNAL_WRITE_PERMISSIONS);
            return false;
        }
        return true;
    }

    /**
     * Invoked when the "Take Photo" {@link Button} is clicked. It starts an intent to get a photo
     * from the device's camera to be altered in this {@link MainActivity}.
     */
    public void takePhotoClick(View view) {
        if (!hasExternalWritePermission()) return;

        // Create implicit intent.
        Intent photoCaptureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (photoCaptureIntent.resolveActivity(getPackageManager()) != null) {

            // Create the File where the photo should go.
            File photoFile = null;
            try {
                photoFile = createImageFile();
                mPhotoPath = photoFile.getAbsolutePath();
            } catch (IOException ex) {
                // Error occurred while creating the File.
                ex.printStackTrace();
            }

            // If the File was successfully created, start camera app.
            if (photoFile != null) {

                // Create content URI to grant camera app write permission to photoFile.
                Uri photoUri = FileProvider.getUriForFile(this,
                        "com.davidread.photoexpresslegacy.fileprovider",
                        photoFile);

                // Add content URI to intent.
                photoCaptureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

                // Start camera app.
                startActivityForResult(photoCaptureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    /**
     * Returns a {@link File} referring to an empty file in the device's external storage that may
     * be populated with an image.
     *
     * @return A {@link File} referring to an empty file on the device's external storage.
     */
    private File createImageFile() throws IOException {
        // Create a unique filename.
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFilename = "photo_" + timeStamp + ".jpg";

        // Create the file in the Pictures directory on external storage.
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return new File(storageDir, imageFilename);
    }

    /**
     * Displays a thumbnail in {@link #mPhotoImageView} of the image snapped by the camera intent
     * started in {@link #takePhotoClick(View)}.
     */
    private void displayPhoto() {
        // Get ImageView dimensions.
        int targetWidth = mPhotoImageView.getWidth();
        int targetHeight = mPhotoImageView.getHeight();

        // Get bitmap dimensions.
        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mPhotoPath, bitmapOptions);
        int photoWidth = bitmapOptions.outWidth;
        int photoHeight = bitmapOptions.outHeight;

        // Determine how much to scale down the image.
        int scaleFactor = Math.min(photoWidth / targetWidth, photoHeight / targetHeight);

        // Decode the image file into a smaller bitmap that fills the ImageView.
        bitmapOptions.inJustDecodeBounds = false;
        bitmapOptions.inSampleSize = scaleFactor;
        bitmapOptions.inPurgeable = true;
        Bitmap bitmap = BitmapFactory.decodeFile(mPhotoPath, bitmapOptions);

        // Display smaller bitmap.
        mPhotoImageView.setImageBitmap(bitmap);
    }

    /**
     * Sends a broadcast receiver to notify gallery apps that a new image has been written on
     * the device's external storage.
     */
    private void addPhotoToGallery() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File file = new File(mPhotoPath);
        Uri fileUri = Uri.fromFile(file);
        mediaScanIntent.setData(fileUri);
        sendBroadcast(mediaScanIntent);
    }

    /**
     * Alters the thumbnail image preview in {@link #mPhotoImageView} with the passed brightness
     * value. It saves the alteration values used to {@link #mAddColor} and {@link #mMultColor}.
     *
     * @param brightness An int between 0 and 99 to make the image darker. An int between 101 and
     *                   200 to make the image lighter.
     */
    private void changeBrightness(int brightness) {

        // 100 is the middle value.
        if (brightness > 100) {
            // Add color.
            float addMult = brightness / 100.0f - 1;
            mAddColor = Color.argb(255, (int) (255 * addMult), (int) (255 * addMult), (int) (255 * addMult));
            mMultColor = 0xffffffff;
        } else {
            // Scale color down.
            float brightMult = brightness / 100.0f;
            mMultColor = Color.argb(255, (int) (255 * brightMult), (int) (255 * brightMult), (int) (255 * brightMult));
            mAddColor = 0;
        }

        LightingColorFilter colorFilter = new LightingColorFilter(mMultColor, mAddColor);
        mPhotoImageView.setColorFilter(colorFilter);
    }

    /**
     * Invoked when the "Save" {@link Button} is clicked. It creates a new {@link SaveBitmapTask} to
     * overwrite the photo at file path {@link #mPhotoPath} with a brightness-altered version.
     */
    public void savePhotoClick(View view) {
        // Don't allow Save button to be pressed while image is saving.
        mSaveButton.setEnabled(false);

        // Save the image in a background thread.
        SaveBitmapTask saveTask = new SaveBitmapTask();
        saveTask.execute();
    }

    /**
     * {@link SaveBitmapTask} overwrites a photo with an altered version of the photo on a
     * background thread.
     */
    private class SaveBitmapTask extends AsyncTask<Void, Void, Boolean> {

        /**
         * Invoked by this {@link SaveBitmapTask} to run on a background thread. It overwrites the
         * image at the file path {@link #mPhotoPath} with a brightness-altered version of itself
         * given the brightness alteration values {@link #mMultColor} and {@link #mAddColor}.
         *
         * @return Whether the overwrite operation completes successfully.
         */
        @Override
        protected Boolean doInBackground(Void... params) {
            // Read original image.
            Bitmap bitmap = BitmapFactory.decodeFile(mPhotoPath, null);

            // Create a new bitmap with the same dimensions as the original.
            Bitmap alteredBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap
                    .getConfig());

            // Draw original bitmap on canvas and apply the color filter.
            Canvas canvas = new Canvas(alteredBitmap);
            Paint paint = new Paint();
            LightingColorFilter colorFilter = new LightingColorFilter(mMultColor, mAddColor);
            paint.setColorFilter(colorFilter);
            canvas.drawBitmap(bitmap, 0, 0, paint);

            // Save altered bitmap over the original image.
            File imageFile = new File(mPhotoPath);
            try {
                FileOutputStream outStream = new FileOutputStream(imageFile);
                alteredBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
                outStream.flush();
                outStream.close();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        /**
         * Invoked after {@link #doInBackground(Void...)} completes and is run on the main thread.
         * It pops a {@link Toast} indicating the success of the overwrite operation and re-enables
         * the {@link #mSaveButton}.
         *
         * @param result Whether the overwrite operation completed successfully.
         */
        @Override
        protected void onPostExecute(Boolean result) {
            // Tell the user what happened.
            if (result) {
                Toast.makeText(MainActivity.this, R.string.photo_saved, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, R.string.photo_not_saved, Toast.LENGTH_LONG).show();
            }
            mSaveButton.setEnabled(true);
        }
    }
}