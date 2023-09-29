class WrongBCIAfterMotion {
    static int counter;

    private int id;

    public WrongBCIAfterMotion(int id) {
        this.id = id;
    }

    static void blackhole(Object obj) {
    }

    public static void main(String[] args) {
        final int n = 300_000;

        for (int i =0; i<n;++i) {
        /*
         * PEA moves AllocateNode from 8 to 24. however, there's memory side-effect in between.
         * PEA is using the JVMState including bci=8.
         * This is wrong under -XX:-UseTLAB and -XX:OptimizeALot.
         *
       8: new           #8                  // class WrongBCIAfterMotion
      11: dup
      12: getstatic     #14                 // Field counter:I
      15: dup
      16: iconst_1
      17: iadd
      18: putstatic     #14                 // Field counter:I
      21: invokespecial #17                 // Method "<init>":(I)V
      24: invokestatic  #20                 // Method blackhole:(Ljava/lang/Object;)V
         */
            blackhole(new WrongBCIAfterMotion(counter++));
        }

        if (counter != n) {
            throw new RuntimeException("Wrong result");
        }
    }
}
