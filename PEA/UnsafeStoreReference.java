import jdk.internal.misc.Unsafe;

class UnsafeStoreReference {
  private static final Unsafe U = Unsafe.getUnsafe();
  private transient volatile Node head;
  private static final long HEAD
              = U.objectFieldOffset(UnsafeStoreReference.class, "head");
  private transient volatile Node tail;

  static class Node {}

  // inspired from java.util.concurrent.locks.AbstractQueuedSynchronizer::tryInitializeHead
  void test() {
    Node h = new Node();

    if (U.compareAndSetReference(this, HEAD, null, h))
        tail = h;
  }

  public static void main(String[] args) {
    UnsafeStoreReference kase = new UnsafeStoreReference();

    for (int i = 0; i < 100_000; i++) {
        kase.test();
        if (kase.head != kase.tail) {
            throw new RuntimeException("wrong result");
        }
        kase.head = kase.tail = null;
    }
  }
}
