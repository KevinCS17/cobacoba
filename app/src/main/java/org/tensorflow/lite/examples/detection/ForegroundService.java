package org.tensorflow.lite.examples.detection;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.Trace;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.tensorflow.lite.examples.detection.customview.AutoFitTextureView;
import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.tflite.Detector;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.tensorflow.lite.examples.detection.CameraConnectionFragment.chooseOptimalSize;

public class ForegroundService extends Service{

    @NonNull
    public static final String ACTION_START = "com.kevin.background.service.action.START";
    @NonNull
    public static final String ACTION_START_WITH_PREVIEW = "com.kevin.background.service.action.STARTWITHPREVIEW";
//    private final int layout;
    //UI
    private View rootView = null;
    private WindowManager wm = null;

    //camera-2-related-stuff
    private CameraManager cameraManager = null;
    private Size previewSize = null;
    private CameraDevice cameraDevice = null;
    private CaptureRequest captureRequest = null;
    private CameraCaptureSession captureSession = null;
    private ImageReader imageReader = null;

    // You can start service in 2 modes - 1.) with preview 2.) without preview (only bg processing)
    private boolean shouldShowPreview = true;

    public final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private String cameraId;
    public Handler backgroundHandler;
    private Handler handler;
    private HandlerThread handlerThread;

    //cameraAcitivity
    protected int previewWidth = 0;
    protected int previewHeight = 0;
    private int[] rgbBytes = null;
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int yRowStride;
    private Runnable imageConverter;
    private Runnable postInferenceCallback;

    //detectorActivity
    private long timestamp = 0;
    OverlayView trackingOverlay;
    private boolean computingDetection = false;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Matrix frameToCropTransform;
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private Detector detector;
    private long lastProcessingTimeMs;
    private Bitmap cropCopyBitmap = null;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;
    private static final float TEXT_SIZE_DIP = 10;
    private BorderedText borderedText;
    private Integer sensorOrientation;
    private static final boolean MAINTAIN_ASPECT = false;

    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;

    private Size inputSize;
    private AutoFitTextureView textureView;
    private static final String FRAGMENT_DIALOG = "dialog";
    private ConnectionCallback cameraConnectionCallback = null;

    private Camera camera;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader previewReader;
    private CaptureRequest previewRequest;

    private boolean useCamera2API;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final int MINIMUM_PREVIEW_SIZE = 320;


    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final CaptureResult partialResult) {                        Log.d("jangandulu","lagi capture");
                }

                @Override
                public void onCaptureCompleted(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final TotalCaptureResult result) {                        Log.d("jangandulu","capture complete");
                }
            };

    public interface ConnectionCallback {
        void onPreviewSizeChosen(Size size, int cameraRotation);
    }

    private void configureTransform(final int viewWidth, final int viewHeight) {
//        if (null == textureView || null == previewSize || null == activity) {
//            return;
//        }
        if (null == textureView || null == previewSize) {
            return;
        }
        final int rotation = cameraActivity.getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale =
                    Math.max(
                            (float) viewHeight / previewSize.getHeight(),
                            (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {
//                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {}
            };
    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
//                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

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

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }


    private enum DetectorMode {
        TF_OD_API;
    }
    private boolean isHardwareLevelSupported(
            CameraCharacteristics characteristics, int requiredLevel) {
        int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return requiredLevel == deviceLevel;
        }
        // deviceLevel is not LEGACY, can use numerical sort
        return requiredLevel <= deviceLevel;
    }

    public void setCamera(String cameraId) {
        this.cameraId = cameraId;
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
//                LOGGER.i("Camera API lv2?: %s", useCamera2API);
                Log.d("haio","Camera API lvl2?: "+ useCamera2API);
                return cameraId;
            }
        } catch (CameraAccessException e) {
//            LOGGER.e(e, "Not allowed to access camera");
            Log.d("haio","Not Allowed to access camera");
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
                                    previewHeight = size.getHeight();
                                    previewWidth = size.getWidth();
                                    onPreviewSizeChosen(size, rotation);
                                }
                            },
                            cameraActivity,
                            cameraActivity.getLayoutId(),
                            cameraActivity.getDesiredPreviewFrameSize());
            Log.d("haio","ini kedua : "+ cameraId);
            setCamera(cameraId);
            camera2Fragment.setCamera(cameraId);
            fragment = camera2Fragment;
        } else {
            fragment =
                    new LegacyCameraConnectionFragment((Camera.PreviewCallback) this, cameraActivity.getLayoutId(), cameraActivity.getDesiredPreviewFrameSize());
        }

