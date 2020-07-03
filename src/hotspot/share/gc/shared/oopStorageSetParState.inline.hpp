/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_OOPSTORAGESETPARSTATE_INLINE_HPP
#define SHARE_GC_SHARED_OOPSTORAGESETPARSTATE_INLINE_HPP

#include "gc/shared/oopStorageParState.inline.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "gc/shared/oopStorageSetParState.hpp"

template <bool concurrent, bool is_const>
OopStorageSetStrongParState<concurrent, is_const>::OopStorageSetStrongParState() :
    _par_states(OopStorageSet::strong_iterator()) {
}

template <bool concurrent, bool is_const>
template <typename Closure>
void OopStorageSetStrongParState<concurrent, is_const>::oops_do(Closure* cl) {
  for (int i = 0; i < _par_states.count(); i++) {
    _par_states.at(i)->oops_do(cl);
  }
}

#endif // SHARE_GC_SHARED_OOPSTORAGESETPARSTATE_INLINE_HPP
