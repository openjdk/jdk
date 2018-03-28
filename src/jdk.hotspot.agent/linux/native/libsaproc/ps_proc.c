/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <errno.h>
#include <elf.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/ptrace.h>
#include <sys/uio.h>
#include "libproc_impl.h"

#if defined(x86_64) && !defined(amd64)
#define amd64 1
#endif

#ifndef __WALL
#define __WALL          0x40000000  // Copied from /usr/include/linux/wait.h
#endif

// This file has the libproc implementation specific to live process
// For core files, refer to ps_core.c

static inline uintptr_t align(uintptr_t ptr, size_t size) {
  return (ptr & ~(size - 1));
}

// ---------------------------------------------
// ptrace functions
// ---------------------------------------------

// read "size" bytes of data from "addr" within the target process.
// unlike the standard ptrace() function, process_read_data() can handle
// unaligned address - alignment check, if required, should be done
// before calling process_read_data.

static bool process_read_data(struct ps_prochandle* ph, uintptr_t addr, char *buf, size_t size) {
  long rslt;
  size_t i, words;
  uintptr_t end_addr = addr + size;
  uintptr_t aligned_addr = align(addr, sizeof(long));

  if (aligned_addr != addr) {
    char *ptr = (char *)&rslt;
    errno = 0;
    rslt = ptrace(PTRACE_PEEKDATA, ph->pid, aligned_addr, 0);
    if (errno) {
      print_debug("ptrace(PTRACE_PEEKDATA, ..) failed for %d bytes @ %lx\n", size, addr);
      return false;
    }
    for (; aligned_addr != addr; aligned_addr++, ptr++);
    for (; ((intptr_t)aligned_addr % sizeof(long)) && aligned_addr < end_addr;
        aligned_addr++)
       *(buf++) = *(ptr++);
  }

  words = (end_addr - aligned_addr) / sizeof(long);

  // assert((intptr_t)aligned_addr % sizeof(long) == 0);
  for (i = 0; i < words; i++) {
    errno = 0;
    rslt = ptrace(PTRACE_PEEKDATA, ph->pid, aligned_addr, 0);
    if (errno) {
      print_debug("ptrace(PTRACE_PEEKDATA, ..) failed for %d bytes @ %lx\n", size, addr);
      return false;
    }
    *(long *)buf = rslt;
    buf += sizeof(long);
    aligned_addr += sizeof(long);
  }

  if (aligned_addr != end_addr) {
    char *ptr = (char *)&rslt;
    errno = 0;
    rslt = ptrace(PTRACE_PEEKDATA, ph->pid, aligned_addr, 0);
    if (errno) {
      print_debug("ptrace(PTRACE_PEEKDATA, ..) failed for %d bytes @ %lx\n", size, addr);
      return false;
    }
    for (; aligned_addr != end_addr; aligned_addr++)
       *(buf++) = *(ptr++);
  }
  return true;
}

// null implementation for write
static bool process_write_data(struct ps_prochandle* ph,
                             uintptr_t addr, const char *buf , size_t size) {
  return false;
}

// "user" should be a pointer to a user_regs_struct
static bool process_get_lwp_regs(struct ps_prochandle* ph, pid_t pid, struct user_regs_struct *user) {
  // we have already attached to all thread 'pid's, just use ptrace call
  // to get regset now. Note that we don't cache regset upfront for processes.
// Linux on x86 and sparc are different.  On x86 ptrace(PTRACE_GETREGS, ...)
// uses pointer from 4th argument and ignores 3rd argument.  On sparc it uses
// pointer from 3rd argument and ignores 4th argument
#if defined(sparc) || defined(sparcv9)
#define ptrace_getregs(request, pid, addr, data) ptrace(request, pid, addr, data)
#else
#define ptrace_getregs(request, pid, addr, data) ptrace(request, pid, data, addr)
#endif

#if defined(_LP64) && defined(PTRACE_GETREGS64)
#define PTRACE_GETREGS_REQ PTRACE_GETREGS64
#elif defined(PTRACE_GETREGS)
#define PTRACE_GETREGS_REQ PTRACE_GETREGS
#elif defined(PT_GETREGS)
#define PTRACE_GETREGS_REQ PT_GETREGS
#endif

#ifdef PTRACE_GETREGS_REQ
 if (ptrace_getregs(PTRACE_GETREGS_REQ, pid, user, NULL) < 0) {
   print_debug("ptrace(PTRACE_GETREGS, ...) failed for lwp %d\n", pid);
   return false;
 }
 return true;
#elif defined(PTRACE_GETREGSET)
 struct iovec iov;
 iov.iov_base = user;
 iov.iov_len = sizeof(*user);
 if (ptrace(PTRACE_GETREGSET, pid, NT_PRSTATUS, (void*) &iov) < 0) {
   print_debug("ptrace(PTRACE_GETREGSET, ...) failed for lwp %d\n", pid);
   return false;
 }
 return true;
#else
 print_debug("ptrace(PTRACE_GETREGS, ...) not supported\n");
 return false;
#endif

}

