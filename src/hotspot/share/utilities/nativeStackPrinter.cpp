/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.inline.hpp"
#include "utilities/decoder.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/nativeStackPrinter.hpp"
#include "utilities/ostream.hpp"

bool NativeStackPrinter::print_stack(outputStream* st, char* buf, int buf_size,
                                     address& lastpc, bool print_source_info,
                                     int max_frames) {
  if (os::platform_print_native_stack(st, _context, buf, buf_size, lastpc)) {
    return true;
  } else {
    print_stack_from_frame(st, buf, buf_size, print_source_info, max_frames);
    return false;
  }
}

void NativeStackPrinter::print_stack_from_frame(outputStream* st, frame fr,
                                                char* buf, int buf_size,
                                                bool print_source_info, int max_frames) {
  // see if it's a valid frame
  if (fr.pc()) {
    st->print_cr("Native frames: (J=compiled Java code, j=interpreted, Vv=VM code, C=native code)");
    const int limit = max_frames == -1 ? StackPrintLimit
                                       : MIN2(max_frames, StackPrintLimit);
    int count = 0;
    while (count++ < limit) {
      fr.print_on_error(st, buf, buf_size);
      if (fr.pc()) { // print source file and line, if available
        char filename[128];
        int line_no;
        if (count == 1 && _lineno != 0) {
          // We have source information for the first frame for internal errors,
          // there is no need to parse it from the symbols.
          st->print("  (%s:%d)", _filename, _lineno);
        } else if (print_source_info &&
                   Decoder::get_source_info(fr.pc(), filename, sizeof(filename), &line_no, count != 1)) {
          st->print("  (%s:%d)", filename, line_no);
        }
      }
      st->cr();
      fr = frame::next_frame(fr, _current);
      if (fr.pc() == nullptr) {
        break;
      }
    }

    if (count > limit) {
      st->print_cr("...<more frames>...");
    }

  } else {
    st->print_cr("Native frames: <unavailable>");
  }
}
