/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compileLog.hpp"
#include "oops/objArrayKlass.hpp"
#include "opto/addnode.hpp"
#include "opto/callGenerator.hpp"
#include "opto/cfgnode.hpp"
#include "opto/idealKit.hpp"
#include "opto/mulnode.hpp"
#include "opto/parse.hpp"
#include "opto/runtime.hpp"
#include "opto/subnode.hpp"
#include "prims/nativeLookup.hpp"
#include "runtime/sharedRuntime.hpp"
#include "trace/traceMacros.hpp"

class LibraryIntrinsic : public InlineCallGenerator {
  // Extend the set of intrinsics known to the runtime:
 public:
 private:
  bool             _is_virtual;
  bool             _is_predicted;
  vmIntrinsics::ID _intrinsic_id;

 public:
  LibraryIntrinsic(ciMethod* m, bool is_virtual, bool is_predicted, vmIntrinsics::ID id)
    : InlineCallGenerator(m),
      _is_virtual(is_virtual),
      _is_predicted(is_predicted),
      _intrinsic_id(id)
  {
  }
  virtual bool is_intrinsic() const { return true; }
  virtual bool is_virtual()   const { return _is_virtual; }
  virtual bool is_predicted()   const { return _is_predicted; }
  virtual JVMState* generate(JVMState* jvms);
  virtual Node* generate_predicate(JVMState* jvms);
  vmIntrinsics::ID intrinsic_id() const { return _intrinsic_id; }
};


// Local helper class for LibraryIntrinsic:
class LibraryCallKit : public GraphKit {
 private:
  LibraryIntrinsic* _intrinsic;     // the library intrinsic being called
  Node*             _result;        // the result node, if any
  int               _reexecute_sp;  // the stack pointer when bytecode needs to be reexecuted

  const TypeOopPtr* sharpen_unsafe_type(Compile::AliasType* alias_type, const TypePtr *adr_type, bool is_native_ptr = false);

 public:
  LibraryCallKit(JVMState* jvms, LibraryIntrinsic* intrinsic)
    : GraphKit(jvms),
      _intrinsic(intrinsic),
      _result(NULL)
  {
    // Check if this is a root compile.  In that case we don't have a caller.
    if (!jvms->has_method()) {
      _reexecute_sp = sp();
    } else {
      // Find out how many arguments the interpreter needs when deoptimizing
      // and save the stack pointer value so it can used by uncommon_trap.
      // We find the argument count by looking at the declared signature.
      bool ignored_will_link;
      ciSignature* declared_signature = NULL;
      ciMethod* ignored_callee = caller()->get_method_at_bci(bci(), ignored_will_link, &declared_signature);
      const int nargs = declared_signature->arg_size_for_bc(caller()->java_code_at_bci(bci()));
      _reexecute_sp = sp() + nargs;  // "push" arguments back on stack
    }
  }

  virtual LibraryCallKit* is_LibraryCallKit() const { return (LibraryCallKit*)this; }

  ciMethod*         caller()    const    { return jvms()->method(); }
  int               bci()       const    { return jvms()->bci(); }
  LibraryIntrinsic* intrinsic() const    { return _intrinsic; }
  vmIntrinsics::ID  intrinsic_id() const { return _intrinsic->intrinsic_id(); }
  ciMethod*         callee()    const    { return _intrinsic->method(); }

  bool try_to_inline();
  Node* try_to_predicate();

  void push_result() {
    // Push the result onto the stack.
    if (!stopped() && result() != NULL) {
      BasicType bt = result()->bottom_type()->basic_type();
      push_node(bt, result());
    }
  }

 private:
  void fatal_unexpected_iid(vmIntrinsics::ID iid) {
    fatal(err_msg_res("unexpected intrinsic %d: %s", iid, vmIntrinsics::name_at(iid)));
  }

  void  set_result(Node* n) { assert(_result == NULL, "only set once"); _result = n; }
  void  set_result(RegionNode* region, PhiNode* value);
  Node*     result() { return _result; }

  virtual int reexecute_sp() { return _reexecute_sp; }

  // Helper functions to inline natives
  Node* generate_guard(Node* test, RegionNode* region, float true_prob);
  Node* generate_slow_guard(Node* test, RegionNode* region);
  Node* generate_fair_guard(Node* test, RegionNode* region);
  Node* generate_negative_guard(Node* index, RegionNode* region,
                                // resulting CastII of index:
                                Node* *pos_index = NULL);
  Node* generate_nonpositive_guard(Node* index, bool never_negative,
                                   // resulting CastII of index:
                                   Node* *pos_index = NULL);
  Node* generate_limit_guard(Node* offset, Node* subseq_length,
                             Node* array_length,
                             RegionNode* region);
  Node* generate_current_thread(Node* &tls_output);
  address basictype2arraycopy(BasicType t, Node *src_offset, Node *dest_offset,
                              bool disjoint_bases, const char* &name, bool dest_uninitialized);
  Node* load_mirror_from_klass(Node* klass);
  Node* load_klass_from_mirror_common(Node* mirror, bool never_see_null,
                                      RegionNode* region, int null_path,
                                      int offset);
  Node* load_klass_from_mirror(Node* mirror, bool never_see_null,
                               RegionNode* region, int null_path) {
    int offset = java_lang_Class::klass_offset_in_bytes();
    return load_klass_from_mirror_common(mirror, never_see_null,
                                         region, null_path,
                                         offset);
  }
  Node* load_array_klass_from_mirror(Node* mirror, bool never_see_null,
                                     RegionNode* region, int null_path) {
    int offset = java_lang_Class::array_klass_offset_in_bytes();
    return load_klass_from_mirror_common(mirror, never_see_null,
                                         region, null_path,
                                         offset);
  }
  Node* generate_access_flags_guard(Node* kls,
                                    int modifier_mask, int modifier_bits,
                                    RegionNode* region);
  Node* generate_interface_guard(Node* kls, RegionNode* region);
  Node* generate_array_guard(Node* kls, RegionNode* region) {
    return generate_array_guard_common(kls, region, false, false);
  }
  Node* generate_non_array_guard(Node* kls, RegionNode* region) {
    return generate_array_guard_common(kls, region, false, true);
  }
  Node* generate_objArray_guard(Node* kls, RegionNode* region) {
    return generate_array_guard_common(kls, region, true, false);
  }
  Node* generate_non_objArray_guard(Node* kls, RegionNode* region) {
    return generate_array_guard_common(kls, region, true, true);
  }
  Node* generate_array_guard_common(Node* kls, RegionNode* region,
                                    bool obj_array, bool not_array);
  Node* generate_virtual_guard(Node* obj_klass, RegionNode* slow_region);
  CallJavaNode* generate_method_call(vmIntrinsics::ID method_id,
                                     bool is_virtual = false, bool is_static = false);
  CallJavaNode* generate_method_call_static(vmIntrinsics::ID method_id) {
    return generate_method_call(method_id, false, true);
  }
  CallJavaNode* generate_method_call_virtual(vmIntrinsics::ID method_id) {
    return generate_method_call(method_id, true, false);
  }
  Node * load_field_from_object(Node * fromObj, const char * fieldName, const char * fieldTypeString, bool is_exact, bool is_static);

  Node* make_string_method_node(int opcode, Node* str1_start, Node* cnt1, Node* str2_start, Node* cnt2);
  Node* make_string_method_node(int opcode, Node* str1, Node* str2);
  bool inline_string_compareTo();
  bool inline_string_indexOf();
  Node* string_indexOf(Node* string_object, ciTypeArray* target_array, jint offset, jint cache_i, jint md2_i);
  bool inline_string_equals();
  Node* round_double_node(Node* n);
  bool runtime_math(const TypeFunc* call_type, address funcAddr, const char* funcName);
  bool inline_math_native(vmIntrinsics::ID id);
  bool inline_trig(vmIntrinsics::ID id);
  bool inline_math(vmIntrinsics::ID id);
  bool inline_exp();
  bool inline_pow();
  void finish_pow_exp(Node* result, Node* x, Node* y, const TypeFunc* call_type, address funcAddr, const char* funcName);
  bool inline_min_max(vmIntrinsics::ID id);
  Node* generate_min_max(vmIntrinsics::ID id, Node* x, Node* y);
  // This returns Type::AnyPtr, RawPtr, or OopPtr.
  int classify_unsafe_addr(Node* &base, Node* &offset);
  Node* make_unsafe_address(Node* base, Node* offset);
  // Helper for inline_unsafe_access.
  // Generates the guards that check whether the result of
  // Unsafe.getObject should be recorded in an SATB log buffer.
  void insert_pre_barrier(Node* base_oop, Node* offset, Node* pre_val, bool need_mem_bar);
  bool inline_unsafe_access(bool is_native_ptr, bool is_store, BasicType type, bool is_volatile);
  bool inline_unsafe_prefetch(bool is_native_ptr, bool is_store, bool is_static);
  static bool klass_needs_init_guard(Node* kls);
  bool inline_unsafe_allocate();
  bool inline_unsafe_copyMemory();
  bool inline_native_currentThread();
#ifdef TRACE_HAVE_INTRINSICS
  bool inline_native_classID();
  bool inline_native_threadID();
#endif
  bool inline_native_time_funcs(address method, const char* funcName);
  bool inline_native_isInterrupted();
  bool inline_native_Class_query(vmIntrinsics::ID id);
  bool inline_native_subtype_check();

  bool inline_native_newArray();
  bool inline_native_getLength();
  bool inline_array_copyOf(bool is_copyOfRange);
  bool inline_array_equals();
  void copy_to_clone(Node* obj, Node* alloc_obj, Node* obj_size, bool is_array, bool card_mark);
  bool inline_native_clone(bool is_virtual);
  bool inline_native_Reflection_getCallerClass();
  // Helper function for inlining native object hash method
  bool inline_native_hashcode(bool is_virtual, bool is_static);
  bool inline_native_getClass();

  // Helper functions for inlining arraycopy
  bool inline_arraycopy();
  void generate_arraycopy(const TypePtr* adr_type,
                          BasicType basic_elem_type,
                          Node* src,  Node* src_offset,
                          Node* dest, Node* dest_offset,
                          Node* copy_length,
                          bool disjoint_bases = false,
                          bool length_never_negative = false,
                          RegionNode* slow_region = NULL);
  AllocateArrayNode* tightly_coupled_allocation(Node* ptr,
                                                RegionNode* slow_region);
  void generate_clear_array(const TypePtr* adr_type,
                            Node* dest,
                            BasicType basic_elem_type,
                            Node* slice_off,
                            Node* slice_len,
                            Node* slice_end);
  bool generate_block_arraycopy(const TypePtr* adr_type,
                                BasicType basic_elem_type,
                                AllocateNode* alloc,
                                Node* src,  Node* src_offset,
                                Node* dest, Node* dest_offset,
                                Node* dest_size, bool dest_uninitialized);
  void generate_slow_arraycopy(const TypePtr* adr_type,
                               Node* src,  Node* src_offset,
                               Node* dest, Node* dest_offset,
                               Node* copy_length, bool dest_uninitialized);
  Node* generate_checkcast_arraycopy(const TypePtr* adr_type,
                                     Node* dest_elem_klass,
                                     Node* src,  Node* src_offset,
                                     Node* dest, Node* dest_offset,
                                     Node* copy_length, bool dest_uninitialized);
  Node* generate_generic_arraycopy(const TypePtr* adr_type,
                                   Node* src,  Node* src_offset,
                                   Node* dest, Node* dest_offset,
                                   Node* copy_length, bool dest_uninitialized);
  void generate_unchecked_arraycopy(const TypePtr* adr_type,
                                    BasicType basic_elem_type,
                                    bool disjoint_bases,
                                    Node* src,  Node* src_offset,
                                    Node* dest, Node* dest_offset,
                                    Node* copy_length, bool dest_uninitialized);
  typedef enum { LS_xadd, LS_xchg, LS_cmpxchg } LoadStoreKind;
  bool inline_unsafe_load_store(BasicType type,  LoadStoreKind kind);
  bool inline_unsafe_ordered_store(BasicType type);
  bool inline_unsafe_fence(vmIntrinsics::ID id);
  bool inline_fp_conversions(vmIntrinsics::ID id);
  bool inline_number_methods(vmIntrinsics::ID id);
  bool inline_reference_get();
  bool inline_aescrypt_Block(vmIntrinsics::ID id);
  bool inline_cipherBlockChaining_AESCrypt(vmIntrinsics::ID id);
  Node* inline_cipherBlockChaining_AESCrypt_predicate(bool decrypting);
  Node* get_key_start_from_aescrypt_object(Node* aescrypt_object);
  bool inline_encodeISOArray();
  bool inline_updateCRC32();
  bool inline_updateBytesCRC32();
  bool inline_updateByteBufferCRC32();
};


//---------------------------make_vm_intrinsic----------------------------
CallGenerator* Compile::make_vm_intrinsic(ciMethod* m, bool is_virtual) {
  vmIntrinsics::ID id = m->intrinsic_id();
  assert(id != vmIntrinsics::_none, "must be a VM intrinsic");

  if (DisableIntrinsic[0] != '\0'
      && strstr(DisableIntrinsic, vmIntrinsics::name_at(id)) != NULL) {
    // disabled by a user request on the command line:
    // example: -XX:DisableIntrinsic=_hashCode,_getClass
    return NULL;
  }

  if (!m->is_loaded()) {
    // do not attempt to inline unloaded methods
    return NULL;
  }

  // Only a few intrinsics implement a virtual dispatch.
  // They are expensive calls which are also frequently overridden.
  if (is_virtual) {
    switch (id) {
    case vmIntrinsics::_hashCode:
    case vmIntrinsics::_clone:
      // OK, Object.hashCode and Object.clone intrinsics come in both flavors
      break;
    default:
      return NULL;
    }
  }

  // -XX:-InlineNatives disables nearly all intrinsics:
  if (!InlineNatives) {
    switch (id) {
    case vmIntrinsics::_indexOf:
    case vmIntrinsics::_compareTo:
    case vmIntrinsics::_equals:
    case vmIntrinsics::_equalsC:
    case vmIntrinsics::_getAndAddInt:
    case vmIntrinsics::_getAndAddLong:
    case vmIntrinsics::_getAndSetInt:
    case vmIntrinsics::_getAndSetLong:
    case vmIntrinsics::_getAndSetObject:
    case vmIntrinsics::_loadFence:
    case vmIntrinsics::_storeFence:
    case vmIntrinsics::_fullFence:
      break;  // InlineNatives does not control String.compareTo
    case vmIntrinsics::_Reference_get:
      break;  // InlineNatives does not control Reference.get
    default:
      return NULL;
    }
  }

  bool is_predicted = false;

  switch (id) {
  case vmIntrinsics::_compareTo:
    if (!SpecialStringCompareTo)  return NULL;
    if (!Matcher::match_rule_supported(Op_StrComp))  return NULL;
    break;
  case vmIntrinsics::_indexOf:
    if (!SpecialStringIndexOf)  return NULL;
    break;
  case vmIntrinsics::_equals:
    if (!SpecialStringEquals)  return NULL;
    if (!Matcher::match_rule_supported(Op_StrEquals))  return NULL;
    break;
  case vmIntrinsics::_equalsC:
    if (!SpecialArraysEquals)  return NULL;
    if (!Matcher::match_rule_supported(Op_AryEq))  return NULL;
    break;
  case vmIntrinsics::_arraycopy:
    if (!InlineArrayCopy)  return NULL;
    break;
  case vmIntrinsics::_copyMemory:
    if (StubRoutines::unsafe_arraycopy() == NULL)  return NULL;
    if (!InlineArrayCopy)  return NULL;
    break;
  case vmIntrinsics::_hashCode:
    if (!InlineObjectHash)  return NULL;
    break;
  case vmIntrinsics::_clone:
  case vmIntrinsics::_copyOf:
  case vmIntrinsics::_copyOfRange:
    if (!InlineObjectCopy)  return NULL;
    // These also use the arraycopy intrinsic mechanism:
    if (!InlineArrayCopy)  return NULL;
    break;
  case vmIntrinsics::_encodeISOArray:
    if (!SpecialEncodeISOArray)  return NULL;
    if (!Matcher::match_rule_supported(Op_EncodeISOArray))  return NULL;
    break;
  case vmIntrinsics::_checkIndex:
    // We do not intrinsify this.  The optimizer does fine with it.
    return NULL;

  case vmIntrinsics::_getCallerClass:
    if (!UseNewReflection)  return NULL;
    if (!InlineReflectionGetCallerClass)  return NULL;
    if (SystemDictionary::reflect_CallerSensitive_klass() == NULL)  return NULL;
    break;

  case vmIntrinsics::_bitCount_i:
    if (!Matcher::match_rule_supported(Op_PopCountI)) return NULL;
    break;

  case vmIntrinsics::_bitCount_l:
    if (!Matcher::match_rule_supported(Op_PopCountL)) return NULL;
    break;

  case vmIntrinsics::_numberOfLeadingZeros_i:
    if (!Matcher::match_rule_supported(Op_CountLeadingZerosI)) return NULL;
    break;

  case vmIntrinsics::_numberOfLeadingZeros_l:
    if (!Matcher::match_rule_supported(Op_CountLeadingZerosL)) return NULL;
    break;

  case vmIntrinsics::_numberOfTrailingZeros_i:
    if (!Matcher::match_rule_supported(Op_CountTrailingZerosI)) return NULL;
    break;

  case vmIntrinsics::_numberOfTrailingZeros_l:
    if (!Matcher::match_rule_supported(Op_CountTrailingZerosL)) return NULL;
    break;

  case vmIntrinsics::_reverseBytes_c:
    if (!Matcher::match_rule_supported(Op_ReverseBytesUS)) return NULL;
    break;
  case vmIntrinsics::_reverseBytes_s:
    if (!Matcher::match_rule_supported(Op_ReverseBytesS))  return NULL;
    break;
  case vmIntrinsics::_reverseBytes_i:
    if (!Matcher::match_rule_supported(Op_ReverseBytesI))  return NULL;
    break;
  case vmIntrinsics::_reverseBytes_l:
    if (!Matcher::match_rule_supported(Op_ReverseBytesL))  return NULL;
    break;

  case vmIntrinsics::_Reference_get:
    // Use the intrinsic version of Reference.get() so that the value in
    // the referent field can be registered by the G1 pre-barrier code.
    // Also add memory barrier to prevent commoning reads from this field
    // across safepoint since GC can change it value.
    break;

  case vmIntrinsics::_compareAndSwapObject:
#ifdef _LP64
    if (!UseCompressedOops && !Matcher::match_rule_supported(Op_CompareAndSwapP)) return NULL;
#endif
    break;

  case vmIntrinsics::_compareAndSwapLong:
    if (!Matcher::match_rule_supported(Op_CompareAndSwapL)) return NULL;
    break;

  case vmIntrinsics::_getAndAddInt:
    if (!Matcher::match_rule_supported(Op_GetAndAddI)) return NULL;
    break;

  case vmIntrinsics::_getAndAddLong:
    if (!Matcher::match_rule_supported(Op_GetAndAddL)) return NULL;
    break;

  case vmIntrinsics::_getAndSetInt:
    if (!Matcher::match_rule_supported(Op_GetAndSetI)) return NULL;
    break;

  case vmIntrinsics::_getAndSetLong:
    if (!Matcher::match_rule_supported(Op_GetAndSetL)) return NULL;
    break;

  case vmIntrinsics::_getAndSetObject:
#ifdef _LP64
    if (!UseCompressedOops && !Matcher::match_rule_supported(Op_GetAndSetP)) return NULL;
    if (UseCompressedOops && !Matcher::match_rule_supported(Op_GetAndSetN)) return NULL;
    break;
#else
    if (!Matcher::match_rule_supported(Op_GetAndSetP)) return NULL;
    break;
#endif

  case vmIntrinsics::_aescrypt_encryptBlock:
  case vmIntrinsics::_aescrypt_decryptBlock:
    if (!UseAESIntrinsics) return NULL;
    break;

  case vmIntrinsics::_cipherBlockChaining_encryptAESCrypt:
  case vmIntrinsics::_cipherBlockChaining_decryptAESCrypt:
    if (!UseAESIntrinsics) return NULL;
    // these two require the predicated logic
    is_predicted = true;
    break;

  case vmIntrinsics::_updateCRC32:
  case vmIntrinsics::_updateBytesCRC32:
  case vmIntrinsics::_updateByteBufferCRC32:
    if (!UseCRC32Intrinsics) return NULL;
    break;

 default:
    assert(id <= vmIntrinsics::LAST_COMPILER_INLINE, "caller responsibility");
    assert(id != vmIntrinsics::_Object_init && id != vmIntrinsics::_invoke, "enum out of order?");
    break;
  }

  // -XX:-InlineClassNatives disables natives from the Class class.
  // The flag applies to all reflective calls, notably Array.newArray
  // (visible to Java programmers as Array.newInstance).
  if (m->holder()->name() == ciSymbol::java_lang_Class() ||
      m->holder()->name() == ciSymbol::java_lang_reflect_Array()) {
    if (!InlineClassNatives)  return NULL;
  }

  // -XX:-InlineThreadNatives disables natives from the Thread class.
  if (m->holder()->name() == ciSymbol::java_lang_Thread()) {
    if (!InlineThreadNatives)  return NULL;
  }

  // -XX:-InlineMathNatives disables natives from the Math,Float and Double classes.
  if (m->holder()->name() == ciSymbol::java_lang_Math() ||
      m->holder()->name() == ciSymbol::java_lang_Float() ||
      m->holder()->name() == ciSymbol::java_lang_Double()) {
    if (!InlineMathNatives)  return NULL;
  }

  // -XX:-InlineUnsafeOps disables natives from the Unsafe class.
  if (m->holder()->name() == ciSymbol::sun_misc_Unsafe()) {
    if (!InlineUnsafeOps)  return NULL;
  }

  return new LibraryIntrinsic(m, is_virtual, is_predicted, (vmIntrinsics::ID) id);
}

//----------------------register_library_intrinsics-----------------------
// Initialize this file's data structures, for each Compile instance.
void Compile::register_library_intrinsics() {
  // Nothing to do here.
}

JVMState* LibraryIntrinsic::generate(JVMState* jvms) {
  LibraryCallKit kit(jvms, this);
  Compile* C = kit.C;
  int nodes = C->unique();
#ifndef PRODUCT
  if ((PrintIntrinsics || PrintInlining NOT_PRODUCT( || PrintOptoInlining) ) && Verbose) {
    char buf[1000];
    const char* str = vmIntrinsics::short_name_as_C_string(intrinsic_id(), buf, sizeof(buf));
    tty->print_cr("Intrinsic %s", str);
  }
#endif
  ciMethod* callee = kit.callee();
  const int bci    = kit.bci();

  // Try to inline the intrinsic.
  if (kit.try_to_inline()) {
    if (PrintIntrinsics || PrintInlining NOT_PRODUCT( || PrintOptoInlining) ) {
      C->print_inlining(callee, jvms->depth() - 1, bci, is_virtual() ? "(intrinsic, virtual)" : "(intrinsic)");
    }
    C->gather_intrinsic_statistics(intrinsic_id(), is_virtual(), Compile::_intrinsic_worked);
    if (C->log()) {
      C->log()->elem("intrinsic id='%s'%s nodes='%d'",
                     vmIntrinsics::name_at(intrinsic_id()),
                     (is_virtual() ? " virtual='1'" : ""),
                     C->unique() - nodes);
    }
    // Push the result from the inlined method onto the stack.
    kit.push_result();
    return kit.transfer_exceptions_into_jvms();
  }

  // The intrinsic bailed out
  if (PrintIntrinsics || PrintInlining NOT_PRODUCT( || PrintOptoInlining) ) {
    if (jvms->has_method()) {
      // Not a root compile.
      const char* msg = is_virtual() ? "failed to inline (intrinsic, virtual)" : "failed to inline (intrinsic)";
      C->print_inlining(callee, jvms->depth() - 1, bci, msg);
    } else {
      // Root compile
      tty->print("Did not generate intrinsic %s%s at bci:%d in",
               vmIntrinsics::name_at(intrinsic_id()),
               (is_virtual() ? " (virtual)" : ""), bci);
    }
  }
  C->gather_intrinsic_statistics(intrinsic_id(), is_virtual(), Compile::_intrinsic_failed);
  return NULL;
}

Node* LibraryIntrinsic::generate_predicate(JVMState* jvms) {
  LibraryCallKit kit(jvms, this);
  Compile* C = kit.C;
  int nodes = C->unique();
#ifndef PRODUCT
  assert(is_predicted(), "sanity");
  if ((PrintIntrinsics || PrintInlining NOT_PRODUCT( || PrintOptoInlining) ) && Verbose) {
    char buf[1000];
    const char* str = vmIntrinsics::short_name_as_C_string(intrinsic_id(), buf, sizeof(buf));
    tty->print_cr("Predicate for intrinsic %s", str);
  }
#endif
  ciMethod* callee = kit.callee();
  const int bci    = kit.bci();

  Node* slow_ctl = kit.try_to_predicate();
  if (!kit.failing()) {
    if (PrintIntrinsics || PrintInlining NOT_PRODUCT( || PrintOptoInlining) ) {
      C->print_inlining(callee, jvms->depth() - 1, bci, is_virtual() ? "(intrinsic, virtual)" : "(intrinsic)");
    }
    C->gather_intrinsic_statistics(intrinsic_id(), is_virtual(), Compile::_intrinsic_worked);
    if (C->log()) {
      C->log()->elem("predicate_intrinsic id='%s'%s nodes='%d'",
                     vmIntrinsics::name_at(intrinsic_id()),
                     (is_virtual() ? " virtual='1'" : ""),
                     C->unique() - nodes);
    }
    return slow_ctl; // Could be NULL if the check folds.
  }

  // The intrinsic bailed out
  if (PrintIntrinsics || PrintInlining NOT_PRODUCT( || PrintOptoInlining) ) {
    if (jvms->has_method()) {
      // Not a root compile.
      const char* msg = "failed to generate predicate for intrinsic";
      C->print_inlining(kit.callee(), jvms->depth() - 1, bci, msg);
    } else {
      // Root compile
      C->print_inlining_stream()->print("Did not generate predicate for intrinsic %s%s at bci:%d in",
                                        vmIntrinsics::name_at(intrinsic_id()),
                                        (is_virtual() ? " (virtual)" : ""), bci);
    }
  }
  C->gather_intrinsic_statistics(intrinsic_id(), is_virtual(), Compile::_intrinsic_failed);
  return NULL;
}

