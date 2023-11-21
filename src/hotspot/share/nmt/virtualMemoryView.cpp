/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "memory/metaspaceStats.hpp"
#include "memory/metaspaceUtils.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/threadStackTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "nmt/virtualMemoryView.hpp"
#include "runtime/os.hpp"
#include "runtime/threadCritical.hpp"
#include "utilities/ostream.hpp"

uint32_t VirtualMemoryView::PhysicalMemorySpace::unique_id = 0;
VirtualMemoryView::RegionStorage* VirtualMemoryView::_reserved_regions = nullptr;
GrowableArrayCHeap<const char*, mtNMT>* VirtualMemoryView::_names = nullptr;
GrowableArrayCHeap<VirtualMemoryView::OffsetRegionStorage, mtNMT>* VirtualMemoryView::_mapped_regions = nullptr;
GrowableArrayCHeap<VirtualMemoryView::RegionStorage, mtNMT>* VirtualMemoryView::_committed_regions = nullptr;
NativeCallStackStorage<VirtualMemoryView::IndexIterator>* VirtualMemoryView::_stack_storage = nullptr;
bool VirtualMemoryView::_is_detailed_mode = false;

void VirtualMemoryView::report(outputStream* output, size_t scale) {
  auto print_mapped_virtual_memory_region = [&](TrackedOffsetRange& mapped_range) -> void {
    output->print("\n\t");
    const NativeCallStack& stack = _stack_storage->get(mapped_range.stack_idx);
    const char* scale_name = NMTUtil::scale_name(scale);
    output->print("[" PTR_FORMAT " - " PTR_FORMAT "]" " of size " SIZE_FORMAT "%s for %s",
                  p2i(mapped_range.start), p2i(mapped_range.end()),
                  NMTUtil::amount_in_scale(mapped_range.size, scale), scale_name,
                  NMTUtil::flag_to_name(mapped_range.flag));
    if (mapped_range.start != mapped_range.physical_address) {
      output->print(" mapped to [" PTR_FORMAT " - " PTR_FORMAT "]", p2i(mapped_range.physical_address), p2i(mapped_range.physical_end()));
    } else {
      // Do nothing
    }
    if (stack.is_empty()) {
      output->print_cr(" ");
    } else {
      output->print_cr(" from");
      stack.print_on(output, 4);
    }
  };
  const auto print_committed_memory = [&](TrackedRange& committed_range) {
    const NativeCallStack& stack = _stack_storage->get(committed_range.stack_idx);
    const char* scale_name = NMTUtil::scale_name(scale);
    output->print("\n\t");
    output->print("\n\t");
    output->print("[" PTR_FORMAT " - " PTR_FORMAT "]" " committed " SIZE_FORMAT "%s",
                  p2i(committed_range.start), p2i(committed_range.end()),
                  NMTUtil::amount_in_scale(committed_range.size, scale), scale_name);
    if (stack.is_empty()) {
      output->print_cr(" ");
    } else {
      output->print_cr(" from");
      stack.print_on(output, 12);
    }
  };
  const auto print_reserved_memory = [&](TrackedRange& reserved_range) {
    const NativeCallStack& stack = _stack_storage->get(reserved_range.stack_idx);
    const char* scale_name = NMTUtil::scale_name(scale);
    output->print("[" PTR_FORMAT " - " PTR_FORMAT "]" " reserved " SIZE_FORMAT "%s for %s",
                  p2i(reserved_range.start), p2i(reserved_range.end()),
                  NMTUtil::amount_in_scale(reserved_range.size, scale), scale_name,
                  NMTUtil::flag_to_name(reserved_range.flag));
    if (stack.is_empty()) {
      output->print_cr(" ");
    } else {
      output->print_cr(" from");
      stack.print_on(output, 12);
    }
  };
  for (Id space_id = 0; space_id < PhysicalMemorySpace::unique_id; space_id++) {
    RegionStorage& reserved_ranges = *_reserved_regions;
    OffsetRegionStorage& mapped_ranges = _mapped_regions->at(space_id);
    RegionStorage& committed_ranges = _committed_regions->at(space_id);
    bool found_committed[committed_ranges.length()];
    for (int i = 0; i < committed_ranges.length(); i++) {
      found_committed[i] = false;
    }
    VirtualMemoryView::sort_regions(mapped_ranges);
    VirtualMemoryView::merge_mapped(mapped_ranges);

    output->print_cr("%s:", _names->at(space_id));
    for (int reserved_range_idx = 0; reserved_range_idx < reserved_ranges.length(); reserved_range_idx++) {
      TrackedRange& reserved_range = reserved_ranges.at(reserved_range_idx);
      print_reserved_memory(reserved_range);
      for (int mapped_range_idx = 0; mapped_range_idx < mapped_ranges.length(); mapped_range_idx++) {
        TrackedOffsetRange& mapped_range = mapped_ranges.at(mapped_range_idx);
        if (overlaps(reserved_range, mapped_range)) {
          output->print_cr("");
          print_mapped_virtual_memory_region(mapped_range);
          for (int committed_range_idx = 0; committed_range_idx < committed_ranges.length();
               committed_range_idx++) {
            TrackedRange& committed_range = committed_ranges.at(committed_range_idx);
            if (overlaps(Range{committed_range.start, committed_range.size},
                         Range{mapped_range.physical_address, mapped_range.size})) {
              print_committed_memory(committed_range);
              found_committed[committed_range_idx] = true;
            }
          }
        }
      }
    }
    for (int i = 0; i < committed_ranges.length(); i++) {
      if (!found_committed[i]) {
        TrackedRange& committed_range = committed_ranges.at(i);
        print_committed_memory(committed_range);
      }
    }
  }
}

