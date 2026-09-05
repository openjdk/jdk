cmake_minimum_required(VERSION 3.18)

set(SLEEF_VERSION 3.9.0)

message(STATUS "Configuring SLEEF ${SLEEF_VERSION}")
project(SLEEF VERSION ${SLEEF_VERSION} LANGUAGES C CXX)

set(SLEEF_SOVERSION ${SLEEF_VERSION_MAJOR})

set(CMAKE_CXX_STANDARD 20)

# Options

option(SLEEF_BUILD_STATIC_TEST_BINS "Build statically linked test executables" OFF)
option(SLEEF_ENABLE_LTO "Enable LTO on GCC or ThinLTO on clang" OFF)
option(SLEEF_BUILD_LIBM "libsleef will be built." ON)
option(SLEEF_BUILD_DFT "libsleefdft will be built." OFF)
option(SLEEF_BUILD_QUAD "libsleefquad will be built." OFF)
option(SLEEF_BUILD_GNUABI_LIBS "libsleefgnuabi will be built." ON)
option(SLEEF_BUILD_SCALAR_LIB "libsleefscalar will be built." OFF)
option(SLEEF_BUILD_TESTS "Tests will be built." ON)
option(SLEEF_BUILD_BENCH "Bench will be built." OFF)
option(SLEEF_BUILD_BENCH_REF "Benchmark script for reference (e.g. system libm) will be built." OFF)
option(SLEEF_BUILD_INLINE_HEADERS "Build header for inlining whole SLEEF functions" OFF)

option(SLEEF_ENFORCE_DFT "Build fails if DFT is not built" OFF)
option(SLEEFDFT_ENABLE_STREAM "Streaming instructions are utilized in DFT." OFF)

option(SLEEF_TEST_ALL_IUT "Perform tests on implementations with all vector extensions" OFF)
option(SLEEF_SHOW_CONFIG "Show SLEEF configuration status messages." ON)
option(SLEEF_SHOW_ERROR_LOG "Show cmake error log." OFF)
option(SLEEF_ASAN "Enable address sanitizing on all targets." OFF)

option(SLEEF_ENABLE_TESTER "Enable testing libm with tester" OFF)
option(SLEEF_ENFORCE_TESTER "Build fails if tester is not available" OFF)

option(SLEEF_ENFORCE_TESTER3 "Build fails if tester3 is not built" OFF)

option(SLEEF_ENABLE_TESTER4 "Enable testing with tester4" ON)
option(SLEEF_ENFORCE_TESTER4 "Build fails if tester4 is not available" OFF)

option(SLEEF_ENABLE_ALTDIV  "Enable alternative division method (aarch64 only)" OFF)
option(SLEEF_ENABLE_ALTSQRT "Enable alternative sqrt method (aarch64 only)" OFF)

option(SLEEF_DISABLE_FFTW "Disable testing the DFT library with FFTW" OFF)
option(SLEEF_DISABLE_MPFR "Disable testing with the MPFR library" OFF)
option(SLEEF_ENABLE_TLFLOAT "Enable use of TLFloat library" ON)

option(SLEEF_DISABLE_SSL "Disable testing with the SSL library" OFF)
set(OPENSSL_EXTRA_LIBRARIES "" CACHE STRING "Extra libraries for openssl")

option(SLEEF_ENABLE_CUDA "Enable CUDA" OFF)

option(SLEEF_BUILD_WITH_LIBM "build libsleef with libm, can turn off on Windows to solve mutiple math functions issue." ON)

option(SLEEF_DISABLE_LONG_DOUBLE "Disable long double" OFF)
option(SLEEF_ENFORCE_LONG_DOUBLE "Build fails if long double is not supported by the compiler" OFF)

option(SLEEF_DISABLE_FLOAT128 "Disable float128" OFF)
option(SLEEF_ENFORCE_FLOAT128 "Build fails if float128 is not supported by the compiler" OFF)

