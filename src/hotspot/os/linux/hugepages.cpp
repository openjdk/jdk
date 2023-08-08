/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2011, 2023, Red Hat Inc. All rights reserved.
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
#include "hugepages.hpp"

#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

#include <dirent.h>

StaticHugePageSupport::StaticHugePageSupport() :
  _initialized(false), _pagesizes(), _default_hugepage_size(SIZE_MAX), _inconsistent(false) {}

os::PageSizes StaticHugePageSupport::pagesizes() const {
  assert(_initialized, "Not initialized");
  return _pagesizes;
}

size_t StaticHugePageSupport::default_hugepage_size() const {
  assert(_initialized, "Not initialized");
  return _default_hugepage_size;
}

// Scan /proc/meminfo and return value of Hugepagesize
static size_t scan_default_hugepagesize() {
  size_t pagesize = 0;

  // large_page_size on Linux is used to round up heap size. x86 uses either
  // 2M or 4M page, depending on whether PAE (Physical Address Extensions)
  // mode is enabled. AMD64/EM64T uses 2M page in 64bit mode. IA64 can use
  // page as large as 1G.
  //
  // Here we try to figure out page size by parsing /proc/meminfo and looking
  // for a line with the following format:
  //    Hugepagesize:     2048 kB
  //
  // If we can't determine the value (e.g. /proc is not mounted, or the text
  // format has been changed), we'll set largest page size to 0

  FILE *fp = os::fopen("/proc/meminfo", "r");
  if (fp) {
    while (!feof(fp)) {
      int x = 0;
      char buf[16];
      if (fscanf(fp, "Hugepagesize: %d", &x) == 1) {
        if (x && fgets(buf, sizeof(buf), fp) && strcmp(buf, " kB\n") == 0) {
          pagesize = x * K;
          break;
        }
      } else {
        // skip to next line
        for (;;) {
          int ch = fgetc(fp);
          if (ch == EOF || ch == (int)'\n') break;
        }
      }
    }
    fclose(fp);
  }

  return pagesize;
}

// Given a file that contains a single (integral) number, return that number in (*out) and true;
// in case of an error, return false.
static bool read_number_file(const char* file, size_t* out) {
  FILE* f = ::fopen(file, "r");
  bool rc = false;
  if (f != nullptr) {
    uint64_t i = 0;
    if (::fscanf(f, SIZE_FORMAT, out) == 1) {
      rc = true;
    }
    ::fclose(f);
  }
  return rc;
}

static const char* const sys_hugepages = "/sys/kernel/mm/hugepages";

// Scan all directories in /sys/kernel/mm/hugepages/hugepages-xxxx
// to discover the available page sizes
static os::PageSizes scan_hugepages() {

  os::PageSizes pagesizes;

  DIR* dir = opendir(sys_hugepages);

  if (dir != nullptr) {
    struct dirent *entry;
    size_t pagesize;
    while ((entry = readdir(dir)) != nullptr) {
      if (entry->d_type == DT_DIR &&
          sscanf(entry->d_name, "hugepages-%zukB", &pagesize) == 1) {
        // The kernel is using kB, hotspot uses bytes
        // Add each found Large Page Size to page_sizes
        pagesize *= K;
        pagesizes.add(pagesize);
      }
    }
    closedir(dir);
  }

  return pagesizes;
}

void StaticHugePageSupport::print_on(outputStream* os) {
  if (_initialized) {
    os->print_cr("Static hugepage support:");
    for (size_t s = _pagesizes.smallest(); s != 0; s = _pagesizes.next_larger(s)) {
      os->print_cr("  hugepage size: " EXACTFMT, EXACTFMTARGS(s));
    }
    os->print_cr("  default hugepage size: " EXACTFMT, EXACTFMTARGS(_default_hugepage_size));
  } else {
    os->print_cr("  unknown.");
  }
  if (_inconsistent) {
    os->print_cr("  Support inconsistent. JVM will not use static hugepages.");
  }
}

void StaticHugePageSupport::scan_os() {
  _default_hugepage_size = scan_default_hugepagesize();
  if (_default_hugepage_size > 0) {
    _pagesizes = scan_hugepages();
    // See https://www.kernel.org/doc/Documentation/vm/hugetlbpage.txt: /proc/meminfo should match
    // /sys/kernel/mm/hugepages/hugepages-xxxx. However, we may run on a broken kernel (e.g. on WSL)
    // that only exposes /proc/meminfo but not /sys/kernel/mm/hugepages. In that case, we are not
    // sure about the state of hugepage support by the kernel, so we won't use static hugepages.
    if (!_pagesizes.contains(_default_hugepage_size)) {
      log_info(pagesize)("Unexpected configuration: default pagesize (" SIZE_FORMAT ") "
                         "has no associated directory in /sys/kernel/mm/hugepages..", _default_hugepage_size);
      _inconsistent = true;
    }
  }
  _initialized = true;
  LogTarget(Info, pagesize) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    print_on(&ls);
  }
}

THPSupport::THPSupport() :
    _initialized(false), _mode(THPMode::never), _pagesize(SIZE_MAX) {}


THPMode THPSupport::mode() const {
  assert(_initialized, "Not initialized");
  return _mode;
}

size_t THPSupport::pagesize() const {
  assert(_initialized, "Not initialized");
  return _pagesize;
}

void THPSupport::scan_os() {
  // Scan /sys/kernel/mm/transparent_hugepage/enabled
  // see mm/huge_memory.c
  _mode = THPMode::never;
  const char* filename = "/sys/kernel/mm/transparent_hugepage/enabled";
  FILE* f = ::fopen(filename, "r");
  if (f != nullptr) {
    char buf[64];
    char* s = fgets(buf, sizeof(buf), f);
    assert(s == buf, "Should have worked");
    if (::strstr(buf, "[madvise]") != nullptr) {
      _mode = THPMode::madvise;
    } else if (::strstr(buf, "[always]") != nullptr) {
      _mode = THPMode::always;
    } else {
      assert(::strstr(buf, "[never]") != nullptr, "Weird content of %s: %s", filename, buf);
    }
    fclose(f);
  }

  // Scan large page size for THP from hpage_pmd_size
  _pagesize = 0;
  if (read_number_file("/sys/kernel/mm/transparent_hugepage/hpage_pmd_size", &_pagesize)) {
    assert(_pagesize > 0, "Expected");
  }
  _initialized = true;

  LogTarget(Info, pagesize) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    print_on(&ls);
  }
}

void THPSupport::print_on(outputStream* os) {
  if (_initialized) {
    os->print_cr("Transparent hugepage (THP) support:");
    os->print_cr("  THP mode: %s",
        (_mode == THPMode::always ? "always" : (_mode == THPMode::never ? "never" : "madvise")));
    os->print_cr("  THP pagesize: " EXACTFMT, EXACTFMTARGS(_pagesize));
  } else {
    os->print_cr("  unknown.");
  }
}

StaticHugePageSupport HugePages::_static_hugepage_support;
THPSupport HugePages::_thp_support;

void HugePages::initialize() {
  _static_hugepage_support.scan_os();
  _thp_support.scan_os();
}

void HugePages::print_on(outputStream* os) {
  _static_hugepage_support.print_on(os);
  _thp_support.print_on(os);
}
