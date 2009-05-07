/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.tools.internal.xjc;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.sun.tools.internal.xjc.generator.bean.field.FieldRendererFactory;
import com.sun.tools.internal.xjc.model.CPluginCustomization;
import com.sun.tools.internal.xjc.model.Model;
import com.sun.tools.internal.xjc.outline.Outline;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

/**
 * Add-on that works on the generated source code.
 *
 * <p>
 * This add-on will be called after the default bean generation
 * has finished.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 *
 * @since
 *      JAXB RI 2.0 EA
 */
public abstract class Plugin {

    /**
     * Gets the option name to turn on this add-on.
     *
     * <p>
     * For example, if "abc" is returned, "-abc" will
     * turn on this plugin. A plugin needs to be turned
     * on explicitly, or else no other methods of {@link Plugin}
     * will be invoked.
     *
     * <p>
     * Starting 2.1, when an option matches the name returned
     * from this method, XJC will then invoke {@link #parseArgument(Options, String[], int)},
     * allowing plugins to handle arguments to this option.
     */
    public abstract String getOptionName();

    /**
     * Gets the description of this add-on. Used to generate
     * a usage screen.
     *
     * @return
     *      localized description message. should be terminated by \n.
     */
    public abstract String getUsage();

    /**
     * Parses an option <code>args[i]</code> and augment
     * the <code>opt</code> object appropriately, then return
     * the number of tokens consumed.
     *
     * <p>
     * The callee doesn't need to recognize the option that the
     * getOptionName method returns.
     *
     * <p>
     * Once a plugin is activated, this method is called
     * for options that XJC didn't recognize. This allows
     * a plugin to define additional options to customize
     * its behavior.
     *
     * <p>
     * Since options can appear in no particular order,
     * XJC allows sub-options of a plugin to show up before
     * the option that activates a plugin (one that's returned
     * by {@link #getOptionName().)
     *
     * But nevertheless a {@link Plugin} needs to be activated
     * to participate in further processing.
     *
     * @return
     *      0 if the argument is not understood.
     *      Otherwise return the number of tokens that are
     *      consumed, including the option itself.
     *      (so if you have an option like "-foo 3", return 2.)
     * @exception BadCommandLineException
     *      If the option was recognized but there's an error.
     *      This halts the argument parsing process and causes
     *      XJC to abort, reporting an error.
     */
    public int parseArgument( Options opt, String[] args, int i ) throws BadCommandLineException, IOException {
        return 0;
    }

    /**
     * Returns the list of namespace URIs that are supported by this plug-in
     * as schema annotations.
     *
     * <p>
     * If a plug-in returns a non-empty list, the JAXB RI will recognize
     * these namespace URIs as vendor extensions
     * (much like "http://java.sun.com/xml/ns/jaxb/xjc"). This allows users
     * to write those annotations inside a schema, or in external binding files,
     * and later plug-ins can access those annotations as DOM nodes.
     *
     * <p>
     * See <a href="http://java.sun.com/webservices/docs/1.5/jaxb/vendorCustomizations.html">
     * http://java.sun.com/webservices/docs/1.5/jaxb/vendorCustomizations.html</a>
     * for the syntax that users need to use to enable extension URIs.
     *
     * @return
     *      can be empty, be never be null.
     */
    public List<String> getCustomizationURIs() {
        return Collections.emptyList();
    }

    /**
     * Checks if the given tag name is a valid tag name for the customization element in this plug-in.
     *
     * <p>
     * This method is invoked by XJC to determine if the user-specified customization element
     * is really a customization or not. This information is used to pick the proper error message.
     *
     * <p>
     * A plug-in is still encouraged to do the validation of the customization element in the
     * {@link #run} method before using any {@link CPluginCustomization}, to make sure that it
     * has proper child elements and attributes.
     *
     * @param nsUri
     *      the namespace URI of the element. Never null.
     * @param localName
     *      the local name of the element. Never null.
     */
    public boolean isCustomizationTagName(String nsUri,String localName) {
        return false;
    }

    /**
     * Notifies a plugin that it's activated.
     *
     * <p>
     * This method is called when a plugin is activated
     * through the command line option (as specified by {@link #getOptionName()}.
     *
     * <p>
     * This is a good opportunity to use
     * {@link Options#setFieldRendererFactory(FieldRendererFactory, Plugin)}
     * if a plugin so desires.
     *
     * <p>
     * Noop by default.
     *
     * @since JAXB 2.0 EA4
     */
    public void onActivated(Options opts) throws BadCommandLineException {
        // noop
    }

    /**
     * Performs the post-processing of the {@link Model}.
     *
     * <p>
     * This method is invoked after XJC has internally finished
     * the model construction. This is a chance for a plugin to
     * affect the way code generation is performed.
     *
     * <p>
     * Compared to the {@link #run(Outline, Options, ErrorHandler)}
     * method, this method allows a plugin to work at the higher level
     * conceptually closer to the abstract JAXB model, as opposed to
     * Java syntax level.
     *
     * <p>
     * Note that this method is invoked only when a {@link Plugin}
     * is activated.
     *
     * @param model
     *      The object that represents the classes/properties to
     *      be generated.
     *
     * @param errorHandler
     *      Errors should be reported to this handler.
     *
     * @since JAXB 2.0.2
     */
    public void postProcessModel(Model model, ErrorHandler errorHandler) {
        // noop
    }

    /**
     * Run the add-on.
     *
     * <p>
     * This method is invoked after XJC has internally finished
     * the code generation. Plugins can tweak some of the generated
     * code (or add more code) by using {@link Outline} and {@link Options}.
     *
     * <p>
     * Note that this method is invoked only when a {@link Plugin}
     * is activated.
     *
     * @param outline
     *      This object allows access to various generated code.
     *
     * @param errorHandler
     *      Errors should be reported to this handler.
     *
     * @return
     *      If the add-on executes successfully, return true.
     *      If it detects some errors but those are reported and
     *      recovered gracefully, return false.
     *
     * @throws SAXException
     *      After an error is reported to {@link ErrorHandler}, the
     *      same exception can be thrown to indicate a fatal irrecoverable
     *      error. {@link ErrorHandler} itself may throw it, if it chooses
     *      not to recover from the error.
     */
    public abstract boolean run(
        Outline outline, Options opt, ErrorHandler errorHandler ) throws SAXException ;
}
