package ca.phcri;

/*
 * #%L
 * This plugin is written to extract subimages from Aperio SVS by 
 * modifying the Read_Image.java.
 * (http://www.openmicroscopy.org/site/support/bio-formats5/developers/java-library.html)
 * (https://github.com/openmicroscopy/bioformats/blob/v5.0.6/
 * components/bio-formats-plugins/utils/Read_Image.java).
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
import java.util.Random;

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
 * An ImageJ plugin that uses Bio-Formats to open and save 
 * regions of SVS file at highest magnification.
 */

public class Subimage_Extractor implements PlugIn, DialogListener, ActionListener {
	private String dir, name, id;
	private int startX, startY;
	private static int subWidth = 1028, subHeight = 768,
			noSubHor = 1, noSubVert = 1, spaceHor, spaceVert;
	private static String specification, location;
	
	private ImagePlus impThumb;
	Roi sectionLocation;

	PlugInFrame rg;
	private static int subsStartX, subsStartY;
	private static final String[] subimagesSpecifiedBy = 
		{"Subimage Number", "Spacing between Subimages"};
	private static final int NUMBER = 0, SPACE = 1;

	private static final String[] subimagesLocatedBy = 
		{"Random offset", "Starting at x = 0, y = 0", "Manual Location"};
	private static final int RANDOM = 0,  STARTINGPOINT= 1, MANUAL = 2;
	private Random random = new Random(System.currentTimeMillis());
	private int appX, appY;
	private int imageWidth, imageHeight;
	private int thumbWidth, thumbHeight;
	private double ratioImageThumbX, ratioImageThumbY;
	private int rectX, rectY;
	private int rectWidth, rectHeight;
	
	
	public void run(String arg) {
		openThumb(arg);
	}

