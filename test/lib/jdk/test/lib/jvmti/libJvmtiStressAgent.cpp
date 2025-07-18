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
 */

#include <stddef.h>

#include "jvmti.h"
#include "jvmti_common.hpp"



#define JVMTI_AGENT_NAME "JvmtiStressAgent"


typedef struct {

  /* Monitor and flags to synchronize agent completion.*/
  jrawMonitorID finished_lock;
  volatile jboolean agent_request_stop;
  volatile jboolean is_agent_finished;

  /* Some configurable settings */

  // If agent enabled or not.
  jboolean is_agent_enabled;

  // If events testing is enabled.
  jboolean are_events_enabled;

  // If debugging functionality could be used.
  // Not compatible with debugger tests.
  jlong is_debugger_enabled;

  jint heap_sampling_interval;
  jint frequent_events_interval;

  /* Excluded events */
  jint* events_excluded;
  jsize events_excluded_size;

  /* Event statistics */
  jrawMonitorID events_lock; /* Monitor to register events and reset/read them */

  /* The counters are racy intentionally to avoid synchronization */
  jlong cbBreakpoint;
  jlong cbClassFileLoadHook;
  jlong cbClassLoad;
  jlong cbClassPrepare;
  jlong cbCompiledMethodLoad;
  jlong cbCompiledMethodUnload;
  jlong cbDataDumpRequest;
  jlong cbDynamicCodeGenerated;
  jlong cbException;
  jlong cbExceptionCatch;
  jlong cbFieldAccess;
  jlong cbFieldModification;
  jlong cbFramePop;
  jlong cbGarbageCollectionFinish;
  jlong cbGarbageCollectionStart;
  jlong cbMethodEntry;
  jlong cbMethodExit;
  jlong cbMonitorContendedEnter;
  jlong cbMonitorContendedEntered;
  jlong cbMonitorWait;
  jlong cbMonitorWaited;
  jlong cbNativeMethodBind;
  jlong cbObjectFree;
  jlong cbResourceExhausted;
  jlong cbSampledObjectAlloc;
  jlong cbSingleStep;
  jlong cbThreadEnd;
  jlong cbThreadStart;
  jlong cbVirtualThreadEnd;
  jlong cbVirtualThreadStart;
  jlong cbVMDeath;
  jlong cbVMInit;
  jlong cbVMObjectAlloc;

  /* Inspector statistics */
  jlong inspectedMethods;
  jlong inspectedVariables;

  FILE* log_file;
} ModuleData;

ModuleData *mdata;
static ModuleData*
mdata_init(jboolean is_debugger_enabled) {
  static ModuleData data;
  (void) memset(&data, 0, sizeof (ModuleData));

  data.is_debugger_enabled = is_debugger_enabled;
  data.log_file = fopen("JvmtiStressAgent.out", "w");

  data.agent_request_stop = JNI_FALSE;
  data.is_agent_finished = JNI_FALSE;

  /* Set jvmti stress properties */
  data.heap_sampling_interval = 1000;
  data.frequent_events_interval = 10;
  data.is_agent_enabled = JNI_TRUE;
  data.are_events_enabled = JNI_TRUE;


  if (data.is_debugger_enabled) {
    data.events_excluded_size = 0;
    data.events_excluded = nullptr;
  } else {
    data.events_excluded_size = 4;
    data.events_excluded = new jint[] {
      JVMTI_EVENT_BREAKPOINT,
      JVMTI_EVENT_FIELD_ACCESS,
      JVMTI_EVENT_FIELD_MODIFICATION,
      JVMTI_EVENT_SAMPLED_OBJECT_ALLOC,
    };
}


  return &data;
}

void
mdata_close() {
  free(mdata->events_excluded);
  fclose(mdata->log_file);
}
// uncomment to enable verbose logging
// #define DEBUG_ENABLED

// Internal buffer length for all messages
#define MESSAGE_LIMIT 16384

#define FAILURE_EXIT_CODE 74
void
debug(const char* format, ...) {
#ifdef DEBUG_ENABLED
  char dest[MESSAGE_LIMIT];
  va_list argptr;
  va_start(argptr, format);
  vsnprintf(dest, MESSAGE_LIMIT, format, argptr);
  va_end(argptr);
  printf("%s\n", dest);
  fprintf(mdata->log_file, "%s\n", dest);
  fflush(mdata->log_file);
#endif
}

