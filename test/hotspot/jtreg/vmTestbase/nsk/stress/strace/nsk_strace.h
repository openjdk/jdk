/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include "jni_tools.h"

#ifndef _IS_NSK_STRACE_DEFINED_
#define _IS_NSK_STRACE_DEFINED_

#define JNI_VERSION JNI_VERSION_1_1

#define EXCEPTION_CLEAR (*env)->ExceptionClear(env)
#define EXCEPTION_OCCURRED (*env)->ExceptionOccurred(env)

// Check for pending exception of the specified type
// If it's present, then clear it
#define EXCEPTION_CHECK(exceptionClass, recurDepth) \
        if (EXCEPTION_OCCURRED != NULL) { \
            jobject exception = EXCEPTION_OCCURRED; \
            if (NSK_CPP_STUB3(IsInstanceOf, env, exception, \
                        exceptionClass) == JNI_TRUE) { \
                EXCEPTION_CLEAR; \
                NSK_DISPLAY1("StackOverflowError occurred at depth %d\n", recurDepth); \
            } \
        }

#define FIND_CLASS(_class, _className)\
    if (!NSK_JNI_VERIFY(env, (_class = \
            NSK_CPP_STUB2(FindClass, env, _className)) != NULL))\
        exit(1)

#define GET_OBJECT_CLASS(_class, _obj)\
    if (!NSK_JNI_VERIFY(env, (_class = \
            NSK_CPP_STUB2(GetObjectClass, env, _obj)) != NULL))\
        exit(1)

#define GET_FIELD_ID(_fieldID, _class, _fieldName, _fieldSig)\
    if (!NSK_JNI_VERIFY(env, (_fieldID = \
            NSK_CPP_STUB4(GetFieldID, env, _class,\
                _fieldName, _fieldSig)) != NULL))\
        exit(1)

#define GET_STATIC_FIELD_ID(_fieldID, _class, _fieldName, _fieldSig)\
    if (!NSK_JNI_VERIFY(env, (_fieldID = \
            NSK_CPP_STUB4(GetStaticFieldID, env, _class,\
                _fieldName, _fieldSig)) != NULL))\
        exit(1)

#define GET_STATIC_BOOL_FIELD(_value, _class, _fieldName)\
    GET_STATIC_FIELD_ID(field, _class, _fieldName, "Z");\
    _value = NSK_CPP_STUB3(GetStaticBooleanField, env, _class, field)

#define GET_STATIC_INT_FIELD(_value, _class, _fieldName)\
    GET_STATIC_FIELD_ID(field, _class, _fieldName, "I");\
    _value = NSK_CPP_STUB3(GetStaticIntField, env, _class, field)

#define GET_STATIC_OBJ_FIELD(_value, _class, _fieldName, _fieldSig)\
    GET_STATIC_FIELD_ID(field, _class, _fieldName, _fieldSig);\
    _value = NSK_CPP_STUB3(GetStaticObjectField, env, _class, \
                                field)

#define GET_INT_FIELD(_value, _obj, _class, _fieldName)\
    GET_FIELD_ID(field, _class, _fieldName, "I");\
    _value = NSK_CPP_STUB3(GetIntField, env, _obj, field)

#define SET_INT_FIELD(_obj, _class, _fieldName, _newValue)\
    GET_FIELD_ID(field, _class, _fieldName, "I");\
    NSK_CPP_STUB4(SetIntField, env, _obj, field, _newValue)

#define SET_STATIC_INT_FIELD(_class, _fieldName, _newValue)\
    GET_STATIC_FIELD_ID(field, _class, _fieldName, "I");\
    NSK_CPP_STUB4(SetStaticIntField, env, _class, field, _newValue)

#define GET_OBJ_FIELD(_value, _obj, _class, _fieldName, _fieldSig)\
    GET_FIELD_ID(field, _class, _fieldName, _fieldSig);\
    _value = NSK_CPP_STUB3(GetObjectField, env, _obj, field)

#define GET_STATIC_METHOD_ID(_methodID, _class, _methodName, _sig)\
    if (!NSK_JNI_VERIFY(env, (_methodID = \
            NSK_CPP_STUB4(GetStaticMethodID, env, _class,\
                _methodName, _sig)) != NULL))\
        exit(1)

#define GET_METHOD_ID(_methodID, _class, _methodName, _sig)\
    if (!NSK_JNI_VERIFY(env, (_methodID = \
            NSK_CPP_STUB4(GetMethodID, env, _class,\
                _methodName, _sig)) != NULL))\
        exit(1)

#define CALL_STATIC_VOID_NOPARAM(_class, _methodName)\
    GET_STATIC_METHOD_ID(method, _class, _methodName, "()V");\
    if (!NSK_JNI_VERIFY_VOID(env, NSK_CPP_STUB3(CallStaticVoidMethod, env,\
                            _class, method)))\
        exit(1)

#define CALL_STATIC_VOID(_class, _methodName, _sig, _param)\
    GET_STATIC_METHOD_ID(method, _class, _methodName, _sig);\
    if (!NSK_JNI_VERIFY_VOID(env, NSK_CPP_STUB4(CallStaticVoidMethod, env,\
                                                    _class, method, _param)))\
        exit(1)

#define CALL_VOID_NOPARAM(_obj, _class, _methodName)\
    GET_METHOD_ID(method, _class, _methodName, "()V");\
    if (!NSK_JNI_VERIFY_VOID(env, NSK_CPP_STUB3(CallVoidMethod, env, _obj,\
                                                    method)))\
        exit(1)

#define CALL_VOID(_obj, _class, _methodName, _sig, _param)\
    GET_METHOD_ID(method, _class, _methodName, _sig);\
    if (!NSK_JNI_VERIFY_VOID(env, NSK_CPP_STUB4(CallVoidMethod, env, _obj,\
                                                    method, _param)))\
        exit(1)

#define MONITOR_ENTER(x) \
    NSK_JNI_VERIFY(env, NSK_CPP_STUB2(MonitorEnter, env, x) == 0)

#define MONITOR_EXIT(x) \
    NSK_JNI_VERIFY(env, NSK_CPP_STUB2(MonitorExit, env, x) == 0)

#endif /* _IS_NSK_STRACE_DEFINED_ */
