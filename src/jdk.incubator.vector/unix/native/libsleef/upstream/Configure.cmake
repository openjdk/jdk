include(CheckCCompilerFlag)
include(CheckCSourceCompiles)
include(CheckCXXSourceCompiles)
include(CheckTypeSize)
include(CheckLanguage)

#

if (SLEEF_BUILD_STATIC_TEST_BINS)
  set(CMAKE_FIND_LIBRARY_SUFFIXES ".a")
  set(BUILD_SHARED_LIBS OFF)
  set(CMAKE_EXE_LINKER_FLAGS "-static")
endif()

if (NOT SLEEF_DISABLE_SSL)
  if (NOT CMAKE_CROSSCOMPILING AND NOT SLEEF_FORCE_FIND_PACKAGE_SSL)
    if (SLEEF_BUILD_STATIC_TEST_BINS)
      set(OPENSSL_USE_STATIC_LIBS TRUE)
    endif()
    find_package(OpenSSL)
    if (OPENSSL_FOUND)
      set(SLEEF_OPENSSL_FOUND TRUE)
      set(SLEEF_OPENSSL_LIBRARIES ${OPENSSL_LIBRARIES})
      # Work around for tester3 sig segv, when linking versions of openssl (1.1.1) statically.
      # This is a known issue https://github.com/openssl/openssl/issues/13872.
      if (SLEEF_BUILD_STATIC_TEST_BINS)
        string(REGEX REPLACE
          "-lpthread" "-Wl,--whole-archive -lpthread -Wl,--no-whole-archive"
          SLEEF_OPENSSL_LIBRARIES "${OPENSSL_LIBRARIES}")
      endif()
      set(SLEEF_OPENSSL_VERSION ${OPENSSL_VERSION})
      set(SLEEF_OPENSSL_LIBRARIES ${SLEEF_OPENSSL_LIBRARIES} ${OPENSSL_EXTRA_LIBRARIES})
      set(SLEEF_OPENSSL_INCLUDE_DIR ${OPENSSL_INCLUDE_DIR})
    endif()
  else()
    # find_package cannot find OpenSSL when cross-compiling
    find_library(LIBSSL ssl)
    find_library(LIBCRYPTO crypto)
    if (LIBSSL AND LIBCRYPTO)
      set(SLEEF_OPENSSL_FOUND TRUE)
      set(SLEEF_OPENSSL_LIBRARIES ${LIBSSL} ${LIBCRYPTO} ${OPENSSL_EXTRA_LIBRARIES})
      set(SLEEF_OPENSSL_VERSION ${LIBSSL})
    endif()
  endif()
else()
  set(SLEEF_OPENSSL_FOUND FALSE)
  message(STATUS "Detection of OpenSSL is skipped since SLEEF_DISABLE_SSL is specified")
endif()

if (SLEEF_ENFORCE_TESTER3 AND NOT SLEEF_OPENSSL_FOUND)
  message(FATAL_ERROR "SLEEF_ENFORCE_TESTER3 is specified and OpenSSL not found")
endif()

# Some toolchains require explicit linking of the libraries following.
find_library(LIB_MPFR mpfr)
if(SLEEF_BUILD_WITH_LIBM)
  find_library(LIBM m)
endif()
find_library(LIBGMP gmp)
find_library(LIBRT rt)

find_library(LIBFFTW3 fftw3)
find_library(LIBFFTW3F fftw3f)
find_library(LIBFFTW3_OMP fftw3_omp)
find_library(LIBFFTW3F_OMP fftw3f_omp)

if (LIBFFTW3 AND LIBFFTW3F AND LIBFFTW3_OMP AND LIBFFTW3F_OMP)
  set(SLEEF_LIBFFTW3_LIBRARIES ${LIBFFTW3} ${LIBFFTW3F} ${LIBFFTW3_OMP} ${LIBFFTW3F_OMP})
endif()

if (LIB_MPFR)
  find_path(MPFR_INCLUDE_DIR
    NAMES mpfr.h
    ONLY_CMAKE_FIND_ROOT_PATH)
endif(LIB_MPFR)

if (LIBFFTW3)
  find_path(FFTW3_INCLUDE_DIR
    NAMES fftw3.h
    ONLY_CMAKE_FIND_ROOT_PATH)
endif()

if (NOT LIBM)
  set(LIBM "")
endif()

if (NOT LIBRT)
  set(LIBRT "")
endif()

if (SLEEF_DISABLE_MPFR)
  set(LIB_MPFR "")
endif()

# Include submodules

set(SLEEF_SUBMODULE_INSTALL_DIR "${CMAKE_BINARY_DIR}/submodules")

include(ExternalProject)
include(FindPkgConfig)

if (NOT EXISTS "${PROJECT_SOURCE_DIR}/submodules")
  file(MAKE_DIRECTORY "${PROJECT_SOURCE_DIR}/submodules")
endif()

# Include TLFloat as a submodule

