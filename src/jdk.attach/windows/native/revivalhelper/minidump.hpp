/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

#ifndef MINIDUMP_H
#define MINIDUMP_H

#include "revival.hpp"

#include <direct.h>
#include <fileapi.h>
#include <intrin.h>
#include <imagehlp.h>
#include <io.h>
#include <memoryapi.h>
#include <minidumpapiset.h>
#include <processthreadsapi.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <shlwapi.h>
#include <sysinfoapi.h>
#include <windows.h>
#include <winternl.h>
#include <sys/types.h>

/**
 * Windows MiniDump.
 * Operations as required by the process revival mechanism, to enable jcmd to operate on a MiniDump.
 */
class MiniDump {
  public:
    MiniDump(const char* filename, const char* libdirs);
    ~MiniDump();

    bool is_valid() { return fd >= 0; }
    void close();

    uint64_t get_teb(); // Thread Environment Block
    uint64_t get_peb(); // Process Enviornment Block (from TEB).

    MINIDUMP_DIRECTORY* find_stream(int stream);
    Segment* readSegment(MINIDUMP_MEMORY_DESCRIPTOR64 *d, RVA64* currentRVA, boolean skipLibraries);

    std::list<Segment> get_library_mappings();
    Segment* get_library_mapping(const char* filename);
    uint64_t get_library_mapping_after(uint64_t address);

    // Write the list of memory mappings in the core, to be used in the revived process.
    void write_mem_mappings(int mappings_fd, const char* exec_name);

    void prepare_memory_ranges();
    RVA64 getBaseRVA() { return BaseRVA; }

    uint64_t file_offset_for_vaddr(uint64_t addr);
    char* read_string_at_address(uint64_t addr);
    uint64_t read_pointer_at_address(uint64_t addr);

    void set_jvm_data(Segment* data) {
      this->jvm_data_seg = data;
    }

  private:
    const char* filename;
    const char* libdirs;
    int fd;
    _MINIDUMP_HEADER hdr;
    std::list<Segment> libs;

    uint64_t resolve_teb();
    uint64_t teb;
    void read_sharedlibs();
    Segment* readSegment0(MINIDUMP_MEMORY_DESCRIPTOR64 *d, RVA64* currentRVA);
    ULONG64 NumberOfMemoryRanges;
    RVA64 BaseRVA;
    int rangesRead;
    Segment* jvm_data_seg;
};

#endif MINIDUMP_H /* MINIDUMP_H */
