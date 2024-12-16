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
#include "cds/archiveBuilder.hpp"
#include "cds/dumpTimeClassInfo.hpp"
#include "cds/runTimeClassInfo.hpp"
#include "classfile/systemDictionaryShared.hpp"

void RunTimeClassInfo::init(DumpTimeClassInfo& info) {
  ArchiveBuilder* builder = ArchiveBuilder::current();
  InstanceKlass* k = info._klass;
  _klass_offset = builder->any_to_offset_u4(k);

  if (!SystemDictionaryShared::is_builtin(k)) {
    CrcInfo* c = crc();
    c->_clsfile_size = info._clsfile_size;
    c->_clsfile_crc32 = info._clsfile_crc32;
  }
  _num_verifier_constraints = info.num_verifier_constraints();
  _num_loader_constraints   = info.num_loader_constraints();
  int i;
  if (_num_verifier_constraints > 0) {
    RTVerifierConstraint* vf_constraints = verifier_constraints();
    char* flags = verifier_constraint_flags();
    for (i = 0; i < _num_verifier_constraints; i++) {
      vf_constraints[i]._name      = builder->any_to_offset_u4(info._verifier_constraints->at(i).name());
      vf_constraints[i]._from_name = builder->any_to_offset_u4(info._verifier_constraints->at(i).from_name());
    }
    for (i = 0; i < _num_verifier_constraints; i++) {
      flags[i] = info._verifier_constraint_flags->at(i);
    }
  }

  if (_num_loader_constraints > 0) {
    RTLoaderConstraint* ld_constraints = loader_constraints();
    for (i = 0; i < _num_loader_constraints; i++) {
      ld_constraints[i]._name = builder->any_to_offset_u4(info._loader_constraints->at(i).name());
      ld_constraints[i]._loader_type1 = info._loader_constraints->at(i).loader_type1();
      ld_constraints[i]._loader_type2 = info._loader_constraints->at(i).loader_type2();
    }
  }

  if (k->is_hidden() && info.nest_host() != nullptr) {
    _nest_host_offset = builder->any_to_offset_u4(info.nest_host());
  }
  if (k->has_archived_enum_objs()) {
    int num = info.num_enum_klass_static_fields();
    set_num_enum_klass_static_fields(num);
    for (int i = 0; i < num; i++) {
      int root_index = info.enum_klass_static_field(i);
      set_enum_klass_static_field_root_index_at(i, root_index);
    }
  }
}

InstanceKlass* RunTimeClassInfo::klass() const {
  if (MetaspaceShared::is_in_shared_metaspace(this)) {
    // <this> is inside a mmaped CDS archive.
    return ArchiveUtils::offset_to_archived_address<InstanceKlass*>(_klass_offset);
  } else {
    // <this> is a temporary copy of a RunTimeClassInfo that's being initialized
    // by the ArchiveBuilder.
    return ArchiveBuilder::current()->offset_to_buffered<InstanceKlass*>(_klass_offset);
  }
}

size_t RunTimeClassInfo::crc_size(InstanceKlass* klass) {
  if (!SystemDictionaryShared::is_builtin(klass)) {
    return sizeof(CrcInfo);
  } else {
    return 0;
  }
}
