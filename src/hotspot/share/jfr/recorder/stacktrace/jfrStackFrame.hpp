/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_STACKTRACE_JFRSTACKFRAME_HPP
#define SHARE_JFR_RECORDER_STACKTRACE_JFRSTACKFRAME_HPP

#include "jfr/utilities/jfrTypes.hpp"

class JfrCheckpointWriter;
class JfrChunkWriter;
class InstanceKlass;

class JfrStackFrame {
  friend class ObjectSampleCheckpoint;
 private:
  const InstanceKlass* _klass;
  traceid _methodid;
  mutable int _line;
  int _bci;
  u1 _type;

 public:
  JfrStackFrame();
  JfrStackFrame(const traceid& id, int bci, u1 type, const InstanceKlass* klass);
  JfrStackFrame(const traceid& id, int bci, u1 type, int lineno, const InstanceKlass* klass);

  bool equals(const JfrStackFrame& rhs) const;
  void write(JfrChunkWriter& cw) const;
  void write(JfrCheckpointWriter& cpw) const;
  void resolve_lineno() const;

  enum : u1 {
    FRAME_INTERPRETER = 0,
    FRAME_JIT,
    FRAME_INLINE,
    FRAME_NATIVE,
    NUM_FRAME_TYPES
  };
};

template <typename>
class GrowableArray;

typedef GrowableArray<JfrStackFrame> JfrStackFrames;

#endif // SHARE_JFR_RECORDER_STACKTRACE_JFRSTACKFRAME_HPP
