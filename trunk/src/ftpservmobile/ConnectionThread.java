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

import javax.microedition.io.*;
import javax.microedition.io.file.FileConnection;
import java.io.*;

/**
 * This class implements the functionality for a thread that handles the FTP
 * commands received from a client. It does not do any retrieving or storing of
 * files.
 */
public class ConnectionThread extends Thread {
	protected StreamConnection client = null;              // The stream connection for the client
	protected final int SOCKET_IN_BUFFER_SIZE = 4096;      // Size of the buffer used when buffering data from the socket
	protected PrintStream out = null;                      // The stream used to write to the socket
	protected InputStream in = null;                       // The stream used to read from the socket
	protected int state;                                   // Records the current state of the state machine, see FtpState class for possible values
	protected DataConnectionThread dataConnection = null;
	protected String ipAddress = null;                     // Records the server's IP address
	protected boolean shutdownInitiated = false;
	protected final String ROOT_URL = "file:///";          // The prefix for URL's in the file system
	protected String cwdUrl = ROOT_URL;                    // Stores the current working directory of the server
	protected String renameFromPath = null;                // Used to remember the renameFrom path while awaiting the renameTo path
	protected int oldState = -1;                           // Stores a fall back state for complicated failure cases
	protected java.util.Timer idleTimer = null;
	protected final int IDLE_TIMEOUT = 300;                 // Idle timeout in seconds before a client is disconnected
	protected int dataPort = 5001;							// Initial port used for the data socket

	/**
	 * Default constructor.
	 * 
	 * @param client The StreamConnection that this thread will communicate with.
	 * @param ipAddress The IP address of the server.
	 */
	public ConnectionThread(StreamConnection client, String ipAddress) {
		super();
		this.client = client;
		if (ipAddress.length() == 0) {
			this.ipAddress = "127,0,0,1";
			Log.put("ERROR: unable to determine current IP address.");
		} else {
			this.ipAddress = ipAddress.replace('.', ',');
		}
	}

	/**
	 * This will send one line down the socket. The newline is
	 * automatically appended. The output is also copied to the log.
	 * 
	 * @param data
	 */
	protected void sendLine(String data) {
		Log.put("Sending [" + data + "]");
		out.print(data + "\r\n");
		out.flush();
	}

	/**
	 * Stops a running idle timer if it exists and starts a new one.
	 */
	protected void resetIdleTimer() {
		// Cancel old timer if running
		if (idleTimer != null) {
			idleTimer.cancel();
		}

		idleTimer = new java.util.Timer();
		idleTimer.schedule(new IdleTimerTask(), IDLE_TIMEOUT * 1000);
	}

	/**
	 * Read one line from the socket. 
	 * 
	 * @return The line that was read from the socket. The newline character(s) are removed automatically.
	 * @throws Exception Thrown if the socket closed prematurely by the client or the input buffer over fills.
	 */
	protected String getLine() throws Exception {
		resetIdleTimer();
		char[] buffer = new char[SOCKET_IN_BUFFER_SIZE];
		int bufferIndex = 0;
		int lastChar = '\0';
		int thisChar;
		String retVal;
		while ((thisChar = in.read()) != -1) {
			// Keep filling buffer until a newline char sequence is reached
			buffer[bufferIndex++] = (char) thisChar;
			if (thisChar == 10 && lastChar == 13) {
				// End of line reached, terminate the string
				buffer[bufferIndex - 2] = '\0';
				buffer[bufferIndex - 1] = '\0';
				
				// Remove the newline char sequence from end of string
				bufferIndex -= 2;

				break;
			}
			lastChar = buffer[bufferIndex - 1];

			if (bufferIndex >= SOCKET_IN_BUFFER_SIZE) {
				// If the line is too long, print the line, then throw exception

				System.out.print("Received " + bufferIndex + " bytes [");
				for (int i = 0; i < bufferIndex; i++) {
					System.out.print((int) buffer[i] + " ");
				}
				System.out.println("]");
				throw new Exception("Input buffer filled after " + bufferIndex
						+ " bytes.");
			}
		}

		if (thisChar == -1) {
			// The socket was prematurely closed
			throw new Exception("Socket closed unexpectedly.");
		}

		retVal = new String(buffer).substring(0, bufferIndex);
		Log.put("Received " + bufferIndex + " bytes [" + retVal + "]"
				+ " in state " + state + ", string length = " + retVal.length());
		return retVal;
	}

