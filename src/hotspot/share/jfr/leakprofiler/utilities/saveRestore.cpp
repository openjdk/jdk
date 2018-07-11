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

#include "precompiled.hpp"
#include "classfile/classLoaderData.hpp"
#include "jfr/leakprofiler/utilities/saveRestore.hpp"
#include "oops/oop.inline.hpp"

MarkOopContext::MarkOopContext() : _obj(NULL), _mark_oop(NULL) {}

MarkOopContext::MarkOopContext(const oop obj) : _obj(obj), _mark_oop(obj->mark()) {
  assert(_obj->mark() == _mark_oop, "invariant");
  // now we will "poison" the mark word of the object
  // to the intermediate monitor INFLATING state.
  // This is an "impossible" state during a safepoint,
  // hence we will use it to quickly identify objects
  // during the reachability search from gc roots.
  assert(NULL == markOopDesc::INFLATING(), "invariant");
  _obj->set_mark(markOopDesc::INFLATING());
  assert(NULL == obj->mark(), "invariant");
}

MarkOopContext::~MarkOopContext() {
  if (_obj != NULL) {
    _obj->set_mark(_mark_oop);
    assert(_obj->mark() == _mark_oop, "invariant");
  }
}

MarkOopContext::MarkOopContext(const MarkOopContext& rhs) : _obj(NULL), _mark_oop(NULL) {
  swap(const_cast<MarkOopContext&>(rhs));
}

void MarkOopContext::operator=(MarkOopContext rhs) {
  swap(rhs);
}

void MarkOopContext::swap(MarkOopContext& rhs) {
  oop temp_obj = rhs._obj;
  markOop temp_mark_oop = rhs._mark_oop;
  rhs._obj = _obj;
  rhs._mark_oop = _mark_oop;
  _obj = temp_obj;
  _mark_oop = temp_mark_oop;
}

CLDClaimContext::CLDClaimContext() : _cld(NULL) {}

CLDClaimContext::CLDClaimContext(ClassLoaderData* cld) : _cld(cld) {
  assert(_cld->claimed(), "invariant");
  _cld->clear_claimed();
}

CLDClaimContext::~CLDClaimContext() {
  if (_cld != NULL) {
    _cld->claim();
    assert(_cld->claimed(), "invariant");
  }
}

CLDClaimContext::CLDClaimContext(const CLDClaimContext& rhs) : _cld(NULL) {
  swap(const_cast<CLDClaimContext&>(rhs));
}

void CLDClaimContext::operator=(CLDClaimContext rhs) {
  swap(rhs);
}

void CLDClaimContext::swap(CLDClaimContext& rhs) {
  ClassLoaderData* temp_cld = rhs._cld;
  rhs._cld = _cld;
  _cld = temp_cld;
}

CLDClaimStateClosure::CLDClaimStateClosure() : CLDClosure(), _state() {}

void CLDClaimStateClosure::do_cld(ClassLoaderData* cld) {
  assert(cld != NULL, "invariant");
  if (cld->claimed()) {
    _state.save(cld);
  }
}

SaveRestoreCLDClaimBits::SaveRestoreCLDClaimBits() : _claim_state_closure() {
  ClassLoaderDataGraph::cld_do(&_claim_state_closure);
}

SaveRestoreCLDClaimBits::~SaveRestoreCLDClaimBits() {
  ClassLoaderDataGraph::clear_claimed_marks();
}
