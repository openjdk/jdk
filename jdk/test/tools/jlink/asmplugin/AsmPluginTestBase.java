/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.tools.jlink.internal.PoolImpl;
import jdk.tools.jlink.internal.StringTable;

import jdk.tools.jlink.internal.plugins.asm.AsmPlugin;
import jdk.tools.jlink.internal.plugins.asm.AsmPools;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;

public abstract class AsmPluginTestBase {

    protected static final String TEST_MODULE = "jlink.test";
    protected static final Map<String, List<String>> MODULES;

    private static final Predicate<ModuleData> isClass = r -> r.getPath().endsWith(".class");
    private final List<String> classes;
    private final List<String> resources;
    private final Pool pool;

    static {
        Map<String, List<String>> map = new HashMap<>();
        map.put("jdk.localedata", new ArrayList<>());
        map.put("java.base", new ArrayList<>());
        map.put(TEST_MODULE, new ArrayList<>());
        MODULES = Collections.unmodifiableMap(map);
    }

    public static boolean isImageBuild() {
        Path javaHome = Paths.get(System.getProperty("test.jdk"));
        Path jmods = javaHome.resolve("jmods");
        return Files.exists(jmods);
    }

    public AsmPluginTestBase() {
        try {
            List<String> classes = new ArrayList<>();
            List<String> resources = new ArrayList<>();

            pool = new PoolImpl();

            FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
            Path root = fs.getPath("/modules");

            List<byte[]> moduleInfos = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(root)) {
                for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                    Path p = iterator.next();
                    if (Files.isRegularFile(p)) {
                        String module = p.toString().substring("/modules/".length());
                        module = module.substring(0, module.indexOf("/"));
                        if (MODULES.keySet().contains(module)) {
                            try {
                                boolean isModuleInfo = p.endsWith("module-info.class");
                                if (isModuleInfo) {
                                    moduleInfos.add(Files.readAllBytes(p));
                                }
                                byte[] content = Files.readAllBytes(p);
                                if (p.toString().endsWith(".class") && !isModuleInfo) {
                                    classes.add(toClassName(p));
                                } else if (!isModuleInfo) {
                                    MODULES.get(module).add(toResourceFile(p));
                                }
                                resources.add(toPath(p.toString()));
                                ModuleData res = Pool.newResource(toPath(p.toString()), content);
                                pool.add(res);
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    }
                }
            }
            // There is more than 10 classes in java.base...
            if (classes.size() < 10 || pool.getContent().size() < 10) {
                throw new AssertionError("Not expected resource or class number");
            }

            //Add a fake resource file
            String content = "java.lang.Object";
            String path = "META-INF/services/com.foo.BarProvider";
            ModuleData resFile = Pool.newResource("/" + TEST_MODULE + "/" +
                    path, content.getBytes());
            pool.add(resFile);
            ModuleData fakeInfoFile = Pool.newResource("/" + TEST_MODULE
                    + "/module-info.class", moduleInfos.get(0));
            pool.add(fakeInfoFile);
            MODULES.get(TEST_MODULE).add(path);
            for(Map.Entry<String, List<String>> entry : MODULES.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    throw new AssertionError("No resource file for " + entry.getKey());
                }
            }
            this.classes = Collections.unmodifiableList(classes);
            this.resources = Collections.unmodifiableList(resources);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public List<String> getClasses() {
        return classes;
    }

    public List<String> getResources() {
        return resources;
    }

    public Pool getPool() {
        return pool;
    }

    public abstract void test() throws Exception;

    public Collection<ModuleData> extractClasses(Pool pool) {
        return pool.getContent().stream()
                .filter(isClass)
                .collect(Collectors.toSet());
    }

    public Collection<ModuleData> extractResources(Pool pool) {
        return pool.getContent().stream()
                .filter(isClass.negate())
                .collect(Collectors.toSet());
    }

    public String getModule(String path) {
        int index = path.indexOf("/", 1);
        return path.substring(1, index);
    }

    public String removeModule(String path) {
        int index = path.indexOf("/", 1);
        return path.substring(index + 1);
    }

    private String toPath(String p) {
        return p.substring("/modules".length());
    }

    private String toClassName(Path p) {
        String path = p.toString();
        path = path.substring("/modules/".length());
        // remove module
        if (!path.endsWith("module-info.class")) {
            path = path.substring(path.indexOf("/") + 1);
        }
        path = path.substring(0, path.length() - ".class".length());

        return path;
    }

    private String toResourceFile(Path p) {
        String path = p.toString();
        path = path.substring("/modules/".length());
        // remove module
        path = path.substring(path.indexOf("/") + 1);

        return path;
    }

    public abstract class TestPlugin extends AsmPlugin {

        private AsmPools pools;

        public AsmPools getPools() {
            return pools;
        }

        public boolean isVisitCalled() {
            return pools != null;
        }

        public Pool visit(Pool inResources) throws IOException {
            try {
                Pool outResources = new PoolImpl(inResources.getByteOrder(), new StringTable() {
                    @Override
                    public int addString(String str) {
                        return -1;
                    }

                    @Override
                    public String getString(int id) {
                        return null;
                    }
                });
                visit(inResources, outResources);
                return outResources;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        public void visit(AsmPools pools) {
            if (isVisitCalled()) {
                throw new AssertionError("Visit was called twice");
            }
            this.pools = pools;
            visit();
        }

        public abstract void visit();
        public abstract void test(Pool inResources, Pool outResources) throws Exception;

        @Override
        public String getName() {
            return "test-plugin";
        }
    }
}
