/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

class Generation;
class HeapWord;
class CardTableRS;
class CardTableModRefBS;
class DefNewGeneration;

template<class E> class GenericTaskQueue;
typedef GenericTaskQueue<oop> OopTaskQueue;
template<class E> class GenericTaskQueueSet;
typedef GenericTaskQueueSet<oop> OopTaskQueueSet;

// Closure for iterating roots from a particular generation
// Note: all classes deriving from this MUST call this do_barrier
// method at the end of their own do_oop method!
// Note: no do_oop defined, this is an abstract class.

class OopsInGenClosure : public OopClosure {
 private:
  Generation*  _orig_gen;     // generation originally set in ctor
  Generation*  _gen;          // generation being scanned

 protected:
  // Some subtypes need access.
  HeapWord*    _gen_boundary; // start of generation
  CardTableRS* _rs;           // remembered set

  // For assertions
  Generation* generation() { return _gen; }
  CardTableRS* rs() { return _rs; }

  // Derived classes that modify oops so that they might be old-to-young
  // pointers must call the method below.
  template <class T> void do_barrier(T* p);

  // Version for use by closures that may be called in parallel code.
  template <class T> void par_do_barrier(T* p);

 public:
  OopsInGenClosure() : OopClosure(NULL),
    _orig_gen(NULL), _gen(NULL), _gen_boundary(NULL), _rs(NULL) {};

  OopsInGenClosure(Generation* gen);
  void set_generation(Generation* gen);

  void reset_generation() { _gen = _orig_gen; }

  // Problem with static closures: must have _gen_boundary set at some point,
  // but cannot do this until after the heap is initialized.
  void set_orig_generation(Generation* gen) {
    _orig_gen = gen;
    set_generation(gen);
  }

  HeapWord* gen_boundary() { return _gen_boundary; }
};

// Closure for scanning DefNewGeneration.
//
// This closure will perform barrier store calls for ALL
// pointers in scanned oops.
class ScanClosure: public OopsInGenClosure {
 protected:
  DefNewGeneration* _g;
  HeapWord*         _boundary;
  bool              _gc_barrier;
  template <class T> inline void do_oop_work(T* p);
 public:
  ScanClosure(DefNewGeneration* g, bool gc_barrier);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p);
  inline void do_oop_nv(narrowOop* p);
  bool do_header() { return false; }
  Prefetch::style prefetch_style() {
    return Prefetch::do_write;
  }
};

// Closure for scanning DefNewGeneration.
//
// This closure only performs barrier store calls on
// pointers into the DefNewGeneration. This is less
// precise, but faster, than a ScanClosure
class FastScanClosure: public OopsInGenClosure {
 protected:
  DefNewGeneration* _g;
  HeapWord*         _boundary;
  bool              _gc_barrier;
  template <class T> inline void do_oop_work(T* p);
 public:
  FastScanClosure(DefNewGeneration* g, bool gc_barrier);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p);
  inline void do_oop_nv(narrowOop* p);
  bool do_header() { return false; }
  Prefetch::style prefetch_style() {
    return Prefetch::do_write;
  }
};

class FilteringClosure: public OopClosure {
 private:
  HeapWord*   _boundary;
  OopClosure* _cl;
 protected:
  template <class T> inline void do_oop_work(T* p) {
    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
      if ((HeapWord*)obj < _boundary) {
        _cl->do_oop(p);
      }
    }
  }
 public:
  FilteringClosure(HeapWord* boundary, OopClosure* cl) :
    OopClosure(cl->_ref_processor), _boundary(boundary),
    _cl(cl) {}
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p)       { FilteringClosure::do_oop_work(p); }
  inline void do_oop_nv(narrowOop* p) { FilteringClosure::do_oop_work(p); }
  bool do_header() { return false; }
};

// Closure for scanning DefNewGeneration's weak references.
// NOTE: very much like ScanClosure but not derived from
//  OopsInGenClosure -- weak references are processed all
//  at once, with no notion of which generation they were in.
class ScanWeakRefClosure: public OopClosure {
 protected:
  DefNewGeneration* _g;
  HeapWord*         _boundary;
  template <class T> inline void do_oop_work(T* p);
 public:
  ScanWeakRefClosure(DefNewGeneration* g);
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p);
  inline void do_oop_nv(narrowOop* p);
};

class VerifyOopClosure: public OopClosure {
 protected:
  template <class T> inline void do_oop_work(T* p) {
    oop obj = oopDesc::load_decode_heap_oop(p);
    guarantee(obj->is_oop_or_null(), "invalid oop");
  }
 public:
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  static VerifyOopClosure verify_oop;
};