option(SLEEF_DISABLE_SSE2 "Disable SSE2" OFF)
option(SLEEF_ENFORCE_SSE2 "Build fails if SSE2 is not supported by the compiler" OFF)
option(SLEEF_DISABLE_SSE4 "Disable SSE4" OFF)
option(SLEEF_ENFORCE_SSE4 "Build fails if SSE4 is not supported by the compiler" OFF)
option(SLEEF_DISABLE_AVX "Disable AVX" OFF)
option(SLEEF_ENFORCE_AVX "Build fails if AVX is not supported by the compiler" OFF)
option(SLEEF_DISABLE_FMA4 "Disable FMA4" OFF)
option(SLEEF_ENFORCE_FMA4 "Build fails if FMA4 is not supported by the compiler" OFF)
option(SLEEF_DISABLE_AVX2 "Disable AVX2" OFF)
option(SLEEF_ENFORCE_AVX2 "Build fails if AVX2 is not supported by the compiler" OFF)
option(SLEEF_DISABLE_AVX512F "Disable AVX512F" OFF)
option(SLEEF_ENFORCE_AVX512F "Build fails if AVX512F is not supported by the compiler" OFF)
option(SLEEF_DISABLE_SVE "Disable SVE" OFF)
option(SLEEF_ENFORCE_SVE "Build fails if SVE is not supported by the compiler" OFF)
option(SLEEF_DISABLE_VSX "Disable VSX" OFF)
option(SLEEF_ENFORCE_VSX "Build fails if VSX is not supported by the compiler" OFF)
option(SLEEF_DISABLE_VSX3 "Disable VSX3" OFF)
option(SLEEF_ENFORCE_VSX3 "Build fails if VSX3 is not supported by the compiler" OFF)
option(SLEEF_DISABLE_VXE "Disable VXE" OFF)
option(SLEEF_ENFORCE_VXE "Build fails if VXE is not supported by the compiler" OFF)
option(SLEEF_DISABLE_VXE2 "Disable VXE2" OFF)
option(SLEEF_ENFORCE_VXE2 "Build fails if VXE2 is not supported by the compiler" OFF)
option(SLEEF_DISABLE_RVVM1 "Disable RVVM1" OFF)
option(SLEEF_ENFORCE_RVVM1 "Build fails if RVVM1 is not supported by the compiler" OFF)
option(SLEEF_DISABLE_RVVM2 "Disable RVVM2" OFF)
option(SLEEF_ENFORCE_RVVM2 "Build fails if RVVM2 is not supported by the compiler" OFF)

option(SLEEF_ENFORCE_CUDA "Build fails if CUDA is not supported" OFF)

option(SLEEF_DISABLE_OPENMP "Disable OPENMP" OFF)
option(SLEEF_ENFORCE_OPENMP "Build fails if OPENMP is not supported by the compiler" OFF)

#

if ((NOT "${CMAKE_C_COMPILER_ID}" STREQUAL "${CMAKE_CXX_COMPILER_ID}") OR
    (NOT CMAKE_C_COMPILER_VERSION VERSION_EQUAL CMAKE_CXX_COMPILER_VERSION))
  message(FATAL_ERROR "Different versions of C compiler and C++ compiler")
endif()

#

if (SLEEF_BUILD_BENCH_REF)
  if (NOT SLEEF_BUILD_BENCH)
    message(FATAL_ERROR "SLEEF_BUILD_BENCH must be on when SLEEF_BUILD_BENCH_REF is enabled.")
  endif ()
  if(NOT CMAKE_SYSTEM_NAME MATCHES Linux)
     message(FATAL_ERROR "Libm benchmarking not supported in this OS.")
  endif()
endif ()

if (DEFINED SLEEF_BUILD_SHARED_LIBS)
  set(BUILD_SHARED_LIBS ${SLEEF_BUILD_SHARED_LIBS})
endif ()

if (SLEEF_SHOW_CONFIG)
  # Normalize the value of BUILD_SHARED_LIBS so that it displays nicely
  # in the configuration display
  if (BUILD_SHARED_LIBS)
    set(BUILD_SHARED_LIBS ON)
  else ()
    set(BUILD_SHARED_LIBS OFF)
  endif ()
