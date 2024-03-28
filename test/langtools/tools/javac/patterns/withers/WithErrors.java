/**
 * @test /nodynamiccopyright/
 * @bug 8324651
 * @summary Support for derived record creation expression
 * @enablePreview
 * @compile/fail/ref=WithErrors.out -XDrawDiagnostics -XDshould-stop.at=FLOW WithErrors.java
 */

import java.util.List;

public class WithErrors {

    private int field;

    private void assignments(R input) {
        int i = 0;

        input = input with {
            int l;

            value = "nue"; //OK - assignment to the component
            i = ++i + i++ + --i + i-- + -i + +i; //error - assignment to outter variable
            i += 1; //error - (compound) assignment to outter variable
            field = 0; //error - unqualified assignment to field
            this.field = 0; // OK - qualified assignment
            l = 0; //OK - assignment to a variable local to the block
        };

        input = input with {
            int l1;
            Runnable _ = () -> {
                int l2;

                value = "nue"; //error - cannot assign inside lambda
                i = 0; //error - assignment to outter variable, and inside lambda
                i += 1; //error - (compound) assignment to outter variable
                field = 0; //error - unqualified assignment to field
                this.field = 0; // OK - qualified assignment
                l1 = 0; //error - cannot assign inside lambda
                l2 = 0; //OK - assignment to a variable local to the block
            };
        };

        input = input with {
            Runnable _ = () -> {
                String _ = value; //error - "value" is not effectivelly final
            };
            value = "nue";
        };

        input = input with {
            Runnable _ = () -> {
                String _ = value; //OK - "value" is effectivelly final
            };
        };

        input = input with {
            int l1;
            Runnable _ = new Runnable() {
                public void run () {
                    int l2;

                    value = "nue"; //error - cannot assign inside the anonymous class
                    i = 0; //error - cannot assign inside the anonymous class
                    field = 0; //OK - assignment to outer field from inside the anonymous class
                    WithErrors.this.field = 0; // OK - qualified assignment
                    l1 = 0; //error - cannot assign inside lambda
                    l2 = 0; //OK - assignment to a variable local to this block/method
                }
            };
        };

        input = input with {
            Runnable _ = new Runnable() {
                public void run () {
                    String _ = value; //error - "value" is not effectivelly final
                }
            };
            value = "nue";
        };

        input = input with {
            Runnable _ = new Runnable() {
                public void run () {
                    String _ = value; //OK - "value" is effectivelly final
                }
            };
        };

        String _ = "" with {};
        int _ = 1 with {};
    }

    private void controlFlow(R input) {
        if (true) {
            input = input with {
                return ;
            };
        }

        for (;;) {
            input = input with {
                break;
            };
        }

        input = input with {
            for (;;) {
                break;
            }
        };

        for (String s : List.of("")) {
            input = input with {
                break;
            };
        }

        input = input with {
            for (String s : List.of("")) {
                break;
            }
        };

        while (true) {
            input = input with {
                break;
            };
        }

        input = input with {
            while (true) {
                break;
            }
        };

        do {
            input = input with {
                break;
            };
        } while (true);

        input = input with {
            do {
                break;
            } while (true);
        };

        switch (0) {
            default ->
                input = input with {
                    break;
                };
        }

        input = input with {
            switch (0) {
                default -> {break;}
            }
        };

        if (true) {
            for (;;) {
                input = input with {
                    continue;
                };
            }
        }

        if (true) {
            input = input with {
                for (;;) {
                    continue;
                }
            };
        }

        if (true) {
            for (String s : List.of("")) {
                input = input with {
                    continue;
                };
            }
        }

        if (true) {
            input = input with {
                for (String s : List.of("")) {
                    continue;
                }
            };
        }

        if (true) {
            while (true) {
                input = input with {
                    continue;
                };
            }
        }

        if (true) {
            input = input with {
                while (true) {
                    continue;
                }
            };
        }

        if (true) {
            do {
                input = input with {
                    continue;
                };
            } while (true);
        }

        if (true) {
            input = input with {
                do {
                    continue;
                } while (true);
            };
        }

        int _ = switch (0) {
            default -> {
                input = input with {
                    yield 0;
                };
            }
        };

        input = input with {
            int _ = switch (0) {
                default -> { yield 0; }
            };
        };
    }

    record R(String value) {}

}
