
# Settings

# TESTER3_DEFINITIONS

set(TESTER3_DEFINITIONS_SSE2            DPTYPE=__m128d SPTYPE=__m128 DPTYPESPEC=d2 SPTYPESPEC=f4  EXTSPEC=sse2)
set(TESTER3_DEFINITIONS_AVX2128         DPTYPE=__m128d SPTYPE=__m128 DPTYPESPEC=d2 SPTYPESPEC=f4  EXTSPEC=avx2128)
set(TESTER3_DEFINITIONS_FMA4            DPTYPE=__m256d SPTYPE=__m256 DPTYPESPEC=d4 SPTYPESPEC=f8  EXTSPEC=fma4)
set(TESTER3_DEFINITIONS_AVX2            DPTYPE=__m256d SPTYPE=__m256 DPTYPESPEC=d4 SPTYPESPEC=f8  EXTSPEC=avx2)
set(TESTER3_DEFINITIONS_AVX512F         DPTYPE=__m512d SPTYPE=__m512 DPTYPESPEC=d8 SPTYPESPEC=f16 EXTSPEC=avx512f)

set(TESTER3_DEFINITIONS_ADVSIMD         DPTYPE=float64x2_t SPTYPE=float32x4_t DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=advsimd)
set(TESTER3_DEFINITIONS_SVE             DPTYPE=svfloat64_t SPTYPE=svfloat32_t DPTYPESPEC=dx SPTYPESPEC=fx EXTSPEC=sve)

set(TESTER3_DEFINITIONS_VSX             DPTYPE=SLEEF_VECTOR_DOUBLE SPTYPE=SLEEF_VECTOR_FLOAT DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=vsx)
set(TESTER3_DEFINITIONS_VSX3            DPTYPE=SLEEF_VECTOR_DOUBLE SPTYPE=SLEEF_VECTOR_FLOAT DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=vsx3)

set(TESTER3_DEFINITIONS_VXE             DPTYPE=SLEEF_VECTOR_DOUBLE SPTYPE=SLEEF_VECTOR_FLOAT DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=vxe)
set(TESTER3_DEFINITIONS_VXE2            DPTYPE=SLEEF_VECTOR_DOUBLE SPTYPE=SLEEF_VECTOR_FLOAT DPTYPESPEC=d2 SPTYPESPEC=f4 EXTSPEC=vxe2)

set(TESTER3_DEFINITIONS_RVVM1           DPTYPE=vfloat64m1_t SPTYPE=vfloat32m1_t DPTYPESPEC=dx SPTYPESPEC=fx EXTSPEC=rvvm1 ENABLE_RVVM1)
set(TESTER3_DEFINITIONS_RVVM2           DPTYPE=vfloat64m2_t SPTYPE=vfloat32m2_t DPTYPESPEC=dx SPTYPESPEC=fx EXTSPEC=rvvm2 ENABLE_RVVM2)

set(TESTER3_DEFINITIONS_PUREC_SCALAR    DPTYPE=double SPTYPE=float DPTYPESPEC=d1 SPTYPESPEC=f1 EXTSPEC=purec)
set(TESTER3_DEFINITIONS_PURECFMA_SCALAR DPTYPE=double SPTYPE=float DPTYPESPEC=d1 SPTYPESPEC=f1 EXTSPEC=purecfma)

#

link_directories(${sleef_BINARY_DIR}/lib)                 # libsleef
link_directories(${sleef_BINARY_DIR}/src/common)          # common.a
include_directories(${sleef_BINARY_DIR}/include)          # sleef.h
include_directories(${sleef_SOURCE_DIR}/src/libm)         # rename.h
include_directories(${sleef_BINARY_DIR}/src/libm/include) # rename headers

if (SLEEF_ENFORCE_TESTER AND NOT SLEEF_ENABLE_TESTER)
  message(FATAL_ERROR "SLEEF_ENFORCE_TESTER is specified but SLEEF_ENABLE_TESTER is false")
endif(SLEEF_ENFORCE_TESTER AND NOT SLEEF_ENABLE_TESTER)

if(SLEEF_ENABLE_TESTER AND NOT LIB_MPFR)
  find_program(TESTER_COMMAND tester)
endif(SLEEF_ENABLE_TESTER AND NOT LIB_MPFR)

if (SLEEF_ENFORCE_TESTER AND NOT LIB_MPFR AND NOT TESTER_COMMAND)
  message(FATAL_ERROR "SLEEF_ENFORCE_TESTER is specified and tester is not available")
