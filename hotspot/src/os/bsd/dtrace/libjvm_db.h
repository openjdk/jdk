/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_SOLARIS_DTRACE_LIBJVM_DB_H
#define OS_SOLARIS_DTRACE_LIBJVM_DB_H

// not available on macosx #include <proc_service.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct jvm_agent jvm_agent_t;

#define JVM_DB_VERSION  1

jvm_agent_t *Jagent_create(struct ps_prochandle *P, int vers);

/*
 * Called from Jframe_iter() for each java frame.  If it returns 0, then
 * Jframe_iter() proceeds to the next frame.  Otherwise, the return value is
 * immediately returned to the caller of Jframe_iter().
 *
 * Parameters:
 *    'cld' is client supplied data (to maintain iterator state, if any).
 *    'name' is java method name.
 *    'bci' is byte code index. it will be -1 if not available.
 *    'line' is java source line number. it will be 0 if not available.
 *    'handle' is an abstract client handle, reserved for future expansions
 */

typedef int java_stack_f(void *cld, const prgregset_t regs, const char* name, int bci, int line, void *handle);

/*
 * Iterates over the java frames at the current location.  Returns -1 if no java
 * frames were found, or if there was some unrecoverable error.  Otherwise,
 * returns the last value returned from 'func'.
 */
int Jframe_iter(jvm_agent_t *agent, prgregset_t gregs, java_stack_f *func, void* cld);

void Jagent_destroy(jvm_agent_t *J);

#ifdef __cplusplus
} /* extern "C" */
#endif /* __cplusplus */

#endif // OS_SOLARIS_DTRACE_LIBJVM_DB_H
