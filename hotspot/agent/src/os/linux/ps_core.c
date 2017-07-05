/*
 * Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <stdlib.h>
#include <stddef.h>
#include <elf.h>
#include <link.h>
#include "libproc_impl.h"
#include "salibelf.h"

// This file has the libproc implementation to read core files.
// For live processes, refer to ps_proc.c. Portions of this is adapted
// /modelled after Solaris libproc.so (in particular Pcore.c)

//----------------------------------------------------------------------
// ps_prochandle cleanup helper functions

// close all file descriptors
static void close_elf_files(struct ps_prochandle* ph) {
   lib_info* lib = NULL;

   // close core file descriptor
   if (ph->core->core_fd >= 0)
     close(ph->core->core_fd);

   // close exec file descriptor
   if (ph->core->exec_fd >= 0)
     close(ph->core->exec_fd);

   // close interp file descriptor
   if (ph->core->interp_fd >= 0)
     close(ph->core->interp_fd);

   // close class share archive file
   if (ph->core->classes_jsa_fd >= 0)
     close(ph->core->classes_jsa_fd);

   // close all library file descriptors
   lib = ph->libs;
   while (lib) {
      int fd = lib->fd;
      if (fd >= 0 && fd != ph->core->exec_fd) close(fd);
      lib = lib->next;
   }
}

// clean all map_info stuff
static void destroy_map_info(struct ps_prochandle* ph) {
  map_info* map = ph->core->maps;
  while (map) {
     map_info* next = map->next;
     free(map);
     map = next;
  }

  if (ph->core->map_array) {
     free(ph->core->map_array);
  }

  // Part of the class sharing workaround
  map = ph->core->class_share_maps;
  while (map) {
     map_info* next = map->next;
     free(map);
     map = next;
  }
}

// ps_prochandle operations
static void core_release(struct ps_prochandle* ph) {
   if (ph->core) {
      close_elf_files(ph);
      destroy_map_info(ph);
      free(ph->core);
   }
}

static map_info* allocate_init_map(int fd, off_t offset, uintptr_t vaddr, size_t memsz) {
   map_info* map;
   if ( (map = (map_info*) calloc(1, sizeof(map_info))) == NULL) {
      print_debug("can't allocate memory for map_info\n");
      return NULL;
   }

   // initialize map
   map->fd     = fd;
   map->offset = offset;
   map->vaddr  = vaddr;
   map->memsz  = memsz;
   return map;
}

// add map info with given fd, offset, vaddr and memsz
static map_info* add_map_info(struct ps_prochandle* ph, int fd, off_t offset,
                             uintptr_t vaddr, size_t memsz) {
   map_info* map;
   if ((map = allocate_init_map(fd, offset, vaddr, memsz)) == NULL) {
      return NULL;
   }

   // add this to map list
   map->next  = ph->core->maps;
   ph->core->maps   = map;
   ph->core->num_maps++;

   return map;
}

// Part of the class sharing workaround
static map_info* add_class_share_map_info(struct ps_prochandle* ph, off_t offset,
                             uintptr_t vaddr, size_t memsz) {
   map_info* map;
   if ((map = allocate_init_map(ph->core->classes_jsa_fd,
                                offset, vaddr, memsz)) == NULL) {
      return NULL;
   }

   map->next = ph->core->class_share_maps;
   ph->core->class_share_maps = map;
}

// Return the map_info for the given virtual address.  We keep a sorted
// array of pointers in ph->map_array, so we can binary search.
static map_info* core_lookup(struct ps_prochandle *ph, uintptr_t addr)
{
   int mid, lo = 0, hi = ph->core->num_maps - 1;
   map_info *mp;

   while (hi - lo > 1) {
     mid = (lo + hi) / 2;
      if (addr >= ph->core->map_array[mid]->vaddr)
         lo = mid;
      else
         hi = mid;
   }

   if (addr < ph->core->map_array[hi]->vaddr)
      mp = ph->core->map_array[lo];
   else
      mp = ph->core->map_array[hi];

   if (addr >= mp->vaddr && addr < mp->vaddr + mp->memsz)
      return (mp);


   // Part of the class sharing workaround
   // Unfortunately, we have no way of detecting -Xshare state.
   // Check out the share maps atlast, if we don't find anywhere.
   // This is done this way so to avoid reading share pages
   // ahead of other normal maps. For eg. with -Xshare:off we don't
   // want to prefer class sharing data to data from core.
   mp = ph->core->class_share_maps;
   if (mp) {
      print_debug("can't locate map_info at 0x%lx, trying class share maps\n",
             addr);
   }
   while (mp) {
      if (addr >= mp->vaddr && addr < mp->vaddr + mp->memsz) {
         print_debug("located map_info at 0x%lx from class share maps\n",
                  addr);
         return (mp);
      }
      mp = mp->next;
   }

   print_debug("can't locate map_info at 0x%lx\n", addr);
   return (NULL);
}

//---------------------------------------------------------------
// Part of the class sharing workaround:
//
// With class sharing, pages are mapped from classes[_g].jsa file.
// The read-only class sharing pages are mapped as MAP_SHARED,
// PROT_READ pages. These pages are not dumped into core dump.
// With this workaround, these pages are read from classes[_g].jsa.

// FIXME: !HACK ALERT!
// The format of sharing achive file header is needed to read shared heap
// file mappings. For now, I am hard coding portion of FileMapHeader here.
// Refer to filemap.hpp.

// FileMapHeader describes the shared space data in the file to be
// mapped.  This structure gets written to a file.  It is not a class,
// so that the compilers don't add any compiler-private data to it.

// Refer to CompactingPermGenGen::n_regions in compactingPermGenGen.hpp
#define NUM_SHARED_MAPS 4

// Refer to FileMapInfo::_current_version in filemap.hpp
#define CURRENT_ARCHIVE_VERSION 1

struct FileMapHeader {
  int   _magic;              // identify file type.
  int   _version;            // (from enum, above.)
  size_t _alignment;         // how shared archive should be aligned

  struct space_info {
    int    _file_offset;     // sizeof(this) rounded to vm page size
    char*  _base;            // copy-on-write base address
    size_t _capacity;        // for validity checking
    size_t _used;            // for setting space top on read

    // 4991491 NOTICE These are C++ bool's in filemap.hpp and must match up with
    // the C type matching the C++ bool type on any given platform. For
    // Hotspot on Linux we assume the corresponding C type is char but
    // licensees on Linux versions may need to adjust the type of these fields.
    char   _read_only;       // read only space?
    char   _allow_exec;      // executable code in space?

  } _space[NUM_SHARED_MAPS]; // was _space[CompactingPermGenGen::n_regions];

  // Ignore the rest of the FileMapHeader. We don't need those fields here.
};

static bool read_jboolean(struct ps_prochandle* ph, uintptr_t addr, jboolean* pvalue) {
   jboolean i;
   if (ps_pdread(ph, (psaddr_t) addr, &i, sizeof(i)) == PS_OK) {
      *pvalue = i;
      return true;
   } else {
      return false;
   }
}

static bool read_pointer(struct ps_prochandle* ph, uintptr_t addr, uintptr_t* pvalue) {
   uintptr_t uip;
   if (ps_pdread(ph, (psaddr_t) addr, &uip, sizeof(uip)) == PS_OK) {
      *pvalue = uip;
      return true;
   } else {
      return false;
   }
}

// used to read strings from debuggee
static bool read_string(struct ps_prochandle* ph, uintptr_t addr, char* buf, size_t size) {
   size_t i = 0;
   char  c = ' ';

   while (c != '\0') {
     if (ps_pdread(ph, (psaddr_t) addr, &c, sizeof(char)) != PS_OK)
         return false;
      if (i < size - 1)
         buf[i] = c;
      else // smaller buffer
         return false;
      i++; addr++;
   }

   buf[i] = '\0';
   return true;
}

#define USE_SHARED_SPACES_SYM "UseSharedSpaces"
// mangled name of Arguments::SharedArchivePath
#define SHARED_ARCHIVE_PATH_SYM "_ZN9Arguments17SharedArchivePathE"

static bool init_classsharing_workaround(struct ps_prochandle* ph) {
   lib_info* lib = ph->libs;
   while (lib != NULL) {
      // we are iterating over shared objects from the core dump. look for
      // libjvm[_g].so.
      const char *jvm_name = 0;
      if ((jvm_name = strstr(lib->name, "/libjvm.so")) != 0 ||
          (jvm_name = strstr(lib->name, "/libjvm_g.so")) != 0) {
         char classes_jsa[PATH_MAX];
         struct FileMapHeader header;
         size_t n = 0;
         int fd = -1, m = 0;
         uintptr_t base = 0, useSharedSpacesAddr = 0;
         uintptr_t sharedArchivePathAddrAddr = 0, sharedArchivePathAddr = 0;
         jboolean useSharedSpaces = 0;
         map_info* mi = 0;

         memset(classes_jsa, 0, sizeof(classes_jsa));
         jvm_name = lib->name;
         useSharedSpacesAddr = lookup_symbol(ph, jvm_name, USE_SHARED_SPACES_SYM);
         if (useSharedSpacesAddr == 0) {
            print_debug("can't lookup 'UseSharedSpaces' flag\n");
            return false;
         }

         // Hotspot vm types are not exported to build this library. So
         // using equivalent type jboolean to read the value of
         // UseSharedSpaces which is same as hotspot type "bool".
         if (read_jboolean(ph, useSharedSpacesAddr, &useSharedSpaces) != true) {
            print_debug("can't read the value of 'UseSharedSpaces' flag\n");
            return false;
         }

         if ((int)useSharedSpaces == 0) {
            print_debug("UseSharedSpaces is false, assuming -Xshare:off!\n");
            return true;
         }

         sharedArchivePathAddrAddr = lookup_symbol(ph, jvm_name, SHARED_ARCHIVE_PATH_SYM);
         if (sharedArchivePathAddrAddr == 0) {
            print_debug("can't lookup shared archive path symbol\n");
            return false;
         }

         if (read_pointer(ph, sharedArchivePathAddrAddr, &sharedArchivePathAddr) != true) {
            print_debug("can't read shared archive path pointer\n");
            return false;
         }

         if (read_string(ph, sharedArchivePathAddr, classes_jsa, sizeof(classes_jsa)) != true) {
            print_debug("can't read shared archive path value\n");
            return false;
         }

         print_debug("looking for %s\n", classes_jsa);
         // open the class sharing archive file
         fd = pathmap_open(classes_jsa);
         if (fd < 0) {
            print_debug("can't open %s!\n", classes_jsa);
            ph->core->classes_jsa_fd = -1;
            return false;
         } else {
            print_debug("opened %s\n", classes_jsa);
         }

         // read FileMapHeader from the file
         memset(&header, 0, sizeof(struct FileMapHeader));
         if ((n = read(fd, &header, sizeof(struct FileMapHeader)))
              != sizeof(struct FileMapHeader)) {
            print_debug("can't read shared archive file map header from %s\n", classes_jsa);
            close(fd);
            return false;
         }

         // check file magic
         if (header._magic != 0xf00baba2) {
            print_debug("%s has bad shared archive file magic number 0x%x, expecing 0xf00baba2\n",
                        classes_jsa, header._magic);
            close(fd);
            return false;
         }

         // check version
         if (header._version != CURRENT_ARCHIVE_VERSION) {
            print_debug("%s has wrong shared archive file version %d, expecting %d\n",
                        classes_jsa, header._version, CURRENT_ARCHIVE_VERSION);
            close(fd);
            return false;
         }

         ph->core->classes_jsa_fd = fd;
         // add read-only maps from classes[_g].jsa to the list of maps
         for (m = 0; m < NUM_SHARED_MAPS; m++) {
            if (header._space[m]._read_only) {
               base = (uintptr_t) header._space[m]._base;
               // no need to worry about the fractional pages at-the-end.
               // possible fractional pages are handled by core_read_data.
               add_class_share_map_info(ph, (off_t) header._space[m]._file_offset,
                         base, (size_t) header._space[m]._used);
               print_debug("added a share archive map at 0x%lx\n", base);
            }
         }
         return true;
      }
      lib = lib->next;
   }
   return true;
}


//---------------------------------------------------------------------------
// functions to handle map_info

// Order mappings based on virtual address.  We use this function as the
// callback for sorting the array of map_info pointers.
static int core_cmp_mapping(const void *lhsp, const void *rhsp)
{
   const map_info *lhs = *((const map_info **)lhsp);
   const map_info *rhs = *((const map_info **)rhsp);

   if (lhs->vaddr == rhs->vaddr)
      return (0);

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
      print_debug("can't allocate memory for map array\n");
      return false;
   }

   // add maps to array
   while (map) {
      array[i] = map;
      i++;
      map = map->next;
   }

   // sort is called twice. If this is second time, clear map array
   if (ph->core->map_array) free(ph->core->map_array);
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

      if (mp == NULL)
         break;  /* No mapping for this address */

      fd = mp->fd;
      mapoff = addr - mp->vaddr;
      len = MIN(resid, mp->memsz - mapoff);
      off = mp->offset + mapoff;

      if ((len = pread(fd, buf, len, off)) <= 0)
         break;

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
                          struct user_regs_struct* regs) {
   // for core we have cached the lwp regs from NOTE section
   thread_info* thr = ph->threads;
   while (thr) {
     if (thr->lwp_id == lwp_id) {
       memcpy(regs, &thr->regs, sizeof(struct user_regs_struct));
       return true;
     }
     thr = thr->next;
   }
   return false;
}

