/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @summary example code used in javadoc for java.dyn API
 * @compile -XDallowTransitionalJSR292=no JavaDocExamplesTest.java
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableMethodHandles test.java.dyn.JavaDocExamplesTest
 */

/*
---- To run outside jtreg:
$ $JAVA7X_HOME/bin/javac -cp $JUNIT4_JAR -d /tmp/Classes \
   $DAVINCI/sources/jdk/test/java/dyn/JavaDocExamplesTest.java
$ $JAVA7X_HOME/bin/java   -cp $JUNIT4_JAR:/tmp/Classes \
   -XX:+UnlockExperimentalVMOptions -XX:+EnableMethodHandles \
   -Dtest.java.dyn.JavaDocExamplesTest.verbosity=1 \
     test.java.dyn.JavaDocExamplesTest
----
*/

package test.java.dyn;

import java.dyn.*;
import static java.dyn.MethodHandles.*;
import static java.dyn.MethodType.*;

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
    static int verbosity = Integer.getInteger("test.java.dyn.JavaDocExamplesTest.verbosity", 0);

{}
static final private Lookup LOOKUP = lookup();
// static final private MethodHandle CONCAT_1 = LOOKUP.findVirtual(String.class,
//     "concat", methodType(String.class, String.class));
// static final private MethodHandle HASHCODE_1 = LOOKUP.findVirtual(Object.class,
//     "hashCode", methodType(int.class));

// form required if NoAccessException is intercepted:
static final private MethodHandle CONCAT_2, HASHCODE_2;
static {
  try {
    CONCAT_2 = LOOKUP.findVirtual(String.class,
      "concat", methodType(String.class, String.class));
    HASHCODE_2 = LOOKUP.findVirtual(Object.class,
      "hashCode", methodType(int.class));
   } catch (NoAccessException ex) {
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
assertEquals("xy", (String) CONCAT_2.<String>invokeExact("x", "y"));
assertEquals("xy", (String) CONCAT_3.<String>invokeExact("x", "y"));
//assertEquals("xy".hashCode(), (int) HASHCODE_1.<int>invokeExact((Object)"xy"));
assertEquals("xy".hashCode(), (int) HASHCODE_2.<int>invokeExact((Object)"xy"));
assertEquals("xy".hashCode(), (int) HASHCODE_3.<int>invokeExact((Object)"xy"));
{}
    }
    @Test public void testDropArguments() throws Throwable {
        {{
{} /// JAVADOC
MethodHandle cat = lookup().findVirtual(String.class,
  "concat", methodType(String.class, String.class));
cat = cat.asType(methodType(Object.class, String.class, String.class)); /*(String)*/
assertEquals("xy", /*(String)*/ cat.invokeExact("x", "y"));
MethodHandle d0 = dropArguments(cat, 0, String.class);
assertEquals("yz", /*(String)*/ d0.invokeExact("x", "y", "z"));
MethodHandle d1 = dropArguments(cat, 1, String.class);
assertEquals("xz", /*(String)*/ d1.invokeExact("x", "y", "z"));
MethodHandle d2 = dropArguments(cat, 2, String.class);
assertEquals("xy", /*(String)*/ d2.invokeExact("x", "y", "z"));
MethodHandle d12 = dropArguments(cat, 1, int.class, boolean.class);
assertEquals("xz", /*(String)*/ d12.invokeExact("x", 12, true, "z"));
            }}
    }

    @Test public void testFilterArguments() throws Throwable {
        {{
{} /// JAVADOC
MethodHandle cat = lookup().findVirtual(String.class,
  "concat", methodType(String.class, String.class));
cat = cat.asType(methodType(Object.class, String.class, String.class)); /*(String)*/
MethodHandle upcase = lookup().findVirtual(String.class,
  "toUpperCase", methodType(String.class));
assertEquals("xy", /*(String)*/ cat.invokeExact("x", "y")); // xy
MethodHandle f0 = filterArguments(cat, 0, upcase);
assertEquals("Xy", /*(String)*/ f0.invokeExact("x", "y")); // Xy
MethodHandle f1 = filterArguments(cat, 1, upcase);
assertEquals("xY", /*(String)*/ f1.invokeExact("x", "y")); // xY
MethodHandle f2 = filterArguments(cat, 0, upcase, upcase);
assertEquals("XY", /*(String)*/ f2.invokeExact("x", "y")); // XY
            }}
    }

    static void assertEquals(Object exp, Object act) {
        if (verbosity > 0)
            System.out.println("result: "+act);
        Assert.assertEquals(exp, act);
    }

    @Test public void testVolatileCallSite() throws Throwable {
        {{
{} /// JAVADOC
MethodHandle strcat = MethodHandles.lookup()
  .findVirtual(String.class, "concat", MethodType.methodType(String.class, String.class));
MethodHandle trueCon  = MethodHandles.constant(boolean.class, true);
MethodHandle falseCon = MethodHandles.constant(boolean.class, false);
VolatileCallSite switcher = new VolatileCallSite(trueCon, falseCon);
// following steps may be repeated to re-use the same switcher:
MethodHandle worker1 = strcat;
MethodHandle worker2 = MethodHandles.permuteArguments(strcat, strcat.type(), 1, 0);
MethodHandle worker = MethodHandles.guardWithTest(switcher.dynamicInvoker(), worker1, worker2);
System.out.println((String) worker.invokeExact("met", "hod"));  // method
switcher.invalidate();
System.out.println((String) worker.invokeExact("met", "hod"));  // hodmet
            }}
    }

static MethodHandle asList;
    @Test public void testWithTypeHandler() throws Throwable {
        {{
{} /// JAVADOC
MethodHandle makeEmptyList = MethodHandles.constant(List.class, Arrays.asList());
MethodHandle asList = lookup()
  .findStatic(Arrays.class, "asList", methodType(List.class, Object[].class));

JavaDocExamplesTest.asList = asList;
/*
static MethodHandle collectingTypeHandler(MethodHandle base, MethodType newType) {
  return asList.asCollector(Object[].class, newType.parameterCount()).asType(newType);
}
*/

MethodHandle collectingTypeHandler = lookup()
  .findStatic(lookup().lookupClass(), "collectingTypeHandler",
     methodType(MethodHandle.class, MethodHandle.class, MethodType.class));
MethodHandle makeAnyList = makeEmptyList.withTypeHandler(collectingTypeHandler);

System.out.println(makeAnyList.invokeGeneric());
System.out.println(makeAnyList.invokeGeneric(1));
System.out.println(makeAnyList.invokeGeneric("two", "too"));
            }}
    }

static MethodHandle collectingTypeHandler(MethodHandle base, MethodType newType) {
    //System.out.println("Converting "+asList+" to "+newType);
    MethodHandle conv = asList.asCollector(Object[].class, newType.parameterCount()).asType(newType);
    //System.out.println(" =>"+conv);
    return conv;
}

}
