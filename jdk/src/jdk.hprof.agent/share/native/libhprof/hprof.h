/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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


/* Primary hprof #include file, should be included by most if not
 *    all hprof source files. Gives access to the global data structure
 *    and all global macros, and everything declared in the #include
 *    files of each of the source files.
 */

#ifndef HPROF_H
#define HPROF_H

/* Standard C functions used throughout. */

#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <string.h>
#include <stddef.h>
#include <stdarg.h>
#include <limits.h>
#include <time.h>
#include <errno.h>

/* General JVM/Java functions, types and macros. */

#include <sys/types.h>
#include "jni.h"
#include "jvmti.h"
#include "classfile_constants.h"
#include "jvm_md.h"

/* Macros to extract the upper and lower 32 bits of a jlong */

#define jlong_high(a)    ((jint)((a)>>32))
#define jlong_low(a)     ((jint)(a))
#define jlong_to_jint(a)  ((jint)(a))
#define jint_to_jlong(a) ((jlong)(a))

#define jlong_add(a, b) ((a) + (b))


/* The type used to contain a generic 32bit "serial number". */

typedef unsigned SerialNumber;

/* How the options get to OnLoad: */

#define AGENTNAME               "hprof"
#define XRUN                    "-Xrun" AGENTNAME
#define AGENTLIB                "-agentlib:" AGENTNAME

/* Name of prelude file, found at runtime relative to java binary location */

#define PRELUDE_FILE            "jvm.hprof.txt"

/* File I/O buffer size to be used with any file i/o operation */

#define FILE_IO_BUFFER_SIZE     (1024*64)

/* Machine dependent functions. */

#include "hprof_md.h"

/* Table index types */

typedef unsigned   TableIndex;
typedef TableIndex ClassIndex;
typedef TableIndex FrameIndex;
typedef TableIndex IoNameIndex;
typedef TableIndex MonitorIndex;
typedef TableIndex ObjectIndex;
typedef TableIndex LoaderIndex;
typedef TableIndex RefIndex;
typedef TableIndex SiteIndex;
typedef TableIndex StringIndex;
typedef TableIndex TlsIndex;
typedef TableIndex TraceIndex;

/* Index for method tables in classes */

typedef int        MethodIndex;

/* The different kinds of class status bits. */

enum ClassStatus {
        CLASS_PREPARED          = 0x00000001,
        CLASS_LOADED            = 0x00000002,
        CLASS_UNLOADED          = 0x00000004,
        CLASS_SPECIAL           = 0x00000008,
        CLASS_IN_LOAD_LIST      = 0x00000010,
        CLASS_SYSTEM            = 0x00000020,
        CLASS_DUMPED            = 0x00000040
};
typedef jint       ClassStatus;

/* The different kind of objects we track with heap=dump */

typedef unsigned char ObjectKind;
enum {
        OBJECT_NORMAL = 1,
        OBJECT_CLASS  = 2,
        OBJECT_SYSTEM = 3,
        OBJECT_HPROF  = 4,
        OBJECT_LOADER = 5
};

/* Used by site_write() when writing out the heap=sites data. */

enum {
        SITE_DUMP_INCREMENTAL   = 0x01,
        SITE_SORT_BY_ALLOC      = 0x02,
        SITE_FORCE_GC           = 0x04
};

/* Used to hold information about a field, and potentially a value too. */

typedef struct FieldInfo {
    ClassIndex         cnum;
    StringIndex        name_index;
    StringIndex        sig_index;
    unsigned short     modifiers;
    unsigned char      primType;
    unsigned char      primSize;
} FieldInfo;

/* Used to hold information about a constant pool entry value for a class. */

typedef struct ConstantPoolValue {
    unsigned    constant_pool_index;
    StringIndex sig_index;
    jvalue      value;
} ConstantPoolValue;

/* All machine independent functions */

