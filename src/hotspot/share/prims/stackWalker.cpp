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

#include "stackWalker.hpp"
#include "code/debugInfoRec.hpp"
#include "code/pcDesc.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "prims/forte.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/registerMap.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vframe.inline.hpp"
#include "runtime/vframeArray.hpp"
#include "runtime/vframe_hp.hpp"
#include "compiler/compilerDefinitions.hpp"


// the following code was originally present in the forte.cpp file
// it is moved in to this file to allow reuse in JFR

#define LOG 0
#define ST_LOG(...) LOG ? printf(__VA_ARGS__) : 1

compiledFrameStream::compiledFrameStream(JavaThread *jt, frame fr, bool stop_at_java_call_stub) :
  vframeStreamCommon(RegisterMap(jt, RegisterMap::UpdateMap::skip,
    RegisterMap::ProcessFrames::skip, RegisterMap::WalkContinuation::skip)),
  cf_next_into_inlined(false), _invalid(false) {

  _stop_at_java_call_stub = stop_at_java_call_stub;
  _frame = fr;

  // We must always have a valid frame to start filling

  bool filled_in = fill_from_frame();

  assert(filled_in, "invariant");

}


// Solaris SPARC Compiler1 needs an additional check on the grandparent
// of the top_frame when the parent of the top_frame is interpreted and
// the grandparent is compiled. However, in this method we do not know
// the relationship of the current _frame relative to the top_frame so
// we implement a more broad sanity check. When the previous callee is
// interpreted and the current sender is compiled, we verify that the
// current sender is also walkable. If it is not walkable, then we mark
// the current vframeStream as at the end.
//
// returns true if inlined
void compiledFrameStream::cf_next() {
  assert(!_invalid, "invalid stream used");
  // handle frames with inlining
  cf_next_into_inlined = false;
  if (_mode == compiled_mode &&
      vframeStreamCommon::fill_in_compiled_inlined_sender()) {
    cf_next_into_inlined = true;
    return;
  }

  // handle general case

  int loop_count = 0;
  int loop_max = MaxJavaStackTraceDepth * 2;


  do {

    loop_count++;

    // By the time we get here we should never see unsafe but better
    // safe then segv'd

    if ((loop_max != 0 && loop_count > loop_max) || !_frame.safe_for_sender(_thread)) {
      _mode = at_end_mode;
      return;
    }

    _frame = _frame.sender(&_reg_map);

  } while (!fill_from_frame());
}

// Determine if 'fr' is a decipherable compiled frame. We are already
// assured that fr is for a java compiled method.

static bool is_decipherable_compiled_frame(JavaThread* thread, frame* fr, CompiledMethod* nm) {
  assert(nm->is_java_method(), "invariant");

  if (thread->has_last_Java_frame() && thread->last_Java_pc() == fr->pc()) {
    // We're stopped at a call into the JVM so look for a PcDesc with
    // the actual pc reported by the frame.
    PcDesc* pc_desc = nm->pc_desc_at(fr->pc());

    // Did we find a useful PcDesc?
    if (pc_desc != NULL &&
        pc_desc->scope_decode_offset() != DebugInformationRecorder::serialized_null) {
      return true;
    }
  }

  // We're at some random pc in the compiled method so search for the PcDesc
  // whose pc is greater than the current PC.  It's done this way
  // because the extra PcDescs that are recorded for improved debug
  // info record the end of the region covered by the ScopeDesc
  // instead of the beginning.
  PcDesc* pc_desc = nm->pc_desc_near(fr->pc() + 1);

  // Now do we have a useful PcDesc?
  if (pc_desc == NULL ||
      pc_desc->scope_decode_offset() == DebugInformationRecorder::serialized_null) {
    // No debug information is available for this PC.
    //
    // vframeStreamCommon::fill_from_frame() will decode the frame depending
    // on the state of the thread.
    //
    // Case #1: If the thread is in Java (state == _thread_in_Java), then
    // the vframeStreamCommon object will be filled as if the frame were a native
    // compiled frame. Therefore, no debug information is needed.
    //
    // Case #2: If the thread is in any other state, then two steps will be performed:
    // - if asserts are enabled, found_bad_method_frame() will be called and
    //   the assert in found_bad_method_frame() will be triggered;
    // - if asserts are disabled, the vframeStreamCommon object will be filled
    //   as if it were a native compiled frame.
    //
    // Case (2) is similar to the way interpreter frames are processed in
    // vframeStreamCommon::fill_from_interpreter_frame in case no valid BCI
    // was found for an interpreted frame. If asserts are enabled, the assert
    // in found_bad_method_frame() will be triggered. If asserts are disabled,
    // the vframeStreamCommon object will be filled afterwards as if the
    // interpreter were at the point of entering into the method.
    return false;
  }

  // This PcDesc is useful however we must adjust the frame's pc
  // so that the vframeStream lookups will use this same pc
  fr->set_pc(pc_desc->real_pc(nm));
  return true;
}


