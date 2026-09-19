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

#include <dirent.h>
#include <dlfcn.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <libgen.h>
#include <signal.h>
#include <cassert>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include <sys/mman.h>
#include <sys/sendfile.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>

#include <list>
#include <array>

// For dlinfo:
#define _GNU_SOURCE 1
#include <link.h>
#include <dlfcn.h>

#include "revival.hpp"
#include "elffile.hpp"

long vaddr_align;
uint64_t bin_addr;
uint64_t bin_end;
#define LIBC_NAME "libc.so.6"
uint64_t libc_addr;
uint64_t libc_end;
uint64_t heap_end;

char* basename_pd(char* s) {
    return basename(s);
}

uint64_t vaddr_alignment_pd() {
    return vaddr_align;
}

unsigned long long max_user_vaddr_pd() {
    return 0xffff800000000000;
}

/**
 * Return the actual load address for a shared object, given its opaque handle
 * (the value returned from dlopen).
 * More specifically this returns the difference from the preferred address.
 * For a file with no preferred address, that is the loaded address.
 * For a file with a specific address, will return zero if loaded at that address.
 */
void* base_address_for_sharedobject_live(void* h) {
    struct link_map lm;
    struct link_map* lp = &lm;
    struct link_map** lpp = &lp;

    int e = dlinfo(h, RTLD_DI_LINKMAP, (struct link_map **) lpp);
    if (e == -1) {
        warn("base_address_for_sharedobject_live: dlinfo error %d: %s", e, dlerror());
        return (void*) -1;
    }
    return (void*) (*lpp)->l_addr;
}

// Holder struct for dl_iterate_phdr
struct ph_data {
    const char* name;
    uint64_t end;
};

// Callback function for dl_iterate_phdr
int ph_func(struct dl_phdr_info* info, size_t size, void* data) {
    struct ph_data* d = (struct ph_data*) data;
    if (!info->dlpi_name || strcmp(info->dlpi_name, d->name) != 0) {
        return 0;
    }
    logd("ph_func found %s", d->name);
    for (int i = 0; i < info->dlpi_phnum; i++) {
        Elf64_Phdr* ph = (Elf64_Phdr*) &info->dlpi_phdr[i];
        uint64_t end = (uint64_t) info->dlpi_addr + ph->p_vaddr + ph->p_memsz;
        if (end > d->end) {
            d->end = end;
        }
        logd("end: 0x%lx", d->end);
    }
    return 1;
}

uint64_t end_address_for_sharedobject_live(void* h) {
    struct link_map *lm = nullptr;
    if (dlinfo(h, RTLD_DI_LINKMAP, &lm) != 0 || lm == nullptr) {
       return 0;
    }
    struct ph_data x;
    x.name = lm->l_name;
    x.end = 0;
    dl_iterate_phdr(ph_func, &x);
    return x.end;
}

void init_pd() {
    // Check LD_USE_LOAD_BIAS is set:
    char* env = getenv("LD_USE_LOAD_BIAS");
    if (env == nullptr || strncmp(env, "1", 1) != 0) {
        error("Error: LD_USE_LOAD_BIAS not set.");
    }

    long value = sysconf(_SC_PAGESIZE); // Expect 0x1000
    if (value < 1) {
        warn("init_pd: sysconf retuns 0x%lx: %s", value, strerror(errno));
        value = 0x1000;
    }
    vaddr_align = value;
    logv("revival: init_pd: vaddr_alignment (pagesize) = 0x%llx", (unsigned long long) vaddr_alignment_pd());

    // Addresses for later conflict checking.
    void* h = dlopen(nullptr, RTLD_NOW | RTLD_LOCAL);
    if (h == nullptr) {
        warn("init_pd: dlopen failed");
        bin_addr = 0;
    } else {
        // Main binary has dynamic load address.
        bin_addr = (uint64_t) base_address_for_sharedobject_live(h);
        logv("revivalhelper: binary address: 0x%lx", bin_addr);
        bin_end = (uint64_t) end_address_for_sharedobject_live(h);
        logv("revivalhelper: binary end: 0x%lx", bin_end);
    }
    h = dlopen(LIBC_NAME, RTLD_NOW | RTLD_LOCAL);
    if (h == nullptr) {
        warn("init_pd: dlopen " LIBC_NAME " failed");
        libc_addr = 0;
    } else {
        libc_addr = (uint64_t) base_address_for_sharedobject_live(h);
        logv("revivalhelper: libc address: 0x%lx", libc_addr);
        libc_end = (uint64_t) end_address_for_sharedobject_live(h);
        // libc data area will be fairly predictable but add a generous margin.
        libc_end += 0x200000;
        logv("revivalhelper: libc end: 0x%lx", libc_end);
    }
    // Here in the throwaway revivalhelper launcher app, we expect low native heap usage,
    // no large/mmap allocations. Traditional program break is adequate.
    heap_end = (uint64_t) sbrk(0) + 0x100000;
    logv("revivalhelper: heap end: 0x%lx", heap_end);
}

