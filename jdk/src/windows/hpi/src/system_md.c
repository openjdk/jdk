/*
 * Copyright (c) 1994, 2002, Oracle and/or its affiliates. All rights reserved.
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

#include <windows.h>
#include <float.h>              /* For constants for _control87() */
#include <signal.h>
#include <time.h>               /* For _tzset() and _ftime() */
#include <errno.h>

#include "hpi_impl.h"

#include "jni_md.h"
#include "monitor_md.h"


static int pending_signals[NSIG];
static HANDLE sigEvent;
static CRITICAL_SECTION userSigMon;


void sysSignalNotify(int sig)
{
    sys_thread_t *self = sysThreadSelf();
    EnterCriticalSection(&userSigMon);
    pending_signals[sig]++;
    LeaveCriticalSection(&userSigMon);
    SetEvent(sigEvent);
}

static int lookupSignal()
{
    int i;
    EnterCriticalSection(&userSigMon);
    for (i = 0; i < NSIG; i++) {
        if (pending_signals[i]) {
            pending_signals[i]--;
            LeaveCriticalSection(&userSigMon);
            return i;
        }
    }
    LeaveCriticalSection(&userSigMon);
    return -1;
}

int sysSignalWait()
{
    int sig;
    while ((sig = lookupSignal()) == -1) {
        WaitForSingleObject(sigEvent, INFINITE);
    }
    return sig;
}

signal_handler_t sysSignal(int sig, signal_handler_t newHandler)
{
    return (signal_handler_t)signal(sig, (void (*)(int))newHandler);
}

void sysRaise(int sig)
{
    raise(sig);
}

int sysThreadBootstrap(sys_thread_t **tidP, sys_mon_t **lockP, int nb)
{
    extern void InitializeMem(void);

    threadBootstrapMD(tidP, lockP, nb);

    sigEvent = CreateEvent(NULL, FALSE, FALSE, NULL);
    InitializeCriticalSection(&userSigMon);
    memset(pending_signals, 0, sizeof(pending_signals));

    /*
     * Change default for std. streams stdout, stderr,
     * stdin to be O_BINARY not O_TEXT.  The `\r` characters
     * corrupt binary files.
     */

    _setmode(0, O_BINARY);
    _setmode(1, O_BINARY);
    _setmode(2, O_BINARY);

    /*
     * Set floating point processor to no floating point exceptions.
     * See bug 4027374.  Should be the same values VC++ would set them
     * to, but by doing this here we ensure other dll's don't override.
     */
    _control87(_MCW_EM | _RC_NEAR | _PC_53, _MCW_EM | _MCW_RC | _MCW_PC);

    InitializeMem();

    return SYS_OK;
}

long
sysGetMilliTicks(void)
{
    return(GetTickCount());
}

#define FT2INT64(ft) \
        ((jlong)(ft).dwHighDateTime << 32 | (jlong)(ft).dwLowDateTime)

jlong
sysTimeMillis(void)
{
    static jlong fileTime_1_1_70 = 0;
    SYSTEMTIME st0;
    FILETIME   ft0;

    if (fileTime_1_1_70 == 0) {
        /* Initialize fileTime_1_1_70 -- the Win32 file time of midnight
         * 1/1/70.
         */

        memset(&st0, 0, sizeof(st0));
        st0.wYear  = 1970;
        st0.wMonth = 1;
        st0.wDay   = 1;
        SystemTimeToFileTime(&st0, &ft0);
        fileTime_1_1_70 = FT2INT64(ft0);
    }

    GetSystemTime(&st0);
    SystemTimeToFileTime(&st0, &ft0);

    return (FT2INT64(ft0) - fileTime_1_1_70) / 10000;
}

void *
sysAllocateMem(long size)
{
    return malloc(size);
}

int sysShutdown()
{
    return SYS_OK;
}

unsigned
sleep(unsigned seconds)
{
    Sleep(seconds * 1000);
    return 0;
}

int
sysGetLastErrorString(char *buf, int len)
{
    long errval;

    if ((errval = GetLastError()) != 0) {
        /* DOS error */
        int n = FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM|FORMAT_MESSAGE_IGNORE_INSERTS,
                              NULL, errval,
                              0, buf, len, NULL);
        if (n > 3) {
            /* Drop final '.', CR, LF */
            if (buf[n - 1] == '\n') n--;
            if (buf[n - 1] == '\r') n--;
            if (buf[n - 1] == '.') n--;
            buf[n] = '\0';
        }
        return n;
    }

    if (errno != 0) {
        /* C runtime error that has no corresponding DOS error code */
        const char *s = strerror(errno);
        int n = strlen(s);
        if (n >= len) n = len - 1;
        strncpy(buf, s, n);
        buf[n] = '\0';
        return n;
    }

    return 0;
}
