/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jfr.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/periodic/sampling/jfrThreadSampler.hpp"
#include "jfr/recorder/jfrEventSetting.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/checkpoint/jfrMetadataEvent.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/repository/jfrRepository.hpp"
#include "jfr/recorder/repository/jfrChunkRotation.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/repository/jfrEmergencyDump.hpp"
#include "jfr/recorder/service/jfrEventThrottler.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/service/jfrRecorderService.hpp"
#include "jfr/recorder/stacktrace/jfrStackFilter.hpp"
#include "jfr/recorder/stacktrace/jfrStackFilterRegistry.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/recorder/stringpool/jfrStringPool.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/jni/jfrJniMethodRegistration.hpp"
#include "jfr/instrumentation/jfrEventClassTransformer.hpp"
#include "jfr/instrumentation/jfrJvmtiAgent.hpp"
#include "jfr/leakprofiler/leakProfiler.hpp"
#include "jfr/support/jfrDeprecationManager.hpp"
#include "jfr/support/jfrJdkJfrEvent.hpp"
#include "jfr/support/jfrKlassUnloading.hpp"
#include "jfr/utilities/jfrJavaLog.hpp"
#include "jfr/utilities/jfrTimeConverter.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/writers/jfrJavaEventWriter.hpp"
#include "jfrfiles/jfrPeriodic.hpp"
#include "jfrfiles/jfrTypes.hpp"
#include "jni.h"
#include "jvm.h"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#ifdef LINUX
#include "osContainer_linux.hpp"
#include "os_linux.hpp"
#endif

#define NO_TRANSITION(result_type, header) extern "C" { result_type JNICALL header {
#define NO_TRANSITION_END } }

/*
 * NO_TRANSITION entries
 *
 * Thread remains _thread_in_native
 */

NO_TRANSITION(void, jfr_register_natives(JNIEnv* env, jclass jvm))
  JfrJniMethodRegistration register_native_methods(env);
NO_TRANSITION_END

NO_TRANSITION(jstring, jfr_get_pid(JNIEnv* env, jclass jvm))
  char pid_buf[32] = { 0 };
  jio_snprintf(pid_buf, sizeof(pid_buf), "%d", os::current_process_id());
  jstring pid_string = env->NewStringUTF(pid_buf);
  return pid_string; // exception pending if null
NO_TRANSITION_END

NO_TRANSITION(jlong, jfr_elapsed_frequency(JNIEnv* env, jclass jvm))
  return JfrTime::frequency();
NO_TRANSITION_END

NO_TRANSITION(jlong, jfr_elapsed_counter(JNIEnv* env, jclass jvm))
  return JfrTicks::now();
NO_TRANSITION_END

NO_TRANSITION(void, jfr_retransform_classes(JNIEnv* env, jclass jvm, jobjectArray classes))
  JfrJvmtiAgent::retransform_classes(env, classes, JavaThread::thread_from_jni_environment(env));
NO_TRANSITION_END

NO_TRANSITION(void, jfr_set_enabled(JNIEnv* env, jclass jvm, jlong event_type_id, jboolean enabled))
  JfrEventSetting::set_enabled(event_type_id, JNI_TRUE == enabled);
  if (EventOldObjectSample::eventId == event_type_id) {
    ThreadInVMfromNative transition(JavaThread::thread_from_jni_environment(env));
    if (JNI_TRUE == enabled) {
      LeakProfiler::start(JfrOptionSet::old_object_queue_size());
    } else {
      LeakProfiler::stop();
    }
  }
NO_TRANSITION_END

NO_TRANSITION(void, jfr_set_file_notification(JNIEnv* env, jclass jvm, jlong threshold))
  JfrChunkRotation::set_threshold(threshold);
NO_TRANSITION_END

NO_TRANSITION(void, jfr_set_stack_depth(JNIEnv* env, jclass jvm, jint depth))
  JfrOptionSet::set_stackdepth((jlong)depth);
NO_TRANSITION_END

NO_TRANSITION(void, jfr_set_stacktrace_enabled(JNIEnv* env, jclass jvm, jlong event_type_id, jboolean enabled))
  JfrEventSetting::set_stacktrace(event_type_id, JNI_TRUE == enabled);
NO_TRANSITION_END

NO_TRANSITION(void, jfr_set_global_buffer_count(JNIEnv* env, jclass jvm, jlong count))
  JfrOptionSet::set_num_global_buffers(count);
NO_TRANSITION_END

NO_TRANSITION(void, jfr_set_global_buffer_size(JNIEnv* env, jclass jvm, jlong size))
JfrOptionSet::set_global_buffer_size(size);
NO_TRANSITION_END

