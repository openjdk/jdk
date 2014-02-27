/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.hasher;

import java.io.*;
import java.util.*;


/**
 * Reads a map in the form of a sequence of key/value-expression pairs from the
 * standard input, attempts to construct a hash map that fits within the given
 * table-size and chain-depth constraints, and, if successful, writes source
 * code to the standard output for a subclass of sun.util.PreHashedMap that
 * implements the map.
 *
 * @see sun.util.PreHashedMap
 *
 * @author Mark Reinhold
 */

public class Hasher {

    static final PrintStream out = System.out;
    static final PrintStream err = System.err;

    boolean verbose = false;

    List<String> keys = new ArrayList<>();      // Key strings
    List<String> values = new ArrayList<>();    // Value expressions
    String pkg = null;                          // Package prefix for generated class
    String cln = null;                          // Name of generated class
    String vtype = "String";                    // Value type
    int maxBits = 11;                           // lg table size
    int maxDepth = 3;                           // Max chain depth
    boolean inner = false;                      // Generating an inner class?
    boolean empty = false;                      // Generating an empty table?

    void usage() {
        err.println("usage: java Hasher [options] [[pkgName.]ClassName]");
        err.println("options:");
        err.println("    -e           generate empty table (ignores value exprs)");
        err.println("    -i           generate a static inner class");
        err.println("    -md depth    max chain depth (default 3)");
        err.println("    -mb bits     max index bits (lg of table size, default 10)");
        err.println("    -t type      value type (default String)");
        err.println("    -v           verbose");
        err.println("Key/value-expression pairs are read from standard input");
        err.println("If class name is given then source is written to standard output");
        System.exit(1);
    }

    Hasher(String[] args) {
        List<String> as = Arrays.asList(args);
        for (Iterator<String> i = as.iterator(); i.hasNext();) {
            String a = i.next();
            if (a.equals("-e")) {
                empty = true;
            } else if (a.equals("-i")) {
                inner = true;
            } else if (a.equals("-v")) {
                verbose = true;
            } else if (a.equals("-md")) {
                if (!i.hasNext())
                    usage();
                maxDepth = Integer.parseInt(i.next());
            } else if (a.equals("-mb")) {
                if (!i.hasNext())
                    usage();
                maxBits = Integer.parseInt(i.next());
            } else if (a.equals("-t")) {
                if (!i.hasNext())
                    usage();
                vtype = i.next();
            } else if (a.startsWith("-")) {
                usage();
            } else {
                int j = a.lastIndexOf('.');
                if (j >= 0) {
                    pkg = a.substring(0, j);
                    cln = a.substring(j + 1);
                } else {
                    cln = a;
                }
            }
        }
        if (verbose)
            err.println("pkg=" + pkg + ", cln=" + cln);
    }

    // Read keys and values
    //
    Hasher load() throws IOException {
        BufferedReader br
            = new BufferedReader(new InputStreamReader(System.in));
        for (String ln; (ln = br.readLine()) != null;) {
            String[] ws = ln.split(",?\\s+", 2);
            String w = ws[0].replaceAll("\"", "");
            if (keys.contains(w))
                throw new RuntimeException("Duplicate word in input: " + w);
            keys.add(w);
            if (ws.length < 2)
                throw new RuntimeException("Missing expression for key " + w);
            values.add(ws[1]);
        }
        return this;
    }

    Object[] ht;                        // Hash table itself
    int nb;                             // Number of bits (lg table size)
    int md;                             // Maximum chain depth
    int mask;                           // Hash-code mask
    int shift;                          // Hash-code shift size

    int hash(String w) {
        return (w.hashCode() >> shift) & mask;
    }

    // Build a hash table of size 2^nb, shifting the hash code by s bits
    //
    void build(int nb, int s) {

        this.nb = nb;
        this.shift = s;
        int n = 1 << nb;
        this.mask = n - 1;
        ht = new Object[n];
        int nw = keys.size();

        for (int i = 0; i < nw; i++) {
            String w = keys.get(i);
            String v = values.get(i);
            int h = hash(w);
            if (ht[h] == null)
                ht[h] = new Object[] { w, v };
            else
                ht[h] = new Object[] { w, v, ht[h] };
        }

        this.md = 0;
        for (int i = 0; i < n; i++) {
            int d = 1;
            for (Object[] a = (Object[])ht[i];
                 a != null && a.length > 2;
                 a = (Object[])a[2], d++);
            this.md = Math.max(md, d);
        }

    }

