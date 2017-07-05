/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_classLoader.cpp.incl"


// Entry points in zip.dll for loading zip/jar file entries

typedef void * * (JNICALL *ZipOpen_t)(const char *name, char **pmsg);
typedef void (JNICALL *ZipClose_t)(jzfile *zip);
typedef jzentry* (JNICALL *FindEntry_t)(jzfile *zip, const char *name, jint *sizeP, jint *nameLen);
typedef jboolean (JNICALL *ReadEntry_t)(jzfile *zip, jzentry *entry, unsigned char *buf, char *namebuf);
typedef jboolean (JNICALL *ReadMappedEntry_t)(jzfile *zip, jzentry *entry, unsigned char **buf, char *namebuf);
typedef jzentry* (JNICALL *GetNextEntry_t)(jzfile *zip, jint n);

static ZipOpen_t         ZipOpen            = NULL;
static ZipClose_t        ZipClose           = NULL;
static FindEntry_t       FindEntry          = NULL;
static ReadEntry_t       ReadEntry          = NULL;
static ReadMappedEntry_t ReadMappedEntry    = NULL;
static GetNextEntry_t    GetNextEntry       = NULL;
static canonicalize_fn_t CanonicalizeEntry  = NULL;

// Globals

PerfCounter*    ClassLoader::_perf_accumulated_time = NULL;
PerfCounter*    ClassLoader::_perf_classes_inited = NULL;
PerfCounter*    ClassLoader::_perf_class_init_time = NULL;
PerfCounter*    ClassLoader::_perf_class_init_selftime = NULL;
PerfCounter*    ClassLoader::_perf_classes_verified = NULL;
PerfCounter*    ClassLoader::_perf_class_verify_time = NULL;
PerfCounter*    ClassLoader::_perf_class_verify_selftime = NULL;
PerfCounter*    ClassLoader::_perf_classes_linked = NULL;
PerfCounter*    ClassLoader::_perf_class_link_time = NULL;
PerfCounter*    ClassLoader::_perf_class_link_selftime = NULL;
PerfCounter*    ClassLoader::_perf_class_parse_time = NULL;
PerfCounter*    ClassLoader::_perf_class_parse_selftime = NULL;
PerfCounter*    ClassLoader::_perf_sys_class_lookup_time = NULL;
PerfCounter*    ClassLoader::_perf_shared_classload_time = NULL;
PerfCounter*    ClassLoader::_perf_sys_classload_time = NULL;
PerfCounter*    ClassLoader::_perf_app_classload_time = NULL;
PerfCounter*    ClassLoader::_perf_app_classload_selftime = NULL;
PerfCounter*    ClassLoader::_perf_app_classload_count = NULL;
PerfCounter*    ClassLoader::_perf_define_appclasses = NULL;
PerfCounter*    ClassLoader::_perf_define_appclass_time = NULL;
PerfCounter*    ClassLoader::_perf_define_appclass_selftime = NULL;
PerfCounter*    ClassLoader::_perf_app_classfile_bytes_read = NULL;
PerfCounter*    ClassLoader::_perf_sys_classfile_bytes_read = NULL;
PerfCounter*    ClassLoader::_sync_systemLoaderLockContentionRate = NULL;
PerfCounter*    ClassLoader::_sync_nonSystemLoaderLockContentionRate = NULL;
PerfCounter*    ClassLoader::_sync_JVMFindLoadedClassLockFreeCounter = NULL;
PerfCounter*    ClassLoader::_sync_JVMDefineClassLockFreeCounter = NULL;
PerfCounter*    ClassLoader::_sync_JNIDefineClassLockFreeCounter = NULL;
PerfCounter*    ClassLoader::_unsafe_defineClassCallCounter = NULL;
PerfCounter*    ClassLoader::_isUnsyncloadClass = NULL;
PerfCounter*    ClassLoader::_load_instance_class_failCounter = NULL;

ClassPathEntry* ClassLoader::_first_entry         = NULL;
ClassPathEntry* ClassLoader::_last_entry          = NULL;
PackageHashtable* ClassLoader::_package_hash_table = NULL;

// helper routines
bool string_starts_with(const char* str, const char* str_to_find) {
  size_t str_len = strlen(str);
  size_t str_to_find_len = strlen(str_to_find);
  if (str_to_find_len > str_len) {
    return false;
  }
  return (strncmp(str, str_to_find, str_to_find_len) == 0);
}

bool string_ends_with(const char* str, const char* str_to_find) {
  size_t str_len = strlen(str);
  size_t str_to_find_len = strlen(str_to_find);
  if (str_to_find_len > str_len) {
    return false;
  }
  return (strncmp(str + (str_len - str_to_find_len), str_to_find, str_to_find_len) == 0);
}


MetaIndex::MetaIndex(char** meta_package_names, int num_meta_package_names) {
  if (num_meta_package_names == 0) {
    _meta_package_names = NULL;
    _num_meta_package_names = 0;
  } else {
    _meta_package_names = NEW_C_HEAP_ARRAY(char*, num_meta_package_names);
    _num_meta_package_names = num_meta_package_names;
    memcpy(_meta_package_names, meta_package_names, num_meta_package_names * sizeof(char*));
  }
}


MetaIndex::~MetaIndex() {
  FREE_C_HEAP_ARRAY(char*, _meta_package_names);
}


bool MetaIndex::may_contain(const char* class_name) {
  if ( _num_meta_package_names == 0) {
    return false;
  }
  size_t class_name_len = strlen(class_name);
  for (int i = 0; i < _num_meta_package_names; i++) {
    char* pkg = _meta_package_names[i];
    size_t pkg_len = strlen(pkg);
    size_t min_len = MIN2(class_name_len, pkg_len);
    if (!strncmp(class_name, pkg, min_len)) {
      return true;
    }
  }
  return false;
}


ClassPathEntry::ClassPathEntry() {
  set_next(NULL);
}


bool ClassPathEntry::is_lazy() {
  return false;
}

ClassPathDirEntry::ClassPathDirEntry(char* dir) : ClassPathEntry() {
  _dir = NEW_C_HEAP_ARRAY(char, strlen(dir)+1);
  strcpy(_dir, dir);
}


