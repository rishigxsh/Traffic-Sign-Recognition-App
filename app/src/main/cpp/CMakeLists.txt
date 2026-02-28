cmake_minimum_required(VERSION 3.22.1)
project("traffic_sign_detection")

# --- OpenCV Configuration (optional) ---
# To enable OpenCV:
#   1. Download the OpenCV Android SDK: https://opencv.org/releases/
#   2. Set OpenCV_DIR to the sdk/native/jni folder, e.g.:
#      set(OpenCV_DIR "/path/to/opencv-android-sdk/sdk/native/jni")
#   3. Uncomment the find_package line below.
#
# find_package(OpenCV QUIET)

if(OpenCV_FOUND)
    message(STATUS "OpenCV found: ${OpenCV_VERSION} — native image processing enabled")
    add_definitions(-DOPENCV_ENABLED)
else()
    message(STATUS "OpenCV not found — native image processing disabled; using Kotlin ONNX path")
endif()

add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp
        src/tensor.cpp
        src/logger.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        include
)

if(OpenCV_FOUND)
    target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE ${OpenCV_INCLUDE_DIRS})
endif()

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
)

if(OpenCV_FOUND)
    target_link_libraries(${CMAKE_PROJECT_NAME} ${OpenCV_LIBS})
endif()
