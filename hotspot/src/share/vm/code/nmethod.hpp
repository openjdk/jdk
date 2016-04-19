/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CODE_NMETHOD_HPP
#define SHARE_VM_CODE_NMETHOD_HPP

#include "code/codeBlob.hpp"
#include "code/pcDesc.hpp"
#include "oops/metadata.hpp"

class DirectiveSet;

// This class is used internally by nmethods, to cache
// exception/pc/handler information.

class ExceptionCache : public CHeapObj<mtCode> {
  friend class VMStructs;
 private:
  enum { cache_size = 16 };
  Klass*   _exception_type;
  address  _pc[cache_size];
  address  _handler[cache_size];
  int      _count;
  ExceptionCache* _next;

  address pc_at(int index)                     { assert(index >= 0 && index < count(),""); return _pc[index]; }
  void    set_pc_at(int index, address a)      { assert(index >= 0 && index < cache_size,""); _pc[index] = a; }
  address handler_at(int index)                { assert(index >= 0 && index < count(),""); return _handler[index]; }
  void    set_handler_at(int index, address a) { assert(index >= 0 && index < cache_size,""); _handler[index] = a; }
  int     count()                              { return _count; }
  void    increment_count()                    { _count++; }

 public:

  ExceptionCache(Handle exception, address pc, address handler);

  Klass*    exception_type()                { return _exception_type; }
  ExceptionCache* next()                    { return _next; }
  void      set_next(ExceptionCache *ec)    { _next = ec; }

  address match(Handle exception, address pc);
  bool    match_exception_with_space(Handle exception) ;
  address test_address(address addr);
  bool    add_address_and_handler(address addr, address handler) ;
};


// cache pc descs found in earlier inquiries
class PcDescCache VALUE_OBJ_CLASS_SPEC {
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
  PcDescCache() { debug_only(_pc_descs[0] = NULL); }
  void    reset_to(PcDesc* initial_pc_desc);
  PcDesc* find_pc_desc(int pc_offset, bool approximate);
  void    add_pc_desc(PcDesc* pc_desc);
  PcDesc* last_pc_desc() { return _pc_descs[0]; }
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

class DepChange;
class Dependencies;
class ExceptionHandlerTable;
class ImplicitExceptionTable;
class AbstractCompiler;
class xmlStream;

class nmethod : public CodeBlob {
  friend class VMStructs;
  friend class JVMCIVMStructs;
  friend class NMethodSweeper;
  friend class CodeCache;  // scavengable oops
 private:

  // GC support to help figure out if an nmethod has been
  // cleaned/unloaded by the current GC.
  static unsigned char _global_unloading_clock;

  // Shared fields for all nmethod's
  Method*   _method;
  int       _entry_bci;        // != InvocationEntryBci if this nmethod is an on-stack replacement method
  jmethodID _jmethod_id;       // Cache of method()->jmethod_id()

#if INCLUDE_JVMCI
  // Needed to keep nmethods alive that are not the default nmethod for the associated Method.
  oop       _jvmci_installed_code;
  oop       _speculation_log;
#endif

  // To support simple linked-list chaining of nmethods:
  nmethod*  _osr_link;         // from InstanceKlass::osr_nmethods_head

  union {
    // Used by G1 to chain nmethods.
    nmethod* _unloading_next;
    // Used by non-G1 GCs to chain nmethods.
    nmethod* _scavenge_root_link; // from CodeCache::scavenge_root_nmethods
  };

  static nmethod* volatile _oops_do_mark_nmethods;
  nmethod*        volatile _oops_do_mark_link;

  AbstractCompiler* _compiler; // The compiler which compiled this nmethod

  // offsets for entry points
  address _entry_point;                      // entry point with class check
  address _verified_entry_point;             // entry point without class check
  address _osr_entry_point;                  // entry point for on stack replacement

  // Offsets for different nmethod parts
  int _exception_offset;
  // All deoptee's will resume execution at this location described by
  // this offset.
  int _deoptimize_offset;
  // All deoptee's at a MethodHandle call site will resume execution
  // at this location described by this offset.
  int _deoptimize_mh_offset;
  // Offset of the unwind handler if it exists
  int _unwind_handler_offset;

