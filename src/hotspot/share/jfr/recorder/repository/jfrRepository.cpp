/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/repository/jfrEmergencyDump.hpp"
#include "jfr/recorder/repository/jfrRepository.hpp"
#include "jfr/recorder/service/jfrPostBox.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/mutex.hpp"
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

void JfrRepository::on_vm_error() {
  assert(!JfrStream_lock->owned_by_self(), "invariant");
  if (_path == NULL) {
    // completed already
    return;
  }
  JfrEmergencyDump::on_vm_error(_path);
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
  strncpy(_path, path, path_len + 1);
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
    _chunkwriter->set_chunk_path(JfrEmergencyDump::build_dump_path(_path));
  }
  return _chunkwriter->open();
}

size_t JfrRepository::close_chunk(int64_t metadata_offset) {
  return _chunkwriter->close(metadata_offset);
}
