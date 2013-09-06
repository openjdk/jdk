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


// Implementation of LoadedLibraries and friends

// Ultimately this just uses loadquery()
// See:
// http://publib.boulder.ibm.com/infocenter/pseries/v5r3/index.jsp
//      ?topic=/com.ibm.aix.basetechref/doc/basetrf1/loadquery.htm

#ifndef __STDC_FORMAT_MACROS
#define __STDC_FORMAT_MACROS
#endif
// 'allocation.inline.hpp' triggers the inclusion of 'inttypes.h' which defines macros
// required by the definitions in 'globalDefinitions.hpp'. But these macros in 'inttypes.h'
// are only defined if '__STDC_FORMAT_MACROS' is defined!
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/threadCritical.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"
#include "loadlib_aix.hpp"
#include "porting_aix.hpp"

// For loadquery()
#include <sys/ldr.h>

///////////////////////////////////////////////////////////////////////////////
// Implementation for LoadedLibraryModule

// output debug info
void LoadedLibraryModule::print(outputStream* os) const {
  os->print("%15.15s: text: " INTPTR_FORMAT " - " INTPTR_FORMAT
               ", data: " INTPTR_FORMAT " - " INTPTR_FORMAT " ",
      shortname, text_from, text_to, data_from, data_to);
  os->print(" %s", fullpath);
  if (strlen(membername) > 0) {
    os->print("(%s)", membername);
  }
  os->cr();
}


///////////////////////////////////////////////////////////////////////////////
// Implementation for LoadedLibraries

// class variables
LoadedLibraryModule LoadedLibraries::tab[MAX_MODULES];
int LoadedLibraries::num_loaded = 0;

// Checks whether the address p points to any of the loaded code segments.
// If it does, returns the LoadedLibraryModule entry. If not, returns NULL.
// static
const LoadedLibraryModule* LoadedLibraries::find_for_text_address(const unsigned char* p) {

  if (num_loaded == 0) {
    reload();
  }
  for (int i = 0; i < num_loaded; i++) {
    if (tab[i].is_in_text(p)) {
      return &tab[i];
    }
  }
  return NULL;
}

// Checks whether the address p points to any of the loaded data segments.
// If it does, returns the LoadedLibraryModule entry. If not, returns NULL.
// static
const LoadedLibraryModule* LoadedLibraries::find_for_data_address(const unsigned char* p) {
  if (num_loaded == 0) {
    reload();
  }
  for (int i = 0; i < num_loaded; i++) {
    if (tab[i].is_in_data(p)) {
      return &tab[i];
    }
  }
  return NULL;
}

// Rebuild the internal table of LoadedLibraryModule objects
// static
void LoadedLibraries::reload() {

  ThreadCritical cs;

  // discard old content
  num_loaded = 0;

  // Call loadquery(L_GETINFO..) to get a list of all loaded Dlls from AIX.
  size_t buf_size = 4096;
  char* loadquery_buf = AllocateHeap(buf_size, mtInternal);

  while(loadquery(L_GETINFO, loadquery_buf, buf_size) == -1) {
    if (errno == ENOMEM) {
      buf_size *= 2;
      loadquery_buf = ReallocateHeap(loadquery_buf, buf_size, mtInternal);
    } else {
      FreeHeap(loadquery_buf);
      // Ensure that the uintptr_t pointer is valid
      assert(errno != EFAULT, "loadquery: Invalid uintptr_t in info buffer.");
      fprintf(stderr, "loadquery failed (%d %s)", errno, strerror(errno));
      return;
    }
  }

  // Iterate over the loadquery result. For details see sys/ldr.h on AIX.
  const struct ld_info* p = (struct ld_info*) loadquery_buf;

  // Ensure we have all loaded libs.
  bool all_loaded = false;
  while(num_loaded < MAX_MODULES) {
    LoadedLibraryModule& mod = tab[num_loaded];
    mod.text_from = (const unsigned char*) p->ldinfo_textorg;
    mod.text_to   = (const unsigned char*) (((char*)p->ldinfo_textorg) + p->ldinfo_textsize);
    mod.data_from = (const unsigned char*) p->ldinfo_dataorg;
    mod.data_to   = (const unsigned char*) (((char*)p->ldinfo_dataorg) + p->ldinfo_datasize);
    sprintf(mod.fullpath, "%.*s", sizeof(mod.fullpath), p->ldinfo_filename);
    // do we have a member name as well (see ldr.h)?
    const char* p_mbr_name = p->ldinfo_filename + strlen(p->ldinfo_filename) + 1;
    if (*p_mbr_name) {
      sprintf(mod.membername, "%.*s", sizeof(mod.membername), p_mbr_name);
    } else {
      mod.membername[0] = '\0';
    }

    // fill in the short name
    const char* p_slash = strrchr(mod.fullpath, '/');
    if (p_slash) {
      sprintf(mod.shortname, "%.*s", sizeof(mod.shortname), p_slash + 1);
    } else {
      sprintf(mod.shortname, "%.*s", sizeof(mod.shortname), mod.fullpath);
    }
    num_loaded ++;

    // next entry...
    if (p->ldinfo_next) {
      p = (struct ld_info*)(((char*)p) + p->ldinfo_next);
    } else {
      all_loaded = true;
      break;
    }
  }

  FreeHeap(loadquery_buf);

  // Ensure we have all loaded libs
  assert(all_loaded, "loadquery returned more entries then expected. Please increase MAX_MODULES");

} // end LoadedLibraries::reload()


// output loaded libraries table
//static
void LoadedLibraries::print(outputStream* os) {

  for (int i = 0; i < num_loaded; i++) {
    tab[i].print(os);
  }

}

