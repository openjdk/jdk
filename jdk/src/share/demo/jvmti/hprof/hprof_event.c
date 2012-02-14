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


/* This file contains all class, method and allocation event support functions,
 *   both JVMTI and BCI events.
 *   (See hprof_monitor.c for the monitor event handlers).
 */

#include "hprof.h"

/* Private internal functions. */

/* Return a TraceIndex for the given thread. */
static TraceIndex
get_current(TlsIndex tls_index, JNIEnv *env, jboolean skip_init)
{
    TraceIndex trace_index;

    trace_index  = tls_get_trace(tls_index, env, gdata->max_trace_depth, skip_init);
    return trace_index;
}

/* Return a ClassIndex for the given jclass, loader supplied or looked up. */
static ClassIndex
find_cnum(JNIEnv *env, jclass klass, jobject loader)
{
    LoaderIndex loader_index;
    ClassIndex  cnum;
    char *      signature;

    HPROF_ASSERT(klass!=NULL);

    /* Get the loader index */
    loader_index = loader_find_or_create(env, loader);

    /* Get the signature for this class */
    getClassSignature(klass, &signature, NULL);

    /* Find the ClassIndex for this class */
    cnum   = class_find_or_create(signature, loader_index);

    /* Free the signature space */
    jvmtiDeallocate(signature);

    /* Make sure we save a global reference to this class in the table */
    HPROF_ASSERT(cnum!=0);
    (void)class_new_classref(env, cnum, klass);
    return cnum;
}

/* Get the ClassIndex for the superClass of this jclass. */
static ClassIndex
get_super(JNIEnv *env, jclass klass)
{
    ClassIndex super_cnum;

    super_cnum  = 0;
    WITH_LOCAL_REFS(env, 1) {
        jclass  super_klass;

        super_klass = getSuperclass(env, klass);
        if ( super_klass != NULL ) {
            super_cnum = find_cnum(env, super_klass,
                                getClassLoader(super_klass));
        }
    } END_WITH_LOCAL_REFS;
    return super_cnum;
}

/* Record an allocation. Could be jobject, jclass, jarray or primitive type. */
static void
any_allocation(JNIEnv *env, SerialNumber thread_serial_num,
               TraceIndex trace_index, jobject object)
{
    SiteIndex    site_index;
    ClassIndex   cnum;
    jint         size;
    jclass       klass;

    /*    NOTE: Normally the getObjectClass() and getClassLoader()
     *          would require a
     *               WITH_LOCAL_REFS(env, 1) {
     *               } END_WITH_LOCAL_REFS;
     *          but for performance reasons we skip it here.
     */

    /* Get and tag the klass */
    klass = getObjectClass(env, object);
    cnum = find_cnum(env, klass, getClassLoader(klass));
    site_index = site_find_or_create(cnum, trace_index);
    tag_class(env, klass, cnum, thread_serial_num, site_index);

    /* Tag the object */
    size  = (jint)getObjectSize(object);
    tag_new_object(object, OBJECT_NORMAL, thread_serial_num, size, site_index);
}

/* Handle a java.lang.Object.<init> object allocation. */
void
event_object_init(JNIEnv *env, jthread thread, jobject object)
{
    /* Called via BCI Tracker class */

    /* Be very careful what is called here, watch out for recursion. */

    jint        *pstatus;
    TraceIndex   trace_index;
    SerialNumber thread_serial_num;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(thread!=NULL);
    HPROF_ASSERT(object!=NULL);

    /* Prevent recursion into any BCI function for this thread (pstatus). */
    if ( tls_get_tracker_status(env, thread, JNI_TRUE,
             &pstatus, NULL, &thread_serial_num, &trace_index) == 0 ) {
        (*pstatus) = 1;
        any_allocation(env, thread_serial_num, trace_index, object);
        (*pstatus) = 0;
    }
}

/* Handle any newarray opcode allocation. */
void
event_newarray(JNIEnv *env, jthread thread, jobject object)
{
    /* Called via BCI Tracker class */

    /* Be very careful what is called here, watch out for recursion. */

    jint        *pstatus;
    TraceIndex   trace_index;
    SerialNumber thread_serial_num;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(thread!=NULL);
    HPROF_ASSERT(object!=NULL);

    /* Prevent recursion into any BCI function for this thread (pstatus). */
    if ( tls_get_tracker_status(env, thread, JNI_FALSE,
             &pstatus, NULL, &thread_serial_num, &trace_index) == 0 ) {
        (*pstatus) = 1;
        any_allocation(env, thread_serial_num, trace_index, object);
        (*pstatus) = 0;
    }
}

