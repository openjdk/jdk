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

package jdk.jshell;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import java.util.function.Supplier;
import jdk.internal.jshell.debug.InternalDebugControl;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static jdk.internal.jshell.debug.InternalDebugControl.DBG_EVNT;
import static jdk.jshell.Util.expunge;
import jdk.jshell.Snippet.Status;

/**
 * The JShell evaluation state engine.  This is the central class in the JShell
 * API.  A <code>JShell</code> instance holds the evolving compilation and
 * execution state.  The state is changed with the instance methods
 * {@link jdk.jshell.JShell#eval(java.lang.String) eval(String)},
 * {@link jdk.jshell.JShell#drop(jdk.jshell.PersistentSnippet) drop(PersistentSnippet)} and
 * {@link jdk.jshell.JShell#addToClasspath(java.lang.String) addToClasspath(String)}.
 * The majority of methods query the state.
 * A <code>JShell</code> instance also allows registering for events with
 * {@link jdk.jshell.JShell#onSnippetEvent(java.util.function.Consumer) onSnippetEvent(Consumer)}
 * and {@link jdk.jshell.JShell#onShutdown(java.util.function.Consumer) onShutdown(Consumer)}, which
 * are unregistered with
 * {@link jdk.jshell.JShell#unsubscribe(jdk.jshell.JShell.Subscription) unsubscribe(Subscription)}.
 * Access to the source analysis utilities is via
 * {@link jdk.jshell.JShell#sourceCodeAnalysis()}.
 * When complete the instance should be closed to free resources --
 * {@link jdk.jshell.JShell#close()}.
 * <p>
 * An instance of <code>JShell</code> is created with
 * <code>JShell.create()</code>.
 * <p>
 * This class is not thread safe, except as noted, all access should be through
 * a single thread.
 * @see jdk.jshell
 * @author Robert Field
 */
public class JShell implements AutoCloseable {

    final SnippetMaps maps;
    final KeyMap keyMap;
    final TaskFactory taskFactory;
    final InputStream in;
    final PrintStream out;
    final PrintStream err;
    final Supplier<String> tempVariableNameGenerator;
    final BiFunction<Snippet, Integer, String> idGenerator;

    private int nextKeyIndex = 1;

    final Eval eval;
    final ClassTracker classTracker;
    private final Map<Subscription, Consumer<JShell>> shutdownListeners = new HashMap<>();
    private final Map<Subscription, Consumer<SnippetEvent>> keyStatusListeners = new HashMap<>();
    private boolean closed = false;


    private ExecutionControl executionControl = null;
    private SourceCodeAnalysisImpl sourceCodeAnalysis = null;


    JShell(Builder b) {
        this.in = b.in;
        this.out = b.out;
        this.err = b.err;
        this.tempVariableNameGenerator = b.tempVariableNameGenerator;
        this.idGenerator = b.idGenerator;

        this.maps = new SnippetMaps(this);
        maps.setPackageName("REPL");
        this.keyMap = new KeyMap(this);
        this.taskFactory = new TaskFactory(this);
        this.eval = new Eval(this);
        this.classTracker = new ClassTracker(this);
    }

    /**
     * Builder for <code>JShell</code> instances.
     * Create custom instances of <code>JShell</code> by using the setter
     * methods on this class.  After zero or more of these, use the
     * {@link #build()} method to create a <code>JShell</code> instance.
     * These can all be chained. For example, setting the remote output and
     * error streams:
     * <pre>
     * <code>
     *     JShell myShell =
     *       JShell.builder()
     *         .out(myOutStream)
     *         .err(myErrStream)
     *         .build(); </code> </pre>
     * If no special set-up is needed, just use
     * <code>JShell.builder().build()</code> or the short-cut equivalent
     * <code>JShell.create()</code>.
     */
    public static class Builder {

        InputStream in = new ByteArrayInputStream(new byte[0]);
        PrintStream out = System.out;
        PrintStream err = System.err;
        Supplier<String> tempVariableNameGenerator = null;
        BiFunction<Snippet, Integer, String> idGenerator = null;

        Builder() { }

