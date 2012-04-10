/*
 * Copyright (c) 2004, 2010, Oracle and/or its affiliates. All rights reserved.
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


#include "stdlib.h"

#include "heapTracker.h"
#include "java_crw_demo.h"

#include "jni.h"
#include "jvmti.h"

#include "agent_util.h"

/* -------------------------------------------------------------------
 * Some constant names that tie to Java class/method names.
 *    We assume the Java class whose static methods we will be calling
 *    looks like:
 *
 * public class HeapTracker {
 *     private static int engaged;
 *     private static native void _newobj(Object thr, Object o);
 *     public static void newobj(Object o)
 *     {
 *              if ( engaged != 0 ) {
 *               _newobj(Thread.currentThread(), o);
 *           }
 *     }
 *     private static native void _newarr(Object thr, Object a);
 *     public static void newarr(Object a)
 *     {
 *            if ( engaged != 0 ) {
 *               _newarr(Thread.currentThread(), a);
 *           }
 *     }
 * }
 *
 *    The engaged field allows us to inject all classes (even system classes)
 *    and delay the actual calls to the native code until the VM has reached
 *    a safe time to call native methods (Past the JVMTI VM_START event).
 *
 */

#define HEAP_TRACKER_class           HeapTracker /* Name of class we are using */
#define HEAP_TRACKER_newobj        newobj   /* Name of java init method */
#define HEAP_TRACKER_newarr        newarr   /* Name of java newarray method */
#define HEAP_TRACKER_native_newobj _newobj  /* Name of java newobj native */
#define HEAP_TRACKER_native_newarr _newarr  /* Name of java newarray native */
#define HEAP_TRACKER_engaged       engaged  /* Name of static field switch */

/* C macros to create strings from tokens */
#define _STRING(s) #s
#define STRING(s) _STRING(s)

/* ------------------------------------------------------------------- */

/* Flavors of traces (to separate out stack traces) */

typedef enum {
    TRACE_FIRST                        = 0,
    TRACE_USER                        = 0,
    TRACE_BEFORE_VM_START        = 1,
    TRACE_BEFORE_VM_INIT        = 2,
    TRACE_VM_OBJECT                = 3,
    TRACE_MYSTERY                = 4,
    TRACE_LAST                        = 4
} TraceFlavor;

static char * flavorDesc[] = {
    "",
    "before VM_START",
    "before VM_INIT",
    "VM_OBJECT",
    "unknown"
};

/* Trace (Stack Trace) */

#define MAX_FRAMES 6
typedef struct Trace {
    /* Number of frames (includes HEAP_TRACKER methods) */
    jint           nframes;
    /* Frames from GetStackTrace() (2 extra for HEAP_TRACKER methods) */
    jvmtiFrameInfo frames[MAX_FRAMES+2];
    /* Used to make some traces unique */
    TraceFlavor    flavor;
} Trace;

/* Trace information (more than one object will have this as a tag) */

typedef struct TraceInfo {
    /* Trace where this object was allocated from */
    Trace             trace;
    /* 64 bit hash code that attempts to identify this specific trace */
    jlong             hashCode;
    /* Total space taken up by objects allocated from this trace */
    jlong             totalSpace;
    /* Total count of objects ever allocated from this trace */
    int               totalCount;
    /* Total live objects that were allocated from this trace */
    int               useCount;
    /* The next TraceInfo in the hash bucket chain */
    struct TraceInfo *next;
} TraceInfo;

/* Global agent data structure */

typedef struct {
    /* JVMTI Environment */
    jvmtiEnv      *jvmti;
    /* State of the VM flags */
    jboolean       vmStarted;
    jboolean       vmInitialized;
    jboolean       vmDead;
    /* Options */
    int            maxDump;
    /* Data access Lock */
    jrawMonitorID  lock;
    /* Counter on classes where BCI has been applied */
    jint           ccount;
    /* Hash table to lookup TraceInfo's via Trace's */
    #define HASH_INDEX_BIT_WIDTH 12 /* 4096 */
    #define HASH_BUCKET_COUNT (1<<HASH_INDEX_BIT_WIDTH)
    #define HASH_INDEX_MASK (HASH_BUCKET_COUNT-1)
    TraceInfo     *hashBuckets[HASH_BUCKET_COUNT];
    /* Count of TraceInfo's allocated */
    int            traceInfoCount;
    /* Pre-defined traces for the system and mystery situations */
    TraceInfo     *emptyTrace[TRACE_LAST+1];
} GlobalAgentData;

