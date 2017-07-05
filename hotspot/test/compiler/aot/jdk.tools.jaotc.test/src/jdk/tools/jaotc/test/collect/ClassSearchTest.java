/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jaotc.test.collect;


import jdk.tools.jaotc.LoadedClass;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public class ClassSearchTest {
    @Test(expected = InternalError.class)
    public void itShouldThrowExceptionIfNoProvidersAvailable() {
        ClassSearch target = new ClassSearch();
        SearchPath searchPath = new SearchPath();
        target.search(list("foo"), searchPath);
    }

    @Test
    public void itShouldFindAProviderForEachEntry() {
        Set<String> searched = new HashSet<>();
        ClassSearch target = new ClassSearch();
        target.addProvider(new SourceProvider() {
            @Override
            public ClassSource findSource(String name, SearchPath searchPath) {
                searched.add(name);
                return new NoopSource();
            }
        });
        target.search(list("foo", "bar", "foobar"), null);
        Assert.assertEquals(hashset("foo", "bar", "foobar"), searched);
    }

    @Test
    public void itShouldSearchAllProviders() {
        Set<String> visited = new HashSet<>();
        ClassSearch target = new ClassSearch();
        target.addProvider((name, searchPath) -> {
            visited.add("1");
            return null;
        });
        target.addProvider((name, searchPath) -> {
            visited.add("2");
            return null;
        });

        try {
            target.search(list("foo"), null);
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
            public ClassSource findSource(String name, SearchPath searchPath) {
                return new ClassSource() {
                    @Override
                    public void eachClass(BiConsumer<String, ClassLoader> consumer) {
                        consumer.accept("foo.Bar", new ClassLoader() {
                            @Override
                            public Class<?> loadClass(String name) throws ClassNotFoundException {
                                loaded.add(name);
                                return null;
                            }
                        });
                    }
                };
            }
        });

        java.util.List<LoadedClass> search = target.search(list("/tmp/something"), null);
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
        target.addProvider((name, searchPath) -> consumer -> consumer.accept("foo.Bar", classLoader));
        target.search(list("foobar"), null);
    }

    private <T> List<T> list(T... entries) {
        List<T> list = new ArrayList<T>();
        for (T entry : entries) {
            list.add(entry);
        }
        return list;
    }

    private <T> Set<T> hashset(T... entries) {
        Set<T> set = new HashSet<T>();
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
