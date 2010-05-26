/*
 * Copyright (c) 1994, 2004, Oracle and/or its affiliates. All rights reserved.
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

#include "hpi_impl.h"

#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/time.h> /* timeval */
#include <stdlib.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <signal.h>
#include <netdb.h>
#include <limits.h>
#include <errno.h>

#include <dlfcn.h>

#include "jni_md.h"
#include "mutex_md.h"

#include "hpi_init.h"

#include "interrupt.h"
#include "threads_md.h"
#include "monitor_md.h"
#include "largefile.h"


#define O_DELETE 0x10000

int sysThreadBootstrap(sys_thread_t **tidP, sys_mon_t **lockP, int nb)
{
    threadBootstrapMD(tidP, lockP, nb);

    intrInit();

#ifndef NATIVE
    /* Initialize the special case for sbrk on Solaris (see synch.c) */
    InitializeSbrk();
    /* Initialize the async io */
    InitializeAsyncIO();
    InitializeMem();
    /* Initialize Clock and Idle threads */
    InitializeHelperThreads();
#else /* if NATIVE */
    initializeContentionCountMutex();
    InitializeMem();
#endif /* NATIVE */

    return SYS_OK;
}

int sysShutdown()
{
    return SYS_OK;
}

long
sysGetMilliTicks()
{
    struct timeval tv;

    (void) gettimeofday(&tv, (void *) 0);
    return((tv.tv_sec * 1000) + (tv.tv_usec / 1000));
}

jlong
sysTimeMillis()
{
    struct timeval t;
    gettimeofday(&t, 0);
    return ((jlong)t.tv_sec) * 1000 + (jlong)(t.tv_usec/1000);
}

int
sysGetLastErrorString(char *buf, int len)
{
    if (errno == 0) {
        return 0;
    } else {
        const char *s = strerror(errno);
        int n = strlen(s);
        if (n >= len) n = len - 1;
        strncpy(buf, s, n);
        buf[n] = '\0';
        return n;
    }
}

/*
 * File system
 *
 * These are all the sys API which implement the straight POSIX
 * API. Those that do not are defined by thread-specific files
 * (i.e. io_md.c)
 */

/*
 * Open a file. Unlink the file immediately after open returns
 * if the specified oflag has the O_DELETE flag set.
 */
int sysOpen(const char *path, int oflag, int mode)
{
    int fd;
    int delete = (oflag & O_DELETE);
    oflag = oflag & ~O_DELETE;
    fd = open64_w(path, oflag, mode);
    if (delete != 0) {
        unlink(path);
    }
    return fd;
}

char *sysNativePath(char *path)
{
    return path;
}

int
sysFileSizeFD(int fd, jlong *size)
{
    struct stat64 buf64;
    int ret = fstat64(fd, &buf64);
    *size = buf64.st_size;
    return ret;
}

int
sysFfileMode(int fd, int *mode)
{
    struct stat64 buf64;
    int ret = fstat64(fd, &buf64);
    (*mode) = buf64.st_mode;
    return ret;
}

int
sysFileType(const char *path)
{
    int ret;
    struct stat buf;

    if ((ret = stat(path, &buf)) == 0) {
      mode_t mode = buf.st_mode & S_IFMT;
      if (mode == S_IFREG) return SYS_FILETYPE_REGULAR;
      if (mode == S_IFDIR) return SYS_FILETYPE_DIRECTORY;
      return SYS_FILETYPE_OTHER;
    }
    return ret;
}

/*
 * Wrapper functions for low-level I/O routines - use the 64 bit
 * version if available, else revert to the 32 bit versions.
 */

off64_t
lseek64_w(int fd, off64_t offset, int whence)
{
    return lseek64(fd, offset, whence);
}

int
ftruncate64_w(int fd, off64_t length)
{
    return ftruncate64(fd, length);
}

