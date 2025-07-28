/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "asm/assembler.inline.hpp"
#include "code/codeCache.hpp"
#include "code/compiledIC.hpp"
#include "code/dependencies.hpp"
#include "code/nativeInst.hpp"
#include "code/nmethod.inline.hpp"
#include "code/scopeDesc.hpp"
#include "compiler/abstractCompiler.hpp"
#include "compiler/compilationLog.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compileLog.hpp"
#include "compiler/compileTask.hpp"
#include "compiler/compilerDirectives.hpp"
#include "compiler/compilerOracle.hpp"
#include "compiler/directivesParser.hpp"
#include "compiler/disassembler.hpp"
#include "compiler/oopMap.inline.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "interpreter/bytecode.inline.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/oop.inline.hpp"
#include "oops/weakHandle.inline.hpp"
#include "prims/jvmtiImpl.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/continuation.hpp"
#include "runtime/atomic.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/flags/flagSetting.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/serviceThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/threadWXSetters.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/events.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/resourceHash.hpp"
#include "utilities/xmlstream.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmciRuntime.hpp"
#endif

#ifdef DTRACE_ENABLED

// Only bother with this argument setup if dtrace is available

#define DTRACE_METHOD_UNLOAD_PROBE(method)                                \
  {                                                                       \
    Method* m = (method);                                                 \
    if (m != nullptr) {                                                   \
      Symbol* klass_name = m->klass_name();                               \
      Symbol* name = m->name();                                           \
      Symbol* signature = m->signature();                                 \
      HOTSPOT_COMPILED_METHOD_UNLOAD(                                     \
        (char *) klass_name->bytes(), klass_name->utf8_length(),          \
        (char *) name->bytes(), name->utf8_length(),                      \
        (char *) signature->bytes(), signature->utf8_length());           \
    }                                                                     \
  }

#else //  ndef DTRACE_ENABLED

#define DTRACE_METHOD_UNLOAD_PROBE(method)

#endif

// Cast from int value to narrow type
#define CHECKED_CAST(result, T, thing)      \
  result = static_cast<T>(thing); \
  guarantee(static_cast<int>(result) == thing, "failed: %d != %d", static_cast<int>(result), thing);

//---------------------------------------------------------------------------------
// NMethod statistics
// They are printed under various flags, including:
//   PrintC1Statistics, PrintOptoStatistics, LogVMOutput, and LogCompilation.
// (In the latter two cases, they like other stats are printed to the log only.)

#ifndef PRODUCT
// These variables are put into one block to reduce relocations
// and make it simpler to print from the debugger.
struct java_nmethod_stats_struct {
  uint nmethod_count;
  uint total_nm_size;
  uint total_immut_size;
  uint total_mut_size;
  uint relocation_size;
  uint consts_size;
  uint insts_size;
  uint stub_size;
  uint oops_size;
  uint metadata_size;
  uint dependencies_size;
  uint nul_chk_table_size;
  uint handler_table_size;
  uint scopes_pcs_size;
  uint scopes_data_size;
#if INCLUDE_JVMCI
  uint speculations_size;
  uint jvmci_data_size;
#endif

  void note_nmethod(nmethod* nm) {
    nmethod_count += 1;
    total_nm_size       += nm->size();
    total_immut_size    += nm->immutable_data_size();
    total_mut_size      += nm->mutable_data_size();
    relocation_size     += nm->relocation_size();
    consts_size         += nm->consts_size();
    insts_size          += nm->insts_size();
    stub_size           += nm->stub_size();
    oops_size           += nm->oops_size();
    metadata_size       += nm->metadata_size();
    scopes_data_size    += nm->scopes_data_size();
    scopes_pcs_size     += nm->scopes_pcs_size();
    dependencies_size   += nm->dependencies_size();
    handler_table_size  += nm->handler_table_size();
    nul_chk_table_size  += nm->nul_chk_table_size();
#if INCLUDE_JVMCI
    speculations_size   += nm->speculations_size();
    jvmci_data_size     += nm->jvmci_data_size();
#endif
  }
  void print_nmethod_stats(const char* name) {
    if (nmethod_count == 0)  return;
    tty->print_cr("Statistics for %u bytecoded nmethods for %s:", nmethod_count, name);
    uint total_size = total_nm_size + total_immut_size + total_mut_size;
    if (total_nm_size != 0) {
      tty->print_cr(" total size      = %u (100%%)", total_size);
      tty->print_cr(" in CodeCache    = %u (%f%%)", total_nm_size, (total_nm_size * 100.0f)/total_size);
    }
    uint header_size = (uint)(nmethod_count * sizeof(nmethod));
    if (nmethod_count != 0) {
      tty->print_cr("   header        = %u (%f%%)", header_size, (header_size * 100.0f)/total_nm_size);
    }
    if (consts_size != 0) {
      tty->print_cr("   constants     = %u (%f%%)", consts_size, (consts_size * 100.0f)/total_nm_size);
    }
    if (insts_size != 0) {
      tty->print_cr("   main code     = %u (%f%%)", insts_size, (insts_size * 100.0f)/total_nm_size);
    }
    if (stub_size != 0) {
      tty->print_cr("   stub code     = %u (%f%%)", stub_size, (stub_size * 100.0f)/total_nm_size);
    }
    if (oops_size != 0) {
      tty->print_cr("   oops          = %u (%f%%)", oops_size, (oops_size * 100.0f)/total_nm_size);
    }
    if (total_mut_size != 0) {
      tty->print_cr(" mutable data    = %u (%f%%)", total_mut_size, (total_mut_size * 100.0f)/total_size);
    }
    if (relocation_size != 0) {
      tty->print_cr("   relocation    = %u (%f%%)", relocation_size, (relocation_size * 100.0f)/total_mut_size);
    }
    if (metadata_size != 0) {
      tty->print_cr("   metadata      = %u (%f%%)", metadata_size, (metadata_size * 100.0f)/total_mut_size);
    }
#if INCLUDE_JVMCI
    if (jvmci_data_size != 0) {
      tty->print_cr("   JVMCI data    = %u (%f%%)", jvmci_data_size, (jvmci_data_size * 100.0f)/total_mut_size);
    }
#endif
    if (total_immut_size != 0) {
      tty->print_cr(" immutable data  = %u (%f%%)", total_immut_size, (total_immut_size * 100.0f)/total_size);
    }
    if (dependencies_size != 0) {
      tty->print_cr("   dependencies  = %u (%f%%)", dependencies_size, (dependencies_size * 100.0f)/total_immut_size);
    }
    if (nul_chk_table_size != 0) {
      tty->print_cr("   nul chk table = %u (%f%%)", nul_chk_table_size, (nul_chk_table_size * 100.0f)/total_immut_size);
    }
    if (handler_table_size != 0) {
      tty->print_cr("   handler table = %u (%f%%)", handler_table_size, (handler_table_size * 100.0f)/total_immut_size);
    }
    if (scopes_pcs_size != 0) {
      tty->print_cr("   scopes pcs    = %u (%f%%)", scopes_pcs_size, (scopes_pcs_size * 100.0f)/total_immut_size);
    }
    if (scopes_data_size != 0) {
      tty->print_cr("   scopes data   = %u (%f%%)", scopes_data_size, (scopes_data_size * 100.0f)/total_immut_size);
    }
#if INCLUDE_JVMCI
    if (speculations_size != 0) {
      tty->print_cr("   speculations  = %u (%f%%)", speculations_size, (speculations_size * 100.0f)/total_immut_size);
    }
#endif
  }
};

struct native_nmethod_stats_struct {
  uint native_nmethod_count;
  uint native_total_size;
  uint native_relocation_size;
  uint native_insts_size;
  uint native_oops_size;
  uint native_metadata_size;
  void note_native_nmethod(nmethod* nm) {
    native_nmethod_count += 1;
    native_total_size       += nm->size();
    native_relocation_size  += nm->relocation_size();
    native_insts_size       += nm->insts_size();
    native_oops_size        += nm->oops_size();
    native_metadata_size    += nm->metadata_size();
  }
  void print_native_nmethod_stats() {
    if (native_nmethod_count == 0)  return;
    tty->print_cr("Statistics for %u native nmethods:", native_nmethod_count);
    if (native_total_size != 0)       tty->print_cr(" N. total size  = %u", native_total_size);
    if (native_relocation_size != 0)  tty->print_cr(" N. relocation  = %u", native_relocation_size);
    if (native_insts_size != 0)       tty->print_cr(" N. main code   = %u", native_insts_size);
    if (native_oops_size != 0)        tty->print_cr(" N. oops        = %u", native_oops_size);
    if (native_metadata_size != 0)    tty->print_cr(" N. metadata    = %u", native_metadata_size);
  }
};

struct pc_nmethod_stats_struct {
  uint pc_desc_init;     // number of initialization of cache (= number of caches)
  uint pc_desc_queries;  // queries to nmethod::find_pc_desc
  uint pc_desc_approx;   // number of those which have approximate true
  uint pc_desc_repeats;  // number of _pc_descs[0] hits
  uint pc_desc_hits;     // number of LRU cache hits
  uint pc_desc_tests;    // total number of PcDesc examinations
  uint pc_desc_searches; // total number of quasi-binary search steps
  uint pc_desc_adds;     // number of LUR cache insertions

  void print_pc_stats() {
    tty->print_cr("PcDesc Statistics:  %u queries, %.2f comparisons per query",
                  pc_desc_queries,
                  (double)(pc_desc_tests + pc_desc_searches)
                  / pc_desc_queries);
    tty->print_cr("  caches=%d queries=%u/%u, hits=%u+%u, tests=%u+%u, adds=%u",
                  pc_desc_init,
                  pc_desc_queries, pc_desc_approx,
                  pc_desc_repeats, pc_desc_hits,
                  pc_desc_tests, pc_desc_searches, pc_desc_adds);
  }
};

#ifdef COMPILER1
static java_nmethod_stats_struct c1_java_nmethod_stats;
#endif
#ifdef COMPILER2
static java_nmethod_stats_struct c2_java_nmethod_stats;
#endif
#if INCLUDE_JVMCI
static java_nmethod_stats_struct jvmci_java_nmethod_stats;
#endif
static java_nmethod_stats_struct unknown_java_nmethod_stats;

static native_nmethod_stats_struct native_nmethod_stats;
static pc_nmethod_stats_struct pc_nmethod_stats;

static void note_java_nmethod(nmethod* nm) {
#ifdef COMPILER1
  if (nm->is_compiled_by_c1()) {
    c1_java_nmethod_stats.note_nmethod(nm);
  } else
#endif
#ifdef COMPILER2
  if (nm->is_compiled_by_c2()) {
    c2_java_nmethod_stats.note_nmethod(nm);
  } else
#endif
#if INCLUDE_JVMCI
  if (nm->is_compiled_by_jvmci()) {
    jvmci_java_nmethod_stats.note_nmethod(nm);
  } else
#endif
  {
    unknown_java_nmethod_stats.note_nmethod(nm);
  }
}
#endif // !PRODUCT

//---------------------------------------------------------------------------------


ExceptionCache::ExceptionCache(Handle exception, address pc, address handler) {
  assert(pc != nullptr, "Must be non null");
  assert(exception.not_null(), "Must be non null");
  assert(handler != nullptr, "Must be non null");

  _count = 0;
  _exception_type = exception->klass();
  _next = nullptr;
  _purge_list_next = nullptr;

  add_address_and_handler(pc,handler);
}


address ExceptionCache::match(Handle exception, address pc) {
  assert(pc != nullptr,"Must be non null");
  assert(exception.not_null(),"Must be non null");
  if (exception->klass() == exception_type()) {
    return (test_address(pc));
  }

  return nullptr;
}


bool ExceptionCache::match_exception_with_space(Handle exception) {
  assert(exception.not_null(),"Must be non null");
  if (exception->klass() == exception_type() && count() < cache_size) {
    return true;
  }
  return false;
}


address ExceptionCache::test_address(address addr) {
  int limit = count();
  for (int i = 0; i < limit; i++) {
    if (pc_at(i) == addr) {
      return handler_at(i);
    }
  }
  return nullptr;
}


bool ExceptionCache::add_address_and_handler(address addr, address handler) {
  if (test_address(addr) == handler) return true;

  int index = count();
  if (index < cache_size) {
    set_pc_at(index, addr);
    set_handler_at(index, handler);
    increment_count();
    return true;
  }
  return false;
}

ExceptionCache* ExceptionCache::next() {
  return Atomic::load(&_next);
}

void ExceptionCache::set_next(ExceptionCache *ec) {
  Atomic::store(&_next, ec);
}

//-----------------------------------------------------------------------------


// Helper used by both find_pc_desc methods.
static inline bool match_desc(PcDesc* pc, int pc_offset, bool approximate) {
  NOT_PRODUCT(++pc_nmethod_stats.pc_desc_tests);
  if (!approximate) {
    return pc->pc_offset() == pc_offset;
  } else {
    // Do not look before the sentinel
    assert(pc_offset > PcDesc::lower_offset_limit, "illegal pc_offset");
    return pc_offset <= pc->pc_offset() && (pc-1)->pc_offset() < pc_offset;
  }
}

void PcDescCache::init_to(PcDesc* initial_pc_desc) {
  NOT_PRODUCT(++pc_nmethod_stats.pc_desc_init);
  // initialize the cache by filling it with benign (non-null) values
  assert(initial_pc_desc != nullptr && initial_pc_desc->pc_offset() == PcDesc::lower_offset_limit,
         "must start with a sentinel");
  for (int i = 0; i < cache_size; i++) {
    _pc_descs[i] = initial_pc_desc;
  }
}

PcDesc* PcDescCache::find_pc_desc(int pc_offset, bool approximate) {
  // Note: one might think that caching the most recently
  // read value separately would be a win, but one would be
  // wrong.  When many threads are updating it, the cache
  // line it's in would bounce between caches, negating
  // any benefit.

  // In order to prevent race conditions do not load cache elements
  // repeatedly, but use a local copy:
  PcDesc* res;

  // Step one:  Check the most recently added value.
  res = _pc_descs[0];
  assert(res != nullptr, "PcDesc cache should be initialized already");

  // Approximate only here since PcDescContainer::find_pc_desc() checked for exact case.
  if (approximate && match_desc(res, pc_offset, approximate)) {
    NOT_PRODUCT(++pc_nmethod_stats.pc_desc_repeats);
    return res;
  }

  // Step two:  Check the rest of the LRU cache.
  for (int i = 1; i < cache_size; ++i) {
    res = _pc_descs[i];
    if (res->pc_offset() < 0) break;  // optimization: skip empty cache
    if (match_desc(res, pc_offset, approximate)) {
      NOT_PRODUCT(++pc_nmethod_stats.pc_desc_hits);
      return res;
    }
  }

  // Report failure.
  return nullptr;
}

void PcDescCache::add_pc_desc(PcDesc* pc_desc) {
  NOT_PRODUCT(++pc_nmethod_stats.pc_desc_adds);
  // Update the LRU cache by shifting pc_desc forward.
  for (int i = 0; i < cache_size; i++)  {
    PcDesc* next = _pc_descs[i];
    _pc_descs[i] = pc_desc;
    pc_desc = next;
  }
}

// adjust pcs_size so that it is a multiple of both oopSize and
// sizeof(PcDesc) (assumes that if sizeof(PcDesc) is not a multiple
// of oopSize, then 2*sizeof(PcDesc) is)
static int adjust_pcs_size(int pcs_size) {
  int nsize = align_up(pcs_size,   oopSize);
  if ((nsize % sizeof(PcDesc)) != 0) {
    nsize = pcs_size + sizeof(PcDesc);
  }
  assert((nsize % oopSize) == 0, "correct alignment");
  return nsize;
}

bool nmethod::is_method_handle_return(address return_pc) {
  if (!has_method_handle_invokes())  return false;
  PcDesc* pd = pc_desc_at(return_pc);
  if (pd == nullptr)
    return false;
  return pd->is_method_handle_invoke();
}

// Returns a string version of the method state.
const char* nmethod::state() const {
  int state = get_state();
  switch (state) {
  case not_installed:
    return "not installed";
  case in_use:
    return "in use";
  case not_entrant:
    return "not_entrant";
  default:
    fatal("unexpected method state: %d", state);
    return nullptr;
  }
}

void nmethod::set_deoptimized_done() {
  ConditionalMutexLocker ml(NMethodState_lock, !NMethodState_lock->owned_by_self(), Mutex::_no_safepoint_check_flag);
  if (_deoptimization_status != deoptimize_done) { // can't go backwards
    Atomic::store(&_deoptimization_status, deoptimize_done);
  }
}

ExceptionCache* nmethod::exception_cache_acquire() const {
  return Atomic::load_acquire(&_exception_cache);
}

void nmethod::add_exception_cache_entry(ExceptionCache* new_entry) {
  assert(ExceptionCache_lock->owned_by_self(),"Must hold the ExceptionCache_lock");
  assert(new_entry != nullptr,"Must be non null");
  assert(new_entry->next() == nullptr, "Must be null");

  for (;;) {
    ExceptionCache *ec = exception_cache();
    if (ec != nullptr) {
      Klass* ex_klass = ec->exception_type();
      if (!ex_klass->is_loader_alive()) {
        // We must guarantee that entries are not inserted with new next pointer
        // edges to ExceptionCache entries with dead klasses, due to bad interactions
        // with concurrent ExceptionCache cleanup. Therefore, the inserts roll
        // the head pointer forward to the first live ExceptionCache, so that the new
        // next pointers always point at live ExceptionCaches, that are not removed due
        // to concurrent ExceptionCache cleanup.
        ExceptionCache* next = ec->next();
        if (Atomic::cmpxchg(&_exception_cache, ec, next) == ec) {
          CodeCache::release_exception_cache(ec);
        }
        continue;
      }
      ec = exception_cache();
      if (ec != nullptr) {
        new_entry->set_next(ec);
      }
    }
    if (Atomic::cmpxchg(&_exception_cache, ec, new_entry) == ec) {
      return;
    }
  }
}

