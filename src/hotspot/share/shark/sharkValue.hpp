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

#ifndef SHARE_VM_SHARK_SHARKVALUE_HPP
#define SHARE_VM_SHARK_SHARKVALUE_HPP

#include "ci/ciType.hpp"
#include "memory/allocation.hpp"
#include "shark/llvmHeaders.hpp"
#include "shark/llvmValue.hpp"
#include "shark/sharkType.hpp"

// Items on the stack and in local variables are tracked using
// SharkValue objects.
//
// All SharkValues are one of two core types, SharkNormalValue
// and SharkAddressValue, but no code outside this file should
// ever refer to those directly.  The split is because of the
// way JSRs are handled: the typeflow pass expands them into
// multiple copies, so the return addresses pushed by jsr and
// popped by ret only exist at compile time.  Having separate
// classes for these allows us to check that our jsr handling
// is correct, via assertions.
//
// There is one more type, SharkPHIValue, which is a subclass
// of SharkNormalValue with a couple of extra methods.  Use of
// SharkPHIValue outside of this file is acceptable, so long
// as it is obtained via SharkValue::as_phi().

class SharkBuilder;
class SharkPHIValue;

class SharkValue : public ResourceObj {
 protected:
  SharkValue() {}

  // Cloning
 public:
  virtual SharkValue* clone() const = 0;

  // Casting
 public:
  virtual bool           is_phi() const;
  virtual SharkPHIValue* as_phi();

  // Comparison
 public:
  virtual bool equal_to(SharkValue* other) const = 0;

  // Type access
 public:
  virtual BasicType basic_type() const = 0;
  virtual ciType*   type()       const;

  virtual bool is_jint()    const;
  virtual bool is_jlong()   const;
  virtual bool is_jfloat()  const;
  virtual bool is_jdouble() const;
  virtual bool is_jobject() const;
  virtual bool is_jarray()  const;
  virtual bool is_address() const;

  virtual int size() const = 0;

  bool is_one_word() const {
    return size() == 1;
  }
  bool is_two_word() const {
    return size() == 2;
  }

  // Typed conversion from SharkValues
 public:
  virtual llvm::Value* jint_value()    const;
  virtual llvm::Value* jlong_value()   const;
  virtual llvm::Value* jfloat_value()  const;
  virtual llvm::Value* jdouble_value() const;
  virtual llvm::Value* jobject_value() const;
  virtual llvm::Value* jarray_value()  const;
  virtual int          address_value() const;

  // Typed conversion to SharkValues
 public:
  static SharkValue* create_jint(llvm::Value* value, bool zero_checked) {
    assert(value->getType() == SharkType::jint_type(), "should be");
    return create_generic(ciType::make(T_INT), value, zero_checked);
  }
  static SharkValue* create_jlong(llvm::Value* value, bool zero_checked) {
    assert(value->getType() == SharkType::jlong_type(), "should be");
    return create_generic(ciType::make(T_LONG), value, zero_checked);
  }
  static SharkValue* create_jfloat(llvm::Value* value) {
    assert(value->getType() == SharkType::jfloat_type(), "should be");
    return create_generic(ciType::make(T_FLOAT), value, false);
  }
  static SharkValue* create_jdouble(llvm::Value* value) {
    assert(value->getType() == SharkType::jdouble_type(), "should be");
    return create_generic(ciType::make(T_DOUBLE), value, false);
  }
  static SharkValue* create_jobject(llvm::Value* value, bool zero_checked) {
    assert(value->getType() == SharkType::oop_type(), "should be");
    return create_generic(ciType::make(T_OBJECT), value, zero_checked);
  }

  // Typed conversion from constants of various types
 public:
  static SharkValue* jint_constant(jint value) {
    return create_jint(LLVMValue::jint_constant(value), value != 0);
  }
  static SharkValue* jlong_constant(jlong value) {
    return create_jlong(LLVMValue::jlong_constant(value), value != 0);
  }
  static SharkValue* jfloat_constant(jfloat value) {
    return create_jfloat(LLVMValue::jfloat_constant(value));
  }
  static SharkValue* jdouble_constant(jdouble value) {
    return create_jdouble(LLVMValue::jdouble_constant(value));
  }
  static SharkValue* null() {
    return create_jobject(LLVMValue::null(), false);
  }
  static inline SharkValue* address_constant(int bci);

  // Type-losing conversions -- use with care!
 public:
  virtual llvm::Value* generic_value() const = 0;
  virtual llvm::Value* intptr_value(SharkBuilder* builder) const;

  static inline SharkValue* create_generic(ciType*      type,
                                           llvm::Value* value,
                                           bool         zero_checked);
  static inline SharkValue* create_phi(ciType*              type,
                                       llvm::PHINode*       phi,
                                       const SharkPHIValue* parent = NULL);