endif ()

# Function used to generate safe command arguments for add_custom_command
function(command_arguments PROPNAME)
  set(quoted_args "")
  foreach(arg ${ARGN})
    list(APPEND quoted_args "\"${arg}\"" )
  endforeach()
  set(${PROPNAME} ${quoted_args} PARENT_SCOPE)
endfunction()

# Helper function for concatenating several files
function(sleef_concat_files)
  cmake_parse_arguments(concat_required "" "OUTPUT" "SOURCES" ${ARGN})
  if("${concat_required_OUTPUT}" STREQUAL "")
    message(FATAL_ERROR "Must pass OUTPUT to sleef_concat_files")
  endif()

  if(NOT concat_required_SOURCES)
    message(FATAL_ERROR "sleef_concat_files not passed any SOURCES")
  endif()

  add_custom_command(
    OUTPUT ${concat_required_OUTPUT}
    COMMAND ${CMAKE_COMMAND} -E cat ${concat_required_SOURCES} > ${concat_required_OUTPUT}
    DEPENDS ${concat_required_SOURCES}
    COMMAND_EXPAND_LISTS)
endfunction()

# Settings

set(SLEEF_ALL_SUPPORTED_EXTENSIONS
  AVX512FNOFMA AVX512F AVX2 AVX2128 FMA4 AVX SSE4 SSE2  # x86
  SVENOFMA SVE ADVSIMDNOFMA ADVSIMD                     # Aarch64
  NEON32 NEON32VFPV4                                    # Aarch32
  VSX VSXNOFMA VSX3 VSX3NOFMA                           # PPC64
  VXE VXENOFMA VXE2 VXE2NOFMA                           # IBM Z
  RVVM1NOFMA RVVM1 RVVM2NOFMA RVVM2                     # RISC-V Vectors
  PUREC_SCALAR PURECFMA_SCALAR                          # Generic type
  CACHE STRING "List of SIMD architectures supported by libsleef."
  )

set(SLEEF_SUPPORTED_LIBM_EXTENSIONS
  AVX512FNOFMA AVX512F AVX2 AVX2128 FMA4 AVX SSE4 SSE2  # x86
  SVENOFMA SVE ADVSIMDNOFMA ADVSIMD                     # Aarch64
  NEON32 NEON32VFPV4                                    # Aarch32
  VSX VSXNOFMA VSX3 VSX3NOFMA                           # PPC64
  VXE VXENOFMA VXE2 VXE2NOFMA                           # IBM Z
  RVVM1NOFMA RVVM1 RVVM2NOFMA RVVM2                     # RISC-V Vectors
  PUREC_SCALAR PURECFMA_SCALAR                          # Generic type
  CACHE STRING "List of SIMD architectures supported by libsleef."
  )
set(SLEEF_SUPPORTED_GNUABI_EXTENSIONS
  SSE2 AVX AVX2 AVX512F ADVSIMD SVE
  CACHE STRING "List of SIMD architectures supported by libsleef for GNU ABI."
)

set(SLEEF_SUPPORTED_QUAD_EXTENSIONS
  PUREC_SCALAR PURECFMA_SCALAR SSE2 AVX2128 AVX2 AVX512F ADVSIMD SVE VSX VSX3 VXE VXE2 RVVM1 RVVM2)

# MKMASKED_PARAMS

command_arguments(MKMASKED_PARAMS_GNUABI_AVX512F_dp avx512f e 8)
command_arguments(MKMASKED_PARAMS_GNUABI_AVX512F_sp avx512f e -16)

command_arguments(MKMASKED_PARAMS_GNUABI_SVE_dp sve s 2)
command_arguments(MKMASKED_PARAMS_GNUABI_SVE_sp sve s -4)

#

