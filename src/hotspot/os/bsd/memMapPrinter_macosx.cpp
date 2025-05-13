/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2024, Red Hat, Inc. and/or its affiliates.
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

#if defined(__APPLE__)

#include "nmt/memMapPrinter.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/powerOfTwo.hpp"

#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <libproc.h>
#include <unistd.h>

#include <mach/vm_inherit.h>
#include <mach/vm_prot.h>
#include <mach/mach_vm.h>

// maximum number of mapping records returned
static const int MAX_REGIONS_RETURNED = 1000000;

// ::mmap() on MacOS is a layer on top of Mach system calls, and will allocate in 128MB chunks.
// This code will coalesce a series of identical 128GB chunks (maybe followed by one smaller chunk
// with identical flags) into one.
// Unfortunately, two or more identically allocated contiguous sections will appear as one, if the
// first section is size 128MB.  vmmap(1) has the same issue.
static const int MACOS_PARTIAL_ALLOCATION_SIZE = 128 * M;

class MappingInfo {
  proc_regioninfo _rinfo;
public:
  const char* _address;
  size_t _size;
  stringStream _share_buffer;
  stringStream _type_buffer;
  stringStream _protect_buffer;
  stringStream _file_name;
  const char* _tag_text;

  MappingInfo() : _address(nullptr), _size(0), _tag_text(nullptr) {}

  void reset() {
    _share_buffer.reset();
    _protect_buffer.reset();
    _type_buffer.reset();
    _file_name.reset();
    _tag_text = nullptr;
  }

  bool canCombine(const proc_regionwithpathinfo& mem_info) {
    const proc_regioninfo& n = mem_info.prp_prinfo;
    bool cc = _rinfo.pri_size == MACOS_PARTIAL_ALLOCATION_SIZE
              && n.pri_address == (_rinfo.pri_address + _size)
              && n.pri_protection == _rinfo.pri_protection
              && n.pri_max_protection == _rinfo.pri_max_protection
              && n.pri_user_tag == _rinfo.pri_user_tag
              && n.pri_share_mode == _rinfo.pri_share_mode
              && n.pri_offset == 0;
    return cc;
  }

  void combineWithFollowing(const proc_regionwithpathinfo& mem_info) {
    _size += mem_info.prp_prinfo.pri_size;
  }

  void process(const proc_regionwithpathinfo& mem_info) {
    reset();

    _rinfo = mem_info.prp_prinfo;

    _address = (const char*) _rinfo.pri_address;
    _size = _rinfo.pri_size;

    if (mem_info.prp_vip.vip_path[0] != '\0') {
      _file_name.print_raw(mem_info.prp_vip.vip_path);
    }
    // proc_regionfilename() seems to give bad results, so we don't try to use it here.

    char prot[4];
    char maxprot[4];
    rwbits(_rinfo.pri_protection, prot);
    rwbits(_rinfo.pri_max_protection, maxprot);
    _protect_buffer.print("%s/%s", prot, maxprot);

    get_share_mode(_share_buffer, _rinfo);
    _tag_text = tagToStr(_rinfo.pri_user_tag);
  }

