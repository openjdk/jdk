/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @requires vm.aot
 * @modules jdk.aot/jdk.tools.jaotc
 *          jdk.aot/jdk.tools.jaotc.collect
 * @run junit/othervm jdk.tools.jaotc.test.collect.ClassSearchTest
 */



package jdk.tools.jaotc.test.collect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.junit.Assert;
import org.junit.Test;

import jdk.tools.jaotc.LoadedClass;
import jdk.tools.jaotc.collect.ClassSearch;
import jdk.tools.jaotc.collect.ClassSource;
import jdk.tools.jaotc.collect.SearchFor;
import jdk.tools.jaotc.collect.SearchPath;
import jdk.tools.jaotc.collect.SourceProvider;

public class ClassSearchTest {
    @Test(expected = InternalError.class)
    public void itShouldThrowExceptionIfNoProvidersAvailable() {
        ClassSearch target = new ClassSearch();
        SearchPath searchPath = new SearchPath();
        target.search(list(new SearchFor("foo")), searchPath);
    }

    @Test
    public void itShouldFindAProviderForEachEntry() {
        Set<String> searched = new HashSet<>();
        ClassSearch target = new ClassSearch();
        target.addProvider(provider("", (name, searchPath) -> {
            searched.add(name);
            return new NoopSource();
        }));
        target.search(searchForList("foo", "bar", "foobar"), null);
        Assert.assertEquals(hashset("foo", "bar", "foobar"), searched);
    }

    private static SourceProvider provider(String supports, BiFunction<String, SearchPath, ClassSource> fn) {
        return new SourceProvider() {
            @Override
            public ClassSource findSource(String name, SearchPath searchPath) {
                return fn.apply(name, searchPath);
            }

            @Override
            public boolean supports(String type) {
                return supports.equals(type);
            }
        };
    }

    @Test
    public void itShouldOnlySearchSupportedProvidersForKnownType() {
        Set<String> visited = new HashSet<>();
        ClassSearch target = new ClassSearch();

        target.addProvider(provider("jar", (name, searchPath) -> {
            visited.add("jar");
            return null;
        }));

        target.addProvider(provider("dir", (name, searchPath) -> {
            visited.add("dir");
            return null;
        }));

        try {
            target.search(list(new SearchFor("some", "dir")), null);
        } catch (InternalError e) {
            // throws because no provider gives a source
        }

        Assert.assertEquals(hashset("dir"), visited);
    }

    @Test(expected = InternalError.class)
    public void itShouldThrowErrorIfMultipleSourcesAreAvailable() {
        ClassSearch target = new ClassSearch();
        target.addProvider(provider("", (name, searchPath) -> consumer -> Assert.fail()));
        target.addProvider(provider("", (name, searchPath) -> consumer -> Assert.fail()));

        target.search(searchForList("somethign"), null);
    }

    @Test
    public void itShouldSearchAllProvidersForUnknownType() {
        Set<String> visited = new HashSet<>();
        ClassSearch target = new ClassSearch();
        target.addProvider(provider("", (name, searchPath) -> {
            visited.add("1");
            return null;
        }));
        target.addProvider(provider("", (name, searchPath) -> {
            visited.add("2");
            return null;
        }));

        try {
            target.search(searchForList("foo"), null);
        } catch (InternalError e) {
            // throws because no provider gives a source
        }

        Assert.assertEquals(hashset("1", "2"), visited);
    }

    @Test
    public void itShouldTryToLoadSaidClassFromClassLoader() {
        Set<String> loaded = new HashSet<>();

        ClassSearch target = new ClassSearch();
        target.addProvider(new SourceProvider() {
            @Override
            public boolean supports(String type) {
                return true;
            }

            @Override
            public ClassSource findSource(String name, SearchPath searchPath) {
                return new ClassSource() {
                    @Override
                    public void eachClass(BiConsumer<String, ClassLoader> consumer) {
                        consumer.accept("foo.Bar", new ClassLoader() {
                            @Override
                            public Class<?> loadClass(String nm) throws ClassNotFoundException {
                                loaded.add(nm);
                                return null;
                            }
                        });
                    }
                };
            }
        });

        java.util.List<LoadedClass> search = target.search(searchForList("/tmp/something"), null);
        Assert.assertEquals(list(new LoadedClass("foo.Bar", null)), search);
    }

    @Test(expected = InternalError.class)
    public void itShouldThrowInternalErrorWhenClassLoaderFails() {
        ClassLoader classLoader = new ClassLoader() {
            @Override
            public Class<?> loadClass(String name1) throws ClassNotFoundException {
                throw new ClassNotFoundException("failed to find " + name1);
            }
        };

        ClassSearch target = new ClassSearch();
        target.addProvider(provider("", (name, searchPath) -> consumer -> consumer.accept("foo.Bar", classLoader)));
        target.search(searchForList("foobar"), null);
    }

    private static List<SearchFor> searchForList(String... entries) {
        List<SearchFor> list = new ArrayList<>();
        for (String entry : entries) {
            list.add(new SearchFor(entry));
        }
        return list;
    }

    @SafeVarargs
    private static <T> List<T> list(T... entries) {
        List<T> list = new ArrayList<>();
        for (T entry : entries) {
            list.add(entry);
        }
        return list;
    }

    @SafeVarargs
    private static <T> Set<T> hashset(T... entries) {
        Set<T> set = new HashSet<>();
        for (T entry : entries) {
            set.add(entry);
        }
        return set;
    }

    private static class NoopSource implements ClassSource {
        @Override
        public void eachClass(BiConsumer<String, ClassLoader> consumer) {
        }
    }
}
