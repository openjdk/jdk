/*
 * Copyright (c) 1998, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

#ifndef _JAVASOFT_VM_CALLS_H_
#define _JAVASOFT_VM_CALLS_H_

/* This file defines the function table and macros exported from the VM
 * for the implementation of HPI.
 */

extern vm_calls_t *vm_calls;

#define VM_CALLS_READY() vm_calls
#define VM_CALL(f) (vm_calls->f)

#undef sysAssert

#ifdef DEBUG
#define sysAssert(expression) {         \
    if (!(expression)) {                \
        vm_calls->panic \
            ("\"%s\", line %d: assertion failure\n", __FILE__, __LINE__); \
    }                                   \
}
#else
#define sysAssert(expression) ((void) 0)
#endif

#ifdef LOGGING

#define Log(level, message) {                                           \
    if (vm_calls && level <= logging_level)                     \
        vm_calls->jio_fprintf(stderr, message);                         \
}

#define Log1(level, message, x1) {                                      \
    if (vm_calls && level <= logging_level)                     \
        vm_calls->jio_fprintf(stderr, message, (x1));                   \
}

#define Log2(level, message, x1, x2) {                                  \
    if (vm_calls && level <= logging_level)                     \
        vm_calls->jio_fprintf(stderr, message, (x1), (x2));             \
}

#define Log3(level, message, x1, x2, x3) {                              \
    if (vm_calls && level <= logging_level)                     \
        vm_calls->jio_fprintf(stderr, message, (x1), (x2), (x3));       \
}

#define Log4(level, message, x1, x2, x3, x4) {                          \
    if (vm_calls && level <= logging_level)                     \
        vm_calls->jio_fprintf(stderr, message, (x1), (x2), (x3), (x4)); \
}

#else

#define Log(level, message)                     ((void) 0)
#define Log1(level, message, x1)                ((void) 0)
#define Log2(level, message, x1, x2)            ((void) 0)
#define Log3(level, message, x1, x2, x3)        ((void) 0)
#define Log4(level, message, x1, x2, x3, x4)    ((void) 0)

#endif /* LOGGING */

#endif /* !_JAVASOFT_VM_CALLS_H_ */
