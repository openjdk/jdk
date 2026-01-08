
# Settings

set(QUAD_HEADER_PARAMS_PUREC_SCALAR    1 Sleef_quad   Sleef_quad2    double      float       uint64_t   int32_t   int64_t   uint64_t   __STDC__          purec)
set(QUAD_HEADER_PARAMS_PURECFMA_SCALAR 1 Sleef_quad   Sleef_quad2    double      float       uint64_t   int32_t   int64_t   uint64_t   __STDC__          purecfma)
set(QUAD_HEADER_PARAMS_DSPSCALAR       1 Sleef_quad   Sleef_quad2    double      float       uint64_t   int32_t   int64_t   uint64_t   __STDC__)
set(QUAD_HEADER_PARAMS_SSE2            2 Sleef_quadx2 Sleef_quadx2_2 __m128d     __m128      __m128i    __m128i   __m128i   __m128i    __SSE2__          sse2)
set(QUAD_HEADER_PARAMS_AVX2128         2 Sleef_quadx2 Sleef_quadx2_2 __m128d     __m128      __m128i    __m128i   __m128i   __m128i    __SSE2__          avx2128)
set(QUAD_HEADER_PARAMS_DSPX2_X86       2 Sleef_quadx2 Sleef_quadx2_2 __m128d     __m128      __m128i    __m128i   __m128i   __m128i    __SSE2__)
set(QUAD_HEADER_PARAMS_AVX2            4 Sleef_quadx4 Sleef_quadx4_2 __m256d     __m256      __m256i    __m128i   __m256i   __m256i    __AVX__           avx2)
set(QUAD_HEADER_PARAMS_AVX512F         8 Sleef_quadx8 Sleef_quadx8_2 __m512d     __m512      __m512i    __m256i   __m512i   __m512i    __AVX512F__       avx512f)
set(QUAD_HEADER_PARAMS_ADVSIMD         2 Sleef_quadx2 Sleef_quadx2_2 float64x2_t float32x4_t uint32x4_t int32x2_t int64x2_t uint64x2_t __ARM_NEON        advsimd)
set(QUAD_HEADER_PARAMS_DSPX2_AARCH64   2 Sleef_quadx2 Sleef_quadx2_2 float64x2_t float32x4_t uint32x4_t int32x2_t int64x2_t uint64x2_t __ARM_NEON)
set(QUAD_HEADER_PARAMS_SVE             x Sleef_svquad Sleef_svquad_2 svfloat64_t svfloat32_t svint32_t  svint32_t svint64_t svuint64_t __ARM_FEATURE_SVE sve)
set(QUAD_HEADER_PARAMS_RVVM1           x Sleef_rvvm1quad Sleef_rvvm1quad_2 vfloat64m1_t vfloat32m1_t rvv_dp_mask vint32mf2_t vint64m1_t vuint64m1_t __riscv_vector rvvm1)
set(QUAD_HEADER_PARAMS_RVVM2           x Sleef_rvvm2quad Sleef_rvvm2quad_2 vfloat64m2_t vfloat32m2_t rvv_dp_mask vint32m1_t vint64m2_t vuint64m2_t __riscv_vector rvvm2)
set(QUAD_HEADER_PARAMS_VSX             2 Sleef_quadx2 Sleef_quadx2_2 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_FLOAT" "SLEEF_VECTOR_UINT" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_LONGLONG" "SLEEF_VECTOR_ULONGLONG" __VSX__ vsx)
set(QUAD_HEADER_PARAMS_VSX3            2 Sleef_quadx2 Sleef_quadx2_2 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_FLOAT" "SLEEF_VECTOR_UINT" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_LONGLONG" "SLEEF_VECTOR_ULONGLONG" __VSX__ vsx3)
set(QUAD_HEADER_PARAMS_DSPX2_PPC64     2 Sleef_quadx2 Sleef_quadx2_2 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_FLOAT" "SLEEF_VECTOR_UINT" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_LONGLONG" "SLEEF_VECTOR_ULONGLONG" __VSX__)
set(QUAD_HEADER_PARAMS_VXE             2 Sleef_quadx2 Sleef_quadx2_2 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_FLOAT" "SLEEF_VECTOR_ULONGLONG" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_LONGLONG" "SLEEF_VECTOR_ULONGLONG" __VEC__ vxe)
set(QUAD_HEADER_PARAMS_VXE2            2 Sleef_quadx2 Sleef_quadx2_2 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_FLOAT" "SLEEF_VECTOR_ULONGLONG" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_LONGLONG" "SLEEF_VECTOR_ULONGLONG" __VEC__ vxe2)
set(QUAD_HEADER_PARAMS_DSPX2_S390X     2 Sleef_quadx2 Sleef_quadx2_2 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_FLOAT" "SLEEF_VECTOR_ULONGLONG" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_LONGLONG" "SLEEF_VECTOR_ULONGLONG" __VEC__)

