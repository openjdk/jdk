
# Settings

# TESTER3_DEFINITIONS

set(TESTER3_DEFINITIONS_SSE2          ATR=cinz_ DPTYPE=__m128d SPTYPE=__m128 DPTYPESPEC=d2 SPTYPESPEC=f4  EXTSPEC=sse2)
set(TESTER3_DEFINITIONS_SSE4          ATR=cinz_ DPTYPE=__m128d SPTYPE=__m128 DPTYPESPEC=d2 SPTYPESPEC=f4  EXTSPEC=sse4)
set(TESTER3_DEFINITIONS_AVX2128       ATR=finz_ DPTYPE=__m128d SPTYPE=__m128 DPTYPESPEC=d2 SPTYPESPEC=f4  EXTSPEC=avx2128)
set(TESTER3_DEFINITIONS_AVX           ATR=cinz_ DPTYPE=__m256d SPTYPE=__m256 DPTYPESPEC=d4 SPTYPESPEC=f8  EXTSPEC=avx)
set(TESTER3_DEFINITIONS_FMA4          ATR=finz_ DPTYPE=__m256d SPTYPE=__m256 DPTYPESPEC=d4 SPTYPESPEC=f8  EXTSPEC=fma4)
set(TESTER3_DEFINITIONS_AVX2          ATR=finz_ DPTYPE=__m256d SPTYPE=__m256 DPTYPESPEC=d4 SPTYPESPEC=f8  EXTSPEC=avx2)
set(TESTER3_DEFINITIONS_AVX512F       ATR=finz_ DPTYPE=__m512d SPTYPE=__m512 DPTYPESPEC=d8 SPTYPESPEC=f16 EXTSPEC=avx512f)
set(TESTER3_DEFINITIONS_AVX512FNOFMA  ATR=cinz_ DPTYPE=__m512d SPTYPE=__m512 DPTYPESPEC=d8 SPTYPESPEC=f16 EXTSPEC=avx512fnofma)

set(TESTER3_DEFINITIONS_ADVSIMD       ATR=finz_ DPTYPE=float64x2_t SPTYPE=float32x4_t DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=advsimd)
set(TESTER3_DEFINITIONS_ADVSIMDNOFMA  ATR=cinz_ DPTYPE=float64x2_t SPTYPE=float32x4_t DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=advsimdnofma)
set(TESTER3_DEFINITIONS_SVE           ATR=finz_ DPTYPE=svfloat64_t SPTYPE=svfloat32_t DPTYPESPEC=dx SPTYPESPEC=fx EXTSPEC=sve)
set(TESTER3_DEFINITIONS_SVENOFMA      ATR=cinz_ DPTYPE=svfloat64_t SPTYPE=svfloat32_t DPTYPESPEC=dx SPTYPESPEC=fx EXTSPEC=svenofma)

set(TESTER3_DEFINITIONS_VSX       ATR=finz_ DPTYPE=SLEEF_VECTOR_DOUBLE SPTYPE=SLEEF_VECTOR_FLOAT DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=vsx)
set(TESTER3_DEFINITIONS_VSXNOFMA  ATR=cinz_ DPTYPE=SLEEF_VECTOR_DOUBLE SPTYPE=SLEEF_VECTOR_FLOAT DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=vsxnofma)
set(TESTER3_DEFINITIONS_VSX3      ATR=finz_ DPTYPE=SLEEF_VECTOR_DOUBLE SPTYPE=SLEEF_VECTOR_FLOAT DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=vsx3)
set(TESTER3_DEFINITIONS_VSX3NOFMA ATR=cinz_ DPTYPE=SLEEF_VECTOR_DOUBLE SPTYPE=SLEEF_VECTOR_FLOAT DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=vsx3nofma)

set(TESTER3_DEFINITIONS_VXE       ATR=finz_ DPTYPE=SLEEF_VECTOR_DOUBLE SPTYPE=SLEEF_VECTOR_FLOAT DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=vxe)
set(TESTER3_DEFINITIONS_VXENOFMA  ATR=cinz_ DPTYPE=SLEEF_VECTOR_DOUBLE SPTYPE=SLEEF_VECTOR_FLOAT DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=vxenofma)
set(TESTER3_DEFINITIONS_VXE2      ATR=finz_ DPTYPE=SLEEF_VECTOR_DOUBLE SPTYPE=SLEEF_VECTOR_FLOAT DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=vxe2)
set(TESTER3_DEFINITIONS_VXE2NOFMA ATR=cinz_ DPTYPE=SLEEF_VECTOR_DOUBLE SPTYPE=SLEEF_VECTOR_FLOAT DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=vxe2nofma)

