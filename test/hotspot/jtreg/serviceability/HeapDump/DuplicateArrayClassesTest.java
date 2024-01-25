/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.Reference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.hprof.parser.PositionDataInputStream;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @bug 8281267
 * @summary Verifies heap dump does not contain duplicate array classes
 * @library /test/lib
 * @run driver DuplicateArrayClassesTest
 */

class DuplicateArrayClassesTarg extends LingeredApp {
    public static void main(String[] args) {
        // Initialize some array classes (for primitive type and object type).
        int[][][] intArray = new int[0][][];
        String[][][] strArray = new String[0][][];
        LingeredApp.main(args);
        Reference.reachabilityFence(intArray);
        Reference.reachabilityFence(strArray);
    }
}


public class DuplicateArrayClassesTest {

    public static void main(String[] args) throws Exception {
        File dumpFile = new File("Myheapdump.hprof");
        createDump(dumpFile);
        verifyDump(dumpFile);
    }

    private static void createDump(File dumpFile) throws Exception {
        LingeredApp theApp = null;
        try {
            theApp = new DuplicateArrayClassesTarg();

            LingeredApp.startApp(theApp);

            //jcmd <pid> GC.heap_dump <file_path>
            JDKToolLauncher launcher = JDKToolLauncher
                    .createUsingTestJDK("jcmd")
                    .addToolArg(Long.toString(theApp.getPid()))
                    .addToolArg("GC.heap_dump")
                    .addToolArg(dumpFile.getAbsolutePath());
            Process p = ProcessTools.startProcess("jcmd", new ProcessBuilder(launcher.getCommand()));
            // If something goes wrong with heap dumping most likely we'll get crash of the target VM.
            while (!p.waitFor(5, TimeUnit.SECONDS)) {
                if (!theApp.getProcess().isAlive()) {
                    log("ERROR: target VM died, killing jcmd...");
                    p.destroyForcibly();
                    throw new Exception("Target VM died");
                }
            }

            if (p.exitValue() != 0) {
                throw new Exception("Jcmd exited with code " + p.exitValue());
            }
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }

    private static final byte HPROF_UTF8                = 0x01;
    private static final byte HPROF_LOAD_CLASS          = 0x02;
    private static final byte HPROF_HEAP_DUMP           = 0x0c;
    private static final byte HPROF_GC_CLASS_DUMP       = 0x20;
    private static final byte HPROF_HEAP_DUMP_SEGMENT   = 0x1C;
    private static final byte HPROF_HEAP_DUMP_END       = 0x2C;

    private static void verifyDump(File dumpFile) throws IOException {
        Asserts.assertTrue(dumpFile.exists(), "Heap dump file not found.");

        // HPROF_UTF8 records.
        Map<Long, String> names = new HashMap<>();
        // Maps from HPROF_LOAD_CLASS records.
        Map<Long, String> classId2Name = new Hashtable<>();
        Map<String, Long> className2Id = new Hashtable<>();
        // Duplicate HPROF_LOAD_CLASS records.
        List<Long> duplicateLoadClassIDs = new LinkedList<>();
        // HPROF_GC_CLASS_DUMP records.
        Set<Long> dumpClassIDs = new HashSet<>();
        // Duplicate HPROF_GC_CLASS_DUMP records.
        List<Long> duplicateDumpClassIDs = new LinkedList<>();

        try (DumpInputStream in = new DumpInputStream(dumpFile)) {
            while (true) {
                DumpRecord rec;
                try {
                    rec = in.readRecord();
                } catch (EOFException ex) {
                    break;
                }
                long pos = in.position();   // save the current pos

                switch (rec.tag()) {
                    case HPROF_UTF8:
                        long id = in.readID();
                        byte[] chars = new byte[(int) rec.size - in.idSize];
                        in.readFully(chars);
                        names.put(id, new String(chars));
                        break;
                    case HPROF_LOAD_CLASS:
                        long classSerialNo = in.readU4();
                        long classID = in.readID();
                        long stackTraceSerialNo = in.readU4();
                        long classNameID = in.readID();
                        // We expect all names are dumped before classes.
                        String className = names.get(classNameID);

                        String prevName = classId2Name.putIfAbsent(classID, className);
                        if (prevName != null) { // there is a class with the same ID
                            if (!prevName.equals(className)) {
                                // Something is completely wrong.
                                throw new RuntimeException("Found new class with id=" + classID
                                        + " and different name (" + className + ", was " + prevName + ")");
                            }
                            duplicateLoadClassIDs.add(classID);
                        }
                        // It's ok if we have other class with the same name (may be from other classloader).
                        className2Id.putIfAbsent(className, classID);
                        break;
                    case HPROF_HEAP_DUMP:
                    case HPROF_HEAP_DUMP_SEGMENT:
                        // HPROF_GC_CLASS_DUMP records are dumped first (in the beginning of the dump).
                        long endOfRecordPos = pos + rec.size();

                        while (in.position() < endOfRecordPos) {
                            byte subTag = in.readU1();
                            if (subTag != HPROF_GC_CLASS_DUMP) {
                                break;
                            }
                            // We don't know HPROF_GC_CLASS_DUMP size, so have to read it completely.
                            long dumpClassID = readClassDump(in);

                            if (!dumpClassIDs.add(dumpClassID)) {
                                duplicateDumpClassIDs.add(dumpClassID);
                            }
                        }
                        break;
                }

                // Skip bytes till end of the record.
                long bytesRead = in.position() - pos;
                if (bytesRead > rec.size()) {
                    throw new RuntimeException("Bad record,"
                            + " record.size = " + rec.size() + ", read " + bytesRead);
                }
                in.skipNBytes(rec.size() - bytesRead);
            }

            log("HPROF_LOAD_CLASS records: " + (classId2Name.size() + duplicateLoadClassIDs.size()));
            log("HPROF_GC_CLASS_DUMP records: " + (dumpClassIDs.size() + duplicateDumpClassIDs.size()));

            // Verify we have array classes used by target app.
            String[] expectedClasses = {"[I", "[[I", "[[[I",
                    "[Ljava/lang/String;", "[[Ljava/lang/String;", "[[[Ljava/lang/String;"};
            for (String className: expectedClasses) {
                Long classId = className2Id.get(className);
                if (classId == null) {
                    throw new RuntimeException("no HPROF_LOAD_CLASS record for class " + className);
                }
                // verify there is HPROF_GC_CLASS_DUMP record for the class
                if (!dumpClassIDs.contains(classId)) {
                    throw new RuntimeException("no HPROF_GC_CLASS_DUMP for class " + className);
                }
                log("found " + className);
            }
            if (!duplicateLoadClassIDs.isEmpty() || !duplicateDumpClassIDs.isEmpty()) {
                log("Duplicate(s) detected:");
                log("HPROF_LOAD_CLASS records (" + duplicateLoadClassIDs.size() + "):");
                duplicateLoadClassIDs.forEach(id -> log("  - id = " + id + ": " + classId2Name.get(id)));
                log("HPROF_GC_CLASS_DUMP records (" + duplicateDumpClassIDs.size() + "):");
                duplicateDumpClassIDs.forEach(id -> log("  - id = " + id + ": " + classId2Name.get(id)));
                throw new RuntimeException("duplicates detected");
            }
        }
    }

    // Reads the whole HPROF_GC_CLASS_DUMP record, returns class ID.
    private static long readClassDump(DumpInputStream in) throws IOException {
        long classID = in.readID();
        long stackTraceNum = in.readU4();
        long superClassId = in.readID();
        long loaderClassId = in.readID();
        long signerClassId = in.readID();
        long protectionDomainClassId = in.readID();
        long reserved1 = in.readID();
        long reserved2 = in.readID();
        long instanceSize = in.readU4();
        long cpSize = in.readU2();
        for (long i = 0; i < cpSize; i++) {
            long cpIndex = in.readU2();
            byte type = in.readU1();
            in.skipNBytes(in.type2size(type)); // value
        }
        long staticNum = in.readU2();
        for (long i = 0; i < staticNum; i++) {
            long nameId = in.readID();
            byte type = in.readU1();
            in.skipNBytes(in.type2size(type)); // value
        }
        long instanceNum = in.readU2();
        for (long i = 0; i < instanceNum; i++) {
            long nameId = in.readID();
            byte type = in.readU1();
        }
        return classID;
    }

    private static void log(Object s) {
        System.out.println(s);
    }


    private static record DumpRecord (byte tag, long size) {}

    private static class DumpInputStream extends PositionDataInputStream {
        public final int idSize;

        public DumpInputStream(File f) throws IOException {
            super(new BufferedInputStream(new FileInputStream(f)));

            // read header:
            //   header    "JAVA PROFILE 1.0.2" (0-terminated)
            //   u4        size of identifiers. Identifiers are used to represent
            //   u4         high word
            //   u4         low word    number of milliseconds since 0:00 GMT, 1/1/70
            String header = readStr();
            log("header: \"" + header + "\"");
            Asserts.assertTrue(header.startsWith("JAVA PROFILE "));

            idSize = readInt();
            if (idSize != 4 && idSize != 8) {
                Asserts.fail("id size " + idSize + " is not supported");
            }
            // ignore timestamp
            readU4();
            readU4();
        }

        // Reads null-terminated string
        public String readStr() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (char ch = (char)readByte(); ch != '\0'; ch = (char)readByte()) {
                sb.append(ch);
            }
            return sb.toString();
        }

        public byte readU1() throws IOException {
            return readByte();
        }
        public int readU2() throws IOException {
            return readUnsignedShort();
        }
        public long readU4() throws IOException {
            // keep the value positive
            return ((long)readInt() & 0x0FFFFFFFFL);
        }

        public long readID() throws IOException {
            return idSize == 4 ? readU4() : readLong();
        }

        public DumpRecord readRecord() throws IOException {
            byte tag = readU1();
            readU4();   // timestamp, ignore it
            long size = readU4();
            return new DumpRecord(tag, size);
        }

        public long type2size(byte type) {
            switch (type) {
                case 1:     // array
                case 2:     // object
                    return idSize;
                case 4:     // boolean
                case 8:     // byte
                    return 1;
                case 5:     // char
                case 9:     // short
                    return 2;
                case 6:     // float
                case 10:    // int
                    return 4;
                case 7:     // double
                case 11:    // long
                    return 8;
            }
            throw new RuntimeException("unknown type: " + type);
        }

    }

}
