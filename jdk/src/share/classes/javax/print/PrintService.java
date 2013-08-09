/*
 * Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
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

package javax.print;

import java.util.Locale;

import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.PrintServiceAttribute;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.event.PrintServiceAttributeListener;


/**
 * Interface PrintService is the factory for a DocPrintJob. A PrintService
 * describes the capabilities of a Printer and can be queried regarding
 * a printer's supported attributes.
 * <P>
 * Example:
 *   <PRE>{@code
 *   DocFlavor flavor = DocFlavor.INPUT_STREAM.POSTSCRIPT;
 *   PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
 *   aset.add(MediaSizeName.ISO_A4);
 *   PrintService[] pservices =
 *                 PrintServiceLookup.lookupPrintServices(flavor, aset);
 *   if (pservices.length > 0) {
 *       DocPrintJob pj = pservices[0].createPrintJob();
 *       try {
 *           FileInputStream fis = new FileInputStream("test.ps");
 *           Doc doc = new SimpleDoc(fis, flavor, null);
 *           pj.print(doc, aset);
 *        } catch (FileNotFoundException fe) {
 *        } catch (PrintException e) {
 *        }
 *   }
 *   }</PRE>
 */
public interface PrintService {

    /** Returns a String name for this print service which may be used
      * by applications to request a particular print service.
      * In a suitable context, such as a name service, this name must be
      * unique.
      * In some environments this unique name may be the same as the user
      * friendly printer name defined as the
      * {@link javax.print.attribute.standard.PrinterName PrinterName}
      * attribute.
      * @return name of the service.
      */
    public String getName();

    /**
     * Creates and returns a PrintJob capable of handling data from
     * any of the supported document flavors.
     * @return a DocPrintJob object
     */
    public DocPrintJob createPrintJob();

    /**
     * Registers a listener for events on this PrintService.
     * @param listener  a PrintServiceAttributeListener, which
     *        monitors the status of a print service
     * @see #removePrintServiceAttributeListener
     */
    public void addPrintServiceAttributeListener(
                                       PrintServiceAttributeListener listener);

    /**
     * Removes the print-service listener from this print service.
     * This means the listener is no longer interested in
     * <code>PrintService</code> events.
     * @param listener  a PrintServiceAttributeListener object
     * @see #addPrintServiceAttributeListener
     */
    public void removePrintServiceAttributeListener(
                                       PrintServiceAttributeListener listener);

    /**
     * Obtains this print service's set of printer description attributes
     * giving this Print Service's status. The returned attribute set object
     * is unmodifiable. The returned attribute set object is a "snapshot" of
     * this Print Service's attribute set at the time of the
     * <CODE>getAttributes()</CODE> method call: that is, the returned
     * attribute set's contents will <I>not</I> be updated if this print
     * service's attribute set's contents change in the future. To detect
     * changes in attribute values, call <CODE>getAttributes()</CODE> again
     * and compare the new attribute set to the previous attribute set;
     * alternatively, register a listener for print service events.
     *
     * @return  Unmodifiable snapshot of this Print Service's attribute set.
     *          May be empty, but not null.
     */
    public PrintServiceAttributeSet getAttributes();

    /**
     * Gets the value of the single specified service attribute.
     * This may be useful to clients which only need the value of one
     * attribute and want to minimise overhead.
     * @param category the category of a PrintServiceAttribute supported
     * by this service - may not be null.
     * @return the value of the supported attribute or null if the
     * attribute is not supported by this service.
     * @exception NullPointerException if the category is null.
     * @exception  IllegalArgumentException
     *     (unchecked exception) if <CODE>category</CODE> is not a
     *     <code>Class</code> that implements interface
     *{@link javax.print.attribute.PrintServiceAttribute PrintServiceAttribute}.
     */
    public <T extends PrintServiceAttribute>
        T getAttribute(Class<T> category);

    /**
     * Determines the print data formats a client can specify when setting
     * up a job for this <code>PrintService</code>. A print data format is
     * designated by a "doc
     * flavor" (class {@link javax.print.DocFlavor DocFlavor})
     * consisting of a MIME type plus a print data representation class.
     * <P>
     * Note that some doc flavors may not be supported in combination
     * with all attributes. Use <code>getUnsupportedAttributes(..)</code>
     * to validate specific combinations.
     *
     * @return  Array of supported doc flavors, should have at least
     *          one element.
     *
     */
    public DocFlavor[] getSupportedDocFlavors();

