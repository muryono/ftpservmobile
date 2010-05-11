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

public class FtpCommand {
	// Comment indicates the direction the command is used in:
	//   -> from ConnectionThread to DataConnectionThread
	//   <- from DataConnectionThread to ConnectionThread
	//
	public static final int LIST = 0;      // ->
	public static final int CLOSE = 1;     // ->
	public static final int REPLY = 2;     // <-
	public static final int RETR = 3;      // ->
	public static final int CONTINUE = 4;  // ->
	public static final int STOR = 5;      // ->
	public static final int RESULT = 6;    // <-
}
