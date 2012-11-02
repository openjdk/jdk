/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_INTERPRETER_BYTECODEINTERPRETER_HPP
#define SHARE_VM_INTERPRETER_BYTECODEINTERPRETER_HPP

#include "memory/allocation.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "runtime/basicLock.hpp"
#include "runtime/frame.hpp"
#include "runtime/globals.hpp"
#include "utilities/globalDefinitions.hpp"
#ifdef TARGET_ARCH_x86
# include "bytes_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "bytes_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "bytes_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "bytes_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "bytes_ppc.hpp"
#endif

#ifdef CC_INTERP

// JavaStack Implementation
#define MORE_STACK(count)  \
    (topOfStack -= ((count) * Interpreter::stackElementWords))

// CVM definitions find hotspot equivalents...

union VMJavaVal64 {
    jlong   l;
    jdouble d;
    uint32_t      v[2];
};


typedef class BytecodeInterpreter* interpreterState;

struct call_message {
    class Method* _callee;    /* method to call during call_method request */
    address   _callee_entry_point;   /* address to jump to for call_method request */
    int       _bcp_advance;          /* size of the invoke bytecode operation */
};

struct osr_message {
    address _osr_buf;                 /* the osr buffer */
    address _osr_entry;               /* the entry to the osr method */
};

struct osr_result {
  nmethod* nm;                       /* osr nmethod */
  address return_addr;               /* osr blob return address */
};

// Result returned to frame manager
union frame_manager_message {
    call_message _to_call;            /* describes callee */
    Bytecodes::Code _return_kind;     /* i_return, a_return, ... */
    osr_message _osr;                 /* describes the osr */
    osr_result _osr_result;           /* result of OSR request */
};

