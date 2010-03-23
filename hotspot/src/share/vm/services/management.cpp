/*
 * Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_management.cpp.incl"

PerfVariable* Management::_begin_vm_creation_time = NULL;
PerfVariable* Management::_end_vm_creation_time = NULL;
PerfVariable* Management::_vm_init_done_time = NULL;

klassOop Management::_sensor_klass = NULL;
klassOop Management::_threadInfo_klass = NULL;
klassOop Management::_memoryUsage_klass = NULL;
klassOop Management::_memoryPoolMXBean_klass = NULL;
klassOop Management::_memoryManagerMXBean_klass = NULL;
klassOop Management::_garbageCollectorMXBean_klass = NULL;
klassOop Management::_managementFactory_klass = NULL;

jmmOptionalSupport Management::_optional_support = {0};
TimeStamp Management::_stamp;

void management_init() {
  Management::init();
  ThreadService::init();
  RuntimeService::init();
  ClassLoadingService::init();
}

void Management::init() {
  EXCEPTION_MARK;

  // These counters are for java.lang.management API support.
  // They are created even if -XX:-UsePerfData is set and in
  // that case, they will be allocated on C heap.

  _begin_vm_creation_time =
            PerfDataManager::create_variable(SUN_RT, "createVmBeginTime",
                                             PerfData::U_None, CHECK);

  _end_vm_creation_time =
            PerfDataManager::create_variable(SUN_RT, "createVmEndTime",
                                             PerfData::U_None, CHECK);

  _vm_init_done_time =
            PerfDataManager::create_variable(SUN_RT, "vmInitDoneTime",
                                             PerfData::U_None, CHECK);

  // Initialize optional support
  _optional_support.isLowMemoryDetectionSupported = 1;
  _optional_support.isCompilationTimeMonitoringSupported = 1;
  _optional_support.isThreadContentionMonitoringSupported = 1;

  if (os::is_thread_cpu_time_supported()) {
    _optional_support.isCurrentThreadCpuTimeSupported = 1;
    _optional_support.isOtherThreadCpuTimeSupported = 1;
  } else {
    _optional_support.isCurrentThreadCpuTimeSupported = 0;
    _optional_support.isOtherThreadCpuTimeSupported = 0;
  }
  _optional_support.isBootClassPathSupported = 1;
  _optional_support.isObjectMonitorUsageSupported = 1;
#ifndef SERVICES_KERNEL
  // This depends on the heap inspector
  _optional_support.isSynchronizerUsageSupported = 1;
#endif // SERVICES_KERNEL
}

void Management::initialize(TRAPS) {
  // Start the low memory detector thread
  LowMemoryDetector::initialize();

  if (ManagementServer) {
    ResourceMark rm(THREAD);
    HandleMark hm(THREAD);

    // Load and initialize the sun.management.Agent class
    // invoke startAgent method to start the management server
    Handle loader = Handle(THREAD, SystemDictionary::java_system_loader());
    klassOop k = SystemDictionary::resolve_or_fail(vmSymbolHandles::sun_management_Agent(),
                                                   loader,
                                                   Handle(),
                                                   true,
                                                   CHECK);
    instanceKlassHandle ik (THREAD, k);

    JavaValue result(T_VOID);
    JavaCalls::call_static(&result,
                           ik,
                           vmSymbolHandles::startAgent_name(),
                           vmSymbolHandles::void_method_signature(),
                           CHECK);
  }
}

void Management::get_optional_support(jmmOptionalSupport* support) {
  memcpy(support, &_optional_support, sizeof(jmmOptionalSupport));
}

klassOop Management::load_and_initialize_klass(symbolHandle sh, TRAPS) {
  klassOop k = SystemDictionary::resolve_or_fail(sh, true, CHECK_NULL);
  instanceKlassHandle ik (THREAD, k);
  if (ik->should_be_initialized()) {
    ik->initialize(CHECK_NULL);
  }
  return ik();
}

void Management::record_vm_startup_time(jlong begin, jlong duration) {
  // if the performance counter is not initialized,
  // then vm initialization failed; simply return.
  if (_begin_vm_creation_time == NULL) return;

  _begin_vm_creation_time->set_value(begin);
  _end_vm_creation_time->set_value(begin + duration);
  PerfMemory::set_accessible(true);
}

jlong Management::timestamp() {
  TimeStamp t;
  t.update();
  return t.ticks() - _stamp.ticks();
}

void Management::oops_do(OopClosure* f) {
  MemoryService::oops_do(f);
  ThreadService::oops_do(f);

  f->do_oop((oop*) &_sensor_klass);
  f->do_oop((oop*) &_threadInfo_klass);
  f->do_oop((oop*) &_memoryUsage_klass);
  f->do_oop((oop*) &_memoryPoolMXBean_klass);
  f->do_oop((oop*) &_memoryManagerMXBean_klass);
  f->do_oop((oop*) &_garbageCollectorMXBean_klass);
  f->do_oop((oop*) &_managementFactory_klass);
}

klassOop Management::java_lang_management_ThreadInfo_klass(TRAPS) {
  if (_threadInfo_klass == NULL) {
    _threadInfo_klass = load_and_initialize_klass(vmSymbolHandles::java_lang_management_ThreadInfo(), CHECK_NULL);
  }
  return _threadInfo_klass;
}

klassOop Management::java_lang_management_MemoryUsage_klass(TRAPS) {
  if (_memoryUsage_klass == NULL) {
    _memoryUsage_klass = load_and_initialize_klass(vmSymbolHandles::java_lang_management_MemoryUsage(), CHECK_NULL);
  }
  return _memoryUsage_klass;
}

klassOop Management::java_lang_management_MemoryPoolMXBean_klass(TRAPS) {
  if (_memoryPoolMXBean_klass == NULL) {
    _memoryPoolMXBean_klass = load_and_initialize_klass(vmSymbolHandles::java_lang_management_MemoryPoolMXBean(), CHECK_NULL);
  }
  return _memoryPoolMXBean_klass;
}

klassOop Management::java_lang_management_MemoryManagerMXBean_klass(TRAPS) {
  if (_memoryManagerMXBean_klass == NULL) {
    _memoryManagerMXBean_klass = load_and_initialize_klass(vmSymbolHandles::java_lang_management_MemoryManagerMXBean(), CHECK_NULL);
  }
  return _memoryManagerMXBean_klass;
}

klassOop Management::java_lang_management_GarbageCollectorMXBean_klass(TRAPS) {
  if (_garbageCollectorMXBean_klass == NULL) {
      _garbageCollectorMXBean_klass = load_and_initialize_klass(vmSymbolHandles::java_lang_management_GarbageCollectorMXBean(), CHECK_NULL);
  }
  return _garbageCollectorMXBean_klass;
}

klassOop Management::sun_management_Sensor_klass(TRAPS) {
  if (_sensor_klass == NULL) {
    _sensor_klass = load_and_initialize_klass(vmSymbolHandles::sun_management_Sensor(), CHECK_NULL);
  }
  return _sensor_klass;
}

klassOop Management::sun_management_ManagementFactory_klass(TRAPS) {
  if (_managementFactory_klass == NULL) {
    _managementFactory_klass = load_and_initialize_klass(vmSymbolHandles::sun_management_ManagementFactory(), CHECK_NULL);
  }
  return _managementFactory_klass;
}

static void initialize_ThreadInfo_constructor_arguments(JavaCallArguments* args, ThreadSnapshot* snapshot, TRAPS) {
  Handle snapshot_thread(THREAD, snapshot->threadObj());

  jlong contended_time;
  jlong waited_time;
  if (ThreadService::is_thread_monitoring_contention()) {
    contended_time = Management::ticks_to_ms(snapshot->contended_enter_ticks());
    waited_time = Management::ticks_to_ms(snapshot->monitor_wait_ticks() + snapshot->sleep_ticks());
  } else {
    // set them to -1 if thread contention monitoring is disabled.
    contended_time = max_julong;
    waited_time = max_julong;
  }

  int thread_status = snapshot->thread_status();
  assert((thread_status & JMM_THREAD_STATE_FLAG_MASK) == 0, "Flags already set in thread_status in Thread object");
  if (snapshot->is_ext_suspended()) {
    thread_status |= JMM_THREAD_STATE_FLAG_SUSPENDED;
  }
  if (snapshot->is_in_native()) {
    thread_status |= JMM_THREAD_STATE_FLAG_NATIVE;
  }

  ThreadStackTrace* st = snapshot->get_stack_trace();
  Handle stacktrace_h;
  if (st != NULL) {
    stacktrace_h = st->allocate_fill_stack_trace_element_array(CHECK);
  } else {
    stacktrace_h = Handle();
  }

  args->push_oop(snapshot_thread);
  args->push_int(thread_status);
  args->push_oop(Handle(THREAD, snapshot->blocker_object()));
  args->push_oop(Handle(THREAD, snapshot->blocker_object_owner()));
  args->push_long(snapshot->contended_enter_count());
  args->push_long(contended_time);
  args->push_long(snapshot->monitor_wait_count() + snapshot->sleep_count());
  args->push_long(waited_time);
  args->push_oop(stacktrace_h);
}

// Helper function to construct a ThreadInfo object
instanceOop Management::create_thread_info_instance(ThreadSnapshot* snapshot, TRAPS) {
  klassOop k = Management::java_lang_management_ThreadInfo_klass(CHECK_NULL);
  instanceKlassHandle ik (THREAD, k);

  JavaValue result(T_VOID);
  JavaCallArguments args(14);

  // First allocate a ThreadObj object and
  // push the receiver as the first argument
  Handle element = ik->allocate_instance_handle(CHECK_NULL);
  args.push_oop(element);

  // initialize the arguments for the ThreadInfo constructor
  initialize_ThreadInfo_constructor_arguments(&args, snapshot, CHECK_NULL);

  // Call ThreadInfo constructor with no locked monitors and synchronizers
  JavaCalls::call_special(&result,
                          ik,
                          vmSymbolHandles::object_initializer_name(),
                          vmSymbolHandles::java_lang_management_ThreadInfo_constructor_signature(),
                          &args,
                          CHECK_NULL);

  return (instanceOop) element();
}

instanceOop Management::create_thread_info_instance(ThreadSnapshot* snapshot,
                                                    objArrayHandle monitors_array,
                                                    typeArrayHandle depths_array,
                                                    objArrayHandle synchronizers_array,
                                                    TRAPS) {
  klassOop k = Management::java_lang_management_ThreadInfo_klass(CHECK_NULL);
  instanceKlassHandle ik (THREAD, k);

  JavaValue result(T_VOID);
  JavaCallArguments args(17);

  // First allocate a ThreadObj object and
  // push the receiver as the first argument
  Handle element = ik->allocate_instance_handle(CHECK_NULL);
  args.push_oop(element);

  // initialize the arguments for the ThreadInfo constructor
  initialize_ThreadInfo_constructor_arguments(&args, snapshot, CHECK_NULL);

  // push the locked monitors and synchronizers in the arguments
  args.push_oop(monitors_array);
  args.push_oop(depths_array);
  args.push_oop(synchronizers_array);

  // Call ThreadInfo constructor with locked monitors and synchronizers
  JavaCalls::call_special(&result,
                          ik,
                          vmSymbolHandles::object_initializer_name(),
                          vmSymbolHandles::java_lang_management_ThreadInfo_with_locks_constructor_signature(),
                          &args,
                          CHECK_NULL);

  return (instanceOop) element();
}

// Helper functions
static JavaThread* find_java_thread_from_id(jlong thread_id) {
  assert(Threads_lock->owned_by_self(), "Must hold Threads_lock");

  JavaThread* java_thread = NULL;
  // Sequential search for now.  Need to do better optimization later.
  for (JavaThread* thread = Threads::first(); thread != NULL; thread = thread->next()) {
    oop tobj = thread->threadObj();
    if (!thread->is_exiting() &&
        tobj != NULL &&
        thread_id == java_lang_Thread::thread_id(tobj)) {
      java_thread = thread;
      break;
    }
  }
  return java_thread;
}

static GCMemoryManager* get_gc_memory_manager_from_jobject(jobject mgr, TRAPS) {
  if (mgr == NULL) {
    THROW_(vmSymbols::java_lang_NullPointerException(), NULL);
  }
  oop mgr_obj = JNIHandles::resolve(mgr);
  instanceHandle h(THREAD, (instanceOop) mgr_obj);

  klassOop k = Management::java_lang_management_GarbageCollectorMXBean_klass(CHECK_NULL);
  if (!h->is_a(k)) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "the object is not an instance of java.lang.management.GarbageCollectorMXBean class",
               NULL);
  }

  MemoryManager* gc = MemoryService::get_memory_manager(h);
  if (gc == NULL || !gc->is_gc_memory_manager()) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "Invalid GC memory manager",
               NULL);
  }
  return (GCMemoryManager*) gc;
}

static MemoryPool* get_memory_pool_from_jobject(jobject obj, TRAPS) {
  if (obj == NULL) {
    THROW_(vmSymbols::java_lang_NullPointerException(), NULL);
  }

  oop pool_obj = JNIHandles::resolve(obj);
  assert(pool_obj->is_instance(), "Should be an instanceOop");
  instanceHandle ph(THREAD, (instanceOop) pool_obj);

  return MemoryService::get_memory_pool(ph);
}

static void validate_thread_id_array(typeArrayHandle ids_ah, TRAPS) {
  int num_threads = ids_ah->length();
  // should be non-empty array
  if (num_threads == 0) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Empty array of thread IDs");
  }

  // Validate input thread IDs
  int i = 0;
  for (i = 0; i < num_threads; i++) {
    jlong tid = ids_ah->long_at(i);
    if (tid <= 0) {
      // throw exception if invalid thread id.
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                "Invalid thread ID entry");
    }
  }

}

static void validate_thread_info_array(objArrayHandle infoArray_h, TRAPS) {

  // check if the element of infoArray is of type ThreadInfo class
  klassOop threadinfo_klass = Management::java_lang_management_ThreadInfo_klass(CHECK);
  klassOop element_klass = objArrayKlass::cast(infoArray_h->klass())->element_klass();
  if (element_klass != threadinfo_klass) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "infoArray element type is not ThreadInfo class");
  }

}


static MemoryManager* get_memory_manager_from_jobject(jobject obj, TRAPS) {
  if (obj == NULL) {
    THROW_(vmSymbols::java_lang_NullPointerException(), NULL);
  }

  oop mgr_obj = JNIHandles::resolve(obj);
  assert(mgr_obj->is_instance(), "Should be an instanceOop");
  instanceHandle mh(THREAD, (instanceOop) mgr_obj);

  return MemoryService::get_memory_manager(mh);
}

// Returns a version string and sets major and minor version if
// the input parameters are non-null.
JVM_LEAF(jint, jmm_GetVersion(JNIEnv *env))
  return JMM_VERSION;
JVM_END

// Gets the list of VM monitoring and management optional supports
// Returns 0 if succeeded; otherwise returns non-zero.
JVM_LEAF(jint, jmm_GetOptionalSupport(JNIEnv *env, jmmOptionalSupport* support))
  if (support == NULL) {
    return -1;
  }
  Management::get_optional_support(support);
  return 0;
JVM_END

// Returns a java.lang.String object containing the input arguments to the VM.
JVM_ENTRY(jobject, jmm_GetInputArguments(JNIEnv *env))
  ResourceMark rm(THREAD);

  if (Arguments::num_jvm_args() == 0 && Arguments::num_jvm_flags() == 0) {
    return NULL;
  }

  char** vm_flags = Arguments::jvm_flags_array();
  char** vm_args  = Arguments::jvm_args_array();
  int num_flags   = Arguments::num_jvm_flags();
  int num_args    = Arguments::num_jvm_args();

  size_t length = 1; // null terminator
  int i;
  for (i = 0; i < num_flags; i++) {
    length += strlen(vm_flags[i]);
  }
  for (i = 0; i < num_args; i++) {
    length += strlen(vm_args[i]);
  }
  // add a space between each argument
  length += num_flags + num_args - 1;

  // Return the list of input arguments passed to the VM
  // and preserve the order that the VM processes.
  char* args = NEW_RESOURCE_ARRAY(char, length);
  args[0] = '\0';
  // concatenate all jvm_flags
  if (num_flags > 0) {
    strcat(args, vm_flags[0]);
    for (i = 1; i < num_flags; i++) {
      strcat(args, " ");
      strcat(args, vm_flags[i]);
    }
  }

  if (num_args > 0 && num_flags > 0) {
    // append a space if args already contains one or more jvm_flags
    strcat(args, " ");
  }

  // concatenate all jvm_args
  if (num_args > 0) {
    strcat(args, vm_args[0]);
    for (i = 1; i < num_args; i++) {
      strcat(args, " ");
      strcat(args, vm_args[i]);
    }
  }

  Handle hargs = java_lang_String::create_from_platform_dependent_str(args, CHECK_NULL);
  return JNIHandles::make_local(env, hargs());
JVM_END

// Returns an array of java.lang.String object containing the input arguments to the VM.
JVM_ENTRY(jobjectArray, jmm_GetInputArgumentArray(JNIEnv *env))
  ResourceMark rm(THREAD);

  if (Arguments::num_jvm_args() == 0 && Arguments::num_jvm_flags() == 0) {
    return NULL;
  }

  char** vm_flags = Arguments::jvm_flags_array();
  char** vm_args = Arguments::jvm_args_array();
  int num_flags = Arguments::num_jvm_flags();
  int num_args = Arguments::num_jvm_args();

  instanceKlassHandle ik (THREAD, SystemDictionary::String_klass());
  objArrayOop r = oopFactory::new_objArray(ik(), num_args + num_flags, CHECK_NULL);
  objArrayHandle result_h(THREAD, r);

  int index = 0;
  for (int j = 0; j < num_flags; j++, index++) {
    Handle h = java_lang_String::create_from_platform_dependent_str(vm_flags[j], CHECK_NULL);
    result_h->obj_at_put(index, h());
  }
  for (int i = 0; i < num_args; i++, index++) {
    Handle h = java_lang_String::create_from_platform_dependent_str(vm_args[i], CHECK_NULL);
    result_h->obj_at_put(index, h());
  }
  return (jobjectArray) JNIHandles::make_local(env, result_h());
JVM_END

// Returns an array of java/lang/management/MemoryPoolMXBean object
// one for each memory pool if obj == null; otherwise returns
// an array of memory pools for a given memory manager if
// it is a valid memory manager.
JVM_ENTRY(jobjectArray, jmm_GetMemoryPools(JNIEnv* env, jobject obj))
  ResourceMark rm(THREAD);

  int num_memory_pools;
  MemoryManager* mgr = NULL;
  if (obj == NULL) {
    num_memory_pools = MemoryService::num_memory_pools();
  } else {
    mgr = get_memory_manager_from_jobject(obj, CHECK_NULL);
    if (mgr == NULL) {
      return NULL;
    }
    num_memory_pools = mgr->num_memory_pools();
  }

  // Allocate the resulting MemoryPoolMXBean[] object
  klassOop k = Management::java_lang_management_MemoryPoolMXBean_klass(CHECK_NULL);
  instanceKlassHandle ik (THREAD, k);
  objArrayOop r = oopFactory::new_objArray(ik(), num_memory_pools, CHECK_NULL);
  objArrayHandle poolArray(THREAD, r);

  if (mgr == NULL) {
    // Get all memory pools
    for (int i = 0; i < num_memory_pools; i++) {
      MemoryPool* pool = MemoryService::get_memory_pool(i);
      instanceOop p = pool->get_memory_pool_instance(CHECK_NULL);
      instanceHandle ph(THREAD, p);
      poolArray->obj_at_put(i, ph());
    }
  } else {
    // Get memory pools managed by a given memory manager
    for (int i = 0; i < num_memory_pools; i++) {
      MemoryPool* pool = mgr->get_memory_pool(i);
      instanceOop p = pool->get_memory_pool_instance(CHECK_NULL);
      instanceHandle ph(THREAD, p);
      poolArray->obj_at_put(i, ph());
    }
  }
  return (jobjectArray) JNIHandles::make_local(env, poolArray());
JVM_END

// Returns an array of java/lang/management/MemoryManagerMXBean object
// one for each memory manager if obj == null; otherwise returns
// an array of memory managers for a given memory pool if
// it is a valid memory pool.
JVM_ENTRY(jobjectArray, jmm_GetMemoryManagers(JNIEnv* env, jobject obj))
  ResourceMark rm(THREAD);

  int num_mgrs;
  MemoryPool* pool = NULL;
  if (obj == NULL) {
    num_mgrs = MemoryService::num_memory_managers();
  } else {
    pool = get_memory_pool_from_jobject(obj, CHECK_NULL);
    if (pool == NULL) {
      return NULL;
    }
    num_mgrs = pool->num_memory_managers();
  }

  // Allocate the resulting MemoryManagerMXBean[] object
  klassOop k = Management::java_lang_management_MemoryManagerMXBean_klass(CHECK_NULL);
  instanceKlassHandle ik (THREAD, k);
  objArrayOop r = oopFactory::new_objArray(ik(), num_mgrs, CHECK_NULL);
  objArrayHandle mgrArray(THREAD, r);

  if (pool == NULL) {
    // Get all memory managers
    for (int i = 0; i < num_mgrs; i++) {
      MemoryManager* mgr = MemoryService::get_memory_manager(i);
      instanceOop p = mgr->get_memory_manager_instance(CHECK_NULL);
      instanceHandle ph(THREAD, p);
      mgrArray->obj_at_put(i, ph());
    }
  } else {
    // Get memory managers for a given memory pool
    for (int i = 0; i < num_mgrs; i++) {
      MemoryManager* mgr = pool->get_memory_manager(i);
      instanceOop p = mgr->get_memory_manager_instance(CHECK_NULL);
      instanceHandle ph(THREAD, p);
      mgrArray->obj_at_put(i, ph());
    }
  }
  return (jobjectArray) JNIHandles::make_local(env, mgrArray());
JVM_END


// Returns a java/lang/management/MemoryUsage object containing the memory usage
// of a given memory pool.
JVM_ENTRY(jobject, jmm_GetMemoryPoolUsage(JNIEnv* env, jobject obj))
  ResourceMark rm(THREAD);

  MemoryPool* pool = get_memory_pool_from_jobject(obj, CHECK_NULL);
  if (pool != NULL) {
    MemoryUsage usage = pool->get_memory_usage();
    Handle h = MemoryService::create_MemoryUsage_obj(usage, CHECK_NULL);
    return JNIHandles::make_local(env, h());
  } else {
    return NULL;
  }
JVM_END

// Returns a java/lang/management/MemoryUsage object containing the memory usage
// of a given memory pool.
JVM_ENTRY(jobject, jmm_GetPeakMemoryPoolUsage(JNIEnv* env, jobject obj))
  ResourceMark rm(THREAD);

  MemoryPool* pool = get_memory_pool_from_jobject(obj, CHECK_NULL);
  if (pool != NULL) {
    MemoryUsage usage = pool->get_peak_memory_usage();
    Handle h = MemoryService::create_MemoryUsage_obj(usage, CHECK_NULL);
    return JNIHandles::make_local(env, h());
  } else {
    return NULL;
  }
JVM_END

// Returns a java/lang/management/MemoryUsage object containing the memory usage
// of a given memory pool after most recent GC.
JVM_ENTRY(jobject, jmm_GetPoolCollectionUsage(JNIEnv* env, jobject obj))
  ResourceMark rm(THREAD);

  MemoryPool* pool = get_memory_pool_from_jobject(obj, CHECK_NULL);
  if (pool != NULL && pool->is_collected_pool()) {
    MemoryUsage usage = pool->get_last_collection_usage();
    Handle h = MemoryService::create_MemoryUsage_obj(usage, CHECK_NULL);
    return JNIHandles::make_local(env, h());
  } else {
    return NULL;
  }
JVM_END

// Sets the memory pool sensor for a threshold type
JVM_ENTRY(void, jmm_SetPoolSensor(JNIEnv* env, jobject obj, jmmThresholdType type, jobject sensorObj))
  if (obj == NULL || sensorObj == NULL) {
    THROW(vmSymbols::java_lang_NullPointerException());
  }

  klassOop sensor_klass = Management::sun_management_Sensor_klass(CHECK);
  oop s = JNIHandles::resolve(sensorObj);
  assert(s->is_instance(), "Sensor should be an instanceOop");
  instanceHandle sensor_h(THREAD, (instanceOop) s);
  if (!sensor_h->is_a(sensor_klass)) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Sensor is not an instance of sun.management.Sensor class");
  }

  MemoryPool* mpool = get_memory_pool_from_jobject(obj, CHECK);
  assert(mpool != NULL, "MemoryPool should exist");

  switch (type) {
    case JMM_USAGE_THRESHOLD_HIGH:
    case JMM_USAGE_THRESHOLD_LOW:
      // have only one sensor for threshold high and low
      mpool->set_usage_sensor_obj(sensor_h);
      break;
    case JMM_COLLECTION_USAGE_THRESHOLD_HIGH:
    case JMM_COLLECTION_USAGE_THRESHOLD_LOW:
      // have only one sensor for threshold high and low
      mpool->set_gc_usage_sensor_obj(sensor_h);
      break;
    default:
      assert(false, "Unrecognized type");
  }

JVM_END


// Sets the threshold of a given memory pool.
// Returns the previous threshold.
//
// Input parameters:
//   pool      - the MemoryPoolMXBean object
//   type      - threshold type
//   threshold - the new threshold (must not be negative)
//
JVM_ENTRY(jlong, jmm_SetPoolThreshold(JNIEnv* env, jobject obj, jmmThresholdType type, jlong threshold))
  if (threshold < 0) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "Invalid threshold value",
               -1);
  }

  if ((size_t)threshold > max_uintx) {
    stringStream st;
    st.print("Invalid valid threshold value. Threshold value (" UINT64_FORMAT ") > max value of size_t (" SIZE_FORMAT ")", (size_t)threshold, max_uintx);
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(), st.as_string(), -1);
  }

  MemoryPool* pool = get_memory_pool_from_jobject(obj, CHECK_(0L));
  assert(pool != NULL, "MemoryPool should exist");

  jlong prev = 0;
  switch (type) {
    case JMM_USAGE_THRESHOLD_HIGH:
      if (!pool->usage_threshold()->is_high_threshold_supported()) {
        return -1;
      }
      prev = pool->usage_threshold()->set_high_threshold((size_t) threshold);
      break;

    case JMM_USAGE_THRESHOLD_LOW:
      if (!pool->usage_threshold()->is_low_threshold_supported()) {
        return -1;
      }
      prev = pool->usage_threshold()->set_low_threshold((size_t) threshold);
      break;

    case JMM_COLLECTION_USAGE_THRESHOLD_HIGH:
      if (!pool->gc_usage_threshold()->is_high_threshold_supported()) {
        return -1;
      }
      // return and the new threshold is effective for the next GC
      return pool->gc_usage_threshold()->set_high_threshold((size_t) threshold);

    case JMM_COLLECTION_USAGE_THRESHOLD_LOW:
      if (!pool->gc_usage_threshold()->is_low_threshold_supported()) {
        return -1;
      }
      // return and the new threshold is effective for the next GC
      return pool->gc_usage_threshold()->set_low_threshold((size_t) threshold);

    default:
      assert(false, "Unrecognized type");
      return -1;
  }

  // When the threshold is changed, reevaluate if the low memory
  // detection is enabled.
  if (prev != threshold) {
    LowMemoryDetector::recompute_enabled_for_collected_pools();
    LowMemoryDetector::detect_low_memory(pool);
  }
  return prev;
JVM_END

// Returns a java/lang/management/MemoryUsage object representing
// the memory usage for the heap or non-heap memory.
JVM_ENTRY(jobject, jmm_GetMemoryUsage(JNIEnv* env, jboolean heap))
  ResourceMark rm(THREAD);

  // Calculate the memory usage
  size_t total_init = 0;
  size_t total_used = 0;
  size_t total_committed = 0;
  size_t total_max = 0;
  bool   has_undefined_init_size = false;
  bool   has_undefined_max_size = false;

  for (int i = 0; i < MemoryService::num_memory_pools(); i++) {
    MemoryPool* pool = MemoryService::get_memory_pool(i);
    if ((heap && pool->is_heap()) || (!heap && pool->is_non_heap())) {
      MemoryUsage u = pool->get_memory_usage();
      total_used += u.used();
      total_committed += u.committed();

      // if any one of the memory pool has undefined init_size or max_size,
      // set it to -1
      if (u.init_size() == (size_t)-1) {
        has_undefined_init_size = true;
      }
      if (!has_undefined_init_size) {
        total_init += u.init_size();
      }

      if (u.max_size() == (size_t)-1) {
        has_undefined_max_size = true;
      }
      if (!has_undefined_max_size) {
        total_max += u.max_size();
      }
    }
  }

  // In our current implementation, all pools should have
  // defined init and max size
  assert(!has_undefined_init_size, "Undefined init size");
  assert(!has_undefined_max_size, "Undefined max size");

  MemoryUsage usage((heap ? InitialHeapSize : total_init),
                    total_used,
                    total_committed,
                    (heap ? Universe::heap()->max_capacity() : total_max));

  Handle obj = MemoryService::create_MemoryUsage_obj(usage, CHECK_NULL);
  return JNIHandles::make_local(env, obj());
JVM_END

// Returns the boolean value of a given attribute.
JVM_LEAF(jboolean, jmm_GetBoolAttribute(JNIEnv *env, jmmBoolAttribute att))
  switch (att) {
  case JMM_VERBOSE_GC:
    return MemoryService::get_verbose();
  case JMM_VERBOSE_CLASS:
    return ClassLoadingService::get_verbose();
  case JMM_THREAD_CONTENTION_MONITORING:
    return ThreadService::is_thread_monitoring_contention();
  case JMM_THREAD_CPU_TIME:
    return ThreadService::is_thread_cpu_time_enabled();
  default:
    assert(0, "Unrecognized attribute");
    return false;
  }
JVM_END

// Sets the given boolean attribute and returns the previous value.
JVM_ENTRY(jboolean, jmm_SetBoolAttribute(JNIEnv *env, jmmBoolAttribute att, jboolean flag))
  switch (att) {
  case JMM_VERBOSE_GC:
    return MemoryService::set_verbose(flag != 0);
  case JMM_VERBOSE_CLASS:
    return ClassLoadingService::set_verbose(flag != 0);
  case JMM_THREAD_CONTENTION_MONITORING:
    return ThreadService::set_thread_monitoring_contention(flag != 0);
  case JMM_THREAD_CPU_TIME:
    return ThreadService::set_thread_cpu_time_enabled(flag != 0);
  default:
    assert(0, "Unrecognized attribute");
    return false;
  }
JVM_END


static jlong get_gc_attribute(GCMemoryManager* mgr, jmmLongAttribute att) {
  switch (att) {
  case JMM_GC_TIME_MS:
    return mgr->gc_time_ms();

  case JMM_GC_COUNT:
    return mgr->gc_count();

  case JMM_GC_EXT_ATTRIBUTE_INFO_SIZE:
    // current implementation only has 1 ext attribute
    return 1;

  default:
    assert(0, "Unrecognized GC attribute");
    return -1;
  }
}

class VmThreadCountClosure: public ThreadClosure {
 private:
  int _count;
 public:
  VmThreadCountClosure() : _count(0) {};
  void do_thread(Thread* thread);
  int count() { return _count; }
};

void VmThreadCountClosure::do_thread(Thread* thread) {
  // exclude externally visible JavaThreads
  if (thread->is_Java_thread() && !thread->is_hidden_from_external_view()) {
    return;
  }

  _count++;
}

static jint get_vm_thread_count() {
  VmThreadCountClosure vmtcc;
  {
    MutexLockerEx ml(Threads_lock);
    Threads::threads_do(&vmtcc);
  }

  return vmtcc.count();
}

static jint get_num_flags() {
  // last flag entry is always NULL, so subtract 1
  int nFlags = (int) Flag::numFlags - 1;
  int count = 0;
  for (int i = 0; i < nFlags; i++) {
    Flag* flag = &Flag::flags[i];
    // Exclude the locked (diagnostic, experimental) flags
    if (flag->is_unlocked() || flag->is_unlocker()) {
      count++;
    }
  }
  return count;
}

static jlong get_long_attribute(jmmLongAttribute att) {
  switch (att) {
  case JMM_CLASS_LOADED_COUNT:
    return ClassLoadingService::loaded_class_count();

  case JMM_CLASS_UNLOADED_COUNT:
    return ClassLoadingService::unloaded_class_count();

  case JMM_THREAD_TOTAL_COUNT:
    return ThreadService::get_total_thread_count();

  case JMM_THREAD_LIVE_COUNT:
    return ThreadService::get_live_thread_count();

  case JMM_THREAD_PEAK_COUNT:
    return ThreadService::get_peak_thread_count();

  case JMM_THREAD_DAEMON_COUNT:
    return ThreadService::get_daemon_thread_count();

  case JMM_JVM_INIT_DONE_TIME_MS:
    return Management::vm_init_done_time();

  case JMM_COMPILE_TOTAL_TIME_MS:
    return Management::ticks_to_ms(CompileBroker::total_compilation_ticks());

  case JMM_OS_PROCESS_ID:
    return os::current_process_id();

  // Hotspot-specific counters
  case JMM_CLASS_LOADED_BYTES:
    return ClassLoadingService::loaded_class_bytes();

  case JMM_CLASS_UNLOADED_BYTES:
    return ClassLoadingService::unloaded_class_bytes();

  case JMM_SHARED_CLASS_LOADED_COUNT:
    return ClassLoadingService::loaded_shared_class_count();

  case JMM_SHARED_CLASS_UNLOADED_COUNT:
    return ClassLoadingService::unloaded_shared_class_count();


  case JMM_SHARED_CLASS_LOADED_BYTES:
    return ClassLoadingService::loaded_shared_class_bytes();

  case JMM_SHARED_CLASS_UNLOADED_BYTES:
    return ClassLoadingService::unloaded_shared_class_bytes();

  case JMM_TOTAL_CLASSLOAD_TIME_MS:
    return ClassLoader::classloader_time_ms();

  case JMM_VM_GLOBAL_COUNT:
    return get_num_flags();

  case JMM_SAFEPOINT_COUNT:
    return RuntimeService::safepoint_count();

  case JMM_TOTAL_SAFEPOINTSYNC_TIME_MS:
    return RuntimeService::safepoint_sync_time_ms();

  case JMM_TOTAL_STOPPED_TIME_MS:
    return RuntimeService::safepoint_time_ms();

  case JMM_TOTAL_APP_TIME_MS:
    return RuntimeService::application_time_ms();

  case JMM_VM_THREAD_COUNT:
    return get_vm_thread_count();

  case JMM_CLASS_INIT_TOTAL_COUNT:
    return ClassLoader::class_init_count();

  case JMM_CLASS_INIT_TOTAL_TIME_MS:
    return ClassLoader::class_init_time_ms();

  case JMM_CLASS_VERIFY_TOTAL_TIME_MS:
    return ClassLoader::class_verify_time_ms();

  case JMM_METHOD_DATA_SIZE_BYTES:
    return ClassLoadingService::class_method_data_size();

  case JMM_OS_MEM_TOTAL_PHYSICAL_BYTES:
    return os::physical_memory();

  default:
    return -1;
  }
}


// Returns the long value of a given attribute.
JVM_ENTRY(jlong, jmm_GetLongAttribute(JNIEnv *env, jobject obj, jmmLongAttribute att))
  if (obj == NULL) {
    return get_long_attribute(att);
  } else {
    GCMemoryManager* mgr = get_gc_memory_manager_from_jobject(obj, CHECK_(0L));
    if (mgr != NULL) {
      return get_gc_attribute(mgr, att);
    }
  }
  return -1;
JVM_END

// Gets the value of all attributes specified in the given array
// and sets the value in the result array.
// Returns the number of attributes found.
JVM_ENTRY(jint, jmm_GetLongAttributes(JNIEnv *env,
                                      jobject obj,
                                      jmmLongAttribute* atts,
                                      jint count,
                                      jlong* result))

  int num_atts = 0;
  if (obj == NULL) {
    for (int i = 0; i < count; i++) {
      result[i] = get_long_attribute(atts[i]);
      if (result[i] != -1) {
        num_atts++;
      }
    }
  } else {
    GCMemoryManager* mgr = get_gc_memory_manager_from_jobject(obj, CHECK_0);
    for (int i = 0; i < count; i++) {
      result[i] = get_gc_attribute(mgr, atts[i]);
      if (result[i] != -1) {
        num_atts++;
      }
    }
  }
  return num_atts;
JVM_END

// Helper function to do thread dump for a specific list of threads
static void do_thread_dump(ThreadDumpResult* dump_result,
                           typeArrayHandle ids_ah,  // array of thread ID (long[])
                           int num_threads,
                           int max_depth,
                           bool with_locked_monitors,
                           bool with_locked_synchronizers,
                           TRAPS) {

  // First get an array of threadObj handles.
  // A JavaThread may terminate before we get the stack trace.
  GrowableArray<instanceHandle>* thread_handle_array = new GrowableArray<instanceHandle>(num_threads);
  {
    MutexLockerEx ml(Threads_lock);
    for (int i = 0; i < num_threads; i++) {
      jlong tid = ids_ah->long_at(i);
      JavaThread* jt = find_java_thread_from_id(tid);
      oop thread_obj = (jt != NULL ? jt->threadObj() : (oop)NULL);
      instanceHandle threadObj_h(THREAD, (instanceOop) thread_obj);
      thread_handle_array->append(threadObj_h);
    }
  }

  // Obtain thread dumps and thread snapshot information
  VM_ThreadDump op(dump_result,
                   thread_handle_array,
                   num_threads,
                   max_depth, /* stack depth */
                   with_locked_monitors,
                   with_locked_synchronizers);
  VMThread::execute(&op);
}

