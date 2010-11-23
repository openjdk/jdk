/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "java.h"
#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <inttypes.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <limits.h>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/types.h>

#ifndef GAMMA
#include "manifest_info.h"
#include "version_comp.h"
#endif

#define JVM_DLL "libjvm.so"
#define JAVA_DLL "libjava.so"

#ifndef GAMMA   /* launcher.make defines ARCH */

/*
 * If a processor / os combination has the ability to run binaries of
 * two data models and cohabitation of jre/jdk bits with both data
 * models is supported, then DUAL_MODE is defined.  When DUAL_MODE is
 * defined, the architecture names for the narrow and wide version of
 * the architecture are defined in BIG_ARCH and SMALL_ARCH.  Currently
 * only Solaris on sparc/sparcv9 and i586/amd64 is DUAL_MODE; linux
 * i586/amd64 could be defined as DUAL_MODE but that is not the
 * current policy.
 */

#ifdef _LP64

#  ifdef ia64
#    define ARCH "ia64"
#  elif defined(amd64)
#    define ARCH "amd64"
#  elif defined(__sparc)
#    define ARCH "sparcv9"
#  else
#    define ARCH "unknown" /* unknown 64-bit architecture */
#  endif

#else /* 32-bit data model */

#  ifdef i586
#    define ARCH "i386"
#  elif defined(__sparc)
#    define ARCH "sparc"
#  endif

#endif /* _LP64 */

#ifdef __sun
#  define DUAL_MODE
#  ifdef __sparc
#    define BIG_ARCH "sparcv9"
#    define SMALL_ARCH "sparc"
#  else
#    define BIG_ARCH "amd64"
#    define SMALL_ARCH "i386"
#  endif
#  include <sys/systeminfo.h>
#  include <sys/elf.h>
#  include <stdio.h>
#else
#  ifndef ARCH
#    include <sys/systeminfo.h>
#  endif
#endif

#endif /* ifndef GAMMA */

/* pointer to environment */
extern char **environ;

#ifndef GAMMA

/*
 *      A collection of useful strings. One should think of these as #define
 *      entries, but actual strings can be more efficient (with many compilers).
 */
#ifdef __linux__
static const char *system_dir   = "/usr/java";
static const char *user_dir     = "/java";
#else /* Solaris */
static const char *system_dir   = "/usr/jdk";
static const char *user_dir     = "/jdk";
#endif

#endif  /* ifndef GAMMA */

/*
 * Flowchart of launcher execs and options processing on unix
 *
 * The selection of the proper vm shared library to open depends on
 * several classes of command line options, including vm "flavor"
 * options (-client, -server) and the data model options, -d32  and
 * -d64, as well as a version specification which may have come from
 * the command line or from the manifest of an executable jar file.
 * The vm selection options are not passed to the running
 * virtual machine; they must be screened out by the launcher.
 *
 * The version specification (if any) is processed first by the
 * platform independent routine SelectVersion.  This may result in
 * the exec of the specified launcher version.
 *
 * Typically, the launcher execs at least once to ensure a suitable
 * LD_LIBRARY_PATH is in effect for the process.  The first exec
 * screens out all the data model options; leaving the choice of data
 * model implicit in the binary selected to run.  However, in case no
 * exec is done, the data model options are screened out before the vm
 * is invoked.
 *
 *  incoming argv ------------------------------
 *  |                                          |
 * \|/                                         |
 * CheckJVMType                                |
 * (removes -client, -server, etc.)            |
 *                                            \|/
 *                                            CreateExecutionEnvironment
 *                                            (removes -d32 and -d64,
 *                                             determines desired data model,
 *                                             sets up LD_LIBRARY_PATH,
 *                                             and exec's)
 *                                             |
 *  --------------------------------------------
 *  |
 * \|/
 * exec child 1 incoming argv -----------------
 *  |                                          |
 * \|/                                         |
 * CheckJVMType                                |
 * (removes -client, -server, etc.)            |
 *  |                                         \|/
 *  |                                          CreateExecutionEnvironment
 *  |                                          (verifies desired data model
 *  |                                           is running and acceptable
 *  |                                           LD_LIBRARY_PATH;
 *  |                                           no-op in child)
 *  |
 * \|/
 * TranslateDashJArgs...
 * (Prepare to pass args to vm)
 *  |
 *  |
 *  |
 * \|/
 * ParseArguments
 * (ignores -d32 and -d64,
 *  processes version options,
 *  creates argument list for vm,
 *  etc.)
 *
 */

static char *SetExecname(char **argv);
static char * GetExecname();
static jboolean GetJVMPath(const char *jrepath, const char *jvmtype,
                           char *jvmpath, jint jvmpathsize, char * arch);
static jboolean GetJREPath(char *path, jint pathsize, char * arch, jboolean speculative);

const char *
GetArch()
{
    static char *arch = NULL;
    static char buf[12];
    if (arch) {
        return arch;
    }

#ifdef ARCH
    strcpy(buf, ARCH);
#else
    sysinfo(SI_ARCHITECTURE, buf, sizeof(buf));
#endif
    arch = buf;
    return arch;
}