set(COSTOVERRIDE_AVX512F 10)
set(COSTOVERRIDE_AVX512FNOFMA 10)
set(COSTOVERRIDE_AVX2 2)
set(COSTOVERRIDE_AVX 2)
set(COSTOVERRIDE_NEON32 2)
set(COSTOVERRIDE_NEON32VFPV4 2)
set(COSTOVERRIDE_SVE 10)
set(COSTOVERRIDE_SVENOFMA 10)
set(COSTOVERRIDE_RVVM1 10)
set(COSTOVERRIDE_RVVM1NOFMA 10)
set(COSTOVERRIDE_RVVM2 20)
set(COSTOVERRIDE_RVVM2NOFMA 20)

#

enable_testing()
enable_language(CXX)

if (SLEEF_ENABLE_CUDA)
  enable_language(CUDA)
  set(CMAKE_CUDA_ARCHITECTURES all-major)
endif()

# For specifying installation directories
include(GNUInstallDirs)

if(NOT DEFINED sleef_SOURCE_DIR)
   set(sleef_SOURCE_DIR ${CMAKE_SOURCE_DIR})
endif()

if(NOT DEFINED sleef_BINARY_DIR)
   set(sleef_BINARY_DIR ${CMAKE_BINARY_DIR})
endif()

# Sanity check for in-source builds which we do not want to happen
if(sleef_SOURCE_DIR STREQUAL sleef_BINARY_DIR)
  message(FATAL_ERROR "SLEEF does not allow in-source builds.
You can refer to docs/build-with-cmake.md for instructions on how provide a \
separate build directory. Note: Please remove autogenerated file \
`CMakeCache.txt` and directory `CMakeFiles` in the current directory.")
endif()

if(SLEEF_ENABLE_LTO AND BUILD_SHARED_LIBS)
  message(FATAL_ERROR "SLEEF_ENABLE_LTO and BUILD_SHARED_LIBS cannot be specified at the same time")
endif(SLEEF_ENABLE_LTO AND BUILD_SHARED_LIBS)

if(SLEEF_ENABLE_LTO)
  include(CheckIPOSupported)
  check_ipo_supported(RESULT supported OUTPUT error)
endif()

# Set output directories for the library files
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${PROJECT_BINARY_DIR}/lib)
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${PROJECT_BINARY_DIR}/lib)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${PROJECT_BINARY_DIR}/bin)

foreach(CONFIG ${CMAKE_CONFIGURATION_TYPES})
  string(TOUPPER ${CONFIG} CONFIG)
  set(CMAKE_LIBRARY_OUTPUT_DIRECTORY_${CONFIG} ${PROJECT_BINARY_DIR}/lib)
  set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY_${CONFIG} ${PROJECT_BINARY_DIR}/lib)
  set(CMAKE_RUNTIME_OUTPUT_DIRECTORY_${CONFIG} ${PROJECT_BINARY_DIR}/bin)
endforeach(CONFIG CMAKE_CONFIGURATION_TYPES)

# Path for finding cmake modules
set(CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake/Modules)
set(SLEEF_SCRIPT_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake/Scripts CACHE PATH
  "Path for finding sleef specific cmake scripts")

if (CMAKE_C_COMPILER_ID MATCHES "Clang" AND "x${CMAKE_C_SIMULATE_ID}" STREQUAL "xMSVC")
  message(STATUS "Building with Clang on Windows")
  set(SLEEF_CLANG_ON_WINDOWS TRUE)
endif()

# sleef-config.h.in passes cmake settings to the source code
include(Configure.cmake)
configure_file(
  ${PROJECT_SOURCE_DIR}/sleef-config.h.in
  ${PROJECT_BINARY_DIR}/include/sleef-config.h @ONLY)
include_directories(AFTER "${PROJECT_BINARY_DIR}/include")

# We like to have a documented index of all targets in the project. The
# variables listed below carry the names of the targets defined throughout
# the project.

