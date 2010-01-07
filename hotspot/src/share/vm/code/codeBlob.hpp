/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

// CodeBlob - superclass for all entries in the CodeCache.
//
// Suptypes are:
//   nmethod            : Compiled Java methods (include method that calls to native code)
//   RuntimeStub        : Call to VM runtime methods
//   DeoptimizationBlob : Used for deoptimizatation
//   ExceptionBlob      : Used for stack unrolling
//   SafepointBlob      : Used to handle illegal instruction exceptions
//
//
// Layout:
//   - header
//   - relocation
//   - instruction space
//   - data space
class DeoptimizationBlob;

class CodeBlob VALUE_OBJ_CLASS_SPEC {

  friend class VMStructs;

 private:
  const char* _name;
  int        _size;                              // total size of CodeBlob in bytes
  int        _header_size;                       // size of header (depends on subclass)
  int        _relocation_size;                   // size of relocation
  int        _instructions_offset;               // offset to where instructions region begins
  int        _frame_complete_offset;             // instruction offsets in [0.._frame_complete_offset) have
                                                 // not finished setting up their frame. Beware of pc's in
                                                 // that range. There is a similar range(s) on returns
                                                 // which we don't detect.
  int        _data_offset;                       // offset to where data region begins
  int        _oops_offset;                       // offset to where embedded oop table begins (inside data)
  int        _oops_length;                       // number of embedded oops
  int        _frame_size;                        // size of stack frame
  OopMapSet* _oop_maps;                          // OopMap for this CodeBlob
  CodeComments _comments;

  friend class OopRecorder;

  void fix_oop_relocations(address begin, address end, bool initialize_immediates);
  inline void initialize_immediate_oop(oop* dest, jobject handle);

 public:
  // Returns the space needed for CodeBlob
  static unsigned int allocation_size(CodeBuffer* cb, int header_size);

  // Creation
  // a) simple CodeBlob
  // frame_complete is the offset from the beginning of the instructions
  // to where the frame setup (from stackwalk viewpoint) is complete.
  CodeBlob(const char* name, int header_size, int size, int frame_complete, int locs_size);

  // b) full CodeBlob
  CodeBlob(
    const char* name,
    CodeBuffer* cb,
    int         header_size,
    int         size,
    int         frame_complete,
    int         frame_size,
    OopMapSet*  oop_maps
  );

  // Deletion
  void flush();

  // Typing
  virtual bool is_buffer_blob() const            { return false; }
  virtual bool is_nmethod() const                { return false; }
  virtual bool is_runtime_stub() const           { return false; }
  virtual bool is_deoptimization_stub() const    { return false; }
  virtual bool is_uncommon_trap_stub() const     { return false; }
  virtual bool is_exception_stub() const         { return false; }
  virtual bool is_safepoint_stub() const         { return false; }
  virtual bool is_adapter_blob() const           { return false; }

  virtual bool is_compiled_by_c2() const         { return false; }
  virtual bool is_compiled_by_c1() const         { return false; }

  // Casting
  nmethod* as_nmethod_or_null()                  { return is_nmethod() ? (nmethod*) this : NULL; }

  // Boundaries
  address    header_begin() const                { return (address)    this; }
  address    header_end() const                  { return ((address)   this) + _header_size; };
  relocInfo* relocation_begin() const            { return (relocInfo*) header_end(); };
  relocInfo* relocation_end() const              { return (relocInfo*)(header_end()   + _relocation_size); }
  address    instructions_begin() const          { return (address)    header_begin() + _instructions_offset;  }
  address    instructions_end() const            { return (address)    header_begin() + _data_offset; }
  address    data_begin() const                  { return (address)    header_begin() + _data_offset; }
  address    data_end() const                    { return (address)    header_begin() + _size; }
  oop*       oops_begin() const                  { return (oop*)      (header_begin() + _oops_offset); }
  oop*       oops_end() const                    { return                oops_begin() + _oops_length; }

  // Offsets
  int relocation_offset() const                  { return _header_size; }
  int instructions_offset() const                { return _instructions_offset; }
  int data_offset() const                        { return _data_offset; }
  int oops_offset() const                        { return _oops_offset; }

