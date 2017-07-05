/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_SHARED_MARKSWEEP_HPP
#define SHARE_VM_GC_IMPLEMENTATION_SHARED_MARKSWEEP_HPP

#include "gc_interface/collectedHeap.hpp"
#include "memory/universe.hpp"
#include "oops/markOop.hpp"
#include "oops/oop.hpp"
#include "runtime/timer.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/stack.hpp"
#include "utilities/taskqueue.hpp"

class ReferenceProcessor;
class DataLayout;

// MarkSweep takes care of global mark-compact garbage collection for a
// GenCollectedHeap using a four-phase pointer forwarding algorithm.  All
// generations are assumed to support marking; those that can also support
// compaction.
//
// Class unloading will only occur when a full gc is invoked.

// If VALIDATE_MARK_SWEEP is defined, the -XX:+ValidateMarkSweep flag will
// be operational, and will provide slow but comprehensive self-checks within
// the GC.  This is not enabled by default in product or release builds,
// since the extra call to track_adjusted_pointer() in _adjust_pointer()
// would be too much overhead, and would disturb performance measurement.
// However, debug builds are sometimes way too slow to run GC tests!
#ifdef ASSERT
#define VALIDATE_MARK_SWEEP 1
#endif
#ifdef VALIDATE_MARK_SWEEP
#define VALIDATE_MARK_SWEEP_ONLY(code) code
#else
#define VALIDATE_MARK_SWEEP_ONLY(code)
#endif

// declared at end
class PreservedMark;

class MarkSweep : AllStatic {
  //
  // Inline closure decls
  //
  class FollowRootClosure: public OopsInGenClosure {
   public:
    virtual void do_oop(oop* p);
    virtual void do_oop(narrowOop* p);
  };

  class MarkAndPushClosure: public OopClosure {
   public:
    virtual void do_oop(oop* p);
    virtual void do_oop(narrowOop* p);
    virtual const bool should_remember_mdo() const { return true; }
    virtual void remember_mdo(DataLayout* p) { MarkSweep::revisit_mdo(p); }
  };

  class FollowStackClosure: public VoidClosure {
   public:
    virtual void do_void();
  };

  class AdjustPointerClosure: public OopsInGenClosure {
   private:
    bool _is_root;
   public:
    AdjustPointerClosure(bool is_root) : _is_root(is_root) {}
    virtual void do_oop(oop* p);
    virtual void do_oop(narrowOop* p);
  };

  // Used for java/lang/ref handling
  class IsAliveClosure: public BoolObjectClosure {
   public:
    virtual void do_object(oop p);
    virtual bool do_object_b(oop p);
  };

  class KeepAliveClosure: public OopClosure {
   protected:
    template <class T> void do_oop_work(T* p);
   public:
    virtual void do_oop(oop* p);
    virtual void do_oop(narrowOop* p);
  };

  //
  // Friend decls
  //
  friend class AdjustPointerClosure;
  friend class KeepAliveClosure;
  friend class VM_MarkSweep;
  friend void marksweep_init();

  //
  // Vars
  //
 protected:
  // Traversal stacks used during phase1
  static Stack<oop>                      _marking_stack;
  static Stack<ObjArrayTask>             _objarray_stack;
  // Stack for live klasses to revisit at end of marking phase
  static Stack<Klass*>                   _revisit_klass_stack;
  // Set (stack) of MDO's to revisit at end of marking phase
  static Stack<DataLayout*>              _revisit_mdo_stack;

  // Space for storing/restoring mark word
  static Stack<markOop>                  _preserved_mark_stack;
  static Stack<oop>                      _preserved_oop_stack;
  static size_t                          _preserved_count;
  static size_t                          _preserved_count_max;
  static PreservedMark*                  _preserved_marks;

  // Reference processing (used in ...follow_contents)
  static ReferenceProcessor*             _ref_processor;

#ifdef VALIDATE_MARK_SWEEP
  static GrowableArray<void*>*           _root_refs_stack;
  static GrowableArray<oop> *            _live_oops;
  static GrowableArray<oop> *            _live_oops_moved_to;
  static GrowableArray<size_t>*          _live_oops_size;
  static size_t                          _live_oops_index;
  static size_t                          _live_oops_index_at_perm;
  static GrowableArray<void*>*           _other_refs_stack;
  static GrowableArray<void*>*           _adjusted_pointers;
  static bool                            _pointer_tracking;
  static bool                            _root_tracking;

