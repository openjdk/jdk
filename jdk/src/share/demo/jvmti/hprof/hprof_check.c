/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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


/* Functionality for checking hprof format=b output. */

/* ONLY used with logflags=4. */

/* Verifies and write a verbose textual version of a format=b file.
 *   Textual output file is gdata->checkfilename, fd is gdata->check_fd.
 *   Buffer is in gdata too, see gdata->check* variables.
 *   Could probably be isolated to a separate library or utility.
 */

#include "hprof.h"

typedef TableIndex HprofId;

#include "hprof_b_spec.h"

static int type_size[ /*HprofType*/ ] =  HPROF_TYPE_SIZES;

/* For map from HPROF_UTF8 to a string */
typedef struct UmapInfo {
    char *str;
} UmapInfo;

/* Field information */
typedef struct Finfo {
    HprofId   id;
    HprofType ty;
} Finfo;

/* Class information map from class ID (ClassIndex) to class information */
typedef struct CmapInfo {
    int      max_finfo;
    int      n_finfo;
    Finfo   *finfo;
    int      inst_size;
    HprofId  sup;
} CmapInfo;

/* Read raw bytes from the file image, update the pointer */
static void
read_raw(unsigned char **pp, unsigned char *buf, int len)
{
    while ( len > 0 ) {
        *buf = **pp;
        buf++;
        (*pp)++;
        len--;
    }
}

/* Read various sized elements, properly converted from big to right endian.
 *    File will contain big endian format.
 */
static unsigned
read_u1(unsigned char **pp)
{
    unsigned char b;

    read_raw(pp, &b, 1);
    return b;
}
static unsigned
read_u2(unsigned char **pp)
{
    unsigned short s;

    read_raw(pp, (void*)&s, 2);
    return md_htons(s);
}
static unsigned
read_u4(unsigned char **pp)
{
    unsigned int u;

    read_raw(pp, (void*)&u, 4);
    return md_htonl(u);
}
static jlong
read_u8(unsigned char **pp)
{
    unsigned int high;
    unsigned int low;
    jlong        x;

    high = read_u4(pp);
    low  = read_u4(pp);
    x = high;
    x = (x << 32) | low;
    return x;
}
static HprofId
read_id(unsigned char **pp)
{
    return (HprofId)read_u4(pp);
}

/* System error routine */
static void
system_error(const char *system_call, int rc, int errnum)
{
    char buf[256];
    char details[256];

    details[0] = 0;
    if ( errnum != 0 ) {
        md_system_error(details, (int)sizeof(details));
    } else if ( rc >= 0 ) {
        (void)strcpy(details,"Only part of buffer processed");
    }
    if ( details[0] == 0 ) {
        (void)strcpy(details,"Unknown system error condition");
    }
    (void)md_snprintf(buf, sizeof(buf), "System %s failed: %s\n",
                            system_call, details);
    HPROF_ERROR(JNI_TRUE, buf);
}

/* Write to a fd */
static void
system_write(int fd, void *buf, int len)
{
    int res;

    HPROF_ASSERT(fd>=0);
    res = md_write(fd, buf, len);
    if (res < 0 || res!=len) {
        system_error("write", res, errno);
    }
}

/* Flush check buffer */
static void
check_flush(void)
{
    if ( gdata->check_fd < 0 ) {
        return;
    }
    if (gdata->check_buffer_index) {
        system_write(gdata->check_fd, gdata->check_buffer, gdata->check_buffer_index);
        gdata->check_buffer_index = 0;
    }
}

/* Read out a given typed element */
static jvalue
read_val(unsigned char **pp, HprofType ty)
{
    jvalue        val;
    static jvalue empty_val;

    val = empty_val;
    switch ( ty ) {
        case 0:
        case HPROF_ARRAY_OBJECT:
        case HPROF_NORMAL_OBJECT:
            val.i = read_id(pp);
            break;
        case HPROF_BYTE:
        case HPROF_BOOLEAN:
            val.b = read_u1(pp);
            break;
        case HPROF_CHAR:
        case HPROF_SHORT:
            val.s = read_u2(pp);
            break;
        case HPROF_FLOAT:
        case HPROF_INT:
            val.i = read_u4(pp);
            break;
        case HPROF_DOUBLE:
        case HPROF_LONG:
            val.j = read_u8(pp);
            break;
        default:
            HPROF_ERROR(JNI_TRUE, "bad type number");
            break;
    }
    return val;
}

/* Move arbitrary byte stream into gdata->check_fd */
static void
check_raw(void *buf, int len)
{
    if ( gdata->check_fd < 0 ) {
        return;
    }

    if ( len <= 0 ) {
        return;
    }

    if (gdata->check_buffer_index + len > gdata->check_buffer_size) {
        check_flush();
        if (len > gdata->check_buffer_size) {
            system_write(gdata->check_fd, buf, len);
            return;
        }
    }
    (void)memcpy(gdata->check_buffer + gdata->check_buffer_index, buf, len);
    gdata->check_buffer_index += len;
}