set(TESTER3_DEFINITIONS_RVVM1      ATR=finz_ DPTYPE=vfloat64m1_t SPTYPE=vfloat32m1_t DPTYPESPEC=dx SPTYPESPEC=fx EXTSPEC=rvvm1 ENABLE_RVVM1)
set(TESTER3_DEFINITIONS_RVVM1NOFMA ATR=cinz_ DPTYPE=vfloat64m1_t SPTYPE=vfloat32m1_t DPTYPESPEC=dx SPTYPESPEC=fx EXTSPEC=rvvm1nofma ENABLE_RVVM1)
set(TESTER3_DEFINITIONS_RVVM2      ATR=finz_ DPTYPE=vfloat64m2_t SPTYPE=vfloat32m2_t DPTYPESPEC=dx SPTYPESPEC=fx EXTSPEC=rvvm2 ENABLE_RVVM2)
set(TESTER3_DEFINITIONS_RVVM2NOFMA ATR=cinz_ DPTYPE=vfloat64m2_t SPTYPE=vfloat32m2_t DPTYPESPEC=dx SPTYPESPEC=fx EXTSPEC=rvvm2nofma ENABLE_RVVM2)

set(TESTER3_DEFINITIONS_PUREC_SCALAR    ATR=cinz_ DPTYPE=double SPTYPE=float DPTYPESPEC=d1 SPTYPESPEC=f1 EXTSPEC=purec)
set(TESTER3_DEFINITIONS_PURECFMA_SCALAR ATR=finz_ DPTYPE=double SPTYPE=float DPTYPESPEC=d1 SPTYPESPEC=f1 EXTSPEC=purecfma)

#

if (SLEEF_ARCH_X86)
  set(TEST3_CINZ purec_scalar sse2 sse4 avx avx512fnofma)
  set(TEST3_FINZ purecfma_scalar avx2128 avx2 avx512f)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64")
  set(TEST3_CINZ purec_scalar advsimdnofma svenofma)
  set(TEST3_FINZ purecfma_scalar advsimd sve)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "arm")
  set(TEST3_CINZ purec_scalar)
  set(TEST3_FINZ purecfma_scalar)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "^(powerpc|ppc)64")
  set(TEST3_CINZ purec_scalar vsxnofma vsx3nofma)
  set(TEST3_FINZ purecfma_scalar vsx vsx3)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "s390x")
  set(TEST3_CINZ purec_scalar vxenofma vxe2nofma)
  set(TEST3_FINZ purecfma_scalar vxe vxe2)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "riscv64")
  set(TEST3_CINZ purec_scalar rvvm1nofma rvvm2nofma)
  set(TEST3_FINZ purecfma_scalar rvvm1 rvvm2)
endif()

#

link_directories(${sleef_BINARY_DIR}/lib)                 # libsleef
link_directories(${sleef_BINARY_DIR}/src/common)          # common.a
include_directories(${sleef_BINARY_DIR}/include)          # sleef.h
include_directories(${sleef_SOURCE_DIR}/src/libm)         # rename.h
include_directories(${sleef_BINARY_DIR}/src/libm/include) # rename headers

if(NOT LIB_MPFR)
  find_program(TESTER_COMMAND tester)
endif(NOT LIB_MPFR)

if (SLEEF_ENFORCE_TESTER AND NOT LIB_MPFR AND NOT TESTER_COMMAND)
  message(FATAL_ERROR "SLEEF_ENFORCE_TESTER is specified and tester is not available")
endif(SLEEF_ENFORCE_TESTER AND NOT LIB_MPFR AND NOT TESTER_COMMAND)

find_library(LIBRT rt)
if (NOT LIBRT)
  set(LIBRT "")
endif()

set(CMAKE_C_FLAGS "${ORG_CMAKE_C_FLAGS} ${SLEEF_C_FLAGS} ${FLAGS_NOSTRICTALIASING}")

set(COMMON_TARGET_PROPERTIES
  C_STANDARD 99                  # -std=gnu99
  )

if (SLEEF_ENABLE_LTO)
  list(APPEND COMMON_TARGET_PROPERTIES INTERPROCEDURAL_OPTIMIZATION TRUE)  # -flto
endif()

#

