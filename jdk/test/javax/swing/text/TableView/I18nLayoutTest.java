/*
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8133864
 * @summary  Wrong display, when the document I18n properties is true.
 * @author Semyon Sadetsky
 * @run main I18nLayoutTest
 */

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.ArrayList;

public class I18nLayoutTest extends JFrame {

    private static int height;
    JEditorPane edit = new JEditorPane();
    private static I18nLayoutTest frame;

    public I18nLayoutTest() {
        super("Code example for a TableView bug");
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        edit.setEditorKit(new CodeBugEditorKit());
        initCodeBug();
        this.getContentPane().add(new JScrollPane(edit));
        this.pack();
        this.setLocationRelativeTo(null);

    }

    private void initCodeBug() {
        CodeBugDocument doc = (CodeBugDocument) edit.getDocument();
        try {
            doc.insertString(0, "TextB TextE", null);
        } catch (BadLocationException ex) {
        }
        doc.insertTable(6, 4, 3);
        try {
            doc.insertString(7, "Cell11", null);
            doc.insertString(14, "Cell12", null);
            doc.insertString(21, "Cell13", null);
            doc.insertString(28, "Cell21", null);
            doc.insertString(35, "Cell22", null);
            doc.insertString(42, "Cell23", null);
            doc.insertString(49, "Cell31", null);
            doc.insertString(56, "Cell32", null);
            doc.insertString(63, "Cell33", null);
            doc.insertString(70, "Cell41", null);
            doc.insertString(77, "Cell42", null);
            doc.insertString(84, "Cell43", null);
        } catch (BadLocationException ex) {
        }
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                frame = new I18nLayoutTest();
                frame.setVisible(true);
            }
        });
        Robot robot = new Robot();
        robot.delay(200);
        robot.waitForIdle();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                height = frame.getHeight();
            }
        });
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                frame.dispose();
            }
        });
        if (height < 32) {
            throw new RuntimeException(
                    "TableView layout height is wrong " + height);
        }
        System.out.println("ok");
    }
}

//------------------------------------------------------------------------------
class CodeBugEditorKit extends StyledEditorKit {

    ViewFactory defaultFactory = new TableFactory();

    @Override
    public ViewFactory getViewFactory() {
        return defaultFactory;
    }

    @Override
    public Document createDefaultDocument() {
        return new CodeBugDocument();
    }
}
//------------------------------------------------------------------------------

class TableFactory implements ViewFactory {

    @Override
    public View create(Element elem) {
        String kind = elem.getName();
        if (kind != null) {
            if (kind.equals(AbstractDocument.ContentElementName)) {
                return new LabelView(elem);
            } else if (kind.equals(AbstractDocument.ParagraphElementName)) {
                return new ParagraphView(elem);
            } else if (kind.equals(AbstractDocument.SectionElementName)) {
                return new BoxView(elem, View.Y_AXIS);
            } else if (kind.equals(StyleConstants.ComponentElementName)) {
                return new ComponentView(elem);
            } else if (kind.equals(CodeBugDocument.ELEMENT_TABLE)) {
                return new tableView(elem);
            } else if (kind.equals(StyleConstants.IconElementName)) {
                return new IconView(elem);
            }
        }
        // default to text display
        return new LabelView(elem);

    }
}
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
class tableView extends TableView implements ViewFactory {

    public tableView(Element elem) {
        super(elem);
    }