  int _consts_offset;
  int _stub_offset;
  int _oops_offset;                       // offset to where embedded oop table begins (inside data)
  int _metadata_offset;                   // embedded meta data table
  int _scopes_data_offset;
  int _scopes_pcs_offset;
  int _dependencies_offset;
  int _handler_table_offset;
  int _nul_chk_table_offset;
  int _nmethod_end_offset;

  // location in frame (offset for sp) that deopt can store the original
  // pc during a deopt.
  int _orig_pc_offset;

  int _compile_id;                           // which compilation made this nmethod
  int _comp_level;                           // compilation level

  // protected by CodeCache_lock
  bool _has_flushed_dependencies;            // Used for maintenance of dependencies (CodeCache_lock)

  enum MarkForDeoptimizationStatus {
    not_marked,
    deoptimize,
    deoptimize_noupdate };

  MarkForDeoptimizationStatus _mark_for_deoptimization_status; // Used for stack deoptimization

  // used by jvmti to track if an unload event has been posted for this nmethod.
  bool _unload_reported;

  // set during construction
  unsigned int _has_unsafe_access:1;         // May fault due to unsafe access.
  unsigned int _has_method_handle_invokes:1; // Has this method MethodHandle invokes?
  unsigned int _lazy_critical_native:1;      // Lazy JNI critical native
  unsigned int _has_wide_vectors:1;          // Preserve wide vectors at safepoints

  // Protected by Patching_lock
  volatile unsigned char _state;             // {in_use, not_entrant, zombie, unloaded}

  volatile unsigned char _unloading_clock;   // Incremented after GC unloaded/cleaned the nmethod

#ifdef ASSERT
  bool _oops_are_stale;  // indicates that it's no longer safe to access oops section
#endif

  jbyte _scavenge_root_state;

#if INCLUDE_RTM_OPT
  // RTM state at compile time. Used during deoptimization to decide
  // whether to restart collecting RTM locking abort statistic again.
  RTMState _rtm_state;
#endif

  // Nmethod Flushing lock. If non-zero, then the nmethod is not removed
  // and is not made into a zombie. However, once the nmethod is made into
  // a zombie, it will be locked one final time if CompiledMethodUnload
  // event processing needs to be done.
  volatile jint _lock_count;

  // not_entrant method removal. Each mark_sweep pass will update
  // this mark to current sweep invocation count if it is seen on the
  // stack.  An not_entrant method can be removed when there are no
  // more activations, i.e., when the _stack_traversal_mark is less than
  // current sweep traversal index.
  long _stack_traversal_mark;

  // The _hotness_counter indicates the hotness of a method. The higher
  // the value the hotter the method. The hotness counter of a nmethod is
  // set to [(ReservedCodeCacheSize / (1024 * 1024)) * 2] each time the method
  // is active while stack scanning (mark_active_nmethods()). The hotness
  // counter is decreased (by 1) while sweeping.
  int _hotness_counter;

  ExceptionCache *_exception_cache;
  PcDescCache     _pc_desc_cache;

  // These are used for compiled synchronized native methods to
  // locate the owner and stack slot for the BasicLock so that we can
  // properly revoke the bias of the owner if necessary. They are
  // needed because there is no debug information for compiled native
  // wrappers and the oop maps are insufficient to allow
  // frame::retrieve_receiver() to work. Currently they are expected
  // to be byte offsets from the Java stack pointer for maximum code
  // sharing between platforms. Note that currently biased locking
  // will never cause Class instances to be biased but this code
  // handles the static synchronized case as well.
  // JVMTI's GetLocalInstance() also uses these offsets to find the receiver
  // for non-static native wrapper frames.
  ByteSize _native_receiver_sp_offset;
  ByteSize _native_basic_lock_sp_offset;

  friend class nmethodLocker;

  // For native wrappers
  nmethod(Method* method,
          int nmethod_size,
          int compile_id,
          CodeOffsets* offsets,
          CodeBuffer *code_buffer,
          int frame_size,
          ByteSize basic_lock_owner_sp_offset, /* synchronized natives only */
          ByteSize basic_lock_sp_offset,       /* synchronized natives only */
          OopMapSet* oop_maps);

