/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * The Serviceability Agent's live-process side for the BSDs other than
 * macOS.  macosx has its own, written against mach_vm_read and
 * task_for_pid; those calls do not exist here, and NetBSD's ptrace(2) is
 * closer to Linux's, so this follows LinuxDebuggerLocal.cpp instead:
 *
 *   PT_ATTACH / PT_DETACH        as on Linux
 *   PT_LWPNEXT                   walks the threads.  Linux reads
 *                                /proc/<pid>/task; there is no such
 *                                directory here, and a ptrace request is
 *                                the documented way
 *   PT_IO with PIOD_READ_D       reads the target's memory in one call.
 *                                Linux uses PTRACE_PEEKDATA a word at a
 *                                time
 *   PT_GETREGS                   fills struct reg, whose members are the
 *                                _REG_* indices of <machine/mcontext.h>
 *
 * Symbols are a separate problem.  Linux reads /proc/<pid>/maps to learn
 * what is mapped where; there is no such file here, so the mappings come
 * from kinfo_getvmmap(3), which libutil provides on NetBSD and FreeBSD
 * alike and which reports the same path-and-range triples.  The symbol
 * tables themselves are then read out of the files on disk rather than
 * out of the target, exactly as Linux's symtab.c does: the tables are not
 * loaded at run time, so the target has no copy to read.
 *
 * Only the live-process case is handled.  Core files go through the
 * shared elf reader, which needs no per-OS code, and are left for later.
 */

#include <jni.h>

#include <sys/types.h>
#include <sys/mman.h>
#include <sys/ptrace.h>
#include <sys/stat.h>
#include <sys/sysctl.h>
#ifdef __FreeBSD__
/* FreeBSD keeps struct kinfo_vmentry here rather than in <sys/sysctl.h>. */
#include <sys/user.h>
#endif
#include <sys/wait.h>
#include <machine/reg.h>
#ifdef __NetBSD__
/* _REG_RAX and the rest of the indices into struct reg live here. */
#include <machine/mcontext.h>
#endif

#include <elf.h>
#include <stdint.h>
#include <errno.h>
#include <link.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#ifdef __FreeBSD__
#include <libutil.h>
#else
#include <util.h>
#endif

/*
 * __cxa_demangle has C linkage even though it belongs to the C++ runtime,
 * which is why libsaproc is linked with the C++ driver here as it is on
 * Linux.  <cxxabi.h> itself is a C++ header, so the declaration is spelled
 * out rather than included.
 */
extern char* __cxa_demangle(const char* mangled, char* buf, size_t* len,
                            int* status);

#include "sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal.h"
#include "sun_jvm_hotspot_debugger_amd64_AMD64ThreadContext.h"

/*
 * NetBSD and FreeBSD only.
 *
 * OpenBSD's kinfo_vmentry carries no kve_path, so there is no way to learn
 * which file is mapped where, and without that there are no load objects and
 * no symbols -- the agent cannot get started.  Its struct reg also has
 * neither r_trapno nor r_err.  DragonFly is untried.  Both get the same
 * empty libsaproc they had before, rather than a half of one that claims to
 * work.
 */
#if defined(__NetBSD__) || defined(__FreeBSD__)

#define CHECK_EXCEPTION_(value) if ((*env)->ExceptionOccurred(env)) { return value; }
#define CHECK_EXCEPTION if ((*env)->ExceptionOccurred(env)) { return; }
#define THROW_NEW_DEBUGGER_EXCEPTION_(str, value) { \
    throw_new_debugger_exception(env, str); return value; }
#define THROW_NEW_DEBUGGER_EXCEPTION(str) { \
    throw_new_debugger_exception(env, str); return; }

static jfieldID p_ps_prochandle_ID = 0;
static jfieldID loadObjectList_ID = 0;
static jmethodID createClosestSymbol_ID = 0;
static jmethodID createLoadObject_ID = 0;
static jmethodID listAdd_ID = 0;

static void throw_new_debugger_exception(JNIEnv* env, const char* errMsg) {
  jclass clazz = (*env)->FindClass(env, "sun/jvm/hotspot/debugger/DebuggerException");
  CHECK_EXCEPTION;
  (*env)->ThrowNew(env, clazz, errMsg);
}

/*
 * One file mapped into the target.  base is the address the file's own
 * vaddr 0 would sit at, so a link-time value from its symbol table
 * becomes a runtime one by adding base and taking off the lowest vaddr
 * the file's PT_LOAD headers name.
 */
struct map_info;

/* OpenBSD names a thread by the id getthrid() returns and declares no
   lwpid_t; NetBSD and FreeBSD both have one. */
#ifdef __OpenBSD__
typedef int sa_lwpid_t;
#else
typedef lwpid_t sa_lwpid_t;
#endif

typedef struct seg_info {
  uintptr_t        vaddr;   /* link-time address of the segment */
  size_t           filesz;
  off_t            offset;
  struct seg_info *next;
} seg_info;

/*
 * base and end are the outermost bounds, which is what a load object is
 * described by; ranges is what the object is actually mapped over.  The two
 * are not the same and the difference matters: ld.elf_so puts other objects
 * into the gap between an object's segments, so libjimage, libgcc, libm and
 * libstdc++ all sat inside libjvm's bounds in one ordinary run.  Deciding
 * which object an address belongs to by the bounds picks the wrong one.
 */
typedef struct lib_info {
  char             *name;
  uintptr_t         base;
  uintptr_t         end;
  int               fd;     /* open while reading a core; -1 for a live target */
  seg_info         *segs;
  struct map_info  *ranges;
  struct lib_info  *next;
} lib_info;

/* One PT_LOAD of a core: where a stretch of the target's address space was
   written into the file.  filesz is short of memsz wherever the kernel had
   nothing to save -- bss, and the file-backed read-only pages it leaves out
   because the file itself still has them. */
typedef struct map_info {
  uintptr_t        vaddr;
  size_t           memsz;
  size_t           filesz;
  off_t            offset;
  struct map_info *next;
} map_info;

typedef struct thread_info {
  sa_lwpid_t          lwpid;
  struct reg          regs;
  struct thread_info *next;
} thread_info;

struct ps_prochandle {
  pid_t        pid;       /* 0 for a core */
  int          core_fd;   /* -1 for a live process */
  map_info    *maps;
  thread_info *threads;
  lib_info    *libs;
};

static struct ps_prochandle* get_proc(JNIEnv* env, jobject this_obj) {
  return (struct ps_prochandle*)(intptr_t)
      (*env)->GetLongField(env, this_obj, p_ps_prochandle_ID);
}

static const char* base_name(const char* path) {
  const char* slash = strrchr(path, '/');
  return slash == NULL ? path : slash + 1;
}

static void add_range(lib_info* lib, uintptr_t start, size_t len) {
  map_info* r = (map_info*)calloc(1, sizeof(*r));

  if (r == NULL) {
    return;
  }
  r->vaddr = start;
  r->memsz = len;
  r->next = lib->ranges;
  lib->ranges = r;
}

