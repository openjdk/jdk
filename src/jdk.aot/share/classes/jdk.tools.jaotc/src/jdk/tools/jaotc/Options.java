/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import jdk.tools.jaotc.collect.ClassSearch;
import jdk.tools.jaotc.collect.ClassSource;
import jdk.tools.jaotc.collect.SearchFor;
import jdk.tools.jaotc.collect.SearchPath;
import jdk.tools.jaotc.collect.classname.ClassNameSourceProvider;
import jdk.tools.jaotc.collect.directory.DirectorySourceProvider;
import jdk.tools.jaotc.collect.jar.JarSourceProvider;
import jdk.tools.jaotc.collect.module.ModuleSourceProvider;

final class Options {
    List<SearchFor> files = new LinkedList<>();
    String osName;
    String outputName = defaultOutputName();
    String methodList;
    List<ClassSource> sources = new ArrayList<>();
    String linkerpath = null;
    SearchPath searchPath = new SearchPath();

    /**
     * We don't see scaling beyond 16 threads.
     */
    private static final int COMPILER_THREADS = 16;

    int threads = Integer.min(COMPILER_THREADS, Runtime.getRuntime().availableProcessors());

    boolean ignoreClassLoadingErrors;
    boolean exitOnError;
    boolean info;
    boolean verbose;
    boolean debug;
    boolean help;
    boolean version;
    boolean compileWithAssertions;
    boolean tiered;

    private String defaultOutputName() {
        osName = System.getProperty("os.name");
        String name = "unnamed.";
        String ext;

        switch (osName) {
            case "Linux":
            case "SunOS":
                ext = "so";
                break;
            case "Mac OS X":
                ext = "dylib";
                break;
            default:
                if (osName.startsWith("Windows")) {
                    ext = "dll";
                } else {
                    ext = "so";
                }
        }

        return name + ext;
    }

    static class BadArgs extends Exception {
        private static final long serialVersionUID = 1L;
        final String key;
        final Object[] args;
        boolean showUsage;

        BadArgs(String key, Object... args) {
            super(MessageFormat.format(key, args));
            this.key = key;
            this.args = args;
        }

        BadArgs showUsage(boolean b) {
            showUsage = b;
            return this;
        }
    }

    abstract static class Option {
        final String help;
        final boolean hasArg;
        final String[] aliases;

        Option(String help, boolean hasArg, String... aliases) {
            this.help = help;
            this.hasArg = hasArg;
            this.aliases = aliases;
        }

        boolean isHidden() {
            return false;
        }

        boolean matches(String opt) {
            for (String a : aliases) {
                if (a.equals(opt)) {
                    return true;
                } else if (opt.startsWith("--") && hasArg && opt.startsWith(a + "=")) {
                    return true;
                }
            }
            return false;
        }

        boolean ignoreRest() {
            return false;
        }

        abstract void process(Main task, String opt, String arg) throws BadArgs;
    }

