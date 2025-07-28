/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "cds/cdsConfig.hpp"
#include "compiler/compilerDefinitions.hpp"
#include "jvm_io.h"
#include "runtime/arguments.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/globalDefinitions.hpp"

const char* Abstract_VM_Version::_s_vm_release = Abstract_VM_Version::vm_release();
const char* Abstract_VM_Version::_s_internal_vm_info_string = Abstract_VM_Version::internal_vm_info_string();

uint64_t Abstract_VM_Version::_features = 0;
const char* Abstract_VM_Version::_features_string = "";
const char* Abstract_VM_Version::_cpu_info_string = "";
uint64_t Abstract_VM_Version::_cpu_features = 0;

#ifndef SUPPORTS_NATIVE_CX8
bool Abstract_VM_Version::_supports_cx8 = false;
#endif
bool Abstract_VM_Version::_supports_atomic_getset4 = false;
bool Abstract_VM_Version::_supports_atomic_getset8 = false;
bool Abstract_VM_Version::_supports_atomic_getadd4 = false;
bool Abstract_VM_Version::_supports_atomic_getadd8 = false;
unsigned int Abstract_VM_Version::_logical_processors_per_package = 1U;
unsigned int Abstract_VM_Version::_L1_data_cache_line_size = 0;
unsigned int Abstract_VM_Version::_data_cache_line_flush_size = 0;

VirtualizationType Abstract_VM_Version::_detected_virtualization = NoDetectedVirtualization;

#ifndef HOTSPOT_VERSION_STRING
  #error HOTSPOT_VERSION_STRING must be defined
#endif

#ifndef VERSION_FEATURE
  #error VERSION_FEATURE must be defined
#endif
#ifndef VERSION_INTERIM
  #error VERSION_INTERIM must be defined
#endif
#ifndef VERSION_UPDATE
  #error VERSION_UPDATE must be defined
#endif
#ifndef VERSION_PATCH
  #error VERSION_PATCH must be defined
#endif
#ifndef VERSION_BUILD
  #error VERSION_BUILD must be defined
#endif

#ifndef VERSION_STRING
  #error VERSION_STRING must be defined
#endif

#ifndef DEBUG_LEVEL
  #error DEBUG_LEVEL must be defined
#endif

#ifndef HOTSPOT_BUILD_TIME
  #error HOTSPOT_BUILD_TIME must be defined
#endif

#ifndef JVM_VARIANT
  #error JVM_VARIANT must be defined
#endif

#define VM_RELEASE HOTSPOT_VERSION_STRING

// HOTSPOT_VERSION_STRING equals the JDK VERSION_STRING (unless overridden
// in a standalone build).
int Abstract_VM_Version::_vm_major_version = VERSION_FEATURE;
int Abstract_VM_Version::_vm_minor_version = VERSION_INTERIM;
int Abstract_VM_Version::_vm_security_version = VERSION_UPDATE;
int Abstract_VM_Version::_vm_patch_version = VERSION_PATCH;
int Abstract_VM_Version::_vm_build_number = VERSION_BUILD;

#if defined(_LP64)
  #define VMLP "64-Bit "
#else
  #define VMLP ""
#endif

#ifndef VMTYPE
  #if COMPILER1_AND_COMPILER2
    #define VMTYPE "Server"
  #else // COMPILER1_AND_COMPILER2
  #ifdef ZERO
    #define VMTYPE "Zero"
  #else // ZERO
     #define VMTYPE COMPILER1_PRESENT("Client")   \
                    COMPILER2_PRESENT("Server")
  #endif // ZERO
  #endif // COMPILER1_AND_COMPILER2
#endif

#ifndef HOTSPOT_VM_DISTRO
  #error HOTSPOT_VM_DISTRO must be defined
#endif
#define VMNAME HOTSPOT_VM_DISTRO " " VMLP VMTYPE " VM"

const char* Abstract_VM_Version::vm_name() {
  return VMNAME;
}

#ifndef VENDOR_PADDING
# define VENDOR_PADDING 64
#endif
#ifndef VENDOR
# define VENDOR  "Oracle Corporation"
#endif

static const char vm_vendor_string[sizeof(VENDOR) < VENDOR_PADDING ? VENDOR_PADDING : sizeof(VENDOR)] = VENDOR;

const char* Abstract_VM_Version::vm_vendor() {
  return vm_vendor_string;
}


