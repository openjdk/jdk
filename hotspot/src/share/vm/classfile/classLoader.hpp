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

#ifndef SHARE_VM_CLASSFILE_CLASSLOADER_HPP
#define SHARE_VM_CLASSFILE_CLASSLOADER_HPP

#include "runtime/orderAccess.hpp"
#include "runtime/perfData.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/macros.hpp"

// The VM class loader.
#include <sys/stat.h>

// Name of boot "modules" image
#define  MODULES_IMAGE_NAME "modules"

// Name of the resource containing mapping from module names to defining class loader type
#define MODULE_LOADER_MAP "jdk/internal/vm/cds/resources/ModuleLoaderMap.dat"

// Initial sizes of the following arrays are based on the generated ModuleLoaderMap.dat
#define INITIAL_BOOT_MODULES_ARRAY_SIZE 30
#define INITIAL_PLATFORM_MODULES_ARRAY_SIZE  15

// Class path entry (directory or zip file)

class JImageFile;
class ClassFileStream;

class ClassPathEntry : public CHeapObj<mtClass> {
private:
  ClassPathEntry* _next;
public:
  // Next entry in class path
  ClassPathEntry* next() const { return _next; }
  void set_next(ClassPathEntry* next) {
    // may have unlocked readers, so write atomically.
    OrderAccess::release_store_ptr(&_next, next);
  }
  virtual bool is_jrt() = 0;
  virtual bool is_jar_file() const = 0;
  virtual const char* name() const = 0;
  virtual JImageFile* jimage() const = 0;
  // Constructor
  ClassPathEntry() : _next(NULL) {}
  // Attempt to locate file_name through this class path entry.
  // Returns a class file parsing stream if successfull.
  virtual ClassFileStream* open_stream(const char* name, TRAPS) = 0;
  // Debugging
  NOT_PRODUCT(virtual void compile_the_world(Handle loader, TRAPS) = 0;)
};

class ClassPathDirEntry: public ClassPathEntry {
 private:
  const char* _dir;           // Name of directory
 public:
  bool is_jrt()            { return false; }
  bool is_jar_file() const { return false;  }
  const char* name() const { return _dir; }
  JImageFile* jimage() const { return NULL; }
  ClassPathDirEntry(const char* dir);
  ClassFileStream* open_stream(const char* name, TRAPS);
  // Debugging
  NOT_PRODUCT(void compile_the_world(Handle loader, TRAPS);)
};


// Type definitions for zip file and zip file entry
typedef void* jzfile;
typedef struct {
  char *name;                   /* entry name */
  jlong time;                   /* modification time */
  jlong size;                   /* size of uncompressed data */
  jlong csize;                  /* size of compressed data (zero if uncompressed) */
  jint crc;                     /* crc of uncompressed data */
  char *comment;                /* optional zip file comment */
  jbyte *extra;                 /* optional extra data */
  jlong pos;                    /* position of LOC header (if negative) or data */
} jzentry;


class ClassPathZipEntry: public ClassPathEntry {
 private:
  jzfile* _zip;              // The zip archive
  const char*   _zip_name;   // Name of zip archive
 public:
  bool is_jrt()            { return false; }
  bool is_jar_file() const { return true;  }
  const char* name() const { return _zip_name; }
  JImageFile* jimage() const { return NULL; }
  ClassPathZipEntry(jzfile* zip, const char* zip_name);
  ~ClassPathZipEntry();
  u1* open_entry(const char* name, jint* filesize, bool nul_terminate, TRAPS);
  ClassFileStream* open_stream(const char* name, TRAPS);
  void contents_do(void f(const char* name, void* context), void* context);
  // Debugging
  NOT_PRODUCT(void compile_the_world(Handle loader, TRAPS);)
};


// For java image files
class ClassPathImageEntry: public ClassPathEntry {
private:
  JImageFile* _jimage;
  const char* _name;
public:
  bool is_jrt();
  bool is_jar_file() const { return false; }
  bool is_open() const { return _jimage != NULL; }
  const char* name() const { return _name == NULL ? "" : _name; }
  JImageFile* jimage() const { return _jimage; }
  ClassPathImageEntry(JImageFile* jimage, const char* name);
  ~ClassPathImageEntry();
  void name_to_package(const char* name, char* package, int length);
  ClassFileStream* open_stream(const char* name, TRAPS);

  // Debugging
  NOT_PRODUCT(void compile_the_world(Handle loader, TRAPS);)
};

class SharedPathsMiscInfo;

class ClassLoader: AllStatic {
 public:
  enum ClassLoaderType {
    BOOT_LOADER = 1,      /* boot loader */
    PLATFORM_LOADER  = 2, /* PlatformClassLoader */
    APP_LOADER  = 3       /* AppClassLoader */
  };
 protected:

