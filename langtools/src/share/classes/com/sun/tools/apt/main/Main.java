/*
 * Copyright (c) 2004, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.apt.main;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.util.StringTokenizer;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import java.net.URLClassLoader;
import java.net.URL;
import java.net.MalformedURLException;

import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.util.*;

import com.sun.tools.apt.comp.AnnotationProcessingError;
import com.sun.tools.apt.comp.UsageMessageNeededException;
import com.sun.tools.apt.util.Bark;
import com.sun.mirror.apt.AnnotationProcessorFactory;

import static com.sun.tools.javac.file.Paths.pathToURLs;

/** This class provides a commandline interface to the apt build-time
 *  tool.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.
 *  If you write code that depends on this, you do so at your own
 *  risk.  This code and its internal interfaces are subject to change
 *  or deletion without notice.</b>
 */
@SuppressWarnings("deprecation")
public class Main {

    /** For testing: enter any options you want to be set implicitly
     *  here.
     */
    static String[] forcedOpts = {
        // Preserve parameter names from class files if the class was
        // compiled with debug enabled
        "-XDsave-parameter-names"
    };

    /** The name of the compiler, for use in diagnostics.
     */
    String ownName;

    /** The writer to use for diagnostic output.
     */
    PrintWriter out;


    /** Instantiated factory to use in lieu of discovery process.
     */
    AnnotationProcessorFactory providedFactory = null;

    /** Map representing original command-line arguments.
     */
    Map<String,String> origOptions = new HashMap<String, String>();

    /** Classloader to use for finding factories.
     */
    ClassLoader aptCL = null;

    /** Result codes.
     */
    static final int
        EXIT_OK = 0,        // Compilation completed with no errors.
        EXIT_ERROR = 1,     // Completed but reported errors.
        EXIT_CMDERR = 2,    // Bad command-line arguments
        EXIT_SYSERR = 3,    // System error or resource exhaustion.
        EXIT_ABNORMAL = 4;  // Compiler terminated abnormally

    /** This class represents an option recognized by the main program
     */
    private class Option {
        /** Whether or not the option is used only aptOnly.
         */
        boolean aptOnly = false;

        /** Option string.
         */
        String name;

        /** Documentation key for arguments.
         */
        String argsNameKey;

        /** Documentation key for description.
         */
        String descrKey;

        /** Suffix option (-foo=bar or -foo:bar)
         */
        boolean hasSuffix;

        Option(String name, String argsNameKey, String descrKey) {
            this.name = name;
            this.argsNameKey = argsNameKey;
            this.descrKey = descrKey;
            char lastChar = name.charAt(name.length()-1);
            hasSuffix = lastChar == ':' || lastChar == '=';
        }
        Option(String name, String descrKey) {
            this(name, null, descrKey);
        }

        public String toString() {
            return name;
        }

        /** Does this option take a (separate) operand?
         */
        boolean hasArg() {
            return argsNameKey != null && !hasSuffix;
        }

        /** Does argument string match option pattern?
         *  @param arg        The command line argument string.
         */
        boolean matches(String arg) {
            return hasSuffix ? arg.startsWith(name) : arg.equals(name);
        }

        /** For javac-only options, print nothing.
         */
        void help() {
        }

        String helpSynopsis() {
            return name +
                (argsNameKey == null ? "" :
                 ((hasSuffix ? "" : " ") +
                  getLocalizedString(argsNameKey)));
        }

        /** Print a line of documentation describing this option, if non-standard.
         */
        void xhelp() {}

        /** Process the option (with arg). Return true if error detected.
         */
        boolean process(String option, String arg) {
            options.put(option, arg);
            return false;
        }

        /** Process the option (without arg). Return true if error detected.
         */
        boolean process(String option) {
            if (hasSuffix)
                return process(name, option.substring(name.length()));
            else
                return process(option, option);
        }
    };

    private class SharedOption extends Option {
        SharedOption(String name, String argsNameKey, String descrKey) {
            super(name, argsNameKey, descrKey);
        }

        SharedOption(String name, String descrKey) {
            super(name, descrKey);
        }

        void help() {
            String s = "  " + helpSynopsis();
            out.print(s);
            for (int j = s.length(); j < 29; j++) out.print(" ");
            Bark.printLines(out, getLocalizedString(descrKey));
        }

    }

    private class AptOption extends Option {
        AptOption(String name, String argsNameKey, String descrKey) {
            super(name, argsNameKey, descrKey);
            aptOnly = true;
        }

        AptOption(String name, String descrKey) {
            super(name, descrKey);
            aptOnly = true;
        }

