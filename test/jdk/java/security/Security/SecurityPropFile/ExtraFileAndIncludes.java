/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.FileUtils;
import sun.net.www.ParseUtil;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.Security;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/*
 * @test
 * @summary Tests security properties passed through java.security,
 *   java.security.properties or included from other properties files.
 * @bug 4303068 8155246 8292297 8292177 8281658 8319332
 * @modules java.base/sun.net.www
 * @library /test/lib
 * @run main ExtraFileAndIncludes
 */

public class ExtraFileAndIncludes {
    static final String SEPARATOR_THIN = "----------------------------";

    private static void printTestHeader(String testName) {
        System.out.println();
        System.out.println(SEPARATOR_THIN);
        System.out.println(testName);
        System.out.println(SEPARATOR_THIN);
        System.out.println();
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && Executor.RUNNER_ARG.equals(args[0])) {
            // Executed by a test-launched JVM.
            // Force the initialization of java.security.Security.
            Security.getProviders();
            Security.setProperty("postInitTest", "shouldNotRecord");
            System.out.println(FilesManager.LAST_FILE_PROP_NAME + ": " +
                    Security.getProperty(FilesManager.LAST_FILE_PROP_NAME));
            assertTestSecuritySetPropertyShouldNotInclude();
        } else {
            // Executed by the test JVM.
            try (FilesManager filesMgr = new FilesManager()) {
                for (Method m :
                        ExtraFileAndIncludes.class.getDeclaredMethods()) {
                    if (m.getName().startsWith("test")) {
                        printTestHeader(m.getName());
                        Executor.run(m, filesMgr);
                    }
                }
            }
        }
    }

    /*
     * Success cases
     */

    static void testShowSettings(Executor ex, FilesManager filesMgr)
            throws Exception {
        // Sanity test passing the -XshowSettings:security option.
        ex.addJvmArg("-XshowSettings:security");
        ex.setMasterFile(filesMgr.newMasterFile());
        ex.assertSuccess();
        ex.getOutputAnalyzer()
                .shouldContain("Security properties:")
                .shouldContain("Security provider static configuration:")
                .shouldContain("Security TLS configuration");
    }

    static void testIncludeBasic(Executor ex, FilesManager filesMgr)
            throws Exception {
        PropsFile masterFile = filesMgr.newMasterFile();
        ExtraPropsFile extraFile = filesMgr.newExtraFile(ExtraMode.FILE_URI);
        PropsFile file0 = filesMgr.newFile("file0.properties");
        PropsFile file1 = filesMgr.newFile("dir1/file1.properties");
        PropsFile file2 = filesMgr.newFile("dir1/dir2/file2.properties");

        masterFile.addAbsoluteInclude(file0);
        extraFile.addRelativeInclude(file2);
        file2.addAbsoluteInclude(file1);

        ex.setMasterFile(masterFile);
        ex.setExtraFile(extraFile, false);
        ex.assertSuccess();
    }

    static void testRepeatedInclude(Executor ex, FilesManager filesMgr)
            throws Exception {
        PropsFile masterFile = filesMgr.newMasterFile();
        PropsFile file0 = filesMgr.newFile("file0.properties");
        PropsFile file1 = filesMgr.newFile("dir1/file1.properties");

        masterFile.addAbsoluteInclude(file0);
        masterFile.addAbsoluteInclude(file1);
        masterFile.addAbsoluteInclude(file0);
        file1.addRelativeInclude(file0);

        ex.setMasterFile(masterFile);
        ex.assertSuccess();
    }

    static void testIncludeWithOverrideAll(Executor ex, FilesManager filesMgr)
            throws Exception {
        PropsFile masterFile = filesMgr.newMasterFile();
        ExtraPropsFile extraFile = filesMgr.newExtraFile(ExtraMode.HTTP_SERVED);
        PropsFile file0 = filesMgr.newFile("file0.properties");
        PropsFile file1 = filesMgr.newFile("dir1/file1.properties");

        masterFile.addRelativeInclude(file0);
        extraFile.addAbsoluteInclude(file1);

        ex.setMasterFile(masterFile);
        ex.setExtraFile(extraFile, true);
        ex.assertSuccess();
    }

    static void extraPropertiesByHelper(Executor ex, FilesManager filesMgr,
            ExtraMode mode) throws Exception {
        ExtraPropsFile extraFile = filesMgr.newExtraFile(mode);
        PropsFile file0 = filesMgr.newFile("file0.properties");

        extraFile.addRelativeInclude(file0);

        ex.setMasterFile(filesMgr.newMasterFile());
        ex.setExtraFile(extraFile, true);
        ex.assertSuccess();
    }

    static void testExtraPropertiesByPathAbsolute(Executor ex,
            FilesManager filesMgr) throws Exception {
        extraPropertiesByHelper(ex, filesMgr, ExtraMode.PATH_ABS);
    }

    static void testExtraPropertiesByPathRelative(Executor ex,
            FilesManager filesMgr) throws Exception {
        extraPropertiesByHelper(ex, filesMgr, ExtraMode.PATH_REL);
    }

    static void specialCharsIncludes(Executor ex, FilesManager filesMgr,
            char specialChar, ExtraMode extraMode, boolean useRelativeIncludes)
            throws Exception {
        String suffix = specialChar + ".properties";
        ExtraPropsFile extraFile;
        PropsFile file0, file1;
        try {
            extraFile = filesMgr.newExtraFile("extra" + suffix, extraMode);
            file0 = filesMgr.newFile("file0" + suffix);
            file1 = filesMgr.newFile("file1" + suffix);
        } catch (InvalidPathException ipe) {
            // The platform encoding may not allow to create files with some
            // special characters. Skip the test in these cases.
            return;
        }

        if (useRelativeIncludes) {
            extraFile.addRelativeInclude(file0);
        } else {
            extraFile.addAbsoluteInclude(file0);
        }
        extraFile.addAbsoluteInclude(file1);

        ex.setMasterFile(filesMgr.newMasterFile());
        ex.setExtraFile(extraFile, false);
        ex.assertSuccess();
    }

    static void testUnicodeIncludes1(Executor ex, FilesManager filesMgr)
            throws Exception {
        specialCharsIncludes(ex, filesMgr, '\u2022', ExtraMode.PATH_ABS, true);
    }

    static void testUnicodeIncludes2(Executor ex, FilesManager filesMgr)
            throws Exception {
        specialCharsIncludes(ex, filesMgr, '\u2022', ExtraMode.FILE_URI, true);
    }

    static void testUnicodeIncludes3(Executor ex, FilesManager filesMgr)
            throws Exception {
        // Backward compatibility check. Malformed URLs such as
        // file:/tmp/extra•.properties are supported for the extra file.
        // However, relative includes are not allowed in these cases.
        specialCharsIncludes(ex, filesMgr, '\u2022',
                ExtraMode.RAW_FILE_URI1, false);
    }

    static void testUnicodeIncludes4(Executor ex, FilesManager filesMgr)
            throws Exception {
        // Backward compatibility check. Malformed URLs such as
        // file:///tmp/extra•.properties are supported for the extra file.
        // However, relative includes are not allowed in these cases.
        specialCharsIncludes(ex, filesMgr, '\u2022',
                ExtraMode.RAW_FILE_URI2, false);
    }

    static void testSpaceIncludes1(Executor ex, FilesManager filesMgr)
            throws Exception {
        specialCharsIncludes(ex, filesMgr, ' ', ExtraMode.PATH_ABS, true);
    }

    static void testSpaceIncludes2(Executor ex, FilesManager filesMgr)
            throws Exception {
        specialCharsIncludes(ex, filesMgr, ' ', ExtraMode.FILE_URI, true);
    }

    static void testSpaceIncludes3(Executor ex, FilesManager filesMgr)
            throws Exception {
        // Backward compatibility check. Malformed URLs such as
        // file:/tmp/extra .properties are supported for the extra file.
        // However, relative includes are not allowed in these cases.
        specialCharsIncludes(ex, filesMgr, ' ', ExtraMode.RAW_FILE_URI1, false);
    }

    static void testSpaceIncludes4(Executor ex, FilesManager filesMgr)
            throws Exception {
        // Backward compatibility check. Malformed URLs such as
        // file:///tmp/extra .properties are supported for the extra file.
        // However, relative includes are not allowed in these cases.
        specialCharsIncludes(ex, filesMgr, ' ', ExtraMode.RAW_FILE_URI2, false);
    }

    static void notOverrideOnFailureHelper(Executor ex, FilesManager filesMgr,
            String nonExistentExtraFile) throws Exception {
        // An overriding extra properties file that does not exist
        // should not erase properties from the master file.
        ex.setIgnoredExtraFile(nonExistentExtraFile, true);
        ex.setMasterFile(filesMgr.newMasterFile());
        ex.assertSuccess();
        ex.getOutputAnalyzer().shouldContain("unable to load security " +
                "properties from " + nonExistentExtraFile);
    }

    static void testNotOverrideOnEmptyFailure(Executor ex,
            FilesManager filesMgr) throws Exception {
        notOverrideOnFailureHelper(ex, filesMgr, "");
        ex.getOutputAnalyzer()
                .shouldContain("Empty extra properties file path");
    }

    static void testNotOverrideOnURLFailure(Executor ex, FilesManager filesMgr)
            throws Exception {
        notOverrideOnFailureHelper(ex, filesMgr,
                "file:///nonExistentFile.properties");
    }

    static void testNotOverrideOnPathFailure(Executor ex, FilesManager filesMgr)
            throws Exception {
        notOverrideOnFailureHelper(ex, filesMgr, "nonExistentFile.properties");
    }

    static void testNotOverrideOnDirFailure(Executor ex, FilesManager filesMgr)
            throws Exception {
        notOverrideOnFailureHelper(ex, filesMgr, "file:///");
        ex.getOutputAnalyzer().shouldContain("Is a directory");
    }

    static void testNotOverrideOnBadFileURLFailure(Executor ex,
            FilesManager filesMgr) throws Exception {
        notOverrideOnFailureHelper(ex, filesMgr, "file:///%00");
    }

    static void testDisabledExtraPropertiesFile(Executor ex,
            FilesManager filesMgr) throws Exception {
        PropsFile masterFile = filesMgr.newMasterFile();
        PropsFile file0 = filesMgr.newFile("file0.properties");

        masterFile.addRawProperty("security.overridePropertiesFile", "false");

        ex.setMasterFile(masterFile);
        ex.setIgnoredExtraFile(file0.path.toString(), true);
        ex.assertSuccess();
    }

    static final String SECURITY_SET_PROP_FILE_PATH =
            "testSecuritySetPropertyShouldNotInclude.propsFilePath";

    static void testSecuritySetPropertyShouldNotInclude(Executor ex,
            FilesManager filesMgr) throws Exception {
        PropsFile masterFile = filesMgr.newMasterFile();
        PropsFile file0 = filesMgr.newFile("file0.properties");

        ex.addSystemProp(SECURITY_SET_PROP_FILE_PATH, file0.path.toString());
        ex.setMasterFile(masterFile);
        ex.assertSuccess();
    }

    static void assertTestSecuritySetPropertyShouldNotInclude() {
        // This check is executed by the launched JVM.
        String propsFilePath = System.getProperty(SECURITY_SET_PROP_FILE_PATH);
        if (propsFilePath != null) {
            String name = Path.of(propsFilePath).getFileName().toString();
            String setPropInvokeRepr = "Security.setProperty(\"include\", " +
                    "\"" + propsFilePath + "\")";
            try {
                Security.setProperty("include", propsFilePath);
                throw new RuntimeException(setPropInvokeRepr + " was " +
                        "expected to throw IllegalArgumentException.");
            } catch (IllegalArgumentException expected) {}
            if (FilesManager.APPLIED_PROP_VALUE.equals(
                    Security.getProperty(name))) {
                throw new RuntimeException(setPropInvokeRepr + " caused " +
                        "a file inclusion.");
            }
            try {
                Security.getProperty("include");
                throw new RuntimeException("Security.getProperty(\"include\")" +
                        " was expected to throw IllegalArgumentException.");
            } catch (IllegalArgumentException expected) {}
        }
    }

    /*
     * Error cases
     */

    static void testCannotResolveRelativeFromHTTPServed(Executor ex,
            FilesManager filesMgr) throws Exception {
        ExtraPropsFile extraFile = filesMgr.newExtraFile(ExtraMode.HTTP_SERVED);
        PropsFile file0 = filesMgr.newFile("file0.properties");

        extraFile.addRelativeInclude(file0);

        ex.setMasterFile(filesMgr.newMasterFile());
        ex.setExtraFile(extraFile, true);
        ex.assertError("InternalError: Cannot resolve '" + file0.fileName +
                "' relative path when included from a non-regular " +
                "properties file (e.g. HTTP served file)");
    }

    static void testCannotIncludeCycles(Executor ex, FilesManager filesMgr)
            throws Exception {
        PropsFile masterFile = filesMgr.newMasterFile();
        PropsFile file0 = filesMgr.newFile("file0.properties");
        PropsFile file1 = filesMgr.newFile("dir1/file1.properties");

        // Includes chain: master -> file0 -> file1 -> master.
        file1.addRelativeInclude(masterFile);
        file0.addRelativeInclude(file1);
        masterFile.addRelativeInclude(file0);

        ex.setMasterFile(masterFile);
        ex.assertError("Cyclic include");
        ex.getOutputAnalyzer().stderrShouldMatch("\\QInternalError: Cyclic " +
                "include of '\\E[^']+\\Q" + masterFile.fileName + "'\\E");
    }

    static void testCannotIncludeURL(Executor ex, FilesManager filesMgr)
            throws Exception {
        PropsFile masterFile = filesMgr.newMasterFile();
        ExtraPropsFile extraFile = filesMgr.newExtraFile(ExtraMode.HTTP_SERVED);

        masterFile.addRawProperty("include", extraFile.url.toString());

        ex.setMasterFile(masterFile);
        ex.assertError("InternalError: Unable to include 'http://127.0.0.1:");
    }

    static void testCannotIncludeNonexistentFile(Executor ex,
            FilesManager filesMgr) throws Exception {
        PropsFile masterFile = filesMgr.newMasterFile();

        String nonexistentPath = "/nonExistentFile.properties";
        masterFile.addRawProperty("include", nonexistentPath);

        ex.setMasterFile(masterFile);
        ex.assertError(
                "InternalError: Unable to include '" + nonexistentPath + "'");
    }

    static void testMustHaveMasterFile(Executor ex, FilesManager filesMgr)
            throws Exception {
        // Launch a JDK without a master java.security file present.
        ex.assertError("InternalError: Error loading java.security file");
    }

    static void testMustHaveMasterFileEvenWithExtraFile(Executor ex,
            FilesManager filesMgr) throws Exception {
        // Launch a JDK without a master java.security file present, but with an
        // extra file passed. Since the "security.overridePropertiesFile=true"
        // security property is missing, it should fail anyway.
        ex.setExtraFile(filesMgr.newExtraFile(ExtraMode.FILE_URI), true);
        ex.assertError("InternalError: Error loading java.security file");
    }
}

