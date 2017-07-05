/*
 * Copyright 2005, 2012, Oracle and/or its affiliates. All Rights Reserved.
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

#include "jni.h"
#include "jni_util.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <signal.h>
#include <dirent.h>
#include <ctype.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syslimits.h>
#include <sys/un.h>
#include <fcntl.h>

#include "sun_tools_attach_BsdVirtualMachine.h"

#define RESTARTABLE(_cmd, _result) do { \
  do { \
    _result = _cmd; \
  } while((_result == -1) && (errno == EINTR)); \
} while(0)

/*
 * Class:     sun_tools_attach_BsdVirtualMachine
 * Method:    socket
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_tools_attach_BsdVirtualMachine_socket
  (JNIEnv *env, jclass cls)
{
    int fd = socket(PF_UNIX, SOCK_STREAM, 0);
    if (fd == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "socket");
    }
    return (jint)fd;
}

/*
 * Class:     sun_tools_attach_BsdVirtualMachine
 * Method:    connect
 * Signature: (ILjava/lang/String;)I
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_BsdVirtualMachine_connect
  (JNIEnv *env, jclass cls, jint fd, jstring path)
{
    jboolean isCopy;
    const char* p = GetStringPlatformChars(env, path, &isCopy);
    if (p != NULL) {
        struct sockaddr_un addr;
        int err = 0;

        addr.sun_family = AF_UNIX;
        strcpy(addr.sun_path, p);

        if (connect(fd, (struct sockaddr*)&addr, sizeof(addr)) == -1) {
            err = errno;
        }

        if (isCopy) {
            JNU_ReleaseStringPlatformChars(env, path, p);
        }

        /*
         * If the connect failed then we throw the appropriate exception
         * here (can't throw it before releasing the string as can't call
         * JNI with pending exception)
         */
        if (err != 0) {
            if (err == ENOENT) {
                JNU_ThrowByName(env, "java/io/FileNotFoundException", NULL);
            } else {
                char* msg = strdup(strerror(err));
                JNU_ThrowIOException(env, msg);
                if (msg != NULL) {
                    free(msg);
                }
            }
        }
    }
}

/*
 * Class:     sun_tools_attach_BsdVirtualMachine
 * Method:    sendQuitTo
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_BsdVirtualMachine_sendQuitTo
  (JNIEnv *env, jclass cls, jint pid)
{
    if (kill((pid_t)pid, SIGQUIT)) {
        JNU_ThrowIOExceptionWithLastError(env, "kill");
    }
}

/*
 * Class:     sun_tools_attach_BsdVirtualMachine
 * Method:    checkPermissions
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_BsdVirtualMachine_checkPermissions
  (JNIEnv *env, jclass cls, jstring path)
{
    jboolean isCopy;
    const char* p = GetStringPlatformChars(env, path, &isCopy);
    if (p != NULL) {
        struct stat sb;
        uid_t uid, gid;
        int res;

        /*
         * Check that the path is owned by the effective uid/gid of this
         * process. Also check that group/other access is not allowed.
         */
        uid = geteuid();
        gid = getegid();

        res = stat(p, &sb);
        if (res != 0) {
            /* save errno */
            res = errno;
        }

        /* release p here before we throw an I/O exception */
        if (isCopy) {
            JNU_ReleaseStringPlatformChars(env, path, p);
        }

        if (res == 0) {
            if ( (sb.st_uid != uid) || (sb.st_gid != gid) ||
                 ((sb.st_mode & (S_IRGRP|S_IWGRP|S_IROTH|S_IWOTH)) != 0) ) {
                JNU_ThrowIOException(env, "well-known file is not secure");
            }
        } else {
            char* msg = strdup(strerror(res));
            JNU_ThrowIOException(env, msg);
            if (msg != NULL) {
                free(msg);
            }
        }
    }
}

/*
 * Class:     sun_tools_attach_BsdVirtualMachine
 * Method:    close
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_BsdVirtualMachine_close
  (JNIEnv *env, jclass cls, jint fd)
{
    int res;
    RESTARTABLE(close(fd), res);
}

/*
 * Class:     sun_tools_attach_BsdVirtualMachine
 * Method:    read
 * Signature: (I[BI)I
 */
