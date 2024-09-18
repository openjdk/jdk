/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/aotClassInitializer.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "oops/instanceKlass.inline.hpp"

bool AOTClassInitializer::can_archive_initialized_mirror(InstanceKlass* ik) {
  assert(!ArchiveBuilder::current()->is_in_buffer_space(ik), "must be source klass");
  if (!CDSConfig::is_initing_classes_at_dump_time()) {
    return false;
  }

  if (ik->is_hidden()) {
    return HeapShared::is_archivable_hidden_klass(ik);
  }

  if (ik->is_initialized() && ik->java_super() == vmClasses::Enum_klass()) {
    return true;
  }

  Symbol* name = ik->name();
  if (name->equals("jdk/internal/constant/PrimitiveClassDescImpl") ||
      name->equals("jdk/internal/constant/ReferenceClassDescImpl") ||
      name->equals("java/lang/constant/ConstantDescs")) {
    assert(ik->is_initialized(), "must be");
    // The above 3 classes are special cases needed to support the aot-caching of
    // java.lang.invoke.MethodType instances:
    // - MethodType points to sun.invoke.util.Wrapper enums
    // - The Wrapper enums point to static final fields in the above 3 classes.
    //   E.g., ConstantDescs.CD_Boolean.
    // - If we re-run the <clinit> of these 3 classes again during the production
    //   run, ConstantDescs.CD_Boolean will get a new value that has a different
    //   object identity than the value referenced the the Wrapper enums.
    // - However, Wrapper requires object identity (it allows the use of == to
    //   test the equality of ClassDesc, etc).
    // Therefore, we must preserve the static fields of these 3 classes from
    // the assembly phase.
    return true;
  }
  if (CDSConfig::is_dumping_invokedynamic()) {
    if (name->equals("java/lang/invoke/DirectMethodHandle$AOTHolder") ||
        name->equals("java/lang/invoke/LambdaForm$NamedFunction$AOTHolder") ||
        name->equals("java/lang/invoke/MethodType$AOTHolder")) {
      assert(ik->is_initialized(), "must be");
      return true;
    }
  }

  return false;
}
