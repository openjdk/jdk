/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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


/* Table of byte arrays (e.g. char* string + NULL byte) */

/*
 * Strings are unique by their own contents, since the string itself
 *   is the Key, and the hprof_table.c guarantees that keys don't move,
 *   this works out perfect. Any key in this table can be used as
 *   an char*.
 *
 * This does mean that this table has dynamically sized keys.
 *
 * Care needs to be taken to make sure the NULL byte is included, not for
 *   the sake of hprof_table.c, but so that the key can be used as a char*.
 *
 */

#include "hprof.h"

void
string_init(void)
{
    HPROF_ASSERT(gdata->string_table==NULL);
    gdata->string_table = table_initialize("Strings", 4096, 4096, 1024, 0);
}

StringIndex
string_find_or_create(const char *str)
{
    return table_find_or_create_entry(gdata->string_table,
                (void*)str, (int)strlen(str)+1, NULL, NULL);
}

static void
list_item(TableIndex index, void *str, int len, void *info_ptr, void *arg)
{
    debug_message( "0x%08x: String \"%s\"\n", index, (const char *)str);
}

void
string_list(void)
{
    debug_message(
        "-------------------- String Table ------------------------\n");
    table_walk_items(gdata->string_table, &list_item, NULL);
    debug_message(
        "----------------------------------------------------------\n");
}

void
string_cleanup(void)
{
    table_cleanup(gdata->string_table, NULL, NULL);
    gdata->string_table = NULL;
}

char *
string_get(StringIndex index)
{
    void *key;
    int   key_len;

    table_get_key(gdata->string_table, index, &key, &key_len);
    HPROF_ASSERT(key_len>0);
    return (char*)key;
}

int
string_get_len(StringIndex index)
{
    void *key;
    int   key_len;

    table_get_key(gdata->string_table, index, &key, &key_len);
    HPROF_ASSERT(key_len>0);
    return key_len-1;
}
