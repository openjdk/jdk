/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/*
 * If app is "/foo/bin/javac", or "/foo/bin/sparcv9/javac" then put
 * "/foo" into buf.
 */
jboolean
GetApplicationHome(char *buf, jint bufsize)
{
    const char *execname = GetExecName();
    if (execname != NULL) {
        JLI_Snprintf(buf, bufsize, "%s", execname);
        buf[bufsize-1] = '\0';
    } else {
        return JNI_FALSE;
    }

    if (JLI_StrRChr(buf, '/') == 0) {
        buf[0] = '\0';
        return JNI_FALSE;
    }
    *(JLI_StrRChr(buf, '/')) = '\0';    /* executable file      */
    if (JLI_StrLen(buf) < 4 || JLI_StrRChr(buf, '/') == 0) {
        buf[0] = '\0';
        return JNI_FALSE;
    }
    if (JLI_StrCmp("/bin", buf + JLI_StrLen(buf) - 4) != 0)
        *(JLI_StrRChr(buf, '/')) = '\0';        /* sparcv9 or amd64     */
    if (JLI_StrLen(buf) < 4 || JLI_StrCmp("/bin", buf + JLI_StrLen(buf) - 4) != 0) {
        buf[0] = '\0';
        return JNI_FALSE;
    }
    *(JLI_StrRChr(buf, '/')) = '\0';    /* bin                  */

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

    if ((JLI_StrLen(indir) + JLI_StrLen(cmd) + 1)  > PATH_MAX) return 0;
    JLI_Snprintf(name, sizeof(name), "%s%c%s", indir, FILE_SEPARATOR, cmd);
    if (!ProgramExists(name)) return 0;
    real = JLI_MemAlloc(PATH_MAX + 2);
    if (!realpath(name, real))
        JLI_StrCpy(real, name);
    return real;
}

/*
 * Find a path for the executable
 */
char *
FindExecName(char *program)
{
    char cwdbuf[PATH_MAX+2];
    char *path;
    char *tmp_path;
    char *f;
    char *result = NULL;

    /* absolute path? */
    if (*program == FILE_SEPARATOR ||
        (FILE_SEPARATOR=='\\' && JLI_StrRChr(program, ':')))
        return Resolve("", program+1);

    /* relative path? */
    if (JLI_StrRChr(program, FILE_SEPARATOR) != 0) {
        char buf[PATH_MAX+2];
        return Resolve(getcwd(cwdbuf, sizeof(cwdbuf)), program);
    }

    /* from search path? */
    path = getenv("PATH");
    if (!path || !*path) path = ".";
    tmp_path = JLI_MemAlloc(JLI_StrLen(path) + 2);
    JLI_StrCpy(tmp_path, path);

    for (f=tmp_path; *f && result==0; ) {
        char *s = f;
        while (*f && (*f != PATH_SEPARATOR)) ++f;
        if (*f) *f++ = 0;
        if (*s == FILE_SEPARATOR)
            result = Resolve(s, program);
        else {
            /* relative path element */
            char dir[2*PATH_MAX];
            JLI_Snprintf(dir, sizeof(dir), "%s%c%s", getcwd(cwdbuf, sizeof(cwdbuf)),
                    FILE_SEPARATOR, s);
            result = Resolve(dir, program);
        }
        if (result != 0) break;
    }

    JLI_MemFree(tmp_path);
    return result;
}

void JLI_ReportErrorMessage(const char* fmt, ...) {
    va_list vl;
    va_start(vl, fmt);
    vfprintf(stderr, fmt, vl);
    fprintf(stderr, "\n");
    va_end(vl);
}

void JLI_ReportErrorMessageSys(const char* fmt, ...) {
    va_list vl;
    char *emsg;

    /*
     * TODO: its safer to use strerror_r but is not available on
     * Solaris 8. Until then....
     */
    emsg = strerror(errno);
    if (emsg != NULL) {
        fprintf(stderr, "%s\n", emsg);
    }

    va_start(vl, fmt);
    vfprintf(stderr, fmt, vl);
    fprintf(stderr, "\n");
    va_end(vl);
}

