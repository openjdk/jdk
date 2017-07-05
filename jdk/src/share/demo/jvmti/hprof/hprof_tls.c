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

#include "hprof.h"

/* Thread Local Storage Table and method entry/exit handling. */

/*
 * The tls table items have a key of it's serial number, but may be
 *   searched via a walk of the table looking for a jthread match.
 *   This isn't a performance
 *   issue because the table index should normally be stored in the
 *   Thread Local Storage for the thread. The table is only searched
 *   when the jthread is seen before the Thread Local Storage is set
 *   (e.g. before VM_INIT or the ThreadStart).
 *   The key is only used when we need to lookup a tls table entry by
 *   way of it's serial number, which should be unique per thread.
 *
 * Each active thread that we have seen should have a unique TlsIndex
 *   which is an index into this table.
 *
 * For cpu=times, each table entry will have a stack to hold the method
 *   that have been called, effectively keeping an active stack trace
 *   for the thread. As each method exits, the statistics for the trace
 *   associated with the current stack contents is updated.
 *
 * For cpu=samples, each thread is checked to see if it's runnable,
 *   and not suspended, and has a stack associated with it, and then
 *   that stack trace is updated with an additional 'hit'.
 *
 * This file also contains the dump logic for owned monitors, and for
 *   threads.
 *
 */

/*
 * Initial number of stack elements to track per thread. This
 * value should be set to a reasonable guess as to the number of
 * methods deep a thread calls. This stack doubles in size for each
 * reallocation and does not shrink.
 */

#define INITIAL_THREAD_STACK_LIMIT 64

typedef struct StackElement {
    FrameIndex  frame_index;            /* Frame (method/location(-1)) */
    jmethodID   method;                 /* Method ID */
    jlong       method_start_time;      /* method start time */
    jlong       time_in_callees;        /* time in callees */
} StackElement;

typedef struct TlsInfo {
    jint            sample_status;      /* Thread status for cpu sampling */
    jboolean        agent_thread;       /* Is thread our own agent thread? */
    jthread         globalref;          /* Global reference for thread */
    Stack          *stack;              /* Stack of StackElements entry/exit */
    MonitorIndex    monitor_index;      /* last contended mon */
    jint            tracker_status;     /* If we are inside Tracker class */
    FrameIndex     *frames_buffer;      /* Buffer used to create TraceIndex */
    jvmtiFrameInfo *jframes_buffer;     /* Buffer used to create TraceIndex */
    int             buffer_depth;       /* Frames allowed in buffer */
    TraceIndex      last_trace;         /* Last trace for this thread */
    ObjectIndex     thread_object_index;/* If heap=dump */
    jlong           monitor_start_time; /* Start time for monitor */
    jint            in_heap_dump;       /* If we are an object in the dump */
} TlsInfo;

typedef struct SearchData {
    JNIEnv      *env;
    jthread      thread;
    TlsIndex     found;
} SearchData;

typedef struct IterateInfo {
    TlsIndex *          ptls_index;
    jthread  *          pthreads;
    jint                count;
} IterateInfo;

typedef struct ThreadList {
    jthread      *threads;
    SerialNumber *serial_nums;
    TlsInfo     **infos;
    jint          count;
    JNIEnv       *env;
} ThreadList;

typedef struct SampleData {
    ObjectIndex  thread_object_index;
    jint         sample_status;
} SampleData;

/* Private internal functions. */

static SerialNumber
get_key(TlsIndex index)
{
    SerialNumber *pkey;
    int           key_len;

    if ( index == 0 ) {
        return 0;
    }
    pkey    = NULL;
    key_len = 0;
    table_get_key(gdata->tls_table, index, (void**)&pkey, &key_len);
    HPROF_ASSERT(pkey!=NULL);
    HPROF_ASSERT(key_len==(int)sizeof(SerialNumber));
    return *pkey;
}

static TlsInfo *
get_info(TlsIndex index)
{
    return (TlsInfo*)table_get_info(gdata->tls_table, index);
}

static void
delete_globalref(JNIEnv *env, TlsInfo *info)
{
    jthread ref;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(info!=NULL);
    ref = info->globalref;
    info->globalref = NULL;
    if ( ref != NULL ) {
        deleteWeakGlobalReference(env, ref);
    }
}

