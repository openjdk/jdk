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
#include "jni_util.h"
#include "jdk_util.h"
#include "endian.hpp"
#include "imageDecompressor.hpp"
#include "imageFile.hpp"
#include "inttypes.hpp"
#include "jimage.hpp"
#include "osSupport.hpp"

#include "jdk_internal_jimage_ImageNativeSubstrate.h"

extern bool MemoryMapImage;

// jdk.internal.jimage /////////////////////////////////////////////////////////

// Java entry to open an image file for sharing.

static jlong JIMAGE_Open(JNIEnv *env, const char *nativePath, jboolean big_endian) {
    // Open image file for reading.
    ImageFileReader* reader = ImageFileReader::open(nativePath, big_endian != JNI_FALSE);
    // Return image ID as a jlong.
    return ImageFileReader::readerToID(reader);
}

// Java entry for closing a shared image file.

static void JIMAGE_Close(JNIEnv *env, jlong id) {
    // Convert image ID to image reader structure.
    ImageFileReader* reader = ImageFileReader::idToReader(id);
    // If valid reader the close.
    if (reader != NULL) {
        ImageFileReader::close(reader);
    }
}

// Java entry for accessing the base address of the image index.

static jlong JIMAGE_GetIndexAddress(JNIEnv *env, jlong id) {
    // Convert image ID to image reader structure.
    ImageFileReader* reader = ImageFileReader::idToReader(id);
    // If valid reader return index base address (as jlong) else zero.
    return reader != NULL ? (jlong) reader->get_index_address() : 0L;
}

// Java entry for accessing the base address of the image data.

static jlong JIMAGE_GetDataAddress(JNIEnv *env, jlong id) {
    // Convert image ID to image reader structure.
    ImageFileReader* reader = ImageFileReader::idToReader(id);
    // If valid reader return data base address (as jlong) else zero.
    return MemoryMapImage && reader != NULL ? (jlong) reader->get_data_address() : 0L;
}

// Java entry for reading an uncompressed resource from the image.

static jboolean JIMAGE_Read(JNIEnv *env, jlong id, jlong offset,
        unsigned char* uncompressedAddress, jlong uncompressed_size) {
    // Convert image ID to image reader structure.
    ImageFileReader* reader = ImageFileReader::idToReader(id);\
  // If not a valid reader the fail the read.
    if (reader == NULL) return false;
    // Get the file offset of resource data.
    u8 file_offset = reader->get_index_size() + offset;
    // Check validity of arguments.
    if (offset < 0 ||
            uncompressed_size < 0 ||
            file_offset > reader->file_size() - uncompressed_size) {
        return false;
    }
    // Read file content into buffer.
    return (jboolean) reader->read_at((u1*) uncompressedAddress, uncompressed_size,
            file_offset);
}

// Java entry for reading a compressed resource from the image.

static jboolean JIMAGE_ReadCompressed(JNIEnv *env,
        jlong id, jlong offset,
        unsigned char* compressedAddress, jlong compressed_size,
        unsigned char* uncompressedAddress, jlong uncompressed_size) {
    // Convert image ID to image reader structure.
    ImageFileReader* reader = ImageFileReader::idToReader(id);
    // If not a valid reader the fail the read.
    if (reader == NULL) return false;
    // Get the file offset of resource data.
    u8 file_offset = reader->get_index_size() + offset;
    // Check validity of arguments.
    if (offset < 0 ||
            compressed_size < 0 ||
            uncompressed_size < 0 ||
            file_offset > reader->file_size() - compressed_size) {
        return false;
    }

    // Read file content into buffer.
    bool is_read = reader->read_at(compressedAddress, compressed_size,
            file_offset);
    // If successfully read then decompress.
    if (is_read) {
        const ImageStrings strings = reader->get_strings();
        ImageDecompressor::decompress_resource(compressedAddress, uncompressedAddress,
                (u4) uncompressed_size, &strings);
    }
    return (jboolean) is_read;
}

// Java entry for retrieving UTF-8 bytes from image string table.

