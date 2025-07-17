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

#include "java.h"
#include "jvm_md.h"
#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <inttypes.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/types.h>
#include "manifest_info.h"


#define JVM_DLL "libjvm.so"
#define JAVA_DLL "libjava.so"
#ifdef AIX
#define LD_LIBRARY_PATH "LIBPATH"
#else
#define LD_LIBRARY_PATH "LD_LIBRARY_PATH"
#endif

/* help jettison the LD_LIBRARY_PATH settings in the future */
#ifndef SETENV_REQUIRED
#define SETENV_REQUIRED
#endif

/*
 * Following is the high level flow of the launcher
 * code residing in the common java.c and this
 * unix specific java_md file:
 *
 *  - JLI_Launch function, which is the entry point
 *    to the launcher, calls CreateExecutionEnvironment.
 *
 *  - CreateExecutionEnvironment does the following
 *    (not necessarily in this order):
 *      - determines the relevant JVM type that
 *        needs to be ultimately created
 *      - determines the path and asserts the presence
 *        of libjava and relevant libjvm library
 *      - removes any JVM selection options from the
 *        arguments that were passed to the launcher
 *
 *  - CreateExecutionEnvironment then determines (by calling
 *    RequiresSetenv function) if LD_LIBRARY_PATH environment
 *    variable needs to be set/updated.
 *      - If LD_LIBRARY_PATH needs to be set/updated,
 *        then CreateExecutionEnvironment exec()s
 *        the current process with the appropriate value
 *        for LD_LIBRARY_PATH.
 *      - Else if LD_LIBRARY_PATH need not be set or
 *        updated, then CreateExecutionEnvironment
 *        returns back.
 *
 *  - If CreateExecutionEnvironment exec()ed the process
 *    in the previous step, then the code control for the
 *    process will again start from the process' entry
 *    point and JLI_Launch is thus re-invoked and the
 *    same above sequence of code flow repeats again.
 *    During this "recursive" call into CreateExecutionEnvironment,
 *    the implementation of the check for LD_LIBRARY_PATH
 *    will realize that no further exec() is required and
 *    the control will return back from CreateExecutionEnvironment.
 *
 *  - The control returns back from CreateExecutionEnvironment
 *    to JLI_Launch.
 *
 *  - JLI_Launch then invokes LoadJavaVM which dlopen()s
 *    the JVM library and asserts the presence of
 *    JNI Invocation Functions "JNI_CreateJavaVM",
 *    "JNI_GetDefaultJavaVMInitArgs" and
 *    "JNI_GetCreatedJavaVMs" in that library. It then
 *    sets internal function pointers in the launcher to
 *    point to those functions.
 *
 *  - JLI_Launch then translates any -J options by
 *    invoking TranslateApplicationArgs.
 *
 *  - JLI_Launch then invokes ParseArguments to
 *    parse/process the launcher arguments.
 *
 *  - JLI_Launch then ultimately calls JVMInit.
 *
 *  - JVMInit invokes ShowSplashScreen which displays
 *    a splash screen for the application, if applicable.
 *
 *  - JVMInit then creates a new thread (T2), in the
 *    current process, and invokes JavaMain function
 *    in that new thread. The current thread (T1) then
 *    waits for the newly launched thread (T2) to complete.
 *
 *  - JavaMain function, in thread T2, before launching
 *    the application, invokes PostJVMInit.
 *
 *  - PostJVMInit is a no-op and returns back.
 *
 *  - Control then returns back from PostJVMInit into JavaMain,
 *    which then loads the application's main class and invokes
 *    the relevant main() Java method.
 *
 *  - JavaMain, in thread T2, then returns back an integer
 *    result and thread T2 execution ends here.
 *
 *  - The thread T1 in JVMInit, which is waiting on T2 to
 *    complete, receives the integer result and then propagates
 *    it as a return value all the way out of the
 *    JLI_Launch function.
 */

/* Store the name of the executable once computed */
static char *execname = NULL;

/*
 * execname accessor from other parts of platform dependent logic
 */
const char *
GetExecName() {
    return execname;
}

