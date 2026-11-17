/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "jfr/instrumentation/jfrClassTransformer.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/support/jfrClassDefineEvent.hpp"
#include "jfr/support/jfrSymbolTable.hpp"
#include "jfrfiles/jfrEventClasses.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oopsHierarchy.hpp"
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

static inline bool is_jdk_module(const ModuleEntry* module) {
  if (is_unnamed_module(module)) {
    return false;
  }
  const Symbol* const module_symbol = module->name();
  assert(module_symbol != nullptr, "invariant");
  return is_jdk_module(module_symbol->as_C_string());
}

static inline bool is_jdk_module(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  return is_jdk_module(ik->module());
}

static const char* module_source(const InstanceKlass* ik, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  const ModuleEntry* const module_entry = ik->module();
  if (is_unnamed_module(module_entry)) {
    return nullptr;
  }
  const char* const module_name = module_entry->name()->as_C_string();
  assert(module_name != nullptr, "invariant");
  if (is_jdk_module(module_name)) {
    const size_t module_name_len = strlen(module_name);
    char* const source = NEW_RESOURCE_ARRAY_IN_THREAD(jt, char, module_name_len + 6); // "jrt:/"
    jio_snprintf(source, module_name_len + 6, "%s%s", "jrt:/", module_name);
    return source;
  }
  return nullptr;
}

// java_mirror -> ProtectionDomain -> CodeSource

static const char* allocate(oop string, JavaThread* jt) {
  char* str = nullptr;
  const typeArrayOop value = java_lang_String::value(string);
  if (value != nullptr) {
    const size_t length = java_lang_String::utf8_length(string, value);
    str = NEW_RESOURCE_ARRAY_IN_THREAD(jt, char, length + 1);
    java_lang_String::as_utf8_string(string, value, str, length + 1);
  }
  return str;
}

static int compute_field_offset(const Klass* klass, const char* field_name, const char* field_signature) {
  assert(klass != nullptr, "invariant");
  Symbol* const name = SymbolTable::new_symbol(field_name);
  assert(name != nullptr, "invariant");
  Symbol* const signature = SymbolTable::new_symbol(field_signature);
  assert(signature != nullptr, "invariant");
  assert(klass->is_instance_klass(), "invariant");
  fieldDescriptor fd;
  InstanceKlass::cast(klass)->find_field(name, signature, false, &fd);
  return fd.offset();
}

static const char* location_no_frag_string(oop codesource, JavaThread* jt) {
  assert(codesource != nullptr, "invariant");
  static int loc_no_frag_offset = compute_field_offset(codesource->klass(), "locationNoFragString", "Ljava/lang/String;");
  guarantee(loc_no_frag_offset > 0, "invariant");
  oop string = codesource->obj_field(loc_no_frag_offset);
  return string != nullptr ? allocate(string, jt) : nullptr;
}

static oop code_source(oop pd) {
  assert(pd != nullptr, "invariant");
  static int codesource_offset = compute_field_offset(pd->klass(), "codesource", "Ljava/security/CodeSource;");
  return pd->obj_field(codesource_offset);
}

static const char* code_source(const InstanceKlass* ik, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  assert(ik->java_mirror() != nullptr, "invariant");
  oop pd = java_lang_Class::protection_domain(ik->java_mirror());
  if (pd == nullptr) {
    return nullptr;
  }
  oop cs = code_source(pd);
  return cs != nullptr ? location_no_frag_string(cs, jt) : nullptr;
}

// Misc source info

static const char* caller_source(const InstanceKlass* ik, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  assert(ik->class_loader_data()->is_the_null_class_loader_data(), "invariant");
  const Klass* const caller = jt->security_get_caller_class(1);
  // caller can be null, for example, during a JVMTI VM_Init hook
  if (caller != nullptr) {
    const char* caller_name = caller->external_name();
    assert(caller_name != nullptr, "invariant");
    const size_t caller_name_len = strlen(caller_name);
    char* const source = NEW_RESOURCE_ARRAY_IN_THREAD(jt, char, caller_name_len + 13); // "instance of "
    jio_snprintf(source, caller_name_len + 13, "%s%s", "instance of ", caller_name);
    return source;
  }
  return nullptr;
}

static const char* class_loader_source(const InstanceKlass* ik, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  assert(!ik->class_loader_data()->is_the_null_class_loader_data(), "invariant");
  oop class_loader = ik->class_loader_data()->class_loader();
  return class_loader->klass()->external_name();
}

static const char* misc_source(const InstanceKlass* ik, JavaThread* jt) {
  const char* source;
  if (is_jdk_module(ik)) {
    source = module_source(ik, jt);
  } else if (ik->class_loader_data()->is_the_null_class_loader_data()) {
    source = caller_source(ik, jt);
  } else {
    source = class_loader_source(ik, jt);
  }
  return source;
}

/*
 *  Ordering:
 *
 *  1. from_boot_loader_modules_image -> module_source
 *  2. code source -> the java_mirror->ProtectionDomain->CodeSource->locationNoFragString representation
 *  3. misc source -> assorted source constants as a function of state (similar to log output)
 */
static const char* source(const InstanceKlass* ik, bool from_boot_loader_modules_image, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  const char* s = nullptr;
  if (from_boot_loader_modules_image) {
    assert(is_jdk_module(ik), "invariant");
    s = module_source(ik, jt);
  } else {
    s = code_source(ik, jt);
    if (s == nullptr) {
      s = misc_source(ik, jt);
    }
  }
  return s;
}

static inline bool is_not_retransforming(const InstanceKlass* ik, JavaThread* jt) {
  return JfrClassTransformer::find_existing_klass(ik, jt) == nullptr;
}

void JfrClassDefineEvent::on_creation(const InstanceKlass* ik, const ClassFileParser& parser, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_loaded(), "invarinat");
  assert(ik->trace_id() != 0, "invariant");
  assert(!parser.is_internal(), "invariant");
  assert(jt != nullptr, "invariant");
  if (is_not_retransforming(ik, jt)) {
    if (parser.stream().from_boot_loader_modules_image()) {
      JfrTraceId::set_preload_bootloader_bit(ik);
    }
  }
}

#if INCLUDE_CDS
void JfrClassDefineEvent::on_restoration(const InstanceKlass* ik, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_loaded(), "invariant");
  assert(ik->trace_id() != 0, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(jt);)
  assert(is_not_retransforming(ik, jt), "invariant");
  if (!ik->defined_by_other_loaders()) {
    const int index = ik->shared_classpath_index();
    assert(index >= 0, "invariant");
    const AOTClassLocation* const cl = AOTClassLocationConfig::runtime()->class_location_at(index);
    assert(cl != nullptr, "invariant");
    if (cl->is_modules_image()) {
      JfrTraceId::set_preload_bootloader_bit(ik);
    }
  }
}
#endif

static inline void commit_event(const InstanceKlass* ik, const char* s) {
  assert(ik != nullptr, "invariant");
  EventClassDefine event;
  event.set_definedClass(ik);
  event.set_definingClassLoader(ik->class_loader_data());
  event.set_source(s != nullptr ? JfrSymbolTable::add(s) : 0);
  event.commit();
}

void JfrClassDefineEvent::send_event(const InstanceKlass* ik, bool from_boot_loader_modules_image, JavaThread* jt) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_loaded(), "invariant");
  assert(is_not_retransforming(ik, jt), "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(jt);)
  if (EventClassDefine::is_enabled()) {
    ResourceMark rm(jt);
    commit_event(ik, source(ik, from_boot_loader_modules_image, jt));
  }
}
