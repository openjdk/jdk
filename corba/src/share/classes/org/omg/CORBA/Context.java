/*
 * Copyright (c) 1996, 2000, Oracle and/or its affiliates. All rights reserved.
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

package org.omg.CORBA;

/**
 * An object used in <code>Request</code> operations
 * to specify the context object in which context strings
 * must be resolved before being sent along with the request invocation.
 * A <code>Context</code> object
 * contains a list of properties in the form of <code>NamedValue</code>
 * objects. These properties represent information
 * about the client, the environment, or the circumstances of a request
 * and generally are properties that might be inconvenient
 * to pass as parameters.
 * <P>
 * A <code>Context</code> object is created by first calling the
 * <code>ORB</code> method <code>get_default_context</code>
 * and then calling the method <code>create_child</code> on the
 * default context.
 * <P>
 * Each property in a <code>Context</code> object is represented by
 * a <code>NamedValue</code> object.  The property name is contained
 * in the <code>NamedValue</code> object's <code>name</code> field, and
 * the value associated with the name is contained in the <code>Any</code>
 * object that was assigned to the <code>NamedValue</code> object's
 * <code>value</code> field.
 * <P>
 * <code>Context</code> properties can represent a portion of a client's
 * or application's environment that is meant to be propagated to
 * (and made implicitly part of) a server's environment.
 * (Examples might be a window identifier or user preference information).
 * Once a server has been invoked (that is, after the properties are
 * propagated), the server may query its <code>Context</code> object
 * for these properties using the method <code>get_values</code>.
 *
 *<P>
 * When an operation declaration includes a context clause,
 * the stubs and skeletons will have an additional argument
 * added for the context.  When an operation invocation occurs,
 * the ORB causes the properties that were named in the operation
 * definition in IDL and
 * that are present in the client's <code>Context</code> object
 * to be provided in the <code>Context</code> object parameter to
 * the invoked method.
 * <P>
 * <code>Context</code> property names (which are strings)
 * typically have the form of an OMG IDL identifier or
 * a series of OMG IDL identifiers separated by periods.
 * A context property name pattern is either a property name
 * or a property name followed by a single "*".  A property
 * name pattern without a trailing "*" is said to match only
 * itself.  A property name pattern of the form "&lt;name&gt;*" matches any
 * property name that starts with &lt;name&gt; and continues with zero
 * or more additional characters.
 * <P>
 * Property name patterns are used in the context clause of
 * an operation definition and as a parameter for the
 * method <code>Context.get_values</code>.
 * <P>
 * <code>Context</code> objects may be "chained" together to achieve a
 * particular defaulting behavior.  A <code>Context</code>
 * object created with the method <code>create_child</code> will
 * be chained to its parent (the <code>Context</code> object
 * that created it), and that means that the parent will be searched
 * after the child in a search for property names.
 *<P>
 * Properties defined in a particular <code>Context</code> object
 * effectively override those properties in the next higher level.
 * The scope used in a search for properties may be restricted by specifying a
 * starting scope and by using the flag <code>CTX_RESTRICT_SCOPE</code>
 * when invoking the method <code>get_values</code>.
 * <P>
 * A <code>Context</code> object may be named for purposes of specifying
 * a starting search scope.
 *
 * @since   JDK1.2
 */

public abstract class Context {

    /**
     * Retrieves the name of this <code>Context</code> object.
     *
     * @return                  the name of this <code>Context</code> object
     */

    public abstract String context_name();


    /**
     * Retrieves the parent of this <code>Context</code> object.
     *
     * @return                  the <code>Context</code> object that is the
     *                    parent of this <code>Context</code> object
     */

    public abstract Context parent();

    /**
     * Creates a <code>Context</code> object with the given string as its
     * name and with this <code>Context</code> object set as its parent.
     * <P>
     * The new <code>Context</code> object is chained into its parent
     * <code>Context</code> object.  This means that in a search for
     * matching property names, if a match is not found in this context,
     * the search will continue in the parent.  If that is not successful,
     * the search will continue in the grandparent, if there is one, and
     * so on.
     *
     *
     * @param child_ctx_name    the <code>String</code> object to be set as
     *                        the name of the new <code>Context</code> object
     * @return                  the newly-created child <code>Context</code> object
     *                    initialized with the specified name
     */