// The VM info string should be a constant, but its value cannot be finalized until after VM arguments
// have been fully processed. And we want to avoid dynamic memory allocation which will cause ASAN
// report error, so we enumerate all the cases by static const string value.
const char* Abstract_VM_Version::vm_info_string() {
  switch (Arguments::mode()) {
    case Arguments::_int:
      if (is_vm_statically_linked()) {
        return CDSConfig::is_using_archive() ? "interpreted mode, static, sharing" : "interpreted mode, static";
      } else {
        return CDSConfig::is_using_archive() ? "interpreted mode, sharing" : "interpreted mode";
      }
    case Arguments::_mixed:
      if (is_vm_statically_linked()) {
        if (CompilationModeFlag::quick_only()) {
          return CDSConfig::is_using_archive() ? "mixed mode, emulated-client, static, sharing" : "mixed mode, emulated-client, static";
        } else {
          return CDSConfig::is_using_archive() ? "mixed mode, static, sharing" : "mixed mode, static";
         }
      } else {
        if (CompilationModeFlag::quick_only()) {
          return CDSConfig::is_using_archive() ? "mixed mode, emulated-client, sharing" : "mixed mode, emulated-client";
        } else {
          return CDSConfig::is_using_archive() ? "mixed mode, sharing" : "mixed mode";
        }
      }
    case Arguments::_comp:
      if (is_vm_statically_linked()) {
        if (CompilationModeFlag::quick_only()) {
          return CDSConfig::is_using_archive() ? "compiled mode, emulated-client, static, sharing" : "compiled mode, emulated-client, static";
        }
        return CDSConfig::is_using_archive() ? "compiled mode, static, sharing" : "compiled mode, static";
      } else {
        if (CompilationModeFlag::quick_only()) {
          return CDSConfig::is_using_archive() ? "compiled mode, emulated-client, sharing" : "compiled mode, emulated-client";
        }
        return CDSConfig::is_using_archive() ? "compiled mode, sharing" : "compiled mode";
      }
  }
  ShouldNotReachHere();
  return "";
}

// NOTE: do *not* use stringStream. this function is called by
//       fatal error handler. if the crash is in native thread,
//       stringStream cannot get resource allocated and will SEGV.
const char* Abstract_VM_Version::vm_release() {
  return VM_RELEASE;
}

#define OS       LINUX_ONLY("linux")             \
                 WINDOWS_ONLY("windows")         \
                 AIX_ONLY("aix")                 \
                 BSD_ONLY("bsd")

#ifndef CPU
#ifdef ZERO
#define CPU      ZERO_LIBARCH
#elif defined(PPC64)
#if defined(VM_LITTLE_ENDIAN)
#define CPU      "ppc64le"
#else
#define CPU      "ppc64"
#endif // PPC64
#else
#define CPU      AARCH64_ONLY("aarch64")         \
                 AMD64_ONLY("amd64")             \
                 IA32_ONLY("x86")                \
                 S390_ONLY("s390")               \
                 RISCV64_ONLY("riscv64")
#endif // !ZERO
#endif // !CPU

const char *Abstract_VM_Version::vm_platform_string() {
  return OS "-" CPU;
}

const char* Abstract_VM_Version::vm_variant() {
  return JVM_VARIANT;
}

const char* Abstract_VM_Version::internal_vm_info_string() {
  #ifndef HOTSPOT_BUILD_COMPILER
    #ifdef _MSC_VER
      #if _MSC_VER == 1911
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 15.3 (VS2017)"
      #elif _MSC_VER == 1912
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 15.5 (VS2017)"
      #elif _MSC_VER == 1913
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 15.6 (VS2017)"
      #elif _MSC_VER == 1914
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 15.7 (VS2017)"
      #elif _MSC_VER == 1915
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 15.8 (VS2017)"
      #elif _MSC_VER == 1916
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 15.9 (VS2017)"
      #elif _MSC_VER == 1920
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 16.0 (VS2019)"
      #elif _MSC_VER == 1921
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 16.1 (VS2019)"
      #elif _MSC_VER == 1922
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 16.2 (VS2019)"
      #elif _MSC_VER == 1923
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 16.3 (VS2019)"
      #elif _MSC_VER == 1924
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 16.4 (VS2019)"
      #elif _MSC_VER == 1925
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 16.5 (VS2019)"
      #elif _MSC_VER == 1926
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 16.6 (VS2019)"
      #elif _MSC_VER == 1927
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 16.7 (VS2019)"
      #elif _MSC_VER == 1928
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 16.8 / 16.9 (VS2019)"
      #elif _MSC_VER == 1929
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 16.10 / 16.11 (VS2019)"
      #elif _MSC_VER == 1930
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 17.0 (VS2022)"
      #elif _MSC_VER == 1931
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 17.1 (VS2022)"
      #elif _MSC_VER == 1932
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 17.2 (VS2022)"
      #elif _MSC_VER == 1933
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 17.3 (VS2022)"
      #elif _MSC_VER == 1934
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 17.4 (VS2022)"
      #elif _MSC_VER == 1935
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 17.5 (VS2022)"
      #elif _MSC_VER == 1936
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 17.6 (VS2022)"
      #elif _MSC_VER == 1937
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 17.7 (VS2022)"
      #elif _MSC_VER == 1938
        #define HOTSPOT_BUILD_COMPILER "MS VC++ 17.8 (VS2022)"
      #else
        #define HOTSPOT_BUILD_COMPILER "unknown MS VC++:" XSTR(_MSC_VER)
      #endif
    #elif defined(__clang_version__)
        #define HOTSPOT_BUILD_COMPILER "clang " __VERSION__
    #elif defined(__GNUC__)
        #define HOTSPOT_BUILD_COMPILER "gcc " __VERSION__
    #else
      #define HOTSPOT_BUILD_COMPILER "unknown compiler"
    #endif
  #endif

  #ifndef FLOAT_ARCH
    #if defined(__SOFTFP__)
      #define FLOAT_ARCH_STR "-sflt"
    #else
      #define FLOAT_ARCH_STR ""
    #endif
  #else
    #define FLOAT_ARCH_STR XSTR(FLOAT_ARCH)
  #endif

  #ifdef MUSL_LIBC
    #define LIBC_STR "-" XSTR(LIBC)
  #else
    #define LIBC_STR ""
  #endif

  #define INTERNAL_VERSION_SUFFIX VM_RELEASE ")" \
         " for " OS "-" CPU FLOAT_ARCH_STR LIBC_STR \
         " JRE (" VERSION_STRING "), built on " HOTSPOT_BUILD_TIME \
         " with " HOTSPOT_BUILD_COMPILER

  return strcmp(DEBUG_LEVEL, "release") == 0
      ? VMNAME " (" INTERNAL_VERSION_SUFFIX
      : VMNAME " (" DEBUG_LEVEL " " INTERNAL_VERSION_SUFFIX;
}