// Determine if 'fr' is a walkable interpreted frame. Returns false
// if it is not. *method_p, and *bci_p are not set when false is
// returned. *method_p is non-NULL if frame was executing a Java
// method. *bci_p is != -1 if a valid BCI in the Java method could
// be found.
// Note: this method returns true when a valid Java method is found
// even if a valid BCI cannot be found.

static bool is_decipherable_interpreted_frame(JavaThread* thread,
                                              frame* fr,
                                              Method** method_p,
                                              int* bci_p) {
  assert(fr->is_interpreted_frame(), "just checking");

  // top frame is an interpreted frame
  // check if it is walkable (i.e. valid Method* and valid bci)

  // Because we may be racing a gc thread the method and/or bci
  // of a valid interpreter frame may look bad causing us to
  // fail the is_interpreted_frame_valid test. If the thread
  // is in any of the following states we are assured that the
  // frame is in fact valid and we must have hit the race.

  JavaThreadState state = thread->thread_state();
  bool known_valid = (state == _thread_in_native ||
                      state == _thread_in_vm ||
                      state == _thread_blocked );

  if (known_valid || fr->is_interpreted_frame_valid(thread)) {

    // The frame code should completely validate the frame so that
    // references to Method* and bci are completely safe to access
    // If they aren't the frame code should be fixed not this
    // code. However since gc isn't locked out the values could be
    // stale. This is a race we can never completely win since we can't
    // lock out gc so do one last check after retrieving their values
    // from the frame for additional safety

    Method* method = fr->interpreter_frame_method();

    // We've at least found a method.
    // NOTE: there is something to be said for the approach that
    // if we don't find a valid bci then the method is not likely
    // a valid method. Then again we may have caught an interpreter
    // frame in the middle of construction and the bci field is
    // not yet valid.
    if (!Method::is_valid_method(method)) return false;
    *method_p = method; // If the Method* found is invalid, it is
                        // ignored by forte_fill_call_trace_given_top().
                        // So set method_p only if the Method is valid.

    address bcp = fr->interpreter_frame_bcp();
    int bci = method->validate_bci_from_bcp(bcp);

    // note: bci is set to -1 if not a valid bci
    *bci_p = bci;
    return true;
  }

  return false;
}

static bool is_decipherable_native_frame(frame* fr, CompiledMethod* nm) {
  assert(nm->is_native_method(), "invariant");
  return fr->cb()->frame_size() >= 0;
}

StackWalker::StackWalker(JavaThread* thread, frame top_frame, bool skip_c_frames, int max_c_frames_skip):
  _thread(thread),
  _skip_c_frames(skip_c_frames), _max_c_frames_skip(max_c_frames_skip), _frame(top_frame),
  supports_os_get_frame(!skip_c_frames && os::current_frame().pc() != NULL),
  _state(STACKWALKER_START), _map(thread, RegisterMap::UpdateMap::skip,
     RegisterMap::ProcessFrames::skip, RegisterMap::WalkContinuation::skip),
    in_c_on_top(false) {
  init();
}

