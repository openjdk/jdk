/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import sun.security.krb5.internal.ktab.KeyTab;
import sun.security.krb5.internal.ktab.KeyTabEntry;

/**
 * This class is called by the test ktcheck.sh and is not meant to run
 * by itself.
 */
public class KtabCheck {
    /**
     * Checks if a keytab contains exactly the keys (kvno and etype)
     * @param args keytabname kvno etype...
     */
    public static void main(String[] args) throws Exception {
        System.out.println("Checking " + Arrays.toString(args));
        KeyTab ktab = KeyTab.getInstance(args[0]);
        Set<String> expected = new HashSet<String>();
        for (int i=1; i<args.length; i += 2) {
            expected.add(args[i]+":"+args[i+1]);
        }
        for (KeyTabEntry e: ktab.getEntries()) {
            // KVNO and etype
            String vne = e.getKey().getKeyVersionNumber() + ":" +
                    e.getKey().getEType();
            if (!expected.contains(vne)) {
                throw new Exception("No " + vne + " in expected");
            }
            expected.remove(vne);
        }
        if (!expected.isEmpty()) {
            throw new Exception("Extra elements in expected");
        }
    }
}
