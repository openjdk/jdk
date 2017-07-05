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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


/* General utility functions. */

/*
 * Wrappers over JVM, JNI, and JVMTI functions are placed here.
 *
 * All memory allocation and deallocation goes through jvmtiAllocate()
 *    and jvmtiDeallocate().
 *
 */


#include "hprof.h"

/* Macro to get JNI function pointer. */
#define JNI_FUNC_PTR(env,f) (*((*(env))->f))

/* Macro to get JVM function pointer. */
#define JVM_FUNC_PTR(env,f) (*((*(env))->f))

/* Macro to get JVMTI function pointer. */
#define JVMTI_FUNC_PTR(env,f) (*((*(env))->f))

/* ------------------------------------------------------------------- */
/* JVM functions */

JNIEnv *
getEnv(void)
{
    JNIEnv *env;
    jint    res;

    res = JVM_FUNC_PTR(gdata->jvm,GetEnv)
                     (gdata->jvm, (void **)&env, JNI_VERSION_1_2);
    if (res != JNI_OK) {
        char buf[256];

        (void)md_snprintf(buf, sizeof(buf),
                "Unable to access JNI Version 1.2 (0x%x),"
                " is your JDK a 5.0 or newer version?"
                " JNIEnv's GetEnv() returned %d",
               JNI_VERSION_1_2, res);
        buf[sizeof(buf)-1] = 0;
        HPROF_ERROR(JNI_FALSE, buf);
        error_exit_process(1); /* Kill entire process, no core dump */
    }
    return env;
}

/* ------------------------------------------------------------------- */
/* Memory Allocation */

void *
jvmtiAllocate(int size)
{
    jvmtiError error;
    unsigned char *ptr;

    HPROF_ASSERT(size>=0);
    ptr = NULL;
    if ( size == 0 ) {
        return ptr;
    }
    error = JVMTI_FUNC_PTR(gdata->jvmti,Allocate)
                (gdata->jvmti, (jlong)size, &ptr);
    if ( error != JVMTI_ERROR_NONE || ptr == NULL ) {
        HPROF_JVMTI_ERROR(error, "Cannot allocate jvmti memory");
    }
    return (void*)ptr;
}

void
jvmtiDeallocate(void *ptr)
{
    if ( ptr != NULL ) {
        jvmtiError error;

        error = JVMTI_FUNC_PTR(gdata->jvmti,Deallocate)
                    (gdata->jvmti, (unsigned char*)ptr);
        if ( error != JVMTI_ERROR_NONE ) {
            HPROF_JVMTI_ERROR(error, "Cannot deallocate jvmti memory");
        }
    }
}

#ifdef DEBUG

void *
hprof_debug_malloc(int size, char *file, int line)
{
    void *ptr;

    HPROF_ASSERT(size>0);

    rawMonitorEnter(gdata->debug_malloc_lock); {
        ptr = debug_malloc(size, file, line);
    } rawMonitorExit(gdata->debug_malloc_lock);

    if ( ptr == NULL ) {
        HPROF_ERROR(JNI_TRUE, "Cannot allocate malloc memory");
    }
    return ptr;
}

void
hprof_debug_free(void *ptr, char *file, int line)
{
    HPROF_ASSERT(ptr!=NULL);

    rawMonitorEnter(gdata->debug_malloc_lock); {
        (void)debug_free(ptr, file, line);
    } rawMonitorExit(gdata->debug_malloc_lock);
}

#endif

void *
hprof_malloc(int size)
{
    void *ptr;

    HPROF_ASSERT(size>0);
    ptr = malloc(size);
    if ( ptr == NULL ) {
        HPROF_ERROR(JNI_TRUE, "Cannot allocate malloc memory");
    }
    return ptr;
}

void
hprof_free(void *ptr)
{
    HPROF_ASSERT(ptr!=NULL);
    (void)free(ptr);
}

/* ------------------------------------------------------------------- */
/* JVMTI Version functions */

jint
jvmtiVersion(void)
{
    if (gdata->cachedJvmtiVersion == 0) {
        jvmtiError error;

        error = JVMTI_FUNC_PTR(gdata->jvmti,GetVersionNumber)
                        (gdata->jvmti, &(gdata->cachedJvmtiVersion));
        if (error != JVMTI_ERROR_NONE) {
            HPROF_JVMTI_ERROR(error, "Cannot get jvmti version number");
        }
    }
    return gdata->cachedJvmtiVersion;
}

static jint
jvmtiMajorVersion(void)
{
    return (jvmtiVersion() & JVMTI_VERSION_MASK_MAJOR)
                    >> JVMTI_VERSION_SHIFT_MAJOR;
}

static jint
jvmtiMinorVersion(void)
{
    return (jvmtiVersion() & JVMTI_VERSION_MASK_MINOR)
                    >> JVMTI_VERSION_SHIFT_MINOR;
}

static jint
jvmtiMicroVersion(void)
{
    return (jvmtiVersion() & JVMTI_VERSION_MASK_MICRO)
                    >> JVMTI_VERSION_SHIFT_MICRO;
}

/* Logic to determine JVMTI version compatibility */
static jboolean
compatible_versions(jint major_runtime,     jint minor_runtime,
                    jint major_compiletime, jint minor_compiletime)
{
    /* Runtime major version must match. */
    if ( major_runtime != major_compiletime ) {
        return JNI_FALSE;
    }
    /* Runtime minor version must be >= the version compiled with. */
    if ( minor_runtime < minor_compiletime ) {
        return JNI_FALSE;
    }
    /* Assumed compatible */
    return JNI_TRUE;
}

/* ------------------------------------------------------------------- */
/* JVMTI Raw Monitor support functions */

jrawMonitorID
createRawMonitor(const char *str)
{
    jvmtiError error;
    jrawMonitorID m;

    m = NULL;
    error = JVMTI_FUNC_PTR(gdata->jvmti,CreateRawMonitor)
                (gdata->jvmti, str, &m);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot create raw monitor");
    }
    return m;
}

