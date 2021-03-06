package org.github.sipuada;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
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
import java.util.Timer;
import java.util.TimerTask;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.Constants.Transport;
import org.github.sipuada.Sipuada.RegisterOperation.OperationMethod;
import org.github.sipuada.events.UserAgentNominatedForIncomingRequest;
import org.github.sipuada.exceptions.InternalJainSipException;
import org.github.sipuada.exceptions.SipuadaException;
import org.github.sipuada.plugins.SipuadaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import android.gov.nist.javax.sip.Utils;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ObjectInUseException;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.RequestEvent;
import android.javax.sip.SipException;
import android.javax.sip.SipFactory;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.TransportNotSupportedException;
import android.javax.sip.address.URI;
import android.javax.sip.header.CallIdHeader;

public class Sipuada implements SipuadaApi {

	private static final String STACK_NAME_PREFIX = "SipuadaUserAgentv0";
	private static final int DEFAULT_REGISTRATION_LIFESPAN_SECONDS = 3600;

	private final Logger logger = LoggerFactory.getLogger(Sipuada.class);

	private final EventBus eventBus = new EventBus();
	private final SipuadaListener listener;
	private final String username, primaryHost, password;

	private final Map<Transport, Set<SipUserAgent>> transportToUserAgents = Collections
			.synchronizedMap(new HashMap<Transport, Set<SipUserAgent>>());
	private final String defaultTransport;

	private final Map<RequestMethod, SipuadaPlugin> registeredPlugins = new HashMap<>();

	private final Map<String, CallIdHeader> registerCallIds = Collections
			.synchronizedMap(new HashMap<String, CallIdHeader>());
	private final Map<URI, Long> registerCSeqs = Collections
			.synchronizedMap(new HashMap<URI, Long>());
	private final Map<RequestMethod, Boolean> registerOperationsInProgress = Collections
			.synchronizedMap(new HashMap<RequestMethod, Boolean>());
	{
		for (RequestMethod method : RequestMethod.values()) {
			registerOperationsInProgress.put(method, false);
		}
	}
	private final List<RegisterOperation> postponedRegisterOperations = Collections
			.synchronizedList(new LinkedList<RegisterOperation>());

	private final Map<String, SipUserAgent> callIdToActiveUserAgent = Collections
			.synchronizedMap(new HashMap<String, SipUserAgent>());
	private final Map<SipUserAgent, Set<String>> activeUserAgentCallIds = Collections
			.synchronizedMap(new HashMap<SipUserAgent, Set<String>>());

	private final Map<String, Set<ElectionCandidate>> electionIdToCandidates = Collections
			.synchronizedMap(new HashMap<String, Set<ElectionCandidate>>());
	private final Map<String, Boolean> electionStarted = new HashMap<>();
	private Timer electionTimer;

	private boolean intolerantModeEnabled = false;

	protected static class RegisterOperation {

		public enum OperationMethod {
			REGISTER_ADDRESSES, UNREGISTER_ADDRESSES, CLEAR_ADDRESSES,
			INCLUDE_USER_AGENTS, EXCLUDE_USER_AGENTS, OVERWRITE_USER_AGENTS
		}

		public final OperationMethod method;
		public final BasicRequestCallback callback;
		public final int expires;
		public final String[] arguments;

		public RegisterOperation(OperationMethod method, BasicRequestCallback callback,
				int expires, String... arguments) {
			this.method = method;
			this.callback = callback;
			this.expires = expires;
			this.arguments = arguments;
		}

	}

	protected class ElectionCandidate {

		private final SipUserAgent userAgentCandidate;
		private final RequestEvent requestEvent;

		public ElectionCandidate(SipUserAgent userAgent, RequestEvent event) {
			userAgentCandidate = userAgent;
			requestEvent = event;
		}

		public SipUserAgent getUserAgentCandidate() {
			return userAgentCandidate;
		}

		public RequestEvent getRequestEvent() {
			return requestEvent;
		}

	}

	public Sipuada(SipuadaListener sipuadaListener, final String sipUsername,
			final String sipPrimaryHost, String sipPassword,
			String... localAddresses) throws SipuadaException {
		this(false, sipuadaListener, sipUsername, sipPrimaryHost, sipPassword, localAddresses);
	}

