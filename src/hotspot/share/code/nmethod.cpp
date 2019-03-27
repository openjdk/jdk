/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "code/codeCache.hpp"
#include "code/compiledIC.hpp"
#include "code/compiledMethod.inline.hpp"
#include "code/dependencies.hpp"
#include "code/nativeInst.hpp"
#include "code/nmethod.hpp"
#include "code/scopeDesc.hpp"
#include "compiler/abstractCompiler.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compileLog.hpp"
#include "compiler/compilerDirectives.hpp"
#include "compiler/directivesParser.hpp"
#include "compiler/disassembler.hpp"
#include "interpreter/bytecode.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/method.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiImpl.hpp"
#include "runtime/atomic.hpp"
#include "runtime/flags/flagSetting.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/sweeper.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/align.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/events.hpp"
#include "utilities/resourceHash.hpp"
#include "utilities/xmlstream.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmciJavaClasses.hpp"
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

//---------------------------------------------------------------------------------
// NMethod statistics
// They are printed under various flags, including:
//   PrintC1Statistics, PrintOptoStatistics, LogVMOutput, and LogCompilation.
// (In the latter two cases, they like other stats are printed to the log only.)

#ifndef PRODUCT
// These variables are put into one block to reduce relocations
// and make it simpler to print from the debugger.
struct java_nmethod_stats_struct {
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
  int metadata_size;

  void note_nmethod(nmethod* nm) {
    nmethod_count += 1;
    total_size          += nm->size();
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
  }
  void print_nmethod_stats(const char* name) {
    if (nmethod_count == 0)  return;
    tty->print_cr("Statistics for %d bytecoded nmethods for %s:", nmethod_count, name);
    if (total_size != 0)          tty->print_cr(" total in heap  = %d", total_size);
    if (nmethod_count != 0)       tty->print_cr(" header         = " SIZE_FORMAT, nmethod_count * sizeof(nmethod));
    if (relocation_size != 0)     tty->print_cr(" relocation     = %d", relocation_size);
    if (consts_size != 0)         tty->print_cr(" constants      = %d", consts_size);
    if (insts_size != 0)          tty->print_cr(" main code      = %d", insts_size);
    if (stub_size != 0)           tty->print_cr(" stub code      = %d", stub_size);
    if (oops_size != 0)           tty->print_cr(" oops           = %d", oops_size);
    if (metadata_size != 0)       tty->print_cr(" metadata       = %d", metadata_size);
    if (scopes_data_size != 0)    tty->print_cr(" scopes data    = %d", scopes_data_size);
    if (scopes_pcs_size != 0)     tty->print_cr(" scopes pcs     = %d", scopes_pcs_size);
    if (dependencies_size != 0)   tty->print_cr(" dependencies   = %d", dependencies_size);
    if (handler_table_size != 0)  tty->print_cr(" handler table  = %d", handler_table_size);
    if (nul_chk_table_size != 0)  tty->print_cr(" nul chk table  = %d", nul_chk_table_size);
  }
};

struct native_nmethod_stats_struct {
  int native_nmethod_count;
  int native_total_size;
  int native_relocation_size;
  int native_insts_size;
  int native_oops_size;
  int native_metadata_size;
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
    tty->print_cr("Statistics for %d native nmethods:", native_nmethod_count);
    if (native_total_size != 0)       tty->print_cr(" N. total size  = %d", native_total_size);
    if (native_relocation_size != 0)  tty->print_cr(" N. relocation  = %d", native_relocation_size);
    if (native_insts_size != 0)       tty->print_cr(" N. main code   = %d", native_insts_size);
    if (native_oops_size != 0)        tty->print_cr(" N. oops        = %d", native_oops_size);
    if (native_metadata_size != 0)    tty->print_cr(" N. metadata    = %d", native_metadata_size);
  }
};

struct pc_nmethod_stats_struct {
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
  assert(pc != NULL, "Must be non null");
  assert(exception.not_null(), "Must be non null");
  assert(handler != NULL, "Must be non null");

  _count = 0;
  _exception_type = exception->klass();
  _next = NULL;
  _purge_list_next = NULL;

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
  int limit = count();
  for (int i = 0; i < limit; i++) {
    if (pc_at(i) == addr) {
      return handler_at(i);
    }
  }
  return NULL;
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
  Atomic::store(ec, &_next);
}

//-----------------------------------------------------------------------------


// Helper used by both find_pc_desc methods.
static inline bool match_desc(PcDesc* pc, int pc_offset, bool approximate) {
  NOT_PRODUCT(++pc_nmethod_stats.pc_desc_tests);
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
  NOT_PRODUCT(++pc_nmethod_stats.pc_desc_resets);
  // reset the cache by filling it with benign (non-null) values
  assert(initial_pc_desc->pc_offset() < 0, "must be sentinel");
  for (int i = 0; i < cache_size; i++)
    _pc_descs[i] = initial_pc_desc;
}

