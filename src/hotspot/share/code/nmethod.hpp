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

#ifndef SHARE_CODE_NMETHOD_HPP
#define SHARE_CODE_NMETHOD_HPP

#include "code/compiledMethod.hpp"

class DepChange;
class DirectiveSet;
class DebugInformationRecorder;

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

class nmethod : public CompiledMethod {
  friend class VMStructs;
  friend class JVMCIVMStructs;
  friend class NMethodSweeper;
  friend class CodeCache;  // scavengable oops
 private:

  // Shared fields for all nmethod's
  int       _entry_bci;        // != InvocationEntryBci if this nmethod is an on-stack replacement method
  jmethodID _jmethod_id;       // Cache of method()->jmethod_id()

#if INCLUDE_JVMCI
  // A weak reference to an InstalledCode object associated with
  // this nmethod.
  jweak     _jvmci_installed_code;

  // A weak reference to a SpeculationLog object associated with
  // this nmethod.
  jweak     _speculation_log;

  // Determines whether this nmethod is unloaded when the
  // referent in _jvmci_installed_code is cleared. This
  // will be false if the referent is initialized to a
  // HotSpotNMethod object whose isDefault field is true.
  // That is, installed code other than a "default"
  // HotSpotNMethod causes nmethod unloading.
  // This field is ignored once _jvmci_installed_code is NULL.
  bool _jvmci_installed_code_triggers_invalidation;
#endif

  // To support simple linked-list chaining of nmethods:
  nmethod*  _osr_link;         // from InstanceKlass::osr_nmethods_head

  static nmethod* volatile _oops_do_mark_nmethods;
  nmethod*        volatile _oops_do_mark_link;

  // offsets for entry points
  address _entry_point;                      // entry point with class check
  address _verified_entry_point;             // entry point without class check
  address _osr_entry_point;                  // entry point for on stack replacement

  // Offsets for different nmethod parts
  int  _exception_offset;
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

  int code_offset() const { return (address) code_begin() - header_begin(); }

  // location in frame (offset for sp) that deopt can store the original
  // pc during a deopt.
  int _orig_pc_offset;

  int _compile_id;                           // which compilation made this nmethod
  int _comp_level;                           // compilation level

  // protected by CodeCache_lock
  bool _has_flushed_dependencies;            // Used for maintenance of dependencies (CodeCache_lock)

  // used by jvmti to track if an unload event has been posted for this nmethod.
  bool _unload_reported;

  // Protected by Patching_lock
  volatile signed char _state;               // {not_installed, in_use, not_entrant, zombie, unloaded}

#ifdef ASSERT
  bool _oops_are_stale;  // indicates that it's no longer safe to access oops section
#endif

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
  volatile long _stack_traversal_mark;

  // The _hotness_counter indicates the hotness of a method. The higher
  // the value the hotter the method. The hotness counter of a nmethod is
  // set to [(ReservedCodeCacheSize / (1024 * 1024)) * 2] each time the method
  // is active while stack scanning (mark_active_nmethods()). The hotness
  // counter is decreased (by 1) while sweeping.
  int _hotness_counter;

  // Local state used to keep track of whether unloading is happening or not
  volatile uint8_t _is_unloading_state;

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
          CompilerType type,
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
          CompilerType type,
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
          , jweak installed_code,
          jweak speculation_log
#endif
          );

  // helper methods
  void* operator new(size_t size, int nmethod_size, int comp_level) throw();

  const char* reloc_string_for(u_char* begin, u_char* end);
  // Returns true if this thread changed the state of the nmethod or
  // false if another thread performed the transition.
  bool make_not_entrant_or_zombie(int state);
  bool make_entrant() { Unimplemented(); return false; }
  void inc_decompile_count();

  // Inform external interfaces that a compiled method has been unloaded
  void post_compiled_method_unload();

  // Initailize fields to their default values
  void init_defaults();

  // Offsets
  int content_offset() const                  { return content_begin() - header_begin(); }
  int data_offset() const                     { return _data_offset; }

  address header_end() const                  { return (address)    header_begin() + header_size(); }

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
                              , jweak installed_code = NULL,
                              jweak speculation_log = NULL
