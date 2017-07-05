/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

package test.sun.invoke.util;

import sun.invoke.util.ValueConversions;
import sun.invoke.util.Wrapper;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandle;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

/* @test
 * @summary unit tests for value-type conversion utilities
 * @ignore This test requires a special compilation environment to access sun.inovke.util.  Run by hand.
 * @run junit/othervm test.sun.invoke.util.ValueConversionsTest
 * @run junit/othervm
 *          -DValueConversionsTest.MAX_ARITY=255 -DValueConversionsTest.START_ARITY=250
 *              test.sun.invoke.util.ValueConversionsTest
 */

// This might take a while and burn lots of metadata:
// @run junit/othervm -DValueConversionsTest.MAX_ARITY=255 -DValueConversionsTest.EXHAUSTIVE=true test.sun.invoke.util.ValueConversionsTest

/**
 *
 * @author jrose
 */
public class ValueConversionsTest {
    private static final Class<?> CLASS = ValueConversionsTest.class;
    private static final int MAX_ARITY = Integer.getInteger(CLASS.getSimpleName()+".MAX_ARITY", 40);
    private static final int START_ARITY = Integer.getInteger(CLASS.getSimpleName()+".START_ARITY", 0);
    private static final boolean EXHAUSTIVE = Boolean.getBoolean(CLASS.getSimpleName()+".EXHAUSTIVE");

    @Test
    public void testUnbox() throws Throwable {
        testUnbox(false);
    }

    @Test
    public void testUnboxCast() throws Throwable {
        testUnbox(true);
    }

    private void testUnbox(boolean doCast) throws Throwable {
        //System.out.println("unbox");
        for (Wrapper dst : Wrapper.values()) {
            //System.out.println(dst);
            for (Wrapper src : Wrapper.values()) {
                testUnbox(doCast, dst, src);
            }
        }
    }

    private void testUnbox(boolean doCast, Wrapper dst, Wrapper src) throws Throwable {
        boolean expectThrow = !doCast && !dst.isConvertibleFrom(src);
        if (dst == Wrapper.OBJECT || src == Wrapper.OBJECT)  return;  // must have prims
        if (dst == Wrapper.OBJECT)
            expectThrow = false;  // everything (even VOID==null here) converts to OBJECT
        try {
            for (int n = -5; n < 10; n++) {
                Object box = src.wrap(n);
                switch (src) {
                    case VOID:   assertEquals(box, null); break;
                    case OBJECT: box = box.toString(); break;
                    case SHORT:  assertEquals(box.getClass(), Short.class); break;
                    default:     assertEquals(box.getClass(), src.wrapperType()); break;
                }
                MethodHandle unboxer;
                if (doCast)
                    unboxer = ValueConversions.unboxCast(dst.primitiveType());
                else
                    unboxer = ValueConversions.unbox(dst.primitiveType());
                Object expResult = (box == null) ? dst.zero() : dst.wrap(box);
                Object result = null;
                switch (dst) {
                    case INT:     result = (int)     unboxer.invokeExact(box); break;
                    case LONG:    result = (long)    unboxer.invokeExact(box); break;
                    case FLOAT:   result = (float)   unboxer.invokeExact(box); break;
                    case DOUBLE:  result = (double)  unboxer.invokeExact(box); break;
                    case CHAR:    result = (char)    unboxer.invokeExact(box); break;
                    case BYTE:    result = (byte)    unboxer.invokeExact(box); break;
                    case SHORT:   result = (short)   unboxer.invokeExact(box); break;
                    case OBJECT:  result = (Object)  unboxer.invokeExact(box); break;
                    case BOOLEAN: result = (boolean) unboxer.invokeExact(box); break;
                    case VOID:    result = null;     unboxer.invokeExact(box); break;
                }
                if (expectThrow) {
                    expResult = "(need an exception)";
                }
                assertEquals("(doCast,expectThrow,dst,src,n,box)="+Arrays.asList(doCast,expectThrow,dst,src,n,box),
                             expResult, result);
            }
        } catch (RuntimeException ex) {
            if (expectThrow)  return;
            System.out.println("Unexpected throw for (doCast,expectThrow,dst,src)="+Arrays.asList(doCast,expectThrow,dst,src));
            throw ex;
        }
    }

