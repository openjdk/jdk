/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

import java.io.PrintStream;
import java.util.Iterator;

import org.graalvm.compiler.debug.DebugContext.DisabledScope;

import jdk.vm.ci.meta.JavaMethod;

public final class ScopeImpl implements DebugContext.Scope {

    private final class IndentImpl implements Indent {

        private static final String INDENTATION_INCREMENT = "  ";

        final String indent;
        final IndentImpl parentIndent;

        IndentImpl(IndentImpl parentIndent) {
            this.parentIndent = parentIndent;
            this.indent = (parentIndent == null ? "" : parentIndent.indent + INDENTATION_INCREMENT);
        }

        private boolean logScopeName() {
            return logScopeName;
        }

        private void printScopeName(StringBuilder str, boolean isCurrent) {
            if (logScopeName) {
                boolean parentPrinted = false;
                if (parentIndent != null) {
                    parentPrinted = parentIndent.logScopeName();
                    parentIndent.printScopeName(str, false);
                }
                /*
                 * Always print the current scope, scopes with context and the any scope whose
                 * parent didn't print. This ensure the first new scope always shows up.
                 */
                if (isCurrent || printContext(null) != 0 || !parentPrinted) {
                    str.append(indent).append("[thread:").append(Thread.currentThread().getId()).append("] scope: ").append(getQualifiedName()).append(System.lineSeparator());
                }
                printContext(str);
                logScopeName = false;
            }
        }

        /**
         * Print or count the context objects for the current scope.
         */
        private int printContext(StringBuilder str) {
            int count = 0;
            if (context != null && context.length > 0) {
                // Include some context in the scope output
                for (Object contextObj : context) {
                    if (contextObj instanceof JavaMethodContext || contextObj instanceof JavaMethod) {
                        if (str != null) {
                            str.append(indent).append("Context: ").append(contextObj).append(System.lineSeparator());
                        }
                        count++;
                    }
                }
            }
            return count;
        }

        public void log(int logLevel, String msg, Object... args) {
            if (isLogEnabled(logLevel)) {
                StringBuilder str = new StringBuilder();
                printScopeName(str, true);
                str.append(indent);
                String result = args.length == 0 ? msg : String.format(msg, args);
                String lineSep = System.lineSeparator();
                str.append(result.replace(lineSep, lineSep.concat(indent)));
                str.append(lineSep);
                output.append(str);
                lastUsedIndent = this;
            }
        }

        IndentImpl indent() {
            lastUsedIndent = new IndentImpl(this);
            return lastUsedIndent;
        }

        @Override
        public void close() {
            if (parentIndent != null) {
                lastUsedIndent = parentIndent;
            }
        }
    }

    private final DebugContext owner;
    private final ScopeImpl parent;
    private final boolean sandbox;
    private IndentImpl lastUsedIndent;
    private boolean logScopeName;

    private final Object[] context;

    private String qualifiedName;
    private final String unqualifiedName;

    private static final char SCOPE_SEP = '.';

    private boolean countEnabled;
    private boolean timeEnabled;
    private boolean memUseTrackingEnabled;
    private boolean verifyEnabled;

    private int currentDumpLevel;
    private int currentLogLevel;

    private PrintStream output;
    private boolean interceptDisabled;

    static final Object[] EMPTY_CONTEXT = new Object[0];

    ScopeImpl(DebugContext owner, Thread thread) {
        this(owner, thread.getName(), null, false);
    }

    ScopeImpl(DebugContext owner, String unqualifiedName, ScopeImpl parent, boolean sandbox, Object... context) {
        this.owner = owner;
        this.parent = parent;
        this.sandbox = sandbox;
        this.context = context;
        this.unqualifiedName = unqualifiedName;
        if (parent != null) {
            logScopeName = !unqualifiedName.equals("");
            this.interceptDisabled = parent.interceptDisabled;
        } else {
            logScopeName = true;
        }

        this.output = TTY.out;
        assert context != null;
    }

