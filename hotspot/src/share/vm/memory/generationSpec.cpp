/*
 * Copyright (c) 2001, 2005, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_generationSpec.cpp.incl"

Generation* GenerationSpec::init(ReservedSpace rs, int level,
                                 GenRemSet* remset) {
  switch (name()) {
    case Generation::DefNew:
      return new DefNewGeneration(rs, init_size(), level);

    case Generation::MarkSweepCompact:
      return new TenuredGeneration(rs, init_size(), level, remset);

#ifndef SERIALGC
    case Generation::ParNew:
      return new ParNewGeneration(rs, init_size(), level);

    case Generation::ASParNew:
      return new ASParNewGeneration(rs,
                                    init_size(),
                                    init_size() /* min size */,
                                    level);

    case Generation::ConcurrentMarkSweep: {
      assert(UseConcMarkSweepGC, "UseConcMarkSweepGC should be set");
      CardTableRS* ctrs = remset->as_CardTableRS();
      if (ctrs == NULL) {
        vm_exit_during_initialization("Rem set incompatibility.");
      }
      // Otherwise
      // The constructor creates the CMSCollector if needed,
      // else registers with an existing CMSCollector

      ConcurrentMarkSweepGeneration* g = NULL;
      g = new ConcurrentMarkSweepGeneration(rs,
                 init_size(), level, ctrs, UseCMSAdaptiveFreeLists,
                 (FreeBlockDictionary::DictionaryChoice)CMSDictionaryChoice);

      g->initialize_performance_counters();

      return g;
    }

    case Generation::ASConcurrentMarkSweep: {
      assert(UseConcMarkSweepGC, "UseConcMarkSweepGC should be set");
      CardTableRS* ctrs = remset->as_CardTableRS();
      if (ctrs == NULL) {
        vm_exit_during_initialization("Rem set incompatibility.");
      }
      // Otherwise
      // The constructor creates the CMSCollector if needed,
      // else registers with an existing CMSCollector

      ASConcurrentMarkSweepGeneration* g = NULL;
      g = new ASConcurrentMarkSweepGeneration(rs,
                 init_size(), level, ctrs, UseCMSAdaptiveFreeLists,
                 (FreeBlockDictionary::DictionaryChoice)CMSDictionaryChoice);

      g->initialize_performance_counters();

      return g;
    }
#endif // SERIALGC

    default:
      guarantee(false, "unrecognized GenerationName");
      return NULL;
  }
}


PermanentGenerationSpec::PermanentGenerationSpec(PermGen::Name name,
                      size_t init_size, size_t max_size,
                      size_t read_only_size, size_t read_write_size,
                      size_t misc_data_size, size_t misc_code_size) {
  _name = name;
  _init_size = init_size;

  if (UseSharedSpaces || DumpSharedSpaces) {
    _enable_shared_spaces = true;
    if (UseSharedSpaces) {
      // Override shared space sizes from those in the file.
      FileMapInfo* mapinfo = FileMapInfo::current_info();
      _read_only_size = mapinfo->space_capacity(CompactingPermGenGen::ro);
      _read_write_size = mapinfo->space_capacity(CompactingPermGenGen::rw);
      _misc_data_size = mapinfo->space_capacity(CompactingPermGenGen::md);
      _misc_code_size = mapinfo->space_capacity(CompactingPermGenGen::mc);
    } else {
      _read_only_size = read_only_size;
      _read_write_size = read_write_size;
      _misc_data_size = misc_data_size;
      _misc_code_size = misc_code_size;
    }
  } else {
    _enable_shared_spaces = false;
    _read_only_size = 0;
    _read_write_size = 0;
    _misc_data_size = 0;
    _misc_code_size = 0;
  }

  _max_size = max_size;
}


PermGen* PermanentGenerationSpec::init(ReservedSpace rs,
                                       size_t init_size,
                                       GenRemSet *remset) {

  // Break the reserved spaces into pieces for the permanent space
  // and the shared spaces.
  ReservedSpace perm_rs = rs.first_part(_max_size, UseSharedSpaces,
                                        UseSharedSpaces);
  ReservedSpace shared_rs = rs.last_part(_max_size);

  if (enable_shared_spaces()) {
    if (!perm_rs.is_reserved() ||
        perm_rs.base() + perm_rs.size() != shared_rs.base()) {
      FileMapInfo* mapinfo = FileMapInfo::current_info();
      mapinfo->fail_continue("Sharing disabled - unable to "
                                 "reserve address space.");
      shared_rs.release();
      disable_sharing();
    }
  }

  switch (name()) {
    case PermGen::MarkSweepCompact:
      return new CompactingPermGen(perm_rs, shared_rs, init_size, remset, this);

#ifndef SERIALGC
    case PermGen::MarkSweep:
      guarantee(false, "NYI");
      return NULL;

    case PermGen::ConcurrentMarkSweep: {
      assert(UseConcMarkSweepGC, "UseConcMarkSweepGC should be set");
      CardTableRS* ctrs = remset->as_CardTableRS();
      if (ctrs == NULL) {
        vm_exit_during_initialization("RemSet/generation incompatibility.");
      }
      // XXXPERM
      return new CMSPermGen(perm_rs, init_size, ctrs,
                   (FreeBlockDictionary::DictionaryChoice)CMSDictionaryChoice);
    }
#endif // SERIALGC
    default:
      guarantee(false, "unrecognized GenerationName");
      return NULL;
  }
}


// Alignment
void PermanentGenerationSpec::align(size_t alignment) {
  _init_size       = align_size_up(_init_size,       alignment);
  _max_size        = align_size_up(_max_size,        alignment);
  _read_only_size  = align_size_up(_read_only_size,  alignment);
  _read_write_size = align_size_up(_read_write_size, alignment);
  _misc_data_size  = align_size_up(_misc_data_size,  alignment);
  _misc_code_size  = align_size_up(_misc_code_size,  alignment);

  assert(enable_shared_spaces() || (_read_only_size + _read_write_size == 0),
         "Shared space when disabled?");
}
