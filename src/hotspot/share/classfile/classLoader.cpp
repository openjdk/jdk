/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/cds_globals.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/heapShared.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.inline.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoadInfo.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/klassFactory.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/modules.hpp"
#include "classfile/packageEntry.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "compiler/compileBroker.hpp"
#include "cppstdlib/cstdlib.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/oopMapCache.hpp"
#include "jimage.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "logging/logTag.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "prims/jvm_misc.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/os.hpp"
#include "runtime/perfData.hpp"
#include "runtime/timer.hpp"
#include "runtime/vm_version.hpp"
#include "services/management.hpp"
#include "services/threadService.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/classpathStream.hpp"
#include "utilities/events.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/utf8.hpp"

#include <ctype.h>

// Entry point in java.dll for path canonicalization

typedef int (*canonicalize_fn_t)(const char *orig, char *out, int len);

static canonicalize_fn_t CanonicalizeEntry  = nullptr;

// Entry points for jimage.dll for loading jimage file entries

static JImageOpen_t                    JImageOpen             = nullptr;
static JImageClose_t                   JImageClose            = nullptr;
static JImageFindResource_t            JImageFindResource     = nullptr;
static JImageGetResource_t             JImageGetResource      = nullptr;

// JimageFile pointer, or null if exploded JDK build.
static JImageFile*                     JImage_file            = nullptr;

// Globals

PerfCounter*    ClassLoader::_perf_accumulated_time = nullptr;
PerfCounter*    ClassLoader::_perf_classes_inited = nullptr;
PerfCounter*    ClassLoader::_perf_class_init_time = nullptr;
PerfCounter*    ClassLoader::_perf_class_init_selftime = nullptr;
PerfCounter*    ClassLoader::_perf_classes_verified = nullptr;
PerfCounter*    ClassLoader::_perf_class_verify_time = nullptr;
PerfCounter*    ClassLoader::_perf_class_verify_selftime = nullptr;
PerfCounter*    ClassLoader::_perf_classes_linked = nullptr;
PerfCounter*    ClassLoader::_perf_class_link_time = nullptr;
PerfCounter*    ClassLoader::_perf_class_link_selftime = nullptr;
PerfCounter*    ClassLoader::_perf_shared_classload_time = nullptr;
PerfCounter*    ClassLoader::_perf_sys_classload_time = nullptr;
PerfCounter*    ClassLoader::_perf_app_classload_time = nullptr;
PerfCounter*    ClassLoader::_perf_app_classload_selftime = nullptr;
PerfCounter*    ClassLoader::_perf_app_classload_count = nullptr;
PerfCounter*    ClassLoader::_perf_define_appclasses = nullptr;
PerfCounter*    ClassLoader::_perf_define_appclass_time = nullptr;
PerfCounter*    ClassLoader::_perf_define_appclass_selftime = nullptr;
PerfCounter*    ClassLoader::_perf_app_classfile_bytes_read = nullptr;
PerfCounter*    ClassLoader::_perf_sys_classfile_bytes_read = nullptr;
PerfCounter*    ClassLoader::_perf_ik_link_methods_time = nullptr;
PerfCounter*    ClassLoader::_perf_method_adapters_time = nullptr;
PerfCounter*    ClassLoader::_perf_ik_link_methods_count = nullptr;
PerfCounter*    ClassLoader::_perf_method_adapters_count = nullptr;
PerfCounter*    ClassLoader::_unsafe_defineClassCallCounter = nullptr;
PerfCounter*    ClassLoader::_perf_secondary_hash_time = nullptr;
PerfCounter*    ClassLoader::_perf_change_wx_time = nullptr;

PerfCounter*    ClassLoader::_perf_resolve_indy_time = nullptr;
PerfCounter*    ClassLoader::_perf_resolve_invokehandle_time = nullptr;
PerfCounter*    ClassLoader::_perf_resolve_mh_time = nullptr;
PerfCounter*    ClassLoader::_perf_resolve_mt_time = nullptr;

PerfCounter*    ClassLoader::_perf_resolve_indy_count = nullptr;
PerfCounter*    ClassLoader::_perf_resolve_invokehandle_count = nullptr;
PerfCounter*    ClassLoader::_perf_resolve_mh_count = nullptr;
PerfCounter*    ClassLoader::_perf_resolve_mt_count = nullptr;

void ClassLoader::print_counters(outputStream *st) {
  st->print_cr("ClassLoader:");
  st->print_cr("  clinit:               " JLONG_FORMAT "ms / " JLONG_FORMAT " events", ClassLoader::class_init_time_ms(), ClassLoader::class_init_count());
  st->print_cr("  link methods:         " JLONG_FORMAT "ms / " JLONG_FORMAT " events", Management::ticks_to_ms(_perf_ik_link_methods_time->get_value())   , _perf_ik_link_methods_count->get_value());
  st->print_cr("  method adapters:      " JLONG_FORMAT "ms / " JLONG_FORMAT " events", Management::ticks_to_ms(_perf_method_adapters_time->get_value())   , _perf_method_adapters_count->get_value());
  st->print_cr("  resolve...");
  st->print_cr("    invokedynamic:   " JLONG_FORMAT "ms / " JLONG_FORMAT " events", Management::ticks_to_ms(_perf_resolve_indy_time->get_value())         , _perf_resolve_indy_count->get_value());
  st->print_cr("    invokehandle:    " JLONG_FORMAT "ms / " JLONG_FORMAT " events", Management::ticks_to_ms(_perf_resolve_invokehandle_time->get_value()) , _perf_resolve_invokehandle_count->get_value());
  st->print_cr("    CP_MethodHandle: " JLONG_FORMAT "ms / " JLONG_FORMAT " events", Management::ticks_to_ms(_perf_resolve_mh_time->get_value())           , _perf_resolve_mh_count->get_value());
  st->print_cr("    CP_MethodType:   " JLONG_FORMAT "ms / " JLONG_FORMAT " events", Management::ticks_to_ms(_perf_resolve_mt_time->get_value())           , _perf_resolve_mt_count->get_value());
  st->cr();
}

GrowableArray<ModuleClassPathList*>* ClassLoader::_patch_mod_entries = nullptr;
GrowableArray<ModuleClassPathList*>* ClassLoader::_exploded_entries = nullptr;
ClassPathEntry* ClassLoader::_jrt_entry = nullptr;

ClassPathEntry* volatile ClassLoader::_first_append_entry_list = nullptr;
ClassPathEntry* volatile ClassLoader::_last_append_entry  = nullptr;

// helper routines
#if INCLUDE_CDS
static bool string_starts_with(const char* str, const char* str_to_find) {
  size_t str_len = strlen(str);
  size_t str_to_find_len = strlen(str_to_find);
  if (str_to_find_len > str_len) {
    return false;
  }
  return (strncmp(str, str_to_find, str_to_find_len) == 0);
}
#endif

