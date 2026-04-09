/*
 * Copyright (c) 1995, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * Shared source for 'java' command line tool.
 *
 * If JAVA_ARGS is defined, then acts as a launcher for applications. For
 * instance, the JDK command line tools such as javac and javadoc (see
 * makefiles for more details) are built with this program.  Any arguments
 * prefixed with '-J' will be passed directly to the 'java' command.
 */

/*
 * One job of the launcher is to remove command line options which the
 * vm does not understand and will not process. These options include
 * options which select which style of vm is run (e.g. -client and
 * -server).
 * Additionally, for tools which invoke an underlying vm "-J-foo"
 * options are turned into "-foo" options to the vm.  This option
 * filtering is handled in a number of places in the launcher, some of
 * it in machine-dependent code.  In this file, the function
 * CheckJvmType removes vm style options and TranslateApplicationArgs
 * removes "-J" prefixes.
 */


#include <assert.h>

#include "java.h"
#include "jni.h"
#include "stdbool.h"

/*
 * A NOTE TO DEVELOPERS: For performance reasons it is important that
 * the program image remain relatively small until after
 * CreateExecutionEnvironment has finished its possibly recursive
 * processing. Watch everything, but resist all temptations to use Java
 * interfaces.
 */

#define USE_STDERR JNI_TRUE     /* we usually print to stderr */
#define USE_STDOUT JNI_FALSE

enum HelpKind {
    HELP_NONE,
    HELP_CONCISE,
    HELP_FULL,
    HELP_EXTRA
};

static jboolean printVersion = JNI_FALSE; /* print and exit */
static jboolean showVersion = JNI_FALSE;  /* print but continue */
static enum HelpKind printUsageKind = HELP_NONE; /* if not NONE, print specified usage and exit*/
static jboolean printTo = USE_STDERR;     /* where to print version/usage */
static jboolean dryRun = JNI_FALSE;       /* initialize VM and exit */
static char     *showSettings = NULL;     /* print but continue */
static jboolean showResolvedModules = JNI_FALSE;
static jboolean listModules = JNI_FALSE;
static char     *describeModule = NULL;
static jboolean validateModules = JNI_FALSE;

static const char *_program_name;
static const char *_launcher_name;
static jboolean _is_java_args = JNI_FALSE;
static jboolean _have_classpath = JNI_FALSE;
static const char *_fVersion;
static jboolean _wc_enabled = JNI_FALSE;
static jboolean dumpSharedSpaces = JNI_FALSE; /* -Xshare:dump */

/*
 * Values that will be stored into splash screen environment variables.
 * putenv is performed to set _JAVA_SPLASH_FILE and _JAVA_SPLASH_JAR
 * with these values. We need them in memory until UnsetEnv in
 * ShowSplashScreen, so they are made static global instead of auto local.
 */
static char* splash_file_entry = NULL;
static char* splash_jar_entry = NULL;

/*
 * List of VM options to be specified when the VM is created.
 */
static JavaVMOption *options;
static int numOptions, maxOptions;

/*
 * Prototypes for functions internal to launcher.
 */
static const char* GetFullVersion();
static jboolean IsJavaArgs();
static void SetJavaLauncherProp();
static void SetClassPath(const char *s);
static void SetMainModule(const char *s);
static jboolean ParseArguments(int *pargc, char ***pargv,
                               int *pmode, char **pwhat,
                               int *pret);
static jboolean InitializeJVM(JavaVM **pvm, JNIEnv **penv,
                              InvocationFunctions *ifn);
static jstring NewPlatformString(JNIEnv *env, char *s);
static jclass LoadMainClass(JNIEnv *env, int mode, char *name);
static void SetupSplashScreenEnvVars(const char *splash_file_path, char *jar_path);
static jclass GetApplicationClass(JNIEnv *env);

static void TranslateApplicationArgs(int jargc, const char **jargv, int *pargc, char ***pargv);
static jboolean AddApplicationOptions(int cpathc, const char **cpathv);

static void PrintJavaVersion(JNIEnv *env);
static void PrintUsage(JNIEnv* env, enum HelpKind printUsageKind);
static void ShowSettings(JNIEnv* env, char *optString);
static void ShowResolvedModules(JNIEnv* env);
static void ListModules(JNIEnv* env);
static void DescribeModule(JNIEnv* env, char* optString);

static void DumpState();

enum OptionKind {
    LAUNCHER_OPTION = 0,
    LAUNCHER_OPTION_WITH_ARGUMENT,
    LAUNCHER_MAIN_OPTION,
    VM_LONG_OPTION,
    VM_LONG_OPTION_WITH_ARGUMENT,
    VM_OPTION
};

static int GetOpt(int *pargc, char ***pargv, char **poption, char **pvalue);
static jboolean IsOptionWithArgument(int argc, char **argv);

/* Maximum supported entries from jvm.cfg. */
#define INIT_MAX_KNOWN_VMS      10

/* Values for vmdesc.flag */
enum vmdesc_flag {
    VM_UNKNOWN = -1,
    VM_KNOWN,
    VM_ALIASED_TO,
    VM_WARN,
    VM_ERROR,
    VM_IF_SERVER_CLASS,
    VM_IGNORE
};

struct vmdesc {
    char *name;
    int flag;
    char *alias;
    char *server_class;
};
static struct vmdesc *knownVMs = NULL;
static int knownVMsCount = 0;
static int knownVMsLimit = 0;

static void GrowKnownVMs(int minimum);
static int  KnownVMIndex(const char* name);
static void FreeKnownVMs();
static jboolean IsWildCardEnabled();


#define SOURCE_LAUNCHER_MAIN_ENTRY "jdk.compiler/com.sun.tools.javac.launcher.SourceLauncher"

/*
 * This reports error.  VM will not be created and no usage is printed.
 */
#define REPORT_ERROR(AC_ok, AC_failure_message, AC_questionable_arg) \
    do { \
        if (!AC_ok) { \
            JLI_ReportErrorMessage(AC_failure_message, AC_questionable_arg); \
            printUsageKind = HELP_NONE; \
            *pret = 1; \
            return JNI_FALSE; \
        } \
    } while (JNI_FALSE)

#define ARG_CHECK(AC_arg_count, AC_failure_message, AC_questionable_arg) \
    do { \
        if (AC_arg_count < 1) { \
            JLI_ReportErrorMessage(AC_failure_message, AC_questionable_arg); \
            printUsageKind = HELP_FULL; \
            *pret = 1; \
            return JNI_TRUE; \
        } \
    } while (JNI_FALSE)

/*
 * Running Java code in primordial thread caused many problems. We will
 * create a new thread to invoke JVM. See 6316197 for more information.
 */
static jlong threadStackSize    = 0;  /* stack size of the new thread */
static jlong maxHeapSize        = 0;  /* max heap size */
static jlong initialHeapSize    = 0;  /* initial heap size */

/*
 * A minimum initial-thread stack size suitable for most platforms.
 * This is the minimum amount of stack needed to load the JVM such
 * that it can reject a too small -Xss value. If this is too small
 * JVM initialization would cause a StackOverflowError.
  */
#ifndef STACK_SIZE_MINIMUM
#define STACK_SIZE_MINIMUM (64 * KB)
#endif

/*
 * Entry point.
 */
JNIEXPORT int JNICALL
JLI_Launch(int argc, char ** argv,              /* main argc, argv */
        int jargc, const char** jargv,          /* java args */
        int appclassc, const char** appclassv,  /* app classpath */
        const char* fullversion,                /* full version defined */
        const char* dotversion,                 /* UNUSED dot version defined */
        const char* pname,                      /* program name */
        const char* lname,                      /* launcher name */
        jboolean javaargs,                      /* JAVA_ARGS */
        jboolean cpwildcard,                    /* classpath wildcard*/
        jboolean javaw,                         /* windows-only javaw */
        jint ergo                               /* unused */
)
{
    int mode = LM_UNKNOWN;
    char *what = NULL;
    int ret;
    InvocationFunctions ifn;
    jlong start = 0, end = 0;
    char jvmpath[MAXPATHLEN];
    char jdkroot[MAXPATHLEN];
    char jvmcfg[MAXPATHLEN];

    _fVersion = fullversion;
    _launcher_name = lname;
    _program_name = pname;
    _is_java_args = javaargs;
    _wc_enabled = cpwildcard;

    InitLauncher(javaw);
    DumpState();
    if (JLI_IsTraceLauncher()) {
        char *env_in;
        if ((env_in = getenv(MAIN_CLASS_ENV_ENTRY)) != NULL) {
            printf("Launched through Multiple JRE (mJRE) support\n");
        }
        int i;
        printf("Java args:\n");
        for (i = 0; i < jargc ; i++) {
            printf("jargv[%d] = %s\n", i, jargv[i]);
        }
        printf("Command line args:\n");
        for (i = 0; i < argc ; i++) {
            printf("argv[%d] = %s\n", i, argv[i]);
        }
        AddOption("-Dsun.java.launcher.diag=true", NULL);
    }

    CreateExecutionEnvironment(&argc, &argv,
                               jdkroot, sizeof(jdkroot),
                               jvmpath, sizeof(jvmpath),
                               jvmcfg, sizeof(jvmcfg));

    ifn.CreateJavaVM = 0;
    ifn.GetDefaultJavaVMInitArgs = 0;

    if (JLI_IsTraceLauncher()) {
        start = CurrentTimeMicros();
    }

    if (!LoadJavaVM(jvmpath, &ifn)) {
        return(6);
    }

    if (JLI_IsTraceLauncher()) {
        end   = CurrentTimeMicros();
    }

    JLI_TraceLauncher("%ld micro seconds to LoadJavaVM\n", (long)(end-start));

    ++argv;
    --argc;

    if (IsJavaArgs()) {
        /* Preprocess wrapper arguments */
        TranslateApplicationArgs(jargc, jargv, &argc, &argv);
        if (!AddApplicationOptions(appclassc, appclassv)) {
            return(1);
        }
    } else {
        /* Set default CLASSPATH */
        char* cpath = getenv("CLASSPATH");
        if (cpath != NULL) {
            SetClassPath(cpath);
        }
    }

    /* Parse command line options; if the return value of
     * ParseArguments is false, the program should exit.
     */
    if (!ParseArguments(&argc, &argv, &mode, &what, &ret)) {
        return(ret);
    }

    /* Override class path if -jar flag was specified */
    if (mode == LM_JAR) {
        SetClassPath(what);     /* Override class path */
    }

    /* set the -Dsun.java.command pseudo property */
    SetJavaCommandLineProp(what, argc, argv);

    /* Set the -Dsun.java.launcher pseudo property */
    SetJavaLauncherProp();

    return JVMInit(&ifn, threadStackSize, argc, argv, mode, what, ret);
}
/*
 * Always detach the main thread so that it appears to have ended when
 * the application's main method exits.  This will invoke the
 * uncaught exception handler machinery if main threw an
 * exception.  An uncaught exception handler cannot change the
 * launcher's return code except by calling System.exit.
 *
 * Wait for all non-daemon threads to end, then destroy the VM.
 * This will actually create a trivial new Java waiter thread
 * named "DestroyJavaVM", but this will be seen as a different
 * thread from the one that executed main, even though they are
 * the same C thread.  This allows mainThread.join() and
 * mainThread.isAlive() to work as expected.
 */
