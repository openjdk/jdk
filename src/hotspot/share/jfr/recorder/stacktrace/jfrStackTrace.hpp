/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_STACKTRACE_JFRSTACKTRACE_HPP
#define SHARE_JFR_RECORDER_STACKTRACE_JFRSTACKTRACE_HPP

#include "jfr/recorder/stacktrace/jfrStackFrame.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrTypes.hpp"

class frame;
class InstanceKlass;
class JavaThread;
class JfrCheckpointWriter;
class JfrChunkWriter;
struct JfrSampleRequest;

class JfrStackTrace : public JfrCHeapObj {
  friend class JfrNativeSamplerCallback;
  friend class JfrStackTraceRepository;
  friend class LeakProfilerStackTraceWriter;
  friend class JfrThreadSampling;
  friend class ObjectSampleCheckpoint;
  friend class ObjectSampler;
  friend class StackTraceResolver;
 private:
  const JfrStackTrace* _next;
  JfrStackFrames* _frames;
  traceid _id;
  traceid _hash;
  u4 _count;
  u4 _max_frames;
  bool _frames_ownership;
  bool _reached_root;
  mutable bool _lineno;
  mutable bool _written;

  const JfrStackTrace* next() const { return _next; }

  void write(JfrChunkWriter& cw) const;
  void write(JfrCheckpointWriter& cpw) const;
  bool equals(const JfrStackTrace& rhs) const;

  void set_id(traceid id) { _id = id; }
  void set_hash(unsigned int hash) { _hash = hash; }
  void set_reached_root(bool reached_root) { _reached_root = reached_root; }
  void resolve_linenos() const;

  int number_of_frames() const;
  bool have_lineno() const { return _lineno; }
  bool full_stacktrace() const { return _reached_root; }
  bool record_inner(JavaThread* jt, const frame& frame, bool in_continuation, int skip, int64_t stack_filter_id = -1);
  bool record(JavaThread* jt, const frame& frame, bool in_continuation, int skip, int64_t stack_filter_id = -1);
  void record_interpreter_top_frame(const JfrSampleRequest& request);

  JfrStackTrace(traceid id, const JfrStackTrace& trace, const JfrStackTrace* next);

 public:
  // ResourceArea allocation, remember ResourceMark.
  JfrStackTrace();
  ~JfrStackTrace();

  traceid hash() const { return _hash; }
  traceid id() const { return _id; }

  bool record(JavaThread* current_thread, int skip, int64_t stack_filter_id);
  bool record(JavaThread* jt, const frame& frame, bool in_continuation, const JfrSampleRequest& request);
  bool should_write() const { return !_written; }
};

#endif // SHARE_JFR_RECORDER_STACKTRACE_JFRSTACKTRACE_HPP
