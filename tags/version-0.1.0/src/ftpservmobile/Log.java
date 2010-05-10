//
//    Copyright 2010 Paul White
//
//    This file is part of FtpServerMobile.
//
//    FtpServerMobile is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.

//    FtpServerMobile is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with FtpServerMobile.  If not, see <http://www.gnu.org/licenses/>.
//

package ftpservmobile;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import javax.microedition.io.file.FileConnection;
import java.io.*;

/**
 * This class is used to handle logging in a uniform way across
 * all classes associated with the FTP Server.  This class acts
 * as a singleton. 
 */
public class Log {
	private static Log log = null;            // used to refer to the singleton object
	private TextBox tb = null;                // refers to the text box in the gui
	private static final int TB_MAX = 300;    // max number of chars allowed in the text box in the gui
	private PrintStream filePrintStream = null;
	private OutputStream fileOutputStream = null;
	private FileConnection fileCon = null;
	private boolean debugMode = true;         // controls what level of logging we perform
	
	/**
	 * Constructor. This creates the singleton instance, it opens the
	 * streams to the output log file also.
	 * @param textBox         The text box in the gui that will be updated with log entries
	 * @param debugMode
	 */
	public Log(TextBox textBox, boolean debugMode) {
		if (log == null) {
			this.tb = textBox;
			textBox.setMaxSize(TB_MAX);
			log = this;            // set the singleton reference
			
			try {
				// Place the log file on the last of the file system roots.
				java.util.Enumeration enum = javax.microedition.io.file.FileSystemRegistry.listRoots();
				String lastRoot = "";
				for (;enum.hasMoreElements();) {
					lastRoot = (String) enum.nextElement();			
				}
				
				// Open the file connection for the log file
				String dumpLogName = "ftpLog.txt";
				String dumpLogPath = "file:///"+lastRoot+dumpLogName;
				fileCon = (FileConnection) Connector.open(dumpLogPath);
				if (!fileCon.exists())
				{
					put("Created file "+dumpLogPath);					
					fileCon.create();
				}
				else
				{
					fileCon.truncate(0);
				}
				
				putPublic("Logfile: "+dumpLogPath);

				// open the output streams for the log file
				fileOutputStream = fileCon.openOutputStream();
				filePrintStream = new PrintStream(fileOutputStream);

			} catch (Exception e) {
				Log.logException(e);
			}			
		}
	}	
	
	private static Log instance() {
		return log;
	}

	/**
	 * Updates the gui text box with the specified string.
	 * @param preparedString
	 */
	private void printToTextBox(String preparedString) {
		try {		
			// Print to text box in gui
			TextBox tb = instance().tb;			
			
			// Delete old text from the text box
			if (tb.size() + preparedString.length() > TB_MAX) {
				tb.delete(0, (preparedString.length()<=tb.size()) ? preparedString.length() : tb.size() );				
			}
			
			// Only insert text if it's not too long
			if (preparedString.length() < TB_MAX)
			{
				tb.insert(preparedString, tb.size());
			}
			else
			{
				System.out.print("ERROR: can't write to dumpLog. String too long. Length = " + preparedString.length() + " :"+preparedString);
			}
		} catch (Exception e) {
			Log.logException(e);
		}		
	}
	
	/**
	 * Prints the specified string to the log file.
	 * @param preparedString String to be printed.
	 */
	private void printToFile(String preparedString)
	{
		try {
			// Print to file
			if (instance().filePrintStream != null) {			
				instance().filePrintStream.print(preparedString);
			}
			else 
			{
				System.out.print("ERROR: can't write to dumpLog");
			}		
		} catch (Exception e) {
			Log.logException(e);
		}
	}
	
	/**
	 * This method decide's where a log entry should be printed based 
	 * on the type of trace and if debug mode is enabled.
	 * @param str        String to be printed.
	 * @param userTrace  Indicates if this trace should be shown to the user.
	 */
	private synchronized void print(String str, boolean userTrace)
	{
		// Prepare print string
		str = "["+str+"]\r\n";
		
		printToFile(str);
		if (debugMode || userTrace) {
			// When in debug mode print all info to the 
			// text box and stdout for diagnostic purposes; also, if
			// it's a 'public' trace print it there also so the 
			// user can see useful information printed.
			
			printToTextBox(str);
			
			// Print to stdout, useful in simulator only
			System.out.print(str);
		}
	}
	
	/**
	 * Prints the specified string to the text box and log
	 * @param str
	 */
	public synchronized static void putPublic(String str)
	{
		instance().print(str, true);
	}
	
	/**
	 * Internal trace not to be shown to user unless debug mode 
	 * is enabled.
	 * @param str
	 */
	public synchronized static void put(String str) {
		instance().print(str, false);
	}
	
	/**
	 * Exceptions are traced uniformly using this method.
	 * @param ex
	 */
	public synchronized static void logException(Throwable ex)
	{
		ex.printStackTrace();
		put("Exception : " + ex.toString());
		putPublic("An exception has occurred.");
	}
	
	/**
	 * Enable or disable debug mode.
	 * @param debugMode
	 */
	public synchronized static void setDebugModeStatus(boolean debugMode) {
		instance().debugMode = debugMode;
	}
}
