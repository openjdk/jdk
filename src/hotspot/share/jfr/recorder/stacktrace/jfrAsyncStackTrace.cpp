/*
 * Copyright (c) 2024, SAP SE. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "jfr/recorder/stacktrace/jfrAsyncStackTrace.hpp"
#include "jfr/recorder/stacktrace/jfrStackTrace.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "runtime/threadCrashProtection.hpp"
#include "runtime/vframe.inline.hpp"

JfrAsyncStackFrame::JfrAsyncStackFrame(const Method* method, int bci, u1 type, int lineno, const InstanceKlass* ik) :
  _klass(ik), _method(method), _line(lineno), _bci(bci), _type(type) {}


JfrAsyncStackTrace::JfrAsyncStackTrace(JfrAsyncStackFrame* frames, u4 max_frames) :
  _frames(frames),
  _nr_of_frames(0),
  _max_frames(max_frames),
  _reached_root(false)
  {}


static const size_t min_valid_free_size_bytes = 16;

bool JfrAsyncStackTrace::record_async(JavaThread* jt, const frame& frame) {
  NoHandleMark nhm;

  assert(jt != nullptr, "invariant");
  Thread* current_thread = Thread::current_or_null_safe();
  if (current_thread == nullptr) {
    return false;
  }
  assert(current_thread->in_asgct(), "invariant");
  assert(jt != current_thread || current_thread->in_asgct(), "invariant");

  u4 count = 0;
  _reached_root = true;

  JfrVframeStream vfs(jt, frame, false, true);

  while (!vfs.at_end()) {
    if (count >= _max_frames) {
      _reached_root = false;
      break;
    }
    const Method* method = vfs.method();
    if (method == nullptr || !Method::is_valid_method(method)) {
      // we throw away everything we've gathered in this sample since
      // none of it is safe
      return false;
    }
    u1 type = vfs.is_interpreted_frame() ? JfrStackFrame::FRAME_INTERPRETER : JfrStackFrame::FRAME_JIT;
    int bci = 0;
    if (method->is_native()) {
      type = JfrStackFrame::FRAME_NATIVE;
    } else {
      bci = vfs.bci();
    }

    intptr_t* frame_id = vfs.frame_id();
    vfs.next_vframe();
    if (type == JfrStackFrame::FRAME_JIT && !vfs.at_end() && frame_id == vfs.frame_id()) {
      // This frame and the caller frame are both the same physical
      // frame, so this frame is inlined into the caller.
      type = JfrStackFrame::FRAME_INLINE;
    }
    _frames[count] = JfrAsyncStackFrame(method, bci, type, method->line_number_from_bci(bci), method->method_holder());
    count++;
  }
  _nr_of_frames = count;
  return count > 0;
}

class JfrAsyncStackTraceStoreCallback : public CrashProtectionCallback {
 public:
  JfrAsyncStackTraceStoreCallback(const JfrAsyncStackTrace* asyncTrace, JfrStackTrace* trace) :
  _asyncTrace(asyncTrace), _trace(trace), _success(false) {}
  virtual void call() {
    _success = _asyncTrace->inner_store(_trace);
  }
  bool success() { return _success; }

 private:
  const JfrAsyncStackTrace* _asyncTrace;
  JfrStackTrace* _trace;
  bool _success;
};

bool JfrAsyncStackTrace::inner_store(JfrStackTrace* trace) const {
  traceid hash = 1;
  for (u4 i = 0; i < _nr_of_frames; i++) {
    const JfrAsyncStackFrame& frame = _frames[i];
    if (!Method::is_valid_method(frame._method)) {
      // we throw away everything we've gathered in this sample since
      // none of it is safe
      return false;
    }
    const traceid mid = JfrTraceId::load(frame._method);
    hash = (hash * 31) + mid;
    hash = (hash * 31) + frame._bci;
    hash = (hash * 31) + frame._type;
    trace->_frames[i] = JfrStackFrame(mid, frame._bci, frame._type, frame._line, frame._klass);
  }
  trace->set_hash(hash);
  return true;
}

bool JfrAsyncStackTrace::store(JfrStackTrace* trace) const {
  assert(trace != nullptr, "invariant");
  Thread* current_thread = Thread::current();
  assert(current_thread->is_JfrSampler_thread() || current_thread->in_asgct(), "invariant");
  trace->set_nr_of_frames(_nr_of_frames);
  trace->set_reached_root(_reached_root);

  JfrAsyncStackTraceStoreCallback cb(this, trace);
  ThreadCrashProtection crash_protection;
  if (!crash_protection.call(cb)) {
    log_warning(jfr)("JFR CPU time method resolver crashed");
  }
  if (!cb.success()) {
    return false;
  }

  trace->_lineno = true;
  return true;
}