#define LEAVE() \
    do { \
        if ((*vm)->DetachCurrentThread(vm) != JNI_OK) { \
            JLI_ReportErrorMessage(JVM_ERROR2); \
            ret = 1; \
        } \
        if (JNI_TRUE) { \
            (*vm)->DestroyJavaVM(vm); \
            return ret; \
        } \
    } while (JNI_FALSE)

#define CHECK_EXCEPTION_NULL_LEAVE(CENL_exception) \
    do { \
        if ((*env)->ExceptionCheck(env)) { \
            JLI_ReportExceptionDescription(env); \
            LEAVE(); \
        } \
        if ((CENL_exception) == NULL) { \
            JLI_ReportErrorMessage(JNI_ERROR); \
            LEAVE(); \
        } \
    } while (JNI_FALSE)

#define CHECK_EXCEPTION_LEAVE(CEL_return_value) \
    do { \
        if ((*env)->ExceptionCheck(env)) { \
            JLI_ReportExceptionDescription(env); \
            ret = (CEL_return_value); \
            LEAVE(); \
        } \
    } while (JNI_FALSE)

/*
 * Invokes static main(String[]) method if found.
 * Returns 0 with a pending exception if not found. Returns 1 if invoked, maybe
 * a pending exception if the method threw.
 */
int
invokeStaticMainWithArgs(JNIEnv *env, jclass mainClass, jobjectArray mainArgs) {
    jmethodID mainID = (*env)->GetStaticMethodID(env, mainClass, "main",
                                  "([Ljava/lang/String;)V");
    if (mainID == NULL) {
        // static main(String[]) not found
        return 0;
    }
    (*env)->CallStaticVoidMethod(env, mainClass, mainID, mainArgs);
    return 1; // method was invoked
}

/*
 * Invokes instance main(String[]) method if found.
 * Returns 0 with a pending exception if not found. Returns 1 if invoked, maybe
 * a pending exception if the method threw.
 */
int
invokeInstanceMainWithArgs(JNIEnv *env, jclass mainClass, jobjectArray mainArgs) {
    jmethodID constructor = (*env)->GetMethodID(env, mainClass, "<init>", "()V");
    if (constructor == NULL) {
        // main class' no-arg constructor not found
        return 0;
    }
    jobject mainObject = (*env)->NewObject(env, mainClass, constructor);
    if (mainObject == NULL) {
        // main class instance couldn't be constructed
        return 0;
    }
    jmethodID mainID =
        (*env)->GetMethodID(env, mainClass, "main", "([Ljava/lang/String;)V");
    if (mainID == NULL) {
        // instance method main(String[]) method not found
        return 0;
    }
    (*env)->CallVoidMethod(env, mainObject, mainID, mainArgs);
    return 1; // method was invoked
}

/*
 * Invokes no-arg static main() method if found.
 * Returns 0 with a pending exception if not found. Returns 1 if invoked, maybe
 * a pending exception if the method threw.
 */
int
invokeStaticMainWithoutArgs(JNIEnv *env, jclass mainClass) {
    jmethodID mainID = (*env)->GetStaticMethodID(env, mainClass, "main",
                                       "()V");
    if (mainID == NULL) {
        // static main() method couldn't be located
        return 0;
    }
    (*env)->CallStaticVoidMethod(env, mainClass, mainID);
    return 1; // method was invoked
}

/*
 * Invokes no-arg instance main() method if found.
 * Returns 0 with a pending exception if not found. Returns 1 if invoked, maybe
 * a pending exception if the method threw.
 */
int
invokeInstanceMainWithoutArgs(JNIEnv *env, jclass mainClass) {
    jmethodID constructor = (*env)->GetMethodID(env, mainClass, "<init>", "()V");
    if (constructor == NULL) {
        // main class' no-arg constructor not found
        return 0;
    }
    jobject mainObject = (*env)->NewObject(env, mainClass, constructor);
    if (mainObject == NULL) {
        // couldn't create instance of main class
        return 0;
    }
    jmethodID mainID = (*env)->GetMethodID(env, mainClass, "main",
                                 "()V");
    if (mainID == NULL) {
        // instance method main() not found
        return 0;
    }
    (*env)->CallVoidMethod(env, mainObject, mainID);
    return 1; // method was invoked
}

int
JavaMain(void* _args)
{
    JavaMainArgs *args = (JavaMainArgs *)_args;
    int argc = args->argc;
    char **argv = args->argv;
    int mode = args->mode;
    char *what = args->what;
    InvocationFunctions ifn = args->ifn;

    JavaVM *vm = 0;
    JNIEnv *env = 0;
    jclass mainClass = NULL;
    jclass appClass = NULL; // actual application class being launched
    jobjectArray mainArgs;
    int ret = 0;
    jlong start = 0, end = 0;
    jclass helperClass;
    jfieldID isStaticMainField;
    jboolean isStaticMain;
    jfieldID noArgMainField;
    jboolean noArgMain;

    RegisterThread();

    /* Initialize the virtual machine */
    start = CurrentTimeMicros();
    if (!InitializeJVM(&vm, &env, &ifn)) {
        JLI_ReportErrorMessage(JVM_ERROR1);
        exit(1);
    }

    if (showSettings != NULL) {
        ShowSettings(env, showSettings);
        CHECK_EXCEPTION_LEAVE(1);
    }

    // show resolved modules and continue
    if (showResolvedModules) {
        ShowResolvedModules(env);
        CHECK_EXCEPTION_LEAVE(1);
    }

    // list observable modules, then exit
    if (listModules) {
        ListModules(env);
        CHECK_EXCEPTION_LEAVE(1);
        LEAVE();
    }

    // describe a module, then exit
    if (describeModule != NULL) {
        DescribeModule(env, describeModule);
        CHECK_EXCEPTION_LEAVE(1);
        LEAVE();
    }

    if (printVersion || showVersion) {
        PrintJavaVersion(env);
        CHECK_EXCEPTION_LEAVE(0);
        if (printVersion) {
            LEAVE();
        }
    }

    // modules have been validated at startup so exit
    if (validateModules) {
        LEAVE();
    }

    /*
     * -Xshare:dump does not have a main class so the VM can safely exit now
     */
    if (dumpSharedSpaces) {
        CHECK_EXCEPTION_LEAVE(1);
        LEAVE();
    }

    /* If the user specified neither a class name nor a JAR file */
    if (printUsageKind != HELP_NONE) {
        PrintUsage(env, printUsageKind);
        CHECK_EXCEPTION_LEAVE(1);
        LEAVE();
    }

    FreeKnownVMs(); /* after last possible PrintUsage */

    if (JLI_IsTraceLauncher()) {
        end = CurrentTimeMicros();
        JLI_TraceLauncher("%ld micro seconds to InitializeJVM\n", (long)(end-start));
    }

    /* At this stage, argc/argv have the application's arguments */
    if (JLI_IsTraceLauncher()){
        int i;
        printf("%s is '%s'\n", launchModeNames[mode], what);
        printf("App's argc is %d\n", argc);
        for (i=0; i < argc; i++) {
            printf("    argv[%2d] = '%s'\n", i, argv[i]);
        }
    }

    ret = 1;

    /*
     * See bugid 5030265.  The Main-Class name has already been parsed
     * from the manifest, but not parsed properly for UTF-8 support.
     * Hence the code here ignores the value previously extracted and
     * uses the pre-existing code to reextract the value.  This is
     * possibly an end of release cycle expedient.  However, it has
     * also been discovered that passing some character sets through
     * the environment has "strange" behavior on some variants of
     * Windows.  Hence, maybe the manifest parsing code local to the
     * launcher should never be enhanced.
     *
     * Hence, future work should either:
     *     1)   Correct the local parsing code and verify that the
     *          Main-Class attribute gets properly passed through
     *          all environments,
     *     2)   Remove the vestages of maintaining main_class through
     *          the environment (and remove these comments).
     *
     * This method also correctly handles launching existing JavaFX
     * applications that may or may not have a Main-Class manifest entry.
     */
    mainClass = LoadMainClass(env, mode, what);
    CHECK_EXCEPTION_NULL_LEAVE(mainClass);
    /*
     * In some cases when launching an application that needs a helper, e.g., a
     * JavaFX application with no main method, the mainClass will not be the
     * applications own main class but rather a helper class. To keep things
     * consistent in the UI we need to track and report the application main class.
     */
    appClass = GetApplicationClass(env);
    CHECK_EXCEPTION_NULL_LEAVE(appClass);

    /* Build platform specific argument array */
    mainArgs = CreateApplicationArgs(env, argv, argc);
    CHECK_EXCEPTION_NULL_LEAVE(mainArgs);

    if (dryRun) {
        ret = 0;
        LEAVE();
    }

    /*
     * PostJVMInit uses the class name as the application name for GUI purposes,
     * for example, on OSX this sets the application name in the menu bar for
     * both SWT and JavaFX. So we'll pass the actual application class here
     * instead of mainClass as that may be a launcher or helper class instead
     * of the application class.
     */
    PostJVMInit(env, appClass, vm);
    CHECK_EXCEPTION_LEAVE(1);

    /*
     * The main method is invoked here so that extraneous java stacks are not in
     * the application stack trace.
     */

    helperClass = GetLauncherHelperClass(env);
    isStaticMainField = (*env)->GetStaticFieldID(env, helperClass, "isStaticMain", "Z");
    CHECK_EXCEPTION_NULL_LEAVE(isStaticMainField);
    isStaticMain = (*env)->GetStaticBooleanField(env, helperClass, isStaticMainField);

    noArgMainField = (*env)->GetStaticFieldID(env, helperClass, "noArgMain", "Z");
    CHECK_EXCEPTION_NULL_LEAVE(noArgMainField);
    noArgMain = (*env)->GetStaticBooleanField(env, helperClass, noArgMainField);

    if (isStaticMain) {
        if (noArgMain) {
            ret = invokeStaticMainWithoutArgs(env, mainClass);
        } else {
            ret = invokeStaticMainWithArgs(env, mainClass, mainArgs);
        }
    } else {
        if (noArgMain) {
            ret = invokeInstanceMainWithoutArgs(env, mainClass);
        } else {
            ret = invokeInstanceMainWithArgs(env, mainClass, mainArgs);
        }
    }
    if (!ret) {
        // An appropriate main method couldn't be located, check and report
        // any exception and LEAVE()
        CHECK_EXCEPTION_LEAVE(1);
    }

    /*
     * The launcher's exit code (in the absence of calls to
     * System.exit) will be non-zero if main threw an exception.
     */
    if (ret && !(*env)->ExceptionCheck(env)) {
        // main method was invoked and no exception was thrown from it,
        // return success.
        ret = 0;
    } else {
        // Either the main method couldn't be located or an exception occurred
        // in the invoked main method, return failure.
        ret = 1;
    }
    LEAVE();
#if defined(__GNUC__)
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wreturn-type"
#endif
}
#if defined(__GNUC__)
#pragma GCC diagnostic pop
#endif

