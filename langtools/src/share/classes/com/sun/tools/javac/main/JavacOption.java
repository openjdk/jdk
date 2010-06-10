/*
 * Copyright (c) 2006, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.main;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

/**
 * TODO: describe com.sun.tools.javac.main.JavacOption
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public interface JavacOption {

    OptionKind getKind();

    /** Does this option take a (separate) operand?
     *  @return true if this option takes a separate operand
     */
    boolean hasArg();

    /** Does argument string match option pattern?
     *  @param arg   the command line argument string
     *  @return true if {@code arg} matches this option
     */
    boolean matches(String arg);

    /** Process an option with an argument.
     *  @param options the accumulated set of analyzed options
     *  @param option  the option to be processed
     *  @param arg     the arg for the option to be processed
     *  @return true if an error was detected
     */
    boolean process(Options options, String option, String arg);

    /** Process the option with no argument.
     *  @param options the accumulated set of analyzed options
     *  @param option  the option to be processed
     *  @return true if an error was detected
     */
    boolean process(Options options, String option);

    OptionName getName();

    enum OptionKind {
        NORMAL,
        EXTENDED,
        HIDDEN,
    }

    enum ChoiceKind {
        ONEOF,
        ANYOF
    }

    /** This class represents an option recognized by the main program
     */
    static class Option implements JavacOption {

        /** Option string.
         */
        OptionName name;

        /** Documentation key for arguments.
         */
        String argsNameKey;

        /** Documentation key for description.
         */
        String descrKey;

        /** Suffix option (-foo=bar or -foo:bar)
         */
        boolean hasSuffix;

        /** The kind of choices for this option, if any.
         */
        ChoiceKind choiceKind;

        /** The choices for this option, if any, and whether or not the choices
         *  are hidden
         */
        Map<String,Boolean> choices;

        Option(OptionName name, String argsNameKey, String descrKey) {
            this.name = name;
            this.argsNameKey = argsNameKey;
            this.descrKey = descrKey;
            char lastChar = name.optionName.charAt(name.optionName.length()-1);
            hasSuffix = lastChar == ':' || lastChar == '=';
        }

        Option(OptionName name, String descrKey) {
            this(name, null, descrKey);
        }

        Option(OptionName name, String descrKey, ChoiceKind choiceKind, String... choices) {
            this(name, descrKey, choiceKind, createChoices(choices));
        }

        private static Map<String,Boolean> createChoices(String... choices) {
            Map<String,Boolean> map = new LinkedHashMap<String,Boolean>();
            for (String c: choices)
                map.put(c, false);
            return map;
        }

        Option(OptionName name, String descrKey, ChoiceKind choiceKind,
                Map<String,Boolean> choices) {
            this(name, null, descrKey);
            if (choiceKind == null || choices == null)
                throw new NullPointerException();
            this.choiceKind = choiceKind;
            this.choices = choices;
        }

        @Override
        public String toString() {
            return name.optionName;
        }

        public boolean hasArg() {
            return argsNameKey != null && !hasSuffix;
        }

        public boolean matches(String option) {
            if (!hasSuffix)
                return option.equals(name.optionName);

            if (!option.startsWith(name.optionName))
                return false;

            if (choices != null) {
                String arg = option.substring(name.optionName.length());
                if (choiceKind == ChoiceKind.ONEOF)
                    return choices.keySet().contains(arg);
                else {
                    for (String a: arg.split(",+")) {
                        if (!choices.keySet().contains(a))
                            return false;
                    }
                }
            }

            return true;
        }

        /** Print a line of documentation describing this option, if standard.
         * @param out the stream to which to write the documentation
         */
        void help(PrintWriter out) {
            String s = "  " + helpSynopsis();
            out.print(s);
            for (int j = Math.min(s.length(), 28); j < 29; j++) out.print(" ");
            Log.printLines(out, Main.getLocalizedString(descrKey));
        }

        String helpSynopsis() {
            StringBuilder sb = new StringBuilder();
            sb.append(name);
            if (argsNameKey == null) {
                if (choices != null) {
                    String sep = "{";
                    for (Map.Entry<String,Boolean> e: choices.entrySet()) {
                        if (!e.getValue()) {
                            sb.append(sep);
                            sb.append(e.getKey());
                            sep = ",";
                        }
                    }
                    sb.append("}");
                }
            } else {
                if (!hasSuffix)
                    sb.append(" ");
                sb.append(Main.getLocalizedString(argsNameKey));
            }

            return sb.toString();
        }

        /** Print a line of documentation describing this option, if non-standard.
         *  @param out the stream to which to write the documentation
         */
        void xhelp(PrintWriter out) {}

        /** Process the option (with arg). Return true if error detected.
         */
        public boolean process(Options options, String option, String arg) {
            if (options != null) {
                if (choices != null) {
                    if (choiceKind == ChoiceKind.ONEOF) {
                        // some clients like to see just one of option+choice set
                        for (String s: choices.keySet())
                            options.remove(option + s);
                        String opt = option + arg;
                        options.put(opt, opt);
                        // some clients like to see option (without trailing ":")
                        // set to arg
                        String nm = option.substring(0, option.length() - 1);
                        options.put(nm, arg);
                    } else {
                        // set option+word for each word in arg
                        for (String a: arg.split(",+")) {
                            String opt = option + a;
                            options.put(opt, opt);
                        }
                    }
                }
                options.put(option, arg);
            }
            return false;
        }

        /** Process the option (without arg). Return true if error detected.
         */
        public boolean process(Options options, String option) {
            if (hasSuffix)
                return process(options, name.optionName, option.substring(name.optionName.length()));
            else
                return process(options, option, option);
        }

        public OptionKind getKind() { return OptionKind.NORMAL; }

        public OptionName getName() { return name; }
    };

    /** A nonstandard or extended (-X) option
     */
    static class XOption extends Option {
        XOption(OptionName name, String argsNameKey, String descrKey) {
            super(name, argsNameKey, descrKey);
        }
        XOption(OptionName name, String descrKey) {
            this(name, null, descrKey);
        }
        XOption(OptionName name, String descrKey, ChoiceKind kind, String... choices) {
            super(name, descrKey, kind, choices);
        }
        XOption(OptionName name, String descrKey, ChoiceKind kind, Map<String,Boolean> choices) {
            super(name, descrKey, kind, choices);
        }
        @Override
        void help(PrintWriter out) {}
        @Override
        void xhelp(PrintWriter out) { super.help(out); }
        @Override
        public OptionKind getKind() { return OptionKind.EXTENDED; }
    };

    /** A hidden (implementor) option
     */
    static class HiddenOption extends Option {
        HiddenOption(OptionName name) {
            super(name, null, null);
        }
        HiddenOption(OptionName name, String argsNameKey) {
            super(name, argsNameKey, null);
        }
        @Override
        void help(PrintWriter out) {}
        @Override
        void xhelp(PrintWriter out) {}
        @Override
        public OptionKind getKind() { return OptionKind.HIDDEN; }
    };

}