	/**
	 * Starts the DataConnectionThread which will communicate with the client
	 * and handle file transfers. 
	 * 
	 * @return The next state for the state machine.
	 */
	protected int openDataSocket() {
		dataConnection = new DataConnectionThread(dataPort);
		dataConnection.start();
		
		// Read the port number for the socket the socket that is listening 
		int port = Integer.parseInt(dataConnection.commandOut.getCommand().getArg());
		
		if (port > 0) {
			dataPort = port + 1;  // next time, use a different port
			Log.put("Data Connection thread started.");
			sendLine("227 Entering Passive Mode (" + ipAddress + ","
					+ (port / 256) + "," + (port % 256) + ")");
			return FtpState.PASV_WAIT_FOR_COMMAND;
		} else {
			sendLine("421 Cannot open data listen port");
			return FtpState.TERMINATE;
		}
	}

	/**
	 * If the DataConnectionThread was started then this will
	 * wait for it to terminate.
	 */
	protected void closeDataSocket() {
		if (dataConnection != null && dataConnection.isAlive()) {
			dataConnection.commandIn.putCommand(new ThreadCommand(
					FtpCommand.CLOSE));
			while (dataConnection.isAlive()) {
				dataConnection.yield();
				Log.put("Waiting for data connection to close.");
			}
			Log.put("Finished waiting for Data Connection Thread to finish.");
			dataConnection = null;
		}
	}

	/**
	 * Determines the absolute path based on the current working directory
	 * and the suffix parameter here. If the suffix begins with a / then it
	 * is assumed to be absolute already. Otherwise it is assumed to be relative.
	 * The returned path is a fully qualified FileConnection URI. Any trailing /'s 
	 * are removed.
	 * 
	 * @param suffix Absolute or relative path (not a FileConnection URI).  
	 * @return FileConnection URI for the suffix. 
	 */
	protected String getAbsolutePath(String suffix) {
		String retVal = null;
		if (suffix == null)
			return retVal;
		else if (suffix.startsWith("/")) {
			// Absolute path, append to file system root
			suffix = Util.stripTrailingSlashs(suffix.substring(1));
			retVal = ROOT_URL + suffix;
		} else if (suffix.length() == 0) {
			retVal = cwdUrl;
		} else {
			// Relative path, append to current working directory

			if (cwdUrl.equals(ROOT_URL)) {
				retVal = cwdUrl + suffix;
			} else {
				retVal = cwdUrl + "/" + suffix;
			}
			
			if (!retVal.equals(ROOT_URL))
				retVal = Util.stripTrailingSlashs(retVal);
		}

		return retVal;
	}

	/**
	 * Validate's the FTP LIST command. If valid then the command is executed 
	 * and the appropriate next state for the state machine is returned.
	 * 
	 * @param currentState The current state of the state machine.
	 * @param fullCommand  The line that was read from the socket.
	 * @return             The next state for the state machine.
	 * @throws IOException Throws if there was a problem opening the FileConnection.
	 */
	protected int validateListCommand(int currentState, String fullCommand)
			throws IOException {
		int nextState = FtpState.PASV_WAIT_FOR_COMMAND;
		boolean validated = false;
		String arg = stripArgument(fullCommand);
		String absPath = getAbsolutePath(arg);
		if (absPath != null) {
			if (absPath.equals(ROOT_URL)) {
				validated = true;
			} else {
				FileConnection fc = (FileConnection) Connector.open(absPath + "/");
				if (fc.exists() && fc.isDirectory())
					validated = true;
			}
		}

		if (validated) {
			// Issue the LIST command to the DataConnectionThread
			dataConnection.commandIn.putCommand(new ThreadCommand(FtpCommand.LIST, absPath));
			// Await result
			sendLine(dataConnection.commandOut.getCommand().getArg());
			// Close DataConnectionThread
			dataConnection.commandIn.putCommand(new ThreadCommand(FtpCommand.CLOSE));
			
			// Tell client that we have completed the command
			sendLine("226 OK");
			nextState = FtpState.IDLE;
		}
		return nextState;
	}

