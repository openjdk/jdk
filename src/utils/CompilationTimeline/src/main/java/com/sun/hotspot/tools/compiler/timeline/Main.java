package com.sun.hotspot.tools.compiler.timeline;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String... args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Missing command line argument");
        }

        String inputFile = args[0];

        Parser parser = new Parser(inputFile);
        List<Event> events = parser.parse();

        new Renderer(events).render();
    }
}
