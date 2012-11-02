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


/* Trace table. */

/*
 * A trace is an optional thread serial number plus N frames.
 *
 * The thread serial number is added to the key only if the user asks for
 *    threads in traces, which will cause many more traces to be created.
 *    Without it all threads share the traces.
 *
 * This is a variable length Key, depending on the number of frames.
 *   The frames are FrameIndex values into the frame table.
 *
 * It is important that the thread serial number is used and not the
 *    TlsIndex, threads come and go, and TlsIndex values are re-used
 *    but the thread serial number is unique per thread.
 *
 * The cpu=times and cpu=samples dumps rely heavily on traces, the trace
 *   dump preceeds the cpu information and uses the trace information.
 *   Depending on the cpu= request, different sorts are applied to the
 *   traces that are dumped.
 *
 */

#include "hprof.h"

typedef struct TraceKey {
    SerialNumber thread_serial_num; /* Thread serial number */
    short        n_frames;          /* Number of frames that follow. */
    jvmtiPhase   phase : 8;         /* Makes some traces unique */
    FrameIndex   frames[1];         /* Variable length */
} TraceKey;

typedef struct TraceInfo {
    SerialNumber serial_num;        /* Trace serial number */
    jint         num_hits;          /* Number of hits this trace has */
    jlong        total_cost;        /* Total cost associated with trace */
    jlong        self_cost;         /* Total cost without children cost */
    jint         status;            /* Status of dump of trace */
} TraceInfo;

typedef struct IterateInfo {
    TraceIndex* traces;
    int         count;
    jlong       grand_total_cost;
} IterateInfo;

/* Private internal functions. */

static TraceKey*
get_pkey(TraceIndex index)
{
    void *      pkey;
    int         key_len;

    table_get_key(gdata->trace_table, index, &pkey, &key_len);
    HPROF_ASSERT(pkey!=NULL);
    HPROF_ASSERT(key_len>=(int)sizeof(TraceKey));
    HPROF_ASSERT(((TraceKey*)pkey)->n_frames<=1?key_len==(int)sizeof(TraceKey) :
             key_len==(int)sizeof(TraceKey)+
                      (int)sizeof(FrameIndex)*(((TraceKey*)pkey)->n_frames-1));
    return (TraceKey*)pkey;
}

static TraceInfo *
get_info(TraceIndex index)
{
    TraceInfo *         info;

    info        = (TraceInfo*)table_get_info(gdata->trace_table, index);
    return info;
}

static TraceIndex
find_or_create(SerialNumber thread_serial_num, jint n_frames,
            FrameIndex *frames, jvmtiPhase phase, TraceKey *trace_key_buffer)
{
    TraceInfo * info;
    TraceKey *  pkey;
    int         key_len;
    TraceIndex  index;
    jboolean    new_one;
    static TraceKey empty_key;

    HPROF_ASSERT(frames!=NULL);
    HPROF_ASSERT(trace_key_buffer!=NULL);
    key_len = (int)sizeof(TraceKey);
    if ( n_frames > 1 ) {
        key_len += (int)((n_frames-1)*(int)sizeof(FrameIndex));
    }
    pkey = trace_key_buffer;
    *pkey = empty_key;
    pkey->thread_serial_num = (gdata->thread_in_traces ? thread_serial_num : 0);
    pkey->n_frames = (short)n_frames;
    pkey->phase = phase;
    if ( n_frames > 0 ) {
        (void)memcpy(pkey->frames, frames, (n_frames*(int)sizeof(FrameIndex)));
    }

    new_one = JNI_FALSE;
    index = table_find_or_create_entry(gdata->trace_table,
                                pkey, key_len, &new_one, NULL);
    if ( new_one ) {
        info = get_info(index);
        info->serial_num = gdata->trace_serial_number_counter++;
    }
    return index;
}

static void
list_item(TableIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    TraceInfo *info;
    TraceKey         *key;
    int               i;

    HPROF_ASSERT(key_ptr!=NULL);
    HPROF_ASSERT(key_len>0);
    HPROF_ASSERT(info_ptr!=NULL);
    key = (TraceKey*)key_ptr;
    info = (TraceInfo *)info_ptr;

    debug_message( "Trace 0x%08x: SN=%u, threadSN=%u, n_frames=%d, frames=(",
             index,
             info->serial_num,
             key->thread_serial_num,
             key->n_frames);
    for ( i = 0 ; i < key->n_frames ; i++ ) {
        debug_message( "0x%08x, ", key->frames[i]);
    }
    debug_message( "), traceSN=%u, num_hits=%d, self_cost=(%d,%d), "
                        "total_cost=(%d,%d), status=0x%08x\n",
                        info->serial_num,
                        info->num_hits,
                        jlong_high(info->self_cost),
                        jlong_low(info->self_cost),
                        jlong_high(info->total_cost),
                        jlong_low(info->total_cost),
                        info->status);
}

