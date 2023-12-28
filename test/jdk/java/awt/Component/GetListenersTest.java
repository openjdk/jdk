/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4240721
  @summary Test Component.getListeners API added in 1.3
  @key headful
  @run main GetListenersTest
 */

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.CheckboxMenuItem;
import java.awt.Choice;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentListener;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusListener;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.event.WindowListener;
import java.awt.event.WindowStateListener;
import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.Introspector;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.EventListener;

public class GetListenersTest {

    public static void main(String args[]) throws Exception {
        EventQueue.invokeAndWait(()-> {
            // Create frame with a bunch of components
            // and test that each component returns
            // the right type of listeners from Component.getListeners
            GLTFrame gltFrame = new GLTFrame();
            try {
                gltFrame.initAndShowGui();
                gltFrame.test();
            } catch (Exception e) {
                throw new RuntimeException("Test failed", e);
            } finally {
                gltFrame.dispose();
            }
        });
    }

    /*
     * Checks an object has a listener for every support listener type
     */
    static void checkForListenersOfEveryType(Object object) throws Exception {
        Class type = object.getClass();

        BeanInfo info = Introspector.getBeanInfo(type);
        EventSetDescriptor esets[] = info.getEventSetDescriptors();

        // ensure there are listeners for every type
        for (int nset = 0; nset < esets.length; nset++) {
            Class listenerType = esets[nset].getListenerType();
            EventListener listener[] = getListeners(object, listenerType);
            // Skip PropertyChangeListener for now
            if (listener.length == 0 && validListenerToTest(listenerType)) {
                throw new RuntimeException("getListeners didn't return type "
                        + listenerType);
            }
        }

        System.out.println("************");
        System.out.println("PASSED: getListeners on "
                + object + " has all the right listeners.");
        System.out.println("************");
    }

    /*
     * Calls getListeners on the object
     */
    static EventListener[] getListeners(Object object, Class type)
            throws Exception {
        Method methods[] = object.getClass().getMethods();
        Method method = null;

        for (int nmethod = 0; nmethod < methods.length; nmethod++) {
            if (methods[nmethod].getName().equals("getListeners")) {
                method = methods[nmethod];
                break;
            }
        }
        if (method == null) {
            throw new RuntimeException("Object "
                    + object + " has no getListeners method");
        }
        Class params[] = {type};
        EventListener listeners[] = null;
        listeners = (EventListener[]) method.invoke(object, params);
        System.out.println("Listeners of type: " + type + " on " + object);
        GetListenersTest.printArray(listeners);
        return listeners;
    }

    /*
     * Adds a listener of every type to the object
     */
    static void addDummyListenersOfEveryType(Object object) throws Exception {
        Class type = object.getClass();

        BeanInfo info = Introspector.getBeanInfo(type);
        EventSetDescriptor esets[] = info.getEventSetDescriptors();

        // add every kind of listener
        for (int nset = 0; nset < esets.length; nset++) {
            Class listenerType = esets[nset].getListenerType();
            EventListener listener = makeListener(listenerType);
            Method addListenerMethod = esets[nset].getAddListenerMethod();
            Object params[] = {listener};
            addListenerMethod.invoke(object, params);
        }
    }

    /*
     * Determines what listeners to exclude from the test for now
     */
    static boolean validListenerToTest(Class listenerType) {
        /* Don't have any provision for PropertyChangeListeners... */
        if ( listenerType == PropertyChangeListener.class ) {
            return false;
        }

        return true;
    }

    static void testGetListeners(Object object) throws Exception {
        GetListenersTest.addDummyListenersOfEveryType(object);
        GetListenersTest.checkForListenersOfEveryType(object);
    }

    static void printArray(Object objects[]) {
        System.out.println("{");
        for(int n = 0; n < objects.length; n++) {
            System.out.println("\t"+objects[n]+",");
        }
        System.out.println("}");
    }

