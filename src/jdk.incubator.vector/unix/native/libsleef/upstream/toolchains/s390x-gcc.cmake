SET (CMAKE_CROSSCOMPILING   TRUE)
SET (CMAKE_SYSTEM_NAME      "Linux")
SET (CMAKE_SYSTEM_PROCESSOR "s390x")

SET(CMAKE_FIND_ROOT_PATH /usr/s390x-linux-gnu /usr/include/s390x-linux-gnu /usr/lib/s390x-linux-gnu)

execute_process(COMMAND bash -c "compgen -c | egrep '^s390x-linux-gnu-gcc(-[0-9]+(\\.[0-9]+\\.[0-9]+)?)?$' | sort -nr | uniq" OUTPUT_VARIABLE GCC_CANDIDATES)
string(REPLACE "\n" ";" GCC_CANDIDATES "${GCC_CANDIDATES}")
execute_process(COMMAND bash -c "compgen -c | egrep '^s390x-linux-gnu-g\\+\\+(-[0-9]+(\\.[0-9]+\\.[0-9]+)?)?$' | sort -nr | uniq" OUTPUT_VARIABLE GXX_CANDIDATES)
string(REPLACE "\n" ";" GXX_CANDIDATES "${GXX_CANDIDATES}")

find_program(CMAKE_C_COMPILER NAMES ${GCC_CANDIDATES})
find_program(CMAKE_CXX_COMPILER NAMES ${GXX_CANDIDATES})

SET(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
SET(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
SET(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
