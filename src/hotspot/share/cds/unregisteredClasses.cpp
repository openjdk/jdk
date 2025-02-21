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

#include "cds/unregisteredClasses.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.inline.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oopHandle.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "services/threadService.hpp"

InstanceKlass* UnregisteredClasses::_UnregisteredClassLoader_klass = nullptr;

void UnregisteredClasses::initialize(TRAPS) {
  if (_UnregisteredClassLoader_klass == nullptr) {
    // no need for synchronization as this function is called single-threaded.
    Symbol* klass_name = SymbolTable::new_symbol("jdk/internal/misc/CDS$UnregisteredClassLoader");
    Klass* k = SystemDictionary::resolve_or_fail(klass_name, true, CHECK);
    _UnregisteredClassLoader_klass = InstanceKlass::cast(k);
  }
}

// Load the class of the given name from the location given by path. The path is specified by
// the "source:" in the class list file (see classListParser.cpp), and can be a directory or
// a JAR file.
InstanceKlass* UnregisteredClasses::load_class(Symbol* name, const char* path,
                                               Handle super_class, objArrayHandle interfaces, TRAPS) {
  assert(name != nullptr, "invariant");
  assert(CDSConfig::is_dumping_static_archive(), "this function is only used with -Xshare:dump");

  PerfClassTraceTime vmtimer(ClassLoader::perf_app_classload_time(),
                             THREAD->get_thread_stat()->perf_timers_addr(),
                             PerfClassTraceTime::CLASS_LOAD);

  // Call CDS$UnregisteredClassLoader::load(String name, Class<?> superClass, Class<?>[] interfaces)
  Symbol* methodName = SymbolTable::new_symbol("load");
  Symbol* methodSignature = SymbolTable::new_symbol("(Ljava/lang/String;Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/Class;");
  Symbol* path_symbol = SymbolTable::new_symbol(path);
  Handle classloader = get_classloader(path_symbol, CHECK_NULL);
  Handle ext_class_name = java_lang_String::externalize_classname(name, CHECK_NULL);

  JavaValue result(T_OBJECT);
  JavaCallArguments args(3);
  args.set_receiver(classloader);
  args.push_oop(ext_class_name);
  args.push_oop(super_class);
  args.push_oop(interfaces);
  JavaCalls::call_virtual(&result,
                          UnregisteredClassLoader_klass(),
                          methodName,
                          methodSignature,
                          &args,
                          CHECK_NULL);
  assert(result.get_type() == T_OBJECT, "just checking");
  oop obj = result.get_oop();
  return InstanceKlass::cast(java_lang_Class::as_Klass(obj));
}

class UnregisteredClasses::ClassLoaderTable : public ResourceHashtable<
  Symbol*, OopHandle,
  137, // prime number
  AnyObj::C_HEAP> {};

static UnregisteredClasses::ClassLoaderTable* _classloader_table = nullptr;

Handle UnregisteredClasses::create_classloader(Symbol* path, TRAPS) {
  ResourceMark rm(THREAD);
  JavaValue result(T_OBJECT);
  Handle path_string = java_lang_String::create_from_str(path->as_C_string(), CHECK_NH);
  Handle classloader = JavaCalls::construct_new_instance(
                           UnregisteredClassLoader_klass(),
                           vmSymbols::string_void_signature(),
                           path_string, CHECK_NH);
  return classloader;
}

Handle UnregisteredClasses::get_classloader(Symbol* path, TRAPS) {
  if (_classloader_table == nullptr) {
    _classloader_table = new (mtClass)ClassLoaderTable();
  }
  OopHandle* classloader_ptr = _classloader_table->get(path);
  if (classloader_ptr != nullptr) {
    return Handle(THREAD, (*classloader_ptr).resolve());
  } else {
    Handle classloader = create_classloader(path, CHECK_NH);
    _classloader_table->put(path, OopHandle(Universe::vm_global(), classloader()));
    path->increment_refcount();
    return classloader;
  }
}
