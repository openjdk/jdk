/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "io_util.h"
#include "io_util_md.h"
#include <stdio.h>

#include <wchar.h>
#include <io.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <limits.h>
#include <wincon.h>

extern jboolean onNT = JNI_FALSE;

static DWORD MAX_INPUT_EVENTS = 2000;

void
initializeWindowsVersion() {
    OSVERSIONINFO ver;
    ver.dwOSVersionInfoSize = sizeof(ver);
    GetVersionEx(&ver);
    if (ver.dwPlatformId == VER_PLATFORM_WIN32_NT) {
        onNT = JNI_TRUE;
    } else {
        onNT = JNI_FALSE;
    }
}

/* If this returns NULL then an exception is pending */
WCHAR*
fileToNTPath(JNIEnv *env, jobject file, jfieldID id) {
    jstring path = NULL;
    if (file != NULL) {
        path = (*env)->GetObjectField(env, file, id);
    }
    return pathToNTPath(env, path, JNI_FALSE);
}

/* Returns the working directory for the given drive, or NULL */
WCHAR*
currentDir(int di) {
    UINT dt;
    WCHAR root[4];
    // verify drive is valid as _wgetdcwd in the VC++ 2010 runtime
    // library does not handle invalid drives.
    root[0] = L'A' + (WCHAR)(di - 1);
    root[1] = L':';
    root[2] = L'\\';
    root[3] = L'\0';
    dt = GetDriveTypeW(root);
    if (dt == DRIVE_UNKNOWN || dt == DRIVE_NO_ROOT_DIR) {
        return NULL;
    } else {
        return _wgetdcwd(di, NULL, MAX_PATH);
    }
}

/* We cache the length of current working dir here to avoid
   calling _wgetcwd() every time we need to resolve a relative
   path. This piece of code needs to be revisited if chdir
   makes its way into java runtime.
*/

int
currentDirLength(const WCHAR* ps, int pathlen) {
    WCHAR *dir;
    if (pathlen > 2 && ps[1] == L':' && ps[2] != L'\\') {
        //drive-relative
        WCHAR d = ps[0];
        int dirlen = 0;
        int di = 0;
        if ((d >= L'a') && (d <= L'z')) di = d - L'a' + 1;
        else if ((d >= L'A') && (d <= L'Z')) di = d - L'A' + 1;
        else return 0; /* invalid drive name. */
        dir = currentDir(di);
        if (dir != NULL){
            dirlen = (int)wcslen(dir);
            free(dir);
        }
        return dirlen;
    } else {
        static int curDirLenCached = -1;
        //relative to both drive and directory
        if (curDirLenCached == -1) {
            int dirlen = -1;
            dir = _wgetcwd(NULL, MAX_PATH);
            if (dir != NULL) {
                curDirLenCached = (int)wcslen(dir);
                free(dir);
            }
        }
        return curDirLenCached;
    }
}

/*
  The "abpathlen" is the size of the buffer needed by _wfullpath. If the
  "path" is a relative path, it is "the length of the current dir" + "the
  length of the path", if it's "absolute" already, it's the same as
  pathlen which is the length of "path".
 */
WCHAR* prefixAbpath(const WCHAR* path, int pathlen, int abpathlen) {
    WCHAR* pathbuf = NULL;
    WCHAR* abpath = NULL;

    abpathlen += 10;  //padding
    abpath = (WCHAR*)malloc(abpathlen * sizeof(WCHAR));
    if (abpath) {
        /* Collapse instances of "foo\.." and ensure absoluteness before
           going down to prefixing.
        */
        if (_wfullpath(abpath, path, abpathlen)) {
            pathbuf = getPrefixed(abpath, abpathlen);
        } else {
            /* _wfullpath fails if the pathlength exceeds 32k wchar.
               Instead of doing more fancy things we simply copy the
               ps into the return buffer, the subsequent win32 API will
               probably fail with FileNotFoundException, which is expected
            */
            pathbuf = (WCHAR*)malloc((pathlen + 6) * sizeof(WCHAR));
            if (pathbuf != 0) {
                wcscpy(pathbuf, path);
            }
        }
        free(abpath);
    }
    return pathbuf;
}

