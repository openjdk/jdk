package com.sun.hotspot.tools.compiler.timeline;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Parser {
    private final String inputFile;

    public Parser(String inputFile) {
        this.inputFile = inputFile;
    }

    public List<Event> parse() throws IOException {
        List<Event> events = new ArrayList<>();

        // Sample format:
        // [0.388s][info][jit,compilation]     387884      25     339  1540         3    com.sun.tools.javac.util.ArrayUtils::ensureCapacity (61 bytes)
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                while (line.contains("  ")) {
                    line = line.replaceAll("  ", " ");
                }
                String[] comp = line.split(" ");
                if (comp.length >= 9) {
                    try {
                        long finishTime = Long.parseLong(comp[1]);
                        long queueTime = Long.parseLong(comp[2]);
                        long compileTime = Long.parseLong(comp[3]);
                        int id = Integer.parseInt(comp[4]);
                        int level = Integer.parseInt(comp[5]);
                        String method = comp[6];
                        events.add(new Event(id, method, level, finishTime - queueTime - compileTime, finishTime - compileTime, finishTime));
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid time format: " + line);
                    }
                }
            }
        }

//        events.sort(Comparator.comparingInt(Event::level).thenComparing(Event::timeCreated));
        events.sort(Comparator.comparingLong(Event::timeStarted));
        //events.sort(Comparator.comparingLong(Event::timeCreated));

        System.out.println(events);

        return events;
    }
}
