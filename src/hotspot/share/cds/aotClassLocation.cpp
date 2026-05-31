/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotClassLocation.hpp"
#include "cds/aotLogging.hpp"
#include "cds/aotMetaspace.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/filemap.hpp"
#include "cds/serializeClosure.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/javaClasses.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "oops/array.hpp"
#include "oops/objArrayKlass.hpp"
#include "runtime/arguments.hpp"
#include "utilities/classpathStream.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/stringUtils.hpp"

#include <errno.h>
#include <sys/stat.h>

Array<ClassPathZipEntry*>* AOTClassLocationConfig::_dumptime_jar_files = nullptr;
AOTClassLocationConfig* AOTClassLocationConfig::_dumptime_instance = nullptr;
const AOTClassLocationConfig* AOTClassLocationConfig::_runtime_instance = nullptr;

// A ClassLocationStream represents a list of code locations, which can be iterated using
// start() and has_next().
class ClassLocationStream {
protected:
  GrowableArray<const char*> _array;
  int _current;

  // Add one path to this stream.
  void add_one_path(const char* path) {
    _array.append(path);
  }

  // Add all paths specified in cp; cp must be from -classpath or -Xbootclasspath/a.
  void add_paths_in_classpath(const char* cp) {
    ClasspathStream cp_stream(cp);
    while (cp_stream.has_next()) {
      add_one_path(cp_stream.get_next());
    }
  }

public:
  ClassLocationStream() : _array(), _current(0) {}

  void print(outputStream* st) const {
    const char* sep = "";
    for (int i = 0; i < _array.length(); i++) {
      st->print("%s%s", sep, _array.at(i));
      sep = os::path_separator();
    }
  }

  void add(ClassLocationStream& css) {
    for (css.start(); css.has_next();) {
      add_one_path(css.get_next());
    }
  }

  // Iteration
  void start() { _current = 0; }
  bool has_next() const { return _current < _array.length(); }
  const char* get_next() {
    return _array.at(_current++);
  }

  int current() const { return _current; }
  bool is_empty() const { return _array.length() == 0; }
};

class BootCpClassLocationStream : public ClassLocationStream {
public:
  BootCpClassLocationStream() : ClassLocationStream() {
    // Arguments::get_boot_class_path() contains $JAVA_HOME/lib/modules, but we treat that separately
    for (const char* bootcp = Arguments::get_boot_class_path(); *bootcp != '\0'; ++bootcp) {
      if (*bootcp == *os::path_separator()) {
        ++bootcp;
        add_paths_in_classpath(bootcp);
        break;
      }
    }
  }
};

class AppCpClassLocationStream : public ClassLocationStream {
public:
  AppCpClassLocationStream() : ClassLocationStream() {
    const char* appcp = Arguments::get_appclasspath();
    if (strcmp(appcp, ".") == 0) {
      appcp = "";
    }
    add_paths_in_classpath(appcp);
  }
};

class ModulePathClassLocationStream : public ClassLocationStream {
  bool _has_non_jar_modules;
public:
  ModulePathClassLocationStream();
  bool has_non_jar_modules() { return _has_non_jar_modules; }
};

// AllClassLocationStreams is used to iterate over all the code locations that
// are available to the application from -Xbootclasspath, -classpath and --module-path.
// When creating an AOT cache, we store the contents from AllClassLocationStreams
// into an array of AOTClassLocations. See AOTClassLocationConfig::dumptime_init_helper().
// When loading the AOT cache in a production run, we compare the contents of the
// stored AOTClassLocations against the current AllClassLocationStreams to determine whether
// the AOT cache is compatible with the current JVM. See AOTClassLocationConfig::validate().
class AllClassLocationStreams {
  BootCpClassLocationStream _boot_cp;          // Specified by -Xbootclasspath/a
  AppCpClassLocationStream _app_cp;            // Specified by -classpath
  ModulePathClassLocationStream _module_path;  // Specified by --module-path
  ClassLocationStream _boot_and_app_cp;        // Convenience for iterating over both _boot and _app
public:
  BootCpClassLocationStream& boot_cp()             { return _boot_cp; }
  AppCpClassLocationStream& app_cp()               { return _app_cp; }
  ModulePathClassLocationStream& module_path()     { return _module_path; }
  ClassLocationStream& boot_and_app_cp()           { return _boot_and_app_cp; }

  AllClassLocationStreams() : _boot_cp(), _app_cp(), _module_path(), _boot_and_app_cp() {
    _boot_and_app_cp.add(_boot_cp);
    _boot_and_app_cp.add(_app_cp);
  }
};

static bool has_jar_suffix(const char* filename) {
  // In jdk.internal.module.ModulePath.readModule(), it checks for the ".jar" suffix.
  // Performing the same check here.
  const char* dot = strrchr(filename, '.');
  if (dot != nullptr && strcmp(dot + 1, "jar") == 0) {
    return true;
  }
  return false;
}

static int compare_module_path_by_name(const char** p1, const char** p2) {
  return strcmp(*p1, *p2);
}

