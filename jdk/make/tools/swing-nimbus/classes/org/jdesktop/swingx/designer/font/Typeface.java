/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
package org.jdesktop.swingx.designer.font;

import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.UIDefaults;
import org.jdesktop.beans.AbstractBean;
import org.jdesktop.swingx.designer.utils.HasUIDefaults;
import org.jibx.runtime.IUnmarshallingContext;

/**
 * I don't think the name is technically correct (ie: a typeface is not a font),
 * but I wanted something besides "font" so, here it is.
 *
 * This is a mutable font, much like Matte is a mutable color. Also like Matte,
 * Typeface can be derived.
 *
 * @author rbair
 */
public class Typeface extends AbstractBean {
    //specifies whether to derive bold, or italic.
    //Default means, get my value from my parent.
    //Off means, leave bold/italic off.
    //On means, make bold/italic on.
    public enum DeriveStyle { Default, Off, On }

    private String uiDefaultParentName;
    /** This is a local UIDefaults that contains all the UIDefaults in the Model. */
    private transient UIDefaults uiDefaults = new UIDefaults();
    private PropertyChangeListener uiDefaultsChangeListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if (uiDefaultParentName != null && uiDefaultParentName.equals(evt.getPropertyName())) {
                updateFontFromOffsets();
            }
        }
    };

    /**
     * The name of the font. If uiDefaultParentName is specified, then this name
     * will be set to be equal to the name of the parent font.
     */
    private String name;
    /**
     * The size of the font. If uiDefaultParentName is set, then this value is
     * updated to reflect the size of the parent font * the sizeOffset.
     */
    private int size;

    //this field is not publically accessible. Rather, it is updated based on
    //"bold" and "italic" as necessary.
    private int style = Font.PLAIN;
    private DeriveStyle bold = DeriveStyle.Default;
    private DeriveStyle italic = DeriveStyle.Default;

    /**
     * The size offset. Only used if uiDefaultParentName is specified. This offset
     * will be multiplied with the parent font's size to determine the size of this
     * typeface. The offset is specified as a percentage, either positive or negative.
     *
     * The reason a percentage was used, was so that things would look correctly
     * when scaled, such as with high DPI situations.
     */
    private float sizeOffset;

    /**
     * Create a new Typeface. Note that, without specifying the uiDefaults,
     * you cannot have font derivation. Thus, this constructor should never
     * be called, except for the XML binding stuff.
     */
    public Typeface() { }

    /**
     * Creates a new Typeface.
     *
     * @param f The font from which to get the font name, size, and style to use
     * to initialize this typeface. Note that this font is not used as a parent
     * font for derivation purposes. Rather, it is used as a source from which to
     * copy initial settings.
     *
     * @param uiDefaults The uiDefaults to use for font derivation purposes.
     * When the uiDefaultParentName is specified, then this Typeface will inspect
     * the given UIDefaults for that parent <em>font</em>. Note that the UIDefaults
     * should be populated with a font, and not with a typeface.
     */
    public Typeface(Font f, UIDefaults uiDefaults) {
        if (f != null) {
            this.name = f.getName();
            this.size = f.getSize();
            this.style = f.getStyle();
        }
        setUiDefaults(uiDefaults);
    }

    // =================================================================================================================
    // JIBX Methods

    /**
     * Called by JIBX after all fields have been set
     *
     * @param context The JIBX Unmarshalling Context
     */
    protected void postSet(IUnmarshallingContext context) {
        // walk up till we get synth model
        for (int i = 0; i < context.getStackDepth(); i++) {
            if (context.getStackObject(i) instanceof HasUIDefaults) {
                UIDefaults uiDefaults = ((HasUIDefaults) context.getStackObject(i)).getUiDefaults();
                if (uiDefaults != null) {
                    setUiDefaults(uiDefaults);
                    break;
                }
            }
        }
    }

    // =================================================================================================================
    // Typeface methods

    /**
     * Is the Typeface an absolute Font not derived from a parent ui default
     *
     * @return <code>true</code> if this is a absolute not uidefault derived font
     */
    public boolean isAbsolute() {
        return uiDefaultParentName == null;
    }

    /**
     * Set all properties of this Typeface to be the same as <code>src</code> and fire all the change events
     *
     * @param src the Typeface to copy properties from
     */
    public void copy(Typeface src) {
        // keep old values
        Font oldFont = getFont();
        String oldParentName = uiDefaultParentName;
        String oldName = name;
        int oldSize = size;
        float oldSizeOffset = sizeOffset;
        DeriveStyle oldBold = bold, oldItalic = italic;

        style = src.style;

        //Note, I don't just call the setters here, because I want to make
        //sure the "font" PCE is only fired once, at the end.
        name = src.name;
        firePropertyChange("name", oldName, name);
        size = src.size;
        firePropertyChange("size", oldSize, size);
        bold = src.bold;
        firePropertyChange("bold", oldBold, bold);
        italic = src.italic;
        firePropertyChange("italic", oldItalic, italic);
        sizeOffset = src.sizeOffset;
        firePropertyChange("sizeOffset", oldSizeOffset, sizeOffset);
        uiDefaultParentName = src.uiDefaultParentName;
        firePropertyChange("uiDefaultParentName", oldParentName, uiDefaultParentName);
        setUiDefaults(src.uiDefaults);
        firePropertyChange("font", oldFont, getFont());
    }

    // =================================================================================================================
    // Bean Methods

    /**
     * Get the local UIDefaults that contains all the UIDefaults in the Model.
     *
     * @return The UIDefaults for the model that contains this Typeface, can be null if this Typeface is not part of a bigger
     *         model
     */
    public UIDefaults getUiDefaults() {
        return uiDefaults;
    }

    /**
     * Set the local UIDefaults that contains all the UIDefaults in the Model.
     *
     * @param uiDefaults The UIDefaults for the model that contains this Typeface, can be null if this Typeface is not part of
     *                   a bigger model
     */
    public void setUiDefaults(UIDefaults uiDefaults) {
        if (uiDefaults != this.uiDefaults) {
            UIDefaults old = getUiDefaults();
            if (old != null) old.removePropertyChangeListener(uiDefaultsChangeListener);
            this.uiDefaults = uiDefaults;
            if (uiDefaults != null) this.uiDefaults.addPropertyChangeListener(uiDefaultsChangeListener);
            firePropertyChange("uiDefaults", old, getUiDefaults());
        }
    }

    /**
     * Get the name if the uidefault font that is the parent that this Typeface is derived from. If null then this is a
     * absolute font.
     *
     * @return Parent font ui default name
     */
    public String getUiDefaultParentName() {
        return uiDefaultParentName;
    }

    /**
     * Set the name if the uidefault font that is the parent that this Typeface is derived from. If null then this is a
     * absolute font.
     *
     * @param uiDefaultParentName Parent font ui default name
     */
    public void setUiDefaultParentName(String uiDefaultParentName) {
        String old = getUiDefaultParentName();
        this.uiDefaultParentName = uiDefaultParentName;
        firePropertyChange("uiDefaultParentName", old, getUiDefaultParentName());
        if (isAbsolute()) {
            // reset offsets
            float oldSizeOffset = sizeOffset;
            sizeOffset = 0;
            firePropertyChange("sizeOffset", oldSizeOffset, sizeOffset);
        } else {
            updateFontFromOffsets();
        }
    }

    /**
     * @return Gets the name of the font
     */
    public final String getName() {
        return name;
    }

    /**
     * Sets the name of the font. This method call <em>only</em> works if
     * <code>isAbsolute</code> returns true. Otherwise, it is ignored.
     * @param name the name of the font
     */
    public void setName(String name) {
        if (isAbsolute()) {
            String old = this.name;
            Font oldF = getFont();
            this.name = name;
            firePropertyChange("name", old, this.name);
            firePropertyChange("font", oldF, getFont());
        }
    }

    /**
     * @return gets the size of the font.
     */
    public final int getSize() {
        return size;
    }

    /**
     * <p>Sets the size of the font. THis method call will work whether
     * <code>isAbsolute</code> returns true or false. If this is an absolute
     * typeface, then the size is set directly. Otherwise, if this is a
     * derived typeface, then the sizeOffset will be updated to reflect the
     * proper offset based on this size, and the size of the parent font.</p>
     *
     * <p>For example, if the parent font's size was 12, and the sizeOffset was
     * -2 (thus yielding as size on this typeface of 10), and you call setSize
     * passing in "14" as the size, then the sizeOffset will be updated to be
     * equal to "2".</p>
     *
     * @param size the new size for this typeface.
     */
    public void setSize(int size) {
        int old = this.size;
        Font oldF = getFont();
        this.size = size;
        firePropertyChange("size", old, this.size);
        firePropertyChange("font", oldF, getFont());
        updateOffsetsFromFont();
    }

    /**
     * @return the size offset
     */
    public final float getSizeOffset() {
        return sizeOffset;
    }

    /**
     * Sets the percentage by which the size of this font should be different
     * from its parent font. This property is kept in synch with the size property.
     *
     * @param sizeOffset the size offset. May be any float. The value "1" means,
     * 100%. -1 means "-100%". 2 means "200%", and so on.
     */
    public void setSizeOffset(float sizeOffset) {
        float old = this.sizeOffset;
        Font oldF = getFont();
        this.sizeOffset = sizeOffset;
        firePropertyChange("sizeOffset", old, this.sizeOffset);
        firePropertyChange("font", oldF, getFont());
        updateFontFromOffsets();
    }

    public DeriveStyle getBold() {
        return bold;
    }

    public void setBold(DeriveStyle bold) {
        DeriveStyle old = this.bold;
        this.bold = bold == null ? DeriveStyle.Default : bold;
        firePropertyChange("bold", old, this.bold);
        updateFontFromOffsets();
    }

    public DeriveStyle getItalic() {
        return italic;
    }

    public void setItalic(DeriveStyle italic) {
        DeriveStyle old = this.italic;
        this.italic = italic == null ? DeriveStyle.Default : italic;
        firePropertyChange("italic", old, this.italic);
        updateFontFromOffsets();
    }

    /**
     * @return whether or not the font represented by this typeface is supported
     * on this operating system platform.
     */
    public boolean isFontSupported() {
        return true;//Font.getFont(name) != null;
    }

    /**
     * @return Gets the font associated with this Typeface. If font derivation is
     * being used, then the Font returned is the result of that derivation.
     */
    public Font getFont() {
        return new Font(name, style, size);
    }

    /**
     * Sets the font from which this Typeface should extract the font name, style,
     * and size. If font derivation is being used, then the font name will be ignored,
     * the style will be used (and always override the parent font), and the size
     * will be set and the sizeOffset updated appropriately.
     *
     * @param f the Font
     */
    public void setFont(Font f) {
        Font oldFont = getFont();
        String oldName = name;
        int oldSize = size;
        DeriveStyle oldBold = bold, oldItalic = italic;
        name = f.getName();
        size = f.getSize();
        style = f.getStyle();
        updateOffsetsFromFont();
        firePropertyChange("name", oldName, name);
        firePropertyChange("size", oldSize, size);
        firePropertyChange("bold", oldBold, bold);
        firePropertyChange("italic", oldItalic, italic);
        firePropertyChange("font", oldFont, getFont());
    }

    /**
     * @inheritDoc
     *
     * @return A formatted string representing this Typeface. This String should
     * not be considered public API, as it may change in a future release.
     */
    @Override public String toString() {
        Font f = getFont();
        String  strStyle;
        if (f.isBold()) {
            strStyle = f.isItalic() ? "bolditalic" : "bold";
        } else {
            strStyle = f.isItalic() ? "italic" : "plain";
        }

        if (isAbsolute()) {
            return Typeface.class.getName() + "[name=" + name + ", size=" + size + ", style=" + strStyle + "]";
        } else {
            return Typeface.class.getName() + "[base=" + uiDefaultParentName +
                    ", name=" + name + ", size=" + size + "(offset " + sizeOffset + ")" +
                    ", style=" + strStyle + "]";
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Typeface typeface = (Typeface) o;
        if (!typeface.name.equals(name)) return false;
        if (size != typeface.size) return false;
        if (bold != typeface.bold) return false;
        if (italic != typeface.italic) return false;
        if (sizeOffset != typeface.sizeOffset) return false;
        if (uiDefaultParentName != null ? !uiDefaultParentName.equals(typeface.uiDefaultParentName) :
                typeface.uiDefaultParentName != null) return false;
        return true;
    }

    @Override public int hashCode() {
        int result;
        result = name.hashCode();
        result = 31 * result + size;
        result = 31 * result + bold.ordinal();
        result = 31 * result + italic.ordinal();
        result = 31 * result + (int)(sizeOffset*100);
        result = 31 * result + (uiDefaultParentName != null ? uiDefaultParentName.hashCode() : 0);
        return result;
    }

    @Override public Typeface clone() {
        Typeface clone = new Typeface();
        clone.name = name;
        clone.size = size;
        clone.style = style;
        clone.bold = bold;
        clone.italic = italic;
        clone.sizeOffset = sizeOffset;
        clone.uiDefaultParentName = uiDefaultParentName;
        clone.setUiDefaults(uiDefaults);
        return clone;
    }

    // =================================================================================================================
    // Private Helper Methods

    private void updateOffsetsFromFont() {
        if (!isAbsolute()) {
            float oldSizeOffset = sizeOffset;
            Font parentFont = uiDefaults.getFont(uiDefaultParentName);
            if (parentFont != null) {
                float s = size;
                float p = parentFont.getSize();
                sizeOffset = (s/p) - 1f;
                firePropertyChange("sizeOffset", oldSizeOffset, sizeOffset);
            }
        }
    }

    private void updateFontFromOffsets() {
        if (!isAbsolute()) {
            Font oldFont = getFont();
            // get parent font data
            Font parentFont = uiDefaults.getFont(uiDefaultParentName);
            if (parentFont != null) {
                String oldName = name;
                int oldSize = size;

                name = parentFont.getName();
                size = Math.round(parentFont.getSize() * (1f + sizeOffset));

                boolean isBold = (bold == DeriveStyle.Default && parentFont.isBold()) || bold == DeriveStyle.On;
                boolean isItalic = (italic == DeriveStyle.Default && parentFont.isItalic()) || italic == DeriveStyle.On;
                style = Font.PLAIN;
                if (isBold) style = style | Font.BOLD;
                if (isItalic) style = style | Font.ITALIC;

                // update fire events
                firePropertyChange("name", oldName, name);
                firePropertyChange("size", oldSize, size);
                firePropertyChange("font", oldFont, getFont());
            }
        }
    }
}
