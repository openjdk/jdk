SET (CMAKE_CROSSCOMPILING   TRUE)
SET (CMAKE_SYSTEM_NAME      "Linux")
SET (CMAKE_SYSTEM_PROCESSOR "ppc64")

SET(CMAKE_FIND_ROOT_PATH  /usr/powerpc64le-linux-gnu /usr/include/powerpc64le-linux-gnu /usr/lib/powerpc64le-linux-gnu)

find_program(CMAKE_C_COMPILER NAMES powerpc64le-linux-gnu-gcc-11 powerpc64le-linux-gnu-gcc ppc64el-cc)

SET(CMAKE_AR /usr/powerpc64le-linux-gnu/bin/ar)

SET(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
SET(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
SET(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
