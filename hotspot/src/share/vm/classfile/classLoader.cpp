/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/jimage.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/modules.hpp"
#include "classfile/packageEntry.hpp"
#include "classfile/klassFactory.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "compiler/compileBroker.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/generation.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/oopMapCache.hpp"
#include "logging/logTag.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/filemap.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "prims/jvm_misc.hpp"
#include "runtime/arguments.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/handles.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/os.hpp"
#include "runtime/threadCritical.hpp"
#include "runtime/timer.hpp"
#include "runtime/vm_version.hpp"
#include "services/management.hpp"
#include "services/threadService.hpp"
#include "utilities/events.hpp"
#include "utilities/hashtable.inline.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_CDS
#include "classfile/sharedClassUtil.hpp"
#include "classfile/sharedPathsMiscInfo.hpp"
#endif

// Entry points in zip.dll for loading zip/jar file entries

typedef void * * (JNICALL *ZipOpen_t)(const char *name, char **pmsg);
typedef void (JNICALL *ZipClose_t)(jzfile *zip);
typedef jzentry* (JNICALL *FindEntry_t)(jzfile *zip, const char *name, jint *sizeP, jint *nameLen);
typedef jboolean (JNICALL *ReadEntry_t)(jzfile *zip, jzentry *entry, unsigned char *buf, char *namebuf);
typedef jboolean (JNICALL *ReadMappedEntry_t)(jzfile *zip, jzentry *entry, unsigned char **buf, char *namebuf);
typedef jzentry* (JNICALL *GetNextEntry_t)(jzfile *zip, jint n);
typedef jboolean (JNICALL *ZipInflateFully_t)(void *inBuf, jlong inLen, void *outBuf, jlong outLen, char **pmsg);
typedef jint     (JNICALL *Crc32_t)(jint crc, const jbyte *buf, jint len);

static ZipOpen_t         ZipOpen            = NULL;
static ZipClose_t        ZipClose           = NULL;
static FindEntry_t       FindEntry          = NULL;
static ReadEntry_t       ReadEntry          = NULL;
static ReadMappedEntry_t ReadMappedEntry    = NULL;
static GetNextEntry_t    GetNextEntry       = NULL;
static canonicalize_fn_t CanonicalizeEntry  = NULL;
static ZipInflateFully_t ZipInflateFully    = NULL;
static Crc32_t           Crc32              = NULL;

// Entry points for jimage.dll for loading jimage file entries

static JImageOpen_t                    JImageOpen                    = NULL;
static JImageClose_t                   JImageClose                   = NULL;
static JImagePackageToModule_t         JImagePackageToModule         = NULL;
static JImageFindResource_t            JImageFindResource            = NULL;
static JImageGetResource_t             JImageGetResource             = NULL;
static JImageResourceIterator_t        JImageResourceIterator        = NULL;

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
int             ClassLoader::_num_entries         = 0;
ClassPathEntry* ClassLoader::_first_append_entry = NULL;
bool            ClassLoader::_has_jimage = false;
#if INCLUDE_CDS
GrowableArray<char*>* ClassLoader::_boot_modules_array = NULL;
GrowableArray<char*>* ClassLoader::_platform_modules_array = NULL;
SharedPathsMiscInfo* ClassLoader::_shared_paths_misc_info = NULL;
#endif

// helper routines
bool string_starts_with(const char* str, const char* str_to_find) {
  size_t str_len = strlen(str);
  size_t str_to_find_len = strlen(str_to_find);
  if (str_to_find_len > str_len) {
    return false;
  }
  return (strncmp(str, str_to_find, str_to_find_len) == 0);
}

static const char* get_jimage_version_string() {
  static char version_string[10] = "";
  if (version_string[0] == '\0') {
    jio_snprintf(version_string, sizeof(version_string), "%d.%d",
                 Abstract_VM_Version::vm_major_version(), Abstract_VM_Version::vm_minor_version());
  }
  return (const char*)version_string;
}

bool ClassLoader::string_ends_with(const char* str, const char* str_to_find) {
  size_t str_len = strlen(str);
  size_t str_to_find_len = strlen(str_to_find);
  if (str_to_find_len > str_len) {
    return false;
  }
  return (strncmp(str + (str_len - str_to_find_len), str_to_find, str_to_find_len) == 0);
}


ClassPathDirEntry::ClassPathDirEntry(const char* dir) : ClassPathEntry() {
  char* copy = NEW_C_HEAP_ARRAY(char, strlen(dir)+1, mtClass);
  strcpy(copy, dir);
  _dir = copy;
}


ClassFileStream* ClassPathDirEntry::open_stream(const char* name, TRAPS) {
  // construct full path name
  char path[JVM_MAXPATHLEN];
  if (jio_snprintf(path, sizeof(path), "%s%s%s", _dir, os::file_separator(), name) == -1) {
    return NULL;
  }
  // check if file exists
  struct stat st;
  if (os::stat(path, &st) == 0) {
#if INCLUDE_CDS
    if (DumpSharedSpaces) {
      // We have already check in ClassLoader::check_shared_classpath() that the directory is empty, so
      // we should never find a file underneath it -- unless user has added a new file while we are running
      // the dump, in which case let's quit!
      ShouldNotReachHere();
    }
#endif
    // found file, open it
    int file_handle = os::open(path, 0, 0);
    if (file_handle != -1) {
      // read contents into resource array
      u1* buffer = NEW_RESOURCE_ARRAY(u1, st.st_size);
      size_t num_read = os::read(file_handle, (char*) buffer, st.st_size);
      // close file
      os::close(file_handle);
      // construct ClassFileStream
      if (num_read == (size_t)st.st_size) {
        if (UsePerfData) {
          ClassLoader::perf_sys_classfile_bytes_read()->inc(num_read);
        }
        // Resource allocated
        return new ClassFileStream(buffer,
                                   st.st_size,
                                   _dir,
                                   ClassFileStream::verify);
      }
    }
  }
  return NULL;
}

ClassPathZipEntry::ClassPathZipEntry(jzfile* zip, const char* zip_name, bool is_boot_append) : ClassPathEntry() {
  _zip = zip;
  char *copy = NEW_C_HEAP_ARRAY(char, strlen(zip_name)+1, mtClass);
  strcpy(copy, zip_name);
  _zip_name = copy;
  _is_boot_append = is_boot_append;
}

ClassPathZipEntry::~ClassPathZipEntry() {
  if (ZipClose != NULL) {
    (*ZipClose)(_zip);
  }
  FREE_C_HEAP_ARRAY(char, _zip_name);
}

