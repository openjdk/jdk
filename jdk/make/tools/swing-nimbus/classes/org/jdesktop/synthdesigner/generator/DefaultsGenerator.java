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
package org.jdesktop.synthdesigner.generator;

import org.jdesktop.swingx.designer.Canvas;
import org.jdesktop.swingx.designer.font.Typeface;
import org.jdesktop.swingx.designer.paint.Matte;
import org.jdesktop.swingx.designer.paint.PaintModel;
import static org.jdesktop.synthdesigner.generator.GeneratorUtils.makePretty;
import static org.jdesktop.synthdesigner.generator.GeneratorUtils.toConstantName;
import static org.jdesktop.synthdesigner.generator.ObjectCodeConvertors.convert;
import static org.jdesktop.synthdesigner.generator.TemplateWriter.read;
import static org.jdesktop.synthdesigner.generator.TemplateWriter.writeSrcFile;
import org.jdesktop.synthdesigner.synthmodel.SynthModel;
import org.jdesktop.synthdesigner.synthmodel.UIComponent;
import org.jdesktop.synthdesigner.synthmodel.UIFont;
import org.jdesktop.synthdesigner.synthmodel.UIIconRegion;
import org.jdesktop.synthdesigner.synthmodel.UIPaint;
import org.jdesktop.synthdesigner.synthmodel.UIProperty;
import org.jdesktop.synthdesigner.synthmodel.UIRegion;
import org.jdesktop.synthdesigner.synthmodel.UIState;
import org.jdesktop.synthdesigner.synthmodel.UIStateType;
import org.jdesktop.synthdesigner.synthmodel.UIStyle;

import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jdesktop.synthdesigner.synthmodel.PainterBorder;

/**
 * DefaultsGenerator
 * <p/>
 * There are two main sets of defaults that must be configured. The first is
 * the actual UI defaults tree. The second is a map of components + regions, which
 * are used to decide what SynthStyle to use.
 *
 * @author Jasper Potts
 * @author Richard Bair
 */
public class DefaultsGenerator {
    private static String stateTypeImplTemplate;

    private static String getStateTypeTemplate() {
        if (stateTypeImplTemplate == null) {
            //load the painter template file into an in-memory string to improve performance
            //when generating a lot of classes
            try {
                stateTypeImplTemplate = read("resources/StateImpl.template");
            } catch (IOException e) {
                System.err.println("Failed to read template files.");
                throw new RuntimeException(e);
            }
        }
        return stateTypeImplTemplate;
    }

    /**
     * Generate the defaults file and all painter files for a SynthModel. This method
     * is the main entry point, called by the Generator class.
     *
     * @param uiDefaultInit      The buffer to write ui default put methods of the form <code>d.put("activeCaption", new
     *                           ColorUIResource(123, 45, 200));</code>
     * @param styleInit          The buffer to write out code to generate Synth Style populating the styles map <code>m
     *                           = new HashMap<Key, LazyStyle>()</code>
     * @param model              The Synth Model we are writing out defaults class for
     * @param variables          The variables map pre populated with "PACKAGE" and "LAF_NAME"
     * @param packageNamePrefix  The package name associated with this synth look and feel. For example,
     *                           org.mypackage.mylaf
     * @param painterPackageRoot The directory to write painters out to
     */
    public static void generateDefaults(StringBuilder uiDefaultInit, StringBuilder styleInit, SynthModel model,
                                        Map<String, String> variables, String packageNamePrefix,
                                        File painterPackageRoot) {
        // write color palette
        uiDefaultInit.append("        //Color palette\n");
        writeColorPalette(uiDefaultInit, model.getColorPalette());
        uiDefaultInit.append("\n");
        // write fonts palette
        uiDefaultInit.append("        //Font palette\n");
        uiDefaultInit.append("        d.put(\"defaultFont\", new FontUIResource(defaultFont));\n");
        writeFontPalette(uiDefaultInit, model.getFontPalette());
        uiDefaultInit.append("\n");
        // TODO: Other palettes
        uiDefaultInit.append("        //Border palette\n");
        uiDefaultInit.append("\n");
        // write global style
        uiDefaultInit.append("        //The global style definition\n");
        writeStyle(model.getStyle(), uiDefaultInit, "");
        uiDefaultInit.append("\n");
        // write components
        for (UIComponent c : model.getComponents()) {
            String prefix = escape(c.getKey());
            uiDefaultInit.append("        //Initialize ").append(prefix)
                    .append("\n");
            writeRegion(c, c, prefix, uiDefaultInit,
                    styleInit, variables, packageNamePrefix, painterPackageRoot);
            uiDefaultInit.append("\n");
        }
    }