static void
clean_info(TlsInfo *info)
{
    /* Free up any allocated space in this TlsInfo structure */
    if ( info->stack != NULL ) {
        stack_term(info->stack);
        info->stack = NULL;
    }
    if ( info->frames_buffer != NULL ) {
        HPROF_FREE(info->frames_buffer);
        info->frames_buffer = NULL;
    }
    if ( info->jframes_buffer != NULL ) {
        HPROF_FREE(info->jframes_buffer);
        info->jframes_buffer = NULL;
    }
}

static void
cleanup_item(TableIndex index, void *key_ptr, int key_len,
                        void *info_ptr, void *arg)
{
    TlsInfo *   info;

    info = (TlsInfo*)info_ptr;
    clean_info(info);
}

static void
delete_ref_item(TableIndex index, void *key_ptr, int key_len,
                        void *info_ptr, void *arg)
{
    delete_globalref((JNIEnv*)arg, (TlsInfo*)info_ptr);
}

static void
list_item(TableIndex index, void *key_ptr, int key_len,
                        void *info_ptr, void *arg)
{
    TlsInfo     *info;

    HPROF_ASSERT(info_ptr!=NULL);

    info        = (TlsInfo*)info_ptr;
    debug_message( "Tls 0x%08x: SN=%u, sample_status=%d, agent=%d, "
                          "thread=%p, monitor=0x%08x, "
                          "tracker_status=%d\n",
                index,
                *(SerialNumber*)key_ptr,
                info->sample_status,
                info->agent_thread,
                (void*)info->globalref,
                info->monitor_index,
                info->tracker_status);
}

static void
search_item(TableIndex index, void *key_ptr, int key_len,
                        void *info_ptr, void *arg)
{
    TlsInfo     *info;
    SearchData  *data;
    jobject      lref;

    HPROF_ASSERT(info_ptr!=NULL);
    HPROF_ASSERT(arg!=NULL);
    info        = (TlsInfo*)info_ptr;
    data        = (SearchData*)arg;
    lref        = newLocalReference(data->env, info->globalref);
    if ( lref != NULL ) {
        if ( isSameObject(data->env, data->thread, lref) ) {
            HPROF_ASSERT(data->found==0); /* Did we find more than one? */
            data->found = index;
        }
        deleteLocalReference(data->env, lref);
    }
}

static TlsIndex
search(JNIEnv *env, jthread thread)
{
    SearchData  data;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(thread!=NULL);

    data.env = env;
    data.thread = thread;
    data.found = 0;
    table_walk_items(gdata->tls_table, &search_item, (void*)&data);
    return data.found;
}

static void
garbage_collect_item(TableIndex index, void *key_ptr, int key_len,
                        void *info_ptr, void *arg)
{
    TlsInfo     *info;
    JNIEnv      *env;
    jobject      lref;

    HPROF_ASSERT(info_ptr!=NULL);
    HPROF_ASSERT(arg!=NULL);
    info        = (TlsInfo*)info_ptr;
    env         = (JNIEnv*)arg;
    lref        = newLocalReference(env, info->globalref);
    if ( lref == NULL ) {
        delete_globalref(env, info);
        clean_info(info);
        table_free_entry(gdata->tls_table, index);
    } else {
        deleteLocalReference(env, lref);
    }
}

void
tls_garbage_collect(JNIEnv *env)
{
    HPROF_ASSERT(env!=NULL);
    rawMonitorEnter(gdata->data_access_lock); {
        table_walk_items(gdata->tls_table, &garbage_collect_item, (void*)env);
    } rawMonitorExit(gdata->data_access_lock);
}

static void
sum_sample_status_item(TableIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    TlsInfo     *info;

    HPROF_ASSERT(info_ptr!=NULL);
    info                = (TlsInfo*)info_ptr;
    if ( !info->agent_thread ) {
        (*(jint*)arg)      += info->sample_status;
    }
}

static void
setup_trace_buffers(TlsInfo *info, int max_depth)
{
    int nbytes;
    int max_frames;

    if ( info->frames_buffer != NULL && info->buffer_depth >= max_depth ) {
        return;
    }
    if ( info->frames_buffer != NULL ) {
        HPROF_FREE(info->frames_buffer);
    }
    if ( info->jframes_buffer != NULL ) {
        HPROF_FREE(info->jframes_buffer);
    }
    info->buffer_depth      = max_depth;
    max_frames              = max_depth + 4; /* Allow for BCI & <init> */
    nbytes                  = (int)sizeof(FrameIndex)*(max_frames+1);
    info->frames_buffer     = HPROF_MALLOC(nbytes);
    nbytes                  = (int)sizeof(jvmtiFrameInfo)*(max_frames+1);
    info->jframes_buffer    = HPROF_MALLOC(nbytes);
}

