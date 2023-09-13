import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import javax.swing.JFrame;
import javax.swing.JPanel;

import static java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment;
import static java.awt.Toolkit.getDefaultToolkit;

/*
 * @test
 * @bug 8294156
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Demonstrates adding and positioning several test windows
 *          horizontally in columns
 * @run main/manual TwoWindowColumnsH
 */
public class TwoWindowColumnsH {
    public static final String INSTRUCTIONS = """
            A simple demo to position test windows
            in two columns or rows near the
            instruction frame.
            """;
    private static final Dimension[] SIZE = {
            new Dimension(300, 200),
            new Dimension(250, 100)
    };

    private static final Color[] COLORS = {
            new Color(240, 200, 240),
            new Color(200, 240, 200),
            new Color(200, 240, 240)
    };

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(INSTRUCTIONS)
                      .rows(15)
                      .columns(30)
                      .testUI(TwoWindowColumnsH::createTestUI)
                      .positionTestUI(TwoWindowColumnsH::positionTestUI)
                      .position(PassFailJFrame.Position.HORIZONTAL)
                      .build()
                      .awaitAndCheck();
    }

    private static final int GAP = 8;

    private static Point getScreenCenter() {
        GraphicsConfiguration gc = getLocalGraphicsEnvironment()
                                   .getDefaultScreenDevice()
                                   .getDefaultConfiguration();
        Dimension size = gc.getBounds()
                           .getSize();
        Insets insets = getDefaultToolkit()
                        .getScreenInsets(gc);

        return new Point((size.width - insets.left - insets.right) / 2,
                         (size.height - insets.top - insets.bottom) / 2);
    }

    private static Dimension getColumnSize(List<? extends Window> column) {
        return new Dimension(column.stream()
                                   .mapToInt(Component::getWidth)
                                   .max()
                                   .orElseThrow(),
                             column.stream()
                                         .mapToInt(Component::getHeight)
                                         .sum()
                             + (column.size() - 1) * GAP);
    }

    private static Dimension getRowSize(List<? extends Window> row) {
        return new Dimension(row.stream()
                                     .mapToInt(Component::getWidth)
                                     .sum(),
                             row.stream()
                                      .mapToInt(Component::getHeight)
                                      .max()
                                      .orElseThrow()
                             + (row.size() - 1) * GAP);
    }

    private static final int ROWS = 3;
    private static final int COLUMNS = 3;

    public static void positionTestUI(List<? extends Window> windows,
                                      PassFailJFrame.InstructionUI instructionUI) {
        final Point center = getScreenCenter();

        Dimension columnSize;
        int x;
        int y;
        switch (instructionUI.getPosition()) {
            case HORIZONTAL:
                x = instructionUI.getLocation().x + instructionUI.getSize().width + GAP;
                int columnStart = 0;
                do {
                    List<? extends Window> column =
                            windows.subList(columnStart,
                                            Math.min(columnStart + ROWS,
                                                     windows.size()));
                    columnSize = getColumnSize(column);

                    y = center.y - columnSize.height / 2;
                    for (Window w : column) {
                        w.setLocation(x, y);
                        y += w.getHeight() + GAP;
                    }

                    x += columnSize.width + GAP;
                    columnStart += ROWS;
                } while (columnStart < windows.size());
                break;

            case VERTICAL:
                List<List<? extends Window>> windowRows =
                        new ArrayList<>(windows.size() / COLUMNS + 1);
                int rowStart = 0;
                do {
                    windowRows.add(windows.subList(rowStart,
                                                   Math.min(rowStart + COLUMNS,
                                                            windows.size())));
                    rowStart += COLUMNS;
                } while (rowStart < windows.size());

                List<Dimension> rowSizes =
                        windowRows.stream()
                                  .map(TwoWindowColumnsH::getRowSize)
                                  .toList();

                y = center.y - (rowSizes.stream()
                                        .mapToInt(d -> d.height)
                                        .sum()
                                + (rowSizes.size() - 1) * GAP
                                - instructionUI.getSize().height) / 2;
                instructionUI.setLocation(instructionUI.getLocation().x,
                                          y - GAP - instructionUI.getSize().height);

                for (int i = 0; i < windowRows.size(); i++) {
                    List<? extends Window> row = windowRows.get(i);
                    Dimension rowSize = rowSizes.get(i);

                    x = center.x - (rowSize.width
                                    + (row.size() - 1) * GAP) / 2;
                    for (Window w : row) {
                        w.setLocation(x, y);
                        x += w.getWidth() + GAP;
                    }

                    y += rowSize.height + GAP;
                }
                break;

            default:
                throw new IllegalStateException("Unexpected position value: "
                                                + instructionUI.getPosition());
        }
    }

    public static List<? extends Window> createTestUI() {
        return createTestUI(6);
    }

    public static List<? extends Window> createTestUI(final int windowLimit) {
        return IntStream.rangeClosed(1, windowLimit)
                        .mapToObj(TwoWindowColumnsH::createFrame)
                        .toList();
    }

    private static JFrame createFrame(int index) {
        JFrame frame = new JFrame("Test window " + index);
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
