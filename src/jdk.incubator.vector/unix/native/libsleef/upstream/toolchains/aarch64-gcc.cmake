SET (CMAKE_CROSSCOMPILING   TRUE)
SET (CMAKE_SYSTEM_NAME      "Linux")
SET (CMAKE_SYSTEM_PROCESSOR "aarch64")

SET(CMAKE_FIND_ROOT_PATH  /usr/aarch64-linux-gnu /usr/include/aarch64-linux-gnu /usr/lib/aarch64-linux-gnu /lib/aarch64-linux-gnu)

find_program(CMAKE_C_COMPILER NAMES aarch64-linux-gnu-gcc-11 aarch64-linux-gnu-gcc-8 aarch64-linux-gnu-gcc-7 aarch64-linux-gnu-gcc-6 aarch64-linux-gnu-gcc-5 aarch64-linux-gnu-gcc)

SET(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
SET(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
SET(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
