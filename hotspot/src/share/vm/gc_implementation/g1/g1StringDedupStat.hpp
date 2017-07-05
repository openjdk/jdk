/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1STRINGDEDUPSTAT_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1STRINGDEDUPSTAT_HPP

#include "memory/allocation.hpp"
#include "runtime/os.hpp"

// Macros for GC log output formating
#define G1_STRDEDUP_OBJECTS_FORMAT         UINTX_FORMAT_W(12)
#define G1_STRDEDUP_TIME_FORMAT            "%1.7lf secs"
#define G1_STRDEDUP_PERCENT_FORMAT         "%5.1lf%%"
#define G1_STRDEDUP_PERCENT_FORMAT_NS      "%.1lf%%"
#define G1_STRDEDUP_BYTES_FORMAT           "%8.1lf%s"
#define G1_STRDEDUP_BYTES_FORMAT_NS        "%.1lf%s"
#define G1_STRDEDUP_BYTES_PARAM(bytes)     byte_size_in_proper_unit((double)(bytes)), proper_unit_for_byte_size((bytes))

//
// Statistics gathered by the deduplication thread.
//
class G1StringDedupStat : public StackObj {
private:
  // Counters
  uintx  _inspected;
  uintx  _skipped;
  uintx  _hashed;
  uintx  _known;
  uintx  _new;
  uintx  _new_bytes;
  uintx  _deduped;
  uintx  _deduped_bytes;
  uintx  _deduped_young;
  uintx  _deduped_young_bytes;
  uintx  _deduped_old;
  uintx  _deduped_old_bytes;
  uintx  _idle;
  uintx  _exec;
  uintx  _block;

  // Time spent by the deduplication thread in different phases
  double _start;
  double _idle_elapsed;
  double _exec_elapsed;
  double _block_elapsed;

public:
  G1StringDedupStat();

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

  void inc_deduped_young(uintx bytes) {
    _deduped++;
    _deduped_bytes += bytes;
    _deduped_young++;
    _deduped_young_bytes += bytes;
  }

  void inc_deduped_old(uintx bytes) {
    _deduped++;
    _deduped_bytes += bytes;
    _deduped_old++;
    _deduped_old_bytes += bytes;
  }

  void mark_idle() {
    _start = os::elapsedTime();
    _idle++;
  }

  void mark_exec() {
    double now = os::elapsedTime();
    _idle_elapsed = now - _start;
    _start = now;
    _exec++;
  }

  void mark_block() {
    double now = os::elapsedTime();
    _exec_elapsed += now - _start;
    _start = now;
    _block++;
  }

  void mark_unblock() {
    double now = os::elapsedTime();
    _block_elapsed += now - _start;
    _start = now;
  }

  void mark_done() {
    double now = os::elapsedTime();
    _exec_elapsed += now - _start;
  }

  void add(const G1StringDedupStat& stat);

  static void print_summary(outputStream* st, const G1StringDedupStat& last_stat, const G1StringDedupStat& total_stat);
  static void print_statistics(outputStream* st, const G1StringDedupStat& stat, bool total);
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1STRINGDEDUPSTAT_HPP
