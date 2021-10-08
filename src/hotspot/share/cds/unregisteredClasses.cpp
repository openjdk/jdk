/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmSymbols.hpp"
#include "memory/oopFactory.hpp"
#include "oops/instanceKlass.hpp"
#include "runtime/javaCalls.hpp"
#include "services/threadService.hpp"

// Load the class of the given name from the location given by path. The path is specified by
// the "source:" in the class list file (see classListParser.cpp), and can be a directory or
// a JAR file.
InstanceKlass* UnregisteredClasses::load_class(Symbol* name, const char* path, TRAPS) {
  assert(name != NULL, "invariant");
  assert(DumpSharedSpaces, "this function is only used with -Xshare:dump");

  {
    PerfClassTraceTime vmtimer(ClassLoader::perf_sys_class_lookup_time(),
                               THREAD->get_thread_stat()->perf_timers_addr(),
                               PerfClassTraceTime::CLASS_LOAD);
  }

  Symbol* jar_util_name  = vmSymbols::jdk_internal_misc_UberJarUtils();
  Klass*  jar_util_klass = SystemDictionary::resolve_or_null(jar_util_name, THREAD);
  guarantee(jar_util_klass != NULL, "jdk/internal/misc/UberJarUtils must exist!");

  Symbol* method = vmSymbols::loadClass_name();
  Symbol* signature = vmSymbols::string_string_class_signature();
  Handle path_string = java_lang_String::create_from_str(path, CHECK_NULL);
  Handle ext_class_name = java_lang_String::externalize_classname(name, CHECK_NULL);

  JavaValue result(T_OBJECT);
  JavaCallArguments args(2);
  args.push_oop(path_string);
  args.push_oop(ext_class_name);
  JavaCalls::call_static(&result, jar_util_klass, method, signature, &args, CHECK_NULL);

  assert(result.get_type() == T_OBJECT, "just checking");
  oop obj = result.get_oop();
  return InstanceKlass::cast(java_lang_Class::as_Klass(obj));
}