#endif
  );

  // Only used for unit tests.
  nmethod()
    : CompiledMethod(),
      _is_unloading_state(0),
      _native_receiver_sp_offset(in_ByteSize(-1)),
      _native_basic_lock_sp_offset(in_ByteSize(-1)) {}


  static nmethod* new_native_nmethod(const methodHandle& method,
                                     int compile_id,
                                     CodeBuffer *code_buffer,
                                     int vep_offset,
                                     int frame_complete,
                                     int frame_size,
                                     ByteSize receiver_sp_offset,
                                     ByteSize basic_lock_sp_offset,
                                     OopMapSet* oop_maps);

  // type info
  bool is_nmethod() const                         { return true; }
  bool is_osr_method() const                      { return _entry_bci != InvocationEntryBci; }

  // boundaries for different parts
  address consts_begin          () const          { return           header_begin() + _consts_offset        ; }
  address consts_end            () const          { return           code_begin()                           ; }
  address stub_begin            () const          { return           header_begin() + _stub_offset          ; }
  address stub_end              () const          { return           header_begin() + _oops_offset          ; }
  address exception_begin       () const          { return           header_begin() + _exception_offset     ; }
  address unwind_handler_begin  () const          { return _unwind_handler_offset != -1 ? (header_begin() + _unwind_handler_offset) : NULL; }
  oop*    oops_begin            () const          { return (oop*)   (header_begin() + _oops_offset)         ; }
  oop*    oops_end              () const          { return (oop*)   (header_begin() + _metadata_offset)     ; }

  Metadata** metadata_begin   () const            { return (Metadata**)  (header_begin() + _metadata_offset)     ; }
  Metadata** metadata_end     () const            { return (Metadata**)  _scopes_data_begin; }

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
  int oops_size         () const                  { return (address)  oops_end         () - (address)  oops_begin         (); }
  int metadata_size     () const                  { return (address)  metadata_end     () - (address)  metadata_begin     (); }
  int dependencies_size () const                  { return            dependencies_end () -            dependencies_begin (); }

  int     oops_count() const { assert(oops_size() % oopSize == 0, "");  return (oops_size() / oopSize) + 1; }
  int metadata_count() const { assert(metadata_size() % wordSize == 0, ""); return (metadata_size() / wordSize) + 1; }

  int total_size        () const;

  void dec_hotness_counter()        { _hotness_counter--; }
  void set_hotness_counter(int val) { _hotness_counter = val; }
  int  hotness_counter() const      { return _hotness_counter; }

  // Containment
  bool oops_contains         (oop*    addr) const { return oops_begin         () <= addr && addr < oops_end         (); }
  bool metadata_contains     (Metadata** addr) const   { return metadata_begin     () <= addr && addr < metadata_end     (); }
  bool scopes_data_contains  (address addr) const { return scopes_data_begin  () <= addr && addr < scopes_data_end  (); }
  bool scopes_pcs_contains   (PcDesc* addr) const { return scopes_pcs_begin   () <= addr && addr < scopes_pcs_end   (); }

  // entry points
  address entry_point() const                     { return _entry_point;             } // normal entry point
  address verified_entry_point() const            { return _verified_entry_point;    } // if klass is correct

  // flag accessing and manipulation
  bool  is_not_installed() const                  { return _state == not_installed; }
  bool  is_in_use() const                         { return _state <= in_use; }
  bool  is_alive() const                          { return _state < zombie; }
  bool  is_not_entrant() const                    { return _state == not_entrant; }
  bool  is_zombie() const                         { return _state == zombie; }
  bool  is_unloaded() const                       { return _state == unloaded; }

  void clear_unloading_state();
  virtual bool is_unloading();
  virtual void do_unloading(bool unloading_occurred);