static const char* JIMAGE_GetStringBytes(JNIEnv *env, jlong id, jint offset) {
    // Convert image ID to image reader structure.
    ImageFileReader* reader = ImageFileReader::idToReader(id);
    // Fail if not valid reader.
    if (reader == NULL) return NULL;
    // Manage image string table.
    ImageStrings strings = reader->get_strings();
    // Retrieve string adrress from table.
    const char* data = strings.get(offset);
    return data;
}

// Utility function to copy location information into a jlong array.
// WARNING: This function is experimental and temporary during JDK 9 development
// cycle. It will not be supported in the eventual JDK 9 release.

static void image_expand_location(JNIEnv *env, jlong* rawAttributes, ImageLocation& location) {
    // Copy attributes from location.
    for (int kind = ImageLocation::ATTRIBUTE_END + 1;
            kind < ImageLocation::ATTRIBUTE_COUNT;
            kind++) {
        rawAttributes[kind] = location.get_attribute(kind);
    }
}

// Java entry for retrieving location attributes for attribute offset.

static jlong* JIMAGE_GetAttributes(JNIEnv *env, jlong* rawAttributes, jlong id, jint offset) {
    // Convert image ID to image reader structure.
    ImageFileReader* reader = ImageFileReader::idToReader(id);
    // Fail if not valid reader.
    if (reader == NULL) return NULL;
    // Retrieve first byte address of resource's location attribute stream.
    u1* data = reader->get_location_offset_data(offset);
    // Fail if not valid offset.
    if (data == NULL) return NULL;
    // Expand stream into array.
    ImageLocation location(data);
    image_expand_location(env, rawAttributes, location);
    return rawAttributes;
}

// Java entry for retrieving location attributes count for attribute offset.

static jsize JIMAGE_GetAttributesCount(JNIEnv *env) {
    return ImageLocation::ATTRIBUTE_COUNT;
}

// Java entry for retrieving location attributes for named resource.

static jlong* JIMAGE_FindAttributes(JNIEnv *env, jlong* rawAttributes, jbyte* rawBytes, jsize size, jlong id) {
    // Convert image ID to image reader structure.
    ImageFileReader* reader = ImageFileReader::idToReader(id);
    // Fail if not valid reader.
    if (reader == NULL) return NULL;
    // Convert byte array to a cstring.
    char* path = new char[size + 1];
    memcpy(path, rawBytes, size);
    path[size] = '\0';
    // Locate resource location data.
    ImageLocation location;
    bool found = reader->find_location(path, location);
    delete path;
    // Resource not found.
    if (!found) return NULL;
    // Expand stream into array.
    image_expand_location(env, rawAttributes, location);
    return rawAttributes;
}

// Java entry for retrieving all the attribute stream offsets from an image.

static jint* JIMAGE_AttributeOffsets(JNIEnv *env, jint* rawOffsets, unsigned int length, jlong id) {
    // Convert image ID to image reader structure.
    ImageFileReader* reader = ImageFileReader::idToReader(id);
    // Fail if not valid reader.
    if (reader == NULL) return NULL;
    // Determine endian for reader.
    Endian* endian = reader->endian();
    // Get base address of attribute stream offsets table.
    u4* offsets_table = reader->offsets_table();
    // Allocate int array result.
    // Copy values to result (converting endian.)
    for (u4 i = 0; i < length; i++) {
        rawOffsets[i] = endian->get(offsets_table[i]);
    }
    return rawOffsets;
}

// Java entry for retrieving all the attribute stream offsets length from an image.

static unsigned int JIMAGE_AttributeOffsetsLength(JNIEnv *env, jlong id) {
    // Convert image ID to image reader structure.
    ImageFileReader* reader = ImageFileReader::idToReader(id);
    // Fail if not valid reader.
    if (reader == NULL) return 0;
    // Get perfect hash table length.
    u4 length = reader->table_length();
    return (jint) length;
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_2) != JNI_OK) {
        return JNI_EVERSION; /* JNI version not supported */
    }

    return JNI_VERSION_1_2;
}

JNIEXPORT jlong JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_openImage(JNIEnv *env,
        jclass cls, jstring path, jboolean big_endian) {
    const char *nativePath;
    jlong ret;

    nativePath = env->GetStringUTFChars(path, NULL);
    ret = JIMAGE_Open(env, nativePath, big_endian);
    env->ReleaseStringUTFChars(path, nativePath);
    return ret;
}

