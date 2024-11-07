/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zBarrierSetRuntime.hpp"
#include "oops/access.hpp"
#include "runtime/interfaceSupport.inline.hpp"

JRT_LEAF(oopDesc*, ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded(oopDesc* o, oop* p))
  return to_oop(ZBarrier::load_barrier_on_oop_field_preloaded((zpointer*)p, to_zpointer(o)));
JRT_END

JRT_LEAF(zpointer, ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_store_good(oopDesc* o, oop* p))
  return ZAddress::color(ZBarrier::load_barrier_on_oop_field_preloaded((zpointer*)p, to_zpointer(o)), ZPointerStoreGoodMask);
JRT_END

JRT_LEAF(oopDesc*, ZBarrierSetRuntime::load_barrier_on_weak_oop_field_preloaded(oopDesc* o, oop* p))
  return to_oop(ZBarrier::load_barrier_on_weak_oop_field_preloaded((zpointer*)p, to_zpointer(o)));
JRT_END

JRT_LEAF(oopDesc*, ZBarrierSetRuntime::load_barrier_on_phantom_oop_field_preloaded(oopDesc* o, oop* p))
  return to_oop(ZBarrier::load_barrier_on_phantom_oop_field_preloaded((zpointer*)p, to_zpointer(o)));
JRT_END

JRT_LEAF(oopDesc*, ZBarrierSetRuntime::no_keepalive_load_barrier_on_weak_oop_field_preloaded(oopDesc* o, oop* p))
  return to_oop(ZBarrier::no_keep_alive_load_barrier_on_weak_oop_field_preloaded((zpointer*)p, to_zpointer(o)));
JRT_END

JRT_LEAF(oopDesc*, ZBarrierSetRuntime::no_keepalive_load_barrier_on_phantom_oop_field_preloaded(oopDesc* o, oop* p))
  return to_oop(ZBarrier::no_keep_alive_load_barrier_on_phantom_oop_field_preloaded((zpointer*)p, to_zpointer(o)));
JRT_END

JRT_LEAF(void, ZBarrierSetRuntime::store_barrier_on_oop_field_with_healing(oop* p))
  ZBarrier::store_barrier_on_heap_oop_field((zpointer*)p, true /* heal */);
JRT_END

JRT_LEAF(void, ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing(oop* p))
  ZBarrier::store_barrier_on_heap_oop_field((zpointer*)p, false /* heal */);
JRT_END

JRT_LEAF(void, ZBarrierSetRuntime::no_keepalive_store_barrier_on_oop_field_without_healing(oop* p))
  ZBarrier::no_keep_alive_store_barrier_on_heap_oop_field((zpointer*)p);
JRT_END

JRT_LEAF(void, ZBarrierSetRuntime::store_barrier_on_native_oop_field_without_healing(oop* p))
  ZBarrier::store_barrier_on_native_oop_field((zpointer*)p, false /* heal */);
JRT_END

JRT_LEAF(void, ZBarrierSetRuntime::load_barrier_on_oop_array(oop* p, size_t length))
  ZBarrier::load_barrier_on_oop_array((zpointer*)p, length);
JRT_END

JRT_LEAF(void, ZBarrierSetRuntime::clone(oopDesc* src, oopDesc* dst, size_t size))
  HeapAccess<>::clone(src, dst, size);
JRT_END

address ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(DecoratorSet decorators) {
  if (decorators & AS_NO_KEEPALIVE) {
    if (decorators & ON_PHANTOM_OOP_REF) {
      return no_keepalive_load_barrier_on_phantom_oop_field_preloaded_addr();
    } else if (decorators & ON_WEAK_OOP_REF) {
      return no_keepalive_load_barrier_on_weak_oop_field_preloaded_addr();
    } else {
      assert((decorators & ON_STRONG_OOP_REF), "Expected type");
      // Normal loads on strong oop never keep objects alive
      return load_barrier_on_oop_field_preloaded_addr();
    }
  } else {
    if (decorators & ON_PHANTOM_OOP_REF) {
      return load_barrier_on_phantom_oop_field_preloaded_addr();
    } else if (decorators & ON_WEAK_OOP_REF) {
      return load_barrier_on_weak_oop_field_preloaded_addr();
    } else {
      assert((decorators & ON_STRONG_OOP_REF), "Expected type");
      return load_barrier_on_oop_field_preloaded_addr();
    }
  }
}

address ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr() {
  return reinterpret_cast<address>(load_barrier_on_oop_field_preloaded);
}

address ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_store_good_addr() {
  return reinterpret_cast<address>(load_barrier_on_oop_field_preloaded_store_good);
}

address ZBarrierSetRuntime::load_barrier_on_weak_oop_field_preloaded_addr() {
  return reinterpret_cast<address>(load_barrier_on_weak_oop_field_preloaded);
}

address ZBarrierSetRuntime::load_barrier_on_phantom_oop_field_preloaded_addr() {
  return reinterpret_cast<address>(load_barrier_on_phantom_oop_field_preloaded);
}

address ZBarrierSetRuntime::no_keepalive_load_barrier_on_weak_oop_field_preloaded_addr() {
  return reinterpret_cast<address>(no_keepalive_load_barrier_on_weak_oop_field_preloaded);
}

address ZBarrierSetRuntime::no_keepalive_load_barrier_on_phantom_oop_field_preloaded_addr() {
  return reinterpret_cast<address>(no_keepalive_load_barrier_on_phantom_oop_field_preloaded);
}

address ZBarrierSetRuntime::store_barrier_on_oop_field_with_healing_addr() {
  return reinterpret_cast<address>(store_barrier_on_oop_field_with_healing);
}

address ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr() {
  return reinterpret_cast<address>(store_barrier_on_oop_field_without_healing);
}

address ZBarrierSetRuntime::no_keepalive_store_barrier_on_oop_field_without_healing_addr() {
  return reinterpret_cast<address>(no_keepalive_store_barrier_on_oop_field_without_healing);
}

address ZBarrierSetRuntime::store_barrier_on_native_oop_field_without_healing_addr() {
  return reinterpret_cast<address>(store_barrier_on_native_oop_field_without_healing);
}

address ZBarrierSetRuntime::load_barrier_on_oop_array_addr() {
  return reinterpret_cast<address>(load_barrier_on_oop_array);
}

address ZBarrierSetRuntime::clone_addr() {
  return reinterpret_cast<address>(clone);
}
