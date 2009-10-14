/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

// The following classes are C++ `closures` for iterating over objects, roots and spaces

class CodeBlob;
class nmethod;
class ReferenceProcessor;
class DataLayout;

// Closure provides abortability.

class Closure : public StackObj {
 protected:
  bool _abort;
  void set_abort() { _abort = true; }
 public:
  Closure() : _abort(false) {}
  // A subtype can use this mechanism to indicate to some iterator mapping
  // functions that the iteration should cease.
  bool abort() { return _abort; }
  void clear_abort() { _abort = false; }
};

// OopClosure is used for iterating through roots (oop*)

class OopClosure : public Closure {
 public:
  ReferenceProcessor* _ref_processor;
  OopClosure(ReferenceProcessor* rp) : _ref_processor(rp) { }
  OopClosure() : _ref_processor(NULL) { }
  virtual void do_oop(oop* o) = 0;
  virtual void do_oop_v(oop* o) { do_oop(o); }
  virtual void do_oop(narrowOop* o) = 0;
  virtual void do_oop_v(narrowOop* o) { do_oop(o); }

  // In support of post-processing of weak links of KlassKlass objects;
  // see KlassKlass::oop_oop_iterate().

  virtual const bool should_remember_klasses() const {
    assert(!must_remember_klasses(), "Should have overriden this method.");
    return false;
  }

  virtual void remember_klass(Klass* k) { /* do nothing */ }

  // In support of post-processing of weak references in
  // ProfileData (MethodDataOop) objects; see, for example,
  // VirtualCallData::oop_iterate().
  virtual const bool should_remember_mdo() const { return false; }
  virtual void remember_mdo(DataLayout* v) { /* do nothing */ }

  // The methods below control how object iterations invoking this closure
  // should be performed:

  // If "true", invoke on header klass field.
  bool do_header() { return true; } // Note that this is non-virtual.
  // Controls how prefetching is done for invocations of this closure.
  Prefetch::style prefetch_style() { // Note that this is non-virtual.
    return Prefetch::do_none;
  }

  // True iff this closure may be safely applied more than once to an oop
  // location without an intervening "major reset" (like the end of a GC).
  virtual bool idempotent() { return false; }
  virtual bool apply_to_weak_ref_discovered_field() { return false; }

#ifdef ASSERT
  static bool _must_remember_klasses;
  static bool must_remember_klasses();
  static void set_must_remember_klasses(bool v);
#endif
};

// ObjectClosure is used for iterating through an object space

class ObjectClosure : public Closure {
 public:
  // Called for each object.
  virtual void do_object(oop obj) = 0;
};


class BoolObjectClosure : public ObjectClosure {
 public:
  virtual bool do_object_b(oop obj) = 0;
};

// Applies an oop closure to all ref fields in objects iterated over in an
// object iteration.
class ObjectToOopClosure: public ObjectClosure {
  OopClosure* _cl;
public:
  void do_object(oop obj);
  ObjectToOopClosure(OopClosure* cl) : _cl(cl) {}
};

// A version of ObjectClosure with "memory" (see _previous_address below)
class UpwardsObjectClosure: public BoolObjectClosure {
  HeapWord* _previous_address;
 public:
  UpwardsObjectClosure() : _previous_address(NULL) { }
  void set_previous(HeapWord* addr) { _previous_address = addr; }
  HeapWord* previous()              { return _previous_address; }
  // A return value of "true" can be used by the caller to decide
  // if this object's end should *NOT* be recorded in
  // _previous_address above.
  virtual bool do_object_bm(oop obj, MemRegion mr) = 0;
};

// A version of ObjectClosure that is expected to be robust
// in the face of possibly uninitialized objects.
class ObjectClosureCareful : public ObjectClosure {
 public:
  virtual size_t do_object_careful_m(oop p, MemRegion mr) = 0;
  virtual size_t do_object_careful(oop p) = 0;
};

// The following are used in CompactibleFreeListSpace and
// ConcurrentMarkSweepGeneration.

// Blk closure (abstract class)
class BlkClosure : public StackObj {
 public:
  virtual size_t do_blk(HeapWord* addr) = 0;
};

// A version of BlkClosure that is expected to be robust
// in the face of possibly uninitialized objects.
class BlkClosureCareful : public BlkClosure {
 public:
  size_t do_blk(HeapWord* addr) {
    guarantee(false, "call do_blk_careful instead");
    return 0;
  }
  virtual size_t do_blk_careful(HeapWord* addr) = 0;
};

// SpaceClosure is used for iterating over spaces

class Space;
class CompactibleSpace;

class SpaceClosure : public StackObj {
 public:
  // Called for each space
  virtual void do_space(Space* s) = 0;
};

