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

#include "revival.hpp"

// Diagnostics
int logLevel = 0;
int debugPause = false;
int versionCheckEnabled = true;
bool allLibraries = false;

// Revival prep state:
char* core_filename;
const char* revivaldir;
unsigned long long core_timestamp;
const char* mappings_filename;

// Set during actual revival:
char* jvm_filename = nullptr;
void* jvm_address = nullptr;
void* h; // Opaque handle to libjvm
std::list<Segment> delayedCopySegments;
struct revival_data* rdata; // Data from revived JVM

#ifndef WINDOWS
struct timeval start_time;
#else
ULONGLONG start_time;
#endif

#ifdef WINDOWS
uint64_t* core_teb;
#endif

void revived_exit(int e) {
       logv("revived_exit: %d", e);
#ifdef WINDOWS
    TerminateProcess(GetCurrentProcess(), e);
#else
    _exit(e);
#endif
}

void exitForRetry() {
    revived_exit(EXIT_SUGGEST_RETRY);
}

uint64_t align_down(uint64_t ptr, uint64_t mask) {
    return ptr & ~mask;
}

uint64_t align_up(uint64_t ptr, uint64_t mask) {
    return (ptr & ~mask) + mask + 1;
}

void write0(int fd, const char* buf) {
    size_t len = strlen(buf);
    int e = (int) write(fd, buf, (unsigned int) len);
    if (e < 0) {
        // Do not call warn(), that calls this method.
        fprintf(stderr, "revival write: Write failed: %s\n", strerror(errno));
    } else if (e != (int) len) {
        fprintf(stderr, "revival write: Write failed: written %d buf %d.\n", e, (int) len);
    }
}

void writef(int fd, const char* format, ...) {
    char buffer[BUFLEN];
    memset(buffer, 0, BUFLEN);
    va_list args;
    va_start(args, format);
    vsnprintf(buffer, BUFLEN - 1, format, args);
    va_end(args);
    write0(fd, buffer);
}

/**
 * Read a char string from an fd.
 */
char* readstring(int fd) {
    char* buf = (char*) malloc(BUFLEN);
    if (buf == nullptr) {
        error("readstring: Failed to malloc buffer to read string");
    }
    int c = 0;
    do {
        int e = read(fd, &buf[c], 1);
        if (e != 1) {
            free(buf);
            return nullptr;
        }
    } while (buf[c++] != 0 && c < BUFLEN);
    return buf;
}

void log0(const char* msg) {
    // Add timestamp and newline to message, write on stderr.
    char buffer[BUFLEN];
#ifndef WINDOWS
    struct timeval now;
    gettimeofday(&now, nullptr);
    struct timeval timediff;
    timersub(&now, &start_time, &timediff);
    snprintf(buffer, BUFLEN - 1, "%ld.%ld: %s\n", timediff.tv_sec, (long) timediff.tv_usec, msg);
#else
    ULONGLONG now = GetTickCount64();
    snprintf(buffer, BUFLEN - 1, "%lldms: %s\n", (now - start_time), msg);
#endif
    write0(2 /* stderr */, buffer);
}

void log(const char* format, ...) {
    char buffer[BUFLEN];
    memset(buffer, 0, BUFLEN);
    va_list args;
    va_start(args, format);
    vsnprintf(buffer, BUFLEN - 1, format, args);
    va_end(args);
    log0(buffer);
}

void logv(const char* format, ...) {
    if (logLevel >= LOG_VERBOSE) {
        char buffer[BUFLEN];
        memset(buffer, 0, BUFLEN);
        va_list args;
        va_start(args, format);
        vsnprintf(buffer, BUFLEN - 1, format, args);
        va_end(args);
        log0(buffer);
    }
}

void logd(const char* format, ...) {
    if (logLevel >= LOG_DEBUG) {
        char buffer[BUFLEN];
        memset(buffer, 0, BUFLEN);
        va_list args;
        va_start(args, format);
        vsnprintf(buffer, BUFLEN - 1, format, args);
        va_end(args);
        log0(buffer);
    }
}

void warn(const char* format, ...) {
    char buffer[BUFLEN];
    memset(buffer, 0, BUFLEN);
    va_list args;
    va_start(args, format);
    vsnprintf(buffer, BUFLEN - 1, format, args);
    va_end(args);
    write0(2 /* stderr */, buffer);
    write0(2, "\n");
}

void error(const char* format, ...) {
    char buffer[BUFLEN];
    memset(buffer, 0, BUFLEN);
    va_list args;
    va_start(args, format);
    vsnprintf(buffer, BUFLEN - 1, format, args);
    va_end(args);
    write0(2 /* stderr */, buffer);
    write0(2, "\n");
    revived_exit(1);
}

/**
 * Diagnostic pause (e.g. for debugger attach) when revivalhelper is run with REVIVAL_WAIT=1 in environment.
 */
void waitHitRet() {
    if (debugPause) {
        warn("(waiting, hit return)");
        getchar();
    }
}

/**
 * Return the file size in bytes, or zero on error.
 */
