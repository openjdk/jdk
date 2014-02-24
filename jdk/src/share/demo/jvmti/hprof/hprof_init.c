/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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


/* Main source file, the basic JVMTI connection/startup code. */

#include "hprof.h"

#include "java_crw_demo.h"

/*
 * This file contains all the startup logic (Agent_Onload) and
 *   connection to the JVMTI interface.
 * All JVMTI Event callbacks are in this file.
 * All setting of global data (gdata) is done here.
 * Options are parsed here.
 * Option help messages are here.
 * Termination handled here (VM_DEATH) and shutdown (Agent_OnUnload).
 * Spawning of the cpu sample loop thread and listener thread is done here.
 *
 * Use of private 'static' data has been limited, most shared static data
 *    should be found in the GlobalData structure pointed to by gdata
 *    (see hprof.h).
 *
 */

/* The default output filenames. */

#define DEFAULT_TXT_SUFFIX      ".txt"
#define DEFAULT_OUTPUTFILE      "java.hprof"
#define DEFAULT_OUTPUTTEMP      "java.hprof.temp"

/* The only global variable, defined by this library */
GlobalData *gdata;

/* Experimental options */
#define EXPERIMENT_NO_EARLY_HOOK 0x1

/* Default trace depth */
#define DEFAULT_TRACE_DEPTH 4

/* Default sample interval */
#define DEFAULT_SAMPLE_INTERVAL 10

/* Default cutoff */
#define DEFAULT_CUTOFF_POINT 0.0001

/* Stringize macros for help. */
#define _TO_STR(a) #a
#define TO_STR(a) _TO_STR(a)

/* Macros to surround callback code (non-VM_DEATH callbacks).
 *   Note that this just keeps a count of the non-VM_DEATH callbacks that
 *   are currently active, it does not prevent these callbacks from
 *   operating in parallel. It's the VM_DEATH callback that will wait
 *   for all these callbacks to either complete and block, or just block.
 *   We need to hold back these threads so they don't die during the final
 *   VM_DEATH processing.
 *   If the VM_DEATH callback is active in the beginning, then this callback
 *   just blocks to prevent further execution of the thread.
 *   If the VM_DEATH callback is active at the end, then this callback
 *   will notify the VM_DEATH callback if it's the last one.
 *   In all cases, the last thing they do is Enter/Exit the monitor
 *   gdata->callbackBlock, which will block this callback if VM_DEATH
 *   is running.
 *
 *   WARNING: No not 'return' or 'goto' out of the BEGIN_CALLBACK/END_CALLBACK
 *            block, this will mess up the count.
 */

#define BEGIN_CALLBACK()                                            \
{ /* BEGIN OF CALLBACK */                                           \
    jboolean bypass;                                                \
    rawMonitorEnter(gdata->callbackLock);                           \
    if (gdata->vm_death_callback_active) {                          \
        /* VM_DEATH is active, we will bypass the CALLBACK CODE */  \
        bypass = JNI_TRUE;                                          \
        rawMonitorExit(gdata->callbackLock);                        \
        /* Bypassed CALLBACKS block here until VM_DEATH done */     \
        rawMonitorEnter(gdata->callbackBlock);                      \
        rawMonitorExit(gdata->callbackBlock);                       \
    } else {                                                        \
        /* We will be executing the CALLBACK CODE in this case */   \
        gdata->active_callbacks++;                                  \
        bypass = JNI_FALSE;                                         \
        rawMonitorExit(gdata->callbackLock);                        \
    }                                                               \
    if ( !bypass ) {                                                \
        /* BODY OF CALLBACK CODE (with no callback locks held) */

#define END_CALLBACK() /* Part of bypass if body */                 \
        rawMonitorEnter(gdata->callbackLock);                       \
        gdata->active_callbacks--;                                  \
        /* If VM_DEATH is active, and last one, send notify. */     \
        if (gdata->vm_death_callback_active) {                      \
            if (gdata->active_callbacks == 0) {                     \
                rawMonitorNotifyAll(gdata->callbackLock);           \
            }                                                       \
        }                                                           \
        rawMonitorExit(gdata->callbackLock);                        \
        /* Non-Bypassed CALLBACKS block here until VM_DEATH done */ \
        rawMonitorEnter(gdata->callbackBlock);                      \
        rawMonitorExit(gdata->callbackBlock);                       \
    }                                                               \
} /* END OF CALLBACK */

/* Forward declarations */
static void set_callbacks(jboolean on);

/* ------------------------------------------------------------------- */
/* Global data initialization */

/* Get initialized global data area */
static GlobalData *
get_gdata(void)
{
    static GlobalData data;

    /* Create initial default values */
    (void)memset(&data, 0, sizeof(GlobalData));

    data.fd                             = -1; /* Non-zero file or socket. */
    data.heap_fd                        = -1; /* For heap=dump, see hprof_io */
    data.check_fd                       = -1; /* For heap=dump, see hprof_io */
    data.max_trace_depth                = DEFAULT_TRACE_DEPTH;
    data.prof_trace_depth               = DEFAULT_TRACE_DEPTH;
    data.sample_interval                = DEFAULT_SAMPLE_INTERVAL;
    data.lineno_in_traces               = JNI_TRUE;
    data.output_format                  = 'a';      /* 'b' for binary */
    data.cutoff_point                   = DEFAULT_CUTOFF_POINT;
    data.dump_on_exit                   = JNI_TRUE;
    data.gc_start_time                  = -1L;
#ifdef DEBUG
    data.debug                          = JNI_TRUE;
    data.coredump                       = JNI_TRUE;
#endif
    data.micro_state_accounting         = JNI_FALSE;
    data.force_output                   = JNI_TRUE;
    data.verbose                        = JNI_TRUE;
    data.primfields                     = JNI_TRUE;
    data.primarrays                     = JNI_TRUE;

    data.table_serial_number_start    = 1;
    data.class_serial_number_start    = 100000;
    data.thread_serial_number_start   = 200000;
    data.trace_serial_number_start    = 300000;
    data.object_serial_number_start   = 400000;
    data.frame_serial_number_start    = 500000;
    data.gref_serial_number_start     = 1;

    data.table_serial_number_counter  = data.table_serial_number_start;
    data.class_serial_number_counter  = data.class_serial_number_start;
    data.thread_serial_number_counter = data.thread_serial_number_start;
    data.trace_serial_number_counter  = data.trace_serial_number_start;
    data.object_serial_number_counter = data.object_serial_number_start;
    data.frame_serial_number_counter  = data.frame_serial_number_start;
    data.gref_serial_number_counter   = data.gref_serial_number_start;

    data.unknown_thread_serial_num    = data.thread_serial_number_counter++;
    return &data;
}

/* ------------------------------------------------------------------- */
/* Error handler callback for the java_crw_demo (classfile read write) functions. */

static void
my_crw_fatal_error_handler(const char * msg, const char *file, int line)
{
    char errmsg[256];

    (void)md_snprintf(errmsg, sizeof(errmsg),
                "%s [%s:%d]", msg, file, line);
    errmsg[sizeof(errmsg)-1] = 0;
    HPROF_ERROR(JNI_TRUE, errmsg);
}

static void
list_all_tables(void)
{
    string_list();
    class_list();
    frame_list();
    site_list();
    object_list();
    trace_list();
    monitor_list();
    tls_list();
    loader_list();
}

/* ------------------------------------------------------------------- */
/* Option Parsing support */

/**
 * Socket connection
 */

/*
 * Return a socket  connect()ed to a "hostname" that is
 * accept()ing heap profile data on "port." Return a value <= 0 if
 * such a connection can't be made.
 */
static int
connect_to_socket(char *hostname, unsigned short port)
{
    int fd;

    if (port == 0 || port > 65535) {
        HPROF_ERROR(JNI_FALSE, "invalid port number");
        return -1;
    }
    if (hostname == NULL) {
        HPROF_ERROR(JNI_FALSE, "hostname is NULL");
        return -1;
    }

    /* create a socket */
    fd = md_connect(hostname, port);
    return fd;
}

/* Accept a filename, and adjust the name so that it is unique for this PID */
static void
make_unique_filename(char **filename)
{
    int fd;

    /* Find a file that doesn't exist */
    fd = md_open(*filename);
    if ( fd >= 0 ) {
        int   pid;
        char *new_name;
        char *old_name;
        char *prefix;
        char  suffix[5];
        int   new_len;

        /* Close the file. */
        md_close(fd);

        /* Make filename name.PID[.txt] */
        pid = md_getpid();
        old_name = *filename;
        new_len = (int)strlen(old_name)+64;
        new_name = HPROF_MALLOC(new_len);
        prefix = old_name;
        suffix[0] = 0;

        /* Look for .txt suffix if not binary output */
        if (gdata->output_format != 'b') {
            char *dot;
            char *format_suffix;

            format_suffix = DEFAULT_TXT_SUFFIX;

            (void)strcpy(suffix, format_suffix);

            dot = strrchr(old_name, '.');
            if ( dot != NULL ) {
                int i;
                int slen;
                int match;

                slen = (int)strlen(format_suffix);
                match = 1;
                for ( i = 0; i < slen; i++ ) {
                    if ( dot[i]==0 ||
                         tolower(format_suffix[i]) != tolower(dot[i]) ) {
                        match = 0;
                        break;
                    }
                }
                if ( match ) {
                    (void)strcpy(suffix, dot);
                    *dot = 0; /* truncates prefix and old_name */
                }
            }
        }

        /* Construct the name */
        (void)md_snprintf(new_name, new_len,
                   "%s.%d%s", prefix, pid, suffix);
        *filename = new_name;
        HPROF_FREE(old_name);

        /* Odds are with Windows, this file may not be so unique. */
        (void)remove(gdata->output_filename);
    }
}

