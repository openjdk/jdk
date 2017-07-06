/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static jdk.internal.jshell.tool.ContinuousCompletionProvider.PERFECT_MATCHER;
import jdk.internal.jshell.tool.JShellTool.CompletionProvider;
import static jdk.internal.jshell.tool.JShellTool.EMPTY_COMPLETION_PROVIDER;

/**
 * Feedback customization support
 *
 * @author Robert Field
 */
class Feedback {

    // Patern for substituted fields within a customized format string
    private static final Pattern FIELD_PATTERN = Pattern.compile("\\{(.*?)\\}");

    // Internal field name for truncation length
    private static final String TRUNCATION_FIELD = "<truncation>";

    // For encoding to Properties String
    private static final String RECORD_SEPARATOR = "\u241E";

    // Current mode -- initial value is placeholder during start-up
    private Mode mode = new Mode("");

    // Retained current mode -- for checks
    private Mode retainedCurrentMode = null;

    // Mapping of mode name to mode
    private final Map<String, Mode> modeMap = new HashMap<>();

    // Mapping of mode names to encoded retained mode
    private final Map<String, String> retainedMap = new HashMap<>();

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

    public String format(String field, FormatCase fc, FormatAction fa, FormatWhen fw,
                    FormatResolve fr, FormatUnresolved fu, FormatErrors fe,
                    String name, String type, String value, String unresolved, List<String> errorLines) {
        return mode.format(field, fc, fa, fw, fr, fu, fe,
                name, type, value, unresolved, errorLines);
    }

    public String truncateVarValue(String value) {
        return mode.truncateVarValue(value);
    }

    public String getPrompt(String nextId) {
        return mode.getPrompt(nextId);
    }

    public String getContinuationPrompt(String nextId) {
        return mode.getContinuationPrompt(nextId);
    }

    public boolean setFeedback(MessageHandler messageHandler, ArgTokenizer at, Consumer<String> retainer) {
        return new Setter(messageHandler, at).setFeedback(retainer);
    }

    public boolean setFormat(MessageHandler messageHandler, ArgTokenizer at) {
        return new Setter(messageHandler, at).setFormat();
    }

    public boolean setTruncation(MessageHandler messageHandler, ArgTokenizer at) {
        return new Setter(messageHandler, at).setTruncation();
    }

    public boolean setMode(MessageHandler messageHandler, ArgTokenizer at, Consumer<String> retainer) {
        return new Setter(messageHandler, at).setMode(retainer);
    }

    public boolean setPrompt(MessageHandler messageHandler, ArgTokenizer at) {
        return new Setter(messageHandler, at).setPrompt();
    }

    public boolean restoreEncodedModes(MessageHandler messageHandler, String encoded) {
        return new Setter(messageHandler, new ArgTokenizer("<init>", "")).restoreEncodedModes(encoded);
    }

    public void markModesReadOnly() {
        modeMap.values().stream()
                .forEach(m -> m.readOnly = true);
    }

    JShellTool.CompletionProvider modeCompletions() {
        return modeCompletions(EMPTY_COMPLETION_PROVIDER);
    }

    JShellTool.CompletionProvider modeCompletions(CompletionProvider successor) {
        return new ContinuousCompletionProvider(
                () -> modeMap.keySet().stream()
                        .collect(toMap(Function.identity(), m -> successor)),
                PERFECT_MATCHER);
    }

