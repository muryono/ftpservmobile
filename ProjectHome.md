## Introduction ##
Using Sun MIDP 2.0 platform, this J2ME based MIDlet will run on your mobile phone and allow you to transfer files to or from the phone using your pc over the wireless LAN.

This is intended for phones which have a wifi connection but no easy way of transferring files using it. I bought a Samsung Jet S8000 and found this to be a major short-coming. I wrote this MIDlet so that I could transfer my music on, and photos off the phone. This can be easily done using drag-and-drop in Windows Explorer and connecting to your phone running this MIDlet.

## Testing ##
This MIDlet is currently a work in progress and so far this has been tested on the  following phone(s):

  * Samsung Jet S8000 : working
  * Sony Ericsson C905 : working (partially)

It may work on other phones which support the MIDP platform too. To be of use the phone should have a wifi connection.

## Important Notes ##
  * No user authentication is performed so running this MIDlet opens up your phone so that anyone on the network can access your files.
  * The MIDlet currently doesn't allow the user to restrict access to certain parts of the phones file tree.
  * The port number is currently restricted to port 21.
  * Only one user is allowed at a time.
  * File copied to the server will be overwritten if they already exist.
  * Renaming of files and folders is allowed.

## How to install and use ##
  1. Download the latest zip archive from the [Download page](http://code.google.com/p/ftpservmobile/downloads/list)
  1. Ensure that the Wifi connection on your phone is switched on and connected to your Wifi network
  1. Copy the Jar file from inside the zip archive to your phone by any means (bluetooth, usb cable etc...)
  1. On the phone it should allow you to 'install' the jar file. This step varies from phone to phone.
  1. After it installs the MIDlet should appear in your list of applications on the phone. Run it from here.
  1. When the MIDlet starts up it should tell you the IP address that it is listening on
  1. Back on your PC start up an FTP client and connect it to the IP address listed on the phone. On Windows this can be done by typing ftp://<ip address> into the address bar of Windows Explorer.
  1. You should now be able to navigate, upload and download files. On Windows do this by dragging and dropping files to or from the window

## Contact ##
If you would like to contact me you can send me an [email](mailto:paulfeaturesATgmail.com) or if you have a bug to report submit it in the [issue tracker](http://code.google.com/p/ftpservmobile/issues/list).


---


While this software is free to use, if you do find it useful and would like to contribute then you can do so here :)

[![](https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=WXHTVVW9RS6M4&lc=IE&item_name=ftpservmobile&currency_code=EUR&bn=PP%2dDonationsBF%3abtn_donateCC_LG%2egif%3aNonHosted)