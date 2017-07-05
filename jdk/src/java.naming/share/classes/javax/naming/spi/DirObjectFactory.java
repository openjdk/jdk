/*
 * Copyright (c) 1999, 2004, Oracle and/or its affiliates. All rights reserved.
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

package javax.naming.spi;

import java.util.Hashtable;
import javax.naming.*;
import javax.naming.directory.Attributes;

/**
  * This interface represents a factory for creating an object given
  * an object and attributes about the object.
  *<p>
  * The JNDI framework allows for object implementations to
  * be loaded in dynamically via <em>object factories</em>. See
  * <tt>ObjectFactory</tt> for details.
  * <p>
  * A <tt>DirObjectFactory</tt> extends <tt>ObjectFactory</tt> by allowing
  * an <tt>Attributes</tt> instance
  * to be supplied to the <tt>getObjectInstance()</tt> method.
  * <tt>DirObjectFactory</tt> implementations are intended to be used by <tt>DirContext</tt>
  * service providers. The service provider, in addition reading an
  * object from the directory, might already have attributes that
  * are useful for the object factory to check to see whether the
  * factory is supposed to process the object. For instance, an LDAP-style
  * service provider might have read the "objectclass" of the object.
  * A CORBA object factory might be interested only in LDAP entries
  * with "objectclass=corbaObject". By using the attributes supplied by
  * the LDAP service provider, the CORBA object factory can quickly
  * eliminate objects that it need not worry about, and non-CORBA object
  * factories can quickly eliminate CORBA-related LDAP entries.
  *
  * @author Rosanna Lee
  * @author Scott Seligman
  *
  * @see NamingManager#getObjectInstance
  * @see DirectoryManager#getObjectInstance
  * @see ObjectFactory
  * @since 1.3
  */

public interface DirObjectFactory extends ObjectFactory {
/**
 * Creates an object using the location or reference information, and attributes
 * specified.
 * <p>
 * Special requirements of this object are supplied
 * using <code>environment</code>.
 * An example of such an environment property is user identity
 * information.
 *<p>
 * <tt>DirectoryManager.getObjectInstance()</tt>
 * successively loads in object factories. If it encounters a <tt>DirObjectFactory</tt>,
 * it will invoke <tt>DirObjectFactory.getObjectInstance()</tt>;
 * otherwise, it invokes
 * <tt>ObjectFactory.getObjectInstance()</tt>. It does this until a factory
 * produces a non-null answer.
 * <p> When an exception
 * is thrown by an object factory, the exception is passed on to the caller
 * of <tt>DirectoryManager.getObjectInstance()</tt>. The search for other factories
 * that may produce a non-null answer is halted.
 * An object factory should only throw an exception if it is sure that
 * it is the only intended factory and that no other object factories
 * should be tried.
 * If this factory cannot create an object using the arguments supplied,
 * it should return null.
  *<p>Since <tt>DirObjectFactory</tt> extends <tt>ObjectFactory</tt>, it
  * effectively
  * has two <tt>getObjectInstance()</tt> methods, where one differs from the other by
  * the attributes argument. Given a factory that implements <tt>DirObjectFactory</tt>,
  * <tt>DirectoryManager.getObjectInstance()</tt> will only
  * use the method that accepts the attributes argument, while
  * <tt>NamingManager.getObjectInstance()</tt> will only use the one that does not accept
  * the attributes argument.
 *<p>
 * See <tt>ObjectFactory</tt> for a description URL context factories and other
 * properties of object factories that apply equally to <tt>DirObjectFactory</tt>.
 *<p>
 * The <tt>name</tt>, <tt>attrs</tt>, and <tt>environment</tt> parameters
 * are owned by the caller.
 * The implementation will not modify these objects or keep references
 * to them, although it may keep references to clones or copies.
 *
 * @param obj The possibly null object containing location or reference
 *              information that can be used in creating an object.
 * @param name The name of this object relative to <code>nameCtx</code>,
 *              or null if no name is specified.
 * @param nameCtx The context relative to which the <code>name</code>
 *              parameter is specified, or null if <code>name</code> is
 *              relative to the default initial context.
 * @param environment The possibly null environment that is used in
 *              creating the object.
 * @param attrs The possibly null attributes containing some of <tt>obj</tt>'s
 * attributes. <tt>attrs</tt> might not necessarily have all of <tt>obj</tt>'s
 * attributes. If the object factory requires more attributes, it needs
 * to get it, either using <tt>obj</tt>, or <tt>name</tt> and <tt>nameCtx</tt>.
 *      The factory must not modify attrs.
 * @return The object created; null if an object cannot be created.
 * @exception Exception If this object factory encountered an exception
 * while attempting to create an object, and no other object factories are
 * to be tried.
 *
 * @see DirectoryManager#getObjectInstance
 * @see NamingManager#getURLContext
 */
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                    Hashtable<?,?> environment,
                                    Attributes attrs)
        throws Exception;
}
