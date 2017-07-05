/*
 * Copyright (c) 2006, 2008, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_vm_version_linux_sparc.cpp.incl"

static bool detect_niagara() {
  char cpu[128];
  bool rv = false;

  FILE* fp = fopen("/proc/cpuinfo", "r");
  if (fp == NULL) {
    return rv;
  }

  while (!feof(fp)) {
    if (fscanf(fp, "cpu\t\t: %100[^\n]", &cpu) == 1) {
      if (strstr(cpu, "Niagara") != NULL) {
        rv = true;
      }
      break;
    }
  }

  fclose(fp);

  return rv;
}

int VM_Version::platform_features(int features) {
  // Default to generic v9
  features = generic_v9_m;

  if (detect_niagara()) {
    NOT_PRODUCT(if (PrintMiscellaneous && Verbose) tty->print_cr("Detected Linux on Niagara");)
    features = niagara1_m;
  }

  return features;
}