NO_TRANSITION(void, jfr_set_thread_buffer_size(JNIEnv* env, jclass jvm, jlong size))
  JfrOptionSet::set_thread_buffer_size(size);
NO_TRANSITION_END

NO_TRANSITION(void, jfr_set_memory_size(JNIEnv* env, jclass jvm, jlong size))
  JfrOptionSet::set_memory_size(size);
NO_TRANSITION_END

NO_TRANSITION(jboolean, jfr_set_threshold(JNIEnv* env, jclass jvm, jlong event_type_id, jlong thresholdTicks))
  return JfrEventSetting::set_threshold(event_type_id, thresholdTicks) ? JNI_TRUE : JNI_FALSE;
NO_TRANSITION_END

NO_TRANSITION(jboolean, jfr_allow_event_retransforms(JNIEnv* env, jclass jvm))
  return JfrOptionSet::allow_event_retransforms() ? JNI_TRUE : JNI_FALSE;
NO_TRANSITION_END

NO_TRANSITION(jboolean, jfr_is_available(JNIEnv* env, jclass jvm))
  return !Jfr::is_disabled() ? JNI_TRUE : JNI_FALSE;
NO_TRANSITION_END

NO_TRANSITION(jlong, jfr_get_unloaded_event_classes_count(JNIEnv* env, jclass jvm))
  return JfrKlassUnloading::event_class_count();
NO_TRANSITION_END

NO_TRANSITION(jdouble, jfr_time_conv_factor(JNIEnv* env, jclass jvm))
  return (jdouble)JfrTimeConverter::nano_to_counter_multiplier();
NO_TRANSITION_END

NO_TRANSITION(jboolean, jfr_set_throttle(JNIEnv* env, jclass jvm, jlong event_type_id, jlong event_sample_size, jlong period_ms))
  JfrEventThrottler::configure(static_cast<JfrEventId>(event_type_id), event_sample_size, period_ms);
  return JNI_TRUE;
NO_TRANSITION_END

NO_TRANSITION(void, jfr_set_miscellaneous(JNIEnv* env, jobject jvm, jlong event_type_id, jlong value))
  JfrEventSetting::set_miscellaneous(event_type_id, value);
  const JfrEventId typed_event_id = (JfrEventId)event_type_id;
  if (EventDeprecatedInvocation::eventId == typed_event_id) {
    JfrDeprecationManager::on_level_setting_update(value);
  }
NO_TRANSITION_END

NO_TRANSITION(jboolean, jfr_should_rotate_disk(JNIEnv* env, jclass jvm))
  return JfrChunkRotation::should_rotate() ? JNI_TRUE : JNI_FALSE;
NO_TRANSITION_END

NO_TRANSITION(jlong, jfr_get_type_id_from_string(JNIEnv * env, jclass jvm, jstring type))
  const char* type_name = env->GetStringUTFChars(type, nullptr);
  jlong id = JfrType::name_to_id(type_name);
  env->ReleaseStringUTFChars(type, type_name);
  return id;
NO_TRANSITION_END
/*
 * JVM_ENTRY_NO_ENV entries
 *
 * Transitions:
 *   Entry: _thread_in_native -> _thread_in_vm
 *   Exit:  _thread_in_vm -> _thread_in_native
 *
 * Current JavaThread available as "thread" variable
 */

JVM_ENTRY_NO_ENV(jboolean, jfr_create_jfr(JNIEnv* env, jclass jvm, jboolean simulate_failure))
  if (JfrRecorder::is_created()) {
    return JNI_TRUE;
  }
  if (!JfrRecorder::create(simulate_failure == JNI_TRUE)) {
    if (!thread->has_pending_exception()) {
      JfrJavaSupport::throw_illegal_state_exception("Unable to start Jfr", thread);
    }
    return JNI_FALSE;
  }
  return JNI_TRUE;
JVM_END

JVM_ENTRY_NO_ENV(jboolean, jfr_destroy_jfr(JNIEnv* env, jclass jvm))
  JfrRecorder::destroy();
  return JNI_TRUE;
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_begin_recording(JNIEnv* env, jclass jvm))
  if (JfrRecorder::is_recording()) {
    return;
  }
  JfrRecorder::start_recording();
JVM_END

JVM_ENTRY_NO_ENV(jboolean, jfr_is_recording(JNIEnv * env, jclass jvm))
  return JfrRecorder::is_recording() ? JNI_TRUE : JNI_FALSE;
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_end_recording(JNIEnv* env, jclass jvm))
  if (!JfrRecorder::is_recording()) {
    return;
  }
  JfrRecorder::stop_recording();
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_mark_chunk_final(JNIEnv * env, jclass jvm))
  JfrRepository::mark_chunk_final();