  // Creation support
  nmethod(Method* method,
          int nmethod_size,
          int compile_id,
          int entry_bci,
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
          int comp_level
#if INCLUDE_JVMCI
          , Handle installed_code,
          Handle speculation_log
#endif
          );

  // helper methods
  void* operator new(size_t size, int nmethod_size, int comp_level) throw();

  const char* reloc_string_for(u_char* begin, u_char* end);
  // Returns true if this thread changed the state of the nmethod or
  // false if another thread performed the transition.
  bool make_not_entrant_or_zombie(unsigned int state);
  void inc_decompile_count();

  // Used to manipulate the exception cache
  void add_exception_cache_entry(ExceptionCache* new_entry);
  ExceptionCache* exception_cache_entry_for_exception(Handle exception);

  // Inform external interfaces that a compiled method has been unloaded
  void post_compiled_method_unload();

  // Initailize fields to their default values
  void init_defaults();

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
                              int comp_level
#if INCLUDE_JVMCI
                              , Handle installed_code = Handle(),
                              Handle speculation_log = Handle()
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
                                     OopMapSet* oop_maps);

  // accessors
  Method* method() const                          { return _method; }
  AbstractCompiler* compiler() const              { return _compiler; }

  // type info
  bool is_nmethod() const                         { return true; }
  bool is_java_method() const                     { return !method()->is_native(); }
  bool is_native_method() const                   { return method()->is_native(); }
  bool is_osr_method() const                      { return _entry_bci != InvocationEntryBci; }

  bool is_compiled_by_c1() const;
  bool is_compiled_by_jvmci() const;
  bool is_compiled_by_c2() const;
  bool is_compiled_by_shark() const;

  // boundaries for different parts
  address consts_begin          () const          { return           header_begin() + _consts_offset        ; }
  address consts_end            () const          { return           header_begin() +  code_offset()        ; }
  address insts_begin           () const          { return           header_begin() +  code_offset()        ; }
  address insts_end             () const          { return           header_begin() + _stub_offset          ; }
  address stub_begin            () const          { return           header_begin() + _stub_offset          ; }
  address stub_end              () const          { return           header_begin() + _oops_offset          ; }
  address exception_begin       () const          { return           header_begin() + _exception_offset     ; }
  address deopt_handler_begin   () const          { return           header_begin() + _deoptimize_offset    ; }
  address deopt_mh_handler_begin() const          { return           header_begin() + _deoptimize_mh_offset ; }
  address unwind_handler_begin  () const          { return _unwind_handler_offset != -1 ? (header_begin() + _unwind_handler_offset) : NULL; }
  oop*    oops_begin            () const          { return (oop*)   (header_begin() + _oops_offset)         ; }
  oop*    oops_end              () const          { return (oop*)   (header_begin() + _metadata_offset)     ; }

  Metadata** metadata_begin   () const            { return (Metadata**)  (header_begin() + _metadata_offset)     ; }
  Metadata** metadata_end     () const            { return (Metadata**)  (header_begin() + _scopes_data_offset)  ; }

  address scopes_data_begin     () const          { return           header_begin() + _scopes_data_offset   ; }
  address scopes_data_end       () const          { return           header_begin() + _scopes_pcs_offset    ; }
  PcDesc* scopes_pcs_begin      () const          { return (PcDesc*)(header_begin() + _scopes_pcs_offset   ); }
  PcDesc* scopes_pcs_end        () const          { return (PcDesc*)(header_begin() + _dependencies_offset) ; }
  address dependencies_begin    () const          { return           header_begin() + _dependencies_offset  ; }
  address dependencies_end      () const          { return           header_begin() + _handler_table_offset ; }
  address handler_table_begin   () const          { return           header_begin() + _handler_table_offset ; }
  address handler_table_end     () const          { return           header_begin() + _nul_chk_table_offset ; }
  address nul_chk_table_begin   () const          { return           header_begin() + _nul_chk_table_offset ; }
  address nul_chk_table_end     () const          { return           header_begin() + _nmethod_end_offset   ; }