void
CreateExecutionEnvironment(int *_argcp,
                           char ***_argvp,
                           char jrepath[],
                           jint so_jrepath,
                           char jvmpath[],
                           jint so_jvmpath,
                           char **original_argv) {
  /*
   * First, determine if we are running the desired data model.  If we
   * are running the desired data model, all the error messages
   * associated with calling GetJREPath, ReadKnownVMs, etc. should be
   * output.  However, if we are not running the desired data model,
   * some of the errors should be suppressed since it is more
   * informative to issue an error message based on whether or not the
   * os/processor combination has dual mode capabilities.
   */

    char *execname = NULL;
    int original_argc = *_argcp;
    jboolean jvmpathExists;

    /* Compute the name of the executable */
    execname = SetExecname(*_argvp);

#ifndef GAMMA
    /* Set the LD_LIBRARY_PATH environment variable, check data model
       flags, and exec process, if needed */
    {
      char *arch        = (char *)GetArch(); /* like sparc or sparcv9 */
      char * jvmtype    = NULL;
      int argc          = *_argcp;
      char **argv       = original_argv;

      char *runpath     = NULL; /* existing effective LD_LIBRARY_PATH
                                   setting */

      int running       =       /* What data model is being ILP32 =>
                                   32 bit vm; LP64 => 64 bit vm */
#ifdef _LP64
        64;
#else
      32;
#endif

      int wanted        = running;      /* What data mode is being
                                           asked for? Current model is
                                           fine unless another model
                                           is asked for */

      char* new_runpath = NULL; /* desired new LD_LIBRARY_PATH string */
      char* newpath     = NULL; /* path on new LD_LIBRARY_PATH */
      char* lastslash   = NULL;

      char** newenvp    = NULL; /* current environment */

      char** newargv    = NULL;
      int    newargc    = 0;
#ifdef __sun
      char*  dmpath     = NULL;  /* data model specific LD_LIBRARY_PATH,
                                    Solaris only */
#endif

      /*
       * Starting in 1.5, all unix platforms accept the -d32 and -d64
       * options.  On platforms where only one data-model is supported
       * (e.g. ia-64 Linux), using the flag for the other data model is
       * an error and will terminate the program.
       */

      { /* open new scope to declare local variables */
        int i;

        newargv = (char **)MemAlloc((argc+1) * sizeof(*newargv));
        newargv[newargc++] = argv[0];

        /* scan for data model arguments and remove from argument list;
           last occurrence determines desired data model */
        for (i=1; i < argc; i++) {

          if (strcmp(argv[i], "-J-d64") == 0 || strcmp(argv[i], "-d64") == 0) {
            wanted = 64;
            continue;
          }
          if (strcmp(argv[i], "-J-d32") == 0 || strcmp(argv[i], "-d32") == 0) {
            wanted = 32;
            continue;
          }
          newargv[newargc++] = argv[i];

#ifdef JAVA_ARGS
          if (argv[i][0] != '-')
            continue;
#else
          if (strcmp(argv[i], "-classpath") == 0 || strcmp(argv[i], "-cp") == 0) {
            i++;
            if (i >= argc) break;
            newargv[newargc++] = argv[i];
            continue;
          }
          if (argv[i][0] != '-') { i++; break; }
#endif
        }

        /* copy rest of args [i .. argc) */
        while (i < argc) {
          newargv[newargc++] = argv[i++];
        }
        newargv[newargc] = NULL;

        /*
         * newargv has all proper arguments here
         */

        argc = newargc;
        argv = newargv;
      }

      /* If the data model is not changing, it is an error if the
         jvmpath does not exist */
      if (wanted == running) {
        /* Find out where the JRE is that we will be using. */
        if (!GetJREPath(jrepath, so_jrepath, arch, JNI_FALSE) ) {
          fprintf(stderr, "Error: could not find Java 2 Runtime Environment.\n");
          exit(2);
        }

        /* Find the specified JVM type */
        if (ReadKnownVMs(jrepath, arch, JNI_FALSE) < 1) {
          fprintf(stderr, "Error: no known VMs. (check for corrupt jvm.cfg file)\n");
          exit(1);
        }

        jvmpath[0] = '\0';
        jvmtype = CheckJvmType(_argcp, _argvp, JNI_FALSE);

        if (!GetJVMPath(jrepath, jvmtype, jvmpath, so_jvmpath, arch )) {
          fprintf(stderr, "Error: no `%s' JVM at `%s'.\n", jvmtype, jvmpath);
          exit(4);
        }
      } else {  /* do the same speculatively or exit */
#ifdef DUAL_MODE
        if (running != wanted) {
          /* Find out where the JRE is that we will be using. */
          if (!GetJREPath(jrepath, so_jrepath, ((wanted==64)?BIG_ARCH:SMALL_ARCH), JNI_TRUE)) {
            goto EndDataModelSpeculate;
          }

          /*
           * Read in jvm.cfg for target data model and process vm
           * selection options.
           */
          if (ReadKnownVMs(jrepath, ((wanted==64)?BIG_ARCH:SMALL_ARCH), JNI_TRUE) < 1) {
            goto EndDataModelSpeculate;
          }
          jvmpath[0] = '\0';
          jvmtype = CheckJvmType(_argcp, _argvp, JNI_TRUE);
          /* exec child can do error checking on the existence of the path */
          jvmpathExists = GetJVMPath(jrepath, jvmtype, jvmpath, so_jvmpath,
                                     ((wanted==64)?BIG_ARCH:SMALL_ARCH));

        }
      EndDataModelSpeculate: /* give up and let other code report error message */
        ;
#else
        fprintf(stderr, "Running a %d-bit JVM is not supported on this platform.\n", wanted);
        exit(1);
#endif
      }

      /*
       * We will set the LD_LIBRARY_PATH as follows:
       *
       *     o          $JVMPATH (directory portion only)
       *     o          $JRE/lib/$ARCH
       *     o          $JRE/../lib/$ARCH
       *
       * followed by the user's previous effective LD_LIBRARY_PATH, if
       * any.
       */

#ifdef __sun
      /*
       * Starting in Solaris 7, ld.so.1 supports three LD_LIBRARY_PATH
       * variables:
       *
       * 1. LD_LIBRARY_PATH -- used for 32 and 64 bit searches if
       * data-model specific variables are not set.
       *
       * 2. LD_LIBRARY_PATH_64 -- overrides and replaces LD_LIBRARY_PATH
       * for 64-bit binaries.
       *
       * 3. LD_LIBRARY_PATH_32 -- overrides and replaces LD_LIBRARY_PATH
       * for 32-bit binaries.
       *
       * The vm uses LD_LIBRARY_PATH to set the java.library.path system
       * property.  To shield the vm from the complication of multiple
       * LD_LIBRARY_PATH variables, if the appropriate data model
       * specific variable is set, we will act as if LD_LIBRARY_PATH had
       * the value of the data model specific variant and the data model
       * specific variant will be unset.  Note that the variable for the
       * *wanted* data model must be used (if it is set), not simply the
       * current running data model.
       */

      switch(wanted) {
      case 0:
        if(running == 32) {
          dmpath = getenv("LD_LIBRARY_PATH_32");
          wanted = 32;
        }
        else {
          dmpath = getenv("LD_LIBRARY_PATH_64");
          wanted = 64;
        }
        break;

      case 32:
        dmpath = getenv("LD_LIBRARY_PATH_32");
        break;

      case 64:
        dmpath = getenv("LD_LIBRARY_PATH_64");
        break;

      default:
        fprintf(stderr, "Improper value at line %d.", __LINE__);
        exit(1); /* unknown value in wanted */
        break;
      }

      /*
       * If dmpath is NULL, the relevant data model specific variable is
       * not set and normal LD_LIBRARY_PATH should be used.
       */
      if( dmpath == NULL) {
        runpath = getenv("LD_LIBRARY_PATH");
      }
      else {
        runpath = dmpath;
      }
#else
      /*
       * If not on Solaris, assume only a single LD_LIBRARY_PATH
       * variable.
       */
      runpath = getenv("LD_LIBRARY_PATH");
#endif /* __sun */

#ifdef __linux
      /*
       * On linux, if a binary is running as sgid or suid, glibc sets
       * LD_LIBRARY_PATH to the empty string for security purposes.  (In
       * contrast, on Solaris the LD_LIBRARY_PATH variable for a
       * privileged binary does not lose its settings; but the dynamic
       * linker does apply more scrutiny to the path.) The launcher uses
       * the value of LD_LIBRARY_PATH to prevent an exec loop.
       * Therefore, if we are running sgid or suid, this function's
       * setting of LD_LIBRARY_PATH will be ineffective and we should
       * return from the function now.  Getting the right libraries to
       * be found must be handled through other mechanisms.
       */
      if((getgid() != getegid()) || (getuid() != geteuid()) ) {
        return;
      }
#endif

      /* runpath contains current effective LD_LIBRARY_PATH setting */

      jvmpath = strdup(jvmpath);
      new_runpath = MemAlloc( ((runpath!=NULL)?strlen(runpath):0) +
                              2*strlen(jrepath) + 2*strlen(arch) +
                              strlen(jvmpath) + 52);
      newpath = new_runpath + strlen("LD_LIBRARY_PATH=");


      /*
       * Create desired LD_LIBRARY_PATH value for target data model.
       */
      {
        /* remove the name of the .so from the JVM path */
        lastslash = strrchr(jvmpath, '/');
        if (lastslash)
          *lastslash = '\0';


        /* jvmpath, ((running != wanted)?((wanted==64)?"/"BIG_ARCH:"/.."):""), */

        sprintf(new_runpath, "LD_LIBRARY_PATH="
                "%s:"
                "%s/lib/%s:"
                "%s/../lib/%s",
                jvmpath,
#ifdef DUAL_MODE
                jrepath, ((wanted==64)?BIG_ARCH:SMALL_ARCH),
                jrepath, ((wanted==64)?BIG_ARCH:SMALL_ARCH)
#else
                jrepath, arch,
                jrepath, arch
#endif
                );


        /*
         * Check to make sure that the prefix of the current path is the
         * desired environment variable setting.
         */
        if (runpath != NULL &&
            strncmp(newpath, runpath, strlen(newpath))==0 &&
            (runpath[strlen(newpath)] == 0 || runpath[strlen(newpath)] == ':') &&
            (running == wanted) /* data model does not have to be changed */
#ifdef __sun
            && (dmpath == NULL)    /* data model specific variables not set  */
#endif
            ) {

          return;

        }
      }

      /*
       * Place the desired environment setting onto the prefix of
       * LD_LIBRARY_PATH.  Note that this prevents any possible infinite
       * loop of execv() because we test for the prefix, above.
       */
      if (runpath != 0) {
        strcat(new_runpath, ":");
        strcat(new_runpath, runpath);
      }

      if( putenv(new_runpath) != 0) {
        exit(1); /* problem allocating memory; LD_LIBRARY_PATH not set
                    properly */
      }

      /*
       * Unix systems document that they look at LD_LIBRARY_PATH only
       * once at startup, so we have to re-exec the current executable
       * to get the changed environment variable to have an effect.
       */

#ifdef __sun
      /*
       * If dmpath is not NULL, remove the data model specific string
       * in the environment for the exec'ed child.
       */

      if( dmpath != NULL)
        (void)UnsetEnv((wanted==32)?"LD_LIBRARY_PATH_32":"LD_LIBRARY_PATH_64");
#endif

      newenvp = environ;

      {
        char *newexec = execname;
#ifdef DUAL_MODE
        /*
         * If the data model is being changed, the path to the
         * executable must be updated accordingly; the executable name
         * and directory the executable resides in are separate.  In the
         * case of 32 => 64, the new bits are assumed to reside in, e.g.
         * "olddir/BIGARCH/execname"; in the case of 64 => 32,
         * the bits are assumed to be in "olddir/../execname".  For example,
         *
         * olddir/sparcv9/execname
         * olddir/amd64/execname
         *
         * for Solaris SPARC and Linux amd64, respectively.
         */

        if (running != wanted) {
          char *oldexec = strcpy(MemAlloc(strlen(execname) + 1), execname);
          char *olddir = oldexec;
          char *oldbase = strrchr(oldexec, '/');


          newexec = MemAlloc(strlen(execname) + 20);
          *oldbase++ = 0;
          sprintf(newexec, "%s/%s/%s", olddir,
                  ((wanted==64) ? BIG_ARCH : ".."), oldbase);
          argv[0] = newexec;
        }
#endif

        execve(newexec, argv, newenvp);
        perror("execve()");

        fprintf(stderr, "Error trying to exec %s.\n", newexec);
        fprintf(stderr, "Check if file exists and permissions are set correctly.\n");

#ifdef DUAL_MODE
        if (running != wanted) {
          fprintf(stderr, "Failed to start a %d-bit JVM process from a %d-bit JVM.\n",
                  wanted, running);
#  ifdef __sun

#    ifdef __sparc
          fprintf(stderr, "Verify all necessary J2SE components have been installed.\n" );
          fprintf(stderr,
                  "(Solaris SPARC 64-bit components must be installed after 32-bit components.)\n" );
#    else
          fprintf(stderr, "Either 64-bit processes are not supported by this platform\n");
          fprintf(stderr, "or the 64-bit components have not been installed.\n");
#    endif
        }
#  endif
#endif

      }

      exit(1);
    }

#else  /* ifndef GAMMA */

  /* gamma launcher is simpler in that it doesn't handle VM flavors, data  */
  /* model, LD_LIBRARY_PATH, etc. Assuming everything is set-up correctly  */
  /* all we need to do here is to return correct path names. See also      */
  /* GetJVMPath() and GetApplicationHome().                                */

  { char *arch = (char *)GetArch(); /* like sparc or sparcv9 */
    char *p;

    if (!GetJREPath(jrepath, so_jrepath, arch, JNI_FALSE) ) {
      fprintf(stderr, "Error: could not find Java 2 Runtime Environment.\n");
      exit(2);
    }

    if (!GetJVMPath(jrepath, NULL, jvmpath, so_jvmpath, arch )) {
      fprintf(stderr, "Error: no JVM at `%s'.\n", jvmpath);
      exit(4);
    }
  }

#endif  /* ifndef GAMMA */
}