        /** Print a line of documentation describing this option, if standard.
         */
        void help() {
            String s = "  " + helpSynopsis();
            out.print(s);
            for (int j = s.length(); j < 29; j++) out.print(" ");
            Bark.printLines(out, getLocalizedString(descrKey));
        }

    }

    /** A nonstandard or extended (-X) option
     */
    private class XOption extends Option {
        XOption(String name, String argsNameKey, String descrKey) {
            super(name, argsNameKey, descrKey);
        }
        XOption(String name, String descrKey) {
            this(name, null, descrKey);
        }
        void help() {}
        void xhelp() {}
    };

    /** A nonstandard or extended (-X) option
     */
    private class AptXOption extends Option {
        AptXOption(String name, String argsNameKey, String descrKey) {
            super(name, argsNameKey, descrKey);
            aptOnly = true;
        }
        AptXOption(String name, String descrKey) {
            this(name, null, descrKey);
        }
        void xhelp() {
            String s = "  " + helpSynopsis();
            out.print(s);
            for (int j = s.length(); j < 29; j++) out.print(" ");
            Log.printLines(out, getLocalizedString(descrKey));
        }
    };

    /** A hidden (implementor) option
     */
    private class HiddenOption extends Option {
        HiddenOption(String name) {
            super(name, null, null);
        }
        HiddenOption(String name, String argsNameKey) {
            super(name, argsNameKey, null);
        }
        void help() {}
        void xhelp() {}
    };

    private class AptHiddenOption extends HiddenOption {
        AptHiddenOption(String name) {
            super(name);
            aptOnly = true;
        }
        AptHiddenOption(String name, String argsNameKey) {
            super(name, argsNameKey);
            aptOnly = true;
        }
    }

