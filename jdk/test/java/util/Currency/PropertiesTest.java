/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.text.*;
import java.util.*;
import java.util.regex.*;

public class PropertiesTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("-d")) {
            dump(args[1]);
        } else if (args.length == 4 && args[0].equals("-c")) {
            compare(args[1], args[2], args[3]);
        } else {
            System.err.println("Usage:  java PropertiesTest -d <dumpfile>");
            System.err.println("        java PropertiesTest -c <beforedump> <afterdump> <propsfile>");
            System.exit(-1);
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

    private static void compare(String beforeFile, String afterFile, String propsFile)
        throws IOException
    {
        // load file contents
        Properties before = new Properties();
        try (Reader reader = new FileReader(beforeFile)) {
            before.load(reader);
        }
        Properties after = new Properties();
        try (Reader reader = new FileReader(afterFile)) {
            after.load(reader);
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
        Properties p = new Properties();
        try (Reader reader = new FileReader(propsFile)) {
            p.load(reader);
        }

        // test each replacements
        keys = p.stringPropertyNames();
        Pattern propertiesPattern =
            Pattern.compile("([A-Z]{3})\\s*,\\s*(\\d{3})\\s*,\\s*" +
                "(\\d+)\\s*,?\\s*(\\d{4}-\\d{2}-\\d{2}T\\d{2}:" +
                "\\d{2}:\\d{2})?");
        for (String key: keys) {
            String val = p.getProperty(key);
            try {
                if (countOccurrences(val, ',') == 3 && !isPastCutoverDate(val)) {
                    System.out.println("Skipping since date is in future");
                    continue; // skip since date in future (no effect)
                }
            } catch (ParseException pe) {
                // swallow - currency class should not honour this value
                continue;
            }
            String afterVal = after.getProperty(key);
            System.out.printf("Testing key: %s, val: %s... ", key, val);
            System.out.println("AfterVal is : " + afterVal);

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

            String code = m.group(1);
            int numeric = Integer.parseInt(m.group(2));
            int fraction = Integer.parseInt(m.group(3));
            if (fraction > 9) {
                System.out.println("Skipping since the fraction is greater than 9");
                continue;
            }

            Matcher mAfter = propertiesPattern.matcher(afterVal);
            mAfter.find();

            String codeAfter = mAfter.group(1);
            int numericAfter = Integer.parseInt(mAfter.group(2));
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

    private static boolean isPastCutoverDate(String s)
            throws IndexOutOfBoundsException, NullPointerException, ParseException {
        String dateString = s.substring(s.lastIndexOf(',')+1, s.length()).trim();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        format.setLenient(false);

        long time = format.parse(dateString).getTime();
        if (System.currentTimeMillis() - time >= 0L) {
            return true;
        } else {
            return false;
        }
    }

    private static int countOccurrences(String value, char match) {
        int count = 0;
        for (char c : value.toCharArray()) {
            if (c == match) {
               ++count;
            }
        }
        return count;
    }
}
