/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_BUFFERINGOOPCLOSURE_HPP
#define SHARE_VM_GC_G1_BUFFERINGOOPCLOSURE_HPP

#include "memory/iterator.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"

// A BufferingOops closure tries to separate out the cost of finding roots
// from the cost of applying closures to them.  It maintains an array of
// ref-containing locations.  Until the array is full, applying the closure
// to an oop* merely records that location in the array.  Since this
// closure app cost is small, an elapsed timer can approximately attribute
// all of this cost to the cost of finding the roots.  When the array fills
// up, the wrapped closure is applied to all elements, keeping track of
// this elapsed time of this process, and leaving the array empty.
// The caller must be sure to call "done" to process any unprocessed
// buffered entries.

class BufferingOopClosure: public OopClosure {
  friend class BufferingOopClosureTest;
protected:
  static const size_t BufferLength = 1024;

  // We need to know if the buffered addresses contain oops or narrowOops.
  // We can't tag the addresses the way StarTask does, because we need to
  // be able to handle unaligned addresses coming from oops embedded in code.
  //
  // The addresses for the full-sized oops are filled in from the bottom,
  // while the addresses for the narrowOops are filled in from the top.
  OopOrNarrowOopStar  _buffer[BufferLength];
  OopOrNarrowOopStar* _oop_top;
  OopOrNarrowOopStar* _narrowOop_bottom;

  OopClosure* _oc;
  double      _closure_app_seconds;


  bool is_buffer_empty() {
    return _oop_top == _buffer && _narrowOop_bottom == (_buffer + BufferLength - 1);
  }

  bool is_buffer_full() {
    return _narrowOop_bottom < _oop_top;
  }

  // Process addresses containing full-sized oops.
  void process_oops() {
    for (OopOrNarrowOopStar* curr = _buffer; curr < _oop_top; ++curr) {
      _oc->do_oop((oop*)(*curr));
    }
    _oop_top = _buffer;
  }

  // Process addresses containing narrow oops.
  void process_narrowOops() {
    for (OopOrNarrowOopStar* curr = _buffer + BufferLength - 1; curr > _narrowOop_bottom; --curr) {
      _oc->do_oop((narrowOop*)(*curr));
    }
    _narrowOop_bottom = _buffer + BufferLength - 1;
  }

  // Apply the closure to all oops and clear the buffer.
  // Accumulate the time it took.
  void process_buffer() {
    double start = os::elapsedTime();

    process_oops();
    process_narrowOops();

    _closure_app_seconds += (os::elapsedTime() - start);
  }

  void process_buffer_if_full() {
    if (is_buffer_full()) {
      process_buffer();
    }
  }

  void add_narrowOop(narrowOop* p) {
    assert(!is_buffer_full(), "Buffer should not be full");
    *_narrowOop_bottom = (OopOrNarrowOopStar)p;
    _narrowOop_bottom--;
  }

  void add_oop(oop* p) {
    assert(!is_buffer_full(), "Buffer should not be full");
    *_oop_top = (OopOrNarrowOopStar)p;
    _oop_top++;
  }

public:
  virtual void do_oop(narrowOop* p) {
    process_buffer_if_full();
    add_narrowOop(p);
  }

  virtual void do_oop(oop* p)       {
    process_buffer_if_full();
    add_oop(p);
  }

  void done() {
    if (!is_buffer_empty()) {
      process_buffer();
    }
  }

  double closure_app_seconds() {
    return _closure_app_seconds;
  }

  BufferingOopClosure(OopClosure *oc) :
    _oc(oc),
    _oop_top(_buffer),
    _narrowOop_bottom(_buffer + BufferLength - 1),
    _closure_app_seconds(0.0) { }
};

#endif // SHARE_VM_GC_G1_BUFFERINGOOPCLOSURE_HPP
