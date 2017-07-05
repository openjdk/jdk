/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

/* All I/O functionality for hprof. */

/*
 * The hprof agent has many forms of output:
 *
 *   format=b   gdata->output_format=='b'
 *      Binary format. Defined below. This is used by HAT.
 *      This is NOT the same format as emitted by JVMPI.
 *
 *   format=a   gdata->output_format=='a'
 *      Ascii format. Not exactly an ascii representation of the binary format.
 *
 * And many forms of dumps:
 *
 *    heap=dump
 *        A large dump that in this implementation is written to a separate
 *        file first before being placed in the output file. Several reasons,
 *        the binary form needs a byte count of the length in the header, and
 *        references in this dump to other items need to be emitted first.
 *        So it's two pass, or use a temp file and copy.
 *    heap=sites
 *        Dumps the sites in the order of most allocations.
 *    cpu=samples
 *        Dumps the traces in order of most hits
 *    cpu=times
 *        Dumps the traces in the order of most time spent there.
 *    cpu=old   (format=a only)
 *        Dumps out an older form of cpu output (old -prof format)
 *    monitor=y (format=a only)
 *        Dumps out a list of monitors in order of most contended.
 *
 * This file also includes a binary format check function that will read
 *   back in the hprof binary format and verify the syntax looks correct.
 *
 * WARNING: Besides the comments below, there is little format spec on this,
 *          however see:
 *           http://java.sun.com/j2se/1.4.2/docs/guide/jvmpi/jvmpi.html#hprof
 */

#include "hprof.h"

typedef TableIndex HprofId;

#include "hprof_ioname.h"
#include "hprof_b_spec.h"

static int type_size[ /*HprofType*/ ] =  HPROF_TYPE_SIZES;

static void dump_heap_segment_and_reset(jlong segment_size);

static void
not_implemented(void)
{
}

static IoNameIndex
get_name_index(char *name)
{
    if (name != NULL && gdata->output_format == 'b') {
        return ioname_find_or_create(name, NULL);
    }
    return 0;
}

static char *
signature_to_name(char *sig)
{
    char *ptr;
    char *basename;
    char *name;
    int i;
    int len;
    int name_len;

    if ( sig != NULL ) {
        switch ( sig[0] ) {
            case JVM_SIGNATURE_CLASS:
                ptr = strchr(sig+1, JVM_SIGNATURE_ENDCLASS);
                if ( ptr == NULL ) {
                    basename = "Unknown_class";
                    break;
                }
                /*LINTED*/
                name_len = (jint)(ptr - (sig+1));
                name = HPROF_MALLOC(name_len+1);
                (void)memcpy(name, sig+1, name_len);
                name[name_len] = 0;
                for ( i = 0 ; i < name_len ; i++ ) {
                    if ( name[i] == '/' ) name[i] = '.';
                }
                return name;
            case JVM_SIGNATURE_ARRAY:
                basename = signature_to_name(sig+1);
                len = (int)strlen(basename);
                name_len = len+2;
                name = HPROF_MALLOC(name_len+1);
                (void)memcpy(name, basename, len);
                (void)memcpy(name+len, "[]", 2);
                name[name_len] = 0;
                HPROF_FREE(basename);
                return name;
            case JVM_SIGNATURE_FUNC:
                ptr = strchr(sig+1, JVM_SIGNATURE_ENDFUNC);
                if ( ptr == NULL ) {
                    basename = "Unknown_method";
                    break;
                }
                basename = "()"; /* Someday deal with method signatures */
                break;
            case JVM_SIGNATURE_BYTE:
                basename = "byte";
                break;
            case JVM_SIGNATURE_CHAR:
                basename = "char";
                break;
            case JVM_SIGNATURE_ENUM:
                basename = "enum";
                break;
            case JVM_SIGNATURE_FLOAT:
                basename = "float";
                break;
            case JVM_SIGNATURE_DOUBLE:
                basename = "double";
                break;
            case JVM_SIGNATURE_INT:
                basename = "int";
                break;
            case JVM_SIGNATURE_LONG:
                basename = "long";
                break;
            case JVM_SIGNATURE_SHORT:
                basename = "short";
                break;
            case JVM_SIGNATURE_VOID:
                basename = "void";
                break;
            case JVM_SIGNATURE_BOOLEAN:
                basename = "boolean";
                break;
            default:
                basename = "Unknown_class";
                break;
        }
    } else {
        basename = "Unknown_class";
    }

    /* Simple basename */
    name_len = (int)strlen(basename);
    name = HPROF_MALLOC(name_len+1);
    (void)strcpy(name, basename);
    return name;
}

static int
size_from_field_info(int size)
{
    if ( size == 0 ) {
        size = (int)sizeof(HprofId);
    }
    return size;
}

static void
type_from_signature(const char *sig, HprofType *kind, jint *size)
{
    *kind = HPROF_NORMAL_OBJECT;
    *size = 0;
    switch ( sig[0] ) {
        case JVM_SIGNATURE_ENUM:
        case JVM_SIGNATURE_CLASS:
        case JVM_SIGNATURE_ARRAY:
            *kind = HPROF_NORMAL_OBJECT;
            break;
        case JVM_SIGNATURE_BOOLEAN:
            *kind = HPROF_BOOLEAN;
            break;
        case JVM_SIGNATURE_CHAR:
            *kind = HPROF_CHAR;
            break;
        case JVM_SIGNATURE_FLOAT:
            *kind = HPROF_FLOAT;
            break;
        case JVM_SIGNATURE_DOUBLE:
            *kind = HPROF_DOUBLE;
            break;
        case JVM_SIGNATURE_BYTE:
            *kind = HPROF_BYTE;
            break;
        case JVM_SIGNATURE_SHORT:
            *kind = HPROF_SHORT;
            break;
        case JVM_SIGNATURE_INT:
            *kind = HPROF_INT;
            break;
        case JVM_SIGNATURE_LONG:
            *kind = HPROF_LONG;
            break;
        default:
            HPROF_ASSERT(0);
            break;
    }
    *size = type_size[*kind];
}

static void
type_array(const char *sig, HprofType *kind, jint *elem_size)
{
    *kind = 0;
    *elem_size = 0;
    switch ( sig[0] ) {
        case JVM_SIGNATURE_ARRAY:
            type_from_signature(sig+1, kind, elem_size);
            break;
    }
}

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

static void
system_write(int fd, void *buf, int len, jboolean socket)
{
    int res;

    HPROF_ASSERT(fd>=0);
    if (socket) {
        res = md_send(fd, buf, len, 0);
        if (res < 0 || res!=len) {
            system_error("send", res, errno);
        }
    } else {
        res = md_write(fd, buf, len);
        if (res < 0 || res!=len) {
            system_error("write", res, errno);
        }
    }
}

static void
write_flush(void)
{
    HPROF_ASSERT(gdata->fd >= 0);
    if (gdata->write_buffer_index) {
        system_write(gdata->fd, gdata->write_buffer, gdata->write_buffer_index,
                                gdata->socket);
        gdata->write_buffer_index = 0;
    }
}

static void
heap_flush(void)
{
    HPROF_ASSERT(gdata->heap_fd >= 0);
    if (gdata->heap_buffer_index) {
        gdata->heap_write_count += (jlong)gdata->heap_buffer_index;
        system_write(gdata->heap_fd, gdata->heap_buffer, gdata->heap_buffer_index,
                                JNI_FALSE);
        gdata->heap_buffer_index = 0;
    }
}

