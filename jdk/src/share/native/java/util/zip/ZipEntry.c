/*
 * Copyright 1998 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * Native method support for java.util.zip.ZipEntry
 */

#include <stdio.h>
#include <stdlib.h>
#include "jlong.h"
#include "jvm.h"
#include "jni.h"
#include "jni_util.h"
#include "zip_util.h"

#include "java_util_zip_ZipEntry.h"

#define DEFLATED 8
#define STORED 0

static jfieldID nameID;
static jfieldID timeID;
static jfieldID crcID;
static jfieldID sizeID;
static jfieldID csizeID;
static jfieldID methodID;
static jfieldID extraID;
static jfieldID commentID;

JNIEXPORT void JNICALL
Java_java_util_zip_ZipEntry_initIDs(JNIEnv *env, jclass cls)
{
    nameID = (*env)->GetFieldID(env, cls, "name", "Ljava/lang/String;");
    timeID = (*env)->GetFieldID(env, cls, "time", "J");
    crcID = (*env)->GetFieldID(env, cls, "crc", "J");
    sizeID = (*env)->GetFieldID(env, cls, "size", "J");
    csizeID = (*env)->GetFieldID(env, cls, "csize", "J");
    methodID = (*env)->GetFieldID(env, cls, "method", "I");
    extraID = (*env)->GetFieldID(env, cls, "extra", "[B");
    commentID = (*env)->GetFieldID(env, cls, "comment", "Ljava/lang/String;");
}

JNIEXPORT void JNICALL
Java_java_util_zip_ZipEntry_initFields(JNIEnv *env, jobject obj, jlong zentry)
{
    jzentry *ze = jlong_to_ptr(zentry);
    jstring name = (*env)->GetObjectField(env, obj, nameID);

    if (name == 0) {
        name = (*env)->NewStringUTF(env, ze->name);
        if (name == 0) {
            return;
        }
        (*env)->SetObjectField(env, obj, nameID, name);
    }
    (*env)->SetLongField(env, obj, timeID, (jlong)ze->time & 0xffffffffUL);
    (*env)->SetLongField(env, obj, crcID, (jlong)ze->crc & 0xffffffffUL);
    (*env)->SetLongField(env, obj, sizeID, (jlong)ze->size);
    if (ze->csize == 0) {
        (*env)->SetLongField(env, obj, csizeID, (jlong)ze->size);
        (*env)->SetIntField(env, obj, methodID, STORED);
    } else {
        (*env)->SetLongField(env, obj, csizeID, (jlong)ze->csize);
        (*env)->SetIntField(env, obj, methodID, DEFLATED);
    }
    if (ze->extra != 0) {
        unsigned char *bp = (unsigned char *)&ze->extra[0];
        jsize len = (bp[0] | (bp[1] << 8));
        jbyteArray extra = (*env)->NewByteArray(env, len);
        if (extra == 0) {
            return;
        }
        (*env)->SetByteArrayRegion(env, extra, 0, len, &ze->extra[2]);
        (*env)->SetObjectField(env, obj, extraID, extra);
    }
    if (ze->comment != 0) {
        jstring comment = (*env)->NewStringUTF(env, ze->comment);
        if (comment == 0) {
            return;
        }
        (*env)->SetObjectField(env, obj, commentID, comment);
    }
}
