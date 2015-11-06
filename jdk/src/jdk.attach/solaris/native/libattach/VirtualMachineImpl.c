/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include <sys/types.h>
#include <sys/stat.h>
#include <door.h>
#include <stdlib.h>
#include <unistd.h>
#include <signal.h>
#include <string.h>
#include <fcntl.h>
#include <errno.h>
#include <limits.h>

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"

#include "sun_tools_attach_VirtualMachineImpl.h"

#define RESTARTABLE(_cmd, _result) do { \
  do { \
    _result = _cmd; \
  } while((_result == -1) && (errno == EINTR)); \
} while(0)

/*
 * Declare library specific JNI_Onload entry if static build
 */
DEF_STATIC_JNI_OnLoad

/*
 * Class:     sun_tools_attach_VirtualMachineImpl
 * Method:    open
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_sun_tools_attach_VirtualMachineImpl_open
  (JNIEnv *env, jclass cls, jstring path)
{
    jboolean isCopy;
    const char* p = GetStringPlatformChars(env, path, &isCopy);
    if (p == NULL) {
        return 0;
    } else {
        int fd;
        int err = 0;

        fd = open(p, O_RDWR);
        if (fd == -1) {
            err = errno;
        }

        if (isCopy) {
            JNU_ReleaseStringPlatformChars(env, path, p);
        }

        if (fd == -1) {
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
        return fd;
    }
}

/*
 * Class:     sun_tools_attach_VirtualMachineImpl
 * Method:    checkPermissions
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_VirtualMachineImpl_checkPermissions
  (JNIEnv *env, jclass cls, jstring path)
{
    jboolean isCopy;
    const char* p = GetStringPlatformChars(env, path, &isCopy);
    if (p != NULL) {
        struct stat64 sb;
        uid_t uid, gid;
        int res;

        /*
         * Check that the path is owned by the effective uid/gid of this
         * process. Also check that group/other access is not allowed.
         */
        uid = geteuid();
        gid = getegid();

        res = stat64(p, &sb);
        if (res != 0) {
            /* save errno */
            res = errno;
        }

        if (res == 0) {
            char msg[100];
            jboolean isError = JNI_FALSE;
            if (sb.st_uid != uid) {
                jio_snprintf(msg, sizeof(msg)-1,
                    "file should be owned by the current user (which is %d) but is owned by %d", uid, sb.st_uid);
                isError = JNI_TRUE;
            } else if (sb.st_gid != gid) {
                jio_snprintf(msg, sizeof(msg)-1,
                    "file's group should be the current group (which is %d) but the group is %d", gid, sb.st_gid);
                isError = JNI_TRUE;
            } else if ((sb.st_mode & (S_IRGRP|S_IWGRP|S_IROTH|S_IWOTH)) != 0) {
                jio_snprintf(msg, sizeof(msg)-1,
                    "file should only be readable and writable by the owner but has 0%03o access", sb.st_mode & 0777);
                isError = JNI_TRUE;
            }
            if (isError) {
                char buf[256];
                jio_snprintf(buf, sizeof(buf)-1, "well-known file %s is not secure: %s", p, msg);
                JNU_ThrowIOException(env, buf);
            }
        } else {
            char* msg = strdup(strerror(res));
            JNU_ThrowIOException(env, msg);
            if (msg != NULL) {
                free(msg);
            }
        }

        if (isCopy) {
            JNU_ReleaseStringPlatformChars(env, path, p);
        }
    }
}

/*
 * Class:     sun_tools_attach_VirtualMachineImpl
 * Method:    close
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_VirtualMachineImpl_close
  (JNIEnv *env, jclass cls, jint fd)
{
    int ret;
    RESTARTABLE(close(fd), ret);
}

/*
 * Class:     sun_tools_attach_VirtualMachineImpl
 * Method:    read
 * Signature: (I[BI)I
 */
JNIEXPORT jint JNICALL Java_sun_tools_attach_VirtualMachineImpl_read
  (JNIEnv *env, jclass cls, jint fd, jbyteArray ba, jint off, jint baLen)
{
    unsigned char buf[128];
    size_t len = sizeof(buf);
    ssize_t n;

    size_t remaining = (size_t)(baLen - off);
    if (len > remaining) {
        len = remaining;
    }

    RESTARTABLE(read(fd, buf, len), n);
    if (n == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "read");
    } else {
        if (n == 0) {
            n = -1;     // EOF
        } else {
            (*env)->SetByteArrayRegion(env, ba, off, (jint)n, (jbyte *)(buf));
        }
    }
    return n;
}

/*
 * Class:     sun_tools_attach_VirtualMachineImpl
 * Method:    sigquit
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_tools_attach_VirtualMachineImpl_sigquit
  (JNIEnv *env, jclass cls, jint pid)
{
    if (kill((pid_t)pid, SIGQUIT) == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "kill");
    }
}

/*
 * A simple table to translate some known errors into reasonable
 * error messages
 */
