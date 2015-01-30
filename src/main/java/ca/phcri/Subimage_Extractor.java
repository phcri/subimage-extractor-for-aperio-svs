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
	private static int width = 1028, height = 768, noSub;

	ImageProcessorReader r ;
	ImagePlus impThumb;
	Roi sectionLocation;

	PlugInFrame rg;

	
	
	public void run(String arg) {
		openImage(arg);
	}
	
	void askSettings() {
		GenericDialog gd = new GenericDialog("Subimage location and size...");
		
		//gd.addNumericField("xStart:", xStart, 0);
		//gd.addNumericField("yStart:", yStart, 0);
		gd.addNumericField("Subimage Width:", width, 0);
		gd.addNumericField("Subimage Height:", height, 0);
		gd.addNumericField("Number of Subimages", noSub, 0);
		
		gd.addDialogListener(this);
		gd.showDialog();
		
		if (gd.wasCanceled()) return;
		if (gd.wasOKed()){
			r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
			openSubimage();
		}
	}
	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		//xStart = (int) gd.getNextNumber();
		//yStart = (int) gd.getNextNumber();
		width = (int) gd.getNextNumber();
		height = (int) gd.getNextNumber();
		noSub = (int) gd.getNextNumber();
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
			Overlay ol = impThumb.getOverlay();
			Roi roi = new Roi(0, 0, (int) width/71, (int) height/71);
			
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