static TraceIndex
get_trace(jthread thread, SerialNumber thread_serial_num,
                int depth, jboolean skip_init,
                FrameIndex *frames_buffer, jvmtiFrameInfo *jframes_buffer)
{
    TraceIndex trace_index;

    trace_index = gdata->system_trace_index;
    if ( thread != NULL ) {
        trace_index = trace_get_current(thread,
                        thread_serial_num, depth, skip_init,
                        frames_buffer, jframes_buffer);
    }
    return trace_index;
}

/* Find thread with certain object index */
static void
sample_setter(TableIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    TlsInfo *info;

    HPROF_ASSERT(info_ptr!=NULL);

    info  = (TlsInfo*)info_ptr;
    if ( info->globalref != NULL && !info->agent_thread ) {
        SampleData *data;

        data   = (SampleData*)arg;
        if ( data->thread_object_index == info->thread_object_index ) {
            info->sample_status = data->sample_status;
        }
    }
}

/* Get various lists on known threads */
static void
get_thread_list(TableIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    SerialNumber thread_serial_num;
    TlsInfo     *info;
    ThreadList  *list;
    jthread      thread;

    HPROF_ASSERT(key_ptr!=NULL);
    HPROF_ASSERT(info_ptr!=NULL);

    thread_serial_num = *(SerialNumber*)key_ptr;
    info              = (TlsInfo*)info_ptr;
    list              = (ThreadList*)arg;
    thread            = newLocalReference(list->env, info->globalref);
    if ( thread != NULL && info->sample_status != 0 && !info->agent_thread ) {
        if ( list->infos != NULL ) {
            list->infos[list->count] = info;
        }
        if ( list->serial_nums != NULL ) {
            list->serial_nums[list->count] = thread_serial_num;
        }
        list->threads[list->count] = thread;
        list->count++;
        /* Local reference gets freed by caller */
    } else {
        /* If we don't use the local reference, delete it now */
        if ( thread != NULL ) {
            deleteLocalReference(list->env, thread);
        }
    }
}

static void
adjust_stats(jlong total_time, jlong self_time, TraceIndex trace_index,
             StackElement *parent)
{
    if ( total_time > 0 && parent != NULL ) {  /* if a caller exists */
        parent->time_in_callees += total_time;
    }
    trace_increment_cost(trace_index, 1, self_time, total_time);
}

static void
push_method(Stack *stack, jlong method_start_time, jmethodID method)
{
    StackElement new_element;
    FrameIndex   frame_index;

    HPROF_ASSERT(method!=NULL);
    HPROF_ASSERT(stack!=NULL);

    frame_index                  = frame_find_or_create(method, -1);
    HPROF_ASSERT(frame_index != 0);
    new_element.frame_index      = frame_index;
    new_element.method           = method;
    new_element.method_start_time= method_start_time;
    new_element.time_in_callees  = (jlong)0;
    stack_push(stack, &new_element);
}

static Stack *
insure_method_on_stack(jthread thread, TlsInfo *info, jlong current_time,
                FrameIndex frame_index, jmethodID method)
{
    StackElement  element;
    void         *p;
    int           depth;
    int           count;
    int           fcount;
    int           i;
    Stack         *new_stack;
    Stack         *stack;

    stack = info->stack;

    HPROF_ASSERT(method!=NULL);

    /* If this method is on the stack, just return */
    depth   = stack_depth(stack);
    p = stack_top(stack);
    if ( p != NULL ) {
        element = *(StackElement*)p;
        if ( element.frame_index == frame_index ) {
            return stack;
        }
    }
    for ( i = 0 ; i < depth ; i++ ) {
        p = stack_element(stack, i);
        element = *(StackElement*)p;
        if ( element.frame_index == frame_index ) {
            return stack;
        }
    }

    /* It wasn't found, create a new stack */
    getFrameCount(thread, &count);
    if ( count <= 0 ) {
        HPROF_ERROR(JNI_FALSE, "no frames, method can't be on stack");
    }
    setup_trace_buffers(info, count);
    getStackTrace(thread, info->jframes_buffer, count, &fcount);
    HPROF_ASSERT(count==fcount);

    /* Create a new stack */
    new_stack = stack_init(INITIAL_THREAD_STACK_LIMIT,
                            INITIAL_THREAD_STACK_LIMIT,
                            (int)sizeof(StackElement));
    for ( i = count-1; i >= 0 ; i-- ) {
        push_method(new_stack, current_time, info->jframes_buffer[i].method);
    }
    if ( depth > 0 ) {
        for ( i = depth-1 ; i >= 0; i-- ) {
            stack_push(new_stack, stack_element(stack, i));
        }
    }
    stack_term(stack);
    return new_stack;
}

