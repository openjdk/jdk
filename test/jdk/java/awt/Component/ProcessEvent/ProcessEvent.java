/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4292099
 * @summary AWT Event delivery to processEvent
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ProcessEvent
 */

import java.awt.AWTEvent;
import java.awt.AWTEventMulticaster;
import java.awt.Adjustable;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.ItemSelectable;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.lang.reflect.InvocationTargetException;

public class ProcessEvent extends Frame {

    static final String INSTRUCTIONS = """
                Press each of the four buttons for ActionEvent, AdjustmentEvent,
                ItemEvent and TextEvent. If a message for each corresponding event
                appears in the log area and says the event listener was
                called, then press Pass otherwise press Fail.
            """;
    ActionBtn af;
    AdjustmentBtn adjf;
    ItemBtn itf;
    TextBtn txtf;

    public ProcessEvent() {
        setLayout(new FlowLayout());
        add(af = new ActionBtn());
        af.setBackground(Color.green);

        add(adjf = new AdjustmentBtn());
        adjf.setBackground(Color.green);

        add(itf = new ItemBtn());
        itf.setBackground(Color.green);

        add(txtf = new TextBtn());
        txtf.setBackground(Color.green);

        // These action listeners simply provide feedback of when
        // the event is delivered properly.
        af.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                PassFailJFrame.log(af.getText()
                        + ": action listener called: "
                        + ae.toString());
            }
        });

        adjf.addAdjustmentListener(new AdjustmentListener() {
            public void adjustmentValueChanged(AdjustmentEvent ae) {
                PassFailJFrame.log(adjf.getText()
                        + ": adjustment listener called: "
                        + ae.toString());
            }
        });

        itf.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                PassFailJFrame.log(itf.getText()
                        + ": item listener called: "
                        + e.toString());
            }
        });

        txtf.addTextListener(new TextListener() {
            public void textValueChanged(TextEvent e) {
                PassFailJFrame.log(txtf.getText()
                        + ": text listener called: "
                        + e.toString());
            }
        });

        pack();
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Process Events Test")
                .testUI(ProcessEvent::new)
                .instructions(INSTRUCTIONS)
                .columns(40)
                .logArea()
                .build()
                .awaitAndCheck();
    }
}

class ButtonComponent extends Component implements ItemSelectable, Adjustable {

    transient protected TextListener textListener;
    transient ActionListener actionListener;
    transient AdjustmentListener adjustmentListener;
    transient ItemListener itemListener;
    String actionCommand = null;

    String text = null;

    public ButtonComponent(String label) {
        super();
        text = label;
    }

    public String getText() {
        return text;
    }

    public Dimension getPreferredSize() {
        return new Dimension(200, 30);
    }

    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    public String getActionCommand() {
        if (actionCommand == null)
            return getText();
        else
            return actionCommand;
    }

    public void setActionCommand(String ac) {
        actionCommand = ac;
    }

    // ActionEvent listener support

    public synchronized void addActionListener(ActionListener l) {
        if (l == null) {
            return;
        }
        enableEvents(AWTEvent.ACTION_EVENT_MASK);
        actionListener = AWTEventMulticaster.add(actionListener, l);
    }

    public synchronized void removeActionListener(ActionListener l) {
        if (l == null) {
            return;
        }
        actionListener = AWTEventMulticaster.remove(actionListener, l);
    }

    // AdjustmentEvent listener support

    public synchronized void addAdjustmentListener(AdjustmentListener l) {
        if (l == null) {
            return;
        }
        enableEvents(AWTEvent.ADJUSTMENT_EVENT_MASK);
        adjustmentListener = AWTEventMulticaster.add(adjustmentListener, l);
    }

    public synchronized void removeAdjustmentListener(AdjustmentListener l) {
        if (l == null) {
            return;
        }
        adjustmentListener = AWTEventMulticaster.remove(adjustmentListener, l);
    }

    // ItemEvent listener support

    public synchronized void addItemListener(ItemListener l) {
        if (l == null) {
            return;
        }
        enableEvents(AWTEvent.ITEM_EVENT_MASK);
        itemListener = AWTEventMulticaster.add(itemListener, l);
    }

    public synchronized void removeItemListener(ItemListener l) {
        if (l == null) {
            return;
        }
        itemListener = AWTEventMulticaster.remove(itemListener, l);
    }

    // TextEvent listener support

    public synchronized void addTextListener(TextListener l) {
        if (l == null) {
            return;
        }
        enableEvents(AWTEvent.TEXT_EVENT_MASK);
        textListener = AWTEventMulticaster.add(textListener, l);
    }

