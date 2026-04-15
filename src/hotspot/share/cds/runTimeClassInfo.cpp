/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotCompressedPointers.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/dumpTimeClassInfo.hpp"
#include "cds/runTimeClassInfo.hpp"
#include "classfile/systemDictionaryShared.hpp"

void RunTimeClassInfo::init(DumpTimeClassInfo& info) {
  InstanceKlass* k = info._klass;
  _klass = AOTCompressedPointers::encode_not_null(k);

  if (!SystemDictionaryShared::is_builtin(k)) {
    CrcInfo* c = crc();
    c->_clsfile_size = info._clsfile_size;
    c->_clsfile_crc32 = info._clsfile_crc32;
  }
  _num_verifier_constraints = info.num_verifier_constraints();
  _num_loader_constraints   = info.num_loader_constraints();
  int i;

  if (CDSConfig::is_preserving_verification_constraints()) {
    // The production run doesn't need the verifier constraints, as we can guarantee that all classes checked by
    // the verifier during AOT training/assembly phases cannot be replaced in the production run.
    _num_verifier_constraints = 0;
  }
  if (_num_verifier_constraints > 0) {
    RTVerifierConstraint* vf_constraints = verifier_constraints();
    char* flags = verifier_constraint_flags();
    for (i = 0; i < _num_verifier_constraints; i++) {
      vf_constraints[i]._name = AOTCompressedPointers::encode_not_null(info._verifier_constraints->at(i).name());
      vf_constraints[i]._from_name = AOTCompressedPointers::encode(info._verifier_constraints->at(i).from_name());
    }
    for (i = 0; i < _num_verifier_constraints; i++) {
      flags[i] = info._verifier_constraint_flags->at(i);
    }
  }

  if (_num_loader_constraints > 0) {
    RTLoaderConstraint* ld_constraints = loader_constraints();
    for (i = 0; i < _num_loader_constraints; i++) {
      ld_constraints[i]._name = AOTCompressedPointers::encode_not_null(info._loader_constraints->at(i).name());
      ld_constraints[i]._loader_type1 = info._loader_constraints->at(i).loader_type1();
      ld_constraints[i]._loader_type2 = info._loader_constraints->at(i).loader_type2();
    }
  }

  if (k->is_hidden() && info.nest_host() != nullptr) {
    _nest_host = AOTCompressedPointers::encode_not_null(info.nest_host());
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
  if (AOTMetaspace::in_aot_cache(this)) {
    // <this> is inside a mmaped CDS archive.
    return AOTCompressedPointers::decode_not_null<InstanceKlass*>(_klass);
  } else {
    // <this> is a temporary copy of a RunTimeClassInfo that's being initialized
    // by the ArchiveBuilder.
    size_t byte_offset = AOTCompressedPointers::get_byte_offset(_klass);
    return ArchiveBuilder::current()->offset_to_buffered<InstanceKlass*>(byte_offset);
  }
}

size_t RunTimeClassInfo::crc_size(InstanceKlass* klass) {
  if (!SystemDictionaryShared::is_builtin(klass)) {
    return sizeof(CrcInfo);
  } else {
    return 0;
  }
}