/* Printf for gdata->check_fd */
static void
check_printf(char *fmt, ...)
{
    char    buf[1024];
    va_list args;

    if ( gdata->check_fd < 0 ) {
        return;
    }

    va_start(args, fmt);
    (void)md_vsnprintf(buf, sizeof(buf), fmt, args);
    buf[sizeof(buf)-1] = 0;
    check_raw(buf, (int)strlen(buf));
    va_end(args);
}

/* Printf of an element for gdata->check_fd */
static void
check_printf_val(HprofType ty, jvalue val, int long_form)
{
    jint low;
    jint high;

    switch ( ty ) {
        case HPROF_ARRAY_OBJECT:
            check_printf("0x%08x", val.i);
            break;
        case HPROF_NORMAL_OBJECT:
            check_printf("0x%08x", val.i);
            break;
        case HPROF_BOOLEAN:
            check_printf("0x%02x", val.b);
            break;
        case HPROF_CHAR:
            if ( long_form ) {
                if ( val.s < 0 || val.s > 0x7f || !isprint(val.s) ) {
                    check_printf("0x%04x", val.s);
                } else {
                    check_printf("0x%04x(%c)", val.s, val.s);
                }
            } else {
                if ( val.s < 0 || val.s > 0x7f || !isprint(val.s) ) {
                    check_printf("\\u%04x", val.s);
                } else {
                    check_printf("%c", val.s);
                }
            }
            break;
        case HPROF_FLOAT:
            low  = jlong_low(val.j);
            check_printf("0x%08x(%f)", low, (double)val.f);
            break;
        case HPROF_DOUBLE:
            high = jlong_high(val.j);
            low  = jlong_low(val.j);
            check_printf("0x%08x%08x(%f)", high, low, val.d);
            break;
        case HPROF_BYTE:
            check_printf("0x%02x", val.b);
            break;
        case HPROF_SHORT:
            check_printf("0x%04x", val.s);
            break;
        case HPROF_INT:
            check_printf("0x%08x", val.i);
            break;
        case HPROF_LONG:
            high = jlong_high(val.j);
            low  = jlong_low(val.j);
            check_printf("0x%08x%08x", high, low);
            break;
    }
}

/* Printf of a string for gdata->check_fd */
static void
check_printf_str(char *str)
{
    int len;
    int i;

    if ( str == NULL ) {
        check_printf("<null>");
    }
    check_printf("\"");
    len = (int)strlen(str);
    for (i = 0; i < len; i++) {
        unsigned char c;
        c = str[i];
        if ( isprint(c) ) {
            check_printf("%c", c);
        } else {
            check_printf("\\x%02x", c);
        }
    }
    check_printf("\"");
}

/* Printf of a utf8 id for gdata->check_fd */
static void
check_print_utf8(struct LookupTable *utab, char *prefix, HprofId id)
{
    TableIndex uindex;

    if ( id == 0 ) {
        check_printf("%s0x%x", prefix, id);
    } else {
        uindex = table_find_entry(utab, &id, sizeof(id));
        if ( uindex == 0 ) {
            check_printf("%s0x%x", prefix, id);
        } else {
            UmapInfo *umap;

            umap = (UmapInfo*)table_get_info(utab, uindex);
            HPROF_ASSERT(umap!=NULL);
            HPROF_ASSERT(umap->str!=NULL);
            check_printf("%s0x%x->", prefix, id);
            check_printf_str(umap->str);
        }
    }
}

/* Add a instance field information to this cmap. */
static void
add_inst_field_to_cmap(CmapInfo *cmap, HprofId id, HprofType ty)
{
   int i;

   HPROF_ASSERT(cmap!=NULL);
   i = cmap->n_finfo++;
   if ( i+1 >= cmap->max_finfo ) {
       int    osize;
       Finfo *new_finfo;

       osize            = cmap->max_finfo;
       cmap->max_finfo += 12;
       new_finfo = (Finfo*)HPROF_MALLOC(cmap->max_finfo*(int)sizeof(Finfo));
       (void)memset(new_finfo,0,cmap->max_finfo*(int)sizeof(Finfo));
       if ( i == 0 ) {
           cmap->finfo = new_finfo;
       } else {
           (void)memcpy(new_finfo,cmap->finfo,osize*(int)sizeof(Finfo));
           HPROF_FREE(cmap->finfo);
           cmap->finfo = new_finfo;
       }
   }
   cmap->finfo[i].id = id;
   cmap->finfo[i].ty = ty;
}

/* LookupTable callback for cmap entry cleanup */
static void
cmap_cleanup(TableIndex i, void *key_ptr, int key_len, void*info, void*data)
{
    CmapInfo *cmap = info;

    if ( cmap == NULL ) {
        return;
    }
    if ( cmap->finfo != NULL ) {
        HPROF_FREE(cmap->finfo);
        cmap->finfo = NULL;
    }
}

