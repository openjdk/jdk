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

JNI_ENTRY(jlong, NEP_makeInvoker(JNIEnv* env, jclass _unused, jobject method_type, jint shadow_space_bytes,
                                 jlongArray arg_moves, jlongArray ret_moves))
  ResourceMark rm;

  // Note: the method_type's first param is the target address, but we don't have
  // and entry for that in the arg_moves array.
  // we need an entry for that in the basic type at least, so we can later
  // generate the right argument shuffle

  oop type = JNIHandles::resolve(method_type);
  // does not contain entry for address:
  typeArrayOop arg_moves_oop = oop_cast<typeArrayOop>(JNIHandles::resolve(arg_moves));
  typeArrayOop ret_moves_oop = oop_cast<typeArrayOop>(JNIHandles::resolve(ret_moves));
  // contains address:
  int pcount = java_lang_invoke_MethodType::ptype_count(type);
  int pslots = java_lang_invoke_MethodType::ptype_slot_count(type);
  // contains address:
  BasicType* basic_type = NEW_RESOURCE_ARRAY(BasicType, pslots);
  // address
  basic_type[0] = T_LONG;
  basic_type[1] = T_VOID;

  // does not contain entry for address:
  GrowableArray<VMReg> input_regs(pslots);

  int num_args = 2;
  for (int i = 1; i < pcount; i++) { // skip addr
    oop type_oop = java_lang_invoke_MethodType::ptype(type, i);
    assert(java_lang_Class::is_primitive(type_oop), "Only primitives expected");
    BasicType bt = java_lang_Class::primitive_type(type_oop);
    basic_type[num_args] = bt;
    input_regs.push(VMRegImpl::as_VMReg(arg_moves_oop->long_at(i - 1))); // address missing in moves
    num_args++;

    if (bt == BasicType::T_DOUBLE || bt == BasicType::T_LONG) {
      basic_type[num_args] = T_VOID;
      input_regs.push(VMRegImpl::Bad()); // half of double/long
      num_args++;
    }
  }

  GrowableArray<VMReg> output_regs(pslots);

  jint outs = ret_moves_oop->length();
  assert(outs <= 1, "No multi-reg returns");
  BasicType ret_bt = T_VOID;
  if (outs == 1) {
    oop type_oop = java_lang_invoke_MethodType::rtype(type);
    ret_bt = java_lang_Class::primitive_type(type_oop);

    output_regs.push(VMRegImpl::as_VMReg(ret_moves_oop->long_at(0)));
    if (ret_bt == BasicType::T_DOUBLE || ret_bt == BasicType::T_LONG) {
      output_regs.push(VMRegImpl::Bad()); // half of double/long
    }
  }

#ifdef ASSERT
  LogTarget(Trace, panama) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    ls.print_cr("Generating native invoker {");
    ls.print("BasicType { ");
    for (int i = 0; i < num_args; i++) {
      ls.print("%s, ", null_safe_string(type2name(basic_type[i])));
    }
    ls.print_cr("}");
    ls.print_cr("shadow_space_bytes = %d", shadow_space_bytes);
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
    basic_type, num_args, ret_bt, shadow_space_bytes, input_regs, output_regs)->code_begin();
JNI_END

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod NEP_methods[] = {
  {CC "vmStorageToVMReg", CC "(II)J", FN_PTR(NEP_vmStorageToVMReg)},
  {CC "makeInvoker", CC "(Ljava/lang/invoke/MethodType;I[J[J)J", FN_PTR(NEP_makeInvoker)},
};

JNI_ENTRY(void, JVM_RegisterNativeEntryPointMethods(JNIEnv *env, jclass NEP_class))
  ThreadToNativeFromVM ttnfv(thread);
  int status = env->RegisterNatives(NEP_class, NEP_methods, sizeof(NEP_methods)/sizeof(JNINativeMethod));
  guarantee(status == JNI_OK && !env->ExceptionOccurred(),
            "register jdk.internal.invoke.NativeEntryPoint natives");
JNI_END
