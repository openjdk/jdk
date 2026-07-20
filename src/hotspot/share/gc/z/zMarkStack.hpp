/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zAttachedArray.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zMarkStackEntry.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

class ZMarkingSMR;
class ZMarkStripe;
class ZMarkTerminate;

class ZMarkStack {
private:
  using AttachedArray = ZAttachedArray<ZMarkStack, ZMarkStackEntry>;

  size_t              _top;
  const AttachedArray _entries;

  ZMarkStackEntry* slots();

  ZMarkStack(size_t capacity);

public:
  static ZMarkStack* create(bool first_stack);
  static void destroy(ZMarkStack* stack);

  bool is_empty() const;
  bool is_full() const;

  void push(ZMarkStackEntry value);
  ZMarkStackEntry pop();
};

class ZMarkStackListNode : public CHeapObj<mtGC> {
private:
  ZMarkStack* const   _stack;
  ZMarkStackListNode* _next;

public:
  ZMarkStackListNode(ZMarkStack* stack);

  ZMarkStack* stack() const;

  ZMarkStackListNode* next() const;
  void set_next(ZMarkStackListNode* next);
};

class ZCACHE_ALIGNED ZMarkStackList {
private:
  ZMarkStackListNode* volatile _head;
  ssize_t volatile             _length;

public:
  ZMarkStackList();

  bool is_empty() const;

  size_t length() const;

  void push(ZMarkStack* stack);
  ZMarkStack* pop(ZMarkingSMR* marking_smr);
};

class ZMarkStripe {
private:
  ZCACHE_ALIGNED ZMarkStackList _published;
  ZCACHE_ALIGNED ZMarkStackList _overflowed;

public:
  explicit ZMarkStripe();

  bool is_empty() const;
  size_t population() const;

  void publish_stack(ZMarkStack* stack, ZMarkTerminate* terminate, bool publish);
  ZMarkStack* steal_stack(ZMarkingSMR* marking_smr);
};

class ZMarkStripeSet {
private:
  size_t      _nstripes_mask;
  ZMarkStripe _stripes[ZMarkStripesMax];

public:
  explicit ZMarkStripeSet();

  void set_nstripes(size_t nstripes);
  bool try_set_nstripes(size_t old_nstripes, size_t new_nstripes);
  size_t nstripes() const;

  bool is_empty() const;
  bool is_crowded() const;

  size_t stripe_id(const ZMarkStripe* stripe) const;
  ZMarkStripe* stripe_at(size_t index);
  ZMarkStripe* stripe_next(ZMarkStripe* stripe);
  ZMarkStripe* stripe_for_worker(uint nworkers, uint worker_id);
  ZMarkStripe* stripe_for_addr(uintptr_t addr);
};

class ZMarkThreadLocalStacks {
private:
  ZMarkStack* _stacks[ZMarkStripesMax];

public:
  ZMarkThreadLocalStacks();

  bool is_empty(const ZMarkStripeSet* stripes) const;

  void install(ZMarkStripeSet* stripes,
               ZMarkStripe* stripe,
               ZMarkStack* stack);

  ZMarkStack* steal(ZMarkStripeSet* stripes,
                    ZMarkStripe* stripe);

  void push(ZMarkStripeSet* stripes,
            ZMarkStripe* stripe,
            ZMarkTerminate* terminate,
            ZMarkStackEntry entry,
            bool publish);

  bool pop(ZMarkingSMR* marking_smr,
           ZMarkStripeSet* stripes,
           ZMarkStripe* stripe,
           ZMarkStackEntry* entry);

  bool flush(ZMarkStripeSet* stripes,
             ZMarkTerminate* terminate);
};

#endif // SHARE_GC_Z_ZMARKSTACK_HPP
