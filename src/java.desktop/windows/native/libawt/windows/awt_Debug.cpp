/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "awt.h"
#include "awt_Toolkit.h"
#include "debug_mem.h"

extern void DumpJavaStack();

#if defined(DEBUG)

////////////////////////////////////////////////////////////////////////////////////
// avoid pulling in our redefinition of 'new'
// since we actually need to implement it here
#if defined(new)
#undef new
#endif
//

void * operator new(size_t size, const char * filename, int linenumber) {
    void * ptr = DMem_AllocateBlock(size, filename, linenumber);
    if (ptr == NULL) {
        throw std::bad_alloc();
    }

    return ptr;
}

void * operator new[](size_t size, const char * filename, int linenumber) {
    void * ptr = DMem_AllocateBlock(size, filename, linenumber);
    if (ptr == NULL) {
        throw std::bad_alloc();
    }

    return ptr;
}

void operator delete(void *ptr, const char*, int) {
    DASSERTMSG(FALSE, "This version of 'delete' should never get called!!!");
}

void operator delete[](void *ptr, const char*, int) {
    DASSERTMSG(FALSE, "This version of 'delete' should never get called!!!");
}

void operator delete(void *ptr) throw() {
    DMem_FreeBlock(ptr);
}

////////////////////////////////////////////////////////////////////////////////////

//
// Declare a static object to init/fini the debug code
//
// specify that this static object will get constructed before
// any other static objects (except CRT objects) so the debug
// code can be used anywhere during the lifetime of the AWT dll
#pragma warning( disable:4073 ) // disable warning about using init_seg(lib) in non-3rd party library code
#pragma init_seg( lib )

static volatile AwtDebugSupport DebugSupport;
static int report_leaks = 0;

AwtDebugSupport::AwtDebugSupport() {
    DMem_Initialize();
    DTrace_Initialize();
    DAssert_SetCallback(AssertCallback);
}

AwtDebugSupport::~AwtDebugSupport() {
    if (report_leaks) {
        DMem_ReportLeaks();
    }
    DMem_Shutdown();
    DTrace_Shutdown();
}

static jboolean isHeadless() {
    jmethodID headlessFn;
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    // be on the safe side and avoid JNI warnings by calling ExceptionCheck
    // an accumulated exception is not cleared
    env->ExceptionCheck();
    jclass graphicsEnvClass = env->FindClass("java/awt/GraphicsEnvironment");

    if (graphicsEnvClass != NULL) {
        headlessFn = env->GetStaticMethodID(graphicsEnvClass, "isHeadless", "()Z");
        if (headlessFn != NULL) {
            return env->CallStaticBooleanMethod(graphicsEnvClass, headlessFn);
        }
    }
    return true;
}

void AwtDebugSupport::AssertCallback(const char * expr, const char * file, int line) {
    static const int ASSERT_MSG_SIZE = 1024;
    static const char * AssertFmt =
            "%s\r\n"
            "File '%s', at line %d\r\n"
            "GetLastError() is %x : %s\r\n"
            "Do you want to break into the debugger?";

    static char assertMsg[ASSERT_MSG_SIZE+1];
    DWORD lastError = GetLastError();
    LPSTR msgBuffer = NULL;
    int ret = IDNO;
    static jboolean headless = isHeadless();

    DWORD fret= FormatMessageA(FORMAT_MESSAGE_ALLOCATE_BUFFER |
                  FORMAT_MESSAGE_FROM_SYSTEM |
                  FORMAT_MESSAGE_IGNORE_INSERTS,
                  NULL,
                  lastError,
                  MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
                  (LPSTR)&msgBuffer, // it's an output parameter when allocate buffer is used
                  0,
                  NULL);

    if (msgBuffer == NULL) {
        msgBuffer = (LPSTR)"<Could not get GetLastError() message text>";
    }
    // format the assertion message
    _snprintf(assertMsg, ASSERT_MSG_SIZE, AssertFmt, expr, file, line, lastError, msgBuffer);
    if (fret != 0) {
        LocalFree(msgBuffer);
    }

    // tell the user the bad news
    fprintf(stderr, "*********************\n");
    fprintf(stderr, "AWT Assertion Failure\n");
    fprintf(stderr, "*********************\n");
    fprintf(stderr, "%s\n", assertMsg);
    fprintf(stderr, "*********************\n");

    if (!headless) {
        ret = MessageBoxA(NULL, assertMsg, "AWT Assertion Failure",
                          MB_YESNO|MB_ICONSTOP|MB_TASKMODAL);
    }

    // if clicked Yes, break into the debugger
    if ( ret == IDYES ) {
        # if defined(_M_IX86)
            _asm { int 3 };
        # else
            DebugBreak();
        # endif
    }
    // otherwise, try to continue execution
}

void AwtDebugSupport::GenerateLeaksReport() {
    report_leaks = 1;
}

#endif // DEBUG
