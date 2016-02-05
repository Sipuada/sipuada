package org.github.sipuada;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TooManyListenersException;

import org.github.sipuada.SipuadaApi.CallInvitationCallback;
import org.github.sipuada.SipuadaApi.RegistrationCallback;
import org.github.sipuada.SipuadaApi.SipuadaListener;
import org.github.sipuada.events.CallInvitationDeclined;
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
	private final int MAX_PORT = 6000;

	private EventBus eventBus = new EventBus();
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

	Map<String, Object> eventBusSubscribers = new HashMap<>();

	public boolean sendRegisterRequest(final RegistrationCallback callback) {
		final String eventBusSubscriberId = Utils.getInstance().generateTag();
		Object eventBusSubscriber = new Object() {

			@Subscribe
			public void onEvent(RegistrationSuccess event) {
				callback.onRegistrationSuccess(event.getContactBindings());
				eventBus.unregister(eventBusSubscribers.get(eventBusSubscriberId));
			}

			@Subscribe
			public void onEvent(RegistrationFailed event) {
				callback.onRegistrationFailed(event.getReason());
				eventBus.unregister(eventBusSubscribers.get(eventBusSubscriberId));
			}

		};
		eventBus.register(eventBusSubscriber);
		eventBusSubscribers.put(eventBusSubscriberId, eventBusSubscriber);
		boolean expectRemoteAnswer = uac.sendRegisterRequest();
		if (!expectRemoteAnswer) {
			eventBus.unregister(eventBusSubscriber);
		}
		return expectRemoteAnswer;
	}
	
	public boolean sendInviteRequest(String remoteUser, String remoteDomain,
			final CallInvitationCallback callback, final SipuadaListener listener) {
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
					logger.error("Received [WaitingForCallInvitationAnswer] event with" +
							" a NULL dialog! This should never happen.");
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
					logger.error("Received [CallInvitationRinging] event with" +
							" a NULL dialog! This should never happen.");
				}
			}

			@Subscribe
			public void onEvent(CallInvitationDeclined event) {
				callback.onCallInvitationDeclined(event.getReason());
				eventBus.unregister(eventBusSubscribers.get(eventBusSubscriberId));
			}

		};
		eventBus.register(eventBusSubscriber);
		eventBusSubscribers.put(eventBusSubscriberId, eventBusSubscriber);
		boolean expectRemoteAnswer = uac.sendInviteRequest(remoteUser, remoteDomain);
		if (!expectRemoteAnswer) {
			eventBus.unregister(eventBusSubscriber);
		}
		return expectRemoteAnswer;
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
				"192.168.130.207", 50400, "TCP");
		ua.sendRegisterRequest(new RegistrationCallback() {

			@Override
			public void onRegistrationSuccess(List<String> registeredContacts) {
				System.out.println("My registration success called: " + registeredContacts);
			}

			@Override
			public void onRegistrationRenewed() {}

			@Override
			public void onRegistrationFailed(String reason) {
				System.out.println("My registration failed called: " + reason + "!");
			}

		});
	}

}
