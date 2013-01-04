/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Helper class to throw various standard "ECMA error" exceptions such as Error, ReferenceError, TypeError etc.
 */
public final class ECMAErrors {
    private static final String MESSAGES_RESOURCE = "jdk.nashorn.internal.runtime.resources.Messages";

    // Without do privileged, under security manager messages can not be loaded.
    private static final ResourceBundle MESSAGES_BUNDLE = AccessController.doPrivileged(
        new PrivilegedAction<ResourceBundle>() {
            @Override
            public ResourceBundle run() {
                return ResourceBundle.getBundle(MESSAGES_RESOURCE, Locale.getDefault());
            }
        });

    private ECMAErrors() {
    }

    private static void throwError(final Object thrown, final Throwable cause) {
        throw new ECMAException(thrown, cause);
    }

    /**
     * Error dispatch mechanism.
     * Throw a {@link ParserException} as the correct JavaScript error
     *
     * @param global global scope object
     * @param e {@code ParserException} for error dispatcher
     */
    public static void throwAsEcmaException(final ScriptObject global, final ParserException e) {
        final JSErrorType errorType = e.getErrorType();
        if (errorType == null) {
            // no errorType set, throw ParserException object 'as is'
            throw e;
        }

        final GlobalObject globalObj = (GlobalObject)global;
        final String       msg    = e.getMessage();

        // translate to ECMAScript Error object using error type
        switch (errorType) {
        case ERROR:
            throwError(globalObj.newError(msg), e);
            break;
        case EVAL_ERROR:
            throwError(globalObj.newEvalError(msg), e);
            break;
        case RANGE_ERROR:
            throwError(globalObj.newRangeError(msg), e);
            break;
        case REFERENCE_ERROR:
            throwError(globalObj.newReferenceError(msg), e);
            break;
        case SYNTAX_ERROR:
            throwError(globalObj.newSyntaxError(msg), e);
            break;
        case TYPE_ERROR:
            throwError(globalObj.newTypeError(msg), e);
            break;
        case URI_ERROR:
            throwError(globalObj.newURIError(msg), e);
            break;
        default:
            break;
        }

        // should not happen - perhaps unknown error type?
        throw e;
    }

    /**
     * Throw a syntax error (ECMA 15.11.6.4)
     *
     * @param global  global scope object
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     */
    public static void syntaxError(final ScriptObject global, final String msgId, final String... args) {
        syntaxError(global, null, msgId, args);
    }

    /**
     * Throw a syntax error (ECMA 15.11.6.4)
     *
     * @param global  global scope object
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     */
    public static void syntaxError(final ScriptObject global, final Throwable cause, final String msgId, final String... args) {
        final String msg = getMessage("syntax.error." + msgId, args);
        throwError(((GlobalObject)global).newSyntaxError(msg), cause);
    }

    /**
     * Throw a type error (ECMA 15.11.6.5)
     *
     * @param global  global scope object
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     */
    public static void typeError(final ScriptObject global, final String msgId, final String... args) {
        typeError(global, null, msgId, args);
    }

    /**
     * Throw a type error (ECMA 15.11.6.5)
     *
     * @param global  global scope object
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     */
    public static void typeError(final ScriptObject global, final Throwable cause, final String msgId, final String... args) {
        final String msg = getMessage("type.error." + msgId, args);
        throwError(((GlobalObject)global).newTypeError(msg), cause);
    }

    /**
     * Throw a range error (ECMA 15.11.6.2)
     *
     * @param global  global scope object
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     */
    public static void rangeError(final ScriptObject global, final String msgId, final String... args) {
        rangeError(global, null, msgId, args);
    }

    /**
     * Throw a range error (ECMA 15.11.6.2)
     *
     * @param global  global scope object
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     */
    public static void rangeError(final ScriptObject global, final Throwable cause, final String msgId, final String... args) {
        final String msg = getMessage("range.error." + msgId, args);
        throwError(((GlobalObject)global).newRangeError(msg), cause);
    }

    /**
     * Throw a reference error (ECMA 15.11.6.3)
     *
     * @param global  global scope object
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     */
    public static void referenceError(final ScriptObject global, final String msgId, final String... args) {
        referenceError(global, null, msgId, args);
    }

    /**
     * Throw a reference error (ECMA 15.11.6.3)
     *
     * @param global  global scope object
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     */
    public static void referenceError(final ScriptObject global, final Throwable cause, final String msgId, final String... args) {
        final String msg = getMessage("reference.error." + msgId, args);
        throwError(((GlobalObject)global).newReferenceError(msg), cause);
    }

    /**
     * Throw a URI error (ECMA 15.11.6.6)
     *
     * @param global  global scope object
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     */
    public static void uriError(final ScriptObject global, final String msgId, final String... args) {
        uriError(global, null, msgId, args);
    }

    /**
     * Throw a URI error (ECMA 15.11.6.6)
     *
     * @param global  global scope object
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     */
    public static void uriError(final ScriptObject global, final Throwable cause, final String msgId, final String... args) {
        final String msg = getMessage("uri.error." + msgId, args);
        throwError(((GlobalObject)global).newURIError(msg), cause);
    }

    /**
     * Get the exception message by placing the args in the resource defined
     * by the resource tag. This is visible to, e.g. the {@link jdk.nashorn.internal.parser.Parser}
     * can use it to generate compile time messages with the correct locale
     *
     * @param msgId the resource tag (message id)
     * @param args  arguments to error string
     *
     * @return the filled out error string
     */
    public static String getMessage(final String msgId, final String... args) {
        try {
            return new MessageFormat(MESSAGES_BUNDLE.getString(msgId)).format(args);
        } catch (final java.util.MissingResourceException e) {
            throw new RuntimeException("no message resource found for message id: "+ msgId);
        }
    }

}
