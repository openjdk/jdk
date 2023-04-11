/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package java.util.concurrent.lazy.snippets;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.lazy.KeyMapper;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReference;
import java.util.concurrent.lazy.LazyReferenceArray;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Sippets for the package-info file
 */
public class PackageInfoSnippets {

    private PackageInfoSnippets() {
    }

    // @start region="DemoPreset"
    class DemoPreset {

        private static final LazyReference<Foo> FOO = Lazy.of(Foo::new);

        public Foo theBar() {
            // Foo is lazily constructed and recorded here upon first invocation
            return FOO.get();
        }
    }
    // @end

    // @start region="DemoHolder"
    class DemoHolder {

        public Foo theBar() {
            class Holder {
                private static final Foo FOO = new Foo();
            }

            // Foo is lazily constructed and recorded here upon first invocation
            return Holder.FOO;
        }
    }
    // @end

    // @start region="Fox"
    class Fox {

        private final LazyReference<String> lazy = Lazy.ofEmpty();

        String init(String color) {
            return lazy.supplyIfEmpty(() -> "The quick " + color + " fox");
        }
    }
    // @end

    // @start region="DemoBackground"
    class DemoBackground {

        private static final LazyReference<Foo> lazy = Lazy.<Foo>builder()
                .withSupplier(Foo::new)
                .withEarliestEvaluation(Lazy.Evaluation.CREATION_BACKGROUND)
                .build();

        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(1000);
            // lazy is likely already pre-computed here by a background thread
            System.out.println("lazy.get() = " + lazy.get());
        }
    }
    // @end

    // @start region="SupplierDemo"
    class SupplierDemo {

        // Eager Supplier of Foo
        private static final Supplier<Foo> EAGER_FOO = Foo::new;

        // Turns an eager Supplier into a caching lazy Supplier
        private static final Supplier<Foo> LAZILY_CACHED_FOO = Lazy.of(EAGER_FOO);

        public static void main(String[] args) {
            // Lazily construct and record the one-and-only Foo
            Foo theFoo = LAZILY_CACHED_FOO.get();
        }
    }
    // @end

    // @start region="DemoArray"
    class DemoArray {

        private static final LazyReferenceArray<Value> VALUE_PO2_CACHE =
                Lazy.ofArray(32, index -> new Value(1L << index));

        public Value powerOfTwoValue(int n) {
            if (n < 0 || n >= VALUE_PO2_CACHE.length()) {
                throw new IllegalArgumentException(Integer.toString(n));
            }

            return VALUE_PO2_CACHE.apply(n);
        }
    }
    // @end

    // @start region="UserCache"
    class UserCache {

        // Cache the first 64 users
        private static final LazyReferenceArray<User> USER_CACHE =
                Lazy.ofEmptyArray(64);

        public User user(int id) {
            Connection c = getDatabaseConnection();
            return USER_CACHE.computeIfEmpty(id, i -> findUserById(c, i));
        }
    }
    // @end

    // @start region="DemoIntFunction"
    class DemoIntFunction {

        // Eager IntFunction<Value>
        private static final IntFunction<Value> EAGER_VALUE =
                index -> new Value(index);

        // Turns an eager IntFunction into a caching lazy IntFunction
        private static final IntFunction<Value> LAZILY_CACHED_VALUES =
                Lazy.ofArray(64, EAGER_VALUE);

        public static void main(String[] args) {
            Value value42 = LAZILY_CACHED_VALUES.apply(42);
        }
    }
    // @end

    // @start region="DemoLazyMapper"
    class DemoLazyMapper {

        private final Function<String, Optional<String>> pageCache = Lazy.mapping(
                List.of("home", "products", "contact"), DbTools::lookupPage);

        public String renderPage(String pageName) {
            return pageCache.apply(pageName)
                    .orElseGet(() -> DbTools.lookupPage(pageName));
        }
    }
    // @end

    // @start region="DemoErrorPageMapper"
    class DemoErrorPageMapper {

        private static final Function<Integer, Optional<String>> lazy =
                Lazy.mapping(
                        List.of(
                                KeyMapper.of(400, DbTools::loadBadRequestPage),
                                KeyMapper.of(401, DbTools::loadUnaothorizedPage),
                                KeyMapper.of(403, DbTools::loadForbiddenPage),
                                KeyMapper.of(404, DbTools::loadNotFoundPage)
                        )
                );

        public String servePage(Request request) {
            int returnCode = check(request);
            if (returnCode >= 400) {
                return lazy.apply(returnCode)
                        .orElse("<!DOCTYPE html><title>Oops: " + returnCode + "</title>");
            }
            return render(request);
        }
    }
    // @end

    static class Foo {
    }

    static class Value {

        long value;

        public Value(long value) {
            this.value = value;
        }

        long asLong() {
            return value;
        }
    }

    static class User {

        private final int id;
        private String name;


        public User(int id) {
            this.id = id;
        }

    }

    static class Connection {
    }

    static class Request {
    }

    static Connection getDatabaseConnection() {
        return new Connection();
    }

    User findUserById(Connection c,
                              int id) {
        return new User(id);
    }

    static class DbTools {
        static String lookupPage(String pageName) {
            // Gets the HTML code for the named page from a content database
            return ""; // Whatever ...
        }

        static String loadBadRequestPage(int code) {
            return "";
        }

        static String loadUnaothorizedPage(int code) {
            return "";
        }

        static String loadForbiddenPage(int code) {
            return "";
        }

        static String loadNotFoundPage(int code) {
            return "";
        }


    }

    int check(Request request) {
        return 0;
    }

    String render(Request request) {
        return "";
    }

}