static int
get_tok(char **src, char *buf, int buflen, int sep)
{
    int len;
    char *p;

    buf[0] = 0;
    if ( **src == 0 ) {
        return 0;
    }
    p = strchr(*src, sep);
    if ( p==NULL ) {
        len = (int)strlen(*src);
        p = (*src) + len;
    } else {
        /*LINTED*/
        len = (int)(p - (*src));
    }
    if ( (len+1) > buflen ) {
        return 0;
    }
    (void)memcpy(buf, *src, len);
    buf[len] = 0;
    if ( *p != 0 && *p == sep ) {
        (*src) = p+1;
    } else {
        (*src) = p;
    }
    return len;
}

static jboolean
setBinarySwitch(char **src, jboolean *ptr)
{
    char buf[80];

    if (!get_tok(src, buf, (int)sizeof(buf), ',')) {
        return JNI_FALSE;
    }
    if (strcmp(buf, "y") == 0) {
        *ptr = JNI_TRUE;
    } else if (strcmp(buf, "n") == 0) {
        *ptr = JNI_FALSE;
    } else {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

static void
print_usage(void)
{

    (void)fprintf(stdout,
"\n"
"     HPROF: Heap and CPU Profiling Agent (JVMTI Demonstration Code)\n"
"\n"
AGENTNAME " usage: java " AGENTLIB "=[help]|[<option>=<value>, ...]\n"
"\n"
"Option Name and Value  Description                    Default\n"
"---------------------  -----------                    -------\n"
"heap=dump|sites|all    heap profiling                 all\n"
"cpu=samples|times|old  CPU usage                      off\n"
"monitor=y|n            monitor contention             n\n"
"format=a|b             text(txt) or binary output     a\n"
"file=<file>            write data to file             " DEFAULT_OUTPUTFILE "[{" DEFAULT_TXT_SUFFIX "}]\n"
"net=<host>:<port>      send data over a socket        off\n"
"depth=<size>           stack trace depth              " TO_STR(DEFAULT_TRACE_DEPTH) "\n"
"interval=<ms>          sample interval in ms          " TO_STR(DEFAULT_SAMPLE_INTERVAL) "\n"
"cutoff=<value>         output cutoff point            " TO_STR(DEFAULT_CUTOFF_POINT) "\n"
"lineno=y|n             line number in traces?         y\n"
"thread=y|n             thread in traces?              n\n"
"doe=y|n                dump on exit?                  y\n"
"msa=y|n                Solaris micro state accounting n\n"
"force=y|n              force output to <file>         y\n"
"verbose=y|n            print messages about dumps     y\n"
"\n"
"Obsolete Options\n"
"----------------\n"
"gc_okay=y|n\n"

#ifdef DEBUG
"\n"
"DEBUG Option           Description                    Default\n"
"------------           -----------                    -------\n"
"primfields=y|n         include primitive field values y\n"
"primarrays=y|n         include primitive array values y\n"
"debugflags=MASK        Various debug flags            0\n"
"                        0x01   Report refs in and of unprepared classes\n"
"logflags=MASK          Logging to stderr              0\n"
"                        " TO_STR(LOG_DUMP_MISC)    " Misc logging\n"
"                        " TO_STR(LOG_DUMP_LISTS)   " Dump out the tables\n"
"                        " TO_STR(LOG_CHECK_BINARY) " Verify & dump format=b\n"
"coredump=y|n           Core dump on fatal             n\n"
"errorexit=y|n          Exit on any error              n\n"
"pause=y|n              Pause on onload & echo PID     n\n"
"debug=y|n              Turn on all debug checking     n\n"
"X=MASK                 Internal use only              0\n"

"\n"
"Environment Variables\n"
"---------------------\n"
"_JAVA_HPROF_OPTIONS\n"
"    Options can be added externally via this environment variable.\n"
"    Anything contained in it will get a comma prepended to it (if needed),\n"
"    then it will be added to the end of the options supplied via the\n"
"    " XRUN " or " AGENTLIB " command line option.\n"

#endif

"\n"
"Examples\n"
"--------\n"
"  - Get sample cpu information every 20 millisec, with a stack depth of 3:\n"
"      java " AGENTLIB "=cpu=samples,interval=20,depth=3 classname\n"
"  - Get heap usage information based on the allocation sites:\n"
"      java " AGENTLIB "=heap=sites classname\n"

#ifdef DEBUG
"  - Using the external option addition with csh, log details on all runs:\n"
"      setenv _JAVA_HPROF_OPTIONS \"logflags=0xC\"\n"
"      java " AGENTLIB "=cpu=samples classname\n"
"    is the same as:\n"
"      java " AGENTLIB "=cpu=samples,logflags=0xC classname\n"
#endif

"\n"
"Notes\n"
"-----\n"
"  - The option format=b cannot be used with monitor=y.\n"
"  - The option format=b cannot be used with cpu=old|times.\n"
"  - Use of the " XRUN " interface can still be used, e.g.\n"
"       java " XRUN ":[help]|[<option>=<value>, ...]\n"
"    will behave exactly the same as:\n"
"       java " AGENTLIB "=[help]|[<option>=<value>, ...]\n"

#ifdef DEBUG
"  - The debug options and environment variables are available with both java\n"
"    and java_g versions.\n"
#endif

"\n"
"Warnings\n"
"--------\n"
"  - This is demonstration code for the JVMTI interface and use of BCI,\n"
"    it is not an official product or formal part of the JDK.\n"
"  - The " XRUN " interface will be removed in a future release.\n"
"  - The option format=b is considered experimental, this format may change\n"
"    in a future release.\n"

#ifdef DEBUG
"  - The obsolete options may be completely removed in a future release.\n"
"  - The debug options and environment variables are not considered public\n"
"    interfaces and can change or be removed with any type of update of\n"
"    " AGENTNAME ", including patches.\n"
#endif

        );
}

static void
option_error(char *description)
{
    char errmsg[FILENAME_MAX+80];

    (void)md_snprintf(errmsg, sizeof(errmsg),
           "%s option error: %s (%s)", AGENTNAME, description, gdata->options);
    errmsg[sizeof(errmsg)-1] = 0;
    HPROF_ERROR(JNI_FALSE, errmsg);
    error_exit_process(1);
}

static void
parse_options(char *command_line_options)
{
    int file_or_net_option_seen = JNI_FALSE;
    char *all_options;
    char *extra_options;
    char *options;
    char *default_filename;
    int   ulen;

    if (command_line_options == 0)
        command_line_options = "";

    if ((strcmp(command_line_options, "help")) == 0) {
        print_usage();
        error_exit_process(0);
    }

    extra_options = getenv("_JAVA_HPROF_OPTIONS");
    if ( extra_options == NULL ) {
        extra_options = "";
    }

    all_options = HPROF_MALLOC((int)strlen(command_line_options) +
                                (int)strlen(extra_options) + 2);
    gdata->options = all_options;
    (void)strcpy(all_options, command_line_options);
    if ( extra_options[0] != 0 ) {
        if ( all_options[0] != 0 ) {
            (void)strcat(all_options, ",");
        }
        (void)strcat(all_options, extra_options);
    }
    options = all_options;

    LOG2("parse_options()", all_options);

    while (*options) {
        char option[16];
        char suboption[FILENAME_MAX+1];
        char *endptr;

        if (!get_tok(&options, option, (int)sizeof(option), '=')) {
            option_error("general syntax error parsing options");
        }
        if (strcmp(option, "file") == 0) {
            if ( file_or_net_option_seen  ) {
                option_error("file or net options should only appear once");
            }
            if (!get_tok(&options, suboption, (int)sizeof(suboption), ',')) {
                option_error("syntax error parsing file=filename");
            }
            gdata->utf8_output_filename = HPROF_MALLOC((int)strlen(suboption)+1);
            (void)strcpy(gdata->utf8_output_filename, suboption);
            file_or_net_option_seen = JNI_TRUE;
        } else if (strcmp(option, "net") == 0) {
            char port_number[16];
            if (file_or_net_option_seen ) {
                option_error("file or net options should only appear once");
            }
            if (!get_tok(&options, suboption, (int)sizeof(suboption), ':')) {
                option_error("net option missing ':'");
            }
            if (!get_tok(&options, port_number, (int)sizeof(port_number), ',')) {
                option_error("net option missing port");
            }
            gdata->net_hostname = HPROF_MALLOC((int)strlen(suboption)+1);
            (void)strcpy(gdata->net_hostname, suboption);
            gdata->net_port = (int)strtol(port_number, NULL, 10);
            file_or_net_option_seen = JNI_TRUE;
        } else if (strcmp(option, "format") == 0) {
            if (!get_tok(&options, suboption, (int)sizeof(suboption), ',')) {
                option_error("syntax error parsing format=a|b");
            }
            if (strcmp(suboption, "a") == 0) {
                gdata->output_format = 'a';
            } else if (strcmp(suboption, "b") == 0) {
                gdata->output_format = 'b';
            } else {
                option_error("format option value must be a|b");
            }
        } else if (strcmp(option, "depth") == 0) {
            if (!get_tok(&options, suboption, (int)sizeof(suboption), ',')) {
                option_error("syntax error parsing depth=DECIMAL");
            }
            gdata->max_trace_depth = (int)strtol(suboption, &endptr, 10);
            if ((endptr != NULL && *endptr != 0) || gdata->max_trace_depth < 0) {
                option_error("depth option value must be decimal and >= 0");
            }
            gdata->prof_trace_depth = gdata->max_trace_depth;
        } else if (strcmp(option, "interval") == 0) {
            if (!get_tok(&options, suboption, (int)sizeof(suboption), ',')) {
                option_error("syntax error parsing interval=DECIMAL");
            }
            gdata->sample_interval = (int)strtol(suboption, &endptr, 10);
            if ((endptr != NULL && *endptr != 0) || gdata->sample_interval <= 0) {
                option_error("interval option value must be decimal and > 0");
            }
        } else if (strcmp(option, "cutoff") == 0) {
            if (!get_tok(&options, suboption, (int)sizeof(suboption), ',')) {
                option_error("syntax error parsing cutoff=DOUBLE");
            }
            gdata->cutoff_point = strtod(suboption, &endptr);
            if ((endptr != NULL && *endptr != 0) || gdata->cutoff_point < 0) {
                option_error("cutoff option value must be floating point and >= 0");
            }
        } else if (strcmp(option, "cpu") == 0) {
            if (!get_tok(&options, suboption, (int)sizeof(suboption), ',')) {
                option_error("syntax error parsing cpu=y|samples|times|old");
            }
            if ((strcmp(suboption, "samples") == 0) ||
                (strcmp(suboption, "y") == 0)) {
                gdata->cpu_sampling = JNI_TRUE;
            } else if (strcmp(suboption, "times") == 0) {
                gdata->cpu_timing = JNI_TRUE;
                gdata->old_timing_format = JNI_FALSE;
            } else if (strcmp(suboption, "old") == 0) {
                gdata->cpu_timing = JNI_TRUE;
                gdata->old_timing_format = JNI_TRUE;
            } else {
                option_error("cpu option value must be y|samples|times|old");
            }
        } else if (strcmp(option, "heap") == 0) {
            if (!get_tok(&options, suboption, (int)sizeof(suboption), ',')) {
                option_error("syntax error parsing heap=dump|sites|all");
            }
            if (strcmp(suboption, "dump") == 0) {
                gdata->heap_dump = JNI_TRUE;
            } else if (strcmp(suboption, "sites") == 0) {
                gdata->alloc_sites = JNI_TRUE;
            } else if (strcmp(suboption, "all") == 0) {
                gdata->heap_dump = JNI_TRUE;
                gdata->alloc_sites = JNI_TRUE;
            } else {
                option_error("heap option value must be dump|sites|all");
            }
        } else if( strcmp(option,"lineno") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->lineno_in_traces)) ) {
                option_error("lineno option value must be y|n");
            }
        } else if( strcmp(option,"thread") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->thread_in_traces)) ) {
                option_error("thread option value must be y|n");
            }
        } else if( strcmp(option,"doe") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->dump_on_exit)) ) {
                option_error("doe option value must be y|n");
            }
        } else if( strcmp(option,"msa") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->micro_state_accounting)) ) {
                option_error("msa option value must be y|n");
            }
        } else if( strcmp(option,"force") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->force_output)) ) {
                option_error("force option value must be y|n");
            }
        } else if( strcmp(option,"verbose") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->verbose)) ) {
                option_error("verbose option value must be y|n");
            }
        } else if( strcmp(option,"primfields") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->primfields)) ) {
                option_error("primfields option value must be y|n");
            }
        } else if( strcmp(option,"primarrays") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->primarrays)) ) {
                option_error("primarrays option value must be y|n");
            }
        } else if( strcmp(option,"monitor") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->monitor_tracing)) ) {
                option_error("monitor option value must be y|n");
            }
        } else if( strcmp(option,"gc_okay") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->gc_okay)) ) {
                option_error("gc_okay option value must be y|n");
            }
        } else if (strcmp(option, "logflags") == 0) {
            if (!get_tok(&options, suboption, (int)sizeof(suboption), ',')) {
                option_error("logflags option value must be numeric");
            }
            gdata->logflags = (int)strtol(suboption, NULL, 0);
        } else if (strcmp(option, "debugflags") == 0) {
            if (!get_tok(&options, suboption, (int)sizeof(suboption), ',')) {
                option_error("debugflags option value must be numeric");
            }
            gdata->debugflags = (int)strtol(suboption, NULL, 0);
        } else if (strcmp(option, "coredump") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->coredump)) ) {
                option_error("coredump option value must be y|n");
            }
        } else if (strcmp(option, "exitpause") == 0) {
            option_error("The exitpause option was removed, use -XX:OnError='cmd %%p'");
        } else if (strcmp(option, "errorexit") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->errorexit)) ) {
                option_error("errorexit option value must be y|n");
            }
        } else if (strcmp(option, "pause") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->pause)) ) {
                option_error("pause option value must be y|n");
            }
        } else if (strcmp(option, "debug") == 0) {
            if ( !setBinarySwitch(&options, &(gdata->debug)) ) {
                option_error("debug option value must be y|n");
            }
        } else if (strcmp(option, "precrash") == 0) {
            option_error("The precrash option was removed, use -XX:OnError='precrash -p %%p'");
        } else if (strcmp(option, "X") == 0) {
            if (!get_tok(&options, suboption, (int)sizeof(suboption), ',')) {
                option_error("X option value must be numeric");
            }
            gdata->experiment = (int)strtol(suboption, NULL, 0);
        } else {
            char errmsg[80];
            (void)strcpy(errmsg, "Unknown option: ");
            (void)strcat(errmsg, option);
            option_error(errmsg);
        }
    }

    if (gdata->output_format == 'b') {
        if (gdata->cpu_timing) {
            option_error("cpu=times|old is not supported with format=b");
        }
        if (gdata->monitor_tracing) {
            option_error("monitor=y is not supported with format=b");
        }
    }

    if (gdata->old_timing_format) {
        gdata->prof_trace_depth = 2;
    }

    if (gdata->output_format == 'b') {
        default_filename = DEFAULT_OUTPUTFILE;
    } else {
        default_filename = DEFAULT_OUTPUTFILE DEFAULT_TXT_SUFFIX;
    }

    if (!file_or_net_option_seen) {
        gdata->utf8_output_filename = HPROF_MALLOC((int)strlen(default_filename)+1);
        (void)strcpy(gdata->utf8_output_filename, default_filename);
    }

    if ( gdata->utf8_output_filename != NULL ) {
        /* UTF-8 to platform encoding (fill in gdata->output_filename) */
        ulen = (int)strlen(gdata->utf8_output_filename);
        gdata->output_filename = (char*)HPROF_MALLOC(ulen*3+3);
#ifdef SKIP_NPT
        (void)strcpy(gdata->output_filename, gdata->utf8_output_filename);
#else
        (void)(gdata->npt->utf8ToPlatform)
              (gdata->npt->utf, (jbyte*)gdata->utf8_output_filename, ulen,
               gdata->output_filename, ulen*3+3);
#endif
    }

    /* By default we turn on gdata->alloc_sites and gdata->heap_dump */
    if (     !gdata->cpu_timing &&
             !gdata->cpu_sampling &&
             !gdata->monitor_tracing &&
             !gdata->alloc_sites &&
             !gdata->heap_dump) {
        gdata->heap_dump = JNI_TRUE;
        gdata->alloc_sites = JNI_TRUE;
    }

    if ( gdata->alloc_sites || gdata->heap_dump ) {
        gdata->obj_watch = JNI_TRUE;
    }
    if ( gdata->obj_watch || gdata->cpu_timing ) {
        gdata->bci = JNI_TRUE;
    }

    /* Create files & sockets needed */
    if (gdata->heap_dump) {
        char *base;
        int   len;

        /* Get a fast tempfile for the heap information */
        base = gdata->output_filename;
        if ( base==NULL ) {
            base = default_filename;
        }
        len = (int)strlen(base);
        gdata->heapfilename = HPROF_MALLOC(len + 5);
        (void)strcpy(gdata->heapfilename, base);
        (void)strcat(gdata->heapfilename, ".TMP");
        make_unique_filename(&(gdata->heapfilename));
        (void)remove(gdata->heapfilename);
        if (gdata->output_format == 'b') {
            if ( gdata->logflags & LOG_CHECK_BINARY ) {
                char * check_suffix;

                check_suffix = ".check" DEFAULT_TXT_SUFFIX;
                gdata->checkfilename =
                    HPROF_MALLOC((int)strlen(default_filename)+
                                (int)strlen(check_suffix)+1);
                (void)strcpy(gdata->checkfilename, default_filename);
                (void)strcat(gdata->checkfilename, check_suffix);
                (void)remove(gdata->checkfilename);
                gdata->check_fd = md_creat(gdata->checkfilename);
            }
            if ( gdata->debug ) {
                gdata->logflags |= LOG_CHECK_BINARY;
            }
            gdata->heap_fd = md_creat_binary(gdata->heapfilename);
        } else {
            gdata->heap_fd = md_creat(gdata->heapfilename);
        }
        if ( gdata->heap_fd < 0 ) {
            char errmsg[FILENAME_MAX+80];

            (void)md_snprintf(errmsg, sizeof(errmsg),
                     "can't create temp heap file: %s", gdata->heapfilename);
                    errmsg[sizeof(errmsg)-1] = 0;
            HPROF_ERROR(JNI_TRUE, errmsg);
        }
    }

    if ( gdata->net_port > 0 ) {
        LOG2("Agent_OnLoad", "Connecting to socket");
        gdata->fd = connect_to_socket(gdata->net_hostname, (unsigned short)gdata->net_port);
        if (gdata->fd <= 0) {
            char errmsg[120];

            (void)md_snprintf(errmsg, sizeof(errmsg),
                "can't connect to %s:%u", gdata->net_hostname, gdata->net_port);
            errmsg[sizeof(errmsg)-1] = 0;
            HPROF_ERROR(JNI_FALSE, errmsg);
            error_exit_process(1);
        }
        gdata->socket = JNI_TRUE;
    } else {
        /* If going out to a file, obey the force=y|n option */
        if ( !gdata->force_output ) {
            make_unique_filename(&(gdata->output_filename));
        }
        /* Make doubly sure this file does NOT exist */
        (void)remove(gdata->output_filename);
        /* Create the file */
        if (gdata->output_format == 'b') {
            gdata->fd = md_creat_binary(gdata->output_filename);
        } else {
            gdata->fd = md_creat(gdata->output_filename);
        }
        if (gdata->fd < 0) {
            char errmsg[FILENAME_MAX+80];

            (void)md_snprintf(errmsg, sizeof(errmsg),
                "can't create profile file: %s", gdata->output_filename);
            errmsg[sizeof(errmsg)-1] = 0;
            HPROF_ERROR(JNI_FALSE, errmsg);
            error_exit_process(1);
        }
    }

}

