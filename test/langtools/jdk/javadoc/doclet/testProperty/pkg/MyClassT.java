package pkg;

import java.util.List;

//import javafx.beans.property.*;

/**
 * Test program for javadoc properties.
 */
public class MyClassT<T> {

    private SimpleObjectProperty<List<T>> list
            = new SimpleObjectProperty<List<T>>();

    /**
     * This is an Object property where the Object is a single {@code List<T>}.
     *
     * @return the list
     */
    public final ObjectProperty<List<T>> listProperty() {
        return list;
    }

    public final void setList(List<T> list) {
    }

    public final List<T> getList() {
        return list.get();
    }


}
