/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <direct.h>
#include <windows.h>
#include <io.h>

#include "jvm.h"
#include "jni.h"
#include "jni_util.h"
#include "jlong.h"
#include "io_util.h"
#include "dirent_md.h"
#include "java_io_FileSystem.h"

/* This macro relies upon the fact that JNU_GetStringPlatformChars always makes
   a copy of the string */

#define WITH_NATIVE_PATH(env, object, id, var)                                \
    WITH_FIELD_PLATFORM_STRING(env, object, id, var)                          \
        JVM_NativePath((char *)var);

#define END_NATIVE_PATH(env, var)    END_PLATFORM_STRING(env, var)


static struct {
    jfieldID path;
} ids;

JNIEXPORT void JNICALL
Java_java_io_Win32FileSystem_initIDs(JNIEnv *env, jclass cls)
{
    jclass fileClass = (*env)->FindClass(env, "java/io/File");
    if (!fileClass) return;
    ids.path = (*env)->GetFieldID(env, fileClass,
                                  "path", "Ljava/lang/String;");
}


/* -- Path operations -- */


extern int canonicalize(char *path, const char *out, int len);
extern int canonicalizeWithPrefix(const char* canonicalPrefix, const char *pathWithCanonicalPrefix, char *out, int len);

JNIEXPORT jstring JNICALL
Java_java_io_Win32FileSystem_canonicalize0(JNIEnv *env, jobject this,
                                           jstring pathname)
{
    jstring rv = NULL;

    WITH_PLATFORM_STRING(env, pathname, path) {
        char canonicalPath[JVM_MAXPATHLEN];
        if (canonicalize(JVM_NativePath((char *)path),
                         canonicalPath, JVM_MAXPATHLEN) < 0) {
            JNU_ThrowIOExceptionWithLastError(env, "Bad pathname");
        } else {
            rv = JNU_NewStringPlatform(env, canonicalPath);
        }
    } END_PLATFORM_STRING(env, path);
    return rv;
}


JNIEXPORT jstring JNICALL
Java_java_io_Win32FileSystem_canonicalizeWithPrefix0(JNIEnv *env, jobject this,
                                                     jstring canonicalPrefixString,
                                                     jstring pathWithCanonicalPrefixString)
{
    jstring rv = NULL;
    char canonicalPath[JVM_MAXPATHLEN];

    WITH_PLATFORM_STRING(env, canonicalPrefixString, canonicalPrefix) {
        WITH_PLATFORM_STRING(env, pathWithCanonicalPrefixString, pathWithCanonicalPrefix) {
            if (canonicalizeWithPrefix(canonicalPrefix,
                                       pathWithCanonicalPrefix,
                                       canonicalPath, JVM_MAXPATHLEN) < 0) {
                JNU_ThrowIOExceptionWithLastError(env, "Bad pathname");
            } else {
                rv = JNU_NewStringPlatform(env, canonicalPath);
            }
        } END_PLATFORM_STRING(env, pathWithCanonicalPrefix);
    } END_PLATFORM_STRING(env, canonicalPrefix);
    return rv;
}



/* -- Attribute accessors -- */

/* Check whether or not the file name in "path" is a Windows reserved
   device name (CON, PRN, AUX, NUL, COM[1-9], LPT[1-9]) based on the
   returned result from GetFullPathName. If the file name in the path
   is indeed a reserved device name GetFuulPathName returns
   "\\.\[ReservedDeviceName]".
 */
BOOL isReservedDeviceName(const char* path) {
#define BUFSIZE 9
    char buf[BUFSIZE];
    char *lpf = NULL;
    DWORD retLen = GetFullPathName(path,
                                   BUFSIZE,
                                   buf,
                                   &lpf);
    if ((retLen == BUFSIZE - 1 || retLen == BUFSIZE - 2) &&
        buf[0] == '\\' && buf[1] == '\\' &&
        buf[2] == '.' && buf[3] == '\\') {
        char* dname = _strupr(buf + 4);
        if (strcmp(dname, "CON") == 0 ||
            strcmp(dname, "PRN") == 0 ||
            strcmp(dname, "AUX") == 0 ||
            strcmp(dname, "NUL") == 0)
            return TRUE;
        if ((strncmp(dname, "COM", 3) == 0 ||
             strncmp(dname, "LPT", 3) == 0) &&
            dname[3] - '0' > 0 &&
            dname[3] - '0' <= 9)
            return TRUE;
    }
    return FALSE;
}

