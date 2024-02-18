/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/archiveBuilder.hpp"
#include "cds/lambdaProxyClassDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "memory/resourceArea.hpp"

DumpTimeLambdaProxyClassInfo::~DumpTimeLambdaProxyClassInfo() {
  if (_proxy_klasses != nullptr) {
    delete _proxy_klasses;
  }
}

void LambdaProxyClassKey::init_for_archive(LambdaProxyClassKey& dumptime_key) {
  ArchiveBuilder* b = ArchiveBuilder::current();

  b->write_pointer_in_buffer(&_caller_ik,                dumptime_key._caller_ik);
  b->write_pointer_in_buffer(&_instantiated_method_type, dumptime_key._instantiated_method_type);
  b->write_pointer_in_buffer(&_invoked_name,             dumptime_key._invoked_name);
  b->write_pointer_in_buffer(&_invoked_type,             dumptime_key._invoked_type);
  b->write_pointer_in_buffer(&_member_method,            dumptime_key._member_method);
  b->write_pointer_in_buffer(&_method_type,              dumptime_key._method_type);
}

unsigned int LambdaProxyClassKey::hash() const {
  return SystemDictionaryShared::hash_for_shared_dictionary((address)_caller_ik) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_invoked_name) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_invoked_type) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_method_type) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_instantiated_method_type);
}

#ifndef PRODUCT
void LambdaProxyClassKey::print_on(outputStream* st) const {
  ResourceMark rm;
  st->print_cr("LambdaProxyClassKey       : " INTPTR_FORMAT " hash: %0x08x", p2i(this), hash());
  st->print_cr("_caller_ik                : %s", _caller_ik->external_name());
  st->print_cr("_instantiated_method_type : %s", _instantiated_method_type->as_C_string());
  st->print_cr("_invoked_name             : %s", _invoked_name->as_C_string());
  st->print_cr("_invoked_type             : %s", _invoked_type->as_C_string());
  st->print_cr("_member_method            : %s", _member_method->name()->as_C_string());
  st->print_cr("_method_type              : %s", _method_type->as_C_string());
}

void RunTimeLambdaProxyClassInfo::print_on(outputStream* st) const {
  _key.print_on(st);
}
#endif

void RunTimeLambdaProxyClassInfo::init(LambdaProxyClassKey& key, DumpTimeLambdaProxyClassInfo& info) {
  _key.init_for_archive(key);
  ArchiveBuilder::current()->write_pointer_in_buffer(&_proxy_klass_head,
                                                     info._proxy_klasses->at(0));
}
