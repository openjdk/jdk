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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


/* Table of class information.
 *
 *   Each element in this table is identified with a ClassIndex.
 *   Each element is uniquely identified by it's signature and loader.
 *   Every class load has a unique class serial number.
 *   While loaded, each element will have a cache of a global reference
 *     to it's jclass object, plus jmethodID's as needed.
 *   Method signatures and names are obtained via BCI.
 *   Methods can be identified with a ClassIndex and MethodIndex pair,
 *     where the MethodIndex matches the index of the method name and
 *     signature arrays obtained from the BCI pass.
 *   Strings are stored in the string table and a StringIndex is used.
 *   Class Loaders are stored in the loader table and a LoaderIndex is used.
 *   Since the jclass object is an object, at some point an object table
 *      entry may be allocated for the jclass as an ObjectIndex.
 */

#include "hprof.h"

/* Effectively represents a jclass object. */

/* These table elements are made unique by and sorted by signature name. */

typedef struct ClassKey {
    StringIndex    sig_string_index;    /* Signature of class */
    LoaderIndex    loader_index;        /* Index for class loader */
} ClassKey;

/* Each class could contain method information, gotten from BCI callback */

typedef struct MethodInfo {
    StringIndex  name_index;    /* Method name, index into string table */
    StringIndex  sig_index;     /* Method signature, index into string table */
    jmethodID    method_id;     /* Method ID, possibly NULL at first */
} MethodInfo;

/* The basic class information we save */

typedef struct ClassInfo {
    jclass         classref;            /* Global ref to jclass */
    MethodInfo    *method;              /* Array of method data */
    int            method_count;        /* Count of methods */
    ObjectIndex    object_index;        /* Optional object index for jclass */
    SerialNumber   serial_num;          /* Unique to the actual class load */
    ClassStatus    status;              /* Current class status (bit mask) */
    ClassIndex     super;               /* Super class in this table */
    StringIndex    name;                /* Name of class */
    jint           inst_size;           /* #bytes needed for instance fields */
    jint           field_count;         /* Number of all fields */
    FieldInfo     *field;               /* Pointer to all FieldInfo's */
} ClassInfo;

/* Private interfaces */

static ClassKey*
get_pkey(ClassIndex index)
{
    void *key_ptr;
    int   key_len;

    table_get_key(gdata->class_table, index, (void*)&key_ptr, &key_len);
    HPROF_ASSERT(key_len==sizeof(ClassKey));
    HPROF_ASSERT(key_ptr!=NULL);
    return (ClassKey*)key_ptr;
}

static void
fillin_pkey(const char *sig, LoaderIndex loader_index, ClassKey *pkey)
{
    static ClassKey empty_key;

    HPROF_ASSERT(loader_index!=0);
    *pkey                  = empty_key;
    pkey->sig_string_index = string_find_or_create(sig);
    pkey->loader_index     = loader_index;
}

static ClassInfo *
get_info(ClassIndex index)
{
    ClassInfo *info;

    info = (ClassInfo*)table_get_info(gdata->class_table, index);
    return info;
}

static void
fill_info(TableIndex index, ClassKey *pkey)
{
    ClassInfo *info;
    char      *sig;

    info = get_info(index);
    info->serial_num = gdata->class_serial_number_counter++;
    info->method_count = 0;
    info->inst_size = -1;
    info->field_count = -1;
    info->field = NULL;
    sig = string_get(pkey->sig_string_index);
    if ( sig[0] != JVM_SIGNATURE_CLASS ) {
        info->name = pkey->sig_string_index;
    } else {
        int        len;

        len = string_get_len(pkey->sig_string_index);
        if ( len > 2  ) {
            char      *name;

            /* Class signature looks like "Lname;", we want "name" here. */
            name = HPROF_MALLOC(len-1);
            (void)memcpy(name, sig+1, len-2);
            name[len-2] = 0;
            info->name = string_find_or_create(name);
            HPROF_FREE(name);
        } else {
            /* This would be strange, a class signature not in "Lname;" form? */
            info->name = pkey->sig_string_index;
        }
   }
}

static ClassIndex
find_entry(ClassKey *pkey)
{
    ClassIndex index;

    index = table_find_entry(gdata->class_table,
                                (void*)pkey, (int)sizeof(ClassKey));
    return index;
}

static ClassIndex
create_entry(ClassKey *pkey)
{
    ClassIndex index;

    index = table_create_entry(gdata->class_table,
                                (void*)pkey, (int)sizeof(ClassKey), NULL);
    fill_info(index, pkey);
    return index;
}

