/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CODE_CODEBLOB_HPP
#define SHARE_CODE_CODEBLOB_HPP

#include "asm/codeBuffer.hpp"
#include "compiler/compilerDefinitions.hpp"
#include "compiler/oopMap.hpp"
#include "runtime/javaFrameAnchor.hpp"
#include "runtime/frame.hpp"
#include "runtime/handles.hpp"
#include "utilities/align.hpp"
#include "utilities/macros.hpp"

class ImmutableOopMap;
class ImmutableOopMapSet;
class JNIHandleBlock;
class OopMapSet;

// CodeBlob Types
// Used in the CodeCache to assign CodeBlobs to different CodeHeaps
enum class CodeBlobType {
  MethodNonProfiled   = 0,    // Execution level 1 and 4 (non-profiled) nmethods (including native nmethods)
  MethodProfiled      = 1,    // Execution level 2 and 3 (profiled) nmethods
  NonNMethod          = 2,    // Non-nmethods like Buffers, Adapters and Runtime Stubs
  All                 = 3,    // All types (No code cache segmentation)
  NumTypes            = 4     // Number of CodeBlobTypes
};

// CodeBlob - superclass for all entries in the CodeCache.
//
// Subtypes are:
//  nmethod              : JIT Compiled Java methods
//  RuntimeBlob          : Non-compiled method code; generated glue code
//   BufferBlob          : Used for non-relocatable code such as interpreter, stubroutines, etc.
//    AdapterBlob        : Used to hold C2I/I2C adapters
//    VtableBlob         : Used for holding vtable chunks
//    MethodHandlesAdapterBlob : Used to hold MethodHandles adapters
//   RuntimeStub         : Call to VM runtime methods
//   SingletonBlob       : Super-class for all blobs that exist in only one instance
//    DeoptimizationBlob : Used for deoptimization
//    ExceptionBlob      : Used for stack unrolling
//    SafepointBlob      : Used to handle illegal instruction exceptions
//    UncommonTrapBlob   : Used to handle uncommon traps
//   UpcallStub  : Used for upcalls from native code
//
//
// Layout : continuous in the CodeCache
//   - header
//   - relocation
//   - content space
//     - instruction space
//   - data space

enum class CodeBlobKind : u1 {
  None,
  Nmethod,
  Buffer,
  Adapter,
  Vtable,
  MH_Adapter,
  Runtime_Stub,
  Deoptimization,
  Exception,
  Safepoint,
  Uncommon_Trap,
  Upcall,
  Number_Of_Kinds
};

class UpcallStub;      // for as_upcall_stub()
class RuntimeStub;     // for as_runtime_stub()
class JavaFrameAnchor; // for UpcallStub::jfa_for_frame

class CodeBlob {
  friend class VMStructs;
  friend class JVMCIVMStructs;
  friend class CodeCacheDumper;

protected:
  // order fields from large to small to minimize padding between fields
  ImmutableOopMapSet* _oop_maps;   // OopMap for this CodeBlob
  const char*         _name;

  int      _size;                  // total size of CodeBlob in bytes
  int      _relocation_size;       // size of relocation (could be bigger than 64Kb)
  int      _content_offset;        // offset to where content region begins (this includes consts, insts, stubs)
  int      _code_offset;           // offset to where instructions region begins (this includes insts, stubs)

  int      _data_offset;           // offset to where data region begins
  int      _frame_size;            // size of stack frame in words (NOT slots. On x64 these are 64bit words)

  S390_ONLY(int _ctable_offset;)

  uint16_t _header_size;           // size of header (depends on subclass)
  int16_t  _frame_complete_offset; // instruction offsets in [0.._frame_complete_offset) have
                                   // not finished setting up their frame. Beware of pc's in
                                   // that range. There is a similar range(s) on returns
                                   // which we don't detect.

  CodeBlobKind _kind;              // Kind of this code blob

  bool _caller_must_gc_arguments;

#ifndef PRODUCT
  AsmRemarks _asm_remarks;
  DbgStrings _dbg_strings;
#endif

