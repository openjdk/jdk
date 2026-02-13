link_directories(${sleef_BINARY_DIR}/lib)                 # libsleefquad
link_directories(${sleef_BINARY_DIR}/src/common)          # common.a
include_directories(${sleef_BINARY_DIR}/include)          # sleef.h, sleefquad.h
include_directories(${sleef_SOURCE_DIR}/src/quad)         # qrename.h
include_directories(${sleef_BINARY_DIR}/src/quad/include) # rename headers

if(NOT LIB_MPFR)
  find_program(QTESTER_COMMAND qtester)
endif(NOT LIB_MPFR)

find_library(LIBRT rt)
if (NOT LIBRT)
  set(LIBRT "")
endif()

if(COMPILER_SUPPORTS_QUADMATH)
  set(LIBQUADMATH "-lquadmath")
else()
  set(LIBQUADMATH "")
endif()

set(CMAKE_C_FLAGS "${ORG_CMAKE_C_FLAGS} ${SLEEF_C_FLAGS} ${FLAGS_NOSTRICTALIASING}")
set(CMAKE_CXX_FLAGS "${ORG_CMAKE_CXX_FLAGS} ${SLEEF_C_FLAGS} ${FLAGS_NOSTRICTALIASING}")

if(COMPILER_SUPPORTS_FLOAT128)
  list(APPEND COMMON_TARGET_DEFINITIONS ENABLEFLOAT128=1)
endif()

# Build tester3printf

add_executable(tester3printf tester3printf.c)
add_dependencies(tester3printf sleefquad sleefquad_headers ${TARGET_LIBSLEEF} ${TARGET_HEADERS})
target_compile_definitions(tester3printf PRIVATE ${COMMON_TARGET_DEFINITIONS})
set_target_properties(tester3printf PROPERTIES C_STANDARD 99)

if (SLEEF_OPENSSL_FOUND)
  target_link_libraries(tester3printf sleefquad ${TARGET_LIBSLEEF} ${SLEEF_OPENSSL_LIBRARIES} ${TLFLOAT_LIBRARIES})
  target_include_directories(tester3printf PRIVATE ${SLEEF_OPENSSL_INCLUDE_DIR})
else()
  target_link_libraries(tester3printf sleefquad ${TARGET_LIBSLEEF} ${TARGET_PSHA_OBJ} ${TLFLOAT_LIBRARIES})
  target_include_directories(tester3printf PRIVATE ${sleef_SOURCE_DIR}/src/common)
  target_compile_definitions(tester3printf PRIVATE SLEEF_USE_INTERNAL_SHA256=1)
endif()

if (SDE_COMMAND)
  add_test(NAME tester3printf COMMAND ${SDE_COMMAND} "--" ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/tester3printf ${sleef_SOURCE_DIR}/src/quad-tester/hash_printf.txt)
elseif(EMULATOR)
  add_test(NAME tester3printf COMMAND ${EMULATOR} ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/tester3printf ${sleef_SOURCE_DIR}/src/quad-tester/hash_printf.txt)
else()
  add_test(NAME tester3printf COMMAND tester3printf ${sleef_SOURCE_DIR}/src/quad-tester/hash_printf.txt)
endif()
set_tests_properties(tester3printf PROPERTIES COST 6.0)

#

function(add_test_iut IUT C)
  if (LIB_MPFR)
    set(QTESTER qtester)
  elseif(QTESTER_COMMAND)
    set(QTESTER ${QTESTER_COMMAND})
  endif()
  # When we are crosscompiling using the mkrename* tools from a native
  # build, we use the tester executable from the native build.
  if (CMAKE_CROSSCOMPILING AND NATIVE_BUILD_DIR)
    set(QTESTER ${NATIVE_BUILD_DIR}/bin/qtester)
  endif(CMAKE_CROSSCOMPILING AND NATIVE_BUILD_DIR)
  if (QTESTER)
    if (NOT EMULATOR)
      if (SDE_COMMAND)
        set(FLAGS_SDE "--sde" ${SDE_COMMAND})
      else()
        set(FLAGS_SDE)
      endif()
      add_test(NAME ${IUT}
        COMMAND ${QTESTER} ${FLAGS_SDE} ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/${IUT}
        WORKING_DIRECTORY ${CMAKE_RUNTIME_OUTPUT_DIRECTORY})
      set_tests_properties(${IUT} PROPERTIES COST ${C})
    else()
      add_test(NAME ${IUT}
        COMMAND ${QTESTER} "--qemu" ${EMULATOR} ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/${IUT}
        WORKING_DIRECTORY ${CMAKE_RUNTIME_OUTPUT_DIRECTORY})
      set_tests_properties(${IUT} PROPERTIES COST ${C})
    endif()
  endif()
