/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.beans.Introspector;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.RandomAccess;

/**
 * @test
 * @bug 8211147 8280132
 * @modules java.desktop/com.sun.beans.introspect:open
 */
public final class MethodOrderException {

    public static void main(final String[] args) throws Exception {
        for (Class<?> beanClass : List.of(D.class, X.class, A_258.class)) {
            // Public API, fails rarely
            testPublicAPI(beanClass);
            // Test using internal API, fails always
            testPrivateAPI(beanClass);
        }
    }

    private static void testPublicAPI(Class<?> beanClass) throws Exception {
        Introspector.getBeanInfo(beanClass);
    }

    private static void testPrivateAPI(Class<?> beanClass) throws Exception {
        Class<?> name = Class.forName(
                "com.sun.beans.introspect.MethodInfo$MethodOrder");
        Field instance = name.getDeclaredField("instance");
        instance.setAccessible(true);
        Comparator<Method> o = (Comparator) instance.get(name);
        List<Method> methods = List.of(beanClass.getDeclaredMethods());
        methods.forEach(m1 -> {
            methods.forEach(m2 -> {
                if (o.compare(m1, m2) != -o.compare(m2, m1)) {
                    System.err.println("Method1 = " + m1);
                    System.err.println("Method2 = " + m2);
                    throw new RuntimeException("Broken contract!");
                }
                methods.forEach(m3 -> {
                    if (o.compare(m1, m2) < 0 && o.compare(m2, m3) < 0) {
                        if (o.compare(m1, m3) >= 0) {
                            System.err.println("Method1 = " + m1);
                            System.err.println("Method2 = " + m2);
                            System.err.println("Method3 = " + m3);
                            throw new RuntimeException("Broken contract!");
                        }
                    }
                });
            });
        });
    }