unsigned long long file_size(const char* filename) {
    struct stat sb;
    if (stat(filename, &sb) == -1) {
       warn("cannot stat '%s': %s", filename, strerror(errno));
       return 0;
   }
   return (long long) sb.st_size;
}

/**
 * Return the file modification time in seconds, or 0 on error.
 */
unsigned long long file_time(const char* filename) {
    struct stat sb;
    if (stat(filename, &sb) == -1) {
       warn("cannot stat '%s': %s", filename, strerror(errno));
       return 0;
   }
   return (long long) sb.st_mtime;
}


bool clash_range(uint64_t v1, uint64_t v2, uint64_t t1, uint64_t t2) {
    // Region v1, v2 surrounds region t1,t2:
    if (v1 <= t1 && v2 >= t2) {
        return true;
    }
    // Either end of region v1, v2 is inside region t1, t2:
    if ((v2 > t1 && v2 < t2)
            || (v1 > t1 && v1 < t2)) {
        return true;
    }
    // Either end of region t1, t2 is inside region v1, v2:
    if ((t2 >= v1 && t2 <= v2)
            || (t1 >= v1 && t1 <= v2)) {
        return true;
    }
    return false;
}

bool clash_addr(uint64_t v1, uint64_t v2, uint64_t xaddr) {
    uint64_t t1 = align_down(xaddr, vaddr_alignment_pd());
    uint64_t t2 = align_up(xaddr, vaddr_alignment_pd());
    if (clash_range(v1, v2, t1, t2)) {
        return true;
    }
    return false;
}

void conflict_check(void* vaddr, size_t length) {
     const char* msg = conflict_check_pd(vaddr, length);
     if (msg != nullptr) {
         warn("revival: conflict: 0x%lx - 0x%lx len=%zx: %s", (uint64_t) vaddr, ((uint64_t) vaddr + length), length, msg);
         exitForRetry();
     }
}

/**
 * Create a memory mapping, at some virtual address, directly from a file/offset/length.
 * Return -1 on failure.
 */
int revival_mapping_mmap(void* vaddr, size_t length, size_t offset, char* filename, int fd) {
    int e = 0;
    logv("revival_mapping_mmap: " PTR_FORMAT " - " PTR_FORMAT " len=0x%zx file offset=0x%llx",
         (uintptr_t) vaddr, (uintptr_t) ((uint64_t) vaddr + length), length, (long long) offset);

    void* mapped_addr = do_mmap_pd(vaddr, length, filename, fd, offset);
    if (mapped_addr != vaddr) {
        logv("revival_mapping_mmap: mapping failed: wanted vaddr: %p returned: %p", vaddr, mapped_addr);
        e = -1;
    } else {
        logd("revival_mapping_mmap: mapping OK %p", vaddr);
        e = 0;
    }
    return e;
}

/**
 * Create a memory allocation at some address, length.
 */
int revival_mapping_allocate(void* vaddr, size_t length, int prot) {
    void* e = do_map_allocate_pd(vaddr, length, prot);
    if (e != vaddr) {
        return -1;
    }
    return 0;
}

/**
 * Copy the actual bytes from a core file offset to virtual address.
 */
int revival_mapping_docopy(void* vaddr, size_t length, size_t offset) {
    logv("revival_mapping_docopy: %p size 0x%lx pos=%zu", vaddr, length, offset);
    if (!mem_canwrite_pd(vaddr, length)) {
        warn("revival_mapping_docopy: cannot write at vaddr 0x%p length " SIZE_FORMAT_X_0, vaddr, length);
        return -1;
    }
    FILE* f = fopen(core_filename, "rb");
    if (!f) {
        warn("revival_mapping_docopy: cannot open: '%s': %s", core_filename, strerror(errno));
        return -1;
    }
    int e = fseek(f, (long) offset, SEEK_SET);
    if (e != 0) {
        warn("revival_mapping_docopy: cannot seek '%s' to offset %lx: returns %d: %s", core_filename, (long) offset, e, strerror(errno));
        fclose(f);
        return -1;
    }
    // Read at offset and copy bytes to vaddr:
    uint64_t* p = (uint64_t*) vaddr;
    *p = 0xb19d1b5; // Check we can write.  Overwrite in the loop below.
    for (size_t i = 0; i < length/8; i++) {
        e = (int) fread(p++, 8, 1, f);
        if (e != 1) {
            warn("revival_mapping_docopy: fread failed: returns %d at %p pos=%zu : %s", e, p, i, strerror(errno));
            fclose(f);
            return -1;
        }
    }
    fclose(f);
    return 0;
}

/**
 * Copy bytes from some offset in a file, to memory.  Optionally create the memory allocation first.
 * Used when a mapping cannot be performed directly from the file, usually due to alignment problems
 * (so expect file offset to not be aligned).
 *
 * Usually set up a delayed copy to be satisfied later in response to a fault, but do the copy now if
 * that is no possible.
 *
 * This method is used on Windows, where file alignment hinders direct mapping from MiniDump, and
 * also on a Linux "gcore" (dumped by gdb).
 *
 * Return -1 on error.
 */
