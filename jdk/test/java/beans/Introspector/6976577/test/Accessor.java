package test;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.EventListener;
import java.util.TooManyListenersException;

public class Accessor {

    public static Class<?> getBeanType() {
        return Bean.class;
    }

    public static Class<?> getListenerType() {
        return TestListener.class;
    }
}

interface TestEvent {
}

interface TestListener extends EventListener {
    void process(TestEvent event);
}

class Bean {

    private boolean b;
    private int[] indexed;
    private TestListener listener;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    public void addTestListener(TestListener listener) throws TooManyListenersException {
        if (listener != null) {
            if (this.listener != null) {
                throw new TooManyListenersException();
            }
            this.listener = listener;
        }
    }

    public void removeTestListener(TestListener listener) {
        if (this.listener == listener) {
            this.listener = null;
        }
    }

    public TestListener[] getTestListeners() {
        return (this.listener != null)
                ? new TestListener[] { this.listener }
                : new TestListener[0];
    }

    public boolean isBoolean() {
        return this.b;
    }

    public void setBoolean(boolean b) {
        this.b = b;
    }

    public int[] getIndexed() {
        return this.indexed;
    }

    public void setIndexed(int[] values) {
        this.indexed = values;
    }

    public int getIndexed(int index) {
        return this.indexed[index];
    }

    public void setIndexed(int index, int value) {
        this.indexed[index] = value;
    }
}
