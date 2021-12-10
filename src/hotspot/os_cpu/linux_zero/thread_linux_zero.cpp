/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2009, 2021, Red Hat, Inc. All rights reserved.
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
#include "runtime/thread.inline.hpp"

frame JavaThread::pd_last_frame() {
  assert(has_last_Java_frame(), "must have last_Java_sp() when suspended");
  return frame(last_Java_fp(), last_Java_sp());
}

void JavaThread::cache_global_variables() {
  // nothing to do
}

bool JavaThread::pd_get_top_frame_for_signal_handler(frame* fr_addr,
                                         void* ucontext,
                                         bool isInJava) {
  if (has_last_Java_frame()) {
    *fr_addr = pd_last_frame();
    return true;
  }

  if (isInJava) {
    // We know we are in Java, but there is no frame?
    // Try to find the top-most Java frame on Zero stack then.
    intptr_t* sp = zero_stack()->sp();
    ZeroFrame* zf = top_zero_frame();
    while (zf != NULL) {
      if (zf->is_interpreter_frame()) {
        interpreterState istate = zf->as_interpreter_frame()->interpreter_state();
        if (istate->self_link() == istate) {
          // Valid interpreter state found, this is our frame.
          *fr_addr = frame(zf, sp);
          return true;
        }
      }
      sp = ((intptr_t *) zf) + 1;
      zf = zf->next();
    }
  }

  // No dice.
  return false;
}

bool JavaThread::pd_get_top_frame_for_profiling(frame* fr_addr,
                                    void* ucontext,
                                    bool isInJava) {
  return pd_get_top_frame_for_signal_handler(fr_addr, ucontext, isInJava);
}