int revival_mapping_copy(void* vaddr, size_t length, size_t offset, bool allocate, char* filename, int fd) {
    logd("revival_mapping_copy: alloc=%d vaddr " PTR_FORMAT " - " PTR_FORMAT " len=" SIZE_FORMAT_X_0 " from file offset 0x%llx",
         allocate, (uintptr_t) vaddr, (uintptr_t) ((uint64_t) vaddr + length), length, (long long) offset);

    if (allocate) {
        int e = revival_mapping_allocate(vaddr, length, 0); // 0 for no permission, copy in on fault.
        if (e < 0) {
            warn("revival_mapping_copy: allocation required at 0x%llx : allocation failed: %d", (unsigned long long) vaddr, e);
            return -1;
        }
    }
    if (can_lazycopy_pd(vaddr)) {
        // Set up a delayed copy, to be called by the handler.
        // We needed to make the mapping to ensure it can be done, and not fail later.
        Segment* seg = new Segment(vaddr, length, offset, length);
        delayedCopySegments.push_back(*seg);
        return 0;
    } else {
        // Otherwise, copy now:
        return revival_mapping_docopy(vaddr, length, offset);
    }
}

/**
 * Load a shared library, using directory name and library name, at the given address.
 * Returns the value from load_sharedobject_pd(), which is an opaque handle (not necessarily the address), or -1 for error.
 */
void* load_sharedlibrary_fromdir(const char* dirname, const char* libname, void* vaddr, char* sum) {
    char buf[BUFLEN];
    snprintf(buf, BUFLEN, "%s/%s", dirname, libname);
    void* a = load_sharedobject_pd(buf, vaddr);
    logv("load_sharedobject_pd: %s: returns %p", buf, a);
    return a;
}

/**
 * Read and process the "core.mappings" file.
 * The file contains the name of the core file to open.
 * Map (revive) memory segments described by the file, into the current process.
 *
 * The core.mappings little language:
 *
 * M    map directly from core                      revival_mapping_mmap(vaddr, length, offset, core_filename, core_fd);
 * m    map allocation, not backed by core          revival_mapping_allocate(void* vaddr, size_t length);
 * C    copy data (into an earlier "m" allocation)  revival_mapping_copy(vaddr, length, offset, false, core_filename, core_fd);
 */
