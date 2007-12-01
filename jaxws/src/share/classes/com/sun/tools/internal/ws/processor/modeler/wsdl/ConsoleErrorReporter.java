/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.ws.processor.modeler.wsdl;

import com.sun.tools.internal.xjc.api.ErrorListener;
import com.sun.tools.internal.ws.processor.util.ProcessorEnvironment;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import org.xml.sax.SAXParseException;

import java.util.ResourceBundle;
import java.text.MessageFormat;

public class ConsoleErrorReporter implements ErrorListener{

    private LocalizableMessageFactory messageFactory;
    private ProcessorEnvironment env;
    private boolean printStackTrace;
    private boolean hasError;

    public ConsoleErrorReporter(ProcessorEnvironment env, boolean printStackTrace) {
        this.env = env;
        this.printStackTrace = printStackTrace;
        messageFactory =
            new LocalizableMessageFactory("com.sun.tools.internal.ws.resources.model");
    }

    public boolean hasError() {
        return hasError;
    }

    // will be null unless set in #error or #fatalError
    //TODO: remove it after error handling is straightened
    private Exception e;
    Exception getException(){
        return e;
    }

    public void error(SAXParseException e) {
        hasError = true;
        this.e = e;
        if(printStackTrace)
            e.printStackTrace();
        env.error(messageFactory.getMessage("model.saxparser.exception",
                new Object[]{e.getMessage(), getLocationString(e)}));
    }

    public void fatalError(SAXParseException e) {
        hasError = true;
        this.e = e;
        if(printStackTrace)
            e.printStackTrace();

        env.error(messageFactory.getMessage("model.saxparser.exception",
                new Object[]{e.getMessage(), getLocationString(e)}));
    }

    public void warning(SAXParseException e) {
        env.warn(messageFactory.getMessage("model.saxparser.exception",
                new Object[]{e.getMessage(), getLocationString(e)}));
    }

    /**
     * Used to report possibly verbose information that
     * can be safely ignored.
     */
    public void info(SAXParseException e) {
        env.info(messageFactory.getMessage("model.saxparser.exception",
                new Object[]{e.getMessage(), getLocationString(e)}));
    }

     /**
    * Returns the human readable string representation of the
    * {@link org.xml.sax.Locator} part of the specified
    * {@link SAXParseException}.
    *
    * @return  non-null valid object.
    */
    protected final String getLocationString( SAXParseException e ) {
      if(e.getLineNumber()!=-1 || e.getSystemId()!=null) {
          int line = e.getLineNumber();
          return format("ConsoleErrorReporter.LineXOfY", line==-1?"?":Integer.toString( line ),
              getShortName( e.getSystemId() ) );
      } else {
          return format("ConsoleErrorReporter.UnknownLocation");
      }
    }

    /** Computes a short name of a given URL for display. */
    private String getShortName( String url ) {
        if(url==null)
            return format("ConsoleErrorReporter.UnknownLocation");
        return url;
    }

    private String format( String property, Object... args ) {
        String text = ResourceBundle.getBundle("com.sun.tools.internal.ws.resources.model").getString(property);
        return MessageFormat.format(text,args);
    }

}
