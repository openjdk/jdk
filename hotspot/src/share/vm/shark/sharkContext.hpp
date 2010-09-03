/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2009, 2010 Red Hat, Inc.
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

// The LLVMContext class allows multiple instances of LLVM to operate
// independently of each other in a multithreaded context.  We extend
// this here to store things in Shark that are LLVMContext-specific.

class SharkFreeQueueItem;

class SharkContext : public llvm::LLVMContext {
 public:
  SharkContext(const char* name);

 private:
  llvm::Module* _module;

#if SHARK_LLVM_VERSION >= 27
 public:
#else
 private:
#endif
  llvm::Module* module() const {
    return _module;
  }

  // Get this thread's SharkContext
 public:
  static SharkContext& current() {
    return *SharkCompiler::compiler()->context();
  }

  // Module accessors
 public:
#if SHARK_LLVM_VERSION < 27
  llvm::ModuleProvider* module_provider() const {
    return new llvm::ExistingModuleProvider(module());
  }
#endif
  void add_function(llvm::Function* function) const {
    module()->getFunctionList().push_back(function);
  }
  llvm::Constant* get_external(const char*               name,
                               const llvm::FunctionType* sig) {
    return module()->getOrInsertFunction(name, sig);
  }

  // Basic types
 private:
  const llvm::Type*        _void_type;
  const llvm::IntegerType* _bit_type;
  const llvm::IntegerType* _jbyte_type;
  const llvm::IntegerType* _jshort_type;
  const llvm::IntegerType* _jint_type;
  const llvm::IntegerType* _jlong_type;
  const llvm::Type*        _jfloat_type;
  const llvm::Type*        _jdouble_type;

 public:
  const llvm::Type* void_type() const {
    return _void_type;
  }
  const llvm::IntegerType* bit_type() const {
    return _bit_type;
  }
  const llvm::IntegerType* jbyte_type() const {
    return _jbyte_type;
  }
  const llvm::IntegerType* jshort_type() const {
    return _jshort_type;
  }
  const llvm::IntegerType* jint_type() const {
    return _jint_type;
  }
  const llvm::IntegerType* jlong_type() const {
    return _jlong_type;
  }
  const llvm::Type* jfloat_type() const {
    return _jfloat_type;
  }
  const llvm::Type* jdouble_type() const {
    return _jdouble_type;
  }
  const llvm::IntegerType* intptr_type() const {
    return LP64_ONLY(jlong_type()) NOT_LP64(jint_type());
  }

  // Compound types
 private:
  const llvm::PointerType*  _itableOffsetEntry_type;
  const llvm::PointerType*  _jniEnv_type;
  const llvm::PointerType*  _jniHandleBlock_type;
  const llvm::PointerType*  _klass_type;
  const llvm::PointerType*  _methodOop_type;
  const llvm::ArrayType*    _monitor_type;
  const llvm::PointerType*  _oop_type;
  const llvm::PointerType*  _thread_type;
  const llvm::PointerType*  _zeroStack_type;
  const llvm::FunctionType* _entry_point_type;
  const llvm::FunctionType* _osr_entry_point_type;

 public:
  const llvm::PointerType* itableOffsetEntry_type() const {
    return _itableOffsetEntry_type;
  }
  const llvm::PointerType* jniEnv_type() const {
    return _jniEnv_type;
  }
  const llvm::PointerType* jniHandleBlock_type() const {
    return _jniHandleBlock_type;
  }
  const llvm::PointerType* klass_type() const {
    return _klass_type;
  }
  const llvm::PointerType* methodOop_type() const {
    return _methodOop_type;
  }
  const llvm::ArrayType* monitor_type() const {
    return _monitor_type;
  }
  const llvm::PointerType* oop_type() const {
    return _oop_type;
  }
  const llvm::PointerType* thread_type() const {
    return _thread_type;
  }
  const llvm::PointerType* zeroStack_type() const {
    return _zeroStack_type;
  }
  const llvm::FunctionType* entry_point_type() const {
    return _entry_point_type;
  }
  const llvm::FunctionType* osr_entry_point_type() const {
    return _osr_entry_point_type;
  }

  // Mappings
 private:
  const llvm::Type* _to_stackType[T_CONFLICT];
  const llvm::Type* _to_arrayType[T_CONFLICT];

 private:
  const llvm::Type* map_type(const llvm::Type* const* table,
                             BasicType                type) const {
    assert(type >= 0 && type < T_CONFLICT, "unhandled type");
    const llvm::Type* result = table[type];
    assert(type != NULL, "unhandled type");
    return result;
  }

 public:
  const llvm::Type* to_stackType(BasicType type) const {
    return map_type(_to_stackType, type);
  }
  const llvm::Type* to_arrayType(BasicType type) const {
    return map_type(_to_arrayType, type);
  }

  // Functions queued for freeing
 private:
  SharkFreeQueueItem* _free_queue;

 public:
  void push_to_free_queue(llvm::Function* function);
  llvm::Function* pop_from_free_queue();
};
