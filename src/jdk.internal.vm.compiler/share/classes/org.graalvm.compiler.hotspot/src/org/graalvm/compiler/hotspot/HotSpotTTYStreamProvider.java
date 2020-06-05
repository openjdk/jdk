/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.hotspot.HotSpotGraalOptionValues.GRAAL_OPTION_PROPERTY_PREFIX;
import static org.graalvm.compiler.hotspot.HotSpotGraalOptionValues.defaultOptions;
import static jdk.internal.vm.compiler.word.LocationIdentity.ANY_LOCATION;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.debug.TTYStreamProvider;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.IsolateUtil;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.Pointer;
import jdk.internal.vm.compiler.word.WordFactory;

import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.services.Services;

@ServiceProvider(TTYStreamProvider.class)
public class HotSpotTTYStreamProvider implements TTYStreamProvider {

    public static class Options {

        // @formatter:off
        @Option(help = "File to which logging is sent.  A %p in the name will be replaced with a string identifying " +
                       "the process, usually the process id and %t will be replaced by System.currentTimeMillis(). " +
                       "If the current runtime is in an isolate, then %i will be replaced by '<isolate id>' " +
                       "otherwise %i is removed. An %I is the same as %i except that the replacement is " +
                       "'<isolate id>@<isolate address>'. " +
                       "Using %o as filename sends logging to System.out whereas %e sends logging to System.err.", type = OptionType.Expert)
        public static final LogStreamOptionKey LogFile = new LogStreamOptionKey();
        // @formatter:on
    }

    @Override
    public PrintStream getStream() {
        return Options.LogFile.getStream();
    }

    static {
        Word.ensureInitialized();
    }

    /**
     * Gets a pointer to a global word initialized to 0.
     */
    private static Pointer getBarrierPointer() {
        // Substituted by Target_org_graalvm_compiler_hotspot_HotSpotTTYStreamProvider
        return WordFactory.nullPointer();
    }

    /**
     * Executes {@code action}. If {@code barrier.isNonNull()}, then {@code barrier} is used to
     * ensure the action is executed exactly once in the process (i.e. synchronized across all
     * threads and isolates) and that threads will block here until the action is guaranteed to have
     * been executed. Note that each {@code barrier} is specific to a specific {@code action} and
     * cannot be used for any other action.
     */
    private static boolean execute(Runnable action, Pointer barrier) {
        if (barrier.isNull()) {
            action.run();
            return true;
        }
        final long initial = 0L;
        final long executing = 1L;
        final long executed = 2L;

        while (true) {
            long value = barrier.readLong(0);
            if (value == initial) {
                if (barrier.compareAndSwapLong(0, value, executing, ANY_LOCATION) == value) {
                    action.run();
                    barrier.writeLong(0, executed);
                    return true;
                }
            } else {
                if (value == executed) {
                    return false;
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                }
                value = barrier.readLong(0);
            }
        }
    }

    /**
     * An option for a configurable file name that can also open a {@link PrintStream} on the file.
     * If no value is given for the option, the stream will output to HotSpot's
     * {@link HotSpotJVMCIRuntime#getLogStream() log} stream
     */
    private static class LogStreamOptionKey extends OptionKey<String> {

        LogStreamOptionKey() {
            super(null);
        }

        /**
         * @return {@code nameTemplate} with all instances of %p replaced by
         *         {@link GraalServices#getExecutionID()} and %t by
         *         {@link System#currentTimeMillis()}. Checks %o and %e are not combined with any
         *         other characters.
         */
        private static String makeFilename(String nameTemplate) {
            String name = nameTemplate;
            if (name.contains("%p")) {
                name = name.replace("%p", GraalServices.getExecutionID());
            }
            if (name.contains("%i")) {
                name = name.replace("%i", IsolateUtil.getIsolateID(false));
            }
            if (name.contains("%I")) {
                name = name.replace("%I", IsolateUtil.getIsolateID(true));
            }
            if (name.contains("%t")) {
                name = name.replace("%t", String.valueOf(System.currentTimeMillis()));
            }

            for (String subst : new String[]{"%o", "%e"}) {
                if (name.contains(subst) && !name.equals(subst)) {
                    throw new IllegalArgumentException("LogFile substitution " + subst + " cannot be combined with any other characters");
                }
            }

            return name;
        }

        /**
         * An output stream that redirects to {@link HotSpotJVMCIRuntime#getLogStream()}. The
         * {@link HotSpotJVMCIRuntime#getLogStream()} value is only accessed the first time an IO
         * operation is performed on the stream. This is required to break a deadlock in early JVMCI
         * initialization.
         */
        class DelayedOutputStream extends OutputStream {
            @NativeImageReinitialize private volatile OutputStream lazy;

            private OutputStream lazy() throws FileNotFoundException {
                if (lazy == null) {
                    synchronized (this) {
                        if (lazy == null) {
                            String nameTemplate = LogStreamOptionKey.this.getValue(defaultOptions());
                            if (nameTemplate != null) {
                                String name = makeFilename(nameTemplate);
                                switch (name) {
                                    case "%o":
                                        lazy = System.out;
                                        break;
                                    case "%e":
                                        lazy = System.err;
                                        break;
                                    default:
                                        boolean executed = execute(() -> {
                                            File file = new File(name);
                                            if (file.exists()) {
                                                file.delete();
                                            }
                                        }, getBarrierPointer());
                                        final boolean enableAutoflush = true;
                                        FileOutputStream result = new FileOutputStream(name, true);
                                        if (executed) {
                                            printVMConfig(enableAutoflush, result);
                                        }
                                        lazy = result;
                                }
                                return lazy;
                            }

                            lazy = HotSpotJVMCIRuntime.runtime().getLogStream();
                            execute(() -> {
                                PrintStream ps = new PrintStream(lazy);
                                ps.printf("[Use -D%sLogFile=<path> to redirect Graal log output to a file.]%n", GRAAL_OPTION_PROPERTY_PREFIX);
                                ps.flush();

                            }, getBarrierPointer());
                        }
                    }
                }
                return lazy;
            }

            @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE", justification = "false positive on dead store to `ps`")
            private void printVMConfig(final boolean enableAutoflush, FileOutputStream result) {
                /*
                 * Add the JVM and Java arguments to the log file to help identity it.
                 */
                PrintStream ps = new PrintStream(result, enableAutoflush);
                List<String> inputArguments = GraalServices.getInputArguments();
                if (inputArguments != null) {
                    ps.println("VM Arguments: " + String.join(" ", inputArguments));
                }
                String cmd = Services.getSavedProperties().get("sun.java.command");
                if (cmd != null) {
                    ps.println("sun.java.command=" + cmd);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                lazy().write(b, off, len);
            }

            @Override
            public void write(int b) throws IOException {
                lazy().write(b);
            }

            @Override
            public void flush() throws IOException {
                lazy().flush();
            }

            @Override
            public void close() throws IOException {
                lazy().close();
            }
        }

        /**
         * Gets the print stream configured by this option. If no file is configured, the print
         * stream will output to HotSpot's {@link HotSpotJVMCIRuntime#getLogStream() log} stream.
         */
        public PrintStream getStream() {
            return new PrintStream(new DelayedOutputStream());
        }
    }
}