// Gets an array of ThreadInfo objects. Each element is the ThreadInfo
// for the thread ID specified in the corresponding entry in
// the given array of thread IDs; or NULL if the thread does not exist
// or has terminated.
//
// Input parameters:
//   ids       - array of thread IDs
//   maxDepth  - the maximum depth of stack traces to be dumped:
//               maxDepth == -1 requests to dump entire stack trace.
//               maxDepth == 0  requests no stack trace.
//   infoArray - array of ThreadInfo objects
//
JVM_ENTRY(jint, jmm_GetThreadInfo(JNIEnv *env, jlongArray ids, jint maxDepth, jobjectArray infoArray))
  // Check if threads is null
  if (ids == NULL || infoArray == NULL) {
    THROW_(vmSymbols::java_lang_NullPointerException(), -1);
  }

  if (maxDepth < -1) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "Invalid maxDepth", -1);
  }

  ResourceMark rm(THREAD);
  typeArrayOop ta = typeArrayOop(JNIHandles::resolve_non_null(ids));
  typeArrayHandle ids_ah(THREAD, ta);

  oop infoArray_obj = JNIHandles::resolve_non_null(infoArray);
  objArrayOop oa = objArrayOop(infoArray_obj);
  objArrayHandle infoArray_h(THREAD, oa);

  // validate the thread id array
  validate_thread_id_array(ids_ah, CHECK_0);

  // validate the ThreadInfo[] parameters
  validate_thread_info_array(infoArray_h, CHECK_0);

  // infoArray must be of the same length as the given array of thread IDs
  int num_threads = ids_ah->length();
  if (num_threads != infoArray_h->length()) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "The length of the given ThreadInfo array does not match the length of the given array of thread IDs", -1);
  }

  if (JDK_Version::is_gte_jdk16x_version()) {
    // make sure the AbstractOwnableSynchronizer klass is loaded before taking thread snapshots
    java_util_concurrent_locks_AbstractOwnableSynchronizer::initialize(CHECK_0);
  }

  // Must use ThreadDumpResult to store the ThreadSnapshot.
  // GC may occur after the thread snapshots are taken but before
  // this function returns. The threadObj and other oops kept
  // in the ThreadSnapshot are marked and adjusted during GC.
  ThreadDumpResult dump_result(num_threads);

  if (maxDepth == 0) {
    // no stack trace dumped - do not need to stop the world
    {
      MutexLockerEx ml(Threads_lock);
      for (int i = 0; i < num_threads; i++) {
        jlong tid = ids_ah->long_at(i);
        JavaThread* jt = find_java_thread_from_id(tid);
        ThreadSnapshot* ts;
        if (jt == NULL) {
          // if the thread does not exist or now it is terminated,
          // create dummy snapshot
          ts = new ThreadSnapshot();
        } else {
          ts = new ThreadSnapshot(jt);
        }
        dump_result.add_thread_snapshot(ts);
      }
    }
  } else {
    // obtain thread dump with the specific list of threads with stack trace

    do_thread_dump(&dump_result,
                   ids_ah,
                   num_threads,
                   maxDepth,
                   false, /* no locked monitor */
                   false, /* no locked synchronizers */
                   CHECK_0);
  }

  int num_snapshots = dump_result.num_snapshots();
  assert(num_snapshots == num_threads, "Must match the number of thread snapshots");
  int index = 0;
  for (ThreadSnapshot* ts = dump_result.snapshots(); ts != NULL; index++, ts = ts->next()) {
    // For each thread, create an java/lang/management/ThreadInfo object
    // and fill with the thread information

    if (ts->threadObj() == NULL) {
     // if the thread does not exist or now it is terminated, set threadinfo to NULL
      infoArray_h->obj_at_put(index, NULL);
      continue;
    }

    // Create java.lang.management.ThreadInfo object
    instanceOop info_obj = Management::create_thread_info_instance(ts, CHECK_0);
    infoArray_h->obj_at_put(index, info_obj);
  }
  return 0;
