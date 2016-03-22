/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.jdeps;

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Dependencies;
import com.sun.tools.classfile.Dependency;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sun.tools.jdeps.Module.*;
import static com.sun.tools.jdeps.ModulePaths.SystemModulePath.JAVA_BASE;

public class DependencyFinder {
    private final List<Archive> roots = new ArrayList<>();
    private final List<Archive> classpaths = new ArrayList<>();
    private final List<Module> modulepaths = new ArrayList<>();
    private final List<String> classes = new ArrayList<>();
    private final boolean compileTimeView;

    DependencyFinder(boolean compileTimeView) {
        this.compileTimeView = compileTimeView;
    }

    /*
     * Adds a class name to the root set
     */
    void addClassName(String cn) {
        classes.add(cn);
    }

    /*
     * Adds the archive of the given path to the root set
     */
    void addRoot(Path path) {
        addRoot(Archive.getInstance(path));
    }

    /*
     * Adds the given archive to the root set
     */
    void addRoot(Archive archive) {
        Objects.requireNonNull(archive);
        if (!roots.contains(archive))
            roots.add(archive);
    }

    /**
     * Add an archive specified in the classpath.
     */
    void addClassPathArchive(Path path) {
        addClassPathArchive(Archive.getInstance(path));
    }

    /**
     * Add an archive specified in the classpath.
     */
    void addClassPathArchive(Archive archive) {
        Objects.requireNonNull(archive);
        classpaths.add(archive);
    }

    /**
     * Add an archive specified in the modulepath.
     */
    void addModule(Module m) {
        Objects.requireNonNull(m);
        modulepaths.add(m);
    }

    /**
     * Returns the root set.
     */
    List<Archive> roots() {
        return roots;
    }

    /**
     * Returns a stream of all archives including the root set, module paths,
     * and classpath.
     *
     * This only returns the archives with classes parsed.
     */
    Stream<Archive> archives() {
        Stream<Archive> archives = Stream.concat(roots.stream(), modulepaths.stream());
        archives = Stream.concat(archives, classpaths.stream());
        return archives.filter(a -> !a.isEmpty())
                       .distinct();
    }

    /**
     * Finds dependencies
     *
     * @param apiOnly  API only
     * @param maxDepth depth of transitive dependency analysis; zero indicates
     * @throws IOException
     */
    void findDependencies(JdepsFilter filter, boolean apiOnly, int maxDepth)
            throws IOException
    {
        Dependency.Finder finder =
                apiOnly ? Dependencies.getAPIFinder(AccessFlags.ACC_PROTECTED)
                        : Dependencies.getClassDependencyFinder();

        // list of archives to be analyzed
        Set<Archive> roots = new LinkedHashSet<>(this.roots);

        // include java.base in root set
        roots.add(JAVA_BASE);

        // If -include pattern specified, classes may be in module path or class path.
        // To get compile time view analysis, all classes are analyzed.
        // add all modules except JDK modules to root set
        modulepaths.stream()
                   .filter(filter::matches)
                   .forEach(roots::add);

        // add classpath to the root set
        classpaths.stream()
                .filter(filter::matches)
                .forEach(roots::add);

        // transitive dependency
        int depth = maxDepth > 0 ? maxDepth : Integer.MAX_VALUE;

        // Work queue of names of classfiles to be searched.
        // Entries will be unique, and for classes that do not yet have
        // dependencies in the results map.
        ConcurrentLinkedDeque<String> deque = new ConcurrentLinkedDeque<>();
        ConcurrentSkipListSet<String> doneClasses = new ConcurrentSkipListSet<>();

        TaskExecutor executor = new TaskExecutor(finder, filter, apiOnly, deque, doneClasses);
        try {
            // get the immediate dependencies of the input files
            for (Archive source : roots) {
                executor.task(source, deque);
            }
            executor.waitForTasksCompleted();

            List<Archive> archives = Stream.concat(Stream.concat(roots.stream(),
                    modulepaths.stream()),
                    classpaths.stream())
                    .collect(Collectors.toList());

            // Additional pass to find archive where dependences are identified
            // and also any specified classes, if any.
            // If -R is specified, perform transitive dependency analysis.
            Deque<String> unresolved = new LinkedList<>(classes);
            do {
                String name;
                while ((name = unresolved.poll()) != null) {
                    if (doneClasses.contains(name)) {
                        continue;
                    }
                    if (compileTimeView) {
                        final String cn = name + ".class";
                        // parse all classes in the source archive
                        Optional<Archive> source = archives.stream()
                                .filter(a -> a.reader().entries().contains(cn))
                                .findFirst();
                        trace("%s compile time view %s%n", name, source.map(Archive::getName).orElse(" not found"));
                        if (source.isPresent()) {
                            executor.runTask(source.get(), deque);
                        }
                    }
                    ClassFile cf = null;
                    for (Archive archive : archives) {
                        cf = archive.reader().getClassFile(name);

                        if (cf != null) {
                            String classFileName;
                            try {
                                classFileName = cf.getName();
                            } catch (ConstantPoolException e) {
                                throw new Dependencies.ClassFileError(e);
                            }
                            if (!doneClasses.contains(classFileName)) {
                                // if name is a fully-qualified class name specified
                                // from command-line, this class might already be parsed
                                doneClasses.add(classFileName);
                                for (Dependency d : finder.findDependencies(cf)) {
                                    if (depth == 0) {
                                        // ignore the dependency
                                        archive.addClass(d.getOrigin());
                                        break;
                                    } else if (filter.accepts(d) && filter.accept(archive)) {
                                        // continue analysis on non-JDK classes
                                        archive.addClass(d.getOrigin(), d.getTarget());
                                        String cn = d.getTarget().getName();
                                        if (!doneClasses.contains(cn) && !deque.contains(cn)) {
                                            deque.add(cn);
                                        }
                                    } else {
                                        // ensure that the parsed class is added the archive
                                        archive.addClass(d.getOrigin());
                                    }
                                }
                            }
                            break;
                        }
                    }
                    if (cf == null) {
                        doneClasses.add(name);
                    }
                }
                unresolved = deque;
                deque = new ConcurrentLinkedDeque<>();
            } while (!unresolved.isEmpty() && depth-- > 0);
        } finally {
            executor.shutdown();
        }
     }

