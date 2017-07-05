/*
 * Copyright (c) 1996, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <process.h>

/*
 * This is a primitive bootstrapping utility for executing Java CGI
 * programs, specifically Java RMI's CGI HTTP forwarding mechanism
 *
 * It executes the Java interpreter with options to define
 * properties corresponding to the environment variables set by the
 * CGI 1.0 specification and runs the target class.
 *
 * The following assumptions are made:
 *     - the Java interpreter can be located by the system
 *       PATH variable
 *     - for RMI 1.1 prebeta release, the target class can be located
 *       using the system CLASSPATH variable
 */

/* name of Java interpreter executable */
#define JAVA_NAME "java"

/* name of Java class to execute with interpreter */
#define CLASS_NAME "sun.rmi.transport.proxy.CGIHandler"

/* names of environment variables set in CGI 1.0 interface */
static char *var_names[] = {
    "AUTH_TYPE",
    "CONTENT_LENGTH",
    "CONTENT_TYPE",
    "GATEWAY_INTERFACE",
    "HTTP_ACCEPT",
    "PATH_INFO",
    "PATH_TRANSLATED",
    "QUERY_STRING",
    "REMOTE_ADDR",
    "REMOTE_HOST",
    "REMOTE_IDENT",
    "REMOTE_USER",
    "REQUEST_METHOD",
    "SCRIPT_NAME",
    "SERVER_NAME",
    "SERVER_PORT",
    "SERVER_PROTOCOL",
    "SERVER_SOFTWARE"
};

#define NUM_VARS (sizeof(var_names) / sizeof(var_names[0]))

/* file static functions */
static void server_error(char *);

/*
 * Program entry point: set up arguments and invoke Java interpreter.
 */
int
main(
    int     argc,
    char   *argv[]
)
{
    int     i;                  /* loop index variable */
    char  **args;               /* array to store arguments to interpreter */
    int     n = 0;              /* next index to fill in argument array */

    /* allocate space for argument list */
    args = (char **)            /* allocate space for: */
        malloc((1               /* executable name */
                + NUM_VARS      /* property definition for each variable */
                + 1             /* class name */
                + 1)            /* terminating NULL */
                * sizeof(*args));
    if (args == NULL) {
        server_error("memory allocation failure");
        return 1;
    }

    /* first argument: name of java interpreter */
    args[n ++] = JAVA_NAME;

    /* next arguments: define CGI variables as properties to Java VM */
    for (i = 0; i < NUM_VARS; ++ i) {
        char *name = var_names[i];      /* name of variable */
        char *value;                    /* value of variable */
        char *buffer;                   /* buffer to store argument string */

        value = getenv(name);
        if (value == NULL)              /* if variable undefined, */
            value = "";                 /* use empty string */

        buffer = (char *)               /* allocate space for: */
            malloc((2                   /* "-D" */
                    + strlen(name)      /* variable name */
                    + 2                 /* "=\"" */
                    + strlen(value)     /* variable value */
                    + 2)                /* "\"" and terminating '\0' */
                    * sizeof(*buffer));
        if (buffer == NULL) {
            server_error("memory allocation failure");
            return 1;
        }

        /* construct property definition parameter */
        sprintf(buffer, "-D%s=\"%s\"", name, value);

        args[n ++] = buffer;            /* add to argument list */
    }

    /* last argument: name of class to execute */
    args[n ++] = CLASS_NAME;

    args[n ++] = NULL;          /* terminate argument list */

    _execvp(JAVA_NAME, args);   /* execute java interpreter */

    /* if exec call returns, there was an error */
    server_error("interpreter execution failure");
    return 1;
}

/*
 * Return primitive error message to server because of some failure in
 * this program.  (This could be embellished to an HTML formatted error
 * message.)
 */
static void
server_error(
    char  *message
)
{
    /*
     * NOTE: CGI 1.0 spec uses "\n" (unlike "\r\n"
     * for HTTP 1.0) for line termination
     */
    printf("Status: 500 Server Error: %s\n", message);
    printf("Content-type: text/plain\n");
    printf("\n");
    printf("%s", message);
}
