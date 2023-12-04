/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "oops/symbolHandle.hpp"
#include "unittest.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/resourceHash.hpp"

class CommonResourceHashtableTest : public ::testing::Test {
 protected:
  typedef void* K;
  typedef uintx V;
  const static MEMFLAGS MEM_TYPE = mtInternal;

  static unsigned identity_hash(const K& k) {
    return (unsigned) (uintptr_t) k;
  }

  static unsigned bad_hash(const K& k) {
    return 1;
  }

  static void* as_K(uintptr_t val) {
    return (void*) val;
  }

  class EqualityTestIter {
   public:

    bool do_entry(K const& k, V const& v) {
      if ((uintptr_t) k != (uintptr_t) v) {
        EXPECT_EQ((uintptr_t) k, (uintptr_t) v);
        return false;
      } else {
        return true; // continue iteration
      }
    }
  };

  class DeleterTestIter {
    int _val;
   public:
    DeleterTestIter(int i) : _val(i) {}

    bool do_entry(K const& k, V const& v) {
      if ((uintptr_t) k == (uintptr_t) _val) {
        // Delete me!
        return true;
      } else {
        return false; // continue iteration
      }
    }
  };

};

class SmallResourceHashtableTest : public CommonResourceHashtableTest {
 protected:

  template<
  unsigned (*HASH) (K const&) = primitive_hash<K>,
  bool (*EQUALS)(K const&, K const&) = primitive_equals<K>,
  unsigned SIZE = 256,
  AnyObj::allocation_type ALLOC_TYPE = AnyObj::RESOURCE_AREA
  >
  class Runner : public AllStatic {
   public:

    static void test(V step) {
      EqualityTestIter et;
      ResourceHashtable<K, V, SIZE, ALLOC_TYPE, MEM_TYPE, HASH, EQUALS> rh;

      ASSERT_FALSE(rh.contains(as_K(step)));

      ASSERT_TRUE(rh.put(as_K(step), step));
      ASSERT_TRUE(rh.contains(as_K(step)));

      ASSERT_FALSE(rh.put(as_K(step), step));

      ASSERT_TRUE(rh.put(as_K(2 * step), 2 * step));
      ASSERT_TRUE(rh.put(as_K(3 * step), 3 * step));
      ASSERT_TRUE(rh.put(as_K(4 * step), 4 * step));
      ASSERT_TRUE(rh.put(as_K(5 * step), 5 * step));

      ASSERT_FALSE(rh.remove(as_K(0x0)));

      rh.iterate(&et);
      if (::testing::Test::HasFailure()) {
        return;
      }

      ASSERT_TRUE(rh.remove(as_K(step)));
      ASSERT_FALSE(rh.contains(as_K(step)));
      rh.iterate(&et);


      // Test put_if_absent(key) (creating a default-created value)
      bool created = false;
      V* v = rh.put_if_absent(as_K(step), &created);
      ASSERT_TRUE(rh.contains(as_K(step)));
      ASSERT_TRUE(created);
      *v = (V)step;

      // Calling this function a second time should yield the same value pointer
      V* v2 = rh.put_if_absent(as_K(step), &created);
      ASSERT_EQ(v, v2);
      ASSERT_EQ(*v2, *v);
      ASSERT_FALSE(created);

      ASSERT_TRUE(rh.remove(as_K(step)));
      ASSERT_FALSE(rh.contains(as_K(step)));
      rh.iterate(&et);

      // Test put_if_absent(key, value)
      v = rh.put_if_absent(as_K(step), step, &created);
      ASSERT_EQ(*v, step);
      ASSERT_TRUE(rh.contains(as_K(step)));
      ASSERT_TRUE(created);

      v2 = rh.put_if_absent(as_K(step), step, &created);
      // Calling this function a second time should yield the same value pointer
      ASSERT_EQ(v, v2);
      ASSERT_EQ(*v2, (V)step);
      ASSERT_FALSE(created);

      ASSERT_TRUE(rh.remove(as_K(step)));
      ASSERT_FALSE(rh.contains(as_K(step)));
      rh.iterate(&et);


    }
  };
};