bool LibraryCallKit::try_to_inline() {
  // Handle symbolic names for otherwise undistinguished boolean switches:
  const bool is_store       = true;
  const bool is_native_ptr  = true;
  const bool is_static      = true;
  const bool is_volatile    = true;

  if (!jvms()->has_method()) {
    // Root JVMState has a null method.
    assert(map()->memory()->Opcode() == Op_Parm, "");
    // Insert the memory aliasing node
    set_all_memory(reset_memory());
  }
  assert(merged_memory(), "");


  switch (intrinsic_id()) {
  case vmIntrinsics::_hashCode:                 return inline_native_hashcode(intrinsic()->is_virtual(), !is_static);
  case vmIntrinsics::_identityHashCode:         return inline_native_hashcode(/*!virtual*/ false,         is_static);
  case vmIntrinsics::_getClass:                 return inline_native_getClass();

  case vmIntrinsics::_dsin:
  case vmIntrinsics::_dcos:
  case vmIntrinsics::_dtan:
  case vmIntrinsics::_dabs:
  case vmIntrinsics::_datan2:
  case vmIntrinsics::_dsqrt:
  case vmIntrinsics::_dexp:
  case vmIntrinsics::_dlog:
  case vmIntrinsics::_dlog10:
  case vmIntrinsics::_dpow:                     return inline_math_native(intrinsic_id());

  case vmIntrinsics::_min:
  case vmIntrinsics::_max:                      return inline_min_max(intrinsic_id());

  case vmIntrinsics::_arraycopy:                return inline_arraycopy();

  case vmIntrinsics::_compareTo:                return inline_string_compareTo();
  case vmIntrinsics::_indexOf:                  return inline_string_indexOf();
  case vmIntrinsics::_equals:                   return inline_string_equals();

  case vmIntrinsics::_getObject:                return inline_unsafe_access(!is_native_ptr, !is_store, T_OBJECT,  !is_volatile);
  case vmIntrinsics::_getBoolean:               return inline_unsafe_access(!is_native_ptr, !is_store, T_BOOLEAN, !is_volatile);
  case vmIntrinsics::_getByte:                  return inline_unsafe_access(!is_native_ptr, !is_store, T_BYTE,    !is_volatile);
  case vmIntrinsics::_getShort:                 return inline_unsafe_access(!is_native_ptr, !is_store, T_SHORT,   !is_volatile);
  case vmIntrinsics::_getChar:                  return inline_unsafe_access(!is_native_ptr, !is_store, T_CHAR,    !is_volatile);
  case vmIntrinsics::_getInt:                   return inline_unsafe_access(!is_native_ptr, !is_store, T_INT,     !is_volatile);
  case vmIntrinsics::_getLong:                  return inline_unsafe_access(!is_native_ptr, !is_store, T_LONG,    !is_volatile);
  case vmIntrinsics::_getFloat:                 return inline_unsafe_access(!is_native_ptr, !is_store, T_FLOAT,   !is_volatile);
  case vmIntrinsics::_getDouble:                return inline_unsafe_access(!is_native_ptr, !is_store, T_DOUBLE,  !is_volatile);

  case vmIntrinsics::_putObject:                return inline_unsafe_access(!is_native_ptr,  is_store, T_OBJECT,  !is_volatile);
  case vmIntrinsics::_putBoolean:               return inline_unsafe_access(!is_native_ptr,  is_store, T_BOOLEAN, !is_volatile);
  case vmIntrinsics::_putByte:                  return inline_unsafe_access(!is_native_ptr,  is_store, T_BYTE,    !is_volatile);
  case vmIntrinsics::_putShort:                 return inline_unsafe_access(!is_native_ptr,  is_store, T_SHORT,   !is_volatile);
  case vmIntrinsics::_putChar:                  return inline_unsafe_access(!is_native_ptr,  is_store, T_CHAR,    !is_volatile);
  case vmIntrinsics::_putInt:                   return inline_unsafe_access(!is_native_ptr,  is_store, T_INT,     !is_volatile);
  case vmIntrinsics::_putLong:                  return inline_unsafe_access(!is_native_ptr,  is_store, T_LONG,    !is_volatile);
  case vmIntrinsics::_putFloat:                 return inline_unsafe_access(!is_native_ptr,  is_store, T_FLOAT,   !is_volatile);
  case vmIntrinsics::_putDouble:                return inline_unsafe_access(!is_native_ptr,  is_store, T_DOUBLE,  !is_volatile);

  case vmIntrinsics::_getByte_raw:              return inline_unsafe_access( is_native_ptr, !is_store, T_BYTE,    !is_volatile);
  case vmIntrinsics::_getShort_raw:             return inline_unsafe_access( is_native_ptr, !is_store, T_SHORT,   !is_volatile);
  case vmIntrinsics::_getChar_raw:              return inline_unsafe_access( is_native_ptr, !is_store, T_CHAR,    !is_volatile);
  case vmIntrinsics::_getInt_raw:               return inline_unsafe_access( is_native_ptr, !is_store, T_INT,     !is_volatile);
  case vmIntrinsics::_getLong_raw:              return inline_unsafe_access( is_native_ptr, !is_store, T_LONG,    !is_volatile);
  case vmIntrinsics::_getFloat_raw:             return inline_unsafe_access( is_native_ptr, !is_store, T_FLOAT,   !is_volatile);
  case vmIntrinsics::_getDouble_raw:            return inline_unsafe_access( is_native_ptr, !is_store, T_DOUBLE,  !is_volatile);
  case vmIntrinsics::_getAddress_raw:           return inline_unsafe_access( is_native_ptr, !is_store, T_ADDRESS, !is_volatile);

  case vmIntrinsics::_putByte_raw:              return inline_unsafe_access( is_native_ptr,  is_store, T_BYTE,    !is_volatile);
  case vmIntrinsics::_putShort_raw:             return inline_unsafe_access( is_native_ptr,  is_store, T_SHORT,   !is_volatile);
  case vmIntrinsics::_putChar_raw:              return inline_unsafe_access( is_native_ptr,  is_store, T_CHAR,    !is_volatile);
  case vmIntrinsics::_putInt_raw:               return inline_unsafe_access( is_native_ptr,  is_store, T_INT,     !is_volatile);
  case vmIntrinsics::_putLong_raw:              return inline_unsafe_access( is_native_ptr,  is_store, T_LONG,    !is_volatile);
  case vmIntrinsics::_putFloat_raw:             return inline_unsafe_access( is_native_ptr,  is_store, T_FLOAT,   !is_volatile);
  case vmIntrinsics::_putDouble_raw:            return inline_unsafe_access( is_native_ptr,  is_store, T_DOUBLE,  !is_volatile);
  case vmIntrinsics::_putAddress_raw:           return inline_unsafe_access( is_native_ptr,  is_store, T_ADDRESS, !is_volatile);

  case vmIntrinsics::_getObjectVolatile:        return inline_unsafe_access(!is_native_ptr, !is_store, T_OBJECT,   is_volatile);
  case vmIntrinsics::_getBooleanVolatile:       return inline_unsafe_access(!is_native_ptr, !is_store, T_BOOLEAN,  is_volatile);
  case vmIntrinsics::_getByteVolatile:          return inline_unsafe_access(!is_native_ptr, !is_store, T_BYTE,     is_volatile);
  case vmIntrinsics::_getShortVolatile:         return inline_unsafe_access(!is_native_ptr, !is_store, T_SHORT,    is_volatile);
  case vmIntrinsics::_getCharVolatile:          return inline_unsafe_access(!is_native_ptr, !is_store, T_CHAR,     is_volatile);
  case vmIntrinsics::_getIntVolatile:           return inline_unsafe_access(!is_native_ptr, !is_store, T_INT,      is_volatile);
  case vmIntrinsics::_getLongVolatile:          return inline_unsafe_access(!is_native_ptr, !is_store, T_LONG,     is_volatile);
  case vmIntrinsics::_getFloatVolatile:         return inline_unsafe_access(!is_native_ptr, !is_store, T_FLOAT,    is_volatile);
  case vmIntrinsics::_getDoubleVolatile:        return inline_unsafe_access(!is_native_ptr, !is_store, T_DOUBLE,   is_volatile);

  case vmIntrinsics::_putObjectVolatile:        return inline_unsafe_access(!is_native_ptr,  is_store, T_OBJECT,   is_volatile);
  case vmIntrinsics::_putBooleanVolatile:       return inline_unsafe_access(!is_native_ptr,  is_store, T_BOOLEAN,  is_volatile);
  case vmIntrinsics::_putByteVolatile:          return inline_unsafe_access(!is_native_ptr,  is_store, T_BYTE,     is_volatile);
  case vmIntrinsics::_putShortVolatile:         return inline_unsafe_access(!is_native_ptr,  is_store, T_SHORT,    is_volatile);
  case vmIntrinsics::_putCharVolatile:          return inline_unsafe_access(!is_native_ptr,  is_store, T_CHAR,     is_volatile);
  case vmIntrinsics::_putIntVolatile:           return inline_unsafe_access(!is_native_ptr,  is_store, T_INT,      is_volatile);
  case vmIntrinsics::_putLongVolatile:          return inline_unsafe_access(!is_native_ptr,  is_store, T_LONG,     is_volatile);
  case vmIntrinsics::_putFloatVolatile:         return inline_unsafe_access(!is_native_ptr,  is_store, T_FLOAT,    is_volatile);
  case vmIntrinsics::_putDoubleVolatile:        return inline_unsafe_access(!is_native_ptr,  is_store, T_DOUBLE,   is_volatile);

  case vmIntrinsics::_prefetchRead:             return inline_unsafe_prefetch(!is_native_ptr, !is_store, !is_static);
  case vmIntrinsics::_prefetchWrite:            return inline_unsafe_prefetch(!is_native_ptr,  is_store, !is_static);
  case vmIntrinsics::_prefetchReadStatic:       return inline_unsafe_prefetch(!is_native_ptr, !is_store,  is_static);
  case vmIntrinsics::_prefetchWriteStatic:      return inline_unsafe_prefetch(!is_native_ptr,  is_store,  is_static);

  case vmIntrinsics::_compareAndSwapObject:     return inline_unsafe_load_store(T_OBJECT, LS_cmpxchg);
  case vmIntrinsics::_compareAndSwapInt:        return inline_unsafe_load_store(T_INT,    LS_cmpxchg);
  case vmIntrinsics::_compareAndSwapLong:       return inline_unsafe_load_store(T_LONG,   LS_cmpxchg);

  case vmIntrinsics::_putOrderedObject:         return inline_unsafe_ordered_store(T_OBJECT);
  case vmIntrinsics::_putOrderedInt:            return inline_unsafe_ordered_store(T_INT);
  case vmIntrinsics::_putOrderedLong:           return inline_unsafe_ordered_store(T_LONG);

  case vmIntrinsics::_getAndAddInt:             return inline_unsafe_load_store(T_INT,    LS_xadd);
  case vmIntrinsics::_getAndAddLong:            return inline_unsafe_load_store(T_LONG,   LS_xadd);
  case vmIntrinsics::_getAndSetInt:             return inline_unsafe_load_store(T_INT,    LS_xchg);
  case vmIntrinsics::_getAndSetLong:            return inline_unsafe_load_store(T_LONG,   LS_xchg);
  case vmIntrinsics::_getAndSetObject:          return inline_unsafe_load_store(T_OBJECT, LS_xchg);

  case vmIntrinsics::_loadFence:
  case vmIntrinsics::_storeFence:
  case vmIntrinsics::_fullFence:                return inline_unsafe_fence(intrinsic_id());

  case vmIntrinsics::_currentThread:            return inline_native_currentThread();
  case vmIntrinsics::_isInterrupted:            return inline_native_isInterrupted();

#ifdef TRACE_HAVE_INTRINSICS
  case vmIntrinsics::_classID:                  return inline_native_classID();
  case vmIntrinsics::_threadID:                 return inline_native_threadID();
  case vmIntrinsics::_counterTime:              return inline_native_time_funcs(CAST_FROM_FN_PTR(address, TRACE_TIME_METHOD), "counterTime");
#endif
  case vmIntrinsics::_currentTimeMillis:        return inline_native_time_funcs(CAST_FROM_FN_PTR(address, os::javaTimeMillis), "currentTimeMillis");
  case vmIntrinsics::_nanoTime:                 return inline_native_time_funcs(CAST_FROM_FN_PTR(address, os::javaTimeNanos), "nanoTime");
  case vmIntrinsics::_allocateInstance:         return inline_unsafe_allocate();
  case vmIntrinsics::_copyMemory:               return inline_unsafe_copyMemory();
  case vmIntrinsics::_newArray:                 return inline_native_newArray();
  case vmIntrinsics::_getLength:                return inline_native_getLength();
  case vmIntrinsics::_copyOf:                   return inline_array_copyOf(false);
  case vmIntrinsics::_copyOfRange:              return inline_array_copyOf(true);
  case vmIntrinsics::_equalsC:                  return inline_array_equals();
  case vmIntrinsics::_clone:                    return inline_native_clone(intrinsic()->is_virtual());

  case vmIntrinsics::_isAssignableFrom:         return inline_native_subtype_check();

  case vmIntrinsics::_isInstance:
  case vmIntrinsics::_getModifiers:
  case vmIntrinsics::_isInterface:
  case vmIntrinsics::_isArray:
  case vmIntrinsics::_isPrimitive:
  case vmIntrinsics::_getSuperclass:
  case vmIntrinsics::_getComponentType:
  case vmIntrinsics::_getClassAccessFlags:      return inline_native_Class_query(intrinsic_id());

  case vmIntrinsics::_floatToRawIntBits:
  case vmIntrinsics::_floatToIntBits:
  case vmIntrinsics::_intBitsToFloat:
  case vmIntrinsics::_doubleToRawLongBits:
  case vmIntrinsics::_doubleToLongBits:
  case vmIntrinsics::_longBitsToDouble:         return inline_fp_conversions(intrinsic_id());

  case vmIntrinsics::_numberOfLeadingZeros_i:
  case vmIntrinsics::_numberOfLeadingZeros_l:
  case vmIntrinsics::_numberOfTrailingZeros_i:
  case vmIntrinsics::_numberOfTrailingZeros_l:
  case vmIntrinsics::_bitCount_i:
  case vmIntrinsics::_bitCount_l:
  case vmIntrinsics::_reverseBytes_i:
  case vmIntrinsics::_reverseBytes_l:
  case vmIntrinsics::_reverseBytes_s:
  case vmIntrinsics::_reverseBytes_c:           return inline_number_methods(intrinsic_id());

  case vmIntrinsics::_getCallerClass:           return inline_native_Reflection_getCallerClass();

  case vmIntrinsics::_Reference_get:            return inline_reference_get();

  case vmIntrinsics::_aescrypt_encryptBlock:
  case vmIntrinsics::_aescrypt_decryptBlock:    return inline_aescrypt_Block(intrinsic_id());

  case vmIntrinsics::_cipherBlockChaining_encryptAESCrypt:
  case vmIntrinsics::_cipherBlockChaining_decryptAESCrypt:
    return inline_cipherBlockChaining_AESCrypt(intrinsic_id());

  case vmIntrinsics::_encodeISOArray:
    return inline_encodeISOArray();

  case vmIntrinsics::_updateCRC32:
    return inline_updateCRC32();
  case vmIntrinsics::_updateBytesCRC32:
    return inline_updateBytesCRC32();
  case vmIntrinsics::_updateByteBufferCRC32:
    return inline_updateByteBufferCRC32();

  default:
    // If you get here, it may be that someone has added a new intrinsic
    // to the list in vmSymbols.hpp without implementing it here.
#ifndef PRODUCT
    if ((PrintMiscellaneous && (Verbose || WizardMode)) || PrintOpto) {
      tty->print_cr("*** Warning: Unimplemented intrinsic %s(%d)",
                    vmIntrinsics::name_at(intrinsic_id()), intrinsic_id());
    }
#endif
    return false;
  }
}

Node* LibraryCallKit::try_to_predicate() {
  if (!jvms()->has_method()) {
    // Root JVMState has a null method.
    assert(map()->memory()->Opcode() == Op_Parm, "");
    // Insert the memory aliasing node
    set_all_memory(reset_memory());
  }
  assert(merged_memory(), "");

  switch (intrinsic_id()) {
  case vmIntrinsics::_cipherBlockChaining_encryptAESCrypt:
    return inline_cipherBlockChaining_AESCrypt_predicate(false);
  case vmIntrinsics::_cipherBlockChaining_decryptAESCrypt:
    return inline_cipherBlockChaining_AESCrypt_predicate(true);

  default:
    // If you get here, it may be that someone has added a new intrinsic
    // to the list in vmSymbols.hpp without implementing it here.
#ifndef PRODUCT
    if ((PrintMiscellaneous && (Verbose || WizardMode)) || PrintOpto) {
      tty->print_cr("*** Warning: Unimplemented predicate for intrinsic %s(%d)",
                    vmIntrinsics::name_at(intrinsic_id()), intrinsic_id());
    }
#endif
    Node* slow_ctl = control();
    set_control(top()); // No fast path instrinsic
    return slow_ctl;
  }
}

//------------------------------set_result-------------------------------
// Helper function for finishing intrinsics.
void LibraryCallKit::set_result(RegionNode* region, PhiNode* value) {
  record_for_igvn(region);
  set_control(_gvn.transform(region));
  set_result( _gvn.transform(value));
  assert(value->type()->basic_type() == result()->bottom_type()->basic_type(), "sanity");
}

//------------------------------generate_guard---------------------------
// Helper function for generating guarded fast-slow graph structures.
// The given 'test', if true, guards a slow path.  If the test fails
// then a fast path can be taken.  (We generally hope it fails.)
// In all cases, GraphKit::control() is updated to the fast path.
// The returned value represents the control for the slow path.
// The return value is never 'top'; it is either a valid control
// or NULL if it is obvious that the slow path can never be taken.
// Also, if region and the slow control are not NULL, the slow edge
// is appended to the region.
Node* LibraryCallKit::generate_guard(Node* test, RegionNode* region, float true_prob) {
  if (stopped()) {
    // Already short circuited.
    return NULL;
  }

  // Build an if node and its projections.
  // If test is true we take the slow path, which we assume is uncommon.
  if (_gvn.type(test) == TypeInt::ZERO) {
    // The slow branch is never taken.  No need to build this guard.
    return NULL;
  }

  IfNode* iff = create_and_map_if(control(), test, true_prob, COUNT_UNKNOWN);

  Node* if_slow = _gvn.transform(new (C) IfTrueNode(iff));
  if (if_slow == top()) {
    // The slow branch is never taken.  No need to build this guard.
    return NULL;
  }

  if (region != NULL)
    region->add_req(if_slow);

  Node* if_fast = _gvn.transform(new (C) IfFalseNode(iff));
  set_control(if_fast);

  return if_slow;
}

inline Node* LibraryCallKit::generate_slow_guard(Node* test, RegionNode* region) {
  return generate_guard(test, region, PROB_UNLIKELY_MAG(3));
}
inline Node* LibraryCallKit::generate_fair_guard(Node* test, RegionNode* region) {
  return generate_guard(test, region, PROB_FAIR);
}

inline Node* LibraryCallKit::generate_negative_guard(Node* index, RegionNode* region,
                                                     Node* *pos_index) {
  if (stopped())
    return NULL;                // already stopped
  if (_gvn.type(index)->higher_equal(TypeInt::POS)) // [0,maxint]
    return NULL;                // index is already adequately typed
  Node* cmp_lt = _gvn.transform(new (C) CmpINode(index, intcon(0)));
  Node* bol_lt = _gvn.transform(new (C) BoolNode(cmp_lt, BoolTest::lt));
  Node* is_neg = generate_guard(bol_lt, region, PROB_MIN);
  if (is_neg != NULL && pos_index != NULL) {
    // Emulate effect of Parse::adjust_map_after_if.
    Node* ccast = new (C) CastIINode(index, TypeInt::POS);
    ccast->set_req(0, control());
    (*pos_index) = _gvn.transform(ccast);
  }
  return is_neg;
}

inline Node* LibraryCallKit::generate_nonpositive_guard(Node* index, bool never_negative,
                                                        Node* *pos_index) {
  if (stopped())
    return NULL;                // already stopped
  if (_gvn.type(index)->higher_equal(TypeInt::POS1)) // [1,maxint]
    return NULL;                // index is already adequately typed
  Node* cmp_le = _gvn.transform(new (C) CmpINode(index, intcon(0)));
  BoolTest::mask le_or_eq = (never_negative ? BoolTest::eq : BoolTest::le);
  Node* bol_le = _gvn.transform(new (C) BoolNode(cmp_le, le_or_eq));
  Node* is_notp = generate_guard(bol_le, NULL, PROB_MIN);
  if (is_notp != NULL && pos_index != NULL) {
    // Emulate effect of Parse::adjust_map_after_if.
    Node* ccast = new (C) CastIINode(index, TypeInt::POS1);
    ccast->set_req(0, control());
    (*pos_index) = _gvn.transform(ccast);
  }
  return is_notp;
}

// Make sure that 'position' is a valid limit index, in [0..length].
// There are two equivalent plans for checking this:
//   A. (offset + copyLength)  unsigned<=  arrayLength
//   B. offset  <=  (arrayLength - copyLength)
// We require that all of the values above, except for the sum and
// difference, are already known to be non-negative.
// Plan A is robust in the face of overflow, if offset and copyLength
// are both hugely positive.
//
// Plan B is less direct and intuitive, but it does not overflow at
// all, since the difference of two non-negatives is always
// representable.  Whenever Java methods must perform the equivalent
// check they generally use Plan B instead of Plan A.
// For the moment we use Plan A.
inline Node* LibraryCallKit::generate_limit_guard(Node* offset,
                                                  Node* subseq_length,
                                                  Node* array_length,
                                                  RegionNode* region) {
  if (stopped())
    return NULL;                // already stopped
  bool zero_offset = _gvn.type(offset) == TypeInt::ZERO;
  if (zero_offset && subseq_length->eqv_uncast(array_length))
    return NULL;                // common case of whole-array copy
  Node* last = subseq_length;
  if (!zero_offset)             // last += offset
    last = _gvn.transform(new (C) AddINode(last, offset));
  Node* cmp_lt = _gvn.transform(new (C) CmpUNode(array_length, last));
  Node* bol_lt = _gvn.transform(new (C) BoolNode(cmp_lt, BoolTest::lt));
  Node* is_over = generate_guard(bol_lt, region, PROB_MIN);
  return is_over;
}


//--------------------------generate_current_thread--------------------
Node* LibraryCallKit::generate_current_thread(Node* &tls_output) {
  ciKlass*    thread_klass = env()->Thread_klass();
  const Type* thread_type  = TypeOopPtr::make_from_klass(thread_klass)->cast_to_ptr_type(TypePtr::NotNull);
  Node* thread = _gvn.transform(new (C) ThreadLocalNode());
  Node* p = basic_plus_adr(top()/*!oop*/, thread, in_bytes(JavaThread::threadObj_offset()));
  Node* threadObj = make_load(NULL, p, thread_type, T_OBJECT);
  tls_output = thread;
  return threadObj;
}


//------------------------------make_string_method_node------------------------
// Helper method for String intrinsic functions. This version is called
// with str1 and str2 pointing to String object nodes.
//
Node* LibraryCallKit::make_string_method_node(int opcode, Node* str1, Node* str2) {
  Node* no_ctrl = NULL;

  // Get start addr of string
  Node* str1_value   = load_String_value(no_ctrl, str1);
  Node* str1_offset  = load_String_offset(no_ctrl, str1);
  Node* str1_start   = array_element_address(str1_value, str1_offset, T_CHAR);

  // Get length of string 1
  Node* str1_len  = load_String_length(no_ctrl, str1);

  Node* str2_value   = load_String_value(no_ctrl, str2);
  Node* str2_offset  = load_String_offset(no_ctrl, str2);
  Node* str2_start   = array_element_address(str2_value, str2_offset, T_CHAR);

  Node* str2_len = NULL;
  Node* result = NULL;

  switch (opcode) {
  case Op_StrIndexOf:
    // Get length of string 2
    str2_len = load_String_length(no_ctrl, str2);

    result = new (C) StrIndexOfNode(control(), memory(TypeAryPtr::CHARS),
                                 str1_start, str1_len, str2_start, str2_len);
    break;
  case Op_StrComp:
    // Get length of string 2
    str2_len = load_String_length(no_ctrl, str2);

    result = new (C) StrCompNode(control(), memory(TypeAryPtr::CHARS),
                                 str1_start, str1_len, str2_start, str2_len);
    break;
  case Op_StrEquals:
    result = new (C) StrEqualsNode(control(), memory(TypeAryPtr::CHARS),
                               str1_start, str2_start, str1_len);
    break;
  default:
    ShouldNotReachHere();
    return NULL;
  }

  // All these intrinsics have checks.
  C->set_has_split_ifs(true); // Has chance for split-if optimization

  return _gvn.transform(result);
}

// Helper method for String intrinsic functions. This version is called
// with str1 and str2 pointing to char[] nodes, with cnt1 and cnt2 pointing
// to Int nodes containing the lenghts of str1 and str2.
//
Node* LibraryCallKit::make_string_method_node(int opcode, Node* str1_start, Node* cnt1, Node* str2_start, Node* cnt2) {
  Node* result = NULL;
  switch (opcode) {
  case Op_StrIndexOf:
    result = new (C) StrIndexOfNode(control(), memory(TypeAryPtr::CHARS),
                                 str1_start, cnt1, str2_start, cnt2);
    break;
  case Op_StrComp:
    result = new (C) StrCompNode(control(), memory(TypeAryPtr::CHARS),
                                 str1_start, cnt1, str2_start, cnt2);
    break;
  case Op_StrEquals:
    result = new (C) StrEqualsNode(control(), memory(TypeAryPtr::CHARS),
                                 str1_start, str2_start, cnt1);
    break;
  default:
    ShouldNotReachHere();
    return NULL;
  }

  // All these intrinsics have checks.
  C->set_has_split_ifs(true); // Has chance for split-if optimization

  return _gvn.transform(result);
}

//------------------------------inline_string_compareTo------------------------
// public int java.lang.String.compareTo(String anotherString);
bool LibraryCallKit::inline_string_compareTo() {
  Node* receiver = null_check(argument(0));
  Node* arg      = null_check(argument(1));
  if (stopped()) {
    return true;
  }
  set_result(make_string_method_node(Op_StrComp, receiver, arg));
  return true;
}

//------------------------------inline_string_equals------------------------
bool LibraryCallKit::inline_string_equals() {
  Node* receiver = null_check_receiver();
  // NOTE: Do not null check argument for String.equals() because spec
  // allows to specify NULL as argument.
  Node* argument = this->argument(1);
  if (stopped()) {
    return true;
  }

  // paths (plus control) merge
  RegionNode* region = new (C) RegionNode(5);
  Node* phi = new (C) PhiNode(region, TypeInt::BOOL);

  // does source == target string?
  Node* cmp = _gvn.transform(new (C) CmpPNode(receiver, argument));
  Node* bol = _gvn.transform(new (C) BoolNode(cmp, BoolTest::eq));

  Node* if_eq = generate_slow_guard(bol, NULL);
  if (if_eq != NULL) {
    // receiver == argument
    phi->init_req(2, intcon(1));
    region->init_req(2, if_eq);
  }

  // get String klass for instanceOf
  ciInstanceKlass* klass = env()->String_klass();

  if (!stopped()) {
    Node* inst = gen_instanceof(argument, makecon(TypeKlassPtr::make(klass)));
    Node* cmp  = _gvn.transform(new (C) CmpINode(inst, intcon(1)));
    Node* bol  = _gvn.transform(new (C) BoolNode(cmp, BoolTest::ne));

    Node* inst_false = generate_guard(bol, NULL, PROB_MIN);
    //instanceOf == true, fallthrough

    if (inst_false != NULL) {
      phi->init_req(3, intcon(0));
      region->init_req(3, inst_false);
    }
  }

  if (!stopped()) {
    const TypeOopPtr* string_type = TypeOopPtr::make_from_klass(klass);

    // Properly cast the argument to String
    argument = _gvn.transform(new (C) CheckCastPPNode(control(), argument, string_type));
    // This path is taken only when argument's type is String:NotNull.
    argument = cast_not_null(argument, false);

    Node* no_ctrl = NULL;

    // Get start addr of receiver
    Node* receiver_val    = load_String_value(no_ctrl, receiver);
    Node* receiver_offset = load_String_offset(no_ctrl, receiver);
    Node* receiver_start = array_element_address(receiver_val, receiver_offset, T_CHAR);

    // Get length of receiver
    Node* receiver_cnt  = load_String_length(no_ctrl, receiver);

    // Get start addr of argument
    Node* argument_val    = load_String_value(no_ctrl, argument);
    Node* argument_offset = load_String_offset(no_ctrl, argument);
    Node* argument_start = array_element_address(argument_val, argument_offset, T_CHAR);

    // Get length of argument
    Node* argument_cnt  = load_String_length(no_ctrl, argument);

    // Check for receiver count != argument count
    Node* cmp = _gvn.transform(new(C) CmpINode(receiver_cnt, argument_cnt));
    Node* bol = _gvn.transform(new(C) BoolNode(cmp, BoolTest::ne));
    Node* if_ne = generate_slow_guard(bol, NULL);
    if (if_ne != NULL) {
      phi->init_req(4, intcon(0));
      region->init_req(4, if_ne);
    }

    // Check for count == 0 is done by assembler code for StrEquals.

    if (!stopped()) {
      Node* equals = make_string_method_node(Op_StrEquals, receiver_start, receiver_cnt, argument_start, argument_cnt);
      phi->init_req(1, equals);
      region->init_req(1, control());
    }
  }

  // post merge
  set_control(_gvn.transform(region));
  record_for_igvn(region);

  set_result(_gvn.transform(phi));
  return true;
}

//------------------------------inline_array_equals----------------------------
bool LibraryCallKit::inline_array_equals() {
  Node* arg1 = argument(0);
  Node* arg2 = argument(1);
  set_result(_gvn.transform(new (C) AryEqNode(control(), memory(TypeAryPtr::CHARS), arg1, arg2)));
  return true;
}

// Java version of String.indexOf(constant string)
// class StringDecl {
//   StringDecl(char[] ca) {
//     offset = 0;
//     count = ca.length;
//     value = ca;
//   }
//   int offset;
//   int count;
//   char[] value;
// }
//
// static int string_indexOf_J(StringDecl string_object, char[] target_object,
//                             int targetOffset, int cache_i, int md2) {
//   int cache = cache_i;
//   int sourceOffset = string_object.offset;
//   int sourceCount = string_object.count;
//   int targetCount = target_object.length;
//
//   int targetCountLess1 = targetCount - 1;
//   int sourceEnd = sourceOffset + sourceCount - targetCountLess1;
//
//   char[] source = string_object.value;
//   char[] target = target_object;
//   int lastChar = target[targetCountLess1];
//
//  outer_loop:
//   for (int i = sourceOffset; i < sourceEnd; ) {
//     int src = source[i + targetCountLess1];
//     if (src == lastChar) {
//       // With random strings and a 4-character alphabet,
//       // reverse matching at this point sets up 0.8% fewer
//       // frames, but (paradoxically) makes 0.3% more probes.
//       // Since those probes are nearer the lastChar probe,
//       // there is may be a net D$ win with reverse matching.
//       // But, reversing loop inhibits unroll of inner loop
//       // for unknown reason.  So, does running outer loop from
//       // (sourceOffset - targetCountLess1) to (sourceOffset + sourceCount)
//       for (int j = 0; j < targetCountLess1; j++) {
//         if (target[targetOffset + j] != source[i+j]) {
//           if ((cache & (1 << source[i+j])) == 0) {
//             if (md2 < j+1) {
//               i += j+1;
//               continue outer_loop;
//             }
//           }
//           i += md2;
//           continue outer_loop;
//         }
//       }
//       return i - sourceOffset;
//     }
//     if ((cache & (1 << src)) == 0) {
//       i += targetCountLess1;
//     } // using "i += targetCount;" and an "else i++;" causes a jump to jump.
//     i++;
//   }
//   return -1;
// }

//------------------------------string_indexOf------------------------
Node* LibraryCallKit::string_indexOf(Node* string_object, ciTypeArray* target_array, jint targetOffset_i,
                                     jint cache_i, jint md2_i) {

  Node* no_ctrl  = NULL;
  float likely   = PROB_LIKELY(0.9);
  float unlikely = PROB_UNLIKELY(0.9);

  const int nargs = 0; // no arguments to push back for uncommon trap in predicate

  Node* source        = load_String_value(no_ctrl, string_object);
  Node* sourceOffset  = load_String_offset(no_ctrl, string_object);
  Node* sourceCount   = load_String_length(no_ctrl, string_object);

  Node* target = _gvn.transform( makecon(TypeOopPtr::make_from_constant(target_array, true)));
  jint target_length = target_array->length();
  const TypeAry* target_array_type = TypeAry::make(TypeInt::CHAR, TypeInt::make(0, target_length, Type::WidenMin));
  const TypeAryPtr* target_type = TypeAryPtr::make(TypePtr::BotPTR, target_array_type, target_array->klass(), true, Type::OffsetBot);

  IdealKit kit(this, false, true);
#define __ kit.
  Node* zero             = __ ConI(0);
  Node* one              = __ ConI(1);
  Node* cache            = __ ConI(cache_i);
  Node* md2              = __ ConI(md2_i);
  Node* lastChar         = __ ConI(target_array->char_at(target_length - 1));
  Node* targetCount      = __ ConI(target_length);
  Node* targetCountLess1 = __ ConI(target_length - 1);
  Node* targetOffset     = __ ConI(targetOffset_i);
  Node* sourceEnd        = __ SubI(__ AddI(sourceOffset, sourceCount), targetCountLess1);

  IdealVariable rtn(kit), i(kit), j(kit); __ declarations_done();
  Node* outer_loop = __ make_label(2 /* goto */);
  Node* return_    = __ make_label(1);

  __ set(rtn,__ ConI(-1));
  __ loop(this, nargs, i, sourceOffset, BoolTest::lt, sourceEnd); {
       Node* i2  = __ AddI(__ value(i), targetCountLess1);
       // pin to prohibit loading of "next iteration" value which may SEGV (rare)
       Node* src = load_array_element(__ ctrl(), source, i2, TypeAryPtr::CHARS);
       __ if_then(src, BoolTest::eq, lastChar, unlikely); {
         __ loop(this, nargs, j, zero, BoolTest::lt, targetCountLess1); {
              Node* tpj = __ AddI(targetOffset, __ value(j));
              Node* targ = load_array_element(no_ctrl, target, tpj, target_type);
              Node* ipj  = __ AddI(__ value(i), __ value(j));
              Node* src2 = load_array_element(no_ctrl, source, ipj, TypeAryPtr::CHARS);
              __ if_then(targ, BoolTest::ne, src2); {
                __ if_then(__ AndI(cache, __ LShiftI(one, src2)), BoolTest::eq, zero); {
                  __ if_then(md2, BoolTest::lt, __ AddI(__ value(j), one)); {
                    __ increment(i, __ AddI(__ value(j), one));
                    __ goto_(outer_loop);
                  } __ end_if(); __ dead(j);
                }__ end_if(); __ dead(j);
                __ increment(i, md2);
                __ goto_(outer_loop);
              }__ end_if();
              __ increment(j, one);
         }__ end_loop(); __ dead(j);
         __ set(rtn, __ SubI(__ value(i), sourceOffset)); __ dead(i);
         __ goto_(return_);
       }__ end_if();
       __ if_then(__ AndI(cache, __ LShiftI(one, src)), BoolTest::eq, zero, likely); {
         __ increment(i, targetCountLess1);
       }__ end_if();
       __ increment(i, one);
       __ bind(outer_loop);
  }__ end_loop(); __ dead(i);
  __ bind(return_);

  // Final sync IdealKit and GraphKit.
  final_sync(kit);
  Node* result = __ value(rtn);
#undef __
  C->set_has_loops(true);
  return result;
}

