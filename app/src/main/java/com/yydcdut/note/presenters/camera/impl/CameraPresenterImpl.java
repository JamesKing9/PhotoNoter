package com.yydcdut.note.presenters.camera.impl;

import android.content.Context;
import android.graphics.ImageFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.NonNull;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.yydcdut.note.R;
import com.yydcdut.note.camera.param.Size;
import com.yydcdut.note.injector.ContextLife;
import com.yydcdut.note.model.camera.ICameraModel;
import com.yydcdut.note.model.camera.ICameraProcess;
import com.yydcdut.note.model.camera.ICameraSettingModel;
import com.yydcdut.note.model.camera.impl.CameraModel;
import com.yydcdut.note.model.compare.SizeComparator;
import com.yydcdut.note.presenters.camera.ICameraPresenter;
import com.yydcdut.note.utils.CameraStateUtils;
import com.yydcdut.note.utils.Const;
import com.yydcdut.note.utils.FilePathUtils;
import com.yydcdut.note.utils.LocalStorageUtils;
import com.yydcdut.note.utils.Utils;
import com.yydcdut.note.views.IView;
import com.yydcdut.note.views.camera.ICameraView;
import com.yydcdut.note.widget.camera.AutoFitPreviewView;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * Created by yuyidong on 16/2/3.
 */