/* Case label for a switch on hprof heap dump elements */
#define CASE_HEAP(name) case name: label = #name;

/* Given the heap dump data and the utf8 map, check/write the heap dump. */
static int
check_heap_tags(struct LookupTable *utab, unsigned char *pstart, int nbytes)
{
    int                 nrecords;
    unsigned char      *p;
    unsigned char      *psave;
    struct LookupTable *ctab;
    CmapInfo            cmap;
    char               *label;
    unsigned            tag;
    HprofType           ty;
    HprofId             id, id2, fr, sup;
    int                 num_elements;
    int                 num_bytes;
    SerialNumber        trace_serial_num;
    SerialNumber        thread_serial_num;
    int                 npos;
    int                 i;
    int                 inst_size;

    ctab     = table_initialize("temp ctab", 64, 64, 512, sizeof(CmapInfo));

    /* First pass over heap records just fills in the CmapInfo table */
    nrecords = 0;
    p        = pstart;
    while ( p < (pstart+nbytes) ) {
        nrecords++;
        /*LINTED*/
        npos = (int)(p - pstart);
        tag  = read_u1(&p);
        switch ( tag ) {
            CASE_HEAP(HPROF_GC_ROOT_UNKNOWN)
                id = read_id(&p);
                break;
            CASE_HEAP(HPROF_GC_ROOT_JNI_GLOBAL)
                id  = read_id(&p);
                id2 = read_id(&p);
                break;
            CASE_HEAP(HPROF_GC_ROOT_JNI_LOCAL)
                id = read_id(&p);
                thread_serial_num = read_u4(&p);
                fr = read_u4(&p);
                break;
            CASE_HEAP(HPROF_GC_ROOT_JAVA_FRAME)
                id = read_id(&p);
                thread_serial_num = read_u4(&p);
                fr = read_u4(&p);
                break;
            CASE_HEAP(HPROF_GC_ROOT_NATIVE_STACK)
                id = read_id(&p);
                thread_serial_num = read_u4(&p);
                break;
            CASE_HEAP(HPROF_GC_ROOT_STICKY_CLASS)
                id = read_id(&p);
                break;
            CASE_HEAP(HPROF_GC_ROOT_THREAD_BLOCK)
                id = read_id(&p);
                thread_serial_num = read_u4(&p);
                break;
            CASE_HEAP(HPROF_GC_ROOT_MONITOR_USED)
                id = read_id(&p);
                break;
            CASE_HEAP(HPROF_GC_ROOT_THREAD_OBJ)
                id = read_id(&p);
                thread_serial_num = read_u4(&p);
                trace_serial_num = read_u4(&p);
                break;
            CASE_HEAP(HPROF_GC_CLASS_DUMP)
                (void)memset((void*)&cmap, 0, sizeof(cmap));
                id = read_id(&p);
                trace_serial_num = read_u4(&p);
                {
                    HprofId ld, si, pr, re1, re2;

                    sup      = read_id(&p);
                    ld       = read_id(&p);
                    si       = read_id(&p);
                    pr       = read_id(&p);
                    re1      = read_id(&p);
                    re2      = read_id(&p);
                    cmap.sup = sup;
                }
                inst_size = read_u4(&p);
                cmap.inst_size = inst_size;
                num_elements = read_u2(&p);
                for(i=0; i<num_elements; i++) {
                    (void)read_u2(&p);
                    ty = read_u1(&p);
                    (void)read_val(&p, ty);
                }
                num_elements = read_u2(&p);
                for(i=0; i<num_elements; i++) {
                    (void)read_id(&p);
                    ty = read_u1(&p);
                    (void)read_val(&p, ty);
                }
                num_elements = read_u2(&p);
                for(i=0; i<num_elements; i++) {
                    HprofType ty;
                    HprofId   id;

                    id = read_id(&p);
                    ty = read_u1(&p);
                    add_inst_field_to_cmap(&cmap, id, ty);
                }
                (void)table_create_entry(ctab, &id, sizeof(id), &cmap);
                break;
            CASE_HEAP(HPROF_GC_INSTANCE_DUMP)
                id = read_id(&p);
                trace_serial_num = read_u4(&p);
                id2 = read_id(&p); /* class id */
                num_bytes = read_u4(&p);
                p += num_bytes;
                break;
            CASE_HEAP(HPROF_GC_OBJ_ARRAY_DUMP)
                id = read_id(&p);
                trace_serial_num = read_u4(&p);
                num_elements = read_u4(&p);
                id2 = read_id(&p);
                p += num_elements*(int)sizeof(HprofId);
                break;
            CASE_HEAP(HPROF_GC_PRIM_ARRAY_DUMP)
                id = read_id(&p);
                trace_serial_num = read_u4(&p);
                num_elements = read_u4(&p);
                ty = read_u1(&p);
                p += type_size[ty]*num_elements;
                break;
            default:
                label = "UNKNOWN";
                check_printf("H#%d@%d %s: ERROR!\n",
                                nrecords, npos, label);
                HPROF_ERROR(JNI_TRUE, "unknown heap record type");
                break;
        }
    }
    CHECK_FOR_ERROR(p==pstart+nbytes);

    /* Scan again once we have our cmap */
    nrecords = 0;
    p        = pstart;
    while ( p < (pstart+nbytes) ) {
        nrecords++;
        /*LINTED*/
        npos = (int)(p - pstart);
        tag  = read_u1(&p);
        switch ( tag ) {
            CASE_HEAP(HPROF_GC_ROOT_UNKNOWN)
                id = read_id(&p);
                check_printf("H#%d@%d %s: id=0x%x\n",
                        nrecords, npos, label, id);
                break;
            CASE_HEAP(HPROF_GC_ROOT_JNI_GLOBAL)
                id = read_id(&p);
                id2 = read_id(&p);
                check_printf("H#%d@%d %s: id=0x%x, id2=0x%x\n",
                        nrecords, npos, label, id, id2);
                break;
            CASE_HEAP(HPROF_GC_ROOT_JNI_LOCAL)
                id = read_id(&p);
                thread_serial_num = read_u4(&p);
                fr = read_u4(&p);
                check_printf("H#%d@%d %s: id=0x%x, thread_serial_num=%u, fr=0x%x\n",
                        nrecords, npos, label, id, thread_serial_num, fr);
                break;
            CASE_HEAP(HPROF_GC_ROOT_JAVA_FRAME)
                id = read_id(&p);
                thread_serial_num = read_u4(&p);
                fr = read_u4(&p);
                check_printf("H#%d@%d %s: id=0x%x, thread_serial_num=%u, fr=0x%x\n",
                        nrecords, npos, label, id, thread_serial_num, fr);
                break;
            CASE_HEAP(HPROF_GC_ROOT_NATIVE_STACK)
                id = read_id(&p);
                thread_serial_num = read_u4(&p);
                check_printf("H#%d@%d %s: id=0x%x, thread_serial_num=%u\n",
                        nrecords, npos, label, id, thread_serial_num);
                break;
            CASE_HEAP(HPROF_GC_ROOT_STICKY_CLASS)
                id = read_id(&p);
                check_printf("H#%d@%d %s: id=0x%x\n",
                        nrecords, npos, label, id);
                break;
            CASE_HEAP(HPROF_GC_ROOT_THREAD_BLOCK)
                id = read_id(&p);
                thread_serial_num = read_u4(&p);
                check_printf("H#%d@%d %s: id=0x%x, thread_serial_num=%u\n",
                        nrecords, npos, label, id, thread_serial_num);
                break;
            CASE_HEAP(HPROF_GC_ROOT_MONITOR_USED)
                id = read_id(&p);
                check_printf("H#%d@%d %s: id=0x%x\n",
                        nrecords, npos, label, id);
                break;
            CASE_HEAP(HPROF_GC_ROOT_THREAD_OBJ)
                id = read_id(&p);
                thread_serial_num = read_u4(&p);
                trace_serial_num = read_u4(&p);
                CHECK_TRACE_SERIAL_NO(trace_serial_num);
                check_printf("H#%d@%d %s: id=0x%x, thread_serial_num=%u,"
                             " trace_serial_num=%u\n",
                        nrecords, npos, label, id, thread_serial_num,
                        trace_serial_num);
                break;
            CASE_HEAP(HPROF_GC_CLASS_DUMP)
                id = read_id(&p);
                trace_serial_num = read_u4(&p);
                CHECK_TRACE_SERIAL_NO(trace_serial_num);
                check_printf("H#%d@%d %s: id=0x%x, trace_serial_num=%u\n",
                        nrecords, npos, label, id, trace_serial_num);
                {
                    HprofId ld, si, pr, re1, re2;

                    sup = read_id(&p);
                    ld  = read_id(&p);
                    si  = read_id(&p);
                    pr  = read_id(&p);
                    re1 = read_id(&p);
                    re2 = read_id(&p);
                    check_printf("  su=0x%x, ld=0x%x, si=0x%x,"
                                 " pr=0x%x, re1=0x%x, re2=0x%x\n",
                        sup, ld, si, pr, re1, re2);
                }
                inst_size = read_u4(&p);
                check_printf("  instance_size=%d\n", inst_size);

                num_elements = read_u2(&p);
                for(i=0; i<num_elements; i++) {
                    HprofType ty;
                    unsigned  cpi;
                    jvalue    val;

                    cpi = read_u2(&p);
                    ty  = read_u1(&p);
                    val = read_val(&p, ty);
                    check_printf("  constant_pool %d: cpi=%d, ty=%d, val=",
                                i, cpi, ty);
                    check_printf_val(ty, val, 1);
                    check_printf("\n");
                }

                num_elements = read_u2(&p);
                check_printf("  static_field_count=%d\n", num_elements);
                for(i=0; i<num_elements; i++) {
                    HprofType ty;
                    HprofId   id;
                    jvalue    val;

                    id  = read_id(&p);
                    ty  = read_u1(&p);
                    val = read_val(&p, ty);
                    check_printf("  static field %d: ", i);
                    check_print_utf8(utab, "id=", id);
                    check_printf(", ty=%d, val=", ty);
                    check_printf_val(ty, val, 1);
                    check_printf("\n");
                }

                num_elements = read_u2(&p);
                check_printf("  instance_field_count=%d\n", num_elements);
                for(i=0; i<num_elements; i++) {
                    HprofType ty;
                    HprofId   id;

                    id = read_id(&p);
                    ty = read_u1(&p);
                    check_printf("  instance_field %d: ", i);
                    check_print_utf8(utab, "id=", id);
                    check_printf(", ty=%d\n", ty);
                }
                break;
            CASE_HEAP(HPROF_GC_INSTANCE_DUMP)
                id = read_id(&p);
                trace_serial_num = read_u4(&p);
                CHECK_TRACE_SERIAL_NO(trace_serial_num);
                id2 = read_id(&p); /* class id */
                num_bytes = read_u4(&p);
                check_printf("H#%d@%d %s: id=0x%x, trace_serial_num=%u,"
                             " cid=0x%x, nbytes=%d\n",
                            nrecords, npos, label, id, trace_serial_num,
                            id2, num_bytes);
                /* This is a packed set of bytes for the instance fields */
                if ( num_bytes > 0 ) {
                    TableIndex cindex;
                    int        ifield;
                    CmapInfo  *map;

                    cindex = table_find_entry(ctab, &id2, sizeof(id2));
                    HPROF_ASSERT(cindex!=0);
                    map = (CmapInfo*)table_get_info(ctab, cindex);
                    HPROF_ASSERT(map!=NULL);
                    HPROF_ASSERT(num_bytes==map->inst_size);

                    psave  = p;
                    ifield = 0;

                    do {
                        for(i=0;i<map->n_finfo;i++) {
                            HprofType ty;
                            HprofId   id;
                            jvalue    val;

                            ty = map->finfo[i].ty;
                            id = map->finfo[i].id;
                            HPROF_ASSERT(ty!=0);
                            HPROF_ASSERT(id!=0);
                            val = read_val(&p, ty);
                            check_printf("  field %d: ", ifield);
                            check_print_utf8(utab, "id=", id);
                            check_printf(", ty=%d, val=", ty);
                            check_printf_val(ty, val, 1);
                            check_printf("\n");
                            ifield++;
                        }
                        id2    = map->sup;
                        map    = NULL;
                        cindex = 0;
                        if ( id2 != 0 ) {
                            cindex = table_find_entry(ctab, &id2, sizeof(id2));
                            HPROF_ASSERT(cindex!=0);
                            map = (CmapInfo*)table_get_info(ctab, cindex);
                            HPROF_ASSERT(map!=NULL);
                        }
                    } while ( map != NULL );
                    HPROF_ASSERT(num_bytes==(p-psave));
                }
                break;
            CASE_HEAP(HPROF_GC_OBJ_ARRAY_DUMP)
                id = read_id(&p);
                trace_serial_num = read_u4(&p);
                CHECK_TRACE_SERIAL_NO(trace_serial_num);
                num_elements = read_u4(&p);
                id2 = read_id(&p);
                check_printf("H#%d@%d %s: id=0x%x, trace_serial_num=%u, nelems=%d, eid=0x%x\n",
                                nrecords, npos, label, id, trace_serial_num, num_elements, id2);
                for(i=0; i<num_elements; i++) {
                    HprofId id;

                    id = read_id(&p);
                    check_printf("  [%d]: id=0x%x\n", i, id);
                }
                break;
            CASE_HEAP(HPROF_GC_PRIM_ARRAY_DUMP)
                id = read_id(&p);
                trace_serial_num = read_u4(&p);
                CHECK_TRACE_SERIAL_NO(trace_serial_num);
                num_elements = read_u4(&p);
                ty = read_u1(&p);
                psave = p;
                check_printf("H#%d@%d %s: id=0x%x, trace_serial_num=%u, "
                             "nelems=%d, ty=%d\n",
                                nrecords, npos, label, id, trace_serial_num, num_elements, ty);
                HPROF_ASSERT(HPROF_TYPE_IS_PRIMITIVE(ty));
                if ( num_elements > 0 ) {
                    int   count;
                    int   long_form;
                    int   max_count;
                    char *quote;

                    quote     = "";
                    long_form = 1;
                    max_count = 8;
                    count     = 0;
                    switch ( ty ) {
                        case HPROF_CHAR:
                            long_form = 0;
                            max_count = 72;
                            quote     = "\"";
                            /*FALLTHRU*/
                        case HPROF_INT:
                        case HPROF_DOUBLE:
                        case HPROF_LONG:
                        case HPROF_BYTE:
                        case HPROF_BOOLEAN:
                        case HPROF_SHORT:
                        case HPROF_FLOAT:
                            check_printf("  val=%s", quote);
                            for(i=0; i<num_elements; i++) {
                                jvalue val;

                                if ( i > 0 && count == 0 ) {
                                    check_printf("  %s", quote);
                                }
                                val = read_val(&p, ty);
                                check_printf_val(ty, val, long_form);
                                count += 1;
                                if ( count >= max_count ) {
                                    check_printf("\"\n");
                                    count = 0;
                                }
                            }
                            if ( count != 0 ) {
                                check_printf("%s\n", quote);
                            }
                            break;
                    }
                }
                HPROF_ASSERT(type_size[ty]*num_elements==(p-psave));
                break;
            default:
                label = "UNKNOWN";
                check_printf("H#%d@%d %s: ERROR!\n",
                                nrecords, npos, label);
                HPROF_ERROR(JNI_TRUE, "unknown heap record type");
                break;
        }
    }
    CHECK_FOR_ERROR(p==pstart+nbytes);

    table_cleanup(ctab, &cmap_cleanup, NULL);

    return nrecords;
}

