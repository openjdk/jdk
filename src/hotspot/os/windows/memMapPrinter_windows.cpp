/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat, Inc. and/or its affiliates.
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

#include "nmt/memMapPrinter.hpp"
#include "utilities/ostream.hpp"

static bool virtualQuery(address addr, MEMORY_BASIC_INFORMATION* minfo) {
  ZeroMemory(minfo, sizeof(MEMORY_BASIC_INFORMATION));
  const bool rc = ::VirtualQuery(addr, minfo, sizeof(MEMORY_BASIC_INFORMATION)) == sizeof(MEMORY_BASIC_INFORMATION);
  return rc;
}

struct WindowsMappingPrintInformation : public MappingPrintInformation {
  char _filename[1024 + 1];
  char _addinfo[256 + 1];

  WindowsMappingPrintInformation(const void* from, const void* to) :
    MappingPrintInformation(from, to), _filename(""), _addinfo("") {}

  void print_OS_specific_details(outputStream* st) const override {
    st->print("%s ", _addinfo);
  }

  const char* filename() const override { return _filename; }
};

void MemMapPrinter::pd_print_header(outputStream* st) {
  st->print(
#ifdef _LP64
  //   0x0000000000000000 - 0x0000000000000000
      "from                 to                 "
#else
  //   0x00000000 - 0x00000000
      "from         to         "
#endif
  );
  st->print_cr("size          info");
}

// Helper function for print_memory_mappings:
//  Given a MEMORY_BASIC_INFORMATION, containing information about a non-free region:
//  print out all regions in that allocation. If any of those regions
//  fall outside the given range [start, end), indicate that in the output.
// Return the pointer to the end of the allocation.
static address handle_one_mapping(MEMORY_BASIC_INFORMATION* minfo, address end, MappingPrintClosure& closure) {
  // Print it like this:
  //
  // Base: <xxxxx>: [xxxx - xxxx], state=MEM_xxx, prot=x, type=MEM_xxx       (region 1)
  //                [xxxx - xxxx], state=MEM_xxx, prot=x, type=MEM_xxx       (region 2)
  assert(minfo->State != MEM_FREE, "Not inside an allocation.");
  address allocation_base = (address)minfo->AllocationBase;
  for(;;) {
    address region_start = (address)minfo->BaseAddress;
    address region_end = region_start + minfo->RegionSize;
    assert(region_end > region_start, "Sanity");

    WindowsMappingPrintInformation mapinfo((const void*) region_start, (const void*) region_end);

    stringStream ss(mapinfo._addinfo, sizeof(mapinfo._addinfo));
    switch (minfo->State) {
      case MEM_COMMIT:  ss.print_raw("MEM_COMMIT "); break;
      case MEM_FREE:    ss.print_raw("MEM_FREE   "); break;
      case MEM_RESERVE: ss.print_raw("MEM_RESERVE"); break;
      default: ss.print("%x?", (unsigned)minfo->State);
    }
    ss.print(", prot=%3x, type=", (unsigned)minfo->Protect);
    switch (minfo->Type) {
      case MEM_IMAGE:   ss.print_raw("MEM_IMAGE  "); break;
      case MEM_MAPPED:  ss.print_raw("MEM_MAPPED "); break;
      case MEM_PRIVATE: ss.print_raw("MEM_PRIVATE"); break;
      default: ss.print("%x?", (unsigned)minfo->State);
    }

    if (!os::dll_address_to_library_name(region_start, mapinfo._filename, sizeof(mapinfo._filename), nullptr)) {
      mapinfo._filename[0] = '\0';
    }

    closure.do_it(&mapinfo);

    // Next region...
    bool rc = virtualQuery(region_end, minfo);
    if (rc == false ||                                         // VirtualQuery error, end of allocation?
       (minfo->State == MEM_FREE) ||                           // end of allocation, free memory follows
       ((address)minfo->AllocationBase != allocation_base) ||  // end of allocation, a new one starts
       (region_end > end))                                     // end of range to print.
    {
      return region_end;
    }
  }
  ShouldNotReachHere();
  return nullptr;
}

void MemMapPrinter::pd_iterate_all_mappings(MappingPrintClosure& closure) {

  // Use VirtualQuery to iterate over all mappings.
  MEMORY_BASIC_INFORMATION minfo;
  address start = (address)os::vm_allocation_granularity();
  static constexpr size_t reasonable_max = (size_t)G * 1024 * 128;
  address end = (address) reasonable_max;

  address p = start;
  address p2 = p; // guard against wraparounds

  static constexpr int max_fuse = 0x100000;
  int fuse = 0;

  while (p < end && p >= p2) {
    p2 = p;
    // Probe for the next mapping.
    if (virtualQuery(p, &minfo)) {
      if (minfo.State != MEM_FREE) {
        // Found one.
        address p2 = handle_one_mapping(&minfo, end, closure);
        assert(p2 > p, "Sanity");
        p = p2;
      } else {
        // Note: for free regions, most of MEMORY_BASIC_INFORMATION is undefined.
        //  Only region dimensions are not: use those to jump to the end of
        //  the free range.
        address region_start = (address)minfo.BaseAddress;
        address region_end = region_start + minfo.RegionSize;
        assert(p >= region_start && p < region_end, "Sanity");
        p = region_end;
      }
    } else {
      // MSDN doc on VirtualQuery is unclear about what it means if it returns an error.
      //  In particular, whether querying an address outside any mappings would report
      //  a MEM_FREE region or just return an error. From experiments, it seems to return
      //  a MEM_FREE region for unmapped areas in valid address space and an error if we
      //  are outside valid address space.
      // Here, we advance the probe pointer by alloc granularity. But if the range to print
      //  is large, this may take a long time. Therefore lets stop right away if the address
      //  is outside of what we know are valid addresses on Windows. Also, add a loop fuse.
      static const address end_virt = (address)(LP64_ONLY(0x7ffffffffffULL) NOT_LP64(3*G));
      if (p >= end_virt) {
        break;
      } else {
        // Advance probe pointer, but with a fuse to break long loops.
        if (fuse++ == max_fuse) {
          break;
        }
        p += os::vm_allocation_granularity();
      }
    }
  }
}