static ClassIndex
find_or_create_entry(ClassKey *pkey)
{
    ClassIndex      index;

    HPROF_ASSERT(pkey!=NULL);
    HPROF_ASSERT(pkey->loader_index!=0);
    index = find_entry(pkey);
    if ( index == 0 ) {
        index = create_entry(pkey);
    }
    return index;
}

static void
delete_classref(JNIEnv *env, ClassInfo *info, jclass klass)
{
    jclass ref;
    int    i;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(info!=NULL);

    for ( i = 0 ; i < info->method_count ; i++ ) {
        info->method[i].method_id  = NULL;
    }
    ref = info->classref;
    if ( klass != NULL ) {
        info->classref = newGlobalReference(env, klass);
    } else {
        info->classref = NULL;
    }
    if ( ref != NULL ) {
        deleteGlobalReference(env, ref);
    }
}

static void
cleanup_item(TableIndex index, void *key_ptr, int key_len,
                                void *info_ptr, void *arg)
{
    ClassInfo *info;

    /* Cleanup any information in this ClassInfo structure. */
    HPROF_ASSERT(key_ptr!=NULL);
    HPROF_ASSERT(key_len==sizeof(ClassKey));
    HPROF_ASSERT(info_ptr!=NULL);
    info = (ClassInfo *)info_ptr;
    if ( info->method_count > 0 ) {
        HPROF_FREE((void*)info->method);
        info->method_count = 0;
        info->method       = NULL;
    }
    if ( info->field != NULL ) {
        HPROF_FREE((void*)info->field);
        info->field_count = 0;
        info->field      = NULL;
    }
}

static void
delete_ref_item(TableIndex index, void *key_ptr, int key_len,
                                void *info_ptr, void *arg)
{
    delete_classref((JNIEnv*)arg, (ClassInfo*)info_ptr, NULL);
}

static void
list_item(TableIndex index, void *key_ptr, int key_len,
                                void *info_ptr, void *arg)
{
    ClassInfo *info;
    ClassKey   key;
    char      *sig;
    int        i;

    HPROF_ASSERT(key_ptr!=NULL);
    HPROF_ASSERT(key_len==sizeof(ClassKey));
    HPROF_ASSERT(info_ptr!=NULL);
    key = *((ClassKey*)key_ptr);
    sig = string_get(key.sig_string_index);
    info = (ClassInfo *)info_ptr;
    debug_message(
             "0x%08x: Class %s, SN=%u, status=0x%08x, ref=%p,"
             " method_count=%d\n",
             index,
             (const char *)sig,
             info->serial_num,
             info->status,
             (void*)info->classref,
             info->method_count);
    if ( info->method_count > 0 ) {
        for ( i = 0 ; i < info->method_count ; i++ ) {
            debug_message(
                "    Method %d: \"%s\", sig=\"%s\", method=%p\n",
                i,
                string_get(info->method[i].name_index),
                string_get(info->method[i].sig_index),
                (void*)info->method[i].method_id);
        }
    }
}

static void
all_status_remove(TableIndex index, void *key_ptr, int key_len,
                                void *info_ptr, void *arg)
{
    ClassInfo   *info;
    ClassStatus  status;

    HPROF_ASSERT(info_ptr!=NULL);
    /*LINTED*/
    status = (ClassStatus)(long)(ptrdiff_t)arg;
    info = (ClassInfo *)info_ptr;
    info->status &= (~status);
}

static void
unload_walker(TableIndex index, void *key_ptr, int key_len,
                                void *info_ptr, void *arg)
{
    ClassInfo        *info;

    HPROF_ASSERT(info_ptr!=NULL);
    info = (ClassInfo *)info_ptr;
    if ( ! ( info->status & CLASS_IN_LOAD_LIST ) ) {
        if ( ! (info->status & (CLASS_SPECIAL|CLASS_SYSTEM|CLASS_UNLOADED)) ) {
            io_write_class_unload(info->serial_num, info->object_index);
            info->status |= CLASS_UNLOADED;
            delete_classref((JNIEnv*)arg, info, NULL);
        }
    }
}

/* External interfaces */

void
class_init(void)
{
    HPROF_ASSERT(gdata->class_table==NULL);
    gdata->class_table = table_initialize("Class", 512, 512, 511,
                                    (int)sizeof(ClassInfo));
}

ClassIndex
class_find_or_create(const char *sig, LoaderIndex loader_index)
{
    ClassKey key;

    fillin_pkey(sig, loader_index, &key);
    return find_or_create_entry(&key);
}