  // Sizes
  int consts_size       () const                  { return            consts_end       () -            consts_begin       (); }
  int insts_size        () const                  { return            insts_end        () -            insts_begin        (); }
  int stub_size         () const                  { return            stub_end         () -            stub_begin         (); }
  int oops_size         () const                  { return (address)  oops_end         () - (address)  oops_begin         (); }
  int metadata_size     () const                  { return (address)  metadata_end     () - (address)  metadata_begin     (); }
  int scopes_data_size  () const                  { return            scopes_data_end  () -            scopes_data_begin  (); }
  int scopes_pcs_size   () const                  { return (intptr_t) scopes_pcs_end   () - (intptr_t) scopes_pcs_begin   (); }
  int dependencies_size () const                  { return            dependencies_end () -            dependencies_begin (); }
  int handler_table_size() const                  { return            handler_table_end() -            handler_table_begin(); }
  int nul_chk_table_size() const                  { return            nul_chk_table_end() -            nul_chk_table_begin(); }

  int     oops_count() const { assert(oops_size() % oopSize == 0, "");  return (oops_size() / oopSize) + 1; }
  int metadata_count() const { assert(metadata_size() % wordSize == 0, ""); return (metadata_size() / wordSize) + 1; }

  int total_size        () const;

  void dec_hotness_counter()        { _hotness_counter--; }
  void set_hotness_counter(int val) { _hotness_counter = val; }
  int  hotness_counter() const      { return _hotness_counter; }

  // Containment
  bool consts_contains       (address addr) const { return consts_begin       () <= addr && addr < consts_end       (); }
  bool insts_contains        (address addr) const { return insts_begin        () <= addr && addr < insts_end        (); }
  bool stub_contains         (address addr) const { return stub_begin         () <= addr && addr < stub_end         (); }
  bool oops_contains         (oop*    addr) const { return oops_begin         () <= addr && addr < oops_end         (); }
  bool metadata_contains     (Metadata** addr) const   { return metadata_begin     () <= addr && addr < metadata_end     (); }
  bool scopes_data_contains  (address addr) const { return scopes_data_begin  () <= addr && addr < scopes_data_end  (); }
  bool scopes_pcs_contains   (PcDesc* addr) const { return scopes_pcs_begin   () <= addr && addr < scopes_pcs_end   (); }
  bool handler_table_contains(address addr) const { return handler_table_begin() <= addr && addr < handler_table_end(); }
  bool nul_chk_table_contains(address addr) const { return nul_chk_table_begin() <= addr && addr < nul_chk_table_end(); }

  // entry points
  address entry_point() const                     { return _entry_point;             } // normal entry point
  address verified_entry_point() const            { return _verified_entry_point;    } // if klass is correct

  enum { in_use       = 0,   // executable nmethod
         not_entrant  = 1,   // marked for deoptimization but activations may still exist,
                             // will be transformed to zombie when all activations are gone
         zombie       = 2,   // no activations exist, nmethod is ready for purge
         unloaded     = 3 }; // there should be no activations, should not be called,
                             // will be transformed to zombie immediately

  // flag accessing and manipulation
  bool  is_in_use() const                         { return _state == in_use; }
  bool  is_alive() const                          { return _state == in_use || _state == not_entrant; }
  bool  is_not_entrant() const                    { return _state == not_entrant; }
  bool  is_zombie() const                         { return _state == zombie; }
  bool  is_unloaded() const                       { return _state == unloaded; }

  // returns a string version of the nmethod state
  const char* state() const {
    switch(_state) {
      case in_use:      return "in use";
      case not_entrant: return "not_entrant";
      case zombie:      return "zombie";
      case unloaded:    return "unloaded";
      default:
        fatal("unexpected nmethod state: %d", _state);
        return NULL;
    }
  }

#if INCLUDE_RTM_OPT
  // rtm state accessing and manipulating
  RTMState  rtm_state() const                     { return _rtm_state; }
  void set_rtm_state(RTMState state)              { _rtm_state = state; }
#endif

