/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Copyright (c) 2009-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package build.tools.tzdb;

import static build.tools.tzdb.Utils.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * A compiler that reads a set of TZDB time-zone files and builds a single
 * combined TZDB data file.
 *
 * @since 1.8
 */
public final class TzdbZoneRulesCompiler {

    public static void main(String[] args) {
        new TzdbZoneRulesCompiler().compile(args);
    }

    private void compile(String[] args) {
        if (args.length < 2) {
            outputHelp();
            return;
        }
        Path srcDir = null;
        Path dstFile = null;
        String version = null;
        // parse args/options
        int i;
        for (i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("-")) {
                break;
            }
            if ("-srcdir".equals(arg)) {
                if (srcDir == null && ++i < args.length) {
                    srcDir = Paths.get(args[i]);
                    continue;
                }
            } else if ("-dstfile".equals(arg)) {
                if (dstFile == null && ++i < args.length) {
                    dstFile = Paths.get(args[i]);
                    continue;
                }
            } else if ("-verbose".equals(arg)) {
                if (!verbose) {
                    verbose = true;
                    continue;
                }
            } else if (!"-help".equals(arg)) {
                System.out.println("Unrecognised option: " + arg);
            }
            outputHelp();
            return;
        }
        // check source directory
        if (srcDir == null) {
            System.err.println("Source directory must be specified using -srcdir");
            System.exit(1);
        }
        if (!Files.isDirectory(srcDir)) {
            System.err.println("Source does not exist or is not a directory: " + srcDir);
            System.exit(1);
        }
        // parse source file names
        if (i == args.length) {
            i = 0;
            args = new String[] {"africa", "antarctica", "asia", "australasia", "europe",
                                 "northamerica","southamerica", "backward", "etcetera" };
            System.out.println("Source filenames not specified, using default set ( ");
            for (String name : args) {
                System.out.printf(name + " ");
            }
            System.out.println(")");
        }
        // source files in this directory
        List<Path> srcFiles = new ArrayList<>();
        for (; i < args.length; i++) {
            Path file = srcDir.resolve(args[i]);
            if (Files.exists(file)) {
                srcFiles.add(file);
            } else {
                System.err.println("Source directory does not contain source file: " + args[i]);
                System.exit(1);
            }
        }
        // check destination file
        if (dstFile == null) {
            dstFile = srcDir.resolve("tzdb.dat");
        } else {
            Path parent = dstFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                System.err.println("Destination directory does not exist: " + parent);
                System.exit(1);
            }
        }
        try {
            // get tzdb source version
            Matcher m = Pattern.compile("tzdata(?<ver>[0-9]{4}[A-z])")
                               .matcher(new String(Files.readAllBytes(srcDir.resolve("VERSION")),
                                                   "ISO-8859-1"));
            if (m.find()) {
                version = m.group("ver");
            } else {
                System.exit(1);
                System.err.println("Source directory does not contain file: VERSION");
            }
            printVerbose("Compiling TZDB version " + version);
            // parse source files
            for (Path file : srcFiles) {
                printVerbose("Parsing file: " + file);
                parseFile(file);
            }
            // build zone rules
            printVerbose("Building rules");
            buildZoneRules();
            // output to file
            printVerbose("Outputting tzdb file: " + dstFile);
            outputFile(dstFile, version, builtZones, links);
        } catch (Exception ex) {
            System.out.println("Failed: " + ex.toString());
            ex.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    /**
     * Output usage text for the command line.
     */
    private static void outputHelp() {
        System.out.println("Usage: TzdbZoneRulesCompiler <options> <tzdb source filenames>");
        System.out.println("where options include:");
        System.out.println("   -srcdir  <directory>  Where to find tzdb source directory (required)");
        System.out.println("   -dstfile <file>       Where to output generated file (default srcdir/tzdb.dat)");
        System.out.println("   -help                 Print this usage message");
        System.out.println("   -verbose              Output verbose information during compilation");
        System.out.println(" The source directory must contain the unpacked tzdb files, such as asia or europe");
    }

    /**
     * Outputs the file.
     */
    private void outputFile(Path dstFile, String version,
                            SortedMap<String, ZoneRules> builtZones,
                            Map<String, String> links) {
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(dstFile))) {
            // file version
            out.writeByte(1);
            // group
            out.writeUTF("TZDB");
            // versions
            out.writeShort(1);
            out.writeUTF(version);
            // regions
            String[] regionArray = builtZones.keySet().toArray(new String[builtZones.size()]);
            out.writeShort(regionArray.length);
            for (String regionId : regionArray) {
                out.writeUTF(regionId);
            }
            // rules  -- hashset -> remove the dup
            List<ZoneRules> rulesList = new ArrayList<>(new HashSet<>(builtZones.values()));
            out.writeShort(rulesList.size());
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            for (ZoneRules rules : rulesList) {
                baos.reset();
                DataOutputStream dataos = new DataOutputStream(baos);
                rules.writeExternal(dataos);
                dataos.close();
                byte[] bytes = baos.toByteArray();
                out.writeShort(bytes.length);
                out.write(bytes);
            }
            // link version-region-rules
            out.writeShort(builtZones.size());
            for (Map.Entry<String, ZoneRules> entry : builtZones.entrySet()) {
                 int regionIndex = Arrays.binarySearch(regionArray, entry.getKey());
                 int rulesIndex = rulesList.indexOf(entry.getValue());
                 out.writeShort(regionIndex);
                 out.writeShort(rulesIndex);
            }
            // alias-region
            out.writeShort(links.size());
            for (Map.Entry<String, String> entry : links.entrySet()) {
                 int aliasIndex = Arrays.binarySearch(regionArray, entry.getKey());
                 int regionIndex = Arrays.binarySearch(regionArray, entry.getValue());
                 out.writeShort(aliasIndex);
                 out.writeShort(regionIndex);
            }
            out.flush();
        } catch (Exception ex) {
            System.out.println("Failed: " + ex.toString());
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static final Pattern YEAR = Pattern.compile("(?i)(?<min>min)|(?<max>max)|(?<only>only)|(?<year>[0-9]+)");
    private static final Pattern MONTH = Pattern.compile("(?i)(jan)|(feb)|(mar)|(apr)|(may)|(jun)|(jul)|(aug)|(sep)|(oct)|(nov)|(dec)");
    private static final Matcher DOW = Pattern.compile("(?i)(mon)|(tue)|(wed)|(thu)|(fri)|(sat)|(sun)").matcher("");
    private static final Matcher TIME = Pattern.compile("(?<neg>-)?+(?<hour>[0-9]{1,2})(:(?<minute>[0-5][0-9]))?+(:(?<second>[0-5][0-9]))?+").matcher("");

    /** The TZDB rules. */
    private final Map<String, List<TZDBRule>> rules = new HashMap<>();

    /** The TZDB zones. */
    private final Map<String, List<TZDBZone>> zones = new HashMap<>();

    /** The TZDB links. */
    private final Map<String, String> links = new HashMap<>();

    /** The built zones. */
    private final SortedMap<String, ZoneRules> builtZones = new TreeMap<>();

    /** Whether to output verbose messages. */
    private boolean verbose;

    /**
     * private contructor
     */
    private TzdbZoneRulesCompiler() {
    }

    /**
     * Parses a source file.
     *
     * @param file  the file being read, not null
     * @throws Exception if an error occurs
     */
    private void parseFile(Path file) throws Exception {
        int lineNumber = 1;
        String line = null;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
            List<TZDBZone> openZone = null;
            for (; lineNumber < lines.size(); lineNumber++) {
                line = lines.get(lineNumber);
                int index = line.indexOf('#');  // remove comments (doesn't handle # in quotes)
                if (index >= 0) {
                    line = line.substring(0, index);
                }
                if (line.trim().length() == 0) {  // ignore blank lines
                    continue;
                }
                Scanner s = new Scanner(line);
                if (openZone != null && Character.isWhitespace(line.charAt(0)) && s.hasNext()) {
                    if (parseZoneLine(s, openZone)) {
                        openZone = null;
                    }
                } else {
                    if (s.hasNext()) {
                        String first = s.next();
                        if (first.equals("Zone")) {
                            openZone = new ArrayList<>();
                            try {
                                zones.put(s.next(), openZone);
                                if (parseZoneLine(s, openZone)) {
                                    openZone = null;
                                }
                            } catch (NoSuchElementException x) {
                                printVerbose("Invalid Zone line in file: " + file + ", line: " + line);
                                throw new IllegalArgumentException("Invalid Zone line");
                            }
                        } else {
                            openZone = null;
                            if (first.equals("Rule")) {
                                try {
                                    parseRuleLine(s);
                                } catch (NoSuchElementException x) {
                                    printVerbose("Invalid Rule line in file: " + file + ", line: " + line);
                                    throw new IllegalArgumentException("Invalid Rule line");
                                }
                            } else if (first.equals("Link")) {
                                try {
                                    String realId = s.next();
                                    String aliasId = s.next();
                                    links.put(aliasId, realId);
                                } catch (NoSuchElementException x) {
                                    printVerbose("Invalid Link line in file: " + file + ", line: " + line);
                                    throw new IllegalArgumentException("Invalid Link line");
                                }

                            } else {
                                throw new IllegalArgumentException("Unknown line");
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new Exception("Failed while parsing file '" + file + "' on line " + lineNumber + " '" + line + "'", ex);
        }
    }

    /**
     * Parses a Rule line.
     *
     * @param s  the line scanner, not null
     */
    private void parseRuleLine(Scanner s) {
        TZDBRule rule = new TZDBRule();
        String name = s.next();
        if (rules.containsKey(name) == false) {
            rules.put(name, new ArrayList<TZDBRule>());
        }
        rules.get(name).add(rule);
        rule.startYear = parseYear(s, 0);
        rule.endYear = parseYear(s, rule.startYear);
        if (rule.startYear > rule.endYear) {
            throw new IllegalArgumentException("Year order invalid: " + rule.startYear + " > " + rule.endYear);
        }
        parseOptional(s.next());  // type is unused
        parseMonthDayTime(s, rule);
        rule.savingsAmount = parsePeriod(s.next());
        rule.text = parseOptional(s.next());
    }

    /**
     * Parses a Zone line.
     *
     * @param s  the line scanner, not null
     * @return true if the zone is complete
     */
    private boolean parseZoneLine(Scanner s, List<TZDBZone> zoneList) {
        TZDBZone zone = new TZDBZone();
        zoneList.add(zone);
        zone.standardOffset = parseOffset(s.next());
        String savingsRule = parseOptional(s.next());
        if (savingsRule == null) {
            zone.fixedSavingsSecs = 0;
            zone.savingsRule = null;
        } else {
            try {
                zone.fixedSavingsSecs = parsePeriod(savingsRule);
                zone.savingsRule = null;
            } catch (Exception ex) {
                zone.fixedSavingsSecs = null;
                zone.savingsRule = savingsRule;
            }
        }
        zone.text = s.next();
        if (s.hasNext()) {
            zone.year = Integer.parseInt(s.next());
            if (s.hasNext()) {
                parseMonthDayTime(s, zone);
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * Parses a Rule line.
     *
     * @param s  the line scanner, not null
     * @param mdt  the object to parse into, not null
     */
    private void parseMonthDayTime(Scanner s, TZDBMonthDayTime mdt) {
        mdt.month = parseMonth(s);
        if (s.hasNext()) {
            String dayRule = s.next();
            if (dayRule.startsWith("last")) {
                mdt.dayOfMonth = -1;
                mdt.dayOfWeek = parseDayOfWeek(dayRule.substring(4));
                mdt.adjustForwards = false;
            } else {
                int index = dayRule.indexOf(">=");
                if (index > 0) {
                    mdt.dayOfWeek = parseDayOfWeek(dayRule.substring(0, index));
                    dayRule = dayRule.substring(index + 2);
                } else {
                    index = dayRule.indexOf("<=");
                    if (index > 0) {
                        mdt.dayOfWeek = parseDayOfWeek(dayRule.substring(0, index));
                        mdt.adjustForwards = false;
                        dayRule = dayRule.substring(index + 2);
                    }
                }
                mdt.dayOfMonth = Integer.parseInt(dayRule);
            }
            if (s.hasNext()) {
                String timeStr = s.next();
                int secsOfDay = parseSecs(timeStr);
                if (secsOfDay == 86400) {
                    mdt.endOfDay = true;
                    secsOfDay = 0;
                }
                LocalTime time = LocalTime.ofSecondOfDay(secsOfDay);
                mdt.time = time;
                mdt.timeDefinition = parseTimeDefinition(timeStr.charAt(timeStr.length() - 1));
            }
        }
    }

    private int parseYear(Scanner s, int defaultYear) {
        if (s.hasNext(YEAR)) {
            s.next(YEAR);
            MatchResult mr = s.match();
            if (mr.group(1) != null) {
                return 1900;  // systemv has min
            } else if (mr.group(2) != null) {
                return YEAR_MAX_VALUE;
            } else if (mr.group(3) != null) {
                return defaultYear;
            }
            return Integer.parseInt(mr.group(4));
            /*
            if (mr.group("min") != null) {
                //return YEAR_MIN_VALUE;
                return 1900;  // systemv has min
            } else if (mr.group("max") != null) {
                return YEAR_MAX_VALUE;
            } else if (mr.group("only") != null) {
                return defaultYear;
            }
            return Integer.parseInt(mr.group("year"));
            */
        }
        throw new IllegalArgumentException("Unknown year: " + s.next());
    }

    private int parseMonth(Scanner s) {
        if (s.hasNext(MONTH)) {
            s.next(MONTH);
            for (int moy = 1; moy < 13; moy++) {
                if (s.match().group(moy) != null) {
                    return moy;
                }
            }
        }
        throw new IllegalArgumentException("Unknown month: " + s.next());
    }

    private int parseDayOfWeek(String str) {
        if (DOW.reset(str).matches()) {
            for (int dow = 1; dow < 8; dow++) {
                if (DOW.group(dow) != null) {
                    return dow;
                }
            }
        }
        throw new IllegalArgumentException("Unknown day-of-week: " + str);
    }

    private String parseOptional(String str) {
        return str.equals("-") ? null : str;
    }

    private int parseSecs(String str) {
        if (str.equals("-")) {
            return 0;
        }
        try {
            if (TIME.reset(str).find()) {
                int secs = Integer.parseInt(TIME.group("hour")) * 60 * 60;
                if (TIME.group("minute") != null) {
                    secs += Integer.parseInt(TIME.group("minute")) * 60;
                }
                if (TIME.group("second") != null) {
                    secs += Integer.parseInt(TIME.group("second"));
                }
                if (TIME.group("neg") != null) {
                    secs = -secs;
                }
                return secs;
            }
        } catch (NumberFormatException x) {}
        throw new IllegalArgumentException(str);
    }

    private ZoneOffset parseOffset(String str) {
        int secs = parseSecs(str);
        return ZoneOffset.ofTotalSeconds(secs);
    }

    private int parsePeriod(String str) {
        return parseSecs(str);
    }

    private TimeDefinition parseTimeDefinition(char c) {
        switch (c) {
            case 's':
            case 'S':
                // standard time
                return TimeDefinition.STANDARD;
            case 'u':
            case 'U':
            case 'g':
            case 'G':
            case 'z':
            case 'Z':
                // UTC
                return TimeDefinition.UTC;
            case 'w':
            case 'W':
            default:
                // wall time
                return TimeDefinition.WALL;
        }
    }

    /**
     * Build the rules, zones and links into real zones.
     *
     * @throws Exception if an error occurs
     */
    private void buildZoneRules() throws Exception {
        // build zones
        for (String zoneId : zones.keySet()) {
            printVerbose("Building zone " + zoneId);
            List<TZDBZone> tzdbZones = zones.get(zoneId);
            ZoneRulesBuilder bld = new ZoneRulesBuilder();
            for (TZDBZone tzdbZone : tzdbZones) {
                bld = tzdbZone.addToBuilder(bld, rules);
            }
            builtZones.put(zoneId, bld.toRules(zoneId));
        }

        // build aliases
        for (String aliasId : links.keySet()) {
            String realId = links.get(aliasId);
            printVerbose("Linking alias " + aliasId + " to " + realId);
            ZoneRules realRules = builtZones.get(realId);
            if (realRules == null) {
                realId = links.get(realId);  // try again (handle alias liked to alias)
                printVerbose("Relinking alias " + aliasId + " to " + realId);
                realRules = builtZones.get(realId);
                if (realRules == null) {
                    throw new IllegalArgumentException("Alias '" + aliasId + "' links to invalid zone '" + realId);
                }
                links.put(aliasId, realId);
            }
            builtZones.put(aliasId, realRules);
        }
        // remove UTC and GMT
        // builtZones.remove("UTC");
        // builtZones.remove("GMT");
        // builtZones.remove("GMT0");
        builtZones.remove("GMT+0");
        builtZones.remove("GMT-0");
        links.remove("GMT+0");
        links.remove("GMT-0");
        // remove ROC, which is not supported in j.u.tz
        builtZones.remove("ROC");
        links.remove("ROC");
        // remove EST, HST and MST. They are supported via
        // the short-id mapping
        builtZones.remove("EST");
        builtZones.remove("HST");
        builtZones.remove("MST");
    }

    /**
     * Prints a verbose message.
     *
     * @param message  the message, not null
     */
    private void printVerbose(String message) {
        if (verbose) {
            System.out.println(message);
        }
    }

    /**
     * Class representing a month-day-time in the TZDB file.
     */
    abstract class TZDBMonthDayTime {
        /** The month of the cutover. */
        int month = 1;
        /** The day-of-month of the cutover. */
        int dayOfMonth = 1;
        /** Whether to adjust forwards. */
        boolean adjustForwards = true;
        /** The day-of-week of the cutover. */
        int dayOfWeek = -1;
        /** The time of the cutover. */
        LocalTime time = LocalTime.MIDNIGHT;
        /** Whether this is midnight end of day. */
        boolean endOfDay;
        /** The time of the cutover. */
        TimeDefinition timeDefinition = TimeDefinition.WALL;
        void adjustToFowards(int year) {
            if (adjustForwards == false && dayOfMonth > 0) {
                LocalDate adjustedDate = LocalDate.of(year, month, dayOfMonth).minusDays(6);
                dayOfMonth = adjustedDate.getDayOfMonth();
                month = adjustedDate.getMonth();
                adjustForwards = true;
            }
        }
    }

    /**
     * Class representing a rule line in the TZDB file.
     */
    final class TZDBRule extends TZDBMonthDayTime {
        /** The start year. */
        int startYear;
        /** The end year. */
        int endYear;
        /** The amount of savings. */
        int savingsAmount;
        /** The text name of the zone. */
        String text;

        void addToBuilder(ZoneRulesBuilder bld) {
            adjustToFowards(2004);  // irrelevant, treat as leap year
            bld.addRuleToWindow(startYear, endYear, month, dayOfMonth, dayOfWeek, time, endOfDay, timeDefinition, savingsAmount);
        }
    }

    /**
     * Class representing a linked set of zone lines in the TZDB file.
     */
    final class TZDBZone extends TZDBMonthDayTime {
        /** The standard offset. */
        ZoneOffset standardOffset;
        /** The fixed savings amount. */
        Integer fixedSavingsSecs;
        /** The savings rule. */
        String savingsRule;
        /** The text name of the zone. */
        String text;
        /** The year of the cutover. */
        int year = YEAR_MAX_VALUE;

        ZoneRulesBuilder addToBuilder(ZoneRulesBuilder bld, Map<String, List<TZDBRule>> rules) {
            if (year != YEAR_MAX_VALUE) {
                bld.addWindow(standardOffset, toDateTime(year), timeDefinition);
            } else {
                bld.addWindowForever(standardOffset);
            }
            if (fixedSavingsSecs != null) {
                bld.setFixedSavingsToWindow(fixedSavingsSecs);
            } else {
                List<TZDBRule> tzdbRules = rules.get(savingsRule);
                if (tzdbRules == null) {
                    throw new IllegalArgumentException("Rule not found: " + savingsRule);
                }
                for (TZDBRule tzdbRule : tzdbRules) {
                    tzdbRule.addToBuilder(bld);
                }
            }
            return bld;
        }

        private LocalDateTime toDateTime(int year) {
            adjustToFowards(year);
            LocalDate date;
            if (dayOfMonth == -1) {
                dayOfMonth = lengthOfMonth(month, isLeapYear(year));
                date = LocalDate.of(year, month, dayOfMonth);
                if (dayOfWeek != -1) {
                    date = previousOrSame(date, dayOfWeek);
                }
            } else {
                date = LocalDate.of(year, month, dayOfMonth);
                if (dayOfWeek != -1) {
                    date = nextOrSame(date, dayOfWeek);
                }
            }
            LocalDateTime ldt = LocalDateTime.of(date, time);
            if (endOfDay) {
                ldt = ldt.plusDays(1);
            }
            return ldt;
        }
    }
}
