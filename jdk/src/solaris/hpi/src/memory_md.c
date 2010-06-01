/*
 * Copyright (c) 1995, 2000, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Implementation of primitive memory allocation.
 *
 * The only thing machine dependent about this allocator is how it
 * initially finds all of the possible memory, and how it implements
 * mapChunk() and unmapChunk().
 *
 * This is all pretty simple stuff.  It is not likely to be banged on
 * frequently enough to be a performance issue, unless the underlying
 * primitives are.  Implementing things:
 *
 * HPI function      Solaris   "malloc"    Win32
 * --------------------------------------------------------------------
 * sysMapMem()       mmap()     malloc()   VirtualAlloc(...MEM_RESERVE...)
 * sysUnMapMem()     munmap()   free()     VirtualFree(...MEM_RESERVE...)
 * sysCommitMem()    no-op      no-op      VirtualAlloc(...MEM_COMMIT...)
 * sysDecommitMem()  no-op      no-op      VirtualFree(...MEM_COMMIT...)
 *
 * Memory mapping is the default, but compiling with -DUSE_MALLOC gives
 * a system based on malloc().
 */

#include <sys/types.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>      /* For perror() */
#include <string.h>
#include <malloc.h>

#include "hpi_impl.h"

#ifndef USE_MALLOC

#include <sys/mman.h>
#include <fcntl.h>
#ifdef __linux__
#ifndef MAP_ANONYMOUS
static int devZeroFD;
#endif
#else
static int devZeroFD;
#endif

#endif /* !USE_MALLOC */

#ifdef __linux__
#ifndef MAP_FAILED
#define MAP_FAILED ((caddr_t)-1)
#endif
static size_t memGrainSize;     /* A page for Linux */
#else
static unsigned int memGrainSize;       /* A page for Solaris */
#endif

/*
 * Mem size rounding is done at this level.  The calling code asks
 * these routines for literally what it thinks it wants.  The size is
 * rounded up to the first multiple of memGrainSize that contains
 * requestedSize bytes.
 */

static long
roundUpToGrain(long value)
{
    return (value + memGrainSize - 1) & ~(memGrainSize - 1);
}

static long
roundDownToGrain(long value)
{
    return value & ~(memGrainSize - 1);
}

void
InitializeMem(void)
{
    static int init = 0;

    if (init) {
        return;         /* Subsequent calls are no-ops */
    }

    /*
     * Set system-specific variables used by mem allocator
     */
    if (memGrainSize == 0) {
        memGrainSize = (int) sysconf(_SC_PAGESIZE);
    }

#ifdef __linux__
#if !defined(USE_MALLOC) && !defined(MAP_ANONYMOUS)
    devZeroFD = open("/dev/zero", O_RDWR);
    if (devZeroFD == -1) {
        perror("devzero");
        exit(1);
    }
#endif /* !USE_MALLOC MAP_ANONYMOUS*/
#else
#ifndef USE_MALLOC
    devZeroFD = open("/dev/zero", O_RDWR);
    if (devZeroFD == -1) {
        perror("devzero");
        exit(1);
    }
#endif /* !USE_MALLOC */
#endif

    init = 1;           /* We're initialized now */
}


#ifndef USE_MALLOC

#define PROT_ALL (PROT_READ|PROT_WRITE|PROT_EXEC)

#ifndef MAP_NORESERVE
#define MAP_NORESERVE 0
#endif

/*
 * Map a chunk of memory.  Return the address of the base if successful,
 * 0 otherwise.  We do not care where the mapped memory is, and can't
 * even express a preference in the current HPI.  If any platforms
 * require us to manage addresses of mapped chunks explicitly, that
 * must be done below the HPI.
 */
static char *
mapChunk(long length)
{
    char *ret;

#if defined(__linux__) && defined(MAP_ANONYMOUS)
     ret = (char *) mmap(0, length, PROT_ALL,
                         MAP_NORESERVE | MAP_PRIVATE | MAP_ANONYMOUS,
                         -1, (off_t) 0);
#else
    ret = (char *) mmap(0, length, PROT_ALL, MAP_NORESERVE|MAP_PRIVATE,
                   devZeroFD, (off_t) 0);
#endif
    return (ret == MAP_FAILED ? 0 : ret);
}

/*
 * Map a chunk of memory at a specific address and reserve swap space
 * for it.  This is currently only used to remap space previously mapped
 * MAP_NORESERVE, reserving swap and getting native error handling.  We
 * assume that all alignment and rounding has been done by the caller.
 * Return 1 if successful and 0 otherwise.
 */
