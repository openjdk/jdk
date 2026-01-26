/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include <string.h>

#include "jimage.hpp"

#include "imageFile.hpp"

#include "jni_util.h"

/*
 * Declare jimage library specific JNI_Onload entry for static build.
 */
extern "C" {
DEF_STATIC_JNI_OnLoad
}

/*
 * JImageOpen - Given the supplied full path file name, open an image file. This
 * function will also initialize tables and retrieve meta-data necessary to
 * satisfy other functions in the API. If the image file has been previously
 * open, a new open request will share memory and resources used by the previous
 * open. A call to JImageOpen should be balanced by a call to JImageClose, to
 * release memory and resources used. If the image file is not found or cannot
 * be open, then NULL is returned and error will contain a reason for the
 * failure; a positive value for a system error number, negative for a jimage
 * specific error (see JImage Error Codes.)
 *
 *  Ex.
 *   jint error;
 *   JImageFile* jimage = (*JImageOpen)(JAVA_HOME "lib/modules", &error);
 *   if (image == NULL) {
 *     tty->print_cr("JImage failed to open: %d", error);
 *     ...
 *   }
 *   ...
 */
extern "C" JNIEXPORT JImageFile*
JIMAGE_Open(const char *name, jint* error) {
    // TODO - return a meaningful error code
    *error = 0;
    ImageFileReader* jfile = ImageFileReader::open(name);
    return (JImageFile*) jfile;
}

/*
 * JImageClose - Given the supplied open image file (see JImageOpen), release
 * memory and resources used by the open file and close the file. If the image
 * file is shared by other uses, release and close is deferred until the last use
 * is also closed.
 *
 * Ex.
 *  (*JImageClose)(image);
 */
extern "C" JNIEXPORT void
JIMAGE_Close(JImageFile* image) {
    ImageFileReader::close((ImageFileReader*) image);
}

/*
 * JImageFindResource - Given an open image file (see JImageOpen), a module
 * name, a version string and the name of a class/resource, return location
 * information describing the resource and its size. If no resource is found, the
 * function returns JIMAGE_NOT_FOUND and the value of size is undefined.
 * The resulting location does/should not have to be released.
 * All strings are utf-8, zero byte terminated.
 *
 *  Ex.
 *   jlong size;
 *   JImageLocationRef location = (*JImageFindResource)(image,
 *           "java.base", "java/lang/String.class", is_preview_mode, &size);
 */
extern "C" JNIEXPORT JImageLocationRef
JIMAGE_FindResource(JImageFile* image,
        const char* module_name, const char* name, bool is_preview_mode,
        jlong* size) {
    static const char str_modules[] = "modules";
    static const char str_packages[] = "packages";
    static const char preview_infix[] = "/META-INF/preview";

    size_t module_name_len = strlen(module_name);
    size_t name_len = strlen(name);
    size_t preview_infix_len = strlen(preview_infix);
    assert(module_name_len > 0 && "module name must be non-empty");
    assert(name_len > 0 && "name must non-empty");

    // Do not attempt to lookup anything of the form /modules/... or /packages/...
    if (strncmp(module_name, str_modules, sizeof(str_modules)) == 0
            || strncmp(module_name, str_packages, sizeof(str_packages)) == 0) {
        return 0L;
    }
    // If the preview mode version of the path string is too long for the buffer,
    // return not found (even when not in preview mode).
    if (1 + module_name_len + preview_infix_len + 1 + name_len + 1 > IMAGE_MAX_PATH) {
        return 0L;
    }

    // Concatenate to get full path
    char name_buffer[IMAGE_MAX_PATH];
    char* path;
    {   // Write the buffer with room to prepend the preview mode infix
        // at the start (saves copying the trailing name part twice).
        size_t index = preview_infix_len;
        name_buffer[index++] = '/';
        memcpy(&name_buffer[index], module_name, module_name_len);
        index += module_name_len;
        name_buffer[index++] = '/';
        memcpy(&name_buffer[index], name, name_len);
        index += name_len;
        name_buffer[index++] = '\0';
        // Path begins at the leading '/' (not the start of the buffer).
        path = &name_buffer[preview_infix_len];
    }

    // find_location_index() returns the data "offset", not an index.
    const ImageFileReader* image_file = (ImageFileReader*) image;
    u4 locOffset = image_file->find_location_index(path, (u8*) size);
    if (locOffset != 0) {
        ImageLocation loc;
        loc.set_data(image_file->get_location_offset_data(locOffset));

        u4 flags = loc.get_preview_flags();
        // No preview flags means "a normal resource, without a preview version".
        // This is the overwhelmingly common case, with or without preview mode.
        if (flags == 0) {
            return locOffset;
        }
        // Regardless of preview mode, don't return resources requested directly
        // via their preview path.
        if ((flags & ImageLocation::FLAGS_IS_PREVIEW_VERSION) != 0) {
            return 0L;
        }
        // Even if there is a preview version, we might not want to return it.
        if (!is_preview_mode || (flags & ImageLocation::FLAGS_HAS_PREVIEW_VERSION) == 0) {
            return locOffset;
        }
    } else if (!is_preview_mode) {
        // No normal resource found, and not in preview mode.
        return 0L;
    }

    // We are in preview mode, and the preview version of the resource is needed.
    // This is either because:
    // 1. The normal resource was flagged as having a preview version (rare)
    // 2. This is a preview-only resource (there was no normal resource, very rare)
    // 3. The requested resource doesn't exist (this should typically not happen)
    //
    // Since we only expect requests for resources which exist in jimage files, we
    // expect this 2nd lookup to succeed (this is contrary to the expectations for
    // the JRT file system, where non-existent resource lookups are common).

    {   // Rewrite the front of the name buffer to make it a preview path.
        size_t index = 0;
        name_buffer[index++] = '/';
        memcpy(&name_buffer[index], module_name, module_name_len);
        index += module_name_len;
        memcpy(&name_buffer[index], preview_infix, preview_infix_len);
        index += preview_infix_len;
        // Check we copied up to the expected '/' separator.
        assert(name_buffer[index] == '/' && "bad string concatenation");
        // The preview path now begins at the start of the buffer.
        path = &name_buffer[0];
    }
    return image_file->find_location_index(path, (u8*) size);
}

