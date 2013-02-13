/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sun.tools.sjavac.server.JavacServer;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

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

    // This is the final destination for classes and copied files.
    private File bin_dir;
    // This is where the annotation process will put generated sources.
    private File gensrc_dir;
    // This is where javac -h puts the generated c-header files.
    private File header_dir;

    // This file contains the list of sources genereated by the makefile.
    // We double check that our calculated list of sources matches this list,
    // if not, then we terminate with an error!
    private File makefile_source_list;
    // The challenging task to manage an incremental build is done by javac_state.
    private JavacState javac_state;

    // The suffix rules tells you for example, that .java files should be compiled,
    // and .html files should be copied and .properties files be translated.
    Map<String,Transformer> suffix_rules;

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
        try {
            if (args.length == 0 || findJavaSourceFiles(args) || findAtFile(args) || null==Util.findServerSettings(args)) {
                printHelp();
                return 0;
            }

            Log.setLogLevel(findLogLevel(args), out, err);
            String server_settings = Util.findServerSettings(args);
            args = verifyImplicitOption(args);
            // Find the source root directories, and add the -src option before these, if not there already.
            args = addSrcBeforeDirectories(args);
            // Check that there is at least one -src supplied.
            checkSrcOption(args);
            // Check that there is one -d supplied.
            bin_dir = findDirectoryOption(args,"-d","output", true, false, true);
            gensrc_dir = findDirectoryOption(args,"-s","gensrc", false, false, true);
            header_dir = findDirectoryOption(args,"-h","headers", false, false, true);
            makefile_source_list = findFileOption(args,"--compare-found-sources","makefile source list", false);

            // Load the prev build state database.
            javac_state = JavacState.load(args, bin_dir, gensrc_dir, header_dir,
                    findBooleanOption(args, "--permit-unidentified-artifacts"), out, err);

            // Setup the suffix rules from the command line.
            suffix_rules = javac_state.getJavaSuffixRule();
            findTranslateOptions(args, suffix_rules);
            if (suffix_rules.keySet().size() > 1 && gensrc_dir == null) {
                Log.error("You have translators but no gensrc dir (-s) specified!");
                return -1;
            }
            findCopyOptions(args, suffix_rules);

            // All found modules are put here.
            Map<String,Module> modules = new HashMap<String,Module>();
            // We start out in the legacy empty no-name module.
            // As soon as we stumble on a module-info.java file we change to that module.
            Module current_module = new Module("", "");
            modules.put("", current_module);

            // Find all sources, use the suffix rules to know which files are sources.
            Map<String,Source> sources = new HashMap<String,Source>();
            // Find the files, this will automatically populate the found modules
            // with found packages where the sources are found!
            findFiles(args, "-src", suffix_rules.keySet(), sources, modules, current_module, false);

            if (sources.isEmpty()) {
                Log.error("Found nothing to compile!");
                return -1;
            }

            // Find all source files allowable for linking.
            // We might find more modules here as well.
            Map<String,Source> sources_to_link_to = new HashMap<String,Source>();
            // Always reuse -src for linking as well! This means that we might
            // get two -sourcepath on the commandline after the rewrite, which is
            // fine. We can have as many as we like. You need to have separate -src/-sourcepath/-classpath
            // if you need different filtering rules for different roots. If you have the same filtering
            // rules for all sourcepath roots, you can concatenate them using :(;) as before.
              rewriteOptions(args, "-src", "-sourcepath");
            findFiles(args, "-sourcepath", Util.set(".java"), sources_to_link_to, modules, current_module, true);

            // Find all class files allowable for linking.
            // And pickup knowledge of all modules found here.
            // This cannot currently filter classes inside jar files.
            Map<String,Source> classes_to_link_to = new HashMap<String,Source>();
//          findFiles(args, "-classpath", Util.set(".class"), classes_to_link_to, modules, current_module, true);

            // Find all module sources allowable for linking.
            Map<String,Source> modules_to_link_to = new HashMap<String,Source>();
 //         findFiles(args, "-modulepath", Util.set(".class"), modules_to_link_to, modules, current_module, true);

            // Add the set of sources to the build database.
            javac_state.now().collectPackagesSourcesAndArtifacts(modules);
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
            if (!findBooleanOption(args, "--permit-unidentified-artifacts")) {
                javac_state.removeUnidentifiedArtifacts();
            }
            // Go through all sources and taint all packages that miss artifacts.
            javac_state.taintPackagesThatMissArtifacts();

            // Now clean out all known artifacts belonging to tainted packages.
            javac_state.deleteClassArtifactsInTaintedPackages();
            // Copy files, for example property files, images files, xml files etc etc.
            javac_state.performCopying(bin_dir, suffix_rules);
            // Translate files, for example compile properties or compile idls.
            javac_state.performTranslation(gensrc_dir, suffix_rules);
            // Add any potentially generated java sources to the tobe compiled list.
            // (Generated sources must always have a package.)
            Map<String,Source> generated_sources = new HashMap<String,Source>();
            Source.scanRoot(gensrc_dir, Util.set(".java"), null, null, null, null,
                   generated_sources, modules, current_module, false, true, false);
            javac_state.now().collectPackagesSourcesAndArtifacts(modules);
            // Recheck the the source files and their timestamps again.
            javac_state.checkSourceStatus(true);

            // Now do a safety check that the list of source files is identical
            // to the list Make believes we are compiling. If we do not get this
            // right, then incremental builds will fail with subtility.
            // If any difference is detected, then we will fail hard here.
            // This is an important safety net.
            javac_state.compareWithMakefileList(makefile_source_list);

            // Do the compilations, repeatedly until no tainted packages exist.
            boolean again;
            // Collect the name of all compiled packages.
            Set<String> recently_compiled = new HashSet<String>();
            boolean[] rc = new boolean[1];
            do {
                // Clean out artifacts in tainted packages.
                javac_state.deleteClassArtifactsInTaintedPackages();
                again = javac_state.performJavaCompilations(bin_dir, server_settings, args, recently_compiled, rc);
                if (!rc[0]) break;
            } while (again);
            // Only update the state if the compile went well.
            if (rc[0]) {
                javac_state.save();
                // Collect all the artifacts.
                javac_state.now().collectArtifacts(modules);
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

    /**
     * Are java source files passed on the command line?
     */
    private boolean findJavaSourceFiles(String[] args) {
        String prev = "";
        for (String s : args) {
            if (s.endsWith(".java") && !prev.equals("-xf") && !prev.equals("-if")) {
                return true;
            }
            prev = s;
        }
        return false;
    }

    /**
     * Is an at file passed on the command line?
     */
    private boolean findAtFile(String[] args) {
        for (String s : args) {
            if (s.startsWith("@")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the log level setting.
     */
    private String findLogLevel(String[] args) {
        for (String s : args) {
            if (s.startsWith("--log=") && s.length()>6) {
                return s.substring(6);
            }
            if (s.equals("-verbose")) {
                return "info";
            }
        }
        return "info";
    }

    /**
     * Remove smart javac wrapper arguments, before feeding
     * the args to the plain javac.
     */
    static String[] removeWrapperArgs(String[] args) {
        String[] out = new String[args.length];
        // The first source path index is remembered
        // here. So that all following can be concatenated to it.
        int source_path = -1;
        // The same for class path.
        int class_path = -1;
        // And module path.
        int module_path = -1;
        int j = 0;
        for (int i = 0; i<args.length; ++i) {
            if (args[i].equals("-src") ||
                args[i].equals("-x") ||
                args[i].equals("-i") ||
                args[i].equals("-xf") ||
                args[i].equals("-if") ||
                args[i].equals("-copy") ||
                args[i].equals("-tr") ||
                args[i].equals("-j")) {
                // Just skip it and skip following value
                i++;
            } else if (args[i].startsWith("--server:")) {
                // Just skip it.
            } else if (args[i].startsWith("--log=")) {
                // Just skip it.
            } else if (args[i].equals("--permit-unidentified-artifacts")) {
                // Just skip it.
            } else if (args[i].equals("--permit-sources-without-package")) {
                // Just skip it.
            } else if (args[i].equals("--compare-found-sources")) {
                // Just skip it and skip verify file name
                i++;
            } else if (args[i].equals("-sourcepath")) {
                if (source_path == -1) {
                    source_path = j;
                    out[j] = args[i];
                    out[j+1] = args[i+1];
                    j+=2;
                    i++;
                } else {
                    // Skip this and its argument, but
                    // append argument to found sourcepath.
                    out[source_path+1] = out[source_path+1]+File.pathSeparatorChar+args[i+1];
                    i++;
                }
            } else if (args[i].equals("-classpath")) {
                if (class_path == -1) {
                    class_path = j;
                    out[j] = args[i];
                    out[j+1] = args[i+1];
                    j+=2;
                    i++;
                } else {
                    // Skip this and its argument, but
                    // append argument to found sourcepath.
                    out[class_path+1] = out[class_path+1]+File.pathSeparatorChar+args[i+1];
                    i++;
                }
            } else if (args[i].equals("-modulepath")) {
                if (module_path == -1) {
                    module_path = j;
                    out[j] = args[i];
                    out[j+1] = args[i+1];
                    j+=2;
                    i++;
                } else {
                    // Skip this and its argument, but
                    // append argument to found sourcepath.
                    out[module_path+1] = out[module_path+1]+File.pathSeparatorChar+args[i+1];
                    i++;
                }
             } else {
                // Copy argument.
                out[j] = args[i];
                j++;
            }
        }
        String[] ret = new String[j];
        System.arraycopy(out, 0, ret, 0, j);
        return ret;
    }

    /**
     * Make sure directory exist, create it if not.
     */
    private static boolean makeSureExists(File dir) {
        // Make sure the dest directories exist.
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.error("Could not create the directory "+dir.getPath());
                return false;
            }
        }
        return true;
    }

    /**
     * Verify that a package pattern is valid.
     */
    private static void checkPattern(String s) throws ProblemException {
        // Package names like foo.bar.gamma are allowed, and
        // package names suffixed with .* like foo.bar.* are
        // also allowed.
        Pattern p = Pattern.compile("[a-zA-Z_]{1}[a-zA-Z0-9_]*(\\.[a-zA-Z_]{1}[a-zA-Z0-9_]*)*(\\.\\*)?+");
        Matcher m = p.matcher(s);
        if (!m.matches()) {
            throw new ProblemException("The string \""+s+"\" is not a proper package name pattern.");
        }
    }

    /**
     * Verify that a translate pattern is valid.
     */
    private static void checkTranslatePattern(String s) throws ProblemException {
        // .prop=com.sun.tools.javac.smart.CompileProperties
        // .idl=com.sun.corba.CompileIdl
        // .g3=antlr.CompileGrammar,debug=true
        Pattern p = Pattern.compile(
            "\\.[a-zA-Z_]{1}[a-zA-Z0-9_]*=[a-z_]{1}[a-z0-9_]*(\\.[a-z_]{1}[a-z0-9_]*)*"+
            "(\\.[a-zA-Z_]{1}[a-zA-Z0-9_]*)(,.*)?");
        Matcher m = p.matcher(s);
        if (!m.matches()) {
            throw new ProblemException("The string \""+s+"\" is not a proper translate pattern.");
        }
    }

    /**
     * Verify that a copy pattern is valid.
     */
    private static void checkCopyPattern(String s) throws ProblemException {
        // .gif
        // .html
        Pattern p = Pattern.compile(
            "\\.[a-zA-Z_]{1}[a-zA-Z0-9_]*");
        Matcher m = p.matcher(s);
        if (!m.matches()) {
            throw new ProblemException("The string \""+s+"\" is not a proper suffix.");
        }
    }

    /**
     * Verify that a source file name is valid.
     */
    private static void checkFilePattern(String s) throws ProblemException {
        // File names like foo/bar/gamma/Bar.java are allowed,
        // as well as /bar/jndi.properties as well as,
        // */bar/Foo.java
        Pattern p = null;
        if (File.separatorChar == '\\') {
            p = Pattern.compile("\\*?(.+\\\\)*.+");
        }
        else if (File.separatorChar == '/') {
            p = Pattern.compile("\\*?(.+/)*.+");
        } else {
            throw new ProblemException("This platform uses the unsupported "+File.separatorChar+
                                      " as file separator character. Please add support for it!");
        }
        Matcher m = p.matcher(s);
        if (!m.matches()) {
            throw new ProblemException("The string \""+s+"\" is not a proper file name.");
        }
    }

    /**
     * Scan the arguments to find an option is used.
     */
    private static boolean hasOption(String[] args, String option) {
        for (String a : args) {
            if (a.equals(option)) return true;
        }
        return false;
    }

    /**
     * Check if -implicit is supplied, if so check that it is none.
     * If -implicit is not supplied, supply -implicit:none
     * Only implicit:none is allowed because otherwise the multicore compilations
     * and dependency tracking will be tangled up.
     */
    private static String[] verifyImplicitOption(String[] args)
        throws ProblemException {

        boolean foundImplicit = false;
        for (String a : args) {
            if (a.startsWith("-implicit:")) {
                foundImplicit = true;
                if (!a.equals("-implicit:none")) {
                    throw new ProblemException("The only allowed setting for sjavac is -implicit:none, it is also the default.");
                }
            }
        }
        if (foundImplicit) {
            return args;
        }
        // -implicit:none not found lets add it.
        String[] newargs = new String[args.length+1];
        System.arraycopy(args,0, newargs, 0, args.length);
        newargs[args.length] = "-implicit:none";
        return newargs;
    }

    /**
     * Rewrite a single option into something else.
     */
    private static void rewriteOptions(String[] args, String option, String new_option) {
        for (int i=0; i<args.length; ++i) {
            if (args[i].equals(option)) {
                args[i] = new_option;
            }
        }
    }

    /**
     * Scan the arguments to find an option that specifies a directory.
     * Create the directory if necessary.
     */
    private static File findDirectoryOption(String[] args, String option, String name, boolean needed, boolean allow_dups, boolean create)
        throws ProblemException, ProblemException {
        File dir = null;
        for (int i = 0; i<args.length; ++i) {
            if (args[i].equals(option)) {
                if (dir != null) {
                    throw new ProblemException("You have already specified the "+name+" dir!");
                }
                if (i+1 >= args.length) {
                    throw new ProblemException("You have to specify a directory following "+option+".");
                }
                if (args[i+1].indexOf(File.pathSeparatorChar) != -1) {
                    throw new ProblemException("You must only specify a single directory for "+option+".");
                }
                dir = new File(args[i+1]);
                if (!dir.exists()) {
                    if (!create) {
                         throw new ProblemException("This directory does not exist: "+dir.getPath());
                    } else
                    if (!makeSureExists(dir)) {
                        throw new ProblemException("Cannot create directory "+dir.getPath());
                    }
                }
                if (!dir.isDirectory()) {
                    throw new ProblemException("\""+args[i+1]+"\" is not a directory.");
                }
            }
        }
        if (dir == null && needed) {
            throw new ProblemException("You have to specify "+option);
        }
        try {
            if (dir != null)
                return dir.getCanonicalFile();
        } catch (IOException e) {
            throw new ProblemException(""+e);
        }
        return null;
    }

    /**
     * Option is followed by path.
     */
    private static boolean shouldBeFollowedByPath(String o) {
        return o.equals("-s") ||
               o.equals("-h") ||
               o.equals("-d") ||
               o.equals("-sourcepath") ||
               o.equals("-classpath") ||
               o.equals("-bootclasspath") ||
               o.equals("-src");
    }

    /**
     * Add -src before source root directories if not already there.
     */
    private static String[] addSrcBeforeDirectories(String[] args) {
        List<String> newargs = new ArrayList<String>();
        for (int i = 0; i<args.length; ++i) {
            File dir = new File(args[i]);
            if (dir.exists() && dir.isDirectory()) {
                if (i == 0 || !shouldBeFollowedByPath(args[i-1])) {
                    newargs.add("-src");
                }
            }
            newargs.add(args[i]);
        }
        return newargs.toArray(new String[0]);
    }

    /**
     * Check the -src options.
     */
    private static void checkSrcOption(String[] args)
        throws ProblemException {
        Set<File> dirs = new HashSet<File>();
        for (int i = 0; i<args.length; ++i) {
            if (args[i].equals("-src")) {
                if (i+1 >= args.length) {
                    throw new ProblemException("You have to specify a directory following -src.");
                }
                StringTokenizer st = new StringTokenizer(args[i+1], File.pathSeparator);
                while (st.hasMoreElements()) {
                    File dir = new File(st.nextToken());
                    if (!dir.exists()) {
                        throw new ProblemException("This directory does not exist: "+dir.getPath());
                    }
                    if (!dir.isDirectory()) {
                        throw new ProblemException("\""+dir.getPath()+"\" is not a directory.");
                    }
                    if (dirs.contains(dir)) {
                        throw new ProblemException("The src directory \""+dir.getPath()+"\" is specified more than once!");
                    }
                    dirs.add(dir);
                }
            }
        }
        if (dirs.isEmpty()) {
            throw new ProblemException("You have to specify -src.");
        }
    }

    /**
     * Scan the arguments to find an option that specifies a file.
     */
    private static File findFileOption(String[] args, String option, String name, boolean needed)
        throws ProblemException, ProblemException {
        File file = null;
        for (int i = 0; i<args.length; ++i) {
            if (args[i].equals(option)) {
                if (file != null) {
                    throw new ProblemException("You have already specified the "+name+" file!");
                }
                if (i+1 >= args.length) {
                    throw new ProblemException("You have to specify a file following "+option+".");
                }
                file = new File(args[i+1]);
                if (file.isDirectory()) {
                    throw new ProblemException("\""+args[i+1]+"\" is not a file.");
                }
                if (!file.exists() && needed) {
                    throw new ProblemException("The file \""+args[i+1]+"\" does not exist.");
                }

            }
        }
        if (file == null && needed) {
            throw new ProblemException("You have to specify "+option);
        }
        return file;
    }

    /**
     * Look for a specific switch, return true if found.
     */
    public static boolean findBooleanOption(String[] args, String option) {
        for (int i = 0; i<args.length; ++i) {
            if (args[i].equals(option)) return true;
        }
        return false;
    }

    /**
     * Scan the arguments to find an option that specifies a number.
     */
    public static int findNumberOption(String[] args, String option) {
        int rc = 0;
        for (int i = 0; i<args.length; ++i) {
            if (args[i].equals(option)) {
                if (args.length > i+1) {
                    rc = Integer.parseInt(args[i+1]);
                }
            }
        }
        return rc;
    }

    /**
     * Scan the arguments to find the option (-tr) that setup translation rules to java source
     * from different sources. For example: .properties are translated using CompileProperties
     * The found translators are stored as suffix rules.
     */
    private static void findTranslateOptions(String[] args, Map<String,Transformer> suffix_rules)
        throws ProblemException, ProblemException {

        for (int i = 0; i<args.length; ++i) {
            if (args[i].equals("-tr")) {
                if (i+1 >= args.length) {
                    throw new ProblemException("You have to specify a translate rule following -tr.");
                }
                String s = args[i+1];
                checkTranslatePattern(s);
                int ep = s.indexOf("=");
                String suffix = s.substring(0,ep);
                String classname = s.substring(ep+1);
                if (suffix_rules.get(suffix) != null) {
                    throw new ProblemException("You have already specified a "+
                                              "rule for the suffix "+suffix);
                }
                if (s.equals(".class")) {
                    throw new ProblemException("You cannot have a translator for .class files!");
                }
                if (s.equals(".java")) {
                    throw new ProblemException("You cannot have a translator for .java files!");
                }
                String extra = null;
                int exp = classname.indexOf(",");
                if (exp != -1) {
                    extra = classname.substring(exp+1);
                    classname = classname.substring(0,exp);
                }
                try {
                    Class<?> cl = Class.forName(classname);
                    Transformer t = (Transformer)cl.newInstance();
                    t.setExtra(extra);
                    suffix_rules.put(suffix, t);
                }
                catch (Exception e) {
                    throw new ProblemException("Cannot use "+classname+" as a translator!");
                }
            }
        }
    }

    /**
     * Scan the arguments to find the option (-copy) that setup copying rules into the bin dir.
     * For example: -copy .html
     * The found copiers are stored as suffix rules as well. No translation is done, just copying.
     */
    private void findCopyOptions(String[] args, Map<String,Transformer> suffix_rules)
        throws ProblemException, ProblemException {

        for (int i = 0; i<args.length; ++i) {
            if (args[i].equals("-copy")) {
                if (i+1 >= args.length) {
                    throw new ProblemException("You have to specify a translate rule following -tr.");
                }
                String s = args[i+1];
                checkCopyPattern(s);
                if (suffix_rules.get(s) != null) {
                    throw new ProblemException("You have already specified a "+
                                              "rule for the suffix "+s);
                }
                if (s.equals(".class")) {
                    throw new ProblemException("You cannot have a copy rule for .class files!");
                }
                if (s.equals(".java")) {
                    throw new ProblemException("You cannot have a copy rule for .java files!");
                }
                suffix_rules.put(s, javac_state.getCopier());
            }
        }
    }

    /**
     * Rewrite a / separated path into \ separated, but only
     * if we are running on a platform were File.separatorChar=='\', ie winapi.
     */
    private String fixupSeparator(String p) {
        if (File.separatorChar == '/') return p;
        return p.replaceAll("/", "\\\\");
    }

    /**
     * Scan the arguments for -i -x -xf -if followed by the option
     * -src, -sourcepath, -modulepath or -classpath and produce a map of all the
     * files to referenced for that particular option.
     *
     * Store the found sources and the found modules in the supplied maps.
     */
    private boolean findFiles(String[] args, String option, Set<String> suffixes,
                              Map<String,Source> found_files, Map<String, Module> found_modules,
                              Module current_module, boolean inLinksrc)
        throws ProblemException, ProblemException
    {
        // Track which source roots, source path roots and class path roots have been added.
        Set<File> roots = new HashSet<File>();
        // Track the current set of package includes,excludes as well as excluded source files,
        // to be used in the next -src/-sourcepath/-classpath
        List<String> includes = new LinkedList<String>();
        List<String> excludes = new LinkedList<String>();
        List<String> excludefiles = new LinkedList<String>();
        List<String> includefiles = new LinkedList<String>();
        // This include is used to find all modules in the source.
        List<String> moduleinfo = new LinkedList<String>();
        moduleinfo.add("module-info.java");

        for (int i = 0; i<args.length; ++i) {
            if (args[i].equals("-i")) {
                if (i+1 >= args.length) {
                    throw new ProblemException("You have to specify a package pattern following -i");
                }
                String incl = args[i+1];
                checkPattern(incl);
                includes.add(incl);
            }
            if (args[i].equals("-x")) {
                if (i+1 >= args.length) {
                    throw new ProblemException("You have to specify a package pattern following -x");
                }
                String excl = args[i+1];
                checkPattern(excl);
                excludes.add(excl);
            }
            if (args[i].equals("-xf")) {
                if (i+1 >= args.length) {
                    throw new ProblemException("You have to specify a file following -xf");
                }
                String exclf = args[i+1];
                checkFilePattern(exclf);
                exclf = Util.normalizeDriveLetter(exclf);
                excludefiles.add(fixupSeparator(exclf));
            }
            if (args[i].equals("-if")) {
                if (i+1 >= args.length) {
                    throw new ProblemException("You have to specify a file following -xf");
                }
                String inclf = args[i+1];
                checkFilePattern(inclf);
                inclf = Util.normalizeDriveLetter(inclf);
                includefiles.add(fixupSeparator(inclf));
            }
            if (args[i].equals(option)) {
                if (i+1 >= args.length) {
                    throw new ProblemException("You have to specify a directory following "+option);
                }
                String[] root_dirs = args[i+1].split(File.pathSeparator);
                for (String r : root_dirs) {
                    File root = new File(r);
                    if (!root.isDirectory()) {
                        throw new ProblemException("\""+r+"\" is not a directory.");
                    }
                    try {
                        root = root.getCanonicalFile();
                    } catch (IOException e) {
                        throw new ProblemException(""+e);
                    }
                    if (roots.contains(root)) {
                        throw new ProblemException("\""+r+"\" has already been used for "+option);
                    }
                    if (roots.equals(bin_dir)) {
                        throw new ProblemException("\""+r+"\" cannot be used both for "+option+" and -d");
                    }
                    if (roots.equals(gensrc_dir)) {
                        throw new ProblemException("\""+r+"\" cannot be used both for "+option+" and -s");
                    }
                    if (roots.equals(header_dir)) {
                        throw new ProblemException("\""+r+"\" cannot be used both for "+option+" and -h");
                    }
                    roots.add(root);
                    Source.scanRoot(root, suffixes, excludes, includes, excludefiles, includefiles,
                                    found_files, found_modules, current_module,
                                    findBooleanOption(args, "--permit-sources-without-package"),
                                    false, inLinksrc);
                }
            }
            if (args[i].equals("-src") ||
                args[i].equals("-sourcepath") ||
                args[i].equals("-modulepath") ||
                args[i].equals("-classpath"))
            {
                // Reset the includes,excludes and excludefiles after they have been used.
                includes = new LinkedList<String>();
                excludes = new LinkedList<String>();
                excludefiles = new LinkedList<String>();
                includefiles = new LinkedList<String>();
            }
        }
        return true;
    }

}

