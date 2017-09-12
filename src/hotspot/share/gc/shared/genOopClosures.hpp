/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_GENOOPCLOSURES_HPP
#define SHARE_VM_GC_SHARED_GENOOPCLOSURES_HPP

#include "memory/iterator.hpp"
#include "oops/oop.hpp"

class Generation;
class HeapWord;
class CardTableRS;
class CardTableModRefBS;
class DefNewGeneration;
class KlassRemSet;

// Closure for iterating roots from a particular generation
// Note: all classes deriving from this MUST call this do_barrier
// method at the end of their own do_oop method!
// Note: no do_oop defined, this is an abstract class.

class OopsInGenClosure : public ExtendedOopClosure {
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
  OopsInGenClosure() : ExtendedOopClosure(NULL),
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

// Super class for scan closures. It contains code to dirty scanned Klasses.
class OopsInKlassOrGenClosure: public OopsInGenClosure {
  Klass* _scanned_klass;
 public:
  OopsInKlassOrGenClosure(Generation* g) : OopsInGenClosure(g), _scanned_klass(NULL) {}
  void set_scanned_klass(Klass* k) {
    assert(k == NULL || _scanned_klass == NULL, "Must be");
    _scanned_klass = k;
  }
  bool is_scanning_a_klass() { return _scanned_klass != NULL; }
  void do_klass_barrier();
};

// Closure for scanning DefNewGeneration.
//
// This closure will perform barrier store calls for ALL
// pointers in scanned oops.
class ScanClosure: public OopsInKlassOrGenClosure {
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
};

// Closure for scanning DefNewGeneration.
//
// This closure only performs barrier store calls on
// pointers into the DefNewGeneration. This is less
// precise, but faster, than a ScanClosure
class FastScanClosure: public OopsInKlassOrGenClosure {
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
};

class KlassScanClosure: public KlassClosure {
  OopsInKlassOrGenClosure* _scavenge_closure;
  // true if the the modified oops state should be saved.
  bool                     _accumulate_modified_oops;
 public:
  KlassScanClosure(OopsInKlassOrGenClosure* scavenge_closure,
                   KlassRemSet* klass_rem_set_policy);
  void do_klass(Klass* k);
};

class FilteringClosure: public ExtendedOopClosure {
 private:
  HeapWord*   _boundary;
  ExtendedOopClosure* _cl;
 protected:
  template <class T> inline void do_oop_work(T* p);
 public:
  FilteringClosure(HeapWord* boundary, ExtendedOopClosure* cl) :
    ExtendedOopClosure(cl->ref_processor()), _boundary(boundary),
    _cl(cl) {}
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  inline void do_oop_nv(oop* p);
  inline void do_oop_nv(narrowOop* p);
  virtual bool do_metadata()          { return do_metadata_nv(); }
  inline bool do_metadata_nv()        { assert(!_cl->do_metadata(), "assumption broken, must change to 'return _cl->do_metadata()'"); return false; }
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

#endif // SHARE_VM_GC_SHARED_GENOOPCLOSURES_HPP
