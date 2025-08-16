package com.sun.hotspot.tools.compiler.timeline;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Renderer {
    private final List<Event> events;
    private long endTime;

    static final int SLOT_HEIGHT = 25;
    static final int LINE_HEIGHT = 20;
    static final int PIX_PER_MS = 10; // 1px per 100us

    final int WIDTH;
    final int HEIGHT;

    public Renderer(List<Event> events) {
        this.events = events;
        endTime = 0L;
        for (Event event : events) {
            endTime = Math.max(endTime, event.timeFinished());
        }
        WIDTH = (int) (endTime * PIX_PER_MS / 1000);

        Slots slots = new Slots();
        int maxSlots = 0;
        for (Event event : events) {
            long timeCreated = event.timeCreated();
            long timeStarted = event.timeStarted();
            long timeFinished = event.timeFinished();

            int slot = slots.claim(timeCreated, timeFinished);
            maxSlots = Math.max(maxSlots, slot);
        }

        HEIGHT = maxSlots * SLOT_HEIGHT + 80;
    }

    private int mapTime(long time) {
        return (int)(time * (WIDTH) / endTime);
    }

    private Color mapLevel(int level) {
        switch (level) {
            case 0:
                return Color.BLACK;
            case 1:
                return Color.GREEN.darker();
            case 2:
                return Color.BLUE;
            case 3:
                return Color.ORANGE;
            case 4:
                return Color.RED;
        }
        return null;
    }

    public void render() throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Render grid

        g.setBackground(Color.LIGHT_GRAY);
        g.setColor(Color.LIGHT_GRAY);
        for (int ms = 0; ms < WIDTH / PIX_PER_MS; ms += 10) {
            int x = mapTime(ms * 1000);
            g.drawLine(x, 0, x, HEIGHT);
            g.drawString(ms + "ms", x + 10, HEIGHT - 10);
        }

        Font font = new Font("Serif", Font.PLAIN, 12);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics(font);

        final int PAD = 2;

        Slots slots = new Slots();

        for (Event event : events) {
            long timeCreated = event.timeCreated();
            long timeStarted = event.timeStarted();
            long timeFinished = event.timeFinished();

            int slot = slots.claim(timeCreated, timeFinished);

            Color compileColor = mapLevel(event.level());

            g.setBackground(Color.GRAY);
            g.setColor(Color.GRAY);

            int queueDur = mapTime(timeStarted) - mapTime(timeCreated);
            queueDur = Math.max(1, queueDur);
            g.fillRect(mapTime(timeCreated), 10 + slot*SLOT_HEIGHT + (LINE_HEIGHT / 3), queueDur, LINE_HEIGHT / 3);

            g.setBackground(compileColor);
            g.setColor(compileColor);

            int compDur = mapTime(timeFinished) - mapTime(timeStarted);
            compDur = Math.max(1, compDur);
            g.fillRect(mapTime(timeStarted), 10 + slot*SLOT_HEIGHT, compDur, LINE_HEIGHT);

//            g.setBackground(Color.WHITE);
//            g.setColor(Color.BLACK);
//            g.drawString(event.method(), mapTime(timeCreated) - 10 - metrics.stringWidth(event.method()), y);
//
        }

        boolean success = ImageIO.write(image, "png", new File("out.png"));
        if (!success) {
            throw new IOException("Failed to render image");
        }
    }

    private static class Slots {
        List<Slot> slots = new ArrayList<>();
        int claim(long startTime, long endTime) {
            for (Slot slot : slots) {
                if (slot.finishTime + 100 < startTime) {
                    // Take this one.
                    slot.finishTime = endTime;
                    return slot.id;
                }
            }
            int id = slots.size();
            slots.add(new Slot(id, endTime));
            return id;
        }
    }

    private static final class Slot {
        private final int id;
        private long finishTime;

        private Slot(int id, long finishTime) {
            this.id = id;
            this.finishTime = finishTime;
        }

        public int id() {
            return id;
        }

        public long finishTime() {
            return finishTime;
        }
    }

}