static void
clear_cost(TableIndex i, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    TraceInfo *info;

    HPROF_ASSERT(key_ptr!=NULL);
    HPROF_ASSERT(key_len>0);
    HPROF_ASSERT(info_ptr!=NULL);
    info = (TraceInfo *)info_ptr;
    info->num_hits = 0;
    info->total_cost = 0;
    info->self_cost = 0;
}

/* Get the names for a frame in order to dump it. */
static void
get_frame_details(JNIEnv *env, FrameIndex frame_index,
                SerialNumber *frame_serial_num, char **pcsig, ClassIndex *pcnum,
                char **pmname, char **pmsig, char **psname, jint *plineno)
{
    jmethodID method;
    jlocation location;
    jint      lineno;

    HPROF_ASSERT(frame_index!=0);
    *pmname = NULL;
    *pmsig = NULL;
    *pcsig = NULL;
    if ( psname != NULL ) {
        *psname = NULL;
    }
    if ( plineno != NULL ) {
        *plineno = -1;
    }
    if ( pcnum != NULL ) {
        *pcnum = 0;
    }
    frame_get_location(frame_index, frame_serial_num, &method, &location, &lineno);
    if ( plineno != NULL ) {
        *plineno = lineno;
    }
    WITH_LOCAL_REFS(env, 1) {
        jclass klass;

        getMethodClass(method, &klass);
        getClassSignature(klass, pcsig, NULL);
        if ( pcnum != NULL ) {
            LoaderIndex loader_index;
            jobject     loader;

            loader = getClassLoader(klass);
            loader_index = loader_find_or_create(env, loader);
            *pcnum = class_find_or_create(*pcsig, loader_index);
             (void)class_new_classref(env, *pcnum, klass);
        }
        if ( psname != NULL ) {
            getSourceFileName(klass, psname);
        }
    } END_WITH_LOCAL_REFS;
    getMethodName(method, pmname, pmsig);
}

/* Write out a stack trace.  */
static void
output_trace(TableIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    TraceKey *key;
    TraceInfo *info;
    SerialNumber serial_num;
    SerialNumber thread_serial_num;
    jint n_frames;
    JNIEnv *env;
    int i;
    char *phase_str;
    struct FrameNames {
        SerialNumber serial_num;
        char * sname;
        char * csig;
        char * mname;
        int    lineno;
    } *finfo;

    info = (TraceInfo*)info_ptr;
    if ( info->status != 0 ) {
        return;
    }

    env = (JNIEnv*)arg;

    key = (TraceKey*)key_ptr;
    thread_serial_num = key->thread_serial_num;
    serial_num = info->serial_num;
    info->status = 1;
    finfo = NULL;

    n_frames = (jint)key->n_frames;
    if ( n_frames > 0 ) {
        finfo = (struct FrameNames *)HPROF_MALLOC(n_frames*(int)sizeof(struct FrameNames));

        /* Write frames, but save information for trace later */
        for (i = 0; i < n_frames; i++) {
            FrameIndex frame_index;
            char *msig;
            ClassIndex cnum;

            frame_index = key->frames[i];
            get_frame_details(env, frame_index, &finfo[i].serial_num,
                        &finfo[i].csig, &cnum,
                        &finfo[i].mname, &msig, &finfo[i].sname, &finfo[i].lineno);

            if (frame_get_status(frame_index) == 0) {
                io_write_frame(frame_index, finfo[i].serial_num,
                               finfo[i].mname, msig,
                               finfo[i].sname, class_get_serial_number(cnum),
                               finfo[i].lineno);
                frame_set_status(frame_index, 1);
            }
            jvmtiDeallocate(msig);
        }
    }

    /* Find phase string */
    if ( key->phase == JVMTI_PHASE_LIVE ) {
        phase_str = NULL; /* Normal trace, no phase annotation */
    } else {
        phase_str =  phaseString(key->phase);
    }

    io_write_trace_header(serial_num, thread_serial_num, n_frames, phase_str);

    for (i = 0; i < n_frames; i++) {
        io_write_trace_elem(serial_num, key->frames[i], finfo[i].serial_num,
                            finfo[i].csig,
                            finfo[i].mname, finfo[i].sname, finfo[i].lineno);
        jvmtiDeallocate(finfo[i].csig);
        jvmtiDeallocate(finfo[i].mname);
        jvmtiDeallocate(finfo[i].sname);
    }

    io_write_trace_footer(serial_num, thread_serial_num, n_frames);

    if ( finfo != NULL ) {
        HPROF_FREE(finfo);
    }
}

