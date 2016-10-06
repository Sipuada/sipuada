package org.github.sipuada;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TooManyListenersException;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.SipuadaApi.BasicRequestCallback;
import org.github.sipuada.SipuadaApi.CallInvitationCallback;
import org.github.sipuada.SipuadaApi.SipuadaListener;
import org.github.sipuada.events.CallInvitationAccepted;
import org.github.sipuada.events.CallInvitationArrived;
import org.github.sipuada.events.CallInvitationCanceled;
import org.github.sipuada.events.CallInvitationDeclined;
import org.github.sipuada.events.CallInvitationFailed;
import org.github.sipuada.events.CallInvitationRinging;
import org.github.sipuada.events.CallInvitationWaiting;
import org.github.sipuada.events.EstablishedCallFailed;
import org.github.sipuada.events.EstablishedCallFinished;
import org.github.sipuada.events.EstablishedCallStarted;
import org.github.sipuada.events.MessageNotSent;
import org.github.sipuada.events.MessageReceived;
import org.github.sipuada.events.MessageSent;
import org.github.sipuada.events.RegistrationFailed;
import org.github.sipuada.events.RegistrationSuccess;
import org.github.sipuada.events.UserAgentNominatedForIncomingRequest;
import org.github.sipuada.plugins.SipuadaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import android.gov.nist.gnjvx.sip.Utils;
import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.DialogTerminatedEvent;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipFactory;
import android.javax.sip.SipListener;
import android.javax.sip.SipProvider;
import android.javax.sip.Timeout;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionTerminatedEvent;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.URI;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;

public class SipUserAgent implements SipListener {

	protected static final RequestMethod ACCEPTED_METHODS[] = {
		RequestMethod.CANCEL,
		RequestMethod.OPTIONS,
		RequestMethod.MESSAGE,
		RequestMethod.INVITE,
		RequestMethod.INFO,
		RequestMethod.ACK,
		RequestMethod.BYE
	};
	protected static final String X_FAILURE_REASON_HEADER = "XFailureReason";

	private final Logger logger = LoggerFactory.getLogger(SipUserAgent.class);

	private final EventBus sipuadaEventBus;
	private final EventBus internalEventBus = new EventBus();
	private final Map<String, Object> eventBusSubscribers = Collections
			.synchronizedMap(new HashMap<String, Object>());
	private final Map<String, String> callIdToEventBusSubscriberId =
			Collections.synchronizedMap(new HashMap<String, String>());
	private final Map<String, List<ClientTransaction>> cancelableInviteOperations =
			Collections.synchronizedMap(new HashMap<String, List<ClientTransaction>>());
	private final Map<String, List<ServerTransaction>> answerableInviteOperations =
			Collections.synchronizedMap(new HashMap<String, List<ServerTransaction>>());
	private final Map<String, List<Dialog>> establishedCalls =
			Collections.synchronizedMap(new HashMap<String, List<Dialog>>());

	private final SipProvider provider;
	private final SipuadaListener listener;
	private SipUserAgentClient uac;
	private SipUserAgentServer uas;

	private final Map<RequestMethod, SipuadaPlugin> registeredPlugins;
	private final String username;
	private final String primaryHost;
	private final String localIp;
	private final int localPort;
	private final String transport;

	private final Map<String, SipUserAgent> callIdToActiveUserAgent;
	private final Map<SipUserAgent, Set<String>> activeUserAgentCallIds;

	private final Map<String, CallIdHeader> registerCallIds;
	private final boolean intolerantModeEnabled;
	private final int maxTolerableTimeout = 10;
	private final int minTolerableTimeout = 2;
	private float currentTolerableTimeout = (maxTolerableTimeout + minTolerableTimeout) / 2;

