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

#include "precompiled.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/sharedClassUtil.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "memory/filemap.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "runtime/arguments.hpp"
#include "runtime/java.hpp"
#include "runtime/os.inline.hpp"

class ManifestStream: public ResourceObj {
  private:
  u1*   _buffer_start; // Buffer bottom
  u1*   _buffer_end;   // Buffer top (one past last element)
  u1*   _current;      // Current buffer position

 public:
  // Constructor
  ManifestStream(u1* buffer, int length) : _buffer_start(buffer),
                                           _current(buffer) {
    _buffer_end = buffer + length;
  }

  static bool is_attr(u1* attr, const char* name) {
    return strncmp((const char*)attr, name, strlen(name)) == 0;
  }

  static char* copy_attr(u1* value, size_t len) {
    char* buf = NEW_RESOURCE_ARRAY(char, len + 1);
    strncpy(buf, (char*)value, len);
    buf[len] = 0;
    return buf;
  }

  // The return value indicates if the JAR is signed or not
  bool check_is_signed() {
    u1* attr = _current;
    bool isSigned = false;
    while (_current < _buffer_end) {
      if (*_current == '\n') {
        *_current = '\0';
        u1* value = (u1*)strchr((char*)attr, ':');
        if (value != NULL) {
          assert(*(value+1) == ' ', "Unrecognized format" );
          if (strstr((char*)attr, "-Digest") != NULL) {
            isSigned = true;
            break;
          }
        }
        *_current = '\n'; // restore
        attr = _current + 1;
      }
      _current ++;
    }
    return isSigned;
  }
};

void SharedPathsMiscInfoExt::print_path(outputStream* out, int type, const char* path) {
  switch(type) {
  case APP:
    ClassLoader::trace_class_path("Expecting -Djava.class.path=", path);
    break;
  case MODULE:
    ClassLoader::trace_class_path("Checking module path: ", path);
    break;
  default:
    SharedPathsMiscInfo::print_path(out, type, path);
  }
}

bool SharedPathsMiscInfoExt::check(jint type, const char* path) {

  switch (type) {
  case APP:
    {
      // Prefix is OK: E.g., dump with -cp foo.jar, but run with -cp foo.jar:bar.jar
      size_t len = strlen(path);
      const char *appcp = Arguments::get_appclasspath();
      assert(appcp != NULL, "NULL app classpath");
      size_t appcp_len = strlen(appcp);
      if (appcp_len < len) {
        return fail("Run time APP classpath is shorter than the one at dump time: ", appcp);
      }
      ResourceMark rm;
      char* tmp_path;
      if (len == appcp_len) {
        tmp_path = (char*)appcp;
      } else {
        tmp_path = NEW_RESOURCE_ARRAY(char, len + 1);
        strncpy(tmp_path, appcp, len);
        tmp_path[len] = 0;
      }
      if (os::file_name_strcmp(path, tmp_path) != 0) {
        return fail("[APP classpath mismatch, actual: -Djava.class.path=", appcp);
      }
      if (appcp[len] != '\0' && appcp[len] != os::path_separator()[0]) {
        return fail("Dump time APP classpath is not a proper prefix of run time APP classpath: ", appcp);
      }
    }
    break;
  default:
    return SharedPathsMiscInfo::check(type, path);
  }

  return true;
}

void SharedClassUtil::update_shared_classpath(ClassPathEntry *cpe, SharedClassPathEntry* e, TRAPS) {
  ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
  SharedClassPathEntryExt* ent = (SharedClassPathEntryExt*)e;
  ResourceMark rm(THREAD);
  jint manifest_size;
  bool isSigned;

  if (cpe->is_jar_file()) {
    char* manifest = ClassLoaderExt::read_manifest(cpe, &manifest_size, CHECK);
    if (manifest != NULL) {
      ManifestStream* stream = new ManifestStream((u1*)manifest,
                                                  manifest_size);
      isSigned = stream->check_is_signed();
      if (isSigned) {
        ent->_is_signed = true;
      } else {
        // Copy the manifest into the shared archive
        manifest = ClassLoaderExt::read_raw_manifest(cpe, &manifest_size, CHECK);
        Array<u1>* buf = MetadataFactory::new_array<u1>(loader_data,
                                                        manifest_size,
                                                        THREAD);
        char* p = (char*)(buf->data());
        memcpy(p, manifest, manifest_size);
        ent->set_manifest(buf);
        ent->_is_signed = false;
      }
    }
  }
}

