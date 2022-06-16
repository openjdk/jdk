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
 */

#ifndef SHARE_GC_Z_ZCONTINUATION_INLINE_HPP
#define SHARE_GC_Z_ZCONTINUATION_INLINE_HPP

#include "classfile/javaClasses.hpp"
#include "compiler/oopMap.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zContinuation.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zStackChunkGCData.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/stackChunkFrameStream.inline.hpp"

class ZColorStackOopClosure : public OopClosure {
private:
  uint64_t _color;

public:
  ZColorStackOopClosure(uint64_t color)
    : _color(color) {}

  virtual void do_oop(oop* p) {
    // Convert zaddress to zpointer
    zaddress_unsafe* p_zaddress_unsafe = (zaddress_unsafe*)p;
    zpointer* p_zpointer = (zpointer*)p;
    *p_zpointer = ZAddress::color(*p_zaddress_unsafe, _color);

  }
  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

class ZUncolorStackOopClosure : public OopClosure {
public:
  void do_oop(oop* p) override {
    zpointer ptr = *(volatile zpointer*)p;
    zaddress addr = ZPointer::uncolor(ptr);
    *(volatile zaddress*)p = addr;
  }

  void do_oop(narrowOop* p) override {}
};

class ZColorStackFrameClosure {
  uint64_t _color;

public:
  ZColorStackFrameClosure(uint64_t color)
    : _color(color) {}

  template <ChunkFrames frame_kind, typename RegisterMapT>
  bool do_frame(const StackChunkFrameStream<frame_kind>& f, const RegisterMapT* map) {
    ZColorStackOopClosure oop_cl(_color);
    f.iterate_oops(&oop_cl, map);
    return true;
  }
};

inline void ZContinuation::color_stack_pointers(stackChunkOop chunk) {
  ZColorStackFrameClosure frame_cl(ZStackChunkGCData::color(chunk));
  chunk->iterate_stack(&frame_cl);
}

template <typename RegisterMapT>
inline void ZContinuation::uncolor_stack_pointers(const frame& f, const RegisterMapT* map) {
  ZUncolorStackOopClosure oop_closure;
  if (f.is_interpreted_frame()) {
    f.oops_interpreted_do(&oop_closure, nullptr);
  } else {
    OopMapDo<ZUncolorStackOopClosure, DerivedOopClosure, SkipNullValue> visitor(&oop_closure, nullptr);
    visitor.oops_do(&f, map, f.oop_map());
  }
}

inline bool ZContinuation::requires_barriers(const ZHeap* heap, stackChunkOop chunk) {
  zpointer* cont_addr = chunk->field_addr<zpointer>(jdk_internal_vm_StackChunk::cont_offset());

  if (!heap->is_allocating(to_zaddress(chunk))) {
    // An object that isn't allocating, is visible from GC tracing. Such
    // stack chunks require barriers.
    return true;
  }

  if (ZStackChunkGCData::color(chunk) != ZPointerStoreGoodMask) {
    // If a chunk is allocated after a GC started, but before relocate start
    // we can have an allocating chunk that isn't deeply good. That means that
    // the contained oops might be bad and require GC barriers.
    return true;
  }

  // The chunk is allocating and its pointers are good. This chunk needs no
  // GC barriers
  return false;
}

#endif // SHARE_GC_Z_ZCONTINUATION_INLINE_HPP
