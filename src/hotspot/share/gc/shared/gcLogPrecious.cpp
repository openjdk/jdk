/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcLogPrecious.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"

stringStream* GCLogPrecious::_temp = NULL;
Mutex* GCLogPrecious::_lock = NULL;

class GCLogPreciousLine : public CHeapObj<mtGC> {
  const char* const _line;
  GCLogPreciousLine* volatile _next;

public:
  GCLogPreciousLine(const char* line) :
      _line(line),
      _next(NULL) {}

  const char* line() const { return _line; }

  GCLogPreciousLine* next() const { return Atomic::load_acquire(&_next); }
  void set_next(GCLogPreciousLine* next) { Atomic::release_store(&_next, next); }
};

GCLogPreciousLine* volatile GCLogPrecious::_head = NULL;
GCLogPreciousLine* GCLogPrecious::_tail = NULL;

void GCLogPrecious::append_line(const char* const str) {
  GCLogPreciousLine* line = new GCLogPreciousLine(str);

  GCLogPreciousLine* head = _head;
  GCLogPreciousLine* tail = _tail;

  if (head == NULL) {
    Atomic::release_store(&_head, line);
  }
  if (_tail != NULL) {
    tail->set_next(line);
  }
  _tail = line;
}

void GCLogPrecious::initialize() {
  _temp = new (ResourceObj::C_HEAP, mtGC) stringStream();
  _lock = new Mutex(Mutex::tty,
                    "GCLogPrecious Lock",
                    true,
                    Mutex::_safepoint_check_never);
}

void GCLogPrecious::vwrite_inner(LogTargetHandle log, const char* format, va_list args) {
  // Generate the string in the temp buffer
  _temp->reset();
  _temp->vprint(format, args);

  // Save it in the precious lines buffer
  append_line(os::strdup_check_oom(_temp->base(), mtGC));

  // Log it to UL
  log.print("%s", _temp->base());

  // Leave _temp buffer to be used by vwrite_and_debug
}

void GCLogPrecious::vwrite(LogTargetHandle log, const char* format, va_list args) {
  MutexLocker locker(_lock, Mutex::_no_safepoint_check_flag);
  vwrite_inner(log, format, args);
}

void GCLogPrecious::vwrite_and_debug(LogTargetHandle log,
                                     const char* format,
                                     va_list args
                                     DEBUG_ONLY(COMMA const char* file)
                                     DEBUG_ONLY(COMMA int line)) {
  DEBUG_ONLY(const char* debug_message;)

  {
    MutexLocker locker(_lock, Mutex::_no_safepoint_check_flag);
    vwrite_inner(log, format, args);
    DEBUG_ONLY(debug_message = strdup(_temp->base()));
  }

  // report error outside lock scope, since report_vm_error will call print_on_error
  DEBUG_ONLY(report_vm_error(file, line, debug_message);)
  DEBUG_ONLY(BREAKPOINT;)
}

void GCLogPrecious::print_on_error(outputStream* st) {
  GCLogPreciousLine* line = Atomic::load_acquire(&_head);
  if (line != NULL) {
    st->print_cr("GC Precious Log:");
    while (line != NULL) {
      st->print_cr(" %s", line->line());
      line = line->next();
    }
    st->print_cr("");
  }
}
