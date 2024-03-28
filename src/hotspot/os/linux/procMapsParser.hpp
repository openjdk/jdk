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
// ProcMapsParser parser(f);
// ProcMapsInfo info;
// while (parser.parse_next(info)) { ... }

struct ProcMapsInfo {
  void* from;
  void* to;
  char prot[20 + 1];
  char filename[1024 + 1];
  inline virtual void reset();
  inline size_t vsize() const;
};

struct ProcSmapsInfo : public ProcMapsInfo {
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
  bool thp_eligible;
  inline void reset() override;
};

class ProcMapsParserBase {
  FILE* _f;
  bool _had_error;
protected:
  const size_t _linelen;
  char* _line;
  bool read_line(); // sets had_error in case of error
public:
  ProcMapsParserBase(FILE* f);
  ~ProcMapsParserBase();
  bool had_error() const { return _had_error; }
};

class ProcMapsParser : public ProcMapsParserBase {
public:
  ProcMapsParser(FILE* f) : ProcMapsParserBase(f) {}
  // Starts or continues parsing. Returns true on success,
  // false on EOF or on error.
  bool parse_next(ProcMapsInfo& out);
};

class ProcSmapsParser : public ProcMapsParserBase {
  bool is_header_line();
  void scan_header_line(ProcSmapsInfo& out);
  void scan_additional_line(ProcSmapsInfo& out);
public:
  ProcSmapsParser(FILE* f) : ProcMapsParserBase(f) {}
  // Starts or continues parsing. Returns true on success,
  // false on EOF or on error.
  bool parse_next(ProcSmapsInfo& out);
};

#endif // OS_LINUX_PROCMAPSPARSER_HPP