JVM_END

// Dump thread info for the specified threads.
// It returns an array of ThreadInfo objects. Each element is the ThreadInfo
// for the thread ID specified in the corresponding entry in
// the given array of thread IDs; or NULL if the thread does not exist
// or has terminated.
//
// Input parameter:
//    ids - array of thread IDs; NULL indicates all live threads
//    locked_monitors - if true, dump locked object monitors
//    locked_synchronizers - if true, dump locked JSR-166 synchronizers
//
JVM_ENTRY(jobjectArray, jmm_DumpThreads(JNIEnv *env, jlongArray thread_ids, jboolean locked_monitors, jboolean locked_synchronizers))
  ResourceMark rm(THREAD);

  if (JDK_Version::is_gte_jdk16x_version()) {
    // make sure the AbstractOwnableSynchronizer klass is loaded before taking thread snapshots
    java_util_concurrent_locks_AbstractOwnableSynchronizer::initialize(CHECK_NULL);
  }

  typeArrayOop ta = typeArrayOop(JNIHandles::resolve(thread_ids));
  int num_threads = (ta != NULL ? ta->length() : 0);
  typeArrayHandle ids_ah(THREAD, ta);

  ThreadDumpResult dump_result(num_threads);  // can safepoint

  if (ids_ah() != NULL) {

    // validate the thread id array
    validate_thread_id_array(ids_ah, CHECK_NULL);

    // obtain thread dump of a specific list of threads
    do_thread_dump(&dump_result,
                   ids_ah,
                   num_threads,
                   -1, /* entire stack */
                   (locked_monitors ? true : false),      /* with locked monitors */
                   (locked_synchronizers ? true : false), /* with locked synchronizers */
                   CHECK_NULL);
  } else {
    // obtain thread dump of all threads
    VM_ThreadDump op(&dump_result,
                     -1, /* entire stack */
                     (locked_monitors ? true : false),     /* with locked monitors */
                     (locked_synchronizers ? true : false) /* with locked synchronizers */);
    VMThread::execute(&op);
  }

  int num_snapshots = dump_result.num_snapshots();

  // create the result ThreadInfo[] object
  klassOop k = Management::java_lang_management_ThreadInfo_klass(CHECK_NULL);
  instanceKlassHandle ik (THREAD, k);
  objArrayOop r = oopFactory::new_objArray(ik(), num_snapshots, CHECK_NULL);
  objArrayHandle result_h(THREAD, r);

  int index = 0;
  for (ThreadSnapshot* ts = dump_result.snapshots(); ts != NULL; ts = ts->next(), index++) {
    if (ts->threadObj() == NULL) {
     // if the thread does not exist or now it is terminated, set threadinfo to NULL
      result_h->obj_at_put(index, NULL);
      continue;
    }



    ThreadStackTrace* stacktrace = ts->get_stack_trace();
    assert(stacktrace != NULL, "Must have a stack trace dumped");

    // Create Object[] filled with locked monitors
    // Create int[] filled with the stack depth where a monitor was locked
    int num_frames = stacktrace->get_stack_depth();
    int num_locked_monitors = stacktrace->num_jni_locked_monitors();

    // Count the total number of locked monitors
    for (int i = 0; i < num_frames; i++) {
      StackFrameInfo* frame = stacktrace->stack_frame_at(i);
      num_locked_monitors += frame->num_locked_monitors();
    }

    objArrayHandle monitors_array;
    typeArrayHandle depths_array;
    objArrayHandle synchronizers_array;

    if (locked_monitors) {
      // Constructs Object[] and int[] to contain the object monitor and the stack depth
      // where the thread locked it
      objArrayOop array = oopFactory::new_system_objArray(num_locked_monitors, CHECK_NULL);
      objArrayHandle mh(THREAD, array);
      monitors_array = mh;

      typeArrayOop tarray = oopFactory::new_typeArray(T_INT, num_locked_monitors, CHECK_NULL);
      typeArrayHandle dh(THREAD, tarray);
      depths_array = dh;

      int count = 0;
      int j = 0;
      for (int depth = 0; depth < num_frames; depth++) {
        StackFrameInfo* frame = stacktrace->stack_frame_at(depth);
        int len = frame->num_locked_monitors();
        GrowableArray<oop>* locked_monitors = frame->locked_monitors();
        for (j = 0; j < len; j++) {
          oop monitor = locked_monitors->at(j);
          assert(monitor != NULL && monitor->is_instance(), "must be a Java object");
          monitors_array->obj_at_put(count, monitor);
          depths_array->int_at_put(count, depth);
          count++;
        }
      }

      GrowableArray<oop>* jni_locked_monitors = stacktrace->jni_locked_monitors();
      for (j = 0; j < jni_locked_monitors->length(); j++) {
        oop object = jni_locked_monitors->at(j);
        assert(object != NULL && object->is_instance(), "must be a Java object");
        monitors_array->obj_at_put(count, object);
        // Monitor locked via JNI MonitorEnter call doesn't have stack depth info
        depths_array->int_at_put(count, -1);
        count++;
      }
      assert(count == num_locked_monitors, "number of locked monitors doesn't match");
    }

    if (locked_synchronizers) {
      // Create Object[] filled with locked JSR-166 synchronizers
      assert(ts->threadObj() != NULL, "Must be a valid JavaThread");
      ThreadConcurrentLocks* tcl = ts->get_concurrent_locks();
      GrowableArray<instanceOop>* locks = (tcl != NULL ? tcl->owned_locks() : NULL);
      int num_locked_synchronizers = (locks != NULL ? locks->length() : 0);

      objArrayOop array = oopFactory::new_system_objArray(num_locked_synchronizers, CHECK_NULL);
      objArrayHandle sh(THREAD, array);
      synchronizers_array = sh;

      for (int k = 0; k < num_locked_synchronizers; k++) {
        synchronizers_array->obj_at_put(k, locks->at(k));
      }
    }

    // Create java.lang.management.ThreadInfo object
    instanceOop info_obj = Management::create_thread_info_instance(ts,
                                                                   monitors_array,
                                                                   depths_array,
                                                                   synchronizers_array,
                                                                   CHECK_NULL);
    result_h->obj_at_put(index, info_obj);
  }

  return (jobjectArray) JNIHandles::make_local(env, result_h());
