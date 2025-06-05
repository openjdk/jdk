SET (CMAKE_CROSSCOMPILING   TRUE)
SET (CMAKE_SYSTEM_NAME      "Linux")
SET (CMAKE_SYSTEM_PROCESSOR "ppc64")

SET(CMAKE_FIND_ROOT_PATH  /usr/powerpc64le-linux-gnu /usr/include/powerpc64le-linux-gnu /usr/lib/powerpc64le-linux-gnu)

find_program(CMAKE_C_COMPILER NAMES clang-17 clang-16 clang-15 clang-14 clang-13 clang)
set(CMAKE_C_COMPILER_TARGET powerpc64le-linux-gnu)

SET(CMAKE_AR /usr/powerpc64le-linux-gnu/bin/ar)

SET(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
SET(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
SET(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
