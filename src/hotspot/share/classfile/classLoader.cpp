/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "jimage.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.inline.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/modules.hpp"
#include "classfile/packageEntry.hpp"
#include "classfile/klassFactory.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "compiler/compileBroker.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/oopMapCache.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "logging/logTag.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/filemap.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/method.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "prims/jvm_misc.hpp"
#include "runtime/arguments.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/handles.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/threadCritical.hpp"
#include "runtime/timer.hpp"
#include "runtime/vm_version.hpp"
#include "services/management.hpp"
#include "services/threadService.hpp"
#include "utilities/events.hpp"
#include "utilities/hashtable.inline.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_CDS
#include "classfile/sharedPathsMiscInfo.hpp"
#endif

// Entry points in zip.dll for loading zip/jar file entries

typedef void * * (*ZipOpen_t)(const char *name, char **pmsg);
typedef void (*ZipClose_t)(jzfile *zip);
typedef jzentry* (*FindEntry_t)(jzfile *zip, const char *name, jint *sizeP, jint *nameLen);
typedef jboolean (*ReadEntry_t)(jzfile *zip, jzentry *entry, unsigned char *buf, char *namebuf);
typedef jzentry* (*GetNextEntry_t)(jzfile *zip, jint n);
typedef jboolean (*ZipInflateFully_t)(void *inBuf, jlong inLen, void *outBuf, jlong outLen, char **pmsg);
typedef jint     (*Crc32_t)(jint crc, const jbyte *buf, jint len);

static ZipOpen_t         ZipOpen            = NULL;
static ZipClose_t        ZipClose           = NULL;
static FindEntry_t       FindEntry          = NULL;
static ReadEntry_t       ReadEntry          = NULL;
static GetNextEntry_t    GetNextEntry       = NULL;
static canonicalize_fn_t CanonicalizeEntry  = NULL;
static ZipInflateFully_t ZipInflateFully    = NULL;
static Crc32_t           Crc32              = NULL;

// Entry points for jimage.dll for loading jimage file entries

static JImageOpen_t                    JImageOpen             = NULL;
static JImageClose_t                   JImageClose            = NULL;
static JImagePackageToModule_t         JImagePackageToModule  = NULL;
static JImageFindResource_t            JImageFindResource     = NULL;
static JImageGetResource_t             JImageGetResource      = NULL;
static JImageResourceIterator_t        JImageResourceIterator = NULL;
static JImage_ResourcePath_t           JImageResourcePath     = NULL;

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
PerfCounter*    ClassLoader::_load_instance_class_failCounter = NULL;

GrowableArray<ModuleClassPathList*>* ClassLoader::_patch_mod_entries = NULL;
GrowableArray<ModuleClassPathList*>* ClassLoader::_exploded_entries = NULL;
ClassPathEntry* ClassLoader::_jrt_entry = NULL;
ClassPathEntry* ClassLoader::_first_append_entry = NULL;
ClassPathEntry* ClassLoader::_last_append_entry  = NULL;
#if INCLUDE_CDS
ClassPathEntry* ClassLoader::_app_classpath_entries = NULL;
ClassPathEntry* ClassLoader::_last_app_classpath_entry = NULL;
ClassPathEntry* ClassLoader::_module_path_entries = NULL;
ClassPathEntry* ClassLoader::_last_module_path_entry = NULL;
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

// Used to obtain the package name from a fully qualified class name.
// It is the responsibility of the caller to establish a ResourceMark.
const char* ClassLoader::package_from_name(const char* const class_name, bool* bad_class_name) {
  if (class_name == NULL) {
    if (bad_class_name != NULL) {
      *bad_class_name = true;
    }
    return NULL;
  }

  if (bad_class_name != NULL) {
    *bad_class_name = false;
  }

  const char* const last_slash = strrchr(class_name, '/');
  if (last_slash == NULL) {
    // No package name
    return NULL;
  }

  char* class_name_ptr = (char*) class_name;
  // Skip over '['s
  if (*class_name_ptr == '[') {
    do {
      class_name_ptr++;
    } while (*class_name_ptr == '[');

    // Fully qualified class names should not contain a 'L'.
    // Set bad_class_name to true to indicate that the package name
    // could not be obtained due to an error condition.
    // In this situation, is_same_class_package returns false.
    if (*class_name_ptr == 'L') {
      if (bad_class_name != NULL) {
        *bad_class_name = true;
      }
      return NULL;
    }
  }

  int length = last_slash - class_name_ptr;

  // A class name could have just the slash character in the name.
  if (length <= 0) {
    // No package name
    if (bad_class_name != NULL) {
      *bad_class_name = true;
    }
    return NULL;
  }

  // drop name after last slash (including slash)
  // Ex., "java/lang/String.class" => "java/lang"
  char* pkg_name = NEW_RESOURCE_ARRAY(char, length + 1);
  strncpy(pkg_name, class_name_ptr, length);
  *(pkg_name+length) = '\0';

  return (const char *)pkg_name;
}

// Given a fully qualified class name, find its defining package in the class loader's
// package entry table.
PackageEntry* ClassLoader::get_package_entry(const char* class_name, ClassLoaderData* loader_data, TRAPS) {
  ResourceMark rm(THREAD);
  const char *pkg_name = ClassLoader::package_from_name(class_name);
  if (pkg_name == NULL) {
    return NULL;
  }
  PackageEntryTable* pkgEntryTable = loader_data->packages();
  TempNewSymbol pkg_symbol = SymbolTable::new_symbol(pkg_name, CHECK_NULL);
  return pkgEntryTable->lookup_only(pkg_symbol);
}

ClassPathDirEntry::ClassPathDirEntry(const char* dir) : ClassPathEntry() {
  char* copy = NEW_C_HEAP_ARRAY(char, strlen(dir)+1, mtClass);
  strcpy(copy, dir);
  _dir = copy;
}


