/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FilePermission;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ReflectPermission;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.PropertyPermission;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * @test
 * @bug 8065552
 * @summary test that all fields returned by getDeclaredFields() can be
 *          set accessible if the right permission is granted; this test
 *          loads all the classes in the BCL, get their declared fields,
 *          and call setAccessible(false) followed by setAccessible(true);
 * @run main/othervm FieldSetAccessibleTest UNSECURE
 * @run main/othervm FieldSetAccessibleTest SECURE
 *
 * @author danielfuchs
 */
public class FieldSetAccessibleTest {

    static final List<String> skipped = new ArrayList<>();
    static final List<String> cantread = new ArrayList<>();
    static final List<String> failed = new ArrayList<>();
    static final AtomicLong classCount = new AtomicLong();
    static final AtomicLong fieldCount = new AtomicLong();
    static long startIndex = 0;
    static long maxSize = Long.MAX_VALUE;
    static long maxIndex = Long.MAX_VALUE;
    static final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();


    // Test that all fields for any given class can be made accessibles
    static void testSetFieldsAccessible(Class<?> c) {
        for (Field f : c.getDeclaredFields()) {
            fieldCount.incrementAndGet();
            f.setAccessible(false);
            f.setAccessible(true);
        }
    }

    // Performs a series of test on the given class.
    // At this time, we only call testSetFieldsAccessible(c)
    public static boolean test(Class<?> c) {
        //System.out.println(c.getName());
        classCount.incrementAndGet();

        // Call getDeclaredFields() and try to set their accessible flag.
        testSetFieldsAccessible(c);

        // add more tests here...

        return c == Class.class;
    }

    // Prints a summary at the end of the test.
    static void printSummary(long secs, long millis, long nanos) {
        System.out.println("Tested " + fieldCount.get() + " fields of "
                + classCount.get() + " classes in "
                + secs + "s " + millis + "ms " + nanos + "ns");
    }