JVM_END

// Returns an array of Class objects.
JVM_ENTRY(jobjectArray, jmm_GetLoadedClasses(JNIEnv *env))
  ResourceMark rm(THREAD);

  LoadedClassesEnumerator lce(THREAD);  // Pass current Thread as parameter

  int num_classes = lce.num_loaded_classes();
  objArrayOop r = oopFactory::new_objArray(SystemDictionary::Class_klass(), num_classes, CHECK_0);
  objArrayHandle classes_ah(THREAD, r);

  for (int i = 0; i < num_classes; i++) {
    KlassHandle kh = lce.get_klass(i);
    oop mirror = Klass::cast(kh())->java_mirror();
    classes_ah->obj_at_put(i, mirror);
  }

  return (jobjectArray) JNIHandles::make_local(env, classes_ah());
JVM_END

// Reset statistic.  Return true if the requested statistic is reset.
// Otherwise, return false.
//
// Input parameters:
//  obj  - specify which instance the statistic associated with to be reset
//         For PEAK_POOL_USAGE stat, obj is required to be a memory pool object.
//         For THREAD_CONTENTION_COUNT and TIME stat, obj is required to be a thread ID.
//  type - the type of statistic to be reset
//
JVM_ENTRY(jboolean, jmm_ResetStatistic(JNIEnv *env, jvalue obj, jmmStatisticType type))
  ResourceMark rm(THREAD);

  switch (type) {
    case JMM_STAT_PEAK_THREAD_COUNT:
      ThreadService::reset_peak_thread_count();
      return true;

    case JMM_STAT_THREAD_CONTENTION_COUNT:
    case JMM_STAT_THREAD_CONTENTION_TIME: {
      jlong tid = obj.j;
      if (tid < 0) {
        THROW_(vmSymbols::java_lang_IllegalArgumentException(), JNI_FALSE);
      }

      // Look for the JavaThread of this given tid
      MutexLockerEx ml(Threads_lock);
      if (tid == 0) {
        // reset contention statistics for all threads if tid == 0
        for (JavaThread* java_thread = Threads::first(); java_thread != NULL; java_thread = java_thread->next()) {
          if (type == JMM_STAT_THREAD_CONTENTION_COUNT) {
            ThreadService::reset_contention_count_stat(java_thread);
          } else {
            ThreadService::reset_contention_time_stat(java_thread);
          }
        }
      } else {
        // reset contention statistics for a given thread
        JavaThread* java_thread = find_java_thread_from_id(tid);
        if (java_thread == NULL) {
          return false;
        }

        if (type == JMM_STAT_THREAD_CONTENTION_COUNT) {
          ThreadService::reset_contention_count_stat(java_thread);
        } else {
          ThreadService::reset_contention_time_stat(java_thread);
        }
      }
      return true;
      break;
    }
    case JMM_STAT_PEAK_POOL_USAGE: {
      jobject o = obj.l;
      if (o == NULL) {
        THROW_(vmSymbols::java_lang_NullPointerException(), JNI_FALSE);
      }

      oop pool_obj = JNIHandles::resolve(o);
      assert(pool_obj->is_instance(), "Should be an instanceOop");
      instanceHandle ph(THREAD, (instanceOop) pool_obj);

      MemoryPool* pool = MemoryService::get_memory_pool(ph);
      if (pool != NULL) {
        pool->reset_peak_memory_usage();
        return true;
      }
      break;
    }
    case JMM_STAT_GC_STAT: {
      jobject o = obj.l;
      if (o == NULL) {
        THROW_(vmSymbols::java_lang_NullPointerException(), JNI_FALSE);
      }

      GCMemoryManager* mgr = get_gc_memory_manager_from_jobject(o, CHECK_0);
      if (mgr != NULL) {
        mgr->reset_gc_stat();
        return true;
      }
      break;
    }
    default:
      assert(0, "Unknown Statistic Type");
  }
  return false;
