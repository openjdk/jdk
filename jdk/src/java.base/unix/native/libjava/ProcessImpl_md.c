/*
 * Copyright (c) 1995, 2015, Oracle and/or its affiliates. All rights reserved.
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

#if defined(__solaris__) || defined(_ALLBSD_SOURCE) || defined(_AIX)
#include <spawn.h>
#endif

#include "childproc.h"

/*
 * There are 4 possible strategies we might use to "fork":
 *
 * - fork(2).  Very portable and reliable but subject to
 *   failure due to overcommit (see the documentation on
 *   /proc/sys/vm/overcommit_memory in Linux proc(5)).
 *   This is the ancient problem of spurious failure whenever a large
 *   process starts a small subprocess.
 *
 * - vfork().  Using this is scary because all relevant man pages
 *   contain dire warnings, e.g. Linux vfork(2).  But at least it's
 *   documented in the glibc docs and is standardized by XPG4.
 *   http://www.opengroup.org/onlinepubs/000095399/functions/vfork.html
 *   On Linux, one might think that vfork() would be implemented using
 *   the clone system call with flag CLONE_VFORK, but in fact vfork is
 *   a separate system call (which is a good sign, suggesting that
 *   vfork will continue to be supported at least on Linux).
 *   Another good sign is that glibc implements posix_spawn using
 *   vfork whenever possible.  Note that we cannot use posix_spawn
 *   ourselves because there's no reliable way to close all inherited
 *   file descriptors.
 *
 * - clone() with flags CLONE_VM but not CLONE_THREAD.  clone() is
 *   Linux-specific, but this ought to work - at least the glibc
 *   sources contain code to handle different combinations of CLONE_VM
 *   and CLONE_THREAD.  However, when this was implemented, it
 *   appeared to fail on 32-bit i386 (but not 64-bit x86_64) Linux with
 *   the simple program
 *     Runtime.getRuntime().exec("/bin/true").waitFor();
 *   with:
 *     #  Internal Error (os_linux_x86.cpp:683), pid=19940, tid=2934639536
 *     #  Error: pthread_getattr_np failed with errno = 3 (ESRCH)
 *   We believe this is a glibc bug, reported here:
 *     http://sources.redhat.com/bugzilla/show_bug.cgi?id=10311
 *   but the glibc maintainers closed it as WONTFIX.
 *
 * - posix_spawn(). While posix_spawn() is a fairly elaborate and
 *   complicated system call, it can't quite do everything that the old
 *   fork()/exec() combination can do, so the only feasible way to do
 *   this, is to use posix_spawn to launch a new helper executable
 *   "jprochelper", which in turn execs the target (after cleaning
 *   up file-descriptors etc.) The end result is the same as before,
 *   a child process linked to the parent in the same way, but it
 *   avoids the problem of duplicating the parent (VM) process
 *   address space temporarily, before launching the target command.
 *
 * Based on the above analysis, we are currently using vfork() on
 * Linux and posix_spawn() on other Unix systems.
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

#define IOE_FORMAT "error=%d, %s"

static void
throwIOException(JNIEnv *env, int errnum, const char *defaultDetail)
{
    const char *detail = defaultDetail;
    char *errmsg;
    size_t fmtsize;
    char tmpbuf[1024];
    jstring s;

    if (errnum != 0) {
        int ret = getErrorString(errnum, tmpbuf, sizeof(tmpbuf));
        if (ret != EINVAL)
            detail = tmpbuf;
    }
    /* ASCII Decimal representation uses 2.4 times as many bits as binary. */
    fmtsize = sizeof(IOE_FORMAT) + strlen(detail) + 3 * sizeof(errnum);
    errmsg = NEW(char, fmtsize);
    if (errmsg == NULL)
        return;

    snprintf(errmsg, fmtsize, IOE_FORMAT, errnum, detail);
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
    int i, bytes, count;
    const char * const *a = arg;
    char *p;
    int *q;
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
vforkChild(ChildStuff *c) {
    volatile pid_t resultPid;

    /*
     * We separate the call to vfork into a separate function to make
     * very sure to keep stack of child from corrupting stack of parent,
     * as suggested by the scary gcc warning:
     *  warning: variable 'foo' might be clobbered by 'longjmp' or 'vfork'
     */
    resultPid = vfork();

    if (resultPid == 0) {
        childProcess(c);
    }
    assert(resultPid != 0);  /* childProcess never returns */
    return resultPid;
}

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

