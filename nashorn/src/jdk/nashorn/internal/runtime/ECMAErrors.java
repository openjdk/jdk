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

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import jdk.nashorn.api.scripting.NashornException;
import jdk.nashorn.internal.scripts.JS;

/**
 * Helper class to throw various standard "ECMA error" exceptions such as Error, ReferenceError, TypeError etc.
 */
public final class ECMAErrors {
    private static final String MESSAGES_RESOURCE = "jdk.nashorn.internal.runtime.resources.Messages";

    private static final ResourceBundle MESSAGES_BUNDLE;
    static {
        MESSAGES_BUNDLE = ResourceBundle.getBundle(MESSAGES_RESOURCE, Locale.getDefault());
    }

    /** We assume that compiler generates script classes into the known package. */
    private static final String scriptPackage;
    static {
        String name = JS.class.getName();
        scriptPackage = name.substring(0, name.lastIndexOf('.'));
    }

    private ECMAErrors() {
    }

    private static ECMAException error(final Object thrown, final Throwable cause) {
        return new ECMAException(thrown, cause);
    }

     /**
     * Error dispatch mechanism.
     * Create a {@link ParserException} as the correct JavaScript error
     *
     * @param e {@code ParserException} for error dispatcher
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException asEcmaException(final ParserException e) {
        return asEcmaException(Context.getGlobalTrusted(), e);
    }

    /**
     * Error dispatch mechanism.
     * Create a {@link ParserException} as the correct JavaScript error
     *
     * @param global global scope object
     * @param e {@code ParserException} for error dispatcher
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException asEcmaException(final ScriptObject global, final ParserException e) {
        final JSErrorType errorType = e.getErrorType();
        assert errorType != null : "error type for " + e + " was null";

        final GlobalObject globalObj = (GlobalObject)global;
        final String       msg    = e.getMessage();

        // translate to ECMAScript Error object using error type
        switch (errorType) {
        case ERROR:
            return error(globalObj.newError(msg), e);
        case EVAL_ERROR:
            return error(globalObj.newEvalError(msg), e);
        case RANGE_ERROR:
            return error(globalObj.newRangeError(msg), e);
        case REFERENCE_ERROR:
            return error(globalObj.newReferenceError(msg), e);
        case SYNTAX_ERROR:
            return error(globalObj.newSyntaxError(msg), e);
        case TYPE_ERROR:
            return error(globalObj.newTypeError(msg), e);
        case URI_ERROR:
            return error(globalObj.newURIError(msg), e);
        default:
            // should not happen - perhaps unknown error type?
            throw new AssertionError(e.getMessage());
        }
    }

    /**
     * Create a syntax error (ECMA 15.11.6.4)
     *
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException syntaxError(final String msgId, final String... args) {
        return syntaxError(Context.getGlobalTrusted(), msgId, args);
    }

    /**
     * Create a syntax error (ECMA 15.11.6.4)
     *
     * @param global  global scope object
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException syntaxError(final ScriptObject global, final String msgId, final String... args) {
        return syntaxError(global, null, msgId, args);
    }

    /**
     * Create a syntax error (ECMA 15.11.6.4)
     *
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException syntaxError(final Throwable cause, final String msgId, final String... args) {
        return syntaxError(Context.getGlobalTrusted(), cause, msgId, args);
    }

    /**
     * Create a syntax error (ECMA 15.11.6.4)
     *
     * @param global  global scope object
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException syntaxError(final ScriptObject global, final Throwable cause, final String msgId, final String... args) {
        final String msg = getMessage("syntax.error." + msgId, args);
        return error(((GlobalObject)global).newSyntaxError(msg), cause);
    }

    /**
     * Create a type error (ECMA 15.11.6.5)
     *
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException typeError(final String msgId, final String... args) {
        return typeError(Context.getGlobalTrusted(), msgId, args);
    }

    /**
     * Create a type error (ECMA 15.11.6.5)
     *
     * @param global  global scope object
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException typeError(final ScriptObject global, final String msgId, final String... args) {
        return typeError(global, null, msgId, args);
    }

    /**
     * Create a type error (ECMA 15.11.6.5)
     *
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException typeError(final Throwable cause, final String msgId, final String... args) {
        return typeError(Context.getGlobalTrusted(), cause, msgId, args);
    }

    /**
     * Create a type error (ECMA 15.11.6.5)
     *
     * @param global  global scope object
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException typeError(final ScriptObject global, final Throwable cause, final String msgId, final String... args) {
        final String msg = getMessage("type.error." + msgId, args);
        return error(((GlobalObject)global).newTypeError(msg), cause);
    }

    /**
     * Create a range error (ECMA 15.11.6.2)
     *
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException rangeError(final String msgId, final String... args) {
        return rangeError(Context.getGlobalTrusted(), msgId, args);
    }

    /**
     * Create a range error (ECMA 15.11.6.2)
     *
     * @param global  global scope object
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException rangeError(final ScriptObject global, final String msgId, final String... args) {
        return rangeError(global, null, msgId, args);
    }

    /**
     * Create a range error (ECMA 15.11.6.2)
     *
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException rangeError(final Throwable cause, final String msgId, final String... args) {
        return rangeError(Context.getGlobalTrusted(), cause, msgId, args);
    }

    /**
     * Create a range error (ECMA 15.11.6.2)
     *
     * @param global  global scope object
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException rangeError(final ScriptObject global, final Throwable cause, final String msgId, final String... args) {
        final String msg = getMessage("range.error." + msgId, args);
        return error(((GlobalObject)global).newRangeError(msg), cause);
    }

    /**
     * Create a reference error (ECMA 15.11.6.3)
     *
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException referenceError(final String msgId, final String... args) {
        return referenceError(Context.getGlobalTrusted(), msgId, args);
    }

    /**
     * Create a reference error (ECMA 15.11.6.3)
     *
     * @param global  global scope object
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException referenceError(final ScriptObject global, final String msgId, final String... args) {
        return referenceError(global, null, msgId, args);
    }

    /**
     * Create a reference error (ECMA 15.11.6.3)
     *
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException referenceError(final Throwable cause, final String msgId, final String... args) {
        return referenceError(Context.getGlobalTrusted(), cause, msgId, args);
    }

    /**
     * Create a reference error (ECMA 15.11.6.3)
     *
     * @param global  global scope object
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException referenceError(final ScriptObject global, final Throwable cause, final String msgId, final String... args) {
        final String msg = getMessage("reference.error." + msgId, args);
        return error(((GlobalObject)global).newReferenceError(msg), cause);
    }

    /**
     * Create a URI error (ECMA 15.11.6.6)
     *
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException uriError(final String msgId, final String... args) {
        return uriError(Context.getGlobalTrusted(), msgId, args);
    }

    /**
     * Create a URI error (ECMA 15.11.6.6)
     *
     * @param global  global scope object
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException uriError(final ScriptObject global, final String msgId, final String... args) {
        return uriError(global, null, msgId, args);
    }

    /**
     * Create a URI error (ECMA 15.11.6.6)
     *
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException uriError(final Throwable cause, final String msgId, final String... args) {
        return uriError(Context.getGlobalTrusted(), cause, msgId, args);
    }

    /**
     * Create a URI error (ECMA 15.11.6.6)
     *
     * @param global  global scope object
     * @param cause   native Java {@code Throwable} that is the cause of error
     * @param msgId   resource tag for error message
     * @param args    arguments to resource
     *
     * @return the resulting {@link ECMAException}
     */
    public static ECMAException uriError(final ScriptObject global, final Throwable cause, final String msgId, final String... args) {
        final String msg = getMessage("uri.error." + msgId, args);
        return error(((GlobalObject)global).newURIError(msg), cause);
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


    /**
     * Check if a stack trace element is in JavaScript
     *
     * @param frame frame
     *
     * @return true if frame is in the script
     */
    public static boolean isScriptFrame(final StackTraceElement frame) {
        final String className = frame.getClassName();

        // Look for script package in class name (into which compiler puts generated code)
        if (className.startsWith(scriptPackage)) {
            final String source = frame.getFileName();
            /*
             * Make sure that it is not some Java code that Nashorn has in that package!
             * also, we don't want to report JavaScript code that lives in script engine implementation
             * We want to report only user's own scripts and not any of our own scripts like "engine.js"
             */
            return source != null && !source.endsWith(".java") && !source.contains(NashornException.ENGINE_SCRIPT_SOURCE_NAME);
        }
        return false;
    }
}