  // The following arrays are saved since the time of the last GC and
  // assist in tracking down problems where someone has done an errant
  // store into the heap, usually to an oop that wasn't properly
  // handleized across a GC. If we crash or otherwise fail before the
  // next GC, we can query these arrays to find out the object we had
  // intended to do the store to (assuming it is still alive) and the
  // offset within that object. Covered under RecordMarkSweepCompaction.
  static GrowableArray<HeapWord*> *      _cur_gc_live_oops;
  static GrowableArray<HeapWord*> *      _cur_gc_live_oops_moved_to;
  static GrowableArray<size_t>*          _cur_gc_live_oops_size;
  static GrowableArray<HeapWord*> *      _last_gc_live_oops;
  static GrowableArray<HeapWord*> *      _last_gc_live_oops_moved_to;
  static GrowableArray<size_t>*          _last_gc_live_oops_size;
#endif

  // Non public closures
  static IsAliveClosure   is_alive;
  static KeepAliveClosure keep_alive;

  // Class unloading. Update subklass/sibling/implementor links at end of marking phase.
  static void follow_weak_klass_links();

  // Class unloading. Clear weak refs in MDO's (ProfileData)
  // at the end of the marking phase.
  static void follow_mdo_weak_refs();

  // Debugging
  static void trace(const char* msg) PRODUCT_RETURN;

 public:
  // Public closures
  static FollowRootClosure    follow_root_closure;
  static CodeBlobToOopClosure follow_code_root_closure; // => follow_root_closure
  static MarkAndPushClosure   mark_and_push_closure;
  static FollowStackClosure   follow_stack_closure;
  static AdjustPointerClosure adjust_root_pointer_closure;
  static AdjustPointerClosure adjust_pointer_closure;

  // Reference Processing
  static ReferenceProcessor* const ref_processor() { return _ref_processor; }

  // Call backs for marking
  static void mark_object(oop obj);
  // Mark pointer and follow contents.  Empty marking stack afterwards.
  template <class T> static inline void follow_root(T* p);
  // Mark pointer and follow contents.
  template <class T> static inline void mark_and_follow(T* p);
  // Check mark and maybe push on marking stack
  template <class T> static inline void mark_and_push(T* p);
  static inline void push_objarray(oop obj, size_t index);

  static void follow_stack();   // Empty marking stack.

  static void preserve_mark(oop p, markOop mark);
                                // Save the mark word so it can be restored later
  static void adjust_marks();   // Adjust the pointers in the preserved marks table
  static void restore_marks();  // Restore the marks that we saved in preserve_mark

  template <class T> static inline void adjust_pointer(T* p, bool isroot);

  static void adjust_root_pointer(oop* p)  { adjust_pointer(p, true); }
  static void adjust_pointer(oop* p)       { adjust_pointer(p, false); }
  static void adjust_pointer(narrowOop* p) { adjust_pointer(p, false); }

#ifdef VALIDATE_MARK_SWEEP
  static void track_adjusted_pointer(void* p, bool isroot);
  static void check_adjust_pointer(void* p);
  static void track_interior_pointers(oop obj);
  static void check_interior_pointers();

  static void reset_live_oop_tracking(bool at_perm);
  static void register_live_oop(oop p, size_t size);
  static void validate_live_oop(oop p, size_t size);
  static void live_oop_moved_to(HeapWord* q, size_t size, HeapWord* compaction_top);
  static void compaction_complete();

  // Querying operation of RecordMarkSweepCompaction results.
  // Finds and prints the current base oop and offset for a word
  // within an oop that was live during the last GC. Helpful for
  // tracking down heap stomps.
  static void print_new_location_of_heap_address(HeapWord* q);
#endif

  // Call backs for class unloading
  // Update subklass/sibling/implementor links at end of marking.
  static void revisit_weak_klass_link(Klass* k);
  // For weak refs clearing in MDO's
  static void revisit_mdo(DataLayout* p);
};

class PreservedMark VALUE_OBJ_CLASS_SPEC {
private:
  oop _obj;
  markOop _mark;

public:
  void init(oop obj, markOop mark) {
    _obj = obj;
    _mark = mark;
  }

  void adjust_pointer() {
    MarkSweep::adjust_pointer(&_obj);
  }

  void restore() {
    _obj->set_mark(_mark);
  }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_SHARED_MARKSWEEP_HPP