//        getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    }

    CameraActivity cameraActivity = new CameraActivity() {
        @Override
        protected void processImage() {
            ++timestamp;
            final long currTimestamp = timestamp;
            trackingOverlay.postInvalidate();

            // No mutex needed as this method is not reentrant.
            if (computingDetection) {
                readyForNextImage();
                return;
            }
            computingDetection = true;
            Log.d("cobaService","Preparing image " + currTimestamp + " for detection in bg thread.");

            rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

            readyForNextImage();

            final Canvas canvas = new Canvas(croppedBitmap);
            canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
            // For examining the actual TF input.
            if (SAVE_PREVIEW_BITMAP) {
                ImageUtils.saveBitmap(croppedBitmap);
            }

            runInBackground(
                    new Runnable() {
                        @Override
                        public void run() {
                            Log.d("cobaService","Running detection on image " + currTimestamp);
                            final long startTime = SystemClock.uptimeMillis();
                            final List<Detector.Recognition> results = detector.recognizeImage(croppedBitmap);
                            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                            final Canvas canvas = new Canvas(cropCopyBitmap);
                            final Paint paint = new Paint();
                            paint.setColor(Color.RED);
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setStrokeWidth(2.0f);

                            float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                            switch (MODE) {
                                case TF_OD_API:
                                    minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                    break;
                            }

                            final List<Detector.Recognition> mappedRecognitions =
                                    new ArrayList<Detector.Recognition>();

                            for (final Detector.Recognition result : results) {
                                final RectF location = result.getLocation();
                                if (location != null && result.getConfidence() >= minimumConfidence) {
                                    canvas.drawRect(location, paint);

                                    cropToFrameTransform.mapRect(location);

                                    result.setLocation(location);
                                    mappedRecognitions.add(result);
                                }
                            }

                            tracker.trackResults(mappedRecognitions, currTimestamp);
                            trackingOverlay.postInvalidate();

                            computingDetection = false;

                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            showFrameInfo(previewWidth + "x" + previewHeight);
                                            showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                            showInference(lastProcessingTimeMs + "ms");
                                        }
                                    });
                        }
                    });
        }

        @Override
        protected void onPreviewSizeChosen(Size size, int rotation) {
            final float textSizePx =
                    TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
            borderedText = new BorderedText(textSizePx);
            borderedText.setTypeface(Typeface.MONOSPACE);

            tracker = new MultiBoxTracker(this);

            int cropSize = TF_OD_API_INPUT_SIZE;

            try {
                detector =
                        TFLiteObjectDetectionAPIModel.create(
                                getAssets(),
                                TF_OD_API_MODEL_FILE,
                                TF_OD_API_LABELS_FILE,
                                TF_OD_API_INPUT_SIZE,
                                TF_OD_API_IS_QUANTIZED);
                cropSize = TF_OD_API_INPUT_SIZE;
            } catch (final IOException e) {
                e.printStackTrace();
//                LOGGER.e(e, "Exception initializing Detector!");
                Toast toast =
                        Toast.makeText(
                                getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
                toast.show();
                finish();
            }

            previewWidth = size.getWidth();
            previewHeight = size.getHeight();

            sensorOrientation = rotation - getScreenOrientation();
//            LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

//            LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
            rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
            croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

            frameToCropTransform =
                    ImageUtils.getTransformationMatrix(
                            previewWidth, previewHeight,
                            cropSize, cropSize,
                            sensorOrientation, MAINTAIN_ASPECT);

            cropToFrameTransform = new Matrix();
            frameToCropTransform.invert(cropToFrameTransform);

            trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
            trackingOverlay.addCallback(
                    new OverlayView.DrawCallback() {
                        @Override
                        public void drawCallback(final Canvas canvas) {
                            tracker.draw(canvas);
                            if (isDebug()) {
                                tracker.drawDebug(canvas);
                            }
                        }
                    });

            tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
        }

        @Override
        protected int getLayoutId() {
            return R.layout.tfe_od_camera_connection_fragment_tracking;
        }

        @Override
        protected Size getDesiredPreviewFrameSize() {
            return DESIRED_PREVIEW_SIZE;
        }

        @Override
        protected void setNumThreads(int numThreads) {
            runInBackground(() -> detector.setNumThreads(numThreads));
        }

        @Override
        protected void setUseNNAPI(boolean isChecked) {
            runInBackground(() -> detector.setUseNNAPI(isChecked));
        }
    };



    private ImageReader.OnImageAvailableListener imageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d("hailo","onImageAvailable2");
            Log.d("jangandulu", "avaible");
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
                final Image.Plane[] planes = image.getPlanes();
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

                cameraActivity.processImage();
            } catch (final Exception e) {
//                LOGGER.e(e, "Exception!");
                Trace.endSection();
                return;
            }
            Trace.endSection();
        }
    };

    private CameraDevice.StateCallback stateCallback =
            new CameraDevice.StateCallback() {
        @Override
        public void onOpened(final CameraDevice cd) {
            Log.d("jangandulu", "open");
            cameraOpenCloseLock.release();
            cameraDevice = cd;
//            createCaptureSession();
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(final CameraDevice cd) {
            Log.d("jangandulu", "dc");
            cameraOpenCloseLock.release();
            cd.close();
            cameraDevice = null;
        }

        @Override
        public void onError(final CameraDevice cd, int error) {
            Log.d("jangandulu", "error");
            cameraOpenCloseLock.release();
            cd.close();
            cameraDevice = null;
        }
    };


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    public int onStartCommand(@Nullable Intent intent, int flags, int startId){
        openCamera(300,300);
//        return super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }


    private void setUpCameraOutputs() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            final StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            previewSize =
                    chooseOptimalSize(
                            map.getOutputSizes(SurfaceTexture.class),
                            640,
                            480);
            // We fit the aspect ratio of TextureView to the size of preview we picked.
            final int orientation = getResources().getConfiguration().orientation;

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());

            } else {
//                textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());

            }
        } catch (final CameraAccessException e) {
//            LOGGER.e(e, "Exception!");
        } catch (final NullPointerException e) {

            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
//            CameraConnectionFragment.ErrorDialog.newInstance(getString(R.string.tfe_od_camera_error))
//                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            throw new IllegalStateException(getString(R.string.tfe_od_camera_error));
        }

