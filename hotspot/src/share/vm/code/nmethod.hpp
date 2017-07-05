/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

// This class is used internally by nmethods, to cache
// exception/pc/handler information.

class ExceptionCache : public CHeapObj {
  friend class VMStructs;
 private:
  static address _unwind_handler;
  enum { cache_size = 16 };
  klassOop _exception_type;
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

  klassOop  exception_type()                { return _exception_type; }
  klassOop* exception_type_addr()           { return &_exception_type; }
  ExceptionCache* next()                    { return _next; }
  void      set_next(ExceptionCache *ec)    { _next = ec; }

  address match(Handle exception, address pc);
  bool    match_exception_with_space(Handle exception) ;
  address test_address(address addr);
  bool    add_address_and_handler(address addr, address handler) ;

  static address unwind_handler() { return _unwind_handler; }
};


// cache pc descs found in earlier inquiries
class PcDescCache VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
 private:
  enum { cache_size = 4 };
  PcDesc* _last_pc_desc;         // most recent pc_desc found
  PcDesc* _pc_descs[cache_size]; // last cache_size pc_descs found
 public:
  PcDescCache() { debug_only(_last_pc_desc = NULL); }
  void    reset_to(PcDesc* initial_pc_desc);
  PcDesc* find_pc_desc(int pc_offset, bool approximate);
  void    add_pc_desc(PcDesc* pc_desc);
  PcDesc* last_pc_desc() { return _last_pc_desc; }
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

class Dependencies;
class ExceptionHandlerTable;
class ImplicitExceptionTable;
class AbstractCompiler;
class xmlStream;

class nmethod : public CodeBlob {
  friend class VMStructs;
  friend class NMethodSweeper;
  friend class CodeCache;  // non-perm oops
 private:
  // Shared fields for all nmethod's
  methodOop _method;
  int       _entry_bci;        // != InvocationEntryBci if this nmethod is an on-stack replacement method
  jmethodID _jmethod_id;       // Cache of method()->jmethod_id()

  // To support simple linked-list chaining of nmethods:
  nmethod*  _osr_link;         // from instanceKlass::osr_nmethods_head
  nmethod*  _scavenge_root_link; // from CodeCache::scavenge_root_nmethods
  nmethod*  _saved_nmethod_link; // from CodeCache::speculatively_disconnect

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

#ifdef HAVE_DTRACE_H
  int _trap_offset;
#endif // def HAVE_DTRACE_H
  int _stub_offset;
  int _consts_offset;
  int _oops_offset;                       // offset to where embedded oop table begins (inside data)
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
  bool _speculatively_disconnected;          // Marked for potential unload

  bool _marked_for_reclamation;              // Used by NMethodSweeper (set only by sweeper)
  bool _marked_for_deoptimization;           // Used for stack deoptimization

  // used by jvmti to track if an unload event has been posted for this nmethod.
  bool _unload_reported;

  // set during construction
  unsigned int _has_unsafe_access:1;         // May fault due to unsafe access.
  unsigned int _has_method_handle_invokes:1; // Has this method MethodHandle invokes?

  // Protected by Patching_lock
  unsigned char _state;                      // {alive, not_entrant, zombie, unloaded)

  enum { alive        = 0,
         not_entrant  = 1, // uncommon trap has happened but activations may still exist
         zombie       = 2,
         unloaded     = 3 };


  jbyte _scavenge_root_state;

  NOT_PRODUCT(bool _has_debug_info; )

  // Nmethod Flushing lock (if non-zero, then the nmethod is not removed)
  jint  _lock_count;

  // not_entrant method removal. Each mark_sweep pass will update
  // this mark to current sweep invocation count if it is seen on the
  // stack.  An not_entrant method can be removed when there is no
  // more activations, i.e., when the _stack_traversal_mark is less than
  // current sweep traversal index.
  long _stack_traversal_mark;

  ExceptionCache *_exception_cache;
  PcDescCache     _pc_desc_cache;

  // These are only used for compiled synchronized native methods to
  // locate the owner and stack slot for the BasicLock so that we can
  // properly revoke the bias of the owner if necessary. They are
  // needed because there is no debug information for compiled native
  // wrappers and the oop maps are insufficient to allow
  // frame::retrieve_receiver() to work. Currently they are expected
  // to be byte offsets from the Java stack pointer for maximum code
  // sharing between platforms. Note that currently biased locking
  // will never cause Class instances to be biased but this code
  // handles the static synchronized case as well.
  ByteSize _compiled_synchronized_native_basic_lock_owner_sp_offset;
  ByteSize _compiled_synchronized_native_basic_lock_sp_offset;