    {
        for (FormatCase e : FormatCase.all)
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatAction e : FormatAction.all)
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatResolve e : FormatResolve.all)
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatUnresolved e : FormatUnresolved.all)
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatErrors e : FormatErrors.all)
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
        for (FormatWhen e : FormatWhen.all)
            selectorMap.put(e.name().toLowerCase(Locale.US), e);
    }

    private static class SelectorSets {
        Set<FormatCase> cc;
        Set<FormatAction> ca;
        Set<FormatWhen> cw;
        Set<FormatResolve> cr;
        Set<FormatUnresolved> cu;
        Set<FormatErrors> ce;
    }

    /**
     * Holds all the context of a mode mode
     */
    private static class Mode {

        // Name of mode
        final String name;

        // Display command verification/information
        boolean commandFluff;

        // Event cases: class, method, expression, ...
        final Map<String, List<Setting>> cases;

        boolean readOnly = false;

        String prompt = "\n-> ";
        String continuationPrompt = ">> ";

        static class Setting {

            final long enumBits;
            final String format;

            Setting(long enumBits, String format) {
                this.enumBits = enumBits;
                this.format = format;
            }

            @Override
            public boolean equals(Object o) {
                if (o instanceof Setting) {
                    Setting ing = (Setting) o;
                    return enumBits == ing.enumBits && format.equals(ing.format);
                } else {
                    return false;
                }
            }

            @Override
            public int hashCode() {
                int hash = 7;
                hash = 67 * hash + (int) (this.enumBits ^ (this.enumBits >>> 32));
                hash = 67 * hash + Objects.hashCode(this.format);
                return hash;
            }
        }

        /**
         * Set up an empty mode.
         *
         * @param name
         * @param commandFluff True if should display command fluff messages
         */
        Mode(String name) {
            this.name = name;
            this.cases = new HashMap<>();
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
         * @param m Mode to copy, or null for no fresh
         */
        Mode(String name, Mode m) {
            this.name = name;
            this.commandFluff = m.commandFluff;
            this.prompt = m.prompt;
            this.continuationPrompt = m.continuationPrompt;
            this.cases = new HashMap<>();
            m.cases.entrySet().stream()
                    .forEach(fes -> fes.getValue()
                    .forEach(ing -> add(fes.getKey(), ing)));

        }

        /**
         * Set up a mode reconstituted from a preferences string.
         *
         * @param it the encoded Mode broken into String chunks, may contain
         * subsequent encoded modes
         */
        Mode(Iterator<String> it) {
            this.name = it.next();
            this.commandFluff = Boolean.parseBoolean(it.next());
            this.prompt = it.next();
            this.continuationPrompt = it.next();
            cases = new HashMap<>();
            String field;
            while (!(field = it.next()).equals("***")) {
                String open = it.next();
                assert open.equals("(");
                List<Setting> settings = new ArrayList<>();
                String bits;
                while (!(bits = it.next()).equals(")")) {
                    String format = it.next();
                    Setting ing = new Setting(Long.parseLong(bits), format);
                    settings.add(ing);
                }
                cases.put(field, settings);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Mode) {
                Mode m = (Mode) o;
                return name.equals((m.name))
                        && commandFluff == m.commandFluff
                        && prompt.equals((m.prompt))
                        && continuationPrompt.equals((m.continuationPrompt))
                        && cases.equals((m.cases));
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }

        /**
         * Set if this mode displays informative/confirmational messages on
         * commands.
         *
         * @param fluff the value to set
         */
        void setCommandFluff(boolean fluff) {
            commandFluff = fluff;
        }

        /**
         * Encodes the mode into a String so it can be saved in Preferences.
         *
         * @return the string representation
         */
        String encode() {
            List<String> el = new ArrayList<>();
            el.add(name);
            el.add(String.valueOf(commandFluff));
            el.add(prompt);
            el.add(continuationPrompt);
            for (Entry<String, List<Setting>> es : cases.entrySet()) {
                el.add(es.getKey());
                el.add("(");
                for (Setting ing : es.getValue()) {
                    el.add(String.valueOf(ing.enumBits));
                    el.add(ing.format);
                }
                el.add(")");
            }
            el.add("***");
            return String.join(RECORD_SEPARATOR, el);
        }

        private void add(String field, Setting ing) {
            List<Setting> settings = cases.get(field);
            if (settings == null) {
                settings = new ArrayList<>();
                cases.put(field, settings);
            } else {
                // remove obscured settings
                long mask = ~ing.enumBits;
                settings.removeIf(t -> (t.enumBits & mask) == 0);
            }
            settings.add(ing);
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

        String truncateVarValue(String value) {
            return truncateValue(value,
                    bits(FormatCase.VARVALUE, FormatAction.ADDED,
                            FormatWhen.PRIMARY, FormatResolve.OK,
                            FormatUnresolved.UNRESOLVED0, FormatErrors.ERROR0));
        }

        String truncateValue(String value, long bits) {
            if (value==null) {
                return "";
            } else {
                // Retrieve the truncation length
                String truncField = format(TRUNCATION_FIELD, bits);
                if (truncField.isEmpty()) {
                    // No truncation set, use whole value
                    return value;
                } else {
                    // Convert truncation length to int
                    // this is safe since it has been tested before it is set
                    int trunc = Integer.parseUnsignedInt(truncField);
                    int len = value.length();
                    if (len > trunc) {
                        if (trunc <= 13) {
                            // Very short truncations have no room for "..."
                            return value.substring(0, trunc);
                        } else {
                            // Normal truncation, make total length equal truncation length
                            int endLen = trunc / 3;
                            int startLen = trunc - 5 - endLen;
                            return value.substring(0, startLen) + " ... " + value.substring(len -endLen);
                        }
                    } else {
                        // Within truncation length, use whole value
                        return value;
                    }
                }
            }
        }

        // Compute the display output given full context and values
        String format(FormatCase fc, FormatAction fa, FormatWhen fw,
                    FormatResolve fr, FormatUnresolved fu, FormatErrors fe,
                    String name, String type, String value, String unresolved, List<String> errorLines) {
            return format("display", fc, fa, fw, fr, fu, fe,
                name, type, value, unresolved, errorLines);
        }

        // Compute the display output given full context and values
        String format(String field, FormatCase fc, FormatAction fa, FormatWhen fw,
                    FormatResolve fr, FormatUnresolved fu, FormatErrors fe,
                    String name, String type, String value, String unresolved, List<String> errorLines) {
            // Convert the context into a bit representation used as selectors for store field formats
            long bits = bits(fc, fa, fw, fr, fu, fe);
            String fname = name==null? "" : name;
            String ftype = type==null? "" : type;
            // Compute the representation of value
            String fvalue = truncateValue(value, bits);
            String funresolved = unresolved==null? "" : unresolved;
            String errors = errorLines.stream()
                    .map(el -> String.format(
                            format("errorline", bits),
                            fname, ftype, fvalue, funresolved, "*cannot-use-errors-here*", el))
                    .collect(joining());
            return String.format(
                    format(field, bits),
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

    private static SelectorSets unpackEnumbits(long enumBits) {
        class Unpacker {

            SelectorSets u = new SelectorSets();
            long b = enumBits;

            <E extends Enum<E>> Set<E> unpackEnumbits(E[] values) {
                Set<E> c = new HashSet<>();
                for (int i = 0; i < values.length; ++i) {
                    if ((b & (1 << i)) != 0) {
                        c.add(values[i]);
                    }
                }
                b >>>= values.length;
                return c;
            }

            SelectorSets unpack() {
                // inverseof the order they were packed
                u.ce = unpackEnumbits(FormatErrors.values());
                u.cu = unpackEnumbits(FormatUnresolved.values());
                u.cr = unpackEnumbits(FormatResolve.values());
                u.cw = unpackEnumbits(FormatWhen.values());
                u.ca = unpackEnumbits(FormatAction.values());
                u.cc = unpackEnumbits(FormatCase.values());
                return u;
            }
        }
        return new Unpacker().unpack();
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
        private final MessageHandler messageHandler;
        boolean valid = true;

        Setter(MessageHandler messageHandler, ArgTokenizer at) {
            this.messageHandler = messageHandler;
            this.at = at;
            at.allowedOptions("-retain");
        }

        void fluff(String format, Object... args) {
            messageHandler.fluff(format, args);
        }

        void hard(String format, Object... args) {
            messageHandler.hard(format, args);
        }

        void fluffmsg(String messageKey, Object... args) {
            messageHandler.fluffmsg(messageKey, args);
        }

        void hardmsg(String messageKey, Object... args) {
            messageHandler.hardmsg(messageKey, args);
        }

        boolean showFluff() {
            return messageHandler.showFluff();
        }

        void errorat(String messageKey, Object... args) {
            if (!valid) {
                // no spew of errors
                return;
            }
            valid = false;
            Object[] a2 = Arrays.copyOf(args, args.length + 2);
            a2[args.length] = at.whole();
            messageHandler.errormsg(messageKey, a2);
        }

        String selectorsToString(SelectorSets u) {
            StringBuilder sb = new StringBuilder();
            selectorToString(sb, u.cc, FormatCase.values());
            selectorToString(sb, u.ca, FormatAction.values());
            selectorToString(sb, u.cw, FormatWhen.values());
            selectorToString(sb, u.cr, FormatResolve.values());
            selectorToString(sb, u.cu, FormatUnresolved.values());
            selectorToString(sb, u.ce, FormatErrors.values());
            return sb.toString();
        }

        private <E extends Enum<E>> void selectorToString(StringBuilder sb, Set<E> c, E[] values) {
            if (!c.containsAll(Arrays.asList(values))) {
                sb.append(c.stream()
                        .sorted((x, y) -> x.ordinal() - y.ordinal())
                        .map(v -> v.name().toLowerCase(Locale.US))
                        .collect(new Collector<CharSequence, StringJoiner, String>() {
                            @Override
                            public BiConsumer<StringJoiner, CharSequence> accumulator() {
                                return StringJoiner::add;
                            }

                            @Override
                            public Supplier<StringJoiner> supplier() {
                                return () -> new StringJoiner(",", (sb.length() == 0)? "" : "-", "")
                                        .setEmptyValue("");
                            }

                            @Override
                            public BinaryOperator<StringJoiner> combiner() {
                                return StringJoiner::merge;
                            }

                            @Override
                            public Function<StringJoiner, String> finisher() {
                                return StringJoiner::toString;
                            }

                            @Override
                            public Set<Characteristics> characteristics() {
                                return Collections.emptySet();
                            }
                        }));
            }
        }

        // Show format settings -- in a predictable order, for testing...
        void showFormatSettings(Mode sm, String f) {
            if (sm == null) {
                modeMap.entrySet().stream()
                        .sorted((es1, es2) -> es1.getKey().compareTo(es2.getKey()))
                        .forEach(m -> showFormatSettings(m.getValue(), f));
            } else {
                sm.cases.entrySet().stream()
                        .filter(ec -> (f == null)
                            ? !ec.getKey().equals(TRUNCATION_FIELD)
                            : ec.getKey().equals(f))
                        .sorted((ec1, ec2) -> ec1.getKey().compareTo(ec2.getKey()))
                        .forEach(ec -> {
                            ec.getValue().forEach(s -> {
                                hard("/set format %s %s %s %s",
                                        sm.name, ec.getKey(), toStringLiteral(s.format),
                                        selectorsToString(unpackEnumbits(s.enumBits)));

                            });
                        });
            }
        }

        void showTruncationSettings(Mode sm) {
            if (sm == null) {
                modeMap.values().forEach(this::showTruncationSettings);
            } else {
                List<Mode.Setting> trunc = sm.cases.get(TRUNCATION_FIELD);
                if (trunc != null) {
                    trunc.forEach(s -> {
                        hard("/set truncation %s %s %s",
                                sm.name, s.format,
                                selectorsToString(unpackEnumbits(s.enumBits)));
                    });
                }
            }
        }

        void showPromptSettings(Mode sm) {
            if (sm == null) {
                modeMap.values().forEach(this::showPromptSettings);
            } else {
                hard("/set prompt %s %s %s",
                        sm.name,
                        toStringLiteral(sm.prompt),
                        toStringLiteral(sm.continuationPrompt));
            }
        }

        void showModeSettings(String umode, String msg) {
            if (umode == null) {
                modeMap.values().forEach(this::showModeSettings);
            } else {
                Mode m;
                String retained = retainedMap.get(umode);
                if (retained == null) {
                    m = searchForMode(umode, msg);
                    if (m == null) {
                        return;
                    }
                    umode = m.name;
                    retained = retainedMap.get(umode);
                } else {
                    m = modeMap.get(umode);
                }
                if (retained != null) {
                    Mode rm = new Mode(encodedModeIterator(retained));
                    showModeSettings(rm);
                    hard("/set mode -retain %s", umode);
                    if (m != null && !m.equals(rm)) {
                        hard("");
                        showModeSettings(m);
                    }
                } else {
                    showModeSettings(m);
                }
            }
        }

        void showModeSettings(Mode sm) {
            hard("/set mode %s %s",
                    sm.name, sm.commandFluff ? "-command" : "-quiet");
            showPromptSettings(sm);
            showFormatSettings(sm, null);
            showTruncationSettings(sm);
        }

        void showFeedbackSetting() {
            if (retainedCurrentMode != null) {
                hard("/set feedback -retain %s", retainedCurrentMode.name);
            }
            if (mode != retainedCurrentMode) {
                hard("/set feedback %s", mode.name);
            }
        }

        // For /set prompt <mode> "<prompt>" "<continuation-prompt>"
        boolean setPrompt() {
            Mode m = nextMode();
            String prompt = nextFormat();
            String continuationPrompt = nextFormat();
            checkOptionsAndRemainingInput();
            if (valid && prompt == null) {
                showPromptSettings(m);
                return valid;
            }
            if (valid && m.readOnly) {
                errorat("jshell.err.not.valid.with.predefined.mode", m.name);
            } else if (continuationPrompt == null) {
                errorat("jshell.err.continuation.prompt.required");
            }
            if (valid) {
                m.setPrompts(prompt, continuationPrompt);
            } else {
                fluffmsg("jshell.msg.see", "/help /set prompt");
            }
            return valid;
        }

        /**
         * Set mode. Create, changed, or delete a feedback mode. For @{code /set
         * mode <mode> [<old-mode>] [-command|-quiet|-delete]}.
         *
         * @return true if successful
         */
        boolean setMode(Consumer<String> retainer) {
            class SetMode {

                final String umode;
                final String omode;
                final boolean commandOption;
                final boolean quietOption;
                final boolean deleteOption;
                final boolean retainOption;

                SetMode() {
                    at.allowedOptions("-command", "-quiet", "-delete", "-retain");
                    umode = nextModeIdentifier();
                    omode = nextModeIdentifier();
                    checkOptionsAndRemainingInput();
                    commandOption = at.hasOption("-command");
                    quietOption = at.hasOption("-quiet");
                    deleteOption = at.hasOption("-delete");
                    retainOption = at.hasOption("-retain");
                }

                void delete() {
                    // Note: delete, for safety reasons, does NOT do name matching
                    if (commandOption || quietOption) {
                        errorat("jshell.err.conflicting.options");
                    } else if (retainOption
                            ? !retainedMap.containsKey(umode) && !modeMap.containsKey(umode)
                            : !modeMap.containsKey(umode)) {
                        // Cannot delete a mode that does not exist
                        errorat("jshell.err.mode.unknown", umode);
                    } else if (omode != null) {
                        // old mode is for creation
                        errorat("jshell.err.unexpected.at.end", omode);
                    } else if (mode.name.equals(umode)) {
                        // Cannot delete the current mode out from under us
                        errorat("jshell.err.cannot.delete.current.mode", umode);
                    } else if (retainOption && retainedCurrentMode != null &&
                             retainedCurrentMode.name.equals(umode)) {
                        // Cannot delete the retained mode or re-start will have an error
                        errorat("jshell.err.cannot.delete.retained.mode", umode);
                    } else {
                        Mode m = modeMap.get(umode);
                        if (m != null && m.readOnly) {
                            errorat("jshell.err.not.valid.with.predefined.mode", umode);
                        } else {
                            // Remove the mode
                            modeMap.remove(umode);
                            if (retainOption) {
                                // Remove the retained mode
                                retainedMap.remove(umode);
                                updateRetainedModes();
                            }
                        }
                    }
                }

                void retain() {
                    if (commandOption || quietOption) {
                        errorat("jshell.err.conflicting.options");
                    } else if (omode != null) {
                        // old mode is for creation
                        errorat("jshell.err.unexpected.at.end", omode);
                    } else {
                        Mode m = modeMap.get(umode);
                        if (m == null) {
                            // can only retain existing modes
                            errorat("jshell.err.mode.unknown", umode);
                        } else if (m.readOnly) {
                            errorat("jshell.err.not.valid.with.predefined.mode", umode);
                        } else {
                            // Add to local cache of retained current encodings
                            retainedMap.put(m.name, m.encode());
                            updateRetainedModes();
                        }
                    }
                }

                void updateRetainedModes() {
                    // Join all the retained encodings
                    String encoded = String.join(RECORD_SEPARATOR, retainedMap.values());
                    // Retain it
                    retainer.accept(encoded);
                }

                void create() {
                    if (commandOption && quietOption) {
                        errorat("jshell.err.conflicting.options");
                    } else if (!commandOption && !quietOption) {
                        errorat("jshell.err.mode.creation");
                    } else if (modeMap.containsKey(umode)) {
                        // Mode already exists
                        errorat("jshell.err.mode.exists", umode);
                    } else {
                        Mode om = searchForMode(omode);
                        if (valid) {
                            // We are copying an existing mode and/or creating a
                            // brand-new mode -- in either case create from scratch
                            Mode m = (om != null)
                                    ? new Mode(umode, om)
                                    : new Mode(umode);
                            modeMap.put(umode, m);
                            fluffmsg("jshell.msg.feedback.new.mode", m.name);
                            m.setCommandFluff(commandOption);
                        }
                    }
                }

                boolean set() {
                    if (valid && !commandOption && !quietOption && !deleteOption &&
                            omode == null && !retainOption) {
                        // Not a creation, deletion, or retain -- show mode(s)
                        showModeSettings(umode, "jshell.err.mode.creation");
                    } else if (valid && umode == null) {
                        errorat("jshell.err.missing.mode");
                    } else if (valid && deleteOption) {
                        delete();
                    } else if (valid && retainOption) {
                        retain();
                    } else if (valid) {
                        create();
                    }
                    if (!valid) {
                        fluffmsg("jshell.msg.see", "/help /set mode");
                    }
                    return valid;
                }
            }
            return new SetMode().set();
        }

        // For /set format <mode> <field> "<format>" <selector>...
        boolean setFormat() {
            Mode m = nextMode();
            String field = toIdentifier(next(), "jshell.err.field.name");
            String format = nextFormat();
            if (valid && format == null) {
                if (field != null && m != null && !m.cases.containsKey(field)) {
                    errorat("jshell.err.field.name", field);
                } else {
                    showFormatSettings(m, field);
                }
            } else {
                installFormat(m, field, format, "/help /set format");
            }
            return valid;
        }

        // For /set truncation <mode> <length> <selector>...
        boolean setTruncation() {
            Mode m = nextMode();
            String length = next();
            if (length == null) {
                showTruncationSettings(m);
            } else {
                try {
                    // Assure that integer format is correct
                    Integer.parseUnsignedInt(length);
                } catch (NumberFormatException ex) {
                    errorat("jshell.err.truncation.length.not.integer", length);
                }
                // install length into an internal format field
                installFormat(m, TRUNCATION_FIELD, length, "/help /set truncation");
            }
            return valid;
        }

        // For /set feedback <mode>
        boolean setFeedback(Consumer<String> retainer) {
            String umode = next();
            checkOptionsAndRemainingInput();
            boolean retainOption = at.hasOption("-retain");
            if (valid && umode == null && !retainOption) {
                showFeedbackSetting();
                hard("");
                showFeedbackModes();
                return true;
            }
            if (valid) {
                Mode m = umode == null
                        ? mode
                        : searchForMode(toModeIdentifier(umode));
                if (valid && retainOption && !m.readOnly && !retainedMap.containsKey(m.name)) {
                    errorat("jshell.err.retained.feedback.mode.must.be.retained.or.predefined");
                }
                if (valid) {
                    if (umode != null) {
                        mode = m;
                        fluffmsg("jshell.msg.feedback.mode", mode.name);
                    }
                    if (retainOption) {
                        retainedCurrentMode = m;
                        retainer.accept(m.name);
                    }
                }
            }
            if (!valid) {
                fluffmsg("jshell.msg.see", "/help /set feedback");
                return false;
            }
            return true;
        }

        boolean restoreEncodedModes(String allEncoded) {
            try {
                // Iterate over each record in each encoded mode
                Iterator<String> itr = encodedModeIterator(allEncoded);
                while (itr.hasNext()) {
                    // Reconstruct the encoded mode
                    Mode m = new Mode(itr);
                    modeMap.put(m.name, m);
                    // Continue to retain it a new retains occur
                    retainedMap.put(m.name, m.encode());
                }
                return true;
            } catch (Throwable exc) {
                // Catastrophic corruption -- clear map
                errorat("jshell.err.retained.mode.failure", exc);
                retainedMap.clear();
                return false;
            }
        }

        Iterator<String> encodedModeIterator(String encoded) {
            String[] ms = encoded.split(RECORD_SEPARATOR);
            return Arrays.asList(ms).iterator();
        }

        // install the format of a field under parsed selectors
        void installFormat(Mode m, String field, String format, String help) {
            String slRaw;
            List<SelectorList> slList = new ArrayList<>();
            while (valid && (slRaw = next()) != null) {
                SelectorList sl = new SelectorList();
                sl.parseSelectorList(slRaw);
                slList.add(sl);
            }
            checkOptionsAndRemainingInput();
            if (valid) {
                if (m.readOnly) {
                    errorat("jshell.err.not.valid.with.predefined.mode", m.name);
                } else if (slList.isEmpty()) {
                    // No selectors specified, then always the format
                    m.set(field, ALWAYS, format);
                } else {
                    // Set the format of the field for specified selector
                    slList.stream()
                            .forEach(sl -> m.set(field,
                            sl.cases.getSet(), sl.actions.getSet(), sl.whens.getSet(),
                            sl.resolves.getSet(), sl.unresolvedCounts.getSet(), sl.errorCounts.getSet(),
                            format));
                }
            } else {
                fluffmsg("jshell.msg.see", help);
            }
        }

        void checkOptionsAndRemainingInput() {
            String junk = at.remainder();
            if (!junk.isEmpty()) {
                errorat("jshell.err.unexpected.at.end", junk);
            } else {
                String bad = at.badOptions();
                if (!bad.isEmpty()) {
                    errorat("jshell.err.unknown.option", bad);
                }
            }
        }

        String next() {
            String s = at.next();
            if (s == null) {
                checkOptionsAndRemainingInput();
            }
            return s;
        }

        /**
         * Check that the specified string is an identifier (Java identifier).
         * If null display the missing error. If it is not an identifier,
         * display the error.
         *
         * @param id the string to check, MUST be the most recently retrieved
         * token from 'at'.
         * @param missing null for no null error, otherwise the resource error to display if id is null
         * @param err the resource error to display if not an identifier
         * @return the identifier string, or null if null or not an identifier
         */
        private String toIdentifier(String id, String err) {
            if (!valid || id == null) {
                return null;
            }
            if (at.isQuoted() ||
                    !id.codePoints().allMatch(Character::isJavaIdentifierPart)) {
                errorat(err, id);
                return null;
            }
            return id;
        }

        private String toModeIdentifier(String id) {
            return toIdentifier(id, "jshell.err.mode.name");
        }

        private String nextModeIdentifier() {
            return toModeIdentifier(next());
        }

        private Mode nextMode() {
            String umode = nextModeIdentifier();
            return searchForMode(umode);
        }

        private Mode searchForMode(String umode) {
            return searchForMode(umode, null);
        }

        private Mode searchForMode(String umode, String msg) {
            if (!valid || umode == null) {
                return null;
            }
            Mode m = modeMap.get(umode);
            if (m != null) {
                return m;
            }
            // Failing an exact match, go searching
            Mode[] matches = modeMap.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(umode))
                    .map(Entry::getValue)
                    .toArray(Mode[]::new);
            if (matches.length == 1) {
                return matches[0];
            } else {
                if (msg != null) {
                    hardmsg(msg, "");
                }
                if (matches.length == 0) {
                    errorat("jshell.err.feedback.does.not.match.mode", umode);
                } else {
                    errorat("jshell.err.feedback.ambiguous.mode", umode);
                }
                if (showFluff()) {
                    showFeedbackModes();
                }
                return null;
            }
        }

        void showFeedbackModes() {
            if (!retainedMap.isEmpty()) {
                hardmsg("jshell.msg.feedback.retained.mode.following");
                retainedMap.keySet().stream()
                        .sorted()
                        .forEach(mk -> hard("   %s", mk));
            }
            hardmsg("jshell.msg.feedback.mode.following");
            modeMap.keySet().stream()
                    .sorted()
                    .forEach(mk -> hard("   %s", mk));
        }

        // Read and test if the format string is correctly
        private String nextFormat() {
            return toFormat(next());
        }

        // Test if the format string is correctly
        private String toFormat(String format) {
            if (!valid || format == null) {
                return null;
            }
            if (!at.isQuoted()) {
                errorat("jshell.err.feedback.must.be.quoted", format);
               return null;
            }
            return format;
        }

        // Convert to a quoted string
        private String toStringLiteral(String s) {
            StringBuilder sb = new StringBuilder();
            sb.append('"');
            final int length = s.length();
            for (int offset = 0; offset < length;) {
                final int codepoint = s.codePointAt(offset);

                switch (codepoint) {
                    case '\b':
                        sb.append("\\b");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\f':
                        sb.append("\\f");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\"':
                        sb.append("\\\"");
                        break;
                    case '\'':
                        sb.append("\\'");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    default:
                        if (codepoint < 040) {
                            sb.append(String.format("\\%o", codepoint));
                        } else {
                            sb.appendCodePoint(codepoint);
                        }
                        break;
                }

                // do something with the codepoint
                offset += Character.charCount(codepoint);

            }
            sb.append('"');
            return sb.toString();
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
                                errorat("jshell.err.feedback.not.a.valid.selector", as, s);
                                return;
                            }
                            SelectorCollector<?> collector = sel.collector(this);
                            if (lastCollector == null) {
                                if (!collector.isEmpty()) {
                                    errorat("jshell.err.feedback.multiple.sections", as, s);
                                    return;
                                }
                            } else if (collector != lastCollector) {
                                errorat("jshell.err.feedback.different.selector.kinds", as, s);
                                return;
                            }
                            collector.add(sel);
                            lastCollector = collector;
                        }
                    }
                }
            }
        }
    }
}
