/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.module;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor.Requires;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import jdk.internal.module.Checks;
import jdk.internal.module.ConfigurableModuleFinder;
import jdk.internal.perf.PerfCounter;


/**
 * A {@code ModuleFinder} that locates modules on the file system by searching
 * a sequence of directories or packaged modules.
 *
 * The {@code ModuleFinder} can be configured to work in either the run-time
 * or link-time phases. In both cases it locates modular JAR and exploded
 * modules. When configured for link-time then it additionally locates
 * modules in JMOD files.
 */

class ModulePath implements ConfigurableModuleFinder {
    private static final String MODULE_INFO = "module-info.class";

    // the entries on this module path
    private final Path[] entries;
    private int next;

    // true if in the link phase
    private boolean isLinkPhase;

    // map of module name to module reference map for modules already located
    private final Map<String, ModuleReference> cachedModules = new HashMap<>();

    ModulePath(Path... entries) {
        this.entries = entries.clone();
        for (Path entry : this.entries) {
            Objects.requireNonNull(entry);
        }
    }

    @Override
    public void configurePhase(Phase phase) {
        isLinkPhase = (phase == Phase.LINK_TIME);
    }

    @Override
    public Optional<ModuleReference> find(String name) {
        Objects.requireNonNull(name);

        // try cached modules
        ModuleReference m = cachedModules.get(name);
        if (m != null)
            return Optional.of(m);

        // the module may not have been encountered yet
        while (hasNextEntry()) {
            scanNextEntry();
            m = cachedModules.get(name);
            if (m != null)
                return Optional.of(m);
        }
        return Optional.empty();
    }

    @Override
    public Set<ModuleReference> findAll() {
        // need to ensure that all entries have been scanned
        while (hasNextEntry()) {
            scanNextEntry();
        }
        return cachedModules.values().stream().collect(Collectors.toSet());
    }

    /**
     * Returns {@code true} if there are additional entries to scan
     */
    private boolean hasNextEntry() {
        return next < entries.length;
    }

    /**
     * Scans the next entry on the module path. A no-op if all entries have
     * already been scanned.
     *
     * @throws FindException if an error occurs scanning the next entry
     */
    private void scanNextEntry() {
        if (hasNextEntry()) {

            long t0 = System.nanoTime();

            Path entry = entries[next];
            Map<String, ModuleReference> modules = scan(entry);
            next++;

            // update cache, ignoring duplicates
            int initialSize = cachedModules.size();
            for (Map.Entry<String, ModuleReference> e : modules.entrySet()) {
                cachedModules.putIfAbsent(e.getKey(), e.getValue());
            }

            // update counters
            int added = cachedModules.size() - initialSize;
            moduleCount.add(added);

            scanTime.addElapsedTimeFrom(t0);
        }
    }


    /**
     * Scan the given module path entry. If the entry is a directory then it is
     * a directory of modules or an exploded module. If the entry is a regular
     * file then it is assumed to be a packaged module.
     *
     * @throws FindException if an error occurs scanning the entry
     */
    private Map<String, ModuleReference> scan(Path entry) {

        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(entry, BasicFileAttributes.class);
        } catch (NoSuchFileException e) {
            return Collections.emptyMap();
        } catch (IOException ioe) {
            throw new FindException(ioe);
        }

