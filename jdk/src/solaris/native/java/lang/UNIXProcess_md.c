/*
 * Copyright 1995-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

#undef  _LARGEFILE64_SOURCE
#define _LARGEFILE64_SOURCE 1

#include "jni.h"
#include "jvm.h"
#include "jvm_md.h"
#include "jni_util.h"
#include "io_util.h"

/*
 * Platform-specific support for java.lang.Process
 */
#include <assert.h>
#include <stddef.h>
#include <stdlib.h>
#include <sys/types.h>
#include <ctype.h>
#include <wait.h>
#include <signal.h>
#include <string.h>
#include <errno.h>
#include <dirent.h>
#include <unistd.h>
#include <fcntl.h>
#include <limits.h>

#ifndef STDIN_FILENO
#define STDIN_FILENO 0
#endif

#ifndef STDOUT_FILENO
#define STDOUT_FILENO 1
#endif

#ifndef STDERR_FILENO
#define STDERR_FILENO 2
#endif

#ifndef SA_NOCLDSTOP
#define SA_NOCLDSTOP 0
#endif

#ifndef SA_RESTART
#define SA_RESTART 0
#endif

#define FAIL_FILENO (STDERR_FILENO + 1)

static void
setSIGCHLDHandler(JNIEnv *env)
{
    /* There is a subtle difference between having the signal handler
     * for SIGCHLD be SIG_DFL and SIG_IGN.  We cannot obtain process
     * termination information for child processes if the signal
     * handler is SIG_IGN.  It must be SIG_DFL.
     *
     * We used to set the SIGCHLD handler only on Linux, but it's
     * safest to set it unconditionally.
     *
     * Consider what happens if java's parent process sets the SIGCHLD
     * handler to SIG_IGN.  Normally signal handlers are inherited by
     * children, but SIGCHLD is a controversial case.  Solaris appears
     * to always reset it to SIG_DFL, but this behavior may be
     * non-standard-compliant, and we shouldn't rely on it.
     *
     * References:
     * http://www.opengroup.org/onlinepubs/7908799/xsh/exec.html
     * http://www.pasc.org/interps/unofficial/db/p1003.1/pasc-1003.1-132.html
     */
    struct sigaction sa;
    sa.sa_handler = SIG_DFL;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_NOCLDSTOP | SA_RESTART;
    if (sigaction(SIGCHLD, &sa, NULL) < 0)
        JNU_ThrowInternalError(env, "Can't set SIGCHLD handler");
}

static void*
xmalloc(JNIEnv *env, size_t size)
{
    void *p = malloc(size);
    if (p == NULL)
        JNU_ThrowOutOfMemoryError(env, NULL);
    return p;
}

#define NEW(type, n) ((type *) xmalloc(env, (n) * sizeof(type)))

/**
 * If PATH is not defined, the OS provides some default value.
 * Unfortunately, there's no portable way to get this value.
 * Fortunately, it's only needed if the child has PATH while we do not.
 */
static const char*
defaultPath(void)
{
#ifdef __solaris__
    /* These really are the Solaris defaults! */
    return (geteuid() == 0 || getuid() == 0) ?
        "/usr/xpg4/bin:/usr/ccs/bin:/usr/bin:/opt/SUNWspro/bin:/usr/sbin" :
        "/usr/xpg4/bin:/usr/ccs/bin:/usr/bin:/opt/SUNWspro/bin:";
#else
    return ":/bin:/usr/bin";    /* glibc */
#endif
}

static const char*
effectivePath(void)
{
    const char *s = getenv("PATH");
    return (s != NULL) ? s : defaultPath();
}

static int
countOccurrences(const char *s, char c)
{
    int count;
    for (count = 0; *s != '\0'; s++)
        count += (*s == c);
    return count;
}

static const char * const *
splitPath(JNIEnv *env, const char *path)
{
    const char *p, *q;
    char **pathv;
    int i;
    int count = countOccurrences(path, ':') + 1;

    pathv = NEW(char*, count+1);
    pathv[count] = NULL;
    for (p = path, i = 0; i < count; i++, p = q + 1) {
        for (q = p; (*q != ':') && (*q != '\0'); q++)
            ;
        if (q == p)             /* empty PATH component => "." */
            pathv[i] = "./";
        else {
            int addSlash = ((*(q - 1)) != '/');
            pathv[i] = NEW(char, q - p + addSlash + 1);
            memcpy(pathv[i], p, q - p);
            if (addSlash)
                pathv[i][q - p] = '/';
            pathv[i][q - p + addSlash] = '\0';
        }
    }
    return (const char * const *) pathv;
}

