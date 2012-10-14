/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ListResourceBundle;
import java.util.Map;
import java.util.Set;

/**
 * Prepares new key names for Resources.java.
 * 6987827: security/util/Resources.java needs improvement
 *
 * Run inside jdk/src/share/classes:
 *
 *      java NewResourcesNames $(
 *          for a in $(find com/sun/security sun/security javax/security -type f); do
 *              egrep -q '(ResourcesMgr.getString|rb.getString)' $a && echo $a
 *          done)
 *
 * Before running this tool, run the following two commands to make sure there
 * are only these 2 types of calls into the resources:
 *      for a in `find com/sun/security sun/security javax/security -type f`; do
 *          cat $a | perl -ne 'print if /\bResourcesMgr\b/'; done |
 *          grep -v ResourcesMgr.getString
 *      for a in `find com/sun/security sun/security -type f`; do
 *          cat $a | perl -ne 'print if /\brb\b/'; done |
 *          grep -v rb.getString
 */
class NewResourcesNames {

    // Max length of normalized names
    static int MAXLEN = 127;

    static String[] resources = {
        "sun/security/tools/jarsigner/Resources.java",
        "sun/security/tools/keytool/Resources.java",
        "sun/security/tools/policytool/Resources.java",
        "sun/security/util/Resources.java",
        "sun/security/util/AuthResources.java",
    };

    public static void main(String[] args) throws Exception {

        // Load all names inside resources files
        Map<String,String> allnames = loadResources();

        // Modify the callers. There are two patterns:
        // 1. ResourcesMgr.getString("
        //    used by most JAAS codes
        // 2. rb.getString("
        //    used by tools
        Set<String> allfound = new HashSet<String>();
        for (String arg: args) {
            allfound.addAll(rewriteFile(arg, "ResourcesMgr.getString(\""));
            allfound.addAll(rewriteFile(arg, "rb.getString(\""));
        }

        // Special case 1: KeyTool's enum definition of commands and options
        allfound.addAll(keyToolEnums());

        // Special case 2: PolicyFile called this 4 times
        allfound.addAll(rewriteFile("sun/security/provider/PolicyFile.java",
                "ResourcesMgr.getString(POLICY+\""));

        // During the calls above, you can read sth like:
        //
        //      Working on com/sun/security/auth/PolicyParser.java
        //          GOOD  match is 17
        //
        // This means a " exists right after getString(. Sometimes you see
        //
        //      Working on sun/security/tools/keytool/Main.java
        //          BAD!! pmatch != match: 212 != 209
        //      Working on sun/security/provider/PolicyFile.java
        //          BAD!! pmatch != match: 14 != 10
        //
        // which is mismatch. There are only two such special cases list above.
        // For KeyTool, there are 3 calls for showing help. For PolicyTool, 3
        // for name prefixed with POLICY. They are covered in the two special
        // cases above.

        // Names used but not defined. This is mostly error, except for
        // special case 2 above. So it's OK to see 3 entries red here
        if (!allnames.keySet().containsAll(allfound)) {
            err("FATAL: Undefined names");
            for (String name: allfound) {
                if (!allnames.keySet().contains(name)) {
                    err("   " + name);
                }
            }
        }

        // Names defined but not used. Mostly this is old entries not removed.
        // When soemone remove a line of code, he dares not remove the entry
        // in case it's also used somewhere else.
        if (!allfound.containsAll(allnames.keySet())) {
            System.err.println("WARNING: Unused names");
            for (String name: allnames.keySet()) {
                if (!allfound.contains(name)) {
                    System.err.println(allnames.get(name));
                    System.err.println("  " + normalize(name));
                    System.err.println("  [" + name + "]");
                }
            }
        }
    }


    /**
     * Loads the three resources files. Saves names into a Map.
     */
    private static Map<String,String> loadResources() throws Exception {

        // Name vs Resource
        Map<String,String> allnames = new HashMap<String,String>();

        for (String f: resources) {
            String clazz =
                    f.replace('/', '.').substring(0, f.length()-5);

            Set<String> expected = loadClass(clazz);
            Set<String> found = rewriteFile(f, "{\"");

            // This is to check that word parsing is identical to Java thinks
            if (!expected.equals(found)) {
                throw new Exception("Expected and found do not match");
            }

            for (String name: found) {
                allnames.put(name, f);
            }
        }
        return allnames;
    }

