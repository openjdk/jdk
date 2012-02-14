/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


// To ensure winsock2.h is used, it has to be included ahead of
// windows.h, which includes winsock.h by default.
#include <winsock2.h>
#include <windows.h>
#include <io.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <mmsystem.h>
#include <fcntl.h>
#include <process.h>

#include "jni.h"
#include "hprof.h"

int
md_getpid(void)
{
    static int pid = -1;

    if ( pid >= 0 ) {
        return pid;
    }
    pid = getpid();
    return pid;
}

void
md_sleep(unsigned seconds)
{
    Sleep((DWORD)seconds*1000);
}

void
md_init(void)
{
}

int
md_connect(char *hostname, unsigned short port)
{
    struct hostent *hentry;
    struct sockaddr_in s;
    int fd;

    /* create a socket */
    fd = (int)socket(AF_INET, SOCK_STREAM, 0);

    /* find remote host's addr from name */
    if ((hentry = gethostbyname(hostname)) == NULL) {
        return -1;
    }
    (void)memset((char *)&s, 0, sizeof(s));
    /* set remote host's addr; its already in network byte order */
    (void)memcpy(&s.sin_addr.s_addr, *(hentry->h_addr_list),
           (int)sizeof(s.sin_addr.s_addr));
    /* set remote host's port */
    s.sin_port = htons(port);
    s.sin_family = AF_INET;

    /* now try connecting */
    if (-1 == connect(fd, (struct sockaddr*)&s, sizeof(s))) {
        return 0;
    }
    return fd;
}

int
md_recv(int f, char *buf, int len, int option)
{
    return recv(f, buf, len, option);
}

int
md_shutdown(int filedes, int option)
{
    return shutdown(filedes, option);
}

int
md_open(const char *filename)
{
    return open(filename, O_RDONLY);
}

int
md_open_binary(const char *filename)
{
    return open(filename, O_RDONLY|O_BINARY);
}

int
md_creat(const char *filename)
{
    return open(filename, O_CREAT | O_WRONLY | O_TRUNC,
                             _S_IREAD | _S_IWRITE);
}

int
md_creat_binary(const char *filename)
{
    return open(filename, O_CREAT | O_WRONLY | O_TRUNC | O_BINARY,
                            _S_IREAD | _S_IWRITE);
}

jlong
md_seek(int filedes, jlong pos)
{
    jlong new_pos;

    if ( pos == (jlong)-1 ) {
        new_pos = _lseeki64(filedes, 0L, SEEK_END);
    } else {
        new_pos = _lseeki64(filedes, pos, SEEK_SET);
    }
    return new_pos;
}

void
md_close(int filedes)
{
    (void)closesocket(filedes);
}

int
md_send(int s, const char *msg, int len, int flags)
{
    return send(s, msg, len, flags);
}

int
md_read(int filedes, void *buf, int nbyte)
{
    return read(filedes, buf, nbyte);
}

int
md_write(int filedes, const void *buf, int nbyte)
{
    return write(filedes, buf, nbyte);
}

jlong
md_get_microsecs(void)
{
    return (jlong)(timeGetTime())*(jlong)1000;
}

#define FT2JLONG(ft) \
        ((jlong)(ft).dwHighDateTime << 32 | (jlong)(ft).dwLowDateTime)

jlong
md_get_timemillis(void)
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
        fileTime_1_1_70 = FT2JLONG(ft0);
    }

    GetSystemTime(&st0);
    SystemTimeToFileTime(&st0, &ft0);

    return (FT2JLONG(ft0) - fileTime_1_1_70) / 10000;
}

jlong
md_get_thread_cpu_timemillis(void)
{
    return md_get_timemillis();
}

HINSTANCE hJavaInst;
static int nError = 0;

BOOL WINAPI
DllMain(HINSTANCE hinst, DWORD reason, LPVOID reserved)
{
    WSADATA wsaData;
    switch (reason) {
        case DLL_PROCESS_ATTACH:
            hJavaInst = hinst;
            nError = WSAStartup(MAKEWORD(2,0), &wsaData);
            break;
        case DLL_PROCESS_DETACH:
            WSACleanup();
            hJavaInst = NULL;
        default:
            break;
    }
    return TRUE;
}