//------------------------------inline_string_indexOf------------------------
bool LibraryCallKit::inline_string_indexOf() {
  Node* receiver = argument(0);
  Node* arg      = argument(1);

  Node* result;
  // Disable the use of pcmpestri until it can be guaranteed that
  // the load doesn't cross into the uncommited space.
  if (Matcher::has_match_rule(Op_StrIndexOf) &&
      UseSSE42Intrinsics) {
    // Generate SSE4.2 version of indexOf
    // We currently only have match rules that use SSE4.2

    receiver = null_check(receiver);
    arg      = null_check(arg);
    if (stopped()) {
      return true;
    }

    ciInstanceKlass* str_klass = env()->String_klass();
    const TypeOopPtr* string_type = TypeOopPtr::make_from_klass(str_klass);

    // Make the merge point
    RegionNode* result_rgn = new (C) RegionNode(4);
    Node*       result_phi = new (C) PhiNode(result_rgn, TypeInt::INT);
    Node* no_ctrl  = NULL;

    // Get start addr of source string
    Node* source = load_String_value(no_ctrl, receiver);
    Node* source_offset = load_String_offset(no_ctrl, receiver);
    Node* source_start = array_element_address(source, source_offset, T_CHAR);

    // Get length of source string
    Node* source_cnt  = load_String_length(no_ctrl, receiver);

    // Get start addr of substring
    Node* substr = load_String_value(no_ctrl, arg);
    Node* substr_offset = load_String_offset(no_ctrl, arg);
    Node* substr_start = array_element_address(substr, substr_offset, T_CHAR);

    // Get length of source string
    Node* substr_cnt  = load_String_length(no_ctrl, arg);

    // Check for substr count > string count
    Node* cmp = _gvn.transform(new(C) CmpINode(substr_cnt, source_cnt));
    Node* bol = _gvn.transform(new(C) BoolNode(cmp, BoolTest::gt));
    Node* if_gt = generate_slow_guard(bol, NULL);
    if (if_gt != NULL) {
      result_phi->init_req(2, intcon(-1));
      result_rgn->init_req(2, if_gt);
    }

    if (!stopped()) {
      // Check for substr count == 0
      cmp = _gvn.transform(new(C) CmpINode(substr_cnt, intcon(0)));
      bol = _gvn.transform(new(C) BoolNode(cmp, BoolTest::eq));
      Node* if_zero = generate_slow_guard(bol, NULL);
      if (if_zero != NULL) {
        result_phi->init_req(3, intcon(0));
        result_rgn->init_req(3, if_zero);
      }
    }

    if (!stopped()) {
      result = make_string_method_node(Op_StrIndexOf, source_start, source_cnt, substr_start, substr_cnt);
      result_phi->init_req(1, result);
      result_rgn->init_req(1, control());
    }
    set_control(_gvn.transform(result_rgn));
    record_for_igvn(result_rgn);
    result = _gvn.transform(result_phi);

  } else { // Use LibraryCallKit::string_indexOf
    // don't intrinsify if argument isn't a constant string.
    if (!arg->is_Con()) {
     return false;
    }
    const TypeOopPtr* str_type = _gvn.type(arg)->isa_oopptr();
    if (str_type == NULL) {
      return false;
    }
    ciInstanceKlass* klass = env()->String_klass();
    ciObject* str_const = str_type->const_oop();
    if (str_const == NULL || str_const->klass() != klass) {
      return false;
    }
    ciInstance* str = str_const->as_instance();
    assert(str != NULL, "must be instance");

    ciObject* v = str->field_value_by_offset(java_lang_String::value_offset_in_bytes()).as_object();
    ciTypeArray* pat = v->as_type_array(); // pattern (argument) character array

    int o;
    int c;
    if (java_lang_String::has_offset_field()) {
      o = str->field_value_by_offset(java_lang_String::offset_offset_in_bytes()).as_int();
      c = str->field_value_by_offset(java_lang_String::count_offset_in_bytes()).as_int();
    } else {
      o = 0;
      c = pat->length();
    }

    // constant strings have no offset and count == length which
    // simplifies the resulting code somewhat so lets optimize for that.
    if (o != 0 || c != pat->length()) {
     return false;
    }

    receiver = null_check(receiver, T_OBJECT);
    // NOTE: No null check on the argument is needed since it's a constant String oop.
    if (stopped()) {
      return true;
    }

    // The null string as a pattern always returns 0 (match at beginning of string)
    if (c == 0) {
      set_result(intcon(0));
      return true;
    }

    // Generate default indexOf
    jchar lastChar = pat->char_at(o + (c - 1));
    int cache = 0;
    int i;
    for (i = 0; i < c - 1; i++) {
      assert(i < pat->length(), "out of range");
      cache |= (1 << (pat->char_at(o + i) & (sizeof(cache) * BitsPerByte - 1)));
    }

    int md2 = c;
    for (i = 0; i < c - 1; i++) {
      assert(i < pat->length(), "out of range");
      if (pat->char_at(o + i) == lastChar) {
        md2 = (c - 1) - i;
      }
    }

    result = string_indexOf(receiver, pat, o, cache, md2);
  }
  set_result(result);
  return true;
}

//--------------------------round_double_node--------------------------------
// Round a double node if necessary.
Node* LibraryCallKit::round_double_node(Node* n) {
  if (Matcher::strict_fp_requires_explicit_rounding && UseSSE <= 1)
    n = _gvn.transform(new (C) RoundDoubleNode(0, n));
  return n;
}

//------------------------------inline_math-----------------------------------
// public static double Math.abs(double)
// public static double Math.sqrt(double)
// public static double Math.log(double)
// public static double Math.log10(double)
bool LibraryCallKit::inline_math(vmIntrinsics::ID id) {
  Node* arg = round_double_node(argument(0));
  Node* n;
  switch (id) {
  case vmIntrinsics::_dabs:   n = new (C) AbsDNode(                arg);  break;
  case vmIntrinsics::_dsqrt:  n = new (C) SqrtDNode(C, control(),  arg);  break;
  case vmIntrinsics::_dlog:   n = new (C) LogDNode(C, control(),   arg);  break;
  case vmIntrinsics::_dlog10: n = new (C) Log10DNode(C, control(), arg);  break;
  default:  fatal_unexpected_iid(id);  break;
  }
  set_result(_gvn.transform(n));
  return true;
}

//------------------------------inline_trig----------------------------------
// Inline sin/cos/tan instructions, if possible.  If rounding is required, do
// argument reduction which will turn into a fast/slow diamond.
bool LibraryCallKit::inline_trig(vmIntrinsics::ID id) {
  Node* arg = round_double_node(argument(0));
  Node* n = NULL;

  switch (id) {
  case vmIntrinsics::_dsin:  n = new (C) SinDNode(C, control(), arg);  break;
  case vmIntrinsics::_dcos:  n = new (C) CosDNode(C, control(), arg);  break;
  case vmIntrinsics::_dtan:  n = new (C) TanDNode(C, control(), arg);  break;
  default:  fatal_unexpected_iid(id);  break;
  }
  n = _gvn.transform(n);

  // Rounding required?  Check for argument reduction!
  if (Matcher::strict_fp_requires_explicit_rounding) {
    static const double     pi_4 =  0.7853981633974483;
    static const double neg_pi_4 = -0.7853981633974483;
    // pi/2 in 80-bit extended precision
    // static const unsigned char pi_2_bits_x[] = {0x35,0xc2,0x68,0x21,0xa2,0xda,0x0f,0xc9,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00};
    // -pi/2 in 80-bit extended precision
    // static const unsigned char neg_pi_2_bits_x[] = {0x35,0xc2,0x68,0x21,0xa2,0xda,0x0f,0xc9,0xff,0xbf,0x00,0x00,0x00,0x00,0x00,0x00};
    // Cutoff value for using this argument reduction technique
    //static const double    pi_2_minus_epsilon =  1.564660403643354;
    //static const double neg_pi_2_plus_epsilon = -1.564660403643354;

    // Pseudocode for sin:
    // if (x <= Math.PI / 4.0) {
    //   if (x >= -Math.PI / 4.0) return  fsin(x);
    //   if (x >= -Math.PI / 2.0) return -fcos(x + Math.PI / 2.0);
    // } else {
    //   if (x <=  Math.PI / 2.0) return  fcos(x - Math.PI / 2.0);
    // }
    // return StrictMath.sin(x);

    // Pseudocode for cos:
    // if (x <= Math.PI / 4.0) {
    //   if (x >= -Math.PI / 4.0) return  fcos(x);
    //   if (x >= -Math.PI / 2.0) return  fsin(x + Math.PI / 2.0);
    // } else {
    //   if (x <=  Math.PI / 2.0) return -fsin(x - Math.PI / 2.0);
    // }
    // return StrictMath.cos(x);

    // Actually, sticking in an 80-bit Intel value into C2 will be tough; it
    // requires a special machine instruction to load it.  Instead we'll try
    // the 'easy' case.  If we really need the extra range +/- PI/2 we'll
    // probably do the math inside the SIN encoding.

    // Make the merge point
    RegionNode* r = new (C) RegionNode(3);
    Node* phi = new (C) PhiNode(r, Type::DOUBLE);

    // Flatten arg so we need only 1 test
    Node *abs = _gvn.transform(new (C) AbsDNode(arg));
    // Node for PI/4 constant
    Node *pi4 = makecon(TypeD::make(pi_4));
    // Check PI/4 : abs(arg)
    Node *cmp = _gvn.transform(new (C) CmpDNode(pi4,abs));
    // Check: If PI/4 < abs(arg) then go slow
    Node *bol = _gvn.transform(new (C) BoolNode( cmp, BoolTest::lt ));
    // Branch either way
    IfNode *iff = create_and_xform_if(control(),bol, PROB_STATIC_FREQUENT, COUNT_UNKNOWN);
    set_control(opt_iff(r,iff));

    // Set fast path result
    phi->init_req(2, n);

    // Slow path - non-blocking leaf call
    Node* call = NULL;
    switch (id) {
    case vmIntrinsics::_dsin:
      call = make_runtime_call(RC_LEAF, OptoRuntime::Math_D_D_Type(),
                               CAST_FROM_FN_PTR(address, SharedRuntime::dsin),
                               "Sin", NULL, arg, top());
      break;
    case vmIntrinsics::_dcos:
      call = make_runtime_call(RC_LEAF, OptoRuntime::Math_D_D_Type(),
                               CAST_FROM_FN_PTR(address, SharedRuntime::dcos),
                               "Cos", NULL, arg, top());
      break;
    case vmIntrinsics::_dtan:
      call = make_runtime_call(RC_LEAF, OptoRuntime::Math_D_D_Type(),
                               CAST_FROM_FN_PTR(address, SharedRuntime::dtan),
                               "Tan", NULL, arg, top());
      break;
    }
    assert(control()->in(0) == call, "");
    Node* slow_result = _gvn.transform(new (C) ProjNode(call, TypeFunc::Parms));
    r->init_req(1, control());
    phi->init_req(1, slow_result);

    // Post-merge
    set_control(_gvn.transform(r));
    record_for_igvn(r);
    n = _gvn.transform(phi);

    C->set_has_split_ifs(true); // Has chance for split-if optimization
  }
  set_result(n);
  return true;
}

void LibraryCallKit::finish_pow_exp(Node* result, Node* x, Node* y, const TypeFunc* call_type, address funcAddr, const char* funcName) {
  //-------------------
  //result=(result.isNaN())? funcAddr():result;
  // Check: If isNaN() by checking result!=result? then either trap
  // or go to runtime
  Node* cmpisnan = _gvn.transform(new (C) CmpDNode(result, result));
  // Build the boolean node
  Node* bolisnum = _gvn.transform(new (C) BoolNode(cmpisnan, BoolTest::eq));

  if (!too_many_traps(Deoptimization::Reason_intrinsic)) {
    { BuildCutout unless(this, bolisnum, PROB_STATIC_FREQUENT);
      // The pow or exp intrinsic returned a NaN, which requires a call
      // to the runtime.  Recompile with the runtime call.
      uncommon_trap(Deoptimization::Reason_intrinsic,
                    Deoptimization::Action_make_not_entrant);
    }
    set_result(result);
  } else {
    // If this inlining ever returned NaN in the past, we compile a call
    // to the runtime to properly handle corner cases

    IfNode* iff = create_and_xform_if(control(), bolisnum, PROB_STATIC_FREQUENT, COUNT_UNKNOWN);
    Node* if_slow = _gvn.transform(new (C) IfFalseNode(iff));
    Node* if_fast = _gvn.transform(new (C) IfTrueNode(iff));

    if (!if_slow->is_top()) {
      RegionNode* result_region = new (C) RegionNode(3);
      PhiNode*    result_val = new (C) PhiNode(result_region, Type::DOUBLE);

      result_region->init_req(1, if_fast);
      result_val->init_req(1, result);

      set_control(if_slow);

      const TypePtr* no_memory_effects = NULL;
      Node* rt = make_runtime_call(RC_LEAF, call_type, funcAddr, funcName,
                                   no_memory_effects,
                                   x, top(), y, y ? top() : NULL);
      Node* value = _gvn.transform(new (C) ProjNode(rt, TypeFunc::Parms+0));
#ifdef ASSERT
      Node* value_top = _gvn.transform(new (C) ProjNode(rt, TypeFunc::Parms+1));
      assert(value_top == top(), "second value must be top");
#endif

      result_region->init_req(2, control());
      result_val->init_req(2, value);
      set_result(result_region, result_val);
    } else {
      set_result(result);
    }
  }
}

//------------------------------inline_exp-------------------------------------
// Inline exp instructions, if possible.  The Intel hardware only misses
// really odd corner cases (+/- Infinity).  Just uncommon-trap them.
bool LibraryCallKit::inline_exp() {
  Node* arg = round_double_node(argument(0));
  Node* n   = _gvn.transform(new (C) ExpDNode(C, control(), arg));

  finish_pow_exp(n, arg, NULL, OptoRuntime::Math_D_D_Type(), CAST_FROM_FN_PTR(address, SharedRuntime::dexp), "EXP");

  C->set_has_split_ifs(true); // Has chance for split-if optimization
  return true;
}

//------------------------------inline_pow-------------------------------------
// Inline power instructions, if possible.
bool LibraryCallKit::inline_pow() {
  // Pseudocode for pow
  // if (x <= 0.0) {
  //   long longy = (long)y;
  //   if ((double)longy == y) { // if y is long
  //     if (y + 1 == y) longy = 0; // huge number: even
  //     result = ((1&longy) == 0)?-DPow(abs(x), y):DPow(abs(x), y);
  //   } else {
  //     result = NaN;
  //   }
  // } else {
  //   result = DPow(x,y);
  // }
  // if (result != result)?  {
  //   result = uncommon_trap() or runtime_call();
  // }
  // return result;

  Node* x = round_double_node(argument(0));
  Node* y = round_double_node(argument(2));

  Node* result = NULL;

  if (!too_many_traps(Deoptimization::Reason_intrinsic)) {
    // Short form: skip the fancy tests and just check for NaN result.
    result = _gvn.transform(new (C) PowDNode(C, control(), x, y));
  } else {
    // If this inlining ever returned NaN in the past, include all
    // checks + call to the runtime.

    // Set the merge point for If node with condition of (x <= 0.0)
    // There are four possible paths to region node and phi node
    RegionNode *r = new (C) RegionNode(4);
    Node *phi = new (C) PhiNode(r, Type::DOUBLE);

    // Build the first if node: if (x <= 0.0)
    // Node for 0 constant
    Node *zeronode = makecon(TypeD::ZERO);
    // Check x:0
    Node *cmp = _gvn.transform(new (C) CmpDNode(x, zeronode));
    // Check: If (x<=0) then go complex path
    Node *bol1 = _gvn.transform(new (C) BoolNode( cmp, BoolTest::le ));
    // Branch either way
    IfNode *if1 = create_and_xform_if(control(),bol1, PROB_STATIC_INFREQUENT, COUNT_UNKNOWN);
    // Fast path taken; set region slot 3
    Node *fast_taken = _gvn.transform(new (C) IfFalseNode(if1));
    r->init_req(3,fast_taken); // Capture fast-control

    // Fast path not-taken, i.e. slow path
    Node *complex_path = _gvn.transform(new (C) IfTrueNode(if1));

    // Set fast path result
    Node *fast_result = _gvn.transform(new (C) PowDNode(C, control(), x, y));
    phi->init_req(3, fast_result);

    // Complex path
    // Build the second if node (if y is long)
    // Node for (long)y
    Node *longy = _gvn.transform(new (C) ConvD2LNode(y));
    // Node for (double)((long) y)
    Node *doublelongy= _gvn.transform(new (C) ConvL2DNode(longy));
    // Check (double)((long) y) : y
    Node *cmplongy= _gvn.transform(new (C) CmpDNode(doublelongy, y));
    // Check if (y isn't long) then go to slow path

    Node *bol2 = _gvn.transform(new (C) BoolNode( cmplongy, BoolTest::ne ));
    // Branch either way
    IfNode *if2 = create_and_xform_if(complex_path,bol2, PROB_STATIC_INFREQUENT, COUNT_UNKNOWN);
    Node* ylong_path = _gvn.transform(new (C) IfFalseNode(if2));

    Node *slow_path = _gvn.transform(new (C) IfTrueNode(if2));

    // Calculate DPow(abs(x), y)*(1 & (long)y)
    // Node for constant 1
    Node *conone = longcon(1);
    // 1& (long)y
    Node *signnode= _gvn.transform(new (C) AndLNode(conone, longy));

    // A huge number is always even. Detect a huge number by checking
    // if y + 1 == y and set integer to be tested for parity to 0.
    // Required for corner case:
    // (long)9.223372036854776E18 = max_jlong
    // (double)(long)9.223372036854776E18 = 9.223372036854776E18
    // max_jlong is odd but 9.223372036854776E18 is even
    Node* yplus1 = _gvn.transform(new (C) AddDNode(y, makecon(TypeD::make(1))));
    Node *cmpyplus1= _gvn.transform(new (C) CmpDNode(yplus1, y));
    Node *bolyplus1 = _gvn.transform(new (C) BoolNode( cmpyplus1, BoolTest::eq ));
    Node* correctedsign = NULL;
    if (ConditionalMoveLimit != 0) {
      correctedsign = _gvn.transform( CMoveNode::make(C, NULL, bolyplus1, signnode, longcon(0), TypeLong::LONG));
    } else {
      IfNode *ifyplus1 = create_and_xform_if(ylong_path,bolyplus1, PROB_FAIR, COUNT_UNKNOWN);
      RegionNode *r = new (C) RegionNode(3);
      Node *phi = new (C) PhiNode(r, TypeLong::LONG);
      r->init_req(1, _gvn.transform(new (C) IfFalseNode(ifyplus1)));
      r->init_req(2, _gvn.transform(new (C) IfTrueNode(ifyplus1)));
      phi->init_req(1, signnode);
      phi->init_req(2, longcon(0));
      correctedsign = _gvn.transform(phi);
      ylong_path = _gvn.transform(r);
      record_for_igvn(r);
    }

    // zero node
    Node *conzero = longcon(0);
    // Check (1&(long)y)==0?
    Node *cmpeq1 = _gvn.transform(new (C) CmpLNode(correctedsign, conzero));
    // Check if (1&(long)y)!=0?, if so the result is negative
    Node *bol3 = _gvn.transform(new (C) BoolNode( cmpeq1, BoolTest::ne ));
    // abs(x)
    Node *absx=_gvn.transform(new (C) AbsDNode(x));
    // abs(x)^y
    Node *absxpowy = _gvn.transform(new (C) PowDNode(C, control(), absx, y));
    // -abs(x)^y
    Node *negabsxpowy = _gvn.transform(new (C) NegDNode (absxpowy));
    // (1&(long)y)==1?-DPow(abs(x), y):DPow(abs(x), y)
    Node *signresult = NULL;
    if (ConditionalMoveLimit != 0) {
      signresult = _gvn.transform( CMoveNode::make(C, NULL, bol3, absxpowy, negabsxpowy, Type::DOUBLE));
    } else {
      IfNode *ifyeven = create_and_xform_if(ylong_path,bol3, PROB_FAIR, COUNT_UNKNOWN);
      RegionNode *r = new (C) RegionNode(3);
      Node *phi = new (C) PhiNode(r, Type::DOUBLE);
      r->init_req(1, _gvn.transform(new (C) IfFalseNode(ifyeven)));
      r->init_req(2, _gvn.transform(new (C) IfTrueNode(ifyeven)));
      phi->init_req(1, absxpowy);
      phi->init_req(2, negabsxpowy);
      signresult = _gvn.transform(phi);
      ylong_path = _gvn.transform(r);
      record_for_igvn(r);
    }
    // Set complex path fast result
    r->init_req(2, ylong_path);
    phi->init_req(2, signresult);

    static const jlong nan_bits = CONST64(0x7ff8000000000000);
    Node *slow_result = makecon(TypeD::make(*(double*)&nan_bits)); // return NaN
    r->init_req(1,slow_path);
    phi->init_req(1,slow_result);

    // Post merge
    set_control(_gvn.transform(r));
    record_for_igvn(r);
    result = _gvn.transform(phi);
  }

  finish_pow_exp(result, x, y, OptoRuntime::Math_DD_D_Type(), CAST_FROM_FN_PTR(address, SharedRuntime::dpow), "POW");

  C->set_has_split_ifs(true); // Has chance for split-if optimization
  return true;
}

//------------------------------runtime_math-----------------------------
bool LibraryCallKit::runtime_math(const TypeFunc* call_type, address funcAddr, const char* funcName) {
  assert(call_type == OptoRuntime::Math_DD_D_Type() || call_type == OptoRuntime::Math_D_D_Type(),
         "must be (DD)D or (D)D type");

  // Inputs
  Node* a = round_double_node(argument(0));
  Node* b = (call_type == OptoRuntime::Math_DD_D_Type()) ? round_double_node(argument(2)) : NULL;

  const TypePtr* no_memory_effects = NULL;
  Node* trig = make_runtime_call(RC_LEAF, call_type, funcAddr, funcName,
                                 no_memory_effects,
                                 a, top(), b, b ? top() : NULL);
  Node* value = _gvn.transform(new (C) ProjNode(trig, TypeFunc::Parms+0));
#ifdef ASSERT
  Node* value_top = _gvn.transform(new (C) ProjNode(trig, TypeFunc::Parms+1));
  assert(value_top == top(), "second value must be top");
#endif

  set_result(value);
  return true;
}

//------------------------------inline_math_native-----------------------------
bool LibraryCallKit::inline_math_native(vmIntrinsics::ID id) {
#define FN_PTR(f) CAST_FROM_FN_PTR(address, f)
  switch (id) {
    // These intrinsics are not properly supported on all hardware
  case vmIntrinsics::_dcos:   return Matcher::has_match_rule(Op_CosD)   ? inline_trig(id) :
    runtime_math(OptoRuntime::Math_D_D_Type(), FN_PTR(SharedRuntime::dcos),   "COS");
  case vmIntrinsics::_dsin:   return Matcher::has_match_rule(Op_SinD)   ? inline_trig(id) :
    runtime_math(OptoRuntime::Math_D_D_Type(), FN_PTR(SharedRuntime::dsin),   "SIN");
  case vmIntrinsics::_dtan:   return Matcher::has_match_rule(Op_TanD)   ? inline_trig(id) :
    runtime_math(OptoRuntime::Math_D_D_Type(), FN_PTR(SharedRuntime::dtan),   "TAN");

  case vmIntrinsics::_dlog:   return Matcher::has_match_rule(Op_LogD)   ? inline_math(id) :
    runtime_math(OptoRuntime::Math_D_D_Type(), FN_PTR(SharedRuntime::dlog),   "LOG");
  case vmIntrinsics::_dlog10: return Matcher::has_match_rule(Op_Log10D) ? inline_math(id) :
    runtime_math(OptoRuntime::Math_D_D_Type(), FN_PTR(SharedRuntime::dlog10), "LOG10");

    // These intrinsics are supported on all hardware
  case vmIntrinsics::_dsqrt:  return Matcher::has_match_rule(Op_SqrtD)  ? inline_math(id) : false;
  case vmIntrinsics::_dabs:   return Matcher::has_match_rule(Op_AbsD)   ? inline_math(id) : false;

  case vmIntrinsics::_dexp:   return Matcher::has_match_rule(Op_ExpD)   ? inline_exp()    :
    runtime_math(OptoRuntime::Math_D_D_Type(),  FN_PTR(SharedRuntime::dexp),  "EXP");
  case vmIntrinsics::_dpow:   return Matcher::has_match_rule(Op_PowD)   ? inline_pow()    :
    runtime_math(OptoRuntime::Math_DD_D_Type(), FN_PTR(SharedRuntime::dpow),  "POW");
#undef FN_PTR

   // These intrinsics are not yet correctly implemented
  case vmIntrinsics::_datan2:
    return false;

  default:
    fatal_unexpected_iid(id);
    return false;
  }
}

static bool is_simple_name(Node* n) {
  return (n->req() == 1         // constant
          || (n->is_Type() && n->as_Type()->type()->singleton())
          || n->is_Proj()       // parameter or return value
          || n->is_Phi()        // local of some sort
          );
}

//----------------------------inline_min_max-----------------------------------
bool LibraryCallKit::inline_min_max(vmIntrinsics::ID id) {
  set_result(generate_min_max(id, argument(0), argument(1)));
  return true;
}

Node*
LibraryCallKit::generate_min_max(vmIntrinsics::ID id, Node* x0, Node* y0) {
  // These are the candidate return value:
  Node* xvalue = x0;
  Node* yvalue = y0;

  if (xvalue == yvalue) {
    return xvalue;
  }

  bool want_max = (id == vmIntrinsics::_max);

  const TypeInt* txvalue = _gvn.type(xvalue)->isa_int();
  const TypeInt* tyvalue = _gvn.type(yvalue)->isa_int();
  if (txvalue == NULL || tyvalue == NULL)  return top();
  // This is not really necessary, but it is consistent with a
  // hypothetical MaxINode::Value method:
  int widen = MAX2(txvalue->_widen, tyvalue->_widen);

  // %%% This folding logic should (ideally) be in a different place.
  // Some should be inside IfNode, and there to be a more reliable
  // transformation of ?: style patterns into cmoves.  We also want
  // more powerful optimizations around cmove and min/max.

  // Try to find a dominating comparison of these guys.
  // It can simplify the index computation for Arrays.copyOf
  // and similar uses of System.arraycopy.
  // First, compute the normalized version of CmpI(x, y).
  int   cmp_op = Op_CmpI;
  Node* xkey = xvalue;
  Node* ykey = yvalue;
  Node* ideal_cmpxy = _gvn.transform(new(C) CmpINode(xkey, ykey));
  if (ideal_cmpxy->is_Cmp()) {
    // E.g., if we have CmpI(length - offset, count),
    // it might idealize to CmpI(length, count + offset)
    cmp_op = ideal_cmpxy->Opcode();
    xkey = ideal_cmpxy->in(1);
    ykey = ideal_cmpxy->in(2);
  }

  // Start by locating any relevant comparisons.
  Node* start_from = (xkey->outcnt() < ykey->outcnt()) ? xkey : ykey;
  Node* cmpxy = NULL;
  Node* cmpyx = NULL;
  for (DUIterator_Fast kmax, k = start_from->fast_outs(kmax); k < kmax; k++) {
    Node* cmp = start_from->fast_out(k);
    if (cmp->outcnt() > 0 &&            // must have prior uses
        cmp->in(0) == NULL &&           // must be context-independent
        cmp->Opcode() == cmp_op) {      // right kind of compare
      if (cmp->in(1) == xkey && cmp->in(2) == ykey)  cmpxy = cmp;
      if (cmp->in(1) == ykey && cmp->in(2) == xkey)  cmpyx = cmp;
    }
  }

  const int NCMPS = 2;
  Node* cmps[NCMPS] = { cmpxy, cmpyx };
  int cmpn;
  for (cmpn = 0; cmpn < NCMPS; cmpn++) {
    if (cmps[cmpn] != NULL)  break;     // find a result
  }
  if (cmpn < NCMPS) {
    // Look for a dominating test that tells us the min and max.
    int depth = 0;                // Limit search depth for speed
    Node* dom = control();
    for (; dom != NULL; dom = IfNode::up_one_dom(dom, true)) {
      if (++depth >= 100)  break;
      Node* ifproj = dom;
      if (!ifproj->is_Proj())  continue;
      Node* iff = ifproj->in(0);
      if (!iff->is_If())  continue;
      Node* bol = iff->in(1);
      if (!bol->is_Bool())  continue;
      Node* cmp = bol->in(1);
      if (cmp == NULL)  continue;
      for (cmpn = 0; cmpn < NCMPS; cmpn++)
        if (cmps[cmpn] == cmp)  break;
      if (cmpn == NCMPS)  continue;
      BoolTest::mask btest = bol->as_Bool()->_test._test;
      if (ifproj->is_IfFalse())  btest = BoolTest(btest).negate();
      if (cmp->in(1) == ykey)    btest = BoolTest(btest).commute();
      // At this point, we know that 'x btest y' is true.
      switch (btest) {
      case BoolTest::eq:
        // They are proven equal, so we can collapse the min/max.
        // Either value is the answer.  Choose the simpler.
        if (is_simple_name(yvalue) && !is_simple_name(xvalue))
          return yvalue;
        return xvalue;
      case BoolTest::lt:          // x < y
      case BoolTest::le:          // x <= y
        return (want_max ? yvalue : xvalue);
      case BoolTest::gt:          // x > y
      case BoolTest::ge:          // x >= y
        return (want_max ? xvalue : yvalue);
      }
    }
  }

  // We failed to find a dominating test.
  // Let's pick a test that might GVN with prior tests.
  Node*          best_bol   = NULL;
  BoolTest::mask best_btest = BoolTest::illegal;
  for (cmpn = 0; cmpn < NCMPS; cmpn++) {
    Node* cmp = cmps[cmpn];
    if (cmp == NULL)  continue;
    for (DUIterator_Fast jmax, j = cmp->fast_outs(jmax); j < jmax; j++) {
      Node* bol = cmp->fast_out(j);
      if (!bol->is_Bool())  continue;
      BoolTest::mask btest = bol->as_Bool()->_test._test;
      if (btest == BoolTest::eq || btest == BoolTest::ne)  continue;
      if (cmp->in(1) == ykey)   btest = BoolTest(btest).commute();
      if (bol->outcnt() > (best_bol == NULL ? 0 : best_bol->outcnt())) {
        best_bol   = bol->as_Bool();
        best_btest = btest;
      }
    }
  }

  Node* answer_if_true  = NULL;
  Node* answer_if_false = NULL;
  switch (best_btest) {
  default:
    if (cmpxy == NULL)
      cmpxy = ideal_cmpxy;
    best_bol = _gvn.transform(new(C) BoolNode(cmpxy, BoolTest::lt));
    // and fall through:
  case BoolTest::lt:          // x < y
  case BoolTest::le:          // x <= y
    answer_if_true  = (want_max ? yvalue : xvalue);
    answer_if_false = (want_max ? xvalue : yvalue);
    break;
  case BoolTest::gt:          // x > y
  case BoolTest::ge:          // x >= y
    answer_if_true  = (want_max ? xvalue : yvalue);
    answer_if_false = (want_max ? yvalue : xvalue);
    break;
  }

  jint hi, lo;
  if (want_max) {
    // We can sharpen the minimum.
    hi = MAX2(txvalue->_hi, tyvalue->_hi);
    lo = MAX2(txvalue->_lo, tyvalue->_lo);
  } else {
    // We can sharpen the maximum.
    hi = MIN2(txvalue->_hi, tyvalue->_hi);
    lo = MIN2(txvalue->_lo, tyvalue->_lo);
  }

  // Use a flow-free graph structure, to avoid creating excess control edges
  // which could hinder other optimizations.
  // Since Math.min/max is often used with arraycopy, we want
  // tightly_coupled_allocation to be able to see beyond min/max expressions.
  Node* cmov = CMoveNode::make(C, NULL, best_bol,
                               answer_if_false, answer_if_true,
                               TypeInt::make(lo, hi, widen));

  return _gvn.transform(cmov);

  /*
  // This is not as desirable as it may seem, since Min and Max
  // nodes do not have a full set of optimizations.
  // And they would interfere, anyway, with 'if' optimizations
  // and with CMoveI canonical forms.
  switch (id) {
  case vmIntrinsics::_min:
    result_val = _gvn.transform(new (C, 3) MinINode(x,y)); break;
  case vmIntrinsics::_max:
    result_val = _gvn.transform(new (C, 3) MaxINode(x,y)); break;
  default:
    ShouldNotReachHere();
  }
  */
}

