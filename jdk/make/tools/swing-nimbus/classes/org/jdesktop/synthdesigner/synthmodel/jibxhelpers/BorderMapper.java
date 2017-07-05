/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package org.jdesktop.synthdesigner.synthmodel.jibxhelpers;

import org.jibx.runtime.IMarshaller;
import org.jibx.runtime.IUnmarshaller;
import org.jibx.runtime.IAliasable;
import org.jibx.runtime.IUnmarshallingContext;
import org.jibx.runtime.JiBXException;
import org.jibx.runtime.IMarshallingContext;
import org.jibx.runtime.impl.MarshallingContext;
import org.jibx.runtime.impl.UnmarshallingContext;
import org.jdesktop.swingx.designer.jibxhelpers.ColorMapper;

import javax.swing.border.LineBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.BevelBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.Border;
import javax.swing.BorderFactory;
import java.awt.Insets;
import java.awt.Color;
import org.jdesktop.synthdesigner.synthmodel.PainterBorder;

/**
 * BorderMapper - JIBX xml mapper for swing standard borders
 *
 * @author Jasper Potts
 */
public class BorderMapper implements IMarshaller, IUnmarshaller, IAliasable {
    private static enum BorderType {
        empty, line, etched, bevel, matte, compound, painter
    }
    private static enum SubType {
        raised(EtchedBorder.RAISED), lowered(EtchedBorder.LOWERED);
        private int subtype;

        SubType(int type) {
            this.subtype = type;
        }

        public int getSubType() {
            return subtype;
        }
    }
    private static final String ELEMENT_NAME = "border";
    private static final String TYPE_NAME = "type";
    private static final String SUB_TYPE_NAME = "subtype";
    private static final String TOP_NAME = "top";
    private static final String BOTTOM_NAME = "bottom";
    private static final String LEFT_NAME = "left";
    private static final String RIGHT_NAME = "right";
    private static final String THICKNESS_NAME = "thickness";
    private static final String INSIDE_NAME = "inside";
    private static final String OUTSIDE_NAME = "outside";
    private static final String PAINTER_NAME = "painter";

    private String uri;
    private int index;
    private String name;

    public BorderMapper() {
        uri = null;
        index = 0;
        name = ELEMENT_NAME;
    }

    public BorderMapper(String uri, int index, String name) {
        this.uri = uri;
        this.index = index;
        this.name = name;
    }

    public boolean isExtension(int i) {
        return false;
    }

    public boolean isPresent(IUnmarshallingContext iUnmarshallingContext) throws
            JiBXException {
        return iUnmarshallingContext.isAt(uri, ELEMENT_NAME);
    }

    public void marshal(Object object, IMarshallingContext iMarshallingContext)
            throws JiBXException {
        if (!(iMarshallingContext instanceof MarshallingContext)) {
            throw new JiBXException("Invalid object type for marshaller");
        } else {
            MarshallingContext ctx = (MarshallingContext) iMarshallingContext;
            if (object instanceof PainterBorder) {
                PainterBorder border = (PainterBorder) object;
                Insets insets = border.getBorderInsets();
                ctx.startTagAttributes(index, name)
                        .attribute(index, TYPE_NAME, BorderType.painter.toString())
                        .attribute(index, PAINTER_NAME, border.getPainterName())
                        .attribute(index, TOP_NAME, insets.top)
                        .attribute(index, BOTTOM_NAME, insets.bottom)
                        .attribute(index, LEFT_NAME, insets.left)
                        .attribute(index, RIGHT_NAME, insets.right)
                        .closeStartContent();
                ctx.endTag(index, name);
            } else if (object instanceof EmptyBorder) {
                Insets insets = ((EmptyBorder) object).getBorderInsets();
                ctx.startTagAttributes(index, name)
                        .attribute(index, TYPE_NAME,
                                BorderType.empty.toString())
                        .attribute(index, TOP_NAME, insets.top)
                        .attribute(index, BOTTOM_NAME, insets.bottom)
                        .attribute(index, LEFT_NAME, insets.left)
                        .attribute(index, RIGHT_NAME, insets.right)
                        .closeStartEmpty();
            } else if (object instanceof LineBorder) {
                LineBorder border = (LineBorder) object;
                ctx.startTagAttributes(index, name).
                        attribute(index, TYPE_NAME, BorderType.line.toString()).
                        attribute(index, THICKNESS_NAME, border.getThickness()).
                        closeStartContent();
                new ColorMapper().marshal(border.getLineColor(), ctx);
                ctx.endTag(index, name);
            } else if (object instanceof EtchedBorder) {
                EtchedBorder border = (EtchedBorder) object;
                ctx.startTagAttributes(index, name).
                        attribute(index, TYPE_NAME,
                                BorderType.etched.toString()).
                        attribute(index, SUB_TYPE_NAME,
                                border.getEtchType()==EtchedBorder.RAISED?
                                        SubType.raised.toString():
                                        SubType.lowered.toString()).
                        closeStartContent();
                new ColorMapper().marshal(border.getHighlightColor(), ctx);
                new ColorMapper().marshal(border.getShadowColor(), ctx);
                ctx.endTag(index, name);
            } else if (object instanceof BevelBorder) {
                BevelBorder border = (BevelBorder) object;
                ctx.startTagAttributes(index, name).
                        attribute(index, TYPE_NAME,
                                BorderType.bevel.toString()).
                        attribute(index, SUB_TYPE_NAME,
                                border.getBevelType()==BevelBorder.RAISED?
                                        SubType.raised.toString():
                                        SubType.lowered.toString()).
                        closeStartContent();
                new ColorMapper().marshal(border.getHighlightInnerColor(), ctx);
                new ColorMapper().marshal(border.getHighlightOuterColor(), ctx);
                new ColorMapper().marshal(border.getShadowInnerColor(), ctx);
                new ColorMapper().marshal(border.getHighlightOuterColor(), ctx);
                ctx.endTag(index, name);
            } else if (object instanceof MatteBorder) {
                MatteBorder border = (MatteBorder) object;
                Insets insets = ((EmptyBorder) object).getBorderInsets();
                ctx.startTagAttributes(index, name)
                        .attribute(index, TYPE_NAME,
                                BorderType.matte.toString())
                        .attribute(index, TOP_NAME, insets.top)
                        .attribute(index, BOTTOM_NAME, insets.bottom)
                        .attribute(index, LEFT_NAME, insets.left)
                        .attribute(index, RIGHT_NAME, insets.right)
                        .closeStartContent();
                new ColorMapper().marshal(border.getMatteColor(), ctx);
                // todo: we should support tiled icons here to be 100% complete
                ctx.endTag(index, name);
            } else if (object instanceof CompoundBorder) {
                CompoundBorder border = (CompoundBorder) object;
                ctx.startTagAttributes(index, name)
                        .attribute(index, TYPE_NAME,
                                BorderType.compound.toString())
                        .closeStartContent();
                new BorderMapper(null,0, INSIDE_NAME).marshal(border.getInsideBorder(),ctx);
                new BorderMapper(null,0, OUTSIDE_NAME).marshal(border.getOutsideBorder(),ctx);
                ctx.endTag(index, name);
            } else {
                throw new JiBXException("Invalid object type for marshaller");
            }
        }
    }