/* Handle tracking of a method call. */
void
event_call(JNIEnv *env, jthread thread, ClassIndex cnum, MethodIndex mnum)
{
    /* Called via BCI Tracker class */

    /* Be very careful what is called here, watch out for recursion. */

    TlsIndex tls_index;
    jint     *pstatus;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(thread!=NULL);
    HPROF_ASSERT(cnum!=0 && cnum!=gdata->tracker_cnum);

    /* Prevent recursion into any BCI function for this thread (pstatus). */
    if ( tls_get_tracker_status(env, thread, JNI_FALSE,
             &pstatus, &tls_index, NULL, NULL) == 0 ) {
        jmethodID     method;

        (*pstatus) = 1;
        method      = class_get_methodID(env, cnum, mnum);
        HPROF_ASSERT(method!=NULL);
        tls_push_method(tls_index, method);
        (*pstatus) = 0;
    }
}

/* Handle tracking of an exception catch */
void
event_exception_catch(JNIEnv *env, jthread thread, jmethodID method,
        jlocation location, jobject exception)
{
    /* Called via JVMTI_EVENT_EXCEPTION_CATCH callback */

    /* Be very careful what is called here, watch out for recursion. */

    TlsIndex tls_index;
    jint     *pstatus;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(thread!=NULL);
    HPROF_ASSERT(method!=NULL);

    /* Prevent recursion into any BCI function for this thread (pstatus). */
    if ( tls_get_tracker_status(env, thread, JNI_FALSE,
             &pstatus, &tls_index, NULL, NULL) == 0 ) {
        (*pstatus) = 1;
         tls_pop_exception_catch(tls_index, thread, method);
        (*pstatus) = 0;
    }
}

/* Handle tracking of a method return pop one (maybe more) methods. */
void
event_return(JNIEnv *env, jthread thread, ClassIndex cnum, MethodIndex mnum)
{
    /* Called via BCI Tracker class */

    /* Be very careful what is called here, watch out for recursion. */

    TlsIndex tls_index;
    jint     *pstatus;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(thread!=NULL);
    HPROF_ASSERT(cnum!=0 && cnum!=gdata->tracker_cnum);

    /* Prevent recursion into any BCI function for this thread (pstatus). */
    if ( tls_get_tracker_status(env, thread, JNI_FALSE,
             &pstatus, &tls_index, NULL, NULL) == 0 ) {
        jmethodID     method;

        (*pstatus) = 1;
        method      = class_get_methodID(env, cnum, mnum);
        HPROF_ASSERT(method!=NULL);
        tls_pop_method(tls_index, thread, method);
        (*pstatus) = 0;
    }
}

/* Handle a class prepare (should have been already loaded) */
void
event_class_prepare(JNIEnv *env, jthread thread, jclass klass, jobject loader)
{
    /* Called via JVMTI_EVENT_CLASS_PREPARE event */

    ClassIndex    cnum;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(thread!=NULL);
    HPROF_ASSERT(klass!=NULL);

    /* Find the ClassIndex for this class */
    cnum   = find_cnum(env, klass, loader);
    class_add_status(cnum, CLASS_PREPARED);

}

