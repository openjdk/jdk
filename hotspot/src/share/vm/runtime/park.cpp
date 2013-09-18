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

#include "precompiled.hpp"
#include "runtime/thread.hpp"



// Lifecycle management for TSM ParkEvents.
// ParkEvents are type-stable (TSM).
// In our particular implementation they happen to be immortal.
//
// We manage concurrency on the FreeList with a CAS-based
// detach-modify-reattach idiom that avoids the ABA problems
// that would otherwise be present in a simple CAS-based
// push-pop implementation.   (push-one and pop-all)
//
// Caveat: Allocate() and Release() may be called from threads
// other than the thread associated with the Event!
// If we need to call Allocate() when running as the thread in
// question then look for the PD calls to initialize native TLS.
// Native TLS (Win32/Linux/Solaris) can only be initialized or
// accessed by the associated thread.
// See also pd_initialize().
//
// Note that we could defer associating a ParkEvent with a thread
// until the 1st time the thread calls park().  unpark() calls to
// an unprovisioned thread would be ignored.  The first park() call
// for a thread would allocate and associate a ParkEvent and return
// immediately.

volatile int ParkEvent::ListLock = 0 ;
ParkEvent * volatile ParkEvent::FreeList = NULL ;

ParkEvent * ParkEvent::Allocate (Thread * t) {
  // In rare cases -- JVM_RawMonitor* operations -- we can find t == null.
  ParkEvent * ev ;

  // Start by trying to recycle an existing but unassociated
  // ParkEvent from the global free list.
  for (;;) {
    ev = FreeList ;
    if (ev == NULL) break ;
    // 1: Detach - sequester or privatize the list
    // Tantamount to ev = Swap (&FreeList, NULL)
    if (Atomic::cmpxchg_ptr (NULL, &FreeList, ev) != ev) {
       continue ;
    }

    // We've detached the list.  The list in-hand is now
    // local to this thread.   This thread can operate on the
    // list without risk of interference from other threads.
    // 2: Extract -- pop the 1st element from the list.
    ParkEvent * List = ev->FreeNext ;
    if (List == NULL) break ;
    for (;;) {
        // 3: Try to reattach the residual list
        guarantee (List != NULL, "invariant") ;
        ParkEvent * Arv =  (ParkEvent *) Atomic::cmpxchg_ptr (List, &FreeList, NULL) ;
        if (Arv == NULL) break ;

        // New nodes arrived.  Try to detach the recent arrivals.
        if (Atomic::cmpxchg_ptr (NULL, &FreeList, Arv) != Arv) {
            continue ;
        }
        guarantee (Arv != NULL, "invariant") ;
        // 4: Merge Arv into List
        ParkEvent * Tail = List ;
        while (Tail->FreeNext != NULL) Tail = Tail->FreeNext ;
        Tail->FreeNext = Arv ;
    }
    break ;
  }

  if (ev != NULL) {
    guarantee (ev->AssociatedWith == NULL, "invariant") ;
  } else {
    // Do this the hard way -- materialize a new ParkEvent.
    // In rare cases an allocating thread might detach a long list --
    // installing null into FreeList -- and then stall or be obstructed.
    // A 2nd thread calling Allocate() would see FreeList == null.
    // The list held privately by the 1st thread is unavailable to the 2nd thread.
    // In that case the 2nd thread would have to materialize a new ParkEvent,
    // even though free ParkEvents existed in the system.  In this case we end up
    // with more ParkEvents in circulation than we need, but the race is
    // rare and the outcome is benign.  Ideally, the # of extant ParkEvents
    // is equal to the maximum # of threads that existed at any one time.
    // Because of the race mentioned above, segments of the freelist
    // can be transiently inaccessible.  At worst we may end up with the
    // # of ParkEvents in circulation slightly above the ideal.
    // Note that if we didn't have the TSM/immortal constraint, then
    // when reattaching, above, we could trim the list.
    ev = new ParkEvent () ;
    guarantee ((intptr_t(ev) & 0xFF) == 0, "invariant") ;
  }
  ev->reset() ;                     // courtesy to caller
  ev->AssociatedWith = t ;          // Associate ev with t
  ev->FreeNext       = NULL ;
  return ev ;
}

