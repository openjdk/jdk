/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/register.hpp"
#include "runtime/os.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/vm_version.hpp"

#define read_csr(csr)                                           \
({                                                              \
        register unsigned long __v;                             \
        __asm__ __volatile__ ("csrr %0, %1"                     \
                              : "=r" (__v)                      \
                              : "i" (csr)                       \
                              : "memory");                      \
        __v;                                                    \
})

uint32_t VM_Version::get_current_vector_length() {
  assert(_cpu_features.ext_v, "should not call this");
  return (uint32_t)read_csr(CSR_VLENB);
}

VM_Version::VM_MODE VM_Version::get_satp_mode() {
  if (!strcmp(_vm_mode, "sv39")) {
    return VM_SV39;
  } else if (!strcmp(_vm_mode, "sv48")) {
    return VM_SV48;
  } else if (!strcmp(_vm_mode, "sv57")) {
    return VM_SV57;
  } else if (!strcmp(_vm_mode, "sv64")) {
    return VM_SV64;
  } else {
    return VM_MBARE;
  }
}

void VM_Version::get_os_cpu_info() {
  /**
   * cpuinfo in riscv would like:
   * processor       : 1
   * hard            : 1
   * isa             : rv64imafdc
   * mmu             : sv39
   * uarch           : sifive,u74-mc
   */
  if (FILE *f = fopen("/proc/cpuinfo", "r")) {
    char buf[512], *p;
    while (fgets(buf, sizeof (buf), f) != nullptr) {
      if ((p = strchr(buf, ':')) != nullptr) {
        if (strncmp(buf, "mmu", sizeof "mmu" - 1) == 0) {
          if (_vm_mode[0] != '\0') {
            continue;
          }
          char* vm_mode = os::strdup(p + 2);
          vm_mode[strcspn(vm_mode, "\n")] = '\0';
          _vm_mode = vm_mode;
        } else if (strncmp(buf, "isa", sizeof "isa" - 1) == 0) {
          if (_isa[0]!= '\0') {
            continue;
          }
          char* isa = os::strdup(p + 2);
          isa[strcspn(isa, "\n")] = '\0';
          _isa = isa;
          get_isa();
        } else if (strncmp(buf, "uarch", sizeof "uarch" - 1) == 0) {
          char* uarch = os::strdup(p + 2);
          uarch[strcspn(uarch, "\n")] = '\0';
          _uarch = uarch;
          break;
        }
      }
    }
    fclose(f);
  }
}

void VM_Version::get_isa() {
  char isa_buf[500];
  strcpy(isa_buf, _isa);
  char* saved_ptr;
  char* isa_ext = strtok_r(isa_buf, "_", &saved_ptr);
  while (isa_ext != NULL) {
    // special case for rv64* string
    if (strncmp(isa_ext, "rv64", sizeof "rv64" - 1) == 0) {
      const char* base_ext = strdup(isa_ext + 4); // skip "rv64"
      int i = 0;
      while (base_ext[i] != '\0') {
        const char ch = base_ext[i++];
        if (ch == 'i') {
          _cpu_features.ext_i = true;
        } else if (ch == 'm') {
          _cpu_features.ext_m = true;
        } else if (ch == 'a') {
          _cpu_features.ext_a = true;
        } else if (ch == 'f') {
          _cpu_features.ext_f = true;
        } else if (ch == 'd') {
          _cpu_features.ext_d = true;
        } else if (ch == 'c') {
          _cpu_features.ext_c = true;
        } else if (ch == 'v') {
          _cpu_features.ext_v = true;
        }
      }
    } else if (strncmp(isa_ext, "zba", sizeof "zba" - 1) == 0) {
      _cpu_features.ext_zba = true;
    } else if (strncmp(isa_ext, "zbb", sizeof "zbb" - 1) == 0) {
      _cpu_features.ext_zbb = true;
    } else if (strncmp(isa_ext, "zbs", sizeof "zbs" - 1) == 0) {
      _cpu_features.ext_zbs = true;
    } else if (strncmp(isa_ext, "zic64b", sizeof "zic64b" - 1) == 0) {
      _cpu_features.ext_zic64b = true;
    } else if (strncmp(isa_ext, "zicbom", sizeof "zicbom" - 1) == 0) {
      _cpu_features.ext_zicbom = true;
    } else if (strncmp(isa_ext, "zicbop", sizeof "zicbop" - 1) == 0) {
      _cpu_features.ext_zicbop = true;
    } else if (strncmp(isa_ext, "zicboz", sizeof "zicboz" - 1) == 0) {
      _cpu_features.ext_zicboz = true;
    } else if (strncmp(isa_ext, "zfhmin", sizeof "zfhmin" - 1) == 0) {
      _cpu_features.ext_zfhmin = true;
    }

    // read next isa extension string, if any
    isa_ext = strtok_r(NULL, "_", &saved_ptr);
  }
}