# Generates object file (shared library) `libsleef`
# Defined in src/libm/CMakeLists.txt via command add_library
set(TARGET_LIBSLEEF "sleef")
set(TARGET_LIBSLEEFGNUABI "sleefgnuabi")
# Generates the sleef.h headers and all the rename headers
# Defined in src/libm/CMakeLists.txt via custom commands and a custom target
set(TARGET_HEADERS "headers")
set(TARGET_INLINE_HEADERS "inline_headers")
set(TARGET_QINLINE_HEADERS "quad_inline_headers")
set(TARGET_LIBINLINE "sleefinline")
# Generates executable files for running the test suite
# Defined in src/libm-tester/CMakeLists.txt via command add_executable
set(TARGET_TESTER "tester")
set(TARGET_IUT "iut")
# The target to generate LLVM bitcode only, available when SLEEF_ENABLE_LLVM_BITCODE is passed to cmake
set(TARGET_LLVM_BITCODE "llvm-bitcode")
# Generates the helper executable file mkrename needed to write the sleef header
set(TARGET_MKRENAME "mkrename")
set(TARGET_MKRENAME_GNUABI "mkrename_gnuabi")
set(TARGET_MKMASKED_GNUABI "mkmasked_gnuabi")
# Generates the helper executable file mkdisp needed to write the sleef header
set(TARGET_MKDISP "mkdisp")
set(TARGET_MKALIAS "mkalias")
# Generates static library common
# Defined in src/common/CMakeLists.txt via command add_library
set(TARGET_LIBCOMMON_OBJ "common")
set(TARGET_PSHA_OBJ "psha_obj")
set(TARGET_TESTERUTIL_OBJ "testerutil_obj")
set(TARGET_QTESTERUTIL_OBJ "qtesterutil_obj")

# Function used to add an executable that is executed on host
function(add_host_executable TARGETNAME)
  if (NOT CMAKE_CROSSCOMPILING)
    add_executable(${TARGETNAME} ${ARGN})
    # Ensure that Darwin host executable is built as universal binary
    if(CMAKE_SYSTEM_NAME STREQUAL "Darwin" AND CMAKE_OSX_ARCHITECTURES MATCHES "^(x86_64|arm64)$")
      target_compile_options(${TARGETNAME} PRIVATE -arch "${CMAKE_HOST_SYSTEM_PROCESSOR}")
      target_link_options(${TARGETNAME} PRIVATE -arch "${CMAKE_HOST_SYSTEM_PROCESSOR}")
    endif()
  elseif (DEFINED ENV{SLEEF_TARGET_EXEC_USE_QEMU})
    if($ENV{SLEEF_TARGET_EXEC_USE_QEMU})
      add_executable(${TARGETNAME} ${ARGN})
    endif()
  else()
    add_executable(${TARGETNAME} IMPORTED GLOBAL)
    if(CMAKE_HOST_WIN32)
      set_property(TARGET ${TARGETNAME} PROPERTY IMPORTED_LOCATION ${NATIVE_BUILD_DIR}/bin/${TARGETNAME}.exe)
    else()
      set_property(TARGET ${TARGETNAME} PROPERTY IMPORTED_LOCATION ${NATIVE_BUILD_DIR}/bin/${TARGETNAME})
    endif()
  endif()
endfunction()

function(host_target_AAVPCS_definitions TARGETNAME)
  if (NOT CMAKE_CROSSCOMPILING)
#    target_compile_definitions(${TARGETNAME} PRIVATE ENABLE_AAVPCS=1)
  endif()
endfunction()

# Generates object file (shared library) `libsleefdft`
# Defined in src/dft/CMakeLists.txt via command add_library
set(TARGET_LIBDFT "sleefdft")

# Check subdirectories
add_subdirectory("src")

# Install the CMake package config
include(CMakePackageConfigHelpers)

write_basic_package_version_file(
    sleefConfigVersion.cmake
    COMPATIBILITY SameMajorVersion
)

set(
    SLEEF_INSTALL_CMAKEDIR "${CMAKE_INSTALL_LIBDIR}/cmake/sleef"
    CACHE STRING "CMake package config location relative to the install prefix"
)