/**
 * Cached value of JVM's effective PATH.
 * (We don't support putenv("PATH=...") in native code)
 */
static const char *parentPath;

/**
 * Split, canonicalized version of parentPath
 */
static const char * const *parentPathv;

static jfieldID field_exitcode;

JNIEXPORT void JNICALL
Java_java_lang_UNIXProcess_initIDs(JNIEnv *env, jclass clazz)
{
    field_exitcode = (*env)->GetFieldID(env, clazz, "exitcode", "I");

    parentPath  = effectivePath();
    parentPathv = splitPath(env, parentPath);

    setSIGCHLDHandler(env);
}


#ifndef WIFEXITED
#define WIFEXITED(status) (((status)&0xFF) == 0)
#endif

#ifndef WEXITSTATUS
#define WEXITSTATUS(status) (((status)>>8)&0xFF)
#endif

#ifndef WIFSIGNALED
#define WIFSIGNALED(status) (((status)&0xFF) > 0 && ((status)&0xFF00) == 0)
#endif

#ifndef WTERMSIG
#define WTERMSIG(status) ((status)&0x7F)
#endif

/* Block until a child process exits and return its exit code.
   Note, can only be called once for any given pid. */
JNIEXPORT jint JNICALL
Java_java_lang_UNIXProcess_waitForProcessExit(JNIEnv* env,
                                              jobject junk,
                                              jint pid)
{
    /* We used to use waitid() on Solaris, waitpid() on Linux, but
     * waitpid() is more standard, so use it on all POSIX platforms. */
    int status;
    /* Wait for the child process to exit.  This returns immediately if
       the child has already exited. */
    while (waitpid(pid, &status, 0) < 0) {
        switch (errno) {
        case ECHILD: return 0;
        case EINTR: break;
        default: return -1;
        }
    }

    if (WIFEXITED(status)) {
        /*
         * The child exited normally; get its exit code.
         */
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        /* The child exited because of a signal.
         * The best value to return is 0x80 + signal number,
         * because that is what all Unix shells do, and because
         * it allows callers to distinguish between process exit and
         * process death by signal.
         * Unfortunately, the historical behavior on Solaris is to return
         * the signal number, and we preserve this for compatibility. */
#ifdef __solaris__
        return WTERMSIG(status);
#else
        return 0x80 + WTERMSIG(status);
#endif
    } else {
        /*
         * Unknown exit code; pass it through.
         */
        return status;
    }
}

static int
isAsciiDigit(char c)
{
  return c >= '0' && c <= '9';
}

static int
closeDescriptors(void)
{
    DIR *dp;
    struct dirent64 *dirp;
    int from_fd = FAIL_FILENO + 1;

    /* We're trying to close all file descriptors, but opendir() might
     * itself be implemented using a file descriptor, and we certainly
     * don't want to close that while it's in use.  We assume that if
     * opendir() is implemented using a file descriptor, then it uses
     * the lowest numbered file descriptor, just like open().  So we
     * close a couple explicitly.  */

    close(from_fd);             /* for possible use by opendir() */
    close(from_fd + 1);         /* another one for good luck */

    if ((dp = opendir("/proc/self/fd")) == NULL)
        return 0;

    /* We use readdir64 instead of readdir to work around Solaris bug
     * 6395699: /proc/self/fd fails to report file descriptors >= 1024 on Solaris 9
     */
    while ((dirp = readdir64(dp)) != NULL) {
        int fd;
        if (isAsciiDigit(dirp->d_name[0]) &&
            (fd = strtol(dirp->d_name, NULL, 10)) >= from_fd + 2)
            close(fd);
    }

    closedir(dp);

    return 1;
}

static void
moveDescriptor(int fd_from, int fd_to)
{
    if (fd_from != fd_to) {
        dup2(fd_from, fd_to);
        close(fd_from);
    }
}