static void
pop_method(TlsIndex index, jlong current_time, jmethodID method, FrameIndex frame_index)
{
    SerialNumber  thread_serial_num;
    TlsInfo  *    info;
    StackElement  element;
    void         *p;
    int           depth;
    int           trace_depth;
    jlong         total_time;
    jlong         self_time;
    int           i;
    TraceIndex    trace_index;

    HPROF_ASSERT(method!=NULL);
    HPROF_ASSERT(frame_index!=0);

    thread_serial_num  = get_key(index);
    info               = get_info(index);
    HPROF_ASSERT(info!=NULL);
    HPROF_ASSERT(info->stack!=NULL);
    depth   = stack_depth(info->stack);
    p = stack_pop(info->stack);
    if (p == NULL) {
        HPROF_ERROR(JNI_FALSE, "method return tracked, but stack is empty");
        return;
    }
    element = *(StackElement*)p;
    HPROF_ASSERT(element.frame_index!=0);

    /* The depth of frames we should keep track for reporting */
    if (gdata->prof_trace_depth > depth) {
        trace_depth = depth;
    } else {
        trace_depth = gdata->prof_trace_depth;
    }

    /* Create a trace entry */
    HPROF_ASSERT(info->frames_buffer!=NULL);
    HPROF_ASSERT(info->jframes_buffer!=NULL);
    setup_trace_buffers(info, trace_depth);
    info->frames_buffer[0] = element.frame_index;
    for (i = 1; i < trace_depth; i++) {
        StackElement e;

        e = *(StackElement*)stack_element(info->stack, (depth - i) - 1);
        info->frames_buffer[i] = e.frame_index;
        HPROF_ASSERT(e.frame_index!=0);
    }
    trace_index = trace_find_or_create(thread_serial_num,
                    trace_depth, info->frames_buffer, info->jframes_buffer);

    /* Calculate time spent */
    total_time = current_time - element.method_start_time;
    if ( total_time < 0 ) {
        total_time = 0;
        self_time = 0;
    } else {
        self_time = total_time - element.time_in_callees;
    }

    /* Update stats */
    p = stack_top(info->stack);
    if ( p != NULL ) {
        adjust_stats(total_time, self_time, trace_index, (StackElement*)p);
    } else {
        adjust_stats(total_time, self_time, trace_index, NULL);
    }
}

static void
dump_thread_state(TlsIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    SerialNumber thread_serial_num;
    TlsInfo     *info;
    jthread      thread;
    JNIEnv      *env;

    HPROF_ASSERT(key_ptr!=NULL);
    HPROF_ASSERT(info_ptr!=NULL);
    env                  = (JNIEnv*)arg;
    thread_serial_num    = *(SerialNumber*)key_ptr;
    info                 = (TlsInfo*)info_ptr;
    thread               = newLocalReference(env, info->globalref);
    if ( thread != NULL ) {
        jint         threadState;
        SerialNumber trace_serial_num;

        getThreadState(thread, &threadState);
        /* A 0 trace at this time means the thread is in unknown territory.
         *   The trace serial number MUST be a valid serial number, so we use
         *   the system trace (empty) just so it has a valid trace.
         */
        if ( info->last_trace == 0 ) {
            trace_serial_num = trace_get_serial_number(gdata->system_trace_index);
        } else {
            trace_serial_num = trace_get_serial_number(info->last_trace);
        }
        io_write_monitor_dump_thread_state(thread_serial_num,
                       trace_serial_num, threadState);
        deleteLocalReference(env, thread);
    }
}

static SerialNumber
get_serial_number(JNIEnv *env, jthread thread)
{
    TlsIndex     index;

    if ( thread == NULL ) {
        return gdata->unknown_thread_serial_num;
    }
    HPROF_ASSERT(env!=NULL);
    index = tls_find_or_create(env, thread);
    return get_key(index);
}

