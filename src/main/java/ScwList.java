import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Scanner;
import org.apache.log4j.Logger;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Set;

// This class is for reading, writing and representing an OSA science window list

public class ScwList {
    
    // Class variables
    private InputStream str;
    private int nScws = 0;
    private String[] scwIDs = null;
    private String[] revIDs = null;
    private String[] uniqueRevIDs = null;
    private double[] ras = null;
    private double[] decs = null;
    private Point2D.Double[] coords = null;
    private double[] durations = null;
    private double[] effectiveExps = null;
    private int[] revs = null;
    private int[] uniqueRevs = null;
    private int nUniqueRevs = 0;
    private double totalDuration = 0;
    private double totalEffectiveExp = 0;
    private boolean scwIDsAreSet = false;
    private boolean coordsAreSet = false;
    private boolean durationsAreSet = false;
    private boolean effectiveExpsAreSet = false;
    private int bufferSize = 8*1024;
    private static String sep = File.separator;
    private static Logger logger  = Logger.getLogger(ScwList.class);

    // Constructors
    public ScwList() {
    }

    public ScwList(String[] scwIDs) throws Exception {
	setScwIDs(scwIDs);
    }

    public ScwList(String[] scwIDs, double[] ras, double[] decs) throws Exception {
	setScwIDs(scwIDs);
	setCoords(ras, decs);
    }

    public ScwList(String[] scwIDs, double[] ras, double[] decs, double[] durations) throws Exception {
	setScwIDs(scwIDs);
	setCoords(ras, decs);
	setDurations(durations);
    }

    public ScwList(String[] ids, double[] ras, double[] decs, double[] durations, double[] effectiveExps) throws Exception {
	setScwIDs(ids);
	setCoords(ras, decs);
	setDurations(durations);
	setEffectiveExps(effectiveExps);
    }

    public ScwList(String filename) throws Exception {
	File file = new File(filename);
	new ScwList(file);
    }

    public ScwList(File file) throws Exception {
	if ( ! file.exists() ) {
	    throw new FileNotFoundException("File does not exist");
	}
	else {
	    readScwListFile(file);
	}	    
    }

    private void readScwListFile(File file) throws Exception {
	logger.info("Reading list of science windows");
	logger.info("  File: "+file.getCanonicalPath());
	Scanner in = new Scanner(new FileReader(file));
	ArrayList<String> scwIDList = new ArrayList<String>();
	while ( in.hasNextLine() ) {
	    String line = in.nextLine();
	    String scwID = line.substring(9,21);
	    scwIDList.add(scwID);
	}
	scwIDList.trimToSize();
	Collections.sort(scwIDList);
	Set<String> scwIDSet = new HashSet<String>(scwIDList);
	String[] ids = scwIDSet.toArray(new String[scwIDSet.size()]);
	if ( scwIDList.size() != scwIDSet.size() ) {
	    int duplicates = scwIDList.size() - scwIDSet.size();
	    logger.warn("Dropping "+duplicates+" duplicate science window IDs from list");
	}
	setScwIDs(ids);
    }

    private void setScwIDs(String[] ids) throws Exception {
	Arrays.sort(ids);
	this.scwIDs = Arrays.copyOf(ids, ids.length);
	this.nScws = this.scwIDs.length;
	this.revIDs = new String[this.nScws];
	this.revs = new int[this.nScws];
	ArrayList<String> revIDsList = new ArrayList<String>();
	ArrayList<Integer> revsList = new ArrayList<Integer>();
	for ( int i=0; i < this.nScws; i++ ) {
	    this.revIDs[i] = this.scwIDs[i].substring(0,4);
	    revIDsList.add(this.revIDs[i]);
	    Integer rev = new Integer(this.revIDs[i]);
	    revsList.add(rev);
	    this.revs[i] = rev.intValue();
	}
	Set<String> revIDsSet = new HashSet<String>(revIDsList);
	Set<Integer> revsSet = new HashSet<Integer>(revsList);
	this.uniqueRevIDs = revIDsSet.toArray(new String[revIDsSet.size()]);
	this.uniqueRevs = new int[revsSet.size()];
	Integer[] uniqueRevsInt = revsSet.toArray(new Integer[revsSet.size()]);
	for ( int i=0; i < revsSet.size() ; i++ ) {
	    this.uniqueRevs[i] = (int) uniqueRevsInt[i];
	}
	this.nUniqueRevs = this.uniqueRevs.length;
	this.scwIDsAreSet = true;
	printScwIDInfo();
    }
    