const char* conflict_check_pd(void* vaddr, size_t length) {
    int x;
    if (clash_range((uint64_t) vaddr, length, (uint64_t) &x - 0x4000, (uint64_t) &x + 0x4000)) {
        return "conflict with local/stack";
    }
    uint64_t vaddr_end = (uint64_t) vaddr + (uint64_t) length;
    if (bin_addr !=0 && clash_range((uint64_t) vaddr, vaddr_end, bin_addr, bin_end)) {
        return "conflict with this binary";
    }
    if (libc_addr != 0 && clash_range((uint64_t) vaddr, vaddr_end, libc_addr, libc_end)) {
        return "conflict with libc";
    }
    // Checking from base of binary to end of heap is simple, if slightly over-cautious:
    if (heap_end != 0 && clash_range((uint64_t) vaddr, vaddr_end, bin_addr, heap_end)) {
        return "conflict with live c heap";
    }
    return nullptr;
}

bool dir_exists_pd(const char* dirname) {
    int fd = open(dirname, O_DIRECTORY);
    if (fd < 0) {
        if (errno != ENOENT) {
            warn("Checking directory exists: '%s': %d: %s", dirname, errno, strerror(errno));
        }
    } else {
        close(fd);
        return true;
    }
    return false;
}

bool dir_isempty_pd(const char* dirname) {
    int count = 0;
    DIR* dir = opendir(dirname);
    if (dir != nullptr) {
        struct dirent* ent;
        while (true) {
            ent = readdir(dir);
            if (ent == nullptr) {
                break;
            }
            count++;
        }
        closedir(dir);
        return count <= 2;
    }
    return false;
}

bool file_exists_pd(const char* filename) {
    int fd = open(filename, O_RDONLY);
    if (fd < 0) {
        logv("%s: error %d: %s", filename, errno, strerror(errno));
        if (errno != ENOENT) {
            return true; // Exists, but e.g. no permission.
        } else {
            return false;
        }
    } else {
        close(fd);
        return true;
    }
}

bool file_canread_pd(const char* filename) {
    int fd = open(filename, O_RDONLY);
    if (fd < 0) {
        return false;
    } else {
        close(fd);
        return true;
    }
}

bool file_exists_indir_pd(const char* dirname, const char* filename) {
    char path[BUFLEN];
    snprintf(path, BUFLEN - 1, "%s%s%s", dirname, FILE_SEPARATOR, filename);
    return file_exists_pd(path);
}

char* readstring_at_offset_pd(const char* filename, uint64_t offset) {
    int fd = open(filename, O_RDONLY);
    if (fd < 0) {
        warn("cannot open %s", filename);
        return (char*) -1;
    }
    off_t pos = lseek(fd, offset, SEEK_SET);
    if (pos < 0) {
        warn("readstring_at_offset_pd: %s: lseek(%ld) fails %d : %s", filename, offset, errno, strerror(errno));
        close(fd);
        return (char*) -1;
    }
    char* s = readstring(fd);
    close(fd);
    return s;
}