JVM_END

JVM_ENTRY_NO_ENV(jboolean, jfr_emit_event(JNIEnv* env, jclass jvm, jlong event_type_id, jlong timestamp, jlong periodic_type))
  JfrPeriodicEventSet::requestEvent((JfrEventId)event_type_id, timestamp, static_cast<PeriodicType>(periodic_type));
  return thread->has_pending_exception() ? JNI_FALSE : JNI_TRUE;
JVM_END

JVM_ENTRY_NO_ENV(jobject, jfr_get_all_event_classes(JNIEnv* env, jclass jvm))
  return JdkJfrEvent::get_all_klasses(thread);
JVM_END

JVM_ENTRY_NO_ENV(jlong, jfr_class_id(JNIEnv* env, jclass jvm, jclass jc))
  return JfrTraceId::load(jc);
JVM_END

JVM_ENTRY_NO_ENV(jlong, jfr_stacktrace_id(JNIEnv* env, jclass jvm, jint skip, jlong stack_filter_id))
  return JfrStackTraceRepository::record(thread, skip, stack_filter_id);
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_log(JNIEnv* env, jclass jvm, jint tag_set, jint level, jstring message))
  JfrJavaLog::log(tag_set, level, message, thread);
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_log_event(JNIEnv* env, jclass jvm, jint level, jobjectArray lines, jboolean system))
  JfrJavaLog::log_event(env, level, lines, system == JNI_TRUE, thread);
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_subscribe_log_level(JNIEnv* env, jclass jvm, jobject log_tag, jint id))
  JfrJavaLog::subscribe_log_level(log_tag, id, thread);
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_set_output(JNIEnv* env, jclass jvm, jstring path))
  JfrRepository::set_chunk_path(path, thread);
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_set_method_sampling_period(JNIEnv* env, jclass jvm, jlong type, jlong periodMillis))
  if (periodMillis < 0) {
    periodMillis = 0;
  }
  JfrEventId typed_event_id = (JfrEventId)type;
  assert(EventExecutionSample::eventId == typed_event_id || EventNativeMethodSample::eventId == typed_event_id, "invariant");
  JfrEventSetting::set_enabled(typed_event_id, periodMillis > 0);
  if (EventExecutionSample::eventId == type) {
    JfrThreadSampling::set_java_sample_period(periodMillis);
  } else {
    JfrThreadSampling::set_native_sample_period(periodMillis);
  }
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_store_metadata_descriptor(JNIEnv* env, jclass jvm, jbyteArray descriptor))
  JfrMetadataEvent::update(descriptor);
JVM_END

// trace thread id for a thread object
JVM_ENTRY_NO_ENV(jlong, jfr_id_for_thread(JNIEnv* env, jclass jvm, jobject t))
  return JfrJavaSupport::jfr_thread_id(thread, t);
JVM_END

JVM_ENTRY_NO_ENV(jobject, jfr_get_event_writer(JNIEnv* env, jclass jvm))
  return JfrJavaEventWriter::event_writer(thread);
JVM_END

JVM_ENTRY_NO_ENV(jobject, jfr_new_event_writer(JNIEnv* env, jclass jvm))
  return JfrJavaEventWriter::new_event_writer(thread);
JVM_END

NO_TRANSITION(void, jfr_event_writer_flush(JNIEnv* env, jclass jvm, jobject writer, jint used_size, jint requested_size))
  JfrJavaEventWriter::flush(writer, used_size, requested_size, JavaThread::current());
NO_TRANSITION_END

NO_TRANSITION(jlong, jfr_commit(JNIEnv* env, jclass jvm, jlong next_position))
  return JfrJavaEventWriter::commit(next_position);
NO_TRANSITION_END

JVM_ENTRY_NO_ENV(void, jfr_flush(JNIEnv* env, jclass jvm))
  JfrRepository::flush(thread);
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_set_repository_location(JNIEnv* env, jclass jvm, jstring location))
  return JfrRepository::set_path(location, thread);
JVM_END

NO_TRANSITION(void, jfr_set_dump_path(JNIEnv* env, jclass jvm, jstring dumppath))
  if (dumppath == nullptr) {
    JfrEmergencyDump::set_dump_path(nullptr);
  } else {
    const char* dump_path = env->GetStringUTFChars(dumppath, nullptr);
    JfrEmergencyDump::set_dump_path(dump_path);
    env->ReleaseStringUTFChars(dumppath, dump_path);
  }
NO_TRANSITION_END

