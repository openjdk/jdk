/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/objectMarker.hpp"
#include "memory/universe.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/growableArray.hpp"

ObjectMarker* ObjectMarkerController::_marker = NULL;

ObjectMarkerController::ObjectMarkerController() {
  // prepare heap for iteration
  CollectedHeap* heap = Universe::heap();
  heap->ensure_parsability(false);  // no need to retire TLABs
  _marker = heap->init_object_marker();
}

ObjectMarkerController::~ObjectMarkerController() {
  delete _marker;
  _marker = NULL;
}

void ObjectMarkerController::mark(oop o) {
  assert(_marker != NULL, "need object marker");
  _marker->mark(o);
}

bool ObjectMarkerController::is_marked(oop o) {
  assert(_marker != NULL, "need object marker");
  return _marker->is_marked(o);
}

void ObjectMarkerController::set_needs_reset(bool needs_reset) {
  assert(_marker != NULL, "need object marker");
  return _marker->set_needs_reset(needs_reset);
}

class RestoreMarksClosure : public ObjectClosure {
public:
  void do_object(oop o) {
    if (o != NULL) {
      markWord mark = o->mark();
      if (mark.is_marked()) {
        o->init_mark();
      }
    }
  }
};

HeaderObjectMarker::HeaderObjectMarker() :
 _saved_oop_stack(new (ResourceObj::C_HEAP, mtGC) GrowableArray<oop>(4000, mtGC)),
 _saved_mark_stack(new (ResourceObj::C_HEAP, mtGC) GrowableArray<markWord>(4000, mtGC)),
 _needs_reset(false) {
}

// object marking done, so restore headers
HeaderObjectMarker::~HeaderObjectMarker() {
  // iterate over all objects and restore the mark bits to
  // their initial value
  RestoreMarksClosure blk;
  if (_needs_reset) {
    Universe::heap()->object_iterate(&blk);
  }

  // Now restore the interesting headers
  for (int i = 0; i < _saved_oop_stack->length(); i++) {
    oop o = _saved_oop_stack->at(i);
    markWord mark = _saved_mark_stack->at(i);
    o->set_mark(mark);
  }

  // free the stacks
  delete _saved_oop_stack;
  delete _saved_mark_stack;
}

void HeaderObjectMarker::set_needs_reset(bool needs_reset) {
  _needs_reset = needs_reset;
}

// mark an object
void HeaderObjectMarker::mark(oop o) {
  assert(Universe::heap()->is_in(o), "sanity check");
  assert(!o->mark().is_marked(), "should only mark an object once");

  // object's mark word
  markWord mark = o->mark();

  if (o->mark_must_be_preserved(mark)) {
    _saved_mark_stack->push(mark);
    _saved_oop_stack->push(o);
  }

  // mark the object
  o->set_mark(markWord::prototype().set_marked());
}

// return true if object is marked
bool HeaderObjectMarker::is_marked(oop o) {
  return o->mark().is_marked();
}
