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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A builder that can read the TZDB time-zone files and build {@code ZoneRules} instances.
 *
 * @since 1.8
 */
public final class TzdbZoneRulesCompiler {

    private static final Matcher YEAR = Pattern.compile("(?i)(?<min>min)|(?<max>max)|(?<only>only)|(?<year>[0-9]+)").matcher("");
    private static final Matcher MONTH = Pattern.compile("(?i)(jan)|(feb)|(mar)|(apr)|(may)|(jun)|(jul)|(aug)|(sep)|(oct)|(nov)|(dec)").matcher("");
    private static final Matcher DOW = Pattern.compile("(?i)(mon)|(tue)|(wed)|(thu)|(fri)|(sat)|(sun)").matcher("");
    private static final Matcher TIME = Pattern.compile("(?<neg>-)?+(?<hour>[0-9]{1,2})(:(?<minute>[0-5][0-9]))?+(:(?<second>[0-5][0-9]))?+").matcher("");

    /**
     * Constant for MJD 1972-01-01.
     */
    private static final long MJD_1972_01_01 = 41317L;

    /**
     * Reads a set of TZDB files and builds a single combined data file.
     *
     * @param args  the arguments
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            outputHelp();
            return;
        }

        // parse args
        String version = null;
        File baseSrcDir = null;
        File dstDir = null;
        boolean verbose = false;

        // parse options
        int i;
        for (i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-") == false) {
                break;
            }
            if ("-srcdir".equals(arg)) {
                if (baseSrcDir == null && ++i < args.length) {
                    baseSrcDir = new File(args[i]);
                    continue;
                }
            } else if ("-dstdir".equals(arg)) {
                if (dstDir == null && ++i < args.length) {
                    dstDir = new File(args[i]);
                    continue;
                }
            } else if ("-version".equals(arg)) {
                if (version == null && ++i < args.length) {
                    version = args[i];
                    continue;
                }
            } else if ("-verbose".equals(arg)) {
                if (verbose == false) {
                    verbose = true;
                    continue;
                }
            } else if ("-help".equals(arg) == false) {
                System.out.println("Unrecognised option: " + arg);
            }
            outputHelp();
            return;
        }

        // check source directory
        if (baseSrcDir == null) {
            System.out.println("Source directory must be specified using -srcdir: " + baseSrcDir);
            return;
        }
        if (baseSrcDir.isDirectory() == false) {
            System.out.println("Source does not exist or is not a directory: " + baseSrcDir);
            return;
        }
        dstDir = (dstDir != null ? dstDir : baseSrcDir);

        // parse source file names
        List<String> srcFileNames = Arrays.asList(Arrays.copyOfRange(args, i, args.length));
        if (srcFileNames.isEmpty()) {
            System.out.println("Source filenames not specified, using default set");
            System.out.println("(africa antarctica asia australasia backward etcetera europe northamerica southamerica)");
            srcFileNames = Arrays.asList("africa", "antarctica", "asia", "australasia", "backward",
                    "etcetera", "europe", "northamerica", "southamerica");
        }

        // find source directories to process
        List<File> srcDirs = new ArrayList<>();
        if (version != null) {
            //  if the "version" specified, as in jdk repo, the "baseSrcDir" is
            //  the "srcDir" that contains the tzdb data.
            srcDirs.add(baseSrcDir);
        } else {
            File[] dirs = baseSrcDir.listFiles();
            for (File dir : dirs) {
                if (dir.isDirectory() && dir.getName().matches("[12][0-9]{3}[A-Za-z0-9._-]+")) {
                    srcDirs.add(dir);
                }
            }
        }
        if (srcDirs.isEmpty()) {
            System.out.println("Source directory contains no valid source folders: " + baseSrcDir);
            return;
        }
        // check destination directory
        if (dstDir.exists() == false && dstDir.mkdirs() == false) {
            System.out.println("Destination directory could not be created: " + dstDir);
            return;
        }
        if (dstDir.isDirectory() == false) {
            System.out.println("Destination is not a directory: " + dstDir);
            return;
        }
        process(srcDirs, srcFileNames, dstDir, version, verbose);
        System.exit(0);
    }

    /**
     * Output usage text for the command line.
     */
    private static void outputHelp() {
        System.out.println("Usage: TzdbZoneRulesCompiler <options> <tzdb source filenames>");
        System.out.println("where options include:");
        System.out.println("   -srcdir <directory>   Where to find source directories (required)");
        System.out.println("   -dstdir <directory>   Where to output generated files (default srcdir)");
        System.out.println("   -version <version>    Specify the version, such as 2009a (optional)");
        System.out.println("   -help                 Print this usage message");
        System.out.println("   -verbose              Output verbose information during compilation");
        System.out.println(" There must be one directory for each version in srcdir");
        System.out.println(" Each directory must have the name of the version, such as 2009a");
        System.out.println(" Each directory must contain the unpacked tzdb files, such as asia or europe");
        System.out.println(" Directories must match the regex [12][0-9][0-9][0-9][A-Za-z0-9._-]+");
        System.out.println(" There will be one jar file for each version and one combined jar in dstdir");
        System.out.println(" If the version is specified, only that version is processed");
    }

