/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include "precompiled.hpp"
#include "runtime/thread.inline.hpp"
#include "classfile/imageDecompressor.hpp"
#include "runtime/thread.hpp"
#include "utilities/bytes.hpp"

/*
 * Allocate in C Heap not in resource area, otherwise JVM crashes.
 * This array life time is the VM life time. Array is never freed and
 * is not expected to contain more than few references.
 */
GrowableArray<ImageDecompressor*>* ImageDecompressor::_decompressors =
  new(ResourceObj::C_HEAP, mtInternal) GrowableArray<ImageDecompressor*>(2, true);

static Symbol* createSymbol(const char* str) {
  Thread* THREAD = Thread::current();
  Symbol* sym = SymbolTable::lookup(str, (int) strlen(str), THREAD);
  if (HAS_PENDING_EXCEPTION) {
    warning("can't create symbol\n");
    CLEAR_PENDING_EXCEPTION;
    return NULL;
  }
  return sym;
}

/*
 * Initialize the array of decompressors.
 */
bool image_decompressor_init() {
  Symbol* zipSymbol = createSymbol("zip");
  if (zipSymbol == NULL) {
    return false;
  }
  ImageDecompressor::add_decompressor(new ZipDecompressor(zipSymbol));

  return true;
}

/*
 * Decompression entry point. Called from ImageFileReader::get_resource.
 */
void ImageDecompressor::decompress_resource(u1* compressed, u1* uncompressed,
        u4 uncompressed_size, const ImageStrings* strings, bool is_C_heap) {
  bool has_header = false;
  u1* decompressed_resource = compressed;
  u1* compressed_resource = compressed;

  // Resource could have been transformed by a stack of decompressors.
  // Iterate and decompress resources until there is no more header.
  do {
    ResourceHeader _header;
    memcpy(&_header, compressed_resource, sizeof (ResourceHeader));
    has_header = _header._magic == ResourceHeader::resource_header_magic;
    if (has_header) {
      // decompressed_resource array contains the result of decompression
      // when a resource content is terminal, it means that it is an actual resource,
      // not an intermediate not fully uncompressed content. In this case
      // the resource is allocated as an mtClass, otherwise as an mtOther
      decompressed_resource = is_C_heap && _header._is_terminal ?
              NEW_C_HEAP_ARRAY(u1, _header._uncompressed_size, mtClass) :
              NEW_C_HEAP_ARRAY(u1, _header._uncompressed_size, mtOther);
      // Retrieve the decompressor name
      const char* decompressor_name = strings->get(_header._decompressor_name_offset);
      if (decompressor_name == NULL) warning("image decompressor not found\n");
      guarantee(decompressor_name, "image decompressor not found");
      // Retrieve the decompressor instance
      ImageDecompressor* decompressor = get_decompressor(decompressor_name);
      if (decompressor == NULL) {
        warning("image decompressor %s not found\n", decompressor_name);
      }
      guarantee(decompressor, "image decompressor not found");
      u1* compressed_resource_base = compressed_resource;
      compressed_resource += ResourceHeader::resource_header_length;
      // Ask the decompressor to decompress the compressed content
      decompressor->decompress_resource(compressed_resource, decompressed_resource,
        &_header, strings);
      if (compressed_resource_base != compressed) {
        FREE_C_HEAP_ARRAY(char, compressed_resource_base);
      }
      compressed_resource = decompressed_resource;
    }
  } while (has_header);
  memcpy(uncompressed, decompressed_resource, uncompressed_size);
}

// Zip decompressor

void ZipDecompressor::decompress_resource(u1* data, u1* uncompressed,
        ResourceHeader* header, const ImageStrings* strings) {
  char* msg = NULL;
  jboolean res = ClassLoader::decompress(data, header->_size, uncompressed,
          header->_uncompressed_size, &msg);
  if (!res) warning("decompression failed due to %s\n", msg);
  guarantee(res, "decompression failed");
}

// END Zip Decompressor
