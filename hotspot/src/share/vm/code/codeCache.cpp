/*
 * Copyright 1997-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_codeCache.cpp.incl"

// Helper class for printing in CodeCache

class CodeBlob_sizes {
 private:
  int count;
  int total_size;
  int header_size;
  int code_size;
  int stub_size;
  int relocation_size;
  int scopes_oop_size;
  int scopes_data_size;
  int scopes_pcs_size;

 public:
  CodeBlob_sizes() {
    count            = 0;
    total_size       = 0;
    header_size      = 0;
    code_size        = 0;
    stub_size        = 0;
    relocation_size  = 0;
    scopes_oop_size  = 0;
    scopes_data_size = 0;
    scopes_pcs_size  = 0;
  }

  int total()                                    { return total_size; }
  bool is_empty()                                { return count == 0; }

  void print(const char* title) {
    tty->print_cr(" #%d %s = %dK (hdr %d%%,  loc %d%%, code %d%%, stub %d%%, [oops %d%%, data %d%%, pcs %d%%])",
                  count,
                  title,
                  total() / K,
                  header_size             * 100 / total_size,
                  relocation_size         * 100 / total_size,
                  code_size               * 100 / total_size,
                  stub_size               * 100 / total_size,
                  scopes_oop_size         * 100 / total_size,
                  scopes_data_size        * 100 / total_size,
                  scopes_pcs_size         * 100 / total_size);
  }

  void add(CodeBlob* cb) {
    count++;
    total_size       += cb->size();
    header_size      += cb->header_size();
    relocation_size  += cb->relocation_size();
    scopes_oop_size  += cb->oops_size();
    if (cb->is_nmethod()) {
      nmethod *nm = (nmethod*)cb;
      code_size        += nm->code_size();
      stub_size        += nm->stub_size();

      scopes_data_size += nm->scopes_data_size();
      scopes_pcs_size  += nm->scopes_pcs_size();
    } else {
      code_size        += cb->instructions_size();
    }
  }
};


// CodeCache implementation

CodeHeap * CodeCache::_heap = new CodeHeap();
int CodeCache::_number_of_blobs = 0;
int CodeCache::_number_of_nmethods_with_dependencies = 0;
bool CodeCache::_needs_cache_clean = false;
nmethod* CodeCache::_scavenge_root_nmethods = NULL;
nmethod* CodeCache::_saved_nmethods = NULL;


CodeBlob* CodeCache::first() {
  assert_locked_or_safepoint(CodeCache_lock);
  return (CodeBlob*)_heap->first();
}


CodeBlob* CodeCache::next(CodeBlob* cb) {
  assert_locked_or_safepoint(CodeCache_lock);
  return (CodeBlob*)_heap->next(cb);
}


CodeBlob* CodeCache::alive(CodeBlob *cb) {
  assert_locked_or_safepoint(CodeCache_lock);
  while (cb != NULL && !cb->is_alive()) cb = next(cb);
  return cb;
}


nmethod* CodeCache::alive_nmethod(CodeBlob* cb) {
  assert_locked_or_safepoint(CodeCache_lock);
  while (cb != NULL && (!cb->is_alive() || !cb->is_nmethod())) cb = next(cb);
  return (nmethod*)cb;
}

nmethod* CodeCache::first_nmethod() {
  assert_locked_or_safepoint(CodeCache_lock);
  CodeBlob* cb = first();
  while (cb != NULL && !cb->is_nmethod()) {
    cb = next(cb);
  }
  return (nmethod*)cb;
}

nmethod* CodeCache::next_nmethod (CodeBlob* cb) {
  assert_locked_or_safepoint(CodeCache_lock);
  cb = next(cb);
  while (cb != NULL && !cb->is_nmethod()) {
    cb = next(cb);
  }
  return (nmethod*)cb;
}

CodeBlob* CodeCache::allocate(int size) {
  // Do not seize the CodeCache lock here--if the caller has not
  // already done so, we are going to lose bigtime, since the code
  // cache will contain a garbage CodeBlob until the caller can
  // run the constructor for the CodeBlob subclass he is busy
  // instantiating.
  guarantee(size >= 0, "allocation request must be reasonable");
  assert_locked_or_safepoint(CodeCache_lock);
  CodeBlob* cb = NULL;
  _number_of_blobs++;
  while (true) {
    cb = (CodeBlob*)_heap->allocate(size);
    if (cb != NULL) break;
    if (!_heap->expand_by(CodeCacheExpansionSize)) {
      // Expansion failed
      return NULL;
    }
    if (PrintCodeCacheExtension) {
      ResourceMark rm;
      tty->print_cr("code cache extended to [" INTPTR_FORMAT ", " INTPTR_FORMAT "] (%d bytes)",
                    (intptr_t)_heap->begin(), (intptr_t)_heap->end(),
                    (address)_heap->end() - (address)_heap->begin());
    }
  }
  verify_if_often();
  print_trace("allocation", cb, size);
  return cb;
}

void CodeCache::free(CodeBlob* cb) {
  assert_locked_or_safepoint(CodeCache_lock);
  verify_if_often();

  print_trace("free", cb);
  if (cb->is_nmethod() && ((nmethod *)cb)->has_dependencies()) {
    _number_of_nmethods_with_dependencies--;
  }
  _number_of_blobs--;

  _heap->deallocate(cb);

  verify_if_often();
  assert(_number_of_blobs >= 0, "sanity check");
}


void CodeCache::commit(CodeBlob* cb) {
  // this is called by nmethod::nmethod, which must already own CodeCache_lock
  assert_locked_or_safepoint(CodeCache_lock);
  if (cb->is_nmethod() && ((nmethod *)cb)->has_dependencies()) {
    _number_of_nmethods_with_dependencies++;
  }
  // flush the hardware I-cache
  ICache::invalidate_range(cb->instructions_begin(), cb->instructions_size());
}


void CodeCache::flush() {
  assert_locked_or_safepoint(CodeCache_lock);
  Unimplemented();
}


// Iteration over CodeBlobs

#define FOR_ALL_BLOBS(var)       for (CodeBlob *var =       first() ; var != NULL; var =       next(var) )
#define FOR_ALL_ALIVE_BLOBS(var) for (CodeBlob *var = alive(first()); var != NULL; var = alive(next(var)))
#define FOR_ALL_ALIVE_NMETHODS(var) for (nmethod *var = alive_nmethod(first()); var != NULL; var = alive_nmethod(next(var)))


bool CodeCache::contains(void *p) {
  // It should be ok to call contains without holding a lock
  return _heap->contains(p);
}


// This method is safe to call without holding the CodeCache_lock, as long as a dead codeblob is not
// looked up (i.e., one that has been marked for deletion). It only dependes on the _segmap to contain
// valid indices, which it will always do, as long as the CodeBlob is not in the process of being recycled.
CodeBlob* CodeCache::find_blob(void* start) {
  CodeBlob* result = find_blob_unsafe(start);
  if (result == NULL) return NULL;
  // We could potientially look up non_entrant methods
  guarantee(!result->is_zombie() || result->is_locked_by_vm() || is_error_reported(), "unsafe access to zombie method");
  return result;
}

nmethod* CodeCache::find_nmethod(void* start) {
  CodeBlob *cb = find_blob(start);
  assert(cb == NULL || cb->is_nmethod(), "did not find an nmethod");
  return (nmethod*)cb;
}


void CodeCache::blobs_do(void f(CodeBlob* nm)) {
  assert_locked_or_safepoint(CodeCache_lock);
  FOR_ALL_BLOBS(p) {
    f(p);
  }
}


void CodeCache::nmethods_do(void f(nmethod* nm)) {
  assert_locked_or_safepoint(CodeCache_lock);
  FOR_ALL_BLOBS(nm) {
    if (nm->is_nmethod()) f((nmethod*)nm);
  }
}


int CodeCache::alignment_unit() {
  return (int)_heap->alignment_unit();
}


int CodeCache::alignment_offset() {
  return (int)_heap->alignment_offset();
}


// Mark code blobs for unloading if they contain otherwise
// unreachable oops.
void CodeCache::do_unloading(BoolObjectClosure* is_alive,
                             OopClosure* keep_alive,
                             bool unloading_occurred) {
  assert_locked_or_safepoint(CodeCache_lock);
  FOR_ALL_ALIVE_BLOBS(cb) {
    cb->do_unloading(is_alive, keep_alive, unloading_occurred);
  }
}

void CodeCache::blobs_do(CodeBlobClosure* f) {
  assert_locked_or_safepoint(CodeCache_lock);
  FOR_ALL_ALIVE_BLOBS(cb) {
    f->do_code_blob(cb);

#ifdef ASSERT
    if (cb->is_nmethod())
      ((nmethod*)cb)->verify_scavenge_root_oops();
#endif //ASSERT
  }
}

// Walk the list of methods which might contain non-perm oops.
void CodeCache::scavenge_root_nmethods_do(CodeBlobClosure* f) {
  assert_locked_or_safepoint(CodeCache_lock);
  debug_only(mark_scavenge_root_nmethods());

  for (nmethod* cur = scavenge_root_nmethods(); cur != NULL; cur = cur->scavenge_root_link()) {
    debug_only(cur->clear_scavenge_root_marked());
    assert(cur->scavenge_root_not_marked(), "");
    assert(cur->on_scavenge_root_list(), "else shouldn't be on this list");

    bool is_live = (!cur->is_zombie() && !cur->is_unloaded());
#ifndef PRODUCT
    if (TraceScavenge) {
      cur->print_on(tty, is_live ? "scavenge root" : "dead scavenge root"); tty->cr();
    }
#endif //PRODUCT
    if (is_live) {
      // Perform cur->oops_do(f), maybe just once per nmethod.
      f->do_code_blob(cur);
      cur->fix_oop_relocations();
    }
  }

  // Check for stray marks.
  debug_only(verify_perm_nmethods(NULL));
}

void CodeCache::add_scavenge_root_nmethod(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
  nm->set_on_scavenge_root_list();
  nm->set_scavenge_root_link(_scavenge_root_nmethods);
  set_scavenge_root_nmethods(nm);
  print_trace("add_scavenge_root", nm);
}

void CodeCache::drop_scavenge_root_nmethod(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
  print_trace("drop_scavenge_root", nm);
  nmethod* last = NULL;
  nmethod* cur = scavenge_root_nmethods();
  while (cur != NULL) {
    nmethod* next = cur->scavenge_root_link();
    if (cur == nm) {
      if (last != NULL)
            last->set_scavenge_root_link(next);
      else  set_scavenge_root_nmethods(next);
      nm->set_scavenge_root_link(NULL);
      nm->clear_on_scavenge_root_list();
      return;
    }
    last = cur;
    cur = next;
  }
  assert(false, "should have been on list");
}

void CodeCache::prune_scavenge_root_nmethods() {
  assert_locked_or_safepoint(CodeCache_lock);
  debug_only(mark_scavenge_root_nmethods());

  nmethod* last = NULL;
  nmethod* cur = scavenge_root_nmethods();
  while (cur != NULL) {
    nmethod* next = cur->scavenge_root_link();
    debug_only(cur->clear_scavenge_root_marked());
    assert(cur->scavenge_root_not_marked(), "");
    assert(cur->on_scavenge_root_list(), "else shouldn't be on this list");

    if (!cur->is_zombie() && !cur->is_unloaded()
        && cur->detect_scavenge_root_oops()) {
      // Keep it.  Advance 'last' to prevent deletion.
      last = cur;
    } else {
      // Prune it from the list, so we don't have to look at it any more.
      print_trace("prune_scavenge_root", cur);
      cur->set_scavenge_root_link(NULL);
      cur->clear_on_scavenge_root_list();
      if (last != NULL)
            last->set_scavenge_root_link(next);
      else  set_scavenge_root_nmethods(next);
    }
    cur = next;
  }

  // Check for stray marks.
  debug_only(verify_perm_nmethods(NULL));
}

#ifndef PRODUCT
void CodeCache::asserted_non_scavengable_nmethods_do(CodeBlobClosure* f) {
  // While we are here, verify the integrity of the list.
  mark_scavenge_root_nmethods();
  for (nmethod* cur = scavenge_root_nmethods(); cur != NULL; cur = cur->scavenge_root_link()) {
    assert(cur->on_scavenge_root_list(), "else shouldn't be on this list");
    cur->clear_scavenge_root_marked();
  }
  verify_perm_nmethods(f);
}

// Temporarily mark nmethods that are claimed to be on the non-perm list.
void CodeCache::mark_scavenge_root_nmethods() {
  FOR_ALL_ALIVE_BLOBS(cb) {
    if (cb->is_nmethod()) {
      nmethod *nm = (nmethod*)cb;
      assert(nm->scavenge_root_not_marked(), "clean state");
      if (nm->on_scavenge_root_list())
        nm->set_scavenge_root_marked();
    }
  }
}

// If the closure is given, run it on the unlisted nmethods.
// Also make sure that the effects of mark_scavenge_root_nmethods is gone.
void CodeCache::verify_perm_nmethods(CodeBlobClosure* f_or_null) {
  FOR_ALL_ALIVE_BLOBS(cb) {
    bool call_f = (f_or_null != NULL);
    if (cb->is_nmethod()) {
      nmethod *nm = (nmethod*)cb;
      assert(nm->scavenge_root_not_marked(), "must be already processed");
      if (nm->on_scavenge_root_list())
        call_f = false;  // don't show this one to the client
      nm->verify_scavenge_root_oops();
    } else {
      call_f = false;   // not an nmethod
    }
    if (call_f)  f_or_null->do_code_blob(cb);
  }
}
#endif //PRODUCT


nmethod* CodeCache::find_and_remove_saved_code(methodOop m) {
  MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  nmethod* saved = _saved_nmethods;
  nmethod* prev = NULL;
  while (saved != NULL) {
    if (saved->is_in_use() && saved->method() == m) {
      if (prev != NULL) {
        prev->set_saved_nmethod_link(saved->saved_nmethod_link());
      } else {
        _saved_nmethods = saved->saved_nmethod_link();
      }
      assert(saved->is_speculatively_disconnected(), "shouldn't call for other nmethods");
      saved->set_speculatively_disconnected(false);
      saved->set_saved_nmethod_link(NULL);
      if (PrintMethodFlushing) {
        saved->print_on(tty, " ### nmethod is reconnected\n");
      }
      if (LogCompilation && (xtty != NULL)) {
        ttyLocker ttyl;
        xtty->begin_elem("nmethod_reconnected compile_id='%3d'", saved->compile_id());
        xtty->method(methodOop(m));
        xtty->stamp();
        xtty->end_elem();
      }
      return saved;
    }
    prev = saved;
    saved = saved->saved_nmethod_link();
  }
  return NULL;
}

void CodeCache::remove_saved_code(nmethod* nm) {
  // For conc swpr this will be called with CodeCache_lock taken by caller
  assert_locked_or_safepoint(CodeCache_lock);
  assert(nm->is_speculatively_disconnected(), "shouldn't call for other nmethods");
  nmethod* saved = _saved_nmethods;
  nmethod* prev = NULL;
  while (saved != NULL) {
    if (saved == nm) {
      if (prev != NULL) {
        prev->set_saved_nmethod_link(saved->saved_nmethod_link());
      } else {
        _saved_nmethods = saved->saved_nmethod_link();
      }
      if (LogCompilation && (xtty != NULL)) {
        ttyLocker ttyl;
        xtty->begin_elem("nmethod_removed compile_id='%3d'", nm->compile_id());
        xtty->stamp();
        xtty->end_elem();
      }
      return;
    }
    prev = saved;
    saved = saved->saved_nmethod_link();
  }
  ShouldNotReachHere();
}

void CodeCache::speculatively_disconnect(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
  assert(nm->is_in_use() && !nm->is_speculatively_disconnected(), "should only disconnect live nmethods");
  nm->set_saved_nmethod_link(_saved_nmethods);
  _saved_nmethods = nm;
  if (PrintMethodFlushing) {
    nm->print_on(tty, " ### nmethod is speculatively disconnected\n");
  }
  if (LogCompilation && (xtty != NULL)) {
    ttyLocker ttyl;
    xtty->begin_elem("nmethod_disconnected compile_id='%3d'", nm->compile_id());
    xtty->method(methodOop(nm->method()));
    xtty->stamp();
    xtty->end_elem();
  }
  nm->method()->clear_code();
  nm->set_speculatively_disconnected(true);
}


void CodeCache::gc_prologue() {
  assert(!nmethod::oops_do_marking_is_active(), "oops_do_marking_epilogue must be called");
}


void CodeCache::gc_epilogue() {
  assert_locked_or_safepoint(CodeCache_lock);
  FOR_ALL_ALIVE_BLOBS(cb) {
    if (cb->is_nmethod()) {
      nmethod *nm = (nmethod*)cb;
      assert(!nm->is_unloaded(), "Tautology");
      if (needs_cache_clean()) {
        nm->cleanup_inline_caches();
      }
      debug_only(nm->verify();)
    }
    cb->fix_oop_relocations();
  }
  set_needs_cache_clean(false);
  prune_scavenge_root_nmethods();
  assert(!nmethod::oops_do_marking_is_active(), "oops_do_marking_prologue must be called");
}


address CodeCache::first_address() {
  assert_locked_or_safepoint(CodeCache_lock);
  return (address)_heap->begin();
}


address CodeCache::last_address() {
  assert_locked_or_safepoint(CodeCache_lock);
  return (address)_heap->end();
}


void icache_init();

void CodeCache::initialize() {
  assert(CodeCacheSegmentSize >= (uintx)CodeEntryAlignment, "CodeCacheSegmentSize must be large enough to align entry points");
#ifdef COMPILER2
  assert(CodeCacheSegmentSize >= (uintx)OptoLoopAlignment,  "CodeCacheSegmentSize must be large enough to align inner loops");
#endif
  assert(CodeCacheSegmentSize >= sizeof(jdouble),    "CodeCacheSegmentSize must be large enough to align constants");
  // This was originally just a check of the alignment, causing failure, instead, round
  // the code cache to the page size.  In particular, Solaris is moving to a larger
  // default page size.
  CodeCacheExpansionSize = round_to(CodeCacheExpansionSize, os::vm_page_size());
  InitialCodeCacheSize = round_to(InitialCodeCacheSize, os::vm_page_size());
  ReservedCodeCacheSize = round_to(ReservedCodeCacheSize, os::vm_page_size());
  if (!_heap->reserve(ReservedCodeCacheSize, InitialCodeCacheSize, CodeCacheSegmentSize)) {
    vm_exit_during_initialization("Could not reserve enough space for code cache");
  }

  MemoryService::add_code_heap_memory_pool(_heap);

  // Initialize ICache flush mechanism
  // This service is needed for os::register_code_area
  icache_init();

  // Give OS a chance to register generated code area.
  // This is used on Windows 64 bit platforms to register
  // Structured Exception Handlers for our generated code.
  os::register_code_area(_heap->low_boundary(), _heap->high_boundary());
}


void codeCache_init() {
  CodeCache::initialize();
}

//------------------------------------------------------------------------------------------------

int CodeCache::number_of_nmethods_with_dependencies() {
  return _number_of_nmethods_with_dependencies;
}

void CodeCache::clear_inline_caches() {
  assert_locked_or_safepoint(CodeCache_lock);
  FOR_ALL_ALIVE_NMETHODS(nm) {
    nm->clear_inline_caches();
  }
}

#ifndef PRODUCT
// used to keep track of how much time is spent in mark_for_deoptimization
static elapsedTimer dependentCheckTime;
static int dependentCheckCount = 0;
#endif // PRODUCT


int CodeCache::mark_for_deoptimization(DepChange& changes) {
  MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

#ifndef PRODUCT
  dependentCheckTime.start();
  dependentCheckCount++;
#endif // PRODUCT

  int number_of_marked_CodeBlobs = 0;

  // search the hierarchy looking for nmethods which are affected by the loading of this class

  // then search the interfaces this class implements looking for nmethods
  // which might be dependent of the fact that an interface only had one
  // implementor.

  { No_Safepoint_Verifier nsv;
    for (DepChange::ContextStream str(changes, nsv); str.next(); ) {
      klassOop d = str.klass();
      number_of_marked_CodeBlobs += instanceKlass::cast(d)->mark_dependent_nmethods(changes);
    }
  }

  if (VerifyDependencies) {
    // Turn off dependency tracing while actually testing deps.
    NOT_PRODUCT( FlagSetting fs(TraceDependencies, false) );
    FOR_ALL_ALIVE_NMETHODS(nm) {
      if (!nm->is_marked_for_deoptimization() &&
          nm->check_all_dependencies()) {
        ResourceMark rm;
        tty->print_cr("Should have been marked for deoptimization:");
        changes.print();
        nm->print();
        nm->print_dependencies();
      }
    }
  }

#ifndef PRODUCT
  dependentCheckTime.stop();
#endif // PRODUCT

  return number_of_marked_CodeBlobs;
}


#ifdef HOTSWAP
int CodeCache::mark_for_evol_deoptimization(instanceKlassHandle dependee) {
  MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  int number_of_marked_CodeBlobs = 0;

  // Deoptimize all methods of the evolving class itself
  objArrayOop old_methods = dependee->methods();
  for (int i = 0; i < old_methods->length(); i++) {
    ResourceMark rm;
    methodOop old_method = (methodOop) old_methods->obj_at(i);
    nmethod *nm = old_method->code();
    if (nm != NULL) {
      nm->mark_for_deoptimization();
      number_of_marked_CodeBlobs++;
    }
  }

  FOR_ALL_ALIVE_NMETHODS(nm) {
    if (nm->is_marked_for_deoptimization()) {
      // ...Already marked in the previous pass; don't count it again.
    } else if (nm->is_evol_dependent_on(dependee())) {
      ResourceMark rm;
      nm->mark_for_deoptimization();
      number_of_marked_CodeBlobs++;
    } else  {
      // flush caches in case they refer to a redefined methodOop
      nm->clear_inline_caches();
    }
  }

  return number_of_marked_CodeBlobs;
}
#endif // HOTSWAP


// Deoptimize all methods
void CodeCache::mark_all_nmethods_for_deoptimization() {
  MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  FOR_ALL_ALIVE_NMETHODS(nm) {
    nm->mark_for_deoptimization();
  }
}


int CodeCache::mark_for_deoptimization(methodOop dependee) {
  MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  int number_of_marked_CodeBlobs = 0;

  FOR_ALL_ALIVE_NMETHODS(nm) {
    if (nm->is_dependent_on_method(dependee)) {
      ResourceMark rm;
      nm->mark_for_deoptimization();
      number_of_marked_CodeBlobs++;
    }
  }

  return number_of_marked_CodeBlobs;
}

void CodeCache::make_marked_nmethods_zombies() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");
  FOR_ALL_ALIVE_NMETHODS(nm) {
    if (nm->is_marked_for_deoptimization()) {

      // If the nmethod has already been made non-entrant and it can be converted
      // then zombie it now. Otherwise make it non-entrant and it will eventually
      // be zombied when it is no longer seen on the stack. Note that the nmethod
      // might be "entrant" and not on the stack and so could be zombied immediately
      // but we can't tell because we don't track it on stack until it becomes
      // non-entrant.

      if (nm->is_not_entrant() && nm->can_not_entrant_be_converted()) {
        nm->make_zombie();
      } else {
        nm->make_not_entrant();
      }
    }
  }
}

void CodeCache::make_marked_nmethods_not_entrant() {
  assert_locked_or_safepoint(CodeCache_lock);
  FOR_ALL_ALIVE_NMETHODS(nm) {
    if (nm->is_marked_for_deoptimization()) {
      nm->make_not_entrant();
    }
  }
}

void CodeCache::verify() {
  _heap->verify();
  FOR_ALL_ALIVE_BLOBS(p) {
    p->verify();
  }
}

//------------------------------------------------------------------------------------------------
// Non-product version

#ifndef PRODUCT

void CodeCache::verify_if_often() {
  if (VerifyCodeCacheOften) {
    _heap->verify();
  }
}

void CodeCache::print_trace(const char* event, CodeBlob* cb, int size) {
  if (PrintCodeCache2) {  // Need to add a new flag
    ResourceMark rm;
    if (size == 0)  size = cb->size();
    tty->print_cr("CodeCache %s:  addr: " INTPTR_FORMAT ", size: 0x%x", event, cb, size);
  }
}

void CodeCache::print_internals() {
  int nmethodCount = 0;
  int runtimeStubCount = 0;
  int adapterCount = 0;
  int deoptimizationStubCount = 0;
  int uncommonTrapStubCount = 0;
  int bufferBlobCount = 0;
  int total = 0;
  int nmethodAlive = 0;
  int nmethodNotEntrant = 0;
  int nmethodZombie = 0;
  int nmethodUnloaded = 0;
  int nmethodJava = 0;
  int nmethodNative = 0;
  int maxCodeSize = 0;
  ResourceMark rm;

  CodeBlob *cb;
  for (cb = first(); cb != NULL; cb = next(cb)) {
    total++;
    if (cb->is_nmethod()) {
      nmethod* nm = (nmethod*)cb;

      if (Verbose && nm->method() != NULL) {
        ResourceMark rm;
        char *method_name = nm->method()->name_and_sig_as_C_string();
        tty->print("%s", method_name);
        if(nm->is_alive()) { tty->print_cr(" alive"); }
        if(nm->is_not_entrant()) { tty->print_cr(" not-entrant"); }
        if(nm->is_zombie()) { tty->print_cr(" zombie"); }
      }

      nmethodCount++;

      if(nm->is_alive()) { nmethodAlive++; }
      if(nm->is_not_entrant()) { nmethodNotEntrant++; }
      if(nm->is_zombie()) { nmethodZombie++; }
      if(nm->is_unloaded()) { nmethodUnloaded++; }
      if(nm->is_native_method()) { nmethodNative++; }

      if(nm->method() != NULL && nm->is_java_method()) {
        nmethodJava++;
        if(nm->code_size() > maxCodeSize) {
          maxCodeSize = nm->code_size();
        }
      }
    } else if (cb->is_runtime_stub()) {
      runtimeStubCount++;
    } else if (cb->is_deoptimization_stub()) {
      deoptimizationStubCount++;
    } else if (cb->is_uncommon_trap_stub()) {
      uncommonTrapStubCount++;
    } else if (cb->is_adapter_blob()) {
      adapterCount++;
    } else if (cb->is_buffer_blob()) {
      bufferBlobCount++;
    }
  }

  int bucketSize = 512;
  int bucketLimit = maxCodeSize / bucketSize + 1;
  int *buckets = NEW_C_HEAP_ARRAY(int, bucketLimit);
  memset(buckets,0,sizeof(int) * bucketLimit);

  for (cb = first(); cb != NULL; cb = next(cb)) {
    if (cb->is_nmethod()) {
      nmethod* nm = (nmethod*)cb;
      if(nm->is_java_method()) {
        buckets[nm->code_size() / bucketSize]++;
      }
    }
  }
  tty->print_cr("Code Cache Entries (total of %d)",total);
  tty->print_cr("-------------------------------------------------");
  tty->print_cr("nmethods: %d",nmethodCount);
  tty->print_cr("\talive: %d",nmethodAlive);
  tty->print_cr("\tnot_entrant: %d",nmethodNotEntrant);
  tty->print_cr("\tzombie: %d",nmethodZombie);
  tty->print_cr("\tunloaded: %d",nmethodUnloaded);
  tty->print_cr("\tjava: %d",nmethodJava);
  tty->print_cr("\tnative: %d",nmethodNative);
  tty->print_cr("runtime_stubs: %d",runtimeStubCount);
  tty->print_cr("adapters: %d",adapterCount);
  tty->print_cr("buffer blobs: %d",bufferBlobCount);
  tty->print_cr("deoptimization_stubs: %d",deoptimizationStubCount);
  tty->print_cr("uncommon_traps: %d",uncommonTrapStubCount);
  tty->print_cr("\nnmethod size distribution (non-zombie java)");
  tty->print_cr("-------------------------------------------------");

  for(int i=0; i<bucketLimit; i++) {
    if(buckets[i] != 0) {
      tty->print("%d - %d bytes",i*bucketSize,(i+1)*bucketSize);
      tty->fill_to(40);
      tty->print_cr("%d",buckets[i]);
    }
  }

  FREE_C_HEAP_ARRAY(int, buckets);
}

void CodeCache::print() {
  CodeBlob_sizes live;
  CodeBlob_sizes dead;

  FOR_ALL_BLOBS(p) {
    if (!p->is_alive()) {
      dead.add(p);
    } else {
      live.add(p);
    }
  }

  tty->print_cr("CodeCache:");

  tty->print_cr("nmethod dependency checking time %f", dependentCheckTime.seconds(),
                dependentCheckTime.seconds() / dependentCheckCount);

  if (!live.is_empty()) {
    live.print("live");
  }
  if (!dead.is_empty()) {
    dead.print("dead");
  }


  if (Verbose) {
     // print the oop_map usage
    int code_size = 0;
    int number_of_blobs = 0;
    int number_of_oop_maps = 0;
    int map_size = 0;
    FOR_ALL_BLOBS(p) {
      if (p->is_alive()) {
        number_of_blobs++;
        code_size += p->instructions_size();
        OopMapSet* set = p->oop_maps();
        if (set != NULL) {
          number_of_oop_maps += set->size();
          map_size   += set->heap_size();
        }
      }
    }
    tty->print_cr("OopMaps");
    tty->print_cr("  #blobs    = %d", number_of_blobs);
    tty->print_cr("  code size = %d", code_size);
    tty->print_cr("  #oop_maps = %d", number_of_oop_maps);
    tty->print_cr("  map size  = %d", map_size);
  }

}

#endif // PRODUCT