  // Phi-style stuff
 public:
  virtual void addIncoming(SharkValue* value, llvm::BasicBlock* block);
  virtual SharkValue* merge(SharkBuilder*     builder,
                            SharkValue*       other,
                            llvm::BasicBlock* other_block,
                            llvm::BasicBlock* this_block,
                            const char*       name) = 0;

  // Repeated null and divide-by-zero check removal
 public:
  virtual bool zero_checked() const;
  virtual void set_zero_checked(bool zero_checked);
};

class SharkNormalValue : public SharkValue {
  friend class SharkValue;

 protected:
  SharkNormalValue(ciType* type, llvm::Value* value, bool zero_checked)
    : _type(type), _llvm_value(value), _zero_checked(zero_checked) {}

 private:
  ciType*      _type;
  llvm::Value* _llvm_value;
  bool         _zero_checked;

 private:
  llvm::Value* llvm_value() const {
    return _llvm_value;
  }

  // Cloning
 public:
  SharkValue* clone() const;

  // Comparison
 public:
  bool equal_to(SharkValue* other) const;

  // Type access
 public:
  ciType*   type()       const;
  BasicType basic_type() const;
  int       size()       const;

 public:
  bool is_jint()    const;
  bool is_jlong()   const;
  bool is_jfloat()  const;
  bool is_jdouble() const;
  bool is_jobject() const;
  bool is_jarray()  const;

  // Typed conversions to LLVM values
 public:
  llvm::Value* jint_value()    const;
  llvm::Value* jlong_value()   const;
  llvm::Value* jfloat_value()  const;
  llvm::Value* jdouble_value() const;
  llvm::Value* jobject_value() const;
  llvm::Value* jarray_value()  const;

  // Type-losing conversions, use with care
 public:
  llvm::Value* generic_value() const;
  llvm::Value* intptr_value(SharkBuilder* builder) const;

  // Phi-style stuff
 public:
  SharkValue* merge(SharkBuilder*     builder,
                    SharkValue*       other,
                    llvm::BasicBlock* other_block,
                    llvm::BasicBlock* this_block,
                    const char*       name);

  // Repeated null and divide-by-zero check removal
 public:
  bool zero_checked() const;
  void set_zero_checked(bool zero_checked);
};

class SharkPHIValue : public SharkNormalValue {
  friend class SharkValue;

 protected:
  SharkPHIValue(ciType* type, llvm::PHINode* phi, const SharkPHIValue *parent)
    : SharkNormalValue(type, phi, parent && parent->zero_checked()),
      _parent(parent),
      _all_incomers_zero_checked(true) {}

 private:
  const SharkPHIValue* _parent;
  bool                 _all_incomers_zero_checked;

 private:
  const SharkPHIValue* parent() const {
    return _parent;
  }
  bool is_clone() const {
    return parent() != NULL;
  }

 public:
  bool all_incomers_zero_checked() const {
    if (is_clone())
      return parent()->all_incomers_zero_checked();

    return _all_incomers_zero_checked;
  }

  // Cloning
 public:
  SharkValue* clone() const;

  // Casting
 public:
  bool           is_phi() const;
  SharkPHIValue* as_phi();

  // Phi-style stuff
 public:
  void addIncoming(SharkValue *value, llvm::BasicBlock* block);
};

class SharkAddressValue : public SharkValue {
  friend class SharkValue;

 protected:
  SharkAddressValue(int bci)
    : _bci(bci) {}

 private:
  int _bci;

  // Cloning
 public:
  SharkValue* clone() const;

  // Comparison
 public:
  bool equal_to(SharkValue* other) const;

  // Type access
 public:
  BasicType basic_type() const;
  int       size()       const;
  bool      is_address() const;

  // Typed conversion from SharkValues
 public:
  int address_value() const;

  // Type-losing conversion -- use with care!
 public:
  llvm::Value* generic_value() const;

  // Phi-style stuff
 public:
  void addIncoming(SharkValue *value, llvm::BasicBlock* block);
  SharkValue* merge(SharkBuilder*     builder,
                    SharkValue*       other,
                    llvm::BasicBlock* other_block,
                    llvm::BasicBlock* this_block,
                    const char*       name);
};

// SharkValue methods that can't be declared above

inline SharkValue* SharkValue::create_generic(ciType*      type,
                                              llvm::Value* value,
                                              bool         zero_checked) {
  return new SharkNormalValue(type, value, zero_checked);
}

inline SharkValue* SharkValue::create_phi(ciType*              type,
                                          llvm::PHINode*       phi,
                                          const SharkPHIValue* parent) {
  return new SharkPHIValue(type, phi, parent);
}

inline SharkValue* SharkValue::address_constant(int bci) {
  return new SharkAddressValue(bci);
}

#endif // SHARE_VM_SHARK_SHARKVALUE_HPP
