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

#ifndef SHARE_JFR_RECORDER_STACKTRACE_JFRSTACKTRACEREPOSITORY_HPP
#define SHARE_JFR_RECORDER_STACKTRACE_JFRSTACKTRACEREPOSITORY_HPP

#include "jfr/recorder/stacktrace/jfrStackTrace.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrTypes.hpp"

class JavaThread;
class JfrCheckpointWriter;
class JfrChunkWriter;

class JfrStackTraceRepository : public JfrCHeapObj {
  friend class JfrRecorder;
  friend class JfrRecorderService;
  friend class JfrThreadSampleClosure;
  friend class ObjectSampleCheckpoint;
  friend class ObjectSampler;
  friend class StackTraceBlobInstaller;
  friend class WriteStackTraceRepository;

 private:
  static const u4 TABLE_SIZE = 2053;
  JfrStackTrace* _table[TABLE_SIZE];
  traceid _next_id;
  u4 _entries;

  JfrStackTraceRepository();
  static JfrStackTraceRepository& instance();
  static JfrStackTraceRepository* create();
  bool initialize();
  static void destroy();

  size_t write_impl(JfrChunkWriter& cw, bool clear);
  static void write_metadata(JfrCheckpointWriter& cpw);
  traceid write(JfrCheckpointWriter& cpw, traceid id, unsigned int hash);
  size_t write(JfrChunkWriter& cw, bool clear);
  size_t clear();

  traceid add_trace(const JfrStackTrace& stacktrace);
  static traceid add(const JfrStackTrace& stacktrace);
  traceid record_for(JavaThread* thread, int skip, JfrStackFrame* frames, u4 max_frames);
  const JfrStackTrace* lookup(unsigned int hash, traceid id) const;

 public:
  static traceid record(Thread* thread, int skip = 0);
  static void record_and_cache(JavaThread* thread, int skip = 0);
};

#endif // SHARE_JFR_RECORDER_STACKTRACE_JFRSTACKTRACEREPOSITORY_HPP
