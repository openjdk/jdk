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
        int iterations = 0;
        try {
            while (iterations < 20_000) {
                CrazyException.foo(0 == (iterations & 0xf));
                iterations++;
            }
        } finally {
            System.err.println("Epsilon Test: " + iterations);
        }
    }
}
