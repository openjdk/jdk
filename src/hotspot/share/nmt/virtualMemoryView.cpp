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
#include "nmt/memTracker.hpp"
#include "nmt/threadStackTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "nmt/virtualMemoryView.hpp"
#include "runtime/os.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/ostream.hpp"

int VirtualMemoryView::PhysicalMemorySpace::unique_id = 0;
VirtualMemoryView::PhysicalMemorySpace VirtualMemoryView::Interface::_heap{};
VirtualMemoryView* VirtualMemoryView::Interface::_instance = nullptr;
GrowableArrayCHeap<const char*, mtNMT>* VirtualMemoryView::Interface::_names = nullptr;

void VirtualMemoryView::report(VirtualMemory& mem, outputStream* output, size_t scale) {
  auto print_mapped_memory = [&](TrackedOffsetRange& mapped_range) -> void {
    output->print("\n\t");
    const NativeCallStack& stack = _stack_storage.get(mapped_range.stack_idx);
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
    const NativeCallStack& stack = _stack_storage.get(committed_range.stack_idx);
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
    const NativeCallStack& stack = _stack_storage.get(reserved_range.stack_idx);
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
    RegionStorage& reserved_ranges = mem.reserved_regions;
    OffsetRegionStorage& mapped_ranges = mem.mapped_regions.at(space_id);
    RegionStorage& committed_ranges = mem.committed_regions.at(space_id);
    // Sort and minimize
    sort_regions(reserved_ranges);
    sort_regions(mapped_ranges);
    sort_regions(committed_ranges);
    merge_memregions(reserved_ranges);
    merge_memregions(committed_ranges);
    merge_mapped(mapped_ranges);
    for (int i = 0; i < reserved_ranges.length(); i++) {
      print_reserved_memory(reserved_ranges.at(i));
    }
    for (int i = 0; i < mapped_ranges.length(); i++) {
      print_mapped_memory(mapped_ranges.at(i));
    }
    for (int i = 0; i < committed_ranges.length(); i++) {
      print_committed_memory(committed_ranges.at(i));
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
    NativeCallStackStorage::StackIndex stack_idx = crng.stack_idx;
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
    }
    // We're not breaking, no guarantee that there's exactly 1 region that matches
  }
}

void VirtualMemoryView::release_memory(address base_addr, size_t size) {
  unregister_memory(_virt_mem.reserved_regions, base_addr, size);
}

void VirtualMemoryView::uncommit_memory_into_space(const PhysicalMemorySpace& space,
                                                         address offset, size_t size) {
  RegionStorage& committed_ranges = _virt_mem.committed_regions.at(space.id);
  unregister_memory(committed_ranges, offset, size);

}

void VirtualMemoryView::register_memory(RegionStorage& storage, address base_addr, size_t size, MEMFLAGS flag, const NativeCallStack& stack) {
  NativeCallStackStorage::StackIndex idx = _stack_storage.push(stack);
  // Small optimization: While the next commit overlapping with the last one we can merge without pushing.
  if (storage.length() > 0) {
    int i = 1;
    int len = storage.length();
    TrackedRange base_range{base_addr, size, idx, flag};
    TrackedRange& range = storage.at(len - i);

    // Do the merging repeatedly.
    while ((overlaps(range, base_range)
         || adjacent(range, base_range))
        && _stack_storage.get(range.stack_idx).equals(stack)
        && range.flag == flag) {
      base_range.start = MIN2(base_addr, range.start);
      base_range.size = MAX2(base_addr+size, range.end()) - range.start;
      i++;
      range = storage.at(len - i);
    }
    // Did we merge any?
    if (i > 1) {
      storage.remove_range(len - i+1, len);
      storage.push(base_range);
      return;
    }
    // Otherwise pass onto regular case.
  }
  storage.push(TrackedRange{base_addr, size, idx, flag});
}

void VirtualMemoryView::reserve_memory(address base_addr, size_t size, MEMFLAGS flag, const NativeCallStack& stack) {
  register_memory(_virt_mem.reserved_regions, base_addr, size, flag, stack);
}