if (SLEEF_ENABLE_TLFLOAT)
  set(TLFLOAT_MINIMUM_VERSION 1.15.0)
  set(TLFLOAT_GIT_TAG "fb0390157d5c8811fc2a5a6d7d8eac27261f06fb")

  set(TLFLOAT_SOURCE_DIR "${PROJECT_SOURCE_DIR}/submodules/tlfloat")
  set(TLFLOAT_INSTALL_DIR "${SLEEF_SUBMODULE_INSTALL_DIR}/tlfloat")

  set(TLFLOAT_CMAKE_ARGS -DCMAKE_INSTALL_PREFIX=${TLFLOAT_INSTALL_DIR} -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE} -DBUILD_LIBS=True -DBUILD_UTILS=False -DBUILD_TESTS=False)

  if (CMAKE_C_COMPILER)
    list(APPEND TLFLOAT_CMAKE_ARGS -DCMAKE_C_COMPILER:PATH=${CMAKE_C_COMPILER})
  endif()

  if (CMAKE_CXX_COMPILER)
    list(APPEND TLFLOAT_CMAKE_ARGS -DCMAKE_CXX_COMPILER:PATH=${CMAKE_CXX_COMPILER})
  endif()

  if (CMAKE_TOOLCHAIN_FILE)
    list(APPEND TLFLOAT_CMAKE_ARGS -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE})
  endif()

  if (EXISTS "${TLFLOAT_SOURCE_DIR}/CMakeLists.txt")
    # If the source code of tlfloat is already downloaded, use it
    ExternalProject_Add(ext_tlfloat
      SOURCE_DIR "${TLFLOAT_SOURCE_DIR}"
      CMAKE_ARGS ${TLFLOAT_CMAKE_ARGS}
      UPDATE_DISCONNECTED TRUE
    )
    include_directories(BEFORE "${TLFLOAT_INSTALL_DIR}/include")
    link_directories(BEFORE "${TLFLOAT_INSTALL_DIR}/lib")
    set(TLFLOAT_LIBRARIES "tlfloat")
  else()
    pkg_search_module(TLFLOAT tlfloat)

    if (TLFLOAT_FOUND AND TLFLOAT_VERSION VERSION_GREATER_EQUAL TLFLOAT_MINIMUM_VERSION)
      # If tlfloat is installed on the system
      add_custom_target(ext_tlfloat ALL)
      include_directories(BEFORE "${TLFLOAT_INCLUDE_DIRS}")
      link_directories(BEFORE "${TLFLOAT_LIBDIR}")
      message(STATUS "Found installed TLFloat " ${TLFLOAT_VERSION})
    else()
      # Otherwise, download the source code
      find_package(Git REQUIRED)
      ExternalProject_Add(ext_tlfloat
        GIT_REPOSITORY https://github.com/shibatch/tlfloat
        GIT_TAG "${TLFLOAT_GIT_TAG}"
        SOURCE_DIR "${TLFLOAT_SOURCE_DIR}"
        CMAKE_ARGS ${TLFLOAT_CMAKE_ARGS}
        UPDATE_DISCONNECTED TRUE
      )

      include_directories(BEFORE "${TLFLOAT_INSTALL_DIR}/include")
      link_directories(BEFORE "${TLFLOAT_INSTALL_DIR}/lib")
      set(TLFLOAT_LIBRARIES "tlfloat")
    endif()
  endif()
endif(SLEEF_ENABLE_TLFLOAT)

# Force set default build type if none was specified
# Note: some sleef code requires the optimisation flags turned on
if(NOT CMAKE_BUILD_TYPE)
  message(STATUS "Setting build type to 'Release' (required for full support).")
  set(CMAKE_BUILD_TYPE Release CACHE STRING "Choose the type of build." FORCE)
  set_property(CACHE CMAKE_BUILD_TYPE PROPERTY STRINGS
    "Debug" "Release" "RelWithDebInfo" "MinSizeRel")
endif()

# Sanitizers
if(SLEEF_ASAN)
  # Add address sanitizing to all targets
  add_compile_options(-fno-omit-frame-pointer -fsanitize=address)
  add_link_options(-fno-omit-frame-pointer -fsanitize=address)
endif()

# TARGET PROCESSOR DETECTION
set(SLEEF_TARGET_PROCESSOR "${CMAKE_SYSTEM_PROCESSOR}")
if(CMAKE_SYSTEM_NAME STREQUAL "Darwin" AND CMAKE_OSX_ARCHITECTURES MATCHES "^(x86_64|arm64)$")
  set(SLEEF_TARGET_PROCESSOR "${CMAKE_OSX_ARCHITECTURES}")
endif()

# PLATFORM DETECTION
if(CMAKE_SIZEOF_VOID_P EQUAL 4)
  set(SLEEF_ARCH_32BIT ON CACHE INTERNAL "True for 32-bit architecture.")
endif()

if(SLEEF_TARGET_PROCESSOR MATCHES "(x86|AMD64|amd64|^i.86$)")
  set(SLEEF_ARCH_X86 ON CACHE INTERNAL "True for x86 architecture.")

  set(CLANG_FLAGS_ENABLE_PURECFMA_SCALAR "-mavx2;-mfma")
elseif(SLEEF_TARGET_PROCESSOR MATCHES "aarch64|arm64")
  set(SLEEF_ARCH_AARCH64 ON CACHE INTERNAL "True for Aarch64 architecture.")
  # Aarch64 requires support for advsimdfma4
  set(COMPILER_SUPPORTS_ADVSIMD 1)
  set(COMPILER_SUPPORTS_ADVSIMDNOFMA 1)

elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "arm")
  set(SLEEF_ARCH_AARCH32 ON CACHE INTERNAL "True for Aarch32 architecture.")
  set(COMPILER_SUPPORTS_NEON32 1)
  set(COMPILER_SUPPORTS_NEON32VFPV4 1)

  set(CLANG_FLAGS_ENABLE_PURECFMA_SCALAR "-mfpu=vfpv4")
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "^(powerpc|ppc)64" OR CMAKE_SYSTEM_PROCESSOR MATCHES "^(powerpc|ppc)")
  set(SLEEF_ARCH_PPC64 ON CACHE INTERNAL "True for PPC64 architecture.")

  set(CLANG_FLAGS_ENABLE_PURECFMA_SCALAR "-mvsx")
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "s390x")
  set(SLEEF_ARCH_S390X ON CACHE INTERNAL "True for IBM Z architecture.")

  set(CLANG_FLAGS_ENABLE_PUREC_SCALAR "-march=z14;-mzvector")
  set(CLANG_FLAGS_ENABLE_PURECFMA_SCALAR "-march=z14;-mzvector")
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "riscv64")
  set(SLEEF_ARCH_RISCV64 ON CACHE INTERNAL "True for RISCV64 architecture.")
endif()

set(COMPILER_SUPPORTS_PUREC_SCALAR 1)
set(COMPILER_SUPPORTS_PURECFMA_SCALAR 1)

# Compiler feature detection