void nmethod::clean_exception_cache() {
  // For each nmethod, only a single thread may call this cleanup function
  // at the same time, whether called in STW cleanup or concurrent cleanup.
  // Note that if the GC is processing exception cache cleaning in a concurrent phase,
  // then a single writer may contend with cleaning up the head pointer to the
  // first ExceptionCache node that has a Klass* that is alive. That is fine,
  // as long as there is no concurrent cleanup of next pointers from concurrent writers.
  // And the concurrent writers do not clean up next pointers, only the head.
  // Also note that concurrent readers will walk through Klass* pointers that are not
  // alive. That does not cause ABA problems, because Klass* is deleted after
  // a handshake with all threads, after all stale ExceptionCaches have been
  // unlinked. That is also when the CodeCache::exception_cache_purge_list()
  // is deleted, with all ExceptionCache entries that were cleaned concurrently.
  // That similarly implies that CAS operations on ExceptionCache entries do not
  // suffer from ABA problems as unlinking and deletion is separated by a global
  // handshake operation.
  ExceptionCache* prev = nullptr;
  ExceptionCache* curr = exception_cache_acquire();

  while (curr != nullptr) {
    ExceptionCache* next = curr->next();

    if (!curr->exception_type()->is_loader_alive()) {
      if (prev == nullptr) {
        // Try to clean head; this is contended by concurrent inserts, that
        // both lazily clean the head, and insert entries at the head. If
        // the CAS fails, the operation is restarted.
        if (Atomic::cmpxchg(&_exception_cache, curr, next) != curr) {
          prev = nullptr;
          curr = exception_cache_acquire();
          continue;
        }
      } else {
        // It is impossible to during cleanup connect the next pointer to
        // an ExceptionCache that has not been published before a safepoint
        // prior to the cleanup. Therefore, release is not required.
        prev->set_next(next);
      }
      // prev stays the same.

      CodeCache::release_exception_cache(curr);
    } else {
      prev = curr;
    }

    curr = next;
  }
}

// public method for accessing the exception cache
// These are the public access methods.
address nmethod::handler_for_exception_and_pc(Handle exception, address pc) {
  // We never grab a lock to read the exception cache, so we may
  // have false negatives. This is okay, as it can only happen during
  // the first few exception lookups for a given nmethod.
  ExceptionCache* ec = exception_cache_acquire();
  while (ec != nullptr) {
    address ret_val;
    if ((ret_val = ec->match(exception,pc)) != nullptr) {
      return ret_val;
    }
    ec = ec->next();
  }
  return nullptr;
}

void nmethod::add_handler_for_exception_and_pc(Handle exception, address pc, address handler) {
  // There are potential race conditions during exception cache updates, so we
  // must own the ExceptionCache_lock before doing ANY modifications. Because
  // we don't lock during reads, it is possible to have several threads attempt
  // to update the cache with the same data. We need to check for already inserted
  // copies of the current data before adding it.

  MutexLocker ml(ExceptionCache_lock);
  ExceptionCache* target_entry = exception_cache_entry_for_exception(exception);

  if (target_entry == nullptr || !target_entry->add_address_and_handler(pc,handler)) {
    target_entry = new ExceptionCache(exception,pc,handler);
    add_exception_cache_entry(target_entry);
  }
}

// private method for handling exception cache
// These methods are private, and used to manipulate the exception cache
// directly.
ExceptionCache* nmethod::exception_cache_entry_for_exception(Handle exception) {
  ExceptionCache* ec = exception_cache_acquire();
  while (ec != nullptr) {
    if (ec->match_exception_with_space(exception)) {
      return ec;
    }
    ec = ec->next();
  }
  return nullptr;
}

bool nmethod::is_at_poll_return(address pc) {
  RelocIterator iter(this, pc, pc+1);
  while (iter.next()) {
    if (iter.type() == relocInfo::poll_return_type)
      return true;
  }
  return false;
}


bool nmethod::is_at_poll_or_poll_return(address pc) {
  RelocIterator iter(this, pc, pc+1);
  while (iter.next()) {
    relocInfo::relocType t = iter.type();
    if (t == relocInfo::poll_return_type || t == relocInfo::poll_type)
      return true;
  }
  return false;
}

void nmethod::verify_oop_relocations() {
  // Ensure sure that the code matches the current oop values
  RelocIterator iter(this, nullptr, nullptr);
  while (iter.next()) {
    if (iter.type() == relocInfo::oop_type) {
      oop_Relocation* reloc = iter.oop_reloc();
      if (!reloc->oop_is_immediate()) {
        reloc->verify_oop_relocation();
      }
    }
  }
}


ScopeDesc* nmethod::scope_desc_at(address pc) {
  PcDesc* pd = pc_desc_at(pc);
  guarantee(pd != nullptr, "scope must be present");
  return new ScopeDesc(this, pd);
}

ScopeDesc* nmethod::scope_desc_near(address pc) {
  PcDesc* pd = pc_desc_near(pc);
  guarantee(pd != nullptr, "scope must be present");
  return new ScopeDesc(this, pd);
}

address nmethod::oops_reloc_begin() const {
  // If the method is not entrant then a JMP is plastered over the
  // first few bytes.  If an oop in the old code was there, that oop
  // should not get GC'd.  Skip the first few bytes of oops on
  // not-entrant methods.
  if (frame_complete_offset() != CodeOffsets::frame_never_safe &&
      code_begin() + frame_complete_offset() >
      verified_entry_point() + NativeJump::instruction_size)
  {
    // If we have a frame_complete_offset after the native jump, then there
    // is no point trying to look for oops before that. This is a requirement
    // for being allowed to scan oops concurrently.
    return code_begin() + frame_complete_offset();
  }

  address low_boundary = verified_entry_point();
  return low_boundary;
}

// Method that knows how to preserve outgoing arguments at call. This method must be
// called with a frame corresponding to a Java invoke
void nmethod::preserve_callee_argument_oops(frame fr, const RegisterMap *reg_map, OopClosure* f) {
  if (method() == nullptr) {
    return;
  }

  // handle the case of an anchor explicitly set in continuation code that doesn't have a callee
  JavaThread* thread = reg_map->thread();
  if ((thread->has_last_Java_frame() && fr.sp() == thread->last_Java_sp())
      JVMTI_ONLY(|| (method()->is_continuation_enter_intrinsic() && thread->on_monitor_waited_event()))) {
    return;
  }

  if (!method()->is_native()) {
    address pc = fr.pc();
    bool has_receiver, has_appendix;
    Symbol* signature;

    // The method attached by JIT-compilers should be used, if present.
    // Bytecode can be inaccurate in such case.
    Method* callee = attached_method_before_pc(pc);
    if (callee != nullptr) {
      has_receiver = !(callee->access_flags().is_static());
      has_appendix = false;
      signature    = callee->signature();
    } else {
      SimpleScopeDesc ssd(this, pc);

      Bytecode_invoke call(methodHandle(Thread::current(), ssd.method()), ssd.bci());
      has_receiver = call.has_receiver();
      has_appendix = call.has_appendix();
      signature    = call.signature();
    }

    fr.oops_compiled_arguments_do(signature, has_receiver, has_appendix, reg_map, f);
  } else if (method()->is_continuation_enter_intrinsic()) {
    // This method only calls Continuation.enter()
    Symbol* signature = vmSymbols::continuationEnter_signature();
    fr.oops_compiled_arguments_do(signature, false, false, reg_map, f);
  }
}

Method* nmethod::attached_method(address call_instr) {
  assert(code_contains(call_instr), "not part of the nmethod");
  RelocIterator iter(this, call_instr, call_instr + 1);
  while (iter.next()) {
    if (iter.addr() == call_instr) {
      switch(iter.type()) {
        case relocInfo::static_call_type:      return iter.static_call_reloc()->method_value();
        case relocInfo::opt_virtual_call_type: return iter.opt_virtual_call_reloc()->method_value();
        case relocInfo::virtual_call_type:     return iter.virtual_call_reloc()->method_value();
        default:                               break;
      }
    }
  }
  return nullptr; // not found
}

Method* nmethod::attached_method_before_pc(address pc) {
  if (NativeCall::is_call_before(pc)) {
    NativeCall* ncall = nativeCall_before(pc);
    return attached_method(ncall->instruction_address());
  }
  return nullptr; // not a call
}

void nmethod::clear_inline_caches() {
  assert(SafepointSynchronize::is_at_safepoint(), "clearing of IC's only allowed at safepoint");
  RelocIterator iter(this);
  while (iter.next()) {
    iter.reloc()->clear_inline_cache();
  }
}

#ifdef ASSERT
// Check class_loader is alive for this bit of metadata.
class CheckClass : public MetadataClosure {
  void do_metadata(Metadata* md) {
    Klass* klass = nullptr;
    if (md->is_klass()) {
      klass = ((Klass*)md);
    } else if (md->is_method()) {
      klass = ((Method*)md)->method_holder();
    } else if (md->is_methodData()) {
      klass = ((MethodData*)md)->method()->method_holder();
    } else if (md->is_methodCounters()) {
      klass = ((MethodCounters*)md)->method()->method_holder();
    } else {
      md->print();
      ShouldNotReachHere();
    }
    assert(klass->is_loader_alive(), "must be alive");
  }
};
#endif // ASSERT


static void clean_ic_if_metadata_is_dead(CompiledIC *ic) {
  ic->clean_metadata();
}

// Clean references to unloaded nmethods at addr from this one, which is not unloaded.
template <typename CallsiteT>
static void clean_if_nmethod_is_unloaded(CallsiteT* callsite, nmethod* from,
                                         bool clean_all) {
  CodeBlob* cb = CodeCache::find_blob(callsite->destination());
  if (!cb->is_nmethod()) {
    return;
  }
  nmethod* nm = cb->as_nmethod();
  if (clean_all || !nm->is_in_use() || nm->is_unloading() || nm->method()->code() != nm) {
    callsite->set_to_clean();
  }
}

// Cleans caches in nmethods that point to either classes that are unloaded
// or nmethods that are unloaded.
//
// Can be called either in parallel by G1 currently or after all
// nmethods are unloaded.  Return postponed=true in the parallel case for
// inline caches found that point to nmethods that are not yet visited during
// the do_unloading walk.
void nmethod::unload_nmethod_caches(bool unloading_occurred) {
  ResourceMark rm;

  // Exception cache only needs to be called if unloading occurred
  if (unloading_occurred) {
    clean_exception_cache();
  }

  cleanup_inline_caches_impl(unloading_occurred, false);

#ifdef ASSERT
  // Check that the metadata embedded in the nmethod is alive
  CheckClass check_class;
  metadata_do(&check_class);
#endif
}

void nmethod::run_nmethod_entry_barrier() {
  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  if (bs_nm != nullptr) {
    // We want to keep an invariant that nmethods found through iterations of a Thread's
    // nmethods found in safepoints have gone through an entry barrier and are not armed.
    // By calling this nmethod entry barrier, it plays along and acts
    // like any other nmethod found on the stack of a thread (fewer surprises).
    nmethod* nm = this;
    bool alive = bs_nm->nmethod_entry_barrier(nm);
    assert(alive, "should be alive");
  }
}

// Only called by whitebox test
void nmethod::cleanup_inline_caches_whitebox() {
  assert_locked_or_safepoint(CodeCache_lock);
  CompiledICLocker ic_locker(this);
  cleanup_inline_caches_impl(false /* unloading_occurred */, true /* clean_all */);
}

address* nmethod::orig_pc_addr(const frame* fr) {
  return (address*) ((address)fr->unextended_sp() + orig_pc_offset());
}

// Called to clean up after class unloading for live nmethods
void nmethod::cleanup_inline_caches_impl(bool unloading_occurred, bool clean_all) {
  assert(CompiledICLocker::is_safe(this), "mt unsafe call");
  ResourceMark rm;

  // Find all calls in an nmethod and clear the ones that point to bad nmethods.
  RelocIterator iter(this, oops_reloc_begin());
  bool is_in_static_stub = false;
  while(iter.next()) {

    switch (iter.type()) {

    case relocInfo::virtual_call_type:
      if (unloading_occurred) {
        // If class unloading occurred we first clear ICs where the cached metadata
        // is referring to an unloaded klass or method.
        clean_ic_if_metadata_is_dead(CompiledIC_at(&iter));
      }

      clean_if_nmethod_is_unloaded(CompiledIC_at(&iter), this, clean_all);
      break;

    case relocInfo::opt_virtual_call_type:
    case relocInfo::static_call_type:
      clean_if_nmethod_is_unloaded(CompiledDirectCall::at(iter.reloc()), this, clean_all);
      break;

    case relocInfo::static_stub_type: {
      is_in_static_stub = true;
      break;
    }

    case relocInfo::metadata_type: {
      // Only the metadata relocations contained in static/opt virtual call stubs
      // contains the Method* passed to c2i adapters. It is the only metadata
      // relocation that needs to be walked, as it is the one metadata relocation
      // that violates the invariant that all metadata relocations have an oop
      // in the compiled method (due to deferred resolution and code patching).

      // This causes dead metadata to remain in compiled methods that are not
      // unloading. Unless these slippery metadata relocations of the static
      // stubs are at least cleared, subsequent class redefinition operations
      // will access potentially free memory, and JavaThread execution
      // concurrent to class unloading may call c2i adapters with dead methods.
      if (!is_in_static_stub) {
        // The first metadata relocation after a static stub relocation is the
        // metadata relocation of the static stub used to pass the Method* to
        // c2i adapters.
        continue;
      }
      is_in_static_stub = false;
      if (is_unloading()) {
        // If the nmethod itself is dying, then it may point at dead metadata.
        // Nobody should follow that metadata; it is strictly unsafe.
        continue;
      }
      metadata_Relocation* r = iter.metadata_reloc();
      Metadata* md = r->metadata_value();
      if (md != nullptr && md->is_method()) {
        Method* method = static_cast<Method*>(md);
        if (!method->method_holder()->is_loader_alive()) {
          Atomic::store(r->metadata_addr(), (Method*)nullptr);

          if (!r->metadata_is_immediate()) {
            r->fix_metadata_relocation();
          }
        }
      }
      break;
    }

    default:
      break;
    }
  }
}

address nmethod::continuation_for_implicit_exception(address pc, bool for_div0_check) {
  // Exception happened outside inline-cache check code => we are inside
  // an active nmethod => use cpc to determine a return address
  int exception_offset = int(pc - code_begin());
  int cont_offset = ImplicitExceptionTable(this).continuation_offset( exception_offset );
#ifdef ASSERT
  if (cont_offset == 0) {
    Thread* thread = Thread::current();
    ResourceMark rm(thread);
    CodeBlob* cb = CodeCache::find_blob(pc);
    assert(cb != nullptr && cb == this, "");

    // Keep tty output consistent. To avoid ttyLocker, we buffer in stream, and print all at once.
    stringStream ss;
    ss.print_cr("implicit exception happened at " INTPTR_FORMAT, p2i(pc));
    print_on(&ss);
    method()->print_codes_on(&ss);
    print_code_on(&ss);
    print_pcs_on(&ss);
    tty->print("%s", ss.as_string()); // print all at once
  }
#endif
  if (cont_offset == 0) {
    // Let the normal error handling report the exception
    return nullptr;
  }
  if (cont_offset == exception_offset) {
#if INCLUDE_JVMCI
    Deoptimization::DeoptReason deopt_reason = for_div0_check ? Deoptimization::Reason_div0_check : Deoptimization::Reason_null_check;
    JavaThread *thread = JavaThread::current();
    thread->set_jvmci_implicit_exception_pc(pc);
    thread->set_pending_deoptimization(Deoptimization::make_trap_request(deopt_reason,
                                                                         Deoptimization::Action_reinterpret));
    return (SharedRuntime::deopt_blob()->implicit_exception_uncommon_trap());
#else
    ShouldNotReachHere();
#endif
  }
  return code_begin() + cont_offset;
}

class HasEvolDependency : public MetadataClosure {
  bool _has_evol_dependency;
 public:
  HasEvolDependency() : _has_evol_dependency(false) {}
  void do_metadata(Metadata* md) {
    if (md->is_method()) {
      Method* method = (Method*)md;
      if (method->is_old()) {
        _has_evol_dependency = true;
      }
    }
  }
  bool has_evol_dependency() const { return _has_evol_dependency; }
};

bool nmethod::has_evol_metadata() {
  // Check the metadata in relocIter and CompiledIC and also deoptimize
  // any nmethod that has reference to old methods.
  HasEvolDependency check_evol;
  metadata_do(&check_evol);
  if (check_evol.has_evol_dependency() && log_is_enabled(Debug, redefine, class, nmethod)) {
    ResourceMark rm;
    log_debug(redefine, class, nmethod)
            ("Found evol dependency of nmethod %s.%s(%s) compile_id=%d on in nmethod metadata",
             _method->method_holder()->external_name(),
             _method->name()->as_C_string(),
             _method->signature()->as_C_string(),
             compile_id());
  }
  return check_evol.has_evol_dependency();
}

int nmethod::total_size() const {
  return
    consts_size()        +
    insts_size()         +
    stub_size()          +
    scopes_data_size()   +
    scopes_pcs_size()    +
    handler_table_size() +
    nul_chk_table_size();
}

const char* nmethod::compile_kind() const {
  if (is_osr_method())     return "osr";
  if (method() != nullptr && is_native_method()) {
    if (method()->is_continuation_native_intrinsic()) {
      return "cnt";
    }
    return "c2n";
  }
  return nullptr;
}

const char* nmethod::compiler_name() const {
  return compilertype2name(_compiler_type);
}

#ifdef ASSERT
class CheckForOopsClosure : public OopClosure {
  bool _found_oop = false;
 public:
  virtual void do_oop(oop* o) { _found_oop = true; }
  virtual void do_oop(narrowOop* o) { _found_oop = true; }
  bool found_oop() { return _found_oop; }
};
class CheckForMetadataClosure : public MetadataClosure {
  bool _found_metadata = false;
  Metadata* _ignore = nullptr;
 public:
  CheckForMetadataClosure(Metadata* ignore) : _ignore(ignore) {}
  virtual void do_metadata(Metadata* md) { if (md != _ignore) _found_metadata = true; }
  bool found_metadata() { return _found_metadata; }
};

