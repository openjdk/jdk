/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_METASPACEZAPPER_HPP
#define SHARE_MEMORY_METASPACE_METASPACEZAPPER_HPP

#include "memory/allStatic.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {

class Zapper : public AllStatic {
public:

  static constexpr uintptr_t zap_pattern = NOT_LP64(0x646561646) LP64_ONLY(0x646561646D657461ULL); // "dead" resp. "deadmeta"

  static void zap_memory(MetaWord* start, size_t word_size) {
    for (size_t pos = 0; pos < word_size; pos ++) {
      zap_location(start + pos);
    }
  }

  static void zap_location(MetaWord* p) { ((uintptr_t*)p)[0] = zap_pattern; }
  static bool is_zapped_location(const MetaWord* p) { return ((uintptr_t*)p)[0] == zap_pattern; }

  // Given a header followed by a variable-sized payload with a total (including header) word_size,
  // zap the payload while leaving the header alone
  template <class HEADER>
  static void zap_payload(HEADER* p, size_t word_size) {
    const size_t start = align_up(sizeof(HEADER), sizeof(MetaWord)) / sizeof(MetaWord);
    for (size_t pos = start; pos < word_size; pos ++) {
      zap_location((MetaWord*)p + pos);
    }
  }

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_METASPACEZAPPER_HPP
