/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jfrEvents.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/leakprofiler/leakProfiler.hpp"
#include "jfr/recorder/repository/jfrEmergencyDump.hpp"
#include "jfr/recorder/service/jfrPostBox.hpp"
#include "jfr/recorder/service/jfrRecorderService.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/growableArray.hpp"

static const char vm_error_filename_fmt[] = "hs_err_pid%p.jfr";
static const char vm_oom_filename_fmt[] = "hs_oom_pid%p.jfr";
static const char vm_soe_filename_fmt[] = "hs_soe_pid%p.jfr";
static const char chunk_file_jfr_ext[] = ".jfr";
static const size_t iso8601_len = 19; // "YYYY-MM-DDTHH:MM:SS"

static fio_fd open_exclusivly(const char* path) {
  return os::open(path, O_CREAT | O_RDWR, S_IREAD | S_IWRITE);
}

static int file_sort(const char** const file1, const char** file2) {
  assert(NULL != *file1 && NULL != *file2, "invariant");
  int cmp = strncmp(*file1, *file2, iso8601_len);
  if (0 == cmp) {
    const char* const dot1 = strchr(*file1, '.');
    assert(NULL != dot1, "invariant");
    const char* const dot2 = strchr(*file2, '.');
    assert(NULL != dot2, "invariant");
    ptrdiff_t file1_len = dot1 - *file1;
    ptrdiff_t file2_len = dot2 - *file2;
    if (file1_len < file2_len) {
      return -1;
    }
    if (file1_len > file2_len) {
      return 1;
    }
    assert(file1_len == file2_len, "invariant");
    cmp = strncmp(*file1, *file2, file1_len);
  }
  assert(cmp != 0, "invariant");
  return cmp;
}

static void iso8601_to_date_time(char* iso8601_str) {
  assert(iso8601_str != NULL, "invariant");
  assert(strlen(iso8601_str) == iso8601_len, "invariant");
  // "YYYY-MM-DDTHH:MM:SS"
  for (size_t i = 0; i < iso8601_len; ++i) {
    switch (iso8601_str[i]) {
    case 'T':
    case '-':
    case ':':
      iso8601_str[i] = '_';
      break;
    }
  }
  // "YYYY_MM_DD_HH_MM_SS"
}

static void date_time(char* buffer, size_t buffer_len) {
  assert(buffer != NULL, "invariant");
  assert(buffer_len >= iso8601_len, "buffer too small");
  os::iso8601_time(buffer, buffer_len);
  assert(strlen(buffer) >= iso8601_len + 1, "invariant");
  // "YYYY-MM-DDTHH:MM:SS"
  buffer[iso8601_len] = '\0';
  iso8601_to_date_time(buffer);
}

static int64_t file_size(fio_fd fd) {
  assert(fd != invalid_fd, "invariant");
  const int64_t current_offset = os::current_file_offset(fd);
  const int64_t size = os::lseek(fd, 0, SEEK_END);
  os::seek_to_file_offset(fd, current_offset);
  return size;
}

class RepositoryIterator : public StackObj {
 private:
  const char* const _repo;
  const size_t _repository_len;
  GrowableArray<const char*>* _files;
  const char* const fully_qualified(const char* entry) const;
  mutable int _iterator;

 public:
  RepositoryIterator(const char* repository, size_t repository_len);
  ~RepositoryIterator() {}
  const char* const filter(const char* entry) const;
  bool has_next() const;
  const char* const next() const;
};

