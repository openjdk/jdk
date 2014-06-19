/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind.util;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.UnmarshallerHandler;
import javax.xml.transform.sax.SAXResult;

/**
 * JAXP {@link javax.xml.transform.Result} implementation
 * that unmarshals a JAXB object.
 *
 * <p>
 * This utility class is useful to combine JAXB with
 * other Java/XML technologies.
 *
 * <p>
 * The following example shows how to use JAXB to unmarshal a document
 * resulting from an XSLT transformation.
 *
 * <blockquote>
 *    <pre>
 *       JAXBResult result = new JAXBResult(
 *         JAXBContext.newInstance("org.acme.foo") );
 *
 *       // set up XSLT transformation
 *       TransformerFactory tf = TransformerFactory.newInstance();
 *       Transformer t = tf.newTransformer(new StreamSource("test.xsl"));
 *
 *       // run transformation
 *       t.transform(new StreamSource("document.xml"),result);
 *
 *       // obtain the unmarshalled content tree
 *       Object o = result.getResult();
 *    </pre>
 * </blockquote>
 *
 * <p>
 * The fact that JAXBResult derives from SAXResult is an implementation
 * detail. Thus in general applications are strongly discouraged from
 * accessing methods defined on SAXResult.
 *
 * <p>
 * In particular it shall never attempt to call the setHandler,
 * setLexicalHandler, and setSystemId methods.
 *
 * @author
 *      Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 * @since 1.6
 */
public class JAXBResult extends SAXResult {

    /**
     * Creates a new instance that uses the specified
     * JAXBContext to unmarshal.
     *
     * @param context The JAXBContext that will be used to create the
     * necessary Unmarshaller.  This parameter must not be null.
     * @exception JAXBException if an error is encountered while creating the
     * JAXBResult or if the context parameter is null.
     */
    public JAXBResult( JAXBContext context ) throws JAXBException {
        this( ( context == null ) ? assertionFailed() : context.createUnmarshaller() );
    }

    /**
     * Creates a new instance that uses the specified
     * Unmarshaller to unmarshal an object.
     *
     * <p>
     * This JAXBResult object will use the specified Unmarshaller
     * instance. It is the caller's responsibility not to use the
     * same Unmarshaller for other purposes while it is being
     * used by this object.
     *
     * <p>
     * The primary purpose of this method is to allow the client
     * to configure Unmarshaller. Unless you know what you are doing,
     * it's easier and safer to pass a JAXBContext.
     *
     * @param _unmarshaller the unmarshaller.  This parameter must not be null.
     * @throws JAXBException if an error is encountered while creating the
     * JAXBResult or the Unmarshaller parameter is null.
     */
    public JAXBResult( Unmarshaller _unmarshaller ) throws JAXBException {
        if( _unmarshaller == null )
            throw new JAXBException(
                Messages.format( Messages.RESULT_NULL_UNMARSHALLER ) );

        this.unmarshallerHandler = _unmarshaller.getUnmarshallerHandler();

        super.setHandler(unmarshallerHandler);
    }

    /**
     * Unmarshaller that will be used to unmarshal
     * the input documents.
     */
    private final UnmarshallerHandler unmarshallerHandler;

    /**
     * Gets the unmarshalled object created by the transformation.
     *
     * @return
     *      Always return a non-null object.
     *
     * @exception IllegalStateException
     *  if this method is called before an object is unmarshalled.
     *
     * @exception JAXBException
     *      if there is any unmarshalling error.
     *      Note that the implementation is allowed to throw SAXException
     *      during the parsing when it finds an error.
     */
    public Object getResult() throws JAXBException {
        return unmarshallerHandler.getResult();
    }

    /**
     * Hook to throw exception from the middle of a contructor chained call
     * to this
     */
    private static Unmarshaller assertionFailed() throws JAXBException {
        throw new JAXBException( Messages.format( Messages.RESULT_NULL_CONTEXT ) );
    }
}
