
# Options

if (COMPILER_SUPPORTS_SVE)
  set(SLEEFDFT_MAXBUTWIDTH 6 CACHE STRING "Log_2 (Maximum butterfly length) of butterflies")
else()
  set(SLEEFDFT_MAXBUTWIDTH 4 CACHE STRING "Log_2 (Maximum butterfly length) of butterflies")
endif()

if (SLEEFDFT_MAXBUTWIDTH GREATER 7)
  message(FATAL_ERROR "SLEEFDFT_MAXBUTWIDTH has to be smaller than 8." )
endif()

# Settings

# Constants definition

set(LISTSHORTTYPENAME "dp" "sp")
set(LISTLONGTYPENAME "double" "float")
set(LISTTYPEID "1" "2")

set(MACRODEF_purecdp BASETYPEID=1 ENABLE_PUREC CONFIG=1)
set(CFLAGS_purecdp ${FLAGS_ENABLE_PUREC})
set(MACRODEF_purecsp BASETYPEID=2 ENABLE_PUREC CONFIG=1)
set(CFLAGS_purecsp ${FLAGS_ENABLE_PUREC})
if(CMAKE_C_COMPILER_ID MATCHES "Clang")
set(CFLAGS_purecdp ${FLAGS_ENABLE_PUREC} -O0)
set(CFLAGS_purecsp ${FLAGS_ENABLE_PUREC} -O0)
endif()
set(MACRODEF_purecld BASETYPEID=3 ENABLE_PUREC CONFIG=1)
set(CFLAGS_purecld ${FLAGS_ENABLE_PUREC})
set(MACRODEF_purecqp BASETYPEID=4 ENABLE_PUREC CONFIG=1)
set(CFLAGS_purecqp ${FLAGS_ENABLE_PUREC})
set(MACRODEF_sse2dp BASETYPEID=1 ENABLE_SSE2 CONFIG=2)
set(CFLAGS_sse2dp ${FLAGS_ENABLE_SSE2})
set(MACRODEF_sse2sp BASETYPEID=2 ENABLE_SSE2 CONFIG=2)
set(CFLAGS_sse2sp ${FLAGS_ENABLE_SSE2})
set(MACRODEF_avx2dp BASETYPEID=1 ENABLE_AVX2 CONFIG=1)
set(CFLAGS_avx2dp ${FLAGS_ENABLE_AVX2})
set(MACRODEF_avx2sp BASETYPEID=2 ENABLE_AVX2 CONFIG=1)
set(CFLAGS_avx2sp ${FLAGS_ENABLE_AVX2})
set(MACRODEF_avx512fdp BASETYPEID=1 ENABLE_AVX512F CONFIG=1)
set(CFLAGS_avx512fdp ${FLAGS_ENABLE_AVX512F})
set(MACRODEF_avx512fsp BASETYPEID=2 ENABLE_AVX512F CONFIG=1)
set(CFLAGS_avx512fsp ${FLAGS_ENABLE_AVX512F})
set(MACRODEF_advsimddp BASETYPEID=1 ENABLE_ADVSIMD CONFIG=1)
set(CFLAGS_advsimddp ${FLAGS_ENABLE_ADVSIMD})
set(MACRODEF_advsimdsp BASETYPEID=2 ENABLE_ADVSIMD CONFIG=1)
set(CFLAGS_advsimdsp ${FLAGS_ENABLE_ADVSIMD})
set(MACRODEF_sve256dp BASETYPEID=1 ENABLE_SVE CONFIG=8)
set(CFLAGS_sve256dp ${FLAGS_ENABLE_SVE})
set(MACRODEF_sve256sp BASETYPEID=2 ENABLE_SVE CONFIG=8)
set(CFLAGS_sve256sp ${FLAGS_ENABLE_SVE})
set(MACRODEF_sve512dp BASETYPEID=1 ENABLE_SVE CONFIG=9)
set(CFLAGS_sve512dp ${FLAGS_ENABLE_SVE})
set(MACRODEF_sve512sp BASETYPEID=2 ENABLE_SVE CONFIG=9)
set(CFLAGS_sve512sp ${FLAGS_ENABLE_SVE})
set(MACRODEF_sve1024dp BASETYPEID=1 ENABLE_SVE CONFIG=10)
set(CFLAGS_sve1024dp ${FLAGS_ENABLE_SVE})
set(MACRODEF_sve1024sp BASETYPEID=2 ENABLE_SVE CONFIG=10)
set(CFLAGS_sve1024sp ${FLAGS_ENABLE_SVE})
set(MACRODEF_sve2048dp BASETYPEID=1 ENABLE_SVE CONFIG=11)
set(CFLAGS_sve2048dp ${FLAGS_ENABLE_SVE})
set(MACRODEF_sve2048sp BASETYPEID=2 ENABLE_SVE CONFIG=11)
set(CFLAGS_sve2048sp ${FLAGS_ENABLE_SVE})
set(MACRODEF_rvvm1128dp BASETYPEID=1 ENABLE_RVVM1 CONFIG=7)
set(CFLAGS_rvvm1128dp ${FLAGS_ENABLE_RVVM1})
set(MACRODEF_rvvm1128sp BASETYPEID=2 ENABLE_RVVM1 CONFIG=7)
set(CFLAGS_rvvm1128sp ${FLAGS_ENABLE_RVVM1})
set(MACRODEF_rvvm1256dp BASETYPEID=1 ENABLE_RVVM1 CONFIG=8)
set(CFLAGS_rvvm1256dp ${FLAGS_ENABLE_RVVM1})
set(MACRODEF_rvvm1256sp BASETYPEID=2 ENABLE_RVVM1 CONFIG=8)
set(CFLAGS_rvvm1256sp ${FLAGS_ENABLE_RVVM1})
set(MACRODEF_rvvm1512dp BASETYPEID=1 ENABLE_RVVM1 CONFIG=9)
set(CFLAGS_rvvm1512dp ${FLAGS_ENABLE_RVVM1})
set(MACRODEF_rvvm1512sp BASETYPEID=2 ENABLE_RVVM1 CONFIG=9)
set(CFLAGS_rvvm1512sp ${FLAGS_ENABLE_RVVM1})
set(MACRODEF_rvvm11024dp BASETYPEID=1 ENABLE_RVVM1 CONFIG=10)
set(CFLAGS_rvvm11024dp ${FLAGS_ENABLE_RVVM1})
set(MACRODEF_rvvm11024sp BASETYPEID=2 ENABLE_RVVM1 CONFIG=10)
set(CFLAGS_rvvm11024sp ${FLAGS_ENABLE_RVVM1})
set(MACRODEF_rvvm12048dp BASETYPEID=1 ENABLE_RVVM1 CONFIG=11)
set(CFLAGS_rvvm12048dp ${FLAGS_ENABLE_RVVM1})
set(MACRODEF_rvvm12048sp BASETYPEID=2 ENABLE_RVVM1 CONFIG=11)
set(CFLAGS_rvvm12048sp ${FLAGS_ENABLE_RVVM1})
set(MACRODEF_rvvm2128dp BASETYPEID=1 ENABLE_RVVM2 CONFIG=7)
set(CFLAGS_rvvm2128dp ${FLAGS_ENABLE_RVVM2})
set(MACRODEF_rvvm2128sp BASETYPEID=2 ENABLE_RVVM2 CONFIG=7)
set(CFLAGS_rvvm2128sp ${FLAGS_ENABLE_RVVM2})
set(MACRODEF_rvvm2256dp BASETYPEID=1 ENABLE_RVVM2 CONFIG=8)
set(CFLAGS_rvvm2256dp ${FLAGS_ENABLE_RVVM2})
set(MACRODEF_rvvm2256sp BASETYPEID=2 ENABLE_RVVM2 CONFIG=8)
set(CFLAGS_rvvm2256sp ${FLAGS_ENABLE_RVVM2})
set(MACRODEF_rvvm2512dp BASETYPEID=1 ENABLE_RVVM2 CONFIG=9)
set(CFLAGS_rvvm2512dp ${FLAGS_ENABLE_RVVM2})
set(MACRODEF_rvvm2512sp BASETYPEID=2 ENABLE_RVVM2 CONFIG=9)
set(CFLAGS_rvvm2512sp ${FLAGS_ENABLE_RVVM2})
set(MACRODEF_rvvm21024dp BASETYPEID=1 ENABLE_RVVM2 CONFIG=10)
set(CFLAGS_rvvm21024dp ${FLAGS_ENABLE_RVVM2})
set(MACRODEF_rvvm21024sp BASETYPEID=2 ENABLE_RVVM2 CONFIG=10)
set(CFLAGS_rvvm21024sp ${FLAGS_ENABLE_RVVM2})
set(MACRODEF_rvvm22048dp BASETYPEID=1 ENABLE_RVVM2 CONFIG=11)
set(CFLAGS_rvvm22048dp ${FLAGS_ENABLE_RVVM2})
set(MACRODEF_rvvm22048sp BASETYPEID=2 ENABLE_RVVM2 CONFIG=11)
set(CFLAGS_rvvm22048sp ${FLAGS_ENABLE_RVVM2})
set(MACRODEF_vsxdp BASETYPEID=1 ENABLE_VSX CONFIG=1)
set(CFLAGS_vsxdp ${FLAGS_ENABLE_VSX})
set(MACRODEF_vsxsp BASETYPEID=2 ENABLE_VSX CONFIG=1)
set(CFLAGS_vsxsp ${FLAGS_ENABLE_VSX})
set(MACRODEF_vsx3dp BASETYPEID=1 ENABLE_VSX3 CONFIG=1)
set(CFLAGS_vsx3dp ${FLAGS_ENABLE_VSX3})
set(MACRODEF_vsx3sp BASETYPEID=2 ENABLE_VSX3 CONFIG=1)
set(CFLAGS_vsx3sp ${FLAGS_ENABLE_VSX3})
set(MACRODEF_vxedp BASETYPEID=1 ENABLE_VXE CONFIG=140)
set(CFLAGS_vxedp ${FLAGS_ENABLE_VXE})
set(MACRODEF_vxesp BASETYPEID=2 ENABLE_VXE CONFIG=140)
set(CFLAGS_vxesp ${FLAGS_ENABLE_VXE})
set(MACRODEF_vxe2dp BASETYPEID=1 ENABLE_VXE2 CONFIG=150)
set(CFLAGS_vxe2dp ${FLAGS_ENABLE_VXE2})
set(MACRODEF_vxe2sp BASETYPEID=2 ENABLE_VXE2 CONFIG=150)
set(CFLAGS_vxe2sp ${FLAGS_ENABLE_VXE2})

