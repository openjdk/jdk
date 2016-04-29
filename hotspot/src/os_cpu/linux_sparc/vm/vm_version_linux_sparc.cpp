/*
 * Copyright (c) 2006, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "vm_version_sparc.hpp"

static bool cpuinfo_field_contains(const char* field, const char* value) {
  char line[1024];
  bool rv = false;

  FILE* fp = fopen("/proc/cpuinfo", "r");
  if (fp == NULL) {
    return rv;
  }

  while (fgets(line, sizeof(line), fp) != NULL) {
    assert(strlen(line) < sizeof(line) - 1, "buffer line[1024] is too small.");
    if (strncmp(line, field, strlen(field)) == 0) {
      if (strstr(line, value) != NULL) {
        rv = true;
      }
      break;
    }
  }

  fclose(fp);
  return rv;
}

static bool detect_niagara() {
  return cpuinfo_field_contains("cpu", "Niagara");
}

static bool detect_M_family() {
  return cpuinfo_field_contains("cpu", "SPARC-M");
}

static bool detect_blkinit() {
  return cpuinfo_field_contains("cpucaps", "blkinit");
}

int VM_Version::platform_features(int features) {
  // Default to generic v9
  features = generic_v9_m;

  if (detect_niagara()) {
    log_info(os, cpu)("Detected Linux on Niagara");
    features = niagara1_m | T_family_m;
  }

  if (detect_M_family()) {
    log_info(os, cpu)("Detected Linux on M family");
    features = sun4v_m | generic_v9_m | M_family_m | T_family_m;
  }

  if (detect_blkinit()) {
    features |= blk_init_instructions_m;
  }

  return features;
}