        /**
         * Input for the running evaluation (it's <code>System.in</code>). Note:
         * applications that use <code>System.in</code> for snippet or other
         * user input cannot use <code>System.in</code> as the input stream for
         * the remote process.
         * <p>
         * The default, if this is not set, is to provide an empty input stream
         * -- <code>new ByteArrayInputStream(new byte[0])</code>.
         *
         * @param in the <code>InputStream</code> to be channelled to
         * <code>System.in</code> in the remote execution process.
         * @return the <code>Builder</code> instance (for use in chained
         * initialization).
         */
        public Builder in(InputStream in) {
            this.in = in;
            return this;
        }

        /**
         * Output for the running evaluation (it's <code>System.out</code>).
         * The controlling process and
         * the remote process can share <code>System.out</code>.
         * <p>
         * The default, if this is not set, is <code>System.out</code>.
         *
         * @param out the <code>PrintStream</code> to be channelled to
         * <code>System.out</code> in the remote execution process.
         * @return the <code>Builder</code> instance (for use in chained
         * initialization).
         */
        public Builder out(PrintStream out) {
            this.out = out;
            return this;
        }

        /**
         * Error output for the running evaluation (it's
         * <code>System.err</code>). The controlling process and the remote
         * process can share <code>System.err</code>.
         * <p>
         * The default, if this is not set, is <code>System.err</code>.
         *
         * @param err the <code>PrintStream</code> to be channelled to
         * <code>System.err</code> in the remote execution process.
         * @return the <code>Builder</code> instance (for use in chained
         * initialization).
         */
        public Builder err(PrintStream err) {
            this.err = err;
            return this;
        }

        /**
         * Set a generator of temp variable names for
         * {@link jdk.jshell.VarSnippet} of
         * {@link jdk.jshell.Snippet.SubKind#TEMP_VAR_EXPRESSION_SUBKIND}.
         * <p>
         * Do not use this method unless you have explicit need for it.
         * <p>
         * The generator will be used for newly created VarSnippet
         * instances. The name of a variable is queried with
         * {@link jdk.jshell.VarSnippet#name()}.
         * <p>
         * The callback is sent during the processing of the snippet, the
         * JShell state is not stable. No calls whatsoever on the
         * <code>JShell</code> instance may be made from the callback.
         * <p>
         * The generated name must be unique within active snippets.
         * <p>
         * The default behavior (if this is not set or <code>generator</code>
         * is null) is to generate the name as a sequential number with a
         * prefixing dollar sign ("$").
         *
         * @param generator the <code>Supplier</code> to generate the temporary
         * variable name string or <code>null</code>.
         * @return the <code>Builder</code> instance (for use in chained
         * initialization).
         */
        public Builder tempVariableNameGenerator(Supplier<String> generator) {
            this.tempVariableNameGenerator = generator;
            return this;
        }

        /**
         * Set the generator of identifying names for Snippets.
         * <p>
         * Do not use this method unless you have explicit need for it.
         * <p>
         * The generator will be used for newly created Snippet instances. The
         * identifying name (id) is accessed with
         * {@link jdk.jshell.Snippet#id()} and can be seen in the
         * <code>StackTraceElement.getFileName()</code> for a
         * {@link jdk.jshell.EvalException} and
         * {@link jdk.jshell.UnresolvedReferenceException}.
         * <p>
         * The inputs to the generator are the {@link jdk.jshell.Snippet} and an
         * integer. The integer will be the same for two Snippets which would
         * overwrite one-another, but otherwise is unique.
         * <p>
         * The callback is sent during the processing of the snippet and the
         * Snippet and the state as a whole are not stable. No calls to change
         * system state (including Snippet state) should be made. Queries of
         * Snippet may be made except to {@link jdk.jshell.Snippet#id()}. No
         * calls on the <code>JShell</code> instance may be made from the
         * callback, except to
         * {@link #status(jdk.jshell.Snippet) status(Snippet)}.
         * <p>
         * The default behavior (if this is not set or <code>generator</code>
         * is null) is to generate the id as the integer converted to a string.
         *
         * @param generator the <code>BiFunction</code> to generate the id
         * string or <code>null</code>.
         * @return the <code>Builder</code> instance (for use in chained
         * initialization).
         */
        public Builder idGenerator(BiFunction<Snippet, Integer, String> generator) {
            this.idGenerator = generator;
            return this;
        }

