/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug.internal;

import static org.graalvm.compiler.debug.GraalDebugConfig.Options.DebugValueFile;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.DebugValueHumanReadable;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.DebugValueSummary;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.DebugValueThreadFilter;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.SuppressZeroDebugValues;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.compiler.debug.CSVUtil;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.LogStream;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.debug.internal.method.MethodMetricsImpl;
import org.graalvm.compiler.debug.internal.method.MethodMetricsPrinter;

/**
 * Facility for printing the {@linkplain KeyRegistry#getDebugValues() values} collected across all
 * {@link DebugValueMap#getTopLevelMaps() threads}.
 */
public class DebugValuesPrinter {
    private static final String COMPUTER_READABLE_FMT = CSVUtil.buildFormatString("%s", "%s", "%s", "%s");
    private static final char SCOPE_DELIMITER = '.';
    private final MethodMetricsPrinter mmPrinter;

    public DebugValuesPrinter() {
        this(null);
    }

    public DebugValuesPrinter(MethodMetricsPrinter mmPrinter) {
        this.mmPrinter = mmPrinter;
    }

    public void printDebugValues() throws GraalError {
        TTY.println();
        TTY.println("<DebugValues>");
        List<DebugValueMap> topLevelMaps = DebugValueMap.getTopLevelMaps();
        List<DebugValue> debugValues = KeyRegistry.getDebugValues();
        if (debugValues.size() > 0) {
            try {
                ArrayList<DebugValue> sortedValues = new ArrayList<>(debugValues);
                Collections.sort(sortedValues);

                String summary = DebugValueSummary.getValue();
                if (summary == null) {
                    summary = "Complete";
                }
                if (DebugValueThreadFilter.getValue() != null && topLevelMaps.size() != 0) {
                    topLevelMaps = topLevelMaps.stream().filter(map -> Pattern.compile(DebugValueThreadFilter.getValue()).matcher(map.getName()).find()).collect(Collectors.toList());
                    if (topLevelMaps.size() == 0) {
                        TTY.println("Warning: DebugValueThreadFilter=%s eliminated all maps so nothing will be printed", DebugValueThreadFilter.getValue());
                    }
                }
                switch (summary) {
                    case "Name": {
                        LogStream log = getLogStream();
                        printSummary(log, topLevelMaps, sortedValues);
                        break;
                    }
                    case "Partial": {
                        DebugValueMap globalMap = new DebugValueMap("Global");
                        for (DebugValueMap map : topLevelMaps) {
                            flattenChildren(map, globalMap);
                        }
                        globalMap.normalize();
                        LogStream log = getLogStream();
                        printMap(log, new DebugValueScope(null, globalMap), sortedValues);
                        break;
                    }
                    case "Complete": {
                        DebugValueMap globalMap = new DebugValueMap("Global");
                        for (DebugValueMap map : topLevelMaps) {
                            globalMap.addChild(map);
                        }
                        globalMap.group();
                        globalMap.normalize();
                        LogStream log = getLogStream();
                        printMap(log, new DebugValueScope(null, globalMap), sortedValues);
                        break;
                    }
                    case "Thread":
                        for (DebugValueMap map : topLevelMaps) {
                            TTY.println("Showing the results for thread: " + map.getName());
                            map.group();
                            map.normalize();
                            LogStream log = getLogStream(map.getName().replace(' ', '_'));
                            printMap(log, new DebugValueScope(null, map), sortedValues);
                        }
                        break;
                    default:
                        throw new GraalError("Unknown summary type: %s", summary);
                }
                for (DebugValueMap topLevelMap : topLevelMaps) {
                    topLevelMap.reset();
                }
            } catch (Throwable e) {
                // Don't want this to change the exit status of the VM
                PrintStream err = System.err;
                err.println("Error while printing debug values:");
                e.printStackTrace();
            }
        }
        if (mmPrinter != null) {
            mmPrinter.printMethodMetrics(MethodMetricsImpl.collectedMetrics());
        }
        TTY.println("</DebugValues>");
    }

    private static LogStream getLogStream() {
        return getLogStream(null);
    }

    private static LogStream getLogStream(String prefix) {
        String debugValueFile = DebugValueFile.getValue();
        if (debugValueFile != null) {
            try {
                final String fileName;
                if (prefix != null) {
                    fileName = prefix + '-' + debugValueFile;
                } else {
                    fileName = debugValueFile;
                }
                LogStream logStream = new LogStream(new FileOutputStream(fileName));
                TTY.println("Writing debug values to '%s'", fileName);
                return logStream;
            } catch (FileNotFoundException e) {
                TTY.println("Warning: Could not open debug value log file: %s (defaulting to TTY)", e.getMessage());
            }
        }
        return TTY.out();
    }

