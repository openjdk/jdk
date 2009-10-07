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

# include "incls/_precompiled.incl"
# include "incls/_sweeper.cpp.incl"

long      NMethodSweeper::_traversals = 0;   // No. of stack traversals performed
CodeBlob* NMethodSweeper::_current = NULL;   // Current nmethod
int       NMethodSweeper::_seen = 0 ;        // No. of blobs we have currently processed in current pass of CodeCache
int       NMethodSweeper::_invocations = 0;  // No. of invocations left until we are completed with this pass

jint      NMethodSweeper::_locked_seen = 0;
jint      NMethodSweeper::_not_entrant_seen_on_stack = 0;
bool      NMethodSweeper::_rescan = false;

void NMethodSweeper::sweep() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be executed at a safepoint");
  if (!MethodFlushing) return;

  // No need to synchronize access, since this is always executed at a
  // safepoint.  If we aren't in the middle of scan and a rescan
  // hasn't been requested then just return.
  if (_current == NULL && !_rescan) return;

  // Make sure CompiledIC_lock in unlocked, since we might update some
  // inline caches. If it is, we just bail-out and try later.
  if (CompiledIC_lock->is_locked() || Patching_lock->is_locked()) return;

  // Check for restart
  assert(CodeCache::find_blob_unsafe(_current) == _current, "Sweeper nmethod cached state invalid");
  if (_current == NULL) {
    _seen        = 0;
    _invocations = NmethodSweepFraction;
    _current     = CodeCache::first();
    _traversals  += 1;
    if (PrintMethodFlushing) {
      tty->print_cr("### Sweep: stack traversal %d", _traversals);
    }
    Threads::nmethods_do();

    // reset the flags since we started a scan from the beginning.
    _rescan = false;
    _locked_seen = 0;
    _not_entrant_seen_on_stack = 0;
  }

  if (PrintMethodFlushing && Verbose) {
    tty->print_cr("### Sweep at %d out of %d. Invocations left: %d", _seen, CodeCache::nof_blobs(), _invocations);
  }

  // We want to visit all nmethods after NmethodSweepFraction invocations.
  // If invocation is 1 we do the rest
  int todo = CodeCache::nof_blobs();
  if (_invocations != 1) {
    todo = (CodeCache::nof_blobs() - _seen) / _invocations;
    _invocations--;
  }

  for(int i = 0; i < todo && _current != NULL; i++) {
    CodeBlob* next = CodeCache::next(_current); // Read next before we potentially delete current
    if (_current->is_nmethod()) {
      process_nmethod((nmethod *)_current);
    }
    _seen++;
    _current = next;
  }
  // Because we could stop on a codeBlob other than an nmethod we skip forward
  // to the next nmethod (if any). codeBlobs other than nmethods can be freed
  // async to us and make _current invalid while we sleep.
  while (_current != NULL && !_current->is_nmethod()) {
    _current = CodeCache::next(_current);
  }

  if (_current == NULL && !_rescan && (_locked_seen || _not_entrant_seen_on_stack)) {
    // we've completed a scan without making progress but there were
    // nmethods we were unable to process either because they were
    // locked or were still on stack.  We don't have to aggresively
    // clean them up so just stop scanning.  We could scan once more
    // but that complicates the control logic and it's unlikely to
    // matter much.
    if (PrintMethodFlushing) {
      tty->print_cr("### Couldn't make progress on some nmethods so stopping sweep");
    }
  }
}


void NMethodSweeper::process_nmethod(nmethod *nm) {
  // Skip methods that are currently referenced by the VM
  if (nm->is_locked_by_vm()) {
    // But still remember to clean-up inline caches for alive nmethods
    if (nm->is_alive()) {
      // Clean-up all inline caches that points to zombie/non-reentrant methods
      nm->cleanup_inline_caches();
    } else {
      _locked_seen++;
    }
    return;
  }

  if (nm->is_zombie()) {
    // If it is first time, we see nmethod then we mark it. Otherwise,
    // we reclame it. When we have seen a zombie method twice, we know that
    // there are no inline caches that referes to it.
    if (nm->is_marked_for_reclamation()) {
      assert(!nm->is_locked_by_vm(), "must not flush locked nmethods");
      if (PrintMethodFlushing && Verbose) {
        tty->print_cr("### Nmethod 0x%x (marked for reclamation) being flushed", nm);
      }
      nm->flush();
    } else {
      if (PrintMethodFlushing && Verbose) {
        tty->print_cr("### Nmethod 0x%x (zombie) being marked for reclamation", nm);
      }
      nm->mark_for_reclamation();
      _rescan = true;
    }
  } else if (nm->is_not_entrant()) {
    // If there is no current activations of this method on the
    // stack we can safely convert it to a zombie method
    if (nm->can_not_entrant_be_converted()) {
      if (PrintMethodFlushing && Verbose) {
        tty->print_cr("### Nmethod 0x%x (not entrant) being made zombie", nm);
      }
      nm->make_zombie();
      _rescan = true;
    } else {
      // Still alive, clean up its inline caches
      nm->cleanup_inline_caches();
      // we coudn't transition this nmethod so don't immediately
      // request a rescan.  If this method stays on the stack for a
      // long time we don't want to keep rescanning at every safepoint.
      _not_entrant_seen_on_stack++;
    }
  } else if (nm->is_unloaded()) {
    // Unloaded code, just make it a zombie
    if (PrintMethodFlushing && Verbose)
      tty->print_cr("### Nmethod 0x%x (unloaded) being made zombie", nm);
    if (nm->is_osr_method()) {
      // No inline caches will ever point to osr methods, so we can just remove it
      nm->flush();
    } else {
      nm->make_zombie();
      _rescan = true;
    }
  } else {
    assert(nm->is_alive(), "should be alive");
    // Clean-up all inline caches that points to zombie/non-reentrant methods
    nm->cleanup_inline_caches();
  }
}