JNIEXPORT void JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_closeImage(JNIEnv *env,
        jclass cls, jlong id) {
    JIMAGE_Close(env, id);
}

JNIEXPORT jlong JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_getIndexAddress(JNIEnv *env,
        jclass cls, jlong id) {
    return JIMAGE_GetIndexAddress(env, id);
}

JNIEXPORT jlong JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_getDataAddress(JNIEnv *env,
        jclass cls, jlong id) {
    return JIMAGE_GetDataAddress(env, id);
}

JNIEXPORT jboolean JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_read(JNIEnv *env,
        jclass cls, jlong id, jlong offset,
        jobject uncompressedBuffer, jlong uncompressed_size) {
    unsigned char* uncompressedAddress;

    uncompressedAddress = (unsigned char*) env->GetDirectBufferAddress(uncompressedBuffer);
    if (uncompressedAddress == NULL) {
        return JNI_FALSE;
    }
    return JIMAGE_Read(env, id, offset, uncompressedAddress, uncompressed_size);
}

JNIEXPORT jboolean JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_readCompressed(JNIEnv *env,
        jclass cls, jlong id, jlong offset,
        jobject compressedBuffer, jlong compressed_size,
        jobject uncompressedBuffer, jlong uncompressed_size) {
    // Get address of read direct buffer.
    unsigned char* compressedAddress;
    unsigned char* uncompressedAddress;

    compressedAddress = (unsigned char*) env->GetDirectBufferAddress(compressedBuffer);
    // Get address of decompression direct buffer.
    uncompressedAddress = (unsigned char*) env->GetDirectBufferAddress(uncompressedBuffer);
    if (compressedAddress == NULL || uncompressedAddress == NULL) {
        return JNI_FALSE;
    }
    return JIMAGE_ReadCompressed(env, id, offset, compressedAddress, compressed_size,
            uncompressedAddress, uncompressed_size);
}

JNIEXPORT jbyteArray JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_getStringBytes(JNIEnv *env,
        jclass cls, jlong id, jint offset) {
    const char* data;
    size_t size;
    jbyteArray byteArray;
    jbyte* rawBytes;

    data = JIMAGE_GetStringBytes(env, id, offset);
    // Determine String length.
    size = strlen(data);
    // Allocate byte array.
    byteArray = env->NewByteArray((jsize) size);
    if (byteArray == NULL) {
        return NULL;
    }
    // Get array base address.
    rawBytes = env->GetByteArrayElements(byteArray, NULL);
    // Copy bytes from image string table.
    memcpy(rawBytes, data, size);
    // Release byte array base address.
    env->ReleaseByteArrayElements(byteArray, rawBytes, 0);
    return byteArray;
}

JNIEXPORT jlongArray JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_getAttributes(JNIEnv *env,
        jclass cls, jlong id, jint offset) {
    // Allocate a jlong large enough for all location attributes.
    jlongArray attributes;
    jlong* rawAttributes;
    jlong* ret;

    attributes = env->NewLongArray(JIMAGE_GetAttributesCount(env));
    if (attributes == NULL) {
        return NULL;
    }
    // Get base address for jlong array.
    rawAttributes = env->GetLongArrayElements(attributes, NULL);
    ret = JIMAGE_GetAttributes(env, rawAttributes, id, offset);
    // Release jlong array base address.
    env->ReleaseLongArrayElements(attributes, rawAttributes, 0);
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

    count = JIMAGE_GetAttributesCount(env);
    attributes = env->NewLongArray(JIMAGE_GetAttributesCount(env));
    if (attributes == NULL) {
        return NULL;
    }
    // Get base address for jlong array.
    rawAttributes = env->GetLongArrayElements(attributes, NULL);
    size = env->GetArrayLength(utf8);
    rawBytes = env->GetByteArrayElements(utf8, NULL);
    ret = JIMAGE_FindAttributes(env, rawAttributes, rawBytes, size, id);
    env->ReleaseByteArrayElements(utf8, rawBytes, 0);
    // Release jlong array base address.
    env->ReleaseLongArrayElements(attributes, rawAttributes, 0);
    return ret == NULL ? NULL : attributes;

}