    private void flattenChildren(DebugValueMap map, DebugValueMap globalMap) {
        globalMap.addChild(map);
        for (DebugValueMap child : map.getChildren()) {
            flattenChildren(child, globalMap);
        }
        map.clearChildren();
    }

    private void printSummary(LogStream log, List<DebugValueMap> topLevelMaps, List<DebugValue> debugValues) {
        DebugValueMap result = new DebugValueMap("Summary");
        for (int i = debugValues.size() - 1; i >= 0; i--) {
            DebugValue debugValue = debugValues.get(i);
            int index = debugValue.getIndex();
            long total = collectTotal(topLevelMaps, index);
            result.setCurrentValue(index, total);
        }
        printMap(log, new DebugValueScope(null, result), debugValues);
    }

    private long collectTotal(List<DebugValueMap> maps, int index) {
        long total = 0;
        for (int i = 0; i < maps.size(); i++) {
            DebugValueMap map = maps.get(i);
            total += map.getCurrentValue(index);
            total += collectTotal(map.getChildren(), index);
        }
        return total;
    }

    /**
     * Tracks the scope when printing a {@link DebugValueMap}, allowing "empty" scopes to be
     * omitted. An empty scope is one in which there are no (nested) non-zero debug values.
     */
    static class DebugValueScope {

        final DebugValueScope parent;
        final int level;
        final DebugValueMap map;
        private boolean printed;

        DebugValueScope(DebugValueScope parent, DebugValueMap map) {
            this.parent = parent;
            this.map = map;
            this.level = parent == null ? 0 : parent.level + 1;
        }

        public void print(LogStream log) {
            if (!printed) {
                printed = true;
                if (parent != null) {
                    parent.print(log);
                }
                printIndent(log, level);
                log.printf("%s%n", map.getName());
            }
        }

        public String toRawString() {
            return toRaw(new StringBuilder()).toString();
        }

        private StringBuilder toRaw(StringBuilder stringBuilder) {
            final StringBuilder sb = (parent == null) ? stringBuilder : parent.toRaw(stringBuilder).append(SCOPE_DELIMITER);
            return sb.append(map.getName());
        }

    }

    private void printMap(LogStream log, DebugValueScope scope, List<DebugValue> debugValues) {
        if (DebugValueHumanReadable.getValue()) {
            printMapHumanReadable(log, scope, debugValues);
        } else {
            printMapComputerReadable(log, scope, debugValues);
        }
    }

    private void printMapComputerReadable(LogStream log, DebugValueScope scope, List<DebugValue> debugValues) {

        for (DebugValue value : debugValues) {
            long l = scope.map.getCurrentValue(value.getIndex());
            if (l != 0 || !SuppressZeroDebugValues.getValue()) {
                CSVUtil.Escape.println(log, COMPUTER_READABLE_FMT, scope.toRawString(), value.getName(), value.toRawString(l), value.rawUnit());
            }
        }

        List<DebugValueMap> children = scope.map.getChildren();
        for (int i = 0; i < children.size(); i++) {
            DebugValueMap child = children.get(i);
            printMapComputerReadable(log, new DebugValueScope(scope, child), debugValues);
        }
    }

    private void printMapHumanReadable(LogStream log, DebugValueScope scope, List<DebugValue> debugValues) {

        for (DebugValue value : debugValues) {
            long l = scope.map.getCurrentValue(value.getIndex());
            if (l != 0 || !SuppressZeroDebugValues.getValue()) {
                scope.print(log);
                printIndent(log, scope.level + 1);
                log.println(value.getName() + "=" + value.toString(l));
            }
        }

        List<DebugValueMap> children = scope.map.getChildren();
        for (int i = 0; i < children.size(); i++) {
            DebugValueMap child = children.get(i);
            printMapHumanReadable(log, new DebugValueScope(scope, child), debugValues);
        }
    }

    private static void printIndent(LogStream log, int level) {
        for (int i = 0; i < level; ++i) {
            log.print("    ");
        }
        log.print("|-> ");
    }

    public void clearDebugValues() {
        List<DebugValueMap> topLevelMaps = DebugValueMap.getTopLevelMaps();
        List<DebugValue> debugValues = KeyRegistry.getDebugValues();
        if (debugValues.size() > 0) {
            for (DebugValueMap map : topLevelMaps) {
                map.reset();
            }
        }
        if (mmPrinter != null) {
            MethodMetricsImpl.clearMM();
        }
    }
}
