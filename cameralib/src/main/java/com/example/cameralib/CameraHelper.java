package com.example.cameralib;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class CameraHelper {
    MyCameraView myCameraView;
    Activity activity;
    Context context;
    public addCameraListener listener;
    private String mCameraId = "0";//摄像头id（通常0代表后置摄像头，1代表前置摄像头）
    private final int RESULT_CODE_CAMERA = 1;//判断是否有拍照权限的标识码
    int sensorOrentation;//传感器方向
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewSession;
    private CaptureRequest.Builder mCaptureRequestBuilder, captureRequestBuilder;
    private CaptureRequest mCaptureRequest;
    private ImageReader imageReader;
    private int mHeight = 0, mWidth = 0;
    private Size previewSize;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    String TAG = "CameraHelper";

    //横向的时候
//    static {
//        ORIENTATIONS.append(Surface.ROTATION_0, 270);
//        ORIENTATIONS.append(Surface.ROTATION_90, 0);
//        ORIENTATIONS.append(Surface.ROTATION_180, 90);
//        ORIENTATIONS.append(Surface.ROTATION_270, 180);
//    }


    /**
     * @param myCameraView 视频预览控件
     * @param context      预览上下午环境
     * @param activity     使用相机的activity
     */
    public CameraHelper(MyCameraView myCameraView, Context context, Activity activity) {
        this.myCameraView = myCameraView;
        this.context = context;
        this.activity = activity;
        initOrientations();
        myCameraView.setSurfaceTextureListener(surfaceTextureListener);
    }

    public static CameraHelper of(MyCameraView myCameraView, Context context, Activity activity) {
        return new CameraHelper(myCameraView, context, activity);
    }


    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {

        //可用
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mWidth = width;
            mHeight = height;
            openCamera();
        }


        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        //释放
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            stopCamera();
            return true;
        }

        //更新
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    /**
     * 打开摄像头
     */
    private void openCamera() {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        //设置摄像头特性
        setCameraCharacteristics(manager);
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                //提示用户开户权限
                String[] perms = {"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};
                ActivityCompat.requestPermissions(activity, perms, RESULT_CODE_CAMERA);
            } else {
                manager.openCamera(mCameraId, stateCallback, null);

            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置摄像头的参数
     */
    private void setCameraCharacteristics(CameraManager manager) {
        try {

            // 获取指定摄像头的特性
            CameraCharacteristics characteristics
                    = manager.getCameraCharacteristics(mCameraId);
            //获取摄像头sensor方向
            sensorOrentation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            // 获取摄像头支持的配置属性
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // 获取摄像头支持的最大尺寸
            Size largest = Collections.max(
                    Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
            // 创建一个ImageReader对象，用于获取摄像头的图像数据
            imageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                    ImageFormat.JPEG, 2);
            System.out.print(imageReader);
            //设置获取图片的监听
            imageReader.setOnImageAvailableListener(imageAvailableListener, null);
            // 获取最佳的预览尺寸
            previewSize = chooseOptimalSize(map.getOutputSizes(
                    SurfaceTexture.class), mWidth, mHeight, largest);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
        }
    }

    private static Size chooseOptimalSize(Size[] choices
            , int width, int height, Size aspectRatio) {
        // 收集摄像头支持的大过预览Surface的分辨率
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        // 如果找到多个预览尺寸，获取其中面积最小的
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            //没有合适的预览尺寸
            return choices[0];
        }
    }

    // 为Size定义一个比较器Comparator
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // 强转为long保证不会发生溢出
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * 摄像头状态的监听
     */
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        // 摄像头被打开时触发该方法
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            // 开始预览
            takePreview();
        }

        // 摄像头断开连接时触发该方法
        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;

        }

        // 打开摄像头出现错误时触发该方法
        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            cameraDevice.close();
        }
    };

    /**
     * 开始预览
     */
    private void takePreview() {
        //获取屏幕状态值
        int screenState = activity.getRequestedOrientation();
        configureTransform(mWidth, mHeight, activity);
        SurfaceTexture mSurfaceTexture = myCameraView.getSurfaceTexture();
        //设置TextureView的缓冲区大小
        //  mSurfaceTexture.setDefaultBufferSize(previewSize.getHeight(), previewSize.getWidth());
        mSurfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        //获取Surface显示预览数据
        Surface mSurface = new Surface(mSurfaceTexture);
        try {
            //创建预览请求
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 设置自动对焦模式
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //设置Surface作为预览数据的显示界面
            mCaptureRequestBuilder.addTarget(mSurface);
            //创建相机捕获会话，第一个参数是捕获数据的输出Surface列表，第二个参数是CameraCaptureSession的状态回调接口，当它创建好后会回调onConfigured方法，第三个参数用来确定Callback在哪个线程执行，为null的话就在当前线程执行
            mCameraDevice.createCaptureSession(Arrays.asList(mSurface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        //开始预览
                        mCaptureRequest = mCaptureRequestBuilder.build();
                        mPreviewSession = session;
                        //设置反复捕获数据的请求，这样预览界面就会一直有数据显示
                        mPreviewSession.setRepeatingRequest(mCaptureRequest, null, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

//    public void setDisplayOriention() {
//        mCaptureRequestBuilder.setDisplayOrientation(0);
//    }

    /**
     * 拍照
     */
    public void takePicture(addCameraListener listener) {
        this.listener = listener;
        try {
            if (mCameraDevice == null) {
                return;
            }
            // 创建拍照请求
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // 设置自动对焦模式
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 将imageReader的surface设为目标
            captureRequestBuilder.addTarget(imageReader.getSurface());
            // 获取设备方向
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            // 根据设备方向计算设置照片的方向
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION
                    , ORIENTATIONS.get(rotation));
            //拍照
            CaptureRequest captureRequest = captureRequestBuilder.build();
            //设置拍照监听
            mPreviewSession.capture(captureRequest, captureCallback, null);
//            //设置获取图片的监听
            imageReader.setOnImageAvailableListener(imageAvailableListener, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 监听拍照结果
     */
    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        // 拍照成功
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            // 重设自动对焦模式
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            // 设置自动曝光模式
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }
    };
    /**
     * 监听拍照的图片
     */
    private ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        // 当照片数据可用时激发该方法
        @Override
        public void onImageAvailable(ImageReader reader) {
            //先验证手机是否有sdcard
            String status = Environment.getExternalStorageState();
            // 获取捕获的照片数据
            Image image = reader.acquireNextImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            listener.onImage(data);
            image.close();
        }
    };

    /**
     * 停止拍照释放资源
     */
    private void stopCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

    }

    private void configureTransform(int viewWidth, int viewHeight, Activity activity) {
        if (null == myCameraView || null == previewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        myCameraView.setTransform(matrix);
    }

    public interface addCameraListener {
        void onImage(byte[] data);
    }

    void initOrientations() {
        int screenState = activity.getRequestedOrientation();
        Log.d(TAG, "屏幕方向的值：" + screenState);
        if (screenState == -1) {
            //竖向的时候
            ORIENTATIONS.append(Surface.ROTATION_0, 90);
            ORIENTATIONS.append(Surface.ROTATION_90, 0);
            ORIENTATIONS.append(Surface.ROTATION_180, 270);
            ORIENTATIONS.append(Surface.ROTATION_270, 180);
        } else {
            ORIENTATIONS.append(Surface.ROTATION_0, 270);
            ORIENTATIONS.append(Surface.ROTATION_90, 0);
            ORIENTATIONS.append(Surface.ROTATION_180, 90);
            ORIENTATIONS.append(Surface.ROTATION_270, 180);
        }

    }
}
