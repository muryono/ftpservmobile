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

/**
 * This class facilitates sending of commands between
 * two threads. An object of this type can hold one 
 * command.
 */
public class ThreadCommunicator {
	private ThreadCommand command = null;
	
	/**
	 * Get the command if one is available, if not,
	 * wait until it is available. Causes caller to
	 * block.
	 * @return
	 */
	public synchronized ThreadCommand getCommand() {		
		while (command == null) {
			try {
				wait();
			} catch (Exception e) {
				Log.logException(e);	
			}			
		}
		ThreadCommand returnVal = command;
		command = null;
		notify();
		Log.put("ThreadCommunicator::getCommand : "+returnVal);
		return returnVal;
	}
	
	/**
	 * Store the specified command if one isn't already stored.
	 * If one is already stored then wait until that existing one 
	 * has been read. Causes caller to block.
	 * @param newCommand
	 */
	public synchronized void putCommand(ThreadCommand newCommand) {
		while (command != null) {
			try {
				wait();
			} catch (Exception e) {
				Log.logException(e);
			}			
		}
		command = newCommand;
		notify();
		Log.put("ThreadCommunicator::putCommand : "+newCommand);
	}	
}
