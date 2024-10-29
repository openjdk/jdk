/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"

#include "nmt/memMapPrinter.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <libproc.h>
#include <unistd.h>

#include <mach/vm_inherit.h>
#include <mach/vm_prot.h>
#include <mach/mach_vm.h>

/* maximum number of mapping records returned */
static const int MAX_REGIONS_RETURNED = 1000000;

class MappingInfo {
public:
  const char* _address;
  size_t _size;
  stringStream _share_buffer;
  stringStream _type_buffer;
  stringStream _protect_buffer;
  stringStream _file_name;
  const char* _tag_text;

  MappingInfo() {}

  void process(const proc_regionwithpathinfo& mem_info) {
    _share_buffer.reset();
    _protect_buffer.reset();
    _type_buffer.reset();
    _file_name.reset();
    _tag_text = "";

    const proc_regioninfo& rinfo = mem_info.prp_prinfo;

    _address = (const char*) rinfo.pri_address;
    _size = rinfo.pri_size;

    const char* path = mem_info.prp_vip.vip_path;
    if (path != nullptr) {
      _file_name.print_raw(path);
    } else {
      char buf[PATH_MAX];
      buf[0] = '\0';
      proc_regionfilename(getpid(), (uint64_t) _address, buf, sizeof(buf));
      if (buf[0] != 0) {
        buf[sizeof(buf) - 1] = '\0';
        _file_name.print_raw("-> ");
        _file_name.print_raw(buf);
      }
    }

    char prot[4];
    rwbits(rinfo.pri_protection, prot);
    _protect_buffer.print("%s", prot);

    get_share_mode(_share_buffer, rinfo);
    _tag_text = tagToStr(rinfo.pri_user_tag);
  }