#ifdef SETENV_REQUIRED
static jboolean
JvmExists(const char *path) {
    char tmp[PATH_MAX + 1];
    struct stat statbuf;
    JLI_Snprintf(tmp, PATH_MAX, "%s/%s", path, JVM_DLL);
    if (stat(tmp, &statbuf) == 0) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}
/*
 * contains a lib/{server,client}/libjvm.so ?
 */
static jboolean
ContainsLibJVM(const char *env) {
    /* the usual suspects */
    char clientPattern[] = "lib/client";
    char serverPattern[] = "lib/server";
    char *envpath;
    char *path;
    char* save_ptr = NULL;
    jboolean clientPatternFound;
    jboolean serverPatternFound;

    /* fastest path */
    if (env == NULL) {
        return JNI_FALSE;
    }

    /* to optimize for time, test if any of our usual suspects are present. */
    clientPatternFound = JLI_StrStr(env, clientPattern) != NULL;
    serverPatternFound = JLI_StrStr(env, serverPattern) != NULL;
    if (clientPatternFound == JNI_FALSE && serverPatternFound == JNI_FALSE) {
        return JNI_FALSE;
    }

    /*
     * we have a suspicious path component, check if it contains a libjvm.so
     */
    envpath = JLI_StringDup(env);
    for (path = strtok_r(envpath, ":", &save_ptr); path != NULL; path = strtok_r(NULL, ":", &save_ptr)) {
        if (clientPatternFound && JLI_StrStr(path, clientPattern) != NULL) {
            if (JvmExists(path)) {
                JLI_MemFree(envpath);
                return JNI_TRUE;
            }
        }
        if (serverPatternFound && JLI_StrStr(path, serverPattern)  != NULL) {
            if (JvmExists(path)) {
                JLI_MemFree(envpath);
                return JNI_TRUE;
            }
        }
    }
    JLI_MemFree(envpath);
    return JNI_FALSE;
}

/*
 * Test whether the LD_LIBRARY_PATH environment variable needs to be set.
 */
static jboolean
RequiresSetenv(const char *jvmpath) {
    char jpath[PATH_MAX + 1];
    char *llp;
    char *p; /* a utility pointer */

#ifdef MUSL_LIBC
    /*
     * The musl library loader requires LD_LIBRARY_PATH to be set in order
     * to correctly resolve the dependency libjava.so has on libjvm.so.
     */
    return JNI_TRUE;
#endif

#ifdef AIX
    /* We always have to set the LIBPATH on AIX because ld doesn't support $ORIGIN. */
    return JNI_TRUE;
#endif

    llp = getenv("LD_LIBRARY_PATH");
    /* no environment variable is a good environment variable */
    if (llp == NULL) {
        return JNI_FALSE;
    }
#ifdef __linux
    /*
     * On linux, if a binary is running as sgid or suid, glibc sets
     * LD_LIBRARY_PATH to the empty string for security purposes. (In contrast,
     * on Solaris the LD_LIBRARY_PATH variable for a privileged binary does not
     * lose its settings; but the dynamic linker does apply more scrutiny to the
     * path.) The launcher uses the value of LD_LIBRARY_PATH to prevent an exec
     * loop, here and further downstream. Therefore, if we are running sgid or
     * suid, this function's setting of LD_LIBRARY_PATH will be ineffective and
     * we should case a return from the calling function.  Getting the right
     * libraries will be handled by the RPATH. In reality, this check is
     * redundant, as the previous check for a non-null LD_LIBRARY_PATH will
     * return back to the calling function forthwith, it is left here to safe
     * guard against any changes, in the glibc's existing security policy.
     */
    if ((getgid() != getegid()) || (getuid() != geteuid())) {
        return JNI_FALSE;
    }
#endif /* __linux */

    /*
     * Prevent recursions. Since LD_LIBRARY_PATH is the one which will be set by
     * previous versions of the JDK, thus it is the only path that matters here.
     * So we check to see if the desired JDK is set.
     */
    JLI_StrNCpy(jpath, jvmpath, PATH_MAX);
    p = JLI_StrRChr(jpath, '/');
    *p = '\0';
    if (llp != NULL && JLI_StrNCmp(llp, jpath, JLI_StrLen(jpath)) == 0) {
        return JNI_FALSE;
    }

    /* scrutinize all the paths further */
    if (llp != NULL &&  ContainsLibJVM(llp)) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}