u1* ClassPathZipEntry::open_entry(const char* name, jint* filesize, bool nul_terminate, TRAPS) {
    // enable call to C land
  JavaThread* thread = JavaThread::current();
  ThreadToNativeFromVM ttn(thread);
  // check whether zip archive contains name
  jint name_len;
  jzentry* entry = (*FindEntry)(_zip, name, filesize, &name_len);
  if (entry == NULL) return NULL;
  u1* buffer;
  char name_buf[128];
  char* filename;
  if (name_len < 128) {
    filename = name_buf;
  } else {
    filename = NEW_RESOURCE_ARRAY(char, name_len + 1);
  }

  // file found, get pointer to the entry in mmapped jar file.
  if (ReadMappedEntry == NULL ||
      !(*ReadMappedEntry)(_zip, entry, &buffer, filename)) {
      // mmapped access not available, perhaps due to compression,
      // read contents into resource array
      int size = (*filesize) + ((nul_terminate) ? 1 : 0);
      buffer = NEW_RESOURCE_ARRAY(u1, size);
      if (!(*ReadEntry)(_zip, entry, buffer, filename)) return NULL;
  }

  // return result
  if (nul_terminate) {
    buffer[*filesize] = 0;
  }
  return buffer;
}

#if INCLUDE_CDS
u1* ClassPathZipEntry::open_versioned_entry(const char* name, jint* filesize, TRAPS) {
  u1* buffer = NULL;
  if (!_is_boot_append) {
    assert(DumpSharedSpaces, "Should be called only for non-boot entries during dump time");
    // We presume default is multi-release enabled
    const char* multi_ver = Arguments::get_property("jdk.util.jar.enableMultiRelease");
    const char* verstr = Arguments::get_property("jdk.util.jar.version");
    bool is_multi_ver = (multi_ver == NULL ||
                         strcmp(multi_ver, "true") == 0 ||
                         strcmp(multi_ver, "force")  == 0) &&
                         is_multiple_versioned(THREAD);
    // command line version setting
    int version = 0;
    const int base_version = 8; // JDK8
    int cur_ver = JDK_Version::current().major_version();
    if (verstr != NULL) {
      version = atoi(verstr);
      if (version < base_version || version > cur_ver) {
        is_multi_ver = false;
        // print out warning, do not use assertion here since it will continue to look
        // for proper version.
        warning("JDK%d is not supported in multiple version jars", version);
      }
    }

    if (is_multi_ver) {
      int n;
      char entry_name[JVM_MAXPATHLEN];
      if (version > 0) {
        n = jio_snprintf(entry_name, sizeof(entry_name), "META-INF/versions/%d/%s", version, name);
        entry_name[n] = '\0';
        buffer = open_entry((const char*)entry_name, filesize, false, CHECK_NULL);
        if (buffer == NULL) {
          warning("Could not find %s in %s, try to find highest version instead", entry_name, _zip_name);
        }
      }
      if (buffer == NULL) {
        for (int i = cur_ver; i >= base_version; i--) {
          n = jio_snprintf(entry_name, sizeof(entry_name), "META-INF/versions/%d/%s", i, name);
          entry_name[n] = '\0';
          buffer = open_entry((const char*)entry_name, filesize, false, CHECK_NULL);
          if (buffer != NULL) {
            break;
          }
        }
      }
    }
  }
  return buffer;
}

bool ClassPathZipEntry::is_multiple_versioned(TRAPS) {
  assert(DumpSharedSpaces, "called only at dump time");
  jint size;
  char* buffer = (char*)open_entry("META-INF/MANIFEST.MF", &size, false, CHECK_false);
  if (buffer != NULL) {
    if (strstr(buffer, "Multi-Release: true") != NULL) {
      return true;
    }
  }
  return false;
}
#endif // INCLUDE_CDS