static GlobalAgentData *gdata;

/* Enter a critical section by doing a JVMTI Raw Monitor Enter */
static void
enterCriticalSection(jvmtiEnv *jvmti)
{
    jvmtiError error;

    error = (*jvmti)->RawMonitorEnter(jvmti, gdata->lock);
    check_jvmti_error(jvmti, error, "Cannot enter with raw monitor");
}

/* Exit a critical section by doing a JVMTI Raw Monitor Exit */
static void
exitCriticalSection(jvmtiEnv *jvmti)
{
    jvmtiError error;

    error = (*jvmti)->RawMonitorExit(jvmti, gdata->lock);
    check_jvmti_error(jvmti, error, "Cannot exit with raw monitor");
}

/* Update stats on a TraceInfo */
static TraceInfo *
updateStats(TraceInfo *tinfo)
{
    tinfo->totalCount++;
    tinfo->useCount++;
    return tinfo;
}

/* Get TraceInfo for empty stack */
static TraceInfo *
emptyTrace(TraceFlavor flavor)
{
    return updateStats(gdata->emptyTrace[flavor]);
}

/* Allocate new TraceInfo */
static TraceInfo *
newTraceInfo(Trace *trace, jlong hashCode, TraceFlavor flavor)
{
    TraceInfo *tinfo;

    tinfo = (TraceInfo*)calloc(1, sizeof(TraceInfo));
    if ( tinfo == NULL ) {
        fatal_error("ERROR: Ran out of malloc() space\n");
    } else {
        int hashIndex;

        tinfo->trace = *trace;
        tinfo->trace.flavor = flavor;
        tinfo->hashCode = hashCode;
        gdata->traceInfoCount++;
        hashIndex = (int)(hashCode & HASH_INDEX_MASK);
        tinfo->next = gdata->hashBuckets[hashIndex];
        gdata->hashBuckets[hashIndex] = tinfo;
    }
    return tinfo;
}

/* Create hash code for a Trace */
static jlong
hashTrace(Trace *trace)
{
    jlong hashCode;
    int   i;

    hashCode = 0;
    for ( i = 0 ; i < trace->nframes ; i++ ) {
        hashCode = (hashCode << 3) +
                (jlong)(ptrdiff_t)(void*)(trace->frames[i].method);
        hashCode = (hashCode << 2) +
                (jlong)(trace->frames[i].location);
    }
    hashCode = (hashCode << 3) + trace->nframes;
    hashCode += trace->flavor;
    return hashCode;
}

/* Lookup or create a new TraceInfo */
static TraceInfo *
lookupOrEnter(jvmtiEnv *jvmti, Trace *trace, TraceFlavor flavor)
{
    TraceInfo *tinfo;
    jlong      hashCode;

    /* Calculate hash code (outside critical section to lessen contention) */
    hashCode = hashTrace(trace);

    /* Do a lookup in the hash table */
    enterCriticalSection(jvmti); {
        TraceInfo *prev;
        int        hashIndex;

        /* Start with first item in hash buck chain */
        prev = NULL;
        hashIndex = (int)(hashCode & HASH_INDEX_MASK);
        tinfo = gdata->hashBuckets[hashIndex];
        while ( tinfo != NULL ) {
            if ( tinfo->hashCode == hashCode &&
                 memcmp(trace, &(tinfo->trace), sizeof(Trace))==0 ) {
                 /* We found one that matches, move to head of bucket chain */
                 if ( prev != NULL ) {
                     /* Remove from list and add to head of list */
                     prev->next = tinfo->next;
                     tinfo->next = gdata->hashBuckets[hashIndex];
                     gdata->hashBuckets[hashIndex] = tinfo;
                 }
                 /* Break out */
                 break;
            }
            prev = tinfo;
            tinfo = tinfo->next;
        }

        /* If we didn't find anything we need to enter a new entry */
        if ( tinfo == NULL ) {
            /* Create new hash table element */
            tinfo = newTraceInfo(trace, hashCode, flavor);
        }

        /* Update stats */
        (void)updateStats(tinfo);

    } exitCriticalSection(jvmti);

    return tinfo;
}

