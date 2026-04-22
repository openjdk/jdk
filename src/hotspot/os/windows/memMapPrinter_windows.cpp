/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat, Inc. and/or its affiliates.
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

#include "nmt/memMapPrinter.hpp"
#include "os_windows.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/ostream.hpp"

#include <limits.h>
#include <winnt.h>
#include <memoryapi.h>
#include <psapi.h>

/* maximum number of mapping records returned */
static const int MAX_REGIONS_RETURNED = 1000000;

class MappingInfo {
public:
  stringStream _ap_buffer;
  stringStream _state_buffer;
  stringStream _protect_buffer;
  stringStream _type_buffer;
  char _file_name[MAX_PATH];

  MappingInfo() {}

  void process(MEMORY_BASIC_INFORMATION& mem_info) {
    _ap_buffer.reset();
    _state_buffer.reset();
    _protect_buffer.reset();
    _type_buffer.reset();
    get_protect_string(_ap_buffer, mem_info.AllocationProtect);
    get_state_string(_state_buffer, mem_info);
    get_protect_string(_protect_buffer,  mem_info.Protect);
    get_type_string(_type_buffer, mem_info);
    _file_name[0] = 0;
    if (mem_info.Type == MEM_IMAGE) {
      HMODULE hModule = 0;
      if (GetModuleHandleEx(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS, static_cast<LPCSTR>(mem_info.AllocationBase), &hModule)) {
        GetModuleFileName(hModule, _file_name, sizeof(_file_name));
      }
    }
  }

  void get_protect_string(outputStream& out, DWORD prot) {
    const char read_c = prot & (PAGE_READONLY | PAGE_READWRITE | PAGE_EXECUTE_READ | PAGE_EXECUTE_READWRITE | PAGE_WRITECOPY | PAGE_EXECUTE_WRITECOPY) ? 'r' : '-';
    const char write_c = prot & (PAGE_READWRITE | PAGE_WRITECOPY | PAGE_EXECUTE_READWRITE | PAGE_EXECUTE_WRITECOPY) ? 'w' : '-';
    const char execute_c = prot & (PAGE_EXECUTE | PAGE_EXECUTE_READ | PAGE_EXECUTE_READWRITE | PAGE_EXECUTE_WRITECOPY) ? 'x' : '-';
    out.print("%c%c%c", read_c, write_c, execute_c);
    if (prot & (PAGE_WRITECOPY | PAGE_EXECUTE_WRITECOPY)) {
      out.put('c');
    }
    if (prot & PAGE_GUARD) {
      out.put('g');
    }
    if (prot & PAGE_NOCACHE) {
      out.put('n');
    }
    if (prot & PAGE_WRITECOMBINE) {
      out.put('W');
    }
    const DWORD bits = PAGE_NOACCESS | PAGE_READONLY | PAGE_READWRITE | PAGE_EXECUTE_READ | PAGE_EXECUTE_READWRITE
                        | PAGE_WRITECOPY | PAGE_EXECUTE_WRITECOPY | PAGE_EXECUTE
                        | PAGE_GUARD | PAGE_NOCACHE | PAGE_WRITECOMBINE;
    if ((prot & bits) != prot) {
      out.print_cr("Unknown Windows memory protection value: 0x%x unknown bits: 0x%x", prot, prot & ~bits);
      assert(false, "Unknown Windows memory protection value: 0x%x unknown bits: 0x%x", prot, prot & ~bits);
    }
  }

  void get_state_string(outputStream& out, MEMORY_BASIC_INFORMATION& mem_info) {
    if (mem_info.State == MEM_COMMIT) {
      out.put('c');
    } else if (mem_info.State == MEM_FREE) {
      out.put('f');
    } else if (mem_info.State == MEM_RESERVE) {
      out.put('r');
    } else {
      out.print_cr("Unknown Windows memory state value: 0x%x", mem_info.State);
      assert(false, "Unknown Windows memory state value: 0x%x", mem_info.State);
    }
  }

  void get_type_string(outputStream& out, MEMORY_BASIC_INFORMATION& mem_info) {
    if (mem_info.Type == MEM_IMAGE) {
      out.print("img");
    } else if (mem_info.Type == MEM_MAPPED) {
      out.print("map");
    } else if (mem_info.Type == MEM_PRIVATE) {
      out.print("pvt");
    } else if (mem_info.Type == 0 && mem_info.State == MEM_FREE) {
      out.print("---");
    } else {
      out.print_cr("Unknown Windows memory type 0x%x", mem_info.Type);
      assert(false, "Unknown Windows memory type 0x%x", mem_info.Type);
    }
  }
};

class MappingInfoSummary {
  unsigned _num_mappings;
  size_t _total_region_size;  // combined resident set size
  size_t _total_committed;    // combined committed size
  class WinOsInfo : public os::win32 {
    public:
      static void printOsInfo(outputStream* st) {
        st->print("OS:");
        os::win32::print_windows_version(st);
        os::win32::print_uptime_info(st);
        VM_Version::print_platform_virtualization_info(st);
        os::print_memory_info(st);
      }
  };
public:
  MappingInfoSummary() : _num_mappings(0),  _total_region_size(0),
                      _total_committed(0) {}

