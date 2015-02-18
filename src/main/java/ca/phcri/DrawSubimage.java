package ca.phcri;

import ij.IJ;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.io.IOException;
import java.util.concurrent.Callable;

import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.plugins.util.LociPrefs;


public class DrawSubimage implements Callable<ImageProcessor>{
	private int sliceN, cacheN;
	private int noSubHol, noSubVert;
	private int width, height;
	private int originX, originY;
	private ChannelSeparator r;
	private static int[] cachedSlices = new int[3];
	private static ImageProcessor[] cachedProcessors = new ImageProcessor[3]; 

	DrawSubimage(int startSlice, int cacheN, String path, String name, int series,
			int originX, int originY, int width, int height, 
			int noSubHol, int noSubVert){
		
		this.sliceN = startSlice + cacheN;
		this.cacheN = cacheN;
		this.originX = originX;
		this.originY = originY;
		this.width = width;
		this.height =height;
		this.noSubHol = noSubHol;
		this.noSubVert = noSubVert;
		
		
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
	
public ImageProcessor call() throws Exception {
	if(sliceN == cachedSlices[0]){
		cachedProcessors[2] = (ImageProcessor) cachedProcessors[1].clone();
		cachedProcessors[1] = (ImageProcessor) cachedProcessors[0].clone();
		cachedProcessors[0] = null;
		
		cachedSlices[2] = cachedSlices[1];
		cachedSlices[1] = cachedSlices[0];
		cachedSlices[0] = -1;
		
		return cachedProcessors[1];
	}
	

	for(int cached = 1; cached < cachedSlices.length; cached++){
		if(sliceN == cachedSlices[cached]){
			
			for(int k = 0; k < cachedSlices.length; k++)
				if(cached + k - 1 < cachedSlices.length){
					cachedProcessors[k] = 
							(ImageProcessor) cachedProcessors[cached + k - 1].clone();
					cachedSlices[k] = cachedSlices[cached + k - 1];
				}else{
					cachedProcessors[k] = null;
					cachedSlices[k] = -1;
				}
			IJ.log("called cache" + cached + " of " + (sliceN - cacheN));
			return cachedProcessors[1];
		}
	}
	
	
	
	int i = (sliceN - 1) % noSubHol;
	int j = (int) (sliceN / noSubVert);
	
	//check if originX and/or originY >= 0;
	int subimageX = originX + width * i;
	int subimageY = originY + height * j;
		
	
	try {
		byte[] R = r.openBytes(0, subimageX, subimageY, 
				width, height);
		byte[] G = r.openBytes(1, subimageX, subimageY, 
				width, height);
		byte[] B = r.openBytes(2, subimageX, subimageY, 
				width, height);
		ColorProcessor cp = new ColorProcessor(width, height);
		cp.setRGB(R, G, B);
		
		cachedSlices[cacheN + 1] = sliceN;
		cachedProcessors[cacheN + 1] = cp;
		r.close();
		IJ.log("processed cache " + cacheN + " of " + (sliceN - cacheN));
		return cp;
		
	} catch (FormatException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
	r.close();
	return null;
}
}