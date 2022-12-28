/*
 * Copyright (c) 2022, Amazon.com, Inc. or its affiliates.  All rights reserved.
 *
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

#include "gc/shenandoah/shenandoahCardStats.hpp"
#include "logging/log.hpp"

#ifndef PRODUCT
void ShenandoahCardStats::update_run_work(bool record) {
  assert(!(_last_dirty || _last_clean) || (_last_dirty && _dirty_run > 0) || (_last_clean && _clean_run > 0),
         "dirty/clean run stats inconsistent");
  assert(_dirty_run == 0 || _clean_run == 0, "Both shouldn't be non-zero");
  if (_dirty_run > _max_dirty_run) {
    assert(_last_dirty, "Error");
    _max_dirty_run = _dirty_run;
  } else if (_clean_run > _max_clean_run) {
    assert(_last_clean, "Error");
    _max_clean_run = _clean_run;
  }
  _dirty_card_cnt += _dirty_run;
  _clean_card_cnt += _clean_run;

  // Update local stats
  {
    assert(_dirty_run <= _cards_in_cluster, "Error");
    assert(_clean_run <= _cards_in_cluster, "Error");
    // Update global stats for distribution of dirty/clean run lengths
    _local_card_stats[DIRTY_RUN].add((double)_dirty_run*100/(double)_cards_in_cluster);
    _local_card_stats[CLEAN_RUN].add((double)_clean_run*100/(double)_cards_in_cluster);

    if (record) {
      // Update global stats for distribution of dirty/clean cards as a percentage of chunk
      _local_card_stats[DIRTY_CARDS].add((double)_dirty_card_cnt*100/(double)_cards_in_cluster);
      _local_card_stats[CLEAN_CARDS].add((double)_clean_card_cnt*100/(double)_cards_in_cluster);

      // Update global stats for max dirty/clean run distribution as a percentage of chunk
      _local_card_stats[MAX_DIRTY_RUN].add((double)_max_dirty_run*100/(double)_cards_in_cluster);
      _local_card_stats[MAX_CLEAN_RUN].add((double)_max_clean_run*100/(double)_cards_in_cluster);

      // Update global stats for dirty & clean object counts
      _local_card_stats[DIRTY_OBJS].add(_dirty_obj_cnt);
      _local_card_stats[CLEAN_OBJS].add(_clean_obj_cnt);
      _local_card_stats[DIRTY_SCANS].add(_dirty_scan_cnt);
      _local_card_stats[CLEAN_SCANS].add(_clean_scan_cnt);

      _local_card_stats[ALTERNATIONS].add(_alternation_cnt);
    }
  }

  if (record) {
    // reset the stats for the next cluster
    _dirty_card_cnt = 0;
    _clean_card_cnt = 0;

    _max_dirty_run = 0;
    _max_clean_run = 0;

    _dirty_obj_cnt = 0;
    _clean_obj_cnt = 0;

    _dirty_scan_cnt = 0;
    _clean_scan_cnt = 0;

    _alternation_cnt = 0;
  }
  _dirty_run = 0;
  _clean_run = 0;
  _last_dirty = false;
  _last_clean = false;
  assert(!record || is_clean(), "Error");
}

bool ShenandoahCardStats::is_clean() {
  return
    _dirty_card_cnt == 0 &&
    _clean_card_cnt == 0 &&
    _max_dirty_run == 0 &&
    _max_clean_run == 0 &&
    _dirty_obj_cnt == 0 &&
    _clean_obj_cnt == 0 &&
    _dirty_scan_cnt == 0 &&
    _clean_scan_cnt == 0 &&
    _alternation_cnt == 0 &&
    _dirty_run == 0 &&
    _clean_run == 0 &&
    _last_dirty == false &&
    _last_clean == false;
}

void ShenandoahCardStats::log() const {
  if (ShenandoahEnableCardStats) {
    log_info(gc,remset)("Card stats: dirty " SIZE_FORMAT " (max run: " SIZE_FORMAT "),"
      " clean " SIZE_FORMAT " (max run: " SIZE_FORMAT "),"
      " dirty objs " SIZE_FORMAT ", clean objs " SIZE_FORMAT ","
      " dirty scans " SIZE_FORMAT ", clean scans " SIZE_FORMAT,
      _dirty_card_cnt, _max_dirty_run, _clean_card_cnt, _max_clean_run,
      _dirty_obj_cnt, _clean_obj_cnt,
      _dirty_scan_cnt, _clean_scan_cnt);
  }
}
#endif // !PRODUCT