void SharedClassUtil::initialize(TRAPS) {
  if (UseSharedSpaces) {
    int size = FileMapInfo::get_number_of_shared_paths();
    if (size > 0) {
      SystemDictionaryShared::allocate_shared_data_arrays(size, THREAD);
      FileMapHeaderExt* header = (FileMapHeaderExt*)FileMapInfo::current_info()->header();
      ClassLoaderExt::init_paths_start_index(header->_app_class_paths_start_index);
      ClassLoaderExt::init_app_module_paths_start_index(header->_app_module_paths_start_index);
    }
  }

  if (DumpSharedSpaces) {
    if (SharedArchiveConfigFile) {
      read_extra_data(SharedArchiveConfigFile, THREAD);
    }
  }
}

void SharedClassUtil::read_extra_data(const char* filename, TRAPS) {
  HashtableTextDump reader(filename);
  reader.check_version("VERSION: 1.0");

  while (reader.remain() > 0) {
    int utf8_length;
    int prefix_type = reader.scan_prefix(&utf8_length);
    ResourceMark rm(THREAD);
    char* utf8_buffer = NEW_RESOURCE_ARRAY(char, utf8_length);
    reader.get_utf8(utf8_buffer, utf8_length);

    if (prefix_type == HashtableTextDump::SymbolPrefix) {
      SymbolTable::new_symbol(utf8_buffer, utf8_length, THREAD);
    } else{
      assert(prefix_type == HashtableTextDump::StringPrefix, "Sanity");
      utf8_buffer[utf8_length] = '\0';
      oop s = StringTable::intern(utf8_buffer, THREAD);
    }
  }
}

bool SharedClassUtil::is_classpath_entry_signed(int classpath_index) {
  assert(classpath_index >= 0, "Sanity");
  SharedClassPathEntryExt* ent = (SharedClassPathEntryExt*)
    FileMapInfo::shared_path(classpath_index);
  return ent->_is_signed;
}

void FileMapHeaderExt::populate(FileMapInfo* mapinfo, size_t alignment) {
  FileMapInfo::FileMapHeader::populate(mapinfo, alignment);

  ClassLoaderExt::finalize_shared_paths_misc_info();
  _app_class_paths_start_index = ClassLoaderExt::app_class_paths_start_index();
  _app_module_paths_start_index = ClassLoaderExt::app_module_paths_start_index();

  _verify_local = BytecodeVerificationLocal;
  _verify_remote = BytecodeVerificationRemote;
  _has_platform_or_app_classes = ClassLoaderExt::has_platform_or_app_classes();
}

bool FileMapHeaderExt::validate() {
  if (!FileMapInfo::FileMapHeader::validate()) {
    return false;
  }

  // This must be done after header validation because it might change the
  // header data
  const char* prop = Arguments::get_property("java.system.class.loader");
  if (prop != NULL) {
    warning("Archived non-system classes are disabled because the "
            "java.system.class.loader property is specified (value = \"%s\"). "
            "To use archived non-system classes, this property must be not be set", prop);
    _has_platform_or_app_classes = false;
  }

  // For backwards compatibility, we don't check the verification setting
  // if the archive only contains system classes.
  if (_has_platform_or_app_classes &&
      ((!_verify_local && BytecodeVerificationLocal) ||
       (!_verify_remote && BytecodeVerificationRemote))) {
    FileMapInfo::fail_continue("The shared archive file was created with less restrictive "
                  "verification setting than the current setting.");
    return false;
  }

  return true;
}