#if INCLUDE_RTM_OPT
  // rtm state accessing and manipulating
  RTMState  rtm_state() const                     { return _rtm_state; }
  void set_rtm_state(RTMState state)              { _rtm_state = state; }
#endif

  void make_in_use()                              { _state = in_use; }
  // Make the nmethod non entrant. The nmethod will continue to be
  // alive.  It is used when an uncommon trap happens.  Returns true
  // if this thread changed the state of the nmethod or false if
  // another thread performed the transition.
  bool  make_not_entrant() {
    assert(!method()->is_method_handle_intrinsic(), "Cannot make MH intrinsic not entrant");
    return make_not_entrant_or_zombie(not_entrant);
  }
  bool  make_not_used()    { return make_not_entrant(); }
  bool  make_zombie()      { return make_not_entrant_or_zombie(zombie); }

  // used by jvmti to track if the unload event has been reported
  bool  unload_reported()                         { return _unload_reported; }
  void  set_unload_reported()                     { _unload_reported = true; }

  int get_state() const {
    return _state;
  }

  void  make_unloaded();

  bool has_dependencies()                         { return dependencies_size() != 0; }
  void flush_dependencies(bool delete_immediately);
  bool has_flushed_dependencies()                 { return _has_flushed_dependencies; }
  void set_has_flushed_dependencies()             {
    assert(!has_flushed_dependencies(), "should only happen once");
    _has_flushed_dependencies = 1;
  }

  int   comp_level() const                        { return _comp_level; }

  void unlink_from_method(bool acquire_lock);

  // Support for oops in scopes and relocs:
  // Note: index 0 is reserved for null.
  oop   oop_at(int index) const;
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

  // Relocation support
private:
  void fix_oop_relocations(address begin, address end, bool initialize_immediates);
  inline void initialize_immediate_oop(oop* dest, jobject handle);

public:
  void fix_oop_relocations(address begin, address end) { fix_oop_relocations(begin, end, false); }
  void fix_oop_relocations()                           { fix_oop_relocations(NULL, NULL, false); }

  // Sweeper support
  long  stack_traversal_mark()                    { return _stack_traversal_mark; }
  void  set_stack_traversal_mark(long l)          { _stack_traversal_mark = l; }

  // implicit exceptions support
  address continuation_for_implicit_exception(address pc);

  // On-stack replacement support
  int   osr_entry_bci() const                     { assert(is_osr_method(), "wrong kind of nmethod"); return _entry_bci; }
  address  osr_entry() const                      { assert(is_osr_method(), "wrong kind of nmethod"); return _osr_entry_point; }
  void  invalidate_osr_method();
  nmethod* osr_link() const                       { return _osr_link; }
  void     set_osr_link(nmethod *n)               { _osr_link = n; }

  // Verify calls to dead methods have been cleaned.
  void verify_clean_inline_caches();

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
  // Gets the InstalledCode object associated with this nmethod
  // which may be NULL if this nmethod was not compiled by JVMCI
  // or the weak reference has been cleared.
  oop jvmci_installed_code();

  // Copies the value of the name field in the InstalledCode
  // object (if any) associated with this nmethod into buf.
  // Returns the value of buf if it was updated otherwise NULL.
  char* jvmci_installed_code_name(char* buf, size_t buflen) const;

  // Updates the state of the InstalledCode (if any) associated with
  // this nmethod based on the current value of _state.
  void maybe_invalidate_installed_code();

  // Deoptimizes the nmethod (if any) in the address field of a given
  // InstalledCode object. The address field is zeroed upon return.
  static void invalidate_installed_code(Handle installed_code, TRAPS);

  // Gets the SpeculationLog object associated with this nmethod
  // which may be NULL if this nmethod was not compiled by JVMCI
  // or the weak reference has been cleared.
  oop speculation_log();

 private:
  // Deletes the weak reference (if any) to the InstalledCode object
  // associated with this nmethod.
  void clear_jvmci_installed_code();

  // Deletes the weak reference (if any) to the SpeculationLog object
  // associated with this nmethod.
  void clear_speculation_log();

 public:
