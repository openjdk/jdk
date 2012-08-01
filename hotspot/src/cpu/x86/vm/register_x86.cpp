/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "register_x86.hpp"

#ifndef AMD64
const int ConcreteRegisterImpl::max_gpr = RegisterImpl::number_of_registers;
#else
const int ConcreteRegisterImpl::max_gpr = RegisterImpl::number_of_registers << 1;
#endif // AMD64


const int ConcreteRegisterImpl::max_fpr = ConcreteRegisterImpl::max_gpr +
                                                                 2 * FloatRegisterImpl::number_of_registers;
const int ConcreteRegisterImpl::max_xmm = ConcreteRegisterImpl::max_fpr +
                                                                 8 * XMMRegisterImpl::number_of_registers;
const char* RegisterImpl::name() const {
  const char* names[number_of_registers] = {
#ifndef AMD64
    "eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi"
#else
    "rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi",
    "r8",  "r9",  "r10", "r11", "r12", "r13", "r14", "r15"
#endif // AMD64
  };
  return is_valid() ? names[encoding()] : "noreg";
}

const char* FloatRegisterImpl::name() const {
  const char* names[number_of_registers] = {
    "st0", "st1", "st2", "st3", "st4", "st5", "st6", "st7"
  };
  return is_valid() ? names[encoding()] : "noreg";
}

const char* XMMRegisterImpl::name() const {
  const char* names[number_of_registers] = {
    "xmm0","xmm1","xmm2","xmm3","xmm4","xmm5","xmm6","xmm7"
#ifdef AMD64
    ,"xmm8",  "xmm9", "xmm10", "xmm11", "xmm12", "xmm13", "xmm14", "xmm15"
#endif // AMD64
  };
  return is_valid() ? names[encoding()] : "xnoreg";
}
