/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_SUPPORT_JFRSYMBOLTABLE_INLINE_HPP
#define SHARE_JFR_SUPPORT_JFRSYMBOLTABLE_INLINE_HPP

#include "jfr/support/jfrSymbolTable.hpp"

#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/utilities/jfrConcurrentHashtable.inline.hpp"

inline JfrSymbolTable::Impl* JfrSymbolTable::epoch_table_selector(u1 epoch) {
  return epoch == 0 ? _epoch_0 : _epoch_1;
}

inline JfrSymbolTable::Impl* JfrSymbolTable::this_epoch_table() {
  return epoch_table_selector(JfrTraceIdEpoch::current());
}

inline JfrSymbolTable::Impl* JfrSymbolTable::previous_epoch_table() {
  return epoch_table_selector(JfrTraceIdEpoch::previous());
}

template <typename Functor>
inline void JfrSymbolTable::Impl::iterate_symbols(Functor& functor) {
  _symbols->iterate_entry(functor);
}

template <typename Functor>
inline void JfrSymbolTable::iterate_symbols(Functor& functor, bool previous_epoch /* false */) {
  Impl* const table = previous_epoch ? previous_epoch_table() : this_epoch_table();
  assert(table != nullptr, "invariant");
  table->iterate_symbols(functor);
}

template <typename Functor>
inline void JfrSymbolTable::Impl::iterate_strings(Functor& functor) {
  _strings->iterate_entry(functor);
}

template <typename Functor>
inline void JfrSymbolTable::iterate_strings(Functor& functor, bool previous_epoch /* false */) {
  Impl* const table = previous_epoch ? previous_epoch_table() : this_epoch_table();
  assert(table != nullptr, "invariant");
  if (!functor(_bootstrap)) {
    return;
  }
  table->iterate_strings(functor);
}

#endif // SHARE_JFR_SUPPORT_JFRSYMBOLTABLE_INLINE_HPP
