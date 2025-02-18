/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_AOTCODESOURCE_HPP
#define SHARE_CDS_AOTCODESOURCE_HPP

#include "memory/allocation.hpp"
#include "oops/array.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class AllCodeSourceStreams;
class CodeSourceStream;
class LogStream;

// An AOTCodeSource is a location where the application is configured to load Java classes
// from. It can be:
// - the location of $JAVA_HOME/lib/modules
// - an entry in -Xbootclasspath/a
// - an entry in -classpath
// - a JAR file specified using --module-path.
//
// AOTCodeSource is similar to java.security.CodeSource, except:
// - Only local files/dirs are allowed. Directories must be empty. Network locations are not allowed.
// - No code signing information is recorded.
//
// We avoid using pointers in AOTCodeSource to avoid runtime pointer relocation. Each AOTCodeSource
// is a variable-size structure:
//    [ all fields specified below (sizeof(AOTCodeSource) bytes)          ]
//    [ path (_path_length bytes, including the terminating zero)         ]
//    [ manifest (_manifest_length bytes, including the terminating zero) ]
class AOTCodeSource {
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
  int      _index; // index of this AOTCodeSource inside AOTCodeSourceConfig::_code_sources
  time_t   _timestamp;
  int64_t  _filesize;

  static size_t header_size()      { return sizeof(AOTCodeSource); } // bytes
  size_t path_offset()       const { return header_size(); }
  size_t manifest_offset()   const { return path_offset() + _path_length + 1; }
  static char* read_manifest(JavaThread* current, const char* path, size_t& manifest_length);

public:
  static AOTCodeSource* allocate(JavaThread* current, const char* path, int index, Group group,
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
  AOTCodeSource* write_to_archive() const;

  // Returns true IFF this AOTCodeSource is discovered from the -classpath or -Xbootclasspath/a by parsing the
  // "Class-Path" attribute of a JAR file.
  bool from_cpattr() const { return _from_cpattr; }
  const char* file_type_string() const;
  bool check(const char* runtime_path, bool has_aot_linked_classes) const;
};

// AOTCodeSourceConfig
//
// Keep track of the set of AOTCodeSources used when an AOTCache is created.
// To load the AOTCache in a production run, the JVM must be using a compatible set of
// AOTCodeSources (subjected to AOTCodeSourceConfig::validate()).
//
// In general, validation is performed on the AOTCodeSources to ensure the code sources used
// during AOTCache creation are the same as when the AOTCache is used during runtime.
// Non-existent entries are recorded during AOTCache creation. Those non-existent entries
// must not exist during runtime.
//
// Some details on validation:
// - the boot classpath could be appended during runtime if there's no app classpath and
//   module path specified when an AOTCache is created;
// - the app classpath could be appended during runtime;
// - the module path during runtime could be a superset of the one specified during AOTCache creation.

class AOTCodeSourceConfig : public CHeapObj<mtClassShared> {
  using Group = AOTCodeSource::Group;
  using GrowableCodeSourceArray = GrowableArrayCHeap<AOTCodeSource*, mtClassShared>;

  // Note: both of the following are non-null if we are dumping a dynamic archive.
  static AOTCodeSourceConfig* _dumptime_instance;
  static const AOTCodeSourceConfig* _runtime_instance;

  Array<AOTCodeSource*>* _code_sources; // jrt -> -Xbootclasspath/a -> -classpath -> --module_path
  int _boot_classpath_end;
  int _app_classpath_end;
  int _module_end;
  bool _has_non_jar_modules;
  bool _has_platform_classes;
  bool _has_app_classes;
  int  _max_used_index;
  size_t _dumptime_lcp_len;

  // accessors
  Array<AOTCodeSource*>* code_sources() const { return _code_sources; }

  void parse(JavaThread* current, GrowableCodeSourceArray& tmp_array, CodeSourceStream& css,
             Group group, bool parse_manifest);
  void add_code_source(JavaThread* current, GrowableCodeSourceArray& tmp_array, const char* path,
                       Group group, bool parse_manifest, bool from_cpattr);
  void dumptime_init_helper(TRAPS);

  bool check_classpaths(bool is_boot_classpath, bool has_aot_linked_classes,
                        int index_start, int index_end, CodeSourceStream& runtime_css,
                        bool use_lcp_match, const char* runtime_lcp, size_t runtime_lcp_len) const;
  bool check_module_paths(bool has_aot_linked_classes, int index_start, int index_end, CodeSourceStream& runtime_css,
                          bool* has_extra_module_paths) const;
  bool check_paths_existence(CodeSourceStream& runtime_css) const;

  static const char* substitute(const char* path, size_t remove_prefix_len,
                                const char* prepend, size_t prepend_len);
  static const char* find_lcp(CodeSourceStream& css, size_t& lcp_len);
  bool need_lcp_match(AllCodeSourceStreams& all_css) const;
  bool need_lcp_match_helper(int start, int end, CodeSourceStream& css) const;

  template <typename FUNC> void dumptime_iterate_helper(FUNC func) const {
    assert(_code_sources != nullptr, "sanity");
    int n = _code_sources->length();
    for (int i = 0; i < n; i++) {
      if (!func(_code_sources->at(i))) {
        break;
      }
    }
  }

  template <typename FUNC> void iterate(FUNC func) const {
    int n = code_sources()->length();
    for (int i = 0; i < n; i++) {
      if (!func(code_sources()->at(i))) {
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
public:
  static AOTCodeSourceConfig* dumptime() {
    assert(_dumptime_instance != nullptr, "can only be called when dumping an AOT cache");
    return _dumptime_instance;
  }

  static const AOTCodeSourceConfig* runtime() {
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
    return _code_sources->length();
  }

  const AOTCodeSource* code_source_at(int index) const;
  int get_module_shared_path_index(Symbol* location) const;

  // Functions used only during dumptime
  static void dumptime_init(TRAPS);

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

  AOTCodeSourceConfig* write_to_archive() const;

  // Functions used only during runtime
  bool validate(bool has_aot_linked_classes, bool* has_extra_module_paths) const;
};


#endif // SHARE_CDS_AOTCODESOURCE_HPP
