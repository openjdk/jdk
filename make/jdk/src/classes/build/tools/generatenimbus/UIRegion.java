/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.generatenimbus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;

class UIRegion {
    @XmlAttribute protected String name;
    @XmlAttribute protected String key;
    @XmlAttribute private boolean opaque = false;

    @XmlElement private Insets contentMargins = new Insets(0, 0, 0, 0);

    @XmlElement(name="state")
    @XmlElementWrapper(name="backgroundStates")
    protected List<UIState> backgroundStates = new ArrayList<UIState>();
    public List<UIState> getBackgroundStates() { return backgroundStates; }

    @XmlElement(name="state")
    @XmlElementWrapper(name="foregroundStates")
    protected List<UIState> foregroundStates = new ArrayList<UIState>();
    public List<UIState> getForegroundStates() { return foregroundStates; }

    @XmlElement(name="state")
    @XmlElementWrapper(name="borderStates")
    protected List<UIState> borderStates = new ArrayList<UIState>();
    public List<UIState> getBorderStates() { return borderStates; }

    @XmlElement private UIStyle style = new UIStyle();

    @XmlElements({
        @XmlElement(name = "region", type = UIRegion.class),
        @XmlElement(name = "uiComponent", type = UIComponent.class),
        @XmlElement(name = "uiIconRegion", type = UIIconRegion.class)
    })
    @XmlElementWrapper(name="regions")
    private List<UIRegion> subRegions = new ArrayList<UIRegion>();
    public List<UIRegion> getSubRegions() { return subRegions; }

    protected void initStyles(UIStyle parentStyle) {
        style.setParentStyle(parentStyle);
        for (UIState state: backgroundStates) {
            state.getStyle().setParentStyle(this.style);
        }
        for (UIState state: foregroundStates) {
            state.getStyle().setParentStyle(this.style);
        }
        for (UIState state: borderStates) {
            state.getStyle().setParentStyle(this.style);
        }
        for (UIRegion region: subRegions) {
            region.initStyles(this.style);
        }
    }

    public String getKey() {
        return key == null || "".equals(key) ? name : key;
    }

    private boolean hasCanvas() {
        for (UIState s : backgroundStates) {
            if (s.hasCanvas()) return true;
        }
        for (UIState s : borderStates) {
            if (s.hasCanvas()) return true;
        }
        for (UIState s : foregroundStates) {
            if (s.hasCanvas()) return true;
        }
        for (UIRegion r: subRegions) {
            if (r.hasCanvas()) return true;
        }
        return false;
    }

    public void write(StringBuilder sb, StringBuilder styleBuffer,
                      UIComponent comp, String prefix, String pkg) {
        // write content margins
        sb.append(String.format("        d.put(\"%s.contentMargins\", %s);\n",
                                prefix, contentMargins.write(true)));
        // write opaque if true
        if (opaque) {
            sb.append(String.format("        d.put(\"%s.opaque\", Boolean.TRUE);\n", prefix));
        }

        // register component with LAF
        String regionCode = "Region." + Utils.regionNameToCaps(name);
        styleBuffer.append(String.format("        register(%s, \"%s\");\n",
                                         regionCode, prefix));

        //write the State, if necessary
        StringBuffer regString = new StringBuffer();
        List<UIStateType> types = comp.getStateTypes();
        if (types != null && types.size() > 0) {
            for (UIStateType type : types) {
                regString.append(type.getKey());
                regString.append(",");
            }
            //remove the last ","
            regString.deleteCharAt(regString.length() - 1);
        }

        if (! regString.equals("Enabled,MouseOver,Pressed,Disabled,Focused,Selected,Default") && types.size() > 0) {
            //there were either custom states, or the normal states were in a custom order
            //so go ahead and write out prefix.State
            sb.append(String.format("        d.put(\"%s.States\", \"%s\");\n",
                                    prefix, regString));
        }

        // write out any custom states, if necessary
        for (UIStateType type : types) {
            String synthState = type.getKey();
            if (! "Enabled".equals(synthState) &&
                ! "MouseOver".equals(synthState) &&
                ! "Pressed".equals(synthState) &&
                ! "Disabled".equals(synthState) &&
                ! "Focused".equals(synthState) &&
                ! "Selected".equals(synthState) &&
                ! "Default".equals(synthState)) {

                //what we have here, gentlemen, is a bona-fide custom state.
                //if the type is not one of the standard types, then construct a name for
                //the new type, and write out a new subclass of State.
                String className = Utils.normalize(prefix) + synthState + "State";
                sb.append(String.format("        d.put(\"%s.%s\", new %s());\n",
                                        prefix, synthState, className));

                String body = type.getCodeSnippet();
                Map<String, String> variables = Generator.getVariables();
                variables.put("STATE_NAME", className);
                variables.put("STATE_KEY", synthState);
                variables.put("BODY", body);

                Generator.writeSrcFile("StateImpl", variables, className);
            }
        }

        // write style
        sb.append(style.write(prefix + '.'));

        String fileName = Utils.normalize(prefix) + "Painter";
        boolean hasCanvas = hasCanvas();
        if (hasCanvas) {
            PainterGenerator.writePainter(this, fileName);
        }
        // write states ui defaults
        for (UIState state : backgroundStates) {
            state.write(sb, prefix, pkg, fileName, "background");
        }
        for (UIState state : foregroundStates) {
            state.write(sb, prefix, pkg, fileName, "foreground");
        }
        for (UIState state : borderStates) {
            state.write(sb, prefix, pkg, fileName, "border");
        }

        // handle sub regions
        for (UIRegion subreg : subRegions) {
            String p = prefix;
            if (! (subreg instanceof UIIconRegion)) {
                p = prefix + ":" + Utils.escape(subreg.getKey());
            }
            UIComponent c = comp;
            if (subreg instanceof UIComponent) {
                c = (UIComponent) subreg;
            }
            subreg.write(sb, styleBuffer, c, p, pkg);
        }
    }
}
