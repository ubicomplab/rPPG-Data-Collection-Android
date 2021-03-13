package top.defaults.camera;

import android.app.Activity;

public class PhotographerFactory {

    public static Photographer createPhotographerWithCamera2(Activity activity, CameraView preview, CameraView preview2) {
        InternalPhotographer photographer = new Camera2Photographer();
        photographer.initWithViewfinder(activity, preview);
        photographer.initWithViewfinder2(activity, preview2);
        preview.assign(photographer);
        return photographer;
    }
}
