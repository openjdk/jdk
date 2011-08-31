/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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


#ifndef HPROF_TABLE_H
#define HPROF_TABLE_H

/* Key based generic lookup table */

struct LookupTable;

typedef void (*LookupTableIterator)
                (TableIndex, void *key_ptr, int key_len, void*, void*);

struct LookupTable * table_initialize(const char *name, int size,
                                int incr, int buckets, int esize);
int                  table_element_count(struct LookupTable *ltable);
TableIndex           table_create_entry(struct LookupTable *ltable,
                                void *key_ptr, int key_len, void *info_ptr);
TableIndex           table_find_entry(struct LookupTable *ltable,
                                void *key_ptr, int key_len);
TableIndex           table_find_or_create_entry(struct LookupTable *ltable,
                                void *key_ptr, int key_len,
                                jboolean *pnew_entry, void *info_ptr);
void                 table_free_entry(struct LookupTable *ltable,
                                TableIndex index);
void                 table_cleanup(struct LookupTable *ltable,
                                LookupTableIterator func, void *arg);
void                 table_walk_items(struct LookupTable *ltable,
                                LookupTableIterator func, void *arg);
void *               table_get_info(struct LookupTable *ltable,
                                TableIndex index);
void                 table_get_key(struct LookupTable *ltable,
                                TableIndex index, void **pkey_ptr,
                                int *pkey_len);
void                 table_lock_enter(struct LookupTable *ltable);
void                 table_lock_exit(struct LookupTable *ltable);

#endif