ClassFileStream* ClassPathDirEntry::open_stream(const char* name) {
  // construct full path name
  char path[JVM_MAXPATHLEN];
  if (jio_snprintf(path, sizeof(path), "%s%s%s", _dir, os::file_separator(), name) == -1) {
    return NULL;
  }
  // check if file exists
  struct stat st;
  if (os::stat(path, &st) == 0) {
    // found file, open it
    int file_handle = hpi::open(path, 0, 0);
    if (file_handle != -1) {
      // read contents into resource array
      u1* buffer = NEW_RESOURCE_ARRAY(u1, st.st_size);
      size_t num_read = os::read(file_handle, (char*) buffer, st.st_size);
      // close file
      hpi::close(file_handle);
      // construct ClassFileStream
      if (num_read == (size_t)st.st_size) {
        if (UsePerfData) {
          ClassLoader::perf_sys_classfile_bytes_read()->inc(num_read);
        }
        return new ClassFileStream(buffer, st.st_size, _dir);    // Resource allocated
      }
    }
  }
  return NULL;
}


ClassPathZipEntry::ClassPathZipEntry(jzfile* zip, const char* zip_name) : ClassPathEntry() {
  _zip = zip;
  _zip_name = NEW_C_HEAP_ARRAY(char, strlen(zip_name)+1);
  strcpy(_zip_name, zip_name);
}

ClassPathZipEntry::~ClassPathZipEntry() {
  if (ZipClose != NULL) {
    (*ZipClose)(_zip);
  }
  FREE_C_HEAP_ARRAY(char, _zip_name);
}

ClassFileStream* ClassPathZipEntry::open_stream(const char* name) {
  // enable call to C land
  JavaThread* thread = JavaThread::current();
  ThreadToNativeFromVM ttn(thread);
  // check whether zip archive contains name
  jint filesize, name_len;
  jzentry* entry = (*FindEntry)(_zip, name, &filesize, &name_len);
  if (entry == NULL) return NULL;
  u1* buffer;
  char name_buf[128];
  char* filename;
  if (name_len < 128) {
    filename = name_buf;
  } else {
    filename = NEW_RESOURCE_ARRAY(char, name_len + 1);
  }

  // file found, get pointer to class in mmaped jar file.
  if (ReadMappedEntry == NULL ||
      !(*ReadMappedEntry)(_zip, entry, &buffer, filename)) {
      // mmaped access not available, perhaps due to compression,
      // read contents into resource array
      buffer     = NEW_RESOURCE_ARRAY(u1, filesize);
      if (!(*ReadEntry)(_zip, entry, buffer, filename)) return NULL;
  }
  if (UsePerfData) {
    ClassLoader::perf_sys_classfile_bytes_read()->inc(filesize);
  }
  // return result
  return new ClassFileStream(buffer, filesize, _zip_name);    // Resource allocated
}

// invoke function for each entry in the zip file
void ClassPathZipEntry::contents_do(void f(const char* name, void* context), void* context) {
  JavaThread* thread = JavaThread::current();
  HandleMark  handle_mark(thread);
  ThreadToNativeFromVM ttn(thread);
  for (int n = 0; ; n++) {
    jzentry * ze = ((*GetNextEntry)(_zip, n));
    if (ze == NULL) break;
    (*f)(ze->name, context);
  }
}

LazyClassPathEntry::LazyClassPathEntry(char* path, struct stat st) : ClassPathEntry() {
  _path = strdup(path);
  _st = st;
  _meta_index = NULL;
  _resolved_entry = NULL;
}

bool LazyClassPathEntry::is_jar_file() {
  return ((_st.st_mode & S_IFREG) == S_IFREG);
}

ClassPathEntry* LazyClassPathEntry::resolve_entry() {
  if (_resolved_entry != NULL) {
    return (ClassPathEntry*) _resolved_entry;
  }
  ClassPathEntry* new_entry = NULL;
  ClassLoader::create_class_path_entry(_path, _st, &new_entry, false);
  assert(new_entry != NULL, "earlier code should have caught this");
  {
    ThreadCritical tc;
    if (_resolved_entry == NULL) {
      _resolved_entry = new_entry;
      return new_entry;
    }
  }
  assert(_resolved_entry != NULL, "bug in MT-safe resolution logic");
  delete new_entry;
  return (ClassPathEntry*) _resolved_entry;
}

ClassFileStream* LazyClassPathEntry::open_stream(const char* name) {
  if (_meta_index != NULL &&
      !_meta_index->may_contain(name)) {
    return NULL;
  }
  return resolve_entry()->open_stream(name);
}

bool LazyClassPathEntry::is_lazy() {
  return true;
}

static void print_meta_index(LazyClassPathEntry* entry,
                             GrowableArray<char*>& meta_packages) {
  tty->print("[Meta index for %s=", entry->name());
  for (int i = 0; i < meta_packages.length(); i++) {
    if (i > 0) tty->print(" ");
    tty->print(meta_packages.at(i));
  }
  tty->print_cr("]");
}


void ClassLoader::setup_meta_index() {
  // Set up meta index which allows us to open boot jars lazily if
  // class data sharing is enabled
  const char* known_version = "% VERSION 2";
  char* meta_index_path = Arguments::get_meta_index_path();
  char* meta_index_dir  = Arguments::get_meta_index_dir();
  FILE* file = fopen(meta_index_path, "r");
  int line_no = 0;
  if (file != NULL) {
    ResourceMark rm;
    LazyClassPathEntry* cur_entry = NULL;
    GrowableArray<char*> boot_class_path_packages(10);
    char package_name[256];
    bool skipCurrentJar = false;
    while (fgets(package_name, sizeof(package_name), file) != NULL) {
      ++line_no;
      // Remove trailing newline
      package_name[strlen(package_name) - 1] = '\0';
      switch(package_name[0]) {
        case '%':
        {
          if ((line_no == 1) && (strcmp(package_name, known_version) != 0)) {
            if (TraceClassLoading && Verbose) {
              tty->print("[Unsupported meta index version]");
            }
            fclose(file);
            return;
          }
        }

        // These directives indicate jar files which contain only
        // classes, only non-classfile resources, or a combination of
        // the two. See src/share/classes/sun/misc/MetaIndex.java and
        // make/tools/MetaIndex/BuildMetaIndex.java in the J2SE
        // workspace.
        case '#':
        case '!':
        case '@':
        {
          // Hand off current packages to current lazy entry (if any)
          if ((cur_entry != NULL) &&
              (boot_class_path_packages.length() > 0)) {
            if (TraceClassLoading && Verbose) {
              print_meta_index(cur_entry, boot_class_path_packages);
            }
            MetaIndex* index = new MetaIndex(boot_class_path_packages.adr_at(0),
                                             boot_class_path_packages.length());
            cur_entry->set_meta_index(index);
          }
          cur_entry = NULL;
          boot_class_path_packages.clear();

          // Find lazy entry corresponding to this jar file
          for (ClassPathEntry* entry = _first_entry; entry != NULL; entry = entry->next()) {
            if (entry->is_lazy() &&
                string_starts_with(entry->name(), meta_index_dir) &&
                string_ends_with(entry->name(), &package_name[2])) {
              cur_entry = (LazyClassPathEntry*) entry;
              break;
            }
          }

          // If the first character is '@', it indicates the following jar
          // file is a resource only jar file in which case, we should skip
          // reading the subsequent entries since the resource loading is
          // totally handled by J2SE side.
          if (package_name[0] == '@') {
            if (cur_entry != NULL) {
              cur_entry->set_meta_index(new MetaIndex(NULL, 0));
            }
            cur_entry = NULL;
            skipCurrentJar = true;
          } else {
            skipCurrentJar = false;
          }

          break;
        }

        default:
        {
          if (!skipCurrentJar && cur_entry != NULL) {
            char* new_name = strdup(package_name);
            boot_class_path_packages.append(new_name);
          }
        }
      }
    }
    // Hand off current packages to current lazy entry (if any)
    if ((cur_entry != NULL) &&
        (boot_class_path_packages.length() > 0)) {
      if (TraceClassLoading && Verbose) {
        print_meta_index(cur_entry, boot_class_path_packages);
      }
      MetaIndex* index = new MetaIndex(boot_class_path_packages.adr_at(0),
                                       boot_class_path_packages.length());
      cur_entry->set_meta_index(index);
    }
    fclose(file);
  }
}

