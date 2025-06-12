/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/stacktrace/jfrStackFrame.hpp"
#include "jfr/support/jfrMethodLookup.hpp"
#include "oops/method.inline.hpp"

JfrStackFrame::JfrStackFrame() : _klass(nullptr), _methodid(0), _line(0), _bci(0), _type(0) {}

JfrStackFrame::JfrStackFrame(const traceid& id, int bci, u1 type, const InstanceKlass* ik) :
  _klass(ik), _methodid(id), _line(0), _bci(bci), _type(type) {}

JfrStackFrame::JfrStackFrame(const traceid& id, int bci, u1 type, int lineno, const InstanceKlass* ik) :
  _klass(ik), _methodid(id), _line(lineno), _bci(bci), _type(type) {}

template <typename Writer>
static void write_frame(Writer& w, traceid methodid, int line, int bci, u1 type) {
  w.write(methodid);
  w.write(static_cast<u4>(line));
  w.write(static_cast<u4>(bci));
  w.write(static_cast<u8>(type));
}

void JfrStackFrame::write(JfrChunkWriter& cw) const {
  write_frame(cw, _methodid, _line, _bci, _type);
}

void JfrStackFrame::write(JfrCheckpointWriter& cpw) const {
  write_frame(cpw, _methodid, _line, _bci, _type);
}

bool JfrStackFrame::equals(const JfrStackFrame& rhs) const {
  return _methodid == rhs._methodid && _bci == rhs._bci && _type == rhs._type;
}

void JfrStackFrame::resolve_lineno() const {
  assert(_klass, "no klass pointer");
  assert(_line == 0, "already have linenumber");
  const Method* const method = JfrMethodLookup::lookup(_klass, _methodid);
  assert(method != nullptr, "invariant");
  assert(method->method_holder() == _klass, "invariant");
  _line = method->line_number_from_bci(_bci);
}