void VirtualMemoryView::commit_memory_into_space(const PhysicalMemorySpace& space,
                                                 address offset, size_t size,
                                                 const NativeCallStack& stack) {
  RegionStorage& crngs = _virt_mem.committed_regions.at(space.id);
  register_memory(crngs, offset, size, mtNone, stack);
}

void VirtualMemoryView::remove_view_into_space(const PhysicalMemorySpace& space,
                                               address base_addr, size_t size) {
  Range range_to_remove{base_addr, size};
  OffsetRegionStorage& range_array = _virt_mem.mapped_regions.at(space.id);
  TrackedOffsetRange out[2];
  int len;
  for (int i = 0; i < range_array.length(); i++) {
    TrackedOffsetRange& range = range_array.at(i);
    bool has_overlap =
        OverlappingResult::NoOverlap != overlap_of(range, range_to_remove, out, &len);
    if (has_overlap) {
      NativeCallStackStorage::StackIndex stack_idx = range.stack_idx;
      // Delete old region.
      range_array.delete_at(i);
      // Replace with the remaining ones
      for (int j = 0; j < len; j++) {
        range_array.push(out[j]);
      }
    }
  }
  VirtualMemoryView::sort_regions(range_array);
  VirtualMemoryView::merge_mapped(range_array);
}

void VirtualMemoryView::add_view_into_space(const PhysicalMemorySpace& space,
                                                  address base_addr, size_t size, address offset,
                                                  MEMFLAGS flag, const NativeCallStack& stack) {
  // This method is a bit tricky because we need to care about preserving the offsets of any already existing view
  // that overlaps with the view being added.
  NativeCallStackStorage::StackIndex stack_idx = _stack_storage.push(stack);
  OffsetRegionStorage& rngs = _virt_mem.mapped_regions.at(space.id);
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
  VirtualMemoryView::sort_regions(rngs);
  VirtualMemoryView::merge_mapped(rngs);
}

VirtualMemoryView::VirtualMemoryView(bool is_detailed_mode)
: _virt_mem{}, _thread_stacks{}, _stack_storage{is_detailed_mode} {}

void VirtualMemoryView::merge_memregions(RegionStorage& ranges) {
  RegionStorage merged_ranges;
  int rlen = ranges.length();
  if (rlen <= 1) return;
  merged_ranges.push(ranges.at(0));
  int j = 0;
  for (int i = 1; i < rlen; i++) {
    TrackedRange& merging_range = merged_ranges.at(j);
    TrackedRange& potential_range = ranges.at(i);

    if (
        // There's overlap, known because of pre-condition
        merging_range.end() >= potential_range.start
        && merging_range.flag == potential_range.flag
        && equal_stacks(merging_range.stack_idx, potential_range.stack_idx)
        ) {
      // Merge it
      merging_range.size = potential_range.end() - merging_range.start;
    } else {
      j++;
      merged_ranges.push(potential_range);
    }
  }
  ranges.swap(&merged_ranges);
}