ModulePathClassLocationStream::ModulePathClassLocationStream() : ClassLocationStream(), _has_non_jar_modules(false) {
  // Note: for handling of --module-path, see
  //   https://openjdk.org/jeps/261#Module-paths
  //   https://docs.oracle.com/en/java/javase/23/docs/api/java.base/java/lang/module/ModuleFinder.html#of(java.nio.file.Path...)

  const char* jdk_module_path = Arguments::get_property("jdk.module.path");
  if (jdk_module_path == nullptr) {
    return;
  }

  ClasspathStream cp_stream(jdk_module_path);
  while (cp_stream.has_next()) {
    const char* path = cp_stream.get_next();
    DIR* dirp = os::opendir(path);
    if (dirp == nullptr && errno == ENOTDIR && has_jar_suffix(path)) {
      add_one_path(path);
    } else if (dirp != nullptr) {
      struct dirent* dentry;
      bool found_jar = false;
      while ((dentry = os::readdir(dirp)) != nullptr) {
        const char* file_name = dentry->d_name;
        if (has_jar_suffix(file_name)) {
          size_t full_name_len = strlen(path) + strlen(file_name) + strlen(os::file_separator()) + 1;
          char* full_name = NEW_RESOURCE_ARRAY(char, full_name_len);
          int n = os::snprintf(full_name, full_name_len, "%s%s%s", path, os::file_separator(), file_name);
          assert((size_t)n == full_name_len - 1, "Unexpected number of characters in string");
          add_one_path(full_name);
          found_jar = true;
        } else if (strcmp(file_name, ".") != 0 && strcmp(file_name, "..") != 0) {
          // Found some non jar entries
          _has_non_jar_modules = true;
          log_info(class, path)("Found non-jar path: '%s%s%s'", path, os::file_separator(), file_name);
        }
      }
      if (!found_jar) {
        log_info(class, path)("Found exploded module path: '%s'", path);
        _has_non_jar_modules = true;
      }
      os::closedir(dirp);
    } else {
      _has_non_jar_modules = true;
    }
  }

  _array.sort(compare_module_path_by_name);
}

AOTClassLocation* AOTClassLocation::allocate(JavaThread* current, const char* path, int index,
                                             Group group, bool from_cpattr, bool is_jrt) {
  size_t path_length = 0;
  size_t manifest_length = 0;
  bool check_time = false;
  time_t timestamp = 0;
  int64_t filesize = 0;
  FileType type = FileType::NORMAL;
  // Do not record the actual path of the jrt, as the entire JDK can be moved to a different
  // directory.
  const char* recorded_path = is_jrt ? "" : path;
  path_length = strlen(recorded_path);

  struct stat st;
  if (os::stat(path, &st) == 0) {
    if ((st.st_mode & S_IFMT) == S_IFDIR) {
      type = FileType::DIR;
    } else {
      timestamp = st.st_mtime;
      filesize = st.st_size;

      // The timestamp of $JAVA_HOME/lib/modules is not checked at runtime.
      check_time = !is_jrt;
    }
  } else if (errno == ENOENT) {
    // We allow the file to not exist, as long as it also doesn't exist during runtime.
    type = FileType::NOT_EXIST;
  } else {
    aot_log_error(aot)("Unable to open file %s.", path);
    AOTMetaspace::unrecoverable_loading_error();
  }

  ResourceMark rm(current);
  char* manifest = nullptr;

  if (!is_jrt && type == FileType::NORMAL) {
    manifest = read_manifest(current, path, manifest_length); // resource allocated
  }

  size_t cs_size = header_size() +
    + path_length + 1 /* nul-terminated */
    + manifest_length + 1; /* nul-terminated */

  AOTClassLocation* cs = (AOTClassLocation*)os::malloc(cs_size, mtClassShared);
  memset(cs, 0, cs_size);
  cs->_path_length = path_length;
  cs->_manifest_length = manifest_length;
  cs->_check_time = check_time;
  cs->_from_cpattr = from_cpattr;
  cs->_timestamp = check_time ? timestamp : 0;
  cs->_filesize = filesize;
  cs->_file_type = type;
  cs->_group = group;
  cs->_index = index;

  strcpy(((char*)cs) + cs->path_offset(), recorded_path);
  if (manifest_length > 0) {
    memcpy(((char*)cs) + cs->manifest_offset(), manifest, manifest_length);
  }
  assert(*(cs->manifest() + cs->manifest_length()) == '\0', "should be nul-terminated");

  if (strstr(cs->manifest(), "Multi-Release: true") != nullptr) {
    cs->_is_multi_release_jar = true;
  }

  if (strstr(cs->manifest(), "Extension-List:") != nullptr) {
    vm_exit_during_cds_dumping(err_msg("-Xshare:dump does not support Extension-List in JAR manifest: %s", path));
  }

  return cs;
}

char* AOTClassLocation::read_manifest(JavaThread* current, const char* path, size_t& manifest_length) {
  manifest_length = 0;

  struct stat st;
  if (os::stat(path, &st) != 0) {
    return nullptr;
  }

  ClassPathEntry* cpe = ClassLoader::create_class_path_entry(current, path, &st);
  if (cpe == nullptr) {
    // <path> is a file, but not a JAR file
    return nullptr;
  }
  assert(cpe->is_jar_file(), "should not be called with a directory");

  const char* name = "META-INF/MANIFEST.MF";
  char* manifest;
  jint size;
  manifest = (char*) ((ClassPathZipEntry*)cpe)->open_entry(current, name, &size, true);

  if (manifest == nullptr || size <= 0) { // No Manifest
    manifest_length = 0;
  } else {
    manifest_length = (size_t)size;
  }

  delete cpe;
  return manifest;
}