//        cameraConnectionCallback.onPreviewSizeChosen(previewSize, sensorOrientation);
    }

    private void openCamera(int width, int height)
    {
        setUpCameraOutputs();
//        configureTransform(width, height);
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (final CameraAccessException e) {
//            LOGGER.e(e, "Exception!");
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void createCameraPreviewSession() {
        textureView = CameraConnectionFragment.getMyTextureview();
        Log.d("halo1","ini suram" + textureView);
        try {
            Log.d("halo1","ini service : " + textureView.getSurfaceTexture().toString());
            final SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // This is the output Surface we need to start preview.
            final Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            Log.d("cobaService","Opening camera preview: " + previewSize.getWidth() + "x" + previewSize.getHeight());

            // Create the reader for the preview frames.
            previewReader =
                    ImageReader.newInstance(
                            previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

            previewReader.setOnImageAvailableListener(imageListener, backgroundHandler);
            previewRequestBuilder.addTarget(previewReader.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, previewReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // Finally, we start displaying the camera preview.
                                previewRequest = previewRequestBuilder.build();
                                captureSession.setRepeatingRequest(
                                        previewRequest, captureCallback, backgroundHandler);
                            } catch (final CameraAccessException e) {
//                                LOGGER.e(e, "Exception!");
                            }
                        }

                        @Override
                        public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
                            Log.d("hailo","Failed");
//                            showToast("Failed");
                        }
                    },
                    null);
        } catch (final CameraAccessException e) {
//            LOGGER.e(e, "Exception!");
        }
    }

    public static boolean isMute = false;
    public static boolean isVibrate = false;
    @Override
    public void onCreate(){
        Toast.makeText(this, "Service Created", Toast.LENGTH_LONG).show();
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startMyOwnForeground();
        else
            startForeground(1, new Notification());
        isMute = CameraActivity.getMuted();
        isVibrate = CameraActivity.getVibrate();
    }

    public static boolean getVibrate(){return isVibrate;}
    public static boolean getMute(){return isMute;}

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startMyOwnForeground(){
        Intent intent = new Intent (this, DetectorActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,1,intent,PendingIntent.FLAG_UPDATE_CURRENT);

        String NOTIFICATION_CHANNEL_ID = "Tensorflow Lite";
        String channelName = "TFlite Background service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.RED);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("TFlite Background service")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(2, notification);
        setFragment();
//        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "Service Stopped", Toast.LENGTH_LONG).show();
        Log.d("jangandulu", "destroy");
        stopCamera();
    }

    protected void stopCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
        Log.d("jangandulu", "stopCamera");

        cameraDevice = null;
        imageReader = null;
    }
}
