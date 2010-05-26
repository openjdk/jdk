/*
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

// This file is available under and governed by the GNU General Public
// License version 2 only, as published by the Free Software Foundation.
// However, the following notice accompanied the original version of this
// file:
//
//
//  Little cms
//  Copyright (C) 1998-2007 Marti Maria
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the Software
// is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
// THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.


#include "lcms.h"


// As a rule, only the functions visible from API can signal
// errors.

void cdecl cmsSignalError(int ErrorCode, const char *ErrorText, ...);

int  LCMSEXPORT cmsErrorAction(int lAbort);
void LCMSEXPORT cmsSetErrorHandler(cmsErrorHandlerFunction Fn);


// ******************************************************************

static int nDoAbort = LCMS_ERROR_ABORT;
static cmsErrorHandlerFunction UserErrorHandler = (cmsErrorHandlerFunction) NULL;


int LCMSEXPORT cmsErrorAction(int nAction)
{
       int nOld = nDoAbort;
       nDoAbort = nAction;

       return nOld;
}

void LCMSEXPORT cmsSetErrorHandler(cmsErrorHandlerFunction Fn)
{
       UserErrorHandler = Fn;
}


// Default error handler


void cmsSignalError(int ErrorCode, const char *ErrorText, ...)
{
       va_list args;

       if (nDoAbort == LCMS_ERROR_IGNORE) return;

        va_start(args, ErrorText);

        if (UserErrorHandler != NULL) {

            char Buffer[1024];

            vsnprintf(Buffer, 1023, ErrorText, args);
            va_end(args);

            if (UserErrorHandler(ErrorCode, Buffer)) {

                return;
                }
         }

#if defined( __CONSOLE__ ) || defined( NON_WINDOWS )

              fprintf(stderr, "lcms: Error #%d; ", ErrorCode);
              vfprintf(stderr, ErrorText, args);
              fprintf(stderr, "\n");
              va_end(args);

              if (nDoAbort == LCMS_ERROR_ABORT) exit(1);
#else
              {
              char Buffer1[1024];
              char Buffer2[256];

              snprintf(Buffer1,  767, "Error #%x; ", ErrorCode);
              vsnprintf(Buffer2, 255, ErrorText, args);
              strcat(Buffer1, Buffer2);
              MessageBox(NULL, Buffer1, "Little cms",
                                          MB_OK|MB_ICONSTOP|MB_TASKMODAL);
              va_end(args);

              if (nDoAbort == LCMS_ERROR_ABORT) {

#ifdef __BORLANDC__
                    _cexit();
#endif

                  FatalAppExit(0, "lcms is terminating application");
              }
              }
#endif
}
