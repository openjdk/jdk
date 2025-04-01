/*
 * Copyright (c) 2025 Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 */

#include "nmt/nMemoryLimitPrinter.hpp"
#include "utilities/vmError.hpp"

bool NMemoryLimitPrinter::total_limit_reached(size_t s, size_t so_far, const nMemlimit* limit, const NMemType type) {

  const char* typeStr = NMemLimitHandler::nmem_type_to_str(type);

  #define FORMATTED \
  "%sLimit: reached global limit (triggering allocation size: " PROPERFMT ", allocated so far: " PROPERFMT ", limit: " PROPERFMT ") ", \
  typeStr, PROPERFMTARGS(s), PROPERFMTARGS(so_far), PROPERFMTARGS(limit->sz)

  // If we hit the limit during error reporting, we print a short warning but otherwise ignore it.
  // We don't want to risk recursive assertion or torn hs-err logs.
  if (VMError::is_error_reported()) {
    // Print warning, but only the first n times to avoid flooding output.
    static int stopafter = 10;
    if (stopafter-- > 0) {
      log_warning(nmt)(FORMATTED);
    }
    return false;
  }

  if (limit->mode == NMemLimitMode::trigger_fatal) {
    fatal(FORMATTED);
  } else {
    log_warning(nmt)(FORMATTED);
  }
#undef FORMATTED

  return true;
}

bool NMemoryLimitPrinter::category_limit_reached(MemTag mem_tag, size_t s, size_t so_far, const nMemlimit* limit, const NMemType type) {

  const char* typeStr = NMemLimitHandler::nmem_type_to_str(type);

  #define FORMATTED \
    "%sLimit: reached category \"%s\" limit (triggering allocation size: " PROPERFMT ", allocated so far: " PROPERFMT ", limit: " PROPERFMT ") ", \
    typeStr, NMTUtil::tag_to_enum_name(mem_tag), PROPERFMTARGS(s), PROPERFMTARGS(so_far), PROPERFMTARGS(limit->sz)

    // If we hit the limit during error reporting, we print a short warning but otherwise ignore it.
    // We don't want to risk recursive assertion or torn hs-err logs.
    if (VMError::is_error_reported()) {
      // Print warning, but only the first n times to avoid flooding output.
      static int stopafter = 10;
      if (stopafter-- > 0) {
        log_warning(nmt)(FORMATTED);
      }
      return false;
    }

    if (limit->mode == NMemLimitMode::trigger_fatal) {
      fatal(FORMATTED);
    } else {
      log_warning(nmt)(FORMATTED);
    }
  #undef FORMATTED

    return true;
}

