/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Azul Systems, Inc. All rights reserved.
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

#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <stdlib.h>
#include <stddef.h>
#include <sys/errno.h>
#include "libproc_impl.h"
#include "ps_core_common.h"

#if defined(amd64)
#include "sun_jvm_hotspot_debugger_amd64_AMD64ThreadContext.h"
#elif defined(aarch64)
#include "sun_jvm_hotspot_debugger_aarch64_AARCH64ThreadContext.h"
#else
#error UNSUPPORTED_ARCH
#endif

// This file has the libproc implementation to read core files.
// For live processes, refer to ps_proc.c. Portions of this is adapted
// /modelled after Solaris libproc.so (in particular Pcore.c)

//---------------------------------------------------------------------------
// functions to handle map_info

// Order mappings based on virtual address.  We use this function as the
// callback for sorting the array of map_info pointers.
static int core_cmp_mapping(const void *lhsp, const void *rhsp)
{
  const map_info *lhs = *((const map_info **)lhsp);
  const map_info *rhs = *((const map_info **)rhsp);

  if (lhs->vaddr == rhs->vaddr) {
    return (0);
  }

  return (lhs->vaddr < rhs->vaddr ? -1 : 1);
}

// we sort map_info by starting virtual address so that we can do
// binary search to read from an address.
static bool sort_map_array(struct ps_prochandle* ph) {
  size_t num_maps = ph->core->num_maps;
  map_info* map = ph->core->maps;
  int i = 0;

  // allocate map_array
  map_info** array;
  if ( (array = (map_info**) malloc(sizeof(map_info*) * num_maps)) == NULL) {
    print_error("can't allocate memory for map array\n");
    return false;
  }

  // add maps to array
  while (map) {
    array[i] = map;
    i++;
    map = map->next;
  }

  // sort is called twice. If this is second time, clear map array
  if (ph->core->map_array) {
    free(ph->core->map_array);
  }
  ph->core->map_array = array;
  // sort the map_info array by base virtual address.
  qsort(ph->core->map_array, ph->core->num_maps, sizeof (map_info*),
        core_cmp_mapping);

  // print map
  if (is_debug()) {
    int j = 0;
    print_debug("---- sorted virtual address map ----\n");
    for (j = 0; j < ph->core->num_maps; j++) {
      print_debug("base = 0x%lx\tsize = %d\n", ph->core->map_array[j]->vaddr,
                  ph->core->map_array[j]->memsz);
    }
  }

  return true;
}

#ifndef MIN
#define MIN(x, y) (((x) < (y))? (x): (y))
#endif

static bool core_read_data(struct ps_prochandle* ph, uintptr_t addr, char *buf, size_t size) {
   ssize_t resid = size;
   int page_size=sysconf(_SC_PAGE_SIZE);
   while (resid != 0) {
      map_info *mp = core_lookup(ph, addr);
      uintptr_t mapoff;
      ssize_t len, rem;
      off_t off;
      int fd;

      if (mp == NULL) {
         break;  /* No mapping for this address */
      }

      fd = mp->fd;
      mapoff = addr - mp->vaddr;
      len = MIN(resid, mp->memsz - mapoff);
      off = mp->offset + mapoff;

      if ((len = pread(fd, buf, len, off)) <= 0) {
         break;
      }

      resid -= len;
      addr += len;
      buf = (char *)buf + len;

      // mappings always start at page boundary. But, may end in fractional
      // page. fill zeros for possible fractional page at the end of a mapping.
      rem = mp->memsz % page_size;
      if (rem > 0) {
         rem = page_size - rem;
         len = MIN(resid, rem);
         resid -= len;
         addr += len;
         // we are not assuming 'buf' to be zero initialized.
         memset(buf, 0, len);
         buf += len;
      }
   }

   if (resid) {
      print_debug("core read failed for %d byte(s) @ 0x%lx (%d more bytes)\n",
              size, addr, resid);
      return false;
   } else {
      return true;
   }
}

// null implementation for write
static bool core_write_data(struct ps_prochandle* ph,
                             uintptr_t addr, const char *buf , size_t size) {
   return false;
}

