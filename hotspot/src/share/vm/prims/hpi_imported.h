/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

/*
 * HotSpot integration note:
 *
 * This is a consolidation of these two files:
 *      src/share/hpi/export/hpi.h      1.15  99/06/18  JDK1.3 beta build I
 *      src/share/hpi/export/dll.h      1.3   98/09/15  JDK1.3 beta build I
 * from the classic VM.
 *
 * bool_t is a type in the classic VM, and we define it here,
 * but in the future this should be a jboolean.
 *
 * The files are included verbatim expect for local includes removed from hpi.h.
 */

#ifndef _JAVASOFT_HPI_H_
#define _JAVASOFT_HPI_H_

#ifdef __cplusplus
extern "C" {
#endif

/* A classic VMism that should become a jboolean.  Fix in 1.2.1? */
typedef enum { HPI_FALSE = 0, HPI_TRUE = 1 } bool_t;

/*
 * DLL.H: A common interface for helper DLLs loaded by the VM.
 * Each library exports the main entry point "DLL_Initialize". Through
 * that function the programmer can obtain a function pointer which has
 * type "GetInterfaceFunc." Through the function pointer the programmer
 * can obtain other interfaces supported in the DLL.
 */
typedef jint (JNICALL * GetInterfaceFunc)
       (void **intfP, const char *name, jint ver);

jint JNICALL DLL_Initialize(GetInterfaceFunc *, void *args);


/*
 * Host Porting Interface. This defines the "porting layer" for
 * POSIX.1 compliant operating systems.
 */

/*
 * memory allocations
 */
typedef struct {
    /*
     * Malloc must return a unique pointer if size == 0.
     */
  void *  (*Malloc)(size_t size);
  void *  (*Realloc)(void *ptr, size_t new_size);
    /*
     * Free must allow ptr == NULL to be a no-op.
     */
  void    (*Free)(void *ptr);
    /*
     * Calloc must return a unique pointer for if
     * n_item == 0 || item_size == 0.
     */
  void *  (*Calloc)(size_t n_item, size_t item_size);
  char *  (*Strdup)(const char *str);

  void *  (*MapMem)(size_t req_size, size_t *maped_size);
  void *  (*UnmapMem)(void *req_addr, size_t req_size, size_t *unmap_size);
  /*
   * CommitMem should round the ptr down to the nearest page and
   * round the size up to the nearest page so that the committed
   * region is at least as large as the requested region.
   */
  void *  (*CommitMem)(void *ptr, size_t size, size_t *actual);
  /*
   * sysDecommitMem should round the ptr up to the nearest page and
   * round the size down to the nearest page so that the decommitted
   * region is no greater than the requested region.
   */
  void *  (*DecommitMem)(void *ptr, size_t size, size_t *actual);

#define HPI_PAGE_ALIGNMENT          (64 * 1024)

  void *  (*AllocBlock)(size_t size, void **headP);
  void    (*FreeBlock)(void *head);
} HPI_MemoryInterface;

/*
 * dynamic linking libraries
 */
typedef struct {
  void   (*BuildLibName)(char *buf, int buf_len, char *path, const char *name);
  int    (*BuildFunName)(char *name, int name_len, int arg_size, int en_idx);

  void * (*LoadLibrary)(const char *name, char *err_buf, int err_buflen);
  void   (*UnloadLibrary)(void *lib);
  void * (*FindLibraryEntry)(void *lib, const char *name);
} HPI_LibraryInterface;

typedef void (*signal_handler_t)(int sig, void *siginfo, void *context);

#define HPI_SIG_DFL (signal_handler_t)0
#define HPI_SIG_ERR (signal_handler_t)-1
#define HPI_SIG_IGN (signal_handler_t)1

typedef struct {
  char *name; /* name such as green/native threads. */
  int  isMP;
} HPI_SysInfo;

typedef struct {
  HPI_SysInfo *    (*GetSysInfo)(void);
  long             (*GetMilliTicks)(void);
  jlong            (*TimeMillis)(void);

  signal_handler_t (*Signal)(int sig, signal_handler_t handler);
  void             (*Raise)(int sig);
  void             (*SignalNotify)(int sig);
  int              (*SignalWait)(void);

  int              (*Shutdown)(void);

  int              (*SetLoggingLevel)(int level);
  bool_t           (*SetMonitoringOn)(bool_t on);
  int              (*GetLastErrorString)(char *buf, int len);
} HPI_SystemInterface;

/*
 * threads and monitors
 */
typedef struct  sys_thread sys_thread_t;
typedef struct  sys_mon sys_mon_t;

#define HPI_OK          0
#define HPI_ERR        -1
#define HPI_INTRPT     -2    /* Operation was interrupted */
#define HPI_TIMEOUT    -3    /* A timer ran out */
#define HPI_NOMEM      -5    /* Ran out of memory */
#define HPI_NORESOURCE -6    /* Ran out of some system resource */

/* There are three basic states: RUNNABLE, MONITOR_WAIT, and CONDVAR_WAIT.
 * When the thread is suspended in any of these states, the
 * HPI_THREAD_SUSPENDED bit will be set
 */
enum {
    HPI_THREAD_RUNNABLE = 1,
    HPI_THREAD_MONITOR_WAIT,
    HPI_THREAD_CONDVAR_WAIT
};

#define HPI_MINIMUM_PRIORITY        1
#define HPI_MAXIMUM_PRIORITY        10
#define HPI_NORMAL_PRIORITY         5

#define HPI_THREAD_SUSPENDED        0x8000
#define HPI_THREAD_INTERRUPTED      0x4000

typedef struct {
    sys_thread_t *owner;
    int          entry_count;
    sys_thread_t **monitor_waiters;
    sys_thread_t **condvar_waiters;
    int          sz_monitor_waiters;
    int          sz_condvar_waiters;
    int          n_monitor_waiters;
    int          n_condvar_waiters;
} sys_mon_info;

typedef struct {
  int            (*ThreadBootstrap)(sys_thread_t **tidP,
                                    sys_mon_t **qlockP,
                                    int nReservedBytes);
  int            (*ThreadCreate)(sys_thread_t **tidP,
                                 long stk_size,
                                 void (*func)(void *),
                                 void *arg);
  sys_thread_t * (*ThreadSelf)(void);
  void           (*ThreadYield)(void);
  int            (*ThreadSuspend)(sys_thread_t *tid);
  int            (*ThreadResume)(sys_thread_t *tid);
  int            (*ThreadSetPriority)(sys_thread_t *tid, int prio);
  int            (*ThreadGetPriority)(sys_thread_t *tid, int *prio);
  void *         (*ThreadStackPointer)(sys_thread_t *tid);
  void *         (*ThreadStackTop)(sys_thread_t *tid);
  long *         (*ThreadRegs)(sys_thread_t *tid, int *regs);
  int            (*ThreadSingle)(void);
  void           (*ThreadMulti)(void);
  int            (*ThreadEnumerateOver)(int (*func)(sys_thread_t *, void *),
                                        void *arg);
  int            (*ThreadCheckStack)(void);
  void           (*ThreadPostException)(sys_thread_t *tid, void *arg);
  void           (*ThreadInterrupt)(sys_thread_t *tid);
  int            (*ThreadIsInterrupted)(sys_thread_t *tid, int clear);
  int            (*ThreadAlloc)(sys_thread_t **tidP);
  int            (*ThreadFree)(void);
  jlong          (*ThreadCPUTime)(void);
  int            (*ThreadGetStatus)(sys_thread_t *tid, sys_mon_t **monitor);
  void *         (*ThreadInterruptEvent)(void);
  void *         (*ThreadNativeID)(sys_thread_t *tid);

  /* These three functions are used by the CPU time profiler.
   * sysThreadIsRunning determines whether the thread is running (not just
   * runnable). It is only safe to call this function after calling
   * sysThreadProfSuspend.
   */
  bool_t         (*ThreadIsRunning)(sys_thread_t *tid);
  void           (*ThreadProfSuspend)(sys_thread_t *tid);
  void           (*ThreadProfResume)(sys_thread_t *tid);

  int            (*AdjustTimeSlice)(int ms);

  size_t         (*MonitorSizeof)(void);
  int            (*MonitorInit)(sys_mon_t *mid);
  int            (*MonitorDestroy)(sys_mon_t *mid);
  int            (*MonitorEnter)(sys_thread_t *self, sys_mon_t *mid);
  bool_t         (*MonitorEntered)(sys_thread_t *self, sys_mon_t *mid);
  int            (*MonitorExit)(sys_thread_t *self, sys_mon_t *mid);
  int            (*MonitorNotify)(sys_thread_t *self, sys_mon_t *mid);
  int            (*MonitorNotifyAll)(sys_thread_t *self, sys_mon_t *mid);
  int            (*MonitorWait)(sys_thread_t *self, sys_mon_t *mid, jlong ms);
  bool_t         (*MonitorInUse)(sys_mon_t *mid);
  sys_thread_t * (*MonitorOwner)(sys_mon_t *mid);
  int            (*MonitorGetInfo)(sys_mon_t *mid, sys_mon_info *info);

} HPI_ThreadInterface;

/*
 * files
 */

#define HPI_FILETYPE_REGULAR    (0)
#define HPI_FILETYPE_DIRECTORY  (1)
#define HPI_FILETYPE_OTHER      (2)

typedef struct {
  char *         (*NativePath)(char *path);
  int            (*FileType)(const char *path);
  int            (*Open)(const char *name, int openMode, int filePerm);
  int            (*Close)(int fd);
  jlong          (*Seek)(int fd, jlong offset, int whence);
  int            (*SetLength)(int fd, jlong length);
  int            (*Sync)(int fd);
  int            (*Available)(int fd, jlong *bytes);
  size_t         (*Read)(int fd, void *buf, unsigned int nBytes);
  size_t         (*Write)(int fd, const void *buf, unsigned int nBytes);
  int            (*FileSizeFD)(int fd, jlong *size);
} HPI_FileInterface;

/*
 * sockets
 */
struct sockaddr;
struct hostent;

typedef struct {
  int              (*Close)(int fd);
  long             (*Available)(int fd, jint *pbytes);
  int              (*Connect)(int fd, struct sockaddr *him, int len);
  int              (*Accept)(int fd, struct sockaddr *him, int *len);
  int              (*SendTo)(int fd, char *buf, int len, int flags,
                             struct sockaddr *to, int tolen);
  int              (*RecvFrom)(int fd, char *buf, int nbytes, int flags,
                               struct sockaddr *from, int *fromlen);
  int              (*Listen)(int fd, long count);
  int              (*Recv)(int fd, char *buf, int nBytes, int flags);
  int              (*Send)(int fd, char *buf, int nBytes, int flags);
  int              (*Timeout)(int fd, long timeout);
  struct hostent * (*GetHostByName)(char *hostname);
  int              (*Socket)(int domain, int type, int protocol);
  int              (*SocketShutdown)(int fd, int howto);
  int              (*Bind)(int fd, struct sockaddr *him, int len);
  int              (*GetSocketName)(int fd, struct sockaddr *him, int *len);
  int              (*GetHostName)(char *hostname, int namelen);
  struct hostent * (*GetHostByAddr)(const char *hostname, int len, int type);
  int              (*SocketGetOption)(int fd, int level, int optname, char *optval, int *optlen);
  int              (*SocketSetOption)(int fd, int level, int optname, const char *optval, int optlen);
  struct protoent * (*GetProtoByName)(char* name);
} HPI_SocketInterface;

/*
 * callbacks.
 */
typedef struct vm_calls {
    int    (*jio_fprintf)(FILE *fp, const char *fmt, ...);
    void   (*panic)(const char *fmt, ...);
    void   (*monitorRegister)(sys_mon_t *mid, char *info_str);

    void   (*monitorContendedEnter)(sys_thread_t *self, sys_mon_t *mid);
    void   (*monitorContendedEntered)(sys_thread_t *self, sys_mon_t *mid);
    void   (*monitorContendedExit)(sys_thread_t *self, sys_mon_t *mid);
} vm_calls_t;

#ifdef __cplusplus
}
#endif

#endif /* !_JAVASOFT_HPI_H_ */
