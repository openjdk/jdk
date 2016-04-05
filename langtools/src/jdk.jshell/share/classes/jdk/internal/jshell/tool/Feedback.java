/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jshell.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import static java.util.stream.Collectors.joining;

/**
 * Feedback customization support
 *
 * @author Robert Field
 */
class Feedback {

    // Patern for substituted fields within a customized format string
    private static final Pattern FIELD_PATTERN = Pattern.compile("\\{(.*?)\\}");

    // Current mode
    private Mode mode = new Mode("", false); // initial value placeholder during start-up

    // Mapping of mode names to mode modes
    private final Map<String, Mode> modeMap = new HashMap<>();

    // Mapping selector enum names to enums
    private final Map<String, Selector<?>> selectorMap = new HashMap<>();

    private static final long ALWAYS = bits(FormatCase.all, FormatAction.all, FormatWhen.all,
            FormatResolve.all, FormatUnresolved.all, FormatErrors.all);
    private static final long ANY = 0L;

    public boolean shouldDisplayCommandFluff() {
        return mode.commandFluff;
    }

    public String getPre() {
        return mode.format("pre", ANY);
    }

    public String getPost() {
        return mode.format("post", ANY);
    }

    public String getErrorPre() {
        return mode.format("errorpre", ANY);
    }

    public String getErrorPost() {
        return mode.format("errorpost", ANY);
    }

    public String format(FormatCase fc, FormatAction fa, FormatWhen fw,
                    FormatResolve fr, FormatUnresolved fu, FormatErrors fe,
                    String name, String type, String value, String unresolved, List<String> errorLines) {
        return mode.format(fc, fa, fw, fr, fu, fe,
                name, type, value, unresolved, errorLines);
    }

    public String getPrompt(String nextId) {
        return mode.getPrompt(nextId);
    }

    public String getContinuationPrompt(String nextId) {
        return mode.getContinuationPrompt(nextId);
    }

    public boolean setFeedback(JShellTool tool, ArgTokenizer at) {
        return new Setter(tool, at).setFeedback();
    }

    public boolean setFormat(JShellTool tool, ArgTokenizer at) {
        return new Setter(tool, at).setFormat();
    }

    public boolean setNewMode(JShellTool tool, ArgTokenizer at) {
        return new Setter(tool, at).setNewMode();
    }

    public boolean setPrompt(JShellTool tool, ArgTokenizer at) {
        return new Setter(tool, at).setPrompt();
    }

    public void printFeedbackHelp(JShellTool tool) {
        new Setter(tool, null).printFeedbackHelp();
    }

    public void printFormatHelp(JShellTool tool) {
        new Setter(tool, null).printFormatHelp();
    }

    public void printNewModeHelp(JShellTool tool) {
        new Setter(tool, null).printNewModeHelp();
    }

    public void printPromptHelp(JShellTool tool) {
        new Setter(tool, null).printPromptHelp();
    }

