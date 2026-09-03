/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "nmt/nmtHashTable.hpp"
#include "unittest.hpp"


struct KVElement {
  int k;
  int v;
  static int hash(const KVElement& a) {
    return a.k & 0xFF;
  }
  static bool equals(const KVElement& a, const KVElement& b) {
    return a.k == b.k;
  }
};

TEST(NMTOAHTTest, Basic) {
  using BasicHT = OpenAddressedHashTable<KVElement,
                                         decltype(&KVElement::hash),
                                         decltype(&KVElement::equals)>;

  BasicHT ht(&KVElement::hash, &KVElement::equals);
  KVElement kv{1, 1};
  bool found = false;
  ht.put_if_absent(kv, &found);
  EXPECT_FALSE(found);
  EXPECT_EQ(1, ht.occupied());
  kv.v = 0;
  KVElement* found_kv = ht.put_if_absent(kv, &found);
  EXPECT_TRUE(found);
  EXPECT_EQ(1, found_kv->v);
}

struct PointerKey {
  int value;

  unsigned int hash() const {
    return value;
  }

  bool equals(const PointerKey& other) const {
    return value == other.value;
  }
};

struct PointerKeyElement {
  PointerKey _key;
  int _v;

  const PointerKey* key() const {
    return &_key;
  }
};

TEST(NMTOAHTTest, PointerKeyAccessor) {
  auto hash = [](const PointerKeyElement& kv) { return kv.key()->hash(); };
  auto equals = [](const PointerKeyElement& a, const PointerKeyElement& b) {
    return a.key()->equals(*b.key());
  };
  using PointerKeyHT = OpenAddressedHashTable<PointerKeyElement,
                                             decltype(hash),
                                             decltype(equals)>;

  PointerKeyHT ht(hash, equals);
  PointerKeyElement kv{{1}, 1};
  bool found = false;
  ht.put_if_absent(kv, &found);
  EXPECT_FALSE(found);
  EXPECT_EQ(1, ht.occupied());
  kv._v = 0;
  PointerKeyElement* found_kv = ht.put_if_absent(kv, &found);
  EXPECT_TRUE(found);
  EXPECT_EQ(1, found_kv->_v);
}

TEST(NMTOAHTTest, Detach) {
  auto hash = [](const PointerKeyElement& kv) { return kv.key()->hash(); };
  auto equals = [](const PointerKeyElement& a, const PointerKeyElement& b) {
    return a.key()->equals(*b.key());
  };
  using PointerKeyHT = OpenAddressedHashTable<PointerKeyElement,
                                              decltype(hash),
                                              decltype(equals)>;
  PointerKeyHT ht(hash, equals);
  bool found = false;
  PointerKeyElement kv{{1}, 1};
  ht.put_if_absent(kv, &found);
  PointerKeyElement kv2{{2}, 1};
  ht.put_if_absent(kv2, &found);

  // Check state of returned array
  int len = 0;
  PointerKeyElement* array = ht.detach(&len);
  EXPECT_EQ(2, len);
  // Check both elements are inserted
  bool found_elts[2] = {false, false};
  for (int i = 0; i < len; i++) {
    PointerKeyElement x = array[i];
    found_elts[0] = found_elts[0] || x.key()->equals(*kv.key());
    found_elts[1] = found_elts[1] || x.key()->equals(*kv2.key());
  }
  EXPECT_TRUE(found_elts[0]);
  EXPECT_TRUE(found_elts[1]);

  // Check state of the hashtable
  EXPECT_EQ(0, ht.occupied());
  found = false;
  EXPECT_NE(nullptr, ht.put_if_absent(kv, &found));
  EXPECT_FALSE(found);
  EXPECT_NE(nullptr, ht.put_if_absent(kv2, &found));
  EXPECT_FALSE(found);

  FREE_C_HEAP_ARRAY(array);
}