/* Output a specific list of traces. */
static void
output_list(JNIEnv *env, TraceIndex *list, jint count)
{
    rawMonitorEnter(gdata->data_access_lock); {
        int i;

        for ( i = 0; i < count ; i++ ) {
            TraceIndex index;
            TraceInfo  *info;
            void *      pkey;
            int         key_len;

            index = list[i];
            table_get_key(gdata->trace_table, index, &pkey, &key_len);
            info = get_info(index);
            output_trace(index, pkey, key_len, info, (void*)env);
        }
    } rawMonitorExit(gdata->data_access_lock);
}

static void
collect_iterator(TableIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    TraceInfo *info;
    IterateInfo      *iterate;

    HPROF_ASSERT(key_ptr!=NULL);
    HPROF_ASSERT(key_len>0);
    HPROF_ASSERT(arg!=NULL);
    HPROF_ASSERT(info_ptr!=NULL);
    iterate = (IterateInfo *)arg;
    info = (TraceInfo *)info_ptr;
    iterate->traces[iterate->count++] = index;
    iterate->grand_total_cost += info->self_cost;
}

static int
qsort_compare_cost(const void *p_trace1, const void *p_trace2)
{
    TraceIndex          trace1;
    TraceIndex          trace2;
    TraceInfo * info1;
    TraceInfo * info2;

    HPROF_ASSERT(p_trace1!=NULL);
    HPROF_ASSERT(p_trace2!=NULL);
    trace1 = *(TraceIndex *)p_trace1;
    trace2 = *(TraceIndex *)p_trace2;
    info1 = get_info(trace1);
    info2 = get_info(trace2);
    /*LINTED*/
    return (int)(info2->self_cost - info1->self_cost);
}

static int
qsort_compare_num_hits(const void *p_trace1, const void *p_trace2)
{
    TraceIndex          trace1;
    TraceIndex          trace2;
    TraceInfo * info1;
    TraceInfo * info2;

    HPROF_ASSERT(p_trace1!=NULL);
    HPROF_ASSERT(p_trace2!=NULL);
    trace1 = *(TraceIndex *)p_trace1;
    trace2 = *(TraceIndex *)p_trace2;
    info1 = get_info(trace1);
    info2 = get_info(trace2);
    return info2->num_hits - info1->num_hits;
}

/* External interfaces. */

void
trace_init(void)
{
    gdata->trace_table = table_initialize("Trace",
                            256, 256, 511, (int)sizeof(TraceInfo));
}

void
trace_list(void)
{
    debug_message(
        "--------------------- Trace Table ------------------------\n");
    table_walk_items(gdata->trace_table, &list_item, NULL);
    debug_message(
        "----------------------------------------------------------\n");
}

void
trace_cleanup(void)
{
    table_cleanup(gdata->trace_table, NULL, NULL);
    gdata->trace_table = NULL;
}

SerialNumber
trace_get_serial_number(TraceIndex index)
{
    TraceInfo *info;

    if ( index == 0 ) {
        return 0;
    }
    info = get_info(index);
    return info->serial_num;
}

void
trace_increment_cost(TraceIndex index, jint num_hits, jlong self_cost, jlong total_cost)
{
    TraceInfo *info;

    table_lock_enter(gdata->trace_table); {
        info              = get_info(index);
        info->num_hits   += num_hits;
        info->self_cost  += self_cost;
        info->total_cost += total_cost;
    } table_lock_exit(gdata->trace_table);
}

TraceIndex
trace_find_or_create(SerialNumber thread_serial_num, jint n_frames, FrameIndex *frames, jvmtiFrameInfo *jframes_buffer)
{
    return find_or_create(thread_serial_num, n_frames, frames, getPhase(),
                                (TraceKey*)jframes_buffer);
}

