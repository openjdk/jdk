#
# matching the following output specified as a pattern that verifies
# that the numerical values conform to a specific pattern, rather than
# specific values.
#
# S0C    S1C    S0U    S1U   TT MTT  DSS      EC       EU     YGC     YGCT  
#  64.0   64.0    0.0   64.0  1  31   32.0   2048.0     41.4      1    0.031



BEGIN	{
	    headerlines=0; datalines=0; totallines=0
	}

/^ S0C    S1C    S0U    S1U   TT MTT  DSS      EC       EU     YGC     YGCT  $/	{

	    headerlines++;
	}

/^[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+$/	{
	    datalines++;
	}

	{ totallines++; print $0 }

END	{
	    if ((headerlines == 1) && (datalines == 1) && (totallines == 2)) {
	        exit 0
	    }
	    else {
	        exit 1
	    }
	}
