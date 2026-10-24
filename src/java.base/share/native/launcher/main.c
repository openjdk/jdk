/*
 * Copyright (c) 1995, 2025, Oracle and/or its affiliates. All rights reserved.
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


/*
 * This file contains the main entry point into the launcher code
 * this is the only file which will be repeatedly compiled by other
 * tools. The rest of the files will be linked in.
 */

#include "java.h"
#include "jli_util.h"
#include "jni.h"

// Unused, but retained for JLI_Launch compatibility
#define DOT_VERSION "0.0"

// This is reported when requesting a full version
static char* launcher = LAUNCHER_NAME;

// This is used as the name of the executable in the help message
static char* progname = PROGNAME;

#ifdef JAVA_ARGS
static const char* jargs[] = JAVA_ARGS;
#else
static const char** jargs = NULL;
#endif
static int jargc;

static jboolean cpwildcard = CLASSPATH_WILDCARDS;
static jboolean disable_argfile = DISABLE_ARGFILE;

#ifdef STATIC_BUILD
static void check_relauncher_argument(char* arg) {
    if (strcmp(arg, "-J-DjavaLauncherWildcards=false") == 0) {
        cpwildcard = JNI_FALSE;
    }
    const char *progname_prefix = "-J-DjavaLauncherProgname=";
    size_t progname_prefix_len = strlen(progname_prefix);
    if (strncmp(arg, progname_prefix, progname_prefix_len) == 0) {
        progname = arg + progname_prefix_len;
    }
    const char *args_prefix = "-J-DjavaLauncherArgs=";
    size_t args_prefix_len = strlen(args_prefix);
    if (strncmp(arg, args_prefix, args_prefix_len) == 0) {
        char* java_args_ptr = arg + args_prefix_len;
        size_t java_args_len = strlen(arg) - args_prefix_len;

        JLI_List java_args = JLI_List_new(java_args_len);
        char* next_space;
        while ((next_space = strchr(java_args_ptr, ' ')) != NULL) {
            size_t next_arg_len = next_space - java_args_ptr;
            JLI_List_addSubstring(java_args, java_args_ptr, next_arg_len);
            java_args_ptr = next_space + 1;
        }
        JLI_List_add(java_args, java_args_ptr);

        jargc = (int) java_args->size;
        jargs = (const char**) java_args->elements;
    }
}
#endif

/*
 * Entry point.
 */
#ifdef JAVAW

char **__initenv;

int WINAPI
WinMain(HINSTANCE inst, HINSTANCE previnst, LPSTR cmdline, int cmdshow)
{
    const jboolean javaw = JNI_TRUE;

    __initenv = _environ;

#else /* JAVAW */
JNIEXPORT int
main(int argc, char **argv)
{
    const jboolean javaw = JNI_FALSE;
#endif /* JAVAW */

    int margc;
    char** margv;

    jargc = (sizeof(jargs) / sizeof(char *)) > 1
        ? sizeof(jargs) / sizeof(char *)
        : 0; // ignore the null terminator index

#ifdef STATIC_BUILD
        // Relaunchers always give -J-DjavaLauncherArgFiles as the first argument, if present
        // We must check disable_argfile before calling JLI_InitArgProcessing.
        if (argc > 1 && strcmp(argv[1], "-J-DjavaLauncherArgFiles=false") == 0) {
            disable_argfile = JNI_TRUE;
        }
#endif

    JLI_InitArgProcessing(jargc > 0, disable_argfile);

#ifdef _WIN32
    {
        int i = 0;
        if (getenv(JLDEBUG_ENV_ENTRY) != NULL) {
            printf("Windows original main args:\n");
            for (i = 0 ; i < __argc ; i++) {
                printf("wwwd_args[%d] = %s\n", i, __argv[i]);
            }
        }
    }

    // Obtain the command line in UTF-16, then convert it to ANSI code page
    // without the "best-fit" option
    LPWSTR wcCmdline = GetCommandLineW();
    int mbSize = WideCharToMultiByte(CP_ACP,
        WC_NO_BEST_FIT_CHARS | WC_COMPOSITECHECK | WC_DEFAULTCHAR,
        wcCmdline, -1, NULL, 0, NULL, NULL);
    // If the call to WideCharToMultiByte() fails, it returns 0, which
    // will then make the following JLI_MemAlloc() to issue exit(1)
    LPSTR mbCmdline = JLI_MemAlloc(mbSize);
    if (WideCharToMultiByte(CP_ACP, WC_NO_BEST_FIT_CHARS | WC_COMPOSITECHECK | WC_DEFAULTCHAR,
        wcCmdline, -1, mbCmdline, mbSize, NULL, NULL) == 0) {
        perror("command line encoding conversion failure");
        exit(1);
    }

    JLI_CmdToArgs(mbCmdline);
    JLI_MemFree(mbCmdline);

    margc = JLI_GetStdArgc();
    // add one more to mark the end
    margv = (char **)JLI_MemAlloc((margc + 1) * (sizeof(char *)));
    {
        int i = 0;
        StdArg *stdargs = JLI_GetStdArgs();
        for (i = 0 ; i < margc ; i++) {
            margv[i] = stdargs[i].arg;
#ifdef STATIC_BUILD
            check_relauncher_argument(margv[i]);
#endif
        }
        margv[i] = NULL;
    }
#else /* *NIXES */
    {
        // accommodate the NULL at the end
        JLI_List args = JLI_List_new(argc + 1);
        int i = 0;

        // Add first arg, which is the app name
        JLI_List_add(args, JLI_StringDup(argv[0]));
        // Append JDK_JAVA_OPTIONS
        if (JLI_AddArgsFromEnvVar(args, JDK_JAVA_OPTIONS)) {
            // JLI_SetTraceLauncher is not called yet
            // Show _JAVA_OPTIONS content along with JDK_JAVA_OPTIONS to aid diagnosis
            if (getenv(JLDEBUG_ENV_ENTRY)) {
                char *tmp = getenv("_JAVA_OPTIONS");
                if (NULL != tmp) {
                    JLI_ReportMessage(ARG_INFO_ENVVAR, "_JAVA_OPTIONS", tmp);
                }
            }
        }
        // Iterate the rest of command line
        for (i = 1; i < argc; i++) {
#ifdef STATIC_BUILD
            check_relauncher_argument(argv[i]);
#endif
            JLI_List argsInFile = JLI_PreprocessArg(argv[i], JNI_TRUE);
            if (NULL == argsInFile) {
                JLI_List_add(args, JLI_StringDup(argv[i]));
            } else {
                int cnt, idx;
                cnt = argsInFile->size;
                for (idx = 0; idx < cnt; idx++) {
                    JLI_List_add(args, argsInFile->elements[idx]);
                }
                // Shallow free, we reuse the string to avoid copy
                JLI_MemFree(argsInFile->elements);
                JLI_MemFree(argsInFile);
            }
        }
        margc = args->size;
        // add the NULL pointer at argv[argc]
        JLI_List_add(args, NULL);
        margv = args->elements;
    }
#endif /* WIN32 */
    return JLI_Launch(margc, margv,
                   jargc, jargs,
                   0, NULL,
                   VERSION_STRING,
                   DOT_VERSION,
                   progname,
                   launcher,
                   jargc > 0,
                   cpwildcard, javaw, 0);
}
