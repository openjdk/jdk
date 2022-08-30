/*
 * Copyright (c) 2021 SAP SE. All rights reserved.
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "os_linux.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"
#include "trimCHeapDCmd.hpp"

#include <malloc.h>

void TrimCLibcHeapDCmd::execute(DCmdSource source, TRAPS) {
  if (os::can_trim_native_heap()) {
    os::size_change_t sc;
    if (os::trim_native_heap(&sc)) {
      if (sc.after == SIZE_MAX) {
        _output->print_cr("Done (no details available).");
      } else {
        const size_t scale_to_use = sc.before > (10 * M) ? M : K;
        const char scale_c = scale_to_use == K ? 'K' : 'M';
        const size_t m_before = sc.before / scale_to_use;
        const size_t m_after = sc.after / scale_to_use;
        _output->print_cr("Done. RSS+Swap reduction: " SIZE_FORMAT "%c->" SIZE_FORMAT "%c (" SSIZE_FORMAT "%c))",
                          m_before, scale_c, m_after, scale_c, (ssize_t)m_after - (ssize_t)m_before, scale_c);
      }
    }
  } else {
    _output->print_cr("Not available.");
  }
}