static struct {
    jint err;
    const char* msg;
} const error_messages[] = {
    { 100,      "Bad request" },
    { 101,      "Protocol mismatch" },
    { 102,      "Resource failure" },
    { 103,      "Internal error" },
    { 104,      "Permission denied" },
};

/*
 * Lookup the given error code and return the appropriate
 * message. If not found return NULL.
 */
static const char* translate_error(jint err) {
    int table_size = sizeof(error_messages) / sizeof(error_messages[0]);
    int i;

    for (i=0; i<table_size; i++) {
        if (err == error_messages[i].err) {
            return error_messages[i].msg;
        }
    }
    return NULL;
}

/*
 * Current protocol version
 */
static const char* PROTOCOL_VERSION = "1";

/*
 * Class:     sun_tools_attach_VirtualMachineImpl
 * Method:    enqueue
 * Signature: (JILjava/lang/String;[Ljava/lang/Object;)V
 */
JNIEXPORT jint JNICALL Java_sun_tools_attach_VirtualMachineImpl_enqueue
  (JNIEnv *env, jclass cls, jint fd, jstring cmd, jobjectArray args)
{
    jint arg_count, i;
    size_t size;
    jboolean isCopy;
    door_arg_t door_args;
    char res_buffer[128];
    jint result = -1;
    int rc;
    const char* cstr;
    char* buf;

    /*
     * First we get the command string and create the start of the
     * argument string to send to the target VM:
     * <ver>\0<cmd>\0
     */
    cstr = JNU_GetStringPlatformChars(env, cmd, &isCopy);
    if (cstr == NULL) {
        return -1;              /* pending exception */
    }
    size = strlen(PROTOCOL_VERSION) + strlen(cstr) + 2;
    buf = (char*)malloc(size);
    if (buf != NULL) {
        char* pos = buf;
        strcpy(buf, PROTOCOL_VERSION);
        pos += strlen(PROTOCOL_VERSION)+1;
        strcpy(pos, cstr);
    }
    if (isCopy) {
        JNU_ReleaseStringPlatformChars(env, cmd, cstr);
    }
    if (buf == NULL) {
        JNU_ThrowOutOfMemoryError(env, "malloc failed");
        return -1;
    }

    /*
     * Next we iterate over the arguments and extend the buffer
     * to include them.
     */
    arg_count = (*env)->GetArrayLength(env, args);

    for (i=0; i<arg_count; i++) {
        jobject obj = (*env)->GetObjectArrayElement(env, args, i);
        if (obj != NULL) {
            cstr = JNU_GetStringPlatformChars(env, obj, &isCopy);
            if (cstr != NULL) {
                size_t len = strlen(cstr);
                char* newbuf = (char*)realloc(buf, size+len+1);
                if (newbuf != NULL) {
                    buf = newbuf;
                    strcpy(buf+size, cstr);
                    size += len+1;
                }
                if (isCopy) {
                    JNU_ReleaseStringPlatformChars(env, obj, cstr);
                }
                if (newbuf == NULL) {
                    free(buf);
                    JNU_ThrowOutOfMemoryError(env, "realloc failed");
                    return -1;
                }
            }
        }
        if ((*env)->ExceptionOccurred(env)) {
            free(buf);
            return -1;
        }
    }

    /*
     * The arguments to the door function are in 'buf' so we now
     * do the door call
     */
    door_args.data_ptr = buf;
    door_args.data_size = size;
    door_args.desc_ptr = NULL;
    door_args.desc_num = 0;
    door_args.rbuf = (char*)&res_buffer;
    door_args.rsize = sizeof(res_buffer);

    RESTARTABLE(door_call(fd, &door_args), rc);

    /*
     * door_call failed
     */
    if (rc == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "door_call");
    } else {
        /*
         * door_call succeeded but the call didn't return the expected jint.
         */
        if (door_args.data_size < sizeof(jint)) {
            JNU_ThrowIOException(env, "Enqueue error - reason unknown as result is truncated!");
        } else {
            jint* res = (jint*)(door_args.data_ptr);
            if (*res != JNI_OK) {
                const char* msg = translate_error(*res);
                char buf[255];
                if (msg == NULL) {
                    sprintf(buf, "Unable to enqueue command to target VM: %d", *res);
                } else {
                    sprintf(buf, "Unable to enqueue command to target VM: %s", msg);
                }
                JNU_ThrowIOException(env, buf);
            } else {
                /*
                 * The door call should return a file descriptor to one end of
                 * a socket pair
                 */
                if ((door_args.desc_ptr != NULL) &&
                    (door_args.desc_num == 1) &&
                    (door_args.desc_ptr->d_attributes & DOOR_DESCRIPTOR)) {
                    result = door_args.desc_ptr->d_data.d_desc.d_descriptor;
                } else {
                    JNU_ThrowIOException(env, "Reply from enqueue missing descriptor!");
                }
            }
        }
    }

    free(buf);
    return result;
}
