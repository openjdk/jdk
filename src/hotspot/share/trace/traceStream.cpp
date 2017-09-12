/*
* Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "trace/traceStream.hpp"
#if INCLUDE_TRACE
#include "classfile/classLoaderData.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/klass.hpp"
#include "oops/method.hpp"
#include "oops/symbol.hpp"

void TraceStream::print_val(const char* label, const Klass* val) const {
  ResourceMark rm;
  const char* description = "NULL";
  if (val != NULL) {
    const Symbol* name = val->name();
    if (name != NULL) {
      description = name->as_C_string();
    }
  }
  tty->print("%s = %s", label, description);
}

void TraceStream::print_val(const char* label, const Method* val) const {
  ResourceMark rm;
  const char* description = "NULL";
  if (val != NULL) {
    description = val->name_and_sig_as_C_string();
  }
  tty->print("%s = %s", label, description);
}

void TraceStream::print_val(const char* label, const ClassLoaderData* cld) const {
  ResourceMark rm;
  if (cld == NULL || cld->is_anonymous()) {
    tty->print("%s = NULL", label);
    return;
  }
  const char* class_loader_name = "NULL";
  const char* class_loader_type_name = "NULL";
  const oop class_loader_oop = cld->class_loader();

  if (class_loader_oop != NULL) {
    const Klass* k = class_loader_oop->klass();
    assert(k != NULL, "invariant");
    const Symbol* klass_name_sym = k->name();
    if (klass_name_sym != NULL) {
      class_loader_type_name = klass_name_sym->as_C_string();
    }
    const oop class_loader_name_oop =
      java_lang_ClassLoader::name(class_loader_oop);
    if (class_loader_name_oop != NULL) {
      const char* class_loader_name_from_oop =
        java_lang_String::as_utf8_string(class_loader_name_oop);
      if (class_loader_name_from_oop != NULL &&
            class_loader_name_from_oop[0] != '\0') {
        class_loader_name = class_loader_name_from_oop;
      }
    }
  } else {
    assert(class_loader_oop == NULL, "invariant");
    // anonymous CLDs are excluded, this would be the boot loader
    class_loader_name = "boot";
  }
  tty->print("%s = name=%s class=%s", label, class_loader_name, class_loader_type_name);
}

#endif // INCLUDE_TRACE