void ClassLoader::setup_bootstrap_search_path() {
  assert(_first_entry == NULL, "should not setup bootstrap class search path twice");
  char* sys_class_path = os::strdup(Arguments::get_sysclasspath());
  if (TraceClassLoading && Verbose) {
    tty->print_cr("[Bootstrap loader class path=%s]", sys_class_path);
  }

  int len = (int)strlen(sys_class_path);
  int end = 0;

  // Iterate over class path entries
  for (int start = 0; start < len; start = end) {
    while (sys_class_path[end] && sys_class_path[end] != os::path_separator()[0]) {
      end++;
    }
    char* path = NEW_C_HEAP_ARRAY(char, end-start+1);
    strncpy(path, &sys_class_path[start], end-start);
    path[end-start] = '\0';
    update_class_path_entry_list(path, false);
    FREE_C_HEAP_ARRAY(char, path);
    while (sys_class_path[end] == os::path_separator()[0]) {
      end++;
    }
  }
}

void ClassLoader::create_class_path_entry(char *path, struct stat st, ClassPathEntry **new_entry, bool lazy) {
  JavaThread* thread = JavaThread::current();
  if (lazy) {
    *new_entry = new LazyClassPathEntry(path, st);
    return;
  }
  if ((st.st_mode & S_IFREG) == S_IFREG) {
    // Regular file, should be a zip file
    // Canonicalized filename
    char canonical_path[JVM_MAXPATHLEN];
    if (!get_canonical_path(path, canonical_path, JVM_MAXPATHLEN)) {
      // This matches the classic VM
      EXCEPTION_MARK;
      THROW_MSG(vmSymbols::java_io_IOException(), "Bad pathname");
    }
    char* error_msg = NULL;
    jzfile* zip;
    {
      // enable call to C land
      ThreadToNativeFromVM ttn(thread);
      HandleMark hm(thread);
      zip = (*ZipOpen)(canonical_path, &error_msg);
    }
    if (zip != NULL && error_msg == NULL) {
      *new_entry = new ClassPathZipEntry(zip, path);
      if (TraceClassLoading) {
        tty->print_cr("[Opened %s]", path);
      }
    } else {
      ResourceMark rm(thread);
      char *msg;
      if (error_msg == NULL) {
        msg = NEW_RESOURCE_ARRAY(char, strlen(path) + 128); ;
        jio_snprintf(msg, strlen(path) + 127, "error in opening JAR file %s", path);
      } else {
        int len = (int)(strlen(path) + strlen(error_msg) + 128);
        msg = NEW_RESOURCE_ARRAY(char, len); ;
        jio_snprintf(msg, len - 1, "error in opening JAR file <%s> %s", error_msg, path);
      }
      EXCEPTION_MARK;
      THROW_MSG(vmSymbols::java_lang_ClassNotFoundException(), msg);
    }
  } else {
    // Directory
    *new_entry = new ClassPathDirEntry(path);
    if (TraceClassLoading) {
      tty->print_cr("[Path %s]", path);
    }
  }
}


// Create a class path zip entry for a given path (return NULL if not found
// or zip/JAR file cannot be opened)
ClassPathZipEntry* ClassLoader::create_class_path_zip_entry(const char *path) {
  // check for a regular file
  struct stat st;
  if (os::stat(path, &st) == 0) {
    if ((st.st_mode & S_IFREG) == S_IFREG) {
      char orig_path[JVM_MAXPATHLEN];
      char canonical_path[JVM_MAXPATHLEN];

      strcpy(orig_path, path);
      if (get_canonical_path(orig_path, canonical_path, JVM_MAXPATHLEN)) {
        char* error_msg = NULL;
        jzfile* zip;
        {
          // enable call to C land
          JavaThread* thread = JavaThread::current();
          ThreadToNativeFromVM ttn(thread);
          HandleMark hm(thread);
          zip = (*ZipOpen)(canonical_path, &error_msg);
        }
        if (zip != NULL && error_msg == NULL) {
          // create using canonical path
          return new ClassPathZipEntry(zip, canonical_path);
        }
      }
    }
  }
  return NULL;
}

// returns true if entry already on class path
bool ClassLoader::contains_entry(ClassPathEntry *entry) {
  ClassPathEntry* e = _first_entry;
  while (e != NULL) {
    // assume zip entries have been canonicalized
    if (strcmp(entry->name(), e->name()) == 0) {
      return true;
    }
    e = e->next();
  }
  return false;
}

void ClassLoader::add_to_list(ClassPathEntry *new_entry) {
  if (new_entry != NULL) {
    if (_last_entry == NULL) {
      _first_entry = _last_entry = new_entry;
    } else {
      _last_entry->set_next(new_entry);
      _last_entry = new_entry;
    }
  }
}

