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

/* Monitor contention tracking and monitor wait handling. */

/*
 * Monitor's under contention are unique per trace and signature.
 *  Two monitors with the same trace and signature will be treated
 *  the same as far as accumulated contention time.
 *
 * The tls table (or thread table) will be used to store the monitor in
 *   contention or being waited on.
 *
 * Monitor wait activity is emitted as it happens.
 *
 * Monitor contention is tabulated and summarized at dump time.
 *
 */

#include "hprof.h"

typedef struct MonitorKey {
    TraceIndex   trace_index;
    StringIndex  sig_index;
} MonitorKey;

typedef struct MonitorInfo {
    jint         num_hits;
    jlong        contended_time;
} MonitorInfo;

typedef struct IterateInfo {
    MonitorIndex *monitors;
    int           count;
    jlong         total_contended_time;
} IterateInfo;

/* Private internal functions. */

static MonitorKey*
get_pkey(MonitorIndex index)
{
    void * key_ptr;
    int    key_len;

    table_get_key(gdata->monitor_table, index, &key_ptr, &key_len);
    HPROF_ASSERT(key_len==sizeof(MonitorKey));
    HPROF_ASSERT(key_ptr!=NULL);
    return (MonitorKey*)key_ptr;
}

static MonitorInfo *
get_info(MonitorIndex index)
{
    MonitorInfo *       info;

    HPROF_ASSERT(index!=0);
    info = (MonitorInfo*)table_get_info(gdata->monitor_table, index);
    HPROF_ASSERT(info!=NULL);
    return info;
}

static MonitorIndex
find_or_create_entry(JNIEnv *env, TraceIndex trace_index, jobject object)
{
    static MonitorKey empty_key;
    MonitorKey   key;
    MonitorIndex index;
    char        *sig;

    HPROF_ASSERT(object!=NULL);
    WITH_LOCAL_REFS(env, 1) {
        jclass clazz;

        clazz = getObjectClass(env, object);
        getClassSignature(clazz, &sig, NULL);
    } END_WITH_LOCAL_REFS;

    key                    = empty_key;
    key.trace_index        = trace_index;
    key.sig_index = string_find_or_create(sig);
    jvmtiDeallocate(sig);
    index = table_find_or_create_entry(gdata->monitor_table, &key,
                        (int)sizeof(key), NULL, NULL);
    return index;
}

static void
cleanup_item(MonitorIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
}

static void
list_item(TableIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    MonitorInfo *info;
    MonitorKey  *pkey;

    HPROF_ASSERT(key_len==sizeof(MonitorKey));
    HPROF_ASSERT(key_ptr!=NULL);
    HPROF_ASSERT(info_ptr!=NULL);
    pkey = (MonitorKey*)key_ptr;
    info = (MonitorInfo *)info_ptr;
    debug_message(
                "Monitor 0x%08x: trace=0x%08x, sig=0x%08x, "
                "num_hits=%d, contended_time=(%d,%d)\n",
                 index,
                 pkey->trace_index,
                 pkey->sig_index,
                 info->num_hits,
                 jlong_high(info->contended_time),
                 jlong_low(info->contended_time));
}

static void
collect_iterator(MonitorIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    MonitorInfo *info;
    IterateInfo *iterate;

    HPROF_ASSERT(key_len==sizeof(MonitorKey));
    HPROF_ASSERT(info_ptr!=NULL);
    HPROF_ASSERT(arg!=NULL);
    iterate = (IterateInfo *)arg;
    info = (MonitorInfo *)info_ptr;
    iterate->monitors[iterate->count++] = index;
    iterate->total_contended_time += info->contended_time;
}

static int
qsort_compare(const void *p_monitor1, const void *p_monitor2)
{
    MonitorInfo * info1;
    MonitorInfo * info2;
    MonitorIndex  monitor1;
    MonitorIndex  monitor2;
    jlong         result;

    HPROF_ASSERT(p_monitor1!=NULL);
    HPROF_ASSERT(p_monitor2!=NULL);
    monitor1 = *(MonitorIndex *)p_monitor1;
    monitor2 = *(MonitorIndex *)p_monitor2;
    info1 = get_info(monitor1);
    info2 = get_info(monitor2);

    result = info2->contended_time - info1->contended_time;
    if (result < (jlong)0) {
        return -1;
    } else if ( result > (jlong)0 ) {
        return 1;
    }
    return info2->num_hits - info1->num_hits;
}