/* LookupTable cleanup callback for utab */
static void
utab_cleanup(TableIndex i, void *key_ptr, int key_len, void*info, void*data)
{
    UmapInfo *umap = info;

    if ( umap == NULL ) {
        return;
    }
    if ( umap->str != NULL ) {
        HPROF_FREE(umap->str);
        umap->str = NULL;
    }
}

/* Check all the heap tags in a heap dump */
static int
check_tags(unsigned char *pstart, int nbytes)
{
    unsigned char      *p;
    int                 nrecord;
    struct LookupTable *utab;
    UmapInfo            umap;

    check_printf("\nCHECK TAGS: starting\n");

    utab    = table_initialize("temp utf8 map", 64, 64, 512, sizeof(UmapInfo));

    /* Walk the tags, assumes UTF8 tags are defined before used */
    p       = pstart;
    nrecord = 0;
    while ( p < (pstart+nbytes) ) {
        unsigned     tag;
        unsigned     size;
        int          nheap_records;
        int          npos;
        char        *label;
        HprofId      id, nm, sg, so, gr, gn;
        int          i, li, num_elements;
        HprofType    ty;
        SerialNumber trace_serial_num;
        SerialNumber thread_serial_num;
        SerialNumber class_serial_num;
        unsigned     flags;
        unsigned     depth;
        float        cutoff;
        unsigned     temp;
        jint         nblive;
        jint         nilive;
        jlong        tbytes;
        jlong        tinsts;
        jint         total_samples;
        jint         trace_count;

        nrecord++;
        /*LINTED*/
        npos = (int)(p - pstart);
        tag = read_u1(&p);
        (void)read_u4(&p); /* microsecs */
        size = read_u4(&p);
        #define CASE_TAG(name) case name: label = #name;
        switch ( tag ) {
            CASE_TAG(HPROF_UTF8)
                CHECK_FOR_ERROR(size>=(int)sizeof(HprofId));
                id = read_id(&p);
                check_printf("#%d@%d: %s, sz=%d, name_id=0x%x, \"",
                                nrecord, npos, label, size, id);
                num_elements = size-(int)sizeof(HprofId);
                check_raw(p, num_elements);
                check_printf("\"\n");
                /* Create entry in umap */
                umap.str = HPROF_MALLOC(num_elements+1);
                (void)strncpy(umap.str, (char*)p, (size_t)num_elements);
                umap.str[num_elements] = 0;
                (void)table_create_entry(utab, &id, sizeof(id), &umap);
                p += num_elements;
                break;
            CASE_TAG(HPROF_LOAD_CLASS)
                CHECK_FOR_ERROR(size==2*4+2*(int)sizeof(HprofId));
                class_serial_num = read_u4(&p);
                CHECK_CLASS_SERIAL_NO(class_serial_num);
                id = read_id(&p);
                trace_serial_num = read_u4(&p);
                CHECK_TRACE_SERIAL_NO(trace_serial_num);
                nm = read_id(&p);
                check_printf("#%d@%d: %s, sz=%d, class_serial_num=%u,"
                             " id=0x%x, trace_serial_num=%u, name_id=0x%x\n",
                                nrecord, npos, label, size, class_serial_num,
                                id, trace_serial_num, nm);
                break;
            CASE_TAG(HPROF_UNLOAD_CLASS)
                CHECK_FOR_ERROR(size==4);
                class_serial_num = read_u4(&p);
                CHECK_CLASS_SERIAL_NO(class_serial_num);
                check_printf("#%d@%d: %s, sz=%d, class_serial_num=%u\n",
                                nrecord, npos, label, size, class_serial_num);
                break;
            CASE_TAG(HPROF_FRAME)
                CHECK_FOR_ERROR(size==2*4+4*(int)sizeof(HprofId));
                id = read_id(&p);
                nm = read_id(&p);
                sg = read_id(&p);
                so = read_id(&p);
                class_serial_num = read_u4(&p);
                CHECK_CLASS_SERIAL_NO(class_serial_num);
                li = read_u4(&p);
                check_printf("#%d@%d: %s, sz=%d, ", nrecord, npos, label, size);
                check_print_utf8(utab, "id=", id);
                check_printf(" name_id=0x%x, sig_id=0x%x, source_id=0x%x,"
                             " class_serial_num=%u, lineno=%d\n",
                                nm, sg, so, class_serial_num, li);
                break;
            CASE_TAG(HPROF_TRACE)
                CHECK_FOR_ERROR(size>=3*4);
                trace_serial_num = read_u4(&p);
                CHECK_TRACE_SERIAL_NO(trace_serial_num);
                thread_serial_num = read_u4(&p); /* Can be 0 */
                num_elements = read_u4(&p);
                check_printf("#%d@%d: %s, sz=%d, trace_serial_num=%u,"
                             " thread_serial_num=%u, nelems=%d [",
                                nrecord, npos, label, size,
                                trace_serial_num, thread_serial_num, num_elements);
                for(i=0; i< num_elements; i++) {
                    check_printf("0x%x,", read_id(&p));
                }
                check_printf("]\n");
                break;
            CASE_TAG(HPROF_ALLOC_SITES)
                CHECK_FOR_ERROR(size>=2+4*4+2*8);
                flags = read_u2(&p);
                temp  = read_u4(&p);
                cutoff = *((float*)&temp);
                nblive = read_u4(&p);
                nilive = read_u4(&p);
                tbytes = read_u8(&p);
                tinsts = read_u8(&p);
                num_elements     = read_u4(&p);
                check_printf("#%d@%d: %s, sz=%d, flags=0x%x, cutoff=%g,"
                             " nblive=%d, nilive=%d, tbytes=(%d,%d),"
                             " tinsts=(%d,%d), num_elements=%d\n",
                                nrecord, npos, label, size,
                                flags, cutoff, nblive, nilive,
                                jlong_high(tbytes), jlong_low(tbytes),
                                jlong_high(tinsts), jlong_low(tinsts),
                                num_elements);
                for(i=0; i< num_elements; i++) {
                    ty = read_u1(&p);
                    class_serial_num = read_u4(&p);
                    CHECK_CLASS_SERIAL_NO(class_serial_num);
                    trace_serial_num = read_u4(&p);
                    CHECK_TRACE_SERIAL_NO(trace_serial_num);
                    nblive = read_u4(&p);
                    nilive = read_u4(&p);
                    tbytes = read_u4(&p);
                    tinsts = read_u4(&p);
                    check_printf("\t %d: ty=%d, class_serial_num=%u,"
                                 " trace_serial_num=%u, nblive=%d, nilive=%d,"
                                 " tbytes=%d, tinsts=%d\n",
                                 i, ty, class_serial_num, trace_serial_num,
                                 nblive, nilive, (jint)tbytes, (jint)tinsts);
                }
                break;
            CASE_TAG(HPROF_HEAP_SUMMARY)
                CHECK_FOR_ERROR(size==2*4+2*8);
                nblive = read_u4(&p);
                nilive = read_u4(&p);
                tbytes = read_u8(&p);
                tinsts = read_u8(&p);
                check_printf("#%d@%d: %s, sz=%d,"
                             " nblive=%d, nilive=%d, tbytes=(%d,%d),"
                             " tinsts=(%d,%d)\n",
                                nrecord, npos, label, size,
                                nblive, nilive,
                                jlong_high(tbytes), jlong_low(tbytes),
                                jlong_high(tinsts), jlong_low(tinsts));
                break;
            CASE_TAG(HPROF_START_THREAD)
                CHECK_FOR_ERROR(size==2*4+4*(int)sizeof(HprofId));
                thread_serial_num = read_u4(&p);
                CHECK_THREAD_SERIAL_NO(thread_serial_num);
                id = read_id(&p);
                trace_serial_num = read_u4(&p);
                CHECK_TRACE_SERIAL_NO(trace_serial_num);
                nm = read_id(&p);
                gr = read_id(&p);
                gn = read_id(&p);
                check_printf("#%d@%d: %s, sz=%d, thread_serial_num=%u,"
                             " id=0x%x, trace_serial_num=%u, ",
                                nrecord, npos, label, size,
                                thread_serial_num, id, trace_serial_num);
                check_print_utf8(utab, "nm=", id);
                check_printf(" trace_serial_num=%u, nm=0x%x,"
                             " gr=0x%x, gn=0x%x\n",
                                trace_serial_num, nm, gr, gn);
                break;
            CASE_TAG(HPROF_END_THREAD)
                CHECK_FOR_ERROR(size==4);
                thread_serial_num = read_u4(&p);
                CHECK_THREAD_SERIAL_NO(thread_serial_num);
                check_printf("#%d@%d: %s, sz=%d, thread_serial_num=%u\n",
                                nrecord, npos, label, size, thread_serial_num);
                break;
            CASE_TAG(HPROF_HEAP_DUMP)
                check_printf("#%d@%d: BEGIN: %s, sz=%d\n",
                                nrecord, npos, label, size);
                nheap_records = check_heap_tags(utab, p, size);
                check_printf("#%d@%d: END: %s, sz=%d, nheap_recs=%d\n",
                                nrecord, npos, label, size, nheap_records);
                p += size;
                break;
            CASE_TAG(HPROF_HEAP_DUMP_SEGMENT) /* 1.0.2 */
                check_printf("#%d@%d: BEGIN SEGMENT: %s, sz=%d\n",
                                nrecord, npos, label, size);
                nheap_records = check_heap_tags(utab, p, size);
                check_printf("#%d@%d: END SEGMENT: %s, sz=%d, nheap_recs=%d\n",
                                nrecord, npos, label, size, nheap_records);
                p += size;
                break;
            CASE_TAG(HPROF_HEAP_DUMP_END) /* 1.0.2 */
                check_printf("#%d@%d: SEGMENT END: %s, sz=%d\n",
                                nrecord, npos, label, size);
                break;
            CASE_TAG(HPROF_CPU_SAMPLES)
                CHECK_FOR_ERROR(size>=2*4);
                total_samples = read_u4(&p);
                trace_count = read_u4(&p);
                check_printf("#%d@%d: %s, sz=%d, total_samples=%d,"
                             " trace_count=%d\n",
                                nrecord, npos, label, size,
                                total_samples, trace_count);
                for(i=0; i< trace_count; i++) {
                    num_elements = read_u4(&p);
                    trace_serial_num = read_u4(&p);
                    CHECK_TRACE_SERIAL_NO(trace_serial_num);
                    check_printf("\t %d: samples=%d, trace_serial_num=%u\n",
                                 trace_serial_num, num_elements);
                }
                break;
            CASE_TAG(HPROF_CONTROL_SETTINGS)
                CHECK_FOR_ERROR(size==4+2);
                flags = read_u4(&p);
                depth = read_u2(&p);
                check_printf("#%d@%d: %s, sz=%d, flags=0x%x, depth=%d\n",
                                nrecord, npos, label, size, flags, depth);
                break;
            default:
                label = "UNKNOWN";
                check_printf("#%d@%d: %s, sz=%d\n",
                                nrecord, npos, label, size);
                HPROF_ERROR(JNI_TRUE, "unknown record type");
                p += size;
                break;
        }
        CHECK_FOR_ERROR(p<=(pstart+nbytes));
    }
    check_flush();
    CHECK_FOR_ERROR(p==(pstart+nbytes));
    table_cleanup(utab, &utab_cleanup, NULL);
    return nrecord;
}

