/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/regeneratedClasses.hpp"
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
#include "oops/klass.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"

GrowableArrayCHeap<char*, mtClassShared>* LambdaFormInvokers::_lambdaform_lines = nullptr;
Array<Array<char>*>*  LambdaFormInvokers::_static_archive_invokers = nullptr;

#define NUM_FILTER 4
static const char* filter[NUM_FILTER] = {"java.lang.invoke.Invokers$Holder",
                                         "java.lang.invoke.DirectMethodHandle$Holder",
                                         "java.lang.invoke.DelegatingMethodHandle$Holder",
                                         "java.lang.invoke.LambdaForm$Holder"};

static bool should_be_archived(char* line) {
  for (int k = 0; k < NUM_FILTER; k++) {
    if (strstr(line, filter[k]) != nullptr) {
      return true;
    }
  }
  return false;
}
#undef NUM_FILTER

void LambdaFormInvokers::append(char* line) {
  MutexLocker ml(Thread::current(), LambdaFormInvokers_lock);
  if (_lambdaform_lines == nullptr) {
    _lambdaform_lines = new GrowableArrayCHeap<char*, mtClassShared>(150);
  }
  _lambdaform_lines->append(line);
}


// convenient output
class PrintLambdaFormMessage {
 public:
  PrintLambdaFormMessage() {
    log_info(cds)("Regenerate MethodHandle Holder classes...");
  }
  ~PrintLambdaFormMessage() {
    log_info(cds)("Regenerate MethodHandle Holder classes...done");
  }
};

void LambdaFormInvokers::regenerate_holder_classes(TRAPS) {
  PrintLambdaFormMessage plm;
  if (_lambdaform_lines == nullptr || _lambdaform_lines->length() == 0) {
    log_info(cds)("Nothing to regenerate for holder classes");
    return;
  }

  ResourceMark rm(THREAD);

  Symbol* cds_name  = vmSymbols::jdk_internal_misc_CDS();
  Klass*  cds_klass = SystemDictionary::resolve_or_null(cds_name, THREAD);
  guarantee(cds_klass != nullptr, "jdk/internal/misc/CDS must exist!");

  HandleMark hm(THREAD);
  int len = _lambdaform_lines->length();
  objArrayHandle list_lines;
  {
    MutexLocker ml(Thread::current(), LambdaFormInvokers_lock);
    list_lines = oopFactory::new_objArray_handle(vmClasses::String_klass(), len, CHECK);
    for (int i = 0; i < len; i++) {
      Handle h_line = java_lang_String::create_from_str(_lambdaform_lines->at(i), CHECK);
      list_lines->obj_at_put(i, h_line());
    }
  } // Before calling into java, release vm lock.
  //
  // Object[] CDS.generateLambdaFormHolderClasses(String[] lines)
  // the returned Object[] layout:
  //   name, byte[], name, byte[] ....
  Symbol* method = vmSymbols::generateLambdaFormHolderClasses();
  Symbol* signrs = vmSymbols::generateLambdaFormHolderClasses_signature();

  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result, cds_klass, method, signrs, list_lines, THREAD);

  if (HAS_PENDING_EXCEPTION) {
    if (!PENDING_EXCEPTION->is_a(vmClasses::OutOfMemoryError_klass())) {
      log_error(cds)("%s: %s", PENDING_EXCEPTION->klass()->external_name(),
                     java_lang_String::as_utf8_string(java_lang_Throwable::message(PENDING_EXCEPTION)));
      if (CDSConfig::is_dumping_static_archive()) {
        log_error(cds)("Failed to generate LambdaForm holder classes. Is your classlist out of date?");
      } else {
        log_error(cds)("Failed to generate LambdaForm holder classes. Was the base archive generated with an outdated classlist?");
      }
      CLEAR_PENDING_EXCEPTION;
    }
    return;
  }

  objArrayHandle h_array(THREAD, (objArrayOop)result.get_oop());
  int sz = h_array->length();
  assert(sz % 2 == 0 && sz >= 2, "Must be even size of length");
  for (int i = 0; i < sz; i+= 2) {
    Handle h_name(THREAD, h_array->obj_at(i));
    typeArrayHandle h_bytes(THREAD, (typeArrayOop)h_array->obj_at(i+1));
    assert(h_name != nullptr, "Class name is null");
    assert(h_bytes != nullptr, "Class bytes is null");

    char *class_name = java_lang_String::as_utf8_string(h_name());
    if (strstr(class_name, "java/lang/invoke/BoundMethodHandle$Species_") != nullptr) {
      // The species classes are already loaded into the system dictionary
      // during the execution of CDS.generateLambdaFormHolderClasses(). No
      // need to regenerate.
      TempNewSymbol class_name_sym = SymbolTable::new_symbol(class_name);
      Klass* klass = SystemDictionary::resolve_or_null(class_name_sym, THREAD);
      assert(klass != nullptr, "must already be loaded");
      if (!klass->is_shared() && klass->shared_classpath_index() < 0) {
        // Fake it, so that it will be included into the archive.
        klass->set_shared_classpath_index(0);
        // Set the "generated" bit, so it won't interfere with JVMTI.
        // See SystemDictionaryShared::find_builtin_class().
        klass->set_is_generated_shared_class();
      }
    } else {
      int len = h_bytes->length();
      // make a copy of class bytes so GC will not affect us.
      char *buf = NEW_RESOURCE_ARRAY(char, len);
      memcpy(buf, (char*)h_bytes->byte_at_addr(0), len);
      ClassFileStream st((u1*)buf, len, nullptr, ClassFileStream::verify);
      regenerate_class(class_name, st, CHECK);
    }
  }
}