/*
 * Test if the given name is one of the class path options.
 */
static jboolean
IsClassPathOption(const char* name) {
    return JLI_StrCmp(name, "-classpath") == 0 ||
           JLI_StrCmp(name, "-cp") == 0 ||
           JLI_StrCmp(name, "--class-path") == 0;
}

/*
 * Test if the given name is a launcher option taking the main entry point.
 */
static jboolean
IsLauncherMainOption(const char* name) {
    return JLI_StrCmp(name, "--module") == 0 ||
           JLI_StrCmp(name, "-m") == 0;
}

/*
 * Test if the given name is a white-space launcher option.
 */
static jboolean
IsLauncherOption(const char* name) {
    return IsClassPathOption(name) ||
           IsLauncherMainOption(name) ||
           JLI_StrCmp(name, "--describe-module") == 0 ||
           JLI_StrCmp(name, "-d") == 0 ||
           JLI_StrCmp(name, "--source") == 0;
}

/*
 * Test if the given name is a module-system white-space option that
 * will be passed to the VM with its corresponding long-form option
 * name and "=" delimiter.
 */
static jboolean
IsModuleOption(const char* name) {
    return JLI_StrCmp(name, "--module-path") == 0 ||
           JLI_StrCmp(name, "-p") == 0 ||
           JLI_StrCmp(name, "--upgrade-module-path") == 0 ||
           JLI_StrCmp(name, "--add-modules") == 0 ||
           JLI_StrCmp(name, "--enable-native-access") == 0 ||
           JLI_StrCmp(name, "--limit-modules") == 0 ||
           JLI_StrCmp(name, "--add-exports") == 0 ||
           JLI_StrCmp(name, "--add-opens") == 0 ||
           JLI_StrCmp(name, "--add-reads") == 0 ||
           JLI_StrCmp(name, "--patch-module") == 0;
}

static jboolean
IsLongFormModuleOption(const char* name) {
    return JLI_StrCCmp(name, "--module-path=") == 0 ||
           JLI_StrCCmp(name, "--upgrade-module-path=") == 0 ||
           JLI_StrCCmp(name, "--add-modules=") == 0 ||
           JLI_StrCCmp(name, "--enable-native-access=") == 0 ||
           JLI_StrCCmp(name, "--limit-modules=") == 0 ||
           JLI_StrCCmp(name, "--add-exports=") == 0 ||
           JLI_StrCCmp(name, "--add-reads=") == 0 ||
           JLI_StrCCmp(name, "--patch-module=") == 0;
}

/*
 * Test if the given name has a white space option.
 */
jboolean
IsWhiteSpaceOption(const char* name) {
    return IsModuleOption(name) ||
           IsLauncherOption(name);
}

/*
 * Check if it is OK to set the mode.
 * If the mode was previously set, and should not be changed,
 * a fatal error is reported.
 */
static int
checkMode(int mode, int newMode, const char *arg) {
    if (mode == LM_SOURCE) {
        JLI_ReportErrorMessage(ARG_ERROR14, arg);
        exit(1);
    }
    return newMode;
}

/*
 * Test if an arg identifies a source file.
 */
static jboolean IsSourceFile(const char *arg) {
    struct stat st;
    return (JLI_HasSuffix(arg, ".java") && stat(arg, &st) == 0);
}

/*
 * Checks the command line options to find which JVM type was
 * specified.  If no command line option was given for the JVM type,
 * the default type is used.  The environment variable
 * JDK_ALTERNATE_VM and the command line option -XXaltjvm= are also
 * checked as ways of specifying which JVM type to invoke.
 */
char *
CheckJvmType(int *pargc, char ***argv, jboolean speculative) {
    int i, argi;
    int argc;
    char **newArgv;
    int newArgvIdx = 0;
    int isVMType;
    int jvmidx = -1;
    char *jvmtype = getenv("JDK_ALTERNATE_VM");

    argc = *pargc;

    /* To make things simpler we always copy the argv array */
    newArgv = JLI_MemAlloc((argc + 1) * sizeof(char *));

    /* The program name is always present */
    newArgv[newArgvIdx++] = (*argv)[0];

    for (argi = 1; argi < argc; argi++) {
        char *arg = (*argv)[argi];
        isVMType = 0;

        if (IsJavaArgs()) {
            if (arg[0] != '-') {
                newArgv[newArgvIdx++] = arg;
                continue;
            }
        } else {
            if (IsWhiteSpaceOption(arg)) {
                newArgv[newArgvIdx++] = arg;
                argi++;
                if (argi < argc) {
                    newArgv[newArgvIdx++] = (*argv)[argi];
                }
                continue;
            }
            if (arg[0] != '-') break;
        }

        /* Did the user pass an explicit VM type? */
        i = KnownVMIndex(arg);
        if (i >= 0) {
            jvmtype = knownVMs[jvmidx = i].name + 1; /* skip the - */
            isVMType = 1;
            *pargc = *pargc - 1;
        }

        /* Did the user specify an "alternate" VM? */
        else if (JLI_StrCCmp(arg, "-XXaltjvm=") == 0 || JLI_StrCCmp(arg, "-J-XXaltjvm=") == 0) {
            isVMType = 1;
            jvmtype = arg+((arg[1]=='X')? 10 : 12);
            jvmidx = -1;
        }

        if (!isVMType) {
            newArgv[newArgvIdx++] = arg;
        }
    }

    /*
     * Finish copying the arguments if we aborted the above loop.
     * NOTE that if we aborted via "break" then we did NOT copy the
     * last argument above, and in addition argi will be less than
     * argc.
     */
    while (argi < argc) {
        newArgv[newArgvIdx++] = (*argv)[argi];
        argi++;
    }

    /* argv is null-terminated */
    newArgv[newArgvIdx] = 0;

    /* Copy back argv */
    *argv = newArgv;
    *pargc = newArgvIdx;

    /* use the default VM type if not specified (no alias processing) */
    if (jvmtype == NULL) {
      char* result = knownVMs[0].name+1;
      JLI_TraceLauncher("Default VM: %s\n", result);
      return result;
    }

    /* if using an alternate VM, no alias processing */
    if (jvmidx < 0)
      return jvmtype;

    /* Resolve aliases first */
    {
      int loopCount = 0;
      while (knownVMs[jvmidx].flag == VM_ALIASED_TO) {
        int nextIdx = KnownVMIndex(knownVMs[jvmidx].alias);

        if (loopCount > knownVMsCount) {
          if (!speculative) {
            JLI_ReportErrorMessage(CFG_ERROR1);
            exit(1);
          } else {
            return "ERROR";
            /* break; */
          }
        }

        if (nextIdx < 0) {
          if (!speculative) {
            JLI_ReportErrorMessage(CFG_ERROR2, knownVMs[jvmidx].alias);
            exit(1);
          } else {
            return "ERROR";
          }
        }
        jvmidx = nextIdx;
        jvmtype = knownVMs[jvmidx].name+1;
        loopCount++;
      }
    }

    switch (knownVMs[jvmidx].flag) {
    case VM_WARN:
        if (!speculative) {
            JLI_ReportErrorMessage(CFG_WARN1, jvmtype, knownVMs[0].name + 1);
        }
        /* fall through */
    case VM_IGNORE:
        jvmtype = knownVMs[jvmidx=0].name + 1;
        /* fall through */
    case VM_KNOWN:
        break;
    case VM_ERROR:
        if (!speculative) {
            JLI_ReportErrorMessage(CFG_ERROR3, jvmtype);
            exit(1);
        } else {
            return "ERROR";
        }
    }

    return jvmtype;
}

/* copied from HotSpot function "atomll()" */
static int
parse_size(const char *s, jlong *result) {
  jlong n = 0;
  int args_read = sscanf(s, JLONG_FORMAT_SPECIFIER, &n);
  if (args_read != 1) {
    return 0;
  }
  while (*s != '\0' && *s >= '0' && *s <= '9') {
    s++;
  }
  // 4705540: illegal if more characters are found after the first non-digit
  if (JLI_StrLen(s) > 1) {
    return 0;
  }
  switch (*s) {
    case 'T': case 't':
      *result = n * GB * KB;
      return 1;
    case 'G': case 'g':
      *result = n * GB;
      return 1;
    case 'M': case 'm':
      *result = n * MB;
      return 1;
    case 'K': case 'k':
      *result = n * KB;
      return 1;
    case '\0':
      *result = n;
      return 1;
    default:
      /* Create JVM with default stack and let VM handle malformed -Xss string*/
      return 0;
  }
}

/*
 * Adds a new VM option with the given name and value.
 */