JNIEXPORT jint JNICALL
Java_java_io_Win32FileSystem_getBooleanAttributes(JNIEnv *env, jobject this,
                                                  jobject file)
{
    jint rv = 0;

    WITH_NATIVE_PATH(env, file, ids.path, path) {
        WIN32_FILE_ATTRIBUTE_DATA wfad;
        if (!isReservedDeviceName(path) &&
            GetFileAttributesEx(path, GetFileExInfoStandard, &wfad)) {
            rv = (java_io_FileSystem_BA_EXISTS
                  | ((wfad.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)
                     ? java_io_FileSystem_BA_DIRECTORY
                     : java_io_FileSystem_BA_REGULAR)
                  | ((wfad.dwFileAttributes & FILE_ATTRIBUTE_HIDDEN)
                     ? java_io_FileSystem_BA_HIDDEN : 0));
        }
    } END_NATIVE_PATH(env, path);
    return rv;
}


JNIEXPORT jboolean JNICALL
Java_java_io_Win32FileSystem_checkAccess(JNIEnv *env, jobject this,
                                         jobject file, jint a)
{
    jboolean rv = JNI_FALSE;
    int mode;
    switch (a) {
    case java_io_FileSystem_ACCESS_READ:
    case java_io_FileSystem_ACCESS_EXECUTE:
        mode = 4;
        break;
    case java_io_FileSystem_ACCESS_WRITE:
        mode = 2;
        break;
    default: assert(0);
    }
    WITH_NATIVE_PATH(env, file, ids.path, path) {
        if (access(path, mode) == 0) {
            rv = JNI_TRUE;
        }
    } END_NATIVE_PATH(env, path);
    return rv;
}

JNIEXPORT jboolean JNICALL
Java_java_io_Win32FileSystem_setPermission(JNIEnv *env, jobject this,
                                           jobject file,
                                           jint access,
                                           jboolean enable,
                                           jboolean owneronly)
{
    jboolean rv = JNI_FALSE;
    if (access == java_io_FileSystem_ACCESS_READ ||
        access == java_io_FileSystem_ACCESS_EXECUTE) {
        return enable;
    }
    WITH_NATIVE_PATH(env, file, ids.path, path) {
        DWORD a;
        a = GetFileAttributes(path);
        if (a != INVALID_FILE_ATTRIBUTES) {
            if (enable)
                a =  a & ~FILE_ATTRIBUTE_READONLY;
            else
                a =  a | FILE_ATTRIBUTE_READONLY;
            if (SetFileAttributes(path, a))
                rv = JNI_TRUE;
        }
    } END_NATIVE_PATH(env, path);
    return rv;
}

JNIEXPORT jlong JNICALL
Java_java_io_Win32FileSystem_getLastModifiedTime(JNIEnv *env, jobject this,
                                                 jobject file)
{
    jlong rv = 0;
    WITH_NATIVE_PATH(env, file, ids.path, path) {
    /* Win95, Win98, WinME */
    WIN32_FIND_DATA fd;
    jlong temp = 0;
    LARGE_INTEGER modTime;
    HANDLE h = FindFirstFile(path, &fd);
    if (h != INVALID_HANDLE_VALUE) {
        FindClose(h);
        modTime.LowPart = (DWORD) fd.ftLastWriteTime.dwLowDateTime;
        modTime.HighPart = (LONG) fd.ftLastWriteTime.dwHighDateTime;
        rv = modTime.QuadPart / 10000;
        rv -= 11644473600000;
    }
    } END_NATIVE_PATH(env, path);
    return rv;
}

JNIEXPORT jlong JNICALL
Java_java_io_Win32FileSystem_getLength(JNIEnv *env, jobject this,
                                       jobject file)
{
    jlong rv = 0;

    WITH_NATIVE_PATH(env, file, ids.path, path) {
        struct _stati64 sb;
        if (_stati64(path, &sb) == 0) {
            rv = sb.st_size;
        }
    } END_NATIVE_PATH(env, path);
    return rv;
}