    interface C1 {
        C1 foo0();
    }
    interface C2 {
        C2 foo0();
    }
    interface C3 extends C1 {
        C3 foo0();
    }
    interface D extends C3, C2, C1 {
        D foo0();
    }
    public interface A_239 {
    }
    public interface A_240 {
    }
    public interface A_000 {
    }
    public interface A_238<T> {
    }
    public interface A_035 extends A_195, A_106, A_240 {
        A_035 a_040();
        A_035 a_000();
        A_035 a_018();
    }
    public static class A_258 implements A_053, A_196, A_200, A_070, A_106,
            A_057, A_094, A_098, A_105, A_107, A_097, A_093, A_214, A_215,
            A_210, A_129, A_067, A_180, A_108, A_184, A_110, A_111, A_082,
            A_221, A_172, A_171, A_168, A_139, A_143, A_140, A_075, A_081,
            A_080, A_163, A_165, A_164, A_159, A_161, A_155, A_158, A_157,
            A_156, A_195, A_197, A_114, A_213, A_236, A_220, A_201, A_035,
            A_136, A_135, A_226, A_227, A_005, A_054, A_203, A_202, A_071,
            A_115, A_113, A_112, A_058, A_095, A_096, A_099, A_100, A_237,
            A_091, A_092, A_217, A_218, A_216, A_211, A_130, A_063, A_062,
            A_064, A_065, A_066, A_061, A_060, A_181, A_208, A_207, A_209,
            A_185, A_186, A_083, A_173, A_176, A_222, A_223, A_174, A_169,
            A_153, A_154, A_194, A_190, A_104, A_132, A_141, A_142, A_166,
            A_167, A_160, A_162, A_076, A_077, A_078, A_079, A_074, A_085,
            A_192, A_188, A_134, A_138, A_137, A_228 {
        @Override
        public A_258 a_052() {
            return null;
        }
        @Override
        public A_258 a_071() {
            return null;
        }
        @Override
        public A_258 a_029() {
            return null;
        }
        @Override
        public A_258 a_046() {
            return null;
        }
        @Override
        public A_258 a_045() {
            return null;
        }
        @Override
        public A_258 a_047() {
            return null;
        }
        @Override
        public A_258 a_048() {
            return null;
        }
        @Override
        public A_258 a_049() {
            return null;
        }
        @Override
        public A_258 a_044() {
            return null;
        }
        @Override
        public A_258 a_043() {
            return null;
        }
        @Override
        public A_258 a_026() {
            return null;
        }
        @Override
        public A_258 a_027() {
            return null;
        }
        @Override
        public A_258 a_074() {
            return null;
        }
        @Override
        public A_258 a_079() {
            return null;
        }
        @Override
        public A_258 a_012() {
            return null;
        }
        @Override
        public A_258 a_100() {
            return null;
        }
        @Override
        public A_258 a_085() {
            return null;
        }
        @Override
        public A_258 a_084() {
            return null;
        }
        @Override
        public A_258 a_011() {
            return null;
        }
        @Override
        public A_258 a_059() {
            return null;
        }
        @Override
        public A_258 a_058() {
            return null;
        }
        @Override
        public A_258 a_080() {
            return null;
        }
        @Override
        public A_258 a_030() {
            return null;
        }
        @Override
        public A_258 a_031() {
            return null;
        }
        @Override
        public A_258 a_081() {
            return null;
        }
        @Override
        public A_258 a_077() {
            return null;
        }
        @Override
        public A_258 a_036() {
            return null;
        }
        @Override
        public A_258 a_056() {
            return null;
        }
        @Override
        public A_258 a_078() {
            return null;
        }
        @Override
        public A_258 a_076() {
            return null;
        }
        @Override
        public A_258 a_057() {
            return null;
        }
        @Override
        public A_258 a_005() {
            return null;
        }
        @Override
        public A_258 a_089() {
            return null;
        }
        @Override
        public A_258 a_088() {
            return null;
        }
        @Override
        public A_258 a_090() {
            return null;
        }
        @Override
        public A_258 a_072() {
            return null;
        }
        @Override
        public A_258 a_002() {
            return null;
        }
        @Override
        public A_258 a_040() {
            return null;
        }
        @Override
        public A_258 a_060() {
            return null;
        }
        @Override
        public A_258 a_061() {
            return null;
        }
        @Override
        public A_258 a_039() {
            return null;
        }
        @Override
        public A_258 a_032() {
            return null;
        }
        @Override
        public A_258 a_033() {
            return null;
        }
        @Override
        public A_258 a_000() {
            return null;
        }
        @Override
        public A_258 a_037() {
            return null;
        }
        @Override
        public A_258 a_014() {
            return null;
        }
        @Override
        public A_258 a_015() {
            return null;
        }
        @Override
        public A_258 a_016() {
            return null;
        }
        @Override
        public A_258 a_017() {
            return null;
        }
        @Override
        public A_258 a_091() {
            return null;
        }
        @Override
        public A_258 a_065() {
            return null;
        }
        @Override
        public A_258 a_066() {
            return null;
        }
        @Override
        public A_258 a_018() {
            return null;
        }
        @Override
        public A_258 a_093() {
            return null;
        }
        @Override
        public A_258 a_092() {
            return null;
        }
        @Override
        public A_258 a_095() {
            return null;
        }
        @Override
        public A_258 a_096() {
            return null;
        }
        @Override
        public A_258 a_069() {
            return null;
        }
    }
    public interface A_250 extends A_239 {
        A_250 a_094();
    }
    public interface A_253 extends A_239 {
        A_253 a_000();
    }
    public interface A_256 extends A_239 {
        A_256 a_009();
    }
    public interface A_248 extends A_239 {
        A_248 a_022();
    }
    public interface A_255 extends A_239 {
        A_255 a_007();
    }
    public interface A_241 extends A_248, A_250, A_251, A_249, A_239 {
    }
    public interface A_254 extends A_239 {
        A_254 a_008();
    }
    public interface A_251 extends A_239 {
        A_251 a_097();
    }
    public interface A_252 extends A_241, A_255, A_253, A_257, A_254, A_256,
            A_239 {
        A_252 a_022();
        A_252 a_094();
        A_252 a_097();
        A_252 a_087();
    }
    public interface A_229 extends A_239 {
        A_229 a_000();
    }
    public interface A_232 extends A_239 {
    }
    public interface A_249 extends A_239 {
        A_249 a_087();
    }
    public interface A_230 extends A_239 {
    }
    public interface A_234 extends A_239 {
        A_234 a_026();
    }
    public interface A_037 extends A_239 {
        A_037 a_013();
    }
    public interface A_233 extends A_239 {
        A_233 a_018();
    }
    public interface A_231 extends A_239 {
        A_231 a_007();
    }
    public interface A_049 extends A_239 {
        A_049 a_068();
    }
    public interface A_257 extends A_239 {
        A_257 a_018();
    }
    public interface A_235 extends A_239 {
    }
    public interface A_040 extends A_239 {
        A_040 a_025();
    }
    public interface A_133 extends A_000, A_005, A_134, A_240 {
        A_133 a_040();
        A_133 a_057();
    }
    public interface A_001 extends A_239 {
        A_001 a_020();
    }
    public interface A_031 extends A_239 {
    }
    public interface A_089 extends A_239 {
        A_089 a_098();
    }
    public interface A_166 extends A_239 {
        A_166 a_065();
    }
    public interface A_054 extends A_239 {
        A_054 a_000();
    }
    public interface A_190 extends A_239 {
        A_190 a_077();
    }
    public interface A_169 extends A_239 {
        A_169 a_037();
    }
    public interface A_217 extends A_239 {
        A_217 a_093();
    }
    public interface A_078 extends A_239 {
        A_078 a_016();
    }
    public interface A_192 extends A_239 {
    }
    public interface A_222 extends A_239 {
        A_222 a_095();
    }
    public interface A_112 extends A_239 {
        A_112 a_033();
    }
    public interface A_066 extends A_239 {
        A_066 a_049();
    }
    public interface A_074 extends A_239 {
        A_074 a_012();
    }
    public interface A_003 extends A_239 {
        A_003 a_039();
    }
    public interface A_083 extends A_239 {
    }
    public interface A_050 extends A_239 {
        A_050 a_070();
    }
    public interface A_087 extends A_239 {
    }
    public interface A_058 extends A_239 {
    }
    public interface A_128 extends A_239 {
    }
    public interface A_092 extends A_239 {
    }
    public interface A_004 extends A_240 {
        A_004 a_040();
    }
    public interface A_115 extends A_239 {
        A_115 a_039();
    }
    public interface A_176 extends A_239 {
        A_176 a_071();
    }
    public interface A_162 extends A_239 {
    }
    public interface A_132 extends A_239 {
        A_132 a_056();
    }
    public interface A_064 extends A_239 {
        A_064 a_047();
    }
    public interface A_021 extends A_239 {
    }
    public interface A_160 extends A_239 {
    }
    public interface A_141 extends A_239 {
        A_141 a_060();
    }
    public interface A_091 extends A_239 {
        A_091 a_026();
    }
    public interface A_034 extends A_239 {
        A_034 a_084();
    }
    public interface A_151 extends A_239 {
    }
    public interface A_026 extends A_239 {
        A_026 a_026();
    }
    public interface A_130 extends A_239 {
        A_130 a_052();
    }
    public interface A_242 extends A_239 {
        A_242 a_001();
    }
    public interface A_205 extends A_239 {
        A_205 a_086();
    }
    public interface A_048 extends A_239 {
        A_048 a_065();
    }
    public interface A_044 extends A_240 {
        A_044 a_040();
    }
    public interface A_023 extends A_239 {
    }
    public interface A_027 extends A_239 {
    }
    public interface A_138 extends A_239 {
        A_138 a_059();
    }
    public interface A_024 extends A_239 {
        A_024 a_011();
    }
    public interface A_038 extends A_239 {
        A_038 a_021();
    }
    public interface A_016 extends A_239 {
    }
    public interface A_118 extends A_239 {
        A_118 a_045();
    }
    public interface A_071 extends A_239 {
        A_071 a_011();
    }
    public interface A_203 extends A_239 {
        A_203 a_084();
    }
    public interface A_137 extends A_239 {
    }
    public interface A_119 extends A_239 {
        A_119 a_046();
    }
    public interface A_145 extends A_239 {
    }
    public interface A_045 extends A_239 {
        A_045 a_041();
    }
    public interface A_069 extends A_239 {
        A_069 a_010();
    }
    public interface A_150 extends A_239 {
    }
    public interface A_047 extends A_239 {
        A_047 a_057();
    }
    public interface A_179 extends A_239 {
    }
    public interface A_207 extends A_239 {
        A_207 a_088();
    }
    public interface A_228 extends A_239 {
        A_228 a_100();
    }
    public interface A_005 extends A_240 {
        A_005 a_040();
    }
    public interface A_030 extends A_239 {
        A_030 a_039();
    }
    public interface A_173 extends A_239 {
        A_173 a_069();
    }
    public interface A_060 extends A_239 {
        A_060 a_043();
    }
    public interface A_245 extends A_239 {
        A_245 a_009();
    }
    public interface A_042 extends A_239 {
        A_042 a_035();
    }
    public interface A_209 extends A_239 {
        A_209 a_090();
    }
    public interface A_216 extends A_239 {
    }
    public interface A_142 extends A_239 {
    }
    public interface A_246 extends A_239 {
        A_246 a_019();
    }
    public interface A_223 extends A_239 {
    }
    public interface A_211 extends A_239 {
        A_211 a_091();
    }
    public interface A_244 extends A_239 {
        A_244 a_008();
    }
    public interface A_019 extends A_239 {
        A_019 a_050();
    }
    public interface A_041 extends A_239 {
        A_041 a_034();
    }
    public interface A_208 extends A_239 {
        A_208 a_089();
    }
    public interface A_065 extends A_239 {
    }
    public interface A_127 extends A_239 {
        A_127 a_083();
    }
    public interface A_033 extends A_239 {
    }
    public interface A_153 extends A_239 {
    }
    public interface A_079 extends A_239 {
    }
    public interface A_025 extends A_239 {
    }
    public interface A_046 extends A_239 {
        A_046 a_042();
    }
    public interface A_002 extends A_239 {
    }
    public interface A_154 extends A_239 {
    }
    public interface A_077 extends A_239 {
        A_077 a_015();
    }
    public interface A_121 extends A_239 {
        A_121 a_053();
    }
    public interface A_036 extends A_239 {
        A_036 a_003();
    }
    public interface A_225 extends A_239 {
        A_225 a_054();
    }
    public interface A_181 extends A_239 {
        A_181 a_005();
    }
    public interface A_134 extends A_239 {
        A_134 a_057();
    }
    public interface A_017 extends A_239 {
    }
    public interface A_194 extends A_239 {
        A_194 a_081();
    }
    public interface A_243 extends A_239 {
        A_243 a_006();
    }
    public interface A_015 extends A_239 {
        A_015 a_004();
    }
    public interface A_028 extends A_239 {
        A_028 a_032();
    }
    public interface A_218 extends A_239 {
    }
    public interface A_174 extends A_239 {
    }
    public interface A_039 extends A_239 {
        A_039 a_023();
    }
    public interface A_029 extends A_239 {
    }
    public interface A_095 extends A_239 {
        A_095 a_029();
    }
    public interface A_096 extends A_239 {
    }
    public interface A_124 extends A_239 {
        A_124 a_028();
    }
    public interface A_202 extends A_239 {
        A_202 a_085();
    }
    public interface A_186 extends A_239 {
    }
    public interface A_120 extends A_239 {
    }
    public interface A_076 extends A_239 {
        A_076 a_014();
    }
    public interface A_052 extends A_239 {
        A_052 a_099();
    }
    public interface A_056 extends A_239 {
    }
    public interface A_020 extends A_239 {
        A_020 a_062();
    }
    public interface A_018 extends A_239 {
        A_018 a_045();
    }
    public interface A_149 extends A_239 {
        A_149 a_051();
    }
    public interface A_022 extends A_239 {
        A_022 a_075();
    }
    public interface A_063 extends A_239 {
        A_063 a_046();
    }
    public interface A_043 extends A_239 {
        A_043 a_038();
    }
    public interface A_167 extends A_239 {
    }
    public interface A_085 extends A_239 {
        A_085 a_018();
    }
    public interface A_032 extends A_239 {
    }
    public interface A_188 extends A_239 {
        A_188 a_076();
    }
    public interface A_126 extends A_239 {
    }
    public interface A_113 extends A_239 {
        A_113 a_032();
    }
    public interface A_051 extends A_239 {
        A_051 a_082();
    }
    public interface A_185 extends A_239 {
        A_185 a_074();
    }
    public interface A_099 extends A_239 {
    }
    public interface A_062 extends A_239 {
        A_062 a_045();
    }
    public interface A_237 extends A_239 {
        A_237 a_027();
    }
    public interface A_100 extends A_239 {
    }
    public interface A_189 extends A_000, A_005, A_190, A_240 {
        A_189 a_040();
        A_189 a_077();
    }
    public interface A_061 extends A_239 {
        A_061 a_044();
    }
    public interface A_104 extends A_239 {
        A_104 a_036();
    }
    public interface A_084 extends A_000, A_005, A_085, A_240 {
        A_084 a_040();
        A_084 a_018();
    }
    public interface A_129 extends A_000, A_005, A_130, A_240 {
        A_129 a_040();
        A_129 a_052();
    }
    public interface A_086 extends A_000, A_005, A_087, A_089, A_240 {
        A_086 a_040();
        A_086 a_024();
        A_086 a_098();
    }
    public interface A_125 extends A_239 {
    }
    public interface A_212 extends A_053, A_084, A_005, A_054, A_085, A_217,
            A_218, A_240 {
        A_212 a_040();
        A_212 a_000();
        A_212 a_018();
        A_212 a_093();
    }
    public interface A_171 extends A_170, A_175, A_005, A_173, A_176, A_174,
            A_240 {
        A_171 a_040();
        A_171 a_069();
        A_171 a_071();
        A_171 a_072();
    }
    public interface A_247 extends A_239 {
    }
    public interface A_183 extends A_053, A_084, A_005, A_054, A_085, A_185,
            A_186, A_240 {
        A_183 a_040();
        A_183 a_000();
        A_183 a_018();
        A_183 a_074();
    }
    public interface A_198 extends A_053, A_196, A_070, A_131, A_005, A_054,
            A_203, A_071, A_132, A_240 {
        A_198 a_040();
        A_198 a_000();
        A_198 a_084();
        A_198 a_011();
        A_198 a_056();
    }
    public interface A_070 extends A_000, A_005, A_071, A_240 {
        A_070 a_040();
        A_070 a_011();
    }
    public interface A_109 extends A_106, A_212, A_005, A_115, A_113, A_112,
            A_054, A_085, A_217, A_218, A_240 {
        A_109 a_040();
        A_109 a_039();
        A_109 a_032();
        A_109 a_033();
        A_109 a_000();
        A_109 a_018();
        A_109 a_093();
    }
    public interface A_158 extends A_159, A_161, A_152, A_005, A_054, A_160,
            A_162, A_153, A_154, A_240 {
        A_158 a_040();
        A_158 a_000();
        A_158 a_079();
    }
    public interface A_110 extends A_106, A_212, A_183, A_005, A_054, A_085,
            A_115, A_113, A_112, A_217, A_218, A_185, A_186, A_240 {
        A_110 a_040();
        A_110 a_000();
        A_110 a_018();
        A_110 a_039();
        A_110 a_032();
        A_110 a_033();
        A_110 a_093();
        A_110 a_074();
    }
    public interface A_200 extends A_000, A_005, A_202, A_240 {
        A_200 a_040();
        A_200 a_085();
    }
    public interface A_161 extends A_053, A_005, A_054, A_162, A_240 {
        A_161 a_040();
        A_161 a_000();
    }
    public interface A_175 extends A_000, A_005, A_176, A_240 {
        A_175 a_040();
        A_175 a_071();
    }
    public interface A_103 extends A_000, A_084, A_005, A_085, A_104, A_240 {
        A_103 a_040();
        A_103 a_018();
        A_103 a_036();
    }
    public interface A_093 extends A_090, A_152, A_005, A_054, A_085, A_091,
            A_092, A_153, A_154, A_240 {
        A_093 a_040();
        A_093 a_000();
        A_093 a_018();
        A_093 a_026();
        A_093 a_079();
        A_093 a_027();
    }
    public interface A_204 extends A_000, A_005, A_205, A_240 {
        A_204 a_040();
        A_204 a_086();
    }
    public interface A_067 extends A_059, A_152, A_005, A_054, A_085, A_063,
            A_062, A_064, A_065, A_066, A_061, A_060, A_153, A_154, A_240 {
        A_067 a_040();
        A_067 a_000();
        A_067 a_018();
        A_067 a_046();
        A_067 a_045();
        A_067 a_047();
        A_067 a_049();
        A_067 a_044();
        A_067 a_043();
        A_067 a_079();
    }
    public interface A_101 extends A_070, A_005, A_071, A_240 {
        A_101 a_040();
        A_101 a_011();
    }
    public interface A_224 extends A_000, A_225, A_240 {
        A_224 a_054();
        A_224 a_040();
    }
    public interface A_156 extends A_053, A_084, A_155, A_059, A_005, A_054,
            A_085, A_160, A_162, A_063, A_062, A_064, A_065, A_066, A_061,
            A_060, A_240 {
        A_156 a_040();
        A_156 a_000();
        A_156 a_018();
        A_156 a_046();
        A_156 a_045();
        A_156 a_047();
        A_156 a_049();
        A_156 a_044();
        A_156 a_043();
    }
    public interface A_122 extends A_116, A_152, A_005, A_054, A_085, A_121,
            A_119, A_118, A_120, A_153, A_154, A_240 {
        A_122 a_040();
        A_122 a_000();
        A_122 a_018();
        A_122 a_053();
        A_122 a_046();
        A_122 a_045();
        A_122 a_048();
        A_122 a_079();
        A_122 a_080();
    }
    public interface A_184 extends A_183, A_152, A_005, A_054, A_085, A_185,
            A_186, A_153, A_154, A_240 {
        A_184 a_040();
        A_184 a_000();
        A_184 a_018();
        A_184 a_074();
        A_184 a_079();
    }
    public interface A_180 extends A_000, A_181, A_240 {
        A_180 a_005();
        A_180 a_040();
    }
    public interface A_191 extends A_000, A_005, A_192, A_240 {
        A_191 a_040();
        A_191 a_078();
    }
    public interface A_107 extends A_106, A_094, A_005, A_115, A_113, A_112,
            A_095, A_096, A_240 {
        A_107 a_040();
        A_107 a_039();
        A_107 a_032();
        A_107 a_033();
        A_107 a_029();
        A_107 a_000();
        A_107 a_018();
    }
    public interface A_102 extends A_196, A_005, A_203, A_240 {
        A_102 a_040();
        A_102 a_084();
    }
    public interface A_177 extends A_000, A_005, A_179, A_240 {
        A_177 a_040();
    }
    public interface A_123 extends A_195, A_005, A_054, A_124, A_127, A_128,
            A_126, A_125, A_240 {
        A_123 a_040();
        A_123 a_000();
        A_123 a_028();
        A_123 a_083();
        A_123 a_064();
        A_123 a_055();
        A_123 a_018();
        A_123 a_079();
        A_123 a_080();
        A_123 a_081();
        A_123 a_077();
        A_123 a_036();
        A_123 a_056();
        A_123 a_012();
        A_123 a_078();
        A_123 a_076();
        A_123 a_057();
    }
    public interface A_088 extends A_106, A_086, A_240 {
        A_088 a_040();
        A_088 a_039();
        A_088 a_032();
        A_088 a_033();
        A_088 a_000();
        A_088 a_018();
        A_088 a_024();
        A_088 a_098();
    }
    public interface A_094 extends A_084, A_005, A_085, A_095, A_096, A_240 {
        A_094 a_040();
        A_094 a_018();
        A_094 a_029();
    }
    public interface A_105 extends A_098, A_236, A_005, A_085, A_153, A_074,
            A_099, A_100, A_240 {
        A_105 a_040();
        A_105 a_018();
        A_105 a_079();
        A_105 a_012();
        A_105 a_030();
        A_105 a_031();
        A_105 a_000();
        A_105 a_081();
        A_105 a_077();
        A_105 a_036();
        A_105 a_056();
        A_105 a_078();
        A_105 a_076();
        A_105 a_057();
    }
    public interface A_199 extends A_196, A_090, A_005, A_054, A_085, A_091,
            A_092, A_203, A_240 {
        A_199 a_040();
        A_199 a_000();
        A_199 a_018();
        A_199 a_026();
        A_199 a_084();
        A_199 a_027();
    }
    public interface A_080 extends A_075, A_236, A_005, A_085, A_153, A_074,
            A_076, A_077, A_078, A_079, A_240 {
        A_080 a_040();
        A_080 a_018();
        A_080 a_079();
        A_080 a_012();
        A_080 a_014();
        A_080 a_015();
        A_080 a_016();
        A_080 a_000();
        A_080 a_080();
        A_080 a_081();
        A_080 a_077();
        A_080 a_036();
        A_080 a_056();
        A_080 a_078();
        A_080 a_076();
        A_080 a_057();
    }
    public interface A_172 extends A_170, A_219, A_005, A_054, A_085, A_173,
            A_222, A_223, A_174, A_240 {
        A_172 a_040();
        A_172 a_000();
        A_172 a_018();
        A_172 a_069();
        A_172 a_095();
        A_172 a_072();
    }
    public interface A_108 extends A_106, A_180, A_206, A_005, A_115, A_113,
            A_112, A_181, A_208, A_207, A_209, A_240 {
        A_108 a_040();
        A_108 a_039();
        A_108 a_032();
        A_108 a_033();
        A_108 a_005();
        A_108 a_089();
        A_108 a_088();
        A_108 a_090();
        A_108 a_000();
        A_108 a_018();
    }
    public interface A_195 extends A_053, A_084, A_152, A_193, A_189, A_103,
            A_131, A_073, A_191, A_187, A_133, A_005, A_054, A_085, A_153,
            A_154, A_194, A_190, A_104, A_132, A_074, A_192, A_188, A_134,
            A_240 {
        A_195 a_040();
        A_195 a_000();
        A_195 a_018();
        A_195 a_079();
        A_195 a_080();
        A_195 a_081();
        A_195 a_077();
        A_195 a_036();
        A_195 a_056();
        A_195 a_012();
        A_195 a_078();
        A_195 a_076();
        A_195 a_057();
    }
    public interface A_220 extends A_219, A_236, A_005, A_085, A_153, A_074,
            A_240 {
        A_220 a_040();
        A_220 a_018();
        A_220 a_079();
        A_220 a_012();
        A_220 a_000();
        A_220 a_095();
        A_220 a_080();
        A_220 a_081();
        A_220 a_077();
        A_220 a_036();
        A_220 a_056();
        A_220 a_078();
        A_220 a_076();
        A_220 a_057();
    }
    public interface A_146 extends A_000, A_005, A_151, A_150, A_149, A_240 {
        A_146 a_040();
        A_146 a_073();
        A_146 a_051();
    }
    public interface A_090 extends A_000, A_053, A_084, A_005, A_054, A_085,
            A_237, A_091, A_092, A_240 {
        A_090 a_040();
        A_090 a_000();
        A_090 a_018();
        A_090 a_027();
        A_090 a_026();
    }
    public interface A_057 extends A_106, A_058, A_240 {
        A_057 a_002();
        A_057 a_040();
        A_057 a_039();
        A_057 a_032();
        A_057 a_033();
        A_057 a_000();
        A_057 a_018();
    }
    public interface A_098 extends A_084, A_005, A_085, A_099, A_100, A_240 {
    }
    public interface A_136 extends A_084, A_005, A_085, A_138, A_137, A_240 {
    }
    public interface A_116 extends A_053, A_084, A_005, A_054, A_085, A_121,
            A_119, A_118, A_120, A_240 {
        A_116 a_040();
        A_116 a_000();
        A_116 a_018();
        A_116 a_053();
        A_116 a_046();
        A_116 a_045();
        A_116 a_048();
    }
    public interface A_159 extends A_053, A_005, A_054, A_160, A_240 {
        A_159 a_040();
        A_159 a_000();
    }
    public interface A_140 extends A_139, A_236, A_005, A_085, A_153, A_074,
            A_141, A_142, A_240 {
        A_140 a_040();
        A_140 a_018();
        A_140 a_079();
        A_140 a_012();
        A_140 a_060();
        A_140 a_061();
        A_140 a_000();
        A_140 a_080();
        A_140 a_081();
        A_140 a_077();
        A_140 a_036();
        A_140 a_056();
        A_140 a_078();
        A_140 a_076();
        A_140 a_057();
    }
    public interface A_178 extends A_177, A_144, A_005, A_179, A_145, A_240 {
        A_178 a_040();
    }
    public interface A_139 extends A_053, A_005, A_054, A_141, A_142, A_240 {
        A_139 a_040();
        A_139 a_000();
        A_139 a_060();
        A_139 a_061();
    }
    public interface A_165 extends A_163, A_152, A_005, A_054, A_166, A_167,
            A_153, A_154, A_240 {
        A_165 a_040();
        A_165 a_000();
        A_165 a_065();
        A_165 a_066();
        A_165 a_079();
        A_165 a_080();
    }
    public interface A_053 extends A_000, A_238<Long>, A_005, A_054, A_240 {
        A_053 a_040();
        A_053 a_000();
    }
    public interface A_135 extends A_136, A_236, A_005, A_085, A_153, A_074,
            A_138, A_137, A_240 {
        A_135 a_040();
        A_135 a_018();
        A_135 a_079();
        A_135 a_012();
        A_135 a_059();
        A_135 a_058();
        A_135 a_000();
        A_135 a_081();
        A_135 a_077();
        A_135 a_036();
        A_135 a_056();
        A_135 a_078();
        A_135 a_076();
        A_135 a_057();
    }
    public interface A_148 extends A_146, A_236, A_005, A_085, A_153, A_074,
            A_240 {
        A_148 a_040();
        A_148 a_018();
        A_148 a_079();
        A_148 a_012();
        A_148 a_051();
        A_148 a_000();
        A_148 a_081();
        A_148 a_077();
        A_148 a_036();
        A_148 a_056();
        A_148 a_078();
        A_148 a_076();
        A_148 a_057();
    }
    public interface A_206 extends A_000, A_005, A_208, A_207, A_209, A_240 {
        A_206 a_040();
        A_206 a_089();
        A_206 a_088();
        A_206 a_090();
    }
    public interface A_215 extends A_000, A_005, A_216, A_240 {
        A_215 a_040();
        A_215 a_092();
    }
    public interface A_117 extends A_116, A_090, A_005, A_054, A_085, A_121,
            A_119, A_118, A_120, A_091, A_092, A_240 {
        A_117 a_040();
        A_117 a_000();
        A_117 a_018();
        A_117 a_053();
        A_117 a_046();
        A_117 a_045();
        A_117 a_048();
        A_117 a_026();
        A_117 a_027();
    }
    public interface A_082 extends A_000, A_005, A_083, A_240 {
        A_082 a_040();
    }
    public interface A_182 extends A_053, A_084, A_152, A_193, A_189, A_005,
            A_054, A_085, A_153, A_154, A_194, A_190, A_240 {
        A_182 a_040();
        A_182 a_000();
        A_182 a_018();
        A_182 a_079();
        A_182 a_080();
        A_182 a_081();
        A_182 a_077();
    }
    public interface A_055 extends A_000, A_005, A_056, A_240 {
    }
    public interface A_193 extends A_000, A_005, A_194, A_240 {
        A_193 a_040();
        A_193 a_081();
    }
    public interface A_214 extends A_212, A_152, A_005, A_054, A_085, A_217,
            A_218, A_153, A_154, A_240 {
        A_214 a_040();
        A_214 a_000();
        A_214 a_018();
        A_214 a_093();
        A_214 a_079();
    }
    public interface A_059 extends A_053, A_084, A_005, A_054, A_085, A_063,
            A_062, A_064, A_065, A_066, A_061, A_060, A_240 {
        A_059 a_040();
        A_059 a_000();
        A_059 a_018();
        A_059 a_046();
        A_059 a_045();
        A_059 a_047();
        A_059 a_048();
        A_059 a_049();
        A_059 a_044();
        A_059 a_043();
    }
    public interface A_226 extends A_000, A_228, A_240 {
        A_226 a_100();
        A_226 a_040();
    }
    public interface A_210 extends A_053, A_005, A_054, A_211, A_240 {
        A_210 a_040();
        A_210 a_000();
        A_210 a_091();
    }
    public interface A_073 extends A_000, A_005, A_074, A_240 {
        A_073 a_040();
        A_073 a_012();
    }
    public interface A_157 extends A_155, A_236, A_005, A_085, A_153, A_074,
            A_160, A_162, A_240 {
        A_157 a_040();
        A_157 a_018();
        A_157 a_079();
        A_157 a_012();
        A_157 a_000();
        A_157 a_081();
        A_157 a_077();
        A_157 a_036();
        A_157 a_056();
        A_157 a_078();
        A_157 a_076();
        A_157 a_057();
    }
    public interface A_131 extends A_053, A_005, A_054, A_132, A_240 {
        A_131 a_040();
        A_131 a_000();
        A_131 a_056();
    }
    public interface A_152 extends A_053, A_005, A_054, A_153, A_154, A_240 {
        A_152 a_040();
        A_152 a_000();
        A_152 a_079();
    }
    public interface A_106 extends A_053, A_084, A_005, A_115, A_113, A_112,
            A_240 {
        A_106 a_040();
        A_106 a_039();
        A_106 a_032();
        A_106 a_033();
        A_106 a_000();
        A_106 a_018();
    }
    public interface A_147 extends A_106, A_236, A_005, A_085, A_153, A_074,
            A_240 {
        A_147 a_040();
        A_147 a_018();
        A_147 a_012();
        A_147 a_039();
        A_147 a_032();
        A_147 a_033();
        A_147 a_000();
        A_147 a_081();
        A_147 a_077();
        A_147 a_036();
        A_147 a_056();
        A_147 a_078();
        A_147 a_076();
        A_147 a_057();
    }
    public interface A_081 extends A_075, A_152, A_005, A_054, A_076, A_077,
            A_078, A_079, A_153, A_154, A_240 {
        A_081 a_040();
        A_081 a_000();
        A_081 a_014();
        A_081 a_015();
        A_081 a_016();
        A_081 a_079();
        A_081 a_080();
    }
    public interface A_197 extends A_196, A_070, A_236, A_005, A_085, A_153,
            A_074, A_203, A_071, A_240 {
        A_197 a_040();
        A_197 a_018();
        A_197 a_079();
        A_197 a_012();
        A_197 a_084();
        A_197 a_011();
        A_197 a_000();
        A_197 a_081();
        A_197 a_077();
        A_197 a_036();
        A_197 a_056();
        A_197 a_078();
        A_197 a_076();
        A_197 a_057();
    }
    public interface A_213 extends A_212, A_215, A_236, A_005, A_085, A_153,
            A_074, A_240 {
        A_213 a_040();
        A_213 a_018();
        A_213 a_079();
        A_213 a_012();
        A_213 a_000();
        A_213 a_093();
        A_213 a_092();
        A_213 a_081();
        A_213 a_077();
        A_213 a_036();
        A_213 a_056();
        A_213 a_078();
        A_213 a_076();
        A_213 a_057();
    }
    public interface A_143 extends A_139, A_152, A_005, A_141, A_142, A_153,
            A_154, A_240 {
        A_143 a_040();
        A_143 a_060();
        A_143 a_061();
        A_143 a_079();
        A_143 a_080();
        A_143 a_000();
    }
    public interface A_221 extends A_219, A_152, A_005, A_054, A_085, A_222,
            A_223, A_153, A_154, A_240 {
        A_221 a_040();
        A_221 a_000();
        A_221 a_018();
        A_221 a_095();
        A_221 a_079();
        A_221 a_080();
    }
    public interface A_196 extends A_000, A_005, A_203, A_240 {
        A_196 a_040();
        A_196 a_084();
    }
    public interface A_155 extends A_159, A_161, A_005, A_054, A_160, A_162,
            A_240 {
        A_155 a_040();
        A_155 a_000();
    }
    public interface A_097 extends A_094, A_152, A_005, A_085, A_095, A_096,
            A_153, A_154, A_240 {
        A_097 a_040();
        A_097 a_018();
        A_097 a_029();
        A_097 a_079();
        A_097 a_000();
    }
    public interface A_219 extends A_053, A_084, A_005, A_054, A_085, A_222,
            A_223, A_240 {
        A_219 a_040();
        A_219 a_000();
        A_219 a_018();
        A_219 a_095();
        A_219 a_096();
    }
    public interface A_068 extends A_053, A_005, A_054, A_069, A_240 {
        A_068 a_040();
        A_068 a_000();
        A_068 a_010();
    }
    public interface A_114 extends A_106, A_236, A_005, A_085, A_153, A_074,
            A_115, A_113, A_240 {
        A_114 a_040();
        A_114 a_018();
        A_114 a_079();
        A_114 a_012();
        A_114 a_039();
        A_114 a_032();
        A_114 a_033();
        A_114 a_000();
        A_114 a_080();
        A_114 a_081();
        A_114 a_077();
        A_114 a_036();
        A_114 a_056();
        A_114 a_078();
        A_114 a_076();
        A_114 a_057();
    }
    public interface A_163 extends A_053, A_005, A_054, A_166, A_167, A_240 {
        A_163 a_040();
        A_163 a_000();
        A_163 a_065();
        A_163 a_066();
    }
    public interface A_187 extends A_000, A_005, A_188, A_240 {
        A_187 a_040();
        A_187 a_076();
    }
    public interface A_144 extends A_000, A_005, A_145, A_240 {
        A_144 a_040();
    }
    public interface A_111 extends A_106, A_212, A_219, A_170, A_005, A_054,
            A_085, A_115, A_113, A_112, A_217, A_218, A_222, A_223, A_173,
            A_240 {
        A_111 a_040();
        A_111 a_000();
        A_111 a_018();
        A_111 a_039();
        A_111 a_032();
        A_111 a_033();
        A_111 a_093();
        A_111 a_095();
        A_111 a_069();
    }
    public interface A_168 extends A_000, A_053, A_005, A_054, A_169, A_240 {
        A_168 a_040();
        A_168 a_000();
        A_168 a_037();
    }
    public interface A_227 extends A_226, A_236, A_005, A_085, A_153, A_074,
            A_228, A_240 {
        A_227 a_040();
        A_227 a_018();
        A_227 a_079();
        A_227 a_012();
        A_227 a_100();
        A_227 a_000();
        A_227 a_081();
        A_227 a_077();
        A_227 a_036();
        A_227 a_056();
        A_227 a_078();
        A_227 a_076();
        A_227 a_057();
    }
    public interface A_075 extends A_053, A_005, A_054, A_076, A_077, A_078,
            A_079, A_240 {
        A_075 a_040();
        A_075 a_000();
        A_075 a_014();
        A_075 a_015();
        A_075 a_016();
        A_075 a_017();
    }
    public interface A_008 extends A_239 {
        A_008 a_040();
    }
    public interface A_072 extends A_070, A_090, A_005, A_054, A_085, A_091,
            A_092, A_071, A_240 {
        A_072 a_040();
        A_072 a_000();
        A_072 a_018();
        A_072 a_026();
        A_072 a_011();
        A_072 a_027();
    }
    public interface A_170 extends A_000, A_005, A_173, A_174, A_240 {
        A_170 a_040();
        A_170 a_069();
        A_170 a_072();
    }
    public interface A_012 extends A_239 {
        A_012 a_063();
    }
    public interface A_164 extends A_163, A_236, A_005, A_085, A_153, A_074,
            A_166, A_167, A_240 {
        A_164 a_040();
        A_164 a_018();
        A_164 a_079();
        A_164 a_012();
        A_164 a_065();
        A_164 a_066();
        A_164 a_000();
        A_164 a_080();
        A_164 a_081();
        A_164 a_077();
        A_164 a_036();
        A_164 a_056();
        A_164 a_078();
        A_164 a_076();
        A_164 a_057();
    }
    public interface A_006 extends A_239 {
        A_006 a_004();
    }
    public interface A_007 extends A_239 {
    }
    public interface A_201 extends A_200, A_236, A_005, A_085, A_153, A_074,
            A_240 {
        A_201 a_040();
        A_201 a_018();
        A_201 a_079();
        A_201 a_012();
        A_201 a_085();
        A_201 a_000();
        A_201 a_081();
        A_201 a_077();
        A_201 a_036();
        A_201 a_056();
        A_201 a_078();
        A_201 a_076();
        A_201 a_057();
    }
    public interface A_011 extends A_239 {
        A_011 a_062();
    }
    public interface A_014 extends A_239 {
        A_014 a_075();
    }
    public interface A_009 extends A_239 {
        A_009 a_045();
    }
    public interface A_010 extends A_239 {
        A_010 a_050();
    }
    public interface A_013 extends A_239 {
        A_013 a_067();
    }
    public interface A_236 extends A_195, A_005, A_085, A_153, A_074, A_240 {
        A_236 a_040();
        A_236 a_018();
        A_236 a_079();
        A_236 a_012();
        A_236 a_000();
        A_236 a_081();
        A_236 a_077();
        A_236 a_036();
        A_236 a_056();
        A_236 a_078();
        A_236 a_076();
        A_236 a_057();
    }

