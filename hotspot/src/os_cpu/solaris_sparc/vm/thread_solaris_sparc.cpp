/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "runtime/frame.inline.hpp"
#include "thread_solaris.inline.hpp"

// For Forte Analyzer AsyncGetCallTrace profiling support - thread is
// currently interrupted by SIGPROF
//
// NOTE: On Solaris, register windows are flushed in the signal handler
// except for possibly the top frame.
//
bool JavaThread::pd_get_top_frame_for_signal_handler(frame* fr_addr,
  void* ucontext, bool isInJava) {

  assert(Thread::current() == this, "caller must be current thread");
  assert(this->is_Java_thread(), "must be JavaThread");

  JavaThread* jt = (JavaThread *)this;

  if (!isInJava) {
    // make_walkable flushes register windows and grabs last_Java_pc
    // which can not be done if the ucontext sp matches last_Java_sp
    // stack walking utilities assume last_Java_pc set if marked flushed
    jt->frame_anchor()->make_walkable(jt);
  }

  // If we have a walkable last_Java_frame, then we should use it
  // even if isInJava == true. It should be more reliable than
  // ucontext info.
  if (jt->has_last_Java_frame() && jt->frame_anchor()->walkable()) {
    *fr_addr = jt->pd_last_frame();
    return true;
  }

  ucontext_t* uc = (ucontext_t*) ucontext;

  // At this point, we don't have a walkable last_Java_frame, so
  // we try to glean some information out of the ucontext.
  intptr_t* ret_sp;
  ExtendedPC addr = os::Solaris::fetch_frame_from_ucontext(this, uc,
    &ret_sp, NULL /* ret_fp only used on Solaris X86 */);
  if (addr.pc() == NULL || ret_sp == NULL) {
    // ucontext wasn't useful
    return false;
  }

  frame ret_frame(ret_sp, frame::unpatchable, addr.pc());

  // we were running Java code when SIGPROF came in
  if (isInJava) {


    // If the frame we got is safe then it is most certainly valid
    if (ret_frame.safe_for_sender(jt)) {
      *fr_addr = ret_frame;
      return true;
    }

    // If it isn't safe then we can try several things to try and get
    // a good starting point.
    //
    // On sparc the frames are almost certainly walkable in the sense
    // of sp/fp linkages. However because of recycling of windows if
    // a piece of code does multiple save's where the initial save creates
    // a real frame with a return pc and the succeeding save's are used to
    // simply get free registers and have no real pc then the pc linkage on these
    // "inner" temporary frames will be bogus.
    // Since there is in general only a nesting level like
    // this one deep in general we'll try and unwind such an "inner" frame
    // here ourselves and see if it makes sense

    frame unwind_frame(ret_frame.fp(), frame::unpatchable, addr.pc());

    if (unwind_frame.safe_for_sender(jt)) {
      *fr_addr = unwind_frame;
      return true;
    }

    // Well that didn't work. Most likely we're toast on this tick
    // The previous code would try this. I think it is dubious in light
    // of changes to safe_for_sender and the unwind trick above but
    // if it gets us a safe frame who wants to argue.

    // If we have a last_Java_sp, then the SIGPROF signal caught us
    // right when we were transitioning from _thread_in_Java to a new
    // JavaThreadState. We use last_Java_sp instead of the sp from
    // the ucontext since it should be more reliable.

    if (jt->has_last_Java_frame()) {
      ret_sp = jt->last_Java_sp();
      frame ret_frame2(ret_sp, frame::unpatchable, addr.pc());
      if (ret_frame2.safe_for_sender(jt)) {
        *fr_addr = ret_frame2;
        return true;
      }
    }

    // This is the best we can do. We will only be able to decode the top frame

    *fr_addr = ret_frame;
    return true;
  }

  // At this point, we know we weren't running Java code. We might
  // have a last_Java_sp, but we don't have a walkable frame.
  // However, we might still be able to construct something useful
  // if the thread was running native code.
  if (jt->has_last_Java_frame()) {
    assert(!jt->frame_anchor()->walkable(), "case covered above");

    frame ret_frame(jt->last_Java_sp(), frame::unpatchable, addr.pc());
    *fr_addr = ret_frame;
    return true;
  }

  // nothing else to try but what we found initially

  *fr_addr = ret_frame;
  return true;
}

void JavaThread::cache_global_variables() { }