static bool core_get_lwp_regs(struct ps_prochandle* ph, lwpid_t lwp_id,
                          struct reg* regs) {
   // for core we have cached the lwp regs after segment parsed
   sa_thread_info* thr = ph->threads;
   while (thr) {
     if (thr->lwp_id == lwp_id) {
       memcpy(regs, &thr->regs, sizeof(struct reg));
       return true;
     }
     thr = thr->next;
   }
   return false;
}

static bool core_get_lwp_info(struct ps_prochandle *ph, lwpid_t id, void *info) {
   print_debug("core_get_lwp_info not implemented\n");
   return false;
}

static ps_prochandle_ops core_ops = {
   .release=  core_release,
   .p_pread=  core_read_data,
   .p_pwrite= core_write_data,
   .get_lwp_regs= core_get_lwp_regs,
   .get_lwp_info= core_get_lwp_info
};

void print_thread(sa_thread_info *threadinfo) {
  print_debug("thread added: %d\n", threadinfo->lwp_id);
  print_debug("registers:\n");

#if defined(amd64)
  print_debug("  r_r15: 0x%" PRIx64 "\n", threadinfo->regs.r_r15);
  print_debug("  r_r14: 0x%" PRIx64 "\n", threadinfo->regs.r_r14);
  print_debug("  r_r13: 0x%" PRIx64 "\n", threadinfo->regs.r_r13);
  print_debug("  r_r12: 0x%" PRIx64 "\n", threadinfo->regs.r_r12);
  print_debug("  r_r11: 0x%" PRIx64 "\n", threadinfo->regs.r_r11);
  print_debug("  r_r10: 0x%" PRIx64 "\n", threadinfo->regs.r_r10);
  print_debug("  r_r9:  0x%" PRIx64 "\n", threadinfo->regs.r_r9);
  print_debug("  r_r8:  0x%" PRIx64 "\n", threadinfo->regs.r_r8);
  print_debug("  r_rdi: 0x%" PRIx64 "\n", threadinfo->regs.r_rdi);
  print_debug("  r_rsi: 0x%" PRIx64 "\n", threadinfo->regs.r_rsi);
  print_debug("  r_rbp: 0x%" PRIx64 "\n", threadinfo->regs.r_rbp);
  print_debug("  r_rbx: 0x%" PRIx64 "\n", threadinfo->regs.r_rbx);
  print_debug("  r_rdx: 0x%" PRIx64 "\n", threadinfo->regs.r_rdx);
  print_debug("  r_rcx: 0x%" PRIx64 "\n", threadinfo->regs.r_rcx);
  print_debug("  r_rax: 0x%" PRIx64 "\n", threadinfo->regs.r_rax);
  print_debug("  r_fs:  0x%" PRIx32 "\n", threadinfo->regs.r_fs);
  print_debug("  r_gs:  0x%" PRIx32 "\n", threadinfo->regs.r_gs);
  print_debug("  r_rip  0x%" PRIx64 "\n", threadinfo->regs.r_rip);
  print_debug("  r_cs:  0x%" PRIx64 "\n", threadinfo->regs.r_cs);
  print_debug("  r_rsp: 0x%" PRIx64 "\n", threadinfo->regs.r_rsp);
  print_debug("  r_rflags: 0x%" PRIx64 "\n", threadinfo->regs.r_rflags);

#elif defined(aarch64)
  print_debug(" r_r0:  0x%" PRIx64 "\n", threadinfo->regs.r_r0);
  print_debug(" r_r1:  0x%" PRIx64 "\n", threadinfo->regs.r_r1);
  print_debug(" r_r2:  0x%" PRIx64 "\n", threadinfo->regs.r_r2);
  print_debug(" r_r3:  0x%" PRIx64 "\n", threadinfo->regs.r_r3);
  print_debug(" r_r4:  0x%" PRIx64 "\n", threadinfo->regs.r_r4);
  print_debug(" r_r5:  0x%" PRIx64 "\n", threadinfo->regs.r_r5);
  print_debug(" r_r6:  0x%" PRIx64 "\n", threadinfo->regs.r_r6);
  print_debug(" r_r7:  0x%" PRIx64 "\n", threadinfo->regs.r_r7);
  print_debug(" r_r8:  0x%" PRIx64 "\n", threadinfo->regs.r_r8);
  print_debug(" r_r9:  0x%" PRIx64 "\n", threadinfo->regs.r_r9);
  print_debug(" r_r10: 0x%" PRIx64 "\n", threadinfo->regs.r_r10);
  print_debug(" r_r11: 0x%" PRIx64 "\n", threadinfo->regs.r_r11);
  print_debug(" r_r12: 0x%" PRIx64 "\n", threadinfo->regs.r_r12);
  print_debug(" r_r13: 0x%" PRIx64 "\n", threadinfo->regs.r_r13);
  print_debug(" r_r14: 0x%" PRIx64 "\n", threadinfo->regs.r_r14);
  print_debug(" r_r15: 0x%" PRIx64 "\n", threadinfo->regs.r_r15);
  print_debug(" r_r16: 0x%" PRIx64 "\n", threadinfo->regs.r_r16);
  print_debug(" r_r17: 0x%" PRIx64 "\n", threadinfo->regs.r_r17);
  print_debug(" r_r18: 0x%" PRIx64 "\n", threadinfo->regs.r_r18);
  print_debug(" r_r19: 0x%" PRIx64 "\n", threadinfo->regs.r_r19);
  print_debug(" r_r20: 0x%" PRIx64 "\n", threadinfo->regs.r_r20);
  print_debug(" r_r21: 0x%" PRIx64 "\n", threadinfo->regs.r_r21);
  print_debug(" r_r22: 0x%" PRIx64 "\n", threadinfo->regs.r_r22);
  print_debug(" r_r23: 0x%" PRIx64 "\n", threadinfo->regs.r_r23);
  print_debug(" r_r24: 0x%" PRIx64 "\n", threadinfo->regs.r_r24);
  print_debug(" r_r25: 0x%" PRIx64 "\n", threadinfo->regs.r_r25);
  print_debug(" r_r26: 0x%" PRIx64 "\n", threadinfo->regs.r_r26);
  print_debug(" r_r27: 0x%" PRIx64 "\n", threadinfo->regs.r_r27);
  print_debug(" r_r28: 0x%" PRIx64 "\n", threadinfo->regs.r_r28);
  print_debug(" r_fp:  0x%" PRIx64 "\n", threadinfo->regs.r_fp);
  print_debug(" r_lr:  0x%" PRIx64 "\n", threadinfo->regs.r_lr);
  print_debug(" r_sp:  0x%" PRIx64 "\n", threadinfo->regs.r_sp);
  print_debug(" r_pc:  0x%" PRIx64 "\n", threadinfo->regs.r_pc);

#else
#error UNSUPPORTED_ARCH
#endif
}

