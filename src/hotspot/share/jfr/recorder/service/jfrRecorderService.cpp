/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleCheckpoint.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointManager.hpp"
#include "jfr/recorder/checkpoint/jfrMetadataEvent.hpp"
#include "jfr/recorder/repository/jfrChunkRotation.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/repository/jfrRepository.hpp"
#include "jfr/recorder/service/jfrPostBox.hpp"
#include "jfr/recorder/service/jfrRecorderService.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/recorder/storage/jfrStorage.hpp"
#include "jfr/recorder/storage/jfrStorageControl.hpp"
#include "jfr/recorder/stringpool/jfrStringPool.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/writers/jfrJavaEventWriter.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"

// set data iff *dest == NULL
static bool try_set(void* const data, void** dest, bool clear) {
  assert(data != NULL, "invariant");
  const void* const current = OrderAccess::load_acquire(dest);
  if (current != NULL) {
    if (current != data) {
      // already set
      return false;
    }
    assert(current == data, "invariant");
    if (!clear) {
      // recursion disallowed
      return false;
    }
  }
  return Atomic::cmpxchg(clear ? NULL : data, dest, current) == current;
}

static void* rotation_thread = NULL;
static const int rotation_try_limit = 1000;
static const int rotation_retry_sleep_millis = 10;

class RotationLock : public StackObj {
 private:
  Thread* const _thread;
  bool _acquired;

  void log(bool recursion) {
    assert(!_acquired, "invariant");
    const char* error_msg = NULL;
    if (recursion) {
      error_msg = "Unable to issue rotation due to recursive calls.";
    }
    else {
      error_msg = "Unable to issue rotation due to wait timeout.";
    }
    log_info(jfr)( // For user, should not be "jfr, system"
      "%s", error_msg);
  }
 public:
  RotationLock(Thread* thread) : _thread(thread), _acquired(false) {
    assert(_thread != NULL, "invariant");
    if (_thread == rotation_thread) {
      // recursion not supported
      log(true);
      return;
    }

    // limited to not spin indefinitely
    for (int i = 0; i < rotation_try_limit; ++i) {
      if (try_set(_thread, &rotation_thread, false)) {
        _acquired = true;
        assert(_thread == rotation_thread, "invariant");
        return;
      }
      if (_thread->is_Java_thread()) {
        // in order to allow the system to move to a safepoint
        MutexLockerEx msg_lock(JfrMsg_lock);
        JfrMsg_lock->wait(false, rotation_retry_sleep_millis);
      }
      else {
        os::naked_short_sleep(rotation_retry_sleep_millis);
      }
    }
    log(false);
  }

  ~RotationLock() {
    assert(_thread != NULL, "invariant");
    if (_acquired) {
      assert(_thread == rotation_thread, "invariant");
      while (!try_set(_thread, &rotation_thread, true));
    }
  }
  bool not_acquired() const { return !_acquired; }
};

static intptr_t write_checkpoint_event_prologue(JfrChunkWriter& cw, u8 type_id) {
  const intptr_t prev_cp_offset = cw.previous_checkpoint_offset();
  const intptr_t prev_cp_relative_offset = 0 == prev_cp_offset ? 0 : prev_cp_offset - cw.current_offset();
  cw.reserve(sizeof(u4));
  cw.write<u8>(EVENT_CHECKPOINT);
  cw.write(JfrTicks::now());
  cw.write<jlong>((jlong)0);
  cw.write(prev_cp_relative_offset); // write previous checkpoint offset delta
  cw.write<bool>(false); // flushpoint
  cw.write<u4>((u4)1); // nof types in this checkpoint
  cw.write<u8>(type_id);
  const intptr_t number_of_elements_offset = cw.current_offset();
  cw.reserve(sizeof(u4));
  return number_of_elements_offset;
}

template <typename ContentFunctor>
class WriteCheckpointEvent : public StackObj {
 private:
  JfrChunkWriter& _cw;
  u8 _type_id;
  ContentFunctor& _content_functor;
 public:
  WriteCheckpointEvent(JfrChunkWriter& cw, u8 type_id, ContentFunctor& functor) :
    _cw(cw),
    _type_id(type_id),
    _content_functor(functor) {
    assert(_cw.is_valid(), "invariant");
  }
  bool process() {
    // current_cp_offset is also offset for the event size header field
    const intptr_t current_cp_offset = _cw.current_offset();
    const intptr_t num_elements_offset = write_checkpoint_event_prologue(_cw, _type_id);
    // invocation
    _content_functor.process();
    const u4 number_of_elements = (u4)_content_functor.processed();
    if (number_of_elements == 0) {
      // nothing to do, rewind writer to start
      _cw.seek(current_cp_offset);
      return true;
    }
    assert(number_of_elements > 0, "invariant");
    assert(_cw.current_offset() > num_elements_offset, "invariant");
    _cw.write_padded_at_offset<u4>(number_of_elements, num_elements_offset);
    _cw.write_padded_at_offset<u4>((u4)_cw.current_offset() - current_cp_offset, current_cp_offset);
    // update writer with last checkpoint position
    _cw.set_previous_checkpoint_offset(current_cp_offset);
    return true;
  }
};

