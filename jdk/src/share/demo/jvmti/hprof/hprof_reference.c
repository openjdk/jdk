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


/* Object references table (used in hprof_object.c). */

/*
 * This table is used by the object table to store object reference
 *   and primitive data information obtained from iterations over the
 *   heap (see hprof_site.c).
 *
 * Most of these table entries have no Key, but the key is used to store
 *   the primitive array and primitive field jvalue. None of these entries
 *   are ever looked up, there will be no hash table, use of the
 *   LookupTable was just an easy way to handle a unbounded table of
 *   entries. The object table (see hprof_object.c) will completely
 *   free this reference table after each heap dump or after processing the
 *   references and primitive data.
 *
 * The hprof format required this accumulation of all heap iteration
 *   references and primitive data from objects in order to compose an
 *   hprof records for it.
 *
 * This file contains detailed understandings of how an hprof CLASS
 *   and INSTANCE dump is constructed, most of this is derived from the
 *   original hprof code, but some has been derived by reading the HAT
 *   code that accepts this format.
 *
 */

#include "hprof.h"

/* The flavor of data being saved in the RefInfo */
enum {
    INFO_OBJECT_REF_DATA    = 1,
    INFO_PRIM_FIELD_DATA    = 2,
    INFO_PRIM_ARRAY_DATA    = 3
};

/* Reference information, object reference or primitive data information */
typedef struct RefInfo {
    ObjectIndex object_index; /* If an object reference, the referree index */
    jint        index;        /* If array or field, array or field index */
    jint        length;       /* If array the element count, if not -1 */
    RefIndex    next;         /* The next table element */
    unsigned    flavor   : 8; /* INFO_*, flavor of RefInfo */
    unsigned    refKind  : 8; /* The kind of reference */
    unsigned    primType : 8; /* If primitive data involved, it's type */
} RefInfo;

/* Private internal functions. */

/* Get the RefInfo structure from an entry */
static RefInfo *
get_info(RefIndex index)
{
    RefInfo *info;

    info = (RefInfo*)table_get_info(gdata->reference_table, index);
    return info;
}

/* Get a jvalue that was stored as the key. */
static jvalue
get_key_value(RefIndex index)
{
    void  *key;
    int    len;
    jvalue value;
    static jvalue empty_value;

    key = NULL;
    table_get_key(gdata->reference_table, index, &key, &len);
    HPROF_ASSERT(key!=NULL);
    HPROF_ASSERT(len==(int)sizeof(jvalue));
    if ( key != NULL ) {
        (void)memcpy(&value, key, (int)sizeof(jvalue));
    } else {
        value = empty_value;
    }
    return value;
}

/* Get size of a primitive type */
static jint
get_prim_size(jvmtiPrimitiveType primType)
{
    jint size;

    switch ( primType ) {
        case JVMTI_PRIMITIVE_TYPE_BOOLEAN:
            size = (jint)sizeof(jboolean);
            break;
        case JVMTI_PRIMITIVE_TYPE_BYTE:
            size = (jint)sizeof(jbyte);
            break;
        case JVMTI_PRIMITIVE_TYPE_CHAR:
            size = (jint)sizeof(jchar);
            break;
        case JVMTI_PRIMITIVE_TYPE_SHORT:
            size = (jint)sizeof(jshort);
            break;
        case JVMTI_PRIMITIVE_TYPE_INT:
            size = (jint)sizeof(jint);
            break;
        case JVMTI_PRIMITIVE_TYPE_FLOAT:
            size = (jint)sizeof(jfloat);
            break;
        case JVMTI_PRIMITIVE_TYPE_LONG:
            size = (jint)sizeof(jlong);
            break;
        case JVMTI_PRIMITIVE_TYPE_DOUBLE:
            size = (jint)sizeof(jdouble);
            break;
        default:
            HPROF_ASSERT(0);
            size = 1;
            break;
    }
    return size;
}