static ps_prochandle_ops core_ops = {
   .release=  core_release,
   .p_pread=  core_read_data,
   .p_pwrite= core_write_data,
   .get_lwp_regs= core_get_lwp_regs
};

// read regs and create thread from NT_PRSTATUS entries from core file
static bool core_handle_prstatus(struct ps_prochandle* ph, const char* buf, size_t nbytes) {
   // we have to read prstatus_t from buf
   // assert(nbytes == sizeof(prstaus_t), "size mismatch on prstatus_t");
   prstatus_t* prstat = (prstatus_t*) buf;
   thread_info* newthr;
   print_debug("got integer regset for lwp %d\n", prstat->pr_pid);
   // we set pthread_t to -1 for core dump
   if((newthr = add_thread_info(ph, (pthread_t) -1,  prstat->pr_pid)) == NULL)
      return false;

   // copy regs
   memcpy(&newthr->regs, prstat->pr_reg, sizeof(struct user_regs_struct));

   if (is_debug()) {
      print_debug("integer regset\n");
#ifdef i386
      // print the regset
      print_debug("\teax = 0x%x\n", newthr->regs.eax);
      print_debug("\tebx = 0x%x\n", newthr->regs.ebx);
      print_debug("\tecx = 0x%x\n", newthr->regs.ecx);
      print_debug("\tedx = 0x%x\n", newthr->regs.edx);
      print_debug("\tesp = 0x%x\n", newthr->regs.esp);
      print_debug("\tebp = 0x%x\n", newthr->regs.ebp);
      print_debug("\tesi = 0x%x\n", newthr->regs.esi);
      print_debug("\tedi = 0x%x\n", newthr->regs.edi);
      print_debug("\teip = 0x%x\n", newthr->regs.eip);
#endif

#if defined(amd64) || defined(x86_64)
      // print the regset
      print_debug("\tr15 = 0x%lx\n", newthr->regs.r15);
      print_debug("\tr14 = 0x%lx\n", newthr->regs.r14);
      print_debug("\tr13 = 0x%lx\n", newthr->regs.r13);
      print_debug("\tr12 = 0x%lx\n", newthr->regs.r12);
      print_debug("\trbp = 0x%lx\n", newthr->regs.rbp);
      print_debug("\trbx = 0x%lx\n", newthr->regs.rbx);
      print_debug("\tr11 = 0x%lx\n", newthr->regs.r11);
      print_debug("\tr10 = 0x%lx\n", newthr->regs.r10);
      print_debug("\tr9 = 0x%lx\n", newthr->regs.r9);
      print_debug("\tr8 = 0x%lx\n", newthr->regs.r8);
      print_debug("\trax = 0x%lx\n", newthr->regs.rax);
      print_debug("\trcx = 0x%lx\n", newthr->regs.rcx);
      print_debug("\trdx = 0x%lx\n", newthr->regs.rdx);
      print_debug("\trsi = 0x%lx\n", newthr->regs.rsi);
      print_debug("\trdi = 0x%lx\n", newthr->regs.rdi);
      print_debug("\torig_rax = 0x%lx\n", newthr->regs.orig_rax);
      print_debug("\trip = 0x%lx\n", newthr->regs.rip);
      print_debug("\tcs = 0x%lx\n", newthr->regs.cs);
      print_debug("\teflags = 0x%lx\n", newthr->regs.eflags);
      print_debug("\trsp = 0x%lx\n", newthr->regs.rsp);
      print_debug("\tss = 0x%lx\n", newthr->regs.ss);
      print_debug("\tfs_base = 0x%lx\n", newthr->regs.fs_base);
      print_debug("\tgs_base = 0x%lx\n", newthr->regs.gs_base);
      print_debug("\tds = 0x%lx\n", newthr->regs.ds);
      print_debug("\tes = 0x%lx\n", newthr->regs.es);
      print_debug("\tfs = 0x%lx\n", newthr->regs.fs);
      print_debug("\tgs = 0x%lx\n", newthr->regs.gs);
#endif
   }

   return true;
}

