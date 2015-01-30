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
 * An ImageJ plugin that uses Bio-Formats to open and save regions of SVS file at highest magnification.
 */

public class Subimage_Extractor implements PlugIn, DialogListener, ActionListener {
	private String dir, name, id;
	private int startX, startY;
	private static int subWidth = 1028, subHeight = 768, noSubH = 1, noSubV = 1, spaceH, spaceV;
	private static String specification, location;
	
	private ImagePlus impThumb;
	Roi sectionLocation;

	PlugInFrame rg;
	//private Rectangle selectionRect;
	private static int subsStartX, subsStartY;
	private static final String[] subimagesSpecifiedBy = 
		{"Subimage Number", "Spacing between Subimages"};
	private static final int NUMBER = 0, SPACING = 1;

	private static final String[] subimagesLocatedBy = 
		{"Random offset", "Starting at x = 0, y = 0", "Manual Location"};
	private static final int RANDOM = 0,  STARTINGPOINT= 1, MANUAL = 2;
	private Random random = new Random(System.currentTimeMillis());
	private int appX;
	private int appY;
	private int widthImage;
	private int heightImage;
	private int widthThumb;
	private int heightThumb;
	private double ratioImageThumb;
	private int rectX;
	private int rectY;
	private int rectWidth;
	private int rectHeight;
	
	
	public void run(String arg) {
		openImage(arg);
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
			r.setSeries(0);
			widthImage = r.getSizeX();
			heightImage = r.getSizeY();
			widthThumb = r.getThumbSizeX();
			heightThumb = r.getThumbSizeY();
			ratioImageThumb = widthImage/widthThumb;

			int num = r.getImageCount();
			
			ImageStack stack = new ImageStack(widthThumb, heightThumb);

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
				impThumb.setOverlay(new Overlay(sectionLocation));
				rg.dispose();

				Rectangle selectionRect = sectionLocation.getBounds();
				rectX = selectionRect.x;
				rectY = selectionRect.y;
				rectWidth = selectionRect.width;
				rectHeight = selectionRect.height;
				IJ.log("selection" + selectionRect.x + selectionRect.y + selectionRect.width + selectionRect.height);
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
		gd.addNumericField("Number of Subimages Horizontally", noSubH, 0);
		gd.addNumericField("Number of Subimages Vertically", noSubV, 0);
		gd.addNumericField("Spacing between Subimages Horizontally", 0, 0);
		gd.addNumericField("Spacing between Subimages Vertically", 0, 0);
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
		noSubH = (int) gd.getNextNumber();
		noSubV = (int) gd.getNextNumber();
		spaceH = (int) gd.getNextNumber();
		spaceV = (int) gd.getNextNumber();
		location = gd.getNextRadioButton();
		subsStartX = (int) gd.getNextNumber();
		subsStartY = (int) gd.getNextNumber();
		
		if(specification.equals(subimagesSpecifiedBy[NUMBER])){
			if(noSubH == 1){
				spaceH = 0;
				spaceV = 0;
			} else {
				spaceH = (int) (rectWidth/(noSubH - 1) - subWidth);
				spaceV = (int) (rectHeight/(noSubV - 1) - subHeight);
				//actual area taken by subimages are (noSubH - 1) * (noSubV - 1)
			}
		}
		
		appX = subWidth + spaceH;
		appY = subHeight + spaceV;
		
		if(location.equals(subimagesLocatedBy[RANDOM])){
//			subsStartX = (int) (random.nextInt(appX) - appX + selectionRect.x * ratioImageThumb);
//			subsStartY = (int) (random.nextInt(appY) - appY + selectionRect.y * ratioImageThumb);
		} else if(location.equals(subimagesLocatedBy[STARTINGPOINT])){
			subsStartX = (int) (rectX * ratioImageThumb);
			subsStartY = (int) (rectY * ratioImageThumb);
		}
		
		return true;
	}

	
	void openSubimages(){
		ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
		
		try {
			IJ.showStatus("Examining file " + name);
			r.setId(id);
			r.setSeries(0);      
			int num = r.getImageCount();
			
			ImageStack stack = new ImageStack(subWidth, subHeight);
			
			startX = subsStartX;
			startY = subsStartY;

			for (int i=0; i<num; i++) {
				IJ.showStatus("Reading image plane #" + (i + 1) + "/" + num);
				ImageProcessor ip = r.openProcessors(i, startX, startY, subWidth, subHeight)[0];

				stack.addSlice("" + (i + 1), ip);
			}
			IJ.showStatus("Constructing image");
			ImagePlus imp = new ImagePlus(name + ", subimage starting at (x = " + startX + ", y = " + startY + ")", stack);
			

			new ImageConverter(imp).convertRGBStackToRGB();
			
			imp.show();
			Overlay ol = impThumb.getOverlay();
			Roi roi = new Roi((int) startX/ratioImageThumb, (int) startY/ratioImageThumb, (int) subWidth/ratioImageThumb, (int) subHeight/ratioImageThumb);
			
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