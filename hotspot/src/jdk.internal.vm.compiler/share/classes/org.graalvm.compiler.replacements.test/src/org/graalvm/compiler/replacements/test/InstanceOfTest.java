/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.phases.common.AbstractInliningPhase;

import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.Mark;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.meta.JavaTypeProfile;

/**
 * Tests the implementation of instanceof, allowing profiling information to be manually specified.
 */
public class InstanceOfTest extends TypeCheckTest {

    public InstanceOfTest() {
        getSuites().getHighTier().findPhase(AbstractInliningPhase.class).remove();
    }

    @Override
    protected void replaceProfile(StructuredGraph graph, JavaTypeProfile profile) {
        InstanceOfNode ion = graph.getNodes().filter(InstanceOfNode.class).first();
        if (ion != null) {
            ion.setProfile(profile, graph.start());
        }
    }

    @Test
    public void test1() {
        test("isString", profile(), "object");
        test("isString", profile(String.class), "object");

        test("isString", profile(), Object.class);
        test("isString", profile(String.class), Object.class);
    }

    @Test
    public void test2() {
        test("isStringInt", profile(), "object");
        test("isStringInt", profile(String.class), "object");

        test("isStringInt", profile(), Object.class);
        test("isStringInt", profile(String.class), Object.class);
    }

    @Test
    public void test201() {
        test("isStringIntComplex", profile(), "object");
        test("isStringIntComplex", profile(String.class), "object");

        test("isStringIntComplex", profile(), Object.class);
        test("isStringIntComplex", profile(String.class), Object.class);
    }

    @Test
    public void test3() {
        Throwable throwable = new Exception();
        test("isThrowable", profile(), throwable);
        test("isThrowable", profile(Throwable.class), throwable);
        test("isThrowable", profile(Exception.class, Error.class), throwable);

        test("isThrowable", profile(), Object.class);
        test("isThrowable", profile(Throwable.class), Object.class);
        test("isThrowable", profile(Exception.class, Error.class), Object.class);
    }

    @Test
    public void test301() {
        onlyFirstIsException(new Exception(), new Error());
        test("onlyFirstIsException", profile(), new Exception(), new Error());
        test("onlyFirstIsException", profile(), new Error(), new Exception());
        test("onlyFirstIsException", profile(), new Exception(), new Exception());
        test("onlyFirstIsException", profile(), new Error(), new Error());
    }

    @Test
    public void test4() {
        Throwable throwable = new Exception();
        test("isThrowableInt", profile(), throwable);
        test("isThrowableInt", profile(Throwable.class), throwable);
        test("isThrowableInt", profile(Exception.class, Error.class), throwable);

        test("isThrowableInt", profile(), Object.class);
        test("isThrowableInt", profile(Throwable.class), Object.class);
        test("isThrowableInt", profile(Exception.class, Error.class), Object.class);
    }

    @Test
    public void test5() {
        Map<?, ?> map = new HashMap<>();
        test("isMap", profile(), map);
        test("isMap", profile(HashMap.class), map);
        test("isMap", profile(TreeMap.class, HashMap.class), map);

        test("isMap", profile(), Object.class);
        test("isMap", profile(HashMap.class), Object.class);
        test("isMap", profile(TreeMap.class, HashMap.class), Object.class);
        test("isMap", profile(String.class, HashMap.class), Object.class);
    }

    @Test
    public void test6() {
        Map<?, ?> map = new HashMap<>();
        test("isMapInt", profile(), map);
        test("isMapInt", profile(HashMap.class), map);
        test("isMapInt", profile(TreeMap.class, HashMap.class), map);

        test("isMapInt", profile(), Object.class);
        test("isMapInt", profile(HashMap.class), Object.class);
        test("isMapInt", profile(TreeMap.class, HashMap.class), Object.class);
    }

    @Test
    public void test7() {
        Object o = new Depth13();
        test("isDepth12", profile(), o);
        test("isDepth12", profile(Depth13.class), o);
        test("isDepth12", profile(Depth13.class, Depth14.class), o);

        o = "not a depth";
        test("isDepth12", profile(), o);
        test("isDepth12", profile(Depth13.class), o);
        test("isDepth12", profile(Depth13.class, Depth14.class), o);
        test("isDepth12", profile(String.class, HashMap.class), o);
    }

    @Test
    public void test8() {
        Object o = new Depth13();
        test("isDepth12Int", profile(), o);
        test("isDepth12Int", profile(Depth13.class), o);
        test("isDepth12Int", profile(Depth13.class, Depth14.class), o);

        o = "not a depth";
        test("isDepth12Int", profile(), o);
        test("isDepth12Int", profile(Depth13.class), o);
        test("isDepth12Int", profile(Depth13.class, Depth14.class), o);
    }

    public static boolean isString(Object o) {
        return o instanceof String;
    }

