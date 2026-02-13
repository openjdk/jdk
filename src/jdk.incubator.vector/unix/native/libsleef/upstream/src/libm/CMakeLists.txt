
# Settings

if (SLEEF_ARCH_X86)
  set(SLEEF_HEADER_LIST
    SSE_
    SSE2
    AVX2
    AVX2128
    AVX512F_
    AVX512F
    PUREC_SCALAR
    PURECFMA_SCALAR
    DSP_SCALAR
  )
elseif(SLEEF_ARCH_AARCH64)
  set(SLEEF_HEADER_LIST
    ADVSIMD_
    ADVSIMD
    SVE
    PUREC_SCALAR
    PURECFMA_SCALAR
    DSP_SCALAR
  )
elseif(SLEEF_ARCH_PPC64)
  set(SLEEF_HEADER_LIST
    VSX_
    VSX
    VSX3
    PUREC_SCALAR
    PURECFMA_SCALAR
    DSP_SCALAR
  )
elseif(SLEEF_ARCH_S390X)
  set(SLEEF_HEADER_LIST
    VXE_
    VXE
    VXE2
    PUREC_SCALAR
    PURECFMA_SCALAR
    DSP_SCALAR
  )
elseif(SLEEF_ARCH_RISCV64)
  set(SLEEF_HEADER_LIST
    RVVM1
    RVVM2
    PUREC_SCALAR
    PURECFMA_SCALAR
    DSP_SCALAR
    )
else()
  set(SLEEF_HEADER_LIST
    PUREC_SCALAR
    PURECFMA_SCALAR
    DSP_SCALAR
    )
endif()

# HEADER_PARAMS

command_arguments(HEADER_PARAMS_SSE_            -     2 4 __m128d __m128 __m128i __m128i __SSE2__)
command_arguments(HEADER_PARAMS_SSE2            finz_ 2 4 __m128d __m128 __m128i __m128i __SSE2__ sse2)
command_arguments(HEADER_PARAMS_AVX2            finz_ 4 8 __m256d __m256 __m128i __m256i __AVX__ avx2)
command_arguments(HEADER_PARAMS_AVX2128         finz_ 2 4 __m128d __m128 __m128i __m128i __SSE2__ avx2128)
command_arguments(HEADER_PARAMS_AVX512F_        -     8 16 __m512d __m512 __m256i __m512i __AVX512F__)
command_arguments(HEADER_PARAMS_AVX512F         finz_ 8 16 __m512d __m512 __m256i __m512i __AVX512F__ avx512f)

command_arguments(HEADER_PARAMS_ADVSIMD_        -     2 4 float64x2_t float32x4_t int32x2_t int32x4_t __ARM_NEON)
command_arguments(HEADER_PARAMS_ADVSIMD         finz_ 2 4 float64x2_t float32x4_t int32x2_t int32x4_t __ARM_NEON advsimd)
command_arguments(HEADER_PARAMS_SVE             finz_ x x svfloat64_t svfloat32_t svint32_t svint32_t __ARM_FEATURE_SVE sve)

command_arguments(HEADER_PARAMS_VSX_            -     2 4 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_FLOAT" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_INT" __VSX__)
command_arguments(HEADER_PARAMS_VSX             finz_ 2 4 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_FLOAT" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_INT" __VSX__ vsx)
command_arguments(HEADER_PARAMS_VSX3            finz_ 2 4 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_FLOAT" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_INT" __VSX__ vsx3)

command_arguments(HEADER_PARAMS_VXE_            -     2 4 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_FLOAT" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_INT" __VEC__)
command_arguments(HEADER_PARAMS_VXE             finz_ 2 4 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_FLOAT" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_INT" __VEC__ vxe)
command_arguments(HEADER_PARAMS_VXE2            finz_ 2 4 "SLEEF_VECTOR_DOUBLE" "SLEEF_VECTOR_FLOAT" "SLEEF_VECTOR_INT" "SLEEF_VECTOR_INT" __VEC__ vxe2)

command_arguments(HEADER_PARAMS_RVVM1           finz_ x x vfloat64m1_t vfloat32m1_t vint32mf2_t vint32m1_t __riscv_v rvvm1)
command_arguments(HEADER_PARAMS_RVVM2           finz_ x x vfloat64m2_t vfloat32m2_t vint32m1_t vint32m2_t __riscv_v rvvm2)

command_arguments(HEADER_PARAMS_DSP_SCALAR      -     1 1 double float int32_t int32_t __STDC__)
command_arguments(HEADER_PARAMS_PUREC_SCALAR    finz_ 1 1 double float int32_t int32_t __STDC__ purec)
command_arguments(HEADER_PARAMS_PURECFMA_SCALAR finz_ 1 1 double float int32_t int32_t __STDC__ purecfma)

# RENAME_PARAMS

