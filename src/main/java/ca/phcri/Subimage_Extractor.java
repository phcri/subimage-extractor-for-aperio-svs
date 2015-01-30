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
import ij.plugin.frame.PlugInFrame;
import ij.process.ImageProcessor;

import java.io.IOException;

import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LociPrefs;
import ij.process.ImageConverter;
import ij.gui.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


/**
 * An ImageJ plugin that uses Bio-Formats to open and save regions of SVS file at highest magnification.
 */

public class Subimage_Extractor implements PlugIn, DialogListener, ActionListener {
	private String dir, name, id;
	private int xStart, yStart;
	private static int subWidth = 1028, subHeight = 768, noSubH, noSubV, spaceH, spaceV;
	private static String specification, location;
	
	ImageProcessorReader r ;
	ImagePlus impThumb;
	Roi sectionLocation;

	PlugInFrame rg;
	private static int xSubsStart, ySubsStart;
	private static final String[] subimagesSpecifiedBy = 
		{"Subimage Number", "Spacing between Subimages"};
	private static final int NUMBER = 0, SPACING = 1;

	private static final String[] subimagesLocatedBy = 
		{"Random offset", "Starting at x = 0, y = 0", "Manual Location"};
	private static final int RANDOM = 0,  STARTINGPOINT= 1, MANUAL = 2;

	
	
	public void run(String arg) {
		openImage(arg);
	}
	
	void askSettings() {
		GenericDialog gd = new GenericDialog("Subimage location and size...");
		
		//gd.addNumericField("xStart:", xStart, 0);
		//gd.addNumericField("yStart:", yStart, 0);
		gd.addNumericField("Subimage Width:", subWidth, 0);
		gd.addNumericField("Subimage Height:", subHeight, 0);
		
		gd.addRadioButtonGroup("Subimage selection: ", subimagesSpecifiedBy,
				2, 1, subimagesSpecifiedBy[NUMBER]);

		gd.addNumericField("Number of Subimages Horizontally", noSubH, 0);
		gd.addNumericField("Number of Subimages Vertically", noSubV, 0);
		gd.addNumericField("Spacing between Subimages Horizontally", 0, 0);
		gd.addNumericField("Spacing between Subimages Vertically", 0, 0);
		
		gd.addRadioButtonGroup("Subimage location: ", subimagesLocatedBy,
				3, 1, subimagesLocatedBy[RANDOM]);
		gd.addNumericField("xSubsStart", 0, 0);
		gd.addNumericField("ySubsStart", 0, 0);
		
		
		gd.addDialogListener(this);
		gd.showDialog();
		
		
		if (gd.wasCanceled()) return;
		if (gd.wasOKed()){
			r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
			openSubimage();
		}
	}
	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {

		subWidth = (int) gd.getNextNumber();
		subHeight = (int) gd.getNextNumber();
		
		specification = gd.getNextRadioButton();
		
		noSubH = (int) gd.getNextNumber();
		noSubV = (int) gd.getNextNumber();
		spaceH = (int) gd.getNextNumber();
		spaceV = (int) gd.getNextNumber();
		
		location = gd.getNextRadioButton();
		
		xSubsStart = (int) gd.getNextNumber();
		ySubsStart = (int) gd.getNextNumber();
		return true;
	}
	

	
	void openImage(String arg) {
		OpenDialog od = new OpenDialog("Open Image File...", arg);
		dir = od.getDirectory();
		name = od.getFileName();
		id = dir + name;	
		ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
		
		try {
			IJ.showStatus("Examining file " + name);
			r.setId(id);
			r.setSeries(3);      
			int num = r.getImageCount();
			
			ImageStack stack = new ImageStack(r.getSizeX(), r.getSizeY());

			for (int i=0; i<num; i++) {
				IJ.showStatus("Reading image plane #" + (i + 1) + "/" + num);
				ImageProcessor ip = r.openProcessors(i)[0];

				stack.addSlice("" + (i + 1), ip);
			}
			IJ.showStatus("Constructing image");
			impThumb = new ImagePlus("thumbnail of " + name, stack);
			

			new ImageConverter(impThumb).convertRGBStackToRGB();
			impThumb.show();
			
			r.close();
			IJ.showStatus("");
		}
		catch (FormatException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
		catch (IOException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
		
		roiGetter();
	}
	
	
	void roiGetter(){
		rg = new PlugInFrame("set ROI");
		rg.setSize(200, 100);
		Button b1 = new Button("OK");
		//Button b2 = new Button("Cancel");
		b1.addActionListener(this);
		//b2.addActionListener(this);
		
		rg.add(b1);
		//rg.add(b2);
		
		rg.setVisible(true);
	}
	
	public void actionPerformed(ActionEvent e){
		if("OK".equals(e.getActionCommand())){
			Roi sectionLocation = impThumb.getRoi();
			
			if(sectionLocation != null){
				sectionLocation.setName("section");
				impThumb.setOverlay(new Overlay(sectionLocation));
				rg.dispose();
				askSettings();
				Rectangle selectionRect = sectionLocation.getBounds();
				IJ.log("selection" + selectionRect.x + selectionRect.y + selectionRect.width + selectionRect.height);
			} else {
				IJ.error("No selection");
			}
			

		}
		if("Cancel".equals(e.getActionCommand()))
			rg.dispose();
	}
	
	void openSubimage(){
		try {
			IJ.showStatus("Examining file " + name);
			r.setId(id);
			r.setSeries(0);      
			int num = r.getImageCount();
			
			ImageStack stack = new ImageStack(subWidth, subHeight);

			for (int i=0; i<num; i++) {
				IJ.showStatus("Reading image plane #" + (i + 1) + "/" + num);
				ImageProcessor ip = r.openProcessors(i, xStart, yStart, subWidth, subHeight)[0];

				stack.addSlice("" + (i + 1), ip);
			}
			IJ.showStatus("Constructing image");
			ImagePlus imp = new ImagePlus(name + ", subimage starting at (x = " + xStart + ", y = " + yStart + ")", stack);
			

			new ImageConverter(imp).convertRGBStackToRGB();
			
			imp.show();
			Overlay ol = impThumb.getOverlay();
			Roi roi = new Roi(0, 0, (int) subWidth/71, (int) subHeight/71);
			
			ol.addElement(roi);
			impThumb.setOverlay(ol);
			
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