static void assert_no_oops_or_metadata(nmethod* nm) {
  if (nm == nullptr) return;
  assert(nm->oop_maps() == nullptr, "expectation");

  CheckForOopsClosure cfo;
  nm->oops_do(&cfo);
  assert(!cfo.found_oop(), "no oops allowed");

  // We allow an exception for the own Method, but require its class to be permanent.
  Method* own_method = nm->method();
  CheckForMetadataClosure cfm(/* ignore reference to own Method */ own_method);
  nm->metadata_do(&cfm);
  assert(!cfm.found_metadata(), "no metadata allowed");

  assert(own_method->method_holder()->class_loader_data()->is_permanent_class_loader_data(),
         "Method's class needs to be permanent");
}
#endif

static int required_mutable_data_size(CodeBuffer* code_buffer,
                                      int jvmci_data_size = 0) {
  return align_up(code_buffer->total_relocation_size(), oopSize) +
         align_up(code_buffer->total_metadata_size(), oopSize) +
         align_up(jvmci_data_size, oopSize);
}

nmethod* nmethod::new_native_nmethod(const methodHandle& method,
  int compile_id,
  CodeBuffer *code_buffer,
  int vep_offset,
  int frame_complete,
  int frame_size,
  ByteSize basic_lock_owner_sp_offset,
  ByteSize basic_lock_sp_offset,
  OopMapSet* oop_maps,
  int exception_handler) {
  code_buffer->finalize_oop_references(method);
  // create nmethod
  nmethod* nm = nullptr;
  int native_nmethod_size = CodeBlob::allocation_size(code_buffer, sizeof(nmethod));
  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

    CodeOffsets offsets;
    offsets.set_value(CodeOffsets::Verified_Entry, vep_offset);
    offsets.set_value(CodeOffsets::Frame_Complete, frame_complete);
    if (exception_handler != -1) {
      offsets.set_value(CodeOffsets::Exceptions, exception_handler);
    }

    int mutable_data_size = required_mutable_data_size(code_buffer);

    // MH intrinsics are dispatch stubs which are compatible with NonNMethod space.
    // IsUnloadingBehaviour::is_unloading needs to handle them separately.
    bool allow_NonNMethod_space = method->can_be_allocated_in_NonNMethod_space();
    nm = new (native_nmethod_size, allow_NonNMethod_space)
    nmethod(method(), compiler_none, native_nmethod_size,
            compile_id, &offsets,
            code_buffer, frame_size,
            basic_lock_owner_sp_offset,
            basic_lock_sp_offset,
            oop_maps, mutable_data_size);
    DEBUG_ONLY( if (allow_NonNMethod_space) assert_no_oops_or_metadata(nm); )
    NOT_PRODUCT(if (nm != nullptr) native_nmethod_stats.note_native_nmethod(nm));
  }

  if (nm != nullptr) {
    // verify nmethod
    DEBUG_ONLY(nm->verify();) // might block

    nm->log_new_nmethod();
  }
  return nm;
}

nmethod* nmethod::new_nmethod(const methodHandle& method,
  int compile_id,
  int entry_bci,
  CodeOffsets* offsets,
  int orig_pc_offset,
  DebugInformationRecorder* debug_info,
  Dependencies* dependencies,
  CodeBuffer* code_buffer, int frame_size,
  OopMapSet* oop_maps,
  ExceptionHandlerTable* handler_table,
  ImplicitExceptionTable* nul_chk_table,
  AbstractCompiler* compiler,
  CompLevel comp_level
#if INCLUDE_JVMCI
  , char* speculations,
  int speculations_len,
  JVMCINMethodData* jvmci_data
#endif
)
{
  assert(debug_info->oop_recorder() == code_buffer->oop_recorder(), "shared OR");
  code_buffer->finalize_oop_references(method);
  // create nmethod
  nmethod* nm = nullptr;
  int nmethod_size = CodeBlob::allocation_size(code_buffer, sizeof(nmethod));

  int immutable_data_size =
      adjust_pcs_size(debug_info->pcs_size())
    + align_up((int)dependencies->size_in_bytes(), oopSize)
    + align_up(handler_table->size_in_bytes()    , oopSize)
    + align_up(nul_chk_table->size_in_bytes()    , oopSize)
#if INCLUDE_JVMCI
    + align_up(speculations_len                  , oopSize)
#endif
    + align_up(debug_info->data_size()           , oopSize);

  // First, allocate space for immutable data in C heap.
  address immutable_data = nullptr;
  if (immutable_data_size > 0) {
    immutable_data = (address)os::malloc(immutable_data_size, mtCode);
    if (immutable_data == nullptr) {
      vm_exit_out_of_memory(immutable_data_size, OOM_MALLOC_ERROR, "nmethod: no space for immutable data");
      return nullptr;
    }
  }

  int mutable_data_size = required_mutable_data_size(code_buffer
    JVMCI_ONLY(COMMA (compiler->is_jvmci() ? jvmci_data->size() : 0)));

  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);

    nm = new (nmethod_size, comp_level)
    nmethod(method(), compiler->type(), nmethod_size, immutable_data_size, mutable_data_size,
            compile_id, entry_bci, immutable_data, offsets, orig_pc_offset,
            debug_info, dependencies, code_buffer, frame_size, oop_maps,
            handler_table, nul_chk_table, compiler, comp_level
#if INCLUDE_JVMCI
            , speculations,
            speculations_len,
            jvmci_data
#endif
            );

    if (nm != nullptr) {
      // To make dependency checking during class loading fast, record
      // the nmethod dependencies in the classes it is dependent on.
      // This allows the dependency checking code to simply walk the
      // class hierarchy above the loaded class, checking only nmethods
      // which are dependent on those classes.  The slow way is to
      // check every nmethod for dependencies which makes it linear in
      // the number of methods compiled.  For applications with a lot
      // classes the slow way is too slow.
      for (Dependencies::DepStream deps(nm); deps.next(); ) {
        if (deps.type() == Dependencies::call_site_target_value) {
          // CallSite dependencies are managed on per-CallSite instance basis.
          oop call_site = deps.argument_oop(0);
          MethodHandles::add_dependent_nmethod(call_site, nm);
        } else {
          InstanceKlass* ik = deps.context_type();
          if (ik == nullptr) {
            continue;  // ignore things like evol_method
          }
          // record this nmethod as dependent on this klass
          ik->add_dependent_nmethod(nm);
        }
      }
      NOT_PRODUCT(if (nm != nullptr)  note_java_nmethod(nm));
    }
  }
  // Do verification and logging outside CodeCache_lock.
  if (nm != nullptr) {
    // Safepoints in nmethod::verify aren't allowed because nm hasn't been installed yet.
    DEBUG_ONLY(nm->verify();)
    nm->log_new_nmethod();
  }
  return nm;
}

// Fill in default values for various fields
void nmethod::init_defaults(CodeBuffer *code_buffer, CodeOffsets* offsets) {
  // avoid uninitialized fields, even for short time periods
  _exception_cache            = nullptr;
  _gc_data                    = nullptr;
  _oops_do_mark_link          = nullptr;
  _compiled_ic_data           = nullptr;

  _is_unloading_state         = 0;
  _state                      = not_installed;

  _has_unsafe_access          = 0;
  _has_method_handle_invokes  = 0;
  _has_wide_vectors           = 0;
  _has_monitors               = 0;
  _has_scoped_access          = 0;
  _has_flushed_dependencies   = 0;
  _is_unlinked                = 0;
  _load_reported              = 0; // jvmti state

  _deoptimization_status      = not_marked;

  // SECT_CONSTS is first in code buffer so the offset should be 0.
  int consts_offset = code_buffer->total_offset_of(code_buffer->consts());
  assert(consts_offset == 0, "const_offset: %d", consts_offset);

  _stub_offset = content_offset() + code_buffer->total_offset_of(code_buffer->stubs());

  CHECKED_CAST(_entry_offset,              uint16_t, (offsets->value(CodeOffsets::Entry)));
  CHECKED_CAST(_verified_entry_offset,     uint16_t, (offsets->value(CodeOffsets::Verified_Entry)));

  _skipped_instructions_size = code_buffer->total_skipped_instructions_size();
}

// Post initialization
void nmethod::post_init() {
  clear_unloading_state();

  finalize_relocations();

  Universe::heap()->register_nmethod(this);
  DEBUG_ONLY(Universe::heap()->verify_nmethod(this));

  CodeCache::commit(this);
}

// For native wrappers
nmethod::nmethod(
  Method* method,
  CompilerType type,
  int nmethod_size,
  int compile_id,
  CodeOffsets* offsets,
  CodeBuffer* code_buffer,
  int frame_size,
  ByteSize basic_lock_owner_sp_offset,
  ByteSize basic_lock_sp_offset,
  OopMapSet* oop_maps,
  int mutable_data_size)
  : CodeBlob("native nmethod", CodeBlobKind::Nmethod, code_buffer, nmethod_size, sizeof(nmethod),
             offsets->value(CodeOffsets::Frame_Complete), frame_size, oop_maps, false, mutable_data_size),
  _deoptimization_generation(0),
  _gc_epoch(CodeCache::gc_epoch()),
  _method(method),
  _native_receiver_sp_offset(basic_lock_owner_sp_offset),
  _native_basic_lock_sp_offset(basic_lock_sp_offset)
{
  {
    DEBUG_ONLY(NoSafepointVerifier nsv;)
    assert_locked_or_safepoint(CodeCache_lock);

    init_defaults(code_buffer, offsets);

    _osr_entry_point         = nullptr;
    _pc_desc_container       = nullptr;
    _entry_bci               = InvocationEntryBci;
    _compile_id              = compile_id;
    _comp_level              = CompLevel_none;
    _compiler_type           = type;
    _orig_pc_offset          = 0;
    _num_stack_arg_slots     = 0;

    if (offsets->value(CodeOffsets::Exceptions) != -1) {
      // Continuation enter intrinsic
      _exception_offset      = code_offset() + offsets->value(CodeOffsets::Exceptions);
    } else {
      _exception_offset      = 0;
    }
    // Native wrappers do not have deopt handlers. Make the values
    // something that will never match a pc like the nmethod vtable entry
    _deopt_handler_offset    = 0;
    _deopt_mh_handler_offset = 0;
    _unwind_handler_offset   = 0;

    CHECKED_CAST(_oops_size, uint16_t, align_up(code_buffer->total_oop_size(), oopSize));
    uint16_t metadata_size;
    CHECKED_CAST(metadata_size, uint16_t, align_up(code_buffer->total_metadata_size(), wordSize));
    JVMCI_ONLY( _metadata_size = metadata_size; )
    assert(_mutable_data_size == _relocation_size + metadata_size,
           "wrong mutable data size: %d != %d + %d",
           _mutable_data_size, _relocation_size, metadata_size);

    // native wrapper does not have read-only data but we need unique not null address
    _immutable_data          = blob_end();
    _immutable_data_size     = 0;
    _nul_chk_table_offset    = 0;
    _handler_table_offset    = 0;
    _scopes_pcs_offset       = 0;
    _scopes_data_offset      = 0;
#if INCLUDE_JVMCI
    _speculations_offset     = 0;
#endif

    code_buffer->copy_code_and_locs_to(this);
    code_buffer->copy_values_to(this);

    post_init();
  }

  if (PrintNativeNMethods || PrintDebugInfo || PrintRelocations || PrintDependencies) {
    ttyLocker ttyl;  // keep the following output all in one block
    // This output goes directly to the tty, not the compiler log.
    // To enable tools to match it up with the compilation activity,
    // be sure to tag this tty output with the compile ID.
    if (xtty != nullptr) {
      xtty->begin_head("print_native_nmethod");
      xtty->method(_method);
      xtty->stamp();
      xtty->end_head(" address='" INTPTR_FORMAT "'", (intptr_t) this);
    }
    // Print the header part, then print the requested information.
    // This is both handled in decode2(), called via print_code() -> decode()
    if (PrintNativeNMethods) {
      tty->print_cr("-------------------------- Assembly (native nmethod) ---------------------------");
      print_code();
      tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
#if defined(SUPPORT_DATA_STRUCTS)
      if (AbstractDisassembler::show_structs()) {
        if (oop_maps != nullptr) {
          tty->print("oop maps:"); // oop_maps->print_on(tty) outputs a cr() at the beginning
          oop_maps->print_on(tty);
          tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
        }
      }
#endif
    } else {
      print(); // print the header part only.
    }
#if defined(SUPPORT_DATA_STRUCTS)
    if (AbstractDisassembler::show_structs()) {
      if (PrintRelocations) {
        print_relocations();
        tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
      }
    }
#endif
    if (xtty != nullptr) {
      xtty->tail("print_native_nmethod");
    }
  }
}

void* nmethod::operator new(size_t size, int nmethod_size, int comp_level) throw () {
  return CodeCache::allocate(nmethod_size, CodeCache::get_code_blob_type(comp_level));
}

void* nmethod::operator new(size_t size, int nmethod_size, bool allow_NonNMethod_space) throw () {
  // Try MethodNonProfiled and MethodProfiled.
  void* return_value = CodeCache::allocate(nmethod_size, CodeBlobType::MethodNonProfiled);
  if (return_value != nullptr || !allow_NonNMethod_space) return return_value;
  // Try NonNMethod or give up.
  return CodeCache::allocate(nmethod_size, CodeBlobType::NonNMethod);
}

// For normal JIT compiled code
nmethod::nmethod(
  Method* method,
  CompilerType type,
  int nmethod_size,
  int immutable_data_size,
  int mutable_data_size,
  int compile_id,
  int entry_bci,
  address immutable_data,
  CodeOffsets* offsets,
  int orig_pc_offset,
  DebugInformationRecorder* debug_info,
  Dependencies* dependencies,
  CodeBuffer *code_buffer,
  int frame_size,
  OopMapSet* oop_maps,
  ExceptionHandlerTable* handler_table,
  ImplicitExceptionTable* nul_chk_table,
  AbstractCompiler* compiler,
  CompLevel comp_level
#if INCLUDE_JVMCI
  , char* speculations,
  int speculations_len,
  JVMCINMethodData* jvmci_data
#endif
  )
  : CodeBlob("nmethod", CodeBlobKind::Nmethod, code_buffer, nmethod_size, sizeof(nmethod),
             offsets->value(CodeOffsets::Frame_Complete), frame_size, oop_maps, false, mutable_data_size),
  _deoptimization_generation(0),
  _gc_epoch(CodeCache::gc_epoch()),
  _method(method),
  _osr_link(nullptr)
{
  assert(debug_info->oop_recorder() == code_buffer->oop_recorder(), "shared OR");
  {
    DEBUG_ONLY(NoSafepointVerifier nsv;)
    assert_locked_or_safepoint(CodeCache_lock);

    init_defaults(code_buffer, offsets);

    _osr_entry_point = code_begin() + offsets->value(CodeOffsets::OSR_Entry);
    _entry_bci       = entry_bci;
    _compile_id      = compile_id;
    _comp_level      = comp_level;
    _compiler_type   = type;
    _orig_pc_offset  = orig_pc_offset;

    _num_stack_arg_slots = entry_bci != InvocationEntryBci ? 0 : _method->constMethod()->num_stack_arg_slots();

    set_ctable_begin(header_begin() + content_offset());

#if INCLUDE_JVMCI
    if (compiler->is_jvmci()) {
      // JVMCI might not produce any stub sections
      if (offsets->value(CodeOffsets::Exceptions) != -1) {
        _exception_offset        = code_offset() + offsets->value(CodeOffsets::Exceptions);
      } else {
        _exception_offset        = -1;
      }
      if (offsets->value(CodeOffsets::Deopt) != -1) {
        _deopt_handler_offset    = code_offset() + offsets->value(CodeOffsets::Deopt);
      } else {
        _deopt_handler_offset    = -1;
      }
      if (offsets->value(CodeOffsets::DeoptMH) != -1) {
        _deopt_mh_handler_offset = code_offset() + offsets->value(CodeOffsets::DeoptMH);
      } else {
        _deopt_mh_handler_offset = -1;
      }
    } else
#endif
    {
      // Exception handler and deopt handler are in the stub section
      assert(offsets->value(CodeOffsets::Exceptions) != -1, "must be set");
      assert(offsets->value(CodeOffsets::Deopt     ) != -1, "must be set");

      _exception_offset          = _stub_offset + offsets->value(CodeOffsets::Exceptions);
      _deopt_handler_offset      = _stub_offset + offsets->value(CodeOffsets::Deopt);
      if (offsets->value(CodeOffsets::DeoptMH) != -1) {
        _deopt_mh_handler_offset = _stub_offset + offsets->value(CodeOffsets::DeoptMH);
      } else {
        _deopt_mh_handler_offset = -1;
      }
    }
    if (offsets->value(CodeOffsets::UnwindHandler) != -1) {
      // C1 generates UnwindHandler at the end of instructions section.
      // Calculate positive offset as distance between the start of stubs section
      // (which is also the end of instructions section) and the start of the handler.
      int unwind_handler_offset = code_offset() + offsets->value(CodeOffsets::UnwindHandler);
      CHECKED_CAST(_unwind_handler_offset, int16_t, (_stub_offset - unwind_handler_offset));
    } else {
      _unwind_handler_offset = -1;
    }

    CHECKED_CAST(_oops_size, uint16_t, align_up(code_buffer->total_oop_size(), oopSize));
    uint16_t metadata_size;
    CHECKED_CAST(metadata_size, uint16_t, align_up(code_buffer->total_metadata_size(), wordSize));
    JVMCI_ONLY( _metadata_size = metadata_size; )
    int jvmci_data_size = 0 JVMCI_ONLY( + align_up(compiler->is_jvmci() ? jvmci_data->size() : 0, oopSize));
    assert(_mutable_data_size == _relocation_size + metadata_size + jvmci_data_size,
           "wrong mutable data size: %d != %d + %d + %d",
           _mutable_data_size, _relocation_size, metadata_size, jvmci_data_size);
    assert(nmethod_size == data_end() - header_begin(), "wrong nmethod size: %d != %d",
           nmethod_size, (int)(code_end() - header_begin()));

    _immutable_data_size  = immutable_data_size;
    if (immutable_data_size > 0) {
      assert(immutable_data != nullptr, "required");
      _immutable_data     = immutable_data;
    } else {
      // We need unique not null address
      _immutable_data     = blob_end();
    }
    CHECKED_CAST(_nul_chk_table_offset, uint16_t, (align_up((int)dependencies->size_in_bytes(), oopSize)));
    CHECKED_CAST(_handler_table_offset, uint16_t, (_nul_chk_table_offset + align_up(nul_chk_table->size_in_bytes(), oopSize)));
    _scopes_pcs_offset    = _handler_table_offset + align_up(handler_table->size_in_bytes(), oopSize);
    _scopes_data_offset   = _scopes_pcs_offset    + adjust_pcs_size(debug_info->pcs_size());

#if INCLUDE_JVMCI
    _speculations_offset  = _scopes_data_offset   + align_up(debug_info->data_size(), oopSize);
    DEBUG_ONLY( int immutable_data_end_offset = _speculations_offset  + align_up(speculations_len, oopSize); )
#else
    DEBUG_ONLY( int immutable_data_end_offset = _scopes_data_offset + align_up(debug_info->data_size(), oopSize); )
#endif
    assert(immutable_data_end_offset <= immutable_data_size, "wrong read-only data size: %d > %d",
           immutable_data_end_offset, immutable_data_size);

    // Copy code and relocation info
    code_buffer->copy_code_and_locs_to(this);
    // Copy oops and metadata
    code_buffer->copy_values_to(this);
    dependencies->copy_to(this);
    // Copy PcDesc and ScopeDesc data
    debug_info->copy_to(this);

    // Create cache after PcDesc data is copied - it will be used to initialize cache
    _pc_desc_container = new PcDescContainer(scopes_pcs_begin());

#if INCLUDE_JVMCI
    if (compiler->is_jvmci()) {
      // Initialize the JVMCINMethodData object inlined into nm
      jvmci_nmethod_data()->copy(jvmci_data);
    }
#endif

    // Copy contents of ExceptionHandlerTable to nmethod
    handler_table->copy_to(this);
    nul_chk_table->copy_to(this);

#if INCLUDE_JVMCI
    // Copy speculations to nmethod
    if (speculations_size() != 0) {
      memcpy(speculations_begin(), speculations, speculations_len);
    }
#endif

    post_init();

    // we use the information of entry points to find out if a method is
    // static or non static
    assert(compiler->is_c2() || compiler->is_jvmci() ||
           _method->is_static() == (entry_point() == verified_entry_point()),
           " entry points must be same for static methods and vice versa");
  }
}