  CodeBlob(const char* name, CodeBlobKind kind, CodeBuffer* cb, int size, uint16_t header_size,
           int16_t frame_complete_offset, int frame_size, OopMapSet* oop_maps, bool caller_must_gc_arguments);

  // Simple CodeBlob used for simple BufferBlob.
  CodeBlob(const char* name, CodeBlobKind kind, int size, uint16_t header_size);

  void operator delete(void* p) { }

public:

  virtual ~CodeBlob() {
    assert(_oop_maps == nullptr, "Not flushed");
  }

  // Returns the space needed for CodeBlob
  static unsigned int allocation_size(CodeBuffer* cb, int header_size);
  static unsigned int align_code_offset(int offset);

  // Deletion
  void purge();

  // Typing
  bool is_nmethod() const                     { return _kind == CodeBlobKind::Nmethod; }
  bool is_buffer_blob() const                 { return _kind == CodeBlobKind::Buffer; }
  bool is_runtime_stub() const                { return _kind == CodeBlobKind::Runtime_Stub; }
  bool is_deoptimization_stub() const         { return _kind == CodeBlobKind::Deoptimization; }
  bool is_uncommon_trap_stub() const          { return _kind == CodeBlobKind::Uncommon_Trap; }
  bool is_exception_stub() const              { return _kind == CodeBlobKind::Exception; }
  bool is_safepoint_stub() const              { return _kind == CodeBlobKind::Safepoint; }
  bool is_adapter_blob() const                { return _kind == CodeBlobKind::Adapter; }
  bool is_vtable_blob() const                 { return _kind == CodeBlobKind::Vtable; }
  bool is_method_handles_adapter_blob() const { return _kind == CodeBlobKind::MH_Adapter; }
  bool is_upcall_stub() const                 { return _kind == CodeBlobKind::Upcall; }

  // Casting
  nmethod* as_nmethod_or_null()               { return is_nmethod() ? (nmethod*) this : nullptr; }
  nmethod* as_nmethod()                       { assert(is_nmethod(), "must be nmethod"); return (nmethod*) this; }
  CodeBlob* as_codeblob_or_null() const       { return (CodeBlob*) this; }
  UpcallStub* as_upcall_stub() const          { assert(is_upcall_stub(), "must be upcall stub"); return (UpcallStub*) this; }
  RuntimeStub* as_runtime_stub() const        { assert(is_runtime_stub(), "must be runtime blob"); return (RuntimeStub*) this; }

  // Boundaries
  address    header_begin() const             { return (address)    this; }
  address    header_end() const               { return ((address)   this) + _header_size; }
  relocInfo* relocation_begin() const         { return (relocInfo*) header_end(); }
  relocInfo* relocation_end() const           { return (relocInfo*)(header_end()   + _relocation_size); }
  address    content_begin() const            { return (address)    header_begin() + _content_offset; }
  address    content_end() const              { return (address)    header_begin() + _data_offset; }
  address    code_begin() const               { return (address)    header_begin() + _code_offset; }
  // code_end == content_end is true for all types of blobs for now, it is also checked in the constructor
  address    code_end() const                 { return (address)    header_begin() + _data_offset; }
  address    data_begin() const               { return (address)    header_begin() + _data_offset; }
  address    data_end() const                 { return (address)    header_begin() + _size; }

  // Offsets
  int content_offset() const                  { return _content_offset; }
  int code_offset() const                     { return _code_offset; }
  int data_offset() const                     { return _data_offset; }

  // This field holds the beginning of the const section in the old code buffer.
  // It is needed to fix relocations of pc-relative loads when resizing the
  // the constant pool or moving it.
  S390_ONLY(address ctable_begin() const { return header_begin() + _ctable_offset; })
  void set_ctable_begin(address ctable) { S390_ONLY(_ctable_offset = ctable - header_begin();) }