sealed class PropsFile permits ExtraPropsFile {
    protected static final class Include {
        final PropsFile propsFile;
        final String value;

        private Include(PropsFile propsFile, String value) {
            this.propsFile = propsFile;
            this.value = value;
        }

        static Include of(PropsFile propsFile) {
            return new Include(propsFile, propsFile.path.toString());
        }

        static Include of(PropsFile propsFile, String value) {
            return new Include(propsFile, value);
        }

        void assertProcessed(OutputAnalyzer oa) {
            oa.shouldContain("processing include: '" + value + "'");
            oa.shouldContain("finished processing " + propsFile.displayPath);
        }
    }

    protected final List<Include> includes = new ArrayList<>();
    protected final PrintWriter writer;
    protected boolean includedFromExtra = false;
    protected Path displayPath;
    final String fileName;
    final Path path;

    PropsFile(String fileName, Path path) throws IOException {
        this.fileName = fileName;
        this.path = path;
        this.displayPath = path;
        this.writer = new PrintWriter(Files.newOutputStream(path,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND), true);
    }

    private static String escape(String text, boolean escapeSpace) {
        StringBuilder sb = new StringBuilder(text.length());
        CharBuffer cb = CharBuffer.wrap(text);
        while (cb.hasRemaining()) {
            char c = cb.get();
            if (c == '\\' || escapeSpace && c == ' ') {
                sb.append('\\');
            }
            if (Character.UnicodeBlock.of(c) ==
                    Character.UnicodeBlock.BASIC_LATIN) {
                sb.append(c);
            } else {
                sb.append("\\u%04x".formatted((int) c));
            }
        }
        return sb.toString();
    }

