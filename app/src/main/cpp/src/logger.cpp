//
// Created by aidan on 9/26/2025.
//

#include "logger.hpp"
#include <iomanip>
#include <ctime>

Logger& Logger::getInstance() {
    static Logger instance;
    return instance;
}

void Logger::initialize(const std::string& logFilePath, LogLevel level, bool consoleOutput) {
    currentLevel = level;
    this->consoleOutput = consoleOutput;

    if (logFile.is_open()) {
        logFile.close();
    }

    logFile.open(logFilePath, std::ios::app);
    if (!logFile.is_open()) {
        std::cerr << "Failed to open log file: " << logFilePath << std::endl;
        return;
    }

    initialized = true;

    // Log initialization
    std::string initMsg = "=== Traffic Sign Recognition Application Logger Initialized ===";
    logFile << getCurrentTimestamp() << " [INIT] " << initMsg << std::endl;
    if (consoleOutput) {
        std::cout << getCurrentTimestamp() << " [INIT] " << initMsg << std::endl;
    }
    logFile.flush();
}

void Logger::debug(const std::string& message, const std::string& component) {
    log(LogLevel::DEBUG, message, component);
}

void Logger::info(const std::string& message, const std::string& component) {
    log(LogLevel::INFO, message, component);
}

void Logger::warning(const std::string& message, const std::string& component) {
    log(LogLevel::WARNING, message, component);
}

void Logger::error(const std::string& message, const std::string& component) {
    log(LogLevel::ERROR, message, component);
}

void Logger::critical(const std::string& message, const std::string& component) {
    log(LogLevel::CRITICAL, message, component);
}

void Logger::logPerformance(const std::string& operation, const std::string& device,
                            long long microseconds, const std::string& additionalInfo) {
    if (!initialized) return;

    std::ostringstream oss;
    oss << "PERF [" << device << "] " << operation << ": " << microseconds << " μs";
    if (!additionalInfo.empty()) {
        oss << " (" << additionalInfo << ")";
    }

    std::string logMessage = oss.str();

    if (logFile.is_open()) {
        logFile << getCurrentTimestamp() << " [PERFORMANCE] " << logMessage << std::endl;
        logFile.flush();
    }

    if (consoleOutput) {
        std::cout << getCurrentTimestamp() << " [PERFORMANCE] " << logMessage << std::endl;
    }
}

void Logger::logTensorOperation(const std::string& operation, const std::string& device,
                                const std::vector<int>& shape, bool success, const std::string& errorMsg) {
    if (!initialized) return;

    std::ostringstream oss;
    oss << "TENSOR [" << device << "] " << operation << " - Shape: " << formatShape(shape);
    oss << " - " << (success ? "SUCCESS" : "FAILED");
    if (!success && !errorMsg.empty()) {
        oss << " - Error: " << errorMsg;
    }

    std::string logMessage = oss.str();
    LogLevel level = success ? LogLevel::INFO : LogLevel::ERROR;

    if (logFile.is_open()) {
        logFile << getCurrentTimestamp() << " [" << logLevelToString(level) << "] " << logMessage << std::endl;
        logFile.flush();
    }

    if (consoleOutput) {
        std::cout << getCurrentTimestamp() << " [" << logLevelToString(level) << "] " << logMessage << std::endl;
    }
}

void Logger::logCudaOperation(const std::string& operation, const std::string& kernelName,
                              int blockSize, int gridSize, bool success, const std::string& errorMsg) {
    if (!initialized) return;

    std::ostringstream oss;
    oss << "CUDA [" << operation << "] Kernel: " << kernelName;
    oss << " - Grid: " << gridSize << "x" << blockSize;
    oss << " - " << (success ? "SUCCESS" : "FAILED");
    if (!success && !errorMsg.empty()) {
        oss << " - Error: " << errorMsg;
    }

    std::string logMessage = oss.str();
    LogLevel level = success ? LogLevel::INFO : LogLevel::ERROR;

    if (logFile.is_open()) {
        logFile << getCurrentTimestamp() << " [" << logLevelToString(level) << "] " << logMessage << std::endl;
        logFile.flush();
    }

    if (consoleOutput) {
        std::cout << getCurrentTimestamp() << " [" << logLevelToString(level) << "] " << logMessage << std::endl;
    }
}

