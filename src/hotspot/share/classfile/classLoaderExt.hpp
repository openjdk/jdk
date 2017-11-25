/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_CLASSLOADEREXT_HPP
#define SHARE_VM_CLASSFILE_CLASSLOADEREXT_HPP

#include "classfile/classLoader.hpp"
#include "classfile/systemDictionary.hpp"
#include "oops/instanceKlass.hpp"
#include "runtime/handles.hpp"

class ClassListParser;

class ClassLoaderExt: public ClassLoader { // AllStatic
public:

  class Context {
    const char* _file_name;
  public:
    Context(const char* class_name, const char* file_name, TRAPS) {
      _file_name = file_name;
    }

    bool check(const ClassFileStream* stream, const int classpath_index) {
      return true;
    }

    bool should_verify(int classpath_index) {
      return false;
    }

    void record_result(Symbol* class_name,
                       const s2 classpath_index,
                       InstanceKlass* result, TRAPS) {
#if INCLUDE_CDS
      assert(DumpSharedSpaces, "Sanity");
      oop loader = result->class_loader();
      s2 classloader_type = ClassLoader::BOOT_LOADER;
      if (SystemDictionary::is_system_class_loader(loader)) {
        classloader_type = ClassLoader::APP_LOADER;
        ClassLoaderExt::set_has_app_classes();
      } else if (SystemDictionary::is_platform_class_loader(loader)) {
        classloader_type = ClassLoader::PLATFORM_LOADER;
        ClassLoaderExt::set_has_platform_classes();
      }
      result->set_shared_classpath_index(classpath_index);
      result->set_class_loader_type(classloader_type);
#endif
    }
  };

  static void append_boot_classpath(ClassPathEntry* new_entry) {
    ClassLoader::add_to_boot_append_entries(new_entry);
  }
  static void setup_search_paths() {}
  static bool is_boot_classpath(int classpath_index) {
   return true;
 }
  static Klass* load_one_class(ClassListParser* parser, TRAPS);
#if INCLUDE_CDS
  static void set_has_app_classes() {}
  static void set_has_platform_classes() {}
  static char* read_manifest(ClassPathEntry* entry, jint *manifest_size, TRAPS) {
    return NULL;
  }
  static void process_jar_manifest(ClassPathEntry* entry, bool check_for_duplicates) {}
#endif
};

#endif // SHARE_VM_CLASSFILE_CLASSLOADEREXT_HPP
