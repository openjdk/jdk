/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary example code used in javadoc for java.lang.invoke API
 * @compile JavaDocExamplesTest.java
 * @run junit/othervm test.java.lang.invoke.JavaDocExamplesTest
 */

/*
---- To run outside jtreg:
$ $JAVA7X_HOME/bin/javac -cp $JUNIT4_JAR -d /tmp/Classes \
   $DAVINCI/sources/jdk/test/java/lang/invoke/JavaDocExamplesTest.java
$ $JAVA7X_HOME/bin/java   -cp $JUNIT4_JAR:/tmp/Classes \
   -Dtest.java.lang.invoke.JavaDocExamplesTest.verbosity=1 \
     test.java.lang.invoke.JavaDocExamplesTest
----
*/

package test.java.lang.invoke;

import java.lang.invoke.*;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;

import java.lang.reflect.*;
import java.util.*;

import org.junit.*;
import static org.junit.Assert.*;
import static org.junit.Assume.*;


/**
 * @author jrose
 */
public class JavaDocExamplesTest {
    /** Wrapper for running the JUnit tests in this module.
     *  Put JUnit on the classpath!
     */
    public static void main(String... ignore) {
        org.junit.runner.JUnitCore.runClasses(JavaDocExamplesTest.class);
    }
    // How much output?
    static int verbosity = Integer.getInteger("test.java.lang.invoke.JavaDocExamplesTest.verbosity", 0);

{}
static final private Lookup LOOKUP = lookup();
// static final private MethodHandle CONCAT_1 = LOOKUP.findVirtual(String.class,
//     "concat", methodType(String.class, String.class));
// static final private MethodHandle HASHCODE_1 = LOOKUP.findVirtual(Object.class,
//     "hashCode", methodType(int.class));

// form required if ReflectiveOperationException is intercepted:
static final private MethodHandle CONCAT_2, HASHCODE_2;
static {
  try {
    CONCAT_2 = LOOKUP.findVirtual(String.class,
      "concat", methodType(String.class, String.class));
    HASHCODE_2 = LOOKUP.findVirtual(Object.class,
      "hashCode", methodType(int.class));
   } catch (ReflectiveOperationException ex) {
     throw new RuntimeException(ex);
   }
}
{}

    @Test public void testFindVirtual() throws Throwable {
{}
MethodHandle CONCAT_3 = LOOKUP.findVirtual(String.class,
  "concat", methodType(String.class, String.class));
MethodHandle HASHCODE_3 = LOOKUP.findVirtual(Object.class,
  "hashCode", methodType(int.class));
//assertEquals("xy", (String) CONCAT_1.invokeExact("x", "y"));
assertEquals("xy", (String) CONCAT_2.invokeExact("x", "y"));
assertEquals("xy", (String) CONCAT_3.invokeExact("x", "y"));
//assertEquals("xy".hashCode(), (int) HASHCODE_1.invokeExact((Object)"xy"));
assertEquals("xy".hashCode(), (int) HASHCODE_2.invokeExact((Object)"xy"));
assertEquals("xy".hashCode(), (int) HASHCODE_3.invokeExact((Object)"xy"));
{}
    }
    @Test public void testDropArguments() throws Throwable {
        {{
{} /// JAVADOC
MethodHandle cat = lookup().findVirtual(String.class,
  "concat", methodType(String.class, String.class));
assertEquals("xy", (String) cat.invokeExact("x", "y"));
MethodType bigType = cat.type().insertParameterTypes(0, int.class, String.class);
MethodHandle d0 = dropArguments(cat, 0, bigType.parameterList().subList(0,2));
assertEquals(bigType, d0.type());
assertEquals("yz", (String) d0.invokeExact(123, "x", "y", "z"));
            }}
        {{
{} /// JAVADOC
MethodHandle cat = lookup().findVirtual(String.class,
  "concat", methodType(String.class, String.class));
assertEquals("xy", (String) cat.invokeExact("x", "y"));
MethodHandle d0 = dropArguments(cat, 0, String.class);
assertEquals("yz", (String) d0.invokeExact("x", "y", "z"));
MethodHandle d1 = dropArguments(cat, 1, String.class);
assertEquals("xz", (String) d1.invokeExact("x", "y", "z"));
MethodHandle d2 = dropArguments(cat, 2, String.class);
assertEquals("xy", (String) d2.invokeExact("x", "y", "z"));
MethodHandle d12 = dropArguments(cat, 1, int.class, boolean.class);
assertEquals("xz", (String) d12.invokeExact("x", 12, true, "z"));
            }}
    }

