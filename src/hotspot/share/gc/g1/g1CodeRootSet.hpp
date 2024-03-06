/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CODEROOTSET_HPP
#define SHARE_GC_G1_G1CODEROOTSET_HPP

#include "code/codeCache.hpp"
#include "utilities/globalDefinitions.hpp"

class G1CodeRootSetHashTable;
class HeapRegion;
class nmethod;

// Implements storage for a set of code roots.
// This class is thread safe.
class G1CodeRootSet {
  G1CodeRootSetHashTable* _table;
  DEBUG_ONLY(mutable bool _is_iterating;)

 public:
  G1CodeRootSet();
  ~G1CodeRootSet();

  void add(nmethod* method);
  bool remove(nmethod* method);
  void bulk_remove();
  bool contains(nmethod* method);
  void clear();

  // Prepare for MT iteration. Must be called before nmethods_do.
  void reset_table_scanner();
  void nmethods_do(CodeBlobClosure* blk) const;

  // Remove all nmethods which no longer contain pointers into our "owner" region.
  void clean(HeapRegion* owner);

  bool is_empty() { return length() == 0;}

  // Length in elements
  size_t length() const;

  // Memory size in bytes taken by this set.
  size_t mem_size();
};

#endif // SHARE_GC_G1_G1CODEROOTSET_HPP
