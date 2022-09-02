/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
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

#ifndef SHARE_SERVICES_MALLOCLIMIT_HPP
#define SHARE_SERVICES_MALLOCLIMIT_HPP

#include "memory/allocation.hpp" // for MEMFLAGS
#include "utilities/globalDefinitions.hpp"

class Arguments;
class outputStream;

// This class contains the parsed MallocLimit argument.
class MallocLimitInfo {
  friend class Arguments;

  // If _total_limit != 0, category limits are ignored.
  size_t _limits_per_category[mt_number_of_types];
  size_t _total_limit;
  bool _fake_oom;

public:
  NONCOPYABLE(MallocLimitInfo);

  MallocLimitInfo();

  void reset();

  size_t total_limit() const { return _total_limit; }
  bool is_global_limit() const { return total_limit() > 0; }

  size_t get_limit_for_category(MEMFLAGS f) const {
    return _limits_per_category[(int)f];
  }

  bool should_fake_oom() const { return _fake_oom; }

  void print(outputStream* st) const;
};

#endif // SHARE_SERVICES_MALLOCLIMIT_HPP