/*
 * On Solaris VM choosing is done by the launcher (java.c).
 */
static jboolean
GetJVMPath(const char *jrepath, const char *jvmtype,
           char *jvmpath, jint jvmpathsize, char * arch)
{
    struct stat s;

#ifndef GAMMA
    if (strchr(jvmtype, '/')) {
        sprintf(jvmpath, "%s/" JVM_DLL, jvmtype);
    } else {
        sprintf(jvmpath, "%s/lib/%s/%s/" JVM_DLL, jrepath, arch, jvmtype);
    }
#else
    /* For gamma launcher, JVM is either built-in or in the same directory. */
    /* Either way we return "<exe_path>/libjvm.so" where <exe_path> is the  */
    /* directory where gamma launcher is located.                           */

    char *p;

    snprintf(jvmpath, jvmpathsize, "%s", GetExecname());
    p = strrchr(jvmpath, '/');
    if (p) {
       /* replace executable name with libjvm.so */
       snprintf(p + 1, jvmpathsize - (p + 1 - jvmpath), "%s", JVM_DLL);
    } else {
       /* this case shouldn't happen */
       snprintf(jvmpath, jvmpathsize, "%s", JVM_DLL);
    }
#endif

    if (_launcher_debug)
      printf("Does `%s' exist ... ", jvmpath);

    if (stat(jvmpath, &s) == 0) {
        if (_launcher_debug)
          printf("yes.\n");
        return JNI_TRUE;
    } else {
        if (_launcher_debug)
          printf("no.\n");
        return JNI_FALSE;
    }
}

/*
 * Find path to JRE based on .exe's location or registry settings.
 */
static jboolean
GetJREPath(char *path, jint pathsize, char * arch, jboolean speculative)
{
    char libjava[MAXPATHLEN];

    if (GetApplicationHome(path, pathsize)) {
        /* Is JRE co-located with the application? */
        sprintf(libjava, "%s/lib/%s/" JAVA_DLL, path, arch);
        if (access(libjava, F_OK) == 0) {
            goto found;
        }

        /* Does the app ship a private JRE in <apphome>/jre directory? */
        sprintf(libjava, "%s/jre/lib/%s/" JAVA_DLL, path, arch);
        if (access(libjava, F_OK) == 0) {
            strcat(path, "/jre");
            goto found;
        }
    }

    if (!speculative)
      fprintf(stderr, "Error: could not find " JAVA_DLL "\n");
    return JNI_FALSE;

 found:
    if (_launcher_debug)
      printf("JRE path is %s\n", path);
    return JNI_TRUE;
}