	public Sipuada(boolean intolerantModeIsEnabled, SipuadaListener sipuadaListener,
			final String sipUsername, final String sipPrimaryHost, String sipPassword,
			String... localAddresses) throws SipuadaException {
		eventBus.register(this);
		intolerantModeEnabled = intolerantModeIsEnabled;
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
			} catch (IllegalArgumentException ignore) {
				ignore.printStackTrace();
			}
			synchronized (transportToUserAgents) {
				if (!transportToUserAgents.containsKey(transport)) {
					transportToUserAgents.put(transport, new HashSet<SipUserAgent>());
				}
			}
			Set<SipUserAgent> userAgents = transportToUserAgents.get(transport);
			SipProvider sipProvider;
			final String stackName;
			try {
				SipStack stack = listeningPointToStack.get(listeningPoint);
				sipProvider = stack.createSipProvider(listeningPoint);
				stack.start();
				stackName = stack.getStackName();
			} catch (ObjectInUseException unexpectedException) {
				logger.error("Unexpected problem: {}.", unexpectedException.getMessage(),
						unexpectedException.getCause());
				throw new SipuadaException("Unexpected problem: "
						+ unexpectedException.getMessage(), unexpectedException);
			} catch (SipException unexpectedException) {
				logger.error("Unexpected problem: {}.", unexpectedException.getMessage(),
						unexpectedException.getCause());
				throw new SipuadaException("Unexpected problem: "
						+ unexpectedException.getMessage(), unexpectedException);
			}
			SipUserAgent userAgent = new SipUserAgent(stackName, eventBus, sipProvider, sipuadaListener,
					registeredPlugins, sipUsername, sipPrimaryHost, sipPassword,
					listeningPoint.getIPAddress(), Integer.toString(listeningPoint.getPort()),
					rawTransport, callIdToActiveUserAgent, activeUserAgentCallIds,
					registerCallIds, registerCSeqs, intolerantModeEnabled);
			userAgents.add(userAgent);
			activeUserAgentCallIds.put(userAgent, Collections
					.synchronizedSet(new HashSet<String>()));
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
		Set<Transport> transports = transportToUserAgents.keySet();
		synchronized (transportToUserAgents) {
			for (Transport transport : transports) {
				if (transportGroupIndex != 0) {
					userAgentsDump.append(", ");
				}
				userAgentsDump.append(String.format("'%s' : { ", transport));
				Set<SipUserAgent> userAgents = transportToUserAgents.get(transport);
				synchronized (userAgents) {
					Iterator<SipUserAgent> iterator = userAgents.iterator();
					int userAgentIndex = 0;
					while (iterator.hasNext()) {
						if (userAgentIndex != 0) {
							userAgentsDump.append(", ");
						}
						SipUserAgent userAgent = iterator.next();
						userAgentsDump.append(String.format("'%s:%d'",
								userAgent.getLocalIp(), userAgent.getLocalPort()));
						userAgentIndex++;
					}
				}
				userAgentsDump.append(" } ");
				transportGroupIndex++;
			}
			userAgentsDump.append(" }");
			logger.info("Sipuada created. Default transport: {}. UA: {}",
					defaultTransport, userAgentsDump.toString());
		}
	}

	public void setIntolerantModeEnabled(boolean intolerantModeIsEnabled) {
		intolerantModeEnabled = intolerantModeIsEnabled;
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

	@Subscribe
	public synchronized void electBestUserAgentForIncomingRequest(UserAgentNominatedForIncomingRequest event) {
		String method = event.getRequestEvent().getRequest().getMethod();
		String callId = event.getCallId();
		final String electionId = String.format("(%s:%s)", method, callId);
		if (!electionStarted.containsKey(electionId) || !electionStarted.get(electionId)) {
			if (electionTimer != null) {
				electionTimer.cancel();
			}
			synchronized (electionIdToCandidates) {
				if (!electionIdToCandidates.containsKey(electionId)) {
					electionIdToCandidates.put(electionId, Collections
							.synchronizedSet(new HashSet<ElectionCandidate>()));
				}
				electionIdToCandidates.get(electionId)
					.add(new ElectionCandidate(event.getCandidateUserAgent(),
							event.getRequestEvent()));
			}
			electionTimer = new Timer();
			electionTimer.schedule(new TimerTask() {

				@Override
				public void run() {
					electionStarted.put(electionId, true);
					Set<ElectionCandidate> electionCandidates = electionIdToCandidates.get(electionId);
					synchronized (electionCandidates) {
						Iterator<ElectionCandidate> candidatesIterator = electionCandidates.iterator();
						ElectionCandidate bestCandidate = candidatesIterator.next();
						int randomNumber = (new Random()).nextInt(electionCandidates.size());
						while (candidatesIterator.hasNext() && randomNumber > 0) {
							bestCandidate = candidatesIterator.next();
							randomNumber--;
						}
						SipUserAgent userAgent = bestCandidate.getUserAgentCandidate();
						RequestEvent requestEvent = bestCandidate.getRequestEvent();
						logger.debug("{}:{}/{}'s UAS was elected to process an incoming {} request!",
								userAgent.getLocalIp(), userAgent.getLocalPort(), userAgent.getTransport(),
								requestEvent.getRequest().getMethod());
						userAgent.doProcessRequest(requestEvent);
					}
					electionCandidates.clear();
					electionStarted.put(electionId, false);
				}

			}, 1000);
		}
	}

	@Override
	public boolean registerAddresses(final BasicRequestCallback callback) {
		return registerAddresses(callback, DEFAULT_REGISTRATION_LIFESPAN_SECONDS);
	}

	@Override
	public boolean registerAddresses(final BasicRequestCallback callback, int expires) {
		if (registerOperationsInProgress.get(RequestMethod.REGISTER)) {
			postponedRegisterOperations.add(new RegisterOperation(OperationMethod.REGISTER_ADDRESSES,
					callback, expires));
			logger.info("Register addresses: operation postponed because another " +
					"related operation is in progress.");
			return true;
		}
		List<String> registeredAddresses = new LinkedList<>();
		Set<Transport> transports = transportToUserAgents.keySet();
		synchronized (transportToUserAgents) {
			for (Transport transport : transports) {
				Set<SipUserAgent> userAgents = transportToUserAgents.get(transport);
				synchronized (userAgents) {
					Iterator<SipUserAgent> iterator = userAgents.iterator();
					while (iterator.hasNext()) {
						SipUserAgent userAgent = iterator.next();
						registeredAddresses.add(String.format("%s:%d",
								userAgent.getLocalIp(), userAgent.getLocalPort()));
					}
				}
			}
		}
		logger.debug("All existing registrations will be renewed.");
		try {
			boolean couldDispatchOperation = chooseBestAgentThatIsAvailable()
				.sendRegisterRequest(new BasicRequestCallback() {

					@Override
					public void onRequestSuccess(String localUser, String localUserDomain,
							Object... registeredContacts) {
						registerRelatedOperationFinished(localUser, localUserDomain);
						callback.onRequestSuccess(localUser, localUserDomain, registeredContacts);
					}

					@Override
					public void onRequestFailed(String localUser, String localUserDomain, String reason) {
						registerRelatedOperationFinished(localUser, localUserDomain);
						callback.onRequestFailed(localUser, localUserDomain, reason);
					}

				}, expires, registeredAddresses.toArray(new String[registeredAddresses.size()]));
			if (couldDispatchOperation) {
				registerOperationsInProgress.put(RequestMethod.REGISTER, true);
			}
			return couldDispatchOperation;
		} catch (InternalJainSipException internalJainSipError) {
			return false;
		}
	}

	@Override
	public boolean unregisterAddresses(final BasicRequestCallback callback, String... localAddresses) {
		if (registerOperationsInProgress.get(RequestMethod.REGISTER)) {
			postponedRegisterOperations.add(new RegisterOperation(OperationMethod.UNREGISTER_ADDRESSES,
					callback, 0, localAddresses));
			logger.info("Unregister addresses: operation postponed because another " +
					"related operation is in progress.");
			return true;
		}
		final List<ListeningPoint> mantained = new LinkedList<>();
		final List<ListeningPoint> expired = new LinkedList<>();
		final List<ListeningPoint> brandNew = new LinkedList<>();
		Map<ListeningPoint, SipStack> listeningPointToStack = new HashMap<>();
		categorizeAddresses(mantained, expired, brandNew, listeningPointToStack, false, localAddresses);
		List<String> unregisteredAddresses = new LinkedList<>();
		for (ListeningPoint listeningPoint : expired) {
			logger.debug("{}:{}/{} registration will be excluded " +
					"(but Sipuada instance bound to it will be kept).", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
			unregisteredAddresses.add(String.format("%s:%d",
					listeningPoint.getIPAddress(), listeningPoint.getPort()));
		}
		for (ListeningPoint listeningPoint : brandNew) {
			logger.debug("{}:{}/{} registration will be ignored.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
		}
		if (unregisteredAddresses.isEmpty()) {
			for (ListeningPoint listeningPoint : mantained) {
				logger.debug("{}:{}/{} registration will be excluded " +
					"(but Sipuada instance bound to it will be kept).", listeningPoint.getIPAddress(),
						listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
				unregisteredAddresses.add(String.format("%s:%d",
						listeningPoint.getIPAddress(), listeningPoint.getPort()));
			}
		} else {
			for (ListeningPoint listeningPoint : mantained) {
				logger.debug("{}:{}/{} registration will be left untouched.", listeningPoint.getIPAddress(),
						listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
			}
		}
		try {
			boolean couldDispatchOperation = chooseBestAgentThatIsAvailable()
				.sendUnregisterRequest(new BasicRequestCallback() {

					@Override
					public void onRequestSuccess(String localUser, String localUserDomain,
							Object... unregisteredContacts) {
						registerRelatedOperationFinished(localUser, localUserDomain);
						callback.onRequestSuccess(localUser, localUserDomain, unregisteredContacts);
					}

					@Override
					public void onRequestFailed(String localUser, String localUserDomain, String reason) {
						registerRelatedOperationFinished(localUser, localUserDomain);
						callback.onRequestFailed(localUser, localUserDomain, reason);
					}

				}, unregisteredAddresses.toArray(new String[unregisteredAddresses.size()]));
			if (couldDispatchOperation) {
				registerOperationsInProgress.put(RequestMethod.REGISTER, true);
			}
			return couldDispatchOperation;
		} catch (InternalJainSipException internalJainSipError) {
			return false;
		}
	}

	@Override
	public boolean clearAddresses(final BasicRequestCallback callback) {
		if (registerOperationsInProgress.get(RequestMethod.REGISTER)) {
			postponedRegisterOperations.add(new RegisterOperation(OperationMethod.CLEAR_ADDRESSES,
					callback, 0));
			logger.info("Unregister addresses: operation postponed because another " +
					"related operation is in progress.");
			return true;
		}
		logger.debug("All existing registrations will be excluded" +
				" (but Sipuada instances bound to them will be kept).");
		try {
			boolean couldDispatchOperation = chooseBestAgentThatIsAvailable()
				.sendUnregisterRequest(new BasicRequestCallback() {

					@Override
					public void onRequestSuccess(String localUser, String localUserDomain,
							Object... unregisteredContacts) {
						registerRelatedOperationFinished(localUser, localUserDomain);
						callback.onRequestSuccess(localUser, localUserDomain, unregisteredContacts);
					}

					@Override
					public void onRequestFailed(String localUser, String localUserDomain, String reason) {
						registerRelatedOperationFinished(localUser, localUserDomain);
						callback.onRequestFailed(localUser, localUserDomain, reason);
					}

				});
			if (couldDispatchOperation) {
				registerOperationsInProgress.put(RequestMethod.REGISTER, true);
			}
			return couldDispatchOperation;
		} catch (InternalJainSipException internalJainSipError) {
			return false;
		}
	}

	@Override
	public boolean includeUserAgents(final BasicRequestCallback callback,
			String... localAddresses) {
			return includeUserAgents(callback, DEFAULT_REGISTRATION_LIFESPAN_SECONDS, localAddresses);
	}

	@Override
	public boolean includeUserAgents(final BasicRequestCallback callback,
			int expires, String... localAddresses) {
		if (localAddresses.length == 0) {
			logger.error("Include addresses: operation invalid as no local addresses " +
					"were provided.");
			return false;
		}
		if (registerOperationsInProgress.get(RequestMethod.REGISTER)) {
			postponedRegisterOperations.add(new RegisterOperation(OperationMethod.INCLUDE_USER_AGENTS,
					callback, expires, localAddresses));
			logger.info("Include addresses: operation postponed because another " +
					"related operation is in progress.");
			return true;
		}
		final List<ListeningPoint> ommited = new LinkedList<>();
		final List<ListeningPoint> renewed = new LinkedList<>();
		final List<ListeningPoint> brandNew = new LinkedList<>();
		Map<ListeningPoint, SipStack> listeningPointToStack = new HashMap<>();
		categorizeAddresses(ommited, renewed, brandNew, listeningPointToStack, true, localAddresses);
		performUserAgentsCreation(brandNew, listeningPointToStack);
		List<String> registeredAddresses = new LinkedList<>();
		for (ListeningPoint listeningPoint : renewed) {
			logger.debug("{}:{}/{} registration will be renewed.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
			registeredAddresses.add(String.format("%s:%d",
					listeningPoint.getIPAddress(), listeningPoint.getPort()));
		}
		for (ListeningPoint listeningPoint : brandNew) {
			logger.debug("{}:{}/{} registration will be included.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
			registeredAddresses.add(String.format("%s:%d",
					listeningPoint.getIPAddress(), listeningPoint.getPort()));
		}
		for (ListeningPoint listeningPoint : ommited) {
			logger.debug("{}:{}/{} registration will be left untouched.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
		}
		try {
			boolean couldDispatchOperation = chooseBestAgentThatIsAvailable()
				.sendRegisterRequest(new BasicRequestCallback() {

					@Override
					public void onRequestSuccess(String localUser, String localUserDomain,
							Object... registeredContacts) {
						registerRelatedOperationFinished(localUser, localUserDomain);
						callback.onRequestSuccess(localUser, localUserDomain, registeredContacts);
					}

					@Override
					public void onRequestFailed(String localUser, String localUserDomain, String reason) {
						registerRelatedOperationFinished(localUser, localUserDomain);
						callback.onRequestFailed(localUser, localUserDomain, reason);
					}

				}, expires, registeredAddresses.toArray(new String[registeredAddresses.size()]));
			if (couldDispatchOperation) {
				registerOperationsInProgress.put(RequestMethod.REGISTER, true);
			}
			return couldDispatchOperation;
		} catch (InternalJainSipException internalJainSipError) {
			return false;
		}
	}

	@Override
	public boolean excludeUserAgents(final BasicRequestCallback callback,
			String... localAddresses) {
		if (registerOperationsInProgress.get(RequestMethod.REGISTER)) {
			postponedRegisterOperations.add(new RegisterOperation(OperationMethod.EXCLUDE_USER_AGENTS,
					callback, 0, localAddresses));
			logger.info("Exclude addresses: operation postponed because another " +
					"related operation is in progress.");
			return true;
		}
		final List<ListeningPoint> mantained = new LinkedList<>();
		final List<ListeningPoint> expired = new LinkedList<>();
		final List<ListeningPoint> brandNew = new LinkedList<>();
		Map<ListeningPoint, SipStack> listeningPointToStack = new HashMap<>();
		categorizeAddresses(mantained, expired, brandNew, listeningPointToStack, false, localAddresses);
		if (preventActiveUserAgentsRemoval(mantained)) {
			return false;
		} else {
			if (mantained.isEmpty()) {
				logger.error("Exclude addresses: operation invalid as you're not supposed to " +
						"exclude all local addresses at once.");
				return false;
			}
		}
		List<String> unregisteredAddresses = new LinkedList<>();
		for (ListeningPoint listeningPoint : expired) {
			logger.debug("{}:{}/{} registration will be excluded.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
			unregisteredAddresses.add(String.format("%s:%d",
					listeningPoint.getIPAddress(), listeningPoint.getPort()));
		}
		for (ListeningPoint listeningPoint : brandNew) {
			logger.debug("{}:{}/{} registration will be ignored.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
		}
		for (ListeningPoint listeningPoint : mantained) {
			logger.debug("{}:{}/{} registration will be left untouched.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
		}
		try {
			boolean couldDispatchOperation = chooseBestAgentThatIsAvailable()
				.sendUnregisterRequest(new BasicRequestCallback() {

					@Override
					public void onRequestSuccess(String localUser, String localUserDomain,
							Object... unregisteredContacts) {
						performUserAgentsCleanup(expired);
						registerRelatedOperationFinished(localUser, localUserDomain);
						callback.onRequestSuccess(localUser, localUserDomain, unregisteredContacts);
					}

					@Override
					public void onRequestFailed(String localUser, String localUserDomain, String reason) {
						registerRelatedOperationFinished(localUser, localUserDomain);
						callback.onRequestFailed(localUser, localUserDomain, reason);
					}

				}, unregisteredAddresses.toArray(new String[unregisteredAddresses.size()]));
			if (couldDispatchOperation) {
				registerOperationsInProgress.put(RequestMethod.REGISTER, true);
			}
			return couldDispatchOperation;
		} catch (InternalJainSipException internalJainSipError) {
			return false;
		}
	}

	@Override
	public boolean overwriteUserAgents(final BasicRequestCallback callback,
			String... localAddresses) {
		return overwriteUserAgents(callback, DEFAULT_REGISTRATION_LIFESPAN_SECONDS, localAddresses);
	}

	@Override
	public boolean overwriteUserAgents(final BasicRequestCallback callback,
			int expires, String... localAddresses) {
		if (localAddresses.length == 0) {
			logger.error("Overwrite addresses: operation invalid as no local addresses " +
					"were provided.");
			return false;
		}
		if (registerOperationsInProgress.get(RequestMethod.REGISTER)) {
			postponedRegisterOperations.add(new RegisterOperation(OperationMethod.OVERWRITE_USER_AGENTS,
					callback, expires, localAddresses));
			logger.info("Overwrite addresses: operation postponed because another " +
					"related operation is in progress.");
			return true;
		}
		final List<ListeningPoint> expired = new LinkedList<>();
		final List<ListeningPoint> renewed = new LinkedList<>();
		final List<ListeningPoint> brandNew = new LinkedList<>();
		Map<ListeningPoint, SipStack> listeningPointToStack = new HashMap<>();
		categorizeAddresses(expired, renewed, brandNew, listeningPointToStack, true, localAddresses);
		if (preventActiveUserAgentsRemoval(expired)) {
			for (ListeningPoint listeningPoint : brandNew) {
				SipStack stack = listeningPointToStack.get(listeningPoint);
				try {
					stack.deleteListeningPoint(listeningPoint);
				} catch (ObjectInUseException ignore) {
					ignore.printStackTrace();
				}
			}
			return false;
		}
		performUserAgentsCreation(brandNew, listeningPointToStack);
		final List<String> registeredAddresses = new LinkedList<>();
		for (ListeningPoint listeningPoint : renewed) {
			logger.debug("{}:{}/{} registration will be renewed.", listeningPoint.getIPAddress(),
					listeningPoint.getPort(), listeningPoint.getTransport().toUpperCase());
			registeredAddresses.add(String.format("%s:%d",
					listeningPoint.getIPAddress(), listeningPoint.getPort()));
		}
		for (ListeningPoint listeningPoint : brandNew) {
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
		final SipUserAgent chosenUserAgent = chooseBestAgentThatWontBeRemoved(expired);
		boolean couldDispatchOperation = true;
		if (!registeredAddresses.isEmpty()) {
			try {
				couldDispatchOperation = chosenUserAgent.sendRegisterRequest(new BasicRequestCallback() {

					@Override
					public void onRequestSuccess(String localUser, String localUserDomain,
							Object... registeredContacts) {
						if (!unregisteredAddresses.isEmpty()) {
							boolean couldSendRequest;
							try {
								couldSendRequest = chosenUserAgent
									.sendUnregisterRequest(new BasicRequestCallback() {

										@Override
										public void onRequestSuccess(String localUser, String localUserDomain,
												Object... unregisteredContacts) {
											performUserAgentsCleanup(expired);
											registerRelatedOperationFinished(localUser, localUserDomain);
											callback.onRequestSuccess(localUser,
												localUserDomain, unregisteredContacts);
										}

										@Override
										public void onRequestFailed(String localUser, String localUserDomain,
												String reason) {
											registerRelatedOperationFinished(localUser, localUserDomain);
											callback.onRequestFailed(localUser, localUserDomain, reason);
										}

									}, unregisteredAddresses.toArray(new String[unregisteredAddresses.size()]));
							} catch (InternalJainSipException internalJainSipError) {
								couldSendRequest = false;
							}
							if (!couldSendRequest) {
								registerRelatedOperationFinished(localUser, localUserDomain);
								callback.onRequestFailed(localUser, localUserDomain, String.format("Could register " +
									"some contacts (%s) but could not unregister others (%s).", registeredAddresses,
									unregisteredAddresses));
							}
						} else {
							registerRelatedOperationFinished(localUser, localUserDomain);
							callback.onRequestSuccess(localUser, localUserDomain, registeredContacts);
						}
					}

					@Override
					public void onRequestFailed(String localUser, String localUserDomain, String reason) {
						registerRelatedOperationFinished(localUser, localUserDomain);
						callback.onRequestFailed(localUser, localUserDomain, reason);
					}

				}, expires, registeredAddresses.toArray(new String[registeredAddresses.size()]));
			} catch (InternalJainSipException internalJainSipError) {
				couldDispatchOperation = false;
			}
		} else if (!unregisteredAddresses.isEmpty()) {
			try {
				couldDispatchOperation = chosenUserAgent
					.sendUnregisterRequest(new BasicRequestCallback() {

						@Override
						public void onRequestSuccess(String localUser, String localUserDomain,
								Object... registeredContacts) {
							performUserAgentsCleanup(expired);
							registerRelatedOperationFinished(localUser, localUserDomain);
							callback.onRequestSuccess(localUser, localUserDomain, new LinkedList<String>());
						}

						@Override
						public void onRequestFailed(String localUser, String localUserDomain, String reason) {
							registerRelatedOperationFinished(localUser, localUserDomain);
							callback.onRequestFailed(localUser, localUserDomain, reason);
						}

					}, unregisteredAddresses.toArray(new String[unregisteredAddresses.size()]));
			} catch (InternalJainSipException internalJainSipError) {
				couldDispatchOperation = false;
			}
		}
		if (couldDispatchOperation) {
			registerOperationsInProgress.put(RequestMethod.REGISTER, true);
		}
		return couldDispatchOperation;
	}

	private void categorizeAddresses(List<ListeningPoint> ommited, List<ListeningPoint> existing,
			List<ListeningPoint> brandNew, Map<ListeningPoint, SipStack> listeningPointToStack,
			boolean createNewListeningPoints, String... localAddresses) {
		boolean oldAddresses[] = new boolean[localAddresses.length];
		Set<Transport> transports = transportToUserAgents.keySet();
		synchronized (transportToUserAgents) {
			for (Transport transport : transports) {
				Set<SipUserAgent> userAgents = transportToUserAgents.get(transport);
				synchronized (userAgents) {
					Iterator<SipUserAgent> iterator = userAgents.iterator();
					while (iterator.hasNext()) {
						SipUserAgent userAgent = iterator.next();
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
									throw new SipuadaException("Malformed address provided: " +
											localAddress, malformedAddress);
								} catch (NumberFormatException invalidPort) {
									logger.error("Invalid port for address {}: {}.",
											localAddress, localPort);
									throw new SipuadaException("Invalid port provided for address "
											+ localAddress + ": " + localPort, invalidPort);
								}
								if (listeningPoint.getIPAddress().equals(localIp)
										&& listeningPoint.getPort() == localPort
										&& listeningPoint.getTransport().toUpperCase()
											.equals(localTransport)) {
									existing.add(listeningPoint);
									addressRenewed = true;
									oldAddresses[index] = true;
									break;
								}
								index++;
							}
							if (!addressRenewed) {
								ommited.add(listeningPoint);
							}
						}
					}
				}
			}
		}
		if (createNewListeningPoints) {
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
						listeningPointToStack.put(listeningPoint, stack);
						brandNew.add(listeningPoint);
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
					}
				}
				index++;
			}
		}
	}

	private void registerRelatedOperationFinished(String localUser, String localUserDomain) {
		registerOperationsInProgress.put(RequestMethod.REGISTER, false);
		synchronized (postponedRegisterOperations) {
			Iterator<RegisterOperation> iterator = postponedRegisterOperations.iterator();
			while (iterator.hasNext()) {
				RegisterOperation operation = iterator.next();
				iterator.remove();
				boolean couldDispatchOperation = false;
				switch (operation.method) {
					case REGISTER_ADDRESSES:
						couldDispatchOperation = registerAddresses
							(operation.callback, operation.expires);
						break;
					case UNREGISTER_ADDRESSES:
						couldDispatchOperation = unregisterAddresses
							(operation.callback, operation.arguments);
						break;
					case CLEAR_ADDRESSES:
						couldDispatchOperation = clearAddresses
							(operation.callback);
						break;
					case INCLUDE_USER_AGENTS:
						couldDispatchOperation = includeUserAgents
							(operation.callback, operation.expires, operation.arguments);
						break;
					case EXCLUDE_USER_AGENTS:
						couldDispatchOperation = excludeUserAgents
						(operation.callback, operation.arguments);
					break;
					case OVERWRITE_USER_AGENTS:
						couldDispatchOperation = overwriteUserAgents
							(operation.callback, operation.expires, operation.arguments);
						break;
				}
				if (!couldDispatchOperation) {
					operation.callback.onRequestFailed(localUser, localUserDomain,
						"Request could not be sent.");
					continue;
				}
				break;
			}
		}
	}

	private synchronized void performUserAgentsCreation(List<ListeningPoint> brandNewListeningPoints,
			Map<ListeningPoint, SipStack> listeningPointToStack) {
		for (ListeningPoint listeningPoint : brandNewListeningPoints) {
			try {
				SipStack stack = listeningPointToStack.get(listeningPoint);
				SipProvider sipProvider = stack.createSipProvider(listeningPoint);
				stack.start();
				String rawTransport = listeningPoint.getTransport().toUpperCase();
				Transport transport = Transport.UNKNOWN;
				try {
					transport = Transport.valueOf(rawTransport);
				} catch (IllegalArgumentException ignore) {
					ignore.printStackTrace();
				}
				synchronized (transportToUserAgents) {
					if (!transportToUserAgents.containsKey(transport)) {
						transportToUserAgents.put(transport, new HashSet<SipUserAgent>());
					}
				}
				Set<SipUserAgent> userAgents = transportToUserAgents.get(transport);
				SipUserAgent userAgent = new SipUserAgent(stack.getStackName(),
					eventBus, sipProvider, listener, registeredPlugins,
					username, primaryHost, password, listeningPoint.getIPAddress(),
					Integer.toString(listeningPoint.getPort()), rawTransport,
					callIdToActiveUserAgent, activeUserAgentCallIds,
					registerCallIds, registerCSeqs, intolerantModeEnabled);
				userAgents.add(userAgent);
				activeUserAgentCallIds.put(userAgent, Collections
						.synchronizedSet(new HashSet<String>()));
			} catch (ObjectInUseException unexpectedException) {
				logger.error("Unexpected problem: {}.", unexpectedException.getMessage(),
						unexpectedException.getCause());
				throw new SipuadaException("Unexpected problem: "
						+ unexpectedException.getMessage(), unexpectedException);
			} catch (SipException unexpectedException) {
				logger.error("Unexpected problem: {}.", unexpectedException.getMessage(),
						unexpectedException.getCause());
				throw new SipuadaException("Unexpected problem: "
						+ unexpectedException.getMessage(), unexpectedException);
			}
		}
	}

	private boolean preventActiveUserAgentsRemoval(List<ListeningPoint> expiredListeningPoints) {
		Set<Transport> transports = transportToUserAgents.keySet();
		synchronized (transportToUserAgents) {
			Iterator<Transport> transportsIterator = transports.iterator();
			while (transportsIterator.hasNext()) {
				Transport transport = transportsIterator.next();
				Set<SipUserAgent> userAgents = transportToUserAgents.get(transport);
				synchronized (userAgents) {
					Iterator<SipUserAgent> userAgentsIterator = userAgents.iterator();
					while (userAgentsIterator.hasNext()) {
						SipUserAgent userAgent = userAgentsIterator.next();
						for (ListeningPoint expiredListeningPoint : expiredListeningPoints) {
							if (expiredListeningPoint.getIPAddress().equals(userAgent.getLocalIp())
									&& expiredListeningPoint.getPort() == userAgent.getLocalPort()
									&& expiredListeningPoint.getTransport().toUpperCase()
										.equals(userAgent.getTransport())) {
								Set<String> activeCallIds = activeUserAgentCallIds.get(userAgent);
								if (activeCallIds != null && !activeCallIds.isEmpty()) {
									logger.error("UserAgent bound to {}:{} through {}" +
											" cannot be removed as it is currently in use.",
											expiredListeningPoint.getIPAddress(),
											expiredListeningPoint.getPort(),
											expiredListeningPoint.getTransport().toUpperCase());
									return true;
								}
							}
						}
					}
				}
			}
		}
		return false;
	}

	private SipUserAgent chooseBestAgentThatWontBeRemoved(List<ListeningPoint> expiredListeningPoints) {
		return chooseBestAgentThatWontBeRemoved(true, expiredListeningPoints);
	}

	private SipUserAgent chooseBestAgentThatWontBeRemoved(boolean justStartedChoosing,
			List<ListeningPoint> expiredListeningPoints) {
		SipUserAgent originalBestUserAgent = null;
		try{
			originalBestUserAgent = fetchBestAgent(defaultTransport);
		} catch (SipuadaException noUserAgentAvailable) {
			if (justStartedChoosing) {
				throw noUserAgentAvailable;
			}
			return originalBestUserAgent;
		}
		boolean originalUserAgentWillBeRemoved = false;
		for (ListeningPoint listeningPoint : expiredListeningPoints) {
			if (listeningPoint.getIPAddress().equals(originalBestUserAgent.getLocalIp())
					&& listeningPoint.getPort() == originalBestUserAgent.getLocalPort()
					&& listeningPoint.getTransport().toUpperCase()
						.equals(originalBestUserAgent.getTransport())) {
				originalUserAgentWillBeRemoved = true;
			}
		}
		boolean originalUserAgentIsUnavailable = !checkUserAgentAvailability(originalBestUserAgent);
		SipUserAgent nextBestUserAgent = null;
		if (originalUserAgentWillBeRemoved || originalUserAgentIsUnavailable) {
			String rawTransport = originalBestUserAgent.getTransport();
			Transport transport = Transport.UNKNOWN;
			try {
				transport = Transport.valueOf(rawTransport);
			} catch (IllegalArgumentException ignore) {
				ignore.printStackTrace();
			}
			Set<SipUserAgent> userAgents = transportToUserAgents.get(transport);
			userAgents.remove(originalBestUserAgent);
			nextBestUserAgent = chooseBestAgentThatWontBeRemoved(false, expiredListeningPoints);
			userAgents.add(originalBestUserAgent);
			return nextBestUserAgent != null ? nextBestUserAgent : originalBestUserAgent;
		}
		return originalBestUserAgent;
	}

	private synchronized void performUserAgentsCleanup(List<ListeningPoint> expiredListeningPoints) {
		Set<Transport> transports = transportToUserAgents.keySet();
		synchronized (transportToUserAgents) {
			Iterator<Transport> transportsIterator = transports.iterator();
			while (transportsIterator.hasNext()) {
				Transport transport = transportsIterator.next();
				Set<SipUserAgent> userAgents = transportToUserAgents.get(transport);
				synchronized (userAgents) {
					Iterator<SipUserAgent> userAgentsIterator = userAgents.iterator();
					while (userAgentsIterator.hasNext()) {
						SipUserAgent userAgent = userAgentsIterator.next();
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
								} catch (ObjectInUseException ignore) {
									ignore.printStackTrace();
								}
								synchronized (activeUserAgentCallIds) {
									Set<String> activeCallIds = activeUserAgentCallIds.get(userAgent);
									if (activeCallIds != null) {
										synchronized (activeCallIds) {
											Iterator<String> callIdsIterator = activeCallIds.iterator();
											while (callIdsIterator.hasNext()) {
												String callId = callIdsIterator.next();
												callIdToActiveUserAgent.remove(callId);
											}
											activeUserAgentCallIds.remove(userAgent);
										}
									}
								}
								userAgentsIterator.remove();
								if (userAgents.isEmpty()) {
									transportsIterator.remove();
								}
								break;
							}
						}
					}
				}
			}
		}
	}

	@Override
	public String inviteToCall(String remoteUser, String remoteDomain,
			CallInvitationCallback callback) {
		try {
			return chooseBestAgentThatIsAvailable()
					.sendInviteRequest(remoteUser, remoteDomain, callback);
		} catch (InternalJainSipException internalJainSipError) {
			return null;
		}
	}

	private SipUserAgent chooseBestAgentThatIsAvailable() {
		return chooseBestAgentThatIsAvailable(true);
	}

	private SipUserAgent chooseBestAgentThatIsAvailable(boolean justStartedChoosing) {
		SipUserAgent originalBestUserAgent = null;
		try {
			originalBestUserAgent = fetchBestAgent(defaultTransport);
		} catch (SipuadaException noUserAgentAvailable) {
			if (justStartedChoosing) {
				throw noUserAgentAvailable;
			}
			return originalBestUserAgent;
		}
		boolean originalUserAgentIsUnavailable = !checkUserAgentAvailability(originalBestUserAgent);
		SipUserAgent nextBestUserAgent = null;
		if (originalUserAgentIsUnavailable) {
			String rawTransport = originalBestUserAgent.getTransport();
			Transport transport = Transport.UNKNOWN;
			try {
				transport = Transport.valueOf(rawTransport);
			} catch (IllegalArgumentException ignore) {
				ignore.printStackTrace();
			}
			Set<SipUserAgent> userAgents = transportToUserAgents.get(transport);
			userAgents.remove(originalBestUserAgent);
			nextBestUserAgent = chooseBestAgentThatIsAvailable(false);
			userAgents.add(originalBestUserAgent);
			return nextBestUserAgent != null ? nextBestUserAgent : originalBestUserAgent;
		}
		return originalBestUserAgent;
	}

	public boolean checkUserAgentAvailability(SipUserAgent userAgent) {
		String userAgentAddressIp = userAgent.getLocalIp();
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			if (interfaces == null) {
				return false;
			}
			while (interfaces.hasMoreElements()) {
				NetworkInterface networkInterface = interfaces.nextElement();
				if (networkInterface.isUp() && !networkInterface.isLoopback()) {
					List<InterfaceAddress> addresses = networkInterface.getInterfaceAddresses();
					Iterator<InterfaceAddress> iterator = addresses.iterator();
					while (iterator.hasNext()) {
						InterfaceAddress interfaceAddress = iterator.next();
						InetAddress inetAddress = interfaceAddress.getAddress();
						if (inetAddress instanceof Inet4Address) {
							if (inetAddress.getHostAddress().equals(userAgentAddressIp)) {
								return true;
							}
						}
					}
				}
			}
		} catch (SocketException ioException) {}
		return false;
	}

	private SipUserAgent fetchBestAgent(String rawTransport) {
		Transport transport = Transport.UNKNOWN;
		try {
			transport = Transport.valueOf(rawTransport);
		} catch (IllegalArgumentException ignore) {
			ignore.printStackTrace();
		}
		Set<SipUserAgent> userAgentCandidates = transportToUserAgents.get(transport);
		if (userAgentCandidates == null) {
			userAgentCandidates = new HashSet<>();
		}
		else {
			userAgentCandidates = new HashSet<>(userAgentCandidates);
		}
		int randomNumber = 0;
		Iterator<SipUserAgent> iterator = userAgentCandidates.iterator();
		SipUserAgent bestUserAgent = null;
		try {
			bestUserAgent = iterator.next();
			randomNumber = (new Random()).nextInt(userAgentCandidates.size());
		} catch (NoSuchElementException noUserAgentsOfGivenTransport) {
			Set<Transport> transports = transportToUserAgents.keySet();
			synchronized (transportToUserAgents) {
				for (Transport otherTransport : transports) {
					if (otherTransport == transport) {
						continue;
					}
					Set<SipUserAgent> userAgents = transportToUserAgents.get(otherTransport);
					if (!userAgents.isEmpty()) {
						userAgentCandidates.addAll(userAgents);
					}
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
	public boolean cancelCallInvitation(String callId) {
		SipUserAgent userAgent = callIdToActiveUserAgent.get(callId);
		if (userAgent == null) {
			return false;
		}
		try {
			return userAgent.cancelInviteRequest(callId);
		} catch (InternalJainSipException internalJainSipError) {
			return false;
		}
	}

	@Override
	public boolean acceptCallInvitation(String callId) {
		SipUserAgent userAgent = callIdToActiveUserAgent.get(callId);
		if (userAgent == null) {
			return false;
		}
		try {
			return userAgent.answerInviteRequest(callId, true);
		} catch (InternalJainSipException internalJainSipError) {
			return false;
		}
	}

	@Override
	public boolean declineCallInvitation(String callId) {
		SipUserAgent userAgent = callIdToActiveUserAgent.get(callId);
		if (userAgent == null) {
			return false;
		}
		try {
			return userAgent.answerInviteRequest(callId, false);
		} catch (InternalJainSipException internalJainSipError) {
			return false;
		}
	}

	@Override
	public boolean finishCall(String callId) {
		SipUserAgent userAgent = callIdToActiveUserAgent.get(callId);
		if (userAgent == null) {
			return false;
		}
		try {
			return userAgent.finishCall(callId);
		} catch (InternalJainSipException internalJainSipError) {
			return false;
		}
	}

	@Override
	public boolean sendMessage(String remoteUser, String remoteDomain, String content,
			String contentType, BasicRequestCallback callback, String... additionalHeaders) {
		try {
			return chooseBestAgentThatIsAvailable().sendMessageRequest(remoteUser, remoteDomain, content,
				contentType, callback, additionalHeaders);
		} catch (InternalJainSipException internalJainSipError) {
			return false;
		}
	}

	@Override
	public boolean sendMessage(String callId, String content, String contentType,
			BasicRequestCallback callback, String... additionalHeaders) {
		try {
			return chooseBestAgentThatIsAvailable().sendMessageRequest(callId, content, contentType,
				callback, additionalHeaders);
		} catch (InternalJainSipException internalJainSipError) {
			return false;
		}
	}

	@Override
	public boolean registerPlugin(SipuadaPlugin plugin) {
		if (registeredPlugins.containsKey(RequestMethod.INVITE)) {
			return false;
		}
		registeredPlugins.put(RequestMethod.INVITE, plugin);
		return true;
	}

	public void destroySipuada() {
		eventBus.unregister(this);
		Set<Transport> transports = transportToUserAgents.keySet();
		synchronized (transportToUserAgents) {
			for (Transport transport : transports) {
				Set<SipUserAgent> userAgents = transportToUserAgents.get(transport);
				synchronized (userAgents) {
					for (SipUserAgent userAgent : userAgents) {
						destroyUserAgent(userAgent);
					}
				}
			}
			transportToUserAgents.clear();
			callIdToActiveUserAgent.clear();
			activeUserAgentCallIds.clear();
			registerOperationsInProgress.put(RequestMethod.REGISTER, false);
			postponedRegisterOperations.clear();
		}
	}

	private void destroyUserAgent(SipUserAgent userAgent) {
		SipProvider provider = userAgent.getProvider();
		if (provider != null) {
			SipStack stack = provider.getSipStack();
			if (stack != null) {
				for (ListeningPoint listeningPoint : provider.getListeningPoints()) {
					try {
						if (listeningPoint != null) {
							stack.deleteListeningPoint(listeningPoint);
						}
					} catch (ObjectInUseException ignore) {
						ignore.printStackTrace();
					}
				}
				try {
					stack.deleteSipProvider(provider);
				} catch (ObjectInUseException ignore) {
					ignore.printStackTrace();
				}
				stack.stop();
			}
		}
	}

}
