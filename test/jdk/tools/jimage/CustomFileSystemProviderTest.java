/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.spi.ToolProvider;

import jdk.internal.util.OperatingSystem;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8331467
 * @summary verify that an application launches correctly when launched using an application
 *          specific default file system provider that is packaged in a module
 * @modules java.base/jdk.internal.util
 * @library /test/lib
 * @build jdk.test.lib.process.ProcessTools jdk.test.lib.process.OutputAnalyzer
 * @run driver CustomFileSystemProviderTest
 */
public class CustomFileSystemProviderTest {

    private static final ToolProvider JAVAC_TOOL = ToolProvider.findFirst("javac")
            .orElseThrow(() -> new RuntimeException("javac tool not found"));
    private static final ToolProvider JMOD_TOOL = ToolProvider.findFirst("jmod")
            .orElseThrow(() -> new RuntimeException("jmod tool not found"));
    private static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
            .orElseThrow(() -> new RuntimeException("jlink tool not found"));

    private static final String SYS_PROP_DEF_FS_PRV = "java.nio.file.spi.DefaultFileSystemProvider";
    private static final String CUSTOM_MODULE_NAME = "foo";
    private static final String FS_PROVIDER_CLASS_SRC = """
            package foo;
            import java.io.IOException;
            import java.net.URI;
            import java.nio.channels.SeekableByteChannel;
            import java.nio.file.*;
            import java.nio.file.attribute.*;
            import java.nio.file.spi.FileSystemProvider;
            import java.util.*;
            public final class NoOpFSProvider extends FileSystemProvider {
                @Override
                public String getScheme() {
                    return "dummy";
                }
                @Override
                public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
                    return null;
                }
                @Override
                public FileSystem getFileSystem(URI uri) {
                    return null;
                }
                @Override
                public Path getPath(URI uri) {
                    return null;
                }
                @Override
                public SeekableByteChannel newByteChannel(Path path,
                        Set<? extends OpenOption> options,
                        FileAttribute<?>... attrs) throws IOException {
                    return null;
                }
                @Override
                public DirectoryStream<Path> newDirectoryStream(
                        Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
                    return null;
                }
                @Override
                public void createDirectory(Path dir, FileAttribute<?>... attrs)
                    throws IOException {
                    // no-op
                }
                @Override
                public void delete(Path path) throws IOException {
                    // no-op
                }
                @Override
                public void copy(Path source, Path target, CopyOption... options)
                    throws IOException {
                    // no-op
                }
                @Override
                public void move(Path source, Path target, CopyOption... options)
                    throws IOException {
                    // no-op
                }
                @Override
                public boolean isSameFile(Path path, Path path2) throws IOException {
                    return false;
                }
                @Override
                public boolean isHidden(Path path) throws IOException {
                    return false;
                }
                @Override
                public FileStore getFileStore(Path path) throws IOException {
                    return null;
                }
                @Override
                public void checkAccess(Path path, AccessMode... modes) throws IOException {
                    // no-op
                }
                @Override
                public <V extends FileAttributeView> V getFileAttributeView(
                        Path path, Class<V> type, LinkOption... options) {
                    return null;
                }
                @Override
                public <A extends BasicFileAttributes> A readAttributes(
                        Path path, Class<A> type, LinkOption... options) throws IOException {
                    return null;
                }
                @Override
                public Map<String, Object> readAttributes(
                        Path path, String attributes, LinkOption... options) throws IOException {
                    return Map.of();
                }
                @Override
                public void setAttribute(Path path, String attribute,
                        Object value, LinkOption... options)
                        throws IOException {
                    // no-op
                }
            }
            """;