function(add_test_iut IUT C)
  if (LIB_MPFR)
    set(TESTER ${TARGET_TESTER})
  elseif(TESTER_COMMAND)
    set(TESTER ${TESTER_COMMAND})
  endif()
  # When we are crosscompiling using the mkrename* tools from a native
  # build, we use the tester executable from the native build.
  if (CMAKE_CROSSCOMPILING AND NATIVE_BUILD_DIR)
    set(TESTER ${NATIVE_BUILD_DIR}/bin/${TARGET_TESTER})
  endif(CMAKE_CROSSCOMPILING AND NATIVE_BUILD_DIR)
  if (TESTER)
    if (NOT EMULATOR)
      if (SDE_COMMAND)
        set(FLAGS_SDE "--sde" ${SDE_COMMAND})
      else()
        set(FLAGS_SDE)
      endif()
      if (ARMIE_COMMAND)
        set(FLAGS_ARMIE ${ARMIE_COMMAND} -msve-vector-bits=${SVE_VECTOR_BITS})
      else()
        set(FLAGS_ARMIE)
      endif()
      add_test(NAME ${IUT}
        COMMAND ${TESTER} ${FLAGS_SDE} ${FLAGS_ARMIE} ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/${IUT}
        WORKING_DIRECTORY ${CMAKE_RUNTIME_OUTPUT_DIRECTORY})
      set_tests_properties(${IUT} PROPERTIES COST ${C})
    else()
      add_test(NAME ${IUT}
        COMMAND ${TESTER} "--qemu" ${EMULATOR} ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/${IUT}
        WORKING_DIRECTORY ${CMAKE_RUNTIME_OUTPUT_DIRECTORY})
      set_tests_properties(${IUT} PROPERTIES COST ${C})
    endif()
  endif()
endfunction()

# Compile executable 'iut'
add_executable(${TARGET_IUT} iut.c testerutil.c)
target_compile_definitions(${TARGET_IUT} PRIVATE ${COMMON_TARGET_DEFINITIONS})
target_link_libraries(${TARGET_IUT} ${TARGET_LIBSLEEF}
  ${LIBM} ${LIBRT})
set_target_properties(${TARGET_IUT} PROPERTIES ${COMMON_TARGET_PROPERTIES})
add_test_iut(${TARGET_IUT} 1.0)
set(IUT_LIST ${TARGET_IUT})

# Compile executable 'iutcuda'
if (SLEEF_BUILD_INLINE_HEADERS AND SED_COMMAND AND CMAKE_CUDA_COMPILER)
  add_executable(iutcuda iutcuda.cu)
  set_target_properties(iutcuda PROPERTIES LINKER_LANGUAGE CUDA)
  target_compile_options(iutcuda PRIVATE "--fmad=false;-Xcompiler;-ffp-contract=off")
  add_dependencies(iutcuda ${TARGET_INLINE_HEADERS})
  add_test_iut(iutcuda 20.0)
  list(APPEND IUT_LIST iutcuda)
endif()

set(IUT_SRC iutsimd.c ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c testerutil.c)