/* Some helper functions to start/stop jvmti stress agent thread. */
void
check_jni_exception(JNIEnv *jni, const char *message) {
  jobject exception = jni->ExceptionOccurred();
  if (exception != nullptr) {
    jni->ExceptionDescribe();
    fatal(jni, message);
  }
}

jclass
find_class(JNIEnv *jni, const char *name) {
  char message[MESSAGE_LIMIT];
  jclass clazz = jni->FindClass(name);
  snprintf(message, MESSAGE_LIMIT, "Failed to find class %s.", name);
  check_jni_exception(jni, message);
  return clazz;
}

jmethodID
get_method_id(JNIEnv *jni, jclass clazz, const char *name, const char *sig) {
  char message[MESSAGE_LIMIT];
  jmethodID method = jni->GetMethodID(clazz, name, sig);
  snprintf(message, MESSAGE_LIMIT, "Failed to find method %s.", name);
  check_jni_exception(jni, message);
  return method;
}

void
create_agent_thread(jvmtiEnv *jvmti, JNIEnv *jni, const char *name, jvmtiStartFunction func) {
  jclass clazz = nullptr;
  jmethodID thread_ctor = nullptr;
  jthread thread = nullptr;
  jstring name_utf = nullptr;
  jvmtiError err = JVMTI_ERROR_NONE;

  check_jni_exception(jni, "JNIException before creating Agent Thread.");
  clazz = find_class(jni, "java/lang/Thread");
  thread_ctor = get_method_id(jni, clazz, "<init>",
                                    "(Ljava/lang/String;)V");

  name_utf = jni->NewStringUTF(name);
  check_jni_exception(jni, "Error creating utf name of thread.");

  thread = jni->NewObject(clazz, thread_ctor, name_utf);
  check_jni_exception(jni, "Error during instantiation of Thread object.");
  err = jvmti->RunAgentThread(
                     thread, func, nullptr, JVMTI_THREAD_NORM_PRIORITY);
  check_jvmti_status(jni, err, "RunAgentThread");
}

void
stop_agent_thread(jvmtiEnv *jvmti, JNIEnv *jni) {
  RawMonitorLocker rml(jvmti, jni, mdata->finished_lock);
  mdata->agent_request_stop = JNI_TRUE;
  while (!mdata->is_agent_finished) {
    rml.wait(1000);
  }
  debug("Native agent stopped");
}

static jboolean
should_stop(jvmtiEnv *jvmti, JNIEnv *jni ) {
  jboolean should_stop = JNI_FALSE;
  RawMonitorLocker rml(jvmti, jni, mdata->finished_lock);
  should_stop = mdata->agent_request_stop;
  if (should_stop == JNI_TRUE) {
    fflush(stdout);
    mdata->is_agent_finished = JNI_TRUE;
    rml.notify_all();
  }
  return should_stop;
}

/*
 * Agent stress functions.
 */

static void
trace_stack(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  jvmtiFrameInfo frames[5];
  jint count = 0;
  jvmtiError err = JVMTI_ERROR_NONE;

  debug("In stack_trace: %p", thread);
  err =jvmti->GetStackTrace(thread, 0, 5, frames, &count);
  if (err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
    return;
  }
  check_jvmti_error(err, "GetStackTrace");

  debug("Stack depth: %d", count);

  for (int frame_index = 0; frame_index < count; frame_index++) {
    char *method_name = nullptr;
    jint method_modifiers = 0;
    err =jvmti->GetMethodName(frames[frame_index].method, &method_name, nullptr, nullptr);
    check_jvmti_status(jni, err, "GetMethodName");

    err =jvmti->GetMethodModifiers(frames[frame_index].method, &method_modifiers);
    check_jvmti_status(jni, err, "GetMethodModifiers");

    debug("Inspecting method: %s, %d", method_name, method_modifiers);
    deallocate(jvmti, jni, method_name);

    jvmtiLocalVariableEntry* table = nullptr;
    jint entry_count = 0;
    err =jvmti->GetLocalVariableTable(frames[frame_index].method, &entry_count, &table);
    if (err == JVMTI_ERROR_NATIVE_METHOD || err == JVMTI_ERROR_ABSENT_INFORMATION
            || err == JVMTI_ERROR_WRONG_PHASE) {
      continue;
    }
    check_jvmti_status(jni, err, "GetLocalVariableTable");

    mdata->inspectedMethods += 1;
    mdata->inspectedVariables += entry_count;

    debug("Variables: ");
    for (int cnt = 0; cnt < (int) entry_count; cnt++) {
      debug(" %s  %d", table[cnt].name, (int) table[cnt].slot);
      deallocate(jvmti, jni, table[cnt].name);
      deallocate(jvmti, jni, table[cnt].signature);
      deallocate(jvmti, jni, table[cnt].generic_signature);
    }
    deallocate(jvmti, jni, table);
  }
  debug("---- End of stack inspection %d -----", count);
}

