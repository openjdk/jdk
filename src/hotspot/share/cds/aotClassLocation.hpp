/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_AOTCLASSLOCATION_HPP
#define SHARE_CDS_AOTCLASSLOCATION_HPP

#include "memory/allocation.hpp"
#include "oops/array.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

class AllClassLocationStreams;
class ClassLocationStream;
class ClassPathZipEntry;
class LogStream;

// An AOTClassLocation is a location where the application is configured to load Java classes
// from. It can be:
// - the location of $JAVA_HOME/lib/modules
// - an entry in -Xbootclasspath/a
// - an entry in -classpath
// - a JAR file specified using --module-path.
//
// AOTClassLocation is similar to java.security.CodeSource, except:
// - Only local files/dirs are allowed. Directories must be empty. Network locations are not allowed.
// - No code signing information is recorded.
//
// We avoid using pointers in AOTClassLocation to avoid runtime pointer relocation. Each AOTClassLocation
// is a variable-size structure:
//    [ all fields specified below (sizeof(AOTClassLocation) bytes)          ]
//    [ path (_path_length bytes, including the terminating zero)         ]
//    [ manifest (_manifest_length bytes, including the terminating zero) ]
class AOTClassLocation {
public:
  enum class Group : int {
    MODULES_IMAGE,
    BOOT_CLASSPATH,
    APP_CLASSPATH,
    MODULE_PATH
  };
private:
  enum class FileType : int {
    NORMAL,
    DIR,
    NOT_EXIST
  };
  size_t   _path_length;     // does NOT include terminating zero
  size_t   _manifest_length; // does NOT include terminating zero
  bool     _check_time;
  bool     _from_cpattr;
  bool     _is_multi_release_jar; // is this a JAR file that has multi-release classes?
  FileType _file_type;
  Group    _group;
  int      _index; // index of this AOTClassLocation inside AOTClassLocationConfig::_class_locations
  time_t   _timestamp;
  int64_t  _filesize;

  static size_t header_size()      { return sizeof(AOTClassLocation); } // bytes
  size_t path_offset()       const { return header_size(); }
  size_t manifest_offset()   const { return path_offset() + _path_length + 1; }
  static char* read_manifest(JavaThread* current, const char* path, size_t& manifest_length);

public:
  static AOTClassLocation* allocate(JavaThread* current, const char* path, int index, Group group,
                                    bool from_cpattr = false, bool is_jrt = false);

  size_t total_size()                const { return manifest_offset() + _manifest_length + 1; }
  const char* path()                 const { return ((const char*)this) + path_offset();  }
  size_t manifest_length()           const { return _manifest_length; }
  const char* manifest()             const { return ((const char*)this) + manifest_offset(); }
  bool must_exist()                  const { return _file_type != FileType::NOT_EXIST; }
  bool must_not_exist()              const { return _file_type == FileType::NOT_EXIST; }
  bool is_dir()                      const { return _file_type == FileType::DIR; }
  int index()                        const { return _index; }
  bool is_modules_image()            const { return _group == Group::MODULES_IMAGE; }
  bool from_boot_classpath()         const { return _group == Group::BOOT_CLASSPATH; }
  bool from_app_classpath()          const { return _group == Group::APP_CLASSPATH; }
  bool from_module_path()            const { return _group == Group::MODULE_PATH; }
  bool is_multi_release_jar()        const { return _is_multi_release_jar; }

  // Only boot/app classpaths can contain unnamed module
  bool has_unnamed_module()          const { return from_boot_classpath() || from_app_classpath(); }

  char* get_cpattr() const;
  AOTClassLocation* write_to_archive() const;

  // Returns true IFF this AOTClassLocation is discovered from the -classpath or -Xbootclasspath/a by parsing the
  // "Class-Path" attribute of a JAR file.
  bool from_cpattr() const { return _from_cpattr; }
  const char* file_type_string() const;
  bool check(const char* runtime_path, bool has_aot_linked_classes) const;
};

// AOTClassLocationConfig
//
// Keep track of the set of AOTClassLocations used when an AOTCache is created.
// To load the AOTCache in a production run, the JVM must be using a compatible set of
// AOTClassLocations (subjected to AOTClassLocationConfig::validate()).
//
// In general, validation is performed on the AOTClassLocations to ensure the code locations used
// during AOTCache creation are the same as when the AOTCache is used during runtime.
// Non-existent entries are recorded during AOTCache creation. Those non-existent entries,
// if they are specified at runtime, must not exist.
//
// Some details on validation:
// - the boot classpath can be appended to at runtime if there's no app classpath and no
//   module path specified when an AOTCache is created;
// - the app classpath can be appended to at runtime;
// - the module path at runtime can be a superset of the one specified during AOTCache creation.

class AOTClassLocationConfig : public CHeapObj<mtClassShared> {
  using Group = AOTClassLocation::Group;
  using GrowableClassLocationArray = GrowableArrayCHeap<AOTClassLocation*, mtClassShared>;

  // Note: both of the following are non-null if we are dumping a dynamic archive.
  static AOTClassLocationConfig* _dumptime_instance;
  static const AOTClassLocationConfig* _runtime_instance;

  Array<AOTClassLocation*>* _class_locations; // jrt -> -Xbootclasspath/a -> -classpath -> --module_path
  static Array<ClassPathZipEntry*>* _dumptime_jar_files;