/* Read the entire file into memory */
static void *
get_binary_file_image(char *filename, int *pnbytes)
{
    unsigned char *image;
    int            fd;
    jlong          nbytes;
    int            nread;

    *pnbytes = 0;
    fd = md_open_binary(filename);
    CHECK_FOR_ERROR(fd>=0);
    if ( (nbytes = md_seek(fd, (jlong)-1)) == (jlong)-1 ) {
        HPROF_ERROR(JNI_TRUE, "Cannot md_seek() to end of file");
    }
    CHECK_FOR_ERROR(((jint)nbytes)>512);
    if ( md_seek(fd, (jlong)0) != (jlong)0 ) {
        HPROF_ERROR(JNI_TRUE, "Cannot md_seek() to start of file");
    }
    image = HPROF_MALLOC(((jint)nbytes)+1);
    CHECK_FOR_ERROR(image!=NULL);

    /* Read the entire file image into memory */
    nread = md_read(fd, image, (jint)nbytes);
    if ( nread <= 0 ) {
        HPROF_ERROR(JNI_TRUE, "System read failed.");
    }
    CHECK_FOR_ERROR(((jint)nbytes)==nread);
    md_close(fd);
    *pnbytes = (jint)nbytes;
    return image;
}

/* ------------------------------------------------------------------ */

void
check_binary_file(char *filename)
{
    unsigned char *image;
    unsigned char *p;
    unsigned       idsize;
    int            nbytes;
    int            nrecords;

    image = get_binary_file_image(filename, &nbytes);
    if ( image == NULL ) {
        check_printf("No file image: %s\n", filename);
        return;
    }
    p = image;
    CHECK_FOR_ERROR(strcmp((char*)p, gdata->header)==0);
    check_printf("Filename=%s, nbytes=%d, header=\"%s\"\n",
                        filename, nbytes, p);
    p+=((int)strlen((char*)p)+1);
    idsize = read_u4(&p);
    CHECK_FOR_ERROR(idsize==sizeof(HprofId));
    (void)read_u4(&p);
    (void)read_u4(&p);
    /* LINTED */
    nrecords = check_tags(p, nbytes - (int)( p - image ) );
    check_printf("#%d total records found in %d bytes\n", nrecords, nbytes);
    HPROF_FREE(image);
}
