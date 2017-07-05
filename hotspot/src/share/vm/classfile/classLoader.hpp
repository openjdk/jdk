/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classFileParser.hpp"
#include "runtime/perfData.hpp"

// The VM class loader.
#include <sys/stat.h>


// Meta-index (optional, to be able to skip opening boot classpath jar files)
class MetaIndex: public CHeapObj<mtClass> {
 private:
  char** _meta_package_names;
  int    _num_meta_package_names;
 public:
  MetaIndex(char** meta_package_names, int num_meta_package_names);
  ~MetaIndex();
  bool may_contain(const char* class_name);
};


// Class path entry (directory or zip file)

class ClassPathEntry: public CHeapObj<mtClass> {
 private:
  ClassPathEntry* _next;
 public:
  // Next entry in class path
  ClassPathEntry* next()              { return _next; }
  void set_next(ClassPathEntry* next) {
    // may have unlocked readers, so write atomically.
    OrderAccess::release_store_ptr(&_next, next);
  }
  virtual bool is_jar_file() = 0;
  virtual const char* name() = 0;
  virtual bool is_lazy();
  // Constructor
  ClassPathEntry();
  // Attempt to locate file_name through this class path entry.
  // Returns a class file parsing stream if successfull.
  virtual ClassFileStream* open_stream(const char* name, TRAPS) = 0;
  // Debugging
  NOT_PRODUCT(virtual void compile_the_world(Handle loader, TRAPS) = 0;)
  NOT_PRODUCT(virtual bool is_rt_jar() = 0;)
};


class ClassPathDirEntry: public ClassPathEntry {
 private:
  char* _dir;           // Name of directory
 public:
  bool is_jar_file()  { return false;  }
  const char* name()  { return _dir; }
  ClassPathDirEntry(char* dir);
  ClassFileStream* open_stream(const char* name, TRAPS);
  // Debugging
  NOT_PRODUCT(void compile_the_world(Handle loader, TRAPS);)
  NOT_PRODUCT(bool is_rt_jar();)
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
  jzfile* _zip;        // The zip archive
  char*   _zip_name;   // Name of zip archive
 public:
  bool is_jar_file()  { return true;  }
  const char* name()  { return _zip_name; }
  ClassPathZipEntry(jzfile* zip, const char* zip_name);
  ~ClassPathZipEntry();
  ClassFileStream* open_stream(const char* name, TRAPS);
  void contents_do(void f(const char* name, void* context), void* context);
  // Debugging
  NOT_PRODUCT(void compile_the_world(Handle loader, TRAPS);)
  NOT_PRODUCT(void compile_the_world12(Handle loader, TRAPS);) // JDK 1.2 version
  NOT_PRODUCT(void compile_the_world13(Handle loader, TRAPS);) // JDK 1.3 version
  NOT_PRODUCT(bool is_rt_jar();)
  NOT_PRODUCT(bool is_rt_jar12();)
  NOT_PRODUCT(bool is_rt_jar13();)
};


// For lazier loading of boot class path entries
class LazyClassPathEntry: public ClassPathEntry {
 private:
  char* _path; // dir or file
  struct stat _st;
  MetaIndex* _meta_index;
  bool _has_error;
  volatile ClassPathEntry* _resolved_entry;
  ClassPathEntry* resolve_entry(TRAPS);
 public:
  bool is_jar_file();
  const char* name()  { return _path; }
  LazyClassPathEntry(char* path, const struct stat* st);
  ClassFileStream* open_stream(const char* name, TRAPS);
  void set_meta_index(MetaIndex* meta_index) { _meta_index = meta_index; }
  virtual bool is_lazy();
  // Debugging
  NOT_PRODUCT(void compile_the_world(Handle loader, TRAPS);)
  NOT_PRODUCT(bool is_rt_jar();)
};

class PackageHashtable;
class PackageInfo;
template <MEMFLAGS F> class HashtableBucket;

class ClassLoader: AllStatic {
 public:
  enum SomeConstants {
    package_hash_table_size = 31  // Number of buckets
  };
 private:
  friend class LazyClassPathEntry;

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

  // First entry in linked list of ClassPathEntry instances
  static ClassPathEntry* _first_entry;
  // Last entry in linked list of ClassPathEntry instances
  static ClassPathEntry* _last_entry;
  // Hash table used to keep track of loaded packages
  static PackageHashtable* _package_hash_table;
  static const char* _shared_archive;

  // Hash function
  static unsigned int hash(const char *s, int n);
  // Returns the package file name corresponding to the specified package
  // or class name, or null if not found.
  static PackageInfo* lookup_package(const char *pkgname);
  // Adds a new package entry for the specified class or package name and
  // corresponding directory or jar file name.
  static bool add_package(const char *pkgname, int classpath_index, TRAPS);

  // Initialization
  static void setup_meta_index();
  static void setup_bootstrap_search_path();
  static void load_zip_library();
  static ClassPathEntry* create_class_path_entry(char *path, const struct stat* st,
                                                 bool lazy, TRAPS);

  // Canonicalizes path names, so strcmp will work properly. This is mainly
  // to avoid confusing the zip library
  static bool get_canonical_path(char* orig, char* out, int len);
 public:
  // Used by the kernel jvm.
  static void update_class_path_entry_list(char *path,
                                           bool check_for_duplicates);
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

  // Load individual .class file
  static instanceKlassHandle load_classfile(Symbol* h_name, TRAPS);

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
  static void create_package_info_table();
  static void create_package_info_table(HashtableBucket<mtClass> *t, int length,
                                        int number_of_entries);
  static int compute_Object_vtable();

  static ClassPathEntry* classpath_entry(int n) {
    ClassPathEntry* e = ClassLoader::_first_entry;
    while (--n >= 0) {
      assert(e != NULL, "Not that many classpath entries.");
      e = e->next();
    }
    return e;
  }

  // Sharing dump and restore
  static void copy_package_info_buckets(char** top, char* end);
  static void copy_package_info_table(char** top, char* end);

  // VM monitoring and management support
  static jlong classloader_time_ms();
  static jlong class_method_total_size();
  static jlong class_init_count();
  static jlong class_init_time_ms();
  static jlong class_verify_time_ms();
  static jlong class_link_count();
  static jlong class_link_time_ms();

  // indicates if class path already contains a entry (exact match by name)
  static bool contains_entry(ClassPathEntry* entry);

  // adds a class path list
  static void add_to_list(ClassPathEntry* new_entry);

  // creates a class path zip entry (returns NULL if JAR file cannot be opened)
  static ClassPathZipEntry* create_class_path_zip_entry(const char *apath);

  // Debugging
  static void verify()              PRODUCT_RETURN;

  // Force compilation of all methods in all classes in bootstrap class path (stress test)
#ifndef PRODUCT
 private:
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
