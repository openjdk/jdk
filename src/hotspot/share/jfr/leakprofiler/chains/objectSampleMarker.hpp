/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_LEAKPROFILER_CHAINS_OBJECTSAMPLEMARKER_HPP
#define SHARE_VM_JFR_LEAKPROFILER_CHAINS_OBJECTSAMPLEMARKER_HPP

#include "memory/allocation.hpp"
#include "oops/markOop.hpp"
#include "utilities/growableArray.hpp"
//
// This class will save the original mark oop of a object sample object.
// It will then install an "identifier" mark oop to be used for
// identification purposes in the search for reference chains.
// The destructor will restore each modified oop with its original mark oop.
//
class ObjectSampleMarker : public StackObj {
 private:
  class ObjectSampleMarkOop : public ResourceObj {
    friend class ObjectSampleMarker;
   private:
    oop _obj;
    markOop _mark_oop;
    ObjectSampleMarkOop(const oop obj,
                        const markOop mark_oop) : _obj(obj),
                                                  _mark_oop(mark_oop) {}
   public:
    ObjectSampleMarkOop() : _obj(NULL), _mark_oop(NULL) {}
  };

  GrowableArray<ObjectSampleMarkOop>* _store;

 public:
  ObjectSampleMarker() :
       _store(new GrowableArray<ObjectSampleMarkOop>(16)) {}
  ~ObjectSampleMarker() {
    assert(_store != NULL, "invariant");
    // restore the saved, original, markOop for sample objects
    while (_store->is_nonempty()) {
      ObjectSampleMarkOop sample_oop = _store->pop();
      sample_oop._obj->set_mark(sample_oop._mark_oop);
      assert(sample_oop._obj->mark() == sample_oop._mark_oop, "invariant");
    }
  }

  void mark(oop obj) {
    assert(obj != NULL, "invariant");
    // save the original markOop
    _store->push(ObjectSampleMarkOop(obj, obj->mark()));
    // now we will "poison" the mark word of the sample object
    // to the intermediate monitor INFLATING state.
    // This is an "impossible" state during a safepoint,
    // hence we will use it to quickly identify sample objects
    // during the reachability search from gc roots.
    assert(NULL == markOopDesc::INFLATING(), "invariant");
    obj->set_mark(markOopDesc::INFLATING());
    assert(NULL == obj->mark(), "invariant");
  }
};

#endif // SHARE_VM_JFR_LEAKPROFILER_CHAINS_OBJECTSAMPLEMARKER_HPP
