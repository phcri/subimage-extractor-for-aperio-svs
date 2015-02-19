package ca.phcri;

import java.io.IOException;

import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.plugins.util.LociPrefs;
import ij.IJ;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

public class SVSStack extends ImageStack {
	int nSlices;
	ChannelSeparator r;
	int tilesHol;
	int tilesVert;
	int originX, originY;
	int width;
	int height;
	int series;
	String name;
	ColorProcessor cp;
	String directory;

	/** Creates a new, empty virtual stack */
	
	public SVSStack(String directory, String name, int series, int originX, int originY, 
			int roiWidth, int roiHeight, int width, int height){
		//IJ.log("constructor");
		
		
		this.originX = originX;
		this.originY = originY;
		this.width = width;
		this.height = height;
		this.name = name;
		this.directory = directory;
		this.series = series;
		tilesHol = (int) (roiWidth / this.width + 1);
		tilesVert = (int) (roiHeight / this.height + 1);
		nSlices = tilesHol * tilesVert;
		//IJ.log("constructor2");
		r = new ChannelSeparator(LociPrefs.makeImageReader());
		//IJ.log("constructor3");
		try {
			//IJ.log("SVSStack constructor");
			r.setId(this.directory + this.name);
			r.setSeries(this.series);
			
		}
		catch (FormatException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
		catch (IOException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
		//ij.log("constructor end");
	}
	
	 /** Does nothing. */
		public void addSlice(String sliceLabel, Object pixels) {
		}

		/** Does nothing.. */
		public void addSlice(String sliceLabel, ImageProcessor ip) {
		}
		
		/** Does noting. */
		public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
		}
		
		public Object getPixels(int n) {
			ImageProcessor ip = getProcessor(n);
			if (ip!=null)
				return ip.getPixels();
			else
				return null;
		}
		
		public void deleteSlice(int n) {
			
			}
		
		/** Deletes the last slice in the stack. */
		public void deleteLastSlice() {
			
		}
		
		/** Assigns a pixel array to the specified slice,
		were 1<=n<=nslices. */
	public void setPixels(Object pixels, int n) {
	}
		
		 /** Returns an ImageProcessor for the specified slice,
		were 1<=n<=nslices. Returns null if the stack is empty.
	*/
	public ImageProcessor getProcessor(int n) {
		//ij.log("getProcessor: "+ n);
		int i = (n - 1) % tilesHol;
		int j = (int) ((n - 1) / tilesHol);
		
		//check if originX and/or originY >= 0;
		int subimageX = originX + width * i;
		int subimageY = originY + height * j;
		
		//ij.log("slice number: " + n);
		////ij.log("getProcessor i: " + i);
		//ij.log("getProcessor j: " + j);
		//ij.log("getProcessor originX: " + originX);
		//ij.log("getProcessor originY: " + originY);
		//ij.log("getProcessor width: " + width);
		//ij.log("getProcessor height: " + height);
			
		try {
			byte[] R = r.openBytes(0, subimageX, subimageY, 
					width, height);
			byte[] G = r.openBytes(1, subimageX, subimageY, 
					width, height);
			byte[] B = r.openBytes(2, subimageX, subimageY, 
					width, height);
			cp = new ColorProcessor(width, height);
			cp.setRGB(R, G, B);
			
		} catch (FormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		/*
		if (imp!=null) {
			int w = imp.getWidth();
			int h = imp.getHeight();
			int type = imp.getType();
			ColorModel cm = imp.getProcessor().getColorModel();
		} else
		*/
		if(cp == null)
			return null;
		return cp;
		
	 }
	
	public int getSize() {
		return nSlices;
	}

	/** Returns the file name of the Nth image. */
	public String getSliceLabel(int n) {
		 return name + " slice " + n;
	}
	
	/** Returns null. */
	public Object[] getImageArray() {
		return null;
	}

   /** Does nothing. */
	public void setSliceLabel(String label, int n) {
	}

	/** Always return true. */
	public boolean isVirtual() {
		return true;
	}

   /** Does nothing. */
	public void trim() {
	}
		
	
}