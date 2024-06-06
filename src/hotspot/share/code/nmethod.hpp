/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CODE_NMETHOD_HPP
#define SHARE_CODE_NMETHOD_HPP

#include "code/codeBlob.hpp"
#include "code/pcDesc.hpp"
#include "oops/metadata.hpp"
#include "oops/method.hpp"

class AbstractCompiler;
class CompiledDirectCall;
class CompiledIC;
class CompiledICData;
class CompileTask;
class DepChange;
class Dependencies;
class DirectiveSet;
class DebugInformationRecorder;
class ExceptionHandlerTable;
class ImplicitExceptionTable;
class JvmtiThreadState;
class MetadataClosure;
class NativeCallWrapper;
class OopIterateClosure;
class ScopeDesc;
class xmlStream;

// This class is used internally by nmethods, to cache
// exception/pc/handler information.

class ExceptionCache : public CHeapObj<mtCode> {
  friend class VMStructs;
 private:
  enum { cache_size = 16 };
  Klass*   _exception_type;
  address  _pc[cache_size];
  address  _handler[cache_size];
  volatile int _count;
  ExceptionCache* volatile _next;
  ExceptionCache* _purge_list_next;

  inline address pc_at(int index);
  void set_pc_at(int index, address a)      { assert(index >= 0 && index < cache_size,""); _pc[index] = a; }

  inline address handler_at(int index);
  void set_handler_at(int index, address a) { assert(index >= 0 && index < cache_size,""); _handler[index] = a; }

  inline int count();
  // increment_count is only called under lock, but there may be concurrent readers.
  void increment_count();

 public:

  ExceptionCache(Handle exception, address pc, address handler);

  Klass*    exception_type()                { return _exception_type; }
  ExceptionCache* next();
  void      set_next(ExceptionCache *ec);
  ExceptionCache* purge_list_next()                 { return _purge_list_next; }
  void      set_purge_list_next(ExceptionCache *ec) { _purge_list_next = ec; }

  address match(Handle exception, address pc);
  bool    match_exception_with_space(Handle exception) ;
  address test_address(address addr);
  bool    add_address_and_handler(address addr, address handler) ;
};

// cache pc descs found in earlier inquiries
class PcDescCache {
  friend class VMStructs;
 private:
  enum { cache_size = 4 };
  // The array elements MUST be volatile! Several threads may modify
  // and read from the cache concurrently. find_pc_desc_internal has
  // returned wrong results. C++ compiler (namely xlC12) may duplicate
  // C++ field accesses if the elements are not volatile.
  typedef PcDesc* PcDescPtr;
  volatile PcDescPtr _pc_descs[cache_size]; // last cache_size pc_descs found
 public:
  PcDescCache() { debug_only(_pc_descs[0] = nullptr); }
  void    init_to(PcDesc* initial_pc_desc);
  PcDesc* find_pc_desc(int pc_offset, bool approximate);
  void    add_pc_desc(PcDesc* pc_desc);
  PcDesc* last_pc_desc() { return _pc_descs[0]; }
};

class PcDescContainer : public CHeapObj<mtCode> {
private:
  PcDescCache _pc_desc_cache;
public:
  PcDescContainer(PcDesc* initial_pc_desc) { _pc_desc_cache.init_to(initial_pc_desc); }

  PcDesc* find_pc_desc_internal(address pc, bool approximate, address code_begin,
                                PcDesc* lower, PcDesc* upper);

  PcDesc* find_pc_desc(address pc, bool approximate, address code_begin, PcDesc* lower, PcDesc* upper)
#ifdef PRODUCT
  {
    PcDesc* desc = _pc_desc_cache.last_pc_desc();
    assert(desc != nullptr, "PcDesc cache should be initialized already");
    if (desc->pc_offset() == (pc - code_begin)) {
      // Cached value matched
      return desc;
    }
    return find_pc_desc_internal(pc, approximate, code_begin, lower, upper);
  }
#endif
  ;
};

// nmethods (native methods) are the compiled code versions of Java methods.
//
// An nmethod contains:
//  - header                 (the nmethod structure)
//  [Relocation]
//  - relocation information
//  - constant part          (doubles, longs and floats used in nmethod)
//  - oop table
//  [Code]
//  - code body
//  - exception handler
//  - stub code
//  [Debugging information]
//  - oop array
//  - data array
//  - pcs
//  [Exception handler table]
//  - handler entry point array
//  [Implicit Null Pointer exception table]
//  - implicit null table array
//  [Speculations]
//  - encoded speculations array
//  [JVMCINMethodData]
//  - meta data for JVMCI compiled nmethod

#if INCLUDE_JVMCI
class FailedSpeculation;
class JVMCINMethodData;
#endif

class nmethod : public CodeBlob {
  friend class VMStructs;
  friend class JVMCIVMStructs;
  friend class CodeCache;  // scavengable oops
  friend class JVMCINMethodData;
  friend class DeoptimizationScope;

 private:

  // Used to track in which deoptimize handshake this method will be deoptimized.
  uint64_t  _deoptimization_generation;