static void
write_raw(void *buf, int len)
{
    HPROF_ASSERT(gdata->fd >= 0);
    if (gdata->write_buffer_index + len > gdata->write_buffer_size) {
        write_flush();
        if (len > gdata->write_buffer_size) {
            system_write(gdata->fd, buf, len, gdata->socket);
            return;
        }
    }
    (void)memcpy(gdata->write_buffer + gdata->write_buffer_index, buf, len);
    gdata->write_buffer_index += len;
}

static void
write_u4(unsigned i)
{
    i = md_htonl(i);
    write_raw(&i, (jint)sizeof(unsigned));
}

static void
write_u8(jlong t)
{
    write_u4((jint)jlong_high(t));
    write_u4((jint)jlong_low(t));
}

static void
write_u2(unsigned short i)
{
    i = md_htons(i);
    write_raw(&i, (jint)sizeof(unsigned short));
}

static void
write_u1(unsigned char i)
{
    write_raw(&i, (jint)sizeof(unsigned char));
}

static void
write_id(HprofId i)
{
    write_u4(i);
}

static void
write_current_ticks(void)
{
    write_u4((jint)(md_get_microsecs() - gdata->micro_sec_ticks));
}

static void
write_header(unsigned char type, jint length)
{
    write_u1(type);
    write_current_ticks();
    write_u4(length);
}

static void
write_index_id(HprofId index)
{
    write_id(index);
}

static IoNameIndex
write_name_first(char *name)
{
    if ( name == NULL ) {
        return 0;
    }
    if (gdata->output_format == 'b') {
        IoNameIndex name_index;
        jboolean    new_one;

        new_one = JNI_FALSE;
        name_index = ioname_find_or_create(name, &new_one);
        if ( new_one ) {
            int      len;

            len = (int)strlen(name);
            write_header(HPROF_UTF8, len + (jint)sizeof(HprofId));
            write_index_id(name_index);
            write_raw(name, len);

        }
        return name_index;
    }
    return 0;
}

static void
write_printf(char *fmt, ...)
{
    char buf[1024];
    va_list args;
    va_start(args, fmt);
    (void)md_vsnprintf(buf, sizeof(buf), fmt, args);
    buf[sizeof(buf)-1] = 0;
    write_raw(buf, (int)strlen(buf));
    va_end(args);
}

static void
write_thread_serial_number(SerialNumber thread_serial_num, int with_comma)
{
    if ( thread_serial_num != 0 ) {
        CHECK_THREAD_SERIAL_NO(thread_serial_num);
        if ( with_comma ) {
            write_printf(" thread %d,", thread_serial_num);
        } else {
            write_printf(" thread %d", thread_serial_num);
        }
    } else {
        if ( with_comma ) {
            write_printf(" <unknown thread>,");
        } else {
            write_printf(" <unknown thread>");
        }
    }
}

static void
heap_raw(void *buf, int len)
{
    HPROF_ASSERT(gdata->heap_fd >= 0);
    if (gdata->heap_buffer_index + len > gdata->heap_buffer_size) {
        heap_flush();
        if (len > gdata->heap_buffer_size) {
            gdata->heap_write_count += (jlong)len;
            system_write(gdata->heap_fd, buf, len, JNI_FALSE);
            return;
        }
    }
    (void)memcpy(gdata->heap_buffer + gdata->heap_buffer_index, buf, len);
    gdata->heap_buffer_index += len;
}

static void
heap_u4(unsigned i)
{
    i = md_htonl(i);
    heap_raw(&i, (jint)sizeof(unsigned));
}

static void
heap_u8(jlong i)
{
    heap_u4((jint)jlong_high(i));
    heap_u4((jint)jlong_low(i));
}

static void
heap_u2(unsigned short i)
{
    i = md_htons(i);
    heap_raw(&i, (jint)sizeof(unsigned short));
}

static void
heap_u1(unsigned char i)
{
    heap_raw(&i, (jint)sizeof(unsigned char));
}

/* Write out the first byte of a heap tag */
static void
heap_tag(unsigned char tag)
{
    jlong pos;

    /* Current position in virtual heap dump file */
    pos = gdata->heap_write_count + (jlong)gdata->heap_buffer_index;
    if ( gdata->segmented == JNI_TRUE ) { /* 1.0.2 */
        if ( pos >= gdata->maxHeapSegment ) {
            /* Flush all bytes to the heap dump file */
            heap_flush();

            /* Send out segment (up to last tag written out) */
            dump_heap_segment_and_reset(gdata->heap_last_tag_position);

            /* Get new current position */
            pos = gdata->heap_write_count + (jlong)gdata->heap_buffer_index;
        }
    }
    /* Save position of this tag */
    gdata->heap_last_tag_position = pos;
    /* Write out this tag */
    heap_u1(tag);
}

static void
heap_id(HprofId i)
{
    heap_u4(i);
}

static void
heap_index_id(HprofId index)
{
    heap_id(index);
}

static void
heap_name(char *name)
{
    heap_index_id(get_name_index(name));
}

static void
heap_printf(char *fmt, ...)
{
    char buf[1024];
    va_list args;
    va_start(args, fmt);
    (void)md_vsnprintf(buf, sizeof(buf), fmt, args);
    buf[sizeof(buf)-1] = 0;
    heap_raw(buf, (int)strlen(buf));
    va_end(args);
}

static void
heap_element(HprofType kind, jint size, jvalue value)
{
    if ( !HPROF_TYPE_IS_PRIMITIVE(kind) ) {
        HPROF_ASSERT(size==4);
        heap_id((HprofId)value.i);
    } else {
        switch ( size ) {
            case 8:
                HPROF_ASSERT(size==8);
                HPROF_ASSERT(kind==HPROF_LONG || kind==HPROF_DOUBLE);
                heap_u8(value.j);
                break;
            case 4:
                HPROF_ASSERT(size==4);
                HPROF_ASSERT(kind==HPROF_INT || kind==HPROF_FLOAT);
                heap_u4(value.i);
                break;
            case 2:
                HPROF_ASSERT(size==2);
                HPROF_ASSERT(kind==HPROF_SHORT || kind==HPROF_CHAR);
                heap_u2(value.s);
                break;
            case 1:
                HPROF_ASSERT(size==1);
                HPROF_ASSERT(kind==HPROF_BOOLEAN || kind==HPROF_BYTE);
                HPROF_ASSERT(kind==HPROF_BOOLEAN?(value.b==0 || value.b==1):1);
                heap_u1(value.b);
                break;
            default:
                HPROF_ASSERT(0);
                break;
        }
    }
}