/* -- File operations -- */


JNIEXPORT jboolean JNICALL
Java_java_io_Win32FileSystem_createFileExclusively(JNIEnv *env, jclass cls,
                                                   jstring pathname)
{
    jboolean rv = JNI_FALSE;
    DWORD a;

    WITH_PLATFORM_STRING(env, pathname, path) {
        int orv;
        int error;
        JVM_NativePath((char *)path);
        orv = JVM_Open(path, JVM_O_RDWR | JVM_O_CREAT | JVM_O_EXCL, 0666);
        if (orv < 0) {
            if (orv != JVM_EEXIST) {
                error = GetLastError();

                // If a directory by the named path already exists,
                // return false (behavior of solaris and linux) instead of
                // throwing an exception
                a = GetFileAttributes(path);

                if ((a == INVALID_FILE_ATTRIBUTES) ||
                        !(a & FILE_ATTRIBUTE_DIRECTORY)) {
                    SetLastError(error);
                    JNU_ThrowIOExceptionWithLastError(env, path);
                }
            }
        } else {
            JVM_Close(orv);
            rv = JNI_TRUE;
        }
    } END_PLATFORM_STRING(env, path);
    return rv;
}


static int
removeFileOrDirectory(const char *path) /* Returns 0 on success */
{
    DWORD a;

    SetFileAttributes(path, 0);
    a = GetFileAttributes(path);
    if (a == INVALID_FILE_ATTRIBUTES) {
        return 1;
    } else if (a & FILE_ATTRIBUTE_DIRECTORY) {
        return !RemoveDirectory(path);
    } else {
        return !DeleteFile(path);
    }
}


JNIEXPORT jboolean JNICALL
Java_java_io_Win32FileSystem_delete0(JNIEnv *env, jobject this,
                                     jobject file)
{
    jboolean rv = JNI_FALSE;

    WITH_NATIVE_PATH(env, file, ids.path, path) {
        if (removeFileOrDirectory(path) == 0) {
            rv = JNI_TRUE;
        }
    } END_NATIVE_PATH(env, path);
    return rv;
}


/* ## Clean this up to use direct Win32 calls */

JNIEXPORT jobjectArray JNICALL
Java_java_io_Win32FileSystem_list(JNIEnv *env, jobject this,
                                  jobject file)
{
    DIR *dir;
    struct dirent *ptr;
    int len, maxlen;
    jobjectArray rv, old;

    WITH_NATIVE_PATH(env, file, ids.path, path) {
        dir = opendir(path);
    } END_NATIVE_PATH(env, path);
    if (dir == NULL) return NULL;

    /* Allocate an initial String array */
    len = 0;
    maxlen = 16;
    rv = (*env)->NewObjectArray(env, maxlen, JNU_ClassString(env), NULL);
    if (rv == NULL) goto error;

    /* Scan the directory */
    while ((ptr = readdir(dir)) != NULL) {
        jstring name;
        if (!strcmp(ptr->d_name, ".") || !strcmp(ptr->d_name, ".."))
            continue;
        if (len == maxlen) {
            old = rv;
            rv = (*env)->NewObjectArray(env, maxlen <<= 1,
                                        JNU_ClassString(env), NULL);
            if (rv == NULL) goto error;
            if (JNU_CopyObjectArray(env, rv, old, len) < 0) goto error;
            (*env)->DeleteLocalRef(env, old);
        }
        name = JNU_NewStringPlatform(env, ptr->d_name);
        if (name == NULL) goto error;
        (*env)->SetObjectArrayElement(env, rv, len++, name);
        (*env)->DeleteLocalRef(env, name);
    }
    closedir(dir);

    /* Copy the final results into an appropriately-sized array */
    old = rv;
    rv = (*env)->NewObjectArray(env, len, JNU_ClassString(env), NULL);
    if (rv == NULL) goto error;
    if (JNU_CopyObjectArray(env, rv, old, len) < 0) goto error;
    return rv;

 error:
    closedir(dir);
    return NULL;
}