char* readstring_from_core_at_vaddr_pd(const char* filename, uint64_t addr) {
    ELFFile elf(filename);
    if (!elf.is_valid()) {
        error("readstring_from_core_at_vaddr_pd: %s invalid", filename);
        return nullptr;
    } else {
        return elf.readstring_at_address(addr);
    }
}

bool mem_canwrite_pd(void* vaddr, size_t length) {
    return true;
}

bool can_lazycopy_pd(void* addr) {
    return true;
}

void* do_mmap_pd(void* addr, size_t length, char* filename, int fd, size_t offset) {
    int flags = MAP_PRIVATE | MAP_FIXED;
    int prot = PROT_READ | PROT_WRITE | PROT_EXEC;
    // Values given should simply work for a regular Linux core file.
    // Failure with EINVAL is expected on a Linux gcore (gdb) due to unaligned file offsets.
    void* e = mmap(addr, length, prot, flags, fd, offset);
    if (e == (void*) -1L) {
        logv("do_mmap_pd: mmap(%p, %zu, %d, %d, %d, file offset 0x%lx) failed: errno = %d: %s",
            addr, length, prot, flags, fd, offset, errno, strerror(errno));
    }
    return e;
}

void* do_map_allocate_pd(void* vaddr, size_t length, int prot) {
    if (prot == 1) {
        prot = PROT_READ | PROT_WRITE | PROT_EXEC;
    } else {
        prot = PROT_NONE;
    }
    int flags = MAP_ANONYMOUS | MAP_PRIVATE | MAP_FIXED | MAP_NORESERVE;
    void* h = mmap(vaddr, length, prot, flags, -1, 0);
    logv("do_map_allocate: mmap(%p, %zu, %d, %d, -1, 0) returns: %p", vaddr, length, prot, flags, h);
    if (h == (void*) -1) {
        warn("do_map_allocate: mmap(%p, %zu, %d, %d, -1, 0) failed: returns: %p: errno = %d: %s",
             vaddr, length, prot, flags, h, errno, strerror(errno));
    }
    return h;
}

void* symbol_dynamiclookup_pd(void* h, const char* str) {
    void* s = dlsym(RTLD_NEXT, str);
    logv("symbol_dynamiclookup: %s = %p", str, s);
    if (s == 0) {
        if (logLevel >= LOG_VERBOSE) {
            warn("dlsym: %s", dlerror());
        }
        return (void*) -1;
    }
    return s;
}


/**
 * Create a file name for the core page file, in the revivaldir.
 * Delete any existing file, otherwise it grows without limit.
 */
const char* createTempFilename() {
    char* tempName  = (char*) calloc(1, BUFLEN); // never free'd
    if (tempName == nullptr) {
        error("createTempFilename: calloc failed");
    }
    char* p = strncat(tempName, revivaldir, BUFLEN - 1);
    p = strncat(p, "/revivaltemp", BUFLEN - 1);
    logv("core page file: '%s'", tempName);
    int fdTemp = open(tempName, O_WRONLY | O_CREAT | O_EXCL, 0600);
    if (fdTemp < 0) {
        if (errno == EEXIST) {
            logv("revival: remove existing core page file '%s'", tempName);
            int e = unlink(tempName);
            if (e < 0) {
                warn("revival: remove existing core page file failed: %d", e);
            }
            fdTemp = open(tempName, O_WRONLY | O_CREAT | O_EXCL, 0600);
            if (fdTemp < 0) {
                error("cannot remove open existing core page file '%s': %d", tempName, fdTemp);
            }
        }
    }
    return tempName;
}

/**
 * Open a named file and append data for a Segment from the core.
 * Return the offset at which we wrote, or negative on error.
 */
size_t writeTempFileBytes(const char* tempName, Segment seg) {
    int fdTemp = open(tempName, O_WRONLY | O_APPEND);
    if (fdTemp < 0) {
        return fdTemp;
    }
    off_t pos = lseek(fdTemp, 0, SEEK_END);
    if (pos < 0) {
        warn("writeTempFileBytes: lseek fails %d : %s", errno, strerror(errno));
        close(fdTemp);
    }
    // Write bytes
    size_t s = write(fdTemp, seg.vaddr, seg.length);
    if (s != seg.length) {
        warn("writeTempFileBytes: written %d of %d", (int) s, (int) seg.length);
    }
    close (fdTemp);
    return (size_t) pos;
}

