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

#include <direct.h>
#include <intrin.h>
#include <io.h>
#include <memoryapi.h>
#include <process.h>
#include <processthreadsapi.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sysinfoapi.h>
#include <windows.h>

#include <minidumpapiset.h>

#include <sys/types.h>

#include <fileapi.h>
#include <imagehlp.h>
#include <shlwapi.h>
#include <winternl.h>

#include "revival.hpp"
#include "minidump.hpp"
#include "pefile.hpp"

DWORD stdProt = PAGE_EXECUTE_READWRITE;
uint64_t vaddr_align;
uint64_t heap_test;
char* editbin = nullptr;
HANDLE hProc;

typedef PVOID (*VirtualAlloc2Fn)(HANDLE, PVOID, SIZE_T, ULONG, ULONG, MEM_EXTENDED_PARAMETER*, ULONG);
typedef PVOID (*MapViewOfFile3Fn)(HANDLE, HANDLE, PVOID, ULONG64, SIZE_T, ULONG, ULONG, MEM_EXTENDED_PARAMETER*, ULONG);

static VirtualAlloc2Fn      pVirtualAlloc2;
static MapViewOfFile3Fn     pMapViewOfFile3;

static void* lookup_kernelbase_library() {
    const char* const name = "KernelBase";
    void* const handle = LoadLibrary(name);
    if (handle == nullptr) {
        error("LoadLibrary failed");
    }
    return handle;
}

static void* lookup_kernelbase_symbol(const char* name) {
    static void* const handle = lookup_kernelbase_library();
    if (handle == nullptr) {
        return nullptr;
    }
    void* ret = ::GetProcAddress((HMODULE) handle, name);
    if (ret == nullptr) {
        error("Failed to lookup kernelbase symbol: %s", name);
    }
    return ret;
}

template <typename Fn>
static void install_kernelbase_symbol(Fn*& fn, const char* name) {
    fn = reinterpret_cast<Fn*>(lookup_kernelbase_symbol(name));
}

template <typename Fn>
static void install_kernelbase_1803_symbol_or_exit(Fn*& fn, const char* name) {
    install_kernelbase_symbol(fn, name);
    if (fn == nullptr) {
        error("Failed to find 1803 symbol: %s", name);
    }
}

char* basename_pd(char* s) {
    for (char* p = s + strlen(s); p != s; p--) {
        if (*p == '\\') {
            p++;
            return p;
        }
    }
    return s;
}

void normalize_path_pd(char* s) {
    for (char* p = s; *p != '\0'; p++) {
        if (*p == '/') *p = '\\';
    }
}

bool dir_exists_pd(const char* dirname) {
    DWORD attr = GetFileAttributes(dirname);
    return attr != INVALID_FILE_ATTRIBUTES && (attr & FILE_ATTRIBUTE_DIRECTORY);
}

bool dir_isempty_pd(const char* dirname) {
    return PathIsDirectoryEmptyA(dirname);
}

bool file_exists_pd(const char* filename) {
    return GetFileAttributes(filename) != INVALID_FILE_ATTRIBUTES;
}

bool file_canread_pd(const char* filename) {
    if (!file_exists_pd(filename)) {
        return false;
    }
    HANDLE hFile = CreateFile(filename, GENERIC_READ, FILE_SHARE_READ, NULL,
                              OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL | FILE_FLAG_BACKUP_SEMANTICS, NULL);
    if (hFile == INVALID_HANDLE_VALUE) {
        return false;
    } else {
        CloseHandle(hFile);
        return true;
    }
}

bool file_exists_indir_pd(const char* dirname, const char* filename) {
    char path[BUFLEN];
    snprintf(path, BUFLEN - 1, "%s" FILE_SEPARATOR "%s", dirname, filename);
    return file_exists_pd(path);
}

uint64_t vaddr_alignment_pd() {
    return vaddr_align;
}

unsigned long long max_user_vaddr_pd() {
    return 0x7FFFFFFFFFFF;
}

void printMemBasicInfo(MEMORY_BASIC_INFORMATION meminfo) {
    uint64_t end = (uint64_t) meminfo.BaseAddress + meminfo.RegionSize;
    fprintf(stderr, "Meminfo: AllocationBase: 0x%016llx   BaseAddress: 0x%016llx - 0x%016llx  "
            "RegionSize: 0x%llx  AllocProt: 0x%lx Prot: 0x%lx, State: 0x%lx\n",
            (uint64_t) meminfo.AllocationBase, (uint64_t) meminfo.BaseAddress, end,
            (uint64_t) meminfo.RegionSize, meminfo.AllocationProtect, meminfo.Protect, meminfo.State
        );
}

void printMemBasicInfo(void* addr) {
    MEMORY_BASIC_INFORMATION meminfo;
    size_t s = VirtualQueryEx(hProc, (PVOID) addr, &meminfo, sizeof(meminfo));
    if (s == sizeof(meminfo)) {
        printMemBasicInfo(meminfo);
    } else {
        warn("%p: VirtualQueryEx returns %d (!= %d)", addr, s, sizeof(meminfo));
    }
}

