/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XMARKSTACK_HPP
#define SHARE_GC_X_XMARKSTACK_HPP

#include "gc/x/xGlobals.hpp"
#include "gc/x/xMarkStackEntry.hpp"
#include "utilities/globalDefinitions.hpp"

template <typename T, size_t S>
class XStack {
private:
  size_t        _top;
  XStack<T, S>* _next;
  T             _slots[S];

  bool is_full() const;

public:
  XStack();

  bool is_empty() const;

  bool push(T value);
  bool pop(T& value);

  XStack<T, S>* next() const;
  XStack<T, S>** next_addr();
};

template <typename T>
class XStackList {
private:
  T* volatile _head;

  T* encode_versioned_pointer(const T* stack, uint32_t version) const;
  void decode_versioned_pointer(const T* vstack, T** stack, uint32_t* version) const;

public:
  XStackList();

  bool is_empty() const;

  void push(T* stack);
  T* pop();

  void clear();
};

using XMarkStack = XStack<XMarkStackEntry, XMarkStackSlots>;
using XMarkStackList = XStackList<XMarkStack>;
using XMarkStackMagazine = XStack<XMarkStack*, XMarkStackMagazineSlots>;
using XMarkStackMagazineList = XStackList<XMarkStackMagazine>;

static_assert(sizeof(XMarkStack) == XMarkStackSize, "XMarkStack size mismatch");
static_assert(sizeof(XMarkStackMagazine) <= XMarkStackSize, "XMarkStackMagazine size too large");

class XMarkStripe {
private:
  XCACHE_ALIGNED XMarkStackList _published;
  XCACHE_ALIGNED XMarkStackList _overflowed;

public:
  XMarkStripe();

  bool is_empty() const;

  void publish_stack(XMarkStack* stack, bool publish = true);
  XMarkStack* steal_stack();
};

class XMarkStripeSet {
private:
  size_t      _nstripes;
  size_t      _nstripes_mask;
  XMarkStripe _stripes[XMarkStripesMax];

public:
  XMarkStripeSet();

  size_t nstripes() const;
  void set_nstripes(size_t nstripes);

  bool is_empty() const;

  size_t stripe_id(const XMarkStripe* stripe) const;
  XMarkStripe* stripe_at(size_t index);
  XMarkStripe* stripe_next(XMarkStripe* stripe);
  XMarkStripe* stripe_for_worker(uint nworkers, uint worker_id);
  XMarkStripe* stripe_for_addr(uintptr_t addr);
};

class XMarkStackAllocator;

class XMarkThreadLocalStacks {
private:
  XMarkStackMagazine* _magazine;
  XMarkStack*         _stacks[XMarkStripesMax];

  XMarkStack* allocate_stack(XMarkStackAllocator* allocator);
  void free_stack(XMarkStackAllocator* allocator, XMarkStack* stack);

  bool push_slow(XMarkStackAllocator* allocator,
                 XMarkStripe* stripe,
                 XMarkStack** stackp,
                 XMarkStackEntry entry,
                 bool publish);

  bool pop_slow(XMarkStackAllocator* allocator,
                XMarkStripe* stripe,
                XMarkStack** stackp,
                XMarkStackEntry& entry);

public:
  XMarkThreadLocalStacks();

  bool is_empty(const XMarkStripeSet* stripes) const;

  void install(XMarkStripeSet* stripes,
               XMarkStripe* stripe,
               XMarkStack* stack);

  XMarkStack* steal(XMarkStripeSet* stripes,
                    XMarkStripe* stripe);

  bool push(XMarkStackAllocator* allocator,
            XMarkStripeSet* stripes,
            XMarkStripe* stripe,
            XMarkStackEntry entry,
            bool publish);

  bool pop(XMarkStackAllocator* allocator,
           XMarkStripeSet* stripes,
           XMarkStripe* stripe,
           XMarkStackEntry& entry);

  bool flush(XMarkStackAllocator* allocator,
             XMarkStripeSet* stripes);

  void free(XMarkStackAllocator* allocator);
};

#endif // SHARE_GC_X_XMARKSTACK_HPP
