/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "services/mallocLimit.hpp"
#include "services/nmtCommon.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

MallocLimitInfo::MallocLimitInfo() { reset(); }

void MallocLimitInfo::reset() {
  _total_limit = 0;
  for (int i = 0; i < mt_number_of_types; i ++) {
    _limits_per_category[i] = 0;
  }
  _fake_oom = false;
}

void MallocLimitInfo::print(outputStream* st) const {
  if (_total_limit > 0) {
    st->print_cr("MallocLimit: total limit: " SIZE_FORMAT "%s",
                 byte_size_in_proper_unit(_total_limit),
                 proper_unit_for_byte_size(_total_limit));
  } else {
    for (int i = 0; i < mt_number_of_types; i ++) {
      size_t catlim = _limits_per_category[i];
      if (catlim > 0) {
        st->print_cr("MallocLimit: category \"%s\" limit: " SIZE_FORMAT "%s",
                     NMTUtil::flag_to_name((MEMFLAGS)i),
                     byte_size_in_proper_unit(catlim),
                     proper_unit_for_byte_size(catlim));
      }
    }
  }
  if (_fake_oom) {
    st->print_raw("MallocLimit: fake-oom mode");
  }
}
