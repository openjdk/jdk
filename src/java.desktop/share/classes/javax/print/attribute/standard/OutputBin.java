/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, BELLSOFT. All rights reserved.
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

import java.io.Serial;

import javax.print.attribute.Attribute;
import javax.print.attribute.DocAttribute;
import javax.print.attribute.EnumSyntax;
import javax.print.attribute.PrintJobAttribute;
import javax.print.attribute.PrintRequestAttribute;

import sun.print.CustomOutputBin;

/**
 * Class {@code OutputBin} is a printing attribute class, an enumeration, that
 * specifies the output bin for the job.
 * <p>
 * Class {@code OutputBin} declares keywords for standard output bin kind values.
 * <p>
 * <b>IPP Compatibility:</b> This attribute is not an IPP 1.1 attribute; it is
 * an attribute in the "output-bin" attribute extension
 * (<a href="https://ftp.pwg.org/pub/pwg/candidates/cs-ippoutputbin10-20010207-5100.2.pdf">
 * PDF</a>) of IPP 1.1. The category name returned by {@code getName()} is the
 * IPP attribute name. The enumeration's integer value is the IPP enum value.
 * The {@code toString()} method returns the IPP string representation of the
 * attribute value.
 */
public sealed class OutputBin extends EnumSyntax implements PrintRequestAttribute, PrintJobAttribute permits CustomOutputBin {

    @Serial
    private static final long serialVersionUID = -3718893309873137109L;

    /**
     * The top output bin in the printer.
     */
    public static final OutputBin TOP = new OutputBin(0);

    /**
     * The middle output bin in the printer.
     */
    public static final OutputBin MIDDLE = new OutputBin(1);

    /**
     * The bottom output bin in the printer.
     */
    public static final OutputBin BOTTOM = new OutputBin(2);

    /**
     * The side output bin in the printer.
     */
    public static final OutputBin SIDE = new OutputBin(3);

    /**
     * The left output bin in the printer.
     */
    public static final OutputBin LEFT = new OutputBin(4);

    /**
     * The right output bin in the printer.
     */
    public static final OutputBin RIGHT = new OutputBin(5);

    /**
     * The center output bin in the printer.
     */
    public static final OutputBin CENTER = new OutputBin(6);

    /**
     * The rear output bin in the printer.
     */
    public static final OutputBin REAR = new OutputBin(7);

    /**
     * The face up output bin in the printer.
     */
    public static final OutputBin FACE_UP = new OutputBin(8);

    /**
     * The face down output bin in the printer.
     */
    public static final OutputBin FACE_DOWN = new OutputBin(9);

    /**
     * The large-capacity output bin in the printer.
     */
    public static final OutputBin LARGE_CAPACITY = new OutputBin(10);

    /**
     * Construct a new output bin enumeration value with the given integer
     * value.
     *
     * @param value Integer value
     */
    protected OutputBin(int value) {
        super(value);
    }

    /**
     * The string table for class {@code OutputBin}.
     */
    private static final String[] myStringTable = {
            "top",
            "middle",
            "bottom",
            "side",
            "left",
            "right",
            "center",
            "rear",
            "face-up",
            "face-down",
            "large-capacity",
    };

    /**
     * The enumeration value table for class {@code OutputBin}.
     */
    private static final OutputBin[] myEnumValueTable = {
            TOP,
            MIDDLE,
            BOTTOM,
            SIDE,
            LEFT,
            RIGHT,
            CENTER,
            REAR,
            FACE_UP,
            FACE_DOWN,
            LARGE_CAPACITY,
    };

    /**
     * Returns the string table for class {@code OutputBin}.
     */
    @Override
    protected String[] getStringTable() {
        return myStringTable.clone();
    }

    /**
     * Returns the enumeration value table for class {@code OutputBin}.
     */
    @Override
    protected EnumSyntax[] getEnumValueTable() {
        return (EnumSyntax[]) myEnumValueTable.clone();
    }

    /**
     * Get the printing attribute class which is to be used as the "category"
     * for this printing attribute value.
     * <p>
     * For class {@code OutputBin} and any vendor-defined subclasses, the category
     * is class {@code OutputBin} itself.
     *
     * @return printing attribute class (category), an instance of class
     *         {@link Class java.lang.Class}
     */
    @Override
    public final Class<? extends Attribute> getCategory() {
        return OutputBin.class;
    }

    /**
     * Get the name of the category of which this attribute value is an
     * instance.
     * <p>
     * For class {@code OutputBin} and any vendor-defined subclasses, the category
     * name is {@code "output-bin"}.
     *
     * @return attribute category name
     */
    @Override
    public final String getName() {
        return "output-bin";
    }
}
