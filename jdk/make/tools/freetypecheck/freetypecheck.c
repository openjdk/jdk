/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 */

/* Test program for freetype sanity check.
   Prints "Failed" messages to STDOUT if check fails. */

#include <stdio.h>
#include <string.h>

#include "ft2build.h"
#include FT_FREETYPE_H

#define QUOTEMACRO(x) QUOTEME(x)
#define QUOTEME(x) #x

int main(int argc, char** argv) {
   char v[50];
   FT_Int major, minor, patch;
   FT_Library library;
   sprintf(v, "%d.%d.%d", FREETYPE_MAJOR, FREETYPE_MINOR, FREETYPE_PATCH);

   printf("Required version of freetype: %s\n",
       QUOTEMACRO(REQUIRED_FREETYPE_VERSION));

   printf("Detected freetype headers: %s\n", v);
   if (strcmp(v, QUOTEMACRO(REQUIRED_FREETYPE_VERSION)) < 0) {
       printf("Failed: headers are too old.\n");
   }

   FT_Init_FreeType(&library);
   FT_Library_Version(library, &major, &minor, &patch);
   sprintf(v, "%d.%d.%d", major, minor, patch);

   printf("Detected freetype library: %s\n", v);
   if (strcmp(v, QUOTEMACRO(REQUIRED_FREETYPE_VERSION)) < 0) {
      printf("Failed: too old library.\n");
   }

   return 0;
}
