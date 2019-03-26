/*
 * Copyright (c) 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_SERVICES_THREADSTACKTRACKER_HPP
#define SHARE_SERVICES_THREADSTACKTRACKER_HPP

#if INCLUDE_NMT

#include "services/allocationSite.hpp"
#include "services/mallocSiteTable.hpp"
#include "services/nmtCommon.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/linkedlist.hpp"

class SimpleThreadStackSite;

class SimpleThreadStack {
  friend class SimpleThreadStackSite;
private:
  address _base;
  size_t  _size;
public:
  SimpleThreadStack() : _base(NULL), _size(0) { }
  bool equals(const SimpleThreadStack& s) const {
    return base() == s.base();
  }

  size_t  size() const { return _size; }
  address base() const { return _base; }
private:
  void set_size(size_t size)  { _size = size; }
  void set_base(address base) { _base = base; }
};

class SimpleThreadStackSite : public AllocationSite<SimpleThreadStack> {
public:
  SimpleThreadStackSite(address base, size_t size, const NativeCallStack& stack) :
    AllocationSite<SimpleThreadStack>(stack, mtThreadStack) {
    data()->set_size(size);
    data()->set_base(base);
  }

  SimpleThreadStackSite(address base, size_t size) :
    AllocationSite<SimpleThreadStack>(NativeCallStack::empty_stack(), mtThreadStack) {
    data()->set_base(base);
    data()->set_size(size);
  }

  bool equals(const SimpleThreadStackSite& mts) const {
    bool eq = base() == mts.base();
    assert(!eq || size() == mts.size(), "Must match");
    return eq;
  }

  size_t  size() const { return peek()->size(); }
  address base() const { return peek()->base(); }
};

  /*
   * Most of platforms, that hotspot support, have their thread stacks backed by
   * virtual memory by default. For these cases, thread stack tracker simply
   * delegates tracking to virtual memory tracker.
   * However, there are exceptions, (e.g. AIX), that platforms can provide stacks
   * that are not page aligned. A hypothetical VM implementation, it can provide
   * it own stacks. In these case, track_as_vm() should return false and manage
   * stack tracking by this tracker internally.
   * During memory snapshot, tracked thread stacks memory data is walked and stored
   * along with malloc'd data inside baseline. The regions are not scanned and assumed
   * all committed for now. Can add scanning phase when there is a need.
   */
class ThreadStackTracker : AllStatic {
private:
  static volatile size_t _thread_count;

  static int compare_thread_stack_base(const SimpleThreadStackSite& s1, const SimpleThreadStackSite& s2);
  static SortedLinkedList<SimpleThreadStackSite, compare_thread_stack_base>* _simple_thread_stacks;
public:
  // Late phase initialization
  static bool late_initialize(NMT_TrackingLevel level);
  static bool transition(NMT_TrackingLevel from, NMT_TrackingLevel to);

  static void new_thread_stack(void* base, size_t size, const NativeCallStack& stack);
  static void delete_thread_stack(void* base, size_t size);

  static bool   track_as_vm()  { return AIX_ONLY(false) NOT_AIX(true); }
  static size_t thread_count() { return _thread_count; }

  // Snapshot support. Piggyback thread stack data in malloc slot, NMT always handles
  // thread stack slot specially since beginning.
  static bool walk_simple_thread_stack_site(MallocSiteWalker* walker);
};

#endif // INCLUDE_NMT
#endif // SHARE_SERVICES_THREADSTACKTRACKER_HPP