	public SipUserAgent(EventBus eventBus, SipProvider sipProvider, SipuadaListener sipuadaListener,
			Map<RequestMethod, SipuadaPlugin> plugins, String username, String primaryHost, String password,
			String localIp, String localPort, String transport, Map<String, SipUserAgent> callIdToActiveUserAgent,
			Map<SipUserAgent, Set<String>> activeUserAgentCallIds, Map<String, CallIdHeader> globalRegisterCallIds,
			Map<URI, Long> globalRegisterCSeqs, boolean intolerantModeIsEnabled) {
		sipuadaEventBus = eventBus;
		provider = sipProvider;
		listener = sipuadaListener;
		registerCallIds = globalRegisterCallIds;
		intolerantModeEnabled = intolerantModeIsEnabled;
		internalEventBus.register(this);
		try {
			SipFactory factory = SipFactory.getInstance();
			MessageFactory messenger = factory.createMessageFactory();
			HeaderFactory headerMaker = factory.createHeaderFactory();
			AddressFactory addressMaker = factory.createAddressFactory();
			uac = new SipUserAgentClient(internalEventBus, provider, plugins, messenger, headerMaker, addressMaker,
					globalRegisterCSeqs, username, primaryHost, password, localIp, localPort, transport);
			uas = new SipUserAgentServer(internalEventBus, provider, plugins, messenger, headerMaker, addressMaker,
					username, localIp, localPort, transport);
		} catch (PeerUnavailableException ignore){}
		try {
			provider.addSipListener(this);
		} catch (TooManyListenersException ignore) {}
		registeredPlugins = plugins;
		this.username = username;
		this.primaryHost = primaryHost;
		this.localIp = localIp;
		this.localPort = Integer.parseInt(localPort);
		this.transport = transport;
		this.callIdToActiveUserAgent = callIdToActiveUserAgent;
		this.activeUserAgentCallIds = activeUserAgentCallIds;
		initSipuadaListener();
	}

	protected SipProvider getProvider() {
		return provider;
	}

	protected String getLocalIp() {
		return localIp;
	}

	protected int getLocalPort() {
		return localPort;
	}

	protected String getTransport() {
		return transport;
	}

	@Override
	public void processRequest(RequestEvent requestEvent) {
		Request request = requestEvent.getRequest();
		CallIdHeader callIdHeader = ((CallIdHeader) request.getHeader(CallIdHeader.NAME));
		String callId = callIdHeader.getCallId();
		if (callIdToActiveUserAgent.containsKey(callId) && callIdToActiveUserAgent.get(callId) == this) {
			logger.debug("{}:{}/{}'s UAS will avoid election and process an incoming {} request right away.",
					localIp, localPort, transport, requestEvent.getRequest().getMethod());
			doProcessRequest(requestEvent);
		}
		else {
			logger.debug("{}:{}/{}'s UAS nominates itself to process an incoming {} request...",
					localIp, localPort, transport, requestEvent.getRequest().getMethod());
			sipuadaEventBus.post(new UserAgentNominatedForIncomingRequest(this, callId, requestEvent));
		}
	}

	protected void doProcessRequest(RequestEvent requestEvent) {
		uas.processRequest(requestEvent);
	}

	@Override
	public void processResponse(ResponseEvent responseEvent) {
		ClientTransaction clientTransaction = responseEvent.getClientTransaction();
		if (clientTransaction != null) {
			logger.debug("{}:{}/{}'s UAC about to process an incoming {} response to a {} request...",
					localIp, localPort, transport, responseEvent.getResponse().getStatusCode(),
					clientTransaction.getRequest().getMethod());
		} else {
			logger.debug("{}:{}/{}'s UAC about to process an incoming {} response...",
					localIp, localPort, transport, responseEvent.getResponse().getStatusCode());
		}
		uac.processResponse(responseEvent);
	}

	@Override
	public void processTimeout(TimeoutEvent timeoutOrRetransmissionEvent) {
		if (timeoutOrRetransmissionEvent.getTimeout() == Timeout.TRANSACTION) {
			uac.processTimeout(timeoutOrRetransmissionEvent);
		}
		else if (timeoutOrRetransmissionEvent.getTimeout() == Timeout.RETRANSMIT) {
			uas.processRetransmission(timeoutOrRetransmissionEvent);
		}
	}

	@Override
	public void processIOException(IOExceptionEvent exceptionEvent) {
		uac.processFatalTransportError(exceptionEvent);
	}

	@Override
	public void processTransactionTerminated(
			TransactionTerminatedEvent transactionTerminatedEvent) {
//		logger.warn("<TRANSACTION terminated event>: " + (transactionTerminatedEvent
//				.isServerTransaction() ? transactionTerminatedEvent
//						.getServerTransaction() : transactionTerminatedEvent
//						.getClientTransaction()));
	}