static const char *
getBytes(JNIEnv *env, jbyteArray arr)
{
    return arr == NULL ? NULL :
        (const char*) (*env)->GetByteArrayElements(env, arr, NULL);
}

static void
releaseBytes(JNIEnv *env, jbyteArray arr, const char* parr)
{
    if (parr != NULL)
        (*env)->ReleaseByteArrayElements(env, arr, (jbyte*) parr, JNI_ABORT);
}

static void
initVectorFromBlock(const char**vector, const char* block, int count)
{
    int i;
    const char *p;
    for (i = 0, p = block; i < count; i++) {
        /* Invariant: p always points to the start of a C string. */
        vector[i] = p;
        while (*(p++));
    }
    vector[count] = NULL;
}

static void
throwIOException(JNIEnv *env, int errnum, const char *defaultDetail)
{
    static const char * const format = "error=%d, %s";
    const char *detail = defaultDetail;
    char *errmsg;
    jstring s;

    if (errnum != 0) {
        const char *s = strerror(errnum);
        if (strcmp(s, "Unknown error") != 0)
            detail = s;
    }
    /* ASCII Decimal representation uses 2.4 times as many bits as binary. */
    errmsg = NEW(char, strlen(format) + strlen(detail) + 3 * sizeof(errnum));
    sprintf(errmsg, format, errnum, detail);
    s = JNU_NewStringPlatform(env, errmsg);
    if (s != NULL) {
        jobject x = JNU_NewObjectByName(env, "java/io/IOException",
                                        "(Ljava/lang/String;)V", s);
        if (x != NULL)
            (*env)->Throw(env, x);
    }
    free(errmsg);
}

#ifdef DEBUG_PROCESS
/* Debugging process code is difficult; where to write debug output? */
static void
debugPrint(char *format, ...)
{
    FILE *tty = fopen("/dev/tty", "w");
    va_list ap;
    va_start(ap, format);
    vfprintf(tty, format, ap);
    va_end(ap);
    fclose(tty);
}
#endif /* DEBUG_PROCESS */

/* Version of execvpe when child's PATH differs from parent's */
static int
execvp_usingParentPath(const char *file, const char *const argv[])
{
    char expanded_file[PATH_MAX];
    int filelen = strlen(file);
    int sticky_errno = 0;
    const char * const * dirs;
    /* Search parent's PATH */
    for (dirs = parentPathv; *dirs; dirs++) {
        const char * dir = *dirs;
        int dirlen = strlen(dir);
        if (filelen + dirlen + 1 >= PATH_MAX) {
            /* Resist the urge to remove this limit;
             * calling malloc after fork is unsafe. */
            errno = ENAMETOOLONG;
            continue;
        }
        strcpy(expanded_file, dir);
        strcpy(expanded_file + dirlen, file);
        execvp(expanded_file, (char **) argv);
        /* There are 3 responses to various classes of errno:
         * return immediately, continue (especially for ENOENT),
         * or continue with "sticky" errno.
         *
         * From exec(3):
         *
         * If permission is denied for a file (the attempted
         * execve returned EACCES), these functions will continue
         * searching the rest of the search path.  If no other
         * file is found, however, they will return with the
         * global variable errno set to EACCES.
         */
        switch (errno) {
        case EACCES:
            sticky_errno = errno;
            /* FALLTHRU */
        case ENOENT:
        case ENOTDIR:
#ifdef ELOOP
        case ELOOP:
#endif
#ifdef ESTALE
        case ESTALE:
#endif
#ifdef ENODEV
        case ENODEV:
#endif
#ifdef ETIMEDOUT
        case ETIMEDOUT:
#endif
            break; /* Try other directories in PATH */
        default:
            return -1;
        }
    }
    if (sticky_errno != 0)
        errno = sticky_errno;
    return -1;
}

