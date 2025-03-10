/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/archiveBuilder.hpp"
#include "cds/lambdaProxyClassDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "memory/resourceArea.hpp"

DumpTimeLambdaProxyClassInfo::~DumpTimeLambdaProxyClassInfo() {
  if (_proxy_klasses != nullptr) {
    delete _proxy_klasses;
  }
}

unsigned int LambdaProxyClassKey::hash() const {
  return SystemDictionaryShared::hash_for_shared_dictionary((address)_caller_ik) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_invoked_name) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_invoked_type) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_method_type) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_instantiated_method_type);
}

unsigned int RunTimeLambdaProxyClassKey::hash() const {
  return primitive_hash<u4>(_caller_ik) +
         primitive_hash<u4>(_invoked_name) +
         primitive_hash<u4>(_invoked_type) +
         primitive_hash<u4>(_method_type) +
         primitive_hash<u4>(_instantiated_method_type);
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

void RunTimeLambdaProxyClassKey::print_on(outputStream* st) const {
  ResourceMark rm;
  st->print_cr("LambdaProxyClassKey       : " INTPTR_FORMAT " hash: %0x08x", p2i(this), hash());
  st->print_cr("_caller_ik                : %d", _caller_ik);
  st->print_cr("_instantiated_method_type : %d", _instantiated_method_type);
  st->print_cr("_invoked_name             : %d", _invoked_name);
  st->print_cr("_invoked_type             : %d", _invoked_type);
  st->print_cr("_member_method            : %d", _member_method);
  st->print_cr("_method_type              : %d", _method_type);
}

void RunTimeLambdaProxyClassInfo::print_on(outputStream* st) const {
  _key.print_on(st);
}
#endif

void RunTimeLambdaProxyClassInfo::init(LambdaProxyClassKey& key, DumpTimeLambdaProxyClassInfo& info) {
  _key = RunTimeLambdaProxyClassKey::init_for_dumptime(key);
  ArchiveBuilder::current()->write_pointer_in_buffer(&_proxy_klass_head,
                                                     info._proxy_klasses->at(0));
}