void
rawMonitorEnter(jrawMonitorID m)
{
    jvmtiError error;

    error = JVMTI_FUNC_PTR(gdata->jvmti,RawMonitorEnter)
                (gdata->jvmti, m);
    if ( error == JVMTI_ERROR_WRONG_PHASE ) {
        /* Treat this as ok, after agent shutdown CALLBACK code may call this */
        error = JVMTI_ERROR_NONE;
    }
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot enter with raw monitor");
    }
}

void
rawMonitorWait(jrawMonitorID m, jlong pause_time)
{
    jvmtiError error;

    error = JVMTI_FUNC_PTR(gdata->jvmti,RawMonitorWait)
                (gdata->jvmti, m, pause_time);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot wait with raw monitor");
    }
}

void
rawMonitorNotifyAll(jrawMonitorID m)
{
    jvmtiError error;

    error = JVMTI_FUNC_PTR(gdata->jvmti,RawMonitorNotifyAll)
                (gdata->jvmti, m);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot notify all with raw monitor");
    }
}

void
rawMonitorExit(jrawMonitorID m)
{
    jvmtiError error;

    error = JVMTI_FUNC_PTR(gdata->jvmti,RawMonitorExit)
                (gdata->jvmti, m);
    if ( error == JVMTI_ERROR_WRONG_PHASE ) {
        /* Treat this as ok, after agent shutdown CALLBACK code may call this */
        error = JVMTI_ERROR_NONE;
    }
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot exit with raw monitor");
    }
}

void
destroyRawMonitor(jrawMonitorID m)
{
    jvmtiError error;

    error = JVMTI_FUNC_PTR(gdata->jvmti,DestroyRawMonitor)
                (gdata->jvmti, m);
    if ( error == JVMTI_ERROR_WRONG_PHASE ) {
        /* Treat this as ok */
        error = JVMTI_ERROR_NONE;
    }
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot destroy raw monitor");
    }
}

/* ------------------------------------------------------------------- */
/* JVMTI Event enabling/disabilin */

void
setEventNotificationMode(jvmtiEventMode mode, jvmtiEvent event, jthread thread)
{
    jvmtiError error;

    error = JVMTI_FUNC_PTR(gdata->jvmti,SetEventNotificationMode)
                (gdata->jvmti, mode, event, thread);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot set event notification");
    }
}

/* ---------------------------------------------------------------------- */
/* JNI Support Functions */

jobject
exceptionOccurred(JNIEnv *env)
{
    return JNI_FUNC_PTR(env,ExceptionOccurred)(env);
}

void
exceptionDescribe(JNIEnv *env)
{
    JNI_FUNC_PTR(env,ExceptionDescribe)(env);
}

void
exceptionClear(JNIEnv *env)
{
    JNI_FUNC_PTR(env,ExceptionClear)(env);
}

jobject
newGlobalReference(JNIEnv *env, jobject object)
{
    jobject gref;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(object!=NULL);
    gref = JNI_FUNC_PTR(env,NewGlobalRef)(env, object);
    HPROF_ASSERT(gref!=NULL);
    return gref;
}

jobject
newWeakGlobalReference(JNIEnv *env, jobject object)
{
    jobject gref;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(object!=NULL);
    gref = JNI_FUNC_PTR(env,NewWeakGlobalRef)(env, object);
    HPROF_ASSERT(gref!=NULL);
    return gref;
}

void
deleteGlobalReference(JNIEnv *env, jobject object)
{
    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(object!=NULL);
    JNI_FUNC_PTR(env,DeleteGlobalRef)(env, object);
}

jobject
newLocalReference(JNIEnv *env, jobject object)
{
    jobject lref;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(object!=NULL);
    lref = JNI_FUNC_PTR(env,NewLocalRef)(env, object);
    /* Possible for a non-null weak reference to return a NULL localref */
    return lref;
}

void
deleteLocalReference(JNIEnv *env, jobject object)
{
    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(object!=NULL);
    JNI_FUNC_PTR(env,DeleteLocalRef)(env, object);
}

void
deleteWeakGlobalReference(JNIEnv *env, jobject object)
{
    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(object!=NULL);
    JNI_FUNC_PTR(env,DeleteWeakGlobalRef)(env, object);
}

jclass
getObjectClass(JNIEnv *env, jobject object)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    jclass clazz;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(object!=NULL);
    clazz = JNI_FUNC_PTR(env,GetObjectClass)(env, object);
    HPROF_ASSERT(clazz!=NULL);
    return clazz;
}

jclass
getSuperclass(JNIEnv *env, jclass klass)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    jclass super_klass;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(klass!=NULL);
    super_klass = JNI_FUNC_PTR(env,GetSuperclass)(env, klass);
    return super_klass;
}

jmethodID
getStaticMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig)
{
    jmethodID method;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(clazz!=NULL);
    HPROF_ASSERT(name!=NULL);
    HPROF_ASSERT(sig!=NULL);
    CHECK_EXCEPTIONS(env) {
        method = JNI_FUNC_PTR(env,GetStaticMethodID)(env, clazz, name, sig);
    } END_CHECK_EXCEPTIONS;
    HPROF_ASSERT(method!=NULL);
    return method;
}

jmethodID
getMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig)
{
    jmethodID method;
    jobject exception;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(clazz!=NULL);
    HPROF_ASSERT(name!=NULL);
    HPROF_ASSERT(sig!=NULL);
    method = JNI_FUNC_PTR(env,GetMethodID)(env, clazz, name, sig);
    /* Might be a static method */
    exception = JNI_FUNC_PTR(env,ExceptionOccurred)(env);
    if ( exception != NULL ) {
        JNI_FUNC_PTR(env,ExceptionClear)(env);
        method = getStaticMethodID(env, clazz, name, sig);
    }
    HPROF_ASSERT(method!=NULL);
    return method;
}

jclass
findClass(JNIEnv *env, const char *name)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    jclass clazz;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(name!=NULL);
    LOG2("FindClass", name);
    CHECK_EXCEPTIONS(env) {
        clazz = JNI_FUNC_PTR(env,FindClass)(env, name);
    } END_CHECK_EXCEPTIONS;
    HPROF_ASSERT(clazz!=NULL);
    return clazz;
}

