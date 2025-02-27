package com.marginallyclever.convenience;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Methods to make loading files from disk or jar resource easier.
 * @author Dan Royer
 */
public class FileAccess {
	/**
	 * Open a file.  open() looks in three places:<br>
	 *  - The file may be contained inside a zip, as indicated by the filename "zipname:filename".<br>
	 *  - The file may be a resource inside a jar file.
	 *  - The file may be on disk.
	 *     
	 * @param filename The file to open
	 * @return BufferedInputStream to the file contents
	 * @throws IOException file open failure
	 */
	public static BufferedInputStream open(String filename) throws IOException {
		int index = filename.lastIndexOf(":");
		int index2 = filename.lastIndexOf(":\\");  // hack for windows file system
		if(index!=-1 && index!=index2) {
			return loadFromZip(filename.substring(0, index), filename.substring(index+1,filename.length()));
		} else {
			return new BufferedInputStream(getInputStream(filename));
		}
	}
	
	
	private static InputStream getInputStream(String fname) throws IOException {
		InputStream s = FileAccess.class.getResourceAsStream(fname);
		if( s==null ) {
			s = new FileInputStream(new File(fname));
		}
		return s;
	}
	
	
	private static BufferedInputStream loadFromZip(String zipName,String fname) throws IOException {
		ZipInputStream zipFile=null;
		ZipEntry entry;
		
		zipFile = new ZipInputStream(getInputStream(zipName));
		
		String fnameSuffix = fname.substring(fname.lastIndexOf(".")+1);
		String fnameNoSuffix = fname.substring(0,fname.length()-(fnameSuffix.length()+1));

		while((entry = zipFile.getNextEntry())!=null) {
	        if( entry.getName().equals(fname) ) {
		        // read buffered stream into temp file.
		        File f = File.createTempFile(fnameNoSuffix, fnameSuffix);
		        f.setReadable(true);
		        f.setWritable(true);
                f.deleteOnExit();
		        FileOutputStream fos = new FileOutputStream(f);
	    		byte[] buffer = new byte[2048];
	    		int len;
                while ((len = zipFile.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
		        // return temp file as input stream
                return new BufferedInputStream(new FileInputStream(f));
	        }
	    }
		    
	    zipFile.close();

	    throw new IOException("file not found in zip.");
	}

	public static String getUserDirectory() {
		return System.getProperty("user.dir");
	}
	
	public static String getTempDirectory() { 
		return System.getProperty("java.io.tmpdir");
	}
}
