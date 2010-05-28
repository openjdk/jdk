/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

/*
 * Gamma (Hotspot internal engineering test) launcher based on 1.6.0-b28 JDK,
 * search "GAMMA" for gamma specific changes.
 *
 * GAMMA: gamma launcher is much simpler than regular java launcher in that
 *        JVM is either statically linked in or it is installed in the
 *        same directory where the launcher exists, so we don't have to
 *        worry about choosing the right JVM based on command line flag, jar
 *        file and/or ergonomics. Intead of removing unused logic from source
 *        they are commented out with #ifndef GAMMA, hopefully it'll be easier
 *        to maintain this file in sync with regular JDK launcher.
 */

/*
 * Shared source for 'java' command line tool.
 *
 * If JAVA_ARGS is defined, then acts as a launcher for applications. For
 * instance, the JDK command line tools such as javac and javadoc (see
 * makefiles for more details) are built with this program.  Any arguments
 * prefixed with '-J' will be passed directly to the 'java' command.
 */

#ifdef GAMMA
#  ifdef JAVA_ARGS
#    error Do NOT define JAVA_ARGS when building gamma launcher
#  endif
#  if !defined(LINK_INTO_AOUT) && !defined(LINK_INTO_LIBJVM)
#    error Either LINK_INTO_AOUT or LINK_INTO_LIBJVM must be defined
#  endif
#endif

/*
 * One job of the launcher is to remove command line options which the
 * vm does not understand and will not process.  These options include
 * options which select which style of vm is run (e.g. -client and
 * -server) as well as options which select the data model to use.
 * Additionally, for tools which invoke an underlying vm "-J-foo"
 * options are turned into "-foo" options to the vm.  This option
 * filtering is handled in a number of places in the launcher, some of
 * it in machine-dependent code.  In this file, the function
 * CheckJVMType removes vm style options and TranslateDashJArgs
 * removes "-J" prefixes.  On unix platforms, the
 * CreateExecutionEnvironment function from the unix java_md.c file
 * processes and removes -d<n> options.  However, in case
 * CreateExecutionEnvironment does not need to exec because
 * LD_LIBRARY_PATH is set acceptably and the data model does not need
 * to be changed, ParseArguments will screen out the redundant -d<n>
 * options and prevent them from being passed to the vm; this is done
 * by using the machine-dependent call
 * RemovableMachineDependentOption.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <jni.h>
#include "java.h"

#ifndef GAMMA
#include "manifest_info.h"
#include "version_comp.h"
#endif

#ifndef FULL_VERSION
#define FULL_VERSION JDK_MAJOR_VERSION "." JDK_MINOR_VERSION
#endif

/*
 * The following environment variable is used to influence the behavior
 * of the jre exec'd through the SelectVersion routine.  The command line
 * options which specify the version are not passed to the exec'd version,
 * because that jre may be an older version which wouldn't recognize them.
 * This environment variable is known to this (and later) version and serves
 * to suppress the version selection code.  This is not only for efficiency,
 * but also for correctness, since any command line options have been
 * removed which would cause any value found in the manifest to be used.
 * This would be incorrect because the command line options are defined
 * to take precedence.
 *
 * The value associated with this environment variable is the MainClass
 * name from within the executable jar file (if any). This is strictly a
 * performance enhancement to avoid re-reading the jar file manifest.
 *
 * A NOTE TO DEVELOPERS: For performance reasons it is important that
 * the program image remain relatively small until after SelectVersion
 * CreateExecutionEnvironment have finished their possibly recursive
 * processing. Watch everything, but resist all temptations to use Java
 * interfaces.
 */
#define ENV_ENTRY "_JAVA_VERSION_SET"

static jboolean printVersion = JNI_FALSE; /* print and exit */
static jboolean showVersion = JNI_FALSE;  /* print but continue */
static char *progname;
jboolean _launcher_debug = JNI_FALSE;

/*
 * List of VM options to be specified when the VM is created.
 */
static JavaVMOption *options;
static int numOptions, maxOptions;

/*
 * Prototypes for functions internal to launcher.
 */
static void AddOption(char *str, void *info);
static void SetClassPath(char *s);
static void SelectVersion(int argc, char **argv, char **main_class);
static jboolean ParseArguments(int *pargc, char ***pargv, char **pjarfile,
                               char **pclassname, int *pret);
static jboolean InitializeJVM(JavaVM **pvm, JNIEnv **penv,
                              InvocationFunctions *ifn);
static jstring NewPlatformString(JNIEnv *env, char *s);
static jobjectArray NewPlatformStringArray(JNIEnv *env, char **strv, int strc);
static jclass LoadClass(JNIEnv *env, char *name);
static jstring GetMainClassName(JNIEnv *env, char *jarname);
static void SetJavaCommandLineProp(char* classname, char* jarfile, int argc, char** argv);
#ifdef GAMMA
static void SetJavaLauncherProp(void);
#endif

#ifdef JAVA_ARGS
static void TranslateDashJArgs(int *pargc, char ***pargv);
static jboolean AddApplicationOptions(void);
#endif

static void PrintJavaVersion(JNIEnv *env);
static void PrintUsage(void);
static jint PrintXUsage(void);

static void SetPaths(int argc, char **argv);

/* Maximum supported entries from jvm.cfg. */
#define INIT_MAX_KNOWN_VMS      10
/* Values for vmdesc.flag */
#define VM_UNKNOWN              -1
#define VM_KNOWN                 0
#define VM_ALIASED_TO            1
#define VM_WARN                  2
#define VM_ERROR                 3
#define VM_IF_SERVER_CLASS       4
#define VM_IGNORE                5
struct vmdesc {
    char *name;
    int flag;
    char *alias;
    char *server_class;
};
static struct vmdesc *knownVMs = NULL;
static int knownVMsCount = 0;
static int knownVMsLimit = 0;

static void GrowKnownVMs();
static int  KnownVMIndex(const char* name);
static void FreeKnownVMs();

jboolean ServerClassMachine();

/* flag which if set suppresses error messages from the launcher */
static int noExitErrorMessage = 0;

/*
 * Entry point.
 */