JVM_END

// Returns the fast estimate of CPU time consumed by
// a given thread (in nanoseconds).
// If thread_id == 0, return CPU time for the current thread.
JVM_ENTRY(jlong, jmm_GetThreadCpuTime(JNIEnv *env, jlong thread_id))
  if (!os::is_thread_cpu_time_supported()) {
    return -1;
  }

  if (thread_id < 0) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "Invalid thread ID", -1);
  }

  JavaThread* java_thread = NULL;
  if (thread_id == 0) {
    // current thread
    return os::current_thread_cpu_time();
  } else {
    MutexLockerEx ml(Threads_lock);
    java_thread = find_java_thread_from_id(thread_id);
    if (java_thread != NULL) {
      return os::thread_cpu_time((Thread*) java_thread);
    }
  }
  return -1;
JVM_END

// Returns the CPU time consumed by a given thread (in nanoseconds).
// If thread_id == 0, CPU time for the current thread is returned.
// If user_sys_cpu_time = true, user level and system CPU time of
// a given thread is returned; otherwise, only user level CPU time
// is returned.
JVM_ENTRY(jlong, jmm_GetThreadCpuTimeWithKind(JNIEnv *env, jlong thread_id, jboolean user_sys_cpu_time))
  if (!os::is_thread_cpu_time_supported()) {
    return -1;
  }

  if (thread_id < 0) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "Invalid thread ID", -1);
  }

  JavaThread* java_thread = NULL;
  if (thread_id == 0) {
    // current thread
    return os::current_thread_cpu_time(user_sys_cpu_time != 0);
  } else {
    MutexLockerEx ml(Threads_lock);
    java_thread = find_java_thread_from_id(thread_id);
    if (java_thread != NULL) {
      return os::thread_cpu_time((Thread*) java_thread, user_sys_cpu_time != 0);
    }
  }
  return -1;