    /**
     * @param args the command line arguments:
     *
     *     SECURE|UNSECURE [startIndex (default=0)] [maxSize (default=Long.MAX_VALUE)]
     *
     * @throws java.lang.Exception if the test fails
     */
    public static void main(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            args = new String[] {"SECURE", "0"};
        } else if (args.length > 3) {
            throw new RuntimeException("Expected at most one argument. Found "
                    + Arrays.asList(args));
        }
        try {
            if (args.length > 1) {
                startIndex = Long.parseLong(args[1]);
                if (startIndex < 0) {
                    throw new IllegalArgumentException("startIndex args[1]: "
                            + startIndex);
                }
            }
            if (args.length > 2) {
                maxSize = Long.parseLong(args[2]);
                if (maxSize <= 0) {
                    maxSize = Long.MAX_VALUE;
                }
                maxIndex = (Long.MAX_VALUE - startIndex) < maxSize
                        ? Long.MAX_VALUE : startIndex + maxSize;
            }
            TestCase.valueOf(args[0]).run();
        } catch (OutOfMemoryError oome) {
            System.err.println(classCount.get());
            throw oome;
        }
    }

    public static void run(TestCase test) {
        System.out.println("Testing " + test);
        test(listAllClassNames());
        System.out.println("Passed " + test);
    }

    static Iterable<String> listAllClassNames() {
        return new ClassNameJrtStreamBuilder();
    }

    static void test(Iterable<String> iterable) {
        final long start = System.nanoTime();
        boolean classFound = false;
        int index = 0;
        for (String s: iterable) {
            if (index == maxIndex) break;
            try {
                if (index < startIndex) continue;
                if (test(s)) {
                    classFound = true;
                }
            } finally {
                index++;
            }
        }
        long elapsed = System.nanoTime() - start;
        long secs = elapsed / 1000_000_000;
        long millis = (elapsed % 1000_000_000) / 1000_000;
        long nanos  = elapsed % 1000_000;
        System.out.println("Unreadable path elements: " + cantread);
        System.out.println("Skipped path elements: " + skipped);
        System.out.println("Failed path elements: " + failed);
        printSummary(secs, millis, nanos);

        if (!failed.isEmpty()) {
            throw new RuntimeException("Test failed for the following classes: " + failed);
        }
        if (!classFound && startIndex == 0 && index < maxIndex) {
            // this is just to verify that we have indeed parsed rt.jar
            // (or the java.base module)
            throw  new RuntimeException("Test failed: Class.class not found...");
        }
        if (classCount.get() == 0 && startIndex == 0) {
            throw  new RuntimeException("Test failed: no class found?");
        }
    }

    static boolean test(String s) {
        try {
            if (s.startsWith("WrapperGenerator")) {
                System.out.println("Skipping "+ s);
                return false;
            }
            final Class<?> c = Class.forName(
                    s.replace('/', '.').substring(0, s.length() - 6),
                    false,
                    systemClassLoader);
            return test(c);
        } catch (Exception t) {
            t.printStackTrace(System.err);
            failed.add(s);
        } catch (NoClassDefFoundError e) {
            e.printStackTrace(System.err);
            failed.add(s);
        }
        return false;
    }

    static class ClassNameJrtStreamBuilder implements Iterable<String>{

        final FileSystem jrt;
        final List<Path> roots = new ArrayList<>();
        ClassNameJrtStreamBuilder() {
             jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
             for (Path root : jrt.getRootDirectories()) {
                 roots.add(root);
             }
        }

        Stream<String> build() {
            return roots.stream().flatMap(this::toStream)
                    .filter(x -> x.getNameCount() > 1)
                    .map( x-> x.subpath(1, x.getNameCount()))
                    .map( x -> x.toString())
                    .filter(s -> s.endsWith(".class"));
        }

        @Override
        public Iterator<String> iterator() {
            return build().iterator();
        }

        private Stream<Path> toStream(Path root) {
            try {
                return Files.walk(root);
            } catch(IOException x) {
                x.printStackTrace(System.err);
                skipped.add(root.toString());
            }
            return Collections.<Path>emptyList().stream();
        }

    }

    // Test with or without a security manager
    public static enum TestCase {
        UNSECURE, SECURE;
        public void run() throws Exception {
            System.out.println("Running test case: " + name());
            Configure.setUp(this);
            FieldSetAccessibleTest.run(this);
        }
    }

    // A helper class to configure the security manager for the test,
    // and bypass it when needed.
    static class Configure {
        static Policy policy = null;
        static final ThreadLocal<AtomicBoolean> allowAll = new ThreadLocal<AtomicBoolean>() {
            @Override
            protected AtomicBoolean initialValue() {
                return  new AtomicBoolean(false);
            }
        };
        static void setUp(TestCase test) {
            switch (test) {
                case SECURE:
                    if (policy == null && System.getSecurityManager() != null) {
                        throw new IllegalStateException("SecurityManager already set");
                    } else if (policy == null) {
                        policy = new SimplePolicy(TestCase.SECURE, allowAll);
                        Policy.setPolicy(policy);
                        System.setSecurityManager(new SecurityManager());
                    }
                    if (System.getSecurityManager() == null) {
                        throw new IllegalStateException("No SecurityManager.");
                    }
                    if (policy == null) {
                        throw new IllegalStateException("policy not configured");
                    }
                    break;
                case UNSECURE:
                    if (System.getSecurityManager() != null) {
                        throw new IllegalStateException("SecurityManager already set");
                    }
                    break;
                default:
                    throw new InternalError("No such testcase: " + test);
            }
        }
        static void doPrivileged(Runnable run) {
            allowAll.get().set(true);
            try {
                run.run();
            } finally {
                allowAll.get().set(false);
            }
        }
    }

    // A Helper class to build a set of permissions.
    final static class PermissionsBuilder {
        final Permissions perms;
        public PermissionsBuilder() {
            this(new Permissions());
        }
        public PermissionsBuilder(Permissions perms) {
            this.perms = perms;
        }
        public PermissionsBuilder add(Permission p) {
            perms.add(p);
            return this;
        }
        public PermissionsBuilder addAll(PermissionCollection col) {
            if (col != null) {
                for (Enumeration<Permission> e = col.elements(); e.hasMoreElements(); ) {
                    perms.add(e.nextElement());
                }
            }
            return this;
        }
        public Permissions toPermissions() {
            final PermissionsBuilder builder = new PermissionsBuilder();
            builder.addAll(perms);
            return builder.perms;
        }
    }

    // Policy for the test...
    public static class SimplePolicy extends Policy {

        final Permissions permissions;
        final Permissions allPermissions;
        final ThreadLocal<AtomicBoolean> allowAll;
        public SimplePolicy(TestCase test, ThreadLocal<AtomicBoolean> allowAll) {
            this.allowAll = allowAll;

            // Permission needed by the tested code exercised in the test
            permissions = new Permissions();
            permissions.add(new RuntimePermission("fileSystemProvider"));
            permissions.add(new RuntimePermission("createClassLoader"));
            permissions.add(new RuntimePermission("closeClassLoader"));
            permissions.add(new RuntimePermission("getClassLoader"));
            permissions.add(new RuntimePermission("accessDeclaredMembers"));
            permissions.add(new ReflectPermission("suppressAccessChecks"));
            permissions.add(new PropertyPermission("*", "read"));
            permissions.add(new FilePermission("<<ALL FILES>>", "read"));

            // these are used for configuring the test itself...
            allPermissions = new Permissions();
            allPermissions.add(new java.security.AllPermission());
        }

        @Override
        public boolean implies(ProtectionDomain domain, Permission permission) {
            if (allowAll.get().get()) return allPermissions.implies(permission);
            if (permissions.implies(permission)) return true;
            if (permission instanceof java.lang.RuntimePermission) {
                if (permission.getName().startsWith("accessClassInPackage.")) {
                    // add these along to the set of permission we have, when we
                    // discover that we need them.
                    permissions.add(permission);
                    return true;
                }
            }
            return false;
        }

        @Override
        public PermissionCollection getPermissions(CodeSource codesource) {
            return new PermissionsBuilder().addAll(allowAll.get().get()
                    ? allPermissions : permissions).toPermissions();
        }

        @Override
        public PermissionCollection getPermissions(ProtectionDomain domain) {
            return new PermissionsBuilder().addAll(allowAll.get().get()
                    ? allPermissions : permissions).toPermissions();
        }
    }

}