set(QUAD_RENAME_PARAMS_PUREC_SCALAR    1 purec)
set(QUAD_RENAME_PARAMS_PURECFMA_SCALAR 1 purecfma)
set(QUAD_RENAME_PARAMS_SSE2            2 sse2)
set(QUAD_RENAME_PARAMS_AVX2128         2 avx2128)
set(QUAD_RENAME_PARAMS_AVX2            4 avx2)
set(QUAD_RENAME_PARAMS_AVX512F         8 avx512f)
set(QUAD_RENAME_PARAMS_ADVSIMD         2 advsimd)
set(QUAD_RENAME_PARAMS_SVE             x sve)
set(QUAD_RENAME_PARAMS_RVVM1           x rvvm1)
set(QUAD_RENAME_PARAMS_RVVM2           x rvvm2)
set(QUAD_RENAME_PARAMS_VSX             2 vsx)
set(QUAD_RENAME_PARAMS_VSX3            2 vsx3)
set(QUAD_RENAME_PARAMS_VXE             2 vxe)
set(QUAD_RENAME_PARAMS_VXE2            2 vxe2)
set(QUAD_RENAME_PARAMS_CUDA            1 cuda)

if (SLEEF_ARCH_X86)
  set(SLEEF_HEADER_LIST
    SSE2
    AVX2128
    AVX2
    AVX512F
    PUREC_SCALAR
    PURECFMA_SCALAR
    DSPSCALAR
    DSPX2_X86
    )
  set(QMKDISP_PARAMS_X2 2 Sleef_quadx2 __m128d __m128i __m128i __m128i sse2 avx2128)
elseif(SLEEF_ARCH_AARCH64)
  set(SLEEF_HEADER_LIST
    ADVSIMD
    SVE
    PUREC_SCALAR
    PURECFMA_SCALAR
    DSPSCALAR
    DSPX2_AARCH64
    )
  set(QMKDISP_PARAMS_X2 2 Sleef_quadx2 float64x2_t int32x2_t int64x2_t uint64x2_t advsimd x)
elseif(SLEEF_ARCH_AARCH32)
  set(SLEEF_HEADER_LIST
    PUREC_SCALAR
    PURECFMA_SCALAR
    DSPSCALAR
    )