ClassFileStream* ClassPathZipEntry::open_stream(const char* name, TRAPS) {
  jint filesize;
  u1* buffer = open_versioned_entry(name, &filesize, CHECK_NULL);
  if (buffer == NULL) {
    buffer = open_entry(name, &filesize, false, CHECK_NULL);
    if (buffer == NULL) {
      return NULL;
    }
  }
  if (UsePerfData) {
    ClassLoader::perf_sys_classfile_bytes_read()->inc(filesize);
  }
  // Resource allocated
  return new ClassFileStream(buffer,
                             filesize,
                             _zip_name,
                             ClassFileStream::verify);
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

ClassPathImageEntry::ClassPathImageEntry(JImageFile* jimage, const char* name) :
  ClassPathEntry(),
  _jimage(jimage) {
  guarantee(jimage != NULL, "jimage file is null");
  guarantee(name != NULL, "jimage file name is null");
  size_t len = strlen(name) + 1;
  _name = NEW_C_HEAP_ARRAY(const char, len, mtClass);
  strncpy((char *)_name, name, len);
}

ClassPathImageEntry::~ClassPathImageEntry() {
  if (_name != NULL) {
    FREE_C_HEAP_ARRAY(const char, _name);
    _name = NULL;
  }
  if (_jimage != NULL) {
    (*JImageClose)(_jimage);
    _jimage = NULL;
  }
}

void ClassPathImageEntry::name_to_package(const char* name, char* buffer, int length) {
  const char *pslash = strrchr(name, '/');
  if (pslash == NULL) {
    buffer[0] = '\0';
    return;
  }
  int len = pslash - name;
#if INCLUDE_CDS
  if (len <= 0 && DumpSharedSpaces) {
    buffer[0] = '\0';
    return;
  }
#endif
  assert(len > 0, "Bad length for package name");
  if (len >= length) {
    buffer[0] = '\0';
    return;
  }
  // drop name after last slash (including slash)
  // Ex., "java/lang/String.class" => "java/lang"
  strncpy(buffer, name, len);
  // ensure string termination (strncpy does not guarantee)
  buffer[len] = '\0';
}

// For a class in a named module, look it up in the jimage file using this syntax:
//    /<module-name>/<package-name>/<base-class>
//
// Assumptions:
//     1. There are no unnamed modules in the jimage file.
//     2. A package is in at most one module in the jimage file.
//
ClassFileStream* ClassPathImageEntry::open_stream(const char* name, TRAPS) {
  jlong size;
  JImageLocationRef location = (*JImageFindResource)(_jimage, "", get_jimage_version_string(), name, &size);

  if (location == 0) {
    char package[JIMAGE_MAX_PATH];
    name_to_package(name, package, JIMAGE_MAX_PATH);

#if INCLUDE_CDS
    if (package[0] == '\0' && DumpSharedSpaces) {
      return NULL;
    }
#endif
    if (package[0] != '\0') {
      if (!Universe::is_module_initialized()) {
        location = (*JImageFindResource)(_jimage, "java.base", get_jimage_version_string(), name, &size);
#if INCLUDE_CDS
        // CDS uses the boot class loader to load classes whose packages are in
        // modules defined for other class loaders.  So, for now, get their module
        // names from the "modules" jimage file.
        if (DumpSharedSpaces && location == 0) {
          const char* module_name = (*JImagePackageToModule)(_jimage, package);
          if (module_name != NULL) {
            location = (*JImageFindResource)(_jimage, module_name, get_jimage_version_string(), name, &size);
          }
        }
#endif

      } else {
        // Get boot class loader's package entry table
        PackageEntryTable* pkgEntryTable =
          ClassLoaderData::the_null_class_loader_data()->packages();
        // Get package's package entry
        TempNewSymbol pkg_symbol = SymbolTable::new_symbol(package, CHECK_NULL);
        PackageEntry* package_entry = pkgEntryTable->lookup_only(pkg_symbol);

        if (package_entry != NULL) {
          ResourceMark rm;
          // Get the module name
          ModuleEntry* module = package_entry->module();
          assert(module != NULL, "Boot classLoader package missing module");
          assert(module->is_named(), "Boot classLoader package is in unnamed module");
          const char* module_name = module->name()->as_C_string();
          if (module_name != NULL) {
            location = (*JImageFindResource)(_jimage, module_name, get_jimage_version_string(), name, &size);
          }
        }
      }
    }
  }
  if (location != 0) {
    if (UsePerfData) {
      ClassLoader::perf_sys_classfile_bytes_read()->inc(size);
    }
    char* data = NEW_RESOURCE_ARRAY(char, size);
    (*JImageGetResource)(_jimage, location, data, size);
    // Resource allocated
    return new ClassFileStream((u1*)data,
                               (int)size,
                               _name,
                               ClassFileStream::verify);
  }

  return NULL;
}

#ifndef PRODUCT
bool ctw_visitor(JImageFile* jimage,
        const char* module_name, const char* version, const char* package,
        const char* name, const char* extension, void* arg) {
  if (strcmp(extension, "class") == 0) {
    Thread* THREAD = Thread::current();
    char path[JIMAGE_MAX_PATH];
    jio_snprintf(path, JIMAGE_MAX_PATH - 1, "%s/%s.class", package, name);
    ClassLoader::compile_the_world_in(path, *(Handle*)arg, THREAD);
    return !HAS_PENDING_EXCEPTION;
  }
  return true;
}

void ClassPathImageEntry::compile_the_world(Handle loader, TRAPS) {
  tty->print_cr("CompileTheWorld : Compiling all classes in %s", name());
  tty->cr();
  (*JImageResourceIterator)(_jimage, (JImageResourceVisitor_t)ctw_visitor, (void *)&loader);
  if (HAS_PENDING_EXCEPTION) {
    if (PENDING_EXCEPTION->is_a(SystemDictionary::OutOfMemoryError_klass())) {
      CLEAR_PENDING_EXCEPTION;
      tty->print_cr("\nCompileTheWorld : Ran out of memory\n");
      tty->print_cr("Increase class metadata storage if a limit was set");
    } else {
      tty->print_cr("\nCompileTheWorld : Unexpected exception occurred\n");
    }
  }
}
#endif

bool ClassPathImageEntry::is_jrt() {
  return ClassLoader::is_jrt(name());
}

#if INCLUDE_CDS
void ClassLoader::exit_with_path_failure(const char* error, const char* message) {
  assert(DumpSharedSpaces, "only called at dump time");
  tty->print_cr("Hint: enable -Xlog:classpath=info to diagnose the failure");
  vm_exit_during_initialization(error, message);
}
#endif

void ClassLoader::trace_class_path(const char* msg, const char* name) {
  if (log_is_enabled(Info, classpath)) {
    ResourceMark rm;
    outputStream* out = Log(classpath)::info_stream();
    if (msg) {
      out->print("%s", msg);
    }
    if (name) {
      if (strlen(name) < 256) {
        out->print("%s", name);
      } else {
        // For very long paths, we need to print each character separately,
        // as print_cr() has a length limit
        while (name[0] != '\0') {
          out->print("%c", name[0]);
          name++;
        }
      }
    }
    out->cr();
  }
}

#if INCLUDE_CDS
void ClassLoader::check_shared_classpath(const char *path) {
  if (strcmp(path, "") == 0) {
    exit_with_path_failure("Cannot have empty path in archived classpaths", NULL);
  }

  struct stat st;
  if (os::stat(path, &st) == 0) {
    if ((st.st_mode & S_IFREG) != S_IFREG) { // is directory
      if (!os::dir_is_empty(path)) {
        tty->print_cr("Error: non-empty directory '%s'", path);
        exit_with_path_failure("CDS allows only empty directories in archived classpaths", NULL);
      }
    }
  }
}
#endif

void ClassLoader::setup_bootstrap_search_path() {
  assert(_first_entry == NULL, "should not setup bootstrap class search path twice");
  const char* sys_class_path = Arguments::get_sysclasspath();
  const char* java_class_path = Arguments::get_appclasspath();
  if (PrintSharedArchiveAndExit) {
    // Don't print sys_class_path - this is the bootcp of this current VM process, not necessarily
    // the same as the bootcp of the shared archive.
  } else {
    trace_class_path("bootstrap loader class path=", sys_class_path);
    trace_class_path("classpath: ", java_class_path);
  }
#if INCLUDE_CDS
  if (DumpSharedSpaces) {
    _shared_paths_misc_info->add_boot_classpath(sys_class_path);
  }
#endif
  setup_search_path(sys_class_path, true);
}

#if INCLUDE_CDS
int ClassLoader::get_shared_paths_misc_info_size() {
  return _shared_paths_misc_info->get_used_bytes();
}

void* ClassLoader::get_shared_paths_misc_info() {
  return _shared_paths_misc_info->buffer();
}

bool ClassLoader::check_shared_paths_misc_info(void *buf, int size) {
  SharedPathsMiscInfo* checker = SharedClassUtil::allocate_shared_paths_misc_info((char*)buf, size);
  bool result = checker->check();
  delete checker;
  return result;
}
#endif

void ClassLoader::setup_search_path(const char *class_path, bool bootstrap_search) {
  int offset = 0;
  int len = (int)strlen(class_path);
  int end = 0;
  bool mark_append_entry = false;

  // Iterate over class path entries
  for (int start = 0; start < len; start = end) {
    while (class_path[end] && class_path[end] != os::path_separator()[0]) {
      end++;
    }
    EXCEPTION_MARK;
    ResourceMark rm(THREAD);
    mark_append_entry = (mark_append_entry ||
      (bootstrap_search && (start == Arguments::bootclassloader_append_index())));
    char* path = NEW_RESOURCE_ARRAY(char, end - start + 1);
    strncpy(path, &class_path[start], end - start);
    path[end - start] = '\0';
    update_class_path_entry_list(path, false, mark_append_entry, false, bootstrap_search);

    // Check on the state of the boot loader's append path
    if (mark_append_entry && (_first_append_entry == NULL)) {
      // Failure to mark the first append entry, most likely
      // due to a non-existent path. Record the next entry
      // as the first boot loader append entry.
      mark_append_entry = true;
    } else {
      mark_append_entry = false;
    }

#if INCLUDE_CDS
    if (DumpSharedSpaces) {
      check_shared_classpath(path);
    }
#endif
    while (class_path[end] == os::path_separator()[0]) {
      end++;
    }
  }
}

ClassPathEntry* ClassLoader::create_class_path_entry(const char *path, const struct stat* st,
                                                     bool throw_exception,
                                                     bool is_boot_append, TRAPS) {
  JavaThread* thread = JavaThread::current();
  ClassPathEntry* new_entry = NULL;
  if ((st->st_mode & S_IFREG) == S_IFREG) {
    // Regular file, should be a zip or jimage file
    // Canonicalized filename
    char canonical_path[JVM_MAXPATHLEN];
    if (!get_canonical_path(path, canonical_path, JVM_MAXPATHLEN)) {
      // This matches the classic VM
      if (throw_exception) {
        THROW_MSG_(vmSymbols::java_io_IOException(), "Bad pathname", NULL);
      } else {
        return NULL;
      }
    }
    jint error;
    JImageFile* jimage =(*JImageOpen)(canonical_path, &error);
    if (jimage != NULL) {
      new_entry = new ClassPathImageEntry(jimage, canonical_path);
    } else {
      char* error_msg = NULL;
      jzfile* zip;
      {
        // enable call to C land
        ThreadToNativeFromVM ttn(thread);
        HandleMark hm(thread);
        zip = (*ZipOpen)(canonical_path, &error_msg);
      }
      if (zip != NULL && error_msg == NULL) {
        new_entry = new ClassPathZipEntry(zip, path, is_boot_append);
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
        // Don't complain about bad jar files added via -Xbootclasspath/a:.
        if (throw_exception && is_init_completed()) {
          THROW_MSG_(vmSymbols::java_lang_ClassNotFoundException(), msg, NULL);
        } else {
          return NULL;
        }
      }
    }
    log_info(classpath)("opened: %s", path);
    log_info(classload)("opened: %s", path);
  } else {
    // Directory
    new_entry = new ClassPathDirEntry(path);
    log_info(classload)("path: %s", path);
  }
  return new_entry;
}


// Create a class path zip entry for a given path (return NULL if not found
// or zip/JAR file cannot be opened)
ClassPathZipEntry* ClassLoader::create_class_path_zip_entry(const char *path, bool is_boot_append) {
  // check for a regular file
  struct stat st;
  if (os::stat(path, &st) == 0) {
    if ((st.st_mode & S_IFREG) == S_IFREG) {
      char canonical_path[JVM_MAXPATHLEN];
      if (get_canonical_path(path, canonical_path, JVM_MAXPATHLEN)) {
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
          return new ClassPathZipEntry(zip, canonical_path, is_boot_append);
        }
      }
    }
  }
  return NULL;
}