    private Option[] recognizedOptions = {
        new Option("-g",                                        "opt.g"),
        new Option("-g:none",                                   "opt.g.none") {
            boolean process(String option) {
                options.put("-g:", "none");
                return false;
            }
        },

        new Option("-g:{lines,vars,source}",                    "opt.g.lines.vars.source") {
            boolean matches(String s) {
                return s.startsWith("-g:");
            }
            boolean process(String option) {
                String suboptions = option.substring(3);
                options.put("-g:", suboptions);
                // enter all the -g suboptions as "-g:suboption"
                for (StringTokenizer t = new StringTokenizer(suboptions, ","); t.hasMoreTokens(); ) {
                    String tok = t.nextToken();
                    String opt = "-g:" + tok;
                    options.put(opt, opt);
                }
                return false;
            }
        },

        new XOption("-Xlint",                                   "opt.Xlint"),
        new XOption("-Xlint:{"
                    + "all,"
                    + "cast,deprecation,divzero,empty,unchecked,fallthrough,path,serial,finally,overrides,"
                    + "-cast,-deprecation,-divzero,-empty,-unchecked,-fallthrough,-path,-serial,-finally,-overrides,"
                    + "none}",
                                                                "opt.Xlint.suboptlist") {
            boolean matches(String s) {
                return s.startsWith("-Xlint:");
            }
            boolean process(String option) {
                String suboptions = option.substring(7);
                options.put("-Xlint:", suboptions);
                // enter all the -Xlint suboptions as "-Xlint:suboption"
                for (StringTokenizer t = new StringTokenizer(suboptions, ","); t.hasMoreTokens(); ) {
                    String tok = t.nextToken();
                    String opt = "-Xlint:" + tok;
                    options.put(opt, opt);
                }
                return false;
            }
        },

        new Option("-nowarn",                                   "opt.nowarn"),
        new Option("-verbose",                                  "opt.verbose"),

        // -deprecation is retained for command-line backward compatibility
        new Option("-deprecation",                              "opt.deprecation") {
                boolean process(String option) {
                    options.put("-Xlint:deprecation", option);
                    return false;
                }
            },

        new SharedOption("-classpath",     "opt.arg.path",      "opt.classpath"),
        new SharedOption("-cp",            "opt.arg.path",      "opt.classpath") {
            boolean process(String option, String arg) {
                return super.process("-classpath", arg);
            }
        },
        new Option("-sourcepath",          "opt.arg.path",      "opt.sourcepath"),
        new Option("-bootclasspath",       "opt.arg.path",      "opt.bootclasspath") {
            boolean process(String option, String arg) {
                options.remove("-Xbootclasspath/p:");
                options.remove("-Xbootclasspath/a:");
                return super.process(option, arg);
            }
        },
        new XOption("-Xbootclasspath/p:",  "opt.arg.path", "opt.Xbootclasspath.p"),
        new XOption("-Xbootclasspath/a:",  "opt.arg.path", "opt.Xbootclasspath.a"),
        new XOption("-Xbootclasspath:",    "opt.arg.path", "opt.bootclasspath") {
            boolean process(String option, String arg) {
                options.remove("-Xbootclasspath/p:");
                options.remove("-Xbootclasspath/a:");
                return super.process("-bootclasspath", arg);
            }
        },
        new Option("-extdirs",             "opt.arg.dirs",      "opt.extdirs"),
        new XOption("-Djava.ext.dirs=",    "opt.arg.dirs",      "opt.extdirs") {
            boolean process(String option, String arg) {
                return super.process("-extdirs", arg);
            }
        },
        new Option("-endorseddirs",        "opt.arg.dirs",      "opt.endorseddirs"),
        new XOption("-Djava.endorsed.dirs=","opt.arg.dirs",     "opt.endorseddirs") {
            boolean process(String option, String arg) {
                return super.process("-endorseddirs", arg);
            }
        },
        new Option("-proc:{none, only}",                        "opt.proc.none.only") {
            public boolean matches(String s) {
                return s.equals("-proc:none") || s.equals("-proc:only");
            }
        },
        new Option("-processor",        "opt.arg.class",        "opt.processor"),
        new Option("-processorpath",    "opt.arg.path",         "opt.processorpath"),

        new SharedOption("-d",          "opt.arg.path", "opt.d"),
        new SharedOption("-s",          "opt.arg.path", "opt.s"),
        new Option("-encoding",         "opt.arg.encoding",     "opt.encoding"),
        new SharedOption("-source",             "opt.arg.release",      "opt.source") {
            boolean process(String option, String operand) {
                Source source = Source.lookup(operand);
                if (source == null) {
                    error("err.invalid.source", operand);
                    return true;
                } else if (source.compareTo(Source.JDK1_5) > 0) {
                    error("err.unsupported.source.version", operand);
                    return true;
                }
                return super.process(option, operand);
            }
        },
        new Option("-target",           "opt.arg.release",      "opt.target") {
            boolean process(String option, String operand) {
                Target target = Target.lookup(operand);
                if (target == null) {
                    error("err.invalid.target", operand);
                    return true;
                } else if (target.compareTo(Target.JDK1_5) > 0) {
                    error("err.unsupported.target.version", operand);
                    return true;
                }
                return super.process(option, operand);
            }
        },
        new AptOption("-version",               "opt.version") {
            boolean process(String option) {
                Bark.printLines(out, ownName + " " + JavaCompiler.version());
                return super.process(option);
            }
        },
        new HiddenOption("-fullversion"),
        new AptOption("-help",                                  "opt.help") {
            boolean process(String option) {
                Main.this.help();
                return super.process(option);
            }
        },
        new SharedOption("-X",                                  "opt.X") {
            boolean process(String option) {
                Main.this.xhelp();
                return super.process(option);
            }
        },

        // This option exists only for the purpose of documenting itself.
        // It's actually implemented by the launcher.
        new AptOption("-J",             "opt.arg.flag",         "opt.J") {
            String helpSynopsis() {
                hasSuffix = true;
                return super.helpSynopsis();
            }
            boolean process(String option) {
                throw new AssertionError
                    ("the -J flag should be caught by the launcher.");
            }
        },


        new SharedOption("-A",          "opt.proc.flag",        "opt.A") {
                String helpSynopsis() {
                    hasSuffix = true;
                    return super.helpSynopsis();
                }

                boolean matches(String arg) {
                    return arg.startsWith("-A");
                }

                boolean hasArg() {
                    return false;
                }

                boolean process(String option) {
                    return process(option, option);
                }
            },

        new AptOption("-nocompile",     "opt.nocompile"),

        new AptOption("-print",         "opt.print"),

        new AptOption("-factorypath", "opt.arg.path", "opt.factorypath"),

        new AptOption("-factory",     "opt.arg.class", "opt.factory"),

        new AptXOption("-XListAnnotationTypes", "opt.XListAnnotationTypes"),

        new AptXOption("-XListDeclarations",    "opt.XListDeclarations"),

        new AptXOption("-XPrintAptRounds",      "opt.XPrintAptRounds"),

        new AptXOption("-XPrintFactoryInfo",    "opt.XPrintFactoryInfo"),

        /*
         * Option to treat both classes and source files as
         * declarations that can be given on the command line and
         * processed as the result of an apt round.
         */
        new AptXOption("-XclassesAsDecls", "opt.XClassesAsDecls"),

        // new Option("-moreinfo",                                      "opt.moreinfo") {
        new HiddenOption("-moreinfo") {
            boolean process(String option) {
                Type.moreInfo = true;
                return super.process(option);
            }
        },

        // treat warnings as errors
        new HiddenOption("-Werror"),

        // use complex inference from context in the position of a method call argument
        new HiddenOption("-complexinference"),

        // prompt after each error
        // new Option("-prompt",                                        "opt.prompt"),
        new HiddenOption("-prompt"),

        // dump stack on error
        new HiddenOption("-doe"),

        // display warnings for generic unchecked and unsafe operations
        new HiddenOption("-warnunchecked") {
            boolean process(String option) {
                options.put("-Xlint:unchecked", option);
                return false;
            }
        },

        new HiddenOption("-Xswitchcheck") {
            boolean process(String option) {
                options.put("-Xlint:switchcheck", option);
                return false;
            }
        },

        // generate trace output for subtyping operations
        new HiddenOption("-debugsubtyping"),

        new XOption("-Xmaxerrs",        "opt.arg.number",       "opt.maxerrs"),
        new XOption("-Xmaxwarns",       "opt.arg.number",       "opt.maxwarns"),
        new XOption("-Xstdout",         "opt.arg.file",         "opt.Xstdout") {
            boolean process(String option, String arg) {
                try {
                    out = new PrintWriter(new FileWriter(arg), true);
                } catch (java.io.IOException e) {
                    error("err.error.writing.file", arg, e);
                    return true;
                }
                return super.process(option, arg);
            }
        },

        new XOption("-Xprint",                                  "opt.print"),

        new XOption("-XprintRounds",                            "opt.printRounds"),

        new XOption("-XprintProcessorInfo",                     "opt.printProcessorInfo"),


        /* -O is a no-op, accepted for backward compatibility. */
        new HiddenOption("-O"),

        /* -Xjcov produces tables to support the code coverage tool jcov. */
        new HiddenOption("-Xjcov"),

        /* This is a back door to the compiler's option table.
         * -Dx=y sets the option x to the value y.
         * -Dx sets the option x to the value x.
         */
        new HiddenOption("-XD") {
            String s;
            boolean matches(String s) {
                this.s = s;
                return s.startsWith(name);
            }
            boolean process(String option) {
                s = s.substring(name.length());
                int eq = s.indexOf('=');
                String key = (eq < 0) ? s : s.substring(0, eq);
                String value = (eq < 0) ? s : s.substring(eq+1);
                options.put(key, value);
                return false;
            }
        },

        new HiddenOption("sourcefile") {
                String s;
                boolean matches(String s) {
                    this.s = s;
                    return s.endsWith(".java") ||
                        (options.get("-XclassesAsDecls") != null);
                }
                boolean process(String option) {
                    if (s.endsWith(".java")) {
                        if (!sourceFileNames.contains(s))
                            sourceFileNames.add(s);
                    } else if (options.get("-XclassesAsDecls") != null) {
                        classFileNames.add(s);
                    }
                    return false;
                }
            },
    };

