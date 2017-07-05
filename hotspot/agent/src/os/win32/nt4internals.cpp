/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "nt4internals.hpp"
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

namespace NT4 {

static HMODULE ntDLL = NULL;

HMODULE loadNTDLL() {
  if (ntDLL == NULL) {
    ntDLL = LoadLibrary("NTDLL.DLL");
  }

  assert(ntDLL != NULL);
  return ntDLL;
}

void unloadNTDLL() {
  if (ntDLL != NULL) {
    FreeLibrary(ntDLL);
    ntDLL = NULL;
  }
}

} // namespace NT4

static HMODULE psapiDLL = NULL;

HMODULE
loadPSAPIDLL() {
  if (psapiDLL == NULL) {
    psapiDLL = LoadLibrary("PSAPI.DLL");
  }

  if (psapiDLL == NULL) {
    fprintf(stderr, "Simple Windows Debug Server requires PSAPI.DLL on Windows NT 4.0.\n");
    fprintf(stderr, "Please install this DLL from the SDK and restart the server.\n");
    exit(1);
  }

  return psapiDLL;
}

void
unloadPSAPIDLL() {
  if (psapiDLL != NULL) {
    FreeLibrary(psapiDLL);
    psapiDLL = NULL;
  }
}
