/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/dcmd/jfrDcmds.hpp"
#include "jfr/instrumentation/jfrJvmtiAgent.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/periodic/jfrOSInterface.hpp"
#include "jfr/periodic/sampling/jfrThreadSampler.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointManager.hpp"
#include "jfr/recorder/repository/jfrRepository.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/service/jfrPostBox.hpp"
#include "jfr/recorder/service/jfrRecorderService.hpp"
#include "jfr/recorder/service/jfrRecorderThread.hpp"
#include "jfr/recorder/storage/jfrStorage.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/recorder/stringpool/jfrStringPool.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/writers/jfrJavaEventWriter.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/globals.hpp"

static bool is_disabled_on_command_line() {
  static const size_t length = strlen("FlightRecorder");
  static JVMFlag* const flight_recorder_flag = JVMFlag::find_flag("FlightRecorder", length);
  assert(flight_recorder_flag != NULL, "invariant");
  return flight_recorder_flag->is_command_line() ? !FlightRecorder : false;
}

bool JfrRecorder::is_disabled() {
  return is_disabled_on_command_line();
}

static bool set_flight_recorder_flag(bool flag_value) {
  JVMFlag::boolAtPut((char*)"FlightRecorder", &flag_value, JVMFlag::MANAGEMENT);
  return FlightRecorder;
}

static bool _enabled = false;

static bool enable() {
  assert(!_enabled, "invariant");
  _enabled = set_flight_recorder_flag(true);
  return _enabled;
}

bool JfrRecorder::is_enabled() {
  return _enabled;
}

bool JfrRecorder::on_vm_init() {
  if (!is_disabled()) {
    if (FlightRecorder || StartFlightRecording != NULL) {
      enable();
    }
  }
  // fast time initialization
  return JfrTime::initialize();
}

static JfrStartFlightRecordingDCmd* _startup_recording = NULL;

static void release_startup_recording() {
  if (_startup_recording != NULL) {
    delete _startup_recording;
    _startup_recording = NULL;
  }
}

static void teardown_startup_support() {
  release_startup_recording();
  JfrOptionSet::release_startup_recordings();
}


// Parsing options here to detect errors as soon as possible
static bool parse_recording_options(const char* options, TRAPS) {
  assert(options != NULL, "invariant");
  if (_startup_recording != NULL) {
    delete _startup_recording;
  }
  CmdLine cmdline(options, strlen(options), true);
  _startup_recording = new (ResourceObj::C_HEAP, mtTracing) JfrStartFlightRecordingDCmd(tty, true);
  assert(_startup_recording != NULL, "invariant");
  _startup_recording->parse(&cmdline, ',', THREAD);
  if (HAS_PENDING_EXCEPTION) {
    java_lang_Throwable::print(PENDING_EXCEPTION, tty);
    CLEAR_PENDING_EXCEPTION;
    return false;
  }
  return true;
}

static bool validate_recording_options(TRAPS) {
  const GrowableArray<const char*>* startup_options = JfrOptionSet::startup_recordings();
  if (startup_options == NULL) {
    return true;
  }
  const int length = startup_options->length();
  assert(length >= 1, "invariant");
  for (int i = 0; i < length; ++i) {
    if (!parse_recording_options(startup_options->at(i), THREAD)) {
      return false;
    }
  }
  return true;
}

static bool launch_recording(TRAPS) {
  assert(_startup_recording != NULL, "invariant");
  log_trace(jfr, system)("Starting a recording");
  _startup_recording->execute(DCmd_Source_Internal, Thread::current());
  if (HAS_PENDING_EXCEPTION) {
    log_debug(jfr, system)("Exception while starting a recording");
    CLEAR_PENDING_EXCEPTION;
    return false;
  }
  log_trace(jfr, system)("Finished starting a recording");
  return true;
}