inline int
LibraryCallKit::classify_unsafe_addr(Node* &base, Node* &offset) {
  const TypePtr* base_type = TypePtr::NULL_PTR;
  if (base != NULL)  base_type = _gvn.type(base)->isa_ptr();
  if (base_type == NULL) {
    // Unknown type.
    return Type::AnyPtr;
  } else if (base_type == TypePtr::NULL_PTR) {
    // Since this is a NULL+long form, we have to switch to a rawptr.
    base   = _gvn.transform(new (C) CastX2PNode(offset));
    offset = MakeConX(0);
    return Type::RawPtr;
  } else if (base_type->base() == Type::RawPtr) {
    return Type::RawPtr;
  } else if (base_type->isa_oopptr()) {
    // Base is never null => always a heap address.
    if (base_type->ptr() == TypePtr::NotNull) {
      return Type::OopPtr;
    }
    // Offset is small => always a heap address.
    const TypeX* offset_type = _gvn.type(offset)->isa_intptr_t();
    if (offset_type != NULL &&
        base_type->offset() == 0 &&     // (should always be?)
        offset_type->_lo >= 0 &&
        !MacroAssembler::needs_explicit_null_check(offset_type->_hi)) {
      return Type::OopPtr;
    }
    // Otherwise, it might either be oop+off or NULL+addr.
    return Type::AnyPtr;
  } else {
    // No information:
    return Type::AnyPtr;
  }
}

inline Node* LibraryCallKit::make_unsafe_address(Node* base, Node* offset) {
  int kind = classify_unsafe_addr(base, offset);
  if (kind == Type::RawPtr) {
    return basic_plus_adr(top(), base, offset);
  } else {
    return basic_plus_adr(base, offset);
  }
}

//--------------------------inline_number_methods-----------------------------
// inline int     Integer.numberOfLeadingZeros(int)
// inline int        Long.numberOfLeadingZeros(long)
//
// inline int     Integer.numberOfTrailingZeros(int)
// inline int        Long.numberOfTrailingZeros(long)
//
// inline int     Integer.bitCount(int)
// inline int        Long.bitCount(long)
//
// inline char  Character.reverseBytes(char)
// inline short     Short.reverseBytes(short)
// inline int     Integer.reverseBytes(int)
// inline long       Long.reverseBytes(long)
bool LibraryCallKit::inline_number_methods(vmIntrinsics::ID id) {
  Node* arg = argument(0);
  Node* n;
  switch (id) {
  case vmIntrinsics::_numberOfLeadingZeros_i:   n = new (C) CountLeadingZerosINode( arg);  break;
  case vmIntrinsics::_numberOfLeadingZeros_l:   n = new (C) CountLeadingZerosLNode( arg);  break;
  case vmIntrinsics::_numberOfTrailingZeros_i:  n = new (C) CountTrailingZerosINode(arg);  break;
  case vmIntrinsics::_numberOfTrailingZeros_l:  n = new (C) CountTrailingZerosLNode(arg);  break;
  case vmIntrinsics::_bitCount_i:               n = new (C) PopCountINode(          arg);  break;
  case vmIntrinsics::_bitCount_l:               n = new (C) PopCountLNode(          arg);  break;
  case vmIntrinsics::_reverseBytes_c:           n = new (C) ReverseBytesUSNode(0,   arg);  break;
  case vmIntrinsics::_reverseBytes_s:           n = new (C) ReverseBytesSNode( 0,   arg);  break;
  case vmIntrinsics::_reverseBytes_i:           n = new (C) ReverseBytesINode( 0,   arg);  break;
  case vmIntrinsics::_reverseBytes_l:           n = new (C) ReverseBytesLNode( 0,   arg);  break;
  default:  fatal_unexpected_iid(id);  break;
  }
  set_result(_gvn.transform(n));
  return true;
}

//----------------------------inline_unsafe_access----------------------------

const static BasicType T_ADDRESS_HOLDER = T_LONG;

// Helper that guards and inserts a pre-barrier.
void LibraryCallKit::insert_pre_barrier(Node* base_oop, Node* offset,
                                        Node* pre_val, bool need_mem_bar) {
  // We could be accessing the referent field of a reference object. If so, when G1
  // is enabled, we need to log the value in the referent field in an SATB buffer.
  // This routine performs some compile time filters and generates suitable
  // runtime filters that guard the pre-barrier code.
  // Also add memory barrier for non volatile load from the referent field
  // to prevent commoning of loads across safepoint.
  if (!UseG1GC && !need_mem_bar)
    return;

  // Some compile time checks.

  // If offset is a constant, is it java_lang_ref_Reference::_reference_offset?
  const TypeX* otype = offset->find_intptr_t_type();
  if (otype != NULL && otype->is_con() &&
      otype->get_con() != java_lang_ref_Reference::referent_offset) {
    // Constant offset but not the reference_offset so just return
    return;
  }

  // We only need to generate the runtime guards for instances.
  const TypeOopPtr* btype = base_oop->bottom_type()->isa_oopptr();
  if (btype != NULL) {
    if (btype->isa_aryptr()) {
      // Array type so nothing to do
      return;
    }

    const TypeInstPtr* itype = btype->isa_instptr();
    if (itype != NULL) {
      // Can the klass of base_oop be statically determined to be
      // _not_ a sub-class of Reference and _not_ Object?
      ciKlass* klass = itype->klass();
      if ( klass->is_loaded() &&
          !klass->is_subtype_of(env()->Reference_klass()) &&
          !env()->Object_klass()->is_subtype_of(klass)) {
        return;
      }
    }
  }

  // The compile time filters did not reject base_oop/offset so
  // we need to generate the following runtime filters
  //
  // if (offset == java_lang_ref_Reference::_reference_offset) {
  //   if (instance_of(base, java.lang.ref.Reference)) {
  //     pre_barrier(_, pre_val, ...);
  //   }
  // }

  float likely   = PROB_LIKELY(  0.999);
  float unlikely = PROB_UNLIKELY(0.999);

  IdealKit ideal(this);
#define __ ideal.

  Node* referent_off = __ ConX(java_lang_ref_Reference::referent_offset);

  __ if_then(offset, BoolTest::eq, referent_off, unlikely); {
      // Update graphKit memory and control from IdealKit.
      sync_kit(ideal);

      Node* ref_klass_con = makecon(TypeKlassPtr::make(env()->Reference_klass()));
      Node* is_instof = gen_instanceof(base_oop, ref_klass_con);

      // Update IdealKit memory and control from graphKit.
      __ sync_kit(this);

      Node* one = __ ConI(1);
      // is_instof == 0 if base_oop == NULL
      __ if_then(is_instof, BoolTest::eq, one, unlikely); {

        // Update graphKit from IdeakKit.
        sync_kit(ideal);

        // Use the pre-barrier to record the value in the referent field
        pre_barrier(false /* do_load */,
                    __ ctrl(),
                    NULL /* obj */, NULL /* adr */, max_juint /* alias_idx */, NULL /* val */, NULL /* val_type */,
                    pre_val /* pre_val */,
                    T_OBJECT);
        if (need_mem_bar) {
          // Add memory barrier to prevent commoning reads from this field
          // across safepoint since GC can change its value.
          insert_mem_bar(Op_MemBarCPUOrder);
        }
        // Update IdealKit from graphKit.
        __ sync_kit(this);

      } __ end_if(); // _ref_type != ref_none
  } __ end_if(); // offset == referent_offset

  // Final sync IdealKit and GraphKit.
  final_sync(ideal);
#undef __
}


// Interpret Unsafe.fieldOffset cookies correctly:
extern jlong Unsafe_field_offset_to_byte_offset(jlong field_offset);

const TypeOopPtr* LibraryCallKit::sharpen_unsafe_type(Compile::AliasType* alias_type, const TypePtr *adr_type, bool is_native_ptr) {
  // Attempt to infer a sharper value type from the offset and base type.
  ciKlass* sharpened_klass = NULL;

  // See if it is an instance field, with an object type.
  if (alias_type->field() != NULL) {
    assert(!is_native_ptr, "native pointer op cannot use a java address");
    if (alias_type->field()->type()->is_klass()) {
      sharpened_klass = alias_type->field()->type()->as_klass();
    }
  }

  // See if it is a narrow oop array.
  if (adr_type->isa_aryptr()) {
    if (adr_type->offset() >= objArrayOopDesc::base_offset_in_bytes()) {
      const TypeOopPtr *elem_type = adr_type->is_aryptr()->elem()->isa_oopptr();
      if (elem_type != NULL) {
        sharpened_klass = elem_type->klass();
      }
    }
  }

  // The sharpened class might be unloaded if there is no class loader
  // contraint in place.
  if (sharpened_klass != NULL && sharpened_klass->is_loaded()) {
    const TypeOopPtr* tjp = TypeOopPtr::make_from_klass(sharpened_klass);

#ifndef PRODUCT
    if (PrintIntrinsics || PrintInlining || PrintOptoInlining) {
      tty->print("  from base type: ");  adr_type->dump();
      tty->print("  sharpened value: ");  tjp->dump();
    }
#endif
    // Sharpen the value type.
    return tjp;
  }
  return NULL;
}

bool LibraryCallKit::inline_unsafe_access(bool is_native_ptr, bool is_store, BasicType type, bool is_volatile) {
  if (callee()->is_static())  return false;  // caller must have the capability!

#ifndef PRODUCT
  {
    ResourceMark rm;
    // Check the signatures.
    ciSignature* sig = callee()->signature();
#ifdef ASSERT
    if (!is_store) {
      // Object getObject(Object base, int/long offset), etc.
      BasicType rtype = sig->return_type()->basic_type();
      if (rtype == T_ADDRESS_HOLDER && callee()->name() == ciSymbol::getAddress_name())
          rtype = T_ADDRESS;  // it is really a C void*
      assert(rtype == type, "getter must return the expected value");
      if (!is_native_ptr) {
        assert(sig->count() == 2, "oop getter has 2 arguments");
        assert(sig->type_at(0)->basic_type() == T_OBJECT, "getter base is object");
        assert(sig->type_at(1)->basic_type() == T_LONG, "getter offset is correct");
      } else {
        assert(sig->count() == 1, "native getter has 1 argument");
        assert(sig->type_at(0)->basic_type() == T_LONG, "getter base is long");
      }
    } else {
      // void putObject(Object base, int/long offset, Object x), etc.
      assert(sig->return_type()->basic_type() == T_VOID, "putter must not return a value");
      if (!is_native_ptr) {
        assert(sig->count() == 3, "oop putter has 3 arguments");
        assert(sig->type_at(0)->basic_type() == T_OBJECT, "putter base is object");
        assert(sig->type_at(1)->basic_type() == T_LONG, "putter offset is correct");
      } else {
        assert(sig->count() == 2, "native putter has 2 arguments");
        assert(sig->type_at(0)->basic_type() == T_LONG, "putter base is long");
      }
      BasicType vtype = sig->type_at(sig->count()-1)->basic_type();
      if (vtype == T_ADDRESS_HOLDER && callee()->name() == ciSymbol::putAddress_name())
        vtype = T_ADDRESS;  // it is really a C void*
      assert(vtype == type, "putter must accept the expected value");
    }
#endif // ASSERT
 }
#endif //PRODUCT

  C->set_has_unsafe_access(true);  // Mark eventual nmethod as "unsafe".

  Node* receiver = argument(0);  // type: oop

  // Build address expression.  See the code in inline_unsafe_prefetch.
  Node* adr;
  Node* heap_base_oop = top();
  Node* offset = top();
  Node* val;

  if (!is_native_ptr) {
    // The base is either a Java object or a value produced by Unsafe.staticFieldBase
    Node* base = argument(1);  // type: oop
    // The offset is a value produced by Unsafe.staticFieldOffset or Unsafe.objectFieldOffset
    offset = argument(2);  // type: long
    // We currently rely on the cookies produced by Unsafe.xxxFieldOffset
    // to be plain byte offsets, which are also the same as those accepted
    // by oopDesc::field_base.
    assert(Unsafe_field_offset_to_byte_offset(11) == 11,
           "fieldOffset must be byte-scaled");
    // 32-bit machines ignore the high half!
    offset = ConvL2X(offset);
    adr = make_unsafe_address(base, offset);
    heap_base_oop = base;
    val = is_store ? argument(4) : NULL;
  } else {
    Node* ptr = argument(1);  // type: long
    ptr = ConvL2X(ptr);  // adjust Java long to machine word
    adr = make_unsafe_address(NULL, ptr);
    val = is_store ? argument(3) : NULL;
  }

  const TypePtr *adr_type = _gvn.type(adr)->isa_ptr();

  // First guess at the value type.
  const Type *value_type = Type::get_const_basic_type(type);

  // Try to categorize the address.  If it comes up as TypeJavaPtr::BOTTOM,
  // there was not enough information to nail it down.
  Compile::AliasType* alias_type = C->alias_type(adr_type);
  assert(alias_type->index() != Compile::AliasIdxBot, "no bare pointers here");

  // We will need memory barriers unless we can determine a unique
  // alias category for this reference.  (Note:  If for some reason
  // the barriers get omitted and the unsafe reference begins to "pollute"
  // the alias analysis of the rest of the graph, either Compile::can_alias
  // or Compile::must_alias will throw a diagnostic assert.)
  bool need_mem_bar = (alias_type->adr_type() == TypeOopPtr::BOTTOM);

  // If we are reading the value of the referent field of a Reference
  // object (either by using Unsafe directly or through reflection)
  // then, if G1 is enabled, we need to record the referent in an
  // SATB log buffer using the pre-barrier mechanism.
  // Also we need to add memory barrier to prevent commoning reads
  // from this field across safepoint since GC can change its value.
  bool need_read_barrier = !is_native_ptr && !is_store &&
                           offset != top() && heap_base_oop != top();

  if (!is_store && type == T_OBJECT) {
    const TypeOopPtr* tjp = sharpen_unsafe_type(alias_type, adr_type, is_native_ptr);
    if (tjp != NULL) {
      value_type = tjp;
    }
  }

  receiver = null_check(receiver);
  if (stopped()) {
    return true;
  }
  // Heap pointers get a null-check from the interpreter,
  // as a courtesy.  However, this is not guaranteed by Unsafe,
  // and it is not possible to fully distinguish unintended nulls
  // from intended ones in this API.

  if (is_volatile) {
    // We need to emit leading and trailing CPU membars (see below) in
    // addition to memory membars when is_volatile. This is a little
    // too strong, but avoids the need to insert per-alias-type
    // volatile membars (for stores; compare Parse::do_put_xxx), which
    // we cannot do effectively here because we probably only have a
    // rough approximation of type.
    need_mem_bar = true;
    // For Stores, place a memory ordering barrier now.
    if (is_store)
      insert_mem_bar(Op_MemBarRelease);
  }

  // Memory barrier to prevent normal and 'unsafe' accesses from
  // bypassing each other.  Happens after null checks, so the
  // exception paths do not take memory state from the memory barrier,
  // so there's no problems making a strong assert about mixing users
  // of safe & unsafe memory.  Otherwise fails in a CTW of rt.jar
  // around 5701, class sun/reflect/UnsafeBooleanFieldAccessorImpl.
  if (need_mem_bar) insert_mem_bar(Op_MemBarCPUOrder);

  if (!is_store) {
    Node* p = make_load(control(), adr, value_type, type, adr_type, is_volatile);
    // load value
    switch (type) {
    case T_BOOLEAN:
    case T_CHAR:
    case T_BYTE:
    case T_SHORT:
    case T_INT:
    case T_LONG:
    case T_FLOAT:
    case T_DOUBLE:
      break;
    case T_OBJECT:
      if (need_read_barrier) {
        insert_pre_barrier(heap_base_oop, offset, p, !(is_volatile || need_mem_bar));
      }
      break;
    case T_ADDRESS:
      // Cast to an int type.
      p = _gvn.transform(new (C) CastP2XNode(NULL, p));
      p = ConvX2L(p);
      break;
    default:
      fatal(err_msg_res("unexpected type %d: %s", type, type2name(type)));
      break;
    }
    // The load node has the control of the preceding MemBarCPUOrder.  All
    // following nodes will have the control of the MemBarCPUOrder inserted at
    // the end of this method.  So, pushing the load onto the stack at a later
    // point is fine.
    set_result(p);
  } else {
    // place effect of store into memory
    switch (type) {
    case T_DOUBLE:
      val = dstore_rounding(val);
      break;
    case T_ADDRESS:
      // Repackage the long as a pointer.
      val = ConvL2X(val);
      val = _gvn.transform(new (C) CastX2PNode(val));
      break;
    }

    if (type != T_OBJECT ) {
      (void) store_to_memory(control(), adr, val, type, adr_type, is_volatile);
    } else {
      // Possibly an oop being stored to Java heap or native memory
      if (!TypePtr::NULL_PTR->higher_equal(_gvn.type(heap_base_oop))) {
        // oop to Java heap.
        (void) store_oop_to_unknown(control(), heap_base_oop, adr, adr_type, val, type);
      } else {
        // We can't tell at compile time if we are storing in the Java heap or outside
        // of it. So we need to emit code to conditionally do the proper type of
        // store.

        IdealKit ideal(this);
#define __ ideal.
        // QQQ who knows what probability is here??
        __ if_then(heap_base_oop, BoolTest::ne, null(), PROB_UNLIKELY(0.999)); {
          // Sync IdealKit and graphKit.
          sync_kit(ideal);
          Node* st = store_oop_to_unknown(control(), heap_base_oop, adr, adr_type, val, type);
          // Update IdealKit memory.
          __ sync_kit(this);
        } __ else_(); {
          __ store(__ ctrl(), adr, val, type, alias_type->index(), is_volatile);
        } __ end_if();
        // Final sync IdealKit and GraphKit.
        final_sync(ideal);
#undef __
      }
    }
  }

  if (is_volatile) {
    if (!is_store)
      insert_mem_bar(Op_MemBarAcquire);
    else
      insert_mem_bar(Op_MemBarVolatile);
  }

  if (need_mem_bar) insert_mem_bar(Op_MemBarCPUOrder);

  return true;
}

//----------------------------inline_unsafe_prefetch----------------------------

bool LibraryCallKit::inline_unsafe_prefetch(bool is_native_ptr, bool is_store, bool is_static) {
#ifndef PRODUCT
  {
    ResourceMark rm;
    // Check the signatures.
    ciSignature* sig = callee()->signature();
#ifdef ASSERT
    // Object getObject(Object base, int/long offset), etc.
    BasicType rtype = sig->return_type()->basic_type();
    if (!is_native_ptr) {
      assert(sig->count() == 2, "oop prefetch has 2 arguments");
      assert(sig->type_at(0)->basic_type() == T_OBJECT, "prefetch base is object");
      assert(sig->type_at(1)->basic_type() == T_LONG, "prefetcha offset is correct");
    } else {
      assert(sig->count() == 1, "native prefetch has 1 argument");
      assert(sig->type_at(0)->basic_type() == T_LONG, "prefetch base is long");
    }
#endif // ASSERT
  }
#endif // !PRODUCT

  C->set_has_unsafe_access(true);  // Mark eventual nmethod as "unsafe".

  const int idx = is_static ? 0 : 1;
  if (!is_static) {
    null_check_receiver();
    if (stopped()) {
      return true;
    }
  }

  // Build address expression.  See the code in inline_unsafe_access.
  Node *adr;
  if (!is_native_ptr) {
    // The base is either a Java object or a value produced by Unsafe.staticFieldBase
    Node* base   = argument(idx + 0);  // type: oop
    // The offset is a value produced by Unsafe.staticFieldOffset or Unsafe.objectFieldOffset
    Node* offset = argument(idx + 1);  // type: long
    // We currently rely on the cookies produced by Unsafe.xxxFieldOffset
    // to be plain byte offsets, which are also the same as those accepted
    // by oopDesc::field_base.
    assert(Unsafe_field_offset_to_byte_offset(11) == 11,
           "fieldOffset must be byte-scaled");
    // 32-bit machines ignore the high half!
    offset = ConvL2X(offset);
    adr = make_unsafe_address(base, offset);
  } else {
    Node* ptr = argument(idx + 0);  // type: long
    ptr = ConvL2X(ptr);  // adjust Java long to machine word
    adr = make_unsafe_address(NULL, ptr);
  }

  // Generate the read or write prefetch
  Node *prefetch;
  if (is_store) {
    prefetch = new (C) PrefetchWriteNode(i_o(), adr);
  } else {
    prefetch = new (C) PrefetchReadNode(i_o(), adr);
  }
  prefetch->init_req(0, control());
  set_i_o(_gvn.transform(prefetch));

  return true;
}

//----------------------------inline_unsafe_load_store----------------------------
// This method serves a couple of different customers (depending on LoadStoreKind):
//
// LS_cmpxchg:
//   public final native boolean compareAndSwapObject(Object o, long offset, Object expected, Object x);
//   public final native boolean compareAndSwapInt(   Object o, long offset, int    expected, int    x);
//   public final native boolean compareAndSwapLong(  Object o, long offset, long   expected, long   x);
//
// LS_xadd:
//   public int  getAndAddInt( Object o, long offset, int  delta)
//   public long getAndAddLong(Object o, long offset, long delta)
//
// LS_xchg:
//   int    getAndSet(Object o, long offset, int    newValue)
//   long   getAndSet(Object o, long offset, long   newValue)
//   Object getAndSet(Object o, long offset, Object newValue)
//
bool LibraryCallKit::inline_unsafe_load_store(BasicType type, LoadStoreKind kind) {
  // This basic scheme here is the same as inline_unsafe_access, but
  // differs in enough details that combining them would make the code
  // overly confusing.  (This is a true fact! I originally combined
  // them, but even I was confused by it!) As much code/comments as
  // possible are retained from inline_unsafe_access though to make
  // the correspondences clearer. - dl

  if (callee()->is_static())  return false;  // caller must have the capability!

#ifndef PRODUCT
  BasicType rtype;
  {
    ResourceMark rm;
    // Check the signatures.
    ciSignature* sig = callee()->signature();
    rtype = sig->return_type()->basic_type();
    if (kind == LS_xadd || kind == LS_xchg) {
      // Check the signatures.
#ifdef ASSERT
      assert(rtype == type, "get and set must return the expected type");
      assert(sig->count() == 3, "get and set has 3 arguments");
      assert(sig->type_at(0)->basic_type() == T_OBJECT, "get and set base is object");
      assert(sig->type_at(1)->basic_type() == T_LONG, "get and set offset is long");
      assert(sig->type_at(2)->basic_type() == type, "get and set must take expected type as new value/delta");
#endif // ASSERT
    } else if (kind == LS_cmpxchg) {
      // Check the signatures.
#ifdef ASSERT
      assert(rtype == T_BOOLEAN, "CAS must return boolean");
      assert(sig->count() == 4, "CAS has 4 arguments");
      assert(sig->type_at(0)->basic_type() == T_OBJECT, "CAS base is object");
      assert(sig->type_at(1)->basic_type() == T_LONG, "CAS offset is long");
#endif // ASSERT
    } else {
      ShouldNotReachHere();
    }
  }
#endif //PRODUCT

  C->set_has_unsafe_access(true);  // Mark eventual nmethod as "unsafe".

  // Get arguments:
  Node* receiver = NULL;
  Node* base     = NULL;
  Node* offset   = NULL;
  Node* oldval   = NULL;
  Node* newval   = NULL;
  if (kind == LS_cmpxchg) {
    const bool two_slot_type = type2size[type] == 2;
    receiver = argument(0);  // type: oop
    base     = argument(1);  // type: oop
    offset   = argument(2);  // type: long
    oldval   = argument(4);  // type: oop, int, or long
    newval   = argument(two_slot_type ? 6 : 5);  // type: oop, int, or long
  } else if (kind == LS_xadd || kind == LS_xchg){
    receiver = argument(0);  // type: oop
    base     = argument(1);  // type: oop
    offset   = argument(2);  // type: long
    oldval   = NULL;
    newval   = argument(4);  // type: oop, int, or long
  }

  // Null check receiver.
  receiver = null_check(receiver);
  if (stopped()) {
    return true;
  }

  // Build field offset expression.
  // We currently rely on the cookies produced by Unsafe.xxxFieldOffset
  // to be plain byte offsets, which are also the same as those accepted
  // by oopDesc::field_base.
  assert(Unsafe_field_offset_to_byte_offset(11) == 11, "fieldOffset must be byte-scaled");
  // 32-bit machines ignore the high half of long offsets
  offset = ConvL2X(offset);
  Node* adr = make_unsafe_address(base, offset);
  const TypePtr *adr_type = _gvn.type(adr)->isa_ptr();

  // For CAS, unlike inline_unsafe_access, there seems no point in
  // trying to refine types. Just use the coarse types here.
  const Type *value_type = Type::get_const_basic_type(type);
  Compile::AliasType* alias_type = C->alias_type(adr_type);
  assert(alias_type->index() != Compile::AliasIdxBot, "no bare pointers here");

  if (kind == LS_xchg && type == T_OBJECT) {
    const TypeOopPtr* tjp = sharpen_unsafe_type(alias_type, adr_type);
    if (tjp != NULL) {
      value_type = tjp;
    }
  }

  int alias_idx = C->get_alias_index(adr_type);

  // Memory-model-wise, a LoadStore acts like a little synchronized
  // block, so needs barriers on each side.  These don't translate
  // into actual barriers on most machines, but we still need rest of
  // compiler to respect ordering.

  insert_mem_bar(Op_MemBarRelease);
  insert_mem_bar(Op_MemBarCPUOrder);

  // 4984716: MemBars must be inserted before this
  //          memory node in order to avoid a false
  //          dependency which will confuse the scheduler.
  Node *mem = memory(alias_idx);

  // For now, we handle only those cases that actually exist: ints,
  // longs, and Object. Adding others should be straightforward.
  Node* load_store;
  switch(type) {
  case T_INT:
    if (kind == LS_xadd) {
      load_store = _gvn.transform(new (C) GetAndAddINode(control(), mem, adr, newval, adr_type));
    } else if (kind == LS_xchg) {
      load_store = _gvn.transform(new (C) GetAndSetINode(control(), mem, adr, newval, adr_type));
    } else if (kind == LS_cmpxchg) {
      load_store = _gvn.transform(new (C) CompareAndSwapINode(control(), mem, adr, newval, oldval));
    } else {
      ShouldNotReachHere();
    }
    break;
  case T_LONG:
    if (kind == LS_xadd) {
      load_store = _gvn.transform(new (C) GetAndAddLNode(control(), mem, adr, newval, adr_type));
    } else if (kind == LS_xchg) {
      load_store = _gvn.transform(new (C) GetAndSetLNode(control(), mem, adr, newval, adr_type));
    } else if (kind == LS_cmpxchg) {
      load_store = _gvn.transform(new (C) CompareAndSwapLNode(control(), mem, adr, newval, oldval));
    } else {
      ShouldNotReachHere();
    }
    break;
  case T_OBJECT:
    // Transformation of a value which could be NULL pointer (CastPP #NULL)
    // could be delayed during Parse (for example, in adjust_map_after_if()).
    // Execute transformation here to avoid barrier generation in such case.
    if (_gvn.type(newval) == TypePtr::NULL_PTR)
      newval = _gvn.makecon(TypePtr::NULL_PTR);

    // Reference stores need a store barrier.
    if (kind == LS_xchg) {
      // If pre-barrier must execute before the oop store, old value will require do_load here.
      if (!can_move_pre_barrier()) {
        pre_barrier(true /* do_load*/,
                    control(), base, adr, alias_idx, newval, value_type->make_oopptr(),
                    NULL /* pre_val*/,
                    T_OBJECT);
      } // Else move pre_barrier to use load_store value, see below.
    } else if (kind == LS_cmpxchg) {
      // Same as for newval above:
      if (_gvn.type(oldval) == TypePtr::NULL_PTR) {
        oldval = _gvn.makecon(TypePtr::NULL_PTR);
      }
      // The only known value which might get overwritten is oldval.
      pre_barrier(false /* do_load */,
                  control(), NULL, NULL, max_juint, NULL, NULL,
                  oldval /* pre_val */,
                  T_OBJECT);
    } else {
      ShouldNotReachHere();
    }

#ifdef _LP64
    if (adr->bottom_type()->is_ptr_to_narrowoop()) {
      Node *newval_enc = _gvn.transform(new (C) EncodePNode(newval, newval->bottom_type()->make_narrowoop()));
      if (kind == LS_xchg) {
        load_store = _gvn.transform(new (C) GetAndSetNNode(control(), mem, adr,
                                                              newval_enc, adr_type, value_type->make_narrowoop()));
      } else {
        assert(kind == LS_cmpxchg, "wrong LoadStore operation");
        Node *oldval_enc = _gvn.transform(new (C) EncodePNode(oldval, oldval->bottom_type()->make_narrowoop()));
        load_store = _gvn.transform(new (C) CompareAndSwapNNode(control(), mem, adr,
                                                                   newval_enc, oldval_enc));
      }
    } else
#endif
    {
      if (kind == LS_xchg) {
        load_store = _gvn.transform(new (C) GetAndSetPNode(control(), mem, adr, newval, adr_type, value_type->is_oopptr()));
      } else {
        assert(kind == LS_cmpxchg, "wrong LoadStore operation");
        load_store = _gvn.transform(new (C) CompareAndSwapPNode(control(), mem, adr, newval, oldval));
      }
    }
    post_barrier(control(), load_store, base, adr, alias_idx, newval, T_OBJECT, true);
    break;
  default:
    fatal(err_msg_res("unexpected type %d: %s", type, type2name(type)));
    break;
  }

  // SCMemProjNodes represent the memory state of a LoadStore. Their
  // main role is to prevent LoadStore nodes from being optimized away
  // when their results aren't used.
  Node* proj = _gvn.transform(new (C) SCMemProjNode(load_store));
  set_memory(proj, alias_idx);

  if (type == T_OBJECT && kind == LS_xchg) {
#ifdef _LP64
    if (adr->bottom_type()->is_ptr_to_narrowoop()) {
      load_store = _gvn.transform(new (C) DecodeNNode(load_store, load_store->get_ptr_type()));
    }
#endif
    if (can_move_pre_barrier()) {
      // Don't need to load pre_val. The old value is returned by load_store.
      // The pre_barrier can execute after the xchg as long as no safepoint
      // gets inserted between them.
      pre_barrier(false /* do_load */,
                  control(), NULL, NULL, max_juint, NULL, NULL,
                  load_store /* pre_val */,
                  T_OBJECT);
    }
  }

  // Add the trailing membar surrounding the access
  insert_mem_bar(Op_MemBarCPUOrder);
  insert_mem_bar(Op_MemBarAcquire);

  assert(type2size[load_store->bottom_type()->basic_type()] == type2size[rtype], "result type should match");
  set_result(load_store);
  return true;
}

