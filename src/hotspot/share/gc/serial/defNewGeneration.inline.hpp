/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SERIAL_DEFNEWGENERATION_INLINE_HPP
#define SHARE_GC_SERIAL_DEFNEWGENERATION_INLINE_HPP

#include "gc/serial/defNewGeneration.hpp"

#include "gc/serial/cardTableRS.hpp"
#include "gc/shared/space.inline.hpp"
#include "oops/access.inline.hpp"
#include "utilities/devirtualizer.inline.hpp"

// Methods of protected closure types

template <typename OopClosureType>
void DefNewGeneration::oop_since_save_marks_iterate(OopClosureType* cl) {
  // No allocation in eden and from spaces, so no iteration required.
  assert(eden()->saved_mark_at_top(), "inv");
  assert(from()->saved_mark_at_top(), "inv");

  to()->oop_since_save_marks_iterate(cl);
}

#endif // SHARE_GC_SERIAL_DEFNEWGENERATION_INLINE_HPP
