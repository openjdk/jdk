/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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


#ifndef HPROF_ERROR_H
#define HPROF_ERROR_H

/* Use THIS_FILE when it is available. */
#ifndef THIS_FILE
    #define THIS_FILE __FILE__
#endif

/* Macros over assert and error functions so we can capture the source loc. */

#define HPROF_BOOL(x) ((jboolean)((x)==0?JNI_FALSE:JNI_TRUE))

#define HPROF_ERROR(fatal,msg) \
    error_handler(HPROF_BOOL(fatal), JVMTI_ERROR_NONE, msg, THIS_FILE, __LINE__)

#define HPROF_JVMTI_ERROR(error,msg) \
    error_handler(HPROF_BOOL(error!=JVMTI_ERROR_NONE), \
            error, msg, THIS_FILE, __LINE__)

#if defined(DEBUG) || !defined(NDEBUG)
    #define HPROF_ASSERT(cond) \
        (((int)(cond))?(void)0:error_assert(#cond, THIS_FILE, __LINE__))
#else
    #define HPROF_ASSERT(cond)
#endif

#define LOG_DUMP_MISC           0x1 /* Misc. logging info */
#define LOG_DUMP_LISTS          0x2 /* Dump tables at vm init and death */
#define LOG_CHECK_BINARY        0x4 /* If format=b, verify binary format */

#ifdef HPROF_LOGGING
    #define LOG_STDERR(args) \
        { \
            if ( gdata != NULL && (gdata->logflags & LOG_DUMP_MISC) ) { \
                (void)fprintf args ; \
            } \
        }
#else
    #define LOG_STDERR(args)
#endif

#define LOG_FORMAT(format)      "HPROF LOG: " format " [%s:%d]\n"

#define LOG1(str1)              LOG_STDERR((stderr, LOG_FORMAT("%s"), \
                                    str1, THIS_FILE, __LINE__ ))
#define LOG2(str1,str2)         LOG_STDERR((stderr, LOG_FORMAT("%s %s"), \
                                    str1, str2, THIS_FILE, __LINE__ ))
#define LOG3(str1,str2,num)     LOG_STDERR((stderr, LOG_FORMAT("%s %s 0x%x"), \
                                    str1, str2, num, THIS_FILE, __LINE__ ))

#define LOG(str) LOG1(str)

void       error_handler(jboolean fatal, jvmtiError error,
                const char *message, const char *file, int line);
void       error_assert(const char *condition, const char *file, int line);
void       error_exit_process(int exit_code);
void       error_do_pause(void);
void       error_setup(void);
void       debug_message(const char * format, ...);
void       verbose_message(const char * format, ...);

#endif