//----------------------------inline_unsafe_ordered_store----------------------
// public native void sun.misc.Unsafe.putOrderedObject(Object o, long offset, Object x);
// public native void sun.misc.Unsafe.putOrderedInt(Object o, long offset, int x);
// public native void sun.misc.Unsafe.putOrderedLong(Object o, long offset, long x);
bool LibraryCallKit::inline_unsafe_ordered_store(BasicType type) {
  // This is another variant of inline_unsafe_access, differing in
  // that it always issues store-store ("release") barrier and ensures
  // store-atomicity (which only matters for "long").

  if (callee()->is_static())  return false;  // caller must have the capability!

#ifndef PRODUCT
  {
    ResourceMark rm;
    // Check the signatures.
    ciSignature* sig = callee()->signature();
#ifdef ASSERT
    BasicType rtype = sig->return_type()->basic_type();
    assert(rtype == T_VOID, "must return void");
    assert(sig->count() == 3, "has 3 arguments");
    assert(sig->type_at(0)->basic_type() == T_OBJECT, "base is object");
    assert(sig->type_at(1)->basic_type() == T_LONG, "offset is long");
#endif // ASSERT
  }
#endif //PRODUCT

  C->set_has_unsafe_access(true);  // Mark eventual nmethod as "unsafe".

  // Get arguments:
  Node* receiver = argument(0);  // type: oop
  Node* base     = argument(1);  // type: oop
  Node* offset   = argument(2);  // type: long
  Node* val      = argument(4);  // type: oop, int, or long

  // Null check receiver.
  receiver = null_check(receiver);
  if (stopped()) {
    return true;
  }

  // Build field offset expression.
  assert(Unsafe_field_offset_to_byte_offset(11) == 11, "fieldOffset must be byte-scaled");
  // 32-bit machines ignore the high half of long offsets
  offset = ConvL2X(offset);
  Node* adr = make_unsafe_address(base, offset);
  const TypePtr *adr_type = _gvn.type(adr)->isa_ptr();
  const Type *value_type = Type::get_const_basic_type(type);
  Compile::AliasType* alias_type = C->alias_type(adr_type);

  insert_mem_bar(Op_MemBarRelease);
  insert_mem_bar(Op_MemBarCPUOrder);
  // Ensure that the store is atomic for longs:
  const bool require_atomic_access = true;
  Node* store;
  if (type == T_OBJECT) // reference stores need a store barrier.
    store = store_oop_to_unknown(control(), base, adr, adr_type, val, type);
  else {
    store = store_to_memory(control(), adr, val, type, adr_type, require_atomic_access);
  }
  insert_mem_bar(Op_MemBarCPUOrder);
  return true;
}

bool LibraryCallKit::inline_unsafe_fence(vmIntrinsics::ID id) {
  // Regardless of form, don't allow previous ld/st to move down,
  // then issue acquire, release, or volatile mem_bar.
  insert_mem_bar(Op_MemBarCPUOrder);
  switch(id) {
    case vmIntrinsics::_loadFence:
      insert_mem_bar(Op_MemBarAcquire);
      return true;
    case vmIntrinsics::_storeFence:
      insert_mem_bar(Op_MemBarRelease);
      return true;
    case vmIntrinsics::_fullFence:
      insert_mem_bar(Op_MemBarVolatile);
      return true;
    default:
      fatal_unexpected_iid(id);
      return false;
  }
}

bool LibraryCallKit::klass_needs_init_guard(Node* kls) {
  if (!kls->is_Con()) {
    return true;
  }
  const TypeKlassPtr* klsptr = kls->bottom_type()->isa_klassptr();
  if (klsptr == NULL) {
    return true;
  }
  ciInstanceKlass* ik = klsptr->klass()->as_instance_klass();
  // don't need a guard for a klass that is already initialized
  return !ik->is_initialized();
}

//----------------------------inline_unsafe_allocate---------------------------
// public native Object sun.misc.Unsafe.allocateInstance(Class<?> cls);
bool LibraryCallKit::inline_unsafe_allocate() {
  if (callee()->is_static())  return false;  // caller must have the capability!

  null_check_receiver();  // null-check, then ignore
  Node* cls = null_check(argument(1));
  if (stopped())  return true;

  Node* kls = load_klass_from_mirror(cls, false, NULL, 0);
  kls = null_check(kls);
  if (stopped())  return true;  // argument was like int.class

  Node* test = NULL;
  if (LibraryCallKit::klass_needs_init_guard(kls)) {
    // Note:  The argument might still be an illegal value like
    // Serializable.class or Object[].class.   The runtime will handle it.
    // But we must make an explicit check for initialization.
    Node* insp = basic_plus_adr(kls, in_bytes(InstanceKlass::init_state_offset()));
    // Use T_BOOLEAN for InstanceKlass::_init_state so the compiler
    // can generate code to load it as unsigned byte.
    Node* inst = make_load(NULL, insp, TypeInt::UBYTE, T_BOOLEAN);
    Node* bits = intcon(InstanceKlass::fully_initialized);
    test = _gvn.transform(new (C) SubINode(inst, bits));
    // The 'test' is non-zero if we need to take a slow path.
  }

  Node* obj = new_instance(kls, test);
  set_result(obj);
  return true;
}

#ifdef TRACE_HAVE_INTRINSICS
/*
 * oop -> myklass
 * myklass->trace_id |= USED
 * return myklass->trace_id & ~0x3
 */
bool LibraryCallKit::inline_native_classID() {
  null_check_receiver();  // null-check, then ignore
  Node* cls = null_check(argument(1), T_OBJECT);
  Node* kls = load_klass_from_mirror(cls, false, NULL, 0);
  kls = null_check(kls, T_OBJECT);
  ByteSize offset = TRACE_ID_OFFSET;
  Node* insp = basic_plus_adr(kls, in_bytes(offset));
  Node* tvalue = make_load(NULL, insp, TypeLong::LONG, T_LONG);
  Node* bits = longcon(~0x03l); // ignore bit 0 & 1
  Node* andl = _gvn.transform(new (C) AndLNode(tvalue, bits));
  Node* clsused = longcon(0x01l); // set the class bit
  Node* orl = _gvn.transform(new (C) OrLNode(tvalue, clsused));

  const TypePtr *adr_type = _gvn.type(insp)->isa_ptr();
  store_to_memory(control(), insp, orl, T_LONG, adr_type);
  set_result(andl);
  return true;
}

bool LibraryCallKit::inline_native_threadID() {
  Node* tls_ptr = NULL;
  Node* cur_thr = generate_current_thread(tls_ptr);
  Node* p = basic_plus_adr(top()/*!oop*/, tls_ptr, in_bytes(JavaThread::osthread_offset()));
  Node* osthread = make_load(NULL, p, TypeRawPtr::NOTNULL, T_ADDRESS);
  p = basic_plus_adr(top()/*!oop*/, osthread, in_bytes(OSThread::thread_id_offset()));

  Node* threadid = NULL;
  size_t thread_id_size = OSThread::thread_id_size();
  if (thread_id_size == (size_t) BytesPerLong) {
    threadid = ConvL2I(make_load(control(), p, TypeLong::LONG, T_LONG));
  } else if (thread_id_size == (size_t) BytesPerInt) {
    threadid = make_load(control(), p, TypeInt::INT, T_INT);
  } else {
    ShouldNotReachHere();
  }
  set_result(threadid);
  return true;
}
#endif

//------------------------inline_native_time_funcs--------------
// inline code for System.currentTimeMillis() and System.nanoTime()
// these have the same type and signature
bool LibraryCallKit::inline_native_time_funcs(address funcAddr, const char* funcName) {
  const TypeFunc* tf = OptoRuntime::void_long_Type();
  const TypePtr* no_memory_effects = NULL;
  Node* time = make_runtime_call(RC_LEAF, tf, funcAddr, funcName, no_memory_effects);
  Node* value = _gvn.transform(new (C) ProjNode(time, TypeFunc::Parms+0));
#ifdef ASSERT
  Node* value_top = _gvn.transform(new (C) ProjNode(time, TypeFunc::Parms+1));
  assert(value_top == top(), "second value must be top");
#endif
  set_result(value);
  return true;
}

//------------------------inline_native_currentThread------------------
bool LibraryCallKit::inline_native_currentThread() {
  Node* junk = NULL;
  set_result(generate_current_thread(junk));
  return true;
}

//------------------------inline_native_isInterrupted------------------
// private native boolean java.lang.Thread.isInterrupted(boolean ClearInterrupted);
bool LibraryCallKit::inline_native_isInterrupted() {
  // Add a fast path to t.isInterrupted(clear_int):
  //   (t == Thread.current() && (!TLS._osthread._interrupted || !clear_int))
  //   ? TLS._osthread._interrupted : /*slow path:*/ t.isInterrupted(clear_int)
  // So, in the common case that the interrupt bit is false,
  // we avoid making a call into the VM.  Even if the interrupt bit
  // is true, if the clear_int argument is false, we avoid the VM call.
  // However, if the receiver is not currentThread, we must call the VM,
  // because there must be some locking done around the operation.

  // We only go to the fast case code if we pass two guards.
  // Paths which do not pass are accumulated in the slow_region.

  enum {
    no_int_result_path   = 1, // t == Thread.current() && !TLS._osthread._interrupted
    no_clear_result_path = 2, // t == Thread.current() &&  TLS._osthread._interrupted && !clear_int
    slow_result_path     = 3, // slow path: t.isInterrupted(clear_int)
    PATH_LIMIT
  };

  // Ensure that it's not possible to move the load of TLS._osthread._interrupted flag
  // out of the function.
  insert_mem_bar(Op_MemBarCPUOrder);

  RegionNode* result_rgn = new (C) RegionNode(PATH_LIMIT);
  PhiNode*    result_val = new (C) PhiNode(result_rgn, TypeInt::BOOL);

  RegionNode* slow_region = new (C) RegionNode(1);
  record_for_igvn(slow_region);

  // (a) Receiving thread must be the current thread.
  Node* rec_thr = argument(0);
  Node* tls_ptr = NULL;
  Node* cur_thr = generate_current_thread(tls_ptr);
  Node* cmp_thr = _gvn.transform(new (C) CmpPNode(cur_thr, rec_thr));
  Node* bol_thr = _gvn.transform(new (C) BoolNode(cmp_thr, BoolTest::ne));

  generate_slow_guard(bol_thr, slow_region);

  // (b) Interrupt bit on TLS must be false.
  Node* p = basic_plus_adr(top()/*!oop*/, tls_ptr, in_bytes(JavaThread::osthread_offset()));
  Node* osthread = make_load(NULL, p, TypeRawPtr::NOTNULL, T_ADDRESS);
  p = basic_plus_adr(top()/*!oop*/, osthread, in_bytes(OSThread::interrupted_offset()));

  // Set the control input on the field _interrupted read to prevent it floating up.
  Node* int_bit = make_load(control(), p, TypeInt::BOOL, T_INT);
  Node* cmp_bit = _gvn.transform(new (C) CmpINode(int_bit, intcon(0)));
  Node* bol_bit = _gvn.transform(new (C) BoolNode(cmp_bit, BoolTest::ne));

  IfNode* iff_bit = create_and_map_if(control(), bol_bit, PROB_UNLIKELY_MAG(3), COUNT_UNKNOWN);

  // First fast path:  if (!TLS._interrupted) return false;
  Node* false_bit = _gvn.transform(new (C) IfFalseNode(iff_bit));
  result_rgn->init_req(no_int_result_path, false_bit);
  result_val->init_req(no_int_result_path, intcon(0));

  // drop through to next case
  set_control( _gvn.transform(new (C) IfTrueNode(iff_bit)));

  // (c) Or, if interrupt bit is set and clear_int is false, use 2nd fast path.
  Node* clr_arg = argument(1);
  Node* cmp_arg = _gvn.transform(new (C) CmpINode(clr_arg, intcon(0)));
  Node* bol_arg = _gvn.transform(new (C) BoolNode(cmp_arg, BoolTest::ne));
  IfNode* iff_arg = create_and_map_if(control(), bol_arg, PROB_FAIR, COUNT_UNKNOWN);

  // Second fast path:  ... else if (!clear_int) return true;
  Node* false_arg = _gvn.transform(new (C) IfFalseNode(iff_arg));
  result_rgn->init_req(no_clear_result_path, false_arg);
  result_val->init_req(no_clear_result_path, intcon(1));

  // drop through to next case
  set_control( _gvn.transform(new (C) IfTrueNode(iff_arg)));

  // (d) Otherwise, go to the slow path.
  slow_region->add_req(control());
  set_control( _gvn.transform(slow_region));

  if (stopped()) {
    // There is no slow path.
    result_rgn->init_req(slow_result_path, top());
    result_val->init_req(slow_result_path, top());
  } else {
    // non-virtual because it is a private non-static
    CallJavaNode* slow_call = generate_method_call(vmIntrinsics::_isInterrupted);

    Node* slow_val = set_results_for_java_call(slow_call);
    // this->control() comes from set_results_for_java_call

    Node* fast_io  = slow_call->in(TypeFunc::I_O);
    Node* fast_mem = slow_call->in(TypeFunc::Memory);

    // These two phis are pre-filled with copies of of the fast IO and Memory
    PhiNode* result_mem  = PhiNode::make(result_rgn, fast_mem, Type::MEMORY, TypePtr::BOTTOM);
    PhiNode* result_io   = PhiNode::make(result_rgn, fast_io,  Type::ABIO);

    result_rgn->init_req(slow_result_path, control());
    result_io ->init_req(slow_result_path, i_o());
    result_mem->init_req(slow_result_path, reset_memory());
    result_val->init_req(slow_result_path, slow_val);

    set_all_memory(_gvn.transform(result_mem));
    set_i_o(       _gvn.transform(result_io));
  }

  C->set_has_split_ifs(true); // Has chance for split-if optimization
  set_result(result_rgn, result_val);
  return true;
}

//---------------------------load_mirror_from_klass----------------------------
// Given a klass oop, load its java mirror (a java.lang.Class oop).
Node* LibraryCallKit::load_mirror_from_klass(Node* klass) {
  Node* p = basic_plus_adr(klass, in_bytes(Klass::java_mirror_offset()));
  return make_load(NULL, p, TypeInstPtr::MIRROR, T_OBJECT);
}

//-----------------------load_klass_from_mirror_common-------------------------
// Given a java mirror (a java.lang.Class oop), load its corresponding klass oop.
// Test the klass oop for null (signifying a primitive Class like Integer.TYPE),
// and branch to the given path on the region.
// If never_see_null, take an uncommon trap on null, so we can optimistically
// compile for the non-null case.
// If the region is NULL, force never_see_null = true.
Node* LibraryCallKit::load_klass_from_mirror_common(Node* mirror,
                                                    bool never_see_null,
                                                    RegionNode* region,
                                                    int null_path,
                                                    int offset) {
  if (region == NULL)  never_see_null = true;
  Node* p = basic_plus_adr(mirror, offset);
  const TypeKlassPtr*  kls_type = TypeKlassPtr::OBJECT_OR_NULL;
  Node* kls = _gvn.transform( LoadKlassNode::make(_gvn, immutable_memory(), p, TypeRawPtr::BOTTOM, kls_type));
  Node* null_ctl = top();
  kls = null_check_oop(kls, &null_ctl, never_see_null);
  if (region != NULL) {
    // Set region->in(null_path) if the mirror is a primitive (e.g, int.class).
    region->init_req(null_path, null_ctl);
  } else {
    assert(null_ctl == top(), "no loose ends");
  }
  return kls;
}

//--------------------(inline_native_Class_query helpers)---------------------
// Use this for JVM_ACC_INTERFACE, JVM_ACC_IS_CLONEABLE, JVM_ACC_HAS_FINALIZER.
// Fall through if (mods & mask) == bits, take the guard otherwise.
Node* LibraryCallKit::generate_access_flags_guard(Node* kls, int modifier_mask, int modifier_bits, RegionNode* region) {
  // Branch around if the given klass has the given modifier bit set.
  // Like generate_guard, adds a new path onto the region.
  Node* modp = basic_plus_adr(kls, in_bytes(Klass::access_flags_offset()));
  Node* mods = make_load(NULL, modp, TypeInt::INT, T_INT);
  Node* mask = intcon(modifier_mask);
  Node* bits = intcon(modifier_bits);
  Node* mbit = _gvn.transform(new (C) AndINode(mods, mask));
  Node* cmp  = _gvn.transform(new (C) CmpINode(mbit, bits));
  Node* bol  = _gvn.transform(new (C) BoolNode(cmp, BoolTest::ne));
  return generate_fair_guard(bol, region);
}
Node* LibraryCallKit::generate_interface_guard(Node* kls, RegionNode* region) {
  return generate_access_flags_guard(kls, JVM_ACC_INTERFACE, 0, region);
}

//-------------------------inline_native_Class_query-------------------
bool LibraryCallKit::inline_native_Class_query(vmIntrinsics::ID id) {
  const Type* return_type = TypeInt::BOOL;
  Node* prim_return_value = top();  // what happens if it's a primitive class?
  bool never_see_null = !too_many_traps(Deoptimization::Reason_null_check);
  bool expect_prim = false;     // most of these guys expect to work on refs

  enum { _normal_path = 1, _prim_path = 2, PATH_LIMIT };

  Node* mirror = argument(0);
  Node* obj    = top();

  switch (id) {
  case vmIntrinsics::_isInstance:
    // nothing is an instance of a primitive type
    prim_return_value = intcon(0);
    obj = argument(1);
    break;
  case vmIntrinsics::_getModifiers:
    prim_return_value = intcon(JVM_ACC_ABSTRACT | JVM_ACC_FINAL | JVM_ACC_PUBLIC);
    assert(is_power_of_2((int)JVM_ACC_WRITTEN_FLAGS+1), "change next line");
    return_type = TypeInt::make(0, JVM_ACC_WRITTEN_FLAGS, Type::WidenMin);
    break;
  case vmIntrinsics::_isInterface:
    prim_return_value = intcon(0);
    break;
  case vmIntrinsics::_isArray:
    prim_return_value = intcon(0);
    expect_prim = true;  // cf. ObjectStreamClass.getClassSignature
    break;
  case vmIntrinsics::_isPrimitive:
    prim_return_value = intcon(1);
    expect_prim = true;  // obviously
    break;
  case vmIntrinsics::_getSuperclass:
    prim_return_value = null();
    return_type = TypeInstPtr::MIRROR->cast_to_ptr_type(TypePtr::BotPTR);
    break;
  case vmIntrinsics::_getComponentType:
    prim_return_value = null();
    return_type = TypeInstPtr::MIRROR->cast_to_ptr_type(TypePtr::BotPTR);
    break;
  case vmIntrinsics::_getClassAccessFlags:
    prim_return_value = intcon(JVM_ACC_ABSTRACT | JVM_ACC_FINAL | JVM_ACC_PUBLIC);
    return_type = TypeInt::INT;  // not bool!  6297094
    break;
  default:
    fatal_unexpected_iid(id);
    break;
  }

  const TypeInstPtr* mirror_con = _gvn.type(mirror)->isa_instptr();
  if (mirror_con == NULL)  return false;  // cannot happen?

#ifndef PRODUCT
  if (PrintIntrinsics || PrintInlining || PrintOptoInlining) {
    ciType* k = mirror_con->java_mirror_type();
    if (k) {
      tty->print("Inlining %s on constant Class ", vmIntrinsics::name_at(intrinsic_id()));
      k->print_name();
      tty->cr();
    }
  }
#endif

  // Null-check the mirror, and the mirror's klass ptr (in case it is a primitive).
  RegionNode* region = new (C) RegionNode(PATH_LIMIT);
  record_for_igvn(region);
  PhiNode* phi = new (C) PhiNode(region, return_type);

  // The mirror will never be null of Reflection.getClassAccessFlags, however
  // it may be null for Class.isInstance or Class.getModifiers. Throw a NPE
  // if it is. See bug 4774291.

  // For Reflection.getClassAccessFlags(), the null check occurs in
  // the wrong place; see inline_unsafe_access(), above, for a similar
  // situation.
  mirror = null_check(mirror);
  // If mirror or obj is dead, only null-path is taken.
  if (stopped())  return true;

  if (expect_prim)  never_see_null = false;  // expect nulls (meaning prims)

  // Now load the mirror's klass metaobject, and null-check it.
  // Side-effects region with the control path if the klass is null.
  Node* kls = load_klass_from_mirror(mirror, never_see_null, region, _prim_path);
  // If kls is null, we have a primitive mirror.
  phi->init_req(_prim_path, prim_return_value);
  if (stopped()) { set_result(region, phi); return true; }

  Node* p;  // handy temp
  Node* null_ctl;

  // Now that we have the non-null klass, we can perform the real query.
  // For constant classes, the query will constant-fold in LoadNode::Value.
  Node* query_value = top();
  switch (id) {
  case vmIntrinsics::_isInstance:
    // nothing is an instance of a primitive type
    query_value = gen_instanceof(obj, kls);
    break;

  case vmIntrinsics::_getModifiers:
    p = basic_plus_adr(kls, in_bytes(Klass::modifier_flags_offset()));
    query_value = make_load(NULL, p, TypeInt::INT, T_INT);
    break;

  case vmIntrinsics::_isInterface:
    // (To verify this code sequence, check the asserts in JVM_IsInterface.)
    if (generate_interface_guard(kls, region) != NULL)
      // A guard was added.  If the guard is taken, it was an interface.
      phi->add_req(intcon(1));
    // If we fall through, it's a plain class.
    query_value = intcon(0);
    break;

  case vmIntrinsics::_isArray:
    // (To verify this code sequence, check the asserts in JVM_IsArrayClass.)
    if (generate_array_guard(kls, region) != NULL)
      // A guard was added.  If the guard is taken, it was an array.
      phi->add_req(intcon(1));
    // If we fall through, it's a plain class.
    query_value = intcon(0);
    break;

  case vmIntrinsics::_isPrimitive:
    query_value = intcon(0); // "normal" path produces false
    break;

  case vmIntrinsics::_getSuperclass:
    // The rules here are somewhat unfortunate, but we can still do better
    // with random logic than with a JNI call.
    // Interfaces store null or Object as _super, but must report null.
    // Arrays store an intermediate super as _super, but must report Object.
    // Other types can report the actual _super.
    // (To verify this code sequence, check the asserts in JVM_IsInterface.)
    if (generate_interface_guard(kls, region) != NULL)
      // A guard was added.  If the guard is taken, it was an interface.
      phi->add_req(null());
    if (generate_array_guard(kls, region) != NULL)
      // A guard was added.  If the guard is taken, it was an array.
      phi->add_req(makecon(TypeInstPtr::make(env()->Object_klass()->java_mirror())));
    // If we fall through, it's a plain class.  Get its _super.
    p = basic_plus_adr(kls, in_bytes(Klass::super_offset()));
    kls = _gvn.transform( LoadKlassNode::make(_gvn, immutable_memory(), p, TypeRawPtr::BOTTOM, TypeKlassPtr::OBJECT_OR_NULL));
    null_ctl = top();
    kls = null_check_oop(kls, &null_ctl);
    if (null_ctl != top()) {
      // If the guard is taken, Object.superClass is null (both klass and mirror).
      region->add_req(null_ctl);
      phi   ->add_req(null());
    }
    if (!stopped()) {
      query_value = load_mirror_from_klass(kls);
    }
    break;

  case vmIntrinsics::_getComponentType:
    if (generate_array_guard(kls, region) != NULL) {
      // Be sure to pin the oop load to the guard edge just created:
      Node* is_array_ctrl = region->in(region->req()-1);
      Node* cma = basic_plus_adr(kls, in_bytes(ArrayKlass::component_mirror_offset()));
      Node* cmo = make_load(is_array_ctrl, cma, TypeInstPtr::MIRROR, T_OBJECT);
      phi->add_req(cmo);
    }
    query_value = null();  // non-array case is null
    break;

  case vmIntrinsics::_getClassAccessFlags:
    p = basic_plus_adr(kls, in_bytes(Klass::access_flags_offset()));
    query_value = make_load(NULL, p, TypeInt::INT, T_INT);
    break;

  default:
    fatal_unexpected_iid(id);
    break;
  }

  // Fall-through is the normal case of a query to a real class.
  phi->init_req(1, query_value);
  region->init_req(1, control());

  C->set_has_split_ifs(true); // Has chance for split-if optimization
  set_result(region, phi);
  return true;
}

//--------------------------inline_native_subtype_check------------------------
// This intrinsic takes the JNI calls out of the heart of
// UnsafeFieldAccessorImpl.set, which improves Field.set, readObject, etc.
bool LibraryCallKit::inline_native_subtype_check() {
  // Pull both arguments off the stack.
  Node* args[2];                // two java.lang.Class mirrors: superc, subc
  args[0] = argument(0);
  args[1] = argument(1);
  Node* klasses[2];             // corresponding Klasses: superk, subk
  klasses[0] = klasses[1] = top();

  enum {
    // A full decision tree on {superc is prim, subc is prim}:
    _prim_0_path = 1,           // {P,N} => false
                                // {P,P} & superc!=subc => false
    _prim_same_path,            // {P,P} & superc==subc => true
    _prim_1_path,               // {N,P} => false
    _ref_subtype_path,          // {N,N} & subtype check wins => true
    _both_ref_path,             // {N,N} & subtype check loses => false
    PATH_LIMIT
  };

  RegionNode* region = new (C) RegionNode(PATH_LIMIT);
  Node*       phi    = new (C) PhiNode(region, TypeInt::BOOL);
  record_for_igvn(region);

  const TypePtr* adr_type = TypeRawPtr::BOTTOM;   // memory type of loads
  const TypeKlassPtr* kls_type = TypeKlassPtr::OBJECT_OR_NULL;
  int class_klass_offset = java_lang_Class::klass_offset_in_bytes();

  // First null-check both mirrors and load each mirror's klass metaobject.
  int which_arg;
  for (which_arg = 0; which_arg <= 1; which_arg++) {
    Node* arg = args[which_arg];
    arg = null_check(arg);
    if (stopped())  break;
    args[which_arg] = arg;

    Node* p = basic_plus_adr(arg, class_klass_offset);
    Node* kls = LoadKlassNode::make(_gvn, immutable_memory(), p, adr_type, kls_type);
    klasses[which_arg] = _gvn.transform(kls);
  }

  // Having loaded both klasses, test each for null.
  bool never_see_null = !too_many_traps(Deoptimization::Reason_null_check);
  for (which_arg = 0; which_arg <= 1; which_arg++) {
    Node* kls = klasses[which_arg];
    Node* null_ctl = top();
    kls = null_check_oop(kls, &null_ctl, never_see_null);
    int prim_path = (which_arg == 0 ? _prim_0_path : _prim_1_path);
    region->init_req(prim_path, null_ctl);
    if (stopped())  break;
    klasses[which_arg] = kls;
  }

  if (!stopped()) {
    // now we have two reference types, in klasses[0..1]
    Node* subk   = klasses[1];  // the argument to isAssignableFrom
    Node* superk = klasses[0];  // the receiver
    region->set_req(_both_ref_path, gen_subtype_check(subk, superk));
    // now we have a successful reference subtype check
    region->set_req(_ref_subtype_path, control());
  }

  // If both operands are primitive (both klasses null), then
  // we must return true when they are identical primitives.
  // It is convenient to test this after the first null klass check.
  set_control(region->in(_prim_0_path)); // go back to first null check
  if (!stopped()) {
    // Since superc is primitive, make a guard for the superc==subc case.
    Node* cmp_eq = _gvn.transform(new (C) CmpPNode(args[0], args[1]));
    Node* bol_eq = _gvn.transform(new (C) BoolNode(cmp_eq, BoolTest::eq));
    generate_guard(bol_eq, region, PROB_FAIR);
    if (region->req() == PATH_LIMIT+1) {
      // A guard was added.  If the added guard is taken, superc==subc.
      region->swap_edges(PATH_LIMIT, _prim_same_path);
      region->del_req(PATH_LIMIT);
    }
    region->set_req(_prim_0_path, control()); // Not equal after all.
  }

  // these are the only paths that produce 'true':
  phi->set_req(_prim_same_path,   intcon(1));
  phi->set_req(_ref_subtype_path, intcon(1));

  // pull together the cases:
  assert(region->req() == PATH_LIMIT, "sane region");
  for (uint i = 1; i < region->req(); i++) {
    Node* ctl = region->in(i);
    if (ctl == NULL || ctl == top()) {
      region->set_req(i, top());
      phi   ->set_req(i, top());
    } else if (phi->in(i) == NULL) {
      phi->set_req(i, intcon(0)); // all other paths produce 'false'
    }
  }

  set_control(_gvn.transform(region));
  set_result(_gvn.transform(phi));
  return true;
}

//---------------------generate_array_guard_common------------------------
Node* LibraryCallKit::generate_array_guard_common(Node* kls, RegionNode* region,
                                                  bool obj_array, bool not_array) {
  // If obj_array/non_array==false/false:
  // Branch around if the given klass is in fact an array (either obj or prim).
  // If obj_array/non_array==false/true:
  // Branch around if the given klass is not an array klass of any kind.
  // If obj_array/non_array==true/true:
  // Branch around if the kls is not an oop array (kls is int[], String, etc.)
  // If obj_array/non_array==true/false:
  // Branch around if the kls is an oop array (Object[] or subtype)
  //
  // Like generate_guard, adds a new path onto the region.
  jint  layout_con = 0;
  Node* layout_val = get_layout_helper(kls, layout_con);
  if (layout_val == NULL) {
    bool query = (obj_array
                  ? Klass::layout_helper_is_objArray(layout_con)
                  : Klass::layout_helper_is_array(layout_con));
    if (query == not_array) {
      return NULL;                       // never a branch
    } else {                             // always a branch
      Node* always_branch = control();
      if (region != NULL)
        region->add_req(always_branch);
      set_control(top());
      return always_branch;
    }
  }
  // Now test the correct condition.
  jint  nval = (obj_array
                ? ((jint)Klass::_lh_array_tag_type_value
                   <<    Klass::_lh_array_tag_shift)
                : Klass::_lh_neutral_value);
  Node* cmp = _gvn.transform(new(C) CmpINode(layout_val, intcon(nval)));
  BoolTest::mask btest = BoolTest::lt;  // correct for testing is_[obj]array
  // invert the test if we are looking for a non-array
  if (not_array)  btest = BoolTest(btest).negate();
  Node* bol = _gvn.transform(new(C) BoolNode(cmp, btest));
  return generate_fair_guard(bol, region);
}


//-----------------------inline_native_newArray--------------------------
// private static native Object java.lang.reflect.newArray(Class<?> componentType, int length);
bool LibraryCallKit::inline_native_newArray() {
  Node* mirror    = argument(0);
  Node* count_val = argument(1);

  mirror = null_check(mirror);
  // If mirror or obj is dead, only null-path is taken.
  if (stopped())  return true;

  enum { _normal_path = 1, _slow_path = 2, PATH_LIMIT };
  RegionNode* result_reg = new(C) RegionNode(PATH_LIMIT);
  PhiNode*    result_val = new(C) PhiNode(result_reg,
                                          TypeInstPtr::NOTNULL);
  PhiNode*    result_io  = new(C) PhiNode(result_reg, Type::ABIO);
  PhiNode*    result_mem = new(C) PhiNode(result_reg, Type::MEMORY,
                                          TypePtr::BOTTOM);

  bool never_see_null = !too_many_traps(Deoptimization::Reason_null_check);
  Node* klass_node = load_array_klass_from_mirror(mirror, never_see_null,
                                                  result_reg, _slow_path);
  Node* normal_ctl   = control();
  Node* no_array_ctl = result_reg->in(_slow_path);

  // Generate code for the slow case.  We make a call to newArray().
  set_control(no_array_ctl);
  if (!stopped()) {
    // Either the input type is void.class, or else the
    // array klass has not yet been cached.  Either the
    // ensuing call will throw an exception, or else it
    // will cache the array klass for next time.
    PreserveJVMState pjvms(this);
    CallJavaNode* slow_call = generate_method_call_static(vmIntrinsics::_newArray);
    Node* slow_result = set_results_for_java_call(slow_call);
    // this->control() comes from set_results_for_java_call
    result_reg->set_req(_slow_path, control());
    result_val->set_req(_slow_path, slow_result);
    result_io ->set_req(_slow_path, i_o());
    result_mem->set_req(_slow_path, reset_memory());
  }

  set_control(normal_ctl);
  if (!stopped()) {
    // Normal case:  The array type has been cached in the java.lang.Class.
    // The following call works fine even if the array type is polymorphic.
    // It could be a dynamic mix of int[], boolean[], Object[], etc.
    Node* obj = new_array(klass_node, count_val, 0);  // no arguments to push
    result_reg->init_req(_normal_path, control());
    result_val->init_req(_normal_path, obj);
    result_io ->init_req(_normal_path, i_o());
    result_mem->init_req(_normal_path, reset_memory());
  }

  // Return the combined state.
  set_i_o(        _gvn.transform(result_io)  );
  set_all_memory( _gvn.transform(result_mem));

  C->set_has_split_ifs(true); // Has chance for split-if optimization
  set_result(result_reg, result_val);
  return true;
}

