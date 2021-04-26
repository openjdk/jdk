/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/archiveBuilder.hpp"
#include "cds/lambdaFormInvokers.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/classLoadInfo.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/klassFactory.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"

GrowableArrayCHeap<char*, mtClassShared>* LambdaFormInvokers::_lambdaform_lines = nullptr;
Array<Array<char>*>* LambdaFormInvokers::_static_archive_invokers = nullptr;
#define NUM_FILTER 4
static const char* filter[NUM_FILTER] = {"java.lang.invoke.Invokers$Holder",
                                         "java.lang.invoke.DirectMethodHandle$Holder",
                                         "java.lang.invoke.DelegatingMethodHandle$Holder",
                                         "java.lang.invoke.LambdaForm$Holder"};

void LambdaFormInvokers::append(char* line) {
  if (_lambdaform_lines == NULL) {
    _lambdaform_lines = new GrowableArrayCHeap<char*, mtClassShared>(150);
  }
  _lambdaform_lines->append(line);
}

void LambdaFormInvokers::append_filtered(char* line) {
  for (int k = 0; k < NUM_FILTER; k++) {
    if (strstr(line, filter[k]) != nullptr) {
        append(line);
        break;
    }
  }
}
#undef NUM_FILTER

void LambdaFormInvokers::regenerate_holder_classes(TRAPS) {
  if (_lambdaform_lines == nullptr || _lambdaform_lines->length() == 0) {
    log_info(cds)("Nothing to regenerate for holder classes");
    return;
  }
  ResourceMark rm(THREAD);

  Symbol* cds_name  = vmSymbols::jdk_internal_misc_CDS();
  Klass*  cds_klass = SystemDictionary::resolve_or_null(cds_name, THREAD);
  guarantee(cds_klass != NULL, "jdk/internal/misc/CDS must exist!");

  log_info(cds)("Total lambdaform lines %d", _lambdaform_lines->length());
  HandleMark hm(THREAD);
  int len = _lambdaform_lines->length();
  objArrayHandle list_lines = oopFactory::new_objArray_handle(vmClasses::String_klass(), len, CHECK);
  if (list_lines == nullptr) {
    log_info(cds)("Out of memory, skip regenerate holder classes");
    return;
  }
  for (int i = 0; i < len; i++) {
    Handle h_line = java_lang_String::create_from_str(_lambdaform_lines->at(i), CHECK);
    if (h_line == nullptr) {
      log_info(cds)("Out of memory, skip regenerate holder classes");
      return;
    }
    list_lines->obj_at_put(i, h_line());
  }

  //
  // Object[] CDS.generateLambdaFormHolderClasses(String[] lines)
  // the returned Object[] layout:
  //   name, byte[], name, byte[] ....
  Symbol* method = vmSymbols::generateLambdaFormHolderClasses();
  Symbol* signrs = vmSymbols::generateLambdaFormHolderClasses_signature();

  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result, cds_klass, method, signrs, list_lines, THREAD);

  if (HAS_PENDING_EXCEPTION) {
    log_info(cds)("%s: %s", THREAD->pending_exception()->klass()->external_name(),
                            java_lang_String::as_utf8_string(java_lang_Throwable::message(THREAD->pending_exception())));
    CLEAR_PENDING_EXCEPTION;
    return;
  }

  objArrayHandle h_array(THREAD, (objArrayOop)result.get_oop());
  int sz = h_array->length();
  assert(sz % 2 == 0 && sz >= 2, "Must be even size of length");
  for (int i = 0; i < sz; i+= 2) {
    Handle h_name(THREAD, h_array->obj_at(i));
    typeArrayHandle h_bytes(THREAD, (typeArrayOop)h_array->obj_at(i+1));
    assert(h_name != NULL, "Class name is NULL");
    assert(h_bytes != NULL, "Class bytes is NULL");

    char *class_name = java_lang_String::as_utf8_string(h_name());
    if (class_name == nullptr) {
      log_info(cds)("Out of memory when reloading classes, quit");
      return;
    }
    int len = h_bytes->length();
    // make a copy of class bytes so GC will not affect us.
    char *buf = resource_allocate_bytes(THREAD, len);
    if (buf == nullptr) {
      log_info(cds)("Out of memory when reloading classes for %s, quit", class_name);
      return;
    }
    memcpy(buf, (char*)h_bytes->byte_at_addr(0), len);
    ClassFileStream st((u1*)buf, len, NULL, ClassFileStream::verify);

    reload_class(class_name, st, THREAD);
    // free buf
    resource_free_bytes(buf, len);

    if (HAS_PENDING_EXCEPTION) {
      log_info(cds)("Exception happened: %s", PENDING_EXCEPTION->klass()->name()->as_C_string());
      log_info(cds)("Could not create InstanceKlass for class %s", class_name);
      CLEAR_PENDING_EXCEPTION;
      return;
    }
  }
}

// class_handle - the class name, bytes_handle - the class bytes
void LambdaFormInvokers::reload_class(char* name, ClassFileStream& st, TRAPS) {
  Symbol* class_name = SymbolTable::new_symbol((const char*)name);
  // the class must exist
  Klass* klass = SystemDictionary::resolve_or_null(class_name, THREAD);
  if (klass == NULL) {
    log_info(cds)("Class %s not present, skip", name);
    return;
  }
  assert(klass->is_instance_klass(), "Should be");

  ClassLoaderData* cld = ClassLoaderData::the_null_class_loader_data();
  Handle protection_domain;
  ClassLoadInfo cl_info(protection_domain);

  InstanceKlass* result = KlassFactory::create_from_stream(&st,
                                                   class_name,
                                                   cld,
                                                   cl_info,
                                                   CHECK);

  {
    MutexLocker mu_r(THREAD, Compile_lock); // add_to_hierarchy asserts this.
    SystemDictionary::add_to_hierarchy(result);
  }
  // new class not linked yet.
  MetaspaceShared::try_link_class(THREAD, result);
  assert(!HAS_PENDING_EXCEPTION, "Invariant");

  // exclude the existing class from dump
  SystemDictionaryShared::set_excluded(InstanceKlass::cast(klass));
  SystemDictionaryShared::init_dumptime_info(result);
  log_info(cds, lambda)("Replaced class %s, old: %p  new: %p", name, klass, result);
}

void LambdaFormInvokers::dump_static_archive_invokers() {
  if (_lambdaform_lines != nullptr && _lambdaform_lines->length() > 0) {
    int count = _lambdaform_lines->length();
    _static_archive_invokers = ArchiveBuilder::new_ro_array<Array<char>*>(count);
    for (int i = 0; i < count; i++) {
      char* str = _lambdaform_lines->at(i);
      int len = (int)strlen(str);
      Array<char>* line = ArchiveBuilder::new_ro_array<char>(len+1);
      strcpy(line->adr_at(0), str); // copies terminating zero as well

      _static_archive_invokers->at_put(i, line);
      ArchivePtrMarker::mark_pointer(_static_archive_invokers->adr_at(i));
    }
  }
}

void LambdaFormInvokers::serialize(SerializeClosure* soc) {
  soc->do_ptr((void**)&_static_archive_invokers);
}
