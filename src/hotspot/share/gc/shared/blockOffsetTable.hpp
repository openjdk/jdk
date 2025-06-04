/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_BLOCKOFFSETTABLE_HPP
#define SHARE_GC_SHARED_BLOCKOFFSETTABLE_HPP

#include "gc/shared/cardTable.hpp"
#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

class BOTConstants : public AllStatic {
public:
  // entries "e" of at least N_words mean "go back by Base^(e-N_words)."
  // All entries are less than "N_words + N_powers".
  static const uint LogBase = 4;
  static const uint Base = (1 << LogBase);
  static const uint N_powers = 14;

  static size_t power_to_cards_back(uint i) {
    return (size_t)1 << (LogBase * i);
  }

  static size_t entry_to_cards_back(u_char entry) {
    assert(entry >= CardTable::card_size_in_words(), "Precondition");
    return power_to_cards_back(entry - CardTable::card_size_in_words());
  }
};

#endif // SHARE_GC_SHARED_BLOCKOFFSETTABLE_HPP
