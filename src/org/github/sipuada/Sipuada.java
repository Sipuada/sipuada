package org.github.sipuada;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import org.github.sipuada.Constants.Transport;
import org.github.sipuada.exceptions.SipuadaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ObjectInUseException;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.SipFactory;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.TransportAlreadySupportedException;
import android.javax.sip.TransportNotSupportedException;

public class Sipuada implements SipuadaApi {

	private final Logger logger = LoggerFactory.getLogger(Sipuada.class);

	private final Comparator<ListeningPoint> listeningPointsComparator =
			new Comparator<ListeningPoint>() {
		private Transport[] priorityTransports = {Transport.TLS, Transport.TCP, Transport.UDP};

		@Override
		public int compare(ListeningPoint thisAddress, ListeningPoint thatAddress) {
			String thisTransport = thisAddress.getTransport().toUpperCase();
			String thatTransport = thatAddress.getTransport().toUpperCase();
			if (thisTransport.equals(thatTransport)) {
				return 0;
			}
			for (Transport transport : priorityTransports) {
				if (thisTransport.equals(transport.toString())) {
					return -1;
				}
				else if (thatTransport.equals(transport.toString())) {
					return 1;
				}
			}
			return 0;
		}
	};

	private final Map<Transport, Set<UserAgent>> transportToUserAgents = new HashMap<>();
	private final String defaultTransport;

	public Sipuada(SipuadaListener listener, String sipUsername, String sipPrimaryHost,
			String sipPassword, String... localAddresses) throws SipuadaException {

		Properties properties = new Properties();
		properties.setProperty("android.javax.sip.STACK_NAME", "SipuadaUserAgentv0");
		SipFactory factory = SipFactory.getInstance();
		SipStack stack = null;
		try {
			stack = factory.createSipStack(properties);
		} catch (PeerUnavailableException ignore) {}
		List<ListeningPoint> listeningPoints = new LinkedList<>();
		for (String localAddress : localAddresses) {
			String localIp = null, localPort = null, transport = null;
			try {
				localIp = localAddress.split(":")[0];
				localPort = localAddress.split(":")[1].split("/")[0];
				transport = localAddress.split("/")[1];
				ListeningPoint listeningPoint = stack.createListeningPoint(localIp,
						Integer.parseInt(localPort), transport);
				listeningPoints.add(listeningPoint);
			} catch (IndexOutOfBoundsException malformedAddress) {
				logger.error("Malformed address: {}.", localAddress);
				throw new SipuadaException("Malformed address provided: " + localAddress,
						malformedAddress);
			} catch (NumberFormatException invalidPort) {
				logger.error("Invalid port for address {}: {}.", localAddress, localPort);
				throw new SipuadaException("Invalid port provided for address "
						+ localAddress + ": " + localPort, invalidPort);
			} catch (TransportNotSupportedException invalidTransport) {
				logger.error("Invalid transport for address {}: {}.", localAddress, transport);
				throw new SipuadaException("Invalid transport provided for address "
						+ localAddress + ": " + transport, invalidTransport);
			} catch (InvalidArgumentException invalidAddress) {
				logger.error("Invalid address provided: {}.", localAddress);
				throw new SipuadaException("Invalid address provided: " + localAddress,
						invalidAddress);
			}
		}
		if (listeningPoints.isEmpty()) {
			logger.error("No local address provided.");
			throw new SipuadaException("No local address provided.", null);
		}

		Map<String, SipProvider> ipToProvider = new HashMap<>();
		Map<String, List<ListeningPoint>> ipToListeningPoints = new HashMap<>();
		List<SipProvider> providers = new LinkedList<>();
		boolean shouldGroupByIp = false;
		try {
			Collections.sort(listeningPoints, listeningPointsComparator);
			ListeningPoint listeningPoint = listeningPoints.get(0);
			SipProvider provider = stack.createSipProvider(listeningPoint);
			providers.add(provider);
			String ipAddressKey = listeningPoint.getIPAddress();
			ipToProvider.put(ipAddressKey, provider);
			ipToListeningPoints.put(ipAddressKey, Collections.singletonList(listeningPoint));
			for (int i=1; i<listeningPoints.size(); i++) {
				listeningPoint = listeningPoints.get(i);
				ipAddressKey = listeningPoint.getIPAddress();
				try {
					provider.addListeningPoint(listeningPoint);
					ipToProvider.put(ipAddressKey, provider);
					if (ipToListeningPoints.containsKey(ipAddressKey)) {
						ipToListeningPoints.get(ipAddressKey).add(listeningPoint);
					}
					else {
						ipToListeningPoints.put(ipAddressKey,
								Collections.singletonList(listeningPoint));
					}
				} catch (TransportAlreadySupportedException invalidTransport) {
					shouldGroupByIp = true;
					break;
				}
			}
		} catch (ObjectInUseException ignore) {}
		if (shouldGroupByIp) {
			ipToProvider.clear();
			ipToListeningPoints.clear();
			Map<String, List<ListeningPoint>> groupByIp = new HashMap<>();
			for (ListeningPoint listeningPoint : listeningPoints) {
				if (!groupByIp.containsKey(listeningPoint.getIPAddress())) {
					groupByIp.put(listeningPoint.getIPAddress(),
							new LinkedList<ListeningPoint>());
				}
				groupByIp.get(listeningPoint.getIPAddress()).add(listeningPoint);
			}
			for (String ipAddressKey : groupByIp.keySet()) {
				listeningPoints = groupByIp.get(ipAddressKey);
				try {
					Collections.sort(listeningPoints, listeningPointsComparator);
					ListeningPoint listeningPoint = listeningPoints.get(0);
					SipProvider provider = stack.createSipProvider(listeningPoint);
					providers.add(provider);
					ipToProvider.put(ipAddressKey, provider);
					for (int i=1; i<listeningPoints.size(); i++) {
						listeningPoint = listeningPoints.get(i);
						try {
							provider.addListeningPoint(listeningPoint);
						} catch (TransportAlreadySupportedException ignore) {}
					}
					ipToListeningPoints.put(ipAddressKey, listeningPoints);
				} catch (ObjectInUseException ignore) {}
			}
		}

		String mostVotedTransport = Transport.UNKNOWN.toString();
		int mostVotesToATransport = 0;
		String[] allLocalIps = ipToProvider.keySet()
				.toArray(new String[ipToProvider.keySet().size()]);
		if (providers.size() == 1) {
			listeningPoints = new LinkedList<>();
			for (String localIp : allLocalIps) {
				listeningPoints.addAll(ipToListeningPoints.get(localIp));
			}
			UserAgent userAgent = new UserAgent(providers.get(0), listener, listeningPoints,
					sipUsername, sipPrimaryHost, sipPassword, allLocalIps);
			for (ListeningPoint listeningPoint : listeningPoints) {
				String rawTransport = listeningPoint.getTransport().toUpperCase();
				Transport transport = Transport.UNKNOWN;
				try {
					transport = Transport.valueOf(rawTransport);
				} catch (IllegalArgumentException ignore) {}
				if (!transportToUserAgents.containsKey(transport)) {
					transportToUserAgents.put(transport, new HashSet<UserAgent>());
				}
				Set<UserAgent> userAgents = transportToUserAgents.get(transport);
				userAgents.add(userAgent);
				int votesToThisTransport = userAgents.size();
				if (votesToThisTransport > mostVotesToATransport) {
					mostVotesToATransport = votesToThisTransport;
					mostVotedTransport = rawTransport;
				}
			}
		}
		else {
			for (String localIp : allLocalIps) {
				SipProvider provider = ipToProvider.get(localIp);
				listeningPoints = ipToListeningPoints.get(localIp);
				UserAgent userAgent = new UserAgent(provider, listener, listeningPoints,
						sipUsername, sipPrimaryHost, sipPassword, allLocalIps);
				for (ListeningPoint listeningPoint : listeningPoints) {
					String rawTransport = listeningPoint.getTransport().toUpperCase();
					Transport transport = Transport.UNKNOWN;
					try {
						transport = Transport.valueOf(rawTransport);
					} catch (IllegalArgumentException ignore) {}
					if (!transportToUserAgents.containsKey(transport)) {
						transportToUserAgents.put(transport, new HashSet<UserAgent>());
					}
					Set<UserAgent> userAgents = transportToUserAgents.get(transport);
					userAgents.add(userAgent);
					int votesToThisTransport = userAgents.size();
					if (votesToThisTransport > mostVotesToATransport) {
						mostVotesToATransport = votesToThisTransport;
						mostVotedTransport = rawTransport;
					}
				}
			}
		}
		defaultTransport = mostVotedTransport;
	}

