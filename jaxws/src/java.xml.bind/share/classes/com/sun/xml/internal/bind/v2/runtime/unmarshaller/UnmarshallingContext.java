/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.ValidationEventLocator;
import javax.xml.bind.helpers.ValidationEventImpl;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.istack.internal.SAXParseException2;
import com.sun.xml.internal.bind.IDResolver;
import com.sun.xml.internal.bind.Util;
import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.api.ClassResolver;
import com.sun.xml.internal.bind.unmarshaller.InfosetScanner;
import com.sun.xml.internal.bind.v2.ClassFactory;
import com.sun.xml.internal.bind.v2.runtime.AssociationMap;
import com.sun.xml.internal.bind.v2.runtime.Coordinator;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.JaxBeanInfo;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.LocatorImpl;

/**
 * Center of the unmarshalling.
 *
 * <p>
 * This object is responsible for coordinating {@link Loader}s to
 * perform the whole unmarshalling.
 *
 * @author Kohsuke Kawaguchi
 */
public final class UnmarshallingContext extends Coordinator
    implements NamespaceContext, ValidationEventHandler, ErrorHandler, XmlVisitor, XmlVisitor.TextPredictor {

    private static final Logger logger = Logger.getLogger(UnmarshallingContext.class.getName());

    /**
     * Root state.
     */
    private final State root;

    /**
     * The currently active state.
     */
    private State current;

    private static final LocatorEx DUMMY_INSTANCE;

    static {
        LocatorImpl loc = new LocatorImpl();
        loc.setPublicId(null);
        loc.setSystemId(null);
        loc.setLineNumber(-1);
        loc.setColumnNumber(-1);
        DUMMY_INSTANCE = new LocatorExWrapper(loc);
    }

    private @NotNull LocatorEx locator = DUMMY_INSTANCE;

    /** Root object that is being unmarshalled. */
    private Object result;

    /**
     * If non-null, this unmarshaller will unmarshal {@code JAXBElement<EXPECTEDTYPE>}
     * regardless of the tag name, as opposed to deciding the root object by using
     * the tag name.
     *
     * The property has a package-level access, because we cannot copy this value
     * to {@link UnmarshallingContext} when it is created. The property
     * on {@link Unmarshaller} could be changed after the handler is created.
     */
    private JaxBeanInfo expectedType;

    /**
     * Handles ID/IDREF.
     */
    private IDResolver idResolver;

    /**
     * This flag is set to true at the startDocument event
     * and false at the endDocument event.
     *
     * Until the first document is unmarshalled, we don't
     * want to return an object. So this variable is initialized
     * to true.
     */
    private boolean isUnmarshalInProgress = true;
    private boolean aborted = false;

    public final UnmarshallerImpl parent;

    /**
     * If the unmarshaller is doing associative unmarshalling,
     * this field is initialized to non-null.
     */
    private final AssociationMap assoc;

    /**
     * Indicates whether we are doing in-place unmarshalling
     * or not.
     *
     * <p>
     * This flag is unused when {@link #assoc}==null.
     * If it's non-null, then {@code true} indicates
     * that we are doing in-place associative unmarshalling.
     * If {@code false}, then we are doing associative unmarshalling
     * without object reuse.
     */
    private boolean isInplaceMode;

    /**
     * This object is consulted to get the element object for
     * the current element event.
     *
     * This is used when we are building an association map.
     */
    private InfosetScanner scanner;

    private Object currentElement;

    /**
     * @see XmlVisitor#startDocument(LocatorEx, NamespaceContext)
     */
    private NamespaceContext environmentNamespaceContext;

    /**
     * Used to discover additional classes when we hit unknown elements/types.
     */
    public @Nullable ClassResolver classResolver;

    /**
     * User-supplied {@link ClassLoader} for converting name to {@link Class}.
     * For backward compatibility, when null, use thread context classloader.
     */
    public @Nullable ClassLoader classLoader;

    /**
     * The variable introduced to avoid reporting n^10 similar errors.
     * After error is reported counter is decremented. When it became 0 - errors should not be reported any more.
     *
     * volatile is required to ensure that concurrent threads will see changed value
     */
    private static volatile int errorsCounter = 10;

    /**
     * State information for each element.
     */
    public final class State {
        /**
         * Loader that owns this element.
         */
        private Loader loader;
        /**
         * Once {@link #loader} is completed, this receiver
         * receives the result.
         */
        private Receiver receiver;

        private Intercepter intercepter;

        /**
         * Object being unmarshalled by this {@link #loader}.
         */
        private Object target;

        /**
         * Hack for making JAXBElement unmarshalling work.
         *
         * <p>
         * While the unmarshalling is in progress, the {@link #target} field stores the object being unmarshalled.
         * This makes it convenient to keep track of the unmarshalling activity in context of XML infoset, but
         * since there's only one {@link State} per element, this mechanism only works when there's one object
         * per element, which breaks down when we have {@link JAXBElement}, since the presence of JAXBElement
         * requires that we have two objects unmarshalled (a JAXBElement X and a value object Y bound to an XML type.)
         *
         * <p>
         * So to make room for storing both, this {@link #backup} field is used. When we create X instance
         * in the above example, we set that to {@code state.prev.target} and displace its old value to
         * {@code state.prev.backup} (where Y goes to {@code state.target}.) Upon the completion of the unmarshalling
         * of Y, we revert this.
         *
         * <p>
         * While this attributes X incorrectly to its parent element, this preserves the parent/child
         * relationship between unmarshalled objects and {@link State} parent/child relationship, and
         * it thereby makes {@link Receiver} mechanism simpler.
         *
         * <p>
         * Yes, I know this is a hack, and no, I'm not proud of it.
         *
         * @see ElementBeanInfoImpl.IntercepterLoader#startElement(State, TagName)
         * @see ElementBeanInfoImpl.IntercepterLoader#intercept(State, Object)
         */
        private Object backup;

        /**
         * Number of {@link UnmarshallingContext#nsBind}s declared thus far.
         * (The value of {@link UnmarshallingContext#nsLen} when this state is pushed.
         */
        private int numNsDecl;

        /**
         * If this element has an element default value.
         *
         * This should be set by either a parent {@link Loader} when
         * {@link Loader#childElement(State, TagName)} is called
         * or by a child {@link Loader} when
         * {@link Loader#startElement(State, TagName)} is called.
         */
        private String elementDefaultValue;

        /**
         * {@link State} for the parent element
         *
         * {@link State} objects form a doubly linked list.
         */
        private State prev;
        private State next;

        private boolean nil = false;

        /**
         * specifies that we are working with mixed content
         */
        private boolean mixed = false;

        /**
         * Gets the context.
         */
        public UnmarshallingContext getContext() {
            return UnmarshallingContext.this;
        }

        @SuppressWarnings("LeakingThisInConstructor")
        private State(State prev) {
            this.prev = prev;
            if (prev!=null) {
                prev.next = this;
                if (prev.mixed) // parent is in mixed mode
                    this.mixed = true;
            }
        }

        private void push() {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "State.push");
            }
            if (next==null) {
                assert current == this;
                next = new State(this);
            }
            nil = false;
            State n = next;
            n.numNsDecl = nsLen;
            current = n;
        }

        private void pop() {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "State.pop");
            }
            assert prev!=null;
            loader = null;
            nil = false;
            mixed = false;
            receiver = null;
            intercepter = null;
            elementDefaultValue = null;
            target = null;
            current = prev;
            next = null;
        }

        public boolean isMixed() {
            return mixed;
        }

        public Object getTarget() {
            return target;
        }

        public void setLoader(Loader loader) {
            if (loader instanceof StructureLoader) // set mixed mode
                mixed = !((StructureLoader)loader).getBeanInfo().hasElementOnlyContentModel();
            this.loader = loader;
        }

        public void setReceiver(Receiver receiver) {
            this.receiver = receiver;
        }

        public State getPrev() {
            return prev;
        }

        public void setIntercepter(Intercepter intercepter) {
            this.intercepter = intercepter;
        }

        public void setBackup(Object backup) {
            this.backup = backup;
        }

        public void setTarget(Object target) {
            this.target = target;
        }

        public Object getBackup() {
            return backup;
        }

        public boolean isNil() {
            return nil;
        }

        public void setNil(boolean nil) {
            this.nil = nil;
        }

        public Loader getLoader() {
            return loader;
        }

        public String getElementDefaultValue() {
            return elementDefaultValue;
        }

        public void setElementDefaultValue(String elementDefaultValue) {
            this.elementDefaultValue = elementDefaultValue;
        }
    }

    /**
     * Stub to the user-specified factory method.
     */
    private static class Factory {
        private final Object factorInstance;
        private final Method method;

        public Factory(Object factorInstance, Method method) {
            this.factorInstance = factorInstance;
            this.method = method;
        }

        public Object createInstance() throws SAXException {
            try {
                return method.invoke(factorInstance);
            } catch (IllegalAccessException e) {
                getInstance().handleError(e,false);
            } catch (InvocationTargetException e) {
                getInstance().handleError(e,false);
            }
            return null; // can never be executed
        }
    }


    /**
     * Creates a new unmarshaller.
     *
     * @param assoc
     *      Must be both non-null when the unmarshaller does the
     *      in-place unmarshalling. Otherwise must be both null.
     */
    public UnmarshallingContext( UnmarshallerImpl _parent, AssociationMap assoc) {
        this.parent = _parent;
        this.assoc = assoc;
        this.root = this.current = new State(null);
    }

    public void reset(InfosetScanner scanner,boolean isInplaceMode, JaxBeanInfo expectedType, IDResolver idResolver) {
        this.scanner = scanner;
        this.isInplaceMode = isInplaceMode;
        this.expectedType = expectedType;
        this.idResolver = idResolver;
    }

    public JAXBContextImpl getJAXBContext() {
        return parent.context;
    }

    public State getCurrentState() {
        return current;
    }

    /**
     * On top of {@link JAXBContextImpl#selectRootLoader(State, TagName)},
     * this method also consults {@link ClassResolver}.
     *
     * @throws SAXException
     *      if {@link ValidationEventHandler} reported a failure.
     */
    public Loader selectRootLoader(State state, TagName tag) throws SAXException {
        try {
            Loader l = getJAXBContext().selectRootLoader(state, tag);
            if(l!=null)     return l;

            if(classResolver!=null) {
                Class<?> clazz = classResolver.resolveElementName(tag.uri, tag.local);
                if(clazz!=null) {
                    JAXBContextImpl enhanced = getJAXBContext().createAugmented(clazz);
                    JaxBeanInfo<?> bi = enhanced.getBeanInfo(clazz);
                    return bi.getLoader(enhanced,true);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            handleError(e);
        }

        return null;
    }

    public void clearStates() {
        State last = current;
        while (last.next != null) last = last.next;
        while (last.prev != null) {
            last.loader = null;
            last.nil = false;
            last.receiver = null;
            last.intercepter = null;
            last.elementDefaultValue = null;
            last.target = null;
            last = last.prev;
            last.next.prev = null;
            last.next = null;
        }
        current = last;
    }

    /**
     * User-specified factory methods.
     */
    private final Map<Class,Factory> factories = new HashMap<Class, Factory>();

    public void setFactories(Object factoryInstances) {
        factories.clear();
        if(factoryInstances==null) {
            return;
        }
        if(factoryInstances instanceof Object[]) {
            for( Object factory : (Object[])factoryInstances ) {
                // look for all the public methods inlcuding derived ones
                addFactory(factory);
            }
        } else {
            addFactory(factoryInstances);
        }
    }

    private void addFactory(Object factory) {
        for( Method m : factory.getClass().getMethods() ) {
            // look for methods whose signature is T createXXX()
            if(!m.getName().startsWith("create"))
                continue;
            if(m.getParameterTypes().length>0)
                continue;

            Class type = m.getReturnType();

            factories.put(type,new Factory(factory,m));
        }
    }

    @Override
    public void startDocument(LocatorEx locator, NamespaceContext nsContext) throws SAXException {
        if(locator!=null)
            this.locator = locator;
        this.environmentNamespaceContext = nsContext;
        // reset the object
        result = null;
        current = root;

        patchersLen=0;
        aborted = false;
        isUnmarshalInProgress = true;
        nsLen=0;

        if(expectedType!=null)
            root.loader = EXPECTED_TYPE_ROOT_LOADER;
        else
            root.loader = DEFAULT_ROOT_LOADER;

        idResolver.startDocument(this);
    }

    @Override
    public void startElement(TagName tagName) throws SAXException {
        pushCoordinator();
        try {
            _startElement(tagName);
        } finally {
            popCoordinator();
        }
    }

    private void _startElement(TagName tagName) throws SAXException {
        // remember the current element if we are interested in it.
        // because the inner peer might not be found while we consume
        // the enter element token, we need to keep this information
        // longer than this callback. That's why we assign it to a field.
        if( assoc!=null )
            currentElement = scanner.getCurrentElement();

        Loader h = current.loader;
        current.push();

        // tell the parent about the new child
        h.childElement(current,tagName);
        assert current.loader!=null;   // the childElement should register this
        // and tell the new child that you are activated
        current.loader.startElement(current,tagName);
    }

    @Override
    public void text(CharSequence pcdata) throws SAXException {
        pushCoordinator();
        try {
            if (current.elementDefaultValue != null) {
                if (pcdata.length() == 0) {
                    // send the default value into the unmarshaller instead
                    pcdata = current.elementDefaultValue;
                }
            }
            current.loader.text(current, pcdata);
        } finally {
            popCoordinator();
        }
    }

    @Override
    public final void endElement(TagName tagName) throws SAXException {
        pushCoordinator();
        try {
            State child = current;

            // tell the child that your time is up
            child.loader.leaveElement(child,tagName);

            // child.pop will erase them so store them now
            Object target = child.target;
            Receiver recv = child.receiver;
            Intercepter intercepter = child.intercepter;
            child.pop();

            // then let the parent know
            if(intercepter!=null)
                target = intercepter.intercept(current,target);
            if(recv!=null)
                recv.receive(current,target);
        } finally {
            popCoordinator();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        runPatchers();
        idResolver.endDocument();

        isUnmarshalInProgress = false;
        currentElement = null;
        locator = DUMMY_INSTANCE;
        environmentNamespaceContext = null;

        // at the successful completion, scope must be all closed
        assert root==current;
    }

    /**
     * You should be always calling this through {@link TextPredictor}.
     */
    @Deprecated
    @Override
    public boolean expectText() {
        return current.loader.expectText;
    }

    /**
     * You should be always getting {@link TextPredictor} from {@link XmlVisitor}.
     */
    @Deprecated
    @Override
    public TextPredictor getPredictor() {
        return this;
    }

    @Override
    public UnmarshallingContext getContext() {
        return this;
    }

    /**
     * Gets the result of the unmarshalling
     */
    public Object getResult() throws UnmarshalException {
        if(isUnmarshalInProgress)
            throw new IllegalStateException();

        if(!aborted)       return result;

        // there was an error.
        throw new UnmarshalException((String)null);
    }

    void clearResult() {
        if (isUnmarshalInProgress) {
            throw new IllegalStateException();
        }
        result = null;
    }

    /**
     * Creates a new instance of the specified class.
     * In the unmarshaller, we need to check the user-specified factory class.
     */
    public Object createInstance( Class<?> clazz ) throws SAXException {
        if(!factories.isEmpty()) {
            Factory factory = factories.get(clazz);
            if(factory!=null)
                return factory.createInstance();
        }
        return ClassFactory.create(clazz);
    }

    /**
     * Creates a new instance of the specified class.
     * In the unmarshaller, we need to check the user-specified factory class.
     */
    public Object createInstance( JaxBeanInfo beanInfo ) throws SAXException {
        if(!factories.isEmpty()) {
            Factory factory = factories.get(beanInfo.jaxbType);
            if(factory!=null)
                return factory.createInstance();
        }
        try {
            return beanInfo.createInstance(this);
        } catch (IllegalAccessException e) {
            Loader.reportError("Unable to create an instance of "+beanInfo.jaxbType.getName(),e,false);
        } catch (InvocationTargetException e) {
            Loader.reportError("Unable to create an instance of "+beanInfo.jaxbType.getName(),e,false);
        } catch (InstantiationException e) {
            Loader.reportError("Unable to create an instance of "+beanInfo.jaxbType.getName(),e,false);
        }
        return null;    // can never be here
    }



//
//
// error handling
//
//

    /**
     * Reports an error to the user, and asks if s/he wants
     * to recover. If the canRecover flag is false, regardless
     * of the client instruction, an exception will be thrown.
     *
     * Only if the flag is true and the user wants to recover from an error,
     * the method returns normally.
     *
     * The thrown exception will be catched by the unmarshaller.
     */
    public void handleEvent(ValidationEvent event, boolean canRecover ) throws SAXException {
        ValidationEventHandler eventHandler = parent.getEventHandler();

        boolean recover = eventHandler.handleEvent(event);

        // if the handler says "abort", we will not return the object
        // from the unmarshaller.getResult()
        if(!recover)    aborted = true;

        if( !canRecover || !recover )
            throw new SAXParseException2( event.getMessage(), locator,
                new UnmarshalException(
                    event.getMessage(),
                    event.getLinkedException() ) );
    }

    @Override
    public boolean handleEvent(ValidationEvent event) {
        try {
            // if the handler says "abort", we will not return the object.
            boolean recover = parent.getEventHandler().handleEvent(event);
            if(!recover)    aborted = true;
            return recover;
        } catch( RuntimeException re ) {
            // if client event handler causes a runtime exception, then we
            // have to return false.
            return false;
        }
    }

    /**
     * Reports an exception found during the unmarshalling to the user.
     * This method is a convenience method that calls into
     * {@link #handleEvent(ValidationEvent, boolean)}
     */
    public void handleError(Exception e) throws SAXException {
        handleError(e,true);
    }

    public void handleError(Exception e,boolean canRecover) throws SAXException {
        handleEvent(new ValidationEventImpl(ValidationEvent.ERROR,e.getMessage(),locator.getLocation(),e),canRecover);
    }

    public void handleError(String msg) {
        handleEvent(new ValidationEventImpl(ValidationEvent.ERROR,msg,locator.getLocation()));
    }

    @Override
    protected ValidationEventLocator getLocation() {
        return locator.getLocation();
    }

    /**
     * Gets the current source location information in SAX {@link Locator}.
     * <p>
     * Sometimes the unmarshaller works against a different kind of XML source,
     * making this information meaningless.
     */
    public LocatorEx getLocator() { return locator; }

    /**
     * Called when there's no corresponding ID value.
     */
    public void errorUnresolvedIDREF(Object bean, String idref, LocatorEx loc) throws SAXException {
        handleEvent( new ValidationEventImpl(
            ValidationEvent.ERROR,
            Messages.UNRESOLVED_IDREF.format(idref),
            loc.getLocation()), true );
    }


//
//
// ID/IDREF related code
//
//
    /**
     * Submitted patchers in the order they've submitted.
     * Many XML vocabulary doesn't use ID/IDREF at all, so we
     * initialize it with null.
     */
    private Patcher[] patchers = null;
    private int patchersLen = 0;

    /**
     * Adds a job that will be executed at the last of the unmarshalling.
     * This method is used to support ID/IDREF feature, but it can be used
     * for other purposes as well.
     *
     * @param   job
     *      The run method of this object is called.
     */
    public void addPatcher( Patcher job ) {
        // re-allocate buffer if necessary
        if( patchers==null )
            patchers = new Patcher[32];
        if( patchers.length == patchersLen ) {
            Patcher[] buf = new Patcher[patchersLen*2];
            System.arraycopy(patchers,0,buf,0,patchersLen);
            patchers = buf;
        }
        patchers[patchersLen++] = job;
    }

    /** Executes all the patchers. */
    private void runPatchers() throws SAXException {
        if( patchers!=null ) {
            for( int i=0; i<patchersLen; i++ ) {
                patchers[i].run();
                patchers[i] = null; // free memory
            }
        }
    }

    /**
     * Adds the object which is currently being unmarshalled
     * to the ID table.
     *
     * @return
     *      Returns the value passed as the parameter.
     *      This is a hack, but this makes it easier for ID
     *      transducer to do its job.
     */
    // TODO: what shall we do if the ID is already declared?
    //
    // throwing an exception is one way. Overwriting the previous one
    // is another way. The latter allows us to process invalid documents,
    // while the former makes it impossible to handle them.
    //
    // I prefer to be flexible in terms of invalid document handling,
    // so chose not to throw an exception.
    //
    // I believe this is an implementation choice, not the spec issue.
    // -kk
    public String addToIdTable( String id ) throws SAXException {
        // Hmm...
        // in cases such as when ID is used as an attribute, or as @XmlValue
        // the target wilil be current.target.
        // but in some other cases, such as when ID is used as a child element
        // or a value of JAXBElement, it's current.prev.target.
        // I don't know if this detection logic is complete
        Object o = current.target;
        if(o==null)
            o = current.prev.target;
        idResolver.bind(id,o);
        return id;
    }

    /**
     * Looks up the ID table and gets associated object.
     *
     * <p>
     * The exception thrown from {@link Callable#call()} means the unmarshaller should abort
     * right away.
     *
     * @see IDResolver#resolve(String, Class)
     */
    public Callable getObjectFromId( String id, Class targetType ) throws SAXException {
        return idResolver.resolve(id,targetType);
    }

//
//
// namespace binding maintainance
//
//
    private String[] nsBind = new String[16];
    private int nsLen=0;

    @Override
    public void startPrefixMapping( String prefix, String uri ) {
        if(nsBind.length==nsLen) {
            // expand the buffer
            String[] n = new String[nsLen*2];
            System.arraycopy(nsBind,0,n,0,nsLen);
            nsBind=n;
        }
        nsBind[nsLen++] = prefix;
        nsBind[nsLen++] = uri;
    }
    @Override
    public void endPrefixMapping( String prefix ) {
        nsLen-=2;
    }
    private String resolveNamespacePrefix( String prefix ) {
        if(prefix.equals("xml"))
            return XMLConstants.XML_NS_URI;

        for( int i=nsLen-2; i>=0; i-=2 ) {
            if(prefix.equals(nsBind[i]))
                return nsBind[i+1];
        }

        if(environmentNamespaceContext!=null)
            // temporary workaround until Zephyr fixes 6337180
            return environmentNamespaceContext.getNamespaceURI(prefix.intern());

        // by default, the default ns is bound to "".
        // but allow environmentNamespaceContext to take precedence
        if(prefix.equals(""))
            return "";

        // unresolved. error.
        return null;
    }

    /**
     * Returns a list of prefixes newly declared on the current element.
     *
     * @return
     *      A possible zero-length array of prefixes. The default prefix
     *      is represented by the empty string.
     */
    public String[] getNewlyDeclaredPrefixes() {
        return getPrefixList( current.prev.numNsDecl );
    }

    /**
     * Returns a list of all in-scope prefixes.
     *
     * @return
     *      A possible zero-length array of prefixes. The default prefix
     *      is represented by the empty string.
     */
    public String[] getAllDeclaredPrefixes() {
        return getPrefixList(0);
    }

    private String[] getPrefixList( int startIndex ) {
        int size = (current.numNsDecl - startIndex)/2;
        String[] r = new String[size];
        for( int i=0; i<r.length; i++ )
            r[i] = nsBind[startIndex+i*2];
        return r;
    }

    //  NamespaceContext2 implementation
    //
    @Override
    public Iterator<String> getPrefixes(String uri) {
        // TODO: could be implemented much faster
        // wrap it into unmodifiable list so that the remove method
        // will throw UnsupportedOperationException.
        return Collections.unmodifiableList(
            getAllPrefixesInList(uri)).iterator();
    }

    private List<String> getAllPrefixesInList(String uri) {
        List<String> a = new ArrayList<String>();

        if( uri==null )
            throw new IllegalArgumentException();
        if( uri.equals(XMLConstants.XML_NS_URI) ) {
            a.add(XMLConstants.XML_NS_PREFIX);
            return a;
        }
        if( uri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI) ) {
            a.add(XMLConstants.XMLNS_ATTRIBUTE);
            return a;
        }

        for( int i=nsLen-2; i>=0; i-=2 )
            if(uri.equals(nsBind[i+1]))
                if( getNamespaceURI(nsBind[i]).equals(nsBind[i+1]) )
                    // make sure that this prefix is still effective.
                    a.add(nsBind[i]);

        return a;
    }

    @Override
    public String getPrefix(String uri) {
        if( uri==null )
            throw new IllegalArgumentException();
        if( uri.equals(XMLConstants.XML_NS_URI) )
            return XMLConstants.XML_NS_PREFIX;
        if( uri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI) )
            return XMLConstants.XMLNS_ATTRIBUTE;

        for( int i=nsLen-2; i>=0; i-=2 )
            if(uri.equals(nsBind[i+1]))
                if( getNamespaceURI(nsBind[i]).equals(nsBind[i+1]) )
                    // make sure that this prefix is still effective.
                    return nsBind[i];

        if(environmentNamespaceContext!=null)
            return environmentNamespaceContext.getPrefix(uri);

        return null;
    }

    @Override
    public String getNamespaceURI(String prefix) {
        if (prefix == null)
            throw new IllegalArgumentException();
        if (prefix.equals(XMLConstants.XMLNS_ATTRIBUTE))
            return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;

        return resolveNamespacePrefix(prefix);
    }

//
//
//
// scope management
//
//
//
    private Scope[] scopes = new Scope[16];
    /**
     * Points to the top of the scope stack (=size-1).
     */
    private int scopeTop=0;

    {
        for( int i=0; i<scopes.length; i++ )
            scopes[i] = new Scope(this);
    }

    /**
     * Starts a new packing scope.
     *
     * <p>
     * This method allocates a specified number of fresh {@link Scope} objects.
     * They can be accessed by the {@link #getScope} method until the corresponding
     * {@link #endScope} method is invoked.
     *
     * <p>
     * A new scope will mask the currently active scope. Only one frame of {@link Scope}s
     * can be accessed at any given time.
     *
     * @param frameSize
     *      The # of slots to be allocated.
     */
    public void startScope(int frameSize) {
        scopeTop += frameSize;

        // reallocation
        if(scopeTop>=scopes.length) {
            Scope[] s = new Scope[Math.max(scopeTop+1,scopes.length*2)];
            System.arraycopy(scopes,0,s,0,scopes.length);
            for( int i=scopes.length; i<s.length; i++ )
                s[i] = new Scope(this);
            scopes = s;
        }
    }

    /**
     * Ends the current packing scope.
     *
     * <p>
     * If any packing in progress will be finalized by this method.
     *
     * @param frameSize
     *      The same size that gets passed to the {@link #startScope(int)}
     *      method.
     */
    public void endScope(int frameSize) throws SAXException {
        try {
            for( ; frameSize>0; frameSize--, scopeTop-- )
                scopes[scopeTop].finish();
        } catch (AccessorException e) {
            handleError(e);

            // the error might have left scopes in inconsistent state,
            // so replace them by fresh ones
            for( ; frameSize>0; frameSize-- )
                scopes[scopeTop--] = new Scope(this);
        }
    }

    /**
     * Gets the currently active {@link Scope}.
     *
     * @param offset
     *      a number between [0,frameSize)
     *
     * @return
     *      always a valid {@link Scope} object.
     */
    public Scope getScope(int offset) {
        return scopes[scopeTop-offset];
    }

//
//
//
//
//
//
//

    private static final Loader DEFAULT_ROOT_LOADER = new DefaultRootLoader();
    private static final Loader EXPECTED_TYPE_ROOT_LOADER = new ExpectedTypeRootLoader();

    /**
     * Root loader that uses the tag name and possibly its @xsi:type
     * to decide how to start unmarshalling.
     */
    private static final class DefaultRootLoader extends Loader implements Receiver {
        /**
         * Receives the root element and determines how to start
         * unmarshalling.
         */
        @Override
        public void childElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
            Loader loader = state.getContext().selectRootLoader(state,ea);
            if(loader!=null) {
                state.loader = loader;
                state.receiver = this;
                return;
            }

            // the registry doesn't know about this element.
            // try its xsi:type
            JaxBeanInfo beanInfo = XsiTypeLoader.parseXsiType(state, ea, null);
            if(beanInfo==null) {
                // we don't even know its xsi:type
                reportUnexpectedChildElement(ea,false);
                return;
            }

            state.loader = beanInfo.getLoader(null,false);
            state.prev.backup = new JAXBElement<Object>(ea.createQName(),Object.class,null);
            state.receiver = this;
        }

        @Override
        public Collection<QName> getExpectedChildElements() {
            return getInstance().getJAXBContext().getValidRootNames();
        }

        @Override
        public void receive(State state, Object o) {
             if(state.backup!=null) {
                ((JAXBElement<Object>)state.backup).setValue(o);
                o = state.backup;
            }
            if (state.nil) {
                ((JAXBElement<Object>)o).setNil(true);
            }
            state.getContext().result = o;
        }
    }

    /**
     * Root loader that uses {@link UnmarshallingContext#expectedType}
     * to decide how to start unmarshalling.
     */
    private static final class ExpectedTypeRootLoader extends Loader implements Receiver {
        /**
         * Receives the root element and determines how to start
         * unmarshalling.
         */
        @Override
        public void childElement(UnmarshallingContext.State state, TagName ea) {
            UnmarshallingContext context = state.getContext();

            // unmarshals the specified type
            QName qn = new QName(ea.uri,ea.local);
            state.prev.target = new JAXBElement(qn,context.expectedType.jaxbType,null,null);
            state.receiver = this;
            // this is bit wasteful, as in theory we should have each expectedType keep
            // nillable version --- but that increases the combination from two to four,
            // which adds the resident memory footprint. Since XsiNilLoader is small,
            // I intentionally allocate a new instance freshly.
            state.loader = new XsiNilLoader(context.expectedType.getLoader(null,true));
        }

        @Override
        public void receive(State state, Object o) {
            JAXBElement e = (JAXBElement)state.target;
            e.setValue(o);
            state.getContext().recordOuterPeer(e);
            state.getContext().result = e;
        }
    }