# Detect CLANG executable path (on both Windows and Linux/OSX)
if(NOT CLANG_EXE_PATH)
  # If the current compiler used by CMAKE is already clang, use this one directly
  if(CMAKE_C_COMPILER MATCHES "clang")
    set(CLANG_EXE_PATH ${CMAKE_C_COMPILER})
  else()
    # Else we may find clang on the path?
    find_program(CLANG_EXE_PATH NAMES clang "clang-25" "clang-24" "clang-23" "clang-22" "clang-21" "clang-20" "clang-19" "clang-18" "clang-17")
  endif()
endif()

# Allow to define the Gcc/Clang here
# As we might compile the lib with MSVC, but generates bitcode with CLANG
# Intel vector extensions.
set(CLANG_FLAGS_ENABLE_SSE2 "-msse2")
set(CLANG_FLAGS_ENABLE_SSE4 "-msse4.1")
set(CLANG_FLAGS_ENABLE_AVX "-mavx")
set(CLANG_FLAGS_ENABLE_FMA4 "-mfma4")
set(CLANG_FLAGS_ENABLE_AVX2 "-mavx2;-mfma")
set(CLANG_FLAGS_ENABLE_AVX2128 "-mavx2;-mfma")
set(CLANG_FLAGS_ENABLE_AVX512F "-mavx512f")
set(CLANG_FLAGS_ENABLE_AVX512FNOFMA "-mavx512f")
set(CLANG_FLAGS_ENABLE_NEON32 "--target=arm-linux-gnueabihf;-mcpu=cortex-a8")
set(CLANG_FLAGS_ENABLE_NEON32VFPV4 "-march=armv7-a;-mfpu=neon-vfpv4")
# Arm AArch64 vector extensions.
set(CLANG_FLAGS_ENABLE_SVE "-march=armv8-a+sve")
set(CLANG_FLAGS_ENABLE_SVENOFMA "-march=armv8-a+sve")
# PPC64
set(CLANG_FLAGS_ENABLE_VSX "-mcpu=power8")
set(CLANG_FLAGS_ENABLE_VSXNOFMA "-mcpu=power8")
set(CLANG_FLAGS_ENABLE_VSX3 "-mcpu=power9")
set(CLANG_FLAGS_ENABLE_VSX3NOFMA "-mcpu=power9")
# IBM z
set(CLANG_FLAGS_ENABLE_VXE "-march=z14;-mzvector")
set(CLANG_FLAGS_ENABLE_VXENOFMA "-march=z14;-mzvector")
set(CLANG_FLAGS_ENABLE_VXE2 "-march=z15;-mzvector")
set(CLANG_FLAGS_ENABLE_VXE2NOFMA "-march=z15;-mzvector")
# RISC-V
set(CLANG_FLAGS_ENABLE_RVVM1 "-march=rv64gcv_zba_zbb_zbs")
set(CLANG_FLAGS_ENABLE_RVVM1NOFMA "-march=rv64gcv_zba_zbb_zbs")
set(CLANG_FLAGS_ENABLE_RVVM2 "-march=rv64gcv_zba_zbb_zbs")
set(CLANG_FLAGS_ENABLE_RVVM2NOFMA "-march=rv64gcv_zba_zbb_zbs")

set(FLAGS_OTHERS "")

# All variables storing compiler flags should be prefixed with FLAGS_
if(CMAKE_C_COMPILER_ID MATCHES "(GNU|Clang|QCC)")
  # Always compile sleef with -ffp-contract.
  set(FLAGS_STRICTMATH "-ffp-contract=off")
  set(FLAGS_FASTMATH "-ffast-math")
  set(FLAGS_NOSTRICTALIASING "-fno-strict-aliasing")

  if (SLEEF_ARCH_X86 AND SLEEF_ARCH_32BIT)
    string(CONCAT FLAGS_STRICTMATH ${FLAGS_STRICTMATH} " -msse2 -mfpmath=sse")
    string(CONCAT FLAGS_FASTMATH ${FLAGS_FASTMATH} " -msse2 -mfpmath=sse")
  endif()

  # Without the options below, gcc generates calls to libm
  string(CONCAT FLAGS_OTHERS "-fno-math-errno -fno-trapping-math")

  # Intel vector extensions.
  foreach(SIMD ${SLEEF_ALL_SUPPORTED_EXTENSIONS})
    set(FLAGS_ENABLE_${SIMD} ${CLANG_FLAGS_ENABLE_${SIMD}})
  endforeach()

  # Warning flags.
  set(FLAGS_WALL "-Wall -Wno-unused-function -Wno-attributes -Wno-unused-result")
  if(CMAKE_C_COMPILER_ID MATCHES "(GNU|QCC)")
    # The following compiler option is needed to suppress the warning
    # "AVX vector return without AVX enabled changes the ABI" at
    # src/arch/helpervecext.h:88
    string(CONCAT FLAGS_WALL ${FLAGS_WALL} " -Wno-psabi")
    set(FLAGS_ENABLE_NEON32 "-mfpu=neon")
  endif(CMAKE_C_COMPILER_ID MATCHES "(GNU|QCC)")

  if(CMAKE_C_COMPILER_ID MATCHES "Clang" AND SLEEF_ENABLE_LTO)
    if (NOT SLEEF_LLVM_AR_COMMAND)
      find_program(SLEEF_LLVM_AR_COMMAND "llvm-ar")
    endif()
    if (SLEEF_LLVM_AR_COMMAND)
      SET(CMAKE_AR ${SLEEF_LLVM_AR_COMMAND})
      SET(CMAKE_C_ARCHIVE_CREATE "<CMAKE_AR> rcs <TARGET> <LINK_FLAGS> <OBJECTS>")
      SET(CMAKE_C_ARCHIVE_FINISH "true")
    endif(SLEEF_LLVM_AR_COMMAND)
    string(CONCAT FLAGS_OTHERS "-flto=thin")
  endif(CMAKE_C_COMPILER_ID MATCHES "Clang" AND SLEEF_ENABLE_LTO)

  # Flags for generating inline headers
  set(FLAG_PREPROCESS "-E")
  set(FLAG_PRESERVE_COMMENTS "-C")
  set(FLAG_INCLUDE "-I")
  set(FLAG_DEFINE "-D")

  if (SLEEF_CLANG_ON_WINDOWS)
    # The following line is required to prevent clang from displaying
    # many warnings. Clang on Windows references MSVC header files,
    # which have deprecation and security attributes for many
    # functions.

    string(CONCAT FLAGS_WALL ${FLAGS_WALL} " -D_CRT_SECURE_NO_WARNINGS -D_CRT_NONSTDC_NO_DEPRECATE -Wno-deprecated-declarations")
  endif()
