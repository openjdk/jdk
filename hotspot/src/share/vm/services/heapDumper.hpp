/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SERVICES_HEAPDUMPER_HPP
#define SHARE_VM_SERVICES_HEAPDUMPER_HPP

#include "memory/allocation.hpp"
#include "oops/klassOop.hpp"
#include "oops/oop.hpp"
#include "runtime/os.hpp"

// HeapDumper is used to dump the java heap to file in HPROF binary format:
//
//  { HeapDumper dumper(true /* full GC before heap dump */);
//    if (dumper.dump("/export/java.hprof")) {
//      ResourceMark rm;
//      tty->print_cr("Dump failed: %s", dumper.error_as_C_string());
//    } else {
//      // dump succeeded
//    }
//  }
//

class HeapDumper : public StackObj {
 private:
  char* _error;
  bool _print_to_tty;
  bool _gc_before_heap_dump;
  bool _oome;
  elapsedTimer _t;

  HeapDumper(bool gc_before_heap_dump, bool print_to_tty, bool oome) :
    _gc_before_heap_dump(gc_before_heap_dump), _error(NULL), _print_to_tty(print_to_tty), _oome(oome) { }

  // string representation of error
  char* error() const                   { return _error; }
  void set_error(char* error);

  // indicates if progress messages can be sent to tty
  bool print_to_tty() const             { return _print_to_tty; }

  // internal timer.
  elapsedTimer* timer()                 { return &_t; }

  static void dump_heap(bool oome);

 public:
  HeapDumper(bool gc_before_heap_dump) :
    _gc_before_heap_dump(gc_before_heap_dump), _error(NULL), _print_to_tty(false), _oome(false) { }

  ~HeapDumper();

  // dumps the heap to the specified file, returns 0 if success.
  int dump(const char* path);

  // returns error message (resource allocated), or NULL if no error
  char* error_as_C_string() const;

  static void dump_heap()    KERNEL_RETURN;

  static void dump_heap_from_oome()    KERNEL_RETURN;
};

#endif // SHARE_VM_SERVICES_HEAPDUMPER_HPP