    public synchronized void removeTextListener(TextListener l) {
        if (l == null) {
            return;
        }
        textListener = AWTEventMulticaster.remove(textListener, l);
    }

    // Implement the processEvent and processXXXEvent methods to
    // handle reception and processing of the event types.

    protected void processEvent(AWTEvent e) {
        if (e instanceof ActionEvent) {
            processActionEvent((ActionEvent) e);
            return;
        }
        if (e instanceof AdjustmentEvent) {
            processAdjustmentEvent((AdjustmentEvent) e);
            return;
        }
        if (e instanceof ItemEvent) {
            processItemEvent((ItemEvent) e);
            return;
        }
        if (e instanceof TextEvent) {
            processTextEvent((TextEvent) e);
            return;
        }
        super.processEvent(e);
    }

    protected void processActionEvent(ActionEvent e) {
        if (actionListener != null) {
            actionListener.actionPerformed(e);
        }
    }

    protected void processAdjustmentEvent(AdjustmentEvent e) {
        if (adjustmentListener != null) {
            adjustmentListener.adjustmentValueChanged(e);
        }
    }

    protected void processItemEvent(ItemEvent e) {
        if (itemListener != null) {
            itemListener.itemStateChanged(e);
        }
    }

    protected void processTextEvent(TextEvent e) {
        if (textListener != null) {
            textListener.textValueChanged(e);
        }
    }

    public void paint(Graphics g) {
        Dimension dim = getSize();
        g.clearRect(0, 0, dim.width, dim.height);
        g.setColor(getForeground());
        g.drawString(text, 2, dim.height - 2);
    }

    /**
     * Returns the selected items or null if no items are selected.
     */
    public Object[] getSelectedObjects() {
        return null;
    }

    /**
     * Gets the orientation of the adjustable object.
     */
    public int getOrientation() {
        return 0;
    }

    /**
     * Gets the minimum value of the adjustable object.
     */
    public int getMinimum() {
        return 0;
    }

    /**
     * Sets the minimum value of the adjustable object.
     *
     * @param min the minimum value
     */
    public void setMinimum(int min) {
    }

    /**
     * Gets the maximum value of the adjustable object.
     */
    public int getMaximum() {
        return 0;
    }

    /**
     * Sets the maximum value of the adjustable object.
     *
     * @param max the maximum value
     */
    public void setMaximum(int max) {
    }

    /**
     * Gets the unit value increment for the adjustable object.
     */
    public int getUnitIncrement() {
        return 0;
    }

    /**
     * Sets the unit value increment for the adjustable object.
     *
     * @param u the unit increment
     */
    public void setUnitIncrement(int u) {
    }

    /**
     * Gets the block value increment for the adjustable object.
     */
    public int getBlockIncrement() {
        return 0;
    }

    /**
     * Sets the block value increment for the adjustable object.
     *
     * @param b the block increment
     */
    public void setBlockIncrement(int b) {
    }

    /**
     * Gets the length of the propertional indicator.
     */
    public int getVisibleAmount() {
        return 0;
    }

    /**
     * Sets the length of the proportionl indicator of the
     * adjustable object.
     *
     * @param v the length of the indicator
     */
    public void setVisibleAmount(int v) {
    }

    /**
     * Gets the current value of the adjustable object.
     */
    public int getValue() {
        return 0;
    }

    /**
     * Sets the current value of the adjustable object. This
     * value must be within the range defined by the minimum and
     * maximum values for this object.
     *
     * @param v the current value
     */
    public void setValue(int v) {
    }

}

class ActionBtn extends ButtonComponent {
    public ActionBtn() {
        super("ActionEvent");
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                ActionEvent ae = new ActionEvent(e.getSource(),
                        ActionEvent.ACTION_PERFORMED,
                        getActionCommand());
                Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(ae);
            }
        });
    }
}

class AdjustmentBtn extends ButtonComponent {
    public AdjustmentBtn() {
        super("AdjustmentEvent");
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                AdjustmentEvent ae = new AdjustmentEvent((Adjustable) e.getSource(),
                        AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED,
                        1, 1);
                Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(ae);
            }
        });
    }
}

class ItemBtn extends ButtonComponent {
    public ItemBtn() {
        super("ItemEvent");
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                ItemEvent ae = new ItemEvent((ItemSelectable) e.getSource(),
                        ItemEvent.ITEM_STATE_CHANGED,
                        e.getSource(), 1);
                Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(ae);
            }
        });
    }
}

class TextBtn extends ButtonComponent {
    public TextBtn() {
        super("TextEvent");
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                TextEvent ae = new TextEvent(e.getSource(),
                        TextEvent.TEXT_VALUE_CHANGED);
                Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(ae);
            }
        });
    }
}