void
AddOption(char *str, void *info)
{
    /*
     * Expand options array if needed to accommodate at least one more
     * VM option.
     */
    if (numOptions >= maxOptions) {
        if (options == 0) {
            maxOptions = 4;
            options = JLI_MemAlloc(maxOptions * sizeof(JavaVMOption));
        } else {
            JavaVMOption *tmp;
            maxOptions *= 2;
            tmp = JLI_MemAlloc(maxOptions * sizeof(JavaVMOption));
            memcpy(tmp, options, numOptions * sizeof(JavaVMOption));
            JLI_MemFree(options);
            options = tmp;
        }
    }
    options[numOptions].optionString = str;
    options[numOptions++].extraInfo = info;

    /*
     * -Xss is used both by the JVM and here to establish the stack size of the thread
     * created to launch the JVM. In the latter case we need to ensure we don't go
     * below the minimum stack size allowed. If -Xss is zero that tells the JVM to use
     * 'default' sizes (either from JVM or system configuration, e.g. 'ulimit -s' on linux),
     * and is not itself a small stack size that will be rejected. So we ignore -Xss0 here.
     */
    if (JLI_StrCCmp(str, "-Xss") == 0) {
        jlong tmp;
        if (parse_size(str + 4, &tmp)) {
            threadStackSize = tmp;
            if (threadStackSize > 0 && threadStackSize < (jlong)STACK_SIZE_MINIMUM) {
                threadStackSize = STACK_SIZE_MINIMUM;
            }
        }
    }

    if (JLI_StrCCmp(str, "-Xmx") == 0) {
        jlong tmp;
        if (parse_size(str + 4, &tmp)) {
            maxHeapSize = tmp;
        }
    }

    if (JLI_StrCCmp(str, "-Xms") == 0) {
        jlong tmp;
        if (parse_size(str + 4, &tmp)) {
           initialHeapSize = tmp;
        }
    }
}

static void
SetClassPath(const char *s)
{
    char *def;
    const char *orig = s;
    static const char format[] = "-Djava.class.path=%s";
    /*
     * usually we should not get a null pointer, but there are cases where
     * we might just get one, in which case we simply ignore it, and let the
     * caller deal with it
     */
    if (s == NULL)
        return;
    s = JLI_WildcardExpandClasspath(s);
    if (s == NULL)
        return;
    if (sizeof(format) - 2 + JLI_StrLen(s) < JLI_StrLen(s))
        // s is became corrupted after expanding wildcards
        return;
    size_t defSize = sizeof(format)
                       - 2 /* strlen("%s") */
                       + JLI_StrLen(s);
    def = JLI_MemAlloc(defSize);
    snprintf(def, defSize, format, s);
    AddOption(def, NULL);
    if (s != orig)
        JLI_MemFree((char *) s);
    _have_classpath = JNI_TRUE;
}

static void
AddLongFormOption(const char *option, const char *arg)
{
    static const char format[] = "%s=%s";
    char *def;
    size_t def_len;

    def_len = JLI_StrLen(option) + 1 + JLI_StrLen(arg) + 1;
    def = JLI_MemAlloc(def_len);
    JLI_Snprintf(def, def_len, format, option, arg);
    AddOption(def, NULL);
}

static void
SetMainModule(const char *s)
{
    static const char format[] = "-Djdk.module.main=%s";
    char* slash = JLI_StrChr(s, '/');
    size_t s_len, def_len;
    char *def;

    /* value may be <module> or <module>/<mainclass> */
    if (slash == NULL) {
        s_len = JLI_StrLen(s);
    } else {
        s_len = (size_t) (slash - s);
    }
    def_len = sizeof(format)
               - 2 /* strlen("%s") */
               + s_len;
    def = JLI_MemAlloc(def_len);
    JLI_Snprintf(def, def_len, format, s);
    AddOption(def, NULL);
}

/*
 * Test if the current argv is an option, i.e. with a leading `-`
 * and followed with an argument without a leading `-`.
 */
static jboolean
IsOptionWithArgument(int argc, char** argv) {
    char* option;
    char* arg;

    if (argc <= 1)
        return JNI_FALSE;

    option = *argv;
    arg = *(argv+1);
    return *option == '-' && *arg != '-';
}

/*
 * Gets the option, and its argument if the option has an argument.
 * It will update *pargc, **pargv to the next option.
 */
static int
GetOpt(int *pargc, char ***pargv, char **poption, char **pvalue) {
    int argc = *pargc;
    char** argv = *pargv;
    char* arg = *argv;

    char* option = arg;
    char* value = NULL;
    char* equals = NULL;
    int kind = LAUNCHER_OPTION;
    jboolean has_arg = JNI_FALSE;

    // check if this option may be a white-space option with an argument
    has_arg = IsOptionWithArgument(argc, argv);

    argv++; --argc;
    if (IsLauncherOption(arg)) {
        if (has_arg) {
            value = *argv;
            argv++; --argc;
        }
        kind = IsLauncherMainOption(arg) ? LAUNCHER_MAIN_OPTION
                                         : LAUNCHER_OPTION_WITH_ARGUMENT;
    } else if (IsModuleOption(arg)) {
        kind = VM_LONG_OPTION_WITH_ARGUMENT;
        if (has_arg) {
            value = *argv;
            argv++; --argc;
        }

        /*
         * Support short form alias
         */
        if (JLI_StrCmp(arg, "-p") == 0) {
            option = "--module-path";
        }

    } else if (JLI_StrCCmp(arg, "--") == 0 && (equals = JLI_StrChr(arg, '=')) != NULL) {
        value = equals+1;
        if (JLI_StrCCmp(arg, "--describe-module=") == 0 ||
            JLI_StrCCmp(arg, "--module=") == 0 ||
            JLI_StrCCmp(arg, "--class-path=") == 0||
            JLI_StrCCmp(arg, "--source=") == 0) {
            kind = LAUNCHER_OPTION_WITH_ARGUMENT;
        } else {
            kind = VM_LONG_OPTION;
        }
    }

    *pargc = argc;
    *pargv = argv;
    *poption = option;
    *pvalue = value;
    return kind;
}

/*
 * Parses command line arguments.  Returns JNI_FALSE if launcher
 * should exit without starting vm, returns JNI_TRUE if vm needs
 * to be started to process given options.  *pret (the launcher
 * process return value) is set to 0 for a normal exit.
 */