/* Handle a class load (could have been already loaded) */
void
event_class_load(JNIEnv *env, jthread thread, jclass klass, jobject loader)
{
    /* Called via JVMTI_EVENT_CLASS_LOAD event or reset_class_load_status() */

    ClassIndex   cnum;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(klass!=NULL);

    /* Find the ClassIndex for this class */
    cnum   = find_cnum(env, klass, loader);

    /* Always mark it as being in the load list */
    class_add_status(cnum, CLASS_IN_LOAD_LIST);

    /* If we are seeing this as a new loaded class, extra work */
    if ( ! ( class_get_status(cnum) & CLASS_LOADED ) ) {
        TraceIndex   trace_index;
        SiteIndex    site_index;
        ClassIndex   super;
        SerialNumber class_serial_num;
        SerialNumber trace_serial_num;
        SerialNumber thread_serial_num;
        ObjectIndex  class_object_index;
        char        *signature;

        /* Get the TlsIndex and a TraceIndex for this location */
        if ( thread == NULL ) {
            /* This should be very rare, but if this class load was simulated
             *    from hprof_init.c due to a reset of the class load status,
             *    and it originated from a pre-VM_INIT event, the jthread
             *    would be NULL, or it was a jclass created that didn't get
             *    reported to us, like an array class or a primitive class?
             */
            trace_index       = gdata->system_trace_index;
            thread_serial_num = gdata->unknown_thread_serial_num;
        } else {
            TlsIndex     tls_index;

            tls_index    = tls_find_or_create(env, thread);
            trace_index  = get_current(tls_index, env, JNI_FALSE);
            thread_serial_num = tls_get_thread_serial_number(tls_index);
        }

        /* Get the SiteIndex for this location and a java.lang.Class object */
        /*    Note that the target cnum, not the cnum for java.lang.Class. */
        site_index = site_find_or_create(cnum, trace_index);

        /* Tag this java.lang.Class object */
        tag_class(env, klass, cnum, thread_serial_num, site_index);

        class_add_status(cnum, CLASS_LOADED);

        class_serial_num   = class_get_serial_number(cnum);
        class_object_index = class_get_object_index(cnum);
        trace_serial_num   = trace_get_serial_number(trace_index);
        signature          = string_get(class_get_signature(cnum));

        rawMonitorEnter(gdata->data_access_lock); {
            io_write_class_load(class_serial_num, class_object_index,
                        trace_serial_num, signature);
        } rawMonitorExit(gdata->data_access_lock);

        super  = get_super(env, klass);
        class_set_super(cnum, super);
    }

}

/* Handle a thread start event */
void
event_thread_start(JNIEnv *env, jthread thread)
{
    /* Called via JVMTI_EVENT_THREAD_START event */

    TlsIndex    tls_index;
    ObjectIndex object_index;
    TraceIndex  trace_index;
    jlong       tag;
    SerialNumber thread_serial_num;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(thread!=NULL);

    tls_index = tls_find_or_create(env, thread);
    thread_serial_num = tls_get_thread_serial_number(tls_index);
    trace_index = get_current(tls_index, env, JNI_FALSE);

    tag = getTag(thread);
    if ( tag == (jlong)0 ) {
        SiteIndex site_index;
        jint      size;

        size = (jint)getObjectSize(thread);
        site_index = site_find_or_create(gdata->thread_cnum, trace_index);
        /*  We create a new object with this thread's serial number */
        object_index = object_new(site_index, size, OBJECT_NORMAL,
                                              thread_serial_num);
    } else {
        object_index = tag_extract(tag);
        /* Normally the Thread object is created and tagged before we get
         *   here, but the thread_serial_number on this object isn't what
         *   we want. So we update it to the serial number of this thread.
         */
        object_set_thread_serial_number(object_index, thread_serial_num);
    }
    tls_set_thread_object_index(tls_index, object_index);

    WITH_LOCAL_REFS(env, 1) {
        jvmtiThreadInfo      threadInfo;
        jvmtiThreadGroupInfo threadGroupInfo;
        jvmtiThreadGroupInfo parentGroupInfo;

        getThreadInfo(thread, &threadInfo);
        getThreadGroupInfo(threadInfo.thread_group, &threadGroupInfo);
        if ( threadGroupInfo.parent != NULL ) {
            getThreadGroupInfo(threadGroupInfo.parent, &parentGroupInfo);
        } else {
            (void)memset(&parentGroupInfo, 0, sizeof(parentGroupInfo));
        }

        rawMonitorEnter(gdata->data_access_lock); {
            io_write_thread_start(thread_serial_num,
                object_index, trace_get_serial_number(trace_index),
                threadInfo.name, threadGroupInfo.name, parentGroupInfo.name);
        } rawMonitorExit(gdata->data_access_lock);

        jvmtiDeallocate(threadInfo.name);
        jvmtiDeallocate(threadGroupInfo.name);
        jvmtiDeallocate(parentGroupInfo.name);

    } END_WITH_LOCAL_REFS;
}

void
event_thread_end(JNIEnv *env, jthread thread)
{
    /* Called via JVMTI_EVENT_THREAD_END event */
    TlsIndex     tls_index;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(thread!=NULL);

    tls_index = tls_find_or_create(env, thread);
    rawMonitorEnter(gdata->data_access_lock); {
        io_write_thread_end(tls_get_thread_serial_number(tls_index));
    } rawMonitorExit(gdata->data_access_lock);
    tls_thread_ended(env, tls_index);
    setThreadLocalStorage(thread, (void*)NULL);
}