# List all available scalar data types

set(ISALIST_SP purecsp)
set(ISALIST_DP purecdp)

set(LIST_SUPPORTED_FPTYPE 0 1)

# List all available vector data types

if (COMPILER_SUPPORTS_SSE2)
  set(ISALIST_SP ${ISALIST_SP} sse2sp)
  set(ISALIST_DP ${ISALIST_DP} sse2dp)
endif(COMPILER_SUPPORTS_SSE2)

if (COMPILER_SUPPORTS_AVX2)
  set(ISALIST_SP ${ISALIST_SP} avx2sp)
  set(ISALIST_DP ${ISALIST_DP} avx2dp)
endif(COMPILER_SUPPORTS_AVX2)

if (COMPILER_SUPPORTS_AVX512F)
  set(ISALIST_SP ${ISALIST_SP} avx512fsp)
  set(ISALIST_DP ${ISALIST_DP} avx512fdp)
endif(COMPILER_SUPPORTS_AVX512F)

if (COMPILER_SUPPORTS_ADVSIMD)
  set(ISALIST_SP ${ISALIST_SP} advsimdsp)
  set(ISALIST_DP ${ISALIST_DP} advsimddp)
endif(COMPILER_SUPPORTS_ADVSIMD)

if (COMPILER_SUPPORTS_SVE)
  set(ISALIST_SP ${ISALIST_SP} sve256sp sve512sp sve1024sp sve2048sp)
  set(ISALIST_DP ${ISALIST_DP} sve256dp sve512dp sve1024dp sve2048dp)
