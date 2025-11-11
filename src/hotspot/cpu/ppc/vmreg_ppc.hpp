/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_PPC_VMREG_PPC_HPP
#define CPU_PPC_VMREG_PPC_HPP

inline bool is_Register() {
  return (unsigned int)value() < (unsigned int)ConcreteRegisterImpl::max_gpr;
}

inline bool is_FloatRegister() {
  return value() >= ConcreteRegisterImpl::max_gpr &&
         value() < ConcreteRegisterImpl::max_fpr;
}

inline bool is_VectorRegister() {
  return value() >= ConcreteRegisterImpl::max_fpr &&
         value() < ConcreteRegisterImpl::max_vr;
}

inline bool is_ConditionRegister() {
  return value() >= ConcreteRegisterImpl::max_vr &&
         value() < ConcreteRegisterImpl::max_cnd;
}

inline bool is_SpecialRegister() {
  return value() >= ConcreteRegisterImpl::max_cnd &&
         value() < ConcreteRegisterImpl::max_spr;
}

inline Register as_Register() {
  assert(is_Register() && is_even(value()), "even-aligned GPR name");
  return ::as_Register(value()>>1);
}

inline FloatRegister as_FloatRegister() {
  assert(is_FloatRegister() && is_even(value()), "must be");
  return ::as_FloatRegister((value() - ConcreteRegisterImpl::max_gpr) >> 1);
}

inline VectorRegister as_VectorRegister() {
  assert(is_VectorRegister(), "must be");
  return ::as_VectorRegister((value() - ConcreteRegisterImpl::max_fpr) >> 2);
}

inline bool is_concrete() {
  assert(is_reg(), "must be");
  if (is_Register() || is_FloatRegister()) return is_even(value());
  if (is_VectorRegister()) {
    int base = value() - ConcreteRegisterImpl::max_fpr;
    return (base & 3) == 0;
  }
  return true;
}

#endif // CPU_PPC_VMREG_PPC_HPP
