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

#ifndef SHARE_CDS_UNREGISTEREDCLASSES_HPP
#define SHARE_CDS_UNREGISTEREDCLASSES_HPP

#include "memory/allStatic.hpp"
#include "runtime/handles.hpp"

class InstanceKlass;
class Symbol;

class UnregisteredClasses: AllStatic {
public:
  static InstanceKlass* load_class(Symbol* h_name, const char* path,
                                   Handle super_class, objArrayHandle interfaces,
                                   TRAPS);
  static void initialize(TRAPS);
  static InstanceKlass* UnregisteredClassLoader_klass() {
    return _UnregisteredClassLoader_klass;
  }

  class ClassLoaderTable;

private:
  // Don't put this in vmClasses as it's used only with CDS dumping.
  static InstanceKlass* _UnregisteredClassLoader_klass;

  static Handle create_classloader(Symbol* path, TRAPS);
  static Handle get_classloader(Symbol* path, TRAPS);
};

#endif // SHARE_CDS_UNREGISTEREDCLASSES_HPP
