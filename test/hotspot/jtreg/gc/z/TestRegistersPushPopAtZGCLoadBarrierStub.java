/*
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

package gc.z;

/**
 * @test TestRegistersPushPopAtZGCLoadBarrierStub
 * @bug 8326541
 * @summary Test to verify that registers are saved and restored correctly based on
            the actual register usage length on aarch64 when entering load barrier stub.
 * @library /test/lib /
 * @modules jdk.incubator.vector
 *
 * @requires vm.gc.ZGenerational & vm.debug
 * @requires os.arch=="aarch64"
 *
 * @run driver gc.z.TestRegistersPushPopAtZGCLoadBarrierStub
 */

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.Vector;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

class Inner {}

class InnerFloat extends Inner {
    float data;
    public InnerFloat(float f) {
        data = f;
    }
}

class InnerDouble extends Inner {
    double data;
    public InnerDouble(double f) {
        data = f;
    }
}

class Outer {
    volatile Inner field;
    public Outer(Inner i) {
        field = i;
    }
}

public class TestRegistersPushPopAtZGCLoadBarrierStub {

    class Launcher {
        private final static int NUM = 1024;
        private final static int ITERATIONS = 20_000;
        private final static RandomGenerator RANDOM = RandomGeneratorFactory.getDefault().create(0);
        private final static Map<String, Runnable> TESTS;

        private static float[] f_array;
        private static Outer f_outer;
        private static Outer d_outer;

        static {
            f_array = new float[NUM];
            for (int i = 0; i < NUM; i++) {
                f_array[i] = RANDOM.nextFloat();
            }

            InnerFloat f_inner = new InnerFloat(RANDOM.nextFloat());
            InnerDouble d_inner = new InnerDouble(RANDOM.nextDouble());
            f_outer = new Outer(f_inner);
            d_outer = new Outer(d_inner);

            TESTS = new LinkedHashMap<>();
            TESTS.put("test_one_float_push_pop_at_load_barrier", Launcher::test_one_float);
            TESTS.put("test_two_floats_push_pop_at_load_barrier", Launcher::test_two_floats);
            TESTS.put("test_three_floats_push_pop_at_load_barrier", Launcher::test_three_floats);
            TESTS.put("test_one_double_push_pop_at_load_barrier", Launcher::test_one_double);
            TESTS.put("test_two_doubles_push_pop_at_load_barrier", Launcher::test_two_doubles);
            TESTS.put("test_three_doubles_push_pop_at_load_barrier", Launcher::test_three_doubles);
            TESTS.put("test_one_vector_128_push_pop_at_load_barrier", Launcher::test_one_vector_128);
            TESTS.put("test_two_vectors_128_push_pop_at_load_barrier", Launcher::test_two_vectors_128);
            TESTS.put("test_three_vectors_128_push_pop_at_load_barrier", Launcher::test_three_vectors_128);
            TESTS.put("test_vector_max_push_pop_at_load_barrier", Launcher::test_vector_max);
            TESTS.put("test_float_and_vector_push_pop_at_load_barrier", Launcher::test_float_and_vector);
        }

        static float test_one_float_push_pop_at_load_barrier(Outer outer, float f) {
            Inner inner = outer.field;
            return f + ((InnerFloat)inner).data;
        }

        static float test_two_floats_push_pop_at_load_barrier(Outer outer, float f1, float f2) {
            Inner inner = outer.field;
            return f1 + f2 + ((InnerFloat)inner).data;
        }

        static float test_three_floats_push_pop_at_load_barrier(Outer outer, float f1, float f2, float f3) {
            Inner inner = outer.field;
            return f1 + f2 + f3 + ((InnerFloat)inner).data;
        }

        static double test_one_double_push_pop_at_load_barrier(Outer outer, double d) {
            Inner inner = outer.field;
            return d + ((InnerDouble)inner).data;
        }

        static double test_two_doubles_push_pop_at_load_barrier(Outer outer, double d1, double d2) {
            Inner inner = outer.field;
            return d1 + d2 + ((InnerDouble)inner).data;
        }

        static double test_three_doubles_push_pop_at_load_barrier(Outer outer, double d1, double d2, double d3) {
            Inner inner = outer.field;
            return d1 + d2 + d3 + ((InnerDouble)inner).data;
        }

        static void test_one_vector_128_push_pop_at_load_barrier(float[] b, Outer outer) {
            VectorSpecies<Float> float_species = FloatVector.SPECIES_128;

            FloatVector av = FloatVector.zero(float_species);
            for (int i = 0; i < b.length; i += float_species.length()) {
                Inner inner = outer.field;
                FloatVector bv = FloatVector.fromArray(float_species, b, i);
                float value = ((InnerFloat)inner).data;
                av = av.add(bv).add(value);
            }
        }

