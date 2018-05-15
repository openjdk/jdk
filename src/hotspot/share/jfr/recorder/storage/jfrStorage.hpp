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
#ifndef SHARE_VM_JFR_RECORDER_STORAGE_JFRSTORAGE_HPP
#define SHARE_VM_JFR_RECORDER_STORAGE_JFRSTORAGE_HPP

#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "jfr/recorder/storage/jfrMemorySpace.hpp"
#include "jfr/recorder/storage/jfrMemorySpaceRetrieval.hpp"

class JfrChunkWriter;
class JfrPostBox;
class JfrStorage;
class JfrStorageControl;

typedef JfrMemorySpace<JfrBuffer, JfrMspaceAlternatingRetrieval, JfrStorage> JfrStorageMspace;
typedef JfrMemorySpace<JfrBuffer, JfrThreadLocalRetrieval, JfrStorage> JfrThreadLocalMspace;
typedef JfrMemorySpace<JfrAgeNode, JfrMspaceSequentialRetrieval, JfrStorage> JfrStorageAgeMspace;

//
// Responsible for providing backing storage for writing events.
//
class JfrStorage : public JfrCHeapObj {
 public:
  typedef JfrStorageMspace::Type Buffer;
 private:
  JfrStorageControl* _control;
  JfrStorageMspace* _global_mspace;
  JfrThreadLocalMspace* _thread_local_mspace;
  JfrStorageMspace* _transient_mspace;
  JfrStorageAgeMspace* _age_mspace;
  JfrChunkWriter& _chunkwriter;
  JfrPostBox& _post_box;

  // mspace callbacks
  void register_full(Buffer* t, Thread* thread);
  void lock();
  void unlock();
  DEBUG_ONLY(bool is_locked() const;)

  Buffer* acquire_large(size_t size, Thread* t);
  Buffer* acquire_transient(size_t size, Thread* thread);
  bool flush_regular_buffer(Buffer* const buffer, Thread* t);
  Buffer* flush_regular(Buffer* cur, const u1* cur_pos, size_t used, size_t req, bool native, Thread* t);
  Buffer* flush_large(Buffer* cur, const u1* cur_pos, size_t used, size_t req, bool native, Thread* t);
  Buffer* provision_large(Buffer* cur, const u1* cur_pos, size_t used, size_t req, bool native, Thread* t);
  void release(Buffer* buffer, Thread* t);

  size_t clear();
  size_t clear_full();
  size_t write();
  size_t write_full();
  size_t write_at_safepoint();
  size_t scavenge();

  JfrStorage(JfrChunkWriter& cw, JfrPostBox& post_box);
  ~JfrStorage();

  static JfrStorage& instance();
  static JfrStorage* create(JfrChunkWriter& chunkwriter, JfrPostBox& post_box);
  bool initialize();
  static void destroy();

 public:
  static Buffer* acquire_thread_local(Thread* t, size_t size = 0);
  static void release_thread_local(Buffer* buffer, Thread* t);
  void release_large(Buffer* const buffer, Thread* t);
  static Buffer* flush(Buffer* cur, size_t used, size_t req, bool native, Thread* t);
  void discard_oldest(Thread* t);
  static JfrStorageControl& control();

  friend class JfrRecorder;
  friend class JfrRecorderService;
  template <typename, template <typename> class, typename>
  friend class JfrMemorySpace;
};

#endif // SHARE_VM_JFR_RECORDER_STORAGE_JFRSTORAGE_HPP
