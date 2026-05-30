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

#ifndef REVIVAL_H
#define REVIVAL_H

#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <time.h>

#include <cinttypes>
#include <list>
#include <new>
#include <set>

#include "segment.hpp"

#define PTR_FORMAT               "0x%016"     PRIxPTR
#define UINTX_FORMAT_X_0         "0x%016"     PRIxPTR
#define SIZE_FORMAT_X_0          "0x%016"     PRIxPTR

#define BUFLEN 2048

// Source param for executing DiagnosticCommand (sync with diagnosticFramework.hpp)
#define DCMD_SOURCE 8

// Filenames
#define MAPPINGS_FILENAME "core.mappings"
#define SYMBOLS_FILENAME "jvm.symbols"
#define REVIVAL_SUFFIX ".revival"

// Essential symbols to resolve are defined in "SYM_" macros.
// These are "C" and common to all platforms:
#define SYM_REVIVE_VM "process_revival"
#define SYM_VM_RELEASE "_s_vm_release_global"

//
// Platform specifics
//

//
// Linux
//
#ifdef LINUX

#include <dlfcn.h>
#include <libgen.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/time.h>

#define JVM_FILENAME "libjvm.so"
#define FILE_SEPARATOR  "/"
#define PATH_SEPARATOR  ":"

//
// Windows
//
#elif defined(WINDOWS)

#include <io.h>
#include <windows.h>

#include "pefile.hpp"

void dump();
void normalize_path_pd(char* s);
void tls_fixup_pd(void* tlsPtr);

#define JVM_FILENAME "jvm.dll"
#define FILE_SEPARATOR  "\\"
#define PATH_SEPARATOR  ";"

#else
#error "revival.hpp: OS Not implemented."
#endif

//
// The process revival interface:
//

// One structure to keep in sync with the JVM:
#define REVIVAL_MAGIC 0x2e6e656b6e617266
#define REVIVAL_VERSION 1
struct revival_data {
  uint64_t magic;
  uint64_t version;
  uint64_t size_this;

  const char* runtime_name;
  const char* runtime_version;
  const char* runtime_vendor_version;
  const char* jdk_debug_level;

  uint64_t tls_index;
  uint64_t initial_time_count; // Linux: clock_gettime MONOTONIC (since system boot)
  uint64_t initial_time_date;  // Linux: time_t since epoch
  double error_time;

  void* vm_thread;
  void* tty;
  void* parse_and_execute;
  void* info1;
  void* info2;
  void* info3;
};

// The revivalhelper tool uses the  functions: revive_image, revived_dcmd, revived_exit

/**
 * Process Revival setup entry point.
 * Given a core file name, create mappings data if necessary, and revive into the current process.
 * Accept optional library search directory, and directory for revival cache directory, which may both be null.
 * Return 0 for success, -1 for failure.
 */
int revive_image(const char* corefile, const char* libdirs, const char* revival_data_path);

/**
 * Invoke the given jcmd operation, e.g. "Thread.print" or a string containing command and parameters
 * (space separacter).
 *
 * Calls into the revived JVM:
 * void DCmd::parse_and_execute(DCmdSource source, outputStream* out, const char* cmdline, char delim, TRAPS)
 */
int revived_dcmd(const char* command);

/**
 * Perform any cleanup after revival operation and exit process.
 */
void revived_exit(int e);

// Exit code signalling caller should retry, e.g. address space clash.
#define EXIT_SUGGEST_RETRY 7

//
// Revival internals:
//
extern int logLevel;            // set from env: REVIVAL_LOG
#define LOG_VERBOSE 1
#define LOG_DEBUG   2

extern int versionCheckEnabled; // set from env: REVIVAL_SKIPVERSIONCHECK

// Revival prep state:
extern char* core_filename;
extern int core_fd;
extern const char* revivaldir;
extern unsigned long long core_timestamp;

extern bool allLibraries;

// Set during actual revival:
extern char* jvm_filename;
extern void* jvm_address;
extern void* h; // Opaque handle to libjvm
extern std::list<Segment> delayedCopySegments;
extern struct revival_data* rdata;

void exitForRetry(); // exit process using above exit code to signal a retry

/**
 * Return true if two address ranges (start, end) are in conflict.
 */
bool clash_range(uint64_t v1, uint64_t v2, uint64_t t1, uint64_t t2);

/**
 * Return true if an address range (start, end) conflicts with a pointer.
 */
bool clash_addr(uint64_t v1, uint64_t v2, uint64_t xaddr);

/**
 * Check if the given vaddr, length appears unwise to map.
 * Return a char* message if a clash is found, or nullptr.
 */
const char* conflict_check_pd(void* vaddr, size_t length);

void install_handler_pd();

char* readstring(int fd);
char* readstring_at_offset_pd(const char* filename, uint64_t offset);
char* readstring_from_core_at_vaddr_pd(const char* filename, uint64_t addr);
#ifdef WINDOWS
uint64_t read_pointer_at_offset_pd(const char* filename, uint64_t offset);
#endif

