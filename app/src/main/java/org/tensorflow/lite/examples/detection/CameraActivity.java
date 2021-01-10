/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioManager;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;

import java.nio.ByteBuffer;
import java.util.Locale;

public abstract class CameraActivity extends AppCompatActivity
    implements OnImageAvailableListener,
        Camera.PreviewCallback,
        CompoundButton.OnCheckedChangeListener,
        View.OnClickListener {
  private static final Logger LOGGER = new Logger();

  private static final int PERMISSIONS_REQUEST = 1;

  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  protected int previewWidth = 0;
  protected int previewHeight = 0;
  private boolean debug = false;
  private Handler handler;
  private HandlerThread handlerThread;
  private boolean useCamera2API;
  private boolean isProcessingFrame = false;
  private byte[][] yuvBytes = new byte[3][];
  private int[] rgbBytes = null;
  private int yRowStride;
  private Runnable postInferenceCallback;
  private Runnable imageConverter;

  private LinearLayout bottomSheetLayout;
  private LinearLayout gestureLayout;
  private BottomSheetBehavior<LinearLayout> sheetBehavior;

  protected TextView frameValueTextView, cropValueTextView, inferenceTimeTextView;
  protected ImageView bottomSheetArrowImageView;
  private ImageView plusImageView, minusImageView;
  private SwitchCompat apiSwitchCompat;
  private TextView threadsTextView;

  //mycode
  public static TextToSpeech textToSpeech;

  public static boolean isMuted = false;
  private Button btnMutted;

  private boolean isStart = false;
  private Button btnStartService;
  public static boolean isServiceRun = false;

  private Button btnvibrate;
  public static Boolean isVibrate = false;
  public static Vibrator vibrator;
  private Button shutdown;
  private Button btnClass;
  public static AudioManager am;
  //mycodebg


  @Override
  protected void onCreate(final Bundle savedInstanceState) {
//    if(isMyServiceRunning(ForegroundService.class) == true){
//      stopService(new Intent(this,ForegroundService.class));
//      isStart = false;
//    }
    LOGGER.d("onCreate " + this);
    Log.d("hailo","onstart");
    super.onCreate(null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //membuat layar selalu nyala

    setContentView(R.layout.tfe_od_activity_camera); //call content view
    Toolbar toolbar = findViewById(R.id.toolbar);  //call toolbar
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayShowTitleEnabled(false); //menghilangkan title bar

    //mycode
    btnMutted = findViewById(R.id.buttonMute);
    btnStartService = findViewById(R.id.buttonStartService);
    btnvibrate = findViewById(R.id.buttonVibrate);
    vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
    btnClass = findViewById(R.id.btnClass);
    am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

    textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
      @Override
      public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
          int ttsLang = textToSpeech.setLanguage(Locale.US);

          if (ttsLang == TextToSpeech.LANG_MISSING_DATA
                  || ttsLang == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e("TTS", "The Language is not supported!");
          } else {
            Log.i("TTS", "Language Supported.");
          }
          Log.i("TTS", "Initialization success.");
        } else {
          Toast.makeText(getApplicationContext(), "TTS Initialization failed!", Toast.LENGTH_SHORT).show();
        }
      }
    });

    if(isMyServiceRunning(ForegroundService.class) == true)
    {
      Log.d("getmyservice","service - 1 " + isStart);
      stopService(new Intent(this,ForegroundService.class));
      Log.d("getmyservice","service - 2 " + isStart);
      isStart = false;
      btnStartService.setText("START SERVICE");
      btnStartService.setTextColor(btnStartService.getContext().getResources().getColor(R.color.design_default_color_primary_dark));
      btnStartService.setBackgroundColor(btnStartService.getContext().getResources().getColor(R.color.tfe_color_primary));

      //      isStart = true;
//      btnStartService.setText("STOP SERVICE");
//      btnStartService.setTextColor(Color.WHITE);
//      btnStartService.setBackgroundColor(Color.RED);
      isMuted = ForegroundService.getMute();

      if(isMuted == false){
        btnMutted.setText("MUTE");
        btnMutted.setTextColor(btnMutted.getContext().getResources().getColor(R.color.design_default_color_primary_dark));
        btnMutted.setBackgroundColor(btnMutted.getContext().getResources().getColor(R.color.tfe_color_primary));
      }
      else if(isMuted == true){
        btnMutted.setText("UNMUTE");
        btnMutted.setTextColor(Color.WHITE);
        btnMutted.setBackgroundColor(Color.RED);
      }

      isVibrate = ForegroundService.getVibrate();
      if(isVibrate == false){
        btnvibrate.setText("VIBRATE");
        btnvibrate.setTextColor(btnvibrate.getContext().getResources().getColor(R.color.design_default_color_primary_dark));
        btnvibrate.setBackgroundColor(btnvibrate.getContext().getResources().getColor(R.color.tfe_color_primary));
      }
      else if(isVibrate == true){
        btnvibrate.setText("STOP VIBRATE");
        btnvibrate.setTextColor(Color.WHITE);
        btnvibrate.setBackgroundColor(Color.RED);
      }
    }
    //mycode



    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }

    threadsTextView = findViewById(R.id.threads);
    plusImageView = findViewById(R.id.plus);
    minusImageView = findViewById(R.id.minus);
    apiSwitchCompat = findViewById(R.id.api_info_switch);
    bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);
    gestureLayout = findViewById(R.id.gesture_layout);
    sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
    bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow);

    ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
    vto.addOnGlobalLayoutListener(
        new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
              gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            } else {
              gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
            int width = bottomSheetLayout.getMeasuredWidth();
            int height = gestureLayout.getMeasuredHeight();

            sheetBehavior.setPeekHeight(height);
          }
        });
    sheetBehavior.setHideable(false);

    sheetBehavior.setBottomSheetCallback(
        new BottomSheetBehavior.BottomSheetCallback() {
          @Override
          public void onStateChanged(@NonNull View bottomSheet, int newState) {
            switch (newState) {
              case BottomSheetBehavior.STATE_HIDDEN:
                break;
              case BottomSheetBehavior.STATE_EXPANDED:
                {
                  bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);
                }
                break;
              case BottomSheetBehavior.STATE_COLLAPSED:
                {
                  bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                }
                break;
              case BottomSheetBehavior.STATE_DRAGGING:
                break;
              case BottomSheetBehavior.STATE_SETTLING:
                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                break;
            }
          }

          @Override
          public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });

    frameValueTextView = findViewById(R.id.frame_info);
    cropValueTextView = findViewById(R.id.crop_info);
    inferenceTimeTextView = findViewById(R.id.inference_info);

    apiSwitchCompat.setOnCheckedChangeListener(this);

    plusImageView.setOnClickListener(this);
    minusImageView.setOnClickListener(this);
  }

  //mycode
  public void shutdownOnClick(View view){
    Toast.makeText(this, "Application Shutdown", Toast.LENGTH_LONG).show();
    shudtdown();
  }
  public void shudtdown(){
    CameraActivity.this.moveTaskToBack(true);

    new CountDownTimer(3000, 1000) {

      public void onTick(long millisUntilFinished) {
      }

      public void onFinish() {
        finish();
        Log.d("jalankan","finish");
        System.exit(0);
        Log.d("jalankan","System 0");
      }
    }.start();
  }

  public void mute(View view){
    if(isMuted == true){ //jika unmute di press
      isMuted = false;
      //unmute voice alert
      btnMutted.setText("MUTE");
      btnMutted.setTextColor(btnMutted.getContext().getResources().getColor(R.color.design_default_color_primary_dark));
      btnMutted.setBackgroundColor(btnMutted.getContext().getResources().getColor(R.color.tfe_color_primary));
    }
    else if(isMuted == false){ //jika mute di press
      isMuted = true;
      //mute voice alert
      btnMutted.setText("UNMUTE");
      btnMutted.setTextColor(Color.WHITE);
      btnMutted.setBackgroundColor(Color.RED);
    }
  }


  public static boolean getMuted(){
    return isMuted;
  }

  private boolean isMyServiceRunning(Class<?> serviceClass) {
    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
    for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
      if (serviceClass.getName().equals(service.service.getClassName())) {
        isServiceRun = true;
        return true;
      }
    }
    isServiceRun = false;
    return false;
  }


  public static boolean getMyServiceRun(){return isServiceRun;}
  public void startService(View view){
    if(isStart == false){//jika start belum di tekan
//      isStart = true;
      //start start Foreground
//      btnStartService.setText("STOP SERVICE");
//      btnStartService.setTextColor(Color.WHITE);
//      btnStartService.setBackgroundColor(Color.RED);
      startService(new Intent(this,ForegroundService.class));
      CameraActivity.this.moveTaskToBack(true);
//      finish();
    }
    else if(isStart == true){//service sedang jalan
      isStart = false;
      //stop Foreground Service
      btnStartService.setText("START SERVICE");
      btnStartService.setTextColor(btnStartService.getContext().getResources().getColor(R.color.design_default_color_primary_dark));
      btnStartService.setBackgroundColor(btnStartService.getContext().getResources().getColor(R.color.tfe_color_primary));

      stopService(new Intent(view.getContext(),ForegroundService.class));
    }
  }

  public void vibrateOnClick(View view){
    if(isVibrate == false){
      isVibrate = true;
      vibrateNOW(10);
      //start vibrate
      btnvibrate.setText("STOP VIBRATE");
      btnvibrate.setTextColor(Color.WHITE);
      btnvibrate.setBackgroundColor(Color.RED);
      vibrateNOW(10);
    }
    else if(isVibrate == true){
      isVibrate = false;
      //stop vibarate
      btnvibrate.setText("VIBRATE");
      btnvibrate.setTextColor(btnvibrate.getContext().getResources().getColor(R.color.design_default_color_primary_dark));
      btnvibrate.setBackgroundColor(btnvibrate.getContext().getResources().getColor(R.color.tfe_color_primary));
    }
  }
  public static boolean getVibrate(){return isVibrate;}

  private void vibrateNOW(int time)
  {
    long[] pattern = {10,10};
      vibrator.vibrate(pattern,-1);
  }

  public void btnClassOnClick(View view){
    Log.d("settingjalan","onCLick masuk");
    Intent intent = new Intent(CameraActivity.this, settingActivity.class);
    startActivity(intent);
  }

