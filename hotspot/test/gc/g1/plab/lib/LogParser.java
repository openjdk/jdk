/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package gc.g1.plab.lib;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LogParser class parses VM output to get PLAB and ConsumptionStats values.
 *
 * Typical GC log with PLAB statistics (options - -Xlog:gc=debug,gc+plab=debug) looks like:
 *
 * [0.330s][debug][gc,plab  ] GC(0) Young PLAB allocation: allocated: 1825632B, wasted: 29424B, unused: 2320B, used: 1793888B, undo waste: 0B,
 * [0.330s][debug][gc,plab  ] GC(0) Young other allocation: region end waste: 0B, regions filled: 2, direct allocated: 271520B, failure used: 0B, failure wasted: 0B
 * [0.330s][debug][gc,plab  ] GC(0) Young sizing: calculated: 358776B, actual: 358776B
 * [0.330s][debug][gc,plab  ] GC(0) Old PLAB allocation: allocated: 427248B, wasted: 592B, unused: 368584B, used: 58072B, undo waste: 0B,
 * [0.330s][debug][gc,plab  ] GC(0) Old other allocation: region end waste: 0B, regions filled: 1, direct allocated: 41704B, failure used: 0B, failure wasted: 0B
 * [0.330s][debug][gc,plab  ] GC(0) Old sizing: calculated: 11608B, actual: 11608B
 */
final public class LogParser {

    // Name for GC ID field in report.
    public final static String GC_ID = "gc_id";

    /**
     * Type of parsed log element.
     */
    public static enum ReportType {
        SURVIVOR_STATS,
        OLD_STATS
    }

    private final String log;

    // Contains Map of PLAB statistics for given log.
    private final Map<Long, Map<ReportType, Map<String, Long>>> report;

    // GC ID
    private static final Pattern GC_ID_PATTERN = Pattern.compile("\\[gc,plab\\s*\\] GC\\((\\d+)\\)");
    // Pattern for extraction pair <name>: <numeric value>
    private static final Pattern PAIRS_PATTERN = Pattern.compile("\\w* \\w+:\\s+\\d+");

    /**
     * Construct LogParser object, parse log file with PLAB statistics and store it into report.
     *
     * @param log - VM Output
     */
    public LogParser(String log) {
        if (log == null) {
            throw new IllegalArgumentException("Parameter log should not be null.");
        }
        this.log = log;
        report = parseLines();
    }

    /**
     * @return log which was processed
     */
    public String getLog() {
        return log;
    }

    /**
     * Returns the GC log entries for Survivor and Old stats.
     * The entries are represented as a map of gcID to the StatMap.
     *
     * @return The log entries for the Survivor and Old stats.
     */
    public Map<Long, Map<ReportType, Map<String, Long>>> getEntries() {
        return report;
    }

    private Map<Long, Map<ReportType, Map<String, Long>>> parseLines() throws NumberFormatException {
        Scanner lineScanner = new Scanner(log);
        Map<Long, Map<ReportType, Map<String, Long>>> allocationStatistics = new HashMap<>();
        Optional<Long> gc_id;
        while (lineScanner.hasNextLine()) {
            String line = lineScanner.nextLine();
            gc_id = getGcId(line);
            if (gc_id.isPresent()) {
                Matcher matcher = PAIRS_PATTERN.matcher(line);
                if (matcher.find()) {
                    Map<ReportType, Map<String, Long>> oneReportItem;
                    ReportType reportType;

                    if (!allocationStatistics.containsKey(gc_id.get())) {
                        allocationStatistics.put(gc_id.get(), new EnumMap<>(ReportType.class));
                    }

                    if (line.contains("Young")) {
                        reportType = ReportType.SURVIVOR_STATS;
                    } else {
                        reportType = ReportType.OLD_STATS;
                    }

                    oneReportItem = allocationStatistics.get(gc_id.get());
                    if (!oneReportItem.containsKey(reportType)) {
                        oneReportItem.put(reportType, new HashMap<>());
                    }

                    // Extract all pairs from log.
                    Map<String, Long> plabStats = oneReportItem.get(reportType);
                    do {
                        String pair = matcher.group();
                        String[] nameValue = pair.replaceAll(": ", ":").split(":");
                        plabStats.put(nameValue[0].trim(), Long.parseLong(nameValue[1]));
                    } while (matcher.find());
                }
            }
        }
        return allocationStatistics;
    }

    private Optional<Long> getGcId(String line) {
        Matcher number = GC_ID_PATTERN.matcher(line);
        if (number.find()) {
            return Optional.of(Long.parseLong(number.group(1)));
        }
        return Optional.empty();
    }
}