/* Get a void* elements array that was stored as the key. */
static void *
get_key_elements(RefIndex index, jvmtiPrimitiveType primType,
                 jint *nelements, jint *nbytes)
{
    void  *key;
    jint   byteLen;

    HPROF_ASSERT(nelements!=NULL);
    HPROF_ASSERT(nbytes!=NULL);

    table_get_key(gdata->reference_table, index, &key, &byteLen);
    HPROF_ASSERT(byteLen>=0);
    HPROF_ASSERT(byteLen!=0?key!=NULL:key==NULL);
    *nbytes      = byteLen;
    *nelements   = byteLen / get_prim_size(primType);
    return key;
}

/* Dump a RefInfo* structure */
static void
dump_ref_info(RefInfo *info)
{
    debug_message("[%d]: flavor=%d"
                          ", refKind=%d"
                          ", primType=%d"
                          ", object_index=0x%x"
                          ", length=%d"
                          ", next=0x%x"
                          "\n",
            info->index,
            info->flavor,
            info->refKind,
            info->primType,
            info->object_index,
            info->length,
            info->next);
}

/* Dump a RefIndex list */
static void
dump_ref_list(RefIndex list)
{
    RefInfo *info;
    RefIndex index;

    debug_message("\nFOLLOW REFERENCES RETURNED:\n");
    index = list;
    while ( index != 0 ) {
        info = get_info(index);
        dump_ref_info(info);
        index = info->next;
    }
}

/* Dump information about a field and what ref data we had on it */
static void
dump_field(FieldInfo *fields, jvalue *fvalues, int n_fields,
                jint index, jvalue value, jvmtiPrimitiveType primType)
{
    ClassIndex  cnum;
    StringIndex name;
    StringIndex sig;

    cnum = fields[index].cnum;
    name = fields[index].name_index;
    sig  = fields[index].sig_index;
    debug_message("[%d] %s \"%s\" \"%s\"",
          index,
          cnum!=0?string_get(class_get_signature(cnum)):"?",
          name!=0?string_get(name):"?",
          sig!=0?string_get(sig):"?");
    if ( fields[index].primType!=0 || fields[index].primType!=primType ) {
        debug_message(" (primType=%d(%c)",
          fields[index].primType,
          primTypeToSigChar(fields[index].primType));
        if ( primType != fields[index].primType ) {
            debug_message(", got %d(%c)",
              primType,
              primTypeToSigChar(primType));
        }
        debug_message(")");
    } else {
        debug_message("(ty=OBJ)");
    }
    if ( value.j != (jlong)0 || fvalues[index].j != (jlong)0 ) {
        debug_message(" val=[0x%08x,0x%08x] or [0x%08x,0x%08x]",
            jlong_high(value.j), jlong_low(value.j),
            jlong_high(fvalues[index].j), jlong_low(fvalues[index].j));
    }
    debug_message("\n");
}

/* Dump all the fields of interest */
static void
dump_fields(RefIndex list, FieldInfo *fields, jvalue *fvalues, int n_fields)
{
    int i;

    debug_message("\nHPROF LIST OF ALL FIELDS:\n");
    for ( i = 0 ; i < n_fields ; i++ ) {
        if ( fields[i].name_index != 0 ) {
            dump_field(fields, fvalues, n_fields, i, fvalues[i], fields[i].primType);
        }
    }
    dump_ref_list(list);
}