#define ROUNDUP(x, y)  ((((x)+((y)-1))/(y))*(y))

// read NT_PRSTATUS entries from core NOTE segment
static bool core_handle_note(struct ps_prochandle* ph, ELF_PHDR* note_phdr) {
   char* buf = NULL;
   char* p = NULL;
   size_t size = note_phdr->p_filesz;

   // we are interested in just prstatus entries. we will ignore the rest.
   // Advance the seek pointer to the start of the PT_NOTE data
   if (lseek(ph->core->core_fd, note_phdr->p_offset, SEEK_SET) == (off_t)-1) {
      print_debug("failed to lseek to PT_NOTE data\n");
      return false;
   }

   // Now process the PT_NOTE structures.  Each one is preceded by
   // an Elf{32/64}_Nhdr structure describing its type and size.
   if ( (buf = (char*) malloc(size)) == NULL) {
      print_debug("can't allocate memory for reading core notes\n");
      goto err;
   }

   // read notes into buffer
   if (read(ph->core->core_fd, buf, size) != size) {
      print_debug("failed to read notes, core file must have been truncated\n");
      goto err;
   }

   p = buf;
   while (p < buf + size) {
      ELF_NHDR* notep = (ELF_NHDR*) p;
      char* descdata  = p + sizeof(ELF_NHDR) + ROUNDUP(notep->n_namesz, 4);
      print_debug("Note header with n_type = %d and n_descsz = %u\n",
                                   notep->n_type, notep->n_descsz);

      if (notep->n_type == NT_PRSTATUS) {
         if (core_handle_prstatus(ph, descdata, notep->n_descsz) != true)
            return false;
      }
      p = descdata + ROUNDUP(notep->n_descsz, 4);
   }

   free(buf);
   return true;

err:
   if (buf) free(buf);
   return false;
}

