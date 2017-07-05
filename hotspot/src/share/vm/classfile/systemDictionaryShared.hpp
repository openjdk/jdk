/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_SYSTEMDICTIONARYSHARED_HPP
#define SHARE_VM_CLASSFILE_SYSTEMDICTIONARYSHARED_HPP

#include "classfile/systemDictionary.hpp"
#include "classfile/dictionary.hpp"

class ClassFileStream;

class SystemDictionaryShared: public SystemDictionary {
public:
  static void initialize(TRAPS) {}
  static instanceKlassHandle find_or_load_shared_class(Symbol* class_name,
                                                       Handle class_loader,
                                                       TRAPS) {
    return instanceKlassHandle();
  }
  static void roots_oops_do(OopClosure* blk) {}
  static void oops_do(OopClosure* f) {}
  static bool is_sharing_possible(ClassLoaderData* loader_data) {
    oop class_loader = loader_data->class_loader();
    return (class_loader == NULL);
  }

  static Klass* dump_time_resolve_super_or_fail(Symbol* child_name,
                                                Symbol* class_name,
                                                Handle class_loader,
                                                Handle protection_domain,
                                                bool is_superclass,
                                                TRAPS) {
    return NULL;
  }

  static size_t dictionary_entry_size() {
    return sizeof(DictionaryEntry);
  }

  static void init_shared_dictionary_entry(Klass* k, DictionaryEntry* entry) {}

  static InstanceKlass* lookup_from_stream(Symbol* class_name,
                                           Handle class_loader,
                                           Handle protection_domain,
                                           ClassFileStream* st,
                                           bool verify,
                                           TRAPS) {
    return NULL;
  }
};

#endif // SHARE_VM_CLASSFILE_SYSTEMDICTIONARYSHARED_HPP