jfieldID
getStaticFieldID(JNIEnv *env, jclass clazz, const char *name, const char *sig)
{
    jfieldID field;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(clazz!=NULL);
    HPROF_ASSERT(name!=NULL);
    HPROF_ASSERT(sig!=NULL);
    CHECK_EXCEPTIONS(env) {
        field = JNI_FUNC_PTR(env,GetStaticFieldID)(env, clazz, name, sig);
    } END_CHECK_EXCEPTIONS;
    return field;
}

void
setStaticIntField(JNIEnv *env, jclass clazz, jfieldID field, jint value)
{
    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(clazz!=NULL);
    HPROF_ASSERT(field!=NULL);
    CHECK_EXCEPTIONS(env) {
        JNI_FUNC_PTR(env,SetStaticIntField)(env, clazz, field, value);
    } END_CHECK_EXCEPTIONS;
}

static jobject
callStaticObjectMethod(JNIEnv *env, jclass klass, jmethodID method)
{
    jobject x;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(klass!=NULL);
    HPROF_ASSERT(method!=NULL);
    CHECK_EXCEPTIONS(env) {
        x = JNI_FUNC_PTR(env,CallStaticObjectMethod)(env, klass, method);
    } END_CHECK_EXCEPTIONS;
    return x;
}

static jlong
callLongMethod(JNIEnv *env, jobject object, jmethodID method)
{
    jlong x;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(object!=NULL);
    HPROF_ASSERT(method!=NULL);
    CHECK_EXCEPTIONS(env) {
        x = JNI_FUNC_PTR(env,CallLongMethod)(env, object, method);
    } END_CHECK_EXCEPTIONS;
    return x;
}

static void
callVoidMethod(JNIEnv *env, jobject object, jmethodID method, jboolean arg)
{
    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(object!=NULL);
    HPROF_ASSERT(method!=NULL);
    CHECK_EXCEPTIONS(env) {
        JNI_FUNC_PTR(env,CallVoidMethod)(env, object, method, arg);
    } END_CHECK_EXCEPTIONS;
}

static jstring
newStringUTF(JNIEnv *env, const char *name)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    jstring string;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(name!=NULL);
    CHECK_EXCEPTIONS(env) {
        string = JNI_FUNC_PTR(env,NewStringUTF)(env, name);
    } END_CHECK_EXCEPTIONS;
    HPROF_ASSERT(string!=NULL);
    return string;
}

static jobject
newThreadObject(JNIEnv *env, jclass clazz, jmethodID method,
                jthreadGroup group, jstring name)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    jthread thread;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(clazz!=NULL);
    HPROF_ASSERT(method!=NULL);
    CHECK_EXCEPTIONS(env) {
        thread = JNI_FUNC_PTR(env,NewObject)(env, clazz, method, group, name);
    } END_CHECK_EXCEPTIONS;
    HPROF_ASSERT(thread!=NULL);
    return thread;
}

jboolean
isSameObject(JNIEnv *env, jobject o1, jobject o2)
{
    HPROF_ASSERT(env!=NULL);
    if ( o1 == o2  || JNI_FUNC_PTR(env,IsSameObject)(env, o1, o2) ) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

void
pushLocalFrame(JNIEnv *env, jint capacity)
{
    HPROF_ASSERT(env!=NULL);
    CHECK_EXCEPTIONS(env) {
        jint ret;

        ret = JNI_FUNC_PTR(env,PushLocalFrame)(env, capacity);
        if ( ret != 0 ) {
            HPROF_ERROR(JNI_TRUE, "JNI PushLocalFrame returned non-zero");
        }
    } END_CHECK_EXCEPTIONS;
}

void
popLocalFrame(JNIEnv *env, jobject result)
{
    jobject ret;

    HPROF_ASSERT(env!=NULL);
    ret = JNI_FUNC_PTR(env,PopLocalFrame)(env, result);
    if ( (result != NULL && ret == NULL) || (result == NULL && ret != NULL) ) {
        HPROF_ERROR(JNI_TRUE, "JNI PopLocalFrame returned wrong object");
    }
}

void
registerNatives(JNIEnv *env, jclass clazz,
                        JNINativeMethod *methods, jint count)
{
    jint ret;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(clazz!=NULL);
    HPROF_ASSERT(methods!=NULL);
    HPROF_ASSERT(count>0);
    ret = JNI_FUNC_PTR(env,RegisterNatives)(env, clazz, methods, count);
    if ( ret != 0 ) {
        HPROF_ERROR(JNI_TRUE, "JNI RegisterNatives returned non-zero");
    }
}

/* ---------------------------------------------------------------------- */
/* JVMTI Support Functions */

char *
getErrorName(jvmtiError error_number)
{
    char *error_name;

    error_name = NULL;
    (void)JVMTI_FUNC_PTR(gdata->jvmti,GetErrorName)
                        (gdata->jvmti, error_number, &error_name);
    return error_name;
}

jvmtiPhase
getPhase(void)
{
    jvmtiPhase phase;

    phase = 0;
    (void)JVMTI_FUNC_PTR(gdata->jvmti,GetPhase)(gdata->jvmti, &phase);
    return phase;
}

char *
phaseString(jvmtiPhase phase)
{
    switch ( phase ) {
        case JVMTI_PHASE_ONLOAD:
            return "onload";
        case JVMTI_PHASE_PRIMORDIAL:
            return "primordial";
        case JVMTI_PHASE_START:
            return "start";
        case JVMTI_PHASE_LIVE:
            return "live";
        case JVMTI_PHASE_DEAD:
            return "dead";
    }
    return "unknown";
}

void
disposeEnvironment(void)
{
    (void)JVMTI_FUNC_PTR(gdata->jvmti,DisposeEnvironment)
                        (gdata->jvmti);
}

jlong
getObjectSize(jobject object)
{
    jlong size;
    jvmtiError error;

    HPROF_ASSERT(object!=NULL);
    size = 0;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetObjectSize)
                        (gdata->jvmti, object, &size);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get object size");
    }
    return size;
}