// read all segments64 commands from core file
// read all thread commands from core file
static bool read_core_segments(struct ps_prochandle* ph) {
  int i = 0;
  int num_threads = 0;
  int fd = ph->core->core_fd;
  off_t offset = 0;
  mach_header_64      fhead;
  load_command        lcmd;
  segment_command_64  segcmd;
  // thread_command      thrcmd;

  lseek(fd, offset, SEEK_SET);
  if(read(fd, (void *)&fhead, sizeof(mach_header_64)) != sizeof(mach_header_64)) {
     print_error("Failed to read program header table\n");
     goto err;
  }
  print_debug("total commands: %d\n", fhead.ncmds);
  offset += sizeof(mach_header_64);
  for (i = 0; i < fhead.ncmds; i++) {
    lseek(fd, offset, SEEK_SET);
    if (read(fd, (void *)&lcmd, sizeof(load_command)) != sizeof(load_command)) {
      print_error("Failed to read command\n");
      goto err;
    }
    offset += lcmd.cmdsize;    // next command position
    //print_debug("LC: 0x%x\n", lcmd.cmd);
    if (lcmd.cmd == LC_SEGMENT_64) {
      lseek(fd, -sizeof(load_command), SEEK_CUR);
      if (read(fd, (void *)&segcmd, sizeof(segment_command_64)) != sizeof(segment_command_64)) {
        print_error("failed to read LC_SEGMENT_64 i = %d!\n", i);
        goto err;
      }
      // The base of the library is offset by a random amount which ends up as a load command with a
      // filesize of 0.  This must be ignored otherwise the base address of the library is wrong.
      if (segcmd.filesize != 0) {
        if (add_map_info(ph, fd, segcmd.fileoff, segcmd.vmaddr, segcmd.vmsize, segcmd.flags) == NULL) {
          print_error("Failed to add map_info at i = %d\n", i);
          goto err;
        }
      }
      print_debug("LC_SEGMENT_64 %s: nsects=%d fileoff=0x%llx vmaddr=0x%llx vmsize=0x%llx filesize=0x%llx %s\n",
                  segcmd.filesize == 0 ? "with filesize == 0 ignored" : "added",
                  segcmd.nsects, segcmd.fileoff, segcmd.vmaddr, segcmd.vmsize,
                  segcmd.filesize, &segcmd.segname[0]);
    } else if (lcmd.cmd == LC_THREAD || lcmd.cmd == LC_UNIXTHREAD) {
      typedef struct thread_fc {
        uint32_t  flavor;
        uint32_t  count;
      } thread_fc;
      thread_fc fc;
      uint32_t size = sizeof(load_command);
      while (size < lcmd.cmdsize) {
        if (read(fd, (void *)&fc, sizeof(thread_fc)) != sizeof(thread_fc)) {
          print_error("Reading flavor, count failed.\n");
          goto err;
        }
        size += sizeof(thread_fc);
#if defined(amd64)
        if (fc.flavor == x86_THREAD_STATE) {
          x86_thread_state_t thrstate;
          if (read(fd, (void *)&thrstate, sizeof(x86_thread_state_t)) != sizeof(x86_thread_state_t)) {
            print_error("Reading flavor, count failed.\n");
            goto err;
          }
          size += sizeof(x86_thread_state_t);
          // create thread info list, update lwp_id later
          sa_thread_info* newthr = add_thread_info(ph, (pthread_t) -1, (lwpid_t) num_threads++);
          if (newthr == NULL) {
            print_error("create thread_info failed\n");
            goto err;
          }

          // note __DARWIN_UNIX03 depengs on other definitions
#if __DARWIN_UNIX03
#define get_register_v(regst, regname) \
  regst.uts.ts64.__##regname
#else
#define get_register_v(regst, regname) \
  regst.uts.ts64.##regname
#endif // __DARWIN_UNIX03
          newthr->regs.r_rax = get_register_v(thrstate, rax);
          newthr->regs.r_rbx = get_register_v(thrstate, rbx);
          newthr->regs.r_rcx = get_register_v(thrstate, rcx);
          newthr->regs.r_rdx = get_register_v(thrstate, rdx);
          newthr->regs.r_rdi = get_register_v(thrstate, rdi);
          newthr->regs.r_rsi = get_register_v(thrstate, rsi);
          newthr->regs.r_rbp = get_register_v(thrstate, rbp);
          newthr->regs.r_rsp = get_register_v(thrstate, rsp);
          newthr->regs.r_r8  = get_register_v(thrstate, r8);
          newthr->regs.r_r9  = get_register_v(thrstate, r9);
          newthr->regs.r_r10 = get_register_v(thrstate, r10);
          newthr->regs.r_r11 = get_register_v(thrstate, r11);
          newthr->regs.r_r12 = get_register_v(thrstate, r12);
          newthr->regs.r_r13 = get_register_v(thrstate, r13);
          newthr->regs.r_r14 = get_register_v(thrstate, r14);
          newthr->regs.r_r15 = get_register_v(thrstate, r15);
          newthr->regs.r_rip = get_register_v(thrstate, rip);
          newthr->regs.r_rflags = get_register_v(thrstate, rflags);
          newthr->regs.r_cs  = get_register_v(thrstate, cs);
          newthr->regs.r_fs  = get_register_v(thrstate, fs);
          newthr->regs.r_gs  = get_register_v(thrstate, gs);
          print_thread(newthr);
        } else if (fc.flavor == x86_FLOAT_STATE) {
          x86_float_state_t flstate;
          if (read(fd, (void *)&flstate, sizeof(x86_float_state_t)) != sizeof(x86_float_state_t)) {
            print_error("Reading flavor, count failed.\n");
            goto err;
          }
          size += sizeof(x86_float_state_t);
        } else if (fc.flavor == x86_EXCEPTION_STATE) {
          x86_exception_state_t excpstate;
          if (read(fd, (void *)&excpstate, sizeof(x86_exception_state_t)) != sizeof(x86_exception_state_t)) {
            print_error("Reading flavor, count failed.\n");
            goto err;
          }
          size += sizeof(x86_exception_state_t);
        }

#elif defined(aarch64)
        if (fc.flavor == ARM_THREAD_STATE64) {
          arm_thread_state64_t thrstate;
          if (read(fd, (void *)&thrstate, sizeof(arm_thread_state64_t)) != sizeof(arm_thread_state64_t)) {
            print_error("Reading flavor, count failed.\n");
            goto err;
          }
          size += sizeof(arm_thread_state64_t);
          // create thread info list, update lwp_id later
          sa_thread_info* newthr = add_thread_info(ph, (pthread_t) -1, (lwpid_t) num_threads++);
          if (newthr == NULL) {
            print_error("create thread_info failed\n");
            goto err;
          }

          // note __DARWIN_UNIX03 depengs on other definitions
#if __DARWIN_UNIX03
#define get_register_v(regst, regname) \
  regst.__##regname
#else
#define get_register_v(regst, regname) \
  regst.##regname
#endif // __DARWIN_UNIX03
          newthr->regs.r_r0  = get_register_v(thrstate, x[0]);
          newthr->regs.r_r1  = get_register_v(thrstate, x[1]);
          newthr->regs.r_r2  = get_register_v(thrstate, x[2]);
          newthr->regs.r_r3  = get_register_v(thrstate, x[3]);
          newthr->regs.r_r4  = get_register_v(thrstate, x[4]);
          newthr->regs.r_r5  = get_register_v(thrstate, x[5]);
          newthr->regs.r_r6  = get_register_v(thrstate, x[6]);
          newthr->regs.r_r7  = get_register_v(thrstate, x[7]);
          newthr->regs.r_r8  = get_register_v(thrstate, x[8]);
          newthr->regs.r_r9  = get_register_v(thrstate, x[9]);
          newthr->regs.r_r10 = get_register_v(thrstate, x[10]);
          newthr->regs.r_r11 = get_register_v(thrstate, x[11]);
          newthr->regs.r_r12 = get_register_v(thrstate, x[12]);
          newthr->regs.r_r13 = get_register_v(thrstate, x[13]);
          newthr->regs.r_r14 = get_register_v(thrstate, x[14]);
          newthr->regs.r_r15 = get_register_v(thrstate, x[15]);
          newthr->regs.r_r16 = get_register_v(thrstate, x[16]);
          newthr->regs.r_r17 = get_register_v(thrstate, x[17]);
          newthr->regs.r_r18 = get_register_v(thrstate, x[18]);
          newthr->regs.r_r19 = get_register_v(thrstate, x[19]);
          newthr->regs.r_r20 = get_register_v(thrstate, x[20]);
          newthr->regs.r_r21 = get_register_v(thrstate, x[21]);
          newthr->regs.r_r22 = get_register_v(thrstate, x[22]);
          newthr->regs.r_r23 = get_register_v(thrstate, x[23]);
          newthr->regs.r_r24 = get_register_v(thrstate, x[24]);
          newthr->regs.r_r25 = get_register_v(thrstate, x[25]);
          newthr->regs.r_r26 = get_register_v(thrstate, x[26]);
          newthr->regs.r_r27 = get_register_v(thrstate, x[27]);
          newthr->regs.r_r28 = get_register_v(thrstate, x[28]);
          newthr->regs.r_fp  = get_register_v(thrstate, fp);
          newthr->regs.r_lr  = get_register_v(thrstate, lr);
          newthr->regs.r_sp  = get_register_v(thrstate, sp);
          newthr->regs.r_pc  = get_register_v(thrstate, pc);
          print_thread(newthr);
        } else if (fc.flavor == ARM_NEON_STATE64) {
          arm_neon_state64_t flstate;
          if (read(fd, (void *)&flstate, sizeof(arm_neon_state64_t)) != sizeof(arm_neon_state64_t)) {
            print_error("Reading flavor, count failed.\n");
            goto err;
          }
          size += sizeof(arm_neon_state64_t);
        } else if (fc.flavor == ARM_EXCEPTION_STATE64) {
          arm_exception_state64_t excpstate;
          if (read(fd, (void *)&excpstate, sizeof(arm_exception_state64_t)) != sizeof(arm_exception_state64_t)) {
            print_error("Reading flavor, count failed.\n");
            goto err;
          }
          size += sizeof(arm_exception_state64_t);
        } else if (fc.flavor == ARM_DEBUG_STATE64) {
          arm_debug_state64_t dbgstate;
          if (read(fd, (void *)&dbgstate, sizeof(arm_debug_state64_t)) != sizeof(arm_debug_state64_t)) {
            print_error("Reading flavor, count failed.\n");
            goto err;
          }
          size += sizeof(arm_debug_state64_t);
        }

#else
#error UNSUPPORTED_ARCH
#endif
      }
    }
  }
  return true;
err:
  return false;
}