  static void get_share_mode(outputStream& out, const proc_regioninfo& rinfo) {
    static const char* share_strings[] = {
      "cow", "pvt", "---", "shr", "tsh", "p/a", "s/a", "lpg"
    };
    assert(SM_COW == 1 && SM_LARGE_PAGE == (sizeof(share_strings)/sizeof(share_strings[0])), "share_mode contants are out of range");  // the +1 offset is intentional; see below
    const bool valid_share_mode = rinfo.pri_share_mode >= SM_COW && rinfo.pri_share_mode <= SM_LARGE_PAGE;
    if (valid_share_mode) {
      int share_mode = rinfo.pri_share_mode;
      out.print_raw(share_strings[share_mode - 1]);
    } else {
      out.print_cr("invalid pri_share_mode (%d)", rinfo.pri_share_mode);
      assert(valid_share_mode, "invalid pri_share_mode (%d)", rinfo.pri_share_mode);
    }
  }

#define X1(TAG, DESCR) X2(TAG, DESCR)
#define X2(TAG, DESCRIPTION) case VM_MEMORY_ ## TAG: return # DESCRIPTION;
  static const char* tagToStr(uint32_t user_tag) {
    switch (user_tag) {
      case 0:
        return 0;
      X1(MALLOC, malloc);
      X1(MALLOC_SMALL, malloc_small);
      X1(MALLOC_LARGE, malloc_large);
      X1(MALLOC_HUGE, malloc_huge);
      X1(SBRK, sbrk);
      X1(REALLOC, realloc);
      X1(MALLOC_TINY, malloc_tiny);
      X1(MALLOC_LARGE_REUSABLE, malloc_large_reusable);
      X1(MALLOC_LARGE_REUSED, malloc_lage_reused);
      X1(ANALYSIS_TOOL, analysis_tool);
      X1(MALLOC_NANO, malloc_nano);
      X1(MALLOC_MEDIUM, malloc_medium);
      X1(MALLOC_PROB_GUARD, malloc_prob_guard);
      X1(MACH_MSG, malloc_msg);
      X1(IOKIT, IOKit);
      X1(STACK, stack);
      X1(GUARD, guard);
      X1(SHARED_PMAP, shared_pmap);
      X1(DYLIB, dylib);
      X1(UNSHARED_PMAP, unshared_pmap);
      X2(APPKIT, AppKit);
      X2(FOUNDATION, Foundation);
      X2(COREGRAPHICS, CoreGraphics);
      X2(CORESERVICES, CoreServices); // is also VM_MEMORY_CARBON
      X2(JAVA, Java);
      X2(COREDATA, CoreData);
      X1(COREDATA_OBJECTIDS, CodeData_objectids);
      X1(ATS, ats);
      X1(DYLD, dyld);
      X1(DYLD_MALLOC, dyld_malloc);
      X1(SQLITE, sqlite);
      X1(JAVASCRIPT_CORE, javascript_core);
      X1(JAVASCRIPT_JIT_EXECUTABLE_ALLOCATOR, javascript_jit_executable_allocator);
      X1(JAVASCRIPT_JIT_REGISTER_FILE, javascript_jit_register_file);
      X1(OPENCL, OpenCL);
      X2(COREIMAGE, CoreImage);
      X2(IMAGEIO, ImageIO);
      X2(COREPROFILE, CoreProfile);
      X1(APPLICATION_SPECIFIC_1, application_specific_1);
      X1(APPLICATION_SPECIFIC_16, application_specific_16);
      X1(OS_ALLOC_ONCE, os_alloc_once);
      X1(GENEALOGY, genealogy);
      default:
        static char buffer[30];
        snprintf(buffer, sizeof(buffer), "user_tag=0x%x(%d)", user_tag, user_tag);
        return buffer;
    }
  }

  static void rwbits(int rw, char bits[4]) {
    bits[0] = rw & VM_PROT_READ ? 'r' : '-';
    bits[1] = rw & VM_PROT_WRITE ? 'w' : '-';
    bits[2] = rw & VM_PROT_EXECUTE ? 'x' : '-';
    bits[3] = 0;
  }
};

class ProcSmapsSummary {
  unsigned _num_mappings;
  size_t _private;
  size_t _committed;    // combined committed size
  size_t _reserved;     // reserved but not committed
  size_t _shared;       // combined shared size
  size_t _swapped_out;  // combined amount of swapped-out memory
public:
  ProcSmapsSummary() : _num_mappings(0), _private(0),
                       _committed(0), _shared(0), _swapped_out(0) {}