    private static void writeColorPalette(StringBuilder uiDefaultInit, List<UIPaint> colors) {
        for (UIPaint color : colors) {
            uiDefaultInit.append("        d.put(\"")
                    .append(color.getName())
                    .append("\",")
                    .append(convertPaint(color.getValue()))
                    .append(");\n");
        }
    }

    private static void writeFontPalette(StringBuilder uiDefaultInit, List<UIFont> fonts) {
        for (UIFont font : fonts) {
            // We have no way of doing CSS style font lists yet so will just
            // just the first font
            if (!font.getFonts().isEmpty()){
                Typeface t = font.getFonts().get(0);
                if (t.isAbsolute()){
                    Font f = t.getFont();
                    uiDefaultInit.append("        d.put(\"")
                        .append(font.getName())
                        .append("\", new javax.swing.plaf.FontUIResource(\"")
                        .append(f.getName())
                        .append("\", ")
                        .append(f.getStyle())
                        .append(", ")
                        .append(f.getSize())
                        .append("));\n");
                } else {
                    uiDefaultInit.append("        d.put(\"")
                        .append(font.getName())
                        .append("\", new DerivedFont(\"")
                        .append(t.getUiDefaultParentName())
                        .append("\", ")
                        .append(t.getSizeOffset())
                        .append("f, ");
                    switch (t.getBold()){
                        case Default:
                            uiDefaultInit.append("null");
                            break;
                        case On:
                            uiDefaultInit.append("true");
                            break;
                        case Off:
                            uiDefaultInit.append("false");
                            break;
                    }
                    uiDefaultInit.append(", ");
                    switch (t.getItalic()){
                        case Default:
                            uiDefaultInit.append("null");
                            break;
                        case On:
                            uiDefaultInit.append("true");
                            break;
                        case Off:
                            uiDefaultInit.append("false");
                            break;
                    }
                    uiDefaultInit.append("));\n");
                }
            }
        }
    }