static jboolean
ParseArguments(int *pargc, char ***pargv,
               int *pmode, char **pwhat,
               int *pret)
{
    int argc = *pargc;
    char **argv = *pargv;
    int mode = LM_UNKNOWN;
    char *arg = NULL;
    bool headless = false;
    char *splash_file_path = NULL; // value of "-splash:" option

    *pret = 0;

    while (argc > 0 && *(arg = *argv) == '-') {
        char *option = NULL;
        char *value = NULL;
        int kind = GetOpt(&argc, &argv, &option, &value);
        jboolean has_arg = value != NULL && JLI_StrLen(value) > 0;
        jboolean has_arg_any_len = value != NULL;

/*
 * Option to set main entry point
 */
        if (JLI_StrCmp(arg, "-jar") == 0) {
            ARG_CHECK(argc, ARG_ERROR2, arg);
            mode = checkMode(mode, LM_JAR, arg);
        } else if (JLI_StrCmp(arg, "--module") == 0 ||
                   JLI_StrCCmp(arg, "--module=") == 0 ||
                   JLI_StrCmp(arg, "-m") == 0) {
            REPORT_ERROR (has_arg, ARG_ERROR5, arg);
            SetMainModule(value);
            mode = checkMode(mode, LM_MODULE, arg);
            if (has_arg) {
               *pwhat = value;
                break;
            }
        } else if (JLI_StrCmp(arg, "--source") == 0 ||
                   JLI_StrCCmp(arg, "--source=") == 0) {
            REPORT_ERROR (has_arg, ARG_ERROR13, arg);
            mode = LM_SOURCE;
            if (has_arg) {
                const char *prop = "-Djdk.internal.javac.source=";
                size_t size = JLI_StrLen(prop) + JLI_StrLen(value) + 1;
                char *propValue = (char *)JLI_MemAlloc(size);
                JLI_Snprintf(propValue, size, "%s%s", prop, value);
                AddOption(propValue, NULL);
            }
        } else if (JLI_StrCmp(arg, "--class-path") == 0 ||
                   JLI_StrCCmp(arg, "--class-path=") == 0 ||
                   JLI_StrCmp(arg, "-classpath") == 0 ||
                   JLI_StrCmp(arg, "-cp") == 0) {
            REPORT_ERROR (has_arg_any_len, ARG_ERROR1, arg);
            SetClassPath(value);
        } else if (JLI_StrCmp(arg, "--list-modules") == 0) {
            listModules = JNI_TRUE;
        } else if (JLI_StrCmp(arg, "--show-resolved-modules") == 0) {
            showResolvedModules = JNI_TRUE;
        } else if (JLI_StrCmp(arg, "--validate-modules") == 0) {
            AddOption("-Djdk.module.validation=true", NULL);
            validateModules = JNI_TRUE;
        } else if (JLI_StrCmp(arg, "--describe-module") == 0 ||
                   JLI_StrCCmp(arg, "--describe-module=") == 0 ||
                   JLI_StrCmp(arg, "-d") == 0) {
            REPORT_ERROR (has_arg_any_len, ARG_ERROR12, arg);
            describeModule = value;
/*
 * Parse white-space options
 */
        } else if (has_arg) {
            if (kind == VM_LONG_OPTION) {
                AddOption(option, NULL);
            } else if (kind == VM_LONG_OPTION_WITH_ARGUMENT) {
                AddLongFormOption(option, value);
            }
/*
 * Error missing argument
 */
        } else if (!has_arg && (JLI_StrCmp(arg, "--module-path") == 0 ||
                                JLI_StrCmp(arg, "-p") == 0 ||
                                JLI_StrCmp(arg, "--upgrade-module-path") == 0)) {
            REPORT_ERROR (has_arg, ARG_ERROR4, arg);

        } else if (!has_arg && (IsModuleOption(arg) || IsLongFormModuleOption(arg))) {
            REPORT_ERROR (has_arg, ARG_ERROR6, arg);
/*
 * The following cases will cause the argument parsing to stop
 */
        } else if (JLI_StrCmp(arg, "-help") == 0 ||
                   JLI_StrCmp(arg, "-h") == 0 ||
                   JLI_StrCmp(arg, "-?") == 0) {
            printUsageKind = HELP_FULL;
            return JNI_TRUE;
        } else if (JLI_StrCmp(arg, "--help") == 0) {
            printUsageKind = HELP_FULL;
            printTo = USE_STDOUT;
            return JNI_TRUE;
        } else if (JLI_StrCmp(arg, "-version") == 0) {
            printVersion = JNI_TRUE;
            return JNI_TRUE;
        } else if (JLI_StrCmp(arg, "--version") == 0) {
            printVersion = JNI_TRUE;
            printTo = USE_STDOUT;
            return JNI_TRUE;
        } else if (JLI_StrCmp(arg, "-showversion") == 0) {
            showVersion = JNI_TRUE;
        } else if (JLI_StrCmp(arg, "--show-version") == 0) {
            showVersion = JNI_TRUE;
            printTo = USE_STDOUT;
        } else if (JLI_StrCmp(arg, "--dry-run") == 0) {
            dryRun = JNI_TRUE;
        } else if (JLI_StrCmp(arg, "-X") == 0) {
            printUsageKind = HELP_EXTRA;
            return JNI_TRUE;
        } else if (JLI_StrCmp(arg, "--help-extra") == 0) {
            printUsageKind = HELP_EXTRA;
            printTo = USE_STDOUT;
            return JNI_TRUE;
/*
 * The following case checks for -XshowSettings OR -XshowSetting:SUBOPT.
 * In the latter case, any SUBOPT value not recognized will default to "all"
 */
        } else if (JLI_StrCmp(arg, "-XshowSettings") == 0 ||
                   JLI_StrCCmp(arg, "-XshowSettings:") == 0) {
            showSettings = arg;
        } else if (JLI_StrCmp(arg, "-Xdiag") == 0) {
            AddOption("-Dsun.java.launcher.diag=true", NULL);
        } else if (JLI_StrCmp(arg, "--show-module-resolution") == 0) {
            AddOption("-Djdk.module.showModuleResolution=true", NULL);
/*
 * The following case provide backward compatibility with old-style
 * command line options.
 */
        } else if (JLI_StrCmp(arg, "-fullversion") == 0) {
            JLI_ReportMessage("%s full version \"%s\"", _launcher_name, GetFullVersion());
            return JNI_FALSE;
        } else if (JLI_StrCmp(arg, "--full-version") == 0) {
            JLI_ShowMessage("%s %s", _launcher_name, GetFullVersion());
            return JNI_FALSE;
        } else if (JLI_StrCmp(arg, "-verbosegc") == 0) {
            JLI_ReportErrorMessage(ARG_DEPRECATED, "-verbosegc");
            AddOption("-verbose:gc", NULL);
        } else if (JLI_StrCmp(arg, "-debug") == 0) {
            JLI_ReportErrorMessage(ARG_DEPRECATED, "-debug");
        } else if (JLI_StrCmp(arg, "-noclassgc") == 0) {
            JLI_ReportErrorMessage(ARG_DEPRECATED, "-noclassgc");
            AddOption("-Xnoclassgc", NULL);
        } else if (JLI_StrCmp(arg, "-verify") == 0) {
            JLI_ReportErrorMessage(ARG_DEPRECATED, "-verify");
            AddOption("-Xverify:all", NULL);
        } else if (JLI_StrCmp(arg, "-verifyremote") == 0) {
            JLI_ReportErrorMessage(ARG_DEPRECATED, "-verifyremote");
            AddOption("-Xverify:remote", NULL);
        } else if (JLI_StrCmp(arg, "-noverify") == 0) {
            /*
             * Note that no 'deprecated' message is needed here because the VM
             * issues 'deprecated' messages for -noverify and -Xverify:none.
             */
            AddOption("-Xverify:none", NULL);
        } else if (JLI_StrCCmp(arg, "-ss") == 0 ||
                   JLI_StrCCmp(arg, "-ms") == 0 ||
                   JLI_StrCCmp(arg, "-mx") == 0) {
            JLI_ReportErrorMessage("Warning: %.3s option is deprecated"
                                   " and may be removed in a future release.", arg);
            size_t tmpSize = JLI_StrLen(arg) + 6;
            char *tmp = JLI_MemAlloc(tmpSize);
            snprintf(tmp, tmpSize, "-X%s", arg + 1); /* skip '-' */
            AddOption(tmp, NULL);
        } else if (JLI_StrCCmp(arg, "-splash:") == 0) {
            splash_file_path = arg + 8;
        } else if (JLI_StrCmp(arg, "--disable-@files") == 0) {
            ; /* Ignore --disable-@files option already handled */
        } else if (ProcessPlatformOption(arg)) {
            ; /* Processing of platform dependent options */
        } else {
            /* java.class.path set on the command line */
            if (JLI_StrCCmp(arg, "-Djava.class.path=") == 0) {
                _have_classpath = JNI_TRUE;
            } else if (JLI_StrCmp(arg, "-Djava.awt.headless=true") == 0) {
                /*
                 * Checking for headless toolkit option in the same way as AWT does:
                 * "true" means true and any other value means false
                 */
                headless = true;
            } else if (JLI_StrCCmp(arg, "-Djava.awt.headless=") == 0) {
                headless = false;
            }
            AddOption(arg, NULL);
        }

        /*
        * Check for CDS option
        */
        if (JLI_StrCmp(arg, "-Xshare:dump") == 0) {
            dumpSharedSpaces = JNI_TRUE;
        }
    }

    if (*pwhat == NULL && --argc >= 0) {
        *pwhat = *argv++;
    }

    if (*pwhat == NULL) {
        /* LM_UNKNOWN okay for options that exit */
        if (!listModules && !describeModule && !validateModules && !dumpSharedSpaces) {
            *pret = 1;
            printUsageKind = HELP_CONCISE;
        }
    } else if (mode == LM_UNKNOWN) {
        if (!_have_classpath) {
            SetClassPath(".");
        }
        /* If neither of -m, -jar, --source option is set, then the
         * launcher mode is LM_UNKNOWN. In such cases, we determine the
         * mode as LM_CLASS or LM_SOURCE per the input file. */
        mode = IsSourceFile(arg) ? LM_SOURCE : LM_CLASS;
    } else if (mode == LM_CLASS && IsSourceFile(arg)) {
        /* override LM_CLASS mode if given a source file */
        mode = LM_SOURCE;
    }

    if (mode == LM_SOURCE) {
        // communicate the launcher mode to runtime
        AddOption("-Dsun.java.launcher.mode=source", NULL);
        AddOption("--add-modules=ALL-DEFAULT", NULL);
        *pwhat = SOURCE_LAUNCHER_MAIN_ENTRY;
        // adjust (argc, argv) so that the name of the source file
        // is included in the args passed to the source launcher
        // main entry class
        *pargc = argc + 1;
        *pargv = argv - 1;
    } else {
        if (argc >= 0) {
            *pargc = argc;
            *pargv = argv;
        }
    }

    *pmode = mode;

    if (!headless) {
        char *jar_path = NULL;
        if (mode == LM_JAR) {
            jar_path = *pwhat;
        }
        // Not in headless mode. We now set a couple of env variables that
        // will be used later by ShowSplashScreen().
        SetupSplashScreenEnvVars(splash_file_path, jar_path);
    }

    return JNI_TRUE;
}

/*
 * Sets the relevant environment variables that are subsequently used by
 * the ShowSplashScreen() function. The splash_file_path and jar_path parameters
 * are used to determine which environment variables to set.
 * The splash_file_path is the value that was provided to the "-splash:" option
 * when launching java. It may be null, which implies the "-splash:" option wasn't used.
 * The jar_path is the value that was provided to the "-jar" option when launching java.
 * It too may be null, which implies the "-jar" option wasn't used.
 */
static void
SetupSplashScreenEnvVars(const char *splash_file_path, char *jar_path) {
    // Command line specified "-splash:" takes priority over manifest one.
    if (splash_file_path) {
        // We set up the splash file name as a env variable which then gets
        // used when showing the splash screen in ShowSplashScreen().

        // create the string of the form _JAVA_SPLASH_FILE=<val>
        splash_file_entry = JLI_MemAlloc(JLI_StrLen(SPLASH_FILE_ENV_ENTRY "=")
                                         + JLI_StrLen(splash_file_path) + 1);
        JLI_StrCpy(splash_file_entry, SPLASH_FILE_ENV_ENTRY "=");
        JLI_StrCat(splash_file_entry, splash_file_path);
        putenv(splash_file_entry);
        return;
    }
    if (!jar_path) {
        // no jar to look into for "SplashScreen-Image" manifest attribute
        return;
    }
    // parse the jar's manifest to find any "SplashScreen-Image"
    int res = 0;
    manifest_info  info;
    if ((res = JLI_ParseManifest(jar_path, &info)) != 0) {
        JLI_FreeManifest(); // cleanup any manifest structure
        if (res == -1) {
            JLI_ReportErrorMessage(JAR_ERROR2, jar_path);
        } else {
            JLI_ReportErrorMessage(JAR_ERROR3, jar_path);
        }
        exit(1);
    }
    if (!info.splashscreen_image_file_name) {
        JLI_FreeManifest(); // cleanup the manifest structure
        // no "SplashScreen-Image" in jar's manifest
        return;
    }
    // The jar's manifest had a "Splashscreen-Image" specified. We set up the jar entry name
    // and the jar file name as env variables which then get used when showing the splash screen
    // in ShowSplashScreen().

    // create the string of the form _JAVA_SPLASH_FILE=<val>
    splash_file_entry = JLI_MemAlloc(JLI_StrLen(SPLASH_FILE_ENV_ENTRY "=")
                                     + JLI_StrLen(info.splashscreen_image_file_name) + 1);
    JLI_StrCpy(splash_file_entry, SPLASH_FILE_ENV_ENTRY "=");
    JLI_StrCat(splash_file_entry, info.splashscreen_image_file_name);
    putenv(splash_file_entry);
    // create the string of the form _JAVA_SPLASH_JAR=<val>
    splash_jar_entry = JLI_MemAlloc(JLI_StrLen(SPLASH_JAR_ENV_ENTRY "=") + JLI_StrLen(jar_path) + 1);
    JLI_StrCpy(splash_jar_entry, SPLASH_JAR_ENV_ENTRY "=");
    JLI_StrCat(splash_jar_entry, jar_path);
    putenv(splash_jar_entry);
    JLI_FreeManifest(); // cleanup the manifest structure
}

/*
 * Initializes the Java Virtual Machine. Also frees options array when
 * finished.
 */
static jboolean
InitializeJVM(JavaVM **pvm, JNIEnv **penv, InvocationFunctions *ifn)
{
    JavaVMInitArgs args;
    jint r;

    memset(&args, 0, sizeof(args));
    args.version  = JNI_VERSION_1_2;
    args.nOptions = numOptions;
    args.options  = options;
    args.ignoreUnrecognized = JNI_FALSE;

    if (JLI_IsTraceLauncher()) {
        int i = 0;
        printf("JavaVM args:\n    ");
        printf("version 0x%08lx, ", (long)args.version);
        printf("ignoreUnrecognized is %s, ",
               args.ignoreUnrecognized ? "JNI_TRUE" : "JNI_FALSE");
        printf("nOptions is %ld\n", (long)args.nOptions);
        for (i = 0; i < numOptions; i++)
            printf("    option[%2d] = '%s'\n",
                   i, args.options[i].optionString);
    }

    r = ifn->CreateJavaVM(pvm, (void **)penv, &args);
    JLI_MemFree(options);
    options = NULL;
    return r == JNI_OK;
}