/* If this returns NULL then an exception is pending */
WCHAR*
pathToNTPath(JNIEnv *env, jstring path, jboolean throwFNFE) {
    int pathlen = 0;
    WCHAR *pathbuf = NULL;
    int max_path = 248; /* CreateDirectoryW() has the limit of 248 */

    WITH_UNICODE_STRING(env, path, ps) {
        pathlen = (int)wcslen(ps);
        if (pathlen != 0) {
            if (pathlen > 2 &&
                (ps[0] == L'\\' && ps[1] == L'\\' ||   //UNC
                 ps[1] == L':' && ps[2] == L'\\'))     //absolute
            {
                 if (pathlen > max_path - 1) {
                     pathbuf = prefixAbpath(ps, pathlen, pathlen);
                 } else {
                     pathbuf = (WCHAR*)malloc((pathlen + 6) * sizeof(WCHAR));
                     if (pathbuf != 0) {
                         wcscpy(pathbuf, ps);
                     }
                 }
            } else {
                /* If the path came in as a relative path, need to verify if
                   its absolute form is bigger than max_path or not, if yes
                   need to (1)convert it to absolute and (2)prefix. This is
                   obviously a burden to all relative paths (The current dir/len
                   for "drive & directory" relative path is cached, so we only
                   calculate it once but for "drive-relative path we call
                   _wgetdcwd() and wcslen() everytime), but a hit we have
                   to take if we want to support relative path beyond max_path.
                   There is no way to predict how long the absolute path will be
                   (therefor allocate the sufficient memory block) before calling
                   _wfullpath(), we have to get the length of "current" dir first.
                */
                WCHAR *abpath = NULL;
                int dirlen = currentDirLength(ps, pathlen);
                if (dirlen + pathlen + 1 > max_path - 1) {
                    pathbuf = prefixAbpath(ps, pathlen, dirlen + pathlen);
                } else {
                    pathbuf = (WCHAR*)malloc((pathlen + 6) * sizeof(WCHAR));
                    if (pathbuf != 0) {
                        wcscpy(pathbuf, ps);
                    }
                }
            }
        }
    } END_UNICODE_STRING(env, ps);

    if (pathlen == 0) {
        if (throwFNFE == JNI_TRUE) {
            throwFileNotFoundException(env, path);
            return NULL;
        } else {
            pathbuf = (WCHAR*)malloc(sizeof(WCHAR));
            pathbuf[0] = L'\0';
        }
    }
    if (pathbuf == 0) {
        JNU_ThrowOutOfMemoryError(env, 0);
        return NULL;
    }
    return pathbuf;
}

jlong
winFileHandleOpen(JNIEnv *env, jstring path, int flags)
{
    const DWORD access =
        (flags & O_WRONLY) ?  GENERIC_WRITE :
        (flags & O_RDWR)   ? (GENERIC_READ | GENERIC_WRITE) :
        GENERIC_READ;
    const DWORD sharing =
        FILE_SHARE_READ | FILE_SHARE_WRITE;
    const DWORD disposition =
        /* Note: O_TRUNC overrides O_CREAT */
        (flags & O_TRUNC) ? CREATE_ALWAYS :
        (flags & O_CREAT) ? OPEN_ALWAYS   :
        OPEN_EXISTING;
    const DWORD  maybeWriteThrough =
        (flags & (O_SYNC | O_DSYNC)) ?
        FILE_FLAG_WRITE_THROUGH :
        FILE_ATTRIBUTE_NORMAL;
    const DWORD maybeDeleteOnClose =
        (flags & O_TEMPORARY) ?
        FILE_FLAG_DELETE_ON_CLOSE :
        FILE_ATTRIBUTE_NORMAL;
    const DWORD flagsAndAttributes = maybeWriteThrough | maybeDeleteOnClose;
    HANDLE h = NULL;

    if (onNT) {
        WCHAR *pathbuf = pathToNTPath(env, path, JNI_TRUE);
        if (pathbuf == NULL) {
            /* Exception already pending */
            return -1;
        }
        h = CreateFileW(
            pathbuf,            /* Wide char path name */
            access,             /* Read and/or write permission */
            sharing,            /* File sharing flags */
            NULL,               /* Security attributes */
            disposition,        /* creation disposition */
            flagsAndAttributes, /* flags and attributes */
            NULL);
        free(pathbuf);
    } else {
        WITH_PLATFORM_STRING(env, path, _ps) {
            h = CreateFile(_ps, access, sharing, NULL, disposition,
                           flagsAndAttributes, NULL);
        } END_PLATFORM_STRING(env, _ps);
    }
    if (h == INVALID_HANDLE_VALUE) {
        int error = GetLastError();
        if (error == ERROR_TOO_MANY_OPEN_FILES) {
            JNU_ThrowByName(env, JNU_JAVAIOPKG "IOException",
                            "Too many open files");
            return -1;
        }
        throwFileNotFoundException(env, path);
        return -1;
    }
    return (jlong) h;
}

