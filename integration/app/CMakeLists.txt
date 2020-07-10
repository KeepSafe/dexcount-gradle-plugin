cmake_minimum_required(VERSION 3.10.2)

find_library(log-lib log)

add_library(dummy SHARED src/main/cpp/lib.cpp)

target_link_libraries(dummy ${log-lib})
