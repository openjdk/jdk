/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

// Forward decl
class ConcurrentG1RefineThread;
class G1RemSet;

class ConcurrentG1Refine: public CHeapObj {
  ConcurrentG1RefineThread** _threads;
  int _n_threads;
  // The cache for card refinement.
  bool     _use_cache;
  bool     _def_use_cache;
  size_t _n_periods;
  size_t _total_cards;
  size_t _total_travs;

  unsigned char* _card_counts;
  unsigned       _n_card_counts;
  const jbyte*   _ct_bot;
  unsigned*      _cur_card_count_histo;
  unsigned*      _cum_card_count_histo;

  jbyte**      _hot_cache;
  int          _hot_cache_size;
  int          _n_hot;
  int          _hot_cache_idx;

  int          _hot_cache_par_chunk_size;
  volatile int _hot_cache_par_claimed_idx;

  // Returns the count of this card after incrementing it.
  int add_card_count(jbyte* card_ptr);

  void print_card_count_histo_range(unsigned* histo, int from, int to,
                                    float& cum_card_pct,
                                    float& cum_travs_pct);
 public:
  ConcurrentG1Refine();
  ~ConcurrentG1Refine();

  void init(); // Accomplish some initialization that has to wait.
  void stop();

  // Iterate over the conc refine threads
  void threads_do(ThreadClosure *tc);

  // If this is the first entry for the slot, writes into the cache and
  // returns NULL.  If it causes an eviction, returns the evicted pointer.
  // Otherwise, its a cache hit, and returns NULL.
  jbyte* cache_insert(jbyte* card_ptr);

  // Process the cached entries.
  void clean_up_cache(int worker_i, G1RemSet* g1rs);

  // Set up for parallel processing of the cards in the hot cache
  void clear_hot_cache_claimed_index() {
    _hot_cache_par_claimed_idx = 0;
  }

  // Discard entries in the hot cache.
  void clear_hot_cache() {
    _hot_cache_idx = 0; _n_hot = 0;
  }

  bool hot_cache_is_empty() { return _n_hot == 0; }

  bool use_cache() { return _use_cache; }
  void set_use_cache(bool b) {
    if (b) _use_cache = _def_use_cache;
    else   _use_cache = false;
  }

  void clear_and_record_card_counts();
  void print_final_card_counts();

  static size_t thread_num();
};