static void
dump_monitor_state(TlsIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    TlsInfo *info;
    jthread  thread;
    JNIEnv  *env;

    HPROF_ASSERT(info_ptr!=NULL);
    env = (JNIEnv*)arg;
    info = (TlsInfo*)info_ptr;
    thread = newLocalReference(env, info->globalref);
    if ( thread != NULL ) {
        jobject *objects;
        jint     ocount;
        int      i;

        getOwnedMonitorInfo(thread, &objects, &ocount);
        if ( ocount > 0 ) {
            for ( i = 0 ; i < ocount ; i++ ) {
                jvmtiMonitorUsage usage;
                SerialNumber *waiter_nums;
                SerialNumber *notify_waiter_nums;
                int           t;
                char *        sig;

                WITH_LOCAL_REFS(env, 1) {
                    jclass clazz;

                    clazz = getObjectClass(env, objects[i]);
                    getClassSignature(clazz, &sig, NULL);
                } END_WITH_LOCAL_REFS;

                getObjectMonitorUsage(objects[i], &usage);
                waiter_nums = HPROF_MALLOC(usage.waiter_count*
                                        (int)sizeof(SerialNumber)+1);
                for ( t = 0 ; t < usage.waiter_count ; t++ ) {
                    waiter_nums[t] =
                        get_serial_number(env, usage.waiters[t]);
                }
                notify_waiter_nums = HPROF_MALLOC(usage.notify_waiter_count*
                                        (int)sizeof(SerialNumber)+1);
                for ( t = 0 ; t < usage.notify_waiter_count ; t++ ) {
                    notify_waiter_nums[t] =
                        get_serial_number(env, usage.notify_waiters[t]);
                }
                io_write_monitor_dump_state(sig,
                       get_serial_number(env, usage.owner),
                       usage.entry_count,
                       waiter_nums, usage.waiter_count,
                       notify_waiter_nums, usage.notify_waiter_count);
                jvmtiDeallocate(sig);
                jvmtiDeallocate(usage.waiters);
                jvmtiDeallocate(usage.notify_waiters);
                HPROF_FREE(waiter_nums);
                HPROF_FREE(notify_waiter_nums);
            }
        }
        jvmtiDeallocate(objects);
        deleteLocalReference(env, thread);
    }
}

static jlong
monitor_time(void)
{
    jlong mtime;

    mtime = md_get_timemillis(); /* gettimeofday() */
    return mtime;
}

static jlong
method_time(void)
{
    jlong method_time;

    method_time = md_get_thread_cpu_timemillis(); /* thread CPU time */
    return method_time;
}

/* External interfaces */

TlsIndex
tls_find_or_create(JNIEnv *env, jthread thread)
{
    SerialNumber    thread_serial_num;
    static TlsInfo  empty_info;
    TlsInfo         info;
    TlsIndex        index;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(thread!=NULL);

    /*LINTED*/
    index = (TlsIndex)(ptrdiff_t)getThreadLocalStorage(thread);
    if ( index != 0 ) {
        HPROF_ASSERT(isSameObject(env, thread, get_info(index)->globalref));
        return index;
    }
    index = search(env, thread);
    if ( index != 0 ) {
        setThreadLocalStorage(thread, (void*)(ptrdiff_t)index);
        return index;
    }
    thread_serial_num      = gdata->thread_serial_number_counter++;
    info                   = empty_info;
    info.monitor_index     = 0;
    info.sample_status     = 1;
    info.agent_thread      = JNI_FALSE;
    info.stack             = stack_init(INITIAL_THREAD_STACK_LIMIT,
                                INITIAL_THREAD_STACK_LIMIT,
                                (int)sizeof(StackElement));
    setup_trace_buffers(&info, gdata->max_trace_depth);
    info.globalref = newWeakGlobalReference(env, thread);
    index = table_create_entry(gdata->tls_table, &thread_serial_num, (int)sizeof(SerialNumber), (void*)&info);
    setThreadLocalStorage(thread, (void*)(ptrdiff_t)index);
    HPROF_ASSERT(search(env,thread)==index);
    return index;
}

/* Mark a new or existing entry as being an agent thread */
void
tls_agent_thread(JNIEnv *env, jthread thread)
{
    TlsIndex  index;
    TlsInfo  *info;

    index              = tls_find_or_create(env, thread);
    info               = get_info(index);
    info->agent_thread = JNI_TRUE;
}

void
tls_init(void)
{
    gdata->tls_table = table_initialize("TLS",
                            16, 16, 16, (int)sizeof(TlsInfo));
}

