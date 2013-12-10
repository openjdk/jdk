/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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
#include "register_ppc.hpp"

const int ConcreteRegisterImpl::max_gpr = RegisterImpl::number_of_registers * 2;
const int ConcreteRegisterImpl::max_fpr = ConcreteRegisterImpl::max_gpr +
                                          FloatRegisterImpl::number_of_registers * 2;
const int ConcreteRegisterImpl::max_cnd = ConcreteRegisterImpl::max_fpr +
                                          ConditionRegisterImpl::number_of_registers;

const char* RegisterImpl::name() const {
  const char* names[number_of_registers] = {
    "R0",  "R1",  "R2",  "R3",  "R4",  "R5",  "R6",  "R7",
    "R8",  "R9",  "R10", "R11", "R12", "R13", "R14", "R15",
    "R16", "R17", "R18", "R19", "R20", "R21", "R22", "R23",
    "R24", "R25", "R26", "R27", "R28", "R29", "R30", "R31"
  };
  return is_valid() ? names[encoding()] : "noreg";
}

const char* ConditionRegisterImpl::name() const {
  const char* names[number_of_registers] = {
    "CR0",  "CR1",  "CR2",  "CR3",  "CR4",  "CR5",  "CR6",  "CR7"
  };
  return is_valid() ? names[encoding()] : "cnoreg";
}

const char* FloatRegisterImpl::name() const {
  const char* names[number_of_registers] = {
    "F0",  "F1",  "F2",  "F3",  "F4",  "F5",  "F6",  "F7",
    "F8",  "F9",  "F10", "F11", "F12", "F13", "F14", "F15",
    "F16", "F17", "F18", "F19", "F20", "F21", "F22", "F23",
    "F24", "F25", "F26", "F27", "F28", "F29", "F30", "F31"
  };
  return is_valid() ? names[encoding()] : "fnoreg";
}

const char* SpecialRegisterImpl::name() const {
  const char* names[number_of_registers] = {
    "SR_XER", "SR_LR", "SR_CTR", "SR_VRSAVE", "SR_SPEFSCR", "SR_PPR"
  };
  return is_valid() ? names[encoding()] : "snoreg";
}

const char* VectorRegisterImpl::name() const {
  const char* names[number_of_registers] = {
    "VR0",  "VR1",  "VR2",  "VR3",  "VR4",  "VR5",  "VR6",  "VR7",
    "VR8",  "VR9",  "VR10", "VR11", "VR12", "VR13", "VR14", "VR15",
    "VR16", "VR17", "VR18", "VR19", "VR20", "VR21", "VR22", "VR23",
    "VR24", "VR25", "VR26", "VR27", "VR28", "VR29", "VR30", "VR31"
  };
  return is_valid() ? names[encoding()] : "vnoreg";
}
