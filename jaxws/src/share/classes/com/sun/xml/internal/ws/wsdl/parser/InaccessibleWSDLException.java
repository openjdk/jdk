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
package com.sun.xml.internal.ws.wsdl.parser;



import javax.xml.ws.WebServiceException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A list of {@link InaccessibleWSDLException} wrapped in one exception.
 *
 * <p>
 * This exception is used to report all the errors during WSDL parsing from {@link RuntimeWSDLParser#parse(java.net.URL, org.xml.sax.EntityResolver, boolean, com.sun.xml.internal.ws.api.wsdl.parser.WSDLParserExtension[])}
 *
 * @author Vivek Pandey
 */
public class InaccessibleWSDLException extends WebServiceException {

    private final List<Throwable> errors;

    private static final long serialVersionUID = 1L;

    public InaccessibleWSDLException(List<Throwable> errors) {
        super(errors.size()+" counts of InaccessibleWSDLException.\n");
        assert !errors.isEmpty() : "there must be at least one error";
        this.errors = Collections.unmodifiableList(new ArrayList<Throwable>(errors));
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        sb.append('\n');

        for( Throwable error : errors )
            sb.append(error.toString()).append('\n');

        return sb.toString();
    }

    /**
     * Returns a read-only list of {@link InaccessibleWSDLException}s
     * wrapped in this exception.
     *
     * @return
     *      a non-null list.
     */
    public List<Throwable> getErrors() {
        return errors;
    }

    public static class Builder implements ErrorHandler {
        private final List<Throwable> list = new ArrayList<Throwable>();
        public void error(Throwable e) {
            list.add(e);
        }
        /**
         * If an error was reported, throw the exception.
         * Otherwise exit normally.
         */
        public void check() throws InaccessibleWSDLException {
            if(list.isEmpty())
                return;
            throw new InaccessibleWSDLException(list);
        }
    }

}