void VirtualMemoryView::merge_mapped(OffsetRegionStorage& ranges) {
  OffsetRegionStorage merged_ranges;
  int rlen = ranges.length();
  if (rlen <= 1) return;
  merged_ranges.push(ranges.at(0));
  int j = 0;
  for (int i = 1; i < rlen; i++) {
    TrackedOffsetRange& merging_range = merged_ranges.at(j);
    const Range merging_physical{merging_range.physical_address, merging_range.size};
    TrackedOffsetRange& potential_range = ranges.at(i);
    const Range potential_physical{potential_range.physical_address, potential_range.size};
    if (
        // There's overlap, known because of pre-condition
        merging_range.end() >= potential_range.start
        && merging_range.flag == potential_range.flag
        && equal_stacks(merging_range.stack_idx, potential_range.stack_idx)
        && (overlaps(merging_physical, potential_physical)
            || adjacent(merging_physical, potential_physical))) {
      // Merge it
      merging_range.size = potential_range.end() - merging_range.start;
      Range physical_union = union_of(merging_physical, potential_physical);
      assert(merging_range.size == physical_union.size, "invariant");
      merging_range.physical_address = physical_union.start;
    } else {
      j++;
      merged_ranges.push(potential_range);
    }
  }
  ranges.swap(&merged_ranges);
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

bool VirtualMemoryView::adjacent(Range a, Range b) {
  return (a.start == b.end() || b.start == a.end());
}
bool VirtualMemoryView::disjoint(Range a, Range b) {
  return !(overlaps(a, b) || adjacent(a, b));
}
bool VirtualMemoryView::overlaps(Range a, Range b) {
  return MAX2(b.start, a.start) < MIN2(b.end(), a.end());
}
VirtualMemoryView::Range VirtualMemoryView::union_of(Range a, Range b) {
  precond(!disjoint(a, b));
  const address start = MIN2(b.start, a.start);
  const address end = MAX2(b.end(), a.end());
  return Range(start , pointer_delta(end, start, 1));
}

bool VirtualMemoryView::is_same(Range a, Range b) {
  return a.start == b.start && a.size == b.size;
}

bool VirtualMemoryView::is_empty(Range a) {
  return a.size == 0;
}

bool VirtualMemoryView::same_stack(TrackedRange& a, TrackedRange& b) {
  NativeCallStackStorage::StackIndex& ai = a.stack_idx;
  NativeCallStackStorage::StackIndex& bi = b.stack_idx;
  return ai.index() == bi.index() && ai.chunk() == bi.chunk();
}

VirtualMemoryView::Range VirtualMemoryView::overlap_of(Range a, Range b) {
  if (!overlaps(a, b)) {
    return {};
  }
  const address start = MAX2(b.start, a.start);
  const address end = MIN2(b.end(), a.end());
  return Range(start, pointer_delta(end, start, 1));
}



VirtualMemoryView::OverlappingResult
VirtualMemoryView::overlap_of(TrackedOffsetRange to_split, Range to_remove,
                                    TrackedOffsetRange* out, int* len) {
  const address a = to_split.start;
  const address b = to_split.end();
  const address c = to_remove.start;
  const address d = to_remove.end();
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


void VirtualMemoryView::merge_thread_stacks(GrowableArrayCHeap<VirtualMemoryView::Range, mtNMT>& ranges) {
  GrowableArrayCHeap<VirtualMemoryView::Range, mtNMT> merged_ranges{32};
  auto rlen = ranges.length();
  if (rlen == 0) return;
  int j = 0;
  merged_ranges.push(ranges.at(j));
  for (int i = 1; i < rlen; i++) {
    Range& merging_range = merged_ranges.at(j);
    Range& potential_range = ranges.at(i);
    if (merging_range.end() >= potential_range.start) { // There's overlap, known because of pre-condition
      // Merge it
      merging_range.size = potential_range.end() - merging_range.start;
    } else {
      j++;
      merged_ranges.push(potential_range);
    }
  }
  ranges.swap(&merged_ranges);
}
address VirtualMemoryView::thread_stack_uncommitted_bottom(TrackedRange& rng, RegionStorage& committed_ranges) {
  address bottom = rng.start;
  address top = bottom + rng.size;
  for (int i = 0; i < committed_ranges.length(); i++) {
    TrackedRange& crng = committed_ranges.at(i);
    address committed_top = crng.start + crng.size;
    if (crng.start >= bottom && committed_top < top) {
      bottom = committed_top;
    }
  }
  return bottom;
}

bool VirtualMemoryView::RegionIterator::next_committed(address& committed_start, size_t& committed_size) {
    if (end() <= _current_start) return false;

    const size_t page_sz = os::vm_page_size();
    const size_t current_size = end() - _current_start;
    if (os::committed_in_range(_current_start, current_size, committed_start, committed_size)) {
      assert(committed_start != nullptr, "Must be");
      assert(committed_size > 0 && is_aligned(committed_size, os::vm_page_size()), "Must be");

      _current_start = committed_start + committed_size;
      return true;
    } else {
      return false;
    }
  }


// TODO: Broken. Refactor somehow.
void VirtualMemoryView::snapshot_thread_stacks() {
  _thread_stacks.clear();
  RegionStorage& reserved_ranges = _virt_mem.reserved_regions;
  RegionStorage& committed_ranges = _virt_mem.committed_regions.at(0);
  for (int i = 0; i < reserved_ranges.length(); i++) {
    TrackedRange& rng = reserved_ranges.at(i);
    if (rng.flag == mtThreadStack) {
      address stack_bottom = thread_stack_uncommitted_bottom(rng, committed_ranges);
      address committed_start;
      size_t committed_size;
      size_t stack_size = rng.start + rng.size - stack_bottom;
      // Align the size to work with full pages (Alpine and AIX stack top is not page aligned)
      size_t aligned_stack_size = align_up(stack_size, os::vm_page_size());

      NativeCallStack ncs; // empty stack
      VirtualMemoryView::RegionIterator itr(stack_bottom, aligned_stack_size);
      while (itr.next_committed(committed_start, committed_size)) {
        assert(committed_start != nullptr, "Should not be null");
        assert(committed_size > 0, "Should not be 0");
        if (stack_bottom + stack_size < committed_start + committed_size) {
          committed_size = stack_bottom + stack_size - committed_start;
        }
      }
      _thread_stacks.push(Range{committed_start, committed_size});
    }
  }

  sort_regions(_thread_stacks);
  merge_thread_stacks(_thread_stacks);
}

void VirtualMemoryView::map_it(const VirtualMemoryView::RegionStorage& res,
                               const VirtualMemoryView::OffsetRegionStorage& map,
                               VirtualMemoryView::RegionStorage& mapping) {
  // Pre-cond: res and map merged and sorted
  using Range = VirtualMemoryView::Range;
  mapping.clear();
  auto& front = mapping;
  RegionStorage back{};
  for (int i = 0; i < res.length(); i++) {
    back.push(res.at(i));
  }

  for (int i = 0; i < back.length(); i++) {
    // Ignore O(n^2) for now
    TrackedRange& range = back.at(i);
    for (int j = 0; j < map.length(); j++) {
      // Ignore case when we're outside of map range
      const TrackedOffsetRange& mapped = map.at(j);
      TrackedOffsetRange out[2];
      int len;
      OverlappingResult res = overlap_of(TrackedOffsetRange{range}, Range{mapped}, out, &len);
      if (res == OverlappingResult::NoOverlap) {
        continue;
      } else if (res == OverlappingResult::EntirelyEnclosed) {
        front.push(TrackedRange{mapped.physical_address, mapped.size, range.stack_idx, range.flag});
      } else if (res == OverlappingResult::ShortenedFromLeft) {
        TrackedOffsetRange& R = out[0];
        size_t offset = range.start - mapped.start;
        address phys_start = mapped.physical_address + offset;
        size_t size = R.start - range.start;
        // Push the mapping
        front.push(TrackedRange{phys_start, size, range.stack_idx, range.flag});
        // Replace the original range with the now smaller one
        back.at_put(i, R);
      } else if (res == OverlappingResult::ShortenedFromRight) {
        TrackedOffsetRange& R = out[0];
        address phys_start = mapped.physical_address;
        size_t size = range.size - R.size;
        // Push the mapping
        front.push(TrackedRange{phys_start, size, range.stack_idx, range.flag});
        // Replace the original range with the now smaller one
        back.at_put(i, R);
      } else if (res == OverlappingResult::SplitInMiddle) {
        front.push(TrackedRange{mapped.physical_address, mapped.size, range.stack_idx, range.flag});
        // Here's the O(n^2) case, we really need to reverse the order of the merge and sort.
        back.at_put(i, out[1]);
        back.insert_before(i, out[0]);
      }
    }
    // OK, done with mapping this range, push it and remove it
    front.push(range);
    // O(n^2) here also.
    back.remove_at(i);
  }
};

void VirtualMemoryView::compute_summary_snapshot(VirtualMemory& vmem) {
  // Reset all memory, keeping peak values
  for (int i = 0; i < vmem.summary.length(); i++) {
    VirtualMemorySnapshot& snap = vmem.summary.at(i);
    for (int i = 0; i < mt_number_of_types; i++) {
      MEMFLAGS flag = NMTUtil::index_to_flag(i);
      ::VirtualMemory* mem = snap.by_type(flag);
      mem->release_memory(mem->reserved());
      mem->uncommit_memory(mem->committed());
    }
  }

  // Register all reserved memory for each id
  RegionStorage& reserved_ranges = vmem.reserved_regions;
  for (int i = 0; i < vmem.summary.length(); i++) {
    VirtualMemorySnapshot& snap = vmem.summary.at(i);
    for (int j = 0; j < reserved_ranges.length(); j++) {
      const TrackedRange& range = reserved_ranges.at(j);
      snap.by_type(range.flag)->reserve_memory(range.size);
    }
  }

  for (int i = 0; i < vmem.committed_regions.length(); i++) {
    // We must now find all committed memory regions contained by each reserved area.
    // Any committed memory outside of the reserved area is ignored.
    OffsetRegionStorage& mapped_ranges = vmem.mapped_regions.at(i);
    RegionStorage& committed_ranges = vmem.committed_regions.at(i);

    // Set up for map_it.
    sort_regions(reserved_ranges);
    merge_memregions(reserved_ranges);
    sort_regions(committed_ranges);
    merge_memregions(committed_ranges);
    sort_regions(mapped_ranges);
    merge_mapped(mapped_ranges);

    RegionStorage mapping;
    map_it(reserved_ranges, mapped_ranges, mapping);
    VirtualMemorySnapshot& snap = vmem.summary.at(i);
    // Use this mapping to find each appropriate memory flag for each committed mapping
    for (int i = 0; i < committed_ranges.length(); i++) {
      const TrackedRange& crng = committed_ranges.at(i);
      for (int j = 0; j < mapping.length(); j++) {
        TrackedRange& m = mapping.at(j);
        Range olap = overlap_of(crng, m);
        snap.by_type(m.flag)->commit_memory(olap.size);
      }
    }
  }
}

void VirtualMemoryView::Interface::initialize(bool is_detailed_mode) {
  _instance = (VirtualMemoryView*)os::malloc(sizeof(VirtualMemoryView), mtNMT);
  ::new (_instance) VirtualMemoryView(is_detailed_mode);;
  _names = new GrowableArrayCHeap<const char*, mtNMT>{};
  _heap = register_space("Heap");
}

VirtualMemoryView::PhysicalMemorySpace VirtualMemoryView::Interface::register_space(const char* descriptive_name) {
  const PhysicalMemorySpace next_space = PhysicalMemorySpace{PhysicalMemorySpace::next_unique()};
  // These are allocated just to be copied for at_put_grow.
  OffsetRegionStorage to_copy_mapped{};
  RegionStorage to_copy_committed{};
  VirtualMemorySnapshot to_copy_snapshot{};

  _instance->_virt_mem.mapped_regions.at_put_grow(next_space.id, to_copy_mapped);
  _instance->_virt_mem.committed_regions.at_put_grow(next_space.id, to_copy_committed);
  _instance->_virt_mem.summary.at_put_grow(next_space.id, to_copy_snapshot);
  _names->at_put_grow(next_space.id, descriptive_name, "");
  return next_space;
}

void VirtualMemoryView::Interface::reserve_memory(address base_addr, size_t size, MEMFLAGS flag,
                                                  const NativeCallStack& stack) {
}
void VirtualMemoryView::Interface::release_memory(address base_addr, size_t size) {
}
void VirtualMemoryView::Interface::commit_memory(address base_addr, size_t size,
                                                 const NativeCallStack& stack) {
}
void VirtualMemoryView::Interface::uncommit_memory(address base_addr, size_t size) {
}
void VirtualMemoryView::Interface::add_view_into_space(const PhysicalMemorySpace& space,
                                                       address base_addr, size_t size,
                                                       address offset, MEMFLAGS flag,
                                                       const NativeCallStack& stack) {
}
void VirtualMemoryView::Interface::remove_view_into_space(const PhysicalMemorySpace& space,
                                                          address base_addr, size_t size) {
}
void VirtualMemoryView::Interface::commit_memory_into_space(const PhysicalMemorySpace& space,
                                                            address offset, size_t size,
                                                            const NativeCallStack& stack) {
}
void VirtualMemoryView::Interface::uncommit_memory_into_space(const PhysicalMemorySpace& space,
                                                              address offset, size_t size) {
}
void VirtualMemoryView::Interface::report(VirtualMemory& mem, outputStream* output, size_t scale) {
}
void VirtualMemoryView::Interface::compute_summary_snapshot(VirtualMemory& vmem) {
}