static void JNICALL
inspect_all_threads(jvmtiEnv *jvmti, JNIEnv *jni) {
    jint threads_count = 0;
    jthread *threads = nullptr;
    jvmtiError err = JVMTI_ERROR_NONE;
    debug("Inspect:  Starting cycle...");
    err =jvmti->GetAllThreads(&threads_count, &threads);
    check_jvmti_status(jni, err, "GetAllThreads");
    for (int t = 0; t < (int)threads_count; t++) {
      jvmtiThreadInfo info;
      debug("Inspecting thread num %d at addr [%p]",t, threads[t]);
      err = jvmti->GetThreadInfo(threads[t], &info);
      check_jvmti_status(jni, err, "GetThreadInfo");
      // Skip agent thread itself and JFR threads to avoid potential deadlocks
      if (strstr(info.name, JVMTI_AGENT_NAME) == nullptr
        && strstr(info.name, "JFR") == nullptr) {
        // The non-intrusive actions are allowed to ensure that results of target
        // thread are not affected.
        jthread thread = threads[t];
        trace_stack(jvmti, jni, thread);

        if (mdata->is_debugger_enabled) {
          debug("Inspect: Trying to suspend thread %s", info.name);
          err =jvmti->SuspendThread(thread);
          if (err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
            debug("Inspect:  thread %s is not alive. Skipping.", info.name);
            continue;
          }
          check_jvmti_status(jni, err, "SuspendThread");
          debug("Inspect:  Suspended thread %s", info.name);

          trace_stack(jvmti, jni, thread);

          debug("Inspect: Trying to resume thread %s", info.name);
          err =jvmti->ResumeThread(thread);
          check_jvmti_status(jni, err, "ResumeThread");
          debug("Inspect:  Resumed thread %s", info.name);
        }

      }
      deallocate(jvmti, jni, info.name);
      jni->DeleteLocalRef(info.thread_group);
      jni->DeleteLocalRef(info.context_class_loader);
      jni->DeleteLocalRef(threads[t]);
    }
    deallocate(jvmti, jni, threads);
}


static jint JNICALL
heap_iteration_callback(jlong class_tag, jlong size, jlong* tag_ptr, jint length, void* user_data) {
  int* count = (int*) user_data;
  *count += 1;
  return JVMTI_VISIT_OBJECTS;
}


static jint
get_heap_info(jvmtiEnv *jvmti, JNIEnv *jni, jclass klass) {
  jvmtiError err = JVMTI_ERROR_NONE;
  int count = 0;
  jvmtiHeapCallbacks callbacks;
  (void) memset(&callbacks, 0, sizeof (callbacks));
  callbacks.heap_iteration_callback = &heap_iteration_callback;
  err = jvmti->IterateThroughHeap(0, klass, &callbacks, &count);
  check_jvmti_status(jni, err, "IterateThroughHeap");
  return count;
}

int
is_event_frequent(int event) {
  return event == JVMTI_EVENT_SINGLE_STEP
      || event == JVMTI_EVENT_METHOD_ENTRY
      || event == JVMTI_EVENT_METHOD_EXIT
      || event == JVMTI_EVENT_FRAME_POP
      || event == JVMTI_EVENT_FIELD_ACCESS
      || event == JVMTI_EVENT_FIELD_MODIFICATION
      || event == JVMTI_EVENT_EXCEPTION_CATCH
      || event == JVMTI_EVENT_EXCEPTION
  ;
}

int
is_event_excluded(int event) {
  for (int i = 0; i < mdata->events_excluded_size; i++) {
    if (event == mdata->events_excluded[i]) {
      return JNI_TRUE;
    }
  }
  return JNI_FALSE;
}

/*
 * This helper method is used by enable_frequent_events() and enable_common_events().
 * It enables frequent OR non-frequent events.
 */