    public static void main(String[] args) throws Exception {
        Path fsProviderJmod = createCustomFSProviderModule();
        System.out.println("jmod created at " + fsProviderJmod);
        Path image = createImage(fsProviderJmod);
        System.out.println("image created at " + image);
        Path javaBinary = OperatingSystem.isWindows()
                ? image.resolve("bin", "java.exe")
                : image.resolve("bin", "java");
        if (Files.notExists(javaBinary)) {
            throw new AssertionError(javaBinary + " is missing");
        }
        System.out.println("launching main class with system-default FileSystemProvider");
        // launch with system-default FileSystemProvider
        OutputAnalyzer oa = ProcessTools.executeCommand(javaBinary.toString(),
                "-m", CUSTOM_MODULE_NAME);
        oa.shouldHaveExitValue(0);
        oa.shouldContain("hello world");
        // now launch with custom default FileSystemProvider
        String sysProp = "-D" + SYS_PROP_DEF_FS_PRV + "=foo.NoOpFSProvider";
        System.out.println("launching main class with custom FileSystemProvider");
        oa = ProcessTools.executeCommand(javaBinary.toString(),
                sysProp, "-m", CUSTOM_MODULE_NAME);
        oa.shouldHaveExitValue(0);
        oa.shouldContain("hello world");
    }

    // creates a module which contains the custom implementation of a FileSystemProvider
    private static Path createCustomFSProviderModule() throws Exception {
        Path compileDestDir = compileModuleClasses();
        Path fsProviderJmod = Path.of(CUSTOM_MODULE_NAME + ".jmod");
        String[] cmd = {"create", "--class-path", compileDestDir.toString(),
                "--main-class", "foo.SimpleApp",
                fsProviderJmod.getFileName().toString()};
        System.out.println("creating module for custom FileSystemProvider: "
                + Arrays.toString(cmd));
        int exitCode = JMOD_TOOL.run(System.out, System.err, cmd);
        if (exitCode != 0) {
            throw new AssertionError("Unexpected exit code: " + exitCode + " from jmod command");
        }
        return fsProviderJmod.toAbsolutePath();
    }

    // compiles the classes that we will be used for creating an application specific module
    private static Path compileModuleClasses() throws Exception {
        Path tmpSrcDir = Files.createTempDirectory(Path.of("."), "8331467-src");
        String pkgName = "foo";
        Files.createDirectories(tmpSrcDir.resolve(pkgName));
        Path fsProviderJavaFile = Files.writeString(
                tmpSrcDir.resolve(pkgName, "NoOpFSProvider.java"), FS_PROVIDER_CLASS_SRC);
        Path moduleInfoJava = Files.writeString(tmpSrcDir.resolve("module-info.java"),
                "module " + CUSTOM_MODULE_NAME + "{}");
        Path mainJavaFile = Files.writeString(tmpSrcDir.resolve(pkgName, "SimpleApp.java"),
                """
                        package foo;
                        public class SimpleApp {
                            public static void main(String[] args) {
                                System.out.println("hello world");
                            }
                        }
                        """);
        Path compileDestDir = Files.createTempDirectory(Path.of("."), "8331467-");
        String[] cmd = {"-d", compileDestDir.toString(), fsProviderJavaFile.toString(),
                moduleInfoJava.toString(), mainJavaFile.toString()};
        System.out.println("compiling classes: " + Arrays.toString(cmd));
        int exitCode = JAVAC_TOOL.run(System.out, System.err, cmd);
        if (exitCode != 0) {
            throw new AssertionError("Unexpected exit code: " + exitCode + " from javac command");
        }
        Path compiledClassFile = compileDestDir.resolve("foo", "NoOpFSProvider.class");
        if (!Files.isRegularFile(compiledClassFile)) {
            throw new AssertionError("compiled class file is missing at " + compiledClassFile);
        }
        return compileDestDir.toAbsolutePath();
    }

    // create a image which includes the application specific module
    private static Path createImage(Path fsProviderJmod) {
        Path image = Path.of("8331467-image");
        String[] cmd = {"--output", image.getFileName().toString(),
                "--add-modules", CUSTOM_MODULE_NAME,
                "--module-path", fsProviderJmod.toString()};
        int exitCode = JLINK_TOOL.run(System.out, System.err, cmd);
        if (exitCode != 0) {
            throw new AssertionError("Unexpected exit code: " + exitCode + " from jlink command");
        }
        return image.toAbsolutePath();
    }
}
