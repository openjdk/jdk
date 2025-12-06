/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8367031 8367938
 * @summary Test serialization compatibility of classes
 * @library /test/lib
 * @build jdk.test.lib.hexdump.HexPrinter jdk.test.lib.hexdump.ObjectStreamPrinter
 * @run junit ArchivedClassesTest
 */

import jdk.test.lib.hexdump.HexPrinter;
import jdk.test.lib.hexdump.ObjectStreamPrinter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test compatibility of classes when serialized by an earlier version
 * and deserialized by the current version.
 * The serialized archive contains serialized classes from `java.se` modules.
 * <p>
 * If an archive already exists those classes are compared against the current runtime classes.
 * The archive is found in ${test.src} in the same directory as the ArchivedClassesTest test.
 * <p>
 * If the archive does not exist, it is created by the iterating
 * through ModuleFinder.ofSystem() and extracting the list of filtered classes.
 * The archive is generated in the local directory and the test fails.
 * The archive is manually committed to the repo in the same directory as the test.
 */
public class ArchivedClassesTest {

    // File name in test source directory to store serialized stream encoded with HexDump
    private static final String ARCHIVED_CLASSES_TEST_FILE = "ArchivedClasses.txt";

    // Full path to archive of serialized classes
    private static Path CLASS_ARCHIVE_PATH;

    // Compute the path in test.src of the archived classes
    private static Path setupClassesArchivePath() {
        Path path = Path.of(System.getProperty("test.src", "."));
        return path.resolve(ARCHIVED_CLASSES_TEST_FILE);
    }

    // Modules that are not archived because the compatibility is not needed or are un-interesting
    private static final Set<String> EXCLUDED_MODULES =
            Set.of("java.desktop", "jdk.unsupported.desktop", "jdk.jconsole");

    // Classes that are ignored because the errors are expected or un-interesting
    private static final Set<String> EXCLUDED_CLASSES =
            Set.of("java.nio.ByteOrder",                        // changed occurred JDK 26
                    "java.security.interfaces.XECPublicKey",    // changed occurred JDK 22
                    "java.security.interfaces.XECPrivateKey",   // changed occurred JDK 22
                    "java.security.interfaces.EdECPublicKey",   // changed occurred JDK 22
                    "java.security.interfaces.EdECPrivateKey"); // changed occurred JDK 22

    @BeforeAll
    public static void initClassArchivePath() {
        CLASS_ARCHIVE_PATH = setupClassesArchivePath();
    }

    // Return true to include the class.
    private static boolean includedClasses(Class<?> clazz) {
        String moduleName = clazz.getModule().getName();
        return !EXCLUDED_MODULES.contains(moduleName);
    }

