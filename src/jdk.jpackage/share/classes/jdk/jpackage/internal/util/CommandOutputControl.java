/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import static java.util.stream.Collectors.joining;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.internal.util.function.ThrowingRunnable;

/**
 * Runs commands and processes their stdout and stderr streams.
 * <p>
 * A command is either a subprocess represented by {@link ProcessBuilder} or a
 * tool provided by {@link ToolProvider}.
 * <p>
 * A command is executed synchronously, and the result of its execution is
 * stored in a {@link Result} instance which captures the exit code and any
 * saved output streams.
 * <p>
 * Depending on the configuration, it can save the entire output stream, only
 * the first line, or not save the output at all. Stdout and stderr streams can
 * be configured independently.
 * <p>
 * Output streams can be treated as either byte streams or character streams.
 *
 * <p>
 * The table below shows how different parameter combinations affect the content
 * written to streams returned by {@link #dumpStdout()} and
 * {@link #dumpStderr()} for subsequently executed tools, regardless of whether
 * their output streams are saved, or for subprocesses when the output streams
 * are saved:
 * <table border="1">
 * <thead>
 * <tr>
 * <th></th>
 * <th>discardStdout(false) and discardStderr(false)</th>
 * <th>discardStdout(false) and discardStderr(true)</th>
 * <th>discardStdout(true) and discardStderr(false)</th>
 * </tr>
 * </thead> <tbody>
 * <tr>
 * <th scope="row">redirectStderr(true) and dumpOutput(true)</th>
 * <td>
 * <p>
 * dumpStdout(): STDOUT and STDERR interleaved
 * <p>
 * dumpStderr(): unchanged</td>
 * <td>
 * <p>
 * dumpStdout(): STDOUT
 * <p>
 * dumpStderr(): unchanged</td>
 * <td>
 * <p>
 * dumpStdout(): STDERR;
 * <p>
 * dumpStderr(): unchanged</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectStderr(false) and dumpOutput(true)</th>
 * <td>
 * <p>
 * dumpStdout(): STDOUT
 * <p>
 * dumpStderr(): STDERR</td>
 * <td>
 * <p>
 * dumpStdout(): STDOUT
 * <p>
 * dumpStderr(): unchanged</td>
 * <td>
 * <p>
 * dumpStdout(): unchanged
 * <p>
 * dumpStderr(): STDERR</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <p>
 * The table below shows how different parameter combinations affect the content
 * written to the native file descriptors associated with {@link System#out} and
 * {@link System#err} for subsequently executed subprocesses when the output
 * streams are not saved:
 * <table border="1">
 * <thead>
 * <tr>
 * <th></th>
 * <th>discardStdout(false) and discardStderr(false)</th>
 * <th>discardStdout(false) and discardStderr(true)</th>
 * <th>discardStdout(true) and discardStderr(false)</th>
 * </tr>
 * </thead> <tbody>
 * <tr>
 * <th scope="row">redirectStderr(true) and dumpOutput(true)</th>
 * <td>
 * <p>
 * System.out: STDOUT and STDERR interleaved
 * <p>
 * System.err: unchanged</td>
 * <td>
 * <p>
 * System.out: STDOUT
 * <p>
 * System.err: unchanged</td>
 * <td>
 * <p>
 * System.out: STDERR;
 * <p>
 * The command's STDERR will be written to the stream referenced by
 * {@link #dumpStdout()} rather than to the underlying file descriptor
 * associated with the Java process's STDOUT
 * <p>
 * System.err: unchanged</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectStderr(false) and dumpOutput(true)</th>
 * <td>
 * <p>
 * System.out: STDOUT
 * <p>
 * System.err: STDERR</td>
 * <td>
 * <p>
 * System.out: STDOUT
 * <p>
 * System.err: unchanged</td>
 * <td>
 * <p>
 * System.out: unchanged
 * <p>
 * System.err: STDERR</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <p>
 * The table below shows how different parameter combinations affect the
 * properties of {@link Result} objects returned by
 * {@link #execute(ProcessBuilder, long)} or
 * {@link #execute(ToolProvider, long, String...)} when processing character
 * streams:
 * <table border="1">
 * <thead>
 * <tr>
 * <th></th>
 * <th>saveOutput(true)</th>
 * <th>saveFirstLineOfOutput()</th>
 * </tr>
 * </thead> <tbody>
 * <tr>
 * <th scope="row">redirectStderr(true) and discardStdout(false) and
 * discardStderr(false)</th>
 * <td>
 * <p>
 * content(): STDOUT and STDERR interleaved
 * <p>
 * findStdout(): {@code Optional.empty()}
 * <p>
 * findStderr(): {@code Optional.empty()}</td>
 * <td>
 * <p>
 * content(): a single-item list containing the first line of interleaved STDOUT
 * and STDERR if the command produced any output; otherwise, an empty list
 * <p>
 * findStdout(): {@code Optional.empty()}
 * <p>
 * findStderr(): {@code Optional.empty()}</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectStderr(false) and discardStdout(false) and
 * discardStderr(false)</th>
 * <td>
 * <p>
 * content(): STDOUT followed by STDERR
 * <p>
 * stdout(): STDOUT
 * <p>
 * stderr(): STDERR</td>
 * <td>
 * <p>
 * content(): a list containing at most two items: the first line of STDOUT (if
 * the command produced any), followed by the first line of STDERR (if the
 * command produced any)
 * <p>
 * stdout(): The first line of STDOUT (if the command produced any); otherwise
 * an empty list
 * <p>
 * findStderr(): The first line of STDERR (if the command produced any);
 * otherwise an empty list
 * </tr>
 * <tr>
 * <th scope="row">redirectStderr(true) and discardStdout(false) and
 * discardStderr(true)</th>
 * <td>
 * <p>
 * content(): STDOUT
 * <p>
 * stdout(): The same as content()
 * <p>
 * findStderr(): {@code Optional.empty()}</td>
 * <td>
 * <p>
 * content(): The first line of STDOUT (if the command produced any); otherwise
 * an empty list
 * <p>
 * stdout(): The same as content()
 * <p>
 * findStderr(): {@code Optional.empty()}</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectStderr(false) and discardStdout(false) and
 * discardStderr(true)</th>
 * <td>
 * <p>
 * content(): STDOUT
 * <p>
 * stdout(): The same as content()
 * <p>
 * stderr(): an empty list</td>
 * <td>
 * <p>
 * content(): The first line of STDOUT (if the command produced any); otherwise
 * an empty list
 * <p>
 * stdout(): The same as content()
 * <p>
 * stderr(): an empty list</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectStderr(true) and discardStdout(true) and
 * discardStderr(false)</th>
 * <td>
 * <p>
 * content(): STDERR
 * <p>
 * stdout(): The same as content()
 * <p>
 * findStderr(): {@code Optional.empty()}</td>
 * <td>
 * <p>
 * content(): The first line of STDERR (if the command produced any); otherwise
 * an empty list
 * <p>
 * stdout(): The same as content()
 * <p>
 * findStderr(): {@code Optional.empty()}</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectStderr(false) and discardStdout(true) and
 * discardStderr(false)</th>
 * <td>
 * <p>
 * content(): STDERR
 * <p>
 * findStdout(): an empty list
 * <p>
 * stderr(): The same as content()</td>
 * <td>
 * <p>
 * content(): The first line of STDERR (if the command produced any); otherwise
 * an empty list
 * <p>
 * findStdout(): an empty list
 * <p>
 * stderr(): The same as content()</td>
 * </tr>
 * </tbody>
 * </table>
 * <p>
 * The table below shows how different parameter combinations affect the
 * properties of {@link Result} objects returned by
 * {@link #execute(ProcessBuilder, long)} or
 * {@link #execute(ToolProvider, long, String...)} when processing byte streams:
 * <table border="1">
 * <thead>
 * <tr>
 * <th></th>
 * <th>saveOutput(true) or saveFirstLineOfOutput()</th>
 * </tr>
 * </thead> <tbody>
 * <tr>
 * <th scope="row">redirectStderr(true) and discardStdout(false) and
 * discardStderr(false)</th>
 * <td>
 * <p>
 * byteContent(): STDOUT and STDERR interleaved
 * <p>
 * findByteStdout(): {@code Optional.empty()}
 * <p>
 * findByteStderr(): {@code Optional.empty()}</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectStderr(false) and discardStdout(false) and
 * discardStderr(false)</th>
 * <td>
 * <p>
 * byteContent(): STDOUT followed by STDERR
 * <p>
 * byteStdout(): STDOUT
 * <p>
 * byteStderr(): STDERR</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectStderr(true) and discardStdout(false) and
 * discardStderr(true)</th>
 * <td>
 * <p>
 * byteContent(): STDOUT
 * <p>
 * byteStdout(): The same as byteContent()
 * <p>
 * findByteStderr(): {@code Optional.empty()}</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectStderr(false) and discardStdout(false) and
 * discardStderr(true)</th>
 * <td>
 * <p>
 * byteContent(): STDOUT
 * <p>
 * byteStdout(): The same as byteContent()
 * <p>
 * byteStderr(): an empty array</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectStderr(true) and discardStdout(true) and
 * discardStderr(false)</th>
 * <td>
 * <p>
 * byteContent(): STDERR
 * <p>
 * byteStdout(): The same as byteContent()
 * <p>
 * findByteStderr(): {@code Optional.empty()}</td>
 * </tr>
 * <tr>
 * <th scope="row">redirectStderr(false) and discardStdout(true) and
 * discardStderr(false)</th>
 * <td>
 * <p>
 * byteContent(): STDERR
 * <p>
 * findByteStdout(): an empty array
 * <p>
 * byteStderr(): The same as byteContent()</td>
 * </tr>
 * </tbody>
 * </table>
 */
