/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/repository/jfrChunkState.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/repository/jfrRepository.hpp"
#include "jfr/recorder/service/jfrPostBox.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/mutex.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.inline.hpp"

static JfrRepository* _instance = NULL;

JfrRepository& JfrRepository::instance() {
  return *_instance;
}

static JfrChunkWriter* _chunkwriter = NULL;

static bool initialize_chunkwriter() {
  assert(_chunkwriter == NULL, "invariant");
  _chunkwriter = new JfrChunkWriter();
  return _chunkwriter != NULL && _chunkwriter->initialize();
}

JfrChunkWriter& JfrRepository::chunkwriter() {
  return *_chunkwriter;
}

JfrRepository::JfrRepository(JfrPostBox& post_box) : _path(NULL), _post_box(post_box) {}

bool JfrRepository::initialize() {
  return initialize_chunkwriter();
}

JfrRepository::~JfrRepository() {
  if (_path != NULL) {
    JfrCHeapObj::free(_path, strlen(_path) + 1);
    _path = NULL;
  }

  if (_chunkwriter != NULL) {
    delete _chunkwriter;
    _chunkwriter = NULL;
  }
}

JfrRepository* JfrRepository::create(JfrPostBox& post_box) {
  assert(_instance == NULL, "invariant");
  _instance = new JfrRepository(post_box);
  return _instance;
}

void JfrRepository::destroy() {
  assert(_instance != NULL, "invariant");
  delete _instance;
  _instance = NULL;
}

static const char vm_error_filename_fmt[] = "hs_err_pid%p.jfr";
static const char vm_oom_filename_fmt[] = "hs_oom_pid%p.jfr";
static const char vm_soe_filename_fmt[] = "hs_soe_pid%p.jfr";
static const char chunk_file_jfr_ext[] = ".jfr";
static const size_t iso8601_len = 19; // "YYYY-MM-DDTHH:MM:SS"

static fio_fd open_exclusivly(const char* path) {
  return os::open(path, O_CREAT | O_WRONLY, S_IREAD | S_IWRITE);
}