static bool launch_recordings(const GrowableArray<const char*>* startup_options, TRAPS) {
  assert(startup_options != NULL, "invariant");
  const int length = startup_options->length();
  assert(length >= 1, "invariant");
  if (length == 1) {
    // already parsed and ready, launch it
    return launch_recording(THREAD);
  }
  for (int i = 0; i < length; ++i) {
    parse_recording_options(startup_options->at(i), THREAD);
    if (!launch_recording(THREAD)) {
      return false;
    }
  }
  return true;
}

static bool startup_recordings(TRAPS) {
  const GrowableArray<const char*>* startup_options = JfrOptionSet::startup_recordings();
  if (startup_options == NULL) {
    return true;
  }
  const bool ret = launch_recordings(startup_options, THREAD);
  teardown_startup_support();
  return ret;
}

static void log_jdk_jfr_module_resolution_error(TRAPS) {
  LogTarget(Error, jfr, system) lt_error;
  LogTargetHandle handle(lt_error);
  LogStream stream(handle);
  JfrJavaSupport::is_jdk_jfr_module_available(&stream, THREAD);
}

bool JfrRecorder::on_vm_start() {
  if (DumpSharedSpaces && (JfrOptionSet::startup_recordings() != NULL)) {
    warning("JFR will be disabled during CDS dumping");
    teardown_startup_support();
    return true;
  }
  const bool in_graph = JfrJavaSupport::is_jdk_jfr_module_available();
  Thread* const thread = Thread::current();
  if (!JfrOptionSet::initialize(thread)) {
    return false;
  }
  if (!register_jfr_dcmds()) {
    return false;
  }
  if (!validate_recording_options(thread)) {
    return false;
  }
  if (in_graph) {
    if (!JfrJavaEventWriter::initialize()) {
      return false;
    }
    if (!JfrOptionSet::configure(thread)) {
      return false;
    }
  }
  if (!is_enabled()) {
    return true;
  }
  if (!in_graph) {
    log_jdk_jfr_module_resolution_error(thread);
    return false;
  }
  return startup_recordings(thread);
}

static bool _created = false;

//
// Main entry point for starting Jfr functionality.
// Non-protected initializations assume single-threaded setup.
//
bool JfrRecorder::create(bool simulate_failure) {
  assert(!is_disabled(), "invariant");
  assert(!is_created(), "invariant");
  if (!is_enabled()) {
    enable();
  }
  if (!create_components() || simulate_failure) {
    destroy_components();
    return false;
  }
  if (!create_recorder_thread()) {
    destroy_components();
    return false;
  }
  _created = true;
  return true;
}

bool JfrRecorder::is_created() {
  return _created;
}

bool JfrRecorder::create_components() {
  ResourceMark rm;
  HandleMark hm;

  if (!create_jvmti_agent()) {
    return false;
  }
  if (!create_post_box()) {
    return false;
  }
  if (!create_chunk_repository()) {
    return false;
  }
  if (!create_storage()) {
    return false;
  }
  if (!create_checkpoint_manager()) {
    return false;
  }
  if (!create_stacktrace_repository()) {
    return false;
  }
  if (!create_os_interface()) {
    return false;
  }
  if (!create_stringpool()) {
    return false;
  }
  if (!create_thread_sampling()) {
    return false;
  }
  return true;
}

// subsystems
static JfrJvmtiAgent* _jvmti_agent = NULL;
static JfrPostBox* _post_box = NULL;
static JfrStorage* _storage = NULL;
static JfrCheckpointManager* _checkpoint_manager = NULL;
static JfrRepository* _repository = NULL;
static JfrStackTraceRepository* _stack_trace_repository;
static JfrStringPool* _stringpool = NULL;
static JfrOSInterface* _os_interface = NULL;
static JfrThreadSampling* _thread_sampling = NULL;

bool JfrRecorder::create_jvmti_agent() {
  return JfrOptionSet::allow_retransforms() ? JfrJvmtiAgent::create() : true;
}