int mappings_file_read(const char* corename, const char* dirname, const char* mappings_filename) {
    char s1[BUFLEN];
    char s2[BUFLEN];
    char s3[BUFLEN];
    int lines = 0;
    int M_count = 0;
    int MtoC_count = 0;
    int m_count = 0;
    int C_count = 0;

    FILE* f = fopen(mappings_filename, "r");
    if (!f) {
        warn("cannot open: '%s': %s", mappings_filename, strerror(errno));
        return -1;
    }
    // Use generous sizes for scanf string destinations, easily within BUFLEN.
    // Read header:
    int e = fscanf(f, "revival %32s\n", s1 /* version */);
    if (e != 1) {
        warn("mappings_file_read: unrecognised header in: %s", mappings_filename);
        fclose(f);
        return -1;
    }
    logv("mappings_file_read: revival data version %s", s1);

    // Read corefile details:
    e = fscanf(f, "core %1024s %32s %128s\n", s1 /* core filename */, s2 /* length */, s3 /* possible checksum */);
    if (e != 3) {
        warn("mappings_file_read: unrecognised core file info in: %s", mappings_filename);
        fclose(f);
        return -1;
    }
    lines++;
    // Do not compare core names, cores can be renamed.
    // Compare size: this must match.
    unsigned long long parsedSize = strtoull(s2, nullptr, 10);
    unsigned long long coresize = file_size(corename);
    if ((unsigned long long) coresize != parsedSize) {
        error("%s: error: size mismatch.  revival cache recorded core size %lld, actual file size %lld", core_filename, parsedSize, coresize);
    }

    // timestamp from core file is used for time of crash if nothing better is available from VM.  millis since epoch.
    core_timestamp = 0;
    e = fscanf(f, "time %32s\n", s1);
    if (e == 1) {
        core_timestamp = (long long) strtoll(s1, nullptr, 10);
        logv("core file timestamp: %lld", core_timestamp);
    }
    lines++;

    // Linux needs an fd to pass to mmap.  Windows will pass a filename.
    int core_fd = -1;
#ifdef LINUX
    core_fd = open(core_filename, O_RDONLY);
    if (core_fd < 0) {
        warn("%s: %s", core_filename, strerror(errno));
        fclose(f);
        return -1;
    }
#endif
    waitHitRet();

    // Read and process the mappings:
    while (1) {
        lines++;
        logd("mappings_file_read: line %d", lines);
        s1[0] = '\0';
        char s3[BUFLEN];
        char s4[BUFLEN];
        char s5[BUFLEN];
        char s6[BUFLEN];
        char s7[BUFLEN];
        e = fscanf(f, "L %32s %32s %128s\n", s1, s2, s3);
        if (e == 3) {
            void* vaddr = (void*) strtoull(s2, nullptr, 16);
            logv("Load library '%s' required at %p...", s1, vaddr);
            h = load_sharedlibrary_fromdir(dirname, s1, vaddr, s3);
            if (h == (void*) -1) {
                warn("Load library '%s' failed to load at %p", s1, vaddr);
                fclose(f);
                return -1;
            }
            logv("Loaded library '%s' at %p", s1, vaddr);
            // Record jvm details: needed for version check.
            if (strstr(s1, JVM_FILENAME)) {
                jvm_filename = (char*) malloc(BUFLEN);
                snprintf(jvm_filename, BUFLEN - 1, "%s%s%s", dirname, FILE_SEPARATOR, s1);
                jvm_address = vaddr;
                waitHitRet();
            }
            continue;
        }

        // Windows revival preparation recorded TEB, to fixup TLS on revival:
        e = fscanf(f, "TEB %32s\n", s1);
        if (e == 1) {
#ifdef WINDOWS
            core_teb = (uint64_t*) strtoull(s1, nullptr, 16);
#else
            warn("TEB line invalid on non-Windows.");
#endif
            continue;
        }

        e = fscanf(f, "%32s %32s %32s %32s %32s %32s %32s\n", s1, s2, s3, s4, s5, s6, s7);
        if (e == 7) {
            // command, virtual address, virtual address end, source file offset, source file mapping size, length in memory, RWX
            //  s1      s2               s3                   s4                  s5                        s6                s7
            char* endptr;
            void* vaddr = (void*) strtoull(s2, &endptr, 16);
            size_t length = strtoul(s6, &endptr, 16);
            size_t offset = strtoul(s4, &endptr, 16);
            // Different length in memory and length in file not needed, not implemented.  Field s4 ignored.
            if (strncmp(s1, "M", 1) == 0) {
                // Map memory from core:
                conflict_check(vaddr, length);
                int e = revival_mapping_mmap(vaddr, length, offset, core_filename, core_fd);
                if (e < 0) {
                    // On failure, try copying.  Used for a Linux gcore (gdb) as file offsets are not aligned.
                    // Also on Windows, vaddr and file offset generally not aligned in MiniDump.
                    // Allocate, and fail if allocation fails, to avoid accidentally overwriting e.g. existing libraries.
                    e  = revival_mapping_copy(vaddr, length, offset, true /* allocate */, core_filename, core_fd);
                    logv("mappings_file_read: retry M 0x%llx using revival_mapping_copy returns: %d", (unsigned long long) vaddr, e);
                    if (e < 0 ) {
                        exitForRetry();
                    } else {
                        MtoC_count++;
                    }
                } else {
                    M_count++;
                }
            } else if (strncmp(s1, "m", 1) == 0) {
                // Allocate only, file offset/length not used:
                conflict_check(vaddr, length);
                int e = revival_mapping_allocate(vaddr, length, 0); // No permission, will be filled by a "C" line, delayed copy.
                if (e < 0) {
                    warn("mappings_file_read: m 0x%llx failed: %d", (unsigned long long) vaddr, e);
                    e = revival_mapping_allocate(vaddr, length, 1); // With permission, will not be a delayed copy
                    exitForRetry();
                } else {
                    m_count++;
                }
            } else if (strncmp(s1, "C", 1) == 0) {
                // Copy: "C" lines are to populate allocations in "m" lines.
                // No allocation, an "m" line already allocated.
                int e = revival_mapping_copy(vaddr, length, offset, false /* allocate */, core_filename, core_fd);
                if (e < 0) {
                    warn("mappings_file_read: 'C' copy failed for seg at 0x%llx: %d", (unsigned long long) vaddr, e);
                    exitForRetry();
                } else {
                    C_count++;
                }
            } else {
                error("mappings_file_read: unrecognised mapping line %d: '%s'", lines, s1);
            }
            continue;
        }
        if (strlen(s1) > 0) {
            error("mappings_file_read: unrecognised line %d: '%s'", lines, s1);
        }
        break;
    }
    if (logLevel >= LOG_VERBOSE) {
        warn("mappings_file_read: read %d lines, Mappings: %d  map allocs: %d  Copies: %d  M converted to C: %d",
            lines, M_count, m_count, C_count, MtoC_count);
        warn("delayedCopySegments.size = %d", (int) delayedCopySegments.size());
    }
    if (core_fd >= 0) {
        close(core_fd);
    }
    fclose(f);
    waitHitRet();
    return 0;
}

/**
 * Lookup a symbol in the symbols file in a given direcory.
 */