static void
clear_item(MonitorIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    MonitorInfo *info;

    HPROF_ASSERT(key_len==sizeof(MonitorKey));
    HPROF_ASSERT(info_ptr!=NULL);
    info = (MonitorInfo *)info_ptr;
    info->contended_time = 0;
}

static TraceIndex
get_trace(TlsIndex tls_index, JNIEnv *env)
{
    TraceIndex trace_index;

    trace_index = tls_get_trace(tls_index, env, gdata->max_trace_depth, JNI_FALSE);
    return trace_index;
}

/* External functions (called from hprof_init.c) */

void
monitor_init(void)
{
    gdata->monitor_table = table_initialize("Monitor",
                            32, 32, 31, (int)sizeof(MonitorInfo));
}

void
monitor_list(void)
{
    debug_message(
        "------------------- Monitor Table ------------------------\n");
    table_walk_items(gdata->monitor_table, &list_item, NULL);
    debug_message(
        "----------------------------------------------------------\n");
}

void
monitor_cleanup(void)
{
    table_cleanup(gdata->monitor_table, &cleanup_item, (void*)NULL);
    gdata->monitor_table = NULL;
}

void
monitor_clear(void)
{
    table_walk_items(gdata->monitor_table, &clear_item, NULL);
}

/* Contended monitor output */
void
monitor_write_contended_time(JNIEnv *env, double cutoff)
{
    int n_entries;

    n_entries = table_element_count(gdata->monitor_table);
    if ( n_entries == 0 ) {
        return;
    }

    rawMonitorEnter(gdata->data_access_lock); {
        IterateInfo iterate;
        int i;
        int n_items;
        jlong total_contended_time;

        /* First write all trace we might refer to. */
        trace_output_unmarked(env);

        /* Looking for an array of monitor index values of interest */
        iterate.monitors = HPROF_MALLOC(n_entries*(int)sizeof(MonitorIndex));
        (void)memset(iterate.monitors, 0, n_entries*(int)sizeof(MonitorIndex));

        /* Get a combined total and an array of monitor index numbers */
        iterate.total_contended_time = 0;
        iterate.count = 0;
        table_walk_items(gdata->monitor_table, &collect_iterator, &iterate);

        /* Sort that list */
        n_entries = iterate.count;
        if ( n_entries > 0 ) {
            qsort(iterate.monitors, n_entries, sizeof(MonitorIndex),
                        &qsort_compare);
        }

        /* Apply the cutoff */
        n_items = 0;
        for (i = 0; i < n_entries; i++) {
            MonitorIndex index;
            MonitorInfo *info;
            double percent;

            index = iterate.monitors[i];
            info = get_info(index);
            percent = (double)info->contended_time /
                      (double)iterate.total_contended_time;
            if (percent < cutoff) {
                break;
            }
            iterate.monitors[n_items++] = index;
        }

        /* Output the items that make sense */
        total_contended_time = iterate.total_contended_time / 1000000;

        if ( n_items > 0 && total_contended_time > 0 ) {
            double accum;

            /* Output the info on this monitor enter site */
            io_write_monitor_header(total_contended_time);

            accum = 0.0;
            for (i = 0; i < n_items; i++) {
                MonitorIndex index;
                MonitorInfo *info;
                MonitorKey *pkey;
                double percent;
                char *sig;

                index = iterate.monitors[i];
                pkey = get_pkey(index);
                info = get_info(index);

                sig = string_get(pkey->sig_index);

                percent = (double)info->contended_time /
                          (double)iterate.total_contended_time * 100.0;
                accum += percent;
                io_write_monitor_elem(i + 1, percent, accum,
                                    info->num_hits,
                                    trace_get_serial_number(pkey->trace_index),
                                    sig);
            }
            io_write_monitor_footer();
        }
        HPROF_FREE(iterate.monitors);
    } rawMonitorExit(gdata->data_access_lock);
}