# Add vector extension `iut`s
macro(test_extension SIMD)
  if(COMPILER_SUPPORTS_${SIMD})
    string(TOLOWER ${SIMD} LCSIMD)
    string(CONCAT TARGET_IUT${SIMD} "iut" ${LCSIMD})

    add_executable(${TARGET_IUT${SIMD}} ${IUT_SRC})
    target_compile_options(${TARGET_IUT${SIMD}}
      PRIVATE ${FLAGS_ENABLE_${SIMD}})
    target_compile_definitions(${TARGET_IUT${SIMD}}
      PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS})
    target_link_libraries(${TARGET_IUT${SIMD}} ${TARGET_LIBSLEEF}
      ${LIBM} ${LIBRT})
    if (FORCE_AAVPCS)
      target_compile_definitions(${TARGET_IUT${SIMD}} PRIVATE ENABLE_AAVPCS=1)
    endif(FORCE_AAVPCS)

    add_dependencies(${TARGET_IUT${SIMD}} ${TARGET_HEADERS})
    add_dependencies(${TARGET_IUT${SIMD}} ${TARGET_LIBSLEEF})
    set_target_properties(${TARGET_IUT${SIMD}} PROPERTIES ${COMMON_TARGET_PROPERTIES})
    if (DEFINED COSTOVERRIDE_${SIMD})
      add_test_iut(${TARGET_IUT${SIMD}} ${COSTOVERRIDE_${SIMD}})
    else()
      add_test_iut(${TARGET_IUT${SIMD}} 1.0)
    endif()
    list(APPEND IUT_LIST ${TARGET_IUT${SIMD}})

    # The iut programs whose names begin with "iuty" are the iut for the
    # deterministic version of functions. By checking the result of
    # testing with iutysse2, for example, it can be checked that the
    # corresponding deterministic functions passes the accuracy and
    # nonnumber tests.

    string(CONCAT IUTYNAME "iuty" ${LCSIMD})
    add_executable(${IUTYNAME} ${IUT_SRC})
    target_compile_options(${IUTYNAME}
      PRIVATE ${FLAGS_ENABLE_${SIMD}})
    target_compile_definitions(${IUTYNAME}
      PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS} DETERMINISTIC=1)
    target_link_libraries(${IUTYNAME} ${TARGET_LIBSLEEF}
      ${LIBM} ${LIBRT})
    add_dependencies(${IUTYNAME} ${TARGET_HEADERS})
    add_dependencies(${IUTYNAME} ${TARGET_LIBSLEEF})
    set_target_properties(${IUTYNAME} PROPERTIES ${COMMON_TARGET_PROPERTIES})
    if (DEFINED COSTOVERRIDE_${SIMD})
      add_test_iut(${IUTYNAME} ${COSTOVERRIDE_${SIMD}})
    else()
      add_test_iut(${IUTYNAME} 1.0)
    endif()
    list(APPEND IUT_LIST ${IUTYNAME})

    # The iut programs whose names begin with "iuti" are the iut for the
    # inline version of functions.

    if (SLEEF_BUILD_INLINE_HEADERS AND SED_COMMAND)
      string(CONCAT IUTINAME "iuti" ${LCSIMD})
      add_executable(${IUTINAME} ${IUT_SRC})
      target_compile_options(${IUTINAME} PRIVATE ${FLAGS_ENABLE_${SIMD}})
      target_compile_definitions(${IUTINAME}
        PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS}
        USE_INLINE_HEADER="sleefinline_${LCSIMD}.h"
        MACRO_ONLY_HEADER="macroonly${SIMD}.h"
        SIMD_SUFFIX=_${LCSIMD}_sleef
        )
      target_include_directories(${IUTINAME} PRIVATE ${PROJECT_BINARY_DIR}/include)
      target_link_libraries(${IUTINAME} ${LIBM} ${LIBRT})
      add_dependencies(${IUTINAME} ${TARGET_INLINE_HEADERS})
      set_target_properties(${IUTINAME} PROPERTIES C_STANDARD 99)
      if (DEFINED COSTOVERRIDE_${SIMD})
        add_test_iut(${IUTINAME} ${COSTOVERRIDE_${SIMD}})
      else()
        add_test_iut(${IUTINAME} 1.0)
      endif()
      list(APPEND IUT_LIST ${IUTINAME})
    endif(SLEEF_BUILD_INLINE_HEADERS AND SED_COMMAND)

    if(LIB_MPFR AND NOT ${SIMD} STREQUAL NEON32 AND NOT ${SIMD} STREQUAL NEON32VFPV4 AND NOT MINGW)
      # Build tester2 SIMD
      string(TOLOWER ${SIMD} SCSIMD)
      foreach(P dp sp)
              set(T "tester2${SCSIMD}${P}")
              add_executable(${T} tester2simd${P}.c testerutil.c)
              if(FORCE_AAVPCS)
                target_compile_definitions(${T} PRIVATE ENABLE_AAVPCS=1)
              endif(FORCE_AAVPCS)
              target_compile_options(${T} PRIVATE ${FLAGS_ENABLE_${SIMD}})
              target_compile_definitions(${T} PRIVATE ENABLE_${SIMD}=1 USEMPFR=1 ${COMMON_TARGET_DEFINITIONS})
              set_target_properties(${T} PROPERTIES ${COMMON_TARGET_PROPERTIES})
              target_link_libraries(${T} ${TARGET_LIBSLEEF} ${LIB_MPFR} ${LIBM} ${LIBGMP})
              add_dependencies(${T} ${TARGET_HEADERS})
              add_dependencies(${T} ${TARGET_LIBSLEEF})
              if (MPFR_INCLUDE_DIR)
                target_include_directories(${T} PRIVATE ${MPFR_INCLUDE_DIR})
              endif()

              # The tester2 programs whose name begins with "tester2y" are the
              # testing program for the deterministic version of functions.

              set(T "tester2y${SCSIMD}${P}")
              add_executable(${T} tester2simd${P}.c testerutil.c)
              target_compile_options(${T} PRIVATE ${FLAGS_ENABLE_${SIMD}})
              target_compile_definitions(${T} PRIVATE ENABLE_${SIMD}=1 USEMPFR=1 ${COMMON_TARGET_DEFINITIONS} DETERMINISTIC=1)
              set_target_properties(${T} PROPERTIES ${COMMON_TARGET_PROPERTIES})
              target_link_libraries(${T} ${TARGET_LIBSLEEF} ${LIB_MPFR} ${LIBM} ${LIBGMP})
              add_dependencies(${T} ${TARGET_HEADERS})
              add_dependencies(${T} ${TARGET_LIBSLEEF})
              if (MPFR_INCLUDE_DIR)
                target_include_directories(${T} PRIVATE ${MPFR_INCLUDE_DIR})
              endif()
      endforeach()
    endif()

    if(NOT ${SIMD} STREQUAL NEON32 AND NOT ${SIMD} STREQUAL NEON32VFPV4 AND SLEEF_OPENSSL_FOUND)
      # Build tester3
      string(TOLOWER ${SIMD} SCSIMD)
      set(T "tester3${SCSIMD}")
      add_executable(${T} tester3.c ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c testerutil.c)
      target_compile_options(${T} PRIVATE ${FLAGS_ENABLE_${SIMD}})
      target_compile_definitions(${T} PRIVATE ${COMMON_TARGET_DEFINITIONS} ${TESTER3_DEFINITIONS_${SIMD}})
      set_target_properties(${T} PROPERTIES ${COMMON_TARGET_PROPERTIES})

      # Enable Vector PCS for Advanced SIMD (if supported)
      if(FORCE_AAVPCS)
        host_target_AAVPCS_definitions(${T})
      endif()

      target_link_libraries(${T} ${TARGET_LIBSLEEF} ${LIBM} ${SLEEF_OPENSSL_LIBRARIES})
      target_include_directories(${T} PRIVATE ${SLEEF_OPENSSL_INCLUDE_DIR})
      add_dependencies(${T} ${TARGET_HEADERS})
      add_dependencies(${T} ${TARGET_LIBSLEEF})

      # Add test with tester3
      list(FIND TEST3_CINZ ${SCSIMD} INDEX_TEST3_CINZ)
      if (NOT INDEX_TEST3_CINZ EQUAL -1)
        if (SDE_COMMAND)
          add_test(NAME tester3${SCSIMD} COMMAND ${SDE_COMMAND} "--" ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/tester3${SCSIMD} ${sleef_SOURCE_DIR}/src/libm-tester/hash_cinz.txt)
        elseif(EMULATOR)
          add_test(NAME tester3${SCSIMD} COMMAND ${EMULATOR} ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/tester3${SCSIMD} ${sleef_SOURCE_DIR}/src/libm-tester/hash_cinz.txt)
        else()
          add_test(NAME tester3${SCSIMD} COMMAND tester3${SCSIMD} ${sleef_SOURCE_DIR}/src/libm-tester/hash_cinz.txt)
        endif()
        if (DEFINED COSTOVERRIDE_${SIMD})
          set_tests_properties(tester3${SCSIMD} PROPERTIES COST ${COSTOVERRIDE_${SIMD}})
        else()
          set_tests_properties(tester3${SCSIMD} PROPERTIES COST 0.5)
        endif()
      endif()

      list(FIND TEST3_FINZ ${SCSIMD} INDEX_TEST3_FINZ)
      if (NOT INDEX_TEST3_FINZ EQUAL -1)
        if (SDE_COMMAND)
          add_test(NAME tester3${SCSIMD} COMMAND ${SDE_COMMAND} "--" ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/tester3${SCSIMD} ${sleef_SOURCE_DIR}/src/libm-tester/hash_finz.txt)
        elseif(EMULATOR)
          add_test(NAME tester3${SCSIMD} COMMAND ${EMULATOR} ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/tester3${SCSIMD} ${sleef_SOURCE_DIR}/src/libm-tester/hash_finz.txt)
        else()
          add_test(NAME tester3${SCSIMD} COMMAND tester3${SCSIMD} ${sleef_SOURCE_DIR}/src/libm-tester/hash_finz.txt)
        endif()
        if (DEFINED COSTOVERRIDE_${SIMD})
          set_tests_properties(tester3${SCSIMD} PROPERTIES COST ${COSTOVERRIDE_${SIMD}})
        else()
          set_tests_properties(tester3${SCSIMD} PROPERTIES COST 0.5)
        endif()
      endif()
    endif()
  endif(COMPILER_SUPPORTS_${SIMD})