// The result is resource allocated.
char* AOTClassLocation::get_cpattr() const {
  if (_manifest_length == 0) {
    return nullptr;
  }

  size_t buf_size = _manifest_length + 1;
  char* buf = NEW_RESOURCE_ARRAY(char, buf_size);
  memcpy(buf, manifest(), _manifest_length);
  buf[_manifest_length] = 0; // make sure it's 0-terminated

  // See http://docs.oracle.com/javase/6/docs/technotes/guides/jar/jar.html#JAR%20Manifest
  // Replace all CR/LF and CR with LF
  StringUtils::replace_no_expand(buf, "\r\n", "\n");
  // Remove all new-line continuation (remove all "\n " substrings)
  StringUtils::replace_no_expand(buf, "\n ", "");

  const char* tag = "Class-Path: ";
  size_t tag_len = strlen(tag);
  char* found = nullptr;
  char* line_start = buf;
  char* end = buf + _manifest_length;

  assert(*end == 0, "must be nul-terminated");

  while (line_start < end) {
    char* line_end = strchr(line_start, '\n');
    if (line_end == nullptr) {
      // JAR spec require the manifest file to be terminated by a new line.
      break;
    }
    if (strncmp(tag, line_start, tag_len) == 0) {
      if (found != nullptr) {
        // Same behavior as jdk/src/share/classes/java/util/jar/Attributes.java
        // If duplicated entries are found, the last one is used.
        log_warning(aot)("Warning: Duplicate name in Manifest: %s.\n"
                         "Ensure that the manifest does not have duplicate entries, and\n"
                         "that blank lines separate individual sections in both your\n"
                         "manifest and in the META-INF/MANIFEST.MF entry in the jar file:\n%s\n", tag, path());
      }
      found = line_start + tag_len;
      assert(found <= line_end, "sanity");
      *line_end = '\0';
    }
    line_start = line_end + 1;
  }

  return found;
}

AOTClassLocation* AOTClassLocation::write_to_archive() const {
  AOTClassLocation* archived_copy = (AOTClassLocation*)ArchiveBuilder::ro_region_alloc(total_size());
  memcpy((char*)archived_copy, (char*)this, total_size());
  return archived_copy;
}

const char* AOTClassLocation::file_type_string() const {
  switch (_file_type) {
  case FileType::NORMAL: return "file";
  case FileType::DIR: return "dir";
  case FileType::NOT_EXIST: default: return "not-exist";
  }
}

bool AOTClassLocation::check(const char* runtime_path, bool has_aot_linked_classes) const {
  struct stat st;
  if (os::stat(runtime_path, &st) != 0) {
    if (_file_type != FileType::NOT_EXIST) {
      aot_log_warning(aot)("Required classpath entry does not exist: %s", runtime_path);
      return false;
    }
  } else if ((st.st_mode & S_IFMT) == S_IFDIR) {
    if (_file_type == FileType::NOT_EXIST) {
      aot_log_warning(aot)("'%s' must not exist", runtime_path);
      return false;
    }
    if (_file_type == FileType::NORMAL) {
      aot_log_warning(aot)("'%s' must be a file", runtime_path);
      return false;
    }
    if (!os::dir_is_empty(runtime_path)) {
      aot_log_warning(aot)("directory is not empty: '%s'", runtime_path);
      return false;
    }
  } else {
    if (_file_type == FileType::NOT_EXIST) {
      aot_log_warning(aot)("'%s' must not exist", runtime_path);
      if (has_aot_linked_classes) {
        aot_log_error(aot)("CDS archive has aot-linked classes. It cannot be used because the "
                       "file %s exists", runtime_path);
        return false;
      } else {
        aot_log_warning(aot)("Archived non-system classes are disabled because the "
                         "file %s exists", runtime_path);
        FileMapInfo::current_info()->set_has_platform_or_app_classes(false);
        if (DynamicArchive::is_mapped()) {
          FileMapInfo::dynamic_info()->set_has_platform_or_app_classes(false);
        }
      }
    }
    if (_file_type == FileType::DIR) {
      aot_log_warning(aot)("'%s' must be a directory", runtime_path);
      return false;
    }
    bool size_differs = _filesize != st.st_size;
    bool time_differs = _check_time && (_timestamp != st.st_mtime);
    if (size_differs || time_differs) {
      aot_log_warning(aot)("This file is not the one used while building the %s: '%s'%s%s",
                       CDSConfig::type_of_archive_being_loaded(),
                       runtime_path,
                       time_differs ? ", timestamp has changed" : "",
                       size_differs ? ", size has changed" : "");
      return false;
    }
  }

  log_info(class, path)("ok");
  return true;
}