ClassIndex
class_create(const char *sig, LoaderIndex loader_index)
{
    ClassKey key;

    fillin_pkey(sig, loader_index, &key);
    return create_entry(&key);
}

void
class_prime_system_classes(void)
{
    /* Prime System classes? Anything before VM_START is System class.
     *   Or classes loaded before env arg is non-NULL.
     *   Or any of the classes listed below.
     */
    static const char * signatures[] =
        {
            "Ljava/lang/Object;",
            "Ljava/io/Serializable;",
            "Ljava/lang/String;",
            "Ljava/lang/Class;",
            "Ljava/lang/ClassLoader;",
            "Ljava/lang/System;",
            "Ljava/lang/Thread;",
            "Ljava/lang/ThreadGroup;",
        };
    int n_signatures;
    int i;
    LoaderIndex loader_index;

    n_signatures = (int)sizeof(signatures)/(int)sizeof(signatures[0]);
    loader_index = loader_find_or_create(NULL, NULL);
    for ( i = 0 ; i < n_signatures ; i++ ) {
        ClassInfo  *info;
        ClassIndex  index;
        ClassKey    key;

        fillin_pkey(signatures[i], loader_index, &key);
        index = find_or_create_entry(&key);
        info = get_info(index);
        info->status |= CLASS_SYSTEM;
    }
}

void
class_add_status(ClassIndex index, ClassStatus status)
{
    ClassInfo *info;

    info = get_info(index);
    info->status |= status;
}

ClassStatus
class_get_status(ClassIndex index)
{
    ClassInfo *info;

    info = get_info(index);
    return info->status;
}

StringIndex
class_get_signature(ClassIndex index)
{
    ClassKey *pkey;

    pkey = get_pkey(index);
    return pkey->sig_string_index;
}

SerialNumber
class_get_serial_number(ClassIndex index)
{
    ClassInfo *info;

    if ( index == 0 ) {
        return 0;
    }
    info = get_info(index);
    return info->serial_num;
}

void
class_all_status_remove(ClassStatus status)
{
    table_walk_items(gdata->class_table, &all_status_remove,
                (void*)(ptrdiff_t)(long)status);
}

void
class_do_unloads(JNIEnv *env)
{
    table_walk_items(gdata->class_table, &unload_walker, (void*)env);
}

void
class_list(void)
{
    debug_message(
        "--------------------- Class Table ------------------------\n");
    table_walk_items(gdata->class_table, &list_item, NULL);
    debug_message(
        "----------------------------------------------------------\n");
}

void
class_cleanup(void)
{
    table_cleanup(gdata->class_table, &cleanup_item, NULL);
    gdata->class_table = NULL;
}

void
class_delete_global_references(JNIEnv* env)
{
    table_walk_items(gdata->class_table, &delete_ref_item, (void*)env);
}

void
class_set_methods(ClassIndex index, const char **name, const char **sig,
                        int count)
{
    ClassInfo *info;
    int        i;

    info               = get_info(index);
    if ( info->method_count > 0 ) {
        HPROF_FREE((void*)info->method);
        info->method_count = 0;
        info->method       = NULL;
    }
    info->method_count = count;
    if ( count > 0 ) {
        info->method = (MethodInfo *)HPROF_MALLOC(count*(int)sizeof(MethodInfo));
        for ( i = 0 ; i < count ; i++ ) {
            info->method[i].name_index = string_find_or_create(name[i]);
            info->method[i].sig_index  = string_find_or_create(sig[i]);
            info->method[i].method_id  = NULL;
        }
    }
}

jclass
class_new_classref(JNIEnv *env, ClassIndex index, jclass classref)
{
    ClassInfo *info;

    HPROF_ASSERT(classref!=NULL);
    info = get_info(index);
    if ( ! isSameObject(env, classref, info->classref) ) {
        delete_classref(env, info, classref);
    }
    return info->classref;
}

jclass
class_get_class(JNIEnv *env, ClassIndex index)
{
    ClassInfo *info;
    jclass     clazz;

    info        = get_info(index);
    clazz       = info->classref;
    if ( env != NULL && clazz == NULL ) {
        WITH_LOCAL_REFS(env, 1) {
            jclass   new_clazz;
            char    *class_name;

            class_name = string_get(info->name);
            /* This really only makes sense for the bootclass classes,
             *   since FindClass doesn't provide a way to load a class in
             *   a specific class loader.
             */
            new_clazz = findClass(env, class_name);
            if ( new_clazz == NULL ) {
                HPROF_ERROR(JNI_TRUE, "Cannot load class with findClass");
            }
            HPROF_ASSERT(new_clazz!=NULL);
            clazz = class_new_classref(env, index, new_clazz);
        } END_WITH_LOCAL_REFS;
        HPROF_ASSERT(clazz!=NULL);
    }
    return clazz;
}

