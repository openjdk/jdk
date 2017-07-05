/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

/*
 * KQueueArrayWrapper.c
 * Implementation of Selector using FreeBSD / Mac OS X kqueues
 * Derived from Sun's DevPollArrayWrapper
 */


#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"

#include <sys/types.h>
#include <sys/event.h>
#include <sys/time.h>

JNIEXPORT void JNICALL
Java_sun_nio_ch_KQueueArrayWrapper_initStructSizes(JNIEnv *env, jclass clazz)
{
#define CHECK_EXCEPTION() { \
    if ((*env)->ExceptionCheck(env)) { \
        goto exceptionOccurred; \
    } \
}

#define CHECK_ERROR_AND_EXCEPTION(_field) { \
    if (_field == NULL) { \
        goto badField; \
    } \
    CHECK_EXCEPTION(); \
}


    jfieldID field;

    field = (*env)->GetStaticFieldID(env, clazz, "EVFILT_READ", "S");
    CHECK_ERROR_AND_EXCEPTION(field);
    (*env)->SetStaticShortField(env, clazz, field, EVFILT_READ);
    CHECK_EXCEPTION();

    field = (*env)->GetStaticFieldID(env, clazz, "EVFILT_WRITE", "S");
    CHECK_ERROR_AND_EXCEPTION(field);
    (*env)->SetStaticShortField(env, clazz, field, EVFILT_WRITE);
    CHECK_EXCEPTION();

    field = (*env)->GetStaticFieldID(env, clazz, "SIZEOF_KEVENT", "S");
    CHECK_ERROR_AND_EXCEPTION(field);
    (*env)->SetStaticShortField(env, clazz, field, (short) sizeof(struct kevent));
    CHECK_EXCEPTION();

    field = (*env)->GetStaticFieldID(env, clazz, "FD_OFFSET", "S");
    CHECK_ERROR_AND_EXCEPTION(field);
    (*env)->SetStaticShortField(env, clazz, field, (short) offsetof(struct kevent, ident));
    CHECK_EXCEPTION();

    field = (*env)->GetStaticFieldID(env, clazz, "FILTER_OFFSET", "S");
    CHECK_ERROR_AND_EXCEPTION(field);
    (*env)->SetStaticShortField(env, clazz, field, (short) offsetof(struct kevent, filter));
    CHECK_EXCEPTION();
    return;

badField:
    return;

exceptionOccurred:
    return;

#undef CHECK_EXCEPTION
#undef CHECK_ERROR_AND_EXCEPTION
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_KQueueArrayWrapper_init(JNIEnv *env, jobject this)
{
    int kq = kqueue();
    if (kq < 0) {
        JNU_ThrowIOExceptionWithLastError(env, "KQueueArrayWrapper: kqueue() failed");
    }
    return kq;
}


JNIEXPORT void JNICALL
Java_sun_nio_ch_KQueueArrayWrapper_register0(JNIEnv *env, jobject this,
                                             jint kq, jint fd, jint r, jint w)
{
    struct kevent changes[2];
    struct kevent errors[2];
    struct timespec dontBlock = {0, 0};

    // if (r) then { register for read } else { unregister for read }
    // if (w) then { register for write } else { unregister for write }
    // Ignore errors - they're probably complaints about deleting non-
    //   added filters - but provide an error array anyway because
    //   kqueue behaves erratically if some of its registrations fail.
    EV_SET(&changes[0], fd, EVFILT_READ,  r ? EV_ADD : EV_DELETE, 0, 0, 0);
    EV_SET(&changes[1], fd, EVFILT_WRITE, w ? EV_ADD : EV_DELETE, 0, 0, 0);
    kevent(kq, changes, 2, errors, 2, &dontBlock);
}


JNIEXPORT jint JNICALL
Java_sun_nio_ch_KQueueArrayWrapper_kevent0(JNIEnv *env, jobject this, jint kq,
                                           jlong kevAddr, jint kevCount,
                                           jlong timeout)
{
    struct kevent *kevs = (struct kevent *)jlong_to_ptr(kevAddr);
    struct timespec ts;
    struct timespec *tsp;
    int result;

    // Java timeout is in milliseconds. Convert to struct timespec.
    // Java timeout == -1 : wait forever : timespec timeout of NULL
    // Java timeout == 0  : return immediately : timespec timeout of zero
    if (timeout >= 0) {
        ts.tv_sec = timeout / 1000;
        ts.tv_nsec = (timeout % 1000) * 1000000; //nanosec = 1 million millisec
        tsp = &ts;
    } else {
        tsp = NULL;
    }

    result = kevent(kq, NULL, 0, kevs, kevCount, tsp);

    if (result < 0) {
        if (errno == EINTR) {
            // ignore EINTR, pretend nothing was selected
            result = 0;
        } else {
            JNU_ThrowIOExceptionWithLastError(env, "KQueueArrayWrapper: kqueue failed");
        }
    }

    return result;
}


JNIEXPORT void JNICALL
Java_sun_nio_ch_KQueueArrayWrapper_interrupt(JNIEnv *env, jclass cls, jint fd)
{
    char c = 1;
    if (1 != write(fd, &c, 1)) {
        JNU_ThrowIOExceptionWithLastError(env, "KQueueArrayWrapper: interrupt failed");
    }
}