    @Test
    public void testUnboxRaw() throws Throwable {
        //System.out.println("unboxRaw");
        for (Wrapper w : Wrapper.values()) {
            if (w == Wrapper.OBJECT)  continue;  // skip this; no raw form
            //System.out.println(w);
            for (int n = -5; n < 10; n++) {
                Object box = w.wrap(n);
                long expResult = w.unwrapRaw(box);
                Object box2 = w.wrapRaw(expResult);
                assertEquals(box, box2);
                MethodHandle unboxer = ValueConversions.unboxRaw(w.primitiveType());
                long result = -1;
                switch (w) {
                    case INT:     result = (int)  unboxer.invokeExact(box); break;
                    case LONG:    result = (long) unboxer.invokeExact(box); break;
                    case FLOAT:   result = (int)  unboxer.invokeExact(box); break;
                    case DOUBLE:  result = (long) unboxer.invokeExact(box); break;
                    case CHAR:    result = (int)  unboxer.invokeExact(box); break;
                    case BYTE:    result = (int)  unboxer.invokeExact(box); break;
                    case SHORT:   result = (int)  unboxer.invokeExact(box); break;
                    case BOOLEAN: result = (int)  unboxer.invokeExact(box); break;
                    case VOID:    result = (int)  unboxer.invokeExact(box); break;
                }
                assertEquals("(w,n,box)="+Arrays.asList(w,n,box),
                             expResult, result);
            }
        }
    }

    @Test
    public void testBox() throws Throwable {
        //System.out.println("box");
        for (Wrapper w : Wrapper.values()) {
            if (w == Wrapper.VOID)  continue;  // skip this; no unboxed form
            //System.out.println(w);
            for (int n = -5; n < 10; n++) {
                Object box = w.wrap(n);
                MethodHandle boxer = ValueConversions.box(w.primitiveType());
                Object expResult = box;
                Object result = null;
                switch (w) {
                    case INT:     result = boxer.invokeExact(/*int*/n); break;
                    case LONG:    result = boxer.invokeExact((long)n); break;
                    case FLOAT:   result = boxer.invokeExact((float)n); break;
                    case DOUBLE:  result = boxer.invokeExact((double)n); break;
                    case CHAR:    result = boxer.invokeExact((char)n); break;
                    case BYTE:    result = boxer.invokeExact((byte)n); break;
                    case SHORT:   result = boxer.invokeExact((short)n); break;
                    case OBJECT:  result = boxer.invokeExact((Object)n); break;
                    case BOOLEAN: result = boxer.invokeExact((n & 1) != 0); break;
                }
                assertEquals("(dst,src,n,box)="+Arrays.asList(w,w,n,box),
                             expResult, result);
            }
        }
    }

    @Test
    public void testBoxRaw() throws Throwable {
        //System.out.println("boxRaw");
        for (Wrapper w : Wrapper.values()) {
            if (w == Wrapper.VOID)  continue;  // skip this; no unboxed form
            if (w == Wrapper.OBJECT)  continue;  // skip this; no raw form
            //System.out.println(w);
            for (int n = -5; n < 10; n++) {
                Object box = w.wrap(n);
                long   raw = w.unwrapRaw(box);
                Object expResult = box;
                MethodHandle boxer = ValueConversions.boxRaw(w.primitiveType());
                Object result = null;
                switch (w) {
                case INT:     result = boxer.invokeExact((int)raw); break;
                case LONG:    result = boxer.invokeExact(raw); break;
                case FLOAT:   result = boxer.invokeExact((int)raw); break;
                case DOUBLE:  result = boxer.invokeExact(raw); break;
                case CHAR:    result = boxer.invokeExact((int)raw); break;
                case BYTE:    result = boxer.invokeExact((int)raw); break;
                case SHORT:   result = boxer.invokeExact((int)raw); break;
                case BOOLEAN: result = boxer.invokeExact((int)raw); break;
                }
                assertEquals("(dst,src,n,box)="+Arrays.asList(w,w,n,box),
                             expResult, result);
            }
        }
    }

    @Test
    public void testReboxRaw() throws Throwable {
        //System.out.println("reboxRaw");
        for (Wrapper w : Wrapper.values()) {
            Wrapper pw = Wrapper.forPrimitiveType(w.rawPrimitiveType());
            if (w == Wrapper.VOID)  continue;  // skip this; no unboxed form
            if (w == Wrapper.OBJECT)  continue;  // skip this; no raw form
            //System.out.println(w);
            for (int n = -5; n < 10; n++) {
                Object box = w.wrap(n);
                Object raw = pw.wrap(w.unwrapRaw(box));
                Object expResult = box;
                MethodHandle boxer = ValueConversions.rebox(w.primitiveType());
                Object result = null;
                switch (w) {
                case INT:     result = boxer.invokeExact(raw); break;
                case LONG:    result = boxer.invokeExact(raw); break;
                case FLOAT:   result = boxer.invokeExact(raw); break;
                case DOUBLE:  result = boxer.invokeExact(raw); break;
                case CHAR:    result = boxer.invokeExact(raw); break;
                case BYTE:    result = boxer.invokeExact(raw); break;
                case SHORT:   result = boxer.invokeExact(raw); break;
                case BOOLEAN: result = boxer.invokeExact(raw); break;
                }
                assertEquals("(dst,src,n,box)="+Arrays.asList(w,w,n,box),
                             expResult, result);
            }
        }
    }