  uint64_t  _gc_epoch;

  Method*   _method;

  // To reduce header size union fields which usages do not overlap.
  union {
    // To support simple linked-list chaining of nmethods:
    nmethod*  _osr_link; // from InstanceKlass::osr_nmethods_head
    struct {
      // These are used for compiled synchronized native methods to
      // locate the owner and stack slot for the BasicLock. They are
      // needed because there is no debug information for compiled native
      // wrappers and the oop maps are insufficient to allow
      // frame::retrieve_receiver() to work. Currently they are expected
      // to be byte offsets from the Java stack pointer for maximum code
      // sharing between platforms. JVMTI's GetLocalInstance() uses these
      // offsets to find the receiver for non-static native wrapper frames.
      ByteSize _native_receiver_sp_offset;
      ByteSize _native_basic_lock_sp_offset;
    };
  };

  // nmethod's read-only data
  address _immutable_data;

  PcDescContainer* _pc_desc_container;
  ExceptionCache* volatile _exception_cache;

  void* _gc_data;

  struct oops_do_mark_link; // Opaque data type.
  static nmethod*    volatile _oops_do_mark_nmethods;
  oops_do_mark_link* volatile _oops_do_mark_link;

  CompiledICData* _compiled_ic_data;

  // offsets for entry points
  address  _osr_entry_point;       // entry point for on stack replacement
  uint16_t _entry_offset;          // entry point with class check
  uint16_t _verified_entry_offset; // entry point without class check
  int      _entry_bci;             // != InvocationEntryBci if this nmethod is an on-stack replacement method
  int      _immutable_data_size;

  // _consts_offset == _content_offset because SECT_CONSTS is first in code buffer

  int _skipped_instructions_size;

  int _stub_offset;

  // Offsets for different stubs section parts
  int _exception_offset;
  // All deoptee's will resume execution at this location described by
  // this offset.
  int _deopt_handler_offset;
  // All deoptee's at a MethodHandle call site will resume execution
  // at this location described by this offset.
  int _deopt_mh_handler_offset;
  // Offset (from insts_end) of the unwind handler if it exists
  int16_t  _unwind_handler_offset;
  // Number of arguments passed on the stack
  uint16_t _num_stack_arg_slots;

  // Offsets in mutable data section
  // _oops_offset == _data_offset,  offset where embedded oop table begins (inside data)
  uint16_t _metadata_offset; // embedded meta data table
#if INCLUDE_JVMCI
  uint16_t _jvmci_data_offset;
#endif

  // Offset in immutable data section
  // _dependencies_offset == 0
  uint16_t _nul_chk_table_offset;
  uint16_t _handler_table_offset; // This table could be big in C1 code
  int      _scopes_pcs_offset;
  int      _scopes_data_offset;
#if INCLUDE_JVMCI
  int      _speculations_offset;
#endif

  // location in frame (offset for sp) that deopt can store the original
  // pc during a deopt.
  int _orig_pc_offset;

  int          _compile_id;            // which compilation made this nmethod
  CompLevel    _comp_level;            // compilation level (s1)
  CompilerType _compiler_type;         // which compiler made this nmethod (u1)

#if INCLUDE_RTM_OPT
  // RTM state at compile time. Used during deoptimization to decide
  // whether to restart collecting RTM locking abort statistic again.
  RTMState _rtm_state;
#endif

  // Local state used to keep track of whether unloading is happening or not
  volatile uint8_t _is_unloading_state;

  // Protected by NMethodState_lock
  volatile signed char _state;         // {not_installed, in_use, not_entrant}

  // set during construction
  uint8_t _has_unsafe_access:1,        // May fault due to unsafe access.
          _has_method_handle_invokes:1,// Has this method MethodHandle invokes?
          _has_wide_vectors:1,         // Preserve wide vectors at safepoints
          _has_monitors:1,             // Fastpath monitor detection for continuations
          _has_flushed_dependencies:1, // Used for maintenance of dependencies (under CodeCache_lock)
          _is_unlinked:1,              // mark during class unloading
          _load_reported:1;            // used by jvmti to track if an event has been posted for this nmethod

  enum DeoptimizationStatus : u1 {
    not_marked,
    deoptimize,
    deoptimize_noupdate,
    deoptimize_done
  };

  volatile DeoptimizationStatus _deoptimization_status; // Used for stack deoptimization

  DeoptimizationStatus deoptimization_status() const {
    return Atomic::load(&_deoptimization_status);
  }

  // Initialize fields to their default values
  void init_defaults(CodeBuffer *code_buffer, CodeOffsets* offsets);

  // Post initialization
  void post_init();

  // For native wrappers
  nmethod(Method* method,
          CompilerType type,
          int nmethod_size,
          int compile_id,
          CodeOffsets* offsets,
          CodeBuffer *code_buffer,
          int frame_size,
          ByteSize basic_lock_owner_sp_offset, /* synchronized natives only */
          ByteSize basic_lock_sp_offset,       /* synchronized natives only */
          OopMapSet* oop_maps);

