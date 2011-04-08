/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/shared/markSweep.hpp"
#include "gc_interface/collectedHeap.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/init.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/threadLocalStorage.hpp"
#include "runtime/vframe.hpp"
#include "utilities/preserveException.hpp"


// Implementation of InterfaceSupport

#ifdef ASSERT

long InterfaceSupport::_number_of_calls       = 0;
long InterfaceSupport::_scavenge_alot_counter = 1;
long InterfaceSupport::_fullgc_alot_counter   = 1;
long InterfaceSupport::_fullgc_alot_invocation = 0;

Histogram* RuntimeHistogram;

RuntimeHistogramElement::RuntimeHistogramElement(const char* elementName) {
  static volatile jint RuntimeHistogram_lock = 0;
  _name = elementName;
  uintx count = 0;

  while (Atomic::cmpxchg(1, &RuntimeHistogram_lock, 0) != 0) {
    while (OrderAccess::load_acquire(&RuntimeHistogram_lock) != 0) {
      count +=1;
      if ( (WarnOnStalledSpinLock > 0)
        && (count % WarnOnStalledSpinLock == 0)) {
        warning("RuntimeHistogram_lock seems to be stalled");
      }
    }
  }

  if (RuntimeHistogram == NULL) {
    RuntimeHistogram = new Histogram("VM Runtime Call Counts",200);
  }

  RuntimeHistogram->add_element(this);
  Atomic::dec(&RuntimeHistogram_lock);
}

void InterfaceSupport::trace(const char* result_type, const char* header) {
  tty->print_cr("%6d  %s", _number_of_calls, header);
}

void InterfaceSupport::gc_alot() {
  Thread *thread = Thread::current();
  if (!thread->is_Java_thread()) return; // Avoid concurrent calls
  // Check for new, not quite initialized thread. A thread in new mode cannot initiate a GC.
  JavaThread *current_thread = (JavaThread *)thread;
  if (current_thread->active_handles() == NULL) return;

  // Short-circuit any possible re-entrant gc-a-lot attempt
  if (thread->skip_gcalot()) return;

  if (is_init_completed()) {

    if (++_fullgc_alot_invocation < FullGCALotStart) {
      return;
    }

    // Use this line if you want to block at a specific point,
    // e.g. one number_of_calls/scavenge/gc before you got into problems
    if (FullGCALot) _fullgc_alot_counter--;

    // Check if we should force a full gc
    if (_fullgc_alot_counter == 0) {
      // Release dummy so objects are forced to move
      if (!Universe::release_fullgc_alot_dummy()) {
        warning("FullGCALot: Unable to release more dummies at bottom of heap");
      }
      HandleMark hm(thread);
      Universe::heap()->collect(GCCause::_full_gc_alot);
      unsigned int invocations = Universe::heap()->total_full_collections();
      // Compute new interval
      if (FullGCALotInterval > 1) {
        _fullgc_alot_counter = 1+(long)((double)FullGCALotInterval*os::random()/(max_jint+1.0));
        if (PrintGCDetails && Verbose) {
          tty->print_cr("Full gc no: %u\tInterval: %d", invocations,
                        _fullgc_alot_counter);
        }
      } else {
        _fullgc_alot_counter = 1;
      }
      // Print progress message
      if (invocations % 100 == 0) {
        if (PrintGCDetails && Verbose) tty->print_cr("Full gc no: %u", invocations);
      }
    } else {
      if (ScavengeALot) _scavenge_alot_counter--;
      // Check if we should force a scavenge
      if (_scavenge_alot_counter == 0) {
        HandleMark hm(thread);
        Universe::heap()->collect(GCCause::_scavenge_alot);
        unsigned int invocations = Universe::heap()->total_collections() - Universe::heap()->total_full_collections();
        // Compute new interval
        if (ScavengeALotInterval > 1) {
          _scavenge_alot_counter = 1+(long)((double)ScavengeALotInterval*os::random()/(max_jint+1.0));
          if (PrintGCDetails && Verbose) {
            tty->print_cr("Scavenge no: %u\tInterval: %d", invocations,
                          _scavenge_alot_counter);
          }
        } else {
          _scavenge_alot_counter = 1;
        }
        // Print progress message
        if (invocations % 1000 == 0) {
          if (PrintGCDetails && Verbose) tty->print_cr("Scavenge no: %u", invocations);
        }
      }
    }
  }
}