command_arguments(RENAME_PARAMS_SSE2            finz_ 2 4 sse2)
command_arguments(RENAME_PARAMS_AVX2            finz_ 4 8 avx2)
command_arguments(RENAME_PARAMS_AVX2128         finz_ 2 4 avx2128)
command_arguments(RENAME_PARAMS_AVX512F         finz_ 8 16 avx512f)
command_arguments(RENAME_PARAMS_ADVSIMD         finz_ 2 4 advsimd)
command_arguments(RENAME_PARAMS_VSX             finz_ 2 4 vsx)
command_arguments(RENAME_PARAMS_VSX3            finz_ 2 4 vsx3)
command_arguments(RENAME_PARAMS_VXE             finz_ 2 4 vxe)
command_arguments(RENAME_PARAMS_VXE2            finz_ 2 4 vxe2)
command_arguments(RENAME_PARAMS_PUREC_SCALAR    finz_ 1 1 purec)
command_arguments(RENAME_PARAMS_PURECFMA_SCALAR finz_ 1 1 purecfma)
command_arguments(RENAME_PARAMS_CUDA            finz_ 1 1 cuda)

# The vector length parameters in SVE, for SP and DP, are chosen for
# the smallest SVE vector size (128-bit). The name is generated using
# the "x" token of VLA SVE vector functions.
command_arguments(RENAME_PARAMS_SVE             finz_ x x sve)

command_arguments(RENAME_PARAMS_RVVM1           finz_ x x rvvm1)
command_arguments(RENAME_PARAMS_RVVM2           finz_ x x rvvm2)

# ALIAS_PARAMS

command_arguments(ALIAS_PARAMS_AVX2_DP   4 __m256d __m128i d avx2)
command_arguments(ALIAS_PARAMS_AVX2_SP  -8 __m256  __m256i d avx2)

command_arguments(ALIAS_PARAMS_AVX512F_DP   8 __m512d __m256i e avx512f)
command_arguments(ALIAS_PARAMS_AVX512F_SP -16 __m512  __m512i e avx512f)

command_arguments(ALIAS_PARAMS_ADVSIMD_DP  2 float64x2_t int32x2_t n advsimd)
command_arguments(ALIAS_PARAMS_ADVSIMD_SP -4 float32x4_t int32x4_t n advsimd)

set(EXT_ENABLE_ALIAS AVX2 AVX512F ADVSIMD)

#

file(MAKE_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}/include/)
include_directories(${CMAKE_CURRENT_BINARY_DIR}/include/)
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

set(CMAKE_C_FLAGS "${ORG_CMAKE_C_FLAGS} ${SLEEF_C_FLAGS}")
set(CMAKE_CXX_FLAGS "${ORG_CMAKE_C_FLAGS} ${SLEEF_C_FLAGS}")

# --------------------------------------------------------------------
# sleef.h
# --------------------------------------------------------------------
# File generated for the headers
set(SLEEF_ORG_HEADER ${CMAKE_CURRENT_BINARY_DIR}/sleeflibm_header.h.org)
set(SLEEF_ORG_FOOTER ${CMAKE_CURRENT_SOURCE_DIR}/sleeflibm_footer.h.org)
set(SLEEF_INCLUDE_HEADER ${sleef_BINARY_DIR}/include/sleef.h)

configure_file(${CMAKE_CURRENT_SOURCE_DIR}/sleeflibm_header.h.org.in ${SLEEF_ORG_HEADER})

set(SLEEF_HEADER_SIMD_SECTIONS "")
foreach(SIMD ${SLEEF_HEADER_LIST})
  set(SIMD_SECTION_FILE ${CMAKE_CURRENT_BINARY_DIR}/sleeflibm_${SIMD}.h.tmp)
  list(APPEND SLEEF_HEADER_SIMD_SECTIONS ${SIMD_SECTION_FILE})
  add_custom_command(
    OUTPUT ${SIMD_SECTION_FILE}
    COMMAND $<TARGET_FILE:${TARGET_MKRENAME}> ${HEADER_PARAMS_${SIMD}} > ${SIMD_SECTION_FILE}
    DEPENDS ${TARGET_MKRENAME}
  )
endforeach()

file(MAKE_DIRECTORY ${sleef_BINARY_DIR}/include)
sleef_concat_files(
  OUTPUT ${SLEEF_INCLUDE_HEADER}
  SOURCES ${SLEEF_ORG_HEADER} ${SLEEF_HEADER_SIMD_SECTIONS} ${SLEEF_ORG_FOOTER}
)

# --------------------------------------------------------------------
# TARGET_MKRENAME
# renameXXX.h for each SIMD
# --------------------------------------------------------------------
# Helper executable: generates parts of the sleef header file
add_host_executable(${TARGET_MKRENAME} mkrename.c)