template <typename Instance, size_t(Instance::*func)()>
class ServiceFunctor {
 private:
  Instance& _instance;
  size_t _processed;
 public:
  ServiceFunctor(Instance& instance) : _instance(instance), _processed(0) {}
  bool process() {
    _processed = (_instance.*func)();
    return true;
  }
  size_t processed() const { return _processed; }
};

template <typename Instance, void(Instance::*func)()>
class JfrVMOperation : public VM_Operation {
 private:
  Instance& _instance;
 public:
  JfrVMOperation(Instance& instance) : _instance(instance) {}
  void doit() { (_instance.*func)(); }
  VMOp_Type type() const { return VMOp_JFRCheckpoint; }
  Mode evaluation_mode() const { return _safepoint; } // default
};

class WriteStackTraceRepository : public StackObj {
 private:
  JfrStackTraceRepository& _repo;
  JfrChunkWriter& _cw;
  size_t _elements_processed;
  bool _clear;

 public:
  WriteStackTraceRepository(JfrStackTraceRepository& repo, JfrChunkWriter& cw, bool clear) :
    _repo(repo), _cw(cw), _elements_processed(0), _clear(clear) {}
  bool process() {
    _elements_processed = _repo.write(_cw, _clear);
    return true;
  }
  size_t processed() const { return _elements_processed; }
  void reset() { _elements_processed = 0; }
};

static bool recording = false;

static void set_recording_state(bool is_recording) {
  OrderAccess::storestore();
  recording = is_recording;
}

bool JfrRecorderService::is_recording() {
  return recording;
}

JfrRecorderService::JfrRecorderService() :
  _checkpoint_manager(JfrCheckpointManager::instance()),
  _chunkwriter(JfrRepository::chunkwriter()),
  _repository(JfrRepository::instance()),
  _stack_trace_repository(JfrStackTraceRepository::instance()),
  _storage(JfrStorage::instance()),
  _string_pool(JfrStringPool::instance()) {}

void JfrRecorderService::start() {
  RotationLock rl(Thread::current());
  if (rl.not_acquired()) {
    return;
  }
  log_debug(jfr, system)("Request to START recording");
  assert(!is_recording(), "invariant");
  clear();
  set_recording_state(true);
  assert(is_recording(), "invariant");
  open_new_chunk();
  log_debug(jfr, system)("Recording STARTED");
}

void JfrRecorderService::clear() {
  ResourceMark rm;
  HandleMark hm;
  pre_safepoint_clear();
  invoke_safepoint_clear();
  post_safepoint_clear();
}

void JfrRecorderService::pre_safepoint_clear() {
  _stack_trace_repository.clear();
  _string_pool.clear();
  _storage.clear();
}

void JfrRecorderService::invoke_safepoint_clear() {
  JfrVMOperation<JfrRecorderService, &JfrRecorderService::safepoint_clear> safepoint_task(*this);
  VMThread::execute(&safepoint_task);
}

//
// safepoint clear sequence
//
//  clear stacktrace repository ->
//    clear string pool ->
//      clear storage ->
//        shift epoch ->
//          update time
//
void JfrRecorderService::safepoint_clear() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  _stack_trace_repository.clear();
  _string_pool.clear();
  _storage.clear();
  _checkpoint_manager.shift_epoch();
  _chunkwriter.time_stamp_chunk_now();
}

void JfrRecorderService::post_safepoint_clear() {
  _checkpoint_manager.clear();
}

static void stop() {
  assert(JfrRecorderService::is_recording(), "invariant");
  log_debug(jfr, system)("Recording STOPPED");
  set_recording_state(false);
  assert(!JfrRecorderService::is_recording(), "invariant");
}

