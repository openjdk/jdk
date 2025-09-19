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

#include "cds/aotMetaspace.hpp"
#include "cds/aotOopChecker.hpp"
#include "cds/heapShared.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/vmClasses.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "utilities/debug.hpp"

#if INCLUDE_CDS_JAVA_HEAP

oop AOTOopChecker::get_oop_field(oop obj, const char* name, const char* sig) {
  Symbol* name_sym = SymbolTable::probe(name, checked_cast<int>(strlen(name)));
  assert(name_sym != nullptr, "Symbol must have been resolved for an existing field of this obj");
  Symbol* sig_sym = SymbolTable::probe(sig, checked_cast<int>(strlen(sig)));
  assert(sig_sym != nullptr, "Symbol must have been resolved for an existing field of this obj");

  fieldDescriptor fd;
  Klass* k = InstanceKlass::cast(obj->klass())->find_field(name_sym, sig_sym, &fd);
  assert(k != nullptr, "field must exist");
  precond(!fd.is_static());
  precond(fd.field_type() == T_OBJECT || fd.field_type() == T_ARRAY);
  return obj->obj_field(fd.offset());
}

// Make sure we are not caching objects with assumptions that can be violated in
// the production run.
void AOTOopChecker::check(oop obj) {
  // Currently we only check URL objects, but more rules may be added in the future.

  if (obj->klass()->is_subclass_of(vmClasses::URL_klass())) {
    // If URL could be subclassed, obj may have new fields that we don't know about.
    precond(vmClasses::URL_klass()->is_final());

    // URLs are referenced by the CodeSources/ProtectDomains that are cached
    // for AOT-linked classes loaded by the platform/app loaders.
    //
    // Do not cache any URLs whose URLStreamHandler can be overridden by the application.
    // - "jrt" and "file" will always use the built-in URLStreamHandler. See
    //   java.net.URL::isOverrideable().
    // -  When an AOT-linked class is loaded from a JAR file, its URL is something
    //    like file:HelloWorl.jar, and does NOT use the "jar" protocol.
    oop protocol = get_oop_field(obj, "protocol", "Ljava/lang/String;");
    if (!java_lang_String::equals(protocol, "jrt", 3) &&
        !java_lang_String::equals(protocol, "file", 4)) {
      ResourceMark rm;
      log_error(aot)("Must cache only URLs with jrt/file protocols but got: %s",
                     java_lang_String::as_quoted_ascii(protocol));
      HeapShared::debug_trace();
      AOTMetaspace::unrecoverable_writing_error();
    }
  }
}

#endif //INCLUDE_CDS_JAVA_HEAP