jboolean
LoadJavaVM(const char *jvmpath, InvocationFunctions *ifn)
{
#ifdef GAMMA
    /* JVM is directly linked with gamma launcher; no dlopen() */
    ifn->CreateJavaVM = JNI_CreateJavaVM;
    ifn->GetDefaultJavaVMInitArgs = JNI_GetDefaultJavaVMInitArgs;
    return JNI_TRUE;
#else
    Dl_info dlinfo;
    void *libjvm;

    if (_launcher_debug) {
        printf("JVM path is %s\n", jvmpath);
    }

    libjvm = dlopen(jvmpath, RTLD_NOW + RTLD_GLOBAL);
    if (libjvm == NULL) {
#if defined(__sparc) && !defined(_LP64) /* i.e. 32-bit sparc */
      FILE * fp;
      Elf32_Ehdr elf_head;
      int count;
      int location;

      fp = fopen(jvmpath, "r");
      if(fp == NULL)
        goto error;

      /* read in elf header */
      count = fread((void*)(&elf_head), sizeof(Elf32_Ehdr), 1, fp);
      fclose(fp);
      if(count < 1)
        goto error;

      /*
       * Check for running a server vm (compiled with -xarch=v8plus)
       * on a stock v8 processor.  In this case, the machine type in
       * the elf header would not be included the architecture list
       * provided by the isalist command, which is turn is gotten from
       * sysinfo.  This case cannot occur on 64-bit hardware and thus
       * does not have to be checked for in binaries with an LP64 data
       * model.
       */
      if(elf_head.e_machine == EM_SPARC32PLUS) {
        char buf[257];  /* recommended buffer size from sysinfo man
                           page */
        long length;
        char* location;

        length = sysinfo(SI_ISALIST, buf, 257);
        if(length > 0) {
          location = strstr(buf, "sparcv8plus ");
          if(location == NULL) {
            fprintf(stderr, "SPARC V8 processor detected; Server compiler requires V9 or better.\n");
            fprintf(stderr, "Use Client compiler on V8 processors.\n");
            fprintf(stderr, "Could not create the Java virtual machine.\n");
            return JNI_FALSE;
          }
        }
      }
#endif
      fprintf(stderr, "dl failure on line %d", __LINE__);
      goto error;
    }

    ifn->CreateJavaVM = (CreateJavaVM_t)
      dlsym(libjvm, "JNI_CreateJavaVM");
    if (ifn->CreateJavaVM == NULL)
        goto error;

    ifn->GetDefaultJavaVMInitArgs = (GetDefaultJavaVMInitArgs_t)
        dlsym(libjvm, "JNI_GetDefaultJavaVMInitArgs");
    if (ifn->GetDefaultJavaVMInitArgs == NULL)
      goto error;

    return JNI_TRUE;

error:
    fprintf(stderr, "Error: failed %s, because %s\n", jvmpath, dlerror());
    return JNI_FALSE;
#endif /* GAMMA */
}

/*
 * Get the path to the file that has the usage message for -X options.
 */
void
GetXUsagePath(char *buf, jint bufsize)
{
    static const char Xusage_txt[] = "/Xusage.txt";
    Dl_info dlinfo;

    /* we use RTLD_NOW because of problems with ld.so.1 and green threads */
    dladdr(dlsym(dlopen(JVM_DLL, RTLD_NOW), "JNI_CreateJavaVM"), &dlinfo);
    strncpy(buf, (char *)dlinfo.dli_fname, bufsize - sizeof(Xusage_txt));

    buf[bufsize-1] = '\0';
    strcpy(strrchr(buf, '/'), Xusage_txt);
}

/*
 * If app is "/foo/bin/javac", or "/foo/bin/sparcv9/javac" then put
 * "/foo" into buf.
 */
jboolean
GetApplicationHome(char *buf, jint bufsize)
{
#ifdef __linux__
    char *execname = GetExecname();
    if (execname) {
        strncpy(buf, execname, bufsize-1);
        buf[bufsize-1] = '\0';
    } else {
        return JNI_FALSE;
    }
#else
    Dl_info dlinfo;

    dladdr((void *)GetApplicationHome, &dlinfo);
    if (realpath(dlinfo.dli_fname, buf) == NULL) {
        fprintf(stderr, "Error: realpath(`%s') failed.\n", dlinfo.dli_fname);
        return JNI_FALSE;
    }
#endif

#ifdef GAMMA
    {
      /* gamma launcher uses JAVA_HOME environment variable to find JDK/JRE */
      char* java_home_var = getenv("JAVA_HOME");
      if (java_home_var == NULL) {
        printf("JAVA_HOME must point to a valid JDK/JRE to run gamma\n");
        return JNI_FALSE;
      }
      snprintf(buf, bufsize, "%s", java_home_var);
    }
#else
    if (strrchr(buf, '/') == 0) {
        buf[0] = '\0';
        return JNI_FALSE;
    }
    *(strrchr(buf, '/')) = '\0';        /* executable file      */
    if (strlen(buf) < 4 || strrchr(buf, '/') == 0) {
        buf[0] = '\0';
        return JNI_FALSE;
    }
    if (strcmp("/bin", buf + strlen(buf) - 4) != 0)
        *(strrchr(buf, '/')) = '\0';    /* sparcv9 or amd64     */
    if (strlen(buf) < 4 || strcmp("/bin", buf + strlen(buf) - 4) != 0) {
        buf[0] = '\0';
        return JNI_FALSE;
    }
    *(strrchr(buf, '/')) = '\0';        /* bin                  */
#endif /* GAMMA */

    return JNI_TRUE;
}


/*
 * Return true if the named program exists
 */
static int
ProgramExists(char *name)
{
    struct stat sb;
    if (stat(name, &sb) != 0) return 0;
    if (S_ISDIR(sb.st_mode)) return 0;
    return (sb.st_mode & S_IEXEC) != 0;
}


/*
 * Find a command in a directory, returning the path.
 */
static char *
Resolve(char *indir, char *cmd)
{
    char name[PATH_MAX + 2], *real;

    if ((strlen(indir) + strlen(cmd) + 1)  > PATH_MAX) return 0;
    sprintf(name, "%s%c%s", indir, FILE_SEPARATOR, cmd);
    if (!ProgramExists(name)) return 0;
    real = MemAlloc(PATH_MAX + 2);
    if (!realpath(name, real))
        strcpy(real, name);
    return real;
}


/*
 * Find a path for the executable
 */