/* Dump out all elements of an array, objects in jvalues, prims packed */
static void
heap_elements(HprofType kind, jint num_elements, jint elem_size, void *elements)
{
    int     i;
    jvalue  val;
    static jvalue empty_val;

    if ( num_elements == 0 ) {
        return;
    }

    switch ( kind ) {
        case 0:
        case HPROF_ARRAY_OBJECT:
        case HPROF_NORMAL_OBJECT:
            for (i = 0; i < num_elements; i++) {
                val   = empty_val;
                val.i = ((ObjectIndex*)elements)[i];
                heap_element(kind, elem_size, val);
            }
            break;
        case HPROF_BYTE:
        case HPROF_BOOLEAN:
            HPROF_ASSERT(elem_size==1);
            for (i = 0; i < num_elements; i++) {
                val   = empty_val;
                val.b = ((jboolean*)elements)[i];
                heap_element(kind, elem_size, val);
            }
            break;
        case HPROF_CHAR:
        case HPROF_SHORT:
            HPROF_ASSERT(elem_size==2);
            for (i = 0; i < num_elements; i++) {
                val   = empty_val;
                val.s = ((jshort*)elements)[i];
                heap_element(kind, elem_size, val);
            }
            break;
        case HPROF_FLOAT:
        case HPROF_INT:
            HPROF_ASSERT(elem_size==4);
            for (i = 0; i < num_elements; i++) {
                val   = empty_val;
                val.i = ((jint*)elements)[i];
                heap_element(kind, elem_size, val);
            }
            break;
        case HPROF_DOUBLE:
        case HPROF_LONG:
            HPROF_ASSERT(elem_size==8);
            for (i = 0; i < num_elements; i++) {
                val   = empty_val;
                val.j = ((jlong*)elements)[i];
                heap_element(kind, elem_size, val);
            }
            break;
    }
}

/* ------------------------------------------------------------------ */

void
io_flush(void)
{
    HPROF_ASSERT(gdata->header!=NULL);
    write_flush();
}

void
io_setup(void)
{
    gdata->write_buffer_size = FILE_IO_BUFFER_SIZE;
    gdata->write_buffer = HPROF_MALLOC(gdata->write_buffer_size);
    gdata->write_buffer_index = 0;

    gdata->heap_write_count = (jlong)0;
    gdata->heap_last_tag_position = (jlong)0;
    gdata->heap_buffer_size = FILE_IO_BUFFER_SIZE;
    gdata->heap_buffer = HPROF_MALLOC(gdata->heap_buffer_size);
    gdata->heap_buffer_index = 0;

    if ( gdata->logflags & LOG_CHECK_BINARY ) {
        gdata->check_buffer_size = FILE_IO_BUFFER_SIZE;
        gdata->check_buffer = HPROF_MALLOC(gdata->check_buffer_size);
        gdata->check_buffer_index = 0;
    }

    ioname_init();
}

void
io_cleanup(void)
{
    if ( gdata->write_buffer != NULL ) {
        HPROF_FREE(gdata->write_buffer);
    }
    gdata->write_buffer_size = 0;
    gdata->write_buffer = NULL;
    gdata->write_buffer_index = 0;

    if ( gdata->heap_buffer != NULL ) {
        HPROF_FREE(gdata->heap_buffer);
    }
    gdata->heap_write_count = (jlong)0;
    gdata->heap_last_tag_position = (jlong)0;
    gdata->heap_buffer_size = 0;
    gdata->heap_buffer = NULL;
    gdata->heap_buffer_index = 0;

    if ( gdata->logflags & LOG_CHECK_BINARY ) {
        if ( gdata->check_buffer != NULL ) {
            HPROF_FREE(gdata->check_buffer);
        }
        gdata->check_buffer_size = 0;
        gdata->check_buffer = NULL;
        gdata->check_buffer_index = 0;
    }

    ioname_cleanup();
}

void
io_write_file_header(void)
{
    HPROF_ASSERT(gdata->header!=NULL);
    if (gdata->output_format == 'b') {
        jint settings;
        jlong t;

        settings = 0;
        if (gdata->heap_dump || gdata->alloc_sites) {
            settings |= 1;
        }
        if (gdata->cpu_sampling) {
            settings |= 2;
        }
        t = md_get_timemillis();

        write_raw(gdata->header, (int)strlen(gdata->header) + 1);
        write_u4((jint)sizeof(HprofId));
        write_u8(t);

        write_header(HPROF_CONTROL_SETTINGS, 4 + 2);
        write_u4(settings);
        write_u2((unsigned short)gdata->max_trace_depth);

    } else if ((!gdata->cpu_timing) || (!gdata->old_timing_format)) {
        /* We don't want the prelude file for the old prof output format */
        time_t t;
        char prelude_file[FILENAME_MAX];
        int prelude_fd;
        int nbytes;

        t = time(0);

        md_get_prelude_path(prelude_file, sizeof(prelude_file), PRELUDE_FILE);

        prelude_fd = md_open(prelude_file);
        if (prelude_fd < 0) {
            char buf[FILENAME_MAX+80];

            (void)md_snprintf(buf, sizeof(buf), "Can't open %s", prelude_file);
            buf[sizeof(buf)-1] = 0;
            HPROF_ERROR(JNI_TRUE, buf);
        }

        write_printf("%s, created %s\n", gdata->header, ctime(&t));

        do {
            char buf[1024]; /* File is small, small buffer ok here */

            nbytes = md_read(prelude_fd, buf, sizeof(buf));
            if ( nbytes < 0 ) {
                system_error("read", nbytes, errno);
                break;
            }
            if (nbytes == 0) {
                break;
            }
            write_raw(buf, nbytes);
        } while ( nbytes > 0 );

        md_close(prelude_fd);

        write_printf("\n--------\n\n");

        write_flush();
    }
}

void
io_write_file_footer(void)
{
    HPROF_ASSERT(gdata->header!=NULL);
}

void
io_write_class_load(SerialNumber class_serial_num, ObjectIndex index,
                    SerialNumber trace_serial_num, char *sig)
{
    CHECK_CLASS_SERIAL_NO(class_serial_num);
    CHECK_TRACE_SERIAL_NO(trace_serial_num);
    if (gdata->output_format == 'b') {
        IoNameIndex name_index;
        char *class_name;

        class_name = signature_to_name(sig);
        name_index = write_name_first(class_name);
        write_header(HPROF_LOAD_CLASS, (2 * (jint)sizeof(HprofId)) + (4 * 2));
        write_u4(class_serial_num);
        write_index_id(index);
        write_u4(trace_serial_num);
        write_index_id(name_index);
        HPROF_FREE(class_name);
    }
}

void
io_write_class_unload(SerialNumber class_serial_num, ObjectIndex index)
{
    CHECK_CLASS_SERIAL_NO(class_serial_num);
    if (gdata->output_format == 'b') {
        write_header(HPROF_UNLOAD_CLASS, 4);
        write_u4(class_serial_num);
    }
}

void
io_write_sites_header(const char * comment_str, jint flags, double cutoff,
                    jint total_live_bytes, jint total_live_instances,
                    jlong total_alloced_bytes, jlong total_alloced_instances,
                    jint count)
{
    if ( gdata->output_format == 'b') {
        write_header(HPROF_ALLOC_SITES, 2 + (8 * 4) + (count * (4 * 6 + 1)));
        write_u2((unsigned short)flags);
        write_u4(*(int *)(&cutoff));
        write_u4(total_live_bytes);
        write_u4(total_live_instances);
        write_u8(total_alloced_bytes);
        write_u8(total_alloced_instances);
        write_u4(count);
    } else {
        time_t t;

        t = time(0);
        write_printf("SITES BEGIN (ordered by %s) %s", comment_str, ctime(&t));
        write_printf(
            "          percent          live          alloc'ed  stack class\n");
        write_printf(
            " rank   self  accum     bytes objs     bytes  objs trace name\n");
    }
}