void JfrRecorderService::rotate(int msgs) {
  RotationLock rl(Thread::current());
  if (rl.not_acquired()) {
    return;
  }
  static bool vm_error = false;
  if (msgs & MSGBIT(MSG_VM_ERROR)) {
    vm_error = true;
    prepare_for_vm_error_rotation();
  }
  if (msgs & (MSGBIT(MSG_STOP))) {
    stop();
  }
  // action determined by chunkwriter state
  if (!_chunkwriter.is_valid()) {
    in_memory_rotation();
    return;
  }
  if (vm_error) {
    vm_error_rotation();
    return;
  }
  chunk_rotation();
}

void JfrRecorderService::prepare_for_vm_error_rotation() {
  if (!_chunkwriter.is_valid()) {
    open_new_chunk(true);
  }
  _checkpoint_manager.register_service_thread(Thread::current());
}

void JfrRecorderService::open_new_chunk(bool vm_error) {
  assert(!_chunkwriter.is_valid(), "invariant");
  assert(!JfrStream_lock->owned_by_self(), "invariant");
  JfrChunkRotation::on_rotation();
  MutexLockerEx stream_lock(JfrStream_lock, Mutex::_no_safepoint_check_flag);
  if (!_repository.open_chunk(vm_error)) {
    assert(!_chunkwriter.is_valid(), "invariant");
    _storage.control().set_to_disk(false);
    return;
  }
  assert(_chunkwriter.is_valid(), "invariant");
  _storage.control().set_to_disk(true);
}

void JfrRecorderService::in_memory_rotation() {
  assert(!_chunkwriter.is_valid(), "invariant");
  // currently running an in-memory recording
  open_new_chunk();
  if (_chunkwriter.is_valid()) {
    // dump all in-memory buffer data to the newly created chunk
    serialize_storage_from_in_memory_recording();
  }
}

void JfrRecorderService::serialize_storage_from_in_memory_recording() {
  assert(!JfrStream_lock->owned_by_self(), "not holding stream lock!");
  MutexLockerEx stream_lock(JfrStream_lock, Mutex::_no_safepoint_check_flag);
  _storage.write();
}

void JfrRecorderService::chunk_rotation() {
  finalize_current_chunk();
  open_new_chunk();
}

void JfrRecorderService::finalize_current_chunk() {
  assert(_chunkwriter.is_valid(), "invariant");
  write();
  assert(!_chunkwriter.is_valid(), "invariant");
}

void JfrRecorderService::write() {
  ResourceMark rm;
  HandleMark hm;
  pre_safepoint_write();
  invoke_safepoint_write();
  post_safepoint_write();
}

typedef ServiceFunctor<JfrStringPool, &JfrStringPool::write> WriteStringPool;
typedef ServiceFunctor<JfrStringPool, &JfrStringPool::write_at_safepoint> WriteStringPoolSafepoint;
typedef WriteCheckpointEvent<WriteStackTraceRepository> WriteStackTraceCheckpoint;
typedef WriteCheckpointEvent<WriteStringPool> WriteStringPoolCheckpoint;
typedef WriteCheckpointEvent<WriteStringPoolSafepoint> WriteStringPoolCheckpointSafepoint;

static void write_stacktrace_checkpoint(JfrStackTraceRepository& stack_trace_repo, JfrChunkWriter& chunkwriter, bool clear) {
  WriteStackTraceRepository write_stacktrace_repo(stack_trace_repo, chunkwriter, clear);
  WriteStackTraceCheckpoint write_stack_trace_checkpoint(chunkwriter, TYPE_STACKTRACE, write_stacktrace_repo);
  write_stack_trace_checkpoint.process();
}

static void write_stringpool_checkpoint(JfrStringPool& string_pool, JfrChunkWriter& chunkwriter) {
  WriteStringPool write_string_pool(string_pool);
  WriteStringPoolCheckpoint write_string_pool_checkpoint(chunkwriter, TYPE_STRING, write_string_pool);
  write_string_pool_checkpoint.process();
}

static void write_stringpool_checkpoint_safepoint(JfrStringPool& string_pool, JfrChunkWriter& chunkwriter) {
  WriteStringPoolSafepoint write_string_pool(string_pool);
  WriteStringPoolCheckpointSafepoint write_string_pool_checkpoint(chunkwriter, TYPE_STRING, write_string_pool);
  write_string_pool_checkpoint.process();
}

