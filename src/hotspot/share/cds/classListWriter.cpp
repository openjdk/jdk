/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/cds_globals.hpp"
#include "cds/classListWriter.hpp"
#include "cds/lambdaFormInvokers.inline.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "memory/resourceArea.hpp"
#include "oops/constantPool.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "runtime/mutexLocker.hpp"

fileStream* ClassListWriter::_classlist_file = nullptr;

void ClassListWriter::init() {
  // For -XX:DumpLoadedClassList=<file> option
  if (DumpLoadedClassList != nullptr) {
    const char* list_name = make_log_name(DumpLoadedClassList, nullptr);
    _classlist_file = new(mtInternal)
                         fileStream(list_name);
    _classlist_file->print_cr("# NOTE: Do not modify this file.");
    _classlist_file->print_cr("#");
    _classlist_file->print_cr("# This file is generated via the -XX:DumpLoadedClassList=<class_list_file> option");
    _classlist_file->print_cr("# and is used at CDS archive dump time (see -Xshare:dump).");
    _classlist_file->print_cr("#");
    FREE_C_HEAP_ARRAY(char, list_name);
  }
}

void ClassListWriter::write(const InstanceKlass* k, const ClassFileStream* cfs) {
  assert(is_enabled(), "must be");

  if (!ClassLoader::has_jrt_entry()) {
    log_warning(cds)("DumpLoadedClassList and CDS are not supported in exploded build");
    DumpLoadedClassList = nullptr;
    return;
  }

  ClassListWriter w;
  write_to_stream(k, w.stream(), cfs);
}

class ClassListWriter::IDTable : public ResourceHashtable<
  const InstanceKlass*, int,
  15889, // prime number
  AnyObj::C_HEAP> {};

ClassListWriter::IDTable* ClassListWriter::_id_table = nullptr;
int ClassListWriter::_total_ids = 0;

int ClassListWriter::get_id(const InstanceKlass* k) {
  assert_locked();
  if (_id_table == nullptr) {
    _id_table = new (mtClass)IDTable();
  }
  bool created;
  int* v = _id_table->put_if_absent(k, &created);
  if (created) {
    *v = _total_ids++;
  }
  return *v;
}

bool ClassListWriter::has_id(const InstanceKlass* k) {
  assert_locked();
  if (_id_table != nullptr) {
    return _id_table->get(k) != nullptr;
  } else {
    return false;
  }
}

void ClassListWriter::handle_class_unloading(const InstanceKlass* klass) {
  assert_locked();
  if (_id_table != nullptr) {
    _id_table->remove(klass);
  }
}

void ClassListWriter::write_to_stream(const InstanceKlass* k, outputStream* stream, const ClassFileStream* cfs) {
  assert_locked();

  ClassLoaderData* loader_data = k->class_loader_data();
  bool is_builtin_loader = SystemDictionaryShared::is_builtin_loader(loader_data);
  if (!is_builtin_loader) {
    // class may be loaded from shared archive
    if (!k->is_shared()) {
      if (cfs == nullptr || cfs->source() == nullptr) {
        // CDS static dump only handles unregistered class with known source.
        return;
      }
      if (strncmp(cfs->source(), "file:", 5) != 0) {
        return;
      }
    } else {
      // Shared unregistered classes are skipped since their real source are not recorded in shared space.
      return;
    }
    if (!SystemDictionaryShared::add_unregistered_class(Thread::current(), (InstanceKlass*)k)) {
      return;
    }
  }

  // filter out java/lang/invoke/BoundMethodHandle$Species...
  if (cfs != nullptr && cfs->source() != nullptr && strcmp(cfs->source(), "_ClassSpecializer_generateConcreteSpeciesCode") == 0) {
    return;
  }

  {
    InstanceKlass* super = k->java_super();
    if (super != nullptr && !has_id(super)) {
      return;
    }

    Array<InstanceKlass*>* interfaces = k->local_interfaces();
    int len = interfaces->length();
    for (int i = 0; i < len; i++) {
      InstanceKlass* intf = interfaces->at(i);
      if (!has_id(intf)) {
        return;
      }
    }
  }

  if (k->is_hidden()) {
    return;
  }

  if (k->module()->is_patched()) {
    return;
  }

  ResourceMark rm;
  stream->print("%s id: %d", k->name()->as_C_string(), get_id(k));
  if (!is_builtin_loader) {
    InstanceKlass* super = k->java_super();
    assert(super != nullptr, "must be");
    stream->print(" super: %d", get_id(super));

    Array<InstanceKlass*>* interfaces = k->local_interfaces();
    int len = interfaces->length();
    if (len > 0) {
      stream->print(" interfaces:");
      for (int i = 0; i < len; i++) {
        InstanceKlass* intf = interfaces->at(i);
        stream->print(" %d", get_id(intf));
      }
    }

#ifdef _WINDOWS
    // "file:/C:/dir/foo.jar" -> "C:/dir/foo.jar"
    stream->print(" source: %s", cfs->source() + 6);
#else
    // "file:/dir/foo.jar" -> "/dir/foo.jar"
    stream->print(" source: %s", cfs->source() + 5);
#endif
  }

  stream->cr();
  stream->flush();
}

