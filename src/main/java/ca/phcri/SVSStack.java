package ca.phcri;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.plugins.util.LociPrefs;
import ij.IJ;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

public class SVSStack extends ImageStack{
	int nSlices;
	
	int noSubHol;
	int noSubVert;
	int originX, originY;
	int width;
	int height;
	int series;
	String name;
	ImageProcessor ip;
	String path;
	static int cs1, cs2;
	static ImageProcessor cip1, cip2;
	ExecutorService executor;


	/** Creates a new, empty virtual stack */
	
	public SVSStack(String path, String name, int series, int originX, int originY, 
			int width, int height, int noSubHol, int noSubVert){
		//IJ.log("constructor");
		this.originX = originX;
		this.originY = originY;
		this.width = width;
		this.height = height;
		this.name = name;
		this.path = path;
		this.series = series;
		this.noSubHol = noSubHol;
		this.noSubVert = noSubVert;
		nSlices = noSubHol * noSubVert;
	}
	
	
	
	
	
	 /** Does nothing. */
		@Override
		public void addSlice(String sliceLabel, Object pixels) {
		}

		/** Does nothing.. */
		@Override
		public void addSlice(String sliceLabel, ImageProcessor ip) {
		}
		
		/** Does noting. */
		@Override
		public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
		}
		
		@Override
		public Object getPixels(int n) {
			ImageProcessor ip = getProcessor(n);
			if (ip!=null)
				return ip.getPixels();
			else
				return null;
		}
		
		@Override
		public void deleteSlice(int n) {
			
			}
		
		/** Deletes the last slice in the stack. */
		@Override
		public void deleteLastSlice() {
			
		}
		
		/** Assigns a pixel array to the specified slice,
		were 1<=n<=nslices. */
	@Override
	public void setPixels(Object pixels, int n) {
	}
		
		 /** Returns an ImageProcessor for the specified slice,
		were 1<=n<=nslices. Returns null if the stack is empty.
	*/
	@Override
	public ImageProcessor getProcessor(int n) {
		IJ.log("\ngetProcessor called for slice " + n);
		if(n == cs1){
			IJ.log("returning cip1");
			return cip1;
		}
		
		if(executor != null && !executor.isTerminated()){
			IJ.log("not terminated. Shutting down");
			executor.shutdownNow();
		}
		executor = Executors.newFixedThreadPool(2);
		
		List<Future<ImageProcessor>> ipList = new ArrayList<Future<ImageProcessor>>();
		for(int i = 0; i < 2; i++){
			IJ.log("starting thread " + i);
			Future<ImageProcessor> future = 
					executor.submit(
							new DrawSubimage(n, i, path, name, series, 
									originX, originY, width, height,
									noSubHol, noSubVert)
							);
			ipList.add(future);
			
		}
		
		executor.shutdown();
		
		try{
			ip = ipList.get(0).get();
		} catch (InterruptedException e){
			IJ.log("terminated by shutdownNow");
			e.printStackTrace();
		} catch (ExecutionException e){
			e.printStackTrace();
		}
		
		cs1 = n;
		cip1 = ip;
		return ip;
		
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