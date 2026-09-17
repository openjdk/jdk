SET (CMAKE_CROSSCOMPILING   TRUE)
SET (CMAKE_SYSTEM_NAME      "Linux")
SET (CMAKE_SYSTEM_PROCESSOR "ppc64")

SET(CMAKE_FIND_ROOT_PATH /usr/powerpc64le-linux-gnu /usr/include/powerpc64le-linux-gnu /usr/lib/powerpc64le-linux-gnu)

execute_process(COMMAND bash -c "compgen -c | egrep '^powerpc64le-linux-gnu-gcc(-[0-9]+(\\.[0-9]+\\.[0-9]+)?)?$' | sort -nr | uniq" OUTPUT_VARIABLE GCC_CANDIDATES)
string(REPLACE "\n" ";" GCC_CANDIDATES "${GCC_CANDIDATES}")
execute_process(COMMAND bash -c "compgen -c | egrep '^powerpc64le-linux-gnu-g\\+\\+(-[0-9]+(\\.[0-9]+\\.[0-9]+)?)?$' | sort -nr | uniq" OUTPUT_VARIABLE GXX_CANDIDATES)
string(REPLACE "\n" ";" GXX_CANDIDATES "${GXX_CANDIDATES}")

find_program(CMAKE_C_COMPILER NAMES ${GCC_CANDIDATES})
find_program(CMAKE_CXX_COMPILER NAMES ${GXX_CANDIDATES})

SET(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
SET(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
SET(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