bool JfrRecorder::create_post_box() {
  assert(_post_box == NULL, "invariant");
  _post_box = JfrPostBox::create();
  return _post_box != NULL;
}

bool JfrRecorder::create_chunk_repository() {
  assert(_repository == NULL, "invariant");
  assert(_post_box != NULL, "invariant");
  _repository = JfrRepository::create(*_post_box);
  return _repository != NULL && _repository->initialize();
}

bool JfrRecorder::create_os_interface() {
  assert(_os_interface == NULL, "invariant");
  _os_interface = JfrOSInterface::create();
  return _os_interface != NULL && _os_interface->initialize();
}

bool JfrRecorder::create_storage() {
  assert(_repository != NULL, "invariant");
  assert(_post_box != NULL, "invariant");
  _storage = JfrStorage::create(_repository->chunkwriter(), *_post_box);
  return _storage != NULL && _storage->initialize();
}

bool JfrRecorder::create_checkpoint_manager() {
  assert(_checkpoint_manager == NULL, "invariant");
  assert(_repository != NULL, "invariant");
  _checkpoint_manager = JfrCheckpointManager::create(_repository->chunkwriter());
  return _checkpoint_manager != NULL && _checkpoint_manager->initialize();
}

bool JfrRecorder::create_stacktrace_repository() {
  assert(_stack_trace_repository == NULL, "invariant");
  _stack_trace_repository = JfrStackTraceRepository::create();
  return _stack_trace_repository != NULL && _stack_trace_repository->initialize();
}

bool JfrRecorder::create_stringpool() {
  assert(_stringpool == NULL, "invariant");
  assert(_repository != NULL, "invariant");
  _stringpool = JfrStringPool::create(_repository->chunkwriter());
  return _stringpool != NULL && _stringpool->initialize();
}

bool JfrRecorder::create_thread_sampling() {
  assert(_thread_sampling == NULL, "invariant");
  _thread_sampling = JfrThreadSampling::create();
  return _thread_sampling != NULL;
}

void JfrRecorder::destroy_components() {
  JfrJvmtiAgent::destroy();
  if (_post_box != NULL) {
    JfrPostBox::destroy();
    _post_box = NULL;
  }
  if (_repository != NULL) {
    JfrRepository::destroy();
    _repository = NULL;
  }
  if (_storage != NULL) {
    JfrStorage::destroy();
    _storage = NULL;
  }
  if (_checkpoint_manager != NULL) {
    JfrCheckpointManager::destroy();
    _checkpoint_manager = NULL;
  }
  if (_stack_trace_repository != NULL) {
    JfrStackTraceRepository::destroy();
    _stack_trace_repository = NULL;
  }
  if (_stringpool != NULL) {
    JfrStringPool::destroy();
    _stringpool = NULL;
  }
  if (_os_interface != NULL) {
    JfrOSInterface::destroy();
    _os_interface = NULL;
  }
  if (_thread_sampling != NULL) {
    JfrThreadSampling::destroy();
    _thread_sampling = NULL;
  }
}

bool JfrRecorder::create_recorder_thread() {
  return JfrRecorderThread::start(_checkpoint_manager, _post_box, Thread::current());
}

void JfrRecorder::destroy() {
  assert(is_created(), "invariant");
  _post_box->post(MSG_SHUTDOWN);
  JfrJvmtiAgent::destroy();
}

void JfrRecorder::on_recorder_thread_exit() {
  assert(!is_recording(), "invariant");
  // intent is to destroy the recorder instance and components,
  // but need sensitive coordination not yet in place
  //
  // destroy_components();
  //
  log_debug(jfr, system)("Recorder thread STOPPED");
}

void JfrRecorder::start_recording() {
  _post_box->post(MSG_START);
}

bool JfrRecorder::is_recording() {
  return JfrRecorderService::is_recording();
}

void JfrRecorder::stop_recording() {
  _post_box->post(MSG_STOP);
}