    private void addRawProperty(String key, String value, String sep) {
        writer.println(escape(key, true) + sep + escape(value, false));
    }

    protected void addIncludeDefinition(Include include) {
        if (include.propsFile instanceof ExtraPropsFile) {
            throw new RuntimeException("ExtraPropsFile should not be included");
        }
        includes.add(include);
        addRawProperty("include", include.value, " ");
    }

    void addComment(String comment) {
        writer.println("# " + comment);
    }

    void addRawProperty(String key, String value) {
        addRawProperty(key, value, "=");
    }

    void addAbsoluteInclude(PropsFile propsFile) {
        addIncludeDefinition(Include.of(propsFile));
    }

    void addRelativeInclude(PropsFile propsFile) {
        Path rel = path.getParent().relativize(propsFile.path);
        addIncludeDefinition(Include.of(propsFile, rel.toString()));
        propsFile.displayPath = displayPath.getParent().resolve(rel);
    }

    void assertApplied(OutputAnalyzer oa) {
        oa.shouldContain(Executor.INITIAL_PROP_LOG_MSG + fileName + "=" +
                FilesManager.APPLIED_PROP_VALUE);
        for (Include include : includes) {
            include.propsFile.assertApplied(oa);
            include.assertProcessed(oa);
        }
    }

    void assertWasOverwritten(OutputAnalyzer oa) {
        oa.shouldNotContain(Executor.INITIAL_PROP_LOG_MSG + fileName + "=" +
                FilesManager.APPLIED_PROP_VALUE);
        for (Include include : includes) {
            if (!include.propsFile.includedFromExtra) {
                include.propsFile.assertWasOverwritten(oa);
            }
            include.assertProcessed(oa);
        }
    }

