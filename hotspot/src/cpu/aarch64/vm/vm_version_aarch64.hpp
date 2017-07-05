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
  friend class JVMCIVMStructs;

protected:
  static int _cpu;
  static int _model;
  static int _model2;
  static int _variant;
  static int _revision;
  static int _stepping;

  static void get_processor_features();

public:
  // Initialization
  static void initialize();

  // Asserts
  static void assert_is_initialized() {
  }

  enum Family {
    CPU_ARM       = 'A',
    CPU_BROADCOM  = 'B',
    CPU_CAVIUM    = 'C',
    CPU_DEC       = 'D',
    CPU_INFINEON  = 'I',
    CPU_MOTOROLA  = 'M',
    CPU_NVIDIA    = 'N',
    CPU_AMCC      = 'P',
    CPU_QUALCOM   = 'Q',
    CPU_MARVELL   = 'V',
    CPU_INTEL     = 'i',
  };

  enum Feature_Flag {
    CPU_FP           = (1<<0),
    CPU_ASIMD        = (1<<1),
    CPU_EVTSTRM      = (1<<2),
    CPU_AES          = (1<<3),
    CPU_PMULL        = (1<<4),
    CPU_SHA1         = (1<<5),
    CPU_SHA2         = (1<<6),
    CPU_CRC32        = (1<<7),
    CPU_A53MAC       = (1 << 30),
    CPU_DMB_ATOMICS  = (1 << 31),
  };

  static int cpu_family()                     { return _cpu; }
  static int cpu_model()                      { return _model; }
  static int cpu_model2()                     { return _model2; }
  static int cpu_variant()                    { return _variant; }
  static int cpu_revision()                   { return _revision; }
};

#endif // CPU_AARCH64_VM_VM_VERSION_AARCH64_HPP
