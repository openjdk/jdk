/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.wscompile;

import com.sun.codemodel.internal.JCodeModel;
import com.sun.tools.internal.ws.processor.model.Model;
import java.io.IOException;
import org.xml.sax.SAXException;

/**
 * Add-on that works on the generated source code.
 *
 * <p> This add-on will be called after the default generation has finished.
 *
 * @author Lukas Jungmann
 * @since 2.2.6
 */
public abstract class Plugin {

    /**
     * Gets the option name to turn on this add-on.
     *
     * <p> For example, if "abc" is returned, "-abc" will turn on this plugin. A
     * plugin needs to be turned on explicitly, or else no other methods of {@link Plugin}
     * will be invoked.
     *
     * <p> When an option matches the name returned from this method, WsImport
     * will then invoke {@link #parseArgument(Options, String[], int)}, allowing
     * plugins to handle arguments to this option.
     */
    public abstract String getOptionName();

    /**
     * Gets the description of this add-on. Used to generate a usage screen.
     *
     * @return localized description message. should be terminated by \n.
     */
    public abstract String getUsage();

    /**
     * Parses an option <code>args[i]</code> and augment the <code>opt</code> object
     * appropriately, then return the number of tokens consumed.
     *
     * <p> The callee doesn't need to recognize the option that the
     * getOptionName method returns.
     *
     * <p> Once a plugin is activated, this method is called for options that
     * WsImport didn't recognize. This allows a plugin to define additional
     * options to customize its behavior.
     *
     * <p> Since options can appear in no particular order, WsImport allows
     * sub-options of a plugin to show up before the option that activates a
     * plugin (one that's returned by {@link #getOptionName()}.)
     *
     * But nevertheless a {@link Plugin} needs to be activated to participate in
     * further processing.
     *
     * @return 0 if the argument is not understood. Otherwise return the number
     * of tokens that are consumed, including the option itself. (so if you have
     * an option like "-foo 3", return 2.)
     * @exception BadCommandLineException If the option was recognized but
     * there's an error. This halts the argument parsing process and causes
     * WsImport to abort, reporting an error.
     */
    public int parseArgument(Options opt, String[] args, int i) throws BadCommandLineException, IOException {
        return 0;
    }

    /**
     * Notifies a plugin that it's activated.
     *
     * <p> This method is called when a plugin is activated through the command
     * line option (as specified by {@link #getOptionName()}.
     *
     * <p> Noop by default.
     *
     */
    public void onActivated(Options opts) throws BadCommandLineException {
        // noop
    }

    /**
     * Run the add-on.
     *
     * <p> This method is invoked after WsImport has internally finished the
     * code generation. Plugins can tweak some of the generated code (or add
     * more code) by altering {@link JCodeModel} obtained from {@link WsimportOptions#getCodeModel()
     * } according to the current
     * {@link Model WSDL model} and {@link WsimportOptions}.
     *
     * <p> Note that this method is invoked only when a {@link Plugin} is
     * activated.
     *
     * @param wsdlModel This object allows access to the WSDL model used for
     * code generation.
     *
     * @param options This object allows access to various options used for code
     * generation as well as access to the generated code.
     *
     * @param errorReceiver Errors should be reported to this handler.
     *
     * @return If the add-on executes successfully, return true. If it detects
     * some errors but those are reported and recovered gracefully, return
     * false.
     *
     * @throws SAXException After an error is reported to {@link ErrorReceiver},
     * the same exception can be thrown to indicate a fatal irrecoverable error. {@link ErrorReceiver}
     * itself may throw it, if it chooses not to recover from the error.
     */
    public abstract boolean run(
            Model wsdlModel, WsimportOptions options, ErrorReceiver errorReceiver) throws SAXException;
}
