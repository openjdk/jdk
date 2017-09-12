/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.xsom.impl.util;

import com.sun.xml.internal.xsom.XSAnnotation;
import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSIdentityConstraint;
import com.sun.xml.internal.xsom.XSListSimpleType;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSNotation;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSRestrictionSimpleType;
import com.sun.xml.internal.xsom.XSSchema;
import com.sun.xml.internal.xsom.XSSchemaSet;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSType;
import com.sun.xml.internal.xsom.XSUnionSimpleType;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.XSXPath;
import com.sun.xml.internal.xsom.impl.Const;
import com.sun.xml.internal.xsom.visitor.XSSimpleTypeVisitor;
import com.sun.xml.internal.xsom.visitor.XSTermVisitor;
import com.sun.xml.internal.xsom.visitor.XSVisitor;
import org.xml.sax.Locator;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.Iterator;

/**
 * Generates approximated tree model for XML from a schema component. This is
 * not intended to be a fully-fledged round-trippable tree model.
 *
 * <h2>Usage of this class</h2>
 *
 * <ol> <li>Create a new instance.</li> <li>Call {@link #visit(XSSchemaSet)}
 * function on your schema set.</li>
 * <li>Retrieve the model using {@link #getModel()}. </li></ol>
 *
 * Every node in the resulting tree is a {@link SchemaTreeTraverser.SchemaTreeNode},
 * and the model itself is {@link SchemaTreeTraverser.SchemaTreeModel}. You can
 * use {@link SchemaTreeTraverser.SchemaTreeCellRenderer} as a cell renderer for
 * your tree.
 *
 * @author Kirill Grouchnikov (kirillcool@yahoo.com)
 */
public class SchemaTreeTraverser implements XSVisitor, XSSimpleTypeVisitor {
    /**
     * The associated tree model.
     */
    private SchemaTreeModel model;

    /**
     * The current node in the tree.
     */
    private SchemaTreeNode currNode;

    /**
     * Tree model for schema hierarchy tree.
     *
     * @author Kirill Grouchnikov
     */
    public static final class SchemaTreeModel extends DefaultTreeModel {
        /**
         * A simple constructor. Is made private to allow creating the root node
         * first.
         *
         * @param root The root node.
         */
        private SchemaTreeModel(SchemaRootNode root) {
            super(root);
        }

        /**
         * A factory method for creating a new empty tree.
         *
         * @return New empty tree model.
         */
        public static SchemaTreeModel getInstance() {
            SchemaRootNode root = new SchemaRootNode();
            return new SchemaTreeModel(root);
        }

        public void addSchemaNode(SchemaTreeNode node) {
            ((SchemaRootNode) this.root).add(node);
        }
    }

    /**
     * The node of the schema hierarchy tree.
     *
     * @author Kirill Grouchnikov
     */
    public static class SchemaTreeNode extends DefaultMutableTreeNode {
        /**
         * File name of the corresponding schema artifact.
         */
        private String fileName;

        /**
         * Line number of the corresponding schema artifact.
         */
        private int lineNumber;

        /**
         * The caption of the corresponding artifact.
         */
        private String artifactName;

        /**
         * Simple constructor.
         *
         * @param artifactName Artifact name.
         * @param locator      Artifact locator.
         */
        public SchemaTreeNode(String artifactName, Locator locator) {
            this.artifactName = artifactName;
            if (locator == null) {
                this.fileName = null;
            }
            else {
                String filename = locator.getSystemId();
                filename = filename.replaceAll("\u002520", " ");
                // strip leading protocol
                if (filename.startsWith("file:/")) {
                    filename = filename.substring(6);
                }

                this.fileName = filename;
                this.lineNumber = locator.getLineNumber() - 1;
            }
        }

        /**
         * Returns the caption for <code>this</code> node.
         *
         * @return The caption for <code>this</code> node.
         */
        public String getCaption() {
            return this.artifactName;
        }

        /**
         * @return Returns the file name of the corresponding schema artifact.
         */
        public String getFileName() {
            return fileName;
        }

        /**
         * @param fileName The file name of the corresponding schema artifact to
         *                 set.
         */
        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        /**
         * @return Returns the line number of the corresponding schema
         *         artifact.
         */
        public int getLineNumber() {
            return lineNumber;
        }