public final class CommandOutputControl {

    public CommandOutputControl() {
        outputStreamsControl = new OutputStreamsControl();
    }

    private CommandOutputControl(CommandOutputControl other) {
        flags = other.flags;
        outputStreamsControl = other.outputStreamsControl.copy();
        dumpStdout = other.dumpStdout;
        dumpStderr = other.dumpStderr;
        charset = other.charset;
        processListener = other.processListener;
    }

    /**
     * Specifies whether the full output produced by commands subsequently executed
     * by this object will be saved.
     * <p>
     * If {@code v} is {@code true}, both stdout and stderr streams will be saved;
     * otherwise, they will not be saved.
     * <p>
     * This setting is mutually exclusive with {@link #saveFirstLineOfOutput()}.
     *
     * @param v {@code true} to save the full stdout and stderr streams;
     *          {@code false} otherwise
     * @return this
     */
    public CommandOutputControl saveOutput(boolean v) {
        return setOutputControl(v, OutputControlOption.SAVE_ALL);
    }

    /**
     * Returns whether this object will save the complete output of commands
     * subsequently executed.
     *
     * @return {@code true} if this object will save the full output of commands it
     *         executes subsequently; {@code false} otherwise
     */
    public boolean isSaveOutput() {
        return outputStreamsControl.stdout().saveAll();
    }

    /**
     * Specifies whether the first line of the output, combined from the stdout and
     * stderr streams of commands subsequently executed by this object, will be
     * saved.
     * <p>
     * This setting is mutually exclusive with {@link #saveOutput(boolean)}.
     *
     * @return this
     */
    public CommandOutputControl saveFirstLineOfOutput() {
        return setOutputControl(true, OutputControlOption.SAVE_FIRST_LINE);
    }

    /**
     * Returns whether this object will save the first line of the output of
     * commands subsequently executed.
     *
     * @return {@code true} if this object will save the first line of the output of
     *         commands it executes subsequently; {@code false} otherwise
     */
    public boolean isSaveFirstLineOfOutput() {
        return outputStreamsControl.stdout().saveFirstLine();
    }

    /**
     * Specifies whether this object will dump the output streams from
     * subsequently executed commands into the streams returned by
     * {@link #dumpStdout()} and {@link #dumpStdout()} methods respectively.
     * <p>
     * If this object is configured to redirect stderr of subsequently executed
     * commands into their stdout ({@code redirectStderr(true)}), their output
     * streams will be dumped into the stream returned by {@code dumpStdout()}
     * method. Otherwise, their stdout and stderr streams will be dumped into the
     * stream returned by {@code dumpStdout()} and {@code dumpStderr()} methods
     * respectively.
     *
     * @param v if output streams from subsequently executed commands will be
     *          dumped into streams returned by {@code dumpStdout()} and
     *          {@code dumpStderr()} methods respectively
     *
     * @return this
     *
     * @see #redirectStderr(boolean)
     * @see #dumpStdout()
     * @see #dumpStderr()
     */
    public CommandOutputControl dumpOutput(boolean v) {
        setFlag(Flag.DUMP, v);
        return setOutputControl(v, OutputControlOption.DUMP);
    }

    /**
     * Returns the value passed in the last call of {@link #dumpOutput(boolean)}
     * method on this object, or {@code false} if the method has not been called.
     *
     * @return the value passed in the last call of {@link #dumpOutput(boolean)}
     */
    public boolean isDumpOutput() {
        return Flag.DUMP.isSet(flags);
    }

    /**
     * Specifies whether this object will treat output streams of subsequently
     * executed commands as byte streams rather than character streams.
     *
     * @param v {@code true} if this object will treat the output streams of
     *          subsequently executed commands as byte streams, and {@code false}
     *          otherwise
     *
     * @return this
     */
    public CommandOutputControl binaryOutput(boolean v) {
        return setFlag(Flag.BINARY_OUTPUT, v);
    }

    /**
     * Returns the value passed in the last call of {@link #binaryOutput(boolean)}
     * method on this object, or {@code false} if the method has not been called.
     *
     * @return the value passed in the last call of {@link #binaryOutput(boolean)}
     */
    public boolean isBinaryOutput() {
        return Flag.BINARY_OUTPUT.isSet(flags);
    }

