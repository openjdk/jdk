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

 */

#include "precompiled.hpp"
#include "unittest.hpp"
#include "utilities/indexedFreeListAllocator.hpp"

class TIFLA {
public:
  // A linked list which sets the allocator itself
  template<typename E>
  struct LL {
    struct Node;
    using NodeAllocator = IndexedFreeListAllocator<Node, mtTest>;
    using NodePtr = typename NodeAllocator::I;
    NodeAllocator alloc;
    struct Node {
      E e;
      NodePtr next;
      Node(E e, NodePtr next)
        : e(e),
          next(next) {
      }
    };

    NodePtr start;
    LL() : start(NodeAllocator::nil) {

    }

    void append(E&& e) {
      NodePtr new_element = alloc.allocate(e, NodeAllocator::nil);
      NodePtr& current = start;
      if (current == NodeAllocator::nil) {
        current = new_element;
        return;
      }
      while (alloc[current].next != NodeAllocator::nil) {
        current = alloc[current].next;
      }
      alloc[current].next = new_element;
    };

    E pop() {
      assert(start != NodeAllocator::nil, "must be");
      Node& n = alloc[start];
      E e = n.e;
      alloc.free(start);
      start = n.next;
      return e;
    }

  };
  // A linked list which is capable of having multiple different allocators. This is done through higher-kinded types.
  // That's a very fancy word that means that a templated type like Foo<E> can be passed around like only Foo at first
  // and then be 'applied' to some E. Think of it like passing around a lambda or function pointer, but on a template level,
  // where Foo is a function that can be called on some type with the return type being Foo<E>.
  template<typename E, template<typename, MEMFLAGS> class Allocator>
  struct LL2 {
    struct Node;
    using NodeAllocator = Allocator<Node, mtTest>;
    using NodePtr = typename NodeAllocator::I;
    NodeAllocator alloc;
    struct Node {
      E e;
      NodePtr next;
      Node(E e, NodePtr next)
        : e(e),
          next(next) {
      }
    };

    NodePtr start;
    LL2() : start(NodeAllocator::nil) {}

    void append(E&& e) {
      NodePtr new_element = alloc.allocate(e, NodeAllocator::nil);
      NodePtr& current = start;
      if (current == NodeAllocator::nil) {
        current = new_element;
        return;
      }
      while (alloc[current].next != NodeAllocator::nil) {
        current = alloc[current].next;
      }
      alloc[current].next = new_element;
    };

    E pop() {
      assert(start != NodeAllocator::nil, "must be");
      Node& n = alloc[start];
      E e = n.e;
      alloc.free(start);
      start = n.next;
      return e;
    }

  };
};

TEST_VM(IFLATest, TestIt) {
  {
    TIFLA::LL<int> list;
    list.append(1);
    list.append(2);
    tty->print_cr("%d", list.pop());
    tty->print_cr("%d", list.pop());
  }
  {
    TIFLA::LL2<int, IndexedFreeListAllocator> list;
    tty->print_cr("size: %lu", sizeof(TIFLA::LL2<int, IndexedFreeListAllocator>::Node));
    list.append(1);
    list.append(2);
    tty->print_cr("%d", list.pop());
    tty->print_cr("%d", list.pop());
  }
  {
    TIFLA::LL2<int, CHeapAllocator> list;
    tty->print_cr("size: %lu", sizeof(TIFLA::LL2<int, CHeapAllocator>::Node));
    list.append(1);
    list.append(2);
    tty->print_cr("%d", list.pop());
    tty->print_cr("%d", list.pop());
  }
}