/* Verify field data */
static void
verify_field(RefIndex list, FieldInfo *fields, jvalue *fvalues, int n_fields,
                jint index, jvalue value, jvmtiPrimitiveType primType)
{
    HPROF_ASSERT(fvalues != NULL);
    HPROF_ASSERT(n_fields > 0);
    HPROF_ASSERT(index < n_fields);
    HPROF_ASSERT(index >= 0 );
    if ( primType!=fields[index].primType ) {
        dump_fields(list, fields, fvalues, n_fields);
        debug_message("\nPROBLEM WITH:\n");
        dump_field(fields, fvalues, n_fields, index, value, primType);
        debug_message("\n");
        HPROF_ERROR(JNI_FALSE, "Trouble with fields and heap data");
    }
    if ( primType == JVMTI_PRIMITIVE_TYPE_BOOLEAN &&
         ( value.b != 1 && value.b != 0 ) ) {
        dump_fields(list, fields, fvalues, n_fields);
        debug_message("\nPROBLEM WITH:\n");
        dump_field(fields, fvalues, n_fields, index, value, primType);
        debug_message("\n");
        HPROF_ERROR(JNI_FALSE, "Trouble with fields and heap data");
    }
}

/* Fill in a field value, making sure the index is safe */
static void
fill_in_field_value(RefIndex list, FieldInfo *fields, jvalue *fvalues,
                    int n_fields, jint index, jvalue value,
                    jvmtiPrimitiveType primType)
{
    HPROF_ASSERT(fvalues != NULL);
    HPROF_ASSERT(n_fields > 0);
    HPROF_ASSERT(index < n_fields);
    HPROF_ASSERT(index >= 0 );
    HPROF_ASSERT(fvalues[index].j==(jlong)0);
    verify_field(list, fields, fvalues, n_fields, index, value, primType);
    if (index >= 0 && index < n_fields) {
        fvalues[index] = value;
    }
}