void* symbol_resolve_from_symbol_file(const char* dirname, const char* sym) {
    char buf[BUFLEN];
    snprintf(buf, BUFLEN, "%s/%s", dirname, SYMBOLS_FILENAME);
    int e = 0;
    void* addr = (void*) -1;
    FILE* f = fopen((char*) &buf, "r");
    if (!f) {
        warn("Cannot open symbol file: %s: %s", buf, strerror(errno));
        return (void*) -1;
    }

    char line_buffer[BUFLEN];
    char s1[BUFLEN];
    char s2[BUFLEN];

    while (fgets(line_buffer, BUFLEN, f) != nullptr) {
        memset(s1, 0, BUFLEN);
        memset(s2, 0, BUFLEN);
        e = sscanf(line_buffer, "%128s %32s\n", s1, s2);  // symbol, address
        if (e == 2) {
            if (strncmp(s1, sym, BUFLEN) == 0) {
                char* endptr;
                addr = (void*) strtoll(s2, &endptr, 16);
                break;
            }
        }
    }
    fclose(f);
    logv("symbol: %s = %p", sym, addr);
    return addr;
}

void* symbol_deref(const char* sym) {
    void* p = symbol(sym);
    if (p != (void*) -1) {
        p = (void*) (*(intptr_t*) p);
    }
    return p;
}

/**
 * Lookup a symbol, return as a void* or -1 on failure.
 *
 * Try symbol.mappings first, then a live, platform-specific lookup.
 * Platform-specific lookups such as dlsym() not expected to work for private symbols.
 */
void* symbol(const char* sym) {
    if (!revivaldir) {
        warn("symbol: call revive_image first.");
        return (void*) -1;
    }
    void* p = symbol_resolve_from_symbol_file(revivaldir, sym);
    if (p == (void*) -1) {
        // Lookup e.g. with dlsym:
        p = symbol_dynamiclookup_pd(h, sym);
    }
    return p;
}

/**
 * Resolve a symbol from jvm.symbols, and call it.
 * Use the symbol() function which will try using jvm.symbols first, then a live lookup.
 */
void* symbol_call(const char* sym) {
    void* p = symbol(sym);
    if (p == (void*) -1) {
        return (void*) -1;
    }
    logv("symbol call: %p", p);
    void* (*func)() = (void*(*)()) p;
    return (func)();
}

/**
 * Functions to make a function call, or resolve and call:
 */
void* symbol_call1(const char* sym, void* arg) {
    void* p = symbol(sym);
    if (p == (void*) -1) {
        return (void*) -1;
    }
    logv("symbol call: %p", p);
    void* (*func)(void*) = (void*(*)(void*)) p;
    return (func)(arg);
}

void* symbol_call2(const char* sym, void* arg1, void* arg2) {
    void* p = symbol(sym);
    if (p == (void*) -1) {
        return (void*) -1;
    }
    logv("symbol call: %p", p);
    void* (*func)(void*,void*) = (void*(*)(void*,void*)) p;
    return (func)(arg1, arg2);
}

void* symbol_call3(const char* sym, void* arg1, void* arg2, void* arg3) {
    void* p = symbol(sym);
    if (p == (void*) -1) {
        return (void*) -1;
    }
    logv("symbol call: %p", p);
    void* (*func)(void*,void*,void*) = (void*(*)(void*,void*,void*)) p;
    return (func)(arg1, arg2, arg3);
}

void* symbol_call4(const char* sym, void* arg1, void* arg2, void* arg3, void* arg4) {
    void* p = symbol(sym);
    if (p == (void*) -1) {
        return (void*) -1;
    }
    logv("symbol call: %p", p);
    void* (*func)(void*,void*,void*,void*) = (void*(*)(void*,void*,void*,void*)) p;
    return (func)(arg1, arg2, arg3, arg4);
}

// Call pointer directly, no symbol lookup.
void* call5(void* p, void* arg1, void* arg2, void* arg3, void* arg4, void* arg5) {
    logv("call: %p", p);
    void* (*func)(void*,void*,void*,void*,void*) = (void*(*)(void*,void*,void*,void*,void*)) p;
    return (func)(arg1, arg2, arg3, arg4, arg5);
}

void* symbol_call5(const char* sym, void* arg1, void* arg2, void* arg3, void* arg4, void* arg5) {
    void* p = symbol(sym);
    if (p == (void*) -1) {
        return (void*) -1;
    }
    logv("symbol call: %p", p);
    return call5(p, arg1, arg2, arg3, arg4, arg5);
}

/**
 * Resolve a symbol, and store the given value in that location.
 */
int symbol_set(const char* sym, void* value) {
    void* s = symbol(sym);
    if (s == (void*) -1) {
        return -1;
    }
    *(unsigned long long*) s = (unsigned long long) value;
    return 0;
}

/**
 * Attempt to find the given filename/path in the given directory.
 *
 * The filename may be a path such as /some/dir/jdk/lib/server/libjvm.so
 *
 * Remove leading directory elements from the filename path, until
 * a file exists in the directory or the end of filename is reached.
 *
 * If found, return the path that exists, as a new C heap allocation
 * (strdup) that the caller must free.
 * Return nullptr if not found.
 */
char* find_filename_in_one_dir(const char* dir, const char* filename) {
    char path[BUFLEN];
    char* p = (char*) filename; // Pointer to traverse the given filename/path

    while (true) {
        snprintf(path, BUFLEN - 1, "%s%s%s", dir, FILE_SEPARATOR, p);
        if (file_exists_pd(path)) {
           return strdup(path);
        }
        // Move to next dir entry in filename:
        p = strstr(p, FILE_SEPARATOR);
        if (p != nullptr) {
            // Found, skip the separator itself
            p++;
        } else {
            break;
        }
    }
    logd("find_filename_in_one_dir: Could not find '%s' in '%s'", filename, dir);
    return nullptr;
}

