/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: TemplatesImpl.java,v 1.8 2007/03/26 20:12:27 spericas Exp $
 */

package com.sun.org.apache.xalan.internal.xsltc.trax;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Properties;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.xml.XMLConstants;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.URIResolver;

import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xalan.internal.xsltc.Translet;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.ErrorMsg;
import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xalan.internal.xsltc.runtime.Hashtable;

/**
 * @author Morten Jorgensen
 * @author G. Todd Millerj
 * @author Jochen Cordes <Jochen.Cordes@t-online.de>
 * @author Santiago Pericas-Geertsen
 */
public final class TemplatesImpl implements Templates, Serializable {
    static final long serialVersionUID = 673094361519270707L;
    /**
     * Name of the superclass of all translets. This is needed to
     * determine which, among all classes comprising a translet,
     * is the main one.
     */
    private static String ABSTRACT_TRANSLET
        = "com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet";

    /**
     * Name of the main class or default name if unknown.
     */
    private String _name = null;

    /**
     * Contains the actual class definition for the translet class and
     * any auxiliary classes.
     */
    private byte[][] _bytecodes = null;

    /**
     * Contains the translet class definition(s). These are created when
     * this Templates is created or when it is read back from disk.
     */
    private Class[] _class = null;

    /**
     * The index of the main translet class in the arrays _class[] and
     * _bytecodes.
     */
    private int _transletIndex = -1;

    /**
     * Contains the list of auxiliary class definitions.
     */
    private Hashtable _auxClasses = null;

    /**
     * Output properties of this translet.
     */
    private Properties _outputProperties;

    /**
     * Number of spaces to add for output indentation.
     */
    private int _indentNumber;

    /**
     * This URIResolver is passed to all Transformers.
     * Declaring it transient to fix bug 22438
     */
    private transient URIResolver _uriResolver = null;

    /**
     * Cache the DTM for the stylesheet in a thread local variable,
     * which is used by the document('') function.
     * Use ThreadLocal because a DTM cannot be shared between
     * multiple threads.
     * Declaring it transient to fix bug 22438
     */
    private transient ThreadLocal _sdom = new ThreadLocal();

    /**
     * A reference to the transformer factory that this templates
     * object belongs to.
     */
    private transient TransformerFactoryImpl _tfactory = null;

    static final class TransletClassLoader extends ClassLoader {
        TransletClassLoader(ClassLoader parent) {
            super(parent);
        }

        /**
         * Access to final protected superclass member from outer class.
         */
        Class defineClass(final byte[] b) {
            return defineClass(null, b, 0, b.length);
        }
    }


    /**
     * Create an XSLTC template object from the bytecodes.
     * The bytecodes for the translet and auxiliary classes, plus the name of
     * the main translet class, must be supplied.
     */
    protected TemplatesImpl(byte[][] bytecodes, String transletName,
        Properties outputProperties, int indentNumber,
        TransformerFactoryImpl tfactory)
    {
        _bytecodes = bytecodes;
        _name      = transletName;
        _outputProperties = outputProperties;
        _indentNumber = indentNumber;
        _tfactory = tfactory;
    }

    /**
     * Create an XSLTC template object from the translet class definition(s).
     */
    protected TemplatesImpl(Class[] transletClasses, String transletName,
        Properties outputProperties, int indentNumber,
        TransformerFactoryImpl tfactory)
    {
        _class     = transletClasses;
        _name      = transletName;
        _transletIndex = 0;
        _outputProperties = outputProperties;
        _indentNumber = indentNumber;
        _tfactory = tfactory;
    }


    /**
     * Need for de-serialization, see readObject().
     */
    public TemplatesImpl() { }

    /**
     *  Overrides the default readObject implementation since we decided
     *  it would be cleaner not to serialize the entire tranformer
     *  factory.  [ ref bugzilla 12317 ]
     *  We need to check if the user defined class for URIResolver also
     *  implemented Serializable
     *  if yes then we need to deserialize the URIResolver
     *  Fix for bugzilla bug 22438
     */
    private void  readObject(ObjectInputStream is)
      throws IOException, ClassNotFoundException
    {
        is.defaultReadObject();
        if (is.readBoolean()) {
            _uriResolver = (URIResolver) is.readObject();
        }

        _tfactory = new TransformerFactoryImpl();
    }


    /**
     *  This is to fix bugzilla bug 22438
     *  If the user defined class implements URIResolver and Serializable
     *  then we want it to get serialized
     */
    private void writeObject(ObjectOutputStream os)
        throws IOException, ClassNotFoundException {
        os.defaultWriteObject();
        if (_uriResolver instanceof Serializable) {
            os.writeBoolean(true);
            os.writeObject((Serializable) _uriResolver);
        }
        else {
            os.writeBoolean(false);
        }
    }


     /**
     * Store URIResolver needed for Transformers.
     */
    public synchronized void setURIResolver(URIResolver resolver) {
        _uriResolver = resolver;
    }

    /**
     * The TransformerFactory must pass us the translet bytecodes using this
     * method before we can create any translet instances
     *
     * Note: This method is private for security reasons. See
     * CR 6537898. When merging with Apache, we must ensure
     * that the privateness of this method is maintained (that
     * is why it wasn't removed).
     */
    private synchronized void setTransletBytecodes(byte[][] bytecodes) {
        _bytecodes = bytecodes;
    }

