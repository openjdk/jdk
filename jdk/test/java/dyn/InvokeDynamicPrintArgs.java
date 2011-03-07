/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary smoke test for invokedynamic instructions
 * @build indify.Indify
 * @compile InvokeDynamicPrintArgs.java
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableInvokeDynamic
 *      indify.Indify
 *      --verify-specifier-count=3 --transitionalJSR292=false
 *      --expand-properties --classpath ${test.classes}
 *      --java test.java.dyn.InvokeDynamicPrintArgs --check-output
 */

package test.java.dyn;

import org.junit.Test;

import java.util.*;
import java.io.*;

import java.dyn.*;
import static java.dyn.MethodHandles.*;
import static java.dyn.MethodType.*;

public class InvokeDynamicPrintArgs {
    public static void main(String... av) throws Throwable {
        if (av.length > 0)  openBuf();  // --check-output mode
        System.out.println("Printing some argument lists, starting with a empty one:");
        INDY_nothing().invokeExact();                 // BSM specifier #0 = {bsm}
        INDY_bar().invokeExact("bar arg", 1);         // BSM specifier #1 = {bsm2, Void.class, "void type"}
        INDY_bar2().invokeExact("bar2 arg", 222);     // BSM specifier #1 = (same)
        INDY_baz().invokeExact("baz arg", 2, 3.14);   // BSM specifier #2 = {bsm2, 1234.5}
        INDY_foo().invokeExact("foo arg");            // BSM specifier #0 = (same)
        // Hence, BSM specifier count should be 3.  See "--verify-specifier-count=3" above.
        System.out.println("Done printing argument lists.");
        closeBuf();
    }

    @Test
    public void testInvokeDynamicPrintArgs() throws IOException {
        System.err.println(System.getProperties());
        String testClassPath = System.getProperty("build.test.classes.dir");
        if (testClassPath == null)  throw new RuntimeException();
        String[] args = new String[]{
            "--verify-specifier-count=3", "--transitionalJSR292=false",
            "--expand-properties", "--classpath", testClassPath,
            "--java", "test.java.dyn.InvokeDynamicPrintArgs", "--check-output"
        };
        System.err.println("Indify: "+Arrays.toString(args));
        indify.Indify.main(args);
    }

