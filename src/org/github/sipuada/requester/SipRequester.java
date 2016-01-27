package org.github.sipuada.requester;

import java.text.ParseException;

import org.github.sipuada.SipReceiver;
import org.github.sipuada.Sipuada;
import org.github.sipuada.events.SendRequestEvent;
import org.github.sipuada.events.SendResponseEvent;
import org.github.sipuada.requester.messages.SipRequest;
import org.github.sipuada.requester.messages.SipRequestUtils;
import org.github.sipuada.sip.SipContact;
import org.github.sipuada.sip.SipProfile;
import org.github.sipuada.state.SipStateMachine;

import android.javax.sdp.SdpException;
import android.javax.sdp.SessionDescription;
import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipException;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.TransactionUnavailableException;
import android.javax.sip.address.Address;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.SipURI;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContactHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class SipRequester {

	private static String TAG = "tSipRequester";
	private AddressFactory addressFactory;
	private MessageFactory messageFactory;
	private HeaderFactory headerFactory;

	private SipStateMachine stateMachine;
	private SipReceiver receiver;
	private SipConnection sipConnection;

	public SipRequester(SipStateMachine machine, SipConnection sipConnection) {
		stateMachine = machine;
		receiver = new SipReceiver(machine);
		this.sipConnection = sipConnection;
		Sipuada.getEventBus().register(SendRequestEvent.class);
		Sipuada.getEventBus().register(SendResponseEvent.class);
		// TODO setup the Provider and Listening Point, as well as both Server
		// and Client transactions.
		// TODO also create the current Call Id Header and current Dialog here?
		// TODO register SipReceiver as a SipListener in the SipProvider:
		// provider.addSipListener(receiver)
	}

	public AddressFactory getAddressFactory() {
		return addressFactory;
	}

	public void setAddressFactory(AddressFactory addressFactory) {
		this.addressFactory = addressFactory;
	}

	public MessageFactory getMessageFactory() {
		return messageFactory;
	}

	public void setMessageFactory(MessageFactory messageFactory) {
		this.messageFactory = messageFactory;
	}

	public HeaderFactory getHeaderFactory() {
		return headerFactory;
	}

	public void setHeaderFactory(HeaderFactory headerFactory) {
		this.headerFactory = headerFactory;
	}

	public SipStateMachine getStateMachine() {
		return stateMachine;
	}

	public void setStateMachine(SipStateMachine stateMachine) {
		this.stateMachine = stateMachine;
	}

	public SipReceiver getReceiver() {
		return receiver;
	}

	public void setReceiver(SipReceiver receiver) {
		this.receiver = receiver;
	}

	private String sipRequestVerbToRequestMethod(SipRequestVerb sipRequestVerb) {
		String requestMethod = null;
		switch (sipRequestVerb) {
		case REGISTER:
			requestMethod = Request.REGISTER;
			break;
		case INVITE:
			requestMethod = Request.INVITE;
		case MESSAGE:
			requestMethod = Request.MESSAGE;
		case BYE:
			requestMethod = Request.BYE;
			break;
		case CANCEL:
			requestMethod = Request.CANCEL;
			break;
		case INFO:
			requestMethod = Request.INFO;
			break;
		case UNREGISTER:
			// The request method for unregister is Request.REGISTER. It is not
			// wrong case.
			requestMethod = Request.REGISTER;
			break;
		default:
			break;
		}
		return requestMethod;
	}

	private SipRequest createSipRequest(final SipRequestVerb sipRequestVerb, final SipRequestState state,
			final SipProfile sipProfile, final SipContact sipContact, final String textMessage) {

		// On REGISTER method, the sip contact must be the same as sip
		// profile sender.
		final SipContact newSipContact = (null == sipContact ? new SipContact(sipProfile) : sipContact);

		SipRequest sipRequest = null;
		try {
			sipRequest = new SipRequest(state, sipProfile, newSipContact, addressFactory, headerFactory, messageFactory,
					sipConnection.getProvider(), sipRequestVerbToRequestMethod(sipRequestVerb),
					sipConnection.getCallSequence(), 70, 300, textMessage);

			// update current CallId header of sipConnection
			sipConnection.setCurrentCallId(sipRequest.getCallIdHeader());
		} catch (ParseException | InvalidArgumentException | SipException | SdpException e) {
			e.printStackTrace();
		}

		return sipRequest;
	}

	private SipRequest createSipRequest(final SipRequestVerb sipRequestVerb, final SipRequestState state,
			final SipProfile sipProfile, final SipContact sipContact) {
		return createSipRequest(sipRequestVerb, state, sipProfile, sipContact, null);
	}

	private void sendRegister(final SipRequestState state, final SipProfile sipProfile) {
		SipRequest sipRequest = createSipRequest(SipRequestVerb.REGISTER, state, sipProfile, null);
		sipConnection.setCurrentCallId(sipRequest.getCallIdHeader());
		sendRequest(SipRequestVerb.REGISTER, sipRequest.getRequest());

	}

	private void sendInvite(final SipRequestState state, final SipProfile sipProfile, final SipContact sipContact) {
		SipRequest sipRequest = createSipRequest(SipRequestVerb.INVITE, state, sipProfile, sipContact);
		sipConnection.setCurrentCallId(sipRequest.getCallIdHeader());
		sendRequest(SipRequestVerb.INVITE, sipRequest.getRequest());
	}

	private void sendMessage(final SipRequestState state, final SipProfile sipProfile, final SipContact sipContact,
			final String textMessage) {
		SipRequest sipRequest = createSipRequest(SipRequestVerb.INVITE, state, sipProfile, sipContact, textMessage);
		sendRequest(SipRequestVerb.INVITE, sipRequest.getRequest());
	}

	// ...//

	private void sendBye(final SipRequestState state) {
		try {
			// create Request from dialog
			Request request = sipConnection.getCurrentDialog().createRequest(Request.BYE);
			sendRequest(SipRequestVerb.BYE, request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendCancel(final SipRequestState state) {
		try {
			// create Request from dialog
			Request request = sipConnection.getClientTransaction().createCancel();
			sendRequest(SipRequestVerb.CANCEL, request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendDecline(final SipRequestState state, final Request incomingRequest) {
		// create new response for the request
		try {
			Response response = messageFactory.createResponse(Response.DECLINE, incomingRequest);
			sendResponse(SipResponseCode.DECLINE, incomingRequest, response);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private void sendBusyHere(final SipRequestState state, final Request incomingRequest) {
		// create new response for the request
		try {
			Response response = messageFactory.createResponse(Response.BUSY_HERE, incomingRequest);
			sendResponse(SipResponseCode.BUSY_HERE, incomingRequest, response);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private void sendNotFound(final SipRequestState state, final Request incomingRequest) {
		// create new response for the request
		try {
			Response response = messageFactory.createResponse(Response.NOT_FOUND, incomingRequest);
			sendResponse(SipResponseCode.NOT_FOUND, incomingRequest, response);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private void sendRinging(final SipRequestState state, final Request incomingRequest) {
		// create new response for the request
		try {
			Response response = messageFactory.createResponse(Response.RINGING, incomingRequest);
			sendResponse(SipResponseCode.RINGING, incomingRequest, response);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private void sendAck(final SipRequestState state) {
		// create new response for the request
		try {
			Request request = sipConnection.getCurrentDialog().createAck(sipConnection.incrementCallSequence());
			sendRequest(SipRequestVerb.ACK, request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendOK(SipRequestVerb verb, Request incomingRequest,
			ServerTransaction associatedTransactionWithIncomingRequest, SipProfile sipProfile) {
		
		try {
			Response response = messageFactory.createResponse(Response.OK, incomingRequest);
			sendResponse(verb, SipResponseCode.OK, incomingRequest, response, associatedTransactionWithIncomingRequest,
					sipProfile);
			
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void sendInviteOK(Request incomingRequest, ServerTransaction associatedTransactionWithIncomingRequest,
			SipProfile sipProfile) {
		
		sendOK(SipRequestVerb.INVITE, incomingRequest, associatedTransactionWithIncomingRequest, sipProfile);
	}

	private void sendMessageOK(Request incomingRequest, ServerTransaction associatedTransactionWithIncomingRequest,
			SipProfile sipProfile) {
		
		sendOK(SipRequestVerb.MESSAGE, incomingRequest, associatedTransactionWithIncomingRequest, sipProfile);
	}

	private void sendResponse(SipRequestVerb verb, final int sipResponseCode, final Request incomingRequest,
			final Response response, final ServerTransaction associatedTransactionWithIncomingRequest,
			final SipProfile sipProfile) {

		try {
			if (null == associatedTransactionWithIncomingRequest) {
				// Create the contact name address
				SipURI contactURI = addressFactory.createSipURI(sipProfile.getUsername(),
						sipProfile.getLocalIpAddress());
				contactURI.setPort(sipProfile.getLocalSipPort());
				Address contactAddress = addressFactory.createAddress(contactURI);
				contactAddress.setDisplayName(sipProfile.getDisplayName());

				// Create a new Contact header
				ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
				contactHeader.setExpires(300); // 5 minutes
				response.addHeader(contactHeader);

				ContentTypeHeader contentTypeHeader = null;
				Object content = null;
				if(SipRequestVerb.INVITE == verb) {
					contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
					SessionDescription sdp = SipRequestUtils.createSDP(incomingRequest, sipProfile);
					content = sdp.toString();
				} else if (SipRequestVerb.MESSAGE == verb) {
					contentTypeHeader = headerFactory.createContentTypeHeader("text", "plain");
					content = incomingRequest.getContent().toString();
				}
				response.setContent(content, contentTypeHeader);

				// Send the created response
				if (sipConnection.getServerTransaction() == null)
					sipConnection
							.setServerTransaction(sipConnection.getProvider().getNewServerTransaction(incomingRequest));

				Thread transactionHandler = new Thread() {
					public void run() {
						try {
							sipConnection.getServerTransaction().sendResponse(response);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				};
				transactionHandler.start();
			} else {
				Thread associatedTransactionHandler = new Thread() {
					public void run() {
						try {
							associatedTransactionWithIncomingRequest.sendResponse(response);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				};
				associatedTransactionHandler.start();

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void sendResponse(final int sipResponseCode, final Request incomingRequest, final Response response) {
		Thread transactionHandler = new Thread() {
			public void run() {
				try {
					// Create the client transaction
					// TODO put this in Connection class
					ServerTransaction serverTransaction = sipConnection.getServerTransaction();
					if (null == serverTransaction) {
						serverTransaction = sipConnection.getProvider().getNewServerTransaction(incomingRequest);
						sipConnection.setServerTransaction(serverTransaction);
					}

					// and send the response
					serverTransaction.sendResponse(response);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		transactionHandler.start();
	}

	private void sendRequest(final SipRequestVerb verb, final Request request) {

		// TODO SE DEU CERTO currentCallId = message.getCallIdHeader();

		Thread transactionHandler = new Thread() {
			public void run() {
				try {
					// Create the client transaction
					// TODO put this in Connection class
					sipConnection.setClientTransaction(sipConnection.getProvider().getNewClientTransaction(request));

					final ClientTransaction clientTransaction = sipConnection.getClientTransaction();
					// and send the request
					if (SipRequestVerb.REGISTER == verb || SipRequestVerb.INVITE == verb
							|| SipRequestVerb.MESSAGE == verb || SipRequestVerb.CANCEL == verb) {
						clientTransaction.sendRequest();
					} else if (SipRequestVerb.BYE == verb) {
						sipConnection.getCurrentDialog().sendRequest(clientTransaction);
					} else if (SipRequestVerb.ACK == verb) {
						sipConnection.getCurrentDialog().sendAck(request);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		transactionHandler.start();

		if (SipRequestVerb.INVITE == verb || SipRequestVerb.MESSAGE == verb) {
			sipConnection.setCurrentDialog(sipConnection.getClientTransaction().getDialog());
		}

	}

	public void onEvent(SendRequestEvent event) {
		//TODO send new requests in the order specified in event.getVerbs() using last response in event.getResponse().
		//Important, DON'T CHECK WITH THE MACHINE STATE WHETHER THE REQUESTS CAN BE SENT with canRequestBeSent() before sending, send them right away.
	}

	public void onEvent(SendResponseEvent event) {
		//TODO send a new response with code event.getCode() to answer request in event.getRequest().
		//Important, DON'T CHECK WITH THE MACHINE STATE WHETHER THE REQUEST CAN BE SENT with canResponseBeSent() before sending, send it right away.
	}

}