int
main(int argc, char ** argv)
{
    JavaVM *vm = 0;
    JNIEnv *env = 0;
    char *jarfile = 0;
    char *classname = 0;
    char *s = 0;
    char *main_class = NULL;
    jstring mainClassName;
    jclass mainClass;
    jmethodID mainID;
    jobjectArray mainArgs;
    int ret;
    InvocationFunctions ifn;
    jlong start, end;
    char jrepath[MAXPATHLEN], jvmpath[MAXPATHLEN];
    char ** original_argv = argv;

    /*
     * Error message to print or display; by default the message will
     * only be displayed in a window.
     */
    char * message = "Fatal exception occurred.  Program will exit.";
    jboolean messageDest = JNI_FALSE;

    if (getenv("_JAVA_LAUNCHER_DEBUG") != 0) {
        _launcher_debug = JNI_TRUE;
        printf("----_JAVA_LAUNCHER_DEBUG----\n");
    }

#ifndef GAMMA
    /*
     * Make sure the specified version of the JRE is running.
     *
     * There are three things to note about the SelectVersion() routine:
     *  1) If the version running isn't correct, this routine doesn't
     *     return (either the correct version has been exec'd or an error
     *     was issued).
     *  2) Argc and Argv in this scope are *not* altered by this routine.
     *     It is the responsibility of subsequent code to ignore the
     *     arguments handled by this routine.
     *  3) As a side-effect, the variable "main_class" is guaranteed to
     *     be set (if it should ever be set).  This isn't exactly the
     *     poster child for structured programming, but it is a small
     *     price to pay for not processing a jar file operand twice.
     *     (Note: This side effect has been disabled.  See comment on
     *     bugid 5030265 below.)
     */
    SelectVersion(argc, argv, &main_class);
#endif /* ifndef GAMMA */

    /* copy original argv */
    {
      int i;
      original_argv = (char**)MemAlloc(sizeof(char*)*(argc+1));
      for(i = 0; i < argc+1; i++)
        original_argv[i] = argv[i];
    }

    CreateExecutionEnvironment(&argc, &argv,
                               jrepath, sizeof(jrepath),
                               jvmpath, sizeof(jvmpath),
                               original_argv);
    ifn.CreateJavaVM = 0;
    ifn.GetDefaultJavaVMInitArgs = 0;

    if (_launcher_debug)
      start = CounterGet();
    if (!LoadJavaVM(jvmpath, &ifn)) {
      exit(6);
    }
    if (_launcher_debug) {
      end   = CounterGet();
      printf("%ld micro seconds to LoadJavaVM\n",
             (long)(jint)Counter2Micros(end-start));
    }

#ifdef JAVA_ARGS  /* javac, jar and friends. */
    progname = "java";
#else             /* java, oldjava, javaw and friends */
#ifdef PROGNAME
    progname = PROGNAME;
#else
    progname = *argv;
    if ((s = strrchr(progname, FILE_SEPARATOR)) != 0) {
        progname = s + 1;
    }
#endif /* PROGNAME */
#endif /* JAVA_ARGS */
    ++argv;
    --argc;

#ifdef JAVA_ARGS
    /* Preprocess wrapper arguments */
    TranslateDashJArgs(&argc, &argv);
    if (!AddApplicationOptions()) {
        exit(1);
    }
#endif

    /* Set default CLASSPATH */
    if ((s = getenv("CLASSPATH")) == 0) {
        s = ".";
    }
#ifndef JAVA_ARGS
    SetClassPath(s);
#endif

    /*
     *  Parse command line options; if the return value of
     *  ParseArguments is false, the program should exit.
     */
    if (!ParseArguments(&argc, &argv, &jarfile, &classname, &ret)) {
      exit(ret);
    }

    /* Override class path if -jar flag was specified */
    if (jarfile != 0) {
        SetClassPath(jarfile);
    }

    /* set the -Dsun.java.command pseudo property */
    SetJavaCommandLineProp(classname, jarfile, argc, argv);

#ifdef GAMMA
    /* Set the -Dsun.java.launcher pseudo property */
    SetJavaLauncherProp();
#endif

    /*
     * Done with all command line processing and potential re-execs so
     * clean up the environment.
     */
    (void)UnsetEnv(ENV_ENTRY);

    /* Initialize the virtual machine */

    if (_launcher_debug)
        start = CounterGet();
    if (!InitializeJVM(&vm, &env, &ifn)) {
        ReportErrorMessage("Could not create the Java virtual machine.",
                           JNI_TRUE);
        exit(1);
    }

    if (printVersion || showVersion) {
        PrintJavaVersion(env);
        if ((*env)->ExceptionOccurred(env)) {
            ReportExceptionDescription(env);
            goto leave;
        }
        if (printVersion) {
            ret = 0;
            message = NULL;
            goto leave;
        }
        if (showVersion) {
            fprintf(stderr, "\n");
        }
    }

    /* If the user specified neither a class name nor a JAR file */
    if (jarfile == 0 && classname == 0) {
        PrintUsage();
        message = NULL;
        goto leave;
    }

#ifndef GAMMA
    FreeKnownVMs();  /* after last possible PrintUsage() */
#endif

    if (_launcher_debug) {
        end   = CounterGet();
        printf("%ld micro seconds to InitializeJVM\n",
               (long)(jint)Counter2Micros(end-start));
    }

    /* At this stage, argc/argv have the applications' arguments */
    if (_launcher_debug) {
        int i = 0;
        printf("Main-Class is '%s'\n", classname ? classname : "");
        printf("Apps' argc is %d\n", argc);
        for (; i < argc; i++) {
            printf("    argv[%2d] = '%s'\n", i, argv[i]);
        }
    }

    ret = 1;

    /*
     * Get the application's main class.
     *
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
     */
    if (jarfile != 0) {
        mainClassName = GetMainClassName(env, jarfile);
        if ((*env)->ExceptionOccurred(env)) {
            ReportExceptionDescription(env);
            goto leave;
        }
        if (mainClassName == NULL) {
          const char * format = "Failed to load Main-Class manifest "
                                "attribute from\n%s";
          message = (char*)MemAlloc((strlen(format) + strlen(jarfile)) *
                                    sizeof(char));
          sprintf(message, format, jarfile);
          messageDest = JNI_TRUE;
          goto leave;
        }
        classname = (char *)(*env)->GetStringUTFChars(env, mainClassName, 0);
        if (classname == NULL) {
            ReportExceptionDescription(env);
            goto leave;
        }
        mainClass = LoadClass(env, classname);
        if(mainClass == NULL) { /* exception occurred */
            ReportExceptionDescription(env);
            message = "Could not find the main class.  Program will exit.";
            goto leave;
        }
        (*env)->ReleaseStringUTFChars(env, mainClassName, classname);
    } else {
      mainClassName = NewPlatformString(env, classname);
      if (mainClassName == NULL) {
        const char * format = "Failed to load Main Class: %s";
        message = (char *)MemAlloc((strlen(format) + strlen(classname)) *
                                   sizeof(char) );
        sprintf(message, format, classname);
        messageDest = JNI_TRUE;
        goto leave;
      }
      classname = (char *)(*env)->GetStringUTFChars(env, mainClassName, 0);
      if (classname == NULL) {
        ReportExceptionDescription(env);
        goto leave;
      }
      mainClass = LoadClass(env, classname);
      if(mainClass == NULL) { /* exception occurred */
        ReportExceptionDescription(env);
        message = "Could not find the main class. Program will exit.";
        goto leave;
      }
      (*env)->ReleaseStringUTFChars(env, mainClassName, classname);
    }

    /* Get the application's main method */
    mainID = (*env)->GetStaticMethodID(env, mainClass, "main",
                                       "([Ljava/lang/String;)V");
    if (mainID == NULL) {
        if ((*env)->ExceptionOccurred(env)) {
            ReportExceptionDescription(env);
        } else {
          message = "No main method found in specified class.";
          messageDest = JNI_TRUE;
        }
        goto leave;
    }

    {    /* Make sure the main method is public */
        jint mods;
        jmethodID mid;
        jobject obj = (*env)->ToReflectedMethod(env, mainClass,
                                                mainID, JNI_TRUE);

        if( obj == NULL) { /* exception occurred */
            ReportExceptionDescription(env);
            goto leave;
        }

        mid =
          (*env)->GetMethodID(env,
                              (*env)->GetObjectClass(env, obj),
                              "getModifiers", "()I");
        if ((*env)->ExceptionOccurred(env)) {
            ReportExceptionDescription(env);
            goto leave;
        }

        mods = (*env)->CallIntMethod(env, obj, mid);
        if ((mods & 1) == 0) { /* if (!Modifier.isPublic(mods)) ... */
            message = "Main method not public.";
            messageDest = JNI_TRUE;
            goto leave;
        }
    }

    /* Build argument array */
    mainArgs = NewPlatformStringArray(env, argv, argc);
    if (mainArgs == NULL) {
        ReportExceptionDescription(env);
        goto leave;
    }

    /* Invoke main method. */
    (*env)->CallStaticVoidMethod(env, mainClass, mainID, mainArgs);

    /*
     * The launcher's exit code (in the absence of calls to
     * System.exit) will be non-zero if main threw an exception.
     */
    ret = (*env)->ExceptionOccurred(env) == NULL ? 0 : 1;

    /*
     * Detach the main thread so that it appears to have ended when
     * the application's main method exits.  This will invoke the
     * uncaught exception handler machinery if main threw an
     * exception.  An uncaught exception handler cannot change the
     * launcher's return code except by calling System.exit.
     */
    if ((*vm)->DetachCurrentThread(vm) != 0) {
        message = "Could not detach main thread.";
        messageDest = JNI_TRUE;
        ret = 1;
        goto leave;
    }

    message = NULL;

 leave:
    /*
     * Wait for all non-daemon threads to end, then destroy the VM.
     * This will actually create a trivial new Java waiter thread
     * named "DestroyJavaVM", but this will be seen as a different
     * thread from the one that executed main, even though they are
     * the same C thread.  This allows mainThread.join() and
     * mainThread.isAlive() to work as expected.
     */
    (*vm)->DestroyJavaVM(vm);

    if(message != NULL && !noExitErrorMessage)
      ReportErrorMessage(message, messageDest);
    return ret;
}