	/**
	 * Validates the FTP CWD (change working directory) command. If 
	 * validated okay then the command is executed.
	 * 
	 * @param currentState The current state of the state machine.
	 * @param fullCommand  The line that was read from the socket.
	 * @return             The next state for the state machine.
	 * @throws IOException
	 */
	protected int validateCwdCommand(int currentState, String fullCommand)
			throws IOException {
		int nextState = currentState;
		boolean validated = false;
		String arg = stripArgument(fullCommand);
		String absPath = getAbsolutePath(arg);
		if (absPath != null) {
			if (absPath.equals(ROOT_URL)) {
				validated = true;
			} else {
				String path = absPath + "/";
				Log.put("Check if directory:" + path + ", exists");
				FileConnection fc = (FileConnection) Connector.open(path);
				if (fc.exists() && fc.isDirectory())
					validated = true;
			}
		}

		if (validated) {
			cwdUrl = absPath;
			sendLine("213 OK");
		} else {
			sendLine("550 DIRECTORY NOT FOUND");
		}
		return nextState;
	}

	/**
	 * Validates the FTP RETR command. If 
	 * validated okay then the command is executed.
	 * 
	 * @param currentState The current state of the state machine.
	 * @param fullCommand  The line that was read from the socket.
	 * @return             The next state for the state machine.
	 * @throws IOException
	 */
	protected int validateRetrCommand(int currentState, String fullCommand)
			throws IOException {
		int nextState = currentState;
		boolean validated = false;
		String arg = stripArgument(fullCommand);
		String absPath = getAbsolutePath(arg);
		if (absPath != null) {
			try {
				FileConnection fc = (FileConnection) Connector.open(absPath);
				if (fc.exists() && !fc.isDirectory())
					validated = true;
			} catch (java.lang.IllegalArgumentException e) {
				Log.put("Can't open file connection to:" + absPath);
			}
		}

		if (validated) {
			Log.putPublic("Retrieving file : " + absPath);
			idleTimer.cancel();
			
			// Order DataConnectionThread to act on the command
			dataConnection.commandIn.putCommand(new ThreadCommand(FtpCommand.RETR, absPath));
			
			// Wait for response
			sendLine(dataConnection.commandOut.getCommand().getArg());
			
			// Order DataConnectionThread to continue
			dataConnection.commandIn.putCommand(new ThreadCommand(FtpCommand.CONTINUE));
			
			// Wait for response
			sendLine(dataConnection.commandOut.getCommand().getArg());
			
			// Cause DataConnectionThread to end
			dataConnection.commandIn.putCommand(new ThreadCommand(FtpCommand.CLOSE));
			resetIdleTimer();
		} else {
			sendLine("553 Incorrect path or not such file");
		}

		closeDataSocket();
		nextState = FtpState.IDLE;

		return nextState;
	}

	/**
	 * Validates the FTP Size command. If 
	 * validated okay then the command is executed.
	 * 
	 * @param currentState The current state of the state machine.
	 * @param fullCommand  The line that was read from the socket.
	 * @return             The next state for the state machine.
	 * @throws IOException
	 */
	protected int validateSizeCommand(int currentState, String fullCommand)
			throws IOException {
		int nextState = currentState;
		boolean validated = false;
		String arg = stripArgument(fullCommand);
		String absPath = getAbsolutePath(arg);
		long fileSize = -1;
		if (absPath != null) {
			try {
				FileConnection fc = (FileConnection) Connector.open(absPath);
				if (fc.exists() && !fc.isDirectory()) {
					validated = true;
					fileSize = fc.fileSize();
				}
			} catch (java.lang.IllegalArgumentException e) {
				Log.put("Can't open file connection to:" + absPath);
			}
		}

		if (validated) {
			sendLine("213 " + fileSize);
		} else {
			sendLine("553 Incorrect path or no such file");
		}

		return nextState;
	}