  // For normal JIT compiled code
  nmethod(Method* method,
          CompilerType type,
          int nmethod_size,
          int immutable_data_size,
          int compile_id,
          int entry_bci,
          address immutable_data,
          CodeOffsets* offsets,
          int orig_pc_offset,
          DebugInformationRecorder *recorder,
          Dependencies* dependencies,
          CodeBuffer *code_buffer,
          int frame_size,
          OopMapSet* oop_maps,
          ExceptionHandlerTable* handler_table,
          ImplicitExceptionTable* nul_chk_table,
          AbstractCompiler* compiler,
          CompLevel comp_level
#if INCLUDE_JVMCI
          , char* speculations = nullptr,
          int speculations_len = 0,
          JVMCINMethodData* jvmci_data = nullptr
#endif
          );

  // helper methods
  void* operator new(size_t size, int nmethod_size, int comp_level) throw();

  // For method handle intrinsics: Try MethodNonProfiled, MethodProfiled and NonNMethod.
  // Attention: Only allow NonNMethod space for special nmethods which don't need to be
  // findable by nmethod iterators! In particular, they must not contain oops!
  void* operator new(size_t size, int nmethod_size, bool allow_NonNMethod_space) throw();

  const char* reloc_string_for(u_char* begin, u_char* end);

  bool try_transition(signed char new_state);

  // Returns true if this thread changed the state of the nmethod or
  // false if another thread performed the transition.
  bool make_entrant() { Unimplemented(); return false; }
  void inc_decompile_count();

  // Inform external interfaces that a compiled method has been unloaded
  void post_compiled_method_unload();

  PcDesc* find_pc_desc(address pc, bool approximate) {
    if (_pc_desc_container == nullptr) return nullptr; // native method
    return _pc_desc_container->find_pc_desc(pc, approximate, code_begin(), scopes_pcs_begin(), scopes_pcs_end());
  }

  // STW two-phase nmethod root processing helpers.
  //
  // When determining liveness of a given nmethod to do code cache unloading,
  // some collectors need to do different things depending on whether the nmethods
  // need to absolutely be kept alive during root processing; "strong"ly reachable
  // nmethods are known to be kept alive at root processing, but the liveness of
  // "weak"ly reachable ones is to be determined later.
  //
  // We want to allow strong and weak processing of nmethods by different threads
  // at the same time without heavy synchronization. Additional constraints are
  // to make sure that every nmethod is processed a minimal amount of time, and
  // nmethods themselves are always iterated at most once at a particular time.
  //
  // Note that strong processing work must be a superset of weak processing work
  // for this code to work.
  //
  // We store state and claim information in the _oops_do_mark_link member, using
  // the two LSBs for the state and the remaining upper bits for linking together
  // nmethods that were already visited.
  // The last element is self-looped, i.e. points to itself to avoid some special
  // "end-of-list" sentinel value.
  //
  // _oops_do_mark_link special values:
  //
  //   _oops_do_mark_link == nullptr: the nmethod has not been visited at all yet, i.e.
  //      is Unclaimed.
  //
  // For other values, its lowest two bits indicate the following states of the nmethod:
  //
  //   weak_request (WR): the nmethod has been claimed by a thread for weak processing
  //   weak_done (WD): weak processing has been completed for this nmethod.
  //   strong_request (SR): the nmethod has been found to need strong processing while
  //       being weak processed.
  //   strong_done (SD): strong processing has been completed for this nmethod .
  //
  // The following shows the _only_ possible progressions of the _oops_do_mark_link
  // pointer.
  //
  // Given
  //   N as the nmethod
  //   X the current next value of _oops_do_mark_link
  //
  // Unclaimed (C)-> N|WR (C)-> X|WD: the nmethod has been processed weakly by
  //   a single thread.
  // Unclaimed (C)-> N|WR (C)-> X|WD (O)-> X|SD: after weak processing has been
  //   completed (as above) another thread found that the nmethod needs strong
  //   processing after all.
  // Unclaimed (C)-> N|WR (O)-> N|SR (C)-> X|SD: during weak processing another
  //   thread finds that the nmethod needs strong processing, marks it as such and
  //   terminates. The original thread completes strong processing.
  // Unclaimed (C)-> N|SD (C)-> X|SD: the nmethod has been processed strongly from
  //   the beginning by a single thread.
  //
  // "|" describes the concatenation of bits in _oops_do_mark_link.
  //
  // The diagram also describes the threads responsible for changing the nmethod to
  // the next state by marking the _transition_ with (C) and (O), which mean "current"
  // and "other" thread respectively.
  //

  // States used for claiming nmethods during root processing.
  static const uint claim_weak_request_tag = 0;
  static const uint claim_weak_done_tag = 1;
  static const uint claim_strong_request_tag = 2;
  static const uint claim_strong_done_tag = 3;

  static oops_do_mark_link* mark_link(nmethod* nm, uint tag) {
    assert(tag <= claim_strong_done_tag, "invalid tag %u", tag);
    assert(is_aligned(nm, 4), "nmethod pointer must have zero lower two LSB");
    return (oops_do_mark_link*)(((uintptr_t)nm & ~0x3) | tag);
  }

