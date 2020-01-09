/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_CHECKPOINT_JFRCHECKPOINTMANAGER_HPP
#define SHARE_JFR_RECORDER_CHECKPOINT_JFRCHECKPOINTMANAGER_HPP

#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "jfr/recorder/storage/jfrMemorySpace.hpp"
#include "jfr/recorder/storage/jfrMemorySpaceRetrieval.hpp"

class JfrCheckpointManager;
class JfrChunkWriter;
class JfrSerializer;
class JfrTypeManager;
class Mutex;
class Thread;

struct JfrCheckpointEntry {
  jlong size;
  jlong start_time;
  jlong duration;
  juint flushpoint;
  juint nof_segments;
};

typedef JfrMemorySpace<JfrBuffer, JfrMspaceSequentialRetrieval, JfrCheckpointManager> JfrCheckpointMspace;

//
// Responsible for maintaining checkpoints and by implication types.
// A checkpoint is an event that has a payload consisting of constant types.
// A constant type is a binary relation, a set of key-value pairs.
//
class JfrCheckpointManager : public JfrCHeapObj {
 public:
  typedef JfrCheckpointMspace::Type Buffer;
 private:
  JfrCheckpointMspace* _free_list_mspace;
  JfrCheckpointMspace* _epoch_transition_mspace;
  Mutex* _lock;
  const Thread* _service_thread;
  JfrChunkWriter& _chunkwriter;
  bool _checkpoint_epoch_state;

  // mspace callback
  void register_full(Buffer* t, Thread* thread);
  void lock();
  void unlock();
  DEBUG_ONLY(bool is_locked() const;)

  JfrCheckpointMspace* lookup(Buffer* old) const;
  bool use_epoch_transition_mspace(const Thread* t) const;
  size_t write_epoch_transition_mspace();

  static Buffer* lease_buffer(Thread* t, size_t size = 0);
  static Buffer* lease_buffer(Buffer* old, Thread* t, size_t size = 0);
  static Buffer* flush(Buffer* old, size_t used, size_t requested, Thread* t);

  size_t clear();
  size_t write();
  size_t flush();

  bool is_static_type_set_required();
  size_t write_static_type_set();
  size_t write_threads();
  size_t write_static_type_set_and_threads();
  bool is_type_set_required();
  void clear_type_set();
  void write_type_set();
  static void write_type_set_for_unloaded_classes();

  void begin_epoch_shift();
  void end_epoch_shift();
  void synchronize_checkpoint_manager_with_current_epoch();

  void notify_threads();

  JfrCheckpointManager(JfrChunkWriter& cw);
  ~JfrCheckpointManager();

  static JfrCheckpointManager& instance();
  static JfrCheckpointManager* create(JfrChunkWriter& cw);
  bool initialize();
  void on_rotation();
  static void destroy();

 public:
  size_t flush_type_set();
  void flush_static_type_set();
  static void create_thread_blob(Thread* t);
  static void write_thread_checkpoint(Thread* t);
  void register_service_thread(const Thread* t);

  friend class Jfr;
  friend class JfrRecorder;
  friend class JfrRecorderService;
  friend class JfrCheckpointFlush;
  friend class JfrCheckpointWriter;
  friend class JfrSerializer;
  friend class JfrStackTraceRepository;
  template <typename, template <typename> class, typename>
  friend class JfrMemorySpace;
};

#endif // SHARE_JFR_RECORDER_CHECKPOINT_JFRCHECKPOINTMANAGER_HPP