const char *Abstract_VM_Version::jdk_debug_level() {
  return DEBUG_LEVEL;
}

const char *Abstract_VM_Version::printable_jdk_debug_level() {
  // Debug level is not printed for "release" builds
  return strcmp(DEBUG_LEVEL, "release") == 0 ? "" : DEBUG_LEVEL " ";
}

unsigned int Abstract_VM_Version::jvm_version() {
  return ((Abstract_VM_Version::vm_major_version() & 0xFF) << 24) |
         ((Abstract_VM_Version::vm_minor_version() & 0xFF) << 16) |
         ((Abstract_VM_Version::vm_security_version() & 0xFF) << 8) |
         (Abstract_VM_Version::vm_build_number() & 0xFF);
}

const char* Abstract_VM_Version::extract_features_string(const char* cpu_info_string,
                                                         size_t cpu_info_string_len,
                                                         size_t features_offset) {
  assert(features_offset <= cpu_info_string_len, "");
  if (features_offset < cpu_info_string_len) {
    assert(cpu_info_string[features_offset + 0] == ',', "");
    assert(cpu_info_string[features_offset + 1] == ' ', "");
    return cpu_info_string + features_offset + 2; // skip initial ", "
  } else {
    return ""; // empty
  }
}

bool Abstract_VM_Version::print_matching_lines_from_file(const char* filename, outputStream* st, const char* keywords_to_match[]) {
  char line[500];
  FILE* fp = os::fopen(filename, "r");
  if (fp == nullptr) {
    return false;
  }

  st->print_cr("Virtualization information:");
  while (fgets(line, sizeof(line), fp) != nullptr) {
    int i = 0;
    while (keywords_to_match[i] != nullptr) {
      if (strncmp(line, keywords_to_match[i], strlen(keywords_to_match[i])) == 0) {
        st->print("%s", line);
        break;
      }
      i++;
    }
  }
  fclose(fp);
  return true;
}

// Abstract_VM_Version statics
int   Abstract_VM_Version::_no_of_threads = 0;
int   Abstract_VM_Version::_no_of_cores = 0;
int   Abstract_VM_Version::_no_of_sockets = 0;
bool  Abstract_VM_Version::_initialized = false;
char  Abstract_VM_Version::_cpu_name[CPU_TYPE_DESC_BUF_SIZE] = {0};
char  Abstract_VM_Version::_cpu_desc[CPU_DETAILED_DESC_BUF_SIZE] = {0};

int Abstract_VM_Version::number_of_threads(void) {
  assert(_initialized, "should be initialized");
  return _no_of_threads;
}

int Abstract_VM_Version::number_of_cores(void) {
  assert(_initialized, "should be initialized");
  return _no_of_cores;
}

int Abstract_VM_Version::number_of_sockets(void) {
  assert(_initialized, "should be initialized");
  return _no_of_sockets;
}

const char* Abstract_VM_Version::cpu_name(void) {
  assert(_initialized, "should be initialized");
  char* tmp = NEW_C_HEAP_ARRAY_RETURN_NULL(char, CPU_TYPE_DESC_BUF_SIZE, mtTracing);
  if (nullptr == tmp) {
    return nullptr;
  }
  strncpy(tmp, _cpu_name, CPU_TYPE_DESC_BUF_SIZE);
  return tmp;
}

const char* Abstract_VM_Version::cpu_description(void) {
  assert(_initialized, "should be initialized");
  char* tmp = NEW_C_HEAP_ARRAY_RETURN_NULL(char, CPU_DETAILED_DESC_BUF_SIZE, mtTracing);
  if (nullptr == tmp) {
    return nullptr;
  }
  strncpy(tmp, _cpu_desc, CPU_DETAILED_DESC_BUF_SIZE);
  return tmp;
}