JNIEXPORT jintArray JNICALL
Java_jdk_internal_jimage_ImageNativeSubstrate_attributeOffsets(JNIEnv *env,
        jclass cls, jlong id) {
    unsigned int length;
    jintArray offsets;
    jint* rawOffsets;
    jint* ret;

    length = JIMAGE_AttributeOffsetsLength(env, id);
    offsets = env->NewIntArray(length);
    if (offsets == NULL) {
        return NULL;
    }
    // Get base address of result.
    rawOffsets = env->GetIntArrayElements(offsets, NULL);
    ret = JIMAGE_AttributeOffsets(env, rawOffsets, length, id);
    if (length == 0) {
        return NULL;
    }
    // Release result base address.
    env->ReleaseIntArrayElements(offsets, rawOffsets, 0);
    return ret == NULL ? NULL : offsets;
}

/*
 * Class:     jdk_internal_jimage_ImageNativeSubstrate
 * Method:    JIMAGE_open
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_jdk_internal_jimage_ImageNativeSubstrate_JIMAGE_1Open
(JNIEnv *env, jclass, jstring path) {
    const char *nativePath = env->GetStringUTFChars(path, NULL);
    if (nativePath == NULL)
        return 0; // Exception already thrown
    jint error;
    jlong ret = (jlong) JIMAGE_Open(nativePath, &error);
    env->ReleaseStringUTFChars(path, nativePath);
    return ret;
}

/*
 * Class:     jdk_internal_jimage_ImageNativeSubstrate
 * Method:    JIMAGE_Close
 * Signature: (J)J
 */
JNIEXPORT void JNICALL Java_jdk_internal_jimage_ImageNativeSubstrate_JIMAGE_1Close
(JNIEnv *env, jclass, jlong jimageHandle) {
    JIMAGE_Close((JImageFile*) jimageHandle);
}

/*
 * Class:     jdk_internal_jimage_ImageNativeSubstrate
 * Method:    JIMAGE_FindResource
 * Signature: (JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;[J)J
 */
JNIEXPORT jlong JNICALL Java_jdk_internal_jimage_ImageNativeSubstrate_JIMAGE_1FindResource
(JNIEnv *env, jclass, jlong jimageHandle, jstring moduleName,
        jstring version, jstring path, jlongArray output_size) {
    const char *native_module = NULL;
    const char *native_version = NULL;
    const char *native_path = NULL;
    jlong * native_array = NULL;
    jlong size = 0;
    jlong ret = 0;

    do {
        native_module = env->GetStringUTFChars(moduleName, NULL);
        if (native_module == NULL)
            break;
        native_version = env->GetStringUTFChars(version, NULL);
        if (native_version == NULL)
            break;
        native_path = env->GetStringUTFChars(path, NULL);
        if (native_path == NULL)
            break;
        if (env->GetArrayLength(output_size) < 1)
            break;
        // Get base address for jlong array.
        native_array = env->GetLongArrayElements(output_size, NULL);
        if (native_array == NULL)
            break;

        ret = (jlong) JIMAGE_FindResource((JImageFile *) jimageHandle,
                native_module, native_version, native_path, &size);
        if (ret != 0)
            *native_array = size;
    } while (0);

    if (native_array != NULL)
        env->ReleaseLongArrayElements(output_size, native_array, 0);
    if (native_path != NULL)
        env->ReleaseStringUTFChars(path, native_path);
    if (native_version != NULL)
        env->ReleaseStringUTFChars(path, native_version);
    if (native_module != NULL)
        env->ReleaseStringUTFChars(path, native_module);

    return ret;
}

/*
 * Class:     jdk_internal_jimage_ImageNativeSubstrate
 * Method:    JIMAGE_GetResource
 * Signature: (JJ[BJ)J
 */
