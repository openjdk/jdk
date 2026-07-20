/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_UTILITIES_NATIVESTACKPRINTER_HPP
#define SHARE_UTILITIES_NATIVESTACKPRINTER_HPP

#include "memory/allocation.hpp"
#include "runtime/frame.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"

// Forward declarations
class outputStream;
class Thread;

// Helper class to do native stack printing from various contexts
// including during crash reporting.
// The NativeStackPrinter is created with the basic context information
// available from the caller. Then the print_stack function is called
// to do the actual printing.
class NativeStackPrinter : public StackObj {
  Thread* _current;       // Current thread if known
  const void* _context;   // OS crash context if known
  const char* _filename;  // Source file name if known
  int _lineno;            // Source file line number if known

 public:
  // Creates a NativeStackPrinter using the given additional context
  // information:
  // - the current thread is used for frame-based stack walking
  // - context is the crash context from the OS and can be used to get a frame;
  //   otherwise os::current_frame() will be used
  // - filename and lineno provide details from the fatal error handler so we
  //   can skip use of the Decoder for the first line (optimization)
  NativeStackPrinter(Thread* current_or_null,
                     const void* context,
                     const char* filename,
                     int lineno) :
    _current(current_or_null),
    _context(context),
    _filename(filename),
    _lineno(lineno) {
    assert((_lineno == 0 && _filename == nullptr) ||
           (_lineno  > 0 && _filename != nullptr),
           "file name and line number need to be provided together");
  }

  NativeStackPrinter(Thread* current_or_null)
    : NativeStackPrinter(current_or_null, nullptr, nullptr, 0) {}

  // Prints the stack of the current thread to the given stream.
  // We first try to print via os::platform_print_native_stack. If that
  // succeeds then lastpc is set and we return true. Otherwise we do a
  // frame walk to print the stack, and return false.
  // - st: the stream to print to
  // - buf, buf_size: temporary buffer to use for formatting output
  // - print_source_info: see print_stack_from_frame
  // - max_frames: see print_stack_from_frame
  //
  bool print_stack(outputStream* st, char* buf, int buf_size,
                   address& lastpc, bool print_source_info,
                   int max_frames);

  // Prints the stack to st by walking the frames starting from
  // either the context frame, else the current frame.
  // - st: the stream to print to
  // - buf, buf_size: temporary buffer to use when printing frames
  // - print_source_info: if true obtains source information from the Decoder
  //                      if available. (Useful but may slow down, timeout or
  //                      misfunction in error situations)
  // - max_frames: the maximum number of frames to print. -1 means print all.
  //               However, StackPrintLimit sets a hard limit on the maximum.
  void print_stack_from_frame(outputStream* st, frame fr,
                              char* buf, int buf_size,
                              bool print_source_info, int max_frames);

  // Prints the stack to st by walking the frames starting from
  // either the context frame, else the current frame.
  void print_stack_from_frame(outputStream* st,
                              char* buf, int buf_size,
                              bool print_source_info, int max_frames) {
      frame fr = _context != nullptr  ? os::fetch_frame_from_context(_context)
                                      : os::current_frame();
      print_stack_from_frame(st, fr, buf, buf_size, print_source_info, max_frames);
  }
};

#endif // SHARE_UTILITIES_NATIVESTACKPRINTER_HPP