#ifndef GAMMA
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
    newArgv = MemAlloc((argc + 1) * sizeof(char *));

    /* The program name is always present */
    newArgv[newArgvIdx++] = (*argv)[0];

    for (argi = 1; argi < argc; argi++) {
        char *arg = (*argv)[argi];
        isVMType = 0;

#ifdef JAVA_ARGS
        if (arg[0] != '-') {
            newArgv[newArgvIdx++] = arg;
            continue;
        }
#else
        if (strcmp(arg, "-classpath") == 0 ||
            strcmp(arg, "-cp") == 0) {
            newArgv[newArgvIdx++] = arg;
            argi++;
            if (argi < argc) {
                newArgv[newArgvIdx++] = (*argv)[argi];
            }
            continue;
        }
        if (arg[0] != '-') break;
#endif

        /* Did the user pass an explicit VM type? */
        i = KnownVMIndex(arg);
        if (i >= 0) {
            jvmtype = knownVMs[jvmidx = i].name + 1; /* skip the - */
            isVMType = 1;
            *pargc = *pargc - 1;
        }

        /* Did the user specify an "alternate" VM? */
        else if (strncmp(arg, "-XXaltjvm=", 10) == 0 || strncmp(arg, "-J-XXaltjvm=", 12) == 0) {
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
      /* Use a different VM type if we are on a server class machine? */
      if ((knownVMs[0].flag == VM_IF_SERVER_CLASS) &&
          (ServerClassMachine() == JNI_TRUE)) {
        result = knownVMs[0].server_class+1;
      }
      if (_launcher_debug) {
        printf("Default VM: %s\n", result);
      }
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
            ReportErrorMessage("Error: Corrupt jvm.cfg file; cycle in alias list.",
                               JNI_TRUE);
            exit(1);
          } else {
            return "ERROR";
            /* break; */
          }
        }

        if (nextIdx < 0) {
          if (!speculative) {
            ReportErrorMessage2("Error: Unable to resolve VM alias %s",
                                knownVMs[jvmidx].alias, JNI_TRUE);
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
            fprintf(stderr, "Warning: %s VM not supported; %s VM will be used\n",
                    jvmtype, knownVMs[0].name + 1);
        }
        /* fall through */
    case VM_IGNORE:
        jvmtype = knownVMs[jvmidx=0].name + 1;
        /* fall through */
    case VM_KNOWN:
        break;
    case VM_ERROR:
        if (!speculative) {
            ReportErrorMessage2("Error: %s VM not supported", jvmtype, JNI_TRUE);
            exit(1);
        } else {
            return "ERROR";
        }
    }

    return jvmtype;
}
#endif /* ifndef GAMMA */

/*
 * Adds a new VM option with the given given name and value.
 */
static void
AddOption(char *str, void *info)
{
    /*
     * Expand options array if needed to accommodate at least one more
     * VM option.
     */
    if (numOptions >= maxOptions) {
        if (options == 0) {
            maxOptions = 4;
            options = MemAlloc(maxOptions * sizeof(JavaVMOption));
        } else {
            JavaVMOption *tmp;
            maxOptions *= 2;
            tmp = MemAlloc(maxOptions * sizeof(JavaVMOption));
            memcpy(tmp, options, numOptions * sizeof(JavaVMOption));
            free(options);
            options = tmp;
        }
    }
    options[numOptions].optionString = str;
    options[numOptions++].extraInfo = info;
}

static void
SetClassPath(char *s)
{
    char *def = MemAlloc(strlen(s) + 40);
    sprintf(def, "-Djava.class.path=%s", s);
    AddOption(def, NULL);
}

#ifndef GAMMA
/*
 * The SelectVersion() routine ensures that an appropriate version of
 * the JRE is running.  The specification for the appropriate version
 * is obtained from either the manifest of a jar file (preferred) or
 * from command line options.
 */
