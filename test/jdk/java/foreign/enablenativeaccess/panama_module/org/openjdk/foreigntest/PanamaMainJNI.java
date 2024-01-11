package org.openjdk.foreigntest;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;

public class PanamaMainJNI {

    static {
        System.loadLibrary("LinkerInvokerModule");
    }

    public static void main(String[] args) {
        testDirectAccessCLinker();
    }

    public static void testDirectAccessCLinker() {
        System.out.println("Trying to get downcall handle");
        nativeLinker0(Linker.nativeLinker(), FunctionDescriptor.ofVoid(), new Linker.Option[0]);
        System.out.println("Got downcall handle");
    }

    static native void nativeLinker0(Linker linker, FunctionDescriptor desc, Linker.Option[] options);
}
