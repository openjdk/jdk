import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import java.util.stream.IntStream;

import javax.swing.JFrame;
import javax.swing.JPanel;

public final class WindowCreator {
    public static List<JFrame> createTestWindows(final int windowLimit) {
        return IntStream.rangeClosed(0, windowLimit - 1)
                        .mapToObj(WindowCreator::createFrame)
                        .toList();
    }

    private static final Dimension[] SIZE = {
            new Dimension(300, 150),
            new Dimension(250, 100),
            new Dimension(350, 200),
            new Dimension(225, 100),
    };

    private static final Color[] COLORS = {
            new Color(240, 200, 240),
            new Color(200, 240, 200),
            new Color(200, 240, 240)
    };

    private static JFrame createFrame(int index) {
        JFrame frame = new JFrame("Window " + (index + 1));
        frame.add(createPanel(SIZE[index % SIZE.length],
                              COLORS[index % COLORS.length]));
        frame.pack();
        return frame;
    }

    private static JPanel createPanel(final Dimension size,
                                      final Color color) {
        JPanel panel = new JPanel();
        panel.setPreferredSize(size);
        panel.setBackground(color);
        return panel;
    }
}
