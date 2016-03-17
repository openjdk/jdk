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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public boolean shouldDisplayCommandFluff() {
        return mode.commandFluff;
    }

    public String getPre() {
        return mode.pre;
    }

    public String getPost() {
        return mode.post;
    }

    public String getErrorPre() {
        return mode.errorPre;
    }

    public String getErrorPost() {
        return mode.errorPost;
    }

    public String getFormat(FormatCase fc, FormatWhen fw, FormatAction fa, FormatResolve fr,
            boolean hasName, boolean hasType, boolean hasResult) {
        return mode.getFormat(fc, fw, fa, fr, hasName, hasType, hasResult);
    }

    public String getPrompt(String nextId) {
        return mode.getPrompt(nextId);
    }

    public String getContinuationPrompt(String nextId) {
        return mode.getContinuationPrompt(nextId);
    }

    public boolean setFeedback(JShellTool tool, ArgTokenizer at) {
        return new FormatSetter(tool, at).setFeedback();
    }

    public boolean setField(JShellTool tool, ArgTokenizer at) {
        return new FormatSetter(tool, at).setField();
    }

    public boolean setFormat(JShellTool tool, ArgTokenizer at) {
        return new FormatSetter(tool, at).setFormat();
    }

    public boolean setNewMode(JShellTool tool, ArgTokenizer at) {
        return new FormatSetter(tool, at).setNewMode();
    }

    public boolean setPrompt(JShellTool tool, ArgTokenizer at) {
        return new FormatSetter(tool, at).setPrompt();
    }

    public void printFeedbackHelp(JShellTool tool) {
        new FormatSetter(tool, null).printFeedbackHelp();
    }

    public void printFieldHelp(JShellTool tool) {
        new FormatSetter(tool, null).printFieldHelp();
    }

    public void printFormatHelp(JShellTool tool) {
        new FormatSetter(tool, null).printFormatHelp();
    }

    public void printNewModeHelp(JShellTool tool) {
        new FormatSetter(tool, null).printNewModeHelp();
    }

    public void printPromptHelp(JShellTool tool) {
        new FormatSetter(tool, null).printPromptHelp();
    }

    /**
     * Holds all the context of a mode mode
     */
    private class Mode {

        // Use name of mode mode

        final String name;

        // Display command verification/information
        final boolean commandFluff;

        // event cases: class, method
        final EnumMap<FormatCase, EnumMap<FormatAction, EnumMap<FormatWhen, String>>> cases;

        // action names: add. modified, replaced, ...
        final EnumMap<FormatAction, EnumMap<FormatWhen, String>> actions;

        // resolution status description format with %s for unresolved
        final EnumMap<FormatResolve, EnumMap<FormatWhen, String>> resolves;

        // primary snippet vs update
        final EnumMap<FormatWhen, String> whens;

        // fixed map of how to get format string for a field, given a specific formatting contet
        final EnumMap<FormatField, Function<Context, String>> fields;

        // format wrappers for name, type, and result
        String fname = "%s";
        String ftype = "%s";
        String fresult = "%s";

        // start and end, also used by hard-coded output
        String pre = "|  ";
        String post = "\n";
        String errorPre = "|  Error: ";
        String errorPost = "\n";

        String prompt = "\n-> ";
        String continuationPrompt = ">> ";

        /**
         * The context of a specific mode to potentially display.
         */
        class Context {

            final FormatCase fc;
            final FormatAction fa;
            final FormatResolve fr;
            final FormatWhen fw;
            final boolean hasName;
            final boolean hasType;
            final boolean hasResult;

            Context(FormatCase fc, FormatWhen fw, FormatAction fa, FormatResolve fr,
                    boolean hasName, boolean hasType, boolean hasResult) {
                this.fc = fc;
                this.fa = fa;
                this.fr = fr;
                this.fw = fw;
                this.hasName = hasName;
                this.hasType = hasType;
                this.hasResult = hasResult;
            }

            String when() {
                return whens.get(fw);
            }

            String action() {
                return actions.get(fa).get(fw);
            }

            String resolve() {
                return String.format(resolves.get(fr).get(fw), FormatField.RESOLVE.form);
            }

            String name() {
                return hasName
                        ? String.format(fname, FormatField.NAME.form)
                        : "";
            }

            String type() {
                return hasType
                        ? String.format(ftype, FormatField.TYPE.form)
                        : "";
            }

            String result() {
                return hasResult
                        ? String.format(fresult, FormatField.RESULT.form)
                        : "";
            }

            /**
             * Lookup format based on case, action, and whether it update.
             * Replace fields with context specific formats.
             *
             * @return format string
             */
            String format() {
                String format = cases.get(fc).get(fa).get(fw);
                if (format == null) {
                    return "";
                }
                Matcher m = FIELD_PATTERN.matcher(format);
                StringBuffer sb = new StringBuffer(format.length());
                while (m.find()) {
                    String fieldName = m.group(1).toUpperCase(Locale.US);
                    String sub = null;
                    for (FormatField f : FormatField.values()) {
                        if (f.name().startsWith(fieldName)) {
                            sub = fields.get(f).apply(this);
                            break;
                        }
                    }
                    if (sub != null) {
                        m.appendReplacement(sb, Matcher.quoteReplacement(sub));
                    }
                }
                m.appendTail(sb);
                return sb.toString();
            }
        }

        {
            // set fixed mappings of fields
            fields = new EnumMap<>(FormatField.class);
            fields.put(FormatField.WHEN, c -> c.when());
            fields.put(FormatField.ACTION, c -> c.action());
            fields.put(FormatField.RESOLVE, c -> c.resolve());
            fields.put(FormatField.NAME, c -> c.name());
            fields.put(FormatField.TYPE, c -> c.type());
            fields.put(FormatField.RESULT, c -> c.result());
            fields.put(FormatField.PRE, c -> pre);
            fields.put(FormatField.POST, c -> post);
            fields.put(FormatField.ERRORPRE, c -> errorPre);
            fields.put(FormatField.ERRORPOST, c -> errorPost);
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
            cases = new EnumMap<>(FormatCase.class);
            for (FormatCase fc : FormatCase.values()) {
                EnumMap<FormatAction, EnumMap<FormatWhen, String>> ac = new EnumMap<>(FormatAction.class);
                cases.put(fc, ac);
                for (FormatAction fa : FormatAction.values()) {
                    EnumMap<FormatWhen, String> aw = new EnumMap<>(FormatWhen.class);
                    ac.put(fa, aw);
                    for (FormatWhen fw : FormatWhen.values()) {
                        aw.put(fw, "");
                    }
                }
            }

            actions = new EnumMap<>(FormatAction.class);
            for (FormatAction fa : FormatAction.values()) {
                EnumMap<FormatWhen, String> afw = new EnumMap<>(FormatWhen.class);
                actions.put(fa, afw);
                for (FormatWhen fw : FormatWhen.values()) {
                    afw.put(fw, fa.name() + "-" + fw.name());
                }
            }

            resolves = new EnumMap<>(FormatResolve.class);
            for (FormatResolve fr : FormatResolve.values()) {
                EnumMap<FormatWhen, String> arw = new EnumMap<>(FormatWhen.class);
                resolves.put(fr, arw);
                for (FormatWhen fw : FormatWhen.values()) {
                    arw.put(fw, fr.name() + "-" + fw.name() + ": %s");
                }
            }

            whens = new EnumMap<>(FormatWhen.class);
            for (FormatWhen fw : FormatWhen.values()) {
                whens.put(fw, fw.name());
            }
        }

        /**
         * Set up a copied mode.
         *
         * @param name
         * @param commandFluff True if should display command fluff messages
         * @param m Mode to copy
         */
        Mode(String name, boolean commandFluff, Mode m) {
            this.name = name;
            this.commandFluff = commandFluff;
            cases = new EnumMap<>(FormatCase.class);
            for (FormatCase fc : FormatCase.values()) {
                EnumMap<FormatAction, EnumMap<FormatWhen, String>> ac = new EnumMap<>(FormatAction.class);
                EnumMap<FormatAction, EnumMap<FormatWhen, String>> mc = m.cases.get(fc);
                cases.put(fc, ac);
                for (FormatAction fa : FormatAction.values()) {
                    EnumMap<FormatWhen, String> aw = new EnumMap<>(mc.get(fa));
                    ac.put(fa, aw);
                }
            }

            actions = new EnumMap<>(FormatAction.class);
            for (FormatAction fa : FormatAction.values()) {
                EnumMap<FormatWhen, String> afw = new EnumMap<>(m.actions.get(fa));
                actions.put(fa, afw);
            }

            resolves = new EnumMap<>(FormatResolve.class);
            for (FormatResolve fr : FormatResolve.values()) {
                EnumMap<FormatWhen, String> arw = new EnumMap<>(m.resolves.get(fr));
                resolves.put(fr, arw);
            }

            whens = new EnumMap<>(m.whens);

            this.fname = m.fname;
            this.ftype = m.ftype;
            this.fresult = m.fresult;
            this.pre = m.pre;
            this.post = m.post;
            this.errorPre = m.errorPre;
            this.errorPost = m.errorPost;
            this.prompt = m.prompt;
            this.continuationPrompt = m.continuationPrompt;
        }

        String getFormat(FormatCase fc, FormatWhen fw, FormatAction fa, FormatResolve fr,
                boolean hasName, boolean hasType, boolean hasResult) {
            Context context = new Context(fc, fw, fa, fr,
                    hasName, hasType, hasResult);
            return context.format();
        }

        void setCases(String format, Collection<FormatCase> cc, Collection<FormatAction> ca, Collection<FormatWhen> cw) {
            for (FormatCase fc : cc) {
                EnumMap<FormatAction, EnumMap<FormatWhen, String>> ma = cases.get(fc);
                for (FormatAction fa : ca) {
                    EnumMap<FormatWhen, String> mw = ma.get(fa);
                    for (FormatWhen fw : cw) {
                        mw.put(fw, format);
                    }
                }
            }
        }

        void setActions(String format, Collection<FormatAction> ca, Collection<FormatWhen> cw) {
            for (FormatAction fa : ca) {
                EnumMap<FormatWhen, String> mw = actions.get(fa);
                for (FormatWhen fw : cw) {
                    mw.put(fw, format);
                }
            }
        }

        void setResolves(String format, Collection<FormatResolve> cr, Collection<FormatWhen> cw) {
            for (FormatResolve fr : cr) {
                EnumMap<FormatWhen, String> mw = resolves.get(fr);
                for (FormatWhen fw : cw) {
                    mw.put(fw, format);
                }
            }
        }

        void setWhens(String format, Collection<FormatWhen> cw) {
            for (FormatWhen fw : cw) {
                whens.put(fw, format);
            }
        }

        void setName(String s) {
            fname = s;
        }

        void setType(String s) {
            ftype = s;
        }

        void setResult(String s) {
            fresult = s;
        }

        void setPre(String s) {
            pre = s;
        }

        void setPost(String s) {
            post = s;
        }

        void setErrorPre(String s) {
            errorPre = s;
        }

        void setErrorPost(String s) {
            errorPost = s;
        }

        String getPre() {
            return pre;
        }

        String getPost() {
            return post;
        }

        String getErrorPre() {
            return errorPre;
        }

        String getErrorPost() {
            return errorPost;
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

    /**
     * The brace delimited substitutions
     */
    public enum FormatField {
        WHEN,
        ACTION,
        RESOLVE("%1$s"),
        NAME("%2$s"),
        TYPE("%3$s"),
        RESULT("%4$s"),
        PRE,
        POST,
        ERRORPRE,
        ERRORPOST;
        String form;

        FormatField(String s) {
            this.form = s;
        }

        FormatField() {
            this.form = null;
        }
    }

    /**
     * The event cases
     */
    public enum FormatCase {
        IMPORT("import declaration: {action} {name}"),
        CLASS("class, interface, enum, or annotation declaration: {action} {name} {resolve}"),
        INTERFACE("class, interface, enum, or annotation declaration: {action} {name} {resolve}"),
        ENUM("class, interface, enum, or annotation declaration: {action} {name} {resolve}"),
        ANNOTATION("annotation interface declaration: {action} {name} {resolve}"),
        METHOD("method declaration: {action} {name} {type}==parameter-types {resolve}"),
        VARDECL("variable declaration: {action} {name} {type} {resolve}"),
        VARDECLRECOVERABLE("recoverably failed variable declaration: {action} {name} {resolve}"),
        VARINIT("variable declaration with init: {action} {name} {type} {resolve} {result}"),
        VARRESET("variable reset on update: {action} {name}"),
        EXPRESSION("expression: {action}=='Saved to scratch variable' {name} {type} {result}"),
        VARVALUE("variable value expression: {action} {name} {type} {result}"),
        ASSIGNMENT("assign variable: {action} {name} {type} {result}"),
        STATEMENT("statement: {action}");
        String doc;

        private FormatCase(String doc) {
            this.doc = doc;
        }
    }

    /**
     * The event actions
     */
    public enum FormatAction {
        ADDED("snippet has been added"),
        MODIFIED("an existing snippet has been modified"),
        REPLACED("an existing snippet has been replaced with a new snippet"),
        OVERWROTE("an existing snippet has been overwritten"),
        DROPPED("snippet has been dropped"),
        REJECTED("snippet has failed and been rejected");
        String doc;

        private FormatAction(String doc) {
            this.doc = doc;
        }
    }

    /**
     * When the event occurs: primary or update
     */
    public enum FormatWhen {
        PRIMARY("the entered snippet"),
        UPDATE("an update to a dependent snippet");
        String doc;

        private FormatWhen(String doc) {
            this.doc = doc;
        }
    }

    /**
     * Resolution problems with event
     */
    public enum FormatResolve {
        OK("resolved correctly"),
        DEFINED("defined despite recoverably unresolved references"),
        NOTDEFINED("not defined because of recoverably unresolved references");
        String doc;

        private FormatResolve(String doc) {
            this.doc = doc;
        }
    }

    // Class used to set custom eval output formats
    // For both /set format  and /set field -- Parse arguments, setting custom format, or printing error
    private class FormatSetter {

        private final ArgTokenizer at;
        private final JShellTool tool;
        boolean valid = true;

        class Case<E1 extends Enum<E1>, E2 extends Enum<E2>, E3 extends Enum<E3>> {

            Set<E1> e1;
            Set<E2> e2;
            Set<E3> e3;

            Case(Set<E1> e1, Set<E2> e2, Set<E3> e3) {
                this.e1 = e1;
                this.e2 = e2;
                this.e3 = e3;
            }

            Case(Set<E1> e1, Set<E2> e2) {
                this.e1 = e1;
                this.e2 = e2;
            }
        }

        FormatSetter(JShellTool tool, ArgTokenizer at) {
            this.tool = tool;
            this.at = at;
        }

        void hard(String format, Object... args) {
            tool.hard(format, args);
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
            String format = nextFormat();
            if (valid) {
                List<Case<FormatCase, FormatAction, FormatWhen>> specs = new ArrayList<>();
                String s;
                while ((s = at.next()) != null) {
                    String[] d = s.split("-");
                    specs.add(new Case<>(
                            parseFormatCase(d, 0),
                            parseFormatAction(d, 1),
                            parseFormatWhen(d, 2)
                    ));
                }
                if (valid && specs.isEmpty()) {
                    errorat("At least one selector required");
                    valid = false;
                }
                if (valid) {
                    // set the format in the specified cases
                    specs.stream()
                            .forEach(c -> m.setCases(format, c.e1, c.e2, c.e3));
                }
            }
            if (!valid) {
                fluff("See '/help /set format' for help");
            }
            return valid;
        }

        // For /set field mode <field> "<format>" <selector>...
        boolean setField() {
            Mode m = nextMode();
            String fieldName = at.next();
            FormatField field = parseFormatSelector(fieldName, EnumSet.allOf(FormatField.class), "field");
            String format = nextFormat();
            if (valid) {
                switch (field) {
                    case ACTION: {
                        List<Case<FormatAction, FormatWhen, FormatWhen>> specs = new ArrayList<>();
                        String s;
                        while ((s = at.next()) != null) {
                            String[] d = s.split("-");
                            specs.add(new Case<>(
                                    parseFormatAction(d, 0),
                                    parseFormatWhen(d, 1)
                            ));
                        }
                        if (valid && specs.isEmpty()) {
                            errorat("At least one selector required");
                            valid = false;
                        }
                        if (valid) {
                            // set the format of the specified actions
                            specs.stream()
                                    .forEach(c -> m.setActions(format, c.e1, c.e2));
                        }
                        break;
                    }
                    case RESOLVE: {
                        List<Case<FormatResolve, FormatWhen, FormatWhen>> specs = new ArrayList<>();
                        String s;
                        while ((s = at.next()) != null) {
                            String[] d = s.split("-");
                            specs.add(new Case<>(
                                    parseFormatResolve(d, 0),
                                    parseFormatWhen(d, 1)
                            ));
                        }
                        if (valid && specs.isEmpty()) {
                            errorat("At least one selector required");
                            valid = false;
                        }
                        if (valid) {
                            // set the format of the specified resolves
                            specs.stream()
                                    .forEach(c -> m.setResolves(format, c.e1, c.e2));
                        }
                        break;
                    }
                    case WHEN: {
                        List<Case<FormatWhen, FormatWhen, FormatWhen>> specs = new ArrayList<>();
                        String s;
                        while ((s = at.next()) != null) {
                            String[] d = s.split("-");
                            specs.add(new Case<>(
                                    parseFormatWhen(d, 1),
                                    null
                            ));
                        }
                        if (valid && specs.isEmpty()) {
                            errorat("At least one selector required");
                            valid = false;
                        }
                        if (valid) {
                            // set the format of the specified whens
                            specs.stream()
                                    .forEach(c -> m.setWhens(format, c.e1));
                        }
                        break;
                    }
                    case NAME: {
                        m.setName(format);
                        break;
                    }
                    case TYPE: {
                        m.setType(format);
                        break;
                    }
                    case RESULT: {
                        m.setResult(format);
                        break;
                    }
                    case PRE: {
                        m.setPre(format);
                        break;
                    }
                    case POST: {
                        m.setPost(format);
                        break;
                    }
                    case ERRORPRE: {
                        m.setErrorPre(format);
                        break;
                    }
                    case ERRORPOST: {
                        m.setErrorPost(format);
                        break;
                    }
                }
            }
            if (!valid) {
                fluff("See '/help /set field' for help");
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
                    errorat("Matchs more then one current feedback mode: %s", umode);
                }
                fluff("The feedback mode should be one of the following:");
                modeMap.keySet().stream()
                        .forEach(mk -> fluff("   %s", mk));
                fluff("You may also use just enough letters to make it unique.");
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

        final Set<FormatCase> parseFormatCase(String[] s, int i) {
            return parseFormatSelectorStar(s, i, FormatCase.class, EnumSet.allOf(FormatCase.class), "case");
        }

        final Set<FormatAction> parseFormatAction(String[] s, int i) {
            return parseFormatSelectorStar(s, i, FormatAction.class,
                    EnumSet.of(FormatAction.ADDED, FormatAction.MODIFIED, FormatAction.REPLACED), "action");
        }

        final Set<FormatResolve> parseFormatResolve(String[] s, int i) {
            return parseFormatSelectorStar(s, i, FormatResolve.class,
                    EnumSet.of(FormatResolve.DEFINED, FormatResolve.NOTDEFINED), "resolve");
        }

        final Set<FormatWhen> parseFormatWhen(String[] s, int i) {
            return parseFormatSelectorStar(s, i, FormatWhen.class, EnumSet.of(FormatWhen.PRIMARY), "when");
        }

        /**
         * In a selector x-y-z , parse x, y, or z -- whether they are missing,
         * or a comma separated list of identifiers and stars.
         *
         * @param <E> The enum this selector should belong to
         * @param sa The array of selector strings
         * @param i The index of which selector string to use
         * @param klass The class of the enum that should be used
         * @param defaults The set of enum values to use if the selector is
         * missing
         * @return The set of enum values specified by this selector
         */
        final <E extends Enum<E>> Set<E> parseFormatSelectorStar(String[] sa, int i, Class<E> klass, EnumSet<E> defaults, String label) {
            String s = sa.length > i
                    ? sa[i]
                    : null;
            if (s == null || s.isEmpty()) {
                return defaults;
            }
            Set<E> set = EnumSet.noneOf(klass);
            EnumSet<E> values = EnumSet.allOf(klass);
            for (String as : s.split(",")) {
                if (as.equals("*")) {
                    set.addAll(values);
                } else if (!as.isEmpty()) {
                    set.add(parseFormatSelector(as, values, label));
                }
            }
            return set;
        }

        /**
         * In a x-y-a,b selector, parse an x, y, a, or b -- that is an
         * identifier
         *
         * @param <E> The enum this selector should belong to
         * @param s The string to parse: x, y, or z
         * @param values The allowed of this enum
         * @return The enum value
         */
        final <E extends Enum<E>> E parseFormatSelector(String s, EnumSet<E> values, String label) {
            if (s == null) {
                valid = false;
                return null;
            }
            String u = s.toUpperCase(Locale.US);
            for (E c : values) {
                if (c.name().startsWith(u)) {
                    return c;
                }
            }

            errorat("Not a valid %s: %s, must be one of: %s", label, s,
                    values.stream().map(v -> v.name().toLowerCase(Locale.US)).collect(Collectors.joining(" ")));
            valid = false;
            return values.iterator().next();
        }

        final void printFormatHelp() {
            hard("Set the format for reporting a snippet event.");
            hard("");
            hard("/set format <mode> \"<format>\" <selector>...");
            hard("");
            hard("Where <mode> is the name of a previously defined feedback mode -- see '/help /set newmode'.");
            hard("Where <format> is a quoted string which will have these field substitutions:");
            hard("   {action}    == The action, e.g.: Added, Modified, Assigned, ...");
            hard("   {name}      == The name, e.g.: the variable name, ...");
            hard("   {type}      == The type name");
            hard("   {resolve}   == Unresolved info, e.g.: ', however, it cannot be invoked until'");
            hard("   {result}    == The result value");
            hard("   {when}      == The entered snippet or a resultant update");
            hard("   {pre}       == The feedback prefix");
            hard("   {post}      == The feedback postfix");
            hard("   {errorpre}  == The error prefix");
            hard("   {errorpost} == The error postfix");
            hard("Use '/set field' to set the format of these substitutions.");
            hard("Where <selector> is the context in which the format is applied.");
            hard("The structure of selector is: <case>[-<action>[-<when>]]");
            hard("Where each field component may be missing (indicating defaults),");
            hard("star (indicating all), or a comma separated list of field values.");
            hard("For case, the field values are:");
            hardEnums(EnumSet.allOf(FormatCase.class), ev -> ev.doc);
            hard("For action, the field values are:");
            hardEnums(EnumSet.allOf(FormatAction.class), ev -> ev.doc);
            hard("For when, the field values are:");
            hardEnums(EnumSet.allOf(FormatWhen.class), ev -> ev.doc);
            hard("");
            hard("Example:");
            hard("   /set format example '{pre}{action} variable {name}, reset to null{post}' varreset-*-update");
        }

        final void printFieldHelp() {
            hard("Set the format of a field substitution as used in '/set format'.");
            hard("");
            hard("/set field <mode> <field> \"<format>\" <selector>...");
            hard("");
            hard("Where <mode> is the name of a previously defined feedback mode -- see '/set newmode'.");
            hard("Where <field> is context-specific format to set, each with its own selector structure:");
            hard("   action    == The action. The selector: <action>-<when>.");
            hard("   name      == The name.  '%%s' is the name.  No selectors.");
            hard("   type      == The type name.  '%%s' is the type. No selectors.");
            hard("   resolve   == Unresolved info.  '%%s' is the unresolved list. The selector: <resolve>-<when>.");
            hard("   result    == The result value.  '%%s' is the result value. No selectors.");
            hard("   when      == The entered snippet or a resultant update. The selector: <when>");
            hard("   pre       == The feedback prefix. No selectors.");
            hard("   post      == The feedback postfix. No selectors.");
            hard("   errorpre  == The error prefix. No selectors.");
            hard("   errorpost == The error postfix. No selectors.");
            hard("Where <format> is a quoted string -- see the description specific to the field (above).");
            hard("Where <selector> is the context in which the format is applied (see above).");
            hard("For action, the field values are:");
            hardEnums(EnumSet.allOf(FormatAction.class), ev -> ev.doc);
            hard("For when, the field values are:");
            hardEnums(EnumSet.allOf(FormatWhen.class), ev -> ev.doc);
            hard("For resolve, the field values are:");
            hardEnums(EnumSet.allOf(FormatResolve.class), ev -> ev.doc);
            hard("");
            hard("Example:");
            hard("   /set field example resolve ' which cannot be invoked until%%s is declared' defined-update");
        }

        final void printFeedbackHelp() {
            hard("Set the feedback mode describing displayed feedback for entered snippets and commands.");
            hard("");
            hard("/set feedback <mode>");
            hard("");
            hard("Where <mode> is the name of a previously defined feedback mode.");
            hard("Currently defined feedback modes:");
            modeMap.keySet().stream()
                    .forEach(m -> hard("   %s", m));
            hard("User-defined modes can be added, see '/help /set newmode'");
        }

        final void printNewModeHelp() {
            hard("Create a user-defined feedback mode, optionally copying from an existing mode.");
            hard("");
            hard("/set newmode <new-mode> [command|quiet [<old-mode>]]");
            hard("");
            hard("Where <new-mode> is the name of a mode you wish to create.");
            hard("Where <old-mode> is the name of a previously defined feedback mode.");
            hard("If <old-mode> is present, its settings are copied to the new mode.");
            hard("'command' vs 'quiet' determines if informative/verifying command feedback is displayed.");
            hard("");
            hard("Once the new mode is created, use '/set format', '/set field', and '/set prompt' to configure it.");
            hard("Use '/set feedback' to use the new mode.");
        }

        final void printPromptHelp() {
            hard("Set the prompts.  Both the normal prompt and the continuation-prompt must be set.");
            hard("");
            hard("/set prompt <mode> \"<prompt>\" \"<continuation-propmt>\"");
            hard("");
            hard("Where <mode> is the name of a previously defined feedback mode.");
            hard("Where <prompt> and <continuation-propmt> are quoted strings printed as input promptds;");
            hard("Both may optionally contain '%%s' which will be substituted with the next snippet id --");
            hard("note that what is entered may not be assigned that id, for example it may be an error or command.");
            hard("The continuation-prompt is used on the second and subsequent lines of a multi-line snippet.");
        }
    }
}