    /**
     * TaskExecutor creates FutureTask to analyze all classes in a given archive
     */
    private class TaskExecutor {
        final ExecutorService pool;
        final Dependency.Finder finder;
        final JdepsFilter filter;
        final boolean apiOnly;
        final Set<String> doneClasses;
        final Map<Archive, FutureTask<Void>> tasks = new HashMap<>();

        TaskExecutor(Dependency.Finder finder,
                     JdepsFilter filter,
                     boolean apiOnly,
                     ConcurrentLinkedDeque<String> deque,
                     Set<String> doneClasses) {
            this.pool = Executors.newFixedThreadPool(2);
            this.finder = finder;
            this.filter = filter;
            this.apiOnly = apiOnly;
            this.doneClasses = doneClasses;
        }

        /**
         * Creates a new task to analyze class files in the given archive.
         * The dependences are added to the given deque for analysis.
         */
        FutureTask<Void> task(Archive archive, final ConcurrentLinkedDeque<String> deque) {
            trace("parsing %s %s%n", archive.getName(), archive.path());
            FutureTask<Void> task = new FutureTask<Void>(new Callable<Void>() {
                public Void call() throws Exception {
                    for (ClassFile cf : archive.reader().getClassFiles()) {
                        String classFileName;
                        try {
                            classFileName = cf.getName();
                        } catch (ConstantPoolException e) {
                            throw new Dependencies.ClassFileError(e);
                        }

                        // tests if this class matches the -include
                        String cn = classFileName.replace('/', '.');
                        if (!filter.matches(cn))
                            continue;

                        // if -apionly is specified, analyze only exported and public types
                        if (apiOnly && !(isExported(archive, cn) && cf.access_flags.is(AccessFlags.ACC_PUBLIC)))
                            continue;

                        if (!doneClasses.contains(classFileName)) {
                            doneClasses.add(classFileName);
                        }

                        for (Dependency d : finder.findDependencies(cf)) {
                            if (filter.accepts(d) && filter.accept(archive)) {
                                String name = d.getTarget().getName();
                                if (!doneClasses.contains(name) && !deque.contains(name)) {
                                    deque.add(name);
                                }
                                archive.addClass(d.getOrigin(), d.getTarget());
                            } else {
                                // ensure that the parsed class is added the archive
                                archive.addClass(d.getOrigin());
                            }
                        }
                    }
                    return null;
                }
            });
            tasks.put(archive, task);
            pool.submit(task);
            return task;
        }

        /*
         * This task will parse all class files of the given archive, if it's a new task.
         * This method waits until the task is completed.
         */
        void runTask(Archive archive, final ConcurrentLinkedDeque<String> deque) {
            if (tasks.containsKey(archive))
                return;

            FutureTask<Void> task = task(archive, deque);
            try {
                // wait for completion
                task.get();
            } catch (InterruptedException|ExecutionException e) {
                throw new Error(e);
            }
        }

        /*
         * Waits until all submitted tasks are completed.
         */
        void waitForTasksCompleted() {
            try {
                for (FutureTask<Void> t : tasks.values()) {
                    if (t.isDone())
                        continue;

                    // wait for completion
                    t.get();
                }
            } catch (InterruptedException|ExecutionException e) {
                throw new Error(e);
            }
        }

        /*
         * Shutdown the executor service.
         */
        void shutdown() {
            pool.shutdown();
        }

        /**
         * Tests if the given class name is exported by the given archive.
         *
         * All packages are exported in unnamed module.
         */
        private boolean isExported(Archive archive, String classname) {
            int i = classname.lastIndexOf('.');
            String pn = i > 0 ? classname.substring(0, i) : "";
            return archive.getModule().isExported(pn);
        }
    }
}