const char* const RepositoryIterator::fully_qualified(const char* entry) const {
  assert(NULL != entry, "invariant");
  char* file_path_entry = NULL;
  // only use files that have content, not placeholders
  const char* const file_separator = os::file_separator();
  if (NULL != file_separator) {
    const size_t entry_len = strlen(entry);
    const size_t file_separator_length = strlen(file_separator);
    const size_t file_path_entry_length = _repository_len + file_separator_length + entry_len;
    file_path_entry = NEW_RESOURCE_ARRAY_RETURN_NULL(char, file_path_entry_length + 1);
    if (NULL == file_path_entry) {
      return NULL;
    }
    int position = 0;
    position += jio_snprintf(&file_path_entry[position], _repository_len + 1, "%s", _repo);
    position += jio_snprintf(&file_path_entry[position], file_separator_length + 1, "%s", os::file_separator());
    position += jio_snprintf(&file_path_entry[position], entry_len + 1, "%s", entry);
    file_path_entry[position] = '\0';
    assert((size_t)position == file_path_entry_length, "invariant");
    assert(strlen(file_path_entry) == (size_t)position, "invariant");
  }
  return file_path_entry;
}

const char* const RepositoryIterator::filter(const char* entry) const {
  if (entry == NULL) {
    return NULL;
  }
  const size_t entry_len = strlen(entry);
  if (entry_len <= 2) {
    // for "." and ".."
    return NULL;
  }
  char* entry_name = NEW_RESOURCE_ARRAY_RETURN_NULL(char, entry_len + 1);
  if (entry_name == NULL) {
    return NULL;
  }
  strncpy(entry_name, entry, entry_len + 1);
  const char* const fully_qualified_path_entry = fully_qualified(entry_name);
  if (NULL == fully_qualified_path_entry) {
    return NULL;
  }
  const fio_fd entry_fd = open_exclusivly(fully_qualified_path_entry);
  if (invalid_fd == entry_fd) {
    return NULL;
  }
  const int64_t entry_size = file_size(entry_fd);
  os::close(entry_fd);
  if (0 == entry_size) {
    return NULL;
  }
  return entry_name;
}

RepositoryIterator::RepositoryIterator(const char* repository, size_t repository_len) :
  _repo(repository),
  _repository_len(repository_len),
  _files(NULL),
  _iterator(0) {
  if (NULL != _repo) {
    assert(strlen(_repo) == _repository_len, "invariant");
    _files = new GrowableArray<const char*>(10);
    DIR* dirp = os::opendir(_repo);
    if (dirp == NULL) {
      log_error(jfr, system)("Unable to open repository %s", _repo);
      return;
    }
    struct dirent* dentry;
    while ((dentry = os::readdir(dirp)) != NULL) {
      const char* const entry_path = filter(dentry->d_name);
      if (NULL != entry_path) {
        _files->append(entry_path);
      }
    }
    os::closedir(dirp);
    if (_files->length() > 1) {
      _files->sort(file_sort);
    }
  }
}

bool RepositoryIterator::has_next() const {
  return (_files != NULL && _iterator < _files->length());
}

const char* const RepositoryIterator::next() const {
  return _iterator >= _files->length() ? NULL : fully_qualified(_files->at(_iterator++));
}

static void write_emergency_file(fio_fd emergency_fd, const RepositoryIterator& iterator) {
  assert(emergency_fd != invalid_fd, "invariant");
  const size_t size_of_file_copy_block = 1 * M; // 1 mb
  jbyte* const file_copy_block = NEW_RESOURCE_ARRAY_RETURN_NULL(jbyte, size_of_file_copy_block);
  if (file_copy_block == NULL) {
    return;
  }
  while (iterator.has_next()) {
    fio_fd current_fd = invalid_fd;
    const char* const fqn = iterator.next();
    if (fqn != NULL) {
      current_fd = open_exclusivly(fqn);
      if (current_fd != invalid_fd) {
        const int64_t current_filesize = file_size(current_fd);
        assert(current_filesize > 0, "invariant");
        int64_t bytes_read = 0;
        int64_t bytes_written = 0;
        while (bytes_read < current_filesize) {
          const ssize_t read_result = os::read_at(current_fd, file_copy_block, size_of_file_copy_block, bytes_read);
          if (-1 == read_result) {
            log_info(jfr)( // For user, should not be "jfr, system"
              "Unable to recover JFR data");
            break;
          }
          bytes_read += (int64_t)read_result;
          assert(bytes_read - bytes_written <= (int64_t)size_of_file_copy_block, "invariant");
          bytes_written += (int64_t)os::write(emergency_fd, file_copy_block, bytes_read - bytes_written);
          assert(bytes_read == bytes_written, "invariant");
        }
        os::close(current_fd);
      }
    }
  }
}

