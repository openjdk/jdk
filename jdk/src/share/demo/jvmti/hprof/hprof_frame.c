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


/* This file contains support for handling frames, or (method,location) pairs. */

#include "hprof.h"

/*
 *  Frames map 1-to-1 to (methodID,location) pairs.
 *  When no line number is known, -1 should be used.
 *
 *  Frames are mostly used in traces (see hprof_trace.c) and will be marked
 *    with their status flag as they are written out to the hprof output file.
 *
 */

enum LinenoState {
    LINENUM_UNINITIALIZED = 0,
    LINENUM_AVAILABLE     = 1,
    LINENUM_UNAVAILABLE   = 2
};

typedef struct FrameKey {
    jmethodID   method;
    jlocation   location;
} FrameKey;

typedef struct FrameInfo {
    unsigned short      lineno;
    unsigned char       lineno_state; /* LinenoState */
    unsigned char       status;
    SerialNumber serial_num;
} FrameInfo;

static FrameKey*
get_pkey(FrameIndex index)
{
    void *key_ptr;
    int   key_len;

    table_get_key(gdata->frame_table, index, &key_ptr, &key_len);
    HPROF_ASSERT(key_len==sizeof(FrameKey));
    HPROF_ASSERT(key_ptr!=NULL);
    return (FrameKey*)key_ptr;
}

static FrameInfo *
get_info(FrameIndex index)
{
    FrameInfo *info;

    info = (FrameInfo*)table_get_info(gdata->frame_table, index);
    return info;
}

static void
list_item(TableIndex i, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    FrameKey   key;
    FrameInfo *info;

    HPROF_ASSERT(key_ptr!=NULL);
    HPROF_ASSERT(key_len==sizeof(FrameKey));
    HPROF_ASSERT(info_ptr!=NULL);

    key = *((FrameKey*)key_ptr);
    info = (FrameInfo*)info_ptr;
    debug_message(
        "Frame 0x%08x: method=%p, location=%d, lineno=%d(%d), status=%d \n",
                i, (void*)key.method, (jint)key.location,
                info->lineno, info->lineno_state, info->status);
}

void
frame_init(void)
{
    gdata->frame_table = table_initialize("Frame",
                            1024, 1024, 1023, (int)sizeof(FrameInfo));
}

FrameIndex
frame_find_or_create(jmethodID method, jlocation location)
{
    FrameIndex index;
    static FrameKey empty_key;
    FrameKey key;
    jboolean new_one;

    key          = empty_key;
    key.method   = method;
    key.location = location;
    new_one      = JNI_FALSE;
    index        = table_find_or_create_entry(gdata->frame_table,
                        &key, (int)sizeof(key), &new_one, NULL);
    if ( new_one ) {
        FrameInfo *info;

        info = get_info(index);
        info->lineno_state = LINENUM_UNINITIALIZED;
        if ( location < 0 ) {
            info->lineno_state = LINENUM_UNAVAILABLE;
        }
        info->serial_num = gdata->frame_serial_number_counter++;
    }
    return index;
}

void
frame_list(void)
{
    debug_message(
        "--------------------- Frame Table ------------------------\n");
    table_walk_items(gdata->frame_table, &list_item, NULL);
    debug_message(
        "----------------------------------------------------------\n");
}

void
frame_cleanup(void)
{
    table_cleanup(gdata->frame_table, NULL, NULL);
    gdata->frame_table = NULL;
}

void
frame_set_status(FrameIndex index, jint status)
{
    FrameInfo *info;

    info = get_info(index);
    info->status = (unsigned char)status;
}

void
frame_get_location(FrameIndex index, SerialNumber *pserial_num,
                   jmethodID *pmethod, jlocation *plocation, jint *plineno)
{
    FrameKey  *pkey;
    FrameInfo *info;
    jint       lineno;

    pkey       = get_pkey(index);
    *pmethod   = pkey->method;
    *plocation = pkey->location;
    info       = get_info(index);
    lineno     = (jint)info->lineno;
    if ( info->lineno_state == LINENUM_UNINITIALIZED ) {
        info->lineno_state = LINENUM_UNAVAILABLE;
        if ( gdata->lineno_in_traces ) {
            if ( pkey->location >= 0 && !isMethodNative(pkey->method) ) {
                lineno = getLineNumber(pkey->method, pkey->location);
                if ( lineno >= 0 ) {
                    info->lineno = (unsigned short)lineno; /* save it */
                    info->lineno_state = LINENUM_AVAILABLE;
                }
            }
        }
    }
    if ( info->lineno_state == LINENUM_UNAVAILABLE ) {
        lineno = -1;
    }
    *plineno     = lineno;
    *pserial_num = info->serial_num;
}

jint
frame_get_status(FrameIndex index)
{
    FrameInfo *info;

    info = get_info(index);
    return (jint)info->status;
}