  void add_mapping(const MEMORY_BASIC_INFORMATION& mem_info, const MappingInfo& mapping_info) {
    if (mem_info.State != MEM_FREE) {
      _num_mappings++;
      _total_region_size += mem_info.RegionSize;
      _total_committed += mem_info.State == MEM_COMMIT ? mem_info.RegionSize : 0;
    }
  }

  void print_on(const MappingPrintSession& session) const {
    outputStream* st = session.out();
    WinOsInfo::printOsInfo(st);
    st->print_cr("current process reserved memory: " PROPERFMT, PROPERFMTARGS(_total_region_size));
    st->print_cr("current process committed memory: " PROPERFMT, PROPERFMTARGS(_total_committed));
    st->print_cr("current process region count: " PROPERFMT, PROPERFMTARGS(_num_mappings));
  }
};

class MappingInfoPrinter {
  const MappingPrintSession& _session;
public:
  MappingInfoPrinter(const MappingPrintSession& session) :
    _session(session)
  {}

  void print_single_mapping(const MEMORY_BASIC_INFORMATION& mem_info, const MappingInfo& mapping_info) const {
    outputStream* st = _session.out();
#define INDENT_BY(n)          \
  if (st->fill_to(n) == 0) {  \
    st->print(" ");           \
  }
    st->print(PTR_FORMAT "-" PTR_FORMAT, mem_info.BaseAddress, static_cast<const char*>(mem_info.BaseAddress) + mem_info.RegionSize);
    INDENT_BY(38);
    st->print("%12zu", mem_info.RegionSize);
    INDENT_BY(51);
    st->print("%s", mapping_info._protect_buffer.base());
    INDENT_BY(57);
    st->print("%s-%s", mapping_info._state_buffer.base(), mapping_info._type_buffer.base());
    INDENT_BY(63);
    st->print("%#11llx", reinterpret_cast<const unsigned long long>(mem_info.BaseAddress) - reinterpret_cast<const unsigned long long>(mem_info.AllocationBase));
    INDENT_BY(72);
    if (_session.print_nmt_info_for_region(mem_info.BaseAddress, static_cast<const char*>(mem_info.BaseAddress) + mem_info.RegionSize)) {
      st->print(" ");
    }
    st->print_raw(mapping_info._file_name);
  #undef INDENT_BY
    st->cr();
  }

  void print_legend() const {
    outputStream* st = _session.out();
    st->print_cr("from, to, vsize: address range and size");
    st->print_cr("prot:    protection:");
    st->print_cr("             rwx: read / write / execute");
    st->print_cr("             c: copy on write");
    st->print_cr("             g: guard");
    st->print_cr("             n: no cache");
    st->print_cr("             W: write combine");
    st->print_cr("state:   region state and type:");
    st->print_cr("             state: committed / reserved");
    st->print_cr("             type: image / mapped / private");
    st->print_cr("offset:  offset from start of allocation block");
    st->print_cr("vminfo:  VM information (requires NMT)");
    st->print_cr("file:    file mapped, if mapping is not anonymous");
    {
      StreamIndentor si(st, 16);
      _session.print_nmt_flag_legend();
    }
  }

  void print_header() const {
    outputStream* st = _session.out();
    //            0         1         2         3         4         5         6         7         8         9         0         1         2         3
    //            01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
    //            0x00007ffb24565000-0x00007ffb24a7e000      5345280 r--   c-img   0x1155000 C:\work\jdk\build\fastdebug\jdk\bin\server\jvm.dll
    st->print_cr("from               to                        vsize prot  state      offset vminfo/file");
    st->print_cr("===========================================================================================");
  }
};

void MemMapPrinter::pd_print_all_mappings(const MappingPrintSession& session) {

  HANDLE hProcess = GetCurrentProcess();

  MappingInfoPrinter printer(session);
  MappingInfoSummary summary;

  outputStream* const st = session.out();

  printer.print_legend();
  st->cr();
  printer.print_header();

  MEMORY_BASIC_INFORMATION mem_info;
  MappingInfo mapping_info;

  int region_count = 0;
  ::memset(&mem_info, 0, sizeof(mem_info));
  for (char* ptr = 0; VirtualQueryEx(hProcess, ptr, &mem_info, sizeof(mem_info)) == sizeof(mem_info); ) {
    assert(mem_info.RegionSize > 0, "RegionSize is not greater than zero");
    if (++region_count > MAX_REGIONS_RETURNED) {
      st->print_cr("limit of %d regions reached (results inaccurate)", region_count);
      break;
    }
    mapping_info.process(mem_info);
    if (mem_info.State != MEM_FREE) {
      printer.print_single_mapping(mem_info, mapping_info);
      summary.add_mapping(mem_info, mapping_info);
    }
    ptr += mem_info.RegionSize;
    ::memset(&mem_info, 0, sizeof(mem_info));
  }
  st->cr();
  summary.print_on(session);
  st->cr();
}