void  JLI_ReportExceptionDescription(JNIEnv * env) {
  (*env)->ExceptionDescribe(env);
}

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

    if (JLI_StrLen(path) + JLI_StrLen(dir) + 11 > PATH_MAX)
        return (0);     /* Silently reject "impossibly" long paths */

    JLI_Snprintf(buffer, sizeof(buffer), "%s/%s/bin/java", path, dir);
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
            if ((JLI_StrNCmp(dp->d_name, "jre", 3) == 0) ||
                (JLI_StrNCmp(dp->d_name, "jdk", 3) == 0))
                offset = 3;
            else if (JLI_StrNCmp(dp->d_name, "j2re", 4) == 0)
                offset = 4;
            else if (JLI_StrNCmp(dp->d_name, "j2sdk", 5) == 0)
                offset = 5;
            if (offset > 0) {
                if ((JLI_AcceptableRelease(dp->d_name + offset,
                    info->jre_version)) && CheckSanity(dirname, dp->d_name))
                    if ((best == NULL) || (JLI_ExactVersionId(
                      dp->d_name + offset, best + best_offset) > 0)) {
                        if (best != NULL)
                            JLI_MemFree(best);
                        best = JLI_StringDup(dp->d_name);
                        best_offset = offset;
                    }
            }
        }
    } while (dp != NULL);
    (void) closedir(dirp);
    if (best == NULL)
        return (NULL);
    else {
        ret_str = JLI_MemAlloc(JLI_StrLen(dirname) + JLI_StrLen(best) + 2);
        sprintf(ret_str, "%s/%s", dirname, best);
        JLI_MemFree(best);
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
    if (info->jre_restrict_search) {
        path = JLI_StringDup(system_dir);
    } else if ((path = getenv("JAVA_VERSION_PATH")) != NULL) {
        path = JLI_StringDup(path);
    } else {
        if ((home = getenv("HOME")) != NULL) {
            path = (char *)JLI_MemAlloc(JLI_StrLen(home) + \
                        JLI_StrLen(system_dir) + JLI_StrLen(user_dir) + 2);
            sprintf(path, "%s%s:%s", home, user_dir, system_dir);
        } else {
            path = JLI_StringDup(system_dir);
        }
    }

    /*
     * Step through each directory on the path. Terminate the scan with
     * the first directory with an acceptable JRE.
     */
    cp = dp = path;
    while (dp != NULL) {
        cp = JLI_StrChr(dp, (int)':');
        if (cp != NULL)
            *cp = '\0';
        if ((target = ProcessDir(info, dp)) != NULL)
            break;
        dp = cp;
        if (dp != NULL)
            dp++;
    }
    JLI_MemFree(path);
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
    const char* progname = GetProgramName();
    const char* execname = NULL;

    /*
     * Resolve the real path to the directory containing the selected JRE.
     */
    if (realpath(jre, wanted) == NULL) {
        JLI_ReportErrorMessage(JRE_ERROR9, jre);
        exit(1);
    }

    /*
     * Resolve the real path to the currently running launcher.
     */
    SetExecname(argv);
    execname = GetExecName();
    if (execname == NULL) {
        JLI_ReportErrorMessage(JRE_ERROR10);
        exit(1);
    }

    /*
     * If the path to the selected JRE directory is a match to the initial
     * portion of the path to the currently executing JRE, we have a winner!
     * If so, just return.
     */
    if (JLI_StrNCmp(wanted, execname, JLI_StrLen(wanted)) == 0)
        return;                 /* I am the droid you were looking for */


    /*
     * This should never happen (because of the selection code in SelectJRE),
     * but check for "impossibly" long path names just because buffer overruns
     * can be so deadly.
     */
    if (JLI_StrLen(wanted) + JLI_StrLen(progname) + 6 > PATH_MAX) {
        JLI_ReportErrorMessage(JRE_ERROR11);
        exit(1);
    }

    /*
     * Construct the path and exec it.
     */
    (void)JLI_StrCat(JLI_StrCat(wanted, "/bin/"), progname);
    argv[0] = JLI_StringDup(progname);
    if (JLI_IsTraceLauncher()) {
        int i;
        printf("ReExec Command: %s (%s)\n", wanted, argv[0]);
        printf("ReExec Args:");
        for (i = 1; argv[i] != NULL; i++)
            printf(" %s", argv[i]);
        printf("\n");
    }
    JLI_TraceLauncher("TRACER_MARKER:About to EXEC\n");
    (void)fflush(stdout);
    (void)fflush(stderr);
    execv(wanted, argv);
    JLI_ReportErrorMessageSys(JRE_ERROR12, wanted);
    exit(1);
}

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
            JLI_StrChr(name, '=') != NULL) {
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

const char *
jlong_format_specifier() {
    return "%lld";
}

jboolean
IsJavaw()
{
    /* noop on UNIX */
    return JNI_FALSE;
}

void
InitLauncher(jboolean javaw)
{
    JLI_SetTraceLauncher();
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
           JLI_ReportErrorMessage(DLL_ERROR4,
               "JVM_FindClassFromBootLoader");
           return NULL;
       }
   }
   return findBootClass(env, classname);
}

StdArg
*JLI_GetStdArgs()
{
    return NULL;
}

int
JLI_GetStdArgc() {
    return 0;
}

jobjectArray
CreateApplicationArgs(JNIEnv *env, char **strv, int argc)
{
    return NewPlatformStringArray(env, strv, argc);
}