endif(COMPILER_SUPPORTS_SVE)

if (COMPILER_SUPPORTS_RVVM1)
  set(ISALIST_SP ${ISALIST_SP} rvvm1128sp rvvm1256sp rvvm1512sp rvvm11024sp rvvm12048sp)
  set(ISALIST_DP ${ISALIST_DP} rvvm1128dp rvvm1256dp rvvm1512dp rvvm11024dp rvvm12048dp)
endif(COMPILER_SUPPORTS_RVVM1)

if (COMPILER_SUPPORTS_RVVM2)
  set(ISALIST_SP ${ISALIST_SP} rvvm2128sp rvvm2256sp rvvm2512sp rvvm21024sp rvvm22048sp)
  set(ISALIST_DP ${ISALIST_DP} rvvm2128dp rvvm2256dp rvvm2512dp rvvm21024dp rvvm22048dp)
endif(COMPILER_SUPPORTS_RVVM2)

if (COMPILER_SUPPORTS_VSX)
  set(ISALIST_SP ${ISALIST_SP} vsxsp)
  set(ISALIST_DP ${ISALIST_DP} vsxdp)
endif(COMPILER_SUPPORTS_VSX)

if (COMPILER_SUPPORTS_VSX3)
  set(ISALIST_SP ${ISALIST_SP} vsx3sp)
  set(ISALIST_DP ${ISALIST_DP} vsx3dp)