static jclass helperClass = NULL;

jclass
GetLauncherHelperClass(JNIEnv *env)
{
    if (helperClass == NULL) {
        NULL_CHECK0(helperClass = FindBootStrapClass(env,
                "sun/launcher/LauncherHelper"));
    }
    return helperClass;
}

static jmethodID makePlatformStringMID = NULL;
/*
 * Returns a new Java string object for the specified platform string.
 */
static jstring
NewPlatformString(JNIEnv *env, char *s)
{
    int len = (int)JLI_StrLen(s);
    jbyteArray ary;
    jclass cls = GetLauncherHelperClass(env);
    NULL_CHECK0(cls);
    if (s == NULL)
        return 0;

    ary = (*env)->NewByteArray(env, len);
    if (ary != 0) {
        jstring str = 0;
        (*env)->SetByteArrayRegion(env, ary, 0, len, (jbyte *)s);
        if (!(*env)->ExceptionCheck(env)) {
            if (makePlatformStringMID == NULL) {
                NULL_CHECK0(makePlatformStringMID = (*env)->GetStaticMethodID(env,
                        cls, "makePlatformString", "(Z[B)Ljava/lang/String;"));
            }
            str = (*env)->CallStaticObjectMethod(env, cls,
                    makePlatformStringMID, USE_STDERR, ary);
            CHECK_EXCEPTION_RETURN_VALUE(0);
            (*env)->DeleteLocalRef(env, ary);
            return str;
        }
    }
    return 0;
}

/*
 * Returns a new array of Java string objects for the specified
 * array of platform strings.
 */
jobjectArray
NewPlatformStringArray(JNIEnv *env, char **strv, int strc)
{
    jarray cls;
    jarray ary;
    int i;

    NULL_CHECK0(cls = FindBootStrapClass(env, "java/lang/String"));
    NULL_CHECK0(ary = (*env)->NewObjectArray(env, strc, cls, 0));
    CHECK_EXCEPTION_RETURN_VALUE(0);
    for (i = 0; i < strc; i++) {
        jstring str = NewPlatformString(env, *strv++);
        NULL_CHECK0(str);
        (*env)->SetObjectArrayElement(env, ary, i, str);
        (*env)->DeleteLocalRef(env, str);
    }
    return ary;
}

/*
 * Calls LauncherHelper::checkAndLoadMain to verify that the main class
 * is present, it is ok to load the main class and then load the main class.
 * For more details refer to the java implementation.
 */
static jclass
LoadMainClass(JNIEnv *env, int mode, char *name)
{
    jmethodID mid;
    jstring str;
    jobject result;
    jlong start = 0, end = 0;
    jclass cls = GetLauncherHelperClass(env);
    NULL_CHECK0(cls);
    if (JLI_IsTraceLauncher()) {
        start = CurrentTimeMicros();
    }
    NULL_CHECK0(mid = (*env)->GetStaticMethodID(env, cls,
                "checkAndLoadMain",
                "(ZILjava/lang/String;)Ljava/lang/Class;"));

    NULL_CHECK0(str = NewPlatformString(env, name));
    NULL_CHECK0(result = (*env)->CallStaticObjectMethod(env, cls, mid,
                                                        USE_STDERR, mode, str));

    if (JLI_IsTraceLauncher()) {
        end = CurrentTimeMicros();
        printf("%ld micro seconds to load main class\n", (long)(end-start));
        printf("----%s----\n", JLDEBUG_ENV_ENTRY);
    }

    return (jclass)result;
}

static jclass
GetApplicationClass(JNIEnv *env)
{
    jmethodID mid;
    jclass appClass;
    jclass cls = GetLauncherHelperClass(env);
    NULL_CHECK0(cls);
    NULL_CHECK0(mid = (*env)->GetStaticMethodID(env, cls,
                "getApplicationClass",
                "()Ljava/lang/Class;"));

    appClass = (*env)->CallStaticObjectMethod(env, cls, mid);
    CHECK_EXCEPTION_RETURN_VALUE(0);
    return appClass;
}

static char* expandWildcardOnLongOpt(char* arg) {
    char *p, *value;
    size_t optLen, valueLen;
    p = JLI_StrChr(arg, '=');

    if (p == NULL || p[1] == '\0') {
        JLI_ReportErrorMessage(ARG_ERROR1, arg);
        exit(1);
    }
    p++;
    value = (char *) JLI_WildcardExpandClasspath(p);
    if (p == value) {
        // no wildcard
        return arg;
    }

    optLen = p - arg;
    valueLen = JLI_StrLen(value);
    p = JLI_MemAlloc(optLen + valueLen + 1);
    memcpy(p, arg, optLen);
    memcpy(p + optLen, value, valueLen);
    p[optLen + valueLen] = '\0';
    return p;
}

/*
 * For tools, convert command line args thus:
 *   javac -cp foo:foo/"*" -J-ms32m ...
 *   java -ms32m -cp JLI_WildcardExpandClasspath(foo:foo/"*") ...
 *
 * Takes 4 parameters, and returns the populated arguments
 */
static void
TranslateApplicationArgs(int jargc, const char **jargv, int *pargc, char ***pargv)
{
    int argc = *pargc;
    char **argv = *pargv;
    int nargc = argc + jargc;
    char **nargv = JLI_MemAlloc((nargc + 1) * sizeof(char *));
    int i;

    *pargc = nargc;
    *pargv = nargv;

    /* Copy the VM arguments (i.e. prefixed with -J) */
    for (i = 0; i < jargc; i++) {
        const char *arg = jargv[i];
        if (arg[0] == '-' && arg[1] == 'J') {
            assert(arg[2] != '\0' && "Invalid JAVA_ARGS or EXTRA_JAVA_ARGS defined by build");
            *nargv++ = JLI_StringDup(arg + 2);
        }
    }

    for (i = 0; i < argc; i++) {
        char *arg = argv[i];
        if (arg[0] == '-' && arg[1] == 'J') {
            if (arg[2] == '\0') {
                JLI_ReportErrorMessage(ARG_ERROR3);
                exit(1);
            }
            *nargv++ = arg + 2;
        }
    }

    /* Copy the rest of the arguments */
    for (i = 0; i < jargc ; i++) {
        const char *arg = jargv[i];
        if (arg[0] != '-' || arg[1] != 'J') {
            *nargv++ = (arg == NULL) ? NULL : JLI_StringDup(arg);
        }
    }
    for (i = 0; i < argc; i++) {
        char *arg = argv[i];
        if (arg[0] == '-') {
            if (arg[1] == 'J')
                continue;
            if (IsWildCardEnabled()) {
                if (IsClassPathOption(arg) && i < argc - 1) {
                    *nargv++ = arg;
                    *nargv++ = (char *) JLI_WildcardExpandClasspath(argv[i+1]);
                    i++;
                    continue;
                }
                if (JLI_StrCCmp(arg, "--class-path=") == 0) {
                    *nargv++ = expandWildcardOnLongOpt(arg);
                    continue;
                }
            }
        }
        *nargv++ = arg;
    }
    *nargv = 0;
}

/*
 * For our tools, we try to add 3 VM options:
 *      -Denv.class.path=<envcp>
 *      -Dapplication.home=<apphome>
 *      -Djava.class.path=<appcp>
 * <envcp>   is the user's setting of CLASSPATH -- for instance the user
 *           tells javac where to find binary classes through this environment
 *           variable.  Notice that users will be able to compile against our
 *           tools classes (sun.tools.javac.Main) only if they explicitly add
 *           tools.jar to CLASSPATH.
 * <apphome> is the directory where the application is installed.
 * <appcp>   is the classpath to where our apps' classfiles are.
 */
static jboolean
AddApplicationOptions(int cpathc, const char **cpathv)
{
    char *envcp, *appcp, *apphome;
    char home[MAXPATHLEN]; /* application home */
    char separator[] = { PATH_SEPARATOR, '\0' };
    int size, i;

    {
        const char *s = getenv("CLASSPATH");
        if (s) {
            s = (char *) JLI_WildcardExpandClasspath(s);
            /* 40 for -Denv.class.path= */
            if (JLI_StrLen(s) + 40 > JLI_StrLen(s)) { // Safeguard from overflow
                size_t envcpSize = JLI_StrLen(s) + 40;
                envcp = (char *)JLI_MemAlloc(envcpSize);
                snprintf(envcp, envcpSize, "-Denv.class.path=%s", s);
                AddOption(envcp, NULL);
            }
        }
    }

    if (!GetApplicationHome(home, sizeof(home))) {
        JLI_ReportErrorMessage(CFG_ERROR5);
        return JNI_FALSE;
    }

    /* 40 for '-Dapplication.home=' */
    size_t apphomeSize = JLI_StrLen(home) + 40;
    apphome = (char *)JLI_MemAlloc(apphomeSize);
    snprintf(apphome, apphomeSize, "-Dapplication.home=%s", home);
    AddOption(apphome, NULL);

    /* How big is the application's classpath? */
    if (cpathc > 0) {
        size = 40;                                 /* 40: "-Djava.class.path=" */
        for (i = 0; i < cpathc; i++) {
            size += (int)JLI_StrLen(home) + (int)JLI_StrLen(cpathv[i]) + 1; /* 1: separator */
        }
        appcp = (char *)JLI_MemAlloc(size + 1);
        JLI_StrCpy(appcp, "-Djava.class.path=");
        for (i = 0; i < cpathc; i++) {
            JLI_StrCat(appcp, home);                        /* c:\program files\myapp */
            JLI_StrCat(appcp, cpathv[i]);           /* \lib\myapp.jar         */
            JLI_StrCat(appcp, separator);           /* ;                      */
        }
        appcp[JLI_StrLen(appcp)-1] = '\0';  /* remove trailing path separator */
        AddOption(appcp, NULL);
    }
    return JNI_TRUE;
}

/*
 * inject the -Dsun.java.command pseudo property into the args structure
 * this pseudo property is used in the HotSpot VM to expose the
 * Java class name and arguments to the main method to the VM. The
 * HotSpot VM uses this pseudo property to store the Java class name
 * (or jar file name) and the arguments to the class's main method
 * to the instrumentation memory region. The sun.java.command pseudo
 * property is not exported by HotSpot to the Java layer.
 */