JVM_END

// Returns a String array of all VM global flag names
JVM_ENTRY(jobjectArray, jmm_GetVMGlobalNames(JNIEnv *env))
  // last flag entry is always NULL, so subtract 1
  int nFlags = (int) Flag::numFlags - 1;
  // allocate a temp array
  objArrayOop r = oopFactory::new_objArray(SystemDictionary::String_klass(),
                                           nFlags, CHECK_0);
  objArrayHandle flags_ah(THREAD, r);
  int num_entries = 0;
  for (int i = 0; i < nFlags; i++) {
    Flag* flag = &Flag::flags[i];
    // Exclude the locked (experimental, diagnostic) flags
    if (flag->is_unlocked() || flag->is_unlocker()) {
      Handle s = java_lang_String::create_from_str(flag->name, CHECK_0);
      flags_ah->obj_at_put(num_entries, s());
      num_entries++;
    }
  }

  if (num_entries < nFlags) {
    // Return array of right length
    objArrayOop res = oopFactory::new_objArray(SystemDictionary::String_klass(), num_entries, CHECK_0);
    for(int i = 0; i < num_entries; i++) {
      res->obj_at_put(i, flags_ah->obj_at(i));
    }
    return (jobjectArray)JNIHandles::make_local(env, res);
  }

  return (jobjectArray)JNIHandles::make_local(env, flags_ah());
JVM_END