void tls_fixup_pd(void* teb) {
    uint64_t* cur_teb = (uint64_t*) NtCurrentTeb(); // Get TEB pointer, on x64 is stored in GS.
    // Get pointer to TLS pointer in current process:
    uint64_t* cur_tls = (uint64_t*) ((char*) cur_teb + 0x58); // Read TLS ptr at known offset, effectively __readgsqword(0x58);
    logv("tls_fixup: current teb = 0x%llx tls ptr at 0x%llx", cur_teb, cur_tls);
    waitHitRet();

    // Given we have revived memory, read core TEB address, to find old TLS pointer.
    logv("tls_fixup: MiniDump TEB addr 0x%llx", teb);
    uint64_t* core_tls = (uint64_t*) ((char*) teb + 0x58);
    logv("tls_fixup: MiniDump _tls_array = 0x%llx contains 0x%llx", core_tls, *core_tls);

    *cur_tls = *core_tls; // Replace current TLS with that from MiniDump
    logv("tls_fixup: fixed, cur teb = 0x%llx new tls = 0x%llx contains 0x%llx", cur_teb, cur_tls, *cur_tls);
    waitHitRet();
}

void clock_fixup_pd(struct revival_data* rdata) {
    // Not implemented on Windows.
}

void init_pd() {
    logv("init_pd: PID %ld thread: 0x%lx", _getpid(), GetCurrentThreadId());
    hProc = GetCurrentProcess();
    _SYSTEM_INFO systemInfo;
    GetSystemInfo(&systemInfo);
    vaddr_align = systemInfo.dwAllocationGranularity - 1;
    logv("revival: init_pd: dwAllocationGranularity = %d  vaddr_alignment_pd() = 0x%lx", systemInfo.dwAllocationGranularity, vaddr_alignment_pd());
    if (vaddr_align != 0xffff) {
        warn("Note: dwAllocationGranularity not 64k, vaddr_align = %lld", vaddr_align);
    }
    install_kernelbase_1803_symbol_or_exit(pVirtualAlloc2, "VirtualAlloc2");
    install_kernelbase_1803_symbol_or_exit(pMapViewOfFile3, "MapViewOfFile3");

    heap_test = (uint64_t) malloc(1);
}