/* Get TraceInfo for this allocation */
static TraceInfo *
findTraceInfo(jvmtiEnv *jvmti, jthread thread, TraceFlavor flavor)
{
    TraceInfo *tinfo;
    jvmtiError error;

    tinfo = NULL;
    if ( thread != NULL ) {
        static Trace  empty;
        Trace         trace;

        /* Before VM_INIT thread could be NULL, watch out */
        trace = empty;
        error = (*jvmti)->GetStackTrace(jvmti, thread, 0, MAX_FRAMES+2,
                            trace.frames, &(trace.nframes));
        /* If we get a PHASE error, the VM isn't ready, or it died */
        if ( error == JVMTI_ERROR_WRONG_PHASE ) {
            /* It is assumed this is before VM_INIT */
            if ( flavor == TRACE_USER ) {
                tinfo = emptyTrace(TRACE_BEFORE_VM_INIT);
            } else {
                tinfo = emptyTrace(flavor);
            }
        } else {
            check_jvmti_error(jvmti, error, "Cannot get stack trace");
            /* Lookup this entry */
            tinfo = lookupOrEnter(jvmti, &trace, flavor);
        }
    } else {
        /* If thread==NULL, it's assumed this is before VM_START */
        if ( flavor == TRACE_USER ) {
            tinfo = emptyTrace(TRACE_BEFORE_VM_START);
        } else {
            tinfo = emptyTrace(flavor);
        }
    }
    return tinfo;
}

/* Tag an object with a TraceInfo pointer. */
static void
tagObjectWithTraceInfo(jvmtiEnv *jvmti, jobject object, TraceInfo *tinfo)
{
    jvmtiError error;
    jlong      tag;

    /* Tag this object with this TraceInfo pointer */
    tag = (jlong)(ptrdiff_t)(void*)tinfo;
    error = (*jvmti)->SetTag(jvmti, object, tag);
    check_jvmti_error(jvmti, error, "Cannot tag object");
}

/* Java Native Method for Object.<init> */
static void JNICALL
HEAP_TRACKER_native_newobj(JNIEnv *env, jclass klass, jthread thread, jobject o)
{
    TraceInfo *tinfo;

    if ( gdata->vmDead ) {
        return;
    }
    tinfo = findTraceInfo(gdata->jvmti, thread, TRACE_USER);
    tagObjectWithTraceInfo(gdata->jvmti, o, tinfo);
}

/* Java Native Method for newarray */
static void JNICALL
HEAP_TRACKER_native_newarr(JNIEnv *env, jclass klass, jthread thread, jobject a)
{
    TraceInfo *tinfo;

    if ( gdata->vmDead ) {
        return;
    }
    tinfo = findTraceInfo(gdata->jvmti, thread, TRACE_USER);
    tagObjectWithTraceInfo(gdata->jvmti, a, tinfo);
}

/* Callback for JVMTI_EVENT_VM_START */
static void JNICALL
cbVMStart(jvmtiEnv *jvmti, JNIEnv *env)
{
    enterCriticalSection(jvmti); {
        jclass klass;
        jfieldID field;
        jint rc;

        /* Java Native Methods for class */
        static JNINativeMethod registry[2] = {
            {STRING(HEAP_TRACKER_native_newobj), "(Ljava/lang/Object;Ljava/lang/Object;)V",
                (void*)&HEAP_TRACKER_native_newobj},
            {STRING(HEAP_TRACKER_native_newarr), "(Ljava/lang/Object;Ljava/lang/Object;)V",
                (void*)&HEAP_TRACKER_native_newarr}
        };

        /* Register Natives for class whose methods we use */
        klass = (*env)->FindClass(env, STRING(HEAP_TRACKER_class));
        if ( klass == NULL ) {
            fatal_error("ERROR: JNI: Cannot find %s with FindClass\n",
                        STRING(HEAP_TRACKER_class));
        }
        rc = (*env)->RegisterNatives(env, klass, registry, 2);
        if ( rc != 0 ) {
            fatal_error("ERROR: JNI: Cannot register natives for class %s\n",
                        STRING(HEAP_TRACKER_class));
        }

        /* Engage calls. */
        field = (*env)->GetStaticFieldID(env, klass, STRING(HEAP_TRACKER_engaged), "I");
        if ( field == NULL ) {
            fatal_error("ERROR: JNI: Cannot get field from %s\n",
                        STRING(HEAP_TRACKER_class));
        }
        (*env)->SetStaticIntField(env, klass, field, 1);

        /* Indicate VM has started */
        gdata->vmStarted = JNI_TRUE;

    } exitCriticalSection(jvmti);
}