static const char* create_emergency_dump_path() {
  assert(JfrStream_lock->owned_by_self(), "invariant");
  char* buffer = NEW_RESOURCE_ARRAY_RETURN_NULL(char, JVM_MAXPATHLEN);
  if (NULL == buffer) {
    return NULL;
  }
  const char* const cwd = os::get_current_directory(buffer, JVM_MAXPATHLEN);
  if (NULL == cwd) {
    return NULL;
  }
  size_t pos = strlen(cwd);
  const int fsep_len = jio_snprintf(&buffer[pos], JVM_MAXPATHLEN - pos, "%s", os::file_separator());
  const char* filename_fmt = NULL;
  // fetch specific error cause
  switch (JfrJavaSupport::cause()) {
    case JfrJavaSupport::OUT_OF_MEMORY:
      filename_fmt = vm_oom_filename_fmt;
      break;
    case JfrJavaSupport::STACK_OVERFLOW:
      filename_fmt = vm_soe_filename_fmt;
      break;
    default:
      filename_fmt = vm_error_filename_fmt;
  }
  char* emergency_dump_path = NULL;
  pos += fsep_len;
  if (Arguments::copy_expand_pid(filename_fmt, strlen(filename_fmt), &buffer[pos], JVM_MAXPATHLEN - pos)) {
    const size_t emergency_filename_length = strlen(buffer);
    emergency_dump_path = NEW_RESOURCE_ARRAY_RETURN_NULL(char, emergency_filename_length + 1);
    if (NULL == emergency_dump_path) {
      return NULL;
    }
    strncpy(emergency_dump_path, buffer, emergency_filename_length + 1);
  }
  if (emergency_dump_path != NULL) {
    log_info(jfr)( // For user, should not be "jfr, system"
      "Attempting to recover JFR data, emergency jfr file: %s", emergency_dump_path);
  }
  return emergency_dump_path;
}

// Caller needs ResourceMark
static const char* create_emergency_chunk_path(const char* repository_path) {
  assert(repository_path != NULL, "invariant");
  assert(JfrStream_lock->owned_by_self(), "invariant");
  const size_t repository_path_len = strlen(repository_path);
  // date time
  char date_time_buffer[32] = { 0 };
  date_time(date_time_buffer, sizeof(date_time_buffer));
  size_t date_time_len = strlen(date_time_buffer);
  size_t chunkname_max_len = repository_path_len          // repository_base_path
                             + 1                          // "/"
                             + date_time_len              // date_time
                             + strlen(chunk_file_jfr_ext) // .jfr
                             + 1;
  char* chunk_path = NEW_RESOURCE_ARRAY_RETURN_NULL(char, chunkname_max_len);
  if (chunk_path == NULL) {
    return NULL;
  }
  // append the individual substrings
  jio_snprintf(chunk_path, chunkname_max_len, "%s%s%s%s", repository_path_len, os::file_separator(), date_time_buffer, chunk_file_jfr_ext);
  return chunk_path;
}

static fio_fd emergency_dump_file_descriptor() {
  assert(JfrStream_lock->owned_by_self(), "invariant");
  ResourceMark rm;
  const char* const emergency_dump_path = create_emergency_dump_path();
  return emergency_dump_path != NULL ? open_exclusivly(emergency_dump_path) : invalid_fd;
}

const char* JfrEmergencyDump::build_dump_path(const char* repository_path) {
  return repository_path == NULL ? create_emergency_dump_path() : create_emergency_chunk_path(repository_path);
}

void JfrEmergencyDump::on_vm_error(const char* repository_path) {
  assert(repository_path != NULL, "invariant");
  ResourceMark rm;
  MutexLockerEx stream_lock(JfrStream_lock, Mutex::_no_safepoint_check_flag);
  const fio_fd emergency_fd = emergency_dump_file_descriptor();
  if (emergency_fd != invalid_fd) {
    RepositoryIterator iterator(repository_path, strlen(repository_path));
    write_emergency_file(emergency_fd, iterator);
    os::close(emergency_fd);
  }
}

