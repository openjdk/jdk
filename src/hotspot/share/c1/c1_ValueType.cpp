/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "c1/c1_ValueType.hpp"
#include "ci/ciArray.hpp"
#include "ci/ciInstance.hpp"
#include "ci/ciNullObject.hpp"
#include "memory/resourceArea.hpp"


// predefined types
VoidType*       voidType     = nullptr;
IntType*        intType      = nullptr;
LongType*       longType     = nullptr;
FloatType*      floatType    = nullptr;
DoubleType*     doubleType   = nullptr;
ObjectType*     objectType   = nullptr;
ArrayType*      arrayType    = nullptr;
InstanceType*   instanceType = nullptr;
ClassType*      classType    = nullptr;
AddressType*    addressType  = nullptr;
IllegalType*    illegalType  = nullptr;


// predefined constants
IntConstant*    intZero      = nullptr;
IntConstant*    intOne       = nullptr;
ObjectConstant* objectNull   = nullptr;


void ValueType::initialize() {
#define VALUE_TYPE_STORAGE_NAME(name) name##_storage
#define VALUE_TYPE_STORAGE(name, type) alignas(type) static uint8_t VALUE_TYPE_STORAGE_NAME(name)[sizeof(type)]
#define VALUE_TYPE(name, type, ...)                                \
  assert(name == nullptr, "ValueType initialized more than once"); \
  VALUE_TYPE_STORAGE(name, type);                                  \
  name = ::new(static_cast<void*>(VALUE_TYPE_STORAGE_NAME(name))) type(__VA_ARGS__)

  VALUE_TYPE(voidType    , VoidType);
  VALUE_TYPE(intType     , IntType);
  VALUE_TYPE(longType    , LongType);
  VALUE_TYPE(floatType   , FloatType);
  VALUE_TYPE(doubleType  , DoubleType);
  VALUE_TYPE(objectType  , ObjectType);
  VALUE_TYPE(arrayType   , ArrayType);
  VALUE_TYPE(instanceType, InstanceType);
  VALUE_TYPE(classType   , ClassType);
  VALUE_TYPE(addressType , AddressType);
  VALUE_TYPE(illegalType , IllegalType);

  VALUE_TYPE(intZero     , IntConstant   , 0);
  VALUE_TYPE(intOne      , IntConstant   , 1);
  VALUE_TYPE(objectNull  , ObjectConstant, ciNullObject::make());

#undef VALUE_TYPE
#undef VALUE_TYPE_STORAGE
#undef VALUE_TYPE_STORAGE_NAME
}


ValueType* ValueType::meet(ValueType* y) const {
  // incomplete & conservative solution for now - fix this!
  assert(tag() == y->tag(), "types must match");
  return base();
}


ciType* ObjectConstant::exact_type() const {
  ciObject* c = constant_value();
  return (c != nullptr && !c->is_null_object()) ? c->klass() : nullptr;
}
ciType* ArrayConstant::exact_type() const {
  ciObject* c = constant_value();
  return (c != nullptr && !c->is_null_object()) ? c->klass() : nullptr;
}
ciType* InstanceConstant::exact_type() const {
  ciObject* c = constant_value();
  return (c != nullptr && !c->is_null_object()) ? c->klass() : nullptr;
}
ciType* ClassConstant::exact_type() const {
  return Compilation::current()->env()->Class_klass();
}


jobject ObjectType::encoding() const {
  assert(is_constant(), "must be");
  return constant_value()->constant_encoding();
}

bool ObjectType::is_loaded() const {
  assert(is_constant(), "must be");
  return constant_value()->is_loaded();
}

bool MetadataType::is_loaded() const {
  assert(is_constant(), "must be");
  return constant_value()->is_loaded();
}

ciObject* ObjectConstant::constant_value() const                   { return _value; }
ciObject* ArrayConstant::constant_value() const                    { return _value; }
ciObject* InstanceConstant::constant_value() const                 { return _value; }

ValueType* as_ValueType(BasicType type) {
  switch (type) {
    case T_VOID   : return voidType;
    case T_BYTE   : // fall through
    case T_CHAR   : // fall through
    case T_SHORT  : // fall through
    case T_BOOLEAN: // fall through
    case T_INT    : return intType;
    case T_LONG   : return longType;
    case T_FLOAT  : return floatType;
    case T_DOUBLE : return doubleType;
    case T_ARRAY  : return arrayType;
    case T_OBJECT : return objectType;
    case T_ADDRESS: return addressType;
    case T_ILLEGAL: return illegalType;
    default       : ShouldNotReachHere();
                    return illegalType;
  }
}


ValueType* as_ValueType(ciConstant value) {
  switch (value.basic_type()) {
    case T_BYTE   : // fall through
    case T_CHAR   : // fall through
    case T_SHORT  : // fall through
    case T_BOOLEAN: // fall through
    case T_INT    : return new IntConstant   (value.as_int   ());
    case T_LONG   : return new LongConstant  (value.as_long  ());
    case T_FLOAT  : return new FloatConstant (value.as_float ());
    case T_DOUBLE : return new DoubleConstant(value.as_double());
    case T_ARRAY  : // fall through (ciConstant doesn't have an array accessor)
    case T_OBJECT : {
      // TODO: Common the code with GraphBuilder::load_constant?
      ciObject* obj = value.as_object();
      if (obj->is_null_object())
        return objectNull;
      if (obj->is_loaded()) {
        if (obj->is_array())
          return new ArrayConstant(obj->as_array());
        else if (obj->is_instance())
          return new InstanceConstant(obj->as_instance());
      }
      return new ObjectConstant(obj);
    }
    default       : ShouldNotReachHere();
                    return illegalType;
  }
}


BasicType as_BasicType(ValueType* type) {
  switch (type->tag()) {
    case voidTag:    return T_VOID;
    case intTag:     return T_INT;
    case longTag:    return T_LONG;
    case floatTag:   return T_FLOAT;
    case doubleTag:  return T_DOUBLE;
    case objectTag:  return T_OBJECT;
    case metaDataTag:return T_METADATA;
    case addressTag: return T_ADDRESS;
    case illegalTag: return T_ILLEGAL;
    default        : ShouldNotReachHere();
                     return T_ILLEGAL;
  }
}
