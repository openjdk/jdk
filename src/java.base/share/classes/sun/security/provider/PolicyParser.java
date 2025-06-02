/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import sun.security.util.PropertyExpander;
import sun.security.util.LocalizedMessage;

/**
 * Configuration data that specifies the keystores in a DKS keystore.
 * A keystore domain is a collection of keystores that are presented as a
 * single logical keystore. The configuration data is used during KeyStore
 * load and store operations.
 *
 * The following syntax is supported for configuration data:
 *
 *     domain <domainName> [<property> ...] {
 *        keystore <keystoreName> [<property> ...] ;
 *        ...
 *    };
 *    ...
 *
 * @author Roland Schemers
 * @author Ram Marti
 *
 * @since 1.2
 */

public class PolicyParser {

    private Map<String, DomainEntry> domainEntries;

    private StreamTokenizer st;
    private int lookahead;
    private boolean expandProp = false;

    private String expand(String value)
        throws PropertyExpander.ExpandException
    {
        return expand(value, false);
    }

    private String expand(String value, boolean encodeURL)
        throws PropertyExpander.ExpandException
    {
        if (!expandProp) {
            return value;
        } else {
            return PropertyExpander.expand(value, encodeURL);
        }
    }

    /**
     * Creates a PolicyParser object.
     */
    public PolicyParser(boolean expandProp) {
        this.expandProp = expandProp;
    }

    /**
     * Reads a policy configuration into the Policy object using a
     * Reader object. <p>
     *
     * @param policy the policy Reader object.
     *
     * @exception ParsingException if the policy configuration contains
     *          a syntax error.
     *
     * @exception IOException if an error occurs while reading the policy
     *          configuration.
     */
    public void read(Reader policy)
        throws ParsingException, IOException
    {
        if (!(policy instanceof BufferedReader)) {
            policy = new BufferedReader(policy);
        }

        /*
         * Configure the stream tokenizer:
         *      Recognize strings between "..."
         *      Don't convert words to lowercase
         *      Recognize both C-style and C++-style comments
         *      Treat end-of-line as white space, not as a token
         */
        st   = new StreamTokenizer(policy);

        st.resetSyntax();
        st.wordChars('a', 'z');
        st.wordChars('A', 'Z');
        st.wordChars('.', '.');
        st.wordChars('0', '9');
        st.wordChars('_', '_');
        st.wordChars('$', '$');
        st.wordChars(128 + 32, 255);
        st.whitespaceChars(0, ' ');
        st.commentChar('/');
        st.quoteChar('\'');
        st.quoteChar('"');
        st.lowerCaseMode(false);
        st.ordinaryChar('/');
        st.slashSlashComments(true);
        st.slashStarComments(true);

        /*
         * The main parsing loop.  The loop is executed once
         * for each entry in the config file. The entries
         * are delimited by semicolons.
         *
         */

        lookahead = st.nextToken();
        while (lookahead != StreamTokenizer.TT_EOF) {
            if (peek("domain")) {
                if (domainEntries == null) {
                    domainEntries = new TreeMap<>();
                }
                DomainEntry de = parseDomainEntry();
                String domainName = de.getName();
                if (domainEntries.putIfAbsent(domainName, de) != null) {
                    LocalizedMessage localizedMsg = new LocalizedMessage(
                        "duplicate.keystore.domain.name");
                    Object[] source = {domainName};
                    String msg = "duplicate keystore domain name: " +
                                 domainName;
                    throw new ParsingException(msg, localizedMsg, source);
                }
            } else {
                // error?
            }
            match(";");
        }
    }

    public Collection<DomainEntry> getDomainEntries() {
        return domainEntries.values();
    }

    /**
     * parse a domain entry
     */
    private DomainEntry parseDomainEntry()
        throws ParsingException, IOException
    {
        DomainEntry domainEntry;
        String name;
        Map<String, String> properties = new HashMap<>();

        match("domain");
        name = match("domain name");

        while(!peek("{")) {
            // get the domain properties
            properties = parseProperties("{");
        }
        match("{");
        domainEntry = new DomainEntry(name, properties);

        while(!peek("}")) {

            match("keystore");
            name = match("keystore name");
            // get the keystore properties
            if (!peek("}")) {
                properties = parseProperties(";");
            }
            match(";");
            domainEntry.add(new KeyStoreEntry(name, properties));
        }
        match("}");

        return domainEntry;
    }

