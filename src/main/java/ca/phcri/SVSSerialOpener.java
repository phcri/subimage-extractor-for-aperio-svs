package ca.phcri;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;

import java.io.IOException;

import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.in.SVSReader;
import loci.plugins.util.ImageProcessorReader;


public class SVSSerialOpener implements PlugIn {
	
	
	private String dir;
	private String name;
	private String id;
	private int imageWidth;
	private int imageHeight;
	ChannelSeparator r;
	
	

	@Override
	public void run(String arg0) {
		chooseSVS();
		
		
		ColorProcessor cp = new ColorProcessor(imageWidth, imageHeight);
		ImagePlus imp = new ImagePlus(name + " Series 2 (RGB)", cp);
		imp.show();
		
		int tileSize = 256 * 5;
		int tilesHol = (int) (imageWidth / tileSize);
		int tilesVert = (int) (imageHeight / tileSize);
		
		for (int i = 0; i < tilesVert; i++){
			for (int j = 0; j < tilesHol; j++){
				addSubimage(cp, tileSize * j, tileSize * i, tileSize, tileSize);
				imp.updateAndDraw();
			}
			
			addSubimage(cp, tileSize * tilesHol, tileSize * i, 
					imageWidth - tileSize * tilesHol, tileSize);
			imp.updateAndDraw();
		}
		
		for(int j = 0; j < tilesHol; j++){
			addSubimage(cp, tileSize * j, tileSize * tilesVert,
					tileSize, (imageHeight - tileSize * tilesVert));
			imp.updateAndDraw();
		}
		
		addSubimage(cp, tileSize * tilesHol, tileSize * tilesVert,
				imageWidth - tileSize * tilesHol, imageHeight - tileSize * tilesVert);
		imp.updateAndDraw();
		
		closeReader(r);
		
	}
	
	void chooseSVS(){
		OpenDialog od = new OpenDialog("Open SVS File...");
		dir = od.getDirectory();
		name = od.getFileName();
		id = dir + name;
		
		
		r = new ChannelSeparator();
		
		try {
			r.setId(id);
			r.setSeries(1);
			imageWidth = (int) r.getSizeX();
			imageHeight = (int) r.getSizeY();
		}catch (FormatException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
		catch (IOException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
		
	}
	
	
	
	void addImage(ColorProcessor cp){
		byte[] R = new byte[imageWidth * imageHeight];
		byte[] G = new byte[imageWidth * imageHeight];
		byte[] B = new byte[imageWidth * imageHeight];
		
		try{
			IJ.showStatus("Opening Red channel");
			R = r.openBytes(0);
			IJ.showStatus("Opening Green channel");
			G = r.openBytes(1);
			IJ.showStatus("Opening Blue channel");
			B = r.openBytes(2);
		} catch (FormatException exc) {
			// TODO Auto-generated catch block
			exc.printStackTrace();
		} catch (IOException exc) {
			// TODO Auto-generated catch block
			exc.printStackTrace();
		}	
		
		IJ.showStatus("Stacking to RGB");
		cp.setRGB(R, G, B);
	}
	
	
	void addSubimage(ColorProcessor cp, int x, int y, int subWidth, int subHeight){
		
		byte[] R = new byte[subWidth * subHeight];
		byte[] G = new byte[subWidth * subHeight];
		byte[] B = new byte[subWidth * subHeight];
		
		try{
			R = r.openBytes(0, x, y, subWidth, subHeight);
			G = r.openBytes(1, x, y, subWidth, subHeight);
			B = r.openBytes(2, x, y, subWidth, subHeight);
		} catch (FormatException exc) {
			// TODO Auto-generated catch block
			exc.printStackTrace();
		} catch (IOException exc) {
			// TODO Auto-generated catch block
			exc.printStackTrace();
		}	

		updatePixels(cp, x, y, subWidth, subHeight, R, G, B);
	}
	
	
	
	void closeReader(ChannelSeparator r){
		try {
			r.close();
		} catch (IOException exc) {
			// TODO Auto-generated catch block
			exc.printStackTrace();
		}
	}
	
	void closeReader(ImageProcessorReader r){
		try {
			r.close();
		} catch (IOException exc) {
			// TODO Auto-generated catch block
			exc.printStackTrace();
		}
	}
	
	void updatePixels(ColorProcessor cp, int x, int y, int width, int height, 
			byte[] R, byte[] G, byte[] B){
		for(int i = 0; i < width; i++){
			for(int j = 0; j < height; j++){
				int k = i + width * j;
				cp.putPixel(x + i, y + j,  
						0xff000000 | ((R[k]&0xff)<<16) | ((G[k]&0xff)<<8) | B[k]&0xff);
			}//referring to the source of ColorProcessor.setRGB(R, G, B);
		}
	}
	
	void updatePixels(ColorProcessor cp, int x, int y, int width, int height, 
			int[] intArray){
		for(int i = 0; i < width; i++)
			for(int j = 0; j < height; j++)
				cp.putPixel(x +i, y + j, intArray[i + width * j]);
	}
	
} 