/* Iterate Through Heap callback */
static jint JNICALL
cbObjectTagger(jlong class_tag, jlong size, jlong* tag_ptr, jint length,
               void *user_data)
{
    TraceInfo *tinfo;

    tinfo = emptyTrace(TRACE_BEFORE_VM_INIT);
    *tag_ptr = (jlong)(ptrdiff_t)(void*)tinfo;
    return JVMTI_VISIT_OBJECTS;
}

/* Callback for JVMTI_EVENT_VM_INIT */
static void JNICALL
cbVMInit(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    jvmtiHeapCallbacks heapCallbacks;
    jvmtiError         error;

    /* Iterate through heap, find all untagged objects allocated before this */
    (void)memset(&heapCallbacks, 0, sizeof(heapCallbacks));
    heapCallbacks.heap_iteration_callback = &cbObjectTagger;
    error = (*jvmti)->IterateThroughHeap(jvmti, JVMTI_HEAP_FILTER_TAGGED,
                                         NULL, &heapCallbacks, NULL);
    check_jvmti_error(jvmti, error, "Cannot iterate through heap");

    enterCriticalSection(jvmti); {

        /* Indicate VM is initialized */
        gdata->vmInitialized = JNI_TRUE;

    } exitCriticalSection(jvmti);
}

/* Iterate Through Heap callback */
static jint JNICALL
cbObjectSpaceCounter(jlong class_tag, jlong size, jlong* tag_ptr, jint length,
                     void *user_data)
{
    TraceInfo *tinfo;

    tinfo = (TraceInfo*)(ptrdiff_t)(*tag_ptr);
    if ( tinfo == NULL ) {
        tinfo = emptyTrace(TRACE_MYSTERY);
        *tag_ptr = (jlong)(ptrdiff_t)(void*)tinfo;
    }
    tinfo->totalSpace += size;
    return JVMTI_VISIT_OBJECTS;
}

/* Qsort compare function */
static int
compareInfo(const void *p1, const void *p2)
{
    TraceInfo *tinfo1, *tinfo2;

    tinfo1 = *((TraceInfo**)p1);
    tinfo2 = *((TraceInfo**)p2);
    return (int)(tinfo2->totalSpace - tinfo1->totalSpace);
}

/* Frame to text */
static void
frameToString(jvmtiEnv *jvmti, char *buf, int buflen, jvmtiFrameInfo *finfo)
{
    jvmtiError           error;
    jclass               klass;
    char                *signature;
    char                *methodname;
    char                *methodsig;
    jboolean             isNative;
    char                *filename;
    int                  lineCount;
    jvmtiLineNumberEntry*lineTable;
    int                  lineNumber;

    /* Initialize defaults */
    buf[0]     = 0;
    klass      = NULL;
    signature  = NULL;
    methodname = NULL;
    methodsig  = NULL;
    isNative   = JNI_FALSE;
    filename   = NULL;
    lineCount  = 0;
    lineTable  = NULL;
    lineNumber = 0;

    /* Get jclass object for the jmethodID */
    error = (*jvmti)->GetMethodDeclaringClass(jvmti, finfo->method, &klass);
    check_jvmti_error(jvmti, error, "Cannot get method's class");

    /* Get the class signature */
    error = (*jvmti)->GetClassSignature(jvmti, klass, &signature, NULL);
    check_jvmti_error(jvmti, error, "Cannot get class signature");

    /* Skip all this if it's our own Tracker method */
    if ( strcmp(signature, "L" STRING(HEAP_TRACKER_class) ";" ) == 0 ) {
        deallocate(jvmti, signature);
        return;
    }

    /* Get the name and signature for the method */
    error = (*jvmti)->GetMethodName(jvmti, finfo->method,
                &methodname, &methodsig, NULL);
    check_jvmti_error(jvmti, error, "Cannot method name");

    /* Check to see if it's a native method, which means no lineNumber */
    error = (*jvmti)->IsMethodNative(jvmti, finfo->method, &isNative);
    check_jvmti_error(jvmti, error, "Cannot get method native status");

    /* Get source file name */
    error = (*jvmti)->GetSourceFileName(jvmti, klass, &filename);
    if ( error != JVMTI_ERROR_NONE && error != JVMTI_ERROR_ABSENT_INFORMATION ) {
        check_jvmti_error(jvmti, error, "Cannot get source filename");
    }

    /* Get lineNumber if we can */
    if ( !isNative ) {
        int i;

        /* Get method line table */
        error = (*jvmti)->GetLineNumberTable(jvmti, finfo->method, &lineCount, &lineTable);
        if ( error == JVMTI_ERROR_NONE ) {
            /* Search for line */
            lineNumber = lineTable[0].line_number;
            for ( i = 1 ; i < lineCount ; i++ ) {
                if ( finfo->location < lineTable[i].start_location ) {
                    break;
                }
                lineNumber = lineTable[i].line_number;
            }
        } else if ( error != JVMTI_ERROR_ABSENT_INFORMATION ) {
            check_jvmti_error(jvmti, error, "Cannot get method line table");
        }
    }

    /* Create string for this frame location.
     *    NOTE: These char* quantities are mUTF (Modified UTF-8) bytes
     *          and should actually be converted to the default system
     *          character encoding. Sending them to things like
     *          printf() without converting them is actually an I18n
     *          (Internationalization) error.
     */
    (void)sprintf(buf, "%s.%s@%d[%s:%d]",
            (signature==NULL?"UnknownClass":signature),
            (methodname==NULL?"UnknownMethod":methodname),
            (int)finfo->location,
            (filename==NULL?"UnknownFile":filename),
            lineNumber);

    /* Free up JVMTI space allocated by the above calls */
    deallocate(jvmti, signature);
    deallocate(jvmti, methodname);
    deallocate(jvmti, methodsig);
    deallocate(jvmti, filename);
    deallocate(jvmti, lineTable);
}