  // Performance counters
  static PerfCounter* _perf_accumulated_time;
  static PerfCounter* _perf_classes_inited;
  static PerfCounter* _perf_class_init_time;
  static PerfCounter* _perf_class_init_selftime;
  static PerfCounter* _perf_classes_verified;
  static PerfCounter* _perf_class_verify_time;
  static PerfCounter* _perf_class_verify_selftime;
  static PerfCounter* _perf_classes_linked;
  static PerfCounter* _perf_class_link_time;
  static PerfCounter* _perf_class_link_selftime;
  static PerfCounter* _perf_class_parse_time;
  static PerfCounter* _perf_class_parse_selftime;
  static PerfCounter* _perf_sys_class_lookup_time;
  static PerfCounter* _perf_shared_classload_time;
  static PerfCounter* _perf_sys_classload_time;
  static PerfCounter* _perf_app_classload_time;
  static PerfCounter* _perf_app_classload_selftime;
  static PerfCounter* _perf_app_classload_count;
  static PerfCounter* _perf_define_appclasses;
  static PerfCounter* _perf_define_appclass_time;
  static PerfCounter* _perf_define_appclass_selftime;
  static PerfCounter* _perf_app_classfile_bytes_read;
  static PerfCounter* _perf_sys_classfile_bytes_read;

  static PerfCounter* _sync_systemLoaderLockContentionRate;
  static PerfCounter* _sync_nonSystemLoaderLockContentionRate;
  static PerfCounter* _sync_JVMFindLoadedClassLockFreeCounter;
  static PerfCounter* _sync_JVMDefineClassLockFreeCounter;
  static PerfCounter* _sync_JNIDefineClassLockFreeCounter;

  static PerfCounter* _unsafe_defineClassCallCounter;
  static PerfCounter* _isUnsyncloadClass;
  static PerfCounter* _load_instance_class_failCounter;

  // First entry in linked list of ClassPathEntry instances.
  // This consists of entries made up by:
  //   - boot loader modules
  //     [-Xpatch]; exploded build | modules;
  //   - boot loader append path
  //     [-Xbootclasspath/a]; [jvmti appended entries]
  static ClassPathEntry* _first_entry;
  // Last entry in linked list of ClassPathEntry instances
  static ClassPathEntry* _last_entry;
  static int _num_entries;

  // Pointer into the linked list of ClassPathEntry instances.
  // Marks the start of:
  //   - the boot loader's append path
  //     [-Xbootclasspath/a]; [jvmti appended entries]
  static ClassPathEntry* _first_append_entry;

  static const char* _shared_archive;

  // True if the boot path has a "modules" jimage
  static bool _has_jimage;

  // Array of module names associated with the boot class loader
  CDS_ONLY(static GrowableArray<char*>* _boot_modules_array;)

  // Array of module names associated with the platform class loader
  CDS_ONLY(static GrowableArray<char*>* _platform_modules_array;)

  // Info used by CDS
  CDS_ONLY(static SharedPathsMiscInfo * _shared_paths_misc_info;)

  // Initialization
  static void setup_bootstrap_search_path();
  static void setup_search_path(const char *class_path, bool setting_bootstrap);

  static void load_zip_library();
  static void load_jimage_library();
  static ClassPathEntry* create_class_path_entry(const char *path, const struct stat* st,
                                                 bool throw_exception, TRAPS);

 public:

  // If the package for the fully qualified class name is in the boot
  // loader's package entry table then add_package() sets the classpath_index
  // field so that get_system_package() will know to return a non-null value
  // for the package's location.  And, so that the package will be added to
  // the list of packages returned by get_system_packages().
  // For packages whose classes are loaded from the boot loader class path, the
  // classpath_index indicates which entry on the boot loader class path.
  static bool add_package(const char *fullq_class_name, s2 classpath_index, TRAPS);

  // Canonicalizes path names, so strcmp will work properly. This is mainly
  // to avoid confusing the zip library
  static bool get_canonical_path(const char* orig, char* out, int len);
  static const char* file_name_for_class_name(const char* class_name,
                                              int class_name_len);

 public:
  static jboolean decompress(void *in, u8 inSize, void *out, u8 outSize, char **pmsg);
  static int crc32(int crc, const char* buf, int len);
  static bool update_class_path_entry_list(const char *path,
                                           bool check_for_duplicates,
                                           bool mark_append_entry,
                                           bool prepend_entry,
                                           bool throw_exception=true);
  static void print_bootclasspath();

