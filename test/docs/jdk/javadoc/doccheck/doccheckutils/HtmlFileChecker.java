/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package doccheckutils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads an HTML file, and calls a series of{@link HtmlChecker HTML checkers}
 * for the HTML constructs found therein.
 */
public class HtmlFileChecker implements FileChecker {
    private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.IGNORE)
            .onUnmappableCharacter(CodingErrorAction.IGNORE);

    private final Log log;
    private final HtmlChecker htmlChecker;
    private Path path;
    private BufferedReader in;
    private int ch;
    private int lineNumber;
    private boolean inScript;
    private boolean xml;

    public HtmlFileChecker(HtmlChecker htmlChecker, Path BaseDir) {
        this.log = new Log();
        log.setBaseDirectory(BaseDir);
        this.htmlChecker = htmlChecker;
    }

    @Override
    public void checkFiles(List<Path> files) {
        for (Path file : files) {
            read(file);
        }
    }

    @Override
    public void report() {
        System.err.println(log);
    }

    @Override
    public void close() throws IOException {
//        report();
        htmlChecker.close();
    }

    private void read(Path path) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(Files.newInputStream(path), decoder))) {
            this.path = path;
            this.in = r;
            StringBuilder content = new StringBuilder();

            startFile(path);
            try {
                lineNumber = 1;
                xml = false;
                nextChar();

                while (ch != -1) {
                    if (ch == '<') {
                        content(content.toString());
                        content.setLength(0);
                        html();
                    } else {
                        content.append((char) ch);
                        if (ch == '\n') {
                            content(content.toString());
                            content.setLength(0);
                        }
                        nextChar();
                    }
                }
            } finally {
                endFile();
            }
        } catch (IOException e) {
            log.log(path, lineNumber, e);
        } catch (Throwable t) {
            log.log(path, lineNumber, t);
            log.log(String.valueOf(t));
        }
    }

    private void startFile(Path path) {
        htmlChecker.startFile(path);
    }

    private void endFile() {
        htmlChecker.endFile();
    }

    private void docType(String s) {
        htmlChecker.docType(lineNumber, s);
    }

    private void startElement(String name, Map<String, String> attrs, boolean selfClosing) {
        htmlChecker.startElement(lineNumber, name, attrs, selfClosing);
    }

    private void endElement(String name) {
        htmlChecker.endElement(lineNumber, name);
    }

    private void content(String s) {
        htmlChecker.content(lineNumber, s);
    }

    private void nextChar() throws IOException {
        ch = in.read();
        if (ch == '\n')
            lineNumber++;
    }

    /**
     * Read the start or end of an HTML tag, or an HTML comment
     * {@literal <identifier attrs> } or {@literal </identifier> }
     *
     * @throws IOException if there is a problem reading the file
     */
    protected void html() throws IOException {
        nextChar();
        if (isIdentifierStart((char) ch)) {
            String name = readIdentifier().toLowerCase(Locale.US);
            Map<String, String> attrs = htmlAttrs();
            if (attrs != null) {
                boolean selfClosing = false;
                if (ch == '/') {
                    nextChar();
                    selfClosing = true;
                }
                if (ch == '>') {
                    nextChar();
                    startElement(name, attrs, selfClosing);
                    if (name.equals("script")) {
                        inScript = true;
                    }
                    return;
                }
            }
        } else if (ch == '/') {
            nextChar();
            if (isIdentifierStart((char) ch)) {
                String name = readIdentifier().toLowerCase(Locale.US);
                skipWhitespace();
                if (ch == '>') {
                    nextChar();
                    endElement(name);
                    if (name.equals("script")) {
                        inScript = false;
                    }
                    return;
                }
            }
        } else if (ch == '!') {
            nextChar();
            if (ch == '-') {
                nextChar();
                if (ch == '-') {
                    nextChar();
                    while (ch != -1) {
                        int dash = 0;
                        while (ch == '-') {
                            dash++;
                            nextChar();
                        }
                        // Strictly speaking, a comment should not contain "--"
                        // so dash > 2 is an error, dash == 2 implies ch == '>'
                        // See http://www.w3.org/TR/html-markup/syntax.html#syntax-comments
                        // for more details.
                        if (dash >= 2 && ch == '>') {
                            nextChar();
                            return;
                        }

                        nextChar();
                    }
                }
            } else if (ch == '[') {
                nextChar();
                if (ch == 'C') {
                    nextChar();
                    if (ch == 'D') {
                        nextChar();
                        if (ch == 'A') {
                            nextChar();
                            if (ch == 'T') {
                                nextChar();
                                if (ch == 'A') {
                                    nextChar();
                                    if (ch == '[') {
                                        while (true) {
                                            nextChar();
                                            if (ch == ']') {
                                                nextChar();
                                                if (ch == ']') {
                                                    nextChar();
                                                    if (ch == '>') {
                                                        nextChar();
                                                        return;
                                                    }
                                                }
                                            }
                                        }

                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                StringBuilder sb = new StringBuilder();
                while (ch != -1 && ch != '>') {
                    sb.append((char) ch);
                    nextChar();
                }
                Pattern p = Pattern.compile("(?is)doctype\\s+html\\s?.*");
                String s = sb.toString();
                if (p.matcher(s).matches()) {
                    xml = s.contains("XHTML");
                    docType(s);
                    return;
                }
            }
        } else if (ch == '?') {
            nextChar();
            if (ch == 'x') {
                nextChar();
                if (ch == 'm') {
                    nextChar();
                    if (ch == 'l') {
                        nextChar();
                        if (ch == '?') {
                            nextChar();
                            if (ch == '>') {
                                nextChar();
                                xml = true;
                                return;
                            }
                        }
                    }
                }

            }
        }

        if (!inScript) {
            log.log(path, lineNumber, "bad html");
        }
    }

    /**
     * Read a series of HTML attributes, terminated by {@literal > }.
     * Each attribute is of the form {@literal identifier[=value] }.
     * "value" may be unquoted, single-quoted, or double-quoted.
     */
    protected Map<String, String> htmlAttrs() throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        skipWhitespace();

        while (isIdentifierStart((char) ch)) {
            String name = readAttributeName().toLowerCase(Locale.US);
            skipWhitespace();
            String value = null;
            if (ch == '=') {
                nextChar();
                skipWhitespace();
                if (ch == '\'' || ch == '"') {
                    char quote = (char) ch;
                    nextChar();
                    StringBuilder sb = new StringBuilder();
                    while (ch != -1 && ch != quote) {
//                            if (ch == '\n') {
//                                error(path, lineNumber, "unterminated string");
//                                // No point trying to read more.
//                                // In fact, all attrs get discarded by the caller
//                                // and superseded by a malformed.html node because
//                                // the html tag itself is not terminated correctly.
//                                break loop;
//                            }
                        sb.append((char) ch);
                        nextChar();
                    }
                    value = sb.toString() // hack to replace common entities
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&amp;", "&");
                    nextChar();
                } else {
                    StringBuilder sb = new StringBuilder();
                    while (ch != -1 && !isUnquotedAttrValueTerminator((char) ch)) {
                        sb.append((char) ch);
                        nextChar();
                    }
                    value = sb.toString();
                }
                skipWhitespace();
            }
            map.put(name, value);
        }

        return map;
    }

    protected boolean isIdentifierStart(char ch) {
        return Character.isUnicodeIdentifierStart(ch);
    }

    protected String readIdentifier() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append((char) ch);
        nextChar();
        while (ch != -1 && Character.isUnicodeIdentifierPart(ch)) {
            sb.append((char) ch);
            nextChar();
        }
        return sb.toString();
    }

    protected String readAttributeName() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append((char) ch);
        nextChar();
        while ((ch != -1 && Character.isUnicodeIdentifierPart(ch))
                || ch == '-'
                || (xml && ch == ':')) {
            sb.append((char) ch);
            nextChar();
        }
        return sb.toString();
    }

    protected boolean isWhitespace(char ch) {
        return Character.isWhitespace(ch);
    }

    protected void skipWhitespace() throws IOException {
        while (isWhitespace((char) ch)) {
            nextChar();
        }
    }

    protected boolean isUnquotedAttrValueTerminator(char ch) {
        return switch (ch) {
            case '\f', '\n', '\r', '\t', ' ', '"', '\'', '`', '=', '<', '>' -> true;
            default -> false;
        };
    }

    @Override
    public boolean isOK() {
        throw new UnsupportedOperationException();
    }
}
