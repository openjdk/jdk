/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeCache.hpp"
#include "code/nmethod.hpp"
#include "runtime/frame.hpp"
#include "runtime/init.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/top.hpp"

#ifndef PRODUCT

extern "C" void findpc(int x);


void pd_ps(frame f) {
  intptr_t* sp = f.sp();
  intptr_t* prev_sp = sp - 1;
  intptr_t *pc = NULL;
  intptr_t *next_pc = NULL;
  int count = 0;
  tty->print("register window backtrace from %#x:\n", sp);
  while (sp != NULL && ((intptr_t)sp & 7) == 0 && sp > prev_sp && sp < prev_sp+1000) {
    pc      = next_pc;
    next_pc = (intptr_t*) sp[I7->sp_offset_in_saved_window()];
    tty->print("[%d] sp=%#x pc=", count, sp);
    findpc((intptr_t)pc);
    if (WizardMode && Verbose) {
      // print register window contents also
      tty->print_cr("    L0..L7: {%#x %#x %#x %#x %#x %#x %#x %#x}",
                    sp[0+0],sp[0+1],sp[0+2],sp[0+3],
                    sp[0+4],sp[0+5],sp[0+6],sp[0+7]);
      tty->print_cr("    I0..I7: {%#x %#x %#x %#x %#x %#x %#x %#x}",
                    sp[8+0],sp[8+1],sp[8+2],sp[8+3],
                    sp[8+4],sp[8+5],sp[8+6],sp[8+7]);
      // (and print stack frame contents too??)

      CodeBlob *b = CodeCache::find_blob((address) pc);
      if (b != NULL) {
        if (b->is_nmethod()) {
          methodOop m = ((nmethod*)b)->method();
          int nlocals = m->max_locals();
          int nparams  = m->size_of_parameters();
          tty->print_cr("compiled java method (locals = %d, params = %d)", nlocals, nparams);
        }
      }
    }
    prev_sp = sp;
    sp = (intptr_t *)sp[FP->sp_offset_in_saved_window()];
    sp = (intptr_t *)((intptr_t)sp + STACK_BIAS);
    count += 1;
  }
  if (sp != NULL)
    tty->print("[%d] sp=%#x [bogus sp!]", count, sp);
}

#endif // PRODUCT