    @Test
    public void testCast() throws Throwable {
        //System.out.println("cast");
        Class<?>[] types = { Object.class, Serializable.class, String.class, Number.class, Integer.class };
        Object[] objects = { new Object(), Boolean.FALSE,      "hello",      (Long)12L,    (Integer)6    };
        for (Class<?> dst : types) {
            MethodHandle caster = ValueConversions.cast(dst);
            assertEquals(caster.type(), ValueConversions.identity().type());
            for (Object obj : objects) {
                Class<?> src = obj.getClass();
                boolean canCast;
                if (dst.isInterface()) {
                    canCast = true;
                } else {
                    canCast = dst.isAssignableFrom(src);
                    assertEquals(canCast, dst.isInstance(obj));
                }
                //System.out.println("obj="+obj+" <: dst="+dst);
                try {
                    Object result = caster.invokeExact(obj);
                    if (canCast)
                        assertEquals(obj, result);
                    else
                        assertEquals("cast should not have succeeded", dst, obj);
                } catch (ClassCastException ex) {
                    if (canCast)
                        throw ex;
                }
            }
        }
    }

    @Test
    public void testIdentity() throws Throwable {
        //System.out.println("identity");
        MethodHandle id = ValueConversions.identity();
        Object expResult = "foo";
        Object result = id.invokeExact(expResult);
        // compiler bug:  ValueConversions.identity().invokeExact("bar");
        assertEquals(expResult, result);
    }

    @Test
    public void testVarargsArray() throws Throwable {
        //System.out.println("varargsArray");
        final int MIN = START_ARITY;
        final int MAX = MAX_ARITY-2;  // 253+1 would cause parameter overflow with 'this' added
        for (int nargs = MIN; nargs <= MAX; nargs = nextArgCount(nargs, 17, MAX)) {
            MethodHandle target = ValueConversions.varargsArray(nargs);
            Object[] args = new Object[nargs];
            for (int i = 0; i < nargs; i++)
                args[i] = "#"+i;
            Object res = target.invokeWithArguments(args);
            assertArrayEquals(args, (Object[])res);
        }
    }

    @Test
    public void testVarargsReferenceArray() throws Throwable {
        //System.out.println("varargsReferenceArray");
        testTypedVarargsArray(Object[].class);
        testTypedVarargsArray(String[].class);
        testTypedVarargsArray(Number[].class);
    }

    @Test
    public void testVarargsPrimitiveArray() throws Throwable {
        //System.out.println("varargsPrimitiveArray");
        testTypedVarargsArray(int[].class);
        testTypedVarargsArray(long[].class);
        testTypedVarargsArray(byte[].class);
        testTypedVarargsArray(boolean[].class);
        testTypedVarargsArray(short[].class);
        testTypedVarargsArray(char[].class);
        testTypedVarargsArray(float[].class);
        testTypedVarargsArray(double[].class);
    }

    private static int nextArgCount(int nargs, int density, int MAX) {
        if (EXHAUSTIVE)  return nargs + 1;
        if (nargs >= MAX)  return Integer.MAX_VALUE;
        int BOT = 20, TOP = MAX-5;
        if (density < 10) { BOT = 10; MAX = TOP-2; }
        if (nargs <= BOT || nargs >= TOP) {
            ++nargs;
        } else {
            int bump = Math.max(1, 100 / density);
            nargs += bump;
            if (nargs > TOP)  nargs = TOP;
        }
        return nargs;
    }

