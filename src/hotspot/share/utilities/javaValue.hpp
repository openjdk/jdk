/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_JAVAVALUE_HPP
#define SHARE_UTILITIES_JAVAVALUE_HPP

#include "utilities/basicTypes.hpp"

// JavaValue serves as a container for arbitrary Java values.

class JavaValue {

public:
  typedef union JavaCallValue {
    jfloat   f;
    jdouble  d;
    jint     i;
    jlong    l;
    jobject  h;
    oopDesc* o;
  } JavaCallValue;

private:
  BasicType _type;
  JavaCallValue _value;

public:
  JavaValue(BasicType t = T_ILLEGAL) { _type = t; }

  JavaValue(jfloat value) {
    _type    = T_FLOAT;
    _value.f = value;
  }

  JavaValue(jdouble value) {
    _type    = T_DOUBLE;
    _value.d = value;
  }

  jfloat get_jfloat() const { return _value.f; }
  jdouble get_jdouble() const { return _value.d; }
  jint get_jint() const { return _value.i; }
  jlong get_jlong() const { return _value.l; }
  jobject get_jobject() const { return _value.h; }
  oopDesc* get_oop() const { return _value.o; }
  JavaCallValue* get_value_addr() { return &_value; }
  BasicType get_type() const { return _type; }

  void set_jfloat(jfloat f) { _value.f = f;}
  void set_jdouble(jdouble d) { _value.d = d;}
  void set_jint(jint i) { _value.i = i;}
  void set_jlong(jlong l) { _value.l = l;}
  void set_jobject(jobject h) { _value.h = h;}
  void set_oop(oopDesc* o) { _value.o = o;}
  void set_type(BasicType t) { _type = t; }

  jboolean get_jboolean() const { return (jboolean) (_value.i);}
  jbyte get_jbyte() const { return (jbyte) (_value.i);}
  jchar get_jchar() const { return (jchar) (_value.i);}
  jshort get_jshort() const { return (jshort) (_value.i);}

};

#endif //HOTSPOT_JAVAVALUE_HPP
