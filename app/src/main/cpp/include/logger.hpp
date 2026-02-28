//
// Created by aidan on 9/26/2025.
//

#ifndef TRAFFIC_SIGN_DETECTION_LOGGER_HPP
#define TRAFFIC_SIGN_DETECTION_LOGGER_HPP

#include <string>
#include <fstream>
#include <mutex>
#include <memory>
#include <iostream>
#include <chrono>
#include <sstream>
#include <vector>

enum class LogLevel {
    DEBUG = 0,
    INFO = 1,
    WARNING = 2,
    ERROR = 3,
    CRITICAL = 4
};

class Logger {
public:
    // Singleton pattern
    static Logger& getInstance();

    // Initialize logger with file path and log level
    void initialize(const std::string& logFilePath = "traffic_sign_app.log",
                    LogLevel level = LogLevel::INFO,
                    bool consoleOutput = true);

    // Logging methods
    void debug(const std::string& message, const std::string& component = "");
    void info(const std::string& message, const std::string& component = "");
    void warning(const std::string& message, const std::string& component = "");
    void error(const std::string& message, const std::string& component = "");
    void critical(const std::string& message, const std::string& component = "");

    // Performance logging
    void logPerformance(const std::string& operation,
                        const std::string& device,
                        long long microseconds,
                        const std::string& additionalInfo = "");

    // Tensor-specific logging
    void logTensorOperation(const std::string& operation,
                            const std::string& device,
                            const std::vector<int>& shape,
                            bool success = true,
                            const std::string& errorMsg = "");

    // CUDA-specific logging
    void logCudaOperation(const std::string& operation,
                          const std::string& kernelName,
                          int blockSize,
                          int gridSize,
                          bool success = true,
                          const std::string& errorMsg = "");

    // Memory logging
    void logMemoryAllocation(const std::string& device,
                             size_t sizeBytes,
                             const std::string& purpose = "");

    void logMemoryDeallocation(const std::string& device,
                               size_t sizeBytes,
                               const std::string& purpose = "");

    // Set log level at runtime
    void setLogLevel(LogLevel level);

    // Flush logs
    void flush();

    // Close logger
    void close();

private:
    Logger() = default;
    ~Logger() = default;
    Logger(const Logger&) = delete;
    Logger& operator=(const Logger&) = delete;

    void log(LogLevel level, const std::string& message, const std::string& component = "");
    std::string getCurrentTimestamp();
    std::string logLevelToString(LogLevel level);
    std::string formatShape(const std::vector<int>& shape);

    std::ofstream logFile;
    LogLevel currentLevel;
    bool consoleOutput;
    std::mutex logMutex;
    bool initialized;
};

// Convenience macros for easy logging
#define LOG_DEBUG(msg) Logger::getInstance().debug(msg, __FUNCTION__)
#define LOG_INFO(msg) Logger::getInstance().info(msg, __FUNCTION__)
#define LOG_WARNING(msg) Logger::getInstance().warning(msg, __FUNCTION__)
#define LOG_ERROR(msg) Logger::getInstance().error(msg, __FUNCTION__)
#define LOG_CRITICAL(msg) Logger::getInstance().critical(msg, __FUNCTION__)

#define LOG_TENSOR_OP(op, device, shape, success, error) \
    Logger::getInstance().logTensorOperation(op, device, shape, success, error)

#define LOG_CUDA_OP(op, kernel, blockSize, gridSize, success, error) \
    Logger::getInstance().logCudaOperation(op, kernel, blockSize, gridSize, success, error)

#define LOG_PERFORMANCE(op, device, time_us, info) \
    Logger::getInstance().logPerformance(op, device, time_us, info)

#define LOG_MEMORY_ALLOC(device, size, purpose) \
    Logger::getInstance().logMemoryAllocation(device, size, purpose)

#define LOG_MEMORY_DEALLOC(device, size, purpose) \
    Logger::getInstance().logMemoryDeallocation(device, size, purpose)

#endif //TRAFFIC_SIGN_DETECTION_TEMP_LOGGER_HPP