	@Override
	public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
//		logger.warn("<DIALOG terminated event>: " + dialogTerminatedEvent
//				.getDialog());
	}

	private void initSipuadaListener() {
		final String eventBusSubscriberId = Utils.getInstance().generateTag();
		Object eventBusSubscriber = new Object() {

			@Subscribe
			public void onEvent(CallInvitationArrived event) {
				ServerTransaction serverTransaction = event.getServerTransaction();
				final String callId = event.getCallId();
				inviteOperationIsAnswerable(eventBusSubscriberId, callId, serverTransaction);
				Object inviteCancelerEventBusSubscriber = new Object() {

					@Subscribe
					public void onEvent(CallInvitationCanceled event) {
						if (event.getCallId().equals(callId)) {
							wipeAnswerableInviteOperation(callId, eventBusSubscriberId,
								event.shouldTerminateOriginalInvite());
							internalEventBus.unregister(this);
							listener.onCallInvitationCanceled
								(username, primaryHost, event.getReason(), callId);
						}
					}

				};
				internalEventBus.register(inviteCancelerEventBusSubscriber);
				boolean currentlyBusy = listener.onCallInvitationArrived(username, primaryHost,
					callId, event.getRemoteUser(), event.getRemoteDomain());
				if (currentlyBusy) {
					logger.info("Callee is currently busy.");
					answerInviteRequest(callId, false);
				}
			}

			@Subscribe
			public void onEvent(MessageReceived event) {
				final String callId = event.getCallId();
				String[] additionalHeaders = new String[event.getAdditionalHeaders().length];
				for (int i=0; i<additionalHeaders.length; i++) {
					additionalHeaders[i] = event.getAdditionalHeaders()[i].toString().trim();
				}
				listener.onMessageReceived(username, primaryHost, callId,
					event.getRemoteUser(), event.getRemoteDomain(), event.getContent(),
					event.getContentTypeHeader().toString().replace("Content-Type:", "").trim(),
					additionalHeaders);
			}

		};
		internalEventBus.register(eventBusSubscriber);
	}

	private void inviteOperationIsAnswerable(String eventBusSubscriberId,
			String callId, ServerTransaction serverTransaction) {
		callIdToActiveUserAgent.put(callId, this);
		synchronized (activeUserAgentCallIds) {
			if (!activeUserAgentCallIds.containsKey(this)) {
				activeUserAgentCallIds.put(this, Collections
						.synchronizedSet(new HashSet<String>()));
			}
			activeUserAgentCallIds.get(this).add(callId);
		}
		callIdToEventBusSubscriberId.put(callId, eventBusSubscriberId);
		synchronized (answerableInviteOperations) {
			if (!answerableInviteOperations.containsKey(eventBusSubscriberId)) {
				answerableInviteOperations.put(eventBusSubscriberId, Collections
					.synchronizedList(new LinkedList<ServerTransaction>()));
			}
		}
		answerableInviteOperations.get(eventBusSubscriberId).add(serverTransaction);
	}

	private void wipeAnswerableInviteOperation(String callId,
			String eventBusSubscriberId, boolean shouldTerminate) {
		callIdToActiveUserAgent.remove(callId);
		synchronized (activeUserAgentCallIds) {
			Set<String> activeCallIds = activeUserAgentCallIds.get(this);
			if (activeCallIds != null) {
				activeCallIds.remove(callId);
				if (activeCallIds.isEmpty()) {
					activeUserAgentCallIds.remove(this);
				}
			}
		}
		String foundSubscriberId = callIdToEventBusSubscriberId.remove(callId);
		if (foundSubscriberId == null ||
				!foundSubscriberId.equals(eventBusSubscriberId)) {
			//No data relation should have been assigned for this callId
			//in the first place, so we wouldn't have any links to remove.
			//Otherwise state is pretty inconsistent.
			return;
		}
		synchronized (answerableInviteOperations) {
			List<ServerTransaction> operations = answerableInviteOperations
					.get(eventBusSubscriberId);
			if (operations == null) {
				//No data relation should have been assigned for this callId
				//in the first place, so we wouldn't have any links to remove.
				return;
			}
			synchronized (operations) {
				Iterator<ServerTransaction> iterator = operations.iterator();
				while (iterator.hasNext()) {
					ServerTransaction serverTransaction = iterator.next();
					Request request = serverTransaction.getRequest();
					CallIdHeader callIdHeader = (CallIdHeader) request
							.getHeader(CallIdHeader.NAME);
					String transactionCallId = callIdHeader.getCallId();
					if (transactionCallId.equals(callId)) {
						if (shouldTerminate) {
							inviteOperationFinished(eventBusSubscriberId, callId);
							uas.doTerminateCanceledInvite(request, serverTransaction);
						}
						iterator.remove();
						break;
					}
				}
				if (operations.isEmpty()) {
					answerableInviteOperations.remove(eventBusSubscriberId);
				}
			}
		}
	}

	public boolean sendRegisterRequest(BasicRequestCallback callback,
			String... additionalAddresses) {
		return sendRegisterRequest(callback, 3600, additionalAddresses);
	}

	public boolean sendUnregisterRequest(BasicRequestCallback callback,
			String... expiredAddresses) {
		return sendRegisterRequest(callback, 0, expiredAddresses);
	}

	public boolean sendRegisterRequest(final BasicRequestCallback callback,
			int expires, String... addresses) {
		CallIdHeader callIdHeader;
		synchronized (registerCallIds) {
			if (!registerCallIds.containsKey(primaryHost)) {
				registerCallIds.put(primaryHost, provider.getNewCallId());
			}
			callIdHeader = registerCallIds.get(primaryHost);
		}
		final String callId = callIdHeader.getCallId();
		final String eventBusSubscriberId = Utils.getInstance().generateTag();
		Object eventBusSubscriber = new Object() {

			@Subscribe
			public void onEvent(RegistrationSuccess event) {
				if (event.getCallId().equals(callId)) {
					internalEventBus.unregister(eventBusSubscribers.remove(eventBusSubscriberId));
					callback.onRequestSuccess(username, primaryHost, event.getContactBindings());
				}
			}

			@Subscribe
			public void onEvent(RegistrationFailed event) {
				if (event.getCallId().equals(callId)) {
					internalEventBus.unregister(eventBusSubscribers.remove(eventBusSubscriberId));
					callback.onRequestFailed(username, primaryHost, event.getReason());
				}
			}

		};
		internalEventBus.register(eventBusSubscriber);
		eventBusSubscribers.put(eventBusSubscriberId, eventBusSubscriber);
		boolean expectRemoteAnswer = false;
		if (expires == 0) {
			expectRemoteAnswer = uac.sendUnregisterRequest(callIdHeader, addresses);
		}
		else {
			expectRemoteAnswer = uac.sendRegisterRequest(callIdHeader, expires, addresses);
		}
		if (!expectRemoteAnswer) {
			logger.error("REGISTER request not sent.");
			internalEventBus.unregister(eventBusSubscriber);
		} else {
			if (intolerantModeEnabled) {
				final Timer timeoutTimer = new Timer();
				final Object eventBusTimeoutSubscriber = new Object() {

					@Subscribe
					public void onEvent(RegistrationSuccess event) {
						if (event.getCallId().equals(callId)) {
							internalEventBus.unregister(this);
							currentTolerableTimeout = maxTolerableTimeout;
							timeoutTimer.cancel();
						}
					}

				};
				internalEventBus.register(eventBusTimeoutSubscriber);
				final float tolerableTimeoutAtTheTime = currentTolerableTimeout;
				timeoutTimer.schedule(new TimerTask() {

					@Override
					public void run() {
						internalEventBus.unregister(eventBusTimeoutSubscriber);
						internalEventBus.post(new RegistrationFailed(String.format
							(Locale.US, "Sipuada's Intolerant Timeout event fired "
							+ "(waited %.2f seconds).", tolerableTimeoutAtTheTime), callId));
						currentTolerableTimeout = (tolerableTimeoutAtTheTime / 2);
						if (currentTolerableTimeout < minTolerableTimeout) {
							currentTolerableTimeout = minTolerableTimeout;
						}
					}

				}, (long) currentTolerableTimeout * 1000);
			}
		}
		return expectRemoteAnswer;
	}

	public String sendInviteRequest(String remoteUser, String remoteDomain,
			final CallInvitationCallback callback) {
		CallIdHeader callIdHeader = provider.getNewCallId();
		final String callId = callIdHeader.getCallId();
		final String eventBusSubscriberId = Utils.getInstance().generateTag();
		Object eventBusSubscriber = new Object() {

			@Subscribe
			public void onEvent(CallInvitationWaiting event) {
				ClientTransaction transaction = event.getClientTransaction();
				if (event.getCallId().equals(callId)) {
					inviteOperationIsCancelable(eventBusSubscriberId,
							callId, transaction);
					callback.onWaitingForCallInvitationAnswer(username, primaryHost, callId);
				}
			}

			@Subscribe
			public void onEvent(CallInvitationRinging event) {
				ClientTransaction transaction = event.getClientTransaction();
				if (event.getCallId().equals(callId)) {
					inviteOperationIsCancelable(eventBusSubscriberId,
							callId, transaction);
					callback.onCallInvitationRinging(username, primaryHost, callId);
				}
			}

			@Subscribe
			public void onEvent(CallInvitationDeclined event) {
				if (event.getCallId().equals(callId)) {
					inviteOperationFinished(eventBusSubscriberId, callId);
					wipeCancelableInviteOperation(callId, eventBusSubscriberId);
					callback.onCallInvitationDeclined(username, primaryHost, event.getReason());
				}
			}

			@Subscribe
			public void onEvent(CallInvitationAccepted event) {
				Dialog dialog = event.getDialog();
				if (event.getCallId().equals(callId)) {
					inviteOperationFinished(eventBusSubscriberId, callId);
					wipeCancelableInviteOperation(callId, eventBusSubscriberId);
					listener.onCallEstablished(username, primaryHost, callId);
					callEstablished(eventBusSubscriberId, callId, dialog);
				}
			}

			@Subscribe
			public void onEvent(CallInvitationFailed event) {
				if (event.getCallId().equals(callId)) {
					inviteOperationFinished(eventBusSubscriberId, callId);
					wipeCancelableInviteOperation(callId, eventBusSubscriberId);
					listener.onCallInvitationFailed(username, primaryHost, event.getReason(), callId);
				}
			}

		};
		internalEventBus.register(eventBusSubscriber);
		eventBusSubscribers.put(eventBusSubscriberId, eventBusSubscriber);
		boolean expectRemoteAnswer = uac.sendInviteRequest(remoteUser, remoteDomain, callIdHeader);
		if (!expectRemoteAnswer) {
			logger.error("INVITE request not sent.");
			internalEventBus.unregister(eventBusSubscriber);
		}
		return expectRemoteAnswer ? callId : null;
	}

	private void inviteOperationIsCancelable(String eventBusSubscriberId,
			String callId, ClientTransaction clientTransaction) {
		callIdToActiveUserAgent.put(callId, this);
		synchronized (activeUserAgentCallIds) {
			if (!activeUserAgentCallIds.containsKey(this)) {
				activeUserAgentCallIds.put(this, Collections
						.synchronizedSet(new HashSet<String>()));
			}
			activeUserAgentCallIds.get(this).add(callId);
		}
		callIdToEventBusSubscriberId.put(callId, eventBusSubscriberId);
		synchronized (cancelableInviteOperations) {
			if (!cancelableInviteOperations.containsKey(eventBusSubscriberId)) {
				cancelableInviteOperations.put(eventBusSubscriberId, Collections
					.synchronizedList(new LinkedList<ClientTransaction>()));
			}
		}
		cancelableInviteOperations.get(eventBusSubscriberId).add(clientTransaction);
	}

	private void wipeCancelableInviteOperation(String callId,
			String eventBusSubscriberId) {
		if (callId == null) {
			//No data relation should have been assigned for this callId
			//in the first place, so we wouldn't have any links to remove.
			return;
		}
		callIdToActiveUserAgent.remove(callId);
		synchronized (activeUserAgentCallIds) {
			Set<String> activeCallIds = activeUserAgentCallIds.get(this);
			if (activeCallIds != null) {
				activeCallIds.remove(callId);
				if (activeCallIds.isEmpty()) {
					activeUserAgentCallIds.remove(this);
				}
			}
		}
		String foundSubscriberId = callIdToEventBusSubscriberId.remove(callId);
		if (foundSubscriberId == null ||
				!foundSubscriberId.equals(eventBusSubscriberId)) {
			//No data relation should have been assigned for this callId
			//in the first place, so we wouldn't have any links to remove.
			//Otherwise state is pretty inconsistent.
			return;
		}
		synchronized (cancelableInviteOperations) {
			List<ClientTransaction> operations = cancelableInviteOperations
					.get(eventBusSubscriberId);
			if (operations == null) {
				//No data relation should have been assigned for this callId
				//in the first place, so we wouldn't have any links to remove.
				return;
			}
			synchronized (operations) {
				Iterator<ClientTransaction> iterator = operations.iterator();
				while (iterator.hasNext()) {
					ClientTransaction clientTransaction = iterator.next();
					Request request = clientTransaction.getRequest();
					CallIdHeader callIdHeader = (CallIdHeader) request
							.getHeader(CallIdHeader.NAME);
					String transactionCallId = callIdHeader.getCallId();
					if (transactionCallId.equals(callId)) {
						iterator.remove();
						break;
					}
				}
				if (operations.isEmpty()) {
					cancelableInviteOperations.remove(eventBusSubscriberId);
				}
			}
		}
	}

	private void inviteOperationFinished(String eventBusSubscriberId, String callId) {
		Object eventBusSubscriber = eventBusSubscribers.remove(eventBusSubscriberId);
		if (eventBusSubscriber != null) {
			internalEventBus.unregister(eventBusSubscriber);
		}
	}

	public boolean cancelInviteRequest(final String callId) {
		final String eventBusSubscriberId = callIdToEventBusSubscriberId.get(callId);
		if (eventBusSubscriberId == null) {
			logger.error("Cannot cancel invitation.\nINVITE request with callId " +
					"'{}' not found.", callId);
			return false;
		}
		synchronized (cancelableInviteOperations) {
			List<ClientTransaction> operations = cancelableInviteOperations
					.get(eventBusSubscriberId);
			if (operations == null) {
				logger.error("Cannot cancel invitation.\nINVITE request with callId " +
						"'{}' not found.", callId);
				return false;
			}
			synchronized (operations) {
				Iterator<ClientTransaction> iterator = operations.iterator();
				try {
					while (iterator.hasNext()) {
						ClientTransaction clientTransaction = iterator.next();
						Request request = clientTransaction.getRequest();
						CallIdHeader callIdHeader = (CallIdHeader) request
								.getHeader(CallIdHeader.NAME);
						String transactionCallId = callIdHeader.getCallId();
						if (transactionCallId.equals(callId)) {
							iterator.remove();
							Object eventBusSubscriber = new Object() {

								@Subscribe
								public void onEvent(CallInvitationCanceled event) {
									if (event.getCallId().equals(callId)) {
										inviteOperationFinished(eventBusSubscriberId, callId);
										internalEventBus.unregister(this);
										listener.onCallInvitationCanceled(username, primaryHost,
											event.getReason(), callId);
									}
								}

								@Subscribe
								public void onEvent(CallInvitationFailed event) {
									if (event.getCallId().equals(callId)) {
										internalEventBus.unregister(this);
										listener.onCallInvitationFailed(username, primaryHost,
											event.getReason(), callId);
									}
								}

							};
							internalEventBus.register(eventBusSubscriber);
							return uac.sendCancelRequest(clientTransaction);
						}
					}
				} finally {
					if (operations.isEmpty()) {
						cancelableInviteOperations.remove(eventBusSubscriberId);
					}
				}
			}
		}
		logger.error("Cannot cancel invitation.\nINVITE request with callId " +
				"'{}' not found.", callId);
		return false;
	}

	public synchronized boolean answerInviteRequest(final String callId,
			final boolean acceptCallInvitation) {
		final String eventBusSubscriberId = callIdToEventBusSubscriberId.get(callId);
		if (eventBusSubscriberId == null) {
			logger.error("Cannot {} invitation.\nINVITE request with callId '{}' " +
					"not found.", acceptCallInvitation ? "accept" : "decline", callId);
			return false;
		}
		synchronized (answerableInviteOperations) {
			List<ServerTransaction> operations = answerableInviteOperations.get(eventBusSubscriberId);
			if (operations == null) {
				logger.error("Cannot {} invitation.\nINVITE request with callId '{}' " +
						"not found.", acceptCallInvitation ? "accept" : "decline", callId);
				return false;
			}
			synchronized (operations) {
				Iterator<ServerTransaction> iterator = operations.iterator();
				try {
					while (iterator.hasNext()) {
						ServerTransaction serverTransaction = iterator.next();
						Request request = serverTransaction.getRequest();
						CallIdHeader callIdHeader = (CallIdHeader) request
								.getHeader(CallIdHeader.NAME);
						String transactionCallId = callIdHeader.getCallId();
						if (transactionCallId.equals(callId)) {
							iterator.remove();
							Object eventBusSubscriber = new Object() {

								@Subscribe
								public void onEvent(EstablishedCallStarted event) {
									if (acceptCallInvitation &&
											event.getCallId().equals(callId)) {
										internalEventBus.unregister(this);
										listener.onCallEstablished(username, primaryHost, callId);
										callEstablished(eventBusSubscriberId, callId,
											event.getDialog());
									}
								}

								@Subscribe
								public void onEvent(CallInvitationFailed event) {
									if (event.getCallId().equals(callId)) {
										internalEventBus.unregister(this);
										listener.onCallInvitationFailed(username, primaryHost,
											event.getReason(), callId);
									}
								}

							};
							internalEventBus.register(eventBusSubscriber);
							RequestMethod method = RequestMethod.UNKNOWN;
							try {
								method = RequestMethod.valueOf(request.getMethod());
							} catch (IllegalArgumentException ignore) {};
							if (acceptCallInvitation) {
								return uas.sendAcceptResponse(method, request, serverTransaction);
							}
							else {
								return uas.sendRejectResponse(method, request, serverTransaction);
							}
						}
					}
				} finally {
					if (operations.isEmpty()) {
						answerableInviteOperations.remove(eventBusSubscriberId);
					}
				}
			}
		}
		logger.error("Cannot {} invitation.\nINVITE request with callId '{}' " +
				"not found.", acceptCallInvitation ? "accept" : "decline", callId);
		return false;
	}

	private void callEstablished(final String eventBusSubscriberId,
			final String callId, Dialog dialog) {
		callIdToActiveUserAgent.put(callId, this);
		synchronized (activeUserAgentCallIds) {
			if (!activeUserAgentCallIds.containsKey(this)) {
				activeUserAgentCallIds.put(this, Collections
						.synchronizedSet(new HashSet<String>()));
			}
			activeUserAgentCallIds.get(this).add(callId);
		}
		callIdToEventBusSubscriberId.put(callId, eventBusSubscriberId);
		synchronized (establishedCalls) {
			if (!establishedCalls.containsKey(eventBusSubscriberId)) {
				establishedCalls.put(eventBusSubscriberId, Collections
						.synchronizedList(new LinkedList<Dialog>()));
			}
		}
		establishedCalls.get(eventBusSubscriberId).add(dialog);
		final SipuadaPlugin sessionPlugin = registeredPlugins.get(RequestMethod.INVITE);
		Object eventBusSubscriber = new Object() {

			@Subscribe
			public void onEvent(EstablishedCallFailed event) {
				if (event.getCallId().equals(callId)) {
					wipeEstablishedCall(callId, eventBusSubscriberId);
					internalEventBus.unregister(this);
					if (sessionPlugin != null) {
						try {
							boolean sessionProperlyTerminated = sessionPlugin
									.performSessionTermination(callId);
							if (!sessionProperlyTerminated) {
								logger.error("Plug-in signaled session termination failure " +
										"in context of call {}.", callId);
							}
						} catch (Throwable unexpectedException) {
							logger.error("Bad plug-in crashed while trying " +
									"to perform session termination in context of call {}.",
									callId, unexpectedException);
						}
					}
					listener.onCallFailure(username, primaryHost, event.getReason(), callId);
				}
			}

			@Subscribe
			public void onEvent(EstablishedCallFinished event) {
				if (event.getCallId().equals(callId)) {
					wipeEstablishedCall(callId, eventBusSubscriberId);
					internalEventBus.unregister(this);
					if (sessionPlugin != null) {
						try {
							boolean sessionProperlyTerminated = sessionPlugin
									.performSessionTermination(callId);
							if (!sessionProperlyTerminated) {
								logger.error("Plug-in signaled session termination failure " +
										"in context of call {}.", callId);
							}
						} catch (Throwable unexpectedException) {
							logger.error("Bad plug-in crashed while trying " +
									"to perform session termination in context of call {}.",
									callId, unexpectedException);
						}
					}
					listener.onCallFinished(username, primaryHost, callId);
				}
			}

		};
		internalEventBus.register(eventBusSubscriber);
		if (sessionPlugin != null) {
			try {
				boolean sessionProperlySetup = sessionPlugin.performSessionSetup(callId, this);
				if (!sessionProperlySetup) {
					String error = "Plug-in signaled session setup failure in context of call";
					logger.error(String.format("%s {}.", error), callId);
					listener.onCallFailure(username, primaryHost,
						String.format("%s %s.", error, callId), callId);
				}
			} catch (Throwable unexpectedException) {
				String error = "Bad plug-in crashed while trying to perform" +
						" session setup in context of call";
				logger.error(String.format("%s {}.", error), callId, unexpectedException);
				listener.onCallFailure(username, primaryHost,
					String.format("%s %s.", error, callId), callId);
			}
		}
	}

	public void wipeEstablishedCall(String callId,
			String eventBusSubscriberId) {
		callIdToActiveUserAgent.remove(callId);
		synchronized (activeUserAgentCallIds) {
			Set<String> activeCallIds = activeUserAgentCallIds.get(this);
			if (activeCallIds != null) {
				activeCallIds.remove(callId);
				if (activeCallIds.isEmpty()) {
					activeUserAgentCallIds.remove(this);
				}
			}
		}
		String foundSubscriberId = callIdToEventBusSubscriberId.remove(callId);
		if (foundSubscriberId == null ||
				!foundSubscriberId.equals(eventBusSubscriberId)) {
			//No data relation should have been assigned for this callId
			//in the first place, so we wouldn't have any links to remove.
			//Otherwise state is pretty inconsistent.
			return;
		}
		synchronized (establishedCalls) {
			List<Dialog> calls = establishedCalls.get(eventBusSubscriberId);
			if (calls == null) {
				//No data relation should have been assigned for this callId
				//in the first place, so we wouldn't have any links to remove.
				return;
			}
			synchronized (calls) {
				Iterator<Dialog> iterator = calls.iterator();
				try {
					while (iterator.hasNext()) {
						Dialog dialog = iterator.next();
						if (dialog.getCallId().getCallId().equals(callId)) {
							iterator.remove();
							break;
						}
					}
				} finally {
					if (calls.isEmpty()) {
						establishedCalls.remove(eventBusSubscriberId);
					}
				}
			}
		}
	}

	public boolean finishCall(String callId) {
		String eventBusSubscriberId = callIdToEventBusSubscriberId.get(callId);
		if (eventBusSubscriberId == null) {
			logger.error("Cannot finish call.\nEstablished call with callId " +
					"'{}' not found.", callId);
			return false;
		}
		synchronized (establishedCalls) {
			List<Dialog> calls = establishedCalls.get(eventBusSubscriberId);
			if (calls == null) {
				logger.error("Cannot finish call.\nEstablished call with callId " +
						"'{}' not found.", callId);
				return false;
			}
			synchronized (calls) {
				Iterator<Dialog> iterator = calls.iterator();
				try {
					while (iterator.hasNext()) {
						Dialog dialog = iterator.next();
						if (dialog.getCallId().getCallId().equals(callId)) {
							iterator.remove();
							return uac.sendByeRequest(dialog);
						}
					}
				} finally {
					if (calls.isEmpty()) {
						establishedCalls.remove(eventBusSubscriberId);
					}
				}
			}
		}
		logger.error("Cannot finish call.\nEstablished call with callId " +
				"'{}' not found.", callId);
		return false;
	}

	public boolean sendMessageRequest(String remoteUser, String remoteDomain, String content,
			String contentType, final BasicRequestCallback callback, String... additionalHeaders) {
		CallIdHeader callIdHeader = provider.getNewCallId();
		final String callId = callIdHeader.getCallId();
		final String eventBusSubscriberId = Utils.getInstance().generateTag();
		Object eventBusSubscriber = new Object() {

			@Subscribe
			public void onEvent(MessageSent event) {
				if (event.getCallId().equals(callId)) {
					callback.onRequestSuccess(username, primaryHost);
				}
			}

			@Subscribe
			public void onEvent(MessageNotSent event) {
				if (event.getCallId().equals(callId)) {
					callback.onRequestFailed(username, primaryHost, event.getReason());
				}
			}

		};
		internalEventBus.register(eventBusSubscriber);
		eventBusSubscribers.put(eventBusSubscriberId, eventBusSubscriber);
		boolean expectRemoteAnswer = uac.sendMessageRequest(remoteUser, remoteDomain,
			callIdHeader, content, contentType, additionalHeaders);
		if (!expectRemoteAnswer) {
			internalEventBus.unregister(eventBusSubscriber);
		}
		return true;
	}

	public boolean sendMessageRequest(final String callId, String content, String contentType,
			final BasicRequestCallback callback, String... additionalHeaders) {
		String eventBusSubscriberId = callIdToEventBusSubscriberId.get(callId);
		if (eventBusSubscriberId == null) {
			logger.error("Cannot send message.\nEstablished call with callId " + "'{}' not found.", callId);
			return false;
		}
		final String eventBusSubscriberInfoId = Utils.getInstance().generateTag();
		Object eventBusInfoSubscriber = new Object() {

			@Subscribe
			public void onEvent(MessageSent event) {
				if (event.getCallId().equals(callId)) {
					callback.onRequestSuccess(username, primaryHost);
				}
			}

			@Subscribe
			public void onEvent(MessageNotSent event) {
				if (event.getCallId().equals(callId)) {
					callback.onRequestFailed(username, primaryHost, event.getReason());
				}
			}

		};
		internalEventBus.register(eventBusInfoSubscriber);
		eventBusSubscribers.put(eventBusSubscriberInfoId, eventBusInfoSubscriber);
		synchronized (establishedCalls) {
			List<Dialog> calls = establishedCalls.get(eventBusSubscriberId);
			if (calls == null) {
				logger.error("Cannot send message.\nEstablished call with callId " + "'{}' not found.", callId);
				return false;
			}
			synchronized (calls) {
				Iterator<Dialog> iterator = calls.iterator();
				while (iterator.hasNext()) {
					Dialog dialog = iterator.next();
					if (dialog.getCallId().getCallId().equals(callId)) {
						return uac.sendMessageRequest(dialog, content, contentType, additionalHeaders);
					}
				}
			}
		}
		logger.error("Cannot send message.\nEstablished call with callId " + "'{}' not found.", callId);
		return false;
	}

	@Subscribe
	public void onEvent(DeadEvent deadEvent) {
		System.out.println("Dead event: " + deadEvent.getEvent().getClass());
	}

}
