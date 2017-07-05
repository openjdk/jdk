/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * input validation, and proper error handling, might not be present in
 * this sample code.
 */

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * WC - Prints newline, word, and character counts for each file. See
 * the {@link #usage} method for instructions and command line parameters. This
 * sample shows usages of:
 * <ul>
 * <li>Lambda and bulk operations. Shows how to create a custom collector to
 * gather custom statistics. Implements the collection of statistics using a
 * built-in API.</li>
 * <li>Constructor reference.</li>
 * <li>Try-with-resources feature.</li>
 * </ul>
 *
 */
public class WC {

    //The number of characters that may be read.
    private static final int READ_AHEAD_LIMIT = 100_000_000;

    //The pattern for splitting strings by non word characters to get words.
    private static final Pattern nonWordPattern = Pattern.compile("\\W");

    /**
     * The main method for the WC program. Run the program with an empty
     * argument list to see possible arguments.
     *
     * @param args the argument list for WC
     * @throws java.io.IOException If an input exception occurred.
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            usage();
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new FileReader(args[0]))) {
            reader.mark(READ_AHEAD_LIMIT);
            /*
             * Statistics can be gathered in four passes using a built-in API.
             * The method demonstrates how separate operations can be
             * implemented using a built-in API.
             */
            collectInFourPasses(reader);
            /*
             * Usage of several passes to collect data is not the best way.
             * Statistics can be gathered by a custom collector in one pass.
             */
            reader.reset();
            collectInOnePass(reader);
        } catch (FileNotFoundException e) {
            usage();
            System.err.println(e);
        }
    }

    private static void collectInFourPasses(BufferedReader reader)
            throws IOException {
        /*
         * Input is read as a stream of lines by lines().
         * Every line is turned into a stream of chars by the flatMapToInt(...)
         * method.
         * Length of the stream is counted by count().
         */
        System.out.println("Character count = "
                + reader.lines().flatMapToInt(String::chars).count());
        /*
         * Input is read as a stream of lines by lines().
         * Every line is split by nonWordPattern into words by flatMap(...)
         * method.
         * Empty lines are removed by the filter(...) method.
         * Length of the stream is counted by count().
         */
        reader.reset();
        System.out.println("Word count = "
                + reader.lines()
                .flatMap(nonWordPattern::splitAsStream)
                .filter(str -> !str.isEmpty()).count());

        reader.reset();
        System.out.println("Newline count = " + reader.lines().count());
        /*
         * Input is read as a stream of lines by lines().
         * Every line is mapped to its length.
         * Maximum of the lengths is calculated.
         */
        reader.reset();
        System.out.println("Max line length = "
                + reader.lines().mapToInt(String::length).max().getAsInt());
    }

    private static void collectInOnePass(BufferedReader reader) {
        /*
         * The collect() method has three parameters:
         * The first parameter is the {@code WCStatistic} constructor reference.
         * collect() will create {@code WCStatistics} instances, where
         * statistics will be aggregated.
         * The second parameter shows how {@code WCStatistics} will process
         * String.
         * The third parameter shows how to merge two {@code WCStatistic}
         * instances.
         *
         * Also {@code Collector} can be used, which would be more reusable
         * solution. See {@code CSVProcessor} example for how {@code Collector}
         * can be implemented.
         *
         * Note that the any performance increase when going parallel will
         * depend on the size of the input (lines) and the cost per-element.
         */
        WCStatistics wc = reader.lines().parallel()
                .collect(WCStatistics::new,
                        WCStatistics::accept,
                        WCStatistics::combine);
        System.out.println(wc);
    }

    private static void usage() {
        System.out.println("Usage: " + WC.class.getSimpleName() + " FILE");
        System.out.println("Print newline, word,"
                + "  character counts and max line length for FILE.");
    }

    private static class WCStatistics implements Consumer<String> {
        /*
         * @implNote This implementation does not need to be thread safe because
         * the parallel implementation of
         * {@link java.util.stream.Stream#collect Stream.collect()}
         * provides the necessary partitioning and isolation for safe parallel
         * execution.
         */

        private long characterCount;
        private long lineCount;
        private long wordCount;
        private long maxLineLength;


        /*
         * Processes line.
         */
        @Override
        public void accept(String line) {
            characterCount += line.length();
            lineCount++;
            wordCount += nonWordPattern.splitAsStream(line)
                    .filter(str -> !str.isEmpty()).count();
            maxLineLength = Math.max(maxLineLength, line.length());
        }

        /*
         * Merges two WCStatistics.
         */
        public void combine(WCStatistics stat) {
            wordCount += stat.wordCount;
            lineCount += stat.lineCount;
            characterCount += stat.characterCount;
            maxLineLength = Math.max(maxLineLength, stat.maxLineLength);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("#------WCStatistic------#\n");
            sb.append("Character count = ").append(characterCount).append('\n');
            sb.append("Word count = ").append(wordCount).append('\n');
            sb.append("Newline count = ").append(lineCount).append('\n');
            sb.append("Max line length = ").append(maxLineLength).append('\n');
            return sb.toString();
        }
    }
}