  // Sizes
  int size() const                               { return _size; }
  int header_size() const                        { return _header_size; }
  int relocation_size() const                    { return (address) relocation_end() - (address) relocation_begin(); }
  int instructions_size() const                  { return instructions_end() - instructions_begin();  }
  int data_size() const                          { return data_end() - data_begin(); }
  int oops_size() const                          { return (address) oops_end() - (address) oops_begin(); }

  // Containment
  bool blob_contains(address addr) const         { return header_begin()       <= addr && addr < data_end(); }
  bool relocation_contains(relocInfo* addr) const{ return relocation_begin()   <= addr && addr < relocation_end(); }
  bool instructions_contains(address addr) const { return instructions_begin() <= addr && addr < instructions_end(); }
  bool data_contains(address addr) const         { return data_begin()         <= addr && addr < data_end(); }
  bool oops_contains(oop* addr) const            { return oops_begin()         <= addr && addr < oops_end(); }
  bool contains(address addr) const              { return instructions_contains(addr); }
  bool is_frame_complete_at(address addr) const  { return instructions_contains(addr) &&
                                                          addr >= instructions_begin() + _frame_complete_offset; }

  // Relocation support
  void fix_oop_relocations(address begin, address end) {
    fix_oop_relocations(begin, end, false);
  }
  void fix_oop_relocations() {
    fix_oop_relocations(NULL, NULL, false);
  }
  relocInfo::relocType reloc_type_for_address(address pc);
  bool is_at_poll_return(address pc);
  bool is_at_poll_or_poll_return(address pc);

  // Support for oops in scopes and relocs:
  // Note: index 0 is reserved for null.
  oop  oop_at(int index) const                   { return index == 0? (oop)NULL: *oop_addr_at(index); }
  oop* oop_addr_at(int index) const{             // for GC
    // relocation indexes are biased by 1 (because 0 is reserved)
    assert(index > 0 && index <= _oops_length, "must be a valid non-zero index");
    return &oops_begin()[index-1];
  }

  void copy_oops(GrowableArray<jobject>* oops);

  // CodeCache support: really only used by the nmethods, but in order to get
  // asserts and certain bookkeeping to work in the CodeCache they are defined
  // virtual here.
  virtual bool is_zombie() const                 { return false; }
  virtual bool is_locked_by_vm() const           { return false; }

  virtual bool is_unloaded() const               { return false; }
  virtual bool is_not_entrant() const            { return false; }

  // GC support
  virtual bool is_alive() const                  = 0;
  virtual void do_unloading(BoolObjectClosure* is_alive,
                            OopClosure* keep_alive,
                            bool unloading_occurred);
  virtual void oops_do(OopClosure* f) = 0;
  // (All CodeBlob subtypes other than NMethod currently have
  // an empty oops_do() method.

  // OopMap for frame
  OopMapSet* oop_maps() const                    { return _oop_maps; }
  void set_oop_maps(OopMapSet* p);
  OopMap* oop_map_for_return_address(address return_address);
  virtual void preserve_callee_argument_oops(frame fr, const RegisterMap* reg_map, OopClosure* f)  { ShouldNotReachHere(); }

  // Frame support
  int  frame_size() const                        { return _frame_size; }
  void set_frame_size(int size)                  { _frame_size = size; }

  // Returns true, if the next frame is responsible for GC'ing oops passed as arguments
  virtual bool caller_must_gc_arguments(JavaThread* thread) const { return false; }

  // Naming
  const char* name() const                       { return _name; }
  void set_name(const char* name)                { _name = name; }

  // Debugging
  virtual void verify();
  virtual void print() const                     PRODUCT_RETURN;
  virtual void print_value_on(outputStream* st) const PRODUCT_RETURN;

  // Print the comment associated with offset on stream, if there is one
  void print_block_comment(outputStream* stream, intptr_t offset) {
    _comments.print_block_comment(stream, offset);
  }

  // Transfer ownership of comments to this CodeBlob
  void set_comments(CodeComments& comments) {
    _comments.assign(comments);
  }
};


//----------------------------------------------------------------------------------------------------
// BufferBlob: used to hold non-relocatable machine code such as the interpreter, stubroutines, etc.

class BufferBlob: public CodeBlob {
  friend class VMStructs;
 private:
  // Creation support
  BufferBlob(const char* name, int size);
  BufferBlob(const char* name, int size, CodeBuffer* cb);

  void* operator new(size_t s, unsigned size);