static bool ptrace_continue(pid_t pid, int signal) {
  // pass the signal to the process so we don't swallow it
  if (ptrace(PTRACE_CONT, pid, NULL, signal) < 0) {
    print_debug("ptrace(PTRACE_CONT, ..) failed for %d\n", pid);
    return false;
  }
  return true;
}

// waits until the ATTACH has stopped the process
// by signal SIGSTOP
static bool ptrace_waitpid(pid_t pid) {
  int ret;
  int status;
  while (true) {
    // Wait for debuggee to stop.
    ret = waitpid(pid, &status, 0);
    if (ret == -1 && errno == ECHILD) {
      // try cloned process.
      ret = waitpid(pid, &status, __WALL);
    }
    if (ret >= 0) {
      if (WIFSTOPPED(status)) {
        // Any signal will stop the thread, make sure it is SIGSTOP. Otherwise SIGSTOP
        // will still be pending and delivered when the process is DETACHED and the process
        // will go to sleep.
        if (WSTOPSIG(status) == SIGSTOP) {
          // Debuggee stopped by SIGSTOP.
          return true;
        }
        if (!ptrace_continue(pid, WSTOPSIG(status))) {
          print_error("Failed to correctly attach to VM. VM might HANG! [PTRACE_CONT failed, stopped by %d]\n", WSTOPSIG(status));
          return false;
        }
      } else {
        print_debug("waitpid(): Child process exited/terminated (status = 0x%x)\n", status);
        return false;
      }
    } else {
      switch (errno) {
        case EINTR:
          continue;
          break;
        case ECHILD:
          print_debug("waitpid() failed. Child process pid (%d) does not exist \n", pid);
          break;
        case EINVAL:
          print_debug("waitpid() failed. Invalid options argument.\n");
          break;
        default:
          print_debug("waitpid() failed. Unexpected error %d\n",errno);
          break;
      }
      return false;
    }
  }
}

// attach to a process/thread specified by "pid"
static bool ptrace_attach(pid_t pid, char* err_buf, size_t err_buf_len) {
  if (ptrace(PTRACE_ATTACH, pid, NULL, NULL) < 0) {
    char buf[200];
    char* msg = strerror_r(errno, buf, sizeof(buf));
    snprintf(err_buf, err_buf_len, "ptrace(PTRACE_ATTACH, ..) failed for %d: %s", pid, msg);
    print_debug("%s\n", err_buf);
    return false;
  } else {
    return ptrace_waitpid(pid);
  }
}

// -------------------------------------------------------
// functions for obtaining library information
// -------------------------------------------------------

/*
 * splits a string _str_ into substrings with delimiter _delim_ by replacing old * delimiters with _new_delim_ (ideally, '\0'). the address of each substring
 * is stored in array _ptrs_ as the return value. the maximum capacity of _ptrs_ * array is specified by parameter _n_.
 * RETURN VALUE: total number of substrings (always <= _n_)
 * NOTE: string _str_ is modified if _delim_!=_new_delim_
 */
static int split_n_str(char * str, int n, char ** ptrs, char delim, char new_delim)
{
   int i;
   for(i = 0; i < n; i++) ptrs[i] = NULL;
   if (str == NULL || n < 1 ) return 0;

   i = 0;

   // skipping leading blanks
   while(*str&&*str==delim) str++;

   while(*str&&i<n){
     ptrs[i++] = str;
     while(*str&&*str!=delim) str++;
     while(*str&&*str==delim) *(str++) = new_delim;
   }

   return i;
}

/*
 * fgets without storing '\n' at the end of the string
 */
static char * fgets_no_cr(char * buf, int n, FILE *fp)
{
   char * rslt = fgets(buf, n, fp);
   if (rslt && buf && *buf){
       char *p = strchr(buf, '\0');
       if (*--p=='\n') *p='\0';
   }
   return rslt;
}

