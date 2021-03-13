# rPPG-Android

## Gradle

```
dependencies {
    implementation 'com.github.duanhong169:camera:${latestVersion}'
    ...
}
```

> Replace `${latestVersion}` with the latest version code. See [releases](https://github.com/duanhong169/Camera/releases).

## Usage

- Clone the git repo (can do directly through `Android Studio - Check out project from Version Control`)

- Build Gradle properly

- Make sure `allow storage and camera access` when you download the app on your phone 

- Helpful resources and sample codes are available on [Camera_GitHub](https://github.com/duanhong169/Camera)

## File Description

- activity_main.xml 
(`Camera-app/res/layout`) is a file that you can design first page of the app (Survey Start)
- activity_video_record.xml 
(`Camera-app/res/layout`) is a file that you can design recording page of the app
- MainActivity 
(`Camera-app/java/top.defaults/cameraapp`) is a file that you can control first page of the app
	- Can prepare all the settings that are required for recording video
- Camera2Photographer 
(`Camera-camera\java\top.defaults.camera`) is a file that you can control specific camera settings
- Utils 
(`Camera-camera\java\top.defaults.camera`) is a file that contains utility function for `Camera2Photographer` 

## Reference 
* [duanhong169](https://github.com/duanhong169/Camera)

## Credits

* [google/cameraview](https://github.com/google/cameraview)
* [googlesamples/android-Camera2Basic](https://github.com/googlesamples/android-Camera2Basic)
* [googlesamples/android-Camera2Video](https://github.com/googlesamples/android-Camera2Video)

## License

See the [LICENSE](./LICENSE) file.