/**
 * Attempt to find the given filename/path in the the given directory list,
 * which is a single string that may contain multiple directories separated by
 * PATH_SEPARATOR characters.
 */
char* find_filename_in_libdirs(const char* libdirs, const char* filename) {
    char dir[BUFLEN];
    char* result = nullptr;

    // On Windows, filename may begin with C:\ which does not work inside a directory, but is removed on next iteration.
    char* start = (char*) libdirs;
    char* end = strstr(start, PATH_SEPARATOR);
    while (end != nullptr) {
        // Separator found, check that directory.  Skip if zero length.
        int len = (int) (end - start);
        if (len > 0) {
            strncpy(dir, start, len);
            dir[len] = 0;
            result = find_filename_in_one_dir(dir, filename);
            if (result != nullptr) {
                logv("find_filename_in_libdirs: query '%s' found '%s'", filename, result);
                return result;
            }
        }
        start = end + 1; // Start of next item
        end = strstr(start, PATH_SEPARATOR);
    }
    // No separator, or no further separator:
    return find_filename_in_one_dir(start, filename);
}

int mappings_file_create(const char* dirname, const char* corename) {
// Create file and header lines:
// core FILENAME size 0 (0 is placeholder for possible checksum)
// time 123213123
//
// Shared libraries written later:
// L jvm addresshex 0   (0 is placeholder for possible checksum)
    char buf[BUFLEN];
    snprintf(buf, BUFLEN, "%s%s", dirname, "/" MAPPINGS_FILENAME);
    logv("mappings_file_create: %s", buf);
#ifdef WINDOWS
    int fd = _open(buf, _O_CREAT | _O_WRONLY | _O_TRUNC, _S_IREAD | _S_IWRITE);
#else
    int fd = open(buf, O_CREAT | O_WRONLY | O_TRUNC, S_IRUSR | S_IWUSR);
#endif
    if (fd < 0) {
        // Failed to create: e.g. already exists but no permissions.
        warn("mappings_file_create failed: %s: %s", buf, strerror(errno));
        return fd;
    }
    // Header:
    snprintf(buf, BUFLEN, "revival %d\n", REVIVAL_VERSION);
    write0(fd, buf);
    // Write core file details.  Use base filename (no path), as it can be moved.
    unsigned long long coresize = file_size(corename);
    snprintf(buf, BUFLEN, "core %s %lld 0\n", basename_pd((char*) corename), coresize);
    write0(fd, buf);
    snprintf(buf, BUFLEN, "time %llu\n", file_time(corename));
    write0(fd, buf);
    return fd;
}

int symbols_file_create(const char* dirname) {
    char buf[BUFLEN];
    snprintf(buf, BUFLEN, "%s%s", dirname, "/" SYMBOLS_FILENAME);
    logv("symbols_file_create: %s", buf);
#ifdef WINDOWS
    int fd = _open(buf, _O_CREAT | _O_WRONLY | _O_TRUNC, _S_IREAD | _S_IWRITE);
#else
    int fd = open(buf, O_CREAT | O_WRONLY | O_TRUNC, S_IRUSR | S_IWUSR);
#endif
    if (fd < 0) {
        warn("symbols_file_create: %s: %s", buf, strerror(errno));
        return fd;
    }
    return fd;
}

/**
 * Return true if the given character pointer is a defined
 * environment variable (one which exists and is not null).
 */
bool env_check(char* s) {
    char* env = getenv(s);
    if (env != nullptr && strlen(env) > 0) {
        return true;
    }
    return false;
}

/**
 * Complete the revival using a helper method in the target JVM.
 * Return 0 for success, -1 for error.
 */
int revive_image_cooperative() {
    void* s = symbol(SYM_REVIVE_VM);
    if (s == (void*) -1) {
        warn("revive_image: JVM helper function not found.");
        return -1;
    }
#ifdef WINDOWS
    tls_fixup_pd(core_teb);
#endif
    logv("revive_image: calling JVM revival helper method %p", s);
    void*(*helper)() = (void*(*)()) s;
    waitHitRet();
    rdata = (struct revival_data *) (helper)();
    logv("revive_image: JVM revival helper method returns %p", rdata);
    if (rdata == nullptr) {
        warn("revive_image: JVM helper failed");
        return -1;
    }
    // Verify revival data from JVM:
    if (rdata->magic != REVIVAL_MAGIC) {
        error("revival: VM returns unrecognized data: %llx", (unsigned long long) rdata->magic);
    }
    if (rdata->version == 0 || rdata->version > REVIVAL_VERSION) {
        error("revival: VM returns unrecognised data version: %llx", (unsigned long long) rdata->version);
    }
    if (rdata->size_this != sizeof(struct revival_data)) {
        warn("revival: VM data size mismatch, this helper %ld VM data claims %ld", sizeof(struct revival_data), rdata->size_this);
    }
    logv("revive_image: revival_data %s/%s/%s/%s", rdata->runtime_name, rdata->runtime_version, rdata->runtime_vendor_version, rdata->jdk_debug_level);
    logv("revive_image: VM Thread object = %p", rdata->vm_thread);
    logv("revive_image: initial_time_count ns = %lld", (unsigned long long) rdata->initial_time_count);
    logv("revive_image: initial_time_date  s  = %lld", (unsigned long long) rdata->initial_time_date);
    logv("revive_image: error time         s  = %f", rdata->error_time);

    clock_fixup_pd(rdata);
    return 0;
}

