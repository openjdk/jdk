class SimpleLoop {
    Object[] elements;

    class Iterator {
        int idx = 0;

        Object next() {
            return elements[idx++];
        }
    }

    Iterator iterator() {
        return new Iterator();
    }

    // based on java.util.ImmutableCollections$SetN::toArray
    Object[] toArray(int size) {
        Object[] array = new Object[size];
        Iterator it = iterator();
        for (int i = 0; i < size; i++) { array[i] = it.next(); }
        return array;
    }

    public static void main(String[] args) {
        SimpleLoop kase = new SimpleLoop();

        int sz = 1024;
        kase.elements = new Object[sz];
        for (int i=0; i< sz; ++i ) kase.elements[i] = new Object();

        Object[] results = null;
        for (int i = 0; i < 1_000_000; ++i) {
            results = kase.toArray(24);
        }

        for (int i =0; i< 24; ++i) {
            if (results[i] != kase.elements[i]) {
                throw new RuntimeException("wong answser at " + i);
            }
        }
    }

}