void
io_write_sites_elem(jint index, double ratio, double accum_percent,
                char *sig, SerialNumber class_serial_num,
                SerialNumber trace_serial_num, jint n_live_bytes,
                jint n_live_instances, jint n_alloced_bytes,
                jint n_alloced_instances)
{
    CHECK_CLASS_SERIAL_NO(class_serial_num);
    CHECK_TRACE_SERIAL_NO(trace_serial_num);
    if ( gdata->output_format == 'b') {
        HprofType kind;
        jint size;

        type_array(sig, &kind, &size);
        write_u1(kind);
        write_u4(class_serial_num);
        write_u4(trace_serial_num);
        write_u4(n_live_bytes);
        write_u4(n_live_instances);
        write_u4(n_alloced_bytes);
        write_u4(n_alloced_instances);
    } else {
        char *class_name;

        class_name = signature_to_name(sig);
        write_printf("%5u %5.2f%% %5.2f%% %9u %4u %9u %5u %5u %s\n",
                     index,
                     ratio * 100.0,
                     accum_percent * 100.0,
                     n_live_bytes,
                     n_live_instances,
                     n_alloced_bytes,
                     n_alloced_instances,
                     trace_serial_num,
                     class_name);
        HPROF_FREE(class_name);
    }
}

void
io_write_sites_footer(void)
{
    if (gdata->output_format == 'b') {
        not_implemented();
    } else {
        write_printf("SITES END\n");
    }
}

void
io_write_thread_start(SerialNumber thread_serial_num,
                        ObjectIndex thread_obj_id,
                        SerialNumber trace_serial_num, char *thread_name,
                        char *thread_group_name, char *thread_parent_name)
{
    CHECK_THREAD_SERIAL_NO(thread_serial_num);
    CHECK_TRACE_SERIAL_NO(trace_serial_num);
    if (gdata->output_format == 'b') {
        IoNameIndex tname_index;
        IoNameIndex gname_index;
        IoNameIndex pname_index;

        tname_index = write_name_first(thread_name);
        gname_index = write_name_first(thread_group_name);
        pname_index = write_name_first(thread_parent_name);
        write_header(HPROF_START_THREAD, ((jint)sizeof(HprofId) * 4) + (4 * 2));
        write_u4(thread_serial_num);
        write_index_id(thread_obj_id);
        write_u4(trace_serial_num);
        write_index_id(tname_index);
        write_index_id(gname_index);
        write_index_id(pname_index);

    } else if ( (!gdata->cpu_timing) || (!gdata->old_timing_format)) {
        /* We don't want thread info for the old prof output format */
        write_printf("THREAD START "
                     "(obj=%x, id = %d, name=\"%s\", group=\"%s\")\n",
                     thread_obj_id, thread_serial_num,
                     (thread_name==NULL?"":thread_name),
                     (thread_group_name==NULL?"":thread_group_name));
    }
}

void
io_write_thread_end(SerialNumber thread_serial_num)
{
    CHECK_THREAD_SERIAL_NO(thread_serial_num);
    if (gdata->output_format == 'b') {
        write_header(HPROF_END_THREAD, 4);
        write_u4(thread_serial_num);

    } else if ( (!gdata->cpu_timing) || (!gdata->old_timing_format)) {
        /* we don't want thread info for the old prof output format */
        write_printf("THREAD END (id = %d)\n", thread_serial_num);
    }
}

void
io_write_frame(FrameIndex index, SerialNumber frame_serial_num,
               char *mname, char *msig, char *sname,
               SerialNumber class_serial_num, jint lineno)
{
    CHECK_CLASS_SERIAL_NO(class_serial_num);
    if (gdata->output_format == 'b') {
        IoNameIndex mname_index;
        IoNameIndex msig_index;
        IoNameIndex sname_index;

        mname_index = write_name_first(mname);
        msig_index  = write_name_first(msig);
        sname_index = write_name_first(sname);

        write_header(HPROF_FRAME, ((jint)sizeof(HprofId) * 4) + (4 * 2));
        write_index_id(index);
        write_index_id(mname_index);
        write_index_id(msig_index);
        write_index_id(sname_index);
        write_u4(class_serial_num);
        write_u4(lineno);
    }
}

void
io_write_trace_header(SerialNumber trace_serial_num,
                SerialNumber thread_serial_num, jint n_frames, char *phase_str)
{
    CHECK_TRACE_SERIAL_NO(trace_serial_num);
    if (gdata->output_format == 'b') {
        write_header(HPROF_TRACE, ((jint)sizeof(HprofId) * n_frames) + (4 * 3));
        write_u4(trace_serial_num);
        write_u4(thread_serial_num);
        write_u4(n_frames);
    } else {
        write_printf("TRACE %u:", trace_serial_num);
        if (thread_serial_num) {
            write_printf(" (thread=%d)", thread_serial_num);
        }
        if ( phase_str != NULL ) {
            write_printf(" (from %s phase of JVM)", phase_str);
        }
        write_printf("\n");
        if (n_frames == 0) {
            write_printf("\t<empty>\n");
        }
    }
}

void
io_write_trace_elem(SerialNumber trace_serial_num, FrameIndex frame_index,
                    SerialNumber frame_serial_num,
                    char *csig, char *mname, char *sname, jint lineno)
{
    if (gdata->output_format == 'b') {
        write_index_id(frame_index);
    } else {
        char *class_name;
        char linebuf[32];

        if (lineno == -2) {
            (void)md_snprintf(linebuf, sizeof(linebuf), "Compiled method");
        } else if (lineno == -3) {
            (void)md_snprintf(linebuf, sizeof(linebuf), "Native method");
        } else if (lineno == -1) {
            (void)md_snprintf(linebuf, sizeof(linebuf), "Unknown line");
        } else {
            (void)md_snprintf(linebuf, sizeof(linebuf), "%d", lineno);
        }
        linebuf[sizeof(linebuf)-1] = 0;
        class_name = signature_to_name(csig);
        if ( mname == NULL ) {
            mname = "<Unknown Method>";
        }
        if ( sname == NULL ) {
            sname = "<Unknown Source>";
        }
        write_printf("\t%s.%s(%s:%s)\n", class_name, mname, sname, linebuf);
        HPROF_FREE(class_name);
    }
}

void
io_write_trace_footer(SerialNumber trace_serial_num,
                SerialNumber thread_serial_num, jint n_frames)
{
}

#define CPU_SAMPLES_RECORD_NAME ("CPU SAMPLES")
#define CPU_TIMES_RECORD_NAME ("CPU TIME (ms)")

void
io_write_cpu_samples_header(jlong total_cost, jint n_items)
{

    if (gdata->output_format == 'b') {
        write_header(HPROF_CPU_SAMPLES, (n_items * (4 * 2)) + (4 * 2));
        write_u4((jint)total_cost);
        write_u4(n_items);
    } else {
        time_t t;
        char *record_name;

        if ( gdata->cpu_sampling ) {
            record_name = CPU_SAMPLES_RECORD_NAME;
        } else {
            record_name = CPU_TIMES_RECORD_NAME;
        }
        t = time(0);
        write_printf("%s BEGIN (total = %d) %s", record_name,
                     /*jlong*/(int)total_cost, ctime(&t));
        if ( n_items > 0 ) {
            write_printf("rank   self  accum   count trace method\n");
        }
    }
}

