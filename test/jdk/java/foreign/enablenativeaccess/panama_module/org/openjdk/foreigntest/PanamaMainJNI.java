package org.openjdk.foreigntest;

public class PanamaMainJNI {

    static {
        System.loadLibrary("LinkerInvokerModule");
    }

    public static void main(String[] args) {
        testDirectAccessCLinker();
    }

    public static void testDirectAccessCLinker() {
        System.out.println("Trying to get Linker");
        nativeLinker0();
        System.out.println("Got Linker");
    }

    static native void nativeLinker0();
}
