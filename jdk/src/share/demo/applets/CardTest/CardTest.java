/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


import java.applet.Applet;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.CardLayout;
import java.awt.Choice;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;


@SuppressWarnings("serial")
final class CardPanel extends Panel {

    ActionListener listener;

    Panel create(LayoutManager layout) {
        Button b = null;
        Panel p = new Panel();

        p.setLayout(layout);

        b = new Button("one");
        b.addActionListener(listener);
        p.add("North", b);

        b = new Button("two");
        b.addActionListener(listener);
        p.add("West", b);

        b = new Button("three");
        b.addActionListener(listener);
        p.add("South", b);

        b = new Button("four");
        b.addActionListener(listener);
        p.add("East", b);

        b = new Button("five");
        b.addActionListener(listener);
        p.add("Center", b);

        b = new Button("six");
        b.addActionListener(listener);
        p.add("Center", b);

        return p;
    }

    CardPanel(ActionListener actionListener) {
        listener = actionListener;
        setLayout(new CardLayout());
        add("one", create(new FlowLayout()));
        add("two", create(new BorderLayout()));
        add("three", create(new GridLayout(2, 2)));
        add("four", create(new BorderLayout(10, 10)));
        add("five", create(new FlowLayout(FlowLayout.LEFT, 10, 10)));
        add("six", create(new GridLayout(2, 2, 10, 10)));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, 100);
    }
}


@SuppressWarnings("serial")
public class CardTest extends Applet
        implements ActionListener,
        ItemListener {

    CardPanel cards;

    @SuppressWarnings("LeakingThisInConstructor")
    public CardTest() {
        setLayout(new BorderLayout());
        add("Center", cards = new CardPanel(this));
        Panel p = new Panel();
        p.setLayout(new FlowLayout());
        add("South", p);

        Button b = new Button("first");
        b.addActionListener(this);
        p.add(b);

        b = new Button("next");
        b.addActionListener(this);
        p.add(b);

        b = new Button("previous");
        b.addActionListener(this);
        p.add(b);

        b = new Button("last");
        b.addActionListener(this);
        p.add(b);

        Choice c = new Choice();
        c.addItem("one");
        c.addItem("two");
        c.addItem("three");
        c.addItem("four");
        c.addItem("five");
        c.addItem("six");
        c.addItemListener(this);
        p.add(c);
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        ((CardLayout) cards.getLayout()).show(cards,
                (String) (e.getItem()));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String arg = e.getActionCommand();

        if ("first".equals(arg)) {
            ((CardLayout) cards.getLayout()).first(cards);
        } else if ("next".equals(arg)) {
            ((CardLayout) cards.getLayout()).next(cards);
        } else if ("previous".equals(arg)) {
            ((CardLayout) cards.getLayout()).previous(cards);
        } else if ("last".equals(arg)) {
            ((CardLayout) cards.getLayout()).last(cards);
        } else {
            ((CardLayout) cards.getLayout()).show(cards, arg);
        }
    }

    public static void main(String args[]) {
        Frame f = new Frame("CardTest");
        CardTest cardTest = new CardTest();
        cardTest.init();
        cardTest.start();

        f.add("Center", cardTest);
        f.setSize(300, 300);
        f.setVisible(true);
    }

    @Override
    public String getAppletInfo() {
        return "Demonstrates the different types of layout managers.";
    }
}