    public static int isStringInt(Object o) {
        if (o instanceof String) {
            return id(1);
        }
        return id(0);
    }

    public static int isStringIntComplex(Object o) {
        if (o instanceof String || o instanceof Integer) {
            return id(o instanceof String ? 1 : 0);
        }
        return id(0);
    }

    public static int id(int value) {
        return value;
    }

    public static boolean isThrowable(Object o) {
        return ((Throwable) o) instanceof Exception;
    }

    public static int onlyFirstIsException(Throwable t1, Throwable t2) {
        if (t1 instanceof Exception ^ t2 instanceof Exception) {
            return t1 instanceof Exception ? 1 : -1;
        }
        return -1;
    }

    public static int isThrowableInt(Object o) {
        int result = o instanceof Throwable ? 4 : 5;
        if (o instanceof Throwable) {
            return id(4);
        }
        return result;
    }

    public static boolean isMap(Object o) {
        return o instanceof Map;
    }

    public static int isMapInt(Object o) {
        if (o instanceof Map) {
            return id(1);
        }
        return id(0);
    }

    public static boolean isDepth12(Object o) {
        return o instanceof Depth12;
    }

    public static int isDepth12Int(Object o) {
        if (o instanceof Depth12) {
            return id(0);
        }
        return id(0);
    }

    abstract static class MySite {

        final int offset;

        MySite(int offset) {
            this.offset = offset;
        }
    }

    static class MyMark extends MySite {

        MyMark(int offset) {
            super(offset);
        }
    }

    abstract static class MySafepoint extends MySite {

        MySafepoint(int offset) {
            super(offset);
        }
    }

    static class MyCall extends MySafepoint {

        MyCall(int offset) {
            super(offset);
        }
    }

    @Test
    public void test9() {
        MyCall callAt63 = new MyCall(63);
        MyMark markAt63 = new MyMark(63);
        test("compareMySites", callAt63, callAt63);
        test("compareMySites", callAt63, markAt63);
        test("compareMySites", markAt63, callAt63);
        test("compareMySites", markAt63, markAt63);
    }

    public static int compareMySites(MySite s1, MySite s2) {
        if (s1.offset == s2.offset && (s1 instanceof MyMark ^ s2 instanceof MyMark)) {
            return s1 instanceof MyMark ? -1 : 1;
        }
        return s1.offset - s2.offset;
    }

    @Test
    public void test10() {
        Call callAt63 = new Call(null, 63, 5, true, null);
        Mark markAt63 = new Mark(63, "1");
        test("compareSites", callAt63, callAt63);
        test("compareSites", callAt63, markAt63);
        test("compareSites", markAt63, callAt63);
        test("compareSites", markAt63, markAt63);
    }

    public static int compareSites(Site s1, Site s2) {
        if (s1.pcOffset == s2.pcOffset && (s1 instanceof Mark ^ s2 instanceof Mark)) {
            return s1 instanceof Mark ? -1 : 1;
        }
        return s1.pcOffset - s2.pcOffset;
    }

    /**
     * This test exists to show the kind of pattern that is be optimizable by
     * {@code removeIntermediateMaterialization()} in {@link IfNode}.
     * <p>
     * The test exists in this source file as the transformation was originally motivated by the
     * need to remove use of special JumpNodes in the {@code InstanceOfSnippets}.
     */
    @Test
    public void testRemoveIntermediateMaterialization() {
        List<String> list = Arrays.asList("1", "2", "3", "4");
        test("removeIntermediateMaterialization", profile(), list, "2", "yes", "no");
        test("removeIntermediateMaterialization", profile(), list, null, "yes", "no");
        test("removeIntermediateMaterialization", profile(), null, "2", "yes", "no");
    }

    public static String removeIntermediateMaterialization(List<Object> list, Object e, String a, String b) {
        boolean test;
        if (list == null || e == null) {
            test = false;
        } else {
            test = false;
            for (Object i : list) {
                if (i.equals(e)) {
                    test = true;
                    break;
                }
            }
        }
        if (test) {
            return a;
        }
        return b;
    }

    abstract static class A {
    }

    static class B extends A {
    }

    static class C extends B {
    }

    abstract static class D extends C {
    }

    public static boolean isArrayOfA(Object o) {
        return o instanceof A[];
    }

    public static boolean isArrayOfB(Object o) {
        return o instanceof B[];
    }

    public static boolean isArrayOfC(Object o) {
        return o instanceof C[];
    }

    public static boolean isArrayOfD(Object o) {
        return o instanceof D[];
    }

