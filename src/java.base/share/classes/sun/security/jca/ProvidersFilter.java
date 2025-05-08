/*
 * Copyright (c) 2025, Red Hat, Inc.
 *
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

package sun.security.jca;

import java.io.Closeable;
import java.nio.CharBuffer;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import jdk.internal.access.JavaSecurityProviderAccess;
import jdk.internal.access.SharedSecrets;
import sun.security.util.Debug;
import sun.security.util.SecurityProperties;

public final class ProvidersFilter {

    private static final String FILTER_PROP = "jdk.security.providers.filter";

    private static final Debug debug = Debug.getInstance("jca",
            "ProvidersFilter");

    private static final JavaSecurityProviderAccess jspa = SharedSecrets
            .getJavaSecurityProviderAccess();

    private static final class FilterDecision {
        private enum Result {
            DENY,
            ALLOW,
            UNDECIDED
        }
        private static final int UNDEFINED_PRIORITY = -1;
        private static final FilterDecision UNDECIDED = new FilterDecision();
        private final Result result;
        private final int priority;

        private FilterDecision() {
            this.result = Result.UNDECIDED;
            this.priority = UNDEFINED_PRIORITY;
        }

        FilterDecision(Result result, int priority) {
            assert result != Result.UNDECIDED : "Invalid result.";
            assert priority >= 0 : "Invalid priority";
            this.result = result;
            this.priority = priority;
        }

        boolean isAllow() {
            return result == ProvidersFilter.FilterDecision.Result.ALLOW;
        }

        @Override
        public String toString() {
            return result + (priority != UNDEFINED_PRIORITY ? " - priority: " +
                    priority : "");
        }

        void debugDisplay() {
            if (debug == null) {
                return;
            }
            debug.println(" * Decision: " + this);
        }
    }

    private record FilterQuery(String provider, String svcType,
            String svcAlgo) {
        private FilterQuery {
            assert provider != null && svcType != null && svcAlgo != null :
                    "Invalid FilterQuery.";
        }

        @Override
        public String toString() {
            return "Service filter query (Provider: " + provider +
                    ", Service type: " + svcType + ", Algorithm: " +
                    svcAlgo + ")";
        }
    }

    private static final class Filter {
        private sealed interface Rule permits PatternRule, DefaultRule {
            FilterDecision apply(FilterQuery q);
        }

        private record PatternRuleComponent(Type type, String value,
                Pattern regexp) {
            enum Type {
                PROVIDER("Provider"),
                SVC_TYPE("Service type"),
                SVC_ALGO("Algorithm");

                private final String type;

                Type(String type) {
                    this.type = type;
                }

                @Override
                public String toString() {
                    return type;
                }
            }

            private static final Pattern ALL_PATTERN = Pattern.compile(".*");

            static final PatternRuleComponent ANY_SVC_TYPE =
                    new PatternRuleComponent(Type.SVC_TYPE, "*", ALL_PATTERN);

            static final PatternRuleComponent ANY_SVC_ALGO =
                    new PatternRuleComponent(Type.SVC_ALGO, "*", ALL_PATTERN);

            PatternRuleComponent {
                assert value != null && !value.isEmpty() && regexp != null :
                        "Invalid PatternRuleComponent instance.";
            }

            @Override
            public String toString() {
                return value;
            }

            void debugDisplay() {
                if (debug == null) {
                    return;
                }
                debug.println(" * " + type + ": " + value + " (regexp: " +
                        regexp + ")");
            }
        }

        private static final class PatternRule implements Rule {
            private FilterDecision decision;
            private PatternRuleComponent provider;
            private PatternRuleComponent svcType;
            private PatternRuleComponent svcAlgo;

            @Override
            public FilterDecision apply(FilterQuery q) {
                assert assertIsValid();
                if (provider.regexp.matcher(q.provider).matches() &&
                        svcType.regexp.matcher(q.svcType).matches() &&
                        svcAlgo.regexp.matcher(q.svcAlgo).matches()) {
                    return decision;
                }
                return FilterDecision.UNDECIDED;
            }

            private boolean assertIsValid() {
                assert decision.result != FilterDecision.Result.UNDECIDED :
                        "Invalid decision result.";
                assert decision.priority != FilterDecision.UNDEFINED_PRIORITY :
                        "Invalid decision priority.";
                assert provider != null : "Invalid provider.";
                assert svcType != null : "Invalid service type.";
                assert svcAlgo != null : "Invalid algorithm.";
                return true;
            }

            @Override
            public String toString() {
                return (decision.result == FilterDecision.Result.DENY ? "!" :
                        "") + provider + "." + svcType + "." + svcAlgo;
            }

            void debugDisplay() {
                if (debug == null) {
                    return;
                }
                provider.debugDisplay();
                svcType.debugDisplay();
                svcAlgo.debugDisplay();
                decision.debugDisplay();
            }
        }

        private static final class DefaultRule implements Rule {
            private final FilterDecision d;

            DefaultRule(int priority) {
                d = new FilterDecision(FilterDecision.Result.DENY, priority);
            }

            @Override
            public FilterDecision apply(FilterQuery q) {
                return d;
            }

            @Override
            public String toString() {
                return "!* (DEFAULT)";
            }
        }

        private static final class ParserException extends Exception {
            @java.io.Serial
            private static final long serialVersionUID = -6981287318167654426L;

            private static final String LN = System.lineSeparator();

            private static final String HEADER_STR = " * Filter string: ";

            private static final String MORE_STR = "(...)";

            private static final int MORE_TOTAL = MORE_STR.length() + 1;

            private static final int MAX_MARK = 7;

            private static final int MAX_LINE = 80;

            static {
                assert MAX_LINE >= HEADER_STR.length() + (MORE_TOTAL * 2) + 1
                        : "Not enough line space.";
            }

            private static String addStateInfo(String message, Parser parser) {
                StringBuilder sb = new StringBuilder();
                sb.append(message);
                sb.append(LN);
                sb.append(" * State: ");
                sb.append(parser.state);
                sb.append(LN);
                renderFilterStr(parser.filterBuff.asReadOnlyBuffer(), sb);
                return sb.toString();
            }

            private static void renderFilterStr(CharBuffer filterBuff,
                    StringBuilder sb) {
                int filterBuffLen = filterBuff.limit();
                int cursor = filterBuff.position() - 1;
                int preCutMark, postCutMark;
                int lineAvailable = MAX_LINE - HEADER_STR.length() - 1;
                int preAvailable = lineAvailable / 2;
                int postAvailable = (lineAvailable + 1) / 2;
                boolean preMore = false, postMore = false;
                int preCursor, preSpaceCount, preDashCount, postDashCount;

                // Calculate the filter line
                if (preAvailable < cursor) {
                    preMore = true;
                    preAvailable -= MORE_TOTAL;
                }
                if (postAvailable + cursor + 1 < filterBuffLen) {
                    postMore = true;
                    postAvailable -= MORE_TOTAL;
                }
                preCutMark = Math.max(0, cursor - preAvailable);
                preAvailable -= cursor - preCutMark;
                postCutMark = Math.min(filterBuffLen, cursor + 1 +
                        postAvailable);
                postAvailable -= postCutMark - (cursor + 1);
                if (postAvailable > 0 && preMore) {
                    if (preCutMark - (postAvailable + MORE_TOTAL) <= 0) {
                        postAvailable += MORE_TOTAL;
                        preMore = false;
                    }
                    preCutMark = Math.max(0, preCutMark - postAvailable);
                }
                if (preAvailable > 0 && postMore) {
                    if (postCutMark + preAvailable + MORE_TOTAL >=
                            filterBuffLen) {
                        preAvailable += MORE_TOTAL;
                        postMore = false;
                    }
                    postCutMark = Math.min(filterBuffLen, postCutMark +
                            preAvailable);
                }

                // Calculate the underlining line
                preCursor = HEADER_STR.length() + (preMore ? MORE_TOTAL : 0) +
                        cursor - preCutMark;
                preSpaceCount = Math.max(0, preCursor - MAX_MARK/2);
                preDashCount = Math.min(preCursor, MAX_MARK/2);
                postDashCount = Math.min(MAX_LINE - 1 - preSpaceCount -
                        preDashCount, MAX_MARK/2);

                // Render the filter line
                sb.append(HEADER_STR);
                if (preMore) {
                    sb.append(MORE_STR);
                    sb.append(' ');
                }
                filterBuff.position(0);
                sb.append(filterBuff, preCutMark, postCutMark);
                if (postMore) {
                    sb.append(' ');
                    sb.append(MORE_STR);
                }
                sb.append(LN);

                // Render the underlining line
                sb.append(" ".repeat(preSpaceCount));
                sb.append("-".repeat(preDashCount));
                sb.append("^");
                sb.append("-".repeat(postDashCount));
                sb.append(LN);
            }

            ParserException(String message, Parser parser) {
                super(addStateInfo(message, parser));
            }
        }

        private static final class Parser {
            private enum ParsingState {
                PRE_PATTERN,
                PRE_PATTERN_DENY,
                PATTERN,
                POST_PATTERN
            }

            private enum Transition {
                WHITESPACE_CHAR,
                DENY_CHAR,
                REGULAR_CHAR,
                PATTERN_LEVEL_CHAR,
                PATTERN_END_CHAR
            }

            static List<Rule> parse(String filterStr) throws ParserException {
                return new Parser(filterStr).getRules();
            }

            private final CharBuffer filterBuff;
            private final List<Rule> rules;
            private PatternRule rule;
            private ParsingState state;
            private final StringBuffer buff;
            private final StringBuffer buffR;
            private boolean escape;
            private boolean quote;

            private Parser(String filterStr) throws ParserException {
                filterBuff = CharBuffer.wrap(filterStr);
                rules = new ArrayList<>();
                rule = new PatternRule();
                state = ParsingState.PRE_PATTERN;
                buff = new StringBuffer();
                buffR = new StringBuffer();
                escape = false;
                quote = false;
                parse();
            }

            private List<Rule> getRules() {
                return rules;
            }

            private PatternRuleComponent getComponent(
                    PatternRuleComponent.Type type) throws ParserException {
                if (buff.isEmpty()) {
                    throw new ParserException("Missing " +
                            type.toString().toLowerCase() + " in " +
                            "pattern rule.", this);
                }
                if (quote) {
                    buffR.append("\\E");
                    quote = false;
                }
                return new PatternRuleComponent(type, buff.toString(),
                        Pattern.compile(buffR.toString(),
                                Pattern.CASE_INSENSITIVE));
            }

            private void flushBuffers() throws ParserException {
                if (rule.provider == null) {
                    rule.provider = getComponent(
                            PatternRuleComponent.Type.PROVIDER);
                } else if (rule.svcType == null) {
                    rule.svcType = getComponent(
                            PatternRuleComponent.Type.SVC_TYPE);
                } else if (rule.svcAlgo == null) {
                    rule.svcAlgo = getComponent(
                            PatternRuleComponent.Type.SVC_ALGO);
                } else {
                    assert false : "Should not reach.";
                }
                buff.setLength(0);
                buffR.setLength(0);
            }

            private void endPattern() throws ParserException {
                if (escape) {
                    throw new ParserException("Invalid escaping.", this);
                }
                flushBuffers();
                if (rule.svcType == null) {
                    rule.svcType = PatternRuleComponent.ANY_SVC_TYPE;
                }
                if (rule.svcAlgo == null) {
                    rule.svcAlgo = PatternRuleComponent.ANY_SVC_ALGO;
                }
                if (debug != null) {
                    debug.println("--------------------");
                    debug.println("Rule parsed: " + rule);
                    rule.debugDisplay();
                }
                rules.add(rule);
                rule = new PatternRule();
            }

            /*
             * Transition to the next state if there is a valid reason. If the
             * reason is not valid, throw an exception. If there are no reasons
             * to transition, stay in the same state.
             */
            private void nextState(Transition transition)
                    throws ParserException {
                if (state == ParsingState.PRE_PATTERN) {
                    if (transition == Transition.WHITESPACE_CHAR) {
                        // Stay in PRE_PATTERN state and ignore whitespaces
                        // at the beginning of a pattern:
                        //
                        // "    Provider.ServiceType.Algorithm;"
                        //  ^^^^
                        //
                        // or
                        //
                        // "    !    Provider.ServiceType.Algorithm;"
                        //  ^^^^
                    } else if (transition == Transition.REGULAR_CHAR) {
                        // Transition to PATTERN state:
                        //
                        // "   Provider.ServiceType.Algorithm;"
                        //     ^^^^
                        state = ParsingState.PATTERN;
                        rule.decision = new FilterDecision(
                                FilterDecision.Result.ALLOW, rules.size());
                    } else if (transition == Transition.DENY_CHAR) {
                        // Transition to PRE_PATTERN_DENY state:
                        //
                        // "   !    Provider.ServiceType.Algorithm;"
                        //      ^^^^
                        state = ParsingState.PRE_PATTERN_DENY;
                        rule.decision = new FilterDecision(
                                FilterDecision.Result.DENY, rules.size());
                    } else {
                        throw new ParserException("A pattern must start with " +
                                "a '!' or a security provider name.", this);
                    }
                } else if (state == ParsingState.PRE_PATTERN_DENY) {
                    if (transition == Transition.WHITESPACE_CHAR) {
                        // Stay in PRE_PATTERN_DENY state and ignore whitespaces
                        // before the provider:
                        //
                        // "   !    Provider.ServiceType.Algorithm;"
                        //      ^^^^
                    } else if (transition == Transition.REGULAR_CHAR) {
                        // Transition to PATTERN state:
                        //
                        // "   !    Provider.ServiceType.Algorithm;"
                        //          ^^^^
                        state = ParsingState.PATTERN;
                    } else {
                        throw new ParserException("A pattern must have a " +
                                "security provider name after '!'.", this);
                    }
                } else if (state == ParsingState.PATTERN) {
                    if (transition == Transition.REGULAR_CHAR) {
                        // Stay in PATTERN while the provider, service type
                        // and algorithm names fill up:
                        //
                        // "   Provider.ServiceType.Algorithm;"
                        //     ^^^^
                    } else if (transition == Transition.WHITESPACE_CHAR) {
                        // Transition to POST_PATTERN state, after recording
                        // the parsed rule:
                        //
                        // "   Provider.ServiceType.Algorithm    ;"
                        //                                   ^^^^
                        endPattern();
                        state = ParsingState.POST_PATTERN;
                    } else if (transition == Transition.PATTERN_END_CHAR) {
                        // Transition to PRE_PATTERN state, after recording
                        // the parsed rule:
                        //
                        // "   Provider.ServiceType.Algorithm;    Provider..."
                        //                                  ^^^
                        endPattern();
                        state = ParsingState.PRE_PATTERN;
                    } else if (transition == Transition.PATTERN_LEVEL_CHAR) {
                        // Stay in PATTERN state while recording characters
                        // for the next level (service type or algorithm):
                        //
                        // "    Provider.ServiceType.Algorithm;"
                        //               ^^^^
                        if (rule.svcType != null) {
                            throw new ParserException("Too many levels. Dots " +
                                    "that are part of a provider name, " +
                                    "service type or algorithm must be " +
                                    "escaped.", this);
                        }
                        flushBuffers();
                    } else {
                        throw new ParserException("Invalid name in pattern.",
                                this);
                    }
                } else if (state == ParsingState.POST_PATTERN) {
                    if (transition == Transition.WHITESPACE_CHAR) {
                        // Stay in POST_PATTERN state and ignore whitespaces
                        // until the end of the pattern:
                        //
                        // "    Provider.ServiceType.Algorithm    ;    Provider"
                        //                                    ^^^^
                    } else if (transition == Transition.PATTERN_END_CHAR) {
                        // Transition to PRE_PATTERN state:
                        //
                        // "    Provider.ServiceType.Algorithm    ;    Provider"
                        //                                       ^^^
                        state = ParsingState.PRE_PATTERN;
                    } else {
                        throw new ParserException("Unescaped whitespaces are " +
                                "only valid at the end of a pattern. " +
                                "Whitespace characters internal to a " +
                                "provider name, service type or algorithm " +
                                "must be escaped.", this);
                    }
                } else {
                    // Should not reach.
                    throw new RuntimeException("Unexpected Providers filter " +
                            "parser state.");
                }
            }

            private void appendChar(char c) {
                if (c == '*' && !escape) {
                    // Character is a wildcard.
                    if (quote) {
                        buffR.append("\\E");
                        quote = false;
                    }
                    buffR.append(".*");
                } else {
                    // Character is not a wildcard.
                    if (escape) {
                        buff.append("\\");
                    }
                    if (!quote) {
                        buffR.append("\\Q");
                        quote = true;
                    }
                    buffR.append(c);
                    if (c == '\\') {
                        // A '\' could be problematic because if an 'E' comes
                        // next the sequence "\E" would interfere with regexp
                        // quoting. Split these sequences into separated
                        // quoting units. I.e: "...\\E\QE...".
                        buffR.append("\\E\\Q");
                    }
                }
                buff.append(c);
            }

            private void parse() throws ParserException {
                if (debug != null) {
                    debug.println("Parsing: " + filterBuff);
                }
                assert filterBuff.hasRemaining() : "Cannot parse an empty " +
                        "filter.";
                while (filterBuff.hasRemaining()) {
                    char c = filterBuff.get();
                    if (c == '\n' || c == '\0') {
                        throw new ParserException("Invalid filter character: " +
                                "'" + c + "'", this);
                    } else if (escape) {
                        appendChar(c);
                        escape = false;
                    } else if (c == '\\') {
                        nextState(Transition.REGULAR_CHAR);
                        escape = true;
                    } else if (c == '.') {
                        nextState(Transition.PATTERN_LEVEL_CHAR);
                    } else if (c == ';') {
                        nextState(Transition.PATTERN_END_CHAR);
                    } else if (Character.isWhitespace(c)) {
                        nextState(Transition.WHITESPACE_CHAR);
                    } else if (c == '!') {
                        nextState(Transition.DENY_CHAR);
                    } else if (c == ':' || c == ',') {
                        throw new ParserException("Reserved character '" + c +
                                "' must be escaped.", this);
                    } else {
                        nextState(Transition.REGULAR_CHAR);
                        appendChar(c);
                    }
                }
                if (state != ParsingState.PRE_PATTERN || rules.size() == 0) {
                    nextState(Transition.PATTERN_END_CHAR);
                }
                assert state == ParsingState.PRE_PATTERN : "Parser state " +
                        "must finish in PRE_PATTERN.";
            }
        }

        private final List<Rule> rules;

        Filter(String filterStr) throws IllegalArgumentException {
            try {
                rules = Parser.parse(filterStr);
            } catch (ParserException e) {
                throw new IllegalArgumentException("Invalid Providers filter:" +
                        " " + filterStr, e);
            }
            rules.add(new DefaultRule(rules.size()));
        }

        FilterDecision apply(FilterQuery q) {
            for (Rule r : rules) {
                FilterDecision d = r.apply(q);
                if (d != FilterDecision.UNDECIDED) {
                    if (debug != null) {
                        debug.println("--------------------");
                        debug.println(q.toString());
                        debug.println(" * Decision: " + d);
                        debug.println(" * Made by: " + r);
                    }
                    return d;
                }
            }
            // Should never reach this point: there is always a DefaultRule
            // capable of deciding.
            throw new RuntimeException("Unexpected Providers filter failure: " +
                    "decision not made.");
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Filter: ");
            Iterator<Rule> ri = rules.iterator();
            while (ri.hasNext()) {
                sb.append(ri.next());
                if (ri.hasNext()) {
                    sb.append("; ");
                }
            }
            return sb.toString();
        }
    }

    private static final Filter filter;

    static {
        Filter tmpFilter = null;
        String fStr = SecurityProperties.getOverridableProperty(FILTER_PROP);
        if (debug != null) {
            debug.println("Filter property value read at this point:");
            for (StackTraceElement ste : new Exception().getStackTrace()) {
                debug.println(" ".repeat(4) + ste);
            }
        }
        if (fStr != null && !fStr.isEmpty()) {
            tmpFilter = new Filter(fStr);
        }
        filter = tmpFilter;
        if (debug != null) {
            debug.println(filter != null ? filter.toString() : "No filter");
        }
    }

    /*
     * This method has to be called every time that a Provider.Service instance
     * is obtained with Provider::getService or Provider::getServices.
     */
    public static boolean isAllowed(Provider.Service svc) {
        if (filter == null) {
            return true;
        }
        // For services added to the Provider's services map (most cases), this
        // call is expected to be fast: only a Provider.Service field read. It
        // might take longer on the first time for uncommon services (see
        // Provider.Service::isAllowed).
        return jspa.isAllowed(svc);
    }

    /*
     * This method is called from Provider.Service::computeSvcAllowed and
     * Provider.Service::isTransformationAllowed.
     */
    public static boolean computeSvcAllowed(String providerName,
            String svcType, String algo, List<String> aliases) {
        if (filter == null) {
            return true;
        }
        FilterDecision d = isAllowed(providerName, svcType, algo);
        if (debug != null && aliases.size() > 0) {
            debug.println("--------------------");
            debug.println("The queried service has aliases. Checking them " +
                    "for a final decision...");
        }
        for (String algAlias : aliases) {
            FilterDecision da = isAllowed(providerName, svcType, algAlias);
            if (da.priority < d.priority) {
                d = da;
                if (debug != null) {
                    algo = algAlias;
                }
            }
        }
        if (debug != null && aliases.size() > 0) {
            debug.println("--------------------");
            debug.println("Final decision based on " + algo + " algorithm" +
                    ": " + d);
        }
        return d.isAllow();
    }

    private static FilterDecision isAllowed(String provider, String svcType,
            String svcAlgo) {
        return filter.apply(new FilterQuery(provider, svcType, svcAlgo));
    }

    /*
     * CipherContext is an auxiliary class to bundle information required by
     * CipherTransformation. The field "transformation" is the ongoing Cipher
     * transformation for which a service is being looked up. The field
     * "svcSearchKey" is the key (algorithm or alias) used to look up a
     * service that might support the transformation.
     */
    public record CipherContext(String transformation, String svcSearchKey) {}

    /*
     * CipherTransformation is used from the Cipher::tryGetService,
     * Cipher::newInstance and ProviderList.CipherServiceIterator::tryGetService
     * methods for a thread to indicate that a service will be looked up for a
     * Cipher transformation. In these cases, the service evaluation against
     * the Providers Filter is based on the transformation and not the service
     * algorithm or aliases. Thus, a Filter value such as
     * "*.Cipher.AES/ECB/PKCS5Padding; !*" would allow
     * Cipher.getInstance("AES/ECB/PKCS5Padding") but block
     * Cipher.getInstance("AES") even when the supporting service is the same.
     */
    public static final class CipherTransformation implements Closeable {
        private static final ThreadLocal<CipherContext> cipherTransformContext =
                new ThreadLocal<>();
        private CipherContext prevContext;

        public CipherTransformation(String transformation,
                String svcSearchKey) {
            if (filter == null) {
                return;
            }
            prevContext = cipherTransformContext.get();
            if (!transformation.equalsIgnoreCase(svcSearchKey)) {
                cipherTransformContext.set(new CipherContext(
                        transformation.toUpperCase(Locale.ENGLISH),
                        svcSearchKey));
            } else {
                // The transformation matches the service algorithm or alias.
                // Set the context to null to indicate that a regular service
                // evaluation (not based on the transformation) should be done.
                cipherTransformContext.set(null);
            }
        }

        /*
         * This method is called from Provider.Service::isAllowed for a thread
         * to get the CipherContext related to a service lookup. Returns
         * null if 1) there is not an ongoing service lookup based on a Cipher
         * transformation or 2) the transformation matches the service
         * algorithm or any of its aliases. A regular service evaluation (not
         * based on the transformation) should be done if null is returned.
         */
        public static CipherContext getContext() {
            if (filter == null) {
                return null;
            }
            return cipherTransformContext.get();
        }

        @Override
        public void close() {
            if (filter == null) {
                return;
            }
            cipherTransformContext.set(prevContext);
        }
    }
}
