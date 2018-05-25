/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018 SAP SE. All rights reserved.
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

#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaspaceCommon.hpp"
#include "memory/metaspace/metaspaceStatistics.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

namespace metaspace {

// FreeChunksStatistics methods

FreeChunksStatistics::FreeChunksStatistics()
: _num(0), _cap(0)
{}

void FreeChunksStatistics::reset() {
  _num = 0; _cap = 0;
}

void FreeChunksStatistics::add(uintx n, size_t s) {
  _num += n; _cap += s;
}

void FreeChunksStatistics::add(const FreeChunksStatistics& other) {
  _num += other._num;
  _cap += other._cap;
}

void FreeChunksStatistics::print_on(outputStream* st, size_t scale) const {
  st->print(UINTX_FORMAT, _num);
  st->print(" chunks, total capacity ");
  print_scaled_words(st, _cap, scale);
}

// ChunkManagerStatistics methods

void ChunkManagerStatistics::reset() {
  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    _chunk_stats[i].reset();
  }
}

size_t ChunkManagerStatistics::total_capacity() const {
  return _chunk_stats[SpecializedIndex].cap() +
      _chunk_stats[SmallIndex].cap() +
      _chunk_stats[MediumIndex].cap() +
      _chunk_stats[HumongousIndex].cap();
}

void ChunkManagerStatistics::print_on(outputStream* st, size_t scale) const {
  FreeChunksStatistics totals;
  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    st->cr();
    st->print("%12s chunks: ", chunk_size_name(i));
    if (_chunk_stats[i].num() > 0) {
      st->print(UINTX_FORMAT_W(4) ", capacity ", _chunk_stats[i].num());
      print_scaled_words(st, _chunk_stats[i].cap(), scale);
    } else {
      st->print("(none)");
    }
    totals.add(_chunk_stats[i]);
  }
  st->cr();
  st->print("%19s: " UINTX_FORMAT_W(4) ", capacity=", "Total", totals.num());
  print_scaled_words(st, totals.cap(), scale);
  st->cr();
}

// UsedChunksStatistics methods

UsedChunksStatistics::UsedChunksStatistics()
: _num(0), _cap(0), _used(0), _free(0), _waste(0), _overhead(0)
{}

void UsedChunksStatistics::reset() {
  _num = 0;
  _cap = _overhead = _used = _free = _waste = 0;
}

void UsedChunksStatistics::add(const UsedChunksStatistics& other) {
  _num += other._num;
  _cap += other._cap;
  _used += other._used;
  _free += other._free;
  _waste += other._waste;
  _overhead += other._overhead;
  DEBUG_ONLY(check_sanity());
}

void UsedChunksStatistics::print_on(outputStream* st, size_t scale) const {
  int col = st->position();
  st->print(UINTX_FORMAT_W(4) " chunk%s, ", _num, _num != 1 ? "s" : "");
  if (_num > 0) {
    col += 14; st->fill_to(col);

    print_scaled_words(st, _cap, scale, 5);
    st->print(" capacity, ");

    col += 18; st->fill_to(col);
    print_scaled_words_and_percentage(st, _used, _cap, scale, 5);
    st->print(" used, ");

    col += 20; st->fill_to(col);
    print_scaled_words_and_percentage(st, _free, _cap, scale, 5);
    st->print(" free, ");

    col += 20; st->fill_to(col);
    print_scaled_words_and_percentage(st, _waste, _cap, scale, 5);
    st->print(" waste, ");

    col += 20; st->fill_to(col);
    print_scaled_words_and_percentage(st, _overhead, _cap, scale, 5);
    st->print(" overhead");
  }
  DEBUG_ONLY(check_sanity());
}

#ifdef ASSERT
void UsedChunksStatistics::check_sanity() const {
  assert(_overhead == (Metachunk::overhead() * _num), "Sanity: Overhead.");
  assert(_cap == _used + _free + _waste + _overhead, "Sanity: Capacity.");
}
#endif

// SpaceManagerStatistics methods