void AOTClassLocationConfig::dumptime_init(JavaThread* current) {
  assert(CDSConfig::is_dumping_archive(), "");
  _dumptime_instance = NEW_C_HEAP_OBJ(AOTClassLocationConfig, mtClassShared);
  _dumptime_instance->dumptime_init_helper(current);
  if (current->has_pending_exception()) {
    // we can get an exception only when we run out of metaspace, but that
    // shouldn't happen this early in bootstrap.
    java_lang_Throwable::print(current->pending_exception(), tty);
    vm_exit_during_initialization("AOTClassLocationConfig::dumptime_init_helper() failed unexpectedly");
  }

  if (CDSConfig::is_dumping_final_static_archive()) {
    // The _max_used_index is usually updated by ClassLoader::record_result(). However,
    // when dumping the final archive, the classes are loaded from their images in
    // the AOT config file, so we don't go through ClassLoader::record_result().
    dumptime_update_max_used_index(runtime()->_max_used_index); // Same value as recorded in the training run.
  }
}

void AOTClassLocationConfig::dumptime_init_helper(TRAPS) {
  ResourceMark rm;
  GrowableClassLocationArray tmp_array;
  AllClassLocationStreams all_css;

  AOTClassLocation* jrt = AOTClassLocation::allocate(THREAD, ClassLoader::get_jrt_entry()->name(),
                                               0, Group::MODULES_IMAGE,
                                               /*from_cpattr*/false, /*is_jrt*/true);
  log_info(class, path)("path [%d] = (modules image)", tmp_array.length());
  tmp_array.append(jrt);

  parse(THREAD, tmp_array, all_css.boot_cp(), Group::BOOT_CLASSPATH, /*parse_manifest*/true);
  _boot_classpath_end = tmp_array.length();

  parse(THREAD, tmp_array, all_css.app_cp(), Group::APP_CLASSPATH, /*parse_manifest*/true);
  _app_classpath_end = tmp_array.length();

  parse(THREAD, tmp_array, all_css.module_path(), Group::MODULE_PATH, /*parse_manifest*/false);
  _module_end = tmp_array.length();

  _class_locations =  MetadataFactory::new_array<AOTClassLocation*>(ClassLoaderData::the_null_class_loader_data(),
                                                               tmp_array.length(), CHECK);
  for (int i = 0; i < tmp_array.length(); i++) {
    _class_locations->at_put(i, tmp_array.at(i));
  }

  _dumptime_jar_files = MetadataFactory::new_array<ClassPathZipEntry*>(ClassLoaderData::the_null_class_loader_data(),
                                                                       tmp_array.length(), CHECK);
  for (int i = 1; i < tmp_array.length(); i++) {
    ClassPathZipEntry* jar_file = ClassLoader::create_class_path_zip_entry(tmp_array.at(i)->path());
    _dumptime_jar_files->at_put(i, jar_file); // may be null if the path is not a valid JAR file
  }

  const char* lcp = find_lcp(all_css.boot_and_app_cp(), _dumptime_lcp_len);
  if (_dumptime_lcp_len > 0) {
    log_info(class, path)("Longest common prefix = %s (%zu chars)", lcp, _dumptime_lcp_len);
    os::free((void*)lcp);
  } else {
    assert(_dumptime_lcp_len == 0, "sanity");
    log_info(class, path)("Longest common prefix = <none> (0 chars)");
  }

  _has_non_jar_modules = all_css.module_path().has_non_jar_modules();
  _has_platform_classes = false;
  _has_app_classes = false;
  _max_used_index = 0;
}

// Find the longest common prefix of two paths, up to max_lcp_len.
// E.g.   p1 = "/a/b/foo"
//        p2 = "/a/b/bar"
//        max_lcp_len = 3
// -> returns 3
static size_t find_lcp_of_two_paths(const char* p1, const char* p2, size_t max_lcp_len) {
  size_t lcp_len = 0;
  char sep = os::file_separator()[0];
  for (size_t i = 0; ; i++) {
    char c1 = *p1++;
    char c2 = *p2++;
    if (c1 == 0 || c2 == 0 || c1 != c2) {
      break;
    }
    if (c1 == sep) {
      lcp_len = i + 1;
      assert(lcp_len <= max_lcp_len, "sanity");
      if (lcp_len == max_lcp_len) {
        break;
      }
    }
  }
  return lcp_len;
}

// cheap-allocated if lcp_len > 0
const char* AOTClassLocationConfig::find_lcp(ClassLocationStream& css, size_t& lcp_len) {
  const char* first_path = nullptr;
  char sep = os::file_separator()[0];

  for (css.start(); css.has_next(); ) {
    const char* path = css.get_next();
    if (first_path == nullptr) {
      first_path = path;
      const char* p = strrchr(first_path, sep);
      if (p == nullptr) {
        lcp_len = 0;
        return "";
      } else {
        lcp_len = p - first_path + 1;
      }
    } else {
      lcp_len = find_lcp_of_two_paths(first_path, path, lcp_len);
      if (lcp_len == 0) {
        return "";
      }
    }
  }

  if (first_path != nullptr && lcp_len > 0) {
    char* lcp = NEW_C_HEAP_ARRAY(char, lcp_len + 1, mtClassShared);
    lcp[0] = 0;
    strncat(lcp, first_path, lcp_len);
    return lcp;
  } else {
    lcp_len = 0;
    return "";
  }
}