/**
 * Create (allocate) revival cache directory name, based on corefile name.
 * Use revival_data_path as prefix if non-null.
 */
char* revival_dirname_create(const char* corename, const char* revival_data_path) {
    char* buf = (char*) calloc(1, BUFLEN);
    if (!buf) {
        error("Failed to allocate buffer for revival directory name.");
    }
    int len;
    if (revival_data_path != nullptr) {
        len = snprintf(buf, BUFLEN, "%s%s%s%s", revival_data_path, FILE_SEPARATOR, basename_pd((char*) corename), REVIVAL_SUFFIX);
    } else {
        len = snprintf(buf, BUFLEN, "%s%s", corename, REVIVAL_SUFFIX);
    }
    if (len >= BUFLEN) {
        error("Revival directory name too long.");
    }
    return buf;
}

char* mappings_filename_create(const char* revival_data_path) {
    char* buf = (char*) calloc(1, BUFLEN);
    if (!buf) {
        error("Failed to allocate buffer for mappings file name.");
    }
    int len = snprintf(buf, BUFLEN, "%s%s%s", revival_data_path, FILE_SEPARATOR, "core.mappings");
    if (len >= BUFLEN) {
        error("core.mappings filename too long.");
    }
    return buf;
}

/**
 * Read version string pointed to by some symbol, from both core and binary, and compare.
 * filename parameter names a file (.so or .dll) in the given directory (the revival cache directory).
 */
void version_check(const char* corename, const char* directory, const char* filename, void* base_address,
                    const char* version_symbol) {

    // Called when memory mappings are in place, jvm_filename and jvm_address are set.
    void* ver = symbol_resolve_from_symbol_file(directory, version_symbol);
    if (ver == nullptr || ver == (void*) -1) {
        warn("No version symbol '%s' found, no version check.", version_symbol);
        return;
    }

    logv("Version check '%s' = 0x%llx", version_symbol, (unsigned long long) ver);
    char* ptr = *(char**) ver; // read pointer from now-live memory

    // Can now read string from address space directly, but want to be specific about
    // reading from core and binary, to compare.
    // Read from core:
    logv("Version check: ptr = 0x%llx", (unsigned long long) ptr);
    if (ptr == nullptr) {
        error("JVM version check failed: pointer invalid.");
    }
    char* vm_release_core = readstring_from_core_at_vaddr_pd(corename, (uint64_t) *(char**) ver);
    logv("Version check: version from core: 0x%llx -> 0x%llx '%s'",
         (unsigned long long) ver, (unsigned long long) *(uint64_t*) ver, vm_release_core);
    if (vm_release_core == nullptr) {
        error("JVM version check failed: pointer invalid.");
    }
    // Read from binary:
    char jvm_name[BUFLEN];
    snprintf(jvm_name, BUFLEN - 1, "%s" FILE_SEPARATOR "%s", directory, filename);

    // Location as relative virtual address:
    uint64_t vm_release_relative_vaddr = (uint64_t) ptr - (uint64_t) base_address;
    // Convert address to file offset in binary:
#ifdef WINDOWS
    PEFile pefile(jvm_name);
    uint64_t vm_release_offset = pefile.file_offset_for_reladdr(vm_release_relative_vaddr);
#else
    // In ELF, file offset is just the relative vaddr.
    uint64_t vm_release_offset = vm_release_relative_vaddr;
#endif
    logv("Version check: version binary offset: 0x%lx in %s (size %llud)", vm_release_offset, jvm_filename, file_size(jvm_filename));
    if (vm_release_offset > file_size(jvm_filename)) {
        error("JVM version check failed: pointer invalid.");
    }
    char* vm_release_binary = readstring_at_offset_pd(jvm_name, vm_release_offset);
    logv("Version check: version from binary:  %s", vm_release_binary);

    if (strncmp(vm_release_core, vm_release_binary, BUFLEN) != 0) {
        error("JVM version check failed: mismatch, core '%s', jvm binary '%s'", vm_release_core, vm_release_binary);
    }
}

/**
 * Check presence of 'core.mappings' in revival directory, and copy of JVM.
 * If either are missing, revival cache is rebuilt.
 */