void ClassLoader::update_class_path_entry_list(const char *path,
                                               bool check_for_duplicates) {
  struct stat st;
  if (os::stat((char *)path, &st) == 0) {
    // File or directory found
    ClassPathEntry* new_entry = NULL;
    create_class_path_entry((char *)path, st, &new_entry, LazyBootClassLoader);
    // The kernel VM adds dynamically to the end of the classloader path and
    // doesn't reorder the bootclasspath which would break java.lang.Package
    // (see PackageInfo).
    // Add new entry to linked list
    if (!check_for_duplicates || !contains_entry(new_entry)) {
      add_to_list(new_entry);
    }
  }
}

void ClassLoader::print_bootclasspath() {
  ClassPathEntry* e = _first_entry;
  tty->print("[bootclasspath= ");
  while (e != NULL) {
    tty->print("%s ;", e->name());
    e = e->next();
  }
  tty->print_cr("]");
}

void ClassLoader::load_zip_library() {
  assert(ZipOpen == NULL, "should not load zip library twice");
  // First make sure native library is loaded
  os::native_java_library();
  // Load zip library
  char path[JVM_MAXPATHLEN];
  char ebuf[1024];
  hpi::dll_build_name(path, sizeof(path), Arguments::get_dll_dir(), "zip");
  void* handle = hpi::dll_load(path, ebuf, sizeof ebuf);
  if (handle == NULL) {
    vm_exit_during_initialization("Unable to load ZIP library", path);
  }
  // Lookup zip entry points
  ZipOpen      = CAST_TO_FN_PTR(ZipOpen_t, hpi::dll_lookup(handle, "ZIP_Open"));
  ZipClose     = CAST_TO_FN_PTR(ZipClose_t, hpi::dll_lookup(handle, "ZIP_Close"));
  FindEntry    = CAST_TO_FN_PTR(FindEntry_t, hpi::dll_lookup(handle, "ZIP_FindEntry"));
  ReadEntry    = CAST_TO_FN_PTR(ReadEntry_t, hpi::dll_lookup(handle, "ZIP_ReadEntry"));
  ReadMappedEntry = CAST_TO_FN_PTR(ReadMappedEntry_t, hpi::dll_lookup(handle, "ZIP_ReadMappedEntry"));
  GetNextEntry = CAST_TO_FN_PTR(GetNextEntry_t, hpi::dll_lookup(handle, "ZIP_GetNextEntry"));

  // ZIP_Close is not exported on Windows in JDK5.0 so don't abort if ZIP_Close is NULL
  if (ZipOpen == NULL || FindEntry == NULL || ReadEntry == NULL || GetNextEntry == NULL) {
    vm_exit_during_initialization("Corrupted ZIP library", path);
  }

  // Lookup canonicalize entry in libjava.dll
  void *javalib_handle = os::native_java_library();
  CanonicalizeEntry = CAST_TO_FN_PTR(canonicalize_fn_t, hpi::dll_lookup(javalib_handle, "Canonicalize"));
  // This lookup only works on 1.3. Do not check for non-null here
}

// PackageInfo data exists in order to support the java.lang.Package
// class.  A Package object provides information about a java package
// (version, vendor, etc.) which originates in the manifest of the jar
// file supplying the package.  For application classes, the ClassLoader
// object takes care of this.

// For system (boot) classes, the Java code in the Package class needs
// to be able to identify which source jar file contained the boot
// class, so that it can extract the manifest from it.  This table
// identifies java packages with jar files in the boot classpath.

// Because the boot classpath cannot change, the classpath index is
// sufficient to identify the source jar file or directory.  (Since
// directories have no manifests, the directory name is not required,
// but is available.)

// When using sharing -- the pathnames of entries in the boot classpath
// may not be the same at runtime as they were when the archive was
// created (NFS, Samba, etc.).  The actual files and directories named
// in the classpath must be the same files, in the same order, even
// though the exact name is not the same.

class PackageInfo: public BasicHashtableEntry {
public:
  const char* _pkgname;       // Package name
  int _classpath_index;       // Index of directory or JAR file loaded from

  PackageInfo* next() {
    return (PackageInfo*)BasicHashtableEntry::next();
  }

  const char* pkgname()           { return _pkgname; }
  void set_pkgname(char* pkgname) { _pkgname = pkgname; }

  const char* filename() {
    return ClassLoader::classpath_entry(_classpath_index)->name();
  }

  void set_index(int index) {
    _classpath_index = index;
  }
};


class PackageHashtable : public BasicHashtable {
private:
  inline unsigned int compute_hash(const char *s, int n) {
    unsigned int val = 0;
    while (--n >= 0) {
      val = *s++ + 31 * val;
    }
    return val;
  }

  PackageInfo* bucket(int index) {
    return (PackageInfo*)BasicHashtable::bucket(index);
  }

  PackageInfo* get_entry(int index, unsigned int hash,
                         const char* pkgname, size_t n) {
    for (PackageInfo* pp = bucket(index); pp != NULL; pp = pp->next()) {
      if (pp->hash() == hash &&
          strncmp(pkgname, pp->pkgname(), n) == 0 &&
          pp->pkgname()[n] == '\0') {
        return pp;
      }
    }
    return NULL;
  }

public:
  PackageHashtable(int table_size)
    : BasicHashtable(table_size, sizeof(PackageInfo)) {}

  PackageHashtable(int table_size, HashtableBucket* t, int number_of_entries)
    : BasicHashtable(table_size, sizeof(PackageInfo), t, number_of_entries) {}

  PackageInfo* get_entry(const char* pkgname, int n) {
    unsigned int hash = compute_hash(pkgname, n);
    return get_entry(hash_to_index(hash), hash, pkgname, n);
  }

  PackageInfo* new_entry(char* pkgname, int n) {
    unsigned int hash = compute_hash(pkgname, n);
    PackageInfo* pp;
    pp = (PackageInfo*)BasicHashtable::new_entry(hash);
    pp->set_pkgname(pkgname);
    return pp;
  }

  void add_entry(PackageInfo* pp) {
    int index = hash_to_index(pp->hash());
    BasicHashtable::add_entry(index, pp);
  }

  void copy_pkgnames(const char** packages) {
    int n = 0;
    for (int i = 0; i < table_size(); ++i) {
      for (PackageInfo* pp = bucket(i); pp != NULL; pp = pp->next()) {
        packages[n++] = pp->pkgname();
      }
    }
    assert(n == number_of_entries(), "just checking");
  }

  void copy_table(char** top, char* end, PackageHashtable* table);
};