PcDesc* PcDescCache::find_pc_desc(int pc_offset, bool approximate) {
  NOT_PRODUCT(++pc_nmethod_stats.pc_desc_queries);
  NOT_PRODUCT(if (approximate) ++pc_nmethod_stats.pc_desc_approx);

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
  return NULL;
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

address* nmethod::orig_pc_addr(const frame* fr) {
  return (address*) ((address)fr->unextended_sp() + _orig_pc_offset);
}

const char* nmethod::compile_kind() const {
  if (is_osr_method())     return "osr";
  if (method() != NULL && is_native_method())  return "c2n";
  return NULL;
}

// Fill in default values for various flag fields
void nmethod::init_defaults() {
  _state                      = not_installed;
  _has_flushed_dependencies   = 0;
  _lock_count                 = 0;
  _stack_traversal_mark       = 0;
  _unload_reported            = false; // jvmti state
  _is_far_code                = false; // nmethods are located in CodeCache

#ifdef ASSERT
  _oops_are_stale             = false;
#endif

  _oops_do_mark_link       = NULL;
  _jmethod_id              = NULL;
  _osr_link                = NULL;
#if INCLUDE_RTM_OPT
  _rtm_state               = NoRTM;
#endif
#if INCLUDE_JVMCI
  _jvmci_installed_code   = NULL;
  _speculation_log        = NULL;
  _jvmci_installed_code_triggers_invalidation = false;
#endif
}

nmethod* nmethod::new_native_nmethod(const methodHandle& method,
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
    int native_nmethod_size = CodeBlob::allocation_size(code_buffer, sizeof(nmethod));
    CodeOffsets offsets;
    offsets.set_value(CodeOffsets::Verified_Entry, vep_offset);
    offsets.set_value(CodeOffsets::Frame_Complete, frame_complete);
    nm = new (native_nmethod_size, CompLevel_none) nmethod(method(), compiler_none, native_nmethod_size,
                                            compile_id, &offsets,
                                            code_buffer, frame_size,
                                            basic_lock_owner_sp_offset,
                                            basic_lock_sp_offset, oop_maps);
    NOT_PRODUCT(if (nm != NULL)  native_nmethod_stats.note_native_nmethod(nm));
  }

  if (nm != NULL) {
    // verify nmethod
    debug_only(nm->verify();) // might block

    nm->log_new_nmethod();
    nm->make_in_use();
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
  int comp_level
#if INCLUDE_JVMCI
  , jweak installed_code,
  jweak speculationLog
#endif
)
{
  assert(debug_info->oop_recorder() == code_buffer->oop_recorder(), "shared OR");
  code_buffer->finalize_oop_references(method);
  // create nmethod
  nmethod* nm = NULL;
  { MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    int nmethod_size =
      CodeBlob::allocation_size(code_buffer, sizeof(nmethod))
      + adjust_pcs_size(debug_info->pcs_size())
      + align_up((int)dependencies->size_in_bytes(), oopSize)
      + align_up(handler_table->size_in_bytes()    , oopSize)
      + align_up(nul_chk_table->size_in_bytes()    , oopSize)
      + align_up(debug_info->data_size()           , oopSize);

    nm = new (nmethod_size, comp_level)
    nmethod(method(), compiler->type(), nmethod_size, compile_id, entry_bci, offsets,
            orig_pc_offset, debug_info, dependencies, code_buffer, frame_size,
            oop_maps,
            handler_table,
            nul_chk_table,
            compiler,
            comp_level
#if INCLUDE_JVMCI
            , installed_code,
            speculationLog
#endif
            );

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
        if (deps.type() == Dependencies::call_site_target_value) {
          // CallSite dependencies are managed on per-CallSite instance basis.
          oop call_site = deps.argument_oop(0);
          MethodHandles::add_dependent_nmethod(call_site, nm);
        } else {
          Klass* klass = deps.context_type();
          if (klass == NULL) {
            continue;  // ignore things like evol_method
          }
          // record this nmethod as dependent on this klass
          InstanceKlass::cast(klass)->add_dependent_nmethod(nm);
        }
      }
      NOT_PRODUCT(if (nm != NULL)  note_java_nmethod(nm));
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
  CompilerType type,
  int nmethod_size,
  int compile_id,
  CodeOffsets* offsets,
  CodeBuffer* code_buffer,
  int frame_size,
  ByteSize basic_lock_owner_sp_offset,
  ByteSize basic_lock_sp_offset,
  OopMapSet* oop_maps )
  : CompiledMethod(method, "native nmethod", type, nmethod_size, sizeof(nmethod), code_buffer, offsets->value(CodeOffsets::Frame_Complete), frame_size, oop_maps, false),
  _is_unloading_state(0),
  _native_receiver_sp_offset(basic_lock_owner_sp_offset),
  _native_basic_lock_sp_offset(basic_lock_sp_offset)
{
  {
    int scopes_data_offset = 0;
    int deoptimize_offset       = 0;
    int deoptimize_mh_offset    = 0;

    debug_only(NoSafepointVerifier nsv;)
    assert_locked_or_safepoint(CodeCache_lock);

    init_defaults();
    _entry_bci               = InvocationEntryBci;
    // We have no exception handler or deopt handler make the
    // values something that will never match a pc like the nmethod vtable entry
    _exception_offset        = 0;
    _orig_pc_offset          = 0;

    _consts_offset           = data_offset();
    _stub_offset             = data_offset();
    _oops_offset             = data_offset();
    _metadata_offset         = _oops_offset         + align_up(code_buffer->total_oop_size(), oopSize);
    scopes_data_offset       = _metadata_offset     + align_up(code_buffer->total_metadata_size(), wordSize);
    _scopes_pcs_offset       = scopes_data_offset;
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
    _pc_desc_container.reset_to(NULL);
    _hotness_counter         = NMethodSweeper::hotness_counter_reset_val();

    _scopes_data_begin = (address) this + scopes_data_offset;
    _deopt_handler_begin = (address) this + deoptimize_offset;
    _deopt_mh_handler_begin = (address) this + deoptimize_mh_offset;

    code_buffer->copy_code_and_locs_to(this);
    code_buffer->copy_values_to(this);

    clear_unloading_state();

    Universe::heap()->register_nmethod(this);
    debug_only(Universe::heap()->verify_nmethod(this));

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

void* nmethod::operator new(size_t size, int nmethod_size, int comp_level) throw () {
  return CodeCache::allocate(nmethod_size, CodeCache::get_code_blob_type(comp_level));
}

nmethod::nmethod(
  Method* method,
  CompilerType type,
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
#if INCLUDE_JVMCI
  , jweak installed_code,
  jweak speculation_log
#endif
  )
  : CompiledMethod(method, "nmethod", type, nmethod_size, sizeof(nmethod), code_buffer, offsets->value(CodeOffsets::Frame_Complete), frame_size, oop_maps, false),
  _is_unloading_state(0),
  _native_receiver_sp_offset(in_ByteSize(-1)),
  _native_basic_lock_sp_offset(in_ByteSize(-1))
{
  assert(debug_info->oop_recorder() == code_buffer->oop_recorder(), "shared OR");
  {
    debug_only(NoSafepointVerifier nsv;)
    assert_locked_or_safepoint(CodeCache_lock);

    _deopt_handler_begin = (address) this;
    _deopt_mh_handler_begin = (address) this;

    init_defaults();
    _entry_bci               = entry_bci;
    _compile_id              = compile_id;
    _comp_level              = comp_level;
    _orig_pc_offset          = orig_pc_offset;
    _hotness_counter         = NMethodSweeper::hotness_counter_reset_val();

    // Section offsets
    _consts_offset           = content_offset()      + code_buffer->total_offset_of(code_buffer->consts());
    _stub_offset             = content_offset()      + code_buffer->total_offset_of(code_buffer->stubs());
    set_ctable_begin(header_begin() + _consts_offset);

#if INCLUDE_JVMCI
    _jvmci_installed_code = installed_code;
    _speculation_log = speculation_log;
    oop obj = JNIHandles::resolve(installed_code);
    if (obj == NULL || (obj->is_a(HotSpotNmethod::klass()) && HotSpotNmethod::isDefault(obj))) {
      _jvmci_installed_code_triggers_invalidation = false;
    } else {
      _jvmci_installed_code_triggers_invalidation = true;
    }

    if (compiler->is_jvmci()) {
      // JVMCI might not produce any stub sections
      if (offsets->value(CodeOffsets::Exceptions) != -1) {
        _exception_offset        = code_offset()          + offsets->value(CodeOffsets::Exceptions);
      } else {
        _exception_offset = -1;
      }
      if (offsets->value(CodeOffsets::Deopt) != -1) {
        _deopt_handler_begin       = (address) this + code_offset()          + offsets->value(CodeOffsets::Deopt);
      } else {
        _deopt_handler_begin = NULL;
      }
      if (offsets->value(CodeOffsets::DeoptMH) != -1) {
        _deopt_mh_handler_begin  = (address) this + code_offset()          + offsets->value(CodeOffsets::DeoptMH);
      } else {
        _deopt_mh_handler_begin = NULL;
      }
    } else {
#endif
    // Exception handler and deopt handler are in the stub section
    assert(offsets->value(CodeOffsets::Exceptions) != -1, "must be set");
    assert(offsets->value(CodeOffsets::Deopt     ) != -1, "must be set");

    _exception_offset       = _stub_offset          + offsets->value(CodeOffsets::Exceptions);
    _deopt_handler_begin    = (address) this + _stub_offset          + offsets->value(CodeOffsets::Deopt);
    if (offsets->value(CodeOffsets::DeoptMH) != -1) {
      _deopt_mh_handler_begin  = (address) this + _stub_offset          + offsets->value(CodeOffsets::DeoptMH);
    } else {
      _deopt_mh_handler_begin  = NULL;
#if INCLUDE_JVMCI
    }
#endif
    }
    if (offsets->value(CodeOffsets::UnwindHandler) != -1) {
      _unwind_handler_offset = code_offset()         + offsets->value(CodeOffsets::UnwindHandler);
    } else {
      _unwind_handler_offset = -1;
    }

    _oops_offset             = data_offset();
    _metadata_offset         = _oops_offset          + align_up(code_buffer->total_oop_size(), oopSize);
    int scopes_data_offset   = _metadata_offset      + align_up(code_buffer->total_metadata_size(), wordSize);

    _scopes_pcs_offset       = scopes_data_offset    + align_up(debug_info->data_size       (), oopSize);
    _dependencies_offset     = _scopes_pcs_offset    + adjust_pcs_size(debug_info->pcs_size());
    _handler_table_offset    = _dependencies_offset  + align_up((int)dependencies->size_in_bytes (), oopSize);
    _nul_chk_table_offset    = _handler_table_offset + align_up(handler_table->size_in_bytes(), oopSize);
    _nmethod_end_offset      = _nul_chk_table_offset + align_up(nul_chk_table->size_in_bytes(), oopSize);
    _entry_point             = code_begin()          + offsets->value(CodeOffsets::Entry);
    _verified_entry_point    = code_begin()          + offsets->value(CodeOffsets::Verified_Entry);
    _osr_entry_point         = code_begin()          + offsets->value(CodeOffsets::OSR_Entry);
    _exception_cache         = NULL;

    _scopes_data_begin = (address) this + scopes_data_offset;

    _pc_desc_container.reset_to(scopes_pcs_begin());

    code_buffer->copy_code_and_locs_to(this);
    // Copy contents of ScopeDescRecorder to nmethod
    code_buffer->copy_values_to(this);
    debug_info->copy_to(this);
    dependencies->copy_to(this);
    clear_unloading_state();

    Universe::heap()->register_nmethod(this);
    debug_only(Universe::heap()->verify_nmethod(this));

    CodeCache::commit(this);

    // Copy contents of ExceptionHandlerTable to nmethod
    handler_table->copy_to(this);
    nul_chk_table->copy_to(this);

    // we use the information of entry points to find out if a method is
    // static or non static
    assert(compiler->is_c2() || compiler->is_jvmci() ||
           _method->is_static() == (entry_point() == _verified_entry_point),
           " entry points must be same for static methods and vice versa");
  }
}

// Print a short set of xml attributes to identify this nmethod.  The
// output should be embedded in some other element.
void nmethod::log_identity(xmlStream* log) const {
  log->print(" compile_id='%d'", compile_id());
  const char* nm_kind = compile_kind();
  if (nm_kind != NULL)  log->print(" compile_kind='%s'", nm_kind);
  log->print(" compiler='%s'", compiler_name());
  if (TieredCompilation) {
    log->print(" level='%d'", comp_level());
  }
#if INCLUDE_JVMCI
    char buffer[O_BUFLEN];
    char* jvmci_name = jvmci_installed_code_name(buffer, O_BUFLEN);
    if (jvmci_name != NULL) {
      log->print(" jvmci_installed_code_name='");
      log->text("%s", jvmci_name);
      log->print("'");
    }
#endif
}


#define LOG_OFFSET(log, name)                    \
  if (p2i(name##_end()) - p2i(name##_begin())) \
    log->print(" " XSTR(name) "_offset='" INTX_FORMAT "'"    , \
               p2i(name##_begin()) - p2i(this))


void nmethod::log_new_nmethod() const {
  if (LogCompilation && xtty != NULL) {
    ttyLocker ttyl;
    HandleMark hm;
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
void nmethod::print_on(outputStream* st, const char* msg) const {
  if (st != NULL) {
    ttyLocker ttyl;
    if (WizardMode) {
      CompileTask::print(st, this, msg, /*short_form:*/ true);
      st->print_cr(" (" INTPTR_FORMAT ")", p2i(this));
    } else {
      CompileTask::print(st, this, msg, /*short_form:*/ false);
    }
  }
}

void nmethod::maybe_print_nmethod(DirectiveSet* directive) {
  bool printnmethods = directive->PrintAssemblyOption || directive->PrintNMethodsOption;
  if (printnmethods || PrintDebugInfo || PrintRelocations || PrintDependencies || PrintExceptionHandlers) {
    print_nmethod(printnmethods);
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
  if (printmethod || PrintDebugInfo || CompilerOracle::has_option_string(_method, "PrintDebugInfo")) {
    print_scopes();
  }
  if (printmethod || PrintRelocations || CompilerOracle::has_option_string(_method, "PrintRelocations")) {
    print_relocations();
  }
  if (printmethod || PrintDependencies || CompilerOracle::has_option_string(_method, "PrintDependencies")) {
    print_dependencies();
  }
  if (printmethod || PrintExceptionHandlers) {
    print_handler_table();
    print_nul_chk_table();
  }
  if (printmethod) {
    print_recorded_oops();
    print_recorded_metadata();
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


void nmethod::verify_clean_inline_caches() {
  assert(CompiledICLocker::is_safe(this), "mt unsafe call");

  ResourceMark rm;
  RelocIterator iter(this, oops_reloc_begin());
  while(iter.next()) {
    switch(iter.type()) {
      case relocInfo::virtual_call_type:
      case relocInfo::opt_virtual_call_type: {
        CompiledIC *ic = CompiledIC_at(&iter);
        // Ok, to lookup references to zombies here
        CodeBlob *cb = CodeCache::find_blob_unsafe(ic->ic_destination());
        assert(cb != NULL, "destination not in CodeBlob?");
        nmethod* nm = cb->as_nmethod_or_null();
        if( nm != NULL ) {
          // Verify that inline caches pointing to both zombie and not_entrant methods are clean
          if (!nm->is_in_use() || (nm->method()->code() != nm)) {
            assert(ic->is_clean(), "IC should be clean");
          }
        }
        break;
      }
      case relocInfo::static_call_type: {
        CompiledStaticCall *csc = compiledStaticCall_at(iter.reloc());
        CodeBlob *cb = CodeCache::find_blob_unsafe(csc->destination());
        assert(cb != NULL, "destination not in CodeBlob?");
        nmethod* nm = cb->as_nmethod_or_null();
        if( nm != NULL ) {
          // Verify that inline caches pointing to both zombie and not_entrant methods are clean
          if (!nm->is_in_use() || (nm->method()->code() != nm)) {
            assert(csc->is_clean(), "IC should be clean");
          }
        }
        break;
      }
      default:
        break;
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
bool nmethod::can_convert_to_zombie() {
  // Note that this is called when the sweeper has observed the nmethod to be
  // not_entrant. However, with concurrent code cache unloading, the state
  // might have moved on to unloaded if it is_unloading(), due to racing
  // concurrent GC threads.
  assert(is_not_entrant() || is_unloading(), "must be a non-entrant method");

  // Since the nmethod sweeper only does partial sweep the sweeper's traversal
  // count can be greater than the stack traversal count before it hits the
  // nmethod for the second time.
  // If an is_unloading() nmethod is still not_entrant, then it is not safe to
  // convert it to zombie due to GC unloading interactions. However, if it
  // has become unloaded, then it is okay to convert such nmethods to zombie.
  return stack_traversal_mark() + 1 < NMethodSweeper::traversal_count() &&
         !is_locked_by_vm() && (!is_unloading() || is_unloaded());
}

void nmethod::inc_decompile_count() {
  if (!is_compiled_by_c2() && !is_compiled_by_jvmci()) return;
  // Could be gated by ProfileTraps, but do not bother...
  Method* m = method();
  if (m == NULL)  return;
  MethodData* mdo = m->method_data();
  if (mdo == NULL)  return;
  // There is a benign race here.  See comments in methodData.hpp.
  mdo->inc_decompile_count();
}

void nmethod::make_unloaded() {
  post_compiled_method_unload();

  // This nmethod is being unloaded, make sure that dependencies
  // recorded in instanceKlasses get flushed.
  // Since this work is being done during a GC, defer deleting dependencies from the
  // InstanceKlass.
  assert(Universe::heap()->is_gc_active() || Thread::current()->is_ConcurrentGC_thread(),
         "should only be called during gc");
  flush_dependencies(/*delete_immediately*/false);

  // Break cycle between nmethod & method
  LogTarget(Trace, class, unload, nmethod) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print("making nmethod " INTPTR_FORMAT
             " unloadable, Method*(" INTPTR_FORMAT
             ") ",
             p2i(this), p2i(_method));
     ls.cr();
  }
  // Unlink the osr method, so we do not look this up again
  if (is_osr_method()) {
    // Invalidate the osr nmethod only once
    if (is_in_use()) {
      invalidate_osr_method();
    }
#ifdef ASSERT
    if (method() != NULL) {
      // Make sure osr nmethod is invalidated, i.e. not on the list
      bool found = method()->method_holder()->remove_osr_nmethod(this);
      assert(!found, "osr nmethod should have been invalidated");
    }
#endif
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
  }

  // Make the class unloaded - i.e., change state and notify sweeper
  assert(SafepointSynchronize::is_at_safepoint() || Thread::current()->is_ConcurrentGC_thread(),
         "must be at safepoint");

  {
    // Clear ICStubs and release any CompiledICHolders.
    CompiledICLocker ml(this);
    clear_ic_callsites();
  }

  // Unregister must be done before the state change
  {
    MutexLockerEx ml(SafepointSynchronize::is_at_safepoint() ? NULL : CodeCache_lock,
                     Mutex::_no_safepoint_check_flag);
    Universe::heap()->unregister_nmethod(this);
  }

  // Clear the method of this dead nmethod
  set_method(NULL);

  // Log the unloading.
  log_state_change();

#if INCLUDE_JVMCI
  // The method can only be unloaded after the pointer to the installed code
  // Java wrapper is no longer alive. Here we need to clear out this weak
  // reference to the dead object.
  maybe_invalidate_installed_code();
#endif

  // The Method* is gone at this point
  assert(_method == NULL, "Tautology");

  set_osr_link(NULL);
  NMethodSweeper::report_state_change(this);

  // The release is only needed for compile-time ordering, as accesses
  // into the nmethod after the store are not safe due to the sweeper
  // being allowed to free it when the store is observed, during
  // concurrent nmethod unloading. Therefore, there is no need for
  // acquire on the loader side.
  OrderAccess::release_store(&_state, (signed char)unloaded);
}

void nmethod::invalidate_osr_method() {
  assert(_entry_bci != InvocationEntryBci, "wrong kind of nmethod");
  // Remove from list of active nmethods
  if (method() != NULL) {
    method()->method_holder()->remove_osr_nmethod(this);
  }
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

  const char *state_msg = _state == zombie ? "made zombie" : "made not entrant";
  CompileTask::print_ul(this, state_msg);
  if (PrintCompilation && _state != unloaded) {
    print_on(tty, state_msg);
  }
}

void nmethod::unlink_from_method(bool acquire_lock) {
  // We need to check if both the _code and _from_compiled_code_entry_point
  // refer to this nmethod because there is a race in setting these two fields
  // in Method* as seen in bugid 4947125.
  // If the vep() points to the zombie nmethod, the memory for the nmethod
  // could be flushed and the compiler and vtable stubs could still call
  // through it.
  if (method() != NULL && (method()->code() == this ||
                           method()->from_compiled_entry() == verified_entry_point())) {
    method()->clear_code(acquire_lock);
  }
}

/**
 * Common functionality for both make_not_entrant and make_zombie
 */
bool nmethod::make_not_entrant_or_zombie(int state) {
  assert(state == zombie || state == not_entrant, "must be zombie or not_entrant");
  assert(!is_zombie(), "should not already be a zombie");

  if (_state == state) {
    // Avoid taking the lock if already in required state.
    // This is safe from races because the state is an end-state,
    // which the nmethod cannot back out of once entered.
    // No need for fencing either.
    return false;
  }

  // Make sure neither the nmethod nor the method is flushed in case of a safepoint in code below.
  nmethodLocker nml(this);
  methodHandle the_method(method());
  // This can be called while the system is already at a safepoint which is ok
  NoSafepointVerifier nsv(true, !SafepointSynchronize::is_at_safepoint());

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
    // verified entry point of regular methods. We check that the
    // nmethod is in use to ensure that it is invalidated only once.
    if (is_osr_method() && is_in_use()) {
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

    if (is_in_use() && update_recompile_counts()) {
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
      OrderAccess::storestore(); // _stack_traversal_mark and _state
    }

    // Change state
    _state = state;

    // Log the transition once
    log_state_change();

    // Invalidate while holding the patching lock
    JVMCI_ONLY(maybe_invalidate_installed_code());

    // Remove nmethod from method.
    unlink_from_method(false /* already owns Patching_lock */);
  } // leave critical region under Patching_lock

#ifdef ASSERT
  if (is_osr_method() && method() != NULL) {
    // Make sure osr nmethod is invalidated, i.e. not on the list
    bool found = method()->method_holder()->remove_osr_nmethod(this);
    assert(!found, "osr nmethod should have been invalidated");
  }
#endif

  // When the nmethod becomes zombie it is no longer alive so the
  // dependencies must be flushed.  nmethods in the not_entrant
  // state will be flushed later when the transition to zombie
  // happens or they get unloaded.
  if (state == zombie) {
    {
      // Flushing dependencies must be done before any possible
      // safepoint can sneak in, otherwise the oops used by the
      // dependency logic could have become stale.
      MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      if (nmethod_needs_unregister) {
        Universe::heap()->unregister_nmethod(this);
      }
      flush_dependencies(/*delete_immediately*/true);
    }

    // Clear ICStubs to prevent back patching stubs of zombie or flushed
    // nmethods during the next safepoint (see ICStub::finalize), as well
    // as to free up CompiledICHolder resources.
    {
      CompiledICLocker ml(this);
      clear_ic_callsites();
    }

    // zombie only - if a JVMTI agent has enabled the CompiledMethodUnload
    // event and it hasn't already been reported for this nmethod then
    // report it now. The event may have been reported earlier if the GC
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
    ResourceMark m;
    tty->print_cr("nmethod <" INTPTR_FORMAT "> %s code made %s", p2i(this), this->method() ? this->method()->name_and_sig_as_C_string() : "null", (state == not_entrant) ? "not entrant" : "zombie");
  }

  NMethodSweeper::report_state_change(this);
  return true;
}

void nmethod::flush() {
  MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  // Note that there are no valid oops in the nmethod anymore.
  assert(!is_osr_method() || is_unloaded() || is_zombie(),
         "osr nmethod must be unloaded or zombie before flushing");
  assert(is_zombie() || is_osr_method(), "must be a zombie method");
  assert (!is_locked_by_vm(), "locked methods shouldn't be flushed");
  assert_locked_or_safepoint(CodeCache_lock);

  // completely deallocate this method
  Events::log(JavaThread::current(), "flushing nmethod " INTPTR_FORMAT, p2i(this));
  if (PrintMethodFlushing) {
    tty->print_cr("*flushing %s nmethod %3d/" INTPTR_FORMAT ". Live blobs:" UINT32_FORMAT
                  "/Free CodeCache:" SIZE_FORMAT "Kb",
                  is_osr_method() ? "osr" : "",_compile_id, p2i(this), CodeCache::blob_count(),
                  CodeCache::unallocated_capacity(CodeCache::get_code_blob_type(this))/1024);
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

#if INCLUDE_JVMCI
  assert(_jvmci_installed_code == NULL, "should have been nulled out when transitioned to zombie");
  assert(_speculation_log == NULL, "should have been nulled out when transitioned to zombie");
#endif

  Universe::heap()->flush_nmethod(this);

  CodeBlob::flush();
  CodeCache::free(this);
}

oop nmethod::oop_at(int index) const {
  if (index == 0) {
    return NULL;
  }
  return NativeAccess<AS_NO_KEEPALIVE>::oop_load(oop_addr_at(index));
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
// function is called with a boolean argument and this function only
// notifies instanceKlasses that are reachable

void nmethod::flush_dependencies(bool delete_immediately) {
  DEBUG_ONLY(bool called_by_gc = Universe::heap()->is_gc_active() || Thread::current()->is_ConcurrentGC_thread();)
  assert(called_by_gc != delete_immediately,
  "delete_immediately is false if and only if we are called during GC");
  if (!has_flushed_dependencies()) {
    set_has_flushed_dependencies();
    for (Dependencies::DepStream deps(this); deps.next(); ) {
      if (deps.type() == Dependencies::call_site_target_value) {
        // CallSite dependencies are managed on per-CallSite instance basis.
        oop call_site = deps.argument_oop(0);
        if (delete_immediately) {
          assert_locked_or_safepoint(CodeCache_lock);
          MethodHandles::remove_dependent_nmethod(call_site, this);
        } else {
          MethodHandles::clean_dependency_context(call_site);
        }
      } else {
        Klass* klass = deps.context_type();
        if (klass == NULL) {
          continue;  // ignore things like evol_method
        }
        // During GC delete_immediately is false, and liveness
        // of dependee determines class that needs to be updated.
        if (delete_immediately) {
          assert_locked_or_safepoint(CodeCache_lock);
          InstanceKlass::cast(klass)->remove_dependent_nmethod(this);
        } else if (klass->is_loader_alive()) {
          // The GC may clean dependency contexts concurrently and in parallel.
          InstanceKlass::cast(klass)->clean_dependency_context();
        }
      }
    }
  }
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
    MutexLockerEx ml(Service_lock, Mutex::_no_safepoint_check_flag);
    JvmtiDeferredEventQueue::enqueue(event);
  }

  // The JVMTI CompiledMethodUnload event can be enabled or disabled at
  // any time. As the nmethod is being unloaded now we mark it has
  // having the unload event reported - this will ensure that we don't
  // attempt to report the event in the unlikely scenario where the
  // event is enabled at the time the nmethod is made a zombie.
  set_unload_reported();
}

// Iterate over metadata calling this function.   Used by RedefineClasses
void nmethod::metadata_do(MetadataClosure* f) {
  {
    // Visit all immediate references that are embedded in the instruction stream.
    RelocIterator iter(this, oops_reloc_begin());
    while (iter.next()) {
      if (iter.type() == relocInfo::metadata_type ) {
        metadata_Relocation* r = iter.metadata_reloc();
        // In this metadata, we must only follow those metadatas directly embedded in
        // the code.  Other metadatas (oop_index>0) are seen as part of
        // the metadata section below.
        assert(1 == (r->metadata_is_immediate()) +
               (r->metadata_addr() >= metadata_begin() && r->metadata_addr() < metadata_end()),
               "metadata must be found in exactly one place");
        if (r->metadata_is_immediate() && r->metadata_value() != NULL) {
          Metadata* md = r->metadata_value();
          if (md != _method) f->do_metadata(md);
        }
      } else if (iter.type() == relocInfo::virtual_call_type) {
        // Check compiledIC holders associated with this nmethod
        ResourceMark rm;
        CompiledIC *ic = CompiledIC_at(&iter);
        if (ic->is_icholder_call()) {
          CompiledICHolder* cichk = ic->cached_icholder();
          f->do_metadata(cichk->holder_metadata());
          f->do_metadata(cichk->holder_klass());
        } else {
          Metadata* ic_oop = ic->cached_metadata();
          if (ic_oop != NULL) {
            f->do_metadata(ic_oop);
          }
        }
      }
    }
  }

  // Visit the metadata section
  for (Metadata** p = metadata_begin(); p < metadata_end(); p++) {
    if (*p == Universe::non_oop_word() || *p == NULL)  continue;  // skip non-oops
    Metadata* md = *p;
    f->do_metadata(md);
  }

  // Visit metadata not embedded in the other places.
  if (_method != NULL) f->do_metadata(_method);
}

// The _is_unloading_state encodes a tuple comprising the unloading cycle
// and the result of IsUnloadingBehaviour::is_unloading() fpr that cycle.
// This is the bit layout of the _is_unloading_state byte: 00000CCU
// CC refers to the cycle, which has 2 bits, and U refers to the result of
// IsUnloadingBehaviour::is_unloading() for that unloading cycle.

class IsUnloadingState: public AllStatic {
  static const uint8_t _is_unloading_mask = 1;
  static const uint8_t _is_unloading_shift = 0;
  static const uint8_t _unloading_cycle_mask = 6;
  static const uint8_t _unloading_cycle_shift = 1;

  static uint8_t set_is_unloading(uint8_t state, bool value) {
    state &= ~_is_unloading_mask;
    if (value) {
      state |= 1 << _is_unloading_shift;
    }
    assert(is_unloading(state) == value, "unexpected unloading cycle overflow");
    return state;
  }

  static uint8_t set_unloading_cycle(uint8_t state, uint8_t value) {
    state &= ~_unloading_cycle_mask;
    state |= value << _unloading_cycle_shift;
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
  uint8_t state = RawAccess<MO_RELAXED>::load(&_is_unloading_state);
  bool state_is_unloading = IsUnloadingState::is_unloading(state);
  uint8_t state_unloading_cycle = IsUnloadingState::unloading_cycle(state);
  if (state_is_unloading) {
    return true;
  }
  uint8_t current_cycle = CodeCache::unloading_cycle();
  if (state_unloading_cycle == current_cycle) {
    return false;
  }

  // The IsUnloadingBehaviour is responsible for checking if there are any dead
  // oops in the CompiledMethod, by calling oops_do on it.
  state_unloading_cycle = current_cycle;

  if (is_zombie()) {
    // Zombies without calculated unloading epoch are never unloading due to GC.

    // There are no races where a previously observed is_unloading() nmethod
    // suddenly becomes not is_unloading() due to here being observed as zombie.

    // With STW unloading, all is_alive() && is_unloading() nmethods are unlinked
    // and unloaded in the safepoint. That makes races where an nmethod is first
    // observed as is_alive() && is_unloading() and subsequently observed as
    // is_zombie() impossible.

    // With concurrent unloading, all references to is_unloading() nmethods are
    // first unlinked (e.g. IC caches and dependency contexts). Then a global
    // handshake operation is performed with all JavaThreads before finally
    // unloading the nmethods. The sweeper never converts is_alive() && is_unloading()
    // nmethods to zombies; it waits for them to become is_unloaded(). So before
    // the global handshake, it is impossible for is_unloading() nmethods to
    // racingly become is_zombie(). And is_unloading() is calculated for all is_alive()
    // nmethods before taking that global handshake, meaning that it will never
    // be recalculated after the handshake.

    // After that global handshake, is_unloading() nmethods are only observable
    // to the iterators, and they will never trigger recomputation of the cached
    // is_unloading_state, and hence may not suffer from such races.

    state_is_unloading = false;
  } else {
    state_is_unloading = IsUnloadingBehaviour::current()->is_unloading(this);
  }

  state = IsUnloadingState::create(state_is_unloading, state_unloading_cycle);

  RawAccess<MO_RELAXED>::store(&_is_unloading_state, state);

  return state_is_unloading;
}

void nmethod::clear_unloading_state() {
  uint8_t state = IsUnloadingState::create(false, CodeCache::unloading_cycle());
  RawAccess<MO_RELAXED>::store(&_is_unloading_state, state);
}


// This is called at the end of the strong tracing/marking phase of a
// GC to unload an nmethod if it contains otherwise unreachable
// oops.

void nmethod::do_unloading(bool unloading_occurred) {
  // Make sure the oop's ready to receive visitors
  assert(!is_zombie() && !is_unloaded(),
         "should not call follow on zombie or unloaded nmethod");

  if (is_unloading()) {
    make_unloaded();
  } else {
#if INCLUDE_JVMCI
    if (_jvmci_installed_code != NULL) {
      if (JNIHandles::is_global_weak_cleared(_jvmci_installed_code)) {
        if (_jvmci_installed_code_triggers_invalidation) {
          make_not_entrant();
        }
        clear_jvmci_installed_code();
      }
    }
#endif

    guarantee(unload_nmethod_caches(unloading_occurred),
              "Should not need transition stubs");
  }
}

void nmethod::oops_do(OopClosure* f, bool allow_zombie) {
  // make sure the oops ready to receive visitors
  assert(allow_zombie || !is_zombie(), "should not call follow on zombie nmethod");
  assert(!is_unloaded(), "should not call follow on unloaded nmethod");

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
        if (r->oop_is_immediate() && r->oop_value() != NULL) {
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

#define NMETHOD_SENTINEL ((nmethod*)badAddress)

nmethod* volatile nmethod::_oops_do_mark_nmethods;

// An nmethod is "marked" if its _mark_link is set non-null.
// Even if it is the end of the linked list, it will have a non-null link value,
// as long as it is on the list.
// This code must be MP safe, because it is used from parallel GC passes.
bool nmethod::test_set_oops_do_mark() {
  assert(nmethod::oops_do_marking_is_active(), "oops_do_marking_prologue must be called");
  if (_oops_do_mark_link == NULL) {
    // Claim this nmethod for this thread to mark.
    if (Atomic::replace_if_null(NMETHOD_SENTINEL, &_oops_do_mark_link)) {
      // Atomically append this nmethod (now claimed) to the head of the list:
      nmethod* observed_mark_nmethods = _oops_do_mark_nmethods;
      for (;;) {
        nmethod* required_mark_nmethods = observed_mark_nmethods;
        _oops_do_mark_link = required_mark_nmethods;
        observed_mark_nmethods =
          Atomic::cmpxchg(this, &_oops_do_mark_nmethods, required_mark_nmethods);
        if (observed_mark_nmethods == required_mark_nmethods)
          break;
      }
      // Mark was clear when we first saw this guy.
      LogTarget(Trace, gc, nmethod) lt;
      if (lt.is_enabled()) {
        LogStream ls(lt);
        CompileTask::print(&ls, this, "oops_do, mark", /*short_form:*/ true);
      }
      return false;
    }
  }
  // On fall through, another racing thread marked this nmethod before we did.
  return true;
}

void nmethod::oops_do_marking_prologue() {
  log_trace(gc, nmethod)("oops_do_marking_prologue");
  assert(_oops_do_mark_nmethods == NULL, "must not call oops_do_marking_prologue twice in a row");
  // We use cmpxchg instead of regular assignment here because the user
  // may fork a bunch of threads, and we need them all to see the same state.
  nmethod* observed = Atomic::cmpxchg(NMETHOD_SENTINEL, &_oops_do_mark_nmethods, (nmethod*)NULL);
  guarantee(observed == NULL, "no races in this sequential code");
}

void nmethod::oops_do_marking_epilogue() {
  assert(_oops_do_mark_nmethods != NULL, "must not call oops_do_marking_epilogue twice in a row");
  nmethod* cur = _oops_do_mark_nmethods;
  while (cur != NMETHOD_SENTINEL) {
    assert(cur != NULL, "not NULL-terminated");
    nmethod* next = cur->_oops_do_mark_link;
    cur->_oops_do_mark_link = NULL;
    DEBUG_ONLY(cur->verify_oop_relocations());

    LogTarget(Trace, gc, nmethod) lt;
    if (lt.is_enabled()) {
      LogStream ls(lt);
      CompileTask::print(&ls, cur, "oops_do, unmark", /*short_form:*/ true);
    }
    cur = next;
  }
  nmethod* required = _oops_do_mark_nmethods;
  nmethod* observed = Atomic::cmpxchg((nmethod*)NULL, &_oops_do_mark_nmethods, required);
  guarantee(observed == required, "no races in this sequential code");
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
  assert(has_method_handle_invokes() == (_deopt_mh_handler_begin != NULL), "must have deopt mh handler");

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
static PcDesc* linear_search(const PcDescSearch& search, int pc_offset, bool approximate) {
  PcDesc* lower = search.scopes_pcs_begin();
  PcDesc* upper = search.scopes_pcs_end();
  lower += 1; // exclude initial sentinel
  PcDesc* res = NULL;
  for (PcDesc* p = lower; p < upper; p++) {
    NOT_PRODUCT(--pc_nmethod_stats.pc_desc_tests);  // don't count this call to match_desc
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
PcDesc* PcDescContainer::find_pc_desc_internal(address pc, bool approximate, const PcDescSearch& search) {
  address base_address = search.code_begin();
  if ((pc < base_address) ||
      (pc - base_address) >= (ptrdiff_t) PcDesc::upper_offset_limit) {
    return NULL;  // PC is wildly out of range
  }
  int pc_offset = (int) (pc - base_address);

  // Check the PcDesc cache if it contains the desired PcDesc
  // (This as an almost 100% hit rate.)
  PcDesc* res = _pc_desc_cache.find_pc_desc(pc_offset, approximate);
  if (res != NULL) {
    assert(res == linear_search(search, pc_offset, approximate), "cache ok");
    return res;
  }

  // Fallback algorithm: quasi-linear search for the PcDesc
  // Find the last pc_offset less than the given offset.
  // The successor must be the required match, if there is a match at all.
  // (Use a fixed radix to avoid expensive affine pointer arithmetic.)
  PcDesc* lower = search.scopes_pcs_begin();
  PcDesc* upper = search.scopes_pcs_end();
  upper -= 1; // exclude final sentinel
  if (lower >= upper)  return NULL;  // native method; no PcDescs at all

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
  const int LOG2_RADIX = 4 /*smaller steps in debug mode:*/ debug_only(-1);
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
    assert(upper == linear_search(search, pc_offset, approximate), "search ok");
    _pc_desc_cache.add_pc_desc(upper);
    return upper;
  } else {
    assert(NULL == linear_search(search, pc_offset, approximate), "search ok");
    return NULL;
  }
}


void nmethod::check_all_dependencies(DepChange& changes) {
  // Checked dependencies are allocated into this ResourceMark
  ResourceMark rm;

  // Turn off dependency tracing while actually testing dependencies.
  NOT_PRODUCT( FlagSetting fs(TraceDependencies, false) );

  typedef ResourceHashtable<DependencySignature, int, &DependencySignature::hash,
                            &DependencySignature::equals, 11027> DepTable;

  DepTable* table = new DepTable();

  // Iterate over live nmethods and check dependencies of all nmethods that are not
  // marked for deoptimization. A particular dependency is only checked once.
  NMethodIterator iter(NMethodIterator::only_alive_and_not_unloading);
  while(iter.next()) {
    nmethod* nm = iter.method();
    // Only notify for live nmethods
    if (!nm->is_marked_for_deoptimization()) {
      for (Dependencies::DepStream deps(nm); deps.next(); ) {
        // Construct abstraction of a dependency.
        DependencySignature* current_sig = new DependencySignature(deps);

        // Determine if dependency is already checked. table->put(...) returns
        // 'true' if the dependency is added (i.e., was not in the hashtable).
        if (table->put(*current_sig, 1)) {
          if (deps.check_dependency() != NULL) {
            // Dependency checking failed. Print out information about the failed
            // dependency and finally fail with an assert. We can fail here, since
            // dependency checking is never done in a product build.
            tty->print_cr("Failed dependency:");
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

bool nmethod::is_evol_dependent() {
  for (Dependencies::DepStream deps(this); deps.next(); ) {
    if (deps.type() == Dependencies::evol_method) {
      Method* method = deps.method_argument(0);
      if (method->is_old()) {
        if (log_is_enabled(Debug, redefine, class, nmethod)) {
          ResourceMark rm;
          log_debug(redefine, class, nmethod)
            ("Found evol dependency of nmethod %s.%s(%s) compile_id=%d on method %s.%s(%s)",
             _method->method_holder()->external_name(),
             _method->name()->as_C_string(),
             _method->signature()->as_C_string(),
             compile_id(),
             method->method_holder()->external_name(),
             method->name()->as_C_string(),
             method->signature()->as_C_string());
        }
        if (TraceDependencies || LogCompilation)
          deps.log_dependency(method->method_holder());
        return true;
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
    Thread* thread = Thread::current();
    ResetNoHandleMark rnm; // Might be called from LEAF/QUICK ENTRY
    HandleMark hm(thread);
    ResourceMark rm(thread);
    CodeBlob* cb = CodeCache::find_blob(pc);
    assert(cb != NULL && cb == this, "");
    ttyLocker ttyl;
    tty->print_cr("implicit exception happened at " INTPTR_FORMAT, p2i(pc));
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
  guarantee(cb != NULL && cb->is_compiled(), "bad pc for a nmethod found");
  _nm = cb->as_compiled_method();
  lock_nmethod(_nm);
}

// Only JvmtiDeferredEvent::compiled_method_unload_event()
// should pass zombie_ok == true.
void nmethodLocker::lock_nmethod(CompiledMethod* cm, bool zombie_ok) {
  if (cm == NULL)  return;
  if (cm->is_aot()) return;  // FIXME: Revisit once _lock_count is added to aot_method
  nmethod* nm = cm->as_nmethod();
  Atomic::inc(&nm->_lock_count);
  assert(zombie_ok || !nm->is_zombie(), "cannot lock a zombie method");
}

void nmethodLocker::unlock_nmethod(CompiledMethod* cm) {
  if (cm == NULL)  return;
  if (cm->is_aot()) return;  // FIXME: Revisit once _lock_count is added to aot_method
  nmethod* nm = cm->as_nmethod();
  Atomic::dec(&nm->_lock_count);
  assert(nm->_lock_count >= 0, "unmatched nmethod lock/unlock");
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
    if (_ok) {
      _nm->print_nmethod(true);
      _ok = false;
    }
    tty->print_cr("*** non-oop " PTR_FORMAT " found at " PTR_FORMAT " (offset %d)",
                  p2i(*p), p2i(p), (int)((intptr_t)p - (intptr_t)_nm));
  }
  virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }
};

void nmethod::verify() {

  // Hmm. OSR methods can be deopted but not marked as zombie or not_entrant
  // seems odd.

  if (is_zombie() || is_not_entrant() || is_unloaded())
    return;

  // Make sure all the entry points are correctly aligned for patching.
  NativeJump::check_verified_entry_alignment(entry_point(), verified_entry_point());

  // assert(oopDesc::is_oop(method()), "must be valid");

  ResourceMark rm;

  if (!CodeCache::contains(this)) {
    fatal("nmethod at " INTPTR_FORMAT " not in zone", p2i(this));
  }

  if(is_native_method() )
    return;

  nmethod* nm = CodeCache::find_nmethod(verified_entry_point());
  if (nm != this) {
    fatal("findNMethod did not find this nmethod (" INTPTR_FORMAT ")", p2i(this));
  }

  for (PcDesc* p = scopes_pcs_begin(); p < scopes_pcs_end(); p++) {
    if (! p->verify(this)) {
      tty->print_cr("\t\tin nmethod at " INTPTR_FORMAT " (pcs)", p2i(this));
    }
  }

  VerifyOopsClosure voc(this);
  oops_do(&voc);
  assert(voc.ok(), "embedded oops must be OK");
  Universe::heap()->verify_nmethod(this);

  verify_scopes();
}


void nmethod::verify_interrupt_point(address call_site) {
  // Verify IC only when nmethod installation is finished.
  if (!is_not_installed()) {
    if (CompiledICLocker::is_safe(this)) {
      CompiledIC_at(this, call_site);
      CHECK_UNHANDLED_OOPS_ONLY(Thread::current()->clear_unhandled_oops());
    } else {
      CompiledICLocker ml_verify(this);
      CompiledIC_at(this, call_site);
    }
  }

  PcDesc* pd = pc_desc_at(nativeCall_at(call_site)->return_address());
  assert(pd != NULL, "PcDesc must exist");
  for (ScopeDesc* sd = new ScopeDesc(this, pd->scope_decode_offset(),
                                     pd->obj_decode_offset(), pd->should_reexecute(), pd->rethrow_exception(),
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
        stub = iter.opt_virtual_call_reloc()->static_stub(false);
        verify_interrupt_point(iter.addr());
        break;
      case relocInfo::static_call_type:
        stub = iter.static_call_reloc()->static_stub(false);
        //verify_interrupt_point(iter.addr());
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
    assert(stub == NULL || stub_contains(stub), "static call stub outside stub section");
  }
}


// -----------------------------------------------------------------------------
// Printing operations

void nmethod::print() const {
  ResourceMark rm;
  ttyLocker ttyl;   // keep the following output all in one block

  tty->print("Compiled method ");

  if (is_compiled_by_c1()) {
    tty->print("(c1) ");
  } else if (is_compiled_by_c2()) {
    tty->print("(c2) ");
  } else if (is_compiled_by_jvmci()) {
    tty->print("(JVMCI) ");
  } else {
    tty->print("(nm) ");
  }

  print_on(tty, NULL);

  if (WizardMode) {
    tty->print("((nmethod*) " INTPTR_FORMAT ") ", p2i(this));
    tty->print(" for method " INTPTR_FORMAT , p2i(method()));
    tty->print(" { ");
    tty->print_cr("%s ", state());
    tty->print_cr("}:");
  }
  if (size              () > 0) tty->print_cr(" total in heap  [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              p2i(this),
                                              p2i(this) + size(),
                                              size());
  if (relocation_size   () > 0) tty->print_cr(" relocation     [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              p2i(relocation_begin()),
                                              p2i(relocation_end()),
                                              relocation_size());
  if (consts_size       () > 0) tty->print_cr(" constants      [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              p2i(consts_begin()),
                                              p2i(consts_end()),
                                              consts_size());
  if (insts_size        () > 0) tty->print_cr(" main code      [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              p2i(insts_begin()),
                                              p2i(insts_end()),
                                              insts_size());
  if (stub_size         () > 0) tty->print_cr(" stub code      [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              p2i(stub_begin()),
                                              p2i(stub_end()),
                                              stub_size());
  if (oops_size         () > 0) tty->print_cr(" oops           [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              p2i(oops_begin()),
                                              p2i(oops_end()),
                                              oops_size());
  if (metadata_size      () > 0) tty->print_cr(" metadata       [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              p2i(metadata_begin()),
                                              p2i(metadata_end()),
                                              metadata_size());
  if (scopes_data_size  () > 0) tty->print_cr(" scopes data    [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              p2i(scopes_data_begin()),
                                              p2i(scopes_data_end()),
                                              scopes_data_size());
  if (scopes_pcs_size   () > 0) tty->print_cr(" scopes pcs     [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              p2i(scopes_pcs_begin()),
                                              p2i(scopes_pcs_end()),
                                              scopes_pcs_size());
  if (dependencies_size () > 0) tty->print_cr(" dependencies   [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              p2i(dependencies_begin()),
                                              p2i(dependencies_end()),
                                              dependencies_size());
  if (handler_table_size() > 0) tty->print_cr(" handler table  [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              p2i(handler_table_begin()),
                                              p2i(handler_table_end()),
                                              handler_table_size());
  if (nul_chk_table_size() > 0) tty->print_cr(" nul chk table  [" INTPTR_FORMAT "," INTPTR_FORMAT "] = %d",
                                              p2i(nul_chk_table_begin()),
                                              p2i(nul_chk_table_end()),
                                              nul_chk_table_size());
}

#ifndef PRODUCT

void nmethod::print_scopes() {
  // Find the first pc desc for all scopes in the code and print it.
  ResourceMark rm;
  for (PcDesc* p = scopes_pcs_begin(); p < scopes_pcs_end(); p++) {
    if (p->scope_decode_offset() == DebugInformationRecorder::serialized_null)
      continue;

    ScopeDesc* sd = scope_desc_at(p->real_pc(this));
    while (sd != NULL) {
      sd->print_on(tty, p);
      sd = sd->sender();
    }
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
      if (ctxk->is_instance_klass() && InstanceKlass::cast(ctxk)->is_dependent_nmethod(this)) {
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
}


void nmethod::print_pcs() {
  ResourceMark m;       // in case methods get printed via debugger
  tty->print_cr("pc-bytecode offsets:");
  for (PcDesc* p = scopes_pcs_begin(); p < scopes_pcs_end(); p++) {
    p->print(this);
  }
}

void nmethod::print_recorded_oops() {
  tty->print_cr("Recorded oops:");
  for (int i = 0; i < oops_count(); i++) {
    oop o = oop_at(i);
    tty->print("#%3d: " INTPTR_FORMAT " ", i, p2i(o));
    if (o == Universe::non_oop_word()) {
      tty->print("non-oop word");
    } else {
      if (o != NULL) {
        o->print_value();
      } else {
        tty->print_cr("NULL");
      }
    }
    tty->cr();
  }
}

void nmethod::print_recorded_metadata() {
  tty->print_cr("Recorded metadata:");
  for (int i = 0; i < metadata_count(); i++) {
    Metadata* m = metadata_at(i);
    tty->print("#%3d: " INTPTR_FORMAT " ", i, p2i(m));
    if (m == (Metadata*)Universe::non_oop_word()) {
      tty->print("non-metadata word");
    } else {
      Metadata::print_value_on_maybe_null(tty, m);
    }
    tty->cr();
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
        case relocInfo::runtime_call_type:
        case relocInfo::runtime_call_w_cp_type: {
          stringStream st;
          st.print("runtime_call");
          CallRelocation* r = (CallRelocation*)iter.reloc();
          address dest = r->destination();
          CodeBlob* cb = CodeCache::find_blob(dest);
          if (cb != NULL) {
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
          if (m != NULL) {
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
          if (m != NULL) {
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
          if (m != NULL) {
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
        case relocInfo::type_mask:             return "type_bit_mask";

        default:
          break;
    }
  }
  return have_one ? "other" : NULL;
}

// Return a the last scope in (begin..end]
ScopeDesc* nmethod::scope_desc_in(address begin, address end) {
  PcDesc* p = pc_desc_near(begin+1);
  if (p != NULL && p->real_pc(this) <= end) {
    return new ScopeDesc(this, p->scope_decode_offset(),
                         p->obj_decode_offset(), p->should_reexecute(), p->rethrow_exception(),
                         p->return_oop());
  }
  return NULL;
}

void nmethod::print_nmethod_labels(outputStream* stream, address block_begin) const {
  if (block_begin == entry_point())             stream->print_cr("[Entry Point]");
  if (block_begin == verified_entry_point())    stream->print_cr("[Verified Entry Point]");
  if (JVMCI_ONLY(_exception_offset >= 0 &&) block_begin == exception_begin())         stream->print_cr("[Exception Handler]");
  if (block_begin == stub_begin())              stream->print_cr("[Stub Code]");
  if (JVMCI_ONLY(_deopt_handler_begin != NULL &&) block_begin == deopt_handler_begin())     stream->print_cr("[Deopt Handler Code]");

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
  ImmutableOopMapSet* oms = oop_maps();
  if (oms != NULL) {
    for (int i = 0, imax = oms->count(); i < imax; i++) {
      const ImmutableOopMapPair* pair = oms->pair_at(i);
      const ImmutableOopMap* om = pair->get_from(oms);
      address pc = base + pair->pc_offset();
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
        default:
          break;
        }
      }
      st->print(" {reexecute=%d rethrow=%d return_oop=%d}", sd->should_reexecute(), sd->rethrow_exception(), sd->return_oop());
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
    st->print("; implicit exception: dispatches to " INTPTR_FORMAT, p2i(code_begin() + cont_offset));
  }

}

class DirectNativeCallWrapper: public NativeCallWrapper {
private:
  NativeCall* _call;

public:
  DirectNativeCallWrapper(NativeCall* call) : _call(call) {}

  virtual address destination() const { return _call->destination(); }
  virtual address instruction_address() const { return _call->instruction_address(); }
  virtual address next_instruction_address() const { return _call->next_instruction_address(); }
  virtual address return_address() const { return _call->return_address(); }

  virtual address get_resolve_call_stub(bool is_optimized) const {
    if (is_optimized) {
      return SharedRuntime::get_resolve_opt_virtual_call_stub();
    }
    return SharedRuntime::get_resolve_virtual_call_stub();
  }

  virtual void set_destination_mt_safe(address dest) {
#if INCLUDE_AOT
    if (UseAOT) {
      CodeBlob* callee = CodeCache::find_blob(dest);
      CompiledMethod* cm = callee->as_compiled_method_or_null();
      if (cm != NULL && cm->is_far_code()) {
        // Temporary fix, see JDK-8143106
        CompiledDirectStaticCall* csc = CompiledDirectStaticCall::at(instruction_address());
        csc->set_to_far(methodHandle(cm->method()), dest);
        return;
      }
    }
#endif
    _call->set_destination_mt_safe(dest);
  }

  virtual void set_to_interpreted(const methodHandle& method, CompiledICInfo& info) {
    CompiledDirectStaticCall* csc = CompiledDirectStaticCall::at(instruction_address());
#if INCLUDE_AOT
    if (info.to_aot()) {
      csc->set_to_far(method, info.entry());
    } else
#endif
    {
      csc->set_to_interpreted(method, info.entry());
    }
  }

  virtual void verify() const {
    // make sure code pattern is actually a call imm32 instruction
    _call->verify();
    _call->verify_alignment();
  }

  virtual void verify_resolve_call(address dest) const {
    CodeBlob* db = CodeCache::find_blob_unsafe(dest);
    assert(db != NULL && !db->is_adapter_blob(), "must use stub!");
  }

  virtual bool is_call_to_interpreted(address dest) const {
    CodeBlob* cb = CodeCache::find_blob(_call->instruction_address());
    return cb->contains(dest);
  }

  virtual bool is_safe_for_patching() const { return false; }

  virtual NativeInstruction* get_load_instruction(virtual_call_Relocation* r) const {
    return nativeMovConstReg_at(r->cached_value());
  }

  virtual void *get_data(NativeInstruction* instruction) const {
    return (void*)((NativeMovConstReg*) instruction)->data();
  }

  virtual void set_data(NativeInstruction* instruction, intptr_t data) {
    ((NativeMovConstReg*) instruction)->set_data(data);
  }
};

NativeCallWrapper* nmethod::call_wrapper_at(address call) const {
  return new DirectNativeCallWrapper((NativeCall*) call);
}

NativeCallWrapper* nmethod::call_wrapper_before(address return_pc) const {
  return new DirectNativeCallWrapper(nativeCall_before(return_pc));
}

address nmethod::call_instruction_address(address pc) const {
  if (NativeCall::is_call_before(pc)) {
    NativeCall *ncall = nativeCall_before(pc);
    return ncall->instruction_address();
  }
  return NULL;
}

CompiledStaticCall* nmethod::compiledStaticCall_at(Relocation* call_site) const {
  return CompiledDirectStaticCall::at(call_site);
}

CompiledStaticCall* nmethod::compiledStaticCall_at(address call_site) const {
  return CompiledDirectStaticCall::at(call_site);
}

CompiledStaticCall* nmethod::compiledStaticCall_before(address return_addr) const {
  return CompiledDirectStaticCall::before(return_addr);
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
      CompiledICLocker ml_verify(this);
      CompiledIC_at(&iter)->print();
      break;
    }
    case relocInfo::static_call_type:
      st->print_cr("Static call at " INTPTR_FORMAT, p2i(iter.reloc()->addr()));
      CompiledDirectStaticCall::at(iter.reloc())->print();
      break;
    default:
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
#ifndef PRODUCT
  pc_nmethod_stats.print_pc_stats();
#endif
  Dependencies::print_statistics();
  if (xtty != NULL)  xtty->tail("statistics");
}

#endif // !PRODUCT

#if INCLUDE_JVMCI
void nmethod::clear_jvmci_installed_code() {
  assert_locked_or_safepoint(Patching_lock);
  if (_jvmci_installed_code != NULL) {
    JNIHandles::destroy_weak_global(_jvmci_installed_code);
    _jvmci_installed_code = NULL;
  }
}

void nmethod::clear_speculation_log() {
  assert_locked_or_safepoint(Patching_lock);
  if (_speculation_log != NULL) {
    JNIHandles::destroy_weak_global(_speculation_log);
    _speculation_log = NULL;
  }
}

void nmethod::maybe_invalidate_installed_code() {
  if (!is_compiled_by_jvmci()) {
    return;
  }

  assert(Patching_lock->is_locked() ||
         SafepointSynchronize::is_at_safepoint(), "should be performed under a lock for consistency");
  oop installed_code = JNIHandles::resolve(_jvmci_installed_code);
  if (installed_code != NULL) {
    // Update the values in the InstalledCode instance if it still refers to this nmethod
    nmethod* nm = (nmethod*)InstalledCode::address(installed_code);
    if (nm == this) {
      if (!is_alive() || is_unloading()) {
        // Break the link between nmethod and InstalledCode such that the nmethod
        // can subsequently be flushed safely.  The link must be maintained while
        // the method could have live activations since invalidateInstalledCode
        // might want to invalidate all existing activations.
        InstalledCode::set_address(installed_code, 0);
        InstalledCode::set_entryPoint(installed_code, 0);
      } else if (is_not_entrant()) {
        // Remove the entry point so any invocation will fail but keep
        // the address link around that so that existing activations can
        // be invalidated.
        InstalledCode::set_entryPoint(installed_code, 0);
      }
    }
  }
  if (!is_alive() || is_unloading()) {
    // Clear these out after the nmethod has been unregistered and any
    // updates to the InstalledCode instance have been performed.
    clear_jvmci_installed_code();
    clear_speculation_log();
  }
}

void nmethod::invalidate_installed_code(Handle installedCode, TRAPS) {
  if (installedCode() == NULL) {
    THROW(vmSymbols::java_lang_NullPointerException());
  }
  jlong nativeMethod = InstalledCode::address(installedCode);
  nmethod* nm = (nmethod*)nativeMethod;
  if (nm == NULL) {
    // Nothing to do
    return;
  }

  nmethodLocker nml(nm);
#ifdef ASSERT
  {
    MutexLockerEx pl(Patching_lock, Mutex::_no_safepoint_check_flag);
    // This relationship can only be checked safely under a lock
    assert(!nm->is_alive() || nm->is_unloading() || nm->jvmci_installed_code() == installedCode(), "sanity check");
  }
#endif

  if (nm->is_alive()) {
    // Invalidating the InstalledCode means we want the nmethod
    // to be deoptimized.
    nm->mark_for_deoptimization();
    VM_Deoptimize op;
    VMThread::execute(&op);
  }

  // Multiple threads could reach this point so we now need to
  // lock and re-check the link to the nmethod so that only one
  // thread clears it.
  MutexLockerEx pl(Patching_lock, Mutex::_no_safepoint_check_flag);
  if (InstalledCode::address(installedCode) == nativeMethod) {
      InstalledCode::set_address(installedCode, 0);
  }
}

oop nmethod::jvmci_installed_code() {
  return JNIHandles::resolve(_jvmci_installed_code);
}

oop nmethod::speculation_log() {
  return JNIHandles::resolve(_speculation_log);
}

char* nmethod::jvmci_installed_code_name(char* buf, size_t buflen) const {
  if (!this->is_compiled_by_jvmci()) {
    return NULL;
  }
  oop installed_code = JNIHandles::resolve(_jvmci_installed_code);
  if (installed_code != NULL) {
    oop installed_code_name = NULL;
    if (installed_code->is_a(InstalledCode::klass())) {
      installed_code_name = InstalledCode::name(installed_code);
    }
    if (installed_code_name != NULL) {
      return java_lang_String::as_utf8_string(installed_code_name, buf, (int)buflen);
    }
  }
  return NULL;
}
#endif