static const char* get_jimage_version_string() {
  static char version_string[10] = "";
  if (version_string[0] == '\0') {
    jio_snprintf(version_string, sizeof(version_string), "%d.%d",
                 VM_Version::vm_major_version(), VM_Version::vm_minor_version());
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
Symbol* ClassLoader::package_from_class_name(const Symbol* name, bool* bad_class_name) {
  if (name == nullptr) {
    if (bad_class_name != nullptr) {
      *bad_class_name = true;
    }
    return nullptr;
  }

  int utf_len = name->utf8_length();
  const jbyte* base = (const jbyte*)name->base();
  const jbyte* start = base;
  const jbyte* end = UTF8::strrchr(start, utf_len, JVM_SIGNATURE_SLASH);
  if (end == nullptr) {
    return nullptr;
  }
  // Skip over '['s
  if (*start == JVM_SIGNATURE_ARRAY) {
    do {
      start++;
    } while (start < end && *start == JVM_SIGNATURE_ARRAY);

    // Fully qualified class names should not contain a 'L'.
    // Set bad_class_name to true to indicate that the package name
    // could not be obtained due to an error condition.
    // In this situation, is_same_class_package returns false.
    if (*start == JVM_SIGNATURE_CLASS) {
      if (bad_class_name != nullptr) {
        *bad_class_name = true;
      }
      return nullptr;
    }
  }
  // A class name could have just the slash character in the name,
  // in which case start > end
  if (start >= end) {
    // No package name
    if (bad_class_name != nullptr) {
      *bad_class_name = true;
    }
    return nullptr;
  }
  return SymbolTable::new_symbol(name, pointer_delta_as_int(start, base), pointer_delta_as_int(end, base));
}

// Given a fully qualified package name, find its defining package in the class loader's
// package entry table.
PackageEntry* ClassLoader::get_package_entry(Symbol* pkg_name, ClassLoaderData* loader_data) {
  if (pkg_name == nullptr) {
    return nullptr;
  }
  PackageEntryTable* pkgEntryTable = loader_data->packages();
  return pkgEntryTable->lookup_only(pkg_name);
}

const char* ClassPathEntry::copy_path(const char* path) {
  char* copy = NEW_C_HEAP_ARRAY(char, strlen(path)+1, mtClass);
  strcpy(copy, path);
  return copy;
}

ClassPathDirEntry::~ClassPathDirEntry() {
  FREE_C_HEAP_ARRAY(char, _dir);
}

ClassFileStream* ClassPathDirEntry::open_stream(JavaThread* current, const char* name) {
  // construct full path name
  assert((_dir != nullptr) && (name != nullptr), "sanity");
  size_t path_len = strlen(_dir) + strlen(name) + strlen(os::file_separator()) + 1;
  char* path = NEW_RESOURCE_ARRAY_IN_THREAD(current, char, path_len);
  int len = jio_snprintf(path, path_len, "%s%s%s", _dir, os::file_separator(), name);
  assert(len == (int)(path_len - 1), "sanity");
  // check if file exists
  struct stat st;
  if (os::stat(path, &st) == 0) {
    // found file, open it
    int file_handle = os::open(path, 0, 0);
    if (file_handle != -1) {
      // read contents into resource array
      u1* buffer = NEW_RESOURCE_ARRAY_IN_THREAD(current, u1, st.st_size);
      size_t num_read = ::read(file_handle, (char*) buffer, st.st_size);
      // close file
      ::close(file_handle);
      // construct ClassFileStream
      if (num_read == (size_t)st.st_size) {
        if (UsePerfData) {
          ClassLoader::perf_sys_classfile_bytes_read()->inc(num_read);
        }
#ifdef ASSERT
        // Freeing path is a no-op here as buffer prevents it from being reclaimed. But we keep it for
        // debug builds so that we guard against use-after-free bugs.
        FREE_RESOURCE_ARRAY_IN_THREAD(current, char, path, path_len);
#endif
        // We don't verify the length of the classfile stream fits in an int, but this is the
        // bootloader so we have control of this.
        // Resource allocated
        return new ClassFileStream(buffer,
                                   checked_cast<int>(st.st_size),
                                   _dir);
      }
    }
  }
  FREE_RESOURCE_ARRAY_IN_THREAD(current, char, path, path_len);
  return nullptr;
}

ClassPathZipEntry::ClassPathZipEntry(jzfile* zip, const char* zip_name) : ClassPathEntry() {
  _zip = zip;
  _zip_name = copy_path(zip_name);
}

ClassPathZipEntry::~ClassPathZipEntry() {
  ZipLibrary::close(_zip);
  FREE_C_HEAP_ARRAY(char, _zip_name);
}

bool ClassPathZipEntry::has_entry(JavaThread* current, const char* name) {
  ThreadToNativeFromVM ttn(current);
  // check whether zip archive contains name
  jint name_len;
  jint filesize;
  jzentry* entry = ZipLibrary::find_entry(_zip, name, &filesize, &name_len);
  if (entry == nullptr) {
    return false;
  } else {
     ZipLibrary::free_entry(_zip, entry);
    return true;
  }
}

u1* ClassPathZipEntry::open_entry(JavaThread* current, const char* name, jint* filesize, bool nul_terminate) {
  // enable call to C land
  ThreadToNativeFromVM ttn(current);
  // check whether zip archive contains name
  jint name_len;
  jzentry* entry = ZipLibrary::find_entry(_zip, name, filesize, &name_len);
  if (entry == nullptr) return nullptr;
  u1* buffer;
  char name_buf[128];
  char* filename;
  if (name_len < 128) {
    filename = name_buf;
  } else {
    filename = NEW_RESOURCE_ARRAY(char, name_len + 1);
  }

  // read contents into resource array
  size_t size = (uint32_t)(*filesize);
  if (nul_terminate) {
    if (sizeof(size) == sizeof(uint32_t) && size == UINT_MAX) {
      return nullptr; // 32-bit integer overflow will occur.
    }
    size++;
  }
  buffer = NEW_RESOURCE_ARRAY(u1, size);
  if (!ZipLibrary::read_entry(_zip, entry, buffer, filename)) {
    return nullptr;
  }

  // return result
  if (nul_terminate) {
    buffer[size - 1] = 0;
  }
  return buffer;
}

ClassFileStream* ClassPathZipEntry::open_stream(JavaThread* current, const char* name) {
  jint filesize;
  u1* buffer = open_entry(current, name, &filesize, false);
  if (buffer == nullptr) {
    return nullptr;
  }
  if (UsePerfData) {
    ClassLoader::perf_sys_classfile_bytes_read()->inc(filesize);
  }
  // Resource allocated
  return new ClassFileStream(buffer,
                             filesize,
                             _zip_name);
}

DEBUG_ONLY(ClassPathImageEntry* ClassPathImageEntry::_singleton = nullptr;)

JImageFile* ClassPathImageEntry::jimage() const {
  return JImage_file;
}

JImageFile* ClassPathImageEntry::jimage_non_null() const {
  assert(ClassLoader::has_jrt_entry(), "must be");
  assert(jimage() != nullptr, "should have been opened by ClassLoader::lookup_vm_options "
                           "and remained throughout normal JVM lifetime");
  return jimage();
}

void ClassPathImageEntry::close_jimage() {
  if (jimage() != nullptr) {
    (*JImageClose)(jimage());
    JImage_file = nullptr;
  }
}

ClassPathImageEntry::ClassPathImageEntry(JImageFile* jimage, const char* name) :
  ClassPathEntry() {
  guarantee(jimage != nullptr, "jimage file is null");
  guarantee(name != nullptr, "jimage file name is null");
  assert(_singleton == nullptr, "VM supports only one jimage");
  DEBUG_ONLY(_singleton = this);
  size_t len = strlen(name) + 1;
  _name = copy_path(name);
}

ClassFileStream* ClassPathImageEntry::open_stream(JavaThread* current, const char* name) {
  return open_stream_for_loader(current, name, ClassLoaderData::the_null_class_loader_data());
}

// For a class in a named module, look it up in the jimage file using this syntax:
//    /<module-name>/<package-name>/<base-class>
//
// Assumptions:
//     1. There are no unnamed modules in the jimage file.
//     2. A package is in at most one module in the jimage file.
//
ClassFileStream* ClassPathImageEntry::open_stream_for_loader(JavaThread* current, const char* name, ClassLoaderData* loader_data) {
  jlong size;
  JImageLocationRef location = 0;

  TempNewSymbol class_name = SymbolTable::new_symbol(name);
  TempNewSymbol pkg_name = ClassLoader::package_from_class_name(class_name);

  if (pkg_name != nullptr) {
    if (!Universe::is_module_initialized()) {
      location = (*JImageFindResource)(jimage_non_null(), JAVA_BASE_NAME, get_jimage_version_string(), name, &size);
    } else {
      PackageEntry* package_entry = ClassLoader::get_package_entry(pkg_name, loader_data);
      if (package_entry != nullptr) {
        ResourceMark rm(current);
        // Get the module name
        ModuleEntry* module = package_entry->module();
        assert(module != nullptr, "Boot classLoader package missing module");
        assert(module->is_named(), "Boot classLoader package is in unnamed module");
        const char* module_name = module->name()->as_C_string();
        if (module_name != nullptr) {
          location = (*JImageFindResource)(jimage_non_null(), module_name, get_jimage_version_string(), name, &size);
        }
      }
    }
  }

  if (location != 0) {
    if (UsePerfData) {
      ClassLoader::perf_sys_classfile_bytes_read()->inc(size);
    }
    char* data = NEW_RESOURCE_ARRAY(char, size);
    (*JImageGetResource)(jimage_non_null(), location, data, size);
    // Resource allocated
    assert(this == (ClassPathImageEntry*)ClassLoader::get_jrt_entry(), "must be");
    return new ClassFileStream((u1*)data,
                               checked_cast<int>(size),
                               _name,
                               true); // from_boot_loader_modules_image
  }

  return nullptr;
}

JImageLocationRef ClassLoader::jimage_find_resource(JImageFile* jf,
                                                    const char* module_name,
                                                    const char* file_name,
                                                    jlong &size) {
  return ((*JImageFindResource)(jf, module_name, get_jimage_version_string(), file_name, &size));
}

bool ClassPathImageEntry::is_modules_image() const {
  assert(this == _singleton, "VM supports a single jimage");
  assert(this == (ClassPathImageEntry*)ClassLoader::get_jrt_entry(), "must be used for jrt entry");
  return true;
}

ModuleClassPathList::ModuleClassPathList(Symbol* module_name) {
  _module_name = module_name;
  _module_first_entry = nullptr;
  _module_last_entry = nullptr;
}

ModuleClassPathList::~ModuleClassPathList() {
  // Clean out each ClassPathEntry on list
  ClassPathEntry* e = _module_first_entry;
  while (e != nullptr) {
    ClassPathEntry* next_entry = e->next();
    delete e;
    e = next_entry;
  }
}

void ModuleClassPathList::add_to_list(ClassPathEntry* new_entry) {
  if (new_entry != nullptr) {
    if (_module_last_entry == nullptr) {
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

void ClassLoader::setup_bootstrap_search_path(JavaThread* current) {
  const char* bootcp = Arguments::get_boot_class_path();
  assert(bootcp != nullptr, "Boot class path must not be nullptr");
  if (PrintSharedArchiveAndExit) {
    // Don't print bootcp - this is the bootcp of this current VM process, not necessarily
    // the same as the boot classpath of the shared archive.
  } else {
    trace_class_path("bootstrap loader class path=", bootcp);
  }
  setup_bootstrap_search_path_impl(current, bootcp);
}

void ClassLoader::close_jrt_image() {
  // Not applicable for exploded builds
  if (!ClassLoader::has_jrt_entry()) return;
  _jrt_entry->close_jimage();
}

// Construct the array of module/path pairs as specified to --patch-module
// for the boot loader to search ahead of the jimage, if the class being
// loaded is defined to a module that has been specified to --patch-module.
void ClassLoader::setup_patch_mod_entries() {
  JavaThread* current = JavaThread::current();
  GrowableArray<ModulePatchPath*>* patch_mod_args = Arguments::get_patch_mod_prefix();
  int num_of_entries = patch_mod_args->length();

  // Set up the boot loader's _patch_mod_entries list
  _patch_mod_entries = new (mtModule) GrowableArray<ModuleClassPathList*>(num_of_entries, mtModule);

  for (int i = 0; i < num_of_entries; i++) {
    const char* module_name = (patch_mod_args->at(i))->module_name();
    Symbol* const module_sym = SymbolTable::new_symbol(module_name);
    assert(module_sym != nullptr, "Failed to obtain Symbol for module name");
    ModuleClassPathList* module_cpl = new ModuleClassPathList(module_sym);

    char* class_path = (patch_mod_args->at(i))->path_string();
    ResourceMark rm(current);
    ClasspathStream cp_stream(class_path);

    while (cp_stream.has_next()) {
      const char* path = cp_stream.get_next();
      struct stat st;
      if (os::stat(path, &st) == 0) {
        // File or directory found
        ClassPathEntry* new_entry = create_class_path_entry(current, path, &st);
        // If the path specification is valid, enter it into this module's list
        if (new_entry != nullptr) {
          module_cpl->add_to_list(new_entry);
        }
      }
    }

    // Record the module into the list of --patch-module entries only if
    // valid ClassPathEntrys have been created
    if (module_cpl->module_first_entry() != nullptr) {
      _patch_mod_entries->push(module_cpl);
    }
  }
}

// Determine whether the module has been patched via the command-line
// option --patch-module
bool ClassLoader::is_in_patch_mod_entries(Symbol* module_name) {
  if (_patch_mod_entries != nullptr && _patch_mod_entries->is_nonempty()) {
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
void ClassLoader::setup_bootstrap_search_path_impl(JavaThread* current, const char *class_path) {
  ResourceMark rm(current);
  ClasspathStream cp_stream(class_path);
  bool set_base_piece = true;

#if INCLUDE_CDS
  if (CDSConfig::is_dumping_archive()) {
    if (!Arguments::has_jimage()) {
      vm_exit_during_initialization("CDS is not supported in exploded JDK build", nullptr);
    }
  }
#endif

  while (cp_stream.has_next()) {
    const char* path = cp_stream.get_next();

    if (set_base_piece) {
      // The first time through the bootstrap_search setup, it must be determined
      // what the base or core piece of the boot loader search is.  Either a java runtime
      // image is present or this is an exploded module build situation.
      assert(string_ends_with(path, MODULES_IMAGE_NAME) || string_ends_with(path, JAVA_BASE_NAME),
             "Incorrect boot loader search path, no java runtime image or " JAVA_BASE_NAME " exploded build");
      struct stat st;
      if (os::stat(path, &st) == 0) {
        // Directory found
        if (JImage_file != nullptr) {
          assert(Arguments::has_jimage(), "sanity check");
          const char* canonical_path = get_canonical_path(path, current);
          assert(canonical_path != nullptr, "canonical_path issue");

          _jrt_entry = new ClassPathImageEntry(JImage_file, canonical_path);
          assert(_jrt_entry != nullptr && _jrt_entry->is_modules_image(), "No java runtime image present");
          assert(_jrt_entry->jimage() != nullptr, "No java runtime image");
        } // else it's an exploded build.
      } else {
        // If path does not exist, exit
        vm_exit_during_initialization("Unable to establish the boot loader search path", path);
      }
      set_base_piece = false;
    } else {
      // Every entry on the boot class path after the initial base piece,
      // which is set by os::set_boot_path(), is considered an appended entry.
      update_class_path_entry_list(current, path);
    }
  }
}

// Gets the exploded path for the named module. The memory for the path
// is allocated on the C heap if `c_heap` is true otherwise in the resource area.
static const char* get_exploded_module_path(const char* module_name, bool c_heap) {
  const char *home = Arguments::get_java_home();
  const char file_sep = os::file_separator()[0];
  // 10 represents the length of "modules" + 2 file separators + \0
  size_t len = strlen(home) + strlen(module_name) + 10;
  char *path = c_heap ? NEW_C_HEAP_ARRAY(char, len, mtModule) : NEW_RESOURCE_ARRAY(char, len);
  jio_snprintf(path, len, "%s%cmodules%c%s", home, file_sep, file_sep, module_name);
  return path;
}

// During an exploded modules build, each module defined to the boot loader
// will be added to the ClassLoader::_exploded_entries array.
void ClassLoader::add_to_exploded_build_list(JavaThread* current, Symbol* module_sym) {
  assert(!ClassLoader::has_jrt_entry(), "Exploded build not applicable");
  assert(_exploded_entries != nullptr, "_exploded_entries was not initialized");

  // Find the module's symbol
  ResourceMark rm(current);
  const char *module_name = module_sym->as_C_string();
  const char *path = get_exploded_module_path(module_name, false);

  struct stat st;
  if (os::stat(path, &st) == 0) {
    // Directory found
    ClassPathEntry* new_entry = create_class_path_entry(current, path, &st);

    // If the path specification is valid, enter it into this module's list.
    // There is no need to check for duplicate modules in the exploded entry list,
    // since no two modules with the same name can be defined to the boot loader.
    // This is checked at module definition time in Modules::define_module.
    if (new_entry != nullptr) {
      ModuleClassPathList* module_cpl = new ModuleClassPathList(module_sym);
      module_cpl->add_to_list(new_entry);
      {
        MutexLocker ml(current, Module_lock);
        _exploded_entries->push(module_cpl);
      }
      log_info(class, load)("path: %s", path);
    }
  }
}

jzfile* ClassLoader::open_zip_file(const char* canonical_path, char** error_msg, JavaThread* thread) {
  // enable call to C land
  ThreadToNativeFromVM ttn(thread);
  HandleMark hm(thread);
  return ZipLibrary::open(canonical_path, error_msg);
}

ClassPathEntry* ClassLoader::create_class_path_entry(JavaThread* current,
                                                     const char *path, const struct stat* st) {
  ClassPathEntry* new_entry = nullptr;
  if ((st->st_mode & S_IFMT) == S_IFREG) {
    ResourceMark rm(current);
    // Regular file, should be a zip file
    // Canonicalized filename
    const char* canonical_path = get_canonical_path(path, current);
    if (canonical_path == nullptr) {
      return nullptr;
    }
    char* error_msg = nullptr;
    jzfile* zip = open_zip_file(canonical_path, &error_msg, current);
    if (zip != nullptr && error_msg == nullptr) {
      new_entry = new ClassPathZipEntry(zip, path);
    } else {
      return nullptr;
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


// Create a class path zip entry for a given path (return null if not found
// or zip/JAR file cannot be opened)
ClassPathZipEntry* ClassLoader::create_class_path_zip_entry(const char *path) {
  // check for a regular file
  struct stat st;
  if (os::stat(path, &st) == 0) {
    if ((st.st_mode & S_IFMT) == S_IFREG) {
      JavaThread* thread = JavaThread::current();
      ResourceMark rm(thread);
      const char* canonical_path = get_canonical_path(path, thread);
      if (canonical_path != nullptr) {
        char* error_msg = nullptr;
        jzfile* zip = open_zip_file(canonical_path, &error_msg, thread);
        if (zip != nullptr && error_msg == nullptr) {
          // create using canonical path
          return new ClassPathZipEntry(zip, canonical_path);
        }
      }
    }
  }
  return nullptr;
}

// The boot append entries are added with a lock, and read lock free.
void ClassLoader::add_to_boot_append_entries(ClassPathEntry *new_entry) {
  if (new_entry != nullptr) {
    MutexLocker ml(Bootclasspath_lock, Mutex::_no_safepoint_check_flag);
    if (_last_append_entry == nullptr) {
      _last_append_entry = new_entry;
      assert(first_append_entry() == nullptr, "boot loader's append class path entry list not empty");
      AtomicAccess::release_store(&_first_append_entry_list, new_entry);
    } else {
      _last_append_entry->set_next(new_entry);
      _last_append_entry = new_entry;
    }
  }
}

// Returns true IFF the file/dir exists and the entry was successfully created.
bool ClassLoader::update_class_path_entry_list(JavaThread* current, const char *path) {
  struct stat st;
  if (os::stat(path, &st) == 0) {
    // File or directory found
    ClassPathEntry* new_entry = nullptr;
    new_entry = create_class_path_entry(current, path, &st);
    if (new_entry == nullptr) {
      return false;
    }

    // Do not reorder the bootclasspath which would break get_system_package().
    // Add new entry to linked list
    add_to_boot_append_entries(new_entry);
    return true;
  } else {
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
    while (e != nullptr) {
      tty->print("%s", e->name());
      e = e->next();
      if (e != nullptr) {
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
  if (_patch_mod_entries != nullptr) {
    print_module_entry_table(_patch_mod_entries);
  }

  // [jimage | exploded modules build]
  if (has_jrt_entry()) {
    // Print the location of the java runtime image
    tty->print("%s ;", _jrt_entry->name());
  } else {
    // Print exploded module build path specifications
    if (_exploded_entries != nullptr) {
      print_module_entry_table(_exploded_entries);
    }
  }

  // appended entries
  e = first_append_entry();
  while (e != nullptr) {
    tty->print("%s ;", e->name());
    e = e->next();
  }
  tty->print_cr("]");
}

void* ClassLoader::dll_lookup(void* lib, const char* name, const char* path) {
  void* func = os::dll_lookup(lib, name);
  if (func == nullptr) {
    char msg[256] = "";
    jio_snprintf(msg, sizeof(msg), "Could not resolve \"%s\"", name);
    vm_exit_during_initialization(msg, path);
  }
  return func;
}

void ClassLoader::load_java_library() {
  assert(CanonicalizeEntry == nullptr, "should not load java library twice");
  if (is_vm_statically_linked()) {
    CanonicalizeEntry = CAST_TO_FN_PTR(canonicalize_fn_t, os::lookup_function("JDK_Canonicalize"));
    assert(CanonicalizeEntry != nullptr, "could not lookup JDK_Canonicalize");
    return;
  }

  void *javalib_handle = os::native_java_library();
  if (javalib_handle == nullptr) {
    vm_exit_during_initialization("Unable to load java library", nullptr);
  }

  CanonicalizeEntry = CAST_TO_FN_PTR(canonicalize_fn_t, dll_lookup(javalib_handle, "JDK_Canonicalize", nullptr));
  assert(CanonicalizeEntry != nullptr, "could not lookup JDK_Canonicalize in java library");
}

void ClassLoader::load_jimage_library() {
  assert(JImageOpen == nullptr, "should not load jimage library twice");

  if (is_vm_statically_linked()) {
      JImageOpen = CAST_TO_FN_PTR(JImageOpen_t, os::lookup_function("JIMAGE_Open"));
      JImageClose = CAST_TO_FN_PTR(JImageClose_t, os::lookup_function("JIMAGE_Close"));
      JImageFindResource = CAST_TO_FN_PTR(JImageFindResource_t, os::lookup_function("JIMAGE_FindResource"));
      JImageGetResource = CAST_TO_FN_PTR(JImageGetResource_t, os::lookup_function("JIMAGE_GetResource"));
      assert(JImageOpen != nullptr && JImageClose != nullptr &&
            JImageFindResource != nullptr && JImageGetResource != nullptr,
            "could not lookup all jimage library functions");
      return;
    }

  char path[JVM_MAXPATHLEN];
  char ebuf[1024];
  void* handle = nullptr;
  if (os::dll_locate_lib(path, sizeof(path), Arguments::get_dll_dir(), "jimage")) {
    handle = os::dll_load(path, ebuf, sizeof ebuf);
  }
  if (handle == nullptr) {
    vm_exit_during_initialization("Unable to load jimage library", path);
  }

  JImageOpen = CAST_TO_FN_PTR(JImageOpen_t, dll_lookup(handle, "JIMAGE_Open", path));
  JImageClose = CAST_TO_FN_PTR(JImageClose_t, dll_lookup(handle, "JIMAGE_Close", path));
  JImageFindResource = CAST_TO_FN_PTR(JImageFindResource_t, dll_lookup(handle, "JIMAGE_FindResource", path));
  JImageGetResource = CAST_TO_FN_PTR(JImageGetResource_t, dll_lookup(handle, "JIMAGE_GetResource", path));
  assert(JImageOpen != nullptr && JImageClose != nullptr &&
        JImageFindResource != nullptr && JImageGetResource != nullptr,
        "could not lookup all jimage library functions in jimage library");
}

int ClassLoader::crc32(int crc, const char* buf, int len) {
  return ZipLibrary::crc32(crc, (const jbyte*)buf, len);
}

oop ClassLoader::get_system_package(const char* name, TRAPS) {
  // Look up the name in the boot loader's package entry table.
  if (name != nullptr) {
    TempNewSymbol package_sym = SymbolTable::new_symbol(name);
    // Look for the package entry in the boot loader's package entry table.
    PackageEntry* package =
      ClassLoaderData::the_null_class_loader_data()->packages()->lookup_only(package_sym);

    // Return null if package does not exist or if no classes in that package
    // have been loaded.
    if (package != nullptr && package->has_loaded_class()) {
      ModuleEntry* module = package->module();
      if (module->location() != nullptr) {
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
  return nullptr;
}

objArrayOop ClassLoader::get_system_packages(TRAPS) {
  ResourceMark rm(THREAD);
  // List of pointers to PackageEntrys that have loaded classes.
  PackageEntryTable* pe_table =
      ClassLoaderData::the_null_class_loader_data()->packages();
  GrowableArray<PackageEntry*>* loaded_class_pkgs = pe_table->get_system_packages();

  // Allocate objArray and fill with java.lang.String
  objArrayOop r = oopFactory::new_objArray(vmClasses::String_klass(),
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
  assert(class_name != nullptr, "invariant");
  assert((int)strlen(class_name) == class_name_len, "invariant");

  static const char class_suffix[] = ".class";
  size_t class_suffix_len = sizeof(class_suffix);

  char* const file_name = NEW_RESOURCE_ARRAY(char,
                                             class_name_len +
                                             class_suffix_len); // includes term null

  strncpy(file_name, class_name, class_name_len);
  strncpy(&file_name[class_name_len], class_suffix, class_suffix_len);

  return file_name;
}

static ClassPathEntry* find_first_module_cpe(ModuleEntry* mod_entry,
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
  return nullptr;
}


// Search the module list for the class file stream based on the file name and java package
ClassFileStream* ClassLoader::search_module_entries(JavaThread* current,
                                                    const GrowableArray<ModuleClassPathList*>* const module_list,
                                                    PackageEntry* pkg_entry, // Java package entry derived from the class name
                                                    const char* const file_name) {
  ClassFileStream* stream = nullptr;

  // Find the defining module in the boot loader's module entry table
  ModuleEntry* mod_entry = (pkg_entry != nullptr) ? pkg_entry->module() : nullptr;

  // If the module system has not defined java.base yet, then
  // classes loaded are assumed to be defined to java.base.
  // When java.base is eventually defined by the module system,
  // all packages of classes that have been previously loaded
  // are verified in ModuleEntryTable::verify_javabase_packages().
  if (!Universe::is_module_initialized() &&
      !ModuleEntryTable::javabase_defined() &&
      mod_entry == nullptr) {
    mod_entry = ModuleEntryTable::javabase_moduleEntry();
  }

  // The module must be a named module
  ClassPathEntry* e = nullptr;
  if (mod_entry != nullptr && mod_entry->is_named()) {
    if (module_list == _exploded_entries) {
      // The exploded build entries can be added to at any time so a lock is
      // needed when searching them.
      assert(!ClassLoader::has_jrt_entry(), "Must be exploded build");
      MutexLocker ml(current, Module_lock);
      e = find_first_module_cpe(mod_entry, module_list);
    } else {
      e = find_first_module_cpe(mod_entry, module_list);
    }
  }

  // Try to load the class from the module's ClassPathEntry list.
  while (e != nullptr) {
    stream = e->open_stream(current, file_name);
    // No context.check is required since CDS is not supported
    // for an exploded modules build or if --patch-module is specified.
    if (nullptr != stream) {
      return stream;
    }
    e = e->next();
  }
  // If the module was located, break out even if the class was not
  // located successfully from that module's ClassPathEntry list.
  // There will not be another valid entry for that module.
  return nullptr;
}

// Called by the boot classloader to load classes
InstanceKlass* ClassLoader::load_class(Symbol* name, PackageEntry* pkg_entry, bool search_append_only, TRAPS) {
  assert(name != nullptr, "invariant");

  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);

  const char* const class_name = name->as_C_string();

  EventMarkClassLoading m("Loading class %s", class_name);

  const char* const file_name = file_name_for_class_name(class_name,
                                                         name->utf8_length());
  assert(file_name != nullptr, "invariant");

  // Lookup stream for parsing .class file
  ClassFileStream* stream = nullptr;
  s2 classpath_index = 0;
  ClassPathEntry* e = nullptr;

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
  if (_patch_mod_entries != nullptr && !search_append_only) {
    assert(!CDSConfig::is_dumping_archive(), "CDS doesn't support --patch-module during dumping");
    stream = search_module_entries(THREAD, _patch_mod_entries, pkg_entry, file_name);
  }

  // Load Attempt #2: [jimage | exploded build]
  if (!search_append_only && (nullptr == stream)) {
    if (has_jrt_entry()) {
      e = _jrt_entry;
      stream = _jrt_entry->open_stream(THREAD, file_name);
    } else {
      // Exploded build - attempt to locate class in its defining module's location.
      assert(_exploded_entries != nullptr, "No exploded build entries present");
      assert(!CDSConfig::is_dumping_archive(), "CDS dumping doesn't support exploded build");
      stream = search_module_entries(THREAD, _exploded_entries, pkg_entry, file_name);
    }
  }

  // Load Attempt #3: [-Xbootclasspath/a]; [jvmti appended entries]
  if (search_append_only && (nullptr == stream)) {
    // For the boot loader append path search, the starting classpath_index
    // for the appended piece is always 1 to account for either the
    // _jrt_entry or the _exploded_entries.
    assert(classpath_index == 0, "The classpath_index has been incremented incorrectly");
    classpath_index = 1;

    e = first_append_entry();
    while (e != nullptr) {
      stream = e->open_stream(THREAD, file_name);
      if (nullptr != stream) {
        break;
      }
      e = e->next();
      ++classpath_index;
    }
  }

  if (nullptr == stream) {
    return nullptr;
  }

  ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
  Handle protection_domain;
  ClassLoadInfo cl_info(protection_domain);

  InstanceKlass* result = KlassFactory::create_from_stream(stream,
                                                           name,
                                                           loader_data,
                                                           cl_info,
                                                           CHECK_NULL);
  result->set_classpath_index(classpath_index);
  return result;
}

#if INCLUDE_CDS
static const char* skip_uri_protocol(const char* source) {
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

static char decode_percent_encoded(const char *str, size_t& index) {
  if (str[index] == '%'
      && isxdigit(str[index + 1])
      && isxdigit(str[index + 2])) {
    char hex[3];
    hex[0] = str[index + 1];
    hex[1] = str[index + 2];
    hex[2] = '\0';
    index += 2;
    return (char) strtol(hex, nullptr, 16);
  }
  return str[index];
}

char* ClassLoader::uri_to_path(const char* uri) {
  const size_t len = strlen(uri) + 1;
  char* path = NEW_RESOURCE_ARRAY(char, len);

  uri = skip_uri_protocol(uri);

  if (strncmp(uri, "//", 2) == 0) {
    // Skip the empty "authority" part
    uri += 2;
  }

#ifdef _WINDOWS
  if (uri[0] == '/') {
    // Absolute path name on Windows does not begin with a slash
    uri += 1;
  }
#endif

  size_t path_index = 0;
  for (size_t i = 0; i < strlen(uri); ++i) {
    char decoded = decode_percent_encoded(uri, i);
    path[path_index++] = decoded;
  }
  path[path_index] = '\0';
  return path;
}

// Record the shared classpath index and loader type for classes loaded
// by the builtin loaders at dump time.
void ClassLoader::record_result(JavaThread* current, InstanceKlass* ik,
                                const ClassFileStream* stream, bool redefined) {
  assert(CDSConfig::is_dumping_archive(), "sanity");
  assert(stream != nullptr, "sanity");

  if (ik->is_hidden()) {
    record_hidden_class(ik);
    return;
  }

  oop loader = ik->class_loader();
  char* src = (char*)stream->source();
  if (src == nullptr) {
    ik->set_shared_classpath_index(-1); // unsupported location
    return;
  }

  if (!SystemDictionaryShared::is_builtin_loader(ik->class_loader_data())) {
    // A class loaded by a user-defined classloader.
    assert(ik->shared_classpath_index() < 0, "not assigned yet");
    ik->set_shared_classpath_index(UNREGISTERED_INDEX);
    SystemDictionaryShared::set_shared_class_misc_info(ik, (ClassFileStream*)stream);
    return;
  }

  assert(has_jrt_entry(), "CDS dumping does not support exploded JDK build");

  ResourceMark rm(current);
  int classpath_index = -1;
  bool found_invalid = false;

  PackageEntry* pkg_entry = ik->package();

  if (!AOTClassLocationConfig::dumptime_is_ready()) {
    // The shared path table is set up after module system initialization.
    // The path table contains no entry before that. Any classes loaded prior
    // to the setup of the shared path table must be from the modules image.
    assert(stream->from_boot_loader_modules_image(), "stream must be loaded by boot loader from modules image");
    classpath_index = 0;
  } else {
    // Save the path from the file: protocol or the module name from the jrt: protocol
    // if no protocol prefix is found, path is the same as stream->source(). This path
    // must be valid since the class has been successfully parsed.
    const char* path = ClassLoader::uri_to_path(src);
    assert(path != nullptr, "sanity");
    AOTClassLocationConfig::dumptime_iterate([&] (AOTClassLocation* cl) {
      int i = cl->index();
      // for index 0 and the stream->source() is the modules image or has the jrt: protocol.
      // The class must be from the runtime modules image.
      if (cl->is_modules_image() && (stream->from_boot_loader_modules_image() || string_starts_with(src, "jrt:"))) {
        classpath_index = i;
      } else if (os::same_files(cl->path(), path)) {
        // If the path (from the class stream source) is the same as the shared
        // class or module path, then we have a match.
        // src may come from the App/Platform class loaders, which would canonicalize
        // the file name. We cannot use strcmp to check for equality against cs->path().
        // We must use os::same_files (which is faster than canonicalizing cs->path()).

        // null pkg_entry and pkg_entry in an unnamed module implies the class
        // is from the -cp or boot loader append path which consists of -Xbootclasspath/a
        // and jvmti appended entries.
        if ((pkg_entry == nullptr) || (pkg_entry->in_unnamed_module())) {
          // Ensure the index is within the -cp range before assigning
          // to the classpath_index.
          if (SystemDictionary::is_system_class_loader(loader) && cl->from_app_classpath()) {
            classpath_index = i;
          } else {
            if (cl->from_boot_classpath()) {
              if (loader != nullptr) {
                // Probably loaded by jdk/internal/loader/ClassLoaders$BootClassLoader. Don't archive
                // such classes.
                found_invalid = true;
              } else {
                classpath_index = i;
              }
            }
          }
        } else {
          // A class from a named module from the --module-path. Ensure the index is
          // within the --module-path range before assigning to the classpath_index.
          if ((pkg_entry != nullptr) && !(pkg_entry->in_unnamed_module()) && cl->from_module_path()) {
            classpath_index = i;
          }
        }
      }
      if (classpath_index >= 0 || found_invalid) {
        return false; // Break the AOTClassLocationConfig::dumptime_iterate() loop.
      } else {
        return true; // Keep iterating
      }
    });
  }

  if (found_invalid) {
    assert(classpath_index == -1, "sanity");
  }

  const char* const class_name = ik->name()->as_C_string();
  const char* const file_name = file_name_for_class_name(class_name,
                                                         ik->name()->utf8_length());
  assert(file_name != nullptr, "invariant");
  record_result_for_builtin_loader(checked_cast<s2>(classpath_index), ik, redefined);
}

void ClassLoader::record_result_for_builtin_loader(s2 classpath_index, InstanceKlass* result, bool redefined) {
  assert(CDSConfig::is_dumping_archive(), "sanity");

  oop loader = result->class_loader();
  if (SystemDictionary::is_system_class_loader(loader)) {
    AOTClassLocationConfig::dumptime_set_has_app_classes();
  } else if (SystemDictionary::is_platform_class_loader(loader)) {
    AOTClassLocationConfig::dumptime_set_has_platform_classes();
  } else {
    precond(loader == nullptr);
  }

  if (CDSConfig::is_dumping_preimage_static_archive() || CDSConfig::is_dumping_dynamic_archive()) {
    if (!AOTClassLocationConfig::dumptime()->is_valid_classpath_index(classpath_index, result)) {
      classpath_index = -1;
    }
  }

  AOTClassLocationConfig::dumptime_update_max_used_index(classpath_index);
  result->set_shared_classpath_index(classpath_index);
}

void ClassLoader::record_hidden_class(InstanceKlass* ik) {
  assert(ik->is_hidden(), "must be");

  if (ik->defined_by_other_loaders()) {
    // We don't archive hidden classes for non-builtin loaders.
    return;
  }

  if (HeapShared::is_lambda_proxy_klass(ik)) {
    InstanceKlass* nest_host = ik->nest_host_not_null();
    ik->set_shared_classpath_index(nest_host->shared_classpath_index());
  } else if (HeapShared::is_lambda_form_klass(ik)) {
    ik->set_shared_classpath_index(0);
  } else {
    // Generated invoker classes.
    if (ik->defined_by_app_loader()) {
      ik->set_shared_classpath_index(AOTClassLocationConfig::dumptime()->app_cp_start_index());
    } else {
      ik->set_shared_classpath_index(0);
    }
  }
}

void ClassLoader::append_boot_classpath(ClassPathEntry* new_entry) {
  if (CDSConfig::is_using_archive()) {
    warning("Sharing is only supported for boot loader classes because bootstrap classpath has been appended");
    FileMapInfo::current_info()->set_has_platform_or_app_classes(false);
    if (DynamicArchive::is_mapped()) {
      FileMapInfo::dynamic_info()->set_has_platform_or_app_classes(false);
    }
  }
  add_to_boot_append_entries(new_entry);
}
#endif // INCLUDE_CDS

// Initialize the class loader's access to methods in libzip.  Parse and
// process the boot classpath into a list ClassPathEntry objects.  Once
// this list has been created, it must not change order (see class PackageInfo)
// it can be appended to and is by jvmti.

void ClassLoader::initialize(TRAPS) {
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
    NEWPERFEVENTCOUNTER(_unsafe_defineClassCallCounter, SUN_CLS, "unsafeDefineClassCalls");
    NEWPERFTICKCOUNTER(_perf_secondary_hash_time, SUN_CLS, "secondarySuperHashTime");
    NEWPERFTICKCOUNTER(_perf_change_wx_time, SUN_CLS, "changeWXTime");

    if (log_is_enabled(Info, perf, class, link)) {
      NEWPERFTICKCOUNTER(_perf_ik_link_methods_time, SUN_CLS, "linkMethodsTime");
      NEWPERFTICKCOUNTER(_perf_method_adapters_time, SUN_CLS, "makeAdaptersTime");
      NEWPERFEVENTCOUNTER(_perf_ik_link_methods_count, SUN_CLS, "linkMethodsCount");
      NEWPERFEVENTCOUNTER(_perf_method_adapters_count, SUN_CLS, "makeAdaptersCount");

      NEWPERFTICKCOUNTER(_perf_resolve_indy_time, SUN_CLS, "resolve_invokedynamic_time");
      NEWPERFTICKCOUNTER(_perf_resolve_invokehandle_time, SUN_CLS, "resolve_invokehandle_time");
      NEWPERFTICKCOUNTER(_perf_resolve_mh_time, SUN_CLS, "resolve_MethodHandle_time");
      NEWPERFTICKCOUNTER(_perf_resolve_mt_time, SUN_CLS, "resolve_MethodType_time");

      NEWPERFEVENTCOUNTER(_perf_resolve_indy_count, SUN_CLS, "resolve_invokedynamic_count");
      NEWPERFEVENTCOUNTER(_perf_resolve_invokehandle_count, SUN_CLS, "resolve_invokehandle_count");
      NEWPERFEVENTCOUNTER(_perf_resolve_mh_count, SUN_CLS, "resolve_MethodHandle_count");
      NEWPERFEVENTCOUNTER(_perf_resolve_mt_count, SUN_CLS, "resolve_MethodType_count");
    }
  }

  // lookup java library entry points
  load_java_library();
  // jimage library entry points are loaded below, in lookup_vm_options
  setup_bootstrap_search_path(THREAD);
}

static char* lookup_vm_resource(JImageFile *jimage, const char *jimage_version, const char *path) {
  jlong size;
  JImageLocationRef location = (*JImageFindResource)(jimage, "java.base", jimage_version, path, &size);
  if (location == 0)
    return nullptr;
  char *val = NEW_C_HEAP_ARRAY(char, size+1, mtClass);
  (*JImageGetResource)(jimage, location, val, size);
  val[size] = '\0';
  return val;
}

// Lookup VM options embedded in the modules jimage file
char* ClassLoader::lookup_vm_options() {
  jint error;
  char modules_path[JVM_MAXPATHLEN];
  const char* fileSep = os::file_separator();

  // Initialize jimage library entry points
  load_jimage_library();

  jio_snprintf(modules_path, JVM_MAXPATHLEN, "%s%slib%smodules", Arguments::get_java_home(), fileSep, fileSep);
  JImage_file =(*JImageOpen)(modules_path, &error);
  if (JImage_file == nullptr) {
    if (Arguments::has_jimage()) {
      // The modules file exists but is unreadable or corrupt
      vm_exit_during_initialization(err_msg("Unable to load %s", modules_path));
    }
    return nullptr;
  }

  const char *jimage_version = get_jimage_version_string();
  char *options = lookup_vm_resource(JImage_file, jimage_version, "jdk/internal/vm/options");
  return options;
}

bool ClassLoader::is_module_observable(const char* module_name) {
  assert(JImageOpen != nullptr, "jimage library should have been opened");
  if (JImage_file == nullptr) {
    struct stat st;
    const char *path = get_exploded_module_path(module_name, true);
    bool res = os::stat(path, &st) == 0;
    FREE_C_HEAP_ARRAY(char, path);
    return res;
  }
  jlong size;
  const char *jimage_version = get_jimage_version_string();
  return (*JImageFindResource)(JImage_file, module_name, jimage_version, "module-info.class", &size) != 0;
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


void classLoader_init1() {
  EXCEPTION_MARK;
  ClassLoader::initialize(THREAD);
  if (HAS_PENDING_EXCEPTION) {
    vm_exit_during_initialization("ClassLoader::initialize() failed unexpectedly");
  }
}

// Complete the ClassPathEntry setup for the boot loader
void ClassLoader::classLoader_init2(JavaThread* current) {
  // Setup the list of module/path pairs for --patch-module processing
  // This must be done after the SymbolTable is created in order
  // to use fast_compare on module names instead of a string compare.
  if (Arguments::get_patch_mod_prefix() != nullptr) {
    setup_patch_mod_entries();
  }

  // Create the ModuleEntry for java.base (must occur after setup_patch_mod_entries
  // to successfully determine if java.base has been patched)
  create_javabase();

  // Setup the initial java.base/path pair for the exploded build entries.
  // As more modules are defined during module system initialization, more
  // entries will be added to the exploded build array.
  if (!has_jrt_entry()) {
    assert(!CDSConfig::is_dumping_archive(), "not supported with exploded module builds");
    assert(!CDSConfig::is_using_archive(), "UsedSharedSpaces not supported with exploded module builds");
    // Set up the boot loader's _exploded_entries list.  Note that this gets
    // done before loading any classes, by the same thread that will
    // subsequently do the first class load. So, no lock is needed for this.
    assert(_exploded_entries == nullptr, "Should only get initialized once");
    _exploded_entries = new (mtModule)
      GrowableArray<ModuleClassPathList*>(EXPLODED_ENTRY_SIZE, mtModule);
    add_to_exploded_build_list(current, vmSymbols::java_base());
  }
}

char* ClassLoader::get_canonical_path(const char* orig, Thread* thread) {
  assert(orig != nullptr, "bad arguments");
  // caller needs to allocate ResourceMark for the following output buffer
  char* canonical_path = NEW_RESOURCE_ARRAY_IN_THREAD(thread, char, JVM_MAXPATHLEN);
  ResourceMark rm(thread);
  // os::native_path writes into orig_copy
  char* orig_copy = ResourceArea::strdup(thread, orig);
  if ((CanonicalizeEntry)(os::native_path(orig_copy), canonical_path, JVM_MAXPATHLEN) < 0) {
    return nullptr;
  }
  return canonical_path;
}

void ClassLoader::create_javabase() {
  JavaThread* current = JavaThread::current();

  // Create java.base's module entry for the boot
  // class loader prior to loading j.l.Object.
  ClassLoaderData* null_cld = ClassLoaderData::the_null_class_loader_data();

  // Get module entry table
  ModuleEntryTable* null_cld_modules = null_cld->modules();
  if (null_cld_modules == nullptr) {
    vm_exit_during_initialization("No ModuleEntryTable for the boot class loader");
  }

  {
    MutexLocker ml(current, Module_lock);
    if (ModuleEntryTable::javabase_moduleEntry() == nullptr) {  // may have been inited by CDS.
      ModuleEntry* jb_module = null_cld_modules->locked_create_entry(Handle(),
                               false, vmSymbols::java_base(), nullptr, nullptr, null_cld);
      if (jb_module == nullptr) {
        vm_exit_during_initialization("Unable to create ModuleEntry for " JAVA_BASE_NAME);
      }
      ModuleEntryTable::set_javabase_moduleEntry(jb_module);
    }
  }
}

// Please keep following two functions at end of this file. With them placed at top or in middle of the file,
// they could get inlined by aggressive compiler, an unknown trick, see bug 6966589.
void PerfClassTraceTime::initialize() {
  if (!UsePerfData) return;

  if (_eventp != nullptr) {
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

  if (_recursion_counters == nullptr || (_recursion_counters[_event_type])++ == 0) {
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

  if (_recursion_counters != nullptr && --(_recursion_counters[_event_type]) > 0) return;

  // increment the counters only on the leaf call
  _t.stop();
  _timep->inc(_t.ticks());
  if (_selftimep != nullptr) {
    _selftimep->inc(selftime);
  }
  // add all class loading related event selftime to the accumulated time counter
  ClassLoader::perf_accumulated_time()->inc(selftime);

  // reset the timer
  _timers[_event_type].reset();
}