/* ------------------------------------------------------------------- */
/* Data reset and dump functions */

static void
reset_all_data(void)
{
    if (gdata->cpu_sampling || gdata->cpu_timing || gdata->monitor_tracing) {
        rawMonitorEnter(gdata->data_access_lock);
    }

    if (gdata->cpu_sampling || gdata->cpu_timing) {
        trace_clear_cost();
    }
    if (gdata->monitor_tracing) {
        monitor_clear();
    }

    if (gdata->cpu_sampling || gdata->cpu_timing || gdata->monitor_tracing) {
        rawMonitorExit(gdata->data_access_lock);
    }
}

static void reset_class_load_status(JNIEnv *env, jthread thread);

static void
dump_all_data(JNIEnv *env)
{
    verbose_message("Dumping");
    if (gdata->monitor_tracing) {
        verbose_message(" contended monitor usage ...");
        tls_dump_monitor_state(env);
        monitor_write_contended_time(env, gdata->cutoff_point);
    }
    if (gdata->heap_dump) {
        verbose_message(" Java heap ...");
        /* Update the class table */
        reset_class_load_status(env, NULL);
        site_heapdump(env);
    }
    if (gdata->alloc_sites) {
        verbose_message(" allocation sites ...");
        site_write(env, 0, gdata->cutoff_point);
    }
    if (gdata->cpu_sampling) {
        verbose_message(" CPU usage by sampling running threads ...");
        trace_output_cost(env, gdata->cutoff_point);
    }
    if (gdata->cpu_timing) {
        if (!gdata->old_timing_format) {
            verbose_message(" CPU usage by timing methods ...");
            trace_output_cost(env, gdata->cutoff_point);
        } else {
            verbose_message(" CPU usage in old prof format ...");
            trace_output_cost_in_prof_format(env);
        }
    }
    reset_all_data();
    io_flush();
    verbose_message(" done.\n");
}

