/*
 * Copyright (c) 2006, 2010, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_vm_version_solaris_sparc.cpp.incl"

# include <sys/auxv.h>
# include <sys/auxv_SPARC.h>
# include <sys/systeminfo.h>

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

  char* buf = (char*) malloc(bufsize);

  if (buf == NULL)
    return;

  if (sysinfo(si, buf, bufsize) == bufsize) {
    // Compare the string.
    if (strcmp(buf, string) == 0) {
      *features |= mask;
    }
  }

  free(buf);
}

int VM_Version::platform_features(int features) {
  // getisax(2), SI_ARCHITECTURE_32, and SI_ARCHITECTURE_64 are
  // supported on Solaris 10 and later.
  if (os::Solaris::supports_getisax()) {

    // Check 32-bit architecture.
    do_sysinfo(SI_ARCHITECTURE_32, "sparc", &features, v8_instructions_m);

    // Check 64-bit architecture.
    do_sysinfo(SI_ARCHITECTURE_64, "sparcv9", &features, generic_v9_m);

    // Extract valid instruction set extensions.
    uint_t av;
    uint_t avn = os::Solaris::getisax(&av, 1);
    assert(avn == 1, "should only return one av");

#ifndef PRODUCT
    if (PrintMiscellaneous && Verbose)
      tty->print_cr("getisax(2) returned: " PTR32_FORMAT, av);
#endif

    if (av & AV_SPARC_MUL32)  features |= hardware_mul32_m;
    if (av & AV_SPARC_DIV32)  features |= hardware_div32_m;
    if (av & AV_SPARC_FSMULD) features |= hardware_fsmuld_m;
    if (av & AV_SPARC_V8PLUS) features |= v9_instructions_m;
    if (av & AV_SPARC_POPC)   features |= hardware_popc_m;
    if (av & AV_SPARC_VIS)    features |= vis1_instructions_m;
    if (av & AV_SPARC_VIS2)   features |= vis2_instructions_m;

    // Next values are not defined before Solaris 10
    // but Solaris 8 is used for jdk6 update builds.
#ifndef AV_SPARC_ASI_BLK_INIT
#define AV_SPARC_ASI_BLK_INIT 0x0080  /* ASI_BLK_INIT_xxx ASI */
#endif
#ifndef AV_SPARC_FMAF
#define AV_SPARC_FMAF 0x0100  /* Sparc64 Fused Multiply-Add */
#endif
    if (av & AV_SPARC_ASI_BLK_INIT) features |= blk_init_instructions_m;
    if (av & AV_SPARC_FMAF)         features |= fmaf_instructions_m;
  } else {
    // getisax(2) failed, use the old legacy code.
#ifndef PRODUCT
    if (PrintMiscellaneous && Verbose)
      tty->print_cr("getisax(2) is not supported.");
#endif

    char   tmp;
    size_t bufsize = sysinfo(SI_ISALIST, &tmp, 1);
    char*  buf     = (char*) malloc(bufsize);

    if (buf != NULL) {
      if (sysinfo(SI_ISALIST, buf, bufsize) == bufsize) {
        // Figure out what kind of sparc we have
        char *sparc_string = strstr(buf, "sparc");
        if (sparc_string != NULL) {              features |= v8_instructions_m;
          if (sparc_string[5] == 'v') {
            if (sparc_string[6] == '8') {
              if (sparc_string[7] == '-') {      features |= hardware_mul32_m;
                                                 features |= hardware_div32_m;
              } else if (sparc_string[7] == 'p') features |= generic_v9_m;
              else                               features |= generic_v8_m;
            } else if (sparc_string[6] == '9')   features |= generic_v9_m;
          }
        }

        // Check for visualization instructions
        char *vis = strstr(buf, "vis");
        if (vis != NULL) {                       features |= vis1_instructions_m;
          if (vis[3] == '2')                     features |= vis2_instructions_m;
        }
      }
      free(buf);
    }
  }

  // Determine the machine type.
  do_sysinfo(SI_MACHINE, "sun4v", &features, sun4v_m);

  return features;
}
