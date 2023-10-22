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
#include "services/memMapPrinter.hpp"
#include "utilities/globalDefinitions.hpp"

struct proc_maps_info_t {
  unsigned long long from = 0;
  unsigned long long to = 0;
  char prot[20 + 1];
  char offset[20 + 1];
  char dev[20 + 1];
  char inode[20 + 1];
  char filename[1024 + 1];

  bool scan_proc_maps_line(const char* line) {
    prot[0] = offset[0] = dev[0] = inode[0] = filename[0] = '\0';
    const int items_read = ::sscanf(line, "%llx-%llx %20s %20s %20s %20s %1024s",
        &from, &to, prot, offset, dev, inode, filename);
    if (items_read < 2) {
      return false;
    }
    return items_read >= 2; // need at least from and to
  }
};

class LinuxMappingPrintInformation : public MappingPrintInformation {
  const proc_maps_info_t _info;
public:

  LinuxMappingPrintInformation(const void* from, const void* to, const proc_maps_info_t* info) :
    MappingPrintInformation(from, to), _info(*info) {}

  void print_details_1(outputStream* st) const override {
    st->print("%s %s ", _info.prot, _info.offset);
  }
  void print_details_2(outputStream* st) const override {
    st->print_raw(_info.filename);
  }
};

void MemMapPrinter::pd_print_header(outputStream* st) {
  st->print(
#ifdef _LP64
      "from                 to                 "
#else
      "from         to         "
#endif
  );
  st->print_cr("size          prot offset  VM info");
}

void MemMapPrinter::pd_iterate_all_mappings(MappingPrintClosure& closure) {
  FILE* f = os::fopen("/proc/self/maps", "r");
  if (f != nullptr) {
    char line[1024];
    while(fgets(line, sizeof(line), f) == line) {
      line[sizeof(line) - 1] = '\0';
      proc_maps_info_t info;
      if (info.scan_proc_maps_line(line)) {
        LinuxMappingPrintInformation mapinfo((void*)info.from, (void*)info.to, &info);
        closure.do_it(&mapinfo);
      }
    }
    ::fclose(f);
  }
}