elseif(SLEEF_ARCH_PPC64)
  set(SLEEF_HEADER_LIST
    VSX
    VSX3
    PUREC_SCALAR
    PURECFMA_SCALAR
    DSPSCALAR
    DSPX2_PPC64
    )
  set(QMKDISP_PARAMS_X2 2 Sleef_quadx2 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_LONGLONG" "SLEEF_VECTOR_ULONGLONG" vsx vsx3)
elseif(SLEEF_ARCH_S390X)
  set(SLEEF_HEADER_LIST
    VXE
    VXE2
    PUREC_SCALAR
    PURECFMA_SCALAR
    DSPSCALAR
    DSPX2_S390X
    )
  set(QMKDISP_PARAMS_X2 2 Sleef_quadx2 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_LONGLONG" "SLEEF_VECTOR_ULONGLONG" vxe vxe2)
elseif(SLEEF_ARCH_RISCV64)
  set(SLEEF_HEADER_LIST
    RVVM1
    RVVM2
    PUREC_SCALAR
    PURECFMA_SCALAR
    DSPSCALAR
    )
endif()

#

file(MAKE_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}/include/)
include_directories(${CMAKE_CURRENT_BINARY_DIR}/include/)
include_directories(${CMAKE_CURRENT_SOURCE_DIR})
include_directories(${sleef_SOURCE_DIR}/src/common)

set(COMMON_TARGET_PROPERTIES C_STANDARD 99)

if(COMPILER_SUPPORTS_FLOAT128)
  list(APPEND COMMON_TARGET_DEFINITIONS ENABLEFLOAT128=1)
endif()

if(COMPILER_SUPPORTS_BUILTIN_MATH)
  list(APPEND COMMON_TARGET_DEFINITIONS ENABLE_BUILTIN_MATH=1)
endif()

if (BUILD_SHARED_LIBS)
  list(APPEND COMMON_TARGET_PROPERTIES POSITION_INDEPENDENT_CODE ON)
endif()

#

set(CMAKE_C_FLAGS "${ORG_CMAKE_C_FLAGS} ${SLEEF_C_FLAGS}")

# --------------------------------------------------------------------
# sleefquad.h
# --------------------------------------------------------------------
set(SLEEFQUAD_ORG_HEADER ${CMAKE_CURRENT_BINARY_DIR}/sleefquad_header.h.org)
set(SLEEFQUAD_ORG_FOOTER ${CMAKE_CURRENT_SOURCE_DIR}/sleefquad_footer.h.org)
set(SLEEFQUAD_INCLUDE_HEADER ${sleef_BINARY_DIR}/include/sleefquad.h)

configure_file(${CMAKE_CURRENT_SOURCE_DIR}/sleefquad_header.h.org.in ${SLEEFQUAD_ORG_HEADER})

set(SLEEF_HEADER_SIMD_SECTIONS "")
foreach(SIMD ${SLEEF_HEADER_LIST})
  set(SIMD_SECTION_FILE ${CMAKE_CURRENT_BINARY_DIR}/sleefquad_${SIMD}.h.tmp)
  list(APPEND SLEEF_HEADER_SIMD_SECTIONS ${SIMD_SECTION_FILE})
  add_custom_command(
    OUTPUT ${SIMD_SECTION_FILE}
    COMMAND $<TARGET_FILE:qmkrename> ${QUAD_HEADER_PARAMS_${SIMD}} > ${SIMD_SECTION_FILE}
    DEPENDS qmkrename
    )
endforeach()

file(MAKE_DIRECTORY ${sleef_BINARY_DIR}/include)
sleef_concat_files(
  OUTPUT ${SLEEFQUAD_INCLUDE_HEADER}
  SOURCES ${SLEEFQUAD_ORG_HEADER} ${SLEEF_HEADER_SIMD_SECTIONS} ${SLEEFQUAD_ORG_FOOTER}
)

# --------------------------------------------------------------------
# qmkrename
# qrenameXXX.h for each vector extension
# --------------------------------------------------------------------
# Helper executable: generates parts of the sleef header file
add_host_executable(qmkrename qmkrename.c)
set_target_properties(qmkrename PROPERTIES ${COMMON_TARGET_PROPERTIES})

set(HEADER_FILES_GENERATED "")
foreach(SIMD ${SLEEF_SUPPORTED_QUAD_EXTENSIONS})
  if(COMPILER_SUPPORTS_${SIMD})
    string(TOLOWER ${SIMD} SIMDLC)
    set(HEADER_${SIMD} ${CMAKE_CURRENT_BINARY_DIR}/include/qrename${SIMDLC}.h)
    list(APPEND HEADER_FILES_GENERATED ${HEADER_${SIMD}})

    # Generate qmkrename commands
    add_custom_command(OUTPUT ${HEADER_${SIMD}}
      COMMAND echo Generating qrename${vecarch}.h: qmkrename ${QUAD_RENAME_PARAMS_${SIMD}}
      COMMAND $<TARGET_FILE:qmkrename> ${QUAD_RENAME_PARAMS_${SIMD}} > ${HEADER_${SIMD}}
      DEPENDS qmkrename
    )
    add_custom_target(qrename${SIMD}.h_generated DEPENDS ${HEADER_${SIMD}})
  endif()
endforeach()

# Generate qrenamecuda.h

set(HEADER_CUDA ${CMAKE_CURRENT_BINARY_DIR}/include/qrenamecuda.h)
list(APPEND HEADER_FILES_GENERATED ${HEADER_CUDA})
add_custom_command(OUTPUT ${HEADER_CUDA}
  COMMAND echo Generating qrenamecuda.h: qmkrename ${QUAD_RENAME_PARAMS_CUDA}
  COMMAND $<TARGET_FILE:qmkrename> ${QUAD_RENAME_PARAMS_CUDA} > ${HEADER_CUDA}
  DEPENDS qmkrename
  )
add_custom_target(qrenameCUDA.h_generated DEPENDS ${HEADER_CUDA})

# --------------------------------------------------------------------
# sleefquad_headers
# --------------------------------------------------------------------
add_custom_target(sleefquad_headers ALL
  DEPENDS
    ${SLEEFQUAD_INCLUDE_HEADER}
    ${HEADER_FILES_GENERATED}
)

# --------------------------------------------------------------------
# libsleefquad
# --------------------------------------------------------------------

foreach(SIMD ${SLEEF_SUPPORTED_QUAD_EXTENSIONS})
  if(COMPILER_SUPPORTS_${SIMD})
    string(TOLOWER ${SIMD} SIMDLC)
    set(OBJECT "sleefquad${SIMDLC}_obj")
    add_library(${OBJECT} OBJECT sleefsimdqp.c ${HEADER_${SIMD}})

    if(COMPILER_SUPPORTS_BUILTIN_MATH)
      target_compile_definitions(${OBJECT} PRIVATE ENABLE_BUILTIN_MATH=1)
    endif()
    target_compile_definitions(${OBJECT} PRIVATE ENABLE_${SIMD}=1 DORENAME=1 ${COMMON_TARGET_DEFINITIONS})

    set_target_properties(${OBJECT} PROPERTIES ${COMMON_TARGET_PROPERTIES})
    add_dependencies(${OBJECT} qrename${SIMD}.h_generated)
    target_compile_options(${OBJECT} PRIVATE ${FLAGS_ENABLE_${SIMD}})

    list(APPEND SLEEFQUAD_OBJECTS $<TARGET_OBJECTS:${OBJECT}>)
  endif()
endforeach()

add_library(sleefquad rempitabqp.c ${SLEEFQUAD_OBJECTS})

set_target_properties(sleefquad PROPERTIES
  VERSION ${SLEEF_VERSION}
  SOVERSION ${SLEEF_SOVERSION}
  PUBLIC_HEADER ${SLEEFQUAD_INCLUDE_HEADER}
  ${COMMON_TARGET_PROPERTIES}
)

set_target_properties(sleefquad PROPERTIES ${COMMON_TARGET_PROPERTIES})

if(LIBM AND ((NOT COMPILER_SUPPORTS_BUILTIN_MATH) OR (SLEEF_ARCH_32BIT AND SLEEF_ARCH_X86)))
  target_link_libraries(sleefquad ${LIBM})
endif()

# --------------------------------------------------------------------
# Inline headers
# --------------------------------------------------------------------

if(SLEEF_BUILD_INLINE_HEADERS)
  if(CMAKE_C_COMPILER_ID MATCHES "Intel")
    message(FATAL_ERROR "SLEEF_BUILD_INLINE_HEADERS is not supported with Intel Compiler")
  endif()

  file(MAKE_DIRECTORY ${PROJECT_BINARY_DIR}/include/)
  set(INLINE_HEADER_FILES_GENERATED "")

  if (SED_COMMAND)
    foreach(SIMD ${SLEEF_SUPPORTED_QUAD_EXTENSIONS} CUDA)
      if(COMPILER_SUPPORTS_${SIMD} OR "${SIMD}" STREQUAL "CUDA")
        string(TOLOWER ${SIMD} SIMDLC)

        if(CMAKE_CROSSCOMPILING AND CMAKE_C_COMPILER_ID MATCHES "Clang" AND CMAKE_C_COMPILER_TARGET AND NOT "${SIMD}" STREQUAL "CUDA" )
          set(FLAG_TARGET --target=${CMAKE_C_COMPILER_TARGET})
        endif()

        if ("${SIMD}" STREQUAL "CUDA")
          set(TARGET_PP_FLAGS ${FLAG_DEFINE}SLEEF_ALWAYS_INLINE=__device__
                              ${FLAG_DEFINE}SLEEF_INLINE=__device__
                              ${FLAG_DEFINE}SLEEF_CONST=__device__
                              ${FLAG_DEFINE}static=__device__)
          set(INLINE_HEADER_ORG ${CMAKE_CURRENT_SOURCE_DIR}/sleefquadinline_cuda_header.h.org)
          # Remove redundant __device__
          set(TARGET_REPLACEMENTS -e "s/__device__ __device__/__device__/g" -e "s/__device__ __device__/__device__/g")
          set(TARGET_ADDSUFFIX_KEYWORDS double2 double3 float2)
        else()
          set(TARGET_ADDSUFFIX_KEYWORDS Sleef_rempitabqp)
          set(INLINE_HEADER_ORG ${CMAKE_CURRENT_SOURCE_DIR}/sleefquadinline_header.h.org)
        endif()


        set(INLINE_HEADER_FILE ${PROJECT_BINARY_DIR}/include/sleefquadinline_${SIMDLC}.h)
        add_custom_command(
          OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp1

          # Preprocess sleefsimddp.c with SLEEF_GENHEADER defined, comments are preserved
          COMMAND "${CMAKE_C_COMPILER}" ${FLAG_PREPROCESS} ${FLAG_PRESERVE_COMMENTS}                        # gcc -E -C
          ${FLAG_TARGET} ${FLAGS_ENABLE_${SIMD}}                                                            # -msse2
          ${FLAG_INCLUDE}${PROJECT_SOURCE_DIR}/src/common ${FLAG_INCLUDE}${PROJECT_SOURCE_DIR}/src/arch     # -I/sleef/src/common -I/sleef/src/arch
          ${FLAG_INCLUDE}${CMAKE_CURRENT_BINARY_DIR}/include/                                               # -I/build/src/quad/include
          ${FLAG_DEFINE}SLEEF_GENHEADER ${FLAG_DEFINE}ENABLE_${SIMD} ${FLAG_DEFINE}DORENAME                 # -DSLEEF_GENHEADER -DENABLE_SSE2 -DDORENAME
          ${TARGET_PP_FLAGS}
          ${CMAKE_CURRENT_SOURCE_DIR}/sleefsimdqp.c > ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp1      # /sleef/src/quad/sleefsimdqp.c > /build/quad/sleefSSE2.h.qtmp1

          # ${HEADER_${SIMD}} listed here as sleefsimdqp.c includes it for renaming
          DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/sleefsimdqp.c ${HEADER_${SIMD}}
          )

        add_custom_command(
          OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/include/qmacroonly${SIMD}.h

          # Remove all lines except those begin with "//@", then remove "//@"
          COMMAND ${SED_COMMAND} -e "/^\\/\\/@#.*$/!d" -e "s/^\\/\\/@#/#/g"
          ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp1
          > ${CMAKE_CURRENT_BINARY_DIR}/include/qmacroonly${SIMD}.h

          DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp1
          VERBATIM
          )

        add_custom_command(
          OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/sleefquad${SIMD}.h.c

          # Remove lines beginning with "#" so that the resulting file can be preprocessed again.
          COMMAND ${SED_COMMAND} -e "s/^#.*//g" ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp1            # sed -e "s/^#.*//g" /build/src/quad/sleefSSE2.h.qtmp1
          > ${CMAKE_CURRENT_BINARY_DIR}/sleefquad${SIMD}.h.c                                                # > /build/src/quad/sleefquadSSE2.h.c

          DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp1
          VERBATIM
          )

        add_custom_command(
          OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp2

          # Preprocess the intemediate file again to remove comments
          COMMAND "${CMAKE_C_COMPILER}" ${FLAG_PREPROCESS}                                                  # gcc -E
          ${CMAKE_CURRENT_BINARY_DIR}/sleefquad${SIMD}.h.c                                                  # /build/src/quad/sleefquadSSE2.h.c
          # Remove lines beginning with "#"
          | ${SED_COMMAND} -e "s/^#.*//g"
          > ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp2                                                # > /build/src/quad/sleefSSE2.h.qtmp2

          DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/sleefquad${SIMD}.h.c ${CMAKE_CURRENT_BINARY_DIR}/include/qmacroonly${SIMD}.h
          VERBATIM
          )

        add_custom_command(
          OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp4

          COMMAND ${CMAKE_COMMAND} -E cat
              ${INLINE_HEADER_ORG}
              ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp2
            | ${SED_COMMAND}
              # Embed version number into the header
              -e "s/SLEEF_VERSION_SLEEF/${SLEEF_VERSION_MAJOR}.${SLEEF_VERSION_MINOR}.${SLEEF_VERSION_PATCH}/g" # "s/SLEEF_VERSION_SLEEF/3.5.0/g"
              -e "s/SLEEF_SIMD_SLEEF/${SIMD}/g"                                                                 # -e "s/SLEEF_SIMD_SLEEF/SSE2/g"
              # Substitute "SLEEFSHARP" at the beginning of line with "#"
              -e "s/^SLEEFSHARP/#/g"
              # Remove SLEEFXXX
              -e "s/SLEEFXXX//g"
              # Replace multiple empty lines with a single empty line (part 1)
              -e "s/^[[:space:]]*$//g"
            > ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp4

          DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/sleefquadinline_header.h.org ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp2
          VERBATIM
          )

        add_custom_command(
          OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp8

          # Replace multiple empty lines with a single empty line
          # (part 2 - cannot occur in same command as part 1)
          COMMAND ${SED_COMMAND} -e "/^$/N" -e "/^\\n$/D" ${TARGET_REPLACEMENTS}
          ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp4
          > ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp8

          DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp4
          VERBATIM
          )

        add_custom_command(
          OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp9

          COMMAND $<TARGET_FILE:addSuffix> ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp8                 # addSuffix /build/src/quad/sleefSSE2.h.qtmp8
          ${sleef_SOURCE_DIR}/src/common/keywords.txt "_${SIMDLC}_sleefq"                                   # keywords.txt "_sse2_sleefq"
          ${TARGET_ADDSUFFIX_KEYWORDS}                                                                      # Sleef_rempitabdp Sleef_rempitabsp
          > ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp9

          DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp8 ${sleef_SOURCE_DIR} addSuffix
          )

        sleef_concat_files(
          OUTPUT ${INLINE_HEADER_FILE}
          SOURCES ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.qtmp9 ${CMAKE_CURRENT_SOURCE_DIR}/sleefquadinline_footer.h.org
          )

        list(APPEND INLINE_HEADER_FILES_GENERATED ${INLINE_HEADER_FILE})
      endif(COMPILER_SUPPORTS_${SIMD} OR "${SIMD}" STREQUAL "CUDA")
    endforeach()

    add_custom_target(${TARGET_QINLINE_HEADERS} ALL
      DEPENDS
      ${INLINE_HEADER_FILES_GENERATED}
      )
    install(
        FILES ${INLINE_HEADER_FILES_GENERATED}
        DESTINATION "${CMAKE_INSTALL_INCLUDEDIR}"
        COMPONENT sleef_Development
    )
  endif(SED_COMMAND)
endif(SLEEF_BUILD_INLINE_HEADERS)


# --------------------------------------------------------------------
# qmkdisp
# --------------------------------------------------------------------
add_host_executable(qmkdisp qmkdisp.c)
set_target_properties(qmkdisp PROPERTIES ${COMMON_TARGET_PROPERTIES})

# Target qdispscalar.c

add_custom_command(
  OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/qdispscalar.c.body
  COMMAND $<TARGET_FILE:qmkdisp> 1 Sleef_quad double int32_t int64_t uint64_t purec purecfma > ${CMAKE_CURRENT_BINARY_DIR}/qdispscalar.c.body
  DEPENDS qmkdisp
  )
sleef_concat_files(
  OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/qdispscalar.c
  SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/qdispscalar.c.org ${CMAKE_CURRENT_BINARY_DIR}/qdispscalar.c.body
  )

add_custom_target(qdispscalar.c_generated SOURCES ${CMAKE_CURRENT_BINARY_DIR}/qdispscalar.c)

# Target renamedspscalar.h

add_custom_command(OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/include/qrenamedspscalar.h
  COMMAND $<TARGET_FILE:qmkrename> 1 > ${CMAKE_CURRENT_BINARY_DIR}/include/qrenamedspscalar.h
  DEPENDS qmkrename
  )
add_custom_target(qrenamedspscalar.h_generated SOURCES ${CMAKE_CURRENT_BINARY_DIR}/include/qrenamedspscalar.h)

# Target qdispscalar_obj

add_library(qdispscalar_obj OBJECT qdispscalar.c)
set_target_properties(qdispscalar_obj PROPERTIES ${COMMON_TARGET_PROPERTIES})
target_compile_definitions(qdispscalar_obj PRIVATE ${COMMON_TARGET_DEFINITIONS})
target_include_directories(qdispscalar_obj PRIVATE ${sleef_BINARY_DIR}/include)
add_dependencies(qdispscalar_obj qdispscalar.c_generated qrenamedspscalar.h_generated
   sleefquad_headers ${TARGET_LIBSLEEF} ${TARGET_HEADERS})
target_sources(sleefquad PRIVATE $<TARGET_OBJECTS:qdispscalar_obj>)

if(DEFINED QMKDISP_PARAMS_X2)
  # Target qdispx2.c

  add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/qdispx2.c.body
    COMMAND $<TARGET_FILE:qmkdisp> ${QMKDISP_PARAMS_X2} > ${CMAKE_CURRENT_BINARY_DIR}/qdispx2.c.body
    DEPENDS qmkdisp
    )
  sleef_concat_files(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/qdispx2.c
    SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/qdispx2.c.org ${CMAKE_CURRENT_BINARY_DIR}/qdispx2.c.body
    )
  add_custom_target(qdispx2.c_generated SOURCES ${CMAKE_CURRENT_BINARY_DIR}/qdispx2.c)

  # Target renamedspx2.h

  add_custom_command(OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/include/qrenamedspx2.h
    COMMAND $<TARGET_FILE:qmkrename> 2 > ${CMAKE_CURRENT_BINARY_DIR}/include/qrenamedspx2.h
    DEPENDS qmkrename
    )
  add_custom_target(qrenamedspx2.h_generated SOURCES ${CMAKE_CURRENT_BINARY_DIR}/include/qrenamedspx2.h)

  # Target qdispx2_obj

  add_library(qdispx2_obj OBJECT qdispx2.c)

  if(SLEEF_ARCH_X86)
    if (COMPILER_SUPPORTS_AVX2)
      set(DISPATCHER_DEFINITIONS ${DISPATCHER_DEFINITIONS} ENABLE_AVX2=1)
    endif()
  endif()

  if(SLEEF_ARCH_PPC64)
    target_compile_options(qdispx2_obj PRIVATE ${FLAGS_ENABLE_VSX})
    if (COMPILER_SUPPORTS_VSX3)
      set(DISPATCHER_DEFINITIONS ${DISPATCHER_DEFINITIONS} ENABLE_VSX3=1)
    endif()
  endif()

  if(SLEEF_ARCH_S390X)
    target_compile_options(qdispx2_obj PRIVATE ${FLAGS_ENABLE_VXE})
    if (COMPILER_SUPPORTS_VXE2)
      set(DISPATCHER_DEFINITIONS ${DISPATCHER_DEFINITIONS} ENABLE_VXE2=1)
    endif()
  endif()

  set_target_properties(qdispx2_obj PROPERTIES ${COMMON_TARGET_PROPERTIES})
  target_compile_definitions(qdispx2_obj PRIVATE ${COMMON_TARGET_DEFINITIONS} ${DISPATCHER_DEFINITIONS})
  target_include_directories(qdispx2_obj PRIVATE ${sleef_BINARY_DIR}/include)
  add_dependencies(qdispx2_obj qdispx2.c_generated qrenamedspx2.h_generated
    sleefquad_headers ${TARGET_LIBSLEEF} ${TARGET_HEADERS})
  target_sources(sleefquad PRIVATE $<TARGET_OBJECTS:qdispx2_obj>)
endif(DEFINED QMKDISP_PARAMS_X2)

# --------------------------------------------------------------------
# Install
# --------------------------------------------------------------------
# Install libsleef and sleef.h
install(
    TARGETS sleefquad
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
