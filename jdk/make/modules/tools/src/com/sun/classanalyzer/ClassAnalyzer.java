/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.classanalyzer;

import com.sun.classanalyzer.AnnotatedDependency.*;
import com.sun.classanalyzer.Module.Dependency;
import com.sun.classanalyzer.Module.PackageInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *
 * @author Mandy Chung
 */
public class ClassAnalyzer {

    public static void main(String[] args) throws Exception {
        String jdkhome = null;
        String cpath = null;
        List<String> configs = new ArrayList<String>();
        List<String> depconfigs = new ArrayList<String>();
        String output = ".";
        boolean mergeModules = true;
        boolean showDynamic = false;

        // process arguments
        int i = 0;
        while (i < args.length) {
            String arg = args[i++];
            if (arg.equals("-jdkhome")) {
                if (i < args.length) {
                    jdkhome = args[i++];
                } else {
                    usage();
                }
            } else if (arg.equals("-cpath")) {
                if (i < args.length) {
                    cpath = args[i++];
                } else {
                    usage();
                }
            } else if (arg.equals("-config")) {
                if (i < args.length) {
                    configs.add(args[i++]);
                } else {
                    usage();
                }
            } else if (arg.equals("-depconfig")) {
                if (i < args.length) {
                    depconfigs.add(args[i++]);
                } else {
                    usage();
                }
            } else if (arg.equals("-output")) {
                if (i < args.length) {
                    output = args[i++];
                } else {
                    usage();
                }
            } else if (arg.equals("-base")) {
                ModuleConfig.setBaseModule(args[i++]);
            } else if (arg.equals("-nomerge")) {
                // analyze the fine-grained module dependencies
                mergeModules = false;
            } else if (arg.equals("-showdynamic")) {
                showDynamic = true;
            } else {
                System.err.println("Invalid option: " + arg);
                usage();
            }
        }

        if ((jdkhome == null && cpath == null) || (jdkhome != null && cpath != null)) {
            usage();
        }
        if (configs.isEmpty()) {
            usage();
        }

        if (jdkhome != null) {
            ClassPath.setJDKHome(jdkhome);
        } else if (cpath != null) {
            ClassPath.setClassPath(cpath);
        }

        // create output directory if it doesn't exist
        File dir = new File(output);
        if (!dir.isDirectory()) {
            if (!dir.exists()) {
                boolean created = dir.mkdir();
                if (!created) {
                    throw new RuntimeException("Unable to create `" + dir + "'");
                }
            }
        }

        buildModules(configs, depconfigs, mergeModules);

        // generate output files
        for (Module m : modules) {
            // only generate reports for top-level modules
            if (m.group() == m) {
                m.printClassListTo(resolve(dir, m.name(), "classlist"));
                m.printResourceListTo(resolve(dir, m.name(), "resources"));
                m.printSummaryTo(resolve(dir, m.name(), "summary"));
                m.printDependenciesTo(resolve(dir, m.name(), "dependencies"), showDynamic);
            }
        }

        // Generate other summary reports
        printModulesSummary(dir, showDynamic);
        printModulesDot(dir, showDynamic);
        printModulesList(dir);
        printPackagesSummary(dir);
    }
    private static List<Module> modules = new ArrayList<Module>();

    static void buildModules(List<String> configs,
            List<String> depconfigs,
            boolean mergeModules) throws IOException {
        // create modules based on the input config files
        for (String file : configs) {
            for (ModuleConfig mconfig : ModuleConfig.readConfigurationFile(file)) {
                modules.add(Module.addModule(mconfig));
            }
        }

        // parse class files
        ClassPath.parseAllClassFiles();

        // Add additional dependencies if specified
        if (depconfigs != null && depconfigs.size() > 0) {
            DependencyConfig.parse(depconfigs);
        }

        // process the roots and dependencies to get the classes for each module
        for (Module m : modules) {
            m.processRootsAndReferences();
        }

        // update the dependencies for classes that were subsequently allocated
        // to modules
        for (Module m : modules) {
            m.fixupDependencies();
        }

        if (mergeModules) {
            Module.buildModuleMembers();
        }
    }

    private static void printModulesSummary(File dir, boolean showDynamic) throws IOException {
        // print summary of dependencies
        PrintWriter writer = new PrintWriter(new File(dir, "modules.summary"));
        try {
            for (Module m : modules) {
                // only show top-level module dependencies
                if (m.group() == m) {
                    for (Dependency dep : m.dependents()) {
                        if (!showDynamic && dep.dynamic && dep.optional) {
                            continue;
                        }
                        if (dep.module == null || !dep.module.isBase()) {

                            String prefix = "";
                            if (dep.optional) {
                                if (dep.dynamic) {
                                    prefix = "[dynamic] ";
                                } else {
                                    prefix = "[optional] ";
                                }
                            }

                            Module other = dep != null ? dep.module : null;
                            writer.format("%s%s -> %s%n", prefix, m, other);
                        }
                    }
                }
            }
        } finally {
            writer.close();
        }
    }