    /**
     * Construct a compiler instance.
     */
    public Main(String name) {
        this(name, new PrintWriter(System.err, true));
    }

    /**
     * Construct a compiler instance.
     */
    public Main(String name, PrintWriter out) {
        this.ownName = name;
        this.out = out;
    }

    /** A table of all options that's passed to the JavaCompiler constructor.  */
    private Options options = null;

    /** The list of source files to process
     */
    java.util.List<String> sourceFileNames = new java.util.LinkedList<String>();

    /** The list of class files to process
     */
    java.util.List<String> classFileNames = new java.util.LinkedList<String>();

    /** List of top level names of generated source files from most recent apt round.
     */
    java.util.Set<String> genSourceFileNames = new java.util.LinkedHashSet<String>();

    /** List of names of generated class files from most recent apt round.
     */
    java.util.Set<String> genClassFileNames  = new java.util.LinkedHashSet<String>();

    /**
     * List of all the generated source file names across all apt rounds.
     */
    java.util.Set<String> aggregateGenSourceFileNames = new java.util.LinkedHashSet<String>();

    /**
     * List of all the generated class file names across all apt rounds.
     */
    java.util.Set<String> aggregateGenClassFileNames  = new java.util.LinkedHashSet<String>();

    /**
     * List of all the generated file names across all apt rounds.
     */
    java.util.Set<java.io.File> aggregateGenFiles = new java.util.LinkedHashSet<java.io.File>();

    /**
     * Set of all factories that have provided a processor on some apt round.
     */
    java.util.Set<Class<? extends AnnotationProcessorFactory> > productiveFactories  =
        new java.util.LinkedHashSet<Class<? extends AnnotationProcessorFactory> >();



    /** Print a string that explains usage.
     */
    void help() {
        Bark.printLines(out, getLocalizedString("msg.usage.header", ownName));
        for (int i=0; i < recognizedOptions.length; i++) {
            recognizedOptions[i].help();
        }
        Bark.printLines(out, getLocalizedString("msg.usage.footer"));
        out.println();
    }

