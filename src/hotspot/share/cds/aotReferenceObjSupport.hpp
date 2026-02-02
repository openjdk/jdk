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

#ifndef SHARE_CDS_AOTREFERENCEOBJSUPPORT_HPP
#define SHARE_CDS_AOTREFERENCEOBJSUPPORT_HPP

#include "memory/allStatic.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/exceptions.hpp"

// Support for ahead-of-time allocated instances of java.lang.ref.Reference

class AOTReferenceObjSupport : AllStatic {

public:
  static void initialize(TRAPS);
  static void stabilize_cached_reference_objects(TRAPS);
  static void init_keep_alive_objs_table() NOT_CDS_JAVA_HEAP_RETURN;
  static bool check_if_ref_obj(oop obj);
  static bool skip_field(int field_offset);
  static bool is_enabled();
};

#endif // SHARE_CDS_AOTREFERENCEOBJSUPPORT_HPP
