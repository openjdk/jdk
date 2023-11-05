/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/x/xBarrier.inline.hpp"
#include "gc/x/xBarrierSetStackChunk.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

void XBarrierSetStackChunk::encode_gc_mode(stackChunkOop chunk, OopIterator* iterator) {
  // Do nothing
}

void XBarrierSetStackChunk::decode_gc_mode(stackChunkOop chunk, OopIterator* iterator) {
  // Do nothing
}

oop XBarrierSetStackChunk::load_oop(stackChunkOop chunk, oop* addr) {
  oop obj = Atomic::load(addr);
  return XBarrier::load_barrier_on_oop_field_preloaded((volatile oop*)nullptr, obj);
}

oop XBarrierSetStackChunk::load_oop(stackChunkOop chunk, narrowOop* addr) {
  ShouldNotReachHere();
  return nullptr;
}