mark_as_advanced(SLEEF_INSTALL_CMAKEDIR)

install(
    FILES
    "${PROJECT_SOURCE_DIR}/sleefConfig.cmake"
    "${PROJECT_BINARY_DIR}/sleefConfigVersion.cmake"
    DESTINATION "${SLEEF_INSTALL_CMAKEDIR}"
    COMPONENT sleef_Development
)

install(
    EXPORT sleefTargets
    NAMESPACE sleef::
    DESTINATION "${SLEEF_INSTALL_CMAKEDIR}"
    COMPONENT sleef_Development
)

# Extra messages at configuration time. By default is active, it can be
# turned off by invoking cmake with "-DSLEEF_SHOW_CONFIG=OFF".
if(SLEEF_SHOW_CONFIG)
  message(STATUS "Configuring build for ${PROJECT_NAME}-v${SLEEF_VERSION}")
  message("   Target system: ${CMAKE_SYSTEM}")
  if(CMAKE_SYSTEM_NAME STREQUAL "Darwin" AND CMAKE_OSX_ARCHITECTURES MATCHES "^(x86_64|arm64)$")
    message("   Target processor: ${CMAKE_OSX_ARCHITECTURES}")
  else()
    message("   Target processor: ${CMAKE_SYSTEM_PROCESSOR}")
  endif()
  message("   Host system: ${CMAKE_HOST_SYSTEM}")
  message("   Host processor: ${CMAKE_HOST_SYSTEM_PROCESSOR}")
  message("   Detected C compiler: ${CMAKE_C_COMPILER_ID} @ ${CMAKE_C_COMPILER}")
  message("   CMake: ${CMAKE_VERSION}")
  message("   Make program: ${CMAKE_MAKE_PROGRAM}")
  message("   CMake build type: ${CMAKE_BUILD_TYPE}")
  if(CMAKE_CROSSCOMPILING)
    message("   Crosscompiling SLEEF.")
    message("   Native build dir: ${NATIVE_BUILD_DIR}")
  endif(CMAKE_CROSSCOMPILING)
  message(STATUS "Using option `${SLEEF_C_FLAGS}` to compile libsleef")
  message(STATUS "Building shared libs : " ${BUILD_SHARED_LIBS})
  message(STATUS "Building static test bins: " ${SLEEF_BUILD_STATIC_TEST_BINS})
  message(STATUS "MPFR : " ${LIB_MPFR})
  if (MPFR_INCLUDE_DIR)
    message(STATUS "MPFR header file in " ${MPFR_INCLUDE_DIR})
  endif()
  message(STATUS "GMP : " ${LIBGMP})
  message(STATUS "RT : " ${LIBRT})
  message(STATUS "FFTW3 : " ${LIBFFTW3})
  message(STATUS "FFTW3F : " ${LIBFFTW3F})
  message(STATUS "OPENSSL : " ${OPENSSL_VERSION})
  message(STATUS "SDE : " ${SDE_COMMAND})
  if (SLEEF_BUILD_INLINE_HEADERS)
    message(STATUS "SED : " ${SED_COMMAND})
  endif()
  message(STATUS "COMPILER_SUPPORTS_OPENMP : " ${COMPILER_SUPPORTS_OPENMP})
  if(ENABLE_GNUABI)
    message(STATUS "A version of SLEEF compatible  with libm and libmvec in GNU libc will be produced (${TARGET_LIBSLEEFGNUABI}.so)")
  endif()
  if (COMPILER_SUPPORTS_SVE)
    message(STATUS "Building SLEEF with VLA SVE support")
    if (ARMIE_COMMAND)
      message(STATUS "Arm Instruction Emulator found at ${ARMIE_COMMAND}")
      message(STATUS "SVE testing is done with ${SVE_VECTOR_BITS}-bits vectors.")
    endif()
  endif()
  if(FORCE_AAVPCS)
    message(STATUS "Building SLEEF with AArch64 Vector PCS support")
  endif()
endif(SLEEF_SHOW_CONFIG)

