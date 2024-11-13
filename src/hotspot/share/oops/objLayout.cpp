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

#include "precompiled.hpp"
#include "oops/markWord.hpp"
#include "oops/objLayout.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"

ObjLayout::Mode ObjLayout::_klass_mode = ObjLayout::Undefined;
int ObjLayout::_oop_base_offset_in_bytes = 0;
bool ObjLayout::_oop_has_klass_gap = false;

void ObjLayout::initialize() {
  assert(_klass_mode == Undefined, "ObjLayout initialized twice");
  if (UseCompactObjectHeaders) {
    _klass_mode = Compact;
    _oop_base_offset_in_bytes = sizeof(markWord);
    _oop_has_klass_gap = false;
  } else if (UseCompressedClassPointers) {
    _klass_mode = Compressed;
    _oop_base_offset_in_bytes = sizeof(markWord) + sizeof(narrowKlass);
    _oop_has_klass_gap = true;
  } else {
    _klass_mode = Uncompressed;
    _oop_base_offset_in_bytes = sizeof(markWord) + sizeof(Klass*);
    _oop_has_klass_gap = false;
  }
}
