#include "tensor.hpp"
#include "logger.hpp"
#include <algorithm>
#include <iostream>
#include <ostream>
#include <set>
#include <cmath>

Tensor::Tensor() : device(Device::CPU) {}

Tensor::Tensor(const std::vector<int>& shape_, Device dev) : shape(shape_), device(dev) {
    totalSize = std::accumulate(shape.begin(), shape.end(), 1, std::multiplies<int>());
    contiguous = true;
    cpuData.resize(totalSize, 0.0f);
    cpuGrad.resize(totalSize, 0.0f);
    computeStrides();

    // Log tensor creation
    std::string deviceStr = (device == Device::CPU) ? "CPU" : "GPU";
    LOG_TENSOR_OP("CREATE", deviceStr, shape, true, "");
    LOG_MEMORY_ALLOC("CPU", totalSize * sizeof(float) * 2, "Tensor data and gradients");

#ifdef USE_CUDA
    if (device == Device::GPU) {
		toGpu();
	}
#endif
}

Tensor::~Tensor() {
    // Log tensor destruction
    std::string deviceStr = (device == Device::CPU) ? "CPU" : "GPU";
    LOG_TENSOR_OP("DESTROY", deviceStr, shape, true, "");
    LOG_MEMORY_DEALLOC("CPU", totalSize * sizeof(float) * 2, "Tensor data and gradients");

#ifdef USE_CUDA
    freeGpuMemory();
#endif
}

void Tensor::computeStrides() { // contiguous!!
    strides.resize(shape.size());
    int stride = 1;
    for (int i = shape.size() - 1; i >= 0; --i) {
        strides[i] = stride;
        stride *= shape[i];
    }
}

float& Tensor::at(const std::vector<int>& index) {
    assert(index.size() == shape.size());
#ifdef USE_CUDA
    if (device == Device::GPU) copyCpu();
#endif
    int local = 0;
    for (std::size_t i = 0; i < shape.size(); ++i) {
        assert(index[i] >= 0 && index[i] < shape[i]);
        local += index[i] * strides[i];
    }
    return cpuData[local];
}

void Tensor::edit(const std::vector<int>& index, float val) {
    assert(index.size() == shape.size());
#ifdef USE_CUDA // this edit function needs to be made more efficient, it currently SUCKS
    if (device == Device::GPU) copyCpu();
#endif
    int local = 0;
    for (std::size_t i = 0; i < shape.size(); ++i) {
        assert(index[i] >= 0 && index[i] < shape[i]);
        local += index[i] * strides[i];
    }
    cpuData[local] = val;
#ifdef USE_CUDA
    if (device == Device::GPU) copyGpu();
#endif
}

void Tensor::printShape() {
    std::cout << "Tensor shape: [";
    for (size_t i = 0; i < shape.size(); ++i) {
        std::cout << shape[i];
        if (i != shape.size() - 1) std::cout << ", ";
    }
    std::cout << "]" << std::endl;
}

void Tensor::printData() {
#ifdef USE_CUDA
    if (device == Device::GPU) copyCpu();
#endif
    std::cout << "Tensor data: [";
    for (size_t i = 0; i < cpuData.size(); ++i) {
        std::cout << cpuData[i];
        if (i != cpuData.size() - 1) std::cout << ", ";
    }
    std::cout << "]" << std::endl;
}

void Tensor::printImageTensor() {
    assert(shape.size() == 4);
#ifdef USE_CUDA
    if (device == Device::GPU) copyCpu();
#endif
    int N = shape[0];
    int C = shape[1];
    int H = shape[2];
    int W = shape[3];

    for (int n = 0; n < N; ++n) {
        std::cout << "Batch " << n << ":\n";
        for (int c = 0; c < C; ++c) {
            std::cout << " Channel " << c << ":\n";
            for (int h = 0; h < H; ++h) {
                for (int w = 0; w < W; ++w) {
                    std::cout << cpuData[n * strides[0] + c * strides[1] + h * strides[2] + w * strides[3]] << " ";
                }
                std::cout << "\n";
            }
            std::cout << "\n";
        }
        std::cout << "------------------\n";
    }
}