void VirtualMemoryView::unregister_memory(RegionStorage& storage, address base_addr, size_t size) {
  Range range_to_remove{base_addr, size};
  TrackedOffsetRange out[2];
  int len;
  int orig_len = storage.length();
  for (int i = 0; i < orig_len; i++) {
    TrackedRange& crng = storage.at(i);
    OverlappingResult o_r = overlap_of(TrackedOffsetRange{crng}, range_to_remove, out, &len);
    if (o_r == OverlappingResult::NoOverlap) {
      continue;
    }
    int stack_idx = crng.stack_idx;
    // Delete old region
    storage.delete_at(i);
    // delete_at replaces the ith elt with the last one, so we need to rewind
    // otherwise we'll skip the previously last element.
    i--;
    // We have 1 fewer element to look at
    orig_len--;
    // And push on the new ones.
    for (int j = 0; j < len; j++) {
      storage.push(out[j]);
      _stack_storage->increment(stack_idx);
    }
    _stack_storage->decrement(stack_idx);
    // We're not breaking, no guarantee that there's exactly 1 region that matches
  }

  sort_regions(storage);
  merge_committed(storage);
}

void VirtualMemoryView::release_memory(address base_addr, size_t size) {
  unregister_memory(*_reserved_regions, base_addr, size);
}

void VirtualMemoryView::uncommit_memory_into_space(const PhysicalMemorySpace& space,
                                                         address offset, size_t size) {
  RegionStorage& committed_ranges = _committed_regions->at(space.id);
  unregister_memory(committed_ranges, offset, size);

}

void VirtualMemoryView::register_memory(RegionStorage& storage, address base_addr, size_t size, MEMFLAGS flag, const NativeCallStack& stack) {
  // Small optimization: Is the next commit overlapping with the last one? Then we don't need to push.
  if (storage.length() > 0) {
    TrackedRange& range = storage.at(storage.length() - 1);
    if ((overlaps(range, Range{base_addr, size})
         || adjacent(range, Range{base_addr, size}))
        && _stack_storage->get(range.stack_idx).equals(stack)
        && range.flag == flag) {
      range.start = MIN2(base_addr, range.start);
      range.size = MAX2(base_addr+size, range.end()) - range.start;
      return;
    }
  }
  int idx = _stack_storage->push(stack);
  storage.push(TrackedRange{base_addr, size, idx, flag});

  sort_regions(storage);
  merge_committed(storage);
}

void VirtualMemoryView::reserve_memory(address base_addr, size_t size, MEMFLAGS flag, const NativeCallStack& stack) {
  register_memory(*_reserved_regions, base_addr, size, flag, stack);
}

void VirtualMemoryView::commit_memory_into_space(const PhysicalMemorySpace& space,
                                                       address offset, size_t size,
                                                       const NativeCallStack& stack) {
  RegionStorage& crngs = _committed_regions->at(space.id);
  register_memory(crngs, offset, size, mtNone, stack);
}

void VirtualMemoryView::remove_view_into_space(const PhysicalMemorySpace& space,
                                                     address base_addr, size_t size) {
  Range range_to_remove{base_addr, size};
  OffsetRegionStorage& range_array = _mapped_regions->at(space.id);
  TrackedOffsetRange out[2];
  int len;
  for (int i = 0; i < range_array.length(); i++) {
    TrackedOffsetRange& range = range_array.at(i);
    bool has_overlap =
        OverlappingResult::NoOverlap != overlap_of(range, range_to_remove, out, &len);
    if (has_overlap) {
      int stack_idx = range.stack_idx;
      // Delete old region.
      range_array.delete_at(i);
      // Replace with the remaining ones
      for (int j = 0; j < len; j++) {
        _stack_storage->increment(stack_idx);
        range_array.push(out[j]);
      }
      // Decrement after incrementing to avoid triggering resizing
      _stack_storage->decrement(stack_idx);
    }
  }
}

