/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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


#ifndef HPROF_UTIL_H
#define HPROF_UTIL_H

/* Macros that protect code from accidently using a local ref improperly */
#define WITH_LOCAL_REFS(env, number)            \
    {                                           \
        JNIEnv *_env = (env);                   \
        pushLocalFrame(_env, number);           \
        { /* BEGINNING OF WITH SCOPE */

#define END_WITH_LOCAL_REFS                     \
        } /* END OF WITH SCOPE */               \
        popLocalFrame(_env, NULL);              \
    }

/* Macro to check for exceptions after JNI calls. */
#define CHECK_EXCEPTIONS(env)                                           \
    {                                                                   \
        JNIEnv *_env = (env);                                           \
        jobject _exception;                                             \
        _exception = exceptionOccurred(_env);                           \
        if ( _exception != NULL ) {                                     \
            exceptionDescribe(_env);                                    \
            HPROF_ERROR(JNI_TRUE, "Unexpected Exception found beforehand");\
        }                                                               \
        {

#define END_CHECK_EXCEPTIONS                                            \
        }                                                               \
        _exception = exceptionOccurred(_env);                           \
        if ( _exception != NULL ) {                                     \
            exceptionDescribe(_env);                                    \
            HPROF_ERROR(JNI_TRUE, "Unexpected Exception found afterward");\
        }                                                               \
    }

JNIEnv *   getEnv(void);

/* JNI support functions */
jobject    newGlobalReference(JNIEnv *env, jobject object);
jobject    newWeakGlobalReference(JNIEnv *env, jobject object);
void       deleteGlobalReference(JNIEnv *env, jobject object);
jobject           newLocalReference(JNIEnv *env, jobject object);
void           deleteLocalReference(JNIEnv *env, jobject object);
void       deleteWeakGlobalReference(JNIEnv *env, jobject object);
jclass     getObjectClass(JNIEnv *env, jobject object);
jmethodID  getMethodID(JNIEnv *env, jclass clazz, const char* name,
                        const char *sig);
jclass     getSuperclass(JNIEnv *env, jclass klass);
jmethodID  getStaticMethodID(JNIEnv *env, jclass clazz, const char* name,
                        const char *sig);
jfieldID   getStaticFieldID(JNIEnv *env, jclass clazz, const char* name,
                        const char *sig);
jclass     findClass(JNIEnv *env, const char *name);
void       setStaticIntField(JNIEnv *env, jclass clazz, jfieldID field,
                        jint value);
jboolean   isSameObject(JNIEnv *env, jobject o1, jobject o2);
void       pushLocalFrame(JNIEnv *env, jint capacity);
void       popLocalFrame(JNIEnv *env, jobject ret);
jobject    exceptionOccurred(JNIEnv *env);
void       exceptionDescribe(JNIEnv *env);
void       exceptionClear(JNIEnv *env);
void       registerNatives(JNIEnv *env, jclass clazz,
                        JNINativeMethod *methods, jint count);

/* More JVMTI support functions */
char *    getErrorName(jvmtiError error_number);
jvmtiPhase getPhase(void);
char *    phaseString(jvmtiPhase phase);
void      disposeEnvironment(void);
jlong     getObjectSize(jobject object);
jobject   getClassLoader(jclass klass);
jint      getClassStatus(jclass klass);
jlong     getTag(jobject object);
void      setTag(jobject object, jlong tag);
void      getObjectMonitorUsage(jobject object, jvmtiMonitorUsage *uinfo);
void      getOwnedMonitorInfo(jthread thread, jobject **ppobjects,
                        jint *pcount);
void      getSystemProperty(const char *name, char **value);
void      getClassSignature(jclass klass, char**psignature,
                        char **pgeneric_signature);
void      getSourceFileName(jclass klass, char** src_name_ptr);

jvmtiPrimitiveType sigToPrimType(char *sig);
int       sigToPrimSize(char *sig);
char      primTypeToSigChar(jvmtiPrimitiveType primType);

void      getAllClassFieldInfo(JNIEnv *env, jclass klass,
                        jint* field_count_ptr, FieldInfo** fields_ptr);
void      getMethodName(jmethodID method, char** name_ptr,
                        char** signature_ptr);
void      getMethodClass(jmethodID method, jclass *pclazz);
jboolean  isMethodNative(jmethodID method);
void      getPotentialCapabilities(jvmtiCapabilities *capabilities);
void      addCapabilities(jvmtiCapabilities *capabilities);
void      setEventCallbacks(jvmtiEventCallbacks *pcallbacks);
void      setEventNotificationMode(jvmtiEventMode mode, jvmtiEvent event,
                        jthread thread);
void *    getThreadLocalStorage(jthread thread);
void      setThreadLocalStorage(jthread thread, void *ptr);
void      getThreadState(jthread thread, jint *threadState);
void      getThreadInfo(jthread thread, jvmtiThreadInfo *info);
void      getThreadGroupInfo(jthreadGroup thread_group, jvmtiThreadGroupInfo *info);
void      getLoadedClasses(jclass **ppclasses, jint *pcount);
jint      getLineNumber(jmethodID method, jlocation location);
jlong     getMaxMemory(JNIEnv *env);
void      createAgentThread(JNIEnv *env, const char *name,
                        jvmtiStartFunction func);
jlong     getThreadCpuTime(jthread thread);
void      getStackTrace(jthread thread, jvmtiFrameInfo *pframes, jint depth,
                        jint *pcount);
void      getThreadListStackTraces(jint count, jthread *threads,
                        jint depth, jvmtiStackInfo **stack_info);
void      getFrameCount(jthread thread, jint *pcount);
void      followReferences(jvmtiHeapCallbacks *pHeapCallbacks, void *user_data);

/* GC control */
void      runGC(void);

/* Get initial JVMTI environment */
void      getJvmti(void);

/* Get current runtime JVMTI version */
jint      jvmtiVersion(void);

/* Raw monitor functions */
jrawMonitorID createRawMonitor(const char *str);
void          rawMonitorEnter(jrawMonitorID m);
void          rawMonitorWait(jrawMonitorID m, jlong pause_time);
void          rawMonitorNotifyAll(jrawMonitorID m);
void          rawMonitorExit(jrawMonitorID m);
void          destroyRawMonitor(jrawMonitorID m);

/* JVMTI alloc/dealloc */
void *        jvmtiAllocate(int size);
void          jvmtiDeallocate(void *ptr);

/* System malloc/free */
void *        hprof_malloc(int size);
void          hprof_free(void *ptr);

#include "debug_malloc.h"

#ifdef DEBUG
    void *        hprof_debug_malloc(int size, char *file, int line);
    void          hprof_debug_free(void *ptr, char *file, int line);
    #define HPROF_MALLOC(size)  hprof_debug_malloc(size, __FILE__, __LINE__)
    #define HPROF_FREE(ptr)     hprof_debug_free(ptr, __FILE__, __LINE__)
#else
    #define HPROF_MALLOC(size)  hprof_malloc(size)
    #define HPROF_FREE(ptr)     hprof_free(ptr)
#endif

#endif
