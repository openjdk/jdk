/*
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
 */

#ifndef SHARE_NMT_NMEMORYLIMITPRINTER_HPP
#define SHARE_NMT_NMEMORYLIMITPRINTER_HPP

#include "memory/allStatic.hpp"
#include "nmt/nMemLimit.hpp"

class NMemoryLimitPrinter : public AllStatic {

public:
  // Called when a total limit break was detected.
  // Will return true if the limit was handled, false if it was ignored.
  static bool total_limit_reached(size_t s, size_t so_far, const nMemlimit* limit, const NMemType type);

  // Called when a category limit break was detected.
  // Will return true if the limit was handled, false if it was ignored.
  static bool category_limit_reached(MemTag mem_tag, size_t s, size_t so_far, const nMemlimit* limit, const NMemType type);
};

#endif