// TODO: Maybe sorting the regions makes this easier to understand?
void VirtualMemoryView::add_view_into_space(const PhysicalMemorySpace& space,
                                                  address base_addr, size_t size, address offset,
                                                  MEMFLAGS flag, const NativeCallStack& stack) {
  // This method is a bit tricky because we need to care about preserving the offsets of any already existing view
  // that overlaps with the view being added.
  int stack_idx = _stack_storage->push(stack);
  OffsetRegionStorage& rngs = _mapped_regions->at(space.id);
  // We need to find overlapping regions and split on them, because the offsets may differ.
  for (int i = 0; i < rngs.length(); i++) {
    TrackedOffsetRange& rng = rngs.at(i);
    TrackedOffsetRange out[2];
    int len;
    OverlappingResult res = overlap_of(rng, Range{base_addr, size}, out, &len);
    if (res == OverlappingResult::NoOverlap) {
      // Do nothing
    } else if (res == OverlappingResult::EntirelyEnclosed
               || res == OverlappingResult::SplitInMiddle) {
      // We replace it.
      rngs.at_put(i, TrackedOffsetRange{base_addr, size, offset, stack_idx, flag});
      // And put the out with the physical offsets of the original
      for (int i = 0; i < len; i++) {
        rngs.push(out[i]);
      }
      // There can be no more, so we're done
      return;
    } else if (res == OverlappingResult::ShortenedFromLeft || res == OverlappingResult::ShortenedFromRight) {
      assert(len == 1, "must be");
      // We replace the old one with the shortened one
      rngs.at_put(i, out[0]);
      // But we can't quit, just gotta continue iterating
    }
  }
  // If we reach this then either there has only been ShortenedFromRight/ShortenedFromLeft
  // or no overlap. Then we must add the original region
  rngs.push(TrackedOffsetRange{base_addr, size, offset, stack_idx, flag});
  // And now we're done.
}

VirtualMemoryView::PhysicalMemorySpace VirtualMemoryView::register_space(const char* descriptive_name) {
  const PhysicalMemorySpace next_space = PhysicalMemorySpace{PhysicalMemorySpace::next_unique()};
  // These are allocated just to be copied for at_put_grow.
  OffsetRegionStorage to_copy_res{};
  RegionStorage to_copy_comm{};
  _mapped_regions->at_put_grow(next_space.id, to_copy_res);
  _committed_regions->at_put_grow(next_space.id, to_copy_comm);
  _names->at_put_grow(next_space.id, descriptive_name, "");
  return next_space;
}

void VirtualMemoryView::initialize(bool is_detailed_mode) {
  _reserved_regions = new RegionStorage{};
  _mapped_regions = new GrowableArrayCHeap<OffsetRegionStorage, mtNMT>{5};
  _committed_regions = new GrowableArrayCHeap<RegionStorage, mtNMT>{5};
  _stack_storage = new NativeCallStackStorage<IndexIterator>{is_detailed_mode ? NativeCallStackStorage<IndexIterator>::static_stack_size : 1, is_detailed_mode};
  _names = new GrowableArrayCHeap<const char*, mtNMT>{5};
  _is_detailed_mode = is_detailed_mode;
}

void VirtualMemoryView::merge_committed(RegionStorage& ranges) {
  // We displace into the array at rlen+j instead of
  // creating a new array and swapping it out at the end.
  // This is because of a limitation with GrowableArray
  int rlen = ranges.length();
  if (rlen <= 1) return;
  int j = 0;
  ranges.push(ranges.at(j));
  for (int i = 1; i < rlen; i++) {
    TrackedRange& merging_range = ranges.at(rlen+j);
    // Take an explicit copy, this is necessary because
    // the push might invalidate the reference and then SIGSEGV.
    const TrackedRange potential_range = ranges.at(i);
    if (merging_range.end() >=
            potential_range.start // There's overlap, known because of pre-condition
        && _stack_storage->get(merging_range.stack_idx)
               .equals(_stack_storage->get(potential_range.stack_idx))) {
      // Merge it
      merging_range.size = potential_range.end() - merging_range.start;
    } else {
      j++;
      ranges.push(potential_range);
    }
  }
  // Remove all the old elements, only keeping the merged ones.
  ranges.remove_till(rlen);
  return;
}