    void markAsIncludedFromExtra() {
        includedFromExtra = true;
        for (Include include : includes) {
            include.propsFile.markAsIncludedFromExtra();
        }
    }

    PropsFile getLastFile() {
        return includes.isEmpty() ?
                this : includes.getLast().propsFile.getLastFile();
    }

    void close() {
        writer.close();
    }
}

enum ExtraMode {
    HTTP_SERVED, FILE_URI, RAW_FILE_URI1, RAW_FILE_URI2, PATH_ABS, PATH_REL
}

final class ExtraPropsFile extends PropsFile {
    private static final Path CWD = Path.of(".").toAbsolutePath();
    private final Map<String, String> systemProps = new LinkedHashMap<>();
    private final ExtraMode mode;
    final URI url;

    ExtraPropsFile(String fileName, URI url, Path path, ExtraMode mode)
            throws IOException {
        super(fileName, path);
        this.url = url;
        this.mode = mode;
        if (mode == ExtraMode.PATH_REL) {
            this.displayPath = CWD.relativize(path);
        }
    }

    @Override
    protected void addIncludeDefinition(Include include) {
        if (includes.isEmpty()) {
            String propName = "props.fileName";
            systemProps.put(propName, include.propsFile.fileName);
            include = Include.of(include.propsFile,
                    include.value.replace(include.propsFile.fileName,
                            "${props.none}${" + propName + "}"));
        }
        include.propsFile.markAsIncludedFromExtra();
        super.addIncludeDefinition(include);
    }