    /**
     * Determines if this print service supports a specific
     * <code>DocFlavor</code>.  This is a convenience method to determine
     * if the <code>DocFlavor</code> would be a member of the result of
     * <code>getSupportedDocFlavors()</code>.
     * <p>
     * Note that some doc flavors may not be supported in combination
     * with all attributes. Use <code>getUnsupportedAttributes(..)</code>
     * to validate specific combinations.
     *
     * @param flavor the <code>DocFlavor</code>to query for support.
     * @return  <code>true</code> if this print service supports the
     * specified <code>DocFlavor</code>; <code>false</code> otherwise.
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>flavor</CODE> is null.
     */
    public boolean isDocFlavorSupported(DocFlavor flavor);


    /**
     * Determines the printing attribute categories a client can specify
     * when setting up a job for this print service.
     * A printing attribute category is
     * designated by a <code>Class</code> that implements interface
     * {@link javax.print.attribute.Attribute Attribute}. This method returns
     * just the attribute <I>categories</I> that are supported; it does not
     * return the particular attribute <I>values</I> that are supported.
     * <P>
     * This method returns all the printing attribute
     * categories this print service supports for any possible job.
     * Some categories may not be supported in a particular context (ie
     * for a particular <code>DocFlavor</code>).
     * Use one of the methods that include a <code>DocFlavor</code> to
     * validate the request before submitting it, such as
     * <code>getSupportedAttributeValues(..)</code>.
     *
     * @return  Array of printing attribute categories that the client can
     *          specify as a doc-level or job-level attribute in a Print
     *          Request. Each element in the array is a {@link java.lang.Class
     *          Class} that implements interface {@link
     *          javax.print.attribute.Attribute Attribute}.
     *          The array is empty if no categories are supported.
     */
    public Class<?>[] getSupportedAttributeCategories();

    /**
     * Determines whether a client can specify the given printing
     * attribute category when setting up a job for this print service. A
     * printing attribute category is designated by a <code>Class</code>
     * that implements interface {@link javax.print.attribute.Attribute
     * Attribute}. This method tells whether the attribute <I>category</I> is
     * supported; it does not tell whether a particular attribute <I>value</I>
     * is supported.
     * <p>
     * Some categories may not be supported in a particular context (ie
     * for a particular <code>DocFlavor</code>).
     * Use one of the methods which include a <code>DocFlavor</code> to
     * validate the request before submitting it, such as
     * <code>getSupportedAttributeValues(..)</code>.
     * <P>
     * This is a convenience method to determine if the category
     * would be a member of the result of
     * <code>getSupportedAttributeCategories()</code>.
     *
     * @param  category    Printing attribute category to test. It must be a
     *                        <code>Class</code> that implements
     *                        interface
     *                {@link javax.print.attribute.Attribute Attribute}.
     *
     * @return  <code>true</code> if this print service supports
     *          specifying a doc-level or
     *          job-level attribute in <CODE>category</CODE> in a Print
     *          Request; <code>false</code> if it doesn't.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>category</CODE> is null.
     * @exception  IllegalArgumentException
     *     (unchecked exception) Thrown if <CODE>category</CODE> is not a
     *     <code>Class</code> that implements interface
     *     {@link javax.print.attribute.Attribute Attribute}.
     */
    public boolean
        isAttributeCategorySupported(Class<? extends Attribute> category);

    /**
     * Determines this print service's default printing attribute value in
     * the given category. A printing attribute value is an instance of
     * a class that implements interface
     * {@link javax.print.attribute.Attribute Attribute}. If a client sets
     * up a print job and does not specify any attribute value in the
     * given category, this Print Service will use the
     * default attribute value instead.
     * <p>
     * Some attributes may not be supported in a particular context (ie
     * for a particular <code>DocFlavor</code>).
     * Use one of the methods that include a <code>DocFlavor</code> to
     * validate the request before submitting it, such as
     * <code>getSupportedAttributeValues(..)</code>.
     * <P>
     * Not all attributes have a default value. For example the
     * service will not have a defaultvalue for <code>RequestingUser</code>
     * i.e. a null return for a supported category means there is no
     * service default value for that category. Use the
     * <code>isAttributeCategorySupported(Class)</code> method to
     * distinguish these cases.
     *
     * @param  category    Printing attribute category for which the default
     *                     attribute value is requested. It must be a {@link
     *                        java.lang.Class Class} that implements interface
     *                        {@link javax.print.attribute.Attribute
     *                        Attribute}.
     *
     * @return  Default attribute value for <CODE>category</CODE>, or null
     *       if this Print Service does not support specifying a doc-level or
     *          job-level attribute in <CODE>category</CODE> in a Print
     *          Request, or the service does not have a default value
     *          for this attribute.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>category</CODE> is null.
     * @exception  IllegalArgumentException
     *     (unchecked exception) Thrown if <CODE>category</CODE> is not a
     *     {@link java.lang.Class Class} that implements interface {@link
     *     javax.print.attribute.Attribute Attribute}.
     */
    public Object
        getDefaultAttributeValue(Class<? extends Attribute> category);

