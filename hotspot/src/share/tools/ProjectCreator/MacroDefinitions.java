/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.io.*;
import java.util.*;

public class MacroDefinitions {
    private Vector macros;

    public MacroDefinitions() {
        macros = new Vector();
    }

    public void addMacro(String name, String contents) {
        Macro macro = new Macro();
        macro.name = name;
        macro.contents = contents;
        macros.add(macro);
    }

    private boolean lineIsEmpty(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public void readFrom(String fileName, boolean missingOk)
        throws FileNotFoundException, FileFormatException, IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(fileName));
        } catch (FileNotFoundException e) {
            if (missingOk) {
                return;
            } else {
                throw(e);
            }
        }
        String line;
        do {
            line = reader.readLine();
            if (line != null) {
                // This had to be rewritten (compare to Database.java)
                // because the Solaris platform file has been
                // repurposed and now contains "macros" with spaces in
                // them.

                if ((!line.startsWith("//")) &&
                    (!lineIsEmpty(line))) {
                    int nameBegin = -1;
                    int nameEnd = -1;
                    boolean gotEquals = false;
                    int contentsBegin = -1;
                    int contentsEnd = -1;

                    int i = 0;
                    // Scan forward for beginning of name
                    while (i < line.length()) {
                        if (!Character.isWhitespace(line.charAt(i))) {
                            break;
                        }
                        i++;
                    }
                    nameBegin = i;

                    // Scan forward for end of name
                    while (i < line.length()) {
                        if (Character.isWhitespace(line.charAt(i))) {
                            break;
                        }
                        i++;
                    }
                    nameEnd = i;

                    // Scan forward for equals sign
                    while (i < line.length()) {
                        if (line.charAt(i) == '=') {
                            gotEquals = true;
                            break;
                        }
                        i++;
                    }

                    // Scan forward for start of contents
                    i++;
                    while (i < line.length()) {
                        if (!Character.isWhitespace(line.charAt(i))) {
                            break;
                        }
                        i++;
                    }
                    contentsBegin = i;

                    // Scan *backward* for end of contents
                    i = line.length() - 1;
                    while (i >= 0) {
                        if (!Character.isWhitespace(line.charAt(i))) {
                            break;
                        }
                    }
                    contentsEnd = i+1;

                    // Now do consistency check
                    if (!((nameBegin < nameEnd) &&
                          (nameEnd < contentsBegin) &&
                          (contentsBegin < contentsEnd) &&
                          (gotEquals == true))) {
                        throw new FileFormatException(
                            "Expected \"macroname = value\", " +
                            "but found: " + line
                        );
                    }

                    String name = line.substring(nameBegin, nameEnd);
                    String contents = line.substring(contentsBegin,
                                                     contentsEnd);
                    addMacro(name, contents);
                }
            }
        } while (line != null);
        reader.close();
    }

    /** This returns an Iterator of Macros. You should not mutate the
        returned Macro objects or use the Iterator to remove
        macros. */
    public Iterator getMacros() {
        return macros.iterator();
    }
}
