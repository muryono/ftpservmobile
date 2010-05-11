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
 * This class is used to store commands exchanged between threads.
 */
public class ThreadCommand {
	private int command = -1;
	private String arg1 = null; 
	
	public ThreadCommand(int command) {
		this.command = command;
	}
	public ThreadCommand(int command, String in1) {
		this.command = command;
		arg1 = in1;
	}	
	public int getCommand() {
		return command;
	}
	public String getArg() {
		return arg1;
	}
	public String toString () {
		return command + " " + ((arg1 == null)?("null"):(arg1));
	}
	
}
