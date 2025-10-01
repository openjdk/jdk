/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _JAVA_H_
#define _JAVA_H_

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>

#include <jni.h>
#include <jvm.h>

/*
 * Get system specific defines.
 */
#include "emessages.h"
#include "java_md.h"
#include "jli_util.h"
#include "manifest_info.h"
#include "wildcard.h"
#include "splashscreen.h"

# define KB (1024UL)
# define MB (1024UL * KB)
# define GB (1024UL * MB)

/*
 * Older versions of java launcher used to support JRE version selection - specifically,
 * the java launcher in JDK 1.8 can be used to launch a java application using a different
 * java runtime (older, newer or same version JRE installed at a different location) than
 * the one the launcher belongs to.
 * That support was discontinued starting JDK 9. However, the JDK 8 launcher can still
 * be started with JRE version selection options to launch Java runtimes greater than JDK 8.
 * In such cases, the JDK 8 launcher when exec()ing the JDK N launcher, will set and propagate
 * the _JAVA_VERSION_SET environment variable. The value of this environment variable is the
 * Main-Class name from within the executable jar file (if any).
 * The java launcher in the current version of the JDK doesn't use this environment variable
 * in any way other than merely using it in debug logging.
 */
#define MAIN_CLASS_ENV_ENTRY "_JAVA_VERSION_SET"

#define SPLASH_FILE_ENV_ENTRY "_JAVA_SPLASH_FILE"
#define SPLASH_JAR_ENV_ENTRY "_JAVA_SPLASH_JAR"
#define JDK_JAVA_OPTIONS "JDK_JAVA_OPTIONS"

/*
 * Pointers to the needed JNI invocation API, initialized by LoadJavaVM.
 */
typedef jint (JNICALL *CreateJavaVM_t)(JavaVM **pvm, void **env, void *args);
typedef jint (JNICALL *GetDefaultJavaVMInitArgs_t)(void *args);
typedef jint (JNICALL *GetCreatedJavaVMs_t)(JavaVM **vmBuf, jsize bufLen, jsize *nVMs);

typedef struct {
    CreateJavaVM_t CreateJavaVM;
    GetDefaultJavaVMInitArgs_t GetDefaultJavaVMInitArgs;
    GetCreatedJavaVMs_t GetCreatedJavaVMs;
} InvocationFunctions;

JNIEXPORT int JNICALL
JLI_Launch(int argc, char ** argv,              /* main argc, argc */
        int jargc, const char** jargv,          /* java args */
        int appclassc, const char** appclassv,  /* app classpath */
        const char* fullversion,                /* full version defined */
        const char* dotversion,                 /* dot version defined */
        const char* pname,                      /* program name */
        const char* lname,                      /* launcher name */
        jboolean javaargs,                      /* JAVA_ARGS */
        jboolean cpwildcard,                    /* classpath wildcard */
        jboolean javaw,                         /* windows-only javaw */
        jint     ergo_class                     /* ergnomics policy */
);

/*
 * Prototypes for launcher functions in the system specific java_md.c.
 */

jboolean
LoadJavaVM(const char *jvmpath, InvocationFunctions *ifn);

jboolean
GetApplicationHome(char *buf, jint bufsize);

jboolean
GetApplicationHomeFromDll(char *buf, jint bufsize);

/*
 * Different platforms will implement this, here
 * pargc is a pointer to the original argc,
 * pargv is a pointer to the original argv,
 * jdkroot is an accessible path to the JDK installation root as determined by the call
 * so_jdkroot is the length of the buffer jdkroot
 * jvmpath is an accessible path to the jvm as determined by the call
 * so_jvmpath is the length of the buffer jvmpath
 */
void CreateExecutionEnvironment(int *argc, char ***argv,
                                char *jdkroot, jint so_jdkroot,
                                char *jvmpath, jint so_jvmpath,
                                char *jvmcfg, jint so_jvmcfg);

/* Reports an error message to stderr or a window as appropriate. */
JNIEXPORT void JNICALL
JLI_ReportErrorMessage(const char * message, ...);

/* Reports an error message only to stderr. */
JNIEXPORT void JNICALL
JLI_ReportMessage(const char * message, ...);

