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

import java.io.*;
import java.util.Date;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import javax.microedition.io.file.FileConnection;

/**
 * This class implements a thread which is used to
 * communicate over the ftp data socket. Files are
 * sent and received over this socket.
 */
public class DataConnectionThread extends Thread {
	private StreamConnection connection = null;   // this is the connection that we open on the socket
	public ThreadCommunicator commandIn = null;   // used for IPC: this thread reads from this ThreadCommunicator
	public ThreadCommunicator commandOut = null;  // used for IPC: this thread writes to this ThreadCommunicator 
	private int port;                             // the socket port number that this thread will listen on, the actual
												  // socket number maybe above this value.
	private PrintStream socketOPrintStream = null;
	private InputStream socketIStream = null;	
	private OutputStream socketOStream = null;
	
	private void log(String str) {
		Log.put(str);
	}
	
	/**
	 * Constructor.
	 * @param port First port which we will attempt to start listening on
	 */
	public DataConnectionThread(int port) {
		super();
		this.port = port;
		commandIn = new ThreadCommunicator();
		commandOut = new ThreadCommunicator();
	}
	
	
	/**
	 * This will return a string representing the file info for a given file.
	 * This contains information such as file size, filename etc. 
	 * @param path Fully qualified path to a file eg. file:///root1/file1.txt
	 * @return Formatted string representing the file info.
	 * @throws IOException
	 */
	public String getFileInfo(String path) throws IOException
	{
	    FileConnection fc = (FileConnection) Connector.open(path);

	    // Ensure the file exists
	    if (!fc.exists()) {
	        fc.close();
	        return null;
	    }
	   
	    String permissions = "";
	    long fileSize;
	    if (fc.isDirectory()) 
	    {
	    	permissions += "d";
	    	fileSize = 0;
	    }
	    else 
	    {
	    	permissions += "-";
	    	fileSize = fc.fileSize();
	    }
	   
	    if (fc.canRead()) permissions += "r";
	    else permissions += "-";

	    if (fc.canWrite()) permissions += "w";
	    else permissions += "-";   
	   
	    permissions += "------- ";
	  
	    fc.close();
	    return formatListDetails(permissions, Long.toString(fileSize), Util.stripTrailingSlashs(fc.getName()));
	}	
	
