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
static bool process_get_lwp_regs(struct ps_prochandle* ph, pid_t pid, struct reg *user) {
  // we have already attached to all thread 'pid's, just use ptrace call
  // to get regset now. Note that we don't cache regset upfront for processes.
 if (ptrace(PT_GETREGS, pid, (caddr_t) user, 0) < 0) {
   print_debug("ptrace(PTRACE_GETREGS, ...) failed for lwp %d\n", pid);
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
static bool ptrace_waitpid(pid_t pid) {
  int ret;
  int status;
  do {
    // Wait for debuggee to stop.
    ret = waitpid(pid, &status, 0);
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
      }
      return false;
    }
  } while(true);
}

// attach to a process/thread specified by "pid"
static bool ptrace_attach(pid_t pid) {
  if (ptrace(PT_ATTACH, pid, NULL, 0) < 0) {
    print_debug("ptrace(PTRACE_ATTACH, ..) failed for %d\n", pid);
    return false;
  } else {
    return ptrace_waitpid(pid);
  }
}

// -------------------------------------------------------
// functions for obtaining library information
// -------------------------------------------------------

// callback for read_thread_info
static bool add_new_thread(struct ps_prochandle* ph, pthread_t pthread_id, lwpid_t lwp_id) {
  return add_thread_info(ph, pthread_id, lwp_id) != NULL;
}

#if defined(__FreeBSD__) && __FreeBSD_version < 701000
/*
 * TEXT_START_ADDR from binutils/ld/emulparams/<arch_spec>.sh
 * Not the most robust but good enough.
 */

#if defined(amd64) || defined(x86_64)
#define TEXT_START_ADDR 0x400000
#elif defined(i386)
#define TEXT_START_ADDR 0x8048000
#else
#error TEXT_START_ADDR not defined
#endif

#define BUF_SIZE (PATH_MAX + NAME_MAX + 1)

uintptr_t linkmap_addr(struct ps_prochandle *ph) {
  uintptr_t ehdr_addr, phdr_addr, dyn_addr, dmap_addr, lmap_addr;
  ELF_EHDR ehdr;
  ELF_PHDR *phdrs, *phdr;
  ELF_DYN *dyns, *dyn;
  struct r_debug dmap;
  unsigned long hdrs_size;
  unsigned int i;

  /* read ELF_EHDR at TEXT_START_ADDR and validate */

  ehdr_addr = (uintptr_t)TEXT_START_ADDR;

  if (process_read_data(ph, ehdr_addr, (char *)&ehdr, sizeof(ehdr)) != true) {
    print_debug("process_read_data failed for ehdr_addr %p\n", ehdr_addr);
    return (0);
  }

  if (!IS_ELF(ehdr) ||
        ehdr.e_ident[EI_CLASS] != ELF_TARG_CLASS ||
        ehdr.e_ident[EI_DATA] != ELF_TARG_DATA ||
        ehdr.e_ident[EI_VERSION] != EV_CURRENT ||
        ehdr.e_phentsize != sizeof(ELF_PHDR) ||
        ehdr.e_version != ELF_TARG_VER ||
        ehdr.e_machine != ELF_TARG_MACH) {
    print_debug("not an ELF_EHDR at %p\n", ehdr_addr);
    return (0);
  }

  /* allocate space for all ELF_PHDR's and read */

  phdr_addr = ehdr_addr + ehdr.e_phoff;
  hdrs_size = ehdr.e_phnum * sizeof(ELF_PHDR);

  if ((phdrs = malloc(hdrs_size)) == NULL)
    return (0);

  if (process_read_data(ph, phdr_addr, (char *)phdrs, hdrs_size) != true) {
    print_debug("process_read_data failed for phdr_addr %p\n", phdr_addr);
    return (0);
  }

  /* find PT_DYNAMIC section */

  for (i = 0, phdr = phdrs; i < ehdr.e_phnum; i++, phdr++) {
    if (phdr->p_type == PT_DYNAMIC)
      break;
  }

  if (i >= ehdr.e_phnum) {
    print_debug("PT_DYNAMIC section not found!\n");
    free(phdrs);
    return (0);
  }

  /* allocate space and read in ELF_DYN headers */

  dyn_addr = phdr->p_vaddr;
  hdrs_size = phdr->p_memsz;
  free(phdrs);

  if ((dyns = malloc(hdrs_size)) == NULL)
    return (0);

  if (process_read_data(ph, dyn_addr, (char *)dyns, hdrs_size) != true) {
    print_debug("process_read_data failed for dyn_addr %p\n", dyn_addr);
    free(dyns);
    return (0);
  }

  /* find DT_DEBUG */

  dyn = dyns;
  while (dyn->d_tag != DT_DEBUG && dyn->d_tag != DT_NULL) {
    dyn++;
  }

  if (dyn->d_tag != DT_DEBUG) {
    print_debug("failed to find DT_DEBUG\n");
    free(dyns);
    return (0);
  }

  /* read struct r_debug into dmap */

  dmap_addr = (uintptr_t)dyn->d_un.d_ptr;
  free(dyns);

  if (process_read_data(ph, dmap_addr, (char *)&dmap, sizeof(dmap)) != true) {
    print_debug("process_read_data failed for dmap_addr %p\n", dmap_addr);
    return (0);
  }

  lmap_addr = (uintptr_t)dmap.r_map;

  return (lmap_addr);
}
#endif // __FreeBSD__ && __FreeBSD_version < 701000

static bool read_lib_info(struct ps_prochandle* ph) {
#if defined(__FreeBSD__) && __FreeBSD_version >= 701000
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
        kve->kve_path != NULL &&
        strlen(kve->kve_path) > 0) {

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
struct ps_prochandle* Pgrab(pid_t pid) {
  struct ps_prochandle* ph = NULL;

  if ( (ph = (struct ps_prochandle*) calloc(1, sizeof(struct ps_prochandle))) == NULL) {
     print_debug("can't allocate memory for ps_prochandle\n");
     return NULL;
  }

  if (ptrace_attach(pid) != true) {
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