    String getSysPropValue() {
        return switch (mode) {
            case HTTP_SERVED -> url.toString();
            case FILE_URI -> path.toUri().toString();
            case RAW_FILE_URI1 -> "file:" + path;
            case RAW_FILE_URI2 ->
                    "file://" + (path.startsWith("/") ? "" : "/") + path;
            case PATH_ABS, PATH_REL -> displayPath.toString();
        };
    }

    Map<String, String> getSystemProperties() {
        return Collections.unmodifiableMap(systemProps);
    }
}

final class FilesManager implements Closeable {
    private static final Path ROOT_DIR = Path.of(
            ExtraFileAndIncludes.class.getSimpleName()).toAbsolutePath();
    private static final Path PROPS_DIR = ROOT_DIR.resolve("properties");
    private static final Path JDK_DIR = ROOT_DIR.resolve("jdk");
    private static final Path MASTER_FILE =
            JDK_DIR.resolve("conf/security/java.security");
    private static final Path MASTER_FILE_TEMPLATE =
            MASTER_FILE.resolveSibling("java.security.template");
    static final String JAVA_EXECUTABLE =
            JDK_DIR.resolve("bin/java").toString();
    static final String LAST_FILE_PROP_NAME = "last-file";
    static final String APPLIED_PROP_VALUE = "applied";

