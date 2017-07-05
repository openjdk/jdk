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
package com.sun.tools.internal.xjc.reader.xmlschema;

import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.reader.Ring;

import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;

/**
 * Provides error report capability to other owner components
 * by encapsulating user-specified {@link ErrorHandler}
 * and exposing utlity methods.
 *
 * <p>
 * This class also wraps SAXException to a RuntimeException
 * so that the exception thrown inside the error handler
 * can abort the process.
 *
 * <p>
 * At the end of the day, we need to know if there was any error.
 * So it is important that all the error messages go through this
 * object. This is done by hiding the errorHandler from the rest
 * of the components.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class ErrorReporter extends BindingComponent {

    /**
     * Error handler to report any binding error to.
     * To report errors, use the error method.
     */
    private final ErrorReceiver errorReceiver = Ring.get(ErrorReceiver.class);


    //
    // helper methods for classes in this package.
    //    properties are localized through the Messages.properties file
    //    in this package
    //
    void error( Locator loc, String prop, Object... args ) {
        errorReceiver.error( loc, Messages.format(prop,args) );
    }

    void warning( Locator loc, String prop, Object... args ) {
        errorReceiver.warning( new SAXParseException(
            Messages.format(prop,args), loc ));
    }



    /*
    private String format( String prop, Object[] args ) {
        // use a bit verbose code to make it portable.
        String className = this.getClass().getName();
        int idx = className.lastIndexOf('.');
        String packageName = className.substring(0,idx);

        String fmt = ResourceBundle.getBundle(packageName+".Messages").getString(prop);

        return MessageFormat.format(fmt,args);
    }
    */

////
////
//// ErrorHandler implementation
////
////
//    public void error(SAXParseException exception) {
//        errorReceiver.error(exception);
//    }
//
//    public void fatalError(SAXParseException exception) {
//        errorReceiver.fatalError(exception);
//    }
//
//    public void warning(SAXParseException exception) {
//        errorReceiver.warning(exception);
//    }

}
