/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.hpp"
#include "runtime/fieldDescriptor.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "unittest.hpp"

using testing::HasSubstr;

// Tests for InstanceKlass::is_class_loader_instance_klass() function
TEST_VM(InstanceKlass, class_loader_class) {
  InstanceKlass* klass = vmClasses::ClassLoader_klass();
  ASSERT_TRUE(klass->is_class_loader_instance_klass());
}

TEST_VM(InstanceKlass, string_klass) {
  InstanceKlass* klass = vmClasses::String_klass();
  ASSERT_TRUE(!klass->is_class_loader_instance_klass());
}

TEST_VM(InstanceKlass, class_loader_printer) {
  ThreadInVMfromNative scope(JavaThread::current());
  ResourceMark rm;
  oop loader = SystemDictionary::java_platform_loader();
  stringStream st;
  loader->print_on(&st);
  // See if injected loader_data field is printed in string
  ASSERT_THAT(st.base(), HasSubstr("injected 'loader_data'")) << "Must contain injected fields";
  st.reset();
  // See if mirror injected fields are printed.
  oop mirror = vmClasses::ClassLoader_klass()->java_mirror();
  mirror->print_on(&st);
  ASSERT_THAT(st.base(), HasSubstr("injected 'array_klass'")) << "Must contain injected fields";
  // We should test other printing functions too.
#ifndef PRODUCT
  st.reset();
  // method printing is non-product
  Method* method = vmClasses::ClassLoader_klass()->methods()->at(0);  // we know there's a method here!
  method->print_on(&st);
  ASSERT_THAT(st.base(), HasSubstr("method holder:")) << "Must contain method_holder field";
  ASSERT_THAT(st.base(), HasSubstr("'java/lang/ClassLoader'")) << "Must be in ClassLoader";
#endif
}

TEST_VM(InstanceKlass, class_flag_printer) {
  JavaThread* THREAD = JavaThread::current();
  ThreadInVMfromNative scope(THREAD);
  ResourceMark rm;
  stringStream st;

  vmClasses::String_klass()->print_class_flags(&st);
  ASSERT_STREQ("public final ", st.base());

  st.reset();
  vmClasses::Runnable_klass()->print_class_flags(&st);
  ASSERT_STREQ("public interface abstract ", st.base());

  st.reset();
  Symbol* override_symbol = SymbolTable::new_symbol("java/lang/Override");
  Klass* override_klass = SystemDictionary::resolve_or_fail(override_symbol, true, THREAD);
  ASSERT_FALSE(THREAD->has_pending_exception()) << "java/lang/Override must resolve";
  InstanceKlass::cast(override_klass)->print_class_flags(&st);
  ASSERT_STREQ("public interface abstract annotation ", st.base());

  st.reset();
  Symbol* thread_state_symbol = SymbolTable::new_symbol("java/lang/Thread$State");
  Klass* thread_state_klass = SystemDictionary::resolve_or_fail(thread_state_symbol, true, THREAD);
  ASSERT_FALSE(THREAD->has_pending_exception()) << "java/lang/Thread$State must resolve";
  InstanceKlass::cast(thread_state_klass)->print_class_flags(&st);
  ASSERT_STREQ("public static final enum ", st.base());

  st.reset();
  Symbol* certificate_rep_symbol = SymbolTable::new_symbol("java/security/cert/Certificate$CertificateRep");
  Klass* certificate_rep_klass = SystemDictionary::resolve_or_fail(certificate_rep_symbol, true, THREAD);
  ASSERT_FALSE(THREAD->has_pending_exception()) << "java/security/cert/Certificate$CertificateRep must resolve";
  InstanceKlass::cast(certificate_rep_klass)->print_class_flags(&st);
  ASSERT_STREQ("protected static ", st.base());

  st.reset();
  Symbol* arrays_array_list_symbol = SymbolTable::new_symbol("java/util/Arrays$ArrayList");
  Klass* arrays_array_list_klass = SystemDictionary::resolve_or_fail(arrays_array_list_symbol, true, THREAD);
  ASSERT_FALSE(THREAD->has_pending_exception()) << "java/util/Arrays$ArrayList must resolve";
  InstanceKlass::cast(arrays_array_list_klass)->print_class_flags(&st);
  ASSERT_STREQ("private static ", st.base());
}