    interface X_1 {

        AbstractList x_8();
    }
    interface X_2 {

        Cloneable x_0();
    }
    interface X_3 {

        Serializable x_1();
    }
    interface X_4 {

        Object x_7();
    }
    interface X_5 {

        RandomAccess x_6();
    }
    interface X_6 {

        RandomAccess x_0();
    }
    interface X_7 {

        Serializable x_5();
    }
    interface X_8 {

        Object x_4();
    }
    interface X_9 {

        RandomAccess x_5();
    }
    interface X_10 {

        Cloneable x_5();
    }
    interface X_11 {

        RandomAccess x_9();
    }
    interface X_12 {

        Cloneable x_9();
    }
    interface X_13 {

        Iterable x_2();
    }
    interface X_14 {

        Collection x_7();
    }
    interface X_15 {

        Serializable x_4();
    }
    interface X_16 {

        Cloneable x_7();
    }
    interface X_17 {

        Object x_1();
    }
    interface X_18 {

        ArrayList x_6();
    }
    interface X_19 {

        List x_5();
    }
    interface X_20 {

        Collection x_2();
    }
    interface X_21 {

        List x_1();
    }
    interface X_22 {

        List x_3();
    }
    interface X_23 {

        RandomAccess x_3();
    }
    interface X_24 {