/* Walk all references for an ObjectIndex and construct the hprof CLASS dump. */
static void
dump_class_and_supers(JNIEnv *env, ObjectIndex object_index, RefIndex list)
{
    SiteIndex    site_index;
    SerialNumber trace_serial_num;
    RefIndex     index;
    ClassIndex   super_cnum;
    ObjectIndex  super_index;
    LoaderIndex  loader_index;
    ObjectIndex  signers_index;
    ObjectIndex  domain_index;
    FieldInfo   *fields;
    jvalue      *fvalues;
    jint         n_fields;
    jboolean     skip_fields;
    jint         n_fields_set;
    jlong        size;
    ClassIndex   cnum;
    char        *sig;
    ObjectKind   kind;
    TraceIndex   trace_index;
    Stack       *cpool_values;
    ConstantPoolValue *cpool;
    jint         cpool_count;

    HPROF_ASSERT(object_index!=0);
    kind        = object_get_kind(object_index);
    if ( kind != OBJECT_CLASS ) {
        return;
    }
    site_index         = object_get_site(object_index);
    HPROF_ASSERT(site_index!=0);
    cnum        = site_get_class_index(site_index);
    HPROF_ASSERT(cnum!=0);
    if ( class_get_status(cnum) & CLASS_DUMPED ) {
        return;
    }
    class_add_status(cnum, CLASS_DUMPED);
    size        = (jlong)object_get_size(object_index);

    super_index = 0;
    super_cnum  = class_get_super(cnum);
    if ( super_cnum != 0 ) {
        super_index  = class_get_object_index(super_cnum);
        if ( super_index != 0 ) {
            dump_class_and_supers(env, super_index,
                        object_get_references(super_index));
        }
    }

    trace_index      = site_get_trace_index(site_index);
    HPROF_ASSERT(trace_index!=0);
    trace_serial_num = trace_get_serial_number(trace_index);
    sig              = string_get(class_get_signature(cnum));
    loader_index     = class_get_loader(cnum);
    signers_index    = 0;
    domain_index     = 0;

    /* Get field information */
    n_fields     = 0;
    skip_fields  = JNI_FALSE;
    n_fields_set = 0;
    fields       = NULL;
    fvalues      = NULL;
    if ( class_get_all_fields(env, cnum, &n_fields, &fields) == 1 ) {
        /* Problems getting all the fields, can't trust field index values */
        skip_fields = JNI_TRUE;
        /* Class with no references at all? (ok to be unprepared if list==0?) */
        if ( list != 0 ) {
            /* It is assumed that the reason why we didn't get the fields
             *     was because the class is not prepared.
             */
            if ( gdata->debugflags & DEBUGFLAG_UNPREPARED_CLASSES ) {
                dump_ref_list(list);
                debug_message("Unprepared class with references: %s\n",
                               sig);
            }
            HPROF_ERROR(JNI_FALSE, "Trouble with unprepared classes");
        }
        /* Why would an unprepared class contain references? */
    }
    if ( n_fields > 0 ) {
        fvalues      = (jvalue*)HPROF_MALLOC(n_fields*(int)sizeof(jvalue));
        (void)memset(fvalues, 0, n_fields*(int)sizeof(jvalue));
    }

    /* We use a Stack just because it will automatically expand as needed */
    cpool_values = stack_init(16, 16, sizeof(ConstantPoolValue));
    cpool = NULL;
    cpool_count = 0;

    index      = list;
    while ( index != 0 ) {
        RefInfo    *info;
        jvalue      ovalue;
        static jvalue empty_value;

        info = get_info(index);

        switch ( info->flavor ) {
            case INFO_OBJECT_REF_DATA:
                switch ( info->refKind ) {
                    case JVMTI_HEAP_REFERENCE_FIELD:
                    case JVMTI_HEAP_REFERENCE_ARRAY_ELEMENT:
                        /* Should never be seen on a class dump */
                        HPROF_ASSERT(0);
                        break;
                    case JVMTI_HEAP_REFERENCE_STATIC_FIELD:
                        if ( skip_fields == JNI_TRUE ) {
                            break;
                        }
                        ovalue   = empty_value;
                        ovalue.i = info->object_index;
                        fill_in_field_value(list, fields, fvalues, n_fields,
                                        info->index, ovalue, 0);
                        n_fields_set++;
                        HPROF_ASSERT(n_fields_set <= n_fields);
                        break;
                    case JVMTI_HEAP_REFERENCE_CONSTANT_POOL: {
                        ConstantPoolValue cpv;
                        ObjectIndex       cp_object_index;
                        SiteIndex         cp_site_index;
                        ClassIndex        cp_cnum;

                        cp_object_index = info->object_index;
                        HPROF_ASSERT(cp_object_index!=0);
                        cp_site_index = object_get_site(cp_object_index);
                        HPROF_ASSERT(cp_site_index!=0);
                        cp_cnum = site_get_class_index(cp_site_index);
                        cpv.constant_pool_index = info->index;
                        cpv.sig_index = class_get_signature(cp_cnum);
                        cpv.value.i = cp_object_index;
                        stack_push(cpool_values, (void*)&cpv);
                        cpool_count++;
                        break;
                        }
                    case JVMTI_HEAP_REFERENCE_SIGNERS:
                        signers_index = info->object_index;
                        break;
                    case JVMTI_HEAP_REFERENCE_PROTECTION_DOMAIN:
                        domain_index = info->object_index;
                        break;
                    case JVMTI_HEAP_REFERENCE_CLASS_LOADER:
                    case JVMTI_HEAP_REFERENCE_INTERFACE:
                    default:
                        /* Ignore, not needed */
                        break;
                }
                break;
            case INFO_PRIM_FIELD_DATA:
                if ( skip_fields == JNI_TRUE ) {
                    break;
                }
                HPROF_ASSERT(info->primType!=0);
                HPROF_ASSERT(info->length==-1);
                HPROF_ASSERT(info->refKind==JVMTI_HEAP_REFERENCE_STATIC_FIELD);
                ovalue = get_key_value(index);
                fill_in_field_value(list, fields, fvalues, n_fields,
                                    info->index, ovalue, info->primType);
                n_fields_set++;
                HPROF_ASSERT(n_fields_set <= n_fields);
                break;
            case INFO_PRIM_ARRAY_DATA:
            default:
                /* Should never see these */
                HPROF_ASSERT(0);
                break;
        }

        index = info->next;
    }

    /* Get constant pool data if we have any */
    HPROF_ASSERT(cpool_count==stack_depth(cpool_values));
    if ( cpool_count > 0 ) {
        cpool = (ConstantPoolValue*)stack_element(cpool_values, 0);
    }
    io_heap_class_dump(cnum, sig, object_index, trace_serial_num,
            super_index,
            loader_object_index(env, loader_index),
            signers_index, domain_index,
            (jint)size, cpool_count, cpool, n_fields, fields, fvalues);

    stack_term(cpool_values);
    if ( fvalues != NULL ) {
        HPROF_FREE(fvalues);
    }
}