/* Print the information */
static void
printTraceInfo(jvmtiEnv *jvmti, int index, TraceInfo* tinfo)
{
    if ( tinfo == NULL ) {
        fatal_error("%d: NULL ENTRY ERROR\n", index);
        return;
    }

    stdout_message("%2d: %7d bytes %5d objects %5d live %s",
                index, (int)tinfo->totalSpace, tinfo->totalCount,
                tinfo->useCount, flavorDesc[tinfo->trace.flavor]);

    if (  tinfo->trace.nframes > 0 ) {
        int i;
        int fcount;

        fcount = 0;
        stdout_message(" stack=(");
        for ( i = 0 ; i < tinfo->trace.nframes ; i++ ) {
            char buf[4096];

            frameToString(jvmti, buf, (int)sizeof(buf), tinfo->trace.frames+i);
            if ( buf[0] == 0 ) {
                continue; /* Skip the ones that are from Tracker class */
            }
            fcount++;
            stdout_message("%s", buf);
            if ( i < (tinfo->trace.nframes-1) ) {
                stdout_message(",");
            }
        }
        stdout_message(") nframes=%d\n", fcount);
    } else {
        stdout_message(" stack=<empty>\n");
    }
}

/* Callback for JVMTI_EVENT_VM_DEATH */
static void JNICALL
cbVMDeath(jvmtiEnv *jvmti, JNIEnv *env)
{
    jvmtiHeapCallbacks heapCallbacks;
    jvmtiError         error;

    /* These are purposely done outside the critical section */

    /* Force garbage collection now so we get our ObjectFree calls */
    error = (*jvmti)->ForceGarbageCollection(jvmti);
    check_jvmti_error(jvmti, error, "Cannot force garbage collection");

    /* Iterate through heap and find all objects */
    (void)memset(&heapCallbacks, 0, sizeof(heapCallbacks));
    heapCallbacks.heap_iteration_callback = &cbObjectSpaceCounter;
    error = (*jvmti)->IterateThroughHeap(jvmti, 0, NULL, &heapCallbacks, NULL);
    check_jvmti_error(jvmti, error, "Cannot iterate through heap");

    /* Process VM Death */
    enterCriticalSection(jvmti); {
        jclass              klass;
        jfieldID            field;
        jvmtiEventCallbacks callbacks;

        /* Disengage calls in HEAP_TRACKER_class. */
        klass = (*env)->FindClass(env, STRING(HEAP_TRACKER_class));
        if ( klass == NULL ) {
            fatal_error("ERROR: JNI: Cannot find %s with FindClass\n",
                        STRING(HEAP_TRACKER_class));
        }
        field = (*env)->GetStaticFieldID(env, klass, STRING(HEAP_TRACKER_engaged), "I");
        if ( field == NULL ) {
            fatal_error("ERROR: JNI: Cannot get field from %s\n",
                        STRING(HEAP_TRACKER_class));
        }
        (*env)->SetStaticIntField(env, klass, field, 0);

        /* The critical section here is important to hold back the VM death
         *    until all other callbacks have completed.
         */

        /* Clear out all callbacks. */
        (void)memset(&callbacks,0, sizeof(callbacks));
        error = (*jvmti)->SetEventCallbacks(jvmti, &callbacks,
                                            (jint)sizeof(callbacks));
        check_jvmti_error(jvmti, error, "Cannot set jvmti callbacks");

        /* Since this critical section could be holding up other threads
         *   in other event callbacks, we need to indicate that the VM is
         *   dead so that the other callbacks can short circuit their work.
         *   We don't expect an further events after VmDeath but we do need
         *   to be careful that existing threads might be in our own agent
         *   callback code.
         */
        gdata->vmDead = JNI_TRUE;

        /* Dump all objects */
        if ( gdata->traceInfoCount > 0 ) {
            TraceInfo **list;
            int         count;
            int         i;

            stdout_message("Dumping heap trace information\n");

            /* Create single array of pointers to TraceInfo's, sort, and
             *   print top gdata->maxDump top space users.
             */
            list = (TraceInfo**)calloc(gdata->traceInfoCount,
                                              sizeof(TraceInfo*));
            if ( list == NULL ) {
                fatal_error("ERROR: Ran out of malloc() space\n");
            }
            count = 0;
            for ( i = 0 ; i < HASH_BUCKET_COUNT ; i++ ) {
                TraceInfo *tinfo;

                tinfo = gdata->hashBuckets[i];
                while ( tinfo != NULL ) {
                    if ( count < gdata->traceInfoCount ) {
                        list[count++] = tinfo;
                    }
                    tinfo = tinfo->next;
                }
            }
            if ( count != gdata->traceInfoCount ) {
                fatal_error("ERROR: Count found by iterate doesn't match ours:"
                        " count=%d != traceInfoCount==%d\n",
                        count, gdata->traceInfoCount);
            }
            qsort(list, count, sizeof(TraceInfo*), &compareInfo);
            for ( i = 0 ; i < count ; i++ ) {
                if ( i >= gdata->maxDump ) {
                    break;
                }
                printTraceInfo(jvmti, i+1, list[i]);
            }
            (void)free(list);
        }

    } exitCriticalSection(jvmti);

}

