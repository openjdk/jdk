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
#include <sys/wait.h>
#include <signal.h>
#include <string.h>
#include <fcntl.h>
#include <stdbool.h>
#include <unistd.h>
#include <spawn.h>

#include "childproc.h"
#include "childproc_errorcodes.h"

/*
 *
 * When starting a child on Unix, we need to do three things:
 * - fork off
 * - in the child process, do some pre-exec work: duping/closing file
 *   descriptors to set up stdio-redirection, setting environment variables,
 *   changing paths...
 * - then exec(2) the target binary
 *
 * On the OS-side are three ways to fork off, but we only use two of them:
 *
 * A) fork(2). Portable and safe (no side effects) but could fail on very ancient
 *    Unices that don't employ COW on fork(2). The modern platforms we support
 *    (Linux, MacOS, AIX) all do. It may have a small performance penalty compared
 *    to modern posix_spawn(3) implementations - see below.
 *    fork(2) can be used by specifying -Djdk.lang.Process.launchMechanism=FORK when starting
 *    the (parent process) JVM.
 *
 * B) vfork(2): Portable and fast but very unsafe. For details, see JDK-8357090.
 *    We supported this mode in older releases but removed support for it in JDK 27.
 *    Modern posix_spawn(3) implementations use techniques similar to vfork(2), but
 *    in a much safer way
 *
 * C) posix_spawn(3): Where fork/vfork/clone all fork off the process and leave
 *    pre-exec work and calling exec(2) to the user, posix_spawn(3) offers the user
 *    fork+exec-like functionality in one package, similar to CreateProcess() on Windows.
 *    It is not a system call, but a wrapper implemented in user-space libc in terms
 *    of one of (fork|vfork|clone)+exec - so whether or not it has advantages over calling
 *    the naked (fork|vfork|clone) functions depends on how posix_spawn(3) is implemented.
 *    Modern posix_spawn(3) implementations, on Linux, use clone(2) with CLONE_VM | CLONE_VFORK,
 *    giving us the best ratio between performance and safety.
 *    Note however, that posix_spawn(3) can be buggy, depending on the libc implementation.
 *    E.g., on MacOS, it is still fully not POSIX-compliant. Therefore, we need to retain the
 *    FORK mode as a backup.
 *    Posix_spawn mode is used by default, but can be explicitly enabled using
 *    -Djdk.lang.Process.launchMechanism=POSIX_SPAWN when starting the (parent process) JVM.
 *
 * Note that when using posix_spawn(3), we exec twice: first a tiny binary called
 * the jspawnhelper, then in the jspawnhelper we do the pre-exec work and exec a
 * second time, this time the target binary (similar to the "exec-twice-technique"
 * described in https://mail.openjdk.org/pipermail/core-libs-dev/2018-September/055333.html).
 *
 * This is a JDK-specific implementation detail which just happens to be
 * implemented for jdk.lang.Process.launchMechanism=POSIX_SPAWN.
 *
 * --- Linux-specific ---
 *
 * How does glibc implement posix_spawn?
 *
 * Before glibc 2.4 (released 2006), posix_spawn(3) used just fork(2)/exec(2). From
 * glibc 2.4 up to and including 2.23, it used either fork(2) or vfork(2). None of these
 * versions still matter.
 *
 * Since glibc >= 2.24, glibc uses clone+exec with CLONE_VM | CLONE_VFORK to emulate vfork
 * performance but without the inherent dangers (we run inside the parent's memory image
 * and stop the parent for as long as it takes the child process to exec).
 *
 * ---
 *
 * How does muslc implement posix_spawn?
 *
 * They always did use the clone (.. CLONE_VM | CLONE_VFORK ...)
 * technique. So we are safe to use posix_spawn() here regardless of muslc
 * version.
 *
 * </Linux-specific>
 *
 * Based on the above analysis, we are currently defaulting to posix_spawn()
 * on all Unices including Linux.
 */

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
     * https://pubs.opengroup.org/onlinepubs/7908799/xsh/exec.html
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
    return ":/bin:/usr/bin";
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
effectivePathv(JNIEnv *env)
{
    char *p;
    int i;
    const char *path = effectivePath();
    int count = countOccurrences(path, ':') + 1;
    size_t pathvsize = sizeof(const char *) * (count+1);
    size_t pathsize = strlen(path) + 1;
    const char **pathv = (const char **) xmalloc(env, pathvsize + pathsize);

    if (pathv == NULL)
        return NULL;
    p = (char *) pathv + pathvsize;
    memcpy(p, path, pathsize);
    /* split PATH by replacing ':' with NULs; empty components => "." */
    for (i = 0; i < count; i++) {
        char *q = p + strcspn(p, ":");
        pathv[i] = (p == q) ? "." : p;
        *q = '\0';
        p = q + 1;
    }
    pathv[count] = NULL;
    return pathv;
}