endif(COMPILER_SUPPORTS_VSX3)

if (COMPILER_SUPPORTS_VXE)
  set(ISALIST_SP ${ISALIST_SP} vxesp)
  set(ISALIST_DP ${ISALIST_DP} vxedp)
endif(COMPILER_SUPPORTS_VXE)

if (COMPILER_SUPPORTS_VXE2)
  set(ISALIST_SP ${ISALIST_SP} vxe2sp)
  set(ISALIST_DP ${ISALIST_DP} vxe2dp)
endif(COMPILER_SUPPORTS_VXE2)

if(SLEEFDFT_ENABLE_STREAM)
  set(NLIST 0 1 2 3)
else()
  set(NLIST 0 2)
endif()

#

# Compiler properties

if(OPENMP_FOUND)
  set(CMAKE_C_FLAGS "${ORG_CMAKE_C_FLAGS} ${DFT_C_FLAGS} ${OpenMP_C_FLAGS}")
  set(CMAKE_CXX_FLAGS "${ORG_CMAKE_C_FLAGS} ${DFT_C_FLAGS} ${OpenMP_C_FLAGS}")
else()
  set(CMAKE_C_FLAGS "${ORG_CMAKE_C_FLAGS} ${DFT_C_FLAGS}")
  set(CMAKE_CXX_FLAGS "${ORG_CMAKE_C_FLAGS} ${DFT_C_FLAGS}")
endif()

if(MSVC)
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /EHsc")
endif()

set(COMMON_TARGET_PROPERTIES
  C_STANDARD 99                  # -std=gnu99
  )

if (BUILD_SHARED_LIBS)
  list(APPEND COMMON_TARGET_PROPERTIES POSITION_INDEPENDENT_CODE ON)   # -fPIC
endif()

set(COMMON_TARGET_DEFINITIONS ${COMMON_TARGET_DEFINITIONS}
  MAXBUTWIDTHDP=${SLEEFDFT_MAXBUTWIDTH} MAXBUTWIDTHSP=${SLEEFDFT_MAXBUTWIDTH}
)

if (SLEEFDFT_ENABLE_PARALLELFOR)
  set(COMMON_TARGET_DEFINITIONS ${COMMON_TARGET_DEFINITIONS} SLEEF_ENABLE_PARALLELFOR=1)
endif()

if (SLEEFDFT_ENABLE_STREAM)
  set(COMMON_TARGET_DEFINITIONS ${COMMON_TARGET_DEFINITIONS} ENABLE_STREAM=1)
else()
  set(COMMON_TARGET_DEFINITIONS ${COMMON_TARGET_DEFINITIONS} ENABLE_STREAM=0)
endif()


# Include directories

include_directories(${PROJECT_SOURCE_DIR}/include)
include_directories(${PROJECT_BINARY_DIR}/include)
include_directories(${CMAKE_CURRENT_BINARY_DIR})

# Target mkunroll

set(TARGET_MKUNROLL "mkunroll")
add_host_executable(${TARGET_MKUNROLL} mkunroll.c)
set_target_properties(${TARGET_MKUNROLL} PROPERTIES ${COMMON_TARGET_PROPERTIES})
if (NOT CMAKE_CROSSCOMPILING)
  target_compile_definitions(${TARGET_MKUNROLL} PRIVATE ${COMMON_TARGET_DEFINITIONS})
endif()

# Target mkdispatch