class CompactibleSpaceClosure : public StackObj {
 public:
  // Called for each compactible space
  virtual void do_space(CompactibleSpace* s) = 0;
};


// CodeBlobClosure is used for iterating through code blobs
// in the code cache or on thread stacks

class CodeBlobClosure : public Closure {
 public:
  // Called for each code blob.
  virtual void do_code_blob(CodeBlob* cb) = 0;
};


class MarkingCodeBlobClosure : public CodeBlobClosure {
 public:
  // Called for each code blob, but at most once per unique blob.
  virtual void do_newly_marked_nmethod(nmethod* nm) = 0;

  virtual void do_code_blob(CodeBlob* cb);
    // = { if (!nmethod(cb)->test_set_oops_do_mark())  do_newly_marked_nmethod(cb); }

  class MarkScope : public StackObj {
  protected:
    bool _active;
  public:
    MarkScope(bool activate = true);
      // = { if (active) nmethod::oops_do_marking_prologue(); }
    ~MarkScope();
      // = { if (active) nmethod::oops_do_marking_epilogue(); }
  };
};


// Applies an oop closure to all ref fields in code blobs
// iterated over in an object iteration.
class CodeBlobToOopClosure: public MarkingCodeBlobClosure {
  OopClosure* _cl;
  bool _do_marking;
public:
  virtual void do_newly_marked_nmethod(nmethod* cb);
    // = { cb->oops_do(_cl); }
  virtual void do_code_blob(CodeBlob* cb);
    // = { if (_do_marking)  super::do_code_blob(cb); else cb->oops_do(_cl); }
  CodeBlobToOopClosure(OopClosure* cl, bool do_marking)
    : _cl(cl), _do_marking(do_marking) {}
};



// MonitorClosure is used for iterating over monitors in the monitors cache

class ObjectMonitor;

class MonitorClosure : public StackObj {
 public:
  // called for each monitor in cache
  virtual void do_monitor(ObjectMonitor* m) = 0;
};

// A closure that is applied without any arguments.
class VoidClosure : public StackObj {
 public:
  // I would have liked to declare this a pure virtual, but that breaks
  // in mysterious ways, for unknown reasons.
  virtual void do_void();
};


// YieldClosure is intended for use by iteration loops
// to incrementalize their work, allowing interleaving
// of an interruptable task so as to allow other
// threads to run (which may not otherwise be able to access
// exclusive resources, for instance). Additionally, the
// closure also allows for aborting an ongoing iteration
// by means of checking the return value from the polling
// call.
class YieldClosure : public StackObj {
  public:
   virtual bool should_return() = 0;
};

// Abstract closure for serializing data (read or write).

class SerializeOopClosure : public OopClosure {
public:
  // Return bool indicating whether closure implements read or write.
  virtual bool reading() const = 0;

  // Read/write the int pointed to by i.
  virtual void do_int(int* i) = 0;

  // Read/write the size_t pointed to by i.
  virtual void do_size_t(size_t* i) = 0;

  // Read/write the void pointer pointed to by p.
  virtual void do_ptr(void** p) = 0;

  // Read/write the HeapWord pointer pointed to be p.
  virtual void do_ptr(HeapWord** p) = 0;

  // Read/write the region specified.
  virtual void do_region(u_char* start, size_t size) = 0;

  // Check/write the tag.  If reading, then compare the tag against
  // the passed in value and fail is they don't match.  This allows
  // for verification that sections of the serialized data are of the
  // correct length.
  virtual void do_tag(int tag) = 0;
};

#ifdef ASSERT
// This class is used to flag phases of a collection that
// can unload classes and which should override the
// should_remember_klasses() and remember_klass() of OopClosure.
// The _must_remember_klasses is set in the contructor and restored
// in the destructor.  _must_remember_klasses is checked in assertions
// in the OopClosure implementations of should_remember_klasses() and
// remember_klass() and the expectation is that the OopClosure
// implementation should not be in use if _must_remember_klasses is set.
// Instances of RememberKlassesChecker can be place in
// marking phases of collections which can do class unloading.
// RememberKlassesChecker can be passed "false" to turn off checking.
// It is used by CMS when CMS yields to a different collector.
class RememberKlassesChecker: StackObj {
 bool _state;
 bool _skip;
 public:
  RememberKlassesChecker(bool checking_on) : _state(false), _skip(false) {
    _skip = !(ClassUnloading && !UseConcMarkSweepGC ||
              CMSClassUnloadingEnabled && UseConcMarkSweepGC);
    if (_skip) {
      return;
    }
    _state = OopClosure::must_remember_klasses();
    OopClosure::set_must_remember_klasses(checking_on);
  }
  ~RememberKlassesChecker() {
    if (_skip) {
      return;
    }
    OopClosure::set_must_remember_klasses(_state);
  }
};
#endif  // ASSERT
