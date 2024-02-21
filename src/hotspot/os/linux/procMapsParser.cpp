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

#include "procMapsParser.inline.hpp"
#include "utilities/globalDefinitions.hpp"

static bool is_lowercase_hex(char c) {
  return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
}

ProcMapsParserBase::ProcMapsParserBase(FILE* f) : _f(f), _had_error(false) {
  _line[0] = '\0';
  assert(_f != nullptr, "Invalid file handle given");
}

bool ProcMapsParserBase::read_line() {
  assert(!_had_error, "Don't call in error state");
  if (::fgets(_line, sizeof(_line), _f) == nullptr) {
    _had_error = (::ferror(_f) != 0);
    return false;
  }
  return true;
}

// Starts or continues parsing. Returns true on success,
// false on EOF or on error.
bool ProcMapsParser::parse_next(ProcMapsInfo& out) {
  bool success = false;
  out.reset();
  while (success == false) {
    if (!read_line()) {
      return false;
    }
    const int items_read = ::sscanf(_line, "%p-%p %20s %*20s %*20s %*20s %1024s",
        &out.from, &out.to, out.prot, out.filename);
    success = (items_read >= 2);
  }
  return true;
}

bool ProcSmapsParser::is_header_line() {
  return is_lowercase_hex(_line[0]); // All other lines start with uppercase letters
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
  if (::sscanf(_line, "THPeligible: %d", &i) == 1) {
    out.thp_eligible = (i == 1);
  }
#undef SCAN
  // scan some flags too
  if (strncmp(_line, "VmFlags:", 8) == 0) {
#define SCAN(flag) flag = (::strstr(_line + 8, " " #flag) != nullptr);
    SCAN(out.nr);
    SCAN(out.sh);
    SCAN(out.hg);
    SCAN(out.ht);
    SCAN(out.nh);
#undef SCAN
    return;
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
  assert(is_header_line(), "Not a header line?");
  scan_header_line(out);
  // Now read until we encounter the next header line or EOF or an error.
  bool stop = false;
  do {
    stop = !read_line() || is_header_line();
    if (!stop) {
      scan_additional_line(out);
    }
  } while (!stop);

  return !had_error();
}
