/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8061999
 * @summary Test "-XX:VMOptionsFile" VM option
 * @library /testlibrary
 * @modules jdk.management
 * @run main TestVMOptionsFile
 */

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.ProcessTools;
import jdk.test.lib.Asserts;
import jdk.test.lib.DynamicVMOption;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

public class TestVMOptionsFile {

    /* Empty VM Option file */
    private static final String VM_OPTION_FILE_EMPTY = "optionfile_empty";
    /* VM Option file with tabs and spaces */
    private static final String VM_OPTION_FILE_TABS_AND_SPACES = "optionfile_only_tabsandspaces";
    /* Various valid VM Option files */
    private static final String VM_OPTION_FILE_1 = "optionfile_1";
    private static final String VM_OPTION_FILE_2 = "optionFILE_2";
    private static final String VM_OPTION_FILE_3 = "optionfile_3";
    private static final String VM_OPTION_FILE_QUOTE = "optionfile_quote";
    private static final String VM_OPTION_FILE_QUOTE_MAX_SIZE = "optionfile_quote_max_size";
    /* 2 VM Option files with unmatched quotes */
    private static final String VM_OPTION_FILE_UNMATCHED_QUOTE_1 = "optionfile_unmatched_quote_1";
    private static final String VM_OPTION_FILE_UNMATCHED_QUOTE_2 = "optionfile_unmatched_quote_2";
    /* Name of the file with flags for VM_OPTION_FILE_2 Option file */
    private static final String FLAGS_FILE = "flags_file";
    /* VM Option file with a lot of options with quote on separate lines */
    private static final String VM_OPTION_FILE_LOT_OF_OPTIONS_QUOTE = "optionfile_lot_of_options_quote";
    /* Number of properties defined in VM_OPTION_FILE_LOT_OF_OPTIONS_QUOTE */
    private static final int NUM_OF_PROP_IN_FILE_LOT_OF_OPTIONS_QUOTE = 65;
    /* VM Option file with bad option in it */
    private static final String VM_OPTION_FILE_WITH_BAD_OPTION = "optionfile_bad_option";
    /* VM Option file with "-XX:VMOptionsFile=" option in it */
    private static final String VM_OPTION_FILE_WITH_VM_OPTION_FILE = "optionfile_with_optionfile";
    /* VM Option file with "-XX:VMOptionsFile=" option in it, where file is the same option file */
    private static final String VM_OPTION_FILE_WITH_SAME_VM_OPTION_FILE = "optionfile_with_same_optionfile";
    /* VM Option file without read permissions(not accessible) */
    private static final String VM_OPTION_FILE_WITHOUT_READ_PERMISSIONS = "optionfile_wo_read_perm";
    /* VM Option file with long property(file size is 1024 bytes) */
    private static final String VM_OPTION_FILE_WITH_LONG_PROPERTY = "optionfile_long_property";
    /* VM Option file with very long property(file size is more than 1024 bytes) */
    private static final String VM_OPTION_FILE_WITH_VERY_LONG_PROPERTY = "optionfile_very_long_property";
    /* VM Option file which does not exist */
    private static final String NOT_EXISTING_FILE = "not_exist_junk2123";

    /* JAVA_TOOL_OPTIONS environment variable */
    private static final String JAVA_TOOL_OPTIONS = "JAVA_TOOL_OPTIONS";
    /* _JAVA_OPTIONS environment variable */
    private static final String JAVA_OPTIONS = "_JAVA_OPTIONS";

    /* Exit code for JVM, zero - for success, non-zero for failure */
    private static final int JVM_SUCCESS = 0;
    private static final int JVM_FAIL_WITH_EXIT_CODE_1 = 1;

    /* Current working directory */
    private static final String CURRENT_DIR = System.getProperty("user.dir");

    /* Source directory */
    private static final String SOURCE_DIR = System.getProperty("test.src", ".");

    /* VM Options which are passed to the JVM */
    private static final List<String> VMParams = new ArrayList<>();
    /* Argument passed to the PrintPropertyAndOptions.main */
    private static final Set<String> appParams = new LinkedHashSet<>();

