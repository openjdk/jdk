/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

#ifndef JDWP_ERROR_MESSAGES_H
#define JDWP_ERROR_MESSAGES_H

/* It is assumed that ALL strings are UTF-8 safe on entry */
#define TTY_MESSAGE(args) ( tty_message args )
#define ERROR_MESSAGE(args) ( \
                LOG_ERROR(args), \
                error_message args )

void print_message(FILE *fp, const char *prefix,  const char *suffix,
                   const char *format, ...);
void error_message(const char *, ...);
void tty_message(const char *, ...);
void jdiAssertionFailed(char *fileName, int lineNumber, char *msg);

const char * jvmtiErrorText(jvmtiError);
const char * eventText(int);
const char * jdwpErrorText(jdwpError);

#define EXIT_ERROR(error,msg) \
        { \
                print_message(stderr, "JDWP exit error ", "\n", \
                        "%s(%d): %s [%s:%d]", \
                        jvmtiErrorText((jvmtiError)error), error, (msg==NULL?"":msg), \
                        __FILE__, __LINE__); \
                debugInit_exit((jvmtiError)error, msg); \
        }

#define JDI_ASSERT(expression)  \
do {                            \
    if (gdata && gdata->assertOn && !(expression)) {            \
        jdiAssertionFailed(__FILE__, __LINE__, #expression); \
    }                                           \
} while (0)

#define JDI_ASSERT_MSG(expression, msg)  \
do {                            \
    if (gdata && gdata->assertOn && !(expression)) {            \
        jdiAssertionFailed(__FILE__, __LINE__, msg); \
    }                                           \
} while (0)

#define JDI_ASSERT_FAILED(msg)  \
   jdiAssertionFailed(__FILE__, __LINE__, msg)

void do_pause(void);

#endif
