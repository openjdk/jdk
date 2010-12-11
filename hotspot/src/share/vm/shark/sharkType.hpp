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

#ifndef SHARE_VM_SHARK_SHARKTYPE_HPP
#define SHARE_VM_SHARK_SHARKTYPE_HPP

#include "ci/ciType.hpp"
#include "memory/allocation.hpp"
#include "shark/llvmHeaders.hpp"
#include "shark/sharkContext.hpp"
#include "utilities/globalDefinitions.hpp"

class SharkType : public AllStatic {
 private:
  static SharkContext& context() {
    return SharkContext::current();
  }

  // Basic types
 public:
  static const llvm::Type* void_type() {
    return context().void_type();
  }
  static const llvm::IntegerType* bit_type() {
    return context().bit_type();
  }
  static const llvm::IntegerType* jbyte_type() {
    return context().jbyte_type();
  }
  static const llvm::IntegerType* jshort_type() {
    return context().jshort_type();
  }
  static const llvm::IntegerType* jint_type() {
    return context().jint_type();
  }
  static const llvm::IntegerType* jlong_type() {
    return context().jlong_type();
  }
  static const llvm::Type* jfloat_type() {
    return context().jfloat_type();
  }
  static const llvm::Type* jdouble_type() {
    return context().jdouble_type();
  }
  static const llvm::IntegerType* intptr_type() {
    return context().intptr_type();
  }

  // Compound types
 public:
  static const llvm::PointerType* itableOffsetEntry_type() {
    return context().itableOffsetEntry_type();
  }
  static const llvm::PointerType* jniEnv_type() {
    return context().jniEnv_type();
  }
  static const llvm::PointerType* jniHandleBlock_type() {
    return context().jniHandleBlock_type();
  }
  static const llvm::PointerType* klass_type() {
    return context().klass_type();
  }
  static const llvm::PointerType* methodOop_type() {
    return context().methodOop_type();
  }
  static const llvm::ArrayType* monitor_type() {
    return context().monitor_type();
  }
  static const llvm::PointerType* oop_type() {
    return context().oop_type();
  }
  static const llvm::PointerType* thread_type() {
    return context().thread_type();
  }
  static const llvm::PointerType* zeroStack_type() {
    return context().zeroStack_type();
  }
  static const llvm::FunctionType* entry_point_type() {
    return context().entry_point_type();
  }
  static const llvm::FunctionType* osr_entry_point_type() {
    return context().osr_entry_point_type();
  }

  // Mappings
 public:
  static const llvm::Type* to_stackType(BasicType type) {
    return context().to_stackType(type);
  }
  static const llvm::Type* to_stackType(ciType* type) {
    return to_stackType(type->basic_type());
  }
  static const llvm::Type* to_arrayType(BasicType type) {
    return context().to_arrayType(type);
  }
  static const llvm::Type* to_arrayType(ciType* type) {
    return to_arrayType(type->basic_type());
  }
};

#endif // SHARE_VM_SHARK_SHARKTYPE_HPP
