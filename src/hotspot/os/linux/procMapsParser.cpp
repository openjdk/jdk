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

#include "precompiled.hpp"

#include "procMapsParser.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"

static bool is_lowercase_hex(char c) {
  return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
}

static size_t max_mapping_line_len() {
  return 100 + // everything but the file name
         os::vm_page_size() // the file name (kernel limits /proc/pid/cmdline to one page
         ;
}

ProcSmapsParser::ProcSmapsParser(FILE* f) :
  _f(f), _linelen(max_mapping_line_len()), _line(nullptr) {
  assert(_f != nullptr, "Invalid file handle given");
  _line = NEW_C_HEAP_ARRAY(char, max_mapping_line_len(), mtInternal);
  _line[0] = '\0';
}

ProcSmapsParser::~ProcSmapsParser() {
  FREE_C_HEAP_ARRAY(char, _line);
}

bool ProcSmapsParser::read_line() {
  _line[0] = '\0';
  return ::fgets(_line, _linelen, _f) != nullptr;
}

bool ProcSmapsParser::is_header_line() {
  // e.g. ffffffffff600000-ffffffffff601000 --xp 00000000 00:00 0                  [vsyscall]
  return is_lowercase_hex(_line[0]); // All non-header lines in /proc/pid/smaps start with upper-case letters.
}

void ProcSmapsParser::scan_header_line(ProcSmapsInfo& out) {
  const int items_read = ::sscanf(_line, "%p-%p %20s %*s %*s %*s %1024s",
                                  &out.from, &out.to, out.prot, out.filename);
  assert(items_read >= 2, "Expected header_line");
}

void ProcSmapsParser::scan_additional_line(ProcSmapsInfo& out) {
#define SCAN(key, var) \
 if (::sscanf(_line, key ": %zu kB", &var) == 1) { \
     var *= K; \
     return; \
 }
  SCAN("KernelPageSize", out.kernelpagesize);
  SCAN("Rss", out.rss);
  SCAN("AnonHugePages", out.anonhugepages);
  SCAN("Private_Hugetlb", out.private_hugetlb);
  SCAN("Shared_Hugetlb", out.shared_hugetlb);
  SCAN("Swap", out.swap);
  int i = 0;
#undef SCAN
  // scan some flags too
  if (strncmp(_line, "VmFlags:", 8) == 0) {
#define SCAN(flag) { out.flag = (::strstr(_line + 8, " " #flag) != nullptr); }
    SCAN(rd);
    SCAN(wr);
    SCAN(ex);
    SCAN(nr);
    SCAN(sh);
    SCAN(hg);
    SCAN(ht);
    SCAN(nh);
#undef SCAN
  }
}

// Starts or continues parsing. Returns true on success,
// false on EOF or on error.
bool ProcSmapsParser::parse_next(ProcSmapsInfo& out) {

  // Information about a single mapping reaches across several lines.
  out.reset();

  // Read header line, unless we already read it
  if (_line[0] == '\0') {
    if (!read_line()) {
      return false;
    }
  }
  assert(is_header_line(), "Not a header line: \"%s\".", _line);
  scan_header_line(out);

  // Now read until we encounter the next header line or EOF or an error.
  bool ok = false, stop = false;
  do {
    ok = read_line();
    stop = !ok || is_header_line();
    if (!stop) {
      scan_additional_line(out);
    }
  } while (!stop);

  return ok;
}
