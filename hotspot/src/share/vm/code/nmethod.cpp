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
#include "code/codeCache.hpp"
#include "code/compiledIC.hpp"
#include "code/dependencies.hpp"
#include "code/nmethod.hpp"
#include "code/scopeDesc.hpp"
#include "compiler/abstractCompiler.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compileLog.hpp"
#include "compiler/compilerOracle.hpp"
#include "compiler/disassembler.hpp"
#include "interpreter/bytecode.hpp"
#include "oops/methodData.hpp"
#include "prims/jvmtiRedefineClassesTrace.hpp"
#include "prims/jvmtiImpl.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/sweeper.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/events.hpp"
#include "utilities/xmlstream.hpp"
#ifdef SHARK
#include "shark/sharkCompiler.hpp"
#endif

#ifdef DTRACE_ENABLED

// Only bother with this argument setup if dtrace is available

#define DTRACE_METHOD_UNLOAD_PROBE(method)                                \
  {                                                                       \
    Method* m = (method);                                                 \
    if (m != NULL) {                                                      \
      Symbol* klass_name = m->klass_name();                               \
      Symbol* name = m->name();                                           \
      Symbol* signature = m->signature();                                 \
      HOTSPOT_COMPILED_METHOD_UNLOAD(                                     \
        (char *) klass_name->bytes(), klass_name->utf8_length(),                   \
        (char *) name->bytes(), name->utf8_length(),                               \
        (char *) signature->bytes(), signature->utf8_length());                    \
    }                                                                     \
  }

#else //  ndef DTRACE_ENABLED

#define DTRACE_METHOD_UNLOAD_PROBE(method)

#endif

bool nmethod::is_compiled_by_c1() const {
  if (compiler() == NULL) {
    return false;
  }
  return compiler()->is_c1();
}
bool nmethod::is_compiled_by_c2() const {
  if (compiler() == NULL) {
    return false;
  }
  return compiler()->is_c2();
}
bool nmethod::is_compiled_by_shark() const {
  if (compiler() == NULL) {
    return false;
  }
  return compiler()->is_shark();
}



//---------------------------------------------------------------------------------
// NMethod statistics
// They are printed under various flags, including:
//   PrintC1Statistics, PrintOptoStatistics, LogVMOutput, and LogCompilation.
// (In the latter two cases, they like other stats are printed to the log only.)

#ifndef PRODUCT
// These variables are put into one block to reduce relocations
// and make it simpler to print from the debugger.
static
struct nmethod_stats_struct {
  int nmethod_count;
  int total_size;
  int relocation_size;
  int consts_size;
  int insts_size;
  int stub_size;
  int scopes_data_size;
  int scopes_pcs_size;
  int dependencies_size;
  int handler_table_size;
  int nul_chk_table_size;
  int oops_size;

  void note_nmethod(nmethod* nm) {
    nmethod_count += 1;
    total_size          += nm->size();
    relocation_size     += nm->relocation_size();
    consts_size         += nm->consts_size();
    insts_size          += nm->insts_size();
    stub_size           += nm->stub_size();
    oops_size           += nm->oops_size();
    scopes_data_size    += nm->scopes_data_size();
    scopes_pcs_size     += nm->scopes_pcs_size();
    dependencies_size   += nm->dependencies_size();
    handler_table_size  += nm->handler_table_size();
    nul_chk_table_size  += nm->nul_chk_table_size();
  }
  void print_nmethod_stats() {
    if (nmethod_count == 0)  return;
    tty->print_cr("Statistics for %d bytecoded nmethods:", nmethod_count);
    if (total_size != 0)          tty->print_cr(" total in heap  = %d", total_size);
    if (relocation_size != 0)     tty->print_cr(" relocation     = %d", relocation_size);
    if (consts_size != 0)         tty->print_cr(" constants      = %d", consts_size);
    if (insts_size != 0)          tty->print_cr(" main code      = %d", insts_size);
    if (stub_size != 0)           tty->print_cr(" stub code      = %d", stub_size);
    if (oops_size != 0)           tty->print_cr(" oops           = %d", oops_size);
    if (scopes_data_size != 0)    tty->print_cr(" scopes data    = %d", scopes_data_size);
    if (scopes_pcs_size != 0)     tty->print_cr(" scopes pcs     = %d", scopes_pcs_size);
    if (dependencies_size != 0)   tty->print_cr(" dependencies   = %d", dependencies_size);
    if (handler_table_size != 0)  tty->print_cr(" handler table  = %d", handler_table_size);
    if (nul_chk_table_size != 0)  tty->print_cr(" nul chk table  = %d", nul_chk_table_size);
  }

  int native_nmethod_count;
  int native_total_size;
  int native_relocation_size;
  int native_insts_size;
  int native_oops_size;
  void note_native_nmethod(nmethod* nm) {
    native_nmethod_count += 1;
    native_total_size       += nm->size();
    native_relocation_size  += nm->relocation_size();
    native_insts_size       += nm->insts_size();
    native_oops_size        += nm->oops_size();
  }
  void print_native_nmethod_stats() {
    if (native_nmethod_count == 0)  return;
    tty->print_cr("Statistics for %d native nmethods:", native_nmethod_count);
    if (native_total_size != 0)       tty->print_cr(" N. total size  = %d", native_total_size);
    if (native_relocation_size != 0)  tty->print_cr(" N. relocation  = %d", native_relocation_size);
    if (native_insts_size != 0)       tty->print_cr(" N. main code   = %d", native_insts_size);
    if (native_oops_size != 0)        tty->print_cr(" N. oops        = %d", native_oops_size);
  }

  int pc_desc_resets;   // number of resets (= number of caches)
  int pc_desc_queries;  // queries to nmethod::find_pc_desc
  int pc_desc_approx;   // number of those which have approximate true
  int pc_desc_repeats;  // number of _pc_descs[0] hits
  int pc_desc_hits;     // number of LRU cache hits
  int pc_desc_tests;    // total number of PcDesc examinations
  int pc_desc_searches; // total number of quasi-binary search steps
  int pc_desc_adds;     // number of LUR cache insertions

  void print_pc_stats() {
    tty->print_cr("PcDesc Statistics:  %d queries, %.2f comparisons per query",
                  pc_desc_queries,
                  (double)(pc_desc_tests + pc_desc_searches)
                  / pc_desc_queries);
    tty->print_cr("  caches=%d queries=%d/%d, hits=%d+%d, tests=%d+%d, adds=%d",
                  pc_desc_resets,
                  pc_desc_queries, pc_desc_approx,
                  pc_desc_repeats, pc_desc_hits,
                  pc_desc_tests, pc_desc_searches, pc_desc_adds);
  }
} nmethod_stats;
#endif //PRODUCT


//---------------------------------------------------------------------------------


ExceptionCache::ExceptionCache(Handle exception, address pc, address handler) {
  assert(pc != NULL, "Must be non null");
  assert(exception.not_null(), "Must be non null");
  assert(handler != NULL, "Must be non null");

  _count = 0;
  _exception_type = exception->klass();
  _next = NULL;

  add_address_and_handler(pc,handler);
}


address ExceptionCache::match(Handle exception, address pc) {
  assert(pc != NULL,"Must be non null");
  assert(exception.not_null(),"Must be non null");
  if (exception->klass() == exception_type()) {
    return (test_address(pc));
  }

  return NULL;
}


bool ExceptionCache::match_exception_with_space(Handle exception) {
  assert(exception.not_null(),"Must be non null");
  if (exception->klass() == exception_type() && count() < cache_size) {
    return true;
  }
  return false;
}


address ExceptionCache::test_address(address addr) {
  for (int i=0; i<count(); i++) {
    if (pc_at(i) == addr) {
      return handler_at(i);
    }
  }
  return NULL;
}


bool ExceptionCache::add_address_and_handler(address addr, address handler) {
  if (test_address(addr) == handler) return true;
  if (count() < cache_size) {
    set_pc_at(count(),addr);
    set_handler_at(count(), handler);
    increment_count();
    return true;
  }
  return false;
}


// private method for handling exception cache
// These methods are private, and used to manipulate the exception cache
// directly.
ExceptionCache* nmethod::exception_cache_entry_for_exception(Handle exception) {
  ExceptionCache* ec = exception_cache();
  while (ec != NULL) {
    if (ec->match_exception_with_space(exception)) {
      return ec;
    }
    ec = ec->next();
  }
  return NULL;
}


//-----------------------------------------------------------------------------


// Helper used by both find_pc_desc methods.
static inline bool match_desc(PcDesc* pc, int pc_offset, bool approximate) {
  NOT_PRODUCT(++nmethod_stats.pc_desc_tests);
  if (!approximate)
    return pc->pc_offset() == pc_offset;
  else
    return (pc-1)->pc_offset() < pc_offset && pc_offset <= pc->pc_offset();
}

void PcDescCache::reset_to(PcDesc* initial_pc_desc) {
  if (initial_pc_desc == NULL) {
    _pc_descs[0] = NULL; // native method; no PcDescs at all
    return;
  }
  NOT_PRODUCT(++nmethod_stats.pc_desc_resets);
  // reset the cache by filling it with benign (non-null) values
  assert(initial_pc_desc->pc_offset() < 0, "must be sentinel");
  for (int i = 0; i < cache_size; i++)
    _pc_descs[i] = initial_pc_desc;
}

PcDesc* PcDescCache::find_pc_desc(int pc_offset, bool approximate) {
  NOT_PRODUCT(++nmethod_stats.pc_desc_queries);
  NOT_PRODUCT(if (approximate) ++nmethod_stats.pc_desc_approx);

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
  if (res == NULL) return NULL;  // native method; no PcDescs at all
  if (match_desc(res, pc_offset, approximate)) {
    NOT_PRODUCT(++nmethod_stats.pc_desc_repeats);
    return res;
  }

  // Step two:  Check the rest of the LRU cache.
  for (int i = 1; i < cache_size; ++i) {
    res = _pc_descs[i];
    if (res->pc_offset() < 0) break;  // optimization: skip empty cache
    if (match_desc(res, pc_offset, approximate)) {
      NOT_PRODUCT(++nmethod_stats.pc_desc_hits);
      return res;
    }
  }

  // Report failure.
  return NULL;
}

void PcDescCache::add_pc_desc(PcDesc* pc_desc) {
  NOT_PRODUCT(++nmethod_stats.pc_desc_adds);
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
  int nsize = round_to(pcs_size,   oopSize);
  if ((nsize % sizeof(PcDesc)) != 0) {
    nsize = pcs_size + sizeof(PcDesc);
  }
  assert((nsize % oopSize) == 0, "correct alignment");
  return nsize;
}

//-----------------------------------------------------------------------------


void nmethod::add_exception_cache_entry(ExceptionCache* new_entry) {
  assert(ExceptionCache_lock->owned_by_self(),"Must hold the ExceptionCache_lock");
  assert(new_entry != NULL,"Must be non null");
  assert(new_entry->next() == NULL, "Must be null");

  if (exception_cache() != NULL) {
    new_entry->set_next(exception_cache());
  }
  set_exception_cache(new_entry);
}

void nmethod::remove_from_exception_cache(ExceptionCache* ec) {
  ExceptionCache* prev = NULL;
  ExceptionCache* curr = exception_cache();
  assert(curr != NULL, "nothing to remove");
  // find the previous and next entry of ec
  while (curr != ec) {
    prev = curr;
    curr = curr->next();
    assert(curr != NULL, "ExceptionCache not found");
  }
  // now: curr == ec
  ExceptionCache* next = curr->next();
  if (prev == NULL) {
    set_exception_cache(next);
  } else {
    prev->set_next(next);
  }
  delete curr;
}


// public method for accessing the exception cache
// These are the public access methods.
address nmethod::handler_for_exception_and_pc(Handle exception, address pc) {
  // We never grab a lock to read the exception cache, so we may
  // have false negatives. This is okay, as it can only happen during
  // the first few exception lookups for a given nmethod.
  ExceptionCache* ec = exception_cache();
  while (ec != NULL) {
    address ret_val;
    if ((ret_val = ec->match(exception,pc)) != NULL) {
      return ret_val;
    }
    ec = ec->next();
  }
  return NULL;
}