    /**
     * Determines the printing attribute values a client can specify in
     * the given category when setting up a job for this print service. A
     * printing
     * attribute value is an instance of a class that implements interface
     * {@link javax.print.attribute.Attribute Attribute}.
     * <P>
     * If <CODE>flavor</CODE> is null and <CODE>attributes</CODE> is null
     * or is an empty set, this method returns all the printing attribute
     * values this Print Service supports for any possible job. If
     * <CODE>flavor</CODE> is not null or <CODE>attributes</CODE> is not
     * an empty set, this method returns just the printing attribute values
     * that are compatible with the given doc flavor and/or set of attributes.
     * That is, a null return value may indicate that specifying this attribute
     * is incompatible with the specified DocFlavor.
     * Also if DocFlavor is not null it must be a flavor supported by this
     * PrintService, else IllegalArgumentException will be thrown.
     * <P>
     * If the <code>attributes</code> parameter contains an Attribute whose
     * category is the same as the <code>category</code> parameter, the service
     * must ignore this attribute in the AttributeSet.
     * <p>
     * <code>DocAttribute</code>s which are to be specified on the
     * <code>Doc</code> must be included in this set to accurately
     * represent the context.
     * <p>
     * This method returns an Object because different printing attribute
     * categories indicate the supported attribute values in different ways.
     * The documentation for each printing attribute in package {@link
     * javax.print.attribute.standard javax.print.attribute.standard}
     * describes how each attribute indicates its supported values. Possible
     * ways of indicating support include:
     * <UL>
     * <LI>
     * Return a single instance of the attribute category to indicate that any
     * value is legal -- used, for example, by an attribute whose value is an
     * arbitrary text string. (The value of the returned attribute object is
     * irrelevant.)
     * <LI>
     * Return an array of one or more instances of the attribute category,
     * containing the legal values -- used, for example, by an attribute with
     * a list of enumerated values. The type of the array is an array of the
     * specified attribute category type as returned by its
     * <code>getCategory(Class)</code>.
     * <LI>
     * Return a single object (of some class other than the attribute category)
     * that indicates bounds on the legal values -- used, for example, by an
     * integer-valued attribute that must lie within a certain range.
     * </UL>
     * <P>
     *
     * @param  category    Printing attribute category to test. It must be a
     *                        {@link java.lang.Class Class} that implements
     *                        interface {@link
     *                        javax.print.attribute.Attribute Attribute}.
     * @param  flavor      Doc flavor for a supposed job, or null.
     * @param  attributes  Set of printing attributes for a supposed job
     *                        (both job-level attributes and document-level
     *                        attributes), or null.
     *
     * @return  Object indicating supported values for <CODE>category</CODE>,
     *          or null if this Print Service does not support specifying a
     *          doc-level or job-level attribute in <CODE>category</CODE> in
     *          a Print Request.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>category</CODE> is null.
     * @exception  IllegalArgumentException
     *     (unchecked exception) Thrown if <CODE>category</CODE> is not a
     *     {@link java.lang.Class Class} that implements interface {@link
     *     javax.print.attribute.Attribute Attribute}, or
     *     <code>DocFlavor</code> is not supported by this service.
     */
    public Object
        getSupportedAttributeValues(Class<? extends Attribute> category,
                                    DocFlavor flavor,
                                    AttributeSet attributes);