        /**
         * @param lineNumber The line number of the corresponding schema
         *                   artifact to set.
         */
        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }

    /**
     * The root node of the schema hierarchy tree.
     *
     * @author Kirill Grouchnikov
     */
    public static class SchemaRootNode extends SchemaTreeNode {
        /**
         * A simple constructor.
         */
        public SchemaRootNode() {
            super("Schema set", null);
        }
    }


    /**
     * Sample cell renderer for the schema tree.
     *
     * @author Kirill Grouchnikov
     */
    public static class SchemaTreeCellRenderer extends JPanel implements
            TreeCellRenderer {
        /**
         * The icon label.
         */
        protected final JLabel iconLabel;

        /**
         * The text label
         */
        protected final JLabel nameLabel;

        /**
         * The selection indicator.
         */
        private boolean isSelected;

        /**
         * Background color for selected cells (light brown).
         */
        public final Color selectedBackground = new Color(255, 244, 232);


        /**
         * Foreground color for selected cells, both text and border (dark
         * brown).
         */
        public final Color selectedForeground = new Color(64, 32, 0);

        /**
         * Default font for the text label.
         */
        public final Font nameFont = new Font("Arial", Font.BOLD, 12);


        /**
         * Simple constructor.
         */
        public SchemaTreeCellRenderer() {
            FlowLayout fl = new FlowLayout(FlowLayout.LEFT, 1, 1);
            this.setLayout(fl);
            this.iconLabel = new JLabel();
            this.iconLabel.setOpaque(false);
            this.iconLabel.setBorder(null);
            this.add(this.iconLabel);

            // add some space
            this.add(Box.createHorizontalStrut(5));

            this.nameLabel = new JLabel();
            this.nameLabel.setOpaque(false);
            this.nameLabel.setBorder(null);
            this.nameLabel.setFont(nameFont);
            this.add(this.nameLabel);

            this.isSelected = false;

            this.setOpaque(false);
            this.setBorder(null);
        }

        /*
         * (non-Javadoc)
         *
         * @see javax.swing.JComponent#paintComponent(java.awt.Graphics)
         */
        public final void paintComponent(Graphics g) {
            int width = this.getWidth();
            int height = this.getHeight();
            if (this.isSelected) {
                g.setColor(selectedBackground);
                g.fillRect(0, 0, width - 1, height - 1);
                g.setColor(selectedForeground);
                g.drawRect(0, 0, width - 1, height - 1);
            }
            super.paintComponent(g);
        }

        /**
         * Sets values for the icon and text of <code>this</code> renderer.
         *
         * @param icon     Icon to show.
         * @param caption  Text to show.
         * @param selected Selection indicator. If <code>true</code>, the
         *                 renderer will be shown with different background and
         *                 border settings.
         */
        protected final void setValues(Icon icon, String caption,
                                       boolean selected) {

            this.iconLabel.setIcon(icon);
            this.nameLabel.setText(caption);

            this.isSelected = selected;
            if (selected) {
                this.nameLabel.setForeground(selectedForeground);
            }
            else {
                this.nameLabel.setForeground(Color.black);
            }
        }

        /* (non-Javadoc)
         * @see javax.swing.tree.TreeCellRenderer#getTreeCellRendererComponent(javax.swing.JTree, java.lang.Object, boolean, boolean, boolean, int, boolean)
         */
        public final Component getTreeCellRendererComponent(JTree tree, Object value,
                                                            boolean selected, boolean expanded, boolean leaf, int row,
                                                            boolean hasFocus) {
            if (value instanceof SchemaTreeNode) {
                SchemaTreeNode stn = (SchemaTreeNode) value;

                this.setValues(null, stn.getCaption(), selected);
                return this;
            }
            throw new IllegalStateException("Unknown node");
        }
    }


    /**
     * Simple constructor.
     */
    public SchemaTreeTraverser() {
        this.model = SchemaTreeModel.getInstance();
        this.currNode = (SchemaTreeNode) this.model.getRoot();
    }