elseif(MSVC)
  # Intel vector extensions.
  if (CMAKE_CL_64)
    set(FLAGS_ENABLE_SSE2 /D__SSE2__)
    set(FLAGS_ENABLE_SSE4 /D__SSE2__ /D__SSE3__ /D__SSE4_1__)
  else()
    set(FLAGS_ENABLE_SSE2 /D__SSE2__ /arch:SSE2)
    set(FLAGS_ENABLE_SSE4 /D__SSE2__ /D__SSE3__ /D__SSE4_1__ /arch:SSE2)
  endif()
  set(FLAGS_ENABLE_AVX  /D__SSE2__ /D__SSE3__ /D__SSE4_1__ /D__AVX__ /arch:AVX)
  set(FLAGS_ENABLE_FMA4 /D__SSE2__ /D__SSE3__ /D__SSE4_1__ /D__AVX__ /D__AVX2__ /D__FMA4__ /arch:AVX2)
  set(FLAGS_ENABLE_AVX2 /D__SSE2__ /D__SSE3__ /D__SSE4_1__ /D__AVX__ /D__AVX2__ /arch:AVX2)
  set(FLAGS_ENABLE_AVX2128 /D__SSE2__ /D__SSE3__ /D__SSE4_1__ /D__AVX__ /D__AVX2__ /arch:AVX2)
  set(FLAGS_ENABLE_AVX512F /D__SSE2__ /D__SSE3__ /D__SSE4_1__ /D__AVX__ /D__AVX2__ /D__AVX512F__ /arch:AVX2)
  set(FLAGS_ENABLE_AVX512FNOFMA /D__SSE2__ /D__SSE3__ /D__SSE4_1__ /D__AVX__ /D__AVX2__ /D__AVX512F__ /arch:AVX2)
  set(FLAGS_ENABLE_PURECFMA_SCALAR /D__SSE2__ /D__SSE3__ /D__SSE4_1__ /D__AVX__ /D__AVX2__ /arch:AVX2)
  set(FLAGS_WALL "/D_CRT_SECURE_NO_WARNINGS /D_CRT_NONSTDC_NO_DEPRECATE")

  set(FLAGS_NO_ERRNO "")

  set(FLAG_PREPROCESS "/E")
  set(FLAG_PRESERVE_COMMENTS "/C")
  set(FLAG_INCLUDE "/I")
  set(FLAG_DEFINE "/D")
elseif(CMAKE_C_COMPILER_ID MATCHES "Intel")
  set(FLAGS_ENABLE_SSE2 "-msse2")
  set(FLAGS_ENABLE_SSE4 "-msse4.1")
  set(FLAGS_ENABLE_AVX "-mavx")
  set(FLAGS_ENABLE_AVX2 "-march=core-avx2")
  set(FLAGS_ENABLE_AVX2128 "-march=core-avx2")
  set(FLAGS_ENABLE_AVX512F "-xCOMMON-AVX512")
  set(FLAGS_ENABLE_AVX512FNOFMA "-xCOMMON-AVX512")
  set(FLAGS_ENABLE_PURECFMA_SCALAR "-march=core-avx2;-fno-strict-aliasing")
  set(FLAGS_ENABLE_FMA4 "-msse2")  # This is a dummy flag
  if(CMAKE_C_COMPILER_ID MATCHES "IntelLLVM")
    set(FLAGS_STRICTMATH "-fp-model strict -Qoption,cpp,--extended_float_types")
    set(FLAGS_FASTMATH "-fp-model fast -Qoption,cpp,--extended_float_types")
  else()
    set(FLAGS_STRICTMATH "-fp-model strict -Qoption,cpp,--extended_float_type")
    set(FLAGS_FASTMATH "-fp-model fast=2 -Qoption,cpp,--extended_float_type")
  endif()
  set(FLAGS_NOSTRICTALIASING "-fno-strict-aliasing")
  set(FLAGS_WALL "-fmax-errors=3 -Wall -Wno-unused -Wno-attributes")

  set(FLAGS_NO_ERRNO "")

  set(FLAG_PREPROCESS "-E")
  set(FLAG_PRESERVE_COMMENTS "-C")
  set(FLAG_INCLUDE "-I")
  set(FLAG_DEFINE "-D")
endif()

set(SLEEF_C_FLAGS "${FLAGS_WALL} ${FLAGS_STRICTMATH} ${FLAGS_OTHERS}")
if(CMAKE_C_COMPILER_ID MATCHES "(GNU|QCC)" AND CMAKE_C_COMPILER_VERSION VERSION_GREATER 6.99)
  set(DFT_C_FLAGS "${FLAGS_WALL} ${FLAGS_NOSTRICTALIASING} ${FLAGS_OTHERS}")
else()
  set(DFT_C_FLAGS "${FLAGS_WALL} ${FLAGS_NOSTRICTALIASING} ${FLAGS_FASTMATH} ${FLAGS_OTHERS}")
endif()

if(CMAKE_C_COMPILER_ID MATCHES "GNU")
  set(FLAGS_ENABLE_SVE "${FLAGS_ENABLE_SVE};-fno-tree-vrp")
endif()

