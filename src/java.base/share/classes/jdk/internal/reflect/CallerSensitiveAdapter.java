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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;

/**
 * A method annotated @CallerSensitiveAdapter is an adapter method corresponding
 * to a caller-sensitive method, which is annotated with @CallerSensitive.
 *
 * A caller-sensitive adapter is private and has the same name as its
 * corresponding caller-sensitive method with a trailing caller class parameter.
 *
 * When a caller-sensitive method is invoked via Method::invoke or MethodHandle
 * the core reflection and method handle implementation will find if
 * an @CallerSensitiveAdapter method for that CSM is present. If present,
 * the runtime will invoke the adapter method with the caller class
 * argument instead. This special calling sequence ensures that the same caller
 * class is passed to a caller-sensitive method via Method::invoke,
 * MethodHandle::invokeExact, or a mix of these methods.
 *
 * For example, CSM::returnCallerClass is a caller-sensitive method
 * with an adapter method:
 * {@code
 * class CSM {
 *     @CallerSensitive
 *     static Class<?> returnCallerClass() {
 *         return returnCallerClass(Reflection.getCallerClass());
 *     }
 *     @CallerSensitiveAdapter
 *     private static Class<?> returnCallerClass(Class<?> caller) {
 *         return caller;
 *     }
 * }
 *
 * class Test {
 *     void test() throws Throwable {
 *         // calling CSM::returnCallerClass via reflection
 *         var csm = CSM.class.getMethod("returnCallerClass");
 *         // expect Foo to be the caller class
 *         var caller = csm.invoke(null);
 *         assert(caller == Test.class);
 *     }
 *     void test2() throws Throwable {
 *         // method handle for Method::invoke
 *         MethodHandle mh = MethodHandles.lookup().findVirtual(Method.class, "invoke",
 *                                  methodType(Object.class, Object.class, Object[].class));
 *         var csm = CSM.class.getMethod("returnCallerClass");
 *         // invoke Method::invoke via method handle and the target method
 *         // being invoked reflectively is CSM::returnCallerClass
 *         var caller = mh.invoke(csm, null, null);
 *         assert(caller == Test.class);
 *     }
 * }
 * }
 *
 *
 * Both CSM::returnCallerClass and Method::invoke can have an adapter method
 * with a caller-class parameter defined. Test::test calls CSM::returnCallerClass
 * via Method::invoke which does the stack walking to find the caller's class.
 * It will pass the caller's class directly to the adapter method for
 * CSM::returnCallerClass.
 *
 * Similarly, Test::test2 invokes the method handle of Method::invoke to
 * call CSM::returnCallerClass reflectively.  In that case, MethodHandle::invokeExact
 * uses the lookup class of the Lookup object producing the method handle
 * as the caller's class, so no stack walking involved. The lookup class is Test.
 * It will invoke the adapter method of Method::invoke with Test as the caller's
 * class, which in turn invokes the adapter method of CSM::returnCallerClass
 * with Test as the caller. The calling sequence eliminates the need for
 * multiple stack walks when a caller-sensitive method is invoked reflectively.
 *
 * For caller-sensitive methods that require an exact caller class, the adapter
 * method must be defined for correctness. {@link java.lang.invoke.MethodHandles#lookup()}
 * and {@link ClassLoader#registerAsParallelCapable()} are the only two methods
 * in the JDK that need the exact caller class.
 *
 * On the other hand, for caller-sensitive methods that use the caller's class
 * for access checks or security permission checks, i.e., based on its runtime
 * package, defining loader, or protection domain, the adapter method is optional.
 */
@Retention(RetentionPolicy.CLASS)
@Target({METHOD})
public @interface CallerSensitiveAdapter {
}