endif(SLEEF_ENFORCE_TESTER AND NOT LIB_MPFR AND NOT TESTER_COMMAND)

if (SLEEF_ENFORCE_TESTER4 AND NOT SLEEF_ENABLE_TESTER4)
  message(FATAL_ERROR "SLEEF_ENFORCE_TESTER4 is specified but SLEEF_ENABLE_TESTER4 is false")
endif()

if (SLEEF_ENFORCE_TESTER4 AND NOT TLFLOAT_LIBRARIES)
  message(FATAL_ERROR "SLEEF_ENFORCE_TESTER4 is specified but TLFloat is not available")
endif()

find_library(LIBRT rt)
if (NOT LIBRT)
  set(LIBRT "")
endif()

set(CMAKE_C_FLAGS "${ORG_CMAKE_C_FLAGS} ${SLEEF_C_FLAGS} ${FLAGS_NOSTRICTALIASING}")
set(CMAKE_CXX_FLAGS "${ORG_CMAKE_CXX_FLAGS} ${SLEEF_C_FLAGS} ${FLAGS_NOSTRICTALIASING}")

set(COMMON_TARGET_PROPERTIES
  C_STANDARD 99                  # -std=gnu99
  )

if (SLEEF_ENABLE_LTO)
  list(APPEND COMMON_TARGET_PROPERTIES INTERPROCEDURAL_OPTIMIZATION TRUE)  # -flto
endif()

#

function(add_test_with_emu C CMD)
  if (SDE_COMMAND)
    add_test(NAME ${CMD} COMMAND ${SDE_COMMAND} "--" ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/${CMD})
  elseif(EMULATOR)
    add_test(NAME ${CMD} COMMAND ${EMULATOR} ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/${CMD})
  else()
    add_test(NAME ${CMD} COMMAND ${CMD})
  endif()
  set_tests_properties(${CMD} PROPERTIES COST ${C})
