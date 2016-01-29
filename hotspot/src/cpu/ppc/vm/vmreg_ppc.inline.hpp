/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2013 SAP SE. All rights reserved.
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

#ifndef CPU_PPC_VM_VMREG_PPC_INLINE_HPP
#define CPU_PPC_VM_VMREG_PPC_INLINE_HPP

inline VMReg RegisterImpl::as_VMReg() {
  if (this == noreg) return VMRegImpl::Bad();
  return VMRegImpl::as_VMReg(encoding() << 1);
}

// Since we don't have two halfs here, don't multiply by 2.
inline VMReg ConditionRegisterImpl::as_VMReg() {
  return VMRegImpl::as_VMReg((encoding()) + ConcreteRegisterImpl::max_fpr);
}

inline VMReg FloatRegisterImpl::as_VMReg() {
  return VMRegImpl::as_VMReg((encoding() << 1) + ConcreteRegisterImpl::max_gpr);
}

inline VMReg SpecialRegisterImpl::as_VMReg() {
  return VMRegImpl::as_VMReg((encoding()) + ConcreteRegisterImpl::max_cnd);
}


#endif // CPU_PPC_VM_VMREG_PPC_INLINE_HPP
