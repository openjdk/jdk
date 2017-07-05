/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


import java.util.Arrays;
import java.util.Random;

import static java.lang.Integer.parseInt;

/**
 * MergeExample is a class that runs a demo benchmark of the {@code ForkJoin} framework
 * by benchmarking a {@link MergeSort} algorithm that is implemented using
 * {@link java.util.concurrent.RecursiveAction}.
 * The {@code ForkJoin} framework is setup with different parallelism levels
 * and the sort is executed with arrays of different sizes to see the
 * trade offs by using multiple threads for different sizes of the array.
 */
public class MergeDemo {
    // Use a fixed seed to always get the same random values back
    private final Random random = new Random(759123751834L);
    private static final int ITERATIONS = 10;

    /**
     * Represents the formula {@code f(n) = start + (step * n)} for n = 0 & n < iterations
     */
    private static class Range {
        private final int start;
        private final int step;
        private final int iterations;

        private Range(int start, int step, int iterations) {
            this.start = start;
            this.step = step;
            this.iterations = iterations;
        }

        /**
         * Parses start, step and iterations from args
         * @param args the string array containing the arguments
         * @param start which element to start the start argument from
         * @return the constructed range
         */
        public static Range parse(String[] args, int start) {
            if (args.length < start + 3) {
                throw new IllegalArgumentException("Too few elements in array");
            }
            return new Range(parseInt(args[start]), parseInt(args[start + 1]), parseInt(args[start + 2]));
        }

        public int get(int iteration) {
            return start + (step * iteration);
        }

        public int getIterations() {
            return iterations;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(start).append(" ").append(step).append(" ").append(iterations);
            return builder.toString();
        }
    }

    /**
     * Wraps the different parameters that is used when running the MergeExample.
     * {@code sizes} represents the different array sizes
     * {@code parallelism} represents the different parallelism levels
     */
    private static class Configuration {
        private final Range sizes;
        private final Range parallelism;

        private final static Configuration defaultConfig = new Configuration(new Range(20000, 20000, 10),
                new Range(2, 2, 10));

        private Configuration(Range sizes, Range parallelism) {
            this.sizes = sizes;
            this.parallelism = parallelism;
        }

        /**
         * Parses the arguments and attempts to create a configuration containing the
         * parameters for creating the array sizes and parallelism sizes
         * @param args the input arguments
         * @return the configuration
         */
        public static Configuration parse(String[] args) {
            if (args.length == 0) {
                return defaultConfig;
            } else {
                try {
                    if (args.length == 6) {
                        return new Configuration(Range.parse(args, 0), Range.parse(args, 3));
                    }
                } catch (NumberFormatException e) {
                    System.err.println("MergeExample: error: Argument was not a number.");
                }
                System.err.println("MergeExample <size start> <size step> <size steps> <parallel start> <parallel step>" +
                        " <parallel steps>");
                System.err.println("example: MergeExample 20000 10000 3 1 1 4");
                System.err.println("example: will run with arrays of sizes 20000, 30000, 40000" +
                        " and parallelism: 1, 2, 3, 4");
                return null;
            }
        }

        /**
         * Creates an array for reporting the test result time in
         * @return an array containing {@code sizes.iterations * parallelism.iterations} elements
         */
        private long[][] createTimesArray() {
            return new long[sizes.getIterations()][parallelism.getIterations()];
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("");
            if (this == defaultConfig) {
                builder.append("Default configuration. ");
            }
            builder.append("Running with parameters: ");
            builder.append(sizes);
            builder.append(" ");
            builder.append(parallelism);
            return builder.toString();
        }
    }

    /**
     * Generates an array of {@code elements} random elements
     * @param elements the number of elements requested in the array
     * @return an array of {@code elements} random elements
     */
    private int[] generateArray(int elements) {
        int[] array = new int[elements];
        for (int i = 0; i < elements; ++i) {
            array[i] = random.nextInt();
        }
        return array;
    }

    /**
     * Runs the test
     * @param config contains the settings for the test
     */
    private void run(Configuration config) {
        Range sizes = config.sizes;
        Range parallelism = config.parallelism;

        // Run a couple of sorts to make the JIT compile / optimize the code
        // which should produce somewhat more fair times
        warmup();

        long[][] times = config.createTimesArray();

        for (int size = 0; size < sizes.getIterations(); size++) {
            runForSize(parallelism, sizes.get(size), times, size);
        }

        printResults(sizes, parallelism, times);
    }

    /**
     * Prints the results as a table
     * @param sizes the different sizes of the arrays
     * @param parallelism the different parallelism levels used
     * @param times the median times for the different sizes / parallelism
     */
    private void printResults(Range sizes, Range parallelism, long[][] times) {
        System.out.println("Time in milliseconds. Y-axis: number of elements. X-axis parallelism used.");
        long[] sums = new long[times[0].length];
        System.out.format("%8s  ", "");
        for (int i = 0; i < times[0].length; i++) {
            System.out.format("%4d ", parallelism.get(i));
        }
        System.out.println("");
        for (int size = 0; size < sizes.getIterations(); size++) {
            System.out.format("%8d: ", sizes.get(size));
            for (int i = 0; i < times[size].length; i++) {
                sums[i] += times[size][i];
                System.out.format("%4d ", times[size][i]);
            }
            System.out.println("");
        }
        System.out.format("%8s: ", "Total");
        for (long sum : sums) {
            System.out.format("%4d ", sum);
        }
        System.out.println("");
    }

    private void runForSize(Range parallelism, int elements, long[][] times, int size) {
        for (int step = 0; step < parallelism.getIterations(); step++) {
            long time = runForParallelism(ITERATIONS, elements, parallelism.get(step));
            times[size][step] = time;
        }
    }

    /**
     * Runs <i>iterations</i> number of test sorts of a random array of <i>element</i> length
     * @param iterations number of iterations
     * @param elements number of elements in the random array
     * @param parallelism parallelism for the ForkJoin framework
     * @return the median time of runs
     */
    private long runForParallelism(int iterations, int elements, int parallelism) {
        MergeSort mergeSort = new MergeSort(parallelism);
        long[] times = new long[iterations];

        for (int i = 0; i < iterations; i++) {
            // Suggest the VM to run a garbage collection to reduce the risk of getting one
            // while running the test run
            System.gc();
            long start = System.currentTimeMillis();
            mergeSort.sort(generateArray(elements));
            times[i] = System.currentTimeMillis() - start;
        }

        return medianValue(times);
    }

    /**
     * Calculates the median value of the array
     * @param times array of times
     * @return the median value
     */
    private long medianValue(long[] times) {
        if (times.length == 0) {
            throw new IllegalArgumentException("Empty array");
        }
        // Make a copy of times to avoid having side effects on the parameter value
        Arrays.sort(times.clone());
        long median = times[times.length / 2];
        if (times.length > 1 && times.length % 2 != 0) {
            median = (median + times[times.length / 2 + 1]) / 2;
        }
        return median;
    }

    /**
     * Generates 1000 arrays of 1000 elements and sorts them as a warmup
     */
    private void warmup() {
        MergeSort mergeSort = new MergeSort(Runtime.getRuntime().availableProcessors());
        for (int i = 0; i < 1000; i++) {
            mergeSort.sort(generateArray(1000));
        }
    }

    public static void main(String[] args) {
        Configuration configuration = Configuration.parse(args);
        if (configuration == null) {
            System.exit(1);
        }
        System.out.println(configuration);
        new MergeDemo().run(configuration);
    }
}
