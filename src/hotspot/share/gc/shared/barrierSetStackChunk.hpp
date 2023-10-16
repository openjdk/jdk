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

#ifndef SHARE_GC_SHARED_BARRIERSETSTACKCHUNK_HPP
#define SHARE_GC_SHARED_BARRIERSETSTACKCHUNK_HPP

#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"

class OopClosure;

class BarrierSetStackChunk: public CHeapObj<mtGC> {
public:
  virtual void encode_gc_mode(stackChunkOop chunk, OopIterator* oop_iterator);
  virtual void decode_gc_mode(stackChunkOop chunk, OopIterator* oop_iterator);

  virtual oop load_oop(stackChunkOop chunk, oop* addr);
  virtual oop load_oop(stackChunkOop chunk, narrowOop* addr);
};

#endif // SHARE_GC_SHARED_BARRIERSETSTACKCHUNK_HPP
