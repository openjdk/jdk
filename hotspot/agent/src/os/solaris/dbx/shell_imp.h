/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHELL_IMP_H
#define SHELL_IMP_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdio.h>

/*
 CCR info

 Vesrion history:

        1.0       - Initial CCR release

 Release information for automatic CCR updates:

 BEGIN RELEASE NOTES: (signifies what gets put into CCR release notes)
        1.1
                  - Entry points for va_list style msgs; new shell_imp_vmsg()
                    and shell_imp_verrmsg()
                  - shell_imp_env_checker() is now shell_imp_var_checker().
                    Also the var_checker callback gets passed interp.
        1.2       - interposition framework (used by jdbx)
                  - access to input FILE pointer.

 END RELEASE NOTES: (signifies what gets put into CCR release notes)

Following is used as a CCR version number:
#define CCR_SHELL_IMP_VERSION 1.1
*/

#include <stdarg.h>

#define SHELL_IMP_MAJOR 1
#define SHELL_IMP_MINOR 2
#define SHELL_IMP_FLAG_GLOB 0x1
#define SHELL_IMP_FLAG_ARGQ 0x2

typedef void *shell_imp_interp_t;
typedef void *shell_imp_command_t;
typedef int shell_imp_fun_t(shell_imp_interp_t, int, char **, void *);

int
shell_imp_init(
    int,                /* major version number */
    int,                /* minor version number */
    shell_imp_interp_t, /* interpreter */
    int,                /* argc */
    char *[]            /* argv */
);

int
shell_imp_fini(shell_imp_interp_t);

shell_imp_command_t
shell_imp_define_command(char *,        /* command name e.g. "tnf" */
                    shell_imp_fun_t *,  /* callback function */
                    int,                /* SHELL_IMP_FLAG_* bit vector */
                    void *,             /* client_data Passed as last arg to
                                        /* callback function */
                    char *              /* help message, e.g. */
                                        /* "enable the specified tnf probes" */
            );

int
shell_imp_undefine_command(shell_imp_command_t);

int
shell_imp_var_checker(shell_imp_interp_t,
                      const char *,         /* var name */
                      int (*)(shell_imp_interp_t, const char*) /* env checker */
                     );

int
shell_imp_execute(shell_imp_interp_t, const char *);

const char *
shell_imp_get_var(shell_imp_interp_t, const char *);

void
shell_imp_msg(shell_imp_interp_t, const char *, ...);

void
shell_imp_errmsg(shell_imp_interp_t, const char *, ...);

void
shell_imp_vmsg(shell_imp_interp_t, const char *, va_list);

void
shell_imp_verrmsg(shell_imp_interp_t, const char *, va_list);



/*
 * Stuff added for 1.2
 */

struct shell_imp_interposition_info_t {
    shell_imp_fun_t *
                new_func;
    void *      new_client_data;
    shell_imp_fun_t *
                original_func;
    void *      original_client_data;
    int         original_flags;
};

typedef int shell_imp_dispatcher_t(shell_imp_interp_t, int, char **,
                                   shell_imp_interposition_info_t *);

shell_imp_command_t
shell_imp_interpose(char *name,
                    shell_imp_fun_t *new_func,
                    int    flags,
                    void *client_data,
                    char * description,
                    shell_imp_dispatcher_t *);

int shell_imp_uninterpose(shell_imp_command_t);

int
shell_imp_dispatch_interposition(shell_imp_interp_t,
                                 shell_imp_interposition_info_t *,
                                 int argc, char *argv[]);

int
shell_imp_dispatch_original(shell_imp_interp_t,
                                 shell_imp_interposition_info_t *,
                                 int argc, char *argv[]);

FILE *
shell_imp_cur_input(shell_imp_interp_t);

#ifdef __cplusplus
}
#endif

#endif