jmethodID
class_get_methodID(JNIEnv *env, ClassIndex index, MethodIndex mnum)
{
    ClassInfo *info;
    jmethodID  method;

    info = get_info(index);
    HPROF_ASSERT(mnum < info->method_count);
    method = info->method[mnum].method_id;
    if ( method == NULL ) {
        char * name;
        char * sig;
        jclass clazz;

        name  = (char *)string_get(info->method[mnum].name_index);
        HPROF_ASSERT(name!=NULL);
        sig   = (char *)string_get(info->method[mnum].sig_index);
        HPROF_ASSERT(sig!=NULL);
        clazz = class_get_class(env, index);
        if ( clazz != NULL ) {
            method = getMethodID(env, clazz, name, sig);
            HPROF_ASSERT(method!=NULL);
            info = get_info(index);
            info->method[mnum].method_id = method;
        }
    }
    return method;
}

void
class_set_inst_size(ClassIndex index, jint inst_size)
{
    ClassInfo *info;

    info = get_info(index);
    info->inst_size = inst_size;
}

jint
class_get_inst_size(ClassIndex index)
{
    ClassInfo *info;

    info = get_info(index);
    return info->inst_size;
}

void
class_set_object_index(ClassIndex index, ObjectIndex object_index)
{
    ClassInfo *info;

    info = get_info(index);
    info->object_index = object_index;
}

ObjectIndex
class_get_object_index(ClassIndex index)
{
    ClassInfo *info;

    info = get_info(index);
    return info->object_index;
}

ClassIndex
class_get_super(ClassIndex index)
{
    ClassInfo *info;

    info = get_info(index);
    return info->super;
}

void
class_set_super(ClassIndex index, ClassIndex super)
{
    ClassInfo *info;

    info = get_info(index);
    info->super = super;
}

LoaderIndex
class_get_loader(ClassIndex index)
{
    ClassKey *pkey;

    pkey = get_pkey(index);
    HPROF_ASSERT(pkey->loader_index!=0);
    return pkey->loader_index;
}

/* Get ALL class fields (supers too), return 1 on error, 0 if ok */
jint
class_get_all_fields(JNIEnv *env, ClassIndex index,
                jint *pfield_count, FieldInfo **pfield)
{
    ClassInfo  *info;
    FieldInfo  *finfo;
    jint        count;
    jint        ret;

    count = 0;
    finfo = NULL;
    ret   = 1;       /* Default is to return an error condition */

    info = get_info(index);
    if ( info != NULL ) {
        if ( info->field_count >= 0 ) {
            /* Get cache */
            count = info->field_count;
            finfo = info->field;
            ret   = 0;                 /* Return of cache data, no error */
        } else {
            jclass     klass;

            klass = info->classref;
            if ( klass == NULL || isSameObject(env, klass, NULL) ) {
                /* This is probably an error because this will cause the field
                 *    index values to be off, but I'm hesitant to generate a
                 *    fatal error here, so I will issue something and continue.
                 *    I should have been holding a global reference to all the
                 *    jclass, so I'm not sure how this could happen.
                 *    Issuing a FindClass() here is just asking for trouble
                 *    because if the class went away, we aren't even sure
                 *    what ClassLoader to use.
                 */
                HPROF_ERROR(JNI_FALSE, "Missing jclass when fields needed");
            } else {
                jint status;

                status = getClassStatus(klass);
                if ( status &
                    (JVMTI_CLASS_STATUS_PRIMITIVE|JVMTI_CLASS_STATUS_ARRAY) ) {
                    /* Set cache */
                    info->field_count = count;
                    info->field       = finfo;
                    ret               = 0;      /* Primitive or array ok */
                } else if ( status & JVMTI_CLASS_STATUS_PREPARED ) {
                    /* Call JVMTI to get them */
                    getAllClassFieldInfo(env, klass, &count, &finfo);
                    /* Set cache */
                    info->field_count = count;
                    info->field       = finfo;
                    ret               = 0;
                }
            }
        }
    }
    *pfield_count = count;
    *pfield       = finfo;
    return ret;
}