void AOTClassLocationConfig::parse(JavaThread* current, GrowableClassLocationArray& tmp_array,
                                   ClassLocationStream& css, Group group, bool parse_manifest) {
  for (css.start(); css.has_next(); ) {
    add_class_location(current, tmp_array, css.get_next(), group, parse_manifest, /*from_cpattr*/false);
  }
}

void AOTClassLocationConfig::add_class_location(JavaThread* current, GrowableClassLocationArray& tmp_array,
                                                const char* path, Group group, bool parse_manifest, bool from_cpattr) {
  AOTClassLocation* cs = AOTClassLocation::allocate(current, path, tmp_array.length(), group, from_cpattr);
  log_info(class, path)("path [%d] = %s%s", tmp_array.length(), path, from_cpattr ? " (from cpattr)" : "");
  tmp_array.append(cs);

  if (!parse_manifest) {
    // parse_manifest is true for -classpath and -Xbootclasspath/a, and false for --module-path.
    return;
  }

  ResourceMark rm;
  char* cp_attr = cs->get_cpattr(); // resource allocated
  if (cp_attr != nullptr && strlen(cp_attr) > 0) {
    //trace_class_path("found Class-Path: ", cp_attr); FIXME

    char sep = os::file_separator()[0];
    const char* dir_name = cs->path();
    const char* dir_tail = strrchr(dir_name, sep);
#ifdef _WINDOWS
    // On Windows, we also support forward slash as the file separator when locating entries in the classpath entry.
    const char* dir_tail2 = strrchr(dir_name, '/');
    if (dir_tail == nullptr) {
      dir_tail = dir_tail2;
    } else if (dir_tail2 != nullptr && dir_tail2 > dir_tail) {
      dir_tail = dir_tail2;
    }
#endif
    int dir_len;
    if (dir_tail == nullptr) {
      dir_len = 0;
    } else {
      dir_len = pointer_delta_as_int(dir_tail, dir_name) + 1;
    }

    // Split the cp_attr by spaces, and add each file
    char* file_start = cp_attr;
    char* end = file_start + strlen(file_start);

    while (file_start < end) {
      char* file_end = strchr(file_start, ' ');
      if (file_end != nullptr) {
        *file_end = 0;
        file_end += 1;
      } else {
        file_end = end;
      }

      size_t name_len = strlen(file_start);
      if (name_len > 0) {
        ResourceMark rm(current);
        size_t libname_len = dir_len + name_len;
        char* libname = NEW_RESOURCE_ARRAY(char, libname_len + 1);
        int n = os::snprintf(libname, libname_len + 1, "%.*s%s", dir_len, dir_name, file_start);
        assert((size_t)n == libname_len, "Unexpected number of characters in string");

        // Avoid infinite recursion when two JAR files refer to each
        // other via cpattr.
        bool found_duplicate = false;
        for (int i = boot_cp_start_index(); i < tmp_array.length(); i++) {
          if (strcmp(tmp_array.at(i)->path(), libname) == 0) {
            found_duplicate = true;
            break;
          }
        }
        if (!found_duplicate) {
          add_class_location(current, tmp_array, libname, group, parse_manifest, /*from_cpattr*/true);
        }
      }

      file_start = file_end;
    }
  }
}

AOTClassLocation const* AOTClassLocationConfig::class_location_at(int index) const {
  return _class_locations->at(index);
}

int AOTClassLocationConfig::get_module_shared_path_index(Symbol* location) const {
  if (location->starts_with("jrt:", 4)) {
    assert(class_location_at(0)->is_modules_image(), "sanity");
    return 0;
  }

  if (num_module_paths() == 0) {
    // The archive(s) were created without --module-path option
    return -1;
  }

  if (!location->starts_with("file:", 5)) {
    return -1;
  }

  // skip_uri_protocol was also called during dump time -- see ClassLoaderExt::process_module_table()
  ResourceMark rm;
  const char* file = ClassLoader::uri_to_path(location->as_C_string());
  for (int i = module_path_start_index(); i < module_path_end_index(); i++) {
    const AOTClassLocation* cs = class_location_at(i);
    assert(!cs->has_unnamed_module(), "must be");
    bool same = os::same_files(file, cs->path());
    log_debug(class, path)("get_module_shared_path_index (%d) %s : %s = %s", i,
                           location->as_C_string(), cs->path(), same ? "same" : "different");
    if (same) {
      return i;
    }
  }
  return -1;
}

// We allow non-empty dirs as long as no classes have been loaded from them.
void AOTClassLocationConfig::check_nonempty_dirs() const {
  assert(CDSConfig::is_dumping_archive(), "sanity");

  bool has_nonempty_dir = false;
  dumptime_iterate([&](AOTClassLocation* cs) {
    if (cs->index() > _max_used_index) {
      return false; // stop iterating
    }
    if (cs->is_dir()) {
      if (!os::dir_is_empty(cs->path())) {
        aot_log_error(aot)("Error: non-empty directory '%s'", cs->path());
        has_nonempty_dir = true;
      }
    }
    return true; // keep iterating
  });

  if (has_nonempty_dir) {
    vm_exit_during_cds_dumping("Cannot have non-empty directory in paths", nullptr);
  }
}