char* basename_pd(char* path);
bool create_directory_pd(char* dirname);

// Symbol lookup
void* symbol(const char* symbol);
void* symbol_deref(const char* symbol);

/**
 * Platform-specific symbol lookup.
 * Return symbol information as a pointer.
 * Implementations can print platform-specfic error info and then
 * return (void*) -1 on failure.
*/
void* symbol_dynamiclookup_pd(void* h, const char*str);

/**
 * Make a call to an address in a named symbol.  Variants to pass arguments.
 */
void* symbol_call(const char* sym);
void* symbol_call1(const char* sym, void* arg);
void* symbol_call2(const char* sym, void* arg1, void* arg2);
void* symbol_call3(const char* sym, void* arg1, void* arg2, void* arg3);
void* symbol_call4(const char* sym, void* arg1, void* arg2, void* arg3, void* arg4);
void* symbol_call5(const char* sym, void* arg1, void* arg2, void* arg3, void* arg4, void* arg5);

uint64_t align_down(uint64_t ptr, uint64_t mask);
uint64_t align_up(uint64_t ptr, uint64_t mask);

/**
 * Return a mask for alignment, e.g. 0xfff
 */
uint64_t vaddr_alignment_pd();

/**
 * If we know an upper limit on process virtual address, return it, or return 0 if not known.
 */
unsigned long long max_user_vaddr_pd();

/**
 * Platform-specific setup.
 */
void init_pd();

/**
 * Create a memory mapping at some vaddr, of some length in bytes, from a file at some offset.
 * Return address of allocation, or -1 for failure.
*/
void* do_mmap_pd(void* addr, size_t length, char* filename, int fd, size_t offset);

/**
 * Create a memory mapped allocation at a given address, length.
 */
void* do_map_allocate_pd(void* addr, size_t length, int prot);

/**
 * Return the VMThread created.
 */
void* revived_vm_thread();

/**
 * Utilities to return a boolean for file or directory existence.
 */
bool dir_exists_pd(const char* dirname);
bool dir_isempty_pd(const char* dirname);
bool file_exists_pd(const char* filename);
bool file_canread_pd(const char* filename);
bool file_exists_indir_pd(const char* dirname, const char* filename);

char* find_filename_in_libdirs(const char* libdirs, const char* filename);

unsigned long long file_size(const char* filename);

/**
 * Copy dump file bytes to memory.
 * Called directly when mapping core file memory, or later to implement lazy copying
 * in response to a fault.
 */
int revival_mapping_docopy(void* vaddr, size_t length, size_t offset);

int revival_mapping_copy(void* vaddr, size_t length, size_t offset, bool allocate, char* filename, int fd);

int relocate_sharedlib_pd(const char* filename, const void* addr);

/**
 * Populate the revival cache data directory.
 * Copy JVM library, relocate, read core to create mappings list, and symbols.
 * Return zero on success.
 */
int create_revival_cache_pd(const char* corename, const char* dirname, const char* libdirs);

/**
 * Create the named "core.mappings" file and write the header.
 * Return the fd so other code can write the library and memory mapping lines.
 * Return a negative value on error.
 */
int mappings_file_create(const char* filename, const char* corename);

/**
 * Create jvm.symbols file
 * Return the fd so other code can write the symbols lines.
 */
int symbols_file_create(const char* filename);

void clock_fixup_pd(struct revival_data* rdata);

/**
 * Load a shared library.  Return an opaque handle (not the load address), or -1 for error.
 */
void* load_sharedobject_pd(const char* name, void* vaddr);

bool mem_canwrite_pd(void* vaddr, size_t length);

bool can_lazycopy_pd(void* vaddr);

/**
 * Diagnostics:
 */

/**
 * Simple pause for debugging when REVIVAL_WAIT is set in env.
 * Only for when revivalhelper is run manually at command-line, as requires stdin.
 */
void waitHitRet();

// Avoid "error: format string is not a string literal [-Werror,-Wformat-nonliteral]"
// on Mac, but __attribute__ not a feature on MSVC/Windows.
#if !defined(__GNUC__) && !defined(__clang__)
#define __attribute__(x)
#endif
#define ATTRIBUTE_PRINTF(fmt,vargs)  __attribute__((format(printf, fmt, vargs)))

// Write string fully to fd, log if error.
void write0(int fd, const char* buf);

void writef(int fd, const char* format, ...) ATTRIBUTE_PRINTF(2, 3);

// Log to stderr.  Adds timestamp and newline to given message.
// logv, logd and warn and error all call this method.
void log(const char* format, ...) ATTRIBUTE_PRINTF(1, 2);

// Log if we are "verbose".
void logv(const char* format, ...) ATTRIBUTE_PRINTF(1, 2);

// Log if we are "debug".
void logd(const char* format, ...) ATTRIBUTE_PRINTF(1, 2);

// Write to stderr.
void warn(const char* format, ...) ATTRIBUTE_PRINTF(1, 2);

// Write to stderr and exit.
void error(const char* format, ...) ATTRIBUTE_PRINTF(1, 2);

#endif /* REVIVAL_H */