    @Override
    public void setParent(View parent) {
        super.setParent(parent);
    }

    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);
    }

    @Override
    public ViewFactory getViewFactory() {
        return this;
    }

    @Override
    public float getMinimumSpan(int axis) {
        return getPreferredSpan(axis);
    }

    @Override
    public float getMaximumSpan(int axis) {
        return getPreferredSpan(axis);
    }

    @Override
    public float getAlignment(int axis) {
        return 0.5f;
    }

    @Override
    public float getPreferredSpan(int axis) {
        if (axis == 0) return super.getPreferredSpan(0);
        float preferredSpan = super.getPreferredSpan(axis);
        return preferredSpan;
    }

    @Override
    public void paint(Graphics g, Shape allocation) {
        super.paint(g, allocation);
        Rectangle alloc = allocation.getBounds();
        int lastY = alloc.y + alloc.height - 1;
        g.drawLine(alloc.x, lastY, alloc.x + alloc.width, lastY);
    }

    @Override
    protected void paintChild(Graphics g, Rectangle alloc, int index) {
        super.paintChild(g, alloc, index);
        int lastX = alloc.x + alloc.width;
        g.drawLine(alloc.x, alloc.y, lastX, alloc.y);
    }

    @Override
    public View create(Element elem) {
        String kind = elem.getName();
        if (kind != null) {
            if (kind.equals(CodeBugDocument.ELEMENT_TR)) {
                return new trView(elem);
            } else if (kind.equals(CodeBugDocument.ELEMENT_TD)) {
                return new BoxView(elem, View.Y_AXIS);

            }
        }

        // default is to delegate to the normal factory
        View p = getParent();
        if (p != null) {
            ViewFactory f = p.getViewFactory();
            if (f != null) {
                return f.create(elem);
            }
        }

        return null;
    }

    public class trView extends TableRow {
        @Override
        public void setParent(View parent) {
            super.setParent(parent);
        }

        public trView(Element elem) {
            super(elem);
        }

        public float getMinimumSpan(int axis) {
            return getPreferredSpan(axis);
        }

        public float getMaximumSpan(int axis) {
            return getPreferredSpan(axis);
        }

        public float getAlignment(int axis) {
            return 0f;
        }

        @Override
        protected void paintChild(Graphics g, Rectangle alloc, int index) {
            super.paintChild(g, alloc, index);
            int lastY = alloc.y + alloc.height - 1;
            g.drawLine(alloc.x, alloc.y, alloc.x, lastY);
            int lastX = alloc.x + alloc.width;
            g.drawLine(lastX, alloc.y, lastX, lastY);
        }
    }

    ;
}

//------------------------------------------------------------------------------
class CodeBugDocument extends DefaultStyledDocument {

    public static final String ELEMENT_TABLE = "table";
    public static final String ELEMENT_TR = "table cells row";
    public static final String ELEMENT_TD = "table data cell";

    public CodeBugDocument() {
        putProperty("i18n", Boolean.TRUE);
    }


    protected void insertTable(int offset, int rowCount, int colCount) {
        try {
            ArrayList Specs = new ArrayList();
            ElementSpec gapTag = new ElementSpec(new SimpleAttributeSet(),
                    ElementSpec.ContentType, "\n".toCharArray(), 0, 1);
            Specs.add(gapTag);

            SimpleAttributeSet tableAttrs = new SimpleAttributeSet();
            tableAttrs.addAttribute(ElementNameAttribute, ELEMENT_TABLE);
            ElementSpec tableStart =
                    new ElementSpec(tableAttrs, ElementSpec.StartTagType);
            Specs.add(tableStart); //start table tag


            fillRowSpecs(Specs, rowCount, colCount);

            ElementSpec[] spec = new ElementSpec[Specs.size()];
            Specs.toArray(spec);

            this.insert(offset, spec);
        } catch (BadLocationException ex) {
        }
    }

    protected void fillRowSpecs(ArrayList Specs, int rowCount, int colCount) {
        SimpleAttributeSet rowAttrs = new SimpleAttributeSet();
        rowAttrs.addAttribute(ElementNameAttribute, ELEMENT_TR);
        for (int i = 0; i < rowCount; i++) {
            ElementSpec rowStart =
                    new ElementSpec(rowAttrs, ElementSpec.StartTagType);
            Specs.add(rowStart);

            fillCellSpecs(Specs, colCount);

            ElementSpec rowEnd =
                    new ElementSpec(rowAttrs, ElementSpec.EndTagType);
            Specs.add(rowEnd);
        }

    }

    protected void fillCellSpecs(ArrayList Specs, int colCount) {
        for (int i = 0; i < colCount; i++) {
            SimpleAttributeSet cellAttrs = new SimpleAttributeSet();
            cellAttrs.addAttribute(ElementNameAttribute, ELEMENT_TD);

            ElementSpec cellStart =
                    new ElementSpec(cellAttrs, ElementSpec.StartTagType);
            Specs.add(cellStart);

            ElementSpec parStart = new ElementSpec(new SimpleAttributeSet(),
                    ElementSpec.StartTagType);
            Specs.add(parStart);
            ElementSpec parContent = new ElementSpec(new SimpleAttributeSet(),
                    ElementSpec.ContentType, "\n".toCharArray(), 0, 1);
            Specs.add(parContent);
            ElementSpec parEnd = new ElementSpec(new SimpleAttributeSet(),
                    ElementSpec.EndTagType);
            Specs.add(parEnd);
            ElementSpec cellEnd =
                    new ElementSpec(cellAttrs, ElementSpec.EndTagType);
            Specs.add(cellEnd);
        }
    }
}