JNIEXPORT jlong JNICALL Java_jdk_internal_jimage_ImageNativeSubstrate_JIMAGE_1GetResource
(JNIEnv *env, jclass, jlong jimageHandle, jlong jlocationHandle, jbyteArray buffer, jlong size) {
    jbyte * native_buffer = NULL;
    jlong actual_size = 0;
    do {
        if (env->GetArrayLength(buffer) < size)
            break;

        native_buffer = env->GetByteArrayElements(buffer, NULL);
        if (native_buffer == NULL)
            break;

        actual_size = JIMAGE_GetResource((JImageFile*) jimageHandle,
                (JImageLocationRef) jlocationHandle,
                (char *) native_buffer, size);
    } while (0);
    // Release byte array
    if (native_buffer != NULL)
        env->ReleaseByteArrayElements(buffer, native_buffer, 0);

    return actual_size;
}

// Structure passed from iterator to a visitor to accumulate the results

struct VisitorData {
    JNIEnv *env;
    int size; // current number of strings
    int max; // Maximum number of strings
    jobjectArray array; // String array to store the strings
};

// Visitor to accumulate fully qualified resource names

static bool resourceVisitor(JImageFile* image,
        const char* module, const char* version, const char* package,
        const char* name, const char* extension, void* arg) {
    struct VisitorData *vdata = (struct VisitorData *) arg;
    JNIEnv* env = vdata->env;
    if (vdata->size < vdata->max) {
        // Store if there is room in the array
        // Concatenate to get full path
        char fullpath[IMAGE_MAX_PATH];
        fullpath[0] = '\0';
        if (*module != '\0') {
            strncpy(fullpath, "/", IMAGE_MAX_PATH - 1);
            strncat(fullpath, module, IMAGE_MAX_PATH - 1);
            strncat(fullpath, "/", IMAGE_MAX_PATH - 1);
        }
        if (*package != '\0') {
            strncat(fullpath, package, IMAGE_MAX_PATH - 1);
            strncat(fullpath, "/", IMAGE_MAX_PATH - 1);
        }
        strncat(fullpath, name, IMAGE_MAX_PATH - 1);
        if (*extension != '\0') {
            strncat(fullpath, ".", IMAGE_MAX_PATH - 1);
            strncat(fullpath, extension, IMAGE_MAX_PATH - 1);
        }
        jobject str = env->NewStringUTF(fullpath);
        JNU_CHECK_EXCEPTION_RETURN(env, true);
        env->SetObjectArrayElement(vdata->array, vdata->size, str);
        JNU_CHECK_EXCEPTION_RETURN(env, true);
    }
    vdata->size++; // always count so the total size is returned
    return true;
}

/*
 * Class:     jdk_internal_jimage_ImageNativeSubstrate
 * Method:    JIMAGE_Resources
 * Signature: (J)V
 */
JNIEXPORT jint JNICALL Java_jdk_internal_jimage_ImageNativeSubstrate_JIMAGE_1Resources
(JNIEnv *env, jclass, jlong jimageHandle,
        jobjectArray outputNames) {
    struct VisitorData vdata;
    vdata.env = env;
    vdata.max = 0;
    vdata.size = 0;
    vdata.array = outputNames;

    vdata.max = (outputNames != NULL) ? env->GetArrayLength(outputNames) : 0;
    JIMAGE_ResourceIterator((JImageFile*) jimageHandle, &resourceVisitor, &vdata);
    return vdata.size;
}

/*
 * Class:     jdk_internal_jimage_ImageNativeSubstrate
 * Method:    JIMAGE_PackageToModule
 * Signature: (JLjava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_jdk_internal_jimage_ImageNativeSubstrate_JIMAGE_1PackageToModule
(JNIEnv *env, jclass, jlong jimageHandle, jstring package_name) {
    const char *native_package = NULL;
    const char *native_module = NULL;
    jstring module = NULL;

    native_package = env->GetStringUTFChars(package_name, NULL);
    JNU_CHECK_EXCEPTION_RETURN(env, NULL);

    native_module = JIMAGE_PackageToModule((JImageFile*) jimageHandle, native_package);
    if (native_module != NULL) {
        module = env->NewStringUTF(native_module);
    }
    env->ReleaseStringUTFChars(package_name, native_package);
    return module;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    ImageDecompressor::image_decompressor_close();
}