void
md_get_prelude_path(char *path, int path_len, char *filename)
{
    char libdir[FILENAME_MAX+1];
    char *lastSlash;

    GetModuleFileName(hJavaInst, libdir, FILENAME_MAX);

    /* This is actually in the bin directory, so move above bin for lib */
    lastSlash = strrchr(libdir, '\\');
    if ( lastSlash != NULL ) {
        *lastSlash = '\0';
    }
    lastSlash = strrchr(libdir, '\\');
    if ( lastSlash != NULL ) {
        *lastSlash = '\0';
    }
    (void)md_snprintf(path, path_len, "%s\\lib\\%s", libdir, filename);
}

int
md_vsnprintf(char *s, int n, const char *format, va_list ap)
{
    return _vsnprintf(s, n, format, ap);
}

int
md_snprintf(char *s, int n, const char *format, ...)
{
    int ret;
    va_list ap;

    va_start(ap, format);
    ret = md_vsnprintf(s, n, format, ap);
    va_end(ap);
    return ret;
}

void
md_system_error(char *buf, int len)
{
    long errval;

    errval = GetLastError();
    buf[0] = '\0';
    if (errval != 0) {
        int n;

        n = FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM|FORMAT_MESSAGE_IGNORE_INSERTS,
                              NULL, errval,
                              0, buf, len, NULL);
        if (n > 3) {
            /* Drop final '.', CR, LF */
            if (buf[n - 1] == '\n') n--;
            if (buf[n - 1] == '\r') n--;
            if (buf[n - 1] == '.') n--;
            buf[n] = '\0';
        }
    }
}

unsigned
md_htons(unsigned short s)
{
    return htons(s);
}

unsigned
md_htonl(unsigned l)
{
    return htonl(l);
}

unsigned
md_ntohs(unsigned short s)
{
    return ntohs(s);
}

unsigned
md_ntohl(unsigned l)
{
    return ntohl(l);
}

static int
get_last_error_string(char *buf, int len)
{
    long errval;

    errval = GetLastError();
    if (errval != 0) {
        /* DOS error */
        int n;

        n = FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM|FORMAT_MESSAGE_IGNORE_INSERTS,
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
        const char *s;
        int         n;

        s = strerror(errno);
        n = (int)strlen(s);
        if (n >= len) {
            n = len - 1;
        }
        (void)strncpy(buf, s, n);
        buf[n] = '\0';
        return n;
    }

    return 0;
}

/* Build a machine dependent library name out of a path and file name.  */
void
md_build_library_name(char *holder, int holderlen, char *pname, char *fname)
{
    int   pnamelen;
    char  c;

    pnamelen = pname ? (int)strlen(pname) : 0;
    c = (pnamelen > 0) ? pname[pnamelen-1] : 0;

    /* Quietly truncates on buffer overflow. Should be an error. */
    if (pnamelen + strlen(fname) + 10 > (unsigned int)holderlen) {
        *holder = '\0';
        return;
    }

    if (pnamelen == 0) {
        sprintf(holder, "%s.dll", fname);
    } else if (c == ':' || c == '\\') {
        sprintf(holder, "%s%s.dll", pname, fname);
    } else {
        sprintf(holder, "%s\\%s.dll", pname, fname);
    }
}

void *
md_load_library(const char * name, char *err_buf, int err_buflen)
{
    void *result;

    result = LoadLibrary(name);
    if (result == NULL) {
        /* Error message is pretty lame, try to make a better guess. */
        long errcode;

        errcode = GetLastError();
        if (errcode == ERROR_MOD_NOT_FOUND) {
            strncpy(err_buf, "Can't find dependent libraries", err_buflen-2);
            err_buf[err_buflen-1] = '\0';
        } else {
            get_last_error_string(err_buf, err_buflen);
        }
    }
    return result;
}

void
md_unload_library(void *handle)
{
    FreeLibrary(handle);
}

void *
md_find_library_entry(void *handle, const char *name)
{
    return GetProcAddress(handle, name);
}
