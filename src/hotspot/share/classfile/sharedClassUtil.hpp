/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_SHAREDCLASSUTIL_HPP
#define SHARE_VM_CLASSFILE_SHAREDCLASSUTIL_HPP

#include "classfile/sharedPathsMiscInfo.hpp"
#include "memory/filemap.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "oops/klass.hpp"

class FileMapHeaderExt: public FileMapInfo::FileMapHeader {
public:
  jshort _app_class_paths_start_index;  // Index of first app classpath entry
  jshort _app_module_paths_start_index; // Index of first module path entry
  bool   _verify_local;                 // BytecodeVerificationLocal setting
  bool   _verify_remote;                // BytecodeVerificationRemote setting
  bool   _has_platform_or_app_classes;  // Archive contains app classes

  FileMapHeaderExt() {
    _has_platform_or_app_classes = true;
  }
  void set_has_platform_or_app_classes(bool v) {
    _has_platform_or_app_classes = v;
  }
  bool has_platform_or_app_classes() { return _has_platform_or_app_classes; }
  virtual void populate(FileMapInfo* mapinfo, size_t alignment);
  virtual bool validate();
};

// In addition to SharedPathsMiscInfo, the following information is also stored
//
//
// + The value of Arguments::get_appclasspath() used during dumping.
//
class SharedPathsMiscInfoExt : public SharedPathsMiscInfo {
private:
  int   _app_offset;
public:
  enum {
    APP       = 5,
    MODULE    = 6
  };

  virtual const char* type_name(int type) {
    switch (type) {
    case APP:     return "APP";
    case MODULE:  return "MODULE";
    default:      return SharedPathsMiscInfo::type_name(type);
    }
  }

  virtual void print_path(outputStream* out, int type, const char* path);

  SharedPathsMiscInfoExt() : SharedPathsMiscInfo() {
    _app_offset = 0;
  }
  SharedPathsMiscInfoExt(char* buf, int size) : SharedPathsMiscInfo(buf, size) {
    _app_offset = 0;
  }

  virtual bool check(jint type, const char* path);

  void add_app_classpath(const char* path) {
    add_path(path, APP);
  }

  void record_app_offset() {
    _app_offset = get_used_bytes();
  }
  void pop_app() {
    _cur_ptr = _buf_start + _app_offset;
    write_jint(0);
  }
};

class SharedClassPathEntryExt: public SharedClassPathEntry {
public:
  //Maniest attributes
  bool _is_signed;
  void set_manifest(Array<u1>* manifest) {
    _manifest = manifest;
  }
};

class SharedClassUtil : AllStatic {
public:
  static SharedPathsMiscInfo* allocate_shared_paths_misc_info() {
    return new SharedPathsMiscInfoExt();
  }

  static SharedPathsMiscInfo* allocate_shared_paths_misc_info(char* buf, int size) {
    return new SharedPathsMiscInfoExt(buf, size);
  }

  static FileMapInfo::FileMapHeader* allocate_file_map_header() {
    return new FileMapHeaderExt();
  }

  static size_t file_map_header_size() {
    return sizeof(FileMapHeaderExt);
  }

  static size_t shared_class_path_entry_size() {
    return sizeof(SharedClassPathEntryExt);
  }

  static void update_shared_classpath(ClassPathEntry *cpe, SharedClassPathEntry* ent, TRAPS);
  static void initialize(TRAPS);

private:
  static void read_extra_data(const char* filename, TRAPS);

public:
  static bool is_classpath_entry_signed(int classpath_index);
};

#endif // SHARE_VM_CLASSFILE_SHAREDCLASSUTIL_HPP