endmacro(test_extension)

foreach(SIMD ${SLEEF_SUPPORTED_LIBM_EXTENSIONS})
  test_extension(${SIMD})
endforeach()

function(add_gnuabi_compatibility_test SIMD MASKED)
  if (MASKED)
    set(GNUABI_COMPATIBILITY_TEST gnuabi_compatibility_${SIMD}_masked)
  else(MASKED)
    set(GNUABI_COMPATIBILITY_TEST gnuabi_compatibility_${SIMD})
  endif(MASKED)
  add_executable(${GNUABI_COMPATIBILITY_TEST} gnuabi_compatibility.c ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)
  set_target_properties(${GNUABI_COMPATIBILITY_TEST} PROPERTIES ${COMMON_TARGET_PROPERTIES})
  target_compile_options(${GNUABI_COMPATIBILITY_TEST}
    PRIVATE ${FLAGS_ENABLE_${SIMD}})
  if (MASKED)
    target_compile_definitions(${GNUABI_COMPATIBILITY_TEST}
      PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS} MASKED_GNUABI=1)
  else(MASKED)
    target_compile_definitions(${GNUABI_COMPATIBILITY_TEST}
      PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS})
  endif(MASKED)
  if (FORCE_AAVPCS)
    target_compile_definitions(${GNUABI_COMPATIBILITY_TEST} PRIVATE ENABLE_AAVPCS=1)
  endif(FORCE_AAVPCS)
  target_link_libraries(${GNUABI_COMPATIBILITY_TEST} ${TARGET_LIBSLEEFGNUABI} ${LIBM})
  # These are linker tests that don't really need to be executed,
  # but seeing them in the report of ctest gives an idea of what
  # has been built for testing.
  if (EMULATOR)
    add_test(NAME ${GNUABI_COMPATIBILITY_TEST}
      COMMAND ${EMULATOR} $<TARGET_FILE:${GNUABI_COMPATIBILITY_TEST}>
      WORKING_DIRECTORY ${CMAKE_RUNTIME_OUTPUT_DIRECTORY})
  elseif(SDE_COMMAND)
    add_test(NAME ${GNUABI_COMPATIBILITY_TEST}
      COMMAND ${SDE_COMMAND} "--" $<TARGET_FILE:${GNUABI_COMPATIBILITY_TEST}>)
  else()
    add_test(NAME ${GNUABI_COMPATIBILITY_TEST}
      COMMAND $<TARGET_FILE:${GNUABI_COMPATIBILITY_TEST}>)
  endif(EMULATOR)