  // Make the nmethod non entrant. The nmethod will continue to be
  // alive.  It is used when an uncommon trap happens.  Returns true
  // if this thread changed the state of the nmethod or false if
  // another thread performed the transition.
  bool  make_not_entrant() {
    assert(!method()->is_method_handle_intrinsic(), "Cannot make MH intrinsic not entrant");
    return make_not_entrant_or_zombie(not_entrant);
  }
  bool  make_zombie()      { return make_not_entrant_or_zombie(zombie); }

  // used by jvmti to track if the unload event has been reported
  bool  unload_reported()                         { return _unload_reported; }
  void  set_unload_reported()                     { _unload_reported = true; }

  void set_unloading_next(nmethod* next)          { _unloading_next = next; }
  nmethod* unloading_next()                       { return _unloading_next; }

  static unsigned char global_unloading_clock()   { return _global_unloading_clock; }
  static void increase_unloading_clock();

  void set_unloading_clock(unsigned char unloading_clock);
  unsigned char unloading_clock();

  bool  is_marked_for_deoptimization() const      { return _mark_for_deoptimization_status != not_marked; }
  void  mark_for_deoptimization(bool inc_recompile_counts = true) {
    _mark_for_deoptimization_status = (inc_recompile_counts ? deoptimize : deoptimize_noupdate);
  }
  bool update_recompile_counts() const {
    // Update recompile counts when either the update is explicitly requested (deoptimize)
    // or the nmethod is not marked for deoptimization at all (not_marked).
    // The latter happens during uncommon traps when deoptimized nmethod is made not entrant.
    return _mark_for_deoptimization_status != deoptimize_noupdate;
  }

  void  make_unloaded(BoolObjectClosure* is_alive, oop cause);

  bool has_dependencies()                         { return dependencies_size() != 0; }
  void flush_dependencies(BoolObjectClosure* is_alive);
  bool has_flushed_dependencies()                 { return _has_flushed_dependencies; }
  void set_has_flushed_dependencies()             {
    assert(!has_flushed_dependencies(), "should only happen once");
    _has_flushed_dependencies = 1;
  }

  bool  has_unsafe_access() const                 { return _has_unsafe_access; }
  void  set_has_unsafe_access(bool z)             { _has_unsafe_access = z; }

  bool  has_method_handle_invokes() const         { return _has_method_handle_invokes; }
  void  set_has_method_handle_invokes(bool z)     { _has_method_handle_invokes = z; }

  bool  is_lazy_critical_native() const           { return _lazy_critical_native; }
  void  set_lazy_critical_native(bool z)          { _lazy_critical_native = z; }

  bool  has_wide_vectors() const                  { return _has_wide_vectors; }
  void  set_has_wide_vectors(bool z)              { _has_wide_vectors = z; }

  int   comp_level() const                        { return _comp_level; }

  // Support for oops in scopes and relocs:
  // Note: index 0 is reserved for null.
  oop   oop_at(int index) const                   { return index == 0 ? (oop) NULL: *oop_addr_at(index); }
  oop*  oop_addr_at(int index) const {  // for GC
    // relocation indexes are biased by 1 (because 0 is reserved)
    assert(index > 0 && index <= oops_count(), "must be a valid non-zero index");
    assert(!_oops_are_stale, "oops are stale");
    return &oops_begin()[index - 1];
  }

  // Support for meta data in scopes and relocs:
  // Note: index 0 is reserved for null.
  Metadata*     metadata_at(int index) const      { return index == 0 ? NULL: *metadata_addr_at(index); }
  Metadata**  metadata_addr_at(int index) const {  // for GC
    // relocation indexes are biased by 1 (because 0 is reserved)
    assert(index > 0 && index <= metadata_count(), "must be a valid non-zero index");
    return &metadata_begin()[index - 1];
  }

  void copy_values(GrowableArray<jobject>* oops);
  void copy_values(GrowableArray<Metadata*>* metadata);

  Method* attached_method(address call_pc);
  Method* attached_method_before_pc(address pc);

  // Relocation support
private:
  void fix_oop_relocations(address begin, address end, bool initialize_immediates);
  inline void initialize_immediate_oop(oop* dest, jobject handle);

public:
  void fix_oop_relocations(address begin, address end) { fix_oop_relocations(begin, end, false); }
  void fix_oop_relocations()                           { fix_oop_relocations(NULL, NULL, false); }
  void verify_oop_relocations();

