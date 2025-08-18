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
 */

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDINGTABLE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDINGTABLE_HPP

#include "utilities/globalDefinitions.hpp"

class ShenandoahHeapRegion;
class ShenandoahMarkingContext;

class FwdTableEntry {
  HeapWord* const _original;
  HeapWord* const _forwardee;
public:
  FwdTableEntry(HeapWord* region_base, HeapWord* original, HeapWord* forwardee) : _original(original), _forwardee(forwardee) {}

  HeapWord* original(HeapWord* region_base) const { return _original; }
  HeapWord* forwardee() const { return _forwardee; }
  bool is_marked(ShenandoahMarkingContext* ctx) const;
  bool is_used() const { return _original != nullptr; }
  bool is_original(HeapWord* region_base, HeapWord* original);
};

class CompactFwdTableEntry {
  static const uint64_t ENTRY_MARKER = uint64_t(1) << 63;
  static const uint64_t ORIGINAL_BITS = 22; // Enough to encode 32M regions.
  static const uint64_t FORWARDEE_BITS = 63 - ORIGINAL_BITS; // Spare the uppermost bit to identify an entry

  static const int FORWARDEE_SHIFT = 0;
  static const int ORIGINAL_SHIFT = FORWARDEE_SHIFT + FORWARDEE_BITS;

  static const uint64_t ORIGINAL_MASK = right_n_bits(ORIGINAL_BITS) << ORIGINAL_SHIFT;
  static const uint64_t FORWARDEE_MASK = right_n_bits(FORWARDEE_BITS) << FORWARDEE_SHIFT;

  static HeapWord* _heap_base;
  uint64_t const _encoded;

  static uint64_t encode(HeapWord* region_base, HeapWord* original, HeapWord* forwardee);

  static HeapWord* decode_original(HeapWord* region_base, uint64_t encoded);
  static HeapWord* decode_forwardee(uint64_t encoded);

public:
  CompactFwdTableEntry(HeapWord* region_base, HeapWord* original, HeapWord* forwardee) : _encoded(encode(region_base, original, forwardee)) {}

  static constexpr size_t max_region_size_words() {
    return size_t(1) << ORIGINAL_BITS;
  }

  static constexpr size_t max_heap_size_words() {
    // We can't encode the last word, because of the way we setup heap-base. See below.
    return (size_t(1) << FORWARDEE_BITS) - 1;
  }

  static void set_heap_base(HeapWord* heap_base) {
    // Intentionally assume heap-base is one word lower. This way we
    // can never get a valid encoding of 0. We want to use 0 as 'unused'.
    _heap_base = heap_base - 1;
  }

  HeapWord* original(HeapWord* region_base) const { return decode_original(region_base, _encoded); }
  HeapWord* forwardee() const { return decode_forwardee(_encoded); }
  bool is_marked(ShenandoahMarkingContext* ctx) const;
  bool is_used() const { return _encoded != 0; }
  bool is_original(HeapWord* region_base, HeapWord* original);
};

class ShenandoahForwardingTable {
  static bool _compact;

  ShenandoahHeapRegion* const _region;
  void* _table;
  size_t _num_entries;
  size_t _num_expected_forwardings;
  size_t _num_actual_forwardings;
  size_t _num_live_words;

  template<class Entry>
  bool build(size_t num_forwardings);

  template<class Entry>
  bool initialize(size_t num_forwardings);

  template<class Entry>
  void clear();

  static uint64_t hash(HeapWord* original, void* table);

  template<class Entry>
  void enter_forwarding(HeapWord* original, HeapWord* forwardee);

  template<class Entry>
  void fill_forwardings();

  template<class Entry>
  void log_stats() const;

  template<class Entry>
  void verify_forwardings() PRODUCT_RETURN;

public:
  ShenandoahForwardingTable(ShenandoahHeapRegion* region) :
    _region(region), _table(nullptr), _num_entries(0),
    _num_expected_forwardings(0),
    _num_actual_forwardings(0),
    _num_live_words(0) {}

  static bool use_compact() { return _compact; }
  static void initialize_globals();

  bool build(size_t num_forwardings);

  template<class Entry>
  HeapWord* forwardee(HeapWord* orginal) const;

  void zap_region();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDINGTABLE_HPP