/*
 * JImageGetResource - Given an open image file (see JImageOpen), a resource's
 * location information (see JImageFindResource), a buffer of appropriate
 * size and the size, retrieve the bytes associated with the
 * resource. If the size is less than the resource size then the read is truncated.
 * If the size is greater than the resource size then the remainder of the buffer
 * is zero filled.  The function will return the actual size of the resource.
 *
 * Ex.
 *  jlong size;
 *   JImageLocationRef location = (*JImageFindResource)(image,
 *                                 "java.base", "9.0", "java/lang/String.class", &size);
 *  char* buffer = new char[size];
 *  (*JImageGetResource)(image, location, buffer, size);
 */
extern "C" JNIEXPORT jlong
JIMAGE_GetResource(JImageFile* image, JImageLocationRef location,
        char* buffer, jlong size) {
    ((ImageFileReader*) image)->get_resource((u4) location, (u1*) buffer);
    return size;
}

/*
 * JImageResourceIterator - Given an open image file (see JImageOpen), a visitor
 * function and a visitor argument, iterator through each of the image's resources.
 * The visitor function is called with the image file, the module name, the
 * package name, the base name, the extension and the visitor argument. The return
 * value of the visitor function should be true, unless an early iteration exit is
 * required. All strings are utf-8, zero byte terminated.file.
 *
 * Ex.
 *   bool ctw_visitor(JImageFile* jimage, const char* module_name, const char* version,
 *                  const char* package, const char* name, const char* extension, void* arg) {
 *     if (strcmp(extension, "class") == 0) {
 *       char path[JIMAGE_MAX_PATH];
 *       Thread* THREAD = Thread::current();
 *       jio_snprintf(path, JIMAGE_MAX_PATH - 1, "/%s/%s", package, name);
 *       ClassLoader::compile_the_world_in(path, (Handle)arg, THREAD);
 *       return !HAS_PENDING_EXCEPTION;
 *     }
 *     return true;
 *   }
 *   (*JImageResourceIterator)(image, ctw_visitor, loader);
 */
extern "C" JNIEXPORT void
JIMAGE_ResourceIterator(JImageFile* image,
        JImageResourceVisitor_t visitor, void* arg) {
    ImageFileReader* imageFile = (ImageFileReader*) image;
    u4 nEntries = imageFile->table_length();
    const ImageStrings strings = imageFile->get_strings();
    for (u4 i = 0; i < nEntries; i++) {
        ImageLocation location(imageFile->get_location_data(i));

        u4 moduleOffset = (u4) location.get_attribute(ImageLocation::ATTRIBUTE_MODULE);
        if (moduleOffset == 0) {
            continue; // skip non-modules
        }
        const char *module = strings.get(moduleOffset);
        if (strcmp(module, "modules") == 0
            || strcmp(module, "packages") == 0) {
            continue; // always skip
        }

        u4 parentOffset = (u4) location.get_attribute(ImageLocation::ATTRIBUTE_PARENT);
        const char *parent = strings.get(parentOffset);
        u4 baseOffset = (u4) location.get_attribute(ImageLocation::ATTRIBUTE_BASE);
        const char *base = strings.get(baseOffset);
        u4 extOffset = (u4) location.get_attribute(ImageLocation::ATTRIBUTE_EXTENSION);
        const char *extension = strings.get(extOffset);

        if (!(*visitor)(image, module, "9", parent, base, extension, arg)) {
            break;
        }
    }
}