/* ------------------------------------------------------------------- */
/* Dealing with class load and unload status */

static void
reset_class_load_status(JNIEnv *env, jthread thread)
{

    WITH_LOCAL_REFS(env, 1) {
        jint    class_count;
        jclass *classes;
        jint    i;

        /* Get all classes from JVMTI, make sure they are in the class table. */
        getLoadedClasses(&classes, &class_count);

        /* We don't know if the class list has changed really, so we
         *    guess by the class count changing. Don't want to do
         *    a bunch of work on classes when it's unnecessary.
         *    I assume that even though we have global references on the
         *    jclass object that the class is still considered unloaded.
         *    (e.g. GC of jclass isn't required for it to be included
         *    in the unloaded list, or not in the load list)
         *    [Note: Use of Weak references was a performance problem.]
         */
        if ( class_count != gdata->class_count ) {

            rawMonitorEnter(gdata->data_access_lock); {

                /* Unmark the classes in the load list */
                class_all_status_remove(CLASS_IN_LOAD_LIST);

                /* Pretend like it was a class load event */
                for ( i = 0 ; i < class_count ; i++ ) {
                    jobject loader;

                    loader = getClassLoader(classes[i]);
                    event_class_load(env, thread, classes[i], loader);
                }

                /* Process the classes that have been unloaded */
                class_do_unloads(env);

            } rawMonitorExit(gdata->data_access_lock);

        }

        /* Free the space and save the count. */
        jvmtiDeallocate(classes);
        gdata->class_count = class_count;

    } END_WITH_LOCAL_REFS;

}

/* A GC or Death event has happened, so do some cleanup */
static void
object_free_cleanup(JNIEnv *env, jboolean force_class_table_reset)
{
    Stack *stack;

    /* Then we process the ObjectFreeStack */
    rawMonitorEnter(gdata->object_free_lock); {
        stack = gdata->object_free_stack;
        gdata->object_free_stack = NULL; /* Will trigger new stack */
    } rawMonitorExit(gdata->object_free_lock);

    /* Notice we just grabbed the stack of freed objects so
     *    any object free events will create a new stack.
     */
    if ( stack != NULL ) {
        int count;
        int i;

        count = stack_depth(stack);

        /* If we saw something freed in this GC */
        if ( count > 0 ) {

            for ( i = 0 ; i < count ; i++ ) {
                ObjectIndex object_index;
                jlong tag;

                tag = *(jlong*)stack_element(stack,i);
                    object_index = tag_extract(tag);

                (void)object_free(object_index);
            }

            /* We reset the class load status (only do this once) */
            reset_class_load_status(env, NULL);
            force_class_table_reset = JNI_FALSE;

        }

        /* Just terminate this stack object */
        stack_term(stack);
    }

    /* We reset the class load status if we haven't and need to */
    if ( force_class_table_reset ) {
        reset_class_load_status(env, NULL);
    }

}

/* Main function for thread that watches for GC finish events */
static void JNICALL
gc_finish_watcher(jvmtiEnv *jvmti, JNIEnv *env, void *p)
{
    jboolean active;

    active = JNI_TRUE;

    /* Indicate the watcher thread is active */
    rawMonitorEnter(gdata->gc_finish_lock); {
        gdata->gc_finish_active = JNI_TRUE;
    } rawMonitorExit(gdata->gc_finish_lock);

    /* Loop while active */
    while ( active ) {
        jboolean do_cleanup;

        do_cleanup = JNI_FALSE;
        rawMonitorEnter(gdata->gc_finish_lock); {
            /* Don't wait if VM_DEATH wants us to quit */
            if ( gdata->gc_finish_stop_request ) {
                /* Time to terminate */
                active = JNI_FALSE;
            } else {
                /* Wait for notification to do cleanup, or terminate */
                rawMonitorWait(gdata->gc_finish_lock, 0);
                /* After wait, check to see if VM_DEATH wants us to quit */
                if ( gdata->gc_finish_stop_request ) {
                    /* Time to terminate */
                    active = JNI_FALSE;
                }
            }
            if ( active && gdata->gc_finish > 0 ) {
                /* Time to cleanup, reset count and prepare for cleanup */
                gdata->gc_finish = 0;
                do_cleanup = JNI_TRUE;
            }
        } rawMonitorExit(gdata->gc_finish_lock);

        /* Do the cleanup if requested outside gc_finish_lock */
        if ( do_cleanup ) {
            /* Free up all freed objects, don't force class table reset
             *   We cannot let the VM_DEATH complete while we are doing
             *   this cleanup. So if during this, VM_DEATH happens,
             *   the VM_DEATH callback should block waiting for this
             *   loop to terminate, and send a notification to the
             *   VM_DEATH thread.
             */
            object_free_cleanup(env, JNI_FALSE);

            /* Cleanup the tls table where the Thread objects were GC'd */
            tls_garbage_collect(env);
        }

    }

    /* Falling out means VM_DEATH is happening, we need to notify VM_DEATH
     *    that we are done doing the cleanup. VM_DEATH is waiting on this
     *    notify.
     */
    rawMonitorEnter(gdata->gc_finish_lock); {
        gdata->gc_finish_active = JNI_FALSE;
        rawMonitorNotifyAll(gdata->gc_finish_lock);
    } rawMonitorExit(gdata->gc_finish_lock);
}

/* ------------------------------------------------------------------- */
/* JVMTI Event callback functions */