static void
enable_events(jvmtiEnv *jvmti, jboolean update_frequent_events) {
  if (!mdata->are_events_enabled) {
    return;
  }
  debug("Enabling events\n");
  jvmtiError err = JVMTI_ERROR_NONE;
  for(int event = JVMTI_MIN_EVENT_TYPE_VAL; event < JVMTI_MAX_EVENT_TYPE_VAL; event++) {
    if (is_event_excluded(event)) {
      debug("Event %d excluded.", event);
      continue;
    }
    if (is_event_frequent(event) != update_frequent_events ) {
      debug("Event %d is not enabled as frequent/slow.", event);
      continue;
    }
    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, static_cast<jvmtiEvent>(event), nullptr);
    check_jvmti_error(err, "SetEventNotificationMode");
  }
  debug("Enabling events done\n");
}

static void
enable_frequent_events(jvmtiEnv *jvmti) {
  enable_events(jvmti, JNI_TRUE);
}

static void
enable_common_events(jvmtiEnv *jvmti) {
  enable_events(jvmti,JNI_FALSE);
}


static void
disable_all_events(jvmtiEnv *jvmti) {
  jvmtiError err = JVMTI_ERROR_NONE;
  int event = JVMTI_MIN_EVENT_TYPE_VAL - 1;
  while(event++ < JVMTI_MAX_EVENT_TYPE_VAL) {
    err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, static_cast<jvmtiEvent>(event), nullptr);
    check_jvmti_error(err, "SetEventNotificationMode");
  }
}
static void JNICALL
stress_agent(jvmtiEnv *jvmti, JNIEnv *jni, void *p) {
  jvmtiError err = JVMTI_ERROR_NONE;
  debug("Debugger: Thread started.");
  while (!should_stop(jvmti, jni)) {
    enable_common_events(jvmti);

    // Iterate through heap and get some statistics
    jclass kls = find_class(jni, "java/lang/String");
    jlong obj_count = get_heap_info(jvmti, jni, kls);
    debug("Debugger: Heap info: %d", obj_count);

    // requires can_generate_sampled_object_alloc_events
    // which is solo capability
    err = jvmti->SetHeapSamplingInterval(mdata->heap_sampling_interval);
    check_jvmti_status(jni, err, "SetHeapSamplingInterval");

    inspect_all_threads(jvmti, jni);

    sleep_ms(100);

    err = jvmti->SetHeapSamplingInterval(0);
    check_jvmti_status(jni, err, "SetHeapSamplingInterval");

    // The frequent events
    enable_frequent_events(jvmti);
    sleep_ms(10);
    disable_all_events(jvmti);
    sleep_ms(100);
  }
  debug("Debugger: Thread finished.");
}


/*
 *  Events section.
 *  Most of the events just increase counter and print debug infoe.
 *  The VMInit/VMDeath are also start and stop jvmti stress agent.
 */

static void
register_event(jlong *event) {
  (*event)++;
}

static void JNICALL
cbVMInit(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  register_event(&mdata->cbVMInit);
  debug("Event cbVMInit\n");
  if (mdata->is_agent_enabled) {
    create_agent_thread(jvmti, jni, JVMTI_AGENT_NAME, &stress_agent);
  }
}

static void JNICALL
cbVMDeath(jvmtiEnv *jvmti, JNIEnv *jni) {
  register_event(&mdata->cbVMDeath);
  debug("Event cbVMDeath\n");
  if (mdata->is_agent_enabled) {
    stop_agent_thread(jvmti, jni);
  }
  destroy_raw_monitor(jvmti, jni, mdata->events_lock);
  destroy_raw_monitor(jvmti, jni, mdata->finished_lock);
}

static void JNICALL
cbThreadStart(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  register_event(&mdata->cbThreadStart);
  debug("Event cbThreadStart\n");
}

static void JNICALL
cbThreadEnd(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  register_event(&mdata->cbThreadEnd);
  debug("Event cbThreadEnd\n");
}

static void JNICALL
cbVirtualThreadStart(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  register_event(&mdata->cbThreadStart);
  debug(" cbThreadStart\n");
  debug("Event cbThreadStart\n");
}

static void JNICALL
cbVirtualThreadEnd(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread) {
  register_event(&mdata->cbThreadEnd);
  debug("Event cbThreadEnd\n");
}

