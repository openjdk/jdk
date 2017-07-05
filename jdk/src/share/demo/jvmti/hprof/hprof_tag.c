/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

/* JVMTI tag definitions. */

/*
 * JVMTI tags are jlongs (64 bits) and how the hprof information is
 *   turned into a tag and/or extracted from a tag is here.
 *
 * Currently a special TAG_CHECK is placed in the high order 32 bits of
 *    the tag as a check.
 *
 */

#include "hprof.h"

#define TAG_CHECK 0xfad4dead

jlong
tag_create(ObjectIndex object_index)
{
    jlong               tag;

    HPROF_ASSERT(object_index != 0);
    tag = TAG_CHECK;
    tag = (tag << 32) | object_index;
    return tag;
}

ObjectIndex
tag_extract(jlong tag)
{
    HPROF_ASSERT(tag != (jlong)0);
    if ( ((tag >> 32) & 0xFFFFFFFF) != TAG_CHECK) {
        HPROF_ERROR(JNI_TRUE, "JVMTI tag value is not 0 and missing TAG_CHECK");
    }
    return  (ObjectIndex)(tag & 0xFFFFFFFF);
}

/* Tag a new jobject */
void
tag_new_object(jobject object, ObjectKind kind, SerialNumber thread_serial_num,
                jint size, SiteIndex site_index)
{
    ObjectIndex  object_index;
    jlong        tag;

    HPROF_ASSERT(site_index!=0);
    /* New object for this site. */
    object_index = object_new(site_index, size, kind, thread_serial_num);
    /* Create and set the tag. */
    tag = tag_create(object_index);
    setTag(object, tag);
    LOG3("tag_new_object", "tag", (int)tag);
}

/* Tag a jclass jobject if it hasn't been tagged. */
void
tag_class(JNIEnv *env, jclass klass, ClassIndex cnum,
                SerialNumber thread_serial_num, SiteIndex site_index)
{
    ObjectIndex object_index;

    /* If the ClassIndex has an ObjectIndex, then we have tagged it. */
    object_index = class_get_object_index(cnum);

    if ( object_index == 0 ) {
        jint        size;
        jlong        tag;

        HPROF_ASSERT(site_index!=0);

        /* If we don't know the size of a java.lang.Class object, get it */
        size =  gdata->system_class_size;
        if ( size == 0 ) {
            size  = (jint)getObjectSize(klass);
            gdata->system_class_size = size;
        }

        /* Tag this java.lang.Class object if it hasn't been already */
        tag = getTag(klass);
        if ( tag == (jlong)0 ) {
            /* New object for this site. */
            object_index = object_new(site_index, size, OBJECT_CLASS,
                                        thread_serial_num);
            /* Create and set the tag. */
            tag = tag_create(object_index);
            setTag(klass, tag);
        } else {
            /* Get the ObjectIndex from the tag. */
            object_index = tag_extract(tag);
        }

        /* Record this object index in the Class table */
        class_set_object_index(cnum, object_index);
    }
}
