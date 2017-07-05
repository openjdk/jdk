/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _PROC_SERVICE_2_H
#define _PROC_SERVICE_2_H

/*
 * Types, function definitions for the provider of services beyond
 * proc_service.  This interface will be used by import modules like
 * BAT/prex, NEO debugger etc.
 */

/*
 CCR info

 Version history:

        1.0       - Initial CCR release

        1.1       - Changes for GLUE/neo.
                    New entry points ps_svnt_generic() and ps_svc_generic()
                  - New entry point ps_getpid()

 Release information for automatic CCR updates:
 BEGIN RELEASE NOTES: (signifies what gets put into CCR release notes)
        1.2       - Changes to support Solaris 2.7

 END RELEASE NOTES: (signifies what gets put into CCR release notes)

 Following is used for CCR version number:

#define CCR_PROC_SERVICE_2_VERSION 1.2

*/


#include <proc_service.h>
#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

struct ps_loadobj {
        int     objfd;          /* fd of the load object or executable
                                 * -1 implies its not available.
                                 * This file decriptor is live only during the
                                 * particular call to ps_iter_f().  If you
                                 * need it beyond that you need to dup() it.
                                 */
        psaddr_t
                text_base;      /* address where text of loadobj was mapped */
        psaddr_t
                data_base;      /* address where data of loadobj was mapped */
        const char *objname;    /* loadobj name */
};

typedef int ps_iter_f(const struct ps_prochandle *, const struct ps_loadobj *,
                        void *cd);

/*
 * Returns the ps_prochandle for the current process under focus.  Returns
 * NULL if there is none.
 */

const struct ps_prochandle *
ps_get_prochandle(void);

/*
 * Returns the ps_prochandle for the current process(allows core files to
 * be specified) under focus.  Returns NULL if there is none.
 */
const struct ps_prochandle *
ps_get_prochandle2(int cores_too);

/*
 * Returns the pid of the process referred to by the ps_prochandle.
 *
 * 0 is returned in case the ps_prochandle is not valid or refers to dead
 * process.
 *
 */
pid_t
ps_getpid(const struct ps_prochandle *);

/*
 * Iteration function that iterates over all load objects *and the
 *      executable*
 *
 *      If the callback routine returns:
 *      0 - continue processing link objects
 *      non zero - stop calling the callback function
 *
 */

ps_err_e
ps_loadobj_iter(const struct ps_prochandle *, ps_iter_f *, void *clnt_data);

/*
 * Address => function name mapping
 *
 * Given an address, returns a pointer to the function's
 * linker name (null terminated).
 */

ps_err_e
ps_find_fun_name(const struct ps_prochandle *, psaddr_t addr,
                        const char **name);

/*
 * Interface to LD_PRELOAD.  LD_PRELOAD given library across the
 * program 'exec'.
 *
 */

/*
 * Append/Prepend the 'lib' (has to be library name as understood by LD_PRELOAD)
 * to the LD_PRELOAD variable setting to be used by the debugee
 * Returns a cookie (in id).
 */
ps_err_e
ps_ld_preload_append(const char *lib, int *id);
ps_err_e
ps_ld_preload_prepend(const char *lib, int *id);

/*
 * Remove the library associated with 'id' from the LD_PRELOAD setting.
 *
 */
ps_err_e
ps_ld_preload_remove(int id);

#ifdef __cplusplus
}
#endif

/*
 * The following are C++ only interfaces
 */
#ifdef __cplusplus

/*
 * classes ServiceDbx and ServantDbx and defined in "gp_dbx_svc.h" which is
 * accessed via CCR
 */
extern class ServantDbx *ps_svnt_generic();
extern class ServiceDbx *ps_svc_generic();

#endif

#endif /* _PROC_SERVICE_2_H */