void ParkEvent::Release (ParkEvent * ev) {
  if (ev == NULL) return ;
  guarantee (ev->FreeNext == NULL      , "invariant") ;
  ev->AssociatedWith = NULL ;
  for (;;) {
    // Push ev onto FreeList
    // The mechanism is "half" lock-free.
    ParkEvent * List = FreeList ;
    ev->FreeNext = List ;
    if (Atomic::cmpxchg_ptr (ev, &FreeList, List) == List) break ;
  }
}

// Override operator new and delete so we can ensure that the
// least significant byte of ParkEvent addresses is 0.
// Beware that excessive address alignment is undesirable
// as it can result in D$ index usage imbalance as
// well as bank access imbalance on Niagara-like platforms,
// although Niagara's hash function should help.

void * ParkEvent::operator new (size_t sz) throw() {
  return (void *) ((intptr_t (AllocateHeap(sz + 256, mtInternal, CALLER_PC)) + 256) & -256) ;
}

void ParkEvent::operator delete (void * a) {
  // ParkEvents are type-stable and immortal ...
  ShouldNotReachHere();
}


// 6399321 As a temporary measure we copied & modified the ParkEvent::
// allocate() and release() code for use by Parkers.  The Parker:: forms
// will eventually be removed as we consolide and shift over to ParkEvents
// for both builtin synchronization and JSR166 operations.

volatile int Parker::ListLock = 0 ;
Parker * volatile Parker::FreeList = NULL ;

Parker * Parker::Allocate (JavaThread * t) {
  guarantee (t != NULL, "invariant") ;
  Parker * p ;

  // Start by trying to recycle an existing but unassociated
  // Parker from the global free list.
  for (;;) {
    p = FreeList ;
    if (p  == NULL) break ;
    // 1: Detach
    // Tantamount to p = Swap (&FreeList, NULL)
    if (Atomic::cmpxchg_ptr (NULL, &FreeList, p) != p) {
       continue ;
    }

    // We've detached the list.  The list in-hand is now
    // local to this thread.   This thread can operate on the
    // list without risk of interference from other threads.
    // 2: Extract -- pop the 1st element from the list.
    Parker * List = p->FreeNext ;
    if (List == NULL) break ;
    for (;;) {
        // 3: Try to reattach the residual list
        guarantee (List != NULL, "invariant") ;
        Parker * Arv =  (Parker *) Atomic::cmpxchg_ptr (List, &FreeList, NULL) ;
        if (Arv == NULL) break ;

        // New nodes arrived.  Try to detach the recent arrivals.
        if (Atomic::cmpxchg_ptr (NULL, &FreeList, Arv) != Arv) {
            continue ;
        }
        guarantee (Arv != NULL, "invariant") ;
        // 4: Merge Arv into List
        Parker * Tail = List ;
        while (Tail->FreeNext != NULL) Tail = Tail->FreeNext ;
        Tail->FreeNext = Arv ;
    }
    break ;
  }

  if (p != NULL) {
    guarantee (p->AssociatedWith == NULL, "invariant") ;
  } else {
    // Do this the hard way -- materialize a new Parker..
    // In rare cases an allocating thread might detach
    // a long list -- installing null into FreeList --and
    // then stall.  Another thread calling Allocate() would see
    // FreeList == null and then invoke the ctor.  In this case we
    // end up with more Parkers in circulation than we need, but
    // the race is rare and the outcome is benign.
    // Ideally, the # of extant Parkers is equal to the
    // maximum # of threads that existed at any one time.
    // Because of the race mentioned above, segments of the
    // freelist can be transiently inaccessible.  At worst
    // we may end up with the # of Parkers in circulation
    // slightly above the ideal.
    p = new Parker() ;
  }
  p->AssociatedWith = t ;          // Associate p with t
  p->FreeNext       = NULL ;
  return p ;
}


void Parker::Release (Parker * p) {
  if (p == NULL) return ;
  guarantee (p->AssociatedWith != NULL, "invariant") ;
  guarantee (p->FreeNext == NULL      , "invariant") ;
  p->AssociatedWith = NULL ;
  for (;;) {
    // Push p onto FreeList
    Parker * List = FreeList ;
    p->FreeNext = List ;
    if (Atomic::cmpxchg_ptr (p, &FreeList, List) == List) break ;
  }
}

