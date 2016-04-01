#
# matching the following output specified as a pattern that verifies
# that the numerical values conform to a specific pattern, rather than
# specific values.
#
#  NGCMN      NGCMX       NGC      S0CMX     S0C     S1CMX     S1C       ECMX        EC      YGC   FGC 
#    2176.0     7232.0     2176.0    192.0     64.0    192.0     64.0     6848.0     2048.0     1     0


BEGIN	{
	    headerlines=0; datalines=0; totallines=0
	}

/^  NGCMN      NGCMX       NGC      S0CMX     S0C     S1CMX     S1C       ECMX        EC      YGC   FGC $/	{
	    headerlines++;
	}

/^[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+$/	{
	    datalines++;
	}

	{ totallines++; print $0 }

END	{
	    if ((headerlines == 1) && (datalines == 1)) {
	        exit 0
	    }
	    else {
	        exit 1
	    }
	}
