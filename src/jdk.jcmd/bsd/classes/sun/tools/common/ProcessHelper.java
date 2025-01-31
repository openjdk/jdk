/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.common;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * A helper class that retrieves the main class name for
 * a running Java process.
 */
public
class ProcessHelper {

    private static final String ARGUMENT_TAG = "<arguments>";
    private static final String ARGUMENT_END_TAG = "</arguments>";
    private static final int ARGUMENT_TAG_LENGTH = ARGUMENT_END_TAG.length() - 1;

    /**
     * Gets the main class name for the given Java process by parsing the
     * process command line. If the application was started with the <em>-jar</em>
     * option this method returns the name of the jar file. If the application
     * was started with <em>-m</em> or <em>--module</em> option, the method returns
     * the module name and the main class name.
     * @param pid - process ID (pid)
     * @return the main class name or null if the process no longer exists or
     * was started with a native launcher (e.g. jcmd etc)
     */
    static
    String getMainClass(String pid) {
        List<String> cmdLine = getCommandLine(pid);
        if (cmdLine == null || cmdLine.isEmpty()) {
            return null;
        }
        String mainClass = null;

        // Check the executable
        String[] executablePath = cmdLine.get(0).split("/");
        if (executablePath.length > 0) {
            String binaryName = executablePath[executablePath.length - 1];
            if (!"java".equals(binaryName)) {
                // Skip the process if it is not started with java launcher
                return null;
            }
        }

        // To be consistent with the behavior on other platforms, if -jar, -m, or --module
        // options are used then just return the value (the path to the jar file or module
        // name with a main class). Otherwise, the main class name is the first part that
        // is not a Java option (doesn't start with '-' and is not a classpath or a module
        // whitespace option).

        for (int i = 1; i < cmdLine.size() && mainClass == null; i++) {
            if (i < cmdLine.size() - 1) {
                if (cmdLine.get(i).equals("-m") || cmdLine.get(i).equals("--module") || cmdLine.get(i).equals("-jar")) {
                    return cmdLine.get(i + 1);
                }
            }

            if (cmdLine.get(i).startsWith("--module=")) {
                return cmdLine.get(i).substring("--module=".length());
            }

            // If this is a classpath or a module whitespace option then skip the next part
            // (the classpath or the option value itself)
            if (cmdLine.get(i).equals("-cp") || cmdLine.get(i).equals("-classpath") ||  cmdLine.get(i).equals("--class-path") ||
                    isModuleWhiteSpaceOption(cmdLine.get(i))) {
                i++;
                continue;
            }
            // Skip all other Java options
            if (cmdLine.get(i).startsWith("-")) {
                continue;
            }

            // If it is a source-file mode then return null
            if (cmdLine.get(i).endsWith(".java")) {
                return null;
            }

            mainClass = cmdLine.get(i);
        }
        return mainClass;
    }

    /**
     * Get the command line arguments for the given process.
     *
     * Since doing this is OS specific, null is returned if an unrecognised
     * BSD is encountered.
     *
     * FreeBSD:
     *
     * Use procstat(1) to output the command line arguments for the given
     * process in XML format and "parse" that output to extract the arguments.
     *
     * Other options that were considered:
     *
     * * Parse the text output of procstat.  The problem with this is that
     *   the text output doesn't distinguish between two arguments that are
     *   separated by a space and a single argument which contains a space.
     * * Parse the JSON output of procstat.  The problem with this is that
     *   there is no available JSON parser in the standard code base.
     * * Use an XML Parser to parse the JSON output of procstat.  The problem
     *   with this is that there is no available XML parser in this module.
     * * Use native code to determine the command line arguments.  The problem
     *   with this is that there is no native code within the jdk.jcmd package
     *   and hence no library to put it into.
     *
     *  Sample output of procstat:
     *  <procstat version="1">
     *    <arguments>
     *      <1>
     *        <process_id>1</process_id>
     *        <command>init</command>
     *        <arguments>/sbin/init</arguments>
     *        <arguments>--</arguments>
     *      </1>
     *    </arguments>
     *  </procstat>
     *
     *
     *
     * @param pid
     *        the id of the process
     * @return the command line arguments of the process or null
     */
    private static
    List<String> getCommandLine(String pid) {
        String os = System.getProperty("os.name");
        if ("FreeBSD".equals(os)) {
            // Execute procstat and specify that XML output is desired
            // ("--libxo:X") with the command line arguments ("-c")
            var processBuilder = new ProcessBuilder();
            processBuilder.command("procstat", "--libxo:X", "-c", pid);
            try {
                var process = processBuilder.start();

                // Record output
                StringBuilder output = new StringBuilder();
                try (var reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line);
                    }
                }
                catch (IOException e) {
                    return null;
                }

                // Check procstat exit status
                if (0 != process.waitFor()) {
                    return null;
                }

                String cmdOutput = output.toString();
                // Check if the output is one that is understood
                if (!cmdOutput.contains("<procstat version=\"1\">")) {
                    return null;
                }

                // Ignore the first tag
                // The first <arguments> tag in version 1 output is not for an
                // argument (see example output above)
                int pos = cmdOutput.indexOf(ARGUMENT_TAG);
                if (pos == -1) {
                    return null;
                }

                // Extract the arguments
                List<String> commandArgs = new LinkedList<>();
                while ((pos = cmdOutput.indexOf(ARGUMENT_TAG, pos + 1)) != -1) {
                    extractArgument(cmdOutput, pos).ifPresent(commandArgs::add);
                }

                // Return the list of arguments
                return commandArgs;
            }
            catch (IOException | UncheckedIOException | InterruptedException e) {
                return null;
            }
        }
        return null;
    }

    private static
    boolean isModuleWhiteSpaceOption(String option) {
        return option.equals("-p") ||
               option.equals("--module-path") ||
               option.equals("--upgrade-module-path") ||
               option.equals("--add-modules") ||
               option.equals("--limit-modules") ||
               option.equals("--add-exports") ||
               option.equals("--add-opens") ||
               option.equals("--add-reads") ||
               option.equals("--patch-module");
    }

    /**
     * Extract an argument from the command output given the position of
     * the opening &amp;arguments&amp; tag.
     *
     * @param cmdOutput
     *        the command output
     * @param argumentTagStart
     *        the start of the tag for the argument being extracted
     * @return the extracted argument or empty if none could be found
     */
    private static
    Optional<String> extractArgument(String cmdOutput, int argumentTagStart) {
        int closingArgumentTagStart = cmdOutput.indexOf(ARGUMENT_END_TAG, argumentTagStart);
        if (closingArgumentTagStart != -1) {
            // Extract the argument
            String argument = cmdOutput.substring(argumentTagStart + ARGUMENT_TAG_LENGTH, closingArgumentTagStart);

            // Unescape standard XML escape characters
            // Note that there should be no numeric sequences or others that
            // need dealing with
            argument = argument.replace("&apos;", "'");
            argument = argument.replace("&gt;", ">");
            argument = argument.replace("&lt;", "<");
            argument = argument.replace("&quot;", "\"");
            // This must go last to avoid problems where there is a literal
            // of one of the above in an argument
            argument = argument.replace("&amp;", "&");

            return Optional.of(argument);
        }
        return Optional.empty();
    }
}