endfunction()

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
      add_test(NAME ${IUT}
        COMMAND ${TESTER} ${FLAGS_SDE} ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/${IUT}
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

if (SLEEF_ENABLE_TESTER)
  # Compile executable 'iut'
  add_executable(${TARGET_IUT} iut.c)
  target_compile_definitions(${TARGET_IUT} PRIVATE ${COMMON_TARGET_DEFINITIONS})
  target_link_libraries(${TARGET_IUT} ${TARGET_LIBSLEEF} ${TARGET_TESTERUTIL_OBJ}
    ${TLFLOAT_LIBRARIES} ${LIBM} ${LIBRT})
  set_target_properties(${TARGET_IUT} PROPERTIES ${COMMON_TARGET_PROPERTIES})
  add_test_iut(${TARGET_IUT} 1.0)
  set(IUT_LIST ${TARGET_IUT})

  # Tests depends on the library
  add_dependencies(${TARGET_IUT} ${TARGET_HEADERS})
endif()

# Compile executable 'iutcuda'
if (SLEEF_BUILD_INLINE_HEADERS AND SED_COMMAND AND CMAKE_CUDA_COMPILER)
  add_executable(iutcuda iutcuda.cu)
  set_target_properties(iutcuda PROPERTIES LINKER_LANGUAGE CUDA)
  target_compile_options(iutcuda PRIVATE "--fmad=false;-Xcompiler;-ffp-contract=off")
  add_dependencies(iutcuda ${TARGET_INLINE_HEADERS})
  add_test_iut(iutcuda 20.0)
  list(APPEND IUT_LIST iutcuda)
endif()

set(IUT_SRC iutsimd.c ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)

# Add vector extension `iut`s
macro(test_extension SIMD)
  if(COMPILER_SUPPORTS_${SIMD})
    string(TOLOWER ${SIMD} LCSIMD)

    if (SLEEF_ENABLE_TESTER)
      string(CONCAT TARGET_IUT${SIMD} "iut" ${LCSIMD})

      add_executable(${TARGET_IUT${SIMD}} ${IUT_SRC})
      target_compile_options(${TARGET_IUT${SIMD}}
        PRIVATE ${FLAGS_ENABLE_${SIMD}})
      target_compile_definitions(${TARGET_IUT${SIMD}}
        PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS})
      target_link_libraries(${TARGET_IUT${SIMD}} ${TARGET_LIBSLEEF} ${TARGET_TESTERUTIL_OBJ}
        ${TLFLOAT_LIBRARIES} ${LIBM} ${LIBRT})

      add_dependencies(${TARGET_IUT${SIMD}} ${TARGET_HEADERS})
      add_dependencies(${TARGET_IUT${SIMD}} ${TARGET_LIBSLEEF})
      set_target_properties(${TARGET_IUT${SIMD}} PROPERTIES ${COMMON_TARGET_PROPERTIES})
      if (DEFINED COSTOVERRIDE_${SIMD})
        add_test_iut(${TARGET_IUT${SIMD}} ${COSTOVERRIDE_${SIMD}})
      else()
        add_test_iut(${TARGET_IUT${SIMD}} 1.0)
      endif()
      list(APPEND IUT_LIST ${TARGET_IUT${SIMD}})

      # The iut programs whose names begin with "iuti" are the iut for the
      # inline version of functions.

      list(FIND SLEEF_SUPPORTED_INLINE_LIBM_EXTENSIONS ${SIMD} INDEX_INLINE)
      if (SLEEF_BUILD_INLINE_HEADERS AND SED_COMMAND AND NOT INDEX_INLINE EQUAL -1)
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
        target_link_libraries(${IUTINAME} ${TARGET_TESTERUTIL_OBJ} ${TLFLOAT_LIBRARIES} ${LIBM} ${LIBRT})
        add_dependencies(${IUTINAME} ${TARGET_INLINE_HEADERS} ext_tlfloat)
        set_target_properties(${IUTINAME} PROPERTIES C_STANDARD 99)
        if (DEFINED COSTOVERRIDE_${SIMD})
          add_test_iut(${IUTINAME} ${COSTOVERRIDE_${SIMD}})
        else()
          add_test_iut(${IUTINAME} 1.0)
        endif()
        list(APPEND IUT_LIST ${IUTINAME})
      endif()
    endif(SLEEF_ENABLE_TESTER)

    #

    if (SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
      set(TESTER4_SRC tester4simd.cpp ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)

      string(CONCAT TARGET_TESTER4_${SIMD} "tester4" ${LCSIMD})

      add_executable(${TARGET_TESTER4_${SIMD}} ${TESTER4_SRC})
      target_compile_options(${TARGET_TESTER4_${SIMD}}
        PRIVATE ${FLAGS_ENABLE_${SIMD}})
      target_compile_definitions(${TARGET_TESTER4_${SIMD}}
        PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS})
      target_link_libraries(${TARGET_TESTER4_${SIMD}} ${TARGET_LIBSLEEF} ${TLFLOAT_LIBRARIES} ${TARGET_TESTERUTIL_OBJ})

      add_dependencies(${TARGET_TESTER4_${SIMD}} ${TARGET_HEADERS} ${TARGET_LIBSLEEF} ext_tlfloat)
      set_target_properties(${TARGET_TESTER4_${SIMD}} PROPERTIES ${COMMON_TARGET_PROPERTIES})
      if (DEFINED COSTOVERRIDE_${SIMD})
        add_test_with_emu(${COSTOVERRIDE_${SIMD}} ${TARGET_TESTER4_${SIMD}})
      else()
        add_test_with_emu(1.0 ${TARGET_TESTER4_${SIMD}})
      endif()

      #

      list(FIND SLEEF_SUPPORTED_INLINE_LIBM_EXTENSIONS ${SIMD} INDEX_INLINE)
      if (SLEEF_BUILD_INLINE_HEADERS AND NOT INDEX_INLINE EQUAL -1)
        string(CONCAT TARGET_TESTER4I_${SIMD} "tester4i" ${LCSIMD})

        add_executable(${TARGET_TESTER4I_${SIMD}} ${TESTER4_SRC})
        target_compile_options(${TARGET_TESTER4I_${SIMD}}
          PRIVATE ${FLAGS_ENABLE_${SIMD}})
        target_link_libraries(${TARGET_TESTER4I_${SIMD}} ${TLFLOAT_LIBRARIES} ${TARGET_TESTERUTIL_OBJ})
        if(CMAKE_C_COMPILER_ID MATCHES "GNU")
          target_compile_options(${TARGET_TESTER4I_${SIMD}} PRIVATE "-Wno-unknown-pragmas")
        endif()
        target_compile_definitions(${TARGET_TESTER4I_${SIMD}}
          PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS}
          USE_INLINE_HEADER="sleefinline_${LCSIMD}.h"
          MACRO_ONLY_HEADER="macroonly${SIMD}.h"
          SIMD_SUFFIX=_${LCSIMD}_sleef
          )
        target_include_directories(${TARGET_TESTER4I_${SIMD}} PRIVATE ${PROJECT_BINARY_DIR}/include)
        add_dependencies(${TARGET_TESTER4I_${SIMD}} ${TARGET_INLINE_HEADERS} ext_tlfloat)
        set_target_properties(${TARGET_TESTER4I_${SIMD}} PROPERTIES ${COMMON_TARGET_PROPERTIES})
        if (DEFINED COSTOVERRIDE_${SIMD})
          add_test_with_emu(${COSTOVERRIDE_${SIMD}} ${TARGET_TESTER4I_${SIMD}})
        else()
          add_test_with_emu(1.0 ${TARGET_TESTER4I_${SIMD}})
        endif()
      endif()
    endif(SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)

    #

    if(LIB_MPFR AND NOT MINGW)
      # Build tester2 SIMD
      string(TOLOWER ${SIMD} SCSIMD)
      foreach(P dp sp)
        set(T "tester2${SCSIMD}${P}")
        add_executable(${T} tester2simd${P}.c)
        target_compile_options(${T} PRIVATE ${FLAGS_ENABLE_${SIMD}})
        target_compile_definitions(${T} PRIVATE ENABLE_${SIMD}=1 USEMPFR=1 ${COMMON_TARGET_DEFINITIONS})
        set_target_properties(${T} PROPERTIES ${COMMON_TARGET_PROPERTIES})
        target_link_libraries(${T} ${TARGET_LIBSLEEF} ${TARGET_TESTERUTIL_OBJ} ${TLFLOAT_LIBRARIES} ${LIB_MPFR} ${LIBGMP} ${LIBM})
        add_dependencies(${T} ${TARGET_HEADERS})
        add_dependencies(${T} ${TARGET_LIBSLEEF})
        if (MPFR_INCLUDE_DIR)
          target_include_directories(${T} PRIVATE ${MPFR_INCLUDE_DIR})
        endif()
      endforeach()
    endif()

    # Build tester3
    string(TOLOWER ${SIMD} SCSIMD)
    set(T "tester3${SCSIMD}")
    add_executable(${T} tester3.c ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)
    target_compile_options(${T} PRIVATE ${FLAGS_ENABLE_${SIMD}})
    target_compile_definitions(${T} PRIVATE ${COMMON_TARGET_DEFINITIONS} ${TESTER3_DEFINITIONS_${SIMD}})
    if (NOT SLEEF_OPENSSL_FOUND)
      target_compile_definitions(${T} PRIVATE SLEEF_USE_INTERNAL_SHA256=1)
    endif()
    set_target_properties(${T} PROPERTIES ${COMMON_TARGET_PROPERTIES})

    target_link_libraries(${T} ${TARGET_LIBSLEEF} ${TARGET_TESTERUTIL_OBJ} ${TLFLOAT_LIBRARIES} ${LIBM})
    if(LIB_MPFR)
      target_link_libraries(${T} ${LIB_MPFR} ${LIBGMP})
    endif()
    if (SLEEF_OPENSSL_FOUND)
      target_link_libraries(${T} ${SLEEF_OPENSSL_LIBRARIES})
      target_include_directories(${T} PRIVATE ${SLEEF_OPENSSL_INCLUDE_DIR})
    else()
      target_link_libraries(${T} ${TARGET_PSHA_OBJ})
      target_include_directories(${T} PRIVATE ${sleef_SOURCE_DIR}/src/common)
    endif()

    add_dependencies(${T} ${TARGET_HEADERS})
    add_dependencies(${T} ${TARGET_LIBSLEEF})

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
  endif(COMPILER_SUPPORTS_${SIMD})
