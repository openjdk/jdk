/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/interfaceSupport.inline.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "code/vmreg.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "oops/oopCast.inline.hpp"
#include "prims/foreign_globals.inline.hpp"
#include "prims/universalNativeInvoker.hpp"
#include "runtime/jniHandles.inline.hpp"

JNI_LEAF(jlong, NEP_vmStorageToVMReg(JNIEnv* env, jclass _unused, jint type, jint index))
  return ForeignGlobals::vmstorage_to_vmreg(type, index)->value();
JNI_END

JNI_ENTRY(jlong, NEP_makeInvoker(JNIEnv* env, jclass _unused, jobject method_type, jobject jabi,
                                 jlongArray arg_moves, jlongArray ret_moves, jboolean needs_return_buffer))
  ResourceMark rm;
  const ABIDescriptor abi = ForeignGlobals::parse_abi_descriptor(jabi);

  oop type = JNIHandles::resolve(method_type);
  typeArrayOop arg_moves_oop = oop_cast<typeArrayOop>(JNIHandles::resolve(arg_moves));
  typeArrayOop ret_moves_oop = oop_cast<typeArrayOop>(JNIHandles::resolve(ret_moves));
  int pcount = java_lang_invoke_MethodType::ptype_count(type);
  int pslots = java_lang_invoke_MethodType::ptype_slot_count(type);
  BasicType* basic_type = NEW_RESOURCE_ARRAY(BasicType, pslots);

  GrowableArray<VMReg> input_regs(pcount);
  for (int i = 0, bt_idx = 0; i < pcount; i++) {
    oop type_oop = java_lang_invoke_MethodType::ptype(type, i);
    assert(java_lang_Class::is_primitive(type_oop), "Only primitives expected");
    BasicType bt = java_lang_Class::primitive_type(type_oop);
    basic_type[bt_idx++] = bt;
    input_regs.push(VMRegImpl::as_VMReg(arg_moves_oop->long_at(i)));

    if (bt == BasicType::T_DOUBLE || bt == BasicType::T_LONG) {
      basic_type[bt_idx++] = T_VOID;
      // we only need these in the basic type
      // NativeCallConv ignores them, but they are needed
      // for JavaCallConv
    }
  }


  jint outs = ret_moves_oop->length();
  GrowableArray<VMReg> output_regs(outs);
  oop type_oop = java_lang_invoke_MethodType::rtype(type);
  BasicType  ret_bt = java_lang_Class::primitive_type(type_oop);
  for (int i = 0; i < outs; i++) {
    // note that we don't care about long/double upper halfs here:
    // we are NOT moving Java values, we are moving register-sized values
    output_regs.push(VMRegImpl::as_VMReg(ret_moves_oop->long_at(i)));
  }

#ifdef ASSERT
  LogTarget(Trace, panama) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    ls.print_cr("Generating native invoker {");
    ls.print("BasicType { ");
    for (int i = 0; i < pslots; i++) {
      ls.print("%s, ", null_safe_string(type2name(basic_type[i])));
    }
    ls.print_cr("}");
    ls.print_cr("shadow_space_bytes = %d", abi._shadow_space_bytes);
    ls.print("input_registers { ");
    for (int i = 0; i < input_regs.length(); i++) {
      VMReg reg = input_regs.at(i);
      ls.print("%s (" INTPTR_FORMAT "), ", reg->name(), reg->value());
    }
    ls.print_cr("}");
      ls.print("output_registers { ");
    for (int i = 0; i < output_regs.length(); i++) {
      VMReg reg = output_regs.at(i);
      ls.print("%s (" INTPTR_FORMAT "), ", reg->name(), reg->value());
    }
    ls.print_cr("}");
    ls.print_cr("}");
  }
#endif

  return (jlong) ProgrammableInvoker::make_native_invoker(
    basic_type, pslots, ret_bt, abi, input_regs, output_regs, needs_return_buffer)->code_begin();
JNI_END

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod NEP_methods[] = {
  {CC "vmStorageToVMReg", CC "(II)J", FN_PTR(NEP_vmStorageToVMReg)},
  {CC "makeInvoker", CC "(Ljava/lang/invoke/MethodType;Ljdk/internal/invoke/ABIDescriptorProxy;[J[JZ)J", FN_PTR(NEP_makeInvoker)},
};

JNI_ENTRY(void, JVM_RegisterNativeEntryPointMethods(JNIEnv *env, jclass NEP_class))
  ThreadToNativeFromVM ttnfv(thread);
  int status = env->RegisterNatives(NEP_class, NEP_methods, sizeof(NEP_methods)/sizeof(JNINativeMethod));
  guarantee(status == JNI_OK && !env->ExceptionOccurred(),
            "register jdk.internal.invoke.NativeEntryPoint natives");
JNI_END