    private static void printModulesDot(File dir, boolean showDynamic) throws IOException {
        PrintWriter writer = new PrintWriter(new File(dir, "modules.dot"));
        try {
            writer.println("digraph jdk {");
            for (Module m : modules) {
                if (m.group() == m) {
                    for (Dependency dep : m.dependents()) {
                        if (!showDynamic && dep.dynamic && dep.optional) {
                            continue;
                        }
                        if (dep.module == null || !dep.module.isBase()) {
                            String style = "";
                            String color = "";
                            String property = "";
                            if (dep.optional) {
                                style = "style=dotted";
                            }
                            if (dep.dynamic) {
                                color = "color=red";
                            }
                            if (style.length() > 0 || color.length() > 0) {
                                String comma = "";
                                if (style.length() > 0 && color.length() > 0) {
                                    comma = ", ";
                                }
                                property = String.format(" [%s%s%s]", style, comma, color);
                            }
                            Module other = dep != null ? dep.module : null;
                            writer.format("    \"%s\" -> \"%s\"%s;%n", m, other, property);
                        }
                    }
                }
            }
            writer.println("}");
        } finally {
            writer.close();
        }
    }

    private static void printMembers(Module m, PrintWriter writer) {
        for (Module member : m.members()) {
            if (!member.isEmpty()) {
                writer.format("%s ", member);
                printMembers(member, writer);
            }
        }
    }

    private static void printModulesList(File dir) throws IOException {
        // print module group / members relationship
        PrintWriter writer = new PrintWriter(new File(dir, "modules.list"));
        try {
            for (Module m : modules) {
                if (m.group() == m && !m.isEmpty()) {
                    writer.format("%s ", m);
                    printMembers(m, writer);
                    writer.println();
                }
            }
        } finally {
            writer.close();
        }
    }

    private static void printPackagesSummary(File dir) throws IOException {
        // print package / module relationship
        PrintWriter writer = new PrintWriter(new File(dir, "modules.pkginfo"));
        try {
            Map<String, Set<Module>> packages = new TreeMap<String, Set<Module>>();
            Set<String> splitPackages = new TreeSet<String>();

            for (Module m : modules) {
                if (m.group() == m) {
                    for (PackageInfo info : m.getPackageInfos()) {
                        Set<Module> value = packages.get(info.pkgName);
                        if (value == null) {
                            value = new TreeSet<Module>();
                            packages.put(info.pkgName, value);
                        } else {
                            // package in more than one module
                            splitPackages.add(info.pkgName);
                        }
                        value.add(m);
                    }
                }
            }

            // packages that are splitted among multiple modules
            writer.println("Packages splitted across modules:-\n");
            writer.format("%-60s  %s\n", "Package", "Module");

            for (String pkgname : splitPackages) {
                writer.format("%-60s", pkgname);
                for (Module m : packages.get(pkgname)) {
                    writer.format("  %s", m);
                }
                writer.println();
            }

            writer.println("\nPackage-private dependencies:-");
            for (String pkgname : splitPackages) {
                for (Klass k : Klass.getAllClasses()) {
                    if (k.getPackageName().equals(pkgname)) {
                        Module m = k.getModule();
                        // check if this klass references a package-private
                        // class that is in a different module
                        for (Klass other : k.getReferencedClasses()) {
                            if (other.getModule() != m &&
                                    !other.isPublic() &&
                                    other.getPackageName().equals(pkgname)) {
                                String from = k.getClassName() + " (" + m + ")";
                                writer.format("%-60s -> %s (%s)\n", from, other, other.getModule());
                            }
                        }
                    }
                }
            }
        } finally {
            writer.close();
        }

    }

    private static String resolve(File dir, String mname, String suffix) {
        File f = new File(dir, mname + "." + suffix);
        return f.toString();

    }

    private static void usage() {
        System.out.println("Usage: ClassAnalyzer <options>");
        System.out.println("Options: ");
        System.out.println("\t-jdkhome <JDK home> where all jars will be parsed");
        System.out.println("\t-cpath   <classpath> where classes and jars will be parsed");
        System.out.println("\t         Either -jdkhome or -cpath option can be used.");
        System.out.println("\t-config  <module config file>");
        System.out.println("\t         This option can be repeated for multiple module config files");
        System.out.println("\t-output  <output dir>");
        System.out.println("\t-nomerge specify not to merge modules");
        System.out.println("\t-showdynamic show dynamic dependencies in the reports");
        System.exit(-1);
    }
}