    Hasher build() {
        // Iterate through acceptable table sizes
        for (int nb = 2; nb < maxBits; nb++) {
            // Iterate through possible shift sizes
            for (int s = 0; s < (32 - nb); s++) {
                build(nb, s);
                if (verbose)
                    err.println("nb=" + nb + " s=" + s + " md=" + md);
                if (md <= maxDepth) {
                    // Success
                    out.flush();
                    if (verbose) {
                        if (cln != null)
                            err.print(cln + ": ");
                        err.println("Table size " + (1 << nb) + " (" + nb + " bits)"
                                    + ", shift " + shift
                                    + ", max chain depth " + md);
                    }
                    return this;
                }
            }
        }
        throw new RuntimeException("Cannot find a suitable size"
                                   + " within given constraints");
    }

    // Look for the given key in the hash table
    //
    String get(String k) {
        int h = hash(k);
        Object[] a = (Object[])ht[h];
        for (;;) {
            if (a[0].equals(k))
                return (String)a[1];
            if (a.length < 3)
                return null;
            a = (Object[])a[2];
        }
    }

    // Test that all input keys can be found in the table
    //
    Hasher test() {
        if (verbose)
            err.println();
        for (int i = 0, n = keys.size(); i < n; i++) {
            String w = keys.get(i);
            String v = get(w);
            if (verbose)
                err.println(hash(w) + "\t" + w);
            if (!v.equals(values.get(i)))
                throw new Error("Incorrect value: " + w + " --> "
                                + v + ", should be " + values.get(i));
        }
        return this;
    }

    String ind = "";                    // Indent prefix

    // Generate code for a single table entry
    //
    void genEntry(Object[] a, int depth, PrintWriter pw) {
        Object v = empty ? null : a[1];
        pw.print("new Object[] { \"" + a[0] + "\", " + v);
        if (a.length < 3) {
            pw.print(" }");
            return;
        }
        pw.println(",");
        pw.print(ind + "                     ");
        for (int i = 0; i < depth; i++)
            pw.print("    ");
        genEntry((Object[])a[2], depth + 1, pw);
        pw.print(" }");
    }

    // Generate a PreHashedMap subclass from the computed hash table
    //
    Hasher generate() throws IOException {
        if (cln == null)
            return this;
        PrintWriter pw
            = new PrintWriter(new BufferedWriter(new OutputStreamWriter(out)));

        if (inner)
            ind = "    ";

        if (!inner && pkg != null) {
            pw.println();
            pw.println("package " + pkg + ";");
            pw.println();
        }

        if (inner) {
            pw.println(ind + "private static final class " + cln);
        } else {
            pw.println();
            pw.println("public final class " + cln);
        }
        pw.println(ind + "    extends sun.util.PreHashedMap<" + vtype +">");
        pw.println(ind + "{");

        pw.println();
        pw.println(ind + "    private static final int ROWS = "
                   + ht.length + ";");
        pw.println(ind + "    private static final int SIZE = "
                   + keys.size() + ";");
        pw.println(ind + "    private static final int SHIFT = "
                   + shift + ";");
        pw.println(ind + "    private static final int MASK = 0x"
                   + Integer.toHexString(mask) + ";");
        pw.println();

        pw.println(ind + "    " + (inner ? "private " : "public ")
                   + cln + "() {");
        pw.println(ind + "        super(ROWS, SIZE, SHIFT, MASK);");
        pw.println(ind + "    }");
        pw.println();

        pw.println(ind + "    protected void init(Object[] ht) {");
        for (int i = 0; i < ht.length; i++) {
            if (ht[i] == null)
                continue;
            Object[] a = (Object[])ht[i];
            pw.print(ind + "        ht[" + i + "] = ");
            genEntry(a, 0, pw);
            pw.println(";");
        }
        pw.println(ind + "    }");
        pw.println();

        pw.println(ind + "}");
        if (inner)
            pw.println();

        pw.close();
        return this;
    }

    public static void main(String[] args) throws IOException {
        new Hasher(args)
            .load()
            .build()
            .test()
            .generate();
    }

}
