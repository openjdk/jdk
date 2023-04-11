class CrazyException {
    void blackhole() {}
    public static Object foo(boolean cond) {
        var x = new CrazyException();
        for (int i=0; i< 100; ++i) {
          if (cond)  {
              try {
                  x.blackhole(); // not inline. escape here.
              } finally {        // hidden catch(Throwable) is here!
                  close();
              }
          }
        }
        return x;
    }

    public static void close() {}
    public static void main(String[] args)  {
        for (int i = 0; i < 20_000; ++i) {
            CrazyException.foo(0 == ( i& 0xf));
        }
    }
}
