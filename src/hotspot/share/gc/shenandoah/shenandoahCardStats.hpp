/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

enum CardStatType {
  DIRTY_RUN,
  CLEAN_RUN,
  DIRTY_CARDS,
  CLEAN_CARDS,
  MAX_DIRTY_RUN,
  MAX_CLEAN_RUN,
  DIRTY_SCAN_OBJS,
  ALTERNATIONS,
  MAX_CARD_STAT_TYPE
};

enum CardStatLogType {
  CARD_STAT_SCAN_RS,
  CARD_STAT_UPDATE_REFS,
  MAX_CARD_STAT_LOG_TYPE
};

class ShenandoahCardStats: public CHeapObj<mtGC> {
private:
  size_t _cards_in_cluster;
  HdrSeq* _local_card_stats;

  size_t _dirty_card_cnt;
  size_t _clean_card_cnt;

  size_t _max_dirty_run;
  size_t _max_clean_run;

  size_t _dirty_scan_obj_cnt;

  size_t _alternation_cnt;

public:
  ShenandoahCardStats(size_t cards_in_cluster, HdrSeq* card_stats) :
    _cards_in_cluster(cards_in_cluster),
    _local_card_stats(card_stats),
    _dirty_card_cnt(0),
    _clean_card_cnt(0),
    _max_dirty_run(0),
    _max_clean_run(0),
    _dirty_scan_obj_cnt(0),
    _alternation_cnt(0)
  { }

  ~ShenandoahCardStats() {
    record();
   }

   void record() {
    if (ShenandoahEnableCardStats) {
      // Update global stats for distribution of dirty/clean cards as a percentage of chunk
      _local_card_stats[DIRTY_CARDS].add(percent_of(_dirty_card_cnt, _cards_in_cluster));
      _local_card_stats[CLEAN_CARDS].add(percent_of(_clean_card_cnt, _cards_in_cluster));

      // Update global stats for max dirty/clean run distribution as a percentage of chunk
      _local_card_stats[MAX_DIRTY_RUN].add(percent_of(_max_dirty_run, _cards_in_cluster));
      _local_card_stats[MAX_CLEAN_RUN].add(percent_of(_max_clean_run, _cards_in_cluster));

      // Update global stats for dirty obj scan counts
      _local_card_stats[DIRTY_SCAN_OBJS].add(_dirty_scan_obj_cnt);

      // Update global stats for alternation counts
      _local_card_stats[ALTERNATIONS].add(_alternation_cnt);
    }
  }

public:
  inline void record_dirty_run(size_t len) {
    if (ShenandoahEnableCardStats) {
      _alternation_cnt++;
      if (len > _max_dirty_run) {
        _max_dirty_run = len;
      }
      _dirty_card_cnt += len;
      assert(len <= _cards_in_cluster, "Error");
      _local_card_stats[DIRTY_RUN].add(percent_of(len, _cards_in_cluster));
    }
  }

  inline void record_clean_run(size_t len) {
    if (ShenandoahEnableCardStats) {
      _alternation_cnt++;
      if (len > _max_clean_run) {
        _max_clean_run = len;
      }
      _clean_card_cnt += len;
      assert(len <= _cards_in_cluster, "Error");
      _local_card_stats[CLEAN_RUN].add(percent_of(len, _cards_in_cluster));
    }
  }

  inline void record_scan_obj_cnt(size_t i) {
    if (ShenandoahEnableCardStats) {
      _dirty_scan_obj_cnt += i;
    }
  }

  void log() const PRODUCT_RETURN;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCARDSTATS_HPP