//----------------------inline_native_getLength--------------------------
// public static native int java.lang.reflect.Array.getLength(Object array);
bool LibraryCallKit::inline_native_getLength() {
  if (too_many_traps(Deoptimization::Reason_intrinsic))  return false;

  Node* array = null_check(argument(0));
  // If array is dead, only null-path is taken.
  if (stopped())  return true;

  // Deoptimize if it is a non-array.
  Node* non_array = generate_non_array_guard(load_object_klass(array), NULL);

  if (non_array != NULL) {
    PreserveJVMState pjvms(this);
    set_control(non_array);
    uncommon_trap(Deoptimization::Reason_intrinsic,
                  Deoptimization::Action_maybe_recompile);
  }

  // If control is dead, only non-array-path is taken.
  if (stopped())  return true;

  // The works fine even if the array type is polymorphic.
  // It could be a dynamic mix of int[], boolean[], Object[], etc.
  Node* result = load_array_length(array);

  C->set_has_split_ifs(true);  // Has chance for split-if optimization
  set_result(result);
  return true;
}

//------------------------inline_array_copyOf----------------------------
// public static <T,U> T[] java.util.Arrays.copyOf(     U[] original, int newLength,         Class<? extends T[]> newType);
// public static <T,U> T[] java.util.Arrays.copyOfRange(U[] original, int from,      int to, Class<? extends T[]> newType);
bool LibraryCallKit::inline_array_copyOf(bool is_copyOfRange) {
  if (too_many_traps(Deoptimization::Reason_intrinsic))  return false;

  // Get the arguments.
  Node* original          = argument(0);
  Node* start             = is_copyOfRange? argument(1): intcon(0);
  Node* end               = is_copyOfRange? argument(2): argument(1);
  Node* array_type_mirror = is_copyOfRange? argument(3): argument(2);

  Node* newcopy;

  // Set the original stack and the reexecute bit for the interpreter to reexecute
  // the bytecode that invokes Arrays.copyOf if deoptimization happens.
  { PreserveReexecuteState preexecs(this);
    jvms()->set_should_reexecute(true);

    array_type_mirror = null_check(array_type_mirror);
    original          = null_check(original);

    // Check if a null path was taken unconditionally.
    if (stopped())  return true;

    Node* orig_length = load_array_length(original);

    Node* klass_node = load_klass_from_mirror(array_type_mirror, false, NULL, 0);
    klass_node = null_check(klass_node);

    RegionNode* bailout = new (C) RegionNode(1);
    record_for_igvn(bailout);

    // Despite the generic type of Arrays.copyOf, the mirror might be int, int[], etc.
    // Bail out if that is so.
    Node* not_objArray = generate_non_objArray_guard(klass_node, bailout);
    if (not_objArray != NULL) {
      // Improve the klass node's type from the new optimistic assumption:
      ciKlass* ak = ciArrayKlass::make(env()->Object_klass());
      const Type* akls = TypeKlassPtr::make(TypePtr::NotNull, ak, 0/*offset*/);
      Node* cast = new (C) CastPPNode(klass_node, akls);
      cast->init_req(0, control());
      klass_node = _gvn.transform(cast);
    }

    // Bail out if either start or end is negative.
    generate_negative_guard(start, bailout, &start);
    generate_negative_guard(end,   bailout, &end);

    Node* length = end;
    if (_gvn.type(start) != TypeInt::ZERO) {
      length = _gvn.transform(new (C) SubINode(end, start));
    }

    // Bail out if length is negative.
    // Without this the new_array would throw
    // NegativeArraySizeException but IllegalArgumentException is what
    // should be thrown
    generate_negative_guard(length, bailout, &length);

    if (bailout->req() > 1) {
      PreserveJVMState pjvms(this);
      set_control(_gvn.transform(bailout));
      uncommon_trap(Deoptimization::Reason_intrinsic,
                    Deoptimization::Action_maybe_recompile);
    }

    if (!stopped()) {
      // How many elements will we copy from the original?
      // The answer is MinI(orig_length - start, length).
      Node* orig_tail = _gvn.transform(new (C) SubINode(orig_length, start));
      Node* moved = generate_min_max(vmIntrinsics::_min, orig_tail, length);

      newcopy = new_array(klass_node, length, 0);  // no argments to push

      // Generate a direct call to the right arraycopy function(s).
      // We know the copy is disjoint but we might not know if the
      // oop stores need checking.
      // Extreme case:  Arrays.copyOf((Integer[])x, 10, String[].class).
      // This will fail a store-check if x contains any non-nulls.
      bool disjoint_bases = true;
      // if start > orig_length then the length of the copy may be
      // negative.
      bool length_never_negative = !is_copyOfRange;
      generate_arraycopy(TypeAryPtr::OOPS, T_OBJECT,
                         original, start, newcopy, intcon(0), moved,
                         disjoint_bases, length_never_negative);
    }
  } // original reexecute is set back here

  C->set_has_split_ifs(true); // Has chance for split-if optimization
  if (!stopped()) {
    set_result(newcopy);
  }
  return true;
}


//----------------------generate_virtual_guard---------------------------
// Helper for hashCode and clone.  Peeks inside the vtable to avoid a call.
Node* LibraryCallKit::generate_virtual_guard(Node* obj_klass,
                                             RegionNode* slow_region) {
  ciMethod* method = callee();
  int vtable_index = method->vtable_index();
  // Get the Method* out of the appropriate vtable entry.
  int entry_offset  = (InstanceKlass::vtable_start_offset() +
                     vtable_index*vtableEntry::size()) * wordSize +
                     vtableEntry::method_offset_in_bytes();
  Node* entry_addr  = basic_plus_adr(obj_klass, entry_offset);
  Node* target_call = make_load(NULL, entry_addr, TypePtr::NOTNULL, T_ADDRESS);

  // Compare the target method with the expected method (e.g., Object.hashCode).
  const TypePtr* native_call_addr = TypeMetadataPtr::make(method);

  Node* native_call = makecon(native_call_addr);
  Node* chk_native  = _gvn.transform(new(C) CmpPNode(target_call, native_call));
  Node* test_native = _gvn.transform(new(C) BoolNode(chk_native, BoolTest::ne));

  return generate_slow_guard(test_native, slow_region);
}

//-----------------------generate_method_call----------------------------
// Use generate_method_call to make a slow-call to the real
// method if the fast path fails.  An alternative would be to
// use a stub like OptoRuntime::slow_arraycopy_Java.
// This only works for expanding the current library call,
// not another intrinsic.  (E.g., don't use this for making an
// arraycopy call inside of the copyOf intrinsic.)
CallJavaNode*
LibraryCallKit::generate_method_call(vmIntrinsics::ID method_id, bool is_virtual, bool is_static) {
  // When compiling the intrinsic method itself, do not use this technique.
  guarantee(callee() != C->method(), "cannot make slow-call to self");

  ciMethod* method = callee();
  // ensure the JVMS we have will be correct for this call
  guarantee(method_id == method->intrinsic_id(), "must match");

  const TypeFunc* tf = TypeFunc::make(method);
  CallJavaNode* slow_call;
  if (is_static) {
    assert(!is_virtual, "");
    slow_call = new(C) CallStaticJavaNode(C, tf,
                           SharedRuntime::get_resolve_static_call_stub(),
                           method, bci());
  } else if (is_virtual) {
    null_check_receiver();
    int vtable_index = Method::invalid_vtable_index;
    if (UseInlineCaches) {
      // Suppress the vtable call
    } else {
      // hashCode and clone are not a miranda methods,
      // so the vtable index is fixed.
      // No need to use the linkResolver to get it.
       vtable_index = method->vtable_index();
    }
    slow_call = new(C) CallDynamicJavaNode(tf,
                          SharedRuntime::get_resolve_virtual_call_stub(),
                          method, vtable_index, bci());
  } else {  // neither virtual nor static:  opt_virtual
    null_check_receiver();
    slow_call = new(C) CallStaticJavaNode(C, tf,
                                SharedRuntime::get_resolve_opt_virtual_call_stub(),
                                method, bci());
    slow_call->set_optimized_virtual(true);
  }
  set_arguments_for_java_call(slow_call);
  set_edges_for_java_call(slow_call);
  return slow_call;
}


//------------------------------inline_native_hashcode--------------------
// Build special case code for calls to hashCode on an object.
bool LibraryCallKit::inline_native_hashcode(bool is_virtual, bool is_static) {
  assert(is_static == callee()->is_static(), "correct intrinsic selection");
  assert(!(is_virtual && is_static), "either virtual, special, or static");

  enum { _slow_path = 1, _fast_path, _null_path, PATH_LIMIT };

  RegionNode* result_reg = new(C) RegionNode(PATH_LIMIT);
  PhiNode*    result_val = new(C) PhiNode(result_reg,
                                          TypeInt::INT);
  PhiNode*    result_io  = new(C) PhiNode(result_reg, Type::ABIO);
  PhiNode*    result_mem = new(C) PhiNode(result_reg, Type::MEMORY,
                                          TypePtr::BOTTOM);
  Node* obj = NULL;
  if (!is_static) {
    // Check for hashing null object
    obj = null_check_receiver();
    if (stopped())  return true;        // unconditionally null
    result_reg->init_req(_null_path, top());
    result_val->init_req(_null_path, top());
  } else {
    // Do a null check, and return zero if null.
    // System.identityHashCode(null) == 0
    obj = argument(0);
    Node* null_ctl = top();
    obj = null_check_oop(obj, &null_ctl);
    result_reg->init_req(_null_path, null_ctl);
    result_val->init_req(_null_path, _gvn.intcon(0));
  }

  // Unconditionally null?  Then return right away.
  if (stopped()) {
    set_control( result_reg->in(_null_path));
    if (!stopped())
      set_result(result_val->in(_null_path));
    return true;
  }

  // After null check, get the object's klass.
  Node* obj_klass = load_object_klass(obj);

  // This call may be virtual (invokevirtual) or bound (invokespecial).
  // For each case we generate slightly different code.

  // We only go to the fast case code if we pass a number of guards.  The
  // paths which do not pass are accumulated in the slow_region.
  RegionNode* slow_region = new (C) RegionNode(1);
  record_for_igvn(slow_region);

  // If this is a virtual call, we generate a funny guard.  We pull out
  // the vtable entry corresponding to hashCode() from the target object.
  // If the target method which we are calling happens to be the native
  // Object hashCode() method, we pass the guard.  We do not need this
  // guard for non-virtual calls -- the caller is known to be the native
  // Object hashCode().
  if (is_virtual) {
    generate_virtual_guard(obj_klass, slow_region);
  }

  // Get the header out of the object, use LoadMarkNode when available
  Node* header_addr = basic_plus_adr(obj, oopDesc::mark_offset_in_bytes());
  Node* header = make_load(control(), header_addr, TypeX_X, TypeX_X->basic_type());

  // Test the header to see if it is unlocked.
  Node *lock_mask      = _gvn.MakeConX(markOopDesc::biased_lock_mask_in_place);
  Node *lmasked_header = _gvn.transform(new (C) AndXNode(header, lock_mask));
  Node *unlocked_val   = _gvn.MakeConX(markOopDesc::unlocked_value);
  Node *chk_unlocked   = _gvn.transform(new (C) CmpXNode( lmasked_header, unlocked_val));
  Node *test_unlocked  = _gvn.transform(new (C) BoolNode( chk_unlocked, BoolTest::ne));

  generate_slow_guard(test_unlocked, slow_region);

  // Get the hash value and check to see that it has been properly assigned.
  // We depend on hash_mask being at most 32 bits and avoid the use of
  // hash_mask_in_place because it could be larger than 32 bits in a 64-bit
  // vm: see markOop.hpp.
  Node *hash_mask      = _gvn.intcon(markOopDesc::hash_mask);
  Node *hash_shift     = _gvn.intcon(markOopDesc::hash_shift);
  Node *hshifted_header= _gvn.transform(new (C) URShiftXNode(header, hash_shift));
  // This hack lets the hash bits live anywhere in the mark object now, as long
  // as the shift drops the relevant bits into the low 32 bits.  Note that
  // Java spec says that HashCode is an int so there's no point in capturing
  // an 'X'-sized hashcode (32 in 32-bit build or 64 in 64-bit build).
  hshifted_header      = ConvX2I(hshifted_header);
  Node *hash_val       = _gvn.transform(new (C) AndINode(hshifted_header, hash_mask));

  Node *no_hash_val    = _gvn.intcon(markOopDesc::no_hash);
  Node *chk_assigned   = _gvn.transform(new (C) CmpINode( hash_val, no_hash_val));
  Node *test_assigned  = _gvn.transform(new (C) BoolNode( chk_assigned, BoolTest::eq));

  generate_slow_guard(test_assigned, slow_region);

  Node* init_mem = reset_memory();
  // fill in the rest of the null path:
  result_io ->init_req(_null_path, i_o());
  result_mem->init_req(_null_path, init_mem);

  result_val->init_req(_fast_path, hash_val);
  result_reg->init_req(_fast_path, control());
  result_io ->init_req(_fast_path, i_o());
  result_mem->init_req(_fast_path, init_mem);

  // Generate code for the slow case.  We make a call to hashCode().
  set_control(_gvn.transform(slow_region));
  if (!stopped()) {
    // No need for PreserveJVMState, because we're using up the present state.
    set_all_memory(init_mem);
    vmIntrinsics::ID hashCode_id = is_static ? vmIntrinsics::_identityHashCode : vmIntrinsics::_hashCode;
    CallJavaNode* slow_call = generate_method_call(hashCode_id, is_virtual, is_static);
    Node* slow_result = set_results_for_java_call(slow_call);
    // this->control() comes from set_results_for_java_call
    result_reg->init_req(_slow_path, control());
    result_val->init_req(_slow_path, slow_result);
    result_io  ->set_req(_slow_path, i_o());
    result_mem ->set_req(_slow_path, reset_memory());
  }

  // Return the combined state.
  set_i_o(        _gvn.transform(result_io)  );
  set_all_memory( _gvn.transform(result_mem));

  set_result(result_reg, result_val);
  return true;
}

//---------------------------inline_native_getClass----------------------------
// public final native Class<?> java.lang.Object.getClass();
//
// Build special case code for calls to getClass on an object.
bool LibraryCallKit::inline_native_getClass() {
  Node* obj = null_check_receiver();
  if (stopped())  return true;
  set_result(load_mirror_from_klass(load_object_klass(obj)));
  return true;
}

//-----------------inline_native_Reflection_getCallerClass---------------------
// public static native Class<?> sun.reflect.Reflection.getCallerClass();
//
// In the presence of deep enough inlining, getCallerClass() becomes a no-op.
//
// NOTE: This code must perform the same logic as JVM_GetCallerClass
// in that it must skip particular security frames and checks for
// caller sensitive methods.
bool LibraryCallKit::inline_native_Reflection_getCallerClass() {
#ifndef PRODUCT
  if ((PrintIntrinsics || PrintInlining || PrintOptoInlining) && Verbose) {
    tty->print_cr("Attempting to inline sun.reflect.Reflection.getCallerClass");
  }
#endif

  if (!jvms()->has_method()) {
#ifndef PRODUCT
    if ((PrintIntrinsics || PrintInlining || PrintOptoInlining) && Verbose) {
      tty->print_cr("  Bailing out because intrinsic was inlined at top level");
    }
#endif
    return false;
  }

  // Walk back up the JVM state to find the caller at the required
  // depth.
  JVMState* caller_jvms = jvms();

  // Cf. JVM_GetCallerClass
  // NOTE: Start the loop at depth 1 because the current JVM state does
  // not include the Reflection.getCallerClass() frame.
  for (int n = 1; caller_jvms != NULL; caller_jvms = caller_jvms->caller(), n++) {
    ciMethod* m = caller_jvms->method();
    switch (n) {
    case 0:
      fatal("current JVM state does not include the Reflection.getCallerClass frame");
      break;
    case 1:
      // Frame 0 and 1 must be caller sensitive (see JVM_GetCallerClass).
      if (!m->caller_sensitive()) {
#ifndef PRODUCT
        if ((PrintIntrinsics || PrintInlining || PrintOptoInlining) && Verbose) {
          tty->print_cr("  Bailing out: CallerSensitive annotation expected at frame %d", n);
        }
#endif
        return false;  // bail-out; let JVM_GetCallerClass do the work
      }
      break;
    default:
      if (!m->is_ignored_by_security_stack_walk()) {
        // We have reached the desired frame; return the holder class.
        // Acquire method holder as java.lang.Class and push as constant.
        ciInstanceKlass* caller_klass = caller_jvms->method()->holder();
        ciInstance* caller_mirror = caller_klass->java_mirror();
        set_result(makecon(TypeInstPtr::make(caller_mirror)));

#ifndef PRODUCT
        if ((PrintIntrinsics || PrintInlining || PrintOptoInlining) && Verbose) {
          tty->print_cr("  Succeeded: caller = %d) %s.%s, JVMS depth = %d", n, caller_klass->name()->as_utf8(), caller_jvms->method()->name()->as_utf8(), jvms()->depth());
          tty->print_cr("  JVM state at this point:");
          for (int i = jvms()->depth(), n = 1; i >= 1; i--, n++) {
            ciMethod* m = jvms()->of_depth(i)->method();
            tty->print_cr("   %d) %s.%s", n, m->holder()->name()->as_utf8(), m->name()->as_utf8());
          }
        }
#endif
        return true;
      }
      break;
    }
  }

#ifndef PRODUCT
  if ((PrintIntrinsics || PrintInlining || PrintOptoInlining) && Verbose) {
    tty->print_cr("  Bailing out because caller depth exceeded inlining depth = %d", jvms()->depth());
    tty->print_cr("  JVM state at this point:");
    for (int i = jvms()->depth(), n = 1; i >= 1; i--, n++) {
      ciMethod* m = jvms()->of_depth(i)->method();
      tty->print_cr("   %d) %s.%s", n, m->holder()->name()->as_utf8(), m->name()->as_utf8());
    }
  }
#endif

  return false;  // bail-out; let JVM_GetCallerClass do the work
}

bool LibraryCallKit::inline_fp_conversions(vmIntrinsics::ID id) {
  Node* arg = argument(0);
  Node* result;

  switch (id) {
  case vmIntrinsics::_floatToRawIntBits:    result = new (C) MoveF2INode(arg);  break;
  case vmIntrinsics::_intBitsToFloat:       result = new (C) MoveI2FNode(arg);  break;
  case vmIntrinsics::_doubleToRawLongBits:  result = new (C) MoveD2LNode(arg);  break;
  case vmIntrinsics::_longBitsToDouble:     result = new (C) MoveL2DNode(arg);  break;

  case vmIntrinsics::_doubleToLongBits: {
    // two paths (plus control) merge in a wood
    RegionNode *r = new (C) RegionNode(3);
    Node *phi = new (C) PhiNode(r, TypeLong::LONG);

    Node *cmpisnan = _gvn.transform(new (C) CmpDNode(arg, arg));
    // Build the boolean node
    Node *bolisnan = _gvn.transform(new (C) BoolNode(cmpisnan, BoolTest::ne));

    // Branch either way.
    // NaN case is less traveled, which makes all the difference.
    IfNode *ifisnan = create_and_xform_if(control(), bolisnan, PROB_STATIC_FREQUENT, COUNT_UNKNOWN);
    Node *opt_isnan = _gvn.transform(ifisnan);
    assert( opt_isnan->is_If(), "Expect an IfNode");
    IfNode *opt_ifisnan = (IfNode*)opt_isnan;
    Node *iftrue = _gvn.transform(new (C) IfTrueNode(opt_ifisnan));

    set_control(iftrue);

    static const jlong nan_bits = CONST64(0x7ff8000000000000);
    Node *slow_result = longcon(nan_bits); // return NaN
    phi->init_req(1, _gvn.transform( slow_result ));
    r->init_req(1, iftrue);

    // Else fall through
    Node *iffalse = _gvn.transform(new (C) IfFalseNode(opt_ifisnan));
    set_control(iffalse);

    phi->init_req(2, _gvn.transform(new (C) MoveD2LNode(arg)));
    r->init_req(2, iffalse);

    // Post merge
    set_control(_gvn.transform(r));
    record_for_igvn(r);

    C->set_has_split_ifs(true); // Has chance for split-if optimization
    result = phi;
    assert(result->bottom_type()->isa_long(), "must be");
    break;
  }

  case vmIntrinsics::_floatToIntBits: {
    // two paths (plus control) merge in a wood
    RegionNode *r = new (C) RegionNode(3);
    Node *phi = new (C) PhiNode(r, TypeInt::INT);

    Node *cmpisnan = _gvn.transform(new (C) CmpFNode(arg, arg));
    // Build the boolean node
    Node *bolisnan = _gvn.transform(new (C) BoolNode(cmpisnan, BoolTest::ne));

    // Branch either way.
    // NaN case is less traveled, which makes all the difference.
    IfNode *ifisnan = create_and_xform_if(control(), bolisnan, PROB_STATIC_FREQUENT, COUNT_UNKNOWN);
    Node *opt_isnan = _gvn.transform(ifisnan);
    assert( opt_isnan->is_If(), "Expect an IfNode");
    IfNode *opt_ifisnan = (IfNode*)opt_isnan;
    Node *iftrue = _gvn.transform(new (C) IfTrueNode(opt_ifisnan));

    set_control(iftrue);

    static const jint nan_bits = 0x7fc00000;
    Node *slow_result = makecon(TypeInt::make(nan_bits)); // return NaN
    phi->init_req(1, _gvn.transform( slow_result ));
    r->init_req(1, iftrue);

    // Else fall through
    Node *iffalse = _gvn.transform(new (C) IfFalseNode(opt_ifisnan));
    set_control(iffalse);

    phi->init_req(2, _gvn.transform(new (C) MoveF2INode(arg)));
    r->init_req(2, iffalse);

    // Post merge
    set_control(_gvn.transform(r));
    record_for_igvn(r);

    C->set_has_split_ifs(true); // Has chance for split-if optimization
    result = phi;
    assert(result->bottom_type()->isa_int(), "must be");
    break;
  }

  default:
    fatal_unexpected_iid(id);
    break;
  }
  set_result(_gvn.transform(result));
  return true;
}

#ifdef _LP64
#define XTOP ,top() /*additional argument*/
#else  //_LP64
#define XTOP        /*no additional argument*/
#endif //_LP64

//----------------------inline_unsafe_copyMemory-------------------------
// public native void sun.misc.Unsafe.copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes);
bool LibraryCallKit::inline_unsafe_copyMemory() {
  if (callee()->is_static())  return false;  // caller must have the capability!
  null_check_receiver();  // null-check receiver
  if (stopped())  return true;

  C->set_has_unsafe_access(true);  // Mark eventual nmethod as "unsafe".

  Node* src_ptr =         argument(1);   // type: oop
  Node* src_off = ConvL2X(argument(2));  // type: long
  Node* dst_ptr =         argument(4);   // type: oop
  Node* dst_off = ConvL2X(argument(5));  // type: long
  Node* size    = ConvL2X(argument(7));  // type: long

  assert(Unsafe_field_offset_to_byte_offset(11) == 11,
         "fieldOffset must be byte-scaled");

  Node* src = make_unsafe_address(src_ptr, src_off);
  Node* dst = make_unsafe_address(dst_ptr, dst_off);

  // Conservatively insert a memory barrier on all memory slices.
  // Do not let writes of the copy source or destination float below the copy.
  insert_mem_bar(Op_MemBarCPUOrder);

  // Call it.  Note that the length argument is not scaled.
  make_runtime_call(RC_LEAF|RC_NO_FP,
                    OptoRuntime::fast_arraycopy_Type(),
                    StubRoutines::unsafe_arraycopy(),
                    "unsafe_arraycopy",
                    TypeRawPtr::BOTTOM,
                    src, dst, size XTOP);

  // Do not let reads of the copy destination float above the copy.
  insert_mem_bar(Op_MemBarCPUOrder);

  return true;
}

//------------------------clone_coping-----------------------------------
// Helper function for inline_native_clone.
void LibraryCallKit::copy_to_clone(Node* obj, Node* alloc_obj, Node* obj_size, bool is_array, bool card_mark) {
  assert(obj_size != NULL, "");
  Node* raw_obj = alloc_obj->in(1);
  assert(alloc_obj->is_CheckCastPP() && raw_obj->is_Proj() && raw_obj->in(0)->is_Allocate(), "");

  AllocateNode* alloc = NULL;
  if (ReduceBulkZeroing) {
    // We will be completely responsible for initializing this object -
    // mark Initialize node as complete.
    alloc = AllocateNode::Ideal_allocation(alloc_obj, &_gvn);
    // The object was just allocated - there should be no any stores!
    guarantee(alloc != NULL && alloc->maybe_set_complete(&_gvn), "");
    // Mark as complete_with_arraycopy so that on AllocateNode
    // expansion, we know this AllocateNode is initialized by an array
    // copy and a StoreStore barrier exists after the array copy.
    alloc->initialization()->set_complete_with_arraycopy();
  }

  // Copy the fastest available way.
  // TODO: generate fields copies for small objects instead.
  Node* src  = obj;
  Node* dest = alloc_obj;
  Node* size = _gvn.transform(obj_size);

  // Exclude the header but include array length to copy by 8 bytes words.
  // Can't use base_offset_in_bytes(bt) since basic type is unknown.
  int base_off = is_array ? arrayOopDesc::length_offset_in_bytes() :
                            instanceOopDesc::base_offset_in_bytes();
  // base_off:
  // 8  - 32-bit VM
  // 12 - 64-bit VM, compressed klass
  // 16 - 64-bit VM, normal klass
  if (base_off % BytesPerLong != 0) {
    assert(UseCompressedClassPointers, "");
    if (is_array) {
      // Exclude length to copy by 8 bytes words.
      base_off += sizeof(int);
    } else {
      // Include klass to copy by 8 bytes words.
      base_off = instanceOopDesc::klass_offset_in_bytes();
    }
    assert(base_off % BytesPerLong == 0, "expect 8 bytes alignment");
  }
  src  = basic_plus_adr(src,  base_off);
  dest = basic_plus_adr(dest, base_off);

  // Compute the length also, if needed:
  Node* countx = size;
  countx = _gvn.transform(new (C) SubXNode(countx, MakeConX(base_off)));
  countx = _gvn.transform(new (C) URShiftXNode(countx, intcon(LogBytesPerLong) ));

  const TypePtr* raw_adr_type = TypeRawPtr::BOTTOM;
  bool disjoint_bases = true;
  generate_unchecked_arraycopy(raw_adr_type, T_LONG, disjoint_bases,
                               src, NULL, dest, NULL, countx,
                               /*dest_uninitialized*/true);

  // If necessary, emit some card marks afterwards.  (Non-arrays only.)
  if (card_mark) {
    assert(!is_array, "");
    // Put in store barrier for any and all oops we are sticking
    // into this object.  (We could avoid this if we could prove
    // that the object type contains no oop fields at all.)
    Node* no_particular_value = NULL;
    Node* no_particular_field = NULL;
    int raw_adr_idx = Compile::AliasIdxRaw;
    post_barrier(control(),
                 memory(raw_adr_type),
                 alloc_obj,
                 no_particular_field,
                 raw_adr_idx,
                 no_particular_value,
                 T_OBJECT,
                 false);
  }

  // Do not let reads from the cloned object float above the arraycopy.
  if (alloc != NULL) {
    // Do not let stores that initialize this object be reordered with
    // a subsequent store that would make this object accessible by
    // other threads.
    // Record what AllocateNode this StoreStore protects so that
    // escape analysis can go from the MemBarStoreStoreNode to the
    // AllocateNode and eliminate the MemBarStoreStoreNode if possible
    // based on the escape status of the AllocateNode.
    insert_mem_bar(Op_MemBarStoreStore, alloc->proj_out(AllocateNode::RawAddress));
  } else {
    insert_mem_bar(Op_MemBarCPUOrder);
  }
}

//------------------------inline_native_clone----------------------------
// protected native Object java.lang.Object.clone();
//
// Here are the simple edge cases:
//  null receiver => normal trap
//  virtual and clone was overridden => slow path to out-of-line clone
//  not cloneable or finalizer => slow path to out-of-line Object.clone
//
// The general case has two steps, allocation and copying.
// Allocation has two cases, and uses GraphKit::new_instance or new_array.
//
// Copying also has two cases, oop arrays and everything else.
// Oop arrays use arrayof_oop_arraycopy (same as System.arraycopy).
// Everything else uses the tight inline loop supplied by CopyArrayNode.
//
// These steps fold up nicely if and when the cloned object's klass
// can be sharply typed as an object array, a type array, or an instance.
//
bool LibraryCallKit::inline_native_clone(bool is_virtual) {
  PhiNode* result_val;

  // Set the reexecute bit for the interpreter to reexecute
  // the bytecode that invokes Object.clone if deoptimization happens.
  { PreserveReexecuteState preexecs(this);
    jvms()->set_should_reexecute(true);

    Node* obj = null_check_receiver();
    if (stopped())  return true;

    Node* obj_klass = load_object_klass(obj);
    const TypeKlassPtr* tklass = _gvn.type(obj_klass)->isa_klassptr();
    const TypeOopPtr*   toop   = ((tklass != NULL)
                                ? tklass->as_instance_type()
                                : TypeInstPtr::NOTNULL);

    // Conservatively insert a memory barrier on all memory slices.
    // Do not let writes into the original float below the clone.
    insert_mem_bar(Op_MemBarCPUOrder);

    // paths into result_reg:
    enum {
      _slow_path = 1,     // out-of-line call to clone method (virtual or not)
      _objArray_path,     // plain array allocation, plus arrayof_oop_arraycopy
      _array_path,        // plain array allocation, plus arrayof_long_arraycopy
      _instance_path,     // plain instance allocation, plus arrayof_long_arraycopy
      PATH_LIMIT
    };
    RegionNode* result_reg = new(C) RegionNode(PATH_LIMIT);
    result_val             = new(C) PhiNode(result_reg,
                                            TypeInstPtr::NOTNULL);
    PhiNode*    result_i_o = new(C) PhiNode(result_reg, Type::ABIO);
    PhiNode*    result_mem = new(C) PhiNode(result_reg, Type::MEMORY,
                                            TypePtr::BOTTOM);
    record_for_igvn(result_reg);

    const TypePtr* raw_adr_type = TypeRawPtr::BOTTOM;
    int raw_adr_idx = Compile::AliasIdxRaw;

    Node* array_ctl = generate_array_guard(obj_klass, (RegionNode*)NULL);
    if (array_ctl != NULL) {
      // It's an array.
      PreserveJVMState pjvms(this);
      set_control(array_ctl);
      Node* obj_length = load_array_length(obj);
      Node* obj_size  = NULL;
      Node* alloc_obj = new_array(obj_klass, obj_length, 0, &obj_size);  // no arguments to push

      if (!use_ReduceInitialCardMarks()) {
        // If it is an oop array, it requires very special treatment,
        // because card marking is required on each card of the array.
        Node* is_obja = generate_objArray_guard(obj_klass, (RegionNode*)NULL);
        if (is_obja != NULL) {
          PreserveJVMState pjvms2(this);
          set_control(is_obja);
          // Generate a direct call to the right arraycopy function(s).
          bool disjoint_bases = true;
          bool length_never_negative = true;
          generate_arraycopy(TypeAryPtr::OOPS, T_OBJECT,
                             obj, intcon(0), alloc_obj, intcon(0),
                             obj_length,
                             disjoint_bases, length_never_negative);
          result_reg->init_req(_objArray_path, control());
          result_val->init_req(_objArray_path, alloc_obj);
          result_i_o ->set_req(_objArray_path, i_o());
          result_mem ->set_req(_objArray_path, reset_memory());
        }
      }
      // Otherwise, there are no card marks to worry about.
      // (We can dispense with card marks if we know the allocation
      //  comes out of eden (TLAB)...  In fact, ReduceInitialCardMarks
      //  causes the non-eden paths to take compensating steps to
      //  simulate a fresh allocation, so that no further
      //  card marks are required in compiled code to initialize
      //  the object.)

      if (!stopped()) {
        copy_to_clone(obj, alloc_obj, obj_size, true, false);

        // Present the results of the copy.
        result_reg->init_req(_array_path, control());
        result_val->init_req(_array_path, alloc_obj);
        result_i_o ->set_req(_array_path, i_o());
        result_mem ->set_req(_array_path, reset_memory());
      }
    }

    // We only go to the instance fast case code if we pass a number of guards.
    // The paths which do not pass are accumulated in the slow_region.
    RegionNode* slow_region = new (C) RegionNode(1);
    record_for_igvn(slow_region);
    if (!stopped()) {
      // It's an instance (we did array above).  Make the slow-path tests.
      // If this is a virtual call, we generate a funny guard.  We grab
      // the vtable entry corresponding to clone() from the target object.
      // If the target method which we are calling happens to be the
      // Object clone() method, we pass the guard.  We do not need this
      // guard for non-virtual calls; the caller is known to be the native
      // Object clone().
      if (is_virtual) {
        generate_virtual_guard(obj_klass, slow_region);
      }

      // The object must be cloneable and must not have a finalizer.
      // Both of these conditions may be checked in a single test.
      // We could optimize the cloneable test further, but we don't care.
      generate_access_flags_guard(obj_klass,
                                  // Test both conditions:
                                  JVM_ACC_IS_CLONEABLE | JVM_ACC_HAS_FINALIZER,
                                  // Must be cloneable but not finalizer:
                                  JVM_ACC_IS_CLONEABLE,
                                  slow_region);
    }

    if (!stopped()) {
      // It's an instance, and it passed the slow-path tests.
      PreserveJVMState pjvms(this);
      Node* obj_size  = NULL;
      Node* alloc_obj = new_instance(obj_klass, NULL, &obj_size);

      copy_to_clone(obj, alloc_obj, obj_size, false, !use_ReduceInitialCardMarks());

      // Present the results of the slow call.
      result_reg->init_req(_instance_path, control());
      result_val->init_req(_instance_path, alloc_obj);
      result_i_o ->set_req(_instance_path, i_o());
      result_mem ->set_req(_instance_path, reset_memory());
    }

    // Generate code for the slow case.  We make a call to clone().
    set_control(_gvn.transform(slow_region));
    if (!stopped()) {
      PreserveJVMState pjvms(this);
      CallJavaNode* slow_call = generate_method_call(vmIntrinsics::_clone, is_virtual);
      Node* slow_result = set_results_for_java_call(slow_call);
      // this->control() comes from set_results_for_java_call
      result_reg->init_req(_slow_path, control());
      result_val->init_req(_slow_path, slow_result);
      result_i_o ->set_req(_slow_path, i_o());
      result_mem ->set_req(_slow_path, reset_memory());
    }

    // Return the combined state.
    set_control(    _gvn.transform(result_reg));
    set_i_o(        _gvn.transform(result_i_o));
    set_all_memory( _gvn.transform(result_mem));
  } // original reexecute is set back here

  set_result(_gvn.transform(result_val));
  return true;
}