static jboolean
isInterface(jclass klass)
{
    jvmtiError error;
    jboolean   answer;

    HPROF_ASSERT(klass!=NULL);
    answer = JNI_FALSE;
    error = JVMTI_FUNC_PTR(gdata->jvmti,IsInterface)
                        (gdata->jvmti, klass, &answer);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot call IsInterface");
    }
    return answer;
}

jint
getClassStatus(jclass klass)
{
    jvmtiError error;
    jint       status;

    HPROF_ASSERT(klass!=NULL);
    status = 0;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetClassStatus)
                        (gdata->jvmti, klass, &status);
    if ( error == JVMTI_ERROR_WRONG_PHASE ) {
        /* Treat this as ok */
        error = JVMTI_ERROR_NONE;
        status = 0;
    }
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get class status");
    }
    return status;
}

jobject
getClassLoader(jclass klass)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    jvmtiError error;
    jobject    loader;

    HPROF_ASSERT(klass!=NULL);
    loader = NULL;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetClassLoader)
                        (gdata->jvmti, klass, &loader);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get class loader");
    }
    return loader;
}

jlong
getTag(jobject object)
{
    jlong tag;
    jvmtiError error;

    HPROF_ASSERT(object!=NULL);
    tag = 0;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetTag)
                        (gdata->jvmti, object, &tag);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get object tag");
    }
    return tag;
}

void
setTag(jobject object, jlong tag)
{
    jvmtiError error;

    HPROF_ASSERT(object!=NULL);
    error = JVMTI_FUNC_PTR(gdata->jvmti,SetTag)
                        (gdata->jvmti, object, tag);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot set object tag");
    }
}

void
getObjectMonitorUsage(jobject object, jvmtiMonitorUsage *uinfo)
{
    jvmtiError error;

    HPROF_ASSERT(object!=NULL);
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetObjectMonitorUsage)
                        (gdata->jvmti, object, uinfo);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get monitor usage info");
    }
}

void
getOwnedMonitorInfo(jthread thread, jobject **ppobjects, jint *pcount)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    jvmtiError error;

    HPROF_ASSERT(thread!=NULL);
    HPROF_ASSERT(ppobjects!=NULL);
    HPROF_ASSERT(pcount!=NULL);
    *pcount = 0;
    *ppobjects = NULL;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetOwnedMonitorInfo)
                        (gdata->jvmti, thread, pcount, ppobjects);
    if ( error == JVMTI_ERROR_THREAD_NOT_ALIVE ) {
        *pcount = 0;
        error = JVMTI_ERROR_NONE;
    }
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get thread owned monitor info");
    }
}

void
getSystemProperty(const char *name, char **value)
{
    jvmtiError error;

    HPROF_ASSERT(name!=NULL);
    *value = NULL;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetSystemProperty)
                        (gdata->jvmti, name, value);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get system property");
    }
}

void
getClassSignature(jclass klass, char** psignature, char **pgeneric_signature)
{
    jvmtiError error;
    char *generic_signature;

    HPROF_ASSERT(klass!=NULL);
    *psignature = NULL;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetClassSignature)
                        (gdata->jvmti, klass, psignature, &generic_signature);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get class signature");
    }
    if ( pgeneric_signature != NULL ) {
        *pgeneric_signature = generic_signature;
    } else {
        jvmtiDeallocate(generic_signature);
    }
}

void
getSourceFileName(jclass klass, char** pname)
{
    jvmtiError error;

    HPROF_ASSERT(klass!=NULL);
    *pname = NULL;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetSourceFileName)
                        (gdata->jvmti, klass, pname);
    if ( error == JVMTI_ERROR_ABSENT_INFORMATION ) {
        error = JVMTI_ERROR_NONE;
        *pname = NULL;
    }
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get source file name");
    }
}

static void
getClassFields(jclass klass, jint* pn_fields, jfieldID** pfields)
{
    jvmtiError error;
    jint       status;

    HPROF_ASSERT(klass!=NULL);
    *pn_fields = 0;
    *pfields      = NULL;

    /* Get class status */
    status = getClassStatus(klass);

    /* Arrays have no fields */
    if ( status & JVMTI_CLASS_STATUS_ARRAY ) {
        return;
    }

    /* Primitives have no fields */
    if ( status & JVMTI_CLASS_STATUS_PRIMITIVE ) {
        return;
    }

    /* If the class is not prepared, we have a problem? */
    if ( !(status & JVMTI_CLASS_STATUS_PREPARED) ) {
        HPROF_ERROR(JNI_FALSE, "Class not prepared when needing fields");
        return;
    }

    /* Now try and get all the fields */
    error         = JVMTI_FUNC_PTR(gdata->jvmti,GetClassFields)
                        (gdata->jvmti, klass, pn_fields, pfields);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get class field list");
    }
}

static jint
getFieldModifiers(jclass klass, jfieldID field)
{
    jvmtiError error;
    jint       modifiers;

    HPROF_ASSERT(klass!=NULL);
    HPROF_ASSERT(field!=NULL);
    modifiers = 0;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetFieldModifiers)
            (gdata->jvmti, klass, field, &modifiers);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get field modifiers");
    }
    return modifiers;
}

static void
getFieldName(jclass klass, jfieldID field, char** pname, char** psignature,
                        char **pgeneric_signature)
{
    jvmtiError error;
    char *generic_signature;

    generic_signature = NULL;
    *pname = NULL;
    *psignature = NULL;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetFieldName)
            (gdata->jvmti, klass, field, pname, psignature, &generic_signature);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get field name");
    }
    if ( pgeneric_signature != NULL ) {
        *pgeneric_signature = generic_signature;
    } else {
        jvmtiDeallocate(generic_signature);
    }
}

static void
getImplementedInterfaces(jclass klass, jint* pn_interfaces,
                        jclass** pinterfaces)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    jvmtiError error;

    *pn_interfaces = 0;
    *pinterfaces   = NULL;
    error          = JVMTI_FUNC_PTR(gdata->jvmti,GetImplementedInterfaces)
                        (gdata->jvmti, klass, pn_interfaces, pinterfaces);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get class interface list");
    }
}