    /** Print a string that explains usage for X options.
     */
    void xhelp() {
        for (int i=0; i<recognizedOptions.length; i++) {
            recognizedOptions[i].xhelp();
        }
        out.println();
        Bark.printLines(out, getLocalizedString("msg.usage.nonstandard.footer"));
    }

    /** Report a usage error.
     */
    void error(String key, Object... args) {
        warning(key, args);
        help();
    }

    /** Report a warning.
     */
    void warning(String key, Object... args) {
        Bark.printLines(out, ownName + ": "
                       + getLocalizedString(key, args));
    }

    /** Process command line arguments: store all command line options
     *  in `options' table and return all source filenames.
     *  @param args    The array of command line arguments.
     */
    protected java.util.List<String> processArgs(String[] flags) {
        int ac = 0;
        while (ac < flags.length) {
            String flag = flags[ac];
            ac++;

            int j;
            for (j=0; j < recognizedOptions.length; j++)
                if (recognizedOptions[j].matches(flag))
                    break;

            if (j == recognizedOptions.length) {
                error("err.invalid.flag", flag);
                return null;
            }

            Option option = recognizedOptions[j];
            if (option.hasArg()) {
                if (ac == flags.length) {
                    error("err.req.arg", flag);
                    return null;
                }
                String operand = flags[ac];
                ac++;
                if (option.process(flag, operand))
                    return null;
            } else {
                if (option.process(flag))
                    return null;
            }
        }

        String sourceString = options.get("-source");
        Source source = (sourceString != null)
            ? Source.lookup(sourceString)
            : Source.JDK1_5; // JDK 5 is the latest supported source version
        String targetString = options.get("-target");
        Target target = (targetString != null)
            ? Target.lookup(targetString)
            : Target.JDK1_5; // JDK 5 is the latest supported source version
        // We don't check source/target consistency for CLDC, as J2ME
        // profiles are not aligned with J2SE targets; moreover, a
        // single CLDC target may have many profiles.  In addition,
        // this is needed for the continued functioning of the JSR14
        // prototype.
        if (Character.isDigit(target.name.charAt(0)) &&
            target.compareTo(source.requiredTarget()) < 0) {
            if (targetString != null) {
                if (sourceString == null) {
                    warning("warn.target.default.source.conflict",
                            targetString,
                            source.requiredTarget().name);
                } else {
                    warning("warn.source.target.conflict",
                            sourceString,
                            source.requiredTarget().name);
                }
                return null;
            } else {
                options.put("-target", source.requiredTarget().name);
            }
        }
        return sourceFileNames;
    }

