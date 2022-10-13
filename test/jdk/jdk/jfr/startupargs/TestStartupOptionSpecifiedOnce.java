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
package jdk.jfr.startupargs;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @test The test verifies that options can only be specified once with --XX:StartFlightRecording
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main jdk.jfr.startupargs.TestStartupOptionSpecifiedOnce
 */
public class TestStartupOptionSpecifiedOnce {

    public static class TestMessage {
        public static void main(String[] args) throws Exception {
        }
    }

    public static void main(String[] args) throws Exception {

        testSpecifiedOption("name=abc,name=def", "name" );
        testSpecifiedOption("disk=true,disk=false", "disk" );
        testSpecifiedOption("delay=1s,delay=2s", "delay" );
        testSpecifiedOption("duration=1s,duration=2s", "duration" );
        testSpecifiedOption("maxage=1s,maxage=2s", "maxage" );
        testSpecifiedOption("maxsize=1m,maxsize=2m", "maxsize" );
        testSpecifiedOption("flush-interval=1s,flush-interval=2s", "flush-interval" );
        testSpecifiedOption("dumponexit=true,dumponexit=false", "dumponexit" );
        testSpecifiedOption("filename=filename1,filename=filename2", "filename" );
        testSpecifiedOption("path-to-gc-roots=true,path-to-gc-roots=false", "path-to-gc-roots" );

        testMultipleOption("settings=default,settings=default");

        testSpecifiedOptions("name=abc,name=def,disk=true,disk=false", Set.of("name", "disk"));
        testSpecifiedOptions("name=abc,name=def,disk=true,disk=false,delay=1s,delay=2s", Set.of("name", "disk", "delay"));
        testSpecifiedOptions("name=abc,name=def,disk=true,disk=false,delay=1s,delay=2s,maxage=1s,maxage=2s", Set.of("name", "disk", "delay", "maxage"));
    }

    private static OutputAnalyzer startJfrJvm(String addedOptions) throws Exception {
        List<String> commands = new ArrayList<>(2);
        commands.add("-XX:StartFlightRecording=" + addedOptions);
        commands.add(TestMessage.class.getName());
        ProcessBuilder pb = ProcessTools.createTestJvm(commands);
        OutputAnalyzer out = ProcessTools.executeProcess(pb);
        return out;
    }

    private static void testSpecifiedOption(String option, String expectOption) throws Exception{

        String output = startJfrJvm(option).getOutput();

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

    private static void testMultipleOption(String option) throws Exception{

        String output = startJfrJvm(option).getOutput();
        final String regex = "Option ([a-z-]+) can only be specified once";

        try {
            Matcher matcher = Pattern.compile(regex).matcher(output);
            if(matcher.find()){
                throw new RuntimeException("found in output : \"%s\"".formatted(regex));
            }
        } catch (Exception e){
            System.err.println(output);
            throw e;
        }
    }


    private static void testSpecifiedOptions(String option, Set<String> expectOptions) throws Exception{

        String output = startJfrJvm(option).getOutput();

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