/* execvpe should have been included in the Unix standards. */
static int
execvpe(const char *file, const char *const argv[], const char *const envp[])
{
    /* This is one of the rare times it's more portable to declare an
     * external symbol explicitly, rather than via a system header.
     * The declaration is standardized as part of UNIX98, but there is
     * no standard (not even de-facto) header file where the
     * declaration is to be found.  See:
     * http://www.opengroup.org/onlinepubs/009695399/functions/environ.html
     * http://www.opengroup.org/onlinepubs/009695399/functions/xsh_chap02_02.html
     *
     * "All identifiers in this volume of IEEE Std 1003.1-2001, except
     * environ, are defined in at least one of the headers" (!)
     */
    extern char **environ;

    if (envp != NULL)
        environ = (char **) envp;

    if (/* Parent and child environment the same?  Use child PATH. */
        (envp == NULL)

        /* http://www.opengroup.org/onlinepubs/009695399/functions/exec.html
         * "If the file argument contains a slash character, it is used as
         * the pathname for this file.  Otherwise, the path prefix for this
         * file is obtained by a search of the directories passed in the
         * PATH environment variable" */
        || (strchr(file, '/') != NULL)

        /* Parent and child PATH the same?  Use child PATH. */
        || (strcmp(parentPath, effectivePath()) == 0)

        /* We want ENOENT, not EACCES, for zero-length program names. */
        || (*file == '\0'))

        return execvp(file, (char **) argv);
    else
        return execvp_usingParentPath(file, argv);
}

static void
closeSafely(int fd)
{
    if (fd != -1)
        close(fd);
}

/*
 * Reads nbyte bytes from file descriptor fd into buf,
 * The read operation is retried in case of EINTR or partial reads.
 *
 * Returns number of bytes read (normally nbyte, but may be less in
 * case of EOF).  In case of read errors, returns -1 and sets errno.
 */
static ssize_t
readFully(int fd, void *buf, size_t nbyte)
{
    ssize_t remaining = nbyte;
    for (;;) {
        ssize_t n = read(fd, buf, remaining);
        if (n == 0) {
            return nbyte - remaining;
        } else if (n > 0) {
            remaining -= n;
            if (remaining <= 0)
                return nbyte;
            /* We were interrupted in the middle of reading the bytes.
             * Unlikely, but possible. */
            buf = (void *) (((char *)buf) + n);
        } else if (errno == EINTR) {
            /* Strange signals like SIGJVM1 are possible at any time.
             * See http://www.dreamsongs.com/WorseIsBetter.html */
        } else {
            return -1;
        }
    }
}

#ifndef __solaris__
#undef fork1
#define fork1() fork()
#endif

