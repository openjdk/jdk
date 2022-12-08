
/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_STACKTRACE_STACKWALKER_HPP
#define SHARE_JFR_RECORDER_STACKTRACE_STACKWALKER_HPP

#include "runtime/frame.hpp"
#include "runtime/registerMap.hpp"
#include "runtime/thread.hpp"
#include "runtime/vframe.inline.hpp"

// a helper stream
class compiledFrameStream : public vframeStreamCommon {
  bool cf_next_into_inlined;
  bool _invalid;
 public:
  compiledFrameStream(): vframeStreamCommon(RegisterMap(NULL,
    RegisterMap::UpdateMap::skip,
    RegisterMap::ProcessFrames::skip, RegisterMap::WalkContinuation::skip)),
    cf_next_into_inlined(false), _invalid(true) {};
  // constructor that starts with sender of frame fr (top_frame)
  compiledFrameStream(JavaThread *jt, frame fr, bool stop_at_java_call_stub);
  void cf_next();
  bool cf_next_did_go_into_inlined() const { return cf_next_into_inlined; }
  bool inlined() const { return _sender_decode_offset != 0; }
  bool invalid() const { return _invalid; }
};

// errors, subset of forte errors
enum StackWalkerError {
  STACKWALKER_NO_JAVA_FRAME        =  0,  // too many c frames to skip and no java frame found
  STACKWALKER_INDECIPHERABLE_FRAME = -1,
  STACKWALKER_GC_ACTIVE            = -2,
  STACKWALKER_NOT_WALKABLE         = -6
};

enum StackWalkerReturn {
  STACKWALKER_END = 1,
  STACKWALKER_INTERPRETED_FRAME = 2,
  STACKWALKER_COMPILED_FRAME = 3,
  STACKWALKER_NATIVE_FRAME = 4,
  STACKWALKER_C_FRAME = 5, // might be runtime, stub or real C frame
  STACKWALKER_START = 6
};

// walk the stack of a thread from any given frame
// includes all c frames and lot's of checks
// borrowed from forte.hpp
class StackWalker {

  // Java thread to walk
  // can be null for non java threads (only c frames then)
  JavaThread* _thread;

  bool _skip_c_frames;

  int _max_c_frames_skip;

  // current frame (surrounding frame if inlined)
  frame _frame;

  // is os::get_sender_for_C_frame currently supported?
  // invariant: true if _thread is null
  const bool supports_os_get_frame;

  // StackWalkerError + StackWalkerReturn
  int _state;

  bool _inlined;

  Method *_method;

  int _bci;

  RegisterMap _map;

  compiledFrameStream _st;

  bool in_c_on_top;

  frame next_c_frame(frame fr);

  void init();

  void process();

  void advance();

  // reset _method, _bci and inlined
  void reset();

  // set the state and reset everything besides interpreted and compiled frame
  void set_state(int state);

  // check that current frame is processable
  bool checkFrame();

  void advance_normal();

  void advance_fully_c();

  void process_normal();

  void process_in_compiled();

public:

  StackWalker(JavaThread* thread, frame top_frame, bool skip_c_frames = true, int max_c_frames_skip = -1);

  // requires a non null thread
  StackWalker(JavaThread* thread, bool skip_c_frames = true, int max_c_frames_skip = -1);

  // returns an error code < 0 on error and StackWalkerReturn code otherwise.
  // 0 == ended,
  int next();

  // skip all c frames, return true if Java frame found
  bool skip_c_frames();

  // call advance at most skip times in a row
  void skip_frames(int skip);

  // StackWalkerError + StackWalkerReturn
  int state() const { return _state; }

  bool at_end() const { return _state == STACKWALKER_END; }

  bool at_error() const { return _state <= 0; }

  // not at and and not at error
  bool has_frame() const { return !at_end() && !at_error(); }

  bool is_interpreted_frame() const { return _state == STACKWALKER_INTERPRETED_FRAME; }

  bool is_compiled_frame() const { return _state == STACKWALKER_COMPILED_FRAME; }

  bool is_native_frame() const { return _state == STACKWALKER_NATIVE_FRAME; }

  bool is_c_frame() const { return _state == STACKWALKER_C_FRAME; }

  bool is_java_frame() const { return is_interpreted_frame() || is_compiled_frame() || is_native_frame(); }

  // inlined, returns true only for inlined compiled frames, otherwise false
  bool is_inlined() const { return _inlined; }

  // current frame (surrounding frame if inlined) or NULL if at error or at end
  const frame* base_frame() const { return has_frame() ? &_frame : NULL; }

  // current method or NULL if at error or at end
  Method* method() const { return _method; }

  // bci or -1 if not at a Java frame
  int bci() const { return _bci; }

};

#endif // SHARE_JFR_RECORDER_STACKTRACE_STACKWALKER_HPP