public class CameraPresenterImpl implements ICameraPresenter, Handler.Callback,
        ICameraProcess.PictureReturnCallback, ICameraProcess.StillPictureReturnCallback {
    private static final int TIME_LONG_CAPTURE = 1000;//1s内没有抬起，那么算作长拍摄
    private boolean mIsWannaStillCapture = false;

    private ICameraView mICameraView;
    private int mCategoryId;

    private ICameraModel mCameraModel;
    private ICameraSettingModel mCameraSettingModel;

    private Context mContext;
    private LocalStorageUtils mLocalStorageUtils;

//    private int mFlashState = 0;
//    private int mTimerState = 0;
//    private int mGridState =0;
//    private int mCameraId;


    /* 坐标 */
    private LocationClient mLocationClient;
    private double mLatitude;
    private double mLontitude;

    private String mCurrentCameraId;

    private static final int MSG_DOWN = 1;
    private static final int MSG_UP = 2;
    private static final int MSG_STILL_SIGNAL = 3;
    private Handler mHandler;

    @Inject
    public CameraPresenterImpl(@ContextLife("Activity") Context context, LocalStorageUtils localStorageUtils) {
        mContext = context;
        mLocalStorageUtils = localStorageUtils;
    }

    @Override
    public void attachView(@NonNull IView iView) {
        mICameraView = (ICameraView) iView;
//        if (AppCompat.AFTER_ICE_CREAM) {
        mCameraModel = CameraModel.getInstance();
//        } else {
//            mCameraModel = new Camera2Model()
//        }

        String cameraId = mLocalStorageUtils.getCameraSaveCameraId();
        if (Const.CAMERA_BACK.equals(cameraId)) {
            mCurrentCameraId = Const.CAMERA_BACK;
            mCameraSettingModel = mCameraModel.openCamera(mCurrentCameraId,
                    mLocalStorageUtils.getCameraBackRotation());
        } else {
            mCurrentCameraId = Const.CAMERA_FRONT;
            mCameraSettingModel = mCameraModel.openCamera(cameraId,
                    mLocalStorageUtils.getCameraFrontRotation());
        }
        Size previewSize = getSuitablePreviewSize(mCameraSettingModel.getPreviewSizes());
        mICameraView.setSize(previewSize.getHeight(), previewSize.getWidth());
        getPictureSize();
        initLocation();
        initUIState();
        mHandler = new Handler(this);
    }

    private void initUIState() {
        if (mCameraSettingModel != null) {
            boolean flashSupported = mCameraSettingModel.isFlashSupported();
            int[] flashRes = flashSupported ?
                    new int[]{
                            R.drawable.ic_flash_off_white_24dp,
                            R.drawable.ic_flash_auto_white_24dp,
                            R.drawable.ic_flash_on_white_24dp} :
                    new int[]{R.drawable.ic_flash_off_white_24dp};
            int cameraNumber = mCameraSettingModel.getNumberOfCameras();
            int[] cameraIdRes = cameraNumber == 1 ?
                    new int[]{R.drawable.ic_camera_rear_white_24dp} :
                    new int[]{
                            R.drawable.ic_camera_rear_white_24dp,
                            R.drawable.ic_camera_front_white_24dp};
            if (mLocalStorageUtils.getCameraSaveSetting()) {
                mICameraView.initState(
                        CameraStateUtils.changeFlahsSaveState2UIState(mLocalStorageUtils.getCameraSaveFlash()), flashRes,
                        CameraStateUtils.changeTimerSaveState2UIState(mLocalStorageUtils.getCameraSaveTimer()),
                        CameraStateUtils.changeGridSaveState2UIState(mLocalStorageUtils.getCameraGridOpen()),
                        CameraStateUtils.changeCameraIdSaveState2UIState(mLocalStorageUtils.getCameraSaveCameraId()), cameraIdRes);
            } else {
                mICameraView.initState(0, flashRes, 0, 0, 0, cameraIdRes);
            }
        }
    }

    @Override
    public void bindData(int categoryId) {
        mCategoryId = categoryId;
    }

    /**
     * 获得最佳预览尺寸
     *
     * @param previewList
     * @return
     */
    private Size getSuitablePreviewSize(List<Size> previewList) {
        Size previewSize = null;
        Collections.sort(previewList, new SizeComparator());
        float screenScale = Utils.sScreenHeight / (float) Utils.sScreenWidth;
        for (Size preSize : previewList) {
            if (preSize.getWidth() * preSize.getHeight() > 1200000) {
                continue;
            }
            float preScale = preSize.getWidth() / (float) preSize.getHeight();
            //full ratio 如果全屏也是4：3的话，就先这样吧
            if (Math.abs(preScale - screenScale) < 0.03) {
//                mFullSize = preSize;
                previewSize = preSize;
            }
            //4:3 默认进来4：3
            if (preScale < 1.36f && preScale > 1.30f) {
//                m43Size = preSize;
                previewSize = preSize;
            }
//            if (mSizeState == Const.LAYOUT_PERSONAL_RATIO_1_1) {
//                previewSize = m43Size;
//                mMenuLayout.setRatio11();
//            } else if (mSizeState == Const.LAYOUT_PERSONAL_RATIO_FULL) {
//                mMenuLayout.setRatio43();
//                previewSize = mFullSize;
//            } else {
//                mMenuLayout.setRatio43();
//                previewSize = m43Size;
//            }
        }
//        if (mFullSize == null) {
//            mFullSize = previewList.get(previewList.size() / 2);
//        }
//        if (m43Size == null) {
//            m43Size = previewList.get(previewList.size() / 2);
//        }
        if (previewSize == null) {
            previewSize = previewList.get(0);
        }
        return previewSize;
    }

    private Size getPictureSize() {
        Size size = null;
        try {
            size = mLocalStorageUtils.getPictureSize(mCurrentCameraId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (size == null) {
            List<Size> list = mCameraSettingModel.getPictureSizes();
            Collections.sort(list, new SizeComparator());
            size = list.get(list.size() - 1);
            savePictureSizes(mCurrentCameraId, list);
        }
        return size;
    }

    /**
     * 保存照片尺寸到SharedPreference
     *
     * @param currentCameraId
     * @param list
     */
    private void savePictureSizes(String currentCameraId, List<Size> list) {
        try {
            mLocalStorageUtils.setPictureSizes(currentCameraId, list);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void detachView() {
        if (mCameraModel.isPreview()) {
            mCameraModel.stopPreview();
        }
        if (mCameraModel.isOpen()) {
            mCameraModel.closeCamera();
        }
        mLocationClient.stop();
    }

    @Override
    public void onSurfaceAvailable(AutoFitPreviewView.PreviewSurface surface, boolean sizeChanged, int width, int height) {
        if (sizeChanged) {
            if (mCameraModel.isOpen() && !mCameraModel.isPreview()) {
                Size previewSize = getSuitablePreviewSize(mCameraSettingModel.getPreviewSizes());
                Size pictureSize = getPictureSize();
                mCameraSettingModel.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
                mCameraSettingModel.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
//                mCameraModel.startPreview(surface,
//                        mICameraView.getPreviewViewWidth(), mICameraView.getPreviewViewHeight());
                mCameraModel.startPreview(surface,
                        previewSize.getHeight(), previewSize.getWidth());
            }
        } else {
            if (!mCameraModel.isOpen() && !mCameraModel.isPreview()) {
                String cameraId = mLocalStorageUtils.getCameraSaveCameraId();
                if (Const.CAMERA_BACK.equals(cameraId)) {
                    mCameraSettingModel = mCameraModel.openCamera(cameraId,
                            mLocalStorageUtils.getCameraBackRotation());
                } else {
                    mCameraSettingModel = mCameraModel.openCamera(cameraId,
                            mLocalStorageUtils.getCameraFrontRotation());
                }
            }
        }
    }

    @Override
    public void onSurfaceDestroy() {
        if (mCameraModel.isPreview()) {
            mCameraModel.stopPreview();
        }
        if (mCameraModel.isOpen()) {
            mCameraModel.closeCamera();
        }
    }

    @Override
    public void onDown() {
        if (mCameraModel.isPreview()) {
            mHandler.sendEmptyMessage(MSG_DOWN);
        }
    }

    @Override
    public void onUp() {
        if (mCameraModel.isPreview()) {
            mHandler.sendEmptyMessage(MSG_UP);
        }
    }

    @Override
    public void onFlashClick() {

    }

    @Override
    public void onTimerClick() {

    }

    @Override
    public void onGridClick() {

    }

    @Override
    public void onCameraIdClick() {

    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_DOWN:
                mHandler.sendEmptyMessageDelayed(MSG_STILL_SIGNAL, TIME_LONG_CAPTURE);
                break;
            case MSG_STILL_SIGNAL:
                mIsWannaStillCapture = true;
                mCameraModel.startStillCapture(this);
                break;
            case MSG_UP:
                if (mHandler.hasMessages(MSG_STILL_SIGNAL)) {
                    mHandler.removeMessages(MSG_STILL_SIGNAL);
                }
                if (mIsWannaStillCapture) {
                    mIsWannaStillCapture = false;
                    mCameraModel.stopStillCapture();
                } else {
                    mCameraModel.capture(this);
                }
                break;
        }
        return false;
    }

    private void initLocation() {
        mLocationClient = new LocationClient(mContext);
        mLocationClient.registerLocationListener(new BDLocationListener() {
            @Override
            public void onReceiveLocation(BDLocation bdLocation) {
                mLatitude = bdLocation.getLatitude();
                mLontitude = bdLocation.getLongitude();
            }
        });
        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);//可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        option.setCoorType("gcj02");//可选，默认gcj02，设置返回的定位结果坐标系，
        int span = 2000;
        option.setScanSpan(span);//可选，默认0，即仅定位一次，设置发起定位请求的间隔需要大于等于1000ms才是有效的
//        option.setIsNeedAddress(checkGeoLocation.isChecked());//可选，设置是否需要地址信息，默认不需要
        option.setOpenGps(true);//可选，默认false,设置是否使用gps
        option.setLocationNotify(true);//可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
        option.setIgnoreKillProcess(false);//可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
        option.setEnableSimulateGps(false);//可选，默认false，设置是否需要过滤gps仿真结果，默认需要
        mLocationClient.setLocOption(option);
        mLocationClient.start();
    }

    private boolean addData2Service(byte[] data, String cameraId, long time, int categoryId,
                                    boolean isMirror, int ratio, int imageFormat) {
        boolean bool = true;
        int size = data.length;
        String fileName = time + ".data";
        File file = new File(FilePathUtils.getSandBoxDir() + fileName);
        OutputStream outputStream = null;
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            outputStream = new FileOutputStream(file);
            outputStream.write(data);
            outputStream.flush();
        } catch (IOException e) {
            bool = false;
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    bool = false;
                    e.printStackTrace();
                }
            }
        }

        int orientation = 0;//todo 这个还没做，下个版本做

        String latitude0 = String.valueOf((int) mLatitude) + "/1,";
        String latitude1 = String.valueOf((int) ((mLatitude - (int) mLatitude) * 60) + "/1,");
        String latitude2 = String.valueOf((int) ((((mLatitude - (int) mLatitude) * 60) - ((int) ((mLatitude - (int) mLatitude) * 60))) * 60 * 10000)) + "/10000";
        String latitude = new StringBuilder(latitude0).append(latitude1).append(latitude2).toString();
        String lontitude0 = String.valueOf((int) mLontitude) + "/1,";
        String lontitude1 = String.valueOf((int) ((mLontitude - (int) mLontitude) * 60) + "/1,");
        String lontitude2 = String.valueOf((int) ((((mLontitude - (int) mLontitude) * 60) - ((int) ((mLontitude - (int) mLontitude) * 60))) * 60 * 10000)) + "/10000";
        String lontitude = new StringBuilder(lontitude0).append(lontitude1).append(lontitude2).toString();
        int whiteBalance = 0;
//        if (getSettingModel().getSupportedWhiteBalance().size() > 0) {
//            if (getSettingModel().getWhiteBalance() != ICameraParams.WHITE_BALANCE_AUTO) {
//                whiteBalance = 1;
//            }
//        }
        //todo 这里的flash是指拍照的那个时候闪光灯是否打开了,所以啊。。。这个。。。。
        int flash = 0;
//        if (getSettingModel().getSupportedFlash().size() > 0) {
//            if (getSettingModel().getFlash() != ICameraParams.FLASH_OFF) {
//                flash = 1;
//            }
//        }
        int imageLength;
        int imageWidth;
        switch (imageFormat) {
            case ImageFormat.NV21:
                Size previewSize = mCameraSettingModel.getPreviewSize();
                imageLength = previewSize.getHeight();
                imageWidth = previewSize.getWidth();
                if (ratio == Const.CAMERA_SANDBOX_PHOTO_RATIO_1_1) {
                    imageLength = imageWidth;
                }
                break;
            default:
            case ImageFormat.JPEG:
                Size pictureSize = mCameraSettingModel.getPictureSize();
                imageLength = pictureSize.getHeight();
                imageWidth = pictureSize.getWidth();
                if (ratio == Const.CAMERA_SANDBOX_PHOTO_RATIO_1_1) {
                    imageLength = imageWidth;
                }
                break;

        }
        String make = Build.BRAND;
        String model = Build.MODEL;
        try {
            mICameraView.add2Service(fileName, size, cameraId, time, categoryId, isMirror, ratio,
                    orientation, latitude, lontitude, whiteBalance, flash, imageLength, imageWidth,
                    make, model, imageFormat);
        } catch (RemoteException e) {
            e.printStackTrace();
            bool = false;
        }
        return bool;
    }

    @Override
    public void onPictureTaken(boolean success, byte[] data, long time) {
        if (success) {
            addData2Service(data, mCurrentCameraId, time, mCategoryId, false,
                    Const.CAMERA_SANDBOX_PHOTO_RATIO_FULL, ImageFormat.JPEG);
            mCameraModel.restartPreview();
        } else {
            mICameraView.showToast(mContext.getResources().getString(R.string.toast_fail));
        }

    }

    @Override
    public void onStillPictureTaken(int imageFormat, byte[] data, long time) {
        switch (imageFormat) {
            case ImageFormat.NV21:
                addData2Service(data, mCurrentCameraId, time, mCategoryId, false,
                        Const.CAMERA_SANDBOX_PHOTO_RATIO_FULL, ImageFormat.NV21);
                break;
        }
    }

}