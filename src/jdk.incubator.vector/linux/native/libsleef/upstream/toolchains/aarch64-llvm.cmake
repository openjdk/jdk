SET (CMAKE_CROSSCOMPILING   TRUE)
SET (CMAKE_SYSTEM_NAME      "Linux")
SET (CMAKE_SYSTEM_PROCESSOR "aarch64")

SET(CMAKE_FIND_ROOT_PATH  /usr/aarch64-linux-gnu /usr/include/aarch64-linux-gnu /usr/lib/aarch64-linux-gnu /lib/aarch64-linux-gnu)

find_program(CMAKE_C_COMPILER NAMES clang-17 clang-16 clang-15 clang-14 clang-13 clang)
set(CMAKE_C_COMPILER_TARGET aarch64-linux-gnu)

SET(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
SET(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
SET(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