  // Timing
  static PerfCounter* perf_accumulated_time()         { return _perf_accumulated_time; }
  static PerfCounter* perf_classes_inited()           { return _perf_classes_inited; }
  static PerfCounter* perf_class_init_time()          { return _perf_class_init_time; }
  static PerfCounter* perf_class_init_selftime()      { return _perf_class_init_selftime; }
  static PerfCounter* perf_classes_verified()         { return _perf_classes_verified; }
  static PerfCounter* perf_class_verify_time()        { return _perf_class_verify_time; }
  static PerfCounter* perf_class_verify_selftime()    { return _perf_class_verify_selftime; }
  static PerfCounter* perf_classes_linked()           { return _perf_classes_linked; }
  static PerfCounter* perf_class_link_time()          { return _perf_class_link_time; }
  static PerfCounter* perf_class_link_selftime()      { return _perf_class_link_selftime; }
  static PerfCounter* perf_class_parse_time()         { return _perf_class_parse_time; }
  static PerfCounter* perf_class_parse_selftime()     { return _perf_class_parse_selftime; }
  static PerfCounter* perf_sys_class_lookup_time()    { return _perf_sys_class_lookup_time; }
  static PerfCounter* perf_shared_classload_time()    { return _perf_shared_classload_time; }
  static PerfCounter* perf_sys_classload_time()       { return _perf_sys_classload_time; }
  static PerfCounter* perf_app_classload_time()       { return _perf_app_classload_time; }
  static PerfCounter* perf_app_classload_selftime()   { return _perf_app_classload_selftime; }
  static PerfCounter* perf_app_classload_count()      { return _perf_app_classload_count; }
  static PerfCounter* perf_define_appclasses()        { return _perf_define_appclasses; }
  static PerfCounter* perf_define_appclass_time()     { return _perf_define_appclass_time; }
  static PerfCounter* perf_define_appclass_selftime() { return _perf_define_appclass_selftime; }
  static PerfCounter* perf_app_classfile_bytes_read() { return _perf_app_classfile_bytes_read; }
  static PerfCounter* perf_sys_classfile_bytes_read() { return _perf_sys_classfile_bytes_read; }

  // Record how often system loader lock object is contended
  static PerfCounter* sync_systemLoaderLockContentionRate() {
    return _sync_systemLoaderLockContentionRate;
  }

  // Record how often non system loader lock object is contended
  static PerfCounter* sync_nonSystemLoaderLockContentionRate() {
    return _sync_nonSystemLoaderLockContentionRate;
  }

  // Record how many calls to JVM_FindLoadedClass w/o holding a lock
  static PerfCounter* sync_JVMFindLoadedClassLockFreeCounter() {
    return _sync_JVMFindLoadedClassLockFreeCounter;
  }

  // Record how many calls to JVM_DefineClass w/o holding a lock
  static PerfCounter* sync_JVMDefineClassLockFreeCounter() {
    return _sync_JVMDefineClassLockFreeCounter;
  }

  // Record how many calls to jni_DefineClass w/o holding a lock
  static PerfCounter* sync_JNIDefineClassLockFreeCounter() {
    return _sync_JNIDefineClassLockFreeCounter;
  }

  // Record how many calls to Unsafe_DefineClass
  static PerfCounter* unsafe_defineClassCallCounter() {
    return _unsafe_defineClassCallCounter;
  }

  // Record how many times SystemDictionary::load_instance_class call
  // fails with linkageError when Unsyncloadclass flag is set.
  static PerfCounter* load_instance_class_failCounter() {
    return _load_instance_class_failCounter;
  }

  // Sets _has_jimage to TRUE if "modules" jimage file exists
  static void set_has_jimage(bool val) {
    _has_jimage = val;
  }

  static bool has_jimage() { return _has_jimage; }

  // Create the ModuleEntry for java.base
  static void create_javabase();

  // Load individual .class file
  static instanceKlassHandle load_class(Symbol* class_name, bool search_append_only, TRAPS);

  // If the specified package has been loaded by the system, then returns
  // the name of the directory or ZIP file that the package was loaded from.
  // Returns null if the package was not loaded.
  // Note: The specified name can either be the name of a class or package.
  // If a package name is specified, then it must be "/"-separator and also
  // end with a trailing "/".
  static oop get_system_package(const char* name, TRAPS);

  // Returns an array of Java strings representing all of the currently
  // loaded system packages.
  // Note: The package names returned are "/"-separated and end with a
  // trailing "/".
  static objArrayOop get_system_packages(TRAPS);

  // Initialization
  static void initialize();
  CDS_ONLY(static void initialize_shared_path();)

  static int compute_Object_vtable();

  static ClassPathEntry* classpath_entry(int n) {
    ClassPathEntry* e = ClassLoader::_first_entry;
    while (--n >= 0) {
      assert(e != NULL, "Not that many classpath entries.");
      e = e->next();
    }
    return e;
  }

#if INCLUDE_CDS
  // Sharing dump and restore