set(TARGET_MKDISPATCH "mkdispatch")
add_host_executable(${TARGET_MKDISPATCH} mkdispatch.c)
set_target_properties(${TARGET_MKDISPATCH} PROPERTIES ${COMMON_TARGET_PROPERTIES})
if (NOT CMAKE_CROSSCOMPILING)
  target_compile_definitions(${TARGET_MKDISPATCH} PRIVATE ${COMMON_TARGET_DEFINITIONS})
endif()

# Target dispatchparam.h

add_custom_command(OUTPUT dispatchparam.h
  COMMENT "Generating dispatchparam.h"
  COMMAND $<TARGET_FILE:${TARGET_MKDISPATCH}> paramonly ALL ${SLEEFDFT_MAXBUTWIDTH} 1 1 ${ISALIST_SP} > ${CMAKE_CURRENT_BINARY_DIR}/dispatchparam.h
  DEPENDS ${TARGET_MKDISPATCH}
  )
add_custom_target(dispatchparam.h_generated SOURCES ${CMAKE_CURRENT_BINARY_DIR}/dispatchparam.h)

# Target dispatch*.h

foreach(T ${LIST_SUPPORTED_FPTYPE})
  list(GET LISTSHORTTYPENAME ${T} ST)                       # ST is "dp", for example
  string(TOUPPER ${ST} CST)                                 # CST is "DP"
  list(GET LISTLONGTYPENAME ${T} LT)                        # LT is "double"
  list(GET LISTTYPEID ${T} ID)                              # ID is 1

  string(CONCAT S "dispatch" ${ST} ".hpp")                  # S is dispatchdp.hpp
  add_custom_command(OUTPUT ${S}
    COMMENT "Generating ${S}"
    COMMAND $<TARGET_FILE:${TARGET_MKDISPATCH}> ${LT} ${CST} ${SLEEFDFT_MAXBUTWIDTH} 1 1 ${ISALIST_${CST}} > ${S}
    DEPENDS ${TARGET_MKDISPATCH}
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
    )

  string(CONCAT G ${S} "_generated")                        # G is dispatchdp.hpp_generated
  add_custom_target(${G} SOURCES ${S})
endforeach()

# Target dftcommon.o

add_library(dftcommon_obj OBJECT dftcommon.cpp dftcommon.hpp ${CMAKE_CURRENT_BINARY_DIR}/dispatchparam.h ${sleef_BINARY_DIR}/include/sleef.h)
add_dependencies(dftcommon_obj ${TARGET_HEADERS} dispatchparam.h_generated)
set_source_files_properties(${sleef_BINARY_DIR}/include/sleef.h PROPERTIES GENERATED TRUE)
set_target_properties(dftcommon_obj PROPERTIES ${COMMON_TARGET_PROPERTIES})
target_compile_definitions(dftcommon_obj PRIVATE ${COMMON_TARGET_DEFINITIONS})

# Target dft.o

add_library(dft_obj OBJECT dft.cpp dftcommon.hpp)
add_dependencies(dft_obj "dispatchdp.hpp_generated" "dispatchsp.hpp_generated" dispatchparam.h_generated ${TARGET_HEADERS})
set_target_properties(dft_obj PROPERTIES ${COMMON_TARGET_PROPERTIES})
target_compile_definitions(dft_obj PRIVATE ${COMMON_TARGET_DEFINITIONS})

# Copy unroll*.cpp.in to ${CMAKE_CURRENT_BINARY_DIR}

add_custom_command(OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/unroll0.cpp.in
  COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_SOURCE_DIR}/unroll0.cpp.in ${CMAKE_CURRENT_BINARY_DIR}
  DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/unroll0.cpp.in)
add_custom_target(unroll0.cpp.in.copied DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/unroll0.cpp.in)

add_custom_command(OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/unroll1.cpp.in
  COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_SOURCE_DIR}/unroll1.cpp.in ${CMAKE_CURRENT_BINARY_DIR}
  DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/unroll1.cpp.in)
add_custom_target(unroll1.cpp.in.copied DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/unroll1.cpp.in)

add_custom_command(OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/unroll2.cpp.in
  COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_SOURCE_DIR}/unroll2.cpp.in ${CMAKE_CURRENT_BINARY_DIR}
  DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/unroll2.cpp.in)
add_custom_target(unroll2.cpp.in.copied DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/unroll2.cpp.in)

# Target unroll*.cpp

