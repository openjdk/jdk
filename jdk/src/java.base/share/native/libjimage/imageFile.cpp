/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.    Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.    See the GNU General Public License
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

#include <assert.h>
#include <string.h>
#include <stdlib.h>

#include "endian.hpp"
#include "imageDecompressor.hpp"
#include "imageFile.hpp"
#include "inttypes.hpp"
#include "jni.h"
#include "osSupport.hpp"

// Map the full jimage, only with 64 bit addressing.
bool MemoryMapImage = sizeof(void *) == 8;

#ifdef WIN32
const char FileSeparator = '\\';
#else
const char FileSeparator = '/';
#endif

// Image files are an alternate file format for storing classes and resources. The
// goal is to supply file access which is faster and smaller than the jar format.
//
// (More detailed nodes in the header.)
//

// Compute the Perfect Hashing hash code for the supplied UTF-8 string.
s4 ImageStrings::hash_code(const char* string, s4 seed) {
    // Access bytes as unsigned.
    u1* bytes = (u1*)string;
    // Compute hash code.
    for (u1 byte = *bytes++; byte; byte = *bytes++) {
        seed = (seed * HASH_MULTIPLIER) ^ byte;
    }
    // Ensure the result is not signed.
    return seed & 0x7FFFFFFF;
}

// Match up a string in a perfect hash table.
// Returns the index where the name should be.
// Result still needs validation for precise match (false positive.)
s4 ImageStrings::find(Endian* endian, const char* name, s4* redirect, u4 length) {
    // If the table is empty, then short cut.
    if (!redirect || !length) {
        return NOT_FOUND;
    }
    // Compute the basic perfect hash for name.
    s4 hash_code = ImageStrings::hash_code(name);
    // Modulo table size.
    s4 index = hash_code % length;
    // Get redirect entry.
    //   value == 0 then not found
    //   value < 0 then -1 - value is true index
    //   value > 0 then value is seed for recomputing hash.
    s4 value = endian->get(redirect[index]);
    // if recompute is required.
    if (value > 0 ) {
        // Entry collision value, need to recompute hash.
        hash_code = ImageStrings::hash_code(name, value);
        // Modulo table size.
        return hash_code % length;
    } else if (value < 0) {
        // Compute direct index.
        return -1 - value;
    }
    // No entry found.
    return NOT_FOUND;
}

// Test to see if UTF-8 string begins with the start UTF-8 string.  If so,
// return non-NULL address of remaining portion of string.  Otherwise, return
// NULL.    Used to test sections of a path without copying from image string
// table.
const char* ImageStrings::starts_with(const char* string, const char* start) {
    char ch1, ch2;
    // Match up the strings the best we can.
    while ((ch1 = *string) && (ch2 = *start)) {
        if (ch1 != ch2) {
            // Mismatch, return NULL.
            return NULL;
        }
        // Next characters.
        string++, start++;
    }
    // Return remainder of string.
    return string;
}

// Inflates the attribute stream into individual values stored in the long
// array _attributes. This allows an attribute value to be quickly accessed by
// direct indexing.  Unspecified values default to zero (from constructor.)
void ImageLocation::set_data(u1* data) {
    // Deflate the attribute stream into an array of attributes.
    u1 byte;
    // Repeat until end header is found.
    while ((data != NULL) && (byte = *data)) {
        // Extract kind from header byte.
        u1 kind = attribute_kind(byte);
        assert(kind < ATTRIBUTE_COUNT && "invalid image location attribute");
        // Extract length of data (in bytes).
        u1 n = attribute_length(byte);
        // Read value (most significant first.)
        _attributes[kind] = attribute_value(data + 1, n);
        // Position to next attribute by skipping attribute header and data bytes.
        data += n + 1;
    }
}

// Zero all attribute values.
void ImageLocation::clear_data() {
    // Set defaults to zero.
    memset(_attributes, 0, sizeof(_attributes));
}

