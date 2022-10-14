package com.marginallyclever.convenience.log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Properties;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.marginallyclever.convenience.FileAccess;
import com.marginallyclever.robotoverlord.RobotOverlord;

/**
 * static log methods available everywhere
 * @author Dan Royer
 * See org.slf4j.Logger
 */
public class Log {

    private static final boolean LOG_USE_HOME_DIR = Boolean.getBoolean("log.useHomeDir");
	public static String LOG_FILE_PATH = System.getProperty("log.dir", LOG_USE_HOME_DIR ? System.getProperty("user.home") + "/robot-overlord/" : null);

	static {
		// make sure log path exists
		if (LOG_FILE_PATH != null) {
			new File(LOG_FILE_PATH).mkdirs();
		}
	}

	public static String LOG_FILE_NAME_TXT = System.getProperty("log.fileName", "log.txt");
	public static final String PROGRAM_START_STRING = "PROGRAM START";
	public static final String PROGRAM_END_STRING = "PROGRAM END";
	
	private static final Logger logger = LoggerFactory.getLogger(RobotOverlord.class);
	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static ArrayList<LogListener> listeners = new ArrayList<LogListener>();

	public static void addListener(LogListener listener) {
		listeners.add(listener);
	}
	
	public static void removeListener(LogListener listener) {
		listeners.remove(listener);
	}

    public static String getLogLocation() {
        if (LOG_FILE_PATH == null || LOG_FILE_PATH.isEmpty()) {
            return LOG_FILE_NAME_TXT;
        }
        return LOG_FILE_PATH + File.separator + LOG_FILE_NAME_TXT;
    }

	public static void start() {
		if(logger!=null) {
			// already started
			return;
		}
		
		LOG_FILE_PATH = FileAccess.getUserDirectory();
		if(!LOG_FILE_PATH.endsWith(File.separator)) {
			LOG_FILE_PATH+=File.separator;
		}
			
		System.out.println("log dir="+LOG_FILE_PATH);

		boolean hadCrashed = crashReportCheck();
		deleteOldLog();

		write(PROGRAM_START_STRING);
		write("------------------------------------------------");
		Properties p = System.getProperties();
		Set<String> names = p.stringPropertyNames();
		for(String n : names) {
			write(n+" = "+p.get(n));
		}
		write("------------------------------------------------");
		if(hadCrashed) {
			Log.message("Crash detected on previous run");
		}
	}
	
	public static void end() {
		logger.info(PROGRAM_END_STRING);
	}
	
	private static boolean crashReportCheck() {
		File oldLogFile = new File(LOG_FILE_PATH+LOG_FILE_NAME_TXT);
		if( oldLogFile.exists() ) {
			// read last line of file
			String ending = tail(oldLogFile);
			
			if(!ending.contains(PROGRAM_END_STRING)) {
				// add a crashed message
				//sendLog();
				
				return true;
			}
		}
		return false;
	}
	
	/**
	 * wipe the log file
	 */
	public static void deleteOldLog() {
		Path p = FileSystems.getDefault().getPath(getLogLocation());
		try {
			Files.deleteIfExists(p);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		// print starting time
	}
	
	/**
	 * https://stackoverflow.com/a/7322581
	 * @param file
	 * @return the last line in the file
	 */
	public static String tail( File file ) {
	    RandomAccessFile fileHandler = null;
	    try {
	        fileHandler = new RandomAccessFile( file, "r" );
	        long fileLength = fileHandler.length() - 1;
	        StringBuilder sb = new StringBuilder();

	        for(long filePointer = fileLength; filePointer != -1; filePointer--){
	            fileHandler.seek( filePointer );
	            int readByte = fileHandler.readByte();

	            if( readByte == 0xA ) {  // 10, line feed, '\n'
	                if( filePointer == fileLength ) {
	                    continue;
	                }
	                break;

	            } else if( readByte == 0xD ) {  // 13, carriage-return '\r'
	                if( filePointer == fileLength - 1 ) {
	                    continue;
	                }
	                break;
	            }

	            sb.append( ( char ) readByte );
	        }

	        String lastLine = sb.reverse().toString();
	        return lastLine;
	    } catch( java.io.FileNotFoundException e ) {
	        e.printStackTrace();
	        return null;
	    } catch( java.io.IOException e ) {
	        e.printStackTrace();
	        return null;
	    } finally {
	        if (fileHandler != null )
	            try {
	                fileHandler.close();
	            } catch (IOException e) {
	                /* ignore */
	            }
	    }
	}


	/**
	 * Appends a message to the log file
	 * @param msg HTML to put in the log file
	 */
	public static void write(String msg) {
		if(logger==null) start();
		
		final String cleanMsg = sdf.format(Calendar.getInstance().getTime())+" "+msg;
		
		// TODO why have logger if it's not being used?
		//logger.info(msg);
		
		try (Writer fileWriter = new OutputStreamWriter(new FileOutputStream(getLogLocation(), true), StandardCharsets.UTF_8)) {
			PrintWriter logToFile = new PrintWriter(fileWriter);
			logToFile.write(cleanMsg+"\n");
			logToFile.flush();
		} catch (IOException e) {
			logger.error("{}", e);
		}
		
		SwingUtilities.invokeLater(new Runnable() {
            @Override
			public void run() {
        		for( LogListener listener : listeners ) {
        			listener.logEvent(cleanMsg);
        		}
        		//System.out.println(msg);
            }
         });
	}


	public static String secondsToHumanReadable(double totalTime) {
		return millisecondsToHumanReadable((long)(totalTime*1000));
	}
	
	/**
	 * Turns milliseconds into h:m:s
	 * @param millis milliseconds
	 * @return human-readable string
	 */
	public static String millisecondsToHumanReadable(long millis) {
		long s = millis / 1000;
		long m = s / 60;
		long h = m / 60;
		m %= 60;
		s %= 60;

		String elapsed = "";
		if (h > 0) elapsed += h + "h";
		if (h > 0 || m > 0) elapsed += m + "m";
		elapsed += s + "s ";

		return elapsed;
	}
	

	/**
	 * Appends a message to the log file.  Color will be red.
	 * @param message append text as red HTML
	 */
	public static void error(String message) {
		write("ERROR "+message);
	}

	/**
	 * Appends a message to the log file.  Color will be green.
	 * @param message append text as green HTML
	 */
	public static void message(String message) {
		write(message);		
	}
}
