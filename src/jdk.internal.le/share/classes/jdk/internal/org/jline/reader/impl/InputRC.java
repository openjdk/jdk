/*
 * Copyright (c) 2002-2023, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.reader.Macro;
import jdk.internal.org.jline.reader.Reference;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.utils.Log;

public final class InputRC {

    public static void configure(LineReader reader, URL url) throws IOException {
        try (InputStream is = url.openStream()) {
            configure(reader, is);
        }
    }

    public static void configure(LineReader reader, InputStream is) throws IOException {
        try (InputStreamReader r = new InputStreamReader(is)) {
            configure(reader, r);
        }
    }

    public static void configure(LineReader reader, Reader r) throws IOException {
        BufferedReader br;
        if (r instanceof BufferedReader) {
            br = (BufferedReader) r;
        } else {
            br = new BufferedReader(r);
        }

        Terminal terminal = reader.getTerminal();

        if (Terminal.TYPE_DUMB.equals(terminal.getType()) || Terminal.TYPE_DUMB_COLOR.equals(terminal.getType())) {
            reader.getVariables().putIfAbsent(LineReader.EDITING_MODE, "dumb");
        } else {
            reader.getVariables().putIfAbsent(LineReader.EDITING_MODE, "emacs");
        }

        reader.setKeyMap(LineReader.MAIN);
        new InputRC(reader).parse(br);
        if ("vi".equals(reader.getVariable(LineReader.EDITING_MODE))) {
            reader.getKeyMaps().put(LineReader.MAIN, reader.getKeyMaps().get(LineReader.VIINS));
        } else if ("emacs".equals(reader.getVariable(LineReader.EDITING_MODE))) {
            reader.getKeyMaps().put(LineReader.MAIN, reader.getKeyMaps().get(LineReader.EMACS));
        } else if ("dumb".equals(reader.getVariable(LineReader.EDITING_MODE))) {
            reader.getKeyMaps().put(LineReader.MAIN, reader.getKeyMaps().get(LineReader.DUMB));
        }
    }

    private final LineReader reader;

    private InputRC(LineReader reader) {
        this.reader = reader;
    }

    private void parse(BufferedReader br) throws IOException, IllegalArgumentException {
        String line;
        boolean parsing = true;
        List<Boolean> ifsStack = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            try {
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }
                if (line.charAt(0) == '#') {
                    continue;
                }
                int i = 0;
                if (line.charAt(i) == '$') {
                    String cmd;
                    String args;
                    ++i;
                    while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
                        i++;
                    }
                    int s = i;
                    while (i < line.length() && (line.charAt(i) != ' ' && line.charAt(i) != '\t')) {
                        i++;
                    }
                    cmd = line.substring(s, i);
                    while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
                        i++;
                    }
                    s = i;
                    while (i < line.length() && (line.charAt(i) != ' ' && line.charAt(i) != '\t')) {
                        i++;
                    }
                    args = line.substring(s, i);
                    if ("if".equalsIgnoreCase(cmd)) {
                        ifsStack.add(parsing);
                        if (!parsing) {
                            continue;
                        }
                        if (args.startsWith("term=")) {
                            // TODO
                        } else if (args.startsWith("mode=")) {
                            String mode = (String) reader.getVariable(LineReader.EDITING_MODE);
                            parsing = args.substring("mode=".length()).equalsIgnoreCase(mode);
                        } else {
                            parsing = args.equalsIgnoreCase(reader.getAppName());
                        }
                    } else if ("else".equalsIgnoreCase(cmd)) {
                        if (ifsStack.isEmpty()) {
                            throw new IllegalArgumentException("$else found without matching $if");
                        }
                        boolean invert = true;
                        for (boolean b : ifsStack) {
                            if (!b) {
                                invert = false;
                                break;
                            }
                        }
                        if (invert) {
                            parsing = !parsing;
                        }
                    } else if ("endif".equalsIgnoreCase(cmd)) {
                        if (ifsStack.isEmpty()) {
                            throw new IllegalArgumentException("endif found without matching $if");
                        }
                        parsing = ifsStack.remove(ifsStack.size() - 1);
                    } else if ("include".equalsIgnoreCase(cmd)) {
                        // TODO
                    }
                    continue;
                }
                if (!parsing) {
                    continue;
                }
                if (line.charAt(i++) == '"') {
                    boolean esc = false;
                    for (; ; i++) {
                        if (i >= line.length()) {
                            throw new IllegalArgumentException("Missing closing quote on line '" + line + "'");
                        }
                        if (esc) {
                            esc = false;
                        } else if (line.charAt(i) == '\\') {
                            esc = true;
                        } else if (line.charAt(i) == '"') {
                            break;
                        }
                    }
                }
                while (i < line.length() && line.charAt(i) != ':' && line.charAt(i) != ' ' && line.charAt(i) != '\t') {
                    i++;
                }
                String keySeq = line.substring(0, i);
                boolean equivalency = i + 1 < line.length() && line.charAt(i) == ':' && line.charAt(i + 1) == '=';
                i++;
                if (equivalency) {
                    i++;
                }
                if (keySeq.equalsIgnoreCase("set")) {
                    String key;
                    String val;
                    while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
                        i++;
                    }
                    int s = i;
                    while (i < line.length() && (line.charAt(i) != ' ' && line.charAt(i) != '\t')) {
                        i++;
                    }
                    key = line.substring(s, i);
                    while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
                        i++;
                    }
                    s = i;
                    while (i < line.length() && (line.charAt(i) != ' ' && line.charAt(i) != '\t')) {
                        i++;
                    }
                    val = line.substring(s, i);
                    setVar(reader, key, val);
                } else {
                    while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
                        i++;
                    }
                    int start = i;
                    if (i < line.length() && (line.charAt(i) == '\'' || line.charAt(i) == '\"')) {
                        char delim = line.charAt(i++);
                        boolean esc = false;
                        for (; ; i++) {
                            if (i >= line.length()) {
                                break;
                            }
                            if (esc) {
                                esc = false;
                            } else if (line.charAt(i) == '\\') {
                                esc = true;
                            } else if (line.charAt(i) == delim) {
                                break;
                            }
                        }
                    }
                    for (; i < line.length() && line.charAt(i) != ' ' && line.charAt(i) != '\t'; i++)
                        ;
                    String val = line.substring(Math.min(start, line.length()), Math.min(i, line.length()));
                    if (keySeq.charAt(0) == '"') {
                        keySeq = translateQuoted(keySeq);
                    } else {
                        // Bind key name
                        String keyName =
                                keySeq.lastIndexOf('-') > 0 ? keySeq.substring(keySeq.lastIndexOf('-') + 1) : keySeq;
                        char key = getKeyFromName(keyName);
                        keyName = keySeq.toLowerCase();
                        keySeq = "";
                        if (keyName.contains("meta-") || keyName.contains("m-")) {
                            keySeq += "\u001b";
                        }
                        if (keyName.contains("control-") || keyName.contains("c-") || keyName.contains("ctrl-")) {
                            key = (char) (Character.toUpperCase(key) & 0x1f);
                        }
                        keySeq += key;
                    }
                    if (val.length() > 0 && (val.charAt(0) == '\'' || val.charAt(0) == '\"')) {
                        reader.getKeys().bind(new Macro(translateQuoted(val)), keySeq);
                    } else {
                        reader.getKeys().bind(new Reference(val), keySeq);
                    }
                }
            } catch (IllegalArgumentException e) {
                Log.warn("Unable to parse user configuration: ", e);
            }
        }
    }

    private static String translateQuoted(String keySeq) {
        int i;
        String str = keySeq.substring(1, keySeq.length() - 1);
        StringBuilder sb = new StringBuilder();
        for (i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\') {
                boolean ctrl = str.regionMatches(i, "\\C-", 0, 3) || str.regionMatches(i, "\\M-\\C-", 0, 6);
                boolean meta = str.regionMatches(i, "\\M-", 0, 3) || str.regionMatches(i, "\\C-\\M-", 0, 6);
                i += (meta ? 3 : 0) + (ctrl ? 3 : 0) + (!meta && !ctrl ? 1 : 0);
                if (i >= str.length()) {
                    break;
                }
                c = str.charAt(i);
                if (meta) {
                    sb.append("\u001b");
                }
                if (ctrl) {
                    c = c == '?' ? 0x7f : (char) (Character.toUpperCase(c) & 0x1f);
                }
                if (!meta && !ctrl) {
                    switch (c) {
                        case 'a':
                            c = 0x07;
                            break;
                        case 'b':
                            c = '\b';
                            break;
                        case 'd':
                            c = 0x7f;
                            break;
                        case 'e':
                            c = 0x1b;
                            break;
                        case 'f':
                            c = '\f';
                            break;
                        case 'n':
                            c = '\n';
                            break;
                        case 'r':
                            c = '\r';
                            break;
                        case 't':
                            c = '\t';
                            break;
                        case 'v':
                            c = 0x0b;
                            break;
                        case '\\':
                            c = '\\';
                            break;
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                            c = 0;
                            for (int j = 0; j < 3; j++, i++) {
                                if (i >= str.length()) {
                                    break;
                                }
                                int k = Character.digit(str.charAt(i), 8);
                                if (k < 0) {
                                    break;
                                }
                                c = (char) (c * 8 + k);
                            }
                            c &= 0xFF;
                            break;
                        case 'x':
                            i++;
                            c = 0;
                            for (int j = 0; j < 2; j++, i++) {
                                if (i >= str.length()) {
                                    break;
                                }
                                int k = Character.digit(str.charAt(i), 16);
                                if (k < 0) {
                                    break;
                                }
                                c = (char) (c * 16 + k);
                            }
                            c &= 0xFF;
                            break;
                        case 'u':
                            i++;
                            c = 0;
                            for (int j = 0; j < 4; j++, i++) {
                                if (i >= str.length()) {
                                    break;
                                }
                                int k = Character.digit(str.charAt(i), 16);
                                if (k < 0) {
                                    break;
                                }
                                c = (char) (c * 16 + k);
                            }
                            break;
                    }
                }
                sb.append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static char getKeyFromName(String name) {
        if ("DEL".equalsIgnoreCase(name) || "Rubout".equalsIgnoreCase(name)) {
            return 0x7f;
        } else if ("ESC".equalsIgnoreCase(name) || "Escape".equalsIgnoreCase(name)) {
            return '\033';
        } else if ("LFD".equalsIgnoreCase(name) || "NewLine".equalsIgnoreCase(name)) {
            return '\n';
        } else if ("RET".equalsIgnoreCase(name) || "Return".equalsIgnoreCase(name)) {
            return '\r';
        } else if ("SPC".equalsIgnoreCase(name) || "Space".equalsIgnoreCase(name)) {
            return ' ';
        } else if ("Tab".equalsIgnoreCase(name)) {
            return '\t';
        } else {
            return name.charAt(0);
        }
    }

    static void setVar(LineReader reader, String key, String val) {
        if (LineReader.KEYMAP.equalsIgnoreCase(key)) {
            reader.setKeyMap(val);
            return;
        }

        for (LineReader.Option option : LineReader.Option.values()) {
            if (option.name().toLowerCase(Locale.ENGLISH).replace('_', '-').equals(val)) {
                if ("on".equalsIgnoreCase(val)) {
                    reader.setOpt(option);
                } else if ("off".equalsIgnoreCase(val)) {
                    reader.unsetOpt(option);
                }
                return;
            }
        }

        reader.setVariable(key, val);
    }
}