ClassFileStream* ClassPathDirEntry::open_stream(const char* name, TRAPS) {
  // construct full path name
  assert((_dir != NULL) && (name != NULL), "sanity");
  size_t path_len = strlen(_dir) + strlen(name) + strlen(os::file_separator()) + 1;
  char* path = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, path_len);
  int len = jio_snprintf(path, path_len, "%s%s%s", _dir, os::file_separator(), name);
  assert(len == (int)(path_len - 1), "sanity");
  // check if file exists
  struct stat st;
  if (os::stat(path, &st) == 0) {
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
        FREE_RESOURCE_ARRAY(char, path, path_len);
        // Resource allocated
        return new ClassFileStream(buffer,
                                   st.st_size,
                                   _dir,
                                   ClassFileStream::verify);
      }
    }
  }
  FREE_RESOURCE_ARRAY(char, path, path_len);
  return NULL;
}

ClassPathZipEntry::ClassPathZipEntry(jzfile* zip, const char* zip_name, bool is_boot_append) : ClassPathEntry() {
  _zip = zip;
  char *copy = NEW_C_HEAP_ARRAY(char, strlen(zip_name)+1, mtClass);
  strcpy(copy, zip_name);
  _zip_name = copy;
  _is_boot_append = is_boot_append;
  _multi_versioned = _unknown;
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

  // read contents into resource array
  int size = (*filesize) + ((nul_terminate) ? 1 : 0);
  buffer = NEW_RESOURCE_ARRAY(u1, size);
  if (!(*ReadEntry)(_zip, entry, buffer, filename)) return NULL;

  // return result
  if (nul_terminate) {
    buffer[*filesize] = 0;
  }
  return buffer;
}

#if INCLUDE_CDS
u1* ClassPathZipEntry::open_versioned_entry(const char* name, jint* filesize, TRAPS) {
  u1* buffer = NULL;
  if (DumpSharedSpaces && !_is_boot_append) {
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
        // If the specified version is lower than the base version, the base
        // entry will be used; if the version is higher than the current
        // jdk version, the highest versioned entry will be used.
        if (version < base_version) {
          is_multi_ver = false;
        }
        // print out warning, do not use assertion here since it will continue to look
        // for proper version.
        warning("JDK%d is not supported in multiple version jars", version);
      }
    }

    if (is_multi_ver) {
      int n;
      const char* version_entry = "META-INF/versions/";
      // 10 is the max length of a decimal 32-bit non-negative number
      // 2 includes the '/' and trailing zero
      size_t entry_name_len = strlen(version_entry) + 10 + strlen(name) + 2;
      char* entry_name = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, entry_name_len);
      if (version > 0) {
        n = jio_snprintf(entry_name, entry_name_len, "%s%d/%s", version_entry, version, name);
        entry_name[n] = '\0';
        buffer = open_entry((const char*)entry_name, filesize, false, CHECK_NULL);
        if (buffer == NULL) {
          warning("Could not find %s in %s, try to find highest version instead", entry_name, _zip_name);
        }
      }
      if (buffer == NULL) {
        for (int i = cur_ver; i >= base_version; i--) {
          n = jio_snprintf(entry_name, entry_name_len, "%s%d/%s", version_entry, i, name);
          entry_name[n] = '\0';
          buffer = open_entry((const char*)entry_name, filesize, false, CHECK_NULL);
          if (buffer != NULL) {
            break;
          }
        }
      }
      FREE_RESOURCE_ARRAY(char, entry_name, entry_name_len);
    }
  }
  return buffer;
}

