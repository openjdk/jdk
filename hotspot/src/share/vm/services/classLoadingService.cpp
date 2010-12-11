/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "memory/allocation.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "services/classLoadingService.hpp"
#include "services/memoryService.hpp"
#include "utilities/dtrace.hpp"

#ifdef DTRACE_ENABLED

// Only bother with this argument setup if dtrace is available

HS_DTRACE_PROBE_DECL4(hotspot, class__loaded, char*, int, oop, bool);
HS_DTRACE_PROBE_DECL4(hotspot, class__unloaded, char*, int, oop, bool);

#define DTRACE_CLASSLOAD_PROBE(type, clss, shared)  \
  {                                                 \
    char* data = NULL;                              \
    int len = 0;                                    \
    symbolOop name = (clss)->name();                \
    if (name != NULL) {                             \
      data = (char*)name->bytes();                  \
      len = name->utf8_length();                    \
    }                                               \
    HS_DTRACE_PROBE4(hotspot, class__##type,        \
      data, len, (clss)->class_loader(), (shared)); \
  }

#else //  ndef DTRACE_ENABLED

#define DTRACE_CLASSLOAD_PROBE(type, clss, shared)

#endif

// counters for classes loaded from class files
PerfCounter*    ClassLoadingService::_classes_loaded_count = NULL;
PerfCounter*    ClassLoadingService::_classes_unloaded_count = NULL;
PerfCounter*    ClassLoadingService::_classbytes_loaded = NULL;
PerfCounter*    ClassLoadingService::_classbytes_unloaded = NULL;

// counters for classes loaded from shared archive
PerfCounter*    ClassLoadingService::_shared_classes_loaded_count = NULL;
PerfCounter*    ClassLoadingService::_shared_classes_unloaded_count = NULL;
PerfCounter*    ClassLoadingService::_shared_classbytes_loaded = NULL;
PerfCounter*    ClassLoadingService::_shared_classbytes_unloaded = NULL;
PerfVariable*   ClassLoadingService::_class_methods_size = NULL;

void ClassLoadingService::init() {
  EXCEPTION_MARK;

  // These counters are for java.lang.management API support.
  // They are created even if -XX:-UsePerfData is set and in
  // that case, they will be allocated on C heap.
  _classes_loaded_count =
                 PerfDataManager::create_counter(JAVA_CLS, "loadedClasses",
                                                 PerfData::U_Events, CHECK);

  _classes_unloaded_count =
                 PerfDataManager::create_counter(JAVA_CLS, "unloadedClasses",
                                                 PerfData::U_Events, CHECK);

  _shared_classes_loaded_count =
                 PerfDataManager::create_counter(JAVA_CLS, "sharedLoadedClasses",
                                                 PerfData::U_Events, CHECK);

  _shared_classes_unloaded_count =
                 PerfDataManager::create_counter(JAVA_CLS, "sharedUnloadedClasses",
                                                 PerfData::U_Events, CHECK);

  if (UsePerfData) {
    _classbytes_loaded =
                 PerfDataManager::create_counter(SUN_CLS, "loadedBytes",
                                                 PerfData::U_Bytes, CHECK);

    _classbytes_unloaded =
                 PerfDataManager::create_counter(SUN_CLS, "unloadedBytes",
                                                 PerfData::U_Bytes, CHECK);
    _shared_classbytes_loaded =
                 PerfDataManager::create_counter(SUN_CLS, "sharedLoadedBytes",
                                                 PerfData::U_Bytes, CHECK);

    _shared_classbytes_unloaded =
                 PerfDataManager::create_counter(SUN_CLS, "sharedUnloadedBytes",
                                                 PerfData::U_Bytes, CHECK);
    _class_methods_size =
                 PerfDataManager::create_variable(SUN_CLS, "methodBytes",
                                                  PerfData::U_Bytes, CHECK);
  }
}

