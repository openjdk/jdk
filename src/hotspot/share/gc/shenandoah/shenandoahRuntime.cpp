/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/shenandoahBarrierSet.inline.hpp"
#include "gc/shenandoah/shenandoahRuntime.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "utilities/copy.hpp"

JRT_LEAF(void, ShenandoahRuntime::arraycopy_barrier_oop(oop* src, oop* dst, size_t length))
  ShenandoahBarrierSet::barrier_set()->arraycopy_barrier(src, dst, length);
JRT_END

JRT_LEAF(void, ShenandoahRuntime::arraycopy_barrier_narrow_oop(narrowOop* src, narrowOop* dst, size_t length))
  ShenandoahBarrierSet::barrier_set()->arraycopy_barrier(src, dst, length);
JRT_END

JRT_LEAF(void, ShenandoahRuntime::write_barrier_pre(oopDesc* obj))
  // Called from barrier slow-paths on full buffer.
  // We need to enqueue without filters to force buffer cleanups.
  ShenandoahBarrierSet::barrier_set()->enqueue(obj, /* filter = */ false);
JRT_END

JRT_LEAF(void, ShenandoahRuntime::write_barrier_pre_narrow(narrowOop nobj))
  assert(!CompressedOops::is_null(nobj), "Filtered by caller");
  // Called from barrier slow-paths on full buffer.
  // We need to enqueue without filters to force buffer cleanups.
  oop obj = CompressedOops::decode_not_null(nobj);
  ShenandoahBarrierSet::barrier_set()->enqueue(obj, /* filter = */ false);
JRT_END

JRT_LEAF(oopDesc*, ShenandoahRuntime::load_reference_barrier_strong(oopDesc* src, oop* load_addr))
  return ShenandoahBarrierSet::barrier_set()->load_reference_barrier_mutator<ON_STRONG_OOP_REF, oop>(src, load_addr);
JRT_END

JRT_LEAF(oopDesc*, ShenandoahRuntime::load_reference_barrier_strong_narrow(oopDesc* src, narrowOop* load_addr))
  return ShenandoahBarrierSet::barrier_set()->load_reference_barrier_mutator<ON_STRONG_OOP_REF, narrowOop>(src, load_addr);
JRT_END

JRT_LEAF(narrowOop, ShenandoahRuntime::load_reference_barrier_strong_narrow_narrow(narrowOop src, narrowOop* load_addr))
  assert(!CompressedOops::is_null(src), "Filtered by caller");
  oop s = CompressedOops::decode_not_null(src);
  oop r = ShenandoahBarrierSet::barrier_set()->load_reference_barrier_mutator<ON_STRONG_OOP_REF, narrowOop>(s, load_addr);
  return CompressedOops::encode(r);
JRT_END

JRT_LEAF(oopDesc*, ShenandoahRuntime::load_reference_barrier_weak(oopDesc* src, oop* load_addr))
  return ShenandoahBarrierSet::barrier_set()->load_reference_barrier_mutator<ON_WEAK_OOP_REF, oop>(src, load_addr);
JRT_END

JRT_LEAF(oopDesc*, ShenandoahRuntime::load_reference_barrier_weak_narrow(oopDesc* src, narrowOop* load_addr))
  return ShenandoahBarrierSet::barrier_set()->load_reference_barrier_mutator<ON_WEAK_OOP_REF, narrowOop>(src, load_addr);
JRT_END

JRT_LEAF(narrowOop, ShenandoahRuntime::load_reference_barrier_weak_narrow_narrow(narrowOop src, narrowOop* load_addr))
  assert(!CompressedOops::is_null(src), "Filtered by caller");
  oop s = CompressedOops::decode_not_null(src);
  oop r = ShenandoahBarrierSet::barrier_set()->load_reference_barrier_mutator<ON_WEAK_OOP_REF, narrowOop>(s, load_addr);
  return CompressedOops::encode(r);
JRT_END

JRT_LEAF(oopDesc*, ShenandoahRuntime::load_reference_barrier_phantom(oopDesc* src, oop* load_addr))
  return ShenandoahBarrierSet::barrier_set()->load_reference_barrier_mutator<ON_PHANTOM_OOP_REF, oop>(src, load_addr);
JRT_END

JRT_LEAF(oopDesc*, ShenandoahRuntime::load_reference_barrier_phantom_narrow(oopDesc* src, narrowOop* load_addr))
  return ShenandoahBarrierSet::barrier_set()->load_reference_barrier_mutator<ON_PHANTOM_OOP_REF, narrowOop>(src, load_addr);
JRT_END

JRT_LEAF(narrowOop, ShenandoahRuntime::load_reference_barrier_phantom_narrow_narrow(narrowOop src, narrowOop* load_addr))
  assert(!CompressedOops::is_null(src), "Filtered by caller");
  oop s = CompressedOops::decode_not_null(src);
  oop r = ShenandoahBarrierSet::barrier_set()->load_reference_barrier_mutator<ON_PHANTOM_OOP_REF, narrowOop>(s, load_addr);
  return CompressedOops::encode(r);
JRT_END

JRT_LEAF(void, ShenandoahRuntime::clone(oopDesc* src, oopDesc* dst, size_t size))
  shenandoah_assert_correct(nullptr, src);
  shenandoah_assert_correct(nullptr, dst);
  HeapAccess<>::clone(src, dst, size);
JRT_END
