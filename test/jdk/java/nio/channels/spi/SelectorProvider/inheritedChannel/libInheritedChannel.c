/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * A simple launcher to launch a program as if it was launched by inetd.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <ctype.h>

#include "jni.h"

#define CHECK(X) if ((X) == 0) {printf("JNI init error line %d\n", __LINE__); _exit(1);}

static jclass unixSocketClass;
static jmethodID unixSocketCtor;

/*
 * Throws the exception of the given class name and detail message
 */
static void ThrowException(JNIEnv *env, const char *name, const char *msg) {
    jclass cls = (*env)->FindClass(env, name);
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, msg);
    }
}

/*
 * Convert a jstring to an ISO 8859_1 encoded C string
 */
static char* getString8859_1Chars(JNIEnv *env, jstring jstr) {
    int i;
    char *result;
    jint len = (*env)->GetStringLength(env, jstr);
    const jchar *str = (*env)->GetStringCritical(env, jstr, 0);
    if (str == 0) {
        return NULL;
    }

    result = (char*)malloc(len+1);
    if (result == 0) {
        (*env)->ReleaseStringCritical(env, jstr, str);
        ThrowException(env, "java/lang/OutOfMemoryError", NULL);
        return NULL;
    }

    for (i=0; i<len; i++) {
        jchar unicode = str[i];
        if (unicode <= 0x00ff)
            result[i] = unicode;
        else
            result[i] = '?';
    }

    result[len] = 0;
    (*env)->ReleaseStringCritical(env, jstr, str);
    return result;
}


/*
 * Class:     Launcher
 * Method:    launch0
 * Signature: ([Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_Launcher_launch0
  (JNIEnv *env, jclass cls, jobjectArray cmdarray, jint serviceFd)
{
    pid_t pid;
    DIR* dp;
    struct dirent* dirp;
    int thisFd;
    char** cmdv;
    int i, cmdlen;

    /*
     * Argument 0 of the command array is the program name.
     * Here we just extract the program name and any arguments into
     * a command array suitable for use with execvp.
     */
    cmdlen = (*env)->GetArrayLength(env, cmdarray);
    if (cmdlen == 0) {
        ThrowException(env, "java/lang/IllegalArgumentException",
            "command array must at least include the program name");
        return;
    }
    cmdv = (char **)malloc((cmdlen + 1) * sizeof(char *));
    if (cmdv == NULL) {
        ThrowException(env, "java/lang/OutOfMemoryError", NULL);
        return;
    }

    for (i=0; i<cmdlen; i++) {
        jstring str = (*env)->GetObjectArrayElement(env, cmdarray, i);
        cmdv[i] = (char *) getString8859_1Chars(env, str);
        if (cmdv[i] == NULL) {
            return;
        }
    }

    /*
     * Command array must have NULL as the last entry
     */
    cmdv[cmdlen] = NULL;

    /*
     * Launch the program. As this isn't a complete inetd or Runtime.exec
     * implementation we don't have a reaper to pick up child exit status.
     */
#ifdef __solaris__
    pid = fork1();
#else
    pid = fork();
#endif
    if (pid != 0) {
        if (pid < 0) {
            ThrowException(env, "java/io/IOException", "fork failed");
        }
        return;
    }

    /*
     * We need to close all file descriptors except for serviceFd. To
     * get the list of open file descriptos we read through /proc/self/fd (/dev/fd)
     * but to open this requires a file descriptor. We could use a specific
     * file descriptor and fdopendir but Linux doesn't seem to support
     * fdopendir. Instead we use opendir and make an assumption on the
     * file descriptor that is used (by opening & closing a file).
     */
    thisFd = open("/dev/fd", O_RDONLY);
    if (thisFd < 0) {
        _exit(-1);
    }

    if ((dp = fdopendir(thisFd)) == NULL) {
        _exit(-1);
    }

    while ((dirp = readdir(dp)) != NULL) {
        if (isdigit(dirp->d_name[0])) {
            int fd = strtol(dirp->d_name, NULL, 10);
            if (fd != serviceFd && fd != thisFd) {
                close(fd);
            }
        }
    }
    closedir(dp);

    /*
     * At this point all file descriptors are closed except for
     * serviceFd. We not dup 0,1,2 to this file descriptor and
     * close serviceFd. This should leave us with only 0,1,2
     * open and all connected to the same socket.
     */
    dup2(serviceFd, STDIN_FILENO);
    dup2(serviceFd, STDOUT_FILENO);
    dup2(serviceFd, STDERR_FILENO);
    close(serviceFd);

    execvp(cmdv[0], cmdv);
    _exit(-1);
}

