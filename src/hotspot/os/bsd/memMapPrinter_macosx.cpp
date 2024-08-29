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
  stringStream _ap_buffer;
  stringStream _state_buffer;
  stringStream _type_buffer;
  stringStream _protect_buffer;
  stringStream _file_name;

  MappingInfo() {}

  void process(const proc_regionwithpathinfo& mem_info) {
    _ap_buffer.reset();
    _state_buffer.reset();
    _protect_buffer.reset();
    _type_buffer.reset();
    _file_name.reset();

    const char *path = mem_info.prp_vip.vip_path;
    if (path != nullptr) {
      _file_name.print("%s", path);
    }

    const proc_regioninfo& rinfo = mem_info.prp_prinfo;
    char prot[4];
    char max_prot[4];
    rwbits(rinfo.pri_protection, prot);
    rwbits(rinfo.pri_max_protection, max_prot);
    _protect_buffer.print("%s/%s", prot, max_prot);

    _state_buffer.print("%s", sharemode(rinfo));

    if (mem_info.prp_vip.vip_path[0] == 0 && mem_info.prp_vip.vip_vi.vi_stat.vst_ino != 0) {
      _ap_buffer.print("path null inode %lld", mem_info.prp_vip.vip_vi.vi_stat.vst_ino);
    }
  }

  const char* sharemode(const proc_regioninfo& rinfo) {
    static const char* share_strings[] = {
      "cow", "prv", "---", "shr", "tsh", "pva", "sha", "lrg"
    };
    assert(rinfo.pri_share_mode >= SM_COW && rinfo.pri_share_mode <= SM_LARGE_PAGE, "invalid pri_share_mode (%d)", rinfo.pri_share_mode);
    return share_strings[rinfo.pri_share_mode - 1];
  }

  void rwbits(int rw, char bits[4]) {
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
    st->print(PTR_FORMAT "-" PTR_FORMAT, (size_t)region_info.pri_address, (size_t)(region_info.pri_address + region_info.pri_size));
    INDENT_BY(38);
    st->print("%12zu", (size_t)region_info.pri_size);
    INDENT_BY(51);
    st->print("%s", mapping_info._protect_buffer.base());
    INDENT_BY(58);
    st->print("%s-%s", mapping_info._state_buffer.base(), mapping_info._type_buffer.base());
    INDENT_BY(60);
   // st->print("%#9llx", reinterpret_cast<const unsigned long long>(mem_info.BaseAddress) - reinterpret_cast<const unsigned long long>(mem_info.AllocationBase));
  //  INDENT_BY(72);
    if (_session.print_nmt_info_for_region((const void*)region_info.pri_address, (const void*)(region_info.pri_address + region_info.pri_size))) {
      st->print(" ");
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
    st->print_cr("vm info:         VM information (requires NMT)");
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
    //            0x0000000414000000-0x0000000453000000 123456789012 rw-p 123456789012 123456789012 16g  thp,thpadv       STACK-340754-Monitor-Deflation-Thread /shared/tmp.txt
    st->print_cr("from               to                        vsize prot          rss      hugetlb pgsz notes            info                                  file");
    st->print_cr("========================================================================================================================================================================");
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
    int retval = proc_pidinfo(pid, PROC_PIDREGIONPATHINFO, address, &region_info, sizeof(region_info));
    if (retval <= 0) {
      break;
    } else if (retval < sizeof(region_info)) {
      fatal("proc_pidinfo() returned %d", retval);
    }
    mapping_info.process(region_info);
    printer.print_single_mapping(region_info.prp_prinfo, mapping_info);
    summary.add_mapping(region_info.prp_prinfo, mapping_info);
    assert(region_info.prp_prinfo.pri_size > 0, "size of region is 0");
    address = region_info.prp_prinfo.pri_address + region_info.prp_prinfo.pri_size;
  }
  st->cr();
  summary.print_on(session);
  st->cr();
}

#endif // __APPLE__