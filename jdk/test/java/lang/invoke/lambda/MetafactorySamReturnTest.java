/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8035776
 * @summary metafactory should fail if impl return does not match sam/bridge returns
 */
import java.lang.invoke.*;
import java.util.Arrays;
import static java.lang.invoke.MethodType.methodType;

public class MetafactorySamReturnTest {

    static final MethodHandles.Lookup lookup = MethodHandles.lookup();

    public interface I {}

    public static class C {
        public static void m_void(String arg) {}
        public static boolean m_boolean(String arg) { return true; }
        public static char m_char(String arg) { return 'x'; }
        public static byte m_byte(String arg) { return 12; }
        public static short m_short(String arg) { return 12; }
        public static int m_int(String arg) { return 12; }
        public static long m_long(String arg) { return 12; }
        public static float m_float(String arg) { return 12; }
        public static double m_double(String arg) { return 12; }
        public static String m_String(String arg) { return ""; }
        public static Integer m_Integer(String arg) { return 23; }
        public static Object m_Object(String arg) { return new Object(); }

        public static MethodHandle getMH(Class<?> c) {
            try {
                return lookup.findStatic(C.class, "m_" + c.getSimpleName(), methodType(c, String.class));
            }
            catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String... args) {
        Class<?>[] t = { void.class, boolean.class, char.class,
                         byte.class, short.class, int.class, long.class, float.class, double.class,
                         String.class, Integer.class, Object.class };

        for (int i = 0; i < t.length; i++) {
            MethodHandle mh = C.getMH(t[i]);
            for (int j = 0; j < t.length; j++) {
                // TEMPORARY EXCEPTIONS
                if (t[j] == void.class) continue;
                if (t[i].isPrimitive() && t[j] == Object.class) continue;
                if (t[i] == char.class && (t[j] == int.class || t[j] == long.class || t[j] == float.class || t[j] == double.class)) continue;
                if (t[i] == byte.class && (t[j] == short.class || t[j] == int.class || t[j] == long.class || t[j] == float.class || t[j] == double.class)) continue;
                if (t[i] == short.class && (t[j] == int.class || t[j] == long.class || t[j] == float.class || t[j] == double.class)) continue;
                if (t[i] == int.class && (t[j] == long.class || t[j] == float.class || t[j] == double.class)) continue;
                if (t[i] == long.class && (t[j] == float.class || t[j] == double.class)) continue;
                if (t[i] == float.class && t[j] == double.class) continue;
                if (t[i] == int.class && t[j] == Integer.class) continue;
                if (t[i] == Integer.class && (t[j] == int.class || t[j] == long.class || t[j] == float.class || t[j] == double.class)) continue;
                // END TEMPORARY EXCEPTIONS
                boolean correct = (t[i].isPrimitive() || t[j].isPrimitive())
                                  ? t[i] == t[j]
                                  : t[j].isAssignableFrom(t[i]);
                MethodType mti = methodType(t[i], String.class);
                MethodType mtiCS = methodType(t[i], CharSequence.class);
                MethodType mtj = methodType(t[j], String.class);
                MethodType mtjObj = methodType(t[j], Object.class);
                test(correct, mh, mti, mtj);
                testBridge(correct, mh, mti, mti, mtjObj);
                testBridge(correct, mh, mti, mti, mtiCS, mtjObj);
            }
        }
    }

    static void test(boolean correct, MethodHandle mh, MethodType instMT, MethodType samMT) {
        tryMetafactory(correct, mh, new Class<?>[]{}, instMT, samMT);
        tryAltMetafactory(correct, mh, new Class<?>[]{}, instMT, samMT);
    }

    static void testBridge(boolean correct, MethodHandle mh, MethodType instMT, MethodType samMT, MethodType... bridgeMTs) {
        tryAltMetafactory(correct, mh, new Class<?>[]{}, instMT, samMT, bridgeMTs);
    }

    static void tryMetafactory(boolean correct, MethodHandle mh, Class<?>[] captured,
                               MethodType instMT, MethodType samMT) {
        try {
            LambdaMetafactory.metafactory(lookup, "run", methodType(I.class, captured),
                                          samMT, mh, instMT);
            if (!correct) {
                throw new AssertionError("Uncaught linkage error:" +
                                         " impl=" + mh +
                                         ", captured=" + Arrays.toString(captured) +
                                         ", inst=" + instMT +
                                         ", sam=" + samMT);
            }
        }
        catch (LambdaConversionException e) {
            if (correct) {
                throw new AssertionError("Unexpected linkage error:" +
                                         " e=" + e +
                                         ", impl=" + mh +
                                         ", captured=" + Arrays.toString(captured) +
                                         ", inst=" + instMT +
                                         ", sam=" + samMT);
            }
        }
    }

    static void tryAltMetafactory(boolean correct, MethodHandle mh, Class<?>[] captured,
                                  MethodType instMT, MethodType samMT, MethodType... bridgeMTs) {
        boolean bridge = bridgeMTs.length > 0;
        Object[] args = new Object[bridge ? 5+bridgeMTs.length : 4];
        args[0] = samMT;
        args[1] = mh;
        args[2] = instMT;
        args[3] = bridge ? LambdaMetafactory.FLAG_BRIDGES : 0;
        if (bridge) {
            args[4] = bridgeMTs.length;
            for (int i = 0; i < bridgeMTs.length; i++) args[5+i] = bridgeMTs[i];
        }
        try {
            LambdaMetafactory.altMetafactory(lookup, "run", methodType(I.class, captured), args);
            if (!correct) {
                throw new AssertionError("Uncaught linkage error:" +
                                         " impl=" + mh +
                                         ", captured=" + Arrays.toString(captured) +
                                         ", inst=" + instMT +
                                         ", sam=" + samMT +
                                         ", bridges=" + Arrays.toString(bridgeMTs));
            }
        }
        catch (LambdaConversionException e) {
            if (correct) {
                throw new AssertionError("Unexpected linkage error:" +
                                         " e=" + e +
                                         ", impl=" + mh +
                                         ", captured=" + Arrays.toString(captured) +
                                         ", inst=" + instMT +
                                         ", sam=" + samMT +
                                         ", bridges=" + Arrays.toString(bridgeMTs));
            }
        }
    }

}
