/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_KLASSIDARRAY_HPP
#define SHARE_OOPS_KLASSIDARRAY_HPP

#include "memory/allStatic.hpp"
#include "runtime/atomic.hpp"

class ClassLoaderData;
class Klass;
class outputStream;

// Pointer to Klass.
class KlassIdArray : public AllStatic {
  static int _next;
  static int _free;
  static Klass** _the_compressed_klasses;

  // Puts the klass in the array if it doesn't already exist, and returns
  // the index, which is really the klass ID
  static void add_klass(Klass* k);
  static void release_klass(Klass* k);
 public:
  static address base() { return (address)_the_compressed_klasses; }

  static Klass* at(int index) {
    assert(index > 0 && index < _next, "oob %d", index);
    Klass* k = _the_compressed_klasses[index];
    assert(k != nullptr, "shouldn't be reading bad klass");
    assert(k->compressed_id() == index, "should be");
    return k;
  }

  static void set_next_compressed_id(Klass* k) {
    int kid = k->compressed_id();
    if (kid == 0) {
      add_klass(k);
    }
  }

  static void release_unloaded_klasses(ClassLoaderData* cld);
  static void initialize();
  static void initialize(Array<Klass*>* from_shared_space);
  static void print_on(outputStream* st);
};

#endif // SHARE_OOPS_KLASSIDARRAY_HPP
