/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022 SAP SE. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_RUNNINGCOUNTERS_HPP
#define SHARE_MEMORY_METASPACE_RUNNINGCOUNTERS_HPP

#include "memory/allStatic.hpp"

namespace metaspace {

// This class is a convenience interface for accessing global metaspace counters.
struct RunningCounters : public AllStatic {

  // ---- virtual memory -----

  // Return reserved size, in words, for Metaspace
  static size_t reserved_words();
  static size_t reserved_words_class();
  static size_t reserved_words_nonclass();

  // Return total committed size, in words, for Metaspace
  static size_t committed_words();
  static size_t committed_words_class();
  static size_t committed_words_nonclass();

  // ---- used chunks -----

  // Returns size, in words, used for metadata.
  static size_t used_words();
  static size_t used_words_class();
  static size_t used_words_nonclass();

  // ---- free chunks -----

  // Returns size, in words, of all chunks in all freelists.
  static size_t free_chunks_words();
  static size_t free_chunks_words_class();
  static size_t free_chunks_words_nonclass();

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_RUNNINGCOUNTERS_HPP