// Utility function used by jmm_GetVMGlobals.  Returns false if flag type
// can't be determined, true otherwise.  If false is returned, then *global
// will be incomplete and invalid.
bool add_global_entry(JNIEnv* env, Handle name, jmmVMGlobal *global, Flag *flag, TRAPS) {
  Handle flag_name;
  if (name() == NULL) {
    flag_name = java_lang_String::create_from_str(flag->name, CHECK_false);
  } else {
    flag_name = name;
  }
  global->name = (jstring)JNIHandles::make_local(env, flag_name());

  if (flag->is_bool()) {
    global->value.z = flag->get_bool() ? JNI_TRUE : JNI_FALSE;
    global->type = JMM_VMGLOBAL_TYPE_JBOOLEAN;
  } else if (flag->is_intx()) {
    global->value.j = (jlong)flag->get_intx();
    global->type = JMM_VMGLOBAL_TYPE_JLONG;
  } else if (flag->is_uintx()) {
    global->value.j = (jlong)flag->get_uintx();
    global->type = JMM_VMGLOBAL_TYPE_JLONG;
  } else if (flag->is_uint64_t()) {
    global->value.j = (jlong)flag->get_uint64_t();
    global->type = JMM_VMGLOBAL_TYPE_JLONG;
  } else if (flag->is_ccstr()) {
    Handle str = java_lang_String::create_from_str(flag->get_ccstr(), CHECK_false);
    global->value.l = (jobject)JNIHandles::make_local(env, str());
    global->type = JMM_VMGLOBAL_TYPE_JSTRING;
  } else {
    global->type = JMM_VMGLOBAL_TYPE_UNKNOWN;
    return false;
  }

  global->writeable = flag->is_writeable();
  global->external = flag->is_external();
  switch (flag->origin) {
    case DEFAULT:
      global->origin = JMM_VMGLOBAL_ORIGIN_DEFAULT;
      break;
    case COMMAND_LINE:
      global->origin = JMM_VMGLOBAL_ORIGIN_COMMAND_LINE;
      break;
    case ENVIRON_VAR:
      global->origin = JMM_VMGLOBAL_ORIGIN_ENVIRON_VAR;
      break;
    case CONFIG_FILE:
      global->origin = JMM_VMGLOBAL_ORIGIN_CONFIG_FILE;
      break;
    case MANAGEMENT:
      global->origin = JMM_VMGLOBAL_ORIGIN_MANAGEMENT;
      break;
    case ERGONOMIC:
      global->origin = JMM_VMGLOBAL_ORIGIN_ERGONOMIC;
      break;
    default:
      global->origin = JMM_VMGLOBAL_ORIGIN_OTHER;
  }

  return true;
}

// Fill globals array of count length with jmmVMGlobal entries
// specified by names. If names == NULL, fill globals array
// with all Flags. Return value is number of entries
// created in globals.
// If a Flag with a given name in an array element does not
// exist, globals[i].name will be set to NULL.
JVM_ENTRY(jint, jmm_GetVMGlobals(JNIEnv *env,
                                 jobjectArray names,
                                 jmmVMGlobal *globals,
                                 jint count))


  if (globals == NULL) {
    THROW_(vmSymbols::java_lang_NullPointerException(), 0);
  }

  ResourceMark rm(THREAD);

  if (names != NULL) {
    // return the requested globals
    objArrayOop ta = objArrayOop(JNIHandles::resolve_non_null(names));
    objArrayHandle names_ah(THREAD, ta);
    // Make sure we have a String array
    klassOop element_klass = objArrayKlass::cast(names_ah->klass())->element_klass();
    if (element_klass != SystemDictionary::String_klass()) {
      THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
                 "Array element type is not String class", 0);
    }

    int names_length = names_ah->length();
    int num_entries = 0;
    for (int i = 0; i < names_length && i < count; i++) {
      oop s = names_ah->obj_at(i);
      if (s == NULL) {
        THROW_(vmSymbols::java_lang_NullPointerException(), 0);
      }

      Handle sh(THREAD, s);
      char* str = java_lang_String::as_utf8_string(s);
      Flag* flag = Flag::find_flag(str, strlen(str));
      if (flag != NULL &&
          add_global_entry(env, sh, &globals[i], flag, THREAD)) {
        num_entries++;
      } else {
        globals[i].name = NULL;
      }
    }
    return num_entries;
  } else {
    // return all globals if names == NULL

    // last flag entry is always NULL, so subtract 1
    int nFlags = (int) Flag::numFlags - 1;
    Handle null_h;
    int num_entries = 0;
    for (int i = 0; i < nFlags && num_entries < count;  i++) {
      Flag* flag = &Flag::flags[i];
      // Exclude the locked (diagnostic, experimental) flags
      if ((flag->is_unlocked() || flag->is_unlocker()) &&
          add_global_entry(env, null_h, &globals[num_entries], flag, THREAD)) {
        num_entries++;
      }
    }
    return num_entries;
  }
JVM_END

JVM_ENTRY(void, jmm_SetVMGlobal(JNIEnv *env, jstring flag_name, jvalue new_value))
  ResourceMark rm(THREAD);

  oop fn = JNIHandles::resolve_external_guard(flag_name);
  if (fn == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(),
              "The flag name cannot be null.");
  }
  char* name = java_lang_String::as_utf8_string(fn);
  Flag* flag = Flag::find_flag(name, strlen(name));
  if (flag == NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Flag does not exist.");
  }
  if (!flag->is_writeable()) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "This flag is not writeable.");
  }

  bool succeed;
  if (flag->is_bool()) {
    bool bvalue = (new_value.z == JNI_TRUE ? true : false);
    succeed = CommandLineFlags::boolAtPut(name, &bvalue, MANAGEMENT);
  } else if (flag->is_intx()) {
    intx ivalue = (intx)new_value.j;
    succeed = CommandLineFlags::intxAtPut(name, &ivalue, MANAGEMENT);
  } else if (flag->is_uintx()) {
    uintx uvalue = (uintx)new_value.j;
    succeed = CommandLineFlags::uintxAtPut(name, &uvalue, MANAGEMENT);
  } else if (flag->is_uint64_t()) {
    uint64_t uvalue = (uint64_t)new_value.j;
    succeed = CommandLineFlags::uint64_tAtPut(name, &uvalue, MANAGEMENT);
  } else if (flag->is_ccstr()) {
    oop str = JNIHandles::resolve_external_guard(new_value.l);
    if (str == NULL) {
      THROW(vmSymbols::java_lang_NullPointerException());
    }
    ccstr svalue = java_lang_String::as_utf8_string(str);
    succeed = CommandLineFlags::ccstrAtPut(name, &svalue, MANAGEMENT);
  }
  assert(succeed, "Setting flag should succeed");
JVM_END

class ThreadTimesClosure: public ThreadClosure {
 private:
  objArrayOop _names;
  typeArrayOop _times;
  int _names_len;
  int _times_len;
  int _count;

 public:
  ThreadTimesClosure(objArrayOop names, typeArrayOop times);
  virtual void do_thread(Thread* thread);
  int count() { return _count; }
};

ThreadTimesClosure::ThreadTimesClosure(objArrayOop names,
                                       typeArrayOop times) {
  assert(names != NULL, "names was NULL");
  assert(times != NULL, "times was NULL");
  _names = names;
  _names_len = names->length();
  _times = times;
  _times_len = times->length();
  _count = 0;
}

void ThreadTimesClosure::do_thread(Thread* thread) {
  Handle s;
  assert(thread != NULL, "thread was NULL");

  // exclude externally visible JavaThreads
  if (thread->is_Java_thread() && !thread->is_hidden_from_external_view()) {
    return;
  }

  if (_count >= _names_len || _count >= _times_len) {
    // skip if the result array is not big enough
    return;
  }

  EXCEPTION_MARK;

  assert(thread->name() != NULL, "All threads should have a name");
  s = java_lang_String::create_from_str(thread->name(), CHECK);
  _names->obj_at_put(_count, s());

  _times->long_at_put(_count, os::is_thread_cpu_time_supported() ?
                        os::thread_cpu_time(thread) : -1);
  _count++;
}

// Fills names with VM internal thread names and times with the corresponding
// CPU times.  If names or times is NULL, a NullPointerException is thrown.
// If the element type of names is not String, an IllegalArgumentException is
// thrown.
// If an array is not large enough to hold all the entries, only the entries
// that fit will be returned.  Return value is the number of VM internal
// threads entries.
JVM_ENTRY(jint, jmm_GetInternalThreadTimes(JNIEnv *env,
                                           jobjectArray names,
                                           jlongArray times))
  if (names == NULL || times == NULL) {
     THROW_(vmSymbols::java_lang_NullPointerException(), 0);
  }
  objArrayOop na = objArrayOop(JNIHandles::resolve_non_null(names));
  objArrayHandle names_ah(THREAD, na);

  // Make sure we have a String array
  klassOop element_klass = objArrayKlass::cast(names_ah->klass())->element_klass();
  if (element_klass != SystemDictionary::String_klass()) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "Array element type is not String class", 0);
  }

  typeArrayOop ta = typeArrayOop(JNIHandles::resolve_non_null(times));
  typeArrayHandle times_ah(THREAD, ta);

  ThreadTimesClosure ttc(names_ah(), times_ah());
  {
    MutexLockerEx ml(Threads_lock);
    Threads::threads_do(&ttc);
  }

  return ttc.count();
JVM_END

static Handle find_deadlocks(bool object_monitors_only, TRAPS) {
  ResourceMark rm(THREAD);

  VM_FindDeadlocks op(!object_monitors_only /* also check concurrent locks? */);
  VMThread::execute(&op);

  DeadlockCycle* deadlocks = op.result();
  if (deadlocks == NULL) {
    // no deadlock found and return
    return Handle();
  }

  int num_threads = 0;
  DeadlockCycle* cycle;
  for (cycle = deadlocks; cycle != NULL; cycle = cycle->next()) {
    num_threads += cycle->num_threads();
  }

  objArrayOop r = oopFactory::new_objArray(SystemDictionary::Thread_klass(), num_threads, CHECK_NH);
  objArrayHandle threads_ah(THREAD, r);

  int index = 0;
  for (cycle = deadlocks; cycle != NULL; cycle = cycle->next()) {
    GrowableArray<JavaThread*>* deadlock_threads = cycle->threads();
    int len = deadlock_threads->length();
    for (int i = 0; i < len; i++) {
      threads_ah->obj_at_put(index, deadlock_threads->at(i)->threadObj());
      index++;
    }
  }
  return threads_ah;
}