  bool is_at_poll_return(address pc);
  bool is_at_poll_or_poll_return(address pc);

  // Scavengable oop support
  bool  on_scavenge_root_list() const                  { return (_scavenge_root_state & 1) != 0; }
 protected:
  enum { sl_on_list = 0x01, sl_marked = 0x10 };
  void  set_on_scavenge_root_list()                    { _scavenge_root_state = sl_on_list; }
  void  clear_on_scavenge_root_list()                  { _scavenge_root_state = 0; }
  // assertion-checking and pruning logic uses the bits of _scavenge_root_state
#ifndef PRODUCT
  void  set_scavenge_root_marked()                     { _scavenge_root_state |= sl_marked; }
  void  clear_scavenge_root_marked()                   { _scavenge_root_state &= ~sl_marked; }
  bool  scavenge_root_not_marked()                     { return (_scavenge_root_state &~ sl_on_list) == 0; }
  // N.B. there is no positive marked query, and we only use the not_marked query for asserts.
#endif //PRODUCT
  nmethod* scavenge_root_link() const                  { return _scavenge_root_link; }
  void     set_scavenge_root_link(nmethod *n)          { _scavenge_root_link = n; }

 public:

  // Sweeper support
  long  stack_traversal_mark()                    { return _stack_traversal_mark; }
  void  set_stack_traversal_mark(long l)          { _stack_traversal_mark = l; }

  // Exception cache support
  ExceptionCache* exception_cache() const         { return _exception_cache; }
  void set_exception_cache(ExceptionCache *ec)    { _exception_cache = ec; }
  address handler_for_exception_and_pc(Handle exception, address pc);
  void add_handler_for_exception_and_pc(Handle exception, address pc, address handler);
  void clean_exception_cache(BoolObjectClosure* is_alive);

  // implicit exceptions support
  address continuation_for_implicit_exception(address pc);

  // On-stack replacement support
  int   osr_entry_bci() const                     { assert(is_osr_method(), "wrong kind of nmethod"); return _entry_bci; }
  address  osr_entry() const                      { assert(is_osr_method(), "wrong kind of nmethod"); return _osr_entry_point; }
  void  invalidate_osr_method();
  nmethod* osr_link() const                       { return _osr_link; }
  void     set_osr_link(nmethod *n)               { _osr_link = n; }

  // tells whether frames described by this nmethod can be deoptimized
  // note: native wrappers cannot be deoptimized.
  bool can_be_deoptimized() const { return is_java_method(); }

  // Inline cache support
  void clear_inline_caches();
  void clear_ic_stubs();
  void cleanup_inline_caches(bool clean_all = false);
  bool inlinecache_check_contains(address addr) const {
    return (addr >= code_begin() && addr < verified_entry_point());
  }

  // Verify calls to dead methods have been cleaned.
  void verify_clean_inline_caches();
  // Verify and count cached icholder relocations.
  int  verify_icholder_relocations();
  // Check that all metadata is still alive
  void verify_metadata_loaders(address low_boundary, BoolObjectClosure* is_alive);

  // unlink and deallocate this nmethod
  // Only NMethodSweeper class is expected to use this. NMethodSweeper is not
  // expected to use any other private methods/data in this class.

 protected:
  void flush();

 public:
  // When true is returned, it is unsafe to remove this nmethod even if
  // it is a zombie, since the VM or the ServiceThread might still be
  // using it.
  bool is_locked_by_vm() const                    { return _lock_count >0; }

  // See comment at definition of _last_seen_on_stack
  void mark_as_seen_on_stack();
  bool can_convert_to_zombie();

  // Evolution support. We make old (discarded) compiled methods point to new Method*s.
  void set_method(Method* method) { _method = method; }

#if INCLUDE_JVMCI
  oop jvmci_installed_code() { return _jvmci_installed_code ; }
  char* jvmci_installed_code_name(char* buf, size_t buflen);

  // Update the state of any InstalledCode instance associated with
  // this nmethod based on the current value of _state.
  void maybe_invalidate_installed_code();

