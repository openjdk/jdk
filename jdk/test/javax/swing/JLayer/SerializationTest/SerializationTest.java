/*
 * @test
 * @summary Makes sure that JLayer is synchronizable
 * @author Alexander Potochkin
 * @run main SerializationTest
 */

import javax.swing.*;
import javax.swing.plaf.LayerUI;
import java.io.ByteArrayInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;

public class SerializationTest {

    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream);

        JLayer<JButton> layer = new JLayer<JButton>(new JButton("Hello"));

        layer.setUI(new TestLayerUI<JButton>());

        outputStream.writeObject(layer);
        outputStream.flush();

        ByteArrayInputStream byteArrayInputStream =
                        new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        ObjectInputStream inputStream = new ObjectInputStream(byteArrayInputStream);

        JLayer newLayer = (JLayer) inputStream.readObject();

        if (newLayer.getLayout() == null) {
            throw new RuntimeException("JLayer's layout is null");
        }
        if (newLayer.getGlassPane() == null) {
            throw new RuntimeException("JLayer's glassPane is null");
        }
        if (newLayer.getUI().getClass() != layer.getUI().getClass()) {
            throw new RuntimeException("Different UIs");
        }
        if (newLayer.getView().getClass() != layer.getView().getClass()) {
            throw new RuntimeException("Different Views");
        }
    }

    static class TestLayerUI<V extends JComponent> extends LayerUI<V> {
        public String toString() {
            return "TestLayerUI";
        }
    }
}