        try {

            if (attrs.isDirectory()) {
                Path mi = entry.resolve(MODULE_INFO);
                if (!Files.exists(mi)) {
                    // does not exist or unable to determine so assume a
                    // directory of modules
                    return scanDirectory(entry);
                }
            }

            if (attrs.isRegularFile() || attrs.isDirectory()) {
                // packaged or exploded module
                ModuleReference mref = readModule(entry, attrs);
                if (mref != null) {
                    String name = mref.descriptor().name();
                    return Collections.singletonMap(name, mref);
                }
            }

            // not recognized
            throw new FindException("Unrecognized module: " + entry);

        } catch (IOException ioe) {
            throw new FindException(ioe);
        }
    }


    /**
     * Scans the given directory for packaged or exploded modules.
     *
     * @return a map of module name to ModuleReference for the modules found
     *         in the directory
     *
     * @throws IOException if an I/O error occurs
     * @throws FindException if an error occurs scanning the entry or the
     *         directory contains two or more modules with the same name
     */
    private Map<String, ModuleReference> scanDirectory(Path dir)
        throws IOException
    {
        // The map of name -> mref of modules found in this directory.
        Map<String, ModuleReference> nameToReference = new HashMap<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                BasicFileAttributes attrs;
                try {
                    attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                } catch (NoSuchFileException ignore) {
                    // file has been removed or moved, ignore for now
                    continue;
                }

                ModuleReference mref = readModule(entry, attrs);

                // module found
                if (mref != null) {

                    // can have at most one version of a module in the directory
                    String name = mref.descriptor().name();
                    if (nameToReference.put(name, mref) != null) {
                        throw new FindException("Two versions of module "
                                + name + " found in " + dir);
                    }

                }

            }
        }

        return nameToReference;
    }


    /**
     * Locates a packaged or exploded module, returning a {@code ModuleReference}
     * to the module. Returns {@code null} if the module is not recognized
     * as a packaged or exploded module.
     *
     * @throws IOException if an I/O error occurs
     * @throws FindException if an error occurs parsing the module descriptor
     */
    private ModuleReference readModule(Path entry, BasicFileAttributes attrs)
        throws IOException
    {
        try {

            ModuleReference mref = null;
            if (attrs.isDirectory()) {
                mref = readExplodedModule(entry);
            } if (attrs.isRegularFile()) {
                if (entry.toString().endsWith(".jar")) {
                    mref = readJar(entry);
                } else if (isLinkPhase && entry.toString().endsWith(".jmod")) {
                    mref = readJMod(entry);
                }
            }
            return mref;

        } catch (InvalidModuleDescriptorException e) {
            throw new FindException("Error reading module: " + entry, e);
        }
    }


    // -- jmod files --

    private Set<String> jmodPackages(ZipFile zf) {
        return zf.stream()
            .filter(e -> e.getName().startsWith("classes/") &&
                    e.getName().endsWith(".class"))
            .map(e -> toPackageName(e))
            .filter(pkg -> pkg.length() > 0) // module-info
            .distinct()
            .collect(Collectors.toSet());
    }

    /**
     * Returns a {@code ModuleReference} to a module in jmod file on the
     * file system.
     */
    private ModuleReference readJMod(Path file) throws IOException {
        try (ZipFile zf = new ZipFile(file.toString())) {
            ZipEntry ze = zf.getEntry("classes/" + MODULE_INFO);
            if (ze == null) {
                throw new IOException(MODULE_INFO + " is missing: " + file);
            }
            ModuleDescriptor md;
            try (InputStream in = zf.getInputStream(ze)) {
                md = ModuleDescriptor.read(in, () -> jmodPackages(zf));
            }
            return ModuleReferences.newJModModule(md, file);
        }
    }


    // -- JAR files --

    private static final String SERVICES_PREFIX = "META-INF/services/";

    /**
     * Returns a container with the service type corresponding to the name of
     * a services configuration file.
     *
     * For example, if called with "META-INF/services/p.S" then this method
     * returns a container with the value "p.S".
     */
    private Optional<String> toServiceName(String cf) {
        assert cf.startsWith(SERVICES_PREFIX);
        int index = cf.lastIndexOf("/") + 1;
        if (index < cf.length()) {
            String prefix = cf.substring(0, index);
            if (prefix.equals(SERVICES_PREFIX)) {
                String sn = cf.substring(index);
                if (Checks.isJavaIdentifier(sn))
                    return Optional.of(sn);
            }
        }
        return Optional.empty();
    }

    /**
     * Reads the next line from the given reader and trims it of comments and
     * leading/trailing white space.
     *
     * Returns null if the reader is at EOF.
     */
    private String nextLine(BufferedReader reader) throws IOException {
        String ln = reader.readLine();
        if (ln != null) {
            int ci = ln.indexOf('#');
            if (ci >= 0)
                ln = ln.substring(0, ci);
            ln = ln.trim();
        }
        return ln;
    }

    /**
     * Treat the given JAR file as a module as follows:
     *
     * 1. The module name (and optionally the version) is derived from the file
     *    name of the JAR file
     * 2. The packages of all .class files in the JAR file are exported
     * 3. It has no module-private/concealed packages
     * 4. The contents of any META-INF/services configuration files are mapped
     *    to "provides" declarations
     * 5. The Main-Class attribute in the main attributes of the JAR manifest
     *    is mapped to the module descriptor mainClass
     *
     * @apiNote This needs to move to somewhere where it can be used by tools,
     * maybe even a standard API if automatic modules are a Java SE feature.
     */
    private ModuleDescriptor deriveModuleDescriptor(JarFile jf)
        throws IOException
    {
        // Derive module name and version from JAR file name

        String fn = jf.getName();
        int i = fn.lastIndexOf(File.separator);
        if (i != -1)
            fn = fn.substring(i+1);

        // drop .jar
        String mn = fn.substring(0, fn.length()-4);
        String vs = null;

        // find first occurrence of -${NUMBER}. or -${NUMBER}$
        Matcher matcher = Pattern.compile("-(\\d+(\\.|$))").matcher(mn);
        if (matcher.find()) {
            int start = matcher.start();

            // attempt to parse the tail as a version string
            try {
                String tail = mn.substring(start+1);
                ModuleDescriptor.Version.parse(tail);
                vs = tail;
            } catch (IllegalArgumentException ignore) { }

            mn = mn.substring(0, start);
        }

        // finally clean up the module name
        mn =  mn.replaceAll("[^A-Za-z0-9]", ".")  // replace non-alphanumeric
                .replaceAll("(\\.)(\\1)+", ".")   // collapse repeating dots
                .replaceAll("^\\.", "")           // drop leading dots
                .replaceAll("\\.$", "");          // drop trailing dots


        // Builder throws IAE if module name is empty or invalid
        ModuleDescriptor.Builder builder
            = new ModuleDescriptor.Builder(mn, true)
                .requires(Requires.Modifier.MANDATED, "java.base");
        if (vs != null)
            builder.version(vs);

        // scan the entries in the JAR file to locate the .class and service
        // configuration file
        Stream<String> stream = jf.stream()
            .map(e -> e.getName())
            .filter(e -> (e.endsWith(".class") || e.startsWith(SERVICES_PREFIX)))
            .distinct();
        Map<Boolean, Set<String>> map
            = stream.collect(Collectors.partitioningBy(s -> s.endsWith(".class"),
                             Collectors.toSet()));
        Set<String> classFiles = map.get(Boolean.TRUE);
        Set<String> configFiles = map.get(Boolean.FALSE);

        // all packages are exported
        classFiles.stream()
            .map(c -> toPackageName(c))
            .distinct()
            .forEach(p -> builder.exports(p));

        // map names of service configuration files to service names
        Set<String> serviceNames = configFiles.stream()
            .map(this::toServiceName)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());

        // parse each service configuration file
        for (String sn : serviceNames) {
            JarEntry entry = jf.getJarEntry(SERVICES_PREFIX + sn);
            Set<String> providerClasses = new HashSet<>();
            try (InputStream in = jf.getInputStream(entry)) {
                BufferedReader reader
                    = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                String cn;
                while ((cn = nextLine(reader)) != null) {
                    if (Checks.isJavaIdentifier(cn)) {
                        providerClasses.add(cn);
                    }
                }
            }
            if (!providerClasses.isEmpty())
                builder.provides(sn, providerClasses);
        }

        // Main-Class attribute if it exists
        Manifest man = jf.getManifest();
        if (man != null) {
            Attributes attrs = man.getMainAttributes();
            String mainClass = attrs.getValue(Attributes.Name.MAIN_CLASS);
            if (mainClass != null)
                builder.mainClass(mainClass);
        }

        return builder.build();
    }

    private Set<String> jarPackages(JarFile jf) {
        return jf.stream()
            .filter(e -> e.getName().endsWith(".class"))
            .map(e -> toPackageName(e))
            .filter(pkg -> pkg.length() > 0)   // module-info
            .distinct()
            .collect(Collectors.toSet());
    }

    /**
     * Returns a {@code ModuleReference} to a module in modular JAR file on
     * the file system.
     */
    private ModuleReference readJar(Path file) throws IOException {
        try (JarFile jf = new JarFile(file.toString())) {

            ModuleDescriptor md;
            JarEntry entry = jf.getJarEntry(MODULE_INFO);
            if (entry == null) {

                // no module-info.class so treat it as automatic module
                try {
                    md = deriveModuleDescriptor(jf);
                } catch (IllegalArgumentException iae) {
                    throw new FindException(
                        "Unable to derive module descriptor for: "
                        + jf.getName(), iae);
                }

            } else {
                md = ModuleDescriptor.read(jf.getInputStream(entry),
                                           () -> jarPackages(jf));
            }

            return ModuleReferences.newJarModule(md, file);
        }
    }


    // -- exploded directories --

    private Set<String> explodedPackages(Path dir) {
        try {
            return Files.find(dir, Integer.MAX_VALUE,
                              ((path, attrs) -> attrs.isRegularFile() &&
                               path.toString().endsWith(".class")))
                .map(path -> toPackageName(dir.relativize(path)))
                .filter(pkg -> pkg.length() > 0)   // module-info
                .distinct()
                .collect(Collectors.toSet());
        } catch (IOException x) {
            throw new UncheckedIOException(x);
        }
    }

    /**
     * Returns a {@code ModuleReference} to an exploded module on the file
     * system or {@code null} if {@code module-info.class} not found.
     */
    private ModuleReference readExplodedModule(Path dir) throws IOException {
        Path mi = dir.resolve(MODULE_INFO);
        ModuleDescriptor md;
        try (InputStream in = Files.newInputStream(mi)) {
            md = ModuleDescriptor.read(new BufferedInputStream(in),
                                       () -> explodedPackages(dir));
        } catch (NoSuchFileException e) {
            // for now
            return null;
        }
        return ModuleReferences.newExplodedModule(md, dir);
    }


    //

    // p/q/T.class => p.q
    private String toPackageName(String cn) {
        assert cn.endsWith(".class");
        int start = 0;
        int index = cn.lastIndexOf("/");
        if (index > start) {
            return cn.substring(start, index).replace('/', '.');
        } else {
            return "";
        }
    }

    private String toPackageName(ZipEntry entry) {
        String name = entry.getName();
        assert name.endsWith(".class");
        // jmod classes in classes/, jar in /
        int start = name.startsWith("classes/") ? 8 : 0;
        int index = name.lastIndexOf("/");
        if (index > start) {
            return name.substring(start, index).replace('/', '.');
        } else {
            return "";
        }
    }

    private String toPackageName(Path path) {
        String name = path.toString();
        assert name.endsWith(".class");
        int index = name.lastIndexOf(File.separatorChar);
        if (index != -1) {
            return name.substring(0, index).replace(File.separatorChar, '.');
        } else {
            return "";
        }
    }

    private static final PerfCounter scanTime
        = PerfCounter.newPerfCounter("jdk.module.finder.modulepath.scanTime");
    private static final PerfCounter moduleCount
        = PerfCounter.newPerfCounter("jdk.module.finder.modulepath.modules");
}
