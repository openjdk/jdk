/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.mxtool.junit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.internal.JUnitSystem;
import org.junit.internal.RealSystem;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.RunnerScheduler;

import junit.runner.Version;

public class MxJUnitWrapper {

    public static class MxJUnitConfig {

        public boolean verbose = false;
        public boolean veryVerbose = false;
        public boolean enableTiming = false;
        public boolean failFast = false;
        public boolean color = false;
        public boolean eagerStackTrace = false;
        public boolean gcAfterTest = false;
        public boolean recordResults = false;
        public int repeatCount = 1;
    }

    private static class RepeatingRunner extends Runner {

        private final Runner parent;
        private int repeat;

        RepeatingRunner(Runner parent, int repeat) {
            this.parent = parent;
            this.repeat = repeat;
        }

        @Override
        public Description getDescription() {
            return parent.getDescription();
        }

        @Override
        public void run(RunNotifier notifier) {
            for (int i = 0; i < repeat; i++) {
                parent.run(notifier);
            }
        }

        @Override
        public int testCount() {
            return super.testCount() * repeat;
        }
    }

    private static class RepeatingRequest extends Request {

        private final Request request;
        private final int repeat;

        RepeatingRequest(Request request, int repeat) {
            this.request = request;
            this.repeat = repeat;
        }

        @Override
        public Runner getRunner() {
            return new RepeatingRunner(request.getRunner(), repeat);
        }
    }

    /**
     * Run the tests contained in the classes named in the <code>args</code>. A single test method
     * can be specified by adding #method after the class name. Only a single test can be run in
     * this way. If all tests run successfully, exit with a status of 0. Otherwise exit with a
     * status of 1. Write feedback while tests are running and write stack traces for all failed
     * tests after the tests all complete.
     *
     * @param args names of classes in which to find tests to run
     */
    public static void main(String... args) {
        JUnitSystem system = new RealSystem();
        JUnitCore junitCore = new JUnitCore();
        system.out().println("MxJUnitCore");
        system.out().println("JUnit version " + Version.id());

        MxJUnitRequest.Builder builder = new MxJUnitRequest.Builder();
        MxJUnitConfig config = new MxJUnitConfig();

        String[] expandedArgs = expandArgs(args);
        int i = 0;
        while (i < expandedArgs.length) {
            String each = expandedArgs[i];
            if (each.charAt(0) == '-') {
                // command line arguments
                if (each.contentEquals("-JUnitVerbose")) {
                    config.verbose = true;
                } else if (each.contentEquals("-JUnitVeryVerbose")) {
                    config.veryVerbose = true;
                } else if (each.contentEquals("-JUnitFailFast")) {
                    config.failFast = true;
                } else if (each.contentEquals("-JUnitEnableTiming")) {
                    config.enableTiming = true;
                } else if (each.contentEquals("-JUnitColor")) {
                    config.color = true;
                } else if (each.contentEquals("-JUnitEagerStackTrace")) {
                    config.eagerStackTrace = true;
                } else if (each.contentEquals("-JUnitGCAfterTest")) {
                    config.gcAfterTest = true;
                } else if (each.contentEquals("-JUnitRecordResults")) {
                    config.recordResults = true;
                } else if (each.contentEquals("-JUnitRepeat")) {
                    if (i + 1 >= expandedArgs.length) {
                        system.out().println("Must include argument for -JUnitRepeat");
                        System.exit(1);
                    }
                    try {
                        config.repeatCount = Integer.parseInt(expandedArgs[++i]);
                    } catch (NumberFormatException e) {
                        system.out().println("Expected integer argument for -JUnitRepeat. Found: " + expandedArgs[i]);
                        System.exit(1);
                    }
                } else {
                    system.out().println("Unknown command line argument: " + each);
                }

            } else {

                try {
                    builder.addTestSpec(each);
                } catch (MxJUnitRequest.BuilderException ex) {
                    system.out().println(ex.getMessage());
                    System.exit(1);
                }
            }
            i++;
        }

        MxJUnitRequest request = builder.build();

        if (System.getProperty("java.specification.version").compareTo("1.9") >= 0) {
            addExports(request.classes, system.out());
        }

        for (RunListener p : ServiceLoader.load(RunListener.class)) {
            junitCore.addListener(p);
        }

        Result result = runRequest(junitCore, system, config, request);
        System.exit(result.wasSuccessful() ? 0 : 1);
    }

    private static PrintStream openFile(JUnitSystem system, String name) {
        File file = new File(name).getAbsoluteFile();
        try {
            FileOutputStream fos = new FileOutputStream(file);
            return new PrintStream(fos, true);
        } catch (FileNotFoundException e) {
            system.out().println("Could not open " + file + " for writing: " + e);
            System.exit(1);
            return null;
        }
    }

