/*
 * Copyright 2006-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;

/**
 * TODO: describe com.sun.tools.javac.main.JavacOption
 *
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
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

        /** The choices for this option, if any.
         */
        Collection<String> choices;

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
            this(name, descrKey, choiceKind, Arrays.asList(choices));
        }

        Option(OptionName name, String descrKey, ChoiceKind choiceKind, Collection<String> choices) {
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
                    return choices.contains(arg);
                else {
                    for (String a: arg.split(",+")) {
                        if (!choices.contains(a))
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
                    for (String c: choices) {
                        sb.append(sep);
                        sb.append(c);
                        sep = ",";
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
                        for (String c: choices)
                            options.remove(option + c);
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
        XOption(OptionName name, String descrKey, ChoiceKind kind, Collection<String> choices) {
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