// read all segments from core file
static bool read_core_segments(struct ps_prochandle* ph, ELF_EHDR* core_ehdr) {
   int i = 0;
   ELF_PHDR* phbuf = NULL;
   ELF_PHDR* core_php = NULL;

   if ((phbuf =  read_program_header_table(ph->core->core_fd, core_ehdr)) == NULL)
      return false;

   /*
    * Now iterate through the program headers in the core file.
    * We're interested in two types of Phdrs: PT_NOTE (which
    * contains a set of saved /proc structures), and PT_LOAD (which
    * represents a memory mapping from the process's address space).
    *
    * Difference b/w Solaris PT_NOTE and Linux PT_NOTE:
    *
    *     In Solaris there are two PT_NOTE segments the first PT_NOTE (if present)
    *     contains /proc structs in the pre-2.6 unstructured /proc format. the last
    *     PT_NOTE has data in new /proc format.
    *
    *     In Solaris, there is only one pstatus (process status). pstatus contains
    *     integer register set among other stuff. For each LWP, we have one lwpstatus
    *     entry that has integer regset for that LWP.
    *
    *     Linux threads are actually 'clone'd processes. To support core analysis
    *     of "multithreaded" process, Linux creates more than one pstatus (called
    *     "prstatus") entry in PT_NOTE. Each prstatus entry has integer regset for one
    *     "thread". Please refer to Linux kernel src file 'fs/binfmt_elf.c', in particular
    *     function "elf_core_dump".
    */

    for (core_php = phbuf, i = 0; i < core_ehdr->e_phnum; i++) {
      switch (core_php->p_type) {
         case PT_NOTE:
            if (core_handle_note(ph, core_php) != true) goto err;
            break;

         case PT_LOAD: {
            if (core_php->p_filesz != 0) {
               if (add_map_info(ph, ph->core->core_fd, core_php->p_offset,
                  core_php->p_vaddr, core_php->p_filesz) == NULL) goto err;
            }
            break;
         }
      }

      core_php++;
   }

   free(phbuf);
   return true;
err:
   free(phbuf);
   return false;
}