    private final List<PropsFile> createdFiles;
    private final Set<String> fileNamesInUse;
    private final HttpServer httpServer;
    private final URI serverUri;
    private final long masterFileLines;

    FilesManager() throws Exception {
        createdFiles = new ArrayList<>();
        fileNamesInUse = new HashSet<>();
        httpServer = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServer.createContext("/", this::handleRequest);
        InetSocketAddress address = httpServer.getAddress();
        httpServer.start();
        serverUri = new URI("http", null, address.getHostString(),
                address.getPort(), null, null, null);
        copyJDK();
        try (Stream<String> s = Files.lines(MASTER_FILE_TEMPLATE)) {
            masterFileLines = s.count();
        }
    }

    private static void copyJDK() throws Exception {
        Path testJDK = Path.of(Objects.requireNonNull(
                System.getProperty("test.jdk"), "unspecified test.jdk"));
        if (!Files.exists(testJDK)) {
            throw new RuntimeException("test.jdk -> nonexistent JDK");
        }
        Files.createDirectories(JDK_DIR);
        try (Stream<Path> pathStream = Files.walk(testJDK)) {
            pathStream.skip(1).forEach((Path file) -> {
                try {
                    Files.copy(file, JDK_DIR.resolve(testJDK.relativize(file)),
                            StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                }
            });
        }
        Files.move(MASTER_FILE, MASTER_FILE_TEMPLATE);
    }

    private void handleRequest(HttpExchange x) throws IOException {
        String rawPath = x.getRequestURI().getRawPath();
        Path f = ROOT_DIR.resolve(x.getRequestURI().getPath().substring(1));
        int statusCode;
        byte[] responseBody;
        // Check for unescaped space, unresolved parent or backward slash.
        if (rawPath.matches("^.*( |(\\.|%2[Ee]){2}|\\\\|%5[Cc]).*$")) {
            statusCode = HttpURLConnection.HTTP_BAD_REQUEST;
            responseBody = new byte[0];
        } else if (Files.isRegularFile(f)) {
            x.getResponseHeaders().add("Content-type", "text/plain");
            statusCode = HttpURLConnection.HTTP_OK;
            responseBody = Files.readAllBytes(f);
        } else {
            statusCode = HttpURLConnection.HTTP_NOT_FOUND;
            responseBody = new byte[0];
        }
        System.out.println("[" + Instant.now() + "] " +
                getClass().getSimpleName() + ": " +
                x.getRequestMethod() + " " + rawPath + " -> " +
                statusCode + " (" + responseBody.length + " bytes)");
        try (OutputStream responseStream = x.getResponseBody()) {
            x.sendResponseHeaders(statusCode, responseBody.length);
            responseStream.write(responseBody);
        }
    }

    @FunctionalInterface
    private interface PropsFileBuilder {
        PropsFile build(String fileName, Path path) throws IOException;
    }

    private PropsFile newFile(Path path, PropsFileBuilder builder)
            throws IOException {
        String fileName = path.getFileName().toString();
        if (!fileNamesInUse.add(fileName)) {
            // Names must be unique in order for the special
            // property <fileName>=<APPLIED_PROP_VALUE> to work.
            throw new RuntimeException(fileName + " is repeated");
        }
        Files.createDirectories(path.getParent());
        PropsFile propsFile = builder.build(fileName, path);
        propsFile.addComment("Property to determine if this properties file " +
                "was parsed and not overwritten:");
        propsFile.addRawProperty(fileName, APPLIED_PROP_VALUE);
        propsFile.addComment(ExtraFileAndIncludes.SEPARATOR_THIN);
        propsFile.addComment("Property to be overwritten by every properties " +
                "file (master, extra or included):");
        propsFile.addRawProperty(LAST_FILE_PROP_NAME, fileName);
        propsFile.addComment(ExtraFileAndIncludes.SEPARATOR_THIN);
        createdFiles.add(propsFile);
        return propsFile;
    }

    PropsFile newFile(String relPathStr) throws IOException {
        return newFile(PROPS_DIR.resolve(relPathStr), PropsFile::new);
    }

    PropsFile newMasterFile() throws IOException {
        Files.copy(MASTER_FILE_TEMPLATE, MASTER_FILE);
        return newFile(MASTER_FILE, PropsFile::new);
    }

    ExtraPropsFile newExtraFile(ExtraMode mode) throws IOException {
        return newExtraFile("extra.properties", mode);
    }

    ExtraPropsFile newExtraFile(String extraFileName, ExtraMode mode)
            throws IOException {
        return (ExtraPropsFile) newFile(PROPS_DIR.resolve(extraFileName),
                (fileName, path) -> {
                    URI uri = serverUri.resolve(ParseUtil.encodePath(
                            ROOT_DIR.relativize(path).toString()));
                    return new ExtraPropsFile(fileName, uri, path, mode);
                });
    }

    void reportCreatedFiles() throws IOException {
        for (PropsFile propsFile : createdFiles) {
            System.err.println();
            System.err.println(propsFile.path.toString());
            System.err.println(ExtraFileAndIncludes.SEPARATOR_THIN.repeat(3));
            try (Stream<String> lines = Files.lines(propsFile.path)) {
                long lineNumber = 1L;
                Iterator<String> it = lines.iterator();
                while (it.hasNext()) {
                    String line = it.next();
                    if (!propsFile.path.equals(MASTER_FILE) ||
                            lineNumber > masterFileLines) {
                        System.err.println(line);
                    }
                    lineNumber++;
                }
            }
            System.err.println();
        }
    }

    void clear() throws IOException {
        if (!createdFiles.isEmpty()) {
            for (PropsFile propsFile : createdFiles) {
                propsFile.close();
                Files.delete(propsFile.path);
            }
            FileUtils.deleteFileTreeUnchecked(PROPS_DIR);
            createdFiles.clear();
            fileNamesInUse.clear();
        }
    }

    @Override
    public void close() throws IOException {
        clear();
        httpServer.stop(0);
        FileUtils.deleteFileTreeUnchecked(ROOT_DIR);
    }
}

final class Executor {
    static final String RUNNER_ARG = "runner";
    static final String INITIAL_PROP_LOG_MSG = "Initial security property: ";
    private static final String OVERRIDING_LOG_MSG =
            "overriding other security properties files!";
    private static final String[] ALWAYS_UNEXPECTED_LOG_MSGS = {
            "java.lang.AssertionError",
            INITIAL_PROP_LOG_MSG + "postInitTest=shouldNotRecord",
            INITIAL_PROP_LOG_MSG + "include=",
    };
    private static final String JAVA_SEC_PROPS = "java.security.properties";
    private static final String CLASS_PATH = Objects.requireNonNull(
            System.getProperty("test.classes"), "unspecified test.classes");
    private static final String DEBUG_ARG =
            "-Xrunjdwp:transport=dt_socket,address=localhost:8000,suspend=y";
    private final Map<String, String> systemProps = new LinkedHashMap<>(
            Map.of("java.security.debug", "all", "javax.net.debug", "all",
                    // Ensure we get UTF-8 debug outputs in Windows:
                    "stderr.encoding", "UTF-8", "stdout.encoding", "UTF-8"));
    private final List<String> jvmArgs = new ArrayList<>(
            List.of(FilesManager.JAVA_EXECUTABLE, "-enablesystemassertions",
                    // Uncomment DEBUG_ARG to debug test-launched JVMs:
                    "-classpath", CLASS_PATH//, DEBUG_ARG
            ));
    private PropsFile masterPropsFile;
    private ExtraPropsFile extraPropsFile;
    private boolean expectedOverrideAll = false;
    private OutputAnalyzer oa;