    /**
     * Returns the translet bytecodes stored in this template
     *
     * Note: This method is private for security reasons. See
     * CR 6537898. When merging with Apache, we must ensure
     * that the privateness of this method is maintained (that
     * is why it wasn't removed).
     */
    private synchronized byte[][] getTransletBytecodes() {
        return _bytecodes;
    }

    /**
     * Returns the translet bytecodes stored in this template
     *
     * Note: This method is private for security reasons. See
     * CR 6537898. When merging with Apache, we must ensure
     * that the privateness of this method is maintained (that
     * is why it wasn't removed).
     */
    private synchronized Class[] getTransletClasses() {
        try {
            if (_class == null) defineTransletClasses();
        }
        catch (TransformerConfigurationException e) {
            // Falls through
        }
        return _class;
    }

    /**
     * Returns the index of the main class in array of bytecodes
     */
    public synchronized int getTransletIndex() {
        try {
            if (_class == null) defineTransletClasses();
        }
        catch (TransformerConfigurationException e) {
            // Falls through
        }
        return _transletIndex;
    }

    /**
     * The TransformerFactory should call this method to set the translet name
     */
    protected synchronized void setTransletName(String name) {
        _name = name;
    }

    /**
     * Returns the name of the main translet class stored in this template
     */
    protected synchronized String getTransletName() {
        return _name;
    }

    /**
     * Defines the translet class and auxiliary classes.
     * Returns a reference to the Class object that defines the main class
     */
    private void defineTransletClasses()
        throws TransformerConfigurationException {

        if (_bytecodes == null) {
            ErrorMsg err = new ErrorMsg(ErrorMsg.NO_TRANSLET_CLASS_ERR);
            throw new TransformerConfigurationException(err.toString());
        }

        TransletClassLoader loader = (TransletClassLoader)
            AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                    return new TransletClassLoader(ObjectFactory.findClassLoader());
                }
            });

        try {
            final int classCount = _bytecodes.length;
            _class = new Class[classCount];

            if (classCount > 1) {
                _auxClasses = new Hashtable();
            }

            for (int i = 0; i < classCount; i++) {
                _class[i] = loader.defineClass(_bytecodes[i]);
                final Class superClass = _class[i].getSuperclass();

                // Check if this is the main class
                if (superClass.getName().equals(ABSTRACT_TRANSLET)) {
                    _transletIndex = i;
                }
                else {
                    _auxClasses.put(_class[i].getName(), _class[i]);
                }
            }

            if (_transletIndex < 0) {
                ErrorMsg err= new ErrorMsg(ErrorMsg.NO_MAIN_TRANSLET_ERR, _name);
                throw new TransformerConfigurationException(err.toString());
            }
        }
        catch (ClassFormatError e) {
            ErrorMsg err = new ErrorMsg(ErrorMsg.TRANSLET_CLASS_ERR, _name);
            throw new TransformerConfigurationException(err.toString());
        }
        catch (LinkageError e) {
            ErrorMsg err = new ErrorMsg(ErrorMsg.TRANSLET_OBJECT_ERR, _name);
            throw new TransformerConfigurationException(err.toString());
        }
    }

    /**
     * This method generates an instance of the translet class that is
     * wrapped inside this Template. The translet instance will later
     * be wrapped inside a Transformer object.
     */
    private Translet getTransletInstance()
        throws TransformerConfigurationException {
        try {
            if (_name == null) return null;

            if (_class == null) defineTransletClasses();

            // The translet needs to keep a reference to all its auxiliary
            // class to prevent the GC from collecting them
            AbstractTranslet translet = (AbstractTranslet) _class[_transletIndex].newInstance();
            translet.postInitialization();
            translet.setTemplates(this);
            if (_auxClasses != null) {
                translet.setAuxiliaryClasses(_auxClasses);
            }

            return translet;
        }
        catch (InstantiationException e) {
            ErrorMsg err = new ErrorMsg(ErrorMsg.TRANSLET_OBJECT_ERR, _name);
            throw new TransformerConfigurationException(err.toString());
        }
        catch (IllegalAccessException e) {
            ErrorMsg err = new ErrorMsg(ErrorMsg.TRANSLET_OBJECT_ERR, _name);
            throw new TransformerConfigurationException(err.toString());
        }
    }

    /**
     * Implements JAXP's Templates.newTransformer()
     *
     * @throws TransformerConfigurationException
     */
    public synchronized Transformer newTransformer()
        throws TransformerConfigurationException
    {
        TransformerImpl transformer;

        transformer = new TransformerImpl(getTransletInstance(), _outputProperties,
            _indentNumber, _tfactory);

        if (_uriResolver != null) {
            transformer.setURIResolver(_uriResolver);
        }

        if (_tfactory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING)) {
            transformer.setSecureProcessing(true);
        }
        return transformer;
    }

    /**
     * Implements JAXP's Templates.getOutputProperties(). We need to
     * instanciate a translet to get the output settings, so
     * we might as well just instanciate a Transformer and use its
     * implementation of this method.
     */
    public synchronized Properties getOutputProperties() {
        try {
            return newTransformer().getOutputProperties();
        }
        catch (TransformerConfigurationException e) {
            return null;
        }
    }

    /**
     * Return the thread local copy of the stylesheet DOM.
     */
    public DOM getStylesheetDOM() {
        return (DOM)_sdom.get();
    }

    /**
     * Set the thread local copy of the stylesheet DOM.
     */
    public void setStylesheetDOM(DOM sdom) {
        _sdom.set(sdom);
    }
}
