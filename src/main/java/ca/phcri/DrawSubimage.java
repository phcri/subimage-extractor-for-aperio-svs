package ca.phcri;

import ij.IJ;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.plugins.util.LociPrefs;


public class DrawSubimage implements Callable<ImageProcessor>{
	private int sliceNumber, relativePosToCurrent;
	private int noSubHol;
	//private int noSubVert;
	private int width, height;
	private int originX, originY;
	private ChannelSeparator r;
	private final static int cacheSize = 9;
	private final static int cacheCenter = (int) ((cacheSize -1) / 2);
	private final static int moveInterval = 3;
	private static int cacheForCurrentSlice;
	private static int[] cachedSlices = new int[cacheSize];
	private static ImageProcessor[] cachedIPs = new ImageProcessor[cacheSize]; 
	private final CountDownLatch startSignal;

	DrawSubimage(int currentSlice, int relativePosToCurrent, String path, int series,
			int originX, int originY, int width, int height, 
			int noSubHol, int noSubVert, CountDownLatch startSignal){
		
		this.sliceNumber = currentSlice + relativePosToCurrent;
		this.relativePosToCurrent = relativePosToCurrent;
		this.originX = originX;
		this.originY = originY;
		this.width = width;
		this.height =height;
		this.noSubHol = noSubHol;
		//this.noSubVert = noSubVert;
		this.startSignal = startSignal;
		
		
		r = new ChannelSeparator(LociPrefs.makeImageReader());
		//IJ.log("constructor3");
		try {
			//IJ.log("SVSStack constructor");
			r.setId(path);
			
			r.setSeries(series);
			
		}
		catch (FormatException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
		catch (IOException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
	}
	
@Override
public ImageProcessor call() throws Exception {
	IJ.log("thread " + relativePosToCurrent + " of "
			+ "the slice " + (sliceNumber - relativePosToCurrent) + " has been started");
	//checking if hitting the cache and replace data in the cache space
	//the replacement is separately done 
	//depending on sign of (cacheNo - cacheForCurrentSlice)
	
	if(relativePosToCurrent == 0){
	
		for(int cacheNo = 0; cacheNo < cacheSize; cacheNo++){
			
			if(sliceNumber == cachedSlices[cacheNo]){
				
				IJ.log("thread " + relativePosToCurrent + 
						" of the slice " + (sliceNumber - relativePosToCurrent) + 
						" has hit the cache " + cacheNo);
				
				if(cacheNo >= cacheCenter + moveInterval){
					
					for(int k = 0; k < cacheSize; k++)
						
						if(k - moveInterval > 0 
								&& cachedIPs[k] != null){
							
							cachedIPs[k - moveInterval]
									= (ImageProcessor) cachedIPs[k].clone();
						
							cachedSlices[k - moveInterval]
									= cachedSlices[k];
						
						}else{
							cachedIPs[k] = null;
							
							cachedSlices[k] = 0;
						}
					
					IJ.log("The cache space has been shifted to the left");
					
					cacheForCurrentSlice = cacheNo - moveInterval;
					
					IJ.log("opening the latch");
					
					startSignal.countDown();
					
					return (ImageProcessor) cachedIPs[cacheForCurrentSlice].clone();
				}
				
				
				if(cacheNo <= cacheCenter - moveInterval){
					
					for(int k = cacheSize - 1; k >= 0; k--)
						
						if(k + moveInterval < cacheSize
								&& cachedIPs[k] != null){
							
							cachedIPs[k + moveInterval]
									= (ImageProcessor) cachedIPs[k].clone();
						
							cachedSlices[k + moveInterval]
									= cachedSlices[k];
						
						}else{
							cachedIPs[k] = null;
							
							cachedSlices[k] = 0;
						}
					
					IJ.log("The cache space has been shifted to the right");
					
					cacheForCurrentSlice = cacheNo + moveInterval;
					
					IJ.log("opening the latch");
					
					startSignal.countDown();
					
					return (ImageProcessor) cachedIPs[cacheForCurrentSlice].clone();
				}
				
				IJ.log("No shift of the cache space");
				
				cacheForCurrentSlice = cacheNo;
				
				IJ.log("opening the latch");
				
				startSignal.countDown();
				
				return (ImageProcessor) cachedIPs[cacheForCurrentSlice].clone();
			}
				
		}
		
		IJ.log("thread " + relativePosToCurrent + " of "
				+ "the slice " + (sliceNumber - relativePosToCurrent)
				+ " did not hit the cache");
		
		cacheForCurrentSlice = 
				cacheCenter + sliceNumber % moveInterval - moveInterval + 1;
		
		//formatting the cache space
		int[] newCachedSlices = new int[cacheSize];
		
		ImageProcessor[] newCachedIPs = new ImageProcessor[cacheSize];
		
		for(int i = 0; i < cacheSize; i++){
			
			for(int cacheNo = 0; cacheNo < cacheSize; cacheNo++){
				
				if(cachedSlices[cacheNo] == sliceNumber - cacheForCurrentSlice + i
						&& cachedIPs[cacheNo] != null){
					
					newCachedSlices[i] 
							= sliceNumber - cacheForCurrentSlice + i;
					
					newCachedIPs[i] 
							= (ImageProcessor) cachedIPs[cacheNo].clone();
				}
			}
		}
		
		cachedSlices = newCachedSlices;
		
		cachedIPs = newCachedIPs;
		
		IJ.log("opening the latch");
		
		startSignal.countDown();
		
		
	} else {
		
		for(int cacheNo = 0; cacheNo < cacheSize; cacheNo++){
			
			if(sliceNumber == cachedSlices[cacheNo]){
				
				IJ.log("thread " + relativePosToCurrent + 
						" of the slice " + (sliceNumber - relativePosToCurrent) + 
						" has hit the cache " + cacheNo);
				
				return (ImageProcessor) cachedIPs[cacheNo].clone();
			}
			
		}
		
		IJ.log("thread " + relativePosToCurrent + " of "
				+ "the slice " + (sliceNumber - relativePosToCurrent)
				+ " did not hit the cache");
		
	}
	
	
	
	int positionH = (sliceNumber - 1) % noSubHol;
	
	int positionV = (int) (sliceNumber / noSubHol);
	
	//check if originX and/or originY >= 0;
	int subimageX = originX + width * positionH;
	
	int subimageY = originY + height * positionV;
		
	
	try {
		byte[] R = r.openBytes(0, subimageX, subimageY, width, height);
		
		byte[] G = r.openBytes(1, subimageX, subimageY, width, height);
		
		byte[] B = r.openBytes(2, subimageX, subimageY, width, height);
		
		ColorProcessor cp = new ColorProcessor(width, height);
		
		cp.setRGB(R, G, B);
		
		cachedSlices[cacheForCurrentSlice + relativePosToCurrent]
				= sliceNumber;
		
		cachedIPs[cacheForCurrentSlice + relativePosToCurrent]
				= cp;
		r.close();
		
		IJ.log("finishing thread " + relativePosToCurrent + " of "
				+ "slice" + (sliceNumber - relativePosToCurrent));
		
		return cp;
		
	} catch (FormatException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
	IJ.log("closing r and returning null from " + relativePosToCurrent + " of "
			+ "slice" + (sliceNumber - relativePosToCurrent));
	
	r.close();
	
	return null;
}
}