  static uint extract_state(oops_do_mark_link* link) {
    return (uint)((uintptr_t)link & 0x3);
  }

  static nmethod* extract_nmethod(oops_do_mark_link* link) {
    return (nmethod*)((uintptr_t)link & ~0x3);
  }

  void oops_do_log_change(const char* state);

  static bool oops_do_has_weak_request(oops_do_mark_link* next) {
    return extract_state(next) == claim_weak_request_tag;
  }

  static bool oops_do_has_any_strong_state(oops_do_mark_link* next) {
    return extract_state(next) >= claim_strong_request_tag;
  }

  // Attempt Unclaimed -> N|WR transition. Returns true if successful.
  bool oops_do_try_claim_weak_request();

  // Attempt Unclaimed -> N|SD transition. Returns the current link.
  oops_do_mark_link* oops_do_try_claim_strong_done();
  // Attempt N|WR -> X|WD transition. Returns nullptr if successful, X otherwise.
  nmethod* oops_do_try_add_to_list_as_weak_done();

  // Attempt X|WD -> N|SR transition. Returns the current link.
  oops_do_mark_link* oops_do_try_add_strong_request(oops_do_mark_link* next);
  // Attempt X|WD -> X|SD transition. Returns true if successful.
  bool oops_do_try_claim_weak_done_as_strong_done(oops_do_mark_link* next);

  // Do the N|SD -> X|SD transition.
  void oops_do_add_to_list_as_strong_done();

  // Sets this nmethod as strongly claimed (as part of N|SD -> X|SD and N|SR -> X|SD
  // transitions).
  void oops_do_set_strong_done(nmethod* old_head);

public:
  // create nmethod with entry_bci
  static nmethod* new_nmethod(const methodHandle& method,
                              int compile_id,
                              int entry_bci,
                              CodeOffsets* offsets,
                              int orig_pc_offset,
                              DebugInformationRecorder* recorder,
                              Dependencies* dependencies,
                              CodeBuffer *code_buffer,
                              int frame_size,
                              OopMapSet* oop_maps,
                              ExceptionHandlerTable* handler_table,
                              ImplicitExceptionTable* nul_chk_table,
                              AbstractCompiler* compiler,
                              CompLevel comp_level
#if INCLUDE_JVMCI
                              , char* speculations = nullptr,
                              int speculations_len = 0,
                              JVMCINMethodData* jvmci_data = nullptr
#endif
  );

  static nmethod* new_native_nmethod(const methodHandle& method,
                                     int compile_id,
                                     CodeBuffer *code_buffer,
                                     int vep_offset,
                                     int frame_complete,
                                     int frame_size,
                                     ByteSize receiver_sp_offset,
                                     ByteSize basic_lock_sp_offset,
                                     OopMapSet* oop_maps,
                                     int exception_handler = -1);

  Method* method       () const { return _method; }
  bool is_native_method() const { return _method != nullptr && _method->is_native(); }
  bool is_java_method  () const { return _method != nullptr && !_method->is_native(); }
  bool is_osr_method   () const { return _entry_bci != InvocationEntryBci; }

  // Compiler task identification.  Note that all OSR methods
  // are numbered in an independent sequence if CICountOSR is true,
  // and native method wrappers are also numbered independently if
  // CICountNative is true.
  int compile_id() const { return _compile_id; }
  const char* compile_kind() const;

  inline bool  is_compiled_by_c1   () const { return _compiler_type == compiler_c1; }
  inline bool  is_compiled_by_c2   () const { return _compiler_type == compiler_c2; }
  inline bool  is_compiled_by_jvmci() const { return _compiler_type == compiler_jvmci; }
  CompilerType compiler_type       () const { return _compiler_type; }
  const char*  compiler_name       () const;

  // boundaries for different parts
  address consts_begin          () const { return           content_begin(); }
  address consts_end            () const { return           code_begin()   ; }
  address insts_begin           () const { return           code_begin()   ; }
  address insts_end             () const { return           header_begin() + _stub_offset             ; }
  address stub_begin            () const { return           header_begin() + _stub_offset             ; }
  address stub_end              () const { return           data_begin()   ; }
  address exception_begin       () const { return           header_begin() + _exception_offset        ; }
  address deopt_handler_begin   () const { return           header_begin() + _deopt_handler_offset    ; }
  address deopt_mh_handler_begin() const { return           header_begin() + _deopt_mh_handler_offset ; }
  address unwind_handler_begin  () const { return _unwind_handler_offset != -1 ? (insts_end() - _unwind_handler_offset) : nullptr; }

