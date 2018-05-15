/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_RECORDER_CHECKPOINT_TYPES_JFRTYPESET_HPP
#define SHARE_VM_JFR_RECORDER_CHECKPOINT_TYPES_JFRTYPESET_HPP

#include "jfr/utilities/jfrAllocation.hpp"

class ClassLoaderData;
class JfrArtifactClosure;
class JfrArtifactSet;
class JfrCheckpointWriter;
class Klass;

class ModuleEntry;
class PackageEntry;

class JfrTypeSet : AllStatic {
  friend class CLDCallback;
  friend class JfrTypeManager;
  friend class TypeSetSerialization;
 private:
  static JfrArtifactSet* _artifacts;
  static JfrArtifactClosure* _subsystem_callback;
  static bool _class_unload;

  static void do_klass(Klass* k);
  static void do_unloaded_klass(Klass* k);
  static void do_klasses();

  static void do_package(PackageEntry* entry);
  static void do_unloaded_package(PackageEntry* entry);
  static void do_packages();

  static void do_module(ModuleEntry* entry);
  static void do_unloaded_module(ModuleEntry* entry);
  static void do_modules();

  static void do_class_loader_data(ClassLoaderData* cld);
  static void do_unloaded_class_loader_data(ClassLoaderData* cld);
  static void do_class_loaders();

  static void write_klass_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer);
  static void write_package_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer);
  static void write_module_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer);
  static void write_class_loader_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer);
  static void write_method_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer);
  static void write_symbol_constants(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer);
  static void serialize(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer, bool class_unload);
};

#endif // SHARE_VM_JFR_RECORDER_CHECKPOINT_TYPES_JFRTYPESET_HPP
