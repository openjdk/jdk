/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, IBM Corporation. All rights reserved.
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

#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "shorthist_linux.hpp"
#include OS_HEADER(os)

// all memory sizes in KB
#define btokb(s) ( (s) / K)

void ShortHistoryData_pd::reset() {
  _vmsize = _vmrss = _vmhwm = _vmswap = _glibc_heap_allocated = _glibc_heap_retained = -1;
  _glibc_num_trims = _threads = _fdsize = -1;
}

void ShortHistoryData_pd::measure() {
  // Process memory info
  os::Linux::process_info_t mi;
  os::Linux::query_process_info(&mi);
  _vmsize = mi.vmsize;
  _vmrss = mi.vmrss;
  _vmhwm = mi.vmhwm;
  _vmswap = mi.vmswap;
  _threads = mi.threads;

  // Glibc memory info
#ifdef __GLIBC__
  bool might_have_wrapped = false;
  os::Linux::glibc_mallinfo mai;
  os::Linux::get_mallinfo(&mai, &might_have_wrapped);
  might_have_wrapped = NOT_LP64(false)
                       LP64_ONLY(might_have_wrapped && (size_t)_vmsize > (UINT_MAX / K));
  if (!might_have_wrapped) {
    _glibc_heap_allocated = checked_cast<ssize_t>(btokb(mai.uordblks + mai.hblkhd));
    _glibc_heap_retained = checked_cast<ssize_t>(btokb(mai.fordblks));
    _glibc_num_trims = checked_cast<int>(mai.num_trims);
  }
#endif // __GLIBC__
}

#define HEADER1 "|------------------- process -----------------||--------- glibc ---------|"
#define HEADER2 "     vsize       rss       hwm      swap   thr       live  retained  trim "
//               |.........|.........|.........|.........|.....||.........|.........|.....|

void ShortHistoryData_pd::print_header_1(outputStream* st) {
  st->print_raw(HEADER1);
}
void ShortHistoryData_pd::print_header_2(outputStream* st) {
  st->print(HEADER2);
}

void ShortHistoryData_pd::print_on(outputStream* st) const {
  st->print(" %9zd %9zd %9zd %9zd %5d ", _vmsize, _vmrss, _vmhwm, _vmswap, _threads);
  st->print(" %9zd %9zd %5d ", _glibc_heap_allocated, _glibc_heap_retained, _glibc_num_trims);
}