void ClassListWriter::delete_classlist() {
  if (_classlist_file != nullptr) {
    delete _classlist_file;
  }
}

class ClassListWriter::WriteResolveConstantsCLDClosure : public CLDClosure {
public:
  void do_cld(ClassLoaderData* cld) {
    for (Klass* klass = cld->klasses(); klass != nullptr; klass = klass->next_link()) {
      if (klass->is_instance_klass()) {
        InstanceKlass* ik = InstanceKlass::cast(klass);
        write_resolved_constants_for(ik);
      }
    }
  }
};

void ClassListWriter::write_resolved_constants() {
  if (!is_enabled()) {
    return;
  }
  MutexLocker lock(ClassLoaderDataGraph_lock);
  MutexLocker lock2(ClassListFile_lock, Mutex::_no_safepoint_check_flag);

  WriteResolveConstantsCLDClosure closure;
  ClassLoaderDataGraph::loaded_cld_do(&closure);
}

void ClassListWriter::write_resolved_constants_for(InstanceKlass* ik) {
  if (!SystemDictionaryShared::is_builtin_loader(ik->class_loader_data()) ||
      ik->is_hidden()) {
    return;
  }
  if (LambdaFormInvokers::may_be_regenerated_class(ik->name())) {
    return;
  }
  if (ik->name()->equals("jdk/internal/module/SystemModules$all")) {
    // This class is regenerated during JDK build process, so the classlist
    // may not match the version that's in the real jdk image.
    return;
  }

  if (!has_id(ik)) { // do not resolve CP for classes loaded by custom loaders.
    return;
  }

  ResourceMark rm;
  ConstantPool* cp = ik->constants();
  GrowableArray<bool> list(cp->length(), cp->length(), false);
  bool print = false;

  for (int cp_index = 1; cp_index < cp->length(); cp_index++) { // Index 0 is unused
    switch (cp->tag_at(cp_index).value()) {
    case JVM_CONSTANT_Class:
      {
        Klass* k = cp->resolved_klass_at(cp_index);
        if (k->is_instance_klass()) {
          list.at_put(cp_index, true);
          print = true;
        }
      }
      break;
    }
  }

  if (cp->cache() != nullptr) {
    Array<ResolvedFieldEntry>* field_entries = cp->cache()->resolved_field_entries();
    if (field_entries != nullptr) {
      for (int i = 0; i < field_entries->length(); i++) {
        ResolvedFieldEntry* rfe = field_entries->adr_at(i);
        if (rfe->is_resolved(Bytecodes::_getstatic) ||
            rfe->is_resolved(Bytecodes::_putstatic) ||
            rfe->is_resolved(Bytecodes::_getfield) ||
            rfe->is_resolved(Bytecodes::_putfield)) {
          list.at_put(rfe->constant_pool_index(), true);
          print = true;
        }
      }
    }
  }

  if (print) {
    outputStream* stream = _classlist_file;
    stream->print("@cp %s", ik->name()->as_C_string());
    for (int i = 0; i < list.length(); i++) {
      if (list.at(i)) {
        constantTag cp_tag = cp->tag_at(i).value();
        assert(cp_tag.value() == JVM_CONSTANT_Class ||
               cp_tag.value() == JVM_CONSTANT_Fieldref, "sanity");
        stream->print(" %d", i);
      }
    }
    stream->cr();
  }
}