  // Sizes
  int size() const               { return _size; }
  int header_size() const        { return _header_size; }
  int relocation_size() const    { return pointer_delta_as_int((address) relocation_end(), (address) relocation_begin()); }
  int content_size() const       { return pointer_delta_as_int(content_end(), content_begin()); }
  int code_size() const          { return pointer_delta_as_int(code_end(), code_begin()); }

  // Only used from CodeCache::free_unused_tail() after the Interpreter blob was trimmed
  void adjust_size(size_t used) {
    _size = (int)used;
    _data_offset = (int)used;
  }

  // Containment
  bool blob_contains(address addr) const         { return header_begin()       <= addr && addr < data_end();       }
  bool code_contains(address addr) const         { return code_begin()         <= addr && addr < code_end();       }
  bool contains(address addr) const              { return content_begin()      <= addr && addr < content_end();    }
  bool is_frame_complete_at(address addr) const  { return _frame_complete_offset != CodeOffsets::frame_never_safe &&
                                                          code_contains(addr) && addr >= code_begin() + _frame_complete_offset; }
  int frame_complete_offset() const              { return _frame_complete_offset; }

  // OopMap for frame
  ImmutableOopMapSet* oop_maps() const           { return _oop_maps; }
  void set_oop_maps(OopMapSet* p);

  const ImmutableOopMap* oop_map_for_slot(int slot, address return_address) const;
  const ImmutableOopMap* oop_map_for_return_address(address return_address) const;

  // Frame support. Sizes are in word units.
  int  frame_size() const                        { return _frame_size; }
  void set_frame_size(int size)                  { _frame_size = size; }

  // Returns true, if the next frame is responsible for GC'ing oops passed as arguments
  bool caller_must_gc_arguments(JavaThread* thread) const { return _caller_must_gc_arguments; }

  // Naming
  const char* name() const                       { return _name; }
  void set_name(const char* name)                { _name = name; }

  // Debugging
  virtual void verify() = 0;
  virtual void print() const;
  virtual void print_on(outputStream* st) const;
  virtual void print_value_on(outputStream* st) const;
  void dump_for_addr(address addr, outputStream* st, bool verbose) const;
  void print_code_on(outputStream* st);

  // Print to stream, any comments associated with offset.
  virtual void print_block_comment(outputStream* stream, address block_begin) const {
#ifndef PRODUCT
    ptrdiff_t offset = block_begin - code_begin();
    assert(offset >= 0, "Expecting non-negative offset!");
    _asm_remarks.print(uint(offset), stream);
#endif
  }

#ifndef PRODUCT
  AsmRemarks &asm_remarks() { return _asm_remarks; }
  DbgStrings &dbg_strings() { return _dbg_strings; }

  void use_remarks(AsmRemarks &remarks) { _asm_remarks.share(remarks); }
  void use_strings(DbgStrings &strings) { _dbg_strings.share(strings); }
#endif
};

//----------------------------------------------------------------------------------------------------
// RuntimeBlob: used for non-compiled method code (adapters, stubs, blobs)

class RuntimeBlob : public CodeBlob {
  friend class VMStructs;
 public:

  // Creation
  // a) simple CodeBlob
  RuntimeBlob(const char* name, CodeBlobKind kind, int size, uint16_t header_size)
    : CodeBlob(name, kind, size, header_size)
  {}

  // b) full CodeBlob
  // frame_complete is the offset from the beginning of the instructions
  // to where the frame setup (from stackwalk viewpoint) is complete.
  RuntimeBlob(
    const char* name,
    CodeBlobKind kind,
    CodeBuffer* cb,
    int         size,
    uint16_t    header_size,
    int16_t     frame_complete,
    int         frame_size,
    OopMapSet*  oop_maps,
    bool        caller_must_gc_arguments = false
  );

  static void free(RuntimeBlob* blob);

  // Deal with Disassembler, VTune, Forte, JvmtiExport, MemoryService.
  static void trace_new_stub(RuntimeBlob* blob, const char* name1, const char* name2 = "");
};

class WhiteBox;
//----------------------------------------------------------------------------------------------------
// BufferBlob: used to hold non-relocatable machine code such as the interpreter, stubroutines, etc.