void
io_write_cpu_samples_elem(jint index, double percent, double accum,
                jint num_hits, jlong cost, SerialNumber trace_serial_num,
                jint n_frames, char *csig, char *mname)
{
    CHECK_TRACE_SERIAL_NO(trace_serial_num);
    if (gdata->output_format == 'b') {
        write_u4((jint)cost);
        write_u4(trace_serial_num);
    } else {
        write_printf("%4u %5.2f%% %5.2f%% %7u %5u",
                     index, percent, accum, num_hits,
                     trace_serial_num);
        if (n_frames > 0) {
            char * class_name;

            class_name = signature_to_name(csig);
            write_printf(" %s.%s\n", class_name, mname);
            HPROF_FREE(class_name);
        } else {
            write_printf(" <empty trace>\n");
        }
    }
}

void
io_write_cpu_samples_footer(void)
{
    if (gdata->output_format == 'b') {
        not_implemented();
    } else {
        char *record_name;

        if ( gdata->cpu_sampling ) {
            record_name = CPU_SAMPLES_RECORD_NAME;
        } else {
            record_name = CPU_TIMES_RECORD_NAME;
        }
        write_printf("%s END\n", record_name);
    }
}

void
io_write_heap_summary(jlong total_live_bytes, jlong total_live_instances,
                jlong total_alloced_bytes, jlong total_alloced_instances)
{
    if (gdata->output_format == 'b') {
        write_header(HPROF_HEAP_SUMMARY, 4 * 6);
        write_u4((jint)total_live_bytes);
        write_u4((jint)total_live_instances);
        write_u8(total_alloced_bytes);
        write_u8(total_alloced_instances);
    }
}

void
io_write_oldprof_header(void)
{
    if ( gdata->old_timing_format ) {
        write_printf("count callee caller time\n");
    }
}

void
io_write_oldprof_elem(jint num_hits, jint num_frames, char *csig_callee,
            char *mname_callee, char *msig_callee, char *csig_caller,
            char *mname_caller, char *msig_caller, jlong cost)
{
    if ( gdata->old_timing_format ) {
        char * class_name_callee;
        char * class_name_caller;

        class_name_callee = signature_to_name(csig_callee);
        class_name_caller = signature_to_name(csig_caller);
        write_printf("%d ", num_hits);
        if (num_frames >= 1) {
            write_printf("%s.%s%s ", class_name_callee,
                 mname_callee,  msig_callee);
        } else {
            write_printf("%s ", "<unknown callee>");
        }
        if (num_frames > 1) {
            write_printf("%s.%s%s ", class_name_caller,
                 mname_caller,  msig_caller);
        } else {
            write_printf("%s ", "<unknown caller>");
        }
        write_printf("%d\n", (int)cost);
        HPROF_FREE(class_name_callee);
        HPROF_FREE(class_name_caller);
    }
}

void
io_write_oldprof_footer(void)
{
}

void
io_write_monitor_header(jlong total_time)
{
    if (gdata->output_format == 'b') {
        not_implemented();
    } else {
        time_t t = time(0);

        t = time(0);
        write_printf("MONITOR TIME BEGIN (total = %u ms) %s",
                                (int)total_time, ctime(&t));
        if (total_time > 0) {
            write_printf("rank   self  accum   count trace monitor\n");
        }
    }
}

void
io_write_monitor_elem(jint index, double percent, double accum,
            jint num_hits, SerialNumber trace_serial_num, char *sig)
{
    CHECK_TRACE_SERIAL_NO(trace_serial_num);
    if (gdata->output_format == 'b') {
        not_implemented();
    } else {
        char *class_name;

        class_name = signature_to_name(sig);
        write_printf("%4u %5.2f%% %5.2f%% %7u %5u %s (Java)\n",
                     index, percent, accum, num_hits,
                     trace_serial_num, class_name);
        HPROF_FREE(class_name);
    }
}

void
io_write_monitor_footer(void)
{
    if (gdata->output_format == 'b') {
        not_implemented();
    } else {
        write_printf("MONITOR TIME END\n");
    }
}

void
io_write_monitor_sleep(jlong timeout, SerialNumber thread_serial_num)
{
    if (gdata->output_format == 'b') {
        not_implemented();
    } else {
        if ( thread_serial_num == 0 ) {
            write_printf("SLEEP: timeout=%d, <unknown thread>\n",
                        (int)timeout);
        } else {
            CHECK_THREAD_SERIAL_NO(thread_serial_num);
            write_printf("SLEEP: timeout=%d, thread %d\n",
                        (int)timeout, thread_serial_num);
        }
    }
}

void
io_write_monitor_wait(char *sig, jlong timeout,
                SerialNumber thread_serial_num)
{
    if (gdata->output_format == 'b') {
        not_implemented();
    } else {
        if ( thread_serial_num == 0 ) {
            write_printf("WAIT: MONITOR %s, timeout=%d, <unknown thread>\n",
                        sig, (int)timeout);
        } else {
            CHECK_THREAD_SERIAL_NO(thread_serial_num);
            write_printf("WAIT: MONITOR %s, timeout=%d, thread %d\n",
                        sig, (int)timeout, thread_serial_num);
        }
    }
}

void
io_write_monitor_waited(char *sig, jlong time_waited,
                SerialNumber thread_serial_num)
{
    if (gdata->output_format == 'b') {
        not_implemented();
    } else {
        if ( thread_serial_num == 0 ) {
            write_printf("WAITED: MONITOR %s, time_waited=%d, <unknown thread>\n",
                        sig, (int)time_waited);
        } else {
            CHECK_THREAD_SERIAL_NO(thread_serial_num);
            write_printf("WAITED: MONITOR %s, time_waited=%d, thread %d\n",
                        sig, (int)time_waited, thread_serial_num);
        }
    }
}

void
io_write_monitor_exit(char *sig, SerialNumber thread_serial_num)
{
    if (gdata->output_format == 'b') {
        not_implemented();
    } else {
        if ( thread_serial_num == 0 ) {
            write_printf("EXIT: MONITOR %s, <unknown thread>\n", sig);
        } else {
            CHECK_THREAD_SERIAL_NO(thread_serial_num);
            write_printf("EXIT: MONITOR %s, thread %d\n",
                        sig, thread_serial_num);
        }
    }
}

void
io_write_monitor_dump_header(void)
{
    if (gdata->output_format == 'b') {
        not_implemented();
    } else {
        write_printf("MONITOR DUMP BEGIN\n");
    }
}

void
io_write_monitor_dump_thread_state(SerialNumber thread_serial_num,
                      SerialNumber trace_serial_num,
                      jint threadState)
{
    CHECK_THREAD_SERIAL_NO(thread_serial_num);
    CHECK_TRACE_SERIAL_NO(trace_serial_num);
    if (gdata->output_format == 'b') {
        not_implemented();
    } else {
        char tstate[20];

        tstate[0] = 0;

        if (threadState & JVMTI_THREAD_STATE_SUSPENDED) {
            (void)strcat(tstate,"S|");
        }
        if (threadState & JVMTI_THREAD_STATE_INTERRUPTED) {
            (void)strcat(tstate,"intr|");
        }
        if (threadState & JVMTI_THREAD_STATE_IN_NATIVE) {
            (void)strcat(tstate,"native|");
        }
        if ( ! ( threadState & JVMTI_THREAD_STATE_ALIVE ) ) {
            if ( threadState & JVMTI_THREAD_STATE_TERMINATED ) {
                (void)strcat(tstate,"ZO");
            } else {
                (void)strcat(tstate,"NS");
            }
        } else {
            if ( threadState & JVMTI_THREAD_STATE_SLEEPING ) {
                (void)strcat(tstate,"SL");
            } else if ( threadState & JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER ) {
                (void)strcat(tstate,"MW");
            } else if ( threadState & JVMTI_THREAD_STATE_WAITING ) {
                (void)strcat(tstate,"CW");
            } else if ( threadState & JVMTI_THREAD_STATE_RUNNABLE ) {
                (void)strcat(tstate,"R");
            } else {
                (void)strcat(tstate,"UN");
            }
        }
        write_printf("    THREAD %d, trace %d, status: %s\n",
                     thread_serial_num, trace_serial_num, tstate);
    }
}