	@Override
	public boolean register(RegistrationCallback callback) {
		return fetchBestAgent(defaultTransport).sendRegisterRequest(callback);
	}

	@Override
	public boolean inviteToCall(String remoteUser, String remoteDomain,
			CallInvitationCallback callback) {
		return fetchBestAgent(defaultTransport).sendInviteRequest(remoteUser,
				remoteDomain, callback);
	}

	@Override
	public boolean cancelCallInvitation(String callId) {
		return fetchBestAgent(defaultTransport).cancelInviteRequest(callId);
	}

	@Override
	public boolean acceptCallInvitation(String callId) {
		return fetchBestAgent(defaultTransport).answerInviteRequest(callId, true);
	}

	@Override
	public boolean declineCallInvitation(String callId) {
		return fetchBestAgent(defaultTransport).answerInviteRequest(callId, false);
	}

	@Override
	public boolean finishCall(String callId) {
		return fetchBestAgent(defaultTransport).finishCall(callId);
	}

	private UserAgent fetchBestAgent(String rawTransport) {
		Transport transport = Transport.UNKNOWN;
		try {
			transport = Transport.valueOf(rawTransport);
		} catch (IllegalArgumentException ignore) {}
		Set<UserAgent> userAgentCandidates = transportToUserAgents.get(transport);
		int randomNumber = (new Random()).nextInt(userAgentCandidates.size());
		Iterator<UserAgent> iterator = userAgentCandidates.iterator();
		UserAgent bestUserAgent = iterator.next();
		while (iterator.hasNext() && randomNumber > 0) {
			bestUserAgent = iterator.next();
			randomNumber--;
		}
		bestUserAgent.setPreferredTransport(rawTransport);
		return bestUserAgent;
	}

}
