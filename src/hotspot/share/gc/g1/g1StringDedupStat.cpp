/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1StringDedupStat.hpp"
#include "logging/log.hpp"

G1StringDedupStat::G1StringDedupStat() : StringDedupStat(),
  _deduped_young(0),
  _deduped_young_bytes(0),
  _deduped_old(0),
  _deduped_old_bytes(0),
  _heap(G1CollectedHeap::heap()) {
}



void G1StringDedupStat::deduped(oop obj, uintx bytes) {
  StringDedupStat::deduped(obj, bytes);
  if (_heap->is_in_young(obj)) {
    _deduped_young ++;
    _deduped_young_bytes += bytes;
  } else {
    _deduped_old ++;
    _deduped_old_bytes += bytes;
  }
}

void G1StringDedupStat::add(const StringDedupStat* const stat) {
  StringDedupStat::add(stat);
  const G1StringDedupStat* const g1_stat = (const G1StringDedupStat* const)stat;
  _deduped_young += g1_stat->_deduped_young;
  _deduped_young_bytes += g1_stat->_deduped_young_bytes;
  _deduped_old += g1_stat->_deduped_old;
  _deduped_old_bytes += g1_stat->_deduped_old_bytes;
}

void G1StringDedupStat::print_statistics(bool total) const {
  StringDedupStat::print_statistics(total);

  double deduped_young_percent       = percent_of(_deduped_young, _deduped);
  double deduped_young_bytes_percent = percent_of(_deduped_young_bytes, _deduped_bytes);
  double deduped_old_percent         = percent_of(_deduped_old, _deduped);
  double deduped_old_bytes_percent   = percent_of(_deduped_old_bytes, _deduped_bytes);

  log_debug(gc, stringdedup)("      Young:      " STRDEDUP_OBJECTS_FORMAT "(" STRDEDUP_PERCENT_FORMAT ") " STRDEDUP_BYTES_FORMAT "(" STRDEDUP_PERCENT_FORMAT ")",
                             _deduped_young, deduped_young_percent, STRDEDUP_BYTES_PARAM(_deduped_young_bytes), deduped_young_bytes_percent);
  log_debug(gc, stringdedup)("      Old:        " STRDEDUP_OBJECTS_FORMAT "(" STRDEDUP_PERCENT_FORMAT ") " STRDEDUP_BYTES_FORMAT "(" STRDEDUP_PERCENT_FORMAT ")",
                             _deduped_old, deduped_old_percent, STRDEDUP_BYTES_PARAM(_deduped_old_bytes), deduped_old_bytes_percent);

}

void G1StringDedupStat::reset() {
  StringDedupStat::reset();
  _deduped_young = 0;
  _deduped_young_bytes = 0;
  _deduped_old = 0;
  _deduped_old_bytes = 0;
}