void
io_write_monitor_dump_state(char *sig, SerialNumber thread_serial_num,
                    jint entry_count,
                    SerialNumber *waiters, jint waiter_count,
                    SerialNumber *notify_waiters, jint notify_waiter_count)
{
    if (gdata->output_format == 'b') {
        not_implemented();
    } else {
        int i;

        if ( thread_serial_num != 0 ) {
            CHECK_THREAD_SERIAL_NO(thread_serial_num);
            write_printf("    MONITOR %s\n", sig);
            write_printf("\towner: thread %d, entry count: %d\n",
                thread_serial_num, entry_count);
        } else {
            write_printf("    MONITOR %s unowned\n", sig);
        }
        write_printf("\twaiting to enter:");
        for (i = 0; i < waiter_count; i++) {
            write_thread_serial_number(waiters[i],
                                (i != (waiter_count-1)));
        }
        write_printf("\n");
        write_printf("\twaiting to be notified:");
        for (i = 0; i < notify_waiter_count; i++) {
            write_thread_serial_number(notify_waiters[i],
                                (i != (notify_waiter_count-1)));
        }
        write_printf("\n");
    }
}

void
io_write_monitor_dump_footer(void)
{
    if (gdata->output_format == 'b') {
        not_implemented();
    } else {
        write_printf("MONITOR DUMP END\n");
    }
}

/* ----------------------------------------------------------------- */
/* These functions write to a separate file */

void
io_heap_header(jlong total_live_instances, jlong total_live_bytes)
{
    if (gdata->output_format != 'b') {
        time_t t;

        t = time(0);
        heap_printf("HEAP DUMP BEGIN (%u objects, %u bytes) %s",
                        /*jlong*/(int)total_live_instances,
                        /*jlong*/(int)total_live_bytes, ctime(&t));
    }
}

void
io_heap_root_thread_object(ObjectIndex thread_obj_id,
                SerialNumber thread_serial_num, SerialNumber trace_serial_num)
{
    CHECK_THREAD_SERIAL_NO(thread_serial_num);
    CHECK_TRACE_SERIAL_NO(trace_serial_num);
    if (gdata->output_format == 'b') {
         heap_tag(HPROF_GC_ROOT_THREAD_OBJ);
         heap_id(thread_obj_id);
         heap_u4(thread_serial_num);
         heap_u4(trace_serial_num);
    } else {
        heap_printf("ROOT %x (kind=<thread>, id=%u, trace=%u)\n",
                     thread_obj_id, thread_serial_num, trace_serial_num);
    }
}

void
io_heap_root_unknown(ObjectIndex obj_id)
{
    if (gdata->output_format == 'b') {
        heap_tag(HPROF_GC_ROOT_UNKNOWN);
        heap_id(obj_id);
    } else {
        heap_printf("ROOT %x (kind=<unknown>)\n", obj_id);
    }
}

void
io_heap_root_jni_global(ObjectIndex obj_id, SerialNumber gref_serial_num,
                         SerialNumber trace_serial_num)
{
    CHECK_TRACE_SERIAL_NO(trace_serial_num);
    if (gdata->output_format == 'b') {
        heap_tag(HPROF_GC_ROOT_JNI_GLOBAL);
        heap_id(obj_id);
        heap_id(gref_serial_num);
    } else {
        heap_printf("ROOT %x (kind=<JNI global ref>, "
                     "id=%x, trace=%u)\n",
                     obj_id, gref_serial_num, trace_serial_num);
    }
}

void
io_heap_root_jni_local(ObjectIndex obj_id, SerialNumber thread_serial_num,
        jint frame_depth)
{
    CHECK_THREAD_SERIAL_NO(thread_serial_num);
    if (gdata->output_format == 'b') {
        heap_tag(HPROF_GC_ROOT_JNI_LOCAL);
        heap_id(obj_id);
        heap_u4(thread_serial_num);
        heap_u4(frame_depth);
    } else {
        heap_printf("ROOT %x (kind=<JNI local ref>, "
                     "thread=%u, frame=%d)\n",
                     obj_id, thread_serial_num, frame_depth);
    }
}

void
io_heap_root_system_class(ObjectIndex obj_id, char *sig, SerialNumber class_serial_num)
{
    if (gdata->output_format == 'b') {
        heap_tag(HPROF_GC_ROOT_STICKY_CLASS);
        heap_id(obj_id);
    } else {
        char *class_name;

        class_name = signature_to_name(sig);
        heap_printf("ROOT %x (kind=<system class>, name=%s)\n",
                     obj_id, class_name);
        HPROF_FREE(class_name);
    }
}

void
io_heap_root_monitor(ObjectIndex obj_id)
{
    if (gdata->output_format == 'b') {
        heap_tag(HPROF_GC_ROOT_MONITOR_USED);
        heap_id(obj_id);
    } else {
        heap_printf("ROOT %x (kind=<busy monitor>)\n", obj_id);
    }
}

void
io_heap_root_thread(ObjectIndex obj_id, SerialNumber thread_serial_num)
{
    CHECK_THREAD_SERIAL_NO(thread_serial_num);
    if (gdata->output_format == 'b') {
        heap_tag(HPROF_GC_ROOT_THREAD_BLOCK);
        heap_id(obj_id);
        heap_u4(thread_serial_num);
    } else {
        heap_printf("ROOT %x (kind=<thread block>, thread=%u)\n",
                     obj_id, thread_serial_num);
    }
}

void
io_heap_root_java_frame(ObjectIndex obj_id, SerialNumber thread_serial_num,
        jint frame_depth)
{
    CHECK_THREAD_SERIAL_NO(thread_serial_num);
    if (gdata->output_format == 'b') {
        heap_tag(HPROF_GC_ROOT_JAVA_FRAME);
        heap_id(obj_id);
        heap_u4(thread_serial_num);
        heap_u4(frame_depth);
    } else {
        heap_printf("ROOT %x (kind=<Java stack>, "
                     "thread=%u, frame=%d)\n",
                     obj_id, thread_serial_num, frame_depth);
    }
}

void
io_heap_root_native_stack(ObjectIndex obj_id, SerialNumber thread_serial_num)
{
    CHECK_THREAD_SERIAL_NO(thread_serial_num);
    if (gdata->output_format == 'b') {
        heap_tag(HPROF_GC_ROOT_NATIVE_STACK);
        heap_id(obj_id);
        heap_u4(thread_serial_num);
    } else {
        heap_printf("ROOT %x (kind=<native stack>, thread=%u)\n",
                     obj_id, thread_serial_num);
    }
}

