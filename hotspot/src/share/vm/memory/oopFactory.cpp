/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceOop.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"


typeArrayOop oopFactory::new_charArray(const char* utf8_str, TRAPS) {
  int length = utf8_str == NULL ? 0 : UTF8::unicode_length(utf8_str);
  typeArrayOop result = new_charArray(length, CHECK_NULL);
  if (length > 0) {
    UTF8::convert_to_unicode(utf8_str, result->char_at_addr(0), length);
  }
  return result;
}

typeArrayOop oopFactory::new_tenured_charArray(int length, TRAPS) {
  return TypeArrayKlass::cast(Universe::charArrayKlassObj())->allocate(length, THREAD);
}

typeArrayOop oopFactory::new_typeArray(BasicType type, int length, TRAPS) {
  Klass* type_asKlassOop = Universe::typeArrayKlassObj(type);
  TypeArrayKlass* type_asArrayKlass = TypeArrayKlass::cast(type_asKlassOop);
  typeArrayOop result = type_asArrayKlass->allocate(length, THREAD);
  return result;
}

// Create a Java array that points to metadata.
// As far as Java code is concerned, a metaData array is either an array of
// int or long depending on pointer size.  Only a few things use this, like
// stack trace elements in Throwable.  They cast Method* into this type.
// Note:can't point to symbols because there's no way to unreference count
// them when this object goes away.
typeArrayOop oopFactory::new_metaDataArray(int length, TRAPS) {
  BasicType type = LP64_ONLY(T_LONG) NOT_LP64(T_INT);
  Klass* type_asKlassOop = Universe::typeArrayKlassObj(type);
  TypeArrayKlass* type_asArrayKlass = TypeArrayKlass::cast(type_asKlassOop);
  typeArrayOop result = type_asArrayKlass->allocate_common(length, true, THREAD);
  return result;
}

typeArrayOop oopFactory::new_typeArray_nozero(BasicType type, int length, TRAPS) {
  Klass* type_asKlassOop = Universe::typeArrayKlassObj(type);
  TypeArrayKlass* type_asArrayKlass = TypeArrayKlass::cast(type_asKlassOop);
  typeArrayOop result = type_asArrayKlass->allocate_common(length, false, THREAD);
  return result;
}


objArrayOop oopFactory::new_objArray(Klass* klass, int length, TRAPS) {
  assert(klass->is_klass(), "must be instance class");
  if (klass->oop_is_array()) {
    return ((ArrayKlass*)klass)->allocate_arrayArray(1, length, THREAD);
  } else {
    assert (klass->oop_is_instance(), "new object array with klass not an InstanceKlass");
    return ((InstanceKlass*)klass)->allocate_objArray(1, length, THREAD);
  }
}