    /**
     * Sets character encoding that will be applied to the stdout and the stderr
     * streams of commands (subprocesses and {@code ToolProvider}-s) subsequently
     * executed by this object. The default encoding is {@code UTF-8}.
     * <p>
     * The value will be ignored if this object is configured for byte output
     * streams.
     *
     * @param v character encoding for output streams of subsequently executed
     *          commands
     *
     * @see #binaryOutput(boolean)
     *
     * @return this
     */
    public CommandOutputControl charset(Charset v) {
        charset = v;
        return this;
    }

    /**
     * Returns the value passed in the last call of
     * {@link #charset(Charset)} method on this object, or
     * {@link StandardCharsets#UTF_8} if the method has not been called.
     *
     * @return the character encoding that will be applied to the stdout and stderr
     *         streams of commands subsequently executed by this object
     */
    public Charset charset() {
        return Optional.ofNullable(charset).orElse(StandardCharsets.UTF_8);
    }

    /**
     * Specifies whether the stderr stream will be redirected into the stdout stream
     * for commands subsequently executed by this object.
     *
     * @see ProcessBuilder#redirectErrorStream(boolean)
     *
     * @param v {@code true} if the stderr stream of commands subsequently executed
     *          by this object will be redirected into the stdout stream;
     *          {@code false} otherwise
     *
     * @return this
     */
    public CommandOutputControl redirectStderr(boolean v) {
        return setFlag(Flag.REDIRECT_STDERR, v);
    }

    /**
     * Returns the value passed in the last call of {@link #redirectStderr(boolean)}
     * method on this object, or {@code false} if the method has not been called.
     *
     * @return the value passed in the last call of {@link #redirectStderr(boolean)}
     */
    public boolean isRedirectStderr() {
        return Flag.REDIRECT_STDERR.isSet(flags);
    }

    /**
     * Specifies whether stderr and stdout streams for subprocesses subsequently
     * executed by this object will be stored in files.
     * <p>
     * By default, if an output stream of a subprocess is configured for saving,
     * this object will retrieve the content using {@link Process#getInputStream()}
     * function for stdout and {@link Process#getErrorStream()} function for stderr.
     * However, these functions don't always work correctly due to a
     * <a href="https://bugs.openjdk.org/browse/JDK-8236825">JDK-8236825</a> bug
     * still reproducible on macOS JDK26. The alternative way to get the content of
     * output streams of a subprocess is to redirect them into files and read these
     * files when the subprocess terminates.
     * <p>
     * It will use {@code Files.createTempFile("jpackageOutputTempFile", ".tmp")} to
     * create a file for each subprocess's output stream configured for saving. All
     * created files will be automatically deleted at the exit of
     * {@link #execute(ProcessBuilder, long)} method.
     * <p>
     * Doesn't apply to executing {@code ToolProvider}-s.
     * <p>
     * Storing output streams in files takes longer than managing them in memory and
     * should be avoided if possible.
     *
     * @param v {@code true} if this object will use files to store saved output
     *          streams of subsequently executed subprocesses; {@code false}
     *          otherwise
     * @return this
     */
    public CommandOutputControl storeOutputInFiles(boolean v) {
        return setFlag(Flag.STORE_OUTPUT_IN_FILES, v);
    }

    /**
     * Returns the value passed in the last call of {@link #storeOutputInFiles(boolean)}
     * method on this object, or {@code false} if the method has not been called.
     *
     * @return the value passed in the last call of {@link #storeOutputInFiles(boolean)}
     */
    public boolean isStoreOutputInFiles() {
        return Flag.STORE_OUTPUT_IN_FILES.isSet(flags);
    }

    /**
     * Specifies whether stdout streams from commands subsequently executed by this
     * object will be discarded.
     *
     * @param v {@code true} if this object will discard stdout streams from
     *          commands subsequently executed by this object; {@code false}
     *          otherwise
     * @return this
     */
    public CommandOutputControl discardStdout(boolean v) {
        setFlag(Flag.DISCARD_STDOUT, v);
        outputStreamsControl.stdout().discard(v);
        return this;
    }

    /**
     * Returns the value passed in the last call of {@link #discardStdout(boolean)}
     * method on this object, or {@code false} if the method has not been called.
     *
     * @return the value passed in the last call of {@link #discardStdout(boolean)}
     */
    public boolean isDiscardStdout() {
        return Flag.DISCARD_STDOUT.isSet(flags);
    }

    /**
     * Specifies whether stderr streams from commands subsequently executed by this
     * object will be discarded.
     *
     * @param v {@code true} if this object will discard stderr streams from
     *          commands subsequently executed by this object; {@code false}
     *          otherwise
     * @return this
     */
    public CommandOutputControl discardStderr(boolean v) {
        setFlag(Flag.DISCARD_STDERR, v);
        outputStreamsControl.stderr().discard(v);
        return this;
    }

    /**
     * Returns the value passed in the last call of {@link #discardStderr(boolean)}
     * method on this object, or {@code false} if the method has not been called.
     *
     * @return the value passed in the last call of {@link #discardStderr(boolean)}
     */
    public boolean isDiscardStderr() {
        return Flag.DISCARD_STDERR.isSet(flags);
    }

    /**
     * Specifies the stream where stdout streams from commands subsequently executed
     * by this object will be dumped.
     * <p>
     * If the {@code null} is specified and this object configuration is equivalent
     * to {@code dumpOutput(true).saveOutput(false).discardStdout(false)} the stdout
     * streams from commands subsequently executed by this object will be written
     * into the file descriptor associated with the {@code Systsem.out} stream. If
     * you want them to be written into the {@code Systsem.out} object, pass the
     * {@code Systsem.out} reference into this function.
     *
     * @param v the stream where stdout streams from commands subsequently executed
     *          by this object will be dumped; {@code null} permitted
     * @return this
     */
    public CommandOutputControl dumpStdout(PrintStream v) {
        dumpStdout = v;
        return this;
    }

    /**
     * Returns the value passed in the last call of {@link #dumpStdout(PrintStream)}
     * method on this object, or {@link System#out} if the method has not been
     * called.
     *
     * @return the stream where stdout streams from commands subsequently executed
     *         by this object will be dumped
     */
    public PrintStream dumpStdout() {
        return Optional.ofNullable(dumpStdout).orElse(System.out);
    }

    /**
     * Specifies the stream where stderr streams from commands subsequently executed
     * by this object will be dumped.
     * <p>
     * If the {@code null} is specified and this object configuration is equivalent
     * to
     * {@code dumpOutput(true).saveOutput(false).redirectStderr(false).discardStderr(false)}
     * the stderr streams from commands subsequently executed by this object will be
     * written into the file descriptor associated with the {@code Systsem.err}
     * stream. If you want them to be written into the {@code Systsem.err} object,
     * pass the {@code Systsem.err} reference into this function.
     *
     * @param v the stream where stderr streams from commands subsequently executed
     *          by this object will be dumped; {@code null} permitted
     * @return this
     */
    public CommandOutputControl dumpStderr(PrintStream v) {
        dumpStderr = v;
        return this;
    }