/* We may need to ask for more frames than the user asked for */
static int
get_real_depth(int depth, jboolean skip_init)
{
    int extra_frames;

    extra_frames = 0;
    /* This is only needed if we are doing BCI */
    if ( gdata->bci && depth > 0 ) {
        /* Account for Java and native Tracker methods */
        extra_frames = 2;
        if ( skip_init ) {
            /* Also allow for ignoring the java.lang.Object.<init> method */
            extra_frames += 1;
        }
    }
    return depth + extra_frames;
}

/* Fill in FrameIndex array from jvmtiFrameInfo array, return n_frames */
static int
fill_frame_buffer(int depth, int real_depth,
                 int frame_count, jboolean skip_init,
                 jvmtiFrameInfo *jframes_buffer, FrameIndex *frames_buffer)
{
    int  n_frames;
    jint topframe;

    /* If real_depth is 0, just return 0 */
    if ( real_depth == 0 ) {
        return 0;
    }

    /* Assume top frame index is 0 for now */
    topframe = 0;

    /* Possible top frames belong to the hprof Tracker class, remove them */
    if ( gdata->bci ) {
        while ( ( ( frame_count - topframe ) > 0 ) &&
                ( topframe < (real_depth-depth) ) &&
                ( tracker_method(jframes_buffer[topframe].method) ||
                  ( skip_init
                    && jframes_buffer[topframe].method==gdata->object_init_method ) )
             ) {
            topframe++;
        }
    }

    /* Adjust count to match depth request */
    if ( ( frame_count - topframe ) > depth ) {
        frame_count =  depth + topframe;
    }

    /* The actual frame count we will process */
    n_frames = frame_count - topframe;
    if ( n_frames > 0 ) {
        int i;

        for (i = 0; i < n_frames; i++) {
            jmethodID method;
            jlocation location;

            method = jframes_buffer[i+topframe].method;
            location = jframes_buffer[i+topframe].location;
            frames_buffer[i] = frame_find_or_create(method, location);
        }
    }
    return n_frames;
}

/* Get the trace for the supplied thread */
TraceIndex
trace_get_current(jthread thread, SerialNumber thread_serial_num,
                        int depth, jboolean skip_init,
                        FrameIndex *frames_buffer,
                        jvmtiFrameInfo *jframes_buffer)
{
    TraceIndex index;
    jint       frame_count;
    int        real_depth;
    int        n_frames;

    HPROF_ASSERT(thread!=NULL);
    HPROF_ASSERT(frames_buffer!=NULL);
    HPROF_ASSERT(jframes_buffer!=NULL);

    /* We may need to ask for more frames than the user asked for */
    real_depth = get_real_depth(depth, skip_init);

    /* Get the stack trace for this one thread */
    frame_count = 0;
    if ( real_depth > 0 ) {
        getStackTrace(thread, jframes_buffer, real_depth, &frame_count);
    }

    /* Create FrameIndex's */
    n_frames = fill_frame_buffer(depth, real_depth, frame_count, skip_init,
                                 jframes_buffer, frames_buffer);

    /* Lookup or create new TraceIndex */
    index = find_or_create(thread_serial_num, n_frames, frames_buffer,
                getPhase(), (TraceKey*)jframes_buffer);
    return index;
}

