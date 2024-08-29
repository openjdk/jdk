.PHONY: all
all : gencoef mkrempitab mkrempitabqp

gencoef : gencoef.c simplexfr.c sp.h dp.h ld.h qp.h
	gcc -O gencoef.c simplexfr.c -o gencoef -lmpfr -lm

mkrempitab : mkrempitab.c
	gcc -O mkrempitab.c -o mkrempitab -lmpfr

mkrempitabqp : mkrempitabqp.c
	gcc -O mkrempitabqp.c -o mkrempitabqp -lmpfr

.PHONY: clean
clean :
	rm -f gencoef gencoefdp gencoefld mkrempitab mkrempitabqp a.out *~
	rm -f *.obj *.lib *.dll *.exp *.exe
