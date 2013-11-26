/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "version_comp.h"


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
 * If a processor / os combination has the ability to run binaries of
 * two data models and cohabitation of jre/jdk bits with both data
 * models is supported, then DUAL_MODE is defined.  When DUAL_MODE is
 * defined, the architecture names for the narrow and wide version of
 * the architecture are defined in LIBARCH64NAME and LIBARCH32NAME.
 * Currently  only Solaris on sparc/sparcv9 and i586/amd64 is DUAL_MODE;
 * linux i586/amd64 could be defined as DUAL_MODE but that is not the
 * current policy.
 */

#ifdef __solaris__
#  ifndef LIBARCH32NAME
#    error "The macro LIBARCH32NAME was not defined on the compile line"
#  endif
#  ifndef LIBARCH64NAME
#    error "The macro LIBARCH64NAME was not defined on the compile line"
#  endif
#  include <sys/systeminfo.h>
#  include <sys/elf.h>
#  include <stdio.h>
#endif

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
 * Previously the launcher modified the LD_LIBRARY_PATH appropriately for the
 * desired data model path, regardless if data models matched or not. The
 * launcher subsequently exec'ed the desired executable, in order to make the
 * LD_LIBRARY_PATH path available, for the runtime linker.
 *
 * Now, in most cases,the launcher will dlopen the target libjvm.so. All
 * required libraries are loaded by the runtime linker, using the
 * $RPATH/$ORIGIN baked into the shared libraries at compile time. Therefore,
 * in most cases, the launcher will only exec, if the data models are
 * mismatched, and will not set any environment variables, regardless of the
 * data models.
 *
 * However, if the environment contains a LD_LIBRARY_PATH, this will cause the
 * launcher to inspect the LD_LIBRARY_PATH. The launcher will check
 *  a. if the LD_LIBRARY_PATH's first component is the the path to the desired
 *     libjvm.so
 *  b. if any other libjvm.so is found in any of the paths.
 * If case b is true, then the launcher will set the LD_LIBRARY_PATH to the
 * desired JRE and reexec, in order to propagate the environment.
 *
 *  Main
 *  (incoming argv)
 *  |
 * \|/
 * SelectVersion
 * (selects the JRE version, note: not data model)
 *  |
 * \|/
 * CreateExecutionEnvironment
 * (determines desired data model)
 *  |
 *  |
 * \|/
 *  Have Desired Model ? --> NO --> Is Dual-Mode ? --> NO --> Exit(with error)
 *  |                                          |
 *  |                                          |
 *  |                                         \|/
 *  |                                         YES
 *  |                                          |
 *  |                                          |
 *  |                                         \|/
 *  |                                CheckJvmType
 *  |                               (removes -client, -server etc.)
 *  |                                          |
 *  |                                          |
 * \|/                                        \|/
 * YES                             Find the desired executable/library
 *  |                                          |
 *  |                                          |
 * \|/                                        \|/
 * CheckJvmType                          RequiresSetenv
 * (removes -client, -server, etc.)
 *  |
 *  |
 * \|/
 * TranslateDashJArgs...
 * (Prepare to pass args to vm)
 *  |
 *  |
 * \|/
 * ParseArguments
 * (removes -d32 and -d64 if any,
 *  processes version options,
 *  creates argument list for vm,
 *  etc.)
 *   |
 *   |
 *  \|/
 * RequiresSetenv
 * Is LD_LIBRARY_PATH
 * and friends set ? --> NO --> Have Desired Model ? NO --> Re-exec --> Main
 *  YES                              YES --> Continue
 *   |
 *   |
 *  \|/
 * Path is desired JRE ? YES --> Have Desired Model ? NO --> Re-exec --> Main
 *  NO                               YES --> Continue
 *   |
 *   |
 *  \|/
 * Paths have well known
 * jvm paths ?       --> NO --> Have Desired Model ? NO --> Re-exec --> Main
 *  YES                              YES --> Continue
 *   |
 *   |
 *  \|/
 *  Does libjvm.so exit
 *  in any of them ? --> NO --> Have Desired Model ? NO --> Re-exec --> Main
 *   YES                             YES --> Continue
 *   |
 *   |
 *  \|/
 *  Set the LD_LIBRARY_PATH
 *   |
 *   |
 *  \|/
 * Re-exec
 *   |
 *   |
 *  \|/
 * Main
 */