static fio_fd open_existing(const char* path) {
  return os::open(path, O_RDWR, S_IREAD | S_IWRITE);
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
    switch(iso8601_str[i]) {
      case 'T' :
      case '-' :
      case ':' :
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

static jlong file_size(fio_fd fd) {
  assert(fd != invalid_fd, "invariant");
  const jlong current_offset = os::current_file_offset(fd);
  const jlong size = os::lseek(fd, 0, SEEK_END);
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
  debug_only(void print_repository_files() const;)
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
  strncpy(entry_name, entry, entry_len);
  entry_name[entry_len] = '\0';
  const char* const fully_qualified_path_entry = fully_qualified(entry_name);
  if (NULL == fully_qualified_path_entry) {
    return NULL;
  }
  const fio_fd entry_fd = open_existing(fully_qualified_path_entry);
  if (invalid_fd == entry_fd) {
    return NULL;
  }
  const jlong entry_size = file_size(entry_fd);
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

#ifdef ASSERT
void RepositoryIterator::print_repository_files() const {
  while (has_next()) {
    log_error(jfr, system)( "%s", next());
  }
}
#endif
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
 jlong bytes_written_total = 0;
  while (iterator.has_next()) {
    fio_fd current_fd = invalid_fd;
    const char* const fqn = iterator.next();
    if (fqn != NULL) {
      current_fd = open_existing(fqn);
      if (current_fd != invalid_fd) {
        const jlong current_filesize = file_size(current_fd);
        assert(current_filesize > 0, "invariant");
        jlong bytes_read = 0;
        jlong bytes_written = 0;
        while (bytes_read < current_filesize) {
          bytes_read += (jlong)os::read_at(current_fd, file_copy_block, size_of_file_copy_block, bytes_read);
          assert(bytes_read - bytes_written <= (jlong)size_of_file_copy_block, "invariant");
          bytes_written += (jlong)os::write(emergency_fd, file_copy_block, bytes_read - bytes_written);
          assert(bytes_read == bytes_written, "invariant");
        }
        os::close(current_fd);
        bytes_written_total += bytes_written;
      }
    }
  }
}

static const char* create_emergency_dump_path() {
  assert(JfrStream_lock->owned_by_self(), "invariant");
  char* buffer = NEW_RESOURCE_ARRAY_RETURN_NULL(char, O_BUFLEN);
  if (NULL == buffer) {
    return NULL;
  }
  const char* const cwd = os::get_current_directory(buffer, O_BUFLEN);
  if (NULL == cwd) {
    return NULL;
  }
  size_t pos = strlen(cwd);
  const int fsep_len = jio_snprintf(&buffer[pos], O_BUFLEN - pos, "%s", os::file_separator());
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
  if (Arguments::copy_expand_pid(filename_fmt, strlen(filename_fmt), &buffer[pos], O_BUFLEN - pos)) {
    const size_t emergency_filename_length = strlen(buffer);
    emergency_dump_path = NEW_RESOURCE_ARRAY_RETURN_NULL(char, emergency_filename_length + 1);
    if (NULL == emergency_dump_path) {
      return NULL;
    }
    strncpy(emergency_dump_path, buffer, emergency_filename_length);
    emergency_dump_path[emergency_filename_length] = '\0';
  }
  return emergency_dump_path;
}

// Caller needs ResourceMark
static const char* create_emergency_chunk_path(const char* repository_base, size_t repository_len) {
  assert(repository_base != NULL, "invariant");
  assert(JfrStream_lock->owned_by_self(), "invariant");
  // date time
  char date_time_buffer[32] = {0};
  date_time(date_time_buffer, sizeof(date_time_buffer));
  size_t date_time_len = strlen(date_time_buffer);
  size_t chunkname_max_len = repository_len               // repository_base
                             + 1                          // "/"
                             + date_time_len              // date_time
                             + strlen(chunk_file_jfr_ext) // .jfr
                             + 1;
  char* chunk_path = NEW_RESOURCE_ARRAY_RETURN_NULL(char, chunkname_max_len);
  if (chunk_path == NULL) {
    return NULL;
  }
  // append the individual substrings
  jio_snprintf(chunk_path, chunkname_max_len, "%s%s%s%s", repository_base, os::file_separator(), date_time_buffer, chunk_file_jfr_ext);
  return chunk_path;
}

static fio_fd emergency_dump_file() {
  assert(JfrStream_lock->owned_by_self(), "invariant");
  ResourceMark rm;
  const char* const emergency_dump_path = create_emergency_dump_path();
  if (emergency_dump_path == NULL) {
    return invalid_fd;
  }
  const fio_fd fd = open_exclusivly(emergency_dump_path);
  if (fd != invalid_fd) {
    log_info(jfr)( // For user, should not be "jfr, system"
      "Attempting to recover JFR data, emergency jfr file: %s", emergency_dump_path);
  }
  return fd;
}

static const char* emergency_path(const char* repository, size_t repository_len) {
  return repository == NULL ? create_emergency_dump_path() : create_emergency_chunk_path(repository, repository_len);
}

void JfrRepository::on_vm_error() {
  assert(!JfrStream_lock->owned_by_self(), "invariant");
  const char* path = _path;
  if (path == NULL) {
    // completed already
    return;
  }
  ResourceMark rm;
  MutexLockerEx stream_lock(JfrStream_lock, Mutex::_no_safepoint_check_flag);
  const fio_fd emergency_fd = emergency_dump_file();
  if (emergency_fd != invalid_fd) {
    RepositoryIterator iterator(path, strlen(path));
    write_emergency_file(emergency_fd, iterator);
    os::close(emergency_fd);
  }
}

bool JfrRepository::set_path(const char* path) {
  assert(path != NULL, "trying to set the repository path with a NULL string!");
  if (_path != NULL) {
    // delete existing
    JfrCHeapObj::free(_path, strlen(_path) + 1);
  }
  const size_t path_len = strlen(path);
  _path = JfrCHeapObj::new_array<char>(path_len + 1);
  if (_path == NULL) {
    return false;
  }
  strncpy(_path, path, path_len);
  _path[path_len] = '\0';
  return true;
}

void JfrRepository::set_chunk_path(const char* path) {
  assert(JfrStream_lock->owned_by_self(), "invariant");
  chunkwriter().set_chunk_path(path);
}

void JfrRepository::notify_on_new_chunk_path() {
  if (Jfr::is_recording()) {
    instance()._post_box.post(MSG_ROTATE);
  }
}

/**
* Sets the file where data should be written.
*
* Recording  Previous  Current  Action
* ==============================================
*   true     null      null     Ignore, keep recording in-memory
*   true     null      file1    Start disk recording
*   true     file      null     Copy out metadata to disk and continue in-memory recording
*   true     file1     file2    Copy out metadata and start with new File (file2)
*   false     *        null     Ignore, but start recording to memory
*   false     *        file     Ignore, but start recording to disk
*/
void JfrRepository::set_chunk_path(jstring path, JavaThread* jt) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(jt));
  ResourceMark rm(jt);
  const char* const canonical_chunk_path = JfrJavaSupport::c_str(path, jt);
  {
    MutexLockerEx stream_lock(JfrStream_lock, Mutex::_no_safepoint_check_flag);
    if (NULL == canonical_chunk_path && !_chunkwriter->is_valid()) {
      // new output is NULL and current output is NULL
      return;
    }
    instance().set_chunk_path(canonical_chunk_path);
  }
  notify_on_new_chunk_path();
}

void JfrRepository::set_path(jstring location, JavaThread* jt) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(jt));
  ResourceMark rm(jt);
  const char* const path = JfrJavaSupport::c_str(location, jt);
  if (path != NULL) {
    instance().set_path(path);
  }
}

bool JfrRepository::open_chunk(bool vm_error /* false */) {
  assert(JfrStream_lock->owned_by_self(), "invariant");
  if (vm_error) {
    ResourceMark rm;
    const char* repository_path = _path;
    const size_t repository_path_len = repository_path != NULL ? strlen(repository_path) : 0;
    const char* const path = emergency_path(repository_path, repository_path_len);
    _chunkwriter->set_chunk_path(path);
  }
  return _chunkwriter->open();
}

size_t JfrRepository::close_chunk(jlong metadata_offset) {
  return _chunkwriter->close(metadata_offset);
}