    /**
     * Write out the UIDefaults entries for a style
     *
     * @param style         The style to write defaults entries for
     * @param uiDefaultInit The buffer to write ui default put methods of the form <code>d.put("activeCaption", new
     *                      ColorUIResource(123, 45, 200));</code>
     * @param prefix        The prefix for the style property names, for the model path where the style is from, should
     *                      end with a "."
     */
    private static void writeStyle(UIStyle style, StringBuilder uiDefaultInit, String prefix) {
        if (!style.isTextForegroundInherited()) writeMatte(prefix + "textForeground", style.getTextForeground(), uiDefaultInit);
        if (!style.isTextBackgroundInherited()) writeMatte(prefix + "textBackground", style.getTextBackground(), uiDefaultInit);
        if (!style.isBackgroundInherited()) writeMatte(prefix + "background", style.getBackground(), uiDefaultInit);
        if (!style.isFontInherited()) writeTypeFace(prefix + "font", style.getFont(), uiDefaultInit);
        for (UIProperty property : style.getUiProperties()) {
            switch (property.getType()) {
                case BOOLEAN:
                    Boolean b = ((Boolean)property.getValue());
                    if (b != null) {
                        uiDefaultInit.append("        d.put(\"")
                                .append(prefix)
                                .append(property.getName())
                                .append("\", ")
                                .append(b ? "Boolean.TRUE" : "Boolean.FALSE")
                                .append(");\n");
                    }
                    break;
                case STRING:
                    uiDefaultInit.append("        d.put(\"")
                            .append(prefix)
                            .append(property.getName())
                            .append("\", \"")
                            .append(property.getValue().toString())
                            .append("\");\n");
                    break;
                case INT:
                    uiDefaultInit.append("        d.put(\"")
                            .append(prefix)
                            .append(property.getName())
                            .append("\", new Integer(")
                            .append(((Integer) property.getValue()).intValue())
                            .append("));\n");
                    break;
                case FLOAT:
                    uiDefaultInit.append("        d.put(\"")
                            .append(prefix)
                            .append(property.getName())
                            .append("\", new Float(")
                            .append(((Float) property.getValue()).floatValue())
                            .append("f));\n");
                    break;
                case DOUBLE:
                    uiDefaultInit.append("        d.put(\"")
                            .append(prefix)
                            .append(property.getName())
                            .append("\", new Double(")
                            .append(((Double) property.getValue()).doubleValue())
                            .append("));\n");
                    break;
                case COLOR:
                    uiDefaultInit.append("        d.put(\"")
                            .append(prefix)
                            .append(property.getName())
                            .append("\", ")
                            .append(convertPaint((Matte)property.getValue()))
                            .append(");\n");
                    break;
                case FONT:
                    writeTypeFace(prefix.replace("\"", "\\\"") + property.getName(),
                            (Typeface) property.getValue(), uiDefaultInit);
                    break;
                case INSETS:
                    Insets i = (Insets) property.getValue();
                    uiDefaultInit.append("        d.put(\"")
                            .append(prefix)
                            .append(property.getName())
                            .append("\", new InsetsUIResource(")
                            .append(i.top).append(", ").append(i.left).append(", ").append(i.bottom).append(", ")
                            .append(i.right)
                            .append("));\n");
                    break;
                case DIMENSION:
                    Dimension d = (Dimension) property.getValue();
                    uiDefaultInit.append("        d.put(\"")
                            .append(prefix)
                            .append(property.getName())
                            .append("\", new DimensionUIResource(")
                            .append(d.width).append(", ").append(d.height)
                            .append("));\n");
                    break;
                case BORDER:
                    uiDefaultInit.append("        d.put(\"")
                            .append(prefix)
                            .append(property.getName())
                            .append("\", new BorderUIResource(");
                    uiDefaultInit.append(convertBorder(
                            (Border)property.getValue()));
                    uiDefaultInit.append("));\n");
                    break;
            }
        }
    }

    private static void writeMatte(String propertyName, Matte matte, StringBuilder uiDefaultInit) {
        if (matte==null) System.err.println("Error matte is NULL for ["+propertyName+"]");
        uiDefaultInit.append("        d.put(\"")
                    .append(propertyName)
                    .append("\", ")
                    .append(convertPaint(matte))
                    .append(");\n");
    }

    private static void writeTypeFace(String propertyName, Typeface typeface, StringBuilder uiDefaultInit) {
        uiDefaultInit.append("        d.put(\"")
                .append(propertyName)
                .append("\", new DerivedFont(\"")
                .append(typeface.getUiDefaultParentName())
                .append("\", ")
                .append(typeface.getSizeOffset())
                .append("f, ");
        switch (typeface.getBold()) {
            case Default:
                uiDefaultInit.append("null,");
                break;
            case Off:
                uiDefaultInit.append("Boolean.FALSE,");
                break;
            case On:
                uiDefaultInit.append("Boolean.TRUE,");
                break;
        }
        switch (typeface.getItalic()) {
            case Default:
                uiDefaultInit.append("null");
                break;
            case Off:
                uiDefaultInit.append("Boolean.FALSE");
                break;
            case On:
                uiDefaultInit.append("Boolean.TRUE");
                break;
        }
        uiDefaultInit.append("));\n");
    }