static ClassIndex
get_cnum(JNIEnv *env, jclass klass)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    ClassIndex  cnum;
    LoaderIndex loader_index;
    char       *sig;
    jobject     loader;

    loader       = getClassLoader(klass);
    loader_index = loader_find_or_create(env, loader);
    getClassSignature(klass, &sig, NULL);
    cnum = class_find_or_create(sig, loader_index);
    jvmtiDeallocate(sig);
    (void)class_new_classref(env, cnum, klass);
    return cnum;
}

/* From primitive type, get signature letter */
char
primTypeToSigChar(jvmtiPrimitiveType primType)
{
    char sig_ch;

    sig_ch = 0;
    switch ( primType ) {
        case JVMTI_PRIMITIVE_TYPE_BYTE:
            sig_ch = JVM_SIGNATURE_BYTE;
            break;
        case JVMTI_PRIMITIVE_TYPE_CHAR:
            sig_ch = JVM_SIGNATURE_CHAR;
            break;
        case JVMTI_PRIMITIVE_TYPE_FLOAT:
            sig_ch = JVM_SIGNATURE_FLOAT;
            break;
        case JVMTI_PRIMITIVE_TYPE_DOUBLE:
            sig_ch = JVM_SIGNATURE_DOUBLE;
            break;
        case JVMTI_PRIMITIVE_TYPE_INT:
            sig_ch = JVM_SIGNATURE_INT;
            break;
        case JVMTI_PRIMITIVE_TYPE_LONG:
            sig_ch = JVM_SIGNATURE_LONG;
            break;
        case JVMTI_PRIMITIVE_TYPE_SHORT:
            sig_ch = JVM_SIGNATURE_SHORT;
            break;
        case JVMTI_PRIMITIVE_TYPE_BOOLEAN:
            sig_ch = JVM_SIGNATURE_BOOLEAN;
            break;
        default:
            sig_ch = 0;
            break;
    }
    return sig_ch;
}

/* From signature, get primitive type */
jvmtiPrimitiveType
sigToPrimType(char *sig)
{
    jvmtiPrimitiveType primType;

    primType = 0;
    if ( sig == NULL || sig[0] == 0 ) {
        return primType;
    }
    switch ( sig[0] ) {
        case JVM_SIGNATURE_BYTE:
            primType =  JVMTI_PRIMITIVE_TYPE_BYTE;
            break;
        case JVM_SIGNATURE_CHAR:
            primType =  JVMTI_PRIMITIVE_TYPE_CHAR;
            break;
        case JVM_SIGNATURE_FLOAT:
            primType =  JVMTI_PRIMITIVE_TYPE_FLOAT;
            break;
        case JVM_SIGNATURE_DOUBLE:
            primType =  JVMTI_PRIMITIVE_TYPE_DOUBLE;
            break;
        case JVM_SIGNATURE_INT:
            primType =  JVMTI_PRIMITIVE_TYPE_INT;
            break;
        case JVM_SIGNATURE_LONG:
            primType =  JVMTI_PRIMITIVE_TYPE_LONG;
            break;
        case JVM_SIGNATURE_SHORT:
            primType =  JVMTI_PRIMITIVE_TYPE_SHORT;
            break;
        case JVM_SIGNATURE_BOOLEAN:
            primType =  JVMTI_PRIMITIVE_TYPE_BOOLEAN;
            break;
    }
    return primType;
}

/* From signature, get primitive size */
int
sigToPrimSize(char *sig)
{
    unsigned size;

    size = 0;
    if ( sig == NULL || sig[0] == 0 ) {
        return size;
    }
    switch ( sig[0] ) {
        case JVM_SIGNATURE_BYTE:
        case JVM_SIGNATURE_BOOLEAN:
            size =  1;
            break;
        case JVM_SIGNATURE_CHAR:
        case JVM_SIGNATURE_SHORT:
            size =  2;
            break;
        case JVM_SIGNATURE_FLOAT:
        case JVM_SIGNATURE_INT:
            size =  4;
            break;
        case JVM_SIGNATURE_DOUBLE:
        case JVM_SIGNATURE_LONG:
            size =  8;
            break;
    }
    return size;
}