 public:
  // Creation
  static BufferBlob* create(const char* name, int buffer_size);
  static BufferBlob* create(const char* name, CodeBuffer* cb);

  static void free(BufferBlob* buf);

  // Typing
  bool is_buffer_blob() const                    { return true; }
  bool is_adapter_blob() const;

  // GC/Verification support
  void preserve_callee_argument_oops(frame fr, const RegisterMap* reg_map, OopClosure* f)  { /* nothing to do */ }
  bool is_alive() const                          { return true; }
  void do_unloading(BoolObjectClosure* is_alive,
                    OopClosure* keep_alive,
                    bool unloading_occurred)     { /* do nothing */ }

  void oops_do(OopClosure* f)                    { /* do nothing*/ }

  void verify();
  void print() const                             PRODUCT_RETURN;
  void print_value_on(outputStream* st) const    PRODUCT_RETURN;
};


//----------------------------------------------------------------------------------------------------
// RuntimeStub: describes stubs used by compiled code to call a (static) C++ runtime routine

class RuntimeStub: public CodeBlob {
  friend class VMStructs;
 private:
  bool        _caller_must_gc_arguments;

  // Creation support
  RuntimeStub(
    const char* name,
    CodeBuffer* cb,
    int         size,
    int         frame_complete,
    int         frame_size,
    OopMapSet*  oop_maps,
    bool        caller_must_gc_arguments
  );

  void* operator new(size_t s, unsigned size);

 public:
  // Creation
  static RuntimeStub* new_runtime_stub(
    const char* stub_name,
    CodeBuffer* cb,
    int         frame_complete,
    int         frame_size,
    OopMapSet*  oop_maps,
    bool        caller_must_gc_arguments
  );

  // Typing
  bool is_runtime_stub() const                   { return true; }

  // GC support
  bool caller_must_gc_arguments(JavaThread* thread) const { return _caller_must_gc_arguments; }

  address entry_point()                          { return instructions_begin(); }

  // GC/Verification support
  void preserve_callee_argument_oops(frame fr, const RegisterMap *reg_map, OopClosure* f)  { /* nothing to do */ }
  bool is_alive() const                          { return true; }
  void do_unloading(BoolObjectClosure* is_alive,
                    OopClosure* keep_alive,
                    bool unloading_occurred)     { /* do nothing */ }
  void oops_do(OopClosure* f) { /* do-nothing*/ }

  void verify();
  void print() const                             PRODUCT_RETURN;
  void print_value_on(outputStream* st) const    PRODUCT_RETURN;
};


//----------------------------------------------------------------------------------------------------
// Super-class for all blobs that exist in only one instance. Implements default behaviour.

class SingletonBlob: public CodeBlob {
  friend class VMStructs;
  public:
   SingletonBlob(
     const char* name,
     CodeBuffer* cb,
     int         header_size,
     int         size,
     int         frame_size,
     OopMapSet*  oop_maps
   )
   : CodeBlob(name, cb, header_size, size, CodeOffsets::frame_never_safe, frame_size, oop_maps)
   {};

   bool is_alive() const                         { return true; }
   void do_unloading(BoolObjectClosure* is_alive,
                     OopClosure* keep_alive,
                     bool unloading_occurred)    { /* do-nothing*/ }

   void verify(); // does nothing
   void print() const                            PRODUCT_RETURN;
   void print_value_on(outputStream* st) const   PRODUCT_RETURN;
};


//----------------------------------------------------------------------------------------------------
// DeoptimizationBlob

class DeoptimizationBlob: public SingletonBlob {
  friend class VMStructs;
 private:
  int _unpack_offset;
  int _unpack_with_exception;
  int _unpack_with_reexecution;

  int _unpack_with_exception_in_tls;

  // Creation support
  DeoptimizationBlob(
    CodeBuffer* cb,
    int         size,
    OopMapSet*  oop_maps,
    int         unpack_offset,
    int         unpack_with_exception_offset,
    int         unpack_with_reexecution_offset,
    int         frame_size
  );

  void* operator new(size_t s, unsigned size);

 public:
  // Creation
  static DeoptimizationBlob* create(
    CodeBuffer* cb,
    OopMapSet*  oop_maps,
    int         unpack_offset,
    int         unpack_with_exception_offset,
    int         unpack_with_reexecution_offset,
    int         frame_size
  );