#endif /* SETENV_REQUIRED */

void
CreateExecutionEnvironment(int *pargc, char ***pargv,
                           char jdkroot[], jint so_jdkroot,
                           char jvmpath[], jint so_jvmpath,
                           char jvmcfg[],  jint so_jvmcfg) {
    if (JLI_IsStaticallyLinked()) {
        // With static builds, all JDK and VM natives are statically linked
        // with the launcher executable. No need to manipulate LD_LIBRARY_PATH
        // by adding <jdk_path>/lib and etc. The 'jrepath', 'jvmpath' and
        // 'jvmcfg' are not used by the caller for static builds. Simply return.
        return;
    }

    char * jvmtype = NULL;
    char **argv = *pargv;

#ifdef SETENV_REQUIRED
    jboolean mustsetenv = JNI_FALSE;
    char *runpath = NULL; /* existing effective LD_LIBRARY_PATH setting */
    char* new_runpath = NULL; /* desired new LD_LIBRARY_PATH string */
    char* newpath = NULL; /* path on new LD_LIBRARY_PATH */
    char* lastslash = NULL;
    char** newenvp = NULL; /* current environment */
    size_t new_runpath_size;
#endif  /* SETENV_REQUIRED */

    /* Compute/set the name of the executable */
    SetExecname(*pargv);

    /* Check to see if the jvmpath exists */
    /* Find out where the JDK is that we will be using. */
    if (!GetJDKInstallRoot(jdkroot, so_jdkroot, JNI_FALSE)) {
        JLI_ReportErrorMessage(LAUNCHER_ERROR1);
        exit(2);
    }
    JLI_Snprintf(jvmcfg, so_jvmcfg, "%s%slib%sjvm.cfg",
            jdkroot, FILESEP, FILESEP);
    /* Find the specified JVM type */
    if (ReadKnownVMs(jvmcfg, JNI_FALSE) < 1) {
        JLI_ReportErrorMessage(CFG_ERROR7);
        exit(1);
    }

    jvmpath[0] = '\0';
    jvmtype = CheckJvmType(pargc, pargv, JNI_FALSE);
    if (JLI_StrCmp(jvmtype, "ERROR") == 0) {
        JLI_ReportErrorMessage(CFG_ERROR9);
        exit(4);
    }

    if (!GetJVMPath(jdkroot, jvmtype, jvmpath, so_jvmpath)) {
        JLI_ReportErrorMessage(CFG_ERROR8, jvmtype, jvmpath);
        exit(4);
    }

    /*
     * we seem to have everything we need, so without further ado
     * we return back, otherwise proceed to set the environment.
     */
#ifdef SETENV_REQUIRED
    mustsetenv = RequiresSetenv(jvmpath);
    JLI_TraceLauncher("mustsetenv: %s\n", mustsetenv ? "TRUE" : "FALSE");

    if (mustsetenv == JNI_FALSE) {
        return;
    }
#else
    return;
#endif /* SETENV_REQUIRED */

#ifdef SETENV_REQUIRED
    if (mustsetenv) {
        /*
         * We will set the LD_LIBRARY_PATH as follows:
         *
         *     o          $JVMPATH (directory portion only)
         *     o          $JDK/lib
         *
         * followed by the user's previous effective LD_LIBRARY_PATH, if
         * any.
         */

        runpath = getenv(LD_LIBRARY_PATH);

        /* runpath contains current effective LD_LIBRARY_PATH setting */
        { /* New scope to declare local variable */
            char *new_jvmpath = JLI_StringDup(jvmpath);
            new_runpath_size = ((runpath != NULL) ? JLI_StrLen(runpath) : 0) +
                    2 * JLI_StrLen(jdkroot) +
                    JLI_StrLen(new_jvmpath) + 52;
            new_runpath = JLI_MemAlloc(new_runpath_size);
            newpath = new_runpath + JLI_StrLen(LD_LIBRARY_PATH "=");


            /*
             * Create desired LD_LIBRARY_PATH value for target data model.
             */
            {
                /* remove the name of the .so from the JVM path */
                lastslash = JLI_StrRChr(new_jvmpath, '/');
                if (lastslash)
                    *lastslash = '\0';

                snprintf(new_runpath, new_runpath_size, LD_LIBRARY_PATH "="
                        "%s:"
                        "%s/lib",
                        new_jvmpath,
                        jdkroot
                        );

                JLI_MemFree(new_jvmpath);

                /*
                 * Check to make sure that the prefix of the current path is the
                 * desired environment variable setting, though the RequiresSetenv
                 * checks if the desired runpath exists, this logic does a more
                 * comprehensive check.
                 */
                if (runpath != NULL &&
                        JLI_StrNCmp(newpath, runpath, JLI_StrLen(newpath)) == 0 &&
                        (runpath[JLI_StrLen(newpath)] == 0 ||
                        runpath[JLI_StrLen(newpath)] == ':')) {
                    JLI_MemFree(new_runpath);
                    return;
                }
            }
        }

        /*
         * Place the desired environment setting onto the prefix of
         * LD_LIBRARY_PATH.  Note that this prevents any possible infinite
         * loop of execv() because we test for the prefix, above.
         */
        if (runpath != 0) {
            /* ensure storage for runpath + colon + NULL */
            if ((JLI_StrLen(runpath) + 1 + 1) > new_runpath_size) {
                JLI_ReportErrorMessageSys(LAUNCHER_ERROR3);
                exit(1);
            }
            JLI_StrCat(new_runpath, ":");
            JLI_StrCat(new_runpath, runpath);
        }

        if (putenv(new_runpath) != 0) {
            /* problem allocating memory; LD_LIBRARY_PATH not set properly */
            exit(1);
        }

        /*
         * Unix systems document that they look at LD_LIBRARY_PATH only
         * once at startup, so we have to re-exec the current executable
         * to get the changed environment variable to have an effect.
         */

        newenvp = environ;
    }
#endif /* SETENV_REQUIRED */
    {
        char *newexec = execname;
        JLI_TraceLauncher("TRACER_MARKER:About to EXEC\n");
        (void) fflush(stdout);
        (void) fflush(stderr);
#ifdef SETENV_REQUIRED
        if (mustsetenv) {
            execve(newexec, argv, newenvp);
        } else {
            execv(newexec, argv);
        }
#else /* !SETENV_REQUIRED */
        execv(newexec, argv);
#endif /* SETENV_REQUIRED */
        JLI_ReportErrorMessageSys(LAUNCHER_ERROR4, newexec);
    }
    exit(1);
}


