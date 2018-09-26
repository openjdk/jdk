/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.javac.main.Option.InvalidValueException;
import com.sun.tools.javac.main.Option.OptionKind;
import com.sun.tools.javac.main.OptionHelper;
import com.sun.tools.javac.util.Options;

import static com.sun.tools.javac.main.Option.OptionKind.*;
import static jdk.javadoc.internal.tool.Main.Result.*;

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

    BOOTCLASSPATH("-bootclasspath", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.BOOT_CLASS_PATH.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    CLASS_PATH("--class-path -classpath -cp", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.CLASS_PATH.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    EXTDIRS("-extdirs", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.EXTDIRS.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    SOURCE_PATH("--source-path -sourcepath", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.SOURCE_PATH.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    MODULE_SOURCE_PATH("--module-source-path", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.MODULE_SOURCE_PATH.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    UPGRADE_MODULE_PATH("--upgrade-module-path", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.UPGRADE_MODULE_PATH.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    SYSTEM("--system", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.SYSTEM.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    MODULE_PATH("--module-path -p", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.MODULE_PATH.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    ADD_MODULES("--add-modules", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.ADD_MODULES.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    LIMIT_MODULES("--limit-modules", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.LIMIT_MODULES.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    MODULE("--module", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.addToList(this, ",", arg);
        }
    },

    ENCODING("-encoding", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.ENCODING.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    RELEASE("--release", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.RELEASE.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    SOURCE("--source -source", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.SOURCE.process(helper.getOptionHelper(), primaryName, arg);
            Option.TARGET.process(helper.getOptionHelper(), Option.TARGET.primaryName, arg);
        }
    },

    XMAXERRS("-Xmaxerrs", EXTENDED, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.XMAXERRS.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    XMAXWARNS("-Xmaxwarns", EXTENDED, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.XMAXWARNS.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    ADD_READS("--add-reads", EXTENDED, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.ADD_READS.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    ADD_EXPORTS("--add-exports", EXTENDED, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.ADD_EXPORTS.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    PATCH_MODULE("--patch-module", EXTENDED, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.PATCH_MODULE.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    ADD_OPENS("--add-opens", HIDDEN, true) {
        @Override
        public void process(Helper helper, String arg) throws InvalidValueException {
            Option.ADD_OPENS.process(helper.getOptionHelper(), primaryName, arg);
        }
    },

    ENABLE_PREVIEW("--enable-preview", STANDARD) {
        @Override
        public void process(Helper helper) throws InvalidValueException {
            Option.PREVIEW.process(helper.getOptionHelper(), primaryName);
        }
    },

    // ----- doclet options -----

    DOCLET("-doclet", STANDARD, true), // handled in setDocletInvoker

    DOCLETPATH("-docletpath", STANDARD, true), // handled in setDocletInvoker

    // ----- selection options -----

    SUBPACKAGES("-subpackages", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.addToList(this, ":", arg);
        }
    },

    EXCLUDE("-exclude", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.addToList(this, ":", arg);
        }
    },

    // ----- filtering options -----

    PACKAGE("-package", STANDARD) {
        @Override
        public void process(Helper helper) throws OptionException {
            helper.setSimpleFilter("package");
        }
    },

    PRIVATE("-private", STANDARD) {
        @Override
        public void process(Helper helper) throws OptionException {
            helper.setSimpleFilter("private");
        }
    },

    PROTECTED("-protected", STANDARD) {
        @Override
        public void process(Helper helper) throws OptionException {
            helper.setSimpleFilter("protected");
        }
    },

    PUBLIC("-public", STANDARD) {
        @Override
        public void process(Helper helper) throws OptionException {
            helper.setSimpleFilter("public");
        }
    },

    SHOW_MEMBERS("--show-members", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws OptionException {
            helper.setFilter(this, arg);
        }
    },

    SHOW_TYPES("--show-types", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws OptionException {
            helper.setFilter(this, arg);
        }
    },

    SHOW_PACKAGES("--show-packages", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws OptionException {
            helper.setShowPackageAccess(SHOW_PACKAGES, arg);
        }
    },

    SHOW_MODULE_CONTENTS("--show-module-contents", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws OptionException {
            helper.setShowModuleContents(SHOW_MODULE_CONTENTS, arg);
        }
    },

    EXPAND_REQUIRES("--expand-requires", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) throws OptionException {
            helper.setExpandRequires(EXPAND_REQUIRES, arg);
        }
    },

    // ----- output control options -----

    QUIET("-quiet", STANDARD) {
        @Override
        public void process(Helper helper) {
            helper.jdtoolOpts.put(QUIET, true);
        }
    },

    VERBOSE("-verbose", STANDARD) {
        @Override
        public void process(Helper helper) {
            helper.compOpts.put("-verbose", "");
        }
    },

    XWERROR("-Xwerror", HIDDEN) {
        @Override
        public void process(Helper helper) {
            helper.rejectWarnings = true;

        }
    },

    // ----- other options -----

    BREAKITERATOR("-breakiterator", STANDARD) {
        @Override
        public void process(Helper helper) {
            helper.breakiterator = true;
        }
    },

    LOCALE("-locale", STANDARD, true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.docLocale = arg;
        }
    },

    XCLASSES("-Xclasses", HIDDEN) {
        @Override
        public void process(Helper helper) {
            helper.jdtoolOpts.put(XCLASSES, true);
        }
    },

    DUMPONERROR("--dump-on-error", HIDDEN) {
        @Override
        public void process(Helper helper) {
            helper.dumpOnError = true;
        }
    },

    IGNORE_SOURCE_ERRORS("--ignore-source-errors", HIDDEN) {
        @Override
        public void process(Helper helper) {
            helper.jdtoolOpts.put(IGNORE_SOURCE_ERRORS, true);
        }
    },

    // ----- help options -----

    HELP("--help -help -? -h", STANDARD) {
        @Override
        public void process(Helper helper) throws OptionException {
            throw new OptionException(OK, helper::usage);
        }
    },

    HELP_EXTRA("--help-extra -X", STANDARD) {
        @Override
        public void process(Helper helper) throws OptionException {
           throw new OptionException(OK, helper::Xusage);
        }
    },

    // This option exists only for the purpose of documenting itself.
    // It's actually implemented by the launcher.
    J("-J", STANDARD, true) {
        @Override
        public void process(Helper helper) {
            throw new AssertionError("the -J flag should be caught by the launcher.");
        }
    },

    VERSION("--version", STANDARD) {
        @Override
        public void process(Helper helper) throws OptionException {
            throw new OptionException(OK, helper::version);
        }
    },

    FULLVERSION("--full-version", HIDDEN) {
        @Override
        public void process(Helper helper) throws OptionException {
            throw new OptionException(OK, helper::fullVersion);
        }
    };

    public final String primaryName;
    public final List<String> names;
    public final OptionKind kind;
    public final boolean hasArg;
    public final boolean hasSuffix; // ex: foo:bar or -foo=bar

    ToolOption(String opt, OptionKind kind) {
        this(opt, kind, false);
    }

    ToolOption(String names, OptionKind kind, boolean hasArg) {
        this.names = Arrays.asList(names.split("\\s+"));
        this.primaryName = this.names.get(0);
        this.kind = kind;
        this.hasArg = hasArg;
        char lastChar = names.charAt(names.length() - 1);
        this.hasSuffix = lastChar == ':' || lastChar == '=';
    }

    void process(Helper helper, String arg) throws OptionException, Option.InvalidValueException { }

    void process(Helper helper) throws OptionException, Option.InvalidValueException { }

    List<String> getNames() {
        return names;
    }

    String getParameters(Messager messager) {
        return (hasArg || primaryName.endsWith(":"))
                ? messager.getText(getKey(primaryName, ".arg"))
                : null;
    }

    String getDescription(Messager messager) {
        return messager.getText(getKey(primaryName, ".desc"));
    }

    private String getKey(String optionName, String suffix) {
        return "main.opt."
                + optionName
                .replaceAll("^-*", "")              // remove leading '-'
                .replaceAll("[^A-Za-z0-9]+$", "")   // remove trailing non-alphanumeric
                .replaceAll("[^A-Za-z0-9]", ".")    // replace internal non-alphanumeric
                + suffix;
    }


    static ToolOption get(String name) {
        String oname = name;
        if (name.startsWith("--") && name.contains("=")) {
            oname = name.substring(0, name.indexOf('='));
        }
        for (ToolOption o : values()) {
            for (String n : o.names) {
                if (oname.equals(n)) {
                    return o;
                }
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

        /** dump stack traces for debugging etc.*/
        boolean dumpOnError = false;

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

        abstract void version();
        abstract void fullVersion();

        abstract String getLocalizedMessage(String msg, Object... args);

        abstract OptionHelper getOptionHelper();

        @SuppressWarnings("unchecked")
        void addToList(ToolOption opt, String delimiter, String str) {
            List<String> list = (List<String>) jdtoolOpts.computeIfAbsent(opt, v -> new ArrayList<>());
            list.addAll(Arrays.asList(str.split(delimiter)));
            jdtoolOpts.put(opt, list);
        }

        void setExpandRequires(ToolOption opt, String arg) throws OptionException {
            switch (arg) {
                case "transitive":
                    jdtoolOpts.put(opt, AccessKind.PUBLIC);
                    break;
                case "all":
                    jdtoolOpts.put(opt, AccessKind.PRIVATE);
                    break;
                default:
                    String text = getLocalizedMessage("main.illegal_option_value", arg);
                    throw new IllegalOptionValue(this::usage, text);
            }
        }

        void setShowModuleContents(ToolOption opt, String arg) throws OptionException {
            switch (arg) {
                case "api":
                    jdtoolOpts.put(opt, AccessKind.PUBLIC);
                    break;
                case "all":
                    jdtoolOpts.put(opt, AccessKind.PRIVATE);
                    break;
                default:
                    String text = getLocalizedMessage("main.illegal_option_value", arg);
                    throw new IllegalOptionValue(this::usage, text);
            }
        }

        void setShowPackageAccess(ToolOption opt, String arg) throws OptionException {
            switch (arg) {
                case "exported":
                    jdtoolOpts.put(opt, AccessKind.PUBLIC);
                    break;
                case "all":
                    jdtoolOpts.put(opt, AccessKind.PRIVATE);
                    break;
                default:
                    String text = getLocalizedMessage("main.illegal_option_value", arg);
                    throw new IllegalOptionValue(this::usage, text);
            }
        }


        void setFilter(ToolOption opt, String arg) throws OptionException {
            jdtoolOpts.put(opt, getAccessValue(arg));
        }

        void setSimpleFilter(String arg) throws OptionException {
            handleSimpleOption(arg);
        }

        void setFileManagerOpt(Option opt, String arg) {
            fileManagerOpts.put(opt, arg);
        }

        void handleSimpleOption(String arg) throws OptionException {
            populateSimpleAccessMap(getAccessValue(arg));
        }

        /*
         * This method handles both the simple options -package,
         * -private, so on, in addition to the new ones such as
         * --show-types:public and so on.
         */
        private AccessKind getAccessValue(String arg) throws OptionException {
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
                    String text = getLocalizedMessage("main.illegal_option_value", value);
                    throw new IllegalOptionValue(this::usage, text);
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