#endif

 public:
  void oops_do(OopClosure* f) { oops_do(f, false); }
  void oops_do(OopClosure* f, bool allow_zombie);

  bool test_set_oops_do_mark();
  static void oops_do_marking_prologue();
  static void oops_do_marking_epilogue();
  static bool oops_do_marking_is_active() { return _oops_do_mark_nmethods != NULL; }
  bool test_oops_do_mark() { return _oops_do_mark_link != NULL; }

 private:
  ScopeDesc* scope_desc_in(address begin, address end);

  address* orig_pc_addr(const frame* fr);

 public:
  // copying of debugging information
  void copy_scopes_pcs(PcDesc* pcs, int count);
  void copy_scopes_data(address buffer, int size);

  // Accessor/mutator for the original pc of a frame before a frame was deopted.
  address get_original_pc(const frame* fr) { return *orig_pc_addr(fr); }
  void    set_original_pc(const frame* fr, address pc) { *orig_pc_addr(fr) = pc; }

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
  virtual int compile_id() const { return _compile_id; }
  const char* compile_kind() const;

  // tells if any of this method's dependencies have been invalidated
  // (this is expensive!)
  static void check_all_dependencies(DepChange& changes);

  // tells if this compiled method is dependent on the given changes,
  // and the changes have invalidated it
  bool check_dependency_on(DepChange& changes);

  // Evolution support. Tells if this compiled method is dependent on any of
  // redefined methods, such that if m() is replaced,
  // this compiled method will have to be deoptimized.
  bool is_evol_dependent();

  // Fast breakpoint support. Tells if this compiled method is
  // dependent on the given method. Returns true if this nmethod
  // corresponds to the given method as well.
  virtual bool is_dependent_on_method(Method* dependee);

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

  virtual void metadata_do(MetadataClosure* f);

  NativeCallWrapper* call_wrapper_at(address call) const;
  NativeCallWrapper* call_wrapper_before(address return_pc) const;
  address call_instruction_address(address pc) const;

  virtual CompiledStaticCall* compiledStaticCall_at(Relocation* call_site) const;
  virtual CompiledStaticCall* compiledStaticCall_at(address addr) const;
  virtual CompiledStaticCall* compiledStaticCall_before(address addr) const;
};

// Locks an nmethod so its code will not get removed and it will not
// be made into a zombie, even if it is a not_entrant method. After the
// nmethod becomes a zombie, if CompiledMethodUnload event processing
// needs to be done, then lock_nmethod() is used directly to keep the
// generated code from being reused too early.
class nmethodLocker : public StackObj {
  CompiledMethod* _nm;

 public:

  // note: nm can be NULL
  // Only JvmtiDeferredEvent::compiled_method_unload_event()
  // should pass zombie_ok == true.
  static void lock_nmethod(CompiledMethod* nm, bool zombie_ok = false);
  static void unlock_nmethod(CompiledMethod* nm); // (ditto)

  nmethodLocker(address pc); // derive nm from pc
  nmethodLocker(nmethod *nm) { _nm = nm; lock_nmethod(_nm); }
  nmethodLocker(CompiledMethod *nm) {
    _nm = nm;
    lock(_nm);
  }

  static void lock(CompiledMethod* method) {
    if (method == NULL) return;
    lock_nmethod(method);
  }

  static void unlock(CompiledMethod* method) {
    if (method == NULL) return;
    unlock_nmethod(method);
  }

  nmethodLocker() { _nm = NULL; }
  ~nmethodLocker() {
    unlock(_nm);
  }

  CompiledMethod* code() { return _nm; }
  void set_code(CompiledMethod* new_nm) {
    unlock(_nm);   // note:  This works even if _nm==new_nm.
    _nm = new_nm;
    lock(_nm);
  }
};

#endif // SHARE_CODE_NMETHOD_HPP
