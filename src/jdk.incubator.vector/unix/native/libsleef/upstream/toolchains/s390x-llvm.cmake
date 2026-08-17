SET (CMAKE_CROSSCOMPILING   TRUE)
SET (CMAKE_SYSTEM_NAME      "Linux")
SET (CMAKE_SYSTEM_PROCESSOR "s390x")

SET(CMAKE_FIND_ROOT_PATH /usr/s390x-linux-gnu /usr/include/s390x-linux-gnu /usr/lib/s390x-linux-gnu)

execute_process(COMMAND bash -c "compgen -c | egrep '^clang(-[0-9]+(\\.[0-9]+\\.[0-9]+)?)?$' | sort -nr | uniq" OUTPUT_VARIABLE CLANG_CANDIDATES)
string(REPLACE "\n" ";" CLANG_CANDIDATES "${CLANG_CANDIDATES}")
execute_process(COMMAND bash -c "compgen -c | egrep '^clang\\+\\+(-[0-9]+(\\.[0-9]+\\.[0-9]+)?)?$' | sort -nr | uniq" OUTPUT_VARIABLE CLANGXX_CANDIDATES)
string(REPLACE "\n" ";" CLANGXX_CANDIDATES "${CLANGXX_CANDIDATES}")

find_program(CMAKE_C_COMPILER NAMES ${CLANG_CANDIDATES})
set(CMAKE_C_COMPILER_TARGET s390x-linux-gnu)
find_program(CMAKE_CXX_COMPILER NAMES ${CLANGXX_CANDIDATES})
set(CMAKE_CXX_COMPILER_TARGET s390x-linux-gnu)

SET(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
SET(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)
SET(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
