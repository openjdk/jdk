/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_vm_version_solaris_sparc.cpp.incl"

# include <sys/systeminfo.h>

int VM_Version::platform_features(int features) {
  // We determine what sort of hardware we have via sysinfo(SI_ISALIST, ...).
  // This isn't the best of all possible ways because there's not enough
  // detail in the isa list it returns, but it's a bit less arcane than
  // generating assembly code and an illegal instruction handler.  We used
  // to generate a getpsr trap, but that's even more arcane.
  //
  // Another possibility would be to use sysinfo(SI_PLATFORM, ...), but
  // that would require more knowledge here than is wise.

  // isalist spec via 'man isalist' as of 01-Aug-2001

  char   tmp;
  size_t bufsize  = sysinfo(SI_ISALIST, &tmp, 1);
  char*  buf      = (char*)malloc(bufsize);

  if (buf != NULL) {
    if (sysinfo(SI_ISALIST, buf, bufsize) == bufsize) {
      // Figure out what kind of sparc we have
      char *sparc_string = strstr(buf, "sparc");
      if (sparc_string != NULL) {            features |= v8_instructions_m;
        if (sparc_string[5] == 'v') {
          if (sparc_string[6] == '8') {
            if (sparc_string[7] == '-')      features |= hardware_int_muldiv_m;
            else if (sparc_string[7] == 'p') features |= generic_v9_m;
            else                      features |= generic_v8_m;
          } else if (sparc_string[6] == '9') features |= generic_v9_m;
        }
      }

      // Check for visualization instructions
      char *vis = strstr(buf, "vis");
      if (vis != NULL) {              features |= vis1_instructions_m;
        if (vis[3] == '2')            features |= vis2_instructions_m;
      }
    }
    free(buf);
  }

  bufsize = sysinfo(SI_MACHINE, &tmp, 1);
  buf     = (char*)malloc(bufsize);

  if (buf != NULL) {
    if (sysinfo(SI_MACHINE, buf, bufsize) == bufsize) {
      if (strstr(buf, "sun4v") != NULL) {
        features |= sun4v_m;
      }
    }
    free(buf);
  }

  return features;
}
