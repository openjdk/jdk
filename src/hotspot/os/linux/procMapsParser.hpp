/*
 * Copyright (c) 2024, Red Hat, Inc. and/or its affiliates.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_LINUX_PROCMAPSPARSER_HPP
#define OS_LINUX_PROCMAPSPARSER_HPP

#include "utilities/globalDefinitions.hpp"

// This header exposes two simple parsers for /proc/pid/maps and
// /proc/pid/smaps.
//
// Usage:
//
// FILE* f = fopen(...)
// ProcSMapsParser parser(f);
// ProcSMapsInfo info;
// while (parser.parse_next(info)) { ... }

struct ProcSmapsInfo {
  void* from;
  void* to;
  char prot[20 + 1];
  char filename[1024 + 1];
  size_t kernelpagesize;
  size_t rss;
  size_t private_hugetlb;
  size_t shared_hugetlb;
  size_t anonhugepages;
  size_t swap;
  bool rd, wr, ex;
  bool sh; // shared
  bool nr; // no reserve
  bool hg; // thp-advised
  bool ht; // uses hugetlb pages
  bool nh; // thp forbidden

  size_t vsize() const {
    return from < to ? pointer_delta(to, from, 1) : 0;
  }

  void reset() {
    from = to = nullptr;
    prot[0] = filename[0] = '\0';
    kernelpagesize = rss = private_hugetlb = shared_hugetlb = anonhugepages = swap = 0;
    rd = wr = ex = sh = nr = hg = ht = nh = false;
  }
};

class ProcSmapsParser {
  FILE* _f;
  const size_t _linelen;
  char* _line;

  bool read_line(); // sets had_error in case of error
  bool is_header_line();
  void scan_header_line(ProcSmapsInfo& out);
  void scan_additional_line(ProcSmapsInfo& out);

public:

  ProcSmapsParser(FILE* f);
  ~ProcSmapsParser();

  // Starts or continues parsing. Returns true on success,
  // false on EOF or on error.
  bool parse_next(ProcSmapsInfo& out);
};

#endif // OS_LINUX_PROCMAPSPARSER_HPP
