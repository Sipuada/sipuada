package org.github.sipuada;

import java.text.ParseException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.Constants.ResponseClass;
import org.github.sipuada.events.CallInvitationArrived;
import org.github.sipuada.events.CallInvitationCanceled;
import org.github.sipuada.events.EstablishedCallFinished;
import org.github.sipuada.events.EstablishedCallStarted;
import org.github.sipuada.exceptions.RequestCouldNotBeAddressed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import android.javax.sip.Dialog;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.RequestEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipException;
import android.javax.sip.SipProvider;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionAlreadyExistsException;
import android.javax.sip.TransactionUnavailableException;
import android.javax.sip.address.Address;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.SipURI;
import android.javax.sip.address.URI;
import android.javax.sip.header.AllowHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.ContactHeader;
import android.javax.sip.header.Header;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.ToHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class UserAgentServer {

	private final Logger logger = LoggerFactory.getLogger(UserAgentServer.class);

	private final EventBus bus;
	private final SipProvider provider;
	private final MessageFactory messenger;
	private final HeaderFactory headerMaker;
	private final AddressFactory addressMaker;

	private final String username;
	private final String localIp;
	private final int localPort;

	public UserAgentServer(EventBus eventBus, SipProvider sipProvider,
			MessageFactory messageFactory, HeaderFactory headerFactory,
			AddressFactory addressFactory, String... credentialsAndAddress) {
		bus = eventBus;
		provider = sipProvider;
		messenger = messageFactory;
		headerMaker = headerFactory;
		addressMaker = addressFactory;
		username = credentialsAndAddress.length > 0 && credentialsAndAddress[0] != null ?
				credentialsAndAddress[0] : "";
		localIp = credentialsAndAddress.length > 1 && credentialsAndAddress[1] != null ?
				credentialsAndAddress[1] : "127.0.0.1";
		localPort = credentialsAndAddress.length > 2 && credentialsAndAddress[2] != null ?
				Integer.parseInt(credentialsAndAddress[2]) : 5060;
	}

	public void processRequest(RequestEvent requestEvent) {
		ServerTransaction serverTransaction = requestEvent.getServerTransaction();
		Request request = requestEvent.getRequest();
		RequestMethod method = RequestMethod.UNKNOWN;
		try {
			method = RequestMethod.valueOf(request.getMethod());
		} catch (IllegalArgumentException ignore) {}
		logger.debug("Request arrived to UAS with method {}.", method);
		handleRequest(method, request, serverTransaction);
	}
	
	private void handleRequest(RequestMethod method, Request request,
			ServerTransaction serverTransaction) {
		try {
			if (tryHandlingRequestGenerically(method, request, serverTransaction)) {
				switch (method) {
					case CANCEL:
						handleCancelRequest(request, serverTransaction);
						break;
					case OPTIONS:
						//FIXME later handle OPTIONS request as well.
						throw new RequestCouldNotBeAddressed();
					case INVITE:
						handleInviteRequest(request, serverTransaction);
						break;
					case ACK:
						handleAckRequest(request, serverTransaction);
						break;
					case BYE:
						handleByeRequest(request, serverTransaction);
						break;
					case UNKNOWN:
					default:
						throw new RequestCouldNotBeAddressed();
				}
			}
		} catch (RequestCouldNotBeAddressed requestCouldNotBeAddressed) {
			//This means that some internal error happened during UAS processing
			//of this request. Probably it couldn't send a response back.
			//TODO do something about this problem, what?
			logger.error("{} request could not be addressed.", method);
		}
	}

	private boolean tryHandlingRequestGenerically(RequestMethod method, Request request,
			ServerTransaction serverTransaction) {
		if (!methodIsAllowed(method)) {
			logger.warn("{} request is not allowed.", method);
			//TODO add Allow header with supported methods.
			List<Header> allowedMethods = new LinkedList<>();
			try {
				AllowHeader allowHeader = headerMaker
						.createAllowHeader(RequestMethod.CANCEL.toString());
				allowedMethods.add(allowHeader);
				allowHeader = headerMaker
						.createAllowHeader(RequestMethod.OPTIONS.toString());
				allowedMethods.add(allowHeader);
				allowHeader = headerMaker
						.createAllowHeader(RequestMethod.INVITE.toString());
				allowedMethods.add(allowHeader);
				allowHeader = headerMaker
						.createAllowHeader(RequestMethod.BYE.toString());
				allowedMethods.add(allowHeader);
			} catch (ParseException ignore) {}
			if (doSendResponse(Response.METHOD_NOT_ALLOWED, method,
					request, serverTransaction, allowedMethods
					.toArray(new Header[allowedMethods.size()])) != null) {
				logger.info("{} response sent.",
						Response.METHOD_NOT_ALLOWED);
				return false;
			}
			throw new RequestCouldNotBeAddressed();
		}
		//TODO if UAS performs auth challenges, remember that it MUST NOT do it
		//for CANCEL requests.
		if (!requestShouldBeAddressed(method, request, serverTransaction)) {
			logger.info("{} request should not be addressed by this UAS.", method);
			return false;
		}
		//TODO examine Require header field and check whether a
		//(420 BAD EXTENSION) response is appropriate.
		//TODO perform content processing, responding with
		//(415 UNSUPPORTED MEDIA TYPE) when appropriate.
		return true;
	}

	private boolean methodIsAllowed(RequestMethod method) {
		switch (method) {
			case UNKNOWN:
			default:
				return false;
			case CANCEL:
			case OPTIONS:
			case INVITE:
			case ACK:
			case BYE:
				return true;
			}
	}
	
	private boolean requestShouldBeAddressed(RequestMethod method, Request request,
			ServerTransaction serverTransaction) {
		ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);
		String identityUser = username.toLowerCase();
		String identityHost = localIp;
		if (toHeader != null) {
			boolean shouldForbid = true;
			Address toAddress = toHeader.getAddress();
			URI toUri = toAddress.getURI();
			String[] toUriParts = toUri.toString().split("@");
			if (toUriParts.length > 1) {
				String toUriUser = toUriParts[0].split(":")[1].trim().toLowerCase();
				if (toUriUser.equals(identityUser)) {
					shouldForbid = false;
				}
				else {
					logger.info("Request destined to (To Header = {}) arrived but this UAC" +
							" is bound to {}@{}, so about to respond with 403 Forbidden.",
							toUri, identityUser, identityHost);
				}
			}
			if (shouldForbid) {
				if (doSendResponse(Response.FORBIDDEN, method,
						request, serverTransaction) != null) {
					logger.info("{} response sent.", Response.FORBIDDEN);
					return false;
				}
				else {
					throw new RequestCouldNotBeAddressed();
				}
			}
		}
		URI requestUri = request.getRequestURI();
		boolean shouldNotFound = true;
		String[] requestUriParts = requestUri.toString().split("@");
		if (requestUriParts.length > 1) {
			String requestUriUser = requestUriParts[0].split(":")[1].trim().toLowerCase();
			String requestUriHost = requestUriParts[1].split(":")[0].trim().toLowerCase();
			if (requestUriUser.equals(identityUser) &&
					requestUriHost.equals(identityHost)) {
				shouldNotFound = false;
			}
			else {
				logger.info("Request destined to (Request URI = {}) arrived but this UAC" +
						" is bound to {}@{}, so about to respond with 404 Not Found.",
						requestUri, identityUser, identityHost);
			}
		}
		if (shouldNotFound) {
			if (doSendResponse(Response.NOT_FOUND, method,
					request, serverTransaction) != null) {
				logger.info("{} response sent.", Response.NOT_FOUND);
				return false;
			}
			else {
				throw new RequestCouldNotBeAddressed();
			}
		}
		return true;
	}

	private void handleCancelRequest(Request request, ServerTransaction serverTransaction) {
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		if (doSendResponse(Response.OK, RequestMethod.CANCEL,
				request, serverTransaction) != null) {
			logger.info("{} response sent.", Response.OK);
			bus.post(new CallInvitationCanceled("Call invitation canceled by the caller " +
					"or callee took longer than roughly 30 seconds to answer.", callId, true));
			return;
		}
		throw new RequestCouldNotBeAddressed();
	}

	protected void doTerminateCanceledInvite(Request request, ServerTransaction serverTransaction) {
		doSendResponse(Response.REQUEST_TERMINATED, RequestMethod.INVITE,
				request, serverTransaction);
	}

	private void handleInviteRequest(Request request, ServerTransaction serverTransaction) {
		boolean withinDialog = serverTransaction != null;
		if (withinDialog) {
			handleReinviteRequest(request, serverTransaction);
			return;
		}
		//TODO take into consideration session offer/answer negotiation procedures.
		//TODO also consider supporting multicast conferences, by sending silent 2xx responses
		//when appropriate, by using identifiers within the SDP session description.
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		ServerTransaction newServerTransaction = doSendResponse(Response.RINGING,
				RequestMethod.INVITE, request, serverTransaction);
		if (newServerTransaction != null) {
			logger.info("{} response sent.", Response.RINGING);
			bus.post(new CallInvitationArrived(callId, newServerTransaction));
			return;
		}
		throw new RequestCouldNotBeAddressed();
	}

	private void handleReinviteRequest(Request request, ServerTransaction serverTransaction) {
		//FIXME later implement REINVITE as well.
		throw new RequestCouldNotBeAddressed();
	}
	
	private void handleAckRequest(Request request, ServerTransaction serverTransaction) {
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		bus.post(new EstablishedCallStarted(callId, serverTransaction.getDialog()));
		logger.info("New call established: {}.", callId);
	}

	private void handleByeRequest(Request request, ServerTransaction serverTransaction) {
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		if (doSendResponse(Response.OK, RequestMethod.BYE,
				request, serverTransaction) != null) {
			logger.info("{} response sent.", Response.OK);
			bus.post(new EstablishedCallFinished(callId));
			return;
		}
		throw new RequestCouldNotBeAddressed();
	}

	public void processRetransmission(TimeoutEvent retransmissionEvent) {
		if (retransmissionEvent.isServerTransaction()) {
			ServerTransaction serverTransaction = retransmissionEvent.getServerTransaction();
			//TODO Dialog layer says we should retransmit a response. how?
			logger.warn("<RETRANSMISSION event>: " + serverTransaction);
		}
	}

	public boolean sendAcceptResponse(RequestMethod method, Request request,
			ServerTransaction serverTransaction) {
		SipURI contactUri;
		try {
			contactUri = addressMaker.createSipURI(username, localIp);
		} catch (ParseException parseException) {
			logger.error("Could not properly create the contact URI for {} at {}." +
					"[username] must be a valid id, [localIp] must be a valid " +
					"IP address: {}", username, localIp, parseException.getMessage());
			//No need for caller to wait for remote responses.
			return false;
		}
		contactUri.setPort(localPort);
		Address contactAddress = addressMaker.createAddress(contactUri);
		ContactHeader contactHeader = headerMaker.createContactHeader(contactAddress);
		try {
			contactHeader.setExpires(60);
		} catch (InvalidArgumentException ignore) {}
		//TODO later, allow for multiple contact headers here too (the ones REGISTERed).

		Header[] additionalHeaders = ((List<ContactHeader>)(Collections
				.singletonList(contactHeader))).toArray(new ContactHeader[1]);
		if (doSendResponse(Response.OK, method, request,
				serverTransaction, additionalHeaders) != null) {
			logger.info("{} response sent.", Response.OK);
			return true;
		}
		return false;
	}

	public boolean sendRejectResponse(RequestMethod method, Request request,
			ServerTransaction serverTransaction) {
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		if (doSendResponse(Response.BUSY_HERE,
				method, request, serverTransaction) != null) {
			logger.info("{} response sent.", Response.BUSY_HERE);
			bus.post(new CallInvitationCanceled("Call invitation rejected by the callee or callee" +
					" is currently busy and couldn't take another call.", callId, false));
			return true;
		}
		return false;
	}

	private ServerTransaction doSendResponse(int statusCode, RequestMethod method,
			Request request, ServerTransaction serverTransaction, Header... additionalHeaders) {
		if (method == RequestMethod.UNKNOWN) {
			logger.debug("[doSendResponse(int, RequestMethod, Request, " +
					"ServerTransaction, Header...)] method forbidden for " +
					"{} requests.", method);
			return null;
		}
		boolean withinDialog = true;
		ServerTransaction newServerTransaction = serverTransaction;
		if (newServerTransaction == null) {
			withinDialog = false;
			try {
				newServerTransaction = provider.getNewServerTransaction(request);
				/*if (method == RequestMethod.INVITE) {
					newServerTransaction.enableRetransmissionAlerts();
				}*/
			} catch (TransactionAlreadyExistsException requestIsRetransmit) {
				//This may happen if UAS got a retransmit of already pending request.
				logger.debug("{} response could not be sent to {} request: {}.",
						statusCode, request.getMethod(),
						requestIsRetransmit.getMessage());
				return null;
			} catch (TransactionUnavailableException invalidTransaction) {
				//A invalid (maybe null) server transaction
				//can't be used to send this response.
				logger.debug("{} response could not be sent to {} request: {}.",
						statusCode, request.getMethod(),
						invalidTransaction.getMessage());
				return null;
			}/* catch (SipException couldNotEnableRetransmissionAlerts) {
				logger.debug("* {} could not be sent to {} request: {}.",
						statusCode, request.getMethod(),
						couldNotEnableRetransmissionAlerts.getMessage());
				return null;
			}*/
		}
		Dialog dialog = newServerTransaction.getDialog();
		withinDialog &= dialog != null;
		if (Constants.getResponseClass(statusCode) == ResponseClass.PROVISIONAL &&
				method == RequestMethod.INVITE && withinDialog) {
			try {
				Response response = dialog.createReliableProvisionalResponse(statusCode);
				for (Header header : additionalHeaders) {
					response.addHeader(header);
				}
				logger.info("Sending {} response to {} request...", statusCode, method);
				dialog.sendReliableProvisionalResponse(response);
				return newServerTransaction;
			} catch (InvalidArgumentException ignore) {
			} catch (SipException invalidResponse) {
				//A final response to this request was already sent, so this
				//provisional response shall not be sent, or another reliable
				//provisional response is still pending.
				//In either case, we won't send this new response.
				logger.debug("{} response could not be sent to {} request: {} ({}).",
						statusCode, method, invalidResponse.getMessage(),
						invalidResponse.getCause().getMessage());
				return null;
			}
		}
		try {
			Response response = messenger.createResponse(statusCode, request);
			for (Header header : additionalHeaders) {
				response.addHeader(header);
			}
			logger.info("Sending {} response to {} request...", statusCode, method);
			newServerTransaction.sendResponse(response);
			return newServerTransaction;
		} catch (ParseException ignore) {
		} catch (InvalidArgumentException ignore) {
		} catch (SipException responseCouldNotBeSent) {
			logger.debug("{} response could not be sent to {} request: {} {}.",
					statusCode, method, responseCouldNotBeSent.getMessage(),
					responseCouldNotBeSent.getCause() == null ? "" :
					"(" + responseCouldNotBeSent.getCause().getMessage() + ")");
		}
		return null;
	}

}
