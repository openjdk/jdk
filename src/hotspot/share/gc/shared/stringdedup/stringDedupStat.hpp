/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPSTAT_HPP
#define SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPSTAT_HPP

#include "memory/allocation.hpp"
#include "runtime/os.hpp"

// Macros for GC log output formating
#define STRDEDUP_OBJECTS_FORMAT         UINTX_FORMAT_W(12)
#define STRDEDUP_TIME_FORMAT            "%.3fs"
#define STRDEDUP_TIME_PARAM(time)       (time)
#define STRDEDUP_TIME_FORMAT_MS         "%.3fms"
#define STRDEDUP_TIME_PARAM_MS(time)    ((time) * MILLIUNITS)
#define STRDEDUP_PERCENT_FORMAT         "%5.1f%%"
#define STRDEDUP_PERCENT_FORMAT_NS      "%.1f%%"
#define STRDEDUP_BYTES_FORMAT           "%8.1f%s"
#define STRDEDUP_BYTES_FORMAT_NS        "%.1f%s"
#define STRDEDUP_BYTES_PARAM(bytes)     byte_size_in_proper_unit((double)(bytes)), proper_unit_for_byte_size((bytes))

//
// Statistics gathered by the deduplication thread.
//
class StringDedupStat : public CHeapObj<mtGC> {
protected:
  // Counters
  uintx  _inspected;
  uintx  _skipped;
  uintx  _hashed;
  uintx  _known;
  uintx  _new;
  uintx  _new_bytes;
  uintx  _deduped;
  uintx  _deduped_bytes;
  uintx  _idle;
  uintx  _exec;
  uintx  _block;

  // Time spent by the deduplication thread in different phases
  double _start_concurrent;
  double _end_concurrent;
  double _start_phase;
  double _idle_elapsed;
  double _exec_elapsed;
  double _block_elapsed;

public:
  StringDedupStat();

  void inc_inspected() {
    _inspected++;
  }

  void inc_skipped() {
    _skipped++;
  }

  void inc_hashed() {
    _hashed++;
  }

  void inc_known() {
    _known++;
  }

  void inc_new(uintx bytes) {
    _new++;
    _new_bytes += bytes;
  }

  virtual void deduped(oop obj, uintx bytes) {
    _deduped++;
    _deduped_bytes += bytes;
  }

  void mark_idle() {
    _start_phase = os::elapsedTime();
    _idle++;
  }

  void mark_exec() {
    double now = os::elapsedTime();
    _idle_elapsed = now - _start_phase;
    _start_phase = now;
    _start_concurrent = now;
    _exec++;
  }

  void mark_block() {
    double now = os::elapsedTime();
    _exec_elapsed += now - _start_phase;
    _start_phase = now;
    _block++;
  }

  void mark_unblock() {
    double now = os::elapsedTime();
    _block_elapsed += now - _start_phase;
    _start_phase = now;
  }

  void mark_done() {
    double now = os::elapsedTime();
    _exec_elapsed += now - _start_phase;
    _end_concurrent = now;
  }

  virtual void reset();
  virtual void add(const StringDedupStat* const stat);
  virtual void print_statistics(bool total) const;

  static void print_start(const StringDedupStat* last_stat);
  static void print_end(const StringDedupStat* last_stat, const StringDedupStat* total_stat);
};

#endif // SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPSTAT_HPP