// It's possible to use reflection+setAccessible to call into ClassLoader::defineClass() to
// pretend that a dynamically generated class comes from a JAR file in the classpath.
// Detect such classes so that they can be excluded from the archive.
bool AOTClassLocationConfig::is_valid_classpath_index(int classpath_index, InstanceKlass* ik) {
  if (1 <= classpath_index && classpath_index < length()) {
    ClassPathZipEntry *zip = _dumptime_jar_files->at(classpath_index);
    if (zip != nullptr) {
      JavaThread* current = JavaThread::current();
      ResourceMark rm(current);
      const char* const class_name = ik->name()->as_C_string();
      const char* const file_name = ClassLoader::file_name_for_class_name(class_name,
                                                                          ik->name()->utf8_length());
      if (!zip->has_entry(current, file_name)) {
        aot_log_warning(aot)("class %s cannot be archived because it was not defined from %s as claimed",
                         class_name, zip->name());
        return false;
      }
    }
  }

  return true;
}

AOTClassLocationConfig* AOTClassLocationConfig::write_to_archive() const {
  log_locations(CDSConfig::output_archive_path(), /*is_write=*/true);

  Array<AOTClassLocation*>* archived_copy = ArchiveBuilder::new_ro_array<AOTClassLocation*>(_class_locations->length());
  for (int i = 0; i < _class_locations->length(); i++) {
    archived_copy->at_put(i, _class_locations->at(i)->write_to_archive());
    ArchivePtrMarker::mark_pointer((address*)archived_copy->adr_at(i));
  }

  AOTClassLocationConfig* dumped = (AOTClassLocationConfig*)ArchiveBuilder::ro_region_alloc(sizeof(AOTClassLocationConfig));
  memcpy(dumped, this, sizeof(AOTClassLocationConfig));
  dumped->_class_locations = archived_copy;
  ArchivePtrMarker::mark_pointer(&dumped->_class_locations);

  return dumped;
}

bool AOTClassLocationConfig::check_classpaths(bool is_boot_classpath, bool has_aot_linked_classes,
                                              int index_start, int index_end,
                                              ClassLocationStream& runtime_css,
                                              bool use_lcp_match, const char* runtime_lcp,
                                              size_t runtime_lcp_len) const {
  if (index_start >= index_end && runtime_css.is_empty()) { // nothing to check
    return true;
  }

  ResourceMark rm;
  const char* which = is_boot_classpath ? "boot" : "app";
  LogTarget(Info, class, path) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print("Checking %s classpath", which);
    ls.print_cr("%s", use_lcp_match ? " (with longest common prefix substitution)" : "");
    ls.print("- expected : '");
    print_dumptime_classpath(ls, index_start, index_end, use_lcp_match, _dumptime_lcp_len, runtime_lcp, runtime_lcp_len);
    ls.print_cr("'");
    ls.print("- actual   : '");
    runtime_css.print(&ls);
    ls.print_cr("'");
  }

  runtime_css.start();
  for (int i = index_start; i < index_end; i++) {
    ResourceMark rm;
    const AOTClassLocation* cs = class_location_at(i);
    const char* effective_dumptime_path = cs->path();
    if (use_lcp_match && _dumptime_lcp_len > 0) {
      effective_dumptime_path = substitute(effective_dumptime_path, _dumptime_lcp_len, runtime_lcp, runtime_lcp_len);
    }

    log_info(class, path)("Checking [%d] '%s' %s%s", i, effective_dumptime_path, cs->file_type_string(),
                          cs->from_cpattr() ? " (from JAR manifest ClassPath attribute)" : "");
    if (!cs->from_cpattr() && file_exists(effective_dumptime_path)) {
      if (!runtime_css.has_next()) {
        aot_log_warning(aot)("%s classpath has fewer elements than expected", which);
        return false;
      }
      const char* runtime_path = runtime_css.get_next();
      while (!file_exists(runtime_path) && runtime_css.has_next()) {
        runtime_path = runtime_css.get_next();
      }
      if (!os::same_files(effective_dumptime_path, runtime_path)) {
        aot_log_warning(aot)("The name of %s classpath [%d] does not match: expected '%s', got '%s'",
                         which, runtime_css.current(), effective_dumptime_path, runtime_path);
        return false;
      }
    }

    if (!cs->check(effective_dumptime_path, has_aot_linked_classes)) {
      return false;
    }
  }

  // Check if the runtime boot classpath has more entries than the one stored in the archive and if the app classpath
  // or the module path requires validation.
  if (is_boot_classpath && runtime_css.has_next() && (need_to_check_app_classpath() || num_module_paths() > 0)) {
    // the check passes if all the extra runtime boot classpath entries are non-existent
    if (check_paths_existence(runtime_css)) {
      aot_log_warning(aot)("boot classpath is longer than expected");
      return false;
    }
  }

  return true;
}