  static void get_share_mode(outputStream& out, const proc_regioninfo& rinfo) {
    static const char* share_strings[] = {
      "cow", "pvt", "---", "shr", "tsh", "p/a", "s/a", "lpg"
    };
    const bool valid_share_mode = rinfo.pri_share_mode >= SM_COW && rinfo.pri_share_mode <= SM_LARGE_PAGE;
    if (valid_share_mode) {
      int share_mode = rinfo.pri_share_mode;
      out.print_raw(share_strings[share_mode - 1]);
    } else {
      out.print_cr("invalid pri_share_mode (%d)", rinfo.pri_share_mode);
      assert(valid_share_mode, "invalid pri_share_mode (%d)", rinfo.pri_share_mode);
    }
    if (rinfo.pri_flags & PROC_REGION_SHARED) {
        out.print_raw("-shared");
    }
    if (rinfo.pri_flags & PROC_REGION_SUBMAP) {
        out.print_raw("-submap");
    }
    if ((rinfo.pri_flags & (PROC_REGION_SHARED | PROC_REGION_SUBMAP)) != rinfo.pri_flags) {
      out.print_cr("unhandled pri_flags = 0x%x", rinfo.pri_flags);
      assert(false, "unhandled pri_flags = 0x%x", rinfo.pri_flags);
    }
  }

#define X1(TAG) case VM_MEMORY_ ## TAG: return # TAG;
#define X2(TAG, DESCRIPTION) case VM_MEMORY_ ## TAG: return # DESCRIPTION;
  static const char* tagToStr(uint32_t user_tag) {
    switch (user_tag) {
      case 0:
        return 0;
      X1(MALLOC);
      X1(MALLOC_SMALL);
      X1(MALLOC_LARGE);
      X1(MALLOC_HUGE);
      X1(SBRK);
      X1(REALLOC);
      X1(MALLOC_TINY);
      X1(MALLOC_LARGE_REUSABLE);
      X1(MALLOC_LARGE_REUSED);
      X1(ANALYSIS_TOOL);
      X1(MALLOC_NANO);
      X1(MALLOC_MEDIUM);
      X1(MALLOC_PROB_GUARD);
      X1(MACH_MSG);
      X1(IOKIT);
      X1(STACK);
      X1(GUARD);
      X1(SHARED_PMAP);
      X1(DYLIB);
      X1(UNSHARED_PMAP);
      X2(APPKIT, AppKit);
      X2(FOUNDATION, Foundation);
      X2(COREGRAPHICS, CoreGraphics);
      X2(CORESERVICES, CoreServices); /* is also VM_MEMORY_CARBON */
      X2(JAVA, Java);
      X2(COREDATA, CoreData);
      X1(COREDATA_OBJECTIDS);
      X1(ATS);
      X1(DYLD);
      X1(DYLD_MALLOC);
      X1(SQLITE);
      X1(JAVASCRIPT_CORE);
      X1(JAVASCRIPT_JIT_EXECUTABLE_ALLOCATOR);
      X1(JAVASCRIPT_JIT_REGISTER_FILE);
      X1(OPENCL);
      X2(COREIMAGE, CoreImage);
      X2(IMAGEIO, ImageIO);
      X2(COREPROFILE, CoreProfile);
      X1(APPLICATION_SPECIFIC_1);
      X1(APPLICATION_SPECIFIC_16);
      X1(OS_ALLOC_ONCE);
      X1(GENEALOGY);
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
  size_t _shared;       // combined shared size
  size_t _swapped_out;  // combined amount of swapped-out memory
public:
  ProcSmapsSummary() : _num_mappings(0), _private(0),
                       _committed(0), _shared(0), _swapped_out(0) {}

  void add_mapping(const proc_regioninfo& region_info, const MappingInfo& mapping_info) {
    _num_mappings++;

    bool is_private = region_info.pri_share_mode == SM_PRIVATE
                   || region_info.pri_share_mode == SM_PRIVATE_ALIASED;
    bool is_shared = region_info.pri_share_mode == SM_SHARED
                   || region_info.pri_share_mode == SM_SHARED_ALIASED
                   || region_info.pri_share_mode == SM_TRUESHARED
                   || region_info.pri_share_mode == SM_COW;
    _private += is_private ? region_info.pri_size : 0;
    _shared += is_shared ? region_info.pri_size : 0;
    _swapped_out += region_info.pri_pages_swapped_out;
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
    st->print(PTR_FORMAT "-" PTR_FORMAT, (size_t)mapping_info._address, (size_t)(mapping_info._address + mapping_info._size));
    INDENT_BY(38);
    st->print("%12zu", mapping_info._size);
    INDENT_BY(51);
    st->print("%s", mapping_info._protect_buffer.base());
    INDENT_BY(56);
    st->print("%s", mapping_info._share_buffer.base());
    st->print("%s", mapping_info._type_buffer.base());
    INDENT_BY(61);
    st->print("%#11llx", region_info.pri_offset);
    INDENT_BY(73);
    if (_session.print_nmt_info_for_region((const void*)mapping_info._address, (const void*)(mapping_info._address + mapping_info._size))) {
      st->print(" ");
    } else {
      const char* tag = mapping_info._tag_text;
      if (tag != NULL) {
        st->print("[%s] ", tag);
      }
    }
    st->print_raw(mapping_info._file_name.base());
  #undef INDENT_BY
    st->cr();
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
      streamIndentor si(st, 16);
      _session.print_nmt_flag_legend();
    }
    st->print_cr("file:            file mapped, if mapping is not anonymous");
  }

  void print_header() const {
    outputStream* st = _session.out();
    //            0         1         2         3         4         5         6         7         8         9         0         1         2         3         4         5         6         7
    //            012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
    //            0x00000001714a0000-0x000000017169c000      2080768 rw-/rwx  p/a       0xc000 STACK-28419-C2-CompilerThread0
    st->print_cr("from               to                        vsize prot share     offset vminfo/file");
    st->print_cr("==================================================================================================");
  }
};

void MemMapPrinter::pd_print_all_mappings(const MappingPrintSession& session) {

  ProcSmapsPrinter printer(session);
  ProcSmapsSummary summary;
  outputStream* const st = session.out();
  const pid_t pid = getpid();

  printer.print_legend();
  st->cr();
  printer.print_header();

  proc_regionwithpathinfo region_info;
  MappingInfo mapping_info;
  uint64_t address = 0;
  int region_count = 0;
  while (true) {
    if (++region_count > MAX_REGIONS_RETURNED) {
      st->print_cr("limit of %d regions reached (results inaccurate)", region_count);
      break;
    }
    int retval = proc_pidinfo(pid, PROC_PIDREGIONPATHINFO, (uint64_t)address, &region_info, sizeof(region_info));
    if (retval <= 0) {
      break;
    } else if (retval < (int)sizeof(region_info)) {
      st->print_cr("proc_pidinfo() returned %d", retval);
      assert(false, "proc_pidinfo() returned %d", retval);
    }
    if (region_info.prp_prinfo.pri_share_mode != SM_EMPTY) {
      mapping_info.process(region_info);
      printer.print_single_mapping(region_info.prp_prinfo, mapping_info);
      summary.add_mapping(region_info.prp_prinfo, mapping_info);
    }
    assert(region_info.prp_prinfo.pri_size > 0, "size of region is 0");
    address = region_info.prp_prinfo.pri_address + region_info.prp_prinfo.pri_size;
  }
  st->cr();
  summary.print_on(session);
  st->cr();
}
#endif // __APPLE__