JNIEXPORT jint JNICALL Java_sun_tools_attach_BsdVirtualMachine_read
  (JNIEnv *env, jclass cls, jint fd, jbyteArray ba, jint off, jint baLen)
{
    unsigned char buf[128];
    size_t len = sizeof(buf);
    ssize_t n;

    size_t remaining = (size_t)(baLen - off);
    if (len > remaining) {
        len = remaining;
    }

    RESTARTABLE(read(fd, buf+off, len), n);
    if (n == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "read");
    } else {
        if (n == 0) {
            n = -1;     // EOF
        } else {
            (*env)->SetByteArrayRegion(env, ba, off, (jint)n, (jbyte *)(buf+off));
        }
    }
    return n;
}

/*
 * Class:     sun_tools_attach_BsdVirtualMachine
 * Method:    write
 * Signature: (I[B)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_BsdVirtualMachine_write
  (JNIEnv *env, jclass cls, jint fd, jbyteArray ba, jint off, jint bufLen)
{
    size_t remaining = bufLen;
    do {
        unsigned char buf[128];
        size_t len = sizeof(buf);
        int n;

        if (len > remaining) {
            len = remaining;
        }
        (*env)->GetByteArrayRegion(env, ba, off, len, (jbyte *)buf);

        RESTARTABLE(write(fd, buf, len), n);
        if (n > 0) {
           off += n;
           remaining -= n;
        } else {
            JNU_ThrowIOExceptionWithLastError(env, "write");
            return;
        }

    } while (remaining > 0);
}

/*
 * Class:     sun_tools_attach_BSDVirtualMachine
 * Method:    createAttachFile
 * Signature: (Ljava.lang.String;)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_BsdVirtualMachine_createAttachFile(JNIEnv *env, jclass cls, jstring path)
{
    const char* _path;
    jboolean isCopy;
    int fd, rc;

    _path = GetStringPlatformChars(env, path, &isCopy);
    if (_path == NULL) {
        JNU_ThrowIOException(env, "Must specify a path");
        return;
    }

    RESTARTABLE(open(_path, O_CREAT | O_EXCL, S_IWUSR | S_IRUSR), fd);
    if (fd == -1) {
        /* release p here before we throw an I/O exception */
        if (isCopy) {
            JNU_ReleaseStringPlatformChars(env, path, _path);
        }
        JNU_ThrowIOExceptionWithLastError(env, "open");
        return;
    }

    RESTARTABLE(chown(_path, geteuid(), getegid()), rc);

    RESTARTABLE(close(fd), rc);

    /* release p here */
    if (isCopy) {
        JNU_ReleaseStringPlatformChars(env, path, _path);
    }
}

/*
 * Class:     sun_tools_attach_BSDVirtualMachine
 * Method:    getTempDir
 * Signature: (V)Ljava.lang.String;
 */
JNIEXPORT jstring JNICALL Java_sun_tools_attach_BsdVirtualMachine_getTempDir(JNIEnv *env, jclass cls)
{
    // This must be hard coded because it's the system's temporary
    // directory not the java application's temp directory, ala java.io.tmpdir.

#ifdef __APPLE__
    // macosx has a secure per-user temporary directory
    static char *temp_path = NULL;
    char temp_path_storage[PATH_MAX];
    if (temp_path == NULL) {
        int pathSize = confstr(_CS_DARWIN_USER_TEMP_DIR, temp_path_storage, PATH_MAX);
        if (pathSize == 0 || pathSize > PATH_MAX) {
            strlcpy(temp_path_storage, "/tmp", sizeof(temp_path_storage));
        }
        temp_path = temp_path_storage;
    }
    return JNU_NewStringPlatform(env, temp_path);
#else /* __APPLE__ */
    return (*env)->NewStringUTF(env, "/tmp");
#endif /* __APPLE__ */
}