static void JNICALL
cbClassFileLoadHook(jvmtiEnv *jvmti, JNIEnv* jni,
                    jclass class_being_redefined, jobject loader,
                    const char* name, jobject protection_domain,
                    jint class_data_len, const unsigned char *class_data,
                    jint *new_class_data_len, unsigned char **new_class_data) {
  unsigned char* new_class_data_copy = (unsigned char*) malloc(class_data_len);
  /* TODO uncomment for more stress
  memcpy(new_class_data_copy, class_data, class_data_len);
  *new_class_data_len = class_data_len;
  *new_class_data = new_class_data_copy;
  */
  register_event(&mdata->cbClassFileLoadHook);
  debug("Event cbClassFileLoadHook\n");
}

static void JNICALL
cbClassLoad(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jclass klass) {
  register_event(&mdata->cbClassLoad);
  debug("Event cbClassLoad\n");
}

static void JNICALL
cbClassPrepare(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jclass klass) {
  register_event(&mdata->cbClassPrepare);
  debug("Event cbClassPrepare\n");
}

static void JNICALL
cbDataDumpRequest(jvmtiEnv *jvmti) {
  register_event(&mdata->cbDataDumpRequest);
  debug("Event cbDataDumpRequest\n");
}

static void JNICALL
cbException(jvmtiEnv *jvmti,
            JNIEnv *jni,
            jthread thread,
            jmethodID method,
            jlocation location,
            jobject exception,
            jmethodID catch_method,
            jlocation catch_location) {
  register_event(&mdata->cbException);
  debug("Event cbException\n");
}

static void JNICALL
cbExceptionCatch(jvmtiEnv *jvmti, JNIEnv *jni,
                 jthread thread, jmethodID method, jlocation location,
                 jobject exception) {
  register_event(&mdata->cbExceptionCatch);
  debug("Event cbExceptionCatch\n");
}

static void JNICALL
cbMonitorWait(jvmtiEnv *jvmti, JNIEnv *jni,
              jthread thread, jobject object, jlong timeout) {
  register_event(&mdata->cbMonitorWait);
  debug("Event cbMonitorWait\n");
}

static void JNICALL
cbMonitorWaited(jvmtiEnv *jvmti, JNIEnv *jni,
                jthread thread, jobject object, jboolean timed_out) {
  register_event(&mdata->cbMonitorWaited);
  debug("Event cbMonitorWaited\n");
}

static void JNICALL
cbMonitorContendedEnter(jvmtiEnv *jvmti, JNIEnv *jni,
                        jthread thread, jobject object) {
  register_event(&mdata->cbMonitorContendedEnter);
  debug("Event cbMonitorContendedEnter\n");
}

static void JNICALL
cbMonitorContendedEntered(jvmtiEnv *jvmti, JNIEnv* jni,
                          jthread thread, jobject object) {
  register_event(&mdata->cbMonitorContendedEntered);
  debug("Event cbMonitorContendedEntered\n");
}

static void JNICALL
cbGarbageCollectionStart(jvmtiEnv *jvmti) {
  register_event(&mdata->cbGarbageCollectionStart);
  debug("Event cbGarbageCollectionStart\n");
}

static void JNICALL
cbGarbageCollectionFinish(jvmtiEnv *jvmti) {
  register_event(&mdata->cbGarbageCollectionFinish);
  debug("Event cbGarbageCollectionFinish\n");
}

static void JNICALL
cbObjectFree(jvmtiEnv *jvmti, jlong tag) {
  register_event(&mdata->cbObjectFree);
  debug("Event cbObjectFree\n");
}

static void JNICALL
cbBreakpoint(jvmtiEnv *jvmti,
             JNIEnv *jni,
             jthread thread,
             jmethodID method,
             jlocation location) {
  static long breakpoint_cnt = 0;
  register_event(&mdata->cbBreakpoint);
  debug("Event cbBreakpoint\n");
}

static void JNICALL
cbSingleStep(jvmtiEnv *jvmti,
             JNIEnv *jni,
             jthread thread,
             jmethodID method,
             jlocation location) {
  register_event(&mdata->cbSingleStep);
  debug("Event cbSingleStep\n");
}

static void JNICALL
cbFieldAccess(jvmtiEnv *jvmti,
              JNIEnv *jni,
              jthread thread,
              jmethodID method,
              jlocation location,
              jclass field_klass,
              jobject object,
              jfieldID field) {
  register_event(&mdata->cbFieldAccess);
  debug("Event cbFieldAccess\n");
}

