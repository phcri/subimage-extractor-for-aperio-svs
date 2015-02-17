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
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

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

import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.plugins.config.SpringUtilities;
import loci.plugins.util.LociPrefs;


/*
 * An ImageJ plugin that uses Bio-Formats to open and save 
 * regions of SVS file at highest magnification.
 */

public class Subimage_Extractor2 implements 
PlugIn, DialogListener, ActionListener, MouseMotionListener, DocumentListener {


	private String dir, name, id;
	private static int subWidth = 4800, subHeight = 3200;
	
	private static ImagePlus impThumb;

	private JFrame rg;
	private static int subsStartX, subsStartY;
	private static final String[] subimagesLocatedBy = 
		{"Random offset", "Fix to the upper left corner of the ROI", "Manual Location"};
	private static final int RANDOM = 0,  STARTINGPOINT= 1, MANUAL = 2;
	private static String location = subimagesLocatedBy[STARTINGPOINT];
	
	private Random random = new Random(System.currentTimeMillis());
	private int imageWidth, imageHeight;
	private int thumbWidth, thumbHeight;
	private double ratioImageThumbX, ratioImageThumbY;
	private String err = "";
	private static Image iconImg;
	private Component[] compGroup1, compGroup2;
	private static final int[] manualFields = { 6, 7, 8, 9 };
	private JTextField inputX ,inputY, inputWidth, inputHeight;
	private boolean inputByMouseDragged;
	protected boolean mouseReleased;
	private int actRoiX, actRoiY, actRoiWidth, actRoiHeight;
	private int noSubVert;
	private int noSubHol;

	
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
		ChannelSeparator r = new ChannelSeparator(LociPrefs.makeImageReader());

		
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
			
			byte[] R = r.openThumbBytes(0);
			byte[] G = r.openThumbBytes(1);
			byte[] B = r.openThumbBytes(2);
			
			IJ.showStatus("Constructing image");
			ColorProcessor cp = new ColorProcessor(thumbWidth, thumbHeight);
			cp.setRGB(R, G, B);
					
			impThumb = new ImagePlus("thumbnail of " + name, cp);
			impThumb.show();
			
			ImageCanvas ic = impThumb.getCanvas();
			ic.addMouseListener(
					new MouseAdapter(){
						@Override
						public void mouseReleased(MouseEvent e){
							inputByMouseDragged = false;
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
			inputByMouseDragged = true;
			Rectangle selectionRect = sectionLocation.getBounds();
			int rectX = selectionRect.x;
			int rectY = selectionRect.y;
			int rectWidth = selectionRect.width;
			int rectHeight = selectionRect.height;
			
			actRoiX = (int) (rectX * ratioImageThumbX);
			actRoiY = (int) (rectY * ratioImageThumbY);
			actRoiWidth = (int) (rectWidth * ratioImageThumbX);
			actRoiHeight = (int) (rectHeight * ratioImageThumbY);
			
			inputX.setText(String.valueOf(actRoiX));
			inputY.setText(String.valueOf(actRoiY));
			inputWidth.setText(String.valueOf(actRoiWidth));
			inputHeight.setText(String.valueOf(actRoiHeight));
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
			JTextField tf = new JTextField();
			tf.getDocument().addDocumentListener(this);
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
		
		
		compGroup1 = p2.getComponents();
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
	
	
	@Override
	public void insertUpdate(DocumentEvent e) {
		drawRoi();
	}
	
	@Override
	public void removeUpdate(DocumentEvent e) {
		drawRoi();
	}
	
	@Override
	public void changedUpdate(DocumentEvent e) {
		drawRoi();
	}
	
	
	void drawRoi(){
		if(inputByMouseDragged) return;
		
		err = "";
		
		actRoiX = Integer.parseInt(inputX.getText());
		actRoiY = Integer.parseInt(inputY.getText());
		actRoiWidth = Integer.parseInt(inputWidth.getText());
		actRoiHeight = Integer.parseInt(inputHeight.getText());
		
		int rectX = (int) (actRoiX / ratioImageThumbX);
		int rectY = (int) (actRoiY	/ ratioImageThumbY);
		int rectWidth = (int) (actRoiWidth / ratioImageThumbX);
		int rectHeight = (int) (actRoiHeight / ratioImageThumbY);
		
		if(actRoiX + actRoiWidth > imageWidth ||
				actRoiY + actRoiHeight	> imageHeight){
			err += "ROI should be within the image";
		}
		
		if(!"".equals(err)) {
			IJ.showStatus(err);
			return;
			}
		
		impThumb.setRoi(rectX, rectY, rectWidth, rectHeight);
	}

	
	
	@Override
	public void actionPerformed(ActionEvent e){		
		if("b1OK".equals(e.getActionCommand())){
			Roi sectionLocation = impThumb.getRoi();
			
			if(sectionLocation == null){
				IJ.error("No selection");
			} else {
				
				if("".equals(err)){
					sectionLocation.setName("section");
					sectionLocation.setStrokeColor(Color.yellow);
					impThumb.setOverlay(new Overlay(sectionLocation));
					rg.dispose();
					
					askSettings();
				} else 
					IJ.error("Subimage Extractor ", err);
				
			}
		}
		
		
		if("b2Cancel".equals(e.getActionCommand())){
			impThumb.close();
			rg.dispose();
		}
		
	}
	
	
	
	void askSettings() {
		GenericDialog gd = new GenericDialog("Subimage Size and Location...");
		gd.addNumericField("Subimage Width:", subWidth, 0);
		gd.addNumericField("Subimage Height:", subHeight, 0);
		gd.addRadioButtonGroup("Location of Subimages: ", subimagesLocatedBy,
				3, 1, subimagesLocatedBy[RANDOM]);
		gd.addNumericField("subsStartX", 0, 0);
		gd.addNumericField("subsStartY", 0, 0);
		
		
		compGroup2 = gd.getComponents();
		
		
		for (int i : manualFields)
			compGroup2[i].setEnabled(false);
		
		
		
		
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
				impThumb.close();
			}
		}
	}
	
	
	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		subWidth = (int) gd.getNextNumber();
		subHeight = (int) gd.getNextNumber();
		location = gd.getNextRadioButton();
		subsStartX = (int) gd.getNextNumber();
		subsStartY = (int) gd.getNextNumber();
		
		err = "";
		IJ.showStatus(err);
		
		if(subWidth < 1 || subHeight < 1){
			err += "Subimage Width and Height should be positive values";
			IJ.showStatus(err);
			return true;
		}
		
		noSubVert = (int) (actRoiHeight / subHeight + 1);
		noSubHol = (int) (actRoiWidth / subWidth + 1);
		
		if(noSubVert * noSubHol > 1000)
			err += "Not allowed to have more than 1000 slices";
		
		if(location.equalsIgnoreCase(subimagesLocatedBy[MANUAL])){
			for (int i : manualFields)
				compGroup2[i].setEnabled(true);
		} else {
			for (int i : manualFields)
				compGroup2[i].setEnabled(false);
			
			if(location.equals(subimagesLocatedBy[RANDOM])){
				subsStartX = random.nextInt(subWidth) - subWidth + actRoiX;
				subsStartY = random.nextInt(subHeight) - subHeight + actRoiY;
				
			} else if(location.equals(subimagesLocatedBy[STARTINGPOINT])){
				subsStartX = actRoiX;
				subsStartY = actRoiY;
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
		//IJ.log("opening");
		SVSStack ss = new SVSStack(dir, name, 0, subsStartX, subsStartY, 
				subWidth, subHeight, noSubHol, noSubVert);
		ImagePlus imp = new ImagePlus(name, ss);
		imp.show();
		
		drawSubimagesOnThumb();
		IJ.showStatus("");
		showParameters();
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
			int subimageY = subsStartY + subHeight * m;
			for (int n = 0; n < noSubHol; n++) {
				int subimageX = subsStartX + subWidth * n;
				Roi roi = new Roi(
						(int) (subimageX/ratioImageThumbX), 
						(int) (subimageY/ratioImageThumbY), 
						(int) (subWidth/ratioImageThumbX), 
						(int) (subHeight/ratioImageThumbY));
		roi.setName("Sub" + (noSubHol * m + n + 1));
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
						 actRoiX + "\t" + actRoiY + "\t" + 
						 actRoiWidth + "\t" + actRoiHeight + "\t" + 
						subsStartX + "\t" + subsStartY + "\t" + 
						subWidth + "\t" + subHeight + "\t\t\t\t";
			
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