static void
SelectVersion(int argc, char **argv, char **main_class)
{
    char    *arg;
    char    **new_argv;
    char    **new_argp;
    char    *operand;
    char    *version = NULL;
    char    *jre = NULL;
    int     jarflag = 0;
    int     restrict_search = -1;               /* -1 implies not known */
    manifest_info info;
    char    env_entry[MAXNAMELEN + 24] = ENV_ENTRY "=";
    char    *env_in;
    int     res;

    /*
     * If the version has already been selected, set *main_class
     * with the value passed through the environment (if any) and
     * simply return.
     */
    if ((env_in = getenv(ENV_ENTRY)) != NULL) {
        if (*env_in != '\0')
            *main_class = strdup(env_in);
        return;
    }

    /*
     * Scan through the arguments for options relevant to multiple JRE
     * support.  For reference, the command line syntax is defined as:
     *
     * SYNOPSIS
     *      java [options] class [argument...]
     *
     *      java [options] -jar file.jar [argument...]
     *
     * As the scan is performed, make a copy of the argument list with
     * the version specification options (new to 1.5) removed, so that
     * a version less than 1.5 can be exec'd.
     */
    new_argv = MemAlloc((argc + 1) * sizeof(char*));
    new_argv[0] = argv[0];
    new_argp = &new_argv[1];
    argc--;
    argv++;
    while ((arg = *argv) != 0 && *arg == '-') {
        if (strncmp(arg, "-version:", 9) == 0) {
            version = arg + 9;
        } else if (strcmp(arg, "-jre-restrict-search") == 0) {
            restrict_search = 1;
        } else if (strcmp(arg, "-no-jre-restrict-search") == 0) {
            restrict_search = 0;
        } else {
            if (strcmp(arg, "-jar") == 0)
                jarflag = 1;
            /* deal with "unfortunate" classpath syntax */
            if ((strcmp(arg, "-classpath") == 0 || strcmp(arg, "-cp") == 0) &&
              (argc >= 2)) {
                *new_argp++ = arg;
                argc--;
                argv++;
                arg = *argv;
            }
            *new_argp++ = arg;
        }
        argc--;
        argv++;
    }
    if (argc <= 0) {    /* No operand? Possibly legit with -[full]version */
        operand = NULL;
    } else {
        argc--;
        *new_argp++ = operand = *argv++;
    }
    while (argc-- > 0)  /* Copy over [argument...] */
        *new_argp++ = *argv++;
    *new_argp = NULL;

    /*
     * If there is a jar file, read the manifest. If the jarfile can't be
     * read, the manifest can't be read from the jar file, or the manifest
     * is corrupt, issue the appropriate error messages and exit.
     *
     * Even if there isn't a jar file, construct a manifest_info structure
     * containing the command line information.  It's a convenient way to carry
     * this data around.
     */
    if (jarflag && operand) {
        if ((res = parse_manifest(operand, &info)) != 0) {
            if (res == -1)
                ReportErrorMessage2("Unable to access jarfile %s",
                  operand, JNI_TRUE);
            else
                ReportErrorMessage2("Invalid or corrupt jarfile %s",
                  operand, JNI_TRUE);
            exit(1);
        }
    } else {
        info.manifest_version = NULL;
        info.main_class = NULL;
        info.jre_version = NULL;
        info.jre_restrict_search = 0;
    }

    /*
     * The JRE-Version and JRE-Restrict-Search values (if any) from the
     * manifest are overwritten by any specified on the command line.
     */
    if (version != NULL)
        info.jre_version = version;
    if (restrict_search != -1)
        info.jre_restrict_search = restrict_search;

    /*
     * "Valid" returns (other than unrecoverable errors) follow.  Set
     * main_class as a side-effect of this routine.
     */
    if (info.main_class != NULL)
        *main_class = strdup(info.main_class);

    /*
     * If no version selection information is found either on the command
     * line or in the manifest, simply return.
     */
    if (info.jre_version == NULL) {
        free_manifest();
        free(new_argv);
        return;
    }

    /*
     * Check for correct syntax of the version specification (JSR 56).
     */
    if (!valid_version_string(info.jre_version)) {
        ReportErrorMessage2("Syntax error in version specification \"%s\"",
          info.jre_version, JNI_TRUE);
        exit(1);
    }

    /*
     * Find the appropriate JVM on the system. Just to be as forgiving as
     * possible, if the standard algorithms don't locate an appropriate
     * jre, check to see if the one running will satisfy the requirements.
     * This can happen on systems which haven't been set-up for multiple
     * JRE support.
     */
    jre = LocateJRE(&info);
    if (_launcher_debug)
        printf("JRE-Version = %s, JRE-Restrict-Search = %s Selected = %s\n",
          (info.jre_version?info.jre_version:"null"),
          (info.jre_restrict_search?"true":"false"), (jre?jre:"null"));
    if (jre == NULL) {
        if (acceptable_release(FULL_VERSION, info.jre_version)) {
            free_manifest();
            free(new_argv);
            return;
        } else {
            ReportErrorMessage2(
              "Unable to locate JRE meeting specification \"%s\"",
              info.jre_version, JNI_TRUE);
            exit(1);
        }
    }

    /*
     * If I'm not the chosen one, exec the chosen one.  Returning from
     * ExecJRE indicates that I am indeed the chosen one.
     *
     * The private environment variable _JAVA_VERSION_SET is used to
     * prevent the chosen one from re-reading the manifest file and
     * using the values found within to override the (potential) command
     * line flags stripped from argv (because the target may not
     * understand them).  Passing the MainClass value is an optimization
     * to avoid locating, expanding and parsing the manifest extra
     * times.
     */
    if (info.main_class != NULL)
        (void)strcat(env_entry, info.main_class);
    (void)putenv(env_entry);
    ExecJRE(jre, new_argv);
    free_manifest();
    free(new_argv);
    return;
}
#endif /* ifndef GAMMA */

/*
 * Parses command line arguments.  Returns JNI_FALSE if launcher
 * should exit without starting vm (e.g. certain version and usage
 * options); returns JNI_TRUE if vm needs to be started to process
 * given options.  *pret (the launcher process return value) is set to
 * 0 for a normal exit.
 */
