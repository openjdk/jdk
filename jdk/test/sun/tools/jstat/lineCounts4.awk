#
# matching the following output specified as a pattern that verifies
# that the numerical values conform to a specific pattern, rather than
# specific values.
#
#  S0     S1     E      O      P     YGC     YGCT    FGC    FGCT     GCT   
#  0.00  99.99  66.81   1.24  26.55      1    0.028     0    0.000    0.028
#  0.00  99.99  68.81   1.24  27.84      1    0.028     0    0.000    0.028
#  0.00  99.99  70.81   1.24  27.84      1    0.028     0    0.000    0.028
#  0.00  99.99  70.81   1.24  27.84      1    0.028     0    0.000    0.028
#  0.00  99.99  70.81   1.24  27.84      1    0.028     0    0.000    0.028
#  0.00  99.99  72.81   1.24  27.84      1    0.028     0    0.000    0.028
#  0.00  99.99  72.81   1.24  27.84      1    0.028     0    0.000    0.028
#  0.00  99.99  74.81   1.24  27.84      1    0.028     0    0.000    0.028
#  0.00  99.99  74.81   1.24  27.84      1    0.028     0    0.000    0.028
#  0.00  99.99  76.81   1.24  27.85      1    0.028     0    0.000    0.028
#  S0     S1     E      O      P     YGC     YGCT    FGC    FGCT     GCT   
#  0.00  99.99  76.81   1.24  27.85      1    0.028     0    0.000    0.028

BEGIN	{
	    headerlines=0; datalines=0; totallines=0
	    datalines2=0;
        }

/^  S0     S1     E      O      P     YGC     YGCT    FGC    FGCT     GCT   $/	{
	    headerlines++;
	}

/^[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+$/	{
	    if (headerlines == 2) {
	        datalines2++;
	    }
	    datalines++;
	}

	{ totallines++; print $0 }

END	{ 
	    if ((headerlines == 2) && (datalines == 11) && (totallines == 13) && (datalines2 == 1)) {
	        exit 0
	    } else {
	        exit 1
	    }
	}