  // Helper function to invalidate InstalledCode instances
  static void invalidate_installed_code(Handle installed_code, TRAPS);

  oop speculation_log() { return _speculation_log ; }

 private:
  void clear_jvmci_installed_code();

 public:
#endif

  // GC support
  void do_unloading(BoolObjectClosure* is_alive, bool unloading_occurred);
  //  The parallel versions are used by G1.
  bool do_unloading_parallel(BoolObjectClosure* is_alive, bool unloading_occurred);
  void do_unloading_parallel_postponed(BoolObjectClosure* is_alive, bool unloading_occurred);

 private:
  //  Unload a nmethod if the *root object is dead.
  bool can_unload(BoolObjectClosure* is_alive, oop* root, bool unloading_occurred);
  bool unload_if_dead_at(RelocIterator *iter_at_oop, BoolObjectClosure* is_alive, bool unloading_occurred);

 public:
  void preserve_callee_argument_oops(frame fr, const RegisterMap *reg_map,
                                     OopClosure* f);
  void oops_do(OopClosure* f) { oops_do(f, false); }
  void oops_do(OopClosure* f, bool allow_zombie);
  bool detect_scavenge_root_oops();
  void verify_scavenge_root_oops() PRODUCT_RETURN;

  bool test_set_oops_do_mark();
  static void oops_do_marking_prologue();
  static void oops_do_marking_epilogue();
  static bool oops_do_marking_is_active() { return _oops_do_mark_nmethods != NULL; }
  bool test_oops_do_mark() { return _oops_do_mark_link != NULL; }

  // ScopeDesc for an instruction
  ScopeDesc* scope_desc_at(address pc);

 private:
  ScopeDesc* scope_desc_in(address begin, address end);

  address* orig_pc_addr(const frame* fr) { return (address*) ((address)fr->unextended_sp() + _orig_pc_offset); }

  PcDesc* find_pc_desc_internal(address pc, bool approximate);

  PcDesc* find_pc_desc(address pc, bool approximate) {
    PcDesc* desc = _pc_desc_cache.last_pc_desc();
    if (desc != NULL && desc->pc_offset() == pc - code_begin()) {
      return desc;
    }
    return find_pc_desc_internal(pc, approximate);
  }

 public:
  // ScopeDesc retrieval operation
  PcDesc* pc_desc_at(address pc)   { return find_pc_desc(pc, false); }
  // pc_desc_near returns the first PcDesc at or after the givne pc.
  PcDesc* pc_desc_near(address pc) { return find_pc_desc(pc, true); }

 public:
  // copying of debugging information
  void copy_scopes_pcs(PcDesc* pcs, int count);
  void copy_scopes_data(address buffer, int size);

  // Deopt
  // Return true is the PC is one would expect if the frame is being deopted.
  bool is_deopt_pc      (address pc) { return is_deopt_entry(pc) || is_deopt_mh_entry(pc); }
  bool is_deopt_entry   (address pc);
  bool is_deopt_mh_entry(address pc) { return pc == deopt_mh_handler_begin(); }
  // Accessor/mutator for the original pc of a frame before a frame was deopted.
  address get_original_pc(const frame* fr) { return *orig_pc_addr(fr); }
  void    set_original_pc(const frame* fr, address pc) { *orig_pc_addr(fr) = pc; }

  static address get_deopt_original_pc(const frame* fr);

  // MethodHandle
  bool is_method_handle_return(address return_pc);

  // jvmti support:
  void post_compiled_method_load_event();
  jmethodID get_and_cache_jmethod_id();

  // verify operations
  void verify();
  void verify_scopes();
  void verify_interrupt_point(address interrupt_point);

  // printing support
  void print()                          const;
  void print_relocations()                        PRODUCT_RETURN;
  void print_pcs()                                PRODUCT_RETURN;
  void print_scopes()                             PRODUCT_RETURN;
  void print_dependencies()                       PRODUCT_RETURN;
  void print_value_on(outputStream* st) const     PRODUCT_RETURN;
  void print_calls(outputStream* st)              PRODUCT_RETURN;
  void print_handler_table()                      PRODUCT_RETURN;
  void print_nul_chk_table()                      PRODUCT_RETURN;
  void print_recorded_oops()                      PRODUCT_RETURN;
  void print_recorded_metadata()                  PRODUCT_RETURN;