/**
 * Signal handler.
 * Catch errors and do mapping of writable areas on demand.
 */
void handler(int sig, siginfo_t* info, void* ucontext) {
    void* addr  = (void*) info->si_addr;
    logv("revival: handler: sig = %d for address %p", sig, addr);
    if (addr == nullptr) {
        warn("handler: null address");
        abort();
    }

    // Catch access to areas that need copying in:
    std::list<Segment>::iterator iter;
    for (iter = delayedCopySegments.begin(); iter != delayedCopySegments.end(); iter++) {
        if (iter->contains((uint64_t) addr)) {
            logv("Delayed Copy Segment: si_addr = %p found in segment %p", addr, iter->vaddr);
            // Set mapping permissions and copy data now:
            int e = mprotect(iter->vaddr, iter->length, PROT_READ | PROT_WRITE | PROT_EXEC);
            if (e < 0) {
                error("revival: mprotect failed: %d", e);
            }
            revival_mapping_docopy(iter->vaddr, iter->length, iter->file_offset);
            return;
        }
    }
    warn("revival: handler: si_addr = %p : not handled.", addr);
    if (logLevel >= LOG_DEBUG) {
        abort();
    }
    exitForRetry();
}

/**
 * Install the signal hander.
 */
void install_handler_pd() {
    struct sigaction sa, old_sa;
    sigfillset(&sa.sa_mask);
    sa.sa_sigaction = handler;
    sa.sa_flags = SA_SIGINFO|SA_RESTART;
    int e = sigaction(SIGSEGV, &sa, &old_sa);
    if (e) {
        warn("sigaction SIGSEGV: %d", e);
    }
    e = sigaction(SIGBUS, &sa, &old_sa);
    if (e) {
        warn("sigaction SIGBUS: %d", e);
    }
}

/**
 * Use dlopen to load a sharedobject.
 * Verify the base address of the loaded library is as requested, if possible.
 *
 * Return the opaque handle from dlopen, which is not the load address.
 * Return -1 for error.
 */
void* load_sharedobject_pd(const char* name, void* vaddr) {
    void* actual = nullptr;
    void* h = dlopen(name,  RTLD_NOW | RTLD_GLOBAL);
    if (!h) {
        warn("load_sharedobject_pd: dlopen failed: %s: %s", name, dlerror());
        return (void*) -1;
    }
    actual = base_address_for_sharedobject_live(h);
    logv("load_sharedobject_pd: actual = %p", actual);
    if (actual != (void*) 0 && actual != vaddr) {
        // Wrong address:
        // Most likely, Address Space Layout Randomisation has given us an inhospitable layout,
        // e.g. libc where we want to have libjvm.
        // Trying dlclose and forcing retry is not successful.
        // Terminate, for calling process to retry:
        exitForRetry();
    }
    return h;
}

#define NANOS_PER_SECOND 1000000000

// Set value to be returned by interposed clock_gettime in revival support library (preloaded).
void clock_fixup_pd(struct revival_data* rdata) {
    void (*func)(unsigned long long) = (void(*)(unsigned long long)) dlsym(RTLD_NEXT, "set_revival_time_ns");
    if (func != nullptr) {
        double lifetime_s = 0;
        if (rdata->error_time > 0) {
            logv("revive_image: using JVM first error time"); // ...which is better than relying on core file timestamp.
            lifetime_s = rdata->error_time;
        } else {
            logv("revive_image: using core timestamp");
            if (core_timestamp == 0) {
                warn("core timestamp not found in revival cache data");
            } else {
                lifetime_s = core_timestamp - rdata->initial_time_date;
            }
        }
        if (lifetime_s != 0) {
            func((lifetime_s * NANOS_PER_SECOND) + (rdata->initial_time_count));
        }
    } else {
        // Function lookup failed, e.g. revivalhelper invoked directly without preload.
        logv("set_revival_time_ns: symbol lookup failed.");
    }
}

