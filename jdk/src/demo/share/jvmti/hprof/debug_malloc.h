/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


/* ***********************************************************************
 *
 * The source file debug_malloc.c should be included with your sources.
 *
 * The object file debug_malloc.o should be included with your object files.
 *
 *   WARNING: Any memory allocattion from things like memalign(), valloc(),
 *            or any memory not coming from these macros (malloc, realloc,
 *            calloc, and strdup) will fail miserably.
 *
 * ***********************************************************************
 */

#ifndef _DEBUG_MALLOC_H
#define _DEBUG_MALLOC_H

#ifdef DEBUG

#include <stdlib.h>
#include <string.h>

/* Use THIS_FILE when it is available. */
#ifndef THIS_FILE
    #define THIS_FILE __FILE__
#endif

/* The real functions behind the macro curtains. */

void           *debug_malloc(size_t, const char *, int);
void           *debug_realloc(void *, size_t, const char *, int);
void           *debug_calloc(size_t, size_t, const char *, int);
char           *debug_strdup(const char *, const char *, int);
void            debug_free(void *, const char *, int);

#endif

void            debug_malloc_verify(const char*, int);
#undef malloc_verify
#define malloc_verify()     debug_malloc_verify(THIS_FILE, __LINE__)

void            debug_malloc_police(const char*, int);
#undef malloc_police
#define malloc_police()     debug_malloc_police(THIS_FILE, __LINE__)

#endif