	/**
	 * This formats a string for the file info from the arguments passed in.
	 * @param permissions Permissions associated with the file.
	 * @param size        File size
	 * @param name        File name
	 * @return
	 */
	protected String formatListDetails(String permissions,
			String size,
			String name)
	{
		return permissions + "    2 0          5                 " + size + " Apr 14  2001 " + name;
	}
	
	
	/**
	 * Handle the List ftp command. This will send info on the files in 
	 * the specified directory over the data socket.
	 * @param path    Fully qualified directory path.
	 * @return        Currently not used.
	 * @throws IOException
	 */
	public boolean processListCommand(String path) throws IOException
	{
		Log.put("Enter processListCommand");
		if (path.equals("file:///"))
		{
			// There is special handling for the path representing the
			// root of the file system. We should list all the file system roots.
			Log.put("Process list command for filesystem root");
			java.util.Enumeration enum = javax.microedition.io.file.FileSystemRegistry.listRoots();
			for (; enum.hasMoreElements();) {
				String thisChild = (String) enum.nextElement();
				thisChild = thisChild.substring(0,
						(thisChild.charAt(thisChild.length()-1)=='/')? (thisChild.length()-1) : (thisChild.length())
								);
				String details = formatListDetails("drw-------", "0", thisChild);
				
				Log.put(details);
				socketOPrintStream.print(details + "\r\n");
				socketOPrintStream.flush();
			}			
		}
		else {
			// Produce a directory listing for the specified path.
			Log.put("Process list command for absolute path");
			path += "/";
		    FileConnection fc = (FileConnection) Connector.open(path);

		    if (!fc.exists()) {
		        fc.close();
		        return false;
		    }
		   
		    for (java.util.Enumeration contents = fc.list() ; contents.hasMoreElements() ;) {
		        String thisItem = path + contents.nextElement(); //"file://" + fc.getPath() + contents.nextElement();
		        String details = getFileInfo(thisItem);
		        Log.put(details);
		        // Send directory listing over the socket
		        socketOPrintStream.print(details + "\r\n");
		        socketOPrintStream.flush();
		    }

		    fc.close();		      
		}
		commandOut.putCommand(new ThreadCommand(FtpCommand.REPLY, "150 OK"));
		return true;
	}	

	
	/**
	 * Handle the retrieve ftp command for the specified path. This
	 * will send the file over the data socket.
	 * @param path  Fully qualified path to the file to be retrieved.
	 * @return      Currently not used.
	 * @throws IOException
	 */
	public boolean processRetrCommand(String path) throws IOException
	{
		Log.put("Enter processRetrCommand");

		// Check that the file exists
		FileConnection fc = (FileConnection) Connector.open(path);
		if (!fc.exists() || fc.isDirectory()) {
			fc.close();
			return false;
		}
		
		// Tell the client that we are about to start sending data
		commandOut.putCommand(new ThreadCommand(FtpCommand.REPLY, "150 OK"));
		
		// Wait until until that last command is sent to the client, then continue
		if (commandIn.getCommand().getCommand() == FtpCommand.CONTINUE) {
			InputStream inputFileStream = fc.openInputStream();		
			int bytesInBuffer;
			int bytesRead = 0;
			long fileSize = fc.fileSize();
			
			Log.put("Starting transfer of file, size = " + fileSize);
			
			// I had the buffer size at 1024, but it wouldn't work 
			// for loopback connections for some reason. I got error 10053.
			// A value of 64 seems to work for all connections.
			final int transferBufferSize = 64;
			
			Date date = new java.util.Date();
			long startTime = date.getTime();
			
			int percentLastPrinted = 0;
			byte[] transferBuffer = new byte[transferBufferSize];
			while ((bytesInBuffer = inputFileStream.read(transferBuffer)) != -1)
			{
				bytesRead += bytesInBuffer;
				int percent = (int) (((double)bytesRead / (double)fileSize) * 100.0);
				if (percent % 10 == 0 && percentLastPrinted != percent)
				{
					percentLastPrinted = percent;
					Log.put(percent + "% complete (" + bytesRead + " bytes)");
				}
				
				socketOStream.write(transferBuffer, 0, bytesInBuffer);	
				socketOStream.flush();
			}	
			// Print some useful info to log so we can monitor transfer speeds
			date = new java.util.Date();
			long endTime = date.getTime();
			double durationSec = (endTime - startTime) / 1000.0;
			Log.put("Sent file: ["+path+"] Time taken: "+ durationSec + 
					" seconds, average speed: " + (((double)fileSize) / (durationSec * 1024.0) ) + " KB/sec");	
			commandOut.putCommand(new ThreadCommand(FtpCommand.REPLY, "226 OK"));
		}
		fc.close();	      

		return true;
	}		

	
	/**
	 * Process the ftp store command for the specified path.
	 * @param path Fully qualified path to the file to be stored on the server.
	 * @return     Currently not used.
	 * @throws IOException
	 */
	public boolean processStorCommand(String path) throws IOException
	{
		Log.put("Enter processStorCommand");

		// Check that the file exists
		FileConnection fc = (FileConnection) Connector.open(path);
		
		// Create the file if it doesn't exists, if it
		// does exist then clear it.
		if (fc.exists() && !fc.isDirectory()) {
			fc.truncate(0);
		} else {
			fc.create();
		}
		
		// Ensure the file now exists
		if (!fc.exists() || fc.isDirectory()) {
			fc.close();
			return false;
		}
				
		OutputStream outputFileStream = fc.openOutputStream();
		
		// Tell the client that we are ready to receive data from socket
		commandOut.putCommand(new ThreadCommand(FtpCommand.REPLY, "125 Ready to receive"));
		
		int bytesReceived = 0;
		Log.put("About to receive file: " + path);
		
		int transferBufferSize = 64;
		byte [] transferBuffer = new byte[transferBufferSize];
		int bytesInBuffer;
		
		Date date = new java.util.Date();
		long startTime = date.getTime();
		while ((bytesInBuffer = socketIStream.read(transferBuffer)) != -1) {
			bytesReceived += bytesInBuffer;
			if (bytesReceived % (1024*1024) == 0) {
				Log.put("Received " + bytesReceived + " bytes.");
			}
			outputFileStream.write(transferBuffer, 0, bytesInBuffer);
		}
		outputFileStream.flush();
		outputFileStream.close();
		fc.close();
		
		// Print some useful info to log
		date = new java.util.Date();
		long endTime = date.getTime();
		double durationSec = (endTime - startTime) / 1000.0;
		Log.put("File transfer complete ["+path+"] Time taken: "+ durationSec + 
				" seconds, average speed: " + (((double)bytesReceived) / (durationSec * 1024.0) ) + " KB/sec");
		
		commandOut.putCommand(new ThreadCommand(FtpCommand.REPLY, "226 File received"));
		
		return true;
	}		
	
