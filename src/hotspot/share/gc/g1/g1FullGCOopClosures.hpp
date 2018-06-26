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

#ifndef SHARE_GC_G1_G1FULLGCOOPCLOSURES_HPP
#define SHARE_GC_G1_G1FULLGCOOPCLOSURES_HPP

#include "memory/iterator.hpp"
#include "memory/universe.hpp"

class G1CollectedHeap;
class G1FullCollector;
class G1CMBitMap;
class G1FullGCMarker;

// Below are closures used by the G1 Full GC.
class G1IsAliveClosure : public BoolObjectClosure {
  G1CMBitMap* _bitmap;

public:
  G1IsAliveClosure(G1CMBitMap* bitmap) : _bitmap(bitmap) { }

  virtual bool do_object_b(oop p);
};

class G1FullKeepAliveClosure: public OopClosure {
  G1FullGCMarker* _marker;
  template <class T>
  inline void do_oop_work(T* p);

public:
  G1FullKeepAliveClosure(G1FullGCMarker* pm) : _marker(pm) { }

  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
};

class G1MarkAndPushClosure : public OopIterateClosure {
  G1FullGCMarker* _marker;
  uint _worker_id;

public:
  G1MarkAndPushClosure(uint worker, G1FullGCMarker* marker, ReferenceDiscoverer* ref) :
    _marker(marker),
    _worker_id(worker),
    OopIterateClosure(ref) { }

  template <class T> inline void do_oop_work(T* p);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);

  virtual bool do_metadata();
  virtual void do_klass(Klass* k);
  virtual void do_cld(ClassLoaderData* cld);
};

class G1AdjustClosure : public BasicOopIterateClosure {
  template <class T> static inline void adjust_pointer(T* p);
public:
  template <class T> void do_oop_work(T* p) { adjust_pointer(p); }
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);

  virtual ReferenceIterationMode reference_iteration_mode() { return DO_FIELDS; }
};

class G1VerifyOopClosure: public BasicOopIterateClosure {
private:
  G1CollectedHeap* _g1h;
  bool             _failures;
  oop              _containing_obj;
  VerifyOption     _verify_option;

public:
  int _cc;
  G1VerifyOopClosure(VerifyOption option);

  void set_containing_obj(oop obj) {
    _containing_obj = obj;
  }

  bool failures() { return _failures; }
  void print_object(outputStream* out, oop obj);

  template <class T> void do_oop_work(T* p);

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

class G1FollowStackClosure: public VoidClosure {
  G1FullGCMarker* _marker;

public:
  G1FollowStackClosure(G1FullGCMarker* marker) : _marker(marker) {}
  virtual void do_void();
};

#endif // SHARE_GC_G1_G1FULLGCOOPCLOSURES_HPP