    /**
     * Special case treat for enums description in KeyTool
     */
    private static Set<String> keyToolEnums() throws Exception {

        Set<String> names = new HashSet<String>();

        String file = "sun/security/tools/keytool/Main.java";
        System.err.println("Working on " + file);
        File origFile = new File(file);
        File tmpFile = new File(file + ".tmp");
        origFile.renameTo(tmpFile);
        tmpFile.deleteOnExit();

        BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(tmpFile)));
        PrintWriter out = new PrintWriter(new FileOutputStream(origFile));

        int stage = 0;  // 1. commands, 2. options, 3. finished
        int match = 0;

        while (true) {
            String s = br.readLine();
            if (s == null) {
                break;
            }
            if (s.indexOf("enum Command") >= 0) stage = 1;
            else if (s.indexOf("enum Option") >= 0) stage = 2;
            else if (s.indexOf("private static final String JKS") >= 0) stage = 3;

            if (stage == 1 || stage == 2) {
                if (s.indexOf("(\"") >= 0) {
                    match++;
                    int p1, p2;
                    if (stage == 1) {
                        p1 = s.indexOf("\"");
                        p2 = s.indexOf("\"", p1+1);
                    } else {
                        p2 = s.lastIndexOf("\"");
                        p1 = s.lastIndexOf("\"", p2-1);
                    }
                    String name = s.substring(p1+1, p2);
                    names.add(name);
                    out.println(s.substring(0, p1+1) +
                            normalize(name) +
                            s.substring(p2));
                } else {
                    out.println(s);
                }
            } else {
                out.println(s);
            }
        }
        br.close();
        out.close();
        System.err.println("    GOOD  match is " + match);
        return names;
    }

    /**
     * Loads a resources using JRE and returns the names
     */
    private static Set<String> loadClass(String clazz) throws Exception {
        ListResourceBundle lrb =
                (ListResourceBundle)Class.forName(clazz).newInstance();
        Set<String> keys = lrb.keySet();
        Map<String,String> newold = new HashMap<String,String>();
        boolean dup = false;
        // Check if normalize() creates dup entries. This is crucial.
        for (String k: keys) {
            String key = normalize(k);
            if (newold.containsKey(key)) {
                err("Dup found for " + key + ":");
                err("["+newold.get(key)+"]");
                err("["+k+"]");
                dup = true;
            }
            newold.put(key, k);
        }
        if (dup) throw new Exception();
        return keys;
    }

    /**
     * Rewrites a file using a pattern. The name string should be right after
     * the pattern. Note: pattern ignores whitespaces. Returns names found.
     */
    private static Set<String> rewriteFile(String file, String pattern)
            throws Exception {

        System.err.println("Working on " + file);
        Set<String> names = new HashSet<String>();

        int plen = pattern.length();
        int match = 0;

        // The bare XXX.getString is also matched. Sometimes getString is
        // called but does not use literal strings. This is harder to solve.

        int pmatch = 0;
        int pheadlen = plen - 2;
        String phead = pattern.substring(0, plen-2);

        // The non-whitespace chars read since, used to check for pattern
        StringBuilder history = new StringBuilder();
        int hlen = 0;

        File origFile = new File(file);
        File tmpFile = new File(file + ".tmp");
        origFile.renameTo(tmpFile);
        tmpFile.deleteOnExit();

        FileInputStream fis = new FileInputStream(tmpFile);
        FileOutputStream fos = new FileOutputStream(origFile);

        while (true) {
            int ch = fis.read();
            if (ch < 0) break;
            if (!Character.isWhitespace(ch)) {
                history.append((char)ch);
                hlen++;
                if (pheadlen > 0 && hlen >= pheadlen &&
                        history.substring(hlen-pheadlen, hlen).equals(phead)) {
                    pmatch++;
                }
            }

            if (hlen >= plen &&
                    history.substring(hlen-plen, hlen).equals(pattern)) {
                match++;
                history = new StringBuilder();
                hlen = 0;

                fos.write(ch);

                // Save a name
                StringBuilder sb = new StringBuilder();
                // Save things after the second ". Maybe it's an end, maybe
                // it's just literal string concatenation.
                StringBuilder tail = new StringBuilder();

                boolean in = true;  // inside name string
                while (true) {
                    int n = fis.read();
                    if (in) {
                        if (n == '\\') {
                            int second = fis.read();
                            switch (second) {
                                case 'n': sb.append('\n'); break;
                                case 'r': sb.append('\r'); break;
                                case 't': sb.append('\t'); break;
                                case '"': sb.append('"'); break;
                                default: throw new Exception(String.format(
                                        "I don't know this escape: %s%c",
                                        sb.toString(), second));
                            }
                        } else if (n == '"') {
                            in = false;
                            // Maybe string concat? say bytes until clear
                            tail = new StringBuilder();
                            tail.append('"');
                        } else {
                            sb.append((char)n);
                        }
                    } else {
                        tail.append((char)n);
                        if (n == '"') { // string concat, in again
                            in = true;
                        } else if (n == ',' || n == ')') {  // real end
                            break;
                        } else if (Character.isWhitespace(n) || n == '+') {
                            // string concat
                        } else {
                            throw new Exception("Not a correct concat");
                        }
                    }
                }
                String s = sb.toString();
                names.add(s);
                fos.write(normalize(s).getBytes());
                fos.write(tail.toString().getBytes());
            } else {
                fos.write(ch);
            }
        }

        // Check pheadlen > 0. Don't want to mess with rewrite for resources
        if (pheadlen > 0 && pmatch != match) {
            err("    BAD!! pmatch != match: " + pmatch + " != " + match);
        } else {
            System.err.println("    GOOD  match is " + match);
        }

        fis.close();
        fos.close();
        return names;
    }

    /**
     * Normalize a string. Rules:
     *
     * 1. If all spacebar return "nSPACE", n is count
     * 2. If consisting at least one alphanumeric:
     *   a. All alphanumeric remain
     *   b. All others in a row goes to a single ".", even if at head or tail
     * 3. Otherwise:
     *   a. "****\n\n" to "STARNN", special case
     *   b. the English name if first char in *,.\n():'"
     *
     * Current observations show there's no dup, Hurray! Otherwise, add more
     * special cases.
     */
    private static String normalize(String s) throws Exception {
        boolean needDot = false;

        // All spacebar case
        int n = 0;
        for (char c: s.toCharArray()) {
            if (c == ' ') n++;
            else n = -10000;
        }
        if (n == 1) return "SPACE";
        else if (n > 1) return "" + n + "SPACE";

        StringBuilder sb = new StringBuilder();
        int dotpos = -1;
        for (int i=0; i<s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c) || Character.isDigit(c) ||
                    c == '{' || c == '}') {
                if (needDot) {
                    // Rememeber the last dot, we want shorter form nice
                    if (sb.length() <= MAXLEN) dotpos = sb.length();
                    // "." only added when an alphanumeric is seen. This makes
                    // sure sb is empty when there's no alphanumerics at all
                    sb.append(".");
                }
                sb.append(c);
                needDot = false;
            } else {
                needDot = true;
            }
        }

        // No alphanemeric?
        if (sb.length() == 0) {
            if (s.contains("*") && s.contains("\n")) {
                return "STARNN";
            }
            for (char c: s.toCharArray()) {
                switch (c) {
                    case '*': return "STAR";
                    case ',': return "COMMA";
                    case '.': return "PERIOD";
                    case '\n': return "NEWLINE";
                    case '(': return "LPARAM";
                    case ')': return "RPARAM";
                    case ':': return "COLON";
                    case '\'': case '"': return "QUOTE";
                }
            }
            throw new Exception("Unnamed char: [" + s + "]");
        }

        // tail "." only added when there are alphanumerics
        if (needDot) sb.append('.');
        String res = sb.toString();
        if (res.length() > MAXLEN) {
            if (dotpos < 0) throw new Exception("No dot all over? " + s);
            return res.substring(0, dotpos);
        } else {
            return res;
        }
    }

    private static void err(String string) {
        System.out.println("\u001b[1;37;41m" + string + "\u001b[m");
    }
}