if(QNX AND CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
  #set(SLEEF_C_FLAGS "${SLEEF_C_FLAGS} -march=armv8-a ")
  #set(DFT_C_FLAGS "${DFT_C_FLAGS} -march=armv8-a ")
endif()

if (CMAKE_SYSTEM_PROCESSOR MATCHES "^i.86$" AND CMAKE_C_COMPILER_ID MATCHES "GNU")
  set(SLEEF_C_FLAGS "${SLEEF_C_FLAGS} -msse2 -mfpmath=sse")
  set(DFT_C_FLAGS "${DFT_C_FLAGS} -msse2 -mfpmath=sse -m128bit-long-double")
elseif (CMAKE_SYSTEM_PROCESSOR MATCHES "^i.86$" AND CMAKE_C_COMPILER_ID MATCHES "QCC")
  set(SLEEF_C_FLAGS "${SLEEF_C_FLAGS} -msse2 -mfpmath=sse")
  set(DFT_C_FLAGS "${DFT_C_FLAGS} -msse2 -mfpmath=sse -m128bit-long-double")
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "^i.86$" AND CMAKE_C_COMPILER_ID MATCHES "Clang")
  set(SLEEF_C_FLAGS "${SLEEF_C_FLAGS} -msse2 -mfpmath=sse")
  set(DFT_C_FLAGS "${DFT_C_FLAGS} -msse2 -mfpmath=sse")
endif()

if(CYGWIN OR MINGW)
  set(SLEEF_C_FLAGS "${SLEEF_C_FLAGS} -fno-asynchronous-unwind-tables")
  set(DFT_C_FLAGS "${DFT_C_FLAGS} -fno-asynchronous-unwind-tables")
endif()

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64" AND CMAKE_C_COMPILER_ID MATCHES "GNU" AND CMAKE_C_COMPILER_VERSION VERSION_GREATER 9.3 AND CMAKE_C_COMPILER_VERSION VERSION_LESS 10.2)
  set(SLEEF_C_FLAGS "${SLEEF_C_FLAGS} -fno-shrink-wrap -fno-tree-vrp")
  set(DFT_C_FLAGS "${DFT_C_FLAGS} -fno-shrink-wrap -fno-tree-vrp")
endif()

# FEATURE DETECTION

# Long double

