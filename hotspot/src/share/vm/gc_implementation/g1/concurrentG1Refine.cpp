/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_concurrentG1Refine.cpp.incl"

ConcurrentG1Refine::ConcurrentG1Refine() :
  _card_counts(NULL), _cur_card_count_histo(NULL), _cum_card_count_histo(NULL),
  _hot_cache(NULL),
  _def_use_cache(false), _use_cache(false),
  _n_periods(0), _total_cards(0), _total_travs(0),
  _threads(NULL), _n_threads(0)
{
  if (G1ConcRefine) {
    _n_threads = (int)thread_num();
    if (_n_threads > 0) {
      _threads = NEW_C_HEAP_ARRAY(ConcurrentG1RefineThread*, _n_threads);
      int worker_id_offset = (int)DirtyCardQueueSet::num_par_ids();
      ConcurrentG1RefineThread *next = NULL;
      for (int i = _n_threads - 1; i >= 0; i--) {
        ConcurrentG1RefineThread* t = new ConcurrentG1RefineThread(this, next, worker_id_offset, i);
        assert(t != NULL, "Conc refine should have been created");
        assert(t->cg1r() == this, "Conc refine thread should refer to this");
        _threads[i] = t;
        next = t;
      }
    }
  }
}

size_t ConcurrentG1Refine::thread_num() {
  if (G1ConcRefine) {
    return (G1ParallelRSetThreads > 0) ? G1ParallelRSetThreads : ParallelGCThreads;
  }
  return 0;
}

void ConcurrentG1Refine::init() {
  if (G1ConcRSLogCacheSize > 0 || G1ConcRSCountTraversals) {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    _n_card_counts =
      (unsigned) (g1h->g1_reserved_obj_bytes() >> CardTableModRefBS::card_shift);
    _card_counts = NEW_C_HEAP_ARRAY(unsigned char, _n_card_counts);
    for (size_t i = 0; i < _n_card_counts; i++) _card_counts[i] = 0;
    ModRefBarrierSet* bs = g1h->mr_bs();
    guarantee(bs->is_a(BarrierSet::CardTableModRef), "Precondition");
    CardTableModRefBS* ctbs = (CardTableModRefBS*)bs;
    _ct_bot = ctbs->byte_for_const(g1h->reserved_region().start());
    if (G1ConcRSCountTraversals) {
      _cur_card_count_histo = NEW_C_HEAP_ARRAY(unsigned, 256);
      _cum_card_count_histo = NEW_C_HEAP_ARRAY(unsigned, 256);
      for (int i = 0; i < 256; i++) {
        _cur_card_count_histo[i] = 0;
        _cum_card_count_histo[i] = 0;
      }
    }
  }
  if (G1ConcRSLogCacheSize > 0) {
    _def_use_cache = true;
    _use_cache = true;
    _hot_cache_size = (1 << G1ConcRSLogCacheSize);
    _hot_cache = NEW_C_HEAP_ARRAY(jbyte*, _hot_cache_size);
    _n_hot = 0;
    _hot_cache_idx = 0;
  }
}

void ConcurrentG1Refine::stop() {
  if (_threads != NULL) {
    for (int i = 0; i < _n_threads; i++) {
      _threads[i]->stop();
    }
  }
}

ConcurrentG1Refine::~ConcurrentG1Refine() {
  if (G1ConcRSLogCacheSize > 0 || G1ConcRSCountTraversals) {
    assert(_card_counts != NULL, "Logic");
    FREE_C_HEAP_ARRAY(unsigned char, _card_counts);
    assert(_cur_card_count_histo != NULL, "Logic");
    FREE_C_HEAP_ARRAY(unsigned, _cur_card_count_histo);
    assert(_cum_card_count_histo != NULL, "Logic");
    FREE_C_HEAP_ARRAY(unsigned, _cum_card_count_histo);
  }
  if (G1ConcRSLogCacheSize > 0) {
    assert(_hot_cache != NULL, "Logic");
    FREE_C_HEAP_ARRAY(jbyte*, _hot_cache);
  }
  if (_threads != NULL) {
    for (int i = 0; i < _n_threads; i++) {
      delete _threads[i];
    }
    FREE_C_HEAP_ARRAY(ConcurrentG1RefineThread*, _threads);
  }
}

void ConcurrentG1Refine::threads_do(ThreadClosure *tc) {
  if (_threads != NULL) {
    for (int i = 0; i < _n_threads; i++) {
      tc->do_thread(_threads[i]);
    }
  }
}


int ConcurrentG1Refine::add_card_count(jbyte* card_ptr) {
  size_t card_num = (card_ptr - _ct_bot);
  guarantee(0 <= card_num && card_num < _n_card_counts, "Bounds");
  unsigned char cnt = _card_counts[card_num];
  if (cnt < 255) _card_counts[card_num]++;
  return cnt;
  _total_travs++;
}

jbyte* ConcurrentG1Refine::cache_insert(jbyte* card_ptr) {
  int count = add_card_count(card_ptr);
  // Count previously unvisited cards.
  if (count == 0) _total_cards++;
  // We'll assume a traversal unless we store it in the cache.
  if (count < G1ConcRSHotCardLimit) {
    _total_travs++;
    return card_ptr;
  }
  // Otherwise, it's hot.
  jbyte* res = NULL;
  MutexLockerEx x(HotCardCache_lock, Mutex::_no_safepoint_check_flag);
  if (_n_hot == _hot_cache_size) {
    _total_travs++;
    res = _hot_cache[_hot_cache_idx];
    _n_hot--;
  }
  // Now _n_hot < _hot_cache_size, and we can insert at _hot_cache_idx.
  _hot_cache[_hot_cache_idx] = card_ptr;
  _hot_cache_idx++;
  if (_hot_cache_idx == _hot_cache_size) _hot_cache_idx = 0;
  _n_hot++;
  return res;
}


