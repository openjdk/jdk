/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

package com.sun.tools.javac.main;

import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.main.JavacOption.HiddenOption;
import com.sun.tools.javac.main.JavacOption.Option;
import com.sun.tools.javac.main.JavacOption.XOption;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.Set;
import java.util.StringTokenizer;
import javax.lang.model.SourceVersion;

import static com.sun.tools.javac.main.OptionName.*;

/**
 * TODO: describe com.sun.tools.javac.main.RecognizedOptions
 *
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class RecognizedOptions {

    private RecognizedOptions() {}

    public interface OptionHelper {

        void setOut(PrintWriter out);

        void error(String key, Object... args);

        void printVersion();

        void printFullVersion();

        void printHelp();

        void printXhelp();

        void addFile(File f);

        void addClassName(String s);

    }

    public static class GrumpyHelper implements OptionHelper {

        public void setOut(PrintWriter out) {
            throw new IllegalArgumentException();
        }

        public void error(String key, Object... args) {
            throw new IllegalArgumentException(Main.getLocalizedString(key, args));
        }

        public void printVersion() {
            throw new IllegalArgumentException();
        }

        public void printFullVersion() {
            throw new IllegalArgumentException();
        }

        public void printHelp() {
            throw new IllegalArgumentException();
        }

        public void printXhelp() {
            throw new IllegalArgumentException();
        }

        public void addFile(File f) {
            throw new IllegalArgumentException(f.getPath());
        }

        public void addClassName(String s) {
            throw new IllegalArgumentException(s);
        }

    }

    static Set<OptionName> javacOptions = EnumSet.of(
        G,
        G_NONE,
        G_CUSTOM,
        XLINT,
        XLINT_CUSTOM,
        NOWARN,
        VERBOSE,
        DEPRECATION,
        CLASSPATH,
        CP,
        SOURCEPATH,
        BOOTCLASSPATH,
        XBOOTCLASSPATH_PREPEND,
        XBOOTCLASSPATH_APPEND,
        XBOOTCLASSPATH,
        EXTDIRS,
        DJAVA_EXT_DIRS,
        ENDORSEDDIRS,
        DJAVA_ENDORSED_DIRS,
        PROC_CUSTOM,
        PROCESSOR,
        PROCESSORPATH,
        D,
        S,
        IMPLICIT,
        ENCODING,
        SOURCE,
        TARGET,
        VERSION,
        FULLVERSION,
        HELP,
        A,
        X,
        J,
        MOREINFO,
        WERROR,
        // COMPLEXINFERENCE,
        PROMPT,
        DOE,
        PRINTSOURCE,
        WARNUNCHECKED,
        XMAXERRS,
        XMAXWARNS,
        XSTDOUT,
        XPRINT,
        XPRINTROUNDS,
        XPRINTPROCESSORINFO,
        XPREFER,
        O,
        XJCOV,
        XD,
        SOURCEFILE);

    static Set<OptionName> javacFileManagerOptions = EnumSet.of(
        CLASSPATH,
        CP,
        SOURCEPATH,
        BOOTCLASSPATH,
        XBOOTCLASSPATH_PREPEND,
        XBOOTCLASSPATH_APPEND,
        XBOOTCLASSPATH,
        EXTDIRS,
        DJAVA_EXT_DIRS,
        ENDORSEDDIRS,
        DJAVA_ENDORSED_DIRS,
        PROCESSORPATH,
        D,
        S,
        ENCODING,
        SOURCE);

    static Set<OptionName> javacToolOptions = EnumSet.of(
        G,
        G_NONE,
        G_CUSTOM,
        XLINT,
        XLINT_CUSTOM,
        NOWARN,
        VERBOSE,
        DEPRECATION,
        PROC_CUSTOM,
        PROCESSOR,
        IMPLICIT,
        SOURCE,
        TARGET,
        // VERSION,
        // FULLVERSION,
        // HELP,
        A,
        // X,
        // J,
        MOREINFO,
        WERROR,
        // COMPLEXINFERENCE,
        PROMPT,
        DOE,
        PRINTSOURCE,
        WARNUNCHECKED,
        XMAXERRS,
        XMAXWARNS,
        // XSTDOUT,
        XPRINT,
        XPRINTROUNDS,
        XPRINTPROCESSORINFO,
        XPREFER,
        O,
        XJCOV,
        XD);

    static Option[] getJavaCompilerOptions(OptionHelper helper) {
        return getOptions(helper, javacOptions);
    }

    public static Option[] getJavacFileManagerOptions(OptionHelper helper) {
        return getOptions(helper, javacFileManagerOptions);
    }

    public static Option[] getJavacToolOptions(OptionHelper helper) {
        return getOptions(helper, javacToolOptions);
    }

    static Option[] getOptions(OptionHelper helper, Set<OptionName> desired) {
        ListBuffer<Option> options = new ListBuffer<Option>();
        for (Option option : getAll(helper))
            if (desired.contains(option.getName()))
                options.append(option);
        return options.toArray(new Option[options.length()]);
    }

    /**
     * @param out the writer to use for diagnostic output
     */
    public static Option[] getAll(final OptionHelper helper) {
        return new Option[]{
        new Option(G,                                           "opt.g"),
        new Option(G_NONE,                                      "opt.g.none") {
            public boolean process(Options options, String option) {
                options.put("-g:", "none");
                return false;
            }
        },

        new Option(G_CUSTOM,                                    "opt.g.lines.vars.source") {
            public boolean matches(String s) {
                return s.startsWith("-g:");
            }
            public boolean process(Options options, String option) {
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

        new XOption(XLINT,                                      "opt.Xlint"),
        new XOption(XLINT_CUSTOM,                               "opt.Xlint.suboptlist") {
            public boolean matches(String s) {
                return s.startsWith("-Xlint:");
            }
            public boolean process(Options options, String option) {
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

        // -nowarn is retained for command-line backward compatibility
        new Option(NOWARN,                                      "opt.nowarn") {
                public boolean process(Options options, String option) {
                    options.put("-Xlint:none", option);
                    return false;
                }
            },

        new Option(VERBOSE,                                     "opt.verbose"),

        // -deprecation is retained for command-line backward compatibility
        new Option(DEPRECATION,                                 "opt.deprecation") {
                public boolean process(Options options, String option) {
                    options.put("-Xlint:deprecation", option);
                    return false;
                }
            },

        new Option(CLASSPATH,              "opt.arg.path",      "opt.classpath"),
        new Option(CP,                     "opt.arg.path",      "opt.classpath") {
            public boolean process(Options options, String option, String arg) {
                return super.process(options, "-classpath", arg);
            }
        },
        new Option(SOURCEPATH,             "opt.arg.path",      "opt.sourcepath"),
        new Option(BOOTCLASSPATH,          "opt.arg.path",      "opt.bootclasspath") {
            public boolean process(Options options, String option, String arg) {
                options.remove("-Xbootclasspath/p:");
                options.remove("-Xbootclasspath/a:");
                return super.process(options, option, arg);
            }
        },
        new XOption(XBOOTCLASSPATH_PREPEND,"opt.arg.path", "opt.Xbootclasspath.p"),
        new XOption(XBOOTCLASSPATH_APPEND, "opt.arg.path", "opt.Xbootclasspath.a"),
        new XOption(XBOOTCLASSPATH,        "opt.arg.path", "opt.bootclasspath") {
            public boolean process(Options options, String option, String arg) {
                options.remove("-Xbootclasspath/p:");
                options.remove("-Xbootclasspath/a:");
                return super.process(options, "-bootclasspath", arg);
            }
        },
        new Option(EXTDIRS,                "opt.arg.dirs",      "opt.extdirs"),
        new XOption(DJAVA_EXT_DIRS,        "opt.arg.dirs",      "opt.extdirs") {
            public boolean process(Options options, String option, String arg) {
                return super.process(options, "-extdirs", arg);
            }
        },
        new Option(ENDORSEDDIRS,            "opt.arg.dirs",     "opt.endorseddirs"),
        new XOption(DJAVA_ENDORSED_DIRS,    "opt.arg.dirs",     "opt.endorseddirs") {
            public boolean process(Options options, String option, String arg) {
                return super.process(options, "-endorseddirs", arg);
            }
        },
        new Option(PROC_CUSTOM,                                 "opt.proc.none.only") {
            public boolean matches(String s) {
                return s.equals("-proc:none") || s.equals("-proc:only");
            }

            public boolean process(Options options, String option) {
                if (option.equals("-proc:none")) {
                    options.remove("-proc:only");
                } else {
                    options.remove("-proc:none");
                }
                options.put(option, option);
                return false;
            }
        },
        new Option(PROCESSOR,           "opt.arg.class.list",   "opt.processor"),
        new Option(PROCESSORPATH,       "opt.arg.path",         "opt.processorpath"),
        new Option(D,                   "opt.arg.directory",    "opt.d"),
        new Option(S,                   "opt.arg.directory",    "opt.sourceDest"),
        new Option(IMPLICIT,                                    "opt.implicit") {
            public boolean matches(String s) {
                return s.equals("-implicit:none") || s.equals("-implicit:class");
            }
            public boolean process(Options options, String option, String operand) {
                int sep = option.indexOf(":");
                options.put(option.substring(0, sep), option.substring(sep+1));
                options.put(option,option);
                return false;
            }
        },
        new Option(ENCODING,            "opt.arg.encoding",     "opt.encoding"),
        new Option(SOURCE,              "opt.arg.release",      "opt.source") {
            public boolean process(Options options, String option, String operand) {
                Source source = Source.lookup(operand);
                if (source == null) {
                    helper.error("err.invalid.source", operand);
                    return true;
                }
                return super.process(options, option, operand);
            }
        },
        new Option(TARGET,              "opt.arg.release",      "opt.target") {
            public boolean process(Options options, String option, String operand) {
                Target target = Target.lookup(operand);
                if (target == null) {
                    helper.error("err.invalid.target", operand);
                    return true;
                }
                return super.process(options, option, operand);
            }
        },
        new Option(VERSION,                                     "opt.version") {
            public boolean process(Options options, String option) {
                helper.printVersion();
                return super.process(options, option);
            }
        },
        new HiddenOption(FULLVERSION) {
            public boolean process(Options options, String option) {
                helper.printFullVersion();
                return super.process(options, option);
            }
        },
        new Option(HELP,                                        "opt.help") {
            public boolean process(Options options, String option) {
                helper.printHelp();
                return super.process(options, option);
            }
        },
        new Option(A,                "opt.arg.key.equals.value","opt.A") {
                String helpSynopsis() {
                    hasSuffix = true;
                    return super.helpSynopsis();
                }

                public boolean matches(String arg) {
                    return arg.startsWith("-A");
                }

                public boolean hasArg() {
                    return false;
                }
                // Mapping for processor options created in
                // JavacProcessingEnvironment
                public boolean process(Options options, String option) {
                    int argLength = option.length();
                    if (argLength == 2) {
                        helper.error("err.empty.A.argument");
                        return true;
                    }
                    int sepIndex = option.indexOf('=');
                    String key = option.substring(2, (sepIndex != -1 ? sepIndex : argLength) );
                    if (!JavacProcessingEnvironment.isValidOptionName(key)) {
                        helper.error("err.invalid.A.key", option);
                        return true;
                    }
                    return process(options, option, option);
                }
        },
        new Option(X,                                           "opt.X") {
            public boolean process(Options options, String option) {
                helper.printXhelp();
                return super.process(options, option);
            }
        },

        // This option exists only for the purpose of documenting itself.
        // It's actually implemented by the launcher.
        new Option(J,                   "opt.arg.flag",         "opt.J") {
            String helpSynopsis() {
                hasSuffix = true;
                return super.helpSynopsis();
            }
            public boolean process(Options options, String option) {
                throw new AssertionError
                    ("the -J flag should be caught by the launcher.");
            }
        },

        // stop after parsing and attributing.
        // new HiddenOption("-attrparseonly"),

        // new Option("-moreinfo",                                      "opt.moreinfo") {
        new HiddenOption(MOREINFO) {
            public boolean process(Options options, String option) {
                Type.moreInfo = true;
                return super.process(options, option);
            }
        },

        // treat warnings as errors
        new HiddenOption(WERROR),

        // use complex inference from context in the position of a method call argument
        new HiddenOption(COMPLEXINFERENCE),

        // generare source stubs
        // new HiddenOption("-stubs"),

        // relax some constraints to allow compiling from stubs
        // new HiddenOption("-relax"),

        // output source after translating away inner classes
        // new Option("-printflat",                             "opt.printflat"),
        // new HiddenOption("-printflat"),

        // display scope search details
        // new Option("-printsearch",                           "opt.printsearch"),
        // new HiddenOption("-printsearch"),

        // prompt after each error
        // new Option("-prompt",                                        "opt.prompt"),
        new HiddenOption(PROMPT),

        // dump stack on error
        new HiddenOption(DOE),

        // output source after type erasure
        // new Option("-s",                                     "opt.s"),
        new HiddenOption(PRINTSOURCE),

        // output shrouded class files
        // new Option("-scramble",                              "opt.scramble"),
        // new Option("-scrambleall",                           "opt.scrambleall"),

        // display warnings for generic unchecked operations
        new HiddenOption(WARNUNCHECKED) {
            public boolean process(Options options, String option) {
                options.put("-Xlint:unchecked", option);
                return false;
            }
        },

        new XOption(XMAXERRS,           "opt.arg.number",       "opt.maxerrs"),
        new XOption(XMAXWARNS,          "opt.arg.number",       "opt.maxwarns"),
        new XOption(XSTDOUT,            "opt.arg.file",         "opt.Xstdout") {
            public boolean process(Options options, String option, String arg) {
                try {
                    helper.setOut(new PrintWriter(new FileWriter(arg), true));
                } catch (java.io.IOException e) {
                    helper.error("err.error.writing.file", arg, e);
                    return true;
                }
                return super.process(options, option, arg);
            }
        },

        new XOption(XPRINT,                                     "opt.print"),

        new XOption(XPRINTROUNDS,                               "opt.printRounds"),

        new XOption(XPRINTPROCESSORINFO,                        "opt.printProcessorInfo"),

        new XOption(XPREFER,                                     "opt.prefer") {
            public boolean matches(String s) {
                return s.equals("-Xprefer:source") || s.equals("-Xprefer:newer");
            }
            public boolean process(Options options, String option, String operand) {
                int sep = option.indexOf(":");
                options.put(option.substring(0, sep), option.substring(sep+1));
                options.put(option,option);
                return false;
            }
        },

        /* -O is a no-op, accepted for backward compatibility. */
        new HiddenOption(O),

        /* -Xjcov produces tables to support the code coverage tool jcov. */
        new HiddenOption(XJCOV),

        /* This is a back door to the compiler's option table.
         * -XDx=y sets the option x to the value y.
         * -XDx sets the option x to the value x.
         */
        new HiddenOption(XD) {
            String s;
            public boolean matches(String s) {
                this.s = s;
                return s.startsWith(name.optionName);
            }
            public boolean process(Options options, String option) {
                s = s.substring(name.optionName.length());
                int eq = s.indexOf('=');
                String key = (eq < 0) ? s : s.substring(0, eq);
                String value = (eq < 0) ? s : s.substring(eq+1);
                options.put(key, value);
                return false;
            }
        },

        /*
         * TODO: With apt, the matches method accepts anything if
         * -XclassAsDecls is used; code elsewhere does the lookup to
         * see if the class name is both legal and found.
         *
         * In apt, the process method adds the candiate class file
         * name to a separate list.
         */
        new HiddenOption(SOURCEFILE) {
            String s;
            public boolean matches(String s) {
                this.s = s;
                return s.endsWith(".java")  // Java source file
                    || SourceVersion.isName(s);   // Legal type name
            }
            public boolean process(Options options, String option) {
                if (s.endsWith(".java") ) {
                    File f = new File(s);
                    if (!f.exists()) {
                        helper.error("err.file.not.found", f);
                        return true;
                    }
                    if (!f.isFile()) {
                        helper.error("err.file.not.file", f);
                        return true;
                    }
                    helper.addFile(f);
                }
                else
                    helper.addClassName(s);
                return false;
            }
        },
    };
    }

}
