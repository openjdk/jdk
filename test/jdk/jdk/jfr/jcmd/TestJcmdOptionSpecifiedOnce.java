/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.jcmd;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @test
 * @summary The test verifies that options can only be specified once with jcmd JFR.*
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @modules jdk.jfr/jdk.jfr.internal.dcmd
 * @run main/othervm jdk.jfr.jcmd.TestJcmdOptionSpecifiedOnce
 */
public class TestJcmdOptionSpecifiedOnce {

    public static void main(String[] args) throws Exception {

        testCheckingLog();
        checkStarting();
        checkStopping();
        checkDumping();
        checkChecking();
    }

    private static void testCheckingLog() {

        testSpecifiedOption("JFR.start", "name=abc name=def", "name" );
        testSpecifiedOptions("JFR.start", "name=abc name=def disk=true disk=false", Set.of("name", "disk"));
        testSpecifiedOptions("JFR.start", "name=abc name=def disk=true disk=false delay=1s delay=2s", Set.of("name", "disk", "delay"));
        testSpecifiedOptions("JFR.start", "name=abc name=def disk=true disk=false delay=1s delay=2s maxage=1s maxage=2s", Set.of("name", "disk", "delay", "maxage"));
    }

    public static void checkStarting() {

        testSpecifiedOption("JFR.start", "name=abc name=def", "name" );
        testSpecifiedOption("JFR.start", "disk=true disk=false", "disk" );
        testSpecifiedOption("JFR.start", "delay=1s delay=2s", "delay" );
        testSpecifiedOption("JFR.start", "duration=1s duration=2s", "duration" );
        testSpecifiedOption("JFR.start", "maxage=1s maxage=2s", "maxage" );
        testSpecifiedOption("JFR.start", "maxsize=1m maxsize=2m", "maxsize" );
        testSpecifiedOption("JFR.start", "flush-interval=1s flush-interval=2s", "flush-interval" );
        testSpecifiedOption("JFR.start", "dumponexit=true dumponexit=false", "dumponexit" );
        testSpecifiedOption("JFR.start", "filename=filename1 filename=filename2", "filename" );
        testSpecifiedOption("JFR.start", "path-to-gc-roots=true path-to-gc-roots=false", "path-to-gc-roots" );

        testMultipleOption("JFR.start", "settings=default settings=profile");
    }

    public static void checkStopping() {

        testSpecifiedOption("JFR.stop", "filename=filename1 filename=filename2", "filename" );
        testSpecifiedOption("JFR.stop", "name=abc name=def", "name" );
    }

    public static void checkDumping() {

        testSpecifiedOption("JFR.dump", "begin=0:00 begin=0:01", "begin" );
        testSpecifiedOption("JFR.dump", "end=1:00 end=1:01", "end" );
        testSpecifiedOption("JFR.dump", "filename=filename1 filename=filename2", "filename" );
        testSpecifiedOption("JFR.dump", "maxage=1s maxage=2s", "maxage" );
        testSpecifiedOption("JFR.dump", "name=abc name=def", "name" );
        testSpecifiedOption("JFR.dump", "path-to-gc-roots=true path-to-gc-roots=false", "path-to-gc-roots" );
    }

    public static void checkChecking() {

        testSpecifiedOption("JFR.check", "name=abc name=def", "name" );
        testSpecifiedOption("JFR.check", "verbose=true verbose=false", "verbose" );
    }

    private static void testSpecifiedOption(String command, String option, String expectOption){

        String output = JcmdHelper.jcmd("%s %s".formatted(command, option)).getOutput();

        try {
            Matcher matcher = Pattern.compile("Option ([a-z-]+) can only be specified once").matcher(output);
            matcher.find();
            String outputtedOption = matcher.group(1);

            if (!outputtedOption.equals(expectOption)){
                throw new RuntimeException("expected option is %s, but actual is %s".formatted(expectOption, outputtedOption));
            }

        } catch (Exception e){
            System.err.println(output);
            throw e;
        }
    }

    private static void testMultipleOption(String command, String option){

        String output = JcmdHelper.jcmd("%s %s".formatted(command, option)).getOutput();
        final String regex = "Option ([a-z-]+) can only be specified once with ([a-z]+) flight recording";

        try {
            Matcher matcher = Pattern.compile(regex).matcher(output);
            if (matcher.find()){
                throw new RuntimeException("found in output : \"%s\"".formatted(regex));
            }
        } catch (Exception e){
            System.err.println(output);
            throw e;
        }
    }


    private static void testSpecifiedOptions(String command, String option, Set<String> expectOptions){

        String output = JcmdHelper.jcmd("%s %s".formatted(command, option)).getOutput();

        try {
            Matcher matcher = Pattern.compile("Options ([a-z-]+)((?:, [a-z-]+)*) and ([a-z-]+) can only be specified once").matcher(output);
            matcher.find();

            Set<String> outputtedOptions = new HashSet<>();

            // Mandatory First option
            outputtedOptions.add(matcher.group(1));

            // A collection of non-mandatory options, strings joined by ",".
            Arrays.stream(matcher.group(2).split(", ")).filter(s->s.length() > 0).forEach(outputtedOptions::add);

            // Mandatory last option
            outputtedOptions.add(matcher.group(3));

            if (!outputtedOptions.equals(expectOptions)){
                throw new RuntimeException("expected options are %s, but actual are %s".formatted(expectOptions, outputtedOptions));
            }

        } catch (Exception e){
            System.err.println(output);
            throw e;
        }
    }
}

