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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHCARDSTATS_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHCARDSTATS_HPP

#include "gc/shared/gc_globals.hpp"
#include "gc/shenandoah/shenandoahNumberSeq.hpp"

class ShenandoahCardStats: public CHeapObj<mtGC> {
private:
  size_t _cards_in_cluster;
  HdrSeq* _local_card_stats;

  bool _last_dirty;
  bool _last_clean;

  size_t _dirty_card_cnt;
  size_t _clean_card_cnt;

  size_t _dirty_run;
  size_t _clean_run;

  size_t _max_dirty_run;
  size_t _max_clean_run;

  size_t _dirty_obj_cnt;
  size_t _clean_obj_cnt;

  size_t _dirty_scan_cnt;
  size_t _clean_scan_cnt;

  size_t _alternation_cnt;

public:
  ShenandoahCardStats(size_t cards_in_cluster, HdrSeq* card_stats) :
    _cards_in_cluster(cards_in_cluster),
    _local_card_stats(card_stats),
    _last_dirty(false),
    _last_clean(false),
    _dirty_card_cnt(0),
    _clean_card_cnt(0),
    _dirty_run(0),
    _clean_run(0),
    _max_dirty_run(0),
    _max_clean_run(0),
    _dirty_obj_cnt(0),
    _clean_obj_cnt(0),
    _dirty_scan_cnt(0),
    _clean_scan_cnt(0),
    _alternation_cnt(0)
  { }

private:
  void increment_card_cnt_work(bool dirty) {
    if (dirty) { // dirty card
      if (_last_dirty) {
        assert(_dirty_run > 0 && _clean_run == 0 && !_last_clean, "Error");
        _dirty_run++;
      } else {
        if (_last_clean) {
          _alternation_cnt++;
        }
        update_run(false);
        _last_dirty = true;
        _dirty_run = 1;
      }
    } else { // clean card
      if (_last_clean) {
        assert(_clean_run > 0 && _dirty_run == 0 && !_last_dirty, "Error");
        _clean_run++;
      } else {
        if (_last_dirty) {
          _alternation_cnt++;
        }
        update_run(false);
        _last_clean = true;
        _clean_run = 1;
      }
    }
  }

  inline void increment_obj_cnt_work(bool dirty)  {
    assert(!dirty || (_last_dirty && _dirty_run > 0), "Error");
    assert(dirty  || (_last_clean && _clean_run > 0), "Error");
    dirty ? _dirty_obj_cnt++ : _clean_obj_cnt++;
  }

  inline void increment_scan_cnt_work(bool dirty) {
    assert(!dirty || (_last_dirty && _dirty_run > 0), "Error");
    assert(dirty  || (_last_clean && _clean_run > 0), "Error");
    dirty ? _dirty_scan_cnt++ : _clean_scan_cnt++;
  }

  void update_run_work(bool cluster) PRODUCT_RETURN;

public:
  inline void increment_card_cnt(bool dirty) {
    if (ShenandoahEnableCardStats) {
      increment_card_cnt_work(dirty);
    }
  }

  inline void increment_obj_cnt(bool dirty) {
    if (ShenandoahEnableCardStats) {
      increment_obj_cnt_work(dirty);
    }
  }

  inline void increment_scan_cnt(bool dirty) {
    if (ShenandoahEnableCardStats) {
      increment_scan_cnt_work(dirty);
    }
  }

  inline void update_run(bool record) {
    if (ShenandoahEnableCardStats) {
      update_run_work(record);
    }
  }

  bool is_clean() PRODUCT_RETURN0;

  void log() const PRODUCT_RETURN;
};

enum CardStatType {
  DIRTY_RUN = 0,
  CLEAN_RUN = 1,
  DIRTY_CARDS = 2,
  CLEAN_CARDS = 3,
  MAX_DIRTY_RUN = 4,
  MAX_CLEAN_RUN = 5,
  DIRTY_OBJS = 6,
  CLEAN_OBJS = 7,
  DIRTY_SCANS = 8,
  CLEAN_SCANS= 9,
  ALTERNATIONS = 10,
  MAX_CARD_STAT_TYPE = 11
};

enum CardStatLogType {
  CARD_STAT_SCAN_RS = 0,
  CARD_STAT_UPDATE_REFS = 1,
  MAX_CARD_STAT_LOG_TYPE = 2
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCARDSTATS_HPP