bool copy_file_pd(const char* srcfile, const char* destfile) {
    bool result = false;
    ssize_t count;
    ssize_t e;
    int fd_src;
    int fd_dest = 0;

    fd_src = open(srcfile, O_RDONLY, S_IRUSR);
    if (fd_src < 0) {
        warn("Cannot open source file %s: %s", srcfile, strerror(errno));
        goto out;
    }
    fd_dest = open(destfile, O_CREAT | O_WRONLY | O_TRUNC, S_IRUSR | S_IWUSR);
    if (fd_dest < 0) {
        close(fd_src);
        warn("Cannot open destination file %s: %s", destfile, strerror(errno));
        goto out;
    }
    count = (ssize_t) file_size(srcfile);
    e = sendfile(fd_dest, fd_src, 0, count);
    if (e != count) {
        warn("copy_file_pd: requested copy %ld bytes: got %s", count, strerror(errno));
    } else {
        result = true;
    }
out:
    close(fd_src);
    close(fd_dest);
    return result;
}

void relocate_sharedlib_pd(const char* filename, const uint64_t address) {
    ELFFile lib(filename, nullptr, true);
    if (lib.is_sharedlib()) {
        logv("Relocate %s to 0x%lx", filename, address);
        lib.relocate(address /* assume library currently has zero base address */);
        logv("Relocate done");
    }
}

void write_mem_mappings(ELFFile& core, int mappings_fd) {
    core.write_mem_mappings(mappings_fd);
}


const int N_JVM_SYMS = 2;
const char* JVM_SYMS[N_JVM_SYMS] = {
    SYM_REVIVE_VM, SYM_VM_RELEASE
};

void write_symbols(int symbols_fd, const char* symbols[], int count, const char* revival_dirname) {
    char buf[BUFLEN];
    memset(buf, 0, BUFLEN);
    strncpy(buf, revival_dirname, BUFLEN - 1);
    strncat(buf, "/" JVM_FILENAME, BUFLEN - 1);
    ELFFile lib_copy(buf);
    lib_copy.write_symbols(symbols_fd, symbols, count);
}

int open_for_read(const char* filename) {
    int fd = open(filename, O_RDONLY);
    if (fd < 0) {
        warn("Cannot open %s: %s", filename, strerror(errno));
        return -1;
    }
    return fd;
}

int open_for_read_and_write(const char* filename) {
    int fd = open(filename, O_RDWR);
    if (fd < 0) {
        warn("Cannot open %s: %s", filename, strerror(errno));
        return -1;
    }
    return fd;
}

bool create_directory_pd(char* dirname) {
    return mkdir(dirname, S_IRUSR | S_IWUSR | S_IXUSR) == 0;
}

void write_sharedlib_mapping(int mappings_fd, char* filename, void* address) {
        ELFFile elf(filename);
        if (!elf.is_sharedlib()) {
            return;
        }
        char buf[BUFLEN];
        const char* checksum = "0";
        snprintf(buf, BUFLEN, "L %s %llx %s\n", basename(filename), (unsigned long long) address, checksum);
        write0(mappings_fd, buf);
}

void write_sharedlib_mappings(ELFFile& core, int mappings_fd) {
    logv("write_sharedlib_mappings");

    if (!allLibraries) {
        Segment* jvm_mapping = core.get_file_mapping(JVM_FILENAME);
        write_sharedlib_mapping(mappings_fd, jvm_mapping->name, jvm_mapping->vaddr);
    } else {
        std::list<Segment> libs = core.get_sharedlib_mappings();
        std::list<Segment>::iterator iter;
        for (iter = libs.begin(); iter != libs.end(); iter++) {
            write_sharedlib_mapping(mappings_fd, iter->name, iter->vaddr);
        }
    }
}

