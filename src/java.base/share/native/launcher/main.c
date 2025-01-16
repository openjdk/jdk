/*
 * Copyright (c) 1995, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "defines.h"
#include "jli_util.h"
#include "jni.h"

/*
 * Entry point.
 */
#ifdef JAVAW

char **__initenv;

int WINAPI
WinMain(HINSTANCE inst, HINSTANCE previnst, LPSTR cmdline, int cmdshow)
{
    const jboolean const_javaw = JNI_TRUE;

    __initenv = _environ;

#else /* JAVAW */
JNIEXPORT int
main(int argc, char **argv)
{
    const jboolean const_javaw = JNI_FALSE;
#endif /* JAVAW */

    int margc;
    char** margv;
    int jargc;
    const char** jargv = const_jargs;

    jargc = (sizeof(const_jargs) / sizeof(char *)) > 1
        ? sizeof(const_jargs) / sizeof(char *)
        : 0; // ignore the null terminator index

    JLI_InitArgProcessing(jargc > 0, const_disable_argfile);

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
                   jargc, jargv,
                   0, NULL,
                   VERSION_STRING,
                   DOT_VERSION,
                   (const_progname != NULL) ? const_progname : *margv,
                   (const_launcher != NULL) ? const_launcher : *margv,
                   jargc > 0,
                   const_cpwildcard, const_javaw, 0);
}
