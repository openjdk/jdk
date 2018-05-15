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

#ifndef SHARE_VM_JFR_RECORDER_STACKTRACE_JFRSTACKTRACEREPOSITORY_HPP
#define SHARE_VM_JFR_RECORDER_STACKTRACE_JFRSTACKTRACEREPOSITORY_HPP

#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrTypes.hpp"

class frame;
class JavaThread;
class JfrCheckpointSystem;
class JfrCheckpointWriter;
class JfrChunkWriter;
class Method;

class JfrStackFrame {
 private:
  const Method* _method;
  traceid _methodid;
  int _line;
  int _bci;
  u1 _type;

 public:
  enum {
    FRAME_INTERPRETER = 0,
    FRAME_JIT,
    FRAME_INLINE,
    FRAME_NATIVE,
    NUM_FRAME_TYPES
  };

  JfrStackFrame(const traceid& id, int bci, int type, const Method* method) :
    _method(method), _methodid(id), _line(0), _bci(bci), _type(type) {}
  JfrStackFrame(const traceid& id, int bci, int type, int lineno) :
    _method(NULL), _methodid(id), _line(0), _bci(bci), _type(type) {}
  bool equals(const JfrStackFrame& rhs) const;
  void write(JfrChunkWriter& cw) const;
  void write(JfrCheckpointWriter& cpw) const;
  void resolve_lineno();
};

class JfrStackTrace : public StackObj {
  friend class JfrStackTraceRepository;
 private:
  JfrStackFrame* _frames;
  traceid _id;
  u4 _nr_of_frames;
  unsigned int _hash;
  const u4 _max_frames;
  bool _reached_root;
  bool _lineno;

 public:
  JfrStackTrace(JfrStackFrame* frames, u4 max_frames) : _frames(frames),
                                                        _id(0),
                                                        _nr_of_frames(0),
                                                        _hash(0),
                                                        _reached_root(false),
                                                        _max_frames(max_frames),
                                                        _lineno(false) {}
  bool record_thread(JavaThread& thread, frame& frame);
  bool record_safe(JavaThread* thread, int skip, bool leakp = false);
  void resolve_linenos();
  void set_nr_of_frames(u4 nr_of_frames) { _nr_of_frames = nr_of_frames; }
  void set_hash(unsigned int hash) { _hash = hash; }
  void set_frame(u4 frame_pos, JfrStackFrame& frame);
  void set_reached_root(bool reached_root) { _reached_root = reached_root; }
  bool full_stacktrace() const { return _reached_root; }
  bool have_lineno() const { return _lineno; }
};

class JfrStackTraceRepository : public JfrCHeapObj {
  friend class JfrRecorder;
  friend class JfrRecorderService;
  friend class ObjectSampler;
  friend class WriteObjectSampleStacktrace;

  class StackTrace : public JfrCHeapObj {
    friend class JfrStackTrace;
    friend class JfrStackTraceRepository;
   private:
    StackTrace* _next;
    JfrStackFrame* _frames;
    const traceid _id;
    u4 _nr_of_frames;
    unsigned int _hash;
    bool _reached_root;
    mutable bool _written;

    unsigned int hash() const { return _hash; }
    bool should_write() const { return !_written; }

   public:
    StackTrace(traceid id, const JfrStackTrace& trace, StackTrace* next);
    ~StackTrace();
    traceid id() const { return _id; }
    StackTrace* next() const { return _next; }
    void write(JfrChunkWriter& cw) const;
    void write(JfrCheckpointWriter& cpw) const;
    bool equals(const JfrStackTrace& rhs) const;
  };

 private:
  static const u4 TABLE_SIZE = 2053;
  StackTrace* _table[TABLE_SIZE];
  traceid _next_id;
  u4 _entries;

  size_t write_impl(JfrChunkWriter& cw, bool clear);
  traceid record_for(JavaThread* thread, int skip, JfrStackFrame* frames, u4 max_frames);
  traceid record_for(JavaThread* thread, int skip, JfrStackFrame* frames, u4 max_frames, unsigned int* hash);
  traceid add_trace(const JfrStackTrace& stacktrace);
  const StackTrace* resolve_entry(unsigned int hash, traceid id) const;

  static void write_metadata(JfrCheckpointWriter& cpw);

  JfrStackTraceRepository();
  static JfrStackTraceRepository& instance();
 public:
  static JfrStackTraceRepository* create();
  bool initialize();
  static void destroy();
  static traceid add(const JfrStackTrace& stacktrace);
  static traceid record(Thread* thread, int skip = 0);
  static traceid record(Thread* thread, int skip, unsigned int* hash);
  traceid write(JfrCheckpointWriter& cpw, traceid id, unsigned int hash);
  size_t write(JfrChunkWriter& cw, bool clear);
  size_t clear();
};

#endif // SHARE_VM_JFR_RECORDER_STACKTRACE_JFRSTACKTRACEREPOSITORY_HPP
