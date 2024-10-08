/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*

This is a simple parser for parsing the output of

   java -Xshare:dump -Xlog:cds+map=debug,cds+map+oops=trace:file=cds.map:none:filesize=0

The map file contains patterns like this for the heap objects:

======================================================================
0x00000000ffe00000: @@ Object (0xffe00000) java.lang.String
 - klass: 'java/lang/String' 0x0000000800010220
 - fields (3 words):
 - private 'hash' 'I' @12  0 (0x00000000)
 - private final 'coder' 'B' @16  0 (0x00)
 - private 'hashIsZero' 'Z' @17  true (0x01)
 - injected 'flags' 'B' @18  1 (0x01)
 - private final 'value' '[B' @20 0x00000000ffe00018 (0xffe00018) [B length: 0
0x00000000ffe00018: @@ Object (0xffe00018) [B length: 0
 - klass: {type array byte} 0x00000008000024d8
======================================================================

Currently this parser just check the output related to JDK-8308903.
I.e., each oop field must point to a valid HeapObject. For example, the 'value' field
in the String must point to a valid byte array.

This parser can be extended to check for the other parts of the map file, or perform
more analysis on the HeapObjects.

*/

public class CDSMapReader {
    public static class MapFile {
        ArrayList<HeapObject> heapObjects = new ArrayList<>();
        HashMap<Long, HeapObject> oopToObject = new HashMap<>();
        HashMap<Long, HeapObject> narrowOopToObject = new HashMap<>();
        public int stringCount = 0;

        void add(HeapObject heapObject) {
            heapObjects.add(heapObject);
            oopToObject.put(heapObject.address.oop, heapObject);
            if (heapObject.address.narrowOop != 0) {
                narrowOopToObject.put(heapObject.address.narrowOop, heapObject);
            }
            if (heapObject.className.equals("java.lang.String")) {
                stringCount ++;
            }
        }

        public int heapObjectCount() {
            return heapObjects.size();
        }
    }

    public static class HeapAddress {
        long oop;
        long narrowOop;

        HeapAddress(String oopStr, String narrowOopStr) {
            oop = Long.parseUnsignedLong(oopStr, 16);
            if (narrowOopStr != null) {
                narrowOop = Long.parseUnsignedLong(narrowOopStr, 16);
            }
        }
    }

    public static class Klass {
        long address;
        String name;

        static Klass getKlass(String name, String addr) {
            // TODO: look up from a table of known Klasses
            Klass k = new Klass();
            k.name = name;
            k.address =  Long.parseUnsignedLong(addr, 16);
            return k;
        }
    }

    public static class HeapObject {
        HeapAddress address;
        ArrayList<Field> fields;
        String className;
        Klass klass;

        HeapObject(String className, String oop, String narrowOop) {
            this.className = className;
            address = new HeapAddress(oop, narrowOop);
        }

        void setKlass(String klassName, String address) {
            klass = Klass.getKlass(klassName, address);
        }

        void addOopField(String name, String offset, String oopStr, String narrowOopStr) {
            if (fields == null) {
                fields = new ArrayList<Field>();
            }
            fields.add(new Field(name, offset, oopStr, narrowOopStr));
        }
    }

    public static class Field {
        String name;
        int offset;
        HeapAddress referentAddress; // non-null iff this is an object field
        int lineCount;

        Field(String name, String offset, String oopStr, String narrowOopStr) {
            this.name = name;
            this.offset = Integer.parseInt(offset);
            this.referentAddress = new HeapAddress(oopStr, narrowOopStr);
            this.lineCount = CDSMapReader.lineCount;
        }
    }

    // 0x00000007ffc00000:   4a5b8701 00000063 00010290 00000000 00010100 fff80003
    static Pattern rawDataPattern = Pattern.compile("^0x([0-9a-f]+): *( [0-9a-f]+)+ *$");

    // (one address)
    // 0x00000007ffc00000: @@ Object java.lang.String
    static Pattern objPattern1 = Pattern.compile("^0x([0-9a-f]+): @@ Object (.*)");

    // (two addresses)
    // 0x00000007ffc00000: @@ Object (0xfff80000) java.lang.String
    static Pattern objPattern2 = Pattern.compile("^0x([0-9a-f]+): @@ Object [(]0x([0-9a-f]+)[)] (.*)");

    //  - klass: 'java/lang/String' 0x0000000800010290
    static Pattern instanceObjKlassPattern = Pattern.compile("^ - klass: '([^']+)' 0x([0-9a-f]+)");

    //  - klass: {type array byte} 0x00000008000024c8
    static Pattern typeArrayKlassPattern = Pattern.compile("^ - klass: [{]type array ([a-z]+)[}] 0x([0-9a-f]+)");

    //  - klass: 'java/lang/Object'[] 0x00000008000013e0
    static Pattern objArrayKlassPattern = Pattern.compile("^ - klass: ('[^']+'(\\[\\])+) 0x([0-9a-f]+)");

    //  - fields (3 words):
    static Pattern fieldsWordsPattern = Pattern.compile("^ - fields [(]([0-9]+) words[)]:$");

    // (one address)
    //  - final 'key' 'Ljava/lang/Object;' @16 0x00000007ffc68260 java.lang.String
    static Pattern oopFieldPattern1 = Pattern.compile(" - [^']* '([^']+)'.*@([0-9]+) 0x([0-9a-f]+) (.*)");

    // (two addresses)
    //  - final 'key' 'Ljava/lang/Object;' @16 0x00000007ffc68260 (0xfff8d04c) java.lang.String
    static Pattern oopFieldPattern2 = Pattern.compile(" - [^']* '([^']+)'.*@([0-9]+) 0x([0-9a-f]+) [(]0x([0-9a-f]+)[)] (.*)");

    // (injected module_entry)
    //  - injected 'module_entry' 'J' @16 0 (0x0000000000000000)
    static Pattern moduleEntryPattern = Pattern.compile("- injected 'module_entry' 'J' @[0-9]+[ ]+([0-9]+)");

    private static Matcher match(String line, Pattern pattern) {
        Matcher m = pattern.matcher(line);
        if (m.find()) {
            return m;
        } else {
            return null;
        }
    }

    private static void parseHeapObject(String className, String oop, String narrowOop) throws IOException {
        HeapObject heapObject = parseHeapObjectImpl(className, oop, narrowOop);
        mapFile.add(heapObject);
    }

    private static HeapObject parseHeapObjectImpl(String className, String oop, String narrowOop) throws IOException {
        HeapObject heapObject = new HeapObject(className, oop, narrowOop);
        Matcher m;

        nextLine();
        while (line != null && match(line, rawDataPattern) != null) { // skip raw data
            nextLine();
        }

        if (line == null || !line.startsWith(" - ")) {
            return heapObject;
        }

        if ((m = match(line, instanceObjKlassPattern)) != null) {
            heapObject.setKlass(m.group(1), m.group(2));
            nextLine();
            if ((m = match(line, fieldsWordsPattern)) == null) {
                throw new RuntimeException("Expected field size info");
            }
            while (true) {
                nextLine();
                if (line == null || !line.startsWith(" - ")) {
                    return heapObject;
                }
                if (!line.contains("marked metadata pointer")) {
                    if ((m = match(line, oopFieldPattern2)) != null) {
                        heapObject.addOopField(m.group(1), m.group(2), m.group(3), m.group(4));
                    } else if ((m = match(line, oopFieldPattern1)) != null) {
                        heapObject.addOopField(m.group(1), m.group(2), m.group(3), null);
                    } else if ((m = match(line, moduleEntryPattern)) != null) {
                        String value = m.group(1);
                        if (!value.equals("0")) {
                            throw new RuntimeException("module_entry should be 0 but found: " + line);
                        }
                    }
                }
            }
        } else if ((m = match(line, typeArrayKlassPattern)) != null) {
            heapObject.setKlass(m.group(1), m.group(2));
            // TODO: read all the array elements
            while (true) {
                nextLine();
                if (line == null || !line.startsWith(" - ")) {
                    return heapObject;
                }
            }
        } else if ((m = match(line, objArrayKlassPattern)) != null) {
            heapObject.setKlass(m.group(1), m.group(3));
            // TODO: read all the array elements
            while (true) {
                nextLine();
                if (line == null || !line.startsWith(" - ")) {
                    return heapObject;
                }
            }
        } else {
            throw new RuntimeException("Expected klass info");
        }
    }

    static MapFile mapFile;
    static BufferedReader reader;
    static String line = null; // current line being parsed
    static int lineCount = 0;
    static String nextLine()  throws IOException {
        line = reader.readLine();
        ++ lineCount;
        return line;
    }

    public static MapFile read(String fileName) {
        mapFile = new MapFile();
        lineCount = 0;

        try (BufferedReader r = new BufferedReader(new FileReader(fileName))) {
            reader = r;
            nextLine();

            Matcher m;
            while (line != null) {
                if ((m = match(line, objPattern2)) != null) {
                    parseHeapObject(m.group(3), m.group(1), m.group(2));
                } else if ((m = match(line, objPattern1)) != null) {
                    parseHeapObject(m.group(2), m.group(1), null);
                } else {
                    nextLine();
                }
            }
            return mapFile;
        } catch (Throwable t) {
            System.out.println("Error parsing line " + lineCount + ": " + line);
            throw new RuntimeException(t);
        } finally {
            System.out.println("Parsed " + lineCount + " lines in " + fileName);
            System.out.println("Found " + mapFile.heapObjectCount() + " heap objects ("
                               + mapFile.stringCount + " strings)");
            mapFile = null;
            reader = null;
            line = null;
            lineCount = 0;
        }
    }

    private static void mustContain(HashMap<Long, HeapObject> allObjects, Field field, long pointer, boolean isNarrow) {
        if (allObjects.get(pointer) == null) {
            throw new RuntimeException((isNarrow ? "narrowOop" : "oop") + " pointer 0x" + Long.toHexString(pointer) +
                                       " on line " + field.lineCount + " doesn't point to a valid heap object");
        }
    }

    // Check that each oop fields in the HeapObjects must point to a valid HeapObject.
    public static void validate(MapFile mapFile) {
        int count1 = 0;
        int count2 = 0;
        for (HeapObject heapObject : mapFile.heapObjects) {
            if (heapObject.fields != null) {
                for (Field field : heapObject.fields) {
                    HeapAddress referentAddress = field.referentAddress;
                    long oop = referentAddress.oop;
                    long narrowOop = referentAddress.narrowOop;
                    // Is this test actually doing something?
                    //     To see how an invalidate pointer may be found, change oop in the
                    //     following line to oop+1
                    if (oop != 0) {
                        mustContain(mapFile.oopToObject, field, oop, false);
                        count1 ++;
                    }
                    if (narrowOop != 0) {
                        mustContain(mapFile.narrowOopToObject, field, narrowOop, true);
                        count2 ++;
                    }
                }
            }
        }
        System.out.println("Found " + count1 + " non-null oop field references (normal)");
        System.out.println("Found " + count2 + " non-null oop field references (narrow)");

        if (mapFile.heapObjectCount() > 0) {
            // heapObjectCount() may be zero if the selected GC doesn't support heap object archiving.
            if (mapFile.stringCount <= 0) {
                throw new RuntimeException("CDS map file should contain at least one string");
            }
            if (count1 < mapFile.stringCount) {
                throw new RuntimeException("CDS map file seems incorrect: " + mapFile.heapObjectCount() +
                                           " objects (" + mapFile.stringCount + " strings). Each string should" +
                                           " have one non-null oop field but we found only " + count1 +
                                           " non-null oop field references");
            }
        }
    }

    public static void main(String args[]) {
        MapFile mapFile = read(args[0]);
        validate(mapFile);
    }
}
