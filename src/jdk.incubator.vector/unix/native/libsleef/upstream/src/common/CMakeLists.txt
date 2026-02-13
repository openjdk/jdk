# Compiler properties

set(COMMON_TARGET_PROPERTIES
  C_STANDARD 99                  # -std=gnu99
  )

if (BUILD_SHARED_LIBS)
  list(APPEND COMMON_TARGET_PROPERTIES POSITION_INDEPENDENT_CODE ON)   # -fPIC
endif()

# This is a workaround of appveyor bug
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${SLEEF_C_FLAGS}")
set(CMAKE_CXX_FLAGS "${CMAKE_C_FLAGS} ${SLEEF_C_FLAGS}")

# Target TARGET_LIBCOMMON_OBJ

add_library(${TARGET_LIBCOMMON_OBJ} OBJECT common.c)
set_target_properties(${TARGET_LIBCOMMON_OBJ} PROPERTIES ${COMMON_TARGET_PROPERTIES})

add_host_executable("addSuffix" addSuffix.c)
set_target_properties("addSuffix" PROPERTIES C_STANDARD 99)

if (NOT SLEEF_OPENSSL_FOUND)
  add_library(${TARGET_PSHA_OBJ} OBJECT psha2_capi.cpp)
else()
  # Tests for internal sha256
  add_executable(test_psha test_psha2.cpp)
  target_link_libraries(test_psha ${SLEEF_OPENSSL_LIBRARIES})
  target_include_directories(test_psha PRIVATE ${SLEEF_OPENSSL_INCLUDE_DIR})
  add_test(NAME test_psha COMMAND test_psha)
  set_tests_properties(test_psha PROPERTIES COST 2.0)

  add_executable(test_psha_capi test_psha2.cpp)
  target_compile_definitions(test_psha_capi PRIVATE TEST_CAPI=1)
  target_link_libraries(test_psha_capi ${SLEEF_OPENSSL_LIBRARIES})
  target_include_directories(test_psha_capi PRIVATE ${SLEEF_OPENSSL_INCLUDE_DIR})
  add_test(NAME test_psha_capi COMMAND test_psha_capi)
  set_tests_properties(test_psha_capi PROPERTIES COST 2.0)
endif()

# Target TARGET_TESTERUTIL_OBJ
add_library(${TARGET_TESTERUTIL_OBJ} OBJECT testerutil.c)
target_compile_definitions(${TARGET_TESTERUTIL_OBJ} PRIVATE ${COMMON_TARGET_DEFINITIONS})
if(LIB_MPFR)
  target_compile_definitions(${TARGET_TESTERUTIL_OBJ} PRIVATE USEMPFR=1)
  target_link_libraries(${TARGET_TESTERUTIL_OBJ} ${LIB_MPFR} ${LIBGMP})
endif()
if (MPFR_INCLUDE_DIR)
  target_include_directories(${TARGET_TESTERUTIL_OBJ} PRIVATE ${MPFR_INCLUDE_DIR})
endif()

# Target TARGET_QTESTERUTIL_OBJ
add_library(${TARGET_QTESTERUTIL_OBJ} OBJECT qtesterutil.c)
target_compile_definitions(${TARGET_QTESTERUTIL_OBJ} PRIVATE ${COMMON_TARGET_DEFINITIONS})
if(LIB_MPFR)
  target_compile_definitions(${TARGET_QTESTERUTIL_OBJ} PRIVATE USEMPFR=1)
  target_link_libraries(${TARGET_QTESTERUTIL_OBJ} ${LIB_MPFR} ${LIBGMP})
endif()
if (MPFR_INCLUDE_DIR)
  target_include_directories(${TARGET_QTESTERUTIL_OBJ} PRIVATE ${MPFR_INCLUDE_DIR})
endif()
if(COMPILER_SUPPORTS_QUADMATH)
  target_link_libraries(${TARGET_QTESTERUTIL_OBJ} "-lquadmath")
  target_compile_definitions(${TARGET_QTESTERUTIL_OBJ} PRIVATE ENABLEFLOAT128=1)
endif()

# Target TARGET_COMMONXX_OBJ
add_library(${TARGET_COMMONXX_OBJ} OBJECT commonxx.cpp)
target_compile_definitions(${TARGET_COMMONXX_OBJ} PRIVATE ${COMMON_TARGET_DEFINITIONS})
install(TARGETS ${TARGET_COMMONXX_OBJ} EXPORT sleefTargets)
