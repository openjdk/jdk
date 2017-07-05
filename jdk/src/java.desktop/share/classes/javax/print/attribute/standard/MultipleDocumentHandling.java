/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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
package javax.print.attribute.standard;

import javax.print.attribute.Attribute;
import javax.print.attribute.EnumSyntax;
import javax.print.attribute.PrintRequestAttribute;
import javax.print.attribute.PrintJobAttribute;

/**
 * Class MultipleDocumentHandling is a printing attribute class, an enumeration,
 * that controls finishing operations and the placement of one or more
 * print-stream pages into impressions and onto media sheets. When the value of
 * the {@link Copies Copies} attribute exceeds 1, MultipleDocumentHandling also
 * controls the order in which the copies that result from processing the
 * documents are produced. This attribute is relevant only for a multidoc print
 * job consisting of two or more individual docs.
 * <P>
 * Briefly, MultipleDocumentHandling determines the relationship between the
 * multiple input (electronic) documents fed into a multidoc print job and the
 * output (physical) document or documents produced by the multidoc print job.
 * There are two possibilities:
 * <UL>
 * <LI>
 * The multiple input documents are combined into a single output document.
 * Finishing operations ({@link Finishings Finishings}),
 * are performed on this single output
 * document. The {@link Copies Copies} attribute tells how many copies of this
 * single output document to produce. The MultipleDocumentHandling values
 * SINGLE_DOCUMENT and SINGLE_DOCUMENT_NEW_SHEET specify two variations of
 * this  possibility.
 *
 * <LI>
 * The multiple input documents remain separate output documents. Finishing
 * operations ({@link Finishings Finishings}),
 * are performed on each output document
 * separately. The {@link Copies Copies} attribute tells how many copies of each
 * separate output document to produce. The MultipleDocumentHandling values
 * SEPARATE_DOCUMENTS_UNCOLLATED_COPIES and SEPARATE_DOCUMENTS_COLLATED_COPIES
 * specify two variations of this possibility.
 * </UL>
 * <P>
 * In the detailed explanations below, if "{@code a}" represents an
 * instance of document data, then the result of processing the data in
 * document "{@code a}" is a sequence of media sheets represented by
 * "{@code a(*)}".
 * <P>
 * The standard MultipleDocumentHandling values are:
 * <UL>
 * <LI>
 * <a NAME="sdfi"></a>{@link #SINGLE_DOCUMENT
 * <B>SINGLE_DOCUMENT</B>}. If a print job has multiple
 * documents -- say, the document data is called {@code a} and
 * {@code b} -- then the result of processing all the document data
 * ({@code a} and then {@code b}) must be treated as a single sequence
 * of media sheets for finishing operations; that is, finishing would be
 * performed on the concatenation of the sequences {@code a(*),b(*)}. The
 * printer must not force the data in each document instance to be formatted
 * onto a new print-stream page, nor to start a new impression on a new media
 * sheet. If more than one copy is made, the ordering of the sets of media
 * sheets resulting from processing the document data must be
 * {@code a(*),b(*),a(*),b(*),...}, and the printer object must force
 * each copy ({@code a(*),b(*)}) to start on a new media sheet.
 *
 * <LI>
 * <a NAME="sducfi"></a>{@link #SEPARATE_DOCUMENTS_UNCOLLATED_COPIES
 * <B>SEPARATE_DOCUMENTS_UNCOLLATED_COPIES</B>}. If a print job
 * has multiple documents -- say, the document data is called {@code a} and
 * {@code b} -- then the result of processing the data in each document
 * instance must be treated as a single sequence of media sheets for finishing
 * operations; that is, the sets {@code a(*)} and {@code b(*)} would
 * each be finished separately. The printer must force each copy of the result
 * of processing the data in a single document to start on a new media sheet.
 * If more than one copy is made, the ordering of the sets of media sheets
 * resulting from processing the document data must be
 * {@code a(*),a(*),...,b(*),b(*)...}.
 *
 * <LI>
 * <a NAME="sdccfi"></a>{@link #SEPARATE_DOCUMENTS_COLLATED_COPIES
 * <B>SEPARATE_DOCUMENTS_COLLATED_COPIES</B>}. If a print job
 * has multiple documents -- say, the document data is called {@code a} and
 * {@code b} -- then the result of processing the data in each document
 * instance must be treated as a single sequence of media sheets for finishing
 * operations; that is, the sets {@code a(*)} and {@code b(*)} would
 * each be finished separately. The printer must force each copy of the result
 * of processing the data in a single document to start on a new media sheet.
 * If more than one copy is made, the ordering of the sets of media sheets
 * resulting from processing the document data must be
 * {@code a(*),b(*),a(*),b(*),...}.
 *
 * <LI>
 * <a NAME="sdnsfi"></a>{@link #SINGLE_DOCUMENT_NEW_SHEET
 * <B>SINGLE_DOCUMENT_NEW_SHEET</B>}. Same as SINGLE_DOCUMENT,
 * except that the printer must ensure that the first impression of each
 * document instance in the job is placed on a new media sheet. This value
 * allows multiple documents to be stapled together with a single staple where
 * each document starts on a new sheet.
 * </UL>
 * <P>
 * SINGLE_DOCUMENT is the same as SEPARATE_DOCUMENTS_COLLATED_COPIES with
 * respect to ordering of print-stream pages, but not media sheet generation,
 * since SINGLE_DOCUMENT will put the first page of the next document on the
 * back side of a sheet if an odd number of pages have been produced so far
 * for the job, while SEPARATE_DOCUMENTS_COLLATED_COPIES always forces the
 * next document or document copy on to a new sheet.
 * <P>
 * In addition, if a {@link Finishings Finishings} attribute of
 * {@link Finishings#STAPLE STAPLE} is specified, then:
 * <UL>
 * <LI>
 * With SINGLE_DOCUMENT, documents {@code a} and {@code b} are
 * stapled together as a single document with no regard to new sheets.
 *
 * <LI>
 * With SINGLE_DOCUMENT_NEW_SHEET, documents {@code a} and {@code b}
 * are stapled together as a single document, but document {@code b}
 * starts on a new sheet.
 *
 * <LI>
 * With SEPARATE_DOCUMENTS_UNCOLLATED_COPIES and
 * SEPARATE_DOCUMENTS_COLLATED_COPIES, documents {@code a} and
 * {@code b} are stapled separately.
 * </UL>
 * <P>
 * <I>Note:</I> None of these values provide means to produce uncollated
 * sheets within a document, i.e., where multiple copies of sheet <I>n</I>
 * are produced before sheet <I>n</I>+1 of the same document.
 * To specify that, see the {@link SheetCollate SheetCollate} attribute.
 * <P>
 * <B>IPP Compatibility:</B> The category name returned by
 * {@code getName()} is the IPP attribute name.  The enumeration's
 * integer value is the IPP enum value.  The {@code toString()} method
 * returns the IPP string representation of the attribute value.
 *
 * @see  Copies
 * @see  Finishings
 * @see  NumberUp
 * @see  PageRanges
 * @see  SheetCollate
 * @see  Sides
 *
 * @author  David Mendenhall
 * @author  Alan Kaminsky
 */