/* Get traces for all threads in list (traces[i]==0 if thread not running) */
void
trace_get_all_current(jint thread_count, jthread *threads,
                      SerialNumber *thread_serial_nums,
                      int depth, jboolean skip_init,
                      TraceIndex *traces, jboolean always_care)
{
    jvmtiStackInfo *stack_info;
    int             nbytes;
    int             real_depth;
    int             i;
    FrameIndex     *frames_buffer;
    TraceKey       *trace_key_buffer;
    jvmtiPhase      phase;

    HPROF_ASSERT(threads!=NULL);
    HPROF_ASSERT(thread_serial_nums!=NULL);
    HPROF_ASSERT(traces!=NULL);
    HPROF_ASSERT(thread_count > 0);

    /* Find out what the phase is for all these traces */
    phase = getPhase();

    /* We may need to ask for more frames than the user asked for */
    real_depth = get_real_depth(depth, skip_init);

    /* Get the stack traces for all the threads */
    getThreadListStackTraces(thread_count, threads, real_depth, &stack_info);

    /* Allocate a frames_buffer and trace key buffer */
    nbytes = (int)sizeof(FrameIndex)*real_depth;
    frames_buffer = (FrameIndex*)HPROF_MALLOC(nbytes);
    nbytes += (int)sizeof(TraceKey);
    trace_key_buffer = (TraceKey*)HPROF_MALLOC(nbytes);

    /* Loop over the stack traces we have for these 'thread_count' threads */
    for ( i = 0 ; i < thread_count ; i++ ) {
        int n_frames;

        /* Assume 0 at first (no trace) */
        traces[i] = 0;

        /* If thread has frames, is runnable, and isn't suspended, we care */
        if ( always_care ||
             ( stack_info[i].frame_count > 0
               && (stack_info[i].state & JVMTI_THREAD_STATE_RUNNABLE)!=0
               && (stack_info[i].state & JVMTI_THREAD_STATE_SUSPENDED)==0
               && (stack_info[i].state & JVMTI_THREAD_STATE_INTERRUPTED)==0 )
            ) {

            /* Create FrameIndex's */
            n_frames = fill_frame_buffer(depth, real_depth,
                                         stack_info[i].frame_count,
                                         skip_init,
                                         stack_info[i].frame_buffer,
                                         frames_buffer);

            /* Lookup or create new TraceIndex */
            traces[i] = find_or_create(thread_serial_nums[i],
                           n_frames, frames_buffer, phase, trace_key_buffer);
        }
    }

    /* Make sure we free the space */
    HPROF_FREE(frames_buffer);
    HPROF_FREE(trace_key_buffer);
    jvmtiDeallocate(stack_info);
}

/* Increment the trace costs for all the threads (for cpu=samples) */
void
trace_increment_all_sample_costs(jint thread_count, jthread *threads,
                      SerialNumber *thread_serial_nums,
                      int depth, jboolean skip_init)
{
    TraceIndex *traces;
    int         nbytes;

    HPROF_ASSERT(threads!=NULL);
    HPROF_ASSERT(thread_serial_nums!=NULL);
    HPROF_ASSERT(thread_count > 0);
    HPROF_ASSERT(depth >= 0);

    if ( depth == 0 ) {
        return;
    }

    /* Allocate a traces array */
    nbytes = (int)sizeof(TraceIndex)*thread_count;
    traces = (TraceIndex*)HPROF_MALLOC(nbytes);

    /* Get all the current traces for these threads */
    trace_get_all_current(thread_count, threads, thread_serial_nums,
                      depth, skip_init, traces, JNI_FALSE);

    /* Increment the cpu=samples cost on these traces */
    table_lock_enter(gdata->trace_table); {
        int i;

        for ( i = 0 ; i < thread_count ; i++ ) {
            /* Each trace gets a hit and an increment of it's total cost */
            if ( traces[i] != 0 ) {
                TraceInfo *info;

                info              = get_info(traces[i]);
                info->num_hits   += 1;
                info->self_cost  += (jlong)1;
                info->total_cost += (jlong)1;
            }
        }
    } table_lock_exit(gdata->trace_table);

    /* Free up the memory allocated */
    HPROF_FREE(traces);
}

void
trace_output_unmarked(JNIEnv *env)
{
    rawMonitorEnter(gdata->data_access_lock); {
        table_walk_items(gdata->trace_table, &output_trace, (void*)env);
    } rawMonitorExit(gdata->data_access_lock);
}

