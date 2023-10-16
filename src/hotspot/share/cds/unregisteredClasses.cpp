/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/unregisteredClasses.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.inline.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmSymbols.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oopHandle.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "services/threadService.hpp"

// Load the class of the given name from the location given by path. The path is specified by
// the "source:" in the class list file (see classListParser.cpp), and can be a directory or
// a JAR file.
InstanceKlass* UnregisteredClasses::load_class(Symbol* name, const char* path, TRAPS) {
  assert(name != nullptr, "invariant");
  assert(DumpSharedSpaces, "this function is only used with -Xshare:dump");

  PerfClassTraceTime vmtimer(ClassLoader::perf_app_classload_time(),
                             THREAD->get_thread_stat()->perf_timers_addr(),
                             PerfClassTraceTime::CLASS_LOAD);

  Symbol* path_symbol = SymbolTable::new_symbol(path);
  Handle url_classloader = get_url_classloader(path_symbol, CHECK_NULL);
  Handle ext_class_name = java_lang_String::externalize_classname(name, CHECK_NULL);

  JavaValue result(T_OBJECT);
  JavaCallArguments args(2);
  args.set_receiver(url_classloader);
  args.push_oop(ext_class_name);
  args.push_int(JNI_FALSE);
  JavaCalls::call_virtual(&result,
                          vmClasses::URLClassLoader_klass(),
                          vmSymbols::loadClass_name(),
                          vmSymbols::string_boolean_class_signature(),
                          &args,
                          CHECK_NULL);
  assert(result.get_type() == T_OBJECT, "just checking");
  oop obj = result.get_oop();
  return InstanceKlass::cast(java_lang_Class::as_Klass(obj));
}

class URLClassLoaderTable : public ResourceHashtable<
  Symbol*, OopHandle,
  137, // prime number
  AnyObj::C_HEAP> {};

static URLClassLoaderTable* _url_classloader_table = nullptr;

Handle UnregisteredClasses::create_url_classloader(Symbol* path, TRAPS) {
  ResourceMark rm(THREAD);
  JavaValue result(T_OBJECT);
  Handle path_string = java_lang_String::create_from_str(path->as_C_string(), CHECK_NH);
  JavaCalls::call_static(&result,
                         vmClasses::jdk_internal_loader_ClassLoaders_klass(),
                         vmSymbols::toFileURL_name(),
                         vmSymbols::toFileURL_signature(),
                         path_string, CHECK_NH);
  assert(result.get_type() == T_OBJECT, "just checking");
  oop url_h = result.get_oop();
  objArrayHandle urls = oopFactory::new_objArray_handle(vmClasses::URL_klass(), 1, CHECK_NH);
  urls->obj_at_put(0, url_h);

  Handle url_classloader = JavaCalls::construct_new_instance(
                             vmClasses::URLClassLoader_klass(),
                             vmSymbols::url_array_classloader_void_signature(),
                             urls, Handle(), CHECK_NH);
  return url_classloader;
}

Handle UnregisteredClasses::get_url_classloader(Symbol* path, TRAPS) {
  if (_url_classloader_table == nullptr) {
    _url_classloader_table = new (mtClass)URLClassLoaderTable();
  }
  OopHandle* url_classloader_ptr = _url_classloader_table->get(path);
  if (url_classloader_ptr != nullptr) {
    return Handle(THREAD, (*url_classloader_ptr).resolve());
  } else {
    Handle url_classloader = create_url_classloader(path, CHECK_NH);
    _url_classloader_table->put(path, OopHandle(Universe::vm_global(), url_classloader()));
    path->increment_refcount();
    return url_classloader;
  }
}