    static Option[] recognizedOptions = {new Option("  --output <file>            Output file name", true, "--output") {
        @Override
        void process(Main task, String opt, String arg) {
            String name = arg;
            task.options.outputName = name;
        }
    }, new Option("  --class-name <class names> List of classes to compile", true, "--class-name", "--classname") {
        @Override
        void process(Main task, String opt, String arg) {
            task.options.files.addAll(ClassSearch.makeList(ClassNameSourceProvider.TYPE, arg));
        }
    }, new Option("  --jar <jarfiles>           List of jar files to compile", true, "--jar") {
        @Override
        void process(Main task, String opt, String arg) {
            task.options.files.addAll(ClassSearch.makeList(JarSourceProvider.TYPE, arg));
        }
    }, new Option("  --module <modules>         List of modules to compile", true, "--module") {
        @Override
        void process(Main task, String opt, String arg) {
            task.options.files.addAll(ClassSearch.makeList(ModuleSourceProvider.TYPE, arg));
        }
    }, new Option("  --directory <dirs>         List of directories where to search for files to compile", true, "--directory") {
        @Override
        void process(Main task, String opt, String arg) {
            task.options.files.addAll(ClassSearch.makeList(DirectorySourceProvider.TYPE, arg));
        }
    }, new Option("  --search-path <dirs>       List of directories where to search for specified files", true, "--search-path") {
        @Override
        void process(Main task, String opt, String arg) {
            String[] elements = arg.split(":");
            task.options.searchPath.add(elements);
        }
    }, new Option("  --compile-commands <file>  Name of file with compile commands", true, "--compile-commands") {
        @Override
        void process(Main task, String opt, String arg) {
            task.options.methodList = arg;
        }
    }, new Option("  --compile-for-tiered       Generate profiling code for tiered compilation", false, "--compile-for-tiered") {
        @Override
        void process(Main task, String opt, String arg) {
            task.options.tiered = true;
        }
    }, new Option("  --compile-with-assertions  Compile with java assertions", false, "--compile-with-assertions") {
        @Override
        void process(Main task, String opt, String arg) {
            task.options.compileWithAssertions = true;
        }
    }, new Option("  --compile-threads <number> Number of compilation threads to be used", true, "--compile-threads", "--threads") {
        @Override
        void process(Main task, String opt, String arg) {
            int threads = Integer.parseInt(arg);
            final int available = Runtime.getRuntime().availableProcessors();
            if (threads <= 0) {
                task.warning("invalid number of threads specified: {0}, using: {1}", threads, available);
                threads = available;
            }
            if (threads > available) {
                task.warning("too many threads specified: {0}, limiting to: {1}", threads, available);
            }
            task.options.threads = Integer.min(threads, available);
        }
    }, new Option("  --ignore-errors            Ignores all exceptions thrown during class loading", false, "--ignore-errors") {
        @Override
        void process(Main task, String opt, String arg) {
            task.options.ignoreClassLoadingErrors = true;
        }
    }, new Option("  --exit-on-error            Exit on compilation errors", false, "--exit-on-error") {
        @Override
        void process(Main task, String opt, String arg) {
            task.options.exitOnError = true;
        }
    }, new Option("  --info                     Print information during compilation", false, "--info") {
        @Override
        void process(Main task, String opt, String arg) throws BadArgs {
            task.options.info = true;
        }
    }, new Option("  --verbose                  Print verbose information", false, "--verbose") {
        @Override
        void process(Main task, String opt, String arg) throws BadArgs {
            task.options.info = true;
            task.options.verbose = true;
        }
    }, new Option("  --debug                    Print debug information", false, "--debug") {
        @Override
        void process(Main task, String opt, String arg) throws BadArgs {
            task.options.info = true;
            task.options.verbose = true;
            task.options.debug = true;
        }
    }, new Option("  --help                     Print this usage message", false, "--help") {
        @Override
        void process(Main task, String opt, String arg) {
            task.options.help = true;
        }
    }, new Option("  --version                  Version information", false, "--version") {
        @Override
        void process(Main task, String opt, String arg) {
            task.options.version = true;
        }
    }, new Option("  --linker-path              Full path to linker executable", true, "--linker-path") {
        @Override
        void process(Main task, String opt, String arg) {
            task.options.linkerpath = arg;
        }
    }, new Option("  -J<flag>                   Pass <flag> directly to the runtime system", false, "-J") {
        @Override
        void process(Main task, String opt, String arg) {
        }
    }};

    static void handleOptions(Main task, String[] args) throws BadArgs {
        if (args.length == 0) {
            task.options.help = true;
            return;
        }

        // Make checkstyle happy.
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.charAt(0) == '-') {
                Option option = getOption(arg);
                String param = null;

                if (option.hasArg) {
                    if (arg.startsWith("--") && arg.indexOf('=') > 0) {
                        param = arg.substring(arg.indexOf('=') + 1, arg.length());
                    } else if (i + 1 < args.length) {
                        param = args[++i];
                    }

                    if (param == null || param.isEmpty() || param.charAt(0) == '-') {
                        throw new BadArgs("missing argument for option: {0}", arg).showUsage(true);
                    }
                }

                option.process(task, arg, param);

                if (option.ignoreRest()) {
                    break;
                }
            } else {
                task.options.files.add(new SearchFor(arg));
            }
        }
    }

    static Option getOption(String name) throws BadArgs {
        for (Option o : recognizedOptions) {
            if (o.matches(name)) {
                return o;
            }
        }
        throw new BadArgs("unknown option: {0}", name).showUsage(true);
    }

}
