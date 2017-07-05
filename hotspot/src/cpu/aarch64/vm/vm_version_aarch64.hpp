/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

#ifndef CPU_AARCH64_VM_VM_VERSION_AARCH64_HPP
#define CPU_AARCH64_VM_VM_VERSION_AARCH64_HPP

#include "runtime/globals_extension.hpp"
#include "runtime/vm_version.hpp"

class VM_Version : public Abstract_VM_Version {
public:
protected:
  static int _cpu;
  static int _model;
  static int _stepping;
  static int _cpuFeatures;     // features returned by the "cpuid" instruction
                               // 0 if this instruction is not available
  static const char* _features_str;

  static void get_processor_features();

public:
  // Initialization
  static void initialize();

  // Asserts
  static void assert_is_initialized() {
  }

  static const char* cpu_features()           { return _features_str; }

};

#endif // CPU_AARCH64_VM_VM_VERSION_AARCH64_HPP