bool ClassPathZipEntry::is_multiple_versioned(TRAPS) {
  assert(DumpSharedSpaces, "called only at dump time");
  if (_multi_versioned != _unknown) {
    return (_multi_versioned == _yes) ? true : false;
  }
  jint size;
  char* buffer = (char*)open_entry("META-INF/MANIFEST.MF", &size, true, CHECK_false);
  if (buffer != NULL) {
    char* p = buffer;
    for ( ; *p; ++p) *p = tolower(*p);
    if (strstr(buffer, "multi-release: true") != NULL) {
      _multi_versioned = _yes;
      return true;
    }
  }
  _multi_versioned = _no;
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
    ResourceMark rm;
    const char* pkg_name = ClassLoader::package_from_name(name);

    if (pkg_name != NULL) {
      if (!Universe::is_module_initialized()) {
        location = (*JImageFindResource)(_jimage, JAVA_BASE_NAME, get_jimage_version_string(), name, &size);
#if INCLUDE_CDS
        // CDS uses the boot class loader to load classes whose packages are in
        // modules defined for other class loaders.  So, for now, get their module
        // names from the "modules" jimage file.
        if (DumpSharedSpaces && location == 0) {
          const char* module_name = (*JImagePackageToModule)(_jimage, pkg_name);
          if (module_name != NULL) {
            location = (*JImageFindResource)(_jimage, module_name, get_jimage_version_string(), name, &size);
          }
        }
#endif

      } else {
        PackageEntry* package_entry = ClassLoader::get_package_entry(name, ClassLoaderData::the_null_class_loader_data(), CHECK_NULL);
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

JImageLocationRef ClassLoader::jimage_find_resource(JImageFile* jf,
                                                    const char* module_name,
                                                    const char* file_name,
                                                    jlong &size) {
  return ((*JImageFindResource)(jf, module_name, get_jimage_version_string(), file_name, &size));
}

#ifndef PRODUCT
bool ctw_visitor(JImageFile* jimage,
        const char* module_name, const char* version, const char* package,
        const char* name, const char* extension, void* arg) {
  if (strcmp(extension, "class") == 0) {
    Thread* THREAD = Thread::current();
    ResourceMark rm(THREAD);
    char* path = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, JIMAGE_MAX_PATH);
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

bool ClassPathImageEntry::is_modules_image() const {
  return ClassLoader::is_modules_image(name());
}

#if INCLUDE_CDS
void ClassLoader::exit_with_path_failure(const char* error, const char* message) {
  assert(DumpSharedSpaces, "only called at dump time");
  tty->print_cr("Hint: enable -Xlog:class+path=info to diagnose the failure");
  vm_exit_during_initialization(error, message);
}
#endif

ModuleClassPathList::ModuleClassPathList(Symbol* module_name) {
  _module_name = module_name;
  _module_first_entry = NULL;
  _module_last_entry = NULL;
}

ModuleClassPathList::~ModuleClassPathList() {
  // Clean out each ClassPathEntry on list
  ClassPathEntry* e = _module_first_entry;
  while (e != NULL) {
    ClassPathEntry* next_entry = e->next();
    delete e;
    e = next_entry;
  }
}

void ModuleClassPathList::add_to_list(ClassPathEntry* new_entry) {
  if (new_entry != NULL) {
    if (_module_last_entry == NULL) {
      _module_first_entry = _module_last_entry = new_entry;
    } else {
      _module_last_entry->set_next(new_entry);
      _module_last_entry = new_entry;
    }
  }
}

void ClassLoader::trace_class_path(const char* msg, const char* name) {
  LogTarget(Info, class, path) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    if (msg) {
      ls.print("%s", msg);
    }
    if (name) {
      if (strlen(name) < 256) {
        ls.print("%s", name);
      } else {
        // For very long paths, we need to print each character separately,
        // as print_cr() has a length limit
        while (name[0] != '\0') {
          ls.print("%c", name[0]);
          name++;
        }
      }
    }
    ls.cr();
  }
}

void ClassLoader::setup_bootstrap_search_path() {
  const char* sys_class_path = Arguments::get_sysclasspath();
  if (PrintSharedArchiveAndExit) {
    // Don't print sys_class_path - this is the bootcp of this current VM process, not necessarily
    // the same as the bootcp of the shared archive.
  } else {
    trace_class_path("bootstrap loader class path=", sys_class_path);
  }
#if INCLUDE_CDS
  if (DumpSharedSpaces) {
    _shared_paths_misc_info->add_boot_classpath(sys_class_path);
  }
#endif
  setup_boot_search_path(sys_class_path);
}

#if INCLUDE_CDS
int ClassLoader::get_shared_paths_misc_info_size() {
  return _shared_paths_misc_info->get_used_bytes();
}

void* ClassLoader::get_shared_paths_misc_info() {
  return _shared_paths_misc_info->buffer();
}

bool ClassLoader::check_shared_paths_misc_info(void *buf, int size) {
  SharedPathsMiscInfo* checker = new SharedPathsMiscInfo((char*)buf, size);
  bool result = checker->check();
  delete checker;
  return result;
}

void ClassLoader::setup_app_search_path(const char *class_path) {

  assert(DumpSharedSpaces, "Sanity");

  Thread* THREAD = Thread::current();
  int len = (int)strlen(class_path);
  int end = 0;

  // Iterate over class path entries
  for (int start = 0; start < len; start = end) {
    while (class_path[end] && class_path[end] != os::path_separator()[0]) {
      end++;
    }
    EXCEPTION_MARK;
    ResourceMark rm(THREAD);
    char* path = NEW_RESOURCE_ARRAY(char, end - start + 1);
    strncpy(path, &class_path[start], end - start);
    path[end - start] = '\0';

    update_class_path_entry_list(path, false, false);

    while (class_path[end] == os::path_separator()[0]) {
      end++;
    }
  }
}

void ClassLoader::add_to_module_path_entries(const char* path,
                                             ClassPathEntry* entry) {
  assert(entry != NULL, "ClassPathEntry should not be NULL");
  assert(DumpSharedSpaces, "dump time only");

  // The entry does not exist, add to the list
  if (_module_path_entries == NULL) {
    assert(_last_module_path_entry == NULL, "Sanity");
    _module_path_entries = _last_module_path_entry = entry;
  } else {
    _last_module_path_entry->set_next(entry);
    _last_module_path_entry = entry;
  }
}

// Add a module path to the _module_path_entries list.
void ClassLoader::update_module_path_entry_list(const char *path, TRAPS) {
  assert(DumpSharedSpaces, "dump time only");
  struct stat st;
  if (os::stat(path, &st) != 0) {
    tty->print_cr("os::stat error %d (%s). CDS dump aborted (path was \"%s\").",
      errno, os::errno_name(errno), path);
    vm_exit_during_initialization();
  }
  // File or directory found
  ClassPathEntry* new_entry = NULL;
  new_entry = create_class_path_entry(path, &st, true /* throw_exception */,
                                      false /*is_boot_append */, CHECK);
  if (new_entry == NULL) {
    return;
  }

  add_to_module_path_entries(path, new_entry);
  return;
}

void ClassLoader::setup_module_search_path(const char* path, TRAPS) {
  update_module_path_entry_list(path, THREAD);
}
#endif // INCLUDE_CDS

// Construct the array of module/path pairs as specified to --patch-module
// for the boot loader to search ahead of the jimage, if the class being
// loaded is defined to a module that has been specified to --patch-module.
void ClassLoader::setup_patch_mod_entries() {
  Thread* THREAD = Thread::current();
  GrowableArray<ModulePatchPath*>* patch_mod_args = Arguments::get_patch_mod_prefix();
  int num_of_entries = patch_mod_args->length();


  // Set up the boot loader's _patch_mod_entries list
  _patch_mod_entries = new (ResourceObj::C_HEAP, mtModule) GrowableArray<ModuleClassPathList*>(num_of_entries, true);

  for (int i = 0; i < num_of_entries; i++) {
    const char* module_name = (patch_mod_args->at(i))->module_name();
    Symbol* const module_sym = SymbolTable::lookup(module_name, (int)strlen(module_name), CHECK);
    assert(module_sym != NULL, "Failed to obtain Symbol for module name");
    ModuleClassPathList* module_cpl = new ModuleClassPathList(module_sym);

    char* class_path = (patch_mod_args->at(i))->path_string();
    int len = (int)strlen(class_path);
    int end = 0;
    // Iterate over the module's class path entries
    for (int start = 0; start < len; start = end) {
      while (class_path[end] && class_path[end] != os::path_separator()[0]) {
        end++;
      }
      EXCEPTION_MARK;
      ResourceMark rm(THREAD);
      char* path = NEW_RESOURCE_ARRAY(char, end - start + 1);
      strncpy(path, &class_path[start], end - start);
      path[end - start] = '\0';

      struct stat st;
      if (os::stat(path, &st) == 0) {
        // File or directory found
        ClassPathEntry* new_entry = create_class_path_entry(path, &st, false, false, CHECK);
        // If the path specification is valid, enter it into this module's list
        if (new_entry != NULL) {
          module_cpl->add_to_list(new_entry);
        }
      }

      while (class_path[end] == os::path_separator()[0]) {
        end++;
      }
    }

    // Record the module into the list of --patch-module entries only if
    // valid ClassPathEntrys have been created
    if (module_cpl->module_first_entry() != NULL) {
      _patch_mod_entries->push(module_cpl);
    }
  }
}

// Determine whether the module has been patched via the command-line
// option --patch-module
bool ClassLoader::is_in_patch_mod_entries(Symbol* module_name) {
  if (_patch_mod_entries != NULL && _patch_mod_entries->is_nonempty()) {
    int table_len = _patch_mod_entries->length();
    for (int i = 0; i < table_len; i++) {
      ModuleClassPathList* patch_mod = _patch_mod_entries->at(i);
      if (module_name->fast_compare(patch_mod->module_name()) == 0) {
        return true;
      }
    }
  }
  return false;
}

// Set up the _jrt_entry if present and boot append path
void ClassLoader::setup_boot_search_path(const char *class_path) {
  int len = (int)strlen(class_path);
  int end = 0;
  bool set_base_piece = true;

#if INCLUDE_CDS
  if (DumpSharedSpaces) {
    if (!Arguments::has_jimage()) {
      vm_exit_during_initialization("CDS is not supported in exploded JDK build", NULL);
    }
  }
#endif

  // Iterate over class path entries
  for (int start = 0; start < len; start = end) {
    while (class_path[end] && class_path[end] != os::path_separator()[0]) {
      end++;
    }
    EXCEPTION_MARK;
    ResourceMark rm(THREAD);
    char* path = NEW_RESOURCE_ARRAY(char, end - start + 1);
    strncpy(path, &class_path[start], end - start);
    path[end - start] = '\0';

    if (set_base_piece) {
      // The first time through the bootstrap_search setup, it must be determined
      // what the base or core piece of the boot loader search is.  Either a java runtime
      // image is present or this is an exploded module build situation.
      assert(string_ends_with(path, MODULES_IMAGE_NAME) || string_ends_with(path, JAVA_BASE_NAME),
             "Incorrect boot loader search path, no java runtime image or " JAVA_BASE_NAME " exploded build");
      struct stat st;
      if (os::stat(path, &st) == 0) {
        // Directory found
        ClassPathEntry* new_entry = create_class_path_entry(path, &st, false, false, CHECK);

        // Check for a jimage
        if (Arguments::has_jimage()) {
          assert(_jrt_entry == NULL, "should not setup bootstrap class search path twice");
          assert(new_entry != NULL && new_entry->is_modules_image(), "No java runtime image present");
          _jrt_entry = new_entry;
          assert(_jrt_entry->jimage() != NULL, "No java runtime image");
        }
      } else {
        // If path does not exist, exit
        vm_exit_during_initialization("Unable to establish the boot loader search path", path);
      }
      set_base_piece = false;
    } else {
      // Every entry on the system boot class path after the initial base piece,
      // which is set by os::set_boot_path(), is considered an appended entry.
      update_class_path_entry_list(path, false, true);
    }

    while (class_path[end] == os::path_separator()[0]) {
      end++;
    }
  }
}

// During an exploded modules build, each module defined to the boot loader
// will be added to the ClassLoader::_exploded_entries array.
void ClassLoader::add_to_exploded_build_list(Symbol* module_sym, TRAPS) {
  assert(!ClassLoader::has_jrt_entry(), "Exploded build not applicable");
  assert(_exploded_entries != NULL, "_exploded_entries was not initialized");

  // Find the module's symbol
  ResourceMark rm(THREAD);
  const char *module_name = module_sym->as_C_string();
  const char *home = Arguments::get_java_home();
  const char file_sep = os::file_separator()[0];
  // 10 represents the length of "modules" + 2 file separators + \0
  size_t len = strlen(home) + strlen(module_name) + 10;
  char *path = NEW_RESOURCE_ARRAY(char, len);
  jio_snprintf(path, len, "%s%cmodules%c%s", home, file_sep, file_sep, module_name);

  struct stat st;
  if (os::stat(path, &st) == 0) {
    // Directory found
    ClassPathEntry* new_entry = create_class_path_entry(path, &st, false, false, CHECK);

    // If the path specification is valid, enter it into this module's list.
    // There is no need to check for duplicate modules in the exploded entry list,
    // since no two modules with the same name can be defined to the boot loader.
    // This is checked at module definition time in Modules::define_module.
    if (new_entry != NULL) {
      ModuleClassPathList* module_cpl = new ModuleClassPathList(module_sym);
      module_cpl->add_to_list(new_entry);
      {
        MutexLocker ml(Module_lock, THREAD);
        _exploded_entries->push(module_cpl);
      }
      log_info(class, load)("path: %s", path);
    }
  }
}

ClassPathEntry* ClassLoader::create_class_path_entry(const char *path, const struct stat* st,
                                                     bool throw_exception,
                                                     bool is_boot_append, TRAPS) {
  JavaThread* thread = JavaThread::current();
  ClassPathEntry* new_entry = NULL;
  if ((st->st_mode & S_IFMT) == S_IFREG) {
    ResourceMark rm(thread);
    // Regular file, should be a zip or jimage file
    // Canonicalized filename
    char* canonical_path = NEW_RESOURCE_ARRAY_IN_THREAD(thread, char, JVM_MAXPATHLEN);
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
        char *msg;
        if (error_msg == NULL) {
          msg = NEW_RESOURCE_ARRAY_IN_THREAD(thread, char, strlen(path) + 128); ;
          jio_snprintf(msg, strlen(path) + 127, "error in opening JAR file %s", path);
        } else {
          int len = (int)(strlen(path) + strlen(error_msg) + 128);
          msg = NEW_RESOURCE_ARRAY_IN_THREAD(thread, char, len); ;
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
    log_info(class, path)("opened: %s", path);
    log_info(class, load)("opened: %s", path);
  } else {
    // Directory
    new_entry = new ClassPathDirEntry(path);
    log_info(class, load)("path: %s", path);
  }
  return new_entry;
}


// Create a class path zip entry for a given path (return NULL if not found
// or zip/JAR file cannot be opened)
ClassPathZipEntry* ClassLoader::create_class_path_zip_entry(const char *path, bool is_boot_append) {
  // check for a regular file
  struct stat st;
  if (os::stat(path, &st) == 0) {
    if ((st.st_mode & S_IFMT) == S_IFREG) {
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

// returns true if entry already on class path
bool ClassLoader::contains_append_entry(const char* name) {
  ClassPathEntry* e = _first_append_entry;
  while (e != NULL) {
    // assume zip entries have been canonicalized
    if (strcmp(name, e->name()) == 0) {
      return true;
    }
    e = e->next();
  }
  return false;
}

void ClassLoader::add_to_boot_append_entries(ClassPathEntry *new_entry) {
  if (new_entry != NULL) {
    if (_last_append_entry == NULL) {
      assert(_first_append_entry == NULL, "boot loader's append class path entry list not empty");
      _first_append_entry = _last_append_entry = new_entry;
    } else {
      _last_append_entry->set_next(new_entry);
      _last_append_entry = new_entry;
    }
  }
}

// Record the path entries specified in -cp during dump time. The recorded
// information will be used at runtime for loading the archived app classes.
//
// Note that at dump time, ClassLoader::_app_classpath_entries are NOT used for
// loading app classes. Instead, the app class are loaded by the
// jdk/internal/loader/ClassLoaders$AppClassLoader instance.
void ClassLoader::add_to_app_classpath_entries(const char* path,
                                               ClassPathEntry* entry,
                                               bool check_for_duplicates) {
#if INCLUDE_CDS
  assert(entry != NULL, "ClassPathEntry should not be NULL");
  ClassPathEntry* e = _app_classpath_entries;
  if (check_for_duplicates) {
    while (e != NULL) {
      if (strcmp(e->name(), entry->name()) == 0) {
        // entry already exists
        return;
      }
      e = e->next();
    }
  }

  // The entry does not exist, add to the list
  if (_app_classpath_entries == NULL) {
    assert(_last_app_classpath_entry == NULL, "Sanity");
    _app_classpath_entries = _last_app_classpath_entry = entry;
  } else {
    _last_app_classpath_entry->set_next(entry);
    _last_app_classpath_entry = entry;
  }

  if (entry->is_jar_file()) {
    ClassLoaderExt::process_jar_manifest(entry, check_for_duplicates);
  }
#endif
}

// Returns true IFF the file/dir exists and the entry was successfully created.
bool ClassLoader::update_class_path_entry_list(const char *path,
                                               bool check_for_duplicates,
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

    // Do not reorder the bootclasspath which would break get_system_package().
    // Add new entry to linked list
    if (is_boot_append) {
      add_to_boot_append_entries(new_entry);
    } else {
      add_to_app_classpath_entries(path, new_entry, check_for_duplicates);
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

static void print_module_entry_table(const GrowableArray<ModuleClassPathList*>* const module_list) {
  ResourceMark rm;
  int num_of_entries = module_list->length();
  for (int i = 0; i < num_of_entries; i++) {
    ClassPathEntry* e;
    ModuleClassPathList* mpl = module_list->at(i);
    tty->print("%s=", mpl->module_name()->as_C_string());
    e = mpl->module_first_entry();
    while (e != NULL) {
      tty->print("%s", e->name());
      e = e->next();
      if (e != NULL) {
        tty->print("%s", os::path_separator());
      }
    }
    tty->print(" ;");
  }
}

void ClassLoader::print_bootclasspath() {
  ClassPathEntry* e;
  tty->print("[bootclasspath= ");

  // Print --patch-module module/path specifications first
  if (_patch_mod_entries != NULL) {
    print_module_entry_table(_patch_mod_entries);
  }

  // [jimage | exploded modules build]
  if (has_jrt_entry()) {
    // Print the location of the java runtime image
    tty->print("%s ;", _jrt_entry->name());
  } else {
    // Print exploded module build path specifications
    if (_exploded_entries != NULL) {
      print_module_entry_table(_exploded_entries);
    }
  }

  // appended entries
  e = _first_append_entry;
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
  if (os::dll_locate_lib(path, sizeof(path), Arguments::get_dll_dir(), "zip")) {
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
  if (os::dll_locate_lib(path, sizeof(path), Arguments::get_dll_dir(), "jimage")) {
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
  JImageResourcePath = CAST_TO_FN_PTR(JImage_ResourcePath_t, os::dll_lookup(handle, "JIMAGE_ResourcePath"));
  guarantee(JImageResourcePath != NULL, "function JIMAGE_ResourcePath not found");
}

jboolean ClassLoader::decompress(void *in, u8 inSize, void *out, u8 outSize, char **pmsg) {
  return (*ZipInflateFully)(in, inSize, out, outSize, pmsg);
}

int ClassLoader::crc32(int crc, const char* buf, int len) {
  assert(Crc32 != NULL, "ZIP_CRC32 is not found");
  return (*Crc32)(crc, (const jbyte*)buf, len);
}

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
  ResourceMark rm;
  const char *cp = package_from_name(fullq_class_name);
  if (cp != NULL) {
    PackageEntryTable* pkg_entry_tbl = ClassLoaderData::the_null_class_loader_data()->packages();
    TempNewSymbol pkg_symbol = SymbolTable::new_symbol(cp, CHECK_false);
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

ClassPathEntry* find_first_module_cpe(ModuleEntry* mod_entry,
                                      const GrowableArray<ModuleClassPathList*>* const module_list) {
  int num_of_entries = module_list->length();
  const Symbol* class_module_name = mod_entry->name();

  // Loop through all the modules in either the patch-module or exploded entries looking for module
  for (int i = 0; i < num_of_entries; i++) {
    ModuleClassPathList* module_cpl = module_list->at(i);
    Symbol* module_cpl_name = module_cpl->module_name();

    if (module_cpl_name->fast_compare(class_module_name) == 0) {
      // Class' module has been located.
      return module_cpl->module_first_entry();
    }
  }
  return NULL;
}


// Search either the patch-module or exploded build entries for class.
ClassFileStream* ClassLoader::search_module_entries(const GrowableArray<ModuleClassPathList*>* const module_list,
                                                    const char* const class_name,
                                                    const char* const file_name,
                                                    TRAPS) {
  ClassFileStream* stream = NULL;

  // Find the class' defining module in the boot loader's module entry table
  PackageEntry* pkg_entry = get_package_entry(class_name, ClassLoaderData::the_null_class_loader_data(), CHECK_NULL);
  ModuleEntry* mod_entry = (pkg_entry != NULL) ? pkg_entry->module() : NULL;

  // If the module system has not defined java.base yet, then
  // classes loaded are assumed to be defined to java.base.
  // When java.base is eventually defined by the module system,
  // all packages of classes that have been previously loaded
  // are verified in ModuleEntryTable::verify_javabase_packages().
  if (!Universe::is_module_initialized() &&
      !ModuleEntryTable::javabase_defined() &&
      mod_entry == NULL) {
    mod_entry = ModuleEntryTable::javabase_moduleEntry();
  }

  // The module must be a named module
  ClassPathEntry* e = NULL;
  if (mod_entry != NULL && mod_entry->is_named()) {
    if (module_list == _exploded_entries) {
      // The exploded build entries can be added to at any time so a lock is
      // needed when searching them.
      assert(!ClassLoader::has_jrt_entry(), "Must be exploded build");
      MutexLocker ml(Module_lock, THREAD);
      e = find_first_module_cpe(mod_entry, module_list);
    } else {
      e = find_first_module_cpe(mod_entry, module_list);
    }
  }

  // Try to load the class from the module's ClassPathEntry list.
  while (e != NULL) {
    stream = e->open_stream(file_name, CHECK_NULL);
    // No context.check is required since CDS is not supported
    // for an exploded modules build or if --patch-module is specified.
    if (NULL != stream) {
      return stream;
    }
    e = e->next();
  }
  // If the module was located, break out even if the class was not
  // located successfully from that module's ClassPathEntry list.
  // There will not be another valid entry for that module.
  return NULL;
}

// Called by the boot classloader to load classes
InstanceKlass* ClassLoader::load_class(Symbol* name, bool search_append_only, TRAPS) {
  assert(name != NULL, "invariant");
  assert(THREAD->is_Java_thread(), "must be a JavaThread");

  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);

  const char* const class_name = name->as_C_string();

  EventMark m("loading class %s", class_name);

  const char* const file_name = file_name_for_class_name(class_name,
                                                         name->utf8_length());
  assert(file_name != NULL, "invariant");

  // Lookup stream for parsing .class file
  ClassFileStream* stream = NULL;
  s2 classpath_index = 0;
  ClassPathEntry* e = NULL;

  // If search_append_only is true, boot loader visibility boundaries are
  // set to be _first_append_entry to the end. This includes:
  //   [-Xbootclasspath/a]; [jvmti appended entries]
  //
  // If search_append_only is false, boot loader visibility boundaries are
  // set to be the --patch-module entries plus the base piece. This includes:
  //   [--patch-module=<module>=<file>(<pathsep><file>)*]; [jimage | exploded module build]
  //

  // Load Attempt #1: --patch-module
  // Determine the class' defining module.  If it appears in the _patch_mod_entries,
  // attempt to load the class from those locations specific to the module.
  // Specifications to --patch-module can contain a partial number of classes
  // that are part of the overall module definition.  So if a particular class is not
  // found within its module specification, the search should continue to Load Attempt #2.
  // Note: The --patch-module entries are never searched if the boot loader's
  //       visibility boundary is limited to only searching the append entries.
  if (_patch_mod_entries != NULL && !search_append_only) {
    // At CDS dump time, the --patch-module entries are ignored. That means a
    // class is still loaded from the runtime image even if it might
    // appear in the _patch_mod_entries. The runtime shared class visibility
    // check will determine if a shared class is visible based on the runtime
    // environemnt, including the runtime --patch-module setting.
    if (!DumpSharedSpaces) {
      stream = search_module_entries(_patch_mod_entries, class_name, file_name, CHECK_NULL);
    }
  }

  // Load Attempt #2: [jimage | exploded build]
  if (!search_append_only && (NULL == stream)) {
    if (has_jrt_entry()) {
      e = _jrt_entry;
      stream = _jrt_entry->open_stream(file_name, CHECK_NULL);
    } else {
      // Exploded build - attempt to locate class in its defining module's location.
      assert(_exploded_entries != NULL, "No exploded build entries present");
      stream = search_module_entries(_exploded_entries, class_name, file_name, CHECK_NULL);
    }
  }

  // Load Attempt #3: [-Xbootclasspath/a]; [jvmti appended entries]
  if (search_append_only && (NULL == stream)) {
    // For the boot loader append path search, the starting classpath_index
    // for the appended piece is always 1 to account for either the
    // _jrt_entry or the _exploded_entries.
    assert(classpath_index == 0, "The classpath_index has been incremented incorrectly");
    classpath_index = 1;

    e = _first_append_entry;
    while (e != NULL) {
      stream = e->open_stream(file_name, CHECK_NULL);
      if (NULL != stream) {
        break;
      }
      e = e->next();
      ++classpath_index;
    }
  }

  if (NULL == stream) {
    return NULL;
  }

  stream->set_verify(ClassLoaderExt::should_verify(classpath_index));

  ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
  Handle protection_domain;

  InstanceKlass* result = KlassFactory::create_from_stream(stream,
                                                           name,
                                                           loader_data,
                                                           protection_domain,
                                                           NULL, // host_klass
                                                           NULL, // cp_patches
                                                           THREAD);
  if (HAS_PENDING_EXCEPTION) {
    if (DumpSharedSpaces) {
      tty->print_cr("Preload Error: Failed to load %s", class_name);
    }
    return NULL;
  }

  if (!add_package(file_name, classpath_index, THREAD)) {
    return NULL;
  }

  return result;
}

#if INCLUDE_CDS
char* ClassLoader::skip_uri_protocol(char* source) {
  if (strncmp(source, "file:", 5) == 0) {
    // file: protocol path could start with file:/ or file:///
    // locate the char after all the forward slashes
    int offset = 5;
    while (*(source + offset) == '/') {
        offset++;
    }
    source += offset;
  // for non-windows platforms, move back one char as the path begins with a '/'
#ifndef _WINDOWS
    source -= 1;
#endif
  } else if (strncmp(source, "jrt:/", 5) == 0) {
    source += 5;
  }
  return source;
}

// Record the shared classpath index and loader type for classes loaded
// by the builtin loaders at dump time.
void ClassLoader::record_result(InstanceKlass* ik, const ClassFileStream* stream, TRAPS) {
  assert(DumpSharedSpaces, "sanity");
  assert(stream != NULL, "sanity");

  if (ik->is_anonymous()) {
    // We do not archive anonymous classes.
    return;
  }

  oop loader = ik->class_loader();
  char* src = (char*)stream->source();
  if (src == NULL) {
    if (loader == NULL) {
      // JFR classes
      ik->set_shared_classpath_index(0);
      ik->set_class_loader_type(ClassLoader::BOOT_LOADER);
    }
    return;
  }

  assert(has_jrt_entry(), "CDS dumping does not support exploded JDK build");

  ResourceMark rm(THREAD);
  int classpath_index = -1;
  PackageEntry* pkg_entry = ik->package();

  if (FileMapInfo::get_number_of_shared_paths() > 0) {
    char* canonical_path_table_entry = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, JVM_MAXPATHLEN);

    // save the path from the file: protocol or the module name from the jrt: protocol
    // if no protocol prefix is found, path is the same as stream->source()
    char* path = skip_uri_protocol(src);
    char* canonical_class_src_path = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, JVM_MAXPATHLEN);
    if (!get_canonical_path(path, canonical_class_src_path, JVM_MAXPATHLEN)) {
      tty->print_cr("Bad pathname %s. CDS dump aborted.", path);
      vm_exit(1);
    }
    for (int i = 0; i < FileMapInfo::get_number_of_shared_paths(); i++) {
      SharedClassPathEntry* ent = FileMapInfo::shared_path(i);
      if (!get_canonical_path(ent->name(), canonical_path_table_entry, JVM_MAXPATHLEN)) {
        tty->print_cr("Bad pathname %s. CDS dump aborted.", ent->name());
        vm_exit(1);
      }
      // If the path (from the class stream source) is the same as the shared
      // class or module path, then we have a match.
      if (strcmp(canonical_path_table_entry, canonical_class_src_path) == 0) {
        // NULL pkg_entry and pkg_entry in an unnamed module implies the class
        // is from the -cp or boot loader append path which consists of -Xbootclasspath/a
        // and jvmti appended entries.
        if ((pkg_entry == NULL) || (pkg_entry->in_unnamed_module())) {
          // Ensure the index is within the -cp range before assigning
          // to the classpath_index.
          if (SystemDictionary::is_system_class_loader(loader) &&
              (i >= ClassLoaderExt::app_class_paths_start_index()) &&
              (i < ClassLoaderExt::app_module_paths_start_index())) {
            classpath_index = i;
            break;
          } else {
            if ((i >= 1) &&
                (i < ClassLoaderExt::app_class_paths_start_index())) {
              // The class must be from boot loader append path which consists of
              // -Xbootclasspath/a and jvmti appended entries.
              assert(loader == NULL, "sanity");
              classpath_index = i;
              break;
            }
          }
        } else {
          // A class from a named module from the --module-path. Ensure the index is
          // within the --module-path range before assigning to the classpath_index.
          if ((pkg_entry != NULL) && !(pkg_entry->in_unnamed_module()) && (i > 0)) {
            if (i >= ClassLoaderExt::app_module_paths_start_index() &&
                i < FileMapInfo::get_number_of_shared_paths()) {
              classpath_index = i;
              break;
            }
          }
        }
      }
      // for index 0 and the stream->source() is the modules image or has the jrt: protocol.
      // The class must be from the runtime modules image.
      if (i == 0 && (is_modules_image(src) || string_starts_with(src, "jrt:"))) {
        classpath_index = i;
        break;
      }
    }

    // No path entry found for this class. Must be a shared class loaded by the
    // user defined classloader.
    if (classpath_index < 0) {
      assert(ik->shared_classpath_index() < 0, "Sanity");
      return;
    }
  } else {
    // The shared path table is set up after module system initialization.
    // The path table contains no entry before that. Any classes loaded prior
    // to the setup of the shared path table must be from the modules image.
    assert(is_modules_image(src), "stream must be from modules image");
    assert(FileMapInfo::get_number_of_shared_paths() == 0, "shared path table must not have been setup");
    classpath_index = 0;
  }

  const char* const class_name = ik->name()->as_C_string();
  const char* const file_name = file_name_for_class_name(class_name,
                                                         ik->name()->utf8_length());
  assert(file_name != NULL, "invariant");

  ClassLoaderExt::record_result(classpath_index, ik, THREAD);
}
#endif // INCLUDE_CDS

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

    NEWPERFEVENTCOUNTER(_load_instance_class_failCounter, SUN_CLS,
                        "loadInstanceClassFailRate");
  }

  // lookup zip library entry points
  load_zip_library();
  // lookup jimage library entry points
  load_jimage_library();
#if INCLUDE_CDS
  // initialize search path
  if (DumpSharedSpaces) {
    _shared_paths_misc_info = new SharedPathsMiscInfo();
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

void ClassLoader::initialize_module_path(TRAPS) {
  if (DumpSharedSpaces) {
    ClassLoaderExt::setup_module_paths(THREAD);
    FileMapInfo::allocate_shared_path_table();
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


void classLoader_init1() {
  ClassLoader::initialize();
}

// Complete the ClassPathEntry setup for the boot loader
void ClassLoader::classLoader_init2(TRAPS) {
  // Setup the list of module/path pairs for --patch-module processing
  // This must be done after the SymbolTable is created in order
  // to use fast_compare on module names instead of a string compare.
  if (Arguments::get_patch_mod_prefix() != NULL) {
    setup_patch_mod_entries();
  }

  // Create the ModuleEntry for java.base (must occur after setup_patch_mod_entries
  // to successfully determine if java.base has been patched)
  create_javabase();

  // Setup the initial java.base/path pair for the exploded build entries.
  // As more modules are defined during module system initialization, more
  // entries will be added to the exploded build array.
  if (!has_jrt_entry()) {
    assert(!DumpSharedSpaces, "DumpSharedSpaces not supported with exploded module builds");
    assert(!UseSharedSpaces, "UsedSharedSpaces not supported with exploded module builds");
    // Set up the boot loader's _exploded_entries list.  Note that this gets
    // done before loading any classes, by the same thread that will
    // subsequently do the first class load. So, no lock is needed for this.
    assert(_exploded_entries == NULL, "Should only get initialized once");
    _exploded_entries = new (ResourceObj::C_HEAP, mtModule)
      GrowableArray<ModuleClassPathList*>(EXPLODED_ENTRY_SIZE, true);
    add_to_exploded_build_list(vmSymbols::java_base(), CHECK);
  }
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
    ModuleEntry* jb_module = null_cld_modules->locked_create_entry_or_null(Handle(),
                               false, vmSymbols::java_base(), NULL, NULL, null_cld);
    if (jb_module == NULL) {
      vm_exit_during_initialization("Unable to create ModuleEntry for " JAVA_BASE_NAME);
    }
    ModuleEntryTable::set_javabase_moduleEntry(jb_module);
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

  assert(has_jrt_entry(), "Compile The World not supported with exploded module build");

  // Find bootstrap loader
  Handle system_class_loader (THREAD, SystemDictionary::java_system_loader());
  jlong start = os::javaTimeMillis();

  // Compile the world for the modular java runtime image
  _jrt_entry->compile_the_world(system_class_loader, CATCH);

  // Iterate over all bootstrap class path appended entries
  ClassPathEntry* e = _first_append_entry;
  while (e != NULL) {
    assert(!e->is_modules_image(), "A modular java runtime image is present on the list of appended entries");
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
static bool can_be_compiled(const methodHandle& m, int comp_level) {
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
      Klass* k = SystemDictionary::resolve_or_null(sym, loader, Handle(), THREAD);
      if (k != NULL && !HAS_PENDING_EXCEPTION) {
        k->initialize(THREAD);
      }
      bool exception_occurred = HAS_PENDING_EXCEPTION;
      clear_pending_exception_if_not_oom(CHECK);
      if (CompileTheWorldPreloadClasses && k != NULL) {
        InstanceKlass* ik = InstanceKlass::cast(k);
        ConstantPool::preload_and_initialize_all_classes(ik->constants(), THREAD);
        if (HAS_PENDING_EXCEPTION) {
          // If something went wrong in preloading we just ignore it
          clear_pending_exception_if_not_oom(CHECK);
          tty->print_cr("Preloading failed for (%d) %s", _compile_the_world_class_counter, buffer);
        }
      }

      if (_compile_the_world_class_counter >= CompileTheWorldStartAt) {
        if (k == NULL || exception_occurred) {
          // If something went wrong (e.g. ExceptionInInitializerError) we skip this class
          tty->print_cr("CompileTheWorld (%d) : Skipping %s", _compile_the_world_class_counter, buffer);
        } else {
          tty->print_cr("CompileTheWorld (%d) : %s", _compile_the_world_class_counter, buffer);
          // Preload all classes to get around uncommon traps
          // Iterate over all methods in class
          int comp_level = CompilationPolicy::policy()->initial_compile_level();
          InstanceKlass* ik = InstanceKlass::cast(k);
          for (int n = 0; n < ik->methods()->length(); n++) {
            methodHandle m (THREAD, ik->methods()->at(n));
            if (can_be_compiled(m, comp_level)) {
              if (++_codecache_sweep_counter == CompileTheWorldSafepointInterval) {
                // Give sweeper a chance to keep up with CTW
                VM_CTWThreshold op;
                VMThread::execute(&op);
                _codecache_sweep_counter = 0;
              }
              // Force compilation
              CompileBroker::compile_method(m, InvocationEntryBci, comp_level,
                                            methodHandle(), 0, CompileTask::Reason_CTW, THREAD);
              if (HAS_PENDING_EXCEPTION) {
                clear_pending_exception_if_not_oom(CHECK);
                tty->print_cr("CompileTheWorld (%d) : Skipping method: %s", _compile_the_world_class_counter, m->name_and_sig_as_C_string());
              } else {
                _compile_the_world_method_counter++;
              }
              if (TieredCompilation && TieredStopAtLevel >= CompLevel_full_optimization) {
                // Clobber the first compile and force second tier compilation
                CompiledMethod* nm = m->code();
                if (nm != NULL && !m->is_method_handle_intrinsic()) {
                  // Throw out the code so that the code cache doesn't fill up
                  nm->make_not_entrant();
                }
                CompileBroker::compile_method(m, InvocationEntryBci, CompLevel_full_optimization,
                                              methodHandle(), 0, CompileTask::Reason_CTW, THREAD);
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

            CompiledMethod* nm = m->code();
            if (nm != NULL && !m->is_method_handle_intrinsic()) {
              // Throw out the code so that the code cache doesn't fill up
              nm->make_not_entrant();
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
