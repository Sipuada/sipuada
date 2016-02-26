package org.github.sipuada;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.Constants.Transport;
import org.github.sipuada.exceptions.SipuadaException;
import org.github.sipuada.plugins.SipuadaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.gov.nist.gnjvx.sip.Utils;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ObjectInUseException;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.SipFactory;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.TransportNotSupportedException;

public class Sipuada implements SipuadaApi {

	private final Logger logger = LoggerFactory.getLogger(Sipuada.class);
	private final String STACK_NAME_PREFIX = "SipuadaUserAgentv0";

	private final SipuadaListener listener;
	private final String username, primaryHost, password;
	private final Map<Transport, Set<UserAgent>> transportToUserAgents = new HashMap<>();
	private final String defaultTransport;

	private final Map<RequestMethod, SipuadaPlugin> registeredPlugins = new HashMap<>();

	public Sipuada(SipuadaListener sipuadaListener, final String sipUsername,
			final String sipPrimaryHost, String sipPassword,
			String... localAddresses) throws SipuadaException {
		listener = sipuadaListener;
		username = sipUsername;
		primaryHost = sipPrimaryHost;
		password = sipPassword;
		List<ListeningPoint> listeningPoints = new LinkedList<>();
		Map<ListeningPoint, SipStack> listeningPointToStack = new HashMap<>();
		for (String localAddress : localAddresses) {
			String localIp = null, localPort = null, transport = null;
			try {
				localIp = localAddress.split(":")[0];
				localPort = localAddress.split(":")[1].split("/")[0];
				transport = localAddress.split("/")[1];
				SipStack stack = generateSipStack();
				ListeningPoint listeningPoint = stack.createListeningPoint(localIp,
						Integer.parseInt(localPort), transport);
				listeningPoints.add(listeningPoint);
				listeningPointToStack.put(listeningPoint, stack);
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

		String mostVotedTransport = Transport.UNKNOWN.toString();
		int mostVotesToATransport = 0;
		Map<Transport, Integer> transportVotes = new HashMap<>();
		for (Transport transport : Transport.values()) {
			transportVotes.put(transport, 0);
		}
		List<Transport> winners = new LinkedList<>();
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
			SipProvider sipProvider = null;
			try {
				SipStack stack = listeningPointToStack.get(listeningPoint);
				sipProvider = stack.createSipProvider(listeningPoint);
			} catch (ObjectInUseException unexpectedException) {
				logger.error("Unexpected problem: {}.", unexpectedException.getMessage(),
						unexpectedException.getCause());
				throw new SipuadaException("Unexpected problem: "
						+ unexpectedException.getMessage(), unexpectedException);
			}
			userAgents.add(new UserAgent(sipProvider, sipuadaListener,
					registeredPlugins, sipUsername, sipPrimaryHost, sipPassword,
					listeningPoint.getIPAddress(),
					Integer.toString(listeningPoint.getPort()),
					rawTransport));
			transportVotes.put(transport, transportVotes.get(transport) + 1);
			int votesToThisTransport = transportVotes.get(transport);
			if (votesToThisTransport > mostVotesToATransport) {
				mostVotesToATransport = votesToThisTransport;
				mostVotedTransport = rawTransport;
			}
		}
		for (Transport transport : transportVotes.keySet()) {
			if (transportVotes.get(transport) == mostVotesToATransport) {
				winners.add(transport);
			}
		}
		if (winners.size() <= 1) {
			defaultTransport = mostVotedTransport;
		}
		else {
			defaultTransport = winners.get((new Random())
					.nextInt(winners.size())).toString();
		}

		StringBuilder userAgentsDump = new StringBuilder();
		userAgentsDump.append("{ ");
		int transportGroupIndex = 0;
		for (Transport transport : transportToUserAgents.keySet()) {
			if (transportGroupIndex != 0) {
				userAgentsDump.append(", ");
			}
			userAgentsDump.append(String.format("'%s' : { ", transport));
			Iterator<UserAgent> iterator = transportToUserAgents.get(transport).iterator();
			int userAgentIndex = 0;
			while (iterator.hasNext()) {
				if (userAgentIndex != 0) {
					userAgentsDump.append(", ");
				}
				UserAgent userAgent = iterator.next();
				userAgentsDump.append(String.format("'%s:%d'",
						userAgent.getLocalIp(), userAgent.getLocalPort()));
				userAgentIndex++;
			}
			userAgentsDump.append(" } ");
			transportGroupIndex++;
		}
		userAgentsDump.append(" }");
		logger.info("Sipuada created. Default transport: {}. UA: {}",
				defaultTransport, userAgentsDump.toString());
		wipeAddresses(new RegistrationCallback() {

			@Override
			public void onRegistrationSuccess(List<String> registeredContacts) {
				logger.info("Cleared all contact bindings for {}@{}.",
						sipUsername, sipPrimaryHost);
			}

			@Override
			public void onRegistrationFailed(String reason) {
				logger.info("Could not clear all contact bindings for {}@{}: {}",
						sipUsername, sipPrimaryHost, reason);
			}

		});
	}

	private SipStack generateSipStack() {
		Properties properties = new Properties();
		properties.setProperty("android.javax.sip.STACK_NAME", String.format("%s_%s",
				STACK_NAME_PREFIX, Utils.getInstance().generateTag()));
		SipFactory factory = SipFactory.getInstance();
		try {
			return factory.createSipStack(properties);
		} catch (PeerUnavailableException unexpectedException) {
			logger.error("Unexpected problem: {}.", unexpectedException.getMessage(),
					unexpectedException.getCause());
			throw new SipuadaException("Unexpected problem: "
					+ unexpectedException.getMessage(), unexpectedException);
		}
	}

	private boolean wipeAddresses(final RegistrationCallback callback) {
		return fetchBestAgent(defaultTransport).sendUnregisterRequest(callback);
	}

	@Override
	public boolean registerAddresses(RegistrationCallback callback) {
		List<String> registeredAddresses = new LinkedList<>();
		for (Transport transport : transportToUserAgents.keySet()) {
			Set<UserAgent> userAgents = transportToUserAgents.get(transport);
			Iterator<UserAgent> iterator = userAgents.iterator();
			while (iterator.hasNext()) {
				UserAgent userAgent = iterator.next();
				registeredAddresses.add(String.format("%s:%d",
						userAgent.getLocalIp(), userAgent.getLocalPort()));
			}
		}
		return fetchBestAgent(defaultTransport).sendRegisterRequest(callback,
				registeredAddresses.toArray(new String[registeredAddresses.size()]));
	}

	@Override
	public boolean includeAddresses(RegistrationCallback callback, String... localAddresses) {
		final List<ListeningPoint> expired = new LinkedList<>();
		final List<ListeningPoint> renewed = new LinkedList<>();
		final List<ListeningPoint> newest = new LinkedList<>();
		categorizeAddresses(expired, renewed, newest, localAddresses);
		List<String> registeredAddresses = new LinkedList<>();
		for (ListeningPoint listeningPoint : renewed) {
			logger.debug("{}:{}/{} registration will be renewed.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
			registeredAddresses.add(String.format("%s:%d",
					listeningPoint.getIPAddress(), listeningPoint.getPort()));
		}
		for (ListeningPoint listeningPoint : newest) {
			logger.debug("{}:{}/{} registration will be included.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
			registeredAddresses.add(String.format("%s:%d",
					listeningPoint.getIPAddress(), listeningPoint.getPort()));
		}
		for (ListeningPoint listeningPoint : expired) {
			logger.debug("{}:{}/{} registration will be left untouched.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
		}
		return fetchBestAgent(defaultTransport).sendRegisterRequest(callback,
				registeredAddresses.toArray(new String[registeredAddresses.size()]));
	}

	@Override
	public boolean overwriteAddresses(final RegistrationCallback callback,
			String... localAddresses) {
		if (localAddresses.length == 0) {
			logger.error("There must always be at least one local address " +
					"bound with this Sipuada instance.");
			return false;
		}
		final List<ListeningPoint> expired = new LinkedList<>();
		final List<ListeningPoint> renewed = new LinkedList<>();
		final List<ListeningPoint> newest = new LinkedList<>();
		categorizeAddresses(expired, renewed, newest, localAddresses);
		final List<String> registeredAddresses = new LinkedList<>();
		for (ListeningPoint listeningPoint : renewed) {
			logger.debug("{}:{}/{} registration will be renewed.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
			registeredAddresses.add(String.format("%s:%d",
					listeningPoint.getIPAddress(), listeningPoint.getPort()));
		}
		for (ListeningPoint listeningPoint : newest) {
			logger.debug("{}:{}/{} registration will be included.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
			registeredAddresses.add(String.format("%s:%d",
					listeningPoint.getIPAddress(), listeningPoint.getPort()));
		}
		final List<String> unregisteredAddresses = new LinkedList<>();
		for (ListeningPoint listeningPoint : expired) {
			logger.debug("{}:{}/{} registration will be removed.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
			unregisteredAddresses.add(String.format("%s:%d",
					listeningPoint.getIPAddress(), listeningPoint.getPort()));
		}
		final UserAgent chosenUserAgent = chooseBestAgentThatWontBeRemoved(expired);
		boolean couldSendRequest = true;
		if (!registeredAddresses.isEmpty()) {
			couldSendRequest = chosenUserAgent.sendRegisterRequest(new RegistrationCallback() {

				@Override
				public void onRegistrationSuccess(final List<String> registeredContacts) {
					if (!unregisteredAddresses.isEmpty()) {
						boolean couldSendRequest = chosenUserAgent
								.sendUnregisterRequest(new RegistrationCallback() {

							@Override
							public void onRegistrationSuccess(List<String> unregisteredContacts) {
								performUserAgentsCleanup(expired);
								callback.onRegistrationSuccess(registeredContacts);
							}

							@Override
							public void onRegistrationFailed(String reason) {
								callback.onRegistrationFailed(reason);
							}

						}, unregisteredAddresses.toArray(new String[unregisteredAddresses.size()]));
						if (!couldSendRequest) {
							callback.onRegistrationFailed(String.format("Could register some contacts " +
									"(%s) but could not unregister others (%s).", registeredAddresses,
									unregisteredAddresses));
						}
					} else {
						callback.onRegistrationSuccess(registeredContacts);
					}
				}

				@Override
				public void onRegistrationFailed(String reason) {
					callback.onRegistrationFailed(reason);
				}

			}, registeredAddresses.toArray(new String[registeredAddresses.size()]));

		} else if (!unregisteredAddresses.isEmpty()) {
			couldSendRequest = chosenUserAgent.sendUnregisterRequest(new RegistrationCallback() {

				@Override
				public void onRegistrationSuccess(List<String> registeredContacts) {
					performUserAgentsCleanup(expired);
					callback.onRegistrationSuccess(new LinkedList<String>());
				}

				@Override
				public void onRegistrationFailed(String reason) {
					callback.onRegistrationFailed(reason);
				}

			}, unregisteredAddresses.toArray(new String[unregisteredAddresses.size()]));
		}
		return couldSendRequest;
	}

	private void categorizeAddresses(List<ListeningPoint> expired, List<ListeningPoint> renewed,
			List<ListeningPoint> newest, String... localAddresses) {
		boolean oldAddresses[] = new boolean[localAddresses.length];
		for (Transport transport : transportToUserAgents.keySet()) {
			Set<UserAgent> userAgents = transportToUserAgents.get(transport);
			Iterator<UserAgent> iterator = userAgents.iterator();
			while (iterator.hasNext()) {
				UserAgent userAgent = iterator.next();
				SipProvider provider = userAgent.getProvider();
				for (ListeningPoint listeningPoint : provider.getListeningPoints()) {
					int index = 0;
					boolean addressRenewed = false;
					for (String localAddress : localAddresses) {
						String localIp = null, localPortRaw = null, localTransport = null;
						int localPort = -1;
						try {
							localIp = localAddress.split(":")[0];
							localPortRaw = localAddress.split(":")[1].split("/")[0];
							localPort = Integer.parseInt(localPortRaw);
							localTransport = localAddress.split("/")[1];
						} catch (IndexOutOfBoundsException malformedAddress) {
							logger.error("Malformed address: {}.", localAddress);
							throw new SipuadaException("Malformed address provided: " + localAddress,
									malformedAddress);
						} catch (NumberFormatException invalidPort) {
							logger.error("Invalid port for address {}: {}.", localAddress, localPort);
							throw new SipuadaException("Invalid port provided for address "
									+ localAddress + ": " + localPort, invalidPort);
						}
						if (listeningPoint.getIPAddress().equals(localIp)
								&& listeningPoint.getPort() == localPort
								&& listeningPoint.getTransport().toUpperCase()
									.equals(localTransport)) {
							renewed.add(listeningPoint);
							addressRenewed = true;
							oldAddresses[index] = true;
							break;
						}
						index++;
					}
					if (!addressRenewed) {
						expired.add(listeningPoint);
					}
				}
			}
		}
		int index = 0;
		for (String localAddress : localAddresses) {
			if (!oldAddresses[index]) {
				String localIp = null, localPort = null, rawTransport = null;
				try {
					localIp = localAddress.split(":")[0];
					localPort = localAddress.split(":")[1].split("/")[0];
					rawTransport = localAddress.split("/")[1];
					SipStack stack = generateSipStack();
					ListeningPoint listeningPoint = stack.createListeningPoint(localIp,
							Integer.parseInt(localPort), rawTransport);
					newest.add(listeningPoint);
					SipProvider sipProvider = stack.createSipProvider(listeningPoint);
					Transport transport = Transport.UNKNOWN;
					try {
						transport = Transport.valueOf(rawTransport);
					} catch (IllegalArgumentException ignore) {}
					if (!transportToUserAgents.containsKey(transport)) {
						transportToUserAgents.put(transport, new HashSet<UserAgent>());
					}
					Set<UserAgent> userAgents = transportToUserAgents.get(transport);
					userAgents.add(new UserAgent(sipProvider, listener, registeredPlugins,
							username, primaryHost, password, listeningPoint.getIPAddress(),
							Integer.toString(listeningPoint.getPort()),
							rawTransport));
				} catch (IndexOutOfBoundsException malformedAddress) {
					logger.error("Malformed address: {}.", localAddress);
					throw new SipuadaException("Malformed address provided: " + localAddress,
							malformedAddress);
				} catch (NumberFormatException invalidPort) {
					logger.error("Invalid port for address {}: {}.", localAddress, localPort);
					throw new SipuadaException("Invalid port provided for address "
							+ localAddress + ": " + localPort, invalidPort);
				} catch (TransportNotSupportedException invalidTransport) {
					logger.error("Invalid transport for address {}: {}.", localAddress, rawTransport);
					throw new SipuadaException("Invalid transport provided for address "
							+ localAddress + ": " + rawTransport, invalidTransport);
				} catch (InvalidArgumentException invalidAddress) {
					logger.error("Invalid address provided: {}.", localAddress);
					throw new SipuadaException("Invalid address provided: " + localAddress,
							invalidAddress);
				} catch (ObjectInUseException unexpectedException) {
					logger.error("Unexpected problem: {}.", unexpectedException.getMessage(),
							unexpectedException.getCause());
					throw new SipuadaException("Unexpected problem: "
							+ unexpectedException.getMessage(), unexpectedException);
				}
			}
			index++;
		}
	}

	private UserAgent chooseBestAgentThatWontBeRemoved(List<ListeningPoint> expired) {
		UserAgent originalBestUserAgent = fetchBestAgent(defaultTransport);
		boolean originalUserAgentWillBeRemoved = false;
		for (ListeningPoint listeningPoint : expired) {
			if (listeningPoint.getIPAddress().equals(originalBestUserAgent.getLocalIp())
					&& listeningPoint.getPort() == originalBestUserAgent.getLocalPort()
					&& listeningPoint.getTransport().toUpperCase()
						.equals(originalBestUserAgent.getTransport())) {
				originalUserAgentWillBeRemoved = true;
			}
		}
		UserAgent nextBestUserAgent = null;
		if (originalUserAgentWillBeRemoved) {
			String rawTransport = originalBestUserAgent.getTransport();
			Transport transport = Transport.UNKNOWN;
			try {
				transport = Transport.valueOf(rawTransport);
			} catch (IllegalArgumentException ignore) {}
			Set<UserAgent> userAgents = transportToUserAgents.get(transport);
			userAgents.remove(originalBestUserAgent);
			nextBestUserAgent = chooseBestAgentThatWontBeRemoved(expired);
			userAgents.add(originalBestUserAgent);
			return nextBestUserAgent;
		}
		return originalBestUserAgent;
	}

	private void performUserAgentsCleanup(List<ListeningPoint> expiredListeningPoints) {
		for (Transport transport : transportToUserAgents.keySet()) {
			Set<UserAgent> userAgents = transportToUserAgents.get(transport);
			Iterator<UserAgent> iterator = userAgents.iterator();
			while (iterator.hasNext()) {
				UserAgent userAgent = iterator.next();
				for (ListeningPoint expiredListeningPoint : expiredListeningPoints) {
					if (expiredListeningPoint.getIPAddress().equals(userAgent.getLocalIp())
							&& expiredListeningPoint.getPort() == userAgent.getLocalPort()
							&& expiredListeningPoint.getTransport().toUpperCase()
								.equals(userAgent.getTransport())) {
						SipProvider provider = userAgent.getProvider();
						SipStack stack = provider.getSipStack();
						try {
							stack.deleteSipProvider(provider);
							stack.deleteListeningPoint(expiredListeningPoint);
						} catch (ObjectInUseException ignore) {}
						iterator.remove();
						userAgents.remove(userAgent);
						if (userAgents.isEmpty()) {
							transportToUserAgents.remove(transport);
						}
					}
				}
			}
		}
	}

	@Override
	public boolean queryOptions(String remoteUser, String remoteDomain, OptionsQueryingCallback callback) {
		return fetchBestAgent(defaultTransport).sendOptionsRequest(remoteUser,
				remoteDomain, callback);
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
		if (userAgentCandidates == null) {
			userAgentCandidates = new HashSet<>();
		}
		else {
			userAgentCandidates = new HashSet<>(userAgentCandidates);
		}
		int randomNumber = 0;
		Iterator<UserAgent> iterator = userAgentCandidates.iterator();
		UserAgent bestUserAgent = null;
		try {
			bestUserAgent = iterator.next();
			randomNumber = (new Random()).nextInt(userAgentCandidates.size());
		} catch (NoSuchElementException noUserAgentsOfGivenTransport) {
			for (Transport otherTransport : transportToUserAgents.keySet()) {
				if (otherTransport == transport) {
					continue;
				}
				Set<UserAgent> userAgents = transportToUserAgents.get(otherTransport);
				if (!userAgents.isEmpty()) {
					userAgentCandidates.addAll(userAgents);
				}
			}
			iterator = userAgentCandidates.iterator();
			try {
				bestUserAgent = iterator.next();
				randomNumber = (new Random()).nextInt(userAgentCandidates.size());
			} catch (NoSuchElementException noUserAgentsAtAll) {
				throw new SipuadaException("This Sipuada instance has no UserAgents" +
						" available to process the requested operation.", null);
			}
		}
		while (iterator.hasNext() && randomNumber > 0) {
			bestUserAgent = iterator.next();
			randomNumber--;
		}
		return bestUserAgent;
	}

	@Override
	public boolean registerPlugin(SipuadaPlugin plugin) {
		if (registeredPlugins.containsKey(RequestMethod.INVITE)) {
			return false;
		}
		registeredPlugins.put(RequestMethod.INVITE, plugin);
		return true;
	}

	public void destroy() {
		for (Transport transport : transportToUserAgents.keySet()) {
			Set<UserAgent> userAgents = transportToUserAgents.get(transport);
			for (UserAgent userAgent : userAgents) {
				SipProvider provider = userAgent.getProvider();
				SipStack stack = provider.getSipStack();
				stack.stop();
				for (ListeningPoint listeningPoint : provider.getListeningPoints()) {
					try {
						stack.deleteListeningPoint(listeningPoint);
					} catch (ObjectInUseException ignore) {}
				}
				try {
					stack.deleteSipProvider(provider);
				} catch (ObjectInUseException ignore) {}
			}
		}
	}

}