JNIEXPORT void JNICALL
Java_java_lang_ProcessImpl_init(JNIEnv *env, jclass clazz)
{
    parentPathv = effectivePathv(env);
    CHECK_NULL(parentPathv);
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

#ifndef VERSION_STRING
#error VERSION_STRING must be defined
#endif

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

#define IOE_FORMAT "%s, error: %d (%s) %s"

#define SPAWN_HELPER_INTERNAL_ERROR_MSG "\n" \
  "Possible reasons:\n" \
  "  - Spawn helper ran into JDK version mismatch\n" \
  "  - Spawn helper ran into unexpected internal error\n" \
  "  - Spawn helper was terminated by another process\n" \
  "Possible solutions:\n" \
  "  - Restart JVM, especially after in-place JDK updates\n" \
  "  - Check system logs for JDK-related errors\n" \
  "  - Re-install JDK to fix permission/versioning problems\n" \
  "  - Switch to legacy launch mechanism with -Djdk.lang.Process.launchMechanism=FORK\n"

static void
throwIOExceptionImpl(JNIEnv *env, int errnum, const char *externalDetail, const char *internalDetail)
{
    const char *errorDetail;
    char *errmsg;
    size_t fmtsize;
    char tmpbuf[1024];
    jstring s;

    if (errnum != 0) {
        int ret = getErrorString(errnum, tmpbuf, sizeof(tmpbuf));
        if (ret != EINVAL) {
            errorDetail = tmpbuf;
        } else {
            errorDetail = "unknown";
        }
    } else {
        errorDetail = "none";
    }

    /* ASCII Decimal representation uses 2.4 times as many bits as binary. */
    fmtsize = sizeof(IOE_FORMAT) + strlen(externalDetail) + 3 * sizeof(errnum) + strlen(errorDetail) +  strlen(internalDetail) + 1;
    errmsg = NEW(char, fmtsize);
    if (errmsg == NULL)
        return;

    snprintf(errmsg, fmtsize, IOE_FORMAT, externalDetail, errnum, errorDetail, internalDetail);
    s = JNU_NewStringPlatform(env, errmsg);
    if (s != NULL) {
        jobject x = JNU_NewObjectByName(env, "java/io/IOException",
                                        "(Ljava/lang/String;)V", s);
        if (x != NULL)
            (*env)->Throw(env, x);
    }
    free(errmsg);
}

/**
 * Throws IOException that signifies an internal error, e.g. spawn helper failure.
 */
static void
throwInternalIOException(JNIEnv *env, int errnum, const char *externalDetail, int mode)
{
  switch (mode) {
    case MODE_POSIX_SPAWN:
      throwIOExceptionImpl(env, errnum, externalDetail, SPAWN_HELPER_INTERNAL_ERROR_MSG);
      break;
    default:
      throwIOExceptionImpl(env, errnum, externalDetail, "");
  }
}

/**
 * Throws IOException that signifies a normal error.
 */
static void
throwIOException(JNIEnv *env, int errnum, const char *externalDetail)
{
  throwIOExceptionImpl(env, errnum, externalDetail, "");
}

/**
 * Throws an IOException with a message composed from the result of waitpid status.
 */
static void throwExitCause(JNIEnv *env, int pid, int status, int mode) {
    char ebuf[128];
    if (WIFEXITED(status)) {
        snprintf(ebuf, sizeof ebuf,
            "Failed to exec spawn helper: pid: %d, exit code: %d",
            pid, WEXITSTATUS(status));
    } else if (WIFSIGNALED(status)) {
        snprintf(ebuf, sizeof ebuf,
            "Failed to exec spawn helper: pid: %d, signal: %d",
            pid, WTERMSIG(status));
    } else {
        snprintf(ebuf, sizeof ebuf,
            "Failed to exec spawn helper: pid: %d, status: 0x%08x",
            pid, status);
    }
    throwInternalIOException(env, 0, ebuf, mode);
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

static void
copyPipe(int from[2], int to[2])
{
    to[0] = from[0];
    to[1] = from[1];
}

/* arg is an array of pointers to 0 terminated strings. array is terminated
 * by a null element.
 *
 * *nelems and *nbytes receive the number of elements of array (incl 0)
 * and total number of bytes (incl. 0)
 * Note. An empty array will have one null element
 * But if arg is null, then *nelems set to 0, and *nbytes to 0
 */
static void arraysize(const char * const *arg, int *nelems, int *nbytes)
{
    int bytes, count;
    const char * const *a = arg;
    if (arg == 0) {
        *nelems = 0;
        *nbytes = 0;
        return;
    }
    /* count the array elements and number of bytes */
    for (count=0, bytes=0; *a != 0; count++, a++) {
        bytes += strlen(*a)+1;
    }
    *nbytes = bytes;
    *nelems = count+1;
}

/* copy the strings from arg[] into buf, starting at given offset
 * return new offset to next free byte
 */
static int copystrings(char *buf, int offset, const char * const *arg) {
    char *p;
    const char * const *a;
    int count=0;

    if (arg == 0) {
        return offset;
    }
    for (p=buf+offset, a=arg; *a != 0; a++) {
        int len = strlen(*a) +1;
        memcpy(p, *a, len);
        p += len;
        count += len;
    }
    return offset+count;
}

/**
 * We are unusually paranoid; use of vfork is
 * especially likely to tickle gcc/glibc bugs.
 */
#ifdef __attribute_noinline__  /* See: sys/cdefs.h */
__attribute_noinline__
#endif

static pid_t
forkChild(ChildStuff *c) {
    pid_t resultPid;

    /*
     * From Solaris fork(2): In Solaris 10, a call to fork() is
     * identical to a call to fork1(); only the calling thread is
     * replicated in the child process. This is the POSIX-specified
     * behavior for fork().
     */
    resultPid = fork();

    if (resultPid == 0) {
        childProcess(c);
    }
    assert(resultPid != 0);  /* childProcess never returns */
    return resultPid;
}

/* Given two fds, one of which has to be -1, the other one has to be valid,
 * return the valid one. */
static int eitherOneOf(int fd1, int fd2) {
    if (fd2 == -1) {
        assert(fdIsValid(fd1));
        return fd1;
    }
    assert(fd1 == -1);
    assert(fdIsValid(fd2));
    return fd2;
}

static int call_posix_spawn_file_actions_adddup2(posix_spawn_file_actions_t *file_actions, int filedes, int newfiledes) {
#ifdef __APPLE__
    /* MacOS is not POSIX-compliant: dup2 file actions specifying the same fd as source and destination
     * should be handled as no-op according to spec, but they cause EBADF. */
    if (filedes == newfiledes) {
        return 0;
    }
#endif
    return posix_spawn_file_actions_adddup2(file_actions, filedes, newfiledes);
}

static pid_t
spawnChild(JNIEnv *env, jobject process, ChildStuff *c, const char *helperpath) {
    pid_t resultPid;
    int offset, rval, bufsize, magic;
    char* buf;
    char* hlpargs[3];
    SpawnInfo sp;
    posix_spawn_file_actions_t file_actions;
    int child_stdin, child_stdout, child_stderr, child_childenv, child_fail = -1;

    /* NULL-terminated argv array.
     * argv[0] contains path to jspawnhelper, to follow conventions.
     * argv[1] contains the version string as argument to jspawnhelper
     */
    hlpargs[0] = (char*)helperpath;
    hlpargs[1] = VERSION_STRING;
    hlpargs[2] = NULL;

    /* Following items are sent down the pipe to the helper
     * after it is spawned.
     * All strings are null terminated. All arrays of strings
     * have an empty string for termination.
     * - the ChildStuff struct
     * - the SpawnInfo struct
     * - the argv strings array
     * - the envv strings array
     * - the home directory string
     * - the parentPath string
     * - the parentPathv array
     */
    /* First calculate the sizes */
    arraysize(c->argv, &sp.nargv, &sp.argvBytes);
    bufsize = sp.argvBytes;
    arraysize(c->envv, &sp.nenvv, &sp.envvBytes);
    bufsize += sp.envvBytes;
    sp.dirlen = c->pdir == 0 ? 0 : strlen(c->pdir)+1;
    bufsize += sp.dirlen;
    arraysize(parentPathv, &sp.nparentPathv, &sp.parentPathvBytes);
    bufsize += sp.parentPathvBytes;

    /* Prepare file descriptors for jspawnhelper and the target binary. */

    /* 0: copy of either "in" pipe read fd or the stdin redirect fd */
    child_stdin = eitherOneOf(c->fds[0], c->in[0]);

    /* 1: copy of either "out" pipe write fd or the stdout redirect fd */
    child_stdout = eitherOneOf(c->fds[1], c->out[1]);

    /* 2: redirectErrorStream=1: redirected to child's stdout (Order matters!)
     *    redirectErrorStream=0: copy of either "err" pipe write fd or stderr redirect fd. */
    if (c->redirectErrorStream) {
        child_stderr = STDOUT_FILENO; /* Note: this refers to the future stdout in the child process */
    } else {
        child_stderr = eitherOneOf(c->fds[2], c->err[1]);
    }

    /* 3: copy of the "fail" pipe write fd */
    child_fail = c->fail[1];

    /* 4: copy of the "childenv" pipe read end */
    child_childenv = c->childenv[0];

    assert(fdIsValid(child_stdin));
    assert(fdIsValid(child_stdout));
    assert(fdIsValid(child_stderr));
    assert(fdIsPipe(child_fail));
    assert(fdIsPipe(child_childenv));
    /* This must always hold true, unless someone deliberately closed 0, 1, or 2 in the parent JVM. */
    assert(child_fail > STDERR_FILENO);
    assert(child_childenv > STDERR_FILENO);

    /* Slot in dup2 file actions. */
    posix_spawn_file_actions_init(&file_actions);

#ifdef __APPLE__
    /* On MacOS, posix_spawn does not behave in a POSIX-conform way in that the
     * kernel closes CLOEXEC file descriptors too early for dup2 file actions to
     * copy them after the fork. We have to explicitly prevent that by calling a
     * propietary API. */
    posix_spawn_file_actions_addinherit_np(&file_actions, child_stdin);
    posix_spawn_file_actions_addinherit_np(&file_actions, child_stdout);
    posix_spawn_file_actions_addinherit_np(&file_actions, child_stderr);
    posix_spawn_file_actions_addinherit_np(&file_actions, child_fail);
    posix_spawn_file_actions_addinherit_np(&file_actions, child_childenv);
#endif

    /* First dup2 stdin/out/err to 0,1,2. After this, we can safely dup2 over the
     * original stdin/out/err. */
    if (call_posix_spawn_file_actions_adddup2(&file_actions, child_stdin, STDIN_FILENO) != 0 ||
        call_posix_spawn_file_actions_adddup2(&file_actions, child_stdout, STDOUT_FILENO) != 0 ||
        /* Order matters: stderr may be redirected to stdout, so this dup2 must happen after the stdout one. */
        call_posix_spawn_file_actions_adddup2(&file_actions, child_stderr, STDERR_FILENO) != 0)
    {
        return -1;
    }

    /* We dup2 with one intermediary step to prevent accidentally dup2'ing over child_childenv. */
    const int tmp_child_childenv = child_fail < 10 ? 10 : child_fail - 1;
    if (call_posix_spawn_file_actions_adddup2(&file_actions, child_childenv, tmp_child_childenv) != 0 ||
        call_posix_spawn_file_actions_adddup2(&file_actions, child_fail, FAIL_FILENO) != 0 ||
        call_posix_spawn_file_actions_adddup2(&file_actions, tmp_child_childenv, CHILDENV_FILENO) != 0)
    {
        return -1;
    }

    /* Since we won't use these in jspawnhelper, reset them all */
    c->in[0] = c->in[1] = c->out[0] = c->out[1] =
    c->err[0] = c->err[1] = c->fail[0] = c->fail[1] =
    c->fds[0] = c->fds[1] = c->fds[2] = -1;
    c->redirectErrorStream = false;

    rval = posix_spawn(&resultPid, helperpath, &file_actions, 0, (char * const *) hlpargs, environ);

    if (rval != 0) {
        return -1;
    }

#ifdef DEBUG
    jtregSimulateCrash(resultPid, 1);
#endif

    /* now the lengths are known, copy the data */
    buf = NEW(char, bufsize);
    if (buf == 0) {
        return -1;
    }
    offset = copystrings(buf, 0, &c->argv[0]);
    if (c->envv != NULL) {
        offset = copystrings(buf, offset, &c->envv[0]);
    }
    if (c->pdir != NULL) {
        if (sp.dirlen > 0) {
            memcpy(buf+offset, c->pdir, sp.dirlen);
            offset += sp.dirlen;
        }
    } else {
        if (sp.dirlen > 0) {
            free(buf);
            return -1;
        }
    }
    offset = copystrings(buf, offset, parentPathv);
    assert(offset == bufsize);

    magic = magicNumber();

    /* write the two structs and the data buffer */
    if (writeFully(c->childenv[1], (char *)&magic, sizeof(magic)) != sizeof(magic)) { // magic number first
        free(buf);
        return -1;
    }
#ifdef DEBUG
    jtregSimulateCrash(resultPid, 2);
#endif
    if (writeFully(c->childenv[1], (char *)c, sizeof(*c)) != sizeof(*c) ||
        writeFully(c->childenv[1], (char *)&sp, sizeof(sp)) != sizeof(sp) ||
        writeFully(c->childenv[1], buf, bufsize) != bufsize) {
        free(buf);
        return -1;
    }
    /* We're done. Let jspwanhelper know he can't expect any more data from us. */
    close(c->childenv[1]);
    c->childenv[1] = -1;
    free(buf);
#ifdef DEBUG
    jtregSimulateCrash(resultPid, 3);
#endif

    /* In this mode an external main() in invoked which calls back into
     * childProcess() in this file, rather than directly
     * via the statement below */
    return resultPid;
}

/*
 * Start a child process running function childProcess.
 * This function only returns in the parent.
 */
static pid_t
startChild(JNIEnv *env, jobject process, ChildStuff *c, const char *helperpath) {
    switch (c->mode) {
      case MODE_FORK:
        return forkChild(c);
      case MODE_POSIX_SPAWN:
        return spawnChild(env, process, c, helperpath);
      default:
        return -1;
    }
}

static int pipeSafely(int fd[2]) {
    /* Pipe filedescriptors must be CLOEXEC as early as possible - ideally from the point of
     * creation on - since at any moment a concurrent (third-party) fork() could inherit copies
     * of these descriptors and accidentally keep the pipes open. That could cause the parent
     * process to hang (see e.g. JDK-8377907).
     * We use pipe2(2), if we have it. If we don't, we use pipe(2) + fcntl(2) immediately.
     * The latter is still racy and can therefore still cause hangs as described in JDK-8377907,
     * but at least the dangerous time window is as short as we can make it.
     */
    int rc = -1;
#ifdef HAVE_PIPE2
    rc = pipe2(fd, O_CLOEXEC);
#else
    rc = pipe(fd);
    if (rc == 0) {
        fcntl(fd[0], F_SETFD, FD_CLOEXEC);
        fcntl(fd[1], F_SETFD, FD_CLOEXEC);
    }
#endif /* HAVE_PIPE2 */
    assert(fdIsCloexec(fd[0]));
    assert(fdIsCloexec(fd[1]));
    return rc;
}

JNIEXPORT jint JNICALL
Java_java_lang_ProcessImpl_forkAndExec(JNIEnv *env,
                                       jobject process,
                                       jint mode,
                                       jbyteArray helperpath,
                                       jbyteArray prog,
                                       jbyteArray argBlock, jint argc,
                                       jbyteArray envBlock, jint envc,
                                       jbyteArray dir,
                                       jintArray std_fds,
                                       jboolean redirectErrorStream)
{
    int resultPid = -1;
    int in[2], out[2], err[2], fail[2], childenv[2];
    jint *fds = NULL;
    const char *phelperpath = NULL;
    const char *pprog = NULL;
    const char *pargBlock = NULL;
    const char *penvBlock = NULL;
    ChildStuff *c;

    in[0] = in[1] = out[0] = out[1] = err[0] = err[1] = fail[0] = fail[1] = -1;
    childenv[0] = childenv[1] = -1;
    // Reset errno to protect against bogus error messages
    errno = 0;

    if ((c = NEW(ChildStuff, 1)) == NULL) return -1;
    c->argv = NULL;
    c->envv = NULL;
    c->pdir = NULL;

    /* Convert prog + argBlock into a char ** argv.
     * Add one word room for expansion of argv for use by
     * execve_as_traditional_shell_script.
     * This word is also used when using posix_spawn mode
     */
    assert(prog != NULL && argBlock != NULL);
    if ((phelperpath = getBytes(env, helperpath))   == NULL) goto Catch;
    if ((pprog       = getBytes(env, prog))         == NULL) goto Catch;
    if ((pargBlock   = getBytes(env, argBlock))     == NULL) goto Catch;
    if ((c->argv     = NEW(const char *, argc + 3)) == NULL) goto Catch;
    c->argv[0] = pprog;
    c->argc = argc + 2;
    initVectorFromBlock(c->argv+1, pargBlock, argc);

    if (envBlock != NULL) {
        /* Convert envBlock into a char ** envv */
        if ((penvBlock = getBytes(env, envBlock))   == NULL) goto Catch;
        if ((c->envv = NEW(const char *, envc + 1)) == NULL) goto Catch;
        initVectorFromBlock(c->envv, penvBlock, envc);
    }

    if (dir != NULL) {
        if ((c->pdir = getBytes(env, dir)) == NULL) goto Catch;
    }

    assert(std_fds != NULL);
    fds = (*env)->GetIntArrayElements(env, std_fds, NULL);
    if (fds == NULL) goto Catch;

    if ((fds[0] == -1 && pipeSafely(in)  < 0) ||
        (fds[1] == -1 && pipeSafely(out) < 0) ||
        (fds[2] == -1 && !redirectErrorStream && pipeSafely(err) < 0) ||
        (pipeSafely(childenv) < 0) ||
        (pipeSafely(fail) < 0)) {
        throwInternalIOException(env, errno, "Bad file descriptor", mode);
        goto Catch;
    }

    c->fds[0] = fds[0];
    c->fds[1] = fds[1];
    c->fds[2] = fds[2];

    copyPipe(in,   c->in);
    copyPipe(out,  c->out);
    copyPipe(err,  c->err);
    copyPipe(fail, c->fail);
    copyPipe(childenv, c->childenv);

    c->redirectErrorStream = redirectErrorStream;
    c->mode = mode;

    /* In posix_spawn mode, require the child process to signal aliveness
     * right after it comes up. This is because there are implementations of
     * posix_spawn() which do not report failed exec()s back to the caller
     * (e.g. glibc, see JDK-8223777). In those cases, the fork() will have
     * worked and successfully started the child process, but the exec() will
     * have failed. There is no way for us to distinguish this from a target
     * binary just exiting right after start.
     *
     * Note that we could do this additional handshake in all modes but for
     * prudence only do it when it is needed (in posix_spawn mode). */
    c->sendAlivePing = (mode == MODE_POSIX_SPAWN) ? 1 : 0;

    resultPid = startChild(env, process, c, phelperpath);
    assert(resultPid != 0);

    if (resultPid < 0) {
        char * failMessage = "unknown";
        switch (c->mode) {
          case MODE_FORK:
            failMessage = "fork failed";
            break;
          case MODE_POSIX_SPAWN:
            failMessage = "posix_spawn failed";
            break;
        }
        throwInternalIOException(env, errno, failMessage, c->mode);
        goto Catch;
    }
    close(fail[1]); fail[1] = -1; /* See: WhyCantJohnnyExec  (childproc.c)  */

    errcode_t errcode;

    /* If we expect the child to ping aliveness, wait for it. */
    if (c->sendAlivePing) {
        switch(readFully(fail[0], &errcode, sizeof(errcode))) {
        case 0: /* First exec failed; */
            {
                int tmpStatus = 0;
                int p = waitpid(resultPid, &tmpStatus, 0);
                throwExitCause(env, p, tmpStatus, c->mode);
                goto Catch;
            }
        case sizeof(errcode):
            if (errcode.step != ESTEP_CHILD_ALIVE) {
                /* This can happen if the child process encounters an error
                 * before or during initial handshake with the parent. */
                char msg[256];
                snprintf(msg, sizeof(msg),
                         "Bad early code from spawn helper " ERRCODE_FORMAT " (Failed to exec spawn helper)",
                         ERRCODE_FORMAT_ARGS(errcode));
                throwInternalIOException(env, 0, msg, c->mode);
                goto Catch;
            }
            break;
        default:
          throwInternalIOException(env, errno, "Read failed", c->mode);
            goto Catch;
        }
    }

    switch (readFully(fail[0], &errcode, sizeof(errcode))) {
    case 0: break; /* Exec succeeded */
    case sizeof(errcode):
        /* Always reap first! */
        waitpid(resultPid, NULL, 0);
        /* Most of these errors are implementation errors and should result in an internal IOE, but
         * a few can be caused by bad user input and need to be communicated to the end user. */
        switch(errcode.step) {
        case ESTEP_CHDIR_FAIL:
            throwIOException(env, errcode.errno_, "Failed to access working directory");
            break;
        case ESTEP_EXEC_FAIL:
            throwIOException(env, errcode.errno_, "Exec failed");
            break;
        default: {
            /* Probably implementation error */
            char msg[256];
            snprintf(msg, sizeof(msg),
                     "Bad code from spawn helper " ERRCODE_FORMAT " (Failed to exec spawn helper)",
                     ERRCODE_FORMAT_ARGS(errcode));
            throwInternalIOException(env, 0, msg, c->mode);
        }
        };
        goto Catch;
    default:
        throwInternalIOException(env, errno, "Read failed", c->mode);
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

    /* Always clean up fail and childEnv descriptors */
    closeSafely(fail[0]);
    closeSafely(fail[1]);
    /* We use 'c->childenv' here rather than 'childenv' because 'spawnChild()' might have
     * already closed 'c->childenv[1]' and signaled this by setting 'c->childenv[1]' to '-1'.
     * Otherwise 'c->childenv' and 'childenv' are the same because we just copied 'childenv'
     * to 'c->childenv' (with 'copyPipe()') before calling 'startChild()'. */
    closeSafely(c->childenv[0]);
    closeSafely(c->childenv[1]);

    releaseBytes(env, helperpath, phelperpath);
    releaseBytes(env, prog,       pprog);
    releaseBytes(env, argBlock,   pargBlock);
    releaseBytes(env, envBlock,   penvBlock);
    releaseBytes(env, dir,        c->pdir);

    free(c->argv);
    free(c->envv);
    free(c);

    if (fds != NULL)
        (*env)->ReleaseIntArrayElements(env, std_fds, fds, 0);

    return resultPid;

 Catch:
    /* Clean up the parent's side of the pipes in case of failure only */
    closeSafely(in [1]); in[1] = -1;
    closeSafely(out[0]); out[0] = -1;
    closeSafely(err[0]); err[0] = -1;
    goto Finally;
}