// read segments of a shared object
static bool read_lib_segments(struct ps_prochandle* ph, int lib_fd, ELF_EHDR* lib_ehdr, uintptr_t lib_base) {
   int i = 0;
   ELF_PHDR* phbuf;
   ELF_PHDR* lib_php = NULL;

   if ((phbuf = read_program_header_table(lib_fd, lib_ehdr)) == NULL)
      return false;

   // we want to process only PT_LOAD segments that are not writable.
   // i.e., text segments. The read/write/exec (data) segments would
   // have been already added from core file segments.
   for (lib_php = phbuf, i = 0; i < lib_ehdr->e_phnum; i++) {
      if ((lib_php->p_type == PT_LOAD) && !(lib_php->p_flags & PF_W) && (lib_php->p_filesz != 0)) {
         if (add_map_info(ph, lib_fd, lib_php->p_offset, lib_php->p_vaddr + lib_base, lib_php->p_filesz) == NULL)
            goto err;
      }
      lib_php++;
   }

   free(phbuf);
   return true;
err:
   free(phbuf);
   return false;
}

// process segments from interpreter (ld.so or ld-linux.so)
static bool read_interp_segments(struct ps_prochandle* ph) {
   ELF_EHDR interp_ehdr;

   if (read_elf_header(ph->core->interp_fd, &interp_ehdr) != true) {
       print_debug("interpreter is not a valid ELF file\n");
       return false;
   }

   if (read_lib_segments(ph, ph->core->interp_fd, &interp_ehdr, ph->core->ld_base_addr) != true) {
       print_debug("can't read segments of interpreter\n");
       return false;
   }

   return true;
}

