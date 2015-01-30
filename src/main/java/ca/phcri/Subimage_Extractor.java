package ca.phcri;

/*
 * #%L
 * This plugin is written to extract subimages from Aperio SVS by 
 * modifying the Read_Image.java.
 * (http://www.openmicroscopy.org/site/support/bio-formats5/developers/java-library.html)
 * (https://github.com/openmicroscopy/bioformats/blob/v5.0.6/components/bio-formats-plugins/utils/Read_Image.java).
 * #L%
 */

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import java.io.IOException;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LociPrefs;
import ij.process.ImageConverter;
import ij.gui.GenericDialog;
import ij.gui.*;
import java.awt.*;


/**
 * An ImageJ plugin that uses Bio-Formats to open and save regions of SVS file at highest magnification.
 */

public class Subimage_Extractor implements PlugIn, DialogListener {
	private ImageProcessorReader r;
	private String dir, name, id;
	private int xStart, yStart;
	private static int width = 1028, height = 768;
	
	
	public void run(String arg) {
		openImage(arg);
		askSettings();
	}
	
	void askSettings() {
		GenericDialog gd = new GenericDialog("Subimage location and size...");
		
		gd.addNumericField("xStart:", xStart, 0);
		gd.addNumericField("yStart:", yStart, 0);
		gd.addNumericField("Subimage Width:", width, 0);
		gd.addNumericField("Subimage Height:", height, 0);
		
		gd.addDialogListener(this);
		gd.showDialog();
		
		if (gd.wasCanceled()) return;
		if (gd.wasOKed()){
			openSubimage();
		}
	}
	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		xStart = (int) gd.getNextNumber();
		yStart = (int) gd.getNextNumber();
		width = (int) gd.getNextNumber();
		height = (int) gd.getNextNumber();
		return true;
	}
	
	void openImage(String arg) {
		OpenDialog od = new OpenDialog("Open Image File...", arg);
		dir = od.getDirectory();
		name = od.getFileName();
		id = dir + name;
		r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
	}
	
	void openSubimage(){
		try {
			IJ.showStatus("Examining file " + name);
			r.setId(id);
			r.setSeries(0);      
			int num = r.getImageCount();
			
			ImageStack stack = new ImageStack(width, height);

			for (int i=0; i<num; i++) {
				IJ.showStatus("Reading image plane #" + (i + 1) + "/" + num);
				ImageProcessor ip = r.openProcessors(i, xStart, yStart, width, height)[0];

				stack.addSlice("" + (i + 1), ip);
			}
			IJ.showStatus("Constructing image");
			ImagePlus imp = new ImagePlus(name + ", subimage starting at (x = " + xStart + ", y = " + yStart + ")", stack);
			

			new ImageConverter(imp).convertRGBStackToRGB();
			imp.show();
			
			r.close();
			IJ.showStatus("");
			
		}
		catch (FormatException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
		catch (IOException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
	}
}