void
tls_list(void)
{
    debug_message(
        "--------------------- TLS Table ------------------------\n");
    table_walk_items(gdata->tls_table, &list_item, NULL);
    debug_message(
        "----------------------------------------------------------\n");
}

jint
tls_sum_sample_status(void)
{
    jint sample_status_total;

    sample_status_total = 0;
    table_walk_items(gdata->tls_table, &sum_sample_status_item, (void*)&sample_status_total);
    return sample_status_total;
}

void
tls_set_sample_status(ObjectIndex object_index, jint sample_status)
{
    SampleData  data;

    data.thread_object_index = object_index;
    data.sample_status       = sample_status;
    table_walk_items(gdata->tls_table, &sample_setter, (void*)&data);
}

jint
tls_get_tracker_status(JNIEnv *env, jthread thread, jboolean skip_init,
        jint **ppstatus, TlsIndex* pindex,
        SerialNumber *pthread_serial_num, TraceIndex *ptrace_index)
{
    TlsInfo      *info;
    TlsIndex      index;
    SerialNumber  thread_serial_num;
    jint          status;

    index             = tls_find_or_create(env, thread);
    info              = get_info(index);
    *ppstatus         = &(info->tracker_status);
    status            = **ppstatus;
    thread_serial_num = get_key(index);

    if ( pindex != NULL ) {
        *pindex = index;
    }
    if ( status != 0 ) {
        return status;
    }
    if ( ptrace_index != NULL ) {
        setup_trace_buffers(info, gdata->max_trace_depth);
        *ptrace_index = get_trace(thread, thread_serial_num,
                            gdata->max_trace_depth, skip_init,
                            info->frames_buffer, info->jframes_buffer);
    }
    if ( pthread_serial_num != NULL ) {
        *pthread_serial_num = thread_serial_num;
    }
    return status;
}

MonitorIndex
tls_get_monitor(TlsIndex index)
{
    TlsInfo  *info;

    info = get_info(index);
    return info->monitor_index;
}

void
tls_set_thread_object_index(TlsIndex index, ObjectIndex thread_object_index)
{
    TlsInfo  *info;

    info = get_info(index);
    info->thread_object_index = thread_object_index;
}

SerialNumber
tls_get_thread_serial_number(TlsIndex index)
{
    return get_key(index);
}

void
tls_set_monitor(TlsIndex index, MonitorIndex monitor_index)
{
    TlsInfo  *info;

    info = get_info(index);
    info->monitor_index = monitor_index;
}

void
tls_cleanup(void)
{
    table_cleanup(gdata->tls_table, &cleanup_item, NULL);
    gdata->tls_table = NULL;
}

void
tls_delete_global_references(JNIEnv *env)
{
    table_walk_items(gdata->tls_table, &delete_ref_item, (void*)env);
}

void
tls_thread_ended(JNIEnv *env, TlsIndex index)
{
    HPROF_ASSERT(env!=NULL);

    /* Sample thread stack for last time, do NOT free the entry yet. */
    table_lock_enter(gdata->tls_table); {
        SerialNumber thread_serial_num;
        TlsInfo     *info;
        jthread      thread;

        thread_serial_num = get_key(index);
        info              = get_info(index);
        thread            = newLocalReference(env, info->globalref);
        if (gdata->heap_dump && thread!=NULL) {
            setup_trace_buffers(info, gdata->max_trace_depth);
            info->last_trace = get_trace(thread, thread_serial_num,
                                    gdata->max_trace_depth, JNI_FALSE,
                                    info->frames_buffer, info->jframes_buffer);
        }
        if ( thread != NULL ) {
            deleteLocalReference(env, thread);
        }
    } table_lock_exit(gdata->tls_table);

}

/* Sample ALL threads and update the trace costs */
void
tls_sample_all_threads(JNIEnv *env)
{
    ThreadList    list;
    jthread      *threads;
    SerialNumber *serial_nums;

    table_lock_enter(gdata->tls_table); {
        int           max_count;
        int           nbytes;
        int           i;

        /* Get buffers to hold thread list and serial number list */
        max_count   = table_element_count(gdata->tls_table);
        nbytes      = (int)sizeof(jthread)*max_count;
        threads     = (jthread*)HPROF_MALLOC(nbytes);
        nbytes      = (int)sizeof(SerialNumber)*max_count;
        serial_nums = (SerialNumber*)HPROF_MALLOC(nbytes);

        /* Get list of threads and serial numbers */
        list.threads     = threads;
        list.infos       = NULL;
        list.serial_nums = serial_nums;
        list.count       = 0;
        list.env         = env;
        table_walk_items(gdata->tls_table, &get_thread_list, (void*)&list);

        /* Increment the cost on the traces for these threads */
        trace_increment_all_sample_costs(list.count, threads, serial_nums,
                              gdata->max_trace_depth, JNI_FALSE);

        /* Loop over local refs and free them */
        for ( i = 0 ; i < list.count ; i++ ) {
            if ( threads[i] != NULL ) {
                deleteLocalReference(env, threads[i]);
            }
        }

    } table_lock_exit(gdata->tls_table);

    /* Free up allocated space */
    HPROF_FREE(threads);
    HPROF_FREE(serial_nums);

}