  // Typing
  bool is_deoptimization_stub() const { return true; }
  const DeoptimizationBlob *as_deoptimization_stub() const { return this; }
  bool exception_address_is_unpack_entry(address pc) const {
    address unpack_pc = unpack();
    return (pc == unpack_pc || (pc + frame::pc_return_offset) == unpack_pc);
  }




  // GC for args
  void preserve_callee_argument_oops(frame fr, const RegisterMap *reg_map, OopClosure* f) { /* Nothing to do */ }

  // Iteration
  void oops_do(OopClosure* f) {}

  // Printing
  void print_value_on(outputStream* st) const PRODUCT_RETURN;

  address unpack() const                         { return instructions_begin() + _unpack_offset;           }
  address unpack_with_exception() const          { return instructions_begin() + _unpack_with_exception;   }
  address unpack_with_reexecution() const        { return instructions_begin() + _unpack_with_reexecution; }

  // Alternate entry point for C1 where the exception and issuing pc
  // are in JavaThread::_exception_oop and JavaThread::_exception_pc
  // instead of being in registers.  This is needed because C1 doesn't
  // model exception paths in a way that keeps these registers free so
  // there may be live values in those registers during deopt.
  void set_unpack_with_exception_in_tls_offset(int offset) {
    _unpack_with_exception_in_tls = offset;
    assert(contains(instructions_begin() + _unpack_with_exception_in_tls), "must be PC inside codeblob");
  }
  address unpack_with_exception_in_tls() const   { return instructions_begin() + _unpack_with_exception_in_tls;   }
};


//----------------------------------------------------------------------------------------------------
// UncommonTrapBlob (currently only used by Compiler 2)

#ifdef COMPILER2

class UncommonTrapBlob: public SingletonBlob {
  friend class VMStructs;
 private:
  // Creation support
  UncommonTrapBlob(
    CodeBuffer* cb,
    int         size,
    OopMapSet*  oop_maps,
    int         frame_size
  );

  void* operator new(size_t s, unsigned size);

 public:
  // Creation
  static UncommonTrapBlob* create(
    CodeBuffer* cb,
    OopMapSet*  oop_maps,
    int         frame_size
  );

  // GC for args
  void preserve_callee_argument_oops(frame fr, const RegisterMap *reg_map, OopClosure* f)  { /* nothing to do */ }

  // Typing
  bool is_uncommon_trap_stub() const             { return true; }

  // Iteration
  void oops_do(OopClosure* f) {}
};


//----------------------------------------------------------------------------------------------------
// ExceptionBlob: used for exception unwinding in compiled code (currently only used by Compiler 2)

class ExceptionBlob: public SingletonBlob {
  friend class VMStructs;
 private:
  // Creation support
  ExceptionBlob(
    CodeBuffer* cb,
    int         size,
    OopMapSet*  oop_maps,
    int         frame_size
  );

  void* operator new(size_t s, unsigned size);

 public:
  // Creation
  static ExceptionBlob* create(
    CodeBuffer* cb,
    OopMapSet*  oop_maps,
    int         frame_size
  );

  // GC for args
  void preserve_callee_argument_oops(frame fr, const RegisterMap* reg_map, OopClosure* f)  { /* nothing to do */ }

  // Typing
  bool is_exception_stub() const                 { return true; }

  // Iteration
  void oops_do(OopClosure* f) {}
};
#endif // COMPILER2


//----------------------------------------------------------------------------------------------------
// SafepointBlob: handles illegal_instruction exceptions during a safepoint

class SafepointBlob: public SingletonBlob {
  friend class VMStructs;
 private:
  // Creation support
  SafepointBlob(
    CodeBuffer* cb,
    int         size,
    OopMapSet*  oop_maps,
    int         frame_size
  );

  void* operator new(size_t s, unsigned size);

 public:
  // Creation
  static SafepointBlob* create(
    CodeBuffer* cb,
    OopMapSet*  oop_maps,
    int         frame_size
  );

  // GC for args
  void preserve_callee_argument_oops(frame fr, const RegisterMap* reg_map, OopClosure* f)  { /* nothing to do */ }

  // Typing
  bool is_safepoint_stub() const                 { return true; }

  // Iteration
  void oops_do(OopClosure* f) {}
};