void PackageHashtable::copy_table(char** top, char* end,
                                  PackageHashtable* table) {
  // Copy (relocate) the table to the shared space.
  BasicHashtable::copy_table(top, end);

  // Calculate the space needed for the package name strings.
  int i;
  int n = 0;
  for (i = 0; i < table_size(); ++i) {
    for (PackageInfo* pp = table->bucket(i);
                      pp != NULL;
                      pp = pp->next()) {
      n += (int)(strlen(pp->pkgname()) + 1);
    }
  }
  if (*top + n + sizeof(intptr_t) >= end) {
    warning("\nThe shared miscellaneous data space is not large "
            "enough to \npreload requested classes.  Use "
            "-XX:SharedMiscDataSize= to increase \nthe initial "
            "size of the miscellaneous data space.\n");
    exit(2);
  }

  // Copy the table data (the strings) to the shared space.
  n = align_size_up(n, sizeof(HeapWord));
  *(intptr_t*)(*top) = n;
  *top += sizeof(intptr_t);

  for (i = 0; i < table_size(); ++i) {
    for (PackageInfo* pp = table->bucket(i);
                      pp != NULL;
                      pp = pp->next()) {
      int n1 = (int)(strlen(pp->pkgname()) + 1);
      pp->set_pkgname((char*)memcpy(*top, pp->pkgname(), n1));
      *top += n1;
    }
  }
  *top = (char*)align_size_up((intptr_t)*top, sizeof(HeapWord));
}


void ClassLoader::copy_package_info_buckets(char** top, char* end) {
  _package_hash_table->copy_buckets(top, end);
}

void ClassLoader::copy_package_info_table(char** top, char* end) {
  _package_hash_table->copy_table(top, end, _package_hash_table);
}


PackageInfo* ClassLoader::lookup_package(const char *pkgname) {
  const char *cp = strrchr(pkgname, '/');
  if (cp != NULL) {
    // Package prefix found
    int n = cp - pkgname + 1;
    return _package_hash_table->get_entry(pkgname, n);
  }
  return NULL;
}


bool ClassLoader::add_package(const char *pkgname, int classpath_index, TRAPS) {
  assert(pkgname != NULL, "just checking");
  // Bootstrap loader no longer holds system loader lock obj serializing
  // load_instance_class and thereby add_package
  {
    MutexLocker ml(PackageTable_lock, THREAD);
    // First check for previously loaded entry
    PackageInfo* pp = lookup_package(pkgname);
    if (pp != NULL) {
      // Existing entry found, check source of package
      pp->set_index(classpath_index);
      return true;
    }

    const char *cp = strrchr(pkgname, '/');
    if (cp != NULL) {
      // Package prefix found
      int n = cp - pkgname + 1;

      char* new_pkgname = NEW_C_HEAP_ARRAY(char, n + 1);
      if (new_pkgname == NULL) {
        return false;
      }

      memcpy(new_pkgname, pkgname, n);
      new_pkgname[n] = '\0';
      pp = _package_hash_table->new_entry(new_pkgname, n);
      pp->set_index(classpath_index);

      // Insert into hash table
      _package_hash_table->add_entry(pp);
    }
    return true;
  }
}


oop ClassLoader::get_system_package(const char* name, TRAPS) {
  PackageInfo* pp;
  {
    MutexLocker ml(PackageTable_lock, THREAD);
    pp = lookup_package(name);
  }
  if (pp == NULL) {
    return NULL;
  } else {
    Handle p = java_lang_String::create_from_str(pp->filename(), THREAD);
    return p();
  }
}


objArrayOop ClassLoader::get_system_packages(TRAPS) {
  ResourceMark rm(THREAD);
  int nof_entries;
  const char** packages;
  {
    MutexLocker ml(PackageTable_lock, THREAD);
    // Allocate resource char* array containing package names
    nof_entries = _package_hash_table->number_of_entries();
    if ((packages = NEW_RESOURCE_ARRAY(const char*, nof_entries)) == NULL) {
      return NULL;
    }
    _package_hash_table->copy_pkgnames(packages);
  }
  // Allocate objArray and fill with java.lang.String
  objArrayOop r = oopFactory::new_objArray(SystemDictionary::String_klass(),
                                           nof_entries, CHECK_0);
  objArrayHandle result(THREAD, r);
  for (int i = 0; i < nof_entries; i++) {
    Handle str = java_lang_String::create_from_str(packages[i], CHECK_0);
    result->obj_at_put(i, str());
  }

  return result();
}


instanceKlassHandle ClassLoader::load_classfile(symbolHandle h_name, TRAPS) {
  VTuneClassLoadMarker clm;
  ResourceMark rm(THREAD);
  EventMark m("loading class " INTPTR_FORMAT, (address)h_name());
  ThreadProfilerMark tpm(ThreadProfilerMark::classLoaderRegion);

  stringStream st;
  // st.print() uses too much stack space while handling a StackOverflowError
  // st.print("%s.class", h_name->as_utf8());
  st.print_raw(h_name->as_utf8());
  st.print_raw(".class");
  char* name = st.as_string();

  // Lookup stream for parsing .class file
  ClassFileStream* stream = NULL;
  int classpath_index = 0;
  {
    PerfClassTraceTime vmtimer(perf_sys_class_lookup_time(),
                               ((JavaThread*) THREAD)->get_thread_stat()->perf_timers_addr(),
                               PerfClassTraceTime::CLASS_LOAD);
    ClassPathEntry* e = _first_entry;
    while (e != NULL) {
      stream = e->open_stream(name);
      if (stream != NULL) {
        break;
      }
      e = e->next();
      ++classpath_index;
    }
  }

  instanceKlassHandle h(THREAD, klassOop(NULL));
  if (stream != NULL) {

    // class file found, parse it
    ClassFileParser parser(stream);
    Handle class_loader;
    Handle protection_domain;
    symbolHandle parsed_name;
    instanceKlassHandle result = parser.parseClassFile(h_name,
                                                       class_loader,
                                                       protection_domain,
                                                       parsed_name,
                                                       false,
                                                       CHECK_(h));

    // add to package table
    if (add_package(name, classpath_index, THREAD)) {
      h = result;
    }
  }

  return h;
}


void ClassLoader::create_package_info_table(HashtableBucket *t, int length,
                                            int number_of_entries) {
  assert(_package_hash_table == NULL, "One package info table allowed.");
  assert(length == package_hash_table_size * sizeof(HashtableBucket),
         "bad shared package info size.");
  _package_hash_table = new PackageHashtable(package_hash_table_size, t,
                                             number_of_entries);
}