    /**
     * Retrieves the tree model of <code>this</code> traverser.
     *
     * @return Tree model of <code>this</code> traverser.
     */
    public SchemaTreeModel getModel() {
        return model;
    }

    /**
     * Visits the root schema set.
     *
     * @param s Root schema set.
     */
    public void visit(XSSchemaSet s) {
        for (XSSchema schema : s.getSchemas()) {
            schema(schema);
        }
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSVisitor#schema(com.sun.xml.internal.xsom.XSSchema)
     */
    public void schema(XSSchema s) {
        // QUICK HACK: don't print the built-in components
        if (s.getTargetNamespace().equals(Const.schemaNamespace)) {
            return;
        }

        SchemaTreeNode newNode = new SchemaTreeNode("Schema "
                + s.getLocator().getSystemId(), s.getLocator());
        this.currNode = newNode;
        this.model.addSchemaNode(newNode);

        for (XSAttGroupDecl groupDecl : s.getAttGroupDecls().values()) {
            attGroupDecl(groupDecl);
        }

        for (XSAttributeDecl attrDecl : s.getAttributeDecls().values()) {
            attributeDecl(attrDecl);
        }

        for (XSComplexType complexType : s.getComplexTypes().values()) {
            complexType(complexType);
        }

        for (XSElementDecl elementDecl : s.getElementDecls().values()) {
            elementDecl(elementDecl);
        }

        for (XSModelGroupDecl modelGroupDecl : s.getModelGroupDecls().values()) {
            modelGroupDecl(modelGroupDecl);
        }

        for (XSSimpleType simpleType : s.getSimpleTypes().values()) {
            simpleType(simpleType);
        }
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSVisitor#attGroupDecl(com.sun.xml.internal.xsom.XSAttGroupDecl)
     */
    public void attGroupDecl(XSAttGroupDecl decl) {
        SchemaTreeNode newNode = new SchemaTreeNode("Attribute group \""
                + decl.getName() + "\"", decl.getLocator());
        this.currNode.add(newNode);
        this.currNode = newNode;

        Iterator itr;

        itr = decl.iterateAttGroups();
        while (itr.hasNext()) {
            dumpRef((XSAttGroupDecl) itr.next());
        }

        itr = decl.iterateDeclaredAttributeUses();
        while (itr.hasNext()) {
            attributeUse((XSAttributeUse) itr.next());
        }

        this.currNode = (SchemaTreeNode) this.currNode.getParent();
    }

    /**
     * Creates node of attribute group decalration reference.
     *
     * @param decl Attribute group decalration reference.
     */
    public void dumpRef(XSAttGroupDecl decl) {
        SchemaTreeNode newNode = new SchemaTreeNode("Attribute group ref \"{"
                + decl.getTargetNamespace() + "}" + decl.getName() + "\"", decl
                .getLocator());
        this.currNode.add(newNode);
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSVisitor#attributeUse(com.sun.xml.internal.xsom.XSAttributeUse)
     */
    public void attributeUse(XSAttributeUse use) {
        XSAttributeDecl decl = use.getDecl();

        String additionalAtts = "";

        if (use.isRequired()) {
            additionalAtts += " use=\"required\"";
        }
        if (use.getFixedValue() != null
                && use.getDecl().getFixedValue() == null) {
            additionalAtts += " fixed=\"" + use.getFixedValue() + "\"";
        }
        if (use.getDefaultValue() != null
                && use.getDecl().getDefaultValue() == null) {
            additionalAtts += " default=\"" + use.getDefaultValue() + "\"";
        }

        if (decl.isLocal()) {
            // this is anonymous attribute use
            dump(decl, additionalAtts);
        }
        else {
            // reference to a global one
            String str = MessageFormat.format(
                    "Attribute ref \"'{'{0}'}'{1}{2}\"", new Object[]{
                        decl.getTargetNamespace(), decl.getName(),
                        additionalAtts});
            SchemaTreeNode newNode = new SchemaTreeNode(str, decl.getLocator());
            this.currNode.add(newNode);
        }
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSVisitor#attributeDecl(com.sun.xml.internal.xsom.XSAttributeDecl)
     */
    public void attributeDecl(XSAttributeDecl decl) {
        dump(decl, "");
    }

    /**
     * Creates node for attribute declaration with additional attributes.
     *
     * @param decl           Attribute declaration.
     * @param additionalAtts Additional attributes.
     */
    private void dump(XSAttributeDecl decl, String additionalAtts) {
        XSSimpleType type = decl.getType();

        String str = MessageFormat.format("Attribute \"{0}\"{1}{2}{3}{4}",
                new Object[]{
                    decl.getName(),
                    additionalAtts,
                    type.isLocal() ? "" : MessageFormat.format(
                            " type=\"'{'{0}'}'{1}\"", new Object[]{
                                type.getTargetNamespace(),
                                type.getName()}),
                    decl.getFixedValue() == null ? "" : " fixed=\""
                + decl.getFixedValue() + "\"",
                    decl.getDefaultValue() == null ? "" : " default=\""
                + decl.getDefaultValue() + "\""});

        SchemaTreeNode newNode = new SchemaTreeNode(str, decl.getLocator());
        this.currNode.add(newNode);
        this.currNode = newNode;

        if (type.isLocal()) {
            simpleType(type);
        }
        this.currNode = (SchemaTreeNode) this.currNode.getParent();
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSContentTypeVisitor#simpleType(com.sun.xml.internal.xsom.XSSimpleType)
     */
    public void simpleType(XSSimpleType type) {

        String str = MessageFormat.format("Simple type {0}",
                new Object[]{type.isLocal() ? "" : " name=\""
                + type.getName() + "\""});

        SchemaTreeNode newNode = new SchemaTreeNode(str, type.getLocator());
        this.currNode.add(newNode);
        this.currNode = newNode;

        type.visit((XSSimpleTypeVisitor) this);

        this.currNode = (SchemaTreeNode) this.currNode.getParent();
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSSimpleTypeVisitor#listSimpleType(com.sun.xml.internal.xsom.XSListSimpleType)
     */
    public void listSimpleType(XSListSimpleType type) {
        XSSimpleType itemType = type.getItemType();

        if (itemType.isLocal()) {
            SchemaTreeNode newNode = new SchemaTreeNode("List", type
                    .getLocator());
            this.currNode.add(newNode);
            this.currNode = newNode;
            simpleType(itemType);
            this.currNode = (SchemaTreeNode) this.currNode.getParent();
        }
        else {
            // global type
            String str = MessageFormat.format("List itemType=\"'{'{0}'}'{1}\"",
                    new Object[]{itemType.getTargetNamespace(),
                                 itemType.getName()});
            SchemaTreeNode newNode = new SchemaTreeNode(str, itemType
                    .getLocator());
            this.currNode.add(newNode);
        }
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSSimpleTypeVisitor#unionSimpleType(com.sun.xml.internal.xsom.XSUnionSimpleType)
     */
    public void unionSimpleType(XSUnionSimpleType type) {
        final int len = type.getMemberSize();
        StringBuffer ref = new StringBuffer();

        for (int i = 0; i < len; i++) {
            XSSimpleType member = type.getMember(i);
            if (member.isGlobal()) {
                ref.append(MessageFormat.format(" '{'{0}'}'{1}",
                        new Object[]{
                            member.getTargetNamespace(),
                            member.getName()}));
            }
        }

        String name = (ref.length() == 0) ? "Union" : ("Union memberTypes=\""
                + ref + "\"");
        SchemaTreeNode newNode = new SchemaTreeNode(name, type.getLocator());
        this.currNode.add(newNode);
        this.currNode = newNode;

        for (int i = 0; i < len; i++) {
            XSSimpleType member = type.getMember(i);
            if (member.isLocal()) {
                simpleType(member);
            }
        }
        this.currNode = (SchemaTreeNode) this.currNode.getParent();
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSSimpleTypeVisitor#restrictionSimpleType(com.sun.xml.internal.xsom.XSRestrictionSimpleType)
     */
    public void restrictionSimpleType(XSRestrictionSimpleType type) {

        if (type.getBaseType() == null) {
            // don't print anySimpleType
            if (!type.getName().equals("anySimpleType")) {
                throw new InternalError();
            }
            if (!Const.schemaNamespace.equals(type.getTargetNamespace())) {
                throw new InternalError();
            }
            return;
        }

        XSSimpleType baseType = type.getSimpleBaseType();

        String str = MessageFormat.format("Restriction {0}",
                new Object[]{baseType.isLocal() ? "" : " base=\"{"
                + baseType.getTargetNamespace() + "}"
                + baseType.getName() + "\""});

        SchemaTreeNode newNode = new SchemaTreeNode(str, baseType.getLocator());
        this.currNode.add(newNode);
        this.currNode = newNode;

        if (baseType.isLocal()) {
            simpleType(baseType);
        }

        Iterator itr = type.iterateDeclaredFacets();
        while (itr.hasNext()) {
            facet((XSFacet) itr.next());
        }

        this.currNode = (SchemaTreeNode) this.currNode.getParent();
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSVisitor#facet(com.sun.xml.internal.xsom.XSFacet)
     */
    public void facet(XSFacet facet) {
        SchemaTreeNode newNode = new SchemaTreeNode(MessageFormat.format(
                "{0} value=\"{1}\"", new Object[]{facet.getName(),
                                                  facet.getValue(), }),
                facet.getLocator());
        this.currNode.add(newNode);
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSVisitor#notation(com.sun.xml.internal.xsom.XSNotation)
     */
    public void notation(XSNotation notation) {
        SchemaTreeNode newNode = new SchemaTreeNode(MessageFormat.format(
                "Notation name='\"0}\" public =\"{1}\" system=\"{2}\"",
                new Object[]{notation.getName(), notation.getPublicId(),
                             notation.getSystemId()}), notation.getLocator());
        this.currNode.add(newNode);
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSVisitor#complexType(com.sun.xml.internal.xsom.XSComplexType)
     */
    public void complexType(XSComplexType type) {
        SchemaTreeNode newNode = new SchemaTreeNode(MessageFormat.format(
                "ComplexType {0}", new Object[]{type.isLocal() ? ""
                : " name=\"" + type.getName() + "\""}), type
                .getLocator());
        this.currNode.add(newNode);
        this.currNode = newNode;

        // TODO: wildcard

        if (type.getContentType().asSimpleType() != null) {
            // simple content
            SchemaTreeNode newNode2 = new SchemaTreeNode("Simple content", type
                    .getContentType().getLocator());
            this.currNode.add(newNode2);
            this.currNode = newNode2;

            XSType baseType = type.getBaseType();

            if (type.getDerivationMethod() == XSType.RESTRICTION) {
                // restriction
                String str = MessageFormat.format(
                        "Restriction base=\"<{0}>{1}\"", new Object[]{
                            baseType.getTargetNamespace(),
                            baseType.getName()});
                SchemaTreeNode newNode3 = new SchemaTreeNode(str, baseType
                        .getLocator());
                this.currNode.add(newNode3);
                this.currNode = newNode3;

                dumpComplexTypeAttribute(type);

                this.currNode = (SchemaTreeNode) this.currNode.getParent();
            }
            else {
                // extension
                String str = MessageFormat.format(
                        "Extension base=\"<{0}>{1}\"", new Object[]{
                            baseType.getTargetNamespace(),
                            baseType.getName()});
                SchemaTreeNode newNode3 = new SchemaTreeNode(str, baseType
                        .getLocator());
                this.currNode.add(newNode3);
                this.currNode = newNode3;

                // check if have redefine tag
                if ((type.getTargetNamespace().compareTo(
                        baseType.getTargetNamespace()) ==
                        0)
                        && (type.getName().compareTo(baseType.getName()) == 0)) {
                    SchemaTreeNode newNodeRedefine = new SchemaTreeNode(
                            "redefine", type
                            .getLocator());
                    this.currNode.add(newNodeRedefine);
                    this.currNode = newNodeRedefine;
                    baseType.visit(this);
                    this.currNode =
                            (SchemaTreeNode) newNodeRedefine.getParent();
                }

                dumpComplexTypeAttribute(type);

                this.currNode = (SchemaTreeNode) this.currNode.getParent();
            }

            this.currNode = (SchemaTreeNode) this.currNode.getParent();
        }
        else {
            // complex content
            SchemaTreeNode newNode2 = new SchemaTreeNode("Complex content",
                    type.getContentType().getLocator());
            this.currNode.add(newNode2);
            this.currNode = newNode2;

            XSComplexType baseType = type.getBaseType().asComplexType();

            if (type.getDerivationMethod() == XSType.RESTRICTION) {
                // restriction
                String str = MessageFormat.format(
                        "Restriction base=\"<{0}>{1}\"", new Object[]{
                            baseType.getTargetNamespace(),
                            baseType.getName()});
                SchemaTreeNode newNode3 = new SchemaTreeNode(str,
                        baseType.getLocator());
                this.currNode.add(newNode3);
                this.currNode = newNode3;

                type.getContentType().visit(this);
                dumpComplexTypeAttribute(type);

                this.currNode = (SchemaTreeNode) this.currNode.getParent();
            }
            else {
                // extension
                String str = MessageFormat.format(
                        "Extension base=\"'{'{0}'}'{1}\"", new Object[]{
                            baseType.getTargetNamespace(),
                            baseType.getName()});
                SchemaTreeNode newNode3 = new SchemaTreeNode(str,
                        baseType.getLocator());
                this.currNode.add(newNode3);
                this.currNode = newNode3;

                // check if have redefine tag
                if ((type.getTargetNamespace().compareTo(
                        baseType.getTargetNamespace()) ==
                        0)
                        && (type.getName().compareTo(baseType.getName()) == 0)) {
                    SchemaTreeNode newNodeRedefine = new SchemaTreeNode(
                            "redefine", type
                            .getLocator());
                    this.currNode.add(newNodeRedefine);
                    this.currNode = newNodeRedefine;
                    baseType.visit(this);
                    this.currNode =
                            (SchemaTreeNode) newNodeRedefine.getParent();
                }

                type.getExplicitContent().visit(this);
                dumpComplexTypeAttribute(type);

                this.currNode = (SchemaTreeNode) this.currNode.getParent();
            }

            this.currNode = (SchemaTreeNode) this.currNode.getParent();
        }

        this.currNode = (SchemaTreeNode) this.currNode.getParent();
    }

    /**
     * Creates node for complex type.
     *
     * @param type Complex type.
     */
    private void dumpComplexTypeAttribute(XSComplexType type) {
        Iterator itr;

        itr = type.iterateAttGroups();
        while (itr.hasNext()) {
            dumpRef((XSAttGroupDecl) itr.next());
        }

        itr = type.iterateDeclaredAttributeUses();
        while (itr.hasNext()) {
            attributeUse((XSAttributeUse) itr.next());
        }
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSTermVisitor#elementDecl(com.sun.xml.internal.xsom.XSElementDecl)
     */
    public void elementDecl(XSElementDecl decl) {
        elementDecl(decl, "");
    }

    /**
     * Creates node for element declaration with additional attributes.
     *
     * @param decl      Element declaration.
     * @param extraAtts Additional attributes.
     */
    private void elementDecl(XSElementDecl decl, String extraAtts) {
        XSType type = decl.getType();

        // TODO: various other attributes

        String str = MessageFormat.format("Element name=\"{0}\"{1}{2}",
                new Object[]{
                    decl.getName(),
                    type.isLocal() ? "" : " type=\"{"
                + type.getTargetNamespace() + "}"
                + type.getName() + "\"", extraAtts});

        SchemaTreeNode newNode = new SchemaTreeNode(str, decl.getLocator());
        this.currNode.add(newNode);
        this.currNode = newNode;

        if (type.isLocal()) {
            if (type.isLocal()) {
                type.visit(this);
            }
        }

        this.currNode = (SchemaTreeNode) this.currNode.getParent();
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSTermVisitor#modelGroupDecl(com.sun.xml.internal.xsom.XSModelGroupDecl)
     */
    public void modelGroupDecl(XSModelGroupDecl decl) {
        SchemaTreeNode newNode = new SchemaTreeNode(MessageFormat.format(
                "Group name=\"{0}\"", new Object[]{decl.getName()}),
                decl.getLocator());
        this.currNode.add(newNode);
        this.currNode = newNode;

        modelGroup(decl.getModelGroup());

        this.currNode = (SchemaTreeNode) this.currNode.getParent();
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSTermVisitor#modelGroup(com.sun.xml.internal.xsom.XSModelGroup)
     */
    public void modelGroup(XSModelGroup group) {
        modelGroup(group, "");
    }

    /**
     * Creates node for model group with additional attributes.
     *
     * @param group     Model group.
     * @param extraAtts Additional attributes.
     */
    private void modelGroup(XSModelGroup group, String extraAtts) {
        SchemaTreeNode newNode = new SchemaTreeNode(MessageFormat.format(
                "{0}{1}", new Object[]{group.getCompositor(), extraAtts}),
                group.getLocator());
        this.currNode.add(newNode);
        this.currNode = newNode;

        final int len = group.getSize();
        for (int i = 0; i < len; i++) {
            particle(group.getChild(i));
        }

        this.currNode = (SchemaTreeNode) this.currNode.getParent();
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSContentTypeVisitor#particle(com.sun.xml.internal.xsom.XSParticle)
     */
    public void particle(XSParticle part) {
        BigInteger i;

        StringBuffer buf = new StringBuffer();

        i = part.getMaxOccurs();
        if (i.equals(BigInteger.valueOf(XSParticle.UNBOUNDED))) {
            buf.append(" maxOccurs=\"unbounded\"");
        }
        else {
            if (!i.equals(BigInteger.ONE)) {
                buf.append(" maxOccurs=\"" + i + "\"");
            }
        }

        i = part.getMinOccurs();
        if (!i.equals(BigInteger.ONE)) {
            buf.append(" minOccurs=\"" + i + "\"");
        }

        final String extraAtts = buf.toString();

        part.getTerm().visit(new XSTermVisitor() {
            public void elementDecl(XSElementDecl decl) {
                if (decl.isLocal()) {
                    SchemaTreeTraverser.this.elementDecl(decl, extraAtts);
                }
                else {
                    // reference
                    SchemaTreeNode newNode = new SchemaTreeNode(MessageFormat
                            .format("Element ref=\"'{'{0}'}'{1}\"{2}",
                                    new Object[]{decl.getTargetNamespace(),
                                                 decl.getName(), extraAtts}),
                            decl.getLocator());
                    currNode.add(newNode);
                }
            }

            public void modelGroupDecl(XSModelGroupDecl decl) {
                // reference
                SchemaTreeNode newNode = new SchemaTreeNode(MessageFormat
                        .format("Group ref=\"'{'{0}'}'{1}\"{2}", new Object[]{
                            decl.getTargetNamespace(), decl.getName(),
                            extraAtts}), decl.getLocator());
                currNode.add(newNode);
            }

            public void modelGroup(XSModelGroup group) {
                SchemaTreeTraverser.this.modelGroup(group, extraAtts);
            }

            public void wildcard(XSWildcard wc) {
                SchemaTreeTraverser.this.wildcard(wc, extraAtts);
            }
        });
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSTermVisitor#wildcard(com.sun.xml.internal.xsom.XSWildcard)
     */
    public void wildcard(XSWildcard wc) {
        wildcard(wc, "");
    }

    /**
     * Creates node for wild card with additional attributes.
     *
     * @param wc        Wild card.
     * @param extraAtts Additional attributes.
     */
    private void wildcard(XSWildcard wc, String extraAtts) {
        // TODO
        SchemaTreeNode newNode = new SchemaTreeNode(MessageFormat.format(
                "Any ", new Object[]{extraAtts}), wc.getLocator());
        currNode.add(newNode);
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSVisitor#annotation(com.sun.xml.internal.xsom.XSAnnotation)
     */
    public void annotation(XSAnnotation ann) {
        // TODO: it would be nice even if we just put <xs:documentation>
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSContentTypeVisitor#empty(com.sun.xml.internal.xsom.XSContentType)
     */
    public void empty(XSContentType t) {
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSVisitor#identityConstraint(com.sun.xml.internal.xsom.XSIdentityConstraint)
     */
    public void identityConstraint(XSIdentityConstraint ic) {
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.xsom.visitor.XSVisitor#xpath(com.sun.xml.internal.xsom.XSXPath)
     */
    public void xpath(XSXPath xp) {
    }
}