  int _boot_classpath_end;
  int _app_classpath_end;
  int _module_end;
  bool _has_non_jar_modules;
  bool _has_platform_classes;
  bool _has_app_classes;
  int  _max_used_index;
  size_t _dumptime_lcp_len;

  // accessors
  Array<AOTClassLocation*>* class_locations() const { return _class_locations; }

  void parse(JavaThread* current, GrowableClassLocationArray& tmp_array, ClassLocationStream& css,
             Group group, bool parse_manifest);
  void add_class_location(JavaThread* current, GrowableClassLocationArray& tmp_array, const char* path,
                       Group group, bool parse_manifest, bool from_cpattr);
  void dumptime_init_helper(TRAPS);

  bool check_classpaths(bool is_boot_classpath, bool has_aot_linked_classes,
                        int index_start, int index_end, ClassLocationStream& runtime_css,
                        bool use_lcp_match, const char* runtime_lcp, size_t runtime_lcp_len) const;
  bool check_module_paths(bool has_aot_linked_classes, int index_start, int index_end, ClassLocationStream& runtime_css,
                          bool* has_extra_module_paths) const;
  bool file_exists(const char* filename) const;
  bool check_paths_existence(ClassLocationStream& runtime_css) const;

  static const char* substitute(const char* path, size_t remove_prefix_len,
                                const char* prepend, size_t prepend_len);
  static const char* find_lcp(ClassLocationStream& css, size_t& lcp_len);
  bool need_lcp_match(AllClassLocationStreams& all_css) const;
  bool need_lcp_match_helper(int start, int end, ClassLocationStream& css) const;

  template <typename FUNC> void dumptime_iterate_helper(FUNC func) const {
    assert(_class_locations != nullptr, "sanity");
    int n = _class_locations->length();
    for (int i = 0; i < n; i++) {
      if (!func(_class_locations->at(i))) {
        break;
      }
    }
  }

  template <typename FUNC> void iterate(FUNC func) const {
    int n = class_locations()->length();
    for (int i = 0; i < n; i++) {
      if (!func(class_locations()->at(i))) {
        break;
      }
    }
  }

  void check_nonempty_dirs() const;
  bool need_to_check_app_classpath() const {
    return (num_app_classpaths() > 0) && (_max_used_index >= app_cp_start_index()) && has_platform_or_app_classes();
  }

  void print_dumptime_classpath(LogStream& ls, int index_start, int index_limit,
                                bool do_substitute, size_t remove_prefix_len,
                                const char* prepend, size_t prepend_len) const;

  void print_on(outputStream* st) const;
  void log_locations(const char* cache_filename, bool is_writing) const;

public:
  static AOTClassLocationConfig* dumptime() {
    assert(_dumptime_instance != nullptr, "can only be called when dumping an AOT cache");
    return _dumptime_instance;
  }

  static const AOTClassLocationConfig* runtime() {
    assert(_runtime_instance != nullptr, "can only be called when using an AOT cache");
    return _runtime_instance;
  }

  // Common accessors
  int boot_cp_start_index()          const { return 1; }
  int boot_cp_end_index()            const { return _boot_classpath_end; }
  int app_cp_start_index()           const { return boot_cp_end_index(); }
  int app_cp_end_index()             const { return _app_classpath_end; }
  int module_path_start_index()      const { return app_cp_end_index(); }
  int module_path_end_index()        const { return _module_end; }
  bool has_platform_or_app_classes() const { return _has_app_classes || _has_platform_classes; }
  bool has_non_jar_modules()         const { return _has_non_jar_modules; }
  int num_boot_classpaths()          const { return boot_cp_end_index() - boot_cp_start_index(); }
  int num_app_classpaths()           const { return app_cp_end_index() - app_cp_start_index(); }
  int num_module_paths()             const { return module_path_end_index() - module_path_start_index(); }

  int length() const {
    return _class_locations->length();
  }

  const AOTClassLocation* class_location_at(int index) const;
  int get_module_shared_path_index(Symbol* location) const;

  // Functions used only during dumptime
  static void dumptime_init(JavaThread* current);

  static void dumptime_set_has_app_classes() {
    _dumptime_instance->_has_app_classes = true;
  }

  static void dumptime_set_has_platform_classes() {
    _dumptime_instance->_has_platform_classes = true;
  }

  static void dumptime_update_max_used_index(int index) {
    if (_dumptime_instance == nullptr) {
      assert(index == 0, "sanity");
    } else if (_dumptime_instance->_max_used_index < index) {
      _dumptime_instance->_max_used_index = index;
    }
  }

  static void dumptime_check_nonempty_dirs() {
    _dumptime_instance->check_nonempty_dirs();
  }

  static bool dumptime_is_ready() {
    return _dumptime_instance != nullptr;
  }
  template <typename FUNC> static void dumptime_iterate(FUNC func) {
    _dumptime_instance->dumptime_iterate_helper(func);
  }

  AOTClassLocationConfig* write_to_archive() const;

  // Functions used only during runtime
  bool validate(const char* cache_filename, bool has_aot_linked_classes, bool* has_extra_module_paths) const;

  bool is_valid_classpath_index(int classpath_index, InstanceKlass* ik);

  static void print();
};


#endif // SHARE_CDS_AOTCLASSLOCATION_HPP