TEST_VM_F(SmallResourceHashtableTest, default) {
  ResourceMark rm;
  Runner<>::test(0x1);
}

TEST_VM_F(SmallResourceHashtableTest, default_shifted) {
  ResourceMark rm;
  Runner<>::test(0x10);
}

TEST_VM_F(SmallResourceHashtableTest, bad_hash) {
  ResourceMark rm;
  Runner<bad_hash>::test(0x1);
}

TEST_VM_F(SmallResourceHashtableTest, bad_hash_shifted) {
  ResourceMark rm;
  Runner<bad_hash>::test(0x10);
}

TEST_VM_F(SmallResourceHashtableTest, identity_hash) {
  ResourceMark rm;
  Runner<identity_hash>::test(0x1);
}

TEST_VM_F(SmallResourceHashtableTest, identity_hash_shifted) {
  ResourceMark rm;
  Runner<identity_hash>::test(0x10);
}

TEST_VM_F(SmallResourceHashtableTest, primitive_hash_no_rm) {
  Runner<primitive_hash<K>, primitive_equals<K>, 512, AnyObj::C_HEAP>::test(0x1);
}

TEST_VM_F(SmallResourceHashtableTest, primitive_hash_no_rm_shifted) {
  Runner<primitive_hash<K>, primitive_equals<K>, 512, AnyObj::C_HEAP>::test(0x10);
}

TEST_VM_F(SmallResourceHashtableTest, bad_hash_no_rm) {
  Runner<bad_hash, primitive_equals<K>, 512, AnyObj::C_HEAP>::test(0x1);
}

TEST_VM_F(SmallResourceHashtableTest, bad_hash_no_rm_shifted) {
  Runner<bad_hash, primitive_equals<K>, 512, AnyObj::C_HEAP>::test(0x10);
}

TEST_VM_F(SmallResourceHashtableTest, identity_hash_no_rm) {
  Runner<identity_hash, primitive_equals<K>, 1, AnyObj::C_HEAP>::test(0x1);
}

TEST_VM_F(SmallResourceHashtableTest, identity_hash_no_rm_shifted) {
  Runner<identity_hash, primitive_equals<K>, 1, AnyObj::C_HEAP>::test(0x10);
}

class GenericResourceHashtableTest : public CommonResourceHashtableTest {
 protected:

  template<
  unsigned (*HASH) (K const&) = primitive_hash<K>,
  bool (*EQUALS)(K const&, K const&) = primitive_equals<K>,
  unsigned SIZE = 256,
  AnyObj::allocation_type ALLOC_TYPE = AnyObj::RESOURCE_AREA
  >
  class Runner : public AllStatic {
   public:

    static void test(unsigned num_elements = SIZE) {
      EqualityTestIter et;
      ResourceHashtable<K, V, SIZE, ALLOC_TYPE, MEM_TYPE, HASH, EQUALS> rh;

      for (uintptr_t i = 0; i < num_elements; ++i) {
        ASSERT_TRUE(rh.put(as_K(i), i));
      }

      rh.iterate(&et);
      if (::testing::Test::HasFailure()) {
        return;
      }

      for (uintptr_t i = num_elements; i > 0; --i) {
        uintptr_t index = i - 1;
        ASSERT_TRUE((rh.remove(as_K(index))));
      }

      rh.iterate(&et);
      if (::testing::Test::HasFailure()) {
        return;
      }
      for (uintptr_t i = num_elements; i > 0; --i) {
        uintptr_t index = i - 1;
        ASSERT_FALSE(rh.remove(as_K(index)));
      }
      rh.iterate(&et);

      // Add more entries in and then delete one.
      for (uintptr_t i = 10; i > 0; --i) {
        uintptr_t index = i - 1;
        ASSERT_TRUE(rh.put(as_K(index), index));
      }
      DeleterTestIter dt(5);
      rh.unlink(&dt);
      ASSERT_FALSE(rh.get(as_K(5)));
    }
  };
};

TEST_VM_F(GenericResourceHashtableTest, default) {
  ResourceMark rm;
  Runner<>::test();
}

TEST_VM_F(GenericResourceHashtableTest, bad_hash) {
  ResourceMark rm;
  Runner<bad_hash>::test();
}