#define GetArch() GetArchPath(CURRENT_DATA_MODEL)

/* Store the name of the executable once computed */
static char *execname = NULL;

/*
 * execname accessor from other parts of platform dependent logic
 */
const char *
GetExecName() {
    return execname;
}

const char *
GetArchPath(int nbits)
{
    switch(nbits) {
#ifdef DUAL_MODE
        case 32:
            return LIBARCH32NAME;
        case 64:
            return LIBARCH64NAME;
#endif /* DUAL_MODE */
        default:
            return LIBARCHNAME;
    }
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
 * contains a lib/$LIBARCH/{server,client}/libjvm.so ?
 */
static jboolean
ContainsLibJVM(int wanted, const char *env) {
    char clientPattern[PATH_MAX + 1];
    char serverPattern[PATH_MAX + 1];
    char *envpath;
    char *path;
    jboolean clientPatternFound;
    jboolean serverPatternFound;

    /* fastest path */
    if (env == NULL) {
        return JNI_FALSE;
    }

    /* the usual suspects */
    JLI_Snprintf(clientPattern, PATH_MAX, "lib/%s/client", GetArchPath(wanted));
    JLI_Snprintf(serverPattern, PATH_MAX, "lib/%s/server", GetArchPath(wanted));

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
    for (path = JLI_StrTok(envpath, ":"); path != NULL; path = JLI_StrTok(NULL, ":")) {
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
 * Test whether the environment variable needs to be set, see flowchart.
 */
static jboolean
RequiresSetenv(int wanted, const char *jvmpath) {
    char jpath[PATH_MAX + 1];
    char *llp;
    char *dmllp = NULL;
    char *p; /* a utility pointer */

#ifdef AIX
    /* We always have to set the LIBPATH on AIX because ld doesn't support $ORIGIN. */
    return JNI_TRUE;
#endif

    llp = getenv("LD_LIBRARY_PATH");
#ifdef __solaris__
    dmllp = (CURRENT_DATA_MODEL == 32)
            ? getenv("LD_LIBRARY_PATH_32")
            : getenv("LD_LIBRARY_PATH_64");
#endif /* __solaris__ */
    /* no environment variable is a good environment variable */
    if (llp == NULL && dmllp == NULL) {
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
     * previous versions of the JRE, thus it is the only path that matters here.
     * So we check to see if the desired JRE is set.
     */
    JLI_StrNCpy(jpath, jvmpath, PATH_MAX);
    p = JLI_StrRChr(jpath, '/');
    *p = '\0';
    if (llp != NULL && JLI_StrNCmp(llp, jpath, JLI_StrLen(jpath)) == 0) {
        return JNI_FALSE;
    }

    /* scrutinize all the paths further */
    if (llp != NULL &&  ContainsLibJVM(wanted, llp)) {
        return JNI_TRUE;
    }
    if (dmllp != NULL && ContainsLibJVM(wanted, dmllp)) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}
#endif /* SETENV_REQUIRED */

void
CreateExecutionEnvironment(int *pargc, char ***pargv,
                           char jrepath[], jint so_jrepath,
                           char jvmpath[], jint so_jvmpath,
                           char jvmcfg[],  jint so_jvmcfg) {
  /*
   * First, determine if we are running the desired data model.  If we
   * are running the desired data model, all the error messages
   * associated with calling GetJREPath, ReadKnownVMs, etc. should be
   * output.  However, if we are not running the desired data model,
   * some of the errors should be suppressed since it is more
   * informative to issue an error message based on whether or not the
   * os/processor combination has dual mode capabilities.
   */
    jboolean jvmpathExists;

    /* Compute/set the name of the executable */
    SetExecname(*pargv);

    /* Check data model flags, and exec process, if needed */
    {
      char *arch        = (char *)GetArch(); /* like sparc or sparcv9 */
      char * jvmtype    = NULL;
      int  argc         = *pargc;
      char **argv       = *pargv;
      int running       = CURRENT_DATA_MODEL;

      int wanted        = running;      /* What data mode is being
                                           asked for? Current model is
                                           fine unless another model
                                           is asked for */
#ifdef SETENV_REQUIRED
      jboolean mustsetenv = JNI_FALSE;
      char *runpath     = NULL; /* existing effective LD_LIBRARY_PATH setting */
      char* new_runpath = NULL; /* desired new LD_LIBRARY_PATH string */
      char* newpath     = NULL; /* path on new LD_LIBRARY_PATH */
      char* lastslash   = NULL;
      char** newenvp    = NULL; /* current environment */
#ifdef __solaris__
      char*  dmpath     = NULL;  /* data model specific LD_LIBRARY_PATH,
                                    Solaris only */
#endif /* __solaris__ */
#endif  /* SETENV_REQUIRED */

      char** newargv    = NULL;
      int    newargc    = 0;

      /*
       * Starting in 1.5, all unix platforms accept the -d32 and -d64
       * options.  On platforms where only one data-model is supported
       * (e.g. ia-64 Linux), using the flag for the other data model is
       * an error and will terminate the program.
       */

      { /* open new scope to declare local variables */
        int i;

        newargv = (char **)JLI_MemAlloc((argc+1) * sizeof(char*));
        newargv[newargc++] = argv[0];

        /* scan for data model arguments and remove from argument list;
           last occurrence determines desired data model */
        for (i=1; i < argc; i++) {

          if (JLI_StrCmp(argv[i], "-J-d64") == 0 || JLI_StrCmp(argv[i], "-d64") == 0) {
            wanted = 64;
            continue;
          }
          if (JLI_StrCmp(argv[i], "-J-d32") == 0 || JLI_StrCmp(argv[i], "-d32") == 0) {
            wanted = 32;
            continue;
          }
          newargv[newargc++] = argv[i];

          if (IsJavaArgs()) {
            if (argv[i][0] != '-') continue;
          } else {
            if (JLI_StrCmp(argv[i], "-classpath") == 0 || JLI_StrCmp(argv[i], "-cp") == 0) {
              i++;
              if (i >= argc) break;
              newargv[newargc++] = argv[i];
              continue;
            }
            if (argv[i][0] != '-') { i++; break; }
          }
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
          JLI_ReportErrorMessage(JRE_ERROR1);
          exit(2);
        }
        JLI_Snprintf(jvmcfg, so_jvmcfg, "%s%slib%s%s%sjvm.cfg",
                     jrepath, FILESEP, FILESEP,  arch, FILESEP);
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

        if (!GetJVMPath(jrepath, jvmtype, jvmpath, so_jvmpath, arch, 0 )) {
          JLI_ReportErrorMessage(CFG_ERROR8, jvmtype, jvmpath);
          exit(4);
        }
        /*
         * we seem to have everything we need, so without further ado
         * we return back, otherwise proceed to set the environment.
         */
#ifdef SETENV_REQUIRED
        mustsetenv = RequiresSetenv(wanted, jvmpath);
        JLI_TraceLauncher("mustsetenv: %s\n", mustsetenv ? "TRUE" : "FALSE");

        if (mustsetenv == JNI_FALSE) {
            JLI_MemFree(newargv);
            return;
        }
#else
        JLI_MemFree(newargv);
        return;
#endif /* SETENV_REQUIRED */
      } else {  /* do the same speculatively or exit */
#ifdef DUAL_MODE
        if (running != wanted) {
          /* Find out where the JRE is that we will be using. */
          if (!GetJREPath(jrepath, so_jrepath, GetArchPath(wanted), JNI_TRUE)) {
            /* give up and let other code report error message */
            JLI_ReportErrorMessage(JRE_ERROR2, wanted);
            exit(1);
          }
          JLI_Snprintf(jvmcfg, so_jvmcfg, "%s%slib%s%s%sjvm.cfg",
                       jrepath, FILESEP, FILESEP, GetArchPath(wanted), FILESEP);
          /*
           * Read in jvm.cfg for target data model and process vm
           * selection options.
           */
          if (ReadKnownVMs(jvmcfg, JNI_TRUE) < 1) {
            /* give up and let other code report error message */
            JLI_ReportErrorMessage(JRE_ERROR2, wanted);
            exit(1);
          }
          jvmpath[0] = '\0';
          jvmtype = CheckJvmType(pargc, pargv, JNI_TRUE);
          if (JLI_StrCmp(jvmtype, "ERROR") == 0) {
            JLI_ReportErrorMessage(CFG_ERROR9);
            exit(4);
          }

          /* exec child can do error checking on the existence of the path */
          jvmpathExists = GetJVMPath(jrepath, jvmtype, jvmpath, so_jvmpath, GetArchPath(wanted), 0);
#ifdef SETENV_REQUIRED
          mustsetenv = RequiresSetenv(wanted, jvmpath);
#endif /* SETENV_REQUIRED */
        }
#else /* ! DUALMODE */
        JLI_ReportErrorMessage(JRE_ERROR2, wanted);
        exit(1);
#endif /* DUAL_MODE */
        }
#ifdef SETENV_REQUIRED
        if (mustsetenv) {
            /*
             * We will set the LD_LIBRARY_PATH as follows:
             *
             *     o          $JVMPATH (directory portion only)
             *     o          $JRE/lib/$LIBARCHNAME
             *     o          $JRE/../lib/$LIBARCHNAME
             *
             * followed by the user's previous effective LD_LIBRARY_PATH, if
             * any.
             */

#ifdef __solaris__
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

            switch (wanted) {
                case 0:
                    if (running == 32) {
                        dmpath = getenv("LD_LIBRARY_PATH_32");
                        wanted = 32;
                    } else {
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
                    JLI_ReportErrorMessage(JRE_ERROR3, __LINE__);
                    exit(1); /* unknown value in wanted */
                    break;
            }

            /*
             * If dmpath is NULL, the relevant data model specific variable is
             * not set and normal LD_LIBRARY_PATH should be used.
             */
            if (dmpath == NULL) {
                runpath = getenv("LD_LIBRARY_PATH");
            } else {
                runpath = dmpath;
            }
#else /* ! __solaris__ */
            /*
             * If not on Solaris, assume only a single LD_LIBRARY_PATH
             * variable.
             */
            runpath = getenv(LD_LIBRARY_PATH);
#endif /* __solaris__ */

            /* runpath contains current effective LD_LIBRARY_PATH setting */

            jvmpath = JLI_StringDup(jvmpath);
            new_runpath = JLI_MemAlloc(((runpath != NULL) ? JLI_StrLen(runpath) : 0) +
                    2 * JLI_StrLen(jrepath) + 2 * JLI_StrLen(arch) +
#ifdef AIX
                    /* On AIX we additionally need 'jli' in the path because ld doesn't support $ORIGIN. */
                    JLI_StrLen(jrepath) + JLI_StrLen(arch) + JLI_StrLen("/lib//jli:") +
#endif
                    JLI_StrLen(jvmpath) + 52);
            newpath = new_runpath + JLI_StrLen(LD_LIBRARY_PATH "=");


            /*
             * Create desired LD_LIBRARY_PATH value for target data model.
             */
            {
                /* remove the name of the .so from the JVM path */
                lastslash = JLI_StrRChr(jvmpath, '/');
                if (lastslash)
                    *lastslash = '\0';

                sprintf(new_runpath, LD_LIBRARY_PATH "="
                        "%s:"
                        "%s/lib/%s:"
#ifdef AIX
                        "%s/lib/%s/jli:" /* Needed on AIX because ld doesn't support $ORIGIN. */
#endif
                        "%s/../lib/%s",
                        jvmpath,
#ifdef DUAL_MODE
                        jrepath, GetArchPath(wanted),
                        jrepath, GetArchPath(wanted)
#else /* !DUAL_MODE */
                        jrepath, arch,
#ifdef AIX
                        jrepath, arch,
#endif
                        jrepath, arch
#endif /* DUAL_MODE */
                        );


                /*
                 * Check to make sure that the prefix of the current path is the
                 * desired environment variable setting, though the RequiresSetenv
                 * checks if the desired runpath exists, this logic does a more
                 * comprehensive check.
                 */
                if (runpath != NULL &&
                        JLI_StrNCmp(newpath, runpath, JLI_StrLen(newpath)) == 0 &&
                        (runpath[JLI_StrLen(newpath)] == 0 || runpath[JLI_StrLen(newpath)] == ':') &&
                        (running == wanted) /* data model does not have to be changed */
#ifdef __solaris__
                        && (dmpath == NULL) /* data model specific variables not set  */
#endif /* __solaris__ */
                        ) {
                    JLI_MemFree(newargv);
                    JLI_MemFree(new_runpath);
                    return;
                }
            }

            /*
             * Place the desired environment setting onto the prefix of
             * LD_LIBRARY_PATH.  Note that this prevents any possible infinite
             * loop of execv() because we test for the prefix, above.
             */
            if (runpath != 0) {
                JLI_StrCat(new_runpath, ":");
                JLI_StrCat(new_runpath, runpath);
            }

            if (putenv(new_runpath) != 0) {
                exit(1); /* problem allocating memory; LD_LIBRARY_PATH not set
                    properly */
            }

            /*
             * Unix systems document that they look at LD_LIBRARY_PATH only
             * once at startup, so we have to re-exec the current executable
             * to get the changed environment variable to have an effect.
             */

#ifdef __solaris__
            /*
             * If dmpath is not NULL, remove the data model specific string
             * in the environment for the exec'ed child.
             */
            if (dmpath != NULL)
                (void)UnsetEnv((wanted == 32) ? "LD_LIBRARY_PATH_32" : "LD_LIBRARY_PATH_64");
#endif /* __solaris */

            newenvp = environ;
        }
#endif /* SETENV_REQUIRED */
        {
            char *newexec = execname;
#ifdef DUAL_MODE
            /*
             * If the data model is being changed, the path to the
             * executable must be updated accordingly; the executable name
             * and directory the executable resides in are separate.  In the
             * case of 32 => 64, the new bits are assumed to reside in, e.g.
             * "olddir/LIBARCH64NAME/execname"; in the case of 64 => 32,
             * the bits are assumed to be in "olddir/../execname".  For example,
             *
             * olddir/sparcv9/execname
             * olddir/amd64/execname
             *
             * for Solaris SPARC and Linux amd64, respectively.
             */

            if (running != wanted) {
                char *oldexec = JLI_StrCpy(JLI_MemAlloc(JLI_StrLen(execname) + 1), execname);
                char *olddir = oldexec;
                char *oldbase = JLI_StrRChr(oldexec, '/');


                newexec = JLI_MemAlloc(JLI_StrLen(execname) + 20);
                *oldbase++ = 0;
                sprintf(newexec, "%s/%s/%s", olddir,
                        ((wanted == 64) ? LIBARCH64NAME : ".."), oldbase);
                argv[0] = newexec;
            }
#endif /* DUAL_MODE */
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
            JLI_ReportErrorMessageSys(JRE_ERROR4, newexec);

#ifdef DUAL_MODE
            if (running != wanted) {
                JLI_ReportErrorMessage(JRE_ERROR5, wanted, running);
#ifdef __solaris__
#ifdef __sparc
                JLI_ReportErrorMessage(JRE_ERROR6);
#else  /* ! __sparc__ */
                JLI_ReportErrorMessage(JRE_ERROR7);
#endif  /* __sparc */
#endif /* __solaris__ */
            }
#endif /* DUAL_MODE */

        }
        exit(1);
    }
}

/*
 * On Solaris VM choosing is done by the launcher (java.c),
 * bitsWanted is used by MacOSX,  on Solaris and Linux this.
 * parameter is unused.
 */
static jboolean
GetJVMPath(const char *jrepath, const char *jvmtype,
           char *jvmpath, jint jvmpathsize, const char * arch, int bitsWanted)
{
    struct stat s;

    if (JLI_StrChr(jvmtype, '/')) {
        JLI_Snprintf(jvmpath, jvmpathsize, "%s/" JVM_DLL, jvmtype);
    } else {
        JLI_Snprintf(jvmpath, jvmpathsize, "%s/lib/%s/%s/" JVM_DLL, jrepath, arch, jvmtype);
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
 * Find path to JRE based on .exe's location or registry settings.
 */
static jboolean
GetJREPath(char *path, jint pathsize, const char * arch, jboolean speculative)
{
    char libjava[MAXPATHLEN];

    if (GetApplicationHome(path, pathsize)) {
        /* Is JRE co-located with the application? */
        JLI_Snprintf(libjava, sizeof(libjava), "%s/lib/%s/" JAVA_DLL, path, arch);
        if (access(libjava, F_OK) == 0) {
            JLI_TraceLauncher("JRE path is %s\n", path);
            return JNI_TRUE;
        }

        /* Does the app ship a private JRE in <apphome>/jre directory? */
        JLI_Snprintf(libjava, sizeof(libjava), "%s/jre/lib/%s/" JAVA_DLL, path, arch);
        if (access(libjava, F_OK) == 0) {
            JLI_StrCat(path, "/jre");
            JLI_TraceLauncher("JRE path is %s\n", path);
            return JNI_TRUE;
        }
    }

    if (!speculative)
      JLI_ReportErrorMessage(JRE_ERROR8 JAVA_DLL);
    return JNI_FALSE;
}

jboolean
LoadJavaVM(const char *jvmpath, InvocationFunctions *ifn)
{
    void *libjvm;

    JLI_TraceLauncher("JVM path is %s\n", jvmpath);

    libjvm = dlopen(jvmpath, RTLD_NOW + RTLD_GLOBAL);
    if (libjvm == NULL) {
#if defined(__solaris__) && defined(__sparc) && !defined(_LP64) /* i.e. 32-bit sparc */
      FILE * fp;
      Elf32_Ehdr elf_head;
      int count;
      int location;

      fp = fopen(jvmpath, "r");
      if (fp == NULL) {
        JLI_ReportErrorMessage(DLL_ERROR2, jvmpath, dlerror());
        return JNI_FALSE;
      }

      /* read in elf header */
      count = fread((void*)(&elf_head), sizeof(Elf32_Ehdr), 1, fp);
      fclose(fp);
      if (count < 1) {
        JLI_ReportErrorMessage(DLL_ERROR2, jvmpath, dlerror());
        return JNI_FALSE;
      }

      /*
       * Check for running a server vm (compiled with -xarch=v8plus)
       * on a stock v8 processor.  In this case, the machine type in
       * the elf header would not be included the architecture list
       * provided by the isalist command, which is turn is gotten from
       * sysinfo.  This case cannot occur on 64-bit hardware and thus
       * does not have to be checked for in binaries with an LP64 data
       * model.
       */
      if (elf_head.e_machine == EM_SPARC32PLUS) {
        char buf[257];  /* recommended buffer size from sysinfo man
                           page */
        long length;
        char* location;

        length = sysinfo(SI_ISALIST, buf, 257);
        if (length > 0) {
            location = JLI_StrStr(buf, "sparcv8plus ");
          if (location == NULL) {
            JLI_ReportErrorMessage(JVM_ERROR3);
            return JNI_FALSE;
          }
        }
      }
#endif
        JLI_ReportErrorMessage(DLL_ERROR1, __LINE__);
        JLI_ReportErrorMessage(DLL_ERROR2, jvmpath, dlerror());
        return JNI_FALSE;
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
#if defined(__solaris__)
    {
        Dl_info dlinfo;
        int (*fptr)();

        fptr = (int (*)())dlsym(RTLD_DEFAULT, "main");
        if (fptr == NULL) {
            JLI_ReportErrorMessage(DLL_ERROR3, dlerror());
            return JNI_FALSE;
        }

        if (dladdr((void*)fptr, &dlinfo)) {
            char *resolved = (char*)JLI_MemAlloc(PATH_MAX+1);
            if (resolved != NULL) {
                exec_path = realpath(dlinfo.dli_fname, resolved);
                if (exec_path == NULL) {
                    JLI_MemFree(resolved);
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
            buf[len] = '\0';            /* readlink(2) doesn't NUL terminate */
            exec_path = JLI_StringDup(buf);
        }
    }
#else /* !__solaris__ && !__linux__ */
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
        char jrePath[MAXPATHLEN];
        char splashPath[MAXPATHLEN];

        if (!GetJREPath(jrePath, sizeof(jrePath), GetArch(), JNI_FALSE)) {
            JLI_ReportErrorMessage(JRE_ERROR1);
            return NULL;
        }
        ret = JLI_Snprintf(splashPath, sizeof(splashPath), "%s/lib/%s/%s",
                     jrePath, GetArch(), SPLASHSCREEN_SO);

        if (ret >= (int) sizeof(splashPath)) {
            JLI_ReportErrorMessage(JRE_ERROR11);
            return NULL;
        }
        if (ret < 0) {
            JLI_ReportErrorMessage(JRE_ERROR13);
            return NULL;
        }
        hSplashLib = dlopen(splashPath, RTLD_LAZY | RTLD_GLOBAL);
        JLI_TraceLauncher("Info: loaded %s\n", splashPath);
    }
    if (hSplashLib) {
        void* sym = dlsym(hSplashLib, name);
        return sym;
    } else {
        return NULL;
    }
}

void SplashFreeLibrary() {
    if (hSplashLib) {
        dlclose(hSplashLib);
        hSplashLib = NULL;
    }
}

/*
 * Block current thread and continue execution in a new thread
 */
int
ContinueInNewThread0(int (JNICALL *continuation)(void *), jlong stack_size, void * args) {
    int rslt;
#ifndef __solaris__
    pthread_t tid;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

    if (stack_size > 0) {
      pthread_attr_setstacksize(&attr, stack_size);
    }

    if (pthread_create(&tid, &attr, (void *(*)(void*))continuation, (void*)args) == 0) {
      void * tmp;
      pthread_join(tid, &tmp);
      rslt = (int)tmp;
    } else {
     /*
      * Continue execution in current thread if for some reason (e.g. out of
      * memory/LWP)  a new thread can't be created. This will likely fail
      * later in continuation as JNI_CreateJavaVM needs to create quite a
      * few new threads, anyway, just give it a try..
      */
      rslt = continuation(args);
    }

    pthread_attr_destroy(&attr);
#else /* __solaris__ */
    thread_t tid;
    long flags = 0;
    if (thr_create(NULL, stack_size, (void *(*)(void *))continuation, args, flags, &tid) == 0) {
      void * tmp;
      thr_join(tid, NULL, &tmp);
      rslt = (int)tmp;
    } else {
      /* See above. Continue in current thread if thr_create() failed */
      rslt = continuation(args);
    }
#endif /* !__solaris__ */
    return rslt;
}

/* Coarse estimation of number of digits assuming the worst case is a 64-bit pid. */
#define MAX_PID_STR_SZ   20

void SetJavaLauncherPlatformProps() {
   /* Linux only */
#ifdef __linux__
    const char *substr = "-Dsun.java.launcher.pid=";
    char *pid_prop_str = (char *)JLI_MemAlloc(JLI_StrLen(substr) + MAX_PID_STR_SZ + 1);
    sprintf(pid_prop_str, "%s%d", substr, getpid());
    AddOption(pid_prop_str, NULL);
#endif /* __linux__ */
}

int
JVMInit(InvocationFunctions* ifn, jlong threadStackSize,
        int argc, char **argv,
        int mode, char *what, int ret)
{
    ShowSplashScreen();
    return ContinueInNewThread(ifn, threadStackSize, argc, argv, mode, what, ret);
}

void
PostJVMInit(JNIEnv *env, jstring mainClass, JavaVM *vm)
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