// ImageModuleData constructor maps out sub-tables for faster access.
ImageModuleData::ImageModuleData(const ImageFileReader* image_file,
                const char* module_data_name) :
        _image_file(image_file),
        _endian(image_file->endian()),
        _strings(image_file->get_strings()) {
    // Retrieve the resource containing the module data for the image file.
    ImageLocation location;
    bool found = image_file->find_location(module_data_name, location);
    if (found) {
        u8 data_size = location.get_attribute(ImageLocation::ATTRIBUTE_UNCOMPRESSED);
        _data = new u1[(size_t)data_size];
        _image_file->get_resource(location, _data);
        // Map out the header.
        _header = (Header*)_data;
        // Get the package to module entry count.
        u4 ptm_count = _header->ptm_count(_endian);
        // Get the module to package entry count.
        u4 mtp_count = _header->mtp_count(_endian);
        // Compute the offset of the package to module perfect hash redirect.
        u4 ptm_redirect_offset = sizeof(Header);
        // Compute the offset of the package to module data.
        u4 ptm_data_offset = ptm_redirect_offset + ptm_count * sizeof(s4);
        // Compute the offset of the module to package perfect hash redirect.
        u4 mtp_redirect_offset = ptm_data_offset + ptm_count * sizeof(PTMData);
        // Compute the offset of the module to package data.
        u4 mtp_data_offset = mtp_redirect_offset + mtp_count * sizeof(s4);
        // Compute the offset of the module to package tables.
        u4 mtp_packages_offset = mtp_data_offset + mtp_count * sizeof(MTPData);
        // Compute the address of the package to module perfect hash redirect.
        _ptm_redirect = (s4*)(_data + ptm_redirect_offset);
        // Compute the address of the package to module data.
        _ptm_data = (PTMData*)(_data + ptm_data_offset);
        // Compute the address of the module to package perfect hash redirect.
        _mtp_redirect = (s4*)(_data + mtp_redirect_offset);
        // Compute the address of the module to package data.
        _mtp_data = (MTPData*)(_data + mtp_data_offset);
        // Compute the address of the module to package tables.
        _mtp_packages = (s4*)(_data + mtp_packages_offset);
    } else {
        // No module data present.
        _data = NULL;
        _header = NULL;
        _ptm_redirect = NULL;
        _ptm_data = NULL;
        _mtp_redirect = NULL;
        _mtp_data = NULL;
        _mtp_packages = NULL;
    }
}

// Release module data resource.
ImageModuleData::~ImageModuleData() {
    if (_data) {
        delete[] _data;
    }
}

// Return the name of the module data resource.  Ex. "./lib/modules/file.jimage"
// yields "file.jdata"
void ImageModuleData::module_data_name(char* buffer, const char* image_file_name) {
    // Locate the last slash in the file name path.
    const char* slash = strrchr(image_file_name, FileSeparator);
    // Trim the path to name and extension.
    const char* name = slash ? slash + 1 : (char *)image_file_name;
    // Locate the extension period.
    const char* dot = strrchr(name, '.');
    assert(dot && "missing extension on jimage name");
    // Trim to only base name.
    int length = (int)(dot - name);
    strncpy(buffer, name, length);
    buffer[length] = '\0';
    // Append extension.
    strcat(buffer, ".jdata");
}

// Return the module in which a package resides.    Returns NULL if not found.
const char* ImageModuleData::package_to_module(const char* package_name) {
    // Test files may contain no module data.
    if (_data != NULL) {
        // Search the package to module table.
        s4 index = ImageStrings::find(_endian, package_name, _ptm_redirect,
                                      _header->ptm_count(_endian));
        // If entry is found.
        if (index != ImageStrings::NOT_FOUND) {
            // Retrieve the package to module entry.
            PTMData* data = _ptm_data + index;
            // Verify that it is the correct data.
            if (strcmp(package_name, get_string(data->name_offset(_endian))) != 0) {
                return NULL;
            }
            // Return the module name.
            return get_string(data->module_name_offset(_endian));
        }
    }
    return NULL;
}

