/*
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/debug.hpp"
#include "utilities/resourceHash.hpp"

#ifndef PRODUCT

/////////////// Unit tests ///////////////

class TestResourceHashtable : public AllStatic {
  typedef void* K;
  typedef int V;

  static unsigned identity_hash(const K& k) {
    return (unsigned)(uintptr_t)k;
  }

  static unsigned bad_hash(const K& k) {
    return 1;
  }

  class EqualityTestIter {
   public:
    bool do_entry(K const& k, V const& v) {
      assert((uintptr_t)k == (uintptr_t)v, "");
      return true; // continue iteration
    }
  };

  template<
  unsigned (*HASH)  (K const&)           = primitive_hash<K>,
  bool     (*EQUALS)(K const&, K const&) = primitive_equals<K>,
  unsigned SIZE = 256,
  ResourceObj::allocation_type ALLOC_TYPE = ResourceObj::RESOURCE_AREA,
  MEMFLAGS MEM_TYPE = mtInternal
  >
  class Runner : public AllStatic {
    static void* as_K(uintptr_t val) { return (void*)val; }

   public:
    static void test_small() {
      EqualityTestIter et;
      ResourceHashtable<K, V, HASH, EQUALS, SIZE, ALLOC_TYPE, MEM_TYPE> rh;

      assert(!rh.contains(as_K(0x1)), "");

      assert(rh.put(as_K(0x1), 0x1), "");
      assert(rh.contains(as_K(0x1)), "");

      assert(!rh.put(as_K(0x1), 0x1), "");

      assert(rh.put(as_K(0x2), 0x2), "");
      assert(rh.put(as_K(0x3), 0x3), "");
      assert(rh.put(as_K(0x4), 0x4), "");
      assert(rh.put(as_K(0x5), 0x5), "");

      assert(!rh.remove(as_K(0x0)), "");
      rh.iterate(&et);

      assert(rh.remove(as_K(0x1)), "");
      rh.iterate(&et);

    }

    // We use keys with the low bits cleared since the default hash will do some shifting
    static void test_small_shifted() {
      EqualityTestIter et;
      ResourceHashtable<K, V, HASH, EQUALS, SIZE, ALLOC_TYPE, MEM_TYPE> rh;

      assert(!rh.contains(as_K(0x10)), "");

      assert(rh.put(as_K(0x10), 0x10), "");
      assert(rh.contains(as_K(0x10)), "");

      assert(!rh.put(as_K(0x10), 0x10), "");

      assert(rh.put(as_K(0x20), 0x20), "");
      assert(rh.put(as_K(0x30), 0x30), "");
      assert(rh.put(as_K(0x40), 0x40), "");
      assert(rh.put(as_K(0x50), 0x50), "");

      assert(!rh.remove(as_K(0x00)), "");

      assert(rh.remove(as_K(0x10)), "");

      rh.iterate(&et);
    }

    static void test(unsigned num_elements = SIZE) {
      EqualityTestIter et;
      ResourceHashtable<K, V, HASH, EQUALS, SIZE, ALLOC_TYPE, MEM_TYPE> rh;

      for (uintptr_t i = 0; i < num_elements; ++i) {
        assert(rh.put(as_K(i), i), "");
      }

      rh.iterate(&et);

      for (uintptr_t i = num_elements; i > 0; --i) {
        uintptr_t index = i - 1;
        assert(rh.remove(as_K(index)), "");
      }
      rh.iterate(&et);
      for (uintptr_t i = num_elements; i > 0; --i) {
        uintptr_t index = i - 1;
        assert(!rh.remove(as_K(index)), "");
      }
      rh.iterate(&et);
    }
  };

 public:
  static void run_tests() {
    {
      ResourceMark rm;
      Runner<>::test_small();
      Runner<>::test_small_shifted();
      Runner<>::test();
    }

    {
      ResourceMark rm;
      Runner<identity_hash>::test_small();
      Runner<identity_hash>::test_small_shifted();
      Runner<identity_hash>::test();
    }

    {
      ResourceMark rm;
      Runner<bad_hash>::test_small();
      Runner<bad_hash>::test_small_shifted();
      Runner<bad_hash>::test();
    }


    assert(Thread::current()->resource_area()->nesting() == 0, "this code depends on not having an active ResourceMark");
    // The following test calls will cause an assert if resource allocations occur since we don't have an active mark
    Runner<primitive_hash<K>, primitive_equals<K>, 512, ResourceObj::C_HEAP>::test_small();
    Runner<primitive_hash<K>, primitive_equals<K>, 512, ResourceObj::C_HEAP>::test_small_shifted();
    Runner<primitive_hash<K>, primitive_equals<K>, 512, ResourceObj::C_HEAP>::test();

    Runner<bad_hash, primitive_equals<K>, 512, ResourceObj::C_HEAP>::test_small();
    Runner<bad_hash, primitive_equals<K>, 512, ResourceObj::C_HEAP>::test_small_shifted();
    Runner<bad_hash, primitive_equals<K>, 512, ResourceObj::C_HEAP>::test();

    Runner<identity_hash, primitive_equals<K>, 1, ResourceObj::C_HEAP>::test_small();
    Runner<identity_hash, primitive_equals<K>, 1, ResourceObj::C_HEAP>::test_small_shifted();
    Runner<identity_hash, primitive_equals<K>, 1, ResourceObj::C_HEAP>::test(512);
  }
};

void TestResourcehash_test() {
  TestResourceHashtable::run_tests();
}

#endif // not PRODUCT

