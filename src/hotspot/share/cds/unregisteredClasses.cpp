/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/cdsConfig.hpp"
#include "cds/unregisteredClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oopHandle.hpp"
#include "oops/oopHandle.inline.hpp"
#include "runtime/handles.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "services/threadService.hpp"

static InstanceKlass* _UnregisteredClassLoader_klass;
static InstanceKlass* _UnregisteredClassLoader_Source_klass;
static OopHandle _unregistered_class_loader;

void UnregisteredClasses::initialize(TRAPS) {
  if (_UnregisteredClassLoader_klass != nullptr) {
    return;
  }

  Symbol* klass_name;
  Klass* k;

  // no need for synchronization as this function is called single-threaded.
  klass_name = SymbolTable::new_symbol("jdk/internal/misc/CDS$UnregisteredClassLoader");
  k = SystemDictionary::resolve_or_fail(klass_name, true, CHECK);
  _UnregisteredClassLoader_klass = InstanceKlass::cast(k);

  klass_name = SymbolTable::new_symbol("jdk/internal/misc/CDS$UnregisteredClassLoader$Source");
  k = SystemDictionary::resolve_or_fail(klass_name, true, CHECK);
  _UnregisteredClassLoader_Source_klass = InstanceKlass::cast(k);

  precond(_unregistered_class_loader.is_empty());
  HandleMark hm(THREAD);
  const Handle cl = JavaCalls::construct_new_instance(_UnregisteredClassLoader_klass,
                                                      vmSymbols::void_method_signature(), CHECK);
  _unregistered_class_loader = OopHandle(Universe::vm_global(), cl());
}

// Load the class of the given name from the location given by path. The path is specified by
// the "source:" in the class list file (see classListParser.cpp), and can be a directory or
// a JAR file.
InstanceKlass* UnregisteredClasses::load_class(Symbol* name, const char* path, TRAPS) {
  assert(name != nullptr, "invariant");
  assert(CDSConfig::is_dumping_static_archive(), "this function is only used with -Xshare:dump");

  PerfClassTraceTime vmtimer(ClassLoader::perf_app_classload_time(),
                             THREAD->get_thread_stat()->perf_timers_addr(),
                             PerfClassTraceTime::CLASS_LOAD);

  assert(!_unregistered_class_loader.is_empty(), "not initialized");
  Handle classloader(THREAD, _unregistered_class_loader.resolve());

  // Call CDS$UnregisteredClassLoader::load(String name, String source)
  Symbol* methodName = SymbolTable::new_symbol("load");
  Symbol* methodSignature = SymbolTable::new_symbol("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Class;");
  Handle ext_class_name = java_lang_String::externalize_classname(name, CHECK_NULL);
  Handle path_string = java_lang_String::create_from_str(path, CHECK_NULL);

  JavaValue result(T_OBJECT);
  JavaCalls::call_virtual(&result,
                          classloader,
                          _UnregisteredClassLoader_klass,
                          methodName,
                          methodSignature,
                          ext_class_name,
                          path_string,
                          CHECK_NULL);
  assert(result.get_type() == T_OBJECT, "just checking");

  return InstanceKlass::cast(java_lang_Class::as_Klass(result.get_oop()));
}

bool UnregisteredClasses::check_for_exclusion(const InstanceKlass* k) {
  if (_UnregisteredClassLoader_klass == nullptr) {
    return false; // Uninitialized
  }
  return k == _UnregisteredClassLoader_klass ||
         k->implements_interface(_UnregisteredClassLoader_Source_klass);
}