static char *
FindExecName(char *program)
{
    char cwdbuf[PATH_MAX+2];
    char *path;
    char *tmp_path;
    char *f;
    char *result = NULL;

    /* absolute path? */
    if (*program == FILE_SEPARATOR ||
        (FILE_SEPARATOR=='\\' && strrchr(program, ':')))
        return Resolve("", program+1);

    /* relative path? */
    if (strrchr(program, FILE_SEPARATOR) != 0) {
        char buf[PATH_MAX+2];
        return Resolve(getcwd(cwdbuf, sizeof(cwdbuf)), program);
    }

    /* from search path? */
    path = getenv("PATH");
    if (!path || !*path) path = ".";
    tmp_path = MemAlloc(strlen(path) + 2);
    strcpy(tmp_path, path);

    for (f=tmp_path; *f && result==0; ) {
        char *s = f;
        while (*f && (*f != PATH_SEPARATOR)) ++f;
        if (*f) *f++ = 0;
        if (*s == FILE_SEPARATOR)
            result = Resolve(s, program);
        else {
            /* relative path element */
            char dir[2*PATH_MAX];
            sprintf(dir, "%s%c%s", getcwd(cwdbuf, sizeof(cwdbuf)),
                    FILE_SEPARATOR, s);
            result = Resolve(dir, program);
        }
        if (result != 0) break;
    }

    free(tmp_path);
    return result;
}