    /**
     * Returns the value passed in the last call of {@link #dumpStderr(PrintStream)}
     * method on this object, or {@link System#err} if the method has not been
     * called.
     *
     * @return the stream where stderr streams from commands subsequently executed
     *         by this object will be dumped
     */
    public PrintStream dumpStderr() {
        return Optional.ofNullable(dumpStderr).orElse(System.err);
    }

    /**
     * Sets the callback to be invoked when this object starts a subprocess from
     * subsequent {@link #execute(ProcessBuilder, long)} calls.
     *
     * <p>
     * This object maintains a single callback. Calling this method replaces any
     * previously set callback.
     *
     * <p>
     * The callback is invoked on the thread that calls
     * {@link #execute(ProcessBuilder, long)} after the subprocess's output streams
     * begin being pumped.
     *
     * @param v the callback for notifying a subprocess being started or
     *          {@code null}
     * @return this
     */
    public CommandOutputControl processListener(Consumer<Process> v) {
        processListener = v;
        return this;
    }

    /**
     * Returns an {@code Optional} wrapping the value passed in the last call of
     * {@link #processListener(Consumer)} method on this object, or an empty
     * {@code Optional} if the method has not been called or {@code null} was passed in the last call.
     *
     * @return an {@code Optional} wrapping the value passed in the last call of
     *         {@link #processListener(Consumer)}
     */
    public Optional<Consumer<Process>> processListener() {
        return Optional.ofNullable(processListener);
    }

    /**
     * Returns a deep copy of this object. Changes to the copy will not affect the
     * original.
     *
     * @return a deep copy of this object
     */
    public CommandOutputControl copy() {
        return new CommandOutputControl(this);
    }

    public interface ExecutableAttributes {
        List<String> commandLine();
    }

    public sealed interface Executable {

        ExecutableAttributes attributes();

        Result execute() throws IOException, InterruptedException;

        Result execute(long timeout, TimeUnit unit) throws IOException, InterruptedException;
    }

    public record ProcessAttributes(Optional<Long> pid, List<String> commandLine) implements ExecutableAttributes {
        public ProcessAttributes {
            Objects.requireNonNull(pid);
            commandLine.forEach(Objects::requireNonNull);
        }

        @Override
        public String toString() {
            return CommandLineFormat.DEFAULT.apply(commandLine());
        }
    }

    public record ToolProviderAttributes(String name, List<String> args) implements ExecutableAttributes {
        public ToolProviderAttributes {
            Objects.requireNonNull(name);
            args.forEach(Objects::requireNonNull);
        }

        @Override
        public String toString() {
            return CommandLineFormat.DEFAULT.apply(commandLine());
        }

        @Override
        public List<String> commandLine() {
            return Stream.concat(Stream.of(name), args.stream()).toList();
        }
    }

    public static ExecutableAttributes EMPTY_EXECUTABLE_ATTRIBUTES = new ExecutableAttributes() {
        @Override
        public String toString() {
            return "<unknown>";
        }

        @Override
        public List<String> commandLine() {
            return List.of();
        }
    };

    public Executable createExecutable(ToolProvider tp, String... args) {
        return new ToolProviderExecutable(tp, List.of(args), this);
    }

    public Executable createExecutable(ProcessBuilder pb) {
        return new ProcessExecutable(pb, this);
    }

