/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2011, 2024, Red Hat Inc. All rights reserved.
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

#ifndef OS_LINUX_HUGEPAGES_HPP
#define OS_LINUX_HUGEPAGES_HPP

#include "memory/allStatic.hpp"
#include "runtime/os.hpp" // for os::PageSizes
#include "utilities/globalDefinitions.hpp"

class outputStream;

// Header contains the interface that reads OS information about
// available hugepage support:
// - class ExplicitHugePageSupport - about explicit (non-THP) hugepages
// - class THPSupport - about transparent huge pages
// and:
// - class HugePages - a static umbrella wrapper

// Information about static (non-thp) hugepages
class ExplicitHugePageSupport {
  bool _initialized;

  // All supported hugepage sizes (sizes for which entries exist
  // in /sys/kernel/mm/hugepages/hugepage-xxx)
  os::PageSizes _pagesizes;

  // Contains the default hugepage. The "default hugepage size" is the one that
  // - is marked in /proc/meminfo as "Hugepagesize"
  // - is the size one gets when using mmap(MAP_HUGETLB) when omitting size specifiers like MAP_HUGE_SHIFT)
  size_t _default_hugepage_size;

  // If true, the kernel support for hugepages is inconsistent
  bool _inconsistent;

public:
  ExplicitHugePageSupport();

  void scan_os();

  os::PageSizes pagesizes() const;
  size_t default_hugepage_size() const;
  void print_on(outputStream* os);

  bool inconsistent() const { return _inconsistent; }
};

enum class THPMode { always, never, madvise };

// 2) for transparent hugepages
class THPSupport {
  bool _initialized;

  // See /sys/kernel/mm/transparent_hugepages/enabled
  THPMode _mode;

  // Contains the THP page size
  size_t _pagesize;

public:

  THPSupport();

  // Queries the OS, fills in object
  void scan_os();

  THPMode mode() const;
  size_t pagesize() const;
  void print_on(outputStream* os);
};

enum class ShmemTHPMode { always, within_size, advise, never, deny, force, unknown };

// for transparent shmem hugepages
class ShmemTHPSupport {
  bool _initialized;

  // See /sys/kernel/mm/transparent_hugepage/shmem_enabled
  ShmemTHPMode _mode;

  static const char* mode_to_string(ShmemTHPMode mode);

public:

  ShmemTHPSupport();

  // Queries the OS, fills in object
  void scan_os();

  ShmemTHPMode mode() const;

  bool is_forced() const;
  bool is_enabled() const;
  bool is_disabled() const;

  // Printing
  void print_on(outputStream* os);
};

// Umbrella static interface
class HugePages : public AllStatic {

  static ExplicitHugePageSupport _explicit_hugepage_support;
  static THPSupport _thp_support;
  static ShmemTHPSupport _shmem_thp_support;

public:

  static const ExplicitHugePageSupport& explicit_hugepage_info() { return _explicit_hugepage_support; }
  static const THPSupport& thp_info() { return _thp_support; }
  static const ShmemTHPSupport& shmem_thp_info() { return _shmem_thp_support; }

  static size_t default_explicit_hugepage_size() { return _explicit_hugepage_support.default_hugepage_size(); }
  static bool supports_explicit_hugepages()      { return default_explicit_hugepage_size() > 0 && !_explicit_hugepage_support.inconsistent(); }

  static bool supports_thp()                    { return thp_mode() == THPMode::madvise || thp_mode() == THPMode::always; }
  static THPMode thp_mode()                     { return _thp_support.mode(); }
  static size_t thp_pagesize()                  { return _thp_support.pagesize(); }

  static bool supports_shmem_thp()              { return _shmem_thp_support.is_enabled(); }
  static ShmemTHPMode shmem_thp_mode()          { return _shmem_thp_support.mode(); }
  static bool forced_shmem_thp()                { return _shmem_thp_support.is_forced(); }

  static void initialize();
  static void print_on(outputStream* os);
};

#endif // OS_LINUX_HUGEPAGES_HPP
