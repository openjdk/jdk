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

class ShenandoahHeapRegion;
class ShenandoahMarkingContext;

class ShenandoahForwardingTable {

  struct Entry {
    HeapWord* _original;
    HeapWord* _forwardee;
  public:
    Entry(HeapWord* original, HeapWord* forwardee) : _original(original), _forwardee(forwardee) {}

    HeapWord* original() const { return _original; }
    HeapWord* forwardee() const { return _forwardee; }
    bool is_marked(ShenandoahMarkingContext* ctx) const;
  };

  ShenandoahHeapRegion* const _region;
  Entry* _table;
  size_t _num_entries;

  void initialize(size_t num_forwardings);
  void clear();
  uint64_t hash(HeapWord* original, Entry* table);
  void enter_forwarding(HeapWord* original, HeapWord* forwardee);
  void fill_forwardings();
  void verify_forwardings() PRODUCT_RETURN;

public:
  ShenandoahForwardingTable(ShenandoahHeapRegion* region) : _region(region), _table(nullptr), _num_entries(0) {}

  void build(size_t num_forwardings);

  HeapWord* forwardee(HeapWord* orginal) const;

  void zap_region();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDINGTABLE_HPP
