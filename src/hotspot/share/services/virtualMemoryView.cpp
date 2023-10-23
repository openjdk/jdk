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
#include "runtime/os.hpp"
#include "runtime/threadCritical.hpp"
#include "services/memTracker.hpp"
#include "services/threadStackTracker.hpp"
#include "services/virtualMemoryTracker.hpp"
#include "services/virtualMemoryView.hpp"
#include "utilities/ostream.hpp"

uint32_t VirtualMemoryView::PhysicalMemorySpace::unique_id = 0;
GrowableArrayCHeap<const char*, mtNMT>* VirtualMemoryView::names = nullptr;
GrowableArrayCHeap<VirtualMemoryView::OffsetRegionStorage, mtNMT>* VirtualMemoryView::reserved_regions = nullptr;
GrowableArrayCHeap<VirtualMemoryView::RegionStorage, mtNMT>* VirtualMemoryView::committed_regions = nullptr;
GrowableArrayCHeap<NativeCallStack, mtNMT>* VirtualMemoryView::all_the_stacks = nullptr;

void VirtualMemoryView::report(outputStream* output, size_t scale) {
  auto print_virtual_memory_region = [&](TrackedOffsetRange& reserved_range) -> void {
    NativeCallStack& stack = all_the_stacks->at(reserved_range.stack_idx);
    const char* scale_name = NMTUtil::scale_name(scale);
    output->print("[" PTR_FORMAT " - " PTR_FORMAT "]" " reserved " SIZE_FORMAT "%s",
                  p2i(reserved_range.start), p2i(reserved_range.end()),
                  NMTUtil::amount_in_scale(reserved_range.size, scale), scale_name);
    if (reserved_range.start != reserved_range.physical_address) {
      output->print(" mapped to [" PTR_FORMAT ", " PTR_FORMAT ")", p2i(reserved_range.physical_address), p2i(reserved_range.physical_end()));
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
    NativeCallStack& stack = all_the_stacks->at(committed_range.stack_idx);
    const char* scale_name = NMTUtil::scale_name(scale);
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
  for (Id space_id = 0; space_id < PhysicalMemorySpace::unique_id; space_id++) {
    OffsetRegionStorage& reserved_ranges = reserved_regions->at(space_id);
    RegionStorage& committed_ranges = committed_regions->at(space_id);
    bool found_committed[committed_ranges.length()];
    for (int i = 0; i < committed_ranges.length(); i++) {
      found_committed[i] = false;
    }
    output->print_cr("%s:", names->at(space_id));
    for (int reserved_range_idx = 0; reserved_range_idx < reserved_ranges.length(); reserved_range_idx++) {
      output->bol();
      TrackedOffsetRange& reserved_range = reserved_ranges.at(reserved_range_idx);
      print_virtual_memory_region(reserved_range);
      for (int committed_range_idx = 0; committed_range_idx < committed_ranges.length();
           committed_range_idx++) {
        TrackedRange& committed_range = committed_ranges.at(committed_range_idx);
        if (overlaps(Range{committed_range.start, committed_range.size},
                     Range{reserved_range.physical_address, reserved_range.size})) {
          print_committed_memory(committed_range);
          found_committed[committed_range_idx] = true;
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

void VirtualMemoryView::uncommit_memory_into_space(const PhysicalMemorySpace& space,
                                                         address offset, size_t size) {
  Range range_to_remove{offset, size};
  TrackedOffsetRange out[2];
  int len;

  RegionStorage& commits = committed_regions->at(space.id);
  int orig_len = commits.length();
  for (int i = 0; i < orig_len; i++) {
    TrackedRange& crng = commits.at(i);
    OverlappingResult o_r = overlap_of(TrackedOffsetRange{crng}, range_to_remove, out, &len);
    if (o_r == OverlappingResult::NoOverlap) {
      continue;
    }
    // Delete old region
    commits.delete_at(i);
    // delete_at replaces the ith elt with the last one, so we need to rewind
    // otherwise we'll skip the previously last element.
    i--;
    // We have 1 fewer element to look at
    orig_len--;
    // And push on the new ones.
    for (int j = 0; j < len; j++) {
      commits.push(out[j]);
    }
    // We're not breaking, no guarantee that there's exactly 1 region that matches
  }

  sort_regions(commits);
  merge_committed(commits);
}

void VirtualMemoryView::commit_memory_into_space(const PhysicalMemorySpace& space,
                                                       address offset, size_t size,
                                                       const NativeCallStack& stack) {
  RegionStorage& crngs = committed_regions->at(space.id);
  // Small optimization: Is the next commit overlapping with the last one? Then we don't need to push.
  if (crngs.length() > 0) {
    TrackedRange& crng = crngs.at(crngs.length() - 1);
    if (overlaps(crng, Range{offset, size})
        || adjacent(crng, Range{offset, size})
        && all_the_stacks->at(crng.stack_idx).equals(stack)) {
      crng.start = MIN2(offset, crng.start);
      crng.size = MAX2(offset+size, crng.end()) - crng.start;
      return;
    }
  }
  int idx = push_stack(stack);
  crngs.push(TrackedRange{offset, size, idx, mtNone});

  sort_regions(crngs);
  merge_committed(crngs);

}

void VirtualMemoryView::remove_all_views_into_space(const PhysicalMemorySpace& space) {
  reserved_regions->at(space.id).clear_and_deallocate();
}

void VirtualMemoryView::remove_view_into_space(const PhysicalMemorySpace& space,
                                                     address base_addr, size_t size) {
  Range range_to_remove{base_addr, size};
  OffsetRegionStorage& range_array = reserved_regions->at(space.id);
  TrackedOffsetRange out[2];
  int len;
  for (int i = 0; i < range_array.length(); i++) {
    bool has_overlap =
        OverlappingResult::NoOverlap != overlap_of(range_array.at(i), range_to_remove, out, &len);
    if (has_overlap) {
      // Delete old region.
      range_array.delete_at(i);
      // Replace with the remaining ones
      for (int j = 0; j < len; j++) {
        range_array.push(out[j]);
      }
    }
  }
}

// TODO: Maybe sorting the regions makes this easier to understand?
void VirtualMemoryView::add_view_into_space(const PhysicalMemorySpace& space,
                                                  address base_addr, size_t size, address offset,
                                                  MEMFLAGS flag, const NativeCallStack& stack) {
  // This method is a bit tricky because we need to care about preserving the offsets of any already existing view
  // that overlaps with the view being added.
  int stack_idx = push_stack(stack);
  OffsetRegionStorage& rngs = reserved_regions->at(space.id);
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
  reserved_regions->at_ref_grow(next_space.id, [](OffsetRegionStorage* p) -> void {
    ::new (p) OffsetRegionStorage{128};
  });
  committed_regions->at_ref_grow(next_space.id, [](RegionStorage* p) -> void {
    ::new (p) RegionStorage{128};
  });
  names->at_put_grow(next_space.id, descriptive_name, "");
  return next_space;
}

void VirtualMemoryView::initialize() {
  reserved_regions = new GrowableArrayCHeap<OffsetRegionStorage, mtNMT>{5};
  committed_regions = new GrowableArrayCHeap<RegionStorage, mtNMT>{5};
  all_the_stacks = new GrowableArrayCHeap<NativeCallStack, mtNMT>{static_stack_size};
  names = new GrowableArrayCHeap<const char*, mtNMT>{5};
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
        && all_the_stacks->at(merging_range.stack_idx)
               .equals(all_the_stacks->at(potential_range.stack_idx))) {
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

int VirtualMemoryView::push_stack(const NativeCallStack& stack) {
  int len = all_the_stacks->length();
  int idx = stack.calculate_hash() % static_stack_size;
  if (len < idx) {
    all_the_stacks->at_put_grow(idx, stack);
    return idx;
  }
  // Exists and already there? No need for double storage
  if (all_the_stacks->at(idx).equals(stack)) {
    return idx;
  }
  // There was a collision, just push it
  all_the_stacks->push(stack);
  return len;
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
