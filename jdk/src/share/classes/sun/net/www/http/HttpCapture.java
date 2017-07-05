/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.net.www.http;

import java.io.*;
import java.util.ArrayList;
import java.util.regex.*;
import sun.net.NetProperties;
import sun.util.logging.PlatformLogger;

/**
 * Main class of the HTTP traffic capture tool.
 * Captures are triggered by the sun.net.http.captureRules system property.
 * If set, it should point to a file containing the capture rules.
 * Format for the file is simple:
 * - 1 rule per line
 * - Lines starting with a # are considered comments and ignored
 * - a rule is a pair of a regular expression and file pattern, separated by a comma
 * - The regular expression is applied to URLs, if it matches, the traffic for
 *   that URL will be captured in the associated file.
 * - if the file name contains a '%d', then that sequence will be replaced by a
 *   unique random number for each URL. This allow for multi-threaded captures
 *   of URLs matching the same pattern.
 * - Rules are checked in sequence, in the same order as in the file, until a
 *   match is found or the end of the list is reached.
 *
 * Examples of rules:
 * www\.sun\.com , sun%d.log
 * yahoo\.com\/.*asf , yahoo.log
 *
 * @author jccollet
 */
public class HttpCapture {
    private File file = null;
    private boolean incoming = true;
    private BufferedWriter out = null;
    private static boolean initialized = false;
    private static volatile ArrayList<Pattern> patterns = null;
    private static volatile ArrayList<String> capFiles = null;

    private static synchronized void init() {
        initialized = true;
        String rulesFile = java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<String>() {
                public String run() {
                    return NetProperties.get("sun.net.http.captureRules");
                }
            });
        if (rulesFile != null && !rulesFile.isEmpty()) {
            BufferedReader in;
            try {
                in = new BufferedReader(new FileReader(rulesFile));
            } catch (FileNotFoundException ex) {
                return;
            }
            try {
                String line = in.readLine();
                while (line != null) {
                    line = line.trim();
                    if (!line.startsWith("#")) {
                        // skip line if it's a comment
                        String[] s = line.split(",");
                        if (s.length == 2) {
                            if (patterns == null) {
                                patterns = new ArrayList<Pattern>();
                                capFiles = new ArrayList<String>();
                            }
                            patterns.add(Pattern.compile(s[0].trim()));
                            capFiles.add(s[1].trim());
                        }
                    }
                    line = in.readLine();
                }
            } catch (IOException ioe) {

            } finally {
                try {
                    in.close();
                } catch (IOException ex) {
                }
            }
        }
    }

    private static synchronized boolean isInitialized() {
        return initialized;
    }

    private HttpCapture(File f, java.net.URL url) {
        file = f;
        try {
            out = new BufferedWriter(new FileWriter(file, true));
            out.write("URL: " + url + "\n");
        } catch (IOException ex) {
            PlatformLogger.getLogger(HttpCapture.class.getName()).severe(null, ex);
        }
    }

    public synchronized void sent(int c) throws IOException {
        if (incoming) {
            out.write("\n------>\n");
            incoming = false;
            out.flush();
        }
        out.write(c);
    }

    public synchronized void received(int c) throws IOException {
        if (!incoming) {
            out.write("\n<------\n");
            incoming = true;
            out.flush();
        }
        out.write(c);
    }

    public synchronized void flush() throws IOException {
        out.flush();
    }

    public static HttpCapture getCapture(java.net.URL url) {
        if (!isInitialized()) {
            init();
        }
        if (patterns == null || patterns.isEmpty()) {
            return null;
        }
        String s = url.toString();
        for (int i = 0; i < patterns.size(); i++) {
            Pattern p = patterns.get(i);
            if (p.matcher(s).find()) {
                String f = capFiles.get(i);
                File fi;
                if (f.indexOf("%d") >= 0) {
                    java.util.Random rand = new java.util.Random();
                    do {
                        String f2 = f.replace("%d", Integer.toString(rand.nextInt()));
                        fi = new File(f2);
                    } while (fi.exists());
                } else {
                    fi = new File(f);
                }
                return new HttpCapture(fi, url);
            }
        }
        return null;
    }
}
