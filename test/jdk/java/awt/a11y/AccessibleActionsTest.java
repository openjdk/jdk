/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, JetBrains s.r.o.. All rights reserved.
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
 * @bug 8281338
 * @summary Test for an element that has more than one Accessibility Action
 * @author Artem.Semenov@jetbrains.com
 * @run main/manual AccessibleActionsTest
 * @requires (os.family == "mac")
 */

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;
import java.util.concurrent.CountDownLatch;

public class AccessibleActionsTest extends AccessibleComponentTest {

  @Override
  public CountDownLatch createCountDownLatch() {
    return new CountDownLatch(1);
  }

  void createTest() {
    INSTRUCTIONS = "INSTRUCTIONS:\n"
            + "Check a11y actions.\n\n"
            + "Turn screen reader on, and Tab to the label.\n\n"
            + "Perform the VO action \"Press\" (VO+space)\n"
            + "Perform the VO action \"Show menu\" (VO+m)\n\n"
            + "If after the first action the text of the label has changed, and after the second action the menu appears  tab further and press PASS, otherwise press FAIL.";

    exceptionString = "AccessibleAction test failed!";
    super.createUI(new AccessibleActionsTestFrame(), "AccessibleActionsTest");
  }

  void createTree() {
    INSTRUCTIONS = "INSTRUCTIONS:\n"
            + "Check a11y actions.\n\n"
            + "Turn screen reader on, and Tab to the label.\n\n"
            + "Perform the VO action \"Press\" (VO+space) on tree nodes\n\n"
            + "If after press the tree node is expanded  tab further and press PASS, otherwise press FAIL.";

    String root = "Root";
    String[] nodes = new String[] {"One node", "Two node"};
    String[][] leafs = new String[][]{{"leaf 1.1", "leaf 1.2", "leaf 1.3", "leaf 1.4"},
            {"leaf 2.1", "leaf 2.2", "leaf 2.3", "leaf 2.4"}};

    Hashtable<String, String[]> data = new Hashtable<String, String[]>();
    for (int i = 0; i < nodes.length; i++) {
      data.put(nodes[i], leafs[i]);
    }

    JTree tree = new JTree(data);
    tree.setRootVisible(true);

    JPanel panel = new JPanel();
    panel.setLayout(new FlowLayout());
    JScrollPane scrollPane = new JScrollPane(tree);
    panel.add(scrollPane);
    panel.setFocusable(false);

    exceptionString = "AccessibleAction test failed!";
    super.createUI(panel, "AccessibleActionsTest");
  }

  public static void main(String[] args) throws Exception {
    AccessibleActionsTest test = new AccessibleActionsTest();

    countDownLatch = test.createCountDownLatch();
    SwingUtilities.invokeLater(test::createTest);
    countDownLatch.await();

    if (!testResult) {
      throw new RuntimeException(a11yTest.exceptionString);
    }

    countDownLatch = test.createCountDownLatch();
    SwingUtilities.invokeLater(test::createTree);
    countDownLatch.await();

    if (!testResult) {
      throw new RuntimeException(a11yTest.exceptionString);
    }
  }

  private class AccessibleActionsTestFrame extends JPanel {

    public AccessibleActionsTestFrame() {
      MyLabel label = new MyLabel("I'm waiting for the push");
      label.setComponentPopupMenu(createPopup());
      label.setFocusable(true);
      add(label);
      setLayout(new FlowLayout());
    }

    private static class MyLabel extends JLabel {
      public MyLabel(String text) {
        super(text);
      }

      @Override
      public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
          accessibleContext = new MyAccessibleJLabel();
        }
        return accessibleContext;
      }

      private class MyAccessibleJLabel extends JLabel.AccessibleJLabel {
        @Override
        public AccessibleAction getAccessibleAction() {
          return new AccessibleAction() {
            @Override
            public int getAccessibleActionCount() {
              return 2;
            }

            @Override
            public String getAccessibleActionDescription(int i) {
              if (i == 0) {
                return AccessibleAction.CLICK;
              }
              return AccessibleAction.TOGGLE_POPUP;
            }

            @Override
            public boolean doAccessibleAction(int i) {
              if (i == 0) {
                changeText(MyLabel.this, "label is pressed");
                return true;
              }
              JPopupMenu popup = createPopup();
              popup.show(MyLabel.this, 0, 0);
              return true;
            }
          };
        }
      }
    }

    private static JPopupMenu createPopup() {
      JPopupMenu popup = new JPopupMenu("MENU");
      popup.add("One");
      popup.add("Two");
      popup.add("Three");
      return popup;
    }

    private static void changeText(JLabel label, String text) {
      label.setText(text);
    }

  }
}