        RandomAccess x_1();
    }
    interface X_25 {

        Object x_6();
    }
    interface X_26 {

        Cloneable x_7();
    }
    interface X_27 {

        Iterable x_0();
    }
    interface X_28 {

        Iterable x_1();
    }
    interface X_29 {

        AbstractList x_7();
    }
    interface X_30 {

        AbstractList x_1();
    }
    interface X_31 {

        Cloneable x_9();
    }
    interface X_32 {

        ArrayList x_6();
    }
    interface X_33 {

        Cloneable x_2();
    }
    interface X_34 {

        Iterable x_6();
    }
    interface X_35 {

        Iterable x_9();
    }
    interface X_36 {

        AbstractList x_9();
    }
    interface X_37 {

        Iterable x_7();
    }
    interface X_38 {

        Iterable x_3();
    }
    interface X_39 {

        Iterable x_9();
    }
    interface X_40 {

        AbstractList x_3();
    }
    interface X_41 {

        List x_0();
    }
    interface X_42 {

        Iterable x_0();
    }
    interface X_43 {

        Iterable x_2();
    }
    interface X_44 {

        ArrayList x_4();
    }
    interface X_45 {

        AbstractList x_4();
    }
    interface X_46 {

        Collection x_4();
    }
    interface X_47 {

