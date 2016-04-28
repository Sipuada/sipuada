package de.tinysip.stun;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.javawi.jstun.test.DiscoveryInfo;
import de.javawi.jstun.test.DiscoveryTest;

/**
 * Executes an asynchronous task for testing the NAT and NAT traversal. Raises a STUNDiscoveryResultEvent when new information is available.
 * 
 */
public class StunDiscoveryTask {

	private final Logger logger = LoggerFactory.getLogger(StunDiscoveryTask.class);

	public StunDiscoveryResultEvent execute(String localAddress, StunInfo stunInfo) {
		InetAddress inetAddress;
		try {
			inetAddress = InetAddress.getByName(localAddress);
		} catch (UnknownHostException invalidLocalAddress) {
			return null;
		}
		try {
		} catch (Exception fetchingInetAddressFailed) {
			fetchingInetAddressFailed.printStackTrace();
			return null;
		}
		DiscoveryInfo info = null;
		try {
			DiscoveryTest test = new DiscoveryTest(inetAddress,
					stunInfo.getLocalPort(), stunInfo.getStunAddress(), stunInfo.getStunPort());
			info = test.test();
		} catch (Exception stunUsingLocalPortFailed) {
			stunUsingLocalPortFailed.printStackTrace();
			try {
				DiscoveryTest test = new DiscoveryTest(inetAddress,
						stunInfo.getStunAddress(), stunInfo.getStunPort());
				info = test.test();
			} catch (Exception stunFailedAtAll) {
				stunFailedAtAll.printStackTrace();
			}
		}
		if (info != null) {
			StunDiscoveryResultEvent event = new StunDiscoveryResultEvent(this, info, stunInfo);
			return event;
		} else {
			logger.error("No info could be retrieved using STUN!");
			return null;
		}
	}

//	/**
//	 * Returns the first Internet-facing InetAddress, or Localhost, if none was found
//	 * 
//	 * @return InetAddress the InetAddress of the interface
//	 * @throws SocketException
//	 * @throws UnknownHostException
//	 */
//	private InetAddress getInetAddress() throws SocketException, UnknownHostException {
//		Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
//		while (ifaces.hasMoreElements()) {
//			NetworkInterface iface = ifaces.nextElement();
//			Enumeration<InetAddress> iaddresses = iface.getInetAddresses();
//			while (iaddresses.hasMoreElements()) {
//				InetAddress iaddress = iaddresses.nextElement();
//				if (InetAddress.class.isInstance(iaddress)) {
//					if ((!iaddress.isLoopbackAddress()) && (!iaddress.isLinkLocalAddress())) {
//						return iaddress;
//					}
//				}
//			}
//		}
//
//		return InetAddress.getLocalHost();
//	}

}
