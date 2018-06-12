/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZMARKSTACK_HPP
#define SHARE_GC_Z_ZMARKSTACK_HPP

#include "gc/z/zGlobals.hpp"
#include "gc/z/zLock.hpp"
#include "gc/z/zMarkStackEntry.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

template <typename T, size_t S>
class ZStack {
private:
  size_t        _top;
  ZStack<T, S>* _next;
  T             _slots[S];

  bool is_full() const;

public:
  ZStack();

  bool is_empty() const;

  bool push(T value);
  bool pop(T& value);

  ZStack<T, S>* next() const;
  ZStack<T, S>** next_addr();
};

template <typename T>
class ZStackList {
private:
  T* volatile _head;

  T* encode_versioned_pointer(const T* stack, uint32_t version) const;
  void decode_versioned_pointer(const T* vstack, T** stack, uint32_t* version) const;

public:
  ZStackList();

  bool is_empty() const;

  void push_atomic(T* stack);
  T* pop_atomic();
};

typedef ZStack<ZMarkStackEntry, ZMarkStackSlots>     ZMarkStack;
typedef ZStackList<ZMarkStack>                       ZMarkStackList;
typedef ZStack<ZMarkStack*, ZMarkStackMagazineSlots> ZMarkStackMagazine;
typedef ZStackList<ZMarkStackMagazine>               ZMarkStackMagazineList;

class ZMarkStackSpace {
private:
  ZLock              _expand_lock;
  volatile uintptr_t _top;
  volatile uintptr_t _end;

  bool expand();

  uintptr_t alloc_space(size_t size);
  uintptr_t expand_and_alloc_space(size_t size);

public:
  ZMarkStackSpace();

  bool is_initialized() const;

  uintptr_t alloc(size_t size);
};

class ZMarkStackAllocator {
private:
  ZMarkStackMagazineList _freelist ATTRIBUTE_ALIGNED(ZCacheLineSize);
  ZMarkStackSpace        _space    ATTRIBUTE_ALIGNED(ZCacheLineSize);

  void prime_freelist();
  ZMarkStackMagazine* create_magazine_from_space(uintptr_t addr, size_t size);

public:
  ZMarkStackAllocator();

  bool is_initialized() const;

  ZMarkStackMagazine* alloc_magazine();
  void free_magazine(ZMarkStackMagazine* magazine);
};

class ZMarkStripe {
private:
  ZMarkStackList _published  ATTRIBUTE_ALIGNED(ZCacheLineSize);
  ZMarkStackList _overflowed ATTRIBUTE_ALIGNED(ZCacheLineSize);

public:
  ZMarkStripe();

  bool is_empty() const;

  void publish_stack(ZMarkStack* stack, bool publish = true);
  ZMarkStack* steal_stack();
};

class ZMarkStripeSet {
private:
  size_t      _nstripes;
  size_t      _nstripes_mask;
  ZMarkStripe _stripes[ZMarkStripesMax];

public:
  ZMarkStripeSet();

  size_t nstripes() const;
  void set_nstripes(size_t nstripes);

  bool is_empty() const;

  size_t stripe_id(const ZMarkStripe* stripe) const;
  ZMarkStripe* stripe_at(size_t index);
  ZMarkStripe* stripe_next(ZMarkStripe* stripe);
  ZMarkStripe* stripe_for_worker(uint nworkers, uint worker_id);
  ZMarkStripe* stripe_for_addr(uintptr_t addr);
};

class ZMarkThreadLocalStacks {
private:
  ZMarkStackMagazine* _magazine;
  ZMarkStack*         _stacks[ZMarkStripesMax];

  ZMarkStack* allocate_stack(ZMarkStackAllocator* allocator);
  void free_stack(ZMarkStackAllocator* allocator, ZMarkStack* stack);

  bool push_slow(ZMarkStackAllocator* allocator,
                 ZMarkStripe* stripe,
                 ZMarkStack** stackp,
                 ZMarkStackEntry entry,
                 bool publish);

  bool pop_slow(ZMarkStackAllocator* allocator,
                ZMarkStripe* stripe,
                ZMarkStack** stackp,
                ZMarkStackEntry& entry);

public:
  ZMarkThreadLocalStacks();

  bool is_empty(const ZMarkStripeSet* stripes) const;

  void install(ZMarkStripeSet* stripes,
               ZMarkStripe* stripe,
               ZMarkStack* stack);

  bool push(ZMarkStackAllocator* allocator,
            ZMarkStripeSet* stripes,
            ZMarkStripe* stripe,
            ZMarkStackEntry entry,
            bool publish);

  bool pop(ZMarkStackAllocator* allocator,
           ZMarkStripeSet* stripes,
           ZMarkStripe* stripe,
           ZMarkStackEntry& entry);

  bool flush(ZMarkStackAllocator* allocator,
             ZMarkStripeSet* stripes);

  void free(ZMarkStackAllocator* allocator);
};

#endif // SHARE_GC_Z_ZMARKSTACK_HPP