    static void run(Method m, FilesManager filesMgr) throws Exception {
        try {
            m.invoke(null, new Executor(), filesMgr);
        } catch (Throwable e) {
            filesMgr.reportCreatedFiles();
            throw e;
        } finally {
            filesMgr.clear();
        }
    }

    void addSystemProp(String key, String value) {
        systemProps.put(key, value);
    }

    private void setRawExtraFile(String extraFile, boolean overrideAll) {
        addSystemProp(JAVA_SEC_PROPS, (overrideAll ? "=" : "") + extraFile);
    }

    void setMasterFile(PropsFile masterPropsFile) {
        this.masterPropsFile = masterPropsFile;
    }

    void setExtraFile(ExtraPropsFile extraPropsFile, boolean overrideAll) {
        this.extraPropsFile = extraPropsFile;
        expectedOverrideAll = overrideAll;
        setRawExtraFile(extraPropsFile.getSysPropValue(), overrideAll);
    }

    void setIgnoredExtraFile(String extraPropsFile, boolean overrideAll) {
        setRawExtraFile(extraPropsFile, overrideAll);
        expectedOverrideAll = false;
    }

    void addJvmArg(String arg) {
        jvmArgs.add(arg);
    }

    private void execute(boolean successExpected) throws Exception {
        List<String> command = new ArrayList<>(jvmArgs);
        Collections.addAll(command, Utils.getTestJavaOpts());
        addSystemPropertiesAsJvmArgs(command);
        command.add(ExtraFileAndIncludes.class.getSimpleName());
        command.add(RUNNER_ARG);
        oa = ProcessTools.executeProcess(new ProcessBuilder(command));
        oa.shouldHaveExitValue(successExpected ? 0 : 1);
        for (String output : ALWAYS_UNEXPECTED_LOG_MSGS) {
            oa.shouldNotContain(output);
        }
    }

