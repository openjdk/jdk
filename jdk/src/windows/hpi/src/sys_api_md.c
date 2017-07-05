/*
 * Copyright (c) 1997, 2004, Oracle and/or its affiliates. All rights reserved.
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

#include <io.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <limits.h>

#include "hpi_impl.h"

#include "path_md.h"

static int MAX_INPUT_EVENTS = 2000;

int
sysOpen(const char *path, int oflag, int mode)
{
    char pathbuf[MAX_PATH];

    if (strlen(path) > MAX_PATH - 1) {
        errno = ENAMETOOLONG;
        return -1;
    }
    return open(sysNativePath(strcpy(pathbuf, path)),
                oflag | O_BINARY | O_NOINHERIT, mode);
}


static int
nonSeekAvailable(int, long *);
static int
stdinAvailable(int, long *);

int
sysAvailable(int fd, jlong *pbytes) {
    jlong cur, end;
    struct _stati64 stbuf64;

    if (_fstati64(fd, &stbuf64) >= 0) {
        int mode = stbuf64.st_mode;
        if (S_ISCHR(mode) || S_ISFIFO(mode)) {
            int ret;
            long lpbytes;
            if (fd == 0) {
                ret = stdinAvailable(fd, &lpbytes);
            } else {
                ret = nonSeekAvailable(fd, &lpbytes);
            }
            (*pbytes) = (jlong)(lpbytes);
            return ret;
        }
        if ((cur = _lseeki64(fd, 0L, SEEK_CUR)) == -1) {
            return FALSE;
        } else if ((end = _lseeki64(fd, 0L, SEEK_END)) == -1) {
            return FALSE;
        } else if (_lseeki64(fd, cur, SEEK_SET) == -1) {
            return FALSE;
        }
        *pbytes = end - cur;
        return TRUE;
    } else {
        return FALSE;
    }
}

static int
nonSeekAvailable(int fd, long *pbytes) {
    /* This is used for available on non-seekable devices
     * (like both named and anonymous pipes, such as pipes
     *  connected to an exec'd process).
     * Standard Input is a special case.
     *
     */
    HANDLE han;

    if ((han = (HANDLE) _get_osfhandle(fd)) == (HANDLE)(-1)) {
        return FALSE;
    }

    if (! PeekNamedPipe(han, NULL, 0, NULL, pbytes, NULL)) {
        /* PeekNamedPipe fails when at EOF.  In that case we
         * simply make *pbytes = 0 which is consistent with the
         * behavior we get on Solaris when an fd is at EOF.
         * The only alternative is to raise an Exception,
         * which isn't really warranted.
         */
        if (GetLastError() != ERROR_BROKEN_PIPE) {
            return FALSE;
        }
        *pbytes = 0;
    }
    return TRUE;
}

static int
stdinAvailable(int fd, long *pbytes) {
    HANDLE han;
    DWORD numEventsRead = 0;    /* Number of events read from buffer */
    DWORD numEvents = 0;        /* Number of events in buffer */
    DWORD i = 0;                /* Loop index */
    DWORD curLength = 0;        /* Position marker */
    DWORD actualLength = 0;     /* Number of bytes readable */
    BOOL error = FALSE;         /* Error holder */
    INPUT_RECORD *lpBuffer;     /* Pointer to records of input events */

    if ((han = GetStdHandle(STD_INPUT_HANDLE)) == INVALID_HANDLE_VALUE) {
                return FALSE;
    }

    /* Construct an array of input records in the console buffer */
    error = GetNumberOfConsoleInputEvents(han, &numEvents);
    if (error == 0) {
        return nonSeekAvailable(fd, pbytes);
    }

    /* lpBuffer must fit into 64K or else PeekConsoleInput fails */
    if (numEvents > MAX_INPUT_EVENTS) {
        numEvents = MAX_INPUT_EVENTS;
    }

    lpBuffer = sysMalloc(numEvents * sizeof(INPUT_RECORD));
    if (lpBuffer == NULL) {
        return FALSE;
    }

    error = PeekConsoleInput(han, lpBuffer, numEvents, &numEventsRead);
    if (error == 0) {
        sysFree(lpBuffer);
        return FALSE;
    }

    /* Examine input records for the number of bytes available */
    for(i=0; i<numEvents; i++) {
        if (lpBuffer[i].EventType == KEY_EVENT) {
            KEY_EVENT_RECORD *keyRecord = (KEY_EVENT_RECORD *)
                                          &(lpBuffer[i].Event);
            if (keyRecord->bKeyDown == TRUE) {
                CHAR *keyPressed = (CHAR *) &(keyRecord->uChar);
                curLength++;
                if (*keyPressed == '\r')
                    actualLength = curLength;
            }
        }
    }
    if(lpBuffer != NULL)
        sysFree(lpBuffer);
    *pbytes = (long) actualLength;
    return TRUE;
}