JNIEXPORT jboolean JNICALL
Java_java_io_Win32FileSystem_createDirectory(JNIEnv *env, jobject this,
                                             jobject file)
{
    jboolean rv = JNI_FALSE;

    WITH_NATIVE_PATH(env, file, ids.path, path) {
        if (mkdir(path) == 0) {
            rv = JNI_TRUE;
        }
    } END_NATIVE_PATH(env, path);
    return rv;
}


JNIEXPORT jboolean JNICALL
Java_java_io_Win32FileSystem_rename0(JNIEnv *env, jobject this,
                                     jobject from, jobject to)
{
    jboolean rv = JNI_FALSE;

    WITH_NATIVE_PATH(env, from, ids.path, fromPath) {
        WITH_NATIVE_PATH(env, to, ids.path, toPath) {
            if (rename(fromPath, toPath) == 0) {
                rv = JNI_TRUE;
            }
        } END_NATIVE_PATH(env, toPath);
    } END_NATIVE_PATH(env, fromPath);
    return rv;
}


JNIEXPORT jboolean JNICALL
Java_java_io_Win32FileSystem_setLastModifiedTime(JNIEnv *env, jobject this,
                                                 jobject file, jlong time)
{
    jboolean rv = JNI_FALSE;

    WITH_NATIVE_PATH(env, file, ids.path, path) {
        HANDLE h;
        h = CreateFile(path, GENERIC_WRITE, 0, NULL, OPEN_EXISTING,
                       FILE_ATTRIBUTE_NORMAL | FILE_FLAG_BACKUP_SEMANTICS, 0);
        if (h != INVALID_HANDLE_VALUE) {
            LARGE_INTEGER modTime;
            FILETIME t;
            modTime.QuadPart = (time + 11644473600000L) * 10000L;
            t.dwLowDateTime = (DWORD)modTime.LowPart;
            t.dwHighDateTime = (DWORD)modTime.HighPart;
            if (SetFileTime(h, NULL, NULL, &t)) {
                rv = JNI_TRUE;
            }
            CloseHandle(h);
        }
    } END_NATIVE_PATH(env, path);

    return rv;
}


JNIEXPORT jboolean JNICALL
Java_java_io_Win32FileSystem_setReadOnly(JNIEnv *env, jobject this,
                                         jobject file)
{
    jboolean rv = JNI_FALSE;

    WITH_NATIVE_PATH(env, file, ids.path, path) {
        DWORD a;
        a = GetFileAttributes(path);
        if (a != INVALID_FILE_ATTRIBUTES) {
            if (SetFileAttributes(path, a | FILE_ATTRIBUTE_READONLY))
                rv = JNI_TRUE;
        }
    } END_NATIVE_PATH(env, path);
    return rv;
}


/* -- Filesystem interface -- */


JNIEXPORT jobject JNICALL
Java_java_io_Win32FileSystem_getDriveDirectory(JNIEnv *env, jclass ignored,
                                               jint drive)
{
    char buf[_MAX_PATH];
    char *p = _getdcwd(drive, buf, sizeof(buf));
    if (p == NULL) return NULL;
    if (isalpha(*p) && (p[1] == ':')) p += 2;
    return JNU_NewStringPlatform(env, p);
}


JNIEXPORT jint JNICALL
Java_java_io_Win32FileSystem_listRoots0(JNIEnv *env, jclass ignored)
{
    return GetLogicalDrives();
}

JNIEXPORT jlong JNICALL
Java_java_io_Win32FileSystem_getSpace0(JNIEnv *env, jobject this,
                                       jobject file, jint t)
{
    jlong rv = 0L;

    WITH_NATIVE_PATH(env, file, ids.path, path) {
        ULARGE_INTEGER totalSpace, freeSpace, usableSpace;
        if (GetDiskFreeSpaceEx(path, &usableSpace, &totalSpace, &freeSpace)) {
            switch(t) {
            case java_io_FileSystem_SPACE_TOTAL:
                rv = long_to_jlong(totalSpace.QuadPart);
                break;
            case java_io_FileSystem_SPACE_FREE:
                rv = long_to_jlong(freeSpace.QuadPart);
                break;
            case java_io_FileSystem_SPACE_USABLE:
                rv = long_to_jlong(usableSpace.QuadPart);
                break;
            default:
                assert(0);
            }
        }
    } END_NATIVE_PATH(env, path);
    return rv;
}