static void
add_class_fields(JNIEnv *env, ClassIndex top_cnum, ClassIndex cnum,
                jclass klass, Stack *field_list, Stack *class_list)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    jclass     *interfaces;
    jint        n_interfaces;
    jfieldID   *idlist;
    jint        n_fields;
    int         i;
    int         depth;
    int         skip_static_field_names;
    jint        status;

    HPROF_ASSERT(env!=NULL);
    HPROF_ASSERT(klass!=NULL);
    HPROF_ASSERT(field_list!=NULL);
    HPROF_ASSERT(class_list!=NULL);

    /* If not the initial class, we can skip the static fields (perf issue) */
    skip_static_field_names = (cnum != top_cnum);

    /* Get class status */
    status = getClassStatus(klass);

    /* Arrays have no fields */
    if ( status & JVMTI_CLASS_STATUS_ARRAY ) {
        return;
    }

    /* Primitives have no fields */
    if ( status & JVMTI_CLASS_STATUS_PRIMITIVE ) {
        return;
    }

    /* If the class is not prepared, we have a problem? */
    if ( !(status & JVMTI_CLASS_STATUS_PREPARED) ) {
        char *sig;

        getClassSignature(klass, &sig, NULL);
        debug_message("Class signature is: %s\n", sig);
        HPROF_ERROR(JNI_FALSE, "Class not prepared when needing all fields");
        jvmtiDeallocate(sig);
        return;
    }

    /* See if class already processed */
    depth = stack_depth(class_list);
    for ( i = depth-1 ; i >= 0 ; i-- ) {
        if ( isSameObject(env, klass, *(jclass*)stack_element(class_list, i)) ) {
            return;
        }
    }

    /* Class or Interface, do implemented interfaces recursively */
    getImplementedInterfaces(klass, &n_interfaces, &interfaces);
    for ( i = 0 ; i < n_interfaces ; i++ ) {
        add_class_fields(env, top_cnum,
                         get_cnum(env, interfaces[i]), interfaces[i],
                         field_list, class_list);
    }
    jvmtiDeallocate(interfaces);

    /* Begin graph traversal, go up super chain recursively */
    if ( !isInterface(klass) ) {
        jclass super_klass;

        super_klass = getSuperclass(env, klass);
        if ( super_klass != NULL ) {
            add_class_fields(env, top_cnum,
                             get_cnum(env, super_klass), super_klass,
                             field_list, class_list);
        }
    }


    /* Only now we add klass to list so we don't repeat it later */
    stack_push(class_list, &klass);

    /* Now actually add the fields for this klass */
    getClassFields(klass, &n_fields, &idlist);
    for ( i = 0 ; i < n_fields ; i++ ) {
        FieldInfo        finfo;
        static FieldInfo empty_finfo;

        finfo           = empty_finfo;
        finfo.cnum      = cnum;
        finfo.modifiers = (unsigned short)getFieldModifiers(klass, idlist[i]);
        if ( ( finfo.modifiers & JVM_ACC_STATIC ) == 0 ||
             !skip_static_field_names ) {
            char *field_name;
            char *field_sig;

            getFieldName(klass, idlist[i], &field_name, &field_sig, NULL);
            finfo.name_index = string_find_or_create(field_name);
            finfo.sig_index  = string_find_or_create(field_sig);
            finfo.primType   = sigToPrimType(field_sig);
            finfo.primSize   = sigToPrimSize(field_sig);
            jvmtiDeallocate(field_name);
            jvmtiDeallocate(field_sig);
        }
        stack_push(field_list, &finfo);
    }
    jvmtiDeallocate(idlist);
}

void
getAllClassFieldInfo(JNIEnv *env, jclass klass,
                jint* pn_fields, FieldInfo** pfields)
{
    ClassIndex cnum;

    *pfields      = NULL;
    *pn_fields    = 0;

    WITH_LOCAL_REFS(env, 1) {
        Stack *class_list;
        Stack *field_list;
        int    nbytes;

        cnum          = get_cnum(env, klass);
        class_list    = stack_init( 16,  16, (int)sizeof(jclass));
        field_list    = stack_init(128, 128, (int)sizeof(FieldInfo));
        add_class_fields(env, cnum, cnum, klass, field_list, class_list);
        *pn_fields    = stack_depth(field_list);
        if ( (*pn_fields) > 0 ) {
            nbytes        = (*pn_fields) * (int)sizeof(FieldInfo);
            *pfields      = (FieldInfo*)HPROF_MALLOC(nbytes);
            (void)memcpy(*pfields, stack_element(field_list, 0), nbytes);
        }
        stack_term(field_list);
        stack_term(class_list);
    } END_WITH_LOCAL_REFS;
}

void
getMethodClass(jmethodID method, jclass *pclazz)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    jvmtiError error;

    HPROF_ASSERT(method!=NULL);
    *pclazz = NULL;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetMethodDeclaringClass)
                (gdata->jvmti, method, pclazz);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get method class");
    }
}

jboolean
isMethodNative(jmethodID method)
{
    jvmtiError error;
    jboolean   isNative;

    HPROF_ASSERT(method!=NULL);
    error = JVMTI_FUNC_PTR(gdata->jvmti,IsMethodNative)
                (gdata->jvmti, method, &isNative);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot check is method native");
    }
    return isNative;
}

void
getMethodName(jmethodID method, char** pname, char** psignature)
{
    jvmtiError error;
    char *generic_signature;

    HPROF_ASSERT(method!=NULL);
    generic_signature = NULL;
    *pname = NULL;
    *psignature = NULL;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetMethodName)
                (gdata->jvmti, method, pname, psignature, &generic_signature);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get method name");
    }
    jvmtiDeallocate(generic_signature);
}

void
getPotentialCapabilities(jvmtiCapabilities *pcapabilities)
{
    jvmtiError error;

    (void)memset(pcapabilities,0,sizeof(jvmtiCapabilities));
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetPotentialCapabilities)
                (gdata->jvmti, pcapabilities);
    if (error != JVMTI_ERROR_NONE) {
        HPROF_ERROR(JNI_FALSE, "Unable to get potential JVMTI capabilities.");
        error_exit_process(1); /* Kill entire process, no core dump wanted */
    }
}

void
addCapabilities(jvmtiCapabilities *pcapabilities)
{
    jvmtiError error;

    error = JVMTI_FUNC_PTR(gdata->jvmti,AddCapabilities)
                (gdata->jvmti, pcapabilities);
    if (error != JVMTI_ERROR_NONE) {
        HPROF_ERROR(JNI_FALSE, "Unable to get necessary JVMTI capabilities.");
        error_exit_process(1); /* Kill entire process, no core dump wanted */
    }
}

void
setEventCallbacks(jvmtiEventCallbacks *pcallbacks)
{
    jvmtiError error;

    error = JVMTI_FUNC_PTR(gdata->jvmti,SetEventCallbacks)
                (gdata->jvmti, pcallbacks, (int)sizeof(jvmtiEventCallbacks));
    if (error != JVMTI_ERROR_NONE) {
        HPROF_JVMTI_ERROR(error, "Cannot set jvmti callbacks");
    }

}

void *
getThreadLocalStorage(jthread thread)
{
    jvmtiError error;
    void *ptr;

    HPROF_ASSERT(thread!=NULL);
    ptr = NULL;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetThreadLocalStorage)
                (gdata->jvmti, thread, &ptr);
    if ( error == JVMTI_ERROR_WRONG_PHASE ) {
        /* Treat this as ok */
        error = JVMTI_ERROR_NONE;
        ptr = NULL;
    }
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get thread local storage");
    }
    return ptr;
}

