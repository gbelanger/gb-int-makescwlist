import java.awt.geom.Point2D;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;

import jsky.coords.WorldCoords;
import jsky.coords.wcscon;
import org.apache.log4j.Logger;


public final class ScwListMaker {

    public static Logger logger  = Logger.getLogger(ScwListMaker.class);
    private static wcscon convert = new wcscon();

    public static ScwList makeScwList(double ra, double dec, double distInL, double distInB, int firstRev, int lastRev, File pointLisFile, float[][] normEffArea) throws Exception {
	AsciiDataFileReader in = new AsciiDataFileReader(pointLisFile);
	String[] scwNum = (String[]) in.getStrCol(0);
	double[] pointing_ra = (double[]) in.getDblCol(1);
	double[] pointing_dec = (double[]) in.getDblCol(2);
	double[] telapse = (double[]) in.getDblCol(3);
	return makeScwList(ra, dec, distInL, distInB, firstRev, lastRev, scwNum, pointing_ra, pointing_dec, telapse, normEffArea);
    }

    public static ScwList makeScwList(double ra, double dec, double distInL, double distInB, int firstRev, int lastRev, String[] scwNum, double[] pointing_ra, double[] pointing_dec, double[] telapse, float[][] normEffArea) throws Exception {
	int nscw = scwNum.length;
	Point2D.Double radec = new Point2D.Double(ra,dec);
	Point2D.Double lb = convert.fk52gal(radec);
	double maxDiagDist = Math.sqrt(Math.pow(distInL,2)+Math.pow(distInB,2));
	double l = lb.getX();
	double b = lb.getY();
	double lMin = l - distInL;
	if ( lMin < 0 ) lMin += 360;
	double lMax = l + distInL;
	if ( lMax >= 360 ) lMax -= 360;
	double bMin = b - distInB;
	if ( bMin < -90 ) bMin += 180;
	double bMax = b + distInB;
	if ( bMax >= 90 ) bMax -= 180;
	WorldCoords sourceCoords = new WorldCoords(radec);
	double totTelapse = 0;
	double totEffExpo = 0;
	int j=0;
	double maxDiagDistInPix = Math.sqrt(2*Math.pow(199,2));
 	logger.info("Selecting science windows");	
	ArrayList<String> selectedIDsList = new ArrayList<String>();
	ArrayList<Double> selectedRasList = new ArrayList<Double>();
	ArrayList<Double> selectedDecsList = new ArrayList<Double>();
	ArrayList<Double> selectedDurationsList = new ArrayList<Double>();
	ArrayList<Double> selectedEffectiveExpsList = new ArrayList<Double>();
	for ( int i=0; i < nscw; i++ ) {
	    // Duration > 900 seconds
	    boolean durationOK = ( telapse[i] > 900 );
	    // Revolution within range
	    String rev = scwNum[i].substring(0,4);
	    int revNum = (new Integer(rev)).intValue();
	    boolean revRangeOK = ( revNum <= lastRev && revNum >= firstRev );
	    // Max angular distance
	    Point2D.Double pointing_radec = new Point2D.Double(pointing_ra[i], pointing_dec[i]);
	    Point2D.Double pointing_lb = convert.fk52gal(pointing_radec);
	    WorldCoords pointingCoords = new WorldCoords(pointing_radec);
	    double dist = sourceCoords.dist(pointingCoords);
	    int distInPix = (new Double(Math.rint(dist/4.35))).intValue(); // each pix has a size of 4.35'
	    double distInDeg = dist/60;  //  divide by 60 to get degrees
	    boolean maxDistOK = ( distInDeg <= maxDiagDist && distInPix < maxDiagDistInPix );
	    // Latitude (b)
	    double pointing_b = pointing_lb.getY();
	    boolean latitudeOK = false;
	    if ( bMin > bMax ) {
		if ( pointing_b <= bMax || pointing_b >= bMin ) {
		    latitudeOK = true;
		}
	    }
	    else {
		if ( pointing_b >= bMin && pointing_b < bMax ) {
		    latitudeOK = true;
		}
	    }
	    // Longitude (l)
	    double pointing_l = pointing_lb.getX();
	    boolean longitudeOK = false;
	    if ( lMin > lMax ) {
		if ( pointing_l >= lMin || pointing_l <= lMax ) {
		    longitudeOK = true;
		}
	    }
	    else {
		if ( pointing_l >= lMin && pointing_l <= lMax ) {
		    longitudeOK = true;
		}
	    }
	    // Apply all selection criteria
	    if ( durationOK && revRangeOK && maxDistOK && latitudeOK && longitudeOK ) {
		try {
		    totTelapse += telapse[i];
		    double effectiveExp = telapse[i]*normEffArea[199-distInPix][199-distInPix];
		    totEffExpo += effectiveExp;
		    selectedIDsList.add(scwNum[i]);
		    selectedRasList.add(new Double(pointing_ra[i]));
		    selectedDecsList.add(new Double(pointing_dec[i]));
		    selectedDurationsList.add(new Double(telapse[i]));
		    selectedEffectiveExpsList.add(new Double(effectiveExp));
		    j++;
		}
		catch ( ArrayIndexOutOfBoundsException e ) { }
	    }
	}
	//  Print out some stats
	DecimalFormat timeFormat = new DecimalFormat("0.00");
	logger.info("  "+ j +" scw selected");
	logger.info("  Total TELAPSE = "+timeFormat.format(totTelapse/1e3)+" ks");
	logger.info("  Off-axis corrected = "+timeFormat.format(totEffExpo/1e3)+" ks");
	logger.info("  Dead-time corrected (0.82) = "+timeFormat.format(totEffExpo/1e3*0.82)+" ks");
	selectedIDsList.trimToSize();
	selectedRasList.trimToSize();
	selectedDecsList.trimToSize();
	selectedDurationsList.trimToSize();
	selectedEffectiveExpsList.trimToSize();
	int m = selectedIDsList.size();
	String[] ids = new String[m]; 
	double[] ras = new double[m]; 
	double[] decs = new double[m];
	double[] durations = new double[m];
	double[] effectiveExps = new double[m];
	for ( int i=0; i < m; i++ ) {
	    ids[i] = (String) selectedIDsList.get(i);
	    ras[i] = (double) selectedRasList.get(i);
	    decs[i] = (double) selectedDecsList.get(i);
	    durations[i] = (double) selectedDurationsList.get(i);
	    effectiveExps[i] = (double) selectedEffectiveExpsList.get(i);
	}
	return new ScwList(ids, ras, decs, durations, effectiveExps);
    }


}