    private void addSystemPropertiesAsJvmArgs(List<String> command) {
        Map<String, String> allSystemProps = new LinkedHashMap<>(systemProps);
        if (extraPropsFile != null) {
            allSystemProps.putAll(extraPropsFile.getSystemProperties());
        }
        for (Map.Entry<String, String> e : allSystemProps.entrySet()) {
            command.add("-D" + e.getKey() + "=" + e.getValue());
        }
    }

    void assertSuccess() throws Exception {
        execute(true);

        // Ensure every file was processed by checking a unique property used as
        // a flag. Each file defines <fileName>=applied.
        //
        // For example:
        //
        //   file0
        //   ---------------
        //   file0=applied
        //   include file1
        //
        //   file1
        //   ---------------
        //   file1=applied
        //
        // The assertion would be file0 == applied AND file1 == applied.
        //
        if (extraPropsFile != null) {
            extraPropsFile.assertApplied(oa);
        }
        if (expectedOverrideAll) {
            // When overriding with an extra file, check that neither
            // the master file nor its includes are visible.
            oa.shouldContain(OVERRIDING_LOG_MSG);
            masterPropsFile.assertWasOverwritten(oa);
        } else {
            oa.shouldNotContain(OVERRIDING_LOG_MSG);
            masterPropsFile.assertApplied(oa);
        }

        // Ensure the last included file overwrote a fixed property. Each file
        // defines last-file=<fileName>.
        //
        // For example:
        //
        //   file0
        //   ---------------
        //   last-file=file0
        //   include file1
        //
        //   file1
        //   ---------------
        //   last-file=file1
        //
        // The assertion would be last-file == file1.
        //
        PropsFile lastFile = (extraPropsFile == null ?
                masterPropsFile : extraPropsFile).getLastFile();
        oa.shouldContain(FilesManager.LAST_FILE_PROP_NAME + "=" +
                lastFile.fileName);
        oa.stdoutShouldContain(FilesManager.LAST_FILE_PROP_NAME + ": " +
                lastFile.fileName);
    }

    void assertError(String message) throws Exception {
        execute(false);
        oa.shouldContain(message);
    }

    OutputAnalyzer getOutputAnalyzer() {
        return oa;
    }
}
