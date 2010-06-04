/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_oopFactory.cpp.incl"


typeArrayOop oopFactory::new_charArray(const char* utf8_str, TRAPS) {
  int length = utf8_str == NULL ? 0 : UTF8::unicode_length(utf8_str);
  typeArrayOop result = new_charArray(length, CHECK_NULL);
  if (length > 0) {
    UTF8::convert_to_unicode(utf8_str, result->char_at_addr(0), length);
  }
  return result;
}

typeArrayOop oopFactory::new_permanent_charArray(int length, TRAPS) {
  return typeArrayKlass::cast(Universe::charArrayKlassObj())->allocate_permanent(length, THREAD);
}

typeArrayOop oopFactory::new_permanent_byteArray(int length, TRAPS) {
  return typeArrayKlass::cast(Universe::byteArrayKlassObj())->allocate_permanent(length, THREAD);
}


typeArrayOop oopFactory::new_permanent_shortArray(int length, TRAPS) {
  return typeArrayKlass::cast(Universe::shortArrayKlassObj())->allocate_permanent(length, THREAD);
}


typeArrayOop oopFactory::new_permanent_intArray(int length, TRAPS) {
  return typeArrayKlass::cast(Universe::intArrayKlassObj())->allocate_permanent(length, THREAD);
}


typeArrayOop oopFactory::new_typeArray(BasicType type, int length, TRAPS) {
  klassOop type_asKlassOop = Universe::typeArrayKlassObj(type);
  typeArrayKlass* type_asArrayKlass = typeArrayKlass::cast(type_asKlassOop);
  typeArrayOop result = type_asArrayKlass->allocate(length, THREAD);
  return result;
}


objArrayOop oopFactory::new_objArray(klassOop klass, int length, TRAPS) {
  assert(klass->is_klass(), "must be instance class");
  if (klass->klass_part()->oop_is_array()) {
    return ((arrayKlass*)klass->klass_part())->allocate_arrayArray(1, length, THREAD);
  } else {
    assert (klass->klass_part()->oop_is_instance(), "new object array with klass not an instanceKlass");
    return ((instanceKlass*)klass->klass_part())->allocate_objArray(1, length, THREAD);
  }
}

objArrayOop oopFactory::new_system_objArray(int length, TRAPS) {
  int size = objArrayOopDesc::object_size(length);
  KlassHandle klass (THREAD, Universe::systemObjArrayKlassObj());
  objArrayOop o = (objArrayOop)
    Universe::heap()->permanent_array_allocate(klass, size, length, CHECK_NULL);
  // initialization not needed, allocated cleared
  return o;
}


constantPoolOop oopFactory::new_constantPool(int length,
                                             bool is_conc_safe,
                                             TRAPS) {
  constantPoolKlass* ck = constantPoolKlass::cast(Universe::constantPoolKlassObj());
  return ck->allocate(length, is_conc_safe, CHECK_NULL);
}


constantPoolCacheOop oopFactory::new_constantPoolCache(int length,
                                                       bool is_conc_safe,
                                                       TRAPS) {
  constantPoolCacheKlass* ck = constantPoolCacheKlass::cast(Universe::constantPoolCacheKlassObj());
  return ck->allocate(length, is_conc_safe, CHECK_NULL);
}


klassOop oopFactory::new_instanceKlass(int vtable_len, int itable_len,
                                       int static_field_size,
                                       unsigned int nonstatic_oop_map_count,
                                       ReferenceType rt, TRAPS) {
  instanceKlassKlass* ikk = instanceKlassKlass::cast(Universe::instanceKlassKlassObj());
  return ikk->allocate_instance_klass(vtable_len, itable_len, static_field_size, nonstatic_oop_map_count, rt, CHECK_NULL);
}


constMethodOop oopFactory::new_constMethod(int byte_code_size,
                                           int compressed_line_number_size,
                                           int localvariable_table_length,
                                           int checked_exceptions_length,
                                           bool is_conc_safe,
                                           TRAPS) {
  klassOop cmkObj = Universe::constMethodKlassObj();
  constMethodKlass* cmk = constMethodKlass::cast(cmkObj);
  return cmk->allocate(byte_code_size, compressed_line_number_size,
                       localvariable_table_length, checked_exceptions_length,
                       is_conc_safe,
                       CHECK_NULL);
}


methodOop oopFactory::new_method(int byte_code_size, AccessFlags access_flags,
                                 int compressed_line_number_size,
                                 int localvariable_table_length,
                                 int checked_exceptions_length,
                                 bool is_conc_safe,
                                 TRAPS) {
  methodKlass* mk = methodKlass::cast(Universe::methodKlassObj());
  assert(!access_flags.is_native() || byte_code_size == 0,
         "native methods should not contain byte codes");
  constMethodOop cm = new_constMethod(byte_code_size,
                                      compressed_line_number_size,
                                      localvariable_table_length,
                                      checked_exceptions_length,
                                      is_conc_safe, CHECK_NULL);
  constMethodHandle rw(THREAD, cm);
  return mk->allocate(rw, access_flags, CHECK_NULL);
}


methodDataOop oopFactory::new_methodData(methodHandle method, TRAPS) {
  methodDataKlass* mdk = methodDataKlass::cast(Universe::methodDataKlassObj());
  return mdk->allocate(method, CHECK_NULL);
}


compiledICHolderOop oopFactory::new_compiledICHolder(methodHandle method, KlassHandle klass, TRAPS) {
  compiledICHolderKlass* ck = (compiledICHolderKlass*) Universe::compiledICHolderKlassObj()->klass_part();
  compiledICHolderOop c = ck->allocate(CHECK_NULL);
  c->set_holder_method(method());
  c->set_holder_klass(klass());
  return c;
}
