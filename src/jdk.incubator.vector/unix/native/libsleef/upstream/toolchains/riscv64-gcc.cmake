SET (CMAKE_CROSSCOMPILING   TRUE)
SET (CMAKE_SYSTEM_NAME      "Linux")
SET (CMAKE_SYSTEM_PROCESSOR "riscv64")

SET(CMAKE_FIND_ROOT_PATH  /usr/riscv64-linux-gnu /usr/include/riscv64-linux-gnu /usr/lib/riscv64-linux-gnu /lib/riscv64-linux-gnu)

find_program(CMAKE_C_COMPILER NAMES riscv64-linux-gnu-gcc-14 riscv64-linux-gnu-gcc)

SET(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
SET(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
SET(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
