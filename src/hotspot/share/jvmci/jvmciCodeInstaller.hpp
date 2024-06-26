/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_JVMCI_JVMCICODEINSTALLER_HPP
#define SHARE_JVMCI_JVMCICODEINSTALLER_HPP

#include "classfile/classFileStream.hpp"
#include "code/debugInfoRec.hpp"
#include "code/exceptionHandlerTable.hpp"
#include "code/nativeInst.hpp"
#include "jvmci/jvmci.hpp"
#include "jvmci/jvmciEnv.hpp"

// Object for decoding a serialized HotSpotCompiledCode object.
// Encoding is done by jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.
class HotSpotCompiledCodeStream : public ResourceObj {
 private:
  class Chunk {
   private:
    Chunk* _next;
    u4 _size;

   public:
    u4 size() const            { return _size; }
    const u1* data()     const { return ((const u1*)this) + HEADER; }
    const u1* data_end() const { return data() + _size; }
    Chunk* next() const        { return _next; }
  };

  // Mirrors jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.HEADER
  static const int HEADER = sizeof(Chunk*) + sizeof(u4);

  Chunk* _head;                 // First chunk in buffer
  Chunk* _chunk;                // Chunk currently being read
  mutable const u1* _pos;       // Read position in _chunk
  const bool _with_type_info;
  objArrayHandle _object_pool;  // Pool for objects in Java heap (ignored if libjvmci)
  JavaThread* _thread;          // Current thread

  // Virtual objects in DebugInfo currently being decoded
  GrowableArray<ScopeValue*>* _virtual_objects;

  // HotSpotCompiledCode.name or HotSpotCompiledNmethod.method
  const char* _code_desc;

#define checked_read(value, name, type) do { \
  if (_with_type_info) { check_data(sizeof(type), name); } \
  return (type) value; \
} while (0)

  void before_read(u1 size);

  u1 get_u1() { before_read(1); u1 res = *_pos;         _pos += 1; return res; }
  u2 get_u2() { before_read(2); u2 res = *((u2*) _pos); _pos += 2; return res; }
  u4 get_u4() { before_read(4); u4 res = *((u4*) _pos); _pos += 4; return res; }
  u8 get_u8() { before_read(8); u8 res = *((u8*) _pos); _pos += 8; return res; }

  void check_data(u2 expect_size, const char *expect_name);

 public:
  HotSpotCompiledCodeStream(JavaThread* thread, const u1* buffer, bool with_type_info, objArrayHandle& object_pool) :
    _head((Chunk*) buffer),
    _chunk((Chunk*) buffer),
    _pos(_chunk->data()),
    _with_type_info(with_type_info),
    _object_pool(object_pool),
    _thread(thread),
    _virtual_objects(nullptr),
    _code_desc("<unknown>")
  {}

  // Dump complete buffer to `st`.
  void dump_buffer(outputStream* st=tty) const;

  // Dump last `len` bytes of current buffer chunk to `st`
  void dump_buffer_tail(int len, outputStream* st=tty) const;

  // Gets a string containing code_desc() followed by a hexdump
  // of about 100 bytes in the stream up to the current read position.
  const char* context() const;

  // Gets HotSpotCompiledCode.name or HotSpotCompiledNmethod.method.name_and_sig_as_C_string().
  const char* code_desc() const { return _code_desc; }

  void set_code_desc(const char* name, methodHandle& method) {
    if (name != nullptr) {
      _code_desc = name;
    } else if (!method.is_null()) {
      _code_desc = method->name_and_sig_as_C_string();
    }
  }

  // Current read address.
  address pos() const { return (address) _pos; }

  // Offset of current read position from start of buffer.
  u4 offset() const;

  // Gets the number of remaining bytes in the stream.
  bool available() const;

  oop get_oop(int id, JVMCI_TRAPS) const;
  JavaThread* thread() const { return _thread; }

  void set_virtual_objects(GrowableArray<ScopeValue*>* objs) { _virtual_objects = objs; }
  ScopeValue* virtual_object_at(int id, JVMCI_TRAPS) const;

  u1 read_u1(const char* name) { checked_read(get_u1(), name, u1); }
  u2 read_u2(const char* name) { checked_read(get_u2(), name, u2); }
  u4 read_u4(const char* name) { checked_read(get_u4(), name, u4); }
  u8 read_u8(const char* name) { checked_read(get_u8(), name, u8); }
  s2 read_s2(const char* name) { checked_read(get_u2(), name, s2); }
  s4 read_s4(const char* name) { checked_read(get_u4(), name, s4); }
  s8 read_s8(const char* name) { checked_read(get_u8(), name, s8); }

  bool        read_bool(const char* name) { checked_read((get_u1() != 0), name, bool); }
  Method*     read_method(const char* name);
  Klass*      read_klass(const char* name);
  const char* read_utf8(const char* name, JVMCI_TRAPS);