endfunction(add_gnuabi_compatibility_test)

if(ENABLE_GNUABI)
  foreach(SIMD ${SLEEF_SUPPORTED_GNUABI_EXTENSIONS})
    if(COMPILER_SUPPORTS_${SIMD})
      # GNUABI compatibility for the unmasked symbols.
      add_gnuabi_compatibility_test(${SIMD} OFF)
      # GNUABI compatibility for the masked symbols.
      if (MKMASKED_PARAMS_GNUABI_${SIMD}_sp)
        add_gnuabi_compatibility_test(${SIMD} ON)
      endif(MKMASKED_PARAMS_GNUABI_${SIMD}_sp)
    endif (COMPILER_SUPPORTS_${SIMD})
  endforeach(SIMD ${SLEEF_SUPPORTED_GNUABI_EXTENSIONS})
endif(ENABLE_GNUABI)

#

if (SLEEF_ARCH_X86)
  # iutdsp128
  add_executable(iutdsp128 ${IUT_SRC})
  target_compile_definitions(iutdsp128 PRIVATE ENABLE_DSP128=1 ${COMMON_TARGET_DEFINITIONS})
  target_compile_options(iutdsp128 PRIVATE ${FLAGS_ENABLE_SSE2})
  target_link_libraries(iutdsp128 ${TARGET_LIBSLEEF} ${LIBM} ${LIBRT})
  add_dependencies(iutdsp128 ${TARGET_HEADERS} ${TARGET_LIBSLEEF})
  add_test_iut(iutdsp128 1.0)
  list(APPEND IUT_LIST iutdsp128)

  # iutdsp256
  add_executable(iutdsp256 ${IUT_SRC})
  target_compile_definitions(iutdsp256 PRIVATE ENABLE_DSP256=1 ${COMMON_TARGET_DEFINITIONS})
  target_compile_options(iutdsp256 PRIVATE ${FLAGS_ENABLE_AVX})
  target_link_libraries(iutdsp256 ${TARGET_LIBSLEEF} ${LIBM} ${LIBRT})
  add_dependencies(iutdsp256 ${TARGET_HEADERS} ${TARGET_LIBSLEEF})
  add_test_iut(iutdsp256 1.0)
  list(APPEND IUT_LIST iutdsp256)
endif(SLEEF_ARCH_X86)

if (SLEEF_ARCH_PPC64)
  add_executable(iutdsp128 ${IUT_SRC})
  target_compile_definitions(iutdsp128 PRIVATE ENABLE_DSPPOWER_128=1 ${COMMON_TARGET_DEFINITIONS})
  target_compile_options(iutdsp128 PRIVATE ${FLAGS_ENABLE_VSX})
  target_link_libraries(iutdsp128 ${TARGET_LIBSLEEF} ${LIBM} ${LIBRT})
  add_dependencies(iutdsp128 ${TARGET_HEADERS} ${TARGET_LIBSLEEF})
  add_test_iut(iutdsp128 1.0)
  list(APPEND IUT_LIST iutdsp128)
endif(SLEEF_ARCH_PPC64)

