/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2025 SAP SE. All rights reserved.
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

#ifndef CPU_PPC_VM_VERSION_PPC_HPP
#define CPU_PPC_VM_VERSION_PPC_HPP

#include "runtime/abstract_vm_version.hpp"
#include "runtime/globals_extension.hpp"

class VM_Version: public Abstract_VM_Version {
protected:
  enum Feature_Flag {
    mfdscr,
    darn,
    brw,
    num_features // last entry to count features
  };
  enum Feature_Flag_Set {
    unknown_m             = 0,
    mfdscr_m              = (1 << mfdscr ),
    darn_m                = (1 << darn   ),
    brw_m                 = (1 << brw    ),
    all_features_m        = (unsigned long)-1
  };

  static bool _is_determine_features_test_running;

  static void print_features();
  static void determine_features(); // also measures cache line size
  static void config_dscr(); // Power 8: Configure Data Stream Control Register.

public:
  // Initialization
  static void initialize();
  static void check_virtualizations();

  // Override Abstract_VM_Version implementation
  static void print_platform_virtualization_info(outputStream*);

  // PPC64 supports fast class initialization checks
  static bool supports_fast_class_init_checks() { return true; }
  constexpr static bool supports_stack_watermark_barrier() { return true; }
  constexpr static bool supports_recursive_lightweight_locking() { return true; }
  constexpr static bool supports_secondary_supers_table() { return true; }

  static bool supports_float16() { return PowerArchitecturePPC64 >= 9; }

  static bool is_determine_features_test_running() { return _is_determine_features_test_running; }
  // CPU instruction support
  static bool has_mfdscr() { return (_features & mfdscr_m) != 0; } // Power8, but may be unavailable (QEMU)
  static bool has_darn()   { return (_features & darn_m) != 0; }
  static bool has_brw()    { return (_features & brw_m) != 0; }

  // Assembler testing
  static void allow_all();
  static void revert();

  // POWER 8: DSCR current value.
  static uint64_t _dscr_val;

  static void initialize_cpu_information(void);
};

#endif // CPU_PPC_VM_VERSION_PPC_HPP