vframe* vframe_array[50];
int walk_stack_counter = 0;

void InterfaceSupport::walk_stack_from(vframe* start_vf) {
  // walk
  int i = 0;
  for (vframe* f = start_vf; f; f = f->sender() ) {
    if (i < 50) vframe_array[i++] = f;
  }
}


void InterfaceSupport::walk_stack() {
  JavaThread* thread = JavaThread::current();
  walk_stack_counter++;
  if (!thread->has_last_Java_frame()) return;
  ResourceMark rm(thread);
  RegisterMap reg_map(thread);
  walk_stack_from(thread->last_java_vframe(&reg_map));
}


# ifdef ENABLE_ZAP_DEAD_LOCALS

static int zap_traversals = 0;

void InterfaceSupport::zap_dead_locals_old() {
  JavaThread* thread = JavaThread::current();
  if (zap_traversals == -1) // edit constant for debugging
    warning("I am here");
  int zap_frame_count = 0; // count frames to help debugging
  for (StackFrameStream sfs(thread); !sfs.is_done(); sfs.next()) {
    sfs.current()->zap_dead_locals(thread, sfs.register_map());
    ++zap_frame_count;
  }
  ++zap_traversals;
}

# endif


int deoptimizeAllCounter = 0;
int zombieAllCounter = 0;


void InterfaceSupport::zombieAll() {
  if (is_init_completed() && zombieAllCounter > ZombieALotInterval) {
    zombieAllCounter = 0;
    VM_ZombieAll op;
    VMThread::execute(&op);
  } else {
    zombieAllCounter++;
  }
}

void InterfaceSupport::unlinkSymbols() {
  VM_UnlinkSymbols op;
  VMThread::execute(&op);
}

void InterfaceSupport::deoptimizeAll() {
  if (is_init_completed() ) {
    if (DeoptimizeALot && deoptimizeAllCounter > DeoptimizeALotInterval) {
      deoptimizeAllCounter = 0;
      VM_DeoptimizeAll op;
      VMThread::execute(&op);
    } else if (DeoptimizeRandom && (deoptimizeAllCounter & 0x1f) == (os::random() & 0x1f)) {
      VM_DeoptimizeAll op;
      VMThread::execute(&op);
    }
  }
  deoptimizeAllCounter++;
}


void InterfaceSupport::stress_derived_pointers() {
#ifdef COMPILER2
  JavaThread *thread = JavaThread::current();
  if (!is_init_completed()) return;
  ResourceMark rm(thread);
  bool found = false;
  for (StackFrameStream sfs(thread); !sfs.is_done() && !found; sfs.next()) {
    CodeBlob* cb = sfs.current()->cb();
    if (cb != NULL && cb->oop_maps() ) {
      // Find oopmap for current method
      OopMap* map = cb->oop_map_for_return_address(sfs.current()->pc());
      assert(map != NULL, "no oopmap found for pc");
      found = map->has_derived_pointer();
    }
  }
  if (found) {
    // $$$ Not sure what to do here.
    /*
    Scavenge::invoke(0);
    */
  }
#endif
}


void InterfaceSupport::verify_stack() {
  JavaThread* thread = JavaThread::current();
  ResourceMark rm(thread);
  // disabled because it throws warnings that oop maps should only be accessed
  // in VM thread or during debugging

  if (!thread->has_pending_exception()) {
    // verification does not work if there are pending exceptions
    StackFrameStream sfs(thread);
    CodeBlob* cb = sfs.current()->cb();
      // In case of exceptions we might not have a runtime_stub on
      // top of stack, hence, all callee-saved registers are not going
      // to be setup correctly, hence, we cannot do stack verify
    if (cb != NULL && !(cb->is_runtime_stub() || cb->is_uncommon_trap_stub())) return;

    for (; !sfs.is_done(); sfs.next()) {
      sfs.current()->verify(sfs.register_map());
    }
  }
}


void InterfaceSupport::verify_last_frame() {
  JavaThread* thread = JavaThread::current();
  ResourceMark rm(thread);
  RegisterMap reg_map(thread);
  frame fr = thread->last_frame();
  fr.verify(&reg_map);
}


#endif // ASSERT


void InterfaceSupport_init() {
#ifdef ASSERT
  if (ScavengeALot || FullGCALot) {
    srand(ScavengeALotInterval * FullGCALotInterval);
  }
#endif
}