TEST_VM_F(GenericResourceHashtableTest, identity_hash) {
  ResourceMark rm;
  Runner<identity_hash>::test();
}

TEST_VM_F(GenericResourceHashtableTest, primitive_hash_no_rm) {
  Runner<primitive_hash<K>, primitive_equals<K>, 512, AnyObj::C_HEAP>::test();
}

TEST_VM_F(GenericResourceHashtableTest, bad_hash_no_rm) {
  Runner<bad_hash, primitive_equals<K>, 512, AnyObj::C_HEAP>::test();
}

TEST_VM_F(GenericResourceHashtableTest, identity_hash_no_rm) {
  Runner<identity_hash, primitive_equals<K>, 1, AnyObj::C_HEAP>::test(512);
}

// Simple ResourceHashtable whose key is a SymbolHandle and value is an int
// This test is to show that the SymbolHandle will correctly handle the refcounting
// in the table.
class SimpleResourceHashtableDeleteTest : public ::testing::Test {
 public:
    ResourceHashtable<SymbolHandle, int, 107, AnyObj::C_HEAP, mtTest, SymbolHandle::compute_hash> _simple_test_table;

    class SimpleDeleter : public StackObj {
      public:
        bool do_entry(SymbolHandle& key, int value) {
          return true;
        }
    };
};

TEST_VM_F(SimpleResourceHashtableDeleteTest, simple_remove) {
  TempNewSymbol t = SymbolTable::new_symbol("abcdefg_simple");
  Symbol* s = t;
  int s_orig_count = s->refcount();
  _simple_test_table.put(s, 55);
  ASSERT_EQ(s->refcount(), s_orig_count + 1) << "refcount should be incremented in table";

  // Deleting this value from a hashtable
  _simple_test_table.remove(s);
  ASSERT_EQ(s->refcount(), s_orig_count) << "refcount should be same as start";
}

TEST_VM_F(SimpleResourceHashtableDeleteTest, simple_delete) {
  TempNewSymbol t = SymbolTable::new_symbol("abcdefg_simple");
  Symbol* s = t;
  int s_orig_count = s->refcount();
  _simple_test_table.put(s, 66);
  ASSERT_EQ(s->refcount(), s_orig_count + 1) << "refcount should be incremented in table";

  // Use unlink to remove the matching (or all) values from the table.
  SimpleDeleter deleter;
  _simple_test_table.unlink(&deleter);
  ASSERT_EQ(s->refcount(), s_orig_count) << "refcount should be same as start";
}

// More complicated ResourceHashtable with SymbolHandle in the key. Since the *same* Symbol is part
// of the value, it's not necessary to manipulate the refcount of the key, but you must in the value.
// Luckily SymbolHandle does this.
class ResourceHashtableDeleteTest : public ::testing::Test {
 public:
    class TestValue : public CHeapObj<mtTest> {
        SymbolHandle _s;
      public:
        // Never have ctors and dtors fix refcounts without copy ctors and assignment operators!
        // Unless it's declared and used as a CHeapObj with
        // NONCOPYABLE(TestValue)

        // Using SymbolHandle deals with refcount manipulation so this class doesn't have to
        // have dtors, copy ctors and assignment operators to do so.
        TestValue(Symbol* name) : _s(name) { }
        // Symbol* s() const { return _s; }  // needed for conversion from TempNewSymbol to SymbolHandle member
    };

    // ResourceHashtable whose value is a *copy* of TestValue.
    ResourceHashtable<Symbol*, TestValue, 107, AnyObj::C_HEAP, mtTest> _test_table;

    class Deleter : public StackObj {
      public:
        bool do_entry(Symbol*& key, TestValue& value) {
          // Since we didn't increment the key, we shouldn't decrement it.
          // Calling delete on the hashtable Node which contains value will
          // decrement the refcount.  That's actually best since the whole
          // entry will be gone at once.
          return true;
        }
    };

    // ResourceHashtable whose value is a pointer to TestValue.
    ResourceHashtable<Symbol*, TestValue*, 107, AnyObj::C_HEAP, mtTest> _ptr_test_table;

