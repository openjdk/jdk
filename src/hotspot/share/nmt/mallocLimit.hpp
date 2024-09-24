/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_SERVICES_MALLOCLIMIT_HPP
#define SHARE_SERVICES_MALLOCLIMIT_HPP

#include "memory/allStatic.hpp"
#include "nmt/memflags.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

enum class MallocLimitMode {
  trigger_fatal = 0,
  trigger_oom   = 1
};

struct malloclimit {
  size_t sz;            // Limit size
  MallocLimitMode mode; // Behavior flags
};

// forward declaration
class outputStream;

class MallocLimitSet {
  malloclimit _glob;                    // global limit
  malloclimit _cat[mt_number_of_types]; // per-category limit
public:
  MallocLimitSet();

  void reset();
  bool parse_malloclimit_option(const char* optionstring, const char** err);

  void set_global_limit(size_t s, MallocLimitMode flag);
  void set_category_limit(MEMFLAGS f, size_t s, MallocLimitMode flag);

  const malloclimit* global_limit() const             { return &_glob; }
  const malloclimit* category_limit(MEMFLAGS f) const { return &_cat[(int)f]; }

  void print_on(outputStream* st) const;
};

class MallocLimitHandler : public AllStatic {
  static MallocLimitSet _limits;
  static bool _have_limit; // shortcut

public:

  static const malloclimit* global_limit()             { return _limits.global_limit(); }
  static const malloclimit* category_limit(MEMFLAGS f) { return _limits.category_limit(f); }

  static void initialize(const char* options);
  static void print_on(outputStream* st);

  // True if there is any limit established
  static bool have_limit() { return _have_limit; }
};

#endif // SHARE_SERVICES_MALLOCLIMIT_HPP