    @Override
    public void close() {
        owner.currentScope = parent;
        owner.lastClosedScope = this;
    }

    boolean isTopLevel() {
        return parent == null;
    }

    public boolean isDumpEnabled(int dumpLevel) {
        assert dumpLevel >= 0;
        return currentDumpLevel >= dumpLevel;
    }

    public boolean isVerifyEnabled() {
        return verifyEnabled;
    }

    public boolean isLogEnabled(int logLevel) {
        assert logLevel > 0;
        return currentLogLevel >= logLevel;
    }

    public boolean isCountEnabled() {
        return countEnabled;
    }

    public boolean isTimeEnabled() {
        return timeEnabled;
    }

    public boolean isMemUseTrackingEnabled() {
        return memUseTrackingEnabled;
    }

    public void log(int logLevel, String msg, Object... args) {
        assert owner.checkNoConcurrentAccess();
        if (isLogEnabled(logLevel)) {
            getLastUsedIndent().log(logLevel, msg, args);
        }
    }

    public void dump(int dumpLevel, Object object, String formatString, Object... args) {
        assert isDumpEnabled(dumpLevel);
        if (isDumpEnabled(dumpLevel)) {
            DebugConfig config = getConfig();
            if (config != null) {
                for (DebugDumpHandler dumpHandler : config.dumpHandlers()) {
                    dumpHandler.dump(owner, object, formatString, args);
                }
            }
        }
    }

    private DebugConfig getConfig() {
        return owner.currentConfig;
    }

    /**
     * @see DebugContext#verify(Object, String)
     */
    public void verify(Object object, String formatString, Object... args) {
        if (isVerifyEnabled()) {
            DebugConfig config = getConfig();
            if (config != null) {
                String message = String.format(formatString, args);
                for (DebugVerifyHandler handler : config.verifyHandlers()) {
                    handler.verify(owner, object, message);
                }
            }
        }
    }

    /**
     * Creates and enters a new scope which is either a child of the current scope or a disjoint top
     * level scope.
     *
     * @param name the name of the new scope
     * @param sandboxConfig the configuration to use for a new top level scope, or null if the new
     *            scope should be a child scope
     * @param newContextObjects objects to be appended to the debug context
     * @return the new scope which will be exited when its {@link #close()} method is called
     */
    public ScopeImpl scope(CharSequence name, DebugConfig sandboxConfig, Object... newContextObjects) {
        ScopeImpl newScope = null;
        if (sandboxConfig != null) {
            newScope = new ScopeImpl(owner, name.toString(), this, true, newContextObjects);
        } else {
            newScope = this.createChild(name.toString(), newContextObjects);
        }
        newScope.updateFlags(owner.currentConfig);
        return newScope;
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <E extends Exception> RuntimeException silenceException(Class<E> type, Throwable ex) throws E {
        throw (E) ex;
    }

    public RuntimeException handle(Throwable e) {
        try {
            if (owner.lastClosedScope instanceof ScopeImpl) {
                ScopeImpl lastClosed = (ScopeImpl) owner.lastClosedScope;
                assert lastClosed.parent == this : "DebugContext.handle() used without closing a scope opened by DebugContext.scope(...) or DebugContext.sandbox(...) " +
                                "or an exception occurred while opening a scope";
                if (e != owner.lastExceptionThrown) {
                    RuntimeException newException = null;
                    // Make the scope in which the exception was thrown
                    // the current scope again.
                    owner.currentScope = lastClosed;

                    // When this try block exits, the above action will be undone
                    try (ScopeImpl s = lastClosed) {
                        newException = s.interceptException(e);
                    }

                    // Checks that the action really is undone
                    assert owner.currentScope == this;
                    assert lastClosed == owner.lastClosedScope;

                    if (newException == null) {
                        owner.lastExceptionThrown = e;
                    } else {
                        owner.lastExceptionThrown = newException;
                        throw newException;
                    }
                }
            } else if (owner.lastClosedScope == null) {
                throw new AssertionError("DebugContext.handle() used without closing a scope opened by DebugContext.scope(...) or DebugContext.sandbox(...) " +
                                "or an exception occurred while opening a scope");
            } else {
                assert owner.lastClosedScope instanceof DisabledScope : owner.lastClosedScope;
            }
        } catch (Throwable t) {
            t.initCause(e);
            throw t;
        }

        if (e instanceof Error) {
            throw (Error) e;
        }
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        }
        throw silenceException(RuntimeException.class, e);
    }