class BufferBlob: public RuntimeBlob {
  friend class VMStructs;
  friend class AdapterBlob;
  friend class VtableBlob;
  friend class MethodHandlesAdapterBlob;
  friend class UpcallStub;
  friend class WhiteBox;

 private:
  // Creation support
  BufferBlob(const char* name, CodeBlobKind kind, int size);
  BufferBlob(const char* name, CodeBlobKind kind, CodeBuffer* cb, int size);

  void* operator new(size_t s, unsigned size) throw();

 public:
  // Creation
  static BufferBlob* create(const char* name, uint buffer_size);
  static BufferBlob* create(const char* name, CodeBuffer* cb);

  static void free(BufferBlob* buf);

  // Verification support
  void verify() override;

  void print_on(outputStream* st) const override;
  void print_value_on(outputStream* st) const override;
};


//----------------------------------------------------------------------------------------------------
// AdapterBlob: used to hold C2I/I2C adapters

class AdapterBlob: public BufferBlob {
private:
  AdapterBlob(int size, CodeBuffer* cb);

public:
  // Creation
  static AdapterBlob* create(CodeBuffer* cb);
};

//---------------------------------------------------------------------------------------------------
class VtableBlob: public BufferBlob {
private:
  VtableBlob(const char*, int);

  void* operator new(size_t s, unsigned size) throw();

public:
  // Creation
  static VtableBlob* create(const char* name, int buffer_size);
};

//----------------------------------------------------------------------------------------------------
// MethodHandlesAdapterBlob: used to hold MethodHandles adapters

class MethodHandlesAdapterBlob: public BufferBlob {
private:
  MethodHandlesAdapterBlob(int size): BufferBlob("MethodHandles adapters", CodeBlobKind::MH_Adapter, size) {}

public:
  // Creation
  static MethodHandlesAdapterBlob* create(int buffer_size);
};


//----------------------------------------------------------------------------------------------------
// RuntimeStub: describes stubs used by compiled code to call a (static) C++ runtime routine

class RuntimeStub: public RuntimeBlob {
  friend class VMStructs;
 private:
  // Creation support
  RuntimeStub(
    const char* name,
    CodeBuffer* cb,
    int         size,
    int16_t     frame_complete,
    int         frame_size,
    OopMapSet*  oop_maps,
    bool        caller_must_gc_arguments
  );

  void* operator new(size_t s, unsigned size) throw();

 public:
  // Creation
  static RuntimeStub* new_runtime_stub(
    const char* stub_name,
    CodeBuffer* cb,
    int16_t     frame_complete,
    int         frame_size,
    OopMapSet*  oop_maps,
    bool        caller_must_gc_arguments,
    bool        alloc_fail_is_fatal=true
  );

  static void free(RuntimeStub* stub) { RuntimeBlob::free(stub); }

  address entry_point() const                    { return code_begin(); }

  // Verification support
  void verify() override;

  void print_on(outputStream* st) const override;
  void print_value_on(outputStream* st) const override;
};


//----------------------------------------------------------------------------------------------------
// Super-class for all blobs that exist in only one instance. Implements default behaviour.

class SingletonBlob: public RuntimeBlob {
  friend class VMStructs;

 protected:
  void* operator new(size_t s, unsigned size) throw();

 public:
   SingletonBlob(
     const char*  name,
     CodeBlobKind kind,
     CodeBuffer*  cb,
     int          size,
     uint16_t     header_size,
     int          frame_size,
     OopMapSet*   oop_maps
   )
   : RuntimeBlob(name, kind, cb, size, header_size, CodeOffsets::frame_never_safe, frame_size, oop_maps)
  {};

  address entry_point()                          { return code_begin(); }

  // Verification support
  void verify() override; // does nothing

  void print_on(outputStream* st) const override;
  void print_value_on(outputStream* st) const override;
};


//----------------------------------------------------------------------------------------------------
// DeoptimizationBlob

