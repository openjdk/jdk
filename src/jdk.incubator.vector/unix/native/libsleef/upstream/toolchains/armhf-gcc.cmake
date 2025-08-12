SET (CMAKE_CROSSCOMPILING   TRUE)
SET (CMAKE_SYSTEM_NAME      "Linux")
SET (CMAKE_SYSTEM_PROCESSOR "armhf")

SET(CMAKE_FIND_ROOT_PATH  /usr/arm-linux-gnueabihf /usr/include/arm-linux-gnueabihf /usr/lib/arm-linux-gnueabihf)

find_program(CMAKE_C_COMPILER NAMES arm-linux-gnueabihf-gcc-11 arm-linux-gnueabihf-gcc-8 arm-linux-gnueabihf-gcc-7 arm-linux-gnueabihf-gcc-6 arm-linux-gnueabihf-gcc-5 arm-linux-gnueabihf-gcc)

SET(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
SET(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
SET(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
