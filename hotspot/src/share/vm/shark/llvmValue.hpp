/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2008, 2009 Red Hat, Inc.
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

#ifndef SHARE_VM_SHARK_LLVMVALUE_HPP
#define SHARE_VM_SHARK_LLVMVALUE_HPP

#include "shark/llvmHeaders.hpp"
#include "shark/sharkContext.hpp"
#include "shark/sharkType.hpp"

class LLVMValue : public AllStatic {
 public:
  static llvm::ConstantInt* jbyte_constant(jbyte value)
  {
    return llvm::ConstantInt::get(SharkType::jbyte_type(), value, true);
  }
  static llvm::ConstantInt* jint_constant(jint value)
  {
    return llvm::ConstantInt::get(SharkType::jint_type(), value, true);
  }
  static llvm::ConstantInt* jlong_constant(jlong value)
  {
    return llvm::ConstantInt::get(SharkType::jlong_type(), value, true);
  }
  static llvm::ConstantFP* jfloat_constant(jfloat value)
  {
    return llvm::ConstantFP::get(SharkContext::current(), llvm::APFloat(value));
  }
  static llvm::ConstantFP* jdouble_constant(jdouble value)
  {
    return llvm::ConstantFP::get(SharkContext::current(), llvm::APFloat(value));
  }
  static llvm::ConstantPointerNull* null()
  {
    return llvm::ConstantPointerNull::get(SharkType::oop_type());
  }

 public:
  static llvm::ConstantInt* bit_constant(int value)
  {
    return llvm::ConstantInt::get(SharkType::bit_type(), value, false);
  }
  static llvm::ConstantInt* intptr_constant(intptr_t value)
  {
    return llvm::ConstantInt::get(SharkType::intptr_type(), value, false);
  }
};

#endif // SHARE_VM_SHARK_LLVMVALUE_HPP