/* Walk all references for an ObjectIndex and construct the hprof INST dump. */
static void
dump_instance(JNIEnv *env, ObjectIndex object_index, RefIndex list)
{
    jvmtiPrimitiveType primType;
    SiteIndex    site_index;
    SerialNumber trace_serial_num;
    RefIndex     index;
    ObjectIndex  class_index;
    jlong        size;
    ClassIndex   cnum;
    char        *sig;
    void        *elements;
    jint         num_elements;
    jint         num_bytes;
    ObjectIndex *values;
    FieldInfo   *fields;
    jvalue      *fvalues;
    jint         n_fields;
    jboolean     skip_fields;
    jint         n_fields_set;
    ObjectKind   kind;
    TraceIndex   trace_index;
    jboolean     is_array;
    jboolean     is_prim_array;

    HPROF_ASSERT(object_index!=0);
    kind        = object_get_kind(object_index);
    if ( kind == OBJECT_CLASS ) {
        return;
    }
    site_index       = object_get_site(object_index);
    HPROF_ASSERT(site_index!=0);
    cnum             = site_get_class_index(site_index);
    HPROF_ASSERT(cnum!=0);
    size             = (jlong)object_get_size(object_index);
    trace_index      = site_get_trace_index(site_index);
    HPROF_ASSERT(trace_index!=0);
    trace_serial_num = trace_get_serial_number(trace_index);
    sig              = string_get(class_get_signature(cnum));
    class_index      = class_get_object_index(cnum);

    values       = NULL;
    elements     = NULL;
    num_elements = 0;
    num_bytes    = 0;

    n_fields     = 0;
    skip_fields  = JNI_FALSE;
    n_fields_set = 0;
    fields       = NULL;
    fvalues      = NULL;

    index      = list;

    is_array      = JNI_FALSE;
    is_prim_array = JNI_FALSE;

    if ( sig[0] != JVM_SIGNATURE_ARRAY ) {
        if ( class_get_all_fields(env, cnum, &n_fields, &fields) == 1 ) {
            /* Trouble getting all the fields, can't trust field index values */
            skip_fields = JNI_TRUE;
            /* It is assumed that the reason why we didn't get the fields
             *     was because the class is not prepared.
             */
            if ( gdata->debugflags & DEBUGFLAG_UNPREPARED_CLASSES ) {
                if ( list != 0 ) {
                    dump_ref_list(list);
                    debug_message("Instance of unprepared class with refs: %s\n",
                                   sig);
                } else {
                    debug_message("Instance of unprepared class without refs: %s\n",
                                   sig);
                }
                HPROF_ERROR(JNI_FALSE, "Big Trouble with unprepared class instances");
            }
        }
        if ( n_fields > 0 ) {
            fvalues = (jvalue*)HPROF_MALLOC(n_fields*(int)sizeof(jvalue));
            (void)memset(fvalues, 0, n_fields*(int)sizeof(jvalue));
        }
    } else {
        is_array = JNI_TRUE;
        if ( sig[0] != 0 && sigToPrimSize(sig+1) != 0 ) {
            is_prim_array = JNI_TRUE;
        }
    }

    while ( index != 0 ) {
        RefInfo *info;
        jvalue   ovalue;
        static jvalue empty_value;

        info = get_info(index);

        /* Process reference objects, many not used right now. */
        switch ( info->flavor ) {
            case INFO_OBJECT_REF_DATA:
                switch ( info->refKind ) {
                    case JVMTI_HEAP_REFERENCE_SIGNERS:
                    case JVMTI_HEAP_REFERENCE_PROTECTION_DOMAIN:
                    case JVMTI_HEAP_REFERENCE_CLASS_LOADER:
                    case JVMTI_HEAP_REFERENCE_INTERFACE:
                    case JVMTI_HEAP_REFERENCE_STATIC_FIELD:
                    case JVMTI_HEAP_REFERENCE_CONSTANT_POOL:
                        /* Should never be seen on an instance dump */
                        HPROF_ASSERT(0);
                        break;
                    case JVMTI_HEAP_REFERENCE_FIELD:
                        if ( skip_fields == JNI_TRUE ) {
                            break;
                        }
                        HPROF_ASSERT(is_array!=JNI_TRUE);
                        ovalue   = empty_value;
                        ovalue.i = info->object_index;
                        fill_in_field_value(list, fields, fvalues, n_fields,
                                        info->index, ovalue, 0);
                        n_fields_set++;
                        HPROF_ASSERT(n_fields_set <= n_fields);
                        break;
                    case JVMTI_HEAP_REFERENCE_ARRAY_ELEMENT:
                        /* We get each object element one at a time.  */
                        HPROF_ASSERT(is_array==JNI_TRUE);
                        HPROF_ASSERT(is_prim_array!=JNI_TRUE);
                        if ( num_elements <= info->index ) {
                            int nbytes;

                            if ( values == NULL ) {
                                num_elements = info->index + 1;
                                nbytes = num_elements*(int)sizeof(ObjectIndex);
                                values = (ObjectIndex*)HPROF_MALLOC(nbytes);
                                (void)memset(values, 0, nbytes);
                            } else {
                                void *new_values;
                                int   new_size;
                                int   obytes;

                                obytes = num_elements*(int)sizeof(ObjectIndex);
                                new_size = info->index + 1;
                                nbytes = new_size*(int)sizeof(ObjectIndex);
                                new_values = (void*)HPROF_MALLOC(nbytes);
                                (void)memcpy(new_values, values, obytes);
                                (void)memset(((char*)new_values)+obytes, 0,
                                                        nbytes-obytes);
                                HPROF_FREE(values);
                                num_elements = new_size;
                                values =  new_values;
                            }
                        }
                        HPROF_ASSERT(values[info->index]==0);
                        values[info->index] = info->object_index;
                        break;
                    default:
                        /* Ignore, not needed */
                        break;
                }
                break;
            case INFO_PRIM_FIELD_DATA:
                if ( skip_fields == JNI_TRUE ) {
                    break;
                }
                HPROF_ASSERT(info->primType!=0);
                HPROF_ASSERT(info->length==-1);
                HPROF_ASSERT(info->refKind==JVMTI_HEAP_REFERENCE_FIELD);
                HPROF_ASSERT(is_array!=JNI_TRUE);
                ovalue = get_key_value(index);
                fill_in_field_value(list, fields, fvalues, n_fields,
                                    info->index, ovalue, info->primType);
                n_fields_set++;
                HPROF_ASSERT(n_fields_set <= n_fields);
                break;
            case INFO_PRIM_ARRAY_DATA:
                /* Should only be one, and it's handled below */
                HPROF_ASSERT(info->refKind==0);
                /* We assert that nothing else was saved with this array */
                HPROF_ASSERT(index==list&&info->next==0);
                HPROF_ASSERT(is_array==JNI_TRUE);
                HPROF_ASSERT(is_prim_array==JNI_TRUE);
                primType = info->primType;
                elements = get_key_elements(index, primType,
                                            &num_elements, &num_bytes);
                HPROF_ASSERT(info->length==num_elements);
                size = num_bytes;
                break;
            default:
                HPROF_ASSERT(0);
                break;
        }
        index = info->next;
    }

    if ( is_array == JNI_TRUE ) {
        if ( is_prim_array == JNI_TRUE ) {
            HPROF_ASSERT(values==NULL);
            io_heap_prim_array(object_index, trace_serial_num,
                    (jint)size, num_elements, sig, elements);
        } else {
            HPROF_ASSERT(elements==NULL);
            io_heap_object_array(object_index, trace_serial_num,
                    (jint)size, num_elements, sig, values, class_index);
        }
    } else {
        io_heap_instance_dump(cnum, object_index, trace_serial_num,
                    class_index, (jint)size, sig, fields, fvalues, n_fields);
    }
    if ( values != NULL ) {
        HPROF_FREE(values);
    }
    if ( fvalues != NULL ) {
        HPROF_FREE(fvalues);
    }
    if ( elements != NULL ) {
        /* Do NOT free elements, it's a key in the table, leave it be */
    }
}

