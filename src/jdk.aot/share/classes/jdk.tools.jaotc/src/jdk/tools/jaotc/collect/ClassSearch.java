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



package jdk.tools.jaotc.collect;

import jdk.tools.jaotc.LoadedClass;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class ClassSearch {
    private final List<SourceProvider> providers = new ArrayList<>();

    public void addProvider(SourceProvider provider) {
        providers.add(provider);
    }

    public List<LoadedClass> search(List<SearchFor> search, SearchPath searchPath) {
        return search(search, searchPath, (s, t) -> {
            throw new InternalError(s + " : " + t, t);
        });
    }

    public List<LoadedClass> search(List<SearchFor> search, SearchPath searchPath, BiConsumer<String, Throwable> classLoadingErrorsHandler) {
        List<LoadedClass> loaded = new ArrayList<>();

        List<ClassSource> sources = new ArrayList<>();

        for (SearchFor entry : search) {
            sources.add(findSource(entry, searchPath, classLoadingErrorsHandler));
        }

        for (ClassSource source : sources) {
            if (source != null) {
                source.eachClass((name, loader) -> {
                    LoadedClass x = loadClass(name, loader, classLoadingErrorsHandler);
                    if (x != null) {
                        loaded.add(x);
                    }
                });
            }
        }

        return loaded;
    }

    private static LoadedClass loadClass(String name, ClassLoader loader, BiConsumer<String, Throwable> classLoadingErrorsHandler) {
        try {
            Class<?> clzz = loader.loadClass(name);
            return new LoadedClass(name, clzz);
        } catch (Throwable e) {
            classLoadingErrorsHandler.accept(name, e);
            return null;
        }
    }

    private ClassSource findSource(SearchFor searchFor, SearchPath searchPath, BiConsumer<String, Throwable> classLoadingErrorsHandler) {
        ClassSource found = null;

        for (SourceProvider provider : providers) {
            if (!searchFor.isUnknown() && !provider.supports(searchFor.getType())) {
                continue;
            }
            ClassSource source = null;
            try {
                source = provider.findSource(searchFor.getName(), searchPath);
            } catch (Throwable e) {
                classLoadingErrorsHandler.accept(searchFor.getName(), e);
            }
            if (source != null) {
                if (found != null) {
                    throw new InternalError("Multiple possible sources: " + source + " and: " + found);
                }
                found = source;
            }
        }

        if (found == null) {
            classLoadingErrorsHandler.accept(searchFor.getName(), new InternalError("Failed to find " + searchFor.getType() + " file: " + searchFor.getName()));
        }
        return found;
    }

    public static List<SearchFor> makeList(String type, String argument) {
        List<SearchFor> list = new ArrayList<>();
        String[] elements = argument.split(":");
        for (String element : elements) {
            list.add(new SearchFor(element, type));
        }
        return list;
    }
}
