/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "code/codeCache.hpp"
#include "code/nmethod.hpp"
#include "gc/shared/scavengableNMethods.hpp"
#include "gc/shared/scavengableNMethodsData.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"

static ScavengableNMethodsData gc_data(nmethod* nm) {
  return ScavengableNMethodsData(nm);
}

nmethod* ScavengableNMethods::_head = nullptr;
BoolObjectClosure* ScavengableNMethods::_is_scavengable = nullptr;

void ScavengableNMethods::initialize(BoolObjectClosure* is_scavengable) {
  _is_scavengable = is_scavengable;
}

// Conditionally adds the nmethod to the list if it is
// not already on the list and has a scavengeable root.
void ScavengableNMethods::register_nmethod(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);

  ScavengableNMethodsData data = gc_data(nm);

  if (data.on_list() || !has_scavengable_oops(nm)) {
    return;
  }

  data.set_on_list();
  data.set_next(_head);

  _head = nm;
}

void ScavengableNMethods::unregister_nmethod(nmethod* nm) {
  // All users of this method only unregister in bulk during code unloading.
  ShouldNotReachHere();
}

#ifndef PRODUCT

class DebugScavengableOops: public OopClosure {
  BoolObjectClosure* _is_scavengable;
  nmethod*           _nm;
  bool               _ok;
public:
  DebugScavengableOops(BoolObjectClosure* is_scavengable, nmethod* nm) :
      _is_scavengable(is_scavengable),
      _nm(nm),
      _ok(true) { }

  bool ok() { return _ok; }
  virtual void do_oop(oop* p) {
    if (*p == nullptr || !_is_scavengable->do_object_b(*p)) {
      return;
    }

    if (_ok) {
      _nm->print_nmethod(true);
      _ok = false;
    }
    tty->print_cr("*** scavengable oop " PTR_FORMAT " found at " PTR_FORMAT " (offset %d)",
                  p2i(*p), p2i(p), (int)((intptr_t)p - (intptr_t)_nm));
    (*p)->print();
  }
  virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }
};

#endif // PRODUCT

void ScavengableNMethods::verify_nmethod(nmethod* nm) {
#ifndef PRODUCT
  if (!gc_data(nm).on_list()) {
    // Actually look inside, to verify the claim that it's clean.
    DebugScavengableOops cl(_is_scavengable, nm);
    nm->oops_do(&cl);
    if (!cl.ok())
      fatal("found an unadvertised bad scavengable oop in the code cache");
  }
  assert(gc_data(nm).not_marked(), "");
#endif // PRODUCT
}

bool ScavengableNMethods::has_scavengable_oops(nmethod* nm) {
  struct HasScavengableOops: public OopClosure {
    BoolObjectClosure* _is_scavengable;
    bool               _found;

    explicit HasScavengableOops(BoolObjectClosure* is_scavengable) :
      _is_scavengable(is_scavengable),
      _found(false) {}

    virtual void do_oop(oop* p) {
      if (!_found && *p != nullptr && _is_scavengable->do_object_b(*p)) {
        _found = true;
      }
    }
    virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }
  } cl {_is_scavengable};

  nm->oops_do(&cl);
  return cl._found;
}

// Walk the list of methods which might contain oops to the java heap.
void ScavengableNMethods::nmethods_do_and_prune(NMethodToOopClosure* cl) {
  assert_locked_or_safepoint(CodeCache_lock);

  debug_only(mark_on_list_nmethods());

  nmethod* prev = nullptr;
  nmethod* cur = _head;
  while (cur != nullptr) {
    ScavengableNMethodsData data = gc_data(cur);
    debug_only(data.clear_marked());
    assert(data.on_list(), "else shouldn't be on this list");

    if (cl != nullptr) {
      cl->do_nmethod(cur);
    }

    nmethod* const next = data.next();

    if (!has_scavengable_oops(cur)) {
      unlist_nmethod(cur, prev);
    } else {
      prev = cur;
    }

    cur = next;
  }

  // Check for stray marks.
  debug_only(verify_nmethods());
}

void ScavengableNMethods::prune_nmethods_not_into_young() {
  nmethods_do_and_prune(nullptr /* No closure */);
}

void ScavengableNMethods::prune_unlinked_nmethods() {
  assert_locked_or_safepoint(CodeCache_lock);

  debug_only(mark_on_list_nmethods());

  nmethod* prev = nullptr;
  nmethod* cur = _head;
  while (cur != nullptr) {
    ScavengableNMethodsData data = gc_data(cur);
    debug_only(data.clear_marked());
    assert(data.on_list(), "else shouldn't be on this list");

    nmethod* const next = data.next();

    if (cur->is_unlinked()) {
      unlist_nmethod(cur, prev);
    } else {
      prev = cur;
    }

    cur = next;
  }

  // Check for stray marks.
  debug_only(verify_nmethods());
}

// Walk the list of methods which might contain oops to the java heap.
void ScavengableNMethods::nmethods_do(NMethodToOopClosure* cl) {
  nmethods_do_and_prune(cl);
}

void ScavengableNMethods::unlist_nmethod(nmethod* nm, nmethod* prev) {
  assert_locked_or_safepoint(CodeCache_lock);

  assert((prev == nullptr && _head == nm) ||
         (prev != nullptr && gc_data(prev).next() == nm), "precondition");

  ScavengableNMethodsData data = gc_data(nm);

  if (prev == nullptr) {
    _head = data.next();
  } else {
    gc_data(prev).set_next(data.next());
  }
  data.set_next(nullptr);
  data.clear_on_list();
}

#ifndef PRODUCT
// Temporarily mark nmethods that are claimed to be on the scavenge list.
void ScavengableNMethods::mark_on_list_nmethods() {
  NMethodIterator iter(NMethodIterator::all);
  while(iter.next()) {
    nmethod* nm = iter.method();
    ScavengableNMethodsData data = gc_data(nm);
    assert(data.not_marked(), "clean state");
    if (data.on_list()) {
      data.set_marked();
    }
  }
}

// Make sure that the effects of mark_on_list_nmethods is gone.
void ScavengableNMethods::verify_nmethods() {
  NMethodIterator iter(NMethodIterator::all);
  while(iter.next()) {
    nmethod* nm = iter.method();

    // Can not verify already unlinked nmethods as they are partially invalid already.
    if (!nm->is_unlinked()) {
      verify_nmethod(nm);
    }
  }
}

#endif //PRODUCT