set(HEADER_FILES_GENERATED "")
foreach(SIMD ${SLEEF_SUPPORTED_LIBM_EXTENSIONS})
  if(COMPILER_SUPPORTS_${SIMD})
    # Need lowercase string for rename header
    string(TOLOWER ${SIMD} vecarch)
    set(OBJECT_${SIMD} "sleef${vecarch}")
    set(HEADER_${SIMD} ${CMAKE_CURRENT_BINARY_DIR}/include/rename${vecarch}.h)
    list(APPEND HEADER_FILES_GENERATED ${HEADER_${SIMD}})

    # Generate mkrename commands
    add_custom_command(OUTPUT ${HEADER_${SIMD}}
      COMMAND echo Generating rename${vecarch}.h: ${TARGET_MKRENAME} ${RENAME_PARAMS_${SIMD}}
      COMMAND $<TARGET_FILE:${TARGET_MKRENAME}> ${RENAME_PARAMS_${SIMD}} > ${HEADER_${SIMD}}
      DEPENDS ${TARGET_MKRENAME}
    )
    add_custom_target(rename${SIMD}.h_generated DEPENDS ${HEADER_${SIMD}})
  endif()
endforeach()

# Generate renamecuda.h

set(HEADER_CUDA ${CMAKE_CURRENT_BINARY_DIR}/include/renamecuda.h)
list(APPEND HEADER_FILES_GENERATED ${HEADER_CUDA})
add_custom_command(OUTPUT ${HEADER_CUDA}
  COMMAND echo Generating renamecuda.h: ${TARGET_MKRENAME} ${RENAME_PARAMS_CUDA}
  COMMAND $<TARGET_FILE:${TARGET_MKRENAME}> ${RENAME_PARAMS_CUDA} > ${HEADER_CUDA}
  DEPENDS ${TARGET_MKRENAME}
  )
add_custom_target(renameCUDA.h_generated DEPENDS ${HEADER_CUDA})

# --------------------------------------------------------------------
# TARGET_HEADERS
# --------------------------------------------------------------------
add_custom_target(${TARGET_HEADERS} ALL
  DEPENDS
    ${SLEEF_INCLUDE_HEADER}            # Output only
    ${HEADER_FILES_GENERATED}          # Output only
)

# --------------------------------------------------------------------
# TARGET_MKALIAS
# --------------------------------------------------------------------
add_host_executable(${TARGET_MKALIAS} mkalias.c)

# --------------------------------------------------------------------
# TARGET_MKDISP
# --------------------------------------------------------------------
# Helper executable: dispatcher for the vector extensions
add_host_executable(${TARGET_MKDISP} mkdisp.c)

# Set C standard requirement (-std=gnu99 for gcc)
set_target_properties(
  ${TARGET_MKRENAME} ${TARGET_MKDISP} ${TARGET_MKALIAS}
  PROPERTIES C_STANDARD 99
)

# --------------------------------------------------------------------
# TARGET_LIBSLEEF
# --------------------------------------------------------------------
# Build main library

set(COMMON_TARGET_PROPERTIES
  C_STANDARD 99                  # -std=gnu99
  )

if (BUILD_SHARED_LIBS)
  list(APPEND COMMON_TARGET_PROPERTIES POSITION_INDEPENDENT_CODE ON)   # -fPIC
endif()

if (SLEEF_ENABLE_LTO)
  list(APPEND COMMON_TARGET_PROPERTIES INTERPROCEDURAL_OPTIMIZATION TRUE)  # -flto
endif()

# Original sleef sources
set(STANDARD_SOURCES rempitab.c)

add_library(${TARGET_LIBSLEEF} ${STANDARD_SOURCES})
target_link_libraries(${TARGET_LIBSLEEF} ${TARGET_COMMONXX_OBJ} ${TLFLOAT_LIBRARIES})
add_dependencies(${TARGET_LIBSLEEF} ${TARGET_HEADERS} ext_tlfloat)
set_target_properties(${TARGET_LIBSLEEF} PROPERTIES
  VERSION ${SLEEF_VERSION}
  SOVERSION ${SLEEF_SOVERSION}
  PUBLIC_HEADER ${SLEEF_INCLUDE_HEADER}
  ${COMMON_TARGET_PROPERTIES}
)

target_compile_definitions(${TARGET_LIBSLEEF}
  PRIVATE DORENAME=1 ${COMMON_TARGET_DEFINITIONS}
)

if(COMPILER_SUPPORTS_FLOAT128)
  target_compile_definitions(${TARGET_LIBSLEEF}
    PRIVATE ENABLEFLOAT128=1 ${COMMON_TARGET_DEFINITIONS})
endif()

if(COMPILER_SUPPORTS_BUILTIN_MATH)
  target_compile_definitions(${TARGET_LIBSLEEF} PRIVATE ENABLE_BUILTIN_MATH=1)
endif()

# Compile SIMD versions
# Single precision and double precision