    /**
     * Determines whether a client can specify the given printing
     * attribute
     * value when setting up a job for this Print Service. A printing
     * attribute value is an instance of a class that implements interface
     *  {@link javax.print.attribute.Attribute Attribute}.
     * <P>
     * If <CODE>flavor</CODE> is null and <CODE>attributes</CODE> is null or
     * is an empty set, this method tells whether this Print Service supports
     * the given printing attribute value for some possible combination of doc
     * flavor and set of attributes. If <CODE>flavor</CODE> is not null or
     * <CODE>attributes</CODE> is not an empty set, this method tells whether
     * this Print Service supports the given printing attribute value in
     * combination with the given doc flavor and/or set of attributes.
     * <p>
     * Also if DocFlavor is not null it must be a flavor supported by this
     * PrintService, else IllegalArgumentException will be thrown.
     * <p>
     * <code>DocAttribute</code>s which are to be specified on the
     * <code>Doc</code> must be included in this set to accurately
     * represent the context.
     * <p>
     * This is a convenience method to determine if the value
     * would be a member of the result of
     * <code>getSupportedAttributeValues(...)</code>.
     *
     * @param  attrval       Printing attribute value to test.
     * @param  flavor      Doc flavor for a supposed job, or null.
     * @param  attributes  Set of printing attributes for a supposed job
     *                        (both job-level attributes and document-level
     *                        attributes), or null.
     *
     * @return  True if this Print Service supports specifying
     *        <CODE>attrval</CODE> as a doc-level or job-level attribute in a
     *          Print Request, false if it doesn't.
     *
     * @exception  NullPointerException
     *     (unchecked exception)  if <CODE>attrval</CODE> is null.
     * @exception  IllegalArgumentException if flavor is not supported by
     *      this PrintService.
     */
    public boolean isAttributeValueSupported(Attribute attrval,
                                             DocFlavor flavor,
                                             AttributeSet attributes);


    /**
     * Identifies the attributes that are unsupported for a print request
     * in the context of a particular DocFlavor.
     * This method is useful for validating a potential print job and
     * identifying the specific attributes which cannot be supported.
     * It is important to supply only a supported DocFlavor or an
     * IllegalArgumentException will be thrown. If the
     * return value from this method is null, all attributes are supported.
     * <p>
     * <code>DocAttribute</code>s which are to be specified on the
     * <code>Doc</code> must be included in this set to accurately
     * represent the context.
     * <p>
     * If the return value is non-null, all attributes in the returned
     * set are unsupported with this DocFlavor. The returned set does not
     * distinguish attribute categories that are unsupported from
     * unsupported attribute values.
     * <p>
     * A supported print request can then be created by removing
     * all unsupported attributes from the original attribute set,
     * except in the case that the DocFlavor is unsupported.
     * <p>
     * If any attributes are unsupported only because they are in conflict
     * with other attributes then it is at the discretion of the service
     * to select the attribute(s) to be identified as the cause of the
     * conflict.
     * <p>
     * Use <code>isDocFlavorSupported()</code> to verify that a DocFlavor
     * is supported before calling this method.
     *
     * @param  flavor      Doc flavor to test, or null
     * @param  attributes  Set of printing attributes for a supposed job
     *                        (both job-level attributes and document-level
     *                        attributes), or null.
     *
     * @return  null if this Print Service supports the print request
     * specification, else the unsupported attributes.
     *
     * @exception IllegalArgumentException if<CODE>flavor</CODE> is
     *             not supported by this PrintService.
     */
    public AttributeSet getUnsupportedAttributes(DocFlavor flavor,
                                           AttributeSet attributes);

    /**
     * Returns a factory for UI components which allow users to interact
     * with the service in various roles.
     * Services which do not provide any UI should return null.
     * Print Services which do provide UI but want to be supported in
     * an environment with no UI support should ensure that the factory
     * is not initialised unless the application calls this method to
     * obtain the factory.
     * See <code>ServiceUIFactory</code> for more information.
     * @return null or a factory for UI components.
     */
    public ServiceUIFactory getServiceUIFactory();

    /**
     * Determines if two services are referring to the same underlying
     * service.  Objects encapsulating a print service may not exhibit
     * equality of reference even though they refer to the same underlying
     * service.
     * <p>
     * Clients should call this method to determine if two services are
     * referring to the same underlying service.
     * <p>
     * Services must implement this method and return true only if the
     * service objects being compared may be used interchangeably by the
     * client.
     * Services are free to return the same object reference to an underlying
     * service if that, but clients must not depend on equality of reference.
     * @param obj the reference object with which to compare.
     * @return true if this service is the same as the obj argument,
     * false otherwise.
     */
    public boolean equals(Object obj);

    /**
     * This method should be implemented consistently with
     * <code>equals(Object)</code>.
     * @return hash code of this object.
     */
    public int hashCode();

}