TEST_VM(FieldDescriptor, access_flag_printer) {
  JavaThread* THREAD = JavaThread::current();
  ThreadInVMfromNative scope(THREAD);
  ResourceMark rm;
  stringStream st;

  InstanceKlass* integer_klass = vmClasses::Integer_klass();
  Symbol* min_value_symbol = SymbolTable::new_symbol("MIN_VALUE");

  fieldDescriptor fd;
  ASSERT_TRUE(integer_klass->find_local_field(min_value_symbol, vmSymbols::int_signature(), &fd))
      << "Integer.MIN_VALUE must exist";
  fd.print_on(&st);
  ASSERT_THAT(st.base(), HasSubstr("public static final 'MIN_VALUE' 'I'")) << "Must print field access flags";

  st.reset();
  Symbol* thread_state_symbol = SymbolTable::new_symbol("java/lang/Thread$State");
  Klass* thread_state_klass = SystemDictionary::resolve_or_fail(thread_state_symbol, true, THREAD);
  ASSERT_FALSE(THREAD->has_pending_exception()) << "java/lang/Thread$State must resolve";

  fieldDescriptor enum_fd;
  Symbol* enum_symbol = SymbolTable::new_symbol("NEW");
  Symbol* enum_signature = SymbolTable::new_symbol("Ljava/lang/Thread$State;");
  ASSERT_TRUE(InstanceKlass::cast(thread_state_klass)->find_local_field(enum_symbol, enum_signature, &enum_fd))
      << "Thread.State.NEW must exist";

  enum_fd.print_on(&st);
  ASSERT_THAT(st.base(), HasSubstr("public static final enum 'NEW' 'Ljava/lang/Thread$State;'"))
      << "Must print enum field access flags";
}

#ifndef PRODUCT
// This class is friends with Method.
class MethodTest : public ::testing::Test{
 public:
  static void compare_names(Method* method, Symbol* name) {
    ASSERT_EQ(method->_name, name) << "Method name field isn't set";
  }
};

TEST_VM(Method, method_name) {
  InstanceKlass* ik = vmClasses::Object_klass();
  Symbol* tostring = SymbolTable::new_symbol("toString");
  Method* method = ik->find_method(tostring, vmSymbols::void_string_signature());
  ASSERT_TRUE(method != nullptr) << "Object must have toString";
  MethodTest::compare_names(method, tostring);
}

TEST_VM(Method, access_flag_printer) {
  ThreadInVMfromNative scope(JavaThread::current());
  ResourceMark rm;
  stringStream st;

  InstanceKlass* object_klass = vmClasses::Object_klass();
  Symbol* wait_symbol = SymbolTable::new_symbol("wait");
  Method* wait_method = object_klass->find_method(wait_symbol, vmSymbols::long_void_signature());
  ASSERT_TRUE(wait_method != nullptr) << "Object must have wait(long)";
  wait_method->print_access_flags(&st);
  ASSERT_STREQ("public final ", st.base());

  st.reset();
  Symbol* symbol_hash_code = SymbolTable::new_symbol("hashCode");
  Method* hash_code = object_klass->find_method(symbol_hash_code, vmSymbols::void_int_signature());
  ASSERT_TRUE(hash_code != nullptr) << "Object must have hashCode()";
  hash_code->print_access_flags(&st);
  ASSERT_STREQ("public native ", st.base());

  st.reset();
  InstanceKlass* string_klass = vmClasses::String_klass();
  Symbol* format_symbol = SymbolTable::new_symbol("format");
  Symbol* format_signature = SymbolTable::new_symbol("(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;");
  Method* format_method = string_klass->find_method(format_symbol, format_signature);
  ASSERT_TRUE(format_method != nullptr) << "String must have format(String, Object...)";
  format_method->print_access_flags(&st);
  ASSERT_STREQ("public static varargs ", st.base());

  st.reset();
  Symbol* compare_to_symbol = SymbolTable::new_symbol("compareTo");
  Method* compare_to_bridge_method = string_klass->find_method(compare_to_symbol, vmSymbols::object_int_signature());
  ASSERT_TRUE(compare_to_bridge_method != nullptr) << "String must have bridge compareTo(Object)";
  compare_to_bridge_method->print_access_flags(&st);
  ASSERT_STREQ("public bridge synthetic ", st.base());
}
#endif