	/**
	 * This method is used to cleanly shutdown this thread and
	 * exit it gracefully.
	 * @param connection The connection being shutdown.
	 */
	private void shutdown(StreamConnection connection) {		
		try {
			// Close all related streams...
			
			if (socketOPrintStream != null) socketOPrintStream.close();
			if (socketOStream != null) socketOStream.close();
			
			if (socketIStream != null) socketIStream.close();
			
			if (connection != null) connection.close();
			log("Data connection closed.");
		}
		catch (Throwable ioe) {
			Log.logException(ioe);
		}		
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	public void run() 
	{
		log("DataConnectionThread::run()");
		try 
		{
			// Create the server listening socket 
			StreamConnectionNotifier scn = null;
			int openAttempts = 10;
			while (openAttempts-- > 0)
			{
				try {
					String portStr = "socket://:"+String.valueOf(port);
					log("Opening socket: "+portStr);
					scn = (StreamConnectionNotifier)Connector.open(portStr);
					commandOut.putCommand(new ThreadCommand(FtpCommand.RESULT, String.valueOf(port) ));					
					break;
				}
				catch (java.io.IOException ioe) {
					Log.put("IOException when opening socket. Try a different port.");					
				}
				port++;
			}
			
			if (openAttempts < 0) {
				// We failed to find a free port
				commandOut.putCommand(new ThreadCommand(FtpCommand.RESULT, "-1" ));				
				throw new Exception("Unable to open listen data port.");
			}
			
			log("Data connection is listening.");

			
			connection = (StreamConnection) scn.acceptAndOpen();
			log("Connection accepted on data socket.");
			// Close the notifier, this means that any further connections 
			// to this port won't be notified to this app. Streams already
			// derived from the connection remain open.
			scn.close();
			
			// Open input and output streams for the socket...
			socketIStream = connection.openInputStream();
			socketOStream = connection.openOutputStream();
			socketOPrintStream = new PrintStream(socketOStream);					
			
			boolean killThread = false;
			while (!killThread) {
				// Wait for orders...
				ThreadCommand thisCommand = commandIn.getCommand();
				switch (thisCommand.getCommand()) {
				case FtpCommand.LIST:
					log("LIST command received.");
					processListCommand(thisCommand.getArg());					
					break;
				case FtpCommand.CLOSE:
					log("CLOSE command received.");	
					killThread = true;
					break;
				case FtpCommand.RETR:
					log("RETR command received.");
					processRetrCommand(thisCommand.getArg());					
					break;	
				case FtpCommand.STOR:
					log("STOR command received.");
					processStorCommand(thisCommand.getArg());					
					break;
				default:
						log("Unknown command received.");
						break;
				}		
			}
		}
		catch (Throwable ioe) {
			Log.logException(ioe);
		}
		finally {
			shutdown(connection);
		}
		log("Data connection thread finished.");
	}
}