  void add_mapping(const proc_regioninfo& region_info) {
    _num_mappings++;

    bool is_private = region_info.pri_share_mode == SM_PRIVATE
                   || region_info.pri_share_mode == SM_PRIVATE_ALIASED;
    bool is_shared = region_info.pri_share_mode == SM_SHARED
                   || region_info.pri_share_mode == SM_SHARED_ALIASED
                   || region_info.pri_share_mode == SM_TRUESHARED
                   || region_info.pri_share_mode == SM_COW;
    bool is_committed = region_info.pri_share_mode == SM_EMPTY
                   && region_info.pri_max_protection == VM_PROT_ALL
                   && ((region_info.pri_protection & VM_PROT_DEFAULT) == VM_PROT_DEFAULT);
    bool is_reserved = region_info.pri_share_mode == SM_EMPTY
                   && region_info.pri_max_protection == VM_PROT_ALL
                   && region_info.pri_protection == VM_PROT_NONE;

    _private += is_private ? region_info.pri_size : 0;
    _shared += is_shared ? region_info.pri_size : 0;
    _swapped_out += region_info.pri_pages_swapped_out;
    _committed += is_committed ? region_info.pri_size : 0;
    _reserved += is_reserved ? region_info.pri_size : 0;
  }

  void print_on(const MappingPrintSession& session) const {
    outputStream* st = session.out();

    st->print_cr("Number of mappings: %u", _num_mappings);

    task_vm_info vm_info;
    mach_msg_type_number_t num_out = TASK_VM_INFO_COUNT;
    kern_return_t err = task_info(mach_task_self(), TASK_VM_INFO, (task_info_t)(&vm_info), &num_out);
    if (err == KERN_SUCCESS) {
      st->print_cr("             vsize: %llu (%llu%s)", vm_info.virtual_size, PROPERFMTARGS(vm_info.virtual_size));
      st->print_cr("               rss: %llu (%llu%s)", vm_info.resident_size, PROPERFMTARGS(vm_info.resident_size));
      st->print_cr("          peak rss: %llu (%llu%s)", vm_info.resident_size_peak, PROPERFMTARGS(vm_info.resident_size_peak));
      st->print_cr("         page size: %d (%ld%s)", vm_info.page_size, PROPERFMTARGS((size_t)vm_info.page_size));
    } else {
      st->print_cr("error getting vm_info %d", err);
    }
    st->print_cr("          reserved: %zu (" PROPERFMT ")", _reserved, PROPERFMTARGS(_reserved));
    st->print_cr("         committed: %zu (" PROPERFMT ")", _committed, PROPERFMTARGS(_committed));
    st->print_cr("           private: %zu (" PROPERFMT ")", _private, PROPERFMTARGS(_private));
    st->print_cr("            shared: %zu (" PROPERFMT ")", _shared, PROPERFMTARGS(_shared));
    st->print_cr("       swapped out: %zu (" PROPERFMT ")", _swapped_out * vm_info.page_size, PROPERFMTARGS(_swapped_out * vm_info.page_size));
  }
};

class ProcSmapsPrinter {
  const MappingPrintSession& _session;
public:
  ProcSmapsPrinter(const MappingPrintSession& session) :
    _session(session)
  {}

  void print_single_mapping(const proc_regioninfo& region_info, const MappingInfo& mapping_info) const {
     outputStream* st = _session.out();
#define INDENT_BY(n)          \
  if (st->fill_to(n) == 0) {  \
    st->print(" ");           \
  }
    st->print("%#014.12llx-%#014.12llx", (uint64_t)(mapping_info._address), (uint64_t)(mapping_info._address + mapping_info._size));
    INDENT_BY(38);
    st->print("%12ld", mapping_info._size);
    INDENT_BY(51);
    st->print("%s", mapping_info._protect_buffer.base());
    INDENT_BY(59);
    st->print("%s", mapping_info._share_buffer.base());
    st->print("%s", mapping_info._type_buffer.base());
    INDENT_BY(64);
    st->print("%#11llx", region_info.pri_offset);
    INDENT_BY(77);
    if (_session.print_nmt_info_for_region((const void*)mapping_info._address, (const void*)(mapping_info._address + mapping_info._size))) {
      st->print(" ");
    } else {
      const char* tag = mapping_info._tag_text;
      if (tag != nullptr) {
        st->print("[%s] ", tag);
      }
    }

    st->print_raw(mapping_info._file_name.base());
    st->cr();

#undef INDENT_BY
  }

