/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class XcodeProjectMaker {
    private static final String JDK_SCRIPT_TOKEN_1 = "configure";
    private static final String JDK_SCRIPT_TOKEN_2 = ".jcheck";
    private static final String COMPILER_LINE_HEADER = "-I";
    private static final String COMPILER_IFRAMEWORK = "-iframework";
    private static final String COMPILER_FFRAMEWORK = "-F";
    private static final String SRC_HOTSPOT_PATH = "/src/hotspot";
    private static final String TEST_HOTSPOT_PATH = "/test/hotspot/gtest";
    private static final String ALIAS_JAVA_OLD = "java_old.sh";
    private static final String ALIAS_JAVA_NEW = "java_new.sh";
    private static final String JDK_BIN_JAVA = "/jdk/bin/java";
    private static final String FILE_TOKEN = "\"file\": ";
    private static final String COMMAND_TOKEN = "\"command\": ";
    private static final String QUOTE_START_TOKEN = "'\\\"";
    private static final String QUOTE_END_TOKEN = "\\\"'";
    private static final String VERSION = "2.0.0";
    private static final String EXCLUDE_PARSE_TOKEN_1 = "gtest";
    private static final String TEMPLATE_FRAMEWORK_SEARCH_PATHS = "TEMPLATE_FRAMEWORK_SEARCH_PATHS";
    private static final String TEMPLATE_OTHER_CFLAGS = "TEMPLATE_OTHER_CFLAGS";
    private static final String TEMPLATE_OTHER_LDFLAGS = "TEMPLATE_OTHER_LDFLAGS";
    private static final String TEMPLATE_USER_HEADER_SEARCH_PATHS = "TEMPLATE_USER_HEADER_SEARCH_PATHS";
    private static final String TEMPLATE_GROUP_GENSRC = "TEMPLATE_GROUP_GENSRC";
    private static final String TEMPLATE_GROUP_SRC = "TEMPLATE_GROUP_SRC";
    private static final String TEMPLATE_GROUP_TEST = "TEMPLATE_GROUP_TEST";
    private static final String TEMPLATE_GROUPS = "TEMPLATE_GROUPS";
    private static final String TEMPLATE_PBXBUILDFILE = "TEMPLATE_PBXBUILDFILE";
    private static final String TEMPLATE_PBXFILEREFERENCE = "TEMPLATE_PBXFILEREFERENCE";
    private static final String TEMPLATE_PBXSOURCESSBUILDPHASE = "TEMPLATE_PBXSOURCESSBUILDPHASE";
    private static final String TEMPLATE_JDK_PATH = "TEMPLATE_JDK_PATH";
    private static final String HOTSPOT_PBXPROJ = "hotspot.xcodeproj";
    private static final String PBXPROJ = "project.pbxproj";
    private static final String XCSAHAREDDATA = "xcshareddata";
    private static final String XCSCHEMES = "xcschemes";
    private static final String JVM_XCSCHEME = "jvm.xcscheme";
    private static final String J2D_XCSCHEME = "runJ2Demo.xcscheme";
    private static final String XCDEBUGGER = "xcdebugger";
    private static final String XCBKPTLIST = "Breakpoints_v2.xcbkptlist";
    private static final String TEMPLATE_PBXPROJ = PBXPROJ + ".template";
    private static final String TEMPLATE_JVM_XCSCHEME = JVM_XCSCHEME + ".template";
    private static final String TEMPLATE_J2D_XCSCHEME = J2D_XCSCHEME + ".template";
    private static final String TEMPLATE_XCBKPTLIST = XCBKPTLIST + ".template";
    private static final String[] EXCLUDE_FILES_PREFIX = {"."};
    private static final String[] EXCLUDE_FILES_POSTFIX = {".log", ".cmdline"};
    private static final String[] COMPILER_FLAGS_INCLUDE = {"-m", "-f", "-D", "-W"};
    private static final String[] COMPILER_FLAGS_IS = {"-g", "-Os", "-0"};
    private static final String[] COMPILER_FLAGS_EXCLUDE = {"-DTHIS_FILE", "-DGTEST_OS_MAC", "-mmacosx-version-min", "-Werror"}; // "-Werror" causes Xcode to stop compiling
    private static final int EXIT4 = -4;
    private static final int EXIT5 = -5;
    private static final int EXIT6 = -6;
    private static final int EXIT7 = -7;

    private final HashMap<String, ArrayList<String>> compiledFiles = new HashMap<>();
    private final TreeSet<String> compilerFlags = new TreeSet<>();
    private List<String> linkerFlags = List.of();
    private final TreeSet<String> headerPaths = new TreeSet<>();
    private final boolean debugLog;
    private String projectMakerDataPath = null;
    private String generatedHotspotPath = null;
    private String iframework = null;
    private String fframework = null;
    private DiskFile rootGensrc = new DiskFile("/", true);
    private DiskFile rootSrc = new DiskFile("/", true);
    private DiskFile rootTest = new DiskFile("/", true);

    public XcodeProjectMaker(boolean debugLog) {
        this.debugLog = debugLog;
    }

    public static void main(String[] args) {
        String workspaceRoot = args[0];
        String outputDir = args[1];
        String pathToProjectMakerData = args[2];
        String pathToCompileCommands = args[3];
        String pathToLinkerOptionsFile = args[4];
        String linkerOptionsString = readFile(pathToLinkerOptionsFile);
        boolean debugLog = args.length > 5 && args[5].equals("-d");

        File xcodeFolder = new File(outputDir);
        xcodeFolder.mkdirs();
        String workspaceRootPathFromOutputDir = findRelativePathToWorkspaceRoot(outputDir);

        if (debugLog) {
            System.out.println();
            System.out.println("Version " + VERSION);
            System.out.println();
            System.out.println("       Path to workspace root is \"" + workspaceRoot + "\"");
            System.out.println("Path to compile commands file is \"" + pathToCompileCommands + "\"");
            System.out.println(" Xcode project will be placed in \"" + outputDir + "\"");
            System.out.println();
        }

        XcodeProjectMaker maker = new XcodeProjectMaker(debugLog);
        maker.parseHotspotCompileCommands(pathToCompileCommands);
        maker.linkerFlags = List.of(linkerOptionsString.split(" "));
        maker.projectMakerDataPath = pathToProjectMakerData;

        maker.printLogDetails();

        maker.prepareFiles(workspaceRoot);
        maker.makeXcodeProj(outputDir, workspaceRootPathFromOutputDir);

        String pathToBuild = getFileParent(outputDir);
        maker.makeAliases(outputDir, pathToBuild);

        System.out.println();
        System.out.println("The Xcode project for hotspot was succesfully created");
        System.out.println("It can be found in '" + outputDir + "/" + HOTSPOT_PBXPROJ + "'");
        System.out.println();
    }

    // find a path to what looks like jdk
    private static String findRelativePathToWorkspaceRoot(String root) {
        String pathToWorkspaceRoot = null;
        String path = root;
        boolean found1 = false;
        boolean found2 = false;

        while (!found1 && !found2) {
            File folder = new File(path);
            File[] files = folder.listFiles();
            for (File file : files) {
                String fileName = file.toPath().getFileName().toString();
                if (fileName.equals(JDK_SCRIPT_TOKEN_1)) {
                    found1 = true;
                }
                if (fileName.equals(JDK_SCRIPT_TOKEN_2)) {
                    found2 = true;
                }
                if (found1 && found2) {
                    break;
                }
            }

            if (!found1 && !found2) {
                path = Paths.get(path).getParent().toString();
                if (pathToWorkspaceRoot == null) {
                    pathToWorkspaceRoot = "..";
                } else {
                    pathToWorkspaceRoot += "/..";
                }
            }
        }
        return pathToWorkspaceRoot;
    }

    private static String readFile(File file) {
        return readFile(file.toPath());
    }

    private static String readFile(String path) {
        return readFile(Paths.get(path));
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void writeFile(File file, String string) {
        writeFile(file.toPath(), string);
    }

    private static void writeFile(Path path, String string) {
        try {
            Files.writeString(path, string);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(EXIT4);
        }
    }

    private static boolean excludeFile(Path path) {
        return excludeFile(path.toString());
    }

    private static boolean excludeFile(String string) {
        return excludeFile(string, null);
    }

    private static boolean excludeFile(String string, String exclude) {
        if (exclude != null) {
            if (contains(string, exclude)) {
                return true;
            }
        }
        for (String excludeFilesPrefix : EXCLUDE_FILES_PREFIX) {
            if (string.startsWith(excludeFilesPrefix)) {
                return true;
            }
        }
        for (String excludeFilesPostfix : EXCLUDE_FILES_POSTFIX) {
            if (string.endsWith(excludeFilesPostfix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExcludeCompilerFlag(String string) {
        boolean flag = false;
        for (String exclude : COMPILER_FLAGS_EXCLUDE) {
            if (string.contains(exclude)) {
                flag = true;
                break;
            }
        }
        return flag;
    }

    private static boolean isCompilerFlag(String string) {
        boolean flag = false;
        for (String include : COMPILER_FLAGS_INCLUDE) {
            if (string.startsWith(include)) {
                flag = true;
                break;
            }
        }
        for (String is : COMPILER_FLAGS_IS) {
            if (string.equals(is)) {
                flag = true;
                break;
            }
        }
        if (isExcludeCompilerFlag(string)) {
            flag = false;
        }
        return flag;
    }

    private static String strip(String string) {
        return string.substring(2, string.length() - 1);
    }

    private static String strip(String string, String token) {
        int start = string.indexOf(token);
        int end = start + token.length();
        return strip(string.substring(end));
    }

    private static boolean contains(String string, String token) {
        return ((string.length() >= token.length()) && (string.contains(token)));
    }

    private static String getFileParent(String path) {
        return Paths.get(path).getParent().toString();
    }

    private static String extractPath(String string, String from, String to) {
        String result = null;
        String[] tokens = string.split("/");
        int i = 0;
        for (; i < tokens.length; i++) {
            if (tokens[i].equals(from)) {
                result = "";
                break;
            }
        }
        for (; i < tokens.length; i++) {
            result += "/" + tokens[i];
            if (tokens[i].equals(to)) {
                break;
            }
        }
        return result;
    }

    private void extractCommonCompilerFlags() {
        // heuristic, find average count of number of flags used by each compiled file
        int countFiles = 0;
        int countFlags = 0;

        for (Map.Entry<String, ArrayList<String>> entry : this.compiledFiles.entrySet()) {
            countFiles++;
            List<String> flags = entry.getValue();
            countFlags += flags.size();
        }

        // when finding common flags, only consider files with this many flags
        int flagCutoff = (countFlags / countFiles) / 2;

        // collect all flags
        for (Map.Entry<String, ArrayList<String>> entry : this.compiledFiles.entrySet()) {
            List<String> flags = entry.getValue();
            if (flags.size() > flagCutoff) {
                this.compilerFlags.addAll(flags);
            }
        }

        // find flags to remove
        Set<String> removeFlags = new TreeSet<>();
        for (Map.Entry<String, ArrayList<String>> entry : this.compiledFiles.entrySet()) {
            List<String> flags = entry.getValue();
            if (flags.size() > flagCutoff) {
                for (String common : this.compilerFlags) {
                    if (!flags.contains(common)) {
                        removeFlags.add(common);
                    }
                }
            }
        }

        // leave only common flags
        for (String flag : removeFlags) {
            this.compilerFlags.remove(flag);
        }

        // remove common flags from each compiler file, leaving only the unique ones
        for (Map.Entry<String, ArrayList<String>> entry : this.compiledFiles.entrySet()) {
            List<String> flags = entry.getValue();
            if (flags.size() > flagCutoff) {
                for (String common : this.compilerFlags) {
                    flags.remove(common);
                }
            }
        }
    }

    private void extractCompilerFlags(String line) {
        boolean verboseCompilerTokens = false;
        String file = null;
        ArrayList<String> flags = null;

        String[] commands = line.split(",");
        for (String command : commands) {
            if (contains(command, FILE_TOKEN)) {
                file = strip(command, FILE_TOKEN);
                //verbose_compiler_tokens = Contains(file, "vm_version.cpp");
            } else if (contains(command, COMMAND_TOKEN)) {
                String tokens = strip(command, COMMAND_TOKEN);
                String[] arguments = tokens.split(" ");
                if (arguments.length >= 3) {
                    flags = new ArrayList<>();
                    for (int a = 2; a < arguments.length; a++) {
                        String argument = arguments[a];
                        if (isCompilerFlag(argument)) {
                            // catch argument like -DVMTYPE=\"Minimal\"
                            if (contains(argument, "\\\\\\\"") && argument.endsWith("\\\\\\\"")) {
                                // TODO: more robust fix needed here
                                argument = argument.replace("\\", "");
                                argument = argument.replaceFirst("\"", "~.~"); // temp token ~.~
                                argument = argument.replace("\"", "\\\"'");
                                argument = argument.replace("~.~", "'\\\"");
                            }

                            // argument like -DHOTSPOT_VM_DISTRO='\"Java HotSpot(TM)\"'
                            // gets split up, so reconstruct as single string
                            if (contains(argument, QUOTE_START_TOKEN) && !argument.endsWith(QUOTE_END_TOKEN)) {
                                String fullArgument = argument;
                                do {
                                    ++a;
                                    argument = arguments[a];
                                    fullArgument = fullArgument + " " + argument;
                                } while (!argument.endsWith(QUOTE_END_TOKEN));
                                argument = fullArgument;
                            }
                            flags.add(argument);
                            if (verboseCompilerTokens) {
                                System.out.println("    FOUND COMPILER FLAG: " + argument);
                            }
                        } else if (argument.startsWith(COMPILER_LINE_HEADER)) {
                            this.headerPaths.add(argument.substring(2));
                        } else if (argument.equals(COMPILER_IFRAMEWORK)) {
                            if (iframework == null) {
                                ++a;
                                this.iframework = arguments[a]; // gets the value, so skip it for the next loop
                            }
                        } else if (argument.equals(COMPILER_FFRAMEWORK)) {
                            if (fframework == null) {
                                ++a;
                                this.fframework = arguments[a]; // gets the value, so skip it for the next loop
                            }
                        }
                    }
                }
            }
        }

        if ((file != null) && (flags != null)) {
            this.compiledFiles.put(file, flags);
        } else {
            System.err.println(" WARNING: extractCompilerFlags returns file:" + file + ", flags:" + flags);
        }

        if (verboseCompilerTokens) {
            System.exit(0);
        }
    }

    public void parseHotspotCompileCommands(String path) {
        String content = readFile(path);
        String[] parts = content.split("\\{"); // }

        int found = 0;
        for (String line : parts) {
            if (!contains(line, EXCLUDE_PARSE_TOKEN_1) && !line.startsWith("[")) {
                extractCompilerFlags(line);
                found++;
            }
        }
        if (debugLog) {
            System.out.println("Found total of " + found + " files that make up the libjvm.dylib");
        }
        extractCommonCompilerFlags();

        // figure out "gensrc" folder
        // from: "/Users/gerard/Desktop/jdk_test/jdk10/build/macosx-x86_64-normal-server-fastdebug/hotspot/variant-server/gensrc/adfiles/ad_x86_clone.cpp"
        // to:   "/build/macosx-x86_64-normal-server-fastdebug/hotspot/variant-server/gensrc"
        for (Map.Entry<String, ArrayList<String>> entry : this.compiledFiles.entrySet()) {
            String file = entry.getKey();
            if (file.contains("gensrc")) {
                this.generatedHotspotPath = extractPath(file, "build", "gensrc");
                //generatedHotspotPath = "/build/macosx-x64/hotspot/variant-server/gensrc";
                //generatedHotspotPath = "/build/macosx-x86_64-normal-server-fastdebug/hotspot/variant-server/gensrc";
            }
        }
    }

    // https://docs.oracle.com/javase/tutorial/displayCode.html?code=https://docs.oracle.com/javase/tutorial/essential/io/examples/Copy.java
    private DiskFile getHotspotFiles(DiskFile root, String workspaceRoot, String hotspotPath) {
        File file = new File(workspaceRoot + "/" + hotspotPath);
        if (!file.exists()) {
            return null;
        }

        try {
            final Path rootDir = Paths.get(workspaceRoot + hotspotPath);
            Files.walkFileTree(rootDir, new HotspotFileVisitor(root, hotspotPath));
        } catch (IOException ex) {
            System.err.println("ex: " + ex);
        }

        return root;
    }

    public void prepareFiles(String workspaceRoot) {
        this.rootGensrc = getHotspotFiles(this.rootGensrc, workspaceRoot, this.generatedHotspotPath);
        this.rootSrc = getHotspotFiles(this.rootSrc, workspaceRoot, SRC_HOTSPOT_PATH);
        this.rootTest = getHotspotFiles(this.rootTest, workspaceRoot, TEST_HOTSPOT_PATH);

        // make a copy of files from the log
        Set<String> logFiles = new TreeSet<>(this.compiledFiles.keySet());

        int totalMarkedFiles = 0;
        DiskFile[] roots = { this.rootGensrc, this.rootSrc };
        for (DiskFile root : roots) {
            List<DiskFile> diskFiles = root.getFiles();
            for (DiskFile diskFile : diskFiles) {
                if (!diskFile.isDirectory()) {
                    String logFileProcessed = null;
                    String diskFilePath = diskFile.getFilePath();
                    for (String logFilePath : logFiles) {
                        if (contains(logFilePath, diskFilePath)) {
                            totalMarkedFiles++;

                            logFileProcessed = logFilePath;

                            // mark the file as needing compilation
                            diskFile.markAsCompiled(this.compiledFiles.get(logFilePath));

                            // break early if found
                            break;
                        }
                    }
                    if (logFileProcessed != null) {
                        // remove the file, so we don't have to search through it again
                        logFiles.remove(logFileProcessed);
                    }
                }
            }
        }

        if (this.compiledFiles.size() != totalMarkedFiles) {
            System.err.println("\nError: was expecting to compile " + this.compiledFiles.size() + " files, but marked " + totalMarkedFiles);
            for (String file : logFiles) {
                System.err.println("file: " + file);
            }
            System.exit(EXIT5);
        }

        if (!logFiles.isEmpty()) {
            System.err.println("\nError: unprocessed files left over:");
            for (String logFile : logFiles) {
                System.err.println("  " + logFile);
            }
            System.exit(EXIT6);
        }
    }

    public void printLogDetails() {
        if (!debugLog) return;

        System.out.println("\nFound " + this.compilerFlags.size() + " common compiler flags:");
        for (String flag : this.compilerFlags) {
            System.out.println(" " + flag);
        }

        System.out.println("\nList of compiled files (each one uses common compiler flags plus extra ones as specified):");
        int count = 1;
        for (Map.Entry<String, ArrayList<String>> entry : this.compiledFiles.entrySet()) {
            String file = entry.getKey();
            System.out.format("%4d: %s\n", (count), file);
            count++;
            List<String> flags = entry.getValue();
            for (String flag : flags) {
                System.out.println("        " + flag);
            }
        }

        System.out.println("\nFound " + this.linkerFlags.size() + " linker flags:");
        for (String flag : this.linkerFlags) {
            System.out.println(" " + flag);
        }

        System.out.println("\nFound " + this.headerPaths.size() + " header paths:");
        for (String header : this.headerPaths) {
            System.out.println(" " + header);
        }

        System.out.println("\nFrameworks:");
        System.out.println(" -iframework " + iframework);
        System.out.println(" -f " + fframework);
    }

    private String makeProjectPbxproj(String workspaceRootPathFromOutputDir, String string) {
        String cFlags = "";
        for (String flag : this.compilerFlags) {
            cFlags += "          \"" + flag.replace("\"", "\\\\\"") + "\",\n";
        }
        cFlags = cFlags.substring(0, cFlags.length() - 2);
        string = string.replaceFirst(TEMPLATE_OTHER_CFLAGS, cFlags);

        String ldFlags = "";
        for (String flag : this.linkerFlags) {
            ldFlags += "          \"" + flag + "\",\n";
        }
        ldFlags = ldFlags.substring(0, ldFlags.length() - 2);
        string = string.replaceFirst(TEMPLATE_OTHER_LDFLAGS, ldFlags);

        String headerPaths = "";
        for (String header : this.headerPaths) {
            headerPaths += "          \"" + header + "\",\n";
        }
        headerPaths = headerPaths.substring(0, headerPaths.length() - 2);
        string = string.replaceFirst(TEMPLATE_USER_HEADER_SEARCH_PATHS, headerPaths);

        String frameworkPaths = "";
        if (fframework != null) {
            frameworkPaths += "          \"" + fframework + "\"\n";
        }
        string = string.replaceFirst(TEMPLATE_FRAMEWORK_SEARCH_PATHS, frameworkPaths);

        DiskFile gensrcFile = this.rootGensrc.getChild("gensrc");
        string = string.replaceFirst(TEMPLATE_GROUP_GENSRC, "        " + gensrcFile.getXcodeId());

        DiskFile srcFile = this.rootSrc.getChild("src");
        string = string.replaceFirst(TEMPLATE_GROUP_SRC, "        " + srcFile.getXcodeId());

        DiskFile testFile = this.rootTest.getChild("test");
        string = string.replaceFirst(TEMPLATE_GROUP_TEST, "        " + testFile.getXcodeId());

        String gensrcGroups = gensrcFile.generatePbxGroup();
        String srcGroups = srcFile.generatePbxGroup();
        String testGroups = testFile.generatePbxGroup();
        string = string.replaceFirst(TEMPLATE_GROUPS, gensrcGroups + srcGroups + testGroups);

        String gensrcFiles = gensrcFile.generatePbxFileReference(workspaceRootPathFromOutputDir);
        String srcFiles = srcFile.generatePbxFileReference(workspaceRootPathFromOutputDir);
        String testFiles = testFile.generatePbxFileReference(workspaceRootPathFromOutputDir);
        string = string.replaceFirst(TEMPLATE_PBXFILEREFERENCE, gensrcFiles + srcFiles + testFiles);

        String gensrcCompiled = gensrcFile.generatePbxBuildFile();
        String compiled = srcFile.generatePbxBuildFile();
        string = string.replaceFirst(TEMPLATE_PBXBUILDFILE, gensrcCompiled + compiled);

        String gensrcBuilt = gensrcFile.generatePbxSourcesBuildPhase();
        String built = srcFile.generatePbxSourcesBuildPhase();
        string = string.replaceFirst(TEMPLATE_PBXSOURCESSBUILDPHASE, gensrcBuilt + built);

        return string;
    }

    private String makeTemplateXcscheme(String outputDir, String string) {
        string = string.replaceAll(TEMPLATE_JDK_PATH, outputDir);

        return string;
    }

    public void makeXcodeProj(String outputDir, String workspaceRootPathFromOutputDir) {
    /*
     jvm.xcodeproj                     <-- folder
       project.pbxproj                 <-- file
       xcshareddata                    <-- folder
         xcschemes                     <-- folder
           jvm.xcscheme                <-- file
         xcdebugger                    <-- folder
           Breakpoints_v2.xcbkptlist   <-- file
     */
        File xcodeDir = new File(outputDir);
        File jvmXcodeprojDir = new File(xcodeDir, HOTSPOT_PBXPROJ);
        File projectPbxprojFile = new File(jvmXcodeprojDir, PBXPROJ);
        File xcshareddataDir = new File(jvmXcodeprojDir, XCSAHAREDDATA);
        File xcschemesDir = new File(xcshareddataDir, XCSCHEMES);
        File jvmXcschemeFile = new File(xcschemesDir, JVM_XCSCHEME);
        File j2DemoXcschemeFile = new File(xcschemesDir, J2D_XCSCHEME);
        File xcdebuggerDir = new File(xcshareddataDir, XCDEBUGGER);
        File jBreakpointsV2XcbkptlistFile = new File(xcdebuggerDir, XCBKPTLIST);

        if (xcodeDir.exists()) {
            xcodeDir.delete();
        }

        jvmXcodeprojDir.mkdirs();
        xcshareddataDir.mkdirs();
        xcschemesDir.mkdirs();
        xcdebuggerDir.mkdirs();

        File dataDir = new File(projectMakerDataPath);
        File templateProjectPbxprojFile = new File(dataDir, TEMPLATE_PBXPROJ);
        File templateJvmXcschemeFile = new File(dataDir, TEMPLATE_JVM_XCSCHEME);
        File templateJ2DemoXcschemeFile = new File(dataDir, TEMPLATE_J2D_XCSCHEME);
        File templateJBreakpointsV2XcbkptlistFile = new File(dataDir, TEMPLATE_XCBKPTLIST);

        String projectPbxprojString = readFile(templateProjectPbxprojFile);
        String jvmXcschemeString = readFile(templateJvmXcschemeFile);
        String j2DemoXcschemeString = readFile(templateJ2DemoXcschemeFile);
        String jBreakpointsV2XcbkptlistString = readFile(templateJBreakpointsV2XcbkptlistFile);

        writeFile(projectPbxprojFile, makeProjectPbxproj(workspaceRootPathFromOutputDir, projectPbxprojString));
        writeFile(jvmXcschemeFile, makeTemplateXcscheme(outputDir, jvmXcschemeString));
        writeFile(j2DemoXcschemeFile, makeTemplateXcscheme(outputDir, j2DemoXcschemeString));
        writeFile(jBreakpointsV2XcbkptlistFile, jBreakpointsV2XcbkptlistString);
    }

    public void makeAliases(String outputDir, String pathToBuild) {
        File xcodeDir = new File(outputDir);
        File jdkOldSh = new File(xcodeDir, ALIAS_JAVA_OLD);
        File jdkNewSh = new File(xcodeDir, ALIAS_JAVA_NEW);

        writeFile(jdkOldSh, "#!/bin/bash\n" + pathToBuild + JDK_BIN_JAVA + " $@");
        writeFile(jdkNewSh, "#!/bin/bash\n" + outputDir + "/build" + JDK_BIN_JAVA + " $@");

        try {
            Set<PosixFilePermission> permissions = new HashSet<>();
            permissions.add(PosixFilePermission.OWNER_READ);
            permissions.add(PosixFilePermission.OWNER_WRITE);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_READ);
            permissions.add(PosixFilePermission.OTHERS_READ);
            Files.setPosixFilePermissions(jdkOldSh.toPath(), permissions);
            Files.setPosixFilePermissions(jdkNewSh.toPath(), permissions);
        } catch (IOException ex) {
            System.err.println("Warning: unable to change file permissions");
            System.err.println(ex);
        }
    }

    private static class HotspotFileVisitor implements FileVisitor<Path> {
        private final DiskFile root;
        private final String hotspotPath;

        public HotspotFileVisitor(DiskFile root, String hotspotPath) {
            this.root = root;
            this.hotspotPath = hotspotPath;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
            if (excludeFile(path)) {
                return FileVisitResult.SKIP_SUBTREE;
            } else {
                // consider folders based on their names
                Path file = path.getFileName();
                if (!excludeFile(file)) {
                    root.addDirectory(path, hotspotPath);
                    return FileVisitResult.CONTINUE;
                } else {
                    // skip folders with names beginning with ".", etc
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
        }

        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
            Path file = path.getFileName();
            if (!excludeFile(file)) {
                //System.err.println(path.toString());
                root.addFile(path, hotspotPath);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path path, IOException exc) {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path path, IOException exc) {
            if (exc instanceof FileSystemLoopException) {
                System.err.println("cycle detected: " + path);
            } else {
                System.err.format("Unable to process: %s: %s\n", path, exc);
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
