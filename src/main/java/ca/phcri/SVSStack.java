package ca.phcri;

import ij.IJ;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
		if (IJ.debugMode) 
			IJ.log("\ngetProcessor called for slice " + n);
		
		if(n == cachedSlice){
			/*
			if (IJ.debugMode) 
				IJ.log("returning a clone of the cashed processor");
			
			if(cachedIp == null)
				return null;
			return (ImageProcessor) cachedIp.clone();
			*/
			
			if (IJ.debugMode) 
				IJ.log("returning the reference tothe cashed processor");
			
			return cachedIp;
		}
		
		
		if(executor != null && !executor.isTerminated()){
			if (IJ.debugMode) 
				IJ.log("not terminated. Shutting down");
			
			executor.shutdownNow();
		}
		
		ImageProcessor ip = null;
		cachedSlice = -1;
		cachedIp = null;
		
		//Executors need to be created every time (old ones have been shutdown)
		int threadPoolSize = 2;
		executor = Executors.newFixedThreadPool(threadPoolSize);
		if (IJ.debugMode) 
			IJ.log("threadPoolSize " + threadPoolSize);
		
		List<Future<ImageProcessor>> ipList = 
				new ArrayList<Future<ImageProcessor>>();
		
		CountDownLatch startSignal = new CountDownLatch(1);
		
		for(int i = 0; i < 3; i++){
			if (IJ.debugMode) 
				IJ.log("submitting an order to thread " + i + " in slice " + n);
			
			if(n + i > nSlices)
				break;
			
			Future<ImageProcessor> future = 
					executor.submit(
							new DrawSubimage(n, i, path, series, 
									originX, originY, width, height,
									noSubHol, noSubVert, startSignal)
							);
			ipList.add(future);
			
			try {
				if (IJ.debugMode) 
					IJ.log("comming to the latch");
				
				startSignal.await();
				
				if (IJ.debugMode) 
					IJ.log("the latch is open");
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	
		for(int i = -1; i > -2; i--){
			if(n + i > 0){
				if (IJ.debugMode) 
					IJ.log("starting thread " + i);
				
				Future<ImageProcessor> future = 
					executor.submit(
							new DrawSubimage(n, i, path, series, 
									originX, originY, width, height,
									noSubHol, noSubVert, startSignal)
							);
				ipList.add(future);
			}
		}
		
		executor.shutdown();
		
		if (IJ.debugMode) 
			IJ.log("executor shutdown called in getProcessor for slice " + n);
		
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