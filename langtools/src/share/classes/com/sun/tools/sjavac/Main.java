/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.sjavac;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.Files;

import com.sun.tools.sjavac.options.Options;
import com.sun.tools.sjavac.options.SourceLocation;
import com.sun.tools.sjavac.server.JavacService;
import com.sun.tools.sjavac.server.JavacServer;
import com.sun.tools.sjavac.server.JavacServiceClient;

/**
 * The main class of the smart javac wrapper tool.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class Main {

    /*  This is a smart javac wrapper primarily used when building the OpenJDK,
        though other projects are welcome to use it too. But please be aware
        that it is not an official api and will change in the future.
        (We really mean it!)

        Goals:

        ** Create a state file, containing information about the build, so
           that incremental builds only rebuild what is necessary. Also the
           state file can be used by make/ant to detect when to trigger
           a call to the smart javac wrapper.

           This file is called bin/javac_state (assuming that you specified "-d bin")
           Thus the simplest makefile is:

           SJAVAC=java -cp .../tools.jar com.sun.tools.sjavac.Main
           SRCS=$(shell find src -name "*.java")
           bin/javac_state : $(SRCS)
                  $(SJAVAC) src -d bin

           This makefile will run very fast and detect properly when Java code needs to
           be recompiled. The smart javac wrapper will then use the information in java_state
           to do an efficient incremental compile.

           Previously it was near enough impossible to write an efficient makefile for Java
           with support for incremental builds and dependency tracking.

        ** Separate java sources to be compiled from java
           sources used >only< for linking. The options:

           "dir" points to root dir with sources to be compiled
           "-sourcepath dir" points to root dir with sources used only for linking
           "-classpath dir" points to dir with classes used only for linking (as before)

        ** Use all cores for compilation by default.
           "-j 4" limit the number of cores to 4.
           For the moment, the sjavac server additionally limits the number of cores to three.
           This will improve in the future when more sharing is performed between concurrent JavaCompilers.

        ** Basic translation support from other sources to java, and then compilation of the generated java.
           This functionality might be moved into annotation processors instead.
           Again this is driven by the OpenJDK sources where properties and a few other types of files
           are converted into Java sources regularily. The javac_state embraces copy and tr, and perform
           incremental recompiles and copying for these as well. META-INF will be a special copy rule
           that will copy any files found below any META-INF dir in src to the bin/META-INF dir.
           "-copy .gif"
           "-copy META-INF"
           "-tr .prop=com.sun.tools.javac.smart.CompileProperties
           "-tr .propp=com.sun.tools.javac.smart.CompileProperties,java.util.ListResourceBundle
           "-tr .proppp=com.sun.tools.javac.smart.CompileProperties,sun.util.resources.LocaleNamesBundle

        ** Control which classes in the src,sourcepath and classpath that javac is allowed to see.
           Again, this is necessary to deal with the source code structure of the OpenJDK which is
           intricate (read messy).

           "-i tools.*" to include the tools package and all its subpackages in the build.
           "-x tools.net.aviancarrier.*" to exclude the aviancarrier package and all its sources and subpackages.
           "-x tools.net.drums" to exclude the drums package only, keep its subpackages.
           "-xf tools/net/Bar.java" // Do not compile this file...
           "-xf *Bor.java" // Do not compile Bor.java wherever it is found, BUT do compile ABor.java!
           "-if tools/net/Bor.java" // Only compile this file...odd, but sometimes used.

        ** The smart javac wrapper is driven by the modification time on the source files and compared
           to the modification times written into the javac_state file.

           It does not compare the modification time of the source with the modification time of the artifact.
           However it will detect if the modification time of an artifact has changed compared to the java_state,
           and this will trigger a delete of the artifact and a subsequent recompile of the source.

           The smart javac wrapper is not a generic makefile/ant system. Its purpose is to compile java source
           as the final step before the output dir is finalized and immediately jared, or jmodded. The output
           dir should be considered opaque. Do not write into the outputdir yourself!
           Any artifacts found in the outputdir that javac_state does not know of, will be deleted!
           This can however be prevented, using the switch --permit-unidentified-artifacts
           This switch is necessary when build the OpenJDK because its makefiles still write directly to
           the output classes dirs.

           Any makefile/ant rules that want to put contents into the outputdir should put the content
           in one of several source roots. Static content that is under version control, can be put in the same source
           code tree as the Java sources. Dynamic content that is generated by make/ant on the fly, should
           be put in a separate gensrc_stuff root. The smart javac wrapper call will then take the arguments:
           "gensrc_stuff src -d bin"

        The command line:
        java -cp tools.jar com.sun.tools.sjavac.Main \
             -i "com.bar.*" -x "com.bar.foo.*" \
             first_root \
             -i "com.bar.foo.*" \
             second_root \
             -x "org.net.*" \
             -sourcepath link_root_sources \
             -classpath link_root_classes \
             -d bin

        Will compile all sources for package com.bar and its subpackages, found below first_root,
        except the package com.bar.foo (and its subpackages), for which the sources are picked
        from second_root instead. It will link against classes in link_root_classes and against
        sources in link_root_sources, but will not see (try to link against) sources matching org.net.*
        but will link against org.net* classes (if they exist) in link_root_classes.

        (If you want a set of complex filter rules to be applied to several source directories, without
         having to repeat the the filter rules for each root. You can use the explicit -src option. For example:
         sjavac -x "com.foo.*" -src root1:root2:root3  )

        The resulting classes are written into bin.
    */

    private JavacState javac_state;

    public static void main(String... args)  {
        if (args.length > 0 && args[0].startsWith("--startserver:")) {
            if (args.length>1) {
                Log.error("When spawning a background server, only a single --startserver argument is allowed.");
                return;
            }
            // Spawn a background server.
            int rc = JavacServer.startServer(args[0], System.err);
            System.exit(rc);
        }
        Main main = new Main();
        int rc = main.go(args, System.out, System.err);
        // Remove the portfile, but only if this background=false was used.
        JavacServer.cleanup(args);
        System.exit(rc);
    }

    private void printHelp() {
        System.out.println("Usage: sjavac <options>\n"+
                           "where required options are:\n"+
                           "dir                        Compile all sources in dir recursively\n"+
                           "-d dir                     Store generated classes here and the javac_state file\n"+
                           "--server:portfile=/tmp/abc Use a background sjavac server\n\n"+
                           "All other arguments as javac, except -implicit:none which is forced by default.\n"+
                           "No java source files can be supplied on the command line, nor can an @file be supplied.\n\n"+
                           "Warning!\n"+
                           "This tool might disappear at any time, and its command line options might change at any time!");
    }

    public int go(String[] args, PrintStream out, PrintStream err) {

        Log.initializeLog(out, err);

        Options options;
        try {
            options = Options.parseArgs(args);
        } catch (IllegalArgumentException e) {
            Log.error(e.getMessage());
            return -1;
        }

        Log.setLogLevel(options.getLogLevel());

        if (!validateOptions(options))
            return -1;

        if (!createIfMissing(options.getDestDir()))
            return -1;

        Path gensrc = options.getGenSrcDir();
        if (gensrc != null && !createIfMissing(gensrc))
            return -1;

        Path hdrdir = options.getHeaderDir();
        if (hdrdir != null && !createIfMissing(hdrdir))
            return -1;

        // Load the prev build state database.
        javac_state = JavacState.load(options, out, err);

        // Setup the suffix rules from the command line.
        Map<String, Transformer> suffixRules = new HashMap<>();

        // Handling of .java-compilation
        suffixRules.putAll(javac_state.getJavaSuffixRule());

        // Handling of -copy and -tr
        suffixRules.putAll(options.getTranslationRules());

        // All found modules are put here.
        Map<String,Module> modules = new HashMap<>();
        // We start out in the legacy empty no-name module.
        // As soon as we stumble on a module-info.java file we change to that module.
        Module current_module = new Module("", "");
        modules.put("", current_module);

        // Find all sources, use the suffix rules to know which files are sources.
        Map<String,Source> sources = new HashMap<>();

        // Find the files, this will automatically populate the found modules
        // with found packages where the sources are found!
        findSourceFiles(options.getSources(),
                        suffixRules.keySet(),
                        sources,
                        modules,
                        current_module,
                        options.isDefaultPackagePermitted(),
                        false);

        if (sources.isEmpty()) {
            Log.error("Found nothing to compile!");
            return -1;
        }

        // Create a map of all source files that are available for linking. Both -src and
        // -sourcepath point to such files. It is possible to specify multiple
        // -sourcepath options to enable different filtering rules. If the
        // filters are the same for multiple sourcepaths, they may be concatenated
        // using :(;). Before sending the list of sourcepaths to javac, they are
        // all concatenated. The list created here is used by the SmartFileWrapper to
        // make sure only the correct sources are actually available.
        // We might find more modules here as well.
        Map<String,Source> sources_to_link_to = new HashMap<>();

        List<SourceLocation> sourceResolutionLocations = new ArrayList<>();
        sourceResolutionLocations.addAll(options.getSources());
        sourceResolutionLocations.addAll(options.getSourceSearchPaths());
        findSourceFiles(sourceResolutionLocations,
                        Collections.singleton(".java"),
                        sources_to_link_to,
                        modules,
                        current_module,
                        options.isDefaultPackagePermitted(),
                        true);

        // Find all class files allowable for linking.
        // And pickup knowledge of all modules found here.
        // This cannot currently filter classes inside jar files.
//      Map<String,Source> classes_to_link_to = new HashMap<String,Source>();
//      findFiles(args, "-classpath", Util.set(".class"), classes_to_link_to, modules, current_module, true);

        // Find all module sources allowable for linking.
//      Map<String,Source> modules_to_link_to = new HashMap<String,Source>();
//      findFiles(args, "-modulepath", Util.set(".class"), modules_to_link_to, modules, current_module, true);

        // Add the set of sources to the build database.
        javac_state.now().flattenPackagesSourcesAndArtifacts(modules);
        javac_state.now().checkInternalState("checking sources", false, sources);
        javac_state.now().checkInternalState("checking linked sources", true, sources_to_link_to);
        javac_state.setVisibleSources(sources_to_link_to);

        // If there is any change in the source files, taint packages
        // and mark the database in need of saving.
        javac_state.checkSourceStatus(false);

        // Find all existing artifacts. Their timestamp will match the last modified timestamps stored
        // in javac_state, simply because loading of the JavacState will clean out all artifacts
        // that do not match the javac_state database.
        javac_state.findAllArtifacts();

        // Remove unidentified artifacts from the bin, gensrc and header dirs.
        // (Unless we allow them to be there.)
        // I.e. artifacts that are not known according to the build database (javac_state).
        // For examples, files that have been manually copied into these dirs.
        // Artifacts with bad timestamps (ie the on disk timestamp does not match the timestamp
        // in javac_state) have already been removed when the javac_state was loaded.
        if (!options.isUnidentifiedArtifactPermitted()) {
            javac_state.removeUnidentifiedArtifacts();
        }
        // Go through all sources and taint all packages that miss artifacts.
        javac_state.taintPackagesThatMissArtifacts();

        // Now clean out all known artifacts belonging to tainted packages.
        javac_state.deleteClassArtifactsInTaintedPackages();
        // Copy files, for example property files, images files, xml files etc etc.
        javac_state.performCopying(Util.pathToFile(options.getDestDir()), suffixRules);
        // Translate files, for example compile properties or compile idls.
        javac_state.performTranslation(Util.pathToFile(gensrc), suffixRules);
        // Add any potentially generated java sources to the tobe compiled list.
        // (Generated sources must always have a package.)
        Map<String,Source> generated_sources = new HashMap<>();

        try {

            Source.scanRoot(Util.pathToFile(options.getGenSrcDir()), Util.set(".java"), null, null, null, null,
                    generated_sources, modules, current_module, false, true, false);
            javac_state.now().flattenPackagesSourcesAndArtifacts(modules);
            // Recheck the the source files and their timestamps again.
            javac_state.checkSourceStatus(true);

            // Now do a safety check that the list of source files is identical
            // to the list Make believes we are compiling. If we do not get this
            // right, then incremental builds will fail with subtility.
            // If any difference is detected, then we will fail hard here.
            // This is an important safety net.
            javac_state.compareWithMakefileList(Util.pathToFile(options.getSourceReferenceList()));

            // Do the compilations, repeatedly until no tainted packages exist.
            boolean again;
            // Collect the name of all compiled packages.
            Set<String> recently_compiled = new HashSet<>();
            boolean[] rc = new boolean[1];
            do {
                // Clean out artifacts in tainted packages.
                javac_state.deleteClassArtifactsInTaintedPackages();
                // Create a JavacService to delegate the actual compilation to.
                // Currently sjavac always connects to a server through a socket
                // regardless if sjavac runs as a background service or not.
                // This will most likely change in the future.
                JavacService javacService = new JavacServiceClient(options.getServerConf());
                again = javac_state.performJavaCompilations(javacService, options, recently_compiled, rc);
                if (!rc[0]) break;
            } while (again);
            // Only update the state if the compile went well.
            if (rc[0]) {
                javac_state.save();
                // Reflatten only the artifacts.
                javac_state.now().flattenArtifacts(modules);
                // Remove artifacts that were generated during the last compile, but not this one.
                javac_state.removeSuperfluousArtifacts(recently_compiled);
            }
            return rc[0] ? 0 : -1;
        } catch (ProblemException e) {
            Log.error(e.getMessage());
            return -1;
        } catch (Exception e) {
            e.printStackTrace(err);
            return -1;
        }
    }

    private static boolean validateOptions(Options options) {

        String err = null;

        if (options.getDestDir() == null) {
            err = "Please specify output directory.";
        } else if (options.isJavaFilesAmongJavacArgs()) {
            err = "Sjavac does not handle explicit compilation of single .java files.";
        } else if (options.getServerConf() == null) {
            err = "No server configuration provided.";
        } else if (!options.getImplicitPolicy().equals("none")) {
            err = "The only allowed setting for sjavac is -implicit:none";
        } else if (options.getSources().isEmpty()) {
            err = "You have to specify -src.";
        } else if (options.getTranslationRules().size() > 1
                && options.getGenSrcDir() == null) {
            err = "You have translators but no gensrc dir (-s) specified!";
        }

        if (err != null)
            Log.error(err);

        return err == null;

    }

    private static boolean createIfMissing(Path dir) {

        if (Files.isDirectory(dir))
            return true;

        if (Files.exists(dir)) {
            Log.error(dir + " is not a directory.");
            return false;
        }

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Log.error("Could not create directory: " + e.getMessage());
            return false;
        }

        return true;
    }


    /** Find source files in the given source locations. */
    public static void findSourceFiles(List<SourceLocation> sourceLocations,
                                       Set<String> sourceTypes,
                                       Map<String,Source> foundFiles,
                                       Map<String, Module> foundModules,
                                       Module currentModule,
                                       boolean permitSourcesInDefaultPackage,
                                       boolean inLinksrc) {

        for (SourceLocation source : sourceLocations) {
            source.findSourceFiles(sourceTypes,
                                   foundFiles,
                                   foundModules,
                                   currentModule,
                                   permitSourcesInDefaultPackage,
                                   inLinksrc);
        }
    }
}
