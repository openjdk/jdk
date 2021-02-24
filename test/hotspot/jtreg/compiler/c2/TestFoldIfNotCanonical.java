public class TestFoldIfNotCanonical {

    private static int field;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test(true, 1, false);
            test(false, 1, false);
        }
        test(true, 1, true);
//        test(true, 1, false);
//        for (int i = 0; i < 20_000; i++) {
//            test(true, 1, true);
//            test(false, 1, true);
//        }
//        test(true, 1, false);
    }

    private static void test(boolean b, int stride, boolean b1) {
        int i = 0;
        if (b) {
            i = 5;
        }
        for (int j = 0; j < 50; j += stride) {
//            if (i == 1) {
//
//            } else {
//                if (i >= 5) {
//
//                } else {
//                    if (i >= 0) {
//                        if (i > 1) {
//                            // unc
//                        } else {
//
//                        }
//                    } else {
//                        // unc
//                    }
//                }
//                if (i >= 0 && i < 5) {
//
//                } else {
//
//                }
//            }

            switch (i) {
                case 0: // 6226
                    field = 0x42;
                    break;
                case 1: // 160728
                    field = 0x42;
                    break;
                case 2: // 0
                    field = 0x42;
                    break;
                case 3: // 0
                case 4: // 0
                case 5: // 6226
                default:
            }

            i = 1;
        }
        if (b1) {

        }
    }


}