/* External interfaces. */

void
reference_init(void)
{
    HPROF_ASSERT(gdata->reference_table==NULL);
    gdata->reference_table = table_initialize("Ref", 2048, 4096, 0,
                            (int)sizeof(RefInfo));
}

/* Save away a reference to an object */
RefIndex
reference_obj(RefIndex next, jvmtiHeapReferenceKind refKind,
              ObjectIndex object_index, jint index, jint length)
{
    static RefInfo  empty_info;
    RefIndex        entry;
    RefInfo         info;

    info                = empty_info;
    info.flavor         = INFO_OBJECT_REF_DATA;
    info.refKind        = refKind;
    info.object_index   = object_index;
    info.index          = index;
    info.length         = length;
    info.next           = next;
    entry = table_create_entry(gdata->reference_table, NULL, 0, (void*)&info);
    return entry;
}

/* Save away some primitive field data */
RefIndex
reference_prim_field(RefIndex next, jvmtiHeapReferenceKind refKind,
              jvmtiPrimitiveType primType, jvalue field_value, jint field_index)
{
    static RefInfo  empty_info;
    RefIndex        entry;
    RefInfo         info;

    HPROF_ASSERT(primType==JVMTI_PRIMITIVE_TYPE_BOOLEAN?(field_value.b==1||field_value.b==0):1);

    info                = empty_info;
    info.flavor         = INFO_PRIM_FIELD_DATA;
    info.refKind        = refKind;
    info.primType       = primType;
    info.index          = field_index;
    info.length         = -1;
    info.next           = next;
    entry = table_create_entry(gdata->reference_table,
                (void*)&field_value, (int)sizeof(jvalue), (void*)&info);
    return entry;
}