static jboolean
GetJVMPath(const char *jdkroot, const char *jvmtype,
           char *jvmpath, jint jvmpathsize)
{
    struct stat s;

    if (JLI_StrChr(jvmtype, '/')) {
        JLI_Snprintf(jvmpath, jvmpathsize, "%s/" JVM_DLL, jvmtype);
    } else {
        JLI_Snprintf(jvmpath, jvmpathsize, "%s/lib/%s/" JVM_DLL, jdkroot, jvmtype);
    }

    JLI_TraceLauncher("Does `%s' exist ... ", jvmpath);

    if (stat(jvmpath, &s) == 0) {
        JLI_TraceLauncher("yes.\n");
        return JNI_TRUE;
    } else {
        JLI_TraceLauncher("no.\n");
        return JNI_FALSE;
    }
}

/*
 * Find path to the JDK installation root
 */
static jboolean
GetJDKInstallRoot(char *path, jint pathsize, jboolean speculative)
{
    char libjava[MAXPATHLEN];
    struct stat s;

    JLI_TraceLauncher("Attempt to get JDK installation root from launcher executable path\n");

    if (GetApplicationHome(path, pathsize)) {
        /* Is JDK co-located with the application? */
        JLI_Snprintf(libjava, sizeof(libjava), "%s/lib/" JAVA_DLL, path);
        if (access(libjava, F_OK) == 0) {
            JLI_TraceLauncher("JDK installation root path is %s\n", path);
            return JNI_TRUE;
        }
    }

    JLI_TraceLauncher("Attempt to get JDK installation root path from shared lib of the image\n");

    if (GetApplicationHomeFromDll(path, pathsize)) {
        JLI_Snprintf(libjava, sizeof(libjava), "%s/lib/" JAVA_DLL, path);
        if (stat(libjava, &s) == 0) {
            JLI_TraceLauncher("JDK installation root path is %s\n", path);
            return JNI_TRUE;
        }
    }

#if defined(AIX)
    /* at least on AIX try also the LD_LIBRARY_PATH / LIBPATH */
    if (GetApplicationHomeFromLibpath(path, pathsize)) {
        JLI_Snprintf(libjava, sizeof(libjava), "%s/lib/" JAVA_DLL, path);
        if (stat(libjava, &s) == 0) {
            JLI_TraceLauncher("JDK installation root path is %s\n", path);
            return JNI_TRUE;
        }
    }
#endif

    if (!speculative)
      JLI_ReportErrorMessage(LAUNCHER_ERROR2 JAVA_DLL);
    return JNI_FALSE;
}