        /**
         * Build a JShell state engine. This is the entry-point to all JShell
         * functionality. This creates a remote process for execution. It is
         * thus important to close the returned instance.
         *
         * @return the state engine.
         */
        public JShell build() {
            return new JShell(this);
        }
    }

    // --- public API ---

    /**
     * Create a new JShell state engine.
     * That is, create an instance of <code>JShell</code>.
     * <p>
     * Equivalent to {@link JShell#builder() JShell.builder()}{@link JShell.Builder#build() .build()}.
     * @return an instance of <code>JShell</code>.
     */
    public static JShell create() {
        return builder().build();
    }

    /**
     * Factory method for <code>JShell.Builder</code> which, in-turn, is used
     * for creating instances of <code>JShell</code>.
     * Create a default instance of <code>JShell</code> with
     * <code>JShell.builder().build()</code>. For more construction options
     * see {@link jdk.jshell.JShell.Builder}.
     * @return an instance of <code>Builder</code>.
     * @see jdk.jshell.JShell.Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Access to source code analysis functionality.
     * An instance of <code>JShell</code> will always return the same
     * <code>SourceCodeAnalysis</code> instance from
     * <code>sourceCodeAnalysis()</code>.
     * @return an instance of {@link SourceCodeAnalysis SourceCodeAnalysis}
     * which can be used for source analysis such as completion detection and
     * completion suggestions.
     */
    public SourceCodeAnalysis sourceCodeAnalysis() {
        if (sourceCodeAnalysis == null) {
            sourceCodeAnalysis = new SourceCodeAnalysisImpl(this);
        }
        return sourceCodeAnalysis;
    }

    /**
     * Evaluate the input String, including definition and/or execution, if
     * applicable. The input is checked for errors, unless the errors can be
     * deferred (as is the case with some unresolvedDependencies references),
     * errors will abort evaluation. The input should be
     * exactly one complete snippet of source code, that is, one expression,
     * statement, variable declaration, method declaration, class declaration,
     * or import.
     * To break arbitrary input into individual complete snippets, use
     * {@link SourceCodeAnalysis#analyzeCompletion(String)}.
     * <p>
     * For imports, the import is added.  Classes, interfaces. methods,
     * and variables are defined.  The initializer of variables, statements,
     * and expressions are executed.
     * The modifiers public, protected, private, static, and final are not
     * allowed on op-level declarations and are ignored with a warning.
     * Synchronized, native, abstract, and default top-level methods are not
     * allowed and are errors.
     * If a previous definition of a declaration is overwritten then there will
     * be an event showing its status changed to OVERWRITTEN, this will not
     * occur for dropped, rejected, or already overwritten declarations.
     * <p>
     * The execution environment is out of process.  If the evaluated code
     * causes the execution environment to terminate, this <code>JShell</code>
     * instance will be closed but the calling process and VM remain valid.
     * @param input The input String to evaluate
     * @return the list of events directly or indirectly caused by this evaluation.
     * @throws IllegalStateException if this <code>JShell</code> instance is closed.
     * @see SourceCodeAnalysis#analyzeCompletion(String)
     * @see JShell#onShutdown(java.util.function.Consumer)
     */
    public List<SnippetEvent> eval(String input) throws IllegalStateException {
        SourceCodeAnalysisImpl a = sourceCodeAnalysis;
        if (a != null) {
            a.suspendIndexing();
        }
        try {
            checkIfAlive();
            List<SnippetEvent> events = eval.eval(input);
            events.forEach(this::notifyKeyStatusEvent);
            return Collections.unmodifiableList(events);
        } finally {
            if (a != null) {
                a.resumeIndexing();
            }
        }
    }

