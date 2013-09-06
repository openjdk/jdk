/*
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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


// Loadlib_aix.cpp contains support code for analysing the memory
// layout of loaded binaries in ones own process space.
//
// It is needed, among other things, to provide a  dladdr() emulation, because
// that one is not provided by AIX

#ifndef OS_AIX_VM_LOADLIB_AIX_HPP
#define OS_AIX_VM_LOADLIB_AIX_HPP

class outputStream;

// This class holds information about a single loaded library module.
// Note that on AIX, a single library can be spread over multiple
// uintptr_t range on a module base, eg.
// libC.a(shr3_64.o) or libC.a(shrcore_64.o).
class LoadedLibraryModule {

    friend class LoadedLibraries;

    char fullpath[512];  // eg /usr/lib/libC.a
    char shortname[30];  // eg libC.a
    char membername[30]; // eg shrcore_64.o
    const unsigned char* text_from;
    const unsigned char* text_to;
    const unsigned char* data_from;
    const unsigned char* data_to;

  public:

    const char* get_fullpath() const {
      return fullpath;
    }
    const char* get_shortname() const {
      return shortname;
    }
    const char* get_membername() const {
      return membername;
    }

    // text_from, text_to: returns the range of the text (code)
    // segment for that module
    const unsigned char* get_text_from() const {
      return text_from;
    }
    const unsigned char* get_text_to() const {
      return text_to;
    }

    // data_from/data_to: returns the range of the data
    // segment for that module
    const unsigned char* get_data_from() const {
      return data_from;
    }
    const unsigned char* get_data_to() const {
      return data_to;
    }

    // returns true if the
    bool is_in_text(const unsigned char* p) const {
      return p >= text_from && p < text_to ? true : false;
    }

    bool is_in_data(const unsigned char* p) const {
      return p >= data_from && p < data_to ? true : false;
    }

    // output debug info
    void print(outputStream* os) const;

}; // end LoadedLibraryModule

// This class is a singleton holding a map of all loaded binaries
// in the AIX process space.
class LoadedLibraries
// : AllStatic (including allocation.hpp just for AllStatic is overkill.)
{

  private:

    enum {MAX_MODULES = 100};
    static LoadedLibraryModule tab[MAX_MODULES];
    static int num_loaded;

  public:

    // rebuild the internal table of LoadedLibraryModule objects
    static void reload();

    // checks whether the address p points to any of the loaded code segments.
    // If it does, returns the LoadedLibraryModule entry. If not, returns NULL.
    static const LoadedLibraryModule* find_for_text_address(const  unsigned char* p);

    // checks whether the address p points to any of the loaded data segments.
    // If it does, returns the LoadedLibraryModule entry. If not, returns NULL.
    static const LoadedLibraryModule* find_for_data_address(const  unsigned char* p);

    // output debug info
    static void print(outputStream* os);

}; // end LoadedLibraries


#endif // OS_AIX_VM_LOADLIB_AIX_HPP