void nmethod::add_handler_for_exception_and_pc(Handle exception, address pc, address handler) {
  // There are potential race conditions during exception cache updates, so we
  // must own the ExceptionCache_lock before doing ANY modifications. Because
  // we don't lock during reads, it is possible to have several threads attempt
  // to update the cache with the same data. We need to check for already inserted
  // copies of the current data before adding it.

  MutexLocker ml(ExceptionCache_lock);
  ExceptionCache* target_entry = exception_cache_entry_for_exception(exception);

  if (target_entry == NULL || !target_entry->add_address_and_handler(pc,handler)) {
    target_entry = new ExceptionCache(exception,pc,handler);
    add_exception_cache_entry(target_entry);
  }
}


//-------------end of code for ExceptionCache--------------


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
  if (method() != NULL && is_native_method())  return "c2n";
  return NULL;
}

// Fill in default values for various flag fields
void nmethod::init_defaults() {
  _state                      = in_use;
  _marked_for_reclamation     = 0;
  _has_flushed_dependencies   = 0;
  _has_unsafe_access          = 0;
  _has_method_handle_invokes  = 0;
  _lazy_critical_native       = 0;
  _has_wide_vectors           = 0;
  _marked_for_deoptimization  = 0;
  _lock_count                 = 0;
  _stack_traversal_mark       = 0;
  _unload_reported            = false;           // jvmti state

#ifdef ASSERT
  _oops_are_stale             = false;
#endif

  _oops_do_mark_link       = NULL;
  _jmethod_id              = NULL;
  _osr_link                = NULL;
  _scavenge_root_link      = NULL;
  _scavenge_root_state     = 0;
  _compiler                = NULL;

#ifdef HAVE_DTRACE_H
  _trap_offset             = 0;
#endif // def HAVE_DTRACE_H
}

nmethod* nmethod::new_native_nmethod(methodHandle method,
  int compile_id,
  CodeBuffer *code_buffer,
  int vep_offset,
  int frame_complete,
  int frame_size,
  ByteSize basic_lock_owner_sp_offset,
  ByteSize basic_lock_sp_offset,
  OopMapSet* oop_maps) {
  code_buffer->finalize_oop_references(method);
  // create nmethod
  nmethod* nm = NULL;
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    int native_nmethod_size = allocation_size(code_buffer, sizeof(nmethod));
    CodeOffsets offsets;
    offsets.set_value(CodeOffsets::Verified_Entry, vep_offset);
    offsets.set_value(CodeOffsets::Frame_Complete, frame_complete);
    nm = new (native_nmethod_size) nmethod(method(), native_nmethod_size,
                                            compile_id, &offsets,
                                            code_buffer, frame_size,
                                            basic_lock_owner_sp_offset,
                                            basic_lock_sp_offset, oop_maps);
    NOT_PRODUCT(if (nm != NULL)  nmethod_stats.note_native_nmethod(nm));
    if (PrintAssembly && nm != NULL) {
      Disassembler::decode(nm);
    }
  }
  // verify nmethod
  debug_only(if (nm) nm->verify();) // might block

  if (nm != NULL) {
    nm->log_new_nmethod();
  }

  return nm;
}

#ifdef HAVE_DTRACE_H
nmethod* nmethod::new_dtrace_nmethod(methodHandle method,
                                     CodeBuffer *code_buffer,
                                     int vep_offset,
                                     int trap_offset,
                                     int frame_complete,
                                     int frame_size) {
  code_buffer->finalize_oop_references(method);
  // create nmethod
  nmethod* nm = NULL;
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    int nmethod_size = allocation_size(code_buffer, sizeof(nmethod));
    CodeOffsets offsets;
    offsets.set_value(CodeOffsets::Verified_Entry, vep_offset);
    offsets.set_value(CodeOffsets::Dtrace_trap, trap_offset);
    offsets.set_value(CodeOffsets::Frame_Complete, frame_complete);

    nm = new (nmethod_size) nmethod(method(), nmethod_size,
                                    &offsets, code_buffer, frame_size);

    NOT_PRODUCT(if (nm != NULL)  nmethod_stats.note_nmethod(nm));
    if (PrintAssembly && nm != NULL) {
      Disassembler::decode(nm);
    }
  }
  // verify nmethod
  debug_only(if (nm) nm->verify();) // might block

  if (nm != NULL) {
    nm->log_new_nmethod();
  }

  return nm;
}

#endif // def HAVE_DTRACE_H

nmethod* nmethod::new_nmethod(methodHandle method,
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
  int comp_level
)
{
  assert(debug_info->oop_recorder() == code_buffer->oop_recorder(), "shared OR");
  code_buffer->finalize_oop_references(method);
  // create nmethod
  nmethod* nm = NULL;
  { MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    int nmethod_size =
      allocation_size(code_buffer, sizeof(nmethod))
      + adjust_pcs_size(debug_info->pcs_size())
      + round_to(dependencies->size_in_bytes() , oopSize)
      + round_to(handler_table->size_in_bytes(), oopSize)
      + round_to(nul_chk_table->size_in_bytes(), oopSize)
      + round_to(debug_info->data_size()       , oopSize);

    nm = new (nmethod_size)
    nmethod(method(), nmethod_size, compile_id, entry_bci, offsets,
            orig_pc_offset, debug_info, dependencies, code_buffer, frame_size,
            oop_maps,
            handler_table,
            nul_chk_table,
            compiler,
            comp_level);

    if (nm != NULL) {
      // To make dependency checking during class loading fast, record
      // the nmethod dependencies in the classes it is dependent on.
      // This allows the dependency checking code to simply walk the
      // class hierarchy above the loaded class, checking only nmethods
      // which are dependent on those classes.  The slow way is to
      // check every nmethod for dependencies which makes it linear in
      // the number of methods compiled.  For applications with a lot
      // classes the slow way is too slow.
      for (Dependencies::DepStream deps(nm); deps.next(); ) {
        Klass* klass = deps.context_type();
        if (klass == NULL) {
          continue;  // ignore things like evol_method
        }

        // record this nmethod as dependent on this klass
        InstanceKlass::cast(klass)->add_dependent_nmethod(nm);
      }
      NOT_PRODUCT(nmethod_stats.note_nmethod(nm));
      if (PrintAssembly || CompilerOracle::has_option_string(method, "PrintAssembly")) {
        Disassembler::decode(nm);
      }
    }
  }
  // Do verification and logging outside CodeCache_lock.
  if (nm != NULL) {
    // Safepoints in nmethod::verify aren't allowed because nm hasn't been installed yet.
    DEBUG_ONLY(nm->verify();)
    nm->log_new_nmethod();
  }
  return nm;
}


// For native wrappers
nmethod::nmethod(
  Method* method,
  int nmethod_size,
  int compile_id,
  CodeOffsets* offsets,
  CodeBuffer* code_buffer,
  int frame_size,
  ByteSize basic_lock_owner_sp_offset,
  ByteSize basic_lock_sp_offset,
  OopMapSet* oop_maps )
  : CodeBlob("native nmethod", code_buffer, sizeof(nmethod),
             nmethod_size, offsets->value(CodeOffsets::Frame_Complete), frame_size, oop_maps),
  _native_receiver_sp_offset(basic_lock_owner_sp_offset),
  _native_basic_lock_sp_offset(basic_lock_sp_offset)
{
  {
    debug_only(No_Safepoint_Verifier nsv;)
    assert_locked_or_safepoint(CodeCache_lock);

    init_defaults();
    _method                  = method;
    _entry_bci               = InvocationEntryBci;
    // We have no exception handler or deopt handler make the
    // values something that will never match a pc like the nmethod vtable entry
    _exception_offset        = 0;
    _deoptimize_offset       = 0;
    _deoptimize_mh_offset    = 0;
    _orig_pc_offset          = 0;

    _consts_offset           = data_offset();
    _stub_offset             = data_offset();
    _oops_offset             = data_offset();
    _metadata_offset         = _oops_offset         + round_to(code_buffer->total_oop_size(), oopSize);
    _scopes_data_offset      = _metadata_offset     + round_to(code_buffer->total_metadata_size(), wordSize);
    _scopes_pcs_offset       = _scopes_data_offset;
    _dependencies_offset     = _scopes_pcs_offset;
    _handler_table_offset    = _dependencies_offset;
    _nul_chk_table_offset    = _handler_table_offset;
    _nmethod_end_offset      = _nul_chk_table_offset;
    _compile_id              = compile_id;
    _comp_level              = CompLevel_none;
    _entry_point             = code_begin()          + offsets->value(CodeOffsets::Entry);
    _verified_entry_point    = code_begin()          + offsets->value(CodeOffsets::Verified_Entry);
    _osr_entry_point         = NULL;
    _exception_cache         = NULL;
    _pc_desc_cache.reset_to(NULL);
    _hotness_counter         = NMethodSweeper::hotness_counter_reset_val();

    code_buffer->copy_values_to(this);
    if (ScavengeRootsInCode && detect_scavenge_root_oops()) {
      CodeCache::add_scavenge_root_nmethod(this);
      Universe::heap()->register_nmethod(this);
    }
    debug_only(verify_scavenge_root_oops());
    CodeCache::commit(this);
  }

  if (PrintNativeNMethods || PrintDebugInfo || PrintRelocations || PrintDependencies) {
    ttyLocker ttyl;  // keep the following output all in one block
    // This output goes directly to the tty, not the compiler log.
    // To enable tools to match it up with the compilation activity,
    // be sure to tag this tty output with the compile ID.
    if (xtty != NULL) {
      xtty->begin_head("print_native_nmethod");
      xtty->method(_method);
      xtty->stamp();
      xtty->end_head(" address='" INTPTR_FORMAT "'", (intptr_t) this);
    }
    // print the header part first
    print();
    // then print the requested information
    if (PrintNativeNMethods) {
      print_code();
      if (oop_maps != NULL) {
        oop_maps->print();
      }
    }
    if (PrintRelocations) {
      print_relocations();
    }
    if (xtty != NULL) {
      xtty->tail("print_native_nmethod");
    }
  }
}

// For dtrace wrappers
#ifdef HAVE_DTRACE_H
nmethod::nmethod(
  Method* method,
  int nmethod_size,
  CodeOffsets* offsets,
  CodeBuffer* code_buffer,
  int frame_size)
  : CodeBlob("dtrace nmethod", code_buffer, sizeof(nmethod),
             nmethod_size, offsets->value(CodeOffsets::Frame_Complete), frame_size, NULL),
  _native_receiver_sp_offset(in_ByteSize(-1)),
  _native_basic_lock_sp_offset(in_ByteSize(-1))
{
  {
    debug_only(No_Safepoint_Verifier nsv;)
    assert_locked_or_safepoint(CodeCache_lock);

    init_defaults();
    _method                  = method;
    _entry_bci               = InvocationEntryBci;
    // We have no exception handler or deopt handler make the
    // values something that will never match a pc like the nmethod vtable entry
    _exception_offset        = 0;
    _deoptimize_offset       = 0;
    _deoptimize_mh_offset    = 0;
    _unwind_handler_offset   = -1;
    _trap_offset             = offsets->value(CodeOffsets::Dtrace_trap);
    _orig_pc_offset          = 0;
    _consts_offset           = data_offset();
    _stub_offset             = data_offset();
    _oops_offset             = data_offset();
    _metadata_offset         = _oops_offset         + round_to(code_buffer->total_oop_size(), oopSize);
    _scopes_data_offset      = _metadata_offset     + round_to(code_buffer->total_metadata_size(), wordSize);
    _scopes_pcs_offset       = _scopes_data_offset;
    _dependencies_offset     = _scopes_pcs_offset;
    _handler_table_offset    = _dependencies_offset;
    _nul_chk_table_offset    = _handler_table_offset;
    _nmethod_end_offset      = _nul_chk_table_offset;
    _compile_id              = 0;  // default
    _comp_level              = CompLevel_none;
    _entry_point             = code_begin()          + offsets->value(CodeOffsets::Entry);
    _verified_entry_point    = code_begin()          + offsets->value(CodeOffsets::Verified_Entry);
    _osr_entry_point         = NULL;
    _exception_cache         = NULL;
    _pc_desc_cache.reset_to(NULL);
    _hotness_counter         = NMethodSweeper::hotness_counter_reset_val();

    code_buffer->copy_values_to(this);
    debug_only(verify_scavenge_root_oops());
    CodeCache::commit(this);
  }

  if (PrintNMethods || PrintDebugInfo || PrintRelocations || PrintDependencies) {
    ttyLocker ttyl;  // keep the following output all in one block
    // This output goes directly to the tty, not the compiler log.
    // To enable tools to match it up with the compilation activity,
    // be sure to tag this tty output with the compile ID.
    if (xtty != NULL) {
      xtty->begin_head("print_dtrace_nmethod");
      xtty->method(_method);
      xtty->stamp();
      xtty->end_head(" address='" INTPTR_FORMAT "'", (intptr_t) this);
    }
    // print the header part first
    print();
    // then print the requested information
    if (PrintNMethods) {
      print_code();
    }
    if (PrintRelocations) {
      print_relocations();
    }
    if (xtty != NULL) {
      xtty->tail("print_dtrace_nmethod");
    }
  }
}
#endif // def HAVE_DTRACE_H

