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

const Register::RegisterImpl           all_RegisterImpls     [Register::number_of_registers      + 1];
const FloatRegister::FloatRegisterImpl all_FloatRegisterImpls[FloatRegister::number_of_registers + 1];
const XMMRegister::XMMRegisterImpl     all_XMMRegisterImpls  [XMMRegister::number_of_registers   + 1];
const KRegister::KRegisterImpl         all_KRegisterImpls    [KRegister::number_of_registers     + 1];

const char * Register::RegisterImpl::name() const {
  static const char *const names[number_of_registers] = {
#ifdef _LP64
    "rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi",
    "r8",  "r9",  "r10", "r11", "r12", "r13", "r14", "r15",
    "r16", "r17", "r18", "r19", "r20", "r21", "r22", "r23",
    "r24", "r25", "r26", "r27", "r28", "r29", "r30", "r31"
#else
    "eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi"
#endif // _LP64
  };
  return is_valid() ? names[encoding()] : "noreg";
}

const char* FloatRegister::FloatRegisterImpl::name() const {
  static const char *const names[number_of_registers] = {
    "st0", "st1", "st2", "st3", "st4", "st5", "st6", "st7"
  };
  return is_valid() ? names[encoding()] : "fnoreg";
}

const char* XMMRegister::XMMRegisterImpl::name() const {
  static const char *const names[number_of_registers] = {
    "xmm0",    "xmm1",  "xmm2",  "xmm3",  "xmm4",  "xmm5",  "xmm6",  "xmm7"
#ifdef _LP64
    ,"xmm8",   "xmm9",  "xmm10", "xmm11", "xmm12", "xmm13", "xmm14", "xmm15"
    ,"xmm16",  "xmm17", "xmm18", "xmm19", "xmm20", "xmm21", "xmm22", "xmm23"
    ,"xmm24",  "xmm25", "xmm26", "xmm27", "xmm28", "xmm29", "xmm30", "xmm31"
#endif // _LP64
  };
  return is_valid() ? names[encoding()] : "xnoreg";
}

const char* KRegister::KRegisterImpl::name() const {
  const char *const names[number_of_registers] = {
    "k0", "k1", "k2", "k3", "k4", "k5", "k6", "k7"
  };
  return is_valid() ? names[encoding()] : "knoreg";
}