class DeoptimizationBlob: public SingletonBlob {
  friend class VMStructs;
  friend class JVMCIVMStructs;
 private:
  int _unpack_offset;
  int _unpack_with_exception;
  int _unpack_with_reexecution;

  int _unpack_with_exception_in_tls;

#if INCLUDE_JVMCI
  // Offsets when JVMCI calls uncommon_trap.
  int _uncommon_trap_offset;
  int _implicit_exception_uncommon_trap_offset;
#endif

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

  // Printing
  void print_value_on(outputStream* st) const override;

  address unpack() const                         { return code_begin() + _unpack_offset;           }
  address unpack_with_exception() const          { return code_begin() + _unpack_with_exception;   }
  address unpack_with_reexecution() const        { return code_begin() + _unpack_with_reexecution; }

  // Alternate entry point for C1 where the exception and issuing pc
  // are in JavaThread::_exception_oop and JavaThread::_exception_pc
  // instead of being in registers.  This is needed because C1 doesn't
  // model exception paths in a way that keeps these registers free so
  // there may be live values in those registers during deopt.
  void set_unpack_with_exception_in_tls_offset(int offset) {
    _unpack_with_exception_in_tls = offset;
    assert(code_contains(code_begin() + _unpack_with_exception_in_tls), "must be PC inside codeblob");
  }
  address unpack_with_exception_in_tls() const   { return code_begin() + _unpack_with_exception_in_tls; }

#if INCLUDE_JVMCI
  // Offsets when JVMCI calls uncommon_trap.
  void set_uncommon_trap_offset(int offset) {
    _uncommon_trap_offset = offset;
    assert(contains(code_begin() + _uncommon_trap_offset), "must be PC inside codeblob");
  }
  address uncommon_trap() const                  { return code_begin() + _uncommon_trap_offset; }

  void set_implicit_exception_uncommon_trap_offset(int offset) {
    _implicit_exception_uncommon_trap_offset = offset;
    assert(contains(code_begin() + _implicit_exception_uncommon_trap_offset), "must be PC inside codeblob");
  }
  address implicit_exception_uncommon_trap() const { return code_begin() + _implicit_exception_uncommon_trap_offset; }
#endif // INCLUDE_JVMCI
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

 public:
  // Creation
  static UncommonTrapBlob* create(
    CodeBuffer* cb,
    OopMapSet*  oop_maps,
    int         frame_size
  );
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

 public:
  // Creation
  static ExceptionBlob* create(
    CodeBuffer* cb,
    OopMapSet*  oop_maps,
    int         frame_size
  );
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

 public:
  // Creation
  static SafepointBlob* create(
    CodeBuffer* cb,
    OopMapSet*  oop_maps,
    int         frame_size
  );
};

//----------------------------------------------------------------------------------------------------

class UpcallLinker;

// A (Panama) upcall stub. Not used by JNI.
class UpcallStub: public RuntimeBlob {
  friend class UpcallLinker;
 private:
  jobject _receiver;
  ByteSize _frame_data_offset;

  UpcallStub(const char* name, CodeBuffer* cb, int size, jobject receiver, ByteSize frame_data_offset);

  void* operator new(size_t s, unsigned size) throw();

  struct FrameData {
    JavaFrameAnchor jfa;
    JavaThread* thread;
    JNIHandleBlock* old_handles;
    JNIHandleBlock* new_handles;
  };

  // defined in frame_ARCH.cpp
  FrameData* frame_data_for_frame(const frame& frame) const;
 public:
  // Creation
  static UpcallStub* create(const char* name, CodeBuffer* cb, jobject receiver, ByteSize frame_data_offset);

  static void free(UpcallStub* blob);

  jobject receiver() { return _receiver; }

  JavaFrameAnchor* jfa_for_frame(const frame& frame) const;

  // GC/Verification support
  void oops_do(OopClosure* f, const frame& frame);
  void verify() override;

  // Misc.
  void print_on(outputStream* st) const override;
  void print_value_on(outputStream* st) const override;
};

#endif // SHARE_CODE_CODEBLOB_HPP
