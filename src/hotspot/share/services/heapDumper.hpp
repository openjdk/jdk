/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_SERVICES_HEAPDUMPER_HPP
#define SHARE_SERVICES_HEAPDUMPER_HPP

#include "memory/allocation.hpp"
#include "oops/oop.hpp"
#include "runtime/os.hpp"

class outputStream;

// HeapDumper is used to dump the java heap to file in HPROF binary format
class HeapDumper : public StackObj {
 private:
  char* _error;
  bool _gc_before_heap_dump;
  bool _oome;
  elapsedTimer _t;

  HeapDumper(bool gc_before_heap_dump, bool oome) :
    _error(nullptr), _gc_before_heap_dump(gc_before_heap_dump), _oome(oome) { }

  // string representation of error
  char* error() const                   { return _error; }
  void set_error(char const* error);

  // internal timer.
  elapsedTimer* timer()                 { return &_t; }

  static void dump_heap(bool oome);

 public:
  HeapDumper(bool gc_before_heap_dump) :
    _error(nullptr), _gc_before_heap_dump(gc_before_heap_dump), _oome(false) { }

  ~HeapDumper();

  // dumps the heap to the specified file, returns 0 if success.
  // additional info is written to out if not null.
  // compression >= 0 creates a gzipped file with the given compression level.
  // parallel_thread_num >= 0 indicates thread numbers of parallel object dump.
  int dump(const char* path, outputStream* out = nullptr, int compression = -1, bool overwrite = false, uint parallel_thread_num = default_num_of_dump_threads());

  // returns error message (resource allocated), or null if no error
  char* error_as_C_string() const;

  static void dump_heap()    NOT_SERVICES_RETURN;

  static void dump_heap_from_oome()    NOT_SERVICES_RETURN;

  // Parallel thread number for heap dump, initialize based on active processor count.
  static uint default_num_of_dump_threads() {
    return MAX2<uint>(1, (uint)os::initial_active_processor_count() * 3 / 8);
  }
};

#endif // SHARE_SERVICES_HEAPDUMPER_HPP