#if defined(__solaris__) || defined(_ALLBSD_SOURCE) || defined(_AIX)
static pid_t
spawnChild(JNIEnv *env, jobject process, ChildStuff *c, const char *helperpath) {
    pid_t resultPid;
    jboolean isCopy;
    int i, offset, rval, bufsize, magic;
    char *buf, buf1[16];
    char *hlpargs[2];
    SpawnInfo sp;

    /* need to tell helper which fd is for receiving the childstuff
     * and which fd to send response back on
     */
    snprintf(buf1, sizeof(buf1), "%d:%d", c->childenv[0], c->fail[1]);
    /* put the fd string as argument to the helper cmd */
    hlpargs[0] = buf1;
    hlpargs[1] = 0;

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
    /* We need to clear FD_CLOEXEC if set in the fds[].
     * Files are created FD_CLOEXEC in Java.
     * Otherwise, they will be closed when the target gets exec'd */
    for (i=0; i<3; i++) {
        if (c->fds[i] != -1) {
            int flags = fcntl(c->fds[i], F_GETFD);
            if (flags & FD_CLOEXEC) {
                fcntl(c->fds[i], F_SETFD, flags & (~1));
            }
        }
    }

    rval = posix_spawn(&resultPid, helperpath, 0, 0, (char * const *) hlpargs, environ);

    if (rval != 0) {
        return -1;
    }

    /* now the lengths are known, copy the data */
    buf = NEW(char, bufsize);
    if (buf == 0) {
        return -1;
    }
    offset = copystrings(buf, 0, &c->argv[0]);
    offset = copystrings(buf, offset, &c->envv[0]);
    memcpy(buf+offset, c->pdir, sp.dirlen);
    offset += sp.dirlen;
    offset = copystrings(buf, offset, parentPathv);
    assert(offset == bufsize);

    magic = magicNumber();

    /* write the two structs and the data buffer */
    write(c->childenv[1], (char *)&magic, sizeof(magic)); // magic number first
    write(c->childenv[1], (char *)c, sizeof(*c));
    write(c->childenv[1], (char *)&sp, sizeof(sp));
    write(c->childenv[1], buf, bufsize);
    free(buf);

    /* In this mode an external main() in invoked which calls back into
     * childProcess() in this file, rather than directly
     * via the statement below */
    return resultPid;
}
#endif

/*
 * Start a child process running function childProcess.
 * This function only returns in the parent.
 */
static pid_t
startChild(JNIEnv *env, jobject process, ChildStuff *c, const char *helperpath) {
    switch (c->mode) {
      case MODE_VFORK:
        return vforkChild(c);
      case MODE_FORK:
        return forkChild(c);
#if defined(__solaris__) || defined(_ALLBSD_SOURCE) || defined(_AIX)
      case MODE_POSIX_SPAWN:
        return spawnChild(env, process, c, helperpath);
#endif
      default:
        return -1;
    }
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
    int errnum;
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

    if ((fds[0] == -1 && pipe(in)  < 0) ||
        (fds[1] == -1 && pipe(out) < 0) ||
        (fds[2] == -1 && pipe(err) < 0) ||
        (pipe(childenv) < 0) ||
        (pipe(fail) < 0)) {
        throwIOException(env, errno, "Bad file descriptor");
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

    resultPid = startChild(env, process, c, phelperpath);
    assert(resultPid != 0);

    if (resultPid < 0) {
        switch (c->mode) {
          case MODE_VFORK:
            throwIOException(env, errno, "vfork failed");
            break;
          case MODE_FORK:
            throwIOException(env, errno, "fork failed");
            break;
          case MODE_POSIX_SPAWN:
            throwIOException(env, errno, "posix_spawn failed");
            break;
        }
        goto Catch;
    }
    close(fail[1]); fail[1] = -1; /* See: WhyCantJohnnyExec  (childproc.c)  */

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

    /* Always clean up fail and childEnv descriptors */
    closeSafely(fail[0]);
    closeSafely(fail[1]);
    closeSafely(childenv[0]);
    closeSafely(childenv[1]);

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