    private static PrintStream oldOut;
    private static ByteArrayOutputStream buf;
    private static void openBuf() {
        oldOut = System.out;
        buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf));
    }
    private static void closeBuf() {
        if (buf == null)  return;
        System.out.flush();
        System.setOut(oldOut);
        String[] haveLines = new String(buf.toByteArray()).split("[\n\r]+");
        for (String line : haveLines)  System.out.println(line);
        Iterator<String> iter = Arrays.asList(haveLines).iterator();
        for (String want : EXPECT_OUTPUT) {
            String have = iter.hasNext() ? iter.next() : "[EOF]";
            if (want.equals(have))  continue;
            System.err.println("want line: "+want);
            System.err.println("have line: "+have);
            throw new AssertionError("unexpected output: "+have);
        }
        if (iter.hasNext())
            throw new AssertionError("unexpected output: "+iter.next());
    }
    private static final String[] EXPECT_OUTPUT = {
        "Printing some argument lists, starting with a empty one:",
        "[test.java.dyn.InvokeDynamicPrintArgs, nothing, ()void][]",
        "[test.java.dyn.InvokeDynamicPrintArgs, bar, (String,int)void, class java.lang.Void, void type!, 1, 234.5, 67.5, 89][bar arg, 1]",
        "[test.java.dyn.InvokeDynamicPrintArgs, bar2, (String,int)void, class java.lang.Void, void type!, 1, 234.5, 67.5, 89][bar2 arg, 222]",
        "[test.java.dyn.InvokeDynamicPrintArgs, baz, (String,int,double)void, 1234.5][baz arg, 2, 3.14]",
        "[test.java.dyn.InvokeDynamicPrintArgs, foo, (String)void][foo arg]",
        "Done printing argument lists."
    };

    private static void printArgs(Object bsmInfo, Object... args) {
        System.out.println(bsmInfo+Arrays.deepToString(args));
    }
    private static MethodHandle MH_printArgs() throws ReflectiveOperationException {
        shouldNotCallThis();
        return lookup().findStatic(lookup().lookupClass(),
                                   "printArgs", methodType(void.class, Object.class, Object[].class));
    }

    private static CallSite bsm(Lookup caller, String name, MethodType type) throws ReflectiveOperationException {
        // ignore caller and name, but match the type:
        Object bsmInfo = Arrays.asList(caller, name, type);
        return new ConstantCallSite(MH_printArgs().bindTo(bsmInfo).asCollector(Object[].class, type.parameterCount()).asType(type));
    }
    private static MethodType MT_bsm() {
        shouldNotCallThis();
        return methodType(CallSite.class, Lookup.class, String.class, MethodType.class);
    }
    private static MethodHandle MH_bsm() throws ReflectiveOperationException {
        shouldNotCallThis();
        return lookup().findStatic(lookup().lookupClass(), "bsm", MT_bsm());
    }

    private static CallSite bsm2(Lookup caller, String name, MethodType type, Object... arg) throws ReflectiveOperationException {
        // ignore caller and name, but match the type:
        List<Object> bsmInfo = new ArrayList<>(Arrays.asList(caller, name, type));
        bsmInfo.addAll(Arrays.asList((Object[])arg));
        return new ConstantCallSite(MH_printArgs().bindTo(bsmInfo).asCollector(Object[].class, type.parameterCount()).asType(type));
    }
    private static MethodType MT_bsm2() {
        shouldNotCallThis();
        return methodType(CallSite.class, Lookup.class, String.class, MethodType.class, Object[].class);
    }
    private static MethodHandle MH_bsm2() throws ReflectiveOperationException {
        shouldNotCallThis();
        return lookup().findStatic(lookup().lookupClass(), "bsm2", MT_bsm2());
    }

    private static MethodHandle INDY_nothing() throws Throwable {
        shouldNotCallThis();
        return ((CallSite) MH_bsm().invokeGeneric(lookup(),
                                                  "nothing", methodType(void.class)
                                                  )).dynamicInvoker();
    }
    private static MethodHandle INDY_foo() throws Throwable {
        shouldNotCallThis();
        return ((CallSite) MH_bsm().invokeGeneric(lookup(),
                                                  "foo", methodType(void.class, String.class)
                                                  )).dynamicInvoker();
    }
    private static MethodHandle INDY_bar() throws Throwable {
        shouldNotCallThis();
        return ((CallSite) MH_bsm2().invokeGeneric(lookup(),
                                                  "bar", methodType(void.class, String.class, int.class)
                                                  , new Object[] { Void.class, "void type!",
                                                                   1, 234.5F, 67.5, (long)89 }
                                                  )).dynamicInvoker();
    }
    private static MethodHandle INDY_bar2() throws Throwable {
        shouldNotCallThis();
        return ((CallSite) MH_bsm2().invokeGeneric(lookup(),
                                                  "bar2", methodType(void.class, String.class, int.class)
                                                  , new Object[] { Void.class, "void type!",
                                                                   1, 234.5F, 67.5, (long)89 }
                                                  )).dynamicInvoker();
    }
    private static MethodHandle INDY_baz() throws Throwable {
        shouldNotCallThis();
        return ((CallSite) MH_bsm2().invokeGeneric(lookup(),
                                                  "baz", methodType(void.class, String.class, int.class, double.class)
                                                  , 1234.5
                                                  )).dynamicInvoker();
    }

    private static void shouldNotCallThis() {
        // if this gets called, the transformation has not taken place
        if (System.getProperty("InvokeDynamicPrintArgs.allow-untransformed") != null)  return;
        throw new AssertionError("this code should be statically transformed away by Indify");
    }
}
