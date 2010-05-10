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
 * Used to represent the state of the state machine used by 
 * ConnectionThread.
 */
public class FtpState {
	public static final int INVALID = -1;
	public static final int IDLE = 0;
	public static final int PASV_WAIT_FOR_COMMAND = 1;
	public static final int TERMINATE = 2;
	public static final int RNFR_OK = 3; //rename from
}