  friend class nmethodLocker;

  // For native wrappers
  nmethod(methodOop method,
          int nmethod_size,
          CodeOffsets* offsets,
          CodeBuffer *code_buffer,
          int frame_size,
          ByteSize basic_lock_owner_sp_offset, /* synchronized natives only */
          ByteSize basic_lock_sp_offset,       /* synchronized natives only */
          OopMapSet* oop_maps);

#ifdef HAVE_DTRACE_H
  // For native wrappers
  nmethod(methodOop method,
          int nmethod_size,
          CodeOffsets* offsets,
          CodeBuffer *code_buffer,
          int frame_size);
#endif // def HAVE_DTRACE_H

  // Creation support
  nmethod(methodOop method,
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
          int comp_level);

  // helper methods
  void* operator new(size_t size, int nmethod_size);

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
  static nmethod* new_nmethod(methodHandle method,
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
                              int comp_level);

  static nmethod* new_native_nmethod(methodHandle method,
                                     CodeBuffer *code_buffer,
                                     int vep_offset,
                                     int frame_complete,
                                     int frame_size,
                                     ByteSize receiver_sp_offset,
                                     ByteSize basic_lock_sp_offset,
                                     OopMapSet* oop_maps);

#ifdef HAVE_DTRACE_H
  // The method we generate for a dtrace probe has to look
  // like an nmethod as far as the rest of the system is concerned
  // which is somewhat unfortunate.
  static nmethod* new_dtrace_nmethod(methodHandle method,
                                     CodeBuffer *code_buffer,
                                     int vep_offset,
                                     int trap_offset,
                                     int frame_complete,
                                     int frame_size);

  int trap_offset() const      { return _trap_offset; }
  address trap_address() const { return code_begin() + _trap_offset; }

#endif // def HAVE_DTRACE_H

  // accessors
  methodOop method() const                        { return _method; }
  AbstractCompiler* compiler() const              { return _compiler; }

#ifndef PRODUCT
  bool has_debug_info() const                     { return _has_debug_info; }
  void set_has_debug_info(bool f)                 { _has_debug_info = false; }
#endif // NOT PRODUCT

  // type info
  bool is_nmethod() const                         { return true; }
  bool is_java_method() const                     { return !method()->is_native(); }
  bool is_native_method() const                   { return method()->is_native(); }
  bool is_osr_method() const                      { return _entry_bci != InvocationEntryBci; }

  bool is_compiled_by_c1() const;
  bool is_compiled_by_c2() const;
  bool is_compiled_by_shark() const;

  // boundaries for different parts
  address code_begin            () const          { return _entry_point; }
  address code_end              () const          { return           header_begin() + _stub_offset          ; }
  address exception_begin       () const          { return           header_begin() + _exception_offset     ; }
  address deopt_handler_begin   () const          { return           header_begin() + _deoptimize_offset    ; }
  address deopt_mh_handler_begin() const          { return           header_begin() + _deoptimize_mh_offset ; }
  address unwind_handler_begin  () const          { return _unwind_handler_offset != -1 ? (header_begin() + _unwind_handler_offset) : NULL; }
  address stub_begin            () const          { return           header_begin() + _stub_offset          ; }
  address stub_end              () const          { return           header_begin() + _consts_offset        ; }
  address consts_begin          () const          { return           header_begin() + _consts_offset        ; }
  address consts_end            () const          { return           header_begin() + _oops_offset          ; }
  oop*    oops_begin            () const          { return (oop*)   (header_begin() + _oops_offset)         ; }
  oop*    oops_end              () const          { return (oop*)   (header_begin() + _scopes_data_offset)  ; }

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
  int code_size         () const                  { return            code_end         () -            code_begin         (); }
  int stub_size         () const                  { return            stub_end         () -            stub_begin         (); }
  int consts_size       () const                  { return            consts_end       () -            consts_begin       (); }
  int oops_size         () const                  { return (address)  oops_end         () - (address)  oops_begin         (); }
  int scopes_data_size  () const                  { return            scopes_data_end  () -            scopes_data_begin  (); }
  int scopes_pcs_size   () const                  { return (intptr_t) scopes_pcs_end   () - (intptr_t) scopes_pcs_begin   (); }
  int dependencies_size () const                  { return            dependencies_end () -            dependencies_begin (); }
  int handler_table_size() const                  { return            handler_table_end() -            handler_table_begin(); }
  int nul_chk_table_size() const                  { return            nul_chk_table_end() -            nul_chk_table_begin(); }