// The boot class loader must adhere to specfic visibility rules.
// Prior to loading a class in a named package, the package is checked
// to see if it is in a module defined to the boot loader. If the
// package is not in a module defined to the boot loader, the class
// must be loaded only in the boot loader's append path, which
// consists of [-Xbootclasspath/a]; [jvmti appended entries]
void ClassLoader::set_first_append_entry(ClassPathEntry *new_entry) {
  if (_first_append_entry == NULL) {
    _first_append_entry = new_entry;
  }
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
  _num_entries ++;
}

void ClassLoader::prepend_to_list(ClassPathEntry *new_entry) {
  if (new_entry != NULL) {
    if (_last_entry == NULL) {
      _first_entry = _last_entry = new_entry;
    } else {
      new_entry->set_next(_first_entry);
      _first_entry = new_entry;
    }
  }
  _num_entries ++;
}

void ClassLoader::add_to_list(const char *apath) {
  update_class_path_entry_list((char*)apath, false, false, false, false);
}

void ClassLoader::prepend_to_list(const char *apath) {
  update_class_path_entry_list((char*)apath, false, false, true, false);
}

// Returns true IFF the file/dir exists and the entry was successfully created.
bool ClassLoader::update_class_path_entry_list(const char *path,
                                               bool check_for_duplicates,
                                               bool mark_append_entry,
                                               bool prepend_entry,
                                               bool is_boot_append,
                                               bool throw_exception) {
  struct stat st;
  if (os::stat(path, &st) == 0) {
    // File or directory found
    ClassPathEntry* new_entry = NULL;
    Thread* THREAD = Thread::current();
    new_entry = create_class_path_entry(path, &st, throw_exception, is_boot_append, CHECK_(false));
    if (new_entry == NULL) {
      return false;
    }

    // Ensure that the first boot loader append entry will always be set correctly.
    assert((!mark_append_entry ||
            (mark_append_entry && (!check_for_duplicates || !contains_entry(new_entry)))),
           "failed to mark boot loader's first append boundary");

    // Do not reorder the bootclasspath which would break get_system_package().
    // Add new entry to linked list

    if (!check_for_duplicates || !contains_entry(new_entry)) {
      ClassLoaderExt::add_class_path_entry(path, check_for_duplicates, new_entry, prepend_entry);
      if (mark_append_entry) {
        set_first_append_entry(new_entry);
      }
    }
    return true;
  } else {
#if INCLUDE_CDS
    if (DumpSharedSpaces) {
      _shared_paths_misc_info->add_nonexist_path(path);
    }
#endif
    return false;
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
  void* handle = NULL;
  if (os::dll_build_name(path, sizeof(path), Arguments::get_dll_dir(), "zip")) {
    handle = os::dll_load(path, ebuf, sizeof ebuf);
  }
  if (handle == NULL) {
    vm_exit_during_initialization("Unable to load ZIP library", path);
  }
  // Lookup zip entry points
  ZipOpen      = CAST_TO_FN_PTR(ZipOpen_t, os::dll_lookup(handle, "ZIP_Open"));
  ZipClose     = CAST_TO_FN_PTR(ZipClose_t, os::dll_lookup(handle, "ZIP_Close"));
  FindEntry    = CAST_TO_FN_PTR(FindEntry_t, os::dll_lookup(handle, "ZIP_FindEntry"));
  ReadEntry    = CAST_TO_FN_PTR(ReadEntry_t, os::dll_lookup(handle, "ZIP_ReadEntry"));
  ReadMappedEntry = CAST_TO_FN_PTR(ReadMappedEntry_t, os::dll_lookup(handle, "ZIP_ReadMappedEntry"));
  GetNextEntry = CAST_TO_FN_PTR(GetNextEntry_t, os::dll_lookup(handle, "ZIP_GetNextEntry"));
  ZipInflateFully = CAST_TO_FN_PTR(ZipInflateFully_t, os::dll_lookup(handle, "ZIP_InflateFully"));
  Crc32        = CAST_TO_FN_PTR(Crc32_t, os::dll_lookup(handle, "ZIP_CRC32"));

  // ZIP_Close is not exported on Windows in JDK5.0 so don't abort if ZIP_Close is NULL
  if (ZipOpen == NULL || FindEntry == NULL || ReadEntry == NULL ||
      GetNextEntry == NULL || Crc32 == NULL) {
    vm_exit_during_initialization("Corrupted ZIP library", path);
  }

  if (ZipInflateFully == NULL) {
    vm_exit_during_initialization("Corrupted ZIP library ZIP_InflateFully missing", path);
  }

  // Lookup canonicalize entry in libjava.dll
  void *javalib_handle = os::native_java_library();
  CanonicalizeEntry = CAST_TO_FN_PTR(canonicalize_fn_t, os::dll_lookup(javalib_handle, "Canonicalize"));
  // This lookup only works on 1.3. Do not check for non-null here
}

void ClassLoader::load_jimage_library() {
  // First make sure native library is loaded
  os::native_java_library();
  // Load jimage library
  char path[JVM_MAXPATHLEN];
  char ebuf[1024];
  void* handle = NULL;
  if (os::dll_build_name(path, sizeof(path), Arguments::get_dll_dir(), "jimage")) {
    handle = os::dll_load(path, ebuf, sizeof ebuf);
  }
  if (handle == NULL) {
    vm_exit_during_initialization("Unable to load jimage library", path);
  }

  // Lookup jimage entry points
  JImageOpen = CAST_TO_FN_PTR(JImageOpen_t, os::dll_lookup(handle, "JIMAGE_Open"));
  guarantee(JImageOpen != NULL, "function JIMAGE_Open not found");
  JImageClose = CAST_TO_FN_PTR(JImageClose_t, os::dll_lookup(handle, "JIMAGE_Close"));
  guarantee(JImageClose != NULL, "function JIMAGE_Close not found");
  JImagePackageToModule = CAST_TO_FN_PTR(JImagePackageToModule_t, os::dll_lookup(handle, "JIMAGE_PackageToModule"));
  guarantee(JImagePackageToModule != NULL, "function JIMAGE_PackageToModule not found");
  JImageFindResource = CAST_TO_FN_PTR(JImageFindResource_t, os::dll_lookup(handle, "JIMAGE_FindResource"));
  guarantee(JImageFindResource != NULL, "function JIMAGE_FindResource not found");
  JImageGetResource = CAST_TO_FN_PTR(JImageGetResource_t, os::dll_lookup(handle, "JIMAGE_GetResource"));
  guarantee(JImageGetResource != NULL, "function JIMAGE_GetResource not found");
  JImageResourceIterator = CAST_TO_FN_PTR(JImageResourceIterator_t, os::dll_lookup(handle, "JIMAGE_ResourceIterator"));
  guarantee(JImageResourceIterator != NULL, "function JIMAGE_ResourceIterator not found");
}

jboolean ClassLoader::decompress(void *in, u8 inSize, void *out, u8 outSize, char **pmsg) {
  return (*ZipInflateFully)(in, inSize, out, outSize, pmsg);
}

int ClassLoader::crc32(int crc, const char* buf, int len) {
  assert(Crc32 != NULL, "ZIP_CRC32 is not found");
  return (*Crc32)(crc, (const jbyte*)buf, len);
}

#if INCLUDE_CDS
void ClassLoader::initialize_module_loader_map(JImageFile* jimage) {
  jlong size;
  JImageLocationRef location = (*JImageFindResource)(jimage, "java.base", get_jimage_version_string(), MODULE_LOADER_MAP, &size);
  if (location == 0) {
    vm_exit_during_initialization(
      "Cannot find ModuleLoaderMap location from modules jimage.", NULL);
  }
  char* buffer = NEW_RESOURCE_ARRAY(char, size);
  jlong read = (*JImageGetResource)(jimage, location, buffer, size);
  if (read != size) {
    vm_exit_during_initialization(
      "Cannot find ModuleLoaderMap resource from modules jimage.", NULL);
  }
  char* char_buf = (char*)buffer;
  int buflen = (int)strlen(char_buf);
  char* begin_ptr = char_buf;
  char* end_ptr = strchr(begin_ptr, '\n');
  bool process_boot_modules = false;
  _boot_modules_array = new (ResourceObj::C_HEAP, mtInternal)
    GrowableArray<char*>(INITIAL_BOOT_MODULES_ARRAY_SIZE, true);
  _platform_modules_array = new (ResourceObj::C_HEAP, mtInternal)
    GrowableArray<char*>(INITIAL_PLATFORM_MODULES_ARRAY_SIZE, true);
  while (end_ptr != NULL && (end_ptr - char_buf) < buflen) {
    // Allocate a buffer from the C heap to be appended to the _boot_modules_array
    // or the _platform_modules_array.
    char* temp_name = NEW_C_HEAP_ARRAY(char, (size_t)(end_ptr - begin_ptr + 1), mtInternal);
    strncpy(temp_name, begin_ptr, end_ptr - begin_ptr);
    temp_name[end_ptr - begin_ptr] = '\0';
    if (strncmp(temp_name, "BOOT", 4) == 0) {
      process_boot_modules = true;
      FREE_C_HEAP_ARRAY(char, temp_name);
    } else if (strncmp(temp_name, "PLATFORM", 8) == 0) {
      process_boot_modules = false;
      FREE_C_HEAP_ARRAY(char, temp_name);
    } else {
      // module name
      if (process_boot_modules) {
        _boot_modules_array->append(temp_name);
      } else {
        _platform_modules_array->append(temp_name);
      }
    }
    begin_ptr = ++end_ptr;
    end_ptr = strchr(begin_ptr, '\n');
  }
  FREE_RESOURCE_ARRAY(u1, buffer, size);
}
#endif

// Function add_package extracts the package from the fully qualified class name
// and checks if the package is in the boot loader's package entry table.  If so,
// then it sets the classpath_index in the package entry record.
//
// The classpath_index field is used to find the entry on the boot loader class
// path for packages with classes loaded by the boot loader from -Xbootclasspath/a
// in an unnamed module.  It is also used to indicate (for all packages whose
// classes are loaded by the boot loader) that at least one of the package's
// classes has been loaded.
bool ClassLoader::add_package(const char *fullq_class_name, s2 classpath_index, TRAPS) {
  assert(fullq_class_name != NULL, "just checking");

  // Get package name from fully qualified class name.
  const char *cp = strrchr(fullq_class_name, '/');
  if (cp != NULL) {
    int len = cp - fullq_class_name;
    PackageEntryTable* pkg_entry_tbl =
      ClassLoaderData::the_null_class_loader_data()->packages();
    TempNewSymbol pkg_symbol =
      SymbolTable::new_symbol(fullq_class_name, len, CHECK_false);
    PackageEntry* pkg_entry = pkg_entry_tbl->lookup_only(pkg_symbol);
    if (pkg_entry != NULL) {
      assert(classpath_index != -1, "Unexpected classpath_index");
      pkg_entry->set_classpath_index(classpath_index);
    } else {
      return false;
    }
  }
  return true;
}

oop ClassLoader::get_system_package(const char* name, TRAPS) {
  // Look up the name in the boot loader's package entry table.
  if (name != NULL) {
    TempNewSymbol package_sym = SymbolTable::new_symbol(name, (int)strlen(name), CHECK_NULL);
    // Look for the package entry in the boot loader's package entry table.
    PackageEntry* package =
      ClassLoaderData::the_null_class_loader_data()->packages()->lookup_only(package_sym);

    // Return NULL if package does not exist or if no classes in that package
    // have been loaded.
    if (package != NULL && package->has_loaded_class()) {
      ModuleEntry* module = package->module();
      if (module->location() != NULL) {
        ResourceMark rm(THREAD);
        Handle ml = java_lang_String::create_from_str(
          module->location()->as_C_string(), THREAD);
        return ml();
      }
      // Return entry on boot loader class path.
      Handle cph = java_lang_String::create_from_str(
        ClassLoader::classpath_entry(package->classpath_index())->name(), THREAD);
      return cph();
    }
  }
  return NULL;
}

objArrayOop ClassLoader::get_system_packages(TRAPS) {
  ResourceMark rm(THREAD);
  // List of pointers to PackageEntrys that have loaded classes.
  GrowableArray<PackageEntry*>* loaded_class_pkgs = new GrowableArray<PackageEntry*>(50);
  {
    MutexLocker ml(Module_lock, THREAD);

    PackageEntryTable* pe_table =
      ClassLoaderData::the_null_class_loader_data()->packages();

    // Collect the packages that have at least one loaded class.
    for (int x = 0; x < pe_table->table_size(); x++) {
      for (PackageEntry* package_entry = pe_table->bucket(x);
           package_entry != NULL;
           package_entry = package_entry->next()) {
        if (package_entry->has_loaded_class()) {
          loaded_class_pkgs->append(package_entry);
        }
      }
    }
  }


  // Allocate objArray and fill with java.lang.String
  objArrayOop r = oopFactory::new_objArray(SystemDictionary::String_klass(),
                                           loaded_class_pkgs->length(), CHECK_NULL);
  objArrayHandle result(THREAD, r);
  for (int x = 0; x < loaded_class_pkgs->length(); x++) {
    PackageEntry* package_entry = loaded_class_pkgs->at(x);
    Handle str = java_lang_String::create_from_symbol(package_entry->name(), CHECK_NULL);
    result->obj_at_put(x, str());
  }
  return result();
}

#if INCLUDE_CDS
s2 ClassLoader::module_to_classloader(const char* module_name) {

  assert(_boot_modules_array != NULL, "_boot_modules_array is NULL");
  assert(_platform_modules_array != NULL, "_platform_modules_array is NULL");

  int array_size = _boot_modules_array->length();
  for (int i = 0; i < array_size; i++) {
    if (strcmp(module_name, _boot_modules_array->at(i)) == 0) {
      return BOOT_LOADER;
    }
  }

  array_size = _platform_modules_array->length();
  for (int i = 0; i < array_size; i++) {
    if (strcmp(module_name, _platform_modules_array->at(i)) == 0) {
      return PLATFORM_LOADER;
    }
  }

  return APP_LOADER;
}
#endif

s2 ClassLoader::classloader_type(Symbol* class_name, ClassPathEntry* e,
                                     int classpath_index, TRAPS) {
#if INCLUDE_CDS
  // obtain the classloader type based on the class name.
  // First obtain the package name based on the class name. Then obtain
  // the classloader type based on the package name from the jimage using
  // a jimage API. If the classloader type cannot be found from the
  // jimage, it is determined by the class path entry.
  jshort loader_type = ClassLoader::APP_LOADER;
  if (e->is_jrt()) {
    int length = 0;
    const jbyte* pkg_string = InstanceKlass::package_from_name(class_name, length);
    if (pkg_string != NULL) {
      ResourceMark rm;
      TempNewSymbol pkg_name = SymbolTable::new_symbol((const char*)pkg_string, length, THREAD);
      const char* pkg_name_C_string = (const char*)(pkg_name->as_C_string());
      ClassPathImageEntry* cpie = (ClassPathImageEntry*)e;
      JImageFile* jimage = cpie->jimage();
      char* module_name = (char*)(*JImagePackageToModule)(jimage, pkg_name_C_string);
      if (module_name != NULL) {
        loader_type = ClassLoader::module_to_classloader(module_name);
      }
    }
  } else if (ClassLoaderExt::is_boot_classpath(classpath_index)) {
    loader_type = ClassLoader::BOOT_LOADER;
  }
  return loader_type;
#endif
  return ClassLoader::BOOT_LOADER; // the classloader type is ignored in non-CDS cases
}


// caller needs ResourceMark
const char* ClassLoader::file_name_for_class_name(const char* class_name,
                                                  int class_name_len) {
  assert(class_name != NULL, "invariant");
  assert((int)strlen(class_name) == class_name_len, "invariant");

  static const char class_suffix[] = ".class";

  char* const file_name = NEW_RESOURCE_ARRAY(char,
                                             class_name_len +
                                             sizeof(class_suffix)); // includes term NULL

  strncpy(file_name, class_name, class_name_len);
  strncpy(&file_name[class_name_len], class_suffix, sizeof(class_suffix));

  return file_name;
}

instanceKlassHandle ClassLoader::load_class(Symbol* name, bool search_append_only, TRAPS) {

  assert(name != NULL, "invariant");
  assert(THREAD->is_Java_thread(), "must be a JavaThread");

  ResourceMark rm;
  HandleMark hm;

  const char* const class_name = name->as_C_string();

  EventMark m("loading class %s", class_name);
  ThreadProfilerMark tpm(ThreadProfilerMark::classLoaderRegion);

  const char* const file_name = file_name_for_class_name(class_name,
                                                         name->utf8_length());
  assert(file_name != NULL, "invariant");

  ClassLoaderExt::Context context(class_name, file_name, THREAD);

  // Lookup stream for parsing .class file
  ClassFileStream* stream = NULL;
  s2 classpath_index = 0;

  // If DumpSharedSpaces is true, boot loader visibility boundaries are set
  // to be _first_entry to the end (all path entries).
  //
  // If search_append_only is true, boot loader visibility boundaries are
  // set to be _fist_append_entry to the end. This includes:
  //   [-Xbootclasspath/a]; [jvmti appended entries]
  //
  // If both DumpSharedSpaces and search_append_only are false, boot loader
  // visibility boundaries are set to be _first_entry to the entry before
  // the _first_append_entry.  This would include:
  //   [-Xpatch:<dirs>];  [exploded build | modules]
  //
  // DumpSharedSpaces and search_append_only are mutually exclusive and cannot
  // be true at the same time.
  ClassPathEntry* e = (search_append_only ? _first_append_entry : _first_entry);
  ClassPathEntry* last_e =
      (search_append_only || DumpSharedSpaces ? NULL : _first_append_entry);

  {
    if (search_append_only) {
      // For the boot loader append path search, must calculate
      // the starting classpath_index prior to attempting to
      // load the classfile.
      ClassPathEntry *tmp_e = _first_entry;
      while ((tmp_e != NULL) && (tmp_e != _first_append_entry)) {
        tmp_e = tmp_e->next();
        ++classpath_index;
      }
    }

    // Attempt to load the classfile from either:
    //   - [-Xpatch:dir]; exploded build | modules
    //     or
    //   - [-Xbootclasspath/a]; [jvmti appended entries]
    while ((e != NULL) && (e != last_e)) {
      stream = e->open_stream(file_name, CHECK_NULL);
      if (!context.check(stream, classpath_index)) {
        return NULL;
      }
      if (NULL != stream) {
        break;
      }
      e = e->next();
      ++classpath_index;
    }
  }

  if (NULL == stream) {
    if (DumpSharedSpaces) {
      tty->print_cr("Preload Warning: Cannot find %s", class_name);
    }
    return NULL;
  }

  stream->set_verify(context.should_verify(classpath_index));

  ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
  Handle protection_domain;

  instanceKlassHandle result = KlassFactory::create_from_stream(stream,
                                                                name,
                                                                loader_data,
                                                                protection_domain,
                                                                NULL, // host_klass
                                                                NULL, // cp_patches
                                                                NULL, // parsed_name
                                                                THREAD);
  if (HAS_PENDING_EXCEPTION) {
    if (DumpSharedSpaces) {
      tty->print_cr("Preload Error: Failed to load %s", class_name);
    }
    return NULL;
  }

  jshort loader_type = classloader_type(name, e, classpath_index, CHECK_NULL);
  return context.record_result(classpath_index, loader_type, e, result, THREAD);
}

// Initialize the class loader's access to methods in libzip.  Parse and
// process the boot classpath into a list ClassPathEntry objects.  Once
// this list has been created, it must not change order (see class PackageInfo)
// it can be appended to and is by jvmti and the kernel vm.

void ClassLoader::initialize() {
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
  // lookup jimage library entry points
  load_jimage_library();
#if INCLUDE_CDS
  // initialize search path
  if (DumpSharedSpaces) {
    _shared_paths_misc_info = SharedClassUtil::allocate_shared_paths_misc_info();
  }
#endif
  setup_bootstrap_search_path();
}

#if INCLUDE_CDS
void ClassLoader::initialize_shared_path() {
  if (DumpSharedSpaces) {
    ClassLoaderExt::setup_search_paths();
    _shared_paths_misc_info->write_jint(0); // see comments in SharedPathsMiscInfo::check()
  }
}
#endif

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


bool ClassLoader::get_canonical_path(const char* orig, char* out, int len) {
  assert(orig != NULL && out != NULL && len > 0, "bad arguments");
  if (CanonicalizeEntry != NULL) {
    JavaThread* THREAD = JavaThread::current();
    JNIEnv* env = THREAD->jni_environment();
    ResourceMark rm(THREAD);

    // os::native_path writes into orig_copy
    char* orig_copy = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, strlen(orig)+1);
    strcpy(orig_copy, orig);
    if ((CanonicalizeEntry)(env, os::native_path(orig_copy), out, len) < 0) {
      return false;
    }
  } else {
    // On JDK 1.2.2 the Canonicalize does not exist, so just do nothing
    strncpy(out, orig, len);
    out[len - 1] = '\0';
  }
  return true;
}