#undef checked_read
};

// Converts a HotSpotCompiledCode to a CodeBlob or an nmethod.
class CodeInstaller : public StackObj {
  friend class JVMCIVMStructs;
private:
  enum MarkId {
    INVALID_MARK,
    VERIFIED_ENTRY,
    UNVERIFIED_ENTRY,
    OSR_ENTRY,
    EXCEPTION_HANDLER_ENTRY,
    DEOPT_HANDLER_ENTRY,
    FRAME_COMPLETE,
    ENTRY_BARRIER_PATCH,
    INVOKEINTERFACE,
    INVOKEVIRTUAL,
    INVOKESTATIC,
    INVOKESPECIAL,
    INLINE_INVOKE,
    POLL_NEAR,
    POLL_RETURN_NEAR,
    POLL_FAR,
    POLL_RETURN_FAR,
    CARD_TABLE_ADDRESS,
    CARD_TABLE_SHIFT,
    HEAP_TOP_ADDRESS,
    HEAP_END_ADDRESS,
    NARROW_KLASS_BASE_ADDRESS,
    NARROW_OOP_BASE_ADDRESS,
    CRC_TABLE_ADDRESS,
    LOG_OF_HEAP_REGION_GRAIN_BYTES,
    INLINE_CONTIGUOUS_ALLOCATION_SUPPORTED,
    DEOPT_MH_HANDLER_ENTRY,
    VERIFY_OOPS,
    VERIFY_OOP_BITS,
    VERIFY_OOP_MASK,
    VERIFY_OOP_COUNT_ADDRESS,

#ifdef X86
    Z_BARRIER_RELOCATION_FORMAT_LOAD_GOOD_BEFORE_SHL,
    Z_BARRIER_RELOCATION_FORMAT_LOAD_BAD_AFTER_TEST,
    Z_BARRIER_RELOCATION_FORMAT_MARK_BAD_AFTER_TEST,
    Z_BARRIER_RELOCATION_FORMAT_STORE_GOOD_AFTER_CMP,
    Z_BARRIER_RELOCATION_FORMAT_STORE_BAD_AFTER_TEST,
    Z_BARRIER_RELOCATION_FORMAT_STORE_GOOD_AFTER_OR,
    Z_BARRIER_RELOCATION_FORMAT_STORE_GOOD_AFTER_MOV,
#endif
#ifdef AARCH64
    Z_BARRIER_RELOCATION_FORMAT_LOAD_GOOD_BEFORE_TB_X,
    Z_BARRIER_RELOCATION_FORMAT_MARK_BAD_BEFORE_MOV,
    Z_BARRIER_RELOCATION_FORMAT_STORE_GOOD_BEFORE_MOV,
    Z_BARRIER_RELOCATION_FORMAT_STORE_BAD_BEFORE_MOV,
#endif

    INVOKE_INVALID = -1
  };

