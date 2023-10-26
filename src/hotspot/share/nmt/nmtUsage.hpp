/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_NMTUSAGE_HPP
#define SHARE_NMT_NMTUSAGE_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

struct NMTUsagePair {
  size_t reserved;
  size_t committed;
};

struct NMTUsageOptions {
  bool update_thread_stacks;
  bool include_malloc;
  bool include_vm;
};

class NMTUsage : public CHeapObj<mtNMT> {
private:
  size_t _malloc_by_type[mt_number_of_types];
  size_t _malloc_total;
  NMTUsagePair _vm_by_type[mt_number_of_types];
  NMTUsagePair _vm_total;

  NMTUsageOptions _usage_options;

  void walk_thread_stacks();
  void update_malloc_usage();
  void update_vm_usage();

public:
  static const NMTUsageOptions OptionsAll;
  static const NMTUsageOptions OptionsNoTS;

  NMTUsage(NMTUsageOptions options = OptionsAll);
  void refresh();

  size_t total_reserved() const;
  size_t total_committed() const;
  size_t reserved(MEMFLAGS flag) const;
  size_t committed(MEMFLAGS flag) const;
};

#endif // SHARE_NMT_NMTUSAGE_HPP