    private void setCoords(double[] ras, double[] decs) throws ArrayIndexOutOfBoundsException {
	if ( ras.length != decs.length ) {
	    throw new ArrayIndexOutOfBoundsException("Arrays of differnet lengths: ras.length != decs.length");
	}
	this.ras = Arrays.copyOf(ras, ras.length);     
	this.decs = Arrays.copyOf(decs, decs.length);	
	this.coords = new Point2D.Double[ras.length];
	for ( int i=0; i < ras.length; i++ ) {
	    this.coords[i] = new Point2D.Double(this.ras[i], this.decs[i]);
	}
	coordsAreSet = true;
    }

    private void setDurations(double[] durations) {
	this.durations = Arrays.copyOf(durations, durations.length);	
	this.totalDuration = sum(durations);
	durationsAreSet = true;
    }

    private void setEffectiveExps(double[] effectiveExps) {
	this.effectiveExps = Arrays.copyOf(effectiveExps, effectiveExps.length);	
	this.totalEffectiveExp = sum(effectiveExps);
	effectiveExpsAreSet = true;
    }

    private double sum(double[] data) {
	double sum = 0;
	for ( int i=0; i < data.length; i++ ) {
	    sum += data[i];
	}
	return sum;
    }

    //  Public methods
    public int nScws() {
	return this.nScws;
    }

    public int firstRev() {
	return this.revs[0];
    }

    public int lastRev() {
	return this.revs[this.nScws-1];
    }
    
    public double totalDuration() throws IntegralException {
	if ( !durationsAreSet ) {
	    throw new IntegralException("Durations are not set");
	}
	return this.totalDuration;
    }

    public double totalEffectiveExp() throws IntegralException {
	if ( !durationsAreSet ) {
	    throw new IntegralException("EffectiveExps are not set");
	}
	return this.totalEffectiveExp;
    }
    
    public String[] getRevIDs() throws IntegralException {
	if ( !this.scwIDsAreSet ) {
	    throw new IntegralException("Scw IDs are not set");
	}
	return Arrays.copyOf(this.revIDs, this.revIDs.length);
    }

    public int[] getRevs() throws IntegralException {
	if ( !this.scwIDsAreSet ) {
	    throw new IntegralException("Scw IDs are not set");
	}
	return Arrays.copyOf(this.revs, this.revs.length);
    }

    public String[] getUniqueRevIDs() throws IntegralException {
	if ( !this.scwIDsAreSet ) {
	    throw new IntegralException("Scw IDs are not set");
	}
	return Arrays.copyOf(this.uniqueRevIDs, this.uniqueRevIDs.length);
    }

    public int[] getUniqueRevs() throws IntegralException {
	if ( !this.scwIDsAreSet ) {
	    throw new IntegralException("Scw IDs are not set");
	}
	return Arrays.copyOf(this.uniqueRevs, this.uniqueRevs.length);
    }

    public String[] getScwIDs() throws IntegralException {
	if ( !this.scwIDsAreSet ) {
	    throw new IntegralException("Scw IDs are not set");
	}
	return Arrays.copyOf(this.scwIDs, this.scwIDs.length);
    }

    public double[] getRas() throws IntegralException {
	if ( !this.coordsAreSet ) {
	    throw new IntegralException("Coordinates are not set");
	}
	return Arrays.copyOf(this.ras, this.ras.length);
    }

    public double[] getDecs() throws IntegralException {
	if ( !this.coordsAreSet ) {
	    throw new IntegralException("Coordinates are not set");
	}
	return Arrays.copyOf(this.decs, this.decs.length);
    }

    public Point2D.Double[] getCoords() throws IntegralException {
	if ( !this.coordsAreSet ) {
	    throw new IntegralException("Coordinates are not set");
	}
	return Arrays.copyOf(this.coords, this.coords.length);
    }

    public double[] getDurations() throws IntegralException {
	if ( !this.durationsAreSet ) {
	    throw new IntegralException("Durations are not set");
	}
	return Arrays.copyOf(this.durations, this.durations.length);
    }

    public double[] getEffectiveExps() throws IntegralException {
	if ( !this.effectiveExpsAreSet ) {
	    throw new IntegralException("Effective exposures are not set");
	}
	return Arrays.copyOf(this.effectiveExps, this.effectiveExps.length);
    }

    public void write(String filename) throws IOException, IntegralException {
	if ( ! this.scwIDsAreSet ) throw new IntegralException("Scw IDs are not set: there is nothing to write");
	File file = new File(filename);
	PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file), bufferSize));
	String rev = null;
	String line = null;
	String sufix = "swg.fits[1]";
	for ( int i=0; i < this.nScws; i++ ) {
	    line = "scw" +sep+ this.revIDs[i] +sep+ this.scwIDs[i]+ ".001" +sep+ sufix;
	    pw.println(line);
	}
	pw.close();
	logger.info("List written to "+file.getCanonicalPath());
    }

    private void printScwIDInfo() {
	logger.info("Scw IDs are set:");
	logger.info("  Pointings = "+this.nScws);
	logger.info("  Revolutions = "+this.nUniqueRevs);
    }

}