/* Reports a message only to stdout. */
void JLI_ShowMessage(const char * message, ...);

/*
 * Reports an exception which terminates the vm to stderr or a window
 * as appropriate.
 */
JNIEXPORT void JNICALL
JLI_ReportExceptionDescription(JNIEnv * env);

/*
 * Block current thread and continue execution in new thread.
 */
int CallJavaMainInNewThread(jlong stack_size, void* args);

/* sun.java.launcher.* platform properties. */
void SetJavaCommandLineProp(char* what, int argc, char** argv);

/*
 * Functions defined in java.c and used in java_md.c.
 */
jint ReadKnownVMs(const char *jvmcfg, jboolean speculative);
char *CheckJvmType(int *argc, char ***argv, jboolean speculative);
void AddOption(char *str, void *info);
jboolean IsWhiteSpaceOption(const char* name);
jlong CurrentTimeMicros();

// Utility function defined in args.c
int isTerminalOpt(char *arg);
jboolean IsJavaw();

int ContinueInNewThread(InvocationFunctions* ifn, jlong threadStackSize,
                   int argc, char** argv,
                   int mode, char *what, int ret);

int JVMInit(InvocationFunctions* ifn, jlong threadStackSize,
                   int argc, char** argv,
                   int mode, char *what, int ret);

/*
 * Initialize platform specific settings
 */
void InitLauncher(jboolean javaw);

/*
 * For MacOSX and Windows/Unix compatibility we require these
 * entry points, some of them may be stubbed out on Windows/Unixes.
 */
void     PostJVMInit(JNIEnv *env, jclass mainClass, JavaVM *vm);
void     ShowSplashScreen();
void     RegisterThread();
/*
 * this method performs additional platform specific processing and
 * should return JNI_TRUE to indicate the argument has been consumed,
 * otherwise returns JNI_FALSE to allow the calling logic to further
 * process the option.
 */
jboolean ProcessPlatformOption(const char *arg);

/*
 * This allows for finding classes from the VM's bootstrap class loader directly,
 * FindClass uses the application class loader internally, this will cause
 * unnecessary searching of the classpath for the required classes.
 *
 */
typedef jclass (JNICALL FindClassFromBootLoader_t(JNIEnv *env,
                                                  const char *name));
jclass FindBootStrapClass(JNIEnv *env, const char *classname);

jobjectArray CreateApplicationArgs(JNIEnv *env, char **strv, int argc);
jobjectArray NewPlatformStringArray(JNIEnv *env, char **strv, int strc);
jclass GetLauncherHelperClass(JNIEnv *env);

/*
 * Entry point.
 */
int JavaMain(void* args);

enum LaunchMode {               // cf. sun.launcher.LauncherHelper
    LM_UNKNOWN = 0,
    LM_CLASS,
    LM_JAR,
    LM_MODULE,
    LM_SOURCE
};

static const char *launchModeNames[]
    = { "Unknown", "Main class", "JAR file", "Module", "Source" };

typedef struct {
    int    argc;
    char **argv;
    int    mode;
    char  *what;
    InvocationFunctions ifn;
} JavaMainArgs;

#define NULL_CHECK_RETURN_VALUE(NCRV_check_pointer, NCRV_return_value) \
    do { \
        if ((NCRV_check_pointer) == NULL) { \
            JLI_ReportErrorMessage(JNI_ERROR); \
            return NCRV_return_value; \
        } \
    } while (JNI_FALSE)

#define NULL_CHECK0(NC0_check_pointer) \
    NULL_CHECK_RETURN_VALUE(NC0_check_pointer, 0)

#define NULL_CHECK(NC_check_pointer) \
    NULL_CHECK_RETURN_VALUE(NC_check_pointer, )

#define CHECK_EXCEPTION_RETURN_VALUE(CER_value) \
    do { \
        if ((*env)->ExceptionCheck(env)) { \
            return CER_value; \
        } \
    } while (JNI_FALSE)

#define CHECK_EXCEPTION_RETURN() \
    do { \
        if ((*env)->ExceptionCheck(env)) { \
            return; \
        } \
    } while (JNI_FALSE)

#endif /* _JAVA_H_ */
