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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.lang.Double.parseDouble;
import static java.util.stream.Collectors.*;

/**
 * CSVProcessor is a tool for processing CSV files. There are several
 * command-line options. Consult the {@link #printUsageAndExit} method for
 * instructions and command line parameters. This sample shows examples of the
 * following features:
 * <ul>
 * <li>Lambda and bulk operations. Working with streams: map(...), filter(...),
 * sorted(...) methods. The collect(...) method with different collectors:
 * Collectors.maxBy(...), Collectors.minBy(...), Collectors.toList(),
 * Collectors.toCollection(...), Collectors.groupingBy(...),
 * Collectors.toDoubleSummaryStatistics(...), and a custom Collector.</li>
 * <li>Static method reference for printing values.</li>
 * <li>Try-with-resources feature for closing files.</li>
 * <li>Switch by String feature.</li>
 * <li>Other new APIs: Pattern.asPredicate(), BinaryOperator
 * BufferedReader.lines(), Collection.forEach(...), Comparator.comparing(...),
 * Comparator.reversed(), Arrays.stream(...).</li>
 * </ul>
 *
 */
public class CSVProcessor {

    //Number of characters that may be read
    private static final int READ_AHEAD_LIMIT = 100_000_000;

    /**
     * The main method for the CSVProcessor program. Run the program with an
     * empty argument list to see possible arguments.
     *
     * @param args the argument list for CSVProcessor.
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsageAndExit();
        }
        try (BufferedReader br = new BufferedReader(
                Files.newBufferedReader(Paths.get(args[args.length - 1])))) {
            //Assume that the first line contains column names.
            List<String> header = Arrays.stream(br.readLine().split(","))
                    .map(String::trim).collect(toList());
            //Calculate an index of the column in question.
            int column = getColumnNumber(header, args[1]);
            switch (args[0]) {
                case "sort":
                    verifyArgumentNumber(args, 4);
                    //Define the sort order.
                    boolean isAsc;
                    switch (args[2].toUpperCase()) {
                        case "ASC":
                            isAsc = true;
                            break;
                        case "DESC":
                            isAsc = false;
                            break;
                        default:
                            printUsageAndExit("Illegal argument" + args[2]);
                            return;//Should not be reached.
                    }
                    /*
                     * Create a comparator that compares lines by comparing
                     * values in the specified column.
                     */
                    Comparator<String> cmp
                            = Comparator.comparing(str -> getCell(str, column),
                                    String.CASE_INSENSITIVE_ORDER);
                    /*
                     * sorted(...) is used to sort records.
                     * forEach(...) is used to output sorted records.
                     */
                    br.lines().sorted(isAsc ? cmp : cmp.reversed())
                            .forEach(System.out::println);
                    break;
                case "search":
                    verifyArgumentNumber(args, 4);
                    /*
                     * Records are filtered by a regex.
                     * forEach(...) is used to output filtered records.
                     */
                    Predicate<String> pattern
                            = Pattern.compile(args[2]).asPredicate();
                    br.lines().filter(str -> pattern.test(getCell(str, column)))
                            .forEach(System.out::println);
                    break;
                case "groupby":
                    verifyArgumentNumber(args, 3);
                    /*
                     * Group lines by values in the column with collect(...), and
                     * print with forEach(...) for every distinct value within
                     * the column.
                     */
                    br.lines().collect(
                            Collectors.groupingBy(str -> getCell(str, column),
                                    toCollection(TreeSet::new)))
                            .forEach((str, set) -> {
                                System.out.println(str + ":");
                                set.forEach(System.out::println);
                            });
                    break;
                case "stat":
                    verifyArgumentNumber(args, 3);

                    /*
                     * BufferedReader will be read several times.
                     * Mark this point to return here after each pass.
                     * BufferedReader will be read right after the headers line
                     * because it is already read.
                     */
                    br.mark(READ_AHEAD_LIMIT);

                    /*
                     * Statistics can be collected by a custom collector in one
                     * pass. One pass is preferable.
                     */
                    System.out.println(
                            br.lines().collect(new Statistics(column)));

