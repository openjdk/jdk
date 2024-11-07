
import java.awt.event.*;
import java.awt.*;

/*
 * @test
 * @bug 8342782
 * @summary Tests large AWTEventMulticasters for StackOverflowErrors
 * @run main LargeAWTEventMulticasterTest
 */
public class LargeAWTEventMulticasterTest {

    /**
     * This is an empty ActionListener that also has a numeric index.
     */
    static class IndexedActionListener implements ActionListener {
        private final int index;

        public IndexedActionListener(int index) {
            this.index = index;
        }

        @Override
        public void actionPerformed(ActionEvent e) {

        }

        public int getIndex() {
            return index;
        }

        @Override
        public String toString() {
            return Integer.toString(index);
        }
    }

    public static void main(String[] args) {
        int maxA = 0;
        try {
            for (int a = 1; a < 200_000; a *= 2) {
                maxA = a;
                testAddingActionListener(a);
            }
        } finally {
            System.out.println("maximum a = " + maxA);
        }
    }

    private static void testAddingActionListener(int numberOfListeners) {
        // step 1: create the large AWTEventMulticaster
        ActionListener l = null;
        for (int a = 0; a < numberOfListeners; a++) {
            l = AWTEventMulticaster.add(l, new IndexedActionListener(a));
        }

        // Prior to 8342782 we could CREATE a large AWTEventMulticaster, but we couldn't
        // always interact with it.

        // step 2: dispatch an event
        // Here we're making sure we don't get a StackOverflowError when we traverse the tree:
        l.actionPerformed(null);

        // step 3: make sure getListeners() returns elements in the correct order
        // The resolution for 8342782 introduced a `rebalance` method; we want to
        // double-check that the rebalanced tree preserves the appropriate order.
        IndexedActionListener[] array = AWTEventMulticaster.getListeners(l, IndexedActionListener.class);
        for (int b = 0; b < array.length; b++) {
            if (b != array[b].getIndex())
                throw new Error("the listeners are in the wrong order. " + b + " != " + array[b].getIndex());
        }
    }
}