static void
setup_event_mode(jboolean onload_set_only, jvmtiEventMode state)
{
    if ( onload_set_only ) {
        setEventNotificationMode(state,
                        JVMTI_EVENT_VM_INIT,                   NULL);
        setEventNotificationMode(state,
                        JVMTI_EVENT_VM_DEATH,                  NULL);
        if (gdata->bci) {
            setEventNotificationMode(state,
                        JVMTI_EVENT_CLASS_FILE_LOAD_HOOK,      NULL);
        }
    } else {
        /* Enable all other JVMTI events of interest now. */
        setEventNotificationMode(state,
                        JVMTI_EVENT_THREAD_START,              NULL);
        setEventNotificationMode(state,
                        JVMTI_EVENT_THREAD_END,                NULL);
        setEventNotificationMode(state,
                        JVMTI_EVENT_CLASS_LOAD,                NULL);
        setEventNotificationMode(state,
                        JVMTI_EVENT_CLASS_PREPARE,             NULL);
        setEventNotificationMode(state,
                        JVMTI_EVENT_DATA_DUMP_REQUEST,         NULL);
        if (gdata->cpu_timing) {
            setEventNotificationMode(state,
                        JVMTI_EVENT_EXCEPTION_CATCH,           NULL);
        }
        if (gdata->monitor_tracing) {
            setEventNotificationMode(state,
                        JVMTI_EVENT_MONITOR_WAIT,              NULL);
            setEventNotificationMode(state,
                        JVMTI_EVENT_MONITOR_WAITED,            NULL);
            setEventNotificationMode(state,
                        JVMTI_EVENT_MONITOR_CONTENDED_ENTER,   NULL);
            setEventNotificationMode(state,
                        JVMTI_EVENT_MONITOR_CONTENDED_ENTERED, NULL);
        }
        if (gdata->obj_watch) {
            setEventNotificationMode(state,
                        JVMTI_EVENT_OBJECT_FREE,               NULL);
        }
        setEventNotificationMode(state,
                        JVMTI_EVENT_GARBAGE_COLLECTION_START,  NULL);
        setEventNotificationMode(state,
                        JVMTI_EVENT_GARBAGE_COLLECTION_FINISH, NULL);
    }
}

/* JVMTI_EVENT_VM_INIT */
static void JNICALL
cbVMInit(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    rawMonitorEnter(gdata->data_access_lock); {

        LoaderIndex loader_index;
        ClassIndex  cnum;
        TlsIndex    tls_index;

        gdata->jvm_initializing = JNI_TRUE;

        /* Header to use in heap dumps */
        gdata->header    = "JAVA PROFILE 1.0.1";
        gdata->segmented = JNI_FALSE;
        if (gdata->output_format == 'b') {
            /* We need JNI here to call in and get the current maximum memory */
            gdata->maxMemory      = getMaxMemory(env);
            gdata->maxHeapSegment = (jlong)2000000000;
            /* More than 2Gig triggers segments and 1.0.2 */
            if ( gdata->maxMemory >= gdata->maxHeapSegment ) {
                gdata->header    = "JAVA PROFILE 1.0.2";
                gdata->segmented = JNI_TRUE; /* 1.0.2 */
            }
        }

        /* We write the initial header after the VM initializes now
         *    because we needed to use JNI to get maxMemory and determine if
         *    a 1.0.1 or a 1.0.2 header will be used.
         *    This used to be done in Agent_OnLoad.
         */
        io_write_file_header();

        LOG("cbVMInit begin");

        /* Create a system loader entry first */
        loader_index            = loader_find_or_create(NULL,NULL);

        /* Find the thread jclass (does JNI calls) */
        gdata->thread_cnum = class_find_or_create("Ljava/lang/Thread;",
                        loader_index);
        class_add_status(gdata->thread_cnum, CLASS_SYSTEM);

        /* Issue fake system thread start */
        tls_index = tls_find_or_create(env, thread);

        /* Setup the Tracker class (should be first class in table) */
        tracker_setup_class();

        /* Find selected system classes to keep track of */
        gdata->system_class_size = 0;
        cnum = class_find_or_create("Ljava/lang/Object;", loader_index);

        gdata->system_trace_index = tls_get_trace(tls_index, env,
                                gdata->max_trace_depth, JNI_FALSE);
        gdata->system_object_site_index = site_find_or_create(
                    cnum, gdata->system_trace_index);

        /* Used to ID HPROF generated items */
        gdata->hprof_trace_index = tls_get_trace(tls_index, env,
                                gdata->max_trace_depth, JNI_FALSE);
        gdata->hprof_site_index = site_find_or_create(
                    cnum, gdata->hprof_trace_index);

        if ( gdata->logflags & LOG_DUMP_LISTS ) {
            list_all_tables();
        }

        /* Prime the class table */
        reset_class_load_status(env, thread);

        /* Find the tracker jclass and jmethodID's (does JNI calls) */
        if ( gdata->bci ) {
            tracker_setup_methods(env);
        }

        /* Start any agent threads (does JNI, JVMTI, and Java calls) */

        /* Thread to watch for gc_finish events */
        rawMonitorEnter(gdata->gc_finish_lock); {
            createAgentThread(env, "HPROF gc_finish watcher",
                              &gc_finish_watcher);
        } rawMonitorExit(gdata->gc_finish_lock);

        /* Start up listener thread if we need it */
        if ( gdata->socket ) {
            listener_init(env);
        }

        /* Start up cpu sampling thread if we need it */
        if ( gdata->cpu_sampling ) {
            /* Note: this could also get started later (see cpu) */
            cpu_sample_init(env);
        }

        /* Setup event modes */
        setup_event_mode(JNI_FALSE, JVMTI_ENABLE);

        /* Engage tracking (sets Java Tracker field so injections call into
         *     agent library).
         */
        if ( gdata->bci ) {
            tracker_engage(env);
        }

        /* Indicate the VM is initialized now */
        gdata->jvm_initialized = JNI_TRUE;
        gdata->jvm_initializing = JNI_FALSE;

        LOG("cbVMInit end");

    } rawMonitorExit(gdata->data_access_lock);
}

/* JVMTI_EVENT_VM_DEATH */
static void JNICALL
cbVMDeath(jvmtiEnv *jvmti, JNIEnv *env)
{
    /*
     * Use local flag to minimize gdata->dump_lock hold time.
     */
    jboolean need_to_dump = JNI_FALSE;

    LOG("cbVMDeath");

    /* Shutdown thread watching gc_finish, outside CALLBACK locks.
     *   We need to make sure the watcher thread is done doing any cleanup
     *   work before we continue here.
     */
    rawMonitorEnter(gdata->gc_finish_lock); {
        /* Notify watcher thread to finish up, it will send
         *   another notify when done. If the watcher thread is busy
         *   cleaning up, it will detect gc_finish_stop_request when it's done.
         *   Then it sets gc_finish_active to JNI_FALSE and will notify us.
         *   If the watcher thread is waiting to be notified, then the
         *   notification wakes it up.
         *   We do not want to do the VM_DEATH while the gc_finish
         *   watcher thread is in the middle of a cleanup.
         */
        gdata->gc_finish_stop_request = JNI_TRUE;
        rawMonitorNotifyAll(gdata->gc_finish_lock);
        /* Wait for the gc_finish watcher thread to notify us it's done */
        while ( gdata->gc_finish_active ) {
            rawMonitorWait(gdata->gc_finish_lock,0);
        }
    } rawMonitorExit(gdata->gc_finish_lock);

    /* The gc_finish watcher thread should be done now, or done shortly. */


    /* BEGIN_CALLBACK/END_CALLBACK handling. */

    /* The callbackBlock prevents any active callbacks from returning
     *   back to the VM, and also blocks all new callbacks.
     *   We want to prevent any threads from premature death, so
     *   that we don't have worry about that during thread queries
     *   in this final dump process.
     */
    rawMonitorEnter(gdata->callbackBlock); {

        /* We need to wait for all callbacks actively executing to block
         *   on exit, and new ones will block on entry.
         *   The BEGIN_CALLBACK/END_CALLBACK macros keep track of callbacks
         *   that are active.
         *   Once the last active callback is done, it will notify this
         *   thread and block.
         */

        rawMonitorEnter(gdata->callbackLock); {
            /* Turn off native calls */
            if ( gdata->bci ) {
                tracker_disengage(env);
            }
            gdata->vm_death_callback_active = JNI_TRUE;
            while (gdata->active_callbacks > 0) {
                rawMonitorWait(gdata->callbackLock, 0);
            }
        } rawMonitorExit(gdata->callbackLock);

        /* Now we know that no threads will die on us, being blocked
         *   on some event callback, at a minimum ThreadEnd.
         */

        /* Make some basic checks. */
        rawMonitorEnter(gdata->data_access_lock); {
            if ( gdata->jvm_initializing ) {
                HPROF_ERROR(JNI_TRUE, "VM Death during VM Init");
                return;
            }
            if ( !gdata->jvm_initialized ) {
                HPROF_ERROR(JNI_TRUE, "VM Death before VM Init");
                return;
            }
            if (gdata->jvm_shut_down) {
                HPROF_ERROR(JNI_TRUE, "VM Death more than once?");
                return;
            }
        } rawMonitorExit(gdata->data_access_lock);

        /* Shutdown the cpu loop thread */
        if ( gdata->cpu_sampling ) {
            cpu_sample_term(env);
        }

        /* Time to dump the final data */
        rawMonitorEnter(gdata->dump_lock); {

            gdata->jvm_shut_down = JNI_TRUE;

            if (!gdata->dump_in_process) {
                need_to_dump    = JNI_TRUE;
                gdata->dump_in_process = JNI_TRUE;
                /*
                 * Setting gdata->dump_in_process will cause cpu sampling to pause
                 * (if we are sampling). We don't resume sampling after the
                 * dump_all_data() call below because the VM is shutting
                 * down.
                 */
            }

        } rawMonitorExit(gdata->dump_lock);

        /* Dump everything if we need to */
        if (gdata->dump_on_exit && need_to_dump) {

            dump_all_data(env);
        }

        /* Disable all events and callbacks now, all of them.
         *   NOTE: It's important that this be done after the dump
         *         it prevents other threads from messing up the data
         *         because they will block on ThreadStart and ThreadEnd
         *         events due to the CALLBACK block.
         */
        set_callbacks(JNI_FALSE);
        setup_event_mode(JNI_FALSE, JVMTI_DISABLE);
        setup_event_mode(JNI_TRUE, JVMTI_DISABLE);

        /* Write tail of file */
        io_write_file_footer();

    } rawMonitorExit(gdata->callbackBlock);

    /* Shutdown the listener thread and socket, or flush I/O buffers */
    if (gdata->socket) {
        listener_term(env);
    } else {
        io_flush();
    }

    /* Close the file descriptors down */
    if ( gdata->fd  >= 0 ) {
        (void)md_close(gdata->fd);
        gdata->fd = -1;
        if ( gdata->logflags & LOG_CHECK_BINARY ) {
            if (gdata->output_format == 'b' && gdata->output_filename != NULL) {
                check_binary_file(gdata->output_filename);
            }
        }
    }
    if ( gdata->heap_fd  >= 0 ) {
        (void)md_close(gdata->heap_fd);
        gdata->heap_fd = -1;
    }

    if ( gdata->check_fd  >= 0 ) {
        (void)md_close(gdata->check_fd);
        gdata->check_fd = -1;
    }

    /* Remove the temporary heap file */
    if (gdata->heap_dump) {
        (void)remove(gdata->heapfilename);
    }

    /* If logging, dump the tables */
    if ( gdata->logflags & LOG_DUMP_LISTS ) {
        list_all_tables();
    }

    /* Make sure all global references are deleted */
    class_delete_global_references(env);
    loader_delete_global_references(env);
    tls_delete_global_references(env);

}