    /** Programmatic interface for main function.
     * @param args    The command line parameters.
     */
    public int compile(String[] args, AnnotationProcessorFactory factory) {
        int returnCode = 0;
        providedFactory = factory;

        Context context = new Context();
        JavacFileManager.preRegister(context);
        options = Options.instance(context);
        Bark bark;

        /*
         * Process the command line options to create the intial
         * options data.  This processing is at least partially reused
         * by any recursive apt calls.
         */

        // For testing: assume all arguments in forcedOpts are
        // prefixed to command line arguments.
        processArgs(forcedOpts);

        /*
         * A run of apt only gets passed the most recently generated
         * files; the initial run of apt gets passed the files from
         * the command line.
         */

        java.util.List<String> origFilenames;
        try {
            // assign args the result of parse to capture results of
            // '@file' expansion
            origFilenames = processArgs((args=CommandLine.parse(args)));

            if (options.get("suppress-tool-api-removal-message") == null) {
                Bark.printLines(out, getLocalizedString("misc.Deprecation"));
            }

            if (origFilenames == null) {
                return EXIT_CMDERR;
            } else if (origFilenames.size() == 0) {
                // it is allowed to compile nothing if just asking for help
                if (options.get("-help") != null ||
                    options.get("-X") != null)
                    return EXIT_OK;
            }
        } catch (java.io.FileNotFoundException e) {
            Bark.printLines(out, ownName + ": " +
                           getLocalizedString("err.file.not.found",
                                              e.getMessage()));
            return EXIT_SYSERR;
        } catch (IOException ex) {
            ioMessage(ex);
            return EXIT_SYSERR;
        } catch (OutOfMemoryError ex) {
            resourceMessage(ex);
            return EXIT_SYSERR;
        } catch (StackOverflowError ex) {
            resourceMessage(ex);
            return EXIT_SYSERR;
        } catch (FatalError ex) {
            feMessage(ex);
            return EXIT_SYSERR;
        } catch (sun.misc.ServiceConfigurationError sce) {
            sceMessage(sce);
            return EXIT_ABNORMAL;
        } catch (Throwable ex) {
            bugMessage(ex);
            return EXIT_ABNORMAL;
        }


        boolean firstRound = true;
        boolean needSourcePath = false;
        boolean needClassPath  = false;
        boolean classesAsDecls = options.get("-XclassesAsDecls") != null;

        /*
         * Create augumented classpath and sourcepath values.
         *
         * If any of the prior apt rounds generated any new source
         * files, the n'th apt round (and any javac invocation) has the
         * source destination path ("-s path") as the last element of
         * the "-sourcepath" to the n'th call.
         *
         * If any of the prior apt rounds generated any new class files,
         * the n'th apt round (and any javac invocation) has the class
         * destination path ("-d path") as the last element of the
         * "-classpath" to the n'th call.
         */
        String augmentedSourcePath = "";
        String augmentedClassPath = "";
        String baseClassPath = "";

        try {
            /*
             * Record original options for future annotation processor
             * invocations.
             */
            origOptions = new HashMap<String, String>(options.size());
            for(String s: options.keySet()) {
                String value;
                if (s.equals(value = options.get(s)))
                    origOptions.put(s, (String)null);
                else
                    origOptions.put(s, value);
            }
            origOptions = Collections.unmodifiableMap(origOptions);

            JavacFileManager fm = (JavacFileManager) context.get(JavaFileManager.class);
            {
                // Note: it might be necessary to check for an empty
                // component ("") of the source path or class path

                String sourceDest = options.get("-s");
                if (fm.hasLocation(StandardLocation.SOURCE_PATH)) {
                    for(File f: fm.getLocation(StandardLocation.SOURCE_PATH))
                        augmentedSourcePath += (f + File.pathSeparator);
                    augmentedSourcePath += (sourceDest == null)?".":sourceDest;
                } else {
                    augmentedSourcePath = ".";

                    if (sourceDest != null)
                        augmentedSourcePath += (File.pathSeparator + sourceDest);
                }

                String classDest = options.get("-d");
                if (fm.hasLocation(StandardLocation.CLASS_PATH)) {
                    for(File f: fm.getLocation(StandardLocation.CLASS_PATH))
                        baseClassPath += (f + File.pathSeparator);
                    // put baseClassPath into map to handle any
                    // value needed for the classloader
                    options.put("-classpath", baseClassPath);

                    augmentedClassPath = baseClassPath + ((classDest == null)?".":classDest);
                } else {
                    baseClassPath = ".";
                    if (classDest != null)
                        augmentedClassPath = baseClassPath + (File.pathSeparator + classDest);
                }
                assert options.get("-classpath") != null;
            }

            /*
             * Create base and augmented class loaders
             */
            ClassLoader augmentedAptCL = null;
            {
            /*
             * Use a url class loader to look for classes on the
             * user-specified class path. Prepend computed bootclass
             * path, which includes extdirs, to the URLClassLoader apt
             * uses.
             */
                String aptclasspath = "";
                String bcp = "";
                Iterable<? extends File> bootclasspath = fm.getLocation(StandardLocation.PLATFORM_CLASS_PATH);

                if (bootclasspath != null) {
                    for(File f: bootclasspath)
                        bcp += (f + File.pathSeparator);
                }

                // If the factory path is set, use that path
                if (providedFactory == null)
                    aptclasspath = options.get("-factorypath");
                if (aptclasspath == null)
                    aptclasspath = options.get("-classpath");

                assert aptclasspath != null;
                aptclasspath = (bcp + aptclasspath);
                aptCL = new URLClassLoader(pathToURLs(aptclasspath));

                if (providedFactory == null &&
                    options.get("-factorypath") != null) // same CL even if new class files written
                    augmentedAptCL = aptCL;
                else {
                    // Create class loader in case new class files are
                    // written
                    augmentedAptCL = new URLClassLoader(pathToURLs(augmentedClassPath.
                                                                   substring(baseClassPath.length())),
                                                        aptCL);
                }
            }

            int round = 0; // For -XPrintAptRounds
            do {
                round++;

                Context newContext = new Context();
                Options newOptions = Options.instance(newContext); // creates a new context
                newOptions.putAll(options);

                // populate with old options... don't bother reparsing command line, etc.

                // if genSource files, must add destination to source path
                if (genSourceFileNames.size() > 0 && !firstRound) {
                    newOptions.put("-sourcepath", augmentedSourcePath);
                    needSourcePath = true;
                }
                aggregateGenSourceFileNames.addAll(genSourceFileNames);
                sourceFileNames.addAll(genSourceFileNames);
                genSourceFileNames.clear();

                // Don't really need to track this; just have to add -d
                // "foo" to class path if any class files are generated
                if (genClassFileNames.size() > 0) {
                    newOptions.put("-classpath", augmentedClassPath);
                    aptCL = augmentedAptCL;
                    needClassPath = true;
                }
                aggregateGenClassFileNames.addAll(genClassFileNames);
                classFileNames.addAll(genClassFileNames);
                genClassFileNames.clear();

                options = newOptions;

                if (options.get("-XPrintAptRounds") != null) {
                    out.println("apt Round : " + round);
                    out.println("filenames: " + sourceFileNames);
                    if (classesAsDecls)
                        out.println("classnames: " + classFileNames);
                    out.println("options: " + options);
                }

                returnCode = compile(args, newContext);
                firstRound = false;

                // Check for reported errors before continuing
                bark = Bark.instance(newContext);
            } while(((genSourceFileNames.size() != 0 ) ||
                     (classesAsDecls && genClassFileNames.size() != 0)) &&
                    bark.nerrors == 0);
        } catch (UsageMessageNeededException umne) {
            help();
            return EXIT_CMDERR; // will cause usage message to be printed
        }

        /*
         * Do not compile if a processor has reported an error or if
         * there are no source files to process.  A more sophisticated
         * test would also fail for syntax errors caught by javac.
         */
        if (options.get("-nocompile") == null &&
            options.get("-print")     == null &&
            bark.nerrors == 0 &&
            (origFilenames.size() > 0 || aggregateGenSourceFileNames.size() > 0 )) {
            /*
             * Need to create new argument string for calling javac:
             * 1. apt specific arguments (e.g. -factory) must be stripped out
             * 2. proper settings for sourcepath and classpath must be used
             * 3. generated class names must be added
             * 4. class file names as declarations must be removed
             */

            int newArgsLength = args.length +
                (needSourcePath?1:0) +
                (needClassPath?1:0) +
                aggregateGenSourceFileNames.size();

            // Null out apt-specific options and don't copy over into
            // newArgs. This loop should be a lot faster; the options
            // array should be replaced with a better data structure
            // which includes a map from strings to options.
            //
            // If treating classes as declarations, must strip out
            // class names from the javac argument list
            argLoop:
            for(int i = 0; i < args.length; i++) {
                int matchPosition = -1;

                // "-A" by itself is recognized by apt but not javac
                if (args[i] != null && args[i].equals("-A")) {
                    newArgsLength--;
                    args[i] = null;
                    continue argLoop;
                } else {
                    optionLoop:
                    for(int j = 0; j < recognizedOptions.length; j++) {
                        if (args[i] != null && recognizedOptions[j].matches(args[i])) {
                            matchPosition = j;
                            break optionLoop;
                        }
                    }

                    if (matchPosition != -1) {
                        Option op = recognizedOptions[matchPosition];
                        if (op.aptOnly) {
                            newArgsLength--;
                            args[i] = null;
                            if (op.hasArg()) {
                                newArgsLength--;
                                args[i+1] = null;
                            }
                        } else {
                            if (op.hasArg()) { // skip over next string
                                i++;
                                continue argLoop;
                            }

                            if ((options.get("-XclassesAsDecls") != null) &&
                                (matchPosition == (recognizedOptions.length-1)) ){
                                // Remove class file names from
                                // consideration by javac.
                                if (! args[i].endsWith(".java")) {
                                    newArgsLength--;
                                    args[i] = null;
                                }
                            }
                        }
                    }
                }
            }

            String newArgs[] = new String[newArgsLength];

            int j = 0;
            for(int i=0; i < args.length; i++) {
                if (args[i] != null)
                    newArgs[j++] = args[i];
            }

            if (needClassPath)
                newArgs[j++] = "-XD-classpath=" + augmentedClassPath;

            if (needSourcePath) {
                newArgs[j++] = "-XD-sourcepath=" + augmentedSourcePath;

                for(String s: aggregateGenSourceFileNames)
                    newArgs[j++] = s;
            }

            returnCode = com.sun.tools.javac.Main.compile(newArgs);
        }

        return returnCode;
    }