static bool exists(const char *fname) {
  return access(fname, F_OK) == 0;
}

// reverse strstr()
static char* rstrstr(const char *str, const char *sub) {
  char *result = NULL;
  for (char *p = strstr(str, sub); p != NULL; p = strstr(p + 1, sub)) {
    result = p;
  }
  return result;
}

// Check if <filename> exists in the <jdk_subdir> directory of <jdk_dir>.
// Note <jdk_subdir> and  <filename> already have '/' prepended.
static bool get_real_path_jdk_subdir(char* rpath, char* filename, char* jdk_dir, char* jdk_subdir) {
  char  filepath[BUF_SIZE];
  strncpy(filepath, jdk_dir, BUF_SIZE);
  filepath[BUF_SIZE - 1] = '\0'; // just in case jdk_dir didn't fit
  strncat(filepath, jdk_subdir, BUF_SIZE - 1 - strlen(filepath));
  strncat(filepath, filename, BUF_SIZE - 1 - strlen(filepath));
  if (exists(filepath)) {
    strcpy(rpath, filepath);
    return true;
  }
  return false;
}

// Look for <filename> in various subdirs of <jdk_dir>.
static bool get_real_path_jdk_dir(char* rpath, char* filename, char* jdk_dir) {
  if (get_real_path_jdk_subdir(rpath, filename, jdk_dir, "/lib")) {
      return true;
  }
  if (get_real_path_jdk_subdir(rpath, filename, jdk_dir, "/lib/server")) {
      return true;
  }
  if (get_real_path_jdk_subdir(rpath, filename, jdk_dir, "/jre/lib")) {
      return true;
  }
  if (get_real_path_jdk_subdir(rpath, filename, jdk_dir, "/jre/lib/server")) {
      return true;
  }
  return false;
}