  // mutable data
  oop*    oops_begin            () const { return (oop*)        data_begin(); }
  oop*    oops_end              () const { return (oop*)       (data_begin() + _metadata_offset)      ; }
  Metadata** metadata_begin     () const { return (Metadata**) (data_begin() + _metadata_offset)      ; }
#if INCLUDE_JVMCI
  Metadata** metadata_end       () const { return (Metadata**) (data_begin() + _jvmci_data_offset)    ; }
  address jvmci_data_begin      () const { return               data_begin() + _jvmci_data_offset     ; }
  address jvmci_data_end        () const { return               data_end(); }
#else
  Metadata** metadata_end       () const { return (Metadata**)  data_end(); }
#endif

  // immutable data
  address immutable_data_begin  () const { return           _immutable_data; }
  address immutable_data_end    () const { return           _immutable_data + _immutable_data_size ; }
  address dependencies_begin    () const { return           _immutable_data; }
  address dependencies_end      () const { return           _immutable_data + _nul_chk_table_offset; }
  address nul_chk_table_begin   () const { return           _immutable_data + _nul_chk_table_offset; }
  address nul_chk_table_end     () const { return           _immutable_data + _handler_table_offset; }
  address handler_table_begin   () const { return           _immutable_data + _handler_table_offset; }
  address handler_table_end     () const { return           _immutable_data + _scopes_pcs_offset   ; }
  PcDesc* scopes_pcs_begin      () const { return (PcDesc*)(_immutable_data + _scopes_pcs_offset)  ; }
  PcDesc* scopes_pcs_end        () const { return (PcDesc*)(_immutable_data + _scopes_data_offset) ; }
  address scopes_data_begin     () const { return           _immutable_data + _scopes_data_offset  ; }

#if INCLUDE_JVMCI
  address scopes_data_end       () const { return           _immutable_data + _speculations_offset ; }
  address speculations_begin    () const { return           _immutable_data + _speculations_offset ; }
  address speculations_end      () const { return            immutable_data_end(); }
#else
  address scopes_data_end       () const { return            immutable_data_end(); }
#endif

  // Sizes
  int immutable_data_size() const { return _immutable_data_size; }
  int consts_size        () const { return int(          consts_end       () -           consts_begin       ()); }
  int insts_size         () const { return int(          insts_end        () -           insts_begin        ()); }
  int stub_size          () const { return int(          stub_end         () -           stub_begin         ()); }
  int oops_size          () const { return int((address) oops_end         () - (address) oops_begin         ()); }
  int metadata_size      () const { return int((address) metadata_end     () - (address) metadata_begin     ()); }
  int scopes_data_size   () const { return int(          scopes_data_end  () -           scopes_data_begin  ()); }
  int scopes_pcs_size    () const { return int((intptr_t)scopes_pcs_end   () - (intptr_t)scopes_pcs_begin   ()); }
  int dependencies_size  () const { return int(          dependencies_end () -           dependencies_begin ()); }
  int handler_table_size () const { return int(          handler_table_end() -           handler_table_begin()); }
  int nul_chk_table_size () const { return int(          nul_chk_table_end() -           nul_chk_table_begin()); }
#if INCLUDE_JVMCI
  int speculations_size  () const { return int(          speculations_end () -           speculations_begin ()); }
  int jvmci_data_size    () const { return int(          jvmci_data_end   () -           jvmci_data_begin   ()); }
#endif

  int     oops_count() const { assert(oops_size() % oopSize == 0, "");  return (oops_size() / oopSize) + 1; }
  int metadata_count() const { assert(metadata_size() % wordSize == 0, ""); return (metadata_size() / wordSize) + 1; }

  int skipped_instructions_size () const { return _skipped_instructions_size; }
  int total_size() const;

  // Containment
  bool consts_contains         (address addr) const { return consts_begin       () <= addr && addr < consts_end       (); }
  // Returns true if a given address is in the 'insts' section. The method
  // insts_contains_inclusive() is end-inclusive.
  bool insts_contains          (address addr) const { return insts_begin        () <= addr && addr < insts_end        (); }
  bool insts_contains_inclusive(address addr) const { return insts_begin        () <= addr && addr <= insts_end       (); }
  bool stub_contains           (address addr) const { return stub_begin         () <= addr && addr < stub_end         (); }
  bool oops_contains           (oop*    addr) const { return oops_begin         () <= addr && addr < oops_end         (); }
  bool metadata_contains       (Metadata** addr) const { return metadata_begin  () <= addr && addr < metadata_end     (); }
  bool scopes_data_contains    (address addr) const { return scopes_data_begin  () <= addr && addr < scopes_data_end  (); }
  bool scopes_pcs_contains     (PcDesc* addr) const { return scopes_pcs_begin   () <= addr && addr < scopes_pcs_end   (); }
  bool handler_table_contains  (address addr) const { return handler_table_begin() <= addr && addr < handler_table_end(); }
  bool nul_chk_table_contains  (address addr) const { return nul_chk_table_begin() <= addr && addr < nul_chk_table_end(); }

  // entry points
  address entry_point() const          { return code_begin() + _entry_offset;          } // normal entry point
  address verified_entry_point() const { return code_begin() + _verified_entry_offset; } // if klass is correct

  enum : signed char { not_installed = -1, // in construction, only the owner doing the construction is
                                           // allowed to advance state
                       in_use        = 0,  // executable nmethod
                       not_entrant   = 1   // marked for deoptimization but activations may still exist
  };

