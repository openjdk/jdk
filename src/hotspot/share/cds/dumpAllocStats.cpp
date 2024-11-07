/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/dumpAllocStats.hpp"
#include "logging/log.hpp"
#include "logging/logMessage.hpp"

void DumpAllocStats::print_stats(int ro_all, int rw_all) {
  // symbols
  _counts[RO][SymbolHashentryType] = _symbol_stats.hashentry_count;
  _bytes [RO][SymbolHashentryType] = _symbol_stats.hashentry_bytes;

  _counts[RO][SymbolBucketType] = _symbol_stats.bucket_count;
  _bytes [RO][SymbolBucketType] = _symbol_stats.bucket_bytes;

  // strings
  _counts[RO][StringHashentryType] = _string_stats.hashentry_count;
  _bytes [RO][StringHashentryType] = _string_stats.hashentry_bytes;

  _counts[RO][StringBucketType] = _string_stats.bucket_count;
  _bytes [RO][StringBucketType] = _string_stats.bucket_bytes;

  int all_ro_count = 0;
  int all_ro_bytes = 0;
  int all_rw_count = 0;
  int all_rw_bytes = 0;

// To make fmt_stats be a syntactic constant (for format warnings), use #define.
#define fmt_stats "%-20s: %8d %10d %5.1f | %8d %10d %5.1f | %8d %10d %5.1f"
  const char *sep = "--------------------+---------------------------+---------------------------+--------------------------";
  const char *hdr = "                        ro_cnt   ro_bytes     % |   rw_cnt   rw_bytes     % |  all_cnt  all_bytes     %";

  LogMessage(cds) msg;

  msg.debug("Detailed metadata info (excluding heap region):");
  msg.debug("%s", hdr);
  msg.debug("%s", sep);
  for (int type = 0; type < int(_number_of_types); type ++) {
    const char *name = type_name((Type)type);
    int ro_count = _counts[RO][type];
    int ro_bytes = _bytes [RO][type];
    int rw_count = _counts[RW][type];
    int rw_bytes = _bytes [RW][type];
    int count = ro_count + rw_count;
    int bytes = ro_bytes + rw_bytes;

    double ro_perc = percent_of(ro_bytes, ro_all);
    double rw_perc = percent_of(rw_bytes, rw_all);
    double perc    = percent_of(bytes, ro_all + rw_all);

    msg.debug(fmt_stats, name,
                         ro_count, ro_bytes, ro_perc,
                         rw_count, rw_bytes, rw_perc,
                         count, bytes, perc);

    all_ro_count += ro_count;
    all_ro_bytes += ro_bytes;
    all_rw_count += rw_count;
    all_rw_bytes += rw_bytes;
  }

  int all_count = all_ro_count + all_rw_count;
  int all_bytes = all_ro_bytes + all_rw_bytes;

  double all_ro_perc = percent_of(all_ro_bytes, ro_all);
  double all_rw_perc = percent_of(all_rw_bytes, rw_all);
  double all_perc    = percent_of(all_bytes, ro_all + rw_all);

  msg.debug("%s", sep);
  msg.debug(fmt_stats, "Total",
                       all_ro_count, all_ro_bytes, all_ro_perc,
                       all_rw_count, all_rw_bytes, all_rw_perc,
                       all_count, all_bytes, all_perc);

  msg.flush();

  assert(all_ro_bytes == ro_all && all_rw_bytes == rw_all,
         "everything should have been counted (used/counted: ro %d/%d, rw %d/%d",
         ro_all, all_ro_bytes, rw_all, all_rw_bytes);

#undef fmt_stats

  msg.info("Class  CP entries = %6d, archived = %6d (%5.1f%%), reverted = %6d",
           _num_klass_cp_entries, _num_klass_cp_entries_archived,
           percent_of(_num_klass_cp_entries_archived, _num_klass_cp_entries),
           _num_klass_cp_entries_reverted);
  msg.info("Field  CP entries = %6d, archived = %6d (%5.1f%%), reverted = %6d",
           _num_field_cp_entries, _num_field_cp_entries_archived,
           percent_of(_num_field_cp_entries_archived, _num_field_cp_entries),
           _num_field_cp_entries_reverted);
  msg.info("Method CP entries = %6d, archived = %6d (%5.1f%%), reverted = %6d",
           _num_method_cp_entries, _num_method_cp_entries_archived,
           percent_of(_num_method_cp_entries_archived, _num_method_cp_entries),
           _num_method_cp_entries_reverted);
}