  int total_size        () const;

  // Containment
  bool code_contains         (address addr) const { return code_begin         () <= addr && addr < code_end         (); }
  bool stub_contains         (address addr) const { return stub_begin         () <= addr && addr < stub_end         (); }
  bool consts_contains       (address addr) const { return consts_begin       () <= addr && addr < consts_end       (); }
  bool oops_contains         (oop*    addr) const { return oops_begin         () <= addr && addr < oops_end         (); }
  bool scopes_data_contains  (address addr) const { return scopes_data_begin  () <= addr && addr < scopes_data_end  (); }
  bool scopes_pcs_contains   (PcDesc* addr) const { return scopes_pcs_begin   () <= addr && addr < scopes_pcs_end   (); }
  bool handler_table_contains(address addr) const { return handler_table_begin() <= addr && addr < handler_table_end(); }
  bool nul_chk_table_contains(address addr) const { return nul_chk_table_begin() <= addr && addr < nul_chk_table_end(); }

  // entry points
  address entry_point() const                     { return _entry_point;             } // normal entry point
  address verified_entry_point() const            { return _verified_entry_point;    } // if klass is correct

  // flag accessing and manipulation
  bool  is_in_use() const                         { return _state == alive; }
  bool  is_alive() const                          { return _state == alive || _state == not_entrant; }
  bool  is_not_entrant() const                    { return _state == not_entrant; }
  bool  is_zombie() const                         { return _state == zombie; }
  bool  is_unloaded() const                       { return _state == unloaded;   }

  // Make the nmethod non entrant. The nmethod will continue to be
  // alive.  It is used when an uncommon trap happens.  Returns true
  // if this thread changed the state of the nmethod or false if
  // another thread performed the transition.
  bool  make_not_entrant()                        { return make_not_entrant_or_zombie(not_entrant); }
  bool  make_zombie()                             { return make_not_entrant_or_zombie(zombie); }

  // used by jvmti to track if the unload event has been reported
  bool  unload_reported()                         { return _unload_reported; }
  void  set_unload_reported()                     { _unload_reported = true; }

  bool  is_marked_for_deoptimization() const      { return _marked_for_deoptimization; }
  void  mark_for_deoptimization()                 { _marked_for_deoptimization = true; }

  void  make_unloaded(BoolObjectClosure* is_alive, oop cause);

  bool has_dependencies()                         { return dependencies_size() != 0; }
  void flush_dependencies(BoolObjectClosure* is_alive);
  bool has_flushed_dependencies()                 { return _has_flushed_dependencies; }
  void set_has_flushed_dependencies()             {
    assert(!has_flushed_dependencies(), "should only happen once");
    _has_flushed_dependencies = 1;
  }

  bool  is_marked_for_reclamation() const         { return _marked_for_reclamation; }
  void  mark_for_reclamation()                    { _marked_for_reclamation = 1; }

  bool  has_unsafe_access() const                 { return _has_unsafe_access; }
  void  set_has_unsafe_access(bool z)             { _has_unsafe_access = z; }

  bool  has_method_handle_invokes() const         { return _has_method_handle_invokes; }
  void  set_has_method_handle_invokes(bool z)     { _has_method_handle_invokes = z; }

  bool  is_speculatively_disconnected() const     { return _speculatively_disconnected; }
  void  set_speculatively_disconnected(bool z)     { _speculatively_disconnected = z; }

  int   comp_level() const                        { return _comp_level; }

  // Support for oops in scopes and relocs:
  // Note: index 0 is reserved for null.
  oop   oop_at(int index) const                   { return index == 0 ? (oop) NULL: *oop_addr_at(index); }
  oop*  oop_addr_at(int index) const {  // for GC
    // relocation indexes are biased by 1 (because 0 is reserved)
    assert(index > 0 && index <= oops_size(), "must be a valid non-zero index");
    return &oops_begin()[index - 1];
  }

  void copy_oops(GrowableArray<jobject>* oops);

  // Relocation support
private:
  void fix_oop_relocations(address begin, address end, bool initialize_immediates);
  inline void initialize_immediate_oop(oop* dest, jobject handle);

public:
  void fix_oop_relocations(address begin, address end) { fix_oop_relocations(begin, end, false); }
  void fix_oop_relocations()                           { fix_oop_relocations(NULL, NULL, false); }