foreach(T ${LIST_SUPPORTED_FPTYPE})
  list(GET LISTSHORTTYPENAME ${T} ST)                       # ST is "dp", for example
  string(TOUPPER ${ST} CST)                                 # CST is "DP"
  list(GET LISTLONGTYPENAME ${T} LT)                        # LT is "double"

  foreach(E ${ISALIST_${CST}})                              # E is "sse2dp"
    foreach(N ${NLIST})
      string(CONCAT UC unroll_ ${N} _ ${E} ".cpp")          # UC is "unroll_0_sse2dp.cpp"
      set(UNROLL_TARGET_${CST} ${UNROLL_TARGET_${CST}} ${UC})
    endforeach()
  endforeach()
  message(STATUS "Unroll target for ${CST} : ${UNROLL_TARGET_${CST}}")

  if(UNROLL_TARGET_${CST})
    add_custom_command(OUTPUT ${UNROLL_TARGET_${CST}}
      COMMENT "Generating ${UNROLL_TARGET_${CST}}"
      COMMAND $<TARGET_FILE:${TARGET_MKUNROLL}> unroll0.cpp.in ${LT} ${CST} - ${ISALIST_${CST}}
      WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
      DEPENDS ${TARGET_MKUNROLL} unroll0.cpp.in.copied
      )
    add_custom_target(unroll_target_${ST} DEPENDS ${UNROLL_TARGET_${CST}})
  endif()
endforeach()

# Target unroll*.o

foreach(T ${LIST_SUPPORTED_FPTYPE})
  list(GET LISTSHORTTYPENAME ${T} ST)                       # ST is "dp", for example
  string(TOUPPER ${ST} CST)                                 # CST is "DP"
  list(GET LISTLONGTYPENAME ${T} LT)                        # LT is "double"

  foreach(E ${ISALIST_${CST}})                              # E is "sse2dp"
    foreach(N ${NLIST})
      string(CONCAT U unroll_ ${N} _ ${E})                  # U  is "unroll_0_sse2dp"
      string(CONCAT UG ${U} "_obj")                         # UG is "unroll_0_sse2dp_obj"
      string(CONCAT UC ${U} ".cpp")                         # UC is "unroll_0_sse2dp.cpp"
      add_library(${UG} OBJECT ${UC})
      set_target_properties(${UG} PROPERTIES ${COMMON_TARGET_PROPERTIES})
      target_include_directories(${UG} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
      target_compile_definitions(${UG} PRIVATE ${COMMON_TARGET_DEFINITIONS} ${MACRODEF_${E}})
      target_compile_options(${UG} PRIVATE ${CFLAGS_${E}})
      add_dependencies(${UG} ${TARGET_HEADERS} unroll_target_${ST} ext_tlfloat)
      list(APPEND UNROLL_OBJECTS $<TARGET_OBJECTS:${UG}>)
    endforeach()
  endforeach()
endforeach()

# Target libdft

add_library(${TARGET_LIBDFT} $<TARGET_OBJECTS:dftcommon_obj> $<TARGET_OBJECTS:dft_obj> ${UNROLL_OBJECTS})
target_link_libraries(${TARGET_LIBDFT} ${TARGET_LIBSLEEF} ${TLFLOAT_LIBRARIES} ${LIBM})

set_target_properties(${TARGET_LIBDFT} PROPERTIES
  VERSION ${SLEEF_VERSION}
  SOVERSION ${SLEEF_SOVERSION}
  PUBLIC_HEADER ${PROJECT_SOURCE_DIR}/include/sleefdft.h
  ${COMMON_TARGET_PROPERTIES}
  )

# Install
install(
    TARGETS ${TARGET_LIBDFT}
    EXPORT sleefTargets
    PUBLIC_HEADER #
    DESTINATION "${CMAKE_INSTALL_INCLUDEDIR}"
    COMPONENT sleef_Development
    LIBRARY #
    DESTINATION "${CMAKE_INSTALL_LIBDIR}"
    COMPONENT sleef_Runtime
    NAMELINK_COMPONENT sleef_Development
    ARCHIVE #
    DESTINATION "${CMAKE_INSTALL_LIBDIR}"
    COMPONENT sleef_Development
    RUNTIME #
    DESTINATION "${CMAKE_INSTALL_BINDIR}"
    COMPONENT sleef_Runtime
    INCLUDES #
    DESTINATION "${CMAKE_INSTALL_INCLUDEDIR}"
)
