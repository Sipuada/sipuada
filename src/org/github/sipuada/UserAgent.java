package org.github.sipuada;

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

	private final EventBus eventBus = new EventBus();
	private final Map<String, Object> eventBusSubscribers = new HashMap<>();
	private final Map<RequestMethod, Boolean> operationsInProgress = new HashMap<>();
	{
		for (RequestMethod method : RequestMethod.values()) {
			operationsInProgress.put(method, false);
		}
	}
	private final List<Operation> postponedOperations = new LinkedList<>();

	private UserAgentClient uac;
	private UserAgentServer uas;

	public UserAgent(String username, String domain, String password,
			String localIp, int localPort, String transport) {
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
	
	private class Operation {

		public final SipuadaCallback callback;
		public final String[] arguments;
		
		public Operation(SipuadaCallback callback, String... arguments) {
			this.callback = callback;
			this.arguments = arguments;
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
				callback.onRegistrationSuccess(event.getContactBindings());
				registerOperationFinished(eventBusSubscriberId);
			}

			@Subscribe
			public void onEvent(RegistrationFailed event) {
				callback.onRegistrationFailed(event.getReason());
				registerOperationFinished(eventBusSubscriberId);
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

	public boolean sendInviteRequest(String remoteUser, String remoteDomain,
			final CallInvitationCallback callback, final SipuadaListener listener) {
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
					callback.onWaitingForCallInvitationAnswer(callId);
				}
				else {
					String error = "Received [CallInvitationWaiting] event with" +
							" a NULL dialog! This should never happen.";
					logger.error(error);
					eventBus.post(new CallInvitationFailed(error));
				}
			}

			@Subscribe
			public void onEvent(CallInvitationRinging event) {
				ClientTransaction transaction = event.getClientTransaction();
				Dialog dialog = transaction.getDialog();
				if (dialog != null) {
					String callId = dialog.getCallId().getCallId();
					callback.onCallInvitationRinging(callId);
				}
				else {
					String error = "Received [CallInvitationRinging] event with" +
							" a NULL dialog! This should never happen.";
					logger.error(error);
					eventBus.post(new CallInvitationFailed(error));
				}
			}
			
			@Subscribe
			public void onEvent(CallInvitationAccepted event) {
				Dialog dialog = event.getDialog();
				if (dialog != null) {
					String callId = dialog.getCallId().getCallId();
					listener.onCallEstablished(callId);
					inviteOperationFinished(eventBusSubscriberId, listener);
				}
				else {
					String error = "Received [CallInvitationAccepted] event with" +
							" a NULL dialog! This should never happen.";
					logger.error(error);
					eventBus.post(new CallInvitationFailed(error));
				}
			}

			@Subscribe
			public void onEvent(CallInvitationDeclined event) {
				callback.onCallInvitationDeclined(event.getReason());
				inviteOperationFinished(eventBusSubscriberId, listener);
			}

			@Subscribe
			public void onEvent(CallInvitationFailed event) {
				callback.onCallInvitationFailed(event.getReason());
				inviteOperationFinished(eventBusSubscriberId, listener);
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

	private synchronized void inviteOperationFinished(String eventBusSubscriberId,
			final SipuadaListener listener) {
		eventBus.unregister(eventBusSubscribers.remove(eventBusSubscriberId));
		operationsInProgress.put(RequestMethod.INVITE, false);
		Iterator<Operation> iterator = postponedOperations.iterator();
		while (iterator.hasNext()) {
			Operation operation = iterator.next();
			if (operation.callback instanceof CallInvitationCallback) {
				String[] arguments = operation.arguments;
				sendInviteRequest(arguments[0], arguments[1],
						(CallInvitationCallback) operation.callback, listener);
				iterator.remove();
				break;
			}
		}
	}

	@Subscribe
	public void processRequestSent(RequestSent event) {
		System.out.println("Request sent: " + event.getClass() + "!");
	}

	@Subscribe
	public void onEvent(DeadEvent deadEvent) {
		System.out.println("Dead event: " + deadEvent.getEvent().getClass());
	}

	public static void main(String[] args) {
		UserAgent ua = new UserAgent("6001", "192.168.130.126:5060", "6001",
				"192.168.25.217", 50400, "TCP");
//		ua.sendRegisterRequest(new RegistrationCallback() {
//
//			@Override
//			public void onRegistrationSuccess(List<String> registeredContacts) {
//				System.out.println("Registration 1 success: " + registeredContacts);
//			}
//
//			@Override
//			public void onRegistrationRenewed() {}
//
//			@Override
//			public void onRegistrationFailed(String reason) {
//				System.out.println("Registration 1 failed: " + reason);
//			}
//
//		});
//		System.out.println("Registration 1 sent!");
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
		ua.sendInviteRequest("6002", "192.168.130.126:5060", new CallInvitationCallback() {

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

		}, new SipuadaListener() {

			@Override
			public boolean onCallInvitationArrived(String callId) {
				System.out.println("Incoming invite 1 arrived: " + callId);
				return false;
			}

			@Override
			public void onCallInvitationCanceled(String callId) {
				System.out.println("Incoming invite 1 canceled: " + callId);
			}

			@Override
			public void onCallEstablished(String callId) {
				System.out.println("New call 1 established: " + callId);
			}

			@Override
			public void onCallFinished(String callId) {
				System.out.println("Current call 1 finished: " + callId);
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
		ua.sendInviteRequest("6002", "192.168.130.126:5060", new CallInvitationCallback() {

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

		}, new SipuadaListener() {

			@Override
			public boolean onCallInvitationArrived(String callId) {
				System.out.println("Incoming invite 2 arrived: " + callId);
				return false;
			}

			@Override
			public void onCallInvitationCanceled(String callId) {
				System.out.println("Incoming invite 2 canceled: " + callId);
			}

			@Override
			public void onCallEstablished(String callId) {
				System.out.println("New call 2 established: " + callId);
			}

			@Override
			public void onCallFinished(String callId) {
				System.out.println("Current call 2 finished: " + callId);
			}

		});
		System.out.println("Invitation 2 sent!");
	}

}