# Include symbols for each SIMD architecture (if supported by the platform)
# Note: adds object file as sources via cmake conditional generator expression
foreach(SIMD ${SLEEF_SUPPORTED_LIBM_EXTENSIONS})
  if(COMPILER_SUPPORTS_${SIMD})
    list(FIND EXT_ENABLE_ALIAS ${SIMD} INDEX_ALIAS)
    string(TOLOWER ${SIMD} SIMDLC)

    set(SIMD_SOURCES sleefsimdsp.c sleefsimddp.c)

    # Create a library
    add_library(${OBJECT_${SIMD}} OBJECT ${SIMD_SOURCES} ${HEADER_${SIMD}})

    if(COMPILER_SUPPORTS_BUILTIN_MATH)
      target_compile_definitions(${OBJECT_${SIMD}} PRIVATE ENABLE_BUILTIN_MATH=1)
    endif()

    if(SLEEF_ENABLE_ALTDIV)
      target_compile_definitions(${OBJECT_${SIMD}} PRIVATE SLEEF_ENABLE_ALTDIV=1)
    endif()

    if(SLEEF_ENABLE_ALTSQRT)
      target_compile_definitions(${OBJECT_${SIMD}} PRIVATE SLEEF_ENABLE_ALTSQRT=1)
    endif()

    if (INDEX_ALIAS EQUAL -1)
      target_compile_definitions(${OBJECT_${SIMD}} PRIVATE
        ENABLE_${SIMD}=1
        DORENAME=1
        ${COMMON_TARGET_DEFINITIONS}
        )
    else()
      add_custom_command(
        OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/alias_${SIMD}_dp.h.tmp
        COMMAND $<TARGET_FILE:${TARGET_MKALIAS}> ${ALIAS_PARAMS_${SIMD}_DP} > ${CMAKE_CURRENT_BINARY_DIR}/alias_${SIMD}_dp.h.tmp
        DEPENDS ${TARGET_MKALIAS}
        )
      add_custom_command(
        OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/alias_${SIMD}_sp.h.tmp
        COMMAND $<TARGET_FILE:${TARGET_MKALIAS}> ${ALIAS_PARAMS_${SIMD}_SP} > ${CMAKE_CURRENT_BINARY_DIR}/alias_${SIMD}_sp.h.tmp
        DEPENDS ${TARGET_MKALIAS}
        )
      sleef_concat_files(
        OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/include/alias_${SIMDLC}.h
        SOURCES ${CMAKE_CURRENT_BINARY_DIR}/alias_${SIMD}_sp.h.tmp ${CMAKE_CURRENT_BINARY_DIR}/alias_${SIMD}_dp.h.tmp
               )
      add_custom_target(alias_${SIMDLC}.h_generated SOURCES ${CMAKE_CURRENT_BINARY_DIR}/include/alias_${SIMDLC}.h)
      add_dependencies(${OBJECT_${SIMD}} alias_${SIMDLC}.h_generated)
      target_compile_definitions(${OBJECT_${SIMD}} PRIVATE
        ENABLE_${SIMD}=1
        DORENAME=1
        ${COMMON_TARGET_DEFINITIONS}
        ALIAS_NO_EXT_SUFFIX=\"alias_${SIMDLC}.h\"
        )
    endif()

    add_dependencies(${OBJECT_${SIMD}} rename${SIMD}.h_generated ext_tlfloat)

    set_target_properties(${OBJECT_${SIMD}} PROPERTIES
      ${COMMON_TARGET_PROPERTIES}
      )

    target_compile_options(${OBJECT_${SIMD}} PRIVATE
      ${FLAGS_ENABLE_${SIMD}})

    target_sources(${TARGET_LIBSLEEF} PRIVATE $<TARGET_OBJECTS:${OBJECT_${SIMD}}>)
  endif(COMPILER_SUPPORTS_${SIMD})
endforeach()

#