// process segments of a a.out
static bool read_exec_segments(struct ps_prochandle* ph, ELF_EHDR* exec_ehdr) {
   int i = 0;
   ELF_PHDR* phbuf = NULL;
   ELF_PHDR* exec_php = NULL;

   if ((phbuf = read_program_header_table(ph->core->exec_fd, exec_ehdr)) == NULL)
      return false;

   for (exec_php = phbuf, i = 0; i < exec_ehdr->e_phnum; i++) {
      switch (exec_php->p_type) {

         // add mappings for PT_LOAD segments
         case PT_LOAD: {
            // add only non-writable segments of non-zero filesz
            if (!(exec_php->p_flags & PF_W) && exec_php->p_filesz != 0) {
               if (add_map_info(ph, ph->core->exec_fd, exec_php->p_offset, exec_php->p_vaddr, exec_php->p_filesz) == NULL) goto err;
            }
            break;
         }

         // read the interpreter and it's segments
         case PT_INTERP: {
            char interp_name[BUF_SIZE];

            pread(ph->core->exec_fd, interp_name, MIN(exec_php->p_filesz, BUF_SIZE), exec_php->p_offset);
            print_debug("ELF interpreter %s\n", interp_name);
            // read interpreter segments as well
            if ((ph->core->interp_fd = pathmap_open(interp_name)) < 0) {
               print_debug("can't open runtime loader\n");
               goto err;
            }
            break;
         }

         // from PT_DYNAMIC we want to read address of first link_map addr
         case PT_DYNAMIC: {
            ph->core->dynamic_addr = exec_php->p_vaddr;
            print_debug("address of _DYNAMIC is 0x%lx\n", ph->core->dynamic_addr);
            break;
         }

      } // switch
      exec_php++;
   } // for

   free(phbuf);
   return true;
err:
   free(phbuf);
   return false;
}