  // flag accessing and manipulation
  bool is_not_installed() const        { return _state == not_installed; }
  bool is_in_use() const               { return _state <= in_use; }
  bool is_not_entrant() const          { return _state == not_entrant; }
  int  get_state() const               { return _state; }

  void clear_unloading_state();
  // Heuristically deduce an nmethod isn't worth keeping around
  bool is_cold();
  bool is_unloading();
  void do_unloading(bool unloading_occurred);

#if INCLUDE_RTM_OPT
  // rtm state accessing and manipulating
  RTMState  rtm_state() const          { return _rtm_state; }
  void set_rtm_state(RTMState state)   { _rtm_state = state; }
#endif

  bool make_in_use() {
    return try_transition(in_use);
  }
  // Make the nmethod non entrant. The nmethod will continue to be
  // alive.  It is used when an uncommon trap happens.  Returns true
  // if this thread changed the state of the nmethod or false if
  // another thread performed the transition.
  bool  make_not_entrant();
  bool  make_not_used()    { return make_not_entrant(); }

  bool  is_marked_for_deoptimization() const { return deoptimization_status() != not_marked; }
  bool  has_been_deoptimized() const { return deoptimization_status() == deoptimize_done; }
  void  set_deoptimized_done();

  bool update_recompile_counts() const {
    // Update recompile counts when either the update is explicitly requested (deoptimize)
    // or the nmethod is not marked for deoptimization at all (not_marked).
    // The latter happens during uncommon traps when deoptimized nmethod is made not entrant.
    DeoptimizationStatus status = deoptimization_status();
    return status != deoptimize_noupdate && status != deoptimize_done;
  }

  // tells whether frames described by this nmethod can be deoptimized
  // note: native wrappers cannot be deoptimized.
  bool can_be_deoptimized() const { return is_java_method(); }

  bool has_dependencies()                         { return dependencies_size() != 0; }
  void print_dependencies_on(outputStream* out) PRODUCT_RETURN;
  void flush_dependencies();

  template<typename T>
  T* gc_data() const                              { return reinterpret_cast<T*>(_gc_data); }
  template<typename T>
  void set_gc_data(T* gc_data)                    { _gc_data = reinterpret_cast<void*>(gc_data); }

  bool  has_unsafe_access() const                 { return _has_unsafe_access; }
  void  set_has_unsafe_access(bool z)             { _has_unsafe_access = z; }

  bool  has_monitors() const                      { return _has_monitors; }
  void  set_has_monitors(bool z)                  { _has_monitors = z; }

  bool  has_method_handle_invokes() const         { return _has_method_handle_invokes; }
  void  set_has_method_handle_invokes(bool z)     { _has_method_handle_invokes = z; }

  bool  has_wide_vectors() const                  { return _has_wide_vectors; }
  void  set_has_wide_vectors(bool z)              { _has_wide_vectors = z; }

  bool  has_flushed_dependencies() const          { return _has_flushed_dependencies; }
  void  set_has_flushed_dependencies(bool z)      {
    assert(!has_flushed_dependencies(), "should only happen once");
    _has_flushed_dependencies = z;
  }

  bool  is_unlinked() const                       { return _is_unlinked; }
  void  set_is_unlinked()                         {
     assert(!_is_unlinked, "already unlinked");
      _is_unlinked = true;
  }

  int   comp_level() const                        { return _comp_level; }

  // Support for oops in scopes and relocs:
  // Note: index 0 is reserved for null.
  oop   oop_at(int index) const;
  oop   oop_at_phantom(int index) const; // phantom reference
  oop*  oop_addr_at(int index) const {  // for GC
    // relocation indexes are biased by 1 (because 0 is reserved)
    assert(index > 0 && index <= oops_count(), "must be a valid non-zero index");
    return &oops_begin()[index - 1];
  }

  // Support for meta data in scopes and relocs:
  // Note: index 0 is reserved for null.
  Metadata*   metadata_at(int index) const      { return index == 0 ? nullptr: *metadata_addr_at(index); }
  Metadata**  metadata_addr_at(int index) const {  // for GC
    // relocation indexes are biased by 1 (because 0 is reserved)
    assert(index > 0 && index <= metadata_count(), "must be a valid non-zero index");
    return &metadata_begin()[index - 1];
  }

  void copy_values(GrowableArray<jobject>* oops);
  void copy_values(GrowableArray<Metadata*>* metadata);

  // Relocation support
private:
  void fix_oop_relocations(address begin, address end, bool initialize_immediates);
  inline void initialize_immediate_oop(oop* dest, jobject handle);

protected:
  address oops_reloc_begin() const;

public:
  void fix_oop_relocations(address begin, address end) { fix_oop_relocations(begin, end, false); }
  void fix_oop_relocations()                           { fix_oop_relocations(nullptr, nullptr, false); }