if(SLEEF_BUILD_INLINE_HEADERS)
  file(MAKE_DIRECTORY ${PROJECT_BINARY_DIR}/include/)
  set(INLINE_HEADER_FILES_GENERATED "")

  configure_file(${CMAKE_CURRENT_SOURCE_DIR}/sleefinline_header.h.org.in ${CMAKE_CURRENT_BINARY_DIR}/sleefinline_header.h.org)

  if (SED_COMMAND)
    foreach(SIMD ${SLEEF_SUPPORTED_INLINE_LIBM_EXTENSIONS} CUDA)
      if(COMPILER_SUPPORTS_${SIMD} OR ${SIMD} STREQUAL "CUDA")
        string(TOLOWER ${SIMD} SIMDLC)

        if(CMAKE_CROSSCOMPILING AND CMAKE_C_COMPILER_ID MATCHES "Clang" AND CMAKE_C_COMPILER_TARGET AND NOT "${SIMD}" STREQUAL "CUDA" )
          set(FLAG_TARGET --target=${CMAKE_C_COMPILER_TARGET})
        endif()

        if ("${SIMD}" STREQUAL "CUDA")
          set(TARGET_PP_FLAGS ${FLAG_DEFINE}SLEEF_ALWAYS_INLINE=__device__
                              ${FLAG_DEFINE}SLEEF_INLINE=__device__
                              ${FLAG_DEFINE}SLEEF_CONST=__device__
                              ${FLAG_DEFINE}static=__device__)
          set(INLINE_HEADER_ORG ${CMAKE_CURRENT_SOURCE_DIR}/sleefinline_cuda_header.h.org)
          # Remove redundant __device__
          set(TARGET_REPLACEMENTS -e "s/__device__ __device__/__device__/g" -e "s/__device__ __device__/__device__/g")
          set(TARGET_ADDSUFFIX_KEYWORDS)
        else()
          set(TARGET_ADDSUFFIX_KEYWORDS Sleef_rempitabdp Sleef_rempitabsp)
          set(INLINE_HEADER_ORG ${CMAKE_CURRENT_BINARY_DIR}/sleefinline_header.h.org)
        endif()

        set(INLINE_HEADER_FILE ${PROJECT_BINARY_DIR}/include/sleefinline_${SIMDLC}.h)
        add_custom_command(
          OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/sleefsimddp${SIMD}.h.tmp

          # Preprocess sleefsimddp.c with SLEEF_GENHEADER defined, comments are preserved
          COMMAND "${CMAKE_C_COMPILER}"  ${FLAG_PREPROCESS} ${FLAG_PRESERVE_COMMENTS}                      # gcc -E -C
          ${FLAG_TARGET} ${FLAGS_ENABLE_${SIMD}}                                                           # -msse2
          ${FLAG_INCLUDE}${PROJECT_SOURCE_DIR}/src/common ${FLAG_INCLUDE}${PROJECT_SOURCE_DIR}/src/arch    # -I/sleef/src/common -I/sleef/src/arch
          ${FLAG_INCLUDE}${CMAKE_CURRENT_BINARY_DIR}/include/                                              # -I/build/src/libm/include
          ${FLAG_DEFINE}SLEEF_GENHEADER ${FLAG_DEFINE}ENABLE_${SIMD} ${FLAG_DEFINE}DORENAME                # -DSLEEF_GENHEADER -DENABLE_SSE2 -DDORENAME
          ${TARGET_PP_FLAGS}
          ${CMAKE_CURRENT_SOURCE_DIR}/sleefsimddp.c > ${CMAKE_CURRENT_BINARY_DIR}/sleefsimddp${SIMD}.h.tmp # /sleef/src/libm/sleefsimddp.c > /build/libm/sleefsimddpSSE2.h.tmp

          # ${HEADER_${SIMD}} listed here as sleefsimddp.c includes it for renaming
          DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/sleefsimddp.c ${HEADER_${SIMD}}
          )

        add_custom_command(
          OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/include/macroonly${SIMD}.h

          # Remove all lines except those begin with "//@", then remove "//@"
          COMMAND ${SED_COMMAND} -e "/^\\/\\/@#.*$/!d"
                                 -e "s/^\\/\\/@#/#/g"
            ${CMAKE_CURRENT_BINARY_DIR}/sleefsimddp${SIMD}.h.tmp
          > ${CMAKE_CURRENT_BINARY_DIR}/include/macroonly${SIMD}.h

          DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/sleefsimddp${SIMD}.h.tmp
          VERBATIM
          )

        add_custom_command(
          OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/sleefsimdsp${SIMD}.h.tmp

          # Preprocess sleefsimdsp.c with SLEEF_GENHEADER defined. Include macroonly*.h instead of helper*.h.
          COMMAND "${CMAKE_C_COMPILER}" ${FLAG_PREPROCESS} ${FLAG_PRESERVE_COMMENTS}                       # gcc -E -C
          ${FLAG_TARGET} ${FLAGS_ENABLE_${SIMD}}                                                           # -msse2
          ${FLAG_INCLUDE}${PROJECT_SOURCE_DIR}/src/common ${FLAG_INCLUDE}${PROJECT_SOURCE_DIR}/src/arch    # -I/sleef/src/common -I/sleef/src/arch
          ${FLAG_INCLUDE}${CMAKE_CURRENT_BINARY_DIR}/include/                                              # -I/build/src/libm/include
          ${FLAG_DEFINE}SLEEF_GENHEADER ${FLAG_DEFINE}ENABLE_${SIMD} ${FLAG_DEFINE}DORENAME                # -DSLEEF_GENHEADER -DENABLE_SSE2 -DDORENAME
          ${TARGET_PP_FLAGS}
          ${CMAKE_CURRENT_SOURCE_DIR}/sleefsimdsp.c > ${CMAKE_CURRENT_BINARY_DIR}/sleefsimdsp${SIMD}.h.tmp # /sleef/src/libm/sleefsimdsp.c > /build/libm/sleefsimdspSSE2.h.tmp

          DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/include/macroonly${SIMD}.h ${CMAKE_CURRENT_SOURCE_DIR}/sleefsimdsp.c
          )

        add_custom_command(
          OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.c

          # Remove lines beginning with "#" so that the resulting file can be preprocessed again.
          COMMAND ${CMAKE_COMMAND} -E cat ${CMAKE_CURRENT_BINARY_DIR}/sleefsimddp${SIMD}.h.tmp
                                          ${CMAKE_CURRENT_BINARY_DIR}/sleefsimdsp${SIMD}.h.tmp
                | ${SED_COMMAND} -e "s/^#.*//g" > ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.c

          DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/sleefsimdsp${SIMD}.h.tmp ${CMAKE_CURRENT_BINARY_DIR}/sleefsimddp${SIMD}.h.tmp
          VERBATIM
          )

        add_custom_command(
          OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.tmp3

          # Preprocess the intemediate file again to remove comments
          COMMAND "${CMAKE_C_COMPILER}" ${FLAG_PREPROCESS} ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.c
           # Remove lines beginning with "#"
           | ${SED_COMMAND} -e "s/^#.*//g"
           > ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.tmp3

          DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.c
          VERBATIM
          )

        add_custom_command(
          OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.tmp4

          COMMAND ${CMAKE_COMMAND} -E cat
              ${INLINE_HEADER_ORG}
              ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.tmp3
            | ${SED_COMMAND}
              # Embed version number into the header
              -e "s/SLEEF_VERSION_SLEEF/${SLEEF_VERSION_MAJOR}.${SLEEF_VERSION_MINOR}.${SLEEF_VERSION_PATCH}/g"
              -e "s/SLEEF_SIMD_SLEEF/${SIMD}/g"
              # Substitute "SLEEFSHARP" at the beginning of line with "#"
              -e "s/^SLEEFSHARP/#/g"
              # Remove SLEEFXXX
              -e "s/SLEEFXXX//g"
              # Replace multiple empty lines with a single empty line (part 1)
              -e "s/^[[:space:]]*$//g"
              > ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.tmp4

            DEPENDS ${INLINE_HEADER_ORG} ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.tmp3
            VERBATIM
         )

         add_custom_command(
           OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.tmp5

           # Replace multiple empty lines with a single empty line
           # (part 2 - cannot occur in same command as part 1)
           COMMAND ${SED_COMMAND} -e "/^$/N" -e "/^\\n$/D" ${TARGET_REPLACEMENTS} ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.tmp4
           > ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.tmp5

           DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.tmp4
           VERBATIM
           )

        add_custom_command(
          OUTPUT ${INLINE_HEADER_FILE}

          COMMAND $<TARGET_FILE:addSuffix> ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.tmp5                 # addSuffix /build/src/libm/sleefSSE2.h.tmp5
          ${sleef_SOURCE_DIR}/src/common/keywords.txt "_${SIMDLC}_sleef"                                   # keywords.txt "_sse2_sleef"
          ${TARGET_ADDSUFFIX_KEYWORDS}
          > ${PROJECT_BINARY_DIR}/include/sleefinline_${SIMDLC}.h                                          # > /build/include/sleefinline_sse2.h

          DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/sleef${SIMD}.h.tmp5 addSuffix
          VERBATIM
          )

        list(APPEND INLINE_HEADER_FILES_GENERATED ${INLINE_HEADER_FILE})
      endif(COMPILER_SUPPORTS_${SIMD} OR ${SIMD} STREQUAL "CUDA")
    endforeach()

    add_custom_target(${TARGET_INLINE_HEADERS}_util ALL
      DEPENDS
      ${INLINE_HEADER_FILES_GENERATED}
      )

    add_library("${TARGET_INLINE_HEADERS}" INTERFACE)
    add_dependencies("${TARGET_INLINE_HEADERS}" "${TARGET_INLINE_HEADERS}_util")
    target_include_directories(
        "${TARGET_INLINE_HEADERS}"
        INTERFACE
        "$<BUILD_INTERFACE:${PROJECT_BINARY_DIR}/include>"
    )

    # CMake 3.4 can't have PUBLIC_HEADER property on INTERFACE libraries
    install(
        FILES ${INLINE_HEADER_FILES_GENERATED}
        DESTINATION "${CMAKE_INSTALL_INCLUDEDIR}"
        COMPONENT sleef_Development
    )

    install(
        TARGETS "${TARGET_INLINE_HEADERS}"
        EXPORT sleefTargets
        INCLUDES DESTINATION "${CMAKE_INSTALL_INCLUDEDIR}"
    )
  endif(SED_COMMAND)
