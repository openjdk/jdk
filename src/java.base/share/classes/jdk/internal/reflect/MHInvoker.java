/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.reflect;

/**
 * MHInvoker::invoke is the implementation body for
 * Method::invoke(Object obj, Object[] args) and Constructor::newInstance
 * that invokes the MethodHandle for the reflected method and constructor.
 *
 * Methods whose have <= 3 formal parameters use the specialized version.
 * For example, method with 3 arguments will use:
 * MHInvoker::invoke(Object obj, Object arg1, Object arg2, Object arg3)
 *
 * Caller-sensitive methods with an adapter will use the invoke method
 * taking a caller class parameter.
 *
 * See MethodHandleAccessorFactory for the specialization.
 */
public interface MHInvoker {
    // non-specialized method handle invocation also with trailing caller class parameter
    default Object invoke(Object obj, Object[] args) throws Throwable {
        throw new UnsupportedOperationException();
    }
    default Object invoke(Object obj, Object[] args, Class<?> caller) throws Throwable {
        throw new UnsupportedOperationException();
    }

    // specialized version for number of arguments <= 3
    default Object invoke(Object obj) throws Throwable {
        throw new UnsupportedOperationException();
    }
    default Object invoke(Object obj, Object arg1) throws Throwable {
        throw new UnsupportedOperationException();
    }
    default Object invoke(Object obj, Object arg1, Object arg2) throws Throwable {
        throw new UnsupportedOperationException();
    }
    default Object invoke(Object obj, Object arg1, Object arg2, Object arg3) throws Throwable {
        throw new UnsupportedOperationException();
    }
    default Object invoke(Object obj, Class<?> caller) throws Throwable {
        throw new UnsupportedOperationException();
    }
    default Object invoke(Object obj, Object arg1, Class<?> caller) throws Throwable {
        throw new UnsupportedOperationException();
    }
    default Object invoke(Object obj, Object arg1, Object arg2, Class<?> caller) throws Throwable {
        throw new UnsupportedOperationException();
    }
    default Object invoke(Object obj, Object arg1, Object arg2, Object arg3, Class<?> caller) throws Throwable {
        throw new UnsupportedOperationException();
    }

    // for Constructor::newInstance
    default Object invoke(Object[] args) throws Throwable {
        throw new UnsupportedOperationException();
    }
    default Object invoke() throws Throwable {
        throw new UnsupportedOperationException();
    }

//    No need to define them as they are already covered by the above methods
//
//    default Object invoke(Object arg1) throws Throwable {
//        throw new UnsupportedOperationException();
//    }
//    default Object invoke(Object arg1, Object arg2) throws Throwable {
//        throw new UnsupportedOperationException();
//    }
//    default Object invoke(Object arg1, Object arg2, Object arg3) throws Throwable {
//        throw new UnsupportedOperationException();
//    }
}
