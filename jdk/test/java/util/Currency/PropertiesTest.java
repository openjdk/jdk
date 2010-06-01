/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class PropertiesTest {
    public static void main(String[] s) {
        for (int i = 0; i < s.length; i ++) {
            if ("-d".equals(s[i])) {
                i++;
                if (i == s.length) {
                    throw new RuntimeException("-d needs output file name");
                } else {
                    dump(s[i]);
                }
            } else if ("-c".equals(s[i])) {
                if (i+2 == s.length) {
                    throw new RuntimeException("-d needs two file name arguments, before and after respectively");
                } else {
                    compare(s[++i], s[++i]);
                }
            }
        }
    }

    private static void dump(String outfile) {
        File f = new File(outfile);
        PrintWriter pw;
        try {
            f.createNewFile();
            pw = new PrintWriter(f);
        } catch (Exception fnfe) {
            throw new RuntimeException(fnfe);
        }
        for (char c1 = 'A'; c1 <= 'Z'; c1++) {
            for (char c2 = 'A'; c2 <= 'Z'; c2++) {
                String ctry = new StringBuilder().append(c1).append(c2).toString();
                try {
                    Currency c = Currency.getInstance(new Locale("", ctry));
                    if (c != null) {
                        pw.printf(Locale.ROOT, "%s=%s,%03d,%1d\n",
                            ctry,
                            c.getCurrencyCode(),
                            c.getNumericCode(),
                            c.getDefaultFractionDigits());
                    }
                } catch (IllegalArgumentException iae) {
                    // invalid country code
                    continue;
                }
            }
        }
        pw.flush();
        pw.close();
    }

    private static void compare(String beforeFile, String afterFile) {
        // load file contents
        Properties before = new Properties();
        Properties after = new Properties();
        try {
            before.load(new FileReader(beforeFile));
            after.load(new FileReader(afterFile));
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        // remove the same contents from the 'after' properties
        Set<String> keys = before.stringPropertyNames();
        for (String key: keys) {
            String beforeVal = before.getProperty(key);
            String afterVal = after.getProperty(key);
            System.out.printf("Removing country: %s. before: %s, after: %s", key, beforeVal, afterVal);
            if (beforeVal.equals(afterVal)) {
                after.remove(key);
                System.out.printf(" --- removed\n");
            } else {
                System.out.printf(" --- NOT removed\n");
            }
        }

        // now look at the currency.properties
        String propFileName = System.getProperty("java.home") + File.separator +
                              "lib" + File.separator + "currency.properties";
        Properties p = new Properties();
        try {
            p.load(new FileReader(propFileName));
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        // test each replacements
        keys = p.stringPropertyNames();
        Pattern propertiesPattern =
            Pattern.compile("([A-Z]{3})\\s*,\\s*(\\d{3})\\s*,\\s*([0-3])");
        for (String key: keys) {
            String val = p.getProperty(key);
            String afterVal = after.getProperty(key);
            System.out.printf("Testing key: %s, val: %s... ", key, val);

            Matcher m = propertiesPattern.matcher(val.toUpperCase(Locale.ROOT));
            if (!m.find()) {
                // format is not recognized.
                System.out.printf("Format is not recognized.\n");
                if (afterVal != null) {
                    throw new RuntimeException("Currency data replacement for "+key+" failed: It was incorrectly altered to "+afterVal);
                }

                // ignore this
                continue;
            }

            Matcher mAfter = propertiesPattern.matcher(afterVal);
            mAfter.find();

            String code = m.group(1);
            String codeAfter = mAfter.group(1);
            int numeric = Integer.parseInt(m.group(2));
            int numericAfter = Integer.parseInt(mAfter.group(2));
            int fraction = Integer.parseInt(m.group(3));
            int fractionAfter = Integer.parseInt(mAfter.group(3));
            if (code.equals(codeAfter) &&
                (numeric == numericAfter)&&
                (fraction == fractionAfter)) {
                after.remove(key);
            } else {
                throw new RuntimeException("Currency data replacement for "+key+" failed: actual: (alphacode: "+codeAfter+", numcode: "+numericAfter+", fraction: "+fractionAfter+"), expected:  (alphacode: "+code+", numcode: "+numeric+", fraction: "+fraction+")");
            }
            System.out.printf("Success!\n");
        }
        if (!after.isEmpty()) {
            StringBuilder sb = new StringBuilder()
                .append("Currency data replacement failed.  Unnecessary modification was(were) made for the following currencies:\n");
            keys = after.stringPropertyNames();
            for (String key : keys) {
                sb.append("    country: ")
                .append(key)
                .append(" currency: ")
                .append(after.getProperty(key))
                .append("\n");
            }
            throw new RuntimeException(sb.toString());
        }
    }
}