/*
 * ld.elf_so maps one more page of each object -- its first -- well away
 * from the rest, so the outermost of an object's mappings reaches across
 * everything the loader put in between:
 *
 *   0x7050ef1bc000-0x7050f0785000  libjvm.so     the object itself
 *   0x7050f0e6a000-0x7050f0e72000  libjimage.so
 *   0x7050f0f6b000-0x7050f118b000  libstdc++.so
 *   0x7050f1190000-0x7050f1191000  libjvm.so     the stray page
 *
 * Describing libjvm as reaching to the stray page makes it contain three
 * objects it has nothing to do with, and the agent's binary search over
 * those bounds then answers with the wrong file -- an address in the C
 * heap came back as "libjvm.so + 0x1cb8ffc".  The bounds are the run that
 * starts at the object's own base and stays contiguous.
 */
static void set_bounds_to_contiguous_run(lib_info* lib) {
  map_info* r;
  uintptr_t start = (uintptr_t)-1;
  uintptr_t end;
  int extended;

  for (r = lib->ranges; r != NULL; r = r->next) {
    if (r->vaddr < start) {
      start = r->vaddr;
    }
  }
  if (start == (uintptr_t)-1) {
    return;
  }
  end = start;
  do {
    extended = 0;
    for (r = lib->ranges; r != NULL; r = r->next) {
      if (r->vaddr <= end && r->vaddr + r->memsz > end) {
        end = r->vaddr + r->memsz;
        extended = 1;
      }
    }
  } while (extended);
  lib->end = end;
}

static int lib_contains(lib_info* lib, uintptr_t addr) {
  map_info* r;

  for (r = lib->ranges; r != NULL; r = r->next) {
    if (addr >= r->vaddr && addr < r->vaddr + r->memsz) {
      return 1;
    }
  }
  return 0;
}

/* ---------------------------------------------------------------- ELF */