                    /*
                     * Alternatively, statistics can be collected
                     * by a built-in API in several passes.
                     * This method demonstrates how separate operations can be
                     * implemented using a built-in API.
                     */
                    br.reset();
                    statInSeveralPasses(br, column);
                    break;
                default:
                    printUsageAndExit("Illegal argument" + args[0]);
            }
        } catch (IOException e) {
            printUsageAndExit(e.toString());
        }
    }

    private static void statInSeveralPasses(BufferedReader br, int column)
            throws IOException {
        System.out.println("#-----Statistics in several passes-------#");
        //Create a comparator to compare records by the column.
        Comparator<String> comparator
                = Comparator.comparing(
                        (String str) -> parseDouble(getCell(str, column)));
        //Find max record by using Collectors.maxBy(...)
        System.out.println(
                "Max: " + br.lines().collect(maxBy(comparator)).get());
        br.reset();
        //Find min record by using Collectors.minBy(...)
        System.out.println(
                "Min: " + br.lines().collect(minBy(comparator)).get());
        br.reset();
        //Compute the average value and sum with
        //Collectors.toDoubleSummaryStatistics(...)
        DoubleSummaryStatistics doubleSummaryStatistics
                = br.lines().collect(summarizingDouble(
                    str -> parseDouble(getCell(str, column))));
        System.out.println("Average: " + doubleSummaryStatistics.getAverage());
        System.out.println("Sum: " + doubleSummaryStatistics.getSum());
    }

    private static void verifyArgumentNumber(String[] args, int n) {
        if (args.length != n) {
            printUsageAndExit("Expected " + n + " arguments but was "
                    + args.length);
        }
    }

    private static int getColumnNumber(List<String> header, String name) {
        int column = header.indexOf(name);
        if (column == -1) {
            printUsageAndExit("There is no column with name " + name);
        }
        return column;
    }

    private static String getCell(String record, int column) {
        return record.split(",")[column].trim();
    }

    private static void printUsageAndExit(String... str) {
        System.out.println("Usages:");

        System.out.println("CSVProcessor sort COLUMN_NAME ASC|DESC FILE");
        System.out.println("Sort lines by column COLUMN_NAME in CSV FILE\n");

        System.out.println("CSVProcessor search COLUMN_NAME REGEX FILE");
        System.out.println("Search for REGEX in column COLUMN_NAME in CSV FILE\n");

        System.out.println("CSVProcessor groupby COLUMN_NAME FILE");
        System.out.println("Split lines into different groups according to column "
                + "COLUMN_NAME value\n");

        System.out.println("CSVProcessor stat COLUMN_NAME FILE");
        System.out.println("Compute max/min/average/sum  statistics by column "
                + "COLUMN_NAME\n");

        Arrays.asList(str).forEach(System.err::println);
        System.exit(1);
    }

    /*
     * This is a custom implementation of the Collector interface.
     * Statistics are objects gather max,min,sum,average statistics.
     */
    private static class Statistics
            implements Collector<String, Statistics, Statistics> {


        /*
         * This implementation does not need to be thread safe because
         * the parallel implementation of
         * {@link java.util.stream.Stream#collect Stream.collect()}
         * provides the necessary partitioning and isolation for safe parallel
         * execution.
         */
        private String maxRecord;
        private String minRecord;

        private double sum;
        private int lineCount;
        private final BinaryOperator<String> maxOperator;
        private final BinaryOperator<String> minOperator;
        private final int column;

        public Statistics(int column) {
            this.column = column;
            Comparator<String> cmp = Comparator.comparing(
                    (String str) -> parseDouble(getCell(str, column)));
            maxOperator = BinaryOperator.maxBy(cmp);
            minOperator = BinaryOperator.minBy(cmp);
        }

        /*
         * Process line.
         */
        public Statistics accept(String line) {
            maxRecord = maxRecord == null
                    ? line : maxOperator.apply(maxRecord, line);
            minRecord = minRecord == null
                    ? line : minOperator.apply(minRecord, line);

            sum += parseDouble(getCell(line, column));
            lineCount++;
            return this;
        }


        /*
         * Merge two Statistics.
         */
        public Statistics combine(Statistics stat) {
            maxRecord = maxOperator.apply(maxRecord, stat.getMaxRecord());
            minRecord = minOperator.apply(minRecord, stat.getMinRecord());
            sum += stat.getSum();
            lineCount += stat.getLineCount();
            return this;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("#------Statistics------#\n");
            sb.append("Max: ").append(getMaxRecord()).append("\n");
            sb.append("Min: ").append(getMinRecord()).append("\n");
            sb.append("Sum = ").append(getSum()).append("\n");
            sb.append("Average = ").append(average()).append("\n");
            sb.append("#------Statistics------#\n");
            return sb.toString();
        }

        @Override
        public Supplier<Statistics> supplier() {
            return () -> new Statistics(column);
        }

        @Override
        public BiConsumer<Statistics, String> accumulator() {
            return Statistics::accept;
        }

        @Override
        public BinaryOperator<Statistics> combiner() {
            return Statistics::combine;

        }

        @Override
        public Function<Statistics, Statistics> finisher() {
            return stat -> stat;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return EnumSet.of(Characteristics.IDENTITY_FINISH);
        }

        private String getMaxRecord() {
            return maxRecord;
        }

        private String getMinRecord() {
            return minRecord;
        }

        private double getSum() {
            return sum;
        }

        private double average() {
            return sum / lineCount;
        }

        private int getLineCount() {
            return lineCount;
        }

    }

}