int
open64_w(const char *path, int oflag, int mode)
{
    int fd = open64(path, oflag, mode);
    if (fd == -1) return -1;

    /* If the open succeeded, the file might still be a directory */
    {
        int st_mode;
        if (sysFfileMode(fd, &st_mode) != -1) {
            if ((st_mode & S_IFMT) == S_IFDIR) {
                errno = EISDIR;
                close(fd);
                return -1;
            }
        } else {
            close(fd);
            return -1;
        }
    }

    /*
     * 32-bit Solaris systems suffer from:
     *
     * - an historical default soft limit of 256 per-process file
     *   descriptors that is too low for many Java programs.
     *
     * - a design flaw where file descriptors created using stdio
     *   fopen must be less than 256, _even_ when the first limit above
     *   has been raised.  This can cause calls to fopen (but not calls to
     *   open, for example) to fail mysteriously, perhaps in 3rd party
     *   native code (although the JDK itself uses fopen).  One can hardly
     *   criticize them for using this most standard of all functions.
     *
     * We attempt to make everything work anyways by:
     *
     * - raising the soft limit on per-process file descriptors beyond
     *   256 (done by hotspot)
     *
     * - As of Solaris 10u4, we can request that Solaris raise the 256
     *   stdio fopen limit by calling function enable_extended_FILE_stdio,
     *   (also done by hotspot).  We check for its availability.
     *
     * - If we are stuck on an old (pre 10u4) Solaris system, we can
     *   workaround the bug by remapping non-stdio file descriptors below
     *   256 to ones beyond 256, which is done below.
     *
     * See:
     * 1085341: 32-bit stdio routines should support file descriptors >255
     * 6533291: Work around 32-bit Solaris stdio limit of 256 open files
     * 6431278: Netbeans crash on 32 bit Solaris: need to call
     *          enable_extended_FILE_stdio() in VM initialisation
     * Giri Mandalika's blog
     * http://technopark02.blogspot.com/2005_05_01_archive.html
     */
#if defined(__solaris__) && defined(_ILP32)
    {
        static int needToWorkAroundBug1085341 = -1;
        if (needToWorkAroundBug1085341) {
            if (needToWorkAroundBug1085341 == -1)
                needToWorkAroundBug1085341 =
                    (dlsym(RTLD_DEFAULT, "enable_extended_FILE_stdio") == NULL);
            if (needToWorkAroundBug1085341 && fd < 256) {
                int newfd = fcntl(fd, F_DUPFD, 256);
                if (newfd != -1) {
                    close(fd);
                    fd = newfd;
                }
            }
        }
    }
#endif /* 32-bit Solaris */

    /*
     * All file descriptors that are opened in the JVM and not
     * specifically destined for a subprocess should have the
     * close-on-exec flag set.  If we don't set it, then careless 3rd
     * party native code might fork and exec without closing all
     * appropriate file descriptors (e.g. as we do in closeDescriptors in
     * UNIXProcess.c), and this in turn might:
     *
     * - cause end-of-file to fail to be detected on some file
     *   descriptors, resulting in mysterious hangs, or
     *
     * - might cause an fopen in the subprocess to fail on a system
     *   suffering from bug 1085341.
     *
     * (Yes, the default setting of the close-on-exec flag is a Unix
     * design flaw)
     *
     * See:
     * 1085341: 32-bit stdio routines should support file descriptors >255
     * 4843136: (process) pipe file descriptor from Runtime.exec not being closed
     * 6339493: (process) Runtime.exec does not close all file descriptors on Solaris 9
     */
#ifdef FD_CLOEXEC
    {
        int flags = fcntl(fd, F_GETFD);
        if (flags != -1)
            fcntl(fd, F_SETFD, flags | FD_CLOEXEC);
    }
#endif
    return fd;
}

void setFPMode(void)
{
#if    defined(__amd64)
    asm("        pushq   $0x1F80");
    /* ldmxcsr (%rsp) */
    asm("        .byte   0x0f,0xae,0x14,0x24");
    asm("        popq    %rax");
#elif  defined(i386)
    asm("        pushl $575");
    asm("        fldcw (%esp)");
    asm("        popl %eax");
#endif
#if defined(__linux__) && defined(__mc68000__)
    asm("        fmovel #0x80,%fpcr");
#endif
}