    {
        for (FormatCase e : EnumSet.allOf(FormatCase.class))
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatAction e : EnumSet.allOf(FormatAction.class))
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatResolve e : EnumSet.allOf(FormatResolve.class))
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatUnresolved e : EnumSet.allOf(FormatUnresolved.class))
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatErrors e : EnumSet.allOf(FormatErrors.class))
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatWhen e : EnumSet.allOf(FormatWhen.class))
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
    }

    /**
     * Holds all the context of a mode mode
     */
    private static class Mode {

        // Name of mode
        final String name;

        // Display command verification/information
        final boolean commandFluff;

        // Event cases: class, method, expression, ...
        final Map<String, List<Setting>> cases;

        String prompt = "\n-> ";
        String continuationPrompt = ">> ";

        static class Setting {
            final long enumBits;
            final String format;
            Setting(long enumBits, String format) {
                this.enumBits = enumBits;
                this.format = format;
            }
        }

        /**
         * Set up an empty mode.
         *
         * @param name
         * @param commandFluff True if should display command fluff messages
         */
        Mode(String name, boolean commandFluff) {
            this.name = name;
            this.commandFluff = commandFluff;
            cases = new HashMap<>();
            add("name",       new Setting(ALWAYS, "%1$s"));
            add("type",       new Setting(ALWAYS, "%2$s"));
            add("value",      new Setting(ALWAYS, "%3$s"));
            add("unresolved", new Setting(ALWAYS, "%4$s"));
            add("errors",     new Setting(ALWAYS, "%5$s"));
            add("err",        new Setting(ALWAYS, "%6$s"));

            add("errorline",  new Setting(ALWAYS, "    {err}%n"));

            add("pre",        new Setting(ALWAYS, "|  "));
            add("post",       new Setting(ALWAYS, "%n"));
            add("errorpre",   new Setting(ALWAYS, "|  "));
            add("errorpost",  new Setting(ALWAYS, "%n"));
        }

        /**
         * Set up a copied mode.
         *
         * @param name
         * @param commandFluff True if should display command fluff messages
         * @param m Mode to copy, or null for no fresh
         */
        Mode(String name, boolean commandFluff, Mode m) {
            this.name = name;
            this.commandFluff = commandFluff;
            cases = new HashMap<>();

            m.cases.entrySet().stream()
                    .forEach(fes -> fes.getValue()
                    .forEach(ing -> add(fes.getKey(), ing)));

            this.prompt = m.prompt;
            this.continuationPrompt = m.continuationPrompt;
        }

        private boolean add(String field, Setting ing) {
            List<Setting> settings =  cases.computeIfAbsent(field, k -> new ArrayList<>());
            if (settings == null) {
                return false;
            }
            settings.add(ing);
            return true;
        }

        void set(String field,
                Collection<FormatCase> cc, Collection<FormatAction> ca, Collection<FormatWhen> cw,
                Collection<FormatResolve> cr, Collection<FormatUnresolved> cu, Collection<FormatErrors> ce,
                String format) {
            long bits = bits(cc, ca, cw, cr, cu, ce);
            set(field, bits, format);
        }

        void set(String field, long bits, String format) {
            add(field, new Setting(bits, format));
        }

        /**
         * Lookup format Replace fields with context specific formats.
         *
         * @return format string
         */
        String format(String field, long bits) {
            List<Setting> settings = cases.get(field);
            if (settings == null) {
                return ""; //TODO error?
            }
            String format = null;
            for (int i = settings.size() - 1; i >= 0; --i) {
                Setting ing = settings.get(i);
                long mask = ing.enumBits;
                if ((bits & mask) == bits) {
                    format = ing.format;
                    break;
                }
            }
            if (format == null || format.isEmpty()) {
                return "";
            }
            Matcher m = FIELD_PATTERN.matcher(format);
            StringBuffer sb = new StringBuffer(format.length());
            while (m.find()) {
                String fieldName = m.group(1);
                String sub = format(fieldName, bits);
                m.appendReplacement(sb, Matcher.quoteReplacement(sub));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        String format(FormatCase fc, FormatAction fa, FormatWhen fw,
                    FormatResolve fr, FormatUnresolved fu, FormatErrors fe,
                    String name, String type, String value, String unresolved, List<String> errorLines) {
            long bits = bits(fc, fa, fw, fr, fu, fe);
            String fname = name==null? "" : name;
            String ftype = type==null? "" : type;
            String fvalue = value==null? "" : value;
            String funresolved = unresolved==null? "" : unresolved;
            String errors = errorLines.stream()
                    .map(el -> String.format(
                            format("errorline", bits),
                            fname, ftype, fvalue, funresolved, "*cannot-use-errors-here*", el))
                    .collect(joining());
            return String.format(
                    format("display", bits),
                    fname, ftype, fvalue, funresolved, errors, "*cannot-use-err-here*");
        }

        void setPrompts(String prompt, String continuationPrompt) {
            this.prompt = prompt;
            this.continuationPrompt = continuationPrompt;
        }

        String getPrompt(String nextId) {
            return String.format(prompt, nextId);
        }

        String getContinuationPrompt(String nextId) {
            return String.format(continuationPrompt, nextId);
        }
    }

    // Representation of one instance of all the enum values as bits in a long
    private static long bits(FormatCase fc, FormatAction fa, FormatWhen fw,
            FormatResolve fr, FormatUnresolved fu, FormatErrors fe) {
        long res = 0L;
        res |= 1 << fc.ordinal();
        res <<= FormatAction.count;
        res |= 1 << fa.ordinal();
        res <<= FormatWhen.count;
        res |= 1 << fw.ordinal();
        res <<= FormatResolve.count;
        res |= 1 << fr.ordinal();
        res <<= FormatUnresolved.count;
        res |= 1 << fu.ordinal();
        res <<= FormatErrors.count;
        res |= 1 << fe.ordinal();
        return res;
    }

    // Representation of a space of enum values as or'edbits in a long
    private static long bits(Collection<FormatCase> cc, Collection<FormatAction> ca, Collection<FormatWhen> cw,
                Collection<FormatResolve> cr, Collection<FormatUnresolved> cu, Collection<FormatErrors> ce) {
        long res = 0L;
        for (FormatCase fc : cc)
            res |= 1 << fc.ordinal();
        res <<= FormatAction.count;
        for (FormatAction fa : ca)
            res |= 1 << fa.ordinal();
        res <<= FormatWhen.count;
        for (FormatWhen fw : cw)
            res |= 1 << fw.ordinal();
        res <<= FormatResolve.count;
        for (FormatResolve fr : cr)
            res |= 1 << fr.ordinal();
        res <<= FormatUnresolved.count;
        for (FormatUnresolved fu : cu)
            res |= 1 << fu.ordinal();
        res <<= FormatErrors.count;
        for (FormatErrors fe : ce)
            res |= 1 << fe.ordinal();
        return res;
    }

    interface Selector<E extends Enum<E> & Selector<E>> {
        SelectorCollector<E> collector(Setter.SelectorList sl);
        String doc();
    }

    /**
     * The event cases
     */
    public enum FormatCase implements Selector<FormatCase> {
        IMPORT("import declaration"),
        CLASS("class declaration"),
        INTERFACE("interface declaration"),
        ENUM("enum declaration"),
        ANNOTATION("annotation interface declaration"),
        METHOD("method declaration -- note: {type}==parameter-types"),
        VARDECL("variable declaration without init"),
        VARINIT("variable declaration with init"),
        EXPRESSION("expression -- note: {name}==scratch-variable-name"),
        VARVALUE("variable value expression"),
        ASSIGNMENT("assign variable"),
        STATEMENT("statement");
        String doc;
        static final EnumSet<FormatCase> all = EnumSet.allOf(FormatCase.class);
        static final int count = all.size();

        @Override
        public SelectorCollector<FormatCase> collector(Setter.SelectorList sl) {
            return sl.cases;
        }

        @Override
        public String doc() {
            return doc;
        }

        private FormatCase(String doc) {
            this.doc = doc;
        }
    }

    /**
     * The event actions
     */
    public enum FormatAction implements Selector<FormatAction> {
        ADDED("snippet has been added"),
        MODIFIED("an existing snippet has been modified"),
        REPLACED("an existing snippet has been replaced with a new snippet"),
        OVERWROTE("an existing snippet has been overwritten"),
        DROPPED("snippet has been dropped"),
        USED("snippet was used when it cannot be");
        String doc;
        static final EnumSet<FormatAction> all = EnumSet.allOf(FormatAction.class);
        static final int count = all.size();

        @Override
        public SelectorCollector<FormatAction> collector(Setter.SelectorList sl) {
            return sl.actions;
        }

        @Override
        public String doc() {
            return doc;
        }

        private FormatAction(String doc) {
            this.doc = doc;
        }
    }

    /**
     * When the event occurs: primary or update
     */
    public enum FormatWhen implements Selector<FormatWhen> {
        PRIMARY("the entered snippet"),
        UPDATE("an update to a dependent snippet");
        String doc;
        static final EnumSet<FormatWhen> all = EnumSet.allOf(FormatWhen.class);
        static final int count = all.size();

        @Override
        public SelectorCollector<FormatWhen> collector(Setter.SelectorList sl) {
            return sl.whens;
        }

        @Override
        public String doc() {
            return doc;
        }

        private FormatWhen(String doc) {
            this.doc = doc;
        }
    }

    /**
     * Resolution problems
     */
    public enum FormatResolve implements Selector<FormatResolve> {
        OK("resolved correctly"),
        DEFINED("defined despite recoverably unresolved references"),
        NOTDEFINED("not defined because of recoverably unresolved references");
        String doc;
        static final EnumSet<FormatResolve> all = EnumSet.allOf(FormatResolve.class);
        static final int count = all.size();

        @Override
        public SelectorCollector<FormatResolve> collector(Setter.SelectorList sl) {
            return sl.resolves;
        }

        @Override
        public String doc() {
            return doc;
        }

        private FormatResolve(String doc) {
            this.doc = doc;
        }
    }

    /**
     * Count of unresolved references
     */
    public enum FormatUnresolved implements Selector<FormatUnresolved> {
        UNRESOLVED0("no names are unresolved"),
        UNRESOLVED1("one name is unresolved"),
        UNRESOLVED2("two or more names are unresolved");
        String doc;
        static final EnumSet<FormatUnresolved> all = EnumSet.allOf(FormatUnresolved.class);
        static final int count = all.size();

        @Override
        public SelectorCollector<FormatUnresolved> collector(Setter.SelectorList sl) {
            return sl.unresolvedCounts;
        }

        @Override
        public String doc() {
            return doc;
        }

        private FormatUnresolved(String doc) {
            this.doc = doc;
        }
    }

    /**
     * Count of unresolved references
     */
    public enum FormatErrors implements Selector<FormatErrors> {
        ERROR0("no errors"),
        ERROR1("one error"),
        ERROR2("two or more errors");
        String doc;
        static final EnumSet<FormatErrors> all = EnumSet.allOf(FormatErrors.class);
        static final int count = all.size();

        @Override
        public SelectorCollector<FormatErrors> collector(Setter.SelectorList sl) {
            return sl.errorCounts;
        }

        @Override
        public String doc() {
            return doc;
        }

        private FormatErrors(String doc) {
            this.doc = doc;
        }
    }

    class SelectorCollector<E extends Enum<E> & Selector<E>> {
        final EnumSet<E> all;
        EnumSet<E> set = null;
        SelectorCollector(EnumSet<E> all) {
            this.all = all;
        }
        void add(Object o) {
            @SuppressWarnings("unchecked")
            E e = (E) o;
            if (set == null) {
                set = EnumSet.of(e);
            } else {
                set.add(e);
            }
        }

        boolean isEmpty() {
            return set == null;
        }

        EnumSet<E> getSet() {
            return set == null
                    ? all
                    : set;
        }
    }

    // Class used to set custom eval output formats
    // For both /set format  -- Parse arguments, setting custom format, or printing error
    private class Setter {

        private final ArgTokenizer at;
        private final JShellTool tool;
        boolean valid = true;

        Setter(JShellTool tool, ArgTokenizer at) {
            this.tool = tool;
            this.at = at;
        }

        void hard(String format, Object... args) {
            tool.hard(format, args);
        }

        void hardrb(String key) {
            tool.hardrb(key);
        }

        <E extends Enum<E>> void hardEnums(EnumSet<E> es, Function<E, String> e2s) {
            hardPairs(es.stream(), ev -> ev.name().toLowerCase(Locale.US), e2s);
        }

        <T> void hardPairs(Stream<T> stream, Function<T, String> a, Function<T, String> b) {
            tool.hardPairs(stream, a, b);
        }

        void fluff(String format, Object... args) {
            tool.fluff(format, args);
        }

        void error(String format, Object... args) {
            tool.error(format, args);
        }

        void errorat(String format, Object... args) {
            Object[] a2 = Arrays.copyOf(args, args.length + 1);
            a2[args.length] = at.whole();
            tool.error(format + " -- /set %s", a2);
        }

        void fluffRaw(String format, Object... args) {
            tool.fluffRaw(format, args);
        }

        // For /set prompt <mode> "<prompt>" "<continuation-prompt>"
        boolean setPrompt() {
            Mode m = nextMode();
            String prompt = nextFormat();
            String continuationPrompt = nextFormat();
            if (valid) {
                m.setPrompts(prompt, continuationPrompt);
            } else {
                fluff("See '/help /set prompt' for help");
            }
            return valid;
        }

        // For /set newmode <new-mode> [command|quiet [<old-mode>]]
        boolean setNewMode() {
            String umode = at.next();
            if (umode == null) {
                errorat("Expected new feedback mode");
                valid = false;
            }
            if (modeMap.containsKey(umode)) {
                errorat("Expected a new feedback mode name. %s is a known feedback mode", umode);
                valid = false;
            }
            String[] fluffOpt = at.next("command", "quiet");
            boolean fluff = fluffOpt == null || fluffOpt.length != 1 || "command".equals(fluffOpt[0]);
            if (fluffOpt != null && fluffOpt.length != 1) {
                errorat("Specify either 'command' or 'quiet'");
                valid = false;
            }
            Mode om = null;
            String omode = at.next();
            if (omode != null) {
                om = toMode(omode);
            }
            if (valid) {
                Mode nm = (om != null)
                        ? new Mode(umode, fluff, om)
                        : new Mode(umode, fluff);
                modeMap.put(umode, nm);
                fluff("Created new feedback mode: %s", nm.name);
            } else {
                fluff("See '/help /set newmode' for help");
            }
            return valid;
        }

        // For /set feedback <mode>
        boolean setFeedback() {
            Mode m = nextMode();
            if (valid && m != null) {
                mode = m;
                fluff("Feedback mode: %s", mode.name);
            } else {
                fluff("See '/help /set feedback' for help");
            }
            return valid;
        }

        // For /set format <mode> "<format>" <selector>...
        boolean setFormat() {
            Mode m = nextMode();
            String field = at.next();
            if (field == null || at.isQuoted()) {
                errorat("Expected field name missing");
                valid = false;
            }
            String format = valid? nextFormat() : null;
            String slRaw;
            List<SelectorList> slList = new ArrayList<>();
            while (valid && (slRaw = at.next()) != null) {
                SelectorList sl = new SelectorList();
                sl.parseSelectorList(slRaw);
                slList.add(sl);
            }
            if (valid) {
                if (slList.isEmpty()) {
                    m.set(field, ALWAYS, format);
                } else {
                    slList.stream()
                            .forEach(sl -> m.set(field,
                                sl.cases.getSet(), sl.actions.getSet(), sl.whens.getSet(),
                                sl.resolves.getSet(), sl.unresolvedCounts.getSet(), sl.errorCounts.getSet(),
                                format));
                }
            } else {
                fluff("See '/help /set format' for help");
            }
            return valid;
        }

        Mode nextMode() {
            String umode = at.next();
            return toMode(umode);
        }

        Mode toMode(String umode) {
            if (umode == null) {
                errorat("Expected a feedback mode");
                valid = false;
                return null;
            }
            Mode m = modeMap.get(umode);
            if (m != null) {
                return m;
            }
            // Failing an exact match, go searching
            Mode[] matches = modeMap.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(umode))
                    .map(e -> e.getValue())
                    .toArray(size -> new Mode[size]);
            if (matches.length == 1) {
                return matches[0];
            } else {
                valid = false;
                if (matches.length == 0) {
                    errorat("Does not match any current feedback mode: %s", umode);
                } else {
                    errorat("Matches more then one current feedback mode: %s", umode);
                }
                fluff("The feedback mode should be one of the following:");
                modeMap.keySet().stream()
                        .forEach(mk -> fluff("   %s", mk));
                return null;
            }
        }

        // Test if the format string is correctly
        final String nextFormat() {
            String format = at.next();
            if (format == null) {
                errorat("Expected format missing");
                valid = false;
                return null;
            }
            if (!at.isQuoted()) {
                errorat("Format '%s' must be quoted", format);
                valid = false;
                return null;
            }
            return format;
        }

        class SelectorList {

            SelectorCollector<FormatCase> cases = new SelectorCollector<>(FormatCase.all);
            SelectorCollector<FormatAction> actions = new SelectorCollector<>(FormatAction.all);
            SelectorCollector<FormatWhen> whens = new SelectorCollector<>(FormatWhen.all);
            SelectorCollector<FormatResolve> resolves = new SelectorCollector<>(FormatResolve.all);
            SelectorCollector<FormatUnresolved> unresolvedCounts = new SelectorCollector<>(FormatUnresolved.all);
            SelectorCollector<FormatErrors> errorCounts = new SelectorCollector<>(FormatErrors.all);

            final void parseSelectorList(String sl) {
                for (String s : sl.split("-")) {
                    SelectorCollector<?> lastCollector = null;
                    for (String as : s.split(",")) {
                        if (!as.isEmpty()) {
                            Selector<?> sel = selectorMap.get(as);
                            if (sel == null) {
                                errorat("Not a valid selector %s in %s", as, s);
                                valid = false;
                                return;
                            }
                            SelectorCollector<?> collector = sel.collector(this);
                            if (lastCollector == null) {
                                if (!collector.isEmpty()) {
                                    errorat("Selector kind in multiple sections of selector list %s in %s", as, s);
                                    valid = false;
                                    return;
                                }
                            } else if (collector != lastCollector) {
                                errorat("Different selector kinds in same sections of selector list %s in %s", as, s);
                                valid = false;
                                return;
                            }
                            collector.add(sel);
                            lastCollector = collector;
                        }
                    }
                }
            }
        }

        final void printFormatHelp() {
            hardrb("help.set.format");
            hardrb("help.set.format.case");
            hardEnums(EnumSet.allOf(FormatCase.class), ev -> ev.doc);
            hardrb("help.set.format.action");
            hardEnums(EnumSet.allOf(FormatAction.class), ev -> ev.doc);
            hardrb("help.set.format.when");
            hardEnums(EnumSet.allOf(FormatWhen.class), ev -> ev.doc);
            hardrb("help.set.format.resolve");
            hardEnums(EnumSet.allOf(FormatResolve.class), ev -> ev.doc);
            hardrb("help.set.format.unresolved");
            hardEnums(EnumSet.allOf(FormatUnresolved.class), ev -> ev.doc);
            hardrb("help.set.format.errors");
            hardEnums(EnumSet.allOf(FormatErrors.class), ev -> ev.doc);
            hardrb("help.set.format.end");
        }

        final void printFeedbackHelp() {
            hardrb("help.set.feedback");
            modeMap.keySet().stream()
                    .forEach(m -> hard("   %s", m));
        }

        final void printNewModeHelp() {
            hardrb("help.set.newmode");
        }

        final void printPromptHelp() {
            hardrb("help.set.prompt");
        }
    }
}