/* Callback for JVMTI_EVENT_VM_OBJECT_ALLOC */
static void JNICALL
cbVMObjectAlloc(jvmtiEnv *jvmti, JNIEnv *env, jthread thread,
                jobject object, jclass object_klass, jlong size)
{
    TraceInfo *tinfo;

    if ( gdata->vmDead ) {
        return;
    }
    tinfo = findTraceInfo(jvmti, thread, TRACE_VM_OBJECT);
    tagObjectWithTraceInfo(jvmti, object, tinfo);
}

/* Callback for JVMTI_EVENT_OBJECT_FREE */
static void JNICALL
cbObjectFree(jvmtiEnv *jvmti, jlong tag)
{
    TraceInfo *tinfo;

    if ( gdata->vmDead ) {
        return;
    }

    /* The object tag is actually a pointer to a TraceInfo structure */
    tinfo = (TraceInfo*)(void*)(ptrdiff_t)tag;

    /* Decrement the use count */
    tinfo->useCount--;
}

/* Callback for JVMTI_EVENT_CLASS_FILE_LOAD_HOOK */
static void JNICALL
cbClassFileLoadHook(jvmtiEnv *jvmti, JNIEnv* env,
                jclass class_being_redefined, jobject loader,
                const char* name, jobject protection_domain,
                jint class_data_len, const unsigned char* class_data,
                jint* new_class_data_len, unsigned char** new_class_data)
{
    enterCriticalSection(jvmti); {
        /* It's possible we get here right after VmDeath event, be careful */
        if ( !gdata->vmDead ) {

            const char * classname;

            /* Name can be NULL, make sure we avoid SEGV's */
            if ( name == NULL ) {
                classname = java_crw_demo_classname(class_data, class_data_len,
                                NULL);
                if ( classname == NULL ) {
                    fatal_error("ERROR: No classname in classfile\n");
                }
            } else {
                classname = strdup(name);
                if ( classname == NULL ) {
                    fatal_error("ERROR: Ran out of malloc() space\n");
                }
            }

            *new_class_data_len = 0;
            *new_class_data     = NULL;

            /* The tracker class itself? */
            if ( strcmp(classname, STRING(HEAP_TRACKER_class)) != 0 ) {
                jint           cnum;
                int            systemClass;
                unsigned char *newImage;
                long           newLength;

                /* Get number for every class file image loaded */
                cnum = gdata->ccount++;

                /* Is it a system class? If the class load is before VmStart
                 *   then we will consider it a system class that should
                 *   be treated carefully. (See java_crw_demo)
                 */
                systemClass = 0;
                if ( !gdata->vmStarted ) {
                    systemClass = 1;
                }

                newImage = NULL;
                newLength = 0;

                /* Call the class file reader/write demo code */
                java_crw_demo(cnum,
                    classname,
                    class_data,
                    class_data_len,
                    systemClass,
                    STRING(HEAP_TRACKER_class),
                    "L" STRING(HEAP_TRACKER_class) ";",
                    NULL, NULL,
                    NULL, NULL,
                    STRING(HEAP_TRACKER_newobj), "(Ljava/lang/Object;)V",
                    STRING(HEAP_TRACKER_newarr), "(Ljava/lang/Object;)V",
                    &newImage,
                    &newLength,
                    NULL,
                    NULL);

                /* If we got back a new class image, return it back as "the"
                 *   new class image. This must be JVMTI Allocate space.
                 */
                if ( newLength > 0 ) {
                    unsigned char *jvmti_space;

                    jvmti_space = (unsigned char *)allocate(jvmti, (jint)newLength);
                    (void)memcpy((void*)jvmti_space, (void*)newImage, (int)newLength);
                    *new_class_data_len = (jint)newLength;
                    *new_class_data     = jvmti_space; /* VM will deallocate */
                }

                /* Always free up the space we get from java_crw_demo() */
                if ( newImage != NULL ) {
                    (void)free((void*)newImage); /* Free malloc() space with free() */
                }
            }

            (void)free((void*)classname);
        }
    } exitCriticalSection(jvmti);
}