// Returns all the package names in a module in a NULL terminated array.
// Returns NULL if module not found.
const char** ImageModuleData::module_to_packages(const char* module_name) {
    // Test files may contain no module data.
    if (_data != NULL) {
        // Search the module to package table.
        s4 index = ImageStrings::find(_endian, module_name, _mtp_redirect,
                                      _header->mtp_count(_endian));
        // If entry is found.
        if (index != ImageStrings::NOT_FOUND) {
            // Retrieve the module to package entry.
            MTPData* data = _mtp_data + index;
            // Verify that it is the correct data.
            if (strcmp(module_name, get_string(data->name_offset(_endian))) != 0) {
                return NULL;
            }
            // Construct an array of all the package entries.
            u4 count = data->package_count(_endian);
            const char** packages = new const char*[count + 1];
            s4 package_offset = data->package_offset(_endian);
            for (u4 i = 0; i < count; i++) {
                u4 package_name_offset = mtp_package(package_offset + i);
                const char* package_name = get_string(package_name_offset);
                packages[i] = package_name;
            }
            packages[count] = NULL;
            return packages;
        }
    }
    return NULL;
}

// Manage a table of open image files.  This table allows multiple access points
// to share an open image.
ImageFileReaderTable::ImageFileReaderTable() : _count(0), _max(_growth) {
    _table = new ImageFileReader*[_max];
}

ImageFileReaderTable::~ImageFileReaderTable() {
    delete[] _table;
}

// Add a new image entry to the table.
void ImageFileReaderTable::add(ImageFileReader* image) {
    if (_count == _max) {
        _max += _growth;
        _table = static_cast<ImageFileReader**>(realloc(_table, _max * sizeof(ImageFileReader*)));
    }
    _table[_count++] = image;
}

// Remove an image entry from the table.
void ImageFileReaderTable::remove(ImageFileReader* image) {
    s4 last = _count - 1;
    for (s4 i = 0; _count; i++) {
        if (_table[i] == image) {
            if (i != last) {
                _table[i] = _table[last];
                _count = last;
            }
            break;
        }
    }

    if (_count != 0 && _count == _max - _growth) {
        _max -= _growth;
        _table = static_cast<ImageFileReader**>(realloc(_table, _max * sizeof(ImageFileReader*)));
    }
}

// Determine if image entry is in table.
bool ImageFileReaderTable::contains(ImageFileReader* image) {
    for (s4 i = 0; _count; i++) {
        if (_table[i] == image) {
            return true;
        }
    }
    return false;
}

// Table to manage multiple opens of an image file.
ImageFileReaderTable ImageFileReader::_reader_table;

SimpleCriticalSection _reader_table_lock;

// Open an image file, reuse structure if file already open.
ImageFileReader* ImageFileReader::open(const char* name, bool big_endian) {
    {
        // Lock out _reader_table.
        SimpleCriticalSectionLock cs(&_reader_table_lock);
        // Search for an exist image file.
        for (u4 i = 0; i < _reader_table.count(); i++) {
            // Retrieve table entry.
            ImageFileReader* reader = _reader_table.get(i);
            // If name matches, then reuse (bump up use count.)
            if (strcmp(reader->name(), name) == 0) {
                reader->inc_use();
                return reader;
            }
        }
    } // Unlock the mutex

    // Need a new image reader.
    ImageFileReader* reader = new ImageFileReader(name, big_endian);
    bool opened = reader->open();
    // If failed to open.
    if (!opened) {
        delete reader;
        return NULL;
    }

    // Lock to update
    SimpleCriticalSectionLock cs(&_reader_table_lock);
    // Search for an exist image file.
    for (u4 i = 0; i < _reader_table.count(); i++) {
        // Retrieve table entry.
        ImageFileReader* existing_reader = _reader_table.get(i);
        // If name matches, then reuse (bump up use count.)
        if (strcmp(existing_reader->name(), name) == 0) {
            existing_reader->inc_use();
            reader->close();
            delete reader;
            return existing_reader;
        }
    }
    // Bump use count and add to table.
    reader->inc_use();
    _reader_table.add(reader);
    return reader;
}