#define FIRST_LINK_MAP_OFFSET offsetof(struct r_debug,  r_map)
#define LD_BASE_OFFSET        offsetof(struct r_debug,  r_ldbase)
#define LINK_MAP_ADDR_OFFSET  offsetof(struct link_map, l_addr)
#define LINK_MAP_NAME_OFFSET  offsetof(struct link_map, l_name)
#define LINK_MAP_NEXT_OFFSET  offsetof(struct link_map, l_next)

// read shared library info from runtime linker's data structures.
// This work is done by librtlb_db in Solaris
static bool read_shared_lib_info(struct ps_prochandle* ph) {
   uintptr_t addr = ph->core->dynamic_addr;
   uintptr_t debug_base;
   uintptr_t first_link_map_addr;
   uintptr_t ld_base_addr;
   uintptr_t link_map_addr;
   uintptr_t lib_base_diff;
   uintptr_t lib_base;
   uintptr_t lib_name_addr;
   char lib_name[BUF_SIZE];
   ELF_DYN dyn;
   ELF_EHDR elf_ehdr;
   int lib_fd;

   // _DYNAMIC has information of the form
   //         [tag] [data] [tag] [data] .....
   // Both tag and data are pointer sized.
   // We look for dynamic info with DT_DEBUG. This has shared object info.
   // refer to struct r_debug in link.h

   dyn.d_tag = DT_NULL;
   while (dyn.d_tag != DT_DEBUG) {
      if (ps_pdread(ph, (psaddr_t) addr, &dyn, sizeof(ELF_DYN)) != PS_OK) {
         print_debug("can't read debug info from _DYNAMIC\n");
         return false;
      }
      addr += sizeof(ELF_DYN);
   }

   // we have got Dyn entry with DT_DEBUG
   debug_base = dyn.d_un.d_ptr;
   // at debug_base we have struct r_debug. This has first link map in r_map field
   if (ps_pdread(ph, (psaddr_t) debug_base + FIRST_LINK_MAP_OFFSET,
                 &first_link_map_addr, sizeof(uintptr_t)) != PS_OK) {
      print_debug("can't read first link map address\n");
      return false;
   }

   // read ld_base address from struct r_debug
   if (ps_pdread(ph, (psaddr_t) debug_base + LD_BASE_OFFSET, &ld_base_addr,
                 sizeof(uintptr_t)) != PS_OK) {
      print_debug("can't read ld base address\n");
      return false;
   }
   ph->core->ld_base_addr = ld_base_addr;

   print_debug("interpreter base address is 0x%lx\n", ld_base_addr);

   // now read segments from interp (i.e ld.so or ld-linux.so)
   if (read_interp_segments(ph) != true)
      return false;

   // after adding interpreter (ld.so) mappings sort again
   if (sort_map_array(ph) != true)
      return false;

   print_debug("first link map is at 0x%lx\n", first_link_map_addr);

   link_map_addr = first_link_map_addr;
   while (link_map_addr != 0) {
      // read library base address of the .so. Note that even though <sys/link.h> calls
      // link_map->l_addr as "base address",  this is * not * really base virtual
      // address of the shared object. This is actually the difference b/w the virtual
      // address mentioned in shared object and the actual virtual base where runtime
      // linker loaded it. We use "base diff" in read_lib_segments call below.

      if (ps_pdread(ph, (psaddr_t) link_map_addr + LINK_MAP_ADDR_OFFSET,
                   &lib_base_diff, sizeof(uintptr_t)) != PS_OK) {
         print_debug("can't read shared object base address diff\n");
         return false;
      }

      // read address of the name
      if (ps_pdread(ph, (psaddr_t) link_map_addr + LINK_MAP_NAME_OFFSET,
                    &lib_name_addr, sizeof(uintptr_t)) != PS_OK) {
         print_debug("can't read address of shared object name\n");
         return false;
      }

      // read name of the shared object
      if (read_string(ph, (uintptr_t) lib_name_addr, lib_name, sizeof(lib_name)) != true) {
         print_debug("can't read shared object name\n");
         return false;
      }

      if (lib_name[0] != '\0') {
         // ignore empty lib names
         lib_fd = pathmap_open(lib_name);

         if (lib_fd < 0) {
            print_debug("can't open shared object %s\n", lib_name);
            // continue with other libraries...
         } else {
            if (read_elf_header(lib_fd, &elf_ehdr)) {
               lib_base = lib_base_diff + find_base_address(lib_fd, &elf_ehdr);
               print_debug("reading library %s @ 0x%lx [ 0x%lx ]\n",
                           lib_name, lib_base, lib_base_diff);
               // while adding library mappings we need to use "base difference".
               if (! read_lib_segments(ph, lib_fd, &elf_ehdr, lib_base_diff)) {
                  print_debug("can't read shared object's segments\n");
                  close(lib_fd);
                  return false;
               }
               add_lib_info_fd(ph, lib_name, lib_fd, lib_base);
               // Map info is added for the library (lib_name) so
               // we need to re-sort it before calling the p_pdread.
               if (sort_map_array(ph) != true)
                  return false;
            } else {
               print_debug("can't read ELF header for shared object %s\n", lib_name);
               close(lib_fd);
               // continue with other libraries...
            }
         }
      }

      // read next link_map address
      if (ps_pdread(ph, (psaddr_t) link_map_addr + LINK_MAP_NEXT_OFFSET,
                        &link_map_addr, sizeof(uintptr_t)) != PS_OK) {
         print_debug("can't read next link in link_map\n");
         return false;
      }
   }

   return true;
}