/*
 * This is documented to succeed on read-only files, but Win32's
 * FlushFileBuffers functions fails with "access denied" in such a
 * case.  So we only signal an error if the error is *not* "access
 * denied".
 */

int
sysSync(int fd) {
    /*
     * From the documentation:
     *
     *     On Windows NT, the function FlushFileBuffers fails if hFile
     *     is a handle to console output. That is because console
     *     output is not buffered. The function returns FALSE, and
     *     GetLastError returns ERROR_INVALID_HANDLE.
     *
     * On the other hand, on Win95, it returns without error.  I cannot
     * assume that 0, 1, and 2 are console, because if someone closes
     * System.out and then opens a file, they might get file descriptor
     * 1.  An error on *that* version of 1 should be reported, whereas
     * an error on System.out (which was the original 1) should be
     * ignored.  So I use isatty() to ensure that such an error was due
     * to this bogosity, and if it was, I ignore the error.
     */

    HANDLE handle = (HANDLE)_get_osfhandle(fd);

    if (!FlushFileBuffers(handle)) {
        if (GetLastError() != ERROR_ACCESS_DENIED) {    /* from winerror.h */
            return -1;
        }
    }
    return 0;
}


int
sysSetLength(int fd, jlong length) {
    HANDLE h = (HANDLE)_get_osfhandle(fd);
    long high = (long)(length >> 32);
    DWORD ret;

    if (h == (HANDLE)(-1)) return -1;
    ret = SetFilePointer(h, (long)(length), &high, FILE_BEGIN);
    if (ret == 0xFFFFFFFF && GetLastError() != NO_ERROR) {
        return -1;
    }
    if (SetEndOfFile(h) == FALSE) return -1;
    return 0;
}

int
sysFileSizeFD(int fd, jlong *size)
{
    struct _stati64 buf64;

    if(_fstati64(fd, &buf64) < 0) {
        return -1;
    }
    (*size) = buf64.st_size;

    if (*size & 0xFFFFFFFF00000000) {
        /*
         * On Win98 accessing a non-local file we have observed a
         * bogus file size of 0x100000000.  So if upper 32 bits
         * are non-zero we re-calculate the size using lseek.  This
         * should work for any file size, but it might have a
         * performance impact relative to fstati64.  Note: Hotspot
         * doesn't have this problem because it uses stat rather
         * than fstat or fstati64.
         */

        jlong curpos;
        jlong endpos;
        jlong newpos;

        curpos = _lseeki64(fd, 0, SEEK_CUR);
        if (curpos < 0) {
            return -1;
        }
        endpos = _lseeki64(fd, 0, SEEK_END);
        if (endpos < 0) {
            return -1;
        }
        newpos = _lseeki64(fd, curpos, SEEK_SET);
        if (newpos != curpos) {
            return -1;
        }
        (*size) = endpos;

    }
    return 0;
}

int
sysFfileMode(int fd, int *mode)
{
    int ret;
    struct _stati64 buf64;
    ret = _fstati64(fd, &buf64);
    (*mode) = buf64.st_mode;
    return ret;
}

int
sysFileType(const char *path)
{
    int ret;
    struct _stati64 buf;

    if ((ret = _stati64(path, &buf)) == 0) {
      int mode = buf.st_mode & S_IFMT;
      if (mode == S_IFREG) return SYS_FILETYPE_REGULAR;
      if (mode == S_IFDIR) return SYS_FILETYPE_DIRECTORY;
      return SYS_FILETYPE_OTHER;
    }
    return ret;
}

size_t sysRead(int fd, void *buf, unsigned int nBytes)
{
    return read(fd, buf, nBytes);
}

size_t sysWrite(int fd, const void *buf, unsigned int nBytes)
{
    return write(fd, buf, nBytes);
}

int sysClose(int fd)
{
    return close(fd);
}

jlong sysSeek(int fd, jlong offset, int whence)
{
    return _lseeki64(fd, offset, whence);
}