endif(SLEEF_BUILD_INLINE_HEADERS)

# On some systems we need to explicitly link libsleef against libm to
# use some of the math functions used in the scalar code (for example
# sqrt).
if(LIBM AND NOT COMPILER_SUPPORTS_BUILTIN_MATH)
  target_link_libraries(${TARGET_LIBSLEEF} ${LIBM})
endif()

target_sources(${TARGET_LIBSLEEF} PRIVATE $<TARGET_OBJECTS:${TARGET_LIBCOMMON_OBJ}>)

# --------------------------------------------------------------------

# Target dispscalar.c

add_custom_command(
  OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/dispscalar.c.body
  COMMAND $<TARGET_FILE:${TARGET_MKDISP}> 1 1 double float int32_t purec purecfma x > ${CMAKE_CURRENT_BINARY_DIR}/dispscalar.c.body
  DEPENDS ${TARGET_MKDISP}
  )

sleef_concat_files(
  OUTPUT  ${CMAKE_CURRENT_BINARY_DIR}/dispscalar.c
  SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/dispscalar.c.org
          ${CMAKE_CURRENT_BINARY_DIR}/dispscalar.c.body
          ${CMAKE_CURRENT_SOURCE_DIR}/dispscalar_footer.c.org
)
add_custom_target(dispscalar.c_generated SOURCES ${CMAKE_CURRENT_BINARY_DIR}/dispscalar.c)

