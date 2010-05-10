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
 * This class contains some generic static utility methods.
 */
public class Util {
	/**
	 * Strip any trailing /'s from the specified string and return
	 * a new string.
	 * @param input
	 * @return       New string identical to input but with trailing slashes removed
	 */
	public static String stripTrailingSlashs(String input) {
		if (input.length() > 0 && input.charAt(input.length()-1)=='/') {
			input = input.substring(0,input.length()-1 );
			input = stripTrailingSlashs(input);
		}
		return input;
	}
}