static void JNICALL
cbFieldModification(jvmtiEnv *jvmti,
                    JNIEnv *jni,
                    jthread thread,
                    jmethodID method,
                    jlocation location,
                    jclass field_klass,
                    jobject object,
                    jfieldID field,
                    char signature_type,
                    jvalue new_value) {
  register_event(&mdata->cbFieldModification);
  debug("Event cbFieldModification\n");
}

static void JNICALL
cbFramePop(jvmtiEnv *jvmti,
           JNIEnv *jni,
           jthread thread,
           jmethodID method,
           jboolean was_popped_by_exception) {
  register_event(&mdata->cbFramePop);
  debug("Event cbFramePop\n");
}

static void JNICALL
cbMethodEntry(jvmtiEnv *jvmti,
              JNIEnv *jni,
              jthread thread,
              jmethodID method) {
  register_event(&mdata->cbMethodEntry);
  debug("Event cbMethodEntry\n");
}

static void JNICALL
cbMethodExit(jvmtiEnv *jvmti,
             JNIEnv *jni,
             jthread thread,
             jmethodID method,
             jboolean was_popped_by_exception,
             jvalue return_value) {
  register_event(&mdata->cbMethodExit);
  debug("Event cbMethodExit\n");
}

static void JNICALL
cbNativeMethodBind(jvmtiEnv *jvmti,
                   JNIEnv *jni,
                   jthread thread,
                   jmethodID method,
                   void* address,
                   void** new_address_ptr) {
  register_event(&mdata->cbNativeMethodBind);
  debug("Event cbNativeMethodBind\n");
}

static void JNICALL
cbCompiledMethodLoad(jvmtiEnv *jvmti,
                     jmethodID method,
                     jint code_size,
                     const void* code_addr,
                     jint map_length,
                     const jvmtiAddrLocationMap* map,
                     const void* compile_info) {
  register_event(&mdata->cbCompiledMethodLoad);
  debug("Event cbCompiledMethodLoad\n");
}

static void JNICALL
cbCompiledMethodUnload(jvmtiEnv *jvmti,
                       jmethodID method,
                       const void* code_addr) {
  register_event(&mdata->cbCompiledMethodUnload);
  debug("Event cbCompiledMethodUnload\n");
}

static void JNICALL
cbDynamicCodeGenerated(jvmtiEnv *jvmti,
                       const char* name,
                       const void* address,
                       jint length) {
  register_event(&mdata->cbDynamicCodeGenerated);
  debug("Event cbDynamicCodeGenerated\n");
}

static void JNICALL
cbResourceExhausted(jvmtiEnv *jvmti,
                    JNIEnv *jni,
                    jint flags,
                    const void* reserved,
                    const char* description) {
  register_event(&mdata->cbResourceExhausted);
  debug("Event cbResourceExhausted\n");
}

static void JNICALL
cbVMObjectAlloc(jvmtiEnv *jvmti,
                JNIEnv *jni,
                jthread thread,
                jobject object,
                jclass object_klass,
                jlong size) {
  register_event(&mdata->cbVMObjectAlloc);
  debug("Event cbVMObjectAlloc\n");
}

static void JNICALL
cbSampledObjectAlloc(jvmtiEnv *jvmti,
                     JNIEnv *jni,
                     jthread thread,
                     jobject object,
                     jclass object_klass,
                     jlong size) {
  register_event(&mdata->cbSampledObjectAlloc);
  debug("Event cbSampledObjectAlloc\n");
}