jboolean
LoadJavaVM(const char *jvmpath, InvocationFunctions *ifn)
{
    void *libjvm;

    JLI_TraceLauncher("JVM path is %s\n", jvmpath);

    if (JLI_IsStaticallyLinked()) {
        libjvm = dlopen(NULL, RTLD_NOW + RTLD_GLOBAL);
    } else {
        libjvm = dlopen(jvmpath, RTLD_NOW + RTLD_GLOBAL);
        if (libjvm == NULL) {
            JLI_ReportErrorMessage(DLL_ERROR1, __LINE__);
            JLI_ReportErrorMessage(DLL_ERROR2, jvmpath, dlerror());
            return JNI_FALSE;
        }
    }

    ifn->CreateJavaVM = (CreateJavaVM_t)
        dlsym(libjvm, "JNI_CreateJavaVM");
    if (ifn->CreateJavaVM == NULL) {
        JLI_ReportErrorMessage(DLL_ERROR2, jvmpath, dlerror());
        return JNI_FALSE;
    }

    ifn->GetDefaultJavaVMInitArgs = (GetDefaultJavaVMInitArgs_t)
        dlsym(libjvm, "JNI_GetDefaultJavaVMInitArgs");
    if (ifn->GetDefaultJavaVMInitArgs == NULL) {
        JLI_ReportErrorMessage(DLL_ERROR2, jvmpath, dlerror());
        return JNI_FALSE;
    }

    ifn->GetCreatedJavaVMs = (GetCreatedJavaVMs_t)
        dlsym(libjvm, "JNI_GetCreatedJavaVMs");
    if (ifn->GetCreatedJavaVMs == NULL) {
        JLI_ReportErrorMessage(DLL_ERROR2, jvmpath, dlerror());
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/*
 * Compute the name of the executable
 *
 * In order to re-exec securely we need the absolute path of the
 * executable. On Solaris getexecname(3c) may not return an absolute
 * path so we use dladdr to get the filename of the executable and
 * then use realpath to derive an absolute path. From Solaris 9
 * onwards the filename returned in DL_info structure from dladdr is
 * an absolute pathname so technically realpath isn't required.
 * On Linux we read the executable name from /proc/self/exe.
 * As a fallback, and for platforms other than Solaris and Linux,
 * we use FindExecName to compute the executable name.
 */
const char*
SetExecname(char **argv)
{
    char* exec_path = NULL;
#if defined(__linux__)
    {
        const char* self = "/proc/self/exe";
        char buf[PATH_MAX+1];
        int len = readlink(self, buf, PATH_MAX);
        if (len >= 0) {
            buf[len] = '\0';            /* readlink(2) doesn't NUL terminate */
            exec_path = JLI_StringDup(buf);
        }
    }
#else /* !__linux__ */
    {
        /* Not implemented */
    }
#endif

    if (exec_path == NULL) {
        exec_path = FindExecName(argv[0]);
    }
    execname = exec_path;
    return exec_path;
}

/* --- Splash Screen shared library support --- */
static const char* SPLASHSCREEN_SO = JNI_LIB_NAME("splashscreen");
static void* hSplashLib = NULL;

void* SplashProcAddress(const char* name) {
    if (!hSplashLib) {
        int ret;
        char jdkRoot[MAXPATHLEN];
        char splashPath[MAXPATHLEN];

        if (JLI_IsStaticallyLinked()) {
            hSplashLib = dlopen(NULL, RTLD_LAZY);
        } else {
            if (!GetJDKInstallRoot(jdkRoot, sizeof(jdkRoot), JNI_FALSE)) {
                JLI_ReportErrorMessage(LAUNCHER_ERROR1);
                return NULL;
            }
            ret = JLI_Snprintf(splashPath, sizeof(splashPath), "%s/lib/%s",
                           jdkRoot, SPLASHSCREEN_SO);

            if (ret >= (int) sizeof(splashPath)) {
                JLI_ReportErrorMessage(LAUNCHER_ERROR3);
                return NULL;
            }
            if (ret < 0) {
                JLI_ReportErrorMessage(LAUNCHER_ERROR5);
                return NULL;
            }
            hSplashLib = dlopen(splashPath, RTLD_LAZY | RTLD_GLOBAL);
        }
        JLI_TraceLauncher("Info: loaded %s\n", splashPath);
    }
    if (hSplashLib) {
        void* sym = dlsym(hSplashLib, name);
        return sym;
    } else {
        return NULL;
    }
}

/*
 * Signature adapter for pthread_create() or thr_create().
 */
static void* ThreadJavaMain(void* args) {
    return (void*)(intptr_t)JavaMain(args);
}

static size_t adjustStackSize(size_t stack_size) {
    long page_size = sysconf(_SC_PAGESIZE);
    if (stack_size % page_size == 0) {
        return stack_size;
    } else {
        long pages = stack_size / page_size;
        // Ensure we don't go over limit
        if (stack_size <= SIZE_MAX - page_size) {
            pages++;
        }
        return page_size * pages;
    }
}

/*
 * Block current thread and continue execution in a new thread.
 */
int
CallJavaMainInNewThread(jlong stack_size, void* args) {
    int rslt;
    pthread_t tid;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    size_t adjusted_stack_size;

    if (stack_size > 0) {
        if (pthread_attr_setstacksize(&attr, stack_size) == EINVAL) {
            // System may require stack size to be multiple of page size
            // Retry with adjusted value
            adjusted_stack_size = adjustStackSize(stack_size);
            if (adjusted_stack_size != (size_t) stack_size) {
                pthread_attr_setstacksize(&attr, adjusted_stack_size);
            }
        }
    }
    pthread_attr_setguardsize(&attr, 0); // no pthread guard page on java threads

    if (pthread_create(&tid, &attr, ThreadJavaMain, args) == 0) {
        void* tmp;
        pthread_join(tid, &tmp);
        rslt = (int)(intptr_t)tmp;
    } else {
       /*
        * Continue execution in current thread if for some reason (e.g. out of
        * memory/LWP)  a new thread can't be created. This will likely fail
        * later in JavaMain as JNI_CreateJavaVM needs to create quite a
        * few new threads, anyway, just give it a try..
        */
        rslt = JavaMain(args);
    }

    pthread_attr_destroy(&attr);
    return rslt;
}

/* Coarse estimation of number of digits assuming the worst case is a 64-bit pid. */
#define MAX_PID_STR_SZ   20

int
JVMInit(InvocationFunctions* ifn, jlong threadStackSize,
        int argc, char **argv,
        int mode, char *what, int ret)
{
    ShowSplashScreen();
    return ContinueInNewThread(ifn, threadStackSize, argc, argv, mode, what, ret);
}

void
PostJVMInit(JNIEnv *env, jclass mainClass, JavaVM *vm)
{
    // stubbed out for windows and *nixes.
}

void
RegisterThread()
{
    // stubbed out for windows and *nixes.
}

/*
 * on unix, we return a false to indicate this option is not applicable
 */
jboolean
ProcessPlatformOption(const char *arg)
{
    return JNI_FALSE;
}