/* Save away some primitive array data */
RefIndex
reference_prim_array(RefIndex next, jvmtiPrimitiveType primType,
              const void *elements, jint elementCount)
{
    static RefInfo  empty_info;
    RefIndex        entry;
    RefInfo         info;

    HPROF_ASSERT(next == 0);
    HPROF_ASSERT(elementCount >= 0);
    HPROF_ASSERT(elements != NULL);

    info                = empty_info;
    info.flavor         = INFO_PRIM_ARRAY_DATA;
    info.refKind        = 0;
    info.primType       = primType;
    info.index          = 0;
    info.length         = elementCount;
    info.next           = next;
    entry = table_create_entry(gdata->reference_table, (void*)elements,
                         elementCount * get_prim_size(primType), (void*)&info);
    return entry;
}

void
reference_cleanup(void)
{
    if ( gdata->reference_table == NULL ) {
        return;
    }
    table_cleanup(gdata->reference_table, NULL, NULL);
    gdata->reference_table = NULL;
}

void
reference_dump_instance(JNIEnv *env, ObjectIndex object_index, RefIndex list)
{
    dump_instance(env, object_index, list);
}

void
reference_dump_class(JNIEnv *env, ObjectIndex object_index, RefIndex list)
{
    dump_class_and_supers(env, object_index, list);
}
