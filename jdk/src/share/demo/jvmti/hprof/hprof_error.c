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


#include "hprof.h"

/* The error handling logic. */

/*
 * Most hprof error processing and error functions are kept here, along with
 *   termination functions and signal handling (used in debug version only).
 *
 */

#include <signal.h>

static int p = 1; /* Used with pause=y|n option */

/* Private functions */

static void
error_message(const char * format, ...)
{
    va_list ap;

    va_start(ap, format);
    (void)vfprintf(stderr, format, ap);
    va_end(ap);
}

static void
error_abort(void)
{
    /* Important to remove existing signal handler */
    (void)signal(SIGABRT, NULL);
    error_message("HPROF DUMPING CORE\n");
    abort();        /* Sends SIGABRT signal, usually also caught by libjvm */
}

static void
signal_handler(int sig)
{
    /* Caught a signal, most likely a SIGABRT */
    error_message("HPROF SIGNAL %d TERMINATED PROCESS\n", sig);
    error_abort();
}

static void
setup_signal_handler(int sig)
{
    /* Only if debug version or debug=y */
    if ( gdata->debug ) {
        (void)signal(sig, (void(*)(int))(void*)&signal_handler);
    }
}

static void
terminate_everything(jint exit_code)
{
    if ( exit_code > 0 ) {
        /* Could be a fatal error or assert error or a sanity error */
        error_message("HPROF TERMINATED PROCESS\n");
        if ( gdata->coredump || gdata->debug ) {
            /* Core dump here by request */
            error_abort();
        }
    }
    /* Terminate the process */
    error_exit_process(exit_code);
}

/* External functions */

void
error_setup(void)
{
    setup_signal_handler(SIGABRT);
}

void
error_do_pause(void)
{
    int pid = md_getpid();
    int timeleft = 600; /* 10 minutes max */
    int interval = 10;  /* 10 second message check */

    /*LINTED*/
    error_message("\nHPROF pause for PID %d\n", (int)pid);
    while ( p && timeleft > 0 ) {
        md_sleep(interval); /* 'assign p=0' to stop pause loop */
        timeleft -= interval;
    }
    if ( timeleft <= 0 ) {
        error_message("\n HPROF pause got tired of waiting and gave up.\n");
    }
}

void
error_exit_process(int exit_code)
{
    exit(exit_code);
}

static const char *
source_basename(const char *file)
{
    const char *p;

    if ( file == NULL ) {
        return "UnknownSourceFile";
    }
    p = strrchr(file, '/');
    if ( p == NULL ) {
        p = strrchr(file, '\\');
    }
    if ( p == NULL ) {
        p = file;
    } else {
        p++; /* go past / */
    }
    return p;
}

void
error_assert(const char *condition, const char *file, int line)
{
    error_message("ASSERTION FAILURE: %s [%s:%d]\n", condition,
        source_basename(file), line);
    error_abort();
}

void
error_handler(jboolean fatal, jvmtiError error,
                const char *message, const char *file, int line)
{
    char *error_name;

    if ( message==NULL ) {
        message = "";
    }
    if ( error != JVMTI_ERROR_NONE ) {
        error_name = getErrorName(error);
        if ( error_name == NULL ) {
            error_name = "?";
        }
        error_message("HPROF ERROR: %s (JVMTI Error %s(%d)) [%s:%d]\n",
                            message, error_name, error,
                            source_basename(file), line);
    } else {
        error_message("HPROF ERROR: %s [%s:%d]\n", message,
                            source_basename(file), line);
    }
    if ( fatal || gdata->errorexit ) {
        /* If it's fatal, or the user wants termination on any error, die */
        terminate_everything(9);
    }
}

void
debug_message(const char * format, ...)
{
    va_list ap;

    va_start(ap, format);
    (void)vfprintf(stderr, format, ap);
    va_end(ap);
}

void
verbose_message(const char * format, ...)
{
    if ( gdata->verbose ) {
        va_list ap;

        va_start(ap, format);
        (void)vfprintf(stderr, format, ap);
        va_end(ap);
    }
}