void copy_and_relocate(const char* srcfile, const char* destdir, uint64_t address) {
    char copy_path[BUFLEN];

    ELFFile elf(srcfile);
    if (!elf.is_sharedlib()) {
        logv("copy_and_relocate: not an ELF sharedlib: %s", srcfile);
        return;
    }
    memset(copy_path, 0, BUFLEN);
    strncpy(copy_path, destdir, BUFLEN - 1);
    strncat(copy_path, "/", BUFLEN - 1);
    char* basefilename = basename((char*) srcfile);
    strncat(copy_path, basefilename, BUFLEN - 1);
    logv("Copying %s to %s", srcfile, copy_path);
    if (!copy_file_pd(srcfile, copy_path)) {
        return;
    }

    // Copy .debuginfo if present:
    char debuginfo_path[BUFLEN];
    char debuginfo_copy_path[BUFLEN];
    snprintf(debuginfo_path, BUFLEN, "%s", srcfile);
    char* p = strstr(debuginfo_path, ".so");
    if (p != nullptr) {
        snprintf(p, BUFLEN, ".debuginfo"); // Append to debuginfo_path in place of .so
        if (file_exists_pd(debuginfo_path)) {
            snprintf(debuginfo_copy_path, BUFLEN - 1, "%s/%s.debuginfo", destdir, basefilename);
            logv("Copying debuginfo %s to %s", debuginfo_path, debuginfo_copy_path);
            copy_file_pd(debuginfo_path, debuginfo_copy_path);
        }
    }
    // Relocate the copy: address of 0 will avoid any relocation.
    if (address != 0) {
        relocate_sharedlib_pd(copy_path, address);
    }
}

void copy_and_relocate(ELFFile core, const char* destdir) {
    logv("copy_and_relocate: all=%d", allLibraries);
    if (!allLibraries) {
        Segment* jvm_mapping = core.get_file_mapping(JVM_FILENAME);
        copy_and_relocate(jvm_mapping->name, destdir, (uint64_t) jvm_mapping->vaddr);
    } else {
        std::list<Segment> libs = core.get_sharedlib_mappings();
        std::list<Segment>::iterator iter;
        for (iter = libs.begin(); iter != libs.end(); iter++) {
            copy_and_relocate(iter->name, destdir, (uint64_t) iter->vaddr);
        }
    }
}

/**
 * Create a "core.revival" directory containing what's needed to revive a corefile:
 *
 *  - A copy of libjvm.so, which this method then relocates to load at the same address as it was in the corefile
 *  - "core.mappings" a text file with instructions on which segments to load from the core
 *  - "jvm.symbols" a text file with information about important symbols in libjvm.so
 *
 * Also take a copy of libjvm.debuginfo if present.
 */
int create_revival_cache_pd(const char* corename, const char* revival_dirname, const char* libdirs) {
    logv("create_revival_cache_pd");

    ELFFile core(corename, libdirs);
    if  (!core.is_core()) {
        error("Not a core file: %s", corename);
    }

    // Find JVM and its load address from core.
    Segment* jvm_mapping = core.get_file_mapping(JVM_FILENAME);
    if (jvm_mapping == nullptr) {
        warn("JVM library not found in core.");
        error("For cores from other systems, or if JDK at path in core has changed, use -L to specify JVM location.");
    }
    logv("JVM = '%s'", jvm_mapping->name);
    logv("JVM addr = %p", jvm_mapping->vaddr);
    if (!file_exists_pd(jvm_mapping->name)) {
        error("No file for JVM '%s'", jvm_mapping->name);
    }

    // Create mappings file:
    int mappings_fd = mappings_file_create(revival_dirname, corename);
    if (mappings_fd < 0) {
        error("Failed to create mappings file.");
    }
    // Write mappings file:
    write_sharedlib_mappings(core, mappings_fd);
    write_mem_mappings(core, mappings_fd);
    close(mappings_fd);

    // Copy jvm/libraries into core.revival dir
    copy_and_relocate(core, revival_dirname);

    // Create symbols file
    int symbols_fd = symbols_file_create(revival_dirname);
    if (symbols_fd < 0) {
        error("Failed to create symbols file");
    }
    logv("Write symbols");
    write_symbols(symbols_fd, JVM_SYMS, N_JVM_SYMS, revival_dirname);
    close(symbols_fd);
    logv("Write symbols done");

    logv("create_revival_cache_pd returning %d", 0);
    return 0;
}

