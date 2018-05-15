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

#ifndef SHARE_VM_JFR_RECORDER_REPOSITORY_JFRRCHUNKSTATE_HPP
#define SHARE_VM_JFR_RECORDER_REPOSITORY_JFRRCHUNKSTATE_HPP

#include "jni.h"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrTypes.hpp"

class JfrChunkState : public JfrCHeapObj {
  friend class JfrChunkWriter;
 private:
  char* _path;
  jlong _start_ticks;
  jlong _start_nanos;
  jlong _previous_start_ticks;
  jlong _previous_start_nanos;
  jlong _previous_checkpoint_offset;

  void update_start_ticks();
  void update_start_nanos();
  void save_current_and_update_start_ticks();
  void save_current_and_update_start_nanos();

  JfrChunkState();
  ~JfrChunkState();
  void reset();
  jlong previous_checkpoint_offset() const;
  void set_previous_checkpoint_offset(jlong offset);
  jlong previous_start_ticks() const;
  jlong previous_start_nanos() const;
  jlong last_chunk_duration() const;
  void update_time_to_now();
  void set_path(const char* path);
  const char* path() const;
};

#endif // SHARE_VM_JFR_RECORDER_REPOSITORY_JFRRCHUNKSTATE_HPP
