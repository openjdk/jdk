/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.nashorn.internal.runtime.linker.test;

import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Supplier;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.runtime.Context;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JavaAdapterTest {
    public interface TestConversions {
        public byte getByte(byte b);
        public short getShort(short b);
        public char getChar(char c);
        public int getInt(int i);
        public float getFloat(float f);
        public long getLong(long l);
        public double getDouble(double d);
    }

    @Test
    public static void testBlah() throws ScriptException {
        final ScriptEngine e = createEngine();
        e.eval("new java.util.Comparator({})");
    }

    @Test
    public static void testConversions() throws ScriptException {
        final ScriptEngine e = createEngine();
        e.put("TestConversionsClass", TestConversions.class);
        final TestConversions tc = (TestConversions)e.eval(
                "function id(x) { return x };" +
                "new TestConversionsClass.static({" +
                "  getByte: id, getShort: id, getChar: id, getInt: id," +
                "  getFloat: id, getLong: id, getDouble: id });");

        Assert.assertEquals(Byte.MIN_VALUE, tc.getByte(Byte.MIN_VALUE));
        Assert.assertEquals(Byte.MAX_VALUE, tc.getByte(Byte.MAX_VALUE));

        Assert.assertEquals(Short.MIN_VALUE, tc.getShort(Short.MIN_VALUE));
        Assert.assertEquals(Short.MAX_VALUE, tc.getShort(Short.MAX_VALUE));

        Assert.assertEquals(Character.MIN_VALUE, tc.getChar(Character.MIN_VALUE));
        Assert.assertEquals(Character.MAX_VALUE, tc.getChar(Character.MAX_VALUE));

        Assert.assertEquals(Integer.MIN_VALUE, tc.getInt(Integer.MIN_VALUE));
        Assert.assertEquals(Integer.MAX_VALUE, tc.getInt(Integer.MAX_VALUE));

        Assert.assertEquals(Long.MIN_VALUE, tc.getLong(Long.MIN_VALUE));
        Assert.assertEquals(Long.MAX_VALUE, tc.getLong(Long.MAX_VALUE));

        Assert.assertEquals(Float.MIN_VALUE, tc.getFloat(Float.MIN_VALUE));
        Assert.assertEquals(Float.MAX_VALUE, tc.getFloat(Float.MAX_VALUE));
        Assert.assertEquals(Float.MIN_NORMAL, tc.getFloat(Float.MIN_NORMAL));
        Assert.assertEquals(Float.POSITIVE_INFINITY, tc.getFloat(Float.POSITIVE_INFINITY));
        Assert.assertEquals(Float.NEGATIVE_INFINITY, tc.getFloat(Float.NEGATIVE_INFINITY));
        Assert.assertTrue(Float.isNaN(tc.getFloat(Float.NaN)));

        Assert.assertEquals(Double.MIN_VALUE, tc.getDouble(Double.MIN_VALUE));
        Assert.assertEquals(Double.MAX_VALUE, tc.getDouble(Double.MAX_VALUE));
        Assert.assertEquals(Double.MIN_NORMAL, tc.getDouble(Double.MIN_NORMAL));
        Assert.assertEquals(Double.POSITIVE_INFINITY, tc.getDouble(Double.POSITIVE_INFINITY));
        Assert.assertEquals(Double.NEGATIVE_INFINITY, tc.getDouble(Double.NEGATIVE_INFINITY));
        Assert.assertTrue(Double.isNaN(tc.getDouble(Double.NaN)));
    }

    private static ScriptEngine createEngine() {
        // Use no optimistic typing so we run faster; short-running tests.
        return new NashornScriptEngineFactory().getScriptEngine("-ot=false");
    }

    @Test
    public static void testUnimplemented() throws ScriptException {
        final ScriptEngine e = createEngine();
        final Runnable r = (Runnable) e.eval("new java.lang.Runnable({})");
        Assert.assertNull(Context.getGlobal());
        try {
            r.run();
            Assert.fail();
        } catch(final UnsupportedOperationException x) {
            // This is expected
        }
        // Check global has been restored
        Assert.assertNull(Context.getGlobal());
    }

    public interface ThrowingRunnable {
        public void run() throws Throwable;
    }

    @Test
    public static void testUnimplementedWithThrowable() throws Throwable {
        final ScriptEngine e = createEngine();
        e.put("ThrowingRunnableClass", ThrowingRunnable.class);
        final ThrowingRunnable r = (ThrowingRunnable) e.eval("new ThrowingRunnableClass.static({})");
        Assert.assertNull(Context.getGlobal());
        try {
            r.run();
            Assert.fail();
        } catch(final UnsupportedOperationException x) {
            // This is expected
        }
        // Check global has been restored
        Assert.assertNull(Context.getGlobal());
    }

    public interface IntSupplierWithDefault {
        public default int get() { return 42; }
    }

    @Test
    public static void testUnimplementedWithDefault() throws ScriptException {
        final ScriptEngine e = createEngine();
        e.put("IntSupplierWithDefault", IntSupplierWithDefault.class);
        final IntSupplierWithDefault s1 = (IntSupplierWithDefault) e.eval("new IntSupplierWithDefault.static({})");
        Assert.assertEquals(42, s1.get());
        final IntSupplierWithDefault s2 = (IntSupplierWithDefault) e.eval("new IntSupplierWithDefault.static({ get: function() { return 43 }})");
        Assert.assertEquals(43, s2.get());
    }

    public interface SupplierSupplier {
        public Supplier<Object> getSupplier();
    }

    @Test
    public static void testReturnAdapter() throws ScriptException {
        final ScriptEngine e = createEngine();
        e.put("SupplierSupplier", SupplierSupplier.class);
        final SupplierSupplier s = (SupplierSupplier) e.eval("new SupplierSupplier.static(function(){ return function() { return 'foo' } })");
        Assert.assertEquals("foo", s.getSupplier().get());
    }

    public interface MaxParams {
        public Object method(boolean p1, byte p2, short p3, char p4, int p5, float p6, long p7, double p8,
                Object p9, Object p10, Object p11, Object p12, Object p13, Object p14, Object p15, Object p16,
                Object p17, Object p18, Object p19, Object p20, Object p21, Object p22, Object p23, Object p24,
                Object p25, Object p26, Object p27, Object p28, Object p29, Object p30, Object p31, Object p32,
                Object p33, Object p34, Object p35, Object p36, Object p37, Object p38, Object p39, Object p40,
                Object p41, Object p42, Object p43, Object p44, Object p45, Object p46, Object p47, Object p48,
                Object p49, Object p50, Object p51, Object p52, Object p53, Object p54, Object p55, Object p56,
                Object p57, Object p58, Object p59, Object p60, Object p61, Object p62, Object p63, Object p64,
                Object p65, Object p66, Object p67, Object p68, Object p69, Object p70, Object p71, Object p72,
                Object p73, Object p74, Object p75, Object p76, Object p77, Object p78, Object p79, Object p80,
                Object p81, Object p82, Object p83, Object p84, Object p85, Object p86, Object p87, Object p88,
                Object p89, Object p90, Object p91, Object p92, Object p93, Object p94, Object p95, Object p96,
                Object p97, Object p98, Object p99, Object p100, Object p101, Object p102, Object p103, Object p104,
                Object p105, Object p106, Object p107, Object p108, Object p109, Object p110, Object p111, Object p112,
                Object p113, Object p114, Object p115, Object p116, Object p117, Object p118, Object p119, Object p120,
                Object p121, Object p122, Object p123, Object p124, Object p125, Object p126, Object p127, Object p128,
                Object p129, Object p130, Object p131, Object p132, Object p133, Object p134, Object p135, Object p136,
                Object p137, Object p138, Object p139, Object p140, Object p141, Object p142, Object p143, Object p144,
                Object p145, Object p146, Object p147, Object p148, Object p149, Object p150, Object p151, Object p152,
                Object p153, Object p154, Object p155, Object p156, Object p157, Object p158, Object p159, Object p160,
                Object p161, Object p162, Object p163, Object p164, Object p165, Object p166, Object p167, Object p168,
                Object p169, Object p170, Object p171, Object p172, Object p173, Object p174, Object p175, Object p176,
                Object p177, Object p178, Object p179, Object p180, Object p181, Object p182, Object p183, Object p184,
                Object p185, Object p186, Object p187, Object p188, Object p189, Object p190, Object p191, Object p192,
                Object p193, Object p194, Object p195, Object p196, Object p197, Object p198, Object p199, Object p200,
                Object p201, Object p202, Object p203, Object p204, Object p205, Object p206, Object p207, Object p208,
                Object p209, Object p210, Object p211, Object p212, Object p213, Object p214, Object p215, Object p216,
                Object p217, Object p218, Object p219, Object p220, Object p221, Object p222, Object p223, Object p224,
                Object p225, Object p226, Object p227, Object p228, Object p229, Object p230, Object p231, Object p232,
                Object p233, Object p234, Object p235, Object p236, Object p237, Object p238, Object p239, Object p240,
                Object p241, Object p242, Object p243, Object p244, Object p245, Object p246, Object p247, Object p248,
                Object p249, Object p250, Object p251, Object p252);
    }

    @Test
    public static void testMaxLengthAdapter() throws ScriptException {
        final ScriptEngine e = createEngine();
        e.put("MaxParams", MaxParams.class);
        final MaxParams s = (MaxParams) e.eval("new MaxParams.static(function(){ return arguments })");
        final ScriptObjectMirror m = (ScriptObjectMirror)s.method(true, Byte.MIN_VALUE, Short.MIN_VALUE, 'a', Integer.MAX_VALUE, Float.MAX_VALUE, Long.MAX_VALUE, Double.MAX_VALUE,
                "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26",
                "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39", "40", "41", "42", "43", "44",
                "45", "46", "47", "48", "49", "50", "51", "52", "53", "54", "55", "56", "57", "58", "59", "60", "61", "62",
                "63", "64", "65", "66", "67", "68", "69", "70", "71", "72", "73", "74", "75", "76", "77", "78", "79", "80",
                "81", "82", "83", "84", "85", "86", "87", "88", "89", "90", "91", "92", "93", "94", "95", "96", "97", "98",
                "99", "100", "101", "102", "103", "104", "105", "106", "107", "108", "109", "110", "111", "112", "113", "114",
                "115", "116", "117", "118", "119", "120", "121", "122", "123", "124", "125", "126", "127", "128", "129", "130",
                "131", "132", "133", "134", "135", "136", "137", "138", "139", "140", "141", "142", "143", "144", "145", "146",
                "147", "148", "149", "150", "151", "152", "153", "154", "155", "156", "157", "158", "159", "160", "161", "162",
                "163", "164", "165", "166", "167", "168", "169", "170", "171", "172", "173", "174", "175", "176", "177", "178",
                "179", "180", "181", "182", "183", "184", "185", "186", "187", "188", "189", "190", "191", "192", "193", "194",
                "195", "196", "197", "198", "199", "200", "201", "202", "203", "204", "205", "206", "207", "208", "209", "210",
                "211", "212", "213", "214", "215", "216", "217", "218", "219", "220", "221", "222", "223", "224", "225", "226",
                "227", "228", "229", "230", "231", "232", "233", "234", "235", "236", "237", "238", "239", "240", "241", "242",
                "243", "244", "245", "246", "247", "248", "249", "250", "251");
        Assert.assertEquals(true, m.getSlot(0));
        Assert.assertEquals(Integer.valueOf(Byte.MIN_VALUE), m.getSlot(1)); // Byte becomes Integer
        Assert.assertEquals(Integer.valueOf(Short.MIN_VALUE), m.getSlot(2)); // Short becomes Integer
        Assert.assertEquals(Character.valueOf('a'), m.getSlot(3));
        Assert.assertEquals(Integer.valueOf(Integer.MAX_VALUE), m.getSlot(4));
        Assert.assertEquals(Double.valueOf(Float.MAX_VALUE), m.getSlot(5)); // Float becomes Double
        Assert.assertEquals(Long.valueOf(Long.MAX_VALUE), m.getSlot(6)); // Long was untouched
        Assert.assertEquals(Double.valueOf(Double.MAX_VALUE), m.getSlot(7));
        for (int i = 8; i < 252; ++i) {
            Assert.assertEquals(String.valueOf(i), m.getSlot(i));
        }
    }

    public interface TestScriptObjectMirror {
        public JSObject getJSObject();
        public ScriptObjectMirror getScriptObjectMirror();
        public Map<Object, Object> getMap();
        public Bindings getBindings();
    }

    @Test
    public static void testReturnsScriptObjectMirror() throws ScriptException {
        final ScriptEngine e = createEngine();
        e.put("TestScriptObjectMirrorClass", TestScriptObjectMirror.class);
        final TestScriptObjectMirror tsom = (TestScriptObjectMirror)e.eval(
                "new TestScriptObjectMirrorClass.static({\n" +
                "  getJSObject: function() { return { 'kind': 'JSObject' } },\n" +
                "  getScriptObjectMirror: function() { return { 'kind': 'ScriptObjectMirror' } },\n" +
                "  getMap: function() { return { 'kind': 'Map' } },\n" +
                "  getBindings: function() { return { 'kind': 'Bindings' } } })\n");
        Assert.assertEquals(tsom.getJSObject().getMember("kind"), "JSObject");
        Assert.assertEquals(tsom.getScriptObjectMirror().getMember("kind"), "ScriptObjectMirror");
        Assert.assertEquals(tsom.getMap().get("kind"), "Map");
        Assert.assertEquals(tsom.getBindings().get("kind"), "Bindings");
    }

    public interface TestListAdapter {
        public List<Object> getList();
        public Collection<Object> getCollection();
        public Queue<Object> getQueue();
        public Deque<Object> getDequeue();
    }

    @Test
    public static void testReturnsListAdapter() throws ScriptException {
        final ScriptEngine e = createEngine();
        e.put("TestListAdapterClass", TestListAdapter.class);
        final TestListAdapter tla = (TestListAdapter)e.eval(
                "new TestListAdapterClass.static({\n" +
                "  getList: function() { return [ 'List' ] },\n" +
                "  getCollection: function() { return [ 'Collection' ] },\n" +
                "  getQueue: function() { return [ 'Queue' ] },\n" +
                "  getDequeue: function() { return [ 'Dequeue' ] } })\n");
        Assert.assertEquals(tla.getList().get(0), "List");
        Assert.assertEquals(tla.getCollection().iterator().next(), "Collection");
        Assert.assertEquals(tla.getQueue().peek(), "Queue");
        Assert.assertEquals(tla.getDequeue().peek(), "Dequeue");
    }
}