    public Object unmarshal(Object object,
                            IUnmarshallingContext iUnmarshallingContext)
            throws JiBXException {
        Border border = null;
        // make sure we're at the appropriate start tag
        UnmarshallingContext ctx = (UnmarshallingContext) iUnmarshallingContext;
        if (!ctx.isAt(uri, name)) {
            ctx.throwStartTagNameError(uri, name);
        }
        // get type
        BorderType type = BorderType.valueOf(ctx.attributeText(uri, TYPE_NAME)
                .toLowerCase());
        int top,bottom,left,right;
        Color color;
        switch(type){
            case empty:
                top = ctx.attributeInt(uri, TOP_NAME, index);
                bottom = ctx.attributeInt(uri, BOTTOM_NAME, index);
                left = ctx.attributeInt(uri, LEFT_NAME, index);
                right = ctx.attributeInt(uri, RIGHT_NAME, index);
                border = BorderFactory.createEmptyBorder(top,left,bottom,right);
                break;
            case line:
                int thickness = ctx.attributeInt(uri, THICKNESS_NAME, index);
                ctx.parsePastStartTag(uri,name);
                color = (Color)new ColorMapper().unmarshal(null,ctx);
                border = BorderFactory.createLineBorder(color,thickness);
                break;
            case etched:
                SubType etchedType = SubType.valueOf(
                        ctx.attributeText(uri, SUB_TYPE_NAME).toLowerCase());
                ctx.parsePastStartTag(uri,name);
                Color highColor = (Color)new ColorMapper()
                        .unmarshal(null,ctx);
                Color shadowColor = (Color)new ColorMapper()
                        .unmarshal(null,ctx);
                border = BorderFactory.createEtchedBorder(
                        etchedType.getSubType(),highColor,shadowColor);
                break;
            case bevel:
                SubType bevelType = SubType.valueOf(
                        ctx.attributeText(uri, SUB_TYPE_NAME).toLowerCase());
                ctx.parsePastStartTag(uri,name);
                Color innerHighColor = (Color)new ColorMapper()
                        .unmarshal(null,ctx);
                Color outerHighColor = (Color)new ColorMapper()
                        .unmarshal(null,ctx);
                Color innerShadowColor = (Color)new ColorMapper()
                        .unmarshal(null,ctx);
                Color outerShadowColor = (Color)new ColorMapper()
                        .unmarshal(null,ctx);
                border = BorderFactory.createBevelBorder(
                        bevelType.getSubType(),outerHighColor,innerHighColor,
                        outerShadowColor,innerShadowColor);
                break;
            case matte:
                top = ctx.attributeInt(uri, TOP_NAME, index);
                bottom = ctx.attributeInt(uri, BOTTOM_NAME, index);
                left = ctx.attributeInt(uri, LEFT_NAME, index);
                right = ctx.attributeInt(uri, RIGHT_NAME, index);
                ctx.parsePastStartTag(uri,name);
                color = (Color)new ColorMapper().unmarshal(null,ctx);
                border = BorderFactory.createMatteBorder(top,left,bottom,right,
                        color);
                break;
            case compound:
                ctx.parsePastStartTag(uri,name);
                Border inside = (Border) new BorderMapper(null,0, INSIDE_NAME)
                        .unmarshal(null,ctx);
                Border outside = (Border) new BorderMapper(null,0, OUTSIDE_NAME)
                        .unmarshal(null,ctx);
                border = BorderFactory.createCompoundBorder(outside, inside);
                break;
            case painter:
                String painterName = ctx.attributeText(uri, PAINTER_NAME);
                top = ctx.attributeInt(uri, TOP_NAME, index);
                bottom = ctx.attributeInt(uri, BOTTOM_NAME, index);
                left = ctx.attributeInt(uri, LEFT_NAME, index);
                right = ctx.attributeInt(uri, RIGHT_NAME, index);
                border = new PainterBorder(painterName, top, left, bottom, right);
        }
        ctx.parsePastEndTag(uri, name);
        return border;
    }
}

