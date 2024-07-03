/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_CDSENUMKLASS_HPP
#define SHARE_CDS_CDSENUMKLASS_HPP

#include "memory/allStatic.hpp"
#include "oops/oop.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/macros.hpp"

class InstanceKlass;
class JavaFieldStream;
class KlassSubGraphInfo;

class CDSEnumKlass: AllStatic {
public:
  static bool is_enum_obj(oop orig_obj);
  static void handle_enum_obj(int level,
                              KlassSubGraphInfo* subgraph_info,
                              oop orig_obj);
  static bool initialize_enum_klass(InstanceKlass* k, TRAPS) NOT_CDS_JAVA_HEAP_RETURN_(false);

private:
  static void archive_static_field(int level, KlassSubGraphInfo* subgraph_info,
                                   InstanceKlass* ik, oop mirror, JavaFieldStream& fs);
};

#endif // SHARE_CDS_CDSENUMKLASS_HPP