    @Test public void testFilterArguments() throws Throwable {
        {{
{} /// JAVADOC
MethodHandle cat = lookup().findVirtual(String.class,
  "concat", methodType(String.class, String.class));
MethodHandle upcase = lookup().findVirtual(String.class,
  "toUpperCase", methodType(String.class));
assertEquals("xy", (String) cat.invokeExact("x", "y"));
MethodHandle f0 = filterArguments(cat, 0, upcase);
assertEquals("Xy", (String) f0.invokeExact("x", "y")); // Xy
MethodHandle f1 = filterArguments(cat, 1, upcase);
assertEquals("xY", (String) f1.invokeExact("x", "y")); // xY
MethodHandle f2 = filterArguments(cat, 0, upcase, upcase);
assertEquals("XY", (String) f2.invokeExact("x", "y")); // XY
            }}
    }

    static void assertEquals(Object exp, Object act) {
        if (verbosity > 0)
            System.out.println("result: "+act);
        Assert.assertEquals(exp, act);
    }

    @Test public void testMethodHandlesSummary() throws Throwable {
        {{
{} /// JAVADOC
Object x, y; String s; int i;
MethodType mt; MethodHandle mh;
MethodHandles.Lookup lookup = MethodHandles.lookup();
// mt is (char,char)String
mt = MethodType.methodType(String.class, char.class, char.class);
mh = lookup.findVirtual(String.class, "replace", mt);
s = (String) mh.invokeExact("daddy",'d','n');
// invokeExact(Ljava/lang/String;CC)Ljava/lang/String;
assert(s.equals("nanny"));
// weakly typed invocation (using MHs.invoke)
s = (String) mh.invokeWithArguments("sappy", 'p', 'v');
assert(s.equals("savvy"));
// mt is (Object[])List
mt = MethodType.methodType(java.util.List.class, Object[].class);
mh = lookup.findStatic(java.util.Arrays.class, "asList", mt);
assert(mh.isVarargsCollector());
x = mh.invokeGeneric("one", "two");
// invokeGeneric(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;
assert(x.equals(java.util.Arrays.asList("one","two")));
// mt is (Object,Object,Object)Object
mt = MethodType.genericMethodType(3);
mh = mh.asType(mt);
x = mh.invokeExact((Object)1, (Object)2, (Object)3);
// invokeExact(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
assert(x.equals(java.util.Arrays.asList(1,2,3)));
// mt is { =&gt; int}
mt = MethodType.methodType(int.class);
mh = lookup.findVirtual(java.util.List.class, "size", mt);
i = (int) mh.invokeExact(java.util.Arrays.asList(1,2,3));
// invokeExact(Ljava/util/List;)I
assert(i == 3);
mt = MethodType.methodType(void.class, String.class);
mh = lookup.findVirtual(java.io.PrintStream.class, "println", mt);
mh.invokeExact(System.out, "Hello, world.");
// invokeExact(Ljava/io/PrintStream;Ljava/lang/String;)V
{}
            }}
    }

    @Test public void testAsVarargsCollector() throws Throwable {
        {{
{} /// JAVADOC
MethodHandle asList = publicLookup()
  .findStatic(Arrays.class, "asList", methodType(List.class, Object[].class))
  .asVarargsCollector(Object[].class);
assertEquals("[]", asList.invokeGeneric().toString());
assertEquals("[1]", asList.invokeGeneric(1).toString());
assertEquals("[two, too]", asList.invokeGeneric("two", "too").toString());
Object[] argv = { "three", "thee", "tee" };
assertEquals("[three, thee, tee]", asList.invokeGeneric(argv).toString());
List ls = (List) asList.invokeGeneric((Object)argv);
assertEquals(1, ls.size());
assertEquals("[three, thee, tee]", Arrays.toString((Object[])ls.get(0)));
            }}
    }

    @Test public void testVarargsCollectorSuppression() throws Throwable {
        {{
{} /// JAVADOC
MethodHandle vamh = publicLookup()
  .findStatic(Arrays.class, "asList", methodType(List.class, Object[].class))
  .asVarargsCollector(Object[].class);
MethodHandle mh = MethodHandles.exactInvoker(vamh.type()).bindTo(vamh);
assert(vamh.type().equals(mh.type()));
assertEquals("[1, 2, 3]", vamh.invokeGeneric(1,2,3).toString());
boolean failed = false;
try { mh.invokeGeneric(1,2,3); }
catch (WrongMethodTypeException ex) { failed = true; }
assert(failed);
{}
            }}
    }
}
