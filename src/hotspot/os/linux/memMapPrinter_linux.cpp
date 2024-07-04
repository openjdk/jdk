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

#include "precompiled.hpp"

#include "nmt/memMapPrinter.hpp"
#include "procMapsParser.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

#include <limits.h>

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
  void add_mapping(const ProcSmapsInfo& info) {
    _num_mappings++;
    _vsize += info.vsize();
    _rss += info.rss;
    _committed += info.nr ? 0 : info.vsize();
    _shared += info.sh ? info.vsize() : 0;
    _swapped_out += info.swap;
    _hugetlb += info.private_hugetlb + info.shared_hugetlb;
    _thp += info.anonhugepages;
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

  void print_single_mapping(const ProcSmapsInfo& info) const {
    outputStream* st = _session.out();
#define INDENT_BY(n)          \
  if (st->fill_to(n) == 0) {  \
    st->print(" ");           \
  }
    st->print(PTR_FORMAT "-" PTR_FORMAT, p2i(info.from), p2i(info.to));
    INDENT_BY(38);
    st->print("%12zu", info.vsize());
    INDENT_BY(51);
    st->print("%s", info.prot);
    INDENT_BY(56);
    st->print("%12zu", info.rss);
    INDENT_BY(69);
    st->print("%12zu", info.private_hugetlb);
    INDENT_BY(82);
    st->print(EXACTFMT, EXACTFMTARGS(info.kernelpagesize));
    {
      INDENT_BY(87);
      int num_printed = 0;
#define PRINTIF(cond, s)                                    \
      if (cond) {                                           \
        st->print("%s%s", (num_printed > 0 ? "," : ""), s); \
        num_printed++;                                      \
      }
      PRINTIF(info.sh, "shrd");
      PRINTIF(!info.nr, "com");
      PRINTIF(info.swap > 0, "swap");
      PRINTIF(info.ht, "huge");
      PRINTIF(info.anonhugepages > 0, "thp");
      PRINTIF(info.hg, "thpad");
      PRINTIF(info.nh, "nothp");
      if (num_printed == 0) {
        st->print("-");
      }
#undef PRINTIF
    }
    INDENT_BY(104);
    if (!_session.print_nmt_info_for_region(info.from, info.to)) {
      st->print("-");
    }
    INDENT_BY(142);
    st->print_raw(info.filename[0] == '\0' ? "-" : info.filename);
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
  constexpr char filename[] = "/proc/self/smaps";
  FILE* f = os::fopen(filename, "r");
  if (f == nullptr) {
    session.out()->print_cr("Cannot open %s", filename);
    return;
  }

  ProcSmapsPrinter printer(session);
  ProcSmapsSummary summary;

  outputStream* const st = session.out();

  printer.print_legend();
  st->cr();
  printer.print_header();

  ProcSmapsInfo info;
  ProcSmapsParser parser(f);
  while (parser.parse_next(info)) {
    printer.print_single_mapping(info);
    summary.add_mapping(info);
  }
  st->cr();

  summary.print_on(session);
  st->cr();

  ::fclose(f);
}