void
SetJavaCommandLineProp(char *what, int argc, char **argv)
{

    int i = 0;
    size_t len = 0;
    char* javaCommand = NULL;
    char* dashDstr = "-Dsun.java.command=";

    if (what == NULL) {
        /* unexpected, one of these should be set. just return without
         * setting the property
         */
        return;
    }

    /* determine the amount of memory to allocate assuming
     * the individual components will be space separated
     */
    len = JLI_StrLen(what);
    for (i = 0; i < argc; i++) {
        len += JLI_StrLen(argv[i]) + 1;
    }

    /* allocate the memory */
    javaCommand = (char*) JLI_MemAlloc(len + JLI_StrLen(dashDstr) + 1);

    /* build the -D string */
    *javaCommand = '\0';
    JLI_StrCat(javaCommand, dashDstr);
    JLI_StrCat(javaCommand, what);

    for (i = 0; i < argc; i++) {
        /* the components of the string are space separated. In
         * the case of embedded white space, the relationship of
         * the white space separated components to their true
         * positional arguments will be ambiguous. This issue may
         * be addressed in a future release.
         */
        JLI_StrCat(javaCommand, " ");
        JLI_StrCat(javaCommand, argv[i]);
    }

    AddOption(javaCommand, NULL);
}

/*
 * JVM would like to know if it's created by a standard Sun launcher, or by
 * user native application, the following property indicates the former.
 */
static void SetJavaLauncherProp() {
  AddOption("-Dsun.java.launcher=SUN_STANDARD", NULL);
}

/*
 * Prints the version information from the java.version and other properties.
 */
static void
PrintJavaVersion(JNIEnv *env)
{
    jclass ver;
    jmethodID print;

    NULL_CHECK(ver = FindBootStrapClass(env, "java/lang/VersionProps"));
    NULL_CHECK(print = (*env)->GetStaticMethodID(env,
                                                 ver,
                                                 "print",
                                                 "(Z)V"
                                                 )
              );

    (*env)->CallStaticVoidMethod(env, ver, print, printTo);
}

/*
 * Prints all the Java settings, see the java implementation for more details.
 */
static void
ShowSettings(JNIEnv *env, char *optString)
{
    jmethodID showSettingsID;
    jstring joptString;
    jclass cls = GetLauncherHelperClass(env);
    NULL_CHECK(cls);
    NULL_CHECK(showSettingsID = (*env)->GetStaticMethodID(env, cls,
            "showSettings", "(ZLjava/lang/String;JJJ)V"));
    NULL_CHECK(joptString = (*env)->NewStringUTF(env, optString));
    (*env)->CallStaticVoidMethod(env, cls, showSettingsID,
                                 USE_STDERR,
                                 joptString,
                                 (jlong)initialHeapSize,
                                 (jlong)maxHeapSize,
                                 (jlong)threadStackSize);
}

/**
 * Show resolved modules
 */
static void
ShowResolvedModules(JNIEnv *env)
{
    jmethodID showResolvedModulesID;
    jclass cls = GetLauncherHelperClass(env);
    NULL_CHECK(cls);
    NULL_CHECK(showResolvedModulesID = (*env)->GetStaticMethodID(env, cls,
            "showResolvedModules", "()V"));
    (*env)->CallStaticVoidMethod(env, cls, showResolvedModulesID);
}

/**
 * List observable modules
 */
static void
ListModules(JNIEnv *env)
{
    jmethodID listModulesID;
    jclass cls = GetLauncherHelperClass(env);
    NULL_CHECK(cls);
    NULL_CHECK(listModulesID = (*env)->GetStaticMethodID(env, cls,
            "listModules", "()V"));
    (*env)->CallStaticVoidMethod(env, cls, listModulesID);
}

/**
 * Describe a module
 */
static void
DescribeModule(JNIEnv *env, char *optString)
{
    jmethodID describeModuleID;
    jstring joptString = NULL;
    jclass cls = GetLauncherHelperClass(env);
    NULL_CHECK(cls);
    NULL_CHECK(describeModuleID = (*env)->GetStaticMethodID(env, cls,
            "describeModule", "(Ljava/lang/String;)V"));
    NULL_CHECK(joptString = NewPlatformString(env, optString));
    (*env)->CallStaticVoidMethod(env, cls, describeModuleID, joptString);
}

/*
 * Prints default usage or the Xusage message, see sun.launcher.LauncherHelper.java
 */
static void
PrintUsage(JNIEnv* env, enum HelpKind printUsageKind)
{
  jmethodID initHelp, vmSelect, vmSynonym;
  jmethodID printHelp, printConciseUsageMessage, printXUsageMessage;
  jstring jprogname, vm1, vm2;
  int i;
  jclass cls = GetLauncherHelperClass(env);
  NULL_CHECK(cls);
  switch (printUsageKind) {
    case HELP_NONE: break;
    case HELP_CONCISE:
      NULL_CHECK(printConciseUsageMessage = (*env)->GetStaticMethodID(env, cls,
                                          "printConciseUsageMessage", "(Z)V"));
      (*env)->CallStaticVoidMethod(env, cls, printConciseUsageMessage, printTo);
      break;
    case HELP_EXTRA:
      NULL_CHECK(printXUsageMessage = (*env)->GetStaticMethodID(env, cls,
                                          "printXUsageMessage", "(Z)V"));
      (*env)->CallStaticVoidMethod(env, cls, printXUsageMessage, printTo);
      break;
    case HELP_FULL:
      NULL_CHECK(initHelp = (*env)->GetStaticMethodID(env, cls,
                                          "initHelpMessage", "(Ljava/lang/String;)V"));

      NULL_CHECK(vmSelect = (*env)->GetStaticMethodID(env, cls, "appendVmSelectMessage",
                                          "(Ljava/lang/String;Ljava/lang/String;)V"));

      NULL_CHECK(vmSynonym = (*env)->GetStaticMethodID(env, cls,
                                          "appendVmSynonymMessage",
                                          "(Ljava/lang/String;Ljava/lang/String;)V"));

      NULL_CHECK(printHelp = (*env)->GetStaticMethodID(env, cls,
                                          "printHelpMessage", "(Z)V"));

      NULL_CHECK(jprogname = (*env)->NewStringUTF(env, _program_name));

      /* Initialize the usage message with the usual preamble */
      (*env)->CallStaticVoidMethod(env, cls, initHelp, jprogname);
      CHECK_EXCEPTION_RETURN();


      /* Assemble the other variant part of the usage */
      for (i=1; i<knownVMsCount; i++) {
        if (knownVMs[i].flag == VM_KNOWN) {
          NULL_CHECK(vm1 =  (*env)->NewStringUTF(env, knownVMs[i].name));
          NULL_CHECK(vm2 =  (*env)->NewStringUTF(env, knownVMs[i].name+1));
          (*env)->CallStaticVoidMethod(env, cls, vmSelect, vm1, vm2);
          CHECK_EXCEPTION_RETURN();
        }
      }
      for (i=1; i<knownVMsCount; i++) {
        if (knownVMs[i].flag == VM_ALIASED_TO) {
          NULL_CHECK(vm1 =  (*env)->NewStringUTF(env, knownVMs[i].name));
          NULL_CHECK(vm2 =  (*env)->NewStringUTF(env, knownVMs[i].alias+1));
          (*env)->CallStaticVoidMethod(env, cls, vmSynonym, vm1, vm2);
          CHECK_EXCEPTION_RETURN();
        }
      }

      /* Complete the usage message and print to stderr*/
      (*env)->CallStaticVoidMethod(env, cls, printHelp, printTo);
      break;
  }
  return;
}

/*
 * Read the jvm.cfg file and fill the knownJVMs[] array.
 *
 * The functionality of the jvm.cfg file is subject to change without
 * notice and the mechanism will be removed in the future.
 *
 * The lexical structure of the jvm.cfg file is as follows:
 *
 *     jvmcfg         :=  { vmLine }
 *     vmLine         :=  knownLine
 *                    |   aliasLine
 *                    |   warnLine
 *                    |   ignoreLine
 *                    |   errorLine
 *                    |   predicateLine
 *                    |   commentLine
 *     knownLine      :=  flag  "KNOWN"                  EOL
 *     warnLine       :=  flag  "WARN"                   EOL
 *     ignoreLine     :=  flag  "IGNORE"                 EOL
 *     errorLine      :=  flag  "ERROR"                  EOL
 *     aliasLine      :=  flag  "ALIASED_TO"       flag  EOL
 *     predicateLine  :=  flag  "IF_SERVER_CLASS"  flag  EOL
 *     commentLine    :=  "#" text                       EOL
 *     flag           :=  "-" identifier
 *
 * The semantics are that when someone specifies a flag on the command line:
 * - if the flag appears on a knownLine, then the identifier is used as
 *   the name of the directory holding the JVM library (the name of the JVM).
 * - if the flag appears as the first flag on an aliasLine, the identifier
 *   of the second flag is used as the name of the JVM.
 * - if the flag appears on a warnLine, the identifier is used as the
 *   name of the JVM, but a warning is generated.
 * - if the flag appears on an ignoreLine, the identifier is recognized as the
 *   name of a JVM, but the identifier is ignored and the default vm used
 * - if the flag appears on an errorLine, an error is generated.
 * - if the flag appears as the first flag on a predicateLine, and
 *   the machine on which you are running passes the predicate indicated,
 *   then the identifier of the second flag is used as the name of the JVM,
 *   otherwise the identifier of the first flag is used as the name of the JVM.
 * If no flag is given on the command line, the first vmLine of the jvm.cfg
 * file determines the name of the JVM.
 * PredicateLines are only interpreted on first vmLine of a jvm.cfg file,
 * since they only make sense if someone hasn't specified the name of the
 * JVM on the command line.
 *
 * The intent of the jvm.cfg file is to allow several JVM libraries to
 * be installed in different subdirectories of a single JDK installation,
 * for space-savings and convenience in testing.
 * The intent is explicitly not to provide a full aliasing or predicate
 * mechanism.
 */