//------------------------------basictype2arraycopy----------------------------
address LibraryCallKit::basictype2arraycopy(BasicType t,
                                            Node* src_offset,
                                            Node* dest_offset,
                                            bool disjoint_bases,
                                            const char* &name,
                                            bool dest_uninitialized) {
  const TypeInt* src_offset_inttype  = gvn().find_int_type(src_offset);;
  const TypeInt* dest_offset_inttype = gvn().find_int_type(dest_offset);;

  bool aligned = false;
  bool disjoint = disjoint_bases;

  // if the offsets are the same, we can treat the memory regions as
  // disjoint, because either the memory regions are in different arrays,
  // or they are identical (which we can treat as disjoint.)  We can also
  // treat a copy with a destination index  less that the source index
  // as disjoint since a low->high copy will work correctly in this case.
  if (src_offset_inttype != NULL && src_offset_inttype->is_con() &&
      dest_offset_inttype != NULL && dest_offset_inttype->is_con()) {
    // both indices are constants
    int s_offs = src_offset_inttype->get_con();
    int d_offs = dest_offset_inttype->get_con();
    int element_size = type2aelembytes(t);
    aligned = ((arrayOopDesc::base_offset_in_bytes(t) + s_offs * element_size) % HeapWordSize == 0) &&
              ((arrayOopDesc::base_offset_in_bytes(t) + d_offs * element_size) % HeapWordSize == 0);
    if (s_offs >= d_offs)  disjoint = true;
  } else if (src_offset == dest_offset && src_offset != NULL) {
    // This can occur if the offsets are identical non-constants.
    disjoint = true;
  }

  return StubRoutines::select_arraycopy_function(t, aligned, disjoint, name, dest_uninitialized);
}


//------------------------------inline_arraycopy-----------------------
// public static native void java.lang.System.arraycopy(Object src,  int  srcPos,
//                                                      Object dest, int destPos,
//                                                      int length);
bool LibraryCallKit::inline_arraycopy() {
  // Get the arguments.
  Node* src         = argument(0);  // type: oop
  Node* src_offset  = argument(1);  // type: int
  Node* dest        = argument(2);  // type: oop
  Node* dest_offset = argument(3);  // type: int
  Node* length      = argument(4);  // type: int

  // Compile time checks.  If any of these checks cannot be verified at compile time,
  // we do not make a fast path for this call.  Instead, we let the call remain as it
  // is.  The checks we choose to mandate at compile time are:
  //
  // (1) src and dest are arrays.
  const Type* src_type  = src->Value(&_gvn);
  const Type* dest_type = dest->Value(&_gvn);
  const TypeAryPtr* top_src  = src_type->isa_aryptr();
  const TypeAryPtr* top_dest = dest_type->isa_aryptr();
  if (top_src  == NULL || top_src->klass()  == NULL ||
      top_dest == NULL || top_dest->klass() == NULL) {
    // Conservatively insert a memory barrier on all memory slices.
    // Do not let writes into the source float below the arraycopy.
    insert_mem_bar(Op_MemBarCPUOrder);

    // Call StubRoutines::generic_arraycopy stub.
    generate_arraycopy(TypeRawPtr::BOTTOM, T_CONFLICT,
                       src, src_offset, dest, dest_offset, length);

    // Do not let reads from the destination float above the arraycopy.
    // Since we cannot type the arrays, we don't know which slices
    // might be affected.  We could restrict this barrier only to those
    // memory slices which pertain to array elements--but don't bother.
    if (!InsertMemBarAfterArraycopy)
      // (If InsertMemBarAfterArraycopy, there is already one in place.)
      insert_mem_bar(Op_MemBarCPUOrder);
    return true;
  }

  // (2) src and dest arrays must have elements of the same BasicType
  // Figure out the size and type of the elements we will be copying.
  BasicType src_elem  =  top_src->klass()->as_array_klass()->element_type()->basic_type();
  BasicType dest_elem = top_dest->klass()->as_array_klass()->element_type()->basic_type();
  if (src_elem  == T_ARRAY)  src_elem  = T_OBJECT;
  if (dest_elem == T_ARRAY)  dest_elem = T_OBJECT;

  if (src_elem != dest_elem || dest_elem == T_VOID) {
    // The component types are not the same or are not recognized.  Punt.
    // (But, avoid the native method wrapper to JVM_ArrayCopy.)
    generate_slow_arraycopy(TypePtr::BOTTOM,
                            src, src_offset, dest, dest_offset, length,
                            /*dest_uninitialized*/false);
    return true;
  }

  //---------------------------------------------------------------------------
  // We will make a fast path for this call to arraycopy.

  // We have the following tests left to perform:
  //
  // (3) src and dest must not be null.
  // (4) src_offset must not be negative.
  // (5) dest_offset must not be negative.
  // (6) length must not be negative.
  // (7) src_offset + length must not exceed length of src.
  // (8) dest_offset + length must not exceed length of dest.
  // (9) each element of an oop array must be assignable

  RegionNode* slow_region = new (C) RegionNode(1);
  record_for_igvn(slow_region);

  // (3) operands must not be null
  // We currently perform our null checks with the null_check routine.
  // This means that the null exceptions will be reported in the caller
  // rather than (correctly) reported inside of the native arraycopy call.
  // This should be corrected, given time.  We do our null check with the
  // stack pointer restored.
  src  = null_check(src,  T_ARRAY);
  dest = null_check(dest, T_ARRAY);

  // (4) src_offset must not be negative.
  generate_negative_guard(src_offset, slow_region);

  // (5) dest_offset must not be negative.
  generate_negative_guard(dest_offset, slow_region);

  // (6) length must not be negative (moved to generate_arraycopy()).
  // generate_negative_guard(length, slow_region);

  // (7) src_offset + length must not exceed length of src.
  generate_limit_guard(src_offset, length,
                       load_array_length(src),
                       slow_region);

  // (8) dest_offset + length must not exceed length of dest.
  generate_limit_guard(dest_offset, length,
                       load_array_length(dest),
                       slow_region);

  // (9) each element of an oop array must be assignable
  // The generate_arraycopy subroutine checks this.

  // This is where the memory effects are placed:
  const TypePtr* adr_type = TypeAryPtr::get_array_body_type(dest_elem);
  generate_arraycopy(adr_type, dest_elem,
                     src, src_offset, dest, dest_offset, length,
                     false, false, slow_region);

  return true;
}

//-----------------------------generate_arraycopy----------------------
// Generate an optimized call to arraycopy.
// Caller must guard against non-arrays.
// Caller must determine a common array basic-type for both arrays.
// Caller must validate offsets against array bounds.
// The slow_region has already collected guard failure paths
// (such as out of bounds length or non-conformable array types).
// The generated code has this shape, in general:
//
//     if (length == 0)  return   // via zero_path
//     slowval = -1
//     if (types unknown) {
//       slowval = call generic copy loop
//       if (slowval == 0)  return  // via checked_path
//     } else if (indexes in bounds) {
//       if ((is object array) && !(array type check)) {
//         slowval = call checked copy loop
//         if (slowval == 0)  return  // via checked_path
//       } else {
//         call bulk copy loop
//         return  // via fast_path
//       }
//     }
//     // adjust params for remaining work:
//     if (slowval != -1) {
//       n = -1^slowval; src_offset += n; dest_offset += n; length -= n
//     }
//   slow_region:
//     call slow arraycopy(src, src_offset, dest, dest_offset, length)
//     return  // via slow_call_path
//
// This routine is used from several intrinsics:  System.arraycopy,
// Object.clone (the array subcase), and Arrays.copyOf[Range].
//
void
LibraryCallKit::generate_arraycopy(const TypePtr* adr_type,
                                   BasicType basic_elem_type,
                                   Node* src,  Node* src_offset,
                                   Node* dest, Node* dest_offset,
                                   Node* copy_length,
                                   bool disjoint_bases,
                                   bool length_never_negative,
                                   RegionNode* slow_region) {

  if (slow_region == NULL) {
    slow_region = new(C) RegionNode(1);
    record_for_igvn(slow_region);
  }

  Node* original_dest      = dest;
  AllocateArrayNode* alloc = NULL;  // used for zeroing, if needed
  bool  dest_uninitialized = false;

  // See if this is the initialization of a newly-allocated array.
  // If so, we will take responsibility here for initializing it to zero.
  // (Note:  Because tightly_coupled_allocation performs checks on the
  // out-edges of the dest, we need to avoid making derived pointers
  // from it until we have checked its uses.)
  if (ReduceBulkZeroing
      && !ZeroTLAB              // pointless if already zeroed
      && basic_elem_type != T_CONFLICT // avoid corner case
      && !src->eqv_uncast(dest)
      && ((alloc = tightly_coupled_allocation(dest, slow_region))
          != NULL)
      && _gvn.find_int_con(alloc->in(AllocateNode::ALength), 1) > 0
      && alloc->maybe_set_complete(&_gvn)) {
    // "You break it, you buy it."
    InitializeNode* init = alloc->initialization();
    assert(init->is_complete(), "we just did this");
    init->set_complete_with_arraycopy();
    assert(dest->is_CheckCastPP(), "sanity");
    assert(dest->in(0)->in(0) == init, "dest pinned");
    adr_type = TypeRawPtr::BOTTOM;  // all initializations are into raw memory
    // From this point on, every exit path is responsible for
    // initializing any non-copied parts of the object to zero.
    // Also, if this flag is set we make sure that arraycopy interacts properly
    // with G1, eliding pre-barriers. See CR 6627983.
    dest_uninitialized = true;
  } else {
    // No zeroing elimination here.
    alloc             = NULL;
    //original_dest   = dest;
    //dest_uninitialized = false;
  }

  // Results are placed here:
  enum { fast_path        = 1,  // normal void-returning assembly stub
         checked_path     = 2,  // special assembly stub with cleanup
         slow_call_path   = 3,  // something went wrong; call the VM
         zero_path        = 4,  // bypass when length of copy is zero
         bcopy_path       = 5,  // copy primitive array by 64-bit blocks
         PATH_LIMIT       = 6
  };
  RegionNode* result_region = new(C) RegionNode(PATH_LIMIT);
  PhiNode*    result_i_o    = new(C) PhiNode(result_region, Type::ABIO);
  PhiNode*    result_memory = new(C) PhiNode(result_region, Type::MEMORY, adr_type);
  record_for_igvn(result_region);
  _gvn.set_type_bottom(result_i_o);
  _gvn.set_type_bottom(result_memory);
  assert(adr_type != TypePtr::BOTTOM, "must be RawMem or a T[] slice");

  // The slow_control path:
  Node* slow_control;
  Node* slow_i_o = i_o();
  Node* slow_mem = memory(adr_type);
  debug_only(slow_control = (Node*) badAddress);

  // Checked control path:
  Node* checked_control = top();
  Node* checked_mem     = NULL;
  Node* checked_i_o     = NULL;
  Node* checked_value   = NULL;

  if (basic_elem_type == T_CONFLICT) {
    assert(!dest_uninitialized, "");
    Node* cv = generate_generic_arraycopy(adr_type,
                                          src, src_offset, dest, dest_offset,
                                          copy_length, dest_uninitialized);
    if (cv == NULL)  cv = intcon(-1);  // failure (no stub available)
    checked_control = control();
    checked_i_o     = i_o();
    checked_mem     = memory(adr_type);
    checked_value   = cv;
    set_control(top());         // no fast path
  }

  Node* not_pos = generate_nonpositive_guard(copy_length, length_never_negative);
  if (not_pos != NULL) {
    PreserveJVMState pjvms(this);
    set_control(not_pos);

    // (6) length must not be negative.
    if (!length_never_negative) {
      generate_negative_guard(copy_length, slow_region);
    }

    // copy_length is 0.
    if (!stopped() && dest_uninitialized) {
      Node* dest_length = alloc->in(AllocateNode::ALength);
      if (copy_length->eqv_uncast(dest_length)
          || _gvn.find_int_con(dest_length, 1) <= 0) {
        // There is no zeroing to do. No need for a secondary raw memory barrier.
      } else {
        // Clear the whole thing since there are no source elements to copy.
        generate_clear_array(adr_type, dest, basic_elem_type,
                             intcon(0), NULL,
                             alloc->in(AllocateNode::AllocSize));
        // Use a secondary InitializeNode as raw memory barrier.
        // Currently it is needed only on this path since other
        // paths have stub or runtime calls as raw memory barriers.
        InitializeNode* init = insert_mem_bar_volatile(Op_Initialize,
                                                       Compile::AliasIdxRaw,
                                                       top())->as_Initialize();
        init->set_complete(&_gvn);  // (there is no corresponding AllocateNode)
      }
    }

    // Present the results of the fast call.
    result_region->init_req(zero_path, control());
    result_i_o   ->init_req(zero_path, i_o());
    result_memory->init_req(zero_path, memory(adr_type));
  }

  if (!stopped() && dest_uninitialized) {
    // We have to initialize the *uncopied* part of the array to zero.
    // The copy destination is the slice dest[off..off+len].  The other slices
    // are dest_head = dest[0..off] and dest_tail = dest[off+len..dest.length].
    Node* dest_size   = alloc->in(AllocateNode::AllocSize);
    Node* dest_length = alloc->in(AllocateNode::ALength);
    Node* dest_tail   = _gvn.transform(new(C) AddINode(dest_offset,
                                                          copy_length));

    // If there is a head section that needs zeroing, do it now.
    if (find_int_con(dest_offset, -1) != 0) {
      generate_clear_array(adr_type, dest, basic_elem_type,
                           intcon(0), dest_offset,
                           NULL);
    }

    // Next, perform a dynamic check on the tail length.
    // It is often zero, and we can win big if we prove this.
    // There are two wins:  Avoid generating the ClearArray
    // with its attendant messy index arithmetic, and upgrade
    // the copy to a more hardware-friendly word size of 64 bits.
    Node* tail_ctl = NULL;
    if (!stopped() && !dest_tail->eqv_uncast(dest_length)) {
      Node* cmp_lt   = _gvn.transform(new(C) CmpINode(dest_tail, dest_length));
      Node* bol_lt   = _gvn.transform(new(C) BoolNode(cmp_lt, BoolTest::lt));
      tail_ctl = generate_slow_guard(bol_lt, NULL);
      assert(tail_ctl != NULL || !stopped(), "must be an outcome");
    }

    // At this point, let's assume there is no tail.
    if (!stopped() && alloc != NULL && basic_elem_type != T_OBJECT) {
      // There is no tail.  Try an upgrade to a 64-bit copy.
      bool didit = false;
      { PreserveJVMState pjvms(this);
        didit = generate_block_arraycopy(adr_type, basic_elem_type, alloc,
                                         src, src_offset, dest, dest_offset,
                                         dest_size, dest_uninitialized);
        if (didit) {
          // Present the results of the block-copying fast call.
          result_region->init_req(bcopy_path, control());
          result_i_o   ->init_req(bcopy_path, i_o());
          result_memory->init_req(bcopy_path, memory(adr_type));
        }
      }
      if (didit)
        set_control(top());     // no regular fast path
    }

    // Clear the tail, if any.
    if (tail_ctl != NULL) {
      Node* notail_ctl = stopped() ? NULL : control();
      set_control(tail_ctl);
      if (notail_ctl == NULL) {
        generate_clear_array(adr_type, dest, basic_elem_type,
                             dest_tail, NULL,
                             dest_size);
      } else {
        // Make a local merge.
        Node* done_ctl = new(C) RegionNode(3);
        Node* done_mem = new(C) PhiNode(done_ctl, Type::MEMORY, adr_type);
        done_ctl->init_req(1, notail_ctl);
        done_mem->init_req(1, memory(adr_type));
        generate_clear_array(adr_type, dest, basic_elem_type,
                             dest_tail, NULL,
                             dest_size);
        done_ctl->init_req(2, control());
        done_mem->init_req(2, memory(adr_type));
        set_control( _gvn.transform(done_ctl));
        set_memory(  _gvn.transform(done_mem), adr_type );
      }
    }
  }

  BasicType copy_type = basic_elem_type;
  assert(basic_elem_type != T_ARRAY, "caller must fix this");
  if (!stopped() && copy_type == T_OBJECT) {
    // If src and dest have compatible element types, we can copy bits.
    // Types S[] and D[] are compatible if D is a supertype of S.
    //
    // If they are not, we will use checked_oop_disjoint_arraycopy,
    // which performs a fast optimistic per-oop check, and backs off
    // further to JVM_ArrayCopy on the first per-oop check that fails.
    // (Actually, we don't move raw bits only; the GC requires card marks.)

    // Get the Klass* for both src and dest
    Node* src_klass  = load_object_klass(src);
    Node* dest_klass = load_object_klass(dest);

    // Generate the subtype check.
    // This might fold up statically, or then again it might not.
    //
    // Non-static example:  Copying List<String>.elements to a new String[].
    // The backing store for a List<String> is always an Object[],
    // but its elements are always type String, if the generic types
    // are correct at the source level.
    //
    // Test S[] against D[], not S against D, because (probably)
    // the secondary supertype cache is less busy for S[] than S.
    // This usually only matters when D is an interface.
    Node* not_subtype_ctrl = gen_subtype_check(src_klass, dest_klass);
    // Plug failing path into checked_oop_disjoint_arraycopy
    if (not_subtype_ctrl != top()) {
      PreserveJVMState pjvms(this);
      set_control(not_subtype_ctrl);
      // (At this point we can assume disjoint_bases, since types differ.)
      int ek_offset = in_bytes(ObjArrayKlass::element_klass_offset());
      Node* p1 = basic_plus_adr(dest_klass, ek_offset);
      Node* n1 = LoadKlassNode::make(_gvn, immutable_memory(), p1, TypeRawPtr::BOTTOM);
      Node* dest_elem_klass = _gvn.transform(n1);
      Node* cv = generate_checkcast_arraycopy(adr_type,
                                              dest_elem_klass,
                                              src, src_offset, dest, dest_offset,
                                              ConvI2X(copy_length), dest_uninitialized);
      if (cv == NULL)  cv = intcon(-1);  // failure (no stub available)
      checked_control = control();
      checked_i_o     = i_o();
      checked_mem     = memory(adr_type);
      checked_value   = cv;
    }
    // At this point we know we do not need type checks on oop stores.

    // Let's see if we need card marks:
    if (alloc != NULL && use_ReduceInitialCardMarks()) {
      // If we do not need card marks, copy using the jint or jlong stub.
      copy_type = LP64_ONLY(UseCompressedOops ? T_INT : T_LONG) NOT_LP64(T_INT);
      assert(type2aelembytes(basic_elem_type) == type2aelembytes(copy_type),
             "sizes agree");
    }
  }

  if (!stopped()) {
    // Generate the fast path, if possible.
    PreserveJVMState pjvms(this);
    generate_unchecked_arraycopy(adr_type, copy_type, disjoint_bases,
                                 src, src_offset, dest, dest_offset,
                                 ConvI2X(copy_length), dest_uninitialized);

    // Present the results of the fast call.
    result_region->init_req(fast_path, control());
    result_i_o   ->init_req(fast_path, i_o());
    result_memory->init_req(fast_path, memory(adr_type));
  }

  // Here are all the slow paths up to this point, in one bundle:
  slow_control = top();
  if (slow_region != NULL)
    slow_control = _gvn.transform(slow_region);
  DEBUG_ONLY(slow_region = (RegionNode*)badAddress);

  set_control(checked_control);
  if (!stopped()) {
    // Clean up after the checked call.
    // The returned value is either 0 or -1^K,
    // where K = number of partially transferred array elements.
    Node* cmp = _gvn.transform(new(C) CmpINode(checked_value, intcon(0)));
    Node* bol = _gvn.transform(new(C) BoolNode(cmp, BoolTest::eq));
    IfNode* iff = create_and_map_if(control(), bol, PROB_MAX, COUNT_UNKNOWN);

    // If it is 0, we are done, so transfer to the end.
    Node* checks_done = _gvn.transform(new(C) IfTrueNode(iff));
    result_region->init_req(checked_path, checks_done);
    result_i_o   ->init_req(checked_path, checked_i_o);
    result_memory->init_req(checked_path, checked_mem);

    // If it is not zero, merge into the slow call.
    set_control( _gvn.transform(new(C) IfFalseNode(iff) ));
    RegionNode* slow_reg2 = new(C) RegionNode(3);
    PhiNode*    slow_i_o2 = new(C) PhiNode(slow_reg2, Type::ABIO);
    PhiNode*    slow_mem2 = new(C) PhiNode(slow_reg2, Type::MEMORY, adr_type);
    record_for_igvn(slow_reg2);
    slow_reg2  ->init_req(1, slow_control);
    slow_i_o2  ->init_req(1, slow_i_o);
    slow_mem2  ->init_req(1, slow_mem);
    slow_reg2  ->init_req(2, control());
    slow_i_o2  ->init_req(2, checked_i_o);
    slow_mem2  ->init_req(2, checked_mem);

    slow_control = _gvn.transform(slow_reg2);
    slow_i_o     = _gvn.transform(slow_i_o2);
    slow_mem     = _gvn.transform(slow_mem2);

    if (alloc != NULL) {
      // We'll restart from the very beginning, after zeroing the whole thing.
      // This can cause double writes, but that's OK since dest is brand new.
      // So we ignore the low 31 bits of the value returned from the stub.
    } else {
      // We must continue the copy exactly where it failed, or else
      // another thread might see the wrong number of writes to dest.
      Node* checked_offset = _gvn.transform(new(C) XorINode(checked_value, intcon(-1)));
      Node* slow_offset    = new(C) PhiNode(slow_reg2, TypeInt::INT);
      slow_offset->init_req(1, intcon(0));
      slow_offset->init_req(2, checked_offset);
      slow_offset  = _gvn.transform(slow_offset);

      // Adjust the arguments by the conditionally incoming offset.
      Node* src_off_plus  = _gvn.transform(new(C) AddINode(src_offset,  slow_offset));
      Node* dest_off_plus = _gvn.transform(new(C) AddINode(dest_offset, slow_offset));
      Node* length_minus  = _gvn.transform(new(C) SubINode(copy_length, slow_offset));

      // Tweak the node variables to adjust the code produced below:
      src_offset  = src_off_plus;
      dest_offset = dest_off_plus;
      copy_length = length_minus;
    }
  }

  set_control(slow_control);
  if (!stopped()) {
    // Generate the slow path, if needed.
    PreserveJVMState pjvms(this);   // replace_in_map may trash the map

    set_memory(slow_mem, adr_type);
    set_i_o(slow_i_o);

    if (dest_uninitialized) {
      generate_clear_array(adr_type, dest, basic_elem_type,
                           intcon(0), NULL,
                           alloc->in(AllocateNode::AllocSize));
    }

    generate_slow_arraycopy(adr_type,
                            src, src_offset, dest, dest_offset,
                            copy_length, /*dest_uninitialized*/false);

    result_region->init_req(slow_call_path, control());
    result_i_o   ->init_req(slow_call_path, i_o());
    result_memory->init_req(slow_call_path, memory(adr_type));
  }

  // Remove unused edges.
  for (uint i = 1; i < result_region->req(); i++) {
    if (result_region->in(i) == NULL)
      result_region->init_req(i, top());
  }

  // Finished; return the combined state.
  set_control( _gvn.transform(result_region));
  set_i_o(     _gvn.transform(result_i_o)    );
  set_memory(  _gvn.transform(result_memory), adr_type );

  // The memory edges above are precise in order to model effects around
  // array copies accurately to allow value numbering of field loads around
  // arraycopy.  Such field loads, both before and after, are common in Java
  // collections and similar classes involving header/array data structures.
  //
  // But with low number of register or when some registers are used or killed
  // by arraycopy calls it causes registers spilling on stack. See 6544710.
  // The next memory barrier is added to avoid it. If the arraycopy can be
  // optimized away (which it can, sometimes) then we can manually remove
  // the membar also.
  //
  // Do not let reads from the cloned object float above the arraycopy.
  if (alloc != NULL) {
    // Do not let stores that initialize this object be reordered with
    // a subsequent store that would make this object accessible by
    // other threads.
    // Record what AllocateNode this StoreStore protects so that
    // escape analysis can go from the MemBarStoreStoreNode to the
    // AllocateNode and eliminate the MemBarStoreStoreNode if possible
    // based on the escape status of the AllocateNode.
    insert_mem_bar(Op_MemBarStoreStore, alloc->proj_out(AllocateNode::RawAddress));
  } else if (InsertMemBarAfterArraycopy)
    insert_mem_bar(Op_MemBarCPUOrder);
}


// Helper function which determines if an arraycopy immediately follows
// an allocation, with no intervening tests or other escapes for the object.
AllocateArrayNode*
LibraryCallKit::tightly_coupled_allocation(Node* ptr,
                                           RegionNode* slow_region) {
  if (stopped())             return NULL;  // no fast path
  if (C->AliasLevel() == 0)  return NULL;  // no MergeMems around

  AllocateArrayNode* alloc = AllocateArrayNode::Ideal_array_allocation(ptr, &_gvn);
  if (alloc == NULL)  return NULL;

  Node* rawmem = memory(Compile::AliasIdxRaw);
  // Is the allocation's memory state untouched?
  if (!(rawmem->is_Proj() && rawmem->in(0)->is_Initialize())) {
    // Bail out if there have been raw-memory effects since the allocation.
    // (Example:  There might have been a call or safepoint.)
    return NULL;
  }
  rawmem = rawmem->in(0)->as_Initialize()->memory(Compile::AliasIdxRaw);
  if (!(rawmem->is_Proj() && rawmem->in(0) == alloc)) {
    return NULL;
  }

  // There must be no unexpected observers of this allocation.
  for (DUIterator_Fast imax, i = ptr->fast_outs(imax); i < imax; i++) {
    Node* obs = ptr->fast_out(i);
    if (obs != this->map()) {
      return NULL;
    }
  }

  // This arraycopy must unconditionally follow the allocation of the ptr.
  Node* alloc_ctl = ptr->in(0);
  assert(just_allocated_object(alloc_ctl) == ptr, "most recent allo");

  Node* ctl = control();
  while (ctl != alloc_ctl) {
    // There may be guards which feed into the slow_region.
    // Any other control flow means that we might not get a chance
    // to finish initializing the allocated object.
    if ((ctl->is_IfFalse() || ctl->is_IfTrue()) && ctl->in(0)->is_If()) {
      IfNode* iff = ctl->in(0)->as_If();
      Node* not_ctl = iff->proj_out(1 - ctl->as_Proj()->_con);
      assert(not_ctl != NULL && not_ctl != ctl, "found alternate");
      if (slow_region != NULL && slow_region->find_edge(not_ctl) >= 1) {
        ctl = iff->in(0);       // This test feeds the known slow_region.
        continue;
      }
      // One more try:  Various low-level checks bottom out in
      // uncommon traps.  If the debug-info of the trap omits
      // any reference to the allocation, as we've already
      // observed, then there can be no objection to the trap.
      bool found_trap = false;
      for (DUIterator_Fast jmax, j = not_ctl->fast_outs(jmax); j < jmax; j++) {
        Node* obs = not_ctl->fast_out(j);
        if (obs->in(0) == not_ctl && obs->is_Call() &&
            (obs->as_Call()->entry_point() == SharedRuntime::uncommon_trap_blob()->entry_point())) {
          found_trap = true; break;
        }
      }
      if (found_trap) {
        ctl = iff->in(0);       // This test feeds a harmless uncommon trap.
        continue;
      }
    }
    return NULL;
  }

  // If we get this far, we have an allocation which immediately
  // precedes the arraycopy, and we can take over zeroing the new object.
  // The arraycopy will finish the initialization, and provide
  // a new control state to which we will anchor the destination pointer.

  return alloc;
}