StackWalker::StackWalker(JavaThread* thread, bool skip_c_frames, int max_c_frames_skip):
  _thread(thread), _skip_c_frames(skip_c_frames), _max_c_frames_skip(max_c_frames_skip),
  supports_os_get_frame(!skip_c_frames && os::current_frame().pc() != NULL),
  _state(STACKWALKER_START), _map(thread, RegisterMap::UpdateMap::skip,
     RegisterMap::ProcessFrames::skip, RegisterMap::WalkContinuation::skip),
  in_c_on_top(false) {
  if (!thread->has_last_Java_frame()) {
    set_state(STACKWALKER_END);
    return;
  }

  _frame = _thread->last_frame();
  init();
}

void StackWalker::init() {
  if (checkFrame()) {
    process();
    if (_skip_c_frames) {
      skip_c_frames();
    }
  }
}

/**
 *
 * Gets the caller frame of `fr`.
 *
 * based on the next_frame method from vmError.cpp aka pns gdb command
 *
 * only usable when we are sure to not have any compiled frames afterwards,
 * as this method might trip up
 *
 * Problem: leads to "invalid bci or invalid scope error" in vframestream
 */
 frame StackWalker::next_c_frame(frame fr) {
  // Compiled code may use EBP register on x86 so it looks like
  // non-walkable C frame. Use frame.sender() for java frames.
  frame invalid;
  // Catch very first native / c frame by using stack address.
  // For JavaThread stack_base and stack_size should be set.
  if (!_thread->is_in_full_stack((address)(fr.real_fp() + 1))) {
    return invalid;
  }
  if (fr.is_java_frame() || fr.is_native_frame() || fr.is_runtime_frame() || !supports_os_get_frame) {
    if (!fr.safe_for_sender(_thread)) {
      return invalid;
    }
    RegisterMap map(_thread, RegisterMap::UpdateMap::skip, RegisterMap::ProcessFrames::skip,
        RegisterMap::WalkContinuation::skip); // No update
    return fr.sender(&map);
  } else {
    // is_first_C_frame() does only simple checks for frame pointer,
    // it will pass if java compiled code has a pointer in EBP.
    if (os::is_first_C_frame(&fr)) {
      return invalid;
    }
    return os::get_sender_for_C_frame(&fr);
  }
}

void StackWalker::reset() {
  _inlined = false;
  _method = NULL;
  _bci = -1;
}

void StackWalker::set_state(int state) {
  _state = state;
  if (_state != STACKWALKER_INTERPRETED_FRAME &&
      _state != STACKWALKER_COMPILED_FRAME &&
      _state != STACKWALKER_NATIVE_FRAME) {
    reset();
  }
}

void StackWalker::advance() {
  if (!has_frame()) {
    return;
  }
  if (in_c_on_top) {
    advance_fully_c();
  } else {
    advance_normal();
    process();
  }
}

bool StackWalker::checkFrame() {
  ST_LOG("_frame.is_first_frame() %d !_frame.safe_for_sender(_thread) %d\n", _frame.is_first_frame(), !_frame.safe_for_sender(_thread));
  if (_frame.is_first_frame() || !_frame.safe_for_sender(_thread)) {
    if (_skip_c_frames) {
      set_state(STACKWALKER_END);
      return false;
    } else {
      in_c_on_top = true;
      ST_LOG("set in_c_on_top to true\n");
      set_state(STACKWALKER_C_FRAME);
    }
  }
  return true;
}

void StackWalker::advance_normal() {
  assert(!_inlined || in_c_on_top || !_st.invalid(), "have to advance somehow");
  if (checkFrame()) {
    ST_LOG("advance_normal check frame ok is_c_on_top %d _inlined %d\n", in_c_on_top, _inlined);
    if (in_c_on_top) {
      advance_fully_c();
    } else if (!_inlined) {
      if (_frame.safe_for_sender(_thread)) {
        _frame = _frame.sender(&_map);
      } else {
        in_c_on_top = true;
      }
    }
  }
}