bool AOTClassLocationConfig::file_exists(const char* filename) const{
  struct stat st;
  return (os::stat(filename, &st) == 0 && st.st_size > 0);
}

bool AOTClassLocationConfig::check_paths_existence(ClassLocationStream& runtime_css) const {
  bool exist = false;
  while (runtime_css.has_next()) {
    const char* path = runtime_css.get_next();
    if (file_exists(path)) {
      exist = true;
      break;
    }
  }
  return exist;
}

bool AOTClassLocationConfig::check_module_paths(bool has_aot_linked_classes, int index_start, int index_end,
                                                ClassLocationStream& runtime_css,
                                                bool* has_extra_module_paths) const {
  if (index_start >= index_end && runtime_css.is_empty()) { // nothing to check
    return true;
  }

  ResourceMark rm;

  LogTarget(Info, class, path) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("Checking module paths");
    ls.print("- expected : '");
    print_dumptime_classpath(ls, index_start, index_end, false, 0, nullptr, 0);
    ls.print_cr("'");
    ls.print("- actual   : '");
    runtime_css.print(&ls);
    ls.print_cr("'");
  }

  // Make sure all the dumptime module paths exist and are unchanged
  for (int i = index_start; i < index_end; i++) {
    const AOTClassLocation* cs = class_location_at(i);
    const char* dumptime_path = cs->path();

    assert(!cs->from_cpattr(), "not applicable for module path");
    log_info(class, path)("Checking '%s' %s", dumptime_path, cs->file_type_string());

    if (!cs->check(dumptime_path, has_aot_linked_classes)) {
      return false;
    }
  }

  // We allow runtime_css to be a superset of the module paths specified in dumptime. E.g.,
  // Dumptime:    A:C
  // Runtime:     A:B:C
  runtime_css.start();
  for (int i = index_start; i < index_end; i++) {
    const AOTClassLocation* cs = class_location_at(i);
    const char* dumptime_path = cs->path();

    while (true) {
      if (!runtime_css.has_next()) {
        aot_log_warning(aot)("module path has fewer elements than expected");
        *has_extra_module_paths = true;
        return true;
      }
      // Both this->class_locations() and runtime_css are alphabetically sorted. Skip
      // items in runtime_css until we see dumptime_path.
      const char* runtime_path = runtime_css.get_next();
      if (!os::same_files(dumptime_path, runtime_path)) {
        *has_extra_module_paths = true;
        return true;
      } else {
        break;
      }
    }
  }

  if (runtime_css.has_next()) {
    *has_extra_module_paths = true;
  }

  return true;
}

void AOTClassLocationConfig::print_dumptime_classpath(LogStream& ls, int index_start, int index_end,
                                                      bool do_substitute, size_t remove_prefix_len,
                                                      const char* prepend, size_t prepend_len) const {
  const char* sep = "";
  for (int i = index_start; i < index_end; i++) {
    ResourceMark rm;
    const AOTClassLocation* cs = class_location_at(i);
    const char* path = cs->path();
    if (!cs->from_cpattr()) {
      ls.print("%s", sep);
      if (do_substitute) {
        path = substitute(path, remove_prefix_len, prepend, prepend_len);
      }
      ls.print("%s", path);
      sep = os::path_separator();
    }
  }
}

// Returned path is resource-allocated
const char* AOTClassLocationConfig::substitute(const char* path,         // start with this path (which was recorded from dump time)
                                               size_t remove_prefix_len, // remove this number of chars from the beginning
                                               const char* prepend,      // prepend this string
                                               size_t prepend_len) {     // length of the prepended string
  size_t len = strlen(path);
  assert(len > remove_prefix_len, "sanity");
  assert(prepend_len == strlen(prepend), "sanity");
  len -= remove_prefix_len;
  len += prepend_len;

  char* buf = NEW_RESOURCE_ARRAY(char, len + 1);
  int n = os::snprintf(buf, len + 1, "%s%s", prepend, path + remove_prefix_len);
  assert(size_t(n) == len, "sanity");

  return buf;
}

// For performance, we avoid using LCP match if there's at least one
// AOTClassLocation can be matched exactly: this means all other AOTClassLocations must be
// matched exactly.
bool AOTClassLocationConfig::need_lcp_match(AllClassLocationStreams& all_css) const {
  if (app_cp_end_index() == boot_cp_start_index()) {
    // No need to use lcp-match when there are no boot/app paths.
    // TODO: LCP-match not yet supported for modules.
    return false;
  }

  if (need_lcp_match_helper(boot_cp_start_index(), boot_cp_end_index(), all_css.boot_cp()) &&
      need_lcp_match_helper(app_cp_start_index(), app_cp_end_index(), all_css.app_cp())) {
    return true;
  } else {
    return false;
  }
}

bool AOTClassLocationConfig::need_lcp_match_helper(int start, int end, ClassLocationStream& css) const {
  int i = start;
  for (css.start(); i < end && css.has_next(); ) {
    const AOTClassLocation* cs = class_location_at(i++);
    const char* runtime_path = css.get_next();
    if (cs->must_exist() && os::same_files(cs->path(), runtime_path)) {
      // Most likely, we will come to here at the first iteration.
      return false;
    }
  }
  return true;
}