static jboolean
is_static_field(jint modifiers)
{
    if ( modifiers & JVM_ACC_STATIC ) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

static jboolean
is_inst_field(jint modifiers)
{
    if ( modifiers & JVM_ACC_STATIC ) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

void
io_heap_class_dump(ClassIndex cnum, char *sig, ObjectIndex class_id,
                SerialNumber trace_serial_num,
                ObjectIndex super_id, ObjectIndex loader_id,
                ObjectIndex signers_id, ObjectIndex domain_id,
                jint size,
                jint n_cpool, ConstantPoolValue *cpool,
                jint n_fields, FieldInfo *fields, jvalue *fvalues)
{
    CHECK_TRACE_SERIAL_NO(trace_serial_num);
    if (gdata->output_format == 'b') {
        int  i;
        jint n_static_fields;
        jint n_inst_fields;
        jint inst_size;
        jint saved_inst_size;

        n_static_fields = 0;
        n_inst_fields = 0;
        inst_size = 0;

        /* These do NOT go into the heap output */
        for ( i = 0 ; i < n_fields ; i++ ) {
            if ( fields[i].cnum == cnum &&
                 is_static_field(fields[i].modifiers) ) {
                char *field_name;

                field_name = string_get(fields[i].name_index);
                (void)write_name_first(field_name);
                n_static_fields++;
            }
            if ( is_inst_field(fields[i].modifiers) ) {
                inst_size += size_from_field_info(fields[i].primSize);
                if ( fields[i].cnum == cnum ) {
                    char *field_name;

                    field_name = string_get(fields[i].name_index);
                    (void)write_name_first(field_name);
                    n_inst_fields++;
                }
            }
        }

        /* Verify that the instance size we have calculated as we went
         *   through the fields, matches what is saved away with this
         *   class.
         */
        if ( size >= 0 ) {
            saved_inst_size = class_get_inst_size(cnum);
            if ( saved_inst_size == -1 ) {
                class_set_inst_size(cnum, inst_size);
            } else if ( saved_inst_size != inst_size ) {
                HPROF_ERROR(JNI_TRUE, "Mis-match on instance size in class dump");
            }
        }

        heap_tag(HPROF_GC_CLASS_DUMP);
        heap_id(class_id);
        heap_u4(trace_serial_num);
        heap_id(super_id);
        heap_id(loader_id);
        heap_id(signers_id);
        heap_id(domain_id);
        heap_id(0);
        heap_id(0);
        heap_u4(inst_size); /* Must match inst_size in instance dump */

        heap_u2((unsigned short)n_cpool);
        for ( i = 0 ; i < n_cpool ; i++ ) {
            HprofType kind;
            jint size;

            type_from_signature(string_get(cpool[i].sig_index),
                            &kind, &size);
            heap_u2((unsigned short)(cpool[i].constant_pool_index));
            heap_u1(kind);
            HPROF_ASSERT(!HPROF_TYPE_IS_PRIMITIVE(kind));
            heap_element(kind, size, cpool[i].value);
        }

        heap_u2((unsigned short)n_static_fields);
        for ( i = 0 ; i < n_fields ; i++ ) {
            if ( fields[i].cnum == cnum &&
                 is_static_field(fields[i].modifiers) ) {
                char *field_name;
                HprofType kind;
                jint size;

                type_from_signature(string_get(fields[i].sig_index),
                                &kind, &size);
                field_name = string_get(fields[i].name_index);
                heap_name(field_name);
                heap_u1(kind);
                heap_element(kind, size, fvalues[i]);
            }
        }

        heap_u2((unsigned short)n_inst_fields); /* Does not include super class */
        for ( i = 0 ; i < n_fields ; i++ ) {
            if ( fields[i].cnum == cnum &&
                 is_inst_field(fields[i].modifiers) ) {
                HprofType kind;
                jint size;
                char *field_name;

                field_name = string_get(fields[i].name_index);
                type_from_signature(string_get(fields[i].sig_index),
                            &kind, &size);
                heap_name(field_name);
                heap_u1(kind);
            }
        }
    } else {
        char * class_name;
        int i;

        class_name = signature_to_name(sig);
        heap_printf("CLS %x (name=%s, trace=%u)\n",
                     class_id, class_name, trace_serial_num);
        HPROF_FREE(class_name);
        if (super_id) {
            heap_printf("\tsuper\t\t%x\n", super_id);
        }
        if (loader_id) {
            heap_printf("\tloader\t\t%x\n", loader_id);
        }
        if (signers_id) {
            heap_printf("\tsigners\t\t%x\n", signers_id);
        }
        if (domain_id) {
            heap_printf("\tdomain\t\t%x\n", domain_id);
        }
        for ( i = 0 ; i < n_fields ; i++ ) {
            if ( fields[i].cnum == cnum &&
                 is_static_field(fields[i].modifiers) ) {
                HprofType kind;
                jint size;

                type_from_signature(string_get(fields[i].sig_index),
                                &kind, &size);
                if ( !HPROF_TYPE_IS_PRIMITIVE(kind) ) {
                    if (fvalues[i].i != 0 ) {
                        char *field_name;

                        field_name = string_get(fields[i].name_index);
                        heap_printf("\tstatic %s\t%x\n", field_name,
                            fvalues[i].i);
                    }
                }
            }
        }
        for ( i = 0 ; i < n_cpool ; i++ ) {
            HprofType kind;
            jint size;

            type_from_signature(string_get(cpool[i].sig_index), &kind, &size);
            if ( !HPROF_TYPE_IS_PRIMITIVE(kind) ) {
                if (cpool[i].value.i != 0 ) {
                    heap_printf("\tconstant pool entry %d\t%x\n",
                            cpool[i].constant_pool_index, cpool[i].value.i);
                }
            }
        }
    }
}

/* Dump the instance fields in the right order. */
static int
dump_instance_fields(ClassIndex cnum,
                     FieldInfo *fields, jvalue *fvalues, jint n_fields)
{
    ClassIndex super_cnum;
    int        i;
    int        nbytes;

    HPROF_ASSERT(cnum!=0);

    nbytes = 0;
    for (i = 0; i < n_fields; i++) {
        if ( fields[i].cnum == cnum &&
             is_inst_field(fields[i].modifiers) ) {
            HprofType kind;
            int size;

            type_from_signature(string_get(fields[i].sig_index),
                            &kind, &size);
            heap_element(kind, size, fvalues[i]);
            nbytes += size;
        }
    }

    super_cnum = class_get_super(cnum);
    if ( super_cnum != 0 ) {
        nbytes += dump_instance_fields(super_cnum, fields, fvalues, n_fields);
    }
    return nbytes;
}

void
io_heap_instance_dump(ClassIndex cnum, ObjectIndex obj_id,
                SerialNumber trace_serial_num,
                ObjectIndex class_id, jint size, char *sig,
                FieldInfo *fields, jvalue *fvalues, jint n_fields)
{
    CHECK_TRACE_SERIAL_NO(trace_serial_num);
    if (gdata->output_format == 'b') {
        jint inst_size;
        jint saved_inst_size;
        int  i;
        int  nbytes;

        inst_size = 0;
        for (i = 0; i < n_fields; i++) {
            if ( is_inst_field(fields[i].modifiers) ) {
                inst_size += size_from_field_info(fields[i].primSize);
            }
        }

        /* Verify that the instance size we have calculated as we went
         *   through the fields, matches what is saved away with this
         *   class.
         */
        saved_inst_size = class_get_inst_size(cnum);
        if ( saved_inst_size == -1 ) {
            class_set_inst_size(cnum, inst_size);
        } else if ( saved_inst_size != inst_size ) {
            HPROF_ERROR(JNI_TRUE, "Mis-match on instance size in instance dump");
        }

        heap_tag(HPROF_GC_INSTANCE_DUMP);
        heap_id(obj_id);
        heap_u4(trace_serial_num);
        heap_id(class_id);
        heap_u4(inst_size); /* Must match inst_size in class dump */

        /* Order must be class, super, super's super, ... */
        nbytes = dump_instance_fields(cnum, fields, fvalues, n_fields);
        HPROF_ASSERT(nbytes==inst_size);
    } else {
        char * class_name;
        int i;

        class_name = signature_to_name(sig);
        heap_printf("OBJ %x (sz=%u, trace=%u, class=%s@%x)\n",
                     obj_id, size, trace_serial_num, class_name, class_id);
        HPROF_FREE(class_name);

        for (i = 0; i < n_fields; i++) {
            if ( is_inst_field(fields[i].modifiers) ) {
                HprofType kind;
                int size;

                type_from_signature(string_get(fields[i].sig_index),
                            &kind, &size);
                if ( !HPROF_TYPE_IS_PRIMITIVE(kind) ) {
                    if (fvalues[i].i != 0 ) {
                        char *sep;
                        ObjectIndex val_id;
                        char *field_name;

                        field_name = string_get(fields[i].name_index);
                        val_id =  (ObjectIndex)(fvalues[i].i);
                        sep = (int)strlen(field_name) < 8 ? "\t" : "";
                        heap_printf("\t%s\t%s%x\n", field_name, sep, val_id);
                    }
                }
            }
        }
    }
}

void
io_heap_object_array(ObjectIndex obj_id, SerialNumber trace_serial_num,
                jint size, jint num_elements, char *sig, ObjectIndex *values,
                ObjectIndex class_id)
{
    CHECK_TRACE_SERIAL_NO(trace_serial_num);
    if (gdata->output_format == 'b') {

        heap_tag(HPROF_GC_OBJ_ARRAY_DUMP);
        heap_id(obj_id);
        heap_u4(trace_serial_num);
        heap_u4(num_elements);
        heap_id(class_id);
        heap_elements(HPROF_NORMAL_OBJECT, num_elements,
                (jint)sizeof(HprofId), (void*)values);
    } else {
        char *name;
        int i;

        name = signature_to_name(sig);
        heap_printf("ARR %x (sz=%u, trace=%u, nelems=%u, elem type=%s@%x)\n",
                     obj_id, size, trace_serial_num, num_elements,
                     name, class_id);
        for (i = 0; i < num_elements; i++) {
            ObjectIndex id;

            id = values[i];
            if (id != 0) {
                heap_printf("\t[%u]\t\t%x\n", i, id);
            }
        }
        HPROF_FREE(name);
    }
}

void
io_heap_prim_array(ObjectIndex obj_id, SerialNumber trace_serial_num,
              jint size, jint num_elements, char *sig, void *elements)
{
    CHECK_TRACE_SERIAL_NO(trace_serial_num);
    if (gdata->output_format == 'b') {
        HprofType kind;
        jint  esize;

        type_array(sig, &kind, &esize);
        HPROF_ASSERT(HPROF_TYPE_IS_PRIMITIVE(kind));
        heap_tag(HPROF_GC_PRIM_ARRAY_DUMP);
        heap_id(obj_id);
        heap_u4(trace_serial_num);
        heap_u4(num_elements);
        heap_u1(kind);
        heap_elements(kind, num_elements, esize, elements);
    } else {
        char *name;

        name = signature_to_name(sig);
        heap_printf("ARR %x (sz=%u, trace=%u, nelems=%u, elem type=%s)\n",
                     obj_id, size, trace_serial_num, num_elements, name);
        HPROF_FREE(name);
    }
}

/* Move file bytes into supplied raw interface */
static void
write_raw_from_file(int fd, jlong byteCount, void (*raw_interface)(void *,int))
{
    char *buf;
    int   buf_len;
    int   left;
    int   nbytes;

    HPROF_ASSERT(fd >= 0);

    /* Move contents of this file into output file. */
    buf_len = FILE_IO_BUFFER_SIZE*2; /* Twice as big! */
    buf = HPROF_MALLOC(buf_len);
    HPROF_ASSERT(buf!=NULL);

    /* Keep track of how many we have left */
    left = (int)byteCount;
    do {
        int count;

        count = buf_len;
        if ( count > left ) count = left;
        nbytes = md_read(fd, buf, count);
        if (nbytes < 0) {
            system_error("read", nbytes, errno);
            break;
        }
        if (nbytes == 0) {
            break;
        }
        if ( nbytes > 0 ) {
            (*raw_interface)(buf, nbytes);
            left -= nbytes;
        }
    } while ( left > 0 );

    if (left > 0 && nbytes == 0) {
        HPROF_ERROR(JNI_TRUE, "File size is smaller than bytes written");
    }
    HPROF_FREE(buf);
}

/* Write out a heap segment, and copy remainder to top of file. */
static void
dump_heap_segment_and_reset(jlong segment_size)
{
    int   fd;
    jlong last_chunk_len;

    HPROF_ASSERT(gdata->heap_fd >= 0);

    /* Flush all bytes to the heap dump file */
    heap_flush();

    /* Last segment? */
    last_chunk_len = gdata->heap_write_count - segment_size;
    HPROF_ASSERT(last_chunk_len>=0);

    /* Re-open in proper way, binary vs. ascii is important */
    if (gdata->output_format == 'b') {
        int   tag;

        if ( gdata->segmented == JNI_TRUE ) { /* 1.0.2 */
            tag = HPROF_HEAP_DUMP_SEGMENT; /* 1.0.2 */
        } else {
            tag = HPROF_HEAP_DUMP; /* Just one segment */
            HPROF_ASSERT(last_chunk_len==0);
        }

        /* Write header for binary heap dump (don't know size until now) */
        write_header(tag, (jint)segment_size);

        fd = md_open_binary(gdata->heapfilename);
    } else {
        fd = md_open(gdata->heapfilename);
    }

    /* Move file bytes into hprof dump file */
    write_raw_from_file(fd, segment_size, &write_raw);

    /* Clear the byte count and reset the heap file. */
    if ( md_seek(gdata->heap_fd, (jlong)0) != (jlong)0 ) {
        HPROF_ERROR(JNI_TRUE, "Cannot seek to beginning of heap info file");
    }
    gdata->heap_write_count = (jlong)0;
    gdata->heap_last_tag_position = (jlong)0;

    /* Move trailing bytes from heap dump file to beginning of file */
    if ( last_chunk_len > 0 ) {
        write_raw_from_file(fd, last_chunk_len, &heap_raw);
    }

    /* Close the temp file handle */
    md_close(fd);
}

void
io_heap_footer(void)
{
    HPROF_ASSERT(gdata->heap_fd >= 0);

    /* Flush all bytes to the heap dump file */
    heap_flush();

    /* Send out the last (or maybe only) segment */
    dump_heap_segment_and_reset(gdata->heap_write_count);

    /* Write out the last tag */
    if (gdata->output_format != 'b') {
        write_printf("HEAP DUMP END\n");
    } else {
        if ( gdata->segmented == JNI_TRUE ) { /* 1.0.2 */
            write_header(HPROF_HEAP_DUMP_END, 0);
        }
    }
}