    /** Programmatic interface for main function.
     * @param args    The command line parameters.
     */
    int compile(String[] args, Context context) {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        if (!assertionsEnabled) {
            // Bark.printLines(out, "fatal error: assertions must be enabled when running javac");
            // return EXIT_ABNORMAL;
        }
        int exitCode = EXIT_OK;

        JavaCompiler comp = null;
        try {
            context.put(Bark.outKey, out);

            comp = JavaCompiler.instance(context);
            if (comp == null)
                return EXIT_SYSERR;

            java.util.List<String> nameList = new java.util.LinkedList<String>();
            nameList.addAll(sourceFileNames);
            if (options.get("-XclassesAsDecls") != null)
                nameList.addAll(classFileNames);

            List<Symbol.ClassSymbol> cs
                = comp.compile(List.from(nameList.toArray(new String[0])),
                               origOptions,
                               aptCL,
                               providedFactory,
                               productiveFactories,
                               aggregateGenFiles);

            /*
             * If there aren't new source files, we shouldn't bother
             *  running javac if there were errors.
             *
             * If there are new files, we should try running javac in
             * case there were typing errors.
             *
             */

            if (comp.errorCount() != 0 ||
                options.get("-Werror") != null && comp.warningCount() != 0)
                return EXIT_ERROR;
        } catch (IOException ex) {
            ioMessage(ex);
            return EXIT_SYSERR;
        } catch (OutOfMemoryError ex) {
            resourceMessage(ex);
            return EXIT_SYSERR;
        } catch (StackOverflowError ex) {
            resourceMessage(ex);
            return EXIT_SYSERR;
        } catch (FatalError ex) {
            feMessage(ex);
            return EXIT_SYSERR;
        } catch (UsageMessageNeededException umne) {
            help();
            return EXIT_CMDERR; // will cause usage message to be printed
        } catch (AnnotationProcessingError ex) {
            apMessage(ex);
            return EXIT_ABNORMAL;
        } catch (sun.misc.ServiceConfigurationError sce) {
            sceMessage(sce);
            return EXIT_ABNORMAL;
        } catch (Throwable ex) {
            bugMessage(ex);
            return EXIT_ABNORMAL;
        } finally {
            if (comp != null) {
                comp.close();
                genSourceFileNames.addAll(comp.getSourceFileNames());
                genClassFileNames.addAll(comp.getClassFileNames());
            }
            sourceFileNames = new java.util.LinkedList<String>();
            classFileNames  = new java.util.LinkedList<String>();
        }
        return exitCode;
    }