    @Test
    public void testArray() {
        Object aArray = new A[10];
        test("isArrayOfA", aArray);

        Object bArray = new B[10];
        test("isArrayOfA", aArray);
        test("isArrayOfA", bArray);
        test("isArrayOfB", aArray);
        test("isArrayOfB", bArray);

        Object cArray = new C[10];
        test("isArrayOfA", aArray);
        test("isArrayOfA", bArray);
        test("isArrayOfA", cArray);
        test("isArrayOfB", aArray);
        test("isArrayOfB", bArray);
        test("isArrayOfB", cArray);
        test("isArrayOfC", aArray);
        test("isArrayOfC", bArray);
        test("isArrayOfC", cArray);

        Object dArray = new D[10];
        test("isArrayOfA", aArray);
        test("isArrayOfA", bArray);
        test("isArrayOfA", cArray);
        test("isArrayOfA", dArray);
        test("isArrayOfB", aArray);
        test("isArrayOfB", bArray);
        test("isArrayOfB", cArray);
        test("isArrayOfB", dArray);
        test("isArrayOfC", aArray);
        test("isArrayOfC", bArray);
        test("isArrayOfC", cArray);
        test("isArrayOfC", dArray);
        test("isArrayOfD", aArray);
        test("isArrayOfD", bArray);
        test("isArrayOfD", cArray);
        test("isArrayOfD", dArray);
    }

    @SuppressWarnings("unchecked")
    public static <T> String arrayCopyTypeName(T[] original) {
        Class<? extends T[]> newType = (Class<? extends T[]>) original.getClass();
        if (newType == (Object) Object[].class) {
            return Object[].class.getName();
        } else {
            return newType.getName();
        }
    }

    @Test
    public void testArrayCopy() {
        test("arrayCopyTypeName", (Object) new Object[]{"one", "two", "three"});
        test("arrayCopyTypeName", (Object) new String[]{"one", "two", "three"});
    }

    public int conditionalInstantiation(Object o) {
        int total = 0;
        if (o instanceof CharSequence) {
            if (o instanceof StringBuilder || o instanceof String) {
                total = 9;
            }
            total += (o instanceof String ? 2 : 1);
        }

        return total;
    }

    @Test
    public void testInstantiation() {
        test("conditionalInstantiation", "foo");
        test("conditionalInstantiation", new StringBuilder());
        test("conditionalInstantiation", 1);
    }

    public boolean exactlyObject(Thread thread) {
        return thread != null && ((Object) thread).getClass() == Object.class;
    }

    public boolean exactlyObjectArray(Thread[] threads) {
        return threads != null && ((Object[]) threads).getClass() == Object[].class;
    }

    public boolean exactlyString(Thread thread) {
        return thread != null && ((Object) thread).getClass() == String.class;
    }

    public boolean exactlyStringArray(Thread[] threads) {
        return threads != null && ((Object[]) threads).getClass() == String[].class;
    }

    @SuppressWarnings("cast")
    public boolean instanceofStringArray(Thread[] threads) {
        return threads != null && ((Object[]) threads) instanceof String[];
    }

    @SuppressWarnings("cast")
    public boolean instanceofString(Thread thread) {
        return thread != null && ((Object) thread) instanceof String;
    }

    @Test
    public void testTypeCheck() {
        testConstantReturn("exactlyObject", 0);
        testConstantReturn("exactlyObjectArray", 0);
        testConstantReturn("exactlyString", 0);
        testConstantReturn("exactlyStringArray", 0);
        testConstantReturn("instanceofString", 0);
        testConstantReturn("instanceofStringArray", 0);
    }

    private void testConstantReturn(String name, Object value) {
        StructuredGraph result = buildGraph(name);
        ReturnNode ret = result.getNodes(ReturnNode.TYPE).first();
        assertDeepEquals(1, result.getNodes(ReturnNode.TYPE).count());

        assertDeepEquals(true, ret.result().isConstant());
        assertDeepEquals(value, ret.result().asJavaConstant().asBoxedPrimitive());
    }

    @SuppressWarnings("try")
    protected StructuredGraph buildGraph(final String snippet) {
        try (Scope s = Debug.scope("InstanceOfTest", getMetaAccess().lookupJavaMethod(getMethod(snippet)))) {
            StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
            compile(graph.method(), graph);
            Debug.dump(Debug.BASIC_LOG_LEVEL, graph, snippet);
            return graph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    static class Depth1 implements Cloneable {
    }

    static class Depth2 extends Depth1 {
    }

    static class Depth3 extends Depth2 {
    }

    static class Depth4 extends Depth3 {
    }

    static class Depth5 extends Depth4 {
    }

    static class Depth6 extends Depth5 {
    }

    static class Depth7 extends Depth6 {
    }

    static class Depth8 extends Depth7 {
    }

    static class Depth9 extends Depth8 {
    }

    static class Depth10 extends Depth9 {
    }

    static class Depth11 extends Depth10 {
    }

    static class Depth12 extends Depth11 {
    }

    static class Depth13 extends Depth12 {
    }

    static class Depth14 extends Depth12 {
    }
}
