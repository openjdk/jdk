/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_GLOBALJAVAVALUE_HPP
#define SHARE_UTILITIES_GLOBALJAVAVALUE_HPP


#include "metaprogramming/primitiveConversions.hpp"
#include "utilities/compilerWarnings.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"

// Get constants like JVM_T_CHAR and JVM_SIGNATURE_INT, before pulling in <jvm.h>.
#include "classfile_constants.h"

#include COMPILER_HEADER(utilities/globalDefinitions)

#include <cstddef>
#include <type_traits>

// NOTE: replicated in SA in vm/agent/sun/jvm/hotspot/runtime/BasicType.java
enum BasicType {
// The values T_BOOLEAN..T_LONG (4..11) are derived from the JVMS.
  T_BOOLEAN     = JVM_T_BOOLEAN,
  T_CHAR        = JVM_T_CHAR,
  T_FLOAT       = JVM_T_FLOAT,
  T_DOUBLE      = JVM_T_DOUBLE,
  T_BYTE        = JVM_T_BYTE,
  T_SHORT       = JVM_T_SHORT,
  T_INT         = JVM_T_INT,
  T_LONG        = JVM_T_LONG,
  // The remaining values are not part of any standard.
  // T_OBJECT and T_VOID denote two more semantic choices
  // for method return values.
  // T_OBJECT and T_ARRAY describe signature syntax.
  // T_ADDRESS, T_METADATA, T_NARROWOOP, T_NARROWKLASS describe
  // internal references within the JVM as if they were Java
  // types in their own right.
  T_OBJECT      = 12,
  T_ARRAY       = 13,
  T_VOID        = 14,
  T_ADDRESS     = 15,
  T_NARROWOOP   = 16,
  T_METADATA    = 17,
  T_NARROWKLASS = 18,
  T_CONFLICT    = 19, // for stack value type with conflicting contents
  T_ILLEGAL     = 99
};


// JavaValue serves as a container for arbitrary Java values.
class JavaValue {

 public:
  // Define it large enough to hold all possible primitive types.
  typedef long long JavaCallValue;

 private:
  BasicType _type;
  JavaCallValue _value;

 public:
  JavaValue(BasicType t = T_ILLEGAL) { _type = t; }

  JavaValue(jfloat value) {
    _type    = T_FLOAT;
    // This matches with Template #6 of cast<To>(From).
    _value = PrimitiveConversions::cast<JavaCallValue>(value);
  }

  JavaValue(jdouble value) {
    _type    = T_DOUBLE;
    // This matches with Template #5 of cast<To>(From).
    _value = PrimitiveConversions::cast<JavaCallValue>(value);
  }

 jfloat get_jfloat() const    { return PrimitiveConversions::cast<jfloat>(_value);  } // Tempalte #6.
 jdouble get_jdouble() const  { return PrimitiveConversions::cast<jdouble>(_value); } // Tempalte #5.
 jint get_jint() const        { return PrimitiveConversions::cast<jint>(_value);    } // Tempalte #7.
 jlong get_jlong() const      { return PrimitiveConversions::cast<jlong>(_value);   } // Tempalte #1.
 jobject get_jobject() const {
  #ifdef ARM32
    // In arm32 archs, this call compiles to cast<jobject>(const JavaCallValue&) and
    // does not match with any of the cast<To>(From) instances.
    return *(jobject*)(&_value);
  #else
    return PrimitiveConversions::cast<jobject>(_value);
  #endif
 }
 oopDesc* get_oop() const     {
  #ifdef ARM32
    // In arm32 archs, this call compiles to cast<oopDesc*>(const JavaCallValue&) and
    // does not match with any of the cast<To>(From) instances.
    return (oopDesc*)(&_value);
  #else
    // This matches with Template #4 of cast<To>(From).
    return PrimitiveConversions::cast<oopDesc*>(_value);
  #endif
 }

 JavaCallValue* get_value_addr() { return &_value; }
 BasicType get_type() const { return _type; }

 void set_jfloat(jfloat f)   { _value = PrimitiveConversions::cast<JavaCallValue>(f); } // Tempalte #6.
 void set_jdouble(jdouble d) { _value = PrimitiveConversions::cast<JavaCallValue>(d); } // Tempalte #5.
 void set_jint(jint i)       { _value = PrimitiveConversions::cast<JavaCallValue>(i); } // Tempalte #7.
 void set_jlong(jlong l)     { _value = PrimitiveConversions::cast<JavaCallValue>(l); } // Tempalte #1.
 void set_jobject(jobject h) {
  #ifdef ARM32
    // In arm32 archs, this call compiles to cast<JavaCallValue>(_jobject*&) and
    // does not match with any of the cast<To>(From) instances.
    _value = *(JavaCallValue*)h;
  #else
    _value = PrimitiveConversions::cast<JavaCallValue>(h);
  #endif
 }
 void set_oop(oopDesc* o)    {
  #ifdef ARM32
    // In arm32 archs, this call compiles to cast<JavaCallValue>(oopDesc*&) and
    // does not match with any of the cast<To>(From) instances.
    _value = *(JavaCallValue*)o;
  #else
    _value = PrimitiveConversions::cast<JavaCallValue>(o);
  #endif
 }
 void set_type(BasicType t) { _type = t; }

 jboolean get_jboolean() const { return PrimitiveConversions::cast<jboolean>(PrimitiveConversions::cast<jint>(_value)); } // Tempalte #7.
 jbyte get_jbyte() const       { return PrimitiveConversions::cast<jbyte>(PrimitiveConversions::cast<jint>(_value));    } // Tempalte #7.
 jchar get_jchar() const       { return PrimitiveConversions::cast<jchar>(PrimitiveConversions::cast<jint>(_value));    } // Tempalte #7.
 jshort get_jshort() const     { return PrimitiveConversions::cast<jshort>(PrimitiveConversions::cast<jint>(_value));   } // Tempalte #7.

};

//----------------------------------------------------------------------------------------------------
// Special casts
// Cast floats into same-size integers and vice-versa w/o changing bit-pattern

inline jint    jint_cast    (jfloat  x)  { return PrimitiveConversions::cast<jint>(x);    } // Template #5 of cast<To>(From).
inline jfloat  jfloat_cast  (jint    x)  { return PrimitiveConversions::cast<jfloat>(x);  } // Tempalte #5

inline jlong   jlong_cast   (jdouble x)  { return PrimitiveConversions::cast<jlong>(x);   } // Tempalte #5
inline julong  julong_cast  (jdouble x)  { return PrimitiveConversions::cast<julong>(x);  } // Tempalte #5
inline jdouble jdouble_cast (jlong   x)  { return PrimitiveConversions::cast<jdouble>(x); } // Tempalte #5
#endif // SHARE_UTILITIES_GLOBALJAVAVALUE_HPP