// Get the file specified by rpath. We check various directories for it.
static bool get_real_path(struct ps_prochandle* ph, char *rpath) {
  char* execname = ph->core->exec_path;
  char jdk_dir[BUF_SIZE];
  char* posbin;
  char* filename = strrchr(rpath, '/'); // like /libjvm.dylib
  if (filename == NULL) {
    return false;
  }

  // First look for the library in 3 places where the JDK might be located. For each of
  // these 3 potential JDK locations we look in lib, lib/server, jre/lib, and jre/lib/server.

  posbin = rstrstr(execname, "/bin/java");
  if (posbin != NULL) {
    strncpy(jdk_dir, execname, posbin - execname);
    jdk_dir[posbin - execname] = '\0';
    if (get_real_path_jdk_dir(rpath, filename, jdk_dir)) {
      return true;
    }
  }

  char* java_home = getenv("JAVA_HOME");
  if (java_home != NULL) {
    if (get_real_path_jdk_dir(rpath, filename, java_home)) {
      return true;
    }
  }

  // Look for bin directory in path. This is useful when attaching to a core file
  // that was launched with a JDK tool other than "java".
  posbin = rstrstr(execname, "/bin/");  // look for the last occurrence of "/bin/"
  if (posbin != NULL) {
    strncpy(jdk_dir, execname, posbin - execname);
    jdk_dir[posbin - execname] = '\0';
    if (get_real_path_jdk_dir(rpath, filename, jdk_dir)) {
      return true;
    }
  }

  // If we didn't find the library in any of the 3 above potential JDK locations, then look in
  // DYLD_LIBRARY_PATH. This is where user libraries might be. We don't treat these
  // paths as JDK paths, so we don't append the  various JDK subdirs like lib/server.
  // However, that doesn't preclude JDK libraries from actually being in one of the paths.
  char* dyldpath = getenv("DYLD_LIBRARY_PATH");
  if (dyldpath != NULL) {
    char* save_ptr;
    char  filepath[BUF_SIZE];
    char* dypath = strtok_r(dyldpath, ":", &save_ptr);
    while (dypath != NULL) {
      strncpy(filepath, dypath, BUF_SIZE);
      filepath[BUF_SIZE - 1] = '\0'; // just in case dypath didn't fit
      strncat(filepath, filename, BUF_SIZE - 1 - strlen(filepath));
      if (exists(filepath)) {
        strcpy(rpath, filepath);
        return true;
      }
      dypath = strtok_r(NULL, ":", &save_ptr);
    }
  }

  return false;
}

