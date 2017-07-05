/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_ITERATOR_HPP
#define SHARE_VM_MEMORY_ITERATOR_HPP

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "runtime/prefetch.hpp"
#include "utilities/top.hpp"

// The following classes are C++ `closures` for iterating over objects, roots and spaces

class CodeBlob;
class nmethod;
class ReferenceProcessor;
class DataLayout;
class KlassClosure;
class ClassLoaderData;

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

// OopClosure is used for iterating through references to Java objects.

class OopClosure : public Closure {
 public:
  virtual void do_oop(oop* o) = 0;
  virtual void do_oop_v(oop* o) { do_oop(o); }
  virtual void do_oop(narrowOop* o) = 0;
  virtual void do_oop_v(narrowOop* o) { do_oop(o); }
};

// ExtendedOopClosure adds extra code to be run during oop iterations.
// This is needed by the GC and is extracted to a separate type to not
// pollute the OopClosure interface.
class ExtendedOopClosure : public OopClosure {
 public:
  ReferenceProcessor* _ref_processor;
  ExtendedOopClosure(ReferenceProcessor* rp) : _ref_processor(rp) { }
  ExtendedOopClosure() : OopClosure(), _ref_processor(NULL) { }

  // If the do_metadata functions return "true",
  // we invoke the following when running oop_iterate():
  //
  // 1) do_klass on the header klass pointer.
  // 2) do_klass on the klass pointer in the mirrors.
  // 3) do_class_loader_data on the class loader data in class loaders.
  //
  // The virtual (without suffix) and the non-virtual (with _nv suffix) need
  // to be updated together, or else the devirtualization will break.
  //
  // Providing default implementations of the _nv functions unfortunately
  // removes the compile-time safeness, but reduces the clutter for the
  // ExtendedOopClosures that don't need to walk the metadata. Currently,
  // only CMS needs these.

  virtual bool do_metadata() { return do_metadata_nv(); }
  bool do_metadata_v()       { return do_metadata(); }
  bool do_metadata_nv()      { return false; }

  virtual void do_klass(Klass* k)   { do_klass_nv(k); }
  void do_klass_v(Klass* k)         { do_klass(k); }
  void do_klass_nv(Klass* k)        { ShouldNotReachHere(); }

  virtual void do_class_loader_data(ClassLoaderData* cld) { ShouldNotReachHere(); }

  // Controls how prefetching is done for invocations of this closure.
  Prefetch::style prefetch_style() { // Note that this is non-virtual.
    return Prefetch::do_none;
  }

  // True iff this closure may be safely applied more than once to an oop
  // location without an intervening "major reset" (like the end of a GC).
  virtual bool idempotent() { return false; }
  virtual bool apply_to_weak_ref_discovered_field() { return false; }
};

// Wrapper closure only used to implement oop_iterate_no_header().
class NoHeaderExtendedOopClosure : public ExtendedOopClosure {
  OopClosure* _wrapped_closure;
 public:
  NoHeaderExtendedOopClosure(OopClosure* cl) : _wrapped_closure(cl) {}
  // Warning: this calls the virtual version do_oop in the the wrapped closure.
  void do_oop_nv(oop* p)       { _wrapped_closure->do_oop(p); }
  void do_oop_nv(narrowOop* p) { _wrapped_closure->do_oop(p); }

  void do_oop(oop* p)          { assert(false, "Only the _nv versions should be used");
                                 _wrapped_closure->do_oop(p); }
  void do_oop(narrowOop* p)    { assert(false, "Only the _nv versions should be used");
                                 _wrapped_closure->do_oop(p);}
};

class KlassClosure : public Closure {
 public:
  virtual void do_klass(Klass* k) = 0;
};

class KlassToOopClosure : public KlassClosure {
  OopClosure* _oop_closure;
 public:
  KlassToOopClosure(OopClosure* oop_closure) : _oop_closure(oop_closure) {}
  virtual void do_klass(Klass* k);
};

class CLDToOopClosure {
  OopClosure* _oop_closure;
  KlassToOopClosure _klass_closure;
  bool _must_claim_cld;

 public:
  CLDToOopClosure(OopClosure* oop_closure, bool must_claim_cld = true) :
      _oop_closure(oop_closure),
      _klass_closure(oop_closure),
      _must_claim_cld(must_claim_cld) {}

  void do_cld(ClassLoaderData* cld);
};

// ObjectClosure is used for iterating through an object space

class ObjectClosure : public Closure {
 public:
  // Called for each object.
  virtual void do_object(oop obj) = 0;
};


class BoolObjectClosure : public Closure {
 public:
  virtual bool do_object_b(oop obj) = 0;
};

// Applies an oop closure to all ref fields in objects iterated over in an
// object iteration.
class ObjectToOopClosure: public ObjectClosure {
  ExtendedOopClosure* _cl;
public:
  void do_object(oop obj);
  ObjectToOopClosure(ExtendedOopClosure* cl) : _cl(cl) {}
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

class SerializeClosure : public Closure {
public:
  // Return bool indicating whether closure implements read or write.
  virtual bool reading() const = 0;

  // Read/write the void pointer pointed to by p.
  virtual void do_ptr(void** p) = 0;

  // Read/write the region specified.
  virtual void do_region(u_char* start, size_t size) = 0;

  // Check/write the tag.  If reading, then compare the tag against
  // the passed in value and fail is they don't match.  This allows
  // for verification that sections of the serialized data are of the
  // correct length.
  virtual void do_tag(int tag) = 0;
};

class SymbolClosure : public StackObj {
 public:
  virtual void do_symbol(Symbol**) = 0;

  // Clear LSB in symbol address; it can be set by CPSlot.
  static Symbol* load_symbol(Symbol** p) {
    return (Symbol*)(intptr_t(*p) & ~1);
  }

  // Store symbol, adjusting new pointer if the original pointer was adjusted
  // (symbol references in constant pool slots have their LSB set to 1).
  static void store_symbol(Symbol** p, Symbol* sym) {
    *p = (Symbol*)(intptr_t(sym) | (intptr_t(*p) & 1));
  }
};

#endif // SHARE_VM_MEMORY_ITERATOR_HPP
