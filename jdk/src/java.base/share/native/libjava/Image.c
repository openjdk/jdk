/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>

#include "jni.h"
#include "jvm.h"
#include "jdk_internal_jimage_ImageNativeSubstrate.h"

JNIEXPORT jlong JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_openImage(JNIEnv *env,
        jclass cls, jstring path, jboolean big_endian) {
    const char *nativePath;
    jlong ret;

    nativePath = (*env)->GetStringUTFChars(env, path, NULL);
    ret = JVM_ImageOpen(env, nativePath, big_endian);
    (*env)->ReleaseStringUTFChars(env, path, nativePath);
    return ret;
}

JNIEXPORT void JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_closeImage(JNIEnv *env,
                                        jclass cls, jlong id) {
    JVM_ImageClose(env, id);
}

JNIEXPORT jlong JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_getIndexAddress(JNIEnv *env,
                jclass cls, jlong id) {
 return JVM_ImageGetIndexAddress(env, id);
}

JNIEXPORT jlong JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_getDataAddress(JNIEnv *env,
                jclass cls, jlong id) {
 return JVM_ImageGetDataAddress(env, id);
}

JNIEXPORT jboolean JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_read(JNIEnv *env,
                                        jclass cls, jlong id, jlong offset,
        jobject uncompressedBuffer, jlong uncompressed_size) {
    unsigned char* uncompressedAddress;

    uncompressedAddress = (unsigned char*) (*env)->GetDirectBufferAddress(env, uncompressedBuffer);
    if (uncompressedBuffer == NULL) {
      return JNI_FALSE;
    }
    return JVM_ImageRead(env, id, offset, uncompressedAddress, uncompressed_size);
}

JNIEXPORT jboolean JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_readCompressed(JNIEnv *env,
                                        jclass cls, jlong id, jlong offset,
                                        jobject compressedBuffer, jlong compressed_size,
        jobject uncompressedBuffer, jlong uncompressed_size) {
    // Get address of read direct buffer.
    unsigned char* compressedAddress;
    unsigned char* uncompressedAddress;

    compressedAddress = (unsigned char*) (*env)->GetDirectBufferAddress(env, compressedBuffer);
    // Get address of decompression direct buffer.
    uncompressedAddress = (unsigned char*) (*env)->GetDirectBufferAddress(env, uncompressedBuffer);
    if (uncompressedBuffer == NULL || compressedBuffer == NULL) {
      return JNI_FALSE;
    }
    return JVM_ImageReadCompressed(env, id, offset, compressedAddress, compressed_size,
            uncompressedAddress, uncompressed_size);
}

JNIEXPORT jbyteArray JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_getStringBytes(JNIEnv *env,
                                        jclass cls, jlong id, jint offset) {
    const char* data;
    size_t size;
    jbyteArray byteArray;
    jbyte* rawBytes;

    data = JVM_ImageGetStringBytes(env, id, offset);
    // Determine String length.
    size = strlen(data);
    // Allocate byte array.
    byteArray = (*env)->NewByteArray(env, (jsize) size);
    if (byteArray == NULL) {
        return NULL;
    }
    // Get array base address.
    rawBytes = (*env)->GetByteArrayElements(env, byteArray, NULL);
    // Copy bytes from image string table.
    memcpy(rawBytes, data, size);
    // Release byte array base address.
    (*env)->ReleaseByteArrayElements(env, byteArray, rawBytes, 0);
    return byteArray;
}

JNIEXPORT jlongArray JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_getAttributes(JNIEnv *env,
        jclass cls, jlong id, jint offset) {
    // Allocate a jlong large enough for all location attributes.
    jlongArray attributes;
    jlong* rawAttributes;
    jlong* ret;

    attributes = (*env)->NewLongArray(env, JVM_ImageGetAttributesCount(env));
    if (attributes == NULL) {
        return NULL;
    }
    // Get base address for jlong array.
    rawAttributes = (*env)->GetLongArrayElements(env, attributes, NULL);
    ret = JVM_ImageGetAttributes(env, rawAttributes, id, offset);
    // Release jlong array base address.
    (*env)->ReleaseLongArrayElements(env, attributes, rawAttributes, 0);
    return ret == NULL ? NULL : attributes;
}

JNIEXPORT jlongArray JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_findAttributes(JNIEnv *env,
        jclass cls, jlong id, jbyteArray utf8) {
    // Allocate a jlong large enough for all location attributes.
    jsize count;
    jlongArray attributes;
    jlong* rawAttributes;
    jsize size;
    jbyte* rawBytes;
    jlong* ret;

    count = JVM_ImageGetAttributesCount(env);
    attributes = (*env)->NewLongArray(env, JVM_ImageGetAttributesCount(env));
    if (attributes == NULL) {
        return NULL;
    }
    // Get base address for jlong array.
    rawAttributes = (*env)->GetLongArrayElements(env, attributes, NULL);
    size = (*env)->GetArrayLength(env, utf8);
    rawBytes = (*env)->GetByteArrayElements(env, utf8, NULL);
    ret = JVM_ImageFindAttributes(env, rawAttributes, rawBytes, size, id);
    (*env)->ReleaseByteArrayElements(env, utf8, rawBytes, 0);
    // Release jlong array base address.
    (*env)->ReleaseLongArrayElements(env, attributes, rawAttributes, 0);
    return ret == NULL ? NULL : attributes;

}

JNIEXPORT jintArray JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_attributeOffsets(JNIEnv *env,
        jclass cls, jlong id) {
    unsigned int length;
    jintArray offsets;
    jint* rawOffsets;
    jint* ret;

    length = JVM_ImageAttributeOffsetsLength(env, id);
    offsets = (*env)->NewIntArray(env, length);
    if (offsets == NULL) {
        return NULL;
    }
    // Get base address of result.
    rawOffsets = (*env)->GetIntArrayElements(env, offsets, NULL);
    ret = JVM_ImageAttributeOffsets(env, rawOffsets, length, id);
    if (length == 0) {
        return NULL;
    }
    // Release result base address.
    (*env)->ReleaseIntArrayElements(env, offsets, rawOffsets, 0);
    return ret == NULL ? NULL : offsets;
}
