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

#include "runtime/os.hpp"
#include "nmt/memMapPrinter.hpp"
#include "utilities/align.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/globalDefinitions.hpp"
#include <limits.h>

static bool is_lowercase_hex(char c) {
  return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
}

struct ProcMapsInfo {
  void* from = 0;
  void* to = 0;
  char prot[20 + 1];
  char filename[1024 + 1];
  size_t kernelpagesize;
  size_t rss;
  size_t private_hugetlb;
  size_t shared_hugetlb;
  size_t anonhugepages;
  size_t swap;
  bool sh; // shared
  bool nr; // no reserve
  bool hg; // thp-advised
  bool ht; // uses hugetlb pages
  bool nh; // thp forbidden
  bool thp_eligible;

  void reset() {
    from = to = nullptr;
    prot[0] = filename[0] = '\0';
    kernelpagesize = rss = private_hugetlb = anonhugepages = swap = 0;
  }

  static bool is_header_line(const char* line) {
    void* dummy;
    //return ::sscanf(line, "%p", &dummy) == 1;
    //return ::sscanf(line, "%p-%p", &dummy, &dummy) == 2;
    return is_lowercase_hex(line[0]); // All other lines start with uppercase letters
  }

  void scan_header_line(const char* line) {
    const int items_read = ::sscanf(line, "%p-%p %20s %*s %*s %*s %1024s",
        &from, &to, prot, filename);
    assert(items_read >= 2, "Expected header_line");
  }

  void scan_additional_lines(const char* line) {
#define SCAN(key, var) \
  if (::sscanf(line, key ": %zu kB", &var) == 1) { \
      var *= K; \
      return; \
  }
    SCAN("KernelPageSize", kernelpagesize);
    SCAN("Rss", rss);
    SCAN("AnonHugePages", anonhugepages);
    SCAN("Private_Hugetlb", private_hugetlb);
    SCAN("Shared_Hugetlb", shared_hugetlb);
    SCAN("Swap", swap);
    int i = 0;
    if (::sscanf(line, "THPeligible: %d", &i) == 1) {
      thp_eligible = (i == 1);
    }
#undef SCAN
    // scan some flags too
    if (strncmp(line, "VmFlags:", 8) == 0) {
#define SCAN(flag) flag = (::strstr(line + 8, " " #flag) != nullptr);
      SCAN(nr);
      SCAN(sh);
      SCAN(hg);
      SCAN(ht);
      SCAN(nh);
#undef SCAN
      return;
    }
  }

  size_t vsize() const {
    return from < to ? pointer_delta(to, from, 1) : 0;
  }

  void print_mapping(MappingPrintSession& session) {
    outputStream* st = session.out();
    int pos = 0;
#define INDENT_BY(n) pos += n; st->fill_to(pos);
    st->print(PTR_FORMAT " - " PTR_FORMAT " ", p2i(from), p2i(to));
    INDENT_BY(40);

    st->print("%10zu", vsize());
    INDENT_BY(11);

    st->print("%10zu", rss);
    INDENT_BY(11);

    st->print("%10zu", private_hugetlb);
    INDENT_BY(11);

    st->print(EXACTFMT " ", EXACTFMTARGS(kernelpagesize));
    INDENT_BY(5);

    st->print("%s ", prot);
    INDENT_BY(5);

    bool comma = false;
#define PRINTIF(cond, s) \
    if (cond) { \
      st->print("%s%s", (comma ? "," : ""), s); \
      comma = true; \
    }
    PRINTIF(anonhugepages > 0, "thp");
    PRINTIF(hg, "thpadv");
    PRINTIF(nh, "nothp");
    PRINTIF(sh, "shrd");
    PRINTIF(ht, "huge");
    PRINTIF(nr, "nores");
    PRINTIF(thp_eligible, "thpel");
    PRINTIF(swap > 0, "swap");
    if (comma) {
      st->print(" ");
    }
#undef PRINTIF
    INDENT_BY(17);
    session.print_nmt_info_for_region(from, to);
    st->print_raw(filename);
#undef INDENT_BY

    st->cr();
  }
};

// A simple histogram for sizes by pagesize. We keep pagesizes in an array by page size bit
// index. Smallest page size we expect is 4k (2^12), largest pagesize we expect is 16G (powerpc)
// - 2^34. So we store 34-12 = 22 sizes.
class PageSizeHistogram {
  static constexpr int log_smallest_pagesize = 12; // 4K
  static constexpr int log_largest_pagesize = 34;  // 16G, ppc
  static constexpr int num_pagesizes = log_largest_pagesize - log_smallest_pagesize;
  size_t _v[num_pagesizes];
public:
  PageSizeHistogram() {
    memset(_v, 0, sizeof(_v));
  }
  void add(size_t pagesize, size_t size) {
    assert(is_aligned(size, pagesize), "strange");
    const int n = exact_log2(pagesize) - log_smallest_pagesize;
    assert(n >= 0 && n < num_pagesizes, "strange");
    _v[n] += size;
  }
  void print_on(MappingPrintSession& session) {
    outputStream* st = session.out();
    for (int i = 0; i < num_pagesizes; i++) {
      if (_v[i] > 0) {
        const size_t pagesize = 1 << (log_smallest_pagesize + i);
        st->fill_to(16);
        st->print(EXACTFMT, EXACTFMTARGS(pagesize));
        st->print_cr(": %zu pages, %zu bytes (" PROPERFMT ")", _v[i] / pagesize, _v[i], PROPERFMTARGS(_v[i]));
      }
    }
  }
};

