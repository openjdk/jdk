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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHCARDTABLE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHCARDTABLE_HPP

#include "gc/shared/cardTable.hpp"
#include "memory/virtualspace.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/macros.hpp"

#define ShenandoahMinCardSizeInBytes 128

class ShenandoahCardTable: public CardTable {
  friend class VMStructs;

private:
  // We maintain two copies of the card table to facilitate concurrent remembered set scanning
  // and concurrent clearing of stale remembered set information.  During the init_mark safepoint,
  // we copy the contents of _write_byte_map to _read_byte_map and clear _write_byte_map.
  //
  // Concurrent remembered set scanning reads from _read_byte_map while concurrent mutator write
  // barriers are overwriting cards of the _write_byte_map with DIRTY codes.  Concurrent remembered
  // set scanning also overwrites cards of the _write_byte_map with DIRTY codes whenever it discovers
  // interesting pointers.
  //
  // During a concurrent update-references phase, we scan the _write_byte_map concurrently to find
  // all old-gen references that may need to be updated.
  //
  // In a future implementation, we may swap the values of _read_byte_map and _write_byte_map during
  // the init-mark safepoint to avoid the need for bulk STW copying and initialization.  Doing so
  // requires a change to the implementation of mutator write barriers as the address of the card
  // table is currently in-lined and hard-coded.
  CardValue* _read_byte_map;
  CardValue* _write_byte_map;
  CardValue* _read_byte_map_base;
  CardValue* _write_byte_map_base;

public:
  explicit ShenandoahCardTable(MemRegion whole_heap) : CardTable(whole_heap),
    _read_byte_map(nullptr), _write_byte_map(nullptr),
    _read_byte_map_base(nullptr), _write_byte_map_base(nullptr) {}

  void initialize();

  bool is_in_young(const void* obj) const override;

  CardValue* read_byte_for(const void* p);

  size_t last_valid_index();

  CardValue* swap_read_and_write_tables() {
    swap(_read_byte_map, _write_byte_map);
    swap(_read_byte_map_base, _write_byte_map_base);

    _byte_map = _write_byte_map;
    _byte_map_base = _write_byte_map_base;

    return _byte_map_base;
  }

  CardValue* read_byte_map() {
    return _read_byte_map;
  }

  CardValue* read_byte_map_base() {
    return _read_byte_map_base;
  }

  CardValue* write_byte_map() {
    return _write_byte_map;
  }

  CardValue* write_byte_map_base() {
    return _write_byte_map_base;
  }

private:
  void initialize(const ReservedSpace& card_table);
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCARDTABLE_HPP