// callback for read_thread_info
static bool add_new_thread(struct ps_prochandle* ph, pthread_t pthread_id, lwpid_t lwp_id) {
  return add_thread_info(ph, pthread_id, lwp_id) != NULL;
}

static bool read_lib_info(struct ps_prochandle* ph) {
  char fname[32];
  char buf[PATH_MAX];
  FILE *fp = NULL;

  sprintf(fname, "/proc/%d/maps", ph->pid);
  fp = fopen(fname, "r");
  if (fp == NULL) {
    print_debug("can't open /proc/%d/maps file\n", ph->pid);
    return false;
  }

  while(fgets_no_cr(buf, PATH_MAX, fp)){
    char * word[7];
    int nwords = split_n_str(buf, 7, word, ' ', '\0');

    if (nwords < 6) {
      // not a shared library entry. ignore.
      continue;
    }

    // SA does not handle the lines with patterns:
    //   "[stack]", "[heap]", "[vdso]", "[vsyscall]", etc.
    if (word[5][0] == '[') {
        // not a shared library entry. ignore.
        continue;
    }

    if (nwords > 6) {
      // prelink altered mapfile when the program is running.
      // Entries like one below have to be skipped
      //  /lib64/libc-2.15.so (deleted)
      // SO name in entries like one below have to be stripped.
      //  /lib64/libpthread-2.15.so.#prelink#.EECVts
      char *s = strstr(word[5],".#prelink#");
      if (s == NULL) {
        // No prelink keyword. skip deleted library
        print_debug("skip shared object %s deleted by prelink\n", word[5]);
        continue;
      }

      // Fall through
      print_debug("rectifying shared object name %s changed by prelink\n", word[5]);
      *s = 0;
    }

    if (find_lib(ph, word[5]) == false) {
       intptr_t base;
       lib_info* lib;
#ifdef _LP64
       sscanf(word[0], "%lx", &base);
#else
       sscanf(word[0], "%x", &base);
#endif
       if ((lib = add_lib_info(ph, word[5], (uintptr_t)base)) == NULL)
          continue; // ignore, add_lib_info prints error

       // we don't need to keep the library open, symtab is already
       // built. Only for core dump we need to keep the fd open.
       close(lib->fd);
       lib->fd = -1;
    }
  }
  fclose(fp);
  return true;
}

// detach a given pid
static bool ptrace_detach(pid_t pid) {
  if (pid && ptrace(PTRACE_DETACH, pid, NULL, NULL) < 0) {
    print_debug("ptrace(PTRACE_DETACH, ..) failed for %d\n", pid);
    return false;
  } else {
    return true;
  }
}

// detach all pids of a ps_prochandle
static void detach_all_pids(struct ps_prochandle* ph) {
  thread_info* thr = ph->threads;
  while (thr) {
     ptrace_detach(thr->lwp_id);
     thr = thr->next;
  }
}

static void process_cleanup(struct ps_prochandle* ph) {
  detach_all_pids(ph);
}

static ps_prochandle_ops process_ops = {
  .release=  process_cleanup,
  .p_pread=  process_read_data,
  .p_pwrite= process_write_data,
  .get_lwp_regs= process_get_lwp_regs
};

// attach to the process. One and only one exposed stuff
JNIEXPORT struct ps_prochandle* JNICALL
Pgrab(pid_t pid, char* err_buf, size_t err_buf_len) {
  struct ps_prochandle* ph = NULL;
  thread_info* thr = NULL;

  if ( (ph = (struct ps_prochandle*) calloc(1, sizeof(struct ps_prochandle))) == NULL) {
     snprintf(err_buf, err_buf_len, "can't allocate memory for ps_prochandle");
     print_debug("%s\n", err_buf);
     return NULL;
  }

  if (ptrace_attach(pid, err_buf, err_buf_len) != true) {
     free(ph);
     return NULL;
  }

  // initialize ps_prochandle
  ph->pid = pid;

  // initialize vtable
  ph->ops = &process_ops;

  // read library info and symbol tables, must do this before attaching threads,
  // as the symbols in the pthread library will be used to figure out
  // the list of threads within the same process.
  read_lib_info(ph);

  // read thread info
  read_thread_info(ph, add_new_thread);

  // attach to the threads
  thr = ph->threads;
  while (thr) {
     // don't attach to the main thread again
    if (ph->pid != thr->lwp_id && ptrace_attach(thr->lwp_id, err_buf, err_buf_len) != true) {
        // even if one attach fails, we get return NULL
        Prelease(ph);
        return NULL;
     }
     thr = thr->next;
  }
  return ph;
}
