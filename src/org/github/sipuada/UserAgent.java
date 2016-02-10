package org.github.sipuada;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TooManyListenersException;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.SipuadaApi.CallInvitationCallback;
import org.github.sipuada.SipuadaApi.RegistrationCallback;
import org.github.sipuada.SipuadaApi.SipuadaCallback;
import org.github.sipuada.SipuadaApi.SipuadaListener;
import org.github.sipuada.events.CallInvitationAccepted;
import org.github.sipuada.events.CallInvitationArrived;
import org.github.sipuada.events.CallInvitationCanceled;
import org.github.sipuada.events.CallInvitationDeclined;
import org.github.sipuada.events.CallInvitationFailed;
import org.github.sipuada.events.CallInvitationRinging;
import org.github.sipuada.events.CallInvitationWaiting;
import org.github.sipuada.events.EstablishedCallFinished;
import org.github.sipuada.events.EstablishedCallStarted;
import org.github.sipuada.events.RegistrationFailed;
import org.github.sipuada.events.RegistrationSuccess;
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
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ObjectInUseException;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipFactory;
import android.javax.sip.SipListener;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.Timeout;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionTerminatedEvent;
import android.javax.sip.TransportNotSupportedException;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.message.MessageFactory;

public class UserAgent implements SipListener {                                           

	private final Logger logger = LoggerFactory.getLogger(UserAgent.class);
	private final int MIN_PORT = 5000;
	private final int MAX_PORT = 50600;

	private class Operation {

		public final SipuadaCallback callback;
		public final String[] arguments;
		
		public Operation(SipuadaCallback callback, String... arguments) {
			this.callback = callback;
			this.arguments = arguments;
		}

	}

	private final EventBus eventBus = new EventBus();
	private final Map<String, Object> eventBusSubscribers = Collections
			.synchronizedMap(new HashMap<String, Object>());
	private final Map<RequestMethod, Boolean> operationsInProgress = Collections
			.synchronizedMap(new HashMap<RequestMethod, Boolean>());
	{
		for (RequestMethod method : RequestMethod.values()) {
			operationsInProgress.put(method, false);
		}
	}
	private final List<Operation> postponedOperations = Collections
			.synchronizedList(new LinkedList<Operation>());
	private final Map<String, String> callIdToEventBusSubscriberId =
			Collections.synchronizedMap(new HashMap<String, String>());
	private final Map<String, List<ClientTransaction>> cancelableInviteOperations =
			Collections.synchronizedMap(new HashMap<String, List<ClientTransaction>>());
	private final Map<String, List<ServerTransaction>> answerableInviteOperations =
			Collections.synchronizedMap(new HashMap<String, List<ServerTransaction>>());
	private final Map<String, List<Dialog>> establishedCalls =
			Collections.synchronizedMap(new HashMap<String, List<Dialog>>());

	private final SipuadaListener listener;
	private SipProvider provider;
	private UserAgentClient uac;
	private UserAgentServer uas;

	public UserAgent(String username, String domain, String password, String localIp,
			int localPort, String transport, SipuadaListener sipuadaListener) {
		listener = sipuadaListener;
		eventBus.register(this);
		MessageFactory messenger;
		HeaderFactory headerMaker;
		AddressFactory addressMaker;
		Properties properties = new Properties();
		properties.setProperty("android.javax.sip.STACK_NAME", "UserAgent");
		SipStack stack;
		try {
			SipFactory factory = SipFactory.getInstance();
			stack = factory.createSipStack(properties);
			messenger = factory.createMessageFactory();
			headerMaker = factory.createHeaderFactory();
			addressMaker = factory.createAddressFactory();
			ListeningPoint listeningPoint;
			boolean listeningPointBound = false;
			while (!listeningPointBound) {
				try {
					listeningPoint = stack
							.createListeningPoint(localIp, localPort, transport);
					listeningPointBound = true;
					try {
						provider = stack.createSipProvider(listeningPoint);
						uac = new UserAgentClient(eventBus, provider, messenger,
								headerMaker, addressMaker,
								username, domain, password,
								localIp, Integer.toString(localPort), transport);
						uas = new UserAgentServer(provider, messenger, headerMaker);
						try {
							provider.addSipListener(this);
						} catch (TooManyListenersException ignore) {}
						initSipuadaListener(listener);
					} catch (ObjectInUseException e) {
						e.printStackTrace();
					}
				} catch (TransportNotSupportedException ignore) {
				} catch (InvalidArgumentException portUsed) {
					localPort = (int) ((MAX_PORT - MIN_PORT) * Math.random()) + MIN_PORT;
				}
			}
		} catch (PeerUnavailableException ignore) {}
	}