void ClassLoader::create_javabase() {
  Thread* THREAD = Thread::current();

  // Create java.base's module entry for the boot
  // class loader prior to loading j.l.Ojbect.
  ClassLoaderData* null_cld = ClassLoaderData::the_null_class_loader_data();

  // Get module entry table
  ModuleEntryTable* null_cld_modules = null_cld->modules();
  if (null_cld_modules == NULL) {
    vm_exit_during_initialization("No ModuleEntryTable for the boot class loader");
  }

  {
    MutexLocker ml(Module_lock, THREAD);
    ModuleEntry* jb_module = null_cld_modules->locked_create_entry_or_null(Handle(NULL), vmSymbols::java_base(), NULL, NULL, null_cld);
    if (jb_module == NULL) {
      vm_exit_during_initialization("Unable to create ModuleEntry for java.base");
    }
    ModuleEntryTable::set_javabase_module(jb_module);
  }

  // When looking for the jimage file, only
  // search the boot loader's module path which
  // can consist of [-Xpatch]; exploded build | modules
  // Do not search the boot loader's append path.
  ClassPathEntry* e = _first_entry;
  ClassPathEntry* last_e = _first_append_entry;
  while ((e != NULL) && (e != last_e)) {
    JImageFile *jimage = e->jimage();
    if (jimage != NULL && e->is_jrt()) {
      set_has_jimage(true);
#if INCLUDE_CDS
      ClassLoader::initialize_module_loader_map(jimage);
#endif
      return;
    }
    e = e->next();
  }
}

