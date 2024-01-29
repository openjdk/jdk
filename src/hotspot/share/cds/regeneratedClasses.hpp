/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_REGENERATEDCLASSES_HPP
#define SHARE_CDS_REGENERATEDCLASSES_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

class InstanceKlass;

// CDS regenerates some of the classes that are loaded normally during the dumping
// process. For example, LambdaFormInvokers creates new versions of the four
// java.lang.invoke.xxx$Holder classes that have additional methods.
//
// RegeneratedClasses records the relocation between the "original" and
// "regenerated" versions of these classes. When writing the CDS archive, all
// references to the "original" versions are redirected to the "regenerated"
// versions.
class RegeneratedClasses : public AllStatic {
 public:
  static void add_class(InstanceKlass* orig_klass, InstanceKlass* regen_klass);
  static void cleanup();
  static bool has_been_regenerated(address orig_obj);
  static void record_regenerated_objects();
};

#endif // SHARE_CDS_REGENERATEDCLASSES_HPP