  bool is_at_poll_return(address pc);
  bool is_at_poll_or_poll_return(address pc);

  // Non-perm oop support
  bool  on_scavenge_root_list() const                  { return (_scavenge_root_state & 1) != 0; }
 protected:
  enum { npl_on_list = 0x01, npl_marked = 0x10 };
  void  set_on_scavenge_root_list()                    { _scavenge_root_state = npl_on_list; }
  void  clear_on_scavenge_root_list()                  { _scavenge_root_state = 0; }
  // assertion-checking and pruning logic uses the bits of _scavenge_root_state
#ifndef PRODUCT
  void  set_scavenge_root_marked()                     { _scavenge_root_state |= npl_marked; }
  void  clear_scavenge_root_marked()                   { _scavenge_root_state &= ~npl_marked; }
  bool  scavenge_root_not_marked()                     { return (_scavenge_root_state &~ npl_on_list) == 0; }
  // N.B. there is no positive marked query, and we only use the not_marked query for asserts.
#endif //PRODUCT
  nmethod* scavenge_root_link() const                  { return _scavenge_root_link; }
  void     set_scavenge_root_link(nmethod *n)          { _scavenge_root_link = n; }

  nmethod* saved_nmethod_link() const                  { return _saved_nmethod_link; }
  void     set_saved_nmethod_link(nmethod *n)          { _saved_nmethod_link = n; }

 public:

  // Sweeper support
  long  stack_traversal_mark()                    { return _stack_traversal_mark; }
  void  set_stack_traversal_mark(long l)          { _stack_traversal_mark = l; }

  // Exception cache support
  ExceptionCache* exception_cache() const         { return _exception_cache; }
  void set_exception_cache(ExceptionCache *ec)    { _exception_cache = ec; }
  address handler_for_exception_and_pc(Handle exception, address pc);
  void add_handler_for_exception_and_pc(Handle exception, address pc, address handler);
  void remove_from_exception_cache(ExceptionCache* ec);

  // implicit exceptions support
  address continuation_for_implicit_exception(address pc);

  // On-stack replacement support
  int   osr_entry_bci() const                     { assert(_entry_bci != InvocationEntryBci, "wrong kind of nmethod"); return _entry_bci; }
  address  osr_entry() const                      { assert(_entry_bci != InvocationEntryBci, "wrong kind of nmethod"); return _osr_entry_point; }
  void  invalidate_osr_method();
  nmethod* osr_link() const                       { return _osr_link; }
  void     set_osr_link(nmethod *n)               { _osr_link = n; }

  // tells whether frames described by this nmethod can be deoptimized
  // note: native wrappers cannot be deoptimized.
  bool can_be_deoptimized() const { return is_java_method(); }

  // Inline cache support
  void clear_inline_caches();
  void cleanup_inline_caches();
  bool inlinecache_check_contains(address addr) const {
    return (addr >= instructions_begin() && addr < verified_entry_point());
  }

  // unlink and deallocate this nmethod
  // Only NMethodSweeper class is expected to use this. NMethodSweeper is not
  // expected to use any other private methods/data in this class.

 protected:
  void flush();

 public:
  // If returning true, it is unsafe to remove this nmethod even though it is a zombie
  // nmethod, since the VM might have a reference to it. Should only be called from a  safepoint.
  bool is_locked_by_vm() const                    { return _lock_count >0; }

  // See comment at definition of _last_seen_on_stack
  void mark_as_seen_on_stack();
  bool can_not_entrant_be_converted();

  // Evolution support. We make old (discarded) compiled methods point to new methodOops.
  void set_method(methodOop method) { _method = method; }

  // GC support
  void do_unloading(BoolObjectClosure* is_alive, OopClosure* keep_alive,
                    bool unloading_occurred);
  bool can_unload(BoolObjectClosure* is_alive, OopClosure* keep_alive,
                  oop* root, bool unloading_occurred);

  void preserve_callee_argument_oops(frame fr, const RegisterMap *reg_map,
                                     OopClosure* f);
  void oops_do(OopClosure* f) { oops_do(f, false); }
  void oops_do(OopClosure* f, bool do_strong_roots_only);
  bool detect_scavenge_root_oops();
  void verify_scavenge_root_oops() PRODUCT_RETURN;