#include "hprof_error.h"
#include "hprof_util.h"
#include "hprof_blocks.h"
#include "hprof_stack.h"
#include "hprof_init.h"
#include "hprof_table.h"
#include "hprof_string.h"
#include "hprof_class.h"
#include "hprof_tracker.h"
#include "hprof_frame.h"
#include "hprof_monitor.h"
#include "hprof_trace.h"
#include "hprof_site.h"
#include "hprof_event.h"
#include "hprof_reference.h"
#include "hprof_object.h"
#include "hprof_loader.h"
#include "hprof_tls.h"
#include "hprof_check.h"
#include "hprof_io.h"
#include "hprof_listener.h"
#include "hprof_cpu.h"
#include "hprof_tag.h"

/* Global data structure */

struct LineTable;

typedef struct {

    jvmtiEnv            *jvmti; /* JVMTI env for this session */
    JavaVM              *jvm;   /* JavaVM* for this session */
    jint                cachedJvmtiVersion; /* JVMTI version number */

    char               *header; /* "JAVA PROFILE 1.0.[12]" */
    jboolean            segmented;  /* JNI_TRUE if 1.0.2 */
    jlong               maxHeapSegment;
    jlong               maxMemory;

    /* Option settings */
    char *              options;             /* option string copy */
    char *              utf8_output_filename;/* file=filename */
    int                 net_port;            /* net=hostname:port */
    char *              net_hostname;        /* net=hostname:port */
    char                output_format;       /* format=a|b */
    int                 max_trace_depth;     /* depth=max_trace_depth */
    int                 prof_trace_depth;    /* max_trace_depth or 2 (old) */
    int                 sample_interval;     /* interval=sample_interval (ms) */
    double              cutoff_point;        /* cutoff=cutoff_point */
    jboolean            cpu_sampling;        /* cpu=samples|y */
    jboolean            cpu_timing;          /* cpu=times */
    jboolean            old_timing_format;   /* cpu=old (old) output format */
    jboolean            heap_dump;           /* heap=dump|all */
    jboolean            alloc_sites;         /* heap=sites|all */
    jboolean            thread_in_traces;    /* thread=y|n */
    jboolean            lineno_in_traces;    /* lineno=y|n */
    jboolean            dump_on_exit;        /* doe=y|n */
    jboolean            micro_state_accounting; /* msa=y|n */
    jboolean            force_output;        /* force=y|n */
    jboolean            monitor_tracing;     /* monitor=y|n */
    jboolean            gc_okay;             /* gc_okay=y|n (Not used) */

    unsigned            logflags;            /* logflags=bitmask */

    #define DEBUGFLAG_UNPREPARED_CLASSES 0x001
    unsigned            debugflags;          /* debugflags=bitmask */

    jboolean            coredump;            /* coredump=y|n */
    jboolean            errorexit;           /* errorexit=y|n */
    jboolean            pause;               /* pause=y|n */
    jboolean            debug;               /* debug=y|n */
    jboolean            verbose;             /* verbose=y|n */
    jboolean            primfields;          /* primfields=y|n */
    jboolean            primarrays;          /* primarrays=y|n */
    jint                experiment;          /* X=NUMBER */

    int                 fd;             /* file or socket (net=addr). */
    jboolean            socket;         /* True if fd is a socket (net=addr). */
    jboolean            bci;            /* True if any kind of BCI being done */
    jboolean            obj_watch;      /* True if bci and watching allocs */

    int                 bci_counter;    /* Class BCI counter */

    int                 heap_fd;
    char               *output_filename;     /* file=filename */
    char               *heapfilename;

    int                 check_fd;
    char                *checkfilename;

    volatile jboolean   dump_in_process;          /* Dump in process */
    volatile jboolean   jvm_initializing;         /* VMInit happening */
    volatile jboolean   jvm_initialized;          /* VMInit happened */
    volatile jboolean   jvm_shut_down;            /* VMDeath happened */
    jboolean            vm_death_callback_active; /* VMDeath happening */

    /* Stack of objects freed during GC */
    Stack *             object_free_stack;
    jrawMonitorID       object_free_lock;

    /* Lock for debug_malloc() */
    jrawMonitorID       debug_malloc_lock;

    /* Count of classes that JVMTI thinks are active */
    jint                class_count;

    /* Used to track callbacks for VM_DEATH */
    jrawMonitorID       callbackBlock;
    jrawMonitorID       callbackLock;
    jint                active_callbacks;

    /* Running totals on all bytes allocated */
    jlong               total_alloced_bytes;
    jlong               total_alloced_instances;
    jint                total_live_bytes;
    jint                total_live_instances;

    /* Running total on all time spent in GC (very rough estimate) */
    jlong               gc_start_time;
    jlong               time_in_gc;

    /* Global Data access Lock */
    jrawMonitorID       data_access_lock;

    /* Global Dump lock */
    jrawMonitorID       dump_lock;

    /* Milli-second clock when hprof onload started */
    jlong               micro_sec_ticks;

    /* Thread class (for starting agent threads) */
    ClassIndex          thread_cnum;

    /* Agent threads started information */
    jboolean            listener_loop_running;
    jrawMonitorID       listener_loop_lock;
    jboolean            cpu_loop_running;
    jrawMonitorID       cpu_loop_lock;
    jrawMonitorID       cpu_sample_lock;        /* cpu=samples loop */
    jint                gc_finish;              /* Count of GC finish events */
    jboolean            gc_finish_active;       /* True if thread active */
    jboolean            gc_finish_stop_request; /* True if we want it to stop */
    jrawMonitorID       gc_finish_lock;

    jboolean            pause_cpu_sampling; /* temp pause in cpu sampling */

    /* Output buffer, position, size, and position in dump if reading */
    char *              write_buffer;
    int                 write_buffer_index;
    int                 write_buffer_size;
    char *              heap_buffer;
    int                 heap_buffer_index;
    int                 heap_buffer_size;
    jlong               heap_last_tag_position;
    jlong               heap_write_count;
    char *              check_buffer;
    int                 check_buffer_index;
    int                 check_buffer_size;

    /* Serial number counters for tables (see hprof_table.c), classes,
     *     tls (thread local storage), and traces.
     */
    SerialNumber        table_serial_number_start;
    SerialNumber        class_serial_number_start;
    SerialNumber        thread_serial_number_start;
    SerialNumber        trace_serial_number_start;
    SerialNumber        object_serial_number_start;
    SerialNumber        frame_serial_number_start;
    SerialNumber        gref_serial_number_start;

    SerialNumber        table_serial_number_counter;
    SerialNumber        class_serial_number_counter;
    SerialNumber        thread_serial_number_counter;
    SerialNumber        trace_serial_number_counter;
    SerialNumber        object_serial_number_counter;
    SerialNumber        frame_serial_number_counter;
    SerialNumber        gref_serial_number_counter;

    /* The methodID for the Object <init> method. */
    jmethodID           object_init_method;

    /* Keeping track of the tracker class and it's methods */
    volatile jint       tracking_engaged;       /* !=0 means it's on */
    ClassIndex          tracker_cnum;
    int                 tracker_method_count;
    struct {
        StringIndex name;               /* String index for name */
        StringIndex sig;                /* String index for signature */
        jmethodID method;       /* Method ID */
    } tracker_methods[12];      /* MAX 12 Tracker class methods */

    /* Index to some common items */
    LoaderIndex         system_loader;
    SerialNumber        unknown_thread_serial_num;
    TraceIndex          system_trace_index;
    SiteIndex           system_object_site_index;
    jint                system_class_size;
    TraceIndex          hprof_trace_index;
    SiteIndex           hprof_site_index;

    /* Tables for strings, classes, sites, etc. */
    struct LookupTable * string_table;
    struct LookupTable * ioname_table;
    struct LookupTable * class_table;
    struct LookupTable * site_table;
    struct LookupTable * object_table;
    struct LookupTable * reference_table;
    struct LookupTable * frame_table;
    struct LookupTable * trace_table;
    struct LookupTable * monitor_table;
    struct LookupTable * tls_table;
    struct LookupTable * loader_table;

    /* Handles to java_crw_demo library */
    void * java_crw_demo_library;
    void * java_crw_demo_function;
    void * java_crw_demo_classname_function;

    /* Indication that the agent has been loaded */
    jboolean isLoaded;

} GlobalData;

/* This should be the only 'extern' in the library (not exported). */

extern GlobalData * gdata;

#endif
