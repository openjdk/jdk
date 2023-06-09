package com.sun.hotspot.igv.hierarchicallayout;

import com.sun.hotspot.igv.layout.Link;

public class LayoutEdge {

        public LayoutNode from;
        public LayoutNode to;
        // Horizontal distance relative to start of 'from'.
        public int relativeFrom;
        // Horizontal distance relative to start of 'to'.
        public int relativeTo;
        public Link link;
        public boolean vip;

        @Override
        public String toString() {
            return "Edge " + from + ", " + to;
        }
    }