void
tls_push_method(TlsIndex index, jmethodID method)
{
    jlong    method_start_time;
    TlsInfo *info;

    HPROF_ASSERT(method!=NULL);
    info        = get_info(index);
    HPROF_ASSERT(info!=NULL);
    method_start_time  = method_time();
    HPROF_ASSERT(info->stack!=NULL);
    push_method(info->stack, method_start_time, method);
}

void
tls_pop_exception_catch(TlsIndex index, jthread thread, jmethodID method)
{
    TlsInfo      *info;
    StackElement  element;
    void         *p;
    FrameIndex    frame_index;
    jlong         current_time;

    HPROF_ASSERT(method!=NULL);
    frame_index = frame_find_or_create(method, -1);
    HPROF_ASSERT(frame_index != 0);

    info = get_info(index);

    HPROF_ASSERT(info!=NULL);
    HPROF_ASSERT(info->stack!=NULL);
    HPROF_ASSERT(frame_index!=0);
    current_time = method_time();
    info->stack = insure_method_on_stack(thread, info, current_time,
                        frame_index, method);
    p = stack_top(info->stack);
    if (p == NULL) {
        HPROF_ERROR(JNI_FALSE, "expection pop, nothing on stack");
        return;
    }
    element = *(StackElement*)p;
    HPROF_ASSERT(element.frame_index!=0);
    while ( element.frame_index != frame_index ) {
        pop_method(index, current_time, element.method, frame_index);
        p = stack_top(info->stack);
        if ( p == NULL ) {
            break;
        }
        element = *(StackElement*)p;
    }
    if (p == NULL) {
        HPROF_ERROR(JNI_FALSE, "exception pop stack empty");
    }
}

void
tls_pop_method(TlsIndex index, jthread thread, jmethodID method)
{
    TlsInfo      *info;
    StackElement  element;
    void         *p;
    FrameIndex    frame_index;
    jlong         current_time;

    HPROF_ASSERT(method!=NULL);
    frame_index = frame_find_or_create(method, -1);
    HPROF_ASSERT(frame_index != 0);

    info = get_info(index);
    HPROF_ASSERT(info!=NULL);
    HPROF_ASSERT(info->stack!=NULL);
    current_time = method_time();
    HPROF_ASSERT(frame_index!=0);
    info->stack = insure_method_on_stack(thread, info, current_time,
                frame_index, method);
    p = stack_top(info->stack);
    HPROF_ASSERT(p!=NULL);
    element = *(StackElement*)p;
    while ( element.frame_index != frame_index ) {
        pop_method(index, current_time, element.method, frame_index);
        p = stack_top(info->stack);
        if ( p == NULL ) {
            break;
        }
        element = *(StackElement*)p;
    }
    pop_method(index, current_time, method, frame_index);
}

