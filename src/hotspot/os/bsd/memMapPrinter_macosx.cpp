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
      buf[0] = 0;
      proc_regionfilename(getpid(), (uint64_t) _address, buf, sizeof(buf));
      if (buf[0] != 0) {
        _file_name.print_raw("-> ");
        _file_name.print_raw(buf);
      }
    }

    char prot[4];
    char max_prot[4];
    rwbits(rinfo.pri_protection, prot);
    rwbits(rinfo.pri_max_protection, max_prot);
    _protect_buffer.print("%s/%s", prot, max_prot);

    get_share_mode(_share_buffer, rinfo);
    _tag_text = tagToStr(rinfo.pri_user_tag);
  }

  static void get_share_mode(outputStream& out, const proc_regioninfo& rinfo) {
    static const char* share_strings[] = {
      "cow", "prv", "---", "shr", "tsh", "p/a", "s/a", "lpg"
    };
    const bool valid_share_mode = rinfo.pri_share_mode >= SM_COW && rinfo.pri_share_mode <= SM_LARGE_PAGE;
    assert(valid_share_mode, "invalid pri_share_mode (%d)", rinfo.pri_share_mode);
    if (valid_share_mode) {
      int share_mode = rinfo.pri_share_mode;
      //share_mode = share_mode == SM_LARGE_PAGE || share_mode == SM_PRIVATE_ALIASED ? SM_PRIVATE;
      out.print_raw(share_strings[share_mode - 1]);
    } else {
      out.print_raw("???");
    }
    if (rinfo.pri_flags & PROC_REGION_SHARED) {
        out.print_raw("-shared");
    }
    if (rinfo.pri_flags & PROC_REGION_SUBMAP) {
        out.print_raw("-submap");
    }
    if ((rinfo.pri_flags & (PROC_REGION_SHARED | PROC_REGION_SUBMAP)) != rinfo.pri_flags) {
      out.print("***** flags = 0x%x", rinfo.pri_flags);
    }
  }

  static const char* get_inherit_mode(int mode) {
      switch (mode) {
        case VM_INHERIT_COPY:
          return "copy";
        case VM_INHERIT_SHARE:
          return "share";
        case VM_INHERIT_NONE:
          return "none";
        case VM_INHERIT_DONATE_COPY:
          return "copy-and-delete";
        default:
          return "(unknown)";
      }
  }

  static const char* tagToStr(uint32_t user_tag) {
    switch (user_tag) {
      case 0:
        return 0;
      case VM_MEMORY_MALLOC:
        return "MALLOC";
      case VM_MEMORY_MALLOC_SMALL:
        return "MALLOC_SMALL";
      case VM_MEMORY_MALLOC_LARGE:
        return "MALLOC_LARGE";
      case VM_MEMORY_MALLOC_HUGE:
        return "MALLOC_HUGE";
      case VM_MEMORY_SBRK:
        return "SBRK";
      case VM_MEMORY_REALLOC:
        return "REALLOC";
      case VM_MEMORY_MALLOC_TINY:
        return "MALLOC_TINY";
      case VM_MEMORY_MALLOC_LARGE_REUSABLE:
        return "MALLOC_LARGE_REUSABLE";
      case VM_MEMORY_MALLOC_LARGE_REUSED:
        return "MALLOC_LARGE_REUSED";
      case VM_MEMORY_ANALYSIS_TOOL:
        return "ANALYSIS_TOOL";
      case VM_MEMORY_MALLOC_NANO:
        return "MALLOC_NANO";
      case VM_MEMORY_MALLOC_MEDIUM:
        return "MALLOC_MEDIUM";
      case VM_MEMORY_MALLOC_PROB_GUARD:
        return "MALLOC_PROB_GUARD";
      case VM_MEMORY_MACH_MSG:
        return "MACH_MSG";
      case VM_MEMORY_IOKIT:
        return "IOKIT";
      case VM_MEMORY_STACK:
        return "STACK";
      case VM_MEMORY_GUARD:
        return "MEMORY_GUARD";
      case VM_MEMORY_SHARED_PMAP:
        return "SHARED_PMAP";
      case VM_MEMORY_DYLIB:
        return "DYLIB";
      case VM_MEMORY_UNSHARED_PMAP:
        return "UNSHARED_PMAP";
      case VM_MEMORY_APPKIT:
        return "AppKit";
      case VM_MEMORY_FOUNDATION:
        return "Foundation";
      case VM_MEMORY_COREGRAPHICS:
        return "CoreGraphics";
      case VM_MEMORY_CARBON: /* is also VM_MEMORY_CORESERVICES */
        return "Carbon";
      case VM_MEMORY_JAVA:
        return "Java";
      case VM_MEMORY_COREDATA:
        return "CoreData";
      case VM_MEMORY_COREDATA_OBJECTIDS:
        return "COREDATA_OBJECTIDS";
      case VM_MEMORY_ATS:
        return "ATS";
      case VM_MEMORY_DYLD:
        return "DYLD";
      case VM_MEMORY_DYLD_MALLOC:
        return "DYLD_MALLOC";
      case VM_MEMORY_SQLITE:
        return "SQLITE";
      case VM_MEMORY_JAVASCRIPT_CORE:
        return "JAVASCRIPT_CORE";
      case VM_MEMORY_JAVASCRIPT_JIT_EXECUTABLE_ALLOCATOR:
        return "JAVASCRIPT_JIT_EXECUTABLE_ALLOCATOR";
      case VM_MEMORY_JAVASCRIPT_JIT_REGISTER_FILE:
        return "JAVASCRIPT_JIT_REGISTER_FILE";
      case VM_MEMORY_OPENCL:
        return "OPENCL";
      case VM_MEMORY_COREIMAGE:
        return "CoreImage";
      case VM_MEMORY_IMAGEIO:
        return "ImageIO";
      case VM_MEMORY_COREPROFILE:
        return "CoreProfile";
      case VM_MEMORY_APPLICATION_SPECIFIC_1:
        return "APPLICATION_SPECIFIC_1";
      case VM_MEMORY_APPLICATION_SPECIFIC_16:
        return "APPLICATION_SPECIFIC_16";
      case VM_MEMORY_OS_ALLOC_ONCE:
        return "OS_ALLOC_ONCE";
      case VM_MEMORY_GENEALOGY:
        return "GENEALOGY";
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
  size_t _vsize;        // combined virtual size
  size_t _rss;          // combined resident set size
  size_t _committed;    // combined committed size
  size_t _shared;       // combined shared size
  size_t _swapped_out;  // combined amount of swapped-out memory
  size_t _hugetlb;      // combined amount of memory backed by explicit huge pages
  size_t _thp;          // combined amount of memory backed by THPs
public:
  ProcSmapsSummary() : _num_mappings(0), _vsize(0), _rss(0), _committed(0), _shared(0),
                     _swapped_out(0), _hugetlb(0), _thp(0) {}
  void add_mapping(const proc_regioninfo& region_info, const MappingInfo& mapping_info) {
    _num_mappings++;
    _vsize += mapping_info._size;
    _rss += region_info.pri_pages_resident;
   // _committed += region_info.pri_pages_resident + region_info.pri_pages_swapped_out;
    _shared += region_info.pri_shared_pages_resident;
    _swapped_out += region_info.pri_pages_swapped_out;
  //  _hugetlb += region_info.pri_;
   //
   // _thp += info.anonhugepages;
  }

  void print_on(const MappingPrintSession& session) const {
    outputStream* st = session.out();
    st->print_cr("Number of mappings: %u", _num_mappings);
    st->print_cr("             vsize: %zu (" PROPERFMT ")", _vsize, PROPERFMTARGS(_vsize));
    st->print_cr("               rss: %zu (" PROPERFMT ")", _rss, PROPERFMTARGS(_rss));
    st->print_cr("         committed: %zu (" PROPERFMT ")", _committed, PROPERFMTARGS(_committed));
    st->print_cr("            shared: %zu (" PROPERFMT ")", _shared, PROPERFMTARGS(_shared));
    st->print_cr("       swapped out: %zu (" PROPERFMT ")", _swapped_out, PROPERFMTARGS(_swapped_out));
    st->print_cr("         using thp: %zu (" PROPERFMT ")", _thp, PROPERFMTARGS(_thp));
    st->print_cr("           hugetlb: %zu (" PROPERFMT ")", _hugetlb, PROPERFMTARGS(_hugetlb));
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
    st->print("%s", mapping_info._protect_buffer.base());
    INDENT_BY(45);
    st->print("%s", mapping_info._share_buffer.base());
    st->print("%s", mapping_info._type_buffer.base());
    INDENT_BY(55);
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
    st->print_cr("prot:            protection");
    st->print_cr("rss:             resident set size");
    st->print_cr("hugetlb:         size of private hugetlb pages");
    st->print_cr("pgsz:            page size");
    st->print_cr("notes:           mapping information  (detail mode only)");
    st->print_cr("                      shrd: mapping is shared");
    st->print_cr("                       com: mapping committed (swap space reserved)");
    st->print_cr("                      swap: mapping partly or completely swapped out");
    st->print_cr("                       thp: mapping uses THP");
    st->print_cr("                     thpad: mapping is THP-madvised");
    st->print_cr("                     nothp: mapping is forbidden to use THP");
    st->print_cr("                      huge: mapping uses hugetlb pages");
    st->print_cr("vminfo:          VM information (requires NMT)");
    st->print_cr("file:            file mapped, if mapping is not anonymous");
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
    //            0x0000000100ce8000-0x0000000100cf0000 rw-/rwx shr      INTERN /private/var/folders/lj/2hwcbc415h97gjtdm2rdgj7m0000gn/T/hsperfdata_simont/87218
    st->print_cr("from               to                 prot             vminfo/file");
    st->print_cr("======================================================================================================");
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
  const char* address = 0;
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
      fatal("proc_pidinfo() returned %d", retval);
    }
    mapping_info.process(region_info);
    if (region_info.prp_prinfo.pri_share_mode != SM_EMPTY) {
      printer.print_single_mapping(region_info.prp_prinfo, mapping_info);
    }
    summary.add_mapping(region_info.prp_prinfo, mapping_info);
    assert(mapping_info._size > 0, "size of region is 0");
    address = mapping_info._address + mapping_info._size;
  }
  st->cr();
  summary.print_on(session);
  st->cr();
}

#endif // __APPLE__