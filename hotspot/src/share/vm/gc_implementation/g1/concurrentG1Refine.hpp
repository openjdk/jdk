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

// Forward decl
class ConcurrentG1RefineThread;
class G1RemSet;

// What to do after a yield:
enum PostYieldAction {
  PYA_continue,  // Continue the traversal
  PYA_restart,   // Restart
  PYA_cancel     // It's been completed by somebody else: cancel.
};

class ConcurrentG1Refine: public CHeapObj {
  ConcurrentG1RefineThread* _cg1rThread;

  volatile jint _pya;
  PostYieldAction _last_pya;

  static bool _enabled;  // Protected by G1ConcRefine_mon.
  unsigned _traversals;

  // Number of cards processed during last refinement traversal.
  unsigned _first_traversal;
  unsigned _last_cards_during;

  // The cache for card refinement.
  bool     _use_cache;
  bool     _def_use_cache;
  size_t _n_periods;
  size_t _total_cards;
  size_t _total_travs;

  unsigned char*  _card_counts;
  unsigned _n_card_counts;
  const jbyte* _ct_bot;
  unsigned* _cur_card_count_histo;
  unsigned* _cum_card_count_histo;
  jbyte**  _hot_cache;
  int      _hot_cache_size;
  int      _n_hot;
  int      _hot_cache_idx;

  // Returns the count of this card after incrementing it.
  int add_card_count(jbyte* card_ptr);

  void print_card_count_histo_range(unsigned* histo, int from, int to,
                                    float& cum_card_pct,
                                    float& cum_travs_pct);
 public:
  ConcurrentG1Refine();
  ~ConcurrentG1Refine();

  void init(); // Accomplish some initialization that has to wait.

  // Enabled Conc refinement, waking up thread if necessary.
  void enable();

  // Returns the number of traversals performed since this refiner was enabled.
  unsigned disable();

  // Requires G1ConcRefine_mon to be held.
  bool enabled() { return _enabled; }

  // Returns only when G1 concurrent refinement has been enabled.
  void wait_for_ConcurrentG1Refine_enabled();

  // Do one concurrent refinement pass over the card table.  Returns "true"
  // if heuristics determine that another pass should be done immediately.
  bool refine();

  // Indicate that an in-progress refinement pass should start over.
  void set_pya_restart();
  // Indicate that an in-progress refinement pass should quit.
  void set_pya_cancel();

  // Get the appropriate post-yield action.  Also sets last_pya.
  PostYieldAction get_pya();

  // The last PYA read by "get_pya".
  PostYieldAction get_last_pya();

  bool do_traversal();

  ConcurrentG1RefineThread* cg1rThread() { return _cg1rThread; }

  // If this is the first entry for the slot, writes into the cache and
  // returns NULL.  If it causes an eviction, returns the evicted pointer.
  // Otherwise, its a cache hit, and returns NULL.
  jbyte* cache_insert(jbyte* card_ptr);

  // Process the cached entries.
  void clean_up_cache(int worker_i, G1RemSet* g1rs);

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
};