/* For all TLS entries, update the last_trace on all threads */
static void
update_all_last_traces(JNIEnv *env)
{
    jthread        *threads;
    TlsInfo       **infos;
    SerialNumber   *serial_nums;
    TraceIndex     *traces;

    if ( gdata->max_trace_depth == 0 ) {
        return;
    }

    table_lock_enter(gdata->tls_table); {

        ThreadList      list;
        int             max_count;
        int             nbytes;
        int             i;

        /* Get buffers to hold thread list and serial number list */
        max_count   = table_element_count(gdata->tls_table);
        nbytes      = (int)sizeof(jthread)*max_count;
        threads     = (jthread*)HPROF_MALLOC(nbytes);
        nbytes      = (int)sizeof(SerialNumber)*max_count;
        serial_nums = (SerialNumber*)HPROF_MALLOC(nbytes);
        nbytes      = (int)sizeof(TlsInfo*)*max_count;
        infos       = (TlsInfo**)HPROF_MALLOC(nbytes);

        /* Get list of threads, serial numbers, and info pointers */
        list.threads     = threads;
        list.serial_nums = serial_nums;
        list.infos       = infos;
        list.count       = 0;
        list.env         = env;
        table_walk_items(gdata->tls_table, &get_thread_list, (void*)&list);

        /* Get all stack trace index's for all these threadss */
        nbytes      = (int)sizeof(TraceIndex)*max_count;
        traces      = (TraceIndex*)HPROF_MALLOC(nbytes);
        trace_get_all_current(list.count, threads, serial_nums,
                              gdata->max_trace_depth, JNI_FALSE,
                              traces, JNI_TRUE);

        /* Loop over traces and update last_trace's */
        for ( i = 0 ; i < list.count ; i++ ) {
            if ( threads[i] != NULL ) {
                deleteLocalReference(env, threads[i]);
            }
            infos[i]->last_trace = traces[i];
        }

    } table_lock_exit(gdata->tls_table);

    /* Free up all allocated space */
    HPROF_FREE(threads);
    HPROF_FREE(serial_nums);
    HPROF_FREE(infos);
    HPROF_FREE(traces);

}

void
tls_dump_traces(JNIEnv *env)
{
    rawMonitorEnter(gdata->data_access_lock); {
        update_all_last_traces(env);
        trace_output_unmarked(env);
    } rawMonitorExit(gdata->data_access_lock);
}

void
tls_dump_monitor_state(JNIEnv *env)
{
    HPROF_ASSERT(env!=NULL);

    rawMonitorEnter(gdata->data_access_lock); {
        tls_dump_traces(env);
        io_write_monitor_dump_header();
        table_walk_items(gdata->tls_table, &dump_thread_state, (void*)env);
        table_walk_items(gdata->tls_table, &dump_monitor_state, (void*)env);
        io_write_monitor_dump_footer();
    } rawMonitorExit(gdata->data_access_lock);
}

void
tls_monitor_start_timer(TlsIndex index)
{
    TlsInfo *info;

    info = get_info(index);
    HPROF_ASSERT(info!=NULL);
    HPROF_ASSERT(info->globalref!=NULL);
    info->monitor_start_time = monitor_time();
}

jlong
tls_monitor_stop_timer(TlsIndex index)
{
    TlsInfo *info;
    jlong    t;

    info = get_info(index);
    HPROF_ASSERT(info!=NULL);
    t =  monitor_time() - info->monitor_start_time;
    info->monitor_start_time = 0;
    return t;
}

TraceIndex
tls_get_trace(TlsIndex index, JNIEnv *env, int depth, jboolean skip_init)
{
    SerialNumber thread_serial_num;
    TraceIndex   trace_index;
    TlsInfo     *info;
    jthread      thread;

    thread_serial_num = get_key(index);
    info              = get_info(index);
    HPROF_ASSERT(info!=NULL);
    setup_trace_buffers(info, depth);
    thread = newLocalReference(env, info->globalref);
    if ( thread != NULL ) {
        trace_index = get_trace(thread, thread_serial_num, depth, skip_init,
                        info->frames_buffer, info->jframes_buffer);
        deleteLocalReference(env, thread);
    } else {
        trace_index = gdata->system_trace_index;
    }
    return trace_index;
}

void
tls_set_in_heap_dump(TlsIndex index, jint in_heap_dump)
{
    TlsInfo  *info;

    info = get_info(index);
    info->in_heap_dump = in_heap_dump;
}

jint
tls_get_in_heap_dump(TlsIndex index)
{
    TlsInfo  *info;

    info = get_info(index);
    return info->in_heap_dump;
}

static void
clean_in_heap_dump(TableIndex index, void *key_ptr, int key_len, void *info_ptr, void *arg)
{
    TlsInfo *info;

    HPROF_ASSERT(info_ptr!=NULL);
    info  = (TlsInfo*)info_ptr;
    info->in_heap_dump = 0;
}

void
tls_clear_in_heap_dump(void)
{
    table_walk_items(gdata->tls_table, &clean_in_heap_dump, NULL);
}

TlsIndex
tls_find(SerialNumber thread_serial_num)
{
    TlsIndex index;

    if ( thread_serial_num == 0 ) {
        return 0;
    }
    index = table_find_entry(gdata->tls_table,
          (void*)&thread_serial_num, (int)sizeof(SerialNumber));
    return index;
}