        static void test_two_vectors_128_push_pop_at_load_barrier(float[] b, Outer outer) {
            VectorSpecies<Float> float_species = FloatVector.SPECIES_128;

            FloatVector av1 = FloatVector.zero(float_species);
            FloatVector av2 = FloatVector.zero(float_species);
            for (int i = 0; i < b.length; i += float_species.length()) {
                Inner inner = outer.field;
                FloatVector bv = FloatVector.fromArray(float_species, b, i);
                float value = ((InnerFloat)inner).data;
                av1 = av1.add(bv).add(value);
                av2 = av2.add(av1);
            }
        }

        static void test_three_vectors_128_push_pop_at_load_barrier(float[] b, Outer outer) {
            VectorSpecies<Float> float_species = FloatVector.SPECIES_128;

            FloatVector av1 = FloatVector.zero(float_species);
            FloatVector av2 = FloatVector.zero(float_species);
            FloatVector av3 = FloatVector.zero(float_species);
            for (int i = 0; i < b.length; i += float_species.length()) {
                Inner inner = outer.field;
                FloatVector bv = FloatVector.fromArray(float_species, b, i);
                float value = ((InnerFloat)inner).data;
                av1 = av1.add(bv).add(value);
                av2 = av2.add(av1);
                av3 = av3.add(av2);
            }
        }

        static void test_vector_max_push_pop_at_load_barrier(float[] b, Outer outer) {
            VectorSpecies<Float> float_species = FloatVector.SPECIES_MAX;

            FloatVector av = FloatVector.zero(float_species);
            for (int i = 0; i < b.length; i += float_species.length()) {
                Inner inner = outer.field;
                FloatVector bv = FloatVector.fromArray(float_species, b, i);
                float value = ((InnerFloat)inner).data;
                av = av.add(bv).add(value);
            }
        }

        static void test_float_and_vector_push_pop_at_load_barrier(float[] b, Outer outer, float f) {
            VectorSpecies<Float> float_species = FloatVector.SPECIES_MAX;

            FloatVector av = FloatVector.zero(float_species);
            for (int i = 0; i < b.length; i += float_species.length()) {
                Inner inner = outer.field;
                FloatVector bv = FloatVector.fromArray(float_species, b, i);
                float value = ((InnerFloat)inner).data + f;
                av = av.add(bv).add(value);
            }
        }

        static void test_one_float() {
            for (int i = 0; i < ITERATIONS; i++) {
                test_one_float_push_pop_at_load_barrier(f_outer, RANDOM.nextFloat());
            }
        }

        static void test_two_floats() {
            for (int i = 0; i < ITERATIONS; i++) {
                test_two_floats_push_pop_at_load_barrier(f_outer, RANDOM.nextFloat(), RANDOM.nextFloat());
            }
        }

        static void test_three_floats() {
            for (int i = 0; i < ITERATIONS; i++) {
                test_three_floats_push_pop_at_load_barrier(f_outer, RANDOM.nextFloat(), RANDOM.nextFloat(), RANDOM.nextFloat());
            }
        }

        static void test_one_double() {
            for (int i = 0; i < ITERATIONS; i++) {
                test_one_double_push_pop_at_load_barrier(d_outer, RANDOM.nextDouble());
            }
        }

        static void test_two_doubles() {
            for (int i = 0; i < ITERATIONS; i++) {
                test_two_doubles_push_pop_at_load_barrier(d_outer, RANDOM.nextDouble(), RANDOM.nextDouble());
            }
        }

        static void test_three_doubles() {
            for (int i = 0; i < ITERATIONS; i++) {
                test_three_doubles_push_pop_at_load_barrier(d_outer, RANDOM.nextDouble(), RANDOM.nextDouble(), RANDOM.nextDouble());
            }
        }

        static void test_one_vector_128() {
            for (int i = 0; i < ITERATIONS; i++) {
                test_one_vector_128_push_pop_at_load_barrier(f_array, f_outer);
            }
        }

        static void test_two_vectors_128() {
            for (int i = 0; i < ITERATIONS; i++) {
                test_two_vectors_128_push_pop_at_load_barrier(f_array, f_outer);
            }
        }

        static void test_three_vectors_128() {
            for (int i = 0; i < ITERATIONS; i++) {
                test_three_vectors_128_push_pop_at_load_barrier(f_array, f_outer);
            }
        }

        static void test_vector_max() {
            for (int i = 0; i < ITERATIONS; i++) {
                test_vector_max_push_pop_at_load_barrier(f_array, f_outer);
            }
        }

        static void test_float_and_vector() {
            for (int i = 0; i < ITERATIONS; i++) {
                test_float_and_vector_push_pop_at_load_barrier(f_array, f_outer, RANDOM.nextFloat());
            }
        }

