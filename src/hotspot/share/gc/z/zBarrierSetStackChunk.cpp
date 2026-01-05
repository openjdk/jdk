/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zBarrierSetStackChunk.hpp"
#include "gc/z/zContinuation.hpp"
#include "utilities/debug.hpp"

void ZBarrierSetStackChunk::encode_gc_mode(stackChunkOop chunk, OopIterator* iterator) {
  ZContinuation::ZColorStackOopClosure cl(chunk);
  iterator->oops_do(&cl);
}

void ZBarrierSetStackChunk::decode_gc_mode(stackChunkOop chunk, OopIterator* iterator) {
  ZContinuation::ZUncolorStackOopClosure cl;
  iterator->oops_do(&cl);
}

oop ZBarrierSetStackChunk::load_oop(stackChunkOop chunk, oop* addr) {
  return ZContinuation::load_oop(chunk, addr);
}

oop ZBarrierSetStackChunk::load_oop(stackChunkOop chunk, narrowOop* addr) {
  ShouldNotReachHere();
  return nullptr;
}
