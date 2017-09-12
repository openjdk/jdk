/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import javax.print.*;
import javax.print.attribute.*;
import javax.print.event.*;

public class Applet1PrintService implements PrintService {


   public Applet1PrintService() {
   }

    public String getName() {
        return "Applet 1 Printer";
    }

    public DocPrintJob createPrintJob() {
        return null;
    }

    public PrintServiceAttributeSet getUpdatedAttributes() {
        return null;
    }

    public void addPrintServiceAttributeListener(
                                 PrintServiceAttributeListener listener) {
          return;
    }

    public void removePrintServiceAttributeListener(
                                  PrintServiceAttributeListener listener) {
        return;
    }

    public PrintServiceAttribute getAttribute(Class category) {
            return null;
    }

    public PrintServiceAttributeSet getAttributes() {
        return null;
    }

    public DocFlavor[] getSupportedDocFlavors() {
        return null;
    }

    public boolean isDocFlavorSupported(DocFlavor flavor) {
        return false;
    }

    public Class[] getSupportedAttributeCategories() {
        return null;
    }

    public boolean isAttributeCategorySupported(Class category) {
        return false;
    }

    public Object getDefaultAttributeValue(Class category) {
        return null;
    }

    public Object getSupportedAttributeValues(Class category,
                                              DocFlavor flavor,
                                              AttributeSet attributes) {
            return null;
    }

    public boolean isAttributeValueSupported(Attribute attr,
                                             DocFlavor flavor,
                                             AttributeSet attributes) {
        return false;
    }

    public AttributeSet getUnsupportedAttributes(DocFlavor flavor,
                                                 AttributeSet attributes) {

            return null;
        }
    public ServiceUIFactory getServiceUIFactory() {
        return null;
    }

    public String toString() {
        return "Printer : " + getName();
    }

    public boolean equals(Object obj) {
        return  (obj == this ||
                 (obj instanceof Applet1PrintService &&
                  ((Applet1PrintService)obj).getName().equals(getName())));
    }

    public int hashCode() {
        return this.getClass().hashCode()+getName().hashCode();
    }

}
