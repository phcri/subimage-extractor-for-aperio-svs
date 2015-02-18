package ca.phcri;

import ij.IJ;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SVSStack extends ImageStack{
	int nSlices;
	
	int noSubHol;
	int noSubVert;
	int originX, originY;
	int width;
	int height;
	int series;
	String name;
	String path;
	static int cachedSlice;
	static ImageProcessor cachedIp;
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
		return;
	}

	/** Does nothing.. */
	@Override
	public void addSlice(String sliceLabel, ImageProcessor ip) {
		return;
	}
	
	/** Does noting. */
	@Override
	public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
		return;
	}
	
	@Override
	public Object getPixels(int n) {
		ImageProcessor ip = getProcessor(n);
		if (ip!=null)
			return ip.getPixels();
		
		return null;
	}
	
	@Override
	public void deleteSlice(int n) {
		return;
		}
	
	/** Deletes the last slice in the stack. */
	@Override
	public void deleteLastSlice() {
		return;
	}
	
	/** Assigns a pixel array to the specified slice,
	were 1<=n<=nslices. */
	@Override
	public void setPixels(Object pixels, int n) {
		return;
	}
		
		 /** Returns an ImageProcessor for the specified slice,
		were 1<=n<=nslices. Returns null if the stack is empty.
	*/
	@Override
	public ImageProcessor getProcessor(int n) {
		IJ.log("\ngetProcessor called for slice " + n);
		if(n == cachedSlice){
			IJ.log("returning a clone of the cashed processor");
			if(cachedIp == null)
				return null;
			return (ImageProcessor) cachedIp.clone();
		}
		
		if(executor != null && !executor.isTerminated()){
			IJ.log("not terminated. Shutting down");
			executor.shutdownNow();
		}
		
		ImageProcessor ip = null;
		cachedSlice = -1;
		cachedIp = null;
		
		//Executors need to be created every time (old ones have been shutdown)
		int threadPoolSize = 2;
		executor = Executors.newFixedThreadPool(threadPoolSize);
		IJ.log("threadPoolSize " + threadPoolSize);
		
		List<Future<ImageProcessor>> ipList = 
				new ArrayList<Future<ImageProcessor>>();
		
		for(int i = 0; i < 2; i++){
			IJ.log("submitting an order to thread " + i);
			Future<ImageProcessor> future = 
					executor.submit(
							new DrawSubimage(n, i, path, series, 
									originX, originY, width, height,
									noSubHol, noSubVert)
							);
			ipList.add(future);
			
		}
		/*
		if(n > 1){
			IJ.log("starting thread -1");
			Future<ImageProcessor> future = 
				executor.submit(
						new DrawSubimage(n, -1, path, series, 
								originX, originY, width, height,
								noSubHol, noSubVert)
						);
			ipList.add(future);
		}
		*/
		executor.shutdown();
		
		try{
			ip = ipList.get(0).get();
			cachedSlice = n;
			cachedIp = ip;
		} catch (InterruptedException e){
			e.printStackTrace();
		} catch (ExecutionException e){
			e.printStackTrace();
		}
		
		return ip;
		
	 }
	
	@Override
	public int getSize() {
		return nSlices;
	}
	
	@Override
	/** Returns the file name of the Nth image. */
	public String getSliceLabel(int n) {
		 return name + " slice " + n;
	}
	
	@Override
	/** Returns null. */
	public Object[] getImageArray() {
		return null;
	}
	
	@Override
   /** Does nothing. */
	public void setSliceLabel(String label, int n) {
		return;
	}
	
	@Override
	/** Always return true. */
	public boolean isVirtual() {
		return true;
	}
	
	@Override
   /** Does nothing. */
	public void trim() {
		return;
	}

		
	
}