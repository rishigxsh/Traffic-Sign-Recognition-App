#include <jni.h>
#include <string>
#include "logger.hpp"

#ifdef OPENCV_ENABLED
#include <opencv2/opencv.hpp>
#include "tensor.hpp"
#endif

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_tsrapp_ui_main_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_tsrapp_ui_main_MainActivity_processImage(
        JNIEnv* env,
        jobject /* this */,
        jstring imagePath) {

    const char *path = env->GetStringUTFChars(imagePath, 0);
    std::string pathStr(path);
    env->ReleaseStringUTFChars(imagePath, path);

#ifdef OPENCV_ENABLED
    LOG_INFO("Processing image: " + pathStr);

    cv::Mat img = cv::imread(pathStr);
    if (img.empty()) {
        LOG_ERROR("Failed to load image from path: " + pathStr);
        return env->NewStringUTF("Error: Could not load image");
    }

    Tensor t = Tensor::fromMat(img);
    t.multiplyScalar(2.0f);

    std::string result = "Success: Loaded image to Tensor [1, " +
                         std::to_string(img.channels()) + ", " +
                         std::to_string(img.rows) + ", " +
                         std::to_string(img.cols) + "]";
    return env->NewStringUTF(result.c_str());
#else
    return env->NewStringUTF("Native image processing requires OpenCV. Using Kotlin ONNX inference path instead.");
#endif
}