void StackWalker::process() {
  if (in_c_on_top || at_end()){ // nothing to do
    return;
  }
  ST_LOG("%d\n", __LINE__);
  if (_st.invalid()) {
    process_normal();
  } else {
    process_in_compiled();
  }
}

// assumes that _frame has been advanced and not already in compiled stream
// leaves the _frame unchanged
void StackWalker::process_normal() {
  ST_LOG("process_normal\n");
  if (_frame.is_native_frame()) {
    ST_LOG("is_native_frame\n");
    CompiledMethod* nm = _frame.cb()->as_compiled_method();
    if (!is_decipherable_native_frame(&_frame, nm)) {
      set_state(STACKWALKER_INDECIPHERABLE_FRAME);
      return;
    }
    _method = nm->method();
    _bci = -1;
    _inlined = false;
    set_state(STACKWALKER_NATIVE_FRAME);
    return;
  } else if (_frame.is_java_frame()) { // another validity check
    ST_LOG("process_normal java frame\n");
    if (_frame.is_interpreted_frame()) {
      _inlined = false;
      if (!_frame.is_interpreted_frame_valid(_thread) || !is_decipherable_interpreted_frame(_thread, &_frame, &_method, &_bci)) {
        set_state(STACKWALKER_INDECIPHERABLE_FRAME);
        return;
      }
      if (!Method::is_valid_method(_method)) {
        // we throw away everything we've gathered in this sample since
        // none of it is safe
        ST_LOG("interpreted method not valid\n");
        set_state(STACKWALKER_GC_ACTIVE); // -2
        return;
      }
      set_state(STACKWALKER_INTERPRETED_FRAME);
      assert(!_inlined || in_c_on_top || !_st.invalid(), "have to advance somehow 2");
      return;
    } else if (_frame.is_compiled_frame()) {
      CompiledMethod* nm = _frame.cb()->as_compiled_method();
      if (!is_decipherable_compiled_frame(_thread, &_frame, nm)){
        set_state(STACKWALKER_INDECIPHERABLE_FRAME);
        return;
      }
      _st = compiledFrameStream(_thread, _frame, false);
      set_state(STACKWALKER_COMPILED_FRAME);
      process_in_compiled();
      return;
    }
  }
  set_state(STACKWALKER_C_FRAME);
}

// assumes that work has to be done with compiledFrameStream
// leaves the _frame unchanged
// only changes the compiledFrameStream (advances it after copying the data)
void StackWalker::process_in_compiled() {
  assert(!_st.invalid(), "st is invalid");
  if (_st.at_end()) {
    advance_normal();
    _st = {};
    return;
  }
  _method = _st.method();
  _bci = _st.bci();

  if (!Method::is_valid_method(_method)) {
    // we throw away everything we've gathered in this sample since
    // none of it is safe
    ST_LOG("compiled method not valid\n");
    set_state(STACKWALKER_GC_ACTIVE);
    return;
  }
  _inlined = _st.inlined();
  if (!_inlined) {
    _st = {};
    //advance_normal();
    return;
  }
  _st.cf_next();
}

void StackWalker::advance_fully_c() {
  if ((_frame = next_c_frame(_frame)).pc() != NULL) {
    set_state(STACKWALKER_C_FRAME);
  } else {
    set_state(STACKWALKER_END);
  }
}

bool StackWalker::skip_c_frames() {
  int i = 0;
  for (; (i < _max_c_frames_skip || _max_c_frames_skip == -1) && is_c_frame(); i++) {
    advance();
  }
  if (is_c_frame()) {
    set_state(STACKWALKER_NO_JAVA_FRAME);
    return false;
  }
  return is_java_frame();
}

void StackWalker::skip_frames(int skip) {
  for (int i = 0; i < skip && !at_end(); i++) {
    advance();
  }
}

int StackWalker::next(){
  ST_LOG("next\n");
  advance();
  if (_skip_c_frames) {
    skip_c_frames();
  }
  return _state;
}