        public static void main(String args[]) {
            Runnable r = TESTS.get(args[0]);
            r.run();
        }
    }

    static boolean containOnlyOneOccuranceOfKeyword(String text, String keyword) {
        int firstIndex = text.indexOf(keyword);
        int lastIndex = text.lastIndexOf(keyword);
        return firstIndex != -1 && firstIndex == lastIndex;
    }

    // Check that registers are pushed and poped with correct register type and number
    static void checkPushPopRegNumberAndType(String stdout, String keyword, String expected_freg_type,
                                             int expected_number_of_fregs) throws Exception {
        String expected = keyword + expected_number_of_fregs + " " + expected_freg_type + " registers";

        String regex = keyword + "(\\d+) " + expected_freg_type + " registers";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(stdout);

        if (m.find()) {
            String found = m.group();
            Asserts.assertEquals(found, expected, "found '" + found + "' but should print '" + expected + "'");
        } else {
            throw new RuntimeException("'" + regex + "' is not found in stdout");
        }

        if (m.find()) {
            throw new RuntimeException("Stdout is expected to contain only one occurance of '" + regex +
                                       "'. Found another occurance: '" + m.group() + "'");
        }
    }

    static String launchJavaTestProcess(String test_name) throws Exception {
        ArrayList<String> command = new ArrayList<String>();
        command.add("-Xbatch");
        command.add("-XX:LoopUnrollLimit=0");
        command.add("-XX:-UseOnStackReplacement");
        command.add("-XX:-TieredCompilation");
        command.add("-XX:+UseZGC");
        command.add("-XX:+ZGenerational");
        command.add("--add-modules=jdk.incubator.vector");
        command.add("-XX:CompileCommand=print," +  Launcher.class.getName() + "::" + test_name);
        command.add(Launcher.class.getName());
        command.add(test_name);

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(command);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        return output.getStdout();
    }

    static void run_test(String test_name, String expected_freg_type, int expected_number_of_fregs,
                         String expected_vector_reg_type, int expected_number_of_vector_regs) throws Exception {
        String stdout = launchJavaTestProcess(test_name);

        String keyword = "push_fp: ";
        checkPushPopRegNumberAndType(stdout, keyword, expected_freg_type, expected_number_of_fregs);
        checkPushPopRegNumberAndType(stdout, keyword, expected_vector_reg_type, expected_number_of_vector_regs);

        keyword = "pop_fp: ";
        checkPushPopRegNumberAndType(stdout, keyword, expected_freg_type, expected_number_of_fregs);
        checkPushPopRegNumberAndType(stdout, keyword, expected_vector_reg_type, expected_number_of_vector_regs);
    }

    static void run_test(String test_name, String expected_freg_type, int expected_number_of_fregs) throws Exception {
        String stdout = launchJavaTestProcess(test_name);

        String keyword = "push_fp: ";
        if (!containOnlyOneOccuranceOfKeyword(stdout, keyword)) {
            throw new RuntimeException("Stdout is expected to contain only one occurance of keyword: " + "'" + keyword + "'");
        }
        checkPushPopRegNumberAndType(stdout, keyword, expected_freg_type, expected_number_of_fregs);

        keyword = "pop_fp: ";
        if (!containOnlyOneOccuranceOfKeyword(stdout, keyword)) {
            throw new RuntimeException("Stdout is expected to contain only one occurance of keyword: " + "'" + keyword + "'");
        }
        checkPushPopRegNumberAndType(stdout, keyword, expected_freg_type, expected_number_of_fregs);
    }

    public static void main(String[] args) throws Exception {
        String vector_max_reg_type;
        if (VectorShape.S_Max_BIT.vectorBitSize() > 128) {
            vector_max_reg_type = "SVE";
        } else {
            vector_max_reg_type = "Neon";
        }
        run_test("test_one_float_push_pop_at_load_barrier", "fp", 1);
        run_test("test_two_floats_push_pop_at_load_barrier", "fp", 2);
        run_test("test_three_floats_push_pop_at_load_barrier", "fp", 3);
        run_test("test_one_double_push_pop_at_load_barrier", "fp", 1);
        run_test("test_two_doubles_push_pop_at_load_barrier", "fp", 2);
        run_test("test_three_doubles_push_pop_at_load_barrier", "fp", 3);
        run_test("test_one_vector_128_push_pop_at_load_barrier", "Neon", 1);
        run_test("test_two_vectors_128_push_pop_at_load_barrier", "Neon", 2);
        run_test("test_three_vectors_128_push_pop_at_load_barrier", "Neon", 3);
        run_test("test_vector_max_push_pop_at_load_barrier", vector_max_reg_type, 1);
        run_test("test_float_and_vector_push_pop_at_load_barrier", "fp", 1, vector_max_reg_type, 1);
    }
}