void* nmethod::operator new(size_t size, int nmethod_size) throw() {
  // Not critical, may return null if there is too little continuous memory
  return CodeCache::allocate(nmethod_size);
}

nmethod::nmethod(
  Method* method,
  int nmethod_size,
  int compile_id,
  int entry_bci,
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
  int comp_level
  )
  : CodeBlob("nmethod", code_buffer, sizeof(nmethod),
             nmethod_size, offsets->value(CodeOffsets::Frame_Complete), frame_size, oop_maps),
  _native_receiver_sp_offset(in_ByteSize(-1)),
  _native_basic_lock_sp_offset(in_ByteSize(-1))
{
  assert(debug_info->oop_recorder() == code_buffer->oop_recorder(), "shared OR");
  {
    debug_only(No_Safepoint_Verifier nsv;)
    assert_locked_or_safepoint(CodeCache_lock);

    init_defaults();
    _method                  = method;
    _entry_bci               = entry_bci;
    _compile_id              = compile_id;
    _comp_level              = comp_level;
    _compiler                = compiler;
    _orig_pc_offset          = orig_pc_offset;
    _hotness_counter         = NMethodSweeper::hotness_counter_reset_val();

    // Section offsets
    _consts_offset           = content_offset()      + code_buffer->total_offset_of(code_buffer->consts());
    _stub_offset             = content_offset()      + code_buffer->total_offset_of(code_buffer->stubs());

    // Exception handler and deopt handler are in the stub section
    assert(offsets->value(CodeOffsets::Exceptions) != -1, "must be set");
    assert(offsets->value(CodeOffsets::Deopt     ) != -1, "must be set");
    _exception_offset        = _stub_offset          + offsets->value(CodeOffsets::Exceptions);
    _deoptimize_offset       = _stub_offset          + offsets->value(CodeOffsets::Deopt);
    if (offsets->value(CodeOffsets::DeoptMH) != -1) {
      _deoptimize_mh_offset  = _stub_offset          + offsets->value(CodeOffsets::DeoptMH);
    } else {
      _deoptimize_mh_offset  = -1;
    }
    if (offsets->value(CodeOffsets::UnwindHandler) != -1) {
      _unwind_handler_offset = code_offset()         + offsets->value(CodeOffsets::UnwindHandler);
    } else {
      _unwind_handler_offset = -1;
    }

    _oops_offset             = data_offset();
    _metadata_offset         = _oops_offset          + round_to(code_buffer->total_oop_size(), oopSize);
    _scopes_data_offset      = _metadata_offset      + round_to(code_buffer->total_metadata_size(), wordSize);

    _scopes_pcs_offset       = _scopes_data_offset   + round_to(debug_info->data_size       (), oopSize);
    _dependencies_offset     = _scopes_pcs_offset    + adjust_pcs_size(debug_info->pcs_size());
    _handler_table_offset    = _dependencies_offset  + round_to(dependencies->size_in_bytes (), oopSize);
    _nul_chk_table_offset    = _handler_table_offset + round_to(handler_table->size_in_bytes(), oopSize);
    _nmethod_end_offset      = _nul_chk_table_offset + round_to(nul_chk_table->size_in_bytes(), oopSize);

    _entry_point             = code_begin()          + offsets->value(CodeOffsets::Entry);
    _verified_entry_point    = code_begin()          + offsets->value(CodeOffsets::Verified_Entry);
    _osr_entry_point         = code_begin()          + offsets->value(CodeOffsets::OSR_Entry);
    _exception_cache         = NULL;
    _pc_desc_cache.reset_to(scopes_pcs_begin());

    // Copy contents of ScopeDescRecorder to nmethod
    code_buffer->copy_values_to(this);
    debug_info->copy_to(this);
    dependencies->copy_to(this);
    if (ScavengeRootsInCode && detect_scavenge_root_oops()) {
      CodeCache::add_scavenge_root_nmethod(this);
      Universe::heap()->register_nmethod(this);
    }
    debug_only(verify_scavenge_root_oops());

    CodeCache::commit(this);

    // Copy contents of ExceptionHandlerTable to nmethod
    handler_table->copy_to(this);
    nul_chk_table->copy_to(this);

    // we use the information of entry points to find out if a method is
    // static or non static
    assert(compiler->is_c2() ||
           _method->is_static() == (entry_point() == _verified_entry_point),
           " entry points must be same for static methods and vice versa");
  }

  bool printnmethods = PrintNMethods
    || CompilerOracle::should_print(_method)
    || CompilerOracle::has_option_string(_method, "PrintNMethods");
  if (printnmethods || PrintDebugInfo || PrintRelocations || PrintDependencies || PrintExceptionHandlers) {
    print_nmethod(printnmethods);
  }
}


// Print a short set of xml attributes to identify this nmethod.  The
// output should be embedded in some other element.
void nmethod::log_identity(xmlStream* log) const {
  log->print(" compile_id='%d'", compile_id());
  const char* nm_kind = compile_kind();
  if (nm_kind != NULL)  log->print(" compile_kind='%s'", nm_kind);
  if (compiler() != NULL) {
    log->print(" compiler='%s'", compiler()->name());
  }
  if (TieredCompilation) {
    log->print(" level='%d'", comp_level());
  }
}