    private static OutputAnalyzer output;

    /*
     * Get absoulte path to file from folder with sources
     */
    private static String getAbsolutePathFromSource(String fileName) {
        return SOURCE_DIR + File.separator + fileName;
    }

    /*
     * Make file non-readable by modifying its permissions.
     * If file supports "posix" attributes, then modify it.
     * Otherwise check for "acl" attributes.
     */
    private static void makeFileNonReadable(String file) throws IOException {
        Path filePath = Paths.get(file);
        Set<String> supportedAttr = filePath.getFileSystem().supportedFileAttributeViews();

        if (supportedAttr.contains("posix")) {
            Files.setPosixFilePermissions(filePath, PosixFilePermissions.fromString("-w--w----"));
        } else if (supportedAttr.contains("acl")) {
            UserPrincipal fileOwner = Files.getOwner(filePath);

            AclFileAttributeView view = Files.getFileAttributeView(filePath, AclFileAttributeView.class);

            AclEntry entry = AclEntry.newBuilder()
                    .setType(AclEntryType.DENY)
                    .setPrincipal(fileOwner)
                    .setPermissions(AclEntryPermission.READ_DATA)
                    .build();

            List<AclEntry> acl = view.getAcl();
            acl.add(0, entry);
            view.setAcl(acl);
        }
    }