  void print_legend() const {
    outputStream* st = _session.out();
    st->print_cr("from, to, vsize: address range and size");
    st->print_cr("prot:    protection:");
    st->print_cr("           rwx: read / write / execute");
    st->print_cr("share:   share mode:");
    st->print_cr("           cow: copy on write");
    st->print_cr("           pvt: private");
    st->print_cr("           shr: shared");
    st->print_cr("           tsh: true shared");
    st->print_cr("           p/a: private aliased");
    st->print_cr("           s/a: shared aliased");
    st->print_cr("           lpg: large page");
    st->print_cr("offset:  offset from start of allocation block");
    st->print_cr("vminfo:  VM information (requires NMT)");
    st->print_cr("file:    file mapped, if mapping is not anonymous");
    {
      StreamIndentor si(st, 16);
      _session.print_nmt_flag_legend();
    }
    st->print_cr("file:            file mapped, if mapping is not anonymous");
  }

  void print_header() const {
    outputStream* st = _session.out();
    //            0         1         2         3         4         5         6         7         8         9         0         1         2         3         4         5         6         7
    //            012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
    //            0x000102890000-0x000102898000                32768 r--/r-- cow       0xc000 /Users/simont/dev/openjdk/jdk/build/macos-aarch64-fastdebug-shenandoah/images/jdk/bin/java
    st->print_cr("from               to                        vsize prot    share     offset  vminfo/file");
    st->print_cr("==================================================================================================");
  }
};

static bool is_interesting(const proc_regionwithpathinfo& info) {
   return info.prp_prinfo.pri_share_mode != SM_EMPTY
          || info.prp_prinfo.pri_user_tag != 0
          || info.prp_vip.vip_path[0] != '\0'
          || info.prp_prinfo.pri_protection != 0
          || info.prp_prinfo.pri_max_protection != 0;
}

void MemMapPrinter::pd_print_all_mappings(const MappingPrintSession& session) {

  ProcSmapsPrinter printer(session);
  ProcSmapsSummary summary;
  outputStream* const st = session.out();
  const pid_t pid = getpid();

  printer.print_legend();
  st->cr();
  printer.print_header();

  proc_regionwithpathinfo region_info_with_path;
  MappingInfo mapping_info;
  uint64_t address = 0;
  int region_count = 0;
  while (true) {
    if (++region_count > MAX_REGIONS_RETURNED) {
      st->print_cr("limit of %d regions reached (results inaccurate)", region_count);
      break;
    }
    ::bzero(&region_info_with_path, sizeof(region_info_with_path));
    int retval = proc_pidinfo(pid, PROC_PIDREGIONPATHINFO, (uint64_t)address, &region_info_with_path, sizeof(region_info_with_path));
    if (retval <= 0) {
      break;
    } else if (retval < (int)sizeof(region_info_with_path)) {
      st->print_cr("proc_pidinfo() returned %d", retval);
      assert(false, "proc_pidinfo() returned %d", retval);
    }
    proc_regioninfo& region_info = region_info_with_path.prp_prinfo;
    if (is_interesting(region_info_with_path)) {
      if (mapping_info.canCombine(region_info_with_path)) {
        mapping_info.combineWithFollowing(region_info_with_path);
      } else {
        // print previous mapping info
        // avoid printing the empty info at the start
        if (mapping_info._size != 0) {
          printer.print_single_mapping(region_info, mapping_info);
        }
        summary.add_mapping(region_info);
        mapping_info.process(region_info_with_path);
      }
    }
    assert(region_info.pri_size > 0, "size of region is 0");
    address = region_info.pri_address + region_info.pri_size;
  }
  printer.print_single_mapping(region_info_with_path.prp_prinfo, mapping_info);
  summary.add_mapping(region_info_with_path.prp_prinfo);
  st->cr();
  summary.print_on(session);
  st->cr();
}
#endif // __APPLE__