  // Mirrors jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.Tag
  enum Tag {
    ILLEGAL,
    REGISTER_PRIMITIVE,
    REGISTER_OOP,
    REGISTER_NARROW_OOP,
    REGISTER_VECTOR,
    STACK_SLOT_PRIMITIVE,
    STACK_SLOT_OOP,
    STACK_SLOT_NARROW_OOP,
    STACK_SLOT_VECTOR,
    VIRTUAL_OBJECT_ID,
    VIRTUAL_OBJECT_ID2,
    NULL_CONSTANT,
    RAW_CONSTANT,
    PRIMITIVE_0,
    PRIMITIVE4,
    PRIMITIVE8,
    JOBJECT,
    OBJECT_ID,
    OBJECT_ID2,

    NO_FINALIZABLE_SUBCLASS,
    CONCRETE_SUBTYPE,
    LEAF_TYPE,
    CONCRETE_METHOD,
    CALLSITE_TARGET_VALUE,

    PATCH_OBJECT_ID,
    PATCH_OBJECT_ID2,
    PATCH_NARROW_OBJECT_ID,
    PATCH_NARROW_OBJECT_ID2,
    PATCH_JOBJECT,
    PATCH_NARROW_JOBJECT,
    PATCH_KLASS,
    PATCH_NARROW_KLASS,
    PATCH_METHOD,
    PATCH_DATA_SECTION_REFERENCE,

    SITE_CALL,
    SITE_FOREIGN_CALL,
    SITE_FOREIGN_CALL_NO_DEBUG_INFO,
    SITE_SAFEPOINT,
    SITE_INFOPOINT,
    SITE_IMPLICIT_EXCEPTION,
    SITE_IMPLICIT_EXCEPTION_DISPATCH,
    SITE_MARK,
    SITE_DATA_PATCH,
    SITE_EXCEPTION_HANDLER,
  };

  // Mirrors constants from jdk.vm.ci.code.BytecodeFrame.
  enum BytecodeFrameBCI {
    UNWIND_BCI = -1,
    BEFORE_BCI = -2,
    AFTER_BCI = -3,
    AFTER_EXCEPTION_BCI = -4,
    UNKNOWN_BCI = -5,
    INVALID_FRAMESTATE_BCI = -6
  };

  // Mirrors HotSpotCompiledCode flags from jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.
  enum HotSpotCompiledCodeFlags {
    HCC_IS_NMETHOD            = 0x01,
    HCC_HAS_ASSUMPTIONS       = 0x02,
    HCC_HAS_METHODS           = 0x04,
    HCC_HAS_DEOPT_RESCUE_SLOT = 0x08,
    HCC_HAS_COMMENTS          = 0x10
  };

  // Mirrors DebugInfo flags from jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.
  enum DebugInfoFlags {
    DI_HAS_REFERENCE_MAP    = 0x01,
    DI_HAS_CALLEE_SAVE_INFO = 0x02,
    DI_HAS_FRAMES           = 0x04
  };

  // Mirrors BytecodeFrame flags from jdk.vm.ci.hotspot.HotSpotCompiledCodeStream.
  enum DebugInfoFrameFlags {
    DIF_HAS_LOCALS        = 0x01,
    DIF_HAS_STACK         = 0x02,
    DIF_HAS_LOCKS         = 0x04,
    DIF_DURING_CALL       = 0x08,
    DIF_RETHROW_EXCEPTION = 0x10
  };

  // Sentinel value in a DebugInfo stream denoting no register.
  static const int NO_REGISTER = 0xFFFF;

  Arena         _arena;
  JVMCIEnv*     _jvmci_env;

  jint          _sites_count;

  CodeOffsets   _offsets;
  int           _nmethod_entry_patch_offset;

  jint          _code_size;
  jint          _total_frame_size;
  jint          _orig_pc_offset;
  jint          _parameter_count;
  jint          _constants_size;

  bool          _has_monitors;
  bool          _has_wide_vector;

