/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _JAVASOFT_HPI_IMPL_H_
#define _JAVASOFT_HPI_IMPL_H_

#include "hpi.h"

#ifdef __cplusplus
extern "C" {
#endif

#include "vm_calls.h"

extern int nReservedBytes;
sys_thread_t *allocThreadBlock(void);
void freeThreadBlock(sys_thread_t *tid);
int threadBootstrapMD(sys_thread_t **tid, sys_mon_t **lockP, int nb);

HPI_SysInfo *sysGetSysInfo(void);
long    sysGetMilliTicks(void);
jlong sysTimeMillis(void);

signal_handler_t sysSignal(int sig, signal_handler_t handler);
void sysRaise(int sig);
void sysSignalNotify(int sig);
int sysSignalWait(void);
int sysShutdown(void);
int sysSetLoggingLevel(int level);
bool_t sysSetMonitoringOn(bool_t on);
int sysGetLastErrorString(char *buf, int len);

void *  sysMalloc(size_t);
void *  sysRealloc(void*, size_t);
void    sysFree(void*);
void *  sysCalloc(size_t, size_t);
char *  sysStrdup(const char * string);
void *  sysMapMem(size_t, size_t *);
void *  sysUnmapMem(void *, size_t, size_t *);
void *  sysCommitMem(void * ptr, size_t size, size_t * actual);
void *  sysDecommitMem(void * ptr, size_t size, size_t * actual);
void *  sysAllocBlock(size_t, void**);
void    sysFreeBlock(void *);

void    sysBuildLibName(char *, int, char *, char *);
int     sysBuildFunName(char *, int, int, int);
void *  sysLoadLibrary(const char *, char *err_buf, int err_buflen);
void    sysUnloadLibrary(void *);
void *  sysFindLibraryEntry(void *, const char *);

int     sysThreadBootstrap(sys_thread_t **, sys_mon_t **, int);
int     sysThreadCreate(sys_thread_t **,
                        long,
                        void (*)(void *),
                        void *arg);
void    sysThreadExit(void);
sys_thread_t * sysThreadSelf(void);
void    sysThreadYield(void);
int     sysThreadSuspend(sys_thread_t *);
int     sysThreadResume(sys_thread_t *);
int     sysThreadSetPriority(sys_thread_t *, int);
int     sysThreadGetPriority(sys_thread_t *, int *);
void *  sysThreadStackPointer(sys_thread_t *);
void *  sysThreadStackTop(sys_thread_t *);
long *  sysThreadRegs(sys_thread_t *, int *);
int     sysThreadSingle(void);
void    sysThreadMulti(void);
int     sysThreadEnumerateOver(int (*)(sys_thread_t *, void *), void *);
int     sysThreadCheckStack(void);
void    sysThreadPostException(sys_thread_t *, void *);
void    sysThreadInterrupt(sys_thread_t *);
int     sysThreadIsInterrupted(sys_thread_t *, int);
int     sysThreadAlloc(sys_thread_t **);
int     sysThreadFree(void);
size_t  sysThreadSizeof(void);
jlong   sysThreadCPUTime(void);
int     sysThreadGetStatus(sys_thread_t *, sys_mon_t **);
int     sysAdjustUserThreadCount(int delta);
bool_t  sysThreadIsRunning(sys_thread_t *);
void    sysThreadProfSuspend(sys_thread_t *);
void    sysThreadProfResume(sys_thread_t *);
int     sysAdjustTimeSlice(int);
int     sysThreadEnumerateOver(int (*f)(sys_thread_t *, void *), void *arg);
void *  sysThreadInterruptEvent(void);
void *  sysThreadNativeID(sys_thread_t *);

size_t  sysMonitorSizeof(void);
int     sysMonitorInit(sys_mon_t *);
int     sysMonitorDestroy(sys_mon_t *);
int     sysMonitorEnter(sys_thread_t *, sys_mon_t *);
bool_t  sysMonitorEntered(sys_thread_t *, sys_mon_t *);
int     sysMonitorExit(sys_thread_t *, sys_mon_t *);
int     sysMonitorNotify(sys_thread_t *, sys_mon_t *);
int     sysMonitorNotifyAll(sys_thread_t *, sys_mon_t *);
int     sysMonitorWait(sys_thread_t *, sys_mon_t *, jlong);
bool_t  sysMonitorInUse(sys_mon_t *);
sys_thread_t * sysMonitorOwner(sys_mon_t *);
int     sysMonitorGetInfo(sys_mon_t *, sys_mon_info *);

char *sysNativePath(char *path);
int sysFileType(const char *path);
int sysOpen(const char *name, int openMode, int filePerm);
int sysClose(int fd);
jlong sysSeek(int fd, jlong offset, int whence);
int sysSetLength(int fd, jlong length);
int sysSync(int fd);
int sysAvailable(int fd, jlong *bytes);
size_t sysRead(int fd, void *buf, unsigned int nBytes);
size_t sysWrite(int fd, const void *buf, unsigned int nBytes);
int sysFileSizeFD(int fd, jlong *size);

int sysSocketClose(int fd);
int sysSocketShutdown(int fd, int howto);
long sysSocketAvailable(int fd, jint *pbytes);
int sysConnect(int fd, struct sockaddr *him, int len);
int sysBind(int fd, struct sockaddr *him, int len);
int sysAccept(int fd, struct sockaddr *him, int *len);
int sysGetSockName(int fd, struct sockaddr *him, int *len);
#ifdef _LP64
ssize_t sysSendTo(int fd, char *buf, int len, int flags, struct sockaddr *to,
              int tolen);
ssize_t sysRecvFrom(int fd, char *buf, int nbytes, int flags,
                struct sockaddr *from, int *fromlen);
ssize_t sysRecv(int fd, char *buf, int nBytes, int flags);
ssize_t sysSend(int fd, char *buf, int nBytes, int flags);
#else
int sysSendTo(int fd, char *buf, int len, int flags, struct sockaddr *to,
              int tolen);
int sysRecvFrom(int fd, char *buf, int nbytes, int flags,
                struct sockaddr *from, int *fromlen);
int sysRecv(int fd, char *buf, int nBytes, int flags);
int sysSend(int fd, char *buf, int nBytes, int flags);
#endif
int sysListen(int fd, int count);
int sysTimeout(int fd, long timeout);
int sysGetHostName(char* name, int namelen);
struct hostent *sysGetHostByAddr(const char* name, int len, int type);
struct hostent *sysGetHostByName(char *hostname);
int sysSocket(int domain, int type, int protocol);
int sysGetSockOpt(int fd, int level, int optname, char *optval, int *optlen);
int sysSetSockOpt(int fd, int level, int optname, const char *optval, int optlen);
struct protoent * sysGetProtoByName(char* name);

#define SYS_SIG_DFL HPI_SIG_DFL
#define SYS_SIG_ERR HPI_SIG_ERR
#define SYS_SIG_IGN HPI_SIG_IGN

#define SYS_OK         HPI_OK
#define SYS_ERR        HPI_ERR
#define SYS_INTRPT     HPI_INTRPT
#define SYS_TIMEOUT    HPI_TIMEOUT
#define SYS_NOMEM      HPI_NOMEM
#define SYS_NORESOURCE HPI_NORESOURCE

#define SYS_THREAD_RUNNABLE     HPI_THREAD_RUNNABLE
#define SYS_THREAD_MONITOR_WAIT HPI_THREAD_MONITOR_WAIT
#define SYS_THREAD_CONDVAR_WAIT HPI_THREAD_CONDVAR_WAIT

#define MinimumPriority     HPI_MINIMUM_PRIORITY
#define MaximumPriority     HPI_MAXIMUM_PRIORITY
#define NormalPriority      HPI_NORMAL_PRIORITY

#define SYS_THREAD_SUSPENDED HPI_THREAD_SUSPENDED

#define PAGE_ALIGNMENT      HPI_PAGE_ALIGNMENT

#define SYS_TIMEOUT_INFINITY HPI_TIMEOUT_INFINITY

#define SYS_FILETYPE_REGULAR      HPI_FILETYPE_REGULAR
#define SYS_FILETYPE_DIRECTORY    HPI_FILETYPE_DIRECTORY
#define SYS_FILETYPE_OTHER        HPI_FILETYPE_OTHER

typedef void *stackp_t;

/* global vars */

extern int logging_level;
extern bool_t profiler_on;

#ifdef __cplusplus
}
#endif

#endif /* !_JAVASOFT_HPI_IMPL_H_ */
