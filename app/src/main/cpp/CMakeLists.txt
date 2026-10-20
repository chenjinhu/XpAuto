cmake_minimum_required(VERSION 3.22.1)
project(xpauto_inspector LANGUAGES CXX)

add_library(xpauto-inspector SHARED inspector_interrupt.cpp script_blocking.cpp)
target_compile_features(xpauto-inspector PRIVATE cxx_std_20)
target_compile_options(xpauto-inspector PRIVATE -Wall -Wextra -Werror)
# NDK r27 默认使用 4 KB 页；同时对齐 LOAD 段与 RELRO，兼容 4 KB 和 16 KB 设备。
target_link_options(xpauto-inspector PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
target_link_libraries(xpauto-inspector PRIVATE dl)
