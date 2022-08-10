/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

// This constructor is used only by SystemDictionaryShared::clone_dumptime_tables().
// See comments there about the need for making a deep copy.
DumpTimeLambdaProxyClassInfo::DumpTimeLambdaProxyClassInfo(const DumpTimeLambdaProxyClassInfo& src) {
  _proxy_klasses = NULL;
  if (src._proxy_klasses != NULL && src._proxy_klasses->length() > 0) {
    int n = src._proxy_klasses->length();
    _proxy_klasses = new (ResourceObj::C_HEAP, mtClassShared) GrowableArray<InstanceKlass*>(n, mtClassShared);
    for (int i = 0; i < n; i++) {
      _proxy_klasses->append(src._proxy_klasses->at(i));
    }
  }
}

DumpTimeLambdaProxyClassInfo::~DumpTimeLambdaProxyClassInfo() {
  if (_proxy_klasses != NULL) {
    delete _proxy_klasses;
  }
}

void LambdaProxyClassKey::mark_pointers() {
  ArchivePtrMarker::mark_pointer(&_caller_ik);
  ArchivePtrMarker::mark_pointer(&_instantiated_method_type);
  ArchivePtrMarker::mark_pointer(&_invoked_name);
  ArchivePtrMarker::mark_pointer(&_invoked_type);
  ArchivePtrMarker::mark_pointer(&_member_method);
  ArchivePtrMarker::mark_pointer(&_method_type);
}

unsigned int LambdaProxyClassKey::hash() const {
  return SystemDictionaryShared::hash_for_shared_dictionary((address)_caller_ik) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_invoked_name) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_invoked_type) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_method_type) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_instantiated_method_type);
}
