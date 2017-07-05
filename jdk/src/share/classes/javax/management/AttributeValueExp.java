/*
 * Copyright 1999-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.management;


// RI import
import javax.management.MBeanServer;

/**
 * Represents attributes used as arguments to relational constraints.
 * An <CODE>AttributeValueExp</CODE> may be used anywhere a <CODE>ValueExp</CODE> is required.
 *
 * @since 1.5
 */
public class AttributeValueExp implements ValueExp  {


    /* Serial version */
    private static final long serialVersionUID = -7768025046539163385L;

    /**
     * @serial The name of the attribute
     */
    private String attr;

    /**
     * An <code>AttributeValueExp</code> with a null attribute.
     * @deprecated An instance created with this constructor cannot be
     * used in a query.
     */
    @Deprecated
    public AttributeValueExp() {
    }

    /**
     * Creates a new <CODE>AttributeValueExp</CODE> representing the
     * specified object attribute, named attr.
     *
     * @param attr the name of the attribute whose value is the value
     * of this {@link ValueExp}.
     */
    public AttributeValueExp(String attr) {
        this.attr = attr;
    }

    /**
     * Returns a string representation of the name of the attribute.
     *
     * @return the attribute name.
     */
    public String getAttributeName()  {
        return attr;
    }

    /**
     * Applies the <CODE>AttributeValueExp</CODE> on an MBean.
     *
     * @param name The name of the MBean on which the <CODE>AttributeValueExp</CODE> will be applied.
     *
     * @return  The <CODE>ValueExp</CODE>.
     *
     * @exception BadAttributeValueExpException
     * @exception InvalidApplicationException
     * @exception BadStringOperationException
     * @exception BadBinaryOpValueExpException
     *
     */
    public ValueExp apply(ObjectName name) throws BadStringOperationException, BadBinaryOpValueExpException,
        BadAttributeValueExpException, InvalidApplicationException {
        Object result = getAttribute(name);

        if (result instanceof Number) {
            return new NumericValueExp((Number)result);
        } else if (result instanceof String) {
            return new StringValueExp((String)result);
        } else if (result instanceof Boolean) {
            return new BooleanValueExp((Boolean)result);
        } else {
            throw new BadAttributeValueExpException(result);
        }
    }

    /**
     * Returns the string representing its value.
     */
    public String toString()  {
        return attr;
    }


    /**
     * Sets the MBean server on which the query is to be performed.
     *
     * @param s The MBean server on which the query is to be performed.
     */
    /* There is no need for this method, because if a query is being
       evaluted an AttributeValueExp can only appear inside a QueryExp,
       and that QueryExp will itself have done setMBeanServer.  */
    public void setMBeanServer(MBeanServer s)  {
    }


    /**
     * Return the value of the given attribute in the named MBean.
     * If the attempt to access the attribute generates an exception,
     * return null.
     *
     * @param name the name of the MBean whose attribute is to be returned.
     *
     * @return the value of the attribute, or null if it could not be
     * obtained.
     */
    protected Object getAttribute(ObjectName name) {
        try {
            // Get the value from the MBeanServer

            MBeanServer server = QueryEval.getMBeanServer();

            return server.getAttribute(name, attr);
        } catch (Exception re) {
            return null;
        }
    }

}
