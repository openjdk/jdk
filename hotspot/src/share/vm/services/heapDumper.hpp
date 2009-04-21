/*
 * Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

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
  elapsedTimer _t;

  // string representation of error
  char* error() const                   { return _error; }
  void set_error(char* error);

  // indicates if progress messages can be sent to tty
  bool print_to_tty() const             { return _print_to_tty; }

  // internal timer.
  elapsedTimer* timer()                 { return &_t; }

 public:
  HeapDumper(bool gc_before_heap_dump) :
    _gc_before_heap_dump(gc_before_heap_dump), _error(NULL), _print_to_tty(false) { }
  HeapDumper(bool gc_before_heap_dump, bool print_to_tty) :
    _gc_before_heap_dump(gc_before_heap_dump), _error(NULL), _print_to_tty(print_to_tty) { }

  ~HeapDumper();

  // dumps the heap to the specified file, returns 0 if success.
  int dump(const char* path);

  // returns error message (resource allocated), or NULL if no error
  char* error_as_C_string() const;

  static void dump_heap()    KERNEL_RETURN;
};
