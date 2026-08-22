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
  static size_t hash(int x) {
    return x & 0xFF;
  }
  static bool equals(int v1, int v2) {
    return v1 == v2;
  }
};

TEST(NMTOAHTTest, Basic) {
  auto key = [](const KVElement& kv) { return kv.k; };
  auto hash = [](int x) { return KVElement::hash(x); };
  auto equals = [](int v1, int v2) { return KVElement::equals(v1, v2); };
  using BasicHT = OpenAddressedHashTable<KVElement,
                                         decltype(key),
                                         decltype(hash),
                                         decltype(equals)>;

  BasicHT ht(key, hash, equals);
  KVElement kv{1, 1};
  bool found = false;
  ht.put_if_absent(kv, &found);
  assert(found == false, "");
  assert(ht.occupied() == 1, "");
  kv.v = 0;
  KVElement* found_kv = ht.put_if_absent(kv, &found);
  assert(found == true, "");
  assert(found_kv->v == 1, "");
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
  auto key = [](const PointerKeyElement& kv) -> const PointerKey& {
    return *kv.key();
  };
  auto hash = [](const PointerKey& key) { return key.hash(); };
  auto equals = [](const PointerKey& v1, const PointerKey& v2) {
    return v1.equals(v2);
  };
  using PointerKeyHT = OpenAddressedHashTable<PointerKeyElement,
                                             decltype(key),
                                             decltype(hash),
                                             decltype(equals)>;

  PointerKeyHT ht(key, hash, equals);
  PointerKeyElement kv{{1}, 1};
  bool found = false;
  ht.put_if_absent(kv, &found);
  assert(found == false, "");
  assert(ht.occupied() == 1, "");
  kv._v = 0;
  PointerKeyElement* found_kv = ht.put_if_absent(kv, &found);
  assert(found == true, "");
  assert(found_kv->_v == 1, "");
}
