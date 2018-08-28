/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_OOPFACTORY_HPP
#define SHARE_VM_MEMORY_OOPFACTORY_HPP

#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "memory/referenceType.hpp"
#include "memory/universe.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.hpp"
#include "oops/typeArrayKlass.hpp"
#include "utilities/growableArray.hpp"

// oopFactory is a class used for creating new objects.

class vframeArray;

class oopFactory: AllStatic {
 public:
  // Basic type leaf array allocation
  static typeArrayOop    new_boolArray  (int length, TRAPS) { return TypeArrayKlass::cast(Universe::boolArrayKlassObj  ())->allocate(length, THREAD); }
  static typeArrayOop    new_charArray  (int length, TRAPS) { return TypeArrayKlass::cast(Universe::charArrayKlassObj  ())->allocate(length, THREAD); }
  static typeArrayOop    new_floatArray (int length, TRAPS) { return TypeArrayKlass::cast(Universe::floatArrayKlassObj())->allocate(length, THREAD); }
  static typeArrayOop    new_doubleArray(int length, TRAPS) { return TypeArrayKlass::cast(Universe::doubleArrayKlassObj())->allocate(length, THREAD); }
  static typeArrayOop    new_byteArray  (int length, TRAPS) { return TypeArrayKlass::cast(Universe::byteArrayKlassObj  ())->allocate(length, THREAD); }
  static typeArrayOop    new_shortArray (int length, TRAPS) { return TypeArrayKlass::cast(Universe::shortArrayKlassObj ())->allocate(length, THREAD); }
  static typeArrayOop    new_intArray   (int length, TRAPS) { return TypeArrayKlass::cast(Universe::intArrayKlassObj   ())->allocate(length, THREAD); }
  static typeArrayOop    new_longArray  (int length, TRAPS) { return TypeArrayKlass::cast(Universe::longArrayKlassObj  ())->allocate(length, THREAD); }

  // create java.lang.Object[]
  static objArrayOop     new_objectArray(int length, TRAPS)  {
    assert(Universe::objectArrayKlassObj() != NULL, "Too early?");
    return ObjArrayKlass::
      cast(Universe::objectArrayKlassObj())->allocate(length, THREAD);
  }

  static typeArrayOop    new_charArray           (const char* utf8_str,  TRAPS);
  static typeArrayOop    new_tenured_charArray(int length, TRAPS);

  static typeArrayOop    new_typeArray(BasicType type, int length, TRAPS);
  static typeArrayOop    new_typeArray_nozero(BasicType type, int length, TRAPS);
  static typeArrayOop    new_symbolArray(int length, TRAPS);

  // Regular object arrays
  static objArrayOop     new_objArray(Klass* klass, int length, TRAPS);

  // Helpers that return handles
  static objArrayHandle  new_objArray_handle(Klass* klass, int length, TRAPS);
  static typeArrayHandle new_byteArray_handle(int length, TRAPS);
};

#endif // SHARE_VM_MEMORY_OOPFACTORY_HPP