/* Parse the options for this heapTracker agent */
static void
parse_agent_options(char *options)
{
    #define MAX_TOKEN_LENGTH        16
    char  token[MAX_TOKEN_LENGTH];
    char *next;

    /* Defaults */
    gdata->maxDump = 20;

    /* Parse options and set flags in gdata */
    if ( options==NULL ) {
        return;
    }

    /* Get the first token from the options string. */
    next = get_token(options, ",=", token, (int)sizeof(token));

    /* While not at the end of the options string, process this option. */
    while ( next != NULL ) {
        if ( strcmp(token,"help")==0 ) {
            stdout_message("The heapTracker JVMTI demo agent\n");
            stdout_message("\n");
            stdout_message(" java -agent:heapTracker[=options] ...\n");
            stdout_message("\n");
            stdout_message("The options are comma separated:\n");
            stdout_message("\t help\t\t\t Print help information\n");
            stdout_message("\t maxDump=n\t\t\t How many TraceInfo's to dump\n");
            stdout_message("\n");
            exit(0);
        } else if ( strcmp(token,"maxDump")==0 ) {
            char  number[MAX_TOKEN_LENGTH];

            next = get_token(next, ",=", number, (int)sizeof(number));
            if ( next == NULL ) {
                fatal_error("ERROR: Cannot parse maxDump=number: %s\n", options);
            }
            gdata->maxDump = atoi(number);
        } else if ( token[0]!=0 ) {
            /* We got a non-empty token and we don't know what it is. */
            fatal_error("ERROR: Unknown option: %s\n", token);
        }
        /* Get the next token (returns NULL if there are no more) */
        next = get_token(next, ",=", token, (int)sizeof(token));
    }
}

/* Agent_OnLoad: This is called immediately after the shared library is
 *   loaded. This is the first code executed.
 */
JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
{
    static GlobalAgentData data;
    jvmtiEnv              *jvmti;
    jvmtiError             error;
    jint                   res;
    TraceFlavor            flavor;
    jvmtiCapabilities      capabilities;
    jvmtiEventCallbacks    callbacks;
    static Trace           empty;

    /* Setup initial global agent data area
     *   Use of static/extern data should be handled carefully here.
     *   We need to make sure that we are able to cleanup after ourselves
     *     so anything allocated in this library needs to be freed in
     *     the Agent_OnUnload() function.
     */
    (void)memset((void*)&data, 0, sizeof(data));
    gdata = &data;

    /* First thing we need to do is get the jvmtiEnv* or JVMTI environment */
    res = (*vm)->GetEnv(vm, (void **)&jvmti, JVMTI_VERSION_1);
    if (res != JNI_OK) {
        /* This means that the VM was unable to obtain this version of the
         *   JVMTI interface, this is a fatal error.
         */
        fatal_error("ERROR: Unable to access JVMTI Version 1 (0x%x),"
                " is your JDK a 5.0 or newer version?"
                " JNIEnv's GetEnv() returned %d\n",
               JVMTI_VERSION_1, res);
    }

    /* Here we save the jvmtiEnv* for Agent_OnUnload(). */
    gdata->jvmti = jvmti;

    /* Parse any options supplied on java command line */
    parse_agent_options(options);

    /* Immediately after getting the jvmtiEnv* we need to ask for the
     *   capabilities this agent will need.
     */
    (void)memset(&capabilities,0, sizeof(capabilities));
    capabilities.can_generate_all_class_hook_events = 1;
    capabilities.can_tag_objects  = 1;
    capabilities.can_generate_object_free_events  = 1;
    capabilities.can_get_source_file_name  = 1;
    capabilities.can_get_line_numbers  = 1;
    capabilities.can_generate_vm_object_alloc_events  = 1;
    error = (*jvmti)->AddCapabilities(jvmti, &capabilities);
    check_jvmti_error(jvmti, error, "Unable to get necessary JVMTI capabilities.");

    /* Next we need to provide the pointers to the callback functions to
     *   to this jvmtiEnv*
     */
    (void)memset(&callbacks,0, sizeof(callbacks));
    /* JVMTI_EVENT_VM_START */
    callbacks.VMStart           = &cbVMStart;
    /* JVMTI_EVENT_VM_INIT */
    callbacks.VMInit            = &cbVMInit;
    /* JVMTI_EVENT_VM_DEATH */
    callbacks.VMDeath           = &cbVMDeath;
    /* JVMTI_EVENT_OBJECT_FREE */
    callbacks.ObjectFree        = &cbObjectFree;
    /* JVMTI_EVENT_VM_OBJECT_ALLOC */
    callbacks.VMObjectAlloc     = &cbVMObjectAlloc;
    /* JVMTI_EVENT_CLASS_FILE_LOAD_HOOK */
    callbacks.ClassFileLoadHook = &cbClassFileLoadHook;
    error = (*jvmti)->SetEventCallbacks(jvmti, &callbacks, (jint)sizeof(callbacks));
    check_jvmti_error(jvmti, error, "Cannot set jvmti callbacks");

    /* At first the only initial events we are interested in are VM
     *   initialization, VM death, and Class File Loads.
     *   Once the VM is initialized we will request more events.
     */
    error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                          JVMTI_EVENT_VM_START, (jthread)NULL);
    check_jvmti_error(jvmti, error, "Cannot set event notification");
    error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                          JVMTI_EVENT_VM_INIT, (jthread)NULL);
    check_jvmti_error(jvmti, error, "Cannot set event notification");
    error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                          JVMTI_EVENT_VM_DEATH, (jthread)NULL);
    check_jvmti_error(jvmti, error, "Cannot set event notification");
    error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                          JVMTI_EVENT_OBJECT_FREE, (jthread)NULL);
    check_jvmti_error(jvmti, error, "Cannot set event notification");
    error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                          JVMTI_EVENT_VM_OBJECT_ALLOC, (jthread)NULL);
    check_jvmti_error(jvmti, error, "Cannot set event notification");
    error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                          JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, (jthread)NULL);
    check_jvmti_error(jvmti, error, "Cannot set event notification");

    /* Here we create a raw monitor for our use in this agent to
     *   protect critical sections of code.
     */
    error = (*jvmti)->CreateRawMonitor(jvmti, "agent data", &(gdata->lock));
    check_jvmti_error(jvmti, error, "Cannot create raw monitor");

    /* Create the TraceInfo for various flavors of empty traces */
    for ( flavor = TRACE_FIRST ; flavor <= TRACE_LAST ; flavor++ ) {
        gdata->emptyTrace[flavor] =
               newTraceInfo(&empty, hashTrace(&empty), flavor);
    }

    /* Add jar file to boot classpath */
    add_demo_jar_to_bootclasspath(jvmti, "heapTracker");

    /* We return JNI_OK to signify success */
    return JNI_OK;
}

/* Agent_OnUnload: This is called immediately before the shared library is
 *   unloaded. This is the last code executed.
 */
JNIEXPORT void JNICALL
Agent_OnUnload(JavaVM *vm)
{
    /* Skip any cleanup, VM is about to die anyway */
}