    /**
     * Remove a declaration from the state.
     * @param snippet The snippet to remove
     * @return The list of events from updating declarations dependent on the
     * dropped snippet.
     * @throws IllegalStateException if this <code>JShell</code> instance is closed.
     * @throws IllegalArgumentException if the snippet is not associated with
     * this <code>JShell</code> instance.
     */
    public List<SnippetEvent> drop(PersistentSnippet snippet) throws IllegalStateException {
        checkIfAlive();
        checkValidSnippet(snippet);
        List<SnippetEvent> events = eval.drop(snippet);
        events.forEach(this::notifyKeyStatusEvent);
        return Collections.unmodifiableList(events);
    }

    /**
     * The specified path is added to the end of the classpath used in eval().
     * Note that the unnamed package is not accessible from the package in which
     * {@link JShell#eval()} code is placed.
     * @param path the path to add to the classpath.
     */
    public void addToClasspath(String path) {
        taskFactory.addToClasspath(path);  // Compiler
        executionControl().commandAddToClasspath(path);       // Runtime
        if (sourceCodeAnalysis != null) {
            sourceCodeAnalysis.classpathChanged();
        }
    }

    /**
     * Attempt to stop currently running evaluation. When called while
     * the {@link #eval(java.lang.String) } method is running and the
     * user's code being executed, an attempt will be made to stop user's code.
     * Note that typically this method needs to be called from a different thread
     * than the one running the {@code eval} method.
     * <p>
     * If the {@link #eval(java.lang.String) } method is not running, does nothing.
     * <p>
     * The attempt to stop the user's code may fail in some case, which may include
     * when the execution is blocked on an I/O operation, or when the user's code is
     * catching the {@link ThreadDeath} exception.
     */
    public void stop() {
        if (executionControl != null)
            executionControl.commandStop();
    }

    /**
     * Close this state engine. Frees resources. Should be called when this
     * state engine is no longer needed.
     */
    @Override
    public void close() {
        if (!closed) {
            closeDown();
            executionControl().commandExit();
        }
    }

    /**
     * Return all snippets.
     * @return the snippets for all current snippets in id order.
     * @throws IllegalStateException if this JShell instance is closed.
     */
    public List<Snippet> snippets() throws IllegalStateException {
        checkIfAlive();
        return Collections.unmodifiableList(maps.snippetList());
    }