void ClassLoader::create_package_info_table() {
    assert(_package_hash_table == NULL, "shouldn't have one yet");
    _package_hash_table = new PackageHashtable(package_hash_table_size);
}


// Initialize the class loader's access to methods in libzip.  Parse and
// process the boot classpath into a list ClassPathEntry objects.  Once
// this list has been created, it must not change order (see class PackageInfo)
// it can be appended to and is by jvmti and the kernel vm.

void ClassLoader::initialize() {
  assert(_package_hash_table == NULL, "should have been initialized by now.");
  EXCEPTION_MARK;

  if (UsePerfData) {
    // jvmstat performance counters
    NEWPERFTICKCOUNTER(_perf_accumulated_time, SUN_CLS, "time");
    NEWPERFTICKCOUNTER(_perf_class_init_time, SUN_CLS, "classInitTime");
    NEWPERFTICKCOUNTER(_perf_class_init_selftime, SUN_CLS, "classInitTime.self");
    NEWPERFTICKCOUNTER(_perf_class_verify_time, SUN_CLS, "classVerifyTime");
    NEWPERFTICKCOUNTER(_perf_class_verify_selftime, SUN_CLS, "classVerifyTime.self");
    NEWPERFTICKCOUNTER(_perf_class_link_time, SUN_CLS, "classLinkedTime");
    NEWPERFTICKCOUNTER(_perf_class_link_selftime, SUN_CLS, "classLinkedTime.self");
    NEWPERFEVENTCOUNTER(_perf_classes_inited, SUN_CLS, "initializedClasses");
    NEWPERFEVENTCOUNTER(_perf_classes_linked, SUN_CLS, "linkedClasses");
    NEWPERFEVENTCOUNTER(_perf_classes_verified, SUN_CLS, "verifiedClasses");

    NEWPERFTICKCOUNTER(_perf_class_parse_time, SUN_CLS, "parseClassTime");
    NEWPERFTICKCOUNTER(_perf_class_parse_selftime, SUN_CLS, "parseClassTime.self");
    NEWPERFTICKCOUNTER(_perf_sys_class_lookup_time, SUN_CLS, "lookupSysClassTime");
    NEWPERFTICKCOUNTER(_perf_shared_classload_time, SUN_CLS, "sharedClassLoadTime");
    NEWPERFTICKCOUNTER(_perf_sys_classload_time, SUN_CLS, "sysClassLoadTime");
    NEWPERFTICKCOUNTER(_perf_app_classload_time, SUN_CLS, "appClassLoadTime");
    NEWPERFTICKCOUNTER(_perf_app_classload_selftime, SUN_CLS, "appClassLoadTime.self");
    NEWPERFEVENTCOUNTER(_perf_app_classload_count, SUN_CLS, "appClassLoadCount");
    NEWPERFTICKCOUNTER(_perf_define_appclasses, SUN_CLS, "defineAppClasses");
    NEWPERFTICKCOUNTER(_perf_define_appclass_time, SUN_CLS, "defineAppClassTime");
    NEWPERFTICKCOUNTER(_perf_define_appclass_selftime, SUN_CLS, "defineAppClassTime.self");
    NEWPERFBYTECOUNTER(_perf_app_classfile_bytes_read, SUN_CLS, "appClassBytes");
    NEWPERFBYTECOUNTER(_perf_sys_classfile_bytes_read, SUN_CLS, "sysClassBytes");


    // The following performance counters are added for measuring the impact
    // of the bug fix of 6365597. They are mainly focused on finding out
    // the behavior of system & user-defined classloader lock, whether
    // ClassLoader.loadClass/findClass is being called synchronized or not.
    // Also two additional counters are created to see whether 'UnsyncloadClass'
    // flag is being set or not and how many times load_instance_class call
    // fails with linkageError etc.
    NEWPERFEVENTCOUNTER(_sync_systemLoaderLockContentionRate, SUN_CLS,
                        "systemLoaderLockContentionRate");
    NEWPERFEVENTCOUNTER(_sync_nonSystemLoaderLockContentionRate, SUN_CLS,
                        "nonSystemLoaderLockContentionRate");
    NEWPERFEVENTCOUNTER(_sync_JVMFindLoadedClassLockFreeCounter, SUN_CLS,
                        "jvmFindLoadedClassNoLockCalls");
    NEWPERFEVENTCOUNTER(_sync_JVMDefineClassLockFreeCounter, SUN_CLS,
                        "jvmDefineClassNoLockCalls");

    NEWPERFEVENTCOUNTER(_sync_JNIDefineClassLockFreeCounter, SUN_CLS,
                        "jniDefineClassNoLockCalls");

    NEWPERFEVENTCOUNTER(_unsafe_defineClassCallCounter, SUN_CLS,
                        "unsafeDefineClassCalls");

    NEWPERFEVENTCOUNTER(_isUnsyncloadClass, SUN_CLS, "isUnsyncloadClassSet");
    NEWPERFEVENTCOUNTER(_load_instance_class_failCounter, SUN_CLS,
                        "loadInstanceClassFailRate");

    // increment the isUnsyncloadClass counter if UnsyncloadClass is set.
    if (UnsyncloadClass) {
      _isUnsyncloadClass->inc();
    }
  }

  // lookup zip library entry points
  load_zip_library();
  // initialize search path
  setup_bootstrap_search_path();
  if (LazyBootClassLoader) {
    // set up meta index which makes boot classpath initialization lazier
    setup_meta_index();
  }
}


jlong ClassLoader::classloader_time_ms() {
  return UsePerfData ?
    Management::ticks_to_ms(_perf_accumulated_time->get_value()) : -1;
}

jlong ClassLoader::class_init_count() {
  return UsePerfData ? _perf_classes_inited->get_value() : -1;
}

jlong ClassLoader::class_init_time_ms() {
  return UsePerfData ?
    Management::ticks_to_ms(_perf_class_init_time->get_value()) : -1;
}

jlong ClassLoader::class_verify_time_ms() {
  return UsePerfData ?
    Management::ticks_to_ms(_perf_class_verify_time->get_value()) : -1;
}

jlong ClassLoader::class_link_count() {
  return UsePerfData ? _perf_classes_linked->get_value() : -1;
}

jlong ClassLoader::class_link_time_ms() {
  return UsePerfData ?
    Management::ticks_to_ms(_perf_class_link_time->get_value()) : -1;
}

