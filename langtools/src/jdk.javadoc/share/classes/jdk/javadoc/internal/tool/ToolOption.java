/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.ElementKind;

import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.main.OptionHelper;
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
            helper.setFileManagerOpt(Option.BOOT_CLASS_PATH, arg);
        }
    },

    CLASSPATH("-classpath", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.CLASS_PATH, arg);
        }
    },

    CP("-cp", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.CLASS_PATH, arg);
        }
    },

    CLASS_PATH("--class-path", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.CLASS_PATH, arg);
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
            helper.setFileManagerOpt(Option.SOURCE_PATH, arg);
        }
    },

    SOURCE_PATH("--source-path", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.SOURCE_PATH, arg);
        }
    },

    SYSCLASSPATH("-sysclasspath", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.BOOT_CLASS_PATH, arg);
        }
    },

    MODULE_SOURCE_PATH("--module-source-path", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.MODULE_SOURCE_PATH, arg);
        }
    },

    UPGRADE_MODULE_PATH("--upgrade-module-path", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.UPGRADE_MODULE_PATH, arg);
        }
    },

    SYSTEM("--system", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.SYSTEM, arg);
        }
    },

    MODULE_PATH("--module-path", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.MODULE_PATH, arg);
        }
    },

    P("-p", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.MODULE_PATH, arg);
        }
    },

    ADD_MODULES("--add-modules", true) {
        @Override
        public void process(Helper helper, String arg) {
            Option.ADD_MODULES.process(helper.getOptionHelper(), opt, arg);
        }
    },

    LIMIT_MODULES("--limit-modules", true) {
        @Override
        public void process(Helper helper, String arg) {
            Option.LIMIT_MODULES.process(helper.getOptionHelper(), opt, arg);
        }
    },

    MODULE("--module", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.addToList(this, ",", arg);
        }
    },

    ENCODING("-encoding", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFileManagerOpt(Option.ENCODING, arg);
        }
    },

    RELEASE("--release", true) {
        @Override
        public void process(Helper helper, String arg) {
            Option.RELEASE.process(helper.getOptionHelper(), opt, arg);
        }
    },

    SOURCE("-source", true) {
        @Override
        public void process(Helper helper, String arg) {
            Option.SOURCE.process(helper.getOptionHelper(), opt, arg);
        }
    },

    XMAXERRS("-Xmaxerrs", true) {
        @Override
        public void process(Helper helper, String arg) {
            Option.XMAXERRS.process(helper.getOptionHelper(), opt, arg);
        }
    },

    XMAXWARNS("-Xmaxwarns", true) {
        @Override
        public void process(Helper helper, String arg) {
            Option.XMAXWARNS.process(helper.getOptionHelper(), opt, arg);
        }
    },

    ADD_READS("--add-reads", true) {
        @Override
        public void process(Helper helper, String arg) {
            Option.ADD_READS.process(helper.getOptionHelper(), opt, arg);
        }
    },

    ADD_EXPORTS("--add-exports", true) {
        @Override
        public void process(Helper helper, String arg) {
            Option.ADD_EXPORTS.process(helper.getOptionHelper(), opt, arg);
        }
    },

    XMODULE("-Xmodule:", false) {
        @Override
        public void process(Helper helper, String arg) {
            Option.XMODULE.process(helper.getOptionHelper(), arg);
        }
    },

    PATCH_MODULE("--patch-module", true) {
        @Override
        public void process(Helper helper, String arg) {
            Option.PATCH_MODULE.process(helper.getOptionHelper(), opt, arg);
        }
    },

    // ----- doclet options -----

    DOCLET("-doclet", true), // handled in setDocletInvoker

    DOCLETPATH("-docletpath", true), // handled in setDocletInvoker

    // ----- selection options -----

    SUBPACKAGES("-subpackages", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.addToList(this, ":", arg);
        }
    },

    EXCLUDE("-exclude", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.addToList(this, ":", arg);
        }
    },

    // ----- filtering options -----

    PACKAGE("-package") {
        @Override
        public void process(Helper helper) {
            helper.setSimpleFilter("package");
        }
    },

    PRIVATE("-private") {
        @Override
        public void process(Helper helper) {
            helper.setSimpleFilter("private");
        }
    },

    PROTECTED("-protected") {
        @Override
        public void process(Helper helper) {
            helper.setSimpleFilter("protected");
        }
    },

    PUBLIC("-public") {
        @Override
        public void process(Helper helper) {
            helper.setSimpleFilter("public");
        }
    },

    SHOW_MEMBERS("--show-members:") {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFilter(this, arg);
        }
    },

    SHOW_TYPES("--show-types:") {
        @Override
        public void process(Helper helper, String arg) {
            helper.setFilter(this, arg);
        }
    },

    SHOW_PACKAGES("--show-packages:") {
        @Override
        public void process(Helper helper, String arg) {
            helper.setShowPackageAccess(SHOW_PACKAGES, helper.getOptionArgumentValue(arg));
        }
    },

    SHOW_MODULE_CONTENTS("--show-module-contents:") {
        @Override
        public void process(Helper helper, String arg) {
            helper.setShowModuleContents(SHOW_MODULE_CONTENTS, helper.getOptionArgumentValue(arg));
        }
    },

    EXPAND_REQUIRES("--expand-requires:") {
        @Override
        public void process(Helper helper, String arg) {
            helper.setExpandRequires(EXPAND_REQUIRES, helper.getOptionArgumentValue(arg));
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
            helper.jdtoolOpts.put(QUIET, true);
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

    XCLASSES("-Xclasses") {
        @Override
        public void process(Helper helper) {
            helper.jdtoolOpts.put(XCLASSES, true);
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
    public final boolean hasSuffix; // ex: foo:bar or -foo=bar

    ToolOption(String opt) {
        this(opt, false);
    }

    ToolOption(String opt, boolean hasArg) {
        this.opt = opt;
        this.hasArg = hasArg;
        char lastChar = opt.charAt(opt.length() - 1);
        this.hasSuffix = lastChar == ':' || lastChar == '=';
    }

    void process(Helper helper, String arg) { }

    void process(Helper helper) { }

    static ToolOption get(String name) {
        String oname = name;
        if (name.contains(":")) {
            oname = name.substring(0, name.indexOf(':') + 1);
        } else if (name.contains("=")) {
            oname = name.substring(0, name.indexOf('=') + 1);
        }
        for (ToolOption o : values()) {
            if (oname.equals(o.opt)) {
                return o;
            }
        }
        return null;
    }

    static abstract class Helper {

        // File manager options
        final Map<Option, String> fileManagerOpts = new LinkedHashMap<>();

        /** javac options, set by various options. */
        Options compOpts; // = Options.instance(context)

        /** Javadoc tool options */
        final Map<ToolOption, Object> jdtoolOpts = new EnumMap<>(ToolOption.class);

        /** Set by -breakiterator. */
        boolean breakiterator = false;

        /** Set by -Xwerror. */
        boolean rejectWarnings = false;

        /** Set by -prompt. */
        boolean promptOnError;

        /** Set by -locale. */
        String docLocale = "";

        Helper() {
            populateDefaultAccessMap();
        }

        abstract void usage();
        abstract void Xusage();

        abstract void usageError(String msg, Object... args);
        abstract OptionHelper getOptionHelper();

        @SuppressWarnings("unchecked")
        void addToList(ToolOption opt, String delimiter, String str) {
            List<String> list = (List<String>) jdtoolOpts.computeIfAbsent(opt, v -> new ArrayList<>());
            list.addAll(Arrays.asList(str.split(delimiter)));
            jdtoolOpts.put(opt, list);
        }

        String getOptionArgumentValue(String in) {
            String[] values = in.trim().split(":");
            return values[1];
        }

        void setExpandRequires(ToolOption opt, String arg) {
            switch (arg) {
                case "public":
                    jdtoolOpts.put(opt, AccessKind.PUBLIC);
                    break;
                case "all":
                    jdtoolOpts.put(opt, AccessKind.PRIVATE);
                    break;
                default:
                    usageError("main.illegal_option_value", arg);
            }
        }

        void setShowModuleContents(ToolOption opt, String arg) {
            switch (arg) {
                case "api":
                    jdtoolOpts.put(opt, AccessKind.PUBLIC);
                    break;
                case "all":
                    jdtoolOpts.put(opt, AccessKind.PRIVATE);
                    break;
                default:
                    usageError("main.illegal_option_value", arg);
            }
        }

        void setShowPackageAccess(ToolOption opt, String arg) {
            switch (arg) {
                case "exported":
                    jdtoolOpts.put(opt, AccessKind.PUBLIC);
                    break;
                case "all":
                    jdtoolOpts.put(opt, AccessKind.PRIVATE);
                    break;
                default:
                    usageError("main.illegal_option_value", arg);
            }
        }


        void setFilter(ToolOption opt, String arg) {
            jdtoolOpts.put(opt, getAccessValue(arg));
        }

        void setSimpleFilter(String arg) {
            handleSimpleOption(arg);
        }

        void setFileManagerOpt(Option opt, String arg) {
            fileManagerOpts.put(opt, arg);
        }

        void handleSimpleOption(String arg) {
            populateSimpleAccessMap(getAccessValue(arg));
        }

        /*
         * This method handles both the simple options -package,
         * -private, so on, in addition to the new ones such as
         * --show-types:public and so on.
         */
        private AccessKind getAccessValue(String arg) {
            int colon = arg.indexOf(':');
            String value = (colon > 0)
                    ? arg.substring(colon + 1)
                    : arg;
            switch (value) {
                case "public":
                    return AccessKind.PUBLIC;
                case "protected":
                    return AccessKind.PROTECTED;
                case "package":
                    return AccessKind.PACKAGE;
                case "private":
                    return AccessKind.PRIVATE;
                default:
                    usageError("main.illegal_option_value", value);
                    return null;
            }
        }

        /*
         * Sets the entire kind map to PROTECTED this is the default.
         */
        private void populateDefaultAccessMap() {
            populateSimpleAccessMap(AccessKind.PROTECTED);
        }

        /*
         * This sets access to all the allowed kinds in the
         * access map.
         */
        void populateSimpleAccessMap(AccessKind accessValue) {
            for (ElementKind kind : ElementsTable.ModifierFilter.ALLOWED_KINDS) {
                switch (kind) {
                    case METHOD:
                        jdtoolOpts.put(SHOW_MEMBERS, accessValue);
                        break;
                    case CLASS:
                        jdtoolOpts.put(SHOW_TYPES, accessValue);
                        break;
                    case PACKAGE:
                        jdtoolOpts.put(SHOW_PACKAGES, accessValue);
                        break;
                    case MODULE:
                        jdtoolOpts.put(SHOW_MODULE_CONTENTS, accessValue);
                        break;
                    default:
                        throw new AssertionError("unknown element kind:" + kind);
                }
            }
        }
    }
}
