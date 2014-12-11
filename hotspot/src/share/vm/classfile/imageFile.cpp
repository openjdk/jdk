/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/imageFile.hpp"
#include "runtime/os.inline.hpp"
#include "utilities/bytes.hpp"


// Compute the Perfect Hashing hash code for the supplied string.
u4 ImageStrings::hash_code(const char* string, u4 seed) {
  u1* bytes = (u1*)string;

  // Compute hash code.
  for (u1 byte = *bytes++; byte; byte = *bytes++) {
    seed = (seed * HASH_MULTIPLIER) ^ byte;
  }

  // Ensure the result is unsigned.
  return seed & 0x7FFFFFFF;
}

// Test to see if string begins with start.  If so returns remaining portion
// of string.  Otherwise, NULL.
const char* ImageStrings::starts_with(const char* string, const char* start) {
  char ch1, ch2;

  // Match up the strings the best we can.
  while ((ch1 = *string) && (ch2 = *start)) {
    if (ch1 != ch2) {
      // Mismatch, return NULL.
      return NULL;
    }

    string++, start++;
  }

  // Return remainder of string.
  return string;
}

ImageLocation::ImageLocation(u1* data) {
  // Deflate the attribute stream into an array of attributes.
  memset(_attributes, 0, sizeof(_attributes));
  u1 byte;

  while ((byte = *data) != ATTRIBUTE_END) {
    u1 kind = attribute_kind(byte);
    u1 n = attribute_length(byte);
    assert(kind < ATTRIBUTE_COUNT, "invalid image location attribute");
    _attributes[kind] = attribute_value(data + 1, n);
    data += n + 1;
  }
}

ImageFile::ImageFile(const char* name) {
  // Copy the image file name.
  _name = NEW_C_HEAP_ARRAY(char, strlen(name)+1, mtClass);
  strcpy(_name, name);

  // Initialize for a closed file.
  _fd = -1;
  _memory_mapped = true;
  _index_data = NULL;
}

ImageFile::~ImageFile() {
  // Ensure file is closed.
  close();

  // Free up name.
  FREE_C_HEAP_ARRAY(char, _name);
}

bool ImageFile::open() {
  // If file exists open for reading.
  struct stat st;
  if (os::stat(_name, &st) != 0 ||
    (st.st_mode & S_IFREG) != S_IFREG ||
    (_fd = os::open(_name, 0, O_RDONLY)) == -1) {
    return false;
  }

  // Read image file header and verify.
  u8 header_size = sizeof(ImageHeader);
  if (os::read(_fd, &_header, header_size) != header_size ||
    _header._magic != IMAGE_MAGIC ||
    _header._major_version != MAJOR_VERSION ||
    _header._minor_version != MINOR_VERSION) {
    close();
    return false;
  }

  // Memory map index.
  _index_size = index_size();
  _index_data = (u1*)os::map_memory(_fd, _name, 0, NULL, _index_size, true, false);

  // Failing that, read index into C memory.
  if (_index_data == NULL) {
    _memory_mapped = false;
    _index_data = NEW_RESOURCE_ARRAY(u1, _index_size);

    if (os::seek_to_file_offset(_fd, 0) == -1) {
      close();
      return false;
    }

    if (os::read(_fd, _index_data, _index_size) != _index_size) {
      close();
      return false;
    }

    return true;
  }

// Used to advance a pointer, unstructured.
#undef nextPtr
#define nextPtr(base, fromType, count, toType) (toType*)((fromType*)(base) + (count))
  // Pull tables out from the index.
  _redirect_table = nextPtr(_index_data, u1, header_size, s4);
  _offsets_table = nextPtr(_redirect_table, s4, _header._location_count, u4);
  _location_bytes = nextPtr(_offsets_table, u4, _header._location_count, u1);
  _string_bytes = nextPtr(_location_bytes, u1, _header._locations_size, u1);
#undef nextPtr

  // Successful open.
  return true;
}

void ImageFile::close() {
  // Dealllocate the index.
  if (_index_data) {
    if (_memory_mapped) {
      os::unmap_memory((char*)_index_data, _index_size);
    } else {
      FREE_RESOURCE_ARRAY(u1, _index_data, _index_size);
    }

    _index_data = NULL;
  }

  // close file.
  if (_fd != -1) {
    os::close(_fd);
    _fd = -1;
  }

}