    /*
     * Return a collection of domain properties or keystore properties.
     */
    private Map<String, String> parseProperties(String terminator)
        throws ParsingException, IOException {

        Map<String, String> properties = new HashMap<>();
        String key;
        String value;
        while (!peek(terminator)) {
            key = match("property name");
            match("=");

            try {
                value = expand(match("quoted string"));
            } catch (PropertyExpander.ExpandException peee) {
                throw new IOException(peee.getLocalizedMessage());
            }
            properties.put(key.toLowerCase(Locale.ENGLISH), value);
        }

        return properties;
    }

    private boolean peekAndMatch(String expect)
        throws ParsingException, IOException
    {
        if (peek(expect)) {
            match(expect);
            return true;
        } else {
            return false;
        }
    }

    private boolean peek(String expect) {
        boolean found = false;

        switch (lookahead) {

        case StreamTokenizer.TT_WORD:
            if (expect.equalsIgnoreCase(st.sval))
                found = true;
            break;
        case ',':
            if (expect.equalsIgnoreCase(","))
                found = true;
            break;
        case '{':
            if (expect.equalsIgnoreCase("{"))
                found = true;
            break;
        case '}':
            if (expect.equalsIgnoreCase("}"))
                found = true;
            break;
        case '"':
            if (expect.equalsIgnoreCase("\""))
                found = true;
            break;
        case '*':
            if (expect.equalsIgnoreCase("*"))
                found = true;
            break;
        case ';':
            if (expect.equalsIgnoreCase(";"))
                found = true;
            break;
        default:

        }
        return found;
    }

    private String match(String expect)
        throws ParsingException, IOException
    {
        String value = null;

        switch (lookahead) {
        case StreamTokenizer.TT_NUMBER:
            throw new ParsingException(st.lineno(), expect,
                LocalizedMessage.getNonlocalized("number.") + st.nval);
        case StreamTokenizer.TT_EOF:
            LocalizedMessage localizedMsg = new LocalizedMessage
                ("expected.expect.read.end.of.file.");
            Object[] source = {expect};
            String msg = "expected [" + expect + "], read [end of file]";
            throw new ParsingException(msg, localizedMsg, source);
        case StreamTokenizer.TT_WORD:
            if (expect.equalsIgnoreCase(st.sval)) {
                lookahead = st.nextToken();
            } else if (expect.equalsIgnoreCase("domain name") ||
                       expect.equalsIgnoreCase("keystore name") ||
                       expect.equalsIgnoreCase("property name")) {
                value = st.sval;
                lookahead = st.nextToken();
            } else {
                 throw new ParsingException(st.lineno(), expect,
                                            st.sval);
            }
            break;
        case '"':
            if (expect.equalsIgnoreCase("quoted string")) {
                value = st.sval;
                lookahead = st.nextToken();
            } else {
                throw new ParsingException(st.lineno(), expect, st.sval);
            }
            break;
        case ',':
            if (expect.equalsIgnoreCase(","))
                lookahead = st.nextToken();
            else
                throw new ParsingException(st.lineno(), expect, ",");
            break;
        case '{':
            if (expect.equalsIgnoreCase("{"))
                lookahead = st.nextToken();
            else
                throw new ParsingException(st.lineno(), expect, "{");
            break;
        case '}':
            if (expect.equalsIgnoreCase("}"))
                lookahead = st.nextToken();
            else
                throw new ParsingException(st.lineno(), expect, "}");
            break;
        case ';':
            if (expect.equalsIgnoreCase(";"))
                lookahead = st.nextToken();
            else
                throw new ParsingException(st.lineno(), expect, ";");
            break;
        case '*':
            if (expect.equalsIgnoreCase("*"))
                lookahead = st.nextToken();
            else
                throw new ParsingException(st.lineno(), expect, "*");
            break;
        case '=':
            if (expect.equalsIgnoreCase("="))
                lookahead = st.nextToken();
            else
                throw new ParsingException(st.lineno(), expect, "=");
            break;
        default:
            throw new ParsingException(st.lineno(), expect,
                               String.valueOf((char)lookahead));
        }
        return value;
    }

