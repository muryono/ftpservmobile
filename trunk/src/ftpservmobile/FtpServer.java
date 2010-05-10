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

import java.util.*;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;

import java.io.*;

/**
 * This class is the top level class for the FtpServer. 
 */
public class FtpServer extends MIDlet implements CommandListener {

	private Command exitCommand;
	private TextBox tb;
	private StreamConnection streamConnection = null;
	private ConnectionThread connection = null;

	/**
	 * Default constructor, this creates some GUI elements and creates the
	 * Log object which will handle the logging for the system from here on.
	 */
	public FtpServer() {
		exitCommand = new Command("Exit", Command.EXIT, 1);
		tb = new TextBox("Simple FTP Server", "FTP Server started\n", 100, 0);
		new Log(tb, true);
		
		// Change whether or not we are running in debug mode here
		Log.setDebugModeStatus(true);

		// Get current time and date and write it to log
		Date d = new java.util.Date();
		Calendar c = Calendar.getInstance();
		c.setTime(d);
		int month = c.get(Calendar.MONTH) + 1;
		String time = c.get(Calendar.DAY_OF_MONTH) + "/" + month + "/"
				+ c.get(Calendar.YEAR) + " " + c.get(Calendar.HOUR_OF_DAY)
				+ ":" + c.get(Calendar.MINUTE) + ":" + c.get(Calendar.SECOND);
		Log.put("Log started " + time);
	}

	/* (non-Javadoc)
	 * @see javax.microedition.midlet.MIDlet#destroyApp(boolean)
	 */
	protected void destroyApp(boolean unconditional)
			throws MIDletStateChangeException {
		Log.put("Destroying app...");
		shutdown();
	}

	/**
	 * Initiates a shutdown of this MIDlet.
	 */
	protected void shutdown() {
		Log.put("Start shutdown()");
		try {
			if (connection != null) {
				Log.put("Force connection thread to die.");
				connection.shutdown();
				while (connection.isAlive()) {
					Log.put("Still waiting on connection thread to die.");
					connection.yield();
				}
				Log.put("Connection thread is dead.");
			}
		} catch (Exception e) {
			Log.logException(e);
		}
		Log.put("Notifying app destroyed...");
		notifyDestroyed();
	}

	/* (non-Javadoc)
	 * @see javax.microedition.midlet.MIDlet#pauseApp()
	 */
	protected void pauseApp() {
		Log.put("Application paused by gui");
	}

	/* (non-Javadoc)
	 * @see javax.microedition.midlet.MIDlet#startApp()
	 */
	protected void startApp() throws MIDletStateChangeException {
		// Deal with some GUI stuff here
		tb.addCommand(exitCommand);
		tb.setCommandListener(this);
		Display.getDisplay(this).setCurrent(tb);
		
		try {
			// Create the server listening socket for port 21
			final int LISTEN_PORT = 21;
			ServerSocketConnection scn = (ServerSocketConnection) Connector.open("socket://:" + LISTEN_PORT);
			Log.putPublic("IP address is " + scn.getLocalAddress() + ":" + LISTEN_PORT);
			Log.put("Stream connection is open.");

			// Main loop of MIDlet here:
			while (true) {
				// Wait for a client to connect:
				StreamConnection sc = (StreamConnection) scn.acceptAndOpen();
				
				// We currently only allow one client
				if (connection == null || connection.isAlive() == false) {
					Log.put("Connection accepted.");
					streamConnection = sc;
					connection = new ConnectionThread(streamConnection, scn.getLocalAddress());
					connection.start();
					Log.put("Connection thread started.");
				} else {
					Log.put("Connection refused, only one client allowed.");
					sc.close();
				}
			}
		} catch (IOException e) {
			Log.logException(e);
		}
	}

	/* (non-Javadoc)
	 * @see javax.microedition.lcdui.CommandListener#commandAction(javax.microedition.lcdui.Command, javax.microedition.lcdui.Displayable)
	 * 
	 * Handle commands from the GUI here.
	 * 
	 */
	public void commandAction(Command cmd, Displayable disp) {
		Log.put("Command received from gui:" + cmd.getLabel());
		if (cmd == exitCommand) {
			try {
				destroyApp(false);
			} catch (MIDletStateChangeException e) {
				Log.logException(e);
			}
		}
	}
}