static bool read_shared_lib_info(struct ps_prochandle* ph) {
  static int pagesize = 0;
  int fd = ph->core->core_fd;
  int i = 0, j;
  uint32_t  v;
  mach_header_64 header;        // used to check if a file header in segment
  load_command lcmd;
  dylib_command dylibcmd;

  char name[BUF_SIZE];  // use to store name

  if (pagesize == 0) {
    pagesize = getpagesize();
    print_debug("page size is %d\n", pagesize);
  }
  for (j = 0; j < ph->core->num_maps; j++) {
    map_info *iter = ph->core->map_array[j];   // head
    off_t fpos = iter->offset;
    if (iter->fd != fd) {
      // only search core file!
      continue;
    }
    print_debug("map_info %d: vmaddr = 0x%016llx fileoff = 0x%llx vmsize = 0x%lx\n",
                           j, iter->vaddr, iter->offset, iter->memsz);
    lseek(fd, fpos, SEEK_SET);
    // we assume .dylib loaded at segment address --- which is true for JVM libraries
    // multiple files may be loaded in one segment.
    // if first word is not a magic word, means this segment does not contain lib file.
    if (read(fd, (void *)&v, sizeof(uint32_t)) == sizeof(uint32_t)) {
      if (v != MH_MAGIC_64) {
        continue;
      }
    } else {
      // may be encountered last map, which is not readable
      continue;
    }
    while (ltell(fd) - iter->offset < iter->memsz) {
      lseek(fd, fpos, SEEK_SET);
      if (read(fd, (void *)&v, sizeof(uint32_t)) != sizeof(uint32_t)) {
        break;
      }
      if (v != MH_MAGIC_64) {
        fpos = (ltell(fd) + pagesize -1)/pagesize * pagesize;
        continue;
      }
      lseek(fd, -sizeof(uint32_t), SEEK_CUR);
      // This is the beginning of the mach-o file in the segment.
      if (read(fd, (void *)&header, sizeof(mach_header_64)) != sizeof(mach_header_64)) {
        print_error("Failed to file header\n");
        goto err;
      }
      fpos = ltell(fd);

      // found a mach-o file in this segment
      for (i = 0; i < header.ncmds; i++) {
        // read commands in this "file"
        // LC_ID_DYLIB is the file itself for a .dylib
        lseek(fd, fpos, SEEK_SET);
        if (read(fd, (void *)&lcmd, sizeof(load_command)) != sizeof(load_command)) {
          print_error("Failed to read command\n");
          return false;   // error
        }
        fpos += lcmd.cmdsize;  // next command position
        // make sure still within seg size.
        if (fpos  - lcmd.cmdsize - iter->offset > iter->memsz) {
          print_warning("out of segment limit: %ld \n", fpos  - lcmd.cmdsize - iter->offset);
          break;  // no need to iterate all commands
        }
        if (lcmd.cmd == LC_ID_DYLIB) {
          lseek(fd, -sizeof(load_command), SEEK_CUR);
          if (read(fd, (void *)&dylibcmd, sizeof(dylib_command)) != sizeof(dylib_command)) {
            print_error("Failed to read command\n");
            return false;
          }
          /**** name stored at dylib_command.dylib.name.offset, is a C string  */
          lseek(fd, dylibcmd.dylib.name.offset - sizeof(dylib_command), SEEK_CUR);
          int j = 0;
          while (j < BUF_SIZE) {
            read(fd, (void *)(name + j), sizeof(char));
            if (name[j] == '\0') break;
            j++;
          }
          print_debug("%d %s\n", lcmd.cmd, name);
          // changed name from @rpath/xxxx.dylib to real path
          if (strrchr(name, '@')) {
            get_real_path(ph, name);
            print_debug("get_real_path returned: %s\n", name);
          } else {
            break; // Ignore non-relative paths, which are system libs. See JDK-8249779.
          }
          add_lib_info(ph, name, iter->vaddr);
          break;
        }
      }
      // done with the file, advanced to next page to search more files
#if 0
      // This line is disabled due to JDK-8249779. Instead we break out of the loop
      // and don't attempt to find any more mach-o files in this segment.
      fpos = (ltell(fd) + pagesize - 1) / pagesize * pagesize;
#else
      break;
#endif
    }
  }
  return true;
err:
  return false;
}

