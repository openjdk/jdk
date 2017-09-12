/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016 SAP SE. All rights reserved.
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
#include "register_s390.hpp"


const int ConcreteRegisterImpl::max_gpr = RegisterImpl::number_of_registers * 2;
const int ConcreteRegisterImpl::max_fpr = ConcreteRegisterImpl::max_gpr +
                                          FloatRegisterImpl::number_of_registers * 2;

const char* RegisterImpl::name() const {
  const char* names[number_of_registers] = {
    "Z_R0",  "Z_R1",  "Z_R2",  "Z_R3",  "Z_R4",  "Z_R5",  "Z_R6",  "Z_R7",
    "Z_R8",  "Z_R9",  "Z_R10", "Z_R11", "Z_R12", "Z_R13", "Z_R14", "Z_R15"
  };
  return is_valid() ? names[encoding()] : "noreg";
}

const char* FloatRegisterImpl::name() const {
  const char* names[number_of_registers] = {
    "Z_F0",  "Z_F1",   "Z_F2",  "Z_F3",   "Z_F4",  "Z_F5",   "Z_F6",  "Z_F7",   "Z_F8",  "Z_F9",
    "Z_F10", "Z_F11",  "Z_F12", "Z_F13",  "Z_F14", "Z_F15"
  };
  return is_valid() ? names[encoding()] : "fnoreg";
}