    /**
     * Process to create the jar files.
     */
    private static void process(List<File> srcDirs, List<String> srcFileNames, File dstDir, String version, boolean verbose) {
        // build actual jar files
        Map<String, SortedMap<String, ZoneRules>> allBuiltZones = new TreeMap<>();
        Set<String> allRegionIds = new TreeSet<String>();
        Set<ZoneRules> allRules = new HashSet<ZoneRules>();
        Map<String, Map<String, String>> allLinks = new TreeMap<>();

        for (File srcDir : srcDirs) {
            // source files in this directory
            List<File> srcFiles = new ArrayList<>();
            for (String srcFileName : srcFileNames) {
                File file = new File(srcDir, srcFileName);
                if (file.exists()) {
                    srcFiles.add(file);
                }
            }
            if (srcFiles.isEmpty()) {
                continue;  // nothing to process
            }

            // compile
            String loopVersion = (srcDirs.size() == 1 && version != null)
                                 ? version : srcDir.getName();
            TzdbZoneRulesCompiler compiler = new TzdbZoneRulesCompiler(loopVersion, srcFiles, verbose);
            try {
                // compile
                compiler.compile();
                SortedMap<String, ZoneRules> builtZones = compiler.getZones();

                // output version-specific file
                File dstFile = version == null ? new File(dstDir, "tzdb" + loopVersion + ".jar")
                                               : new File(dstDir, "tzdb.jar");
                if (verbose) {
                    System.out.println("Outputting file: " + dstFile);
                }
                outputFile(dstFile, loopVersion, builtZones, compiler.links);

                // create totals
                allBuiltZones.put(loopVersion, builtZones);
                allRegionIds.addAll(builtZones.keySet());
                allRules.addAll(builtZones.values());
                allLinks.put(loopVersion, compiler.links);
            } catch (Exception ex) {
                System.out.println("Failed: " + ex.toString());
                ex.printStackTrace();
                System.exit(1);
            }
        }

        // output merged file
        if (version == null) {
            File dstFile = new File(dstDir, "tzdb-all.jar");
            if (verbose) {
                System.out.println("Outputting combined file: " + dstFile);
            }
            outputFile(dstFile, allBuiltZones, allRegionIds, allRules, allLinks);
        }
    }

    /**
     * Outputs the file.
     */
    private static void outputFile(File dstFile,
                                   String version,
                                   SortedMap<String, ZoneRules> builtZones,
                                   Map<String, String> links) {
        Map<String, SortedMap<String, ZoneRules>> loopAllBuiltZones = new TreeMap<>();
        loopAllBuiltZones.put(version, builtZones);
        Set<String> loopAllRegionIds = new TreeSet<String>(builtZones.keySet());
        Set<ZoneRules> loopAllRules = new HashSet<ZoneRules>(builtZones.values());
        Map<String, Map<String, String>> loopAllLinks = new TreeMap<>();
        loopAllLinks.put(version, links);
        outputFile(dstFile, loopAllBuiltZones, loopAllRegionIds, loopAllRules, loopAllLinks);
    }