void
fileOpen(JNIEnv *env, jobject this, jstring path, jfieldID fid, int flags)
{
    jlong h = winFileHandleOpen(env, path, flags);
    if (h >= 0) {
        SET_FD(this, h, fid);
    }
}

/* These are functions that use a handle fd instead of the
   old C style int fd as is used in HPI layer */

static int
handleNonSeekAvailable(jlong, long *);
static int
handleStdinAvailable(jlong, long *);

int
handleAvailable(jlong fd, jlong *pbytes) {
    HANDLE h = (HANDLE)fd;
    DWORD type = 0;

    type = GetFileType(h);
    /* Handle is for keyboard or pipe */
    if (type == FILE_TYPE_CHAR || type == FILE_TYPE_PIPE) {
        int ret;
        long lpbytes;
        HANDLE stdInHandle = GetStdHandle(STD_INPUT_HANDLE);
        if (stdInHandle == h) {
            ret = handleStdinAvailable(fd, &lpbytes); /* keyboard */
        } else {
            ret = handleNonSeekAvailable(fd, &lpbytes); /* pipe */
        }
        (*pbytes) = (jlong)(lpbytes);
        return ret;
    }
    /* Handle is for regular file */
    if (type == FILE_TYPE_DISK) {
        jlong current, end;

        LARGE_INTEGER filesize;
        current = handleLseek(fd, 0, SEEK_CUR);
        if (current < 0) {
            return FALSE;
        }
        if (GetFileSizeEx(h, &filesize) == 0) {
            return FALSE;
        }
        end = long_to_jlong(filesize.QuadPart);
        *pbytes = end - current;
        return TRUE;
    }
    return FALSE;
}

