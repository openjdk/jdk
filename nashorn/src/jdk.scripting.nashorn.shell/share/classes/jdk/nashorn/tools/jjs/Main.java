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

package jdk.nashorn.tools.jjs;

import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import jdk.internal.jline.console.completer.Completer;
import jdk.internal.jline.console.UserInterruptException;
import jdk.nashorn.api.scripting.NashornException;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.objects.NativeJava;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.NativeJavaPackage;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.tools.Shell;

/**
 * Interactive command line Shell for Nashorn.
 */
public final class Main extends Shell {
    private Main() {}

    static final boolean DEBUG = Boolean.getBoolean("nashorn.jjs.debug");
    static final boolean HEADLESS = GraphicsEnvironment.isHeadless();

    // file where history is persisted.
    private static final File HIST_FILE = new File(new File(System.getProperty("user.home")), ".jjs.history");

    /**
     * Main entry point with the default input, output and error streams.
     *
     * @param args The command line arguments
     */
    public static void main(final String[] args) {
        try {
            final int exitCode = main(System.in, System.out, System.err, args);
            if (exitCode != SUCCESS) {
                System.exit(exitCode);
            }
        } catch (final IOException e) {
            System.err.println(e); //bootstrapping, Context.err may not exist
            System.exit(IO_ERROR);
        }
    }

    /**
     * Starting point for executing a {@code Shell}. Starts a shell with the
     * given arguments and streams and lets it run until exit.
     *
     * @param in input stream for Shell
     * @param out output stream for Shell
     * @param err error stream for Shell
     * @param args arguments to Shell
     *
     * @return exit code
     *
     * @throws IOException if there's a problem setting up the streams
     */
    public static int main(final InputStream in, final OutputStream out, final OutputStream err, final String[] args) throws IOException {
        return new Main().run(in, out, err, args);
    }


    /**
     * read-eval-print loop for Nashorn shell.
     *
     * @param context the nashorn context
     * @param global  global scope object to use
     * @return return code
     */
    protected int readEvalPrint(final Context context, final Global global) {
        final ScriptEnvironment env = context.getEnv();
        final String prompt = bundle.getString("shell.prompt");
        final String prompt2 = bundle.getString("shell.prompt2");
        final PrintWriter err = context.getErr();
        final Global oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);
        final PropertiesHelper propsHelper = new PropertiesHelper(env._classpath);
        final NashornCompleter completer = new NashornCompleter(context, global, this, propsHelper);

        try (final Console in = new Console(System.in, System.out, HIST_FILE, completer,
                str -> {
                    try {
                        final Object res = context.eval(global, str, global, "<shell>");
                        if (res != null && res != UNDEFINED) {
                            // Special case Java types: show the javadoc for the class.
                            if (NativeJava.isType(UNDEFINED, res)) {
                                final String typeName = NativeJava.typeName(UNDEFINED, res).toString();
                                final String url = typeName.replace('.', '/').replace('$', '.') + ".html";
                                openBrowserForJavadoc(url);
                            } else if (res instanceof NativeJavaPackage) {
                                final String pkgName = ((NativeJavaPackage)res).getName();
                                final String url = pkgName.replace('.', '/') + "/package-summary.html";
                                openBrowserForJavadoc(url);
                            } else if (res instanceof ScriptFunction) {
                                return ((ScriptFunction)res).getDocumentation();
                            }

                            // FIXME: better than toString for other cases?
                            return JSType.toString(res);
                        }
                     } catch (Exception ignored) {
                     }
                     return null;
                })) {

            if (globalChanged) {
                Context.setGlobal(global);
            }

            global.addShellBuiltins();

            if (System.getSecurityManager() == null) {
                final Consumer<String> evaluator = str -> {
                    // could be called from different thread (GUI), we need to handle Context set/reset
                    final Global _oldGlobal = Context.getGlobal();
                    final boolean _globalChanged = (oldGlobal != global);
                    if (_globalChanged) {
                        Context.setGlobal(global);
                    }
                    try {
                        evalImpl(context, global, str, err, env._dump_on_error);
                    } finally {
                        if (_globalChanged) {
                            Context.setGlobal(_oldGlobal);
                        }
                    }
                };

                // expose history object for reflecting on command line history
                global.addOwnProperty("history", Property.NOT_ENUMERABLE, new HistoryObject(in.getHistory(), err, evaluator));

                // 'edit' command
                global.addOwnProperty("edit", Property.NOT_ENUMERABLE, new EditObject(in, err::println, evaluator));
            }

            while (true) {
                String source = "";
                try {
                    source = in.readLine(prompt);
                } catch (final IOException ioe) {
                    err.println(ioe.toString());
                    if (env._dump_on_error) {
                        ioe.printStackTrace(err);
                    }
                    return IO_ERROR;
                } catch (final UserInterruptException ex) {
                    break;
                }

                if (source == null) {
                    break;
                }

                if (source.isEmpty()) {
                    continue;
                }

                try {
                    final Object res = context.eval(global, source, global, "<shell>");
                    if (res != UNDEFINED) {
                        err.println(toString(res, global));
                    }
                } catch (final Exception exp) {
                    // Is this a ECMAScript SyntaxError at last column (of the single line)?
                    // If so, it is because parser expected more input but got EOF. Try to
                    // to more lines from the user (multiline edit support).

                    if (completer.isSyntaxErrorAt(exp, 1, source.length())) {
                        final String fullSrc = completer.readMoreLines(source, exp, in, prompt2, err);

                        // check if we succeeded in getting complete code.
                        if (fullSrc != null && !fullSrc.isEmpty()) {
                            evalImpl(context, global, fullSrc, err, env._dump_on_error);
                        } // else ignore, error reported already by 'completer.readMoreLines'
                    } else {

                        // can't read more lines to have parseable/complete code.
                        err.println(exp);
                        if (env._dump_on_error) {
                            exp.printStackTrace(err);
                        }
                    }
                }
            }
        } catch (final Exception e) {
            err.println(e);
            if (env._dump_on_error) {
                e.printStackTrace(err);
            }
        } finally {
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
            }
            try {
                propsHelper.close();
            } catch (final Exception exp) {
                if (DEBUG) {
                    exp.printStackTrace();
                }
            }
        }

        return SUCCESS;
    }

    static String getMessage(final String id) {
        return bundle.getString(id);
    }

    private void evalImpl(final Context context, final Global global, final String source,
            final PrintWriter err, final boolean doe) {
        try {
            final Object res = context.eval(global, source, global, "<shell>");
            if (res != UNDEFINED) {
                err.println(JSType.toString(res));
            }
        } catch (final Exception e) {
            err.println(e);
            if (doe) {
                e.printStackTrace(err);
            }
        }
    }

    // FIXME: needs to be changed to use javase 9 docs later
    private static String JAVADOC_BASE = "http://download.java.net/jdk9/docs/api/";

    private static void openBrowserForJavadoc(String relativeUrl) {
        try {
            final URI uri = new URI(JAVADOC_BASE + relativeUrl);
            Desktop.getDesktop().browse(uri);
        } catch (Exception ignored) {
        }
    }
}
