/*
 * Copyright (c) 1995, 2002, Oracle and/or its affiliates. All rights reserved.
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

#include <windows.h>
#include <sys/types.h>

#include "hpi_impl.h"

static size_t
roundUp(size_t n, size_t m)
{
    return (n + m - 1) & ~(m - 1);
}

static size_t
roundDown(size_t n, size_t m)
{
    return n & ~(m - 1);
}

#define RESERVE_SIZE 65536      /* Memory is reserved in 64KB chunks */

static size_t pageSize;         /* Machine page size */

void
InitializeMem()
{
    SYSTEM_INFO si;

    GetSystemInfo(&si);
    pageSize = si.dwPageSize;
}

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

#ifdef USE_MALLOC
    *mappedSize = roundUp(requestedSize, pageSize);
    mappedAddr = (void *)malloc(*mappedSize);
#else
    *mappedSize = roundUp(requestedSize, RESERVE_SIZE);
    mappedAddr = VirtualAlloc(NULL, *mappedSize, MEM_RESERVE, PAGE_READWRITE);
#endif
    if (mappedAddr != NULL) {
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

#ifdef USE_MALLOC
    *unmappedSize = roundUp(requestedSize, pageSize);
    free(requestedAddr);
    ret = TRUE;
#else
    *unmappedSize = roundUp(requestedSize, RESERVE_SIZE);
    ret = VirtualFree(requestedAddr, 0, MEM_RELEASE);
#endif
    if (ret) {
        unmappedAddr = requestedAddr;
        Log4(2,
             "sysUnmapMem: 0x%x bytes at 0x%x (request: 0x%x bytes at 0x%x)\n",
             *unmappedSize, unmappedAddr, requestedSize, requestedAddr);
    } else {
        unmappedAddr = NULL;
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

    *committedSize = roundUp(requestedSize, pageSize);
    committedAddr = VirtualAlloc(requestedAddr, *committedSize, MEM_COMMIT,
                                 PAGE_READWRITE);
    if (committedAddr != NULL) {
        Log4(2,
             "sysCommitMem: 0x%x bytes at 0x%x (request: 0x%x bytes at 0x%x)\n",
             *committedSize, committedAddr, requestedSize, requestedAddr);
    } else {
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

    /*
     * We round the size down to a multiple of the page size and
     * round the address up.  This ensures that we never decommit
     * more that we intend to.
     */
    *decommittedSize = roundDown(requestedSize, pageSize);
    decommittedAddr = (void *)roundUp((size_t)requestedAddr, pageSize);

    /*
     * If the rounded size is equal to zero we simply fail.  Passing
     * 0 to VirtualFree seems to cause the entire region to be released,
     * which is definitely not what we want, since that probably means
     * that decommittedAddr is at the end of the current mapping which
     * may be the beginning of the next mapping.
     */
    if (*decommittedSize != 0 &&
        VirtualFree(decommittedAddr, *decommittedSize, MEM_DECOMMIT)) {
        Log4(2,
             "sysDecommitMem: 0x%x bytes at 0x%x (request: 0x%x bytes at 0x%x)\n",
             *decommittedSize, decommittedAddr, requestedSize, requestedAddr);
    } else {
        Log4(2,
             "sysDecommitMem: failed 0x%x bytes at 0x%x (request: 0x%x bytes at 0x%x)\n",
             *decommittedSize, decommittedAddr, requestedSize, requestedAddr);
        decommittedAddr = NULL;
    }
    return decommittedAddr;
}

#define PAGED_HEAPS

#ifdef PAGED_HEAPS

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
 *
 * Note that the use of VirtualAlloc on Win32 is closely tied in to
 * the decision for paged heap pages on Win32 to be 64K (that is,
 * PAGE_ALIGNMENT is 64K), a reasonable choice in any case.
 */
void *
sysAllocBlock(size_t size, void** allocHead)
{
    void* alignedPtr = VirtualAlloc(NULL, size, MEM_COMMIT, PAGE_READWRITE);
    *allocHead = alignedPtr;
    return alignedPtr;
}

/*
 * Wrapper to free block allocated by sysMemAlign.
 */
void
sysFreeBlock(void *allocHead)
{
    VirtualFree(allocHead, 0, MEM_RELEASE);
}

#endif /* PAGED_HEAPS */

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