    /**
     * skip all tokens for this entry leaving the delimiter ";"
     * in the stream.
     */
    private void skipEntry() throws ParsingException, IOException {
        while(lookahead != ';') {
            switch (lookahead) {
            case StreamTokenizer.TT_NUMBER:
                throw new ParsingException(st.lineno(), ";",
                        LocalizedMessage.getNonlocalized("number.") + st.nval);
            case StreamTokenizer.TT_EOF:
                throw new ParsingException(LocalizedMessage.getNonlocalized
                        ("expected.read.end.of.file."));
            default:
                lookahead = st.nextToken();
            }
        }
    }

    /**
     * Each domain entry in the keystore domain configuration file is
     * represented by a DomainEntry object.
     */
    static class DomainEntry {
        private final String name;
        private final Map<String, String> properties;
        private final Map<String, KeyStoreEntry> entries;

        DomainEntry(String name, Map<String, String> properties) {
            this.name = name;
            this.properties = properties;
            entries = new HashMap<>();
        }

        String getName() {
            return name;
        }

        Map<String, String> getProperties() {
            return properties;
        }

        Collection<KeyStoreEntry> getEntries() {
            return entries.values();
        }

        void add(KeyStoreEntry entry) throws ParsingException {
            String keystoreName = entry.getName();
            if (!entries.containsKey(keystoreName)) {
                entries.put(keystoreName, entry);
            } else {
                LocalizedMessage localizedMsg = new LocalizedMessage
                    ("duplicate.keystore.name");
                Object[] source = {keystoreName};
                String msg = "duplicate keystore name: " + keystoreName;
                throw new ParsingException(msg, localizedMsg, source);
            }
        }

        @Override
        public String toString() {
            StringBuilder s =
                new StringBuilder("\ndomain ").append(name);

            if (properties != null) {
                for (Map.Entry<String, String> property :
                    properties.entrySet()) {
                    s.append("\n        ").append(property.getKey()).append('=')
                        .append(property.getValue());
                }
            }
            s.append(" {\n");

            for (KeyStoreEntry entry : entries.values()) {
                s.append(entry).append("\n");
            }
            s.append("}");

            return s.toString();
        }
    }

    /**
     * Each keystore entry in the keystore domain configuration file is
     * represented by a KeyStoreEntry object.
     */

    static class KeyStoreEntry {
        private final String name;
        private final Map<String, String> properties;

        KeyStoreEntry(String name, Map<String, String> properties) {
            this.name = name;
            this.properties = properties;
        }

        String getName() {
            return name;
        }

        Map<String, String>  getProperties() {
            return properties;
        }

        @Override
        public String toString() {
            StringBuilder s = new StringBuilder("\n    keystore ").append(name);
            if (properties != null) {
                for (Map.Entry<String, String> property :
                    properties.entrySet()) {
                    s.append("\n        ").append(property.getKey()).append('=')
                        .append(property.getValue());
                }
            }
            s.append(";");

            return s.toString();
        }
    }

    public static class ParsingException extends GeneralSecurityException {

        @java.io.Serial
        private static final long serialVersionUID = -4330692689482574072L;

        private String i18nMessage;
        @SuppressWarnings("serial") // Not statically typed as Serializable
        private LocalizedMessage localizedMsg;
        @SuppressWarnings("serial") // Not statically typed as Serializable
        private Object[] source;

        /**
         * Constructs a ParsingException with the specified
         * detail message. A detail message is a String that describes
         * this particular exception, which may, for example, specify which
         * algorithm is not available.
         *
         * @param msg the detail message.
         */
        public ParsingException(String msg) {
            super(msg);
            i18nMessage = msg;
        }

        public ParsingException(String msg, LocalizedMessage localizedMsg,
                                Object[] source) {
            super(msg);
            this.localizedMsg = localizedMsg;
            this.source = source;
        }

        public ParsingException(int line, String msg) {
            super("line " + line + ": " + msg);
            localizedMsg = new LocalizedMessage("line.number.msg");
            source = new Object[] {line, msg};
        }

        public ParsingException(int line, String expect, String actual) {
            super("line " + line + ": expected [" + expect +
                "], found [" + actual + "]");
            localizedMsg = new LocalizedMessage
                ("line.number.expected.expect.found.actual.");
            source = new Object[] {line, expect, actual};
        }

        public String getNonlocalizedMessage() {
            return i18nMessage != null ? i18nMessage :
                localizedMsg.formatNonlocalized(source);
        }
    }
}