NO_TRANSITION(jstring, jfr_get_dump_path(JNIEnv* env, jclass jvm))
  return env->NewStringUTF(JfrEmergencyDump::get_dump_path());
NO_TRANSITION_END

JVM_ENTRY_NO_ENV(void, jfr_uncaught_exception(JNIEnv* env, jclass jvm, jobject t, jthrowable throwable))
  JfrJavaSupport::uncaught_exception(throwable, thread);
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_abort(JNIEnv* env, jclass jvm, jstring errorMsg))
  JfrJavaSupport::abort(errorMsg, thread);
JVM_END

JVM_ENTRY_NO_ENV(jlong, jfr_type_id(JNIEnv* env, jclass jvm, jclass jc))
  return JfrTraceId::load_raw(jc);
JVM_END

JVM_ENTRY_NO_ENV(jboolean, jfr_add_string_constant(JNIEnv* env, jclass jvm, jlong id, jstring string))
  return JfrStringPool::add(id, string, thread);
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_set_force_instrumentation(JNIEnv* env, jclass jvm, jboolean force_instrumentation))
  JfrEventClassTransformer::set_force_instrumentation(force_instrumentation == JNI_TRUE);
JVM_END

NO_TRANSITION(void, jfr_emit_old_object_samples(JNIEnv* env, jclass jvm, jlong cutoff_ticks, jboolean emit_all, jboolean skip_bfs))
  JfrRecorderService service;
  service.emit_leakprofiler_events(cutoff_ticks, emit_all == JNI_TRUE, skip_bfs == JNI_TRUE);
NO_TRANSITION_END

JVM_ENTRY_NO_ENV(void, jfr_exclude_thread(JNIEnv* env, jclass jvm, jobject t))
  JfrJavaSupport::exclude(thread, t);
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_include_thread(JNIEnv* env, jclass jvm, jobject t))
  JfrJavaSupport::include(thread, t);
JVM_END

JVM_ENTRY_NO_ENV(jboolean, jfr_is_thread_excluded(JNIEnv* env, jclass jvm, jobject t))
  return JfrJavaSupport::is_excluded(t);
JVM_END

JVM_ENTRY_NO_ENV(jlong, jfr_chunk_start_nanos(JNIEnv* env, jclass jvm))
  return JfrRepository::current_chunk_start_nanos();
JVM_END

JVM_ENTRY_NO_ENV(jobject, jfr_get_configuration(JNIEnv * env, jclass jvm, jobject clazz))
  return JfrJavaSupport::get_configuration(clazz, thread);
JVM_END

JVM_ENTRY_NO_ENV(jboolean, jfr_set_configuration(JNIEnv * env, jclass jvm, jobject clazz, jobject configuration))
  return JfrJavaSupport::set_configuration(clazz, configuration, thread);
JVM_END

JVM_ENTRY_NO_ENV(jboolean, jfr_is_class_excluded(JNIEnv * env, jclass jvm, jclass clazz))
  return JdkJfrEvent::is_excluded(clazz);
JVM_END

JVM_ENTRY_NO_ENV(jboolean, jfr_is_class_instrumented(JNIEnv* env, jclass jvm, jclass clazz))
  return JfrJavaSupport::is_instrumented(clazz, thread);
JVM_END

JVM_ENTRY_NO_ENV(jboolean, jfr_is_containerized(JNIEnv* env, jclass jvm))
#ifdef LINUX
  return OSContainer::is_containerized();
#else
  return false;
#endif
JVM_END

JVM_ENTRY_NO_ENV(jlong, jfr_host_total_memory(JNIEnv* env, jclass jvm))
#ifdef LINUX
  // We want the host memory, not the container limit.
  // os::physical_memory() would return the container limit.
  return os::Linux::physical_memory();
#else
  return os::physical_memory();
#endif
JVM_END

JVM_ENTRY_NO_ENV(jlong, jfr_host_total_swap_memory(JNIEnv* env, jclass jvm))
#ifdef LINUX
  // We want the host swap memory, not the container value.
  return os::Linux::host_swap();
#else
  return os::total_swap_space();
#endif
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_emit_data_loss(JNIEnv* env, jclass jvm, jlong bytes))
  EventDataLoss::commit(bytes, min_jlong);
JVM_END

JVM_ENTRY_NO_ENV(jlong, jfr_register_stack_filter(JNIEnv* env,  jclass jvm, jobjectArray classes, jobjectArray methods))
  return JfrStackFilterRegistry::add(classes, methods, thread);
JVM_END

JVM_ENTRY_NO_ENV(void, jfr_unregister_stack_filter(JNIEnv* env,  jclass jvm, jlong id))
  JfrStackFilterRegistry::remove(id);
JVM_END