#define LOG_OFFSET(log, name)                    \
  if ((intptr_t)name##_end() - (intptr_t)name##_begin()) \
    log->print(" " XSTR(name) "_offset='%d'"    , \
               (intptr_t)name##_begin() - (intptr_t)this)


void nmethod::log_new_nmethod() const {
  if (LogCompilation && xtty != NULL) {
    ttyLocker ttyl;
    HandleMark hm;
    xtty->begin_elem("nmethod");
    log_identity(xtty);
    xtty->print(" entry='" INTPTR_FORMAT "' size='%d'", code_begin(), size());
    xtty->print(" address='" INTPTR_FORMAT "'", (intptr_t) this);

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

    xtty->method(method());
    xtty->stamp();
    xtty->end_elem();
  }
}

#undef LOG_OFFSET


// Print out more verbose output usually for a newly created nmethod.
void nmethod::print_on(outputStream* st, const char* msg) const {
  if (st != NULL) {
    ttyLocker ttyl;
    if (WizardMode) {
      CompileTask::print_compilation(st, this, msg, /*short_form:*/ true);
      st->print_cr(" (" INTPTR_FORMAT ")", this);
    } else {
      CompileTask::print_compilation(st, this, msg, /*short_form:*/ false);
    }
  }
}


void nmethod::print_nmethod(bool printmethod) {
  ttyLocker ttyl;  // keep the following output all in one block
  if (xtty != NULL) {
    xtty->begin_head("print_nmethod");
    xtty->stamp();
    xtty->end_head();
  }
  // print the header part first
  print();
  // then print the requested information
  if (printmethod) {
    print_code();
    print_pcs();
    if (oop_maps()) {
      oop_maps()->print();
    }
  }
  if (PrintDebugInfo) {
    print_scopes();
  }
  if (PrintRelocations) {
    print_relocations();
  }
  if (PrintDependencies) {
    print_dependencies();
  }
  if (PrintExceptionHandlers) {
    print_handler_table();
    print_nul_chk_table();
  }
  if (xtty != NULL) {
    xtty->tail("print_nmethod");
  }
}


// Promote one word from an assembly-time handle to a live embedded oop.
inline void nmethod::initialize_immediate_oop(oop* dest, jobject handle) {
  if (handle == NULL ||
      // As a special case, IC oops are initialized to 1 or -1.
      handle == (jobject) Universe::non_oop_word()) {
    (*dest) = (oop) handle;
  } else {
    (*dest) = JNIHandles::resolve_non_null(handle);
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
  fix_oop_relocations(NULL, NULL, /*initialize_immediates=*/ true);
}

void nmethod::copy_values(GrowableArray<Metadata*>* array) {
  int length = array->length();
  assert((address)(metadata_begin() + length) <= (address)metadata_end(), "big enough");
  Metadata** dest = metadata_begin();
  for (int index = 0 ; index < length; index++) {
    dest[index] = array->at(index);
  }
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


void nmethod::fix_oop_relocations(address begin, address end, bool initialize_immediates) {
  // re-patch all oop-bearing instructions, just in case some oops moved
  RelocIterator iter(this, begin, end);
  while (iter.next()) {
    if (iter.type() == relocInfo::oop_type) {
      oop_Relocation* reloc = iter.oop_reloc();
      if (initialize_immediates && reloc->oop_is_immediate()) {
        oop* dest = reloc->oop_addr();
        initialize_immediate_oop(dest, (jobject) *dest);
      }
      // Refresh the oop-related bits of this instruction.
      reloc->fix_oop_relocation();
    } else if (iter.type() == relocInfo::metadata_type) {
      metadata_Relocation* reloc = iter.metadata_reloc();
      reloc->fix_metadata_relocation();
    }
  }
}


void nmethod::verify_oop_relocations() {
  // Ensure sure that the code matches the current oop values
  RelocIterator iter(this, NULL, NULL);
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
  guarantee(pd != NULL, "scope must be present");
  return new ScopeDesc(this, pd->scope_decode_offset(),
                       pd->obj_decode_offset(), pd->should_reexecute(),
                       pd->return_oop());
}


void nmethod::clear_inline_caches() {
  assert(SafepointSynchronize::is_at_safepoint(), "cleaning of IC's only allowed at safepoint");
  if (is_zombie()) {
    return;
  }

  RelocIterator iter(this);
  while (iter.next()) {
    iter.reloc()->clear_inline_cache();
  }
}


void nmethod::cleanup_inline_caches() {

  assert_locked_or_safepoint(CompiledIC_lock);

  // If the method is not entrant or zombie then a JMP is plastered over the
  // first few bytes.  If an oop in the old code was there, that oop
  // should not get GC'd.  Skip the first few bytes of oops on
  // not-entrant methods.
  address low_boundary = verified_entry_point();
  if (!is_in_use()) {
    low_boundary += NativeJump::instruction_size;
    // %%% Note:  On SPARC we patch only a 4-byte trap, not a full NativeJump.
    // This means that the low_boundary is going to be a little too high.
    // This shouldn't matter, since oops of non-entrant methods are never used.
    // In fact, why are we bothering to look at oops in a non-entrant method??
  }

  // Find all calls in an nmethod, and clear the ones that points to zombie methods
  ResourceMark rm;
  RelocIterator iter(this, low_boundary);
  while(iter.next()) {
    switch(iter.type()) {
      case relocInfo::virtual_call_type:
      case relocInfo::opt_virtual_call_type: {
        CompiledIC *ic = CompiledIC_at(iter.reloc());
        // Ok, to lookup references to zombies here
        CodeBlob *cb = CodeCache::find_blob_unsafe(ic->ic_destination());
        if( cb != NULL && cb->is_nmethod() ) {
          nmethod* nm = (nmethod*)cb;
          // Clean inline caches pointing to both zombie and not_entrant methods
          if (!nm->is_in_use() || (nm->method()->code() != nm)) ic->set_to_clean();
        }
        break;
      }
      case relocInfo::static_call_type: {
        CompiledStaticCall *csc = compiledStaticCall_at(iter.reloc());
        CodeBlob *cb = CodeCache::find_blob_unsafe(csc->destination());
        if( cb != NULL && cb->is_nmethod() ) {
          nmethod* nm = (nmethod*)cb;
          // Clean inline caches pointing to both zombie and not_entrant methods
          if (!nm->is_in_use() || (nm->method()->code() != nm)) csc->set_to_clean();
        }
        break;
      }
    }
  }
}

// This is a private interface with the sweeper.
void nmethod::mark_as_seen_on_stack() {
  assert(is_alive(), "Must be an alive method");
  // Set the traversal mark to ensure that the sweeper does 2
  // cleaning passes before moving to zombie.
  set_stack_traversal_mark(NMethodSweeper::traversal_count());
}

// Tell if a non-entrant method can be converted to a zombie (i.e.,
// there are no activations on the stack, not in use by the VM,
// and not in use by the ServiceThread)
bool nmethod::can_not_entrant_be_converted() {
  assert(is_not_entrant(), "must be a non-entrant method");

  // Since the nmethod sweeper only does partial sweep the sweeper's traversal
  // count can be greater than the stack traversal count before it hits the
  // nmethod for the second time.
  return stack_traversal_mark()+1 < NMethodSweeper::traversal_count() &&
         !is_locked_by_vm();
}

void nmethod::inc_decompile_count() {
  if (!is_compiled_by_c2()) return;
  // Could be gated by ProfileTraps, but do not bother...
  Method* m = method();
  if (m == NULL)  return;
  MethodData* mdo = m->method_data();
  if (mdo == NULL)  return;
  // There is a benign race here.  See comments in methodData.hpp.
  mdo->inc_decompile_count();
}

void nmethod::make_unloaded(BoolObjectClosure* is_alive, oop cause) {

  post_compiled_method_unload();

  // Since this nmethod is being unloaded, make sure that dependencies
  // recorded in instanceKlasses get flushed and pass non-NULL closure to
  // indicate that this work is being done during a GC.
  assert(Universe::heap()->is_gc_active(), "should only be called during gc");
  assert(is_alive != NULL, "Should be non-NULL");
  // A non-NULL is_alive closure indicates that this is being called during GC.
  flush_dependencies(is_alive);

  // Break cycle between nmethod & method
  if (TraceClassUnloading && WizardMode) {
    tty->print_cr("[Class unloading: Making nmethod " INTPTR_FORMAT
                  " unloadable], Method*(" INTPTR_FORMAT
                  "), cause(" INTPTR_FORMAT ")",
                  this, (address)_method, (address)cause);
    if (!Universe::heap()->is_gc_active())
      cause->klass()->print();
  }
  // Unlink the osr method, so we do not look this up again
  if (is_osr_method()) {
    invalidate_osr_method();
  }
  // If _method is already NULL the Method* is about to be unloaded,
  // so we don't have to break the cycle. Note that it is possible to
  // have the Method* live here, in case we unload the nmethod because
  // it is pointing to some oop (other than the Method*) being unloaded.
  if (_method != NULL) {
    // OSR methods point to the Method*, but the Method* does not
    // point back!
    if (_method->code() == this) {
      _method->clear_code(); // Break a cycle
    }
    _method = NULL;            // Clear the method of this dead nmethod
  }
  // Make the class unloaded - i.e., change state and notify sweeper
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  if (is_in_use()) {
    // Transitioning directly from live to unloaded -- so
    // we need to force a cache clean-up; remember this
    // for later on.
    CodeCache::set_needs_cache_clean(true);
  }
  _state = unloaded;

  // Log the unloading.
  log_state_change();

  // The Method* is gone at this point
  assert(_method == NULL, "Tautology");

  set_osr_link(NULL);
  //set_scavenge_root_link(NULL); // done by prune_scavenge_root_nmethods
  NMethodSweeper::report_state_change(this);
}

void nmethod::invalidate_osr_method() {
  assert(_entry_bci != InvocationEntryBci, "wrong kind of nmethod");
  // Remove from list of active nmethods
  if (method() != NULL)
    method()->method_holder()->remove_osr_nmethod(this);
  // Set entry as invalid
  _entry_bci = InvalidOSREntryBci;
}

void nmethod::log_state_change() const {
  if (LogCompilation) {
    if (xtty != NULL) {
      ttyLocker ttyl;  // keep the following output all in one block
      if (_state == unloaded) {
        xtty->begin_elem("make_unloaded thread='" UINTX_FORMAT "'",
                         os::current_thread_id());
      } else {
        xtty->begin_elem("make_not_entrant thread='" UINTX_FORMAT "'%s",
                         os::current_thread_id(),
                         (_state == zombie ? " zombie='1'" : ""));
      }
      log_identity(xtty);
      xtty->stamp();
      xtty->end_elem();
    }
  }
  if (PrintCompilation && _state != unloaded) {
    print_on(tty, _state == zombie ? "made zombie" : "made not entrant");
  }
}

/**
 * Common functionality for both make_not_entrant and make_zombie
 */
bool nmethod::make_not_entrant_or_zombie(unsigned int state) {
  assert(state == zombie || state == not_entrant, "must be zombie or not_entrant");
  assert(!is_zombie(), "should not already be a zombie");

  // Make sure neither the nmethod nor the method is flushed in case of a safepoint in code below.
  nmethodLocker nml(this);
  methodHandle the_method(method());
  No_Safepoint_Verifier nsv;

  // during patching, depending on the nmethod state we must notify the GC that
  // code has been unloaded, unregistering it. We cannot do this right while
  // holding the Patching_lock because we need to use the CodeCache_lock. This
  // would be prone to deadlocks.
  // This flag is used to remember whether we need to later lock and unregister.
  bool nmethod_needs_unregister = false;

  {
    // invalidate osr nmethod before acquiring the patching lock since
    // they both acquire leaf locks and we don't want a deadlock.
    // This logic is equivalent to the logic below for patching the
    // verified entry point of regular methods.
    if (is_osr_method()) {
      // this effectively makes the osr nmethod not entrant
      invalidate_osr_method();
    }

    // Enter critical section.  Does not block for safepoint.
    MutexLockerEx pl(Patching_lock, Mutex::_no_safepoint_check_flag);

    if (_state == state) {
      // another thread already performed this transition so nothing
      // to do, but return false to indicate this.
      return false;
    }

    // The caller can be calling the method statically or through an inline
    // cache call.
    if (!is_osr_method() && !is_not_entrant()) {
      NativeJump::patch_verified_entry(entry_point(), verified_entry_point(),
                  SharedRuntime::get_handle_wrong_method_stub());
    }

    if (is_in_use()) {
      // It's a true state change, so mark the method as decompiled.
      // Do it only for transition from alive.
      inc_decompile_count();
    }

    // If the state is becoming a zombie, signal to unregister the nmethod with
    // the heap.
    // This nmethod may have already been unloaded during a full GC.
    if ((state == zombie) && !is_unloaded()) {
      nmethod_needs_unregister = true;
    }

    // Must happen before state change. Otherwise we have a race condition in
    // nmethod::can_not_entrant_be_converted(). I.e., a method can immediately
    // transition its state from 'not_entrant' to 'zombie' without having to wait
    // for stack scanning.
    if (state == not_entrant) {
      mark_as_seen_on_stack();
      OrderAccess::storestore();
    }

    // Change state
    _state = state;

    // Log the transition once
    log_state_change();

    // Remove nmethod from method.
    // We need to check if both the _code and _from_compiled_code_entry_point
    // refer to this nmethod because there is a race in setting these two fields
    // in Method* as seen in bugid 4947125.
    // If the vep() points to the zombie nmethod, the memory for the nmethod
    // could be flushed and the compiler and vtable stubs could still call
    // through it.
    if (method() != NULL && (method()->code() == this ||
                             method()->from_compiled_entry() == verified_entry_point())) {
      HandleMark hm;
      method()->clear_code();
    }
  } // leave critical region under Patching_lock

  // When the nmethod becomes zombie it is no longer alive so the
  // dependencies must be flushed.  nmethods in the not_entrant
  // state will be flushed later when the transition to zombie
  // happens or they get unloaded.
  if (state == zombie) {
    {
      // Flushing dependecies must be done before any possible
      // safepoint can sneak in, otherwise the oops used by the
      // dependency logic could have become stale.
      MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      if (nmethod_needs_unregister) {
        Universe::heap()->unregister_nmethod(this);
      }
      flush_dependencies(NULL);
    }

    // zombie only - if a JVMTI agent has enabled the CompiledMethodUnload
    // event and it hasn't already been reported for this nmethod then
    // report it now. The event may have been reported earilier if the GC
    // marked it for unloading). JvmtiDeferredEventQueue support means
    // we no longer go to a safepoint here.
    post_compiled_method_unload();

#ifdef ASSERT
    // It's no longer safe to access the oops section since zombie
    // nmethods aren't scanned for GC.
    _oops_are_stale = true;
#endif
     // the Method may be reclaimed by class unloading now that the
     // nmethod is in zombie state
    set_method(NULL);
  } else {
    assert(state == not_entrant, "other cases may need to be handled differently");
  }

  if (TraceCreateZombies) {
    tty->print_cr("nmethod <" INTPTR_FORMAT "> code made %s", this, (state == not_entrant) ? "not entrant" : "zombie");
  }

  NMethodSweeper::report_state_change(this);
  return true;
}

void nmethod::flush() {
  // Note that there are no valid oops in the nmethod anymore.
  assert(is_zombie() || (is_osr_method() && is_unloaded()), "must be a zombie method");
  assert(is_marked_for_reclamation() || (is_osr_method() && is_unloaded()), "must be marked for reclamation");

  assert (!is_locked_by_vm(), "locked methods shouldn't be flushed");
  assert_locked_or_safepoint(CodeCache_lock);

  // completely deallocate this method
  Events::log(JavaThread::current(), "flushing nmethod " INTPTR_FORMAT, this);
  if (PrintMethodFlushing) {
    tty->print_cr("*flushing nmethod %3d/" INTPTR_FORMAT ". Live blobs:" UINT32_FORMAT "/Free CodeCache:" SIZE_FORMAT "Kb",
        _compile_id, this, CodeCache::nof_blobs(), CodeCache::unallocated_capacity()/1024);
  }

  // We need to deallocate any ExceptionCache data.
  // Note that we do not need to grab the nmethod lock for this, it
  // better be thread safe if we're disposing of it!
  ExceptionCache* ec = exception_cache();
  set_exception_cache(NULL);
  while(ec != NULL) {
    ExceptionCache* next = ec->next();
    delete ec;
    ec = next;
  }

  if (on_scavenge_root_list()) {
    CodeCache::drop_scavenge_root_nmethod(this);
  }

#ifdef SHARK
  ((SharkCompiler *) compiler())->free_compiled_method(insts_begin());
#endif // SHARK

  ((CodeBlob*)(this))->flush();

  CodeCache::free(this);
}


//
// Notify all classes this nmethod is dependent on that it is no
// longer dependent. This should only be called in two situations.
// First, when a nmethod transitions to a zombie all dependents need
// to be clear.  Since zombification happens at a safepoint there's no
// synchronization issues.  The second place is a little more tricky.
// During phase 1 of mark sweep class unloading may happen and as a
// result some nmethods may get unloaded.  In this case the flushing
// of dependencies must happen during phase 1 since after GC any
// dependencies in the unloaded nmethod won't be updated, so
// traversing the dependency information in unsafe.  In that case this
// function is called with a non-NULL argument and this function only
// notifies instanceKlasses that are reachable

void nmethod::flush_dependencies(BoolObjectClosure* is_alive) {
  assert_locked_or_safepoint(CodeCache_lock);
  assert(Universe::heap()->is_gc_active() == (is_alive != NULL),
  "is_alive is non-NULL if and only if we are called during GC");
  if (!has_flushed_dependencies()) {
    set_has_flushed_dependencies();
    for (Dependencies::DepStream deps(this); deps.next(); ) {
      Klass* klass = deps.context_type();
      if (klass == NULL)  continue;  // ignore things like evol_method

      // During GC the is_alive closure is non-NULL, and is used to
      // determine liveness of dependees that need to be updated.
      if (is_alive == NULL || klass->is_loader_alive(is_alive)) {
        InstanceKlass::cast(klass)->remove_dependent_nmethod(this);
      }
    }
  }
}


// If this oop is not live, the nmethod can be unloaded.
bool nmethod::can_unload(BoolObjectClosure* is_alive, oop* root, bool unloading_occurred) {
  assert(root != NULL, "just checking");
  oop obj = *root;
  if (obj == NULL || is_alive->do_object_b(obj)) {
      return false;
  }

  // If ScavengeRootsInCode is true, an nmethod might be unloaded
  // simply because one of its constant oops has gone dead.
  // No actual classes need to be unloaded in order for this to occur.
  assert(unloading_occurred || ScavengeRootsInCode, "Inconsistency in unloading");
  make_unloaded(is_alive, obj);
  return true;
}

// ------------------------------------------------------------------
// post_compiled_method_load_event
// new method for install_code() path
// Transfer information from compilation to jvmti
void nmethod::post_compiled_method_load_event() {

  Method* moop = method();
  HOTSPOT_COMPILED_METHOD_LOAD(
      (char *) moop->klass_name()->bytes(),
      moop->klass_name()->utf8_length(),
      (char *) moop->name()->bytes(),
      moop->name()->utf8_length(),
      (char *) moop->signature()->bytes(),
      moop->signature()->utf8_length(),
      insts_begin(), insts_size());

  if (JvmtiExport::should_post_compiled_method_load() ||
      JvmtiExport::should_post_compiled_method_unload()) {
    get_and_cache_jmethod_id();
  }

  if (JvmtiExport::should_post_compiled_method_load()) {
    // Let the Service thread (which is a real Java thread) post the event
    MutexLockerEx ml(Service_lock, Mutex::_no_safepoint_check_flag);
    JvmtiDeferredEventQueue::enqueue(
      JvmtiDeferredEvent::compiled_method_load_event(this));
  }
}

jmethodID nmethod::get_and_cache_jmethod_id() {
  if (_jmethod_id == NULL) {
    // Cache the jmethod_id since it can no longer be looked up once the
    // method itself has been marked for unloading.
    _jmethod_id = method()->jmethod_id();
  }
  return _jmethod_id;
}

void nmethod::post_compiled_method_unload() {
  if (unload_reported()) {
    // During unloading we transition to unloaded and then to zombie
    // and the unloading is reported during the first transition.
    return;
  }

  assert(_method != NULL && !is_unloaded(), "just checking");
  DTRACE_METHOD_UNLOAD_PROBE(method());

  // If a JVMTI agent has enabled the CompiledMethodUnload event then
  // post the event. Sometime later this nmethod will be made a zombie
  // by the sweeper but the Method* will not be valid at that point.
  // If the _jmethod_id is null then no load event was ever requested
  // so don't bother posting the unload.  The main reason for this is
  // that the jmethodID is a weak reference to the Method* so if
  // it's being unloaded there's no way to look it up since the weak
  // ref will have been cleared.
  if (_jmethod_id != NULL && JvmtiExport::should_post_compiled_method_unload()) {
    assert(!unload_reported(), "already unloaded");
    JvmtiDeferredEvent event =
      JvmtiDeferredEvent::compiled_method_unload_event(this,
          _jmethod_id, insts_begin());
    if (SafepointSynchronize::is_at_safepoint()) {
      // Don't want to take the queueing lock. Add it as pending and
      // it will get enqueued later.
      JvmtiDeferredEventQueue::add_pending_event(event);
    } else {
      MutexLockerEx ml(Service_lock, Mutex::_no_safepoint_check_flag);
      JvmtiDeferredEventQueue::enqueue(event);
    }
  }

  // The JVMTI CompiledMethodUnload event can be enabled or disabled at
  // any time. As the nmethod is being unloaded now we mark it has
  // having the unload event reported - this will ensure that we don't
  // attempt to report the event in the unlikely scenario where the
  // event is enabled at the time the nmethod is made a zombie.
  set_unload_reported();
}

// This is called at the end of the strong tracing/marking phase of a
// GC to unload an nmethod if it contains otherwise unreachable
// oops.

void nmethod::do_unloading(BoolObjectClosure* is_alive, bool unloading_occurred) {
  // Make sure the oop's ready to receive visitors
  assert(!is_zombie() && !is_unloaded(),
         "should not call follow on zombie or unloaded nmethod");

  // If the method is not entrant then a JMP is plastered over the
  // first few bytes.  If an oop in the old code was there, that oop
  // should not get GC'd.  Skip the first few bytes of oops on
  // not-entrant methods.
  address low_boundary = verified_entry_point();
  if (is_not_entrant()) {
    low_boundary += NativeJump::instruction_size;
    // %%% Note:  On SPARC we patch only a 4-byte trap, not a full NativeJump.
    // (See comment above.)
  }

  // The RedefineClasses() API can cause the class unloading invariant
  // to no longer be true. See jvmtiExport.hpp for details.
  // Also, leave a debugging breadcrumb in local flag.
  bool a_class_was_redefined = JvmtiExport::has_redefined_a_class();
  if (a_class_was_redefined) {
    // This set of the unloading_occurred flag is done before the
    // call to post_compiled_method_unload() so that the unloading
    // of this nmethod is reported.
    unloading_occurred = true;
  }

  // Exception cache
  ExceptionCache* ec = exception_cache();
  while (ec != NULL) {
    Klass* ex_klass = ec->exception_type();
    ExceptionCache* next_ec = ec->next();
    if (ex_klass != NULL && !ex_klass->is_loader_alive(is_alive)) {
      remove_from_exception_cache(ec);
    }
    ec = next_ec;
  }

  // If class unloading occurred we first iterate over all inline caches and
  // clear ICs where the cached oop is referring to an unloaded klass or method.
  // The remaining live cached oops will be traversed in the relocInfo::oop_type
  // iteration below.
  if (unloading_occurred) {
    RelocIterator iter(this, low_boundary);
    while(iter.next()) {
      if (iter.type() == relocInfo::virtual_call_type) {
        CompiledIC *ic = CompiledIC_at(iter.reloc());
        if (ic->is_icholder_call()) {
          // The only exception is compiledICHolder oops which may
          // yet be marked below. (We check this further below).
          CompiledICHolder* cichk_oop = ic->cached_icholder();
          if (cichk_oop->holder_method()->method_holder()->is_loader_alive(is_alive) &&
              cichk_oop->holder_klass()->is_loader_alive(is_alive)) {
            continue;
          }
        } else {
          Metadata* ic_oop = ic->cached_metadata();
          if (ic_oop != NULL) {
            if (ic_oop->is_klass()) {
              if (((Klass*)ic_oop)->is_loader_alive(is_alive)) {
                continue;
              }
            } else if (ic_oop->is_method()) {
              if (((Method*)ic_oop)->method_holder()->is_loader_alive(is_alive)) {
                continue;
              }
            } else {
              ShouldNotReachHere();
            }
          }
        }
        ic->set_to_clean();
      }
    }
  }

  // Compiled code
  {
  RelocIterator iter(this, low_boundary);
  while (iter.next()) {
    if (iter.type() == relocInfo::oop_type) {
      oop_Relocation* r = iter.oop_reloc();
      // In this loop, we must only traverse those oops directly embedded in
      // the code.  Other oops (oop_index>0) are seen as part of scopes_oops.
      assert(1 == (r->oop_is_immediate()) +
                  (r->oop_addr() >= oops_begin() && r->oop_addr() < oops_end()),
             "oop must be found in exactly one place");
      if (r->oop_is_immediate() && r->oop_value() != NULL) {
        if (can_unload(is_alive, r->oop_addr(), unloading_occurred)) {
          return;
        }
      }
    }
  }
  }


  // Scopes
  for (oop* p = oops_begin(); p < oops_end(); p++) {
    if (*p == Universe::non_oop_word())  continue;  // skip non-oops
    if (can_unload(is_alive, p, unloading_occurred)) {
      return;
    }
  }

  // Ensure that all metadata is still alive
  verify_metadata_loaders(low_boundary, is_alive);
}

#ifdef ASSERT

class CheckClass : AllStatic {
  static BoolObjectClosure* _is_alive;

  // Check class_loader is alive for this bit of metadata.
  static void check_class(Metadata* md) {
    Klass* klass = NULL;
    if (md->is_klass()) {
      klass = ((Klass*)md);
    } else if (md->is_method()) {
      klass = ((Method*)md)->method_holder();
    } else if (md->is_methodData()) {
      klass = ((MethodData*)md)->method()->method_holder();
    } else {
      md->print();
      ShouldNotReachHere();
    }
    assert(klass->is_loader_alive(_is_alive), "must be alive");
  }
 public:
  static void do_check_class(BoolObjectClosure* is_alive, nmethod* nm) {
    assert(SafepointSynchronize::is_at_safepoint(), "this is only ok at safepoint");
    _is_alive = is_alive;
    nm->metadata_do(check_class);
  }
};

// This is called during a safepoint so can use static data
BoolObjectClosure* CheckClass::_is_alive = NULL;
#endif // ASSERT


// Processing of oop references should have been sufficient to keep
// all strong references alive.  Any weak references should have been
// cleared as well.  Visit all the metadata and ensure that it's
// really alive.
void nmethod::verify_metadata_loaders(address low_boundary, BoolObjectClosure* is_alive) {
#ifdef ASSERT
    RelocIterator iter(this, low_boundary);
    while (iter.next()) {
    // static_stub_Relocations may have dangling references to
    // Method*s so trim them out here.  Otherwise it looks like
    // compiled code is maintaining a link to dead metadata.
    address static_call_addr = NULL;
    if (iter.type() == relocInfo::opt_virtual_call_type) {
      CompiledIC* cic = CompiledIC_at(iter.reloc());
      if (!cic->is_call_to_interpreted()) {
        static_call_addr = iter.addr();
      }
    } else if (iter.type() == relocInfo::static_call_type) {
      CompiledStaticCall* csc = compiledStaticCall_at(iter.reloc());
      if (!csc->is_call_to_interpreted()) {
        static_call_addr = iter.addr();
      }
    }
    if (static_call_addr != NULL) {
      RelocIterator sciter(this, low_boundary);
      while (sciter.next()) {
        if (sciter.type() == relocInfo::static_stub_type &&
            sciter.static_stub_reloc()->static_call() == static_call_addr) {
          sciter.static_stub_reloc()->clear_inline_cache();
        }
      }
    }
  }
  // Check that the metadata embedded in the nmethod is alive
  CheckClass::do_check_class(is_alive, this);
#endif
}


// Iterate over metadata calling this function.   Used by RedefineClasses
void nmethod::metadata_do(void f(Metadata*)) {
  address low_boundary = verified_entry_point();
  if (is_not_entrant()) {
    low_boundary += NativeJump::instruction_size;
    // %%% Note:  On SPARC we patch only a 4-byte trap, not a full NativeJump.
    // (See comment above.)
  }
  {
    // Visit all immediate references that are embedded in the instruction stream.
    RelocIterator iter(this, low_boundary);
    while (iter.next()) {
      if (iter.type() == relocInfo::metadata_type ) {
        metadata_Relocation* r = iter.metadata_reloc();
        // In this lmetadata, we must only follow those metadatas directly embedded in
        // the code.  Other metadatas (oop_index>0) are seen as part of
        // the metadata section below.
        assert(1 == (r->metadata_is_immediate()) +
               (r->metadata_addr() >= metadata_begin() && r->metadata_addr() < metadata_end()),
               "metadata must be found in exactly one place");
        if (r->metadata_is_immediate() && r->metadata_value() != NULL) {
          Metadata* md = r->metadata_value();
          f(md);
        }
      } else if (iter.type() == relocInfo::virtual_call_type) {
        // Check compiledIC holders associated with this nmethod
        CompiledIC *ic = CompiledIC_at(iter.reloc());
        if (ic->is_icholder_call()) {
          CompiledICHolder* cichk = ic->cached_icholder();
          f(cichk->holder_method());
          f(cichk->holder_klass());
        } else {
          Metadata* ic_oop = ic->cached_metadata();
          if (ic_oop != NULL) {
            f(ic_oop);
          }
        }
      }
    }
  }

  // Visit the metadata section
  for (Metadata** p = metadata_begin(); p < metadata_end(); p++) {
    if (*p == Universe::non_oop_word() || *p == NULL)  continue;  // skip non-oops
    Metadata* md = *p;
    f(md);
  }

  // Call function Method*, not embedded in these other places.
  if (_method != NULL) f(_method);
}

void nmethod::oops_do(OopClosure* f, bool allow_zombie) {
  // make sure the oops ready to receive visitors
  assert(allow_zombie || !is_zombie(), "should not call follow on zombie nmethod");
  assert(!is_unloaded(), "should not call follow on unloaded nmethod");

  // If the method is not entrant or zombie then a JMP is plastered over the
  // first few bytes.  If an oop in the old code was there, that oop
  // should not get GC'd.  Skip the first few bytes of oops on
  // not-entrant methods.
  address low_boundary = verified_entry_point();
  if (is_not_entrant()) {
    low_boundary += NativeJump::instruction_size;
    // %%% Note:  On SPARC we patch only a 4-byte trap, not a full NativeJump.
    // (See comment above.)
  }

  RelocIterator iter(this, low_boundary);

  while (iter.next()) {
    if (iter.type() == relocInfo::oop_type ) {
      oop_Relocation* r = iter.oop_reloc();
      // In this loop, we must only follow those oops directly embedded in
      // the code.  Other oops (oop_index>0) are seen as part of scopes_oops.
      assert(1 == (r->oop_is_immediate()) +
                   (r->oop_addr() >= oops_begin() && r->oop_addr() < oops_end()),
             "oop must be found in exactly one place");
      if (r->oop_is_immediate() && r->oop_value() != NULL) {
        f->do_oop(r->oop_addr());
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

#define NMETHOD_SENTINEL ((nmethod*)badAddress)

nmethod* volatile nmethod::_oops_do_mark_nmethods;

// An nmethod is "marked" if its _mark_link is set non-null.
// Even if it is the end of the linked list, it will have a non-null link value,
// as long as it is on the list.
// This code must be MP safe, because it is used from parallel GC passes.
bool nmethod::test_set_oops_do_mark() {
  assert(nmethod::oops_do_marking_is_active(), "oops_do_marking_prologue must be called");
  nmethod* observed_mark_link = _oops_do_mark_link;
  if (observed_mark_link == NULL) {
    // Claim this nmethod for this thread to mark.
    observed_mark_link = (nmethod*)
      Atomic::cmpxchg_ptr(NMETHOD_SENTINEL, &_oops_do_mark_link, NULL);
    if (observed_mark_link == NULL) {

      // Atomically append this nmethod (now claimed) to the head of the list:
      nmethod* observed_mark_nmethods = _oops_do_mark_nmethods;
      for (;;) {
        nmethod* required_mark_nmethods = observed_mark_nmethods;
        _oops_do_mark_link = required_mark_nmethods;
        observed_mark_nmethods = (nmethod*)
          Atomic::cmpxchg_ptr(this, &_oops_do_mark_nmethods, required_mark_nmethods);
        if (observed_mark_nmethods == required_mark_nmethods)
          break;
      }
      // Mark was clear when we first saw this guy.
      NOT_PRODUCT(if (TraceScavenge)  print_on(tty, "oops_do, mark"));
      return false;
    }
  }
  // On fall through, another racing thread marked this nmethod before we did.
  return true;
}

void nmethod::oops_do_marking_prologue() {
  NOT_PRODUCT(if (TraceScavenge)  tty->print_cr("[oops_do_marking_prologue"));
  assert(_oops_do_mark_nmethods == NULL, "must not call oops_do_marking_prologue twice in a row");
  // We use cmpxchg_ptr instead of regular assignment here because the user
  // may fork a bunch of threads, and we need them all to see the same state.
  void* observed = Atomic::cmpxchg_ptr(NMETHOD_SENTINEL, &_oops_do_mark_nmethods, NULL);
  guarantee(observed == NULL, "no races in this sequential code");
}

void nmethod::oops_do_marking_epilogue() {
  assert(_oops_do_mark_nmethods != NULL, "must not call oops_do_marking_epilogue twice in a row");
  nmethod* cur = _oops_do_mark_nmethods;
  while (cur != NMETHOD_SENTINEL) {
    assert(cur != NULL, "not NULL-terminated");
    nmethod* next = cur->_oops_do_mark_link;
    cur->_oops_do_mark_link = NULL;
    cur->fix_oop_relocations();
    NOT_PRODUCT(if (TraceScavenge)  cur->print_on(tty, "oops_do, unmark"));
    cur = next;
  }
  void* required = _oops_do_mark_nmethods;
  void* observed = Atomic::cmpxchg_ptr(NULL, &_oops_do_mark_nmethods, required);
  guarantee(observed == required, "no races in this sequential code");
  NOT_PRODUCT(if (TraceScavenge)  tty->print_cr("oops_do_marking_epilogue]"));
}

class DetectScavengeRoot: public OopClosure {
  bool     _detected_scavenge_root;
public:
  DetectScavengeRoot() : _detected_scavenge_root(false)
  { NOT_PRODUCT(_print_nm = NULL); }
  bool detected_scavenge_root() { return _detected_scavenge_root; }
  virtual void do_oop(oop* p) {
    if ((*p) != NULL && (*p)->is_scavengable()) {
      NOT_PRODUCT(maybe_print(p));
      _detected_scavenge_root = true;
    }
  }
  virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }

#ifndef PRODUCT
  nmethod* _print_nm;
  void maybe_print(oop* p) {
    if (_print_nm == NULL)  return;
    if (!_detected_scavenge_root)  _print_nm->print_on(tty, "new scavenge root");
    tty->print_cr(""PTR_FORMAT"[offset=%d] detected scavengable oop "PTR_FORMAT" (found at "PTR_FORMAT")",
                  _print_nm, (int)((intptr_t)p - (intptr_t)_print_nm),
                  (void *)(*p), (intptr_t)p);
    (*p)->print();
  }
#endif //PRODUCT
};

bool nmethod::detect_scavenge_root_oops() {
  DetectScavengeRoot detect_scavenge_root;
  NOT_PRODUCT(if (TraceScavenge)  detect_scavenge_root._print_nm = this);
  oops_do(&detect_scavenge_root);
  return detect_scavenge_root.detected_scavenge_root();
}

// Method that knows how to preserve outgoing arguments at call. This method must be
// called with a frame corresponding to a Java invoke
void nmethod::preserve_callee_argument_oops(frame fr, const RegisterMap *reg_map, OopClosure* f) {
#ifndef SHARK
  if (!method()->is_native()) {
    SimpleScopeDesc ssd(this, fr.pc());
    Bytecode_invoke call(ssd.method(), ssd.bci());
    bool has_receiver = call.has_receiver();
    bool has_appendix = call.has_appendix();
    Symbol* signature = call.signature();
    fr.oops_compiled_arguments_do(signature, has_receiver, has_appendix, reg_map, f);
  }
#endif // !SHARK
}


oop nmethod::embeddedOop_at(u_char* p) {
  RelocIterator iter(this, p, p + 1);
  while (iter.next())
    if (iter.type() == relocInfo::oop_type) {
      return iter.oop_reloc()->oop_value();
    }
  return NULL;
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
  assert(has_method_handle_invokes() == (_deoptimize_mh_offset != -1), "must have deopt mh handler");

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
static PcDesc* linear_search(nmethod* nm, int pc_offset, bool approximate) {
  PcDesc* lower = nm->scopes_pcs_begin();
  PcDesc* upper = nm->scopes_pcs_end();
  lower += 1; // exclude initial sentinel
  PcDesc* res = NULL;
  for (PcDesc* p = lower; p < upper; p++) {
    NOT_PRODUCT(--nmethod_stats.pc_desc_tests);  // don't count this call to match_desc
    if (match_desc(p, pc_offset, approximate)) {
      if (res == NULL)
        res = p;
      else
        res = (PcDesc*) badAddress;
    }
  }
  return res;
}
#endif


// Finds a PcDesc with real-pc equal to "pc"
PcDesc* nmethod::find_pc_desc_internal(address pc, bool approximate) {
  address base_address = code_begin();
  if ((pc < base_address) ||
      (pc - base_address) >= (ptrdiff_t) PcDesc::upper_offset_limit) {
    return NULL;  // PC is wildly out of range
  }
  int pc_offset = (int) (pc - base_address);

  // Check the PcDesc cache if it contains the desired PcDesc
  // (This as an almost 100% hit rate.)
  PcDesc* res = _pc_desc_cache.find_pc_desc(pc_offset, approximate);
  if (res != NULL) {
    assert(res == linear_search(this, pc_offset, approximate), "cache ok");
    return res;
  }

  // Fallback algorithm: quasi-linear search for the PcDesc
  // Find the last pc_offset less than the given offset.
  // The successor must be the required match, if there is a match at all.
  // (Use a fixed radix to avoid expensive affine pointer arithmetic.)
  PcDesc* lower = scopes_pcs_begin();
  PcDesc* upper = scopes_pcs_end();
  upper -= 1; // exclude final sentinel
  if (lower >= upper)  return NULL;  // native method; no PcDescs at all

#define assert_LU_OK \
  /* invariant on lower..upper during the following search: */ \
  assert(lower->pc_offset() <  pc_offset, "sanity"); \
  assert(upper->pc_offset() >= pc_offset, "sanity")
  assert_LU_OK;

  // Use the last successful return as a split point.
  PcDesc* mid = _pc_desc_cache.last_pc_desc();
  NOT_PRODUCT(++nmethod_stats.pc_desc_searches);
  if (mid->pc_offset() < pc_offset) {
    lower = mid;
  } else {
    upper = mid;
  }

  // Take giant steps at first (4096, then 256, then 16, then 1)
  const int LOG2_RADIX = 4 /*smaller steps in debug mode:*/ debug_only(-1);
  const int RADIX = (1 << LOG2_RADIX);
  for (int step = (1 << (LOG2_RADIX*3)); step > 1; step >>= LOG2_RADIX) {
    while ((mid = lower + step) < upper) {
      assert_LU_OK;
      NOT_PRODUCT(++nmethod_stats.pc_desc_searches);
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
    NOT_PRODUCT(++nmethod_stats.pc_desc_searches);
    if (mid->pc_offset() < pc_offset) {
      lower = mid;
    } else {
      upper = mid;
      break;
    }
  }
#undef assert_LU_OK

  if (match_desc(upper, pc_offset, approximate)) {
    assert(upper == linear_search(this, pc_offset, approximate), "search ok");
    _pc_desc_cache.add_pc_desc(upper);
    return upper;
  } else {
    assert(NULL == linear_search(this, pc_offset, approximate), "search ok");
    return NULL;
  }
}


void nmethod::check_all_dependencies(DepChange& changes) {
  // Checked dependencies are allocated into this ResourceMark
  ResourceMark rm;

  // Turn off dependency tracing while actually testing dependencies.
  NOT_PRODUCT( FlagSetting fs(TraceDependencies, false) );

 GenericHashtable<DependencySignature, ResourceObj>* table = new GenericHashtable<DependencySignature, ResourceObj>(11027);
  // Iterate over live nmethods and check dependencies of all nmethods that are not
  // marked for deoptimization. A particular dependency is only checked once.
  for(nmethod* nm = CodeCache::alive_nmethod(CodeCache::first()); nm != NULL; nm = CodeCache::alive_nmethod(CodeCache::next(nm))) {
    if (!nm->is_marked_for_deoptimization()) {
      for (Dependencies::DepStream deps(nm); deps.next(); ) {
        // Construct abstraction of a dependency.
        DependencySignature* current_sig = new DependencySignature(deps);
        // Determine if 'deps' is already checked. table->add() returns
        // 'true' if the dependency was added (i.e., was not in the hashtable).
        if (table->add(current_sig)) {
          if (deps.check_dependency() != NULL) {
            // Dependency checking failed. Print out information about the failed
            // dependency and finally fail with an assert. We can fail here, since
            // dependency checking is never done in a product build.
            changes.print();
            nm->print();
            nm->print_dependencies();
            assert(false, "Should have been marked for deoptimization");
          }
        }
      }
    }
  }
}

bool nmethod::check_dependency_on(DepChange& changes) {
  // What has happened:
  // 1) a new class dependee has been added
  // 2) dependee and all its super classes have been marked
  bool found_check = false;  // set true if we are upset
  for (Dependencies::DepStream deps(this); deps.next(); ) {
    // Evaluate only relevant dependencies.
    if (deps.spot_check_dependency_at(changes) != NULL) {
      found_check = true;
      NOT_DEBUG(break);
    }
  }
  return found_check;
}

bool nmethod::is_evol_dependent_on(Klass* dependee) {
  InstanceKlass *dependee_ik = InstanceKlass::cast(dependee);
  Array<Method*>* dependee_methods = dependee_ik->methods();
  for (Dependencies::DepStream deps(this); deps.next(); ) {
    if (deps.type() == Dependencies::evol_method) {
      Method* method = deps.method_argument(0);
      for (int j = 0; j < dependee_methods->length(); j++) {
        if (dependee_methods->at(j) == method) {
          // RC_TRACE macro has an embedded ResourceMark
          RC_TRACE(0x01000000,
            ("Found evol dependency of nmethod %s.%s(%s) compile_id=%d on method %s.%s(%s)",
            _method->method_holder()->external_name(),
            _method->name()->as_C_string(),
            _method->signature()->as_C_string(), compile_id(),
            method->method_holder()->external_name(),
            method->name()->as_C_string(),
            method->signature()->as_C_string()));
          if (TraceDependencies || LogCompilation)
            deps.log_dependency(dependee);
          return true;
        }
      }
    }
  }
  return false;
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


bool nmethod::is_patchable_at(address instr_addr) {
  assert(insts_contains(instr_addr), "wrong nmethod used");
  if (is_zombie()) {
    // a zombie may never be patched
    return false;
  }
  return true;
}


address nmethod::continuation_for_implicit_exception(address pc) {
  // Exception happened outside inline-cache check code => we are inside
  // an active nmethod => use cpc to determine a return address
  int exception_offset = pc - code_begin();
  int cont_offset = ImplicitExceptionTable(this).at( exception_offset );
#ifdef ASSERT
  if (cont_offset == 0) {
    Thread* thread = ThreadLocalStorage::get_thread_slow();
    ResetNoHandleMark rnm; // Might be called from LEAF/QUICK ENTRY
    HandleMark hm(thread);
    ResourceMark rm(thread);
    CodeBlob* cb = CodeCache::find_blob(pc);
    assert(cb != NULL && cb == this, "");
    tty->print_cr("implicit exception happened at " INTPTR_FORMAT, pc);
    print();
    method()->print_codes();
    print_code();
    print_pcs();
  }
#endif
  if (cont_offset == 0) {
    // Let the normal error handling report the exception
    return NULL;
  }
  return code_begin() + cont_offset;
}



void nmethod_init() {
  // make sure you didn't forget to adjust the filler fields
  assert(sizeof(nmethod) % oopSize == 0, "nmethod size must be multiple of a word");
}


//-------------------------------------------------------------------------------------------


// QQQ might we make this work from a frame??
nmethodLocker::nmethodLocker(address pc) {
  CodeBlob* cb = CodeCache::find_blob(pc);
  guarantee(cb != NULL && cb->is_nmethod(), "bad pc for a nmethod found");
  _nm = (nmethod*)cb;
  lock_nmethod(_nm);
}

// Only JvmtiDeferredEvent::compiled_method_unload_event()
// should pass zombie_ok == true.
void nmethodLocker::lock_nmethod(nmethod* nm, bool zombie_ok) {
  if (nm == NULL)  return;
  Atomic::inc(&nm->_lock_count);
  guarantee(zombie_ok || !nm->is_zombie(), "cannot lock a zombie method");
}

void nmethodLocker::unlock_nmethod(nmethod* nm) {
  if (nm == NULL)  return;
  Atomic::dec(&nm->_lock_count);
  guarantee(nm->_lock_count >= 0, "unmatched nmethod lock/unlock");
}


// -----------------------------------------------------------------------------
// nmethod::get_deopt_original_pc
//
// Return the original PC for the given PC if:
// (a) the given PC belongs to a nmethod and
// (b) it is a deopt PC
address nmethod::get_deopt_original_pc(const frame* fr) {
  if (fr->cb() == NULL)  return NULL;

  nmethod* nm = fr->cb()->as_nmethod_or_null();
  if (nm != NULL && nm->is_deopt_pc(fr->pc()))
    return nm->get_original_pc(fr);

  return NULL;
}


// -----------------------------------------------------------------------------
// MethodHandle

bool nmethod::is_method_handle_return(address return_pc) {
  if (!has_method_handle_invokes())  return false;
  PcDesc* pd = pc_desc_at(return_pc);
  if (pd == NULL)
    return false;
  return pd->is_method_handle_invoke();
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
    if ((*p) == NULL || (*p)->is_oop())  return;
    if (_ok) {
      _nm->print_nmethod(true);
      _ok = false;
    }
    tty->print_cr("*** non-oop "PTR_FORMAT" found at "PTR_FORMAT" (offset %d)",
                  (void *)(*p), (intptr_t)p, (int)((intptr_t)p - (intptr_t)_nm));
  }
  virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }
};

void nmethod::verify() {

  // Hmm. OSR methods can be deopted but not marked as zombie or not_entrant
  // seems odd.

  if( is_zombie() || is_not_entrant() )
    return;

  // Make sure all the entry points are correctly aligned for patching.
  NativeJump::check_verified_entry_alignment(entry_point(), verified_entry_point());

  // assert(method()->is_oop(), "must be valid");

  ResourceMark rm;

  if (!CodeCache::contains(this)) {
    fatal(err_msg("nmethod at " INTPTR_FORMAT " not in zone", this));
  }

  if(is_native_method() )
    return;

  nmethod* nm = CodeCache::find_nmethod(verified_entry_point());
  if (nm != this) {
    fatal(err_msg("findNMethod did not find this nmethod (" INTPTR_FORMAT ")",
                  this));
  }

  for (PcDesc* p = scopes_pcs_begin(); p < scopes_pcs_end(); p++) {
    if (! p->verify(this)) {
      tty->print_cr("\t\tin nmethod at " INTPTR_FORMAT " (pcs)", this);
    }
  }

  VerifyOopsClosure voc(this);
  oops_do(&voc);
  assert(voc.ok(), "embedded oops must be OK");
  verify_scavenge_root_oops();

  verify_scopes();
}


void nmethod::verify_interrupt_point(address call_site) {
  // Verify IC only when nmethod installation is finished.
  bool is_installed = (method()->code() == this) // nmethod is in state 'in_use' and installed
                      || !this->is_in_use();     // nmethod is installed, but not in 'in_use' state
  if (is_installed) {
    Thread *cur = Thread::current();
    if (CompiledIC_lock->owner() == cur ||
        ((cur->is_VM_thread() || cur->is_ConcurrentGC_thread()) &&
         SafepointSynchronize::is_at_safepoint())) {
      CompiledIC_at(this, call_site);
      CHECK_UNHANDLED_OOPS_ONLY(Thread::current()->clear_unhandled_oops());
    } else {
      MutexLocker ml_verify (CompiledIC_lock);
      CompiledIC_at(this, call_site);
    }
  }

  PcDesc* pd = pc_desc_at(nativeCall_at(call_site)->return_address());
  assert(pd != NULL, "PcDesc must exist");
  for (ScopeDesc* sd = new ScopeDesc(this, pd->scope_decode_offset(),
                                     pd->obj_decode_offset(), pd->should_reexecute(),
                                     pd->return_oop());
       !sd->is_top(); sd = sd->sender()) {
    sd->verify();
  }
}

void nmethod::verify_scopes() {
  if( !method() ) return;       // Runtime stubs have no scope
  if (method()->is_native()) return; // Ignore stub methods.
  // iterate through all interrupt point
  // and verify the debug information is valid.
  RelocIterator iter((nmethod*)this);
  while (iter.next()) {
    address stub = NULL;
    switch (iter.type()) {
      case relocInfo::virtual_call_type:
        verify_interrupt_point(iter.addr());
        break;
      case relocInfo::opt_virtual_call_type:
        stub = iter.opt_virtual_call_reloc()->static_stub();
        verify_interrupt_point(iter.addr());
        break;
      case relocInfo::static_call_type:
        stub = iter.static_call_reloc()->static_stub();
        //verify_interrupt_point(iter.addr());
        break;
      case relocInfo::runtime_call_type:
        address destination = iter.reloc()->value();
        // Right now there is no way to find out which entries support
        // an interrupt point.  It would be nice if we had this
        // information in a table.
        break;
    }
    assert(stub == NULL || stub_contains(stub), "static call stub outside stub section");
  }
}


// -----------------------------------------------------------------------------
// Non-product code
#ifndef PRODUCT

class DebugScavengeRoot: public OopClosure {
  nmethod* _nm;
  bool     _ok;
public:
  DebugScavengeRoot(nmethod* nm) : _nm(nm), _ok(true) { }
  bool ok() { return _ok; }
  virtual void do_oop(oop* p) {
    if ((*p) == NULL || !(*p)->is_scavengable())  return;
    if (_ok) {
      _nm->print_nmethod(true);
      _ok = false;
    }
    tty->print_cr("*** scavengable oop "PTR_FORMAT" found at "PTR_FORMAT" (offset %d)",
                  (void *)(*p), (intptr_t)p, (int)((intptr_t)p - (intptr_t)_nm));
    (*p)->print();
  }
  virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }
};

void nmethod::verify_scavenge_root_oops() {
  if (!on_scavenge_root_list()) {
    // Actually look inside, to verify the claim that it's clean.
    DebugScavengeRoot debug_scavenge_root(this);
    oops_do(&debug_scavenge_root);
    if (!debug_scavenge_root.ok())
      fatal("found an unadvertised bad scavengable oop in the code cache");
  }
  assert(scavenge_root_not_marked(), "");
}

#endif // PRODUCT

// Printing operations

void nmethod::print() const {
  ResourceMark rm;
  ttyLocker ttyl;   // keep the following output all in one block

  tty->print("Compiled method ");

  if (is_compiled_by_c1()) {
    tty->print("(c1) ");
  } else if (is_compiled_by_c2()) {
    tty->print("(c2) ");
  } else if (is_compiled_by_shark()) {
    tty->print("(shark) ");
  } else {
    tty->print("(nm) ");
  }

  print_on(tty, NULL);

  if (WizardMode) {
    tty->print("((nmethod*) "INTPTR_FORMAT ") ", this);
    tty->print(" for method " INTPTR_FORMAT , (address)method());
    tty->print(" { ");
    if (is_in_use())      tty->print("in_use ");
    if (is_not_entrant()) tty->print("not_entrant ");
    if (is_zombie())      tty->print("zombie ");
    if (is_unloaded())    tty->print("unloaded ");
    if (on_scavenge_root_list())  tty->print("scavenge_root ");
    tty->print_cr("}:");
  }
  if (size              () > 0) tty->print_cr(" total in heap  [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              (address)this,
                                              (address)this + size(),
                                              size());
  if (relocation_size   () > 0) tty->print_cr(" relocation     [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              relocation_begin(),
                                              relocation_end(),
                                              relocation_size());
  if (consts_size       () > 0) tty->print_cr(" constants      [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              consts_begin(),
                                              consts_end(),
                                              consts_size());
  if (insts_size        () > 0) tty->print_cr(" main code      [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              insts_begin(),
                                              insts_end(),
                                              insts_size());
  if (stub_size         () > 0) tty->print_cr(" stub code      [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              stub_begin(),
                                              stub_end(),
                                              stub_size());
  if (oops_size         () > 0) tty->print_cr(" oops           [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              oops_begin(),
                                              oops_end(),
                                              oops_size());
  if (metadata_size      () > 0) tty->print_cr(" metadata       [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              metadata_begin(),
                                              metadata_end(),
                                              metadata_size());
  if (scopes_data_size  () > 0) tty->print_cr(" scopes data    [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              scopes_data_begin(),
                                              scopes_data_end(),
                                              scopes_data_size());
  if (scopes_pcs_size   () > 0) tty->print_cr(" scopes pcs     [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              scopes_pcs_begin(),
                                              scopes_pcs_end(),
                                              scopes_pcs_size());
  if (dependencies_size () > 0) tty->print_cr(" dependencies   [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              dependencies_begin(),
                                              dependencies_end(),
                                              dependencies_size());
  if (handler_table_size() > 0) tty->print_cr(" handler table  [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              handler_table_begin(),
                                              handler_table_end(),
                                              handler_table_size());
  if (nul_chk_table_size() > 0) tty->print_cr(" nul chk table  [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              nul_chk_table_begin(),
                                              nul_chk_table_end(),
                                              nul_chk_table_size());
}

void nmethod::print_code() {
  HandleMark hm;
  ResourceMark m;
  Disassembler::decode(this);
}


#ifndef PRODUCT

void nmethod::print_scopes() {
  // Find the first pc desc for all scopes in the code and print it.
  ResourceMark rm;
  for (PcDesc* p = scopes_pcs_begin(); p < scopes_pcs_end(); p++) {
    if (p->scope_decode_offset() == DebugInformationRecorder::serialized_null)
      continue;

    ScopeDesc* sd = scope_desc_at(p->real_pc(this));
    sd->print_on(tty, p);
  }
}

void nmethod::print_dependencies() {
  ResourceMark rm;
  ttyLocker ttyl;   // keep the following output all in one block
  tty->print_cr("Dependencies:");
  for (Dependencies::DepStream deps(this); deps.next(); ) {
    deps.print_dependency();
    Klass* ctxk = deps.context_type();
    if (ctxk != NULL) {
      if (ctxk->oop_is_instance() && ((InstanceKlass*)ctxk)->is_dependent_nmethod(this)) {
        tty->print_cr("   [nmethod<=klass]%s", ctxk->external_name());
      }
    }
    deps.log_dependency();  // put it into the xml log also
  }
}


void nmethod::print_relocations() {
  ResourceMark m;       // in case methods get printed via the debugger
  tty->print_cr("relocations:");
  RelocIterator iter(this);
  iter.print();
  if (UseRelocIndex) {
    jint* index_end   = (jint*)relocation_end() - 1;
    jint  index_size  = *index_end;
    jint* index_start = (jint*)( (address)index_end - index_size );
    tty->print_cr("    index @" INTPTR_FORMAT ": index_size=%d", index_start, index_size);
    if (index_size > 0) {
      jint* ip;
      for (ip = index_start; ip+2 <= index_end; ip += 2)
        tty->print_cr("  (%d %d) addr=" INTPTR_FORMAT " @" INTPTR_FORMAT,
                      ip[0],
                      ip[1],
                      header_end()+ip[0],
                      relocation_begin()-1+ip[1]);
      for (; ip < index_end; ip++)
        tty->print_cr("  (%d ?)", ip[0]);
      tty->print_cr("          @" INTPTR_FORMAT ": index_size=%d", ip, *ip);
      ip++;
      tty->print_cr("reloc_end @" INTPTR_FORMAT ":", ip);
    }
  }
}


void nmethod::print_pcs() {
  ResourceMark m;       // in case methods get printed via debugger
  tty->print_cr("pc-bytecode offsets:");
  for (PcDesc* p = scopes_pcs_begin(); p < scopes_pcs_end(); p++) {
    p->print(this);
  }
}

#endif // PRODUCT

const char* nmethod::reloc_string_for(u_char* begin, u_char* end) {
  RelocIterator iter(this, begin, end);
  bool have_one = false;
  while (iter.next()) {
    have_one = true;
    switch (iter.type()) {
        case relocInfo::none:                  return "no_reloc";
        case relocInfo::oop_type: {
          stringStream st;
          oop_Relocation* r = iter.oop_reloc();
          oop obj = r->oop_value();
          st.print("oop(");
          if (obj == NULL) st.print("NULL");
          else obj->print_value_on(&st);
          st.print(")");
          return st.as_string();
        }
        case relocInfo::metadata_type: {
          stringStream st;
          metadata_Relocation* r = iter.metadata_reloc();
          Metadata* obj = r->metadata_value();
          st.print("metadata(");
          if (obj == NULL) st.print("NULL");
          else obj->print_value_on(&st);
          st.print(")");
          return st.as_string();
        }
        case relocInfo::virtual_call_type:     return "virtual_call";
        case relocInfo::opt_virtual_call_type: return "optimized virtual_call";
        case relocInfo::static_call_type:      return "static_call";
        case relocInfo::static_stub_type:      return "static_stub";
        case relocInfo::runtime_call_type:     return "runtime_call";
        case relocInfo::external_word_type:    return "external_word";
        case relocInfo::internal_word_type:    return "internal_word";
        case relocInfo::section_word_type:     return "section_word";
        case relocInfo::poll_type:             return "poll";
        case relocInfo::poll_return_type:      return "poll_return";
        case relocInfo::type_mask:             return "type_bit_mask";
    }
  }
  return have_one ? "other" : NULL;
}

// Return a the last scope in (begin..end]
ScopeDesc* nmethod::scope_desc_in(address begin, address end) {
  PcDesc* p = pc_desc_near(begin+1);
  if (p != NULL && p->real_pc(this) <= end) {
    return new ScopeDesc(this, p->scope_decode_offset(),
                         p->obj_decode_offset(), p->should_reexecute(),
                         p->return_oop());
  }
  return NULL;
}

void nmethod::print_nmethod_labels(outputStream* stream, address block_begin) const {
  if (block_begin == entry_point())             stream->print_cr("[Entry Point]");
  if (block_begin == verified_entry_point())    stream->print_cr("[Verified Entry Point]");
  if (block_begin == exception_begin())         stream->print_cr("[Exception Handler]");
  if (block_begin == stub_begin())              stream->print_cr("[Stub Code]");
  if (block_begin == deopt_handler_begin())     stream->print_cr("[Deopt Handler Code]");

  if (has_method_handle_invokes())
    if (block_begin == deopt_mh_handler_begin())  stream->print_cr("[Deopt MH Handler Code]");

  if (block_begin == consts_begin())            stream->print_cr("[Constants]");

  if (block_begin == entry_point()) {
    methodHandle m = method();
    if (m.not_null()) {
      stream->print("  # ");
      m->print_value_on(stream);
      stream->cr();
    }
    if (m.not_null() && !is_osr_method()) {
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
      intptr_t out_preserve = SharedRuntime::java_calling_convention(sig_bt, regs, sizeargs, false);
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
          if (!at_this && ss.is_object()) {
            Symbol* name = ss.as_symbol_or_null();
            if (name != NULL) {
              name->print_value_on(stream);
              did_name = true;
            }
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

void nmethod::print_code_comment_on(outputStream* st, int column, u_char* begin, u_char* end) {
  // First, find an oopmap in (begin, end].
  // We use the odd half-closed interval so that oop maps and scope descs
  // which are tied to the byte after a call are printed with the call itself.
  address base = code_begin();
  OopMapSet* oms = oop_maps();
  if (oms != NULL) {
    for (int i = 0, imax = oms->size(); i < imax; i++) {
      OopMap* om = oms->at(i);
      address pc = base + om->offset();
      if (pc > begin) {
        if (pc <= end) {
          st->move_to(column);
          st->print("; ");
          om->print_on(st);
        }
        break;
      }
    }
  }

  // Print any debug info present at this pc.
  ScopeDesc* sd  = scope_desc_in(begin, end);
  if (sd != NULL) {
    st->move_to(column);
    if (sd->bci() == SynchronizationEntryBCI) {
      st->print(";*synchronization entry");
    } else {
      if (sd->method() == NULL) {
        st->print("method is NULL");
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
            Bytecode_invoke invoke(sd->method(), sd->bci());
            st->print(" ");
            if (invoke.name() != NULL)
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
            Bytecode_field field(sd->method(), sd->bci());
            st->print(" ");
            if (field.name() != NULL)
              field.name()->print_symbol_on(st);
            else
              st->print("<UNKNOWN>");
          }
        }
      }
    }

    // Print all scopes
    for (;sd != NULL; sd = sd->sender()) {
      st->move_to(column);
      st->print("; -");
      if (sd->method() == NULL) {
        st->print("method is NULL");
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
  const char* str = reloc_string_for(begin, end);
  if (str != NULL) {
    if (sd != NULL) st->cr();
    st->move_to(column);
    st->print(";   {%s}", str);
  }
  int cont_offset = ImplicitExceptionTable(this).at(begin - code_begin());
  if (cont_offset != 0) {
    st->move_to(column);
    st->print("; implicit exception: dispatches to " INTPTR_FORMAT, code_begin() + cont_offset);
  }

}

#ifndef PRODUCT

void nmethod::print_value_on(outputStream* st) const {
  st->print("nmethod");
  print_on(st, NULL);
}

void nmethod::print_calls(outputStream* st) {
  RelocIterator iter(this);
  while (iter.next()) {
    switch (iter.type()) {
    case relocInfo::virtual_call_type:
    case relocInfo::opt_virtual_call_type: {
      VerifyMutexLocker mc(CompiledIC_lock);
      CompiledIC_at(iter.reloc())->print();
      break;
    }
    case relocInfo::static_call_type:
      st->print_cr("Static call at " INTPTR_FORMAT, iter.reloc()->addr());
      compiledStaticCall_at(iter.reloc())->print();
      break;
    }
  }
}

void nmethod::print_handler_table() {
  ExceptionHandlerTable(this).print();
}

void nmethod::print_nul_chk_table() {
  ImplicitExceptionTable(this).print(code_begin());
}

void nmethod::print_statistics() {
  ttyLocker ttyl;
  if (xtty != NULL)  xtty->head("statistics type='nmethod'");
  nmethod_stats.print_native_nmethod_stats();
  nmethod_stats.print_nmethod_stats();
  DebugInformationRecorder::print_statistics();
  nmethod_stats.print_pc_stats();
  Dependencies::print_statistics();
  if (xtty != NULL)  xtty->tail("statistics");
}

#endif // PRODUCT
