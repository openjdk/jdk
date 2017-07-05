/*
 * Copyright (c) 1999, 2005, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_c1_ValueType.cpp.incl"


// predefined types
VoidType*       voidType     = NULL;
IntType*        intType      = NULL;
LongType*       longType     = NULL;
FloatType*      floatType    = NULL;
DoubleType*     doubleType   = NULL;
ObjectType*     objectType   = NULL;
ArrayType*      arrayType    = NULL;
InstanceType*   instanceType = NULL;
ClassType*      classType    = NULL;
AddressType*    addressType  = NULL;
IllegalType*    illegalType  = NULL;


// predefined constants
IntConstant*    intZero      = NULL;
IntConstant*    intOne       = NULL;
ObjectConstant* objectNull   = NULL;


void ValueType::initialize() {
  // Note: Must initialize all types for each compilation
  //       as they are allocated within a ResourceMark!

  // types
  voidType     = new VoidType();
  intType      = new IntType();
  longType     = new LongType();
  floatType    = new FloatType();
  doubleType   = new DoubleType();
  objectType   = new ObjectType();
  arrayType    = new ArrayType();
  instanceType = new InstanceType();
  classType    = new ClassType();
  addressType  = new AddressType();
  illegalType  = new IllegalType();

  // constants
  intZero     = new IntConstant(0);
  intOne      = new IntConstant(1);
  objectNull  = new ObjectConstant(ciNullObject::make());
};


ValueType* ValueType::meet(ValueType* y) const {
  // incomplete & conservative solution for now - fix this!
  assert(tag() == y->tag(), "types must match");
  return base();
}


ValueType* ValueType::join(ValueType* y) const {
  Unimplemented();
  return NULL;
}



jobject ObjectType::encoding() const {
  assert(is_constant(), "must be");
  return constant_value()->constant_encoding();
}

bool ObjectType::is_loaded() const {
  assert(is_constant(), "must be");
  return constant_value()->is_loaded();
}

ciObject* ObjectConstant::constant_value() const                   { return _value; }
ciObject* ArrayConstant::constant_value() const                    { return _value; }
ciObject* InstanceConstant::constant_value() const                 { return _value; }
ciObject* ClassConstant::constant_value() const                    { return _value; }


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
  }
  ShouldNotReachHere();
  return illegalType;
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
    case T_OBJECT : return new ObjectConstant(value.as_object());
  }
  ShouldNotReachHere();
  return illegalType;
}


BasicType as_BasicType(ValueType* type) {
  switch (type->tag()) {
    case voidTag:    return T_VOID;
    case intTag:     return T_INT;
    case longTag:    return T_LONG;
    case floatTag:   return T_FLOAT;
    case doubleTag:  return T_DOUBLE;
    case objectTag:  return T_OBJECT;
    case addressTag: return T_ADDRESS;
    case illegalTag: return T_ILLEGAL;
  }
  ShouldNotReachHere();
  return T_ILLEGAL;
}