    public abstract Context create_child(String child_ctx_name);

    /**
     * Creates a <code>NamedValue</code> object and adds it to this
     * <code>Context</code> object.  The <code>name</code> field of the
     * new <code>NamedValue</code> object is set to the given string,
     * the <code>value</code> field is set to the given <code>Any</code>
     * object, and the <code>flags</code> field is set to zero.
     *
     * @param propname          the name of the property to be set
     * @param propvalue         the <code>Any</code> object to which the
     *                        value of the property will be set.  The
     *                        <code>Any</code> object's <code>value</code>
     *                        field contains the value to be associated
     *                        with the given propname; the
     *                        <code>kind</code> field must be set to
     *                        <code>TCKind.tk_string</code>.
     */

    public abstract void set_one_value(String propname, Any propvalue);

    /**
       I Sets one or more property values in this <code>Context</code>
       * object. The <code>NVList</code> supplied to this method
       * contains one or more <code>NamedValue</code> objects.
       * In each <code>NamedValue</code> object,
       * the <code>name</code> field holds the name of the property, and
       * the <code>flags</code> field must be set to zero.
       * The <code>NamedValue</code> object's <code>value</code> field
       * contains an <code>Any</code> object, which, in turn, contains the value
       * for the property.  Since the value is always a string,
       * the <code>Any</code> object must have the <code>kind</code>
       * field of its <code>TypeCode</code> set to <code>TCKind.tk_string</code>.
       *
       * @param values          an NVList containing the property
       *                                    names and associated values to be set
       *
       * @see #get_values
       * @see org.omg.CORBA.NamedValue
       * @see org.omg.CORBA.Any
       */

    public abstract void set_values(NVList values);

    /**
     * Deletes from this <code>Context</code> object the
     * <code>NamedValue</code> object(s) whose
     * <code>name</code> field matches the given property name.
     * If the <code>String</code> object supplied for
     * <code>propname</code> has a
     * trailing wildcard character ("*"), then
     * all <code>NamedValue</code> objects whose <code>name</code>
     * fields match will be deleted. The search scope is always
     * limited to this <code>Context</code> object.
     * <P>
     * If no matching property is found, an exception is returned.
     *
     * @param propname          name of the property to be deleted
     */

    public abstract void delete_values(String propname);

    /**
     * Retrieves the <code>NamedValue</code> objects whose
     * <code>name</code> field matches the given name or name
     * pattern.   This method allows for wildcard searches,
     * which means that there can be multiple matches and
     * therefore multiple values returned. If the
     * property is not found at the indicated level, the search
     * continues up the context object tree until a match is found or
     * all <code>Context</code> objects in the chain have been exhausted.
     * <P>
     * If no match is found, an error is returned and no property list
     * is returned.
     *
     * @param start_scope               a <code>String</code> object indicating the
     *                context object level at which to initiate the
     *                          search for the specified properties
     *                          (for example, "_USER", "_GROUP", "_SYSTEM"). Valid scope
     *                          names are implementation-specific. If a
     *                          scope name is omitted, the search
     *                          begins with the specified context
     *                          object. If the specified scope name is
     *                          not found, an exception is returned.
     * @param op_flags       an operation flag.  The one flag
     *                that may be specified is <code>CTX_RESTRICT_SCOPE</code>.
     *                If this flag is specified, searching is limited to the
     *                          specified <code>start_scope</code> or this
     *                <code>Context</code> object.
     * @param pattern           the property name whose values are to
     *                          be retrieved. <code>pattern</code> may be a
     *                name or a name with a
     *                          trailing wildcard character ("*").
     *
     * @return          an <code>NVList</code> containing all the property values
     *                (in the form of <code>NamedValue</code> objects)
     *                whose associated property name matches the given name or
     *                name pattern
     * @see #set_values
     * @see org.omg.CORBA.NamedValue
     */

    abstract public NVList get_values(String start_scope, int op_flags,
                                      String pattern);
};