    /**
     * Outputs the file.
     */
    private static void outputFile(File dstFile,
                                   Map<String, SortedMap<String, ZoneRules>> allBuiltZones,
                                   Set<String> allRegionIds,
                                   Set<ZoneRules> allRules,
                                   Map<String, Map<String, String>> allLinks) {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(dstFile))) {
            outputTZEntry(jos, allBuiltZones, allRegionIds, allRules, allLinks);
        } catch (Exception ex) {
            System.out.println("Failed: " + ex.toString());
            ex.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Outputs the timezone entry in the JAR file.
     */
    private static void outputTZEntry(JarOutputStream jos,
                                      Map<String, SortedMap<String, ZoneRules>> allBuiltZones,
                                      Set<String> allRegionIds,
                                      Set<ZoneRules> allRules,
                                      Map<String, Map<String, String>> allLinks) {
        // this format is not publicly specified
        try {
            jos.putNextEntry(new ZipEntry("TZDB.dat"));
            DataOutputStream out = new DataOutputStream(jos);

            // file version
            out.writeByte(1);
            // group
            out.writeUTF("TZDB");
            // versions
            String[] versionArray = allBuiltZones.keySet().toArray(new String[allBuiltZones.size()]);
            out.writeShort(versionArray.length);
            for (String version : versionArray) {
                out.writeUTF(version);
            }
            // regions
            String[] regionArray = allRegionIds.toArray(new String[allRegionIds.size()]);
            out.writeShort(regionArray.length);
            for (String regionId : regionArray) {
                out.writeUTF(regionId);
            }
            // rules
            List<ZoneRules> rulesList = new ArrayList<>(allRules);
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
            for (String version : allBuiltZones.keySet()) {
                out.writeShort(allBuiltZones.get(version).size());
                for (Map.Entry<String, ZoneRules> entry : allBuiltZones.get(version).entrySet()) {
                     int regionIndex = Arrays.binarySearch(regionArray, entry.getKey());
                     int rulesIndex = rulesList.indexOf(entry.getValue());
                     out.writeShort(regionIndex);
                     out.writeShort(rulesIndex);
                }
            }
            // alias-region
            for (String version : allLinks.keySet()) {
                out.writeShort(allLinks.get(version).size());
                for (Map.Entry<String, String> entry : allLinks.get(version).entrySet()) {
                     int aliasIndex = Arrays.binarySearch(regionArray, entry.getKey());
                     int regionIndex = Arrays.binarySearch(regionArray, entry.getValue());
                     out.writeShort(aliasIndex);
                     out.writeShort(regionIndex);
                }
            }
            out.flush();
            jos.closeEntry();
        } catch (Exception ex) {
            System.out.println("Failed: " + ex.toString());
            ex.printStackTrace();
            System.exit(1);
        }
    }

    //-----------------------------------------------------------------------
    /** The TZDB rules. */
    private final Map<String, List<TZDBRule>> rules = new HashMap<>();

    /** The TZDB zones. */
    private final Map<String, List<TZDBZone>> zones = new HashMap<>();
    /** The TZDB links. */

    private final Map<String, String> links = new HashMap<>();

    /** The built zones. */
    private final SortedMap<String, ZoneRules> builtZones = new TreeMap<>();


    /** The version to produce. */
    private final String version;

    /** The source files. */

    private final List<File> sourceFiles;

    /** The version to produce. */
    private final boolean verbose;

    /**
     * Creates an instance if you want to invoke the compiler manually.
     *
     * @param version  the version, such as 2009a, not null
     * @param sourceFiles  the list of source files, not empty, not null
     * @param verbose  whether to output verbose messages
     */
    public TzdbZoneRulesCompiler(String version, List<File> sourceFiles, boolean verbose) {
        this.version = version;
        this.sourceFiles = sourceFiles;
        this.verbose = verbose;
    }

    /**
     * Compile the rules file.
     * <p>
     * Use {@link #getZones()} to retrieve the parsed data.
     *
     * @throws Exception if an error occurs
     */
    public void compile() throws Exception {
        printVerbose("Compiling TZDB version " + version);
        parseFiles();
        buildZoneRules();
        printVerbose("Compiled TZDB version " + version);
    }

    /**
     * Gets the parsed zone rules.
     *
     * @return the parsed zone rules, not null
     */
    public SortedMap<String, ZoneRules> getZones() {
        return builtZones;
    }

    /**
     * Parses the source files.
     *
     * @throws Exception if an error occurs
     */
    private void parseFiles() throws Exception {
        for (File file : sourceFiles) {
            printVerbose("Parsing file: " + file);
            parseFile(file);
        }
    }

    /**
     * Parses a source file.
     *
     * @param file  the file being read, not null
     * @throws Exception if an error occurs
     */
    private void parseFile(File file) throws Exception {
        int lineNumber = 1;
        String line = null;
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            List<TZDBZone> openZone = null;
            for ( ; (line = in.readLine()) != null; lineNumber++) {
                int index = line.indexOf('#');  // remove comments (doesn't handle # in quotes)
                if (index >= 0) {
                    line = line.substring(0, index);
                }
                if (line.trim().length() == 0) {  // ignore blank lines
                    continue;
                }
                StringTokenizer st = new StringTokenizer(line, " \t");
                if (openZone != null && Character.isWhitespace(line.charAt(0)) && st.hasMoreTokens()) {
                    if (parseZoneLine(st, openZone)) {
                        openZone = null;
                    }
                } else {
                    if (st.hasMoreTokens()) {
                        String first = st.nextToken();
                        if (first.equals("Zone")) {
                            if (st.countTokens() < 3) {
                                printVerbose("Invalid Zone line in file: " + file + ", line: " + line);
                                throw new IllegalArgumentException("Invalid Zone line");
                            }
                            openZone = new ArrayList<>();
                            zones.put(st.nextToken(), openZone);
                            if (parseZoneLine(st, openZone)) {
                                openZone = null;
                            }
                        } else {
                            openZone = null;
                            if (first.equals("Rule")) {
                                if (st.countTokens() < 9) {
                                    printVerbose("Invalid Rule line in file: " + file + ", line: " + line);
                                    throw new IllegalArgumentException("Invalid Rule line");
                                }
                                parseRuleLine(st);

                            } else if (first.equals("Link")) {
                                if (st.countTokens() < 2) {
                                    printVerbose("Invalid Link line in file: " + file + ", line: " + line);
                                    throw new IllegalArgumentException("Invalid Link line");
                                }
                                String realId = st.nextToken();
                                String aliasId = st.nextToken();
                                links.put(aliasId, realId);

                            } else {
                                throw new IllegalArgumentException("Unknown line");
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new Exception("Failed while processing file '" + file + "' on line " + lineNumber + " '" + line + "'", ex);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ex) {
                // ignore NPE and IOE
            }
        }
    }

    /**
     * Parses a Rule line.
     *
     * @param st  the tokenizer, not null
     */
    private void parseRuleLine(StringTokenizer st) {
        TZDBRule rule = new TZDBRule();
        String name = st.nextToken();
        if (rules.containsKey(name) == false) {
            rules.put(name, new ArrayList<TZDBRule>());
        }
        rules.get(name).add(rule);
        rule.startYear = parseYear(st.nextToken(), 0);
        rule.endYear = parseYear(st.nextToken(), rule.startYear);
        if (rule.startYear > rule.endYear) {
            throw new IllegalArgumentException("Year order invalid: " + rule.startYear + " > " + rule.endYear);
        }
        parseOptional(st.nextToken());  // type is unused
        parseMonthDayTime(st, rule);
        rule.savingsAmount = parsePeriod(st.nextToken());
        rule.text = parseOptional(st.nextToken());
    }

    /**
     * Parses a Zone line.
     *
     * @param st  the tokenizer, not null
     * @return true if the zone is complete
     */
    private boolean parseZoneLine(StringTokenizer st, List<TZDBZone> zoneList) {
        TZDBZone zone = new TZDBZone();
        zoneList.add(zone);
        zone.standardOffset = parseOffset(st.nextToken());
        String savingsRule = parseOptional(st.nextToken());
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
        zone.text = st.nextToken();
        if (st.hasMoreTokens()) {
            zone.year = Integer.parseInt(st.nextToken());
            if (st.hasMoreTokens()) {
                parseMonthDayTime(st, zone);
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * Parses a Rule line.
     *
     * @param st  the tokenizer, not null
     * @param mdt  the object to parse into, not null
     */
    private void parseMonthDayTime(StringTokenizer st, TZDBMonthDayTime mdt) {
        mdt.month = parseMonth(st.nextToken());
        if (st.hasMoreTokens()) {
            String dayRule = st.nextToken();
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
            if (st.hasMoreTokens()) {
                String timeStr = st.nextToken();
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

    private int parseYear(String str, int defaultYear) {
        if (YEAR.reset(str).matches()) {
            if (YEAR.group("min") != null) {
                //return YEAR_MIN_VALUE;
                return 1900;  // systemv has min
            } else if (YEAR.group("max") != null) {
                return YEAR_MAX_VALUE;
            } else if (YEAR.group("only") != null) {
                return defaultYear;
            }
            return Integer.parseInt(YEAR.group("year"));
        }
        throw new IllegalArgumentException("Unknown year: " + str);
    }

    private int parseMonth(String str) {
        if (MONTH.reset(str).matches()) {
            for (int moy = 1; moy < 13; moy++) {
                if (MONTH.group(moy) != null) {
                    return moy;
                }
            }
        }
        throw new IllegalArgumentException("Unknown month: " + str);
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

    //-----------------------------------------------------------------------
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
            ZoneRules buildRules = bld.toRules(zoneId);
            builtZones.put(zoneId, buildRules);
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
                    throw new IllegalArgumentException("Alias '" + aliasId + "' links to invalid zone '" + realId + "' for '" + version + "'");
                }
                links.put(aliasId, realId);

            }
            builtZones.put(aliasId, realRules);
        }

        // remove UTC and GMT
        //builtZones.remove("UTC");
        //builtZones.remove("GMT");
        //builtZones.remove("GMT0");
        builtZones.remove("GMT+0");
        builtZones.remove("GMT-0");
        links.remove("GMT+0");
        links.remove("GMT-0");
    }

    //-----------------------------------------------------------------------
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

    //-----------------------------------------------------------------------
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