class BytecodeInterpreter : StackObj {
friend class SharedRuntime;
friend class AbstractInterpreterGenerator;
friend class CppInterpreterGenerator;
friend class InterpreterGenerator;
friend class InterpreterMacroAssembler;
friend class frame;
friend class VMStructs;

public:
    enum messages {
         no_request = 0,            // unused
         initialize,                // Perform one time interpreter initializations (assumes all switches set)
         // status message to C++ interpreter
         method_entry,              // initial method entry to interpreter
         method_resume,             // frame manager response to return_from_method request (assuming a frame to resume)
         deopt_resume,              // returning from a native call into a deopted frame
         deopt_resume2,             // deopt resume as a result of a PopFrame
         got_monitors,              // frame manager response to more_monitors request
         rethrow_exception,         // unwinding and throwing exception
         // requests to frame manager from C++ interpreter
         call_method,               // request for new frame from interpreter, manager responds with method_entry
         return_from_method,        // request from interpreter to unwind, manager responds with method_continue
         more_monitors,             // need a new monitor
         throwing_exception,        // unwind stack and rethrow
         popping_frame,             // unwind call and retry call
         do_osr                     // request this invocation be OSR's
    };

private:
    JavaThread*           _thread;        // the vm's java thread pointer
    address               _bcp;           // instruction pointer
    intptr_t*             _locals;        // local variable pointer
    ConstantPoolCache*    _constants;     // constant pool cache
    Method*               _method;        // method being executed
    DataLayout*           _mdx;           // compiler profiling data for current bytecode
    intptr_t*             _stack;         // expression stack
    messages              _msg;           // frame manager <-> interpreter message
    frame_manager_message _result;        // result to frame manager
    interpreterState      _prev_link;     // previous interpreter state
    oop                   _oop_temp;      // mirror for interpreted native, null otherwise
    intptr_t*             _stack_base;    // base of expression stack
    intptr_t*             _stack_limit;   // limit of expression stack
    BasicObjectLock*      _monitor_base;  // base of monitors on the native stack


public:
  // Constructor is only used by the initialization step. All other instances are created
  // by the frame manager.
  BytecodeInterpreter(messages msg);

//
// Deoptimization support
//
static void layout_interpreterState(interpreterState to_fill,
                                    frame* caller,
                                    frame* interpreter_frame,
                                    Method* method,
                                    intptr_t* locals,
                                    intptr_t* stack,
                                    intptr_t* stack_base,
                                    intptr_t* monitor_base,
                                    intptr_t* frame_bottom,
                                    bool top_frame);

/*
 * Generic 32-bit wide "Java slot" definition. This type occurs
 * in operand stacks, Java locals, object fields, constant pools.
 */
union VMJavaVal32 {
    jint     i;
    jfloat   f;
    class oopDesc*   r;
    uint32_t raw;
};

/*
 * Generic 64-bit Java value definition
 */
union VMJavaVal64 {
    jlong   l;
    jdouble d;
    uint32_t      v[2];
};

/*
 * Generic 32-bit wide "Java slot" definition. This type occurs
 * in Java locals, object fields, constant pools, and
 * operand stacks (as a CVMStackVal32).
 */
typedef union VMSlotVal32 {
    VMJavaVal32    j;     /* For "Java" values */
    address        a;     /* a return created by jsr or jsr_w */
} VMSlotVal32;


/*
 * Generic 32-bit wide stack slot definition.
 */
union VMStackVal32 {
    VMJavaVal32    j;     /* For "Java" values */
    VMSlotVal32    s;     /* any value from a "slot" or locals[] */
};

inline JavaThread* thread() { return _thread; }

inline address bcp() { return _bcp; }
inline void set_bcp(address new_bcp) { _bcp = new_bcp; }

inline intptr_t* locals() { return _locals; }

inline ConstantPoolCache* constants() { return _constants; }
inline Method* method() { return _method; }
inline DataLayout* mdx() { return _mdx; }
inline void set_mdx(DataLayout *new_mdx) { _mdx = new_mdx; }

inline messages msg() { return _msg; }
inline void set_msg(messages new_msg) { _msg = new_msg; }

inline Method* callee() { return _result._to_call._callee; }
inline void set_callee(Method* new_callee) { _result._to_call._callee = new_callee; }
inline void set_callee_entry_point(address entry) { _result._to_call._callee_entry_point = entry; }
inline void set_osr_buf(address buf) { _result._osr._osr_buf = buf; }
inline void set_osr_entry(address entry) { _result._osr._osr_entry = entry; }
inline int bcp_advance() { return _result._to_call._bcp_advance; }
inline void set_bcp_advance(int count) { _result._to_call._bcp_advance = count; }

inline void set_return_kind(Bytecodes::Code kind) { _result._return_kind = kind; }

inline interpreterState prev() { return _prev_link; }

inline intptr_t* stack() { return _stack; }
inline void set_stack(intptr_t* new_stack) { _stack = new_stack; }


inline intptr_t* stack_base() { return _stack_base; }
inline intptr_t* stack_limit() { return _stack_limit; }

inline BasicObjectLock* monitor_base() { return _monitor_base; }

/*
 * 64-bit Arithmetic:
 *
 * The functions below follow the semantics of the
 * ladd, land, ldiv, lmul, lor, lxor, and lrem bytecodes,
 * respectively.
 */

static jlong VMlongAdd(jlong op1, jlong op2);
static jlong VMlongAnd(jlong op1, jlong op2);
static jlong VMlongDiv(jlong op1, jlong op2);
static jlong VMlongMul(jlong op1, jlong op2);
static jlong VMlongOr (jlong op1, jlong op2);
static jlong VMlongSub(jlong op1, jlong op2);
static jlong VMlongXor(jlong op1, jlong op2);
static jlong VMlongRem(jlong op1, jlong op2);

/*
 * Shift:
 *
 * The functions below follow the semantics of the
 * lushr, lshl, and lshr bytecodes, respectively.
 */

static jlong VMlongUshr(jlong op1, jint op2);
static jlong VMlongShl (jlong op1, jint op2);
static jlong VMlongShr (jlong op1, jint op2);

/*
 * Unary:
 *
 * Return the negation of "op" (-op), according to
 * the semantics of the lneg bytecode.
 */

static jlong VMlongNeg(jlong op);

/*
 * Return the complement of "op" (~op)
 */

static jlong VMlongNot(jlong op);


/*
 * Comparisons to 0:
 */

static int32_t VMlongLtz(jlong op);     /* op <= 0 */
static int32_t VMlongGez(jlong op);     /* op >= 0 */
static int32_t VMlongEqz(jlong op);     /* op == 0 */

/*
 * Between operands:
 */

static int32_t VMlongEq(jlong op1, jlong op2);    /* op1 == op2 */
static int32_t VMlongNe(jlong op1, jlong op2);    /* op1 != op2 */
static int32_t VMlongGe(jlong op1, jlong op2);    /* op1 >= op2 */
static int32_t VMlongLe(jlong op1, jlong op2);    /* op1 <= op2 */
static int32_t VMlongLt(jlong op1, jlong op2);    /* op1 <  op2 */
static int32_t VMlongGt(jlong op1, jlong op2);    /* op1 >  op2 */

/*
 * Comparisons (returning an jint value: 0, 1, or -1)
 *
 * Between operands:
 *
 * Compare "op1" and "op2" according to the semantics of the
 * "lcmp" bytecode.
 */

static int32_t VMlongCompare(jlong op1, jlong op2);

/*
 * Convert int to long, according to "i2l" bytecode semantics
 */
static jlong VMint2Long(jint val);

/*
 * Convert long to int, according to "l2i" bytecode semantics
 */
static jint VMlong2Int(jlong val);

/*
 * Convert long to float, according to "l2f" bytecode semantics
 */
static jfloat VMlong2Float(jlong val);

/*
 * Convert long to double, according to "l2d" bytecode semantics
 */
static jdouble VMlong2Double(jlong val);

/*
 * Java floating-point float value manipulation.
 *
 * The result argument is, once again, an lvalue.
 *
 * Arithmetic:
 *
 * The functions below follow the semantics of the
 * fadd, fsub, fmul, fdiv, and frem bytecodes,
 * respectively.
 */

static jfloat VMfloatAdd(jfloat op1, jfloat op2);
static jfloat VMfloatSub(jfloat op1, jfloat op2);
static jfloat VMfloatMul(jfloat op1, jfloat op2);
static jfloat VMfloatDiv(jfloat op1, jfloat op2);
static jfloat VMfloatRem(jfloat op1, jfloat op2);

/*
 * Unary:
 *
 * Return the negation of "op" (-op), according to
 * the semantics of the fneg bytecode.
 */

static jfloat VMfloatNeg(jfloat op);

/*
 * Comparisons (returning an int value: 0, 1, or -1)
 *
 * Between operands:
 *
 * Compare "op1" and "op2" according to the semantics of the
 * "fcmpl" (direction is -1) or "fcmpg" (direction is 1) bytecodes.
 */

static int32_t VMfloatCompare(jfloat op1, jfloat op2,
                              int32_t direction);
/*
 * Conversion:
 */

/*
 * Convert float to double, according to "f2d" bytecode semantics
 */

static jdouble VMfloat2Double(jfloat op);

/*
 ******************************************
 * Java double floating-point manipulation.
 ******************************************
 *
 * The result argument is, once again, an lvalue.
 *
 * Conversions:
 */

/*
 * Convert double to int, according to "d2i" bytecode semantics
 */

static jint VMdouble2Int(jdouble val);

/*
 * Convert double to float, according to "d2f" bytecode semantics
 */

static jfloat VMdouble2Float(jdouble val);

/*
 * Convert int to double, according to "i2d" bytecode semantics
 */

static jdouble VMint2Double(jint val);

/*
 * Arithmetic:
 *
 * The functions below follow the semantics of the
 * dadd, dsub, ddiv, dmul, and drem bytecodes, respectively.
 */

static jdouble VMdoubleAdd(jdouble op1, jdouble op2);
static jdouble VMdoubleSub(jdouble op1, jdouble op2);
static jdouble VMdoubleDiv(jdouble op1, jdouble op2);
static jdouble VMdoubleMul(jdouble op1, jdouble op2);
static jdouble VMdoubleRem(jdouble op1, jdouble op2);

/*
 * Unary:
 *
 * Return the negation of "op" (-op), according to
 * the semantics of the dneg bytecode.
 */

static jdouble VMdoubleNeg(jdouble op);

/*
 * Comparisons (returning an int32_t value: 0, 1, or -1)
 *
 * Between operands:
 *
 * Compare "op1" and "op2" according to the semantics of the
 * "dcmpl" (direction is -1) or "dcmpg" (direction is 1) bytecodes.
 */

static int32_t VMdoubleCompare(jdouble op1, jdouble op2, int32_t direction);

/*
 * Copy two typeless 32-bit words from one location to another.
 * This is semantically equivalent to:
 *
 * to[0] = from[0];
 * to[1] = from[1];
 *
 * but this interface is provided for those platforms that could
 * optimize this into a single 64-bit transfer.
 */

static void VMmemCopy64(uint32_t to[2], const uint32_t from[2]);


// Arithmetic operations

/*
 * Java arithmetic methods.
 * The functions below follow the semantics of the
 * iadd, isub, imul, idiv, irem, iand, ior, ixor,
 * and ineg bytecodes, respectively.
 */

static jint VMintAdd(jint op1, jint op2);
static jint VMintSub(jint op1, jint op2);
static jint VMintMul(jint op1, jint op2);
static jint VMintDiv(jint op1, jint op2);
static jint VMintRem(jint op1, jint op2);
static jint VMintAnd(jint op1, jint op2);
static jint VMintOr (jint op1, jint op2);
static jint VMintXor(jint op1, jint op2);

/*
 * Shift Operation:
 * The functions below follow the semantics of the
 * iushr, ishl, and ishr bytecodes, respectively.
 */

static juint VMintUshr(jint op, jint num);
static jint VMintShl (jint op, jint num);
static jint VMintShr (jint op, jint num);

/*
 * Unary Operation:
 *
 * Return the negation of "op" (-op), according to
 * the semantics of the ineg bytecode.
 */

static jint VMintNeg(jint op);

/*
 * Int Conversions:
 */

/*
 * Convert int to float, according to "i2f" bytecode semantics
 */

static jfloat VMint2Float(jint val);

/*
 * Convert int to byte, according to "i2b" bytecode semantics
 */

static jbyte VMint2Byte(jint val);

/*
 * Convert int to char, according to "i2c" bytecode semantics
 */

static jchar VMint2Char(jint val);

/*
 * Convert int to short, according to "i2s" bytecode semantics
 */

static jshort VMint2Short(jint val);

/*=========================================================================
 * Bytecode interpreter operations
 *=======================================================================*/

static void dup(intptr_t *tos);
static void dup2(intptr_t *tos);
static void dup_x1(intptr_t *tos);    /* insert top word two down */
static void dup_x2(intptr_t *tos);    /* insert top word three down  */
static void dup2_x1(intptr_t *tos);   /* insert top 2 slots three down */
static void dup2_x2(intptr_t *tos);   /* insert top 2 slots four down */
static void swap(intptr_t *tos);      /* swap top two elements */

// umm don't like this method modifies its object

// The Interpreter used when
static void run(interpreterState istate);
// The interpreter used if JVMTI needs interpreter events
static void runWithChecks(interpreterState istate);
static void End_Of_Interpreter(void);

// Inline static functions for Java Stack and Local manipulation

static address stack_slot(intptr_t *tos, int offset);
static jint stack_int(intptr_t *tos, int offset);
static jfloat stack_float(intptr_t *tos, int offset);
static oop stack_object(intptr_t *tos, int offset);
static jdouble stack_double(intptr_t *tos, int offset);
static jlong stack_long(intptr_t *tos, int offset);

// only used for value types
static void set_stack_slot(intptr_t *tos, address value, int offset);
static void set_stack_int(intptr_t *tos, int value, int offset);
static void set_stack_float(intptr_t *tos, jfloat value, int offset);
static void set_stack_object(intptr_t *tos, oop value, int offset);

// needs to be platform dep for the 32 bit platforms.
static void set_stack_double(intptr_t *tos, jdouble value, int offset);
static void set_stack_long(intptr_t *tos, jlong value, int offset);

static void set_stack_double_from_addr(intptr_t *tos, address addr, int offset);
static void set_stack_long_from_addr(intptr_t *tos, address addr, int offset);

// Locals

static address locals_slot(intptr_t* locals, int offset);
static jint locals_int(intptr_t* locals, int offset);
static jfloat locals_float(intptr_t* locals, int offset);
static oop locals_object(intptr_t* locals, int offset);
static jdouble locals_double(intptr_t* locals, int offset);
static jlong locals_long(intptr_t* locals, int offset);

static address locals_long_at(intptr_t* locals, int offset);
static address locals_double_at(intptr_t* locals, int offset);

static void set_locals_slot(intptr_t *locals, address value, int offset);
static void set_locals_int(intptr_t *locals, jint value, int offset);
static void set_locals_float(intptr_t *locals, jfloat value, int offset);
static void set_locals_object(intptr_t *locals, oop value, int offset);
static void set_locals_double(intptr_t *locals, jdouble value, int offset);
static void set_locals_long(intptr_t *locals, jlong value, int offset);
static void set_locals_double_from_addr(intptr_t *locals,
                                   address addr, int offset);
static void set_locals_long_from_addr(intptr_t *locals,
                                   address addr, int offset);

static void astore(intptr_t* topOfStack, int stack_offset,
                   intptr_t* locals,     int locals_offset);

// Support for dup and swap
static void copy_stack_slot(intptr_t *tos, int from_offset, int to_offset);

#ifndef PRODUCT
static const char* C_msg(BytecodeInterpreter::messages msg);
void print();
#endif // PRODUCT

    // Platform fields/methods
#ifdef TARGET_ARCH_x86
# include "bytecodeInterpreter_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "bytecodeInterpreter_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "bytecodeInterpreter_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "bytecodeInterpreter_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "bytecodeInterpreter_ppc.hpp"
#endif


}; // BytecodeInterpreter

#endif // CC_INTERP

#endif // SHARE_VM_INTERPRETER_BYTECODEINTERPRETER_HPP