	/**
	 * Checks if the given path is in the root of the file system.
	 * 
	 * @param path Fully qualified URI
	 * @return
	 */
	protected boolean isPathInRoot(String path) {
		return path.lastIndexOf('/') <= ROOT_URL.lastIndexOf('/');
	}

	/**
	 * Validates the FTP RNFR (rename from) command. If 
	 * validated okay then the command is executed. This command is
	 * part of a two command sequence. First we get RNFR (rename from)
	 * followed by RNTO (rename to). This allows files and dir's to be
	 * renamed.
	 * 
	 * @param currentState The current state of the state machine.
	 * @param fullCommand  The line that was read from the socket.
	 * @return             The next state for the state machine.
	 * @throws IOException
	 */
	protected int validateRnfrCommand(int currentState, String fullCommand)
			throws IOException {
		int nextState = currentState;
		boolean validated = false;
		String arg = stripArgument(fullCommand);
		String absPath = getAbsolutePath(arg);

		if (absPath != null && !isPathInRoot(absPath)) {
			try {
				FileConnection fc = (FileConnection) Connector.open(absPath);

				if (fc.exists()) {
					// Ensure file exists
					validated = true;
					renameFromPath = absPath;
				}
			} catch (java.lang.IllegalArgumentException e) {
				Log.put("Can't open file connection to:" + absPath);
			}
		}

		if (validated) {
			sendLine("350 Command OK");
			// Remember what the old state was, we will fall back to that after 
			// the following RNTO command.
			oldState = currentState; 
			nextState = FtpState.RNFR_OK;
		} else {
			sendLine("450 Cannot find file");
		}

		return nextState;
	}

	/**
	 * Validates the FTP RNTO (rename to) command. If 
	 * validated okay then the command is executed. This command is
	 * part of a two command sequence. First we get RNFR (rename from)
	 * followed by RNTO (rename to). This allows files and dir's to be
	 * renamed.
	 * 
	 * @param currentState The current state of the state machine.
	 * @param fullCommand  The line that was read from the socket.
	 * @return             The next state for the state machine.
	 * @throws IOException
	 */
	protected int validateRntoCommand(int currentState, String fullCommand)
			throws IOException {
		int nextState = currentState;
		boolean validated = false;
		String arg = stripArgument(fullCommand);
		String absPath = getAbsolutePath(arg);

		if (absPath != null && !isPathInRoot(absPath)) {
			try {
				FileConnection fcTo = (FileConnection) Connector.open(absPath);

				if (!fcTo.exists()) {
					// Ensure file doesn't exist
					FileConnection fcFr = (FileConnection) Connector.open(renameFromPath);
					fcFr.rename(absPath.substring(absPath.lastIndexOf('/') + 1));
					validated = true;
				}
			} catch (java.lang.IllegalArgumentException e) {
				Log.put("Can't open file connection to:" + absPath);
			}
		}

		if (validated) {
			sendLine("250 Rename action completed");
			Log.putPublic("Renamed : " + absPath);
		} else {
			sendLine("553 Cannot rename to this target filename");
		}

		nextState = oldState;

		return nextState;
	}