static int map_file(const char* path, char** addr, size_t* size) {
  struct stat st;
  void* p;
  int fd = open(path, O_RDONLY);

  if (fd < 0) {
    return -1;
  }
  if (fstat(fd, &st) < 0 || (size_t)st.st_size < sizeof(Elf64_Ehdr)) {
    close(fd);
    return -1;
  }
  p = mmap(NULL, (size_t)st.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
  close(fd);
  if (p == MAP_FAILED) {
    return -1;
  }
  *addr = (char*)p;
  *size = (size_t)st.st_size;
  return 0;
}

/* Rejects anything that is not an ELF of this machine's own class. */
static const Elf64_Ehdr* elf_header(const char* addr, size_t size) {
  const Elf64_Ehdr* eh = (const Elf64_Ehdr*)addr;

  if (size < sizeof(*eh) || memcmp(eh->e_ident, ELFMAG, SELFMAG) != 0) {
    return NULL;
  }
  if (eh->e_ident[EI_CLASS] != ELFCLASS64) {
    return NULL;
  }
  if (eh->e_shoff == 0 ||
      eh->e_shoff + (size_t)eh->e_shnum * eh->e_shentsize > size) {
    return NULL;
  }
  return eh;
}

/* Lowest vaddr any PT_LOAD asks for; 0 for the shared objects. */
static uintptr_t elf_vaddr0(const char* addr, size_t size,
                            const Elf64_Ehdr* eh) {
  uintptr_t lowest = (uintptr_t)-1;
  Elf64_Half i;

  if (eh->e_phoff + (size_t)eh->e_phnum * eh->e_phentsize > size) {
    return 0;
  }
  for (i = 0; i < eh->e_phnum; i++) {
    const Elf64_Phdr* ph =
        (const Elf64_Phdr*)(addr + eh->e_phoff + (size_t)i * eh->e_phentsize);
    if (ph->p_type == PT_LOAD && (uintptr_t)ph->p_vaddr < lowest) {
      lowest = (uintptr_t)ph->p_vaddr;
    }
  }
  return lowest == (uintptr_t)-1 ? 0 : lowest;
}

/*
 * Both .symtab and .dynsym are walked.  A stripped libjvm.so keeps only
 * the latter, and the agent asks for names -- gHotSpotVMStructs and the
 * rest -- that are exported either way; searching both means one code
 * path serves a product build and a debug one.
 */
typedef int (*sym_visitor)(const char* name, const Elf64_Sym* sym, void* arg);

static void elf_walk_symbols(const char* addr, size_t size,
                             const Elf64_Ehdr* eh,
                             sym_visitor visit, void* arg) {
  Elf64_Half i;

  for (i = 0; i < eh->e_shnum; i++) {
    const Elf64_Shdr* sh =
        (const Elf64_Shdr*)(addr + eh->e_shoff + (size_t)i * eh->e_shentsize);
    const Elf64_Shdr* strh;
    const char* strtab;
    size_t n, j;

    if (sh->sh_type != SHT_SYMTAB && sh->sh_type != SHT_DYNSYM) {
      continue;
    }
    if (sh->sh_link >= eh->e_shnum || sh->sh_entsize == 0) {
      continue;
    }
    if (sh->sh_offset + sh->sh_size > size) {
      continue;
    }
    strh = (const Elf64_Shdr*)(addr + eh->e_shoff +
                               (size_t)sh->sh_link * eh->e_shentsize);
    if (strh->sh_offset + strh->sh_size > size) {
      continue;
    }
    strtab = addr + strh->sh_offset;
    n = (size_t)(sh->sh_size / sh->sh_entsize);
    for (j = 0; j < n; j++) {
      const Elf64_Sym* sym =
          (const Elf64_Sym*)(addr + sh->sh_offset + j * sh->sh_entsize);
      if (sym->st_name == 0 || sym->st_name >= strh->sh_size) {
        continue;
      }
      if (visit(strtab + sym->st_name, sym, arg) != 0) {
        return;
      }
    }
  }
}

struct find_name {
  const char* want;
  uintptr_t   value;
  int         found;
};

static int visit_name(const char* name, const Elf64_Sym* sym, void* arg) {
  struct find_name* f = (struct find_name*)arg;

  if (sym->st_shndx == SHN_UNDEF || strcmp(name, f->want) != 0) {
    return 0;
  }
  f->value = (uintptr_t)sym->st_value;
  f->found = 1;
  return 1;
}

struct find_addr {
  uintptr_t target;    /* link-time vaddr being looked for */
  uintptr_t best;
  char      name[256];
  int       found;
};

static int visit_addr(const char* name, const Elf64_Sym* sym, void* arg) {
  struct find_addr* f = (struct find_addr*)arg;
  unsigned char type = ELF64_ST_TYPE(sym->st_info);

  if (sym->st_shndx == SHN_UNDEF || sym->st_value == 0) {
    return 0;
  }
  if (type != STT_FUNC && type != STT_OBJECT) {
    return 0;
  }
  if ((uintptr_t)sym->st_value > f->target) {
    return 0;
  }
  /*
   * A symbol whose size is known and which the address falls past is not
   * the answer, however close it is; the address belongs to whatever
   * comes after, or to no symbol at all.
   */
  if (sym->st_size != 0 &&
      f->target >= (uintptr_t)sym->st_value + (uintptr_t)sym->st_size) {
    return 0;
  }
  if (!f->found || (uintptr_t)sym->st_value > f->best) {
    f->best = (uintptr_t)sym->st_value;
    f->found = 1;
    snprintf(f->name, sizeof(f->name), "%s", name);
  }
  return 0;
}

#if defined(__NetBSD__) || defined(__FreeBSD__)

#ifdef __FreeBSD__
/*
 * A FreeBSD core names all of its notes "FreeBSD" and tells them apart by
 * type: one NT_PRSTATUS per thread carrying a prstatus_t, and one
 * NT_PROCSTAT_AUXV for the process.  Measured on 15.1/amd64, prstatus_t is
 * 224 bytes with pr_pid at 40 and pr_reg -- a struct reg -- at 48.
 */
#define CORE_NOTE_NAME    "FreeBSD"
#define CORE_NOTE_AUXV    NT_PROCSTAT_AUXV
#define CORE_NOTE_REGS    NT_PRSTATUS
#define PRSTATUS_PR_PID   40
#define PRSTATUS_PR_REG   48
#define PRSTATUS_SIZE     224
#else
#define CORE_NOTE_NAME    ELF_NOTE_NETBSD_CORE_NAME
#define CORE_NOTE_AUXV    ELF_NOTE_NETBSD_CORE_AUXV
#define CORE_NOTE_REGS    PT_GETREGS
#endif

/*
 * An auxv entry is two 64-bit words on both, but the types do not agree on a
 * name: NetBSD's AuxInfo calls the second one a_v and FreeBSD's Elf64_Auxinfo
 * reaches it through a union.  Copy into one of our own instead.
 */
typedef struct {
  uint64_t a_type;
  uint64_t a_val;
} core_auxv_t;

static int is_core_auxv_note(const Elf64_Nhdr* nh, const char* name) {
  return nh->n_type == CORE_NOTE_AUXV &&
         nh->n_namesz >= sizeof(CORE_NOTE_NAME) &&
         strncmp(name, CORE_NOTE_NAME, sizeof(CORE_NOTE_NAME)) == 0;
}

/*
 * AT_PHDR, AT_PHENT and AT_PHNUM are how the executable's own program
 * headers are found in a core: they say where the headers ended up, which
 * with PT_PHDR gives the bias everything else is measured from.
 */
static void find_core_auxv(const char* notes, size_t size, uintptr_t* phdr,
                           uintptr_t* phent, uintptr_t* phnum) {
  size_t off = 0;

  while (off + sizeof(Elf64_Nhdr) <= size) {
    const Elf64_Nhdr* nh = (const Elf64_Nhdr*)(notes + off);
    size_t namesz = (nh->n_namesz + 3) & ~(size_t)3;
    size_t descsz = (nh->n_descsz + 3) & ~(size_t)3;
    const char* name = notes + off + sizeof(*nh);
    const char* av;
    size_t n, j;

    if (off + sizeof(*nh) + namesz + descsz > size) {
      return;
    }
    av = name + namesz;
    n = nh->n_descsz / sizeof(core_auxv_t);
    off += sizeof(*nh) + namesz + descsz;

    if (!is_core_auxv_note(nh, name)) {
      continue;
    }
#ifdef __FreeBSD__
    /*
     * A FreeBSD NT_PROCSTAT_AUXV descriptor opens with the size of one
     * entry, and the array follows.  That leaves it four bytes out of
     * alignment, so it is copied rather than cast.
     */
    av += sizeof(uint32_t);
    n = (nh->n_descsz - sizeof(uint32_t)) / sizeof(core_auxv_t);
#endif
    for (j = 0; j < n; j++) {
      core_auxv_t one;
      memcpy(&one, av + j * sizeof(one), sizeof(one));
      switch (one.a_type) {
        case AT_PHDR:  *phdr  = (uintptr_t)one.a_val; break;
        case AT_PHENT: *phent = (uintptr_t)one.a_val; break;
        case AT_PHNUM: *phnum = (uintptr_t)one.a_val; break;
        default: break;
      }
    }
    return;
  }
}

#endif /* __NetBSD__ || __FreeBSD__ */

#if defined(__NetBSD__) || defined(__FreeBSD__)
/* ---------------------------------------------------------------- core */

/*
 * A core here is an ELF whose PT_LOADs carry the writable pages and whose
 * PT_NOTEs carry the registers, in one of two arrangements.  NetBSD writes
 * one "NetBSD-CORE" note per process and a set named "NetBSD-CORE@<lwpid>"
 * per thread, the PT_GETREGS one of which holds a struct reg.  FreeBSD
 * names every note "FreeBSD" and writes one NT_PRSTATUS per thread, with
 * the thread's id inside the descriptor instead of in the note's name.
 * Either way what comes out is the struct reg this file already knows how
 * to unpack.
 *
 * The read-only pages are not in the file: the kernel leaves them out
 * because the mapped file still has them, and their PT_LOAD says filesz 0.
 * So a read that the core cannot answer is not an error -- it is a read
 * that has to go to the object on disk instead, which is why every load
 * object is kept open with the file offsets of its own segments.
 */

static int core_pread(int fd, void* buf, size_t len, off_t off) {
  char* p = (char*)buf;

  while (len > 0) {
    ssize_t n = pread(fd, p, len, off);
    if (n <= 0) {
      return -1;
    }
    p += n;
    off += n;
    len -= (size_t)n;
  }
  return 0;
}

/* The part of a read that the object on disk can answer, or -1. */
static int lib_read(struct ps_prochandle* ph, uintptr_t addr, void* buf, size_t len) {
  lib_info* lib;

  for (lib = ph->libs; lib != NULL; lib = lib->next) {
    seg_info* sg;
    if (lib->fd < 0 || !lib_contains(lib, addr)) {
      continue;
    }
    for (sg = lib->segs; sg != NULL; sg = sg->next) {
      uintptr_t start = lib->base + sg->vaddr;
      if (addr >= start && addr + len <= start + sg->filesz) {
        return core_pread(lib->fd, buf, len, sg->offset + (off_t)(addr - start));
      }
    }
  }
  return -1;
}

/*
 * Reads out of the core, out of the object on disk, or as zeroes,
 * whichever the address calls for -- and a single read can need more than
 * one of them, because the agent reads a page at a time and a PT_LOAD's
 * filesz stops at the last byte the kernel had anything to say about.
 * A page that straddles that end is the ordinary case, not an error.
 */
static int core_read(struct ps_prochandle* ph, uintptr_t addr, void* buf, size_t len) {
  size_t done = 0;

  while (done < len) {
    uintptr_t a = addr + done;
    size_t left = len - done;
    char* dst = (char*)buf + done;
    map_info* m;
    size_t in, n;

    for (m = ph->maps; m != NULL; m = m->next) {
      if (a >= m->vaddr && a < m->vaddr + m->memsz) {
        break;
      }
    }
    if (m == NULL) {
      /* Outside every PT_LOAD: only a mapped file can answer. */
      if (lib_read(ph, a, dst, left) != 0) {
        return -1;
      }
      return 0;
    }

    in = a - m->vaddr;
    if (in < m->filesz) {
      n = m->filesz - in;
      if (n > left) {
        n = left;
      }
      if (core_pread(ph->core_fd, dst, n, m->offset + (off_t)in) != 0) {
        return -1;
      }
    } else {
      /*
       * Past what the core holds.  Either the page was file-backed and
       * unmodified, so the file still has it, or it was anonymous and
       * empty, so zeroes are what was there.
       */
      n = m->memsz - in;
      if (n > left) {
        n = left;
      }
      if (lib_read(ph, a, dst, n) != 0) {
        memset(dst, 0, n);
      }
    }
    done += n;
  }
  return 0;
}

static void add_map(struct ps_prochandle* ph, const Elf64_Phdr* ph_ent) {
  map_info* m = (map_info*)calloc(1, sizeof(*m));

  if (m == NULL) {
    return;
  }
  m->vaddr = (uintptr_t)ph_ent->p_vaddr;
  m->memsz = (size_t)ph_ent->p_memsz;
  m->filesz = (size_t)ph_ent->p_filesz;
  m->offset = (off_t)ph_ent->p_offset;
  m->next = ph->maps;
  ph->maps = m;
}

/*
 * Notes are laid out namesz/descsz/type followed by the two payloads, each
 * rounded up to four bytes -- both keep that alignment in 64-bit cores
 * too, so the ELF64 eight-byte rounding some systems use must not be
 * assumed here.
 */
static void parse_core_notes(struct ps_prochandle* ph, const char* notes, size_t size) {
  size_t off = 0;

  while (off + sizeof(Elf64_Nhdr) <= size) {
    const Elf64_Nhdr* nh = (const Elf64_Nhdr*)(notes + off);
    size_t namesz = (nh->n_namesz + 3) & ~(size_t)3;
    size_t descsz = (nh->n_descsz + 3) & ~(size_t)3;
    const char* name = notes + off + sizeof(*nh);
    const char* desc = name + namesz;
    unsigned long lwpid;

    if (off + sizeof(*nh) + namesz + descsz > size) {
      return;
    }
    off += sizeof(*nh) + namesz + descsz;

#ifdef __FreeBSD__
    /*
     * FreeBSD gives every note the same name and one NT_PRSTATUS per
     * thread, with the thread's id inside the descriptor rather than in
     * the note's name.
     */
    if (nh->n_type != CORE_NOTE_REGS || nh->n_descsz < PRSTATUS_SIZE ||
        nh->n_namesz < sizeof(CORE_NOTE_NAME) ||
        strncmp(name, CORE_NOTE_NAME, sizeof(CORE_NOTE_NAME)) != 0) {
      continue;
    }
    {
      int32_t pr_pid;
      memcpy(&pr_pid, desc + PRSTATUS_PR_PID, sizeof(pr_pid));
      lwpid = (unsigned long)pr_pid;
    }
    desc += PRSTATUS_PR_REG;
#else
    if (nh->n_namesz < sizeof(ELF_NOTE_NETBSD_CORE_NAME) ||
        strncmp(name, ELF_NOTE_NETBSD_CORE_NAME "@",
                sizeof(ELF_NOTE_NETBSD_CORE_NAME)) != 0) {
      continue;   /* the per-process notes carry nothing this needs */
    }
    if (nh->n_type != CORE_NOTE_REGS || nh->n_descsz < sizeof(struct reg)) {
      continue;
    }
    lwpid = strtoul(name + sizeof(ELF_NOTE_NETBSD_CORE_NAME), NULL, 10);
#endif
    if (lwpid != 0) {
      thread_info* t = (thread_info*)calloc(1, sizeof(*t));
      if (t != NULL) {
        t->lwpid = (sa_lwpid_t)lwpid;
        memcpy(&t->regs, desc, sizeof(t->regs));
        t->next = ph->threads;
        ph->threads = t;
      }
    }
  }
}

/*
 * Records one shared object: where it sits, and where in its own file each
 * of its segments came from, so that a read the core cannot answer can be
 * satisfied from disk.
 */
static void add_core_lib(struct ps_prochandle* ph, const char* path, uintptr_t l_addr) {
  char* addr;
  size_t size;
  const Elf64_Ehdr* eh;
  lib_info* lib;
  Elf64_Half i;
  uintptr_t lowest = (uintptr_t)-1, highest = 0;

  for (lib = ph->libs; lib != NULL; lib = lib->next) {
    if (strcmp(lib->name, path) == 0) {
      return;
    }
  }
  if (map_file(path, &addr, &size) != 0) {
    return;
  }
  if ((eh = elf_header(addr, size)) == NULL ||
      eh->e_phoff + (size_t)eh->e_phnum * eh->e_phentsize > size) {
    munmap(addr, size);
    return;
  }
  lib = (lib_info*)calloc(1, sizeof(*lib));
  if (lib == NULL) {
    munmap(addr, size);
    return;
  }
  lib->name = strdup(path);
  lib->fd = open(path, O_RDONLY);
  if (lib->name == NULL || lib->fd < 0) {
    if (lib->fd >= 0) {
      close(lib->fd);
    }
    free(lib->name);
    free(lib);
    munmap(addr, size);
    return;
  }

  for (i = 0; i < eh->e_phnum; i++) {
    const Elf64_Phdr* p =
        (const Elf64_Phdr*)(addr + eh->e_phoff + (size_t)i * eh->e_phentsize);
    seg_info* sg;
    if (p->p_type != PT_LOAD) {
      continue;
    }
    if ((uintptr_t)p->p_vaddr < lowest) {
      lowest = (uintptr_t)p->p_vaddr;
    }
    if ((uintptr_t)(p->p_vaddr + p->p_memsz) > highest) {
      highest = (uintptr_t)(p->p_vaddr + p->p_memsz);
    }
    sg = (seg_info*)calloc(1, sizeof(*sg));
    if (sg != NULL) {
      sg->vaddr = (uintptr_t)p->p_vaddr;
      sg->filesz = (size_t)p->p_filesz;
      sg->offset = (off_t)p->p_offset;
      sg->next = lib->segs;
      lib->segs = sg;
    }
    add_range(lib, l_addr + (uintptr_t)p->p_vaddr, (size_t)p->p_memsz);
  }
  munmap(addr, size);

  if (lowest == (uintptr_t)-1) {
    close(lib->fd);
    free(lib->name);
    free(lib);
    return;
  }
  /* l_addr is the bias the linker applied, so vaddr 0 of the file sits
     there and the lowest PT_LOAD sits that much further up. */
  lib->base = l_addr + lowest;
  lib->end = l_addr + highest;
  lib->next = ph->libs;
  ph->libs = lib;
}

/*
 * Walks the dynamic linker's list.  A core records no file names of
 * its own -- there is no equivalent of Linux's NT_FILE -- so the only place
 * the shared objects are named is the link map the process itself was
 * holding, reached through the DT_DEBUG entry of the executable.
 *
 * The executable's own program headers are read out of the file rather than
 * out of the core.  They sit in a read-only page, which the kernel leaves
 * out because the file still has it, and at this point no load object is
 * registered yet for the read to fall back on -- the very list this is
 * about to build.
 */
static void read_core_libs(struct ps_prochandle* ph, const char* execName,
                           uintptr_t phdr_addr) {
  char* addr;
  size_t size;
  const Elf64_Ehdr* eh;
  uintptr_t bias = 0, dyn_addr = 0;
  Elf64_Half i;
  size_t j;
  Elf64_Dyn dyn;
  uintptr_t r_debug_addr = 0;
  struct r_debug dbg;
  struct link_map lm;
  uintptr_t next;
  int guard = 0;

  if (map_file(execName, &addr, &size) != 0) {
    return;
  }
  if ((eh = elf_header(addr, size)) == NULL ||
      eh->e_phoff + (size_t)eh->e_phnum * eh->e_phentsize > size) {
    munmap(addr, size);
    return;
  }
  /*
   * AT_PHDR says where the headers ended up; PT_PHDR says where they were
   * linked to be.  The difference is the bias, and there is none in the
   * non-PIE case, where PT_PHDR is absent.
   */
  for (i = 0; i < eh->e_phnum; i++) {
    const Elf64_Phdr* p =
        (const Elf64_Phdr*)(addr + eh->e_phoff + (size_t)i * eh->e_phentsize);
    if (p->p_type == PT_PHDR) {
      bias = phdr_addr - (uintptr_t)p->p_vaddr;
    }
  }
  for (i = 0; i < eh->e_phnum; i++) {
    const Elf64_Phdr* p =
        (const Elf64_Phdr*)(addr + eh->e_phoff + (size_t)i * eh->e_phentsize);
    if (p->p_type == PT_DYNAMIC) {
      dyn_addr = bias + (uintptr_t)p->p_vaddr;
    }
  }
  munmap(addr, size);

  /* The executable goes in first, so that reads of it have a file to fall
     back on while the rest of the list is being found. */
  add_core_lib(ph, execName, bias);
  if (dyn_addr == 0) {
    return;
  }

  for (j = 0; j < 4096; j++) {
    if (core_read(ph, dyn_addr + j * sizeof(dyn), &dyn, sizeof(dyn)) != 0) {
      return;
    }
    if (dyn.d_tag == DT_NULL) {
      return;
    }
    if (dyn.d_tag == DT_DEBUG) {
      r_debug_addr = (uintptr_t)dyn.d_un.d_ptr;
      break;
    }
  }
  if (r_debug_addr == 0 ||
      core_read(ph, r_debug_addr, &dbg, sizeof(dbg)) != 0) {
    return;
  }

  next = (uintptr_t)dbg.r_map;
  while (next != 0 && guard++ < 1024) {
    char path[PATH_MAX];
    size_t n = 0;

    if (core_read(ph, next, &lm, sizeof(lm)) != 0) {
      return;
    }
    /* The first entry names the executable, and names it as the empty
       string; it is in the list already. */
    if (lm.l_name != NULL) {
      while (n + 1 < sizeof(path)) {
        if (core_read(ph, (uintptr_t)lm.l_name + n, &path[n], 1) != 0) {
          break;
        }
        if (path[n] == '\0') {
          break;
        }
        n++;
      }
    }
    path[n] = '\0';
    if (n > 0) {
      add_core_lib(ph, path, (uintptr_t)lm.l_addr);
    }
    next = (uintptr_t)lm.l_next;
  }
}

/* Returns 0 on success, leaving ph filled in. */
static int open_core(struct ps_prochandle* ph, const char* execName,
                     const char* coreName) {
  Elf64_Ehdr eh;
  Elf64_Phdr* phdrs;
  uintptr_t phdr_addr = 0, phent = 0, phnum = 0;   /* phent/phnum unused */
  Elf64_Half i;

  ph->core_fd = open(coreName, O_RDONLY);
  if (ph->core_fd < 0) {
    return -1;
  }
  if (core_pread(ph->core_fd, &eh, sizeof(eh), 0) != 0 ||
      memcmp(eh.e_ident, ELFMAG, SELFMAG) != 0 ||
      eh.e_ident[EI_CLASS] != ELFCLASS64 || eh.e_type != ET_CORE ||
      eh.e_phnum == 0 || eh.e_phentsize != sizeof(Elf64_Phdr)) {
    return -1;
  }
  phdrs = (Elf64_Phdr*)calloc(eh.e_phnum, sizeof(Elf64_Phdr));
  if (phdrs == NULL) {
    return -1;
  }
  if (core_pread(ph->core_fd, phdrs, (size_t)eh.e_phnum * sizeof(Elf64_Phdr),
                 (off_t)eh.e_phoff) != 0) {
    free(phdrs);
    return -1;
  }
  for (i = 0; i < eh.e_phnum; i++) {
    if (phdrs[i].p_type == PT_LOAD) {
      add_map(ph, &phdrs[i]);
    }
  }
  /* The notes come second: parse_core_notes needs nothing from the maps,
     but read_core_libs needs both, and the auxv is in a note. */
  for (i = 0; i < eh.e_phnum; i++) {
    char* notes;
    if (phdrs[i].p_type != PT_NOTE || phdrs[i].p_filesz == 0) {
      continue;
    }
    notes = (char*)malloc((size_t)phdrs[i].p_filesz);
    if (notes == NULL) {
      continue;
    }
    if (core_pread(ph->core_fd, notes, (size_t)phdrs[i].p_filesz,
                   (off_t)phdrs[i].p_offset) == 0) {
      parse_core_notes(ph, notes, (size_t)phdrs[i].p_filesz);
      find_core_auxv(notes, (size_t)phdrs[i].p_filesz, &phdr_addr, &phent, &phnum);
    }
    free(notes);
  }
  free(phdrs);

  (void)phent; (void)phnum;
  if (phdr_addr != 0) {
    read_core_libs(ph, execName, phdr_addr);
  }
  return 0;
}
#endif /* __NetBSD__ */

/* --------------------------------------------------------- load objects */

/* Runtime address of sym in lib, or 0 if lib does not define it. */
static jlong lookup_in_lib(lib_info* lib, const char* sym) {
  char* addr;
  size_t size;
  const Elf64_Ehdr* eh;
  struct find_name f;
  jlong result = 0;

  if (map_file(lib->name, &addr, &size) != 0) {
    return 0;
  }
  if ((eh = elf_header(addr, size)) != NULL) {
    memset(&f, 0, sizeof(f));
    f.want = sym;
    elf_walk_symbols(addr, size, eh, visit_name, &f);
    if (f.found) {
      result = (jlong)(lib->base - elf_vaddr0(addr, size, eh) + f.value);
    }
  }
  munmap(addr, size);
  return result;
}

/*
 * BsdCDebugger.loadObjectContainingPC() binary-searches the list it is
 * handed, so the order it arrives in is not a presentation detail: an
 * unsorted list makes findpc answer "In unknown location" for addresses
 * that are plainly inside a mapped object.
 */
static lib_info* sort_libs_by_base(lib_info* head) {
  lib_info* sorted = NULL;

  while (head != NULL) {
    lib_info* next = head->next;
    lib_info** slot = &sorted;

    while (*slot != NULL && (*slot)->base < head->base) {
      slot = &(*slot)->next;
    }
    head->next = *slot;
    *slot = head;
    head = next;
  }
  return sorted;
}

static void free_libs(lib_info* lib) {
  while (lib != NULL) {
    lib_info* next = lib->next;
    seg_info* sg = lib->segs;
    map_info* r = lib->ranges;
    while (sg != NULL) {
      seg_info* sgnext = sg->next;
      free(sg);
      sg = sgnext;
    }
    while (r != NULL) {
      map_info* rnext = r->next;
      free(r);
      r = rnext;
    }
    if (lib->fd >= 0) {
      close(lib->fd);
    }
    free(lib->name);
    free(lib);
    lib = next;
  }
}

static void free_proc(struct ps_prochandle* ph) {
  map_info* m = ph->maps;
  thread_info* t = ph->threads;

  while (m != NULL) {
    map_info* next = m->next;
    free(m);
    m = next;
  }
  while (t != NULL) {
    thread_info* next = t->next;
    free(t);
    t = next;
  }
  free_libs(ph->libs);
  if (ph->core_fd >= 0) {
    close(ph->core_fd);
  }
  free(ph);
}

static lib_info* find_lib(struct ps_prochandle* ph, const char* objectName) {
  lib_info* lib;

  for (lib = ph->libs; lib != NULL; lib = lib->next) {
    if (objectName == NULL || strcmp(lib->name, objectName) == 0 ||
        strcmp(base_name(lib->name), base_name(objectName)) == 0) {
      return lib;
    }
  }
  return NULL;
}

static void add_mapping(struct ps_prochandle* ph, const char* path,
                        uintptr_t start, uintptr_t end, uintptr_t offset) {
  lib_info* lib;

  for (lib = ph->libs; lib != NULL; lib = lib->next) {
    if (strcmp(lib->name, path) == 0) {
      /* A file arrives in several pieces; keep the outermost of them. */
      if (start - offset < lib->base) {
        lib->base = start - offset;
      }
      if (end > lib->end) {
        lib->end = end;
      }
      add_range(lib, start, (size_t)(end - start));
      return;
    }
  }
  lib = (lib_info*)calloc(1, sizeof(*lib));
  if (lib == NULL) {
    return;
  }
  lib->name = strdup(path);
  if (lib->name == NULL) {
    free(lib);
    return;
  }
  lib->base = start - offset;
  lib->end = end;
  lib->fd = -1;      /* a live target is read through ptrace, not the file */
  add_range(lib, start, (size_t)(end - start));
  lib->next = ph->libs;
  ph->libs = lib;
}

/*
 * The kernel names every mapping, including the ones backed by files that
 * are not objects at all -- a font, a jar opened with mmap.  Those are
 * dropped here rather than at lookup time so that the load object list
 * the agent hands back holds only things it can make sense of.
 */
static int is_elf_file(const char* path) {
  char magic[SELFMAG];
  int n;
  int fd = open(path, O_RDONLY);

  if (fd < 0) {
    return 0;
  }
  n = (int)read(fd, magic, sizeof(magic));
  close(fd);
  return n == (int)sizeof(magic) && memcmp(magic, ELFMAG, SELFMAG) == 0;
}

static void publish_load_objects(JNIEnv* env, jobject this_obj,
                                 struct ps_prochandle* ph);

static void fill_load_objects(JNIEnv* env, jobject this_obj,
                              struct ps_prochandle* ph) {
  struct kinfo_vmentry* vmmap;
  lib_info* lib;
#ifdef __FreeBSD__
  int nent = 0;
#else
  size_t nent = 0;
#endif
  size_t i;

  vmmap = kinfo_getvmmap(ph->pid, &nent);
  if (vmmap == NULL) {
    return;
  }
  for (i = 0; i < (size_t)nent; i++) {
    if (vmmap[i].kve_path[0] != '/' || !is_elf_file(vmmap[i].kve_path)) {
      continue;
    }
    add_mapping(ph, vmmap[i].kve_path, (uintptr_t)vmmap[i].kve_start,
                (uintptr_t)vmmap[i].kve_end, (uintptr_t)vmmap[i].kve_offset);
  }
  free(vmmap);

  for (lib = ph->libs; lib != NULL; lib = lib->next) {
    set_bounds_to_contiguous_run(lib);
  }
  ph->libs = sort_libs_by_base(ph->libs);
  publish_load_objects(env, this_obj, ph);
}

static void publish_load_objects(JNIEnv* env, jobject this_obj,
                                 struct ps_prochandle* ph) {
  lib_info* lib;
  jobject list = (*env)->GetObjectField(env, this_obj, loadObjectList_ID);

  if (list == NULL) {
    return;
  }
  for (lib = ph->libs; lib != NULL; lib = lib->next) {
    jstring name = (*env)->NewStringUTF(env, lib->name);
    jobject obj;
    CHECK_EXCEPTION;
    obj = (*env)->CallObjectMethod(env, this_obj, createLoadObject_ID, name,
                                   (jlong)(lib->end - lib->base),
                                   (jlong)lib->base);
    CHECK_EXCEPTION;
    (*env)->CallBooleanMethod(env, list, listAdd_ID, obj);
    CHECK_EXCEPTION;
    (*env)->DeleteLocalRef(env, obj);
    (*env)->DeleteLocalRef(env, name);
  }
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    init0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_init0
  (JNIEnv *env, jclass cls) {
  jclass listClass;

  p_ps_prochandle_ID = (*env)->GetFieldID(env, cls, "p_ps_prochandle", "J");
  CHECK_EXCEPTION;
  loadObjectList_ID = (*env)->GetFieldID(env, cls, "loadObjectList", "Ljava/util/List;");
  CHECK_EXCEPTION;

  createClosestSymbol_ID = (*env)->GetMethodID(env, cls, "createClosestSymbol",
      "(Ljava/lang/String;J)Lsun/jvm/hotspot/debugger/cdbg/ClosestSymbol;");
  CHECK_EXCEPTION;
  createLoadObject_ID = (*env)->GetMethodID(env, cls, "createLoadObject",
      "(Ljava/lang/String;JJ)Lsun/jvm/hotspot/debugger/cdbg/LoadObject;");
  CHECK_EXCEPTION;

  listClass = (*env)->FindClass(env, "java/util/List");
  CHECK_EXCEPTION;
  listAdd_ID = (*env)->GetMethodID(env, listClass, "add", "(Ljava/lang/Object;)Z");
  CHECK_EXCEPTION;
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    getAddressSize
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_getAddressSize
  (JNIEnv *env, jclass cls) {
  /* Bytes, as on every other platform: MachineDescription multiplies. */
  return (jint)sizeof(uintptr_t);
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    attach0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_attach0__I
  (JNIEnv *env, jobject this_obj, jint jpid) {
  pid_t pid = (pid_t)jpid;
  struct ps_prochandle* ph;
  int status;

  if (ptrace(PT_ATTACH, pid, NULL, 0) < 0) {
    char msg[256];
    snprintf(msg, sizeof(msg), "ptrace(PT_ATTACH, %d) failed: %s",
             (int)pid, strerror(errno));
    THROW_NEW_DEBUGGER_EXCEPTION(msg);
  }
  /* PT_ATTACH raises SIGSTOP in the target; reap it before going on. */
  if (waitpid(pid, &status, 0) < 0 || !WIFSTOPPED(status)) {
    ptrace(PT_DETACH, pid, (void*)1, 0);
    THROW_NEW_DEBUGGER_EXCEPTION("target did not stop after PT_ATTACH");
  }

  ph = (struct ps_prochandle*)calloc(1, sizeof(*ph));
  if (ph == NULL) {
    ptrace(PT_DETACH, pid, (void*)1, 0);
    THROW_NEW_DEBUGGER_EXCEPTION("out of memory");
  }
  ph->pid = pid;
  ph->core_fd = -1;
  (*env)->SetLongField(env, this_obj, p_ps_prochandle_ID, (jlong)(intptr_t)ph);

  fill_load_objects(env, this_obj, ph);
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    attach0
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_attach0__Ljava_lang_String_2Ljava_lang_String_2
  (JNIEnv *env, jobject this_obj, jstring execName, jstring coreName) {
  struct ps_prochandle* ph;
  const char* exec_str;
  const char* core_str;
  int ok;

  exec_str = (*env)->GetStringUTFChars(env, execName, NULL);
  CHECK_EXCEPTION;
  core_str = (*env)->GetStringUTFChars(env, coreName, NULL);
  if (core_str == NULL) {
    (*env)->ReleaseStringUTFChars(env, execName, exec_str);
    return;
  }

  ph = (struct ps_prochandle*)calloc(1, sizeof(*ph));
  if (ph == NULL) {
    (*env)->ReleaseStringUTFChars(env, coreName, core_str);
    (*env)->ReleaseStringUTFChars(env, execName, exec_str);
    THROW_NEW_DEBUGGER_EXCEPTION("out of memory");
  }
  ph->core_fd = -1;
#if defined(__NetBSD__) || defined(__FreeBSD__)
  ok = (open_core(ph, exec_str, core_str) == 0);
#else
  /* OpenBSD's kinfo_vmentry has no path, so a core there names nothing. */
  ok = 0;
#endif
  (*env)->ReleaseStringUTFChars(env, coreName, core_str);
  (*env)->ReleaseStringUTFChars(env, execName, exec_str);

  if (!ok) {
    free_proc(ph);
    THROW_NEW_DEBUGGER_EXCEPTION("cannot read the core file");
  }
  ph->libs = sort_libs_by_base(ph->libs);
  (*env)->SetLongField(env, this_obj, p_ps_prochandle_ID, (jlong)(intptr_t)ph);

  publish_load_objects(env, this_obj, ph);
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    detach0
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_detach0
  (JNIEnv *env, jobject this_obj) {
  struct ps_prochandle* ph = get_proc(env, this_obj);
  if (ph != NULL) {
    if (ph->pid != 0) {
      ptrace(PT_DETACH, ph->pid, (void*)1, 0);
    }
    free_proc(ph);
    (*env)->SetLongField(env, this_obj, p_ps_prochandle_ID, (jlong)0);
  }
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    lookupByName0
 * Signature: (Ljava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_lookupByName0
  (JNIEnv *env, jobject this_obj, jstring objectName, jstring symbolName) {
  struct ps_prochandle* ph = get_proc(env, this_obj);
  const char* objstr = NULL;
  const char* symstr;
  lib_info* lib;
  jlong result = 0;

  if (ph == NULL) {
    return 0;
  }
  if (objectName != NULL) {
    objstr = (*env)->GetStringUTFChars(env, objectName, NULL);
    CHECK_EXCEPTION_(0);
  }
  symstr = (*env)->GetStringUTFChars(env, symbolName, NULL);
  if (symstr == NULL) {
    if (objstr != NULL) {
      (*env)->ReleaseStringUTFChars(env, objectName, objstr);
    }
    return 0;
  }

  /*
   * The named object is tried first, then every other one.  Linux skips
   * the name altogether because the caller's idea of it -- "libjvm.so" --
   * need not be the path the kernel reports, and the agent also asks for
   * flags such as UseSharedSpaces with no object named at all.
   */
  lib = find_lib(ph, objstr);
  if (lib != NULL) {
    result = lookup_in_lib(lib, symstr);
  }
  if (result == 0) {
    for (lib = ph->libs; lib != NULL && result == 0; lib = lib->next) {
      result = lookup_in_lib(lib, symstr);
    }
  }

  (*env)->ReleaseStringUTFChars(env, symbolName, symstr);
  if (objstr != NULL) {
    (*env)->ReleaseStringUTFChars(env, objectName, objstr);
  }
  return result;
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    lookupByAddress0
 * Signature: (J)Lsun/jvm/hotspot/debugger/cdbg/ClosestSymbol;
 */
JNIEXPORT jobject JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_lookupByAddress0
  (JNIEnv *env, jobject this_obj, jlong address) {
  struct ps_prochandle* ph = get_proc(env, this_obj);
  uintptr_t target = (uintptr_t)address;
  lib_info* lib;
  jobject result = NULL;

  if (ph == NULL) {
    return NULL;
  }
  for (lib = ph->libs; lib != NULL; lib = lib->next) {
    char* addr;
    size_t size;
    const Elf64_Ehdr* eh;
    struct find_addr f;
    uintptr_t bias;

    if (!lib_contains(lib, target)) {
      continue;
    }
    if (map_file(lib->name, &addr, &size) != 0) {
      continue;
    }
    if ((eh = elf_header(addr, size)) != NULL) {
      bias = lib->base - elf_vaddr0(addr, size, eh);
      memset(&f, 0, sizeof(f));
      f.target = target - bias;
      elf_walk_symbols(addr, size, eh, visit_addr, &f);
      if (f.found) {
        jstring name = (*env)->NewStringUTF(env, f.name);
        if (name != NULL) {
          result = (*env)->CallObjectMethod(env, this_obj,
              createClosestSymbol_ID, name, (jlong)(f.target - f.best));
        }
      }
    }
    munmap(addr, size);
    break;
  }
  return result;
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    readBytesFromProcess0
 * Signature: (JJ)[B
 */
JNIEXPORT jbyteArray JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_readBytesFromProcess0
  (JNIEnv *env, jobject this_obj, jlong addr, jlong numBytes) {
  struct ps_prochandle* ph = get_proc(env, this_obj);
  jbyteArray array;
  jbyte *buf;
  int failed;

  if (ph == NULL) {
    return NULL;
  }
  array = (*env)->NewByteArray(env, (jsize)numBytes);
  CHECK_EXCEPTION_(0);
  buf = (*env)->GetByteArrayElements(env, array, NULL);
  CHECK_EXCEPTION_(0);

  if (ph->pid != 0) {
    /*
     * PT_IO moves the whole range in one call.  Linux peeks a word at a
     * time because PTRACE_PEEKDATA is all it has.
     */
    struct ptrace_io_desc io;
    io.piod_op = PIOD_READ_D;
    io.piod_offs = (void*)(uintptr_t)addr;
    io.piod_addr = buf;
    io.piod_len = (size_t)numBytes;
    failed = (ptrace(PT_IO, ph->pid, (void*)&io, 0) != 0 ||
              io.piod_len != (size_t)numBytes);
  } else {
#if defined(__NetBSD__) || defined(__FreeBSD__)
    failed = (core_read(ph, (uintptr_t)addr, buf, (size_t)numBytes) != 0);
#else
    failed = 1;
#endif
  }

  if (failed) {
    (*env)->ReleaseByteArrayElements(env, array, buf, JNI_ABORT);
    return NULL;
  }
  (*env)->ReleaseByteArrayElements(env, array, buf, 0);
  return array;
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    demangle
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_demangle
  (JNIEnv *env, jobject this_obj, jstring jsym) {
  const char* sym;
  char* demangled;
  jstring result = NULL;
  int status;

  sym = (*env)->GetStringUTFChars(env, jsym, NULL);
  CHECK_EXCEPTION_(NULL);
  if (sym == NULL) {
    return NULL;
  }
  /*
   * Only an Itanium mangled name starts _Z, and only such a name may be
   * handed to the demangler.  It is not required to reject anything else,
   * and FreeBSD's does not: it reads MaxJNILocalCapacity as an M for
   * pointer-to-member and answers "long long signed char::*", which is
   * what findpc then prints where the flag's name belongs.
   */
  if (sym[0] != '_' || sym[1] != 'Z') {
    (*env)->ReleaseStringUTFChars(env, jsym, sym);
    return jsym;
  }
  demangled = __cxa_demangle(sym, NULL, NULL, &status);
  (*env)->ReleaseStringUTFChars(env, jsym, sym);

  if (demangled != NULL && status == 0) {
    result = (*env)->NewStringUTF(env, demangled);
    free(demangled);
  } else if (status == -2) {
    /* Not a C++ mangled name.  A C symbol, most likely; hand it back. */
    result = jsym;
  } else {
    free(demangled);
    THROW_NEW_DEBUGGER_EXCEPTION_("Could not demangle", NULL);
  }
  return result;
}

/*
 * Class:     sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal
 * Method:    getThreadIntegerRegisterSet0
 * Signature: (J)[J
 */
JNIEXPORT jlongArray JNICALL Java_sun_jvm_hotspot_debugger_bsd_BsdDebuggerLocal_getThreadIntegerRegisterSet0
  (JNIEnv *env, jobject this_obj, jlong thread_id) {
  struct ps_prochandle* ph = get_proc(env, this_obj);
  struct reg gregs;
  jlongArray array;
  jlong *regs;

  if (ph == NULL) {
    return NULL;
  }
  if (ph->pid != 0) {
    /*
     * Which argument names the thread differs: NetBSD takes the lwp in
     * data and the process in pid, FreeBSD addresses the thread through
     * pid itself.
     */
#ifdef __NetBSD__
    int failed = (ptrace(PT_GETREGS, ph->pid, (void*)&gregs, (int)thread_id) != 0);
#else
    int failed = (ptrace(PT_GETREGS, (pid_t)thread_id, (void*)&gregs, 0) != 0);
#endif
    if (failed) {
      /*
       * Not fatal: the same is true on Linux, where an ESRCH here makes the
       * stack walker fall back on the last java frame.
       */
      return NULL;
    }
  } else {
    thread_info* t;
    for (t = ph->threads; t != NULL; t = t->next) {
      if (t->lwpid == (sa_lwpid_t)thread_id) {
        break;
      }
    }
    if (t == NULL) {
      return NULL;
    }
    gregs = t->regs;
  }

#define NPRGREG   sun_jvm_hotspot_debugger_amd64_AMD64ThreadContext_NPRGREG
#define REG_INDEX(reg) sun_jvm_hotspot_debugger_amd64_AMD64ThreadContext_##reg

  array = (*env)->NewLongArray(env, NPRGREG);
  CHECK_EXCEPTION_(0);
  regs = (*env)->GetLongArrayElements(env, array, NULL);
  CHECK_EXCEPTION_(0);

  /*
   * NetBSD's struct reg is a __gregset_t indexed by the _REG_* enum;
   * FreeBSD's names its members.  Same registers, two spellings.
   */
#ifdef __NetBSD__
#define GREG(up, lo) gregs.regs[_REG_##up]
#else
#define GREG(up, lo) gregs.r_##lo
#endif

  regs[REG_INDEX(R15)]    = GREG(R15, r15);
  regs[REG_INDEX(R14)]    = GREG(R14, r14);
  regs[REG_INDEX(R13)]    = GREG(R13, r13);
  regs[REG_INDEX(R12)]    = GREG(R12, r12);
  regs[REG_INDEX(R11)]    = GREG(R11, r11);
  regs[REG_INDEX(R10)]    = GREG(R10, r10);
  regs[REG_INDEX(R9)]     = GREG(R9, r9);
  regs[REG_INDEX(R8)]     = GREG(R8, r8);
  regs[REG_INDEX(RDI)]    = GREG(RDI, rdi);
  regs[REG_INDEX(RSI)]    = GREG(RSI, rsi);
  regs[REG_INDEX(RBP)]    = GREG(RBP, rbp);
  regs[REG_INDEX(RBX)]    = GREG(RBX, rbx);
  regs[REG_INDEX(RDX)]    = GREG(RDX, rdx);
  regs[REG_INDEX(RCX)]    = GREG(RCX, rcx);
  regs[REG_INDEX(RAX)]    = GREG(RAX, rax);
  regs[REG_INDEX(TRAPNO)] = GREG(TRAPNO, trapno);
  regs[REG_INDEX(ERR)]    = GREG(ERR, err);
  regs[REG_INDEX(RIP)]    = GREG(RIP, rip);
  regs[REG_INDEX(CS)]     = GREG(CS, cs);
  regs[REG_INDEX(RFL)]    = GREG(RFLAGS, rflags);
  regs[REG_INDEX(RSP)]    = GREG(RSP, rsp);
  regs[REG_INDEX(SS)]     = GREG(SS, ss);
  regs[REG_INDEX(FS)]     = GREG(FS, fs);
  regs[REG_INDEX(GS)]     = GREG(GS, gs);
  regs[REG_INDEX(ES)]     = GREG(ES, es);
  regs[REG_INDEX(DS)]     = GREG(DS, ds);
  /* Neither one's struct reg carries FS_BASE or GS_BASE. */
  regs[REG_INDEX(FSBASE)] = 0;
  regs[REG_INDEX(GSBASE)] = 0;

#undef GREG

  (*env)->ReleaseLongArrayElements(env, array, regs, 0);
  return array;
}

#endif /* __NetBSD__ || __FreeBSD__ */