// Finds cycles of threads that are deadlocked involved in object monitors
// and JSR-166 synchronizers.
// Returns an array of Thread objects which are in deadlock, if any.
// Otherwise, returns NULL.
//
// Input parameter:
//    object_monitors_only - if true, only check object monitors
//
JVM_ENTRY(jobjectArray, jmm_FindDeadlockedThreads(JNIEnv *env, jboolean object_monitors_only))
  Handle result = find_deadlocks(object_monitors_only != 0, CHECK_0);
  return (jobjectArray) JNIHandles::make_local(env, result());
JVM_END

// Finds cycles of threads that are deadlocked on monitor locks
// Returns an array of Thread objects which are in deadlock, if any.
// Otherwise, returns NULL.
JVM_ENTRY(jobjectArray, jmm_FindMonitorDeadlockedThreads(JNIEnv *env))
  Handle result = find_deadlocks(true, CHECK_0);
  return (jobjectArray) JNIHandles::make_local(env, result());
JVM_END

// Gets the information about GC extension attributes including
// the name of the attribute, its type, and a short description.
//
// Input parameters:
//   mgr   - GC memory manager
//   info  - caller allocated array of jmmExtAttributeInfo
//   count - number of elements of the info array
//
// Returns the number of GC extension attributes filled in the info array; or
// -1 if info is not big enough
//
JVM_ENTRY(jint, jmm_GetGCExtAttributeInfo(JNIEnv *env, jobject mgr, jmmExtAttributeInfo* info, jint count))
  // All GC memory managers have 1 attribute (number of GC threads)
  if (count == 0) {
    return 0;
  }

  if (info == NULL) {
   THROW_(vmSymbols::java_lang_NullPointerException(), 0);
  }

  info[0].name = "GcThreadCount";
  info[0].type = 'I';
  info[0].description = "Number of GC threads";
  return 1;
JVM_END

// verify the given array is an array of java/lang/management/MemoryUsage objects
// of a given length and return the objArrayOop
static objArrayOop get_memory_usage_objArray(jobjectArray array, int length, TRAPS) {
  if (array == NULL) {
    THROW_(vmSymbols::java_lang_NullPointerException(), 0);
  }

  objArrayOop oa = objArrayOop(JNIHandles::resolve_non_null(array));
  objArrayHandle array_h(THREAD, oa);

  // array must be of the given length
  if (length != array_h->length()) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "The length of the given MemoryUsage array does not match the number of memory pools.", 0);
  }

  // check if the element of array is of type MemoryUsage class
  klassOop usage_klass = Management::java_lang_management_MemoryUsage_klass(CHECK_0);
  klassOop element_klass = objArrayKlass::cast(array_h->klass())->element_klass();
  if (element_klass != usage_klass) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "The element type is not MemoryUsage class", 0);
  }

  return array_h();
}

// Gets the statistics of the last GC of a given GC memory manager.
// Input parameters:
//   obj     - GarbageCollectorMXBean object
//   gc_stat - caller allocated jmmGCStat where:
//     a. before_gc_usage - array of MemoryUsage objects
//     b. after_gc_usage  - array of MemoryUsage objects
//     c. gc_ext_attributes_values_size is set to the
//        gc_ext_attribute_values array allocated
//     d. gc_ext_attribute_values is a caller allocated array of jvalue.
//
// On return,
//   gc_index == 0 indicates no GC statistics available
//
//   before_gc_usage and after_gc_usage - filled with per memory pool
//      before and after GC usage in the same order as the memory pools
//      returned by GetMemoryPools for a given GC memory manager.
//   num_gc_ext_attributes indicates the number of elements in
//      the gc_ext_attribute_values array is filled; or
//      -1 if the gc_ext_attributes_values array is not big enough
//
JVM_ENTRY(void, jmm_GetLastGCStat(JNIEnv *env, jobject obj, jmmGCStat *gc_stat))
  ResourceMark rm(THREAD);

  if (gc_stat->gc_ext_attribute_values_size > 0 && gc_stat->gc_ext_attribute_values == NULL) {
    THROW(vmSymbols::java_lang_NullPointerException());
  }

  // Get the GCMemoryManager
  GCMemoryManager* mgr = get_gc_memory_manager_from_jobject(obj, CHECK);
  if (mgr->last_gc_stat() == NULL) {
    gc_stat->gc_index = 0;
    return;
  }

  // Make a copy of the last GC statistics
  // GC may occur while constructing the last GC information
  int num_pools = MemoryService::num_memory_pools();
  GCStatInfo* stat = new GCStatInfo(num_pools);
  stat->copy_stat(mgr->last_gc_stat());

  gc_stat->gc_index = stat->gc_index();
  gc_stat->start_time = Management::ticks_to_ms(stat->start_time());
  gc_stat->end_time = Management::ticks_to_ms(stat->end_time());

  // Current implementation does not have GC extension attributes
  gc_stat->num_gc_ext_attributes = 0;

  // Fill the arrays of MemoryUsage objects with before and after GC
  // per pool memory usage
  objArrayOop bu = get_memory_usage_objArray(gc_stat->usage_before_gc,
                                             num_pools,
                                             CHECK);
  objArrayHandle usage_before_gc_ah(THREAD, bu);

  objArrayOop au = get_memory_usage_objArray(gc_stat->usage_after_gc,
                                             num_pools,
                                             CHECK);
  objArrayHandle usage_after_gc_ah(THREAD, au);

  for (int i = 0; i < num_pools; i++) {
    Handle before_usage = MemoryService::create_MemoryUsage_obj(stat->before_gc_usage_for_pool(i), CHECK);
    Handle after_usage;

    MemoryUsage u = stat->after_gc_usage_for_pool(i);
    if (u.max_size() == 0 && u.used() > 0) {
      // If max size == 0, this pool is a survivor space.
      // Set max size = -1 since the pools will be swapped after GC.
      MemoryUsage usage(u.init_size(), u.used(), u.committed(), (size_t)-1);
      after_usage = MemoryService::create_MemoryUsage_obj(usage, CHECK);
    } else {
      after_usage = MemoryService::create_MemoryUsage_obj(stat->after_gc_usage_for_pool(i), CHECK);
    }
    usage_before_gc_ah->obj_at_put(i, before_usage());
    usage_after_gc_ah->obj_at_put(i, after_usage());
  }

  if (gc_stat->gc_ext_attribute_values_size > 0) {
    // Current implementation only has 1 attribute (number of GC threads)
    // The type is 'I'
    gc_stat->gc_ext_attribute_values[0].i = mgr->num_gc_threads();
  }
JVM_END

// Dump heap - Returns 0 if succeeds.
JVM_ENTRY(jint, jmm_DumpHeap0(JNIEnv *env, jstring outputfile, jboolean live))
#ifndef SERVICES_KERNEL
  ResourceMark rm(THREAD);
  oop on = JNIHandles::resolve_external_guard(outputfile);
  if (on == NULL) {
    THROW_MSG_(vmSymbols::java_lang_NullPointerException(),
               "Output file name cannot be null.", -1);
  }
  char* name = java_lang_String::as_utf8_string(on);
  if (name == NULL) {
    THROW_MSG_(vmSymbols::java_lang_NullPointerException(),
               "Output file name cannot be null.", -1);
  }
  HeapDumper dumper(live ? true : false);
  if (dumper.dump(name) != 0) {
    const char* errmsg = dumper.error_as_C_string();
    THROW_MSG_(vmSymbols::java_io_IOException(), errmsg, -1);
  }
  return 0;
#else  // SERVICES_KERNEL
  return -1;
#endif // SERVICES_KERNEL
JVM_END

jlong Management::ticks_to_ms(jlong ticks) {
  assert(os::elapsed_frequency() > 0, "Must be non-zero");
  return (jlong)(((double)ticks / (double)os::elapsed_frequency())
                 * (double)1000.0);
}

const struct jmmInterface_1_ jmm_interface = {
  NULL,
  NULL,
  jmm_GetVersion,
  jmm_GetOptionalSupport,
  jmm_GetInputArguments,
  jmm_GetThreadInfo,
  jmm_GetInputArgumentArray,
  jmm_GetMemoryPools,
  jmm_GetMemoryManagers,
  jmm_GetMemoryPoolUsage,
  jmm_GetPeakMemoryPoolUsage,
  NULL,
  jmm_GetMemoryUsage,
  jmm_GetLongAttribute,
  jmm_GetBoolAttribute,
  jmm_SetBoolAttribute,
  jmm_GetLongAttributes,
  jmm_FindMonitorDeadlockedThreads,
  jmm_GetThreadCpuTime,
  jmm_GetVMGlobalNames,
  jmm_GetVMGlobals,
  jmm_GetInternalThreadTimes,
  jmm_ResetStatistic,
  jmm_SetPoolSensor,
  jmm_SetPoolThreshold,
  jmm_GetPoolCollectionUsage,
  jmm_GetGCExtAttributeInfo,
  jmm_GetLastGCStat,
  jmm_GetThreadCpuTimeWithKind,
  NULL,
  jmm_DumpHeap0,
  jmm_FindDeadlockedThreads,
  jmm_SetVMGlobal,
  NULL,
  jmm_DumpThreads
};

void* Management::get_jmm_interface(int version) {
  if (version == JMM_VERSION_1_0) {
    return (void*) &jmm_interface;
  }
  return NULL;
}