    private void testTypedVarargsArray(Class<?> arrayType) throws Throwable {
        System.out.println(arrayType.getSimpleName());
        Class<?> elemType = arrayType.getComponentType();
        int MIN = START_ARITY;
        int MAX = MAX_ARITY-2;  // 253+1 would cause parameter overflow with 'this' added
        int density = 3;
        if (elemType == int.class || elemType == long.class)  density = 7;
        if (elemType == long.class || elemType == double.class) { MAX /= 2; MIN /= 2; }
        for (int nargs = MIN; nargs <= MAX; nargs = nextArgCount(nargs, density, MAX)) {
            Object[] args = makeTestArray(elemType, nargs);
            MethodHandle varargsArray = ValueConversions.varargsArray(arrayType, nargs);
            MethodType vaType = varargsArray.type();
            assertEquals(arrayType, vaType.returnType());
            if (nargs != 0) {
                assertEquals(elemType, vaType.parameterType(0));
                assertEquals(elemType, vaType.parameterType(vaType.parameterCount()-1));
            }
            assertEquals(MethodType.methodType(arrayType, Collections.<Class<?>>nCopies(nargs, elemType)),
                         vaType);
            Object res = varargsArray.invokeWithArguments(args);
            String resString = toArrayString(res);
            assertEquals(Arrays.toString(args), resString);

            MethodHandle spreader = varargsArray.asSpreader(arrayType, nargs);
            MethodType stype = spreader.type();
            assert(stype == MethodType.methodType(arrayType, arrayType));
            if (nargs <= 5) {
                // invoke target as a spreader also:
                @SuppressWarnings("cast")
                Object res2 = spreader.invokeWithArguments((Object)res);
                String res2String = toArrayString(res2);
                assertEquals(Arrays.toString(args), res2String);
                // invoke the spreader on a generic Object[] array; check for error
                try {
                    Object res3 = spreader.invokeWithArguments((Object)args);
                    String res3String = toArrayString(res3);
                    assertTrue(arrayType.getName(), arrayType.isAssignableFrom(Object[].class));
                    assertEquals(Arrays.toString(args), res3String);
                } catch (ClassCastException ex) {
                    assertFalse(arrayType.getName(), arrayType.isAssignableFrom(Object[].class));
                }
            }
            if (nargs == 0) {
                // invoke spreader on null arglist
                Object res3 = spreader.invokeWithArguments((Object)null);
                String res3String = toArrayString(res3);
                assertEquals(Arrays.toString(args), res3String);
            }
        }
    }

    private static Object[] makeTestArray(Class<?> elemType, int len) {
        Wrapper elem = null;
        if (elemType.isPrimitive())
            elem = Wrapper.forPrimitiveType(elemType);
        else if (Wrapper.isWrapperType(elemType))
            elem = Wrapper.forWrapperType(elemType);
        Object[] args = new Object[len];
        for (int i = 0; i < len; i++) {
            Object arg = i * 100;
            if (elem == null) {
                if (elemType == String.class)
                    arg = "#"+arg;
                arg = elemType.cast(arg);  // just to make sure
            } else {
                switch (elem) {
                    case BOOLEAN: arg = (i % 3 == 0);           break;
                    case CHAR:    arg = 'a' + i;                break;
                    case LONG:    arg = (long)i * 1000_000_000; break;
                    case FLOAT:   arg = (float)i / 100;         break;
                    case DOUBLE:  arg = (double)i / 1000_000;   break;
                }
                arg = elem.cast(arg, elemType);
            }
            args[i] = arg;
        }
        //System.out.println(elemType.getName()+Arrays.toString(args));
        return args;
    }

    private static String toArrayString(Object a) {
        if (a == null)  return "null";
        Class<?> elemType = a.getClass().getComponentType();
        if (elemType == null)  return a.toString();
        if (elemType.isPrimitive()) {
            switch (Wrapper.forPrimitiveType(elemType)) {
                case INT:      return Arrays.toString((int[])a);
                case BYTE:     return Arrays.toString((byte[])a);
                case BOOLEAN:  return Arrays.toString((boolean[])a);
                case SHORT:    return Arrays.toString((short[])a);
                case CHAR:     return Arrays.toString((char[])a);
                case FLOAT:    return Arrays.toString((float[])a);
                case LONG:     return Arrays.toString((long[])a);
                case DOUBLE:   return Arrays.toString((double[])a);
            }
        }
        return Arrays.toString((Object[])a);
    }

    @Test
    public void testVarargsList() throws Throwable {
        //System.out.println("varargsList");
        final int MIN = START_ARITY;
        final int MAX = MAX_ARITY-2;  // 253+1 would cause parameter overflow with 'this' added
        for (int nargs = MIN; nargs <= MAX; nargs = nextArgCount(nargs, 7, MAX)) {
            MethodHandle target = ValueConversions.varargsList(nargs);
            Object[] args = new Object[nargs];
            for (int i = 0; i < nargs; i++)
                args[i] = "#"+i;
            Object res = target.invokeWithArguments(args);
            assertEquals(Arrays.asList(args), res);
        }
    }
}
