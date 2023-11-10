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

#include "precompiled.hpp"

#include "runtime/os.hpp"
#include "nmt/memMapPrinter.hpp"
#include "utilities/globalDefinitions.hpp"
#include <limits.h>

struct ProcMapsInfo {
  void* from = 0;
  void* to = 0;
  char prot[20 + 1];
  char offset[20 + 1];
  char dev[20 + 1];
  char inode[20 + 1];
  char filename[1024 + 1];

  bool scan_proc_maps_line(const char* line) {
    prot[0] = offset[0] = dev[0] = inode[0] = filename[0] = '\0';
    const int items_read = ::sscanf(line, "%p-%p %20s %20s %20s %20s %1024s",
        &from, &to, prot, offset, dev, inode, filename);
    return items_read >= 2; // need at least from and to
  }
};

class LinuxMappingPrintInformation : public MappingPrintInformation {
  const ProcMapsInfo _info;
public:

  LinuxMappingPrintInformation(const void* from, const void* to, const ProcMapsInfo* info) :
    MappingPrintInformation(from, to), _info(*info) {}

  void print_OS_specific_details(outputStream* st) const override {
    st->print("%s %s ", _info.prot, _info.offset);
  }

  const char* filename() const override { return _info.filename; }
};

void MemMapPrinter::pd_print_header(outputStream* st) {
  st->print_cr("size          prot offset  What");
}

void MemMapPrinter::pd_iterate_all_mappings(MappingPrintClosure& closure) {
  FILE* f = os::fopen("/proc/self/maps", "r");
  if (f == nullptr) {
    return;
  }
  constexpr size_t linesize = sizeof(ProcMapsInfo);
  char line[linesize];
  while (fgets(line, sizeof(line), f) == line) {
    line[sizeof(line) - 1] = '\0';
    ProcMapsInfo info;
    if (info.scan_proc_maps_line(line)) {
      LinuxMappingPrintInformation mapinfo(info.from, info.to, &info);
      closure.do_it(&mapinfo);
    }
  }
  ::fclose(f);
}