//
// in-place unmarshalling related capabilities
//
    /**
     * Notifies the context about the inner peer of the current element.
     *
     * <p>
     * If the unmarshalling is building the association, the context
     * will use this information. Otherwise it will be just ignored.
     */
    public void recordInnerPeer(Object innerPeer) {
        if(assoc!=null)
            assoc.addInner(currentElement,innerPeer);
    }

    /**
     * Gets the inner peer JAXB object associated with the current element.
     *
     * @return
     *      null if the current element doesn't have an inner peer,
     *      or if we are not doing the in-place unmarshalling.
     */
    public Object getInnerPeer() {
        if(assoc!=null && isInplaceMode)
            return assoc.getInnerPeer(currentElement);
        else
            return null;
    }

    /**
     * Notifies the context about the outer peer of the current element.
     *
     * <p>
     * If the unmarshalling is building the association, the context
     * will use this information. Otherwise it will be just ignored.
     */
    public void recordOuterPeer(Object outerPeer) {
        if(assoc!=null)
            assoc.addOuter(currentElement,outerPeer);
    }

    /**
     * Gets the outer peer JAXB object associated with the current element.
     *
     * @return
     *      null if the current element doesn't have an inner peer,
     *      or if we are not doing the in-place unmarshalling.
     */
    public Object getOuterPeer() {
        if(assoc!=null && isInplaceMode)
            return assoc.getOuterPeer(currentElement);
        else
            return null;
    }

    /**
     * Gets the xmime:contentType value for the current object.
     *
     * @see JAXBContextImpl#getXMIMEContentType(Object)
     */
    public String getXMIMEContentType() {
        /*
            this won't work when the class is like

            class Foo {
                @XmlValue Image img;
            }

            because the target will return Foo, not the class enclosing Foo
            which will have xmime:contentType
        */
        Object t = current.target;
        if(t==null)     return null;
        return getJAXBContext().getXMIMEContentType(t);
    }

    /**
     * When called from within the realm of the unmarshaller, this method
     * returns the current {@link UnmarshallingContext} in charge.
     */
    public static UnmarshallingContext getInstance() {
        return (UnmarshallingContext) Coordinator._getInstance();
    }

    /**
     * Allows to access elements which are expected in current state.
     * Useful for getting elements for current parent.
     *
     * @return
     */
    public Collection<QName> getCurrentExpectedElements() {
        pushCoordinator();
        try {
            State s = getCurrentState();
            Loader l = s.loader;
            return (l != null) ? l.getExpectedChildElements() : null;
        } finally {
            popCoordinator();
        }
    }

    /**
     * Allows to access attributes which are expected in current state.
     * Useful for getting attributes for current parent.
     *
     * @return
     */
    public Collection<QName> getCurrentExpectedAttributes() {
        pushCoordinator();
        try {
            State s = getCurrentState();
            Loader l = s.loader;
            return (l != null) ? l.getExpectedAttributes() : null;
        } finally {
            popCoordinator();
        }
    }

    /**
     * Gets StructureLoader if used as loader.
     * Useful when determining if element is mixed or not.
     *
     */
    public StructureLoader getStructureLoader() {
        if(current.loader instanceof StructureLoader)
            return (StructureLoader)current.loader;

        return null;
    }

    /**
     * Based on current {@link Logger} {@link Level} and errorCounter value determines if error should be reported.
     *
     * If the method called and return true it is expected that error will be reported. And that's why
     * errorCounter is automatically decremented during the check.
     *
     * NOT THREAD SAFE!!! In case of heave concurrency access several additional errors could be reported. It's not expected to be the
     * problem. Otherwise add synchronization here.
     *
     * @return true in case if {@link Level#FINEST} is set OR we haven't exceed errors reporting limit.
     */
    public boolean shouldErrorBeReported() throws SAXException {
        if (logger.isLoggable(Level.FINEST))
            return true;

        if (errorsCounter >= 0) {
            --errorsCounter;
            if (errorsCounter == 0) // it's possible to miss this because of concurrency. If required add synchronization here
                handleEvent(new ValidationEventImpl(ValidationEvent.WARNING, Messages.ERRORS_LIMIT_EXCEEDED.format(),
                        getLocator().getLocation(), null), true);
        }
        return errorsCounter >= 0;
    }
}