int ClassLoader::compute_Object_vtable() {
  // hardwired for JDK1.2 -- would need to duplicate class file parsing
  // code to determine actual value from file
  // Would be value '11' if finals were in vtable
  int JDK_1_2_Object_vtable_size = 5;
  return JDK_1_2_Object_vtable_size * vtableEntry::size();
}


void classLoader_init() {
  ClassLoader::initialize();
}


bool ClassLoader::get_canonical_path(char* orig, char* out, int len) {
  assert(orig != NULL && out != NULL && len > 0, "bad arguments");
  if (CanonicalizeEntry != NULL) {
    JNIEnv* env = JavaThread::current()->jni_environment();
    if ((CanonicalizeEntry)(env, hpi::native_path(orig), out, len) < 0) {
      return false;
    }
  } else {
    // On JDK 1.2.2 the Canonicalize does not exist, so just do nothing
    strncpy(out, orig, len);
    out[len - 1] = '\0';
  }
  return true;
}

#ifndef PRODUCT

void ClassLoader::verify() {
  _package_hash_table->verify();
}


// CompileTheWorld
//
// Iterates over all class path entries and forces compilation of all methods
// in all classes found. Currently, only zip/jar archives are searched.
//
// The classes are loaded by the Java level bootstrap class loader, and the
// initializer is called. If DelayCompilationDuringStartup is true (default),
// the interpreter will run the initialization code. Note that forcing
// initialization in this way could potentially lead to initialization order
// problems, in which case we could just force the initialization bit to be set.


// We need to iterate over the contents of a zip/jar file, so we replicate the
// jzcell and jzfile definitions from zip_util.h but rename jzfile to real_jzfile,
// since jzfile already has a void* definition.
//
// Note that this is only used in debug mode.
//
// HotSpot integration note:
// Matches zip_util.h 1.14 99/06/01 from jdk1.3 beta H build


// JDK 1.3 version
typedef struct real_jzentry13 {         /* Zip file entry */
    char *name;                 /* entry name */
    jint time;                  /* modification time */
    jint size;                  /* size of uncompressed data */
    jint csize;                 /* size of compressed data (zero if uncompressed) */
    jint crc;                   /* crc of uncompressed data */
    char *comment;              /* optional zip file comment */
    jbyte *extra;               /* optional extra data */
    jint pos;                   /* position of LOC header (if negative) or data */
} real_jzentry13;

typedef struct real_jzfile13 {  /* Zip file */
    char *name;                 /* zip file name */
    jint refs;                  /* number of active references */
    jint fd;                    /* open file descriptor */
    void *lock;                 /* read lock */
    char *comment;              /* zip file comment */
    char *msg;                  /* zip error message */
    void *entries;              /* array of hash cells */
    jint total;                 /* total number of entries */
    unsigned short *table;      /* Hash chain heads: indexes into entries */
    jint tablelen;              /* number of hash eads */
    real_jzfile13 *next;        /* next zip file in search list */
    jzentry *cache;             /* we cache the most recently freed jzentry */
    /* Information on metadata names in META-INF directory */
    char **metanames;           /* array of meta names (may have null names) */
    jint metacount;             /* number of slots in metanames array */
    /* If there are any per-entry comments, they are in the comments array */
    char **comments;
} real_jzfile13;

// JDK 1.2 version
typedef struct real_jzentry12 {  /* Zip file entry */
    char *name;                  /* entry name */
    jint time;                   /* modification time */
    jint size;                   /* size of uncompressed data */
    jint csize;                  /* size of compressed data (zero if uncompressed) */
    jint crc;                    /* crc of uncompressed data */
    char *comment;               /* optional zip file comment */
    jbyte *extra;                /* optional extra data */
    jint pos;                    /* position of LOC header (if negative) or data */
    struct real_jzentry12 *next; /* next entry in hash table */
} real_jzentry12;

typedef struct real_jzfile12 {  /* Zip file */
    char *name;                 /* zip file name */
    jint refs;                  /* number of active references */
    jint fd;                    /* open file descriptor */
    void *lock;                 /* read lock */
    char *comment;              /* zip file comment */
    char *msg;                  /* zip error message */
    real_jzentry12 *entries;    /* array of zip entries */
    jint total;                 /* total number of entries */
    real_jzentry12 **table;     /* hash table of entries */
    jint tablelen;              /* number of buckets */
    jzfile *next;               /* next zip file in search list */
} real_jzfile12;


void ClassPathDirEntry::compile_the_world(Handle loader, TRAPS) {
  // For now we only compile all methods in all classes in zip/jar files
  tty->print_cr("CompileTheWorld : Skipped classes in %s", _dir);
  tty->cr();
}


bool ClassPathDirEntry::is_rt_jar() {
  return false;
}

void ClassPathZipEntry::compile_the_world(Handle loader, TRAPS) {
  if (JDK_Version::is_jdk12x_version()) {
    compile_the_world12(loader, THREAD);
  } else {
    compile_the_world13(loader, THREAD);
  }
  if (HAS_PENDING_EXCEPTION) {
    if (PENDING_EXCEPTION->is_a(SystemDictionary::OutOfMemoryError_klass())) {
      CLEAR_PENDING_EXCEPTION;
      tty->print_cr("\nCompileTheWorld : Ran out of memory\n");
      size_t used = Universe::heap()->permanent_used();
      size_t capacity = Universe::heap()->permanent_capacity();
      tty->print_cr("Permanent generation used %dK of %dK", used/K, capacity/K);
      tty->print_cr("Increase size by setting e.g. -XX:MaxPermSize=%dK\n", capacity*2/K);
    } else {
      tty->print_cr("\nCompileTheWorld : Unexpected exception occurred\n");
    }
  }
}

// Version that works for JDK 1.3.x
void ClassPathZipEntry::compile_the_world13(Handle loader, TRAPS) {
  real_jzfile13* zip = (real_jzfile13*) _zip;
  tty->print_cr("CompileTheWorld : Compiling all classes in %s", zip->name);
  tty->cr();
  // Iterate over all entries in zip file
  for (int n = 0; ; n++) {
    real_jzentry13 * ze = (real_jzentry13 *)((*GetNextEntry)(_zip, n));
    if (ze == NULL) break;
    ClassLoader::compile_the_world_in(ze->name, loader, CHECK);
  }
}


