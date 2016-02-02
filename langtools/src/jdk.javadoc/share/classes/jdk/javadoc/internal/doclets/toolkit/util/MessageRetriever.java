/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import java.text.MessageFormat;
import java.util.*;

import javax.lang.model.element.Element;

import com.sun.source.util.DocTreePath;
import jdk.javadoc.internal.doclets.toolkit.Configuration;

import static javax.tools.Diagnostic.Kind.*;


/**
 * Retrieve and format messages stored in a resource.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Robert Field
 */
public class MessageRetriever {
    /**
     * The global configuration information for this run.
     */
    private final Configuration configuration;

    /**
     * The location from which to lazily fetch the resource..
     */
    private final String resourcelocation;

    /**
     * The lazily fetched resource..
     */
    private ResourceBundle messageRB;

    /**
     * Initialize the ResourceBundle with the given resource.
     *
     * @param rb the resource bundle to read.
     */
    public MessageRetriever(ResourceBundle rb) {
        this.configuration = null;
        this.messageRB = rb;
        this.resourcelocation = null;
    }

    /**
     * Initialize the ResourceBundle with the given resource.
     *
     * @param configuration the configuration
     * @param resourcelocation Resource.
     */
    public MessageRetriever(Configuration configuration,
                            String resourcelocation) {
        this.configuration = configuration;
        this.resourcelocation = resourcelocation;
    }

    private void initRB() {
        if (messageRB == null) {
            try {
                messageRB = ResourceBundle.getBundle(resourcelocation, configuration.getLocale());
            } catch (MissingResourceException e) {
                throw new Error("Fatal: Resource (" + resourcelocation
                        + ") for javadoc doclets is missing.");
            }
        }
    }

    /**
     * Get and format message string from resource
     *
     * @param key selects message from resource
     * @param args arguments to be replaced in the message.
     * @return the composed text
     * @throws MissingResourceException when the key does not
     * exist in the properties file.
     */
    public String getText(String key, Object... args) throws MissingResourceException {
        initRB();
        String message = messageRB.getString(key);
        return MessageFormat.format(message, args);
    }

    /**
     * Print error message, increment error count.
     *
     * @param pos the position of the source
     * @param msg message to print
     */
    private void printError(DocTreePath path, String msg) {
        configuration.reporter.print(ERROR, path, msg);
    }

    /**
     * Print error message, increment error count.
     *
     * @param msg message to print
     */
    private void printError(String msg) {
        configuration.reporter.print(ERROR, msg);
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param pos the position of the source
     * @param msg message to print
     */
    private void printWarning(DocTreePath path, String msg) {
        configuration.reporter.print(WARNING, path, msg);
    }

    private void printWarning(Element e, String msg) {
        configuration.reporter.print(WARNING, e, msg);
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param msg message to print
     */
    private void printWarning(String msg) {
        configuration.reporter.print(WARNING, msg);
    }

//    Note: the following do not appear to be needed any more, delete me.
//    /**
//     * Print a message.
//     *
//     * @param pos the position of the source
//     * @param msg message to print
//     */
//    private void printNotice(DocTreePath path, String msg) {
//        DocEnv env = ((RootDocImpl)configuration.root).env;
//        if (env.isQuiet() || env.isSilent()) {
//            return;
//        }
//        configuration.reporter.print(NOTE, path, msg);
//    }

//    Note: does not appear to be needed any more.
//    /**
//     * Print a message.
//     *
//     * @param pos the position of the source
//     * @param key selects message from resource
//     * @param args arguments to be replaced in the message.
//     */
//    public void notice(DocTreePath path, String key, Object... args) {
//        printNotice(path, getText(key, args));
//    }

    // ERRORS
    /**
     * Print error message, increment error count.
     *
     * @param path the path to the source
     * @param key selects message from resource
     * @param args arguments to be replaced in the message.
     */
    public void error(DocTreePath path, String key, Object... args) {
        printError(path, getText(key, args));
    }

    /**
     * Print error message, increment error count.
     *
     * @param key selects message from resource
     * @param args arguments to be replaced in the message.
     */
    public void error(String key, Object... args) {
        printError(getText(key, args));
    }

    // WARNINGS
    /**
     * Print warning message, increment warning count.

     * @param path the path to the source
     * @param key selects message from resource
     * @param args arguments to be replaced in the message.
     */
    public void warning(DocTreePath path, String key, Object... args) {
        if (configuration.showMessage(path, key))
            printWarning(path, getText(key, args));
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param e element target of the message
     * @param key selects message from resource
     * @param args arguments to be replaced in the message.
     */
    public void warning(Element e, String key, Object... args) {
        if (configuration.showMessage(e, key))
            printWarning(e, getText(key, args));
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param key selects message from resource
     * @param args arguments to be replaced in the message.
     */
    public void warning(String key, Object... args) {
        printWarning(getText(key, args));
    }

    // NOTICES
    /**
     * Print a message.
     *
     * @param msg message to print
     */
    private void printNotice(String msg) {
        if (configuration.quiet) {
            return;
        }
        configuration.reporter.print(NOTE, msg);
    }

    /**
     * Print a message.
     *
     * @param key selects message from resource
     * @param args arguments to be replaced in the message.
     */
    public void notice(String key, Object... args) {
        printNotice(getText(key, args));
    }
}