void Tensor::reshape(const std::vector<int>& shape_) {
    if (!contiguous) makeContiguous(); // we ONLY operate on contiguous tensors!

    int newSize = std::accumulate(shape_.begin(), shape_.end(), 1, std::multiplies<int>());
    assert(totalSize == newSize);
    shape = shape_;
    computeStrides();
}

void Tensor::flatten() {
    if (!contiguous) makeContiguous();

    shape = {totalSize};
    strides = {1};
}

void Tensor::transpose(const std::vector<int>& order) {
    assert(order.size() == shape.size());

    std::set<int> elem(order.begin(), order.end());
    assert(elem.size() == shape.size());

    std::vector<int> newShape(shape.size());
    std::vector<int> newStrides(strides.size());

    for (int i = 0; i < shape.size(); ++i) {
        assert(order[i] >= 0 && order[i] < shape.size());
        newShape[i] = shape[order[i]];
        newStrides[i] = strides[order[i]];
    }

    shape = newShape;
    strides = newStrides;
    contiguous = false;
}

Tensor Tensor::broadcast(const std::vector<int>& newShape) {
    assert(newShape.size() >= shape.size());

    for (int i = 0; i < (int)shape.size(); ++i) {
        int dim = shape[shape.size() - 1 - i];
        int newDim = newShape[newShape.size() - 1 - i];
        assert(newDim == dim || dim == 1); // check if shape is broadcastable
    }

    // Ensure contiguous layout before broadcasting
    if (!contiguous) makeContiguous();

    Tensor result(newShape, device);

    // Compute contiguous strides for the source tensor
    std::vector<int> mult(shape.size(), 0);
    if (!shape.empty()) {
        mult[shape.size() - 1] = 1;
        for (int i = (int)shape.size() - 2; i >= 0; --i) {
            mult[i] = mult[i + 1] * shape[i + 1];
        }
    }

    std::vector<int> index(newShape.size(), 0);
    for (int i = 0; i < result.totalSize; ++i) {
        int lin = 0;
        for (int j = 0; j < (int)shape.size(); ++j) {
            int target = (int)newShape.size() - (int)shape.size() + j;
            int idx = (shape[j] == 1) ? 0 : index[target];
            lin += idx * mult[j];
        }

        result.cpuData[i] = cpuData[lin];

        // Advance index in the OUTPUT (newShape) space — was previously wrong (used shape[j])
        for (int j = (int)newShape.size() - 1; j >= 0; --j) {
            index[j]++;
            if (index[j] < newShape[j]) break;
            index[j] = 0;
        }
    }

    return result;
}

void Tensor::makeContiguous() {
    if (contiguous) return;
    if (device == Device::CPU) {
        makeContiguousCpu();
    }
#ifdef USE_CUDA
    else {
        makeContiguousGpu();
    }
#endif
}

void Tensor::makeContiguousCpu() {
    if (contiguous) return;

    std::vector<float> newData(totalSize);

    std::vector<int> index(shape.size(), 0);
    for (int i = 0; i < totalSize; ++i) {
        int linIdx = 0;
        for (int j = 0; j < shape.size(); ++j) { // allows for multi dimensional work!!
            linIdx += index[j] * strides[j];
        }

        newData[i] = cpuData[linIdx];

        for (int j = shape.size() - 1; j >= 0; --j) {
            index[j]++;
            if (index[j] < shape[j]) break;
            index[j] = 0;
        }
    }

    cpuData = newData;
    contiguous = true;
    computeStrides();
}


void Tensor::fillCpu(float val) {
    std::fill(cpuData.begin(), cpuData.end(), val);
}

void Tensor::addTensorCpu(const Tensor &other) {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] += other.cpuData[i];
    }
}

void Tensor::addScalarCpu(const float val) {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] += val;
    }
}

void Tensor::addBiasCpu(const Tensor &bias) {
    int N = shape[0], C = shape[1], H = shape[2], W = shape[3];
    for (int n = 0; n < N; ++n) {
        for (int c = 0; c < C; ++c) {
            float b = bias.cpuData[c];
            for (int h = 0; h < H; ++h) {
                for (int w = 0; w < W; ++w) {
                    int idx = n * strides[0] + c * strides[1] + h * strides[2] + w * strides[3];
                    cpuData[idx] += b;
                }
            }
        }
    }
}