endmacro(test_extension)

foreach(SIMD ${SLEEF_SUPPORTED_LIBM_EXTENSIONS})
  test_extension(${SIMD})
endforeach()

#

if (SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
  # tester4dspscalar
  add_executable(tester4dspscalar ${TESTER4_SRC})
  target_compile_definitions(tester4dspscalar PRIVATE
    ENABLE_DSP_SCALAR=1 ${COMMON_TARGET_DEFINITIONS})
  target_compile_options(tester4dspscalar PRIVATE ${FLAGS_ENABLE_PUREC_SCALAR})
  target_link_libraries(tester4dspscalar ${TARGET_LIBSLEEF} ${TLFLOAT_LIBRARIES} ${TARGET_TESTERUTIL_OBJ})
  add_dependencies(tester4dspscalar ${TARGET_HEADERS} ${TARGET_LIBSLEEF} ext_tlfloat)
  add_test_with_emu(3.0 tester4dspscalar)
endif(SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)

if (SLEEF_ARCH_X86 AND COMPILER_SUPPORTS_SSE2)
  if (SLEEF_ENABLE_TESTER)
    # iutdsp128
    add_executable(iutdsp128 ${IUT_SRC})
    target_compile_definitions(iutdsp128 PRIVATE ENABLE_DSP128=1 ${COMMON_TARGET_DEFINITIONS})
    target_compile_options(iutdsp128 PRIVATE ${FLAGS_ENABLE_SSE2})
    target_link_libraries(iutdsp128 ${TARGET_LIBSLEEF} ${LIBRT} ${TARGET_TESTERUTIL_OBJ} ${TLFLOAT_LIBRARIES} ${LIBM})
    add_dependencies(iutdsp128 ${TARGET_HEADERS} ${TARGET_LIBSLEEF})
    add_test_iut(iutdsp128 1.0)
    list(APPEND IUT_LIST iutdsp128)
  endif(SLEEF_ENABLE_TESTER)

  if (SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
    # tester4dsp128
    add_executable(tester4dsp128 ${TESTER4_SRC})
    target_compile_definitions(tester4dsp128 PRIVATE
      ENABLE_DSP128=1 ${COMMON_TARGET_DEFINITIONS})
    target_compile_options(tester4dsp128 PRIVATE ${FLAGS_ENABLE_SSE2})
    target_link_libraries(tester4dsp128 ${TARGET_LIBSLEEF} ${TLFLOAT_LIBRARIES} ${TARGET_TESTERUTIL_OBJ})
    add_dependencies(tester4dsp128 ${TARGET_HEADERS} ${TARGET_LIBSLEEF} ext_tlfloat)
    add_test_with_emu(1.0 tester4dsp128)
  endif(SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
endif(SLEEF_ARCH_X86 AND COMPILER_SUPPORTS_SSE2)

if (SLEEF_ARCH_PPC64 AND COMPILER_SUPPORTS_VSX)
  if (SLEEF_ENABLE_TESTER)
    add_executable(iutdsp128 ${IUT_SRC})
    target_compile_definitions(iutdsp128 PRIVATE ENABLE_DSPPOWER_128=1 ${COMMON_TARGET_DEFINITIONS})
    target_compile_options(iutdsp128 PRIVATE ${FLAGS_ENABLE_VSX})
    target_link_libraries(iutdsp128 ${TARGET_LIBSLEEF} ${LIBRT} ${TARGET_TESTERUTIL_OBJ} ${TLFLOAT_LIBRARIES} ${LIBM})
    add_dependencies(iutdsp128 ${TARGET_HEADERS} ${TARGET_LIBSLEEF})
    add_test_iut(iutdsp128 1.0)
    list(APPEND IUT_LIST iutdsp128)
  endif(SLEEF_ENABLE_TESTER)

  if (SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
    add_executable(tester4dsp128 ${TESTER4_SRC})
    target_compile_definitions(tester4dsp128 PRIVATE ENABLE_DSPPOWER_128=1 ${COMMON_TARGET_DEFINITIONS})
    target_compile_options(tester4dsp128 PRIVATE ${FLAGS_ENABLE_VSX})
    target_link_libraries(tester4dsp128 ${TARGET_LIBSLEEF} ${TLFLOAT_LIBRARIES} ${TARGET_TESTERUTIL_OBJ})
    add_dependencies(tester4dsp128 ${TARGET_HEADERS} ${TARGET_LIBSLEEF} ext_tlfloat)
    add_test_with_emu(1.0 tester4dsp128)
  endif(SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
endif(SLEEF_ARCH_PPC64 AND COMPILER_SUPPORTS_VSX)

if (SLEEF_ARCH_S390X AND COMPILER_SUPPORTS_VXE)
  if (SLEEF_ENABLE_TESTER)
    add_executable(iutdsp128 ${IUT_SRC})
    target_compile_definitions(iutdsp128 PRIVATE ENABLE_DSPS390X_128=1 ${COMMON_TARGET_DEFINITIONS})
    target_compile_options(iutdsp128 PRIVATE ${FLAGS_ENABLE_VXE})
    target_link_libraries(iutdsp128 ${TARGET_LIBSLEEF} ${LIBRT} ${TARGET_TESTERUTIL_OBJ} ${TLFLOAT_LIBRARIES} ${LIBM})
    add_dependencies(iutdsp128 ${TARGET_HEADERS} ${TARGET_LIBSLEEF})
    add_test_iut(iutdsp128 1.0)
    list(APPEND IUT_LIST iutdsp128)
  endif(SLEEF_ENABLE_TESTER)

  if (SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
    add_executable(tester4dsp128 ${TESTER4_SRC})
    target_compile_definitions(tester4dsp128 PRIVATE ENABLE_DSPS390X_128=1 ${COMMON_TARGET_DEFINITIONS})
    target_compile_options(tester4dsp128 PRIVATE ${FLAGS_ENABLE_VXE})
    target_link_libraries(tester4dsp128 ${TARGET_LIBSLEEF} ${TLFLOAT_LIBRARIES} ${TARGET_TESTERUTIL_OBJ})
    add_dependencies(tester4dsp128 ${TARGET_HEADERS} ${TARGET_LIBSLEEF} ext_tlfloat)
    add_test_with_emu(1.0 tester4dsp128)
  endif(SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
endif(SLEEF_ARCH_S390X AND COMPILER_SUPPORTS_VXE)

if(LIB_MPFR AND NOT MINGW)
  # Compile executable 'tester'
  add_host_executable(${TARGET_TESTER} tester.c)
  if (NOT CMAKE_CROSSCOMPILING)
    target_link_libraries(${TARGET_TESTER} ${TARGET_LIBSLEEF} ${TARGET_TESTERUTIL_OBJ} ${LIB_MPFR} ${LIBGMP} ${TLFLOAT_LIBRARIES} ${LIBM})
    target_compile_definitions(${TARGET_TESTER}
      PRIVATE USEMPFR=1 ${COMMON_TARGET_DEFINITIONS})
    target_compile_options(${TARGET_TESTER} PRIVATE -Wno-unused-result)
    set_target_properties(${TARGET_TESTER} PROPERTIES ${COMMON_TARGET_PROPERTIES})
    if (MPFR_INCLUDE_DIR)
      target_include_directories(${TARGET_TESTER} PRIVATE ${MPFR_INCLUDE_DIR})
    endif()
  endif()
endif(LIB_MPFR AND NOT MINGW)

#

if (FILECHECK_COMMAND AND COMPILER_SUPPORTS_OPENMP AND SLEEF_ARCH_X86 AND CMAKE_C_COMPILER_ID MATCHES "GNU" AND CMAKE_C_COMPILER_VERSION VERSION_GREATER 7.99)
  add_test(NAME autovec-avx2 COMMAND sh -c "${CMAKE_C_COMPILER} -Wno-attributes -fopenmp -mavx2 -O3 ${CMAKE_CURRENT_SOURCE_DIR}/autovec.c -I${sleef_BINARY_DIR}/include -S -o- | ${FILECHECK_COMMAND} ${CMAKE_CURRENT_SOURCE_DIR}/autovec.c -check-prefix=CHECK-AVX2")
  add_test(NAME autovec-sse2 COMMAND sh -c "${CMAKE_C_COMPILER} -Wno-attributes -fopenmp -msse2 -O3 ${CMAKE_CURRENT_SOURCE_DIR}/autovec.c -I${sleef_BINARY_DIR}/include -S -o- | ${FILECHECK_COMMAND} ${CMAKE_CURRENT_SOURCE_DIR}/autovec.c -check-prefix=CHECK-SSE2")
  add_test(NAME testervecabi-sse2 COMMAND sh -c "${CMAKE_C_COMPILER} -Wno-attributes -fopenmp -msse2 -O3 ${CMAKE_CURRENT_SOURCE_DIR}/testervecabi.c -I${sleef_BINARY_DIR}/include -S -o- | ${FILECHECK_COMMAND} ${CMAKE_CURRENT_SOURCE_DIR}/testervecabi.c -check-prefix=CHECK-SSE2")
  add_test(NAME testervecabi-avx2 COMMAND sh -c "${CMAKE_C_COMPILER} -Wno-attributes -fopenmp -mavx2 -O3 ${CMAKE_CURRENT_SOURCE_DIR}/testervecabi.c -I${sleef_BINARY_DIR}/include -S -o- | ${FILECHECK_COMMAND} ${CMAKE_CURRENT_SOURCE_DIR}/testervecabi.c -check-prefix=CHECK-AVX2")
endif()
