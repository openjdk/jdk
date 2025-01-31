/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/sysctl.h>
#include <sys/wait.h>
#include <sys/ptrace.h>
#include <sys/param.h>
#include <sys/user.h>
#include <elf.h>
#include <sys/elf_common.h>
#include <sys/link_elf.h>
#include <libutil.h>
#include "libproc_impl.h"
#include "elfmacros.h"

// This file has the libproc implementation specific to live process
// For core files, refer to ps_core.c

typedef enum {
  ATTACH_SUCCESS,
  ATTACH_FAIL,
  ATTACH_THREAD_DEAD
} attach_state_t;

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
  int rslt;
  size_t i, words;
  uintptr_t end_addr = addr + size;
  uintptr_t aligned_addr = align(addr, sizeof(int));

  if (aligned_addr != addr) {
    char *ptr = (char *)&rslt;
    errno = 0;
    rslt = ptrace(PT_READ_D, ph->pid, (caddr_t) aligned_addr, 0);
    if (errno) {
      print_debug("ptrace(PT_READ_D, ..) failed for %d bytes @ %lx\n", size, addr);
      return false;
    }
    for (; aligned_addr != addr; aligned_addr++, ptr++);
    for (; ((intptr_t)aligned_addr % sizeof(int)) && aligned_addr < end_addr;
        aligned_addr++)
       *(buf++) = *(ptr++);
  }

  words = (end_addr - aligned_addr) / sizeof(int);

  // assert((intptr_t)aligned_addr % sizeof(int) == 0);
  for (i = 0; i < words; i++) {
    errno = 0;
    rslt = ptrace(PT_READ_D, ph->pid, (caddr_t) aligned_addr, 0);
    if (errno) {
      print_debug("ptrace(PT_READ_D, ..) failed for %d bytes @ %lx\n", size, addr);
      return false;
    }
    *(int *)buf = rslt;
    buf += sizeof(int);
    aligned_addr += sizeof(int);
  }

  if (aligned_addr != end_addr) {
    char *ptr = (char *)&rslt;
    errno = 0;
    rslt = ptrace(PT_READ_D, ph->pid, (caddr_t) aligned_addr, 0);
    if (errno) {
      print_debug("ptrace(PT_READ_D, ..) failed for %d bytes @ %lx\n", size, addr);
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

// "user" should be a pointer to a reg
static bool process_get_lwp_regs(struct ps_prochandle* ph, lwpid_t lwpid, struct reg *user) {
  // we have already attached to all thread 'pid's, just use ptrace call
  // to get regset now. Note that we don't cache regset upfront for processes.
 if (ptrace(PT_GETREGS, ph->pid, (caddr_t) user, 0) < 0) {
   print_debug("ptrace(PTRACE_GETREGS, ...) failed for lwp %d (%d)\n", lwpid, ph->pid);
   return false;
 }
 return true;
}

// fill in ptrace_lwpinfo for lid
static bool process_get_lwp_info(struct ps_prochandle *ph, lwpid_t lwp_id, void *linfo) {
  errno = 0;
  ptrace(PT_LWPINFO, lwp_id, linfo, sizeof(struct ptrace_lwpinfo));

  return (errno == 0)? true: false;
}

static bool ptrace_continue(pid_t pid, int signal) {
  // pass the signal to the process so we don't swallow it
  if (ptrace(PT_CONTINUE, pid, NULL, signal) < 0) {
    print_debug("ptrace(PTRACE_CONT, ..) failed for %d\n", pid);
    return false;
  }
  return true;
}

// waits until the ATTACH has stopped the process
// by signal SIGSTOP
static attach_state_t ptrace_waitpid(pid_t pid) {
  int ret;
  int status;
  errno = 0;
  while (true) {
    // Wait for debuggee to stop.
    ret = waitpid(pid, &status, 0);
    if (ret >= 0) {
      if (WIFSTOPPED(status)) {
        // Any signal will stop the thread, make sure it is SIGSTOP. Otherwise SIGSTOP
        // will still be pending and delivered when the process is DETACHED and the process
        // will go to sleep.
        if (WSTOPSIG(status) == SIGSTOP) {
          // Debuggee stopped by SIGSTOP.
          return ATTACH_SUCCESS;
        }
        if (!ptrace_continue(pid, WSTOPSIG(status))) {
          print_error("Failed to correctly attach to VM. VM might HANG! [PTRACE_CONT failed, stopped by %d]\n", WSTOPSIG(status));
          return ATTACH_FAIL;
        }
      } else {
        print_debug("waitpid(): Child process %d exited/terminated (status = 0x%x)\n", pid, status);
        return ATTACH_THREAD_DEAD;
      }
    } else {
      switch (errno) {
        case EINTR:
          continue;
          break;
        case ECHILD:
          print_debug("waitpid() failed. Child process pid (%d) does not exist \n", pid);
          return ATTACH_THREAD_DEAD;
        case EINVAL:
          print_error("waitpid() failed. Invalid options argument.\n");
          return ATTACH_FAIL;
        default:
          print_error("waitpid() failed. Unexpected error %d\n",errno);
          return ATTACH_FAIL;
      }
    } //else
  } // while
}

static bool process_doesnt_exist(pid_t pid) {
#if defined(__FreeBSD__)
  struct kinfo_proc kproc;
  size_t klen;
  int mib[] = { CTL_KERN, KERN_PROC, KERN_PROC_PID, pid };
  if (sysctl(mib, 4, &kproc, &klen, NULL, 0) == -1) {
    return true;
  }

  switch (kproc.ki_stat) {
    case SZOMB:
      return true;
    default:
      return false;
  }
#endif
  return true;
}

// attach to a process/thread specified by "pid"
static attach_state_t ptrace_attach(pid_t pid, char* err_buf, size_t err_buf_len) {
  errno = 0;
  if (ptrace(PT_ATTACH, pid, NULL, 0) < 0) {
    if (errno == EPERM || errno == ESRCH) {
      // Check if the process/thread is exiting or is a zombie
      if (process_doesnt_exist(pid)) {
        print_debug("Thread with pid %d does not exist\n", pid);
        return ATTACH_THREAD_DEAD;
      }
    }
    char buf[200];
    strerror_r(errno, buf, sizeof(buf));
    snprintf(err_buf, err_buf_len, "ptrace(PTRACE_ATTACH, ..) failed for %d: %s", pid, buf);
    print_error("%s\n", err_buf);
    return ATTACH_FAIL;
  } else {
    attach_state_t wait_ret = ptrace_waitpid(pid);
    if (wait_ret == ATTACH_THREAD_DEAD) {
      print_debug("Thread with pid %d does not exist\n", pid);
    }
    return wait_ret;
  }
}

// -------------------------------------------------------
// functions for obtaining library information
// -------------------------------------------------------

// callback for read_thread_info
static bool add_new_thread(struct ps_prochandle* ph, pthread_t pthread_id, lwpid_t lwp_id) {
  return add_thread_info(ph, pthread_id, lwp_id) != NULL;
}

static bool read_lib_info(struct ps_prochandle* ph) {
#if defined(__FreeBSD__)
  struct kinfo_vmentry *freep, *kve;
  int i, cnt;

  freep = kinfo_getvmmap(ph->pid, &cnt);
  if (freep == NULL) {
      print_debug("can't get vm map for pid\n", ph->pid);
      return false;
  }

  for (i = 0; i < cnt; i++) {
    kve = &freep[i];
    if ((kve->kve_flags & KVME_FLAG_COW) &&
        strnlen(kve->kve_path, PATH_MAX) > 0) {

      if (find_lib(ph, kve->kve_path) == false) {
        lib_info* lib;
        if ((lib = add_lib_info(ph, kve->kve_path,
                                (uintptr_t) kve->kve_start)) == NULL)
          continue; // ignore, add_lib_info prints error

        // we don't need to keep the library open, symtab is already
        // built. Only for core dump we need to keep the fd open.
        close(lib->fd);
        lib->fd = -1;
      }
    }
  }

  free(freep);

  return true;
#else
  char *l_name;
  struct link_map *lmap;
  uintptr_t lmap_addr;

  if ((l_name = malloc(BUF_SIZE)) == NULL)
    return false;

  if ((lmap = malloc(sizeof(*lmap))) == NULL) {
    free(l_name);
    return false;
  }

  lmap_addr = linkmap_addr(ph);

  if (lmap_addr == 0) {
    free(l_name);
    free(lmap);
    return false;
  }

  do {
    if (process_read_data(ph, lmap_addr, (char *)lmap, sizeof(*lmap)) != true) {
      print_debug("process_read_data failed for lmap_addr %p\n", lmap_addr);
      free (l_name);
      free (lmap);
      return false;
    }

    if (process_read_data(ph, (uintptr_t)lmap->l_name, l_name,
        BUF_SIZE) != true) {
      print_debug("process_read_data failed for lmap->l_name %p\n",
          lmap->l_name);
      free (l_name);
      free (lmap);
      return false;
    }

    if (find_lib(ph, l_name) == false) {
      lib_info* lib;
      if ((lib = add_lib_info(ph, l_name,
                              (uintptr_t) lmap->l_addr)) == NULL)
        continue; // ignore, add_lib_info prints error

      // we don't need to keep the library open, symtab is already
      // built. Only for core dump we need to keep the fd open.
      close(lib->fd);
      lib->fd = -1;
    }
    lmap_addr = (uintptr_t)lmap->l_next;
  } while (lmap->l_next != NULL);

  free (l_name);
  free (lmap);

  return true;
#endif
}

// detach a given pid
static bool ptrace_detach(pid_t pid) {
  if (pid && ptrace(PT_DETACH, pid, (caddr_t)1, 0) < 0) {
    print_debug("ptrace(PTRACE_DETACH, ..) failed for %d\n", pid);
    return false;
  } else {
    return true;
  }
}

static void process_cleanup(struct ps_prochandle* ph) {
  ptrace_detach(ph->pid);
}

static ps_prochandle_ops process_ops = {
  .release=  process_cleanup,
  .p_pread=  process_read_data,
  .p_pwrite= process_write_data,
  .get_lwp_regs= process_get_lwp_regs,
  .get_lwp_info= process_get_lwp_info
};

// attach to the process. One and only one exposed stuff
struct ps_prochandle* Pgrab(pid_t pid, char* err_buf, size_t err_buf_len) {
  struct ps_prochandle* ph = NULL;
  attach_state_t attach_status = ATTACH_SUCCESS;

  if ( (ph = (struct ps_prochandle*) calloc(1, sizeof(struct ps_prochandle))) == NULL) {
    print_debug("can't allocate memory for ps_prochandle\n");
    return NULL;
  }

  if ((attach_status = ptrace_attach(pid, err_buf, err_buf_len)) != ATTACH_SUCCESS) {
    if (attach_status == ATTACH_THREAD_DEAD) {
       print_error("The process with pid %d does not exist.\n", pid);
    }
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
  if (read_lib_info(ph) != true) {
     ptrace_detach(pid);
     free(ph);
     return NULL;
  }

  // read thread info
  read_thread_info(ph, add_new_thread);

  return ph;
}