/* Store the name of the executable once computed */
static char *execname = NULL;

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
static char *
SetExecname(char **argv)
{
    char* exec_path = NULL;

    if (execname != NULL)       /* Already determined */
        return (execname);

#if defined(__sun)
    {
        Dl_info dlinfo;
        if (dladdr((void*)&SetExecname, &dlinfo)) {
            char *resolved = (char*)MemAlloc(PATH_MAX+1);
            if (resolved != NULL) {
                exec_path = realpath(dlinfo.dli_fname, resolved);
                if (exec_path == NULL) {
                    free(resolved);
                }
            }
        }
    }
#elif defined(__linux__)
    {
        const char* self = "/proc/self/exe";
        char buf[PATH_MAX+1];
        int len = readlink(self, buf, PATH_MAX);
        if (len >= 0) {
            buf[len] = '\0';            /* readlink doesn't nul terminate */
            exec_path = strdup(buf);
        }
    }
#else /* !__sun && !__linux */
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

/*
 * Return the name of the executable.  Used in java_md.c to find the JRE area.
 */
static char *
GetExecname() {
  return execname;
}

void ReportErrorMessage(char * message, jboolean always) {
  if (always) {
    fprintf(stderr, "%s\n", message);
  }
}

void ReportErrorMessage2(char * format, char * string, jboolean always) {
  if (always) {
    fprintf(stderr, format, string);
    fprintf(stderr, "\n");
  }
}

void  ReportExceptionDescription(JNIEnv * env) {
  (*env)->ExceptionDescribe(env);
}

/*
 * Return JNI_TRUE for an option string that has no effect but should
 * _not_ be passed on to the vm; return JNI_FALSE otherwise.  On
 * Solaris SPARC, this screening needs to be done if:
 * 1) LD_LIBRARY_PATH does _not_ need to be reset and
 * 2) -d32 or -d64 is passed to a binary with a matching data model
 *    (the exec in SetLibraryPath removes -d<n> options and points the
 *    exec to the proper binary).  When this exec is not done, these options
 *    would end up getting passed onto the vm.
 */
jboolean RemovableMachineDependentOption(char * option) {
  /*
   * Unconditionally remove both -d32 and -d64 options since only
   * the last such options has an effect; e.g.
   * java -d32 -d64 -d32 -version
   * is equivalent to
   * java -d32 -version
   */

  if( (strcmp(option, "-d32")  == 0 ) ||
      (strcmp(option, "-d64")  == 0 ))
    return JNI_TRUE;
  else
    return JNI_FALSE;
}

void PrintMachineDependentOptions() {
      fprintf(stdout,
        "    -d32          use a 32-bit data model if available\n"
        "\n"
        "    -d64          use a 64-bit data model if available\n");
      return;
}

#ifndef GAMMA  /* gamma launcher does not have ergonomics */

/*
 * The following methods (down to ServerClassMachine()) answer
 * the question about whether a machine is a "server-class"
 * machine.  A server-class machine is loosely defined as one
 * with 2 or more processors and 2 gigabytes or more physical
 * memory.  The definition of a processor is a physical package,
 * not a hyperthreaded chip masquerading as a multi-processor.
 * The definition of memory is also somewhat fuzzy, since x86
 * machines seem not to report all the memory in their DIMMs, we
 * think because of memory mapping of graphics cards, etc.
 *
 * This code is somewhat more confused with #ifdef's than we'd
 * like because this file is used by both Solaris and Linux
 * platforms, and so needs to be parameterized for SPARC and
 * i586 hardware.  The other Linux platforms (amd64 and ia64)
 * don't even ask this question, because they only come with
 * server JVMs.  */

# define KB (1024UL)
# define MB (1024UL * KB)
# define GB (1024UL * MB)

/* Compute physical memory by asking the OS */
uint64_t
physical_memory(void) {
  const uint64_t pages     = (uint64_t) sysconf(_SC_PHYS_PAGES);
  const uint64_t page_size = (uint64_t) sysconf(_SC_PAGESIZE);
  const uint64_t result    = pages * page_size;
# define UINT64_FORMAT "%" PRIu64

  if (_launcher_debug) {
    printf("pages: " UINT64_FORMAT
           "  page_size: " UINT64_FORMAT
           "  physical memory: " UINT64_FORMAT " (%.3fGB)\n",
           pages, page_size, result, result / (double) GB);
  }
  return result;
}

#if defined(__sun) && defined(__sparc)

/* Methods for solaris-sparc: these are easy. */

/* Ask the OS how many processors there are. */
unsigned long
physical_processors(void) {
  const unsigned long sys_processors = sysconf(_SC_NPROCESSORS_CONF);

  if (_launcher_debug) {
    printf("sysconf(_SC_NPROCESSORS_CONF): %lu\n", sys_processors);
  }
  return sys_processors;
}

/* The solaris-sparc version of the "server-class" predicate. */
jboolean
solaris_sparc_ServerClassMachine(void) {
  jboolean            result            = JNI_FALSE;
  /* How big is a server class machine? */
  const unsigned long server_processors = 2UL;
  const uint64_t      server_memory     = 2UL * GB;
  const uint64_t      actual_memory     = physical_memory();

  /* Is this a server class machine? */
  if (actual_memory >= server_memory) {
    const unsigned long actual_processors = physical_processors();
    if (actual_processors >= server_processors) {
      result = JNI_TRUE;
    }
  }
  if (_launcher_debug) {
    printf("solaris_" ARCH "_ServerClassMachine: %s\n",
           (result == JNI_TRUE ? "JNI_TRUE" : "JNI_FALSE"));
  }
  return result;
}

#endif /* __sun && __sparc */

#if defined(__sun) && defined(i586)

/*
 * A utility method for asking the CPU about itself.
 * There's a corresponding version of linux-i586
 * because the compilers are different.
 */
void
get_cpuid(uint32_t arg,
          uint32_t* eaxp,
          uint32_t* ebxp,
          uint32_t* ecxp,
          uint32_t* edxp) {
#ifdef _LP64
  asm(
  /* rbx is a callee-saved register */
      " movq    %rbx, %r11  \n"
  /* rdx and rcx are 3rd and 4th argument registers */
      " movq    %rdx, %r10  \n"
      " movq    %rcx, %r9   \n"
      " movl    %edi, %eax  \n"
      " cpuid               \n"
      " movl    %eax, (%rsi)\n"
      " movl    %ebx, (%r10)\n"
      " movl    %ecx, (%r9) \n"
      " movl    %edx, (%r8) \n"
  /* Restore rbx */
      " movq    %r11, %rbx");
#else
  /* EBX is a callee-saved register */
  asm(" pushl   %ebx");
  /* Need ESI for storing through arguments */
  asm(" pushl   %esi");
  asm(" movl    8(%ebp), %eax   \n"
      " cpuid                   \n"
      " movl    12(%ebp), %esi  \n"
      " movl    %eax, (%esi)    \n"
      " movl    16(%ebp), %esi  \n"
      " movl    %ebx, (%esi)    \n"
      " movl    20(%ebp), %esi  \n"
      " movl    %ecx, (%esi)    \n"
      " movl    24(%ebp), %esi  \n"
      " movl    %edx, (%esi)      ");
  /* Restore ESI and EBX */
  asm(" popl    %esi");
  /* Restore EBX */
  asm(" popl    %ebx");
#endif
}

#endif /* __sun && i586 */

#if defined(__linux__) && defined(i586)

/*
 * A utility method for asking the CPU about itself.
 * There's a corresponding version of solaris-i586
 * because the compilers are different.
 */
void
get_cpuid(uint32_t arg,
          uint32_t* eaxp,
          uint32_t* ebxp,
          uint32_t* ecxp,
          uint32_t* edxp) {
#ifdef _LP64
  __asm__ volatile (/* Instructions */
                    "   movl    %4, %%eax  \n"
                    "   cpuid              \n"
                    "   movl    %%eax, (%0)\n"
                    "   movl    %%ebx, (%1)\n"
                    "   movl    %%ecx, (%2)\n"
                    "   movl    %%edx, (%3)\n"
                    : /* Outputs */
                    : /* Inputs */
                    "r" (eaxp),
                    "r" (ebxp),
                    "r" (ecxp),
                    "r" (edxp),
                    "r" (arg)
                    : /* Clobbers */
                    "%rax", "%rbx", "%rcx", "%rdx", "memory"
                    );
#else
  uint32_t value_of_eax = 0;
  uint32_t value_of_ebx = 0;
  uint32_t value_of_ecx = 0;
  uint32_t value_of_edx = 0;
  __asm__ volatile (/* Instructions */
                        /* ebx is callee-save, so push it */
                        /* even though it's in the clobbers section */
                    "   pushl   %%ebx      \n"
                    "   movl    %4, %%eax  \n"
                    "   cpuid              \n"
                    "   movl    %%eax, %0  \n"
                    "   movl    %%ebx, %1  \n"
                    "   movl    %%ecx, %2  \n"
                    "   movl    %%edx, %3  \n"
                        /* restore ebx */
                    "   popl    %%ebx      \n"

                    : /* Outputs */
                    "=m" (value_of_eax),
                    "=m" (value_of_ebx),
                    "=m" (value_of_ecx),
                    "=m" (value_of_edx)
                    : /* Inputs */
                    "m" (arg)
                    : /* Clobbers */
                    "%eax", "%ebx", "%ecx", "%edx"
                    );
  *eaxp = value_of_eax;
  *ebxp = value_of_ebx;
  *ecxp = value_of_ecx;
  *edxp = value_of_edx;
#endif
}

#endif /* __linux__ && i586 */

#ifdef i586
/*
 * Routines shared by solaris-i586 and linux-i586.
 */

enum HyperThreadingSupport_enum {
  hts_supported        =  1,
  hts_too_soon_to_tell =  0,
  hts_not_supported    = -1,
  hts_not_pentium4     = -2,
  hts_not_intel        = -3
};
typedef enum HyperThreadingSupport_enum HyperThreadingSupport;

/* Determine if hyperthreading is supported */
HyperThreadingSupport
hyperthreading_support(void) {
  HyperThreadingSupport result = hts_too_soon_to_tell;
  /* Bits 11 through 8 is family processor id */
# define FAMILY_ID_SHIFT 8
# define FAMILY_ID_MASK 0xf
  /* Bits 23 through 20 is extended family processor id */
# define EXT_FAMILY_ID_SHIFT 20
# define EXT_FAMILY_ID_MASK 0xf
  /* Pentium 4 family processor id */
# define PENTIUM4_FAMILY_ID 0xf
  /* Bit 28 indicates Hyper-Threading Technology support */
# define HT_BIT_SHIFT 28
# define HT_BIT_MASK 1
  uint32_t vendor_id[3] = { 0U, 0U, 0U };
  uint32_t value_of_eax = 0U;
  uint32_t value_of_edx = 0U;
  uint32_t dummy        = 0U;

  /* Yes, this is supposed to be [0], [2], [1] */
  get_cpuid(0, &dummy, &vendor_id[0], &vendor_id[2], &vendor_id[1]);
  if (_launcher_debug) {
    printf("vendor: %c %c %c %c %c %c %c %c %c %c %c %c \n",
           ((vendor_id[0] >>  0) & 0xff),
           ((vendor_id[0] >>  8) & 0xff),
           ((vendor_id[0] >> 16) & 0xff),
           ((vendor_id[0] >> 24) & 0xff),
           ((vendor_id[1] >>  0) & 0xff),
           ((vendor_id[1] >>  8) & 0xff),
           ((vendor_id[1] >> 16) & 0xff),
           ((vendor_id[1] >> 24) & 0xff),
           ((vendor_id[2] >>  0) & 0xff),
           ((vendor_id[2] >>  8) & 0xff),
           ((vendor_id[2] >> 16) & 0xff),
           ((vendor_id[2] >> 24) & 0xff));
  }
  get_cpuid(1, &value_of_eax, &dummy, &dummy, &value_of_edx);
  if (_launcher_debug) {
    printf("value_of_eax: 0x%x  value_of_edx: 0x%x\n",
           value_of_eax, value_of_edx);
  }
  if ((((value_of_eax >> FAMILY_ID_SHIFT) & FAMILY_ID_MASK) == PENTIUM4_FAMILY_ID) ||
      (((value_of_eax >> EXT_FAMILY_ID_SHIFT) & EXT_FAMILY_ID_MASK) != 0)) {
    if ((((vendor_id[0] >>  0) & 0xff) == 'G') &&
        (((vendor_id[0] >>  8) & 0xff) == 'e') &&
        (((vendor_id[0] >> 16) & 0xff) == 'n') &&
        (((vendor_id[0] >> 24) & 0xff) == 'u') &&
        (((vendor_id[1] >>  0) & 0xff) == 'i') &&
        (((vendor_id[1] >>  8) & 0xff) == 'n') &&
        (((vendor_id[1] >> 16) & 0xff) == 'e') &&
        (((vendor_id[1] >> 24) & 0xff) == 'I') &&
        (((vendor_id[2] >>  0) & 0xff) == 'n') &&
        (((vendor_id[2] >>  8) & 0xff) == 't') &&
        (((vendor_id[2] >> 16) & 0xff) == 'e') &&
        (((vendor_id[2] >> 24) & 0xff) == 'l')) {
      if (((value_of_edx >> HT_BIT_SHIFT) & HT_BIT_MASK) == HT_BIT_MASK) {
        if (_launcher_debug) {
          printf("Hyperthreading supported\n");
        }
        result = hts_supported;
      } else {
        if (_launcher_debug) {
          printf("Hyperthreading not supported\n");
        }
        result = hts_not_supported;
      }
    } else {
      if (_launcher_debug) {
        printf("Not GenuineIntel\n");
      }
      result = hts_not_intel;
    }
  } else {
    if (_launcher_debug) {
      printf("not Pentium 4 or extended\n");
    }
    result = hts_not_pentium4;
  }
  return result;
}

/* Determine how many logical processors there are per CPU */
unsigned int
logical_processors_per_package(void) {
  /*
   * After CPUID with EAX==1, register EBX bits 23 through 16
   * indicate the number of logical processors per package
   */
# define NUM_LOGICAL_SHIFT 16
# define NUM_LOGICAL_MASK 0xff
  unsigned int result                        = 1U;
  const HyperThreadingSupport hyperthreading = hyperthreading_support();

  if (hyperthreading == hts_supported) {
    uint32_t value_of_ebx = 0U;
    uint32_t dummy        = 0U;

    get_cpuid(1, &dummy, &value_of_ebx, &dummy, &dummy);
    result = (value_of_ebx >> NUM_LOGICAL_SHIFT) & NUM_LOGICAL_MASK;
    if (_launcher_debug) {
      printf("logical processors per package: %u\n", result);
    }
  }
  return result;
}

/* Compute the number of physical processors, not logical processors */
unsigned long
physical_processors(void) {
  const long sys_processors = sysconf(_SC_NPROCESSORS_CONF);
  unsigned long result      = sys_processors;

  if (_launcher_debug) {
    printf("sysconf(_SC_NPROCESSORS_CONF): %lu\n", sys_processors);
  }
  if (sys_processors > 1) {
    unsigned int logical_processors = logical_processors_per_package();
    if (logical_processors > 1) {
      result = (unsigned long) sys_processors / logical_processors;
    }
  }
  if (_launcher_debug) {
    printf("physical processors: %lu\n", result);
  }
  return result;
}

#endif /* i586 */

#if defined(__sun) && defined(i586)

/* The definition of a server-class machine for solaris-i586/amd64 */
jboolean
solaris_i586_ServerClassMachine(void) {
  jboolean            result            = JNI_FALSE;
  /* How big is a server class machine? */
  const unsigned long server_processors = 2UL;
  const uint64_t      server_memory     = 2UL * GB;
  /*
   * We seem not to get our full complement of memory.
   *     We allow some part (1/8?) of the memory to be "missing",
   *     based on the sizes of DIMMs, and maybe graphics cards.
   */
  const uint64_t      missing_memory    = 256UL * MB;
  const uint64_t      actual_memory     = physical_memory();

  /* Is this a server class machine? */
  if (actual_memory >= (server_memory - missing_memory)) {
    const unsigned long actual_processors = physical_processors();
    if (actual_processors >= server_processors) {
      result = JNI_TRUE;
    }
  }
  if (_launcher_debug) {
    printf("solaris_" ARCH "_ServerClassMachine: %s\n",
           (result == JNI_TRUE ? "true" : "false"));
  }
  return result;
}

#endif /* __sun && i586 */

#if defined(__linux__) && defined(i586)

/* The definition of a server-class machine for linux-i586 */
jboolean
linux_i586_ServerClassMachine(void) {
  jboolean            result            = JNI_FALSE;
  /* How big is a server class machine? */
  const unsigned long server_processors = 2UL;
  const uint64_t      server_memory     = 2UL * GB;
  /*
   * We seem not to get our full complement of memory.
   *     We allow some part (1/8?) of the memory to be "missing",
   *     based on the sizes of DIMMs, and maybe graphics cards.
   */
  const uint64_t      missing_memory    = 256UL * MB;
  const uint64_t      actual_memory     = physical_memory();

  /* Is this a server class machine? */
  if (actual_memory >= (server_memory - missing_memory)) {
    const unsigned long actual_processors = physical_processors();
    if (actual_processors >= server_processors) {
      result = JNI_TRUE;
    }
  }
  if (_launcher_debug) {
    printf("linux_" ARCH "_ServerClassMachine: %s\n",
           (result == JNI_TRUE ? "true" : "false"));
  }
  return result;
}

#endif /* __linux__ && i586 */

/* Dispatch to the platform-specific definition of "server-class" */
jboolean
ServerClassMachine(void) {
  jboolean result = JNI_FALSE;
#if   defined(__sun) && defined(__sparc)
  result = solaris_sparc_ServerClassMachine();
#elif defined(__sun) && defined(i586)
  result = solaris_i586_ServerClassMachine();
#elif defined(__linux__) && defined(i586)
  result = linux_i586_ServerClassMachine();
#else
  if (_launcher_debug) {
    printf("ServerClassMachine: returns default value of %s\n",
           (result == JNI_TRUE ? "true" : "false"));
  }
#endif
  return result;
}

#endif /* ifndef GAMMA */

#ifndef GAMMA /* gamma launcher does not choose JDK/JRE/JVM */

/*
 *      Since using the file system as a registry is a bit risky, perform
 *      additional sanity checks on the identified directory to validate
 *      it as a valid jre/sdk.
 *
 *      Return 0 if the tests fail; otherwise return non-zero (true).
 *
 *      Note that checking for anything more than the existence of an
 *      executable object at bin/java relative to the path being checked
 *      will break the regression tests.
 */
static int
CheckSanity(char *path, char *dir)
{
    char    buffer[PATH_MAX];

    if (strlen(path) + strlen(dir) + 11 > PATH_MAX)
        return (0);     /* Silently reject "impossibly" long paths */

    (void)strcat(strcat(strcat(strcpy(buffer, path), "/"), dir), "/bin/java");
    return ((access(buffer, X_OK) == 0) ? 1 : 0);
}

/*
 *      Determine if there is an acceptable JRE in the directory dirname.
 *      Upon locating the "best" one, return a fully qualified path to
 *      it. "Best" is defined as the most advanced JRE meeting the
 *      constraints contained in the manifest_info. If no JRE in this
 *      directory meets the constraints, return NULL.
 *
 *      Note that we don't check for errors in reading the directory
 *      (which would be done by checking errno).  This is because it
 *      doesn't matter if we get an error reading the directory, or
 *      we just don't find anything interesting in the directory.  We
 *      just return NULL in either case.
 *
 *      The historical names of j2sdk and j2re were changed to jdk and
 *      jre respecively as part of the 1.5 rebranding effort.  Since the
 *      former names are legacy on Linux, they must be recognized for
 *      all time.  Fortunately, this is a minor cost.
 */
static char
*ProcessDir(manifest_info *info, char *dirname)
{
    DIR     *dirp;
    struct dirent *dp;
    char    *best = NULL;
    int     offset;
    int     best_offset = 0;
    char    *ret_str = NULL;
    char    buffer[PATH_MAX];

    if ((dirp = opendir(dirname)) == NULL)
        return (NULL);

    do {
        if ((dp = readdir(dirp)) != NULL) {
            offset = 0;
            if ((strncmp(dp->d_name, "jre", 3) == 0) ||
                (strncmp(dp->d_name, "jdk", 3) == 0))
                offset = 3;
            else if (strncmp(dp->d_name, "j2re", 4) == 0)
                offset = 4;
            else if (strncmp(dp->d_name, "j2sdk", 5) == 0)
                offset = 5;
            if (offset > 0) {
                if ((acceptable_release(dp->d_name + offset,
                    info->jre_version)) && CheckSanity(dirname, dp->d_name))
                    if ((best == NULL) || (exact_version_id(
                      dp->d_name + offset, best + best_offset) > 0)) {
                        if (best != NULL)
                            free(best);
                        best = strdup(dp->d_name);
                        best_offset = offset;
                    }
            }
        }
    } while (dp != NULL);
    (void) closedir(dirp);
    if (best == NULL)
        return (NULL);
    else {
        ret_str = MemAlloc(strlen(dirname) + strlen(best) + 2);
        ret_str = strcat(strcat(strcpy(ret_str, dirname), "/"), best);
        free(best);
        return (ret_str);
    }
}

/*
 *      This is the global entry point. It examines the host for the optimal
 *      JRE to be used by scanning a set of directories.  The set of directories
 *      is platform dependent and can be overridden by the environment
 *      variable JAVA_VERSION_PATH.
 *
 *      This routine itself simply determines the set of appropriate
 *      directories before passing control onto ProcessDir().
 */
char*
LocateJRE(manifest_info* info)
{
    char        *path;
    char        *home;
    char        *target = NULL;
    char        *dp;
    char        *cp;

    /*
     * Start by getting JAVA_VERSION_PATH
     */
    if (info->jre_restrict_search)
        path = strdup(system_dir);
    else if ((path = getenv("JAVA_VERSION_PATH")) != NULL)
        path = strdup(path);
    else
        if ((home = getenv("HOME")) != NULL) {
            path = (char *)MemAlloc(strlen(home) + 13);
            path = strcat(strcat(strcat(strcpy(path, home),
                user_dir), ":"), system_dir);
        } else
            path = strdup(system_dir);

    /*
     * Step through each directory on the path. Terminate the scan with
     * the first directory with an acceptable JRE.
     */
    cp = dp = path;
    while (dp != NULL) {
        cp = strchr(dp, (int)':');
        if (cp != NULL)
            *cp = (char)NULL;
        if ((target = ProcessDir(info, dp)) != NULL)
            break;
        dp = cp;
        if (dp != NULL)
            dp++;
    }
    free(path);
    return (target);
}

/*
 * Given a path to a jre to execute, this routine checks if this process
 * is indeed that jre.  If not, it exec's that jre.
 *
 * We want to actually check the paths rather than just the version string
 * built into the executable, so that given version specification (and
 * JAVA_VERSION_PATH) will yield the exact same Java environment, regardless
 * of the version of the arbitrary launcher we start with.
 */
void
ExecJRE(char *jre, char **argv)
{
    char    wanted[PATH_MAX];
    char    *execname;
    char    *progname;

    /*
     * Resolve the real path to the directory containing the selected JRE.
     */
    if (realpath(jre, wanted) == NULL) {
        fprintf(stderr, "Unable to resolve %s\n", jre);
        exit(1);
    }

    /*
     * Resolve the real path to the currently running launcher.
     */
    execname = SetExecname(argv);
    if (execname == NULL) {
        fprintf(stderr, "Unable to resolve current executable\n");
        exit(1);
    }

    /*
     * If the path to the selected JRE directory is a match to the initial
     * portion of the path to the currently executing JRE, we have a winner!
     * If so, just return.
     */
    if (strncmp(wanted, execname, strlen(wanted)) == 0)
        return;                 /* I am the droid you were looking for */

    /*
     * If this isn't the selected version, exec the selected version.
     */
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

    /*
     * This should never happen (because of the selection code in SelectJRE),
     * but check for "impossibly" long path names just because buffer overruns
     * can be so deadly.
     */
    if (strlen(wanted) + strlen(progname) + 6 > PATH_MAX) {
        fprintf(stderr, "Path length exceeds maximum length (PATH_MAX)\n");
        exit(1);
    }

    /*
     * Construct the path and exec it.
     */
    (void)strcat(strcat(wanted, "/bin/"), progname);
    argv[0] = progname;
    if (_launcher_debug) {
        int i;
        printf("execv(\"%s\"", wanted);
        for (i = 0; argv[i] != NULL; i++)
            printf(", \"%s\"", argv[i]);
        printf(")\n");
    }
    execv(wanted, argv);
    fprintf(stderr, "Exec of %s failed\n", wanted);
    exit(1);
}

#endif /* ifndef GAMMA */

/*
 * "Borrowed" from Solaris 10 where the unsetenv() function is being added
 * to libc thanks to SUSv3 (Standard Unix Specification, version 3). As
 * such, in the fullness of time this will appear in libc on all relevant
 * Solaris/Linux platforms and maybe even the Windows platform.  At that
 * time, this stub can be removed.
 *
 * This implementation removes the environment locking for multithreaded
 * applications.  (We don't have access to these mutexes within libc and
 * the launcher isn't multithreaded.)  Note that what remains is platform
 * independent, because it only relies on attributes that a POSIX environment
 * defines.
 *
 * Returns 0 on success, -1 on failure.
 *
 * Also removed was the setting of errno.  The only value of errno set
 * was EINVAL ("Invalid Argument").
 */

/*
 * s1(environ) is name=value
 * s2(name) is name(not the form of name=value).
 * if names match, return value of 1, else return 0
 */
static int
match_noeq(const char *s1, const char *s2)
{
        while (*s1 == *s2++) {
                if (*s1++ == '=')
                        return (1);
        }
        if (*s1 == '=' && s2[-1] == '\0')
                return (1);
        return (0);
}

/*
 * added for SUSv3 standard
 *
 * Delete entry from environ.
 * Do not free() memory!  Other threads may be using it.
 * Keep it around forever.
 */
static int
borrowed_unsetenv(const char *name)
{
        long    idx;            /* index into environ */

        if (name == NULL || *name == '\0' ||
            strchr(name, '=') != NULL) {
                return (-1);
        }

        for (idx = 0; environ[idx] != NULL; idx++) {
                if (match_noeq(environ[idx], name))
                        break;
        }
        if (environ[idx] == NULL) {
                /* name not found but still a success */
                return (0);
        }
        /* squeeze up one entry */
        do {
                environ[idx] = environ[idx+1];
        } while (environ[++idx] != NULL);

        return (0);
}
/* --- End of "borrowed" code --- */

/*
 * Wrapper for unsetenv() function.
 */
int
UnsetEnv(char *name)
{
    return(borrowed_unsetenv(name));
}

/*
 * The implementation for finding classes from the bootstrap
 * class loader, refer to java.h
 */
static FindClassFromBootLoader_t *findBootClass = NULL;

jclass
FindBootStrapClass(JNIEnv *env, const char* classname)
{
   if (findBootClass == NULL) {
       findBootClass = (FindClassFromBootLoader_t *)dlsym(RTLD_DEFAULT,
          "JVM_FindClassFromBootLoader");
       if (findBootClass == NULL) {
           fprintf(stderr, "Error: could not load method JVM_FindClassFromBootLoader");
           return NULL;
       }
   }
   return findBootClass(env, classname, JNI_FALSE);
}

