/*
 * This file is part of TinySip. 
 * http://code.google.com/p/de-tiny-sip/
 * 
 * Created 2011 by Sebastian Rosch <flowfire@sebastianroesch.de>
 * 
 * This software is licensed under the Apache License 2.0.
 */

package de.tinysip.stun;

/**
 * Contains information about the STUN discovery test to execute.
 * 
 * @author Sebastian
 *
 */
public class StunInfo
{
	private String stunAddress;
	private int stunPort;
	private int localPort;
	private int type;

	/**
	 * Create a new STUNInfo to contain information for the STUN discovery test. STUN tests a local port and its visibility from the internet, providing information about a NAT and NAT traversal.
	 * @param type the type of the port to test. Use STUNInfo constants
	 * @param stunAddress the address of the STUN server to contact
	 * @param stunPort the port of the STUN server to contact
	 */
	public StunInfo(int type, String stunAddress, int stunPort)
	{
		this.type = type;
		this.stunAddress = stunAddress;
		this.stunPort = stunPort;
	}

	/**
	 * Create a new STUNInfo to contain information for the STUN discovery test. STUN tests a local port and its visibility from the internet, providing information about a NAT and NAT traversal.
	 * @param type the type of the port to test. Use STUNInfo constants
	 * @param stunAddress the address of the STUN server to contact
	 * @param stunPort the port of the STUN server to contact
	 * @param localPort the local port to test
	 */
	public StunInfo(int type, String stunDomain, int stunPort, int localPort)
	{
		this(type, stunDomain, stunPort);
		this.localPort = localPort;
	}

	/**
	 * Set the address of the STUN server
	 * @param stunAddress the address of the STUN server to contact
	 */
	public void setStunAddress(String stunAddress)
	{
		this.stunAddress = stunAddress;
	}

	/**
	 * Set the port of the STUN server
	 * @param stunPort the port of the STUN server to contact
	 */
	public void setStunPort(int stunPort)
	{
		this.stunPort = stunPort;
	}
	
	/**
	 * Set the local port for to test
	 * @param localPort the local port to test
	 */
	public void setLocalPort(int localPort)
	{
		this.localPort = localPort;
	}

	/**
	 * @return the address of the STUN server to contact
	 */
	public String getStunAddress()
	{
		return stunAddress;
	}

	/**
	 * @return the port of the STUN server to contact
	 */
	public int getStunPort()
	{
		return stunPort;
	}
	
	/**
	 * @return the local port to test
	 */
	public int getLocalPort()
	{
		return localPort;
	}
	
	/**
	 * @return the type of the port to test. Use STUNInfo constants
	 */
	public int getType()
	{
		return type;
	}

	/**
	 * Use this constant if the port to test is a SIP port.
	 */
	public static final int TYPE_SIP = 0;
	/**
	 * Use this constant if the port to test is a RTP port.
	 */
	public static final int TYPE_RTP = 1;
	/**
	 * Use this constant if the port to test is a RTCP port.
	 */
	public static final int TYPE_RTCP = 2;
	/**
	 * Use this constant if the port to test is a video port.
	 */
	public static final int TYPE_VIDEO = 3;
}
