/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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
#ifndef _SALIBPROC_H_
#define _SALIBPROC_H_

/*
 * The following definitions, prototypes are from Solaris libproc.h.
 * We used to use the copy of it from Solaris 8.0. But there are
 * problems with that approach in building this library across Solaris
 * versions.  Solaris 10 has libproc.h in /usr/include. And libproc.h
 * varies slightly across Solaris versions. On Solaris 9, we get
 * 'sysret_t multiply defined' error. This is common minimum subset we
 * really need from libproc.h. The libproc.h in the current dir has
 * been left for reference and not used in build.
 */

#include <dlfcn.h>
#include <gelf.h>
#include <procfs.h>
#include <proc_service.h>
#include <fcntl.h>
#include <unistd.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * 'object_name' is the name of a load object obtained from an
 * iteration over the process's address space mappings (Pmapping_iter),
 * or an iteration over the process's mapped objects (Pobject_iter),
 * or else it is one of the special PR_OBJ_* values above.
 */

extern int Plookup_by_addr(struct ps_prochandle *,
    uintptr_t, char *, size_t, GElf_Sym *);

typedef int proc_map_f(void *, const prmap_t *, const char *);
extern int Pobject_iter(struct ps_prochandle *, proc_map_f *, void *);

/*
 * Utility functions for processing arguments which should be /proc files,
 * pids, and/or core files.  The returned error code can be passed to
 * Pgrab_error() in order to convert it to an error string.
 */
#define PR_ARG_PIDS     0x1     /* Allow pid and /proc file arguments */
#define PR_ARG_CORES    0x2     /* Allow core file arguments */
#define PR_ARG_ANY      (PR_ARG_PIDS | PR_ARG_CORES)

/* Flags accepted by Pgrab() (partial) */
#define PGRAB_FORCE     0x02    /* Open the process w/o O_EXCL */

/* Error codes from Pgrab(), Pfgrab_core(), and Pgrab_core() */
#define G_STRANGE       -1      /* Unanticipated error, errno is meaningful */
#define G_NOPROC        1       /* No such process */
#define G_NOCORE        2       /* No such core file */
#define G_NOPROCORCORE  3       /* No such proc or core (for proc_arg_grab) */
#define G_NOEXEC        4       /* Cannot locate executable file */
#define G_ZOMB          5       /* Zombie process */
#define G_PERM          6       /* No permission */
#define G_BUSY          7       /* Another process has control */
#define G_SYS           8       /* System process */
#define G_SELF          9       /* Process is self */
#define G_INTR          10      /* Interrupt received while grabbing */
#define G_LP64          11      /* Process is _LP64, self is ILP32 */
#define G_FORMAT        12      /* File is not an ELF format core file */
#define G_ELF           13      /* Libelf error, elf_errno() is meaningful */
#define G_NOTE          14      /* Required PT_NOTE Phdr not present in core */

extern struct ps_prochandle *proc_arg_grab(const char *, int, int, int *);
extern  const pstatus_t *Pstatus(struct ps_prochandle *);

/* Flags accepted by Prelease (partial) */
#define PRELEASE_CLEAR  0x10    /* Clear all tracing flags */

extern  void    Prelease(struct ps_prochandle *, int);
extern  int     Psetrun(struct ps_prochandle *, int, int);
extern  int     Pstop(struct ps_prochandle *, uint_t);

/*
 * Stack frame iteration interface.
 */
#ifdef SOLARIS_11_B159_OR_LATER
/* building on Nevada-B159 or later so define the new callback */
typedef int proc_stack_f(
    void *,             /* the cookie given to Pstack_iter() */
    const prgregset_t,  /* the frame's registers */
    uint_t,             /* argc for the frame's function */
    const long *,       /* argv for the frame's function */
    int,                /* bitwise flags describing the frame (see below) */
    int);               /* a signal number */

#define PR_SIGNAL_FRAME    1    /* called by a signal handler */
#define PR_FOUND_SIGNAL    2    /* we found the corresponding signal number */
#else
/* building on Nevada-B158 or earlier so define the old callback */
typedef int proc_stack_f(void *, const prgregset_t, uint_t, const long *);
#endif

extern int Pstack_iter(struct ps_prochandle *,
    const prgregset_t, proc_stack_f *, void *);

#define PR_OBJ_EVERY    ((const char *)-1)      /* search every load object */


#ifdef __cplusplus
}
#endif

#endif /* _SALIBPROC_H_ */