void Tensor::subtractTensorCpu(const Tensor &other) {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] -= other.cpuData[i];
    }
}

void Tensor::subtractScalarCpu(const float val) {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] -= val;
    }
}

void Tensor::multiplyTensorCpu(const Tensor &other) {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] *= other.cpuData[i];
    }
}

void Tensor::multiplyScalarCpu(const float val) {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] *= val;
    }
}

void Tensor::multiplyBiasCpu(const Tensor &bias) {
    int N = shape[0], C = shape[1], H = shape[2], W = shape[3];
    for (int n = 0; n < N; ++n) {
        for (int c = 0; c < C; ++c) {
            float b = bias.cpuData[c];
            for (int h = 0; h < H; ++h) {
                for (int w = 0; w < W; ++w) {
                    int idx = n * strides[0] + c * strides[1] + h * strides[2] + w * strides[3];
                    cpuData[idx] *= b;
                }
            }
        }
    }
}

void Tensor::divideTensorCpu(const Tensor &other) {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] /= other.cpuData[i];
    }
}

void Tensor::divideScalarCpu(const float val) {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] /= val;
    }
}

void Tensor::negateCpu() { // bitwise hacking not allowed
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] = -cpuData[i];
    }
}

void Tensor::ReLUCpu() {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] = fmaxf(cpuData[i], 0.0f);
    }
}

void Tensor::sigmoidCpu() {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] = 1.0f / (1.0f + expf(-cpuData[i]));
    }
}

void Tensor::tanhCpu() {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] = tanhf(cpuData[i]);
    }
}

void Tensor::LReLUCpu(const float alpha) {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] = cpuData[i] < 0 ? alpha * cpuData[i] : cpuData[i];
    }
}

void Tensor::ELUCpu(const float alpha) {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] = cpuData[i] < 0 ? alpha * (expf(cpuData[i]) - 1) : cpuData[i];
    }
}

void Tensor::squareCpu() {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] = cpuData[i] * cpuData[i];
    }
}

void Tensor::sqrtCpu() {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] = sqrtf(cpuData[i]);
    }
}

void Tensor::expCpu() {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] = expf(cpuData[i]);
    }
}

void Tensor::logCpu() {
    for (std::size_t i = 0; i < cpuData.size(); ++i) {
        cpuData[i] = logf(cpuData[i]);
    }
}

void Tensor::zeroGradCpu() {
    std::fill(cpuGrad.begin(), cpuGrad.end(), 0.0f);
}


void Tensor::fill(const float val) {
    std::string deviceStr = (device == Device::CPU) ? "CPU" : "GPU";
    LOG_DEBUG("Filling tensor with value: " + std::to_string(val));

    if (device == Device::CPU) {
        fillCpu(val);
    }
#ifdef USE_CUDA
    else {
        fillGpu(val);
  	}
#endif

    LOG_TENSOR_OP("FILL", deviceStr, shape, true, "");
}

void Tensor::addTensor(const Tensor& other) {
    assert(shape == other.shape);
    std::string deviceStr = (device == Device::CPU) ? "CPU" : "GPU";

    if (device == Device::CPU) {
        addTensorCpu(other);
    }
#ifdef USE_CUDA
    else {
        addTensorGpu(other);
    }
#endif

    LOG_TENSOR_OP("ADD_TENSOR", deviceStr, shape, true, "");
}

void Tensor::addScalar(const float val) {
    if (device == Device::CPU) {
        addScalarCpu(val);
    }
#ifdef USE_CUDA
    else {
        addScalarGpu(val);
    }
#endif
}

void Tensor::addBias(const Tensor& bias) {
    assert(shape.size() == 4); // image channeled bias
    assert(bias.shape.size() == 1); // bias application
    assert(shape[1] == bias.shape[0]); // bias can be properly added

    std::string deviceStr = (device == Device::CPU) ? "CPU" : "GPU";
    LOG_DEBUG("Adding bias to tensor with shape " + std::to_string(shape[1]) + " channels");

    if (device == Device::CPU) {
        addBiasCpu(bias);
    }
#ifdef USE_CUDA
    else {
        addBiasGpu(bias);
    }
#endif

    LOG_TENSOR_OP("ADD_BIAS", deviceStr, shape, true, "");
}

