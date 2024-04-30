/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat, Inc. and/or its affiliates.
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

#ifndef SHARE_SERVICES_MEMMAPPRINTER_HPP
#define SHARE_SERVICES_MEMMAPPRINTER_HPP

#include "memory/allocation.hpp"
#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

#ifdef LINUX

class outputStream;
class CachedNMTInformation;

class MappingPrintInformation {
  const void* const _from;
  const void* const _to;
public:
  MappingPrintInformation(const void* from, const void* to) : _from(from), _to(to) {}
  const void* from() const { return _from; }
  const void* to() const { return _to; }
  // Will be called for each mapping before VM annotations are printed.
  virtual void print_OS_specific_details(outputStream* st) const {}
  // If mapping is backed by a file, the name of that file
  virtual const char* filename() const { return nullptr; }
};

class MappingPrintClosure {
  outputStream* const _out;
  const bool _human_readable;
  uintx _total_count;
  size_t _total_vsize;
  const CachedNMTInformation& _nmt_info;
public:
  MappingPrintClosure(outputStream* st, bool human_readable, const CachedNMTInformation& nmt_info);
  void do_it(const MappingPrintInformation* info); // returns false if timeout reached.
  uintx total_count() const { return _total_count; }
  size_t total_vsize() const { return _total_vsize; }
};

class MemMapPrinter : public AllStatic {
  static void pd_print_header(outputStream* st);
  static void print_header(outputStream* st);
  static void pd_iterate_all_mappings(MappingPrintClosure& closure);
public:
  static void mark_page_malloced(const void* p, MEMFLAGS f);
  static void print_all_mappings(outputStream* st, bool human_readable);
};

#endif // LINUX

#endif // SHARE_SERVICES_MEMMAPPRINTER_HPP
