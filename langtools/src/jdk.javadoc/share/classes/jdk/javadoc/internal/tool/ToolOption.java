/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.util.Options;

/**
 * javadoc tool options.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public enum ToolOption {
    // ----- options for underlying compiler -----

    BOOTCLASSPATH("-bootclasspath", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.BOOTCLASSPATH, arg);
        }
    },

    CLASSPATH("-classpath", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.CLASSPATH, arg);
        }
    },

    CP("-cp", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.CP, arg);
        }
    },

    EXTDIRS("-extdirs", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.EXTDIRS, arg);
        }
    },

    SOURCEPATH("-sourcepath", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.SOURCEPATH, arg);
        }
    },

    SYSCLASSPATH("-sysclasspath", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.BOOTCLASSPATH, arg);
        }
    },

    ENCODING("-encoding", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.encoding = arg;
            helper.setCompilerOpt(opt, arg);
        }
    },

    RELEASE("-release", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setCompilerOpt(opt, arg);
        }
    },

    SOURCE("-source", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setCompilerOpt(opt, arg);
        }
    },

    XMAXERRS("-Xmaxerrs", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setCompilerOpt(opt, arg);
        }
    },

    XMAXWARNS("-Xmaxwarns", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setCompilerOpt(opt, arg);
        }
    },

    // ----- doclet options -----

    DOCLET("-doclet", true), // handled in setDocletInvoker

    DOCLETPATH("-docletpath", true), // handled in setDocletInvoker

    // ----- selection options -----

    SUBPACKAGES("-subpackages", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.addToList(helper.subPackages, arg);
        }
    },

    EXCLUDE("-exclude", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.addToList(helper.excludedPackages, arg);
        }
    },

    // ----- filtering options -----

    PACKAGE("-package") {
        @Override
        public void process(Helper helper) {
            helper.setFilter("package");
        }
    },

    PRIVATE("-private") {
        @Override
        public void process(Helper helper) {
            helper.setFilter("private");
        }
    },

    PROTECTED("-protected") {
        @Override
        public void process(Helper helper) {
            helper.setFilter("protected");
        }
    },

    PUBLIC("-public") {
        @Override
        public void process(Helper helper) {
            helper.setFilter("public");
        }
    },

    // ----- output control options -----

    PROMPT("-prompt") {
        @Override
        public void process(Helper helper) {
            helper.compOpts.put("-prompt", "-prompt");
            helper.promptOnError = true;
        }
    },

    QUIET("-quiet") {
        @Override
        public void process(Helper helper) {
            helper.quiet = true;
        }
    },

    VERBOSE("-verbose") {
        @Override
        public void process(Helper helper) {
            helper.compOpts.put("-verbose", "");
        }
    },

    XWERROR("-Xwerror") {
        @Override
        public void process(Helper helper) {
            helper.rejectWarnings = true;

        }
    },

    // ----- other options -----

    BREAKITERATOR("-breakiterator") {
        @Override
        public void process(Helper helper) {
            helper.breakiterator = true;
        }
    },

    LOCALE("-locale", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.docLocale = arg;
        }
    },

    // the doclet consumes this
    OVERVIEW("-overview", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setOverviewpath(arg);
        }
    },

    XCLASSES("-Xclasses") {
        @Override
        public void process(Helper helper) {
            helper.docClasses = true;

        }
    },

    // ----- help options -----

    HELP("-help") {
        @Override
        public void process(Helper helper) {
            helper.usage();
        }
    },

    X("-X") {
        @Override
        public void process(Helper helper) {
            helper.Xusage();
        }
    };

    public final String opt;
    public final boolean hasArg;

    ToolOption(String opt) {
        this(opt, false);
    }

    ToolOption(String opt, boolean hasArg) {
        this.opt = opt;
        this.hasArg = hasArg;
    }

    void process(Helper helper, String arg) { }

    void process(Helper helper) { }

    static ToolOption get(String name) {
        for (ToolOption o: values()) {
            if (name.equals(o.opt))
                return o;
        }
        return null;
    }

    static abstract class Helper {
        /** List of decoded options. */
        final List<List<String>> options = new ArrayList<>();

        /** Selected packages, from -subpackages. */
        final List<String> subPackages = new ArrayList<>();

        /** Excluded packages, from -exclude. */
        final List<String> excludedPackages = new ArrayList<>();

        // File manager options
        final Map<Option, String> fileManagerOpts = new LinkedHashMap<>();

        /** javac options, set by various options. */
        Options compOpts; // = Options.instance(context)

        /* Encoding for javac, and files written? set by -encoding. */
        String encoding = null;

        /** Set by -breakiterator. */
        boolean breakiterator = false;

        /** Set by -quiet. */
        boolean quiet = false;

        /** Set by -Xclasses. */
        boolean docClasses = false;

        /** Set by -Xwerror. */
        boolean rejectWarnings = false;

        /** Set by -prompt. */
        boolean promptOnError;

        /** Set by -locale. */
        String docLocale = "";

        /** Set by -public, private, -protected, -package. */
        String showAccess = null;
        String overviewpath;

        abstract void usage();
        abstract void Xusage();

        abstract void usageError(String msg, Object... args);

        void addToList(List<String> list, String str){
            StringTokenizer st = new StringTokenizer(str, ":");
            String current;
            while(st.hasMoreTokens()){
                current = st.nextToken();
                list.add(current);
            }
        }

        void setFilter(String showAccess) {
            if (showAccess != null) {
                if (!"public".equals(showAccess)
                        && !"protected".equals(showAccess)
                        && !"private".equals(showAccess)
                        && !"package".equals(showAccess)) {
                    usageError("main.incompatible.access.flags");
                }
                this.showAccess = showAccess;
            }
        }

        void setCompilerOpt(String opt, String arg) {
            if (compOpts.get(opt) != null) {
                usageError("main.option.already.seen", opt);
            }
            compOpts.put(opt, arg);
        }

        void setFileManagerOpt(Option opt, String arg) {
            fileManagerOpts.put(opt, arg);
        }

        private void setOverviewpath(String arg) {
            this.overviewpath = arg;
        }
    }
}