void
setThreadLocalStorage(jthread thread, void *ptr)
{
    jvmtiError error;

    HPROF_ASSERT(thread!=NULL);
    error = JVMTI_FUNC_PTR(gdata->jvmti,SetThreadLocalStorage)
                (gdata->jvmti, thread, (const void *)ptr);
    if ( error == JVMTI_ERROR_WRONG_PHASE ) {
        /* Treat this as ok */
        error = JVMTI_ERROR_NONE;
    }
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot set thread local storage");
    }
}

void
getThreadState(jthread thread, jint *threadState)
{
    jvmtiError error;

    HPROF_ASSERT(thread!=NULL);
    HPROF_ASSERT(threadState!=NULL);
    *threadState = 0;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetThreadState)
                (gdata->jvmti, thread, threadState);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get thread state");
    }
}

void
getThreadInfo(jthread thread, jvmtiThreadInfo *info)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    jvmtiError error;

    HPROF_ASSERT(thread!=NULL);
    HPROF_ASSERT(info!=NULL);
    (void)memset((void*)info, 0, sizeof(jvmtiThreadInfo));
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetThreadInfo)
                (gdata->jvmti, thread, info);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get thread info");
    }
}

void
getThreadGroupInfo(jthreadGroup thread_group, jvmtiThreadGroupInfo *info)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    jvmtiError error;

    HPROF_ASSERT(info!=NULL);
    (void)memset((void*)info, 0, sizeof(jvmtiThreadGroupInfo));
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetThreadGroupInfo)
                (gdata->jvmti, thread_group, info);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get thread group info");
    }
}

void
getLoadedClasses(jclass **ppclasses, jint *pcount)
/* WARNING: Must be called inside WITH_LOCAL_REFS */
{
    jvmtiError error;

    *ppclasses = NULL;
    *pcount = 0;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetLoadedClasses)
                (gdata->jvmti, pcount, ppclasses);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get all loaded class list");
    }
}

static void
getLineNumberTable(jmethodID method, jvmtiLineNumberEntry **ppentries,
                jint *pcount)
{
    jvmtiError error;

    HPROF_ASSERT(method!=NULL);
    *ppentries = NULL;
    *pcount    = 0;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetLineNumberTable)
                (gdata->jvmti, method, pcount, ppentries);
    if ( error == JVMTI_ERROR_ABSENT_INFORMATION ) {
        error = JVMTI_ERROR_NONE;
        *ppentries = NULL;
        *pcount    = 0;
    }
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get source line numbers");
    }
}

static jint
map_loc2line(jlocation location, jvmtiLineNumberEntry *table, jint count)
{
    jint line_number;
    int i;
    int start;
    int half;

    HPROF_ASSERT(location>=0);
    HPROF_ASSERT(count>=0);

    line_number = -1;
    if ( count == 0 ) {
        return line_number;
    }

    /* Do a binary search */
    half = count >> 1;
    start = 0;
    while ( half > 0 ) {
        jlocation start_location;

        start_location = table[start + half].start_location;
        if ( location > start_location ) {
            start = start + half;
        } else if ( location == start_location ) {
            start = start + half;
            break;
        }
        half = half >> 1;
    }

    HPROF_ASSERT(start < count);

    /* Now start the table search */
    for ( i = start ; i < count ; i++ ) {
        if ( location < table[i].start_location ) {
            HPROF_ASSERT( ((int)location) < ((int)table[i].start_location) );
            break;
        }
        line_number = table[i].line_number;
    }
    HPROF_ASSERT(line_number > 0);
    return line_number;
}

jint
getLineNumber(jmethodID method, jlocation location)
{
    jvmtiLineNumberEntry *line_table;
    jint                  line_count;
    jint                  lineno;

    HPROF_ASSERT(method!=NULL);
    if ( location < 0 ) {
        HPROF_ASSERT(location > -4);
        return (jint)location;
    }
    lineno = -1;

    getLineNumberTable(method, &line_table, &line_count);
    lineno = map_loc2line(location, line_table, line_count);
    jvmtiDeallocate(line_table);

    return lineno;
}

jlong
getMaxMemory(JNIEnv *env)
{
    jlong max;

    HPROF_ASSERT(env!=NULL);

    max = (jlong)0;
    WITH_LOCAL_REFS(env, 1) {
        jclass          clazz;
        jmethodID       getRuntime;
        jobject         runtime;
        jmethodID       maxMemory;

        clazz      = findClass(env, "java/lang/Runtime");
        getRuntime = getStaticMethodID(env, clazz, "getRuntime",
                                       "()Ljava/lang/Runtime;");
        runtime    = callStaticObjectMethod(env, clazz, getRuntime);
        maxMemory  = getMethodID(env, clazz, "maxMemory", "()J");
        max        = callLongMethod(env, runtime, maxMemory);
    } END_WITH_LOCAL_REFS;
    return max;
}

void
createAgentThread(JNIEnv *env, const char *name, jvmtiStartFunction func)
{
    jvmtiError          error;

    HPROF_ASSERT(name!=NULL);
    HPROF_ASSERT(func!=NULL);

    WITH_LOCAL_REFS(env, 1) {
        jclass          clazz;
        jmethodID       threadConstructor;
        jmethodID       threadSetDaemon;
        jthread         thread;
        jstring         nameString;
        jthreadGroup    systemThreadGroup;
        jthreadGroup *  groups;
        jint            groupCount;

        thread                  = NULL;
        systemThreadGroup       = NULL;
        groups                  = NULL;
        clazz                   = class_get_class(env, gdata->thread_cnum);
        HPROF_ASSERT(clazz!=NULL);
        threadConstructor       = getMethodID(env, clazz, "<init>",
                        "(Ljava/lang/ThreadGroup;Ljava/lang/String;)V");
        threadSetDaemon         = getMethodID(env, clazz, "setDaemon",
                        "(Z)V");

        error = JVMTI_FUNC_PTR(gdata->jvmti,GetTopThreadGroups)
                    (gdata->jvmti, &groupCount, &groups);
        if ( error == JVMTI_ERROR_NONE ) {
            if ( groupCount > 0 ) {
                systemThreadGroup = groups[0];
            }
            jvmtiDeallocate(groups);

            nameString  = newStringUTF(env, name);
            HPROF_ASSERT(nameString!=NULL);
            thread      = newThreadObject(env, clazz, threadConstructor,
                                        systemThreadGroup, nameString);
            HPROF_ASSERT(thread!=NULL);
            callVoidMethod(env, thread, threadSetDaemon, JNI_TRUE);

            error = JVMTI_FUNC_PTR(gdata->jvmti,RunAgentThread)
                (gdata->jvmti, thread, func, NULL, JVMTI_THREAD_MAX_PRIORITY);

            /* After the thread is running... */

            /* Make sure the TLS table has this thread as an agent thread */
            tls_agent_thread(env, thread);
        }
    } END_WITH_LOCAL_REFS;

    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot create agent thread");
    }
}