bool revival_cache_exists(char* dirname, const char* mappings_filename) {
    if (!file_exists_pd(mappings_filename)) {
        return false;
    }
    char buf[BUFLEN];
    snprintf(buf, BUFLEN - 1, "%s" FILE_SEPARATOR JVM_FILENAME, dirname);
    if (!file_exists_pd(buf)) {
        return false;
    }
    return true;
}

int revive_image(const char* corename, const char* libdirs, const char* revival_data_path) {
    int e;
    char* dirname;
    if (rdata != nullptr && rdata->vm_thread) {
        warn("revive_image: already called.");
        return -1;
    }
#ifndef WINDOWS
    gettimeofday(&start_time, nullptr);
#else
    start_time = GetTickCount64();
#endif
    if (!file_exists_pd(corename)) {
        warn("revive_image: '%s': file  not found", corename);
        return -1;
    }
    if (!file_canread_pd(corename)) {
        warn("revive_image: '%s': cannot read file", corename);
        return -1;
    }

    // Environment settings: set to anything means "on":
    debugPause = env_check((char*) "REVIVAL_WAIT");
    if (debugPause) {
        warn("REVIVAL_WAIT is set"); // Warn as can be confusing if picked up from environment
    }
    versionCheckEnabled = !env_check((char*) "REVIVAL_SKIPVERSIONCHECK");
    // For logLevel we actually read the env value:
    logLevel = 0;
    if (env_check((char*) "REVIVAL_LOG")) {
        char* logLevelText = getenv("REVIVAL_LOG");
        if (logLevelText != nullptr) {
            if (strcmp("debug", logLevelText) == 0) {
                logLevel = LOG_DEBUG;
            } else if (strcmp("verbose", logLevelText) == 0) {
                logLevel = LOG_VERBOSE;
            }
        }
    }

    init_pd();

    // Record our copy of core file name:
    core_filename = strdup(corename);
    if (core_filename == nullptr) {
        warn("revive: alloc copy of core_filename failed.");
        return -1;
    }
    // Decide core.revival directory name:
    dirname = revival_dirname_create(corename, revival_data_path);
    if (file_exists_pd(dirname) && !file_canread_pd(dirname)) {
        warn("%s: exists but cannot read.", dirname);
        return -1;
    }

    mappings_filename = mappings_filename_create(dirname);

    // Does revival cache exist? (the directory and some content)
    if (!revival_cache_exists(dirname, mappings_filename)) {
        // Create revival data:
        if (!dir_exists_pd(dirname)) {
            if (!create_directory_pd(dirname)) {
                warn("revival: cannot create directory '%s': use -R to specify usable location for cache directory.", dirname);
                return -1;
            }
        }
        logv("Creating revival data cache in directory: %s", dirname);
        e = create_revival_cache_pd(corename, dirname, libdirs);
        logv("revive_image: create_revival_cache_pd returns: %d", e);
        waitHitRet();
        if (e != 0) {
            warn("revive_image: create_revival_cache failed.  Return code: %d", e);
            return e;
        }
    } else {
        logv("Using cached revival data: %s", dirname);
    }

    // Read mappings file: load library, map memory:
    e = mappings_file_read(corename, dirname, mappings_filename);
    if (e < 0) {
        warn("revive_image: mappings_file_read failed: %d", e);
        return -1;
    }

    if (jvm_address == nullptr) {
        warn("No JVM load address."); // Should have been set in mappings_file_read()
        return -1;
    }

    logv("Installing signal handler.");
    install_handler_pd();

    if (!versionCheckEnabled) {
        warn("JVM version check skipped.");
    } else {
        version_check(corename, dirname, JVM_FILENAME, jvm_address, SYM_VM_RELEASE);
    }

    // Preparation done:
    revivaldir = dirname;

    e = revive_image_cooperative();
    if (e < 0) {
        warn("revival: revive_image failed.");
    } else {
        logv("revive_image: OK");
    }
    return e;
}

void* revived_vm_thread() {
    if (!rdata || !rdata->vm_thread) {
        error("revived_vm_thread: call revive_image first.");
    }
    return rdata->vm_thread;
}

void* revived_tty() {
    if (!revivaldir || !rdata) {
        error("revival_tty: call revive_image first.");
    }
    return rdata->tty;
}

int revived_dcmd(const char* command) {
    if (!revivaldir || !rdata) {
        error("revival_dcmd: call revive_image first.");
    }
    void* s = rdata->parse_and_execute;
    if (s == nullptr) {
        error("revived_dcmd: no parse_and_execute in revival data.");
    }
    if (revived_tty() == nullptr) {
        // null tty will cause a crash during DCmd output.
        error("revived_dcmd: tty not set.");
    }

    logv("revived_dcmd: '%s'", command);
    // We can call parse_and_execute like this:
    //   int(*dcmd_parse)(int, void*, const char*, char, void*) = (int(*)(int, void*, const char*, char, void*)) s;
    //   (dcmd_parse)(DCMD_SOURCE, revived_tty(), command, ' ', revived_vm_thread());
    // Or with:
    call5(s, (void*) DCMD_SOURCE, revived_tty(), (void*) command, (void*) ' ', revived_vm_thread());
    return 0;
}