bool read_macho64_header(int fd, mach_header_64* core_header) {
  bool is_macho = false;
  if (fd < 0) return false;
  off_t pos = ltell(fd);
  lseek(fd, 0, SEEK_SET);
  if (read(fd, (void *)core_header, sizeof(mach_header_64)) != sizeof(mach_header_64)) {
    is_macho = false;
  } else {
    is_macho = (core_header->magic ==  MH_MAGIC_64 || core_header->magic ==  MH_CIGAM_64);
  }
  lseek(fd, pos, SEEK_SET);
  return is_macho;
}

// the one and only one exposed stuff from this file
struct ps_prochandle* Pgrab_core(const char* exec_file, const char* core_file) {
  mach_header_64 core_header;
  mach_header_64 exec_header;

  struct ps_prochandle* ph = (struct ps_prochandle*) calloc(1, sizeof(struct ps_prochandle));
  if (ph == NULL) {
    print_error("can't allocate ps_prochandle\n");
    return NULL;
  }

  if ((ph->core = (struct core_data*) calloc(1, sizeof(struct core_data))) == NULL) {
    free(ph);
    print_error("can't allocate ps_prochandle\n");
    return NULL;
  }

  // initialize ph
  ph->ops = &core_ops;
  ph->core->core_fd   = -1;
  ph->core->exec_fd   = -1;
  ph->core->interp_fd = -1;