void Tensor::subtractTensor(const Tensor& other) {
    assert(shape == other.shape);
    if (device == Device::CPU) {
        subtractTensorCpu(other);
    }
#ifdef USE_CUDA
    else {
        subtractTensorGpu(other);
    }
#endif
}

void Tensor::subtractScalar(const float val) {
    if (device == Device::CPU) {
        subtractScalarCpu(val);
    }
#ifdef USE_CUDA
    else {
        subtractScalarGpu(val);
    }
#endif
}

void Tensor::multiplyTensor(const Tensor& other) {
    assert(shape == other.shape);
    if (device == Device::CPU) {
        multiplyTensorCpu(other);
    }
#ifdef USE_CUDA
    else {
        multiplyTensorGpu(other);
    }
#endif
}

void Tensor::multiplyScalar(const float val) {
    if (device == Device::CPU) {
        multiplyScalarCpu(val);
    }
#ifdef USE_CUDA
    else {
        multiplyScalarGpu(val);
    }
#endif
}

void Tensor::multiplyBias(const Tensor& bias) { // image only
    assert(shape.size() == 4);
    assert(bias.shape.size() == 1);
    assert(shape[1] == bias.shape[0]);
    if (device == Device::CPU) {
        multiplyBiasCpu(bias);
    }
#ifdef USE_CUDA
    else {
        multiplyBiasGpu(bias);
    }
#endif
}

void Tensor::divideTensor(const Tensor& other) {
    assert(shape == other.shape);
    if (device == Device::CPU) {
        divideTensorCpu(other);
    }
#ifdef USE_CUDA
    else {
        divideTensorGpu(other);
    }
#endif
}

void Tensor::divideScalar(const float val) {
    if (device == Device::CPU) {
        divideScalarCpu(val);
    }
#ifdef USE_CUDA
    else {
        divideScalarGpu(val);
    }
#endif
}

void Tensor::negate() {
    if (device == Device::CPU) {
        negateCpu();
    }
#ifdef USE_CUDA
    else {
        negateGpu();
    }
#endif
}

void Tensor::ReLU() {
    if (device == Device::CPU) {
        ReLUCpu();
    }
#ifdef USE_CUDA
    else {
        ReLUGpu();
    }
#endif
}

void Tensor::sigmoid() {
    if (device == Device::CPU) {
        sigmoidCpu();
    }
#ifdef USE_CUDA
    else {
        sigmoidGpu();
    }
#endif
}

void Tensor::tanh() {
    if (device == Device::CPU) {
        tanhCpu();
    }
#ifdef USE_CUDA
    else {
        tanhGpu();
    }
#endif
}

void Tensor::LReLU(const float alpha) {
    if (device == Device::CPU) {
        LReLUCpu(alpha);
    }
#ifdef USE_CUDA
    else {
        LReLUGpu(alpha);
    }
#endif
}

void Tensor::ELU(const float alpha) {
    if (device == Device::CPU) {
        ELUCpu(alpha);
    }
#ifdef USE_CUDA
    else {
        ELUGpu(alpha);
    }
#endif
}

void Tensor::square() {
    if (device == Device::CPU) {
        squareCpu();
    }
#ifdef USE_CUDA
    else {
        squareGpu();
    }
#endif
}

void Tensor::sqrt() {
    if (device == Device::CPU) {
        sqrtCpu();
    }
#ifdef USE_CUDA
    else {
        sqrtGpu();
    }
#endif
}

void Tensor::exp() {
    if (device == Device::CPU) {
        expCpu();
    }
#ifdef USE_CUDA
    else {
        expGpu();
    }
#endif
}

void Tensor::log() {
    if (device == Device::CPU) {
        logCpu();
    }
#ifdef USE_CUDA
    else {
        logGpu();
    }
#endif
}

void Tensor::zeroGrad() {
    if (device == Device::CPU) {
        zeroGradCpu();
    }
#ifdef USE_CUDA
    else {
        zeroGradGpu();
    }
#endif
}