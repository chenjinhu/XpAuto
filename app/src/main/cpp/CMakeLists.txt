cmake_minimum_required(VERSION 3.22.1)
project(xpauto_inspector LANGUAGES CXX)

add_library(xpauto-inspector SHARED inspector_interrupt.cpp script_blocking.cpp)
target_compile_features(xpauto-inspector PRIVATE cxx_std_20)
target_compile_options(xpauto-inspector PRIVATE -Wall -Wextra -Werror)
target_link_libraries(xpauto-inspector PRIVATE dl)