void ClassLoadingService::notify_class_unloaded(instanceKlass* k) {
  DTRACE_CLASSLOAD_PROBE(unloaded, k, false);
  // Classes that can be unloaded must be non-shared
  _classes_unloaded_count->inc();

  if (UsePerfData) {
    // add the class size
    size_t size = compute_class_size(k);
    _classbytes_unloaded->inc(size);

    // Compute method size & subtract from running total.
    // We are called during phase 1 of mark sweep, so it's
    // still ok to iterate through methodOops here.
    objArrayOop methods = k->methods();
    for (int i = 0; i < methods->length(); i++) {
      _class_methods_size->inc(-methods->obj_at(i)->size());
    }
  }

  if (TraceClassUnloading) {
    ResourceMark rm;
    tty->print_cr("[Unloading class %s]", k->external_name());
  }
}

void ClassLoadingService::notify_class_loaded(instanceKlass* k, bool shared_class) {
  DTRACE_CLASSLOAD_PROBE(loaded, k, shared_class);
  PerfCounter* classes_counter = (shared_class ? _shared_classes_loaded_count
                                               : _classes_loaded_count);
  // increment the count
  classes_counter->inc();

  if (UsePerfData) {
    PerfCounter* classbytes_counter = (shared_class ? _shared_classbytes_loaded
                                                    : _classbytes_loaded);
    // add the class size
    size_t size = compute_class_size(k);
    classbytes_counter->inc(size);
  }
}

size_t ClassLoadingService::compute_class_size(instanceKlass* k) {
  // lifted from ClassStatistics.do_class(klassOop k)

  size_t class_size = 0;

  class_size += k->as_klassOop()->size();

  if (k->oop_is_instance()) {
    class_size += k->methods()->size();
    class_size += k->constants()->size();
    class_size += k->local_interfaces()->size();
    class_size += k->transitive_interfaces()->size();
    // We do not have to count implementors, since we only store one!
    class_size += k->fields()->size();
  }
  return class_size * oopSize;
}


bool ClassLoadingService::set_verbose(bool verbose) {
  MutexLocker m(Management_lock);

  // verbose will be set to the previous value
  bool succeed = CommandLineFlags::boolAtPut((char*)"TraceClassLoading", &verbose, MANAGEMENT);
  assert(succeed, "Setting TraceClassLoading flag fails");
  reset_trace_class_unloading();

  return verbose;
}

// Caller to this function must own Management_lock
void ClassLoadingService::reset_trace_class_unloading() {
  assert(Management_lock->owned_by_self(), "Must own the Management_lock");
  bool value = MemoryService::get_verbose() || ClassLoadingService::get_verbose();
  bool succeed = CommandLineFlags::boolAtPut((char*)"TraceClassUnloading", &value, MANAGEMENT);
  assert(succeed, "Setting TraceClassUnLoading flag fails");
}

GrowableArray<KlassHandle>* LoadedClassesEnumerator::_loaded_classes = NULL;
Thread* LoadedClassesEnumerator::_current_thread = NULL;

LoadedClassesEnumerator::LoadedClassesEnumerator(Thread* cur_thread) {
  assert(cur_thread == Thread::current(), "Check current thread");

  int init_size = ClassLoadingService::loaded_class_count();
  _klass_handle_array = new GrowableArray<KlassHandle>(init_size);

  // For consistency of the loaded classes, grab the SystemDictionary lock
  MutexLocker sd_mutex(SystemDictionary_lock);

  // Set _loaded_classes and _current_thread and begin enumerating all classes.
  // Only one thread will do the enumeration at a time.
  // These static variables are needed and they are used by the static method
  // add_loaded_class called from classes_do().
  _loaded_classes = _klass_handle_array;
  _current_thread = cur_thread;

  SystemDictionary::classes_do(&add_loaded_class);

  // FIXME: Exclude array klasses for now
  // Universe::basic_type_classes_do(&add_loaded_class);
}