static jboolean
ParseArguments(int *pargc, char ***pargv, char **pjarfile,
                       char **pclassname, int *pret)
{
    int argc = *pargc;
    char **argv = *pargv;
    jboolean jarflag = JNI_FALSE;
    char *arg;

    *pret = 1;
    while ((arg = *argv) != 0 && *arg == '-') {
        argv++; --argc;
        if (strcmp(arg, "-classpath") == 0 || strcmp(arg, "-cp") == 0) {
            if (argc < 1) {
                ReportErrorMessage2("%s requires class path specification",
                                    arg, JNI_TRUE);
                PrintUsage();
                return JNI_FALSE;
            }
            SetClassPath(*argv);
            argv++; --argc;
        } else if (strcmp(arg, "-jar") == 0) {
            jarflag = JNI_TRUE;
        } else if (strcmp(arg, "-help") == 0 ||
                   strcmp(arg, "-h") == 0 ||
                   strcmp(arg, "-?") == 0) {
            PrintUsage();
            *pret = 0;
            return JNI_FALSE;
        } else if (strcmp(arg, "-version") == 0) {
            printVersion = JNI_TRUE;
            return JNI_TRUE;
        } else if (strcmp(arg, "-showversion") == 0) {
            showVersion = JNI_TRUE;
        } else if (strcmp(arg, "-X") == 0) {
            *pret = PrintXUsage();
            return JNI_FALSE;
/*
 * The following case provide backward compatibility with old-style
 * command line options.
 */
        } else if (strcmp(arg, "-fullversion") == 0) {
            fprintf(stderr, "%s full version \"%s\"\n", progname,
                    FULL_VERSION);
            *pret = 0;
            return JNI_FALSE;
        } else if (strcmp(arg, "-verbosegc") == 0) {
            AddOption("-verbose:gc", NULL);
        } else if (strcmp(arg, "-t") == 0) {
            AddOption("-Xt", NULL);
        } else if (strcmp(arg, "-tm") == 0) {
            AddOption("-Xtm", NULL);
        } else if (strcmp(arg, "-debug") == 0) {
            AddOption("-Xdebug", NULL);
        } else if (strcmp(arg, "-noclassgc") == 0) {
            AddOption("-Xnoclassgc", NULL);
        } else if (strcmp(arg, "-Xfuture") == 0) {
            AddOption("-Xverify:all", NULL);
        } else if (strcmp(arg, "-verify") == 0) {
            AddOption("-Xverify:all", NULL);
        } else if (strcmp(arg, "-verifyremote") == 0) {
            AddOption("-Xverify:remote", NULL);
        } else if (strcmp(arg, "-noverify") == 0) {
            AddOption("-Xverify:none", NULL);
        } else if (strcmp(arg, "-XXsuppressExitMessage") == 0) {
            noExitErrorMessage = 1;
        } else if (strncmp(arg, "-prof", 5) == 0) {
            char *p = arg + 5;
            char *tmp = MemAlloc(strlen(arg) + 50);
            if (*p) {
                sprintf(tmp, "-Xrunhprof:cpu=old,file=%s", p + 1);
            } else {
                sprintf(tmp, "-Xrunhprof:cpu=old,file=java.prof");
            }
            AddOption(tmp, NULL);
        } else if (strncmp(arg, "-ss", 3) == 0 ||
                   strncmp(arg, "-oss", 4) == 0 ||
                   strncmp(arg, "-ms", 3) == 0 ||
                   strncmp(arg, "-mx", 3) == 0) {
            char *tmp = MemAlloc(strlen(arg) + 6);
            sprintf(tmp, "-X%s", arg + 1); /* skip '-' */
            AddOption(tmp, NULL);
        } else if (strcmp(arg, "-checksource") == 0 ||
                   strcmp(arg, "-cs") == 0 ||
                   strcmp(arg, "-noasyncgc") == 0) {
            /* No longer supported */
            fprintf(stderr,
                    "Warning: %s option is no longer supported.\n",
                    arg);
        } else if (strncmp(arg, "-version:", 9) == 0 ||
                   strcmp(arg, "-no-jre-restrict-search") == 0 ||
                   strcmp(arg, "-jre-restrict-search") == 0) {
            ; /* Ignore machine independent options already handled */
        } else if (RemovableMachineDependentOption(arg) ) {
            ; /* Do not pass option to vm. */
        }
        else {
            AddOption(arg, NULL);
        }
    }

    if (--argc >= 0) {
        if (jarflag) {
            *pjarfile = *argv++;
            *pclassname = 0;
        } else {
            *pjarfile = 0;
            *pclassname = *argv++;
        }
        *pargc = argc;
        *pargv = argv;
    }

    return JNI_TRUE;
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

    if (_launcher_debug) {
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
    free(options);
    return r == JNI_OK;
}


#define NULL_CHECK0(e) if ((e) == 0) return 0
#define NULL_CHECK(e) if ((e) == 0) return

/*
 * Returns a pointer to a block of at least 'size' bytes of memory.
 * Prints error message and exits if the memory could not be allocated.
 */
void *
MemAlloc(size_t size)
{
    void *p = malloc(size);
    if (p == 0) {
        perror("malloc");
        exit(1);
    }
    return p;
}

static jstring platformEncoding = NULL;
static jstring getPlatformEncoding(JNIEnv *env) {
    if (platformEncoding == NULL) {
        jstring propname = (*env)->NewStringUTF(env, "sun.jnu.encoding");
        if (propname) {
            jclass cls;
            jmethodID mid;
            NULL_CHECK0 (cls = FindBootStrapClass(env, "java/lang/System"));
            NULL_CHECK0 (mid = (*env)->GetStaticMethodID(
                                   env, cls,
                                   "getProperty",
                                   "(Ljava/lang/String;)Ljava/lang/String;"));
            platformEncoding = (*env)->CallStaticObjectMethod (
                                    env, cls, mid, propname);
        }
    }
    return platformEncoding;
}

static jboolean isEncodingSupported(JNIEnv *env, jstring enc) {
    jclass cls;
    jmethodID mid;
    NULL_CHECK0 (cls = FindBootStrapClass(env, "java/nio/charset/Charset"));
    NULL_CHECK0 (mid = (*env)->GetStaticMethodID(
                           env, cls,
                           "isSupported",
                           "(Ljava/lang/String;)Z"));
    return (*env)->CallStaticBooleanMethod (env, cls, mid, enc);
}

/*
 * Returns a new Java string object for the specified platform string.
 */
static jstring
NewPlatformString(JNIEnv *env, char *s)
{
    int len = (int)strlen(s);
    jclass cls;
    jmethodID mid;
    jbyteArray ary;
    jstring enc;

    if (s == NULL)
        return 0;
    enc = getPlatformEncoding(env);

    ary = (*env)->NewByteArray(env, len);
    if (ary != 0) {
        jstring str = 0;
        (*env)->SetByteArrayRegion(env, ary, 0, len, (jbyte *)s);
        if (!(*env)->ExceptionOccurred(env)) {
#ifdef GAMMA
            /* We support running JVM with older JDK, so here we have to deal */
            /* with the case that sun.jnu.encoding is undefined (enc == NULL) */
            if (enc != NULL && isEncodingSupported(env, enc) == JNI_TRUE) {
#else
            if (isEncodingSupported(env, enc) == JNI_TRUE) {
#endif
                NULL_CHECK0(cls = FindBootStrapClass(env, "java/lang/String"));
                NULL_CHECK0(mid = (*env)->GetMethodID(env, cls, "<init>",
                                          "([BLjava/lang/String;)V"));
                str = (*env)->NewObject(env, cls, mid, ary, enc);
            } else {
                /*If the encoding specified in sun.jnu.encoding is not
                  endorsed by "Charset.isSupported" we have to fall back
                  to use String(byte[]) explicitly here without specifying
                  the encoding name, in which the StringCoding class will
                  pickup the iso-8859-1 as the fallback converter for us.
                */
                NULL_CHECK0(cls = FindBootStrapClass(env, "java/lang/String"));
                NULL_CHECK0(mid = (*env)->GetMethodID(env, cls, "<init>",
                                          "([B)V"));
                str = (*env)->NewObject(env, cls, mid, ary);
            }
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
static jobjectArray
NewPlatformStringArray(JNIEnv *env, char **strv, int strc)
{
    jarray cls;
    jarray ary;
    int i;

    NULL_CHECK0(cls = FindBootStrapClass(env, "java/lang/String"));
    NULL_CHECK0(ary = (*env)->NewObjectArray(env, strc, cls, 0));
    for (i = 0; i < strc; i++) {
        jstring str = NewPlatformString(env, *strv++);
        NULL_CHECK0(str);
        (*env)->SetObjectArrayElement(env, ary, i, str);
        (*env)->DeleteLocalRef(env, str);
    }
    return ary;
}

/*
 * Loads a class, convert the '.' to '/'.
 */
static jclass
LoadClass(JNIEnv *env, char *name)
{
    char *buf = MemAlloc(strlen(name) + 1);
    char *s = buf, *t = name, c;
    jclass cls;
    jlong start, end;

    if (_launcher_debug)
        start = CounterGet();

    do {
        c = *t++;
        *s++ = (c == '.') ? '/' : c;
    } while (c != '\0');
    // use the application class loader for main-class
    cls = (*env)->FindClass(env, buf);
    free(buf);

    if (_launcher_debug) {
        end   = CounterGet();
        printf("%ld micro seconds to load main class\n",
               (long)(jint)Counter2Micros(end-start));
        printf("----_JAVA_LAUNCHER_DEBUG----\n");
    }

    return cls;
}


/*
 * Returns the main class name for the specified jar file.
 */
static jstring
GetMainClassName(JNIEnv *env, char *jarname)
{
#define MAIN_CLASS "Main-Class"
    jclass cls;
    jmethodID mid;
    jobject jar, man, attr;
    jstring str, result = 0;

    NULL_CHECK0(cls = FindBootStrapClass(env, "java/util/jar/JarFile"));
    NULL_CHECK0(mid = (*env)->GetMethodID(env, cls, "<init>",
                                          "(Ljava/lang/String;)V"));
    NULL_CHECK0(str = NewPlatformString(env, jarname));
    NULL_CHECK0(jar = (*env)->NewObject(env, cls, mid, str));
    NULL_CHECK0(mid = (*env)->GetMethodID(env, cls, "getManifest",
                                          "()Ljava/util/jar/Manifest;"));
    man = (*env)->CallObjectMethod(env, jar, mid);
    if (man != 0) {
        NULL_CHECK0(mid = (*env)->GetMethodID(env,
                                    (*env)->GetObjectClass(env, man),
                                    "getMainAttributes",
                                    "()Ljava/util/jar/Attributes;"));
        attr = (*env)->CallObjectMethod(env, man, mid);
        if (attr != 0) {
            NULL_CHECK0(mid = (*env)->GetMethodID(env,
                                    (*env)->GetObjectClass(env, attr),
                                    "getValue",
                                    "(Ljava/lang/String;)Ljava/lang/String;"));
            NULL_CHECK0(str = NewPlatformString(env, MAIN_CLASS));
            result = (*env)->CallObjectMethod(env, attr, mid, str);
        }
    }
    return result;
}

#ifdef JAVA_ARGS
static char *java_args[] = JAVA_ARGS;
static char *app_classpath[] = APP_CLASSPATH;

/*
 * For tools convert 'javac -J-ms32m' to 'java -ms32m ...'
 */
static void
TranslateDashJArgs(int *pargc, char ***pargv)
{
    const int NUM_ARGS = (sizeof(java_args) / sizeof(char *));
    int argc = *pargc;
    char **argv = *pargv;
    int nargc = argc + NUM_ARGS;
    char **nargv = MemAlloc((nargc + 1) * sizeof(char *));
    int i;

    *pargc = nargc;
    *pargv = nargv;

    /* Copy the VM arguments (i.e. prefixed with -J) */
    for (i = 0; i < NUM_ARGS; i++) {
        char *arg = java_args[i];
        if (arg[0] == '-' && arg[1] == 'J') {
            *nargv++ = arg + 2;
        }
    }

    for (i = 0; i < argc; i++) {
        char *arg = argv[i];
        if (arg[0] == '-' && arg[1] == 'J') {
            if (arg[2] == '\0') {
                ReportErrorMessage("Error: the -J option should not be "
                                   "followed by a space.", JNI_TRUE);
                exit(1);
            }
            *nargv++ = arg + 2;
        }
    }

    /* Copy the rest of the arguments */
    for (i = 0; i < NUM_ARGS; i++) {
        char *arg = java_args[i];
        if (arg[0] != '-' || arg[1] != 'J') {
            *nargv++ = arg;
        }
    }
    for (i = 0; i < argc; i++) {
        char *arg = argv[i];
        if (arg[0] != '-' || arg[1] != 'J') {
            *nargv++ = arg;
        }
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
AddApplicationOptions()
{
    const int NUM_APP_CLASSPATH = (sizeof(app_classpath) / sizeof(char *));
    char *s, *envcp, *appcp, *apphome;
    char home[MAXPATHLEN]; /* application home */
    char separator[] = { PATH_SEPARATOR, '\0' };
    int size, i;
    int strlenHome;

    s = getenv("CLASSPATH");
    if (s) {
        /* 40 for -Denv.class.path= */
        envcp = (char *)MemAlloc(strlen(s) + 40);
        sprintf(envcp, "-Denv.class.path=%s", s);
        AddOption(envcp, NULL);
    }

    if (!GetApplicationHome(home, sizeof(home))) {
        ReportErrorMessage("Can't determine application home", JNI_TRUE);
        return JNI_FALSE;
    }

    /* 40 for '-Dapplication.home=' */
    apphome = (char *)MemAlloc(strlen(home) + 40);
    sprintf(apphome, "-Dapplication.home=%s", home);
    AddOption(apphome, NULL);

    /* How big is the application's classpath? */
    size = 40;                                 /* 40: "-Djava.class.path=" */
    strlenHome = (int)strlen(home);
    for (i = 0; i < NUM_APP_CLASSPATH; i++) {
        size += strlenHome + (int)strlen(app_classpath[i]) + 1; /* 1: separator */
    }
    appcp = (char *)MemAlloc(size + 1);
    strcpy(appcp, "-Djava.class.path=");
    for (i = 0; i < NUM_APP_CLASSPATH; i++) {
        strcat(appcp, home);                    /* c:\program files\myapp */
        strcat(appcp, app_classpath[i]);        /* \lib\myapp.jar         */
        strcat(appcp, separator);               /* ;                      */
    }
    appcp[strlen(appcp)-1] = '\0';  /* remove trailing path separator */
    AddOption(appcp, NULL);
    return JNI_TRUE;
}
#endif

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
SetJavaCommandLineProp(char *classname, char *jarfile,
                       int argc, char **argv)
{

    int i = 0;
    size_t len = 0;
    char* javaCommand = NULL;
    char* dashDstr = "-Dsun.java.command=";

    if (classname == NULL && jarfile == NULL) {
        /* unexpected, one of these should be set. just return without
         * setting the property
         */
        return;
    }

    /* if the class name is not set, then use the jarfile name */
    if (classname == NULL) {
        classname = jarfile;
    }

    /* determine the amount of memory to allocate assuming
     * the individual components will be space separated
     */
    len = strlen(classname);
    for (i = 0; i < argc; i++) {
        len += strlen(argv[i]) + 1;
    }

    /* allocate the memory */
    javaCommand = (char*) MemAlloc(len + strlen(dashDstr) + 1);

    /* build the -D string */
    *javaCommand = '\0';
    strcat(javaCommand, dashDstr);
    strcat(javaCommand, classname);

    for (i = 0; i < argc; i++) {
        /* the components of the string are space separated. In
         * the case of embedded white space, the relationship of
         * the white space separated components to their true
         * positional arguments will be ambiguous. This issue may
         * be addressed in a future release.
         */
        strcat(javaCommand, " ");
        strcat(javaCommand, argv[i]);
    }

    AddOption(javaCommand, NULL);
}

/*
 * JVM wants to know launcher type, so tell it.
 */
#ifdef GAMMA
void SetJavaLauncherProp() {
  AddOption("-Dsun.java.launcher=" LAUNCHER_TYPE, NULL);
}
#endif

/*
 * Prints the version information from the java.version and other properties.
 */
static void
PrintJavaVersion(JNIEnv *env)
{
    jclass ver;
    jmethodID print;

    NULL_CHECK(ver = FindBootStrapClass(env, "sun/misc/Version"));
    NULL_CHECK(print = (*env)->GetStaticMethodID(env, ver, "print", "()V"));

    (*env)->CallStaticVoidMethod(env, ver, print);
}

/*
 * Prints default usage message.
 */
static void
PrintUsage(void)
{
    int i;

    fprintf(stdout,
        "Usage: %s [-options] class [args...]\n"
        "           (to execute a class)\n"
        "   or  %s [-options] -jar jarfile [args...]\n"
        "           (to execute a jar file)\n"
        "\n"
        "where options include:\n",
        progname,
        progname);

#ifndef GAMMA
    PrintMachineDependentOptions();

    if ((knownVMs[0].flag == VM_KNOWN) ||
        (knownVMs[0].flag == VM_IF_SERVER_CLASS)) {
      fprintf(stdout, "    %s\t  to select the \"%s\" VM\n",
              knownVMs[0].name, knownVMs[0].name+1);
    }
    for (i=1; i<knownVMsCount; i++) {
        if (knownVMs[i].flag == VM_KNOWN)
            fprintf(stdout, "    %s\t  to select the \"%s\" VM\n",
                    knownVMs[i].name, knownVMs[i].name+1);
    }
    for (i=1; i<knownVMsCount; i++) {
        if (knownVMs[i].flag == VM_ALIASED_TO)
            fprintf(stdout, "    %s\t  is a synonym for "
                    "the \"%s\" VM  [deprecated]\n",
                    knownVMs[i].name, knownVMs[i].alias+1);
    }

    /* The first known VM is the default */
    {
      const char* defaultVM   = knownVMs[0].name+1;
      const char* punctuation = ".";
      const char* reason      = "";
      if ((knownVMs[0].flag == VM_IF_SERVER_CLASS) &&
          (ServerClassMachine() == JNI_TRUE)) {
        defaultVM = knownVMs[0].server_class+1;
        punctuation = ", ";
        reason = "because you are running on a server-class machine.\n";
      }
      fprintf(stdout, "                  The default VM is %s%s\n",
              defaultVM, punctuation);
      fprintf(stdout, "                  %s\n",
              reason);
    }
#endif /* ifndef GAMMA */

    fprintf(stdout,
"    -cp <class search path of directories and zip/jar files>\n"
"    -classpath <class search path of directories and zip/jar files>\n"
"                  A %c separated list of directories, JAR archives,\n"
"                  and ZIP archives to search for class files.\n"
"    -D<name>=<value>\n"
"                  set a system property\n"
"    -verbose[:class|gc|jni]\n"
"                  enable verbose output\n"
"    -version      print product version and exit\n"
"    -version:<value>\n"
"                  require the specified version to run\n"
"    -showversion  print product version and continue\n"
"    -jre-restrict-search | -jre-no-restrict-search\n"
"                  include/exclude user private JREs in the version search\n"
"    -? -help      print this help message\n"
"    -X            print help on non-standard options\n"
"    -ea[:<packagename>...|:<classname>]\n"
"    -enableassertions[:<packagename>...|:<classname>]\n"
"                  enable assertions\n"
"    -da[:<packagename>...|:<classname>]\n"
"    -disableassertions[:<packagename>...|:<classname>]\n"
"                  disable assertions\n"
"    -esa | -enablesystemassertions\n"
"                  enable system assertions\n"
"    -dsa | -disablesystemassertions\n"
"                  disable system assertions\n"
"    -agentlib:<libname>[=<options>]\n"
"                  load native agent library <libname>, e.g. -agentlib:hprof\n"
"                    see also, -agentlib:jdwp=help and -agentlib:hprof=help\n"
"    -agentpath:<pathname>[=<options>]\n"
"                  load native agent library by full pathname\n"
"    -javaagent:<jarpath>[=<options>]\n"
"                  load Java programming language agent, see java.lang.instrument\n"

            ,PATH_SEPARATOR);
}

/*
 * Print usage message for -X options.
 */
static jint
PrintXUsage(void)
{
    char path[MAXPATHLEN];
    char buf[128];
    size_t n;
    FILE *fp;

    GetXUsagePath(path, sizeof(path));
    fp = fopen(path, "r");
    if (fp == 0) {
        fprintf(stderr, "Can't open %s\n", path);
        return 1;
    }
    while ((n = fread(buf, 1, sizeof(buf), fp)) != 0) {
        fwrite(buf, 1, n, stdout);
    }
    fclose(fp);
    return 0;
}

#ifndef GAMMA

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
 * be installed in different subdirectories of a single JRE installation,
 * for space-savings and convenience in testing.
 * The intent is explicitly not to provide a full aliasing or predicate
 * mechanism.
 */
jint
ReadKnownVMs(const char *jrepath, char * arch, jboolean speculative)
{
    FILE *jvmCfg;
    char jvmCfgName[MAXPATHLEN+20];
    char line[MAXPATHLEN+20];
    int cnt = 0;
    int lineno = 0;
    jlong start, end;
    int vmType;
    char *tmpPtr;
    char *altVMName;
    char *serverClassVMName;
    static char *whiteSpace = " \t";
    if (_launcher_debug) {
        start = CounterGet();
    }

    strcpy(jvmCfgName, jrepath);
    strcat(jvmCfgName, FILESEP "lib" FILESEP);
    strcat(jvmCfgName, arch);
    strcat(jvmCfgName, FILESEP "jvm.cfg");

    jvmCfg = fopen(jvmCfgName, "r");
    if (jvmCfg == NULL) {
      if (!speculative) {
        ReportErrorMessage2("Error: could not open `%s'", jvmCfgName,
                            JNI_TRUE);
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
            fprintf(stderr, "Warning: no leading - on line %d of `%s'\n",
                    lineno, jvmCfgName);
        }
        if (cnt >= knownVMsLimit) {
            GrowKnownVMs(cnt);
        }
        line[strlen(line)-1] = '\0'; /* remove trailing newline */
        tmpPtr = line + strcspn(line, whiteSpace);
        if (*tmpPtr == 0) {
            fprintf(stderr, "Warning: missing VM type on line %d of `%s'\n",
                    lineno, jvmCfgName);
        } else {
            /* Null-terminate this string for strdup below */
            *tmpPtr++ = 0;
            tmpPtr += strspn(tmpPtr, whiteSpace);
            if (*tmpPtr == 0) {
                fprintf(stderr, "Warning: missing VM type on line %d of `%s'\n",
                        lineno, jvmCfgName);
            } else {
                if (!strncmp(tmpPtr, "KNOWN", strlen("KNOWN"))) {
                    vmType = VM_KNOWN;
                } else if (!strncmp(tmpPtr, "ALIASED_TO", strlen("ALIASED_TO"))) {
                    tmpPtr += strcspn(tmpPtr, whiteSpace);
                    if (*tmpPtr != 0) {
                        tmpPtr += strspn(tmpPtr, whiteSpace);
                    }
                    if (*tmpPtr == 0) {
                        fprintf(stderr, "Warning: missing VM alias on line %d of `%s'\n",
                                lineno, jvmCfgName);
                    } else {
                        /* Null terminate altVMName */
                        altVMName = tmpPtr;
                        tmpPtr += strcspn(tmpPtr, whiteSpace);
                        *tmpPtr = 0;
                        vmType = VM_ALIASED_TO;
                    }
                } else if (!strncmp(tmpPtr, "WARN", strlen("WARN"))) {
                    vmType = VM_WARN;
                } else if (!strncmp(tmpPtr, "IGNORE", strlen("IGNORE"))) {
                    vmType = VM_IGNORE;
                } else if (!strncmp(tmpPtr, "ERROR", strlen("ERROR"))) {
                    vmType = VM_ERROR;
                } else if (!strncmp(tmpPtr,
                                    "IF_SERVER_CLASS",
                                    strlen("IF_SERVER_CLASS"))) {
                    tmpPtr += strcspn(tmpPtr, whiteSpace);
                    if (*tmpPtr != 0) {
                        tmpPtr += strspn(tmpPtr, whiteSpace);
                    }
                    if (*tmpPtr == 0) {
                        fprintf(stderr, "Warning: missing server class VM on line %d of `%s'\n",
                                lineno, jvmCfgName);
                    } else {
                        /* Null terminate server class VM name */
                        serverClassVMName = tmpPtr;
                        tmpPtr += strcspn(tmpPtr, whiteSpace);
                        *tmpPtr = 0;
                        vmType = VM_IF_SERVER_CLASS;
                    }
                } else {
                    fprintf(stderr, "Warning: unknown VM type on line %d of `%s'\n",
                            lineno, &jvmCfgName[0]);
                    vmType = VM_KNOWN;
                }
            }
        }

        if (_launcher_debug)
            printf("jvm.cfg[%d] = ->%s<-\n", cnt, line);
        if (vmType != VM_UNKNOWN) {
            knownVMs[cnt].name = strdup(line);
            knownVMs[cnt].flag = vmType;
            switch (vmType) {
            default:
                break;
            case VM_ALIASED_TO:
                knownVMs[cnt].alias = strdup(altVMName);
                if (_launcher_debug) {
                    printf("    name: %s  vmType: %s  alias: %s\n",
                           knownVMs[cnt].name, "VM_ALIASED_TO", knownVMs[cnt].alias);
                }
                break;
            case VM_IF_SERVER_CLASS:
                knownVMs[cnt].server_class = strdup(serverClassVMName);
                if (_launcher_debug) {
                    printf("    name: %s  vmType: %s  server_class: %s\n",
                           knownVMs[cnt].name, "VM_IF_SERVER_CLASS", knownVMs[cnt].server_class);
                }
                break;
            }
            cnt++;
        }
    }
    fclose(jvmCfg);
    knownVMsCount = cnt;

    if (_launcher_debug) {
        end   = CounterGet();
        printf("%ld micro seconds to parse jvm.cfg\n",
               (long)(jint)Counter2Micros(end-start));
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
    newKnownVMs = (struct vmdesc*) MemAlloc(newMax * sizeof(struct vmdesc));
    if (knownVMs != NULL) {
        memcpy(newKnownVMs, knownVMs, knownVMsLimit * sizeof(struct vmdesc));
    }
    free(knownVMs);
    knownVMs = newKnownVMs;
    knownVMsLimit = newMax;
}


/* Returns index of VM or -1 if not found */
static int
KnownVMIndex(const char* name)
{
    int i;
    if (strncmp(name, "-J", 2) == 0) name += 2;
    for (i = 0; i < knownVMsCount; i++) {
        if (!strcmp(name, knownVMs[i].name)) {
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
        free(knownVMs[i].name);
        knownVMs[i].name = NULL;
    }
    free(knownVMs);
}

#endif /* ifndef GAMMA */