#ifndef PRODUCT

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
typedef struct real_jzentry {         /* Zip file entry */
    char *name;                 /* entry name */
    jint time;                  /* modification time */
    jint size;                  /* size of uncompressed data */
    jint csize;                 /* size of compressed data (zero if uncompressed) */
    jint crc;                   /* crc of uncompressed data */
    char *comment;              /* optional zip file comment */
    jbyte *extra;               /* optional extra data */
    jint pos;                   /* position of LOC header (if negative) or data */
} real_jzentry;

typedef struct real_jzfile {  /* Zip file */
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
    real_jzfile *next;        /* next zip file in search list */
    jzentry *cache;             /* we cache the most recently freed jzentry */
    /* Information on metadata names in META-INF directory */
    char **metanames;           /* array of meta names (may have null names) */
    jint metacount;             /* number of slots in metanames array */
    /* If there are any per-entry comments, they are in the comments array */
    char **comments;
} real_jzfile;

void ClassPathDirEntry::compile_the_world(Handle loader, TRAPS) {
  // For now we only compile all methods in all classes in zip/jar files
  tty->print_cr("CompileTheWorld : Skipped classes in %s", _dir);
  tty->cr();
}

void ClassPathZipEntry::compile_the_world(Handle loader, TRAPS) {
  real_jzfile* zip = (real_jzfile*) _zip;
  tty->print_cr("CompileTheWorld : Compiling all classes in %s", zip->name);
  tty->cr();
  // Iterate over all entries in zip file
  for (int n = 0; ; n++) {
    real_jzentry * ze = (real_jzentry *)((*GetNextEntry)(_zip, n));
    if (ze == NULL) break;
    ClassLoader::compile_the_world_in(ze->name, loader, CHECK);
  }
  if (HAS_PENDING_EXCEPTION) {
    if (PENDING_EXCEPTION->is_a(SystemDictionary::OutOfMemoryError_klass())) {
      CLEAR_PENDING_EXCEPTION;
      tty->print_cr("\nCompileTheWorld : Ran out of memory\n");
      tty->print_cr("Increase class metadata storage if a limit was set");
    } else {
      tty->print_cr("\nCompileTheWorld : Unexpected exception occurred\n");
    }
  }
}

