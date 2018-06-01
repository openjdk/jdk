/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.console;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.internal.jline.internal.Log;

/**
 * @author St\u00E5le W. Pedersen <stale.pedersen@jboss.org>
 */
public class ConsoleKeys {

    private KeyMap keys;

    private Map<String, KeyMap> keyMaps;
    private Map<String, String> variables = new HashMap<String,String>();

    public ConsoleKeys(String appName, URL inputrcUrl) {
        keyMaps = KeyMap.keyMaps();
        setVar("editing-mode", "emacs");
        loadKeys(appName, inputrcUrl);
        String editingMode = variables.get("editing-mode");
        if ("vi".equalsIgnoreCase(editingMode)) {
            keys = keyMaps.get(KeyMap.VI_INSERT);
        } else if ("emacs".equalsIgnoreCase(editingMode)) {
            keys = keyMaps.get(KeyMap.EMACS);
        }
    }

    protected boolean setKeyMap (String name) {
        KeyMap map = keyMaps.get(name);
        if (map == null) {
            return false;
        }
        this.keys = map;
        return true;
    }

    protected Map<String, KeyMap> getKeyMaps() {
        return keyMaps;
    }

    protected KeyMap getKeys() {
        return keys;
    }

    protected void setKeys(KeyMap keys) {
        this.keys = keys;
    }