    public record Result(
            Optional<Integer> exitCode,
            Optional<CommandOutput<List<String>>> output,
            Optional<CommandOutput<byte[]>> byteOutput,
            ExecutableAttributes execAttrs) {

        public Result {
            Objects.requireNonNull(exitCode);
            Objects.requireNonNull(output);
            Objects.requireNonNull(byteOutput);
            Objects.requireNonNull(execAttrs);
        }

        public Result(int exitCode) {
            this(Optional.of(exitCode), Optional.empty(), Optional.empty(), EMPTY_EXECUTABLE_ATTRIBUTES);
        }

        public int getExitCode() {
            return exitCode.orElseThrow(() -> {
                return new IllegalStateException("Exit code is unavailable for timed-out command");
            });
        }

        public Result expectExitCode(int main, int... other) throws UnexpectedExitCodeException {
            return expectExitCode(v -> {
                return IntStream.concat(IntStream.of(main), IntStream.of(other)).boxed().anyMatch(Predicate.isEqual(v));
            });
        }

        public Result expectExitCode(Collection<Integer> expected) throws UnexpectedExitCodeException {
            return expectExitCode(expected::contains);
        }

        public Result expectExitCode(IntPredicate expected) throws UnexpectedExitCodeException {
            if (!expected.test(getExitCode())) {
                throw new UnexpectedExitCodeException(this);
            }
            return this;
        }

        public UnexpectedResultException unexpected() {
            return new UnexpectedResultException(this);
        }

        public UnexpectedResultException unexpected(String message) {
            return new UnexpectedResultException(this, message);
        }

        public Optional<List<String>> findContent() {
            return output.flatMap(CommandOutput::combined);
        }

        public Optional<List<String>> findStdout() {
            return output.flatMap(CommandOutput::stdout);
        }

        public Optional<List<String>> findStderr() {
            return output.flatMap(CommandOutput::stderr);
        }

        // For backward compatibility
        public List<String> getOutput() {
            return content();
        }

        public List<String> content() {
            return findContent().orElseThrow();
        }

        public List<String> stdout() {
            return findStdout().orElseThrow();
        }

        public List<String> stderr() {
            return findStderr().orElseThrow();
        }

        public Optional<byte[]> findByteContent() {
            return byteOutput.flatMap(CommandOutput::combined);
        }

        public Optional<byte[]> findByteStdout() {
            return byteOutput.flatMap(CommandOutput::stdout);
        }

        public Optional<byte[]> findByteStderr() {
            return byteOutput.flatMap(CommandOutput::stderr);
        }

        public byte[] byteContent() {
            return findByteContent().orElseThrow();
        }

        public byte[] byteStdout() {
            return findByteStdout().orElseThrow();
        }

        public byte[] byteStderr() {
            return findByteStderr().orElseThrow();
        }

        public Result toCharacterResult(Charset charset, boolean keepByteContent) throws IOException {
            Objects.requireNonNull(charset);

            if (byteOutput.isEmpty()) {
                return this;
            }

            var theByteOutput = byteOutput.get();

            try {
                Optional<? extends Content<List<String>>> out;
                if (theByteOutput.content().isEmpty()) {
                    // The content is unavailable.
                    out = Optional.empty();
                } else if (theByteOutput.stdoutContentSize() == 0) {
                    // The content is available, but empty.
                    out = Optional.of(new StringListContent(List.of()));
                } else if (theByteOutput.interleaved()) {
                    // STDOUT and STDERR streams are interleaved.
                    out = theByteOutput.combined().map(data -> {
                        return toStringList(data, charset);
                    });
                } else {
                    // Non-empty STDOUT not interleaved with STDERR.
                    out = findByteStdout().map(data -> {
                        return toStringList(data, charset);
                    });
                }

                var err = findByteStderr().map(data -> {
                    return toStringList(data, charset);
                });

                var newOutput = combine(out, err, theByteOutput.interleaved);

                return new Result(exitCode, Optional.of(newOutput), byteOutput.filter(_ -> keepByteContent), execAttrs);
            } catch (UncheckedIOException ex) {
                throw ex.getCause();
            }
        }

        public Result copyWithExecutableAttributes(ExecutableAttributes execAttrs) {
            return new Result(exitCode, output, byteOutput, Objects.requireNonNull(execAttrs));
        }

        private static StringListContent toStringList(byte[] data, Charset charset) {
            try (var bufReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data), charset))) {
                return new StringListContent(bufReader.lines().toList());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    public static sealed class UnexpectedResultException extends IOException {

        private UnexpectedResultException(Result value, String message) {
            super(Objects.requireNonNull(message));
            this.value = Objects.requireNonNull(value);
        }

        private UnexpectedResultException(Result value) {
            this(value, String.format("Unexpected result from executing the command %s", value.execAttrs()));
        }

        public Result getResult() {
            return value;
        }

        private final transient Result value;

        private static final long serialVersionUID = 1L;
    }

    public static final class UnexpectedExitCodeException extends UnexpectedResultException {

        public UnexpectedExitCodeException(Result value, String message) {
            super(value, message);
        }

        public UnexpectedExitCodeException(Result value) {
            this(value, String.format("Unexpected exit code %d from executing the command %s", value.getExitCode(), value.execAttrs()));
        }

        private static final long serialVersionUID = 1L;
    }

    public String description() {
        var tokens = outputStreamsControl.descriptionTokens();
        if (isBinaryOutput()) {
            tokens.add("byte");
        }
        if (redirectRetainedStderr()) {
            tokens.add("interleave");
        }
        return String.join("; ", tokens);
    }

    private Result execute(ProcessBuilder pb, long timeoutMillis)
            throws IOException, InterruptedException {

        Objects.requireNonNull(pb);

        var theCharset = charset();

        configureProcessBuilder(pb);

        var csc = new CachingStreamsConfig();

        var process = pb.start();

        BiConsumer<InputStream, PrintStream> gobbler = (in, ps) -> {
            try {
                if (isBinaryOutput()) {
                    try (in) {
                        in.transferTo(ps);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                } else {
                    try (var bufReader = new BufferedReader(new InputStreamReader(in, theCharset))) {
                        bufReader.lines().forEach(ps::println);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                }
            } finally {
                suppressIOException(ps::flush);
            }
        };

        // Start fetching process output streams.
        // Do it before waiting for the process termination to avoid deadlocks.

        final Optional<CompletableFuture<Void>> stdoutGobbler;
        if (mustReadOutputStream(pb.redirectOutput())) {
            stdoutGobbler = Optional.of(CompletableFuture.runAsync(() -> {
                gobbler.accept(process.getInputStream(), csc.out());
            }, gobblerExecutor));
        } else {
            stdoutGobbler = Optional.empty();
        }

        final Optional<CompletableFuture<Void>> stderrGobbler;
        if (!pb.redirectErrorStream() && mustReadOutputStream(pb.redirectError())) {
            stderrGobbler = Optional.of(CompletableFuture.runAsync(() -> {
                gobbler.accept(process.getErrorStream(), csc.err());
            }, gobblerExecutor));
        } else {
            stderrGobbler = Optional.empty();
        }

        processListener().ifPresent(c -> {
            c.accept(process);
        });

        final Optional<Integer> exitCode;
        if (timeoutMillis < 0) {
            exitCode = Optional.of(process.waitFor());
        } else if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
            // Destroy the process and cancel the process output stream gobblers.
            process.destroy();
            for (var g : List.of(stdoutGobbler, stderrGobbler)) {
                g.ifPresent(future -> {
                    future.cancel(true);
                });
            }
            exitCode = Optional.empty();
        } else {
            exitCode = Optional.of(process.exitValue());
        }

        try {
            if (isStoreOutputInFiles()) {
                var stdoutStorage = streamFileSink(pb.redirectOutput());
                var stderrStorage = streamFileSink(pb.redirectError());

                Function<Path, InputStream> toInputStream = path -> {
                    try {
                        return Files.newInputStream(path);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                };

                try {
                    stdoutStorage.map(toInputStream).ifPresent(in -> {
                        gobbler.accept(in, csc.out());
                    });

                    stderrStorage.map(toInputStream).ifPresent(in -> {
                        gobbler.accept(in, csc.err());
                    });
                } finally {
                    Consumer<Path> silentDeleter = path -> {
                        suppressIOException(Files::delete, path);
                    };

                    stdoutStorage.ifPresent(silentDeleter);
                    stderrStorage.ifPresent(silentDeleter);
                }
            } else {
                stdoutGobbler.ifPresent(CommandOutputControl::join);
                stderrGobbler.ifPresent(CommandOutputControl::join);
            }
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }

        return csc.createResult(exitCode, new ProcessAttributes(getPID(process), pb.command()));
    }

    private Result execute(ToolProvider tp, long timeoutMillis, String... args)
            throws IOException, InterruptedException {

        var csc = new CachingStreamsConfig();

        Optional<Integer> exitCode;
        var out = csc.out();
        var err = csc.err();
        try {
            if (timeoutMillis < 0) {
                exitCode = Optional.of(tp.run(out, err, args));
            } else {
                var future = new CompletableFuture<Optional<Integer>>();

                var workerThread = Thread.ofVirtual().start(() -> {
                    Optional<Integer> result = Optional.empty();
                    try {
                        result = Optional.of(tp.run(out, err, args));
                    } catch (Exception ex) {
                        future.completeExceptionally(ex);
                        return;
                    }
                    future.complete(result);
                });

                try {
                    exitCode = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
                } catch (ExecutionException ex) {
                    // Rethrow the cause (ex.getCause()) as a RuntimeException.
                    // If `ex.getCause()` returns an Error, ExceptionBox.unbox() will throw it.
                    throw ExceptionBox.toUnchecked(ExceptionBox.unbox(ex.getCause()));
                } catch (TimeoutException ex) {
                    workerThread.interrupt();
                    exitCode = Optional.empty();
                }
            }
        } finally {
            suppressIOException(out::flush);
            suppressIOException(err::flush);
        }

        return csc.createResult(exitCode, new ToolProviderAttributes(tp.name(), List.of(args)));
    }

    private CommandOutputControl setOutputControl(boolean set, OutputControlOption v) {
        outputStreamsControl.stdout().set(set, v);
        outputStreamsControl.stderr().set(set, v);
        return this;
    }

    private CommandOutputControl setFlag(Flag flag, boolean v) {
        flags = flag.set(flags, v);
        return this;
    }

    private Optional<Path> streamFileSink(ProcessBuilder.Redirect redirect) {
        return Optional.of(redirect)
                .filter(Predicate.isEqual(ProcessBuilder.Redirect.DISCARD).negate())
                .map(ProcessBuilder.Redirect::file)
                .map(File::toPath);
    }

    private void configureProcessBuilder(ProcessBuilder pb) throws IOException {

        var stdoutRedirect = outputStreamsControl.stdout().asProcessBuilderRedirect();
        var stderrRedirect = outputStreamsControl.stderr().asProcessBuilderRedirect();

        if (!stdoutRedirect.equals(stderrRedirect) && Stream.of(
                stdoutRedirect,
                stderrRedirect
        ).noneMatch(Predicate.isEqual(ProcessBuilder.Redirect.DISCARD)) && redirectRetainedStderr()) {
            throw new IllegalStateException(String.format(
                    "Can't redirect stderr into stdout because they have different redirects: stdout=%s; stderr=%s",
                    stdoutRedirect, stderrRedirect));
        }

        pb.redirectErrorStream(redirectRetainedStderr());
        if (replaceStdoutWithStderr()) {
            if (stderrRedirect.equals(ProcessBuilder.Redirect.INHERIT)) {
                stderrRedirect = ProcessBuilder.Redirect.PIPE;
            }
            pb.redirectErrorStream(false);
        }

        stdoutRedirect = mapRedirect(stdoutRedirect);
        stderrRedirect = mapRedirect(stderrRedirect);

        if (dumpStdout != null && stdoutRedirect.equals(ProcessBuilder.Redirect.INHERIT)) {
            stdoutRedirect = ProcessBuilder.Redirect.PIPE;
        }

        if (dumpStderr != null && stderrRedirect.equals(ProcessBuilder.Redirect.INHERIT)) {
            stderrRedirect = ProcessBuilder.Redirect.PIPE;
        }

        pb.redirectOutput(stdoutRedirect);
        pb.redirectError(stderrRedirect);
    }

    private ProcessBuilder.Redirect mapRedirect(ProcessBuilder.Redirect redirect) throws IOException {
        if (isStoreOutputInFiles() && redirect.equals(ProcessBuilder.Redirect.PIPE)) {
            var sink = Files.createTempFile("jpackageOutputTempFile", ".tmp");
            return ProcessBuilder.Redirect.to(sink.toFile());
        } else {
            return redirect;
        }
    }

    /**
     * Returns {@code true} if STDERR is not discarded and will be redirected to STDOUT, and {@code false} otherwise.
     */
    private boolean redirectRetainedStderr() {
        return isRedirectStderr() && !outputStreamsControl.stderr().discard();
    }

    /**
     * Returns {@code true} if STDERR will replace STDOUT, and {@code false} otherwise.
     * <p>
     * STDERR will replace STDOUT if it is redirected and not discarded, and if STDOUT is discarded.
     */
    private boolean replaceStdoutWithStderr() {
        return redirectRetainedStderr() && outputStreamsControl.stdout().discard();
    }

    private static <T> T join(CompletableFuture<T> future, T cancelledValue) {
        Objects.requireNonNull(future);
        try {
            return future.join();
        } catch (CancellationException ex) {
            return cancelledValue;
        } catch (CompletionException ex) {
            switch (ExceptionBox.unbox(ex.getCause())) {
                case IOException cause -> {
                    throw new UncheckedIOException(cause);
                }
                case UncheckedIOException cause -> {
                    throw cause;
                }
                case Exception cause -> {
                    throw ExceptionBox.toUnchecked(cause);
                }
            }
        }
    }

    private static void join(CompletableFuture<Void> future) {
        join(future, null);
    }

    private static boolean mustReadOutputStream(ProcessBuilder.Redirect redirect) {
        return redirect.equals(ProcessBuilder.Redirect.PIPE);
    }

    private static Optional<List<String>> read(OutputControl outputControl, CachingPrintStream cps) throws IOException {
        final var bufferAsString = cps.bufferContents();
        try (final var bufReader = new BufferedReader(new StringReader(bufferAsString.orElse("")))) {
            if (outputControl.saveFirstLine()) {
                return Optional.of(bufReader.lines().findFirst().map(List::of).orElseGet(List::of));
            } else if (outputControl.saveAll()) {
                return Optional.of(bufReader.lines().toList());
            } else {
                return Optional.empty();
            }
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private static Optional<byte[]> readBinary(OutputControl outputControl, CachingPrintStream cps) {
        if (outputControl.save()) {
            return cps.buf().map(ByteArrayOutputStream::toByteArray).or(() -> {
                return Optional.of(new byte[0]);
            });
        } else {
            return Optional.empty();
        }
    }

    private static <T> CommandOutput<T> combine(
            Optional<? extends Content<T>> out,
            Optional<? extends Content<T>> err,
            boolean interleaved) {

        if (out.isEmpty() && err.isEmpty()) {
            return CommandOutput.empty();
        } else if (out.isEmpty()) {
            // This branch is unreachable because it is impossible to make it save stderr without saving stdout.
            // If streams are configured for saving and stdout is discarded,
            // its saved contents will be an Optional instance wrapping an empty content, not an empty Optional.
            throw ExceptionBox.reachedUnreachable();
        } else if (err.isEmpty()) {
            return new CommandOutput<>(out, Integer.MAX_VALUE, interleaved);
        } else {
            final var combined = out.get().append(err.get());
            return new CommandOutput<>(Optional.of(combined), out.orElseThrow().size(), interleaved);
        }
    }

    private static PrintStream nullPrintStream() {
        return new PrintStream(OutputStream.nullOutputStream());
    }

    private sealed interface Content<T> {
        T data();
        int size();
        Content<T> slice(int from, int to);
        Content<T> append(Content<T> other);
    }

    private record StringListContent(List<String> data) implements Content<List<String>> {
        StringListContent {
            Objects.requireNonNull(data);
        }

        @Override
        public int size() {
            return data.size();
        }

        @Override
        public StringListContent slice(int from, int to) {
            return new StringListContent(data.subList(from, to));
        }

        @Override
        public StringListContent append(Content<List<String>> other) {
            return new StringListContent(Stream.of(data, other.data()).flatMap(List::stream).toList());
        }
    }

    private record ByteContent(byte[] data) implements Content<byte[]> {
        ByteContent {
            Objects.requireNonNull(data);
        }

        @Override
        public int size() {
            return data.length;
        }

        @Override
        public ByteContent slice(int from, int to) {
            return new ByteContent(Arrays.copyOfRange(data, from, to));
        }

        @Override
        public ByteContent append(Content<byte[]> other) {
            byte[] combined = new byte[size() + other.size()];
            System.arraycopy(data, 0, combined, 0, data.length);
            System.arraycopy(other.data(), 0, combined, data.length, other.size());
            return new ByteContent(combined);
        }
    }

    private record OutputStreamsControl(OutputControl stdout, OutputControl stderr) {
        OutputStreamsControl {
            Objects.requireNonNull(stdout);
            Objects.requireNonNull(stderr);
        }

        OutputStreamsControl() {
            this(new OutputControl(), new OutputControl());
        }

        OutputStreamsControl copy() {
            return new OutputStreamsControl(stdout.copy(), stderr.copy());
        }

        List<String> descriptionTokens() {
            final List<String> tokens = new ArrayList<>();
            if (stdout.save()) { // Save flags are the same for stdout and stderr, checking stdout is sufficient.
                streamsLabel("save ", true).ifPresent(tokens::add);
            }
            if (stdout.dump() || stderr.dump()) {
                streamsLabel("echo ", true).ifPresent(tokens::add);
            }
            streamsLabel("discard ", false).ifPresent(tokens::add);
            if (tokens.isEmpty()) {
                // Unreachable because there is always at least one token in the description.
                throw ExceptionBox.reachedUnreachable();
            } else {
                return tokens;
            }
        }

        private Optional<String> streamsLabel(String prefix, boolean negate) {
            Objects.requireNonNull(prefix);
            final var str = Stream.of(stdoutLabel(negate), stderrLabel(negate))
                    .filter(Optional::isPresent)
                    .map(Optional::orElseThrow)
                    .collect(joining("+"));
            if (str.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(prefix + str);
            }
        }

        private Optional<String> stdoutLabel(boolean negate) {
            if ((stdout.discard() && !negate) || (!stdout.discard() && negate)) {
                return Optional.of("out");
            } else {
                return Optional.empty();
            }
        }

        private Optional<String> stderrLabel(boolean negate) {
            if ((stderr.discard() && !negate) || (!stderr.discard() && negate)) {
                return Optional.of("err");
            } else {
                return Optional.empty();
            }
        }
    }

    private record CachingPrintStream(PrintStream ps, Optional<ByteArrayOutputStream> buf) {
        CachingPrintStream {
            Objects.requireNonNull(ps);
            Objects.requireNonNull(buf);
        }

        Optional<String> bufferContents() {
            return buf.map(ByteArrayOutputStream::toString);
        }

        static Builder build(Charset charset) {
            return new Builder(charset);
        }

        static final class Builder {

            private Builder(Charset charset) {
                this.charset = Objects.requireNonNull(charset);
            }

            Builder save(boolean v) {
                save = v;
                return this;
            }

            Builder discard(boolean v) {
                discard = v;
                return this;
            }

            Builder dumpStream(PrintStream v) {
                dumpStream = v;
                return this;
            }

            Builder buffer(ByteArrayOutputStream v) {
                externalBuffer = v;
                return this;
            }

            CachingPrintStream create() {
                final Optional<ByteArrayOutputStream> buf;
                if (save && !discard) {
                    buf = Optional.ofNullable(externalBuffer).or(() -> {
                        return Optional.of(new ByteArrayOutputStream());
                    });
                } else {
                    buf = Optional.empty();
                }

                final PrintStream ps;
                if (buf.isPresent() && dumpStream != null) {
                    ps = new PrintStream(new TeeOutputStream(List.of(buf.get(), dumpStream)), true, dumpStream.charset());
                } else if (!discard) {
                    ps = buf.map(in -> {
                        return new PrintStream(in, false, charset);
                    }).or(() -> {
                        return Optional.ofNullable(dumpStream);
                    }).orElseGet(CommandOutputControl::nullPrintStream);
                } else {
                    ps = nullPrintStream();
                }

                return new CachingPrintStream(ps, buf);
            }

            private boolean save;
            private boolean discard;
            private PrintStream dumpStream;
            private ByteArrayOutputStream externalBuffer;
            private final Charset charset;
        }
    }

    private final class CachingStreamsConfig {

        CachingStreamsConfig() {
            out = outputStreamsControl.stdout().buildCachingPrintStream(dumpStdout(), charset()).create();
            if (isRedirectStderr()) {
                var builder = outputStreamsControl.stderr().buildCachingPrintStream(dumpStdout(), charset());
                out.buf().ifPresent(builder::buffer);
                err = builder.create();
            } else {
                err = outputStreamsControl.stderr().buildCachingPrintStream(dumpStderr(), charset()).create();
            }
        }

        Result createResult(Optional<Integer> exitCode, ExecutableAttributes execAttrs) throws IOException {

            CommandOutput<List<String>> output;
            CommandOutput<byte[]> byteOutput;

            CachingPrintStream effectiveOut;
            if (out.buf().isEmpty() && isRedirectStderr()) {
                effectiveOut = new CachingPrintStream(nullPrintStream(), err.buf());
            } else {
                effectiveOut = out;
            }

            if (isBinaryOutput()) {
                Optional<ByteContent> outContent, errContent;
                if (isRedirectStderr()) {
                    outContent = readBinary(outputStreamsControl.stdout(), effectiveOut).map(ByteContent::new);
                    errContent = Optional.empty();
                } else {
                    outContent = readBinary(outputStreamsControl.stdout(), out).map(ByteContent::new);
                    errContent = readBinary(outputStreamsControl.stderr(), err).map(ByteContent::new);
                }

                byteOutput = combine(outContent, errContent, redirectRetainedStderr());
                output = null;
            } else {
                Optional<StringListContent> outContent, errContent;
                if (isRedirectStderr()) {
                    outContent = read(outputStreamsControl.stdout(), effectiveOut).map(StringListContent::new);
                    errContent = Optional.empty();
                } else {
                    outContent = read(outputStreamsControl.stdout(), out).map(StringListContent::new);
                    errContent = read(outputStreamsControl.stderr(), err).map(StringListContent::new);
                }

                output = combine(outContent, errContent, redirectRetainedStderr());
                byteOutput = null;
            }

            return new Result(exitCode, Optional.ofNullable(output), Optional.ofNullable(byteOutput), execAttrs);
        }

        PrintStream out() {
            return out.ps();
        }

        PrintStream err() {
            return err.ps();
        }

        private final CachingPrintStream out;
        private final CachingPrintStream err;
    }

    private static final class OutputControl {

        OutputControl() {
        }

        private OutputControl(OutputControl other) {
            dump = other.dump;
            discard = other.discard;
            save = other.save;
        }

        boolean save() {
            return save.isPresent();
        }

        boolean saveAll() {
            return save.orElse(null) == OutputControlOption.SAVE_ALL;
        }

        boolean saveFirstLine() {
            return save.orElse(null) == OutputControlOption.SAVE_FIRST_LINE;
        }

        boolean discard() {
            return discard || (!dump && save.isEmpty());
        }

        boolean dump() {
            return !discard && dump;
        }

        OutputControl dump(boolean v) {
            this.dump = v;
            return this;
        }

        OutputControl discard(boolean v) {
            this.discard = v;
            return this;
        }

        OutputControl saveAll(boolean v) {
            if (v) {
                save = Optional.of(OutputControlOption.SAVE_ALL);
            } else {
                save = Optional.empty();
            }
            return this;
        }

        OutputControl saveFirstLine(boolean v) {
            if (v) {
                save = Optional.of(OutputControlOption.SAVE_FIRST_LINE);
            } else {
                save = Optional.empty();
            }
            return this;
        }

        OutputControl set(boolean set, OutputControlOption v) {
            switch (v) {
            case DUMP -> dump(set);
            case SAVE_ALL -> saveAll(set);
            case SAVE_FIRST_LINE -> saveFirstLine(set);
            }
            return this;
        }

        OutputControl copy() {
            return new OutputControl(this);
        }

        ProcessBuilder.Redirect asProcessBuilderRedirect() {
            if (discard()) {
                return ProcessBuilder.Redirect.DISCARD;
            } else if (dump && !save()) {
                return ProcessBuilder.Redirect.INHERIT;
            } else {
                return ProcessBuilder.Redirect.PIPE;
            }
        }

        CachingPrintStream.Builder buildCachingPrintStream(PrintStream dumpStream, Charset charset) {
            Objects.requireNonNull(dumpStream);
            final var builder = CachingPrintStream.build(charset).save(save()).discard(discard());
            if (dump()) {
                builder.dumpStream(dumpStream);
            }
            return builder;
        }

        private boolean dump;
        private boolean discard;
        private Optional<OutputControlOption> save = Optional.empty();
    }

    private record CommandOutput<T>(Optional<? extends Content<T>> content, int stdoutContentSize, boolean interleaved) {

        CommandOutput {
            Objects.requireNonNull(content);
            if (interleaved) {
                stdoutContentSize = content.map(Content::size).orElse(-1);
            }
        }

        CommandOutput() {
            this(Optional.empty(), 0, false);
        }

        Optional<T> combined() {
            return content.map(Content::data);
        }

        /**
         * Returns non-empty {@code Optional} if stdout is available and stdout and stderr are not interleaved.
         * @return stdout if it can be extracted from the combined output
         */
        Optional<T> stdout() {
            if (withoutExtractableStdout()) {
                return Optional.empty();
            }

            final var theContent = content.orElseThrow();
            if (stdoutContentSize == theContent.size()) {
                return combined();
            } else {
                return Optional.of(theContent.slice(0, Integer.min(stdoutContentSize, theContent.size())).data());
            }
        }

        /**
         * Returns non-empty {@code Optional} if stderr is available and stdout and stderr are not interleaved.
         * @return stderr if it can be extracted from the combined output
         */
        Optional<T> stderr() {
            if (withoutExtractableStderr()) {
                return Optional.empty();
            } else if (stdoutContentSize <= 0) {
                return combined();
            } else {
                final var theContent = content.orElseThrow();
                return Optional.of(theContent.slice(stdoutContentSize, theContent.size()).data());
            }
        }

        @SuppressWarnings("unchecked")
        static <T> CommandOutput<T> empty() {
            return (CommandOutput<T>)EMPTY;
        }

        private boolean withoutExtractableStdout() {
            return interleaved || content.isEmpty() || stdoutContentSize < 0;
        }

        private boolean withoutExtractableStderr() {
            return interleaved || content.isEmpty() || stdoutContentSize > content.get().size();
        }

        private static final CommandOutput<?> EMPTY = new CommandOutput<>();
    }

    private record ToolProviderExecutable(ToolProvider tp, List<String> args, CommandOutputControl coc) implements Executable {

        ToolProviderExecutable {
            Objects.requireNonNull(tp);
            Objects.requireNonNull(args);
            Objects.requireNonNull(coc);
        }

        @Override
        public Result execute() throws IOException, InterruptedException {
            return coc.execute(tp, -1, args.toArray(String[]::new));
        }

        @Override
        public Result execute(long timeout, TimeUnit unit) throws IOException, InterruptedException {
            return coc.execute(tp, unit.toMillis(timeout), args.toArray(String[]::new));
        }

        @Override
        public ExecutableAttributes attributes() {
            return new ToolProviderAttributes(tp.name(), args);
        }
    }

    private record ProcessExecutable(ProcessBuilder pb, CommandOutputControl coc) implements Executable {

        ProcessExecutable {
            Objects.requireNonNull(pb);
            Objects.requireNonNull(coc);
        }

        @Override
        public Result execute() throws IOException, InterruptedException {
            return coc.execute(pb, -1L);
        }

        @Override
        public Result execute(long timeout, TimeUnit unit) throws IOException, InterruptedException {
            return coc.execute(pb, unit.toMillis(timeout));
        }

        @Override
        public ExecutableAttributes attributes() {
            return new ProcessAttributes(Optional.empty(), pb.command());
        }
    }

    private static Optional<Long> getPID(Process p) {
        try {
            return Optional.of(p.pid());
        } catch (UnsupportedOperationException ex) {
            return Optional.empty();
        }
    }

    private static void suppressIOException(ThrowingRunnable<IOException> r) {
        try {
            r.run();
        } catch (IOException ex) {}
    }

    private static <T> void suppressIOException(ThrowingConsumer<T, IOException> c, T value) {
        suppressIOException(() -> {
            c.accept(value);
        });
    }

    private int flags;
    private final OutputStreamsControl outputStreamsControl;
    private PrintStream dumpStdout;
    private PrintStream dumpStderr;
    private Charset charset;
    private Consumer<Process> processListener;

    // Executor to run subprocess output stream gobblers.
    // Output stream gobblers should start fetching output streams ASAP after the process starts.
    // No pooling, no waiting.
    // CompletableFuture#runAsync() method starts an output stream gobbler.
    // If used with the default executor, it is known to make WiX3 light.exe create
    // a locked msi file when multiple jpackage tool providers are executed asynchronously.
    // The AsyncTest fails with cryptic java.nio.file.FileSystemException error:
    // jtreg_open_test_jdk_tools_jpackage_share_AsyncTest_java\\tmp\\jdk.jpackage8108811639097525318\\msi\\Foo-1.0.msi: The process cannot access the file because it is being used by another process.
    // The remedy for the problem is to use non-pooling executor to run subprocess output stream gobblers.
    private final java.util.concurrent.Executor gobblerExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private enum OutputControlOption {
        SAVE_ALL, SAVE_FIRST_LINE, DUMP
    }

    private enum Flag {
        DUMP                    (0x01),
        REDIRECT_STDERR         (0x02),
        BINARY_OUTPUT           (0x04),
        STORE_OUTPUT_IN_FILES   (0x08),
        DISCARD_STDOUT          (0x10),
        DISCARD_STDERR          (0x20),
        ;

        Flag(int value) {
            this.value = value;
        }

        int set(int flags, boolean set) {
            if (set) {
                return flags | value;
            } else {
                return flags & ~value;
            }
        }

        boolean isSet(int flags) {
            return (flags & value) != 0;
        }

        private final int value;
    }
}