void
monitor_contended_enter_event(JNIEnv *env, jthread thread, jobject object)
{
    TlsIndex     tls_index;
    MonitorIndex index;
    TraceIndex   trace_index;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(thread!=NULL);
    HPROF_ASSERT(object!=NULL);

    tls_index =  tls_find_or_create(env, thread);
    HPROF_ASSERT(tls_get_monitor(tls_index)==0);
    trace_index = get_trace(tls_index, env);
    index = find_or_create_entry(env, trace_index, object);
    tls_monitor_start_timer(tls_index);
    tls_set_monitor(tls_index, index);
}

void
monitor_contended_entered_event(JNIEnv* env, jthread thread, jobject object)
{
    TlsIndex     tls_index;
    MonitorInfo *info;
    MonitorIndex index;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(object!=NULL);
    HPROF_ASSERT(thread!=NULL);

    tls_index = tls_find_or_create(env, thread);
    HPROF_ASSERT(tls_index!=0);
    index     = tls_get_monitor(tls_index);
    HPROF_ASSERT(index!=0);
    info      = get_info(index);
    info->contended_time += tls_monitor_stop_timer(tls_index);
    info->num_hits++;
    tls_set_monitor(tls_index, 0);
}

void
monitor_wait_event(JNIEnv *env, jthread thread, jobject object, jlong timeout)
{
    TlsIndex     tls_index;
    MonitorKey  *pkey;
    MonitorIndex index;
    TraceIndex   trace_index;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(object!=NULL);
    HPROF_ASSERT(thread!=NULL);

    tls_index =  tls_find_or_create(env, thread);
    HPROF_ASSERT(tls_index!=0);
    HPROF_ASSERT(tls_get_monitor(tls_index)==0);
    trace_index = get_trace(tls_index, env);
    index = find_or_create_entry(env, trace_index, object);
    pkey = get_pkey(index);
    tls_monitor_start_timer(tls_index);
    tls_set_monitor(tls_index, index);

    rawMonitorEnter(gdata->data_access_lock); {
        io_write_monitor_wait(string_get(pkey->sig_index), timeout,
                            tls_get_thread_serial_number(tls_index));
    } rawMonitorExit(gdata->data_access_lock);
}

void
monitor_waited_event(JNIEnv *env, jthread thread,
                                jobject object, jboolean timed_out)
{
    TlsIndex     tls_index;
    MonitorIndex index;
    jlong        time_waited;

    tls_index =  tls_find_or_create(env, thread);
    HPROF_ASSERT(tls_index!=0);
    time_waited = tls_monitor_stop_timer(tls_index);
    index = tls_get_monitor(tls_index);

    if ( index ==0 ) {
        /* As best as I can tell, on Solaris X86 (not SPARC) I sometimes
         *    get a "waited" event on a thread that I have never seen before
         *    at all, so how did I get a WAITED event? Perhaps when I
         *    did the VM_INIT handling, a thread I've never seen had already
         *    done the WAIT (which I never saw?), and now I see this thread
         *    for the first time, and also as it finishes it's WAIT?
         *    Only happening on faster processors?
         */
        tls_set_monitor(tls_index, 0);
        return;
    }
    HPROF_ASSERT(index!=0);
    tls_set_monitor(tls_index, 0);
    if (object == NULL) {
        rawMonitorEnter(gdata->data_access_lock); {
            io_write_monitor_sleep(time_waited,
                        tls_get_thread_serial_number(tls_index));
        } rawMonitorExit(gdata->data_access_lock);
    } else {
        MonitorKey *pkey;

        pkey = get_pkey(index);
        rawMonitorEnter(gdata->data_access_lock); {
            io_write_monitor_waited(string_get(pkey->sig_index), time_waited,
                tls_get_thread_serial_number(tls_index));
        } rawMonitorExit(gdata->data_access_lock);
    }
}