        ArrayList x_2();
    }
    interface X_48 {

        ArrayList x_6();
    }
    interface X_49 {

        Serializable x_1();
    }
    interface X_50 {

        Cloneable x_7();
    }
    interface X_51 {

        Collection x_5();
    }
    interface X_52 {

        RandomAccess x_5();
    }
    interface X_53 {

        Collection x_5();
    }
    interface X_54 {

        RandomAccess x_4();
    }
    interface X_55 {

        Collection x_0();
    }
    interface X_56 {

        Collection x_7();
    }
    interface X_57 {

        Iterable x_9();
    }
    interface X_58 {

        List x_3();
    }
    interface X_59 {

        Serializable x_7();
    }
    interface X_60 {

        AbstractCollection x_6();
    }
    interface X_61 {

        AbstractList x_9();
    }
    interface X_62 {

        List x_7();
    }
    interface X_63 {

        AbstractCollection x_3();
    }
    interface X_64 {

        RandomAccess x_4();
    }
    interface X_65 {

        Object x_3();
    }
    interface X_66 {

        RandomAccess x_6();
    }
    interface X_67 {

        Cloneable x_6();
    }
    interface X_68 {

        Cloneable x_3();
    }
    interface X_69 {

        Collection x_5();
    }
    interface X_70 {

