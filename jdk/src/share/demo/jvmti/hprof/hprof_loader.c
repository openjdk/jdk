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


/* The Class Loader table. */

/*
 * The Class Loader objects show up so early in the VM process that a
 *   separate table was designated for Class Loaders.
 *
 * The Class Loader is unique by way of it's jobject uniqueness, unfortunately
 *   use of JNI too early for jobject comparisons is problematic.
 *   It is assumed that the number of class loaders will be limited, and
 *   a simple linear search will be performed for now.
 *   That logic is isolated here and can be changed to use the standard
 *   table hash table search once we know JNI can be called safely.
 *
 * A weak global reference is created to keep tabs on loaders, and as
 *   each search for a loader happens, NULL weak global references will
 *   trigger the freedom of those entries.
 *
 */

#include "hprof.h"

typedef struct {
    jobject         globalref;    /* Weak Global reference for object */
    ObjectIndex     object_index;
} LoaderInfo;

static LoaderInfo *
get_info(LoaderIndex index)
{
    return (LoaderInfo*)table_get_info(gdata->loader_table, index);
}

static void
delete_globalref(JNIEnv *env, LoaderInfo *info)
{
    jobject     ref;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(info!=NULL);
    ref = info->globalref;
    info->globalref = NULL;
    if ( ref != NULL ) {
        deleteWeakGlobalReference(env, ref);
    }
    info->object_index = 0;
}

static void
cleanup_item(TableIndex index, void *key_ptr, int key_len,
                        void *info_ptr, void *arg)
{
}

static void
delete_ref_item(TableIndex index, void *key_ptr, int key_len,
                        void *info_ptr, void *arg)
{
    delete_globalref((JNIEnv*)arg, (LoaderInfo*)info_ptr);
}

static void
list_item(TableIndex index, void *key_ptr, int key_len,
                        void *info_ptr, void *arg)
{
    LoaderInfo     *info;

    HPROF_ASSERT(info_ptr!=NULL);

    info        = (LoaderInfo*)info_ptr;
    debug_message( "Loader 0x%08x: globalref=%p, object_index=%d\n",
                index, (void*)info->globalref, info->object_index);
}

static void
free_entry(JNIEnv *env, LoaderIndex index)
{
    LoaderInfo *info;

    info = get_info(index);
    delete_globalref(env, info);
    table_free_entry(gdata->loader_table, index);
}

typedef struct SearchData {
    JNIEnv *env;
    jobject loader;
    LoaderIndex found;
} SearchData;

static void
search_item(TableIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    LoaderInfo  *info;
    SearchData  *data;

    HPROF_ASSERT(info_ptr!=NULL);
    HPROF_ASSERT(arg!=NULL);
    info        = (LoaderInfo*)info_ptr;
    data        = (SearchData*)arg;
    if ( data->loader == info->globalref ) {
        /* Covers when looking for NULL too. */
        HPROF_ASSERT(data->found==0); /* Did we find more than one? */
        data->found = index;
    } else if ( data->env != NULL && data->loader != NULL &&
                info->globalref != NULL ) {
        jobject lref;

        lref = newLocalReference(data->env, info->globalref);
        if ( lref == NULL ) {
            /* Object went away, free reference and entry */
            free_entry(data->env, index);
        } else if ( isSameObject(data->env, data->loader, lref) ) {
            HPROF_ASSERT(data->found==0); /* Did we find more than one? */
            data->found = index;
        }
        if ( lref != NULL ) {
            deleteLocalReference(data->env, lref);
        }
    }

}

static LoaderIndex
search(JNIEnv *env, jobject loader)
{
    SearchData  data;

    data.env    = env;
    data.loader = loader;
    data.found  = 0;
    table_walk_items(gdata->loader_table, &search_item, (void*)&data);
    return data.found;
}

LoaderIndex
loader_find_or_create(JNIEnv *env, jobject loader)
{
    LoaderIndex index;

    /* See if we remembered the system loader */
    if ( loader==NULL && gdata->system_loader != 0 ) {
        return gdata->system_loader;
    }
    if ( loader==NULL ) {
        env = NULL;
    }
    index = search(env, loader);
    if ( index == 0 ) {
        static LoaderInfo  empty_info;
        LoaderInfo  info;

        info = empty_info;
        if ( loader != NULL ) {
            HPROF_ASSERT(env!=NULL);
            info.globalref = newWeakGlobalReference(env, loader);
            info.object_index = 0;
        }
        index = table_create_entry(gdata->loader_table, NULL, 0, (void*)&info);
    }
    HPROF_ASSERT(search(env,loader)==index);
    /* Remember the system loader */
    if ( loader==NULL && gdata->system_loader == 0 ) {
        gdata->system_loader = index;
    }
    return index;
}

void
loader_init(void)
{
    gdata->loader_table = table_initialize("Loader",
                            16, 16, 0, (int)sizeof(LoaderInfo));
}

void
loader_list(void)
{
    debug_message(
        "--------------------- Loader Table ------------------------\n");
    table_walk_items(gdata->loader_table, &list_item, NULL);
    debug_message(
        "----------------------------------------------------------\n");
}

void
loader_cleanup(void)
{
    table_cleanup(gdata->loader_table, &cleanup_item, NULL);
    gdata->loader_table = NULL;
}

void
loader_delete_global_references(JNIEnv *env)
{
    table_walk_items(gdata->loader_table, &delete_ref_item, (void*)env);
}

/* Get the object index for a class loader */
ObjectIndex
loader_object_index(JNIEnv *env, LoaderIndex index)
{
    LoaderInfo *info;
    ObjectIndex object_index;
    jobject     wref;

    /* Assume no object index at first (default class loader) */
    info = get_info(index);
    object_index = info->object_index;
    wref = info->globalref;
    if ( wref != NULL && object_index == 0 ) {
        jobject lref;

        object_index = 0;
        lref = newLocalReference(env, wref);
        if ( lref != NULL && !isSameObject(env, lref, NULL) ) {
            jlong tag;

            /* Get the tag on the object and extract the object_index */
            tag = getTag(lref);
            if ( tag != (jlong)0 ) {
                object_index = tag_extract(tag);
            }
        }
        if ( lref != NULL ) {
            deleteLocalReference(env, lref);
        }
        info->object_index = object_index;
    }
    return object_index;
}
