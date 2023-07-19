java --add-exports java.base/jdk.internal.misc=ALL-UNNAMED -XX:+UnlockExperimentalVMOptions -XX:CompileCommand=compileonly,UnsafeStoreReference::test $*  UnsafeStoreReference