// Close an image file if the file is not in use elsewhere.
void ImageFileReader::close(ImageFileReader *reader) {
    // Lock out _reader_table.
    SimpleCriticalSectionLock cs(&_reader_table_lock);
    // If last use then remove from table and then close.
    if (reader->dec_use()) {
        _reader_table.remove(reader);
        delete reader;
    }
}

// Return an id for the specifed ImageFileReader.
u8 ImageFileReader::readerToID(ImageFileReader *reader) {
    // ID is just the cloaked reader address.
    return (u8)reader;
}

// Validate the image id.
bool ImageFileReader::idCheck(u8 id) {
    // Make sure the ID is a managed (_reader_table) reader.
    SimpleCriticalSectionLock cs(&_reader_table_lock);
    return _reader_table.contains((ImageFileReader*)id);
}

// Return an id for the specifed ImageFileReader.
ImageFileReader* ImageFileReader::idToReader(u8 id) {
    assert(idCheck(id) && "invalid image id");
    return (ImageFileReader*)id;
}

// Constructor intializes to a closed state.
ImageFileReader::ImageFileReader(const char* name, bool big_endian) {
    // Copy the image file name.
     int len = (int) strlen(name) + 1;
    _name = new char[len];
    strncpy(_name, name, len);
    // Initialize for a closed file.
    _fd = -1;
    _endian = Endian::get_handler(big_endian);
    _index_data = NULL;
}

// Close image and free up data structures.
ImageFileReader::~ImageFileReader() {
    // Ensure file is closed.
    close();
    // Free up name.
    if (_name) {
        delete[] _name;
        _name = NULL;
    }
}

// Open image file for read access.
bool ImageFileReader::open() {
    char buffer[IMAGE_MAX_PATH];

    // If file exists open for reading.
    _fd = osSupport::openReadOnly(_name);
    if (_fd == -1) {
        return false;
    }
    // Retrieve the file size.
    _file_size = osSupport::size(_name);
    // Read image file header and verify it has a valid header.
    size_t header_size = sizeof(ImageHeader);
    if (_file_size < header_size ||
        !read_at((u1*)&_header, header_size, 0) ||
        _header.magic(_endian) != IMAGE_MAGIC ||
        _header.major_version(_endian) != MAJOR_VERSION ||
        _header.minor_version(_endian) != MINOR_VERSION) {
        close();
        return false;
    }
    // Size of image index.
    _index_size = index_size();
    // Make sure file is large enough to contain the index.
    if (_file_size < _index_size) {
        return false;
    }
    // Determine how much of the image is memory mapped.
    size_t map_size = (size_t)(MemoryMapImage ? _file_size : _index_size);
    // Memory map image (minimally the index.)
    _index_data = (u1*)osSupport::map_memory(_fd, _name, 0, map_size);
    assert(_index_data && "image file not memory mapped");
    // Retrieve length of index perfect hash table.
    u4 length = table_length();
    // Compute offset of the perfect hash table redirect table.
    u4 redirect_table_offset = (u4)header_size;
    // Compute offset of index attribute offsets.
    u4 offsets_table_offset = redirect_table_offset + length * sizeof(s4);
    // Compute offset of index location attribute data.
    u4 location_bytes_offset = offsets_table_offset + length * sizeof(u4);
    // Compute offset of index string table.
    u4 string_bytes_offset = location_bytes_offset + locations_size();
    // Compute address of the perfect hash table redirect table.
    _redirect_table = (s4*)(_index_data + redirect_table_offset);
    // Compute address of index attribute offsets.
    _offsets_table = (u4*)(_index_data + offsets_table_offset);
    // Compute address of index location attribute data.
    _location_bytes = _index_data + location_bytes_offset;
    // Compute address of index string table.
    _string_bytes = _index_data + string_bytes_offset;

    // Initialize the module data
    ImageModuleData::module_data_name(buffer, _name);
    module_data = new ImageModuleData(this, buffer);
    // Successful open.
    return true;
}