  static void  check_shared_classpath(const char *path);
  static void  finalize_shared_paths_misc_info();
  static int   get_shared_paths_misc_info_size();
  static void* get_shared_paths_misc_info();
  static bool  check_shared_paths_misc_info(void* info, int size);
  static void  exit_with_path_failure(const char* error, const char* message);

  static s2 module_to_classloader(const char* module_name);
  static void initialize_module_loader_map(JImageFile* jimage);
#endif
  static s2 classloader_type(Symbol* class_name, ClassPathEntry* e,
                                 int classpath_index, TRAPS);

  static void  trace_class_path(const char* msg, const char* name = NULL);

  // VM monitoring and management support
  static jlong classloader_time_ms();
  static jlong class_method_total_size();
  static jlong class_init_count();
  static jlong class_init_time_ms();
  static jlong class_verify_time_ms();
  static jlong class_link_count();
  static jlong class_link_time_ms();

  static void set_first_append_entry(ClassPathEntry* entry);

  // indicates if class path already contains a entry (exact match by name)
  static bool contains_entry(ClassPathEntry* entry);

  // adds a class path list
  static void add_to_list(ClassPathEntry* new_entry);

  // prepends a class path list
  static void prepend_to_list(ClassPathEntry* new_entry);

  // creates a class path zip entry (returns NULL if JAR file cannot be opened)
  static ClassPathZipEntry* create_class_path_zip_entry(const char *apath);

  // add a path to class path list
  static void add_to_list(const char* apath);

  // prepend a path to class path list
  static void prepend_to_list(const char* apath);

  static bool string_ends_with(const char* str, const char* str_to_find);

  static bool is_jrt(const char* name) { return string_ends_with(name, MODULES_IMAGE_NAME); }

  // Debugging
  static void verify()              PRODUCT_RETURN;

  // Force compilation of all methods in all classes in bootstrap class path (stress test)
#ifndef PRODUCT
 protected:
  static int _compile_the_world_class_counter;
  static int _compile_the_world_method_counter;
 public:
  static void compile_the_world();
  static void compile_the_world_in(char* name, Handle loader, TRAPS);
  static int  compile_the_world_counter() { return _compile_the_world_class_counter; }
#endif //PRODUCT
};

// PerfClassTraceTime is used to measure time for class loading related events.
// This class tracks cumulative time and exclusive time for specific event types.
// During the execution of one event, other event types (e.g. class loading and
// resolution) as well as recursive calls of the same event type could happen.
// Only one elapsed timer (cumulative) and one thread-local self timer (exclusive)
// (i.e. only one event type) are active at a time even multiple PerfClassTraceTime
// instances have been created as multiple events are happening.
class PerfClassTraceTime {
 public:
  enum {
    CLASS_LOAD   = 0,
    PARSE_CLASS  = 1,
    CLASS_LINK   = 2,
    CLASS_VERIFY = 3,
    CLASS_CLINIT = 4,
    DEFINE_CLASS = 5,
    EVENT_TYPE_COUNT = 6
  };
 protected:
  // _t tracks time from initialization to destruction of this timer instance
  // including time for all other event types, and recursive calls of this type.
  // When a timer is called recursively, the elapsedTimer _t would not be used.
  elapsedTimer     _t;
  PerfLongCounter* _timep;
  PerfLongCounter* _selftimep;
  PerfLongCounter* _eventp;
  // pointer to thread-local recursion counter and timer array
  // The thread_local timers track cumulative time for specific event types
  // exclusive of time for other event types, but including recursive calls
  // of the same type.
  int*             _recursion_counters;
  elapsedTimer*    _timers;
  int              _event_type;
  int              _prev_active_event;

 public:

  inline PerfClassTraceTime(PerfLongCounter* timep,     /* counter incremented with inclusive time */
                            PerfLongCounter* selftimep, /* counter incremented with exclusive time */
                            PerfLongCounter* eventp,    /* event counter */
                            int* recursion_counters,    /* thread-local recursion counter array */
                            elapsedTimer* timers,       /* thread-local timer array */
                            int type                    /* event type */ ) :
      _timep(timep), _selftimep(selftimep), _eventp(eventp), _recursion_counters(recursion_counters), _timers(timers), _event_type(type) {
    initialize();
  }

  inline PerfClassTraceTime(PerfLongCounter* timep,     /* counter incremented with inclusive time */
                            elapsedTimer* timers,       /* thread-local timer array */
                            int type                    /* event type */ ) :
      _timep(timep), _selftimep(NULL), _eventp(NULL), _recursion_counters(NULL), _timers(timers), _event_type(type) {
    initialize();
  }

  inline void suspend() { _t.stop(); _timers[_event_type].stop(); }
  inline void resume()  { _t.start(); _timers[_event_type].start(); }

  ~PerfClassTraceTime();
  void initialize();
};

#endif // SHARE_VM_CLASSFILE_CLASSLOADER_HPP