// Version that works for JDK 1.2.x
void ClassPathZipEntry::compile_the_world12(Handle loader, TRAPS) {
  real_jzfile12* zip = (real_jzfile12*) _zip;
  tty->print_cr("CompileTheWorld : Compiling all classes in %s", zip->name);
  tty->cr();
  // Iterate over all entries in zip file
  for (int n = 0; ; n++) {
    real_jzentry12 * ze = (real_jzentry12 *)((*GetNextEntry)(_zip, n));
    if (ze == NULL) break;
    ClassLoader::compile_the_world_in(ze->name, loader, CHECK);
  }
}

bool ClassPathZipEntry::is_rt_jar() {
  if (JDK_Version::is_jdk12x_version()) {
    return is_rt_jar12();
  } else {
    return is_rt_jar13();
  }
}

// JDK 1.3 version
bool ClassPathZipEntry::is_rt_jar13() {
  real_jzfile13* zip = (real_jzfile13*) _zip;
  int len = (int)strlen(zip->name);
  // Check whether zip name ends in "rt.jar"
  // This will match other archives named rt.jar as well, but this is
  // only used for debugging.
  return (len >= 6) && (strcasecmp(zip->name + len - 6, "rt.jar") == 0);
}

// JDK 1.2 version
bool ClassPathZipEntry::is_rt_jar12() {
  real_jzfile12* zip = (real_jzfile12*) _zip;
  int len = (int)strlen(zip->name);
  // Check whether zip name ends in "rt.jar"
  // This will match other archives named rt.jar as well, but this is
  // only used for debugging.
  return (len >= 6) && (strcasecmp(zip->name + len - 6, "rt.jar") == 0);
}

void LazyClassPathEntry::compile_the_world(Handle loader, TRAPS) {
  resolve_entry()->compile_the_world(loader, CHECK);
}

bool LazyClassPathEntry::is_rt_jar() {
  return resolve_entry()->is_rt_jar();
}

void ClassLoader::compile_the_world() {
  EXCEPTION_MARK;
  HandleMark hm(THREAD);
  ResourceMark rm(THREAD);
  // Make sure we don't run with background compilation
  BackgroundCompilation = false;
  // Find bootstrap loader
  Handle system_class_loader (THREAD, SystemDictionary::java_system_loader());
  // Iterate over all bootstrap class path entries
  ClassPathEntry* e = _first_entry;
  while (e != NULL) {
    // We stop at rt.jar, unless it is the first bootstrap path entry
    if (e->is_rt_jar() && e != _first_entry) break;
    e->compile_the_world(system_class_loader, CATCH);
    e = e->next();
  }
  tty->print_cr("CompileTheWorld : Done");
  {
    // Print statistics as if before normal exit:
    extern void print_statistics();
    print_statistics();
  }
  vm_exit(0);
}

int ClassLoader::_compile_the_world_counter = 0;
static int _codecache_sweep_counter = 0;

void ClassLoader::compile_the_world_in(char* name, Handle loader, TRAPS) {
  int len = (int)strlen(name);
  if (len > 6 && strcmp(".class", name + len - 6) == 0) {
    // We have a .class file
    char buffer[2048];
    strncpy(buffer, name, len - 6);
    buffer[len-6] = 0;
    // If the file has a period after removing .class, it's not really a
    // valid class file.  The class loader will check everything else.
    if (strchr(buffer, '.') == NULL) {
      _compile_the_world_counter++;
      if (_compile_the_world_counter > CompileTheWorldStopAt) return;

      // Construct name without extension
      symbolHandle sym = oopFactory::new_symbol_handle(buffer, CHECK);
      // Use loader to load and initialize class
      klassOop ik = SystemDictionary::resolve_or_null(sym, loader, Handle(), THREAD);
      instanceKlassHandle k (THREAD, ik);
      if (k.not_null() && !HAS_PENDING_EXCEPTION) {
        k->initialize(THREAD);
      }
      bool exception_occurred = HAS_PENDING_EXCEPTION;
      CLEAR_PENDING_EXCEPTION;
      if (CompileTheWorldPreloadClasses && k.not_null()) {
        constantPoolKlass::preload_and_initialize_all_classes(k->constants(), THREAD);
        if (HAS_PENDING_EXCEPTION) {
          // If something went wrong in preloading we just ignore it
          CLEAR_PENDING_EXCEPTION;
          tty->print_cr("Preloading failed for (%d) %s", _compile_the_world_counter, buffer);
        }
      }

      if (_compile_the_world_counter >= CompileTheWorldStartAt) {
        if (k.is_null() || (exception_occurred && !CompileTheWorldIgnoreInitErrors)) {
          // If something went wrong (e.g. ExceptionInInitializerError) we skip this class
          tty->print_cr("CompileTheWorld (%d) : Skipping %s", _compile_the_world_counter, buffer);
        } else {
          tty->print_cr("CompileTheWorld (%d) : %s", _compile_the_world_counter, buffer);
          // Preload all classes to get around uncommon traps
          // Iterate over all methods in class
          for (int n = 0; n < k->methods()->length(); n++) {
            methodHandle m (THREAD, methodOop(k->methods()->obj_at(n)));
            if (CompilationPolicy::canBeCompiled(m)) {

              if (++_codecache_sweep_counter == CompileTheWorldSafepointInterval) {
                // Give sweeper a chance to keep up with CTW
                VM_ForceSafepoint op;
                VMThread::execute(&op);
                _codecache_sweep_counter = 0;
              }
              // Force compilation
              CompileBroker::compile_method(m, InvocationEntryBci,
                                            methodHandle(), 0, "CTW", THREAD);
              if (HAS_PENDING_EXCEPTION) {
                CLEAR_PENDING_EXCEPTION;
                tty->print_cr("CompileTheWorld (%d) : Skipping method: %s", _compile_the_world_counter, m->name()->as_C_string());
              }
              if (TieredCompilation) {
                // Clobber the first compile and force second tier compilation
                nmethod* nm = m->code();
                if (nm != NULL) {
                  // Throw out the code so that the code cache doesn't fill up
                  nm->make_not_entrant();
                  m->clear_code();
                }
                CompileBroker::compile_method(m, InvocationEntryBci,
                                              methodHandle(), 0, "CTW", THREAD);
                if (HAS_PENDING_EXCEPTION) {
                  CLEAR_PENDING_EXCEPTION;
                  tty->print_cr("CompileTheWorld (%d) : Skipping method: %s", _compile_the_world_counter, m->name()->as_C_string());
                }
              }
            }

            nmethod* nm = m->code();
            if (nm != NULL) {
              // Throw out the code so that the code cache doesn't fill up
              nm->make_not_entrant();
              m->clear_code();
            }
          }
        }
      }
    }
  }
}

#endif //PRODUCT