if (SLEEF_ARCH_S390X)
  add_executable(iutdsp128 ${IUT_SRC})
  target_compile_definitions(iutdsp128 PRIVATE ENABLE_DSPS390X_128=1 ${COMMON_TARGET_DEFINITIONS})
  target_compile_options(iutdsp128 PRIVATE ${FLAGS_ENABLE_VXE})
  target_link_libraries(iutdsp128 ${TARGET_LIBSLEEF} ${LIBM} ${LIBRT})
  add_dependencies(iutdsp128 ${TARGET_HEADERS} ${TARGET_LIBSLEEF})
  add_test_iut(iutdsp128 1.0)
  list(APPEND IUT_LIST iutdsp128)
endif(SLEEF_ARCH_S390X)

if(SLEEF_BUILD_SCALAR_LIB)
  # Compile executable 'iutscalar'
  add_executable(iutscalar iut.c testerutil.c)
  target_compile_definitions(iutscalar PRIVATE ${COMMON_TARGET_DEFINITIONS})
  target_link_libraries(iutscalar sleefscalar ${TARGET_LIBSLEEF} ${LIBM} ${LIBRT})
  set_target_properties(iutscalar PROPERTIES ${COMMON_TARGET_PROPERTIES})
  add_test_iut(iutscalar 1.0)
  list(APPEND IUT_LIST iutscalar)
endif()

if(LIB_MPFR AND NOT MINGW)
  # Build tester2 scalar
  set(PRECISIONS dp sp)
  if(COMPILER_SUPPORTS_LONG_DOUBLE)
    list(APPEND PRECISIONS ld)
  endif()
  if(COMPILER_SUPPORTS_QUADMATH)
    list(APPEND PRECISIONS qp)
    set(LIBQUADMATH "-lquadmath")
    set(ENABLEFLOAT128 PRIVATE ENABLEFLOAT128=1)
  endif()
  foreach(P ${PRECISIONS})
    set(T "tester2${P}")
    add_executable(${T} tester2${P}.c testerutil.c)
    target_compile_definitions(${T} PRIVATE USEMPFR=1 ${ENABLEFLOAT128} ${COMMON_TARGET_DEFINITIONS})
    set_target_properties(${T} PROPERTIES ${COMMON_TARGET_PROPERTIES})
    if (FORCE_AAVPCS)
      target_compile_definitions(${T} PRIVATE ENABLE_AAVPCS=1)
    endif(FORCE_AAVPCS)
    if (MPFR_INCLUDE_DIR)
      target_include_directories(${T} PRIVATE ${MPFR_INCLUDE_DIR})
    endif()
    target_link_libraries(${T} ${TARGET_LIBSLEEF} ${LIBQUADMATH} ${LIB_MPFR} ${LIBM} ${LIBGMP})
    add_dependencies(${T} ${TARGET_HEADERS})
    add_dependencies(${T} ${TARGET_LIBSLEEF})
  endforeach()

  # Compile executable 'tester'
  add_host_executable(${TARGET_TESTER} tester.c testerutil.c)
  if (NOT CMAKE_CROSSCOMPILING)
    target_link_libraries(${TARGET_TESTER} ${LIB_MPFR} ${TARGET_LIBSLEEF} ${LIBM} ${LIBGMP})
    target_compile_definitions(${TARGET_TESTER}
      PRIVATE USEMPFR=1 ${COMMON_TARGET_DEFINITIONS})
    target_compile_options(${TARGET_TESTER} PRIVATE -Wno-unused-result)
    set_target_properties(${TARGET_TESTER} PROPERTIES ${COMMON_TARGET_PROPERTIES})
    if (MPFR_INCLUDE_DIR)
      target_include_directories(${TARGET_TESTER} PRIVATE ${MPFR_INCLUDE_DIR})
    endif()
  endif()
endif(LIB_MPFR AND NOT MINGW)

if(ENABLE_GNUABI AND COMPILER_SUPPORTS_OMP_SIMD AND NOT SLEEF_TARGET_PROCESSOR MATCHES "^i.86$")
  # Build tester for vectorabi
  add_executable(testervecabi testervecabi.c)
  target_compile_definitions(testervecabi PRIVATE ${COMMON_TARGET_DEFINITIONS})
  target_compile_options(testervecabi PRIVATE ${OpenMP_C_FLAGS})
  target_link_libraries(testervecabi ${TARGET_LIBSLEEF} ${OpenMP_C_FLAGS})
  set_target_properties(testervecabi PROPERTIES C_STANDARD 99)
  add_test(NAME testervecabi COMMAND ${EMULATOR} testervecabi
    WORKING_DIRECTORY ${CMAKE_RUNTIME_OUTPUT_DIRECTORY})
endif()

# mveclibtest