    protected void loadKeys(String appName, URL inputrcUrl) {
        keys = keyMaps.get(KeyMap.EMACS);

        try {
            InputStream input = inputrcUrl.openStream();
            try {
                loadKeys(input, appName);
                Log.debug("Loaded user configuration: ", inputrcUrl);
            }
            finally {
                try {
                    input.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        catch (IOException e) {
            if (inputrcUrl.getProtocol().equals("file")) {
                File file = new File(inputrcUrl.getPath());
                if (file.exists()) {
                    Log.warn("Unable to read user configuration: ", inputrcUrl, e);
                }
            } else {
                Log.warn("Unable to read user configuration: ", inputrcUrl, e);
            }
        }
    }

    private void loadKeys(InputStream input, String appName) throws IOException {
        BufferedReader reader = new BufferedReader( new java.io.InputStreamReader( input ) );
        String line;
        boolean parsing = true;
        List<Boolean> ifsStack = new ArrayList<Boolean>();
        while ( (line = reader.readLine()) != null ) {
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
                    for (++i; i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t'); i++);
                    int s = i;
                    for (; i < line.length() && (line.charAt(i) != ' ' && line.charAt(i) != '\t'); i++);
                    cmd = line.substring(s, i);
                    for (; i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t'); i++);
                    s = i;
                    for (; i < line.length() && (line.charAt(i) != ' ' && line.charAt(i) != '\t'); i++);
                    args = line.substring(s, i);
                    if ("if".equalsIgnoreCase(cmd)) {
                        ifsStack.add( parsing );
                        if (!parsing) {
                            continue;
                        }
                        if (args.startsWith("term=")) {
                            // TODO
                        } else if (args.startsWith("mode=")) {
                            String mode = variables.get("editing-mode");
                            parsing = args.substring("mode=".length()).equalsIgnoreCase(mode);
                        } else {
                            parsing = args.equalsIgnoreCase(appName);
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
                        parsing = ifsStack.remove( ifsStack.size() - 1 );
                    } else if ("include".equalsIgnoreCase(cmd)) {
                        // TODO
                    }
                    continue;
                }
                if (!parsing) {
                    continue;
                }
                boolean equivalency;
                String keySeq = "";
                if (line.charAt(i++) == '"') {
                    boolean esc = false;
                    for (;; i++) {
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
                for (; i < line.length() && line.charAt(i) != ':'
                        && line.charAt(i) != ' ' && line.charAt(i) != '\t'
                        ; i++);
                keySeq = line.substring(0, i);
                equivalency = i + 1 < line.length() && line.charAt(i) == ':' && line.charAt(i + 1) == '=';
                i++;
                if (equivalency) {
                    i++;
                }
                if (keySeq.equalsIgnoreCase("set")) {
                    String key;
                    String val;
                    for (; i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t'); i++);
                    int s = i;
                    for (; i < line.length() && (line.charAt(i) != ' ' && line.charAt(i) != '\t'); i++);
                    key = line.substring( s, i );
                    for (; i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t'); i++);
                    s = i;
                    for (; i < line.length() && (line.charAt(i) != ' ' && line.charAt(i) != '\t'); i++);
                    val = line.substring( s, i );
                    setVar( key, val );
                } else {
                    for (; i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t'); i++);
                    int start = i;
                    if (i < line.length() && (line.charAt(i) == '\'' || line.charAt(i) == '\"')) {
                        char delim = line.charAt(i++);
                        boolean esc = false;
                        for (;; i++) {
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
                    for (; i < line.length() && line.charAt(i) != ' ' && line.charAt(i) != '\t'; i++);
                    String val = line.substring(Math.min(start, line.length()), Math.min(i, line.length()));
                    if (keySeq.charAt(0) == '"') {
                        keySeq = translateQuoted(keySeq);
                    } else {
                        // Bind key name
                        String keyName = keySeq.lastIndexOf('-') > 0 ? keySeq.substring( keySeq.lastIndexOf('-') + 1 ) : keySeq;
                        char key = getKeyFromName(keyName);
                        keyName = keySeq.toLowerCase();
                        keySeq = "";
                        if (keyName.contains("meta-") || keyName.contains("m-")) {
                            keySeq += "\u001b";
                        }
                        if (keyName.contains("control-") || keyName.contains("c-") || keyName.contains("ctrl-")) {
                            key = (char)(Character.toUpperCase( key ) & 0x1f);
                        }
                        keySeq += key;
                    }
                    if (val.length() > 0 && (val.charAt(0) == '\'' || val.charAt(0) == '\"')) {
                        keys.bind( keySeq, translateQuoted(val) );
                    } else {
                        String operationName = val.replace('-', '_').toUpperCase();
                        try {
                          keys.bind(keySeq, Operation.valueOf(operationName));
                        } catch(IllegalArgumentException e) {
                          Log.info("Unable to bind key for unsupported operation: ", val);
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
              Log.warn("Unable to parse user configuration: ", e);
            }
        }
    }

    private static String translateQuoted(String keySeq) {
        int i;
        String str = keySeq.substring( 1, keySeq.length() - 1 );
        keySeq = "";
        for (i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\') {
                boolean ctrl = str.regionMatches(i, "\\C-", 0, 3)|| str.regionMatches(i, "\\M-\\C-", 0, 6);
                boolean meta = str.regionMatches(i, "\\M-", 0, 3)|| str.regionMatches(i, "\\C-\\M-", 0, 6);
                i += (meta ? 3 : 0) + (ctrl ? 3 : 0) + (!meta && !ctrl ? 1 : 0);
                if (i >= str.length()) {
                    break;
                }
                c = str.charAt(i);
                if (meta) {
                    keySeq += "\u001b";
                }
                if (ctrl) {
                    c = c == '?' ? 0x7f : (char)(Character.toUpperCase( c ) & 0x1f);
                }
                if (!meta && !ctrl) {
                    switch (c) {
                        case 'a': c = 0x07; break;
                        case 'b': c = '\b'; break;
                        case 'd': c = 0x7f; break;
                        case 'e': c = 0x1b; break;
                        case 'f': c = '\f'; break;
                        case 'n': c = '\n'; break;
                        case 'r': c = '\r'; break;
                        case 't': c = '\t'; break;
                        case 'v': c = 0x0b; break;
                        case '\\': c = '\\'; break;
                        case '0': case '1': case '2': case '3':
                        case '4': case '5': case '6': case '7':
                            c = 0;
                            for (int j = 0; j < 3; j++, i++) {
                                if (i >= str.length()) {
                                    break;
                                }
                                int k = Character.digit(str.charAt(i), 8);
                                if (k < 0) {
                                    break;
                                }
                                c = (char)(c * 8 + k);
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
                                c = (char)(c * 16 + k);
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
                                c = (char)(c * 16 + k);
                            }
                            break;
                    }
                }
                keySeq += c;
            } else {
                keySeq += c;
            }
        }
        return keySeq;
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

    private void setVar(String key, String val) {
        if ("keymap".equalsIgnoreCase(key)) {
            if (keyMaps.containsKey(val)) {
                keys = keyMaps.get(val);
            }
        } else if ("blink-matching-paren".equals(key)) {
            if ("on".equalsIgnoreCase(val)) {
              keys.setBlinkMatchingParen(true);
            } else if ("off".equalsIgnoreCase(val)) {
              keys.setBlinkMatchingParen(false);
            }
        }

        /*
         * Technically variables should be defined as a functor class
         * so that validation on the variable value can be done at parse
         * time. This is a stop-gap.
         */
        variables.put(key, val);
    }

    /**
     * Retrieves the value of a variable that was set in the .inputrc file
     * during processing
     * @param var The variable name
     * @return The variable value.
     */
    public String getVariable(String var) {
        return variables.get (var);
    }
}
