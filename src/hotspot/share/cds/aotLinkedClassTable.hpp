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

#ifndef SHARE_CDS_AOTLINKEDCLASSTABLE_HPP
#define SHARE_CDS_AOTLINKEDCLASSTABLE_HPP

#include "utilities/globalDefinitions.hpp"

template <typename T> class Array;
class InstanceKlass;
class SerializeClosure;

// Classes to be bulk-loaded, in the "linked" state, at VM bootstrap.
//
// AOTLinkedClassTable is produced by AOTClassLinker when an AOTCache is assembled.
//
// AOTLinkedClassTable is consumed by AOTLinkedClassBulkLoader when an AOTCache is used
// in a production run.
//
class AOTLinkedClassTable {
  // The VM may load up to 2 CDS archives -- static and dynamic. Each
  // archive can have its own AOTLinkedClassTable.
  static AOTLinkedClassTable _for_static_archive;
  static AOTLinkedClassTable _for_dynamic_archive;

  Array<InstanceKlass*>* _boot;  // only java.base classes
  Array<InstanceKlass*>* _boot2; // boot classes in other modules
  Array<InstanceKlass*>* _platform;
  Array<InstanceKlass*>* _app;

public:
  AOTLinkedClassTable() :
    _boot(nullptr), _boot2(nullptr),
    _platform(nullptr), _app(nullptr) {}

  static AOTLinkedClassTable* for_static_archive()  { return &_for_static_archive; }
  static AOTLinkedClassTable* for_dynamic_archive() { return &_for_dynamic_archive; }

  static AOTLinkedClassTable* get(bool is_static_archive) {
    return is_static_archive ? for_static_archive() : for_dynamic_archive();
  }

  Array<InstanceKlass*>* boot()     const { return _boot;     }
  Array<InstanceKlass*>* boot2()    const { return _boot2;    }
  Array<InstanceKlass*>* platform() const { return _platform; }
  Array<InstanceKlass*>* app()      const { return _app;      }

  void set_boot    (Array<InstanceKlass*>* value) { _boot     = value; }
  void set_boot2   (Array<InstanceKlass*>* value) { _boot2    = value; }
  void set_platform(Array<InstanceKlass*>* value) { _platform = value; }
  void set_app     (Array<InstanceKlass*>* value) { _app      = value; }

  void serialize(SerializeClosure* soc);
};

#endif // SHARE_CDS_AOTLINKEDCLASSTABLE_HPP