endfunction()

# Add vector extension `iut`s
set(IUT_SRC qiutsimd.c ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)

macro(test_extension SIMD)
  if(COMPILER_SUPPORTS_${SIMD})
    string(TOLOWER ${SIMD} LCSIMD)

    if (SLEEF_ENABLE_TESTER)
      string(CONCAT TARGET_IUT${SIMD} "qiut" ${LCSIMD})

      add_executable(${TARGET_IUT${SIMD}} ${IUT_SRC})
      target_compile_options(${TARGET_IUT${SIMD}}
        PRIVATE ${FLAGS_ENABLE_${SIMD}})
      target_compile_definitions(${TARGET_IUT${SIMD}}
        PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS})
      target_link_libraries(${TARGET_IUT${SIMD}} sleefquad ${TARGET_LIBSLEEF} ${LIBRT} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${LIBQUADMATH} ${TLFLOAT_LIBRARIES} ${LIBM})

      add_dependencies(${TARGET_IUT${SIMD}} sleefquad_headers ${TARGET_HEADERS} sleefquad ${TARGET_LIBSLEEF})
      set_target_properties(${TARGET_IUT${SIMD}} PROPERTIES C_STANDARD 99)
      if (DEFINED COSTOVERRIDE_${SIMD})
        math(EXPR C "${COSTOVERRIDE_${SIMD}} + 1")
        add_test_iut(${TARGET_IUT${SIMD}} ${C})
      else()
        add_test_iut(${TARGET_IUT${SIMD}} 0.5)
      endif()
      list(APPEND IUT_LIST ${TARGET_IUT${SIMD}})
    endif(SLEEF_ENABLE_TESTER)

    #

    if (SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
      set(TESTER4_SRC qtester4simd.cpp ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)
      string(CONCAT TARGET_TESTER4_${SIMD} "qtester4" ${LCSIMD})

      add_executable(${TARGET_TESTER4_${SIMD}} ${TESTER4_SRC})
      target_compile_options(${TARGET_TESTER4_${SIMD}}
        PRIVATE ${FLAGS_ENABLE_${SIMD}})
      target_compile_definitions(${TARGET_TESTER4_${SIMD}}
        PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS})
      target_link_libraries(${TARGET_TESTER4_${SIMD}} sleefquad ${TARGET_LIBSLEEF} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${TLFLOAT_LIBRARIES} ${LIBM})

      add_dependencies(${TARGET_TESTER4_${SIMD}} sleefquad_headers ${TARGET_HEADERS} sleefquad ${TARGET_LIBSLEEF} ext_tlfloat)
      set_target_properties(${TARGET_TESTER4_${SIMD}} PROPERTIES C_STANDARD 99)
      if (DEFINED COSTOVERRIDE_${SIMD})
        add_test_with_emu(${COSTOVERRIDE_${SIMD}} ${TARGET_TESTER4_${SIMD}})
      else()
        add_test_with_emu(1.0 ${TARGET_TESTER4_${SIMD}})
      endif()
    endif(SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)

    # The iut programs whose names begin with "qiuti" are the iut for the
    # inline version of quad functions.

    list(FIND SLEEF_SUPPORTED_INLINE_QUAD_EXTENSIONS ${SIMD} INDEX_INLINE)
    if (SLEEF_BUILD_INLINE_HEADERS AND SED_COMMAND AND NOT INDEX_INLINE EQUAL -1)
      if (MSVC AND NOT SLEEF_CLANG_ON_WINDOWS)
        message(STATUS "Quad inline headers are not tested with MSVC")
      else(MSVC AND NOT SLEEF_CLANG_ON_WINDOWS)
        if (SLEEF_ENABLE_TESTER)
          string(CONCAT IUTINAME "qiuti" ${LCSIMD})
          add_executable(${IUTINAME} ${IUT_SRC})
          target_compile_options(${IUTINAME}
            PRIVATE ${FLAGS_ENABLE_${SIMD}})
          target_compile_definitions(${IUTINAME}
            PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS}
            USE_INLINE_HEADER="sleefquadinline_${LCSIMD}.h"
            MACRO_ONLY_HEADER="qmacroonly${SIMD}.h"
            SIMD_SUFFIX=_${LCSIMD}_sleefq
          )
          target_include_directories(${IUTINAME} PRIVATE ${PROJECT_BINARY_DIR}/include)
          target_link_libraries(${IUTINAME} sleefquad ${TARGET_LIBSLEEF} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${LIBQUADMATH} ${TLFLOAT_LIBRARIES} ${LIBM} ${LIBRT})
          add_dependencies(${IUTINAME} ${TARGET_QINLINE_HEADERS} ext_tlfloat)
          set_target_properties(${IUTINAME} PROPERTIES C_STANDARD 99)
          if (DEFINED COSTOVERRIDE_${SIMD})
            math(EXPR C "${COSTOVERRIDE_${SIMD}} + 1")
            add_test_iut(${IUTINAME} ${C})
          else()
            add_test_iut(${IUTINAME} 0.5)
          endif()
          list(APPEND IUT_LIST ${IUTINAME})
        endif(SLEEF_ENABLE_TESTER)

        #

        if (SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
          string(CONCAT TARGET_TESTER4I_${SIMD} "qtester4i" ${LCSIMD})
          add_executable(${TARGET_TESTER4I_${SIMD}} ${TESTER4_SRC})
          target_compile_options(${TARGET_TESTER4I_${SIMD}}
            PRIVATE ${FLAGS_ENABLE_${SIMD}})
          target_compile_definitions(${TARGET_TESTER4I_${SIMD}}
            PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS})
          target_link_libraries(${TARGET_TESTER4I_${SIMD}} sleefquad ${TARGET_LIBSLEEF} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${TLFLOAT_LIBRARIES})
          if(CMAKE_C_COMPILER_ID MATCHES "GNU")
            target_compile_options(${TARGET_TESTER4I_${SIMD}} PRIVATE "-Wno-unknown-pragmas")
          endif()
          target_compile_definitions(${TARGET_TESTER4I_${SIMD}}
            PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS}
            USE_INLINE_HEADER="sleefquadinline_${LCSIMD}.h"
            MACRO_ONLY_HEADER="qmacroonly${SIMD}.h"
            SIMD_SUFFIX=_${LCSIMD}_sleefq
          )
          target_include_directories(${TARGET_TESTER4I_${SIMD}} PRIVATE ${PROJECT_BINARY_DIR}/include)
          add_dependencies(${TARGET_TESTER4I_${SIMD}} sleefquad sleefquad_headers ${TARGET_QINLINE_HEADERS} ext_tlfloat)
          if (DEFINED COSTOVERRIDE_${SIMD})
            add_test_with_emu(${COSTOVERRIDE_${SIMD}} ${TARGET_TESTER4I_${SIMD}})
          else()
            add_test_with_emu(1.0 ${TARGET_TESTER4I_${SIMD}})
          endif()
        endif(SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)

      endif(MSVC AND NOT SLEEF_CLANG_ON_WINDOWS)
    endif()

    #

    if(LIB_MPFR AND NOT MINGW)
      # Build qtester2 SIMD
      string(TOLOWER ${SIMD} SIMDLC)
      set(T "tester2${SIMDLC}qp")
      add_executable(${T} tester2simdqp.c)
      target_compile_options(${T} PRIVATE ${FLAGS_ENABLE_${SIMD}})
      target_compile_definitions(${T} PRIVATE ENABLE_${SIMD}=1 USEMPFR=1 ${COMMON_TARGET_DEFINITIONS})
      set_target_properties(${T} PROPERTIES C_STANDARD 99)
      target_link_libraries(${T} sleefquad ${TARGET_LIBSLEEF} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${LIB_MPFR} ${LIBGMP} ${LIBQUADMATH} ${TLFLOAT_LIBRARIES} ${LIBM})
      add_dependencies(${T} sleefquad sleefquad_headers ${TARGET_LIBSLEEF} ${TARGET_HEADERS})
      if (MPFR_INCLUDE_DIR)
        target_include_directories(${T} PRIVATE ${MPFR_INCLUDE_DIR})
      endif()
    endif()
  endif(COMPILER_SUPPORTS_${SIMD})