void dump() {
    char filename[BUFLEN];
    snprintf(filename, BUFLEN, "revival_dump_%ld.mdmp", _getpid());
    MINIDUMP_TYPE dumpType =  (MINIDUMP_TYPE)(MiniDumpWithFullMemory | MiniDumpWithHandleData | MiniDumpWithFullMemoryInfo | MiniDumpWithThreadInfo | MiniDumpWithUnloadedModules);
    HANDLE hFile = CreateFile(filename, GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (hFile == INVALID_HANDLE_VALUE) {
        warn("%s: CreateFile failed for MiniDump: 0x%x", filename, GetLastError());
    } else {
        warn("revival: Writing MiniDump to %s...", filename);
        if (!MiniDumpWriteDump(hProc, GetCurrentProcessId(), hFile, dumpType, nullptr, nullptr, nullptr)) {
            warn("%s: MiniDumpWriteDump failed: 0x%x", filename, GetLastError());
        }
        CloseHandle(hFile);
    }
}

const char* conflict_check_pd(void* vaddr, unsigned long long length) {
    uint64_t vaddr_end = (uint64_t) vaddr + (uint64_t) length;
    int x;
    if (clash_addr((uint64_t) vaddr, vaddr_end, (uint64_t) &x)) {
        return "conflict with local/stack";
    }
    if (heap_test != 0 && clash_addr((uint64_t) vaddr, vaddr_end, heap_test)) {
        return "conflict with live c heap";
    }
    return nullptr;
}

void set_prot(void* addr, uint64_t length, DWORD prot) {
    DWORD lpfOldProtect;
    if (!VirtualProtect((PVOID) addr, length, prot, &lpfOldProtect)) {
        logv("set_prot: failed (1) setting prot (0x%lx) for: 0x%p, len 0x%lx: error 0x%x.",  prot, addr, length, GetLastError());
        if (logLevel >= LOG_VERBOSE) {
            printMemBasicInfo(addr);
            waitHitRet();
        }
    }
}

// Exception handler:
LPTOP_LEVEL_EXCEPTION_FILTER previousUnhandledExceptionFilter = nullptr;

LONG WINAPI topLevelUnhandledExceptionFilter(struct _EXCEPTION_POINTERS* exceptionInfo) {
#if defined(_M_AMD64)
    uint64_t pc = (uint64_t) exceptionInfo->ContextRecord->Rip;
#else
    // _M_ARM64 would be: pc = exceptionInfo->ContextRecord->Pc;
    error("revival: handler: unsupported platform");
#endif
    uint64_t addr = (uint64_t) exceptionInfo->ExceptionRecord->ExceptionInformation[1];
    logv("revival: handler: PID %ld thread: 0x%lx pc 0x%llx address 0x%llx ", _getpid(), GetCurrentThreadId(), pc, addr);

    std::list<Segment>::iterator iter;
    for (iter = delayedCopySegments.begin(); iter != delayedCopySegments.end(); iter++) {
        if (iter->contains((uint64_t) addr)) {
            logv("Delayed Copy Segment: si_addr = %p in segment %p", addr, iter->vaddr);
            set_prot(iter->vaddr, iter->length, stdProt);
            revival_mapping_docopy(iter->vaddr, iter->length, iter->file_offset);
            return EXCEPTION_CONTINUE_EXECUTION;
        }
    }
    warn("revival: handler: PID %ld thread: 0x%lx pc 0x%llx address 0x%llx: not handled. ", _getpid(), GetCurrentThreadId(), pc, addr);
    waitHitRet();
    if (logLevel >= LOG_DEBUG) {
        dump();
    }
    exitForRetry(); // Letting this process fail would send the wrong return code back to JCmd.
    abort(); // Not reached.
}

void install_handler_pd() {
    previousUnhandledExceptionFilter = SetUnhandledExceptionFilter(topLevelUnhandledExceptionFilter);
}

void* symbol_dynamiclookup_pd(void* h, const char* str) {
    FARPROC s = GetProcAddress((HMODULE) h, str);
    logv("symbol_dynamiclookup: %s = %p", str, s);
    if (s == 0) {
        logv("GetProcAddress failed: 0x%x", GetLastError());
        return (void*) -1;
    }
    return (void*) s;
}

void* load_sharedobject_pd(const char* name, void* vaddr) {
    HMODULE h = LoadLibraryA(name);
    if ((void*) h == vaddr) {
        return (void*) h; // success
    }
    warn("load_sharedobject_pd: %s: load failed address 0x%p != requested 0x%p. error=0x%lx", name, h, vaddr, GetLastError());
    if (h != nullptr) {
        // Loaded, wrong address.
        exitForRetry();
    }
    return (void*) -1;
}

bool mem_canwrite_pd(void* vaddr, size_t length) {
    MEMORY_BASIC_INFORMATION meminfo;
    size_t q = VirtualQueryEx(hProc, vaddr, &meminfo, sizeof(meminfo));
    if (q == sizeof(meminfo)) {
        if (logLevel >= LOG_DEBUG) {
            warn("mem_canwrite_pd:");
            printMemBasicInfo(meminfo);
        }
        int prot = meminfo.Protect & ~PAGE_GUARD; // Remove PAGE_GUARD for this comparison.
        if (prot == PAGE_EXECUTE_READWRITE
            || prot == PAGE_EXECUTE_WRITECOPY
            || prot == PAGE_READWRITE
            || prot == PAGE_WRITECOPY) {
            logd("mem_canwrite_pd: %p protect: 0x%lx: YES", vaddr, meminfo.Protect);
            return true;
        } else {
            logd("mem_canwrite_pd: %p protect: 0x%lx: NO", vaddr, meminfo.Protect);
            return false;
        }
    } else {
        warn("mem_canwrite_pd: %p VirtualQueryEx failed, returning false. Error 0x%x", vaddr, GetLastError());
    }
    return false;
}

bool can_lazycopy_pd(void* addr) {
    MEMORY_BASIC_INFORMATION meminfo;
    size_t q = VirtualQueryEx(hProc, addr, &meminfo, sizeof(meminfo));
    if (q != sizeof(meminfo)) {
        warn("VirtualQueryEx failed");
        return false;
    }
    if ((meminfo.Protect & PAGE_GUARD) != 0) {
        return true;
    } else {
        return false;
    }
}

void* do_mmap_pd(void* addr, size_t length, char* filename, int fd, size_t offset) {
    // Fail quickly if unaligned (MiniDump contents not usually aligned):
    uint64_t offsetAligned = align_down(offset, vaddr_alignment_pd());
    if (offsetAligned != offset) {
        logv("do_mmap_pd: address 0x%llx file offset 0x%llx not aligned, do not try mapping directly, return", addr, offset);
        return (void*) -1;
    }
    LPVOID p = nullptr;
    HANDLE h2;
    HANDLE h = CreateFile(filename, GENERIC_READ | GENERIC_EXECUTE, FILE_SHARE_READ, nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (h == nullptr) {
        logv("do_mmap_pd: CreateFile failed: %s: 0x%lx", filename, GetLastError());
        return (void*) -1;
    } else {
        h2 = CreateFileMapping(h, nullptr, stdProt, 0, 0, nullptr);
        if (h2 == nullptr) {
            logv("do_mmap_pd: CreateFileMapping failed: %s: 0x%lx", filename, GetLastError());
            return (void*) -1;
        }
    }
    p = pMapViewOfFile3(h2, hProc, (PVOID) addr, offset, length, MEM_REPLACE_PLACEHOLDER, stdProt, nullptr, 0);
    if ((uint64_t) p != (uint64_t) addr) {
        logv("do_mmap_pd: MapViewOfFile3 0x%p failed, ret=0x%p error=0x%lx", addr, p, GetLastError());
        p = (void*) -1;
        waitHitRet();
    }
    CloseHandle(h2);
    CloseHandle(h);
    return (void*) p;
}

void* do_map_allocate_pd_VirtualAlloc2(void* addr, size_t length, int protRequested /* 0 to use PAGE_GUARD */) {
    DWORD prot = stdProt;
    if (protRequested == 0) {
        prot |= PAGE_GUARD;
    }
    MEMORY_BASIC_INFORMATION meminfo;
    size_t q = VirtualQueryEx(hProc, addr, &meminfo, sizeof(meminfo));
    if (q != sizeof(meminfo)) {
        warn("VirtualQueryEx failed");
        return (void*) -1;
    }
    void* p = pVirtualAlloc2(hProc, (PVOID) addr, length, MEM_RESERVE | MEM_COMMIT, prot, nullptr, 0);
    logd("do_map_allocate_pd_VirtualAlloc2: first alloc attempt 0x%p len 0x%zx prot 0x%x: returns = 0x%p, error = 0x%lx",
        (void*) addr, length, prot, p, GetLastError());

    if ((void*) p != (void*) addr) {
        // Did not get requested address.
        q = VirtualQueryEx(hProc, addr, &meminfo, sizeof(meminfo));
        if (q != sizeof(meminfo)) {
            warn("do_map_allocate_pd_VirtualAlloc2: 0x%llx: VirtualQueryEx failed", addr);
            return (void*) -1;
        }
        uint64_t existing_end = (uint64_t) meminfo.BaseAddress + (uint64_t) meminfo.RegionSize;
        uint64_t requested_end = (uint64_t) addr + (uint64_t) length;
        uint64_t remaining = requested_end - existing_end;

        int e = GetLastError();
        if (e == 0) {
            // No error, but requested address was changed, or re-aligned.
            // Not likely as caller function do_map_allocate_pd aligns first.
            logv("do_map_allocate_VirtualAlloc2: requested 0x%llx got 0x%llx (no error)", addr, p);
            // If all already mapped, just return as if alloc worked.
            if (p <= addr && requested_end <= existing_end) {
                logv("do_map_allocate_VirtualAlloc2: requested 0x%llx got 0x%lx contains all needed", addr, p);
                return addr;
            } else {
                logv("do_map_allocate_VirtualAlloc2: requested 0x%llx got 0x%lx existing does not contain required mapping", addr, p);
                // Could consider expanding allocation, but not observed in practice.
                return (void*) -1;
            }
        } else if (e  == ERROR_INVALID_ADDRESS /* 0x1e7 */) {
            // Already mapped, conflict? e.g. jvm data.  Is more allocation needed?  This is seen in practice.
            logv("do_map_allocate_pd__VirtualAlloc2: requested 0x%llx got 0x%lx not valid, already mapped?", addr, p);
            uint64_t wanted_end = (uint64_t) addr + (uint64_t) length;
            if (wanted_end <= existing_end) {
                logv("do_map_allocate_pd_VirtualAlloc2: mapping covered by existing_end at 0x%llx", existing_end);
                return addr;
            } else {
                size_t remaining = (uint64_t) wanted_end - existing_end;
                logv("do_map_allocate_pd_VirtualAlloc2: existing. remaining = 0x%llx protRequested = 0x%x", remaining, protRequested);
                void* r = do_map_allocate_pd_VirtualAlloc2((void*) existing_end, remaining, protRequested);
                logd("do_map_allocate_pd_VirtualAlloc2: done recurse");
                // Return original requested address on success:
                if ((uint64_t) r == (uint64_t) existing_end) {
                    return addr;
                } else {
                  return r;
                }
            }
        } else {
            logv("do_map_allocate_VirtualAlloc2: requested 0x%llx got 0x%llx error: 0x%x", addr, p, e);
        }
    }
    return p;
}

void* do_map_allocate_pd(void* vaddr, size_t length, int prot /* 0 for guarded, or 1 for standard RWX permission */) {
    // Alignment: mappings file is created with minidump addresses, not necessarily 64k aligned.
    uint64_t vaddr_aligned = align_down((uint64_t) vaddr, vaddr_alignment_pd());
    uint64_t diff = (uint64_t) vaddr - (uint64_t) vaddr_aligned;
    size_t length_aligned = length + diff;
    length_aligned = align_up(length_aligned, vaddr_alignment_pd());

    if (vaddr_aligned != (uint64_t) vaddr) {
        logd("do_map_allocate_pd: vaddr 0x%p aligns -> 0x%p  len 0x%p adjusts -> 0x%p",
            (void*) vaddr, (void*) vaddr_aligned, length, length_aligned);
    }

    uint64_t r = (uint64_t) do_map_allocate_pd_VirtualAlloc2((void*) vaddr_aligned, length_aligned, prot);
    // Accept the aligned down address and return as if the requested vaddr was honoured.
    if (r == vaddr_aligned) {
        return vaddr;
    } else {
        return (void*) r;
    }
}

char* readstring_at_offset_pd(const char* filename, uint64_t offset) {
   int fd = open(filename, O_RDONLY | O_BINARY);
    if (fd < 0) {
        warn("cannot open %s", filename);
        return (char*) -1;
    }
    off_t pos = lseek(fd, (long) offset, SEEK_SET);
    if (pos < 0) {
        warn("readstring_at_pd: %s: lseek(%ld) fails %d : %s", filename, offset, errno, strerror(errno));
        close(fd);
        return (char*) -1;
    }
    char* s = readstring(fd);
    close(fd);
    return s;
}

char* readstring_from_core_at_vaddr_pd(const char* filename, uint64_t addr) {
    MiniDump dump(filename, nullptr);
    return dump.read_string_at_address(addr);
}

uint64_t read_pointer_at_offset_pd(const char* filename, uint64_t offset) {
    int fd = open(filename, O_RDONLY | O_BINARY);
    if (fd < 0) {
        warn("cannot open %s", filename);
        return 0;
    }
    off_t pos = lseek(fd, (long) offset, SEEK_SET);
    if (pos < 0) {
        warn("read_pointer_at_pd: %s: lseek(%ld) fails %d : %s", filename, offset, errno, strerror(errno));
        close(fd);
        return 0;
    }
    uint64_t p;
    int e = read(fd, &p, sizeof(p));
    close(fd);
    return p;
}

void copy_file_pd(const char* srcfile, const char* destfile) {
    // Copy paths to normalise:
    char* s = strdup(srcfile);
    char* d = strdup(destfile);
    if (s == nullptr || d == nullptr) {
        error("allocation failed normalizing paths to copy");
    }
    normalize_path_pd(s);
    normalize_path_pd(d);
    logv("copy_file_pd: src: %s dest: %s", s, d);
    if (!CopyFile(s, d, false)) {
        warn("Copy file failed: %s %s: error %d", s, d, GetLastError());
    }
    free(s);
    free(d);
}

char* check_editbin() {
    char* editbin_env = getenv("EDITBIN");
    if (editbin_env != nullptr) {
        if (!file_exists_pd(editbin_env)) {
            error("EDITBIN from environment does not exist: '%s'", editbin_env);
        }
        logv("Using EDITBIN: '%s'", editbin_env);
        return editbin_env;
    } else {
        return nullptr;
    }
}

int relocate_sharedlib_pd(const char* filename, const void* addr) {
    if (editbin == nullptr) {
        // Normal usage, editbin not specified.
        // Two calls.  ReBaseImage64 may not report correct rebased address,
        // second call verifies change has been made (see verbose log output).
        if (!PEFile::rebase(filename, (long long) addr)) {
            return -1;
        }
        if (!PEFile::rebase(filename, (long long) addr)) {
            return -1;
        }
        if (!PEFile::remove_dynamicbase(filename)) {
            return -1;
        }
        return 0;
    } else {
        // Alternative usage, call:
        // EDITBIN.EXE /DYNAMICBASE:NO /REBASE:BASE=0xaddress filename
        char command[BUFLEN];
        memset(command, 0, BUFLEN);
        strncat(command, editbin, BUFLEN - 1);
        strncat(command, " /DYNAMICBASE:NO /REBASE:BASE=0x", BUFLEN - 1);
        char address[32];
        sprintf(address, "%llx", (unsigned long long) addr);
        strncat(command, address, BUFLEN - 1);
        strncat(command, " ", BUFLEN - 1);
        strncat(command, filename, BUFLEN - 1);

        int e = system(command);
        logv("relocate_sharedlib_pd: '%s' returns %d", command, e);
        return e;
    }
}

void write_mem_mappings(MiniDump* dump, int fd, const char* corename, uint64_t dump_ReadOnlySharedMemBase) {
    // Read minidump memory list, create the memory mappings list.
    // Ideally map data directly from core, but if alignment does not work (segments too close),
    // create mapping and copy bytes later.
    std::list<Segment> segsToCopy; // Segments that need bytes copied
    char buf[BUFLEN];

    dump->prepare_memory_ranges(); // Get ready to read Segments: locate Memory64ListStream to read all MINIDUMP_MEMORY64_LIST
    RVA64 currentRVA = dump->getBaseRVA(); // Current offset in file
    MINIDUMP_MEMORY_DESCRIPTOR64 d;
    ULONG64 prevAddr = 0;

    // Iterate, reading segments from dump.  Consider a current and next segment, so we can check for "too close" addresses.
    Segment* seg = nullptr;
    Segment* segNext = nullptr;
    while (true) {
        if (seg == nullptr || segNext == nullptr) {
            // First iteration, or no segNext waiting:
            seg = dump->readSegment(&d, &currentRVA, true);
        } else {
            // Use a segNext we already read (but did not use):
            seg = segNext;
            segNext = nullptr;
        }
        if (seg == nullptr) {
            break;
        }
        logd("create_mappings_pd: addr 0x%llx size 0x%llx   current RVA/file offset: 0x%llx", d.StartOfMemoryRange, d.DataSize, currentRVA);
        prevAddr = d.StartOfMemoryRange;

        if (!seg->is_relevant()) {
            logd("create_mappings_pd: not relevant: seg 0x%llx", d.StartOfMemoryRange);
            continue;
        }
        if (seg->contains(dump_ReadOnlySharedMemBase)) {
            // Skip, and skip further segments until the library/module address after ReadOnlySharedMemBase.
            uint64_t resume_address = dump->get_library_mapping_after(dump_ReadOnlySharedMemBase);
            do {
                logv("create_mappings_pd: avoid 0x%llx: seg 0x%llx", dump_ReadOnlySharedMemBase, d.StartOfMemoryRange);
                seg = dump->readSegment(&d, &currentRVA, true);
                if (seg == nullptr) {
                    break; // Not expected.  Usually two or three Segments, then modules start.
                }
            } while ((uint64_t) seg->vaddr <= resume_address);
            continue;
        }
        // We have something in seg, but consider the next region also:
        segNext = dump->readSegment(&d, &currentRVA, true);

        // Is next region too close for vaddr alignment to work?
        // Grow a bigger segment to map, that will have these neighbouring segments' data copied in.
        Segment* biggerSeg = nullptr;
        while (segNext != nullptr && align_up(seg->end(), vaddr_alignment_pd()) >= segNext->start()) {
            if (logLevel >= LOG_DEBUG) {
                logv("create_mappings: segs too close for alignment, seg: %p - %p next seg: %p", seg->vaddr, seg->end(), segNext->vaddr);
                seg->toString(buf, BUFLEN);
                logv("later seg    : %s", buf);
                segNext->toString(buf, BUFLEN);
                logv("later segNext: %s", buf);
            }
            // Save segs, will write "C" copy lines later.
            if (biggerSeg == nullptr) {
                segsToCopy.push_back(seg);    // Write first seg only on first time through this loop
                biggerSeg = new Segment(seg); // Start with copy of seg info.
            }
            segsToCopy.push_back(segNext);      // Write segNext on all iterations

            biggerSeg->set_end(segNext->end());  // Expand to cover both.
            if (logLevel >= LOG_DEBUG) {
                biggerSeg->toString(buf, BUFLEN);
                logv("BIGGER seg expanded: %s", buf);
            }
            // Next.  Again, get two segments to consider:
            seg = segNext;
            segNext = dump->readSegment(&d, &currentRVA, true);
        }

        // Write line to mappings file.
        int e = 0;
        if (biggerSeg != nullptr) {
            // We created a biggerSeg in the loop above.
            if (logLevel >= LOG_DEBUG) {
                biggerSeg->toString(buf, BUFLEN);
                logv("Write BIGGER seg    : %s", buf);
            }
            e = biggerSeg->write_mapping(fd, "m"); // map only, copy later
            biggerSeg = nullptr;
        } else {
            e = seg->write_mapping(fd, "M"); // map directly from core
        }
    } // End loop reading minidump memory descriptors.

    // Write regions to copy
    std::list<Segment>::iterator iter;
    for (iter = segsToCopy.begin(); iter != segsToCopy.end(); iter++) {
        iter->write_mapping(fd, "C");
    }
}

const int N_JVM_SYMS = 2;
const char* JVM_SYMS[N_JVM_SYMS] = {
    SYM_REVIVE_VM,
    SYM_VM_RELEASE
};

void write_symbols(int symbols_fd, const char* symbols[], int count, const char* revival_dirname) {
    // Using SymFromName() on jvm.dll after relocation will give final absolute addresses.
    PLOADED_IMAGE image = ImageLoad(JVM_FILENAME, revival_dirname);
    if (image == nullptr) {
        error("write_symbols: ImageLoad error '%s': %d", GetLastError());
    }
    HANDLE h2 = (HANDLE) 1;
    bool e = SymInitialize(h2, nullptr, false);
    if (e != TRUE) {
        error("write_symbols: SymInitialize error : 0x%lx", GetLastError());
    }

    char moduleFilename[BUFLEN];
    snprintf(moduleFilename, BUFLEN, "%s" FILE_SEPARATOR JVM_FILENAME, revival_dirname);
    SymLoadModuleEx(h2, nullptr, moduleFilename, nullptr, 0, 0, nullptr, 0);

    TCHAR szSymbolName[MAX_SYM_NAME];
    ULONG64 buffer[(sizeof(SYMBOL_INFO) + MAX_SYM_NAME * sizeof(TCHAR) + sizeof(ULONG64) - 1) / sizeof(ULONG64)];
    PSYMBOL_INFO pSymbol = (PSYMBOL_INFO) buffer;
    pSymbol->SizeOfStruct = sizeof(SYMBOL_INFO);
    pSymbol->MaxNameLen = MAX_SYM_NAME;
    char buf[MAX_SYM_NAME];

    for (int i = 0; i < count; i++) {
        strncpy(szSymbolName, symbols[i], MAX_SYM_NAME);
        if (!SymFromName(h2, szSymbolName, pSymbol)) {
            warn("write_symbols: %d: SymFromName '%s' failed, error: %d", i, szSymbolName, GetLastError());
        } else {
            snprintf(buf, MAX_SYM_NAME, "%s %llx", szSymbolName, pSymbol->Address);
            logv("write_symbols: %d: %s", i, buf);
            write0(symbols_fd, buf);
            write0(symbols_fd, "\n");
        }
    }
    e = SymCleanup(h2);
    if (e != TRUE) {
        warn("write_symbols: SymCleanup error: %d", GetLastError());
    }
    e = ImageUnload(image);
    if (e != TRUE) {
        warn("write_symbols: ImageUnload error : %d", GetLastError());
    }
}

bool create_directory_pd(char* dirname) {
    if (!CreateDirectory(dirname, nullptr)) {
        warn("%s: CreateDirectory failed: %d", dirname, GetLastError());
        return false;
    }
    return true;
}

void delete_file_pd(char* filename) {
    logv("delete_file_pd: %s", filename);
    if (!DeleteFile(filename)) {
        warn("%s: delete failed: %d", filename, GetLastError());
    }
}

void write_sharedlib_mapping(int mappings_fd, char* filename, void* address) {
        char buf[BUFLEN];
        const char* checksum = "0";
        snprintf(buf, BUFLEN, "L %s %llx %s\n", basename_pd(filename), (unsigned long long) address, checksum);
        write0(mappings_fd, buf);
}

void write_sharedlib_mappings(int mappings_fd, MiniDump* dump) {
    logv("write_sharedlib_mappings");

    if (!allLibraries) {
        Segment* jvm_mapping = dump->get_library_mapping(JVM_FILENAME);
        write_sharedlib_mapping(mappings_fd, jvm_mapping->name, jvm_mapping->vaddr);
    } else {
        std::list<Segment> libs = dump->get_library_mappings();
        std::list<Segment>::iterator iter;
        for (iter = libs.begin(); iter != libs.end(); iter++) {
            write_sharedlib_mapping(mappings_fd, iter->name, iter->vaddr);
        }
    }
}

void copy_and_relocate(const char* srcfile, const char* destdir, uint64_t address) {
    char copy_path[BUFLEN]; // destination
    memset(copy_path, 0, BUFLEN);
    strncpy(copy_path, destdir, BUFLEN - 1);
    strncat(copy_path, FILE_SEPARATOR, BUFLEN - 1);
    char* basefilename = basename_pd((char*) srcfile); // basefilename is e.g. "file.dll"
    strncat(copy_path, basefilename, BUFLEN - 1);
    logv("Copying %s to %s", srcfile, copy_path);
    copy_file_pd(srcfile, copy_path);

    // Copy .pdb and .map if present:
    char debuginfo_path[BUFLEN];
    char debuginfo_copy_path[BUFLEN];
    snprintf(debuginfo_path, BUFLEN, "%s", srcfile);
    char* p = strstr(debuginfo_path, ".dll");
    if (p != nullptr) {
        // It is a dll, check for .pdb and .map files.
        // JDK builds now create e.g. file.dll.pdb  but also check for just file.pdb
        snprintf(p, BUFLEN, ".pdb"); // Append to debuginfo_path in place of .dll
        if (file_exists_pd(debuginfo_path)) {
            // file.pdb exists
            snprintf(debuginfo_copy_path, BUFLEN - 1, "%s/%s.pdb", destdir, basefilename);
            copy_file_pd(debuginfo_path, debuginfo_copy_path);
        }
        snprintf(p, BUFLEN, ".map");
        if (file_exists_pd(debuginfo_path)) {
            // file.map exists
            snprintf(debuginfo_copy_path, BUFLEN - 1, "%s/%s.map", destdir, basefilename);
            copy_file_pd(debuginfo_path, debuginfo_copy_path);
        }
        snprintf(p, BUFLEN, ".dll.pdb");
        if (file_exists_pd(debuginfo_path)) {
            // file.dll.pdb exists
            snprintf(debuginfo_copy_path, BUFLEN - 1, "%s/%s.pdb", destdir, basefilename);
            copy_file_pd(debuginfo_path, debuginfo_copy_path);
        }
        snprintf(p, BUFLEN, ".dll.map");
        if (file_exists_pd(debuginfo_path)) {
            // file.dll.map exists
            snprintf(debuginfo_copy_path, BUFLEN - 1, "%s/%s.map", destdir, basefilename);
            copy_file_pd(debuginfo_path, debuginfo_copy_path);
        }
    }
    // Relocate the copy: address of 0 will avoid any relocation.
    if (address != 0) {
        int e = relocate_sharedlib_pd(copy_path, (void*) address);
        if (e < 0) {
            // Relocate failed, delete the file so it can be retried:
            logv("Relocate of copied file failed, delete '%s'", copy_path);
            delete_file_pd(copy_path);
            exitForRetry();
        }
    }
}

void copy_and_relocate(MiniDump dump, const char* destdir) {
    logv("copy_and_relocate");
    if (!allLibraries) {
        Segment* jvm_mapping = dump.get_library_mapping(JVM_FILENAME);
        copy_and_relocate(jvm_mapping->name, destdir, (uint64_t) jvm_mapping->vaddr);
    } else {
        std::list<Segment> libs = dump.get_library_mappings();
        std::list<Segment>::iterator iter;
        for (iter = libs.begin(); iter != libs.end(); iter++) {
            copy_and_relocate(iter->name, destdir, (uint64_t) iter->vaddr);
        }
    }
}

int create_revival_cache_pd(const char* corename, const char* revival_dirname, const char* libdirs) {
    logv("create_revival_cache_pd");
    editbin = check_editbin();

    MiniDump dump(corename, libdirs);
    if (!dump.is_valid()) {
        error("Cannot open MiniDump: '%s'", corename);
    }

    // Find JVM and its load address from dump.
    Segment* jvm_mapping = dump.get_library_mapping(JVM_FILENAME);
    if (jvm_mapping == nullptr) {
        warn("JVM library not found in MiniDump.");
        error("For cores from other systems, or if JDK at path in core has changed, use -L to specify JVM location.");
    }
    logv("JVM = '%s' at address %p", jvm_mapping->name,  jvm_mapping->vaddr);
    if (!file_exists_pd( jvm_mapping->name)) {
        error("No file for JVM '%s'",  jvm_mapping->name);
    }

    {
        PEFile pefile(jvm_mapping->name); // Narrow scope means the jvm file gets closed
        Segment* jvm_data_seg = new Segment();
        if (!pefile.find_data_segs(jvm_mapping->vaddr, &jvm_data_seg, nullptr)) {
            error("Failed to find JVM data segments.");
        }
        logv("JVM .data  SEG: 0x%llx - 0x%llx", jvm_data_seg->start(),  jvm_data_seg->end());
        dump.set_jvm_data(jvm_data_seg);

        // Create mappings file:
        // Normalize corename so basename works (if we were given forward slashes, basename fails).
        char* corename_n = strdup(corename);
        normalize_path_pd(corename_n);

        int mappings_fd = mappings_file_create(revival_dirname, corename_n);
        if (mappings_fd < 0) {
            free(corename_n);
            error("Failed to create mappings file.");
        }
        // Write mappings file:
        write_sharedlib_mappings(mappings_fd, &dump);
        // Windows TEB: used to setup TLS on revival.
        uint64_t dump_TEB = dump.get_teb();
        uint64_t dump_PEB = 0;
        uint64_t dump_ReadOnlySharedMemBase = 0;
        if (dump_TEB != 0) {
            dump_PEB = dump.get_peb();
            dump_ReadOnlySharedMemBase = dump.read_pointer_at_address(dump_PEB + 0x88); // Known offset from PEB.
            logv("Dump: TEB 0x%llx PEB 0x%llx ReadOnlySharedMemBase 0x%llx", dump_TEB, dump_PEB, dump_ReadOnlySharedMemBase);
            writef(mappings_fd, "TEB %llx\n", dump_TEB);
        } else {
            warn("TEB not resolved from MiniDump.");
        }
        write_mem_mappings(&dump, mappings_fd, corename_n, dump_ReadOnlySharedMemBase);
        writef(mappings_fd, "\n");
        close(mappings_fd);
        free(corename_n);
    }

    // Copy jvm/libraries into core.revival dir
    copy_and_relocate(dump, revival_dirname);

    // Create symbols file
    int symbols_fd = symbols_file_create(revival_dirname);
    if (symbols_fd < 0) {
        error("Failed to create mappings file");
    }
    logv("Write symbols");
    write_symbols(symbols_fd, JVM_SYMS, N_JVM_SYMS, revival_dirname);
    close(symbols_fd);
    logv("Write symbols done");
    waitHitRet();

    logv("create_revival_cache_pd returning %d", 0);
    return 0;
}