bool AOTClassLocationConfig::validate(const char* cache_filename, bool has_aot_linked_classes, bool* has_extra_module_paths) const {
  ResourceMark rm;
  AllClassLocationStreams all_css;

  log_locations(cache_filename, /*is_write=*/false);

  const char* jrt = ClassLoader::get_jrt_entry()->name();
  log_info(class, path)("Checking [0] (modules image)");
  bool success = class_location_at(0)->check(jrt, has_aot_linked_classes);
  log_info(class, path)("Modules image %s validation: %s", jrt, success ? "passed" : "failed");
  if (!success) {
    return false;
  }
  if (class_locations()->length() == 1) {
    if ((module_path_start_index() >= module_path_end_index()) && Arguments::get_property("jdk.module.path") != nullptr) {
      *has_extra_module_paths = true;
    } else {
      *has_extra_module_paths = false;
    }
  } else {
    bool use_lcp_match = need_lcp_match(all_css);
    const char* runtime_lcp;
    size_t runtime_lcp_len;

    log_info(class, path)("Longest common prefix substitution in boot/app classpath matching: %s",
                          use_lcp_match ? "yes" : "no");
    if (use_lcp_match) {
      runtime_lcp = find_lcp(all_css.boot_and_app_cp(), runtime_lcp_len);
      log_info(class, path)("Longest common prefix: %s (%zu chars)", runtime_lcp, runtime_lcp_len);
    } else {
      runtime_lcp = nullptr;
      runtime_lcp_len = 0;
    }

    success = check_classpaths(true, has_aot_linked_classes, boot_cp_start_index(), boot_cp_end_index(), all_css.boot_cp(),
                               use_lcp_match, runtime_lcp, runtime_lcp_len);
    log_info(class, path)("Archived boot classpath validation: %s", success ? "passed" : "failed");

    if (success && need_to_check_app_classpath()) {
      success = check_classpaths(false, has_aot_linked_classes, app_cp_start_index(), app_cp_end_index(), all_css.app_cp(),
                                 use_lcp_match, runtime_lcp, runtime_lcp_len);
      log_info(class, path)("Archived app classpath validation: %s", success ? "passed" : "failed");
    }

    if (success) {
      success = check_module_paths(has_aot_linked_classes, module_path_start_index(), module_path_end_index(),
                                   all_css.module_path(), has_extra_module_paths);
      log_info(class, path)("Archived module path validation: %s%s", success ? "passed" : "failed",
                            (*has_extra_module_paths) ? " (extra module paths found)" : "");
    }

    if (runtime_lcp_len > 0) {
      os::free((void*)runtime_lcp);
    }
  }

  if (success) {
    _runtime_instance = this;
  } else {
    const char* mismatch_msg = "shared class paths mismatch";
    const char* hint_msg = log_is_enabled(Info, class, path) ?
        "" : " (hint: enable -Xlog:class+path=info to diagnose the failure)";
    if (RequireSharedSpaces && !PrintSharedArchiveAndExit) {
      if (CDSConfig::is_dumping_final_static_archive()) {
        aot_log_error(aot)("class path and/or module path are not compatible with the "
                       "ones specified when the AOTConfiguration file was recorded%s", hint_msg);
        vm_exit_during_initialization("Unable to use create AOT cache.", nullptr);
      } else {
        aot_log_error(aot)("%s%s", mismatch_msg, hint_msg);
        AOTMetaspace::unrecoverable_loading_error();
      }
    } else {
      AOTMetaspace::report_loading_error("%s%s", mismatch_msg, hint_msg);
    }
  }
  return success;
}

void AOTClassLocationConfig::log_locations(const char* cache_filename, bool is_write) const {
  if (log_is_enabled(Info, class, path)) {
    LogStreamHandle(Info, class, path) st;
    st.print_cr("%s classpath(s) %s %s (size = %d)",
                is_write ? "Writing" : "Reading",
                is_write ? "into" : "from",
                cache_filename, class_locations()->length());
    print_on(&st);
  }
}

void AOTClassLocationConfig::print() {
  if (CDSConfig::is_dumping_archive()) {
    tty->print_cr("AOTClassLocationConfig::_dumptime_instance = %p", _dumptime_instance);
    if (_dumptime_instance != nullptr) {
      _dumptime_instance->print_on(tty);
    }
  }
  if (CDSConfig::is_using_archive()) {
    tty->print_cr("AOTClassLocationConfig::_runtime_instance = %p", _runtime_instance);
    if (_runtime_instance != nullptr) {
      _runtime_instance->print_on(tty);
    }
  }
}

void AOTClassLocationConfig::print_on(outputStream* st) const {
  const char* type = "boot";
  int n = class_locations()->length();
  for (int i = 0; i < n; i++) {
    if (i >= boot_cp_end_index()) {
      type = "app";
    }
    if (i >= app_cp_end_index()) {
      type = "module";
    }
    const AOTClassLocation* cs = class_location_at(i);
    const char* path;
    if (i == 0) {
      path = ClassLoader::get_jrt_entry()->name();
    } else {
      path = cs->path();
    }
    st->print_cr("(%-6s) [%d] = %s", type, i, path);
  }
}