void VirtualMemoryView::merge_mapped(OffsetRegionStorage& ranges) {
  // We displace into the array at rlen+j instead of
  // creating a new array and swapping it out at the end.
  // This is because of a limitation with GrowableArray
  int rlen = ranges.length();
  if (rlen <= 1) return;
  int j = 0;
  ranges.push(ranges.at(j));
  for (int i = 1; i < rlen; i++) {
    TrackedOffsetRange& merging_range = ranges.at(rlen+j);
    // Take an explicit copy, this is necessary because
    // the push might invalidate the reference and then SIGSEGV.
    const TrackedOffsetRange potential_range = ranges.at(i);
    if (merging_range.end() >=
        potential_range.start // There's overlap, known because of pre-condition
        && _stack_storage->get(merging_range.stack_idx)
        .equals(_stack_storage->get(potential_range.stack_idx))
        && merging_range.physical_end() >=
        potential_range.physical_address) {
      // Merge it
      merging_range.size = potential_range.end() - merging_range.start;
    } else {
      j++;
      ranges.push(potential_range);
    }
  }
  // Remove all the old elements, only keeping the merged ones.
  ranges.remove_till(rlen);
  return;
}

void VirtualMemoryView::sort_regions(GrowableArrayCHeap<VirtualMemoryView::Range, mtNMT>& storage) {
  storage.sort([](Range* a, Range* b) -> int {
    return (a->start > b->start) - (a->start < b->start);
  });
}
void VirtualMemoryView::sort_regions(RegionStorage& storage) {
  storage.sort([](TrackedRange* a, TrackedRange* b) -> int {
    return (a->start > b->start) - (a->start < b->start);
  });
}

void VirtualMemoryView::sort_regions(OffsetRegionStorage& storage) {
  storage.sort([](TrackedOffsetRange* a, TrackedOffsetRange* b) -> int {
    return (a->start > b->start) - (a->start < b->start);
  });
}

bool VirtualMemoryView::overlaps(Range a, Range b) {
  return MAX2(b.start, a.start) < MIN2(b.end(), a.end());
}
bool VirtualMemoryView::adjacent(Range a, Range b) {
  return (a.start == b.end() || b.start == a.end());
}

VirtualMemoryView::OverlappingResult
VirtualMemoryView::overlap_of(TrackedOffsetRange to_split, Range to_remove,
                                    TrackedOffsetRange* out, int* len) {
  const auto a = to_split.start;
  const auto b = to_split.end();
  const auto c = to_remove.start;
  const auto d = to_remove.end();
  /*
      to_split enclosed entirely by to_remove -- nothing is left
      Also handles the case where they are exactly the same, still has the same result.
         a  b
       | |  | | => None.
       c      d
     */
  if (a >= c && b <= d) {
    *len = 0;
    return OverlappingResult::EntirelyEnclosed;
  }
  // to_remove enclosed entirely by to_split -- we end up with two ranges and a hole in the middle
  /*
       a      b    a c   d b
       | |  | | => | | , | |
         c  d
     */
  if (c > a && d < b) {
    *len = 2;
    address left_start = a;
    size_t left_size = static_cast<size_t>(c - a);
    address left_offset = to_split.physical_address;
    out[0] = TrackedOffsetRange{left_start, left_size, to_split.physical_address,
                                to_split.stack_idx, to_split.flag};
    address right_start = d;
    size_t right_size = static_cast<size_t>((a + to_split.size) - right_start);
    address right_offset =
        to_split.physical_address +
        (right_start - left_start); // How far along have we traversed into our offset?
    out[1] = TrackedOffsetRange{right_start, right_size, right_offset, to_split.stack_idx,
                                to_split.flag};
    return OverlappingResult::SplitInMiddle;
  }
  // Overlap from the left -- We end up with one region on the right
  /*
        a    b    d  b
      | | |  | => |  |
      c   d
     */
  if (c <= a && d > a && d < b) {
    *len = 1;
    out[0] = TrackedOffsetRange{d, static_cast<size_t>(b - d), to_split.physical_address + (d - a),
                                to_split.stack_idx, to_split.flag};
    return OverlappingResult::ShortenedFromLeft;
  }
  // Overlap from the right
  /*
      a   b       a  c
      | | |  | => |  |
        c    d
     */
  if (a < c && c < b && b <= d) {
    *len = 1;
    out[0] = TrackedOffsetRange{a, static_cast<size_t>(c - a), to_split.physical_address,
                                to_split.stack_idx, to_split.flag};
    return OverlappingResult::ShortenedFromRight;
  }
  // No overlap at all
  *len = 0;
  return OverlappingResult::NoOverlap;
}
