package de.tinysip.stun;

import java.util.EventObject;

import de.javawi.jstun.test.DiscoveryInfo;

public class StunDiscoveryResultEvent extends EventObject {

	private static final long serialVersionUID = -6650144679437807701L;
	private DiscoveryInfo discoveryInfo;
	private StunInfo stunInfo;

	/**
	 * Create a new STUNDiscoveryResult event to signal new information about the NAT.
	 * 
	 * @param source
	 *            the sender of the event (the STUNDiscoveryTask)
	 * @param discoveryInfo
	 *            the DiscoveryInfo containing the information about the NAT
	 * @param stunInfo
	 *            the STUNInfo containing the information about the test
	 */
	public StunDiscoveryResultEvent(Object source, DiscoveryInfo discoveryInfo, StunInfo stunInfo) {
		super(source);
		this.discoveryInfo = discoveryInfo;
		this.stunInfo = stunInfo;
	}

	/**
	 * @return the DiscoveryInfo containing the information about the NAT
	 */
	public DiscoveryInfo getDiscoveryInfo() {
		return discoveryInfo;
	}

	/**
	 * @return the STUNInfo containing the information about the test
	 */
	public StunInfo getStunInfo() {
		return stunInfo;
	}

}
