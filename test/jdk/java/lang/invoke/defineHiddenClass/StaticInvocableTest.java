/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8266925
 * @summary hidden class members can't be statically invocable
 * @modules java.base/jdk.internal.misc
 * @build java.base/*
 * @run junit StaticInvocableTest
 */

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.LookupHelper;
import java.lang.reflect.AccessFlag;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;
import org.junit.jupiter.api.Test;

public class StaticInvocableTest {
    public static void main(String[] args) throws Throwable {
        StaticInvocableTest test = new StaticInvocableTest();
        test.testJavaLang();
        test.testJavaUtil();
        test.testJdkInternalMisc();
        test.testJavaLangInvoke();
        test.testProhibitedJavaPkg();
        System.out.println("TEST PASSED");
    }

    // Test hidden classes from different packages
    // (see j.l.i.InvokerBytecodeGenerator::isStaticallyInvocable).
    @Test public void testJavaLang()        throws Throwable { test("java/lang");         }
    @Test public void testJavaUtil()        throws Throwable { test("java/util");         }
    @Test public void testJdkInternalMisc() throws Throwable { test("jdk/internal/misc"); }
    @Test public void testJavaLangInvoke()  throws Throwable { test("java/lang/invoke");  }
    @Test public void testProhibitedJavaPkg() throws Throwable {
       try {
           test("java/prohibited");
       } catch (IllegalArgumentException e) {
           return;
       }
       throw new RuntimeException("Expected SecurityException");
     }

    private static void test(String pkg) throws Throwable {
        byte[] bytes = dumpClass(pkg);
        Lookup lookup;
        if (pkg.equals("java/prohibited")) {
            StaticInvocableTest sampleclass = new StaticInvocableTest();
            lookup = LookupHelper.newLookup(sampleclass.getClass());
        } else if (pkg.equals("java/lang")) {
            lookup = LookupHelper.newLookup(Object.class);
        } else if (pkg.equals("java/util")) {
            lookup = LookupHelper.newLookup(java.util.ArrayList.class);
        } else if (pkg.equals("jdk/internal/misc")) {
            lookup = LookupHelper.newLookup(jdk.internal.misc.Signal.class);
        } else if (pkg.equals("java/lang/invoke")) {
            lookup = LookupHelper.newLookup(java.lang.invoke.CallSite.class);
        } else {
            throw new RuntimeException("Unexpected pkg: " + pkg);
        }

        // Define hidden class
        Lookup l = lookup.defineHiddenClass(bytes, true);

        MethodType t = MethodType.methodType(Object.class, int.class);
        MethodHandle target = l.findStatic(l.lookupClass(), "get", t);

        // Wrap target into LF (convert) to get "target" referenced from LF
        MethodHandle wrappedMH = target.asType(MethodType.methodType(Object.class, Integer.class));

        // Invoke enough times to provoke LF compilation to bytecode.
        for (int i = 0; i<100; i++) {
            Object r = wrappedMH.invokeExact((Integer)1);
        }
    }

    /*
     * Constructs bytecode for the following class:
     * public class pkg.MyClass {
     *     MyClass() {}
     *     public Object get(int i) { return null; }
     * }
     */
    public static byte[] dumpClass(String pkg) {
        return ClassFile.of().build(ClassDesc.of(pkg.replace('/', '.'), "MyClass"), clb -> {
            clb.withSuperclass(CD_Object);
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.SUPER);
            clb.withMethodBody(INIT_NAME, MTD_void, 0, cob -> {
                cob.aload(0);
                cob.invokespecial(CD_Object, INIT_NAME, MTD_void);
                cob.return_();
            });
            clb.withMethodBody("get", MethodTypeDesc.of(CD_Object, CD_int),
                    ACC_PUBLIC | ACC_STATIC, cob -> {
                cob.aconst_null();
                cob.areturn();
            });
        });
    }
}