jint
ReadKnownVMs(const char *jvmCfgName, jboolean speculative)
{
    FILE *jvmCfg;
    char line[MAXPATHLEN+20];
    int cnt = 0;
    int lineno = 0;
    jlong start = 0, end = 0;
    int vmType;
    char *tmpPtr;
    char *altVMName = NULL;
    static char *whiteSpace = " \t";
    if (JLI_IsTraceLauncher()) {
        start = CurrentTimeMicros();
    }

    jvmCfg = fopen(jvmCfgName, "r");
    if (jvmCfg == NULL) {
      if (!speculative) {
        JLI_ReportErrorMessage(CFG_ERROR6, jvmCfgName);
        exit(1);
      } else {
        return -1;
      }
    }
    while (fgets(line, sizeof(line), jvmCfg) != NULL) {
        vmType = VM_UNKNOWN;
        lineno++;
        if (line[0] == '#')
            continue;
        if (line[0] != '-') {
            JLI_ReportErrorMessage(CFG_WARN2, lineno, jvmCfgName);
        }
        if (cnt >= knownVMsLimit) {
            GrowKnownVMs(cnt);
        }
        line[JLI_StrLen(line)-1] = '\0'; /* remove trailing newline */
        tmpPtr = line + JLI_StrCSpn(line, whiteSpace);
        if (*tmpPtr == 0) {
            JLI_ReportErrorMessage(CFG_WARN3, lineno, jvmCfgName);
        } else {
            /* Null-terminate this string for JLI_StringDup below */
            *tmpPtr++ = 0;
            tmpPtr += JLI_StrSpn(tmpPtr, whiteSpace);
            if (*tmpPtr == 0) {
                JLI_ReportErrorMessage(CFG_WARN3, lineno, jvmCfgName);
            } else {
                if (!JLI_StrCCmp(tmpPtr, "KNOWN")) {
                    vmType = VM_KNOWN;
                } else if (!JLI_StrCCmp(tmpPtr, "ALIASED_TO")) {
                    tmpPtr += JLI_StrCSpn(tmpPtr, whiteSpace);
                    if (*tmpPtr != 0) {
                        tmpPtr += JLI_StrSpn(tmpPtr, whiteSpace);
                    }
                    if (*tmpPtr == 0) {
                        JLI_ReportErrorMessage(CFG_WARN3, lineno, jvmCfgName);
                    } else {
                        /* Null terminate altVMName */
                        altVMName = tmpPtr;
                        tmpPtr += JLI_StrCSpn(tmpPtr, whiteSpace);
                        *tmpPtr = 0;
                        vmType = VM_ALIASED_TO;
                    }
                } else if (!JLI_StrCCmp(tmpPtr, "WARN")) {
                    vmType = VM_WARN;
                } else if (!JLI_StrCCmp(tmpPtr, "IGNORE")) {
                    vmType = VM_IGNORE;
                } else if (!JLI_StrCCmp(tmpPtr, "ERROR")) {
                    vmType = VM_ERROR;
                } else if (!JLI_StrCCmp(tmpPtr, "IF_SERVER_CLASS")) {
                    /* ignored */
                } else {
                    JLI_ReportErrorMessage(CFG_WARN5, lineno, &jvmCfgName[0]);
                    vmType = VM_KNOWN;
                }
            }
        }

        JLI_TraceLauncher("jvm.cfg[%d] = ->%s<-\n", cnt, line);
        if (vmType != VM_UNKNOWN) {
            knownVMs[cnt].name = JLI_StringDup(line);
            knownVMs[cnt].flag = vmType;
            switch (vmType) {
            default:
                break;
            case VM_ALIASED_TO:
                knownVMs[cnt].alias = JLI_StringDup(altVMName);
                JLI_TraceLauncher("    name: %s  vmType: %s  alias: %s\n",
                   knownVMs[cnt].name, "VM_ALIASED_TO", knownVMs[cnt].alias);
                break;
            }
            cnt++;
        }
    }
    fclose(jvmCfg);
    knownVMsCount = cnt;

    if (JLI_IsTraceLauncher()) {
        end = CurrentTimeMicros();
        printf("%ld micro seconds to parse jvm.cfg\n", (long)(end-start));
    }

    return cnt;
}


static void
GrowKnownVMs(int minimum)
{
    struct vmdesc* newKnownVMs;
    int newMax;

    newMax = (knownVMsLimit == 0 ? INIT_MAX_KNOWN_VMS : (2 * knownVMsLimit));
    if (newMax <= minimum) {
        newMax = minimum;
    }
    newKnownVMs = (struct vmdesc*) JLI_MemAlloc(newMax * sizeof(struct vmdesc));
    if (knownVMs != NULL) {
        memcpy(newKnownVMs, knownVMs, knownVMsLimit * sizeof(struct vmdesc));
    }
    JLI_MemFree(knownVMs);
    knownVMs = newKnownVMs;
    knownVMsLimit = newMax;
}


/* Returns index of VM or -1 if not found */
static int
KnownVMIndex(const char* name)
{
    int i;
    if (JLI_StrCCmp(name, "-J") == 0) name += 2;
    for (i = 0; i < knownVMsCount; i++) {
        if (!JLI_StrCmp(name, knownVMs[i].name)) {
            return i;
        }
    }
    return -1;
}

static void
FreeKnownVMs()
{
    int i;
    for (i = 0; i < knownVMsCount; i++) {
        JLI_MemFree(knownVMs[i].name);
        knownVMs[i].name = NULL;
    }
    JLI_MemFree(knownVMs);
    knownVMs = NULL;
}

/*
 * Displays the splash screen according to the jar file name
 * and image file names stored in environment variables
 */
void
ShowSplashScreen()
{
    const char *jar_name = getenv(SPLASH_JAR_ENV_ENTRY);
    const char *file_name = getenv(SPLASH_FILE_ENV_ENTRY);
    int data_size;
    void *image_data = NULL;
    float scale_factor = 1;
    char *scaled_splash_name = NULL;
    jboolean isImageScaled = JNI_FALSE;
    size_t maxScaledImgNameLength = 0;
    if (file_name == NULL){
        return;
    }

    if (!DoSplashInit()) {
        goto exit;
    }

    maxScaledImgNameLength = DoSplashGetScaledImgNameMaxPstfixLen(file_name);

    scaled_splash_name = JLI_MemAlloc(
                            maxScaledImgNameLength * sizeof(char));
    isImageScaled = DoSplashGetScaledImageName(jar_name, file_name,
                            &scale_factor,
                            scaled_splash_name, maxScaledImgNameLength);
    if (jar_name) {

        if (isImageScaled) {
            image_data = JLI_JarUnpackFile(
                    jar_name, scaled_splash_name, &data_size);
        }

        if (!image_data) {
            scale_factor = 1;
            image_data = JLI_JarUnpackFile(
                            jar_name, file_name, &data_size);
        }
        if (image_data) {
            DoSplashSetScaleFactor(scale_factor);
            DoSplashLoadMemory(image_data, data_size);
            JLI_MemFree(image_data);
        } else {
            DoSplashClose();
        }
    } else {
        if (isImageScaled) {
            DoSplashSetScaleFactor(scale_factor);
            DoSplashLoadFile(scaled_splash_name);
        } else {
            DoSplashLoadFile(file_name);
        }
    }
    JLI_MemFree(scaled_splash_name);

    DoSplashSetFileJarName(file_name, jar_name);

    exit:
    /*
     * Done with all command line processing and potential re-execs so
     * clean up the environment.
     */
    (void)UnsetEnv(MAIN_CLASS_ENV_ENTRY);
    (void)UnsetEnv(SPLASH_FILE_ENV_ENTRY);
    (void)UnsetEnv(SPLASH_JAR_ENV_ENTRY);

    JLI_MemFree(splash_jar_entry);
    splash_jar_entry = NULL;
    JLI_MemFree(splash_file_entry);
    splash_file_entry = NULL;
}

static const char* GetFullVersion()
{
    return _fVersion;
}

static const char* GetProgramName()
{
    return _program_name;
}

static const char* GetLauncherName()
{
    return _launcher_name;
}

static jboolean IsJavaArgs()
{
    return _is_java_args;
}

static jboolean
IsWildCardEnabled()
{
    return _wc_enabled;
}

int
ContinueInNewThread(InvocationFunctions* ifn, jlong threadStackSize,
                    int argc, char **argv,
                    int mode, char *what, int ret)
{
    if (threadStackSize == 0) {
        /*
         * If the user hasn't specified a non-zero stack size ask the JVM for its default.
         * A returned 0 means 'use the system default' for a platform, e.g., Windows.
         * Note that HotSpot no longer supports JNI_VERSION_1_1 but it will
         * return its default stack size through the init args structure.
         */
        struct JDK1_1InitArgs args1_1;
        memset((void*)&args1_1, 0, sizeof(args1_1));
        args1_1.version = JNI_VERSION_1_1;
        ifn->GetDefaultJavaVMInitArgs(&args1_1);  /* ignore return value */
        if (args1_1.javaStackSize > 0) {
            threadStackSize = args1_1.javaStackSize;
        }
    }

    { /* Create a new thread to create JVM and invoke main method */
        JavaMainArgs args;
        int rslt;

        args.argc = argc;
        args.argv = argv;
        args.mode = mode;
        args.what = what;
        args.ifn = *ifn;

        rslt = CallJavaMainInNewThread(threadStackSize, (void*)&args);
        /* If the caller has deemed there is an error we
         * simply return that, otherwise we return the value of
         * the callee
         */
        return (ret != 0) ? ret : rslt;
    }
}

static void
DumpState()
{
    if (!JLI_IsTraceLauncher()) return ;
    printf("Launcher state:\n");
    printf("\tFirst application arg index: %d\n", JLI_GetAppArgIndex());
    printf("\tdebug:%s\n", (JLI_IsTraceLauncher() == JNI_TRUE) ? "on" : "off");
    printf("\tjavargs:%s\n", (_is_java_args == JNI_TRUE) ? "on" : "off");
    printf("\tprogram name:%s\n", GetProgramName());
    printf("\tlauncher name:%s\n", GetLauncherName());
    printf("\tjavaw:%s\n", (IsJavaw() == JNI_TRUE) ? "on" : "off");
    printf("\tfullversion:%s\n", GetFullVersion());
}

/*
 * A utility procedure to always print to stderr
 */
JNIEXPORT void JNICALL
JLI_ReportMessage(const char* fmt, ...)
{
    va_list vl;
    va_start(vl, fmt);
    vfprintf(stderr, fmt, vl);
    fprintf(stderr, "\n");
    va_end(vl);
}

/*
 * A utility procedure to always print to stdout
 */
void
JLI_ShowMessage(const char* fmt, ...)
{
    va_list vl;
    va_start(vl, fmt);
    vfprintf(stdout, fmt, vl);
    fprintf(stdout, "\n");
    va_end(vl);
}