public class MultipleDocumentHandling extends EnumSyntax
    implements PrintRequestAttribute, PrintJobAttribute {

    private static final long serialVersionUID = 8098326460746413466L;


    /**
     * Single document -- see above for <A HREF="#sdfi">further
     * information</A>.
     */
    public static final MultipleDocumentHandling
        SINGLE_DOCUMENT = new MultipleDocumentHandling (0);

    /**
     * Separate documents uncollated copies -- see above for
     * <A HREF="#sducfi">further information</A>.
     */
    public static final MultipleDocumentHandling
       SEPARATE_DOCUMENTS_UNCOLLATED_COPIES = new MultipleDocumentHandling (1);

    /**
     * Separate documents collated copies -- see above for
     * <A HREF="#sdccfi">further information</A>.
     */
    public static final MultipleDocumentHandling
        SEPARATE_DOCUMENTS_COLLATED_COPIES = new MultipleDocumentHandling (2);

    /**
     * Single document new sheet -- see above for
     * <A HREF="#sdnsfi">further information</A>.
     */
    public static final MultipleDocumentHandling
        SINGLE_DOCUMENT_NEW_SHEET = new MultipleDocumentHandling (3);


    /**
     * Construct a new multiple document handling enumeration value with the
     * given integer value.
     *
     * @param  value  Integer value.
     */
    protected MultipleDocumentHandling(int value) {
        super (value);
    }

    private static final String[] myStringTable = {
        "single-document",
        "separate-documents-uncollated-copies",
        "separate-documents-collated-copies",
        "single-document-new-sheet"
    };

    private static final MultipleDocumentHandling[] myEnumValueTable = {
        SINGLE_DOCUMENT,
        SEPARATE_DOCUMENTS_UNCOLLATED_COPIES,
        SEPARATE_DOCUMENTS_COLLATED_COPIES,
        SINGLE_DOCUMENT_NEW_SHEET
    };

    /**
     * Returns the string table for class MultipleDocumentHandling.
     */
    protected String[] getStringTable() {
        return myStringTable.clone();
    }

    /**
     * Returns the enumeration value table for class MultipleDocumentHandling.
     */
    protected EnumSyntax[] getEnumValueTable() {
        return (EnumSyntax[])myEnumValueTable.clone();
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <P>
     * For class MultipleDocumentHandling and any vendor-defined subclasses,
     * the category is class MultipleDocumentHandling itself.
     *
     * @return  Printing attribute class (category), an instance of class
     *          {@link java.lang.Class java.lang.Class}.
     */
    public final Class<? extends Attribute> getCategory() {
        return MultipleDocumentHandling.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <P>
     * For class MultipleDocumentHandling and any vendor-defined subclasses,
     * the category name is {@code "multiple-document-handling"}.
     *
     * @return  Attribute category name.
     */
    public final String getName() {
        return "multiple-document-handling";
    }

}
