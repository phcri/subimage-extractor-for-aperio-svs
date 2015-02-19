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
	int cacheForCurrentSlice = 2;
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
				
				if(sliceNumber >= cacheCenter + moveInterval){
					
					for(int k = 0; k < cacheSize; k++)
						
						if(k - moveInterval > 0 
								&& cachedIPs[k] != null){
							
							cachedIPs[k - moveInterval]
									= (ImageProcessor) cachedIPs[k].clone();
						
							cachedSlices[k - moveInterval]
									= cachedSlices[k];
						
						}else{
							cachedIPs[k] = null;
							cachedSlices[k] = -1;
						}
					
					IJ.log("thread " + relativePosToCurrent + " has hit the cache " + cacheNo + 
							" of the slice " + (sliceNumber - relativePosToCurrent));
					IJ.log("The cache space has been shifted to the left");
					IJ.log("opening the latch");
					startSignal.countDown();
					return cachedIPs[cacheNo - moveInterval];
				}
				
				
				if(sliceNumber <= cacheCenter - moveInterval){
					
					for(int k = cacheSize - 1; k >= 0; k--)
						
						if(k + moveInterval < cacheSize
								&& cachedIPs[k] != null){
							
							cachedIPs[k + moveInterval]
									= (ImageProcessor) cachedIPs[k].clone();
						
							cachedSlices[k + moveInterval]
									= cachedSlices[k];
						
						}else{
							cachedIPs[k] = null;
							cachedSlices[k] = -1;
						}
					
					IJ.log("thread " + relativePosToCurrent + " has hit the cache " + cacheNo + 
							" of the slice " + (sliceNumber - relativePosToCurrent));
					IJ.log("The cache space has been shifted to the right");
					IJ.log("opening the latch");
					startSignal.countDown();
					return cachedIPs[cacheNo + moveInterval];
				}
				

				IJ.log("thread " + relativePosToCurrent + " has hit the cache " + cacheNo + 
						" of the slice " + (sliceNumber - relativePosToCurrent));
				IJ.log("No shift of the cache space");
				IJ.log("opening the latch");
				startSignal.countDown();
				return cachedIPs[cacheNo];
			
			}
			
		}
		
	} else {
		
		for(int cacheNo = 0; cacheNo < cacheSize; cacheNo++){
			
			if(sliceNumber == cachedSlices[cacheNo]){
				
				IJ.log("thread " + relativePosToCurrent + " has hit the cache " + cacheNo + 
						" of the slice " + (sliceNumber - relativePosToCurrent));
				
				return cachedIPs[cacheNo];
			}
			
		}
		
	}
	
	IJ.log("thread " + relativePosToCurrent + " of "
			+ "the slice " + (sliceNumber - relativePosToCurrent) + " did not hit the cache");
			
	
	
	
	int positionH = (sliceNumber - 1) % noSubHol;
	int positionV = (int) (sliceNumber / noSubHol);
	
	//check if originX and/or originY >= 0;
	int subimageX = originX + width * positionH;
	int subimageY = originY + height * positionV;
		
	
	try {
		byte[] R = r.openBytes(0, subimageX, subimageY, 
				width, height);
		byte[] G = r.openBytes(1, subimageX, subimageY, 
				width, height);
		byte[] B = r.openBytes(2, subimageX, subimageY, 
				width, height);
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