// Close image file.
void ImageFileReader::close() {
    // Deallocate the index.
    if (_index_data) {
        osSupport::unmap_memory((char*)_index_data, _index_size);
        _index_data = NULL;
    }
    // Close file.
    if (_fd != -1) {
        osSupport::close(_fd);
        _fd = -1;
    }
}

// Read directly from the file.
bool ImageFileReader::read_at(u1* data, u8 size, u8 offset) const {
    return (u8)osSupport::read(_fd, (char*)data, size, offset) == size;
}

// Find the location attributes associated with the path.    Returns true if
// the location is found, false otherwise.
bool ImageFileReader::find_location(const char* path, ImageLocation& location) const {
    // Locate the entry in the index perfect hash table.
    s4 index = ImageStrings::find(_endian, path, _redirect_table, table_length());
    // If is found.
    if (index != ImageStrings::NOT_FOUND) {
        // Get address of first byte of location attribute stream.
        u1* data = get_location_data(index);
        // Expand location attributes.
        location.set_data(data);
        // Make sure result is not a false positive.
        return verify_location(location, path);
    }
    return false;
}

// Find the location index and size associated with the path.
// Returns the location index and size if the location is found, 0 otherwise.
u4 ImageFileReader::find_location_index(const char* path, u8 *size) const {
    // Locate the entry in the index perfect hash table.
    s4 index = ImageStrings::find(_endian, path, _redirect_table, table_length());
    // If found.
    if (index != ImageStrings::NOT_FOUND) {
        // Get address of first byte of location attribute stream.
        u4 offset = get_location_offset(index);
        u1* data = get_location_offset_data(offset);
        // Expand location attributes.
        ImageLocation location(data);
        // Make sure result is not a false positive.
        if (verify_location(location, path)) {
                *size = (jlong)location.get_attribute(ImageLocation::ATTRIBUTE_UNCOMPRESSED);
                return offset;
        }
    }
    return 0;            // not found
}

// Assemble the location path from the string fragments indicated in the location attributes.
void ImageFileReader::location_path(ImageLocation& location, char* path, size_t max) const {
    // Manage the image string table.
    ImageStrings strings(_string_bytes, _header.strings_size(_endian));
    // Position to first character of the path buffer.
    char* next = path;
    // Temp for string length.
    size_t length;
    // Get module string.
    const char* module = location.get_attribute(ImageLocation::ATTRIBUTE_MODULE, strings);
    // If module string is not empty string.
    if (*module != '\0') {
        // Get length of module name.
        length = strlen(module);
        // Make sure there is no buffer overflow.
        assert(next - path + length + 2 < max && "buffer overflow");
        // Append '/module/'.
        *next++ = '/';
        strncpy(next, module, length); next += length;
        *next++ = '/';
    }
    // Get parent (package) string.
    const char* parent = location.get_attribute(ImageLocation::ATTRIBUTE_PARENT, strings);
    // If parent string is not empty string.
    if (*parent != '\0') {
        // Get length of module string.
        length = strlen(parent);
        // Make sure there is no buffer overflow.
        assert(next - path + length + 1 < max && "buffer overflow");
        // Append 'patent/' .
        strncpy(next, parent, length); next += length;
        *next++ = '/';
    }
    // Get base name string.
    const char* base = location.get_attribute(ImageLocation::ATTRIBUTE_BASE, strings);
    // Get length of base name.
    length = strlen(base);
    // Make sure there is no buffer overflow.
    assert(next - path + length < max && "buffer overflow");
    // Append base name.
    strncpy(next, base, length); next += length;
    // Get extension string.
    const char* extension = location.get_attribute(ImageLocation::ATTRIBUTE_EXTENSION, strings);
    // If extension string is not empty string.
    if (*extension != '\0') {
        // Get length of extension string.
        length = strlen(extension);
        // Make sure there is no buffer overflow.
        assert(next - path + length + 1 < max && "buffer overflow");
        // Append '.extension' .
        *next++ = '.';
        strncpy(next, extension, length); next += length;
    }
    // Make sure there is no buffer overflow.
    assert((size_t)(next - path) < max && "buffer overflow");
    // Terminate string.
    *next = '\0';
}

