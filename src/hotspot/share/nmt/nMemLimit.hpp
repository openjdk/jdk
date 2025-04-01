/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025 Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_NMT_NMEMLIMIT_HPP
#define SHARE_NMT_NMEMLIMIT_HPP

#include "memory/allStatic.hpp"
#include "nmt/memTag.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

enum class NMemType : int {
  Malloc,
  Mmap
};

enum class NMemLimitMode {
  trigger_fatal = 0,
  trigger_oom   = 1
};

struct nMemlimit {
  size_t sz;            // Limit size
  NMemLimitMode mode; // Behavior flags
};

// forward declaration
class outputStream;

class NMemLimitSet {
  nMemlimit _glob;                    // global limit
  nMemlimit _cat[mt_number_of_tags]; // per-category limit
public:
  NMemLimitSet();

  void reset();
  bool parse_n_mem_limit_option(const char* optionstring, const char** err);

  void set_global_limit(size_t s, NMemLimitMode type);
  void set_category_limit(MemTag mem_tag, size_t s, NMemLimitMode mode);

  const nMemlimit* global_limit() const             { return &_glob; }
  const nMemlimit* category_limit(MemTag mem_tag) const { return &_cat[(int)mem_tag]; }

  void print_on(outputStream* st, const char* type_str) const;
};

class NMemLimitHandler : public AllStatic {
private:
  static NMemLimitSet* get_mem_limit_set(NMemType type) {
    if (NMemType::Malloc == type) {
      return &_malloc_limits;
    } else if (NMemType::Mmap == type) {
      return &_mmap_limits;
    } else {
      ShouldNotReachHere();
    }
  }
protected:
  static NMemLimitSet _malloc_limits;
  static NMemLimitSet _mmap_limits;
  static bool _have_limit_map[2]; // A map mapping from NMemType to whether it has limit set

public:
  static const nMemlimit* global_limit(NMemType type)             { return get_mem_limit_set(type)->global_limit(); }
  static const nMemlimit* category_limit(MemTag mem_tag, NMemType type) { return get_mem_limit_set(type)->category_limit(mem_tag); }

  static void initialize(const char* options, NMemType type);
  static void print_on_by_type(outputStream* st, NMemType type);
  static void print_on(outputStream* st);
  static const char* nmem_type_to_str(NMemType type);
  static int nmemtype_to_int(NMemType type);

  // True if there is any limit established
  static bool have_limit(NMemType type) {
    return _have_limit_map[nmemtype_to_int(type)];
  }
};

#endif