if (ENABLE_GNUABI AND SLEEF_ARCH_X86 AND CMAKE_C_COMPILER_ID MATCHES "GNU" AND CMAKE_C_COMPILER_VERSION VERSION_GREATER 7.99)
  add_executable(mveclibtest-sse2 mveclibtest.c ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)
  set_target_properties(mveclibtest-sse2 PROPERTIES C_STANDARD 99)
  target_compile_options(mveclibtest-sse2 PRIVATE ${FLAGS_FASTMATH} "-O3")
  target_link_libraries(mveclibtest-sse2 ${TARGET_LIBSLEEF} ${TARGET_LIBSLEEFGNUABI})
  add_dependencies(mveclibtest-sse2 ${TARGET_HEADERS})
  add_test(NAME mveclibtest-sse2 COMMAND mveclibtest-sse2)

  add_executable(mveclibtest-avx mveclibtest.c ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)
  set_target_properties(mveclibtest-avx PROPERTIES C_STANDARD 99)
  target_compile_options(mveclibtest-avx PRIVATE ${FLAGS_FASTMATH} ${FLAGS_ENABLE_AVX} "-O3")
  target_link_libraries(mveclibtest-avx ${TARGET_LIBSLEEF} ${TARGET_LIBSLEEFGNUABI})
  add_dependencies(mveclibtest-avx ${TARGET_HEADERS})
  add_test(NAME mveclibtest-avx COMMAND mveclibtest-avx)

  add_executable(mveclibtest-avx2 mveclibtest.c ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)
  set_target_properties(mveclibtest-avx2 PROPERTIES C_STANDARD 99)
  target_compile_options(mveclibtest-avx2 PRIVATE ${FLAGS_FASTMATH} ${FLAGS_ENABLE_AVX2} "-O3")
  target_link_libraries(mveclibtest-avx2 ${TARGET_LIBSLEEF} ${TARGET_LIBSLEEFGNUABI})
  add_dependencies(mveclibtest-avx2 ${TARGET_HEADERS})
  add_test(NAME mveclibtest-avx2 COMMAND mveclibtest-avx2)

  add_executable(mveclibtest-avx512f mveclibtest.c ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)
  set_target_properties(mveclibtest-avx512f PROPERTIES C_STANDARD 99)
  target_compile_options(mveclibtest-avx512f PRIVATE ${FLAGS_FASTMATH} ${FLAGS_ENABLE_AVX512F} "-O3")
  target_link_libraries(mveclibtest-avx512f ${TARGET_LIBSLEEF} ${TARGET_LIBSLEEFGNUABI})
  add_dependencies(mveclibtest-avx512f ${TARGET_HEADERS})
  add_test(NAME mveclibtest-avx512f COMMAND mveclibtest-avx512f)
endif()

#

if (FILECHECK_COMMAND AND COMPILER_SUPPORTS_OPENMP AND SLEEF_ARCH_X86 AND CMAKE_C_COMPILER_ID MATCHES "GNU" AND CMAKE_C_COMPILER_VERSION VERSION_GREATER 7.99)
  add_test(NAME autovec-avx2 COMMAND sh -c "${CMAKE_C_COMPILER} -Wno-attributes -fopenmp -mavx2 -O3 ${CMAKE_CURRENT_SOURCE_DIR}/autovec.c -I${sleef_BINARY_DIR}/include -S -o- | ${FILECHECK_COMMAND} ${CMAKE_CURRENT_SOURCE_DIR}/autovec.c -check-prefix=CHECK-AVX2")
  add_test(NAME autovec-sse2 COMMAND sh -c "${CMAKE_C_COMPILER} -Wno-attributes -fopenmp -msse2 -O3 ${CMAKE_CURRENT_SOURCE_DIR}/autovec.c -I${sleef_BINARY_DIR}/include -S -o- | ${FILECHECK_COMMAND} ${CMAKE_CURRENT_SOURCE_DIR}/autovec.c -check-prefix=CHECK-SSE2")
  add_test(NAME testervecabi-sse2 COMMAND sh -c "${CMAKE_C_COMPILER} -Wno-attributes -fopenmp -msse2 -O3 ${CMAKE_CURRENT_SOURCE_DIR}/testervecabi.c -I${sleef_BINARY_DIR}/include -S -o- | ${FILECHECK_COMMAND} ${CMAKE_CURRENT_SOURCE_DIR}/testervecabi.c -check-prefix=CHECK-SSE2")
  add_test(NAME testervecabi-avx2 COMMAND sh -c "${CMAKE_C_COMPILER} -Wno-attributes -fopenmp -mavx2 -O3 ${CMAKE_CURRENT_SOURCE_DIR}/testervecabi.c -I${sleef_BINARY_DIR}/include -S -o- | ${FILECHECK_COMMAND} ${CMAKE_CURRENT_SOURCE_DIR}/testervecabi.c -check-prefix=CHECK-AVX2")
endif()

# Tests depends on the library
add_dependencies(${TARGET_IUT} ${TARGET_HEADERS})
