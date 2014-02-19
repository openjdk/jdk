/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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

#ifndef CPU_PPC_VM_BYTECODEINTERPRETER_PPC_HPP
#define CPU_PPC_VM_BYTECODEINTERPRETER_PPC_HPP

// Platform specific for C++ based Interpreter
#define LOTS_OF_REGS    /* Lets interpreter use plenty of registers */

private:

    // Save the bottom of the stack after frame manager setup. For ease of restoration after return
    // from recursive interpreter call.
    intptr_t* _frame_bottom;              // Saved bottom of frame manager frame.
    address   _last_Java_pc;              // Pc to return to in frame manager.
    intptr_t* _last_Java_fp;              // frame pointer
    intptr_t* _last_Java_sp;              // stack pointer
    interpreterState _self_link;          // Previous interpreter state  // sometimes points to self???
    double    _native_fresult;            // Save result of native calls that might return floats.
    intptr_t  _native_lresult;            // Save result of native calls that might return handle/longs.

public:
    address last_Java_pc(void)            { return _last_Java_pc; }
    intptr_t* last_Java_fp(void)          { return _last_Java_fp; }

    static ByteSize native_lresult_offset() {
      return byte_offset_of(BytecodeInterpreter, _native_lresult);
    }

    static ByteSize native_fresult_offset() {
      return byte_offset_of(BytecodeInterpreter, _native_fresult);
    }

    static void pd_layout_interpreterState(interpreterState istate, address last_Java_pc, intptr_t* last_Java_fp);

#define SET_LAST_JAVA_FRAME()   THREAD->frame_anchor()->set(istate->_last_Java_sp, istate->_last_Java_pc);
#define RESET_LAST_JAVA_FRAME() THREAD->frame_anchor()->clear();


// Macros for accessing the stack.
#undef STACK_INT
#undef STACK_FLOAT
#undef STACK_ADDR
#undef STACK_OBJECT
#undef STACK_DOUBLE
#undef STACK_LONG

// JavaStack Implementation
#define STACK_SLOT(offset)    ((address) &topOfStack[-(offset)])
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
#define LOCALS_INT(offset)     (*(jint*)&(locals[-(offset)]))
#define LOCALS_OBJECT(offset)  (cast_to_oop(locals[-(offset)]))
#define LOCALS_LONG_AT(offset) (((address)&locals[-((offset) + 1)]))
#define LOCALS_DOUBLE_AT(offset) (((address)&locals[-((offset) + 1)]))

#define SET_LOCALS_SLOT(value, offset)    (*(intptr_t*)&locals[-(offset)] = *(intptr_t *)(value))
#define SET_LOCALS_INT(value, offset)     (*((jint *)&locals[-(offset)]) = (value))
#define SET_LOCALS_DOUBLE(value, offset)  (((VMJavaVal64*)&locals[-((offset)+1)])->d = (value))
#define SET_LOCALS_LONG(value, offset)    (((VMJavaVal64*)&locals[-((offset)+1)])->l = (value))
#define SET_LOCALS_DOUBLE_FROM_ADDR(addr, offset) (((VMJavaVal64*)&locals[-((offset)+1)])->d = \
                                                  ((VMJavaVal64*)(addr))->d)


#endif // CPU_PPC_VM_BYTECODEINTERPRETER_PPC_PP
