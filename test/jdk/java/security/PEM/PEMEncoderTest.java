/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Testing PEM decodings
 */

import java.io.IOException;
import java.security.PEMDecoder;
import java.security.PEMEncoder;
import java.security.SecurityObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class PEMEncoderTest {

    static Map<String, SecurityObject> keymap;

    public static void main(String[] args) {
        keymap = new HashMap<>();
        PEMDecoder pemd = new PEMDecoder();
        List<String> keylist = new ArrayList<>();
        for (PEMCerts.Entry entry : PEMCerts.entryList) {
            try {
                keymap.put(entry.name(), pemd.decode(entry.pem()));
            } catch (Exception e) {
                throw new AssertionError("Failed to initialize map on" +
                    " entry \"" + entry.name() + "\"", e);
            }
        }
        test();
    }

    enum lineSep {NONE, CR, LF, CRLF}

    static void test() {
        PEMEncoder pemE = new PEMEncoder();

        for (String k : keymap.keySet()) {
            String cont, inst;
            PEMCerts.Entry entry = PEMCerts.getEntry(k);
            try {
                cont = pemE.encode(keymap.get(k));
                inst = new PEMEncoder().encode(keymap.get(k));
            } catch (IOException e) {
                throw new AssertionError("Continuous encoder use failure with " + entry.name(), e);
            }

            String pem = entry.pem();
            lineSep type;
            Pattern crlf = Pattern.compile("\r\n");

            if (pem.indexOf("\r\n") > 0) {
                type = lineSep.CRLF;
            } else if (pem.indexOf('\r') > 0) {
                type = lineSep.CR;
                Pattern p = Pattern.compile("\r");
                cont = p.matcher(pem).replaceAll("");
                inst = p.matcher(pem).replaceAll("");
            } else if (pem.indexOf('\n') > 0) {
                // This one is strange.  Apparently Pattern("\n") removes both
                // "\r\n".  To compensate, replacing it with "\n" just removes
                // the "\r"
                type = lineSep.LF;
                Pattern p = Pattern.compile("\n");
                cont = p.matcher(pem).replaceAll("\n");
                inst = p.matcher(pem).replaceAll("\n");
            } else {
                type = lineSep.NONE;
                Pattern p = Pattern.compile("\r\n");
                cont = crlf.matcher(pem).replaceAll("");
                inst = crlf.matcher(pem).replaceAll("");
            }

            try {
                if (pem.compareTo(cont) != 0) {
                    System.err.println("expected:\n" + pem);
                    System.err.println("generated:\n" + cont);
                    indexDiff(pem, cont);
                }
            } catch (AssertionError e) {
                throw new AssertionError("Continuous encoder PEM mismatch " + entry.name(), e);
            }

            try {
                if (pem.compareTo(inst) != 0) {
                    System.err.println("expected:\n" + pem);
                    System.err.println("generated:\n" + inst);
                    indexDiff(pem, inst);
                }
            } catch (AssertionError e) {
                throw new AssertionError("New encoder PEM mismatch " + entry.name());
            }
            System.err.println("Success: " + entry.name());

        }
    }

    static void indexDiff(String a, String b) {
        String lenerr = "";
        int len = a.length();
        int lenb = b.length();
        if (len != lenb) {
            lenerr = ":  Length mismatch: " + len + " vs " + lenb;
            len = Math.min(len, lenb);
        }
        for (int i = 0; i < len; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                throw new AssertionError("Char mistmatch, index #" + i + "  (" + a.charAt(i) + " vs " + b.charAt(i) + ")" + lenerr);
            }
        }
    }
}