# Target renamedspscalar.h

add_custom_command(OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/include/renamedspscalar.h
  COMMAND $<TARGET_FILE:${TARGET_MKRENAME}> - 1 1 > ${CMAKE_CURRENT_BINARY_DIR}/include/renamedspscalar.h
  DEPENDS ${TARGET_MKRENAME}
  )
add_custom_target(renamedspscalar.h_generated SOURCES ${CMAKE_CURRENT_BINARY_DIR}/include/renamedspscalar.h)

# Target dispscalar_obj

add_library(dispscalar_obj OBJECT dispscalar.c)
set_target_properties(dispscalar_obj PROPERTIES ${COMMON_TARGET_PROPERTIES})
if (COMPILER_SUPPORTS_PURECFMA_SCALAR)
  target_compile_definitions(dispscalar_obj PRIVATE ${COMMON_TARGET_DEFINITIONS} ENABLE_PURECFMA=1)
else()
  target_compile_definitions(dispscalar_obj PRIVATE ${COMMON_TARGET_DEFINITIONS})
endif()
target_include_directories(dispscalar_obj PRIVATE ${sleef_BINARY_DIR}/include)
add_dependencies(dispscalar_obj dispscalar.c_generated renamedspscalar.h_generated ${TARGET_HEADERS})
target_sources(${TARGET_LIBSLEEF} PRIVATE $<TARGET_OBJECTS:dispscalar_obj>)

#

function(sleef_generate_disp_simd simd)
  add_custom_command(OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/disp${simd}.c.tmp
    COMMAND $<TARGET_FILE:${TARGET_MKDISP}> "${ARGN}" > ${CMAKE_CURRENT_BINARY_DIR}/disp${simd}.c.tmp
    DEPENDS ${TARGET_MKDISP}
    COMMAND_EXPAND_LISTS
    )
  sleef_concat_files(OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/disp${simd}.c
    SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/disp${simd}.c.org ${CMAKE_CURRENT_BINARY_DIR}/disp${simd}.c.tmp
    )
  add_custom_target(disp${simd}.c_generated SOURCES ${CMAKE_CURRENT_BINARY_DIR}/disp${simd}.c)
endfunction()

function(sleef_generate_rename_simd simd)
  add_custom_command(OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/include/renamed${simd}.h
    COMMAND $<TARGET_FILE:${TARGET_MKRENAME}> "${ARGN}" > ${CMAKE_CURRENT_BINARY_DIR}/include/renamed${simd}.h
    DEPENDS ${TARGET_MKRENAME}
    COMMAND_EXPAND_LISTS
    )
  add_custom_target(renamed${simd}.h_generated SOURCES ${CMAKE_CURRENT_BINARY_DIR}/include/renamed${simd}.h)
  endfunction()

if (SLEEF_ARCH_X86 AND COMPILER_SUPPORTS_SSE2)
  # Target dispsse.c
  sleef_generate_disp_simd(sse 2 4 __m128d __m128 __m128i sse2 avx2128 x)

  # Target renamedsp128.h
  sleef_generate_rename_simd(sp128 - 2 4)

  # Target dispsse_obj
  if (COMPILER_SUPPORTS_AVX2)
    set(DISPATCHER_DEFINITIONS ${DISPATCHER_DEFINITIONS} ENABLE_AVX2=1)
  endif()

  if(COMPILER_SUPPORTS_SSE2)
    add_library(dispsse_obj OBJECT dispsse.c)
    target_compile_options(dispsse_obj PRIVATE ${FLAGS_ENABLE_SSE2})
    set_target_properties(dispsse_obj PROPERTIES ${COMMON_TARGET_PROPERTIES})
    target_compile_definitions(dispsse_obj PRIVATE ${COMMON_TARGET_DEFINITIONS} ${DISPATCHER_DEFINITIONS})
    target_include_directories(dispsse_obj PRIVATE ${sleef_BINARY_DIR}/include)
    add_dependencies(dispsse_obj dispsse.c_generated renamedsp128.h_generated ${TARGET_HEADERS})
    target_sources(${TARGET_LIBSLEEF} PRIVATE $<TARGET_OBJECTS:dispsse_obj>)
  endif()