void ClassLoader::compile_the_world() {
  EXCEPTION_MARK;
  HandleMark hm(THREAD);
  ResourceMark rm(THREAD);

  // Find bootstrap loader
  Handle system_class_loader (THREAD, SystemDictionary::java_system_loader());
  // Iterate over all bootstrap class path entries
  ClassPathEntry* e = _first_entry;
  jlong start = os::javaTimeMillis();
  while (e != NULL) {
    // We stop at "modules" jimage, unless it is the first bootstrap path entry
    if (e->is_jrt() && e != _first_entry) break;
    e->compile_the_world(system_class_loader, CATCH);
    e = e->next();
  }
  jlong end = os::javaTimeMillis();
  tty->print_cr("CompileTheWorld : Done (%d classes, %d methods, " JLONG_FORMAT " ms)",
                _compile_the_world_class_counter, _compile_the_world_method_counter, (end - start));
  {
    // Print statistics as if before normal exit:
    extern void print_statistics();
    print_statistics();
  }
  vm_exit(0);
}

int ClassLoader::_compile_the_world_class_counter = 0;
int ClassLoader::_compile_the_world_method_counter = 0;
static int _codecache_sweep_counter = 0;

// Filter out all exceptions except OOMs
static void clear_pending_exception_if_not_oom(TRAPS) {
  if (HAS_PENDING_EXCEPTION &&
      !PENDING_EXCEPTION->is_a(SystemDictionary::OutOfMemoryError_klass())) {
    CLEAR_PENDING_EXCEPTION;
  }
  // The CHECK at the caller will propagate the exception out
}