        AbstractCollection x_0();
    }
    interface X_71 {

        Object x_8();
    }
    interface X_72 {

        AbstractCollection x_3();
    }
    interface X_73 {

        Serializable x_4();
    }
    interface X_74 {

        AbstractList x_8();
    }
    interface X_75 {

        ArrayList x_1();
    }
    interface X_76 {

        List x_5();
    }
    interface X_77 {

        Object x_0();
    }
    interface X_78 {

        Collection x_0();
    }
    interface X_79 {

        ArrayList x_2();
    }
    interface X_80 {

        ArrayList x_8();
    }
    interface X_81 {

        Cloneable x_3();
    }
    interface X_82 {

        Serializable x_1();
    }
    interface X_83 {

        List x_1();
    }
    interface X_84 {

        Collection x_5();
    }
    interface X_85 {

        RandomAccess x_9();
    }
    interface X_86 {

        AbstractList x_3();
    }
    interface X_87 {

        Cloneable x_6();
    }
    interface X_88 {

        Object x_2();
    }
    interface X_89 {

        ArrayList x_5();
    }
    interface X_90 {

        Iterable x_1();
    }
    interface X_91 {

        ArrayList x_4();
    }
    interface X_92 {

        Iterable x_6();
    }
    interface X_93 {

        Collection x_7();
    }
    interface X_94 {