    void updateFlags(DebugConfig config) {
        if (config == null) {
            countEnabled = false;
            memUseTrackingEnabled = false;
            timeEnabled = false;
            verifyEnabled = false;
            currentDumpLevel = -1;
            // Be pragmatic: provide a default log stream to prevent a crash if the stream is not
            // set while logging
            output = TTY.out;
        } else {
            countEnabled = config.isCountEnabled(this);
            memUseTrackingEnabled = config.isMemUseTrackingEnabled(this);
            timeEnabled = config.isTimeEnabled(this);
            verifyEnabled = config.isVerifyEnabled(this);
            output = config.output();
            currentDumpLevel = config.getDumpLevel(this);
            currentLogLevel = config.getLogLevel(this);
        }
    }

    DebugCloseable disableIntercept() {
        boolean previous = interceptDisabled;
        interceptDisabled = true;
        return new DebugCloseable() {
            @Override
            public void close() {
                interceptDisabled = previous;
            }
        };
    }

    @SuppressWarnings("try")
    private RuntimeException interceptException(final Throwable e) {
        if (!interceptDisabled && owner.currentConfig != null) {
            try (ScopeImpl s = scope("InterceptException", null, e)) {
                return owner.currentConfig.interceptException(owner, e);
            } catch (Throwable t) {
                return new RuntimeException("Exception while intercepting exception", t);
            }
        }
        return null;
    }

    private ScopeImpl createChild(String newName, Object[] newContext) {
        return new ScopeImpl(owner, newName, this, false, newContext);
    }

    @Override
    public Iterable<Object> getCurrentContext() {
        final ScopeImpl scope = this;
        return new Iterable<Object>() {

            @Override
            public Iterator<Object> iterator() {
                return new Iterator<Object>() {

                    ScopeImpl currentScope = scope;
                    int objectIndex;

                    @Override
                    public boolean hasNext() {
                        selectScope();
                        return currentScope != null;
                    }

                    private void selectScope() {
                        while (currentScope != null && currentScope.context.length <= objectIndex) {
                            currentScope = currentScope.sandbox ? null : currentScope.parent;
                            objectIndex = 0;
                        }
                    }

                    @Override
                    public Object next() {
                        selectScope();
                        if (currentScope != null) {
                            return currentScope.context[objectIndex++];
                        }
                        throw new IllegalStateException("May only be called if there is a next element.");
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException("This iterator is read only.");
                    }
                };
            }
        };
    }

    @Override
    public String getQualifiedName() {
        if (qualifiedName == null) {
            if (parent == null) {
                qualifiedName = unqualifiedName;
            } else {
                qualifiedName = parent.getQualifiedName() + SCOPE_SEP + unqualifiedName;
            }
        }
        return qualifiedName;
    }

    public Indent pushIndentLogger() {
        lastUsedIndent = getLastUsedIndent().indent();
        return lastUsedIndent;
    }

    public IndentImpl getLastUsedIndent() {
        if (lastUsedIndent == null) {
            if (parent != null) {
                lastUsedIndent = new IndentImpl(parent.getLastUsedIndent());
            } else {
                lastUsedIndent = new IndentImpl(null);
            }
        }
        return lastUsedIndent;
    }
}