/* JVMTI_EVENT_THREAD_START */
static void JNICALL
cbThreadStart(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    LOG3("cbThreadStart", "thread is", (int)(long)(ptrdiff_t)thread);

    BEGIN_CALLBACK() {
        event_thread_start(env, thread);
    } END_CALLBACK();
}

/* JVMTI_EVENT_THREAD_END */
static void JNICALL
cbThreadEnd(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    LOG3("cbThreadEnd", "thread is", (int)(long)(ptrdiff_t)thread);

    BEGIN_CALLBACK() {
        event_thread_end(env, thread);
    } END_CALLBACK();
}

/* JVMTI_EVENT_CLASS_FILE_LOAD_HOOK */
static void JNICALL
cbClassFileLoadHook(jvmtiEnv *jvmti_env, JNIEnv* env,
                jclass class_being_redefined, jobject loader,
                const char* name, jobject protection_domain,
                jint class_data_len, const unsigned char* class_data,
                jint* new_class_data_len, unsigned char** new_class_data)
{

    /* WARNING: This will be called before VM_INIT. */

    LOG2("cbClassFileLoadHook:",(name==NULL?"Unknown":name));

    if (!gdata->bci) {
        return;
    }

    BEGIN_CALLBACK() {
        rawMonitorEnter(gdata->data_access_lock); {
            const char *classname;

            if ( gdata->bci_counter == 0 ) {
                /* Prime the system classes */
                class_prime_system_classes();
            }

            gdata->bci_counter++;

            *new_class_data_len = 0;
            *new_class_data     = NULL;

            /* Name could be NULL */
            if ( name == NULL ) {
                classname = ((JavaCrwDemoClassname)
                             (gdata->java_crw_demo_classname_function))
                    (class_data, class_data_len, &my_crw_fatal_error_handler);
                if ( classname == NULL ) {
                    HPROF_ERROR(JNI_TRUE, "No classname in classfile");
                }
            } else {
                classname = strdup(name);
                if ( classname == NULL ) {
                    HPROF_ERROR(JNI_TRUE, "Ran out of malloc() space");
                }
            }

            /* The tracker class itself? */
            if ( strcmp(classname, TRACKER_CLASS_NAME) != 0 ) {
                ClassIndex            cnum;
                int                   system_class;
                unsigned char *       new_image;
                long                  new_length;
                int                   len;
                char                 *signature;
                LoaderIndex           loader_index;

                LOG2("cbClassFileLoadHook injecting class" , classname);

                /* Define a unique class number for this class */
                len              = (int)strlen(classname);
                signature        = HPROF_MALLOC(len+3);
                signature[0]     = JVM_SIGNATURE_CLASS;
                (void)memcpy(signature+1, classname, len);
                signature[len+1] = JVM_SIGNATURE_ENDCLASS;
                signature[len+2] = 0;
                loader_index = loader_find_or_create(env,loader);
                if ( class_being_redefined != NULL ) {
                    cnum  = class_find_or_create(signature, loader_index);
                } else {
                    cnum  = class_create(signature, loader_index);
                }
                HPROF_FREE(signature);
                signature        = NULL;

                /* Make sure class doesn't get unloaded by accident */
                class_add_status(cnum, CLASS_IN_LOAD_LIST);

                /* Is it a system class? */
                system_class = 0;
                if (    (!gdata->jvm_initialized)
                     && (!gdata->jvm_initializing)
                     && ( ( class_get_status(cnum) & CLASS_SYSTEM) != 0
                            || gdata->bci_counter < 8 ) ) {
                    system_class = 1;
                    LOG2(classname, " is a system class");
                }

                new_image = NULL;
                new_length = 0;

                /* Call the class file reader/write demo code */
                ((JavaCrwDemo)(gdata->java_crw_demo_function))(
                    cnum,
                    classname,
                    class_data,
                    class_data_len,
                    system_class,
                    TRACKER_CLASS_NAME,
                    TRACKER_CLASS_SIG,
                    (gdata->cpu_timing)?TRACKER_CALL_NAME:NULL,
                    (gdata->cpu_timing)?TRACKER_CALL_SIG:NULL,
                    (gdata->cpu_timing)?TRACKER_RETURN_NAME:NULL,
                    (gdata->cpu_timing)?TRACKER_RETURN_SIG:NULL,
                    (gdata->obj_watch)?TRACKER_OBJECT_INIT_NAME:NULL,
                    (gdata->obj_watch)?TRACKER_OBJECT_INIT_SIG:NULL,
                    (gdata->obj_watch)?TRACKER_NEWARRAY_NAME:NULL,
                    (gdata->obj_watch)?TRACKER_NEWARRAY_SIG:NULL,
                    &new_image,
                    &new_length,
                    &my_crw_fatal_error_handler,
                    &class_set_methods);

                if ( new_length > 0 ) {
                    unsigned char *jvmti_space;

                    LOG2("cbClassFileLoadHook DID inject this class", classname);
                    jvmti_space = (unsigned char *)jvmtiAllocate((jint)new_length);
                    (void)memcpy((void*)jvmti_space, (void*)new_image, (int)new_length);
                    *new_class_data_len = (jint)new_length;
                    *new_class_data     = jvmti_space; /* VM will deallocate */
                } else {
                    LOG2("cbClassFileLoadHook DID NOT inject this class", classname);
                    *new_class_data_len = 0;
                    *new_class_data     = NULL;
                }
                if ( new_image != NULL ) {
                    (void)free((void*)new_image); /* Free malloc() space with free() */
                }
            }
            (void)free((void*)classname);
        } rawMonitorExit(gdata->data_access_lock);
    } END_CALLBACK();
}

/* JVMTI_EVENT_CLASS_LOAD */
static void JNICALL
cbClassLoad(jvmtiEnv *jvmti, JNIEnv *env, jthread thread, jclass klass)
{

    /* WARNING: This MAY be called before VM_INIT. */

    LOG("cbClassLoad");

    BEGIN_CALLBACK() {
        rawMonitorEnter(gdata->data_access_lock); {

            WITH_LOCAL_REFS(env, 1) {
                jobject loader;

                loader = getClassLoader(klass);
                event_class_load(env, thread, klass, loader);
            } END_WITH_LOCAL_REFS;

        } rawMonitorExit(gdata->data_access_lock);
    } END_CALLBACK();
}

/* JVMTI_EVENT_CLASS_PREPARE */
static void JNICALL
cbClassPrepare(jvmtiEnv *jvmti, JNIEnv *env, jthread thread, jclass klass)
{

    /* WARNING: This will be called before VM_INIT. */

    LOG("cbClassPrepare");

    BEGIN_CALLBACK() {
        rawMonitorEnter(gdata->data_access_lock); {

            WITH_LOCAL_REFS(env, 1) {
                jobject loader;

                loader = NULL;
                loader = getClassLoader(klass);
                event_class_prepare(env, thread, klass, loader);
            } END_WITH_LOCAL_REFS;

        } rawMonitorExit(gdata->data_access_lock);
    } END_CALLBACK();

}

/* JVMTI_EVENT_DATA_DUMP_REQUEST */
static void JNICALL
cbDataDumpRequest(jvmtiEnv *jvmti)
{
    jboolean need_to_dump;

    LOG("cbDataDumpRequest");

    BEGIN_CALLBACK() {
        need_to_dump = JNI_FALSE;
        rawMonitorEnter(gdata->dump_lock); {
            if (!gdata->dump_in_process) {
                need_to_dump    = JNI_TRUE;
                gdata->dump_in_process = JNI_TRUE;
            }
        } rawMonitorExit(gdata->dump_lock);

        if (need_to_dump) {
            dump_all_data(getEnv());

            rawMonitorEnter(gdata->dump_lock); {
                gdata->dump_in_process = JNI_FALSE;
            } rawMonitorExit(gdata->dump_lock);

            if (gdata->cpu_sampling && !gdata->jvm_shut_down) {
                cpu_sample_on(NULL, 0); /* resume sampling */
            }
        }
    } END_CALLBACK();

}