// mycode

  protected int[] getRgbBytes() {
    imageConverter.run();
    return rgbBytes;
  }

  protected int getLuminanceStride() {
    return yRowStride;
  }

  protected byte[] getLuminance() {
    return yuvBytes[0];
  }

  /** Callback for android.hardware.Camera API */
  @Override
  public void onPreviewFrame(final byte[] bytes, final Camera camera) {
    Log.d("previewframe","ASLI jalan 3");
    if (isProcessingFrame) {
      LOGGER.w("Dropping frame!");
      return;
    }
    try {
      // Initialize the storage bitmaps once when the resolution is known.
      if (rgbBytes == null) {
        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        previewHeight = previewSize.height;
        previewWidth = previewSize.width;
        rgbBytes = new int[previewWidth * previewHeight];
        onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
      }
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      return;
    }

    isProcessingFrame = true;
    yuvBytes[0] = bytes;
    yRowStride = previewWidth;

    imageConverter =
        new Runnable() {
          @Override
          public void run() {
            ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
          }
        };

    postInferenceCallback =
        new Runnable() {
          @Override
          public void run() {
            camera.addCallbackBuffer(bytes);
            isProcessingFrame = false;
          }
        };
    processImage();
  }

  /** Callback for Camera2 API */
  @Override
  public   void onImageAvailable(final ImageReader reader) {
    // We need wait until we have some size from onPreviewSizeChosen
    if (previewWidth == 0 || previewHeight == 0) {
      return;
    }
    if (rgbBytes == null) {
      rgbBytes = new int[previewWidth * previewHeight];
    }

    try {
      final Image image = reader.acquireLatestImage();
      if (image == null) {
        return;
      }
      if (isProcessingFrame) {
        image.close();
        return;
      }
      isProcessingFrame = true;
      Trace.beginSection("imageAvailable");
      final Plane[] planes = image.getPlanes();
      fillBytes(planes, yuvBytes);
      yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();


      imageConverter =
          new Runnable() {
            @Override
            public void run() {
              ImageUtils.convertYUV420ToARGB8888(
                  yuvBytes[0],
                  yuvBytes[1],
                  yuvBytes[2],
                  previewWidth,
                  previewHeight,
                  yRowStride,
                  uvRowStride,
                  uvPixelStride,
                  rgbBytes);
            }
          };
      postInferenceCallback =
          new Runnable() {
            @Override
            public void run() {
              image.close();
              isProcessingFrame = false;
            }
          };

      processImage();
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }
    Trace.endSection();
  }

  @Override
  public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    Log.d("hailo","onStart");
    Log.d("inipercobaan","onStart()");
    super.onStart();
  }

  @Override
  public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();
    Log.d("hailo","onResume");
    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
    Log.d("inipercobaan","onResume()");
    isMuted = ForegroundService.getMute();

    if(isMuted == false){
      btnMutted.setText("MUTE");
      btnMutted.setTextColor(btnMutted.getContext().getResources().getColor(R.color.design_default_color_primary_dark));
      btnMutted.setBackgroundColor(btnMutted.getContext().getResources().getColor(R.color.tfe_color_primary));
    }
    else if(isMuted == true){
      btnMutted.setText("UNMUTE");
      btnMutted.setTextColor(Color.WHITE);
      btnMutted.setBackgroundColor(Color.RED);
    }

    isVibrate = ForegroundService.getVibrate();
    if(isVibrate == false){
      btnvibrate.setText("VIBRATE");
      btnvibrate.setTextColor(btnvibrate.getContext().getResources().getColor(R.color.design_default_color_primary_dark));
      btnvibrate.setBackgroundColor(btnvibrate.getContext().getResources().getColor(R.color.tfe_color_primary));
    }
    else if(isVibrate == true){
      btnvibrate.setText("STOP VIBRATE");
      btnvibrate.setTextColor(Color.WHITE);
      btnvibrate.setBackgroundColor(Color.RED);
    }
    stopService(new Intent(this,ForegroundService.class));

  }

  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);
    Log.d("hailo","onPause");
    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }
    Log.d("inipercobaan","onPause()");
    super.onPause();
  }

  @Override
  public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    Log.d("hailo","onStop");
    Log.d("inipercobaan","onstop()");
