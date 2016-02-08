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
import org.github.sipuada.events.RegistrationFailed;
import org.github.sipuada.events.RegistrationSuccess;
import org.github.sipuada.events.RequestSent;
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

	private SipuadaListener listener;
	private UserAgentClient uac;
	private UserAgentServer uas;

	public UserAgent(String username, String domain, String password, String localIp,
			int localPort, String transport, SipuadaListener listener) {
		eventBus.register(this);
		SipProvider provider;
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
						assignSipuadaListener(listener);
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

	public void assignSipuadaListener(final SipuadaListener listener) {
		final String eventBusSubscriberId =
				Utils.getInstance().generateTag();
		Object inviteReceiverEventBusSubscriber = new Object() {

			@Subscribe
			public void onEvent(CallInvitationArrived event) {
				ServerTransaction transaction = event.getServerTransaction();
				Dialog dialog = transaction.getDialog();
				if (dialog != null) {
					final String callId = dialog.getCallId().getCallId();
					inviteOperationIsAnswerable(eventBusSubscriberId,
							callId, transaction);
					Object inviteCancelerEventBusSubscriber = new Object() {
						
						@Subscribe
						public void onEvent(CallInvitationCanceled event) {
							wipeAnswerableInviteOperation(callId,
									eventBusSubscriberId);
							eventBus.unregister(this);
							listener.onCallInvitationCanceled(callId);
						}

					};
					eventBus.register(inviteCancelerEventBusSubscriber);
					boolean currentlyBusy = listener.onCallInvitationArrived(callId);
					if (currentlyBusy) {
						eventBus.unregister(inviteCancelerEventBusSubscriber);
						//TODO uas.sendResponse() to pass a BUSY_HERE response back to
						//ServerTransaction associated with this event.
					}
				}
				else {
					String error = "Received [CallInvitationArrived] event with" +
							" a NULL dialog! This should never happen.";
					logger.error(error);
				}
			}

		};
		eventBus.register(inviteReceiverEventBusSubscriber);
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
		final String eventBusSubscriberId = Utils.getInstance().generateTag();
		Object eventBusSubscriber = new Object() {

			@Subscribe
			public void onEvent(CallInvitationWaiting event) {
				ClientTransaction transaction = event.getClientTransaction();
				Dialog dialog = transaction.getDialog();
				if (dialog != null) {
					String callId = dialog.getCallId().getCallId();
					inviteOperationIsCancelable(eventBusSubscriberId,
							callId, transaction);
					inviteOperationIsDeclineable(eventBusSubscriberId, callId, callback);
					callback.onWaitingForCallInvitationAnswer(callId);
				}
				else {
					String error = "Received [CallInvitationWaiting] event with" +
							" a NULL dialog! This should never happen.";
					logger.error(error);
					eventBus.post(new CallInvitationFailed(error, null));
				}
			}

			@Subscribe
			public void onEvent(CallInvitationRinging event) {
				ClientTransaction transaction = event.getClientTransaction();
				Dialog dialog = transaction.getDialog();
				if (dialog != null) {
					final String callId = dialog.getCallId().getCallId();
					inviteOperationIsCancelable(eventBusSubscriberId,
							callId, transaction);
					inviteOperationIsDeclineable(eventBusSubscriberId, callId, callback);
					callback.onCallInvitationRinging(callId);
				}
				else {
					String error = "Received [CallInvitationRinging] event with" +
							" a NULL dialog! This should never happen.";
					logger.error(error);
					eventBus.post(new CallInvitationFailed(error, null));
				}
			}
			
			@Subscribe
			public void onEvent(CallInvitationAccepted event) {
				Dialog dialog = event.getDialog();
				if (dialog != null) {
					String callId = dialog.getCallId().getCallId();
					inviteOperationFinished(eventBusSubscriberId, callId);
					wipeCancelableInviteOperation(callId, eventBusSubscriberId);
					callEstablished(eventBusSubscriberId, callId, dialog);
					scheduleNextInviteRequest();
					listener.onCallEstablished(callId);
				}
				else {
					String error = "Received [CallInvitationAccepted] event with" +
							" a NULL dialog! This should never happen.";
					logger.error(error);
					eventBus.post(new CallInvitationFailed(error, null));
				}
			}

			@Subscribe
			public void onEvent(CallInvitationFailed event) {
				String callId = event.getCallId();
				inviteOperationFinished(eventBusSubscriberId, callId);
				wipeCancelableInviteOperation(callId, eventBusSubscriberId);
				scheduleNextInviteRequest();
				callback.onCallInvitationFailed(event.getReason());
			}

		};
		eventBus.register(eventBusSubscriber);
		eventBusSubscribers.put(eventBusSubscriberId, eventBusSubscriber);
		boolean expectRemoteAnswer = uac.sendInviteRequest(remoteUser, remoteDomain);
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

	private void inviteOperationIsDeclineable(final String eventBusSubscriberId,
			final String callId, final CallInvitationCallback callback) {
		Object inviteDeclinerEventBusSubscriber = new Object() {

			@Subscribe
			public void onEvent(CallInvitationDeclined event) {
				inviteOperationFinished(eventBusSubscriberId, callId);
				wipeCancelableInviteOperation(callId, eventBusSubscriberId);
				scheduleNextInviteRequest();
				eventBus.unregister(this);
				callback.onCallInvitationDeclined(event.getReason());
			}

		};
		eventBus.register(inviteDeclinerEventBusSubscriber);
	}

	private synchronized void wipeCancelableInviteOperation(String callId,
			String eventBusSubscriberId) {
		if (callId == null) {
			//No data relation should have been assigned for this callId
			//in the first place, so we wouldn't have any links to remove.
			return;
		}
		callIdToEventBusSubscriberId.remove(callId);
		List<ClientTransaction> operations = cancelableInviteOperations
				.get(eventBusSubscriberId);
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
	
	//TODO implement cancelInviteRequest(String callId).

	public synchronized boolean answerInviteRequest(String callId,
			boolean acceptCallInvitation) {
		String eventBusSubscriberId = callIdToEventBusSubscriberId.get(callId);
		if (eventBusSubscriberId == null) {
			logger.error("INVITE request with callId '{}' not found.", callId);
			return false;
		}
		List<ServerTransaction> operations = answerableInviteOperations
				.get(eventBusSubscriberId);
		synchronized (operations) {
			Iterator<ServerTransaction> iterator = operations.iterator();
			try {
				while (iterator.hasNext()) {
					ServerTransaction serverTransaction = iterator.next();
					Dialog dialog = serverTransaction.getDialog();
					if (dialog.getCallId().getCallId().equals(callId)) {
						iterator.remove();
						wipeAnswerableInviteOperation(callId, eventBusSubscriberId);
						if (acceptCallInvitation) {
							boolean responseSent = true;//TODO uas.sendResponse() to pass
							//a OK response back to ServerTransaction associated
							//with this event.
							if (responseSent) {
								callEstablished(eventBusSubscriberId, callId, dialog);
								listener.onCallEstablished(callId);
							}
							return responseSent;
						}
						else {
							boolean responseSent = true;//TODO uas.sendResponse() to pass
							//a BUSY_HERE response back to ServerTransaction
							//associated with this event.
							if (responseSent) {
							}
							return responseSent;
						}
					}
				}
			} finally {
				if (operations.isEmpty()) {
					answerableInviteOperations.remove(eventBusSubscriberId);
				}
			}
		}
		return false;
	}

	private synchronized void wipeAnswerableInviteOperation(String callId,
			String eventBusSubscriberId) {
		if (callId == null) {
			//No data relation should have been assigned for this callId
			//in the first place, so we wouldn't have any links to remove.
			return;
		}
		callIdToEventBusSubscriberId.remove(callId);
		List<ServerTransaction> operations = answerableInviteOperations
				.get(eventBusSubscriberId);
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

	private synchronized void callEstablished(String eventBusSubscriberId,
			String callId, Dialog dialog) {
		callIdToEventBusSubscriberId.put(callId, eventBusSubscriberId);
		if (!establishedCalls.containsKey(eventBusSubscriberId)) {
			establishedCalls.put(eventBusSubscriberId, Collections
					.synchronizedList(new LinkedList<Dialog>()));
		}
		establishedCalls.get(eventBusSubscriberId).add(dialog);
		//TODO add eventBusSubscriber to capture onEvent(CallFinished) here.
		//This should eventually call listener.onCallFinished(String callId).
	}

	//TODO implement finishCall(String callId).
	//This should eventually call listener.onCallFinished(String callId).

	@Subscribe
	public void processRequestSent(RequestSent event) {
		System.out.println("Request sent: " + event.getClass() + "!");
	}

	@Subscribe
	public void onEvent(DeadEvent deadEvent) {
		System.out.println("Dead event: " + deadEvent.getEvent().getClass());
	}

	public static void main(String[] args) {
		UserAgent ua = new UserAgent("gui", "192.168.25.217:5060", "gui",
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
		ua.sendInviteRequest("vic", "192.168.25.217:5060", new CallInvitationCallback() {

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

			@Override
			public void onCallInvitationFailed(String reason) {
				System.out.println("Invite 1 failed: " + reason);
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
		ua.sendInviteRequest("and", "192.168.25.217:5060", new CallInvitationCallback() {

			@Override
			public void onWaitingForCallInvitationAnswer(String callId) {
				System.out.println("Invite 2 waiting for answer: " + callId);
			}

			@Override
			public void onCallInvitationRinging(String callId) {
				System.out.println("Invite 2 ringing: " + callId);
			}

			@Override
			public void onCallInvitationDeclined(String reason) {
				System.out.println("Invite 2 declined: " + reason);
			}

			@Override
			public void onCallInvitationFailed(String reason) {
				System.out.println("Invite 2 failed: " + reason);
			}

		});
		System.out.println("Invitation 2 sent!");
	}

}
