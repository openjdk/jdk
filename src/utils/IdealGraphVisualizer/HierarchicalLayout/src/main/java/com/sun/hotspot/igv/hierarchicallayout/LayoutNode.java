package com.sun.hotspot.igv.hierarchicallayout;

import com.sun.hotspot.igv.layout.Vertex;
import java.util.*;

public class LayoutNode {

        public int x;
        public int y;
        public int width;
        public int height;
        public int layer = -1;
        public int xOffset;
        public int yOffset;
        public int bottomYOffset;
        public Vertex vertex; // Only used for non-dummy nodes, otherwise null

        public List<LayoutEdge> preds = new ArrayList<>();
        public List<LayoutEdge> succs = new ArrayList<>();
        public HashMap<Integer, Integer> outOffsets = new HashMap<>();
        public HashMap<Integer, Integer> inOffsets = new HashMap<>();
        public int pos = -1; // Position within layer

        public int crossingNumber;

        @Override
        public String toString() {
            return "Node " + vertex;
        }
    }