  bool test_set_oops_do_mark();
  static void oops_do_marking_prologue();
  static void oops_do_marking_epilogue();
  static bool oops_do_marking_is_active() { return _oops_do_mark_nmethods != NULL; }
  DEBUG_ONLY(bool test_oops_do_mark() { return _oops_do_mark_link != NULL; })

  // ScopeDesc for an instruction
  ScopeDesc* scope_desc_at(address pc);

 private:
  ScopeDesc* scope_desc_in(address begin, address end);

  address* orig_pc_addr(const frame* fr) { return (address*) ((address)fr->unextended_sp() + _orig_pc_offset); }

  PcDesc* find_pc_desc_internal(address pc, bool approximate);

  PcDesc* find_pc_desc(address pc, bool approximate) {
    PcDesc* desc = _pc_desc_cache.last_pc_desc();
    if (desc != NULL && desc->pc_offset() == pc - instructions_begin()) {
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
  bool is_deopt_entry   (address pc) { return pc == deopt_handler_begin(); }
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
  void print_code();
  void print_relocations()                        PRODUCT_RETURN;
  void print_pcs()                                PRODUCT_RETURN;
  void print_scopes()                             PRODUCT_RETURN;
  void print_dependencies()                       PRODUCT_RETURN;
  void print_value_on(outputStream* st) const     PRODUCT_RETURN;
  void print_calls(outputStream* st)              PRODUCT_RETURN;
  void print_handler_table()                      PRODUCT_RETURN;
  void print_nul_chk_table()                      PRODUCT_RETURN;
  void print_nmethod(bool print_code);

  // need to re-define this from CodeBlob else the overload hides it
  virtual void print_on(outputStream* st) const { CodeBlob::print_on(st); }
  void print_on(outputStream* st, const char* title) const;

  // Logging
  void log_identity(xmlStream* log) const;
  void log_new_nmethod() const;
  void log_state_change() const;

  // Prints block-level comments, including nmethod specific block labels:
  virtual void print_block_comment(outputStream* stream, address block_begin) {
    print_nmethod_labels(stream, block_begin);
    CodeBlob::print_block_comment(stream, block_begin);
  }
  void print_nmethod_labels(outputStream* stream, address block_begin);

  // Prints a comment for one native instruction (reloc info, pc desc)
  void print_code_comment_on(outputStream* st, int column, address begin, address end);
  static void print_statistics()                  PRODUCT_RETURN;

  // Compiler task identification.  Note that all OSR methods
  // are numbered in an independent sequence if CICountOSR is true,
  // and native method wrappers are also numbered independently if
  // CICountNative is true.
  int  compile_id() const                         { return _compile_id; }
  const char* compile_kind() const;

  // For debugging
  // CompiledIC*    IC_at(char* p) const;
  // PrimitiveIC*   primitiveIC_at(char* p) const;
  oop embeddedOop_at(address p);

  // tells if any of this method's dependencies have been invalidated
  // (this is expensive!)
  bool check_all_dependencies();

  // tells if this compiled method is dependent on the given changes,
  // and the changes have invalidated it
  bool check_dependency_on(DepChange& changes);

  // Evolution support. Tells if this compiled method is dependent on any of
  // methods m() of class dependee, such that if m() in dependee is replaced,
  // this compiled method will have to be deoptimized.
  bool is_evol_dependent_on(klassOop dependee);

  // Fast breakpoint support. Tells if this compiled method is
  // dependent on the given method. Returns true if this nmethod
  // corresponds to the given method as well.
  bool is_dependent_on_method(methodOop dependee);

  // is it ok to patch at address?
  bool is_patchable_at(address instr_address);

  // UseBiasedLocking support
  ByteSize compiled_synchronized_native_basic_lock_owner_sp_offset() {
    return _compiled_synchronized_native_basic_lock_owner_sp_offset;
  }
  ByteSize compiled_synchronized_native_basic_lock_sp_offset() {
    return _compiled_synchronized_native_basic_lock_sp_offset;
  }

  // support for code generation
  static int verified_entry_point_offset()        { return offset_of(nmethod, _verified_entry_point); }
  static int osr_entry_point_offset()             { return offset_of(nmethod, _osr_entry_point); }
  static int entry_bci_offset()                   { return offset_of(nmethod, _entry_bci); }

};

// Locks an nmethod so its code will not get removed, even if it is a zombie/not_entrant method
class nmethodLocker : public StackObj {
  nmethod* _nm;

  static void lock_nmethod(nmethod* nm);   // note: nm can be NULL
  static void unlock_nmethod(nmethod* nm); // (ditto)

 public:
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