endif(SLEEF_ARCH_X86 AND COMPILER_SUPPORTS_SSE2)

if (SLEEF_ARCH_PPC64 AND COMPILER_SUPPORTS_VSX)
  # Target disppower_128.c
  sleef_generate_disp_simd(power_128 2 4 SLEEF_VECTOR_DOUBLE SLEEF_VECTOR_FLOAT SLEEF_VECTOR_INT vsx vsx3 x)

  # Target renamedsp128.h
  sleef_generate_rename_simd(sp128 - 2 4)

  # Target disppower_128_obj
  add_library(disppower_128_obj OBJECT disppower_128.c)
  target_compile_options(disppower_128_obj PRIVATE ${FLAGS_ENABLE_VSX})
  if(COMPILER_SUPPORTS_VSX3)
    set(DISPATCHER_DEFINITIONS ENABLE_VSX3=1)
  endif()
  set_target_properties(disppower_128_obj PROPERTIES ${COMMON_TARGET_PROPERTIES})
  target_compile_definitions(disppower_128_obj PRIVATE ${COMMON_TARGET_DEFINITIONS} ${DISPATCHER_DEFINITIONS})
  target_include_directories(disppower_128_obj PRIVATE ${CMAKE_BINARY_DIR}/include)
  add_dependencies(disppower_128_obj disppower_128.c_generated renamedsp128.h_generated ${TARGET_HEADERS})
  target_sources(${TARGET_LIBSLEEF} PRIVATE $<TARGET_OBJECTS:disppower_128_obj>)

  if(COMPILER_SUPPORTS_VSX3)
    add_library(tryvsx3_obj OBJECT tryvsx3.c)
    target_compile_options(tryvsx3_obj PRIVATE ${FLAGS_ENABLE_VSX3})
    set_target_properties(tryvsx3_obj PROPERTIES ${COMMON_TARGET_PROPERTIES})
    target_sources(${TARGET_LIBSLEEF} PRIVATE $<TARGET_OBJECTS:tryvsx3_obj>)
  endif()
endif(SLEEF_ARCH_PPC64 AND COMPILER_SUPPORTS_VSX)

if (SLEEF_ARCH_S390X AND COMPILER_SUPPORTS_VXE)
  # Target disps390x_128.c
  sleef_generate_disp_simd(s390x_128 2 4 SLEEF_VECTOR_DOUBLE SLEEF_VECTOR_FLOAT SLEEF_VECTOR_INT vxe vxe2 x)

  # Target renamedsp128.h
  sleef_generate_rename_simd(sp128 - 2 4)

  # Target disps390x_128_obj
  add_library(disps390x_128_obj OBJECT disps390x_128.c)
  target_compile_options(disps390x_128_obj PRIVATE ${FLAGS_ENABLE_VXE})
  if(COMPILER_SUPPORTS_VXE2)
    set(DISPATCHER_DEFINITIONS ENABLE_VXE2=1)
  endif()
  set_target_properties(disps390x_128_obj PROPERTIES ${COMMON_TARGET_PROPERTIES})
  target_compile_definitions(disps390x_128_obj PRIVATE ${COMMON_TARGET_DEFINITIONS} ${DISPATCHER_DEFINITIONS})
  target_include_directories(disps390x_128_obj PRIVATE ${CMAKE_BINARY_DIR}/include)
  add_dependencies(disps390x_128_obj disps390x_128.c_generated renamedsp128.h_generated ${TARGET_HEADERS})
  target_sources(${TARGET_LIBSLEEF} PRIVATE $<TARGET_OBJECTS:disps390x_128_obj>)

endif(SLEEF_ARCH_S390X AND COMPILER_SUPPORTS_VXE)

# --------------------------------------------------------------------
# Install
# --------------------------------------------------------------------
# Install libsleef and sleef.h
install(
    TARGETS ${TARGET_LIBSLEEF}
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
configure_file("sleef.pc.in" "${CMAKE_CURRENT_BINARY_DIR}/sleef.pc" @ONLY)
install(
    FILES "${CMAKE_CURRENT_BINARY_DIR}/sleef.pc"
    DESTINATION "${CMAKE_INSTALL_LIBDIR}/pkgconfig"
    COMPONENT sleef_Development
)
