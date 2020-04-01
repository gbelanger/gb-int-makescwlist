import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.Fits;
import nom.tam.util.BufferedDataInputStream;

public final class PointingListWriter {

    public static void constructPointingList(String scwidxFilename) throws Exception {
	File scwidxFile = new File(scwidxFilename);
	constructPointingList(scwidxFile);
    }

    public static void constructPointingList(File scwidxFile) throws Exception {
	//  Get data from GNRL-SCWG-GRP-IDX.fits.gz
	Fits f = openFits(scwidxFile);
	BinaryTableHDU hdu = (BinaryTableHDU) f.getHDU(1);
	String[] scwid = (String[]) hdu.getColumn("SWID");
	String[] scwtype = (String[]) hdu.getColumn("SW_TYPE");
	float[] ra_scx = (float[]) hdu.getColumn("RA_SCX");
	float[] dec_scx = (float[]) hdu.getColumn("DEC_SCX");
	double[] telapse = (double[]) hdu.getColumn("TELAPSE");
	String[] ertFirst = (String[]) hdu.getColumn("ERTFIRST");
	String[] ertLast = (String[]) hdu.getColumn("ERTLAST");
	double[] tstart = (double[]) hdu.getColumn("TSTART");
	double[] tstop = (double[]) hdu.getColumn("TSTOP");
	byte[] spimode = (byte[]) hdu.getColumn("SPIMODE");
	byte[] ibismode = (byte[]) hdu.getColumn("IBISMODE");
	byte[] jmx1mode = (byte[]) hdu.getColumn("SPIMODE");
	byte[] jmx2mode = (byte[]) hdu.getColumn("SPIMODE");
	byte[] omcmode = (byte[]) hdu.getColumn("SPIMODE");
	float[] ra_scz = (float[]) hdu.getColumn("RA_SCZ");
	float[] dec_scz = (float[]) hdu.getColumn("DEC_SCZ");
	float[] pos_angle = (float[]) hdu.getColumn("POSANGLE");
	//  Write to file
	PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter("point.lis")));
	PrintWriter pw2 = new PrintWriter(new BufferedWriter(new FileWriter("bad_ibis.lis")));
	PrintWriter pw3 = new PrintWriter(new BufferedWriter(new FileWriter("bad_spi.lis")));
	PrintWriter pw4 = new PrintWriter(new BufferedWriter(new FileWriter("bad_jmx.lis")));
	PrintWriter pw5 = new PrintWriter(new BufferedWriter(new FileWriter("bad_omc.lis")));
	for ( int i=0; i < scwid.length; i++ ) {
	    String type = scwtype[i];
	    int ibis = ibismode[i];
	    int spi = spimode[i];
	    int jmx1 = jmx1mode[i];
	    int jmx2 = jmx2mode[i];
	    int omc = omcmode[i];
	    if ( type.equals("POINTING") ) {
		// IBIS
		if ( ibis == 41 || ibis == 42 || ibis == 43 ) {
		    //if ( telapse[i] >= 1800 && telapse[i] <= 4000 ) {
		    pw.println(scwid[i]+"\t"+ra_scx[i]+"\t"+dec_scx[i]+"\t"
			       +telapse[i]+"\t"+tstart[i]+"\t"+tstop[i]+"\t"+ertFirst[i]+"\t"+ertLast[i]+"\t"
			       +ra_scz[i]+"\t"+dec_scz[i]+"\t"+pos_angle[i]);
		    //}
		}
		else {
		    pw2.println(scwid[i]+"\t"+ra_scx[i]+"\t"+dec_scx[i]+"\t"
				+telapse[i]+"\t"+tstart[i]+"\t"+tstop[i]+"\t"
				+"\t"+ibis);
		}
		// SPI
		if ( spi != 41 ) {
		    pw3.println(scwid[i]+"\t"+ra_scx[i]+"\t"+dec_scx[i]+"\t"
				+telapse[i]+"\t"+tstart[i]+"\t"+tstop[i]+"\t"
				+spi);
		}
		// JEM-X
		boolean jmx1_ok = ( jmx1 == 41 || jmx1 ==42 || jmx1 == 43 || jmx1 == 44 || jmx1 == 45 );
		boolean jmx2_ok = ( jmx2 == 41 || jmx2 ==42 || jmx2 == 43 || jmx2 == 44 || jmx2 == 45 );
		if ( !jmx1_ok && !jmx2_ok ) {
		    pw4.println(scwid[i]+"\t"+ra_scx[i]+"\t"+dec_scx[i]+"\t"
				+telapse[i]+"\t"+tstart[i]+"\t"+tstop[i]+"\t"
				+jmx1+"\t"+jmx2);		    
		}
		// OMC
		boolean omc_ok = ( omc == 41 || omc == 42 || omc == 43 );
		if ( !omc_ok ) {
		    pw5.println(scwid[i]+"\t"+ra_scx[i]+"\t"+dec_scx[i]+"\t"
				+telapse[i]+"\t"+tstart[i]+"\t"+tstop[i]+"\t"
				+omc);		    
		}

	    }
	}
	pw.close();
    }    

    public static Fits openFits(File file) throws Exception {
	boolean isGzipped = isGzipped(file);
	BufferedDataInputStream dis = new BufferedDataInputStream(new FileInputStream(file));
	Fits fitsFile = new Fits(dis, isGzipped);
	return fitsFile;
    }
    
    public static boolean isGzipped(File file) throws IOException {
	InputStream in = new FileInputStream(file);
	int magic1 = in.read();
	int magic2 = in.read();
	in.close();
	return (magic1 == 0037 && magic2 == 0213);
    }
}