// Print a short set of xml attributes to identify this nmethod.  The
// output should be embedded in some other element.
void nmethod::log_identity(xmlStream* log) const {
  log->print(" compile_id='%d'", compile_id());
  const char* nm_kind = compile_kind();
  if (nm_kind != nullptr)  log->print(" compile_kind='%s'", nm_kind);
  log->print(" compiler='%s'", compiler_name());
  if (TieredCompilation) {
    log->print(" level='%d'", comp_level());
  }
#if INCLUDE_JVMCI
  if (jvmci_nmethod_data() != nullptr) {
    const char* jvmci_name = jvmci_nmethod_data()->name();
    if (jvmci_name != nullptr) {
      log->print(" jvmci_mirror_name='");
      log->text("%s", jvmci_name);
      log->print("'");
    }
  }
#endif
}


#define LOG_OFFSET(log, name)                    \
  if (p2i(name##_end()) - p2i(name##_begin())) \
    log->print(" " XSTR(name) "_offset='%zd'"    , \
               p2i(name##_begin()) - p2i(this))


void nmethod::log_new_nmethod() const {
  if (LogCompilation && xtty != nullptr) {
    ttyLocker ttyl;
    xtty->begin_elem("nmethod");
    log_identity(xtty);
    xtty->print(" entry='" INTPTR_FORMAT "' size='%d'", p2i(code_begin()), size());
    xtty->print(" address='" INTPTR_FORMAT "'", p2i(this));

    LOG_OFFSET(xtty, relocation);
    LOG_OFFSET(xtty, consts);
    LOG_OFFSET(xtty, insts);
    LOG_OFFSET(xtty, stub);
    LOG_OFFSET(xtty, scopes_data);
    LOG_OFFSET(xtty, scopes_pcs);
    LOG_OFFSET(xtty, dependencies);
    LOG_OFFSET(xtty, handler_table);
    LOG_OFFSET(xtty, nul_chk_table);
    LOG_OFFSET(xtty, oops);
    LOG_OFFSET(xtty, metadata);

    xtty->method(method());
    xtty->stamp();
    xtty->end_elem();
  }
}

#undef LOG_OFFSET


// Print out more verbose output usually for a newly created nmethod.
void nmethod::print_on_with_msg(outputStream* st, const char* msg) const {
  if (st != nullptr) {
    ttyLocker ttyl;
    if (WizardMode) {
      CompileTask::print(st, this, msg, /*short_form:*/ true);
      st->print_cr(" (" INTPTR_FORMAT ")", p2i(this));
    } else {
      CompileTask::print(st, this, msg, /*short_form:*/ false);
    }
  }
}

void nmethod::maybe_print_nmethod(const DirectiveSet* directive) {
  bool printnmethods = directive->PrintAssemblyOption || directive->PrintNMethodsOption;
  if (printnmethods || PrintDebugInfo || PrintRelocations || PrintDependencies || PrintExceptionHandlers) {
    print_nmethod(printnmethods);
  }
}

void nmethod::print_nmethod(bool printmethod) {
  ttyLocker ttyl;  // keep the following output all in one block
  if (xtty != nullptr) {
    xtty->begin_head("print_nmethod");
    log_identity(xtty);
    xtty->stamp();
    xtty->end_head();
  }
  // Print the header part, then print the requested information.
  // This is both handled in decode2().
  if (printmethod) {
    ResourceMark m;
    if (is_compiled_by_c1()) {
      tty->cr();
      tty->print_cr("============================= C1-compiled nmethod ==============================");
    }
    if (is_compiled_by_jvmci()) {
      tty->cr();
      tty->print_cr("=========================== JVMCI-compiled nmethod =============================");
    }
    tty->print_cr("----------------------------------- Assembly -----------------------------------");
    decode2(tty);
#if defined(SUPPORT_DATA_STRUCTS)
    if (AbstractDisassembler::show_structs()) {
      // Print the oops from the underlying CodeBlob as well.
      tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
      print_oops(tty);
      tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
      print_metadata(tty);
      tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
      print_pcs_on(tty);
      tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
      if (oop_maps() != nullptr) {
        tty->print("oop maps:"); // oop_maps()->print_on(tty) outputs a cr() at the beginning
        oop_maps()->print_on(tty);
        tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
      }
    }
#endif
  } else {
    print(); // print the header part only.
  }

#if defined(SUPPORT_DATA_STRUCTS)
  if (AbstractDisassembler::show_structs()) {
    methodHandle mh(Thread::current(), _method);
    if (printmethod || PrintDebugInfo || CompilerOracle::has_option(mh, CompileCommandEnum::PrintDebugInfo)) {
      print_scopes();
      tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
    }
    if (printmethod || PrintRelocations || CompilerOracle::has_option(mh, CompileCommandEnum::PrintRelocations)) {
      print_relocations();
      tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
    }
    if (printmethod || PrintDependencies || CompilerOracle::has_option(mh, CompileCommandEnum::PrintDependencies)) {
      print_dependencies_on(tty);
      tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
    }
    if (printmethod || PrintExceptionHandlers) {
      print_handler_table();
      tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
      print_nul_chk_table();
      tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
    }

    if (printmethod) {
      print_recorded_oops();
      tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
      print_recorded_metadata();
      tty->print_cr("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - ");
    }
  }
#endif

  if (xtty != nullptr) {
    xtty->tail("print_nmethod");
  }
}


// Promote one word from an assembly-time handle to a live embedded oop.
inline void nmethod::initialize_immediate_oop(oop* dest, jobject handle) {
  if (handle == nullptr ||
      // As a special case, IC oops are initialized to 1 or -1.
      handle == (jobject) Universe::non_oop_word()) {
    *(void**)dest = handle;
  } else {
    *dest = JNIHandles::resolve_non_null(handle);
  }
}


// Have to have the same name because it's called by a template
void nmethod::copy_values(GrowableArray<jobject>* array) {
  int length = array->length();
  assert((address)(oops_begin() + length) <= (address)oops_end(), "oops big enough");
  oop* dest = oops_begin();
  for (int index = 0 ; index < length; index++) {
    initialize_immediate_oop(&dest[index], array->at(index));
  }

  // Now we can fix up all the oops in the code.  We need to do this
  // in the code because the assembler uses jobjects as placeholders.
  // The code and relocations have already been initialized by the
  // CodeBlob constructor, so it is valid even at this early point to
  // iterate over relocations and patch the code.
  fix_oop_relocations(nullptr, nullptr, /*initialize_immediates=*/ true);
}

void nmethod::copy_values(GrowableArray<Metadata*>* array) {
  int length = array->length();
  assert((address)(metadata_begin() + length) <= (address)metadata_end(), "big enough");
  Metadata** dest = metadata_begin();
  for (int index = 0 ; index < length; index++) {
    dest[index] = array->at(index);
  }
}

void nmethod::fix_oop_relocations(address begin, address end, bool initialize_immediates) {
  // re-patch all oop-bearing instructions, just in case some oops moved
  RelocIterator iter(this, begin, end);
  while (iter.next()) {
    if (iter.type() == relocInfo::oop_type) {
      oop_Relocation* reloc = iter.oop_reloc();
      if (initialize_immediates && reloc->oop_is_immediate()) {
        oop* dest = reloc->oop_addr();
        jobject obj = *reinterpret_cast<jobject*>(dest);
        initialize_immediate_oop(dest, obj);
      }
      // Refresh the oop-related bits of this instruction.
      reloc->fix_oop_relocation();
    } else if (iter.type() == relocInfo::metadata_type) {
      metadata_Relocation* reloc = iter.metadata_reloc();
      reloc->fix_metadata_relocation();
    }
  }
}

static void install_post_call_nop_displacement(nmethod* nm, address pc) {
  NativePostCallNop* nop = nativePostCallNop_at((address) pc);
  intptr_t cbaddr = (intptr_t) nm;
  intptr_t offset = ((intptr_t) pc) - cbaddr;

  int oopmap_slot = nm->oop_maps()->find_slot_for_offset(int((intptr_t) pc - (intptr_t) nm->code_begin()));
  if (oopmap_slot < 0) { // this can happen at asynchronous (non-safepoint) stackwalks
    log_debug(codecache)("failed to find oopmap for cb: " INTPTR_FORMAT " offset: %d", cbaddr, (int) offset);
  } else if (!nop->patch(oopmap_slot, offset)) {
    log_debug(codecache)("failed to encode %d %d", oopmap_slot, (int) offset);
  }
}

void nmethod::finalize_relocations() {
  NoSafepointVerifier nsv;

  GrowableArray<NativeMovConstReg*> virtual_call_data;

  // Make sure that post call nops fill in nmethod offsets eagerly so
  // we don't have to race with deoptimization
  RelocIterator iter(this);
  while (iter.next()) {
    if (iter.type() == relocInfo::virtual_call_type) {
      virtual_call_Relocation* r = iter.virtual_call_reloc();
      NativeMovConstReg* value = nativeMovConstReg_at(r->cached_value());
      virtual_call_data.append(value);
    } else if (iter.type() == relocInfo::post_call_nop_type) {
      post_call_nop_Relocation* const reloc = iter.post_call_nop_reloc();
      address pc = reloc->addr();
      install_post_call_nop_displacement(this, pc);
    }
  }

  if (virtual_call_data.length() > 0) {
    // We allocate a block of CompiledICData per nmethod so the GC can purge this faster.
    _compiled_ic_data = new CompiledICData[virtual_call_data.length()];
    CompiledICData* next_data = _compiled_ic_data;

    for (NativeMovConstReg* value : virtual_call_data) {
      value->set_data((intptr_t)next_data);
      next_data++;
    }
  }
}

void nmethod::make_deoptimized() {
  if (!Continuations::enabled()) {
    // Don't deopt this again.
    set_deoptimized_done();
    return;
  }

  assert(method() == nullptr || can_be_deoptimized(), "");

  CompiledICLocker ml(this);
  assert(CompiledICLocker::is_safe(this), "mt unsafe call");

  // If post call nops have been already patched, we can just bail-out.
  if (has_been_deoptimized()) {
    return;
  }

  ResourceMark rm;
  RelocIterator iter(this, oops_reloc_begin());

  while (iter.next()) {

    switch (iter.type()) {
      case relocInfo::virtual_call_type: {
        CompiledIC *ic = CompiledIC_at(&iter);
        address pc = ic->end_of_call();
        NativePostCallNop* nop = nativePostCallNop_at(pc);
        if (nop != nullptr) {
          nop->make_deopt();
        }
        assert(NativeDeoptInstruction::is_deopt_at(pc), "check");
        break;
      }
      case relocInfo::static_call_type:
      case relocInfo::opt_virtual_call_type: {
        CompiledDirectCall *csc = CompiledDirectCall::at(iter.reloc());
        address pc = csc->end_of_call();
        NativePostCallNop* nop = nativePostCallNop_at(pc);
        //tty->print_cr(" - static pc %p", pc);
        if (nop != nullptr) {
          nop->make_deopt();
        }
        // We can't assert here, there are some calls to stubs / runtime
        // that have reloc data and doesn't have a post call NOP.
        //assert(NativeDeoptInstruction::is_deopt_at(pc), "check");
        break;
      }
      default:
        break;
    }
  }
  // Don't deopt this again.
  set_deoptimized_done();
}

void nmethod::verify_clean_inline_caches() {
  assert(CompiledICLocker::is_safe(this), "mt unsafe call");

  ResourceMark rm;
  RelocIterator iter(this, oops_reloc_begin());
  while(iter.next()) {
    switch(iter.type()) {
      case relocInfo::virtual_call_type: {
        CompiledIC *ic = CompiledIC_at(&iter);
        CodeBlob *cb = CodeCache::find_blob(ic->destination());
        assert(cb != nullptr, "destination not in CodeBlob?");
        nmethod* nm = cb->as_nmethod_or_null();
        if (nm != nullptr) {
          // Verify that inline caches pointing to bad nmethods are clean
          if (!nm->is_in_use() || nm->is_unloading()) {
            assert(ic->is_clean(), "IC should be clean");
          }
        }
        break;
      }
      case relocInfo::static_call_type:
      case relocInfo::opt_virtual_call_type: {
        CompiledDirectCall *cdc = CompiledDirectCall::at(iter.reloc());
        CodeBlob *cb = CodeCache::find_blob(cdc->destination());
        assert(cb != nullptr, "destination not in CodeBlob?");
        nmethod* nm = cb->as_nmethod_or_null();
        if (nm != nullptr) {
          // Verify that inline caches pointing to bad nmethods are clean
          if (!nm->is_in_use() || nm->is_unloading() || nm->method()->code() != nm) {
            assert(cdc->is_clean(), "IC should be clean");
          }
        }
        break;
      }
      default:
        break;
    }
  }
}

void nmethod::mark_as_maybe_on_stack() {
  Atomic::store(&_gc_epoch, CodeCache::gc_epoch());
}

bool nmethod::is_maybe_on_stack() {
  // If the condition below is true, it means that the nmethod was found to
  // be alive the previous completed marking cycle.
  return Atomic::load(&_gc_epoch) >= CodeCache::previous_completed_gc_marking_cycle();
}

void nmethod::inc_decompile_count() {
  if (!is_compiled_by_c2() && !is_compiled_by_jvmci()) return;
  // Could be gated by ProfileTraps, but do not bother...
#if INCLUDE_JVMCI
  if (jvmci_skip_profile_deopt()) {
    return;
  }
#endif
  Method* m = method();
  if (m == nullptr)  return;
  MethodData* mdo = m->method_data();
  if (mdo == nullptr)  return;
  // There is a benign race here.  See comments in methodData.hpp.
  mdo->inc_decompile_count();
}

bool nmethod::try_transition(signed char new_state_int) {
  signed char new_state = new_state_int;
  assert_lock_strong(NMethodState_lock);
  signed char old_state = _state;
  if (old_state >= new_state) {
    // Ensure monotonicity of transitions.
    return false;
  }
  Atomic::store(&_state, new_state);
  return true;
}

void nmethod::invalidate_osr_method() {
  assert(_entry_bci != InvocationEntryBci, "wrong kind of nmethod");
  // Remove from list of active nmethods
  if (method() != nullptr) {
    method()->method_holder()->remove_osr_nmethod(this);
  }
}

void nmethod::log_state_change(InvalidationReason invalidation_reason) const {
  if (LogCompilation) {
    if (xtty != nullptr) {
      ttyLocker ttyl;  // keep the following output all in one block
      xtty->begin_elem("make_not_entrant thread='%zu' reason='%s'",
                       os::current_thread_id(), invalidation_reason_to_string(invalidation_reason));
      log_identity(xtty);
      xtty->stamp();
      xtty->end_elem();
    }
  }

  ResourceMark rm;
  stringStream ss(NEW_RESOURCE_ARRAY(char, 256), 256);
  ss.print("made not entrant: %s", invalidation_reason_to_string(invalidation_reason));

  CompileTask::print_ul(this, ss.freeze());
  if (PrintCompilation) {
    print_on_with_msg(tty, ss.freeze());
  }
}

void nmethod::unlink_from_method() {
  if (method() != nullptr) {
    method()->unlink_code(this);
  }
}

// Invalidate code
bool nmethod::make_not_entrant(InvalidationReason invalidation_reason) {
  // This can be called while the system is already at a safepoint which is ok
  NoSafepointVerifier nsv;

  if (is_unloading()) {
    // If the nmethod is unloading, then it is already not entrant through
    // the nmethod entry barriers. No need to do anything; GC will unload it.
    return false;
  }

  if (Atomic::load(&_state) == not_entrant) {
    // Avoid taking the lock if already in required state.
    // This is safe from races because the state is an end-state,
    // which the nmethod cannot back out of once entered.
    // No need for fencing either.
    return false;
  }

  {
    // Enter critical section.  Does not block for safepoint.
    ConditionalMutexLocker ml(NMethodState_lock, !NMethodState_lock->owned_by_self(), Mutex::_no_safepoint_check_flag);

    if (Atomic::load(&_state) == not_entrant) {
      // another thread already performed this transition so nothing
      // to do, but return false to indicate this.
      return false;
    }

    if (is_osr_method()) {
      // This logic is equivalent to the logic below for patching the
      // verified entry point of regular methods.
      // this effectively makes the osr nmethod not entrant
      invalidate_osr_method();
    } else {
      // The caller can be calling the method statically or through an inline
      // cache call.
      BarrierSet::barrier_set()->barrier_set_nmethod()->make_not_entrant(this);
    }

    if (update_recompile_counts()) {
      // Mark the method as decompiled.
      inc_decompile_count();
    }

    BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
    if (bs_nm == nullptr || !bs_nm->supports_entry_barrier(this)) {
      // If nmethod entry barriers are not supported, we won't mark
      // nmethods as on-stack when they become on-stack. So we
      // degrade to a less accurate flushing strategy, for now.
      mark_as_maybe_on_stack();
    }

    // Change state
    bool success = try_transition(not_entrant);
    assert(success, "Transition can't fail");

    // Log the transition once
    log_state_change(invalidation_reason);

    // Remove nmethod from method.
    unlink_from_method();

  } // leave critical region under NMethodState_lock

#if INCLUDE_JVMCI
  // Invalidate can't occur while holding the NMethodState_lock
  JVMCINMethodData* nmethod_data = jvmci_nmethod_data();
  if (nmethod_data != nullptr) {
    nmethod_data->invalidate_nmethod_mirror(this, invalidation_reason);
  }
#endif

#ifdef ASSERT
  if (is_osr_method() && method() != nullptr) {
    // Make sure osr nmethod is invalidated, i.e. not on the list
    bool found = method()->method_holder()->remove_osr_nmethod(this);
    assert(!found, "osr nmethod should have been invalidated");
  }
#endif

  return true;
}

// For concurrent GCs, there must be a handshake between unlink and flush
void nmethod::unlink() {
  if (is_unlinked()) {
    // Already unlinked.
    return;
  }

  flush_dependencies();

  // unlink_from_method will take the NMethodState_lock.
  // In this case we don't strictly need it when unlinking nmethods from
  // the Method, because it is only concurrently unlinked by
  // the entry barrier, which acquires the per nmethod lock.
  unlink_from_method();

  if (is_osr_method()) {
    invalidate_osr_method();
  }

#if INCLUDE_JVMCI
  // Clear the link between this nmethod and a HotSpotNmethod mirror
  JVMCINMethodData* nmethod_data = jvmci_nmethod_data();
  if (nmethod_data != nullptr) {
    nmethod_data->invalidate_nmethod_mirror(this, is_cold() ?
            nmethod::InvalidationReason::UNLOADING_COLD :
            nmethod::InvalidationReason::UNLOADING);
  }
#endif

  // Post before flushing as jmethodID is being used
  post_compiled_method_unload();

  // Register for flushing when it is safe. For concurrent class unloading,
  // that would be after the unloading handshake, and for STW class unloading
  // that would be when getting back to the VM thread.
  ClassUnloadingContext::context()->register_unlinked_nmethod(this);
}

void nmethod::purge(bool unregister_nmethod) {

  MutexLocker ml(CodeCache_lock, Mutex::_no_safepoint_check_flag);

  // completely deallocate this method
  Events::log_nmethod_flush(Thread::current(), "flushing %s nmethod " INTPTR_FORMAT, is_osr_method() ? "osr" : "", p2i(this));

  LogTarget(Debug, codecache) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    const char* method_name = method()->name()->as_C_string();
    const size_t codecache_capacity = CodeCache::capacity()/1024;
    const size_t codecache_free_space = CodeCache::unallocated_capacity(CodeCache::get_code_blob_type(this))/1024;
    ls.print("Flushing nmethod %6d/" INTPTR_FORMAT ", level=%d, osr=%d, cold=%d, epoch=" UINT64_FORMAT ", cold_count=" UINT64_FORMAT ". "
              "Cache capacity: %zuKb, free space: %zuKb. method %s (%s)",
              _compile_id, p2i(this), _comp_level, is_osr_method(), is_cold(), _gc_epoch, CodeCache::cold_gc_count(),
              codecache_capacity, codecache_free_space, method_name, compiler_name());
  }

  // We need to deallocate any ExceptionCache data.
  // Note that we do not need to grab the nmethod lock for this, it
  // better be thread safe if we're disposing of it!
  ExceptionCache* ec = exception_cache();
  while(ec != nullptr) {
    ExceptionCache* next = ec->next();
    delete ec;
    ec = next;
  }
  if (_pc_desc_container != nullptr) {
    delete _pc_desc_container;
  }
  delete[] _compiled_ic_data;

  if (_immutable_data != blob_end()) {
    os::free(_immutable_data);
    _immutable_data = blob_end(); // Valid not null address
  }
  if (unregister_nmethod) {
    Universe::heap()->unregister_nmethod(this);
  }
  CodeCache::unregister_old_nmethod(this);

  JVMCI_ONLY( _metadata_size = 0; )
  CodeBlob::purge();
}

oop nmethod::oop_at(int index) const {
  if (index == 0) {
    return nullptr;
  }

  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  return bs_nm->oop_load_no_keepalive(this, index);
}

oop nmethod::oop_at_phantom(int index) const {
  if (index == 0) {
    return nullptr;
  }

  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  return bs_nm->oop_load_phantom(this, index);
}

//
// Notify all classes this nmethod is dependent on that it is no
// longer dependent.

void nmethod::flush_dependencies() {
  if (!has_flushed_dependencies()) {
    set_has_flushed_dependencies(true);
    for (Dependencies::DepStream deps(this); deps.next(); ) {
      if (deps.type() == Dependencies::call_site_target_value) {
        // CallSite dependencies are managed on per-CallSite instance basis.
        oop call_site = deps.argument_oop(0);
        MethodHandles::clean_dependency_context(call_site);
      } else {
        InstanceKlass* ik = deps.context_type();
        if (ik == nullptr) {
          continue;  // ignore things like evol_method
        }
        // During GC liveness of dependee determines class that needs to be updated.
        // The GC may clean dependency contexts concurrently and in parallel.
        ik->clean_dependency_context();
      }
    }
  }
}

void nmethod::post_compiled_method(CompileTask* task) {
  task->mark_success();
  task->set_nm_content_size(content_size());
  task->set_nm_insts_size(insts_size());
  task->set_nm_total_size(total_size());

  // JVMTI -- compiled method notification (must be done outside lock)
  post_compiled_method_load_event();

  if (CompilationLog::log() != nullptr) {
    CompilationLog::log()->log_nmethod(JavaThread::current(), this);
  }

  const DirectiveSet* directive = task->directive();
  maybe_print_nmethod(directive);
}

// ------------------------------------------------------------------
// post_compiled_method_load_event
// new method for install_code() path
// Transfer information from compilation to jvmti
void nmethod::post_compiled_method_load_event(JvmtiThreadState* state) {
  // This is a bad time for a safepoint.  We don't want
  // this nmethod to get unloaded while we're queueing the event.
  NoSafepointVerifier nsv;

  Method* m = method();
  HOTSPOT_COMPILED_METHOD_LOAD(
      (char *) m->klass_name()->bytes(),
      m->klass_name()->utf8_length(),
      (char *) m->name()->bytes(),
      m->name()->utf8_length(),
      (char *) m->signature()->bytes(),
      m->signature()->utf8_length(),
      insts_begin(), insts_size());


  if (JvmtiExport::should_post_compiled_method_load()) {
    // Only post unload events if load events are found.
    set_load_reported();
    // If a JavaThread hasn't been passed in, let the Service thread
    // (which is a real Java thread) post the event
    JvmtiDeferredEvent event = JvmtiDeferredEvent::compiled_method_load_event(this);
    if (state == nullptr) {
      // Execute any barrier code for this nmethod as if it's called, since
      // keeping it alive looks like stack walking.
      run_nmethod_entry_barrier();
      ServiceThread::enqueue_deferred_event(&event);
    } else {
      // This enters the nmethod barrier outside in the caller.
      state->enqueue_event(&event);
    }
  }
}

void nmethod::post_compiled_method_unload() {
  assert(_method != nullptr, "just checking");
  DTRACE_METHOD_UNLOAD_PROBE(method());

  // If a JVMTI agent has enabled the CompiledMethodUnload event then
  // post the event. The Method* will not be valid when this is freed.

  // Don't bother posting the unload if the load event wasn't posted.
  if (load_reported() && JvmtiExport::should_post_compiled_method_unload()) {
    JvmtiDeferredEvent event =
      JvmtiDeferredEvent::compiled_method_unload_event(
          method()->jmethod_id(), insts_begin());
    ServiceThread::enqueue_deferred_event(&event);
  }
}

// Iterate over metadata calling this function.   Used by RedefineClasses
void nmethod::metadata_do(MetadataClosure* f) {
  {
    // Visit all immediate references that are embedded in the instruction stream.
    RelocIterator iter(this, oops_reloc_begin());
    while (iter.next()) {
      if (iter.type() == relocInfo::metadata_type) {
        metadata_Relocation* r = iter.metadata_reloc();
        // In this metadata, we must only follow those metadatas directly embedded in
        // the code.  Other metadatas (oop_index>0) are seen as part of
        // the metadata section below.
        assert(1 == (r->metadata_is_immediate()) +
               (r->metadata_addr() >= metadata_begin() && r->metadata_addr() < metadata_end()),
               "metadata must be found in exactly one place");
        if (r->metadata_is_immediate() && r->metadata_value() != nullptr) {
          Metadata* md = r->metadata_value();
          if (md != _method) f->do_metadata(md);
        }
      } else if (iter.type() == relocInfo::virtual_call_type) {
        // Check compiledIC holders associated with this nmethod
        ResourceMark rm;
        CompiledIC *ic = CompiledIC_at(&iter);
        ic->metadata_do(f);
      }
    }
  }

  // Visit the metadata section
  for (Metadata** p = metadata_begin(); p < metadata_end(); p++) {
    if (*p == Universe::non_oop_word() || *p == nullptr)  continue;  // skip non-oops
    Metadata* md = *p;
    f->do_metadata(md);
  }

  // Visit metadata not embedded in the other places.
  if (_method != nullptr) f->do_metadata(_method);
}

// Heuristic for nuking nmethods even though their oops are live.
// Main purpose is to reduce code cache pressure and get rid of
// nmethods that don't seem to be all that relevant any longer.
bool nmethod::is_cold() {
  if (!MethodFlushing || is_native_method() || is_not_installed()) {
    // No heuristic unloading at all
    return false;
  }

  if (!is_maybe_on_stack() && is_not_entrant()) {
    // Not entrant nmethods that are not on any stack can just
    // be removed
    return true;
  }

  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  if (bs_nm == nullptr || !bs_nm->supports_entry_barrier(this)) {
    // On platforms that don't support nmethod entry barriers, we can't
    // trust the temporal aspect of the gc epochs. So we can't detect
    // cold nmethods on such platforms.
    return false;
  }

  if (!UseCodeCacheFlushing) {
    // Bail out if we don't heuristically remove nmethods
    return false;
  }

  // Other code can be phased out more gradually after N GCs
  return CodeCache::previous_completed_gc_marking_cycle() > _gc_epoch + 2 * CodeCache::cold_gc_count();
}

// The _is_unloading_state encodes a tuple comprising the unloading cycle
// and the result of IsUnloadingBehaviour::is_unloading() for that cycle.
// This is the bit layout of the _is_unloading_state byte: 00000CCU
// CC refers to the cycle, which has 2 bits, and U refers to the result of
// IsUnloadingBehaviour::is_unloading() for that unloading cycle.

class IsUnloadingState: public AllStatic {
  static const uint8_t _is_unloading_mask = 1;
  static const uint8_t _is_unloading_shift = 0;
  static const uint8_t _unloading_cycle_mask = 6;
  static const uint8_t _unloading_cycle_shift = 1;

  static uint8_t set_is_unloading(uint8_t state, bool value) {
    state &= (uint8_t)~_is_unloading_mask;
    if (value) {
      state |= 1 << _is_unloading_shift;
    }
    assert(is_unloading(state) == value, "unexpected unloading cycle overflow");
    return state;
  }

  static uint8_t set_unloading_cycle(uint8_t state, uint8_t value) {
    state &= (uint8_t)~_unloading_cycle_mask;
    state |= (uint8_t)(value << _unloading_cycle_shift);
    assert(unloading_cycle(state) == value, "unexpected unloading cycle overflow");
    return state;
  }

public:
  static bool is_unloading(uint8_t state) { return (state & _is_unloading_mask) >> _is_unloading_shift == 1; }
  static uint8_t unloading_cycle(uint8_t state) { return (state & _unloading_cycle_mask) >> _unloading_cycle_shift; }

  static uint8_t create(bool is_unloading, uint8_t unloading_cycle) {
    uint8_t state = 0;
    state = set_is_unloading(state, is_unloading);
    state = set_unloading_cycle(state, unloading_cycle);
    return state;
  }
};

bool nmethod::is_unloading() {
  uint8_t state = Atomic::load(&_is_unloading_state);
  bool state_is_unloading = IsUnloadingState::is_unloading(state);
  if (state_is_unloading) {
    return true;
  }
  uint8_t state_unloading_cycle = IsUnloadingState::unloading_cycle(state);
  uint8_t current_cycle = CodeCache::unloading_cycle();
  if (state_unloading_cycle == current_cycle) {
    return false;
  }

  // The IsUnloadingBehaviour is responsible for calculating if the nmethod
  // should be unloaded. This can be either because there is a dead oop,
  // or because is_cold() heuristically determines it is time to unload.
  state_unloading_cycle = current_cycle;
  state_is_unloading = IsUnloadingBehaviour::is_unloading(this);
  uint8_t new_state = IsUnloadingState::create(state_is_unloading, state_unloading_cycle);

  // Note that if an nmethod has dead oops, everyone will agree that the
  // nmethod is_unloading. However, the is_cold heuristics can yield
  // different outcomes, so we guard the computed result with a CAS
  // to ensure all threads have a shared view of whether an nmethod
  // is_unloading or not.
  uint8_t found_state = Atomic::cmpxchg(&_is_unloading_state, state, new_state, memory_order_relaxed);

  if (found_state == state) {
    // First to change state, we win
    return state_is_unloading;
  } else {
    // State already set, so use it
    return IsUnloadingState::is_unloading(found_state);
  }
}

void nmethod::clear_unloading_state() {
  uint8_t state = IsUnloadingState::create(false, CodeCache::unloading_cycle());
  Atomic::store(&_is_unloading_state, state);
}


// This is called at the end of the strong tracing/marking phase of a
// GC to unload an nmethod if it contains otherwise unreachable
// oops or is heuristically found to be not important.
void nmethod::do_unloading(bool unloading_occurred) {
  // Make sure the oop's ready to receive visitors
  if (is_unloading()) {
    unlink();
  } else {
    unload_nmethod_caches(unloading_occurred);
    BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
    if (bs_nm != nullptr) {
      bs_nm->disarm(this);
    }
  }
}

void nmethod::oops_do(OopClosure* f) {
  // Prevent extra code cache walk for platforms that don't have immediate oops.
  if (relocInfo::mustIterateImmediateOopsInCode()) {
    RelocIterator iter(this, oops_reloc_begin());

    while (iter.next()) {
      if (iter.type() == relocInfo::oop_type ) {
        oop_Relocation* r = iter.oop_reloc();
        // In this loop, we must only follow those oops directly embedded in
        // the code.  Other oops (oop_index>0) are seen as part of scopes_oops.
        assert(1 == (r->oop_is_immediate()) +
               (r->oop_addr() >= oops_begin() && r->oop_addr() < oops_end()),
               "oop must be found in exactly one place");
        if (r->oop_is_immediate() && r->oop_value() != nullptr) {
          f->do_oop(r->oop_addr());
        }
      }
    }
  }

  // Scopes
  // This includes oop constants not inlined in the code stream.
  for (oop* p = oops_begin(); p < oops_end(); p++) {
    if (*p == Universe::non_oop_word())  continue;  // skip non-oops
    f->do_oop(p);
  }
}

void nmethod::follow_nmethod(OopIterateClosure* cl) {
  // Process oops in the nmethod
  oops_do(cl);

  // CodeCache unloading support
  mark_as_maybe_on_stack();

  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();
  bs_nm->disarm(this);

  // There's an assumption made that this function is not used by GCs that
  // relocate objects, and therefore we don't call fix_oop_relocations.
}

nmethod* volatile nmethod::_oops_do_mark_nmethods;

void nmethod::oops_do_log_change(const char* state) {
  LogTarget(Trace, gc, nmethod) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    CompileTask::print(&ls, this, state, true /* short_form */);
  }
}

bool nmethod::oops_do_try_claim() {
  if (oops_do_try_claim_weak_request()) {
    nmethod* result = oops_do_try_add_to_list_as_weak_done();
    assert(result == nullptr, "adding to global list as weak done must always succeed.");
    return true;
  }
  return false;
}

bool nmethod::oops_do_try_claim_weak_request() {
  assert(SafepointSynchronize::is_at_safepoint(), "only at safepoint");

  if ((_oops_do_mark_link == nullptr) &&
      (Atomic::replace_if_null(&_oops_do_mark_link, mark_link(this, claim_weak_request_tag)))) {
    oops_do_log_change("oops_do, mark weak request");
    return true;
  }
  return false;
}

void nmethod::oops_do_set_strong_done(nmethod* old_head) {
  _oops_do_mark_link = mark_link(old_head, claim_strong_done_tag);
}

nmethod::oops_do_mark_link* nmethod::oops_do_try_claim_strong_done() {
  assert(SafepointSynchronize::is_at_safepoint(), "only at safepoint");

  oops_do_mark_link* old_next = Atomic::cmpxchg(&_oops_do_mark_link, mark_link(nullptr, claim_weak_request_tag), mark_link(this, claim_strong_done_tag));
  if (old_next == nullptr) {
    oops_do_log_change("oops_do, mark strong done");
  }
  return old_next;
}

nmethod::oops_do_mark_link* nmethod::oops_do_try_add_strong_request(nmethod::oops_do_mark_link* next) {
  assert(SafepointSynchronize::is_at_safepoint(), "only at safepoint");
  assert(next == mark_link(this, claim_weak_request_tag), "Should be claimed as weak");

  oops_do_mark_link* old_next = Atomic::cmpxchg(&_oops_do_mark_link, next, mark_link(this, claim_strong_request_tag));
  if (old_next == next) {
    oops_do_log_change("oops_do, mark strong request");
  }
  return old_next;
}

bool nmethod::oops_do_try_claim_weak_done_as_strong_done(nmethod::oops_do_mark_link* next) {
  assert(SafepointSynchronize::is_at_safepoint(), "only at safepoint");
  assert(extract_state(next) == claim_weak_done_tag, "Should be claimed as weak done");

  oops_do_mark_link* old_next = Atomic::cmpxchg(&_oops_do_mark_link, next, mark_link(extract_nmethod(next), claim_strong_done_tag));
  if (old_next == next) {
    oops_do_log_change("oops_do, mark weak done -> mark strong done");
    return true;
  }
  return false;
}

nmethod* nmethod::oops_do_try_add_to_list_as_weak_done() {
  assert(SafepointSynchronize::is_at_safepoint(), "only at safepoint");

  assert(extract_state(_oops_do_mark_link) == claim_weak_request_tag ||
         extract_state(_oops_do_mark_link) == claim_strong_request_tag,
         "must be but is nmethod " PTR_FORMAT " %u", p2i(extract_nmethod(_oops_do_mark_link)), extract_state(_oops_do_mark_link));

  nmethod* old_head = Atomic::xchg(&_oops_do_mark_nmethods, this);
  // Self-loop if needed.
  if (old_head == nullptr) {
    old_head = this;
  }
  // Try to install end of list and weak done tag.
  if (Atomic::cmpxchg(&_oops_do_mark_link, mark_link(this, claim_weak_request_tag), mark_link(old_head, claim_weak_done_tag)) == mark_link(this, claim_weak_request_tag)) {
    oops_do_log_change("oops_do, mark weak done");
    return nullptr;
  } else {
    return old_head;
  }
}

void nmethod::oops_do_add_to_list_as_strong_done() {
  assert(SafepointSynchronize::is_at_safepoint(), "only at safepoint");

  nmethod* old_head = Atomic::xchg(&_oops_do_mark_nmethods, this);
  // Self-loop if needed.
  if (old_head == nullptr) {
    old_head = this;
  }
  assert(_oops_do_mark_link == mark_link(this, claim_strong_done_tag), "must be but is nmethod " PTR_FORMAT " state %u",
         p2i(extract_nmethod(_oops_do_mark_link)), extract_state(_oops_do_mark_link));

  oops_do_set_strong_done(old_head);
}

void nmethod::oops_do_process_weak(OopsDoProcessor* p) {
  if (!oops_do_try_claim_weak_request()) {
    // Failed to claim for weak processing.
    oops_do_log_change("oops_do, mark weak request fail");
    return;
  }

  p->do_regular_processing(this);

  nmethod* old_head = oops_do_try_add_to_list_as_weak_done();
  if (old_head == nullptr) {
    return;
  }
  oops_do_log_change("oops_do, mark weak done fail");
  // Adding to global list failed, another thread added a strong request.
  assert(extract_state(_oops_do_mark_link) == claim_strong_request_tag,
         "must be but is %u", extract_state(_oops_do_mark_link));

  oops_do_log_change("oops_do, mark weak request -> mark strong done");

  oops_do_set_strong_done(old_head);
  // Do missing strong processing.
  p->do_remaining_strong_processing(this);
}

void nmethod::oops_do_process_strong(OopsDoProcessor* p) {
  oops_do_mark_link* next_raw = oops_do_try_claim_strong_done();
  if (next_raw == nullptr) {
    p->do_regular_processing(this);
    oops_do_add_to_list_as_strong_done();
    return;
  }
  // Claim failed. Figure out why and handle it.
  if (oops_do_has_weak_request(next_raw)) {
    oops_do_mark_link* old = next_raw;
    // Claim failed because being weak processed (state == "weak request").
    // Try to request deferred strong processing.
    next_raw = oops_do_try_add_strong_request(old);
    if (next_raw == old) {
      // Successfully requested deferred strong processing.
      return;
    }
    // Failed because of a concurrent transition. No longer in "weak request" state.
  }
  if (oops_do_has_any_strong_state(next_raw)) {
    // Already claimed for strong processing or requested for such.
    return;
  }
  if (oops_do_try_claim_weak_done_as_strong_done(next_raw)) {
    // Successfully claimed "weak done" as "strong done". Do the missing marking.
    p->do_remaining_strong_processing(this);
    return;
  }
  // Claim failed, some other thread got it.
}

void nmethod::oops_do_marking_prologue() {
  assert_at_safepoint();

  log_trace(gc, nmethod)("oops_do_marking_prologue");
  assert(_oops_do_mark_nmethods == nullptr, "must be empty");
}

void nmethod::oops_do_marking_epilogue() {
  assert_at_safepoint();

  nmethod* next = _oops_do_mark_nmethods;
  _oops_do_mark_nmethods = nullptr;
  if (next != nullptr) {
    nmethod* cur;
    do {
      cur = next;
      next = extract_nmethod(cur->_oops_do_mark_link);
      cur->_oops_do_mark_link = nullptr;
      DEBUG_ONLY(cur->verify_oop_relocations());

      LogTarget(Trace, gc, nmethod) lt;
      if (lt.is_enabled()) {
        LogStream ls(lt);
        CompileTask::print(&ls, cur, "oops_do, unmark", /*short_form:*/ true);
      }
      // End if self-loop has been detected.
    } while (cur != next);
  }
  log_trace(gc, nmethod)("oops_do_marking_epilogue");
}

inline bool includes(void* p, void* from, void* to) {
  return from <= p && p < to;
}


void nmethod::copy_scopes_pcs(PcDesc* pcs, int count) {
  assert(count >= 2, "must be sentinel values, at least");

#ifdef ASSERT
  // must be sorted and unique; we do a binary search in find_pc_desc()
  int prev_offset = pcs[0].pc_offset();
  assert(prev_offset == PcDesc::lower_offset_limit,
         "must start with a sentinel");
  for (int i = 1; i < count; i++) {
    int this_offset = pcs[i].pc_offset();
    assert(this_offset > prev_offset, "offsets must be sorted");
    prev_offset = this_offset;
  }
  assert(prev_offset == PcDesc::upper_offset_limit,
         "must end with a sentinel");
#endif //ASSERT

  // Search for MethodHandle invokes and tag the nmethod.
  for (int i = 0; i < count; i++) {
    if (pcs[i].is_method_handle_invoke()) {
      set_has_method_handle_invokes(true);
      break;
    }
  }
  assert(has_method_handle_invokes() == (_deopt_mh_handler_offset != -1), "must have deopt mh handler");

  int size = count * sizeof(PcDesc);
  assert(scopes_pcs_size() >= size, "oob");
  memcpy(scopes_pcs_begin(), pcs, size);

  // Adjust the final sentinel downward.
  PcDesc* last_pc = &scopes_pcs_begin()[count-1];
  assert(last_pc->pc_offset() == PcDesc::upper_offset_limit, "sanity");
  last_pc->set_pc_offset(content_size() + 1);
  for (; last_pc + 1 < scopes_pcs_end(); last_pc += 1) {
    // Fill any rounding gaps with copies of the last record.
    last_pc[1] = last_pc[0];
  }
  // The following assert could fail if sizeof(PcDesc) is not
  // an integral multiple of oopSize (the rounding term).
  // If it fails, change the logic to always allocate a multiple
  // of sizeof(PcDesc), and fill unused words with copies of *last_pc.
  assert(last_pc + 1 == scopes_pcs_end(), "must match exactly");
}

void nmethod::copy_scopes_data(u_char* buffer, int size) {
  assert(scopes_data_size() >= size, "oob");
  memcpy(scopes_data_begin(), buffer, size);
}

#ifdef ASSERT
static PcDesc* linear_search(int pc_offset, bool approximate, PcDesc* lower, PcDesc* upper) {
  PcDesc* res = nullptr;
  assert(lower != nullptr && lower->pc_offset() == PcDesc::lower_offset_limit,
         "must start with a sentinel");
  // lower + 1 to exclude initial sentinel
  for (PcDesc* p = lower + 1; p < upper; p++) {
    NOT_PRODUCT(--pc_nmethod_stats.pc_desc_tests);  // don't count this call to match_desc
    if (match_desc(p, pc_offset, approximate)) {
      if (res == nullptr) {
        res = p;
      } else {
        res = (PcDesc*) badAddress;
      }
    }
  }
  return res;
}
#endif


#ifndef PRODUCT
// Version of method to collect statistic
PcDesc* PcDescContainer::find_pc_desc(address pc, bool approximate, address code_begin,
                                      PcDesc* lower, PcDesc* upper) {
  ++pc_nmethod_stats.pc_desc_queries;
  if (approximate) ++pc_nmethod_stats.pc_desc_approx;

  PcDesc* desc = _pc_desc_cache.last_pc_desc();
  assert(desc != nullptr, "PcDesc cache should be initialized already");
  if (desc->pc_offset() == (pc - code_begin)) {
    // Cached value matched
    ++pc_nmethod_stats.pc_desc_tests;
    ++pc_nmethod_stats.pc_desc_repeats;
    return desc;
  }
  return find_pc_desc_internal(pc, approximate, code_begin, lower, upper);
}
#endif

// Finds a PcDesc with real-pc equal to "pc"
PcDesc* PcDescContainer::find_pc_desc_internal(address pc, bool approximate, address code_begin,
                                               PcDesc* lower_incl, PcDesc* upper_incl) {
  if ((pc < code_begin) ||
      (pc - code_begin) >= (ptrdiff_t) PcDesc::upper_offset_limit) {
    return nullptr;  // PC is wildly out of range
  }
  int pc_offset = (int) (pc - code_begin);

  // Check the PcDesc cache if it contains the desired PcDesc
  // (This as an almost 100% hit rate.)
  PcDesc* res = _pc_desc_cache.find_pc_desc(pc_offset, approximate);
  if (res != nullptr) {
    assert(res == linear_search(pc_offset, approximate, lower_incl, upper_incl), "cache ok");
    return res;
  }

  // Fallback algorithm: quasi-linear search for the PcDesc
  // Find the last pc_offset less than the given offset.
  // The successor must be the required match, if there is a match at all.
  // (Use a fixed radix to avoid expensive affine pointer arithmetic.)
  PcDesc* lower = lower_incl;     // this is initial sentinel
  PcDesc* upper = upper_incl - 1; // exclude final sentinel
  if (lower >= upper)  return nullptr;  // no PcDescs at all

#define assert_LU_OK \
  /* invariant on lower..upper during the following search: */ \
  assert(lower->pc_offset() <  pc_offset, "sanity"); \
  assert(upper->pc_offset() >= pc_offset, "sanity")
  assert_LU_OK;

  // Use the last successful return as a split point.
  PcDesc* mid = _pc_desc_cache.last_pc_desc();
  NOT_PRODUCT(++pc_nmethod_stats.pc_desc_searches);
  if (mid->pc_offset() < pc_offset) {
    lower = mid;
  } else {
    upper = mid;
  }

  // Take giant steps at first (4096, then 256, then 16, then 1)
  const int LOG2_RADIX = 4 /*smaller steps in debug mode:*/ DEBUG_ONLY(-1);
  const int RADIX = (1 << LOG2_RADIX);
  for (int step = (1 << (LOG2_RADIX*3)); step > 1; step >>= LOG2_RADIX) {
    while ((mid = lower + step) < upper) {
      assert_LU_OK;
      NOT_PRODUCT(++pc_nmethod_stats.pc_desc_searches);
      if (mid->pc_offset() < pc_offset) {
        lower = mid;
      } else {
        upper = mid;
        break;
      }
    }
    assert_LU_OK;
  }

  // Sneak up on the value with a linear search of length ~16.
  while (true) {
    assert_LU_OK;
    mid = lower + 1;
    NOT_PRODUCT(++pc_nmethod_stats.pc_desc_searches);
    if (mid->pc_offset() < pc_offset) {
      lower = mid;
    } else {
      upper = mid;
      break;
    }
  }
#undef assert_LU_OK

  if (match_desc(upper, pc_offset, approximate)) {
    assert(upper == linear_search(pc_offset, approximate, lower_incl, upper_incl), "search mismatch");
    if (!Thread::current_in_asgct()) {
      // we don't want to modify the cache if we're in ASGCT
      // which is typically called in a signal handler
      _pc_desc_cache.add_pc_desc(upper);
    }
    return upper;
  } else {
    assert(nullptr == linear_search(pc_offset, approximate, lower_incl, upper_incl), "search mismatch");
    return nullptr;
  }
}

bool nmethod::check_dependency_on(DepChange& changes) {
  // What has happened:
  // 1) a new class dependee has been added
  // 2) dependee and all its super classes have been marked
  bool found_check = false;  // set true if we are upset
  for (Dependencies::DepStream deps(this); deps.next(); ) {
    // Evaluate only relevant dependencies.
    if (deps.spot_check_dependency_at(changes) != nullptr) {
      found_check = true;
      NOT_DEBUG(break);
    }
  }
  return found_check;
}

// Called from mark_for_deoptimization, when dependee is invalidated.
bool nmethod::is_dependent_on_method(Method* dependee) {
  for (Dependencies::DepStream deps(this); deps.next(); ) {
    if (deps.type() != Dependencies::evol_method)
      continue;
    Method* method = deps.method_argument(0);
    if (method == dependee) return true;
  }
  return false;
}

void nmethod_init() {
  // make sure you didn't forget to adjust the filler fields
  assert(sizeof(nmethod) % oopSize == 0, "nmethod size must be multiple of a word");
}

// -----------------------------------------------------------------------------
// Verification

class VerifyOopsClosure: public OopClosure {
  nmethod* _nm;
  bool     _ok;
public:
  VerifyOopsClosure(nmethod* nm) : _nm(nm), _ok(true) { }
  bool ok() { return _ok; }
  virtual void do_oop(oop* p) {
    if (oopDesc::is_oop_or_null(*p)) return;
    // Print diagnostic information before calling print_nmethod().
    // Assertions therein might prevent call from returning.
    tty->print_cr("*** non-oop " PTR_FORMAT " found at " PTR_FORMAT " (offset %d)",
                  p2i(*p), p2i(p), (int)((intptr_t)p - (intptr_t)_nm));
    if (_ok) {
      _nm->print_nmethod(true);
      _ok = false;
    }
  }
  virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }
};

class VerifyMetadataClosure: public MetadataClosure {
 public:
  void do_metadata(Metadata* md) {
    if (md->is_method()) {
      Method* method = (Method*)md;
      assert(!method->is_old(), "Should not be installing old methods");
    }
  }
};


void nmethod::verify() {
  if (is_not_entrant())
    return;

  // assert(oopDesc::is_oop(method()), "must be valid");

  ResourceMark rm;

  if (!CodeCache::contains(this)) {
    fatal("nmethod at " INTPTR_FORMAT " not in zone", p2i(this));
  }

  if(is_native_method() )
    return;

  nmethod* nm = CodeCache::find_nmethod(verified_entry_point());
  if (nm != this) {
    fatal("find_nmethod did not find this nmethod (" INTPTR_FORMAT ")", p2i(this));
  }

  for (PcDesc* p = scopes_pcs_begin(); p < scopes_pcs_end(); p++) {
    if (! p->verify(this)) {
      tty->print_cr("\t\tin nmethod at " INTPTR_FORMAT " (pcs)", p2i(this));
    }
  }

#ifdef ASSERT
#if INCLUDE_JVMCI
  {
    // Verify that implicit exceptions that deoptimize have a PcDesc and OopMap
    ImmutableOopMapSet* oms = oop_maps();
    ImplicitExceptionTable implicit_table(this);
    for (uint i = 0; i < implicit_table.len(); i++) {
      int exec_offset = (int) implicit_table.get_exec_offset(i);
      if (implicit_table.get_exec_offset(i) == implicit_table.get_cont_offset(i)) {
        assert(pc_desc_at(code_begin() + exec_offset) != nullptr, "missing PcDesc");
        bool found = false;
        for (int i = 0, imax = oms->count(); i < imax; i++) {
          if (oms->pair_at(i)->pc_offset() == exec_offset) {
            found = true;
            break;
          }
        }
        assert(found, "missing oopmap");
      }
    }
  }
#endif
#endif

  VerifyOopsClosure voc(this);
  oops_do(&voc);
  assert(voc.ok(), "embedded oops must be OK");
  Universe::heap()->verify_nmethod(this);

  assert(_oops_do_mark_link == nullptr, "_oops_do_mark_link for %s should be nullptr but is " PTR_FORMAT,
         nm->method()->external_name(), p2i(_oops_do_mark_link));
  verify_scopes();

  CompiledICLocker nm_verify(this);
  VerifyMetadataClosure vmc;
  metadata_do(&vmc);
}


void nmethod::verify_interrupt_point(address call_site, bool is_inline_cache) {

  // Verify IC only when nmethod installation is finished.
  if (!is_not_installed()) {
    if (CompiledICLocker::is_safe(this)) {
      if (is_inline_cache) {
        CompiledIC_at(this, call_site);
      } else {
        CompiledDirectCall::at(call_site);
      }
    } else {
      CompiledICLocker ml_verify(this);
      if (is_inline_cache) {
        CompiledIC_at(this, call_site);
      } else {
        CompiledDirectCall::at(call_site);
      }
    }
  }

  HandleMark hm(Thread::current());

  PcDesc* pd = pc_desc_at(nativeCall_at(call_site)->return_address());
  assert(pd != nullptr, "PcDesc must exist");
  for (ScopeDesc* sd = new ScopeDesc(this, pd);
       !sd->is_top(); sd = sd->sender()) {
    sd->verify();
  }
}

void nmethod::verify_scopes() {
  if( !method() ) return;       // Runtime stubs have no scope
  if (method()->is_native()) return; // Ignore stub methods.
  // iterate through all interrupt point
  // and verify the debug information is valid.
  RelocIterator iter(this);
  while (iter.next()) {
    address stub = nullptr;
    switch (iter.type()) {
      case relocInfo::virtual_call_type:
        verify_interrupt_point(iter.addr(), true /* is_inline_cache */);
        break;
      case relocInfo::opt_virtual_call_type:
        stub = iter.opt_virtual_call_reloc()->static_stub();
        verify_interrupt_point(iter.addr(), false /* is_inline_cache */);
        break;
      case relocInfo::static_call_type:
        stub = iter.static_call_reloc()->static_stub();
        verify_interrupt_point(iter.addr(), false /* is_inline_cache */);
        break;
      case relocInfo::runtime_call_type:
      case relocInfo::runtime_call_w_cp_type: {
        address destination = iter.reloc()->value();
        // Right now there is no way to find out which entries support
        // an interrupt point.  It would be nice if we had this
        // information in a table.
        break;
      }
      default:
        break;
    }
    assert(stub == nullptr || stub_contains(stub), "static call stub outside stub section");
  }
}


// -----------------------------------------------------------------------------
// Printing operations

void nmethod::print_on_impl(outputStream* st) const {
  ResourceMark rm;

  st->print("Compiled method ");

  if (is_compiled_by_c1()) {
    st->print("(c1) ");
  } else if (is_compiled_by_c2()) {
    st->print("(c2) ");
  } else if (is_compiled_by_jvmci()) {
    st->print("(JVMCI) ");
  } else {
    st->print("(n/a) ");
  }

  print_on_with_msg(st, nullptr);

  if (WizardMode) {
    st->print("((nmethod*) " INTPTR_FORMAT ") ", p2i(this));
    st->print(" for method " INTPTR_FORMAT , p2i(method()));
    st->print(" { ");
    st->print_cr("%s ", state());
    st->print_cr("}:");
  }
  if (size              () > 0) st->print_cr(" total in heap  [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(this),
                                             p2i(this) + size(),
                                             size());
  if (consts_size       () > 0) st->print_cr(" constants      [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(consts_begin()),
                                             p2i(consts_end()),
                                             consts_size());
  if (insts_size        () > 0) st->print_cr(" main code      [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(insts_begin()),
                                             p2i(insts_end()),
                                             insts_size());
  if (stub_size         () > 0) st->print_cr(" stub code      [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(stub_begin()),
                                             p2i(stub_end()),
                                             stub_size());
  if (oops_size         () > 0) st->print_cr(" oops           [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(oops_begin()),
                                             p2i(oops_end()),
                                             oops_size());
  if (mutable_data_size() > 0) st->print_cr(" mutable data [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(mutable_data_begin()),
                                             p2i(mutable_data_end()),
                                             mutable_data_size());
  if (relocation_size() > 0)   st->print_cr(" relocation     [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(relocation_begin()),
                                             p2i(relocation_end()),
                                             relocation_size());
  if (metadata_size     () > 0) st->print_cr(" metadata       [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(metadata_begin()),
                                             p2i(metadata_end()),
                                             metadata_size());
#if INCLUDE_JVMCI
  if (jvmci_data_size   () > 0) st->print_cr(" JVMCI data     [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(jvmci_data_begin()),
                                             p2i(jvmci_data_end()),
                                             jvmci_data_size());
#endif
  if (immutable_data_size() > 0) st->print_cr(" immutable data [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(immutable_data_begin()),
                                             p2i(immutable_data_end()),
                                             immutable_data_size());
  if (dependencies_size () > 0) st->print_cr(" dependencies   [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(dependencies_begin()),
                                             p2i(dependencies_end()),
                                             dependencies_size());
  if (nul_chk_table_size() > 0) st->print_cr(" nul chk table  [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(nul_chk_table_begin()),
                                             p2i(nul_chk_table_end()),
                                             nul_chk_table_size());
  if (handler_table_size() > 0) st->print_cr(" handler table  [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(handler_table_begin()),
                                             p2i(handler_table_end()),
                                             handler_table_size());
  if (scopes_pcs_size   () > 0) st->print_cr(" scopes pcs     [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(scopes_pcs_begin()),
                                             p2i(scopes_pcs_end()),
                                             scopes_pcs_size());
  if (scopes_data_size  () > 0) st->print_cr(" scopes data    [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(scopes_data_begin()),
                                             p2i(scopes_data_end()),
                                             scopes_data_size());
#if INCLUDE_JVMCI
  if (speculations_size () > 0) st->print_cr(" speculations   [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                             p2i(speculations_begin()),
                                             p2i(speculations_end()),
                                             speculations_size());
#endif
}

void nmethod::print_code() {
  ResourceMark m;
  ttyLocker ttyl;
  // Call the specialized decode method of this class.
  decode(tty);
}

#ifndef PRODUCT  // called InstanceKlass methods are available only then. Declared as PRODUCT_RETURN

void nmethod::print_dependencies_on(outputStream* out) {
  ResourceMark rm;
  stringStream st;
  st.print_cr("Dependencies:");
  for (Dependencies::DepStream deps(this); deps.next(); ) {
    deps.print_dependency(&st);
    InstanceKlass* ctxk = deps.context_type();
    if (ctxk != nullptr) {
      if (ctxk->is_dependent_nmethod(this)) {
        st.print_cr("   [nmethod<=klass]%s", ctxk->external_name());
      }
    }
    deps.log_dependency();  // put it into the xml log also
  }
  out->print_raw(st.as_string());
}
#endif

#if defined(SUPPORT_DATA_STRUCTS)

// Print the oops from the underlying CodeBlob.
void nmethod::print_oops(outputStream* st) {
  ResourceMark m;
  st->print("Oops:");
  if (oops_begin() < oops_end()) {
    st->cr();
    for (oop* p = oops_begin(); p < oops_end(); p++) {
      Disassembler::print_location((unsigned char*)p, (unsigned char*)oops_begin(), (unsigned char*)oops_end(), st, true, false);
      st->print(PTR_FORMAT " ", *((uintptr_t*)p));
      if (Universe::contains_non_oop_word(p)) {
        st->print_cr("NON_OOP");
        continue;  // skip non-oops
      }
      if (*p == nullptr) {
        st->print_cr("nullptr-oop");
        continue;  // skip non-oops
      }
      (*p)->print_value_on(st);
      st->cr();
    }
  } else {
    st->print_cr(" <list empty>");
  }
}

// Print metadata pool.
void nmethod::print_metadata(outputStream* st) {
  ResourceMark m;
  st->print("Metadata:");
  if (metadata_begin() < metadata_end()) {
    st->cr();
    for (Metadata** p = metadata_begin(); p < metadata_end(); p++) {
      Disassembler::print_location((unsigned char*)p, (unsigned char*)metadata_begin(), (unsigned char*)metadata_end(), st, true, false);
      st->print(PTR_FORMAT " ", *((uintptr_t*)p));
      if (*p && *p != Universe::non_oop_word()) {
        (*p)->print_value_on(st);
      }
      st->cr();
    }
  } else {
    st->print_cr(" <list empty>");
  }
}

#ifndef PRODUCT  // ScopeDesc::print_on() is available only then. Declared as PRODUCT_RETURN
void nmethod::print_scopes_on(outputStream* st) {
  // Find the first pc desc for all scopes in the code and print it.
  ResourceMark rm;
  st->print("scopes:");
  if (scopes_pcs_begin() < scopes_pcs_end()) {
    st->cr();
    for (PcDesc* p = scopes_pcs_begin(); p < scopes_pcs_end(); p++) {
      if (p->scope_decode_offset() == DebugInformationRecorder::serialized_null)
        continue;

      ScopeDesc* sd = scope_desc_at(p->real_pc(this));
      while (sd != nullptr) {
        sd->print_on(st, p);  // print output ends with a newline
        sd = sd->sender();
      }
    }
  } else {
    st->print_cr(" <list empty>");
  }
}
#endif

#ifndef PRODUCT  // RelocIterator does support printing only then.
void nmethod::print_relocations() {
  ResourceMark m;       // in case methods get printed via the debugger
  tty->print_cr("relocations:");
  RelocIterator iter(this);
  iter.print_on(tty);
}
#endif

void nmethod::print_pcs_on(outputStream* st) {
  ResourceMark m;       // in case methods get printed via debugger
  st->print("pc-bytecode offsets:");
  if (scopes_pcs_begin() < scopes_pcs_end()) {
    st->cr();
    for (PcDesc* p = scopes_pcs_begin(); p < scopes_pcs_end(); p++) {
      p->print_on(st, this);  // print output ends with a newline
    }
  } else {
    st->print_cr(" <list empty>");
  }
}

void nmethod::print_handler_table() {
  ExceptionHandlerTable(this).print(code_begin());
}

void nmethod::print_nul_chk_table() {
  ImplicitExceptionTable(this).print(code_begin());
}

void nmethod::print_recorded_oop(int log_n, int i) {
  void* value;

  if (i == 0) {
    value = nullptr;
  } else {
    // Be careful around non-oop words. Don't create an oop
    // with that value, or it will assert in verification code.
    if (Universe::contains_non_oop_word(oop_addr_at(i))) {
      value = Universe::non_oop_word();
    } else {
      value = oop_at(i);
    }
  }

  tty->print("#%*d: " INTPTR_FORMAT " ", log_n, i, p2i(value));

  if (value == Universe::non_oop_word()) {
    tty->print("non-oop word");
  } else {
    if (value == nullptr) {
      tty->print("nullptr-oop");
    } else {
      oop_at(i)->print_value_on(tty);
    }
  }

  tty->cr();
}

void nmethod::print_recorded_oops() {
  const int n = oops_count();
  const int log_n = (n<10) ? 1 : (n<100) ? 2 : (n<1000) ? 3 : (n<10000) ? 4 : 6;
  tty->print("Recorded oops:");
  if (n > 0) {
    tty->cr();
    for (int i = 0; i < n; i++) {
      print_recorded_oop(log_n, i);
    }
  } else {
    tty->print_cr(" <list empty>");
  }
}

void nmethod::print_recorded_metadata() {
  const int n = metadata_count();
  const int log_n = (n<10) ? 1 : (n<100) ? 2 : (n<1000) ? 3 : (n<10000) ? 4 : 6;
  tty->print("Recorded metadata:");
  if (n > 0) {
    tty->cr();
    for (int i = 0; i < n; i++) {
      Metadata* m = metadata_at(i);
      tty->print("#%*d: " INTPTR_FORMAT " ", log_n, i, p2i(m));
      if (m == (Metadata*)Universe::non_oop_word()) {
        tty->print("non-metadata word");
      } else if (m == nullptr) {
        tty->print("nullptr-oop");
      } else {
        Metadata::print_value_on_maybe_null(tty, m);
      }
      tty->cr();
    }
  } else {
    tty->print_cr(" <list empty>");
  }
}
#endif

#if defined(SUPPORT_ASSEMBLY) || defined(SUPPORT_ABSTRACT_ASSEMBLY)

void nmethod::print_constant_pool(outputStream* st) {
  //-----------------------------------
  //---<  Print the constant pool  >---
  //-----------------------------------
  int consts_size = this->consts_size();
  if ( consts_size > 0 ) {
    unsigned char* cstart = this->consts_begin();
    unsigned char* cp     = cstart;
    unsigned char* cend   = cp + consts_size;
    unsigned int   bytes_per_line = 4;
    unsigned int   CP_alignment   = 8;
    unsigned int   n;

    st->cr();

    //---<  print CP header to make clear what's printed  >---
    if( ((uintptr_t)cp&(CP_alignment-1)) == 0 ) {
      n = bytes_per_line;
      st->print_cr("[Constant Pool]");
      Disassembler::print_location(cp, cstart, cend, st, true, true);
      Disassembler::print_hexdata(cp, n, st, true);
      st->cr();
    } else {
      n = (int)((uintptr_t)cp & (bytes_per_line-1));
      st->print_cr("[Constant Pool (unaligned)]");
    }

    //---<  print CP contents, bytes_per_line at a time  >---
    while (cp < cend) {
      Disassembler::print_location(cp, cstart, cend, st, true, false);
      Disassembler::print_hexdata(cp, n, st, false);
      cp += n;
      n   = bytes_per_line;
      st->cr();
    }

    //---<  Show potential alignment gap between constant pool and code  >---
    cend = code_begin();
    if( cp < cend ) {
      n = 4;
      st->print_cr("[Code entry alignment]");
      while (cp < cend) {
        Disassembler::print_location(cp, cstart, cend, st, false, false);
        cp += n;
        st->cr();
      }
    }
  } else {
    st->print_cr("[Constant Pool (empty)]");
  }
  st->cr();
}

#endif

// Disassemble this nmethod.
// Print additional debug information, if requested. This could be code
// comments, block comments, profiling counters, etc.
// The undisassembled format is useful no disassembler library is available.
// The resulting hex dump (with markers) can be disassembled later, or on
// another system, when/where a disassembler library is available.
void nmethod::decode2(outputStream* ost) const {

  // Called from frame::back_trace_with_decode without ResourceMark.
  ResourceMark rm;

  // Make sure we have a valid stream to print on.
  outputStream* st = ost ? ost : tty;

#if defined(SUPPORT_ABSTRACT_ASSEMBLY) && ! defined(SUPPORT_ASSEMBLY)
  const bool use_compressed_format    = true;
  const bool compressed_with_comments = use_compressed_format && (AbstractDisassembler::show_comment() ||
                                                                  AbstractDisassembler::show_block_comment());
#else
  const bool use_compressed_format    = Disassembler::is_abstract();
  const bool compressed_with_comments = use_compressed_format && (AbstractDisassembler::show_comment() ||
                                                                  AbstractDisassembler::show_block_comment());
#endif

  st->cr();
  this->print_on(st);
  st->cr();

#if defined(SUPPORT_ASSEMBLY)
  //----------------------------------
  //---<  Print real disassembly  >---
  //----------------------------------
  if (! use_compressed_format) {
    st->print_cr("[Disassembly]");
    Disassembler::decode(const_cast<nmethod*>(this), st);
    st->bol();
    st->print_cr("[/Disassembly]");
    return;
  }
#endif

#if defined(SUPPORT_ABSTRACT_ASSEMBLY)

  // Compressed undisassembled disassembly format.
  // The following status values are defined/supported:
  //   = 0 - currently at bol() position, nothing printed yet on current line.
  //   = 1 - currently at position after print_location().
  //   > 1 - in the midst of printing instruction stream bytes.
  int        compressed_format_idx    = 0;
  int        code_comment_column      = 0;
  const int  instr_maxlen             = Assembler::instr_maxlen();
  const uint tabspacing               = 8;
  unsigned char* start = this->code_begin();
  unsigned char* p     = this->code_begin();
  unsigned char* end   = this->code_end();
  unsigned char* pss   = p; // start of a code section (used for offsets)

  if ((start == nullptr) || (end == nullptr)) {
    st->print_cr("PrintAssembly not possible due to uninitialized section pointers");
    return;
  }
#endif

#if defined(SUPPORT_ABSTRACT_ASSEMBLY)
  //---<  plain abstract disassembly, no comments or anything, just section headers  >---
  if (use_compressed_format && ! compressed_with_comments) {
    const_cast<nmethod*>(this)->print_constant_pool(st);

    st->bol();
    st->cr();
    st->print_cr("Loading hsdis library failed, undisassembled code is shown in MachCode section");
    //---<  Open the output (Marker for post-mortem disassembler)  >---
    st->print_cr("[MachCode]");
    const char* header = nullptr;
    address p0 = p;
    while (p < end) {
      address pp = p;
      while ((p < end) && (header == nullptr)) {
        header = nmethod_section_label(p);
        pp  = p;
        p  += Assembler::instr_len(p);
      }
      if (pp > p0) {
        AbstractDisassembler::decode_range_abstract(p0, pp, start, end, st, Assembler::instr_maxlen());
        p0 = pp;
        p  = pp;
        header = nullptr;
      } else if (header != nullptr) {
        st->bol();
        st->print_cr("%s", header);
        header = nullptr;
      }
    }
    //---<  Close the output (Marker for post-mortem disassembler)  >---
    st->bol();
    st->print_cr("[/MachCode]");
    return;
  }
#endif

#if defined(SUPPORT_ABSTRACT_ASSEMBLY)
  //---<  abstract disassembly with comments and section headers merged in  >---
  if (compressed_with_comments) {
    const_cast<nmethod*>(this)->print_constant_pool(st);

    st->bol();
    st->cr();
    st->print_cr("Loading hsdis library failed, undisassembled code is shown in MachCode section");
    //---<  Open the output (Marker for post-mortem disassembler)  >---
    st->print_cr("[MachCode]");
    while ((p < end) && (p != nullptr)) {
      const int instruction_size_in_bytes = Assembler::instr_len(p);

      //---<  Block comments for nmethod. Interrupts instruction stream, if any.  >---
      // Outputs a bol() before and a cr() after, but only if a comment is printed.
      // Prints nmethod_section_label as well.
      if (AbstractDisassembler::show_block_comment()) {
        print_block_comment(st, p);
        if (st->position() == 0) {
          compressed_format_idx = 0;
        }
      }

      //---<  New location information after line break  >---
      if (compressed_format_idx == 0) {
        code_comment_column   = Disassembler::print_location(p, pss, end, st, false, false);
        compressed_format_idx = 1;
      }

      //---<  Code comment for current instruction. Address range [p..(p+len))  >---
      unsigned char* p_end = p + (ssize_t)instruction_size_in_bytes;
      S390_ONLY(if (p_end > end) p_end = end;) // avoid getting past the end

      if (AbstractDisassembler::show_comment() && const_cast<nmethod*>(this)->has_code_comment(p, p_end)) {
        //---<  interrupt instruction byte stream for code comment  >---
        if (compressed_format_idx > 1) {
          st->cr();  // interrupt byte stream
          st->cr();  // add an empty line
          code_comment_column = Disassembler::print_location(p, pss, end, st, false, false);
        }
        const_cast<nmethod*>(this)->print_code_comment_on(st, code_comment_column, p, p_end );
        st->bol();
        compressed_format_idx = 0;
      }

      //---<  New location information after line break  >---
      if (compressed_format_idx == 0) {
        code_comment_column   = Disassembler::print_location(p, pss, end, st, false, false);
        compressed_format_idx = 1;
      }

      //---<  Nicely align instructions for readability  >---
      if (compressed_format_idx > 1) {
        Disassembler::print_delimiter(st);
      }

      //---<  Now, finally, print the actual instruction bytes  >---
      unsigned char* p0 = p;
      p = Disassembler::decode_instruction_abstract(p, st, instruction_size_in_bytes, instr_maxlen);
      compressed_format_idx += (int)(p - p0);

      if (Disassembler::start_newline(compressed_format_idx-1)) {
        st->cr();
        compressed_format_idx = 0;
      }
    }
    //---<  Close the output (Marker for post-mortem disassembler)  >---
    st->bol();
    st->print_cr("[/MachCode]");
    return;
  }
#endif
}

#if defined(SUPPORT_ASSEMBLY) || defined(SUPPORT_ABSTRACT_ASSEMBLY)

const char* nmethod::reloc_string_for(u_char* begin, u_char* end) {
  RelocIterator iter(this, begin, end);
  bool have_one = false;
  while (iter.next()) {
    have_one = true;
    switch (iter.type()) {
        case relocInfo::none: {
          // Skip it and check next
          break;
        }
        case relocInfo::oop_type: {
          // Get a non-resizable resource-allocated stringStream.
          // Our callees make use of (nested) ResourceMarks.
          stringStream st(NEW_RESOURCE_ARRAY(char, 1024), 1024);
          oop_Relocation* r = iter.oop_reloc();
          oop obj = r->oop_value();
          st.print("oop(");
          if (obj == nullptr) st.print("nullptr");
          else obj->print_value_on(&st);
          st.print(")");
          return st.as_string();
        }
        case relocInfo::metadata_type: {
          stringStream st;
          metadata_Relocation* r = iter.metadata_reloc();
          Metadata* obj = r->metadata_value();
          st.print("metadata(");
          if (obj == nullptr) st.print("nullptr");
          else obj->print_value_on(&st);
          st.print(")");
          return st.as_string();
        }
        case relocInfo::runtime_call_type:
        case relocInfo::runtime_call_w_cp_type: {
          stringStream st;
          st.print("runtime_call");
          CallRelocation* r = (CallRelocation*)iter.reloc();
          address dest = r->destination();
          if (StubRoutines::contains(dest)) {
            StubCodeDesc* desc = StubCodeDesc::desc_for(dest);
            if (desc == nullptr) {
              desc = StubCodeDesc::desc_for(dest + frame::pc_return_offset);
            }
            if (desc != nullptr) {
              st.print(" Stub::%s", desc->name());
              return st.as_string();
            }
          }
          CodeBlob* cb = CodeCache::find_blob(dest);
          if (cb != nullptr) {
            st.print(" %s", cb->name());
          } else {
            ResourceMark rm;
            const int buflen = 1024;
            char* buf = NEW_RESOURCE_ARRAY(char, buflen);
            int offset;
            if (os::dll_address_to_function_name(dest, buf, buflen, &offset)) {
              st.print(" %s", buf);
              if (offset != 0) {
                st.print("+%d", offset);
              }
            }
          }
          return st.as_string();
        }
        case relocInfo::virtual_call_type: {
          stringStream st;
          st.print_raw("virtual_call");
          virtual_call_Relocation* r = iter.virtual_call_reloc();
          Method* m = r->method_value();
          if (m != nullptr) {
            assert(m->is_method(), "");
            m->print_short_name(&st);
          }
          return st.as_string();
        }
        case relocInfo::opt_virtual_call_type: {
          stringStream st;
          st.print_raw("optimized virtual_call");
          opt_virtual_call_Relocation* r = iter.opt_virtual_call_reloc();
          Method* m = r->method_value();
          if (m != nullptr) {
            assert(m->is_method(), "");
            m->print_short_name(&st);
          }
          return st.as_string();
        }
        case relocInfo::static_call_type: {
          stringStream st;
          st.print_raw("static_call");
          static_call_Relocation* r = iter.static_call_reloc();
          Method* m = r->method_value();
          if (m != nullptr) {
            assert(m->is_method(), "");
            m->print_short_name(&st);
          }
          return st.as_string();
        }
        case relocInfo::static_stub_type:      return "static_stub";
        case relocInfo::external_word_type:    return "external_word";
        case relocInfo::internal_word_type:    return "internal_word";
        case relocInfo::section_word_type:     return "section_word";
        case relocInfo::poll_type:             return "poll";
        case relocInfo::poll_return_type:      return "poll_return";
        case relocInfo::trampoline_stub_type:  return "trampoline_stub";
        case relocInfo::entry_guard_type:      return "entry_guard";
        case relocInfo::post_call_nop_type:    return "post_call_nop";
        case relocInfo::barrier_type: {
          barrier_Relocation* const reloc = iter.barrier_reloc();
          stringStream st;
          st.print("barrier format=%d", reloc->format());
          return st.as_string();
        }

        case relocInfo::type_mask:             return "type_bit_mask";

        default: {
          stringStream st;
          st.print("unknown relocInfo=%d", (int) iter.type());
          return st.as_string();
        }
    }
  }
  return have_one ? "other" : nullptr;
}

// Return the last scope in (begin..end]
ScopeDesc* nmethod::scope_desc_in(address begin, address end) {
  PcDesc* p = pc_desc_near(begin+1);
  if (p != nullptr && p->real_pc(this) <= end) {
    return new ScopeDesc(this, p);
  }
  return nullptr;
}

const char* nmethod::nmethod_section_label(address pos) const {
  const char* label = nullptr;
  if (pos == code_begin())                                              label = "[Instructions begin]";
  if (pos == entry_point())                                             label = "[Entry Point]";
  if (pos == verified_entry_point())                                    label = "[Verified Entry Point]";
  if (has_method_handle_invokes() && (pos == deopt_mh_handler_begin())) label = "[Deopt MH Handler Code]";
  if (pos == consts_begin() && pos != insts_begin())                    label = "[Constants]";
  // Check stub_code before checking exception_handler or deopt_handler.
  if (pos == this->stub_begin())                                        label = "[Stub Code]";
  if (JVMCI_ONLY(_exception_offset >= 0 &&) pos == exception_begin())          label = "[Exception Handler]";
  if (JVMCI_ONLY(_deopt_handler_offset != -1 &&) pos == deopt_handler_begin()) label = "[Deopt Handler Code]";
  return label;
}

void nmethod::print_nmethod_labels(outputStream* stream, address block_begin, bool print_section_labels) const {
  if (print_section_labels) {
    const char* label = nmethod_section_label(block_begin);
    if (label != nullptr) {
      stream->bol();
      stream->print_cr("%s", label);
    }
  }

  if (block_begin == entry_point()) {
    Method* m = method();
    if (m != nullptr) {
      stream->print("  # ");
      m->print_value_on(stream);
      stream->cr();
    }
    if (m != nullptr && !is_osr_method()) {
      ResourceMark rm;
      int sizeargs = m->size_of_parameters();
      BasicType* sig_bt = NEW_RESOURCE_ARRAY(BasicType, sizeargs);
      VMRegPair* regs   = NEW_RESOURCE_ARRAY(VMRegPair, sizeargs);
      {
        int sig_index = 0;
        if (!m->is_static())
          sig_bt[sig_index++] = T_OBJECT; // 'this'
        for (SignatureStream ss(m->signature()); !ss.at_return_type(); ss.next()) {
          BasicType t = ss.type();
          sig_bt[sig_index++] = t;
          if (type2size[t] == 2) {
            sig_bt[sig_index++] = T_VOID;
          } else {
            assert(type2size[t] == 1, "size is 1 or 2");
          }
        }
        assert(sig_index == sizeargs, "");
      }
      const char* spname = "sp"; // make arch-specific?
      SharedRuntime::java_calling_convention(sig_bt, regs, sizeargs);
      int stack_slot_offset = this->frame_size() * wordSize;
      int tab1 = 14, tab2 = 24;
      int sig_index = 0;
      int arg_index = (m->is_static() ? 0 : -1);
      bool did_old_sp = false;
      for (SignatureStream ss(m->signature()); !ss.at_return_type(); ) {
        bool at_this = (arg_index == -1);
        bool at_old_sp = false;
        BasicType t = (at_this ? T_OBJECT : ss.type());
        assert(t == sig_bt[sig_index], "sigs in sync");
        if (at_this)
          stream->print("  # this: ");
        else
          stream->print("  # parm%d: ", arg_index);
        stream->move_to(tab1);
        VMReg fst = regs[sig_index].first();
        VMReg snd = regs[sig_index].second();
        if (fst->is_reg()) {
          stream->print("%s", fst->name());
          if (snd->is_valid())  {
            stream->print(":%s", snd->name());
          }
        } else if (fst->is_stack()) {
          int offset = fst->reg2stack() * VMRegImpl::stack_slot_size + stack_slot_offset;
          if (offset == stack_slot_offset)  at_old_sp = true;
          stream->print("[%s+0x%x]", spname, offset);
        } else {
          stream->print("reg%d:%d??", (int)(intptr_t)fst, (int)(intptr_t)snd);
        }
        stream->print(" ");
        stream->move_to(tab2);
        stream->print("= ");
        if (at_this) {
          m->method_holder()->print_value_on(stream);
        } else {
          bool did_name = false;
          if (!at_this && ss.is_reference()) {
            Symbol* name = ss.as_symbol();
            name->print_value_on(stream);
            did_name = true;
          }
          if (!did_name)
            stream->print("%s", type2name(t));
        }
        if (at_old_sp) {
          stream->print("  (%s of caller)", spname);
          did_old_sp = true;
        }
        stream->cr();
        sig_index += type2size[t];
        arg_index += 1;
        if (!at_this)  ss.next();
      }
      if (!did_old_sp) {
        stream->print("  # ");
        stream->move_to(tab1);
        stream->print("[%s+0x%x]", spname, stack_slot_offset);
        stream->print("  (%s of caller)", spname);
        stream->cr();
      }
    }
  }
}

// Returns whether this nmethod has code comments.
bool nmethod::has_code_comment(address begin, address end) {
  // scopes?
  ScopeDesc* sd  = scope_desc_in(begin, end);
  if (sd != nullptr) return true;

  // relocations?
  const char* str = reloc_string_for(begin, end);
  if (str != nullptr) return true;

  // implicit exceptions?
  int cont_offset = ImplicitExceptionTable(this).continuation_offset((uint)(begin - code_begin()));
  if (cont_offset != 0) return true;

  return false;
}

void nmethod::print_code_comment_on(outputStream* st, int column, address begin, address end) {
  ImplicitExceptionTable implicit_table(this);
  int pc_offset = (int)(begin - code_begin());
  int cont_offset = implicit_table.continuation_offset(pc_offset);
  bool oop_map_required = false;
  if (cont_offset != 0) {
    st->move_to(column, 6, 0);
    if (pc_offset == cont_offset) {
      st->print("; implicit exception: deoptimizes");
      oop_map_required = true;
    } else {
      st->print("; implicit exception: dispatches to " INTPTR_FORMAT, p2i(code_begin() + cont_offset));
    }
  }

  // Find an oopmap in (begin, end].  We use the odd half-closed
  // interval so that oop maps and scope descs which are tied to the
  // byte after a call are printed with the call itself.  OopMaps
  // associated with implicit exceptions are printed with the implicit
  // instruction.
  address base = code_begin();
  ImmutableOopMapSet* oms = oop_maps();
  if (oms != nullptr) {
    for (int i = 0, imax = oms->count(); i < imax; i++) {
      const ImmutableOopMapPair* pair = oms->pair_at(i);
      const ImmutableOopMap* om = pair->get_from(oms);
      address pc = base + pair->pc_offset();
      if (pc >= begin) {
#if INCLUDE_JVMCI
        bool is_implicit_deopt = implicit_table.continuation_offset(pair->pc_offset()) == (uint) pair->pc_offset();
#else
        bool is_implicit_deopt = false;
#endif
        if (is_implicit_deopt ? pc == begin : pc > begin && pc <= end) {
          st->move_to(column, 6, 0);
          st->print("; ");
          om->print_on(st);
          oop_map_required = false;
        }
      }
      if (pc > end) {
        break;
      }
    }
  }
  assert(!oop_map_required, "missed oopmap");

  Thread* thread = Thread::current();

  // Print any debug info present at this pc.
  ScopeDesc* sd  = scope_desc_in(begin, end);
  if (sd != nullptr) {
    st->move_to(column, 6, 0);
    if (sd->bci() == SynchronizationEntryBCI) {
      st->print(";*synchronization entry");
    } else if (sd->bci() == AfterBci) {
      st->print(";* method exit (unlocked if synchronized)");
    } else if (sd->bci() == UnwindBci) {
      st->print(";* unwind (locked if synchronized)");
    } else if (sd->bci() == AfterExceptionBci) {
      st->print(";* unwind (unlocked if synchronized)");
    } else if (sd->bci() == UnknownBci) {
      st->print(";* unknown");
    } else if (sd->bci() == InvalidFrameStateBci) {
      st->print(";* invalid frame state");
    } else {
      if (sd->method() == nullptr) {
        st->print("method is nullptr");
      } else if (sd->method()->is_native()) {
        st->print("method is native");
      } else {
        Bytecodes::Code bc = sd->method()->java_code_at(sd->bci());
        st->print(";*%s", Bytecodes::name(bc));
        switch (bc) {
        case Bytecodes::_invokevirtual:
        case Bytecodes::_invokespecial:
        case Bytecodes::_invokestatic:
        case Bytecodes::_invokeinterface:
          {
            Bytecode_invoke invoke(methodHandle(thread, sd->method()), sd->bci());
            st->print(" ");
            if (invoke.name() != nullptr)
              invoke.name()->print_symbol_on(st);
            else
              st->print("<UNKNOWN>");
            break;
          }
        case Bytecodes::_getfield:
        case Bytecodes::_putfield:
        case Bytecodes::_getstatic:
        case Bytecodes::_putstatic:
          {
            Bytecode_field field(methodHandle(thread, sd->method()), sd->bci());
            st->print(" ");
            if (field.name() != nullptr)
              field.name()->print_symbol_on(st);
            else
              st->print("<UNKNOWN>");
          }
        default:
          break;
        }
      }
      st->print(" {reexecute=%d rethrow=%d return_oop=%d}", sd->should_reexecute(), sd->rethrow_exception(), sd->return_oop());
    }

    // Print all scopes
    for (;sd != nullptr; sd = sd->sender()) {
      st->move_to(column, 6, 0);
      st->print("; -");
      if (sd->should_reexecute()) {
        st->print(" (reexecute)");
      }
      if (sd->method() == nullptr) {
        st->print("method is nullptr");
      } else {
        sd->method()->print_short_name(st);
      }
      int lineno = sd->method()->line_number_from_bci(sd->bci());
      if (lineno != -1) {
        st->print("@%d (line %d)", sd->bci(), lineno);
      } else {
        st->print("@%d", sd->bci());
      }
      st->cr();
    }
  }

  // Print relocation information
  // Prevent memory leak: allocating without ResourceMark.
  ResourceMark rm;
  const char* str = reloc_string_for(begin, end);
  if (str != nullptr) {
    if (sd != nullptr) st->cr();
    st->move_to(column, 6, 0);
    st->print(";   {%s}", str);
  }
}

#endif

address nmethod::call_instruction_address(address pc) const {
  if (NativeCall::is_call_before(pc)) {
    NativeCall *ncall = nativeCall_before(pc);
    return ncall->instruction_address();
  }
  return nullptr;
}

void nmethod::print_value_on_impl(outputStream* st) const {
  st->print_cr("nmethod");
#if defined(SUPPORT_DATA_STRUCTS)
  print_on_with_msg(st, nullptr);
#endif
}

#ifndef PRODUCT

void nmethod::print_calls(outputStream* st) {
  RelocIterator iter(this);
  while (iter.next()) {
    switch (iter.type()) {
    case relocInfo::virtual_call_type: {
      CompiledICLocker ml_verify(this);
      CompiledIC_at(&iter)->print();
      break;
    }
    case relocInfo::static_call_type:
    case relocInfo::opt_virtual_call_type:
      st->print_cr("Direct call at " INTPTR_FORMAT, p2i(iter.reloc()->addr()));
      CompiledDirectCall::at(iter.reloc())->print();
      break;
    default:
      break;
    }
  }
}

void nmethod::print_statistics() {
  ttyLocker ttyl;
  if (xtty != nullptr)  xtty->head("statistics type='nmethod'");
  native_nmethod_stats.print_native_nmethod_stats();
#ifdef COMPILER1
  c1_java_nmethod_stats.print_nmethod_stats("C1");
#endif
#ifdef COMPILER2
  c2_java_nmethod_stats.print_nmethod_stats("C2");
#endif
#if INCLUDE_JVMCI
  jvmci_java_nmethod_stats.print_nmethod_stats("JVMCI");
#endif
  unknown_java_nmethod_stats.print_nmethod_stats("Unknown");
  DebugInformationRecorder::print_statistics();
  pc_nmethod_stats.print_pc_stats();
  Dependencies::print_statistics();
  ExternalsRecorder::print_statistics();
  if (xtty != nullptr)  xtty->tail("statistics");
}

#endif // !PRODUCT

#if INCLUDE_JVMCI
void nmethod::update_speculation(JavaThread* thread) {
  jlong speculation = thread->pending_failed_speculation();
  if (speculation != 0) {
    guarantee(jvmci_nmethod_data() != nullptr, "failed speculation in nmethod without failed speculation list");
    jvmci_nmethod_data()->add_failed_speculation(this, speculation);
    thread->set_pending_failed_speculation(0);
  }
}

const char* nmethod::jvmci_name() {
  if (jvmci_nmethod_data() != nullptr) {
    return jvmci_nmethod_data()->name();
  }
  return nullptr;
}

bool nmethod::jvmci_skip_profile_deopt() const {
  return jvmci_nmethod_data() != nullptr && !jvmci_nmethod_data()->profile_deopt();
}
#endif