    /** Print a message reporting an internal error.
     */
    void bugMessage(Throwable ex) {
        Bark.printLines(out, getLocalizedString("msg.bug",
                                               JavaCompiler.version()));
        ex.printStackTrace(out);
    }

    /** Print a message reporting an fatal error.
     */
    void apMessage(AnnotationProcessingError ex) {
        Bark.printLines(out, getLocalizedString("misc.Problem"));
        ex.getCause().printStackTrace(out);
    }

    /** Print a message about sun.misc.Service problem.
     */
    void sceMessage(sun.misc.ServiceConfigurationError ex) {
        Bark.printLines(out, getLocalizedString("misc.SunMiscService"));
        ex.printStackTrace(out);
    }

    /** Print a message reporting an fatal error.
     */
    void feMessage(Throwable ex) {
        Bark.printLines(out, ex.toString());
    }

    /** Print a message reporting an input/output error.
     */
    void ioMessage(Throwable ex) {
        Bark.printLines(out, getLocalizedString("msg.io"));
        ex.printStackTrace(out);
    }

    /** Print a message reporting an out-of-resources error.
     */
    void resourceMessage(Throwable ex) {
        Bark.printLines(out, getLocalizedString("msg.resource"));
        ex.printStackTrace(out);
    }

    /* ************************************************************************
     * Internationalization
     *************************************************************************/

    /** Find a localized string in the resource bundle.
     *  @param key     The key for the localized string.
     */
    private static String getLocalizedString(String key, Object... args) {
        return getText(key, args);
    }

    private static final String javacRB =
        "com.sun.tools.javac.resources.javac";

    private static final String aptRB =
        "com.sun.tools.apt.resources.apt";

    private static ResourceBundle messageRBjavac;
    private static ResourceBundle messageRBapt;

    /** Initialize ResourceBundle.
     */
    private static void initResource() {
        try {
            messageRBapt   = ResourceBundle.getBundle(aptRB);
            messageRBjavac = ResourceBundle.getBundle(javacRB);
        } catch (MissingResourceException e) {
            Error x = new FatalError("Fatal Error: Resource for apt or javac is missing");
            x.initCause(e);
            throw x;
        }
    }

    /** Get and format message string from resource.
     */
    private static String getText(String key, Object... _args) {
        String[] args = new String[_args.length];
        for (int i=0; i<_args.length; i++) {
            args[i] = "" + _args[i];
        }
        if (messageRBapt == null || messageRBjavac == null )
            initResource();
        try {
            return MessageFormat.format(messageRBapt.getString("apt." + key),
                                        (Object[]) args);
        } catch (MissingResourceException e) {
            try {
                return MessageFormat.format(messageRBjavac.getString("javac." + key),
                                            (Object[]) args);
            } catch (MissingResourceException f) {
                String msg = "apt or javac message file broken: key={0} "
                    + "arguments={1}, {2}";
                return MessageFormat.format(msg, (Object[]) args);
            }
        }
    }
}