JNIEXPORT jint JNICALL
Java_java_lang_UNIXProcess_forkAndExec(JNIEnv *env,
                                       jobject process,
                                       jbyteArray prog,
                                       jbyteArray argBlock, jint argc,
                                       jbyteArray envBlock, jint envc,
                                       jbyteArray dir,
                                       jintArray std_fds,
                                       jboolean redirectErrorStream)
{
    int errnum;
    int resultPid = -1;
    int in[2], out[2], err[2], fail[2];
    const char **argv = NULL;
    const char **envv = NULL;
    const char *pprog     = getBytes(env, prog);
    const char *pargBlock = getBytes(env, argBlock);
    const char *penvBlock = getBytes(env, envBlock);
    const char *pdir      = getBytes(env, dir);
    jint *fds = NULL;

    in[0] = in[1] = out[0] = out[1] = err[0] = err[1] = fail[0] = fail[1] = -1;

    assert(prog != NULL && argBlock != NULL);
    if (pprog     == NULL) goto Catch;
    if (pargBlock == NULL) goto Catch;
    if (envBlock  != NULL && penvBlock == NULL) goto Catch;
    if (dir       != NULL && pdir      == NULL) goto Catch;

    /* Convert pprog + pargBlock into a char ** argv */
    if ((argv = NEW(const char *, argc + 2)) == NULL)
        goto Catch;
    argv[0] = pprog;
    initVectorFromBlock(argv+1, pargBlock, argc);

    if (envBlock != NULL) {
        /* Convert penvBlock into a char ** envv */
        if ((envv = NEW(const char *, envc + 1)) == NULL)
            goto Catch;
        initVectorFromBlock(envv, penvBlock, envc);
    }

    assert(std_fds != NULL);
    fds = (*env)->GetIntArrayElements(env, std_fds, NULL);
    if (fds == NULL) goto Catch;

    if ((fds[0] == -1 && pipe(in)  < 0) ||
        (fds[1] == -1 && pipe(out) < 0) ||
        (fds[2] == -1 && pipe(err) < 0) ||
        (pipe(fail) < 0)) {
        throwIOException(env, errno, "Bad file descriptor");
        goto Catch;
    }

    resultPid = fork1();
    if (resultPid < 0) {
        throwIOException(env, errno, "Fork failed");
        goto Catch;
    }

    if (resultPid == 0) {
        /* Child process */

        /* Close the parent sides of the pipes.
           Closing pipe fds here is redundant, since closeDescriptors()
           would do it anyways, but a little paranoia is a good thing. */
        closeSafely(in[1]);
        closeSafely(out[0]);
        closeSafely(err[0]);
        closeSafely(fail[0]);

        /* Give the child sides of the pipes the right fileno's. */
        /* Note: it is possible for in[0] == 0 */
        moveDescriptor(in[0] != -1 ?  in[0] : fds[0], STDIN_FILENO);
        moveDescriptor(out[1]!= -1 ? out[1] : fds[1], STDOUT_FILENO);

        if (redirectErrorStream) {
            closeSafely(err[1]);
            dup2(STDOUT_FILENO, STDERR_FILENO);
        } else {
            moveDescriptor(err[1] != -1 ? err[1] : fds[2], STDERR_FILENO);
        }

        moveDescriptor(fail[1], FAIL_FILENO);

        /* close everything */
        if (closeDescriptors() == 0) { /* failed,  close the old way */
            int max_fd = (int)sysconf(_SC_OPEN_MAX);
            int i;
            for (i = FAIL_FILENO + 1; i < max_fd; i++)
                close(i);
        }

        /* change to the new working directory */
        if (pdir != NULL && chdir(pdir) < 0)
            goto WhyCantJohnnyExec;

        if (fcntl(FAIL_FILENO, F_SETFD, FD_CLOEXEC) == -1)
            goto WhyCantJohnnyExec;

        execvpe(argv[0], argv, envv);

    WhyCantJohnnyExec:
        /* We used to go to an awful lot of trouble to predict whether the
         * child would fail, but there is no reliable way to predict the
         * success of an operation without *trying* it, and there's no way
         * to try a chdir or exec in the parent.  Instead, all we need is a
         * way to communicate any failure back to the parent.  Easy; we just
         * send the errno back to the parent over a pipe in case of failure.
         * The tricky thing is, how do we communicate the *success* of exec?
         * We use FD_CLOEXEC together with the fact that a read() on a pipe
         * yields EOF when the write ends (we have two of them!) are closed.
         */
        errnum = errno;
        write(FAIL_FILENO, &errnum, sizeof(errnum));
        close(FAIL_FILENO);
        _exit(-1);
    }

    /* parent process */

    close(fail[1]); fail[1] = -1; /* See: WhyCantJohnnyExec */

    switch (readFully(fail[0], &errnum, sizeof(errnum))) {
    case 0: break; /* Exec succeeded */
    case sizeof(errnum):
        waitpid(resultPid, NULL, 0);
        throwIOException(env, errnum, "Exec failed");
        goto Catch;
    default:
        throwIOException(env, errno, "Read failed");
        goto Catch;
    }

    fds[0] = (in [1] != -1) ? in [1] : -1;
    fds[1] = (out[0] != -1) ? out[0] : -1;
    fds[2] = (err[0] != -1) ? err[0] : -1;

 Finally:
    /* Always clean up the child's side of the pipes */
    closeSafely(in [0]);
    closeSafely(out[1]);
    closeSafely(err[1]);

    /* Always clean up fail descriptors */
    closeSafely(fail[0]);
    closeSafely(fail[1]);

    free(argv);
    free(envv);

    releaseBytes(env, prog,     pprog);
    releaseBytes(env, argBlock, pargBlock);
    releaseBytes(env, envBlock, penvBlock);
    releaseBytes(env, dir,      pdir);

    if (fds != NULL)
        (*env)->ReleaseIntArrayElements(env, std_fds, fds, 0);

    return resultPid;

 Catch:
    /* Clean up the parent's side of the pipes in case of failure only */
    closeSafely(in [1]);
    closeSafely(out[0]);
    closeSafely(err[0]);
    goto Finally;
}

JNIEXPORT void JNICALL
Java_java_lang_UNIXProcess_destroyProcess(JNIEnv *env, jobject junk, jint pid)
{
    kill(pid, SIGTERM);
}
