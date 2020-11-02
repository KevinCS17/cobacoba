package org.tensorflow.lite.examples.detection;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import org.tensorflow.lite.examples.detection.customview.AutoFitTextureView;
import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.tflite.Detector;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class ForegroundService extends Service {

    private CameraDevice cameraDevice = null;
    private ImageReader imageReader = null;
    private Size previewSize = null;
    private CaptureRequest captureRequest = null;
    private AutoFitTextureView textureView;
    private boolean shouldPreview = false;
    private CameraCaptureSession captureSession = null;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Camera camera;
    protected int previewWidth = 0;
    protected int previewHeight = 0;
    private int[] rgbBytes = null;

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            final Size ActivityCamera = CameraConnectionFragment.getInputSize();
            initcam(ActivityCamera.getWidth(),ActivityCamera.getHeight());
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    protected int getScreenOrientation() {
        WindowManager window = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        switch (window.getDefaultDisplay().getRotation()) {
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

    private void initcam(int width, int height) {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String CamId = null;
        String[] CameraIdList = new String[0];
        try {
            CameraIdList = manager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        for (int i = 0; i < CameraIdList.length; i++) {
            String id = CameraIdList[i];
            CameraCharacteristics characteristics = null;
            try {
                characteristics = manager.getCameraCharacteristics(id);
                int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                Log.d("facingservice","service : " + facing);
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    CamId = id;
                    break;
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        previewSize = chooseSupportedSize(CamId,width,height);
        previewHeight = previewSize.getHeight();
        previewWidth = previewSize.getWidth();
        Log.d("previewSizehaha","preview size service kedua= " + previewSize + " , cam ID = " + CamId + " width = " + width + " Height =  " + height);

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
            Log.e("errorku", "Exception initializing Detector!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
        }
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
        int rotation = 90;
        sensorOrientation = rotation - getScreenOrientation();

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);




        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        try {
            manager.openCamera(CamId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private android.util.Size chooseSupportedSize(String camId, int textureViewWidth, int textureViewHeight){
//        final Size myInputSize = CameraConnectionFragment.getInputSize();
//        Log.d("haram","coba : "+ myInputSize);
//        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
//        try {
//            final CameraCharacteristics characteristics = manager.getCameraCharacteristics(camId);
//            final StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//            final android.util.Size[] supportedSize = map.getOutputSizes(SurfaceTexture.class);
//            final int texViewArea = textureViewWidth * textureViewHeight;
//            final float texViewAspect = (float)textureViewWidth * (float)textureViewHeight;
//
//            final List nearestToFurthestSz = ArraysKt.sortedWith(supportedSize, ComparisonsKt.compareBy(
//                    (Function1)(new Function1() {
//                        public Object invoke(Object var1) {
//                            return this.invoke((android.util.Size)var1);
//                        }
//
//                        public final float invoke(android.util.Size it) {
//                            float aspect;
//                            if(it.getWidth() < it.getHeight()) {
//                                aspect = (float)it.getWidth()/(float)it.getHeight();
//                            }
//                            else{
//                                aspect = (float)it.getHeight()/(float)it.getWidth();
//                            }
//                            float result = aspect - texViewAspect;
//                            return Math.abs(result);
//                        }
//                    }), (Function1)(new Function1() {
//                        public Object invoke(Object var1) {
//                            return this.invoke((android.util.Size)var1);
//                        }
//
//                        public final int invoke(android.util.Size it) {
//                            int result = texViewArea- it.getWidth() * it.getHeight();
//                            return Math.abs(result);
//                        }
//                    })));
//            Log.d("checkreturneror","return nearest" + nearestToFurthestSz );
//            if(nearestToFurthestSz.isEmpty() == false){
//                Size result = (Size) nearestToFurthestSz.get(0);
//                return (Size)result;
//            }
//            else {
//                final android.util.Size size = new android.util.Size(320, 200);
//                return size;
//            }
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//        final android.util.Size size = new android.util.Size(320, 200);
        final Size ActivityCamera = CameraConnectionFragment.getInputSize();
        return ActivityCamera;
    }


    private boolean isProcessingFrame = false;
    private Integer sensorOrientation;
    private byte[][] yuvBytes = new byte[3][];
    private int yRowStride;
    private Runnable imageConverter;
    private Runnable postInferenceCallback;
    private Detector detector;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
    private static final boolean MAINTAIN_ASPECT = false;

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    private ImageReader.OnImageAvailableListener imageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            if (previewWidth == 0 || previewHeight == 0) {
                return;
            }
            if (rgbBytes == null) {
                rgbBytes = new int[previewWidth * previewHeight];
            }

            final Image image = reader.acquireLatestImage();
            Log.d("akupastibisa","GOT IMAGE SERVICE : " + image.getWidth() + " x " + image.getHeight());
            if(image == null)
            {
                return;
            }

//            if (isProcessingFrame) {
//                image.close();
//                return;
//            }
//            isProcessingFrame = true;
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
            processImage();


            Trace.endSection();
            image.close();
        }
    };

    private long timestamp = 0;
    OverlayView trackingOverlay;
    private Bitmap rgbFrameBitmap = null;
    private boolean computingDetection = false;
    private Bitmap croppedBitmap = null;
    private Matrix frameToCropTransform;
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private long lastProcessingTimeMs;
    private Bitmap cropCopyBitmap = null;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;


    private enum DetectorMode {
        TF_OD_API;
    }

    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
//        trackingOverlay.postInvalidate();
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
        readyForNextImage();
        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
//                        LOGGER.i("Running detection on image " + currTimestamp);
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
                        computingDetection = false;
                    }
                });
    }


    private Handler handler;
    private HandlerThread handlerThread;
    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    private final void CreateCaptureSession(){
        textureView = CameraConnectionFragment.getMyTextureview();
        final ArrayList<Surface> targetSurfaces = new ArrayList<>();
        final SurfaceTexture texture = textureView.getSurfaceTexture();
        assert texture != null;
        final Surface surface = new Surface(texture);


        try {
            final CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            if(shouldPreview == true){
                final SurfaceTexture texture2 =textureView.getSurfaceTexture();
                texture2.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());
                final Surface previewSurface = new Surface(texture2);
                targetSurfaces.add(previewSurface);
                requestBuilder.addTarget(previewSurface);
            }

            imageReader = ImageReader.newInstance(
                    previewSize.getWidth(), previewSize.getHeight(),
                    ImageFormat.YUV_420_888,2);
            imageReader.setOnImageAvailableListener(imageListener,backgroundHandler);

            requestBuilder.addTarget(imageReader.getSurface());
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            targetSurfaces.add(imageReader.getSurface());
            requestBuilder.addTarget(imageReader.getSurface());
            captureRequest = requestBuilder.build();

            cameraDevice.createCaptureSession(Arrays.asList(
                    surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if(cameraDevice == null){
                                return;
                            }
                            captureSession = session;
                            captureRequest = requestBuilder.build();
                            try {
                                captureSession.setRepeatingRequest(captureRequest,captureCallback,backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.d("akupastibisa","Error createCaptureSession()");
                        }
                    },null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /** Starts a background thread and its {@link Handler}. */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("ImageListener");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    /** Stops the background thread and its {@link Handler}. */
    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (final InterruptedException e) {
//            LOGGER.e(e, "Exception!");
        }
    }

    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final CaptureResult partialResult) {}

                @Override
                public void onCaptureCompleted(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final TotalCaptureResult result) {}
            };

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            CreateCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
//            cameraDevice.close();
//            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };











    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public int onStartCommand(@Nullable Intent intent, int flags, int startId){
        return super.onStartCommand(intent, flags, startId);
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


        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());


        startBackgroundThread();
        final Size ActivityCamera = CameraConnectionFragment.getInputSize();
        initcam(ActivityCamera.getWidth(),ActivityCamera.getHeight());
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
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "Service Stopped", Toast.LENGTH_LONG).show();
        stopCamera();
    }

    protected void stopCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }

        cameraDevice = null;
        imageReader = null;
    }
}