//
// pre-safepoint write sequence
//
//  lock stream lock ->
//    write non-safepoint dependent types ->
//      write checkpoint epoch transition list->
//        write stack trace checkpoint ->
//          write string pool checkpoint ->
//            write storage ->
//              release stream lock
//
void JfrRecorderService::pre_safepoint_write() {
  MutexLockerEx stream_lock(JfrStream_lock, Mutex::_no_safepoint_check_flag);
  assert(_chunkwriter.is_valid(), "invariant");
  _checkpoint_manager.write_types();
  _checkpoint_manager.write_epoch_transition_mspace();
  write_stacktrace_checkpoint(_stack_trace_repository, _chunkwriter, false);
  write_stringpool_checkpoint(_string_pool, _chunkwriter);
  _storage.write();
}

void JfrRecorderService::invoke_safepoint_write() {
  JfrVMOperation<JfrRecorderService, &JfrRecorderService::safepoint_write> safepoint_task(*this);
  VMThread::execute(&safepoint_task);
}

static void write_object_sample_stacktrace(JfrStackTraceRepository& stack_trace_repository) {
  WriteObjectSampleStacktrace object_sample_stacktrace(stack_trace_repository);
  object_sample_stacktrace.process();
}

//
// safepoint write sequence
//
//   lock stream lock ->
//     write object sample stacktraces ->
//       write stacktrace repository ->
//         write string pool ->
//           write safepoint dependent types ->
//             write storage ->
//                 shift_epoch ->
//                   update time ->
//                     lock metadata descriptor ->
//                       release stream lock
//
void JfrRecorderService::safepoint_write() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  MutexLockerEx stream_lock(JfrStream_lock, Mutex::_no_safepoint_check_flag);
  write_object_sample_stacktrace(_stack_trace_repository);
  write_stacktrace_checkpoint(_stack_trace_repository, _chunkwriter, true);
  write_stringpool_checkpoint_safepoint(_string_pool, _chunkwriter);
  _checkpoint_manager.write_safepoint_types();
  _storage.write_at_safepoint();
  _checkpoint_manager.shift_epoch();
  _chunkwriter.time_stamp_chunk_now();
  JfrMetadataEvent::lock();
}

static jlong write_metadata_event(JfrChunkWriter& chunkwriter) {
  assert(chunkwriter.is_valid(), "invariant");
  const jlong metadata_offset = chunkwriter.current_offset();
  JfrMetadataEvent::write(chunkwriter, metadata_offset);
  return metadata_offset;
}

//
// post-safepoint write sequence
//
//  lock stream lock ->
//    write type set ->
//      write checkpoints ->
//        write metadata event ->
//          write chunk header ->
//            close chunk fd ->
//              release stream lock
//
void JfrRecorderService::post_safepoint_write() {
  assert(_chunkwriter.is_valid(), "invariant");
  // During the safepoint tasks just completed, the system transitioned to a new epoch.
  // Type tagging is epoch relative which entails we are able to write out the
  // already tagged artifacts for the previous epoch. We can accomplish this concurrently
  // with threads now tagging artifacts in relation to the new, now updated, epoch and remain outside of a safepoint.
  _checkpoint_manager.write_type_set();
  MutexLockerEx stream_lock(JfrStream_lock, Mutex::_no_safepoint_check_flag);
  // serialize any outstanding checkpoint memory
  _checkpoint_manager.write();
  // serialize the metadata descriptor event and close out the chunk
  _repository.close_chunk(write_metadata_event(_chunkwriter));
  assert(!_chunkwriter.is_valid(), "invariant");
}

void JfrRecorderService::vm_error_rotation() {
  if (_chunkwriter.is_valid()) {
    finalize_current_chunk_on_vm_error();
    assert(!_chunkwriter.is_valid(), "invariant");
    _repository.on_vm_error();
  }
}

void JfrRecorderService::finalize_current_chunk_on_vm_error() {
  assert(_chunkwriter.is_valid(), "invariant");
  pre_safepoint_write();
  JfrMetadataEvent::lock();
  // Do not attempt safepoint dependent operations during emergency dump.
  // Optimistically write tagged artifacts.
  _checkpoint_manager.shift_epoch();
  _checkpoint_manager.write_type_set();
  // update time
  _chunkwriter.time_stamp_chunk_now();
  post_safepoint_write();
  assert(!_chunkwriter.is_valid(), "invariant");
}

void JfrRecorderService::process_full_buffers() {
  if (_chunkwriter.is_valid()) {
    assert(!JfrStream_lock->owned_by_self(), "invariant");
    MutexLockerEx stream_lock(JfrStream_lock, Mutex::_no_safepoint_check_flag);
    _storage.write_full();
  }
}

void JfrRecorderService::scavenge() {
  _storage.scavenge();
}

void JfrRecorderService::evaluate_chunk_size_for_rotation() {
  JfrChunkRotation::evaluate(_chunkwriter);
}