static void
set_callbacks(jvmtiEnv *jvmti, jboolean on) {
  jvmtiError err = JVMTI_ERROR_NONE;
  jvmtiEventCallbacks callbacks;

  (void) memset(&callbacks, 0, sizeof (callbacks));
  if (on == JNI_FALSE) {
    err = jvmti->SetEventCallbacks(&callbacks, (int) sizeof (jvmtiEventCallbacks));
    check_jvmti_error(err, "SetEventCallbacks");
    return;
  }
  callbacks.Breakpoint = &cbBreakpoint;
  callbacks.ClassFileLoadHook = &cbClassFileLoadHook;
  callbacks.ClassLoad = &cbClassLoad;
  callbacks.ClassPrepare = &cbClassPrepare;
  callbacks.CompiledMethodLoad = &cbCompiledMethodLoad;
  callbacks.CompiledMethodUnload = &cbCompiledMethodUnload;
  callbacks.DataDumpRequest = &cbDataDumpRequest;
  callbacks.DynamicCodeGenerated = &cbDynamicCodeGenerated;
  callbacks.Exception = &cbException;
  callbacks.ExceptionCatch = &cbExceptionCatch;
  callbacks.FieldAccess = &cbFieldAccess;
  callbacks.FieldModification = &cbFieldModification;
  callbacks.FramePop = &cbFramePop;
  callbacks.GarbageCollectionFinish = &cbGarbageCollectionFinish;
  callbacks.GarbageCollectionStart = &cbGarbageCollectionStart;
  callbacks.MethodEntry = &cbMethodEntry;
  callbacks.MethodExit = &cbMethodExit;
  callbacks.MonitorContendedEnter = &cbMonitorContendedEnter;
  callbacks.MonitorContendedEntered = &cbMonitorContendedEntered;
  callbacks.MonitorWait = &cbMonitorWait;
  callbacks.MonitorWaited = &cbMonitorWaited;
  callbacks.NativeMethodBind = &cbNativeMethodBind;
  callbacks.ObjectFree = &cbObjectFree;
  callbacks.ResourceExhausted = &cbResourceExhausted;
  callbacks.SampledObjectAlloc = &cbSampledObjectAlloc;
  callbacks.SingleStep = &cbSingleStep;
  callbacks.ThreadEnd = &cbThreadEnd;
  callbacks.ThreadStart = &cbThreadStart;
  callbacks.VirtualThreadEnd = &cbVirtualThreadEnd;
  callbacks.VirtualThreadStart = &cbVirtualThreadStart;
  callbacks.VMDeath = &cbVMDeath;
  callbacks.VMInit = &cbVMInit;
  callbacks.VMObjectAlloc = &cbVMObjectAlloc;
  err = jvmti->SetEventCallbacks(&callbacks, (int) sizeof (jvmtiEventCallbacks));
  check_jvmti_error(err, "SetEventCallbacks");
}

static
void get_capabilities(jvmtiEnv *jvmti) {
  jvmtiError err = JVMTI_ERROR_NONE;
  jvmtiCapabilities capabilities;
  (void) memset(&capabilities, 0, sizeof (capabilities));
  err = jvmti->GetPotentialCapabilities(&capabilities);

  if (!mdata->is_debugger_enabled) {
    //init_always_solo_capabilities
    capabilities.can_suspend = false;

    // onload_solo
    capabilities.can_generate_breakpoint_events = false;
    capabilities.can_generate_field_access_events = false;
    capabilities.can_generate_field_modification_events = false;
  }

  // can_generate_early_vmstart might impact VMStart for any agent
  // accordingly to spec, so don't enable it
  capabilities.can_generate_early_vmstart = false;

  check_jvmti_error(err, "GetPotentialCapabilities");
  err = jvmti->AddCapabilities(&capabilities);
  check_jvmti_error(err, "AddCapabilities");
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  jvmtiEnv *jvmti = nullptr;
  jint res = vm->GetEnv((void **) &jvmti, JVMTI_VERSION_21);
  if (res != JNI_OK) {
    return JNI_ERR;
  }

  bool debugger_enabled = true;

  if (options != nullptr) {
    char *opts = strdup(options);
    char *token = strtok(opts, ",");

    while (token != nullptr) {
      if (strncmp(token, "debugger=", 9) == 0) {
        if (strcmp(token + 9, "true") == 0)
          debugger_enabled = true;
        else
          debugger_enabled = false;
      }
      token = strtok(nullptr, ",");
    }
    free(opts);
  }

  debug("Debugger enabled: %s\n", debugger_enabled ? "true" : "false");
  mdata = mdata_init(debugger_enabled);

  get_capabilities(jvmti);

  mdata->finished_lock = create_raw_monitor(jvmti, "Finished lock");
  mdata->events_lock = create_raw_monitor(jvmti, "Events lock");
  set_callbacks(jvmti, JNI_TRUE);
  enable_common_events(jvmti);

  return JNI_OK;
}

JNIEXPORT void JNICALL
Agent_OnUnload(JavaVM *vm) {
  mdata_close();
}