    /**
     * Returns the active variable snippets.
     * This convenience method is equivalent to <code>snippets()</code> filtered for
     * {@link jdk.jshell.Snippet.Status#isActive status(snippet).isActive}
     * <code>&amp;&amp; snippet.kind() == Kind.VARIABLE</code>
     * and cast to <code>VarSnippet</code>.
     * @return the active declared variables.
     * @throws IllegalStateException if this JShell instance is closed.
     */
    public List<VarSnippet> variables() throws IllegalStateException {
        return snippets().stream()
                     .filter(sn -> status(sn).isActive && sn.kind() == Snippet.Kind.VAR)
                     .map(sn -> (VarSnippet) sn)
                     .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /**
     * Returns the active method snippets.
     * This convenience method is equivalent to <code>snippets()</code> filtered for
     * {@link jdk.jshell.Snippet.Status#isActive status(snippet).isActive}
     * <code>&amp;&amp; snippet.kind() == Kind.METHOD</code>
     * and cast to MethodSnippet.
     * @return the active declared methods.
     * @throws IllegalStateException if this JShell instance is closed.
     */
    public List<MethodSnippet> methods() throws IllegalStateException {
        return snippets().stream()
                     .filter(sn -> status(sn).isActive && sn.kind() == Snippet.Kind.METHOD)
                     .map(sn -> (MethodSnippet)sn)
                     .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /**
     * Returns the active type declaration (class, interface, annotation type, and enum) snippets.
     * This convenience method is equivalent to <code>snippets()</code> filtered for
     * {@link jdk.jshell.Snippet.Status#isActive status(snippet).isActive}
     * <code>&amp;&amp; snippet.kind() == Kind.TYPE_DECL</code>
     * and cast to TypeDeclSnippet.
     * @return the active declared type declarations.
     * @throws IllegalStateException if this JShell instance is closed.
     */
    public List<TypeDeclSnippet> types() throws IllegalStateException {
        return snippets().stream()
                .filter(sn -> status(sn).isActive && sn.kind() == Snippet.Kind.TYPE_DECL)
                .map(sn -> (TypeDeclSnippet) sn)
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /**
     * Returns the active import snippets.
     * This convenience method is equivalent to <code>snippets()</code> filtered for
     * {@link jdk.jshell.Snippet.Status#isActive status(snippet).isActive}
     * <code>&amp;&amp; snippet.kind() == Kind.IMPORT</code>
     * and cast to ImportSnippet.
     * @return the active declared import declarations.
     * @throws IllegalStateException if this JShell instance is closed.
     */
    public List<ImportSnippet> imports() throws IllegalStateException {
        return snippets().stream()
                .filter(sn -> status(sn).isActive && sn.kind() == Snippet.Kind.IMPORT)
                .map(sn -> (ImportSnippet) sn)
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /**
     * Return the status of the snippet.
     * This is updated either because of an explicit <code>eval()</code> call or
     * an automatic update triggered by a dependency.
     * @param snippet the <code>Snippet</code> to look up
     * @return the status corresponding to this snippet
     * @throws IllegalStateException if this <code>JShell</code> instance is closed.
     * @throws IllegalArgumentException if the snippet is not associated with
     * this <code>JShell</code> instance.
     */
    public Status status(Snippet snippet) {
        return checkValidSnippet(snippet).status();
    }

    /**
     * Return the diagnostics of the most recent evaluation of the snippet.
     * The evaluation can either because of an explicit <code>eval()</code> call or
     * an automatic update triggered by a dependency.
     * @param snippet the <code>Snippet</code> to look up
     * @return the diagnostics corresponding to this snippet.  This does not
     * include unresolvedDependencies references reported in <code>unresolvedDependencies()</code>.
     * @throws IllegalStateException if this <code>JShell</code> instance is closed.
     * @throws IllegalArgumentException if the snippet is not associated with
     * this <code>JShell</code> instance.
     */
    public List<Diag> diagnostics(Snippet snippet) {
        return Collections.unmodifiableList(checkValidSnippet(snippet).diagnostics());
    }

    /**
     * For {@link jdk.jshell.Snippet.Status#RECOVERABLE_DEFINED RECOVERABLE_DEFINED} or
     * {@link jdk.jshell.Snippet.Status#RECOVERABLE_NOT_DEFINED RECOVERABLE_NOT_DEFINED}
     * declarations, the names of current unresolved dependencies for
     * the snippet.
     * The returned value of this method, for a given method may change when an
     * <code>eval()</code> or <code>drop()</code> of another snippet causes
     * an update of a dependency.
     * @param snippet the declaration <code>Snippet</code> to look up
     * @return the list of symbol names that are currently unresolvedDependencies.
     * @throws IllegalStateException if this <code>JShell</code> instance is closed.
     * @throws IllegalArgumentException if the snippet is not associated with
     * this <code>JShell</code> instance.
     */
    public List<String> unresolvedDependencies(DeclarationSnippet snippet) {
        return Collections.unmodifiableList(checkValidSnippet(snippet).unresolved());
    }

    /**
     * Get the current value of a variable.
     * @param snippet the variable Snippet whose value is queried.
     * @return the current value of the variable referenced by snippet.
     * @throws IllegalStateException if this <code>JShell</code> instance is closed.
     * @throws IllegalArgumentException if the snippet is not associated with
     * this <code>JShell</code> instance.
     * @throws IllegalArgumentException if the variable's status is anything but
     * {@link jdk.jshell.Snippet.Status#VALID}.
     */
    public String varValue(VarSnippet snippet) throws IllegalStateException {
        checkIfAlive();
        checkValidSnippet(snippet);
        if (snippet.status() != Status.VALID) {
            throw new IllegalArgumentException("Snippet parameter of varValue() '" +
                    snippet + "' must be VALID, it is: " + snippet.status());
        }
        String value = executionControl().commandVarValue(maps.classFullName(snippet), snippet.name());
        return expunge(value);
    }

    /**
     * Register a callback to be called when the Status of a snippet changes.
     * Each call adds a new subscription.
     * @param listener Action to perform when the Status changes.
     * @return A token which can be used to {@linkplain JShell#unsubscribe unsubscribe} this subscription.
     * @throws IllegalStateException if this <code>JShell</code> instance is closed.
     */
    public Subscription onSnippetEvent(Consumer<SnippetEvent> listener)
            throws IllegalStateException {
        return onX(keyStatusListeners, listener);
    }

    /**
     * Register a callback to be called when this JShell instance terminates.
     * This occurs either because the client process has ended (e.g. called System.exit(0))
     * or the connection has been shutdown, as by close().
     * Each call adds a new subscription.
     * @param listener Action to perform when the state terminates.
     * @return A token which can be used to {@linkplain JShell#unsubscribe unsubscribe} this subscription.
     * @throws IllegalStateException if this JShell instance is closed
     */
    public Subscription onShutdown(Consumer<JShell> listener)
            throws IllegalStateException {
        return onX(shutdownListeners, listener);
    }

    /**
     * Cancel a callback subscription.
     * @param token The token corresponding to the subscription to be unsubscribed.
     */
    public void unsubscribe(Subscription token) {
        synchronized (this) {
            token.remover.accept(token);
        }
    }

    /**
     * Subscription is a token for referring to subscriptions so they can
     * be {@linkplain JShell#unsubscribe unsubscribed}.
     */
    public class Subscription {

        Consumer<Subscription> remover;

        Subscription(Consumer<Subscription> remover) {
            this.remover = remover;
        }
    }

    // --- private / package-private implementation support ---

    ExecutionControl executionControl() {
        if (executionControl == null) {
            this.executionControl = new ExecutionControl(new JDIEnv(this), maps, this);
            try {
                executionControl.launch();
            } catch (IOException ex) {
                throw new InternalError("Launching JDI execution engine threw: " + ex.getMessage(), ex);
            }
        }
        return executionControl;
    }

    void debug(int flags, String format, Object... args) {
        if (InternalDebugControl.debugEnabled(this, flags)) {
            err.printf(format, args);
        }
    }

    void debug(Exception ex, String where) {
        if (InternalDebugControl.debugEnabled(this, 0xFFFFFFFF)) {
            err.printf("Fatal error: %s: %s\n", where, ex.getMessage());
            ex.printStackTrace(err);
        }
    }

    /**
     * Generate the next key index, indicating a unique snippet signature.
     * @return the next key index
     */
    int nextKeyIndex() {
        return nextKeyIndex++;
    }

    private synchronized <T> Subscription onX(Map<Subscription, Consumer<T>> map, Consumer<T> listener)
            throws IllegalStateException {
        Objects.requireNonNull(listener);
        checkIfAlive();
        Subscription token = new Subscription(map::remove);
        map.put(token, listener);
        return token;
    }

    private synchronized void notifyKeyStatusEvent(SnippetEvent event) {
        keyStatusListeners.values().forEach(l -> l.accept(event));
    }

    private synchronized void notifyShutdownEvent(JShell state) {
        shutdownListeners.values().forEach(l -> l.accept(state));
    }

    void closeDown() {
        if (!closed) {
            // Send only once
            closed = true;
            notifyShutdownEvent(this);
        }
    }

    /**
     * Check if this JShell has been closed
     * @throws IllegalStateException if it is closed
     */
    private void checkIfAlive()  throws IllegalStateException {
        if (closed) {
            throw new IllegalStateException("JShell (" + this + ") has been closed.");
        }
    }

    /**
     * Check a Snippet parameter coming from the API user
     * @param sn the Snippet to check
     * @throws NullPointerException if Snippet parameter is null
     * @throws IllegalArgumentException if Snippet is not from this JShell
     * @return the input Snippet (for chained calls)
     */
    private Snippet checkValidSnippet(Snippet sn) {
        if (sn == null) {
            throw new NullPointerException("Snippet must not be null");
        } else {
            if (sn.key().state() != this) {
                throw new IllegalArgumentException("Snippet not from this JShell");
            }
            return sn;
        }
    }

}
