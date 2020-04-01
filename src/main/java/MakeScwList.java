import java.awt.geom.Point2D; 
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.UUID;

import jsky.coords.wcscon;
import nom.tam.fits.Fits;
import nom.tam.fits.ImageHDU;
import nom.tam.util.BufferedDataInputStream;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class MakeScwList {

    // Class variables
    // General
    private static String version = "5.0";
    private static String sep = File.separator;
    private static String homeDir = System.getProperty("user.home");
    private static String osName = System.getProperty("os.name");
    private static String hostName = getHostName();
    private static wcscon wcsConvert = new wcscon();
    private static Logger logger  = Logger.getLogger(MakeScwList.class);
    private static DecimalFormat number = new DecimalFormat("0.00");
    private static File loggerFile;
    private static float[][] normEffArea;
    // Arguments
    private static double ra = 0;
    private static double dec = 0;
    private static double distInL = 0;
    private static double distInB = 0;
    private static int firstRev = 26;
    private static int lastRev = 9999;
    private static String scwFilename = "out";
    // Derived 
    private static Point2D.Double radec = null;
    private static Point2D.Double lb = null;
    private static double maxDiagDist = 0;
    private static double lMin = 0;
    private static double lMax = 0;
    private static double bMin = 0;
    private static double bMax = 0;


    // main
    public static void main (String[] args) throws Exception {
	configureLogger();
	handleArguments(args);
	float[][] normEffArea = calculateNormalisedEffectiveArea();
	File pointLisFile = getPointLisFile();
	ScwList scwlis = ScwListMaker.makeScwList(ra, dec, distInL, distInB, firstRev, lastRev, pointLisFile, normEffArea);
	if (scwFilename.equals("out")) {
	    try {
		scwFilename = "scw_radec_"+ra+"_"+dec+"_fov_"+distInL+"x"+distInB+"_revs_"+scwlis.firstRev()+"-"+scwlis.lastRev()+".lis";
	    }
	    catch (ArrayIndexOutOfBoundsException e) {
		scwFilename = "scw_radec_"+ra+"_"+dec+"_fov_"+distInL+"x"+distInB+"_revs_"+firstRev+"-"+lastRev+".lis";
	    }
	}
	scwlis.write(scwFilename);
    }
    
    // getPointLisFile
    private static File getPointLisFile() throws Exception {

	File pointLisFile = new File("point.lis");
	if ( ! pointLisFile.exists() ) {
	    logger.info("File "+pointLisFile.getPath()+" not found in current directory");
	    pointLisFile = updatePointLis();
	}
	else {
	    logger.info(pointLisFile.getPath()+" found");
	    printLastModified(pointLisFile);
	    long lastModif = pointLisFile.lastModified();
	    long currentTime = System.currentTimeMillis();
	    long threeDaysInMillis = 3*86400*1000;
	    long threeDaysAgo = currentTime - threeDaysInMillis;
	    if ( lastModif < threeDaysAgo ) {
		logger.warn("Older than 3 days");
		pointLisFile = updatePointLis();
	    }
	}
	return pointLisFile;
    }

    // updatePointLis
    public static File updatePointLis() throws Exception {
	String macDirName = homeDir+sep+"Documents"+sep+"integ"+sep+"idx";
	String isdaDirName = homeDir+sep+"integ"+sep+"idx";
	String intggwDirName = homeDir+sep+"integral"+sep+"osa_support";
	boolean mac = false;
	boolean isda = false;
	boolean intggw = false;
	File pointLisFile = null;
	if ( homeDir.equals(sep+"Users"+sep+"gbelanger") && osName.equals("Mac OS X") ) {
	    pointLisFile = new File(macDirName+sep+"point.lis");
	    mac = true;
	    logger.info("Working on gbelanger's MacBook Pro");
	    logger.info("Checking directory "+macDirName);
	}
	else if ( hostName.contains("isdabulk") ) {
	    pointLisFile = new File(isdaDirName+sep+"point.lis");
	    isda = true;
	    logger.info("Working on isdabulk");
	    logger.info("Checking directory "+isdaDirName);
	}
	else if ( hostName.contains("n1grid") ) {
	    pointLisFile = new File(intggwDirName+sep+"point.lis");
	    intggw = true;
	    logger.info("Working on ESAC grid");
	    logger.info("Checking directory "+intggwDirName);
	}
	else pointLisFile = new File("point.lis");
	long lastModif = pointLisFile.lastModified();
	long currentTime = System.currentTimeMillis();
	long threeDaysInMillis = 3*86400*1000;
	long threeDaysAgo = currentTime - threeDaysInMillis;
	if ( ! pointLisFile.exists() || lastModif < threeDaysAgo ) {
	    //  Check if GNRL-SCWG-GRP-IDX.fits is there and its date
	    File scwidxFile = null;
	    String idxName = "GNRL-SCWG-GRP-IDX.fits.gz";
	    if ( mac ) scwidxFile = new File(macDirName+sep+idxName);
	    else if ( isda ) scwidxFile = new File(isdaDirName+sep+idxName);
	    else if ( intggw ) scwidxFile = new File (intggwDirName+sep+idxName);
	    else scwidxFile = new File(idxName);
	    lastModif = scwidxFile.lastModified();
	    if ( ! scwidxFile.exists() || lastModif < threeDaysAgo ) {
		logger.info("Fetching latest general index GNRL-SCWG-GRP-IDX.fits from the ISDC ...");
		boolean fetched = getscwidx();
		if ( fetched ) {
		    scwidxFile = new File(idxName);
		    PointingListWriter.constructPointingList(scwidxFile);
		}
		else {
		    logger.warn("Could not fetch file GNRL-SCWG-GRP-IDX.fits.gz");
		    logger.info("Extracting 'point.lis' from jar");
		    InputStream is = getFileFromJarAsStream("point.lis");
		    inputStreamToFile(is, "point.lis");
		}
		pointLisFile = new File("point.lis");
	    }
	}
	else {
	    logger.info("Found point.lis");
	    printLastModified(pointLisFile);
	}
	return pointLisFile;
    }
    
    //  Effective area
    private static float[][] calculateNormalisedEffectiveArea() throws Exception {
	BufferedDataInputStream effAreaFileAsStream = new BufferedDataInputStream(getFileFromJarAsStream("eff_area.fits.gz"));
	Fits effAreaFits = new Fits(effAreaFileAsStream, true);
	ImageHDU effAreaHDU = (ImageHDU) effAreaFits.getHDU(0);
	float[][] effArea = (float[][]) effAreaHDU.getKernel();
	float max = -Float.MAX_VALUE;
	float min = Float.MAX_VALUE;
	//  Determine min and max values
	for ( int row=0; row < 400; row++ ) {
	    for ( int col=0; col < 400; col++ ) {
		max = Math.max(max, effArea[row][col]);
		min = Math.min(min, effArea[row][col]);
	    }
	}
	max = max - min;
	//  Normalize to min=0 and max=1
	normEffArea = new float[400][400];
	for ( int row=0; row < 400; row++ ) {
	    for ( int col=0; col < 400; col++ ) {
		normEffArea[row][col] = (effArea[row][col] - min)/max;
	    }
	}
	return normEffArea;
    }

    //  Logger
    private static void configureLogger() throws IOException {
	String loggerFilename= "logger.config";
	InputStream log = getFileFromJarAsStream(loggerFilename);
	UUID uuid = UUID.randomUUID();
	String homeDir = System.getProperty("user.home");
	loggerFilename = new String(homeDir+File.pathSeparator+"logger.config_"+uuid.toString());
	loggerFile = new File(loggerFilename);
	loggerFile.deleteOnExit();
	inputStreamToFile(log, loggerFilename);
        PropertyConfigurator.configure(loggerFilename);
    }
    public static InputStream getFileFromJarAsStream(String name) {
	return ClassLoader.getSystemResourceAsStream(name);
    }
    private static void inputStreamToFile(InputStream io, String fileName) throws IOException {       
	FileOutputStream fos = new FileOutputStream(fileName);
	byte[] buf = new byte[256];
	int read = 0;
	while ((read = io.read(buf)) > 0) {
	    fos.write(buf, 0, read);
	}
	fos.flush();
	fos.close();
    }

    // Arguments
    private static void handleArguments(String[] args) throws Exception {
	if (args.length < 3 || args.length > 7 ) {
	    logger.info("Usage: java -jar MakeScwList ra dec distFromAxis");
	    logger.info("       java -jar MakeScwList ra dec distFromAxis out.lis");
	    logger.info("       java -jar MakeScwList ra dec distInL distInB");
	    logger.info("       java -jar MakeScwList ra dec distInL distInB out.lis");
	    logger.info("       java -jar MakeScwList ra dec distInL distInB firstRev");
	    logger.info("       java -jar MakeScwList ra dec distInL distInB firstRev out.lis");
	    logger.info("       java -jar MakeScwList ra dec distInL distInB firstRev lastRev");
	    logger.info("       java -jar MakeScwList ra dec distInL distInB firstRev lastRev out.lis");
	    System.exit(-1);
	}
	else if ( args.length == 3 ) {
	    ra = (Double.valueOf(args[0])).doubleValue();
	    dec = (Double.valueOf(args[1])).doubleValue();
	    distInL = (Double.valueOf(args[2])).doubleValue();
	    distInB = distInL;
	}
	else if ( args.length == 4 ) {
	    ra = (Double.valueOf(args[0])).doubleValue();
	    dec = (Double.valueOf(args[1])).doubleValue();
	    distInL = (Double.valueOf(args[2])).doubleValue();
	    try { distInB = (Integer.valueOf(args[3])).intValue(); }
	    catch (NumberFormatException e) { 
		scwFilename = args[3];
		distInB = distInL;
	    }
	}
	else if ( args.length == 5 ) {
	    ra = (Double.valueOf(args[0])).doubleValue();
	    dec = (Double.valueOf(args[1])).doubleValue();
	    distInL = (Double.valueOf(args[2])).doubleValue();
	    distInB = (Double.valueOf(args[3])).doubleValue();
	    try { firstRev = (Integer.valueOf(args[4])).intValue(); }
	    catch (NumberFormatException e) { scwFilename = args[4]; }
	}
	else if ( args.length == 6 ) {
	    ra = (Double.valueOf(args[0])).doubleValue();
	    dec = (Double.valueOf(args[1])).doubleValue();
	    distInL = (Double.valueOf(args[2])).doubleValue();
	    distInB = (Double.valueOf(args[3])).doubleValue();
	    firstRev = (Integer.valueOf(args[4])).intValue();
	    try { lastRev = (Integer.valueOf(args[5])).intValue(); }
	    catch (NumberFormatException e) { scwFilename = args[5]; }
	}
	else {
	    ra = (Double.valueOf(args[0])).doubleValue();
	    dec = (Double.valueOf(args[1])).doubleValue();
	    distInL = (Double.valueOf(args[2])).doubleValue();
	    distInB = (Double.valueOf(args[3])).doubleValue();
	    firstRev = (Integer.valueOf(args[4])).intValue();
	    lastRev = (Integer.valueOf(args[5])).intValue();
	    scwFilename = args[6];
	}	     
	//  Calculate derived class variables
	radec = new Point2D.Double(ra, dec);
	lb = wcsConvert.fk52gal(radec);
	maxDiagDist = Math.sqrt(Math.pow(distInL,2)+Math.pow(distInB,2));
	double l = lb.getX();
	double b = lb.getY();
	lMin = l - distInL;
	if ( lMin < 0 ) lMin += 360;
	lMax = l + distInL;
	if ( lMax >= 360 ) lMax -= 360;
	bMin = b - distInB;
	if ( bMin < -90 ) bMin += 180;
	bMax = b + distInB;
	if ( bMax >= 90 ) bMax -= 180;
	logger.info("Running MakeScwList on host "+hostName);
	logger.info("  RA, Dec = "+ra+", "+dec);
	logger.info("  L, B = "+number.format(l)+", "+number.format(b));
	logger.info("  Dist in L = "+distInL);
	logger.info("  Dist in B = "+distInB);
	logger.info("  [lMin, lMax] = ["+number.format(lMin)+", "+number.format(lMax)+"]");
	logger.info("  [bMin, bMax] = ["+number.format(bMin)+", "+number.format(bMax)+"]");
	if ( firstRev != 26 ) logger.info("  First rev = "+firstRev);
	if ( lastRev != 9999 )  logger.info("  Last rev = "+lastRev);
	if ( !scwFilename.equals("out") ) logger.info("  Output filename = "+scwFilename);
    }

    // getHostname
    public static String getHostName() {
	java.net.InetAddress localMachine = null;
	try {
	    localMachine = java.net.InetAddress.getLocalHost();	
	}
	catch ( UnknownHostException e ) {
	    logger.fatal(e);
	    System.exit(-1);
	}
	return localMachine.getHostName();
    }

    // getscwidx
    public static boolean getscwidx() throws Exception {
	String filename = "getscwidx";
	InputStream is = getFileFromJarAsStream(filename);
	inputStreamToFile(is, filename);
	MyFile getscwidxFile = new MyFile(filename);
	getscwidxFile.deleteOnExit();
	getscwidxFile.chmod(755);
	systemCall(new String[] {"./"+filename});
	return ( (new File("GNRL-SCWG-GRP-IDX.fits.gz")).exists() );
    }

    static boolean systemCall(String[] args) {
	Runtime rt = Runtime.getRuntime();
	try {
	    Process p = rt.exec(args);
	    int rc = -1;
	    while ( rc == -1 ) {
		try { rc = p.waitFor(); }
		catch (InterruptedException e) { }
	    }
	    return rc == 0;
	}
	catch (IOException e) { return false; }
    } 

    private static void printLastModified(File file) {
	long lastModif = file.lastModified();
	long currentTime = System.currentTimeMillis();
	double diff = (currentTime - lastModif)/(3600.0*1000.0);
	String unit = "hours";
	if ( diff < 1.0 ) {
	    diff *= 60.0;
	    unit = "minutes";
	}
	if ( diff < 1.0 ) {
	    diff *= 60.0;
	    unit = "seconds";
	}
	logger.info("File last modified "+number.format(diff)+" "+unit+" ago");
    }

}