    /*
     * Makes a dummy listener implementation for the given listener type
     */
    static EventListener makeListener(Class listenerType) throws Exception {
        Object map[][] = {
                {ActionListener.class, MyActionAdapter.class},
                {AdjustmentListener.class, MyAdjustmentAdapter.class},
                {ComponentListener.class, MyComponentAdapter.class},
                {ContainerListener.class, MyContainerAdapter.class},
                {FocusListener.class, MyFocusAdapter.class},
                {HierarchyBoundsListener.class, MyHierarchyBoundsAdapter.class},
                {HierarchyListener.class, MyHierarchyAdapter.class},
                {InputMethodListener.class, MyInputMethodAdapter.class},
                {ItemListener.class, MyItemAdapter.class},
                {KeyListener.class, MyKeyAdapter.class},
                {MouseListener.class, MyMouseAdapter.class},
                {MouseMotionListener.class, MyMouseMotionAdapter.class},
                {MouseWheelListener.class, MyMouseWheelAdapter.class},
                {TextListener.class, MyTextAdapter.class},
                {WindowListener.class, MyWindowAdapter.class},
                {WindowFocusListener.class, MyWindowFocusAdapter.class},
                {WindowStateListener.class, MyWindowStateAdapter.class},
                {PropertyChangeListener.class, MyPropertyChangeAdapter.class},
        };

        for (int n = 0; n < map.length; n++) {
            if (map[n][0] == listenerType) {
                Class adapterClass = (Class) map[n][1];
                EventListener listener =
                        (EventListener) adapterClass.newInstance();
                return listener;
            }
        }

        throw new RuntimeException("No adapter found for listener type "
                + listenerType);
    }
}

class GLTFrame extends Frame {
    MenuItem mitem;
    CheckboxMenuItem cmitem;

    GLTFrame() {
        super("Component.getListeners API Test");
    }

    public void initAndShowGui() {
        setLayout(new FlowLayout());

        add(new Label("Label"));
        add(new Button("Button"));
        add(new Checkbox("Checkbox"));
        Choice c = new Choice();
        c.add("choice");
        java.awt.List l = new java.awt.List();
        l.add("list");
        add(new Scrollbar());
        add(new TextField("TextField"));
        add(new TextArea("TextArea"));
        add(new Panel());
        add(new Canvas());

        MenuBar menuBar = new MenuBar();
        Menu menu = new Menu("Menu");
        mitem = new MenuItem("Item 1");
        cmitem = new CheckboxMenuItem("Item 2");
        menu.add(mitem);
        menu.add(cmitem);
        menuBar.add(menu);
        setMenuBar(menuBar);

        pack();
        setVisible(true);
    }

    public void test() throws Exception {
        // test Frame.getListeners
        GetListenersTest.testGetListeners(this);

        //
        // test getListeners on menu items
        //
        GetListenersTest.testGetListeners(mitem);
        GetListenersTest.testGetListeners(cmitem);

        //
        // test getListeners on all AWT Components
        //
        Component components[] = getComponents();
        for (int nc = 0; nc < components.length; nc++) {
            GetListenersTest.testGetListeners(components[nc]);
        }
    }
}

/************************************************
 * Dummy listener implementations we add to our components/models/objects
 */

class MyPropertyChangeAdapter implements PropertyChangeListener {
    public void propertyChange(PropertyChangeEvent evt) {}
}

class MyActionAdapter implements ActionListener {
    public void actionPerformed(ActionEvent ev) {
    }
}

class MyAdjustmentAdapter implements AdjustmentListener {
    public void adjustmentValueChanged(AdjustmentEvent e) {
    }
}

class MyHierarchyAdapter implements HierarchyListener {
    public void hierarchyChanged(HierarchyEvent e) {
    }
}

class MyInputMethodAdapter implements InputMethodListener {
    public void inputMethodTextChanged(InputMethodEvent event) {
    }

    public void caretPositionChanged(InputMethodEvent event) {
    }
}

class MyItemAdapter implements ItemListener {
    public void itemStateChanged(ItemEvent e) {
    }
}

class MyTextAdapter implements TextListener {
    public void textValueChanged(TextEvent e) {
    }
}

class MyComponentAdapter extends ComponentAdapter {
}

class MyContainerAdapter extends ContainerAdapter {
}

class MyFocusAdapter extends FocusAdapter {
}

class MyHierarchyBoundsAdapter extends HierarchyBoundsAdapter {
}

class MyKeyAdapter extends KeyAdapter {
}

class MyMouseAdapter extends MouseAdapter {
}

class MyMouseMotionAdapter extends MouseMotionAdapter {
}

class MyMouseWheelAdapter implements MouseWheelListener {
    public void mouseWheelMoved(MouseWheelEvent e) {}
}

class MyWindowAdapter extends WindowAdapter {
}

class MyWindowFocusAdapter implements WindowFocusListener {
    public void windowGainedFocus(WindowEvent t) {}
    public void windowLostFocus(WindowEvent t) {}
}

class MyWindowStateAdapter extends WindowAdapter {
}