  MarkId        _next_call_type;
  address       _invoke_mark_pc;

  CodeSection*  _instructions;
  CodeSection*  _constants;

  OopRecorder*              _oop_recorder;
  DebugInformationRecorder* _debug_recorder;
  Dependencies*             _dependencies;
  ExceptionHandlerTable     _exception_handler_table;
  ImplicitExceptionTable    _implicit_exception_table;
  bool                      _has_auto_box;

  static ConstantOopWriteValue* _oop_null_scope_value;
  static ConstantIntValue*    _int_m1_scope_value;
  static ConstantIntValue*    _int_0_scope_value;
  static ConstantIntValue*    _int_1_scope_value;
  static ConstantIntValue*    _int_2_scope_value;
  static LocationValue*       _illegal_value;
  static MarkerValue*         _virtual_byte_array_marker;

  jint pd_next_offset(NativeInstruction* inst, jint pc_offset, JVMCI_TRAPS);
  void pd_patch_OopConstant(int pc_offset, Handle& obj, bool compressed, JVMCI_TRAPS);
  void pd_patch_MetaspaceConstant(int pc_offset, HotSpotCompiledCodeStream* stream, u1 tag, JVMCI_TRAPS);
  void pd_patch_DataSectionReference(int pc_offset, int data_offset, JVMCI_TRAPS);
  void pd_relocate_ForeignCall(NativeInstruction* inst, jlong foreign_call_destination, JVMCI_TRAPS);
  void pd_relocate_JavaMethod(CodeBuffer &cbuf, methodHandle& method, jint pc_offset, JVMCI_TRAPS);
  bool pd_relocate(address pc, jint mark);

public:

#ifndef PRODUCT
  // Verifies the enum mirroring BCI constants in BytecodeFrame is in sync.
  static void verify_bci_constants(JVMCIEnv* env);
#endif

  CodeInstaller(JVMCIEnv* jvmci_env) :
    _arena(mtJVMCI),
    _jvmci_env(jvmci_env),
    _has_auto_box(false) {}

  JVMCI::CodeInstallResult install(JVMCICompiler* compiler,
                                   jlong compiled_code_buffer,
                                   bool with_type_info,
                                   JVMCIObject compiled_code,
                                   objArrayHandle object_pool,
                                   CodeBlob*& cb,
                                   JVMCINMethodHandle& nmethod_handle,
                                   JVMCIObject installed_code,
                                   FailedSpeculation** failed_speculations,
                                   char* speculations,
                                   int speculations_len,
                                   JVMCI_TRAPS);

  JVMCIEnv* jvmci_env() { return _jvmci_env; }
  JVMCIRuntime* runtime() { return _jvmci_env->runtime(); }

  static address runtime_call_target_address(oop runtime_call);
  static VMReg get_hotspot_reg(jint jvmciRegisterNumber, JVMCI_TRAPS);
  static bool is_general_purpose_reg(VMReg hotspotRegister);
  static ScopeValue* to_primitive_value(HotSpotCompiledCodeStream* stream, jlong raw, BasicType type, ScopeValue* &second, JVMCI_TRAPS);

  const OopMapSet* oopMapSet() const { return _debug_recorder->_oopmaps; }

  // Gets the tag to be used with `read_oop()` corresponding to `patch_object_tag`.
  static u1 as_read_oop_tag(HotSpotCompiledCodeStream* stream, u1 patch_object_tag, JVMCI_TRAPS);

protected:
  Handle read_oop(HotSpotCompiledCodeStream* stream, u1 tag, JVMCI_TRAPS);

  ScopeValue* get_scope_value(HotSpotCompiledCodeStream* stream, u1 tag, BasicType type, ScopeValue* &second, JVMCI_TRAPS);

  GrowableArray<ScopeValue*>* read_local_or_stack_values(HotSpotCompiledCodeStream* stream, u1 frame_flags, bool is_locals, JVMCI_TRAPS);

