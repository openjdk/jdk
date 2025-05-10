/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "oops/markWord.hpp"
#include "oops/objLayout.inline.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"

HeaderMode ObjLayout::_mode;
int ObjLayout::_oop_base_offset_in_bytes = 0;
bool ObjLayout::_oop_has_klass_gap = false;

void ObjLayout::initialize() {
  assert(!is_initialized(), "ObjLayout initialized twice");
  if (UseCompactObjectHeaders) {
    _mode = HeaderMode::Compact;
    _oop_base_offset_in_bytes = ObjLayoutHelpers::markword_plus_klass_in_bytes<HeaderMode::Compact>();
    _oop_has_klass_gap = ObjLayoutHelpers::oop_has_klass_gap<HeaderMode::Compact>();
  } else if (UseCompressedClassPointers) {
    _mode = HeaderMode::Compressed;
    _oop_base_offset_in_bytes = ObjLayoutHelpers::markword_plus_klass_in_bytes<HeaderMode::Compressed>();
    _oop_has_klass_gap = ObjLayoutHelpers::oop_has_klass_gap<HeaderMode::Compressed>();
  } else {
    _mode = HeaderMode::Uncompressed;
    _oop_base_offset_in_bytes = ObjLayoutHelpers::markword_plus_klass_in_bytes<HeaderMode::Uncompressed>();
    _oop_has_klass_gap = ObjLayoutHelpers::oop_has_klass_gap<HeaderMode::Uncompressed>();
  }
}