//    if(isServiceRun == true){
//      stopService(new Intent(CameraActivity.this,ForegroundService.class));
//      startService(new Intent(CameraActivity.this,ForegroundService.class));
//    }
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    Log.d("hailo","onDestroy");
    Log.d("inipercobaan","onDestroy()");
    super.onDestroy();
  }

  protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      final int requestCode, final String[] permissions, final int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERMISSIONS_REQUEST) {
      if (allPermissionsGranted(grantResults)) {
        setFragment();
      } else {
        requestPermission();
      }
    }
  }

  private static boolean allPermissionsGranted(final int[] grantResults) {
    for (int result : grantResults) {
      if (result != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
        Toast.makeText(
                CameraActivity.this,
                "Camera permission is required for this demo",
                Toast.LENGTH_LONG)
            .show();
      }
      requestPermissions(new String[] {PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
    }
  }

  // Returns true if the device supports the required hardware level, or better.
  private boolean isHardwareLevelSupported(
      CameraCharacteristics characteristics, int requiredLevel) {
    int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
    if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
      return requiredLevel == deviceLevel;
    }
    // deviceLevel is not LEGACY, can use numerical sort
    return requiredLevel <= deviceLevel;
  }

  private String chooseCamera() {
    final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try {
      for (final String cameraId : manager.getCameraIdList()) {
        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        final StreamConfigurationMap map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
          continue;
        }

        // Fallback to camera1 API for internal cameras that don't have full support.
        // This should help with legacy situations where using the camera2 API causes
        // distorted or otherwise broken previews.
        useCamera2API =
                  (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                          || isHardwareLevelSupported(
                          characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);

          LOGGER.i("Camera API lv2?: %s", useCamera2API);
          return cameraId;

      }
    } catch (CameraAccessException e) {
      LOGGER.e(e, "Not allowed to access camera");
    }

    return null;
  }

  protected void setFragment() {
    String cameraId = chooseCamera();

    Fragment fragment;
    if (useCamera2API) {
      CameraConnectionFragment camera2Fragment =
          CameraConnectionFragment.newInstance(
              new CameraConnectionFragment.ConnectionCallback() {
                @Override
                public void onPreviewSizeChosen(final Size size, final int rotation) {
                  Log.d("jalanduluan","OnpreviewSize");

                  previewHeight = size.getHeight();
                  previewWidth = size.getWidth();
                  Log.d("previewSizehaha","hasil asli : " +previewHeight + previewWidth);
                  CameraActivity.this.onPreviewSizeChosen(size, rotation);
                }
              },
              this,
              getLayoutId(),
              getDesiredPreviewFrameSize());

      camera2Fragment.setCamera(cameraId);
      fragment = camera2Fragment;
      Log.d("jalanduluan","fragment-1");
    } else {
      fragment =
          new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
    }

    getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  public boolean isDebug() {
    return debug;
  }

  protected void readyForNextImage() {
    if (postInferenceCallback != null) {
      postInferenceCallback.run();
    }
  }

  protected int getScreenOrientation() {
    switch (getWindowManager().getDefaultDisplay().getRotation()) {
      case Surface.ROTATION_270:
        return 270;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_90:
        return 90;
      default:
        return 0;
    }
  }

  @Override
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    setUseNNAPI(isChecked);
    if (isChecked) apiSwitchCompat.setText("NNAPI");
    else apiSwitchCompat.setText("TFLITE");
  }

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.plus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      if (numThreads >= 9) return;
      numThreads++;
      threadsTextView.setText(String.valueOf(numThreads));
      setNumThreads(numThreads);
    } else if (v.getId() == R.id.minus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      if (numThreads == 1) {
        return;
      }
      numThreads--;
      threadsTextView.setText(String.valueOf(numThreads));
      setNumThreads(numThreads);
    }
  }

  protected void showFrameInfo(String frameInfo) {
    frameValueTextView.setText(frameInfo);
  }

  protected void showCropInfo(String cropInfo) {
    cropValueTextView.setText(cropInfo);
  }

  protected void showInference(String inferenceTime) {
    inferenceTimeTextView.setText(inferenceTime);
  }

  protected abstract void processImage();

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

  protected abstract int getLayoutId();

  protected abstract Size getDesiredPreviewFrameSize();

  protected abstract void setNumThreads(int numThreads);

  protected abstract void setUseNNAPI(boolean isChecked);
}
