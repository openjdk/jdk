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

#ifndef SHARE_VM_CLASSFILE_IMAGEDECOMPRESSOR_HPP
#define SHARE_VM_CLASSFILE_IMAGEDECOMPRESSOR_HPP

#include "runtime/thread.inline.hpp"
#include "precompiled.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/imageFile.hpp"
#include "classfile/symbolTable.hpp"
#include "oops/symbol.hpp"
#include "utilities/growableArray.hpp"

/*
 * Compressed resources located in image have an header.
 * This header contains:
 * - _magic: A magic u4, required to retrieved the header in the compressed content
 * - _size: The size of the compressed resource.
 * - _uncompressed_size: The uncompressed size of the compressed resource.
 * - _decompressor_name_offset: The ImageDecompressor instance name StringsTable offset.
 * - _decompressor_config_offset: StringsTable offset of configuration that could be needed by
 *   the decompressor in order to decompress.
 * - _is_terminal: 1: the compressed content is terminal. Uncompressing it would
 *   create the actual resource. 0: the compressed content is not terminal. Uncompressing it
 *   will result in a compressed content to be decompressed (This occurs when a stack of compressors
 *   have been used to compress the resource.
 */
struct ResourceHeader {
  /* Length of header, needed to retrieve content offset */
  static const u1 resource_header_length = 21;
  /* magic bytes that identifies a compressed resource header*/
  static const u4 resource_header_magic = 0xCAFEFAFA;
  u4 _magic; // Resource header
  u4 _size;  // Resource size
  u4 _uncompressed_size;  // Expected uncompressed size
  u4 _decompressor_name_offset;  // Strings table decompressor offset
  u4 _decompressor_config_offset; // Strings table config offset
  u1 _is_terminal; // Last decompressor 1, otherwise 0.
};

/*
 * Resources located in jimage file can be compressed. Compression occurs at
 * jimage file creation time. When compressed a resource is added an header that
 * contains the name of the compressor that compressed it.
 * Various compression strategies can be applied to compress a resource.
 * The same resource can even be compressed multiple time by a stack of compressors.
 * At runtime, a resource is decompressed in a loop until there is no more header
 * meaning that the resource is equivalent to the not compressed resource.
 * In each iteration, the name of the compressor located in the current header
 * is used to retrieve the associated instance of ImageDecompressor.
 * For example “zip” is the name of the compressor that compresses resources
 * using the zip algorithm. The ZipDecompressor class name is also “zip”.
 * ImageDecompressor instances are retrieved from a static array in which
 * they are registered.
 */
class ImageDecompressor: public CHeapObj<mtClass> {

private:
  const Symbol* _name;

  /*
   * Array of concrete decompressors. This array is used to retrieve the decompressor
   * that can handle resource decompression.
   */
  static GrowableArray<ImageDecompressor*>* _decompressors;

  /*
   * Identifier of a decompressor. This name is the identification key to retrieve
   * decompressor from a resource header.
   */
  inline const Symbol* get_name() const { return _name; }

protected:
  ImageDecompressor(const Symbol* name) : _name(name) {
  }
  virtual void decompress_resource(u1* data, u1* uncompressed,
    ResourceHeader* header, const ImageStrings* strings) = 0;

public:
  inline static void add_decompressor(ImageDecompressor* decompressor) {
    _decompressors->append(decompressor);
  }
  inline static ImageDecompressor* get_decompressor(const char * decompressor_name) {
    Thread* THREAD = Thread::current();
    TempNewSymbol sym = SymbolTable::new_symbol(decompressor_name,
            (int) strlen(decompressor_name), CHECK_NULL);
    if (HAS_PENDING_EXCEPTION) {
      warning("can't create symbol\n");
      CLEAR_PENDING_EXCEPTION;
      return NULL;
    }
    for (int i = 0; i < _decompressors->length(); i++) {
      ImageDecompressor* decompressor = _decompressors->at(i);
      if (decompressor->get_name()->fast_compare(sym) == 0) {
        return decompressor;
      }
    }
    guarantee(false, "No decompressor found.");
    return NULL;
  }
  static void decompress_resource(u1* compressed, u1* uncompressed,
    u4 uncompressed_size, const ImageStrings* strings, bool is_C_heap);
};

/**
 * Zip decompressor.
 */
class ZipDecompressor : public ImageDecompressor {
public:
  ZipDecompressor(const Symbol* sym) : ImageDecompressor(sym) { }
  void decompress_resource(u1* data, u1* uncompressed, ResourceHeader* header,
    const ImageStrings* strings);
};

#endif // SHARE_VM_CLASSFILE_IMAGEDECOMPRESSOR_HPP