void ConcurrentG1Refine::clean_up_cache(int worker_i, G1RemSet* g1rs) {
  assert(!use_cache(), "cache should be disabled");
  int start_ind = _hot_cache_idx-1;
  for (int i = 0; i < _n_hot; i++) {
    int ind = start_ind - i;
    if (ind < 0) ind = ind + _hot_cache_size;
    jbyte* entry = _hot_cache[ind];
    if (entry != NULL) {
      g1rs->concurrentRefineOneCard(entry, worker_i);
    }
  }
  _n_hot = 0;
  _hot_cache_idx = 0;
}

void ConcurrentG1Refine::clear_and_record_card_counts() {
  if (G1ConcRSLogCacheSize == 0 && !G1ConcRSCountTraversals) return;
  _n_periods++;
  if (G1ConcRSCountTraversals) {
    for (size_t i = 0; i < _n_card_counts; i++) {
      unsigned char bucket = _card_counts[i];
      _cur_card_count_histo[bucket]++;
      _card_counts[i] = 0;
    }
    gclog_or_tty->print_cr("Card counts:");
    for (int i = 0; i < 256; i++) {
      if (_cur_card_count_histo[i] > 0) {
        gclog_or_tty->print_cr("  %3d: %9d", i, _cur_card_count_histo[i]);
        _cum_card_count_histo[i] += _cur_card_count_histo[i];
        _cur_card_count_histo[i] = 0;
      }
    }
  } else {
    assert(G1ConcRSLogCacheSize > 0, "Logic");
    Copy::fill_to_words((HeapWord*)(&_card_counts[0]),
                        _n_card_counts / HeapWordSize);
  }
}

void
ConcurrentG1Refine::
print_card_count_histo_range(unsigned* histo, int from, int to,
                             float& cum_card_pct,
                             float& cum_travs_pct) {
  unsigned cards = 0;
  unsigned travs = 0;
  guarantee(to <= 256, "Precondition");
  for (int i = from; i < to-1; i++) {
    cards += histo[i];
    travs += histo[i] * i;
  }
  if (to == 256) {
    unsigned histo_card_sum = 0;
    unsigned histo_trav_sum = 0;
    for (int i = 1; i < 255; i++) {
      histo_trav_sum += histo[i] * i;
    }
    cards += histo[255];
    // correct traversals for the last one.
    unsigned travs_255 = (unsigned) (_total_travs - histo_trav_sum);
    travs += travs_255;

  } else {
    cards += histo[to-1];
    travs += histo[to-1] * (to-1);
  }
  float fperiods = (float)_n_periods;
  float f_tot_cards = (float)_total_cards/fperiods;
  float f_tot_travs = (float)_total_travs/fperiods;
  if (cards > 0) {
    float fcards = (float)cards/fperiods;
    float ftravs = (float)travs/fperiods;
    if (to == 256) {
      gclog_or_tty->print(" %4d-       %10.2f%10.2f", from, fcards, ftravs);
    } else {
      gclog_or_tty->print(" %4d-%4d   %10.2f%10.2f", from, to-1, fcards, ftravs);
    }
    float pct_cards = fcards*100.0/f_tot_cards;
    cum_card_pct += pct_cards;
    float pct_travs = ftravs*100.0/f_tot_travs;
    cum_travs_pct += pct_travs;
    gclog_or_tty->print_cr("%10.2f%10.2f%10.2f%10.2f",
                  pct_cards, cum_card_pct,
                  pct_travs, cum_travs_pct);
  }
}

void ConcurrentG1Refine::print_final_card_counts() {
  if (!G1ConcRSCountTraversals) return;

  gclog_or_tty->print_cr("Did %d total traversals of %d distinct cards.",
                _total_travs, _total_cards);
  float fperiods = (float)_n_periods;
  gclog_or_tty->print_cr("  This is an average of %8.2f traversals, %8.2f cards, "
                "per collection.", (float)_total_travs/fperiods,
                (float)_total_cards/fperiods);
  gclog_or_tty->print_cr("  This is an average of %8.2f traversals/distinct "
                "dirty card.\n",
                _total_cards > 0 ?
                (float)_total_travs/(float)_total_cards : 0.0);


  gclog_or_tty->print_cr("Histogram:\n\n%10s   %10s%10s%10s%10s%10s%10s",
                "range", "# cards", "# travs", "% cards", "(cum)",
                "% travs", "(cum)");
  gclog_or_tty->print_cr("------------------------------------------------------------"
                "-------------");
  float cum_cards_pct = 0.0;
  float cum_travs_pct = 0.0;
  for (int i = 1; i < 10; i++) {
    print_card_count_histo_range(_cum_card_count_histo, i, i+1,
                                 cum_cards_pct, cum_travs_pct);
  }
  for (int i = 10; i < 100; i += 10) {
    print_card_count_histo_range(_cum_card_count_histo, i, i+10,
                                 cum_cards_pct, cum_travs_pct);
  }
  print_card_count_histo_range(_cum_card_count_histo, 100, 150,
                               cum_cards_pct, cum_travs_pct);
  print_card_count_histo_range(_cum_card_count_histo, 150, 200,
                               cum_cards_pct, cum_travs_pct);
  print_card_count_histo_range(_cum_card_count_histo, 150, 255,
                               cum_cards_pct, cum_travs_pct);
  print_card_count_histo_range(_cum_card_count_histo, 255, 256,
                               cum_cards_pct, cum_travs_pct);
}