/*
* We are just about to exit the VM, so we will be very aggressive
* at this point in order to increase overall success of dumping jfr data:
*
* 1. if the thread state is not "_thread_in_vm", we will quick transition
*    it to "_thread_in_vm".
* 2. the nesting state for both resource and handle areas are unknown,
*    so we allocate new fresh arenas, discarding the old ones.
* 3. if the thread is the owner of some critical lock(s), unlock them.
*
* If we end up deadlocking in the attempt of dumping out jfr data,
* we rely on the WatcherThread task "is_error_reported()",
* to exit the VM after a hard-coded timeout.
* This "safety net" somewhat explains the aggressiveness in this attempt.
*
*/
static void prepare_for_emergency_dump(Thread* thread) {
  if (thread->is_Java_thread()) {
    ((JavaThread*)thread)->set_thread_state(_thread_in_vm);
  }

#ifdef ASSERT
  Monitor* owned_lock = thread->owned_locks();
  while (owned_lock != NULL) {
    Monitor* next = owned_lock->next();
    owned_lock->unlock();
    owned_lock = next;
  }
#endif // ASSERT

  if (Threads_lock->owned_by_self()) {
    Threads_lock->unlock();
  }

  if (Module_lock->owned_by_self()) {
    Module_lock->unlock();
  }

  if (ClassLoaderDataGraph_lock->owned_by_self()) {
    ClassLoaderDataGraph_lock->unlock();
  }

  if (Heap_lock->owned_by_self()) {
    Heap_lock->unlock();
  }

  if (VMOperationQueue_lock->owned_by_self()) {
    VMOperationQueue_lock->unlock();
  }

  if (VMOperationRequest_lock->owned_by_self()) {
    VMOperationRequest_lock->unlock();
  }


  if (Service_lock->owned_by_self()) {
    Service_lock->unlock();
  }

  if (CodeCache_lock->owned_by_self()) {
    CodeCache_lock->unlock();
  }

  if (PeriodicTask_lock->owned_by_self()) {
    PeriodicTask_lock->unlock();
  }

  if (JfrMsg_lock->owned_by_self()) {
    JfrMsg_lock->unlock();
  }

  if (JfrBuffer_lock->owned_by_self()) {
    JfrBuffer_lock->unlock();
  }

  if (JfrStream_lock->owned_by_self()) {
    JfrStream_lock->unlock();
  }

  if (JfrStacktrace_lock->owned_by_self()) {
    JfrStacktrace_lock->unlock();
  }
}

static volatile int jfr_shutdown_lock = 0;

static bool guard_reentrancy() {
  return Atomic::cmpxchg(1, &jfr_shutdown_lock, 0) == 0;
}

void JfrEmergencyDump::on_vm_shutdown(bool exception_handler) {
  if (!guard_reentrancy()) {
    return;
  }
  // function made non-reentrant
  Thread* thread = Thread::current();
  if (exception_handler) {
    // we are crashing
    if (thread->is_Watcher_thread()) {
      // The Watcher thread runs the periodic thread sampling task.
      // If it has crashed, it is likely that another thread is
      // left in a suspended state. This would mean the system
      // will not be able to ever move to a safepoint. We try
      // to avoid issuing safepoint operations when attempting
      // an emergency dump, but a safepoint might be already pending.
      return;
    }
    prepare_for_emergency_dump(thread);
  }
  EventDumpReason event;
  if (event.should_commit()) {
    event.set_reason(exception_handler ? "Crash" : "Out of Memory");
    event.set_recordingId(-1);
    event.commit();
  }
  if (!exception_handler) {
    // OOM
    LeakProfiler::emit_events(max_jlong, false);
  }
  const int messages = MSGBIT(MSG_VM_ERROR);
  ResourceMark rm(thread);
  HandleMark hm(thread);
  JfrRecorderService service;
  service.rotate(messages);
}