  bool is_at_poll_return(address pc);
  bool is_at_poll_or_poll_return(address pc);

protected:
  // Exception cache support
  // Note: _exception_cache may be read and cleaned concurrently.
  ExceptionCache* exception_cache() const         { return _exception_cache; }
  ExceptionCache* exception_cache_acquire() const;

public:
  address handler_for_exception_and_pc(Handle exception, address pc);
  void add_handler_for_exception_and_pc(Handle exception, address pc, address handler);
  void clean_exception_cache();

  void add_exception_cache_entry(ExceptionCache* new_entry);
  ExceptionCache* exception_cache_entry_for_exception(Handle exception);


  // MethodHandle
  bool is_method_handle_return(address return_pc);
  // Deopt
  // Return true is the PC is one would expect if the frame is being deopted.
  inline bool is_deopt_pc(address pc);
  inline bool is_deopt_mh_entry(address pc);
  inline bool is_deopt_entry(address pc);

  // Accessor/mutator for the original pc of a frame before a frame was deopted.
  address get_original_pc(const frame* fr) { return *orig_pc_addr(fr); }
  void    set_original_pc(const frame* fr, address pc) { *orig_pc_addr(fr) = pc; }

  const char* state() const;

  bool inlinecache_check_contains(address addr) const {
    return (addr >= code_begin() && addr < verified_entry_point());
  }

  void preserve_callee_argument_oops(frame fr, const RegisterMap *reg_map, OopClosure* f);

  // implicit exceptions support
  address continuation_for_implicit_div0_exception(address pc) { return continuation_for_implicit_exception(pc, true); }
  address continuation_for_implicit_null_exception(address pc) { return continuation_for_implicit_exception(pc, false); }

  // Inline cache support for class unloading and nmethod unloading
 private:
  void cleanup_inline_caches_impl(bool unloading_occurred, bool clean_all);

  address continuation_for_implicit_exception(address pc, bool for_div0_check);

 public:
  // Serial version used by whitebox test
  void cleanup_inline_caches_whitebox();

  void clear_inline_caches();

  // Execute nmethod barrier code, as if entering through nmethod call.
  void run_nmethod_entry_barrier();

  void verify_oop_relocations();

  bool has_evol_metadata();

  Method* attached_method(address call_pc);
  Method* attached_method_before_pc(address pc);

  // GC unloading support
  // Cleans unloaded klasses and unloaded nmethods in inline caches

  void unload_nmethod_caches(bool class_unloading_occurred);

  void unlink_from_method();

  // On-stack replacement support
  int      osr_entry_bci()    const { assert(is_osr_method(), "wrong kind of nmethod"); return _entry_bci; }
  address  osr_entry()        const { assert(is_osr_method(), "wrong kind of nmethod"); return _osr_entry_point; }
  nmethod* osr_link()         const { return _osr_link; }
  void     set_osr_link(nmethod *n) { _osr_link = n; }
  void     invalidate_osr_method();

  int num_stack_arg_slots(bool rounded = true) const {
    return rounded ? align_up(_num_stack_arg_slots, 2) : _num_stack_arg_slots;
  }

  // Verify calls to dead methods have been cleaned.
  void verify_clean_inline_caches();

  // Unlink this nmethod from the system
  void unlink();

  // Deallocate this nmethod - called by the GC
  void purge(bool unregister_nmethod);

  // See comment at definition of _last_seen_on_stack
  void mark_as_maybe_on_stack();
  bool is_maybe_on_stack();

  // Evolution support. We make old (discarded) compiled methods point to new Method*s.
  void set_method(Method* method) { _method = method; }

#if INCLUDE_JVMCI
  // Gets the JVMCI name of this nmethod.
  const char* jvmci_name();

  // Records the pending failed speculation in the
  // JVMCI speculation log associated with this nmethod.
  void update_speculation(JavaThread* thread);

  // Gets the data specific to a JVMCI compiled method.
  // This returns a non-nullptr value iff this nmethod was
  // compiled by the JVMCI compiler.
  JVMCINMethodData* jvmci_nmethod_data() const {
    return jvmci_data_size() == 0 ? nullptr : (JVMCINMethodData*) jvmci_data_begin();
  }
#endif

  void oops_do(OopClosure* f) { oops_do(f, false); }
  void oops_do(OopClosure* f, bool allow_dead);

  // All-in-one claiming of nmethods: returns true if the caller successfully claimed that
  // nmethod.
  bool oops_do_try_claim();

  // Loom support for following nmethods on the stack
  void follow_nmethod(OopIterateClosure* cl);

  // Class containing callbacks for the oops_do_process_weak/strong() methods
  // below.
  class OopsDoProcessor {
  public:
    // Process the oops of the given nmethod based on whether it has been called
    // in a weak or strong processing context, i.e. apply either weak or strong
    // work on it.
    virtual void do_regular_processing(nmethod* nm) = 0;
    // Assuming that the oops of the given nmethod has already been its weak
    // processing applied, apply the remaining strong processing part.
    virtual void do_remaining_strong_processing(nmethod* nm) = 0;
  };