/* JVMTI_EVENT_EXCEPTION_CATCH */
static void JNICALL
cbExceptionCatch(jvmtiEnv *jvmti, JNIEnv* env,
                jthread thread, jmethodID method, jlocation location,
                jobject exception)
{
    LOG("cbExceptionCatch");

    BEGIN_CALLBACK() {
        event_exception_catch(env, thread, method, location, exception);
    } END_CALLBACK();
}

/* JVMTI_EVENT_MONITOR_WAIT */
static void JNICALL
cbMonitorWait(jvmtiEnv *jvmti, JNIEnv* env,
                jthread thread, jobject object, jlong timeout)
{
    LOG("cbMonitorWait");

    BEGIN_CALLBACK() {
        monitor_wait_event(env, thread, object, timeout);
    } END_CALLBACK();
}

/* JVMTI_EVENT_MONITOR_WAITED */
static void JNICALL
cbMonitorWaited(jvmtiEnv *jvmti, JNIEnv* env,
                jthread thread, jobject object, jboolean timed_out)
{
    LOG("cbMonitorWaited");

    BEGIN_CALLBACK() {
        monitor_waited_event(env, thread, object, timed_out);
    } END_CALLBACK();
}

/* JVMTI_EVENT_MONITOR_CONTENDED_ENTER */
static void JNICALL
cbMonitorContendedEnter(jvmtiEnv *jvmti, JNIEnv* env,
                jthread thread, jobject object)
{
    LOG("cbMonitorContendedEnter");

    BEGIN_CALLBACK() {
        monitor_contended_enter_event(env, thread, object);
    } END_CALLBACK();
}

/* JVMTI_EVENT_MONITOR_CONTENDED_ENTERED */
static void JNICALL
cbMonitorContendedEntered(jvmtiEnv *jvmti, JNIEnv* env,
                jthread thread, jobject object)
{
    LOG("cbMonitorContendedEntered");

    BEGIN_CALLBACK() {
        monitor_contended_entered_event(env, thread, object);
    } END_CALLBACK();
}

/* JVMTI_EVENT_GARBAGE_COLLECTION_START */
static void JNICALL
cbGarbageCollectionStart(jvmtiEnv *jvmti)
{
    LOG("cbGarbageCollectionStart");

    /* Only calls to Allocate, Deallocate, RawMonitorEnter & RawMonitorExit
     *   are allowed here (see the JVMTI Spec).
     */

    gdata->gc_start_time = md_get_timemillis();
}

/* JVMTI_EVENT_GARBAGE_COLLECTION_FINISH */
static void JNICALL
cbGarbageCollectionFinish(jvmtiEnv *jvmti)
{
    LOG("cbGarbageCollectionFinish");

    /* Only calls to Allocate, Deallocate, RawMonitorEnter & RawMonitorExit
     *   are allowed here (see the JVMTI Spec).
     */

    if ( gdata->gc_start_time != -1L ) {
        gdata->time_in_gc += (md_get_timemillis() - gdata->gc_start_time);
        gdata->gc_start_time = -1L;
    }

    /* Increment gc_finish counter, notify watcher thread */
    rawMonitorEnter(gdata->gc_finish_lock); {
        /* If VM_DEATH is trying to shut it down, don't do anything at all.
         *    Never send notify if VM_DEATH wants the watcher thread to quit.
         */
        if ( gdata->gc_finish_active ) {
            gdata->gc_finish++;
            rawMonitorNotifyAll(gdata->gc_finish_lock);
        }
    } rawMonitorExit(gdata->gc_finish_lock);
}

/* JVMTI_EVENT_OBJECT_FREE */
static void JNICALL
cbObjectFree(jvmtiEnv *jvmti, jlong tag)
{
    LOG3("cbObjectFree", "tag", (int)tag);

    /* Only calls to Allocate, Deallocate, RawMonitorEnter & RawMonitorExit
     *   are allowed here (see the JVMTI Spec).
     */

    HPROF_ASSERT(tag!=(jlong)0);
    rawMonitorEnter(gdata->object_free_lock); {
        if ( !gdata->jvm_shut_down ) {
            Stack *stack;

            stack = gdata->object_free_stack;
            if ( stack == NULL ) {
                gdata->object_free_stack = stack_init(512, 512, sizeof(jlong));
                stack = gdata->object_free_stack;
            }
            stack_push(stack, (void*)&tag);
        }
    } rawMonitorExit(gdata->object_free_lock);
}

static void
set_callbacks(jboolean on)
{
    jvmtiEventCallbacks callbacks;

    (void)memset(&callbacks,0,sizeof(callbacks));
    if ( ! on ) {
        setEventCallbacks(&callbacks);
        return;
    }

    /* JVMTI_EVENT_VM_INIT */
    callbacks.VMInit                     = &cbVMInit;
    /* JVMTI_EVENT_VM_DEATH */
    callbacks.VMDeath                    = &cbVMDeath;
    /* JVMTI_EVENT_THREAD_START */
    callbacks.ThreadStart                = &cbThreadStart;
    /* JVMTI_EVENT_THREAD_END */
    callbacks.ThreadEnd                  = &cbThreadEnd;
    /* JVMTI_EVENT_CLASS_FILE_LOAD_HOOK */
    callbacks.ClassFileLoadHook          = &cbClassFileLoadHook;
    /* JVMTI_EVENT_CLASS_LOAD */
    callbacks.ClassLoad                  = &cbClassLoad;
    /* JVMTI_EVENT_CLASS_PREPARE */
    callbacks.ClassPrepare               = &cbClassPrepare;
    /* JVMTI_EVENT_DATA_DUMP_REQUEST */
    callbacks.DataDumpRequest            = &cbDataDumpRequest;
    /* JVMTI_EVENT_EXCEPTION_CATCH */
    callbacks.ExceptionCatch             = &cbExceptionCatch;
    /* JVMTI_EVENT_MONITOR_WAIT */
    callbacks.MonitorWait                = &cbMonitorWait;
    /* JVMTI_EVENT_MONITOR_WAITED */
    callbacks.MonitorWaited              = &cbMonitorWaited;
    /* JVMTI_EVENT_MONITOR_CONTENDED_ENTER */
    callbacks.MonitorContendedEnter      = &cbMonitorContendedEnter;
    /* JVMTI_EVENT_MONITOR_CONTENDED_ENTERED */
    callbacks.MonitorContendedEntered    = &cbMonitorContendedEntered;
    /* JVMTI_EVENT_GARBAGE_COLLECTION_START */
    callbacks.GarbageCollectionStart     = &cbGarbageCollectionStart;
    /* JVMTI_EVENT_GARBAGE_COLLECTION_FINISH */
    callbacks.GarbageCollectionFinish    = &cbGarbageCollectionFinish;
    /* JVMTI_EVENT_OBJECT_FREE */
    callbacks.ObjectFree                 = &cbObjectFree;

    setEventCallbacks(&callbacks);

}

static void
getCapabilities(void)
{
    jvmtiCapabilities needed_capabilities;
    jvmtiCapabilities potential_capabilities;

    /* Fill in ones that we must have */
    (void)memset(&needed_capabilities,0,sizeof(needed_capabilities));
    needed_capabilities.can_generate_garbage_collection_events   = 1;
    needed_capabilities.can_tag_objects                          = 1;
    if (gdata->bci) {
        needed_capabilities.can_generate_all_class_hook_events   = 1;
    }
    if (gdata->obj_watch) {
        needed_capabilities.can_generate_object_free_events      = 1;
    }
    if (gdata->cpu_timing || gdata->cpu_sampling) {
        #if 0 /* Not needed until we call JVMTI for CpuTime */
        needed_capabilities.can_get_thread_cpu_time              = 1;
        needed_capabilities.can_get_current_thread_cpu_time      = 1;
        #endif
        needed_capabilities.can_generate_exception_events        = 1;
    }
    if (gdata->monitor_tracing) {
        #if 0 /* Not needed until we call JVMTI for CpuTime */
        needed_capabilities.can_get_thread_cpu_time              = 1;
        needed_capabilities.can_get_current_thread_cpu_time      = 1;
        #endif
        needed_capabilities.can_get_owned_monitor_info           = 1;
        needed_capabilities.can_get_current_contended_monitor    = 1;
        needed_capabilities.can_get_monitor_info                 = 1;
        needed_capabilities.can_generate_monitor_events          = 1;
    }

    /* Get potential capabilities */
    getPotentialCapabilities(&potential_capabilities);

    /* Some capabilities would be nicer to have */
    needed_capabilities.can_get_source_file_name        =
        potential_capabilities.can_get_source_file_name;
    needed_capabilities.can_get_line_numbers    =
        potential_capabilities.can_get_line_numbers;

    /* Add the capabilities */
    addCapabilities(&needed_capabilities);

}

/* Dynamic library loading */
static void *
load_library(char *name)
{
    char  lname[FILENAME_MAX+1];
    char  err_buf[256+FILENAME_MAX+1];
    char *boot_path;
    void *handle;

    handle = NULL;

    /* The library may be located in different ways, try both, but
     *   if it comes from outside the SDK/jre it isn't ours.
     */
    getSystemProperty("sun.boot.library.path", &boot_path);
    md_build_library_name(lname, FILENAME_MAX, boot_path, name);
    if ( strlen(lname) == 0 ) {
        HPROF_ERROR(JNI_TRUE, "Could not find library");
    }
    jvmtiDeallocate(boot_path);
    handle = md_load_library(lname, err_buf, (int)sizeof(err_buf));
    if ( handle == NULL ) {
        /* This may be necessary on Windows. */
        md_build_library_name(lname, FILENAME_MAX, "", name);
        if ( strlen(lname) == 0 ) {
            HPROF_ERROR(JNI_TRUE, "Could not find library");
        }
        handle = md_load_library(lname, err_buf, (int)sizeof(err_buf));
        if ( handle == NULL ) {
            HPROF_ERROR(JNI_TRUE, err_buf);
        }
    }
    return handle;
}

