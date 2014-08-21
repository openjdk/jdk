/*
 * Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/os.hpp"
#include "vm_version_sparc.hpp"

# include <sys/auxv.h>
# include <sys/auxv_SPARC.h>
# include <sys/systeminfo.h>
# include <kstat.h>

// We need to keep these here as long as we have to build on Solaris
// versions before 10.
#ifndef SI_ARCHITECTURE_32
#define SI_ARCHITECTURE_32      516     /* basic 32-bit SI_ARCHITECTURE */
#endif

#ifndef SI_ARCHITECTURE_64
#define SI_ARCHITECTURE_64      517     /* basic 64-bit SI_ARCHITECTURE */
#endif

static void do_sysinfo(int si, const char* string, int* features, int mask) {
  char   tmp;
  size_t bufsize = sysinfo(si, &tmp, 1);

  // All SI defines used below must be supported.
  guarantee(bufsize != -1, "must be supported");

  char* buf = (char*) os::malloc(bufsize, mtInternal);

  if (buf == NULL)
    return;

  if (sysinfo(si, buf, bufsize) == bufsize) {
    // Compare the string.
    if (strcmp(buf, string) == 0) {
      *features |= mask;
    }
  }

  os::free(buf);
}

int VM_Version::platform_features(int features) {
  assert(os::Solaris::supports_getisax(), "getisax() must be available");

  // Check 32-bit architecture.
  do_sysinfo(SI_ARCHITECTURE_32, "sparc", &features, v8_instructions_m);

  // Check 64-bit architecture.
  do_sysinfo(SI_ARCHITECTURE_64, "sparcv9", &features, generic_v9_m);

  // Extract valid instruction set extensions.
  uint_t avs[2];
  uint_t avn = os::Solaris::getisax(avs, 2);
  assert(avn <= 2, "should return two or less av's");
  uint_t av = avs[0];

#ifndef PRODUCT
  if (PrintMiscellaneous && Verbose) {
    tty->print("getisax(2) returned: " PTR32_FORMAT, av);
    if (avn > 1) {
      tty->print(", " PTR32_FORMAT, avs[1]);
    }
    tty->cr();
  }
#endif

  if (av & AV_SPARC_MUL32)  features |= hardware_mul32_m;
  if (av & AV_SPARC_DIV32)  features |= hardware_div32_m;
  if (av & AV_SPARC_FSMULD) features |= hardware_fsmuld_m;
  if (av & AV_SPARC_V8PLUS) features |= v9_instructions_m;
  if (av & AV_SPARC_POPC)   features |= hardware_popc_m;
  if (av & AV_SPARC_VIS)    features |= vis1_instructions_m;
  if (av & AV_SPARC_VIS2)   features |= vis2_instructions_m;
  if (avn > 1) {
    uint_t av2 = avs[1];
#ifndef AV2_SPARC_SPARC5
#define AV2_SPARC_SPARC5 0x00000008 /* The 29 new fp and sub instructions */
#endif
    if (av2 & AV2_SPARC_SPARC5)       features |= sparc5_instructions_m;
  }

  // We only build on Solaris 10 and up, but some of the values below
  // are not defined on all versions of Solaris 10, so we define them,
  // if necessary.
#ifndef AV_SPARC_ASI_BLK_INIT
#define AV_SPARC_ASI_BLK_INIT 0x0080  /* ASI_BLK_INIT_xxx ASI */
#endif
  if (av & AV_SPARC_ASI_BLK_INIT) features |= blk_init_instructions_m;

#ifndef AV_SPARC_FMAF
#define AV_SPARC_FMAF 0x0100        /* Fused Multiply-Add */
#endif
  if (av & AV_SPARC_FMAF)         features |= fmaf_instructions_m;

#ifndef AV_SPARC_FMAU
#define AV_SPARC_FMAU    0x0200  /* Unfused Multiply-Add */
#endif
  if (av & AV_SPARC_FMAU)         features |= fmau_instructions_m;

#ifndef AV_SPARC_VIS3
#define AV_SPARC_VIS3    0x0400  /* VIS3 instruction set extensions */
#endif
  if (av & AV_SPARC_VIS3)         features |= vis3_instructions_m;

#ifndef AV_SPARC_CBCOND
#define AV_SPARC_CBCOND 0x10000000  /* compare and branch instrs supported */
#endif
  if (av & AV_SPARC_CBCOND)       features |= cbcond_instructions_m;

#ifndef AV_SPARC_AES
#define AV_SPARC_AES 0x00020000  /* aes instrs supported */
#endif
  if (av & AV_SPARC_AES)       features |= aes_instructions_m;

#ifndef AV_SPARC_SHA1
#define AV_SPARC_SHA1   0x00400000  /* sha1 instruction supported */
#endif
  if (av & AV_SPARC_SHA1)         features |= sha1_instruction_m;

#ifndef AV_SPARC_SHA256
#define AV_SPARC_SHA256 0x00800000  /* sha256 instruction supported */
#endif
  if (av & AV_SPARC_SHA256)       features |= sha256_instruction_m;

#ifndef AV_SPARC_SHA512
#define AV_SPARC_SHA512 0x01000000  /* sha512 instruction supported */
#endif
  if (av & AV_SPARC_SHA512)       features |= sha512_instruction_m;

  // Determine the machine type.
  do_sysinfo(SI_MACHINE, "sun4v", &features, sun4v_m);

  {
    // Using kstat to determine the machine type.
    kstat_ctl_t* kc = kstat_open();
    kstat_t* ksp = kstat_lookup(kc, (char*)"cpu_info", -1, NULL);
    const char* implementation = "UNKNOWN";
    if (ksp != NULL) {
      if (kstat_read(kc, ksp, NULL) != -1 && ksp->ks_data != NULL) {
        kstat_named_t* knm = (kstat_named_t *)ksp->ks_data;
        for (int i = 0; i < ksp->ks_ndata; i++) {
          if (strcmp((const char*)&(knm[i].name),"implementation") == 0) {
            implementation = KSTAT_NAMED_STR_PTR(&knm[i]);
#ifndef PRODUCT
            if (PrintMiscellaneous && Verbose) {
              tty->print_cr("cpu_info.implementation: %s", implementation);
            }
#endif
            // Convert to UPPER case before compare.
            char* impl = os::strdup_check_oom(implementation);

            for (int i = 0; impl[i] != 0; i++)
              impl[i] = (char)toupper((uint)impl[i]);

            if (strstr(impl, "SPARC64") != NULL) {
              features |= sparc64_family_m;
            } else if (strstr(impl, "SPARC-M") != NULL) {
              // M-series SPARC is based on T-series.
              features |= (M_family_m | T_family_m);
            } else if (strstr(impl, "SPARC-T") != NULL) {
              features |= T_family_m;
              if (strstr(impl, "SPARC-T1") != NULL) {
                features |= T1_model_m;
              }
            } else {
              if (strstr(impl, "SPARC") == NULL) {
#ifndef PRODUCT
                // kstat on Solaris 8 virtual machines (branded zones)
                // returns "(unsupported)" implementation. Solaris 8 is not
                // supported anymore, but include this check to be on the
                // safe side.
                warning("kstat cpu_info implementation = '%s', assume generic SPARC", impl);
#endif
                implementation = "SPARC";
              }
            }
            os::free((void*)impl);
            break;
          }
        } // for(
      }
    }
    assert(strcmp(implementation, "UNKNOWN") != 0,
           "unknown cpu info (changed kstat interface?)");
    kstat_close(kc);
  }

  return features;
}
