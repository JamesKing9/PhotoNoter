package com.yydcdut.note.model.camera.impl2;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Range;

import com.yydcdut.note.model.camera.ICameraSettingModel;
import com.yydcdut.note.utils.YLog;
import com.yydcdut.note.utils.camera.param.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by yuyidong on 16/2/10.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2SettingModel implements ICameraSettingModel {
    private int mCameraNumber;
    private CameraCharacteristics mCameraCharacteristics;

    private CaptureRequest.Builder mBuilder;

    public Camera2SettingModel(CameraCharacteristics cameraCharacteristics, int cameraNumber) {
        mCameraCharacteristics = cameraCharacteristics;
        mCameraNumber = cameraNumber;
    }

    public void setCaptureRequestBuilder(CaptureRequest.Builder builder) {
        mBuilder = builder;
    }

    @Override
    public int getNumberOfCameras() {
        return mCameraNumber;
    }

    @Override
    public boolean isFlashSupported() {
        return mCameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
    }

    @Override
    public List<Size> getSupportPreviewSizes() {
        StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            return null;
        }
        android.util.Size[] supports = map.getOutputSizes(SurfaceTexture.class);
        List<Size> sizeList = new ArrayList<>(supports.length);
        for (android.util.Size size : supports) {
            sizeList.add(Size.translate(size.getWidth(), size.getHeight()));

        }
        return sizeList;
    }

    @Override
    public List<Size> getSupportPictureSizes() {
        StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            return null;
        }
        android.util.Size[] supports = map.getOutputSizes(ImageFormat.JPEG);
        List<Size> sizeList = new ArrayList<>(supports.length);
        for (android.util.Size size : supports) {
            sizeList.add(Size.translate(size.getWidth(), size.getHeight()));

        }
        return sizeList;
    }

    @Override
    public boolean isZoomSupported() {
        return mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) >= 1;
    }

    @Override
    public int getMaxZoom() {
        return (int) (mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) * 100);
    }

    @Override
    public int getMaxExposureCompensation() {
        Range<Long> range = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        return Integer.valueOf(range.getLower().toString());
    }

    @Override
    public int getMinExposureCompensation() {
        Range<Long> range = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        return Integer.valueOf(range.getUpper().toString());
    }

    public List<Size> getSupportYUV420888Sizes() {
        StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            return null;
        }
        android.util.Size[] supports = map.getOutputSizes(ImageFormat.YUV_420_888);
        List<Size> sizeList = new ArrayList<>(supports.length);
        for (android.util.Size size : supports) {
            sizeList.add(Size.translate(size.getWidth(), size.getHeight()));

        }
        return sizeList;
    }

    @Override
    public void setDisplayOrientation(int degree) {

    }

    @Override
    public void setFlash(int flashState) {
        switch (flashState) {
            case FLASH_OFF:
                mBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                mBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                break;
            case FLASH_AUTO:
                mBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                break;
            case FLASH_ON:
                mBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                break;
            default:
                break;
        }
        doChange();
    }

    @Override
    public int getFlash() {
        switch (mBuilder.get(CaptureRequest.CONTROL_AE_MODE)) {
            case CaptureRequest.CONTROL_AE_MODE_OFF:
                return FLASH_OFF;
            case CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH:
                return FLASH_AUTO;
            case CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH:
                return FLASH_ON;

        }
        return FLASH_OFF;
    }

    @Override
    public void setZoom(int value) {
        //value ---> 0 ~ max*100
        float zoom = ((float) value / 100);
        Rect rect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        float max = mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        float scale = 1 - ((max - zoom) / max);//scale是从 0 ~ 1，当为0的时候没有变化，当为1的时候最大放大
        float change = max * scale + 1.0f * (1 - scale);//change是从 1.0 ~ max，当为1.0的时候没有变化，当为max的时候最大放大
        if (change > max) {
            return;
        }
        int centerX = rect.centerX();
        int centerY = rect.centerY();
        int width = rect.width();
        int height = rect.height();
        float newWidth = width / change;
        float newHeight = height / change;
        float left = centerX - newWidth / 2;
        float top = centerY - newHeight / 2;
        float right = newWidth / 2 + centerX;
        float bottom = newHeight / 2 + centerY;
        Rect newRect = new Rect((int) left, (int) top, (int) right, (int) bottom);
        mBuilder.set(CaptureRequest.SCALER_CROP_REGION, newRect);
        YLog.i("yuyidong", "width-->" + width + "  height-->" + height + "  newWidth-->" + newWidth + "  newHeight-->" + newHeight + "  change-->" + change + "  scale-->" + scale);
        doChange();
    }

    @Override
    public int getZoom() {
        return 0;
    }

    @Override
    public void setExposureCompensation(int value) {
        mBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, value);
        doChange();
    }

    @Override
    public int calculateZoom(int firstZoomValue, float firstCurrentSpan, float currectCurrentSpan) {
        if (!isZoomSupported()) {
            return -1;
        }
        YLog.i("yuyidong", "calculateZoom--->" + (int) (((currectCurrentSpan / firstCurrentSpan - 1) * getMaxZoom()) + firstZoomValue));
        return (int) (((currectCurrentSpan / firstCurrentSpan - 1) * getMaxZoom()) + firstZoomValue);
    }

    private OnParameterChangedListener mOnParameterChangedListener;

    public void setOnParameterChangedListener(OnParameterChangedListener onParameterChangedListener) {
        mOnParameterChangedListener = onParameterChangedListener;
    }

    public interface OnParameterChangedListener {
        void onChanged(CaptureRequest.Builder builder);
    }

    private void doChange() {
        if (mOnParameterChangedListener != null) {
            mOnParameterChangedListener.onChanged(mBuilder);
        }
    }
}