	@Override
	public void processRequest(RequestEvent requestEvent) {
		uas.processRequest(requestEvent);
	}

	@Override
	public void processResponse(ResponseEvent responseEvent) {
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
		
	}

	@Override
	public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
		
	}

	public void initSipuadaListener(final SipuadaListener listener) {
		final String eventBusSubscriberId =
				Utils.getInstance().generateTag();
		Object eventBusSubscriber = new Object() {

			@Subscribe
			public void onEvent(CallInvitationArrived event) {
				ServerTransaction transaction = event.getServerTransaction();
				final String callId = event.getCallId();
				inviteOperationIsAnswerable(eventBusSubscriberId,
						callId, transaction);
				Object inviteCancelerEventBusSubscriber = new Object() {
					
					@Subscribe
					public void onEvent(CallInvitationCanceled event) {
						if (event.getCallId().equals(callId)) {
							wipeAnswerableInviteOperation(callId,
									eventBusSubscriberId);
							eventBus.unregister(this);
							listener.onCallInvitationCanceled(callId);
						}
					}

				};
				eventBus.register(inviteCancelerEventBusSubscriber);
				boolean currentlyBusy = listener.onCallInvitationArrived(callId);
				if (currentlyBusy) {
					eventBus.unregister(inviteCancelerEventBusSubscriber);
					answerInviteRequest(callId, false);
				}
			}

		};
		eventBus.register(eventBusSubscriber);
	}

	private synchronized void inviteOperationIsAnswerable(String eventBusSubscriberId,
			String callId, ServerTransaction serverTransaction) {
		callIdToEventBusSubscriberId.put(callId, eventBusSubscriberId);
		if (!answerableInviteOperations.containsKey(eventBusSubscriberId)) {
			answerableInviteOperations.put(eventBusSubscriberId, Collections
				.synchronizedList(new LinkedList<ServerTransaction>()));
		}
		answerableInviteOperations.get(eventBusSubscriberId).add(serverTransaction);
	}

	private synchronized void wipeAnswerableInviteOperation(String callId,
			String eventBusSubscriberId) {
		String foundSubscriberId = callIdToEventBusSubscriberId.remove(callId);
		if (foundSubscriberId == null ||
				!foundSubscriberId.equals(eventBusSubscriberId)) {
			//No data relation should have been assigned for this callId
			//in the first place, so we wouldn't have any links to remove.
			//Otherwise state is pretty inconsistent.
			return;
		}
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
				if (serverTransaction.getDialog().getCallId()
						.getCallId().equals(callId)) {
					iterator.remove();
					break;
				}
			}
			if (operations.isEmpty()) {
				answerableInviteOperations.remove(eventBusSubscriberId);
			}
		}
	}

	public synchronized boolean sendRegisterRequest(final RegistrationCallback callback) {
		if (operationsInProgress.get(RequestMethod.REGISTER)) {
			postponedOperations.add(new Operation(callback));
			logger.info("REGISTER request postponed because another is in progress.");
			return true;
		}
		final String eventBusSubscriberId = Utils.getInstance().generateTag();
		Object eventBusSubscriber = new Object() {

			@Subscribe
			public void onEvent(RegistrationSuccess event) {
				registerOperationFinished(eventBusSubscriberId);
				callback.onRegistrationSuccess(event.getContactBindings());
			}

			@Subscribe
			public void onEvent(RegistrationFailed event) {
				registerOperationFinished(eventBusSubscriberId);
				callback.onRegistrationFailed(event.getReason());
			}

		};
		eventBus.register(eventBusSubscriber);
		eventBusSubscribers.put(eventBusSubscriberId, eventBusSubscriber);
		boolean expectRemoteAnswer = uac.sendRegisterRequest();
		if (expectRemoteAnswer) {
			operationsInProgress.put(RequestMethod.REGISTER, true);
		}
		else {
			logger.error("REGISTER request not sent.");
			eventBus.unregister(eventBusSubscriber);
		}
		return expectRemoteAnswer;
	}

	private synchronized void registerOperationFinished(String eventBusSubscriberId) {
		eventBus.unregister(eventBusSubscribers.remove(eventBusSubscriberId));
		operationsInProgress.put(RequestMethod.REGISTER, false);
		synchronized (postponedOperations) {
			Iterator<Operation> iterator = postponedOperations.iterator();
			while (iterator.hasNext()) {
				Operation operation = iterator.next();
				if (operation.callback instanceof RegistrationCallback) {
					sendRegisterRequest((RegistrationCallback) operation.callback);
					iterator.remove();
					break;
				}
			}
		}
	}

	public boolean sendInviteRequest(String remoteUser, String remoteDomain,
			final CallInvitationCallback callback) {
		if (operationsInProgress.get(RequestMethod.INVITE)) {
			postponedOperations.add(new Operation(callback, remoteUser, remoteDomain));
			logger.info("INVITE request postponed because another is in progress.");
			return true;
		}
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
					callback.onWaitingForCallInvitationAnswer(callId);
				}
			}

			@Subscribe
			public void onEvent(CallInvitationRinging event) {
				ClientTransaction transaction = event.getClientTransaction();
				if (event.getCallId().equals(callId)) {
					inviteOperationIsCancelable(eventBusSubscriberId,
							callId, transaction);
					callback.onCallInvitationRinging(callId);
				}
			}

			@Subscribe
			public void onEvent(CallInvitationDeclined event) {
				if (event.getCallId().equals(callId)) {
					inviteOperationFinished(eventBusSubscriberId, callId);
					wipeCancelableInviteOperation(callId, eventBusSubscriberId);
					callback.onCallInvitationDeclined(event.getReason());
					scheduleNextInviteRequest();
				}
			}

			@Subscribe
			public void onEvent(CallInvitationAccepted event) {
				Dialog dialog = event.getDialog();
				if (event.getCallId().equals(callId)) {
					inviteOperationFinished(eventBusSubscriberId, callId);
					wipeCancelableInviteOperation(callId, eventBusSubscriberId);
					callEstablished(eventBusSubscriberId, callId, dialog);
					listener.onCallEstablished(callId);
					scheduleNextInviteRequest();
				}
			}

			@Subscribe
			public void onEvent(CallInvitationFailed event) {
				if (event.getCallId().equals(callId)) {
					inviteOperationFinished(eventBusSubscriberId, callId);
					wipeCancelableInviteOperation(callId, eventBusSubscriberId);
					listener.onCallInvitationFailed(event.getReason(), callId);
					scheduleNextInviteRequest();
				}
			}

		};
		eventBus.register(eventBusSubscriber);
		eventBusSubscribers.put(eventBusSubscriberId, eventBusSubscriber);
		boolean expectRemoteAnswer = uac.sendInviteRequest(remoteUser, remoteDomain,
				callIdHeader);
		if (expectRemoteAnswer) {
			operationsInProgress.put(RequestMethod.INVITE, true);
		}
		else {
			logger.error("INVITE request not sent.");
			eventBus.unregister(eventBusSubscriber);
		}
		return expectRemoteAnswer;
	}

	private synchronized void inviteOperationIsCancelable(String eventBusSubscriberId,
			String callId, ClientTransaction clientTransaction) {
		callIdToEventBusSubscriberId.put(callId, eventBusSubscriberId);
		if (!cancelableInviteOperations.containsKey(eventBusSubscriberId)) {
			cancelableInviteOperations.put(eventBusSubscriberId, Collections
				.synchronizedList(new LinkedList<ClientTransaction>()));
		}
		cancelableInviteOperations.get(eventBusSubscriberId).add(clientTransaction);
	}

	private synchronized void wipeCancelableInviteOperation(String callId,
			String eventBusSubscriberId) {
		if (callId == null) {
			//No data relation should have been assigned for this callId
			//in the first place, so we wouldn't have any links to remove.
			return;
		}
		String foundSubscriberId = callIdToEventBusSubscriberId.remove(callId);
		if (foundSubscriberId == null ||
				!foundSubscriberId.equals(eventBusSubscriberId)) {
			//No data relation should have been assigned for this callId
			//in the first place, so we wouldn't have any links to remove.
			//Otherwise state is pretty inconsistent.
			return;
		}
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
				if (clientTransaction.getDialog().getCallId()
						.getCallId().equals(callId)) {
					iterator.remove();
					break;
				}
			}
			if (operations.isEmpty()) {
				cancelableInviteOperations.remove(eventBusSubscriberId);
			}
		}
	}

	private synchronized void inviteOperationFinished(String eventBusSubscriberId,
			String callId) {
		eventBus.unregister(eventBusSubscribers.remove(eventBusSubscriberId));
		operationsInProgress.put(RequestMethod.INVITE, false);
	}
	
	private void scheduleNextInviteRequest() {
		synchronized (postponedOperations) {
			Iterator<Operation> iterator = postponedOperations.iterator();
			while (iterator.hasNext()) {
				Operation operation = iterator.next();
				if (operation.callback instanceof CallInvitationCallback) {
					String[] arguments = operation.arguments;
					sendInviteRequest(arguments[0], arguments[1],
							(CallInvitationCallback) operation.callback);
					iterator.remove();
					break;
				}
			}
		}
	}

	public synchronized boolean cancelInviteRequest(final String callId) {
		final String eventBusSubscriberId = callIdToEventBusSubscriberId.get(callId);
		if (eventBusSubscriberId == null) {
			logger.error("Cannot cancel invitation.\nINVITE request with callId " +
					"'{}' not found.", callId);
			return false;
		}
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
					Dialog dialog = clientTransaction.getDialog();
					if (dialog.getCallId().getCallId().equals(callId)) {
						iterator.remove();
						Object eventBusSubscriber = new Object() {

							@Subscribe
							public void onEvent(CallInvitationCanceled event) {
								if (event.getCallId().equals(callId)) {
									eventBus.unregister(this);
									listener.onCallInvitationCanceled(callId);
								}
							}

							@Subscribe
							public void onEvent(CallInvitationFailed event) {
								if (event.getCallId().equals(callId)) {
									eventBus.unregister(this);
									listener.onCallInvitationFailed(event.getReason(),
											callId);
								}
							}

						};
						eventBus.register(eventBusSubscriber);
						return uac.sendCancelRequest(clientTransaction);
					}
				}
			} finally {
				if (operations.isEmpty()) {
					cancelableInviteOperations.remove(eventBusSubscriberId);
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
		List<ServerTransaction> operations = answerableInviteOperations
				.get(eventBusSubscriberId);
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
					final Dialog dialog = serverTransaction.getDialog();
					if (dialog.getCallId().getCallId().equals(callId)) {
						iterator.remove();
						Object eventBusSubscriber = new Object() {

							@Subscribe
							public void onEvent(EstablishedCallStarted event) {
								if (event.getCallId().equals(callId)) {
									callEstablished(eventBusSubscriberId, callId, dialog);
									eventBus.unregister(this);
									listener.onCallEstablished(callId);
								}
							}

							@Subscribe
							public void onEvent(CallInvitationFailed event) {
								if (event.getCallId().equals(callId)) {
									eventBus.unregister(this);
									listener.onCallInvitationFailed(event.getReason(),
											callId);
								}
							}

						};
						eventBus.register(eventBusSubscriber);
						if (acceptCallInvitation) {
							boolean okSent = true;//TODO uas.sendResponse() to pass
							//a OK response back to ServerTransaction associated
							//with this event.
							return okSent;
						}
						else {
							boolean busySent = true;//TODO uas.sendResponse() to pass
							//a BUSY_HERE response back to ServerTransaction
							//associated with this event.
							if (busySent) {
							}
							return busySent;
						}
					}
				}
			} finally {
				if (operations.isEmpty()) {
					answerableInviteOperations.remove(eventBusSubscriberId);
				}
			}
		}
		logger.error("Cannot {} invitation.\nINVITE request with callId '{}' " +
				"not found.", acceptCallInvitation ? "accept" : "decline", callId);
		return false;
	}

	private synchronized void callEstablished(final String eventBusSubscriberId,
			final String callId, Dialog dialog) {
		callIdToEventBusSubscriberId.put(callId, eventBusSubscriberId);
		if (!establishedCalls.containsKey(eventBusSubscriberId)) {
			establishedCalls.put(eventBusSubscriberId, Collections
					.synchronizedList(new LinkedList<Dialog>()));
		}
		establishedCalls.get(eventBusSubscriberId).add(dialog);
		Object eventBusSubscriber = new Object() {
			
			@Subscribe
			public void onEvent(EstablishedCallFinished event) {
				if (event.getCallId().equals(callId)) {
					wipeEstablishedCall(callId, eventBusSubscriberId);
					eventBus.unregister(this);
					listener.onCallFinished(callId);
				}
			}

		};
		eventBus.register(eventBusSubscriber);
	}

	public synchronized void wipeEstablishedCall(String callId,
			String eventBusSubscriberId) {
		String foundSubscriberId = callIdToEventBusSubscriberId.remove(callId);
		if (foundSubscriberId == null ||
				!foundSubscriberId.equals(eventBusSubscriberId)) {
			//No data relation should have been assigned for this callId
			//in the first place, so we wouldn't have any links to remove.
			//Otherwise state is pretty inconsistent.
			return;
		}
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

	public synchronized boolean finishCall(String callId) {
		String eventBusSubscriberId = callIdToEventBusSubscriberId.get(callId);
		if (eventBusSubscriberId == null) {
			logger.error("Cannot finish call.\nEstablished call with callId " +
					"'{}' not found.", callId);
			return false;
		}
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
						boolean byeSent = uac.sendByeRequest(dialog);
						if (byeSent) {
							listener.onCallFinished(callId);
						}
						return byeSent;
					}
				}
			} finally {
				if (calls.isEmpty()) {
					establishedCalls.remove(eventBusSubscriberId);
				}
			}
		}
		logger.error("Cannot finish call.\nEstablished call with callId " +
				"'{}' not found.", callId);
		return false;
	}

	@Subscribe
	public void onEvent(DeadEvent deadEvent) {
		System.out.println("Dead event: " + deadEvent.getEvent().getClass());
	}

	public static void main(String[] args) {
		UserAgent ua = new UserAgent("vic", "192.168.25.217:5060", "vic",
				"192.168.25.217", 50400, "TCP", new SipuadaListener() {

					@Override
					public boolean onCallInvitationArrived(String callId) {
						System.out.println("Incoming invite arrived: " + callId);
						return false;
					}

					@Override
					public void onCallInvitationCanceled(String callId) {
						System.out.println("Incoming invite canceled: " + callId);
					}

					@Override
					public void onCallInvitationFailed(String reason, String callId) {
						System.out.println("'Incoming invite'/'outgoing invite answer' failed: " + reason);
					}

					@Override
					public void onCallEstablished(String callId) {
						System.out.println("New call established: " + callId);
					}

					@Override
					public void onCallFinished(String callId) {
						System.out.println("Current call finished: " + callId);
					}

				}
		);
		ua.sendRegisterRequest(new RegistrationCallback() {

			@Override
			public void onRegistrationSuccess(List<String> registeredContacts) {
				System.out.println("Registration 1 success: " + registeredContacts);
			}

			@Override
			public void onRegistrationRenewed() {}

			@Override
			public void onRegistrationFailed(String reason) {
				System.out.println("Registration 1 failed: " + reason);
			}

		});
		System.out.println("Registration 1 sent!");
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
//		ua.sendRegisterRequest(new RegistrationCallback() {
//
//			@Override
//			public void onRegistrationSuccess(List<String> registeredContacts) {
//				System.out.println("Registration 2 success: " + registeredContacts);
//			}
//
//			@Override
//			public void onRegistrationRenewed() {}
//
//			@Override
//			public void onRegistrationFailed(String reason) {
//				System.out.println("Registration 2 failed: " + reason);
//			}
//
//		});
//		System.out.println("Registration 2 sent!");
//		try {
//			Thread.sleep(3000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
		ua.sendInviteRequest("gui", "192.168.25.217:5060", new CallInvitationCallback() {

			@Override
			public void onWaitingForCallInvitationAnswer(String callId) {
				System.out.println("Invite 1 waiting for answer: " + callId);
			}

			@Override
			public void onCallInvitationRinging(String callId) {
				System.out.println("Invite 1 ringing: " + callId);
			}

			@Override
			public void onCallInvitationDeclined(String reason) {
				System.out.println("Invite 1 declined: " + reason);
			}

		});
		System.out.println("Invitation 1 sent!");
//		try {
//			Thread.sleep(50000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
//		ua.sendRegisterRequest(new RegistrationCallback() {
//
//			@Override
//			public void onRegistrationSuccess(List<String> registeredContacts) {
//				System.out.println("Registration 3 success: " + registeredContacts);
//			}
//
//			@Override
//			public void onRegistrationRenewed() {}
//
//			@Override
//			public void onRegistrationFailed(String reason) {
//				System.out.println("Registration 3 failed: " + reason);
//			}
//
//		});
//		System.out.println("Registration 3 sent!");
//		ua.sendInviteRequest("and", "192.168.25.217:5060", new CallInvitationCallback() {
//
//			@Override
//			public void onWaitingForCallInvitationAnswer(String callId) {
//				System.out.println("Invite 2 waiting for answer: " + callId);
//			}
//
//			@Override
//			public void onCallInvitationRinging(String callId) {
//				System.out.println("Invite 2 ringing: " + callId);
//			}
//
//			@Override
//			public void onCallInvitationDeclined(String reason) {
//				System.out.println("Invite 2 declined: " + reason);
//			}
//
//		});
//		System.out.println("Invitation 2 sent!");
	}

}
