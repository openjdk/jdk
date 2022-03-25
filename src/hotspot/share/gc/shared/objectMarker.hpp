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

#ifndef SHARE_GC_SHARED_OBJECTMARKER_HPP
#define SHARE_GC_SHARED_OBJECTMARKER_HPP

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"

// ObjectMarker is used to support the marking objects when walking the
// heap.
class ObjectMarker : public CHeapObj<mtGC>{
public:
  virtual ~ObjectMarker() {};
  virtual void mark(oop o) = 0;
  virtual bool is_marked(oop o) = 0;

  virtual void set_needs_reset(bool needs_reset) {};
};

// Stack allocated class to help ensure that ObjectMarker is used
// correctly. Constructor initializes ObjectMarker, destructor calls
// ObjectMarker's done() function to restore object headers.
class ObjectMarkerController : public StackObj {
private:
  static ObjectMarker* _marker;
public:
  ObjectMarkerController();
  ~ObjectMarkerController();

  static void mark(oop o);
  static bool is_marked(oop o);

  static void set_needs_reset(bool needs_reset);
};

// ObjectMarker is used to support the marking objects when walking the
// heap.
//
// This implementation uses the existing mark bits in an object for
// marking. Objects that are marked must later have their headers restored.
// As most objects are unlocked and don't have their identity hash computed
// we don't have to save their headers. Instead we save the headers that
// are "interesting". Later when the headers are restored this implementation
// restores all headers to their initial value and then restores the few
// objects that had interesting headers.
//
// Future work: This implementation currently uses growable arrays to save
// the oop and header of interesting objects. As an optimization we could
// use the same technique as the GC and make use of the unused area
// between top() and end().
class HeaderObjectMarker : public ObjectMarker {
private:
  GrowableArray<oop>* _saved_oop_stack;
  GrowableArray<markWord>* _saved_mark_stack;
  bool _needs_reset;
public:
  HeaderObjectMarker();
  ~HeaderObjectMarker();
  void mark(oop o) override;
  bool is_marked(oop o) override;
  void set_needs_reset(bool needs_reset) override;
};

#endif // SHARE_GC_SHARED_OBJECTMARKER_HPP