    /**
     * Write out code for a Component or Region
     *
     * @param comp               This may be the same as the region <code>reg</code> or is the parent component
     *                           containing the region
     * @param region             The region we are writing out
     * @param prefix             This is dot sperated path of component and sub regions to and including the region
     *                           <code>reg</code> of the form [Comp].[Region]......[Region] path
     * @param uiDefaultInit      This is for inserting into org.mypackage.mylaf.MyDefaults#getDefaults() method
     * @param styleInit          This is for inserting into org.mypackage.mylaf.MyDefaults#initialize() method
     * @param variables          The variables map pre populated with "PACKAGE" and "LAF_NAME"
     * @param packageNamePrefix  The package name associated with this synth look and feel. For example,
     *                           org.mypackage.mylaf
     * @param painterPackageRoot The directory to write painters out to
     */
    private static void writeRegion(UIComponent comp, UIRegion region, String prefix, StringBuilder uiDefaultInit,
                                    StringBuilder styleInit, Map<String, String> variables,
                                    String packageNamePrefix, File painterPackageRoot) {
        // register component with LAF
        String regionCode = GeneratorUtils.getRegionNameCaps(region.getName());
        if (regionCode == null) {
            throw new IllegalStateException("We were asked to encode a region we know nothing about: " + region.getName());
        } else {
            regionCode = "Region." + regionCode;
        }

        //construct the list of States that accompany this registration.
        StringBuffer regString = new StringBuffer(); //like: Enabled,Disabled,Foo,Default,Etc
        List<UIStateType> types = comp.getStateTypes(); //state types are only defined on the UIComponent level
        if (types != null && types.size() > 0) {
            for (UIStateType type : types) {
                regString.append(type.getKey());
                regString.append(",");
            }
            //remove the last ","
            regString.deleteCharAt(regString.length()-1);
        }

        styleInit.append("        register(")
                .append(regionCode)
                .append(", \"")
                .append(prefix);
        styleInit.append("\"");
        styleInit.append(");\n");

        // write content margins
        Insets i = (Insets) region.getContentMargins();
        uiDefaultInit.append("        d.put(\"")
                .append(prefix)
                .append(".contentMargins")
                .append("\", new InsetsUIResource(")
                .append(i.top).append(", ").append(i.left).append(", ").append(i.bottom).append(", ").append(i.right)
                .append("));\n");
        // write opaque if true
        if (region instanceof UIComponent && ((UIComponent)region).isOpaque()) {
            uiDefaultInit.append("        d.put(\"")
                    .append(prefix)
                    .append(".opaque")
                    .append("\", Boolean.TRUE);\n");
        }
        //write the State, if necessary
        if (!regString.equals("Enabled,MouseOver,Pressed,Disabled,Focused,Selected,Default") && types.size() > 0) {
            //there were either custom states, or the normal states were in a custom order
            //so go ahead and write out prefix.State
            uiDefaultInit.append("        d.put(\"")
                    .append(prefix)
                    .append(".States")
                    .append("\", \"")
                    .append(regString)
                    .append("\");\n");
        }
        //write out any custom states, if necessary
        for (UIStateType type : types) {
            String synthState = type.getKey();
            if (!"Enabled".equals(synthState) &&
                !"MouseOver".equals(synthState) &&
                !"Pressed".equals(synthState) &&
                !"Disabled".equals(synthState) &&
                !"Focused".equals(synthState) &&
                !"Selected".equals(synthState) &&
                !"Default".equals(synthState)) {
                //what we have here, gentlemen, is a bona-fide custom state.
                try {
                    //if the type is not one of the standard types, then construct a name for
                    //the new type, and write out a new subclass of State.
                    java.lang.String className = makePretty(prefix) + synthState + "State";
                    java.lang.String body = type.getCodeSnippet();
                    variables.put("STATE_NAME", className);
                    variables.put("STATE_KEY", synthState);
                    variables.put("BODY", body);

                    writeSrcFile(getStateTypeTemplate(), variables, new java.io.File(painterPackageRoot, className + ".java"));

                    variables.remove("STATE_NAME");
                    variables.remove("STATE_KEY");
                    variables.remove("BODY");

                    uiDefaultInit.append("        d.put(\"")
                            .append(prefix)
                            .append(".")
                            .append(synthState)
                            .append("\", new ")
                            .append(className)
                            .append("());\n");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        // write region style
        writeStyle(region.getStyle(), uiDefaultInit, prefix + ".");

        try {
            boolean hasCanvas = hasCanvas(region);
            if (hasCanvas) {
                PainterGenerator.writePainter(region, variables, painterPackageRoot, prefix);
            }
            String fileNamePrefix = makePretty(prefix) + "Painter";
            // write states ui defaults
            for (UIState state : region.getBackgroundStates()) {
                String statePrefix = prefix + "[" + state.getName() + "]";
                // write state style
                writeStyle(state.getStyle(), uiDefaultInit, statePrefix + ".");
                // write painter
                if (hasCanvas) {
                    writeLazyPainter(state, uiDefaultInit, statePrefix, packageNamePrefix, fileNamePrefix, "background");
                }
            }
            for (UIState state : region.getForegroundStates()) {
                String statePrefix = prefix + "[" + state.getName() + "]";
                // write state style
                writeStyle(state.getStyle(), uiDefaultInit, statePrefix + ".");
                // write painter
                if (hasCanvas) {
                    writeLazyPainter(state, uiDefaultInit, statePrefix, packageNamePrefix, fileNamePrefix, "foreground");
                }
            }
            for (UIState state : region.getBorderStates()) {
                String statePrefix = prefix + "[" + state.getName() + "]";
                // write state style
                writeStyle(state.getStyle(), uiDefaultInit, statePrefix + ".");
                // write painter
                if (hasCanvas) {
                    writeLazyPainter(state, uiDefaultInit, statePrefix, packageNamePrefix, fileNamePrefix, "border");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // handle sub regions
        for (UIRegion subRegion : region.getSubRegions()) {
            String subregionName = prefix + ":" + escape(subRegion.getKey());
            if (subRegion instanceof UIIconRegion) {
                writeIconRegion(comp, (UIIconRegion) subRegion, prefix, uiDefaultInit,
                        variables, packageNamePrefix, painterPackageRoot);
            } else if (subRegion instanceof UIComponent) {
                // inner named component
                UIComponent subComponent = (UIComponent) subRegion;
                writeRegion(subComponent, subRegion, subregionName,
                        uiDefaultInit, styleInit, variables, packageNamePrefix, painterPackageRoot);
            } else {
                writeRegion(comp, subRegion, subregionName, uiDefaultInit, styleInit, variables,
                        packageNamePrefix, painterPackageRoot);
            }
        }
    }

    private static void writeLazyPainter(UIState state, StringBuilder uiDefaultInit, String statePrefix, String packageNamePrefix, String fileNamePrefix, String painterSuffix) {
        Canvas canvas = state.getCanvas();
        if (!canvas.isBlank()) {
            Insets si = canvas.getStretchingInsets();
            boolean inverted = state.isInverted();
            UIStyle.CacheMode cache = state.getStyle().getCacheMode();
            String cacheModeString = null;
            switch (cache) {
                case NO_CACHING: cacheModeString = "AbstractRegionPainter.PaintContext.CacheMode.NO_CACHING"; break;
                case FIXED_SIZES: cacheModeString = "AbstractRegionPainter.PaintContext.CacheMode.FIXED_SIZES"; break;
                case NINE_SQUARE_SCALE: cacheModeString = "AbstractRegionPainter.PaintContext.CacheMode.NINE_SQUARE_SCALE"; break;
            }
            double maxH = state.getStyle().getMaxHozCachedImgScaling();
            double maxV = state.getStyle().getMaxVertCachedImgScaling();
            String stateConstant = toConstantName(painterSuffix + "_" + UIState.keysToString(state.getStateKeys()));

            uiDefaultInit.append("        d.put(\"")
                    .append(statePrefix)
                    .append(".").append(painterSuffix).append("Painter\", new LazyPainter(\"")
                    .append(packageNamePrefix).append(".").append(fileNamePrefix)
                    .append("\", ")
                    .append(fileNamePrefix).append(".").append(stateConstant).append(", ")
                    .append(convert(si)).append(", ")
                    .append(convert(canvas.getSize())).append(", ")
                    .append(inverted).append(", ")
                    .append(cacheModeString).append(", ")
                    .append(maxH == Double.POSITIVE_INFINITY ? "Double.POSITIVE_INFINITY" : maxH).append(", ")
                    .append(maxV == Double.POSITIVE_INFINITY ? "Double.POSITIVE_INFINITY" : maxV).append("));\n");
        }
    }


    /**
     * Write out code for a IconRegion
     *
     * @param comp               This may be the same as the region <code>region</code> or is the parent component
     *                           containing the region
     * @param region             The region we are writing out
     * @param prefix             This is [Comp][Region]......[Region] path
     * @param key                The key for this icon.
     * @param uiDefaultInit      This is for inserting into org.mypackage.mylaf.MyDefaults#getDefaults() method
     * @param variables          The variables map pre populated with "PACKAGE" and "LAF_NAME"
     * @param packageNamePrefix  The package name associated with this synth look and feel. For example,
     *                           org.mypackage.mylaf
     * @param painterPackageRoot The directory to write painters out to
     */
    private static void writeIconRegion(UIComponent comp, UIIconRegion region, String prefix,
                                        StringBuilder uiDefaultInit, Map<String, String> variables,
                                        String packageNamePrefix, File painterPackageRoot) {

        Dimension size = null;
        String fileNamePrefix = makePretty(prefix) + "Painter";
        // write states ui defaults
        for (UIState state : region.getBackgroundStates()) {// TODO: Handle Background,Foreground and Borders States Lists? Actually not sure that IconRegions need support borders or foregrounds
            Canvas canvas = state.getCanvas();
            if (!canvas.isBlank()) {
                String statePrefix = prefix + "[" + state.getName() + "]";
                // Put Painter in UiDefaults
                writeLazyPainter(state, uiDefaultInit, statePrefix, packageNamePrefix, fileNamePrefix, region.getKey());
                size = canvas.getSize();
            }
        }

        if (size != null) {
            // Put SynthIconImpl wrapper in UiDefaults
            String key = region.getBasicKey() == null ? prefix + "." + region.getKey() : region.getBasicKey();
            uiDefaultInit.append("        d.put(\"")
                    .append(key)
                    .append("\", new NimbusIcon(\"") //TODO should this be wrapped in an IconUIResource?
                    .append(prefix)
                    .append("\", \"")
                    .append(region.getKey())
                    .append("Painter")
                    .append("\", ")
                    .append(size.width)
                    .append(", ")
                    .append(size.height)
                    .append("));\n");
        }

        // handle sub regions
        if (region.getSubRegions().length > 0) {
            // there is no meaning to a sub region inside a IconRegion
            throw new IllegalStateException("You can not have sub regions inside UiIconRegions. \"" +
                    comp.getSubRegions()[0].getName() + "\" is inside \""
                    + prefix.substring(0, prefix.length() - 1) + "\"");
        }
    }

    /**
     * Utility method for escaping all double quotes with backslash double-quote.
     */
    private static String escape(String s) {
        return s.replace("\"", "\\\"");
    }

    private static String convertPaint(PaintModel paint){
        if (paint instanceof Matte){
            Matte matte = (Matte)paint;
            if (matte.isAbsolute()){
                String colorParams = convert(matte.getColor());
                if (matte.isUiResource()) {
                    return "new ColorUIResource(" + colorParams + ")";
                } else {
                    return colorParams;
                }
            } else {
                String s = "getDerivedColor(\"" +
                            matte.getUiDefaultParentName()+"\","+
                            matte.getHueOffset()+"f,"+matte.getSaturationOffset()+
                            "f,"+matte.getBrightnessOffset()+"f,"+
                            matte.getAlphaOffset();
                if (matte.isUiResource()) {
                    return s + ")";
                } else {
                    return s + ",false)";
                }
            }
        } else {
            //TODO: What about gradients etc here?
            System.err.println("Error: Could not write paint in " +
                    "DefaultsGenerator as it was not a Matte. = "+
                    paint.getClass().getName());
            return "";
        }
    }

    private static String convertBorder(Border val) {
        StringBuilder uiDefaultInit = new StringBuilder();
        Insets i;
        if (val instanceof PainterBorder) {
            PainterBorder pb = (PainterBorder) val;
            i = pb.getBorderInsets();
            uiDefaultInit.append("new PainterBorder(\"")
                    .append(pb.getPainterName())
                    .append("\", new Insets(")
                    .append(i.top).append(", ")
                    .append(i.left).append(", ")
                    .append(i.bottom).append(", ")
                    .append(i.right)
                    .append("))");
        } else if (val instanceof EmptyBorder) {
            i = ((EmptyBorder) val).getBorderInsets();
            uiDefaultInit.append("BorderFactory.createEmptyBorder(")
                    .append(i.top).append(", ")
                    .append(i.left).append(", ")
                    .append(i.bottom).append(", ")
                    .append(i.right)
                    .append(")");
        } else if (val instanceof LineBorder) {
            LineBorder border = (LineBorder) val;
            uiDefaultInit.append("BorderFactory.createLineBorder(")
                    .append(convert(border.getLineColor()))
                    .append(",")
                    .append(border.getThickness())
                    .append(")");
        } else if (val instanceof EtchedBorder) {
            EtchedBorder border = (EtchedBorder) val;
            uiDefaultInit.append("BorderFactory.createEtchedBorder(")
                    .append(border.getEtchType())
                    .append(",")
                    .append(convert(border.getHighlightColor()))
                    .append(",")
                    .append(convert(border.getShadowColor()))
                    .append(")");
        } else if (val instanceof BevelBorder) {
            BevelBorder border = (BevelBorder) val;
            uiDefaultInit.append("BorderFactory.createEtchedBorder(")
                    .append(border.getBevelType())
                    .append(",")
                    .append(convert(border.getHighlightOuterColor()))
                    .append(",")
                    .append(convert(border.getHighlightInnerColor()))
                    .append(",")
                    .append(convert(border.getShadowOuterColor()))
                    .append(",")
                    .append(convert(border.getShadowInnerColor()))
                    .append(")");
        } else if (val instanceof MatteBorder) {
            MatteBorder border = (MatteBorder) val;
            i = border.getBorderInsets();
            uiDefaultInit.append("BorderFactory.createEmptyBorder(")
                    .append(i.top).append(", ")
                    .append(i.left).append(", ")
                    .append(i.bottom).append(", ")
                    .append(i.right).append(", ")
                    .append(convert(border.getMatteColor()))
                    .append(")");
        } else if (val instanceof CompoundBorder) {
            CompoundBorder border = (CompoundBorder) val;
            uiDefaultInit.append("BorderFactory.createEmptyBorder(")
                    .append(convertBorder(border.getOutsideBorder()))
                    .append(",")
                    .append(convertBorder(border.getInsideBorder()))
                    .append(")");
        }
        return uiDefaultInit.toString();
    }

    private static boolean hasCanvas(UIRegion region) {
        for (UIState s : region.getBackgroundStates()) {
            if (!s.getCanvas().isBlank()) return true;
        }
        for (UIState s : region.getBorderStates()) {
            if (!s.getCanvas().isBlank()) return true;
        }
        for (UIState s : region.getForegroundStates()) {
            if (!s.getCanvas().isBlank()) return true;
        }
        for (UIRegion subregion : region.getSubRegions()) {
            if (hasCanvas(subregion)) return true;
        }
        return false;
    }
}
