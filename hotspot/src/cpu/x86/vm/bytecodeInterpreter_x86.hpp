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

// Platform specific for C++ based Interpreter

private:

    interpreterState _self_link;          /*  Previous interpreter state  */ /* sometimes points to self??? */
    address   _result_handler;            /* temp for saving native result handler */
    intptr_t* _sender_sp;                 /* sender's sp before stack (locals) extension */

    address   _extra_junk1;               /* temp to save on recompiles */
    address   _extra_junk2;               /* temp to save on recompiles */
    address   _extra_junk3;               /* temp to save on recompiles */
    // address dummy_for_native2;         /* a native frame result handler would be here... */
    // address dummy_for_native1;         /* native result type stored here in a interpreter native frame */
    address   _extra_junk4;               /* temp to save on recompiles */
    address   _extra_junk5;               /* temp to save on recompiles */
    address   _extra_junk6;               /* temp to save on recompiles */
public:
                                                         // we have an interpreter frame...
inline intptr_t* sender_sp() {
  return _sender_sp;
}

// The interpreter always has the frame anchor fully setup so we don't
// have to do anything going to vm from the interpreter. On return
// we do have to clear the flags in case they we're modified to
// maintain the stack walking invariants.
//
#define SET_LAST_JAVA_FRAME()

#define RESET_LAST_JAVA_FRAME()

/*
 * Macros for accessing the stack.
 */
#undef STACK_INT
#undef STACK_FLOAT
#undef STACK_ADDR
#undef STACK_OBJECT
#undef STACK_DOUBLE
#undef STACK_LONG

// JavaStack Implementation

#define GET_STACK_SLOT(offset)    (*((intptr_t*) &topOfStack[-(offset)]))
#define STACK_SLOT(offset)    ((address) &topOfStack[-(offset)])
#define STACK_ADDR(offset)    (*((address *) &topOfStack[-(offset)]))
#define STACK_INT(offset)     (*((jint*) &topOfStack[-(offset)]))
#define STACK_FLOAT(offset)   (*((jfloat *) &topOfStack[-(offset)]))
#define STACK_OBJECT(offset)  (*((oop *) &topOfStack [-(offset)]))
#define STACK_DOUBLE(offset)  (((VMJavaVal64*) &topOfStack[-(offset)])->d)
#define STACK_LONG(offset)    (((VMJavaVal64 *) &topOfStack[-(offset)])->l)

#define SET_STACK_SLOT(value, offset)   (*(intptr_t*)&topOfStack[-(offset)] = *(intptr_t*)(value))
#define SET_STACK_ADDR(value, offset)   (*((address *)&topOfStack[-(offset)]) = (value))
#define SET_STACK_INT(value, offset)    (*((jint *)&topOfStack[-(offset)]) = (value))
#define SET_STACK_FLOAT(value, offset)  (*((jfloat *)&topOfStack[-(offset)]) = (value))
#define SET_STACK_OBJECT(value, offset) (*((oop *)&topOfStack[-(offset)]) = (value))
#define SET_STACK_DOUBLE(value, offset) (((VMJavaVal64*)&topOfStack[-(offset)])->d = (value))
#define SET_STACK_DOUBLE_FROM_ADDR(addr, offset) (((VMJavaVal64*)&topOfStack[-(offset)])->d =  \
                                                 ((VMJavaVal64*)(addr))->d)
#define SET_STACK_LONG(value, offset)   (((VMJavaVal64*)&topOfStack[-(offset)])->l = (value))
#define SET_STACK_LONG_FROM_ADDR(addr, offset)   (((VMJavaVal64*)&topOfStack[-(offset)])->l =  \
                                                 ((VMJavaVal64*)(addr))->l)
// JavaLocals implementation

#define LOCALS_SLOT(offset)    ((intptr_t*)&locals[-(offset)])
#define LOCALS_ADDR(offset)    ((address)locals[-(offset)])
#define LOCALS_INT(offset)     ((jint)(locals[-(offset)]))
#define LOCALS_FLOAT(offset)   (*((jfloat*)&locals[-(offset)]))
#define LOCALS_OBJECT(offset)  ((oop)locals[-(offset)])
#define LOCALS_DOUBLE(offset)  (((VMJavaVal64*)&locals[-((offset) + 1)])->d)
#define LOCALS_LONG(offset)    (((VMJavaVal64*)&locals[-((offset) + 1)])->l)
#define LOCALS_LONG_AT(offset) (((address)&locals[-((offset) + 1)]))
#define LOCALS_DOUBLE_AT(offset) (((address)&locals[-((offset) + 1)]))

#define SET_LOCALS_SLOT(value, offset)    (*(intptr_t*)&locals[-(offset)] = *(intptr_t *)(value))
#define SET_LOCALS_ADDR(value, offset)    (*((address *)&locals[-(offset)]) = (value))
#define SET_LOCALS_INT(value, offset)     (*((jint *)&locals[-(offset)]) = (value))
#define SET_LOCALS_FLOAT(value, offset)   (*((jfloat *)&locals[-(offset)]) = (value))
#define SET_LOCALS_OBJECT(value, offset)  (*((oop *)&locals[-(offset)]) = (value))
#define SET_LOCALS_DOUBLE(value, offset)  (((VMJavaVal64*)&locals[-((offset)+1)])->d = (value))
#define SET_LOCALS_LONG(value, offset)    (((VMJavaVal64*)&locals[-((offset)+1)])->l = (value))
#define SET_LOCALS_DOUBLE_FROM_ADDR(addr, offset) (((VMJavaVal64*)&locals[-((offset)+1)])->d = \
                                                  ((VMJavaVal64*)(addr))->d)
#define SET_LOCALS_LONG_FROM_ADDR(addr, offset) (((VMJavaVal64*)&locals[-((offset)+1)])->l = \
                                                ((VMJavaVal64*)(addr))->l)
