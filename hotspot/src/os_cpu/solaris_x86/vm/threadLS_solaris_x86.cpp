/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_threadLS_solaris_x86.cpp.incl"

#ifdef AMD64
extern "C" Thread*  fs_load(ptrdiff_t tlsOffset);
extern "C" intptr_t fs_thread();
#else
// From solaris_i486.s
extern "C" Thread*  gs_load(ptrdiff_t tlsOffset);
extern "C" intptr_t gs_thread();
#endif // AMD64

// tlsMode encoding:
//
// pd_tlsAccessUndefined : uninitialized
// pd_tlsAccessSlow      : not available
// pd_tlsAccessIndirect  :
//   old-style indirect access - present in "T1" libthread.
//   use thr_slot_sync_allocate() to attempt to allocate a slot.
// pd_tlsAccessDirect    :
//   new-style direct access - present in late-model "T2" libthread.
//   Allocate the offset (slot) via _thr_slot_offset() or by
//   defining an IE- or LE-mode TLS/TSD slot in the launcher and then passing
//   that offset into libjvm.so.
//   See http://sac.eng/Archives/CaseLog/arc/PSARC/2003/159/.
//
// Note that we have a capability gap - some early model T2 forms
// (e.g., unpatched S9) have neither _thr_slot_sync_allocate() nor
// _thr_slot_offset().  In that case we revert to the usual
// thr_getspecific accessor.
//

static ThreadLocalStorage::pd_tlsAccessMode tlsMode = ThreadLocalStorage::pd_tlsAccessUndefined ;
static ptrdiff_t tlsOffset = 0 ;
static thread_key_t tlsKey ;

typedef int (*TSSA_Entry) (ptrdiff_t *, int, int) ;
typedef ptrdiff_t (*TSO_Entry) (int) ;

ThreadLocalStorage::pd_tlsAccessMode ThreadLocalStorage::pd_getTlsAccessMode ()
{
   guarantee (tlsMode != pd_tlsAccessUndefined, "tlsMode not set") ;
   return tlsMode ;
}

ptrdiff_t ThreadLocalStorage::pd_getTlsOffset () {
   guarantee (tlsMode != pd_tlsAccessUndefined, "tlsMode not set") ;
   return tlsOffset ;
}

// TODO: Consider the following improvements:
//
// 1.   Convert from thr_*specific* to pthread_*specific*.
//      The pthread_ forms are slightly faster.  Also, the
//      pthread_ forms have a pthread_key_delete() API which
//      would aid in clean JVM shutdown and the eventual goal
//      of permitting a JVM to reinstantiate itself withing a process.
//
// 2.   See ThreadLocalStorage::init().  We end up allocating
//      two TLS keys during VM startup.  That's benign, but we could collapse
//      down to one key without too much trouble.
//
// 3.   MacroAssembler::get_thread() currently emits calls to thr_getspecific().
//      Modify get_thread() to call Thread::current() instead.
//
// 4.   Thread::current() currently uses a cache keyed by %gs:[0].
//      (The JVM has PSARC permission to use %g7/%gs:[0]
//      as an opaque temporally unique thread identifier).
//      For C++ access to a thread's reflexive "self" pointer we
//      should consider using one of the following:
//      a. a radix tree keyed by %esp - as in EVM.
//         This requires two loads (the 2nd dependent on the 1st), but
//         is easily inlined and doesn't require a "miss" slow path.
//      b. a fast TLS/TSD slot allocated by _thr_slot_offset
//         or _thr_slot_sync_allocate.
//
// 5.   'generate_code_for_get_thread' is a misnomer.
//      We should change it to something more general like
//      pd_ThreadSelf_Init(), for instance.
//

static void AllocateTLSOffset ()
{
   int rslt ;
   TSSA_Entry tssa ;
   TSO_Entry  tso ;
   ptrdiff_t off ;

   guarantee (tlsMode == ThreadLocalStorage::pd_tlsAccessUndefined, "tlsMode not set") ;
   tlsMode = ThreadLocalStorage::pd_tlsAccessSlow ;
   tlsOffset = 0 ;
#ifndef AMD64

   tssa = (TSSA_Entry) dlsym (RTLD_DEFAULT, "thr_slot_sync_allocate") ;
   if (tssa != NULL) {
        off = -1 ;
        rslt = (*tssa)(&off, NULL, NULL) ;                // (off,dtor,darg)
        if (off != -1) {
           tlsOffset = off ;
           tlsMode = ThreadLocalStorage::pd_tlsAccessIndirect ;
           return ;
        }
    }

    rslt = thr_keycreate (&tlsKey, NULL) ;
    if (rslt != 0) {
        tlsMode = ThreadLocalStorage::pd_tlsAccessSlow ;   // revert to slow mode
        return ;
    }

    tso = (TSO_Entry) dlsym (RTLD_DEFAULT, "_thr_slot_offset") ;
    if (tso != NULL) {
        off = (*tso)(tlsKey) ;
        if (off >= 0) {
           tlsOffset = off ;
           tlsMode = ThreadLocalStorage::pd_tlsAccessDirect ;
           return ;
        }
    }

    // Failure: Too bad ... we've allocated a TLS slot we don't need and there's
    // no provision in the ABI for returning the slot.
    //
    // If we didn't find a slot then then:
    // 1. We might be on liblwp.
    // 2. We might be on T2 libthread, but all "fast" slots are already
    //    consumed
    // 3. We might be on T1, and all TSD (thr_slot_sync_allocate) slots are
    //    consumed.
    // 4. We might be on T2 libthread, but it's be re-architected
    //    so that fast slots are no longer g7-relative.
    //

    tlsMode = ThreadLocalStorage::pd_tlsAccessSlow ;
    return ;
#endif // AMD64
}

void ThreadLocalStorage::generate_code_for_get_thread() {
    AllocateTLSOffset() ;
}

void ThreadLocalStorage::set_thread_in_slot(Thread *thread) {
  guarantee (tlsMode != pd_tlsAccessUndefined, "tlsMode not set") ;
  if (tlsMode == pd_tlsAccessIndirect) {
#ifdef AMD64
        intptr_t tbase = fs_thread();
#else
        intptr_t tbase = gs_thread();
#endif // AMD64
        *((Thread**) (tbase + tlsOffset)) = thread ;
  } else
  if (tlsMode == pd_tlsAccessDirect) {
        thr_setspecific (tlsKey, (void *) thread) ;
        // set with thr_setspecific and then readback with gs_load to validate.
#ifdef AMD64
        guarantee (thread == fs_load(tlsOffset), "tls readback failure") ;
#else
        guarantee (thread == gs_load(tlsOffset), "tls readback failure") ;
#endif // AMD64
  }
}


extern "C" Thread* get_thread() {
  return ThreadLocalStorage::thread();
}