  // The following two methods do the work corresponding to weak/strong nmethod
  // processing.
  void oops_do_process_weak(OopsDoProcessor* p);
  void oops_do_process_strong(OopsDoProcessor* p);

  static void oops_do_marking_prologue();
  static void oops_do_marking_epilogue();

 private:
  ScopeDesc* scope_desc_in(address begin, address end);

  address* orig_pc_addr(const frame* fr);

  // used by jvmti to track if the load events has been reported
  bool  load_reported() const                     { return _load_reported; }
  void  set_load_reported()                       { _load_reported = true; }

 public:
  // ScopeDesc retrieval operation
  PcDesc* pc_desc_at(address pc)   { return find_pc_desc(pc, false); }
  // pc_desc_near returns the first PcDesc at or after the given pc.
  PcDesc* pc_desc_near(address pc) { return find_pc_desc(pc, true); }

  // ScopeDesc for an instruction
  ScopeDesc* scope_desc_at(address pc);
  ScopeDesc* scope_desc_near(address pc);

  // copying of debugging information
  void copy_scopes_pcs(PcDesc* pcs, int count);
  void copy_scopes_data(address buffer, int size);

  int orig_pc_offset() { return _orig_pc_offset; }

  // Post successful compilation
  void post_compiled_method(CompileTask* task);

  // jvmti support:
  void post_compiled_method_load_event(JvmtiThreadState* state = nullptr);

  // verify operations
  void verify() override;
  void verify_scopes();
  void verify_interrupt_point(address interrupt_point, bool is_inline_cache);

  // Disassemble this nmethod with additional debug information, e.g. information about blocks.
  void decode2(outputStream* st) const;
  void print_constant_pool(outputStream* st);

  // Avoid hiding of parent's 'decode(outputStream*)' method.
  void decode(outputStream* st) const { decode2(st); } // just delegate here.

  // printing support
  void print()                 const override;
  void print(outputStream* st) const;
  void print_code();

#if defined(SUPPORT_DATA_STRUCTS)
  // print output in opt build for disassembler library
  void print_relocations()                        PRODUCT_RETURN;
  void print_pcs_on(outputStream* st);
  void print_scopes() { print_scopes_on(tty); }
  void print_scopes_on(outputStream* st)          PRODUCT_RETURN;
  void print_value_on(outputStream* st) const override;
  void print_handler_table();
  void print_nul_chk_table();
  void print_recorded_oop(int log_n, int index);
  void print_recorded_oops();
  void print_recorded_metadata();

  void print_oops(outputStream* st);     // oops from the underlying CodeBlob.
  void print_metadata(outputStream* st); // metadata in metadata pool.
#else
  void print_pcs_on(outputStream* st) { return; }
#endif

  void print_calls(outputStream* st)              PRODUCT_RETURN;
  static void print_statistics()                  PRODUCT_RETURN;

  void maybe_print_nmethod(const DirectiveSet* directive);
  void print_nmethod(bool print_code);

  // need to re-define this from CodeBlob else the overload hides it
  void print_on(outputStream* st) const override { CodeBlob::print_on(st); }
  void print_on(outputStream* st, const char* msg) const;

  // Logging
  void log_identity(xmlStream* log) const;
  void log_new_nmethod() const;
  void log_state_change() const;

  // Prints block-level comments, including nmethod specific block labels:
  void print_block_comment(outputStream* stream, address block_begin) const override {
#if defined(SUPPORT_ASSEMBLY) || defined(SUPPORT_ABSTRACT_ASSEMBLY)
    print_nmethod_labels(stream, block_begin);
    CodeBlob::print_block_comment(stream, block_begin);
#endif
  }

  void print_nmethod_labels(outputStream* stream, address block_begin, bool print_section_labels=true) const;
  const char* nmethod_section_label(address pos) const;

  // returns whether this nmethod has code comments.
  bool has_code_comment(address begin, address end);
  // Prints a comment for one native instruction (reloc info, pc desc)
  void print_code_comment_on(outputStream* st, int column, address begin, address end);

  // tells if this compiled method is dependent on the given changes,
  // and the changes have invalidated it
  bool check_dependency_on(DepChange& changes);

  // Fast breakpoint support. Tells if this compiled method is
  // dependent on the given method. Returns true if this nmethod
  // corresponds to the given method as well.
  bool is_dependent_on_method(Method* dependee);

  // JVMTI's GetLocalInstance() support
  ByteSize native_receiver_sp_offset() {
    assert(is_native_method(), "sanity");
    return _native_receiver_sp_offset;
  }
  ByteSize native_basic_lock_sp_offset() {
    assert(is_native_method(), "sanity");
    return _native_basic_lock_sp_offset;
  }

  // support for code generation
  static ByteSize osr_entry_point_offset() { return byte_offset_of(nmethod, _osr_entry_point); }
  static ByteSize state_offset()           { return byte_offset_of(nmethod, _state); }

  void metadata_do(MetadataClosure* f);

  address call_instruction_address(address pc) const;

  void make_deoptimized();
  void finalize_relocations();
};

#endif // SHARE_CODE_NMETHOD_HPP