  print_debug("exec: %s   core: %s\n", exec_file, core_file);

  strncpy(ph->core->exec_path, exec_file, sizeof(ph->core->exec_path));

  // open the core file
  if ((ph->core->core_fd = open(core_file, O_RDONLY)) < 0) {
    print_error("can't open core file: %s\n", strerror(errno));
    goto err;
  }

  // read core file header
  if (read_macho64_header(ph->core->core_fd, &core_header) != true || core_header.filetype != MH_CORE) {
    print_error("core file is not a valid Mach-O file\n");
    goto err;
  }

  if ((ph->core->exec_fd = open(exec_file, O_RDONLY)) < 0) {
    print_error("can't open executable file: %s\n", strerror(errno));
    goto err;
  }

  if (read_macho64_header(ph->core->exec_fd, &exec_header) != true ||
                          exec_header.filetype != MH_EXECUTE) {
    print_error("executable file is not a valid Mach-O file\n");
    goto err;
  }

  // process core file segments
  if (read_core_segments(ph) != true) {
    print_error("failed to read core segments\n");
    goto err;
  }

  // allocate and sort maps into map_array, we need to do this
  // here because read_shared_lib_info needs to read from debuggee
  // address space
  if (sort_map_array(ph) != true) {
    print_error("failed to sort segment map array\n");
    goto err;
  }

  if (read_shared_lib_info(ph) != true) {
    print_error("failed to read libraries\n");
    goto err;
  }

  // sort again because we have added more mappings from shared objects
  if (sort_map_array(ph) != true) {
    print_error("failed to sort segment map array\n");
    goto err;
  }

  if (init_classsharing_workaround(ph) != true) {
    print_error("failed to workaround class sharing\n");
    goto err;
  }

  print_debug("Leave Pgrab_core\n");
  return ph;

err:
  Prelease(ph);
  return NULL;
}