static char *
mapChunkReserve(char *addr, long length)
{
    char *ret;
#if defined(__linux__) && defined(MAP_ANONYMOUS)
     ret = (char *) mmap(addr, length, PROT_ALL,
                         MAP_FIXED | MAP_PRIVATE | MAP_ANONYMOUS,
                         -1, (off_t) 0);
#else
    ret = (char *) mmap(addr, length, PROT_ALL, MAP_FIXED|MAP_PRIVATE,
                        devZeroFD, (off_t) 0);
#endif
    return (ret == MAP_FAILED ? 0 : ret);
}

/*
 * Map a chunk of memory at a specific address and reserve swap space
 * for it.  This is currently only used to remap space previously mapped
 * MAP_RESERVE, unreserving swap and getting native error handling.  We
 * assume that all alignment and rounding has been done by the caller.
 * Return 1 if successful and 0 otherwise.
 */
static char *
mapChunkNoreserve(char *addr, long length)
{
    char *ret;

#if defined(__linux__) && defined(MAP_ANONYMOUS)
     ret = (char *) mmap(addr, length, PROT_ALL,
                       MAP_FIXED | MAP_PRIVATE |
                         MAP_NORESERVE | MAP_ANONYMOUS,
                         -1, (off_t) 0);
#else
    ret = (char *) mmap(addr, length, PROT_ALL,
                        MAP_FIXED|MAP_PRIVATE|MAP_NORESERVE,
                        devZeroFD, (off_t) 0);
#endif
    return (ret == MAP_FAILED ? 0 : ret);
}

/*
 * Unmap a chunk of memory.  Return 1 if successful, 0 otherwise.  We
 * currently don't do any alignment or rounding, assuming that we only
 * will unmap chunks that have previously been returned by mapChunk().
 */
static int
unmapChunk(void *addr, long length)
{
    return (munmap(addr, length) == 0);
}

#endif /* !USE_MALLOC */


/* HPI Functions: */

/*
 * Map a range of virtual memory.  Note that the size asked for here
 * is literally what the upper level has asked for.  We need to do
 * any rounding, etc. here.  If mapping fails return 0, otherwise
 * return the address of the base of the mapped memory.
 */
void *
sysMapMem(size_t requestedSize, size_t *mappedSize)
{
    void *mappedAddr;

    *mappedSize = roundUpToGrain(requestedSize);
#ifdef USE_MALLOC
    mappedAddr = (void *) sysMalloc(*mappedSize); /* Returns 0 on failure */
#ifdef __linux__
     if (mappedAddr) {
       memset(mappedAddr, 0, *mappedSize);
       mappedAddr = (void *) roundUpToGrain(mappedAddr);
     }
#endif
#else
    mappedAddr = mapChunk(*mappedSize);           /* Returns 0 on failure */
#endif /* USE_MALLOC */
    if (mappedAddr) {
        Log3(2, "sysMapMem: 0x%x bytes at 0x%x (request: 0x%x bytes)\n",
             *mappedSize, mappedAddr, requestedSize);
    } else {
        Log1(2, "sysMapMem failed: (request: 0x%x bytes)\n", requestedSize);
    }
    return mappedAddr;
}

/*
 * Unmap a range of virtual memory.  Note that the size asked for here
 * is literally what the upper level has asked for.  We need to do any
 * rounding, etc. here.  If unmapping fails return 0, otherwise return
 * the address of the base of the unmapped memory.
 */
void *
sysUnmapMem(void *requestedAddr, size_t requestedSize, size_t *unmappedSize)
{
    void *unmappedAddr;
    int ret;

    *unmappedSize = roundUpToGrain(requestedSize);
#ifdef USE_MALLOC
    sysFree(requestedAddr);
    ret = 1;
#else
    ret = unmapChunk(requestedAddr, *unmappedSize);
#endif /* USE_MALLOC */
    if (ret) {
        unmappedAddr = requestedAddr;
        Log4(2,
             "sysUnmapMem: 0x%x bytes at 0x%x (request: 0x%x bytes at 0x%x)\n",
             *unmappedSize, unmappedAddr, requestedSize, requestedAddr);
    } else {
        unmappedAddr = 0;
        Log2(2, "sysUnmapMem failed: (request: 0x%x bytes at 0x%x)\n",
             requestedSize, requestedAddr);
    }
    return unmappedAddr;
}

