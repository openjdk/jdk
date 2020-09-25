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
#include "classfile/classLoadInfo.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/klassFactory.hpp"
#include "classfile/lambdaFormInvokers.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "memory/oopFactory.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"

GrowableArray<char*>* LambdaFormInvokers::_lambdaform_lines = NULL;

void LambdaFormInvokers::append(char* line) {
  if (_lambdaform_lines == NULL) {
    _lambdaform_lines = new GrowableArray<char*>(100);
  }
  _lambdaform_lines->append(line);
}

void LambdaFormInvokers::regenerate_holder_classes(TRAPS) {
  assert(_lambdaform_lines != NULL, "Bad List");
  ResourceMark rm(THREAD);

  Symbol* helper_name  = vmSymbols::java_lang_invoke_GenerateJLIClassesHelper();
  Klass*  helper_klass = SystemDictionary::resolve_or_null(helper_name, THREAD);
  guarantee(helper_klass != NULL, "java/lang/invoke/GenerateJLIClassesHelper must exist!");

  int len = _lambdaform_lines->length();
  objArrayHandle list_lines = oopFactory::new_objArray_handle(SystemDictionary::String_klass(), len, CHECK);
  for (int i = 0; i < len; i++) {
    char* record = _lambdaform_lines->at(i);
    record += strlen(lambda_form_invoker_tag()) + 1; // skip the @lambda_form_invoker prefix
    Handle h_line = java_lang_String::create_from_str(record, CHECK);
    list_lines->obj_at_put(i, h_line());
  }

  //
  // Object[] GenerateJLIClassesHelper.cdsGenerateHolderClasses(String[] lines)
  // the returned Object[] layout:
  //   name, byte[], name, byte[] ....
  Symbol* method = vmSymbols::cdsGenerateHolderClasses();
  Symbol* signrs = vmSymbols::cdsGenerateHolderClasses_signature();

  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result, helper_klass, method, signrs, list_lines, THREAD);
  objArrayHandle h_array(THREAD, (objArrayOop)result.get_jobject());
  if (!HAS_PENDING_EXCEPTION) {
    if (h_array() == NULL) {
      log_info(cds)("Failed call to %s.%s", helper_name->as_C_string(), method->as_C_string());
      return;
    }
  } else {
    log_info(cds)("Exception happened: %s", PENDING_EXCEPTION->klass()->name()->as_C_string());
    CLEAR_PENDING_EXCEPTION;
    return;
  }

  int sz = h_array->length();
  assert(sz % 2 == 0 && sz >= 2, "Must be even size of length");
  for (int i = 0; i < sz; i+= 2) {
    Handle h_name(THREAD, h_array->obj_at(i));
    typeArrayHandle h_bytes(THREAD, (typeArrayOop)h_array->obj_at(i+1));
    assert(h_name != NULL, "Class name is NULL");
    assert(h_bytes != NULL, "Class bytes is NULL");
    reload_class(h_name, h_bytes, THREAD);
  }

}

// class_handle - the class name, bytes_handle - the class bytes
void LambdaFormInvokers::reload_class(Handle name_handle, typeArrayHandle bytes_handle, TRAPS) {
  char* name = java_lang_String::as_utf8_string(name_handle());
  Symbol* class_name = SymbolTable::new_symbol((const char*)name);
  // the class must exist
  Klass* klass = SystemDictionary::resolve_or_null(class_name, THREAD);
  assert(klass->is_instance_klass(), "Should be");
  if (klass == NULL) {
    log_info(cds)("Class %s not present, skip", name);
    return;
  }

  int len = bytes_handle->length();
  // make a copy of class bytes so GC will not affect us.
  char *buf = resource_allocate_bytes(THREAD, len);
  memcpy(buf, (char*)bytes_handle->byte_at_addr(0), len);
  ClassFileStream st((u1*)buf, len, NULL, ClassFileStream::verify);
  ClassLoaderData* cld = ClassLoaderData::the_null_class_loader_data();
  Handle protection_domain;
  ClassLoadInfo cl_info(protection_domain);

  InstanceKlass* result = KlassFactory::create_from_stream(&st,
                                                   class_name,
                                                   cld,
                                                   cl_info,
                                                   CHECK);

  // free buf
  resource_free_bytes(buf, len);
  if (HAS_PENDING_EXCEPTION) {
    log_info(cds)("Exception happened: %s", PENDING_EXCEPTION->klass()->name()->as_C_string());
    log_info(cds)("Could not create InstanceKlass for class %s", name);
    CLEAR_PENDING_EXCEPTION;
    return;
  }

  {
    MutexLocker mu_r(THREAD, Compile_lock); // add_to_hierarchy asserts this.
    SystemDictionary::add_to_hierarchy(result, THREAD);
  }
  // new class not linked yet.
  MetaspaceShared::try_link_class(result, THREAD);
  assert(!HAS_PENDING_EXCEPTION, "Invariant");

  // exclude the existing class from dump
  SystemDictionaryShared::set_excluded(InstanceKlass::cast(klass));
  log_info(cds)("Replaced class %s, old: %p  new: %p", name, klass, result);
}