    /**
     * Deserialize the serialized classes.
     * If the archive in test.src does not exist, it is created in the scratch directory
     * and the test fails. The archive should be moved to the test.src directory and committed.
     * <p>
     * For each class in the archive decode the serialized bytes and try to load the class.
     * If the class loading fails, note the error and print a detailed comparison
     * of the serialized class bytes and report an error.
     * <p>
     * Print the names of classes that existed in the archived serialized
     * form but are no longer found.
     * And list the names of compatible classes for which the archived serialized form
     * is not identical to the serialized stream of the local class. Those changes
     * may be due to valid migrated classes, added or removed fields or other flag bits.
     */
    @Test
    void deserializeArchivedClasses() throws IOException {
        final PrintStream out = System.err;

        // Compute the filtered list of local classes
        // Sorted by module and class name to make diffs easier to read
        TreeSet<Class<?>> localClasses = ClassScanner.findAll()
                .filter(ArchivedClassesTest::includedClasses)
                .collect(Collectors.toCollection(() -> new TreeSet<>(ArchivedClassesTest::compareClassByModule)));

        Path path = CLASS_ARCHIVE_PATH;
        if (!Files.exists(path)) {
            var newRefPath = Path.of(ARCHIVED_CLASSES_TEST_FILE);
            createClassesArchive(newRefPath, localClasses);
            out.printf("Archived serialized classes file does not exist: %s%n" +
                    "New serialized classes generated into: %s%n", path, newRefPath.toAbsolutePath());
            fail("Created new archive");
        }

        List<ClassNameAndBytes> classLines = List.of();
        try (Stream<String> lines = Files.lines(CLASS_ARCHIVE_PATH)) {
            // Decode the serialized classes archive and serialize local classes to match
            out.printf("Archive file: %s%n", CLASS_ARCHIVE_PATH);
            ArchiveHeader header = new ArchiveHeader();
            classLines = lines
                    .dropWhile(header::parseHeaderLines)
                    .filter(s -> !isCommentOrBlank(s))
                    .map(ClassNameAndBytes::parseLine)
                    .toList();
            out.print(header);
        } catch (IOException ioe) {
            fail("Serialized archive not accessible: " + CLASS_ARCHIVE_PATH, ioe);
        }

        // Compare non-matching archived vs local classes
        List<ClassNameAndBytes> errors = classLines.stream()
                .filter(ClassNameAndBytes::mismatches)  // drop all the ones that match
                .map( nomatch -> {
                    ByteArrayInputStream is = new ByteArrayInputStream(nomatch.archiveClassBytes);
                    try (ObjectInputStream ois = new ObjectInputStream(is)) {
                        Class<?> clazz = (Class<?>) ois.readObject();
                        return nomatch.addMessage("Compatible change (field added/removed): " + clazz);
                    } catch (ClassNotFoundException cnfe) {
                        return nomatch.addMessage("Removed class");
                    } catch (InvalidClassException ice) {
                        return nomatch.addMessage("InvalidClassException: " + ice.getMessage());
                    } catch (Exception ex) {
                        return nomatch.addMessage("Unable to read: " + ex.getMessage());
                    }
                })
                .sorted()
                .distinct()
                .toList();

        long errCount = 0;
        if (!errors.isEmpty()) {
            // Print and count the serious mismatches that are not being ignored
            out.printf("%n** Incompatible changes%n");
            errCount = errors.stream()
                    .filter(ClassNameAndBytes::seriousError)
                    .filter(entry -> !EXCLUDED_CLASSES.contains(entry.name))
                    .filter(e -> e.showMismatch(out))
                    .count();

            // Print the incompatible but ignored differences
            out.printf("%n** Approved Incompatible changes%n");
            errors.stream()
                    .filter(ClassNameAndBytes::seriousError)
                    .filter(entry -> EXCLUDED_CLASSES.contains(entry.name))
                    .forEach(out::println);

            // Print the compatible or un-interesting differences
            out.printf("%n** Compatible and un-interesting changes%n");
            errors.stream()
                    .filter(e -> !e.seriousError())
                    .forEach(out::println);
        }

        // List any new local classes that are not in the archive
        Set<String> newClasses = localClasses.stream()
                .map(Class::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        classLines.stream()
                .filter(l -> l.localClass() != null)
                .map(l -> l.localClass.getName())
                .forEach(newClasses::remove);
        if (!newClasses.isEmpty()) {
            out.printf("%nNew classes not found in the archive: %d%n", newClasses.size());
            newClasses.forEach(c -> out.printf("    %s%n", c));
        }

        // Fail if there were any mismatches
        assertEquals(0, errCount, "Unexpected mismatches in archived vs local serialized classes");
    }

    // Compare classes by module name and class name
    private static int compareClassByModule(Class<?> a, Class<?> b) {
        int ret = a.getModule().getName().compareTo(b.getModule().getName());
        return (ret != 0) ? ret : a.getName().compareTo(b.getName());
    }

    // Utility to dump the serialized class bytes with the formatter for a serialized stream
    private static void hexPrintSerialStream(byte[] bytes) {
        HexPrinter hp = HexPrinter.canonical()
                .dest(System.err)
                .formatter(ObjectStreamPrinter.formatter());
        hp.format(bytes, 0, 0x1000);    // limit size of stream printed
    }

    // Comment and blank line filter
    private static boolean isCommentOrBlank(String s) {
        return s.isBlank() || s.startsWith("#");
    }

    /**
     * Print and Parsing of the archive header lines.
     */
    private static class ArchiveHeader {
        private static final String TITLE_PREFIX = "# Title: ";
        private static final String VERSION_PREFIX = "# Version: ";
        private static final String CLASSES_PREFIX = "# Classes: ";

        private String title;
        private Runtime.Version version;
        private int classCount;

        ArchiveHeader() {
            this.title = "";
            this.version = null;
            this.classCount = -1;
        }

        ArchiveHeader(String title, Runtime.Version version, int classCount) {
            this.title = title;
            this.version = version;
            this.classCount = classCount;
        }

        public String toString() {
            return TITLE_PREFIX + title + "\n" +
                    VERSION_PREFIX + version + "\n" +
                    CLASSES_PREFIX + classCount + "\n";
        }

        boolean parseHeaderLines(String s) {
            if (s.startsWith(TITLE_PREFIX)) {
                title = s.substring(TITLE_PREFIX.length()).trim();
            } else if (s.startsWith(VERSION_PREFIX)) {
                version = Runtime.Version.parse(s.substring(VERSION_PREFIX.length()).trim());
            } else if (s.startsWith(CLASSES_PREFIX)) {
                classCount = Integer.parseInt(s.substring(CLASSES_PREFIX.length()).trim());
            } else {
                return false;
            }
            return true;
        }
    }

    /**
     * Information about a class read from the archive including the name,
     * the serialized class object, the local class, the serialized local class object,
     * and an error message.
     * @param name class name
     * @param archiveClassBytes decoded serialized class object bytes
     * @param localClass reference to the class of the same name in the runtime
     * @param localClassBytes the serialized class object of the localClass
     * @param message a message, usually an error
     */
    private record ClassNameAndBytes(String name,
                                            byte[] archiveClassBytes,
                                            Class<?> localClass,
                                            byte[] localClassBytes,
                                            String message) implements Comparable<ClassNameAndBytes> {

        // Parse a line from the input, decode and cache information about the class
        static ClassNameAndBytes parseLine(String line) {
            String[] p = line.split(":");
            if (p.length < 2) {
                throw new IllegalArgumentException("Malformed input: " + line);
            }
            var className = p[0].trim();
            byte[] archiveClassBytes = HexFormat.of().parseHex(p[1].trim());
            Optional<Class<?>> optClazz = ClassScanner.findClass(className);
            byte[] localClassBytes = optClazz.map(ArchivedClassesTest::serializedClass).orElse(null);
            return new ClassNameAndBytes(className, archiveClassBytes,
                    optClazz.orElse(null), localClassBytes, null);
        }

        // Return a new ClassNameAndBytes with an error message
        ClassNameAndBytes addMessage(String message) {
            return new ClassNameAndBytes(name, archiveClassBytes, localClass, localClassBytes, message);
        }

        // True if there was an error with this class
        boolean seriousError() {
            return message != null && message.startsWith("InvalidClassException");
        }

        // Check for mismatches between the archived and local serialized class objects
        boolean mismatches() {
            return localClass == null ||
                    !Arrays.equals(archiveClassBytes, localClassBytes);
        }

        // Print a detailed comparison, if any, of two serialized streams for the archived class and local class
        private boolean showMismatch(PrintStream out) {
            int offset = -1;
            if (localClassBytes != null && archiveClassBytes != null) {
                offset = Arrays.mismatch(localClassBytes, archiveClassBytes);
                if (offset < 0)
                    return false;
            }
            if (archiveClassBytes != null) {
                out.printf("%s: %s%n", name, message);
                out.printf("Mismatch at offset: 0x%x%n", offset);
                out.println("Archived serialized class:");
                hexPrintSerialStream(archiveClassBytes);
                out.println();
            }
            if (localClassBytes != null) {
                out.println("Current serialized class:");
                hexPrintSerialStream(localClassBytes);
                out.println();
            }
            return true;
        }

        // Compare on error message strings
        @Override
        public int compareTo(ClassNameAndBytes o) {
            return message.compareTo(o.message);
        }

        // Return the name and message
        @Override
        public String toString() {
            return name + ": " + message;
        }
    }

    /**
     * Create the class archive for all classes (filtered) in the JDK.
     * A header is written followed by lines containing a class name
     * and a class object serialized by ObjectOutputStream and hex encoded.
     *
     * @param newArchivePath a file path to write the archive
     * @param classes        the list of classes to archive
     * @throws IOException if an I/O error occurs
     */
    private static void createClassesArchive(Path newArchivePath, TreeSet<Class<?>> classes) throws IOException {
        try (BufferedWriter wr = Files.newBufferedWriter(newArchivePath, StandardOpenOption.CREATE)) {
            ArchiveHeader header = new ArchiveHeader("Archived Serialized Classes",
                    Runtime.version(),
                    classes.size());
            wr.append(header.toString());
            writeSerializedClasses(wr, classes);
        }
    }

    // Write class name and hex dump of serialized class bytes to the output.
    // Module names are written as comment lines.
    private static void writeSerializedClasses(Appendable out, TreeSet<Class<?>> classes) throws IOException {
        Module currModule = null;
        for (var clazz : classes) {
            if (clazz.getModule() != currModule) {
                currModule = clazz.getModule();
                out.append("# Module ")
                        .append(currModule.getName())
                        .append('\n');
            }
            out.append(clazz.getName()).append(": ");
            HexFormat.of().formatHex(out, serializedClass(clazz));
            out.append("\n");
        }
    }

    // Return byte array of a serialized Class object
    private static byte[] serializedClass(Class<?> clazz) {
        try {
            var byteStream = new ByteArrayOutputStream();
            try (var objStream = new ObjectOutputStream(byteStream)) {
                objStream.writeObject(clazz);
            }
            return byteStream.toByteArray();
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Scans all classes in the JDK for those matching a certain set of criteria.
     * Scanning uses ModuleFinder.ofSystem to locate classes.
     * Classes are matched using the following criteria:
     * <p>
     *  - is a public or protected class
     *  - not module-info
     *  - resides in a module that's open to or exported to the unnamed module
     * <p>
     * `findAll` returns a stream of classes
     */
    static class ClassScanner {
        private static final ClassLoader LOADER = ClassScanner.class.getClassLoader();
        private static final Module UNNAMED = LOADER.getUnnamedModule();

        private static Optional<Class<?>> findClass(String name) {
            try {
                Class<?> clazz = Class.forName(name, false, LOADER);
                return Optional.of(clazz);
            } catch (ClassNotFoundException | ExceptionInInitializerError |
                     NoClassDefFoundError | IllegalAccessError ex) {
                return Optional.empty();
            }
        }

        private static boolean isPublicOrProtected(Class<?> clazz) {
            return (clazz.getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0;
        }

        private static boolean isInExportedOrOpenPkg(Class<?> clazz) {
            String pkg = clazz.getPackageName();
            Module mod = clazz.getModule();
            return mod.isExported(pkg, UNNAMED) || mod.isOpen(pkg, UNNAMED);
        }

        private static Stream<String> getClassFiles(ModuleReader f) {
            try {
                return f.list();
            } catch (IOException ioe2) {
                throw new UncheckedIOException(ioe2);
            }
        }

        private static ModuleReader getModuleReader(ModuleReference f) {
            try {
                return f.open();
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        /**
         * Scans classes from ModuleFinder.ofSystem().find("java.se") and returns all classes.
         * Classes are public or protected in an exported or open package.
         * @return Stream of matching classes
         */
        public static Stream<Class<?>> findAll() {
            final ModuleFinder moduleFinder = ModuleFinder.ofSystem();
            return moduleFinder.find("java.se")
                    .get()
                    .descriptor()
                    .requires()
                    .stream()
                    .map(ModuleDescriptor.Requires::name)
                    .map(moduleFinder::find)
                    .map(Optional::get)
                    .map(ClassScanner::getModuleReader)
                    .flatMap(ClassScanner::getClassFiles)
                    .filter(resourceName -> resourceName.endsWith(".class"))
                    .map((name) -> name.replaceFirst("\\.class$", ""))
                    .filter((name) -> !name.equals("module-info"))
                    .map((name) -> name.replaceAll("/", "."))
                    .flatMap((java.lang.String name) -> findClass(name).stream())
                    .filter(ClassScanner::isPublicOrProtected)
                    .filter(ClassScanner::isInExportedOrOpenPkg);}
    }

    // Main so can be run against other versions, (--classpath needs JUnit and HexPrinter classes)
    public static void main(String[] args) throws IOException {
        CLASS_ARCHIVE_PATH = setupClassesArchivePath();
        var t = new ArchivedClassesTest();
        t.deserializeArchivedClasses();
    }
}
