/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.Configuration;
import java.util.*;
import java.text.MessageFormat;


/**
 * Retrieve and format messages stored in a resource.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @since 1.2
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
     * Initilize the ResourceBundle with the given resource.
     *
     * @param rb the esource bundle to read.
     */
    public MessageRetriever(ResourceBundle rb) {
        this.configuration = null;
        this.messageRB = rb;
        this.resourcelocation = null;
    }

    /**
     * Initilize the ResourceBundle with the given resource.
     *
     * @param configuration the configuration
     * @param resourcelocation Resource.
     */
    public MessageRetriever(Configuration configuration,
                            String resourcelocation) {
        this.configuration = configuration;
        this.resourcelocation = resourcelocation;
    }

    /**
     * Get and format message string from resource
     *
     * @param key selects message from resource
     * @param args arguments to be replaced in the message.
     * @throws MissingResourceException when the key does not
     * exist in the properties file.
     */
    public String getText(String key, Object... args) throws MissingResourceException {
        if (messageRB == null) {
            try {
                messageRB = ResourceBundle.getBundle(resourcelocation);
            } catch (MissingResourceException e) {
                throw new Error("Fatal: Resource (" + resourcelocation +
                                    ") for javadoc doclets is missing.");
            }
        }
        String message = messageRB.getString(key);
        return MessageFormat.format(message, args);
    }

    /**
     * Print error message, increment error count.
     *
     * @param pos the position of the source
     * @param msg message to print
     */
    private void printError(SourcePosition pos, String msg) {
        configuration.root.printError(pos, msg);
    }

    /**
     * Print error message, increment error count.
     *
     * @param msg message to print
     */
    private void printError(String msg) {
        configuration.root.printError(msg);
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param pos the position of the source
     * @param msg message to print
     */
    private void printWarning(SourcePosition pos, String msg) {
        configuration.root.printWarning(pos, msg);
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param msg message to print
     */
    private void printWarning(String msg) {
        configuration.root.printWarning(msg);
    }

    /**
     * Print a message.
     *
     * @param pos the position of the source
     * @param msg message to print
     */
    private void printNotice(SourcePosition pos, String msg) {
        configuration.root.printNotice(pos, msg);
    }

    /**
     * Print a message.
     *
     * @param msg message to print
     */
    private void printNotice(String msg) {
        configuration.root.printNotice(msg);
    }

    /**
     * Print error message, increment error count.
     *
     * @param pos the position of the source
     * @param key selects message from resource
     * @param args arguments to be replaced in the message.
     */
    public void error(SourcePosition pos, String key, Object... args) {
        printError(pos, getText(key, args));
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

    /**
     * Print warning message, increment warning count.
     *
     * @param pos the position of the source
     * @param key selects message from resource
     * @param args arguments to be replaced in the message.
     */
    public void warning(SourcePosition pos, String key, Object... args) {
        printWarning(pos, getText(key, args));
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

    /**
     * Print a message.
     *
     * @param pos the position of the source
     * @param key selects message from resource
     * @param args arguments to be replaced in the message.
     */
    public void notice(SourcePosition pos, String key, Object... args) {
        printNotice(pos, getText(key, args));
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