    class PtrDeleter : public StackObj {
      public:
        bool do_entry(Symbol*& key, TestValue*& value) {
          // If the hashtable value is a pointer, need to delete it from here.
          // This will also potentially make the refcount of the Key = 0, but the
          // next thing that happens is that the hashtable node is deleted so this is ok.
          delete value;
          return true;
        }
    };
};


TEST_VM_F(ResourceHashtableDeleteTest, value_remove) {
  TempNewSymbol s = SymbolTable::new_symbol("abcdefg");
  int s_orig_count = s->refcount();
  {
    TestValue tv(s);
    // Since TestValue contains the pointer to the key, it will handle the
    // refcounting.
    _test_table.put(s, tv);
    ASSERT_EQ(s->refcount(), s_orig_count + 2) << "refcount incremented by copy";
  }
  ASSERT_EQ(s->refcount(), s_orig_count + 1) << "refcount incremented in table";

  // Deleting this value from a hashtable calls the destructor!
  _test_table.remove(s);
  // Removal should make the refcount be the original refcount.
  ASSERT_EQ(s->refcount(), s_orig_count) << "refcount should be as we started";
}

TEST_VM_F(ResourceHashtableDeleteTest, value_delete) {
  TempNewSymbol d = SymbolTable::new_symbol("defghijklmnop");
  int d_orig_count = d->refcount();
  {
    TestValue tv(d);
    // Same as above, but the do_entry does nothing because the value is deleted when the
    // hashtable node is deleted.
    _test_table.put(d, tv);
    ASSERT_EQ(d->refcount(), d_orig_count + 2) << "refcount incremented by copy";
  }
  ASSERT_EQ(d->refcount(), d_orig_count + 1) << "refcount incremented in table";
  Deleter deleter;
  _test_table.unlink(&deleter);
  ASSERT_EQ(d->refcount(), d_orig_count) << "refcount should be as we started";
}

TEST_VM_F(ResourceHashtableDeleteTest, check_delete_ptr) {
  TempNewSymbol s = SymbolTable::new_symbol("abcdefg_ptr");
  int s_orig_count = s->refcount();
  {
    TestValue* tv = new TestValue(s);
    // Again since TestValue contains the pointer to the key Symbol, it will
    // handle the refcounting.
    _ptr_test_table.put(s, tv);
    ASSERT_EQ(s->refcount(), s_orig_count + 1) << "refcount incremented by allocation";
  }
  ASSERT_EQ(s->refcount(), s_orig_count + 1) << "refcount incremented in table";

  // Deleting this pointer value from a hashtable must call the destructor in the
  // do_entry function.
  PtrDeleter deleter;
  _ptr_test_table.unlink(&deleter);
  // Removal should make the refcount be the original refcount.
  ASSERT_EQ(s->refcount(), s_orig_count) << "refcount should be as we started";
}

class ResourceHashtablePrintTest : public ::testing::Test {
 public:
    class TestValue {
      int _i;
      int _j;
      int _k;
     public:
      TestValue(int i) : _i(i), _j(i+1), _k(i+2) {}
    };
    ResourceHashtable<int, TestValue*, 30, AnyObj::C_HEAP, mtTest> _test_table;

    class TableDeleter {
     public:
      bool do_entry(int& key, TestValue*& val) {
        delete val;
        return true;
      }
    };
};

TEST_VM_F(ResourceHashtablePrintTest, print_test) {
  for (int i = 0; i < 300; i++) {
    TestValue* tv = new TestValue(i);
    _test_table.put(i, tv);  // all the entries can be the same.
  }
  auto printer = [&] (int& key, TestValue*& val) {
    return sizeof(*val);
  };
  TableStatistics ts = _test_table.statistics_calculate(printer);
  ResourceMark rm;
  stringStream st;
  ts.print(&st, "TestTable");
  // Verify output in string
  const char* strings[] = {
      "Number of buckets", "Number of entries", "300", "Number of literals", "Average bucket size", "Maximum bucket size" };
  for (const auto& str : strings) {
    ASSERT_THAT(st.base(), testing::HasSubstr(str));
  }
  // Cleanup: need to delete pointers in entries
  TableDeleter deleter;
  _test_table.unlink(&deleter);
}
