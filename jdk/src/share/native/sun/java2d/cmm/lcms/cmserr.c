/*
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

// This file is available under and governed by the GNU General Public
// License version 2 only, as published by the Free Software Foundation.
// However, the following notice accompanied the original version of this
// file:
//
//---------------------------------------------------------------------------------
//
//  Little Color Management System
//  Copyright (c) 1998-2010 Marti Maria Saguer
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the Software
// is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
//---------------------------------------------------------------------------------

#include "lcms2_internal.h"

// I am so tired about incompatibilities on those functions that here are some replacements
// that hopefully would be fully portable.

// compare two strings ignoring case
int CMSEXPORT cmsstrcasecmp(const char* s1, const char* s2)
{
         register const unsigned char *us1 = (const unsigned char *)s1,
                                      *us2 = (const unsigned char *)s2;

        while (toupper(*us1) == toupper(*us2++))
                if (*us1++ == '\0')
                        return (0);
        return (toupper(*us1) - toupper(*--us2));
}

// long int because C99 specifies ftell in such way (7.19.9.2)
long int CMSEXPORT cmsfilelength(FILE* f)
{
    long int n;

    if (fseek(f, 0, SEEK_END) != 0) {
        return -1;
    }
    n = ftell(f);
    fseek(f, 0, SEEK_SET);

    return n;
}

// Memory handling ------------------------------------------------------------------
//
// This is the interface to low-level memory management routines. By default a simple
// wrapping to malloc/free/realloc is provided, although there is a limit on the max
// amount of memoy that can be reclaimed. This is mostly as a safety feature to
// prevent bogus or malintentionated code to allocate huge blocks that otherwise lcms
// would never need.

#define MAX_MEMORY_FOR_ALLOC  ((cmsUInt32Number)(1024U*1024U*512U))

// User may override this behaviour by using a memory plug-in, which basically replaces
// the default memory management functions. In this case, no check is performed and it
// is up to the plug-in writter to keep in the safe side. There are only three functions
// required to be implemented: malloc, realloc and free, although the user may want to
// replace the optional mallocZero, calloc and dup as well.

cmsBool   _cmsRegisterMemHandlerPlugin(cmsPluginBase* Plugin);

// *********************************************************************************

// This is the default memory allocation function. It does a very coarse
// check of amout of memory, just to prevent exploits
static
void* _cmsMallocDefaultFn(cmsContext ContextID, cmsUInt32Number size)
{
    if (size > MAX_MEMORY_FOR_ALLOC) return NULL;  // Never allow over maximum

    return (void*) malloc(size);

    cmsUNUSED_PARAMETER(ContextID);
}

// Generic allocate & zero
static
void* _cmsMallocZeroDefaultFn(cmsContext ContextID, cmsUInt32Number size)
{
    void *pt = _cmsMalloc(ContextID, size);
    if (pt == NULL) return NULL;

    memset(pt, 0, size);
    return pt;
}


// The default free function. The only check proformed is against NULL pointers
static
void _cmsFreeDefaultFn(cmsContext ContextID, void *Ptr)
{
    // free(NULL) is defined a no-op by C99, therefore it is safe to
    // avoid the check, but it is here just in case...

    if (Ptr) free(Ptr);

    cmsUNUSED_PARAMETER(ContextID);
}

// The default realloc function. Again it check for exploits. If Ptr is NULL,
// realloc behaves the same way as malloc and allocates a new block of size bytes.
static
void* _cmsReallocDefaultFn(cmsContext ContextID, void* Ptr, cmsUInt32Number size)
{

    if (size > MAX_MEMORY_FOR_ALLOC) return NULL;  // Never realloc over 512Mb

    return realloc(Ptr, size);

    cmsUNUSED_PARAMETER(ContextID);
}


// The default calloc function. Allocates an array of num elements, each one of size bytes
// all memory is initialized to zero.
static
void* _cmsCallocDefaultFn(cmsContext ContextID, cmsUInt32Number num, cmsUInt32Number size)
{
    cmsUInt32Number Total = num * size;

    // Check for overflow
    if (Total < num || Total < size) {
        return NULL;
    }

    if (Total > MAX_MEMORY_FOR_ALLOC) return NULL;  // Never alloc over 512Mb

    return _cmsMallocZero(ContextID, Total);
}

// Generic block duplication
static
void* _cmsDupDefaultFn(cmsContext ContextID, const void* Org, cmsUInt32Number size)
{
    void* mem;

    if (size > MAX_MEMORY_FOR_ALLOC) return NULL;  // Never dup over 512Mb

    mem = _cmsMalloc(ContextID, size);

    if (mem != NULL && Org != NULL)
        memmove(mem, Org, size);

    return mem;
}

// Pointers to malloc and _cmsFree functions in current environment
static void * (* MallocPtr)(cmsContext ContextID, cmsUInt32Number size)                     = _cmsMallocDefaultFn;
static void * (* MallocZeroPtr)(cmsContext ContextID, cmsUInt32Number size)                 = _cmsMallocZeroDefaultFn;
static void   (* FreePtr)(cmsContext ContextID, void *Ptr)                                  = _cmsFreeDefaultFn;
static void * (* ReallocPtr)(cmsContext ContextID, void *Ptr, cmsUInt32Number NewSize)      = _cmsReallocDefaultFn;
static void * (* CallocPtr)(cmsContext ContextID, cmsUInt32Number num, cmsUInt32Number size)= _cmsCallocDefaultFn;
static void * (* DupPtr)(cmsContext ContextID, const void* Org, cmsUInt32Number size)       = _cmsDupDefaultFn;

// Plug-in replacement entry
cmsBool  _cmsRegisterMemHandlerPlugin(cmsPluginBase *Data)
{
    cmsPluginMemHandler* Plugin = (cmsPluginMemHandler*) Data;

    // NULL forces to reset to defaults
    if (Data == NULL) {

        MallocPtr    = _cmsMallocDefaultFn;
        MallocZeroPtr= _cmsMallocZeroDefaultFn;
        FreePtr      = _cmsFreeDefaultFn;
        ReallocPtr   = _cmsReallocDefaultFn;
        CallocPtr    = _cmsCallocDefaultFn;
        DupPtr       = _cmsDupDefaultFn;
        return TRUE;
    }

    // Check for required callbacks
    if (Plugin -> MallocPtr == NULL ||
        Plugin -> FreePtr == NULL ||
        Plugin -> ReallocPtr == NULL) return FALSE;

    // Set replacement functions
    MallocPtr  = Plugin -> MallocPtr;
    FreePtr    = Plugin -> FreePtr;
    ReallocPtr = Plugin -> ReallocPtr;

    if (Plugin ->MallocZeroPtr != NULL) MallocZeroPtr = Plugin ->MallocZeroPtr;
    if (Plugin ->CallocPtr != NULL)     CallocPtr     = Plugin -> CallocPtr;
    if (Plugin ->DupPtr != NULL)        DupPtr        = Plugin -> DupPtr;

    return TRUE;
}

// Generic allocate
void* CMSEXPORT _cmsMalloc(cmsContext ContextID, cmsUInt32Number size)
{
    return MallocPtr(ContextID, size);
}

// Generic allocate & zero
void* CMSEXPORT _cmsMallocZero(cmsContext ContextID, cmsUInt32Number size)
{
    return MallocZeroPtr(ContextID, size);
}

// Generic calloc
void* CMSEXPORT _cmsCalloc(cmsContext ContextID, cmsUInt32Number num, cmsUInt32Number size)
{
    return CallocPtr(ContextID, num, size);
}

// Generic reallocate
void* CMSEXPORT _cmsRealloc(cmsContext ContextID, void* Ptr, cmsUInt32Number size)
{
    return ReallocPtr(ContextID, Ptr, size);
}

// Generic free memory
void CMSEXPORT _cmsFree(cmsContext ContextID, void* Ptr)
{
    if (Ptr != NULL) FreePtr(ContextID, Ptr);
}

// Generic block duplication
void* CMSEXPORT _cmsDupMem(cmsContext ContextID, const void* Org, cmsUInt32Number size)
{
    return DupPtr(ContextID, Org, size);
}

// ********************************************************************************************

// Sub allocation takes care of many pointers of small size. The memory allocated in
// this way have be freed at once. Next function allocates a single chunk for linked list
// I prefer this method over realloc due to the big inpact on xput realloc may have if
// memory is being swapped to disk. This approach is safer (although thats not true on any platform)
static
_cmsSubAllocator_chunk* _cmsCreateSubAllocChunk(cmsContext ContextID, cmsUInt32Number Initial)
{
    _cmsSubAllocator_chunk* chunk;

    // Create the container
    chunk = (_cmsSubAllocator_chunk*) _cmsMallocZero(ContextID, sizeof(_cmsSubAllocator_chunk));
    if (chunk == NULL) return NULL;

    // Initialize values
    chunk ->Block     = (cmsUInt8Number*) _cmsMalloc(ContextID, Initial);
    if (chunk ->Block == NULL) {

        // Something went wrong
        _cmsFree(ContextID, chunk);
        return NULL;
    }

    // 20K by default
    if (Initial == 0)
        Initial = 20*1024;

    chunk ->BlockSize = Initial;
    chunk ->Used      = 0;
    chunk ->next      = NULL;

    return chunk;
}

// The suballocated is nothing but a pointer to the first element in the list. We also keep
// the thread ID in this structure.
_cmsSubAllocator* _cmsCreateSubAlloc(cmsContext ContextID, cmsUInt32Number Initial)
{
    _cmsSubAllocator* sub;

    // Create the container
    sub = (_cmsSubAllocator*) _cmsMallocZero(ContextID, sizeof(_cmsSubAllocator));
    if (sub == NULL) return NULL;

    sub ->ContextID = ContextID;

    sub ->h = _cmsCreateSubAllocChunk(ContextID, Initial);
    if (sub ->h == NULL) {
        _cmsFree(ContextID, sub);
        return NULL;
    }

    return sub;
}


// Get rid of whole linked list
void _cmsSubAllocDestroy(_cmsSubAllocator* sub)
{
    _cmsSubAllocator_chunk *chunk, *n;

    for (chunk = sub ->h; chunk != NULL; chunk = n) {

        n = chunk->next;
        if (chunk->Block != NULL) _cmsFree(sub ->ContextID, chunk->Block);
        _cmsFree(sub ->ContextID, chunk);
    }

    // Free the header
    _cmsFree(sub ->ContextID, sub);
}


// Get a pointer to small memory block.
void*  _cmsSubAlloc(_cmsSubAllocator* sub, cmsUInt32Number size)
{
    cmsUInt32Number Free = sub -> h ->BlockSize - sub -> h -> Used;
    cmsUInt8Number* ptr;

    size = _cmsALIGNLONG(size);

    // Check for memory. If there is no room, allocate a new chunk of double memory size.
    if (size > Free) {

        _cmsSubAllocator_chunk* chunk;
        cmsUInt32Number newSize;

        newSize = sub -> h ->BlockSize * 2;
        if (newSize < size) newSize = size;

        chunk = _cmsCreateSubAllocChunk(sub -> ContextID, newSize);
        if (chunk == NULL) return NULL;

        // Link list
        chunk ->next = sub ->h;
        sub ->h    = chunk;

    }

    ptr =  sub -> h ->Block + sub -> h ->Used;
    sub -> h -> Used += size;

    return (void*) ptr;
}

// Error logging ******************************************************************

// There is no error handling at all. When a funtion fails, it returns proper value.
// For example, all create functions does return NULL on failure. Other return FALSE
// It may be interesting, for the developer, to know why the function is failing.
// for that reason, lcms2 does offer a logging function. This function does recive
// a ENGLISH string with some clues on what is going wrong. You can show this
// info to the end user, or just create some sort of log.
// The logging function should NOT terminate the program, as this obviously can leave
// resources. It is the programmer's responsability to check each function return code
// to make sure it didn't fail.

// Error messages are limited to MAX_ERROR_MESSAGE_LEN

#define MAX_ERROR_MESSAGE_LEN   1024

// ---------------------------------------------------------------------------------------------------------

// This is our default log error
static void DefaultLogErrorHandlerFunction(cmsContext ContextID, cmsUInt32Number ErrorCode, const char *Text);

// The current handler in actual environment
static cmsLogErrorHandlerFunction LogErrorHandler   = DefaultLogErrorHandlerFunction;

// The default error logger does nothing.
static
void DefaultLogErrorHandlerFunction(cmsContext ContextID, cmsUInt32Number ErrorCode, const char *Text)
{
    // fprintf(stderr, "[lcms]: %s\n", Text);
    // fflush(stderr);

     cmsUNUSED_PARAMETER(ContextID);
     cmsUNUSED_PARAMETER(ErrorCode);
     cmsUNUSED_PARAMETER(Text);
}

// Change log error
void CMSEXPORT cmsSetLogErrorHandler(cmsLogErrorHandlerFunction Fn)
{
    if (Fn == NULL)
        LogErrorHandler = DefaultLogErrorHandlerFunction;
    else
        LogErrorHandler = Fn;
}

// Log an error
// ErrorText is a text holding an english description of error.
void CMSEXPORT cmsSignalError(cmsContext ContextID, cmsUInt32Number ErrorCode, const char *ErrorText, ...)
{
    va_list args;
    char Buffer[MAX_ERROR_MESSAGE_LEN];

    va_start(args, ErrorText);
    vsnprintf(Buffer, MAX_ERROR_MESSAGE_LEN-1, ErrorText, args);
    va_end(args);

    // Call handler
    LogErrorHandler(ContextID, ErrorCode, Buffer);
}

// Utility function to print signatures
void _cmsTagSignature2String(char String[5], cmsTagSignature sig)
{
    cmsUInt32Number be;

    // Convert to big endian
    be = _cmsAdjustEndianess32((cmsUInt32Number) sig);

    // Move chars
    memmove(String, &be, 4);

    // Make sure of terminator
    String[4] = 0;
}

