SET (CMAKE_CROSSCOMPILING   TRUE)
SET (CMAKE_SYSTEM_NAME      "Linux")
SET (CMAKE_SYSTEM_PROCESSOR "armhf")

SET(CMAKE_FIND_ROOT_PATH  /usr/arm-linux-gnueabihf /usr/include/arm-linux-gnueabihf /usr/lib/arm-linux-gnueabihf)

find_program(CMAKE_C_COMPILER NAMES clang-17 clang-16 clang-15 clang-14 clang-13 clang)
set(CMAKE_C_COMPILER_TARGET arm-linux-gnueabihf)

SET(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
SET(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
SET(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