/**
 * Returns if the given method should be compiled when doing compile-the-world.
 *
 * TODO:  This should be a private method in a CompileTheWorld class.
 */
static bool can_be_compiled(methodHandle m, int comp_level) {
  assert(CompileTheWorld, "must be");

  // It's not valid to compile a native wrapper for MethodHandle methods
  // that take a MemberName appendix since the bytecode signature is not
  // correct.
  vmIntrinsics::ID iid = m->intrinsic_id();
  if (MethodHandles::is_signature_polymorphic(iid) && MethodHandles::has_member_arg(iid)) {
    return false;
  }

  return CompilationPolicy::can_be_compiled(m, comp_level);
}

void ClassLoader::compile_the_world_in(char* name, Handle loader, TRAPS) {
  if (string_ends_with(name, ".class")) {
    // We have a .class file
    int len = (int)strlen(name);
    char buffer[2048];
    strncpy(buffer, name, len - 6);
    buffer[len-6] = 0;
    // If the file has a period after removing .class, it's not really a
    // valid class file.  The class loader will check everything else.
    if (strchr(buffer, '.') == NULL) {
      _compile_the_world_class_counter++;
      if (_compile_the_world_class_counter > CompileTheWorldStopAt) return;

      // Construct name without extension
      TempNewSymbol sym = SymbolTable::new_symbol(buffer, CHECK);
      // Use loader to load and initialize class
      Klass* ik = SystemDictionary::resolve_or_null(sym, loader, Handle(), THREAD);
      instanceKlassHandle k (THREAD, ik);
      if (k.not_null() && !HAS_PENDING_EXCEPTION) {
        k->initialize(THREAD);
      }
      bool exception_occurred = HAS_PENDING_EXCEPTION;
      clear_pending_exception_if_not_oom(CHECK);
      if (CompileTheWorldPreloadClasses && k.not_null()) {
        ConstantPool::preload_and_initialize_all_classes(k->constants(), THREAD);
        if (HAS_PENDING_EXCEPTION) {
          // If something went wrong in preloading we just ignore it
          clear_pending_exception_if_not_oom(CHECK);
          tty->print_cr("Preloading failed for (%d) %s", _compile_the_world_class_counter, buffer);
        }
      }

      if (_compile_the_world_class_counter >= CompileTheWorldStartAt) {
        if (k.is_null() || exception_occurred) {
          // If something went wrong (e.g. ExceptionInInitializerError) we skip this class
          tty->print_cr("CompileTheWorld (%d) : Skipping %s", _compile_the_world_class_counter, buffer);
        } else {
          tty->print_cr("CompileTheWorld (%d) : %s", _compile_the_world_class_counter, buffer);
          // Preload all classes to get around uncommon traps
          // Iterate over all methods in class
          int comp_level = CompilationPolicy::policy()->initial_compile_level();
          for (int n = 0; n < k->methods()->length(); n++) {
            methodHandle m (THREAD, k->methods()->at(n));
            if (can_be_compiled(m, comp_level)) {
              if (++_codecache_sweep_counter == CompileTheWorldSafepointInterval) {
                // Give sweeper a chance to keep up with CTW
                VM_ForceSafepoint op;
                VMThread::execute(&op);
                _codecache_sweep_counter = 0;
              }
              // Force compilation
              CompileBroker::compile_method(m, InvocationEntryBci, comp_level,
                                            methodHandle(), 0, "CTW", THREAD);
              if (HAS_PENDING_EXCEPTION) {
                clear_pending_exception_if_not_oom(CHECK);
                tty->print_cr("CompileTheWorld (%d) : Skipping method: %s", _compile_the_world_class_counter, m->name_and_sig_as_C_string());
              } else {
                _compile_the_world_method_counter++;
              }
              if (TieredCompilation && TieredStopAtLevel >= CompLevel_full_optimization) {
                // Clobber the first compile and force second tier compilation
                nmethod* nm = m->code();
                if (nm != NULL && !m->is_method_handle_intrinsic()) {
                  // Throw out the code so that the code cache doesn't fill up
                  nm->make_not_entrant();
                  m->clear_code();
                }
                CompileBroker::compile_method(m, InvocationEntryBci, CompLevel_full_optimization,
                                              methodHandle(), 0, "CTW", THREAD);
                if (HAS_PENDING_EXCEPTION) {
                  clear_pending_exception_if_not_oom(CHECK);
                  tty->print_cr("CompileTheWorld (%d) : Skipping method: %s", _compile_the_world_class_counter, m->name_and_sig_as_C_string());
                } else {
                  _compile_the_world_method_counter++;
                }
              }
            } else {
              tty->print_cr("CompileTheWorld (%d) : Skipping method: %s", _compile_the_world_class_counter, m->name_and_sig_as_C_string());
            }

            nmethod* nm = m->code();
            if (nm != NULL && !m->is_method_handle_intrinsic()) {
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

// Please keep following two functions at end of this file. With them placed at top or in middle of the file,
// they could get inlined by agressive compiler, an unknown trick, see bug 6966589.
void PerfClassTraceTime::initialize() {
  if (!UsePerfData) return;

  if (_eventp != NULL) {
    // increment the event counter
    _eventp->inc();
  }

  // stop the current active thread-local timer to measure inclusive time
  _prev_active_event = -1;
  for (int i=0; i < EVENT_TYPE_COUNT; i++) {
     if (_timers[i].is_active()) {
       assert(_prev_active_event == -1, "should have only one active timer");
       _prev_active_event = i;
       _timers[i].stop();
     }
  }

  if (_recursion_counters == NULL || (_recursion_counters[_event_type])++ == 0) {
    // start the inclusive timer if not recursively called
    _t.start();
  }

  // start thread-local timer of the given event type
   if (!_timers[_event_type].is_active()) {
    _timers[_event_type].start();
  }
}

PerfClassTraceTime::~PerfClassTraceTime() {
  if (!UsePerfData) return;

  // stop the thread-local timer as the event completes
  // and resume the thread-local timer of the event next on the stack
  _timers[_event_type].stop();
  jlong selftime = _timers[_event_type].ticks();

  if (_prev_active_event >= 0) {
    _timers[_prev_active_event].start();
  }

  if (_recursion_counters != NULL && --(_recursion_counters[_event_type]) > 0) return;

  // increment the counters only on the leaf call
  _t.stop();
  _timep->inc(_t.ticks());
  if (_selftimep != NULL) {
    _selftimep->inc(selftime);
  }
  // add all class loading related event selftime to the accumulated time counter
  ClassLoader::perf_accumulated_time()->inc(selftime);

  // reset the timer
  _timers[_event_type].reset();
}