/*
 * Commit/decommit backing store to a range of virtual memory.  This range
 * needs not be identical to a mapped range, but must be a subset of one.
 * On Solaris, we remap the range to reserve swap for the space on
 * commit.  We don't strictly need to do this, as Solaris will demand
 * page pages that we've mapped when we want to access them.  But by
 * reserving swap we get reasonable error handling for free where we'd
 * otherwise end up getting a SIGBUS on a random write when we run out
 * of swap.  It also emphasizes the general need for shared code to
 * postpone committing to mapped memory for as long as is feasible.
 * When Java really needs space (the thread stacks excepted), it will
 * soon write over it (heap, markbits), so we don't really get much from
 * demand paging.
 *
 * We do not validate that commitment requests cover already-mapped
 * memory, although in principle we could.  The size asked for here
 * is what the upper level has asked for.  We need to do any platform-
 * dependent rounding here.
 *
 * When you commit, you commit to the entire page (or whatever quantum
 * your O/S requires) containing the pointer, and return the beginning of
 * that page.  When you decommit, you decommit starting at the next page
 * *up* from that containing the pointer, except that decommitting from
 * a pointer to the beginning of the page operates on that page.
 */

/*
 * Return the address of the base of the newly committed memory, or 0
 * if committing failed.
 */
void *
sysCommitMem(void *requestedAddr, size_t requestedSize, size_t *committedSize)
{
    void *committedAddr;
    char *ret;

    *committedSize = roundUpToGrain(requestedSize);
    committedAddr = (void *) roundDownToGrain((long) requestedAddr);
#ifdef USE_MALLOC
#ifdef __linux__
    ret = committedAddr;
#else
    ret = requestedAddr;
#endif
#else
    ret = mapChunkReserve(committedAddr, *committedSize);
#endif
    if (ret) {
        committedAddr = ret;
        Log4(2,
    "sysCommitMem: 0x%x bytes at 0x%x (request: 0x%x bytes at 0x%x)\n",
             *committedSize, committedAddr, requestedSize, requestedAddr);
    } else {
        committedAddr = 0;
        Log2(2, "sysCommitMem failed: (request: 0x%x bytes at 0x%x)\n",
             requestedSize, requestedAddr);
    }

    return committedAddr;
}

/*
 * Return the address of the base of the newly decommitted memory, or 0
 * if decommitting failed.
 */
void *
sysDecommitMem(void *requestedAddr, size_t requestedSize,
               size_t *decommittedSize)
{
    void *decommittedAddr;
    char *ret;

    *decommittedSize = roundDownToGrain(requestedSize);
    decommittedAddr = (void *) roundUpToGrain((long) requestedAddr);
#ifdef USE_MALLOC
    ret = 0;
#else
    ret = mapChunkNoreserve(decommittedAddr, *decommittedSize);
#endif
    Log4(2,
         "sysDecommitMem: 0x%x bytes at 0x%x (request: 0x%x bytes at 0x%x)\n",
         *decommittedSize, decommittedAddr, requestedSize, requestedAddr);

    return ret;
}

/*
 * Allocate memory on an alignment boundary.  Returns aligned
 * pointer to new memory.  Use sysFreeBlock to free the block.
 *
 * sysAllocBlock() is similar to memalign(), except that it also
 * returns a pointer to the beginning of the block returned by the
 * OS, which must be used to deallocate the block.  (On some OSes,
 * these two won't be the same.)  sysAllocBlock() is also more
 * limited than memalign in that it can only be used to allocate
 * on particular alignments (PAGE_ALIGNMENT) and should be assumed
 * to round the sizes of allocated blocks up to multiples of the
 * alignment value (PAGE_ALIGNMENT*n bytes).
 */
void *
sysAllocBlock(size_t size, void** allocHead)
{
    void* alignedPtr = memalign(PAGE_ALIGNMENT, size);
    *allocHead = alignedPtr;
    return alignedPtr;
}

/*
 * Wrapper to free block allocated by sysMemAlign.
 */
void
sysFreeBlock(void *allocHead)
{
    free(allocHead);
}

void * sysMalloc(size_t s)
{
    if (s == 0)
        return malloc(1);
    return malloc(s);
}

void * sysRealloc(void *p, size_t s)
{
    return realloc(p, s);
}

void sysFree(void *p)
{
    if (p != NULL)
        free(p);
}

void * sysCalloc(size_t s1, size_t s2)
{
    if (s1 == 0 || s2 == 0)
        return calloc(1, 1);
    return calloc(s1, s2);
}

char * sysStrdup(const char * string)
{
    return strdup(string);
}