	void openThumb(String arg) {
		OpenDialog od = new OpenDialog("Open Image File...", arg);
		dir = od.getDirectory();
		name = od.getFileName();
		id = dir + name;	
		ImageProcessorReader r = new ImageProcessorReader(
				new ChannelSeparator(LociPrefs.makeImageReader()));

		
		try {
			IJ.showStatus("Examining file " + name);
			r.setId(id);
			r.setSeries(0);
			imageWidth = r.getSizeX();
			imageHeight = r.getSizeY();
			thumbWidth = r.getThumbSizeX();
			thumbHeight = r.getThumbSizeY();
			ratioImageThumbX = imageWidth/thumbWidth;
			ratioImageThumbY = imageHeight/thumbHeight;

			int num = r.getImageCount();
			
			ImageStack stack = new ImageStack(thumbWidth, thumbHeight);

			for (int i=0; i<num; i++) {
				IJ.showStatus("Reading image plane #" + (i + 1) + "/" + num);
				ImageProcessor ip = r.openThumbProcessors(i)[0];

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
		b1.setSize(100, 100);
		Button b2 = new Button("Cancel");
		b2.setSize(50, 50);
		b1.addActionListener(this);
		b2.addActionListener(this);
		
		rg.add(b1);
		rg.add(b2);
		
		rg.setVisible(true);
	}
	
	public void actionPerformed(ActionEvent e){
		if("OK".equals(e.getActionCommand())){
			Roi sectionLocation = impThumb.getRoi();
			
			if(sectionLocation != null){
				sectionLocation.setName("section");
				sectionLocation.setColor(Color.yellow);
				impThumb.setOverlay(new Overlay(sectionLocation));
				rg.dispose();

				Rectangle selectionRect = sectionLocation.getBounds();
				rectX = selectionRect.x;
				rectY = selectionRect.y;
				rectWidth = selectionRect.width;
				rectHeight = selectionRect.height;
				askSettings();
			
			} else {
				IJ.error("No selection");
			}
			

		}
		if("Cancel".equals(e.getActionCommand())){
			impThumb.close();
			rg.dispose();
		}
	}
	
	
	void askSettings() {
		GenericDialog gd = new GenericDialog("Subimage location and size...");
		gd.addNumericField("Subimage Width:", subWidth, 0);
		gd.addNumericField("Subimage Height:", subHeight, 0);
		gd.addRadioButtonGroup("Subimage selection: ", subimagesSpecifiedBy,
				2, 1, subimagesSpecifiedBy[NUMBER]);
		gd.addNumericField("Number of Subimages Horizontally", noSubHor, 0);
		gd.addNumericField("Number of Subimages Vertically", noSubVert, 0);
		gd.addNumericField("Space between Subimages Horizontally", 0, 0);
		gd.addNumericField("Space between Subimages Vertically", 0, 0);
		gd.addRadioButtonGroup("Subimage location: ", subimagesLocatedBy,
				3, 1, subimagesLocatedBy[RANDOM]);
		gd.addNumericField("subsStartX", 0, 0);
		gd.addNumericField("subsStartY", 0, 0);
		
		gd.addDialogListener(this);
		gd.showDialog();
		
		if (gd.wasCanceled()) return;
		if (gd.wasOKed()){
			openSubimages();
		}
	}
	
	
	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		subWidth = (int) gd.getNextNumber();
		subHeight = (int) gd.getNextNumber();
		specification = gd.getNextRadioButton();
		noSubHor = (int) gd.getNextNumber();
		noSubVert = (int) gd.getNextNumber();
		spaceHor = (int) gd.getNextNumber();
		spaceVert = (int) gd.getNextNumber();
		location = gd.getNextRadioButton();
		subsStartX = (int) gd.getNextNumber();
		subsStartY = (int) gd.getNextNumber();
		
		if(specification.equals(subimagesSpecifiedBy[NUMBER])){
			if(noSubHor == 1)	spaceHor = 0;
			else spaceHor = (int) ((rectWidth * ratioImageThumbX + subWidth)/noSubHor - subWidth);
			
			if(noSubVert == 1) spaceVert = 0;
			else spaceVert = (int) ((rectHeight * ratioImageThumbY + subHeight)/noSubVert - subHeight);
		}
		
		appX = subWidth + spaceHor;
		appY = subHeight + spaceVert;
		
		//IJ.log("appX = " + appX + ", subWidth = " + subWidth + ", spaceHor = " + spaceHor);
		//IJ.log("appY = " + appY + ", subHeight = " + subHeight + ", spaceVert = " + spaceVert);
		
		if(specification.equals(subimagesSpecifiedBy[SPACE])){
			noSubHor = (int) (rectWidth / appX) + 1;
			noSubVert = (int) (rectHeight / appY) + 1;
		}
		
		if(location.equals(subimagesLocatedBy[RANDOM])){
			subsStartX = (int) (random.nextInt(appX) - subWidth + rectX * ratioImageThumbX);
			subsStartY = (int) (random.nextInt(appY) - subHeight + rectY * ratioImageThumbY);
		} else if(location.equals(subimagesLocatedBy[STARTINGPOINT])){
			subsStartX = (int) (rectX * ratioImageThumbX);
			subsStartY = (int) (rectY * ratioImageThumbY);
		}
		
		return true;
	}

	
	void openSubimages(){
		ImageProcessorReader r = 
				new ImageProcessorReader(
						new ChannelSeparator(
								LociPrefs.makeImageReader()
							)
					);
		
		try {
			IJ.showStatus("Examining file " + name);
			r.setId(id);
			r.setSeries(0);      
			int num = r.getImageCount();
			Overlay ol = impThumb.getOverlay();
			
			for(int m = 0; m < noSubVert; m++) {
				int subimageY = subsStartY + appY * m;
				for (int n = 0; n < noSubHor; n++) {
					int subimageX = subsStartX + appX * n;
					
					ImageStack stack = new ImageStack(subWidth, subHeight);
					
					for (int i=0; i<num; i++) {
						IJ.showStatus("Reading image plane #" + (i + 1) + "/" + num);
						ImageProcessor ip = 
								r.openProcessors(i, subimageX, subimageY, 
										subWidth, subHeight)[0];

						stack.addSlice("" + (i + 1), ip);
					}
					
					IJ.showStatus("Constructing image");
					ImagePlus imp = 
							new ImagePlus(name + ", subimage " + (noSubHor * m + n + 1) +
									" (x = " + subimageX + ", y = " +	subimageY + ")", 
									stack);
					
					new ImageConverter(imp).convertRGBStackToRGB();
					
					imp.show();
					
					Roi roi = new Roi((int) (subimageX/ratioImageThumbX), 
									(int) (subimageY/ratioImageThumbY), 
									(int) (subWidth/ratioImageThumbX), 
									(int) (subHeight/ratioImageThumbY));
					roi.setName("Sub" + (noSubHor * m + n + 1));
					roi.setColor(Color.green);
					ol.addElement(roi);
				}
			}
			
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