/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.javac.platform;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import javax.annotation.processing.Processor;

import com.sun.source.util.Plugin;
import com.sun.tools.javac.jvm.Target;

/** PlatformProvider for JDK N.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class JDKPlatformProvider implements PlatformProvider {

    @Override
    public Iterable<String> getSupportedPlatformNames() {
        return SUPPORTED_JAVA_PLATFORM_VERSIONS;
    }

    @Override
    public PlatformDescription getPlatform(String platformName, String options) {
        return new PlatformDescriptionImpl(platformName);
    }

    private static final String[] symbolFileLocation = { "lib", "ct.sym" };

    private static final Set<String> SUPPORTED_JAVA_PLATFORM_VERSIONS;

    static {
        SUPPORTED_JAVA_PLATFORM_VERSIONS = new TreeSet<>();
        Path ctSymFile = findCtSym();
        if (Files.exists(ctSymFile)) {
            try (FileSystem fs = FileSystems.newFileSystem(ctSymFile, null);
                 DirectoryStream<Path> dir =
                         Files.newDirectoryStream(fs.getRootDirectories().iterator().next())) {
                for (Path section : dir) {
                    for (char ver : section.getFileName().toString().toCharArray()) {
                        String verString = Character.toString(ver);
                        Target t = Target.lookup(verString);

                        if (t != null) {
                            SUPPORTED_JAVA_PLATFORM_VERSIONS.add(targetNumericVersion(t));
                        }
                    }
                }
            } catch (IOException | ProviderNotFoundException ex) {
            }
        }
    }

    private static String targetNumericVersion(Target target) {
        return Integer.toString(target.ordinal() - Target.JDK1_1.ordinal() + 1);
    }

    static class PlatformDescriptionImpl implements PlatformDescription {

        private final Map<Path, FileSystem> ctSym2FileSystem = new HashMap<>();
        private final String version;

        PlatformDescriptionImpl(String version) {
            this.version = version;
        }

        @Override
        public Collection<Path> getPlatformPath() {
            List<Path> paths = new ArrayList<>();
            Path file = findCtSym();
            // file == ${jdk.home}/lib/ct.sym
            if (Files.exists(file)) {
                FileSystem fs = ctSym2FileSystem.get(file);
                if (fs == null) {
                    try {
                        ctSym2FileSystem.put(file, fs = FileSystems.newFileSystem(file, null));
                    } catch (IOException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
                Path root = fs.getRootDirectories().iterator().next();
                try (DirectoryStream<Path> dir = Files.newDirectoryStream(root)) {
                    for (Path section : dir) {
                        if (section.getFileName().toString().contains(version)) {
                            Path systemModules = section.resolve("system-modules");

                            if (Files.isRegularFile(systemModules)) {
                                Path modules =
                                        FileSystems.getFileSystem(URI.create("jrt:/"))
                                                   .getPath("modules");
                                try (Stream<String> lines =
                                        Files.lines(systemModules, Charset.forName("UTF-8"))) {
                                    lines.map(line -> modules.resolve(line))
                                         .filter(mod -> Files.exists(mod))
                                         .forEach(mod -> paths.add(mod));
                                }
                            } else {
                                paths.add(section);
                            }
                        }
                    }
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            } else {
                throw new IllegalStateException("Cannot find ct.sym!");
            }
            return paths;
        }

        @Override
        public String getSourceVersion() {
            return version;
        }

        @Override
        public String getTargetVersion() {
            return version;
        }

        @Override
        public List<PluginInfo<Processor>> getAnnotationProcessors() {
            return Collections.emptyList();
        }

        @Override
        public List<PluginInfo<Plugin>> getPlugins() {
            return Collections.emptyList();
        }

        @Override
        public List<String> getAdditionalOptions() {
            return Collections.emptyList();
        }

        @Override
        public void close() throws IOException {
            for (FileSystem fs : ctSym2FileSystem.values()) {
                fs.close();
            }
            ctSym2FileSystem.clear();
        }

    }

    static Path findCtSym() {
        String javaHome = System.getProperty("java.home");
        Path file = Paths.get(javaHome);
        // file == ${jdk.home}
        for (String name : symbolFileLocation)
            file = file.resolve(name);
        return file;
    }

}