JNIEXPORT void JNICALL Java_UnixDomainSocket_init(JNIEnv *env, jclass cls) {
    CHECK(unixSocketClass = (*env)->FindClass(env, "UnixDomainSocket"));
    CHECK(unixSocketClass = (*env)->NewGlobalRef(env, unixSocketClass));
    CHECK(unixSocketCtor = (*env)->GetMethodID(env, unixSocketClass, "<init>", "(I)V"));
}

/*
 * Class:     UnixDomainSocket
 * Method:    socketpair
 * Signature: ()[LUnixDomainSocket
 */
JNIEXPORT jobjectArray JNICALL Java_UnixDomainSocket_socketpair
  (JNIEnv *env, jclass cls)
{
    int fds[2];
    jobject socket;
    jobjectArray result = (*env)->NewObjectArray(env, 2, unixSocketClass, 0);
    if (socketpair(AF_UNIX, SOCK_STREAM, 0, fds) < 0) {
        perror("socketpair");
        return result;
    }
    socket = (*env)->NewObject(env, unixSocketClass, unixSocketCtor, fds[0]);
    (*env)->SetObjectArrayElement(env, result, 0, socket);
    socket = (*env)->NewObject(env, unixSocketClass, unixSocketCtor, fds[1]);
    (*env)->SetObjectArrayElement(env, result, 1, socket);
    return result;
}

JNIEXPORT jint JNICALL Java_UnixDomainSocket_create
  (JNIEnv *env, jclass cls)
{
    int sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (sock == -1) {
        ThrowException(env, "java/io/IOException", "socket create error");
    }
    return sock;
}

JNIEXPORT void JNICALL Java_UnixDomainSocket_bind0
  (JNIEnv *env, jclass cls, jint sock, jstring name)
{
    struct sockaddr_un addr;
    const char *nameUtf = (*env)->GetStringUTFChars(env, name, NULL);
    int ret = -1;
    int length = sizeof(addr.sun_path);
    unlink(nameUtf);
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, nameUtf, length);
    addr.sun_path[length - 1] = '\0';
    ret = bind(sock, (const struct sockaddr*)&addr, sizeof(addr));
    if (ret == -1) {
        ThrowException(env, "java/io/IOException", "socket bind error");
    }
    ret = listen(sock, 5);
    if (ret == -1) {
        ThrowException(env, "java/io/IOException", "socket bind error");
    }
    (*env)->ReleaseStringUTFChars(env, name, nameUtf);
}

JNIEXPORT jint JNICALL Java_UnixDomainSocket_accept0
  (JNIEnv *env, jclass cls, jint sock)
{
    struct sockaddr_storage addr;
    socklen_t len = sizeof(addr);
    int ret = accept(sock, (struct sockaddr *)&addr, &len);
    if (ret == -1)
        ThrowException(env, "java/io/IOException", "socket accept error");
    return ret;
}

JNIEXPORT void JNICALL Java_UnixDomainSocket_connect0
  (JNIEnv *env, jclass cls, jint fd, jstring name)
{
    struct sockaddr_un addr;
    const char *nameUtf = (*env)->GetStringUTFChars(env, name, NULL);
    int ret = -1;
    int length = sizeof(addr.sun_path);
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, nameUtf, length);
    addr.sun_path[length - 1] = '\0';
    ret = connect(fd, (const struct sockaddr*)&addr, sizeof(addr));
    if (ret == -1) {
        ThrowException(env, "java/io/IOException", "socket connect error");
    }
    (*env)->ReleaseStringUTFChars(env, name, nameUtf);
}


JNIEXPORT jint JNICALL Java_UnixDomainSocket_read0
  (JNIEnv *env, jclass cls, jint fd)
{
    int ret;
    unsigned char res;
    ret = read(fd, &res, 1);
    if (ret == 0)
        return -1; /* EOF */
    else if (ret < 0) {
        ThrowException(env, "java/io/IOException", "read error");
        return -1;
    }
    return res;
}

JNIEXPORT void JNICALL Java_UnixDomainSocket_write0
  (JNIEnv *env, jclass cls, jint fd, jint byte)
{
    int ret;
    unsigned char w = (unsigned char)byte;
    ret = write(fd, &w, 1);
    if (ret < 0) {
        ThrowException(env, "java/io/IOException", "write error");
    }
}

JNIEXPORT void JNICALL Java_UnixDomainSocket_close0
  (JNIEnv *env, jclass cls, jint fd, jstring name)
{
    close(fd);
    if (name != NULL) {
        const char *nameUtf = (*env)->GetStringUTFChars(env, name, NULL);
        unlink(nameUtf);
        (*env)->ReleaseStringUTFChars(env, name, nameUtf);
    }
}
