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

#include "precompiled.hpp"

#include "code/codeCache.hpp"
#include "code/nmethod.hpp"
#include "gc/g1/g1CodeRootSet.hpp"
#include "gc/g1/heapRegion.hpp"
#include "oops/oop.inline.hpp"

void G1CodeRootSet::add(nmethod* nm) {
  assert(_is_iterating == false, "should not mutate while iterating the table");
  bool added = false;
  if (_table == nullptr) {
    _table = new (mtGC) Table(SmallSize, LargeSize);
  }
  added = _table->put(nm, nm);
  if (added && _table->table_size() == SmallSize && length() == Threshold) {
    _table->resize(LargeSize);
  }
}

G1CodeRootSet::~G1CodeRootSet() {
  delete _table;
}

bool G1CodeRootSet::remove(nmethod* method) {
  assert(_is_iterating == false, "should not mutate while iterating the table");
  bool removed = false;
  if (_table != nullptr) {
    removed = _table->remove(method);
  }
  if (removed) {
    if (length() == 0) {
      clear();
    }
  }
  return removed;
}

bool G1CodeRootSet::contains(nmethod* method) {
  if (_table != nullptr) {
    return _table->contains(method);
  }
  return false;
}

void G1CodeRootSet::clear() {
  assert(_is_iterating == false, "should not mutate while iterating the table");
  delete _table;
  _table = nullptr;
}

size_t G1CodeRootSet::mem_size() {
  return (_table == nullptr)
    ? sizeof(*this)
    : sizeof(*this) + _table->mem_size();
}

void G1CodeRootSet::nmethods_do(CodeBlobClosure* blk) const {
  DEBUG_ONLY(_is_iterating = true;)
  if (_table != nullptr) {
    _table->iterate_all([&](nmethod* nm, nmethod* _) {
      blk->do_code_blob(nm);
    });
  }
  DEBUG_ONLY(_is_iterating = false;)
}

class CleanCallback : public StackObj {
  NONCOPYABLE(CleanCallback); // can not copy, _blobs will point to old copy
  class PointsIntoHRDetectionClosure : public OopClosure {
    HeapRegion* _hr;
   public:
    bool _points_into;
    PointsIntoHRDetectionClosure(HeapRegion* hr) : _hr(hr), _points_into(false) {}

    void do_oop(narrowOop* o) {
      do_oop_work(o);
    }

    void do_oop(oop* o) {
      do_oop_work(o);
    }

    template <typename T>
    void do_oop_work(T* p) {
      if (_hr->is_in(RawAccess<>::oop_load(p))) {
        _points_into = true;
      }
    }
  };

  PointsIntoHRDetectionClosure _detector;
  CodeBlobToOopClosure _blobs;

 public:
  CleanCallback(HeapRegion* hr) : _detector(hr), _blobs(&_detector, !CodeBlobToOopClosure::FixRelocations) {}

  bool do_entry(nmethod* nm, nmethod* _) {
    _detector._points_into = false;
    _blobs.do_code_blob(nm);
    return !_detector._points_into;
  }
};

void G1CodeRootSet::clean(HeapRegion* owner) {
  assert(_is_iterating == false, "should not mutate while iterating the table");
  CleanCallback should_clean(owner);
  if (_table != nullptr) {
    _table->unlink(&should_clean);
  }
  if (length() == 0) {
    clear();
  }
}