static int
handleNonSeekAvailable(jlong fd, long *pbytes) {
    /* This is used for available on non-seekable devices
     * (like both named and anonymous pipes, such as pipes
     *  connected to an exec'd process).
     * Standard Input is a special case.
     *
     */
    HANDLE han;

    if ((han = (HANDLE) fd) == INVALID_HANDLE_VALUE) {
        return FALSE;
    }

    if (! PeekNamedPipe(han, NULL, 0, NULL, pbytes, NULL)) {
        /* PeekNamedPipe fails when at EOF.  In that case we
         * simply make *pbytes = 0 which is consistent with the
         * behavior we get on Solaris when an fd is at EOF.
         * The only alternative is to raise and Exception,
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
handleStdinAvailable(jlong fd, long *pbytes) {
    HANDLE han;
    DWORD numEventsRead = 0;    /* Number of events read from buffer */
    DWORD numEvents = 0;        /* Number of events in buffer */
    DWORD i = 0;                /* Loop index */
    DWORD curLength = 0;        /* Position marker */
    DWORD actualLength = 0;     /* Number of bytes readable */
    BOOL error = FALSE;         /* Error holder */
    INPUT_RECORD *lpBuffer;     /* Pointer to records of input events */
    DWORD bufferSize = 0;

    if ((han = GetStdHandle(STD_INPUT_HANDLE)) == INVALID_HANDLE_VALUE) {
        return FALSE;
    }

    /* Construct an array of input records in the console buffer */
    error = GetNumberOfConsoleInputEvents(han, &numEvents);
    if (error == 0) {
        return handleNonSeekAvailable(fd, pbytes);
    }

    /* lpBuffer must fit into 64K or else PeekConsoleInput fails */
    if (numEvents > MAX_INPUT_EVENTS) {
        numEvents = MAX_INPUT_EVENTS;
    }

    bufferSize = numEvents * sizeof(INPUT_RECORD);
    if (bufferSize == 0)
        bufferSize = 1;
    lpBuffer = malloc(bufferSize);
    if (lpBuffer == NULL) {
        return FALSE;
    }

    error = PeekConsoleInput(han, lpBuffer, numEvents, &numEventsRead);
    if (error == 0) {
        free(lpBuffer);
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
        free(lpBuffer);
    *pbytes = (long) actualLength;
    return TRUE;
}

/*
 * This is documented to succeed on read-only files, but Win32's
 * FlushFileBuffers functions fails with "access denied" in such a
 * case.  So we only signal an error if the error is *not* "access
 * denied".
 */

JNIEXPORT int
handleSync(jlong fd) {
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

    HANDLE handle = (HANDLE)fd;

    if (!FlushFileBuffers(handle)) {
        if (GetLastError() != ERROR_ACCESS_DENIED) {    /* from winerror.h */
            return -1;
        }
    }
    return 0;
}


int
handleSetLength(jlong fd, jlong length) {
    HANDLE h = (HANDLE)fd;
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

JNIEXPORT
size_t
handleRead(jlong fd, void *buf, jint len)
{
    DWORD read = 0;
    BOOL result = 0;
    HANDLE h = (HANDLE)fd;
    if (h == INVALID_HANDLE_VALUE) {
        return -1;
    }
    result = ReadFile(h,          /* File handle to read */
                      buf,        /* address to put data */
                      len,        /* number of bytes to read */
                      &read,      /* number of bytes read */
                      NULL);      /* no overlapped struct */
    if (result == 0) {
        int error = GetLastError();
        if (error == ERROR_BROKEN_PIPE) {
            return 0; /* EOF */
        }
        return -1;
    }
    return read;
}

static size_t writeInternal(jlong fd, const void *buf, jint len, jboolean append)
{
    BOOL result = 0;
    DWORD written = 0;
    HANDLE h = (HANDLE)fd;
    if (h != INVALID_HANDLE_VALUE) {
        OVERLAPPED ov;
        LPOVERLAPPED lpOv;
        if (append == JNI_TRUE) {
            ov.Offset = (DWORD)0xFFFFFFFF;
            ov.OffsetHigh = (DWORD)0xFFFFFFFF;
            ov.hEvent = NULL;
            lpOv = &ov;
        } else {
            lpOv = NULL;
        }
        result = WriteFile(h,                /* File handle to write */
                           buf,              /* pointers to the buffers */
                           len,              /* number of bytes to write */
                           &written,         /* receives number of bytes written */
                           lpOv);            /* overlapped struct */
    }
    if ((h == INVALID_HANDLE_VALUE) || (result == 0)) {
        return -1;
    }
    return (size_t)written;
}

JNIEXPORT
size_t handleWrite(jlong fd, const void *buf, jint len) {
    return writeInternal(fd, buf, len, JNI_FALSE);
}

JNIEXPORT
size_t handleAppend(jlong fd, const void *buf, jint len) {
    return writeInternal(fd, buf, len, JNI_TRUE);
}

jint
handleClose(JNIEnv *env, jobject this, jfieldID fid)
{
    FD fd = GET_FD(this, fid);
    HANDLE h = (HANDLE)fd;

    if (h == INVALID_HANDLE_VALUE) {
        return 0;
    }

    /* Set the fd to -1 before closing it so that the timing window
     * of other threads using the wrong fd (closed but recycled fd,
     * that gets re-opened with some other filename) is reduced.
     * Practically the chance of its occurance is low, however, we are
     * taking extra precaution over here.
     */
    SET_FD(this, -1, fid);

    if (CloseHandle(h) == 0) { /* Returns zero on failure */
        JNU_ThrowIOExceptionWithLastError(env, "close failed");
    }
    return 0;
}

jlong
handleLseek(jlong fd, jlong offset, jint whence)
{
    LARGE_INTEGER pos, distance;
    DWORD lowPos = 0;
    long highPos = 0;
    DWORD op = FILE_CURRENT;
    HANDLE h = (HANDLE)fd;

    if (whence == SEEK_END) {
        op = FILE_END;
    }
    if (whence == SEEK_CUR) {
        op = FILE_CURRENT;
    }
    if (whence == SEEK_SET) {
        op = FILE_BEGIN;
    }

    distance.QuadPart = offset;
    if (SetFilePointerEx(h, distance, &pos, op) == 0) {
        return -1;
    }
    return long_to_jlong(pos.QuadPart);
}