/* Lookup dynamic function pointer in shared library */
static void *
lookup_library_symbol(void *library, char **symbols, int nsymbols)
{
    void *addr;
    int   i;

    addr = NULL;
    for( i = 0 ; i < nsymbols; i++ ) {
        addr = md_find_library_entry(library, symbols[i]);
        if ( addr != NULL ) {
            break;
        }
    }
    if ( addr == NULL ) {
        char errmsg[256];

        (void)md_snprintf(errmsg, sizeof(errmsg),
                    "Cannot find library symbol '%s'", symbols[0]);
        HPROF_ERROR(JNI_TRUE, errmsg);
    }
    return addr;
}

/* ------------------------------------------------------------------- */
/* The OnLoad interface */

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
{
    char *boot_path = NULL;
    char npt_lib[JVM_MAXPATHLEN];

    /* See if it's already loaded */
    if ( gdata!=NULL && gdata->isLoaded==JNI_TRUE ) {
        HPROF_ERROR(JNI_TRUE, "Cannot load this JVM TI agent twice, check your java command line for duplicate hprof options.");
        return JNI_ERR;
    }

    gdata = get_gdata();

    gdata->isLoaded = JNI_TRUE;

    error_setup();

    LOG2("Agent_OnLoad", "gdata setup");

    gdata->jvm = vm;

    /* Get the JVMTI environment */
    getJvmti();

#ifndef SKIP_NPT
    getSystemProperty("sun.boot.library.path", &boot_path);
    /* Load in NPT library for character conversions */
    md_build_library_name(npt_lib, sizeof(npt_lib), boot_path, NPT_LIBNAME);
    if ( strlen(npt_lib) == 0 ) {
        HPROF_ERROR(JNI_TRUE, "Could not find npt library");
    }
    jvmtiDeallocate(boot_path);
    NPT_INITIALIZE(npt_lib, &(gdata->npt), NPT_VERSION, NULL);
    if ( gdata->npt == NULL ) {
        HPROF_ERROR(JNI_TRUE, "Cannot load npt library");
    }
    gdata->npt->utf = (gdata->npt->utfInitialize)(NULL);
    if ( gdata->npt->utf == NULL ) {
        HPROF_ERROR(JNI_TRUE, "Cannot initialize npt utf functions");
    }
#endif

    /* Lock needed to protect debug_malloc() code, which is not MT safe */
    #ifdef DEBUG
        gdata->debug_malloc_lock = createRawMonitor("HPROF debug_malloc lock");
    #endif

    parse_options(options);

    LOG2("Agent_OnLoad", "Has jvmtiEnv and options parsed");

    /* Initialize machine dependent code (micro state accounting) */
    md_init();

    string_init();      /* Table index values look like: 0x10000000 */

    class_init();       /* Table index values look like: 0x20000000 */
    tls_init();         /* Table index values look like: 0x30000000 */
    trace_init();       /* Table index values look like: 0x40000000 */
    object_init();      /* Table index values look like: 0x50000000 */

    site_init();        /* Table index values look like: 0x60000000 */
    frame_init();       /* Table index values look like: 0x70000000 */
    monitor_init();     /* Table index values look like: 0x80000000 */
    loader_init();      /* Table index values look like: 0x90000000 */

    LOG2("Agent_OnLoad", "Tables initialized");

    if ( gdata->pause ) {
        error_do_pause();
    }

    getCapabilities();

    /* Set the JVMTI callback functions  (do this only once)*/
    set_callbacks(JNI_TRUE);

    /* Create basic locks */
    gdata->dump_lock          = createRawMonitor("HPROF dump lock");
    gdata->data_access_lock   = createRawMonitor("HPROF data access lock");
    gdata->callbackLock       = createRawMonitor("HPROF callback lock");
    gdata->callbackBlock      = createRawMonitor("HPROF callback block");
    gdata->object_free_lock   = createRawMonitor("HPROF object free lock");
    gdata->gc_finish_lock     = createRawMonitor("HPROF gc_finish lock");

    /* Set Onload events mode. */
    setup_event_mode(JNI_TRUE, JVMTI_ENABLE);

    LOG2("Agent_OnLoad", "JVMTI capabilities, callbacks and initial notifications setup");

    /* Used in VM_DEATH to wait for callbacks to complete */
    gdata->jvm_initializing             = JNI_FALSE;
    gdata->jvm_initialized              = JNI_FALSE;
    gdata->vm_death_callback_active     = JNI_FALSE;
    gdata->active_callbacks             = 0;

    /* Write the header information */
    io_setup();

    /* We sample the start time now so that the time increments can be
     *    placed in the various heap dump segments in micro seconds.
     */
    gdata->micro_sec_ticks = md_get_microsecs();

    /* Load java_crw_demo library and find function "java_crw_demo" */
    if ( gdata->bci ) {

        /* Load the library or get the handle to it */
        gdata->java_crw_demo_library = load_library("java_crw_demo");

        { /* "java_crw_demo" */
            static char *symbols[]  = JAVA_CRW_DEMO_SYMBOLS;
            gdata->java_crw_demo_function =
                   lookup_library_symbol(gdata->java_crw_demo_library,
                              symbols, (int)(sizeof(symbols)/sizeof(char*)));
        }
        { /* "java_crw_demo_classname" */
            static char *symbols[] = JAVA_CRW_DEMO_CLASSNAME_SYMBOLS;
            gdata->java_crw_demo_classname_function =
                   lookup_library_symbol(gdata->java_crw_demo_library,
                              symbols, (int)(sizeof(symbols)/sizeof(char*)));
        }
    }

    return JNI_OK;
}

JNIEXPORT void JNICALL
Agent_OnUnload(JavaVM *vm)
{
    Stack *stack;

    LOG("Agent_OnUnload");

    gdata->isLoaded = JNI_FALSE;

    stack = gdata->object_free_stack;
    gdata->object_free_stack = NULL;
    if ( stack != NULL ) {
        stack_term(stack);
    }

    io_cleanup();
    loader_cleanup();
    tls_cleanup();
    monitor_cleanup();
    trace_cleanup();
    site_cleanup();
    object_cleanup();
    frame_cleanup();
    class_cleanup();
    string_cleanup();

    /* Deallocate any memory in gdata */
    if ( gdata->net_hostname != NULL ) {
        HPROF_FREE(gdata->net_hostname);
    }
    if ( gdata->utf8_output_filename != NULL ) {
        HPROF_FREE(gdata->utf8_output_filename);
    }
    if ( gdata->output_filename != NULL ) {
        HPROF_FREE(gdata->output_filename);
    }
    if ( gdata->heapfilename != NULL ) {
        HPROF_FREE(gdata->heapfilename);
    }
    if ( gdata->checkfilename != NULL ) {
        HPROF_FREE(gdata->checkfilename);
    }
    if ( gdata->options != NULL ) {
        HPROF_FREE(gdata->options);
    }

    /* Verify all allocated memory has been taken care of. */
    malloc_police();

    /* Cleanup is hard to do when other threads might still be running
     *  so we skip destroying some raw monitors which still might be in use
     *  and we skip disposal of the jvmtiEnv* which might still be needed.
     *  Only raw monitors that could be held by other threads are left
     *  alone. So we explicitly do NOT do this:
     *      destroyRawMonitor(gdata->callbackLock);
     *      destroyRawMonitor(gdata->callbackBlock);
     *      destroyRawMonitor(gdata->gc_finish_lock);
     *      destroyRawMonitor(gdata->object_free_lock);
     *      destroyRawMonitor(gdata->listener_loop_lock);
     *      destroyRawMonitor(gdata->cpu_loop_lock);
     *      disposeEnvironment();
     *      gdata->jvmti = NULL;
     */

    /* Destroy basic locks */
    destroyRawMonitor(gdata->dump_lock);
    gdata->dump_lock = NULL;
    destroyRawMonitor(gdata->data_access_lock);
    gdata->data_access_lock = NULL;
    if ( gdata->cpu_sample_lock != NULL ) {
        destroyRawMonitor(gdata->cpu_sample_lock);
        gdata->cpu_sample_lock = NULL;
    }
    #ifdef DEBUG
        destroyRawMonitor(gdata->debug_malloc_lock);
        gdata->debug_malloc_lock = NULL;
    #endif

    /* Unload java_crw_demo library */
    if ( gdata->bci && gdata->java_crw_demo_library != NULL ) {
        md_unload_library(gdata->java_crw_demo_library);
        gdata->java_crw_demo_library = NULL;
    }

    /* You would think you could clear out gdata and set it to NULL, but
     *   turns out that isn't a good idea.  Some of the threads could be
     *   blocked inside the CALLBACK*() macros, where they got blocked up
     *   waiting for the VM_DEATH callback to complete. They only have
     *   some raw monitor actions to do, but they need access to gdata to do it.
     *   So do not do this:
     *       (void)memset(gdata, 0, sizeof(GlobalData));
     *       gdata = NULL;
     */
}