jlong
getThreadCpuTime(jthread thread)
{
    jvmtiError error;
    jlong cpuTime;

    HPROF_ASSERT(thread!=NULL);
    cpuTime = -1;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetThreadCpuTime)
                (gdata->jvmti, thread, &cpuTime);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get cpu time");
    }
    return cpuTime;
}

/* Get frame count */
void
getFrameCount(jthread thread, jint *pcount)
{
    jvmtiError error;

    HPROF_ASSERT(thread!=NULL);
    HPROF_ASSERT(pcount!=NULL);
    *pcount = 0;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetFrameCount)
            (gdata->jvmti, thread, pcount);
    if ( error != JVMTI_ERROR_NONE ) {
        *pcount = 0;
    }
}

/* Get call trace */
void
getStackTrace(jthread thread, jvmtiFrameInfo *pframes, jint depth, jint *pcount)
{
    jvmtiError error;

    HPROF_ASSERT(thread!=NULL);
    HPROF_ASSERT(pframes!=NULL);
    HPROF_ASSERT(depth >= 0);
    HPROF_ASSERT(pcount!=NULL);
    *pcount = 0;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetStackTrace)
            (gdata->jvmti, thread, 0, depth, pframes, pcount);
    if ( error != JVMTI_ERROR_NONE ) {
        *pcount = 0;
    }
}

void
getThreadListStackTraces(jint count, jthread *threads,
                        jint depth, jvmtiStackInfo **stack_info)
{
    jvmtiError error;

    HPROF_ASSERT(threads!=NULL);
    HPROF_ASSERT(stack_info!=NULL);
    HPROF_ASSERT(depth >= 0);
    HPROF_ASSERT(count > 0);
    *stack_info = NULL;
    error = JVMTI_FUNC_PTR(gdata->jvmti,GetThreadListStackTraces)
            (gdata->jvmti, count, threads, depth, stack_info);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot get thread list stack info");
    }
}

void
followReferences(jvmtiHeapCallbacks *pHeapCallbacks, void *user_data)
{
    jvmtiError error;

    error = JVMTI_FUNC_PTR(gdata->jvmti,FollowReferences)
            (gdata->jvmti, 0, NULL, NULL, pHeapCallbacks, user_data);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot follow references");
    }
}

/* GC control */
void
runGC(void)
{
    jvmtiError error;
    error = JVMTI_FUNC_PTR(gdata->jvmti,ForceGarbageCollection)
                (gdata->jvmti);
    if ( error != JVMTI_ERROR_NONE ) {
        HPROF_JVMTI_ERROR(error, "Cannot force garbage collection");
    }
}

/* ------------------------------------------------------------------- */
/* Getting the initial JVMTI environment */

void
getJvmti(void)
{
    jvmtiEnv         *jvmti = NULL;
    jint              res;
    jint              jvmtiCompileTimeMajorVersion;
    jint              jvmtiCompileTimeMinorVersion;
    jint              jvmtiCompileTimeMicroVersion;

    res = JVM_FUNC_PTR(gdata->jvm,GetEnv)
                (gdata->jvm, (void **)&jvmti, JVMTI_VERSION_1);
    if (res != JNI_OK) {
        char buf[256];

        (void)md_snprintf(buf, sizeof(buf),
                "Unable to access JVMTI Version 1 (0x%x),"
                " is your JDK a 5.0 or newer version?"
                " JNIEnv's GetEnv() returned %d",
               JVMTI_VERSION_1, res);
        buf[sizeof(buf)-1] = 0;
        HPROF_ERROR(JNI_FALSE, buf);
        error_exit_process(1); /* Kill entire process, no core dump */
    }
    gdata->jvmti = jvmti;

    /* Check to make sure the version of jvmti.h we compiled with
     *      matches the runtime version we are using.
     */
    jvmtiCompileTimeMajorVersion  = ( JVMTI_VERSION & JVMTI_VERSION_MASK_MAJOR )
                                        >> JVMTI_VERSION_SHIFT_MAJOR;
    jvmtiCompileTimeMinorVersion  = ( JVMTI_VERSION & JVMTI_VERSION_MASK_MINOR )
                                        >> JVMTI_VERSION_SHIFT_MINOR;
    jvmtiCompileTimeMicroVersion  = ( JVMTI_VERSION & JVMTI_VERSION_MASK_MICRO )
                                        >> JVMTI_VERSION_SHIFT_MICRO;
    if ( !compatible_versions(jvmtiMajorVersion(), jvmtiMinorVersion(),
                jvmtiCompileTimeMajorVersion, jvmtiCompileTimeMinorVersion) ) {
        char buf[256];

        (void)md_snprintf(buf, sizeof(buf),
               "This " AGENTNAME " native library will not work with this VM's "
               "version of JVMTI (%d.%d.%d), it needs JVMTI %d.%d[.%d]."
               ,
               jvmtiMajorVersion(),
               jvmtiMinorVersion(),
               jvmtiMicroVersion(),
               jvmtiCompileTimeMajorVersion,
               jvmtiCompileTimeMinorVersion,
               jvmtiCompileTimeMicroVersion);
        buf[sizeof(buf)-1] = 0;
        HPROF_ERROR(JNI_FALSE, buf);
        error_exit_process(1); /* Kill entire process, no core dump wanted */
    }
}