SpaceManagerStatistics::SpaceManagerStatistics() { reset(); }

void SpaceManagerStatistics::reset() {
  for (int i = 0; i < NumberOfInUseLists; i ++) {
    _chunk_stats[i].reset();
    _free_blocks_num = 0; _free_blocks_cap_words = 0;
  }
}

void SpaceManagerStatistics::add_free_blocks_info(uintx num, size_t cap) {
  _free_blocks_num += num;
  _free_blocks_cap_words += cap;
}

void SpaceManagerStatistics::add(const SpaceManagerStatistics& other) {
  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    _chunk_stats[i].add(other._chunk_stats[i]);
  }
  _free_blocks_num += other._free_blocks_num;
  _free_blocks_cap_words += other._free_blocks_cap_words;
}

// Returns total chunk statistics over all chunk types.
UsedChunksStatistics SpaceManagerStatistics::totals() const {
  UsedChunksStatistics stat;
  for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
    stat.add(_chunk_stats[i]);
  }
  return stat;
}

void SpaceManagerStatistics::print_on(outputStream* st, size_t scale,  bool detailed) const {
  streamIndentor sti(st);
  if (detailed) {
    st->cr_indent();
    st->print("Usage by chunk type:");
    {
      streamIndentor sti2(st);
      for (ChunkIndex i = ZeroIndex; i < NumberOfInUseLists; i = next_chunk_index(i)) {
        st->cr_indent();
        st->print("%15s: ", chunk_size_name(i));
        if (_chunk_stats[i].num() == 0) {
          st->print(" (none)");
        } else {
          _chunk_stats[i].print_on(st, scale);
        }
      }

      st->cr_indent();
      st->print("%15s: ", "-total-");
      totals().print_on(st, scale);
    }
    if (_free_blocks_num > 0) {
      st->cr_indent();
      st->print("deallocated: " UINTX_FORMAT " blocks with ", _free_blocks_num);
      print_scaled_words(st, _free_blocks_cap_words, scale);
    }
  } else {
    totals().print_on(st, scale);
    st->print(", ");
    st->print("deallocated: " UINTX_FORMAT " blocks with ", _free_blocks_num);
    print_scaled_words(st, _free_blocks_cap_words, scale);
  }
}

// ClassLoaderMetaspaceStatistics methods

ClassLoaderMetaspaceStatistics::ClassLoaderMetaspaceStatistics() { reset(); }

void ClassLoaderMetaspaceStatistics::reset() {
  nonclass_sm_stats().reset();
  if (Metaspace::using_class_space()) {
    class_sm_stats().reset();
  }
}

// Returns total space manager statistics for both class and non-class metaspace
SpaceManagerStatistics ClassLoaderMetaspaceStatistics::totals() const {
  SpaceManagerStatistics stats;
  stats.add(nonclass_sm_stats());
  if (Metaspace::using_class_space()) {
    stats.add(class_sm_stats());
  }
  return stats;
}

void ClassLoaderMetaspaceStatistics::add(const ClassLoaderMetaspaceStatistics& other) {
  nonclass_sm_stats().add(other.nonclass_sm_stats());
  if (Metaspace::using_class_space()) {
    class_sm_stats().add(other.class_sm_stats());
  }
}

void ClassLoaderMetaspaceStatistics::print_on(outputStream* st, size_t scale, bool detailed) const {
  streamIndentor sti(st);
  st->cr_indent();
  if (Metaspace::using_class_space()) {
    st->print("Non-Class: ");
  }
  nonclass_sm_stats().print_on(st, scale, detailed);
  if (detailed) {
    st->cr();
  }
  if (Metaspace::using_class_space()) {
    st->cr_indent();
    st->print("    Class: ");
    class_sm_stats().print_on(st, scale, detailed);
    if (detailed) {
      st->cr();
    }
    st->cr_indent();
    st->print("     Both: ");
    totals().print_on(st, scale, detailed);
    if (detailed) {
      st->cr();
    }
  }
  st->cr();
}

} // end namespace metaspace