void LambdaFormInvokers::regenerate_class(char* class_name, ClassFileStream& st, TRAPS) {
  TempNewSymbol class_name_sym = SymbolTable::new_symbol(class_name);
  Klass* klass = SystemDictionary::resolve_or_null(class_name_sym, THREAD);
  assert(klass != nullptr, "must exist");
  assert(klass->is_instance_klass(), "Should be");

  ClassLoaderData* cld = ClassLoaderData::the_null_class_loader_data();
  Handle protection_domain;
  ClassLoadInfo cl_info(protection_domain);

  InstanceKlass* result = KlassFactory::create_from_stream(&st,
                                                   class_name_sym,
                                                   cld,
                                                   cl_info,
                                                   CHECK);

  assert(result->java_mirror() != nullptr, "must be");
  RegeneratedClasses::add_class(InstanceKlass::cast(klass), result);

  result->add_to_hierarchy(THREAD);

  // new class not linked yet.
  MetaspaceShared::try_link_class(THREAD, result);
  assert(!HAS_PENDING_EXCEPTION, "Invariant");

  result->set_is_generated_shared_class();
  if (!klass->is_shared()) {
    SystemDictionaryShared::set_excluded(InstanceKlass::cast(klass)); // exclude the existing class from dump
  }
  log_info(cds, lambda)("Regenerated class %s, old: " INTPTR_FORMAT " new: " INTPTR_FORMAT,
                 class_name, p2i(klass), p2i(result));
}

void LambdaFormInvokers::dump_static_archive_invokers() {
  if (_lambdaform_lines != nullptr && _lambdaform_lines->length() > 0) {
    int count = 0;
    int len   = _lambdaform_lines->length();
    for (int i = 0; i < len; i++) {
      char* str = _lambdaform_lines->at(i);
      if (should_be_archived(str)) {
        count++;
      }
    }
    if (count > 0) {
      _static_archive_invokers = ArchiveBuilder::new_ro_array<Array<char>*>(count);
      int index = 0;
      for (int i = 0; i < len; i++) {
        char* str = _lambdaform_lines->at(i);
        if (should_be_archived(str)) {
          size_t str_len = strlen(str) + 1;  // including terminating zero
          Array<char>* line = ArchiveBuilder::new_ro_array<char>((int)str_len);
          strncpy(line->adr_at(0), str, str_len);

          _static_archive_invokers->at_put(index, line);
          ArchivePtrMarker::mark_pointer(_static_archive_invokers->adr_at(index));
          index++;
        }
      }
      assert(index == count, "Should match");
    }
    log_debug(cds)("Total LF lines stored into static archive: %d", count);
  }
}

void LambdaFormInvokers::read_static_archive_invokers() {
  if (_static_archive_invokers != nullptr) {
    for (int i = 0; i < _static_archive_invokers->length(); i++) {
      Array<char>* line = _static_archive_invokers->at(i);
      char* str = line->adr_at(0);
      append(str);
    }
    log_debug(cds)("Total LF lines read from static archive: %d", _static_archive_invokers->length());
  }
}

void LambdaFormInvokers::serialize(SerializeClosure* soc) {
  soc->do_ptr(&_static_archive_invokers);
}