	protected int validateMkdCommand(int currentState, String fullCommand)
			throws IOException {
		int nextState = currentState;
		boolean validated = false;
		String arg = stripArgument(fullCommand);
		String absPath = getAbsolutePath(arg);

		if (absPath != null && !isPathInRoot(absPath)) {
			try {
				FileConnection fc = (FileConnection) Connector.open(absPath);

				if (!fc.exists()) {
					// File file/dir exist, check if we can create it
					fc.mkdir();
					validated = true;
				}
			} catch (java.lang.IllegalArgumentException e) {
				Log.put("Can't open file connection to:" + absPath);
			}
		}

		if (validated) {
			sendLine("257 Directory created");
			Log.putPublic("Created directory : " + absPath);
		} else {
			sendLine("550 Cannot create directory here");
		}

		return nextState;
	}

	protected int validateStorCommand(int currentState, String fullCommand)
			throws IOException {
		int nextState = currentState;
		boolean validated = false;
		String arg = stripArgument(fullCommand);
		String absPath = getAbsolutePath(arg);

		if (absPath != null && !isPathInRoot(absPath)) {
			try {
				FileConnection fc = (FileConnection) Connector.open(absPath);

				if (!fc.exists()) {
					// File doesn't exist, check if we can create it
					fc.create();
					validated = true;
				} else {
					// File/Dir exists
					if (!fc.isDirectory() && fc.canWrite()) {
						// Ensure that it's a writable file
						validated = true;
					}
				}
			} catch (java.lang.IllegalArgumentException e) {
				Log.put("Can't open file connection to:" + absPath);
			}
		}

		if (validated) {
			Log.putPublic("Storing file : " + absPath);
			idleTimer.cancel();
			dataConnection.commandIn.putCommand(new ThreadCommand(FtpCommand.STOR, absPath));
			sendLine(dataConnection.commandOut.getCommand().getArg());
			sendLine(dataConnection.commandOut.getCommand().getArg());
			dataConnection.commandIn.putCommand(new ThreadCommand(FtpCommand.CLOSE));
			resetIdleTimer();
		} else {
			sendLine("553 Cannot store this file");
		}

		closeDataSocket();
		nextState = FtpState.IDLE;

		return nextState;
	}
	
	/**
	 * Validate the type command. This is used to set the type
	 * of the data being sent over the data connection. Only 
	 * (I)mage and (A)scii types are supported here.
	 * 
	 * @param currentState
	 * @param fullCommand
	 * @return
	 * @throws IOException
	 */
	protected int validateTypeCommand(int currentState, String fullCommand)
			throws IOException {
		int nextState = currentState;
		String arg = stripArgument(fullCommand);

		if (arg.equals("I") || 
			arg.equals("i") ||
			arg.equals("A") || 
			arg.equals("a") )
		{
			sendLine("200 OK");
		}
		else
		{
			sendLine("504 This type is not supported");
		}

		return nextState;
	}

	/**
	 * This will return the first argument after the ftp command. For example,
	 * in the command 'stor file.txt', the string 'file.txt' is returned. If
	 * there is no first argument then an empty string is returned ("").
	 * 
	 * @param input
	 * @return
	 */
	protected String stripArgument(String input) {
		// Strip leading or trailing whitespace
		input = input.trim();

		int startIndex = -1;
		int endIndex = input.length();
		for (int index = 0; index < input.length(); index++) {
			if (startIndex == -1) {
				if (input.charAt(index) == ' ') {
					startIndex = index;
					break;
				}
			}
		}

		String retVal = null;

		if (startIndex == -1)
			retVal = "";
		else
			retVal = input.substring(startIndex + 1, endIndex);

		Log.put("stripArgument returns [" + retVal + "]");

		return retVal;
	}