endmacro(test_extension)

foreach(SIMD ${SLEEF_SUPPORTED_QUAD_EXTENSIONS})
  test_extension(${SIMD})
endforeach()

# Compile executable 'qiutdspscalar'

if (SLEEF_ENABLE_TESTER)
  add_executable(qiutdspscalar ${IUT_SRC})
  target_compile_definitions(qiutdspscalar PRIVATE ENABLE_DSPSCALAR=1 ${COMMON_TARGET_DEFINITIONS})
  target_link_libraries(qiutdspscalar sleefquad ${TARGET_LIBSLEEF} ${LIBRT} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${LIBQUADMATH} ${TLFLOAT_LIBRARIES} ${LIBM})
  set_target_properties(qiutdspscalar PROPERTIES C_STANDARD 99)
  add_dependencies(qiutdspscalar sleefquad_headers ${TARGET_HEADERS})
  add_dependencies(qiutdspscalar sleefquad ${TARGET_LIBSLEEF})
  add_test_iut(qiutdspscalar 0.5)
  list(APPEND IUT_LIST qiutdspscalar)
endif(SLEEF_ENABLE_TESTER)

if (SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
  # Compile executable 'qtester4dspscalar'
  set(TESTER4_SRC qtester4simd.cpp ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)
  set(SIMD "DSPSCALAR")
  set(LCSIMD "dspscalar")
  string(CONCAT TARGET_TESTER4_${SIMD} "qtester4" ${LCSIMD})

  add_executable(${TARGET_TESTER4_${SIMD}} ${TESTER4_SRC})
  target_compile_definitions(${TARGET_TESTER4_${SIMD}} PRIVATE ENABLE_${SIMD}=1 ${COMMON_TARGET_DEFINITIONS})
  target_link_libraries(${TARGET_TESTER4_${SIMD}} sleefquad ${TARGET_LIBSLEEF} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${TLFLOAT_LIBRARIES} ${LIBM})
  add_dependencies(${TARGET_TESTER4_${SIMD}} sleefquad_headers ${TARGET_HEADERS})
  add_dependencies(${TARGET_TESTER4_${SIMD}} sleefquad ${TARGET_LIBSLEEF})
  add_dependencies(${TARGET_TESTER4_${SIMD}} ext_tlfloat)
  set_target_properties(${TARGET_TESTER4_${SIMD}} PROPERTIES C_STANDARD 99)
  if (DEFINED COSTOVERRIDE_${SIMD})
    add_test_with_emu(${COSTOVERRIDE_${SIMD}} ${TARGET_TESTER4_${SIMD}})
  else()
    add_test_with_emu(1.0 ${TARGET_TESTER4_${SIMD}})
  endif()
endif(SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)

if (SLEEF_ARCH_X86 AND COMPILER_SUPPORTS_SSE2)
  if (SLEEF_ENABLE_TESTER)
    # Compile executable 'qiutdspx2'
    add_executable(qiutdspx2 ${IUT_SRC})
    target_compile_definitions(qiutdspx2 PRIVATE ENABLE_DSPX2_X86=1 ${COMMON_TARGET_DEFINITIONS})
    target_link_libraries(qiutdspx2 sleefquad ${TARGET_LIBSLEEF} ${LIBRT} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${LIBQUADMATH} ${TLFLOAT_LIBRARIES} ${LIBM})
    set_target_properties(qiutdspx2 PROPERTIES C_STANDARD 99)
    add_dependencies(qiutdspx2 sleefquad_headers ${TARGET_HEADERS})
    add_dependencies(qiutdspx2 sleefquad ${TARGET_LIBSLEEF})
    add_test_iut(qiutdspx2 0.5)
    list(APPEND IUT_LIST qiutdspx2)
  endif(SLEEF_ENABLE_TESTER)

  if (SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
    # Compile executable 'qtester4dspx2'
    set(TESTER4_SRC qtester4simd.cpp ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)
    set(SIMD "DSPX2")
    set(LCSIMD "dspx2")
    string(CONCAT TARGET_TESTER4_${SIMD} "qtester4" ${LCSIMD})

    add_executable(${TARGET_TESTER4_${SIMD}} ${TESTER4_SRC})
    target_compile_definitions(${TARGET_TESTER4_${SIMD}} PRIVATE ENABLE_DSPX2_X86=1 ${COMMON_TARGET_DEFINITIONS})
    target_link_libraries(${TARGET_TESTER4_${SIMD}} sleefquad ${TARGET_LIBSLEEF} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${TLFLOAT_LIBRARIES} ${LIBM})
    add_dependencies(${TARGET_TESTER4_${SIMD}} sleefquad_headers ${TARGET_HEADERS})
    add_dependencies(${TARGET_TESTER4_${SIMD}} sleefquad ${TARGET_LIBSLEEF})
    add_dependencies(${TARGET_TESTER4_${SIMD}} ext_tlfloat)
    set_target_properties(${TARGET_TESTER4_${SIMD}} PROPERTIES C_STANDARD 99)
    if (DEFINED COSTOVERRIDE_${SIMD})
      add_test_with_emu(${COSTOVERRIDE_${SIMD}} ${TARGET_TESTER4_${SIMD}})
    else()
      add_test_with_emu(1.0 ${TARGET_TESTER4_${SIMD}})
    endif()
  endif(SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
endif()

if (SLEEF_ARCH_AARCH64)
  if (SLEEF_ENABLE_TESTER)
    # Compile executable 'qiutdspx2'
    add_executable(qiutdspx2 ${IUT_SRC})
    target_compile_definitions(qiutdspx2 PRIVATE ENABLE_DSPX2_AARCH64=1 ${COMMON_TARGET_DEFINITIONS})
    set_target_properties(qiutdspx2 PROPERTIES C_STANDARD 99)
    target_link_libraries(qiutdspx2 sleefquad ${TARGET_LIBSLEEF} ${LIBRT} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${LIBQUADMATH} ${TLFLOAT_LIBRARIES} ${LIBM})
    add_dependencies(qiutdspx2 sleefquad_headers ${TARGET_HEADERS})
    add_dependencies(qiutdspx2 sleefquad ${TARGET_LIBSLEEF})
    add_test_iut(qiutdspx2 0.5)
    list(APPEND IUT_LIST qiutdspx2)
  endif(SLEEF_ENABLE_TESTER)

  if (SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
    # Compile executable 'qtester4dspx2'
    set(TESTER4_SRC qtester4simd.cpp ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)
    set(SIMD "DSPX2")
    set(LCSIMD "dspx2")
    string(CONCAT TARGET_TESTER4_${SIMD} "qtester4" ${LCSIMD})

    add_executable(${TARGET_TESTER4_${SIMD}} ${TESTER4_SRC})
    target_compile_definitions(${TARGET_TESTER4_${SIMD}} PRIVATE ENABLE_DSPX2_AARCH64=1 ${COMMON_TARGET_DEFINITIONS})
    target_link_libraries(${TARGET_TESTER4_${SIMD}} sleefquad ${TARGET_LIBSLEEF} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${TLFLOAT_LIBRARIES} ${TLFLOAT_LIBRARIES} ${LIBM})
    add_dependencies(${TARGET_TESTER4_${SIMD}} sleefquad_headers ${TARGET_HEADERS})
    add_dependencies(${TARGET_TESTER4_${SIMD}} sleefquad ${TARGET_LIBSLEEF})
    add_dependencies(${TARGET_TESTER4_${SIMD}} ext_tlfloat)
    set_target_properties(${TARGET_TESTER4_${SIMD}} PROPERTIES C_STANDARD 99)
    if (DEFINED COSTOVERRIDE_${SIMD})
      add_test_with_emu(${COSTOVERRIDE_${SIMD}} ${TARGET_TESTER4_${SIMD}})
    else()
      add_test_with_emu(1.0 ${TARGET_TESTER4_${SIMD}})
    endif()
  endif(SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
endif()

if (SLEEF_ARCH_PPC64 AND COMPILER_SUPPORTS_VSX)
  if (SLEEF_ENABLE_TESTER)
    # Compile executable 'qiutdspx2'
    add_executable(qiutdspx2 ${IUT_SRC})
    target_compile_options(qiutdspx2 PRIVATE ${FLAGS_ENABLE_VSX})
    set_target_properties(qiutdspx2 PROPERTIES C_STANDARD 99)
    target_compile_definitions(qiutdspx2 PRIVATE ENABLE_DSPX2_PPC64=1 ${COMMON_TARGET_DEFINITIONS})
    target_link_libraries(qiutdspx2 sleefquad ${TARGET_LIBSLEEF} ${LIBRT} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${LIBQUADMATH} ${TLFLOAT_LIBRARIES} ${LIBM})
    add_dependencies(qiutdspx2 sleefquad_headers ${TARGET_HEADERS})
    add_dependencies(qiutdspx2 sleefquad ${TARGET_LIBSLEEF})
    add_test_iut(qiutdspx2 0.5)
    list(APPEND IUT_LIST qiutdspx2)
  endif(SLEEF_ENABLE_TESTER)

  if (SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
    # Compile executable 'qtester4dspx2'
    set(TESTER4_SRC qtester4simd.cpp ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)
    set(SIMD "DSPX2")
    set(LCSIMD "dspx2")
    string(CONCAT TARGET_TESTER4_${SIMD} "qtester4" ${LCSIMD})

    add_executable(${TARGET_TESTER4_${SIMD}} ${TESTER4_SRC})
    target_compile_options(${TARGET_TESTER4_${SIMD}} PRIVATE ${FLAGS_ENABLE_VSX})
    target_compile_definitions(${TARGET_TESTER4_${SIMD}} PRIVATE ENABLE_DSPX2_PPC64=1 ${COMMON_TARGET_DEFINITIONS})
    target_link_libraries(${TARGET_TESTER4_${SIMD}} sleefquad ${TARGET_LIBSLEEF} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${TLFLOAT_LIBRARIES} ${LIBM})
    add_dependencies(${TARGET_TESTER4_${SIMD}} sleefquad_headers ${TARGET_HEADERS})
    add_dependencies(${TARGET_TESTER4_${SIMD}} sleefquad ${TARGET_LIBSLEEF})
    add_dependencies(${TARGET_TESTER4_${SIMD}} ext_tlfloat)
    set_target_properties(${TARGET_TESTER4_${SIMD}} PROPERTIES C_STANDARD 99)
    if (DEFINED COSTOVERRIDE_${SIMD})
      add_test_with_emu(${COSTOVERRIDE_${SIMD}} ${TARGET_TESTER4_${SIMD}})
    else()
      add_test_with_emu(1.0 ${TARGET_TESTER4_${SIMD}})
    endif()
  endif(SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
endif()

if (SLEEF_ARCH_S390X AND COMPILER_SUPPORTS_VXE)
  if (SLEEF_ENABLE_TESTER)
    # Compile executable 'qiutdspx2'
    add_executable(qiutdspx2 ${IUT_SRC})
    target_compile_options(qiutdspx2 PRIVATE ${FLAGS_ENABLE_VXE})
    set_target_properties(qiutdspx2 PROPERTIES C_STANDARD 99)
    target_compile_definitions(qiutdspx2 PRIVATE ENABLE_DSPX2_S390X=1 ${COMMON_TARGET_DEFINITIONS})
    target_link_libraries(qiutdspx2 sleefquad ${TARGET_LIBSLEEF} ${LIBRT} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${LIBQUADMATH} ${TLFLOAT_LIBRARIES} ${LIBM})
    add_dependencies(qiutdspx2 sleefquad_headers ${TARGET_HEADERS})
    add_dependencies(qiutdspx2 sleefquad ${TARGET_LIBSLEEF})
    add_test_iut(qiutdspx2 0.5)
    list(APPEND IUT_LIST qiutdspx2)
  endif(SLEEF_ENABLE_TESTER)

  if (SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
    # Compile executable 'qtester4dspx2'
    set(TESTER4_SRC qtester4simd.cpp ${sleef_SOURCE_DIR}/src/common/main_checkfeature.c)
    set(SIMD "DSPX2")
    set(LCSIMD "dspx2")
    string(CONCAT TARGET_TESTER4_${SIMD} "qtester4" ${LCSIMD})

    add_executable(${TARGET_TESTER4_${SIMD}} ${TESTER4_SRC})
    target_compile_options(${TARGET_TESTER4_${SIMD}} PRIVATE ${FLAGS_ENABLE_VXE})
    target_compile_definitions(${TARGET_TESTER4_${SIMD}} PRIVATE ENABLE_DSPX2_S390X=1 ${COMMON_TARGET_DEFINITIONS})
    target_link_libraries(${TARGET_TESTER4_${SIMD}} sleefquad ${TARGET_LIBSLEEF} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${TLFLOAT_LIBRARIES} ${LIBM})
    add_dependencies(${TARGET_TESTER4_${SIMD}} sleefquad_headers ${TARGET_HEADERS})
    add_dependencies(${TARGET_TESTER4_${SIMD}} sleefquad ${TARGET_LIBSLEEF})
    add_dependencies(${TARGET_TESTER4_${SIMD}} ext_tlfloat)
    set_target_properties(${TARGET_TESTER4_${SIMD}} PROPERTIES C_STANDARD 99)
    if (DEFINED COSTOVERRIDE_${SIMD})
      add_test_with_emu(${COSTOVERRIDE_${SIMD}} ${TARGET_TESTER4_${SIMD}})
    else()
      add_test_with_emu(1.0 ${TARGET_TESTER4_${SIMD}})
    endif()
  endif(SLEEF_ENABLE_TESTER4 AND TLFLOAT_LIBRARIES)
endif()

# Compile executable 'qiutcuda'

if (SLEEF_BUILD_INLINE_HEADERS AND SED_COMMAND AND CMAKE_CUDA_COMPILER)
  add_executable(qiutcuda qiutcuda.cu)
  target_link_libraries(qiutcuda sleefquad ${TARGET_LIBSLEEF} ${TLFLOAT_LIBRARIES})
  set_target_properties(qiutcuda PROPERTIES LINKER_LANGUAGE CUDA)
  target_compile_options(qiutcuda PRIVATE "--fmad=false;-Xcompiler;-fext-numeric-literals;-Xcompiler;-ffp-contract=off")
  add_dependencies(qiutcuda ${TARGET_QINLINE_HEADERS})
  add_test_iut(qiutcuda 20.0)
  list(APPEND IUT_LIST qiutcuda)
endif()

#

if(LIB_MPFR AND NOT MINGW)
  # Compile executable 'qtester'
  add_host_executable(qtester qtester.c)
  if (NOT CMAKE_CROSSCOMPILING)
    target_link_libraries(qtester sleefquad ${TARGET_LIBSLEEF} ${TARGET_QTESTERUTIL_OBJ} ${TARGET_TESTERUTIL_OBJ} ${LIB_MPFR} ${LIBGMP} ${LIBQUADMATH} ${TLFLOAT_LIBRARIES} ${LIBM})
    target_compile_definitions(qtester PRIVATE USEMPFR=1 ${COMMON_TARGET_DEFINITIONS})
    target_compile_options(qtester PRIVATE -Wno-unused-result)
    set_target_properties(qtester PROPERTIES C_STANDARD 99)
    if (MPFR_INCLUDE_DIR)
      target_include_directories(qtester PRIVATE ${MPFR_INCLUDE_DIR})
    endif()
  endif()
endif(LIB_MPFR AND NOT MINGW)

# BUild qutil

add_executable(qutil qutil.c)
add_dependencies(qutil sleefquad sleefquad_headers ${TARGET_LIBSLEEF} ${TARGET_HEADERS})
target_compile_definitions(qutil PRIVATE ${COMMON_TARGET_DEFINITIONS})
set_target_properties(qutil PROPERTIES C_STANDARD 99)
target_link_libraries(qutil sleefquad ${TLFLOAT_LIBRARIES})
