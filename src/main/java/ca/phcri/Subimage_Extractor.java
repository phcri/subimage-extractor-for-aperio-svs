package ca.phcri;

/* 
 * This plugin is written to extract subimages from Aperio SVS by 
 * modifying the Read_Image.java.
 * (http://www.openmicroscopy.org/site/support/bio-formats5/developers/java-library.html)
 * (https://github.com/openmicroscopy/bioformats/blob/v5.0.6/
 * components/bio-formats-plugins/utils/Read_Image.java).
 */

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.plugins.config.SpringUtilities;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LociPrefs;
import ij.process.ImageConverter;
import ij.text.TextPanel;
import ij.text.TextWindow;
import ij.gui.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


/*
 * An ImageJ plugin that uses Bio-Formats to open and save 
 * regions of SVS file at highest magnification.
 */

public class Subimage_Extractor implements 
//PlugIn, DialogListener, ActionListener, DocumentListener {
PlugIn, DialogListener, ActionListener, MouseMotionListener {


	private String dir, name, id;
	private static int subWidth = 1028, subHeight = 768,
			noSubHor = 3, noSubVert = 3, spaceHor, spaceVert;
	
	private static ImagePlus impThumb;

	private JFrame rg;
	private static int subsStartX, subsStartY;
	private static final String[] subimageSpacingSpecifiedBy = 
		{"the number of Subimages", "Space between Subimages"};
	private static final int NUMBER = 0, SPACE = 1;
	private static String spacing = subimageSpacingSpecifiedBy[NUMBER];
	private static final String[] subimagesLocatedBy = 
		{"Random offset", "Fix to the upper left corner of the ROI", "Manual Location"};
	private static final int RANDOM = 0,  STARTINGPOINT= 1, MANUAL = 2;
	private static String location = subimagesLocatedBy[STARTINGPOINT];
	
	private Random random = new Random(System.currentTimeMillis());
	private int appX, appY;
	private int imageWidth, imageHeight;
	private int thumbWidth, thumbHeight;
	private double ratioImageThumbX, ratioImageThumbY;
	private int rectX, rectY;
	private int rectWidth, rectHeight;
	private String err;
	private CheckboxGroup cg1;
	private static Image iconImg;
	private Component[] compGroup1, compGroup2;
	private static boolean cg1EqualsNumber = true;
	private static final int[] numberFields = {6, 7, 8, 9};
	private static final int[] spaceFields = {11, 12, 13, 14};
	private static final int[] manualFields = {17, 18, 19, 20};
	private boolean spacingFieldChange = false;
	private int count = 2;
	private TextField noSubHorInput, noSubVertInput;
	private TextField spaceHorInput, spaceVertInput;
	private JTextField inputX ,inputY, inputWidth, inputHeight;
	private boolean inputByMouseDragged;
	private int actionCount = 3;
	protected boolean mouseReleased;
	private static boolean openInStack = true;
	
	@Override
	public void run(String arg) {
		if(iconImg == null) getIconImage();
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
			
			ImageStack stackInRGB = new ImageStack(thumbWidth, thumbHeight);

			for (int i=0; i<num; i++) {
				IJ.showStatus("Reading image plane #" + (i + 1) + "/" + num);
				ImageProcessor ip = r.openThumbProcessors(i)[0];
				stackInRGB.addSlice("" + (i + 1), ip);
			}
			
			IJ.showStatus("Constructing image");
			impThumb = new ImagePlus("thumbnail of " + name, stackInRGB);
			

			new ImageConverter(impThumb).convertRGBStackToRGB();
			
			impThumb.show();
			
			ImageCanvas ic = impThumb.getCanvas();
			ic.addMouseListener(
					new MouseAdapter(){
						@Override
						public void mouseReleased(MouseEvent e){
							mouseReleased = true;
							IJ.log("mouseReleased");
							if(mouseReleased) IJ.log("mouseReleased true");
						}
					}
				);
			
			ic.addMouseMotionListener(this);
			
			ImageWindow iw = impThumb.getWindow();
			iw.addWindowListener(
					new WindowAdapter(){
						@Override
						public void windowClosing(WindowEvent e){
							impThumb.close();
							rg.dispose();
							}
						}
					);
			
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
	
	@Override
	public void mouseDragged(MouseEvent e) {
		Roi sectionLocation = impThumb.getRoi();
		
		if(sectionLocation != null){
			
			Rectangle selectionRect = sectionLocation.getBounds();
			rectX = selectionRect.x;
			rectY = selectionRect.y;
			rectWidth = selectionRect.width;
			rectHeight = selectionRect.height;
			
			inputX.setText(String.valueOf((int) (rectX * ratioImageThumbX)));
			inputY.setText(String.valueOf((int) (rectY * ratioImageThumbY)));
			inputWidth.setText(String.valueOf((int) (rectWidth * ratioImageThumbX)));
			inputHeight.setText(String.valueOf((int) (rectHeight * ratioImageThumbY)));
		}
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}
	
	
	void getIconImage(){
		ImageJ ij = IJ.getInstance();
		iconImg = ij.getIconImage();
	}
	
	void roiGetter(){
		rg = new JFrame("set ROI");
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.PAGE_AXIS));
		rg.setIconImage(iconImg);
		
		JPanel p1 = new JPanel();
		JLabel lab1 = new JLabel("<html>Draw a rectangle to cover <BR>" +
				"a region of interest and press \"OK\". <BR>"
				+ "You can also specify the ROI by inputting values <BR>"
				+ "in the boxes below</html>");
		p1.add(lab1);
		
		JPanel p2 = new JPanel(new SpringLayout());
		String[] inputRoi = {"x", "y", "Width", "Height"};
		for (int i = 0; i < 4; i++){
			JLabel l = new JLabel(inputRoi[i], SwingConstants.TRAILING);
			p2.add(l);
			JTextField tf = new JTextField(6);
			//tf.getDocument().addDocumentListener(this);
			l.setLabelFor(tf);
			p2.add(tf);
		}
		
		SpringUtilities.makeCompactGrid(p2, 4, 2, 6, 6, 6, 6);
		p2.setBorder(new TitledBorder("ROI location and size"));
		
		JPanel p3 = new JPanel();
		JButton b1 = new JButton("OK");
		JButton b2 = new JButton("Cancel");
		b1.addActionListener(this);
		b2.addActionListener(this);
		b1.setActionCommand("b1OK");
		b2.setActionCommand("b2Cancel");
		p3.add(b1);
		p3.add(b2);
		
		p.add(p1);
		p.add(p2);
		p.add(p3);
		
		
		Component[] compGroup1 = p2.getComponents();
		inputX = (JTextField) compGroup1[1];
		inputY = (JTextField) compGroup1[3];
		inputWidth = (JTextField) compGroup1[5];
		inputHeight = (JTextField) compGroup1[7];

		
		rg.setContentPane(p);
		
		rg.addWindowListener(
				new WindowAdapter(){
					@Override
					public void windowClosing(WindowEvent e){
						impThumb.close();
						rg.dispose();
					}
				}
		);

		rg.pack();
		rg.setVisible(true);	
	}
	
	/*
	@Override
	public void insertUpdate(DocumentEvent e) {
		drawRoi(e);
	}
	
	@Override
	public void removeUpdate(DocumentEvent e) {
		drawRoi(e);
	}
	
	@Override
	public void changedUpdate(DocumentEvent e) {
		drawRoi(e);
	}
	
	
	void drawRoi(DocumentEvent e){
		IJ.log("drawRoi actionCount" + actionCount);
		
		if(inputByMouseDragged){ 
			if(!mouseReleased) {
				IJ.log("mouse button being pressed");
				return;
			}else {
				IJ.log(e.getDocument().getDefaultRootElement().getName());
				IJ.log("mouse released");
			}
		}
		
		{
			
			if(actionCount < 3) {
				actionCount ++;
				return;
			} else {
				//inputByMouseDragged = false;
				//return;
			}
			
			IJ.log("inputByMouseDragged true");
			
		
			
		 else {
			int valueX = 
					(int) (Integer.parseInt(inputX.getText()) / ratioImageThumbX);
			int valueY = 
					(int) (Integer.parseInt(inputY.getText()) / ratioImageThumbY);
			int valueWidth = 
					(int) (Integer.parseInt(inputWidth.getText()) / ratioImageThumbX);
			int valueHeight = 
					(int) (Integer.parseInt(inputHeight.getText()) / ratioImageThumbY);
			
			impThumb.setRoi(valueX, valueY, valueWidth, valueHeight);
			
			IJ.log("inputByMouseDragged false");
		}
		
		IJ.log("action performed");

	}
	*/
	
	
	@Override
	public void actionPerformed(ActionEvent e){		
		if("b1OK".equals(e.getActionCommand())){
			Roi sectionLocation = impThumb.getRoi();
			
			if(sectionLocation != null){
				sectionLocation.setName("section");
				sectionLocation.setStrokeColor(Color.yellow);
				impThumb.setOverlay(new Overlay(sectionLocation));
				rg.dispose();
				
				askSettings();
			
			} else
				IJ.error("No selection");
		}
		
		if("b2Cancel".equals(e.getActionCommand())){
			impThumb.close();
			rg.dispose();
		}
		
	}
	
	
	
	void askSettings() {
		
		if(appX == 0){
			noSubHor = 3;
			noSubVert = 3;
			spaceHor = 
					(int) ((rectWidth * ratioImageThumbX + subWidth)/noSubHor
							- subWidth);
			spaceVert = 
					(int) ((rectHeight * ratioImageThumbY + subHeight)/noSubVert
							- subHeight);
			/*
			appX = subWidth + spaceHor;
			appY = subHeight + spaceVert;
			subsStartX = (int) (rectX * ratioImageThumbX);
			subsStartY = (int) (rectY * ratioImageThumbY);
			*/
		}
		
		GenericDialog gd = new GenericDialog("Subimage Size and Location...");
		gd.addNumericField("Subimage Width:", subWidth, 0);
		gd.addNumericField("Subimage Height:", subHeight, 0);
		
		gd.setInsets(10, 10, 0);
		gd.addMessage("Spacing of Subimages: ");
		cg1 = new CheckboxGroup();
		addRadioButton(gd, subimageSpacingSpecifiedBy[NUMBER], cg1, cg1EqualsNumber);
		gd.addNumericField("Horizontally", noSubHor, 0);
		gd.addNumericField("Vertically", noSubVert, 0);
		
		addRadioButton(gd, subimageSpacingSpecifiedBy[SPACE], cg1, !cg1EqualsNumber);
		gd.addNumericField("Horizontally", spaceHor, 0);
		gd.addNumericField("Vertically", spaceVert, 0);
		
		
		gd.addRadioButtonGroup("Location of Subimages: ", subimagesLocatedBy,
				3, 1, subimagesLocatedBy[RANDOM]);
		gd.addNumericField("subsStartX", 0, 0);
		gd.addNumericField("subsStartY", 0, 0);
		gd.addCheckbox("Open in Stack", openInStack);
		
		compGroup2 = gd.getComponents();
		
		if(cg1EqualsNumber){
			for (int i : numberFields)
				compGroup2[i].setEnabled(true);
			for (int i : spaceFields)
				compGroup2[i].setEnabled(false);
		} else {
			for (int i : numberFields)
				compGroup2[i].setEnabled(false);
			for (int i : spaceFields)
				compGroup2[i].setEnabled(true);
		}
		
		for (int i : manualFields)
			compGroup2[i].setEnabled(false);
		
		//parts to avoid flickering
		noSubHorInput = (TextField) compGroup2[7];
		noSubVertInput = (TextField) compGroup2[9];
		spaceHorInput = (TextField) compGroup2[12];
		spaceVertInput = (TextField) compGroup2[14];
		
		//drawSubimagesOnThumb();
		gd.addDialogListener(this);
		gd.showDialog();
		
		if (gd.wasCanceled()) {
			impThumb.close();
			return;
		}
		if (gd.wasOKed()){
			if("".equals(err))
				openSubimages();
			else {
				IJ.error("Subimage Extractor " + err);
				appX = 0;
				impThumb.close();
			}
		}
	}
	
	
	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		//parts to avoid flickering
		//Because two numeric fields are changed automatically,
		//there are two unnecessary cycles of calculations triggered by the listener.
		if(count < 2) {
			count ++;
			return true;
		}
		
		subWidth = (int) gd.getNextNumber();
		subHeight = (int) gd.getNextNumber();
		spacing = radioButtonCheck(cg1);
		int currentNoSubHor = (int) gd.getNextNumber();
		int currentNoSubVert = (int) gd.getNextNumber();
		int currentSpaceHor = (int) gd.getNextNumber();
		int currentSpaceVert = (int) gd.getNextNumber();
		location = gd.getNextRadioButton();
		subsStartX = (int) gd.getNextNumber();
		subsStartY = (int) gd.getNextNumber();
		openInStack = gd.getNextBoolean();
		err = "";
		
		//parts to avoid flickering
		if(currentNoSubHor != noSubHor || currentNoSubVert != noSubVert || 
				currentSpaceHor != spaceHor || currentSpaceVert != spaceVert){
			spacingFieldChange = true;
			count = 0;
		}
		
		noSubHor = currentNoSubHor;
		noSubVert = currentNoSubVert;
		spaceHor = currentSpaceHor;
		spaceVert = currentSpaceVert;
		
		cg1EqualsNumber = subimageSpacingSpecifiedBy[NUMBER].equals(spacing);

		if(cg1EqualsNumber){
			for (int i : numberFields)
				compGroup2[i].setEnabled(true);
			for (int i : spaceFields)
				compGroup2[i].setEnabled(false);
			if(noSubHor <= 0 || noSubVert <= 0){
				err = "Number of Subimages should be positive";
				spacingFieldChange = false;
				count = 2;
			} else {
				spaceHor = 
					(int) ((rectWidth * ratioImageThumbX + subWidth)/noSubHor - 
							subWidth);
				
				spaceVert = 
					(int) ((rectHeight * ratioImageThumbY + subHeight)/noSubVert - 
							subHeight);
				
				if(spacingFieldChange){
					spaceHorInput.setText(String.valueOf(spaceHor));
					spaceVertInput.setText(String.valueOf(spaceVert));
					spacingFieldChange = false;
				}
			}
		}
		
		
		appX = subWidth + spaceHor;
		appY = subHeight + spaceVert;
		
		if(!cg1EqualsNumber){
			for (int i : numberFields)
				compGroup2[i].setEnabled(false);
			for (int i : spaceFields)
				compGroup2[i].setEnabled(true);
			
			noSubHor = (int) (rectWidth * ratioImageThumbX/ appX) + 1;
			noSubVert = (int) (rectHeight * ratioImageThumbY / appY) + 1;
			
			if(spacingFieldChange){
				noSubHorInput.setText(String.valueOf(noSubHor));
				noSubVertInput.setText(String.valueOf(noSubVert));
				spacingFieldChange = false;
			}
		}
		
		
		if(noSubHor * noSubVert > 500)
			err = "Not allowed to open more than 500 subimages";
		
		
		if(location.equalsIgnoreCase(subimagesLocatedBy[MANUAL])){
			for (int i : manualFields)
				compGroup2[i].setEnabled(true);
		} else {
			for (int i : manualFields)
				compGroup2[i].setEnabled(false);
			
			if(location.equals(subimagesLocatedBy[RANDOM])){
				subsStartX = (int) (random.nextInt(appX) - subWidth + 
						rectX * ratioImageThumbX);
				subsStartY = (int) (random.nextInt(appY) - subHeight + 
						rectY * ratioImageThumbY);
			} else if(location.equals(subimagesLocatedBy[STARTINGPOINT])){
				subsStartX = (int) (rectX * ratioImageThumbX);
				subsStartY = (int) (rectY * ratioImageThumbY);
				spacingFieldChange = false;
			}
		}
		
		if(!"".equals(err)) {
			IJ.showStatus(err);
			return true;
		}
		
		drawSubimagesOnThumb();
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
			
			ImageStack stackOutput = new ImageStack(subWidth, subHeight);
			
			for(int m = 0; m < noSubVert; m++) {
				int subimageY = subsStartY + appY * m;
				for (int n = 0; n < noSubHor; n++) {
					int subimageX = subsStartX + appX * n;
					
					ImageStack stackInRGB = new ImageStack(subWidth, subHeight);
					
					for (int i=0; i<num; i++) {
						IJ.showStatus("Reading image plane #" + (i + 1) + "/" + num);
						ImageProcessor ip = 
								r.openProcessors(i, subimageX, subimageY, 
										subWidth, subHeight)[0];

						stackInRGB.addSlice("" + (i + 1), ip);
					}
					
					IJ.showStatus("Constructing image");
					ImagePlus imp = 
							new ImagePlus(name + ", subimage " + (noSubHor * m + n + 1) +
									" (x = " + subimageX + ", y = " +	subimageY + ")", 
									stackInRGB);
					
					new ImageConverter(imp).convertRGBStackToRGB();
					
					if(openInStack)
						stackOutput.addSlice(imp.getProcessor());
					else
						imp.show();
				}
			}
			
			if(openInStack){
				ImagePlus impOut = new ImagePlus(name + "_SubimageStack", stackOutput);
				impOut.show();
			}
				
			
			r.close();
			drawSubimagesOnThumb();
			IJ.showStatus("");
			showParameters();
		}
		catch (FormatException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
		catch (IOException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
	}
	
	
	
	void drawSubimagesOnThumb(){
		Overlay ol = impThumb.getOverlay();
		
		Roi[] Rois = ol.toArray();
		for(Roi roi : Rois){
			String roiName = roi.getName();
			if(roiName != null && roiName.startsWith("Sub")){
				ol.remove(roi);
			}
		}
		
		for(int m = 0; m < noSubVert; m++) {
			int subimageY = subsStartY + appY * m;
			for (int n = 0; n < noSubHor; n++) {
				int subimageX = subsStartX + appX * n;
				Roi roi = new Roi((int) (subimageX/ratioImageThumbX), 
						(int) (subimageY/ratioImageThumbY), 
						(int) (subWidth/ratioImageThumbX), 
						(int) (subHeight/ratioImageThumbY));
		roi.setName("Sub" + (noSubHor * m + n + 1));
		roi.setStrokeColor(Color.green);
		ol.addElement(roi);
			}
		}
		
		ol.drawNames(true);
		ol.setLabelColor(Color.gray);
		ol.setLabelFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
		impThumb.setOverlay(ol);
	}
	
	
	
	void addRadioButton(GenericDialog gd, String item, 
			CheckboxGroup cg, boolean selected){
		Panel panel = new Panel();
		panel.setLayout(new GridLayout(1, 1, 0, 0));

        Checkbox cb = new Checkbox(item, cg, selected);
        cb.addItemListener(gd);
        panel.add(cb);
        
        Insets insets = new Insets(2, 20, 0, 0);

       gd.addPanel(panel, GridBagConstraints.WEST, insets);
	}
	
	
	 String radioButtonCheck(CheckboxGroup cg) {
	        Checkbox checkbox = cg.getSelectedCheckbox();
	        String item = "null";
	        if (checkbox!=null)
	            item = checkbox.getLabel();
	        return item;
	    }
	 
	 
	 
	 void showParameters(){
		 DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		 Date date = new Date();
		 String parameters = 
				 df.format(date) + "\t" + name + "\t" + 
						rectX + "\t" + rectY + "\t" + 
						rectWidth + "\t" + rectHeight + "\t" + 
						subsStartX + "\t" + subsStartY + "\t" + 
						subWidth + "\t" + subHeight + "\t" + 
						spaceHor + "\t" + spaceVert + "\t" + 
						noSubHor + "\t" + noSubVert;
			
			showHistory(parameters);
		}
	 
	 
	 
	 static void showHistory(String parameterOutput) {
			String windowTitle = "Subimage Extraction History";
				//ShowParameterWindow.java uses String "Grid History" without 
				//referring to this windowTitle variable,
				//so be careful to change the title this window. 
			String fileName = "SubimageExtractionHistory.txt";
			
			TextWindow historyWindow = (TextWindow) WindowManager.getWindow(windowTitle);
			
			if (historyWindow == null) {
				//make a new empty TextWindow with String windowTitle with headings
				historyWindow = new TextWindow(
						windowTitle,
						"Date \t Filename \t ROI starting point x \t ROI starting point y \t "
						+ "ROI width \t ROI height \t "
						+ "Subimage starting point x \t Subimage starting point y \t "
						+ "Subimage width \t Subimage height \t "
						+ "Space between subimages horizontally \t "
						+ "Space between subimages vertically \t "
						+ "No of subimages horizontally \t "
						+ "No of subimages vertically",
						"", 1028, 250);
				
				//If a file whose name is String fileName exists in the plugin folder, 
				//read it into the list.
				try {
					BufferedReader br = 
							new BufferedReader(
									new FileReader(IJ.getDirectory("plugins") + fileName)
							);
					boolean isHeadings = true;
					while (true) {
			            String s = br.readLine();
			            if (s == null) break;
			            if(isHeadings) {
			            	isHeadings = false;
			            	continue;
			            }
			            historyWindow.append(s);
					}
				} catch (IOException e) {}
			}
			
			if(parameterOutput != null){
				historyWindow.append(parameterOutput);
				
				//auto save the parameters into a file whose name is String fileName
				TextPanel tp = historyWindow.getTextPanel();
				tp.saveAs(IJ.getDirectory("plugins") + fileName);	
			}
		}

	


}