// Return the attribute stream for a named resourced.
u1* ImageFile::find_location_data(const char* path) const {
  // Compute hash.
  u4 hash = ImageStrings::hash_code(path) % _header._location_count;
  s4 redirect = _redirect_table[hash];

  if (!redirect) {
    return NULL;
  }

  u4 index;

  if (redirect < 0) {
    // If no collision.
    index = -redirect - 1;
  } else {
    // If collision, recompute hash code.
    index = ImageStrings::hash_code(path, redirect) % _header._location_count;
  }

  assert(index < _header._location_count, "index exceeds location count");
  u4 offset = _offsets_table[index];
  assert(offset < _header._locations_size, "offset exceeds location attributes size");

  if (offset == 0) {
    return NULL;
  }

  return _location_bytes + offset;
}

// Verify that a found location matches the supplied path.
bool ImageFile::verify_location(ImageLocation& location, const char* path) const {
  // Retrieve each path component string.
  ImageStrings strings(_string_bytes, _header._strings_size);
  // Match a path with each subcomponent without concatenation (copy).
  // Match up path parent.
  const char* parent = location.get_attribute(ImageLocation::ATTRIBUTE_PARENT, strings);
  const char* next = ImageStrings::starts_with(path, parent);
  // Continue only if a complete match.
  if (!next) return false;
  // Match up path base.
  const char* base = location.get_attribute(ImageLocation::ATTRIBUTE_BASE, strings);
  next = ImageStrings::starts_with(next, base);
  // Continue only if a complete match.
  if (!next) return false;
  // Match up path extension.
  const char* extension = location.get_attribute(ImageLocation::ATTRIBUTE_EXTENSION, strings);
  next = ImageStrings::starts_with(next, extension);

  // True only if complete match and no more characters.
  return next && *next == '\0';
}

// Return the resource for the supplied location.
u1* ImageFile::get_resource(ImageLocation& location) const {
  // Retrieve the byte offset and size of the resource.
  u8 offset = _index_size + location.get_attribute(ImageLocation::ATTRIBUTE_OFFSET);
  u8 size = location.get_attribute(ImageLocation::ATTRIBUTE_UNCOMPRESSED);
  u8 compressed_size = location.get_attribute(ImageLocation::ATTRIBUTE_COMPRESSED);
  u8 read_size = compressed_size ? compressed_size : size;

  // Allocate space for the resource.
  u1* data = NEW_RESOURCE_ARRAY(u1, read_size);

  bool is_read = os::read_at(_fd, data, read_size, offset) == read_size;
  guarantee(is_read, "error reading from image or short read");

  // If not compressed, just return the data.
  if (!compressed_size) {
    return data;
  }

  u1* uncompressed = NEW_RESOURCE_ARRAY(u1, size);
  char* msg = NULL;
  jboolean res = ClassLoader::decompress(data, compressed_size, uncompressed, size, &msg);
  if (!res) warning("decompression failed due to %s\n", msg);
  guarantee(res, "decompression failed");

  return uncompressed;
}

void ImageFile::get_resource(const char* path, u1*& buffer, u8& size) const {
  buffer = NULL;
  size = 0;
  u1* data = find_location_data(path);
  if (data) {
    ImageLocation location(data);
    if (verify_location(location, path)) {
      size = location.get_attribute(ImageLocation::ATTRIBUTE_UNCOMPRESSED);
      buffer = get_resource(location);
    }
  }
}

GrowableArray<const char*>* ImageFile::packages(const char* name) {
  char entry[JVM_MAXPATHLEN];
  bool overflow = jio_snprintf(entry, sizeof(entry), "%s/packages.offsets", name) == -1;
  guarantee(!overflow, "package name overflow");

  u1* buffer;
  u8 size;

  get_resource(entry, buffer, size);
  guarantee(buffer, "missing module packages reource");
  ImageStrings strings(_string_bytes, _header._strings_size);
  GrowableArray<const char*>* pkgs = new GrowableArray<const char*>();
  int count = size / 4;
  for (int i = 0; i < count; i++) {
    u4 offset = Bytes::get_Java_u4(buffer + (i*4));
    const char* p = strings.get(offset);
    pkgs->append(p);
  }

  return pkgs;
}