  void* record_metadata_reference(CodeSection* section, address dest, HotSpotCompiledCodeStream* stream, u1 tag, JVMCI_TRAPS);
#ifdef _LP64
  narrowKlass record_narrow_metadata_reference(CodeSection* section, address dest, HotSpotCompiledCodeStream* stream, u1 tag, JVMCI_TRAPS);
#endif
  GrowableArray<MonitorValue*>* read_monitor_values(HotSpotCompiledCodeStream* stream, u1 frame_flags, JVMCI_TRAPS);

  // extract the fields of the HotSpotCompiledCode
  void initialize_fields(HotSpotCompiledCodeStream* stream, u1 code_flags, methodHandle& method, CodeBuffer& buffer, JVMCI_TRAPS);
  void initialize_dependencies(HotSpotCompiledCodeStream* stream, u1 code_flags, OopRecorder* oop_recorder, JVMCI_TRAPS);

  int estimate_stubs_size(HotSpotCompiledCodeStream* stream, JVMCI_TRAPS);

  // perform data and call relocation on the CodeBuffer
  JVMCI::CodeInstallResult initialize_buffer(JVMCIObject compiled_code, CodeBuffer& buffer, HotSpotCompiledCodeStream* stream, u1 code_flags, JVMCI_TRAPS);

  void site_Safepoint(CodeBuffer& buffer, jint pc_offset, HotSpotCompiledCodeStream* stream, u1 tag, JVMCI_TRAPS);
  void site_Infopoint(CodeBuffer& buffer, jint pc_offset, HotSpotCompiledCodeStream* stream, JVMCI_TRAPS);
  void site_Call(CodeBuffer& buffer, u1 tag, jint pc_offset, HotSpotCompiledCodeStream* stream, JVMCI_TRAPS);
  void site_DataPatch(CodeBuffer& buffer, jint pc_offset, HotSpotCompiledCodeStream* stream, JVMCI_TRAPS);
  void site_Mark(CodeBuffer& buffer, jint pc_offset, HotSpotCompiledCodeStream* stream, JVMCI_TRAPS);
  void site_ExceptionHandler(jint pc_offset, HotSpotCompiledCodeStream* stream);

  OopMap* create_oop_map(HotSpotCompiledCodeStream* stream, u1 debug_info_flags, JVMCI_TRAPS);

  VMReg getVMRegFromLocation(HotSpotCompiledCodeStream* stream, int total_frame_size, JVMCI_TRAPS);

  int map_jvmci_bci(int bci);

  void record_oop_patch(HotSpotCompiledCodeStream* stream, address dest, u1 read_tag, bool narrow, JVMCI_TRAPS);

  // full_info: if false, only BytecodePosition is in stream otherwise all DebugInfo is in stream
  void record_scope(jint pc_offset, HotSpotCompiledCodeStream* stream, u1 debug_info_flags, bool full_info, bool is_mh_invoke, bool return_oop, JVMCI_TRAPS);

  void record_scope(jint pc_offset, HotSpotCompiledCodeStream* stream, u1 debug_info_flags, bool full_info, JVMCI_TRAPS) {
    record_scope(pc_offset, stream, debug_info_flags, full_info, false /* is_mh_invoke */, false /* return_oop */, JVMCIENV);
  }
  void record_object_value(ObjectValue* sv, HotSpotCompiledCodeStream* stream, JVMCI_TRAPS);

  void read_virtual_objects(HotSpotCompiledCodeStream* stream, JVMCI_TRAPS);

  int estimateStubSpace(int static_call_stubs);

  JVMCI::CodeInstallResult install_runtime_stub(CodeBlob*& cb,
                                                const char* name,
                                                CodeBuffer* buffer,
                                                int stack_slots,
                                                JVMCI_TRAPS);
};

#endif // SHARE_JVMCI_JVMCICODEINSTALLER_HPP