// Verify that a found location matches the supplied path (without copying.)
bool ImageFileReader::verify_location(ImageLocation& location, const char* path) const {
    // Manage the image string table.
    ImageStrings strings(_string_bytes, _header.strings_size(_endian));
    // Position to first character of the path string.
    const char* next = path;
    // Get module name string.
    const char* module = location.get_attribute(ImageLocation::ATTRIBUTE_MODULE, strings);
    // If module string is not empty.
    if (*module != '\0') {
        // Compare '/module/' .
        if (*next++ != '/') return false;
        if (!(next = ImageStrings::starts_with(next, module))) return false;
        if (*next++ != '/') return false;
    }
    // Get parent (package) string
    const char* parent = location.get_attribute(ImageLocation::ATTRIBUTE_PARENT, strings);
    // If parent string is not empty string.
    if (*parent != '\0') {
        // Compare 'parent/' .
        if (!(next = ImageStrings::starts_with(next, parent))) return false;
        if (*next++ != '/') return false;
    }
    // Get base name string.
    const char* base = location.get_attribute(ImageLocation::ATTRIBUTE_BASE, strings);
    // Compare with basne name.
    if (!(next = ImageStrings::starts_with(next, base))) return false;
    // Get extension string.
    const char* extension = location.get_attribute(ImageLocation::ATTRIBUTE_EXTENSION, strings);
    // If extension is not empty.
    if (*extension != '\0') {
        // Compare '.extension' .
        if (*next++ != '.') return false;
        if (!(next = ImageStrings::starts_with(next, extension))) return false;
    }
    // True only if complete match and no more characters.
    return *next == '\0';
}

// Return the resource for the supplied location offset.
void ImageFileReader::get_resource(u4 offset, u1* uncompressed_data) const {
        // Get address of first byte of location attribute stream.
        u1* data = get_location_offset_data(offset);
        // Expand location attributes.
        ImageLocation location(data);
        // Read the data
        get_resource(location, uncompressed_data);
}

// Return the resource for the supplied location.
void ImageFileReader::get_resource(ImageLocation& location, u1* uncompressed_data) const {
    // Retrieve the byte offset and size of the resource.
    u8 offset = location.get_attribute(ImageLocation::ATTRIBUTE_OFFSET);
    u8 uncompressed_size = location.get_attribute(ImageLocation::ATTRIBUTE_UNCOMPRESSED);
    u8 compressed_size = location.get_attribute(ImageLocation::ATTRIBUTE_COMPRESSED);
    // If the resource is compressed.
    if (compressed_size != 0) {
        u1* compressed_data;
        // If not memory mapped read in bytes.
        if (!MemoryMapImage) {
            // Allocate buffer for compression.
            compressed_data = new u1[(u4)compressed_size];
            // Read bytes from offset beyond the image index.
            bool is_read = read_at(compressed_data, compressed_size, _index_size + offset);
            assert(is_read && "error reading from image or short read");
        } else {
            compressed_data = get_data_address() + offset;
        }
        // Get image string table.
        const ImageStrings strings = get_strings();
        // Decompress resource.
        ImageDecompressor::decompress_resource(compressed_data, uncompressed_data, (u4)uncompressed_size,
                        &strings);
        // If not memory mapped then release temporary buffer.
        if (!MemoryMapImage) {
                delete[] compressed_data;
        }
    } else {
        // Read bytes from offset beyond the image index.
        bool is_read = read_at(uncompressed_data, uncompressed_size, _index_size + offset);
        assert(is_read && "error reading from image or short read");
    }
}

// Return the ImageModuleData for this image
ImageModuleData * ImageFileReader::get_image_module_data() {
        return module_data;
}