        Iterable x_2();
    }
    interface X_95 {

        AbstractList x_7();
    }
    interface X_96 {

        RandomAccess x_2();
    }
    interface X_97 {

        RandomAccess x_2();
    }
    interface X_98 {

        List x_6();
    }
    interface X_99 {

        Object x_4();
    }
    interface X_100 {

        Collection x_7();
    }
    static class X
            implements X_1, X_2, X_3, X_4, X_5, X_6, X_7, X_8, X_9, X_10, X_11,
                       X_12, X_13, X_14, X_15, X_16, X_17, X_18, X_19, X_20,
                       X_21, X_22, X_23, X_24, X_25, X_26, X_27, X_28, X_29,
                       X_30, X_31, X_32, X_33, X_34, X_35, X_36, X_37, X_38,
                       X_39, X_40, X_41, X_42, X_43, X_44, X_45, X_46, X_47,
                       X_48, X_49, X_50, X_51, X_52, X_53, X_54, X_55, X_56,
                       X_57, X_58, X_59, X_60, X_61, X_62, X_63, X_64, X_65,
                       X_66, X_67, X_68, X_69, X_70, X_71, X_72, X_73, X_74,
                       X_75, X_76, X_77, X_78, X_79, X_80, X_81, X_82, X_83,
                       X_84, X_85, X_86, X_87, X_88, X_89, X_90, X_91, X_92,
                       X_93, X_94, X_95, X_96, X_97, X_98, X_99, X_100 {

        public ArrayList x_0() {
            return null;
        }

        public ArrayList x_1() {
            return null;
        }

        public ArrayList x_2() {
            return null;
        }

        public ArrayList x_3() {
            return null;
        }

        public ArrayList x_4() {
            return null;
        }

        public ArrayList x_5() {
            return null;
        }

        public ArrayList x_6() {
            return null;
        }

        public ArrayList x_7() {
            return null;
        }

        public ArrayList x_8() {
            return null;
        }

        public ArrayList x_9() {
            return null;
        }
    }
}