    private static void copyFromSource(String fileName) throws IOException {
        Files.copy(Paths.get(getAbsolutePathFromSource(fileName)),
                Paths.get(fileName), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void createOptionFiles() throws IOException {
        FileWriter fw = new FileWriter(VM_OPTION_FILE_WITH_VM_OPTION_FILE);

        /* Create VM option file with following parameters "-XX:VMOptionFile=<absolute_path_to_the_VM_option_file> */
        fw.write("-XX:VMOptionsFile=" + getAbsolutePathFromSource(VM_OPTION_FILE_1));
        fw.close();

        /* Create VM option file with following parameters "-XX:MinHeapFreeRatio=12 -XX:VMOptionFile=<absolute_path_to_the_same_VM_option_file> */
        fw = new FileWriter(VM_OPTION_FILE_WITH_SAME_VM_OPTION_FILE);
        fw.write("-XX:MinHeapFreeRatio=12 -XX:VMOptionsFile=" + (new File(VM_OPTION_FILE_WITH_SAME_VM_OPTION_FILE)).getCanonicalPath());
        fw.close();

        /* Copy valid VM option file and change its permission to make it not accessible */
        Files.copy(Paths.get(getAbsolutePathFromSource(VM_OPTION_FILE_1)),
                Paths.get(VM_OPTION_FILE_WITHOUT_READ_PERMISSIONS),
                StandardCopyOption.REPLACE_EXISTING);

        makeFileNonReadable(VM_OPTION_FILE_WITHOUT_READ_PERMISSIONS);

        /* Copy valid VM option file to perform test with relative path */
        copyFromSource(VM_OPTION_FILE_2);

        /* Copy flags file to the current working folder */
        copyFromSource(FLAGS_FILE);

        /* Create a new empty file */
        new File(VM_OPTION_FILE_EMPTY).createNewFile();
    }

    /*
     * Add parameters to the VM Parameters list
     */
    private static void addVMParam(String... params) {
        VMParams.addAll(Arrays.asList(params));
    }

    /*
     * Add property name to the application arguments list
     */
    private static void addPropertiesToCheck(String... params) {
        for (String param : params) {
            appParams.add("property=" + param);
        }
    }

    /*
     * Add VM option name to the application arguments list
     */
    private static void addVMOptionsToCheck(String... params) {
        for (String param : params) {
            appParams.add("vmoption=" + param);
        }
    }

    /*
     * Add property to the VM Params list and to the application arguments list
     */
    private static void addProperty(String propertyName, String propertyValue) {
        addVMParam("-D" + propertyName + "=" + propertyValue);
        addPropertiesToCheck(propertyName);
    }

    /*
     * Add "-XX:VMOptionsfile" parameter to the VM Params list
     */
    private static void addVMOptionsFile(String fileName) {
        addVMParam("-XX:VMOptionsFile=" + fileName);
    }

    private static void outputShouldContain(String expectedString) {
        output.shouldContain(expectedString);
    }

    private static void outputShouldNotContain(String expectedString) {
        output.shouldNotContain(expectedString);
    }

    private static ProcessBuilder createProcessBuilder() throws Exception {
        ProcessBuilder pb;
        List<String> runJava = new ArrayList<>();

        runJava.addAll(VMParams);
        runJava.add(PrintPropertyAndOptions.class.getName());
        runJava.addAll(appParams);

        pb = ProcessTools.createJavaProcessBuilder(runJava.toArray(new String[0]));

        VMParams.clear();
        appParams.clear();

        return pb;
    }

    private static void runJavaCheckExitValue(ProcessBuilder pb, int expectedExitValue) throws Exception {
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(expectedExitValue);
    }

    private static void runJavaCheckExitValue(int expectedExitValue) throws Exception {
        runJavaCheckExitValue(createProcessBuilder(), expectedExitValue);
    }

    /*
     * Update environment variable in passed ProcessBuilder object to the passed value
     */
    private static void updateEnvironment(ProcessBuilder pb, String name, String value) {
        pb.environment().put(name, value);
    }

    /*
     * Check property value by examining output
     */
    private static void checkProperty(String property, String value) {
        outputShouldContain("Property " + property + "=" + value);
    }

    /*
     * Check VM Option value by examining output
     */
    private static void checkVMOption(String vmOption, String value) {
        outputShouldContain("VM Option " + vmOption + "=" + value);
    }

    private static void testVMOptions() throws Exception {
        StringBuilder longProperty = new StringBuilder();

        /* Check that empty VM Option file is accepted without errors */
        addVMOptionsFile(VM_OPTION_FILE_EMPTY);

        runJavaCheckExitValue(JVM_SUCCESS);

        /* Check that VM Option file with tabs and spaces is accepted without errors */
        addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_TABS_AND_SPACES));

        runJavaCheckExitValue(JVM_SUCCESS);

        /* Check that parameters are gotten from first VM Option file. Pass absolute path to the VM Option file */
        addVMParam("-showversion");
        addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_1));
        addPropertiesToCheck("optfile_1");
        addVMOptionsToCheck("SurvivorRatio", "MinHeapFreeRatio");

        runJavaCheckExitValue(JVM_SUCCESS);
        outputShouldContain("interpreted mode");
        checkProperty("optfile_1", "option_file_1");
        checkVMOption("SurvivorRatio", "16");
        checkVMOption("MinHeapFreeRatio", "22");

        /*
         * Check that parameters are gotten from second VM Option file which also contains flags file.
         * Flags file and option file contains NewRatio, but since options from VM Option file
         * are processed later NewRatio should be set to value from VM Option file
         * Pass relative path to the VM Option file in form "vmoptionfile"
         */
        addVMOptionsFile(VM_OPTION_FILE_2);
        addPropertiesToCheck("javax.net.ssl.keyStorePassword");
        addVMOptionsToCheck("UseGCOverheadLimit", "NewRatio", "MinHeapFreeRatio", "MaxFDLimit", "AlwaysPreTouch");

        runJavaCheckExitValue(JVM_SUCCESS);
        checkProperty("javax.net.ssl.keyStorePassword", "someVALUE123+");
        checkVMOption("UseGCOverheadLimit", "true");
        checkVMOption("NewRatio", "4");
        checkVMOption("MinHeapFreeRatio", "3");
        checkVMOption("MaxFDLimit", "true");
        checkVMOption("AlwaysPreTouch", "false");

        /* Check that parameters are gotten from third VM Option file which contains a mix of the options */
        addVMParam("-showversion");
        addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_3));
        addPropertiesToCheck("other.secret.data", "property");
        addVMOptionsToCheck("UseGCOverheadLimit", "NewRatio");

        runJavaCheckExitValue(JVM_SUCCESS);
        outputShouldContain("interpreted mode");
        checkProperty("other.secret.data", "qwerty");
        checkProperty("property", "second");
        checkVMOption("UseGCOverheadLimit", "false");
        checkVMOption("NewRatio", "16");

        /* Check that quotes are processed normally in VM Option file */
        addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_QUOTE));
        addPropertiesToCheck("my.quote.single", "my.quote.double", "javax.net.ssl.trustStorePassword");
        addVMOptionsToCheck("ErrorFile");

        runJavaCheckExitValue(JVM_SUCCESS);

        checkProperty("my.quote.single", "Property in single quote. Here a double qoute\" Add some slashes \\/");
        checkProperty("my.quote.double", "Double qoute. Include single '.");
        checkProperty("javax.net.ssl.trustStorePassword", "data @+NEW");
        checkVMOption("ErrorFile", "./my error file");

        /* Check that quotes are processed normally in VM Option file. Pass max size file */
        addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_QUOTE_MAX_SIZE));

        addPropertiesToCheck("big");

        runJavaCheckExitValue(JVM_SUCCESS);

        checkProperty("big", String.format("%01016d", 9));

        /*
         * Verify that VM Option file accepts a file with 65 properties and with two options on separate
         * lines and properties that use quotes a lot.
         */
        addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_LOT_OF_OPTIONS_QUOTE));
        for (int i = 1; i <= NUM_OF_PROP_IN_FILE_LOT_OF_OPTIONS_QUOTE; i++) {
            addPropertiesToCheck(String.format("prop%02d", i));
        }
        addVMOptionsToCheck("MinHeapFreeRatio", "MaxHeapFreeRatio");

        runJavaCheckExitValue(JVM_SUCCESS);

        for (int i = 1; i <= NUM_OF_PROP_IN_FILE_LOT_OF_OPTIONS_QUOTE; i++) {
            checkProperty(String.format("prop%02d", i), String.format("%02d", i));
        }
        checkVMOption("MinHeapFreeRatio", "7");
        checkVMOption("MaxHeapFreeRatio", "96");

        /*
         * Verify that VM Option file accepts a file with maximum allowed size(1024 bytes)
         */
        addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_WITH_LONG_PROPERTY));
        addPropertiesToCheck("very.very.long.property");

        runJavaCheckExitValue(JVM_SUCCESS);
        for (int i = 1; i < 249; i++) {
            longProperty.append("long");
        }
        longProperty.append("l");
        checkProperty("very.very.long.property", longProperty.toString());
    }

    private static ProcessBuilder prepareTestCase(int testCase) throws Exception {
        ProcessBuilder pb;

        Asserts.assertTrue(0 < testCase && testCase < 6, "testCase should be from 1 to 5");

        addVMParam("-showversion");
        addPropertiesToCheck("jto", "jo", "optfile_1", "shared.property");
        addVMOptionsToCheck("MinHeapFreeRatio", "SurvivorRatio", "NewRatio");

        if (testCase < 5) {
            addVMParam("-XX:Flags=flags_file");
            addProperty("shared.property", "command_line_before");
            addProperty("clb", "unique_command_line_before");
            addVMParam("-XX:MinHeapFreeRatio=7");
        }

        if (testCase < 4) {
            addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_1));
        }

        if (testCase < 3) {
            addVMParam("-XX:MinHeapFreeRatio=9");
            addProperty("shared.property", "command_line_after");
            addProperty("cla", "unique_command_line_after");
        }

        /* Create ProcessBuilder after all setup is done to update environment variables */
        pb = createProcessBuilder();

        if (testCase < 2) {
            updateEnvironment(pb, JAVA_OPTIONS, "-Dshared.property=somevalue -Djo=unique_java_options "
                    + "-XX:MinHeapFreeRatio=18 -Dshared.property=java_options -XX:MinHeapFreeRatio=11");
        }

        if (testCase < 6) {
            updateEnvironment(pb, JAVA_TOOL_OPTIONS, "-Dshared.property=qwerty -Djto=unique_java_tool_options "
                    + "-XX:MinHeapFreeRatio=15 -Dshared.property=java_tool_options -XX:MinHeapFreeRatio=6");
        }

        return pb;
    }

    private static void testVMOptionsLastArgumentsWins() throws Exception {
        ProcessBuilder pb;

        /*
         * "shared.property" property and "MinHeapFreeRatio" XX VM Option are defined
         * in flags file, JAVA_TOOL_OPTIONS and _JAVA_OPTIONS environment variables,
         * on command line before VM Option file, on command line after VM Option file
         * and also in VM Option file. Verify that last argument wins. Also check
         * unique properties and VM Options.
         * Here is the order of options processing and last argument wins:
         *    1) Flags file
         *    2) JAVA_TOOL_OPTIONS environment variables
         *    3) Pseudo command line from launcher
         *    4) _JAVA_OPTIONS
         * In every category arguments processed from left to right and from up to down
         * and the last processed arguments wins, i.e. if argument is defined several
         * times the value of argument will be equal to the last processed argument.
         *
         * "shared.property" property and "MinHeapFreeRatio" should be equal to the
         * value from _JAVA_OPTIONS environment variable
         */
        pb = prepareTestCase(1);

        runJavaCheckExitValue(pb, JVM_SUCCESS);

        outputShouldContain("interpreted mode");
        checkProperty("shared.property", "java_options");
        checkVMOption("MinHeapFreeRatio", "11");
        /* Each category defines its own properties */
        checkProperty("jto", "unique_java_tool_options");
        checkProperty("jo", "unique_java_options");
        checkProperty("clb", "unique_command_line_before");
        checkProperty("optfile_1", "option_file_1");
        checkProperty("cla", "unique_command_line_after");
        /* SurvivorRatio defined only in VM Option file */
        checkVMOption("SurvivorRatio", "16");
        /* NewRatio defined only in flags file */
        checkVMOption("NewRatio", "5");

        /*
         * The same as previous but without _JAVA_OPTIONS environment variable.
         * "shared.property" property and "MinHeapFreeRatio" should be equal to the
         * value from pseudo command line after VM Option file
         */
        pb = prepareTestCase(2);

        runJavaCheckExitValue(pb, JVM_SUCCESS);

        outputShouldContain("interpreted mode");
        checkProperty("shared.property", "command_line_after");
        checkVMOption("MinHeapFreeRatio", "9");

        /*
         * The same as previous but without arguments in pseudo command line after
         * VM Option file.
         * "shared.property" property and "MinHeapFreeRatio" should be equal to the
         * value from VM Option file.
         */
        pb = prepareTestCase(3);

        runJavaCheckExitValue(pb, JVM_SUCCESS);

        outputShouldContain("interpreted mode");
        checkProperty("shared.property", "vmoptfile");
        checkVMOption("MinHeapFreeRatio", "22");

        /*
         * The same as previous but without arguments in VM Option file.
         * "shared.property" property and "MinHeapFreeRatio" should be equal to the
         * value from pseudo command line.
         */
        pb = prepareTestCase(4);

        runJavaCheckExitValue(pb, JVM_SUCCESS);

        checkProperty("shared.property", "command_line_before");
        checkVMOption("MinHeapFreeRatio", "7");

        /*
         * The same as previous but without arguments from pseudo command line.
         * "shared.property" property and "MinHeapFreeRatio" should be equal to the
         * value from JAVA_TOOL_OPTIONS environment variable.
         */
        pb = prepareTestCase(5);

        runJavaCheckExitValue(pb, JVM_SUCCESS);

        checkProperty("shared.property", "java_tool_options");
        checkVMOption("MinHeapFreeRatio", "6");
    }

    private static void testVMOptionsInvalid() throws Exception {
        ProcessBuilder pb;

        /* Pass directory instead of file */
        addVMOptionsFile(CURRENT_DIR);

        runJavaCheckExitValue(JVM_FAIL_WITH_EXIT_CODE_1);

        /* Pass not existing file */
        addVMOptionsFile(getAbsolutePathFromSource(NOT_EXISTING_FILE));

        runJavaCheckExitValue(JVM_FAIL_WITH_EXIT_CODE_1);
        outputShouldContain("Could not open options file");

        /* Pass VM option file with bad option */
        addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_WITH_BAD_OPTION));

        runJavaCheckExitValue(JVM_FAIL_WITH_EXIT_CODE_1);
        outputShouldContain("Unrecognized VM option");

        /* Pass VM option file with same VM option file option in it */
        addVMOptionsFile(VM_OPTION_FILE_WITH_SAME_VM_OPTION_FILE);

        runJavaCheckExitValue(JVM_FAIL_WITH_EXIT_CODE_1);
        outputShouldContain("VM options file is only supported on the command line");

        /* Pass VM option file with VM option file option in it */
        addVMOptionsFile(VM_OPTION_FILE_WITH_VM_OPTION_FILE);

        runJavaCheckExitValue(JVM_FAIL_WITH_EXIT_CODE_1);
        outputShouldContain("VM options file is only supported on the command line");

        /* Pass VM option file with very long property(more than 1024 bytes) */
        addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_WITH_VERY_LONG_PROPERTY));

        runJavaCheckExitValue(JVM_FAIL_WITH_EXIT_CODE_1);
        outputShouldContain("Options file");
        outputShouldContain("is larger than 1024 bytes");

        /* Pass VM option file which is not accessible (without read permissions) */
        addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_WITHOUT_READ_PERMISSIONS));

        runJavaCheckExitValue(JVM_FAIL_WITH_EXIT_CODE_1);
        outputShouldContain("Could not open options file");

        /* Pass two VM option files */
        addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_1));
        addVMOptionsFile(VM_OPTION_FILE_2);

        runJavaCheckExitValue(JVM_FAIL_WITH_EXIT_CODE_1);
        outputShouldContain("Only one VM Options file is supported on the command line");

        /* Pass empty option file i.e. pass "-XX:VMOptionsFile=" */
        addVMOptionsFile("");

        runJavaCheckExitValue(JVM_FAIL_WITH_EXIT_CODE_1);
        outputShouldContain("Could not open options file");

        /* Pass VM option file with unmatched single quote */
        addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_UNMATCHED_QUOTE_1));

        runJavaCheckExitValue(JVM_FAIL_WITH_EXIT_CODE_1);
        outputShouldContain("Unmatched quote in");

        /* Pass VM option file with unmatched double quote in X option */
        addVMOptionsFile(getAbsolutePathFromSource(VM_OPTION_FILE_UNMATCHED_QUOTE_2));

        runJavaCheckExitValue(JVM_FAIL_WITH_EXIT_CODE_1);
        outputShouldContain("Unmatched quote in");
    }

    public static void main(String[] args) throws Exception {
        /*
         * Preparation before actual testing - create two VM Option files
         * which contains VM Option file in it and copy other files to the
         * current working folder
         */
        createOptionFiles();

        testVMOptions(); /* Test VM Option file general functionality */
        testVMOptionsLastArgumentsWins(); /* Verify that last argument wins */
        testVMOptionsInvalid(); /* Test invalid VM Option file functionality */

    }

    public static class PrintPropertyAndOptions {

        public static void main(String[] arguments) {
            String property;
            String vmOption;
            for (String arg : arguments) {
                if (arg.startsWith("property=")) {
                    property = arg.substring(9);
                    System.out.println("Property " + property + "=" + System.getProperty(property, "NOT DEFINED"));
                } else if (arg.startsWith("vmoption=")) {
                    vmOption = arg.substring(9);
                    System.out.println("VM Option " + vmOption + "=" + (new DynamicVMOption(vmOption)).getValue());
                }
            }
        }
    }
}