// Helper for initialization of arrays, creating a ClearArray.
// It writes zero bits in [start..end), within the body of an array object.
// The memory effects are all chained onto the 'adr_type' alias category.
//
// Since the object is otherwise uninitialized, we are free
// to put a little "slop" around the edges of the cleared area,
// as long as it does not go back into the array's header,
// or beyond the array end within the heap.
//
// The lower edge can be rounded down to the nearest jint and the
// upper edge can be rounded up to the nearest MinObjAlignmentInBytes.
//
// Arguments:
//   adr_type           memory slice where writes are generated
//   dest               oop of the destination array
//   basic_elem_type    element type of the destination
//   slice_idx          array index of first element to store
//   slice_len          number of elements to store (or NULL)
//   dest_size          total size in bytes of the array object
//
// Exactly one of slice_len or dest_size must be non-NULL.
// If dest_size is non-NULL, zeroing extends to the end of the object.
// If slice_len is non-NULL, the slice_idx value must be a constant.
void
LibraryCallKit::generate_clear_array(const TypePtr* adr_type,
                                     Node* dest,
                                     BasicType basic_elem_type,
                                     Node* slice_idx,
                                     Node* slice_len,
                                     Node* dest_size) {
  // one or the other but not both of slice_len and dest_size:
  assert((slice_len != NULL? 1: 0) + (dest_size != NULL? 1: 0) == 1, "");
  if (slice_len == NULL)  slice_len = top();
  if (dest_size == NULL)  dest_size = top();

  // operate on this memory slice:
  Node* mem = memory(adr_type); // memory slice to operate on

  // scaling and rounding of indexes:
  int scale = exact_log2(type2aelembytes(basic_elem_type));
  int abase = arrayOopDesc::base_offset_in_bytes(basic_elem_type);
  int clear_low = (-1 << scale) & (BytesPerInt  - 1);
  int bump_bit  = (-1 << scale) & BytesPerInt;

  // determine constant starts and ends
  const intptr_t BIG_NEG = -128;
  assert(BIG_NEG + 2*abase < 0, "neg enough");
  intptr_t slice_idx_con = (intptr_t) find_int_con(slice_idx, BIG_NEG);
  intptr_t slice_len_con = (intptr_t) find_int_con(slice_len, BIG_NEG);
  if (slice_len_con == 0) {
    return;                     // nothing to do here
  }
  intptr_t start_con = (abase + (slice_idx_con << scale)) & ~clear_low;
  intptr_t end_con   = find_intptr_t_con(dest_size, -1);
  if (slice_idx_con >= 0 && slice_len_con >= 0) {
    assert(end_con < 0, "not two cons");
    end_con = round_to(abase + ((slice_idx_con + slice_len_con) << scale),
                       BytesPerLong);
  }

  if (start_con >= 0 && end_con >= 0) {
    // Constant start and end.  Simple.
    mem = ClearArrayNode::clear_memory(control(), mem, dest,
                                       start_con, end_con, &_gvn);
  } else if (start_con >= 0 && dest_size != top()) {
    // Constant start, pre-rounded end after the tail of the array.
    Node* end = dest_size;
    mem = ClearArrayNode::clear_memory(control(), mem, dest,
                                       start_con, end, &_gvn);
  } else if (start_con >= 0 && slice_len != top()) {
    // Constant start, non-constant end.  End needs rounding up.
    // End offset = round_up(abase + ((slice_idx_con + slice_len) << scale), 8)
    intptr_t end_base  = abase + (slice_idx_con << scale);
    int      end_round = (-1 << scale) & (BytesPerLong  - 1);
    Node*    end       = ConvI2X(slice_len);
    if (scale != 0)
      end = _gvn.transform(new(C) LShiftXNode(end, intcon(scale) ));
    end_base += end_round;
    end = _gvn.transform(new(C) AddXNode(end, MakeConX(end_base)));
    end = _gvn.transform(new(C) AndXNode(end, MakeConX(~end_round)));
    mem = ClearArrayNode::clear_memory(control(), mem, dest,
                                       start_con, end, &_gvn);
  } else if (start_con < 0 && dest_size != top()) {
    // Non-constant start, pre-rounded end after the tail of the array.
    // This is almost certainly a "round-to-end" operation.
    Node* start = slice_idx;
    start = ConvI2X(start);
    if (scale != 0)
      start = _gvn.transform(new(C) LShiftXNode( start, intcon(scale) ));
    start = _gvn.transform(new(C) AddXNode(start, MakeConX(abase)));
    if ((bump_bit | clear_low) != 0) {
      int to_clear = (bump_bit | clear_low);
      // Align up mod 8, then store a jint zero unconditionally
      // just before the mod-8 boundary.
      if (((abase + bump_bit) & ~to_clear) - bump_bit
          < arrayOopDesc::length_offset_in_bytes() + BytesPerInt) {
        bump_bit = 0;
        assert((abase & to_clear) == 0, "array base must be long-aligned");
      } else {
        // Bump 'start' up to (or past) the next jint boundary:
        start = _gvn.transform(new(C) AddXNode(start, MakeConX(bump_bit)));
        assert((abase & clear_low) == 0, "array base must be int-aligned");
      }
      // Round bumped 'start' down to jlong boundary in body of array.
      start = _gvn.transform(new(C) AndXNode(start, MakeConX(~to_clear)));
      if (bump_bit != 0) {
        // Store a zero to the immediately preceding jint:
        Node* x1 = _gvn.transform(new(C) AddXNode(start, MakeConX(-bump_bit)));
        Node* p1 = basic_plus_adr(dest, x1);
        mem = StoreNode::make(_gvn, control(), mem, p1, adr_type, intcon(0), T_INT);
        mem = _gvn.transform(mem);
      }
    }
    Node* end = dest_size; // pre-rounded
    mem = ClearArrayNode::clear_memory(control(), mem, dest,
                                       start, end, &_gvn);
  } else {
    // Non-constant start, unrounded non-constant end.
    // (Nobody zeroes a random midsection of an array using this routine.)
    ShouldNotReachHere();       // fix caller
  }

  // Done.
  set_memory(mem, adr_type);
}


bool
LibraryCallKit::generate_block_arraycopy(const TypePtr* adr_type,
                                         BasicType basic_elem_type,
                                         AllocateNode* alloc,
                                         Node* src,  Node* src_offset,
                                         Node* dest, Node* dest_offset,
                                         Node* dest_size, bool dest_uninitialized) {
  // See if there is an advantage from block transfer.
  int scale = exact_log2(type2aelembytes(basic_elem_type));
  if (scale >= LogBytesPerLong)
    return false;               // it is already a block transfer

  // Look at the alignment of the starting offsets.
  int abase = arrayOopDesc::base_offset_in_bytes(basic_elem_type);

  intptr_t src_off_con  = (intptr_t) find_int_con(src_offset, -1);
  intptr_t dest_off_con = (intptr_t) find_int_con(dest_offset, -1);
  if (src_off_con < 0 || dest_off_con < 0)
    // At present, we can only understand constants.
    return false;

  intptr_t src_off  = abase + (src_off_con  << scale);
  intptr_t dest_off = abase + (dest_off_con << scale);

  if (((src_off | dest_off) & (BytesPerLong-1)) != 0) {
    // Non-aligned; too bad.
    // One more chance:  Pick off an initial 32-bit word.
    // This is a common case, since abase can be odd mod 8.
    if (((src_off | dest_off) & (BytesPerLong-1)) == BytesPerInt &&
        ((src_off ^ dest_off) & (BytesPerLong-1)) == 0) {
      Node* sptr = basic_plus_adr(src,  src_off);
      Node* dptr = basic_plus_adr(dest, dest_off);
      Node* sval = make_load(control(), sptr, TypeInt::INT, T_INT, adr_type);
      store_to_memory(control(), dptr, sval, T_INT, adr_type);
      src_off += BytesPerInt;
      dest_off += BytesPerInt;
    } else {
      return false;
    }
  }
  assert(src_off % BytesPerLong == 0, "");
  assert(dest_off % BytesPerLong == 0, "");

  // Do this copy by giant steps.
  Node* sptr  = basic_plus_adr(src,  src_off);
  Node* dptr  = basic_plus_adr(dest, dest_off);
  Node* countx = dest_size;
  countx = _gvn.transform(new (C) SubXNode(countx, MakeConX(dest_off)));
  countx = _gvn.transform(new (C) URShiftXNode(countx, intcon(LogBytesPerLong)));

  bool disjoint_bases = true;   // since alloc != NULL
  generate_unchecked_arraycopy(adr_type, T_LONG, disjoint_bases,
                               sptr, NULL, dptr, NULL, countx, dest_uninitialized);

  return true;
}


// Helper function; generates code for the slow case.
// We make a call to a runtime method which emulates the native method,
// but without the native wrapper overhead.
void
LibraryCallKit::generate_slow_arraycopy(const TypePtr* adr_type,
                                        Node* src,  Node* src_offset,
                                        Node* dest, Node* dest_offset,
                                        Node* copy_length, bool dest_uninitialized) {
  assert(!dest_uninitialized, "Invariant");
  Node* call = make_runtime_call(RC_NO_LEAF | RC_UNCOMMON,
                                 OptoRuntime::slow_arraycopy_Type(),
                                 OptoRuntime::slow_arraycopy_Java(),
                                 "slow_arraycopy", adr_type,
                                 src, src_offset, dest, dest_offset,
                                 copy_length);

  // Handle exceptions thrown by this fellow:
  make_slow_call_ex(call, env()->Throwable_klass(), false);
}

// Helper function; generates code for cases requiring runtime checks.
Node*
LibraryCallKit::generate_checkcast_arraycopy(const TypePtr* adr_type,
                                             Node* dest_elem_klass,
                                             Node* src,  Node* src_offset,
                                             Node* dest, Node* dest_offset,
                                             Node* copy_length, bool dest_uninitialized) {
  if (stopped())  return NULL;

  address copyfunc_addr = StubRoutines::checkcast_arraycopy(dest_uninitialized);
  if (copyfunc_addr == NULL) { // Stub was not generated, go slow path.
    return NULL;
  }

  // Pick out the parameters required to perform a store-check
  // for the target array.  This is an optimistic check.  It will
  // look in each non-null element's class, at the desired klass's
  // super_check_offset, for the desired klass.
  int sco_offset = in_bytes(Klass::super_check_offset_offset());
  Node* p3 = basic_plus_adr(dest_elem_klass, sco_offset);
  Node* n3 = new(C) LoadINode(NULL, memory(p3), p3, _gvn.type(p3)->is_ptr());
  Node* check_offset = ConvI2X(_gvn.transform(n3));
  Node* check_value  = dest_elem_klass;

  Node* src_start  = array_element_address(src,  src_offset,  T_OBJECT);
  Node* dest_start = array_element_address(dest, dest_offset, T_OBJECT);

  // (We know the arrays are never conjoint, because their types differ.)
  Node* call = make_runtime_call(RC_LEAF|RC_NO_FP,
                                 OptoRuntime::checkcast_arraycopy_Type(),
                                 copyfunc_addr, "checkcast_arraycopy", adr_type,
                                 // five arguments, of which two are
                                 // intptr_t (jlong in LP64)
                                 src_start, dest_start,
                                 copy_length XTOP,
                                 check_offset XTOP,
                                 check_value);

  return _gvn.transform(new (C) ProjNode(call, TypeFunc::Parms));
}


// Helper function; generates code for cases requiring runtime checks.
Node*
LibraryCallKit::generate_generic_arraycopy(const TypePtr* adr_type,
                                           Node* src,  Node* src_offset,
                                           Node* dest, Node* dest_offset,
                                           Node* copy_length, bool dest_uninitialized) {
  assert(!dest_uninitialized, "Invariant");
  if (stopped())  return NULL;
  address copyfunc_addr = StubRoutines::generic_arraycopy();
  if (copyfunc_addr == NULL) { // Stub was not generated, go slow path.
    return NULL;
  }

  Node* call = make_runtime_call(RC_LEAF|RC_NO_FP,
                    OptoRuntime::generic_arraycopy_Type(),
                    copyfunc_addr, "generic_arraycopy", adr_type,
                    src, src_offset, dest, dest_offset, copy_length);

  return _gvn.transform(new (C) ProjNode(call, TypeFunc::Parms));
}

// Helper function; generates the fast out-of-line call to an arraycopy stub.
void
LibraryCallKit::generate_unchecked_arraycopy(const TypePtr* adr_type,
                                             BasicType basic_elem_type,
                                             bool disjoint_bases,
                                             Node* src,  Node* src_offset,
                                             Node* dest, Node* dest_offset,
                                             Node* copy_length, bool dest_uninitialized) {
  if (stopped())  return;               // nothing to do

  Node* src_start  = src;
  Node* dest_start = dest;
  if (src_offset != NULL || dest_offset != NULL) {
    assert(src_offset != NULL && dest_offset != NULL, "");
    src_start  = array_element_address(src,  src_offset,  basic_elem_type);
    dest_start = array_element_address(dest, dest_offset, basic_elem_type);
  }

  // Figure out which arraycopy runtime method to call.
  const char* copyfunc_name = "arraycopy";
  address     copyfunc_addr =
      basictype2arraycopy(basic_elem_type, src_offset, dest_offset,
                          disjoint_bases, copyfunc_name, dest_uninitialized);

  // Call it.  Note that the count_ix value is not scaled to a byte-size.
  make_runtime_call(RC_LEAF|RC_NO_FP,
                    OptoRuntime::fast_arraycopy_Type(),
                    copyfunc_addr, copyfunc_name, adr_type,
                    src_start, dest_start, copy_length XTOP);
}

//-------------inline_encodeISOArray-----------------------------------
// encode char[] to byte[] in ISO_8859_1
bool LibraryCallKit::inline_encodeISOArray() {
  assert(callee()->signature()->size() == 5, "encodeISOArray has 5 parameters");
  // no receiver since it is static method
  Node *src         = argument(0);
  Node *src_offset  = argument(1);
  Node *dst         = argument(2);
  Node *dst_offset  = argument(3);
  Node *length      = argument(4);

  const Type* src_type = src->Value(&_gvn);
  const Type* dst_type = dst->Value(&_gvn);
  const TypeAryPtr* top_src = src_type->isa_aryptr();
  const TypeAryPtr* top_dest = dst_type->isa_aryptr();
  if (top_src  == NULL || top_src->klass()  == NULL ||
      top_dest == NULL || top_dest->klass() == NULL) {
    // failed array check
    return false;
  }

  // Figure out the size and type of the elements we will be copying.
  BasicType src_elem = src_type->isa_aryptr()->klass()->as_array_klass()->element_type()->basic_type();
  BasicType dst_elem = dst_type->isa_aryptr()->klass()->as_array_klass()->element_type()->basic_type();
  if (src_elem != T_CHAR || dst_elem != T_BYTE) {
    return false;
  }
  Node* src_start = array_element_address(src, src_offset, src_elem);
  Node* dst_start = array_element_address(dst, dst_offset, dst_elem);
  // 'src_start' points to src array + scaled offset
  // 'dst_start' points to dst array + scaled offset

  const TypeAryPtr* mtype = TypeAryPtr::BYTES;
  Node* enc = new (C) EncodeISOArrayNode(control(), memory(mtype), src_start, dst_start, length);
  enc = _gvn.transform(enc);
  Node* res_mem = _gvn.transform(new (C) SCMemProjNode(enc));
  set_memory(res_mem, mtype);
  set_result(enc);
  return true;
}

/**
 * Calculate CRC32 for byte.
 * int java.util.zip.CRC32.update(int crc, int b)
 */
bool LibraryCallKit::inline_updateCRC32() {
  assert(UseCRC32Intrinsics, "need AVX and LCMUL instructions support");
  assert(callee()->signature()->size() == 2, "update has 2 parameters");
  // no receiver since it is static method
  Node* crc  = argument(0); // type: int
  Node* b    = argument(1); // type: int

  /*
   *    int c = ~ crc;
   *    b = timesXtoThe32[(b ^ c) & 0xFF];
   *    b = b ^ (c >>> 8);
   *    crc = ~b;
   */

  Node* M1 = intcon(-1);
  crc = _gvn.transform(new (C) XorINode(crc, M1));
  Node* result = _gvn.transform(new (C) XorINode(crc, b));
  result = _gvn.transform(new (C) AndINode(result, intcon(0xFF)));

  Node* base = makecon(TypeRawPtr::make(StubRoutines::crc_table_addr()));
  Node* offset = _gvn.transform(new (C) LShiftINode(result, intcon(0x2)));
  Node* adr = basic_plus_adr(top(), base, ConvI2X(offset));
  result = make_load(control(), adr, TypeInt::INT, T_INT);

  crc = _gvn.transform(new (C) URShiftINode(crc, intcon(8)));
  result = _gvn.transform(new (C) XorINode(crc, result));
  result = _gvn.transform(new (C) XorINode(result, M1));
  set_result(result);
  return true;
}

/**
 * Calculate CRC32 for byte[] array.
 * int java.util.zip.CRC32.updateBytes(int crc, byte[] buf, int off, int len)
 */
bool LibraryCallKit::inline_updateBytesCRC32() {
  assert(UseCRC32Intrinsics, "need AVX and LCMUL instructions support");
  assert(callee()->signature()->size() == 4, "updateBytes has 4 parameters");
  // no receiver since it is static method
  Node* crc     = argument(0); // type: int
  Node* src     = argument(1); // type: oop
  Node* offset  = argument(2); // type: int
  Node* length  = argument(3); // type: int

  const Type* src_type = src->Value(&_gvn);
  const TypeAryPtr* top_src = src_type->isa_aryptr();
  if (top_src  == NULL || top_src->klass()  == NULL) {
    // failed array check
    return false;
  }

  // Figure out the size and type of the elements we will be copying.
  BasicType src_elem = src_type->isa_aryptr()->klass()->as_array_klass()->element_type()->basic_type();
  if (src_elem != T_BYTE) {
    return false;
  }

  // 'src_start' points to src array + scaled offset
  Node* src_start = array_element_address(src, offset, src_elem);

  // We assume that range check is done by caller.
  // TODO: generate range check (offset+length < src.length) in debug VM.

  // Call the stub.
  address stubAddr = StubRoutines::updateBytesCRC32();
  const char *stubName = "updateBytesCRC32";

  Node* call = make_runtime_call(RC_LEAF|RC_NO_FP, OptoRuntime::updateBytesCRC32_Type(),
                                 stubAddr, stubName, TypePtr::BOTTOM,
                                 crc, src_start, length);
  Node* result = _gvn.transform(new (C) ProjNode(call, TypeFunc::Parms));
  set_result(result);
  return true;
}

/**
 * Calculate CRC32 for ByteBuffer.
 * int java.util.zip.CRC32.updateByteBuffer(int crc, long buf, int off, int len)
 */
bool LibraryCallKit::inline_updateByteBufferCRC32() {
  assert(UseCRC32Intrinsics, "need AVX and LCMUL instructions support");
  assert(callee()->signature()->size() == 5, "updateByteBuffer has 4 parameters and one is long");
  // no receiver since it is static method
  Node* crc     = argument(0); // type: int
  Node* src     = argument(1); // type: long
  Node* offset  = argument(3); // type: int
  Node* length  = argument(4); // type: int

  src = ConvL2X(src);  // adjust Java long to machine word
  Node* base = _gvn.transform(new (C) CastX2PNode(src));
  offset = ConvI2X(offset);

  // 'src_start' points to src array + scaled offset
  Node* src_start = basic_plus_adr(top(), base, offset);

  // Call the stub.
  address stubAddr = StubRoutines::updateBytesCRC32();
  const char *stubName = "updateBytesCRC32";

  Node* call = make_runtime_call(RC_LEAF|RC_NO_FP, OptoRuntime::updateBytesCRC32_Type(),
                                 stubAddr, stubName, TypePtr::BOTTOM,
                                 crc, src_start, length);
  Node* result = _gvn.transform(new (C) ProjNode(call, TypeFunc::Parms));
  set_result(result);
  return true;
}

//----------------------------inline_reference_get----------------------------
// public T java.lang.ref.Reference.get();
bool LibraryCallKit::inline_reference_get() {
  const int referent_offset = java_lang_ref_Reference::referent_offset;
  guarantee(referent_offset > 0, "should have already been set");

  // Get the argument:
  Node* reference_obj = null_check_receiver();
  if (stopped()) return true;

  Node* adr = basic_plus_adr(reference_obj, reference_obj, referent_offset);

  ciInstanceKlass* klass = env()->Object_klass();
  const TypeOopPtr* object_type = TypeOopPtr::make_from_klass(klass);

  Node* no_ctrl = NULL;
  Node* result = make_load(no_ctrl, adr, object_type, T_OBJECT);

  // Use the pre-barrier to record the value in the referent field
  pre_barrier(false /* do_load */,
              control(),
              NULL /* obj */, NULL /* adr */, max_juint /* alias_idx */, NULL /* val */, NULL /* val_type */,
              result /* pre_val */,
              T_OBJECT);

  // Add memory barrier to prevent commoning reads from this field
  // across safepoint since GC can change its value.
  insert_mem_bar(Op_MemBarCPUOrder);

  set_result(result);
  return true;
}


Node * LibraryCallKit::load_field_from_object(Node * fromObj, const char * fieldName, const char * fieldTypeString,
                                              bool is_exact=true, bool is_static=false) {

  const TypeInstPtr* tinst = _gvn.type(fromObj)->isa_instptr();
  assert(tinst != NULL, "obj is null");
  assert(tinst->klass()->is_loaded(), "obj is not loaded");
  assert(!is_exact || tinst->klass_is_exact(), "klass not exact");

  ciField* field = tinst->klass()->as_instance_klass()->get_field_by_name(ciSymbol::make(fieldName),
                                                                          ciSymbol::make(fieldTypeString),
                                                                          is_static);
  if (field == NULL) return (Node *) NULL;
  assert (field != NULL, "undefined field");

  // Next code  copied from Parse::do_get_xxx():

  // Compute address and memory type.
  int offset  = field->offset_in_bytes();
  bool is_vol = field->is_volatile();
  ciType* field_klass = field->type();
  assert(field_klass->is_loaded(), "should be loaded");
  const TypePtr* adr_type = C->alias_type(field)->adr_type();
  Node *adr = basic_plus_adr(fromObj, fromObj, offset);
  BasicType bt = field->layout_type();

  // Build the resultant type of the load
  const Type *type = TypeOopPtr::make_from_klass(field_klass->as_klass());

  // Build the load.
  Node* loadedField = make_load(NULL, adr, type, bt, adr_type, is_vol);
  return loadedField;
}


//------------------------------inline_aescrypt_Block-----------------------
bool LibraryCallKit::inline_aescrypt_Block(vmIntrinsics::ID id) {
  address stubAddr;
  const char *stubName;
  assert(UseAES, "need AES instruction support");

  switch(id) {
  case vmIntrinsics::_aescrypt_encryptBlock:
    stubAddr = StubRoutines::aescrypt_encryptBlock();
    stubName = "aescrypt_encryptBlock";
    break;
  case vmIntrinsics::_aescrypt_decryptBlock:
    stubAddr = StubRoutines::aescrypt_decryptBlock();
    stubName = "aescrypt_decryptBlock";
    break;
  }
  if (stubAddr == NULL) return false;

  Node* aescrypt_object = argument(0);
  Node* src             = argument(1);
  Node* src_offset      = argument(2);
  Node* dest            = argument(3);
  Node* dest_offset     = argument(4);

  // (1) src and dest are arrays.
  const Type* src_type = src->Value(&_gvn);
  const Type* dest_type = dest->Value(&_gvn);
  const TypeAryPtr* top_src = src_type->isa_aryptr();
  const TypeAryPtr* top_dest = dest_type->isa_aryptr();
  assert (top_src  != NULL && top_src->klass()  != NULL &&  top_dest != NULL && top_dest->klass() != NULL, "args are strange");

  // for the quick and dirty code we will skip all the checks.
  // we are just trying to get the call to be generated.
  Node* src_start  = src;
  Node* dest_start = dest;
  if (src_offset != NULL || dest_offset != NULL) {
    assert(src_offset != NULL && dest_offset != NULL, "");
    src_start  = array_element_address(src,  src_offset,  T_BYTE);
    dest_start = array_element_address(dest, dest_offset, T_BYTE);
  }

  // now need to get the start of its expanded key array
  // this requires a newer class file that has this array as littleEndian ints, otherwise we revert to java
  Node* k_start = get_key_start_from_aescrypt_object(aescrypt_object);
  if (k_start == NULL) return false;

  // Call the stub.
  make_runtime_call(RC_LEAF|RC_NO_FP, OptoRuntime::aescrypt_block_Type(),
                    stubAddr, stubName, TypePtr::BOTTOM,
                    src_start, dest_start, k_start);

  return true;
}

//------------------------------inline_cipherBlockChaining_AESCrypt-----------------------
bool LibraryCallKit::inline_cipherBlockChaining_AESCrypt(vmIntrinsics::ID id) {
  address stubAddr;
  const char *stubName;

  assert(UseAES, "need AES instruction support");

  switch(id) {
  case vmIntrinsics::_cipherBlockChaining_encryptAESCrypt:
    stubAddr = StubRoutines::cipherBlockChaining_encryptAESCrypt();
    stubName = "cipherBlockChaining_encryptAESCrypt";
    break;
  case vmIntrinsics::_cipherBlockChaining_decryptAESCrypt:
    stubAddr = StubRoutines::cipherBlockChaining_decryptAESCrypt();
    stubName = "cipherBlockChaining_decryptAESCrypt";
    break;
  }
  if (stubAddr == NULL) return false;

  Node* cipherBlockChaining_object = argument(0);
  Node* src                        = argument(1);
  Node* src_offset                 = argument(2);
  Node* len                        = argument(3);
  Node* dest                       = argument(4);
  Node* dest_offset                = argument(5);

  // (1) src and dest are arrays.
  const Type* src_type = src->Value(&_gvn);
  const Type* dest_type = dest->Value(&_gvn);
  const TypeAryPtr* top_src = src_type->isa_aryptr();
  const TypeAryPtr* top_dest = dest_type->isa_aryptr();
  assert (top_src  != NULL && top_src->klass()  != NULL
          &&  top_dest != NULL && top_dest->klass() != NULL, "args are strange");

  // checks are the responsibility of the caller
  Node* src_start  = src;
  Node* dest_start = dest;
  if (src_offset != NULL || dest_offset != NULL) {
    assert(src_offset != NULL && dest_offset != NULL, "");
    src_start  = array_element_address(src,  src_offset,  T_BYTE);
    dest_start = array_element_address(dest, dest_offset, T_BYTE);
  }

  // if we are in this set of code, we "know" the embeddedCipher is an AESCrypt object
  // (because of the predicated logic executed earlier).
  // so we cast it here safely.
  // this requires a newer class file that has this array as littleEndian ints, otherwise we revert to java

  Node* embeddedCipherObj = load_field_from_object(cipherBlockChaining_object, "embeddedCipher", "Lcom/sun/crypto/provider/SymmetricCipher;", /*is_exact*/ false);
  if (embeddedCipherObj == NULL) return false;

  // cast it to what we know it will be at runtime
  const TypeInstPtr* tinst = _gvn.type(cipherBlockChaining_object)->isa_instptr();
  assert(tinst != NULL, "CBC obj is null");
  assert(tinst->klass()->is_loaded(), "CBC obj is not loaded");
  ciKlass* klass_AESCrypt = tinst->klass()->as_instance_klass()->find_klass(ciSymbol::make("com/sun/crypto/provider/AESCrypt"));
  if (!klass_AESCrypt->is_loaded()) return false;

  ciInstanceKlass* instklass_AESCrypt = klass_AESCrypt->as_instance_klass();
  const TypeKlassPtr* aklass = TypeKlassPtr::make(instklass_AESCrypt);
  const TypeOopPtr* xtype = aklass->as_instance_type();
  Node* aescrypt_object = new(C) CheckCastPPNode(control(), embeddedCipherObj, xtype);
  aescrypt_object = _gvn.transform(aescrypt_object);

  // we need to get the start of the aescrypt_object's expanded key array
  Node* k_start = get_key_start_from_aescrypt_object(aescrypt_object);
  if (k_start == NULL) return false;

  // similarly, get the start address of the r vector
  Node* objRvec = load_field_from_object(cipherBlockChaining_object, "r", "[B", /*is_exact*/ false);
  if (objRvec == NULL) return false;
  Node* r_start = array_element_address(objRvec, intcon(0), T_BYTE);

  // Call the stub, passing src_start, dest_start, k_start, r_start and src_len
  make_runtime_call(RC_LEAF|RC_NO_FP,
                    OptoRuntime::cipherBlockChaining_aescrypt_Type(),
                    stubAddr, stubName, TypePtr::BOTTOM,
                    src_start, dest_start, k_start, r_start, len);

  // return is void so no result needs to be pushed

  return true;
}

//------------------------------get_key_start_from_aescrypt_object-----------------------
Node * LibraryCallKit::get_key_start_from_aescrypt_object(Node *aescrypt_object) {
  Node* objAESCryptKey = load_field_from_object(aescrypt_object, "K", "[I", /*is_exact*/ false);
  assert (objAESCryptKey != NULL, "wrong version of com.sun.crypto.provider.AESCrypt");
  if (objAESCryptKey == NULL) return (Node *) NULL;

  // now have the array, need to get the start address of the K array
  Node* k_start = array_element_address(objAESCryptKey, intcon(0), T_INT);
  return k_start;
}

//----------------------------inline_cipherBlockChaining_AESCrypt_predicate----------------------------
// Return node representing slow path of predicate check.
// the pseudo code we want to emulate with this predicate is:
// for encryption:
//    if (embeddedCipherObj instanceof AESCrypt) do_intrinsic, else do_javapath
// for decryption:
//    if ((embeddedCipherObj instanceof AESCrypt) && (cipher!=plain)) do_intrinsic, else do_javapath
//    note cipher==plain is more conservative than the original java code but that's OK
//
Node* LibraryCallKit::inline_cipherBlockChaining_AESCrypt_predicate(bool decrypting) {
  // First, check receiver for NULL since it is virtual method.
  Node* objCBC = argument(0);
  objCBC = null_check(objCBC);

  if (stopped()) return NULL; // Always NULL

  // Load embeddedCipher field of CipherBlockChaining object.
  Node* embeddedCipherObj = load_field_from_object(objCBC, "embeddedCipher", "Lcom/sun/crypto/provider/SymmetricCipher;", /*is_exact*/ false);

  // get AESCrypt klass for instanceOf check
  // AESCrypt might not be loaded yet if some other SymmetricCipher got us to this compile point
  // will have same classloader as CipherBlockChaining object
  const TypeInstPtr* tinst = _gvn.type(objCBC)->isa_instptr();
  assert(tinst != NULL, "CBCobj is null");
  assert(tinst->klass()->is_loaded(), "CBCobj is not loaded");

  // we want to do an instanceof comparison against the AESCrypt class
  ciKlass* klass_AESCrypt = tinst->klass()->as_instance_klass()->find_klass(ciSymbol::make("com/sun/crypto/provider/AESCrypt"));
  if (!klass_AESCrypt->is_loaded()) {
    // if AESCrypt is not even loaded, we never take the intrinsic fast path
    Node* ctrl = control();
    set_control(top()); // no regular fast path
    return ctrl;
  }
  ciInstanceKlass* instklass_AESCrypt = klass_AESCrypt->as_instance_klass();

  Node* instof = gen_instanceof(embeddedCipherObj, makecon(TypeKlassPtr::make(instklass_AESCrypt)));
  Node* cmp_instof  = _gvn.transform(new (C) CmpINode(instof, intcon(1)));
  Node* bool_instof  = _gvn.transform(new (C) BoolNode(cmp_instof, BoolTest::ne));

  Node* instof_false = generate_guard(bool_instof, NULL, PROB_MIN);

  // for encryption, we are done
  if (!decrypting)
    return instof_false;  // even if it is NULL

  // for decryption, we need to add a further check to avoid
  // taking the intrinsic path when cipher and plain are the same
  // see the original java code for why.
  RegionNode* region = new(C) RegionNode(3);
  region->init_req(1, instof_false);
  Node* src = argument(1);
  Node* dest = argument(4);
  Node* cmp_src_dest = _gvn.transform(new (C) CmpPNode(src, dest));
  Node* bool_src_dest = _gvn.transform(new (C) BoolNode(cmp_src_dest, BoolTest::eq));
  Node* src_dest_conjoint = generate_guard(bool_src_dest, NULL, PROB_MIN);
  region->init_req(2, src_dest_conjoint);

  record_for_igvn(region);
  return _gvn.transform(region);
}
