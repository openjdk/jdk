/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_VM_VERSION_RISCV_HPP
#define CPU_RISCV_VM_VERSION_RISCV_HPP

#include "runtime/abstract_vm_version.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/sizes.hpp"

class VM_Version : public Abstract_VM_Version {
public:
  // Initialization
  static void initialize();

  constexpr static bool supports_stack_watermark_barrier() { return true; }

  static bool is_checkvext_fault(address pc) {
    return pc != NULL && pc == _checkvext_fault_pc;
  }

  static address continuation_for_checkvext_fault(address pc) {
    assert(_checkvext_continuation_pc != NULL, "not initialized");
    return _checkvext_continuation_pc;
  }

  static address _checkvext_fault_pc;
  static address _checkvext_continuation_pc;

protected:
  static int _initial_vector_length;
  static void get_processor_features();

#ifdef COMPILER2
private:
  static void get_c2_processor_features();
#endif // COMPILER2
};

#endif // CPU_RISCV_VM_VERSION_RISCV_HPP
