/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
#ifndef __CallHelper_hpp__
#define __CallHelper_hpp__

#include <stdlib.h>
#include <stdio.h>
#include <jni.h>
#undef NDEBUG
#include <assert.h>
#include <string>
#include <algorithm>

/*
 * basis of classes to provide a bunch of checking in native calls to java
 */
class CallHelper {
public:
    CallHelper(JNIEnv* e, const std::string& cname, const std::string& mname, const std::string& sig) :
        classname(cname), method(mname), signature(sig), m(nullptr), env(e) {
        c = env->FindClass(classname.c_str());
        assert (c != nullptr);
    }

protected:

    // emit a message with the call made appended to the message
    void emitErrorMessage(const std::string& msg) {
        std::string nm = classname;
        std::replace(nm.begin(), nm.end(), '/', '.');
        ::fprintf(stderr, "ERROR: %s::%s, %s\n", nm.c_str(), method.c_str(), msg.c_str());
    }

    // check the given object which is expected to be null
    void checkReturnNull(jobject obj) {
        if (obj != nullptr) {
            emitErrorMessage("Null return expected");
            ::exit(-1);
        }
    }

    // check the given object which is expected to NOT be null
    void checkReturnNotNull(jobject obj) {
        if (obj == nullptr) {
            emitErrorMessage("Non-Null return expected");
            ::exit(-1);
        }
    }

    // check if any unexpected exceptions were thrown
    void checkException() {
        if (env->ExceptionOccurred() != nullptr) {
            emitErrorMessage("Exception was thrown");
            env->ExceptionDescribe();
            ::exit(-1);
        }
    }

    // check if an expected exception was thrown
    void checkExpectedExceptionThrown(const std::string& exception) {
         jclass expected = env->FindClass(exception.c_str());
         assert(expected != nullptr);
         jthrowable t = env->ExceptionOccurred();
         if (env->IsInstanceOf(t, expected) == JNI_FALSE) {
            emitErrorMessage("Didn't get the expected " + exception);
            ::exit(-1);
         }
         env->ExceptionClear();
    }

protected:
    std::string classname;
    std::string method;
    std::string signature;
    jclass c;
    jmethodID m;
    JNIEnv* env;
};

/*
 * support for making checked calls on instances of an object
 */
class InstanceCall : public CallHelper {
public:
    InstanceCall(JNIEnv* e, const std::string& cname, const std::string& mname, const std::string& sig)
        : CallHelper(e, cname, mname, sig) {

        m = env->GetMethodID(c, method.c_str(), signature.c_str());
        assert(m != nullptr);
    }

    // call on the given object, checking for exceptions and that the return is not null
    jobject callReturnNotNull(jobject obj) {
        jobject robj = call(obj);
        checkReturnNotNull(robj);
        return robj;
    }

    // call on the given object with an argument,
    // checking for exceptions and that the return is not null
    jobject callReturnNotNull(jobject obj, jobject arg) {
        jobject robj = call(obj, arg);
        checkReturnNotNull(robj);
        return robj;
    }

    // call on the given object, checking for exceptions and that the return is null
    jobject callReturnIsNull(jobject obj) {
        jobject robj = call(obj);
        checkReturnNull(robj);
        return robj;
    }

    // call on the given object with an argument,
    // checking for exceptions and that the return is null
    jobject callReturnIsNull(jobject obj, jobject arg) {
        jobject robj = call(obj, arg);
        checkReturnNull(robj);
        return robj;
    }

    // call a void method checking if exceptions were thrown
    void callVoidMethod(jobject obj) {
        env->CallVoidMethod(obj, m);
        checkException();
    }

    jobject call(jobject obj) {
        jobject robj = env->CallObjectMethod(obj, m);
        checkException();
        return robj;
    }

    jobject call(jobject obj, jobject arg) {
        jobject robj = env->CallObjectMethod(obj, m, arg);
        checkException();
        return robj;
    }

};

/*
 * support for making checked static calls
 */
class StaticCall : public CallHelper {
public:
    StaticCall(JNIEnv* e, const std::string& cname, const std::string& mname, const std::string& sig)
        : CallHelper(e, cname, mname, sig) {

        m = env->GetStaticMethodID(c, method.c_str(), signature.c_str());
        assert(m != nullptr);
    }

    // call a method returning an object checking for exceptions and
    // the return value is not null.
    jobject callReturnNotNull(jobject arg) {
        jobject robj = env->CallStaticObjectMethod(c, m, arg);
        checkException();
        checkReturnNotNull(robj);
        return robj;
    }

    // call a void method checking if any exceptions thrown
    void callVoidMethod() {
        env->CallStaticVoidMethod(c, m);
        checkException();
    }

    // call method returning boolean that is expected to throw the
    // given exception
    void callBooleanMethodWithException(const std::string& exception) {
        env->CallStaticBooleanMethod(c, m);
        checkExpectedExceptionThrown(exception);
    }

    // call method returning an object that is expected to throw the
    // given exception
    void callObjectMethodWithException(const std::string& exception) {
        env->CallStaticObjectMethod(c, m);
        checkExpectedExceptionThrown(exception);
    }
};

void emitErrorMessageAndExit(const std::string& msg) {
    ::fprintf(stderr, "ERROR: %s\n", msg.c_str());
    ::exit(-1);
}

#endif // __CallHelper_hpp__