/* output info on the cost associated with traces  */
void
trace_output_cost(JNIEnv *env, double cutoff)
{
    IterateInfo iterate;
    int i, trace_table_size, n_items;
    double accum;
    int n_entries;

    rawMonitorEnter(gdata->data_access_lock); {

        n_entries = table_element_count(gdata->trace_table);
        iterate.traces = HPROF_MALLOC(n_entries*(int)sizeof(TraceIndex)+1);
        iterate.count = 0;
        iterate.grand_total_cost = 0;
        table_walk_items(gdata->trace_table, &collect_iterator, &iterate);

        trace_table_size = iterate.count;

        /* sort all the traces according to the cost */
        qsort(iterate.traces, trace_table_size, sizeof(TraceIndex),
                    &qsort_compare_cost);

        n_items = 0;
        for (i = 0; i < trace_table_size; i++) {
            TraceInfo *info;
            TraceIndex trace_index;
            double percent;

            trace_index = iterate.traces[i];
            info = get_info(trace_index);
            /* As soon as a trace with zero hits is seen, we need no others */
            if (info->num_hits == 0 ) {
                break;
            }
            percent = (double)info->self_cost / (double)iterate.grand_total_cost;
            if (percent < cutoff) {
                break;
            }
            n_items++;
        }

        /* Now write all trace we might refer to. */
        output_list(env, iterate.traces, n_items);

        io_write_cpu_samples_header(iterate.grand_total_cost, n_items);

        accum = 0;

        for (i = 0; i < n_items; i++) {
            SerialNumber frame_serial_num;
            TraceInfo *info;
            TraceKey *key;
            TraceIndex trace_index;
            double percent;
            char *csig;
            char *mname;
            char *msig;

            trace_index = iterate.traces[i];
            info = get_info(trace_index);
            key = get_pkey(trace_index);
            percent = ((double)info->self_cost / (double)iterate.grand_total_cost) * 100.0;
            accum += percent;

            csig = NULL;
            mname = NULL;
            msig  = NULL;

            if (key->n_frames > 0) {
                get_frame_details(env, key->frames[0], &frame_serial_num,
                        &csig, NULL, &mname, &msig, NULL, NULL);
            }

            io_write_cpu_samples_elem(i+1, percent, accum, info->num_hits,
                        (jint)info->self_cost, info->serial_num,
                        key->n_frames, csig, mname);

            jvmtiDeallocate(csig);
            jvmtiDeallocate(mname);
            jvmtiDeallocate(msig);
        }

        io_write_cpu_samples_footer();

        HPROF_FREE(iterate.traces);

    } rawMonitorExit(gdata->data_access_lock);

}

/* output the trace cost in old prof format */
void
trace_output_cost_in_prof_format(JNIEnv *env)
{
    IterateInfo iterate;
    int i, trace_table_size;
    int n_entries;

    rawMonitorEnter(gdata->data_access_lock); {

        n_entries = table_element_count(gdata->trace_table);
        iterate.traces = HPROF_MALLOC(n_entries*(int)sizeof(TraceIndex)+1);
        iterate.count = 0;
        iterate.grand_total_cost = 0;
        table_walk_items(gdata->trace_table, &collect_iterator, &iterate);

        trace_table_size = iterate.count;

        /* sort all the traces according to the number of hits */
        qsort(iterate.traces, trace_table_size, sizeof(TraceIndex),
                    &qsort_compare_num_hits);

        io_write_oldprof_header();

        for (i = 0; i < trace_table_size; i++) {
            SerialNumber frame_serial_num;
            TraceInfo *info;
            TraceKey *key;
            TraceIndex trace_index;
            int num_frames;
            int num_hits;
            char *csig_callee;
            char *mname_callee;
            char *msig_callee;
            char *csig_caller;
            char *mname_caller;
            char *msig_caller;

            trace_index = iterate.traces[i];
            key = get_pkey(trace_index);
            info = get_info(trace_index);
            num_hits = info->num_hits;

            if (num_hits == 0) {
                break;
            }

            csig_callee  = NULL;
            mname_callee = NULL;
            msig_callee  = NULL;
            csig_caller  = NULL;
            mname_caller = NULL;
            msig_caller  = NULL;

            num_frames = (int)key->n_frames;

            if (num_frames >= 1) {
                get_frame_details(env, key->frames[0], &frame_serial_num,
                        &csig_callee, NULL,
                        &mname_callee, &msig_callee, NULL, NULL);
            }

            if (num_frames > 1) {
                get_frame_details(env, key->frames[1], &frame_serial_num,
                        &csig_caller, NULL,
                        &mname_caller, &msig_caller, NULL, NULL);
            }

            io_write_oldprof_elem(info->num_hits, num_frames,
                                    csig_callee, mname_callee, msig_callee,
                                    csig_caller, mname_caller, msig_caller,
                                    (int)info->total_cost);

            jvmtiDeallocate(csig_callee);
            jvmtiDeallocate(mname_callee);
            jvmtiDeallocate(msig_callee);
            jvmtiDeallocate(csig_caller);
            jvmtiDeallocate(mname_caller);
            jvmtiDeallocate(msig_caller);
        }

        io_write_oldprof_footer();

        HPROF_FREE(iterate.traces);

    } rawMonitorExit(gdata->data_access_lock);
}

void
trace_clear_cost(void)
{
    table_walk_items(gdata->trace_table, &clear_cost, NULL);
}