    public static Result runRequest(JUnitCore junitCore, JUnitSystem system, MxJUnitConfig config, MxJUnitRequest mxRequest) {
        final TextRunListener textListener;
        if (config.veryVerbose) {
            textListener = new VerboseTextListener(system, mxRequest.classes.size(), VerboseTextListener.SHOW_ALL_TESTS);
        } else if (config.verbose) {
            textListener = new VerboseTextListener(system, mxRequest.classes.size());
        } else {
            textListener = new TextRunListener(system);
        }
        TimingDecorator timings = config.enableTiming ? new TimingDecorator(textListener) : null;
        MxRunListener mxListener = config.enableTiming ? timings : textListener;

        if (config.color) {
            mxListener = new AnsiTerminalDecorator(mxListener);
        }
        if (config.eagerStackTrace) {
            mxListener = new EagerStackTraceDecorator(mxListener);
        }
        if (config.gcAfterTest) {
            mxListener = new GCAfterTestDecorator(mxListener);
        }
        if (config.recordResults) {
            PrintStream passed = openFile(system, "passed.txt");
            PrintStream failed = openFile(system, "failed.txt");
            mxListener = new TestResultLoggerDecorator(passed, failed, mxListener);
        }

        junitCore.addListener(TextRunListener.createRunListener(mxListener));

        Request request = mxRequest.getRequest();
        if (mxRequest.methodName == null) {
            if (config.failFast) {
                Runner runner = request.getRunner();
                if (runner instanceof ParentRunner) {
                    ParentRunner<?> parentRunner = (ParentRunner<?>) runner;
                    parentRunner.setScheduler(new RunnerScheduler() {
                        public void schedule(Runnable childStatement) {
                            if (textListener.getLastFailure() == null) {
                                childStatement.run();
                            }
                        }

                        public void finished() {
                        }
                    });
                } else {
                    system.out().println("Unexpected Runner subclass " + runner.getClass().getName() + " - fail fast not supported");
                }
            }
        } else {
            if (config.failFast) {
                system.out().println("Single method selected - fail fast not supported");
            }
        }

        if (config.repeatCount != 1) {
            request = new RepeatingRequest(request, config.repeatCount);
        }

        if (config.enableTiming) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    printTimings(timings);
                }
            });
        }

        Result result = junitCore.run(request);
        for (Failure each : mxRequest.missingClasses) {
            result.getFailures().add(each);
        }

        return result;
    }

    private static final Pattern MODULE_PACKAGE_RE = Pattern.compile("([^/]+)/(.+)");

    private static class Timing<T> implements Comparable<Timing<T>> {
        final T subject;
        final long value;

        Timing(T subject, long value) {
            this.subject = subject;
            this.value = value;
        }

        public int compareTo(Timing<T> o) {
            if (this.value < o.value) {
                return -1;
            }
            if (this.value > o.value) {
                return 1;
            }
            return 0;
        }
    }

    // Should never need to customize so using a system property instead
    // of a command line option for customization is fine.
    private static final int TIMINGS_TO_PRINT = Integer.getInteger("mx.junit.timings_to_print", 10);

    private static void printTimings(TimingDecorator timings) {
        if (TIMINGS_TO_PRINT != 0) {
            List<Timing<Class<?>>> classTimes = new ArrayList<>(timings.classTimes.size());
            List<Timing<Description>> testTimes = new ArrayList<>(timings.testTimes.size());
            for (Map.Entry<Class<?>, Long> e : timings.classTimes.entrySet()) {
                classTimes.add(new Timing<>(e.getKey(), e.getValue()));
            }
            for (Map.Entry<Description, Long> e : timings.testTimes.entrySet()) {
                testTimes.add(new Timing<>(e.getKey(), e.getValue()));
            }
            classTimes.sort(Collections.reverseOrder());
            testTimes.sort(Collections.reverseOrder());

            System.out.println();
            System.out.printf("%d longest running test classes:%n", TIMINGS_TO_PRINT);
            for (int i = 0; i < TIMINGS_TO_PRINT && i < classTimes.size(); i++) {
                Timing<Class<?>> timing = classTimes.get(i);
                System.out.printf(" %,10d ms    %s%n", timing.value, timing.subject.getName());
            }
            System.out.printf("%d longest running tests:%n", TIMINGS_TO_PRINT);
            for (int i = 0; i < TIMINGS_TO_PRINT && i < testTimes.size(); i++) {
                Timing<Description> timing = testTimes.get(i);
                System.out.printf(" %,10d ms    %s%n", timing.value, timing.subject);
            }
            Object[] current = timings.getCurrentTestDuration();
            if (current != null) {
                System.out.printf("Test %s not finished after %d ms%n", current[0], current[1]);
            }

        }
    }

    /**
     * Adds the super types of {@code cls} to {@code supertypes}.
     */
    private static void gatherSupertypes(Class<?> cls, Set<Class<?>> supertypes) {
        if (!supertypes.contains(cls)) {
            supertypes.add(cls);
            Class<?> superclass = cls.getSuperclass();
            if (superclass != null) {
                gatherSupertypes(superclass, supertypes);
            }
            for (Class<?> iface : cls.getInterfaces()) {
                gatherSupertypes(iface, supertypes);
            }
        }
    }

    /**
     * Updates modules specified in {@code AddExport} annotations on {@code classes} to export
     * concealed packages to the annotation classes' declaring modules.
     */
    private static void addExports(Set<Class<?>> classes, PrintStream out) {
        Set<Class<?>> types = new HashSet<>();
        for (Class<?> cls : classes) {
            gatherSupertypes(cls, types);
        }
        for (Class<?> cls : types) {
            Annotation[] annos = cls.getAnnotations();
            for (Annotation a : annos) {
                Class<? extends Annotation> annotationType = a.annotationType();
                if (annotationType.getSimpleName().equals("AddExports")) {
                    Optional<String[]> value = getElement("value", String[].class, a);
                    if (value.isPresent()) {
                        for (String export : value.get()) {
                            Matcher m = MODULE_PACKAGE_RE.matcher(export);
                            if (m.matches()) {
                                String moduleName = m.group(1);
                                String packageName = m.group(2);
                                JLModule module = JLModule.find(moduleName);
                                if (module == null) {
                                    out.printf("%s: Cannot find module named %s specified in \"AddExports\" annotation: %s%n", cls.getName(), moduleName, a);
                                } else {
                                    if (packageName.equals("*")) {
                                        module.exportAllPackagesTo(JLModule.fromClass(cls));
                                    } else {
                                        module.addExports(packageName, JLModule.fromClass(cls));
                                        module.addOpens(packageName, JLModule.fromClass(cls));
                                    }
                                }
                            } else {
                                out.printf("%s: Ignoring \"AddExports\" annotation with value not matching <module>/<package> pattern: %s%n", cls.getName(), a);
                            }
                        }
                    } else {
                        out.printf("%s: Ignoring \"AddExports\" annotation without `String value` element: %s%n", cls.getName(), a);
                    }
                }
            }
        }
    }

    /**
     * Gets the value of the element named {@code name} of type {@code type} from {@code annotation}
     * if present.
     *
     * @return the requested element value wrapped in an {@link Optional} or
     *         {@link Optional#empty()} if {@code annotation} has no element named {@code name}
     * @throws AssertionError if {@code annotation} has an element of the given name but whose type
     *             is not {@code type} or if there's some problem reading the value via reflection
     */
    private static <T> Optional<T> getElement(String name, Class<T> type, Annotation annotation) {
        Class<? extends Annotation> annotationType = annotation.annotationType();
        Method valueAccessor;
        try {
            valueAccessor = annotationType.getMethod(name);
            if (!valueAccessor.getReturnType().equals(type)) {
                throw new AssertionError(String.format("Element %s of %s is of type %s, not %s ", name, annotationType.getName(), valueAccessor.getReturnType().getName(), type.getName()));
            }
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
        try {
            return Optional.of(type.cast(valueAccessor.invoke(annotation)));
        } catch (Exception e) {
            throw new AssertionError(String.format("Could not read %s element from %s", name, annotation), e);
        }
    }

    /**
     * Expand any arguments starting with @ and return the resulting argument array.
     *
     * @return the expanded argument array
     */
    private static String[] expandArgs(String[] args) {
        List<String> result = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.length() > 0 && arg.charAt(0) == '@') {
                if (result == null) {
                    result = new ArrayList<>();
                    for (int j = 0; j < i; j++) {
                        result.add(args[j]);
                    }
                    expandArg(arg.substring(1), result);
                }
            } else if (result != null) {
                result.add(arg);
            }
        }
        return result != null ? result.toArray(new String[0]) : args;
    }

    /**
     * Add each line from {@code filename} to the list {@code args}.
     */
    private static void expandArg(String filename, List<String> args) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(filename));

            String buf;
            while ((buf = br.readLine()) != null) {
                args.add(buf);
            }
            br.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(2);
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
                System.exit(3);
            }
        }
    }
}