class Summary {
  unsigned _num_mappings;
  size_t _vsize;
  size_t _rss;
  size_t _committed;
  size_t _shared;
  size_t _swapped_out;
  size_t _hugetlb;
  size_t _thp;
  PageSizeHistogram _pagesizes;

public:

  Summary() : _num_mappings(0), _vsize(0), _rss(0), _committed(0), _shared(0),
              _swapped_out(0), _hugetlb(0), _thp(0) {}

  void add_mapping(ProcMapsInfo& info) {
    _num_mappings ++;
    _vsize += info.vsize();
    _rss += info.rss;
    _committed += info.nr ? 0 : info.vsize();
    _shared += info.sh ? info.vsize() : 0;
    _swapped_out += info.swap;
    _hugetlb += info.private_hugetlb + info.shared_hugetlb;
    _thp += info.anonhugepages;
    if (info.ht) {
      _pagesizes.add(info.kernelpagesize,
                     info.private_hugetlb + info.shared_hugetlb);
    } else {
      _pagesizes.add(info.kernelpagesize, info.rss); // only resident pages
    }
  }

  void print_on(MappingPrintSession& session) {
    outputStream* st = session.out();
    st->print_cr("Number of mappings: %u", _num_mappings);
    st->print_cr("             vsize: %zu (" PROPERFMT ")", _vsize, PROPERFMTARGS(_vsize));
    st->print_cr("               rss: %zu (" PROPERFMT ")", _rss, PROPERFMTARGS(_rss));
    st->print_cr("         committed: %zu (" PROPERFMT ")", _committed, PROPERFMTARGS(_committed));
    st->print_cr("            shared: %zu (" PROPERFMT ")", _shared, PROPERFMTARGS(_shared));
    st->print_cr("       swapped out: %zu (" PROPERFMT ")", _swapped_out, PROPERFMTARGS(_swapped_out));
    st->print_cr("         using thp: %zu (" PROPERFMT ")", _thp, PROPERFMTARGS(_thp));
    st->print_cr("           hugetlb: %zu (" PROPERFMT ")", _hugetlb, PROPERFMTARGS(_hugetlb));
    st->print_cr("By page size:");
    _pagesizes.print_on(session);
  }
};

static void print_legend(MappingPrintSession& session) {
  outputStream* st = session.out();
  st->print_cr("from, to, size: address range and size");
  st->print_cr("rss:            resident set size");
  st->print_cr("pgsz:           page size");
  st->print_cr("notes:          mapping information");
  st->print_cr("                    shared: mapping is shared");
  st->print_cr("                     nores: mapping uncommitted (no swap space reserved)");
  st->print_cr("                      swap: mapping partly or completely swapped out");
  st->print_cr("                       thp: mapping uses THP");
  st->print_cr("                     thpel: mapping is eligible for THP");
  st->print_cr("                    thpadv: mapping is THP-madvised");
  st->print_cr("                     nothp: mapping will not THP");
  st->print_cr("                      huge: mapping uses hugetlb pages");
  st->print_cr("vm info:        VM information (requires NMT)");
  session.print_nmt_flag_legend(16);
  st->print_cr("file:           file mapped, if mapping is not anonymous");
}

static void print_header(MappingPrintSession& session) {
  outputStream* st = session.out();
  //            .         .         .         .         .         .         .         .         .         .         .
  //            01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
  //            0x0000000414000000 - 0x0000000453000000 1234567890 1234567890 1234567890 16g  rw-p thp,thpadv       JAVAHEAP /shared/tmp.txt
  st->print_cr("from                 to                       size        rss    hugetlb pgsz prot notes            vm-info file");
}

void MemMapPrinter::pd_print_all_mappings(MappingPrintSession& session) {
  outputStream* st = session.out();

  const bool print_individual_mappings = !session.summary_only();

  if (!session.summary_only()) {
    print_legend(session);
    st->cr();
    print_header(session);
  }

  FILE* f = os::fopen("/proc/self/smaps", "r");
  if (f == nullptr) {
    return;
  }

  Summary summary;

  constexpr size_t linesize = sizeof(ProcMapsInfo);
  char line[linesize];
  int lines_scanned = 0;
  ProcMapsInfo info;

  while (fgets(line, sizeof(line), f) == line) {
    line[sizeof(line) - 1] = '\0';
    if (info.is_header_line(line)) {
      if (lines_scanned > 0) {
        summary.add_mapping(info);
        if (print_individual_mappings) {
          info.print_mapping(session);
        }
      }
      info.reset();
      info.scan_header_line(line);
    } else {
      info.scan_additional_lines(line);
    }
    lines_scanned ++;
  }

  if (lines_scanned > 0) {
    summary.add_mapping(info);
    if (print_individual_mappings) {
      info.print_mapping(session);
    }
  }

  ::fclose(f);

  // print summary
  st->cr();
  summary.print_on(session);
}