  void maybe_print_nmethod(DirectiveSet* directive);
  void print_nmethod(bool print_code);

  // need to re-define this from CodeBlob else the overload hides it
  virtual void print_on(outputStream* st) const { CodeBlob::print_on(st); }
  void print_on(outputStream* st, const char* msg) const;

  // Logging
  void log_identity(xmlStream* log) const;
  void log_new_nmethod() const;
  void log_state_change() const;

  // Prints block-level comments, including nmethod specific block labels:
  virtual void print_block_comment(outputStream* stream, address block_begin) const {
    print_nmethod_labels(stream, block_begin);
    CodeBlob::print_block_comment(stream, block_begin);
  }
  void print_nmethod_labels(outputStream* stream, address block_begin) const;

  // Prints a comment for one native instruction (reloc info, pc desc)
  void print_code_comment_on(outputStream* st, int column, address begin, address end);
  static void print_statistics() PRODUCT_RETURN;

  // Compiler task identification.  Note that all OSR methods
  // are numbered in an independent sequence if CICountOSR is true,
  // and native method wrappers are also numbered independently if
  // CICountNative is true.
  int  compile_id() const                         { return _compile_id; }
  const char* compile_kind() const;

  // tells if any of this method's dependencies have been invalidated
  // (this is expensive!)
  static void check_all_dependencies(DepChange& changes);

  // tells if this compiled method is dependent on the given changes,
  // and the changes have invalidated it
  bool check_dependency_on(DepChange& changes);

  // Evolution support. Tells if this compiled method is dependent on any of
  // methods m() of class dependee, such that if m() in dependee is replaced,
  // this compiled method will have to be deoptimized.
  bool is_evol_dependent_on(Klass* dependee);

  // Fast breakpoint support. Tells if this compiled method is
  // dependent on the given method. Returns true if this nmethod
  // corresponds to the given method as well.
  bool is_dependent_on_method(Method* dependee);

  // is it ok to patch at address?
  bool is_patchable_at(address instr_address);

  // UseBiasedLocking support
  ByteSize native_receiver_sp_offset() {
    return _native_receiver_sp_offset;
  }
  ByteSize native_basic_lock_sp_offset() {
    return _native_basic_lock_sp_offset;
  }

  // support for code generation
  static int verified_entry_point_offset()        { return offset_of(nmethod, _verified_entry_point); }
  static int osr_entry_point_offset()             { return offset_of(nmethod, _osr_entry_point); }
  static int state_offset()                       { return offset_of(nmethod, _state); }

  // RedefineClasses support.   Mark metadata in nmethods as on_stack so that
  // redefine classes doesn't purge it.
  static void mark_on_stack(nmethod* nm) {
    nm->metadata_do(Metadata::mark_on_stack);
  }
  void metadata_do(void f(Metadata*));
};

// Locks an nmethod so its code will not get removed and it will not
// be made into a zombie, even if it is a not_entrant method. After the
// nmethod becomes a zombie, if CompiledMethodUnload event processing
// needs to be done, then lock_nmethod() is used directly to keep the
// generated code from being reused too early.
class nmethodLocker : public StackObj {
  nmethod* _nm;

 public:

  // note: nm can be NULL
  // Only JvmtiDeferredEvent::compiled_method_unload_event()
  // should pass zombie_ok == true.
  static void lock_nmethod(nmethod* nm, bool zombie_ok = false);
  static void unlock_nmethod(nmethod* nm); // (ditto)

  nmethodLocker(address pc); // derive nm from pc
  nmethodLocker(nmethod *nm) { _nm = nm; lock_nmethod(_nm); }
  nmethodLocker() { _nm = NULL; }
  ~nmethodLocker() { unlock_nmethod(_nm); }

  nmethod* code() { return _nm; }
  void set_code(nmethod* new_nm) {
    unlock_nmethod(_nm);   // note:  This works even if _nm==new_nm.
    _nm = new_nm;
    lock_nmethod(_nm);
  }
};

#endif // SHARE_VM_CODE_NMETHOD_HPP