// the one and only one exposed stuff from this file
struct ps_prochandle* Pgrab_core(const char* exec_file, const char* core_file) {
   ELF_EHDR core_ehdr;
   ELF_EHDR exec_ehdr;
   ELF_EHDR lib_ehdr;

   struct ps_prochandle* ph = (struct ps_prochandle*) calloc(1, sizeof(struct ps_prochandle));
   if (ph == NULL) {
      print_debug("can't allocate ps_prochandle\n");
      return NULL;
   }

   if ((ph->core = (struct core_data*) calloc(1, sizeof(struct core_data))) == NULL) {
      free(ph);
      print_debug("can't allocate ps_prochandle\n");
      return NULL;
   }

   // initialize ph
   ph->ops = &core_ops;
   ph->core->core_fd   = -1;
   ph->core->exec_fd   = -1;
   ph->core->interp_fd = -1;

   // open the core file
   if ((ph->core->core_fd = open(core_file, O_RDONLY)) < 0) {
      print_debug("can't open core file\n");
      goto err;
   }

   // read core file ELF header
   if (read_elf_header(ph->core->core_fd, &core_ehdr) != true || core_ehdr.e_type != ET_CORE) {
      print_debug("core file is not a valid ELF ET_CORE file\n");
      goto err;
   }

   if ((ph->core->exec_fd = open(exec_file, O_RDONLY)) < 0) {
      print_debug("can't open executable file\n");
      goto err;
   }

   if (read_elf_header(ph->core->exec_fd, &exec_ehdr) != true || exec_ehdr.e_type != ET_EXEC) {
      print_debug("executable file is not a valid ELF ET_EXEC file\n");
      goto err;
   }

   // process core file segments
   if (read_core_segments(ph, &core_ehdr) != true)
      goto err;

   // process exec file segments
   if (read_exec_segments(ph, &exec_ehdr) != true)
      goto err;

   // exec file is also treated like a shared object for symbol search
   if (add_lib_info_fd(ph, exec_file, ph->core->exec_fd,
                       (uintptr_t)0 + find_base_address(ph->core->exec_fd, &exec_ehdr)) == NULL)
      goto err;

   // allocate and sort maps into map_array, we need to do this
   // here because read_shared_lib_info needs to read from debuggee
   // address space
   if (sort_map_array(ph) != true)
      goto err;

   if (read_shared_lib_info(ph) != true)
      goto err;

   // sort again because we have added more mappings from shared objects
   if (sort_map_array(ph) != true)
      goto err;

   if (init_classsharing_workaround(ph) != true)
      goto err;

   return ph;

err:
   Prelease(ph);
   return NULL;
}