void Logger::logMemoryAllocation(const std::string& device, size_t sizeBytes, const std::string& purpose) {
    if (!initialized) return;

    std::ostringstream oss;
    oss << "MEMORY [" << device << "] ALLOC: " << sizeBytes << " bytes";
    if (!purpose.empty()) {
        oss << " - Purpose: " << purpose;
    }

    std::string logMessage = oss.str();

    if (logFile.is_open()) {
        logFile << getCurrentTimestamp() << " [MEMORY] " << logMessage << std::endl;
        logFile.flush();
    }

    if (consoleOutput) {
        std::cout << getCurrentTimestamp() << " [MEMORY] " << logMessage << std::endl;
    }
}

void Logger::logMemoryDeallocation(const std::string& device, size_t sizeBytes, const std::string& purpose) {
    if (!initialized) return;

    std::ostringstream oss;
    oss << "MEMORY [" << device << "] DEALLOC: " << sizeBytes << " bytes";
    if (!purpose.empty()) {
        oss << " - Purpose: " << purpose;
    }

    std::string logMessage = oss.str();

    if (logFile.is_open()) {
        logFile << getCurrentTimestamp() << " [MEMORY] " << logMessage << std::endl;
        logFile.flush();
    }

    if (consoleOutput) {
        std::cout << getCurrentTimestamp() << " [MEMORY] " << logMessage << std::endl;
    }
}

void Logger::setLogLevel(LogLevel level) {
    currentLevel = level;
}

void Logger::flush() {
    if (logFile.is_open()) {
        logFile.flush();
    }
}

void Logger::close() {
    if (initialized) {
        std::string closeMsg = "=== Traffic Sign Recognition Application Logger Closing ===";
        if (logFile.is_open()) {
            logFile << getCurrentTimestamp() << " [SHUTDOWN] " << closeMsg << std::endl;
            logFile.flush();
            logFile.close();
        }
        if (consoleOutput) {
            std::cout << getCurrentTimestamp() << " [SHUTDOWN] " << closeMsg << std::endl;
        }
    }

    initialized = false;
}

void Logger::log(LogLevel level, const std::string& message, const std::string& component) {
    if (!initialized || level < currentLevel) return;

    std::lock_guard<std::mutex> lock(logMutex);
    std::ostringstream oss;
    oss << message;
    if (!component.empty()) {
        oss << " [Component: " << component << "]";
    }

    std::string logMessage = oss.str();

    if (logFile.is_open()) {
        logFile << getCurrentTimestamp() << " [" << logLevelToString(level) << "] " << logMessage << std::endl;
        logFile.flush();
    }

    if (consoleOutput) {
        std::cout << getCurrentTimestamp() << " [" << logLevelToString(level) << "] " << logMessage << std::endl;
    }
}

std::string Logger::getCurrentTimestamp() {
    auto now = std::chrono::system_clock::now();
    auto time_t = std::chrono::system_clock::to_time_t(now);
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
            now.time_since_epoch()) % 1000;

    std::ostringstream oss;
    oss << std::put_time(std::localtime(&time_t), "%Y-%m-%d %H:%M:%S");
    oss << '.' << std::setfill('0') << std::setw(3) << ms.count();

    return oss.str();
}

std::string Logger::logLevelToString(LogLevel level) {
    switch (level) {
        case LogLevel::DEBUG: return "DEBUG";
        case LogLevel::INFO: return "INFO";
        case LogLevel::WARNING: return "WARNING";
        case LogLevel::ERROR: return "ERROR";
        case LogLevel::CRITICAL: return "CRITICAL";
        default: return "UNKNOWN";
    }
}

std::string Logger::formatShape(const std::vector<int>& shape) {
    std::ostringstream oss;
    oss << "[";
    for (size_t i = 0; i < shape.size(); ++i) {
        if (i > 0) oss << ", ";
        oss << shape[i];
    }
    oss << "]";
    return oss.str();
}