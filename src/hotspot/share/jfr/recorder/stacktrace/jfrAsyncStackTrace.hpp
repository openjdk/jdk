/*
 * Copyright (c) 2011, 2024, SAP SE, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_STACKTRACE_JFRASYNCSTACKTRACE_HPP
#define SHARE_JFR_RECORDER_STACKTRACE_JFRASYNCSTACKTRACE_HPP

#include "jfr/utilities/jfrTypes.hpp"
#include "oops/method.hpp"

// This is based on JfrStackTrace, with the major difference that methods
// are not resolved

class frame;
class InstanceKlass;
class JavaThread;
class JfrStackTrace;

class JfrAsyncStackFrame {
  friend class JfrAsyncStackTrace;
 private:
  const InstanceKlass* _klass;
  const Method* _method;
  int _line;
  int _bci;
  u1 _type;

 public:
  JfrAsyncStackFrame(const Method* _method, int bci, u1 type, int lineno, const InstanceKlass* klass);

  enum : u1 {
    FRAME_INTERPRETER = 0,
    FRAME_JIT,
    FRAME_INLINE,
    FRAME_NATIVE,
    NUM_FRAME_TYPES
  };
};

class JfrAsyncStackTraceStoreCallback;

// A trace without methods resolved to ids
class JfrAsyncStackTrace {
  friend class JfrCPUTimeTrace;
  friend class JfrAsyncStackTraceStoreCallback;
 private:
  JfrAsyncStackFrame* _frames;
  u4 _nr_of_frames;
  u4 _max_frames;
  bool _reached_root;

  void set_nr_of_frames(u4 nr_of_frames) { _nr_of_frames = nr_of_frames; }
  void set_reached_root(bool reached_root) { _reached_root = reached_root; }
  void resolve_linenos() const;

  bool record_async(JavaThread* other_thread, const frame& frame);

  bool full_stacktrace() const { return _reached_root; }

  JfrAsyncStackTrace(JfrAsyncStackFrame* frames, u4 max_frames);

  bool inner_store(JfrStackTrace* trace) const;

 public:

  // store the trace in a JfrStackTrace object, resolving methods and line numbers
  bool store(JfrStackTrace* trace) const;

  u4 nr_of_frames() const { return _nr_of_frames; }
};

#endif // SHARE_JFR_RECORDER_STACKTRACE_JFRASYNCSTACKTRACE_HPP