if(NOT SLEEF_DISABLE_LONG_DOUBLE)
  CHECK_TYPE_SIZE("long double" LD_SIZE)
  if(LD_SIZE GREATER "9")
    # This is needed to check since internal compiler error occurs with gcc 4.x
    CHECK_C_SOURCE_COMPILES("
  typedef long double vlongdouble __attribute__((vector_size(sizeof(long double)*2)));
  vlongdouble vcast_vl_l(long double d) { return (vlongdouble) { d, d }; }
  int main() { vlongdouble vld = vcast_vl_l(0);
  }" COMPILER_SUPPORTS_LONG_DOUBLE)
  endif()
else()
  message(STATUS "Support for long double disabled by CMake option")
endif()

if (SLEEF_ENFORCE_LONG_DOUBLE AND NOT COMPILER_SUPPORTS_LONG_DOUBLE)
  message(FATAL_ERROR "SLEEF_ENFORCE_LONG_DOUBLE is specified and that feature is disabled or not supported by the compiler")
endif()

# float128

if(NOT SLEEF_DISABLE_FLOAT128)
  CHECK_C_SOURCE_COMPILES("
  int main() { __float128 r = 1;
  }" COMPILER_SUPPORTS_FLOAT128)
else()
  message(STATUS "Support for float128 disabled by CMake option")
endif()

if (SLEEF_ENFORCE_FLOAT128 AND NOT COMPILER_SUPPORTS_FLOAT128)
  message(FATAL_ERROR "SLEEF_ENFORCE_FLOAT128 is specified and that feature is disabled or not supported by the compiler")
endif()

if(COMPILER_SUPPORTS_FLOAT128)
  CHECK_C_SOURCE_COMPILES("
  #include <quadmath.h>
  int main() { __float128 r = 1;
  }" COMPILER_SUPPORTS_QUADMATH)
endif()

if(COMPILER_SUPPORTS_FLOAT128)
  if (CMAKE_CXX_COMPILER_TARGET)
    set(CMAKE_REQUIRED_FLAGS "--target=${CMAKE_CXX_COMPILER_TARGET}")
  endif()
  CHECK_CXX_SOURCE_COMPILES("
#include <bit>
struct s { long long x, y; };
int main(int argc, char **argv) {
  constexpr s a = std::bit_cast<s>(__float128(0.1234)*__float128(56.789));
  static_assert((a.x ^ a.y) == 0xc7d695c93a4e2b71LL);
  __float128 i = argc;
  return (int)i;
}
" SLEEF_FLOAT128_IS_IEEEQP)
  set(CMAKE_REQUIRED_FLAGS)
endif()

if (CMAKE_CXX_COMPILER_TARGET)
  set(CMAKE_REQUIRED_FLAGS "--target=${CMAKE_CXX_COMPILER_TARGET}")
endif()
CHECK_CXX_SOURCE_COMPILES("
#include <bit>
struct s { long long x, y; };
int main(void) {
  constexpr s a = std::bit_cast<s>((long double)0.1234*(long double)56.789);
  static_assert((a.x ^ a.y) == 0xc7d695c93a4e2b71LL);
}
" SLEEF_LONGDOUBLE_IS_IEEEQP)
set(CMAKE_REQUIRED_FLAGS)

# SSE2

if(SLEEF_ARCH_X86 AND NOT SLEEF_DISABLE_SSE2)
  string (REPLACE ";" " " CMAKE_REQUIRED_FLAGS "${FLAGS_ENABLE_SSE2}")
  CHECK_C_SOURCE_COMPILES("
  #if defined(_MSC_VER)
  #include <intrin.h>
  #else
  #include <x86intrin.h>
  #endif
  int main() {
    __m128d r = _mm_mul_pd(_mm_set1_pd(1), _mm_set1_pd(2)); }"
    COMPILER_SUPPORTS_SSE2)
endif()

if (SLEEF_ENFORCE_SSE2 AND NOT COMPILER_SUPPORTS_SSE2)
  message(FATAL_ERROR "SLEEF_ENFORCE_SSE2 is specified and that feature is disabled or not supported by the compiler")
endif()

# SSE 4.1

if(SLEEF_ARCH_X86 AND NOT SLEEF_DISABLE_SSE4)
  string (REPLACE ";" " " CMAKE_REQUIRED_FLAGS "${FLAGS_ENABLE_SSE4}")
  CHECK_C_SOURCE_COMPILES("
  #if defined(_MSC_VER)
  #include <intrin.h>
  #else
  #include <x86intrin.h>
  #endif
  int main() {
    __m128d r = _mm_floor_sd(_mm_set1_pd(1), _mm_set1_pd(2)); }"
    COMPILER_SUPPORTS_SSE4)
endif()

if (SLEEF_ENFORCE_SSE4 AND NOT COMPILER_SUPPORTS_SSE4)
  message(FATAL_ERROR "SLEEF_ENFORCE_SSE4 is specified and that feature is disabled or not supported by the compiler")
endif()

# AVX

if(SLEEF_ARCH_X86 AND NOT SLEEF_DISABLE_AVX)
  string (REPLACE ";" " " CMAKE_REQUIRED_FLAGS "${FLAGS_ENABLE_AVX}")
  CHECK_C_SOURCE_COMPILES("
  #if defined(_MSC_VER)
  #include <intrin.h>
  #else
  #include <x86intrin.h>
  #endif
  int main() {
    __m256d r = _mm256_add_pd(_mm256_set1_pd(1), _mm256_set1_pd(2));
  }" COMPILER_SUPPORTS_AVX)
endif()

if (SLEEF_ENFORCE_AVX AND NOT COMPILER_SUPPORTS_AVX)
  message(FATAL_ERROR "SLEEF_ENFORCE_AVX is specified and that feature is disabled or not supported by the compiler")
endif()

# FMA4

if(SLEEF_ARCH_X86 AND NOT SLEEF_DISABLE_FMA4)
  string (REPLACE ";" " " CMAKE_REQUIRED_FLAGS "${FLAGS_ENABLE_FMA4}")
  CHECK_C_SOURCE_COMPILES("
  #if defined(_MSC_VER)
  #include <intrin.h>
  #else
  #include <x86intrin.h>
  #endif
  int main() {
    __m256d r = _mm256_macc_pd(_mm256_set1_pd(1), _mm256_set1_pd(2), _mm256_set1_pd(3)); }"
    COMPILER_SUPPORTS_FMA4)
endif()

if (SLEEF_ENFORCE_FMA4 AND NOT COMPILER_SUPPORTS_FMA4)
  message(FATAL_ERROR "SLEEF_ENFORCE_FMA4 is specified and that feature is disabled or not supported by the compiler")
endif()

# AVX2

if(SLEEF_ARCH_X86 AND NOT SLEEF_DISABLE_AVX2)
  string (REPLACE ";" " " CMAKE_REQUIRED_FLAGS "${FLAGS_ENABLE_AVX2}")
  CHECK_C_SOURCE_COMPILES("
  #if defined(_MSC_VER)
  #include <intrin.h>
  #else
  #include <x86intrin.h>
  #endif
  int main() {
    __m256i r = _mm256_abs_epi32(_mm256_set1_epi32(1)); }"
    COMPILER_SUPPORTS_AVX2)

  # AVX2 implies AVX2128
  if(COMPILER_SUPPORTS_AVX2)
    set(COMPILER_SUPPORTS_AVX2128 1)
  endif()
endif()

if (SLEEF_ENFORCE_AVX2 AND NOT COMPILER_SUPPORTS_AVX2)
  message(FATAL_ERROR "SLEEF_ENFORCE_AVX2 is specified and that feature is disabled or not supported by the compiler")
endif()

# AVX512F

if(SLEEF_ARCH_X86 AND NOT SLEEF_DISABLE_AVX512F)
  string (REPLACE ";" " " CMAKE_REQUIRED_FLAGS "${FLAGS_ENABLE_AVX512F}")
  CHECK_C_SOURCE_COMPILES("
  #if defined(_MSC_VER)
  #include <intrin.h>
  #else
  #include <x86intrin.h>
  #endif
  __m512 addConstant(__m512 arg) {
    return _mm512_add_ps(arg, _mm512_set1_ps(1.f));
  }
  int main() {
    __m512i a = _mm512_set1_epi32(1);
    __m256i ymm = _mm512_extracti64x4_epi64(a, 0);
    __mmask16 m = _mm512_cmp_epi32_mask(a, a, _MM_CMPINT_EQ);
    __m512i r = _mm512_andnot_si512(a, a); }"
    COMPILER_SUPPORTS_AVX512F)

  if (COMPILER_SUPPORTS_AVX512F)
    set(COMPILER_SUPPORTS_AVX512FNOFMA 1)
  endif()
endif()

if (SLEEF_ENFORCE_AVX512F AND NOT COMPILER_SUPPORTS_AVX512F)
  message(FATAL_ERROR "SLEEF_ENFORCE_AVX512F is specified and that feature is disabled or not supported by the compiler")
endif()

# SVE

# Darwin does not support SVE yet (see issue #474),
# therefore we disable SVE on Darwin systems.
if(SLEEF_ARCH_AARCH64 AND NOT SLEEF_DISABLE_SVE AND NOT CMAKE_SYSTEM_NAME STREQUAL "Darwin")
  string (REPLACE ";" " " CMAKE_REQUIRED_FLAGS "${FLAGS_ENABLE_SVE}")
  CHECK_C_SOURCE_COMPILES("
  #include <arm_sve.h>
  int main() {
    svint32_t r = svdup_n_s32(1); }"
    COMPILER_SUPPORTS_SVE)

  if(COMPILER_SUPPORTS_SVE)
    set(COMPILER_SUPPORTS_SVENOFMA 1)
  endif()
endif()

if (SLEEF_ENFORCE_SVE AND NOT COMPILER_SUPPORTS_SVE)
  message(FATAL_ERROR "SLEEF_ENFORCE_SVE is specified and that feature is disabled or not supported by the compiler")
endif()

# VSX

if(SLEEF_ARCH_PPC64 AND NOT SLEEF_DISABLE_VSX)
  string (REPLACE ";" " " CMAKE_REQUIRED_FLAGS "${FLAGS_ENABLE_VSX}")
  CHECK_C_SOURCE_COMPILES("
  #include <altivec.h>
  #if !defined(__LITTLE_ENDIAN__) && !defined(_AIX)
    #error \"Only VSX(ISA2.07) little-endian mode and AIX is supported \"
  #endif
  int main() {
    vector double d;
    vector unsigned char p = {
      4, 5, 6, 7, 0, 1, 2, 3, 12, 13, 14, 15, 8, 9, 10, 11
    };
    d = vec_perm(d, d, p);
  }"
    COMPILER_SUPPORTS_VSX)

  if (COMPILER_SUPPORTS_VSX)
    set(COMPILER_SUPPORTS_VSXNOFMA 1)
  endif()
endif()

if (SLEEF_ENFORCE_VSX AND NOT COMPILER_SUPPORTS_VSX)
  message(FATAL_ERROR "SLEEF_ENFORCE_VSX is specified and that feature is disabled or not supported by the compiler")
endif()

# VSX3

if(SLEEF_ARCH_PPC64 AND NOT SLEEF_DISABLE_VSX3)
  string (REPLACE ";" " " CMAKE_REQUIRED_FLAGS "${FLAGS_ENABLE_VSX3}")
  CHECK_C_SOURCE_COMPILES("
  #include <altivec.h>
  #ifndef __LITTLE_ENDIAN__
    #error \"Only VSX3 little-endian mode is supported \"
  #endif
  int main() {
    static vector double d;
    static vector unsigned long long a, b;

    d = vec_insert_exp(a, b);
  }"
    COMPILER_SUPPORTS_VSX3)

  if (COMPILER_SUPPORTS_VSX3)
    set(COMPILER_SUPPORTS_VSX3NOFMA 1)
  endif()
endif()

if (SLEEF_ENFORCE_VSX3 AND NOT COMPILER_SUPPORTS_VSX3)
  message(FATAL_ERROR "SLEEF_ENFORCE_VSX3 is specified and that feature is disabled or not supported by the compiler")
endif()

# IBM Z

if(SLEEF_ARCH_S390X AND NOT SLEEF_DISABLE_VXE)
  string (REPLACE ";" " " CMAKE_REQUIRED_FLAGS "${FLAGS_ENABLE_VXE}")
  CHECK_C_SOURCE_COMPILES("
  #include <vecintrin.h>
  int main() {
    __vector float d;
    d = vec_sqrt(d);
  }"
    COMPILER_SUPPORTS_VXE)

  if(COMPILER_SUPPORTS_VXE)
    set(COMPILER_SUPPORTS_VXENOFMA 1)
  endif()
endif()

if (SLEEF_ENFORCE_VXE AND NOT COMPILER_SUPPORTS_VXE)
  message(FATAL_ERROR "SLEEF_ENFORCE_VXE is specified and that feature is disabled or not supported by the compiler")
endif()

#

if(SLEEF_ARCH_S390X AND NOT SLEEF_DISABLE_VXE2)
  string (REPLACE ";" " " CMAKE_REQUIRED_FLAGS "${FLAGS_ENABLE_VXE2}")
  CHECK_C_SOURCE_COMPILES("
  #include <vecintrin.h>
  int main() {
    __vector float d;
    d = vec_sqrt(d);
  }"
    COMPILER_SUPPORTS_VXE2)

  if(COMPILER_SUPPORTS_VXE2)
    set(COMPILER_SUPPORTS_VXE2NOFMA 1)
  endif()
endif()

if (SLEEF_ENFORCE_VXE2 AND NOT COMPILER_SUPPORTS_VXE2)
  message(FATAL_ERROR "SLEEF_ENFORCE_VXE2 is specified and that feature is disabled or not supported by the compiler")
endif()

# RVVM1

if(SLEEF_ARCH_RISCV64 AND NOT SLEEF_DISABLE_RVVM1)
  string (REPLACE ";" " " CMAKE_REQUIRED_FLAGS "${FLAGS_ENABLE_RVVM1}")
  CHECK_C_SOURCE_COMPILES("
  #ifdef __riscv_v
  #if __riscv_v < 1000000
  #error \"RVV version 1.0 not supported\"
  #endif
  #else
  #error \"RVV not supported\"
  #endif

  #ifdef __riscv_v_intrinsic
  #if __riscv_v_intrinsic < 12000
  #error \"RVV instrinsics version 0.12 not supported\"
  #endif
  #else
  #error \"RVV intrinsics not supported\"
  #endif

  int main(void) { return 0; }"
    COMPILER_SUPPORTS_RVVM1)

  if(COMPILER_SUPPORTS_RVVM1)
    set(COMPILER_SUPPORTS_RVVM1NOFMA 1)
  endif()
endif()

if (SLEEF_ENFORCE_RVVM1 AND NOT COMPILER_SUPPORTS_RVVM1)
  message(FATAL_ERROR "SLEEF_ENFORCE_RVVM1 is specified and that feature is disabled or not supported by the compiler")
endif()

# RVVM2

if(SLEEF_ARCH_RISCV64 AND NOT SLEEF_DISABLE_RVVM2)
  string (REPLACE ";" " " CMAKE_REQUIRED_FLAGS "${FLAGS_ENABLE_RVVM2}")
  CHECK_C_SOURCE_COMPILES("
  #ifdef __riscv_v
  #if __riscv_v < 1000000
  #error \"RVV version 1.0 not supported\"
  #endif
  #else
  #error \"RVV not supported\"
  #endif

  #ifdef __riscv_v_intrinsic
  #if __riscv_v_intrinsic < 12000
  #error \"RVV instrinsics version 0.12 not supported\"
  #endif
  #else
  #error \"RVV intrinsics not supported\"
  #endif

  int main(void) { return 0; }"
    COMPILER_SUPPORTS_RVVM2)

  if(COMPILER_SUPPORTS_RVVM2)
    set(COMPILER_SUPPORTS_RVVM2NOFMA 1)
  endif()
endif()

if (SLEEF_ENFORCE_RVVM2 AND NOT COMPILER_SUPPORTS_RVVM2)
  message(FATAL_ERROR "SLEEF_ENFORCE_RVVM2 is specified and that feature is disabled or not supported by the compiler")
endif()

# CUDA

if (SLEEF_ENFORCE_CUDA AND NOT CMAKE_CUDA_COMPILER)
  message(FATAL_ERROR "SLEEF_ENFORCE_CUDA is specified and that feature is disabled or not supported by the compiler")
endif()

# OpenMP

if(NOT SLEEF_DISABLE_OPENMP)
  set(CMAKE_REQUIRED_FLAGS)
  find_package(OpenMP)
  # Check if compilation with OpenMP really succeeds
  # It might not succeed even though find_package(OpenMP) succeeds.
  if(OPENMP_FOUND)
    set (CMAKE_REQUIRED_FLAGS "${OpenMP_C_FLAGS}")
    CHECK_C_SOURCE_COMPILES("
  #include <stdio.h>
  int main() {
  int i;
  #pragma omp parallel for
    for(i=0;i < 10;i++) { putchar(0); }
  }"
      COMPILER_SUPPORTS_OPENMP)

    CHECK_C_SOURCE_COMPILES("
  #pragma omp declare simd notinbranch
  double func(double x) { return x + 1; }
  double a[1024];
  int main() {
  #pragma omp parallel for simd
    for (int i = 0; i < 1024; i++) a[i] = func(a[i]);
  }
  "
      COMPILER_SUPPORTS_OMP_SIMD)
  endif(OPENMP_FOUND)
else()
  message(STATUS "Support for OpenMP disabled by CMake option")
endif()

if (SLEEF_ENFORCE_OPENMP AND NOT COMPILER_SUPPORTS_OPENMP)
  message(FATAL_ERROR "SLEEF_ENFORCE_OPENMP is specified and that feature is disabled or not supported by the compiler")
endif()

# Weak aliases

CHECK_C_SOURCE_COMPILES("
#if defined(__CYGWIN__)
#define EXPORT __stdcall __declspec(dllexport)
#else
#define EXPORT
#endif
  EXPORT int f(int a) {
   return a + 2;
  }
  EXPORT int g(int a) __attribute__((weak, alias(\"f\")));
  int main(void) {
    return g(2);
  }"
  COMPILER_SUPPORTS_WEAK_ALIASES)
if (COMPILER_SUPPORTS_WEAK_ALIASES AND
    NOT CMAKE_SYSTEM_PROCESSOR MATCHES "arm" AND
    NOT CMAKE_SYSTEM_PROCESSOR MATCHES "^(powerpc|ppc)64" AND
    NOT SLEEF_CLANG_ON_WINDOWS AND
    NOT MINGW AND SLEEF_BUILD_GNUABI_LIBS)
  set(ENABLE_GNUABI ${COMPILER_SUPPORTS_WEAK_ALIASES})
endif()

# Built-in math functions

CHECK_C_SOURCE_COMPILES("
  int main(void) {
    double a = __builtin_sqrt (2);
    float  b = __builtin_sqrtf(2);
  }"
  COMPILER_SUPPORTS_BUILTIN_MATH)

# SYS_getrandom

CHECK_C_SOURCE_COMPILES("
#define _GNU_SOURCE
#include <unistd.h>
#include <sys/syscall.h>
#include <linux/random.h>
  int main(void) {
    int i;
    syscall(SYS_getrandom, &i, sizeof(i), 0);
  }"
  COMPILER_SUPPORTS_SYS_GETRANDOM)

#

# Reset used flags
set(CMAKE_REQUIRED_FLAGS)
set(CMAKE_REQUIRED_LIBRARIES)

# Save the default C flags
set(ORG_CMAKE_C_FLAGS ${CMAKE_C_FLAGS})
set(ORG_CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS})

##

# Check if sde64 command is available

find_program(SDE_COMMAND sde64)
if (NOT SDE_COMMAND)
  find_program(SDE_COMMAND sde)
endif()

# Check if armie command is available

find_program(ARMIE_COMMAND armie)
if (NOT SVE_VECTOR_BITS)
  set(SVE_VECTOR_BITS 128)
endif()

#

find_program(FILECHECK_COMMAND NAMES FileCheck FileCheck-11 FileCheck-10 FileCheck-9)

#

find_program(SED_COMMAND sed)

##

if(SLEEF_SHOW_ERROR_LOG)
  if (EXISTS ${PROJECT_BINARY_DIR}/CMakeFiles/CMakeError.log)
    file(READ ${PROJECT_BINARY_DIR}/CMakeFiles/CMakeError.log FILE_CONTENT)
    message("")
    message("")
    message("======  Content of CMakeError.log  ======")
    message("")
    message("${FILE_CONTENT}")
    message("")
    message("========  End of CMakeError.log  ========")
    message("")
    message("")
  endif()
endif(SLEEF_SHOW_ERROR_LOG)

##

# Set common definitions

if (NOT BUILD_SHARED_LIBS)
  set(COMMON_TARGET_DEFINITIONS SLEEF_STATIC_LIBS=1)
  set(SLEEF_STATIC_LIBS 1)
endif()

if (COMPILER_SUPPORTS_WEAK_ALIASES)
  set(COMMON_TARGET_DEFINITIONS ${COMMON_TARGET_DEFINITIONS} ENABLE_ALIAS=1)
endif()

if (COMPILER_SUPPORTS_SYS_GETRANDOM)
  set(COMMON_TARGET_DEFINITIONS ${COMMON_TARGET_DEFINITIONS} ENABLE_SYS_getrandom=1)
endif()