	/**
	 * This method executes a state machine transition. The current
	 * state is passed in and the new state is returned.
	 * 
	 * @param state       The current state
	 * @return            The next state
	 * @throws Exception
	 */
	protected int processState(int state) throws Exception {
		// Read a line of input from the socket, this will dictate
		// what state we should go to next.
		String input = getLine();
		
		int nextState = FtpState.INVALID;

		// These commands can be processed in any state
		if (input.startsWith("PWD")) {
			sendLine("257 \"" + cwdUrl.substring(ROOT_URL.length()-1) + "\"");
			nextState = state;
		} else if (input.startsWith("TYPE")) {
			nextState = validateTypeCommand(state, input);
		} else if (input.startsWith("CWD")) {
			nextState = validateCwdCommand(state, input);
		} else if (input.startsWith("MKD")) {
			nextState = validateMkdCommand(state, input);
		} else if (input.startsWith("RNFR")) {
			nextState = validateRnfrCommand(state, input);
		} else if (input.startsWith("noop")) {
			sendLine("200 OK");
			nextState = state;
		} else if (input.startsWith("QUIT")) {
			nextState = FtpState.TERMINATE;
		} else if (input.startsWith("USER")) {
			sendLine("230 OK");
			nextState = state;
		} else if (input.startsWith("PASV")) {
			nextState = openDataSocket();
		} else if (input.startsWith("SIZE")) {
		    nextState = validateSizeCommand(state, input);	
		}

		// Now handle state specific commands if the generic 
		// handling above didn't do the job...
		if (nextState == FtpState.INVALID) {
			switch (state) {
			case FtpState.IDLE:
				break;
				
			case FtpState.PASV_WAIT_FOR_COMMAND:
				// A Data connection must be already open for these to work
				
				if (input.startsWith("RETR")) {
					nextState = validateRetrCommand(state, input);
				} else if (input.startsWith("LIST")) {
					nextState = validateListCommand(state, input);
				} else if (input.startsWith("STOR")) {
					nextState = validateStorCommand(state, input);
				} else {
					sendLine("500 Unrecognised command");
					nextState = state;
				}
				break;

			case FtpState.RNFR_OK:
				if (input.startsWith("RNTO")) {
					nextState = validateRntoCommand(state, input);
				}
				break;

			default:
				closeDataSocket();
				nextState = FtpState.IDLE;
				throw new Exception("No state exists for state : " + state);
			}
		}
		if (nextState == FtpState.INVALID) {
			// Handle unrecognised commands
			sendLine("502 Command not supported");
			nextState = state;			
		}
		Log.put("State transition from " + state + " to " + nextState);
		return nextState;
	}

	/**
	 * This method executes a shutdown of this thread and
	 * all its resources.
	 */
	public synchronized void shutdown() {
		Log.put("ConnectionThread::shutdown()");
		idleTimer.cancel();
		shutdownInitiated = true;
		state = FtpState.TERMINATE;
		
		// Wait for DataConnectionThread to close if necessary
		closeDataSocket();
		try {
			// Close comm's streams, and socket
			if (out != null)
				out.close();
			if (in != null)
				in.close();
			if (client != null)
				client.close();
		} catch (Throwable ioe) {
			Log.logException(ioe);
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Thread#run()
	 * This is the main loop of the thread.
	 */
	public void run() {
		Log.put("ConnectionThread::run()");
		Log.putPublic("A user has connected");
		try {
			in = client.openInputStream();
			out = new PrintStream(client.openOutputStream());
			
			// Set the initial state for the state machine
			state = FtpState.IDLE;
			
			// Send the ready command to the client
			sendLine("220 Welcome to the MIDP Ftp Server");
			
			// Start processing the state machine
			while (state != FtpState.TERMINATE) {
				state = processState(state);
			}
		} catch (java.io.InterruptedIOException iioe) {
			if (!shutdownInitiated) {
				Log.logException(iioe);
			}
		} catch (Throwable ioe) {
			Log.logException(ioe);
		} finally {
			shutdown();
		}
		Log.putPublic("A user has disconnected");
		Log.put("ConnectionThread::run() finished.");
	}

	/**
	 * This inner thread is used to implement an idle supervision
	 * timer on the ConnectionThread. If the timeout expires then
	 * a shutdown of the thread is initiated.
	 */
	class IdleTimerTask extends java.util.TimerTask {
		public synchronized void run() {
			Log.put("Idle timer expired");
			shutdown();
		}
	}
}
