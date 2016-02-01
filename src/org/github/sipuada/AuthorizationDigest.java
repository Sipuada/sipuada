package org.github.sipuada;

/*
 * This file is part of TinySip. 
 * http://code.google.com/p/de-tiny-sip/
 * 
 * Created 2011 by Sebastian Rosch <flowfire@sebastianroesch.de>
 * 
 * This software is licensed under the Apache License 2.0.
 */

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Used to calculate the message digest for user authorization. Use getDigest() depending on the values specified by the provider.
 * 
 * @author Sebastian
 * 
 */
public class AuthorizationDigest {
	/**
	 * Calculate a Digest string using the specified values. Use this if qop field does not exist or is not "auth". For further information, see
	 * http://www.straub.as/java/servlet/digest.html
	 * 
	 * @param user
	 *            the local SIP user to authorize
	 * @param realm
	 *            the realm sent in the authorization challenge
	 * @param password
	 *            the local SIP user's password
	 * @param method
	 *            the SIP method, INVITE or REGISTER
	 * @param uri
	 *            the URI of the local SIP user's SIP domain
	 * @param nonce
	 *            the nonce sent in the authorization challenge
	 * @return the calculated Digest String, or nonce, if an exception occurred
	 */
	public static String getDigest(String user, String realm, String password, String method, String uri, String nonce) {
		String digest1 = user + ":" + realm + ":" + password;
		String digest2 = method + ":" + uri;

		try {
			MessageDigest digestOne = MessageDigest.getInstance("md5");
			digestOne.update(digest1.getBytes());
			String hexDigestOne = getHexString(digestOne.digest());

			MessageDigest digestTwo = MessageDigest.getInstance("md5");
			digestTwo.update(digest2.getBytes());
			String hexDigestTwo = getHexString(digestTwo.digest());

			String digest3 = hexDigestOne + ":" + nonce + ":" + hexDigestTwo;

			MessageDigest digestThree = MessageDigest.getInstance("md5");
			digestThree.update(digest3.getBytes());
			String hexDigestThree = getHexString(digestThree.digest());

			return hexDigestThree;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 
	 * Calculate a Digest string using the specified values. Use this if qop field is "auth". For further information, see http://www.straub.as/java/servlet/digest.html
	 * 
	 * @param user
	 *            the local SIP user to authorize
	 * @param realm
	 *            the realm sent in the authorization challenge
	 * @param password
	 *            the local SIP user's password
	 * @param method
	 *            the SIP method, INVITE or REGISTER
	 * @param uri
	 *            the URI of the local SIP user's SIP domain
	 * @param nonce
	 *            the nonce sent in the authorization challenge
	 * @param nc
	 *            the nc sent in the authorization challenge
	 * @param cnonce
	 *            the cnonce sent in the authorization challenge
	 * @param qop
	 *            the qop sent in the authorization challenge
	 * @return the calculated Digest String, or nonce, if an exception occurred
	 */
	public static String getDigest(String user, String realm, String password, String method, String uri, String nonce, String nc, String cnonce, String qop) {
		String digest1 = user + ":" + realm + ":" + password;
		String digest2 = method + ":" + uri;

		try {
			MessageDigest digestOne = MessageDigest.getInstance("md5");
			digestOne.update(digest1.getBytes());
			String hexDigestOne = getHexString(digestOne.digest());

			MessageDigest digestTwo = MessageDigest.getInstance("md5");
			digestTwo.update(digest2.getBytes());
			String hexDigestTwo = getHexString(digestTwo.digest());

			String digest3 = hexDigestOne + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + hexDigestTwo;

			MessageDigest digestThree = MessageDigest.getInstance("md5");
			digestThree.update(digest3.getBytes());
			String hexDigestThree = getHexString(digestThree.digest());

			return hexDigestThree;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Converts a byte[] into a Hex string
	 * 
	 * @param b
	 *            the byte[] to convert into a Hex string
	 * @return the converted Hex string
	 */
	public static String getHexString(byte[] b) {
		String result = "";
		for (int i = 0; i < b.length; i++) {
			result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		}
		return result;
	}

}
