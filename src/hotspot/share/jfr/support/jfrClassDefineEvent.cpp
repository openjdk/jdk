/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotClassLocation.hpp"
#include "classfile/classFileParser.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "jfr/instrumentation/jfrClassTransformer.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/support/jfrClassDefineEvent.hpp"
#include "jfr/support/jfrSymbolTable.hpp"
#include "jfrfiles/jfrEventClasses.hpp"
#include "oops/instanceKlass.hpp"
#include "runtime/javaThread.hpp"

 /*
  * Two cases for JDK modules as outlined by JEP 200: The Modular JDK.
  *
  * The modular structure of the JDK implements the following principles:
  * 1. Standard modules, whose specifications are governed by the JCP, have names starting with the string "java.".
  * 2. All other modules are merely part of the JDK, and have names starting with the string "jdk.".
  * */
static inline bool is_jdk_module(const char* module_name) {
  assert(module_name != nullptr, "invariant");
  return strstr(module_name, "java.") == module_name || strstr(module_name, "jdk.") == module_name;
}

static inline bool is_unnamed_module(const ModuleEntry* module) {
  return module == nullptr || !module->is_named();
}

static inline bool is_jdk_module(const ModuleEntry* module, JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  if (is_unnamed_module(module)) {
    return false;
  }
  const Symbol* const module_symbol = module->name();
  assert(module_symbol != nullptr, "invariant");
  return is_jdk_module(module_symbol->as_C_string());
}

static inline bool is_jdk_module(const InstanceKlass* ik, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  return is_jdk_module(ik->module(), jt);
}

static traceid module_path(const InstanceKlass* ik, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  const ModuleEntry* const module_entry = ik->module();
  if (is_unnamed_module(module_entry)) {
    return 0;
  }
  const char* const module_name = module_entry->name()->as_C_string();
  assert(module_name != nullptr, "invariant");
  if (is_jdk_module(module_name)) {
    const size_t module_name_len = strlen(module_name);
    char* const path = NEW_RESOURCE_ARRAY_IN_THREAD(jt, char, module_name_len + 6); // "jrt:/"
    jio_snprintf(path, module_name_len + 6, "%s%s", "jrt:/", module_name);
    return JfrSymbolTable::add(path);
  }
  return 0;
}

static traceid caller_path(const InstanceKlass* ik, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  assert(ik->class_loader_data()->is_the_null_class_loader_data(), "invariant");
  const Klass* const caller = jt->security_get_caller_class(1);
  // caller can be null, for example, during a JVMTI VM_Init hook
  if (caller != nullptr) {
    const char* caller_name = caller->external_name();
    assert(caller_name != nullptr, "invariant");
    const size_t caller_name_len = strlen(caller_name);
    char* const path = NEW_RESOURCE_ARRAY_IN_THREAD(jt, char, caller_name_len + 13); // "instance of "
    jio_snprintf(path, caller_name_len + 13, "%s%s", "instance of ", caller_name);
    return JfrSymbolTable::add(path);
  }
  return 0;
}

static traceid class_loader_path(const InstanceKlass* ik, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  assert(!ik->class_loader_data()->is_the_null_class_loader_data(), "invariant");
  oop class_loader = ik->class_loader_data()->class_loader();
  const char* class_loader_name = class_loader->klass()->external_name();
  return class_loader_name != nullptr ? JfrSymbolTable::add(class_loader_name) : 0;
}

static inline bool is_not_retransforming(const InstanceKlass* ik, JavaThread* jt) {
  return JfrClassTransformer::find_existing_klass(ik, jt) == nullptr;
}

static traceid get_source(const InstanceKlass* ik, JavaThread* jt) {
  traceid source_id = 0;
  if (is_jdk_module(ik, jt)) {
    source_id = module_path(ik, jt);
  } else if (ik->class_loader_data()->is_the_null_class_loader_data()) {
    source_id = caller_path(ik, jt);
  } else {
    source_id = class_loader_path(ik, jt);
  }
  return source_id;
}

static inline void send_event(const InstanceKlass* ik, traceid source_id) {
  EventClassDefine event;
  event.set_definedClass(ik);
  event.set_definingClassLoader(ik->class_loader_data());
  event.set_source(source_id);
  event.commit();
}

void JfrClassDefineEvent::on_creation(const InstanceKlass* ik, const ClassFileParser& parser, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  assert(ik->trace_id() != 0, "invariant");
  assert(!parser.is_internal(), "invariant");
  assert(jt != nullptr, "invariant");

  if (EventClassDefine::is_enabled() && is_not_retransforming(ik, jt)) {
    ResourceMark rm(jt);
    traceid source_id = 0;
    const ClassFileStream& stream = parser.stream();
    if (stream.source() != nullptr) {
      if (stream.from_boot_loader_modules_image()) {
        assert(is_jdk_module(ik, jt), "invariant");
        source_id = module_path(ik, jt);
      } else {
        source_id = JfrSymbolTable::add(stream.source());
      }
    } else {
      source_id = get_source(ik, jt);
    }
    send_event(ik, source_id);
  }
}

#if INCLUDE_CDS
static traceid get_source(const AOTClassLocation* cl, JavaThread* jt) {
  assert(cl != nullptr, "invariant");
  assert(!cl->is_modules_image(), "invariant");
  const char* const path = cl->path();
  assert(path != nullptr, "invariant");
  size_t len = strlen(path);
  const char* file_type = cl->file_type_string();
  assert(file_type != nullptr, "invariant");
  len += strlen(file_type) + 3; // ":/" + null
  char* const url = NEW_RESOURCE_ARRAY_IN_THREAD(jt, char, len);
  jio_snprintf(url, len, "%s%s%s", file_type, ":/", path);
  return JfrSymbolTable::add(url);
}

void JfrClassDefineEvent::on_restoration(const InstanceKlass* ik, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  assert(ik->trace_id() != 0, "invariant");
  assert(jt != nullptr, "invariant");

  if (EventClassDefine::is_enabled()) {
    ResourceMark rm(jt);
    assert(is_not_retransforming(ik, jt), "invariant");
    const int index = ik->shared_classpath_index();
    assert(index >= 0, "invariant");
    const AOTClassLocation* const cl = AOTClassLocationConfig::runtime()->class_location_at(index);
    assert(cl != nullptr, "invariant");
    send_event(ik, cl->is_modules_image() ? module_path(ik, jt) : get_source(cl, jt));
  }
}
#endif
