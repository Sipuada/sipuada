package org.github.sipuada;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.Constants.ResponseClass;
import org.github.sipuada.events.CallInvitationArrived;
import org.github.sipuada.events.CallInvitationCanceled;
import org.github.sipuada.events.EstablishedCallFinished;
import org.github.sipuada.events.EstablishedCallStarted;
import org.github.sipuada.events.MessageReceived;
import org.github.sipuada.events.ReceivingMessageFailed;
import org.github.sipuada.events.ReceivingOptionsRequestFailed;
import org.github.sipuada.events.SendingInformationFailed;
import org.github.sipuada.events.SendingInformationSuccess;
import org.github.sipuada.exceptions.RequestCouldNotBeAddressed;
import org.github.sipuada.plugins.SipuadaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import android.javax.sdp.SdpFactory;
import android.javax.sdp.SdpParseException;
import android.javax.sdp.SessionDescription;
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
import android.javax.sip.header.ContentTypeHeader;
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
	private final Map<RequestMethod, SipuadaPlugin> sessionPlugins;

	private final String username;
	private final String localIp;
	private final int localPort;

	public UserAgentServer(EventBus eventBus, SipProvider sipProvider, Map<RequestMethod, SipuadaPlugin> plugins,
			MessageFactory messageFactory, HeaderFactory headerFactory, AddressFactory addressFactory,
			String... credentialsAndAddress) {
		bus = eventBus;
		provider = sipProvider;
		messenger = messageFactory;
		headerMaker = headerFactory;
		addressMaker = addressFactory;
		sessionPlugins = plugins;
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
		logger.debug("processRequest - Content:{}", request.getRawContent());
		
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
						handleOptionsRequest(request, serverTransaction);
						break;
					case MESSAGE:
						handleMessageRequest(request, serverTransaction);
						break;
					case INVITE:
						handleInviteRequest(request, serverTransaction);
						break;
					case INFO:
						handleInfoRequest(request, serverTransaction);
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
	

	private void handleInfoRequest(Request request, ServerTransaction serverTransaction) {
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		ServerTransaction newServerTransaction = doSendResponse(Response.OK,
				RequestMethod.INFO, request, serverTransaction);
		
		if (newServerTransaction != null) {
			try {
				ContentTypeHeader contentTypeHeader = (ContentTypeHeader) request.getHeader("Content-Type");
				bus.post(new SendingInformationSuccess(callId, serverTransaction.getDialog(), (String) request.getContent(), contentTypeHeader));
			} catch (Exception e) {
				logger.error("Unable to parse Content-Type header");
			}
			return;
		} else {
			bus.post(new SendingInformationFailed("Unable to retrieve content and Content-Type", callId));
		}
		throw new RequestCouldNotBeAddressed();
	}

	private boolean tryHandlingRequestGenerically(RequestMethod method, Request request,
			ServerTransaction serverTransaction) {
		if (!methodIsAllowed(method)) {
			logger.warn("{} request is not allowed.", method);
			//TODO add Allow header with supported methods.
			List<Header> allowedMethods = new LinkedList<>();
			RequestMethod acceptedMethods[] = {
					RequestMethod.CANCEL,
					RequestMethod.OPTIONS,
					RequestMethod.INVITE,
					RequestMethod.ACK,
					RequestMethod.BYE
			};
			for (RequestMethod acceptedMethod : acceptedMethods) {
				try {
					AllowHeader allowHeader = headerMaker
							.createAllowHeader(acceptedMethod.toString());
					allowedMethods.add(allowHeader);
				} catch (ParseException ignore) {}
			}
			if (doSendResponse(Response.METHOD_NOT_ALLOWED, method,
					request, serverTransaction, allowedMethods
					.toArray(new Header[allowedMethods.size()])) != null) {
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
			case MESSAGE:
			case INFO:
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

	private void handleOptionsRequest(Request request, ServerTransaction serverTransaction) {
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		ServerTransaction newServerTransaction = doSendResponse(Response.OK,
				RequestMethod.OPTIONS, request, serverTransaction);
		if (newServerTransaction != null) {
			RequestMethod method = RequestMethod.OPTIONS;
			if (!putOfferOrAnswerIntoResponseIfApplicable(method, callId, request,
					Response.UNSUPPORTED_MEDIA_TYPE)) {
				bus.post(new ReceivingOptionsRequestFailed("Unsupported Media Type", callId));
				//FIXME enhance this reason above.
			}
			return;
		}
		throw new RequestCouldNotBeAddressed();
	}
	
	private void handleMessageRequest(Request request, ServerTransaction serverTransaction) {
		boolean withinDialog = serverTransaction != null;
		if (withinDialog) {
			handleReSendMessageRequest(request, serverTransaction);
			return;
		}
		//TODO also consider supporting multicast conferences, by sending silent 2xx responses
		//when appropriate, by using identifiers within the SDP session description.
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		List<Header> additionalHeaders = new ArrayList<>();
		RequestMethod acceptedMethods[] = {
				RequestMethod.CANCEL,
				RequestMethod.OPTIONS,
				RequestMethod.INVITE,
				RequestMethod.ACK,
				RequestMethod.BYE,
				RequestMethod.MESSAGE,
				RequestMethod.INFO
		};
		for (RequestMethod method : acceptedMethods) {
			try {
				AllowHeader allowHeader = headerMaker.createAllowHeader(method.toString());
				additionalHeaders.add(allowHeader);
			} catch (ParseException ignore) {}
		}
		ServerTransaction newServerTransaction = doSendResponse(Response.OK, RequestMethod.MESSAGE,
				request, serverTransaction, additionalHeaders.toArray(new Header[additionalHeaders.size()]));
		if (newServerTransaction != null) {
			try {
				ContentTypeHeader contentTypeHeader = (ContentTypeHeader) request.getHeader("Content-Type");
				if (null != contentTypeHeader) {
					try {
						logger.info("CONTENT: {}", new String(request.getRawContent()));
						if (null != request.getRawContent()) {
							bus.post(new MessageReceived(callId,
									(null != serverTransaction ? serverTransaction.getDialog() : null),
									new String(request.getRawContent()), contentTypeHeader));
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					logger.info("CONTENT: {}", new String(request.getRawContent()));
					Iterator it = request.getHeaderNames();
					while (it.hasNext()) {
						logger.info("HEADER: {}", it.next());
					}
					logger.error("Unable to parse Content-Type header");
				}
			} catch (Exception e) {
				e.printStackTrace();
				logger.error("Unable to parse Content-Type header");
			}
			return;
		} else {
			bus.post(new ReceivingMessageFailed("Unable to retrieve content and Content-Type", callId));
		}
		throw new RequestCouldNotBeAddressed();
	}

	private void handleReSendMessageRequest(Request request, ServerTransaction serverTransaction) {
		// //FIXME later implement RESEND MESSAGE as well.
		throw new RequestCouldNotBeAddressed();
	}

	private void handleInviteRequest(Request request, ServerTransaction serverTransaction) {
		boolean withinDialog = serverTransaction != null;
		if (withinDialog) {
			handleReinviteRequest(request, serverTransaction);
			return;
		}
		//TODO also consider supporting multicast conferences, by sending silent 2xx responses
		//when appropriate, by using identifiers within the SDP session description.
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		List<Header> additionalHeaders = new ArrayList<>();
		RequestMethod acceptedMethods[] = {
				RequestMethod.CANCEL,
				RequestMethod.OPTIONS,
				RequestMethod.INVITE,
				RequestMethod.ACK,
				RequestMethod.BYE
		};
		for (RequestMethod method : acceptedMethods) {
			try {
				AllowHeader allowHeader = headerMaker.createAllowHeader(method.toString());
				additionalHeaders.add(allowHeader);
			} catch (ParseException ignore) {}
		}
		ServerTransaction newServerTransaction = doSendResponse(Response.RINGING, RequestMethod.INVITE,
				request, serverTransaction, additionalHeaders.toArray(new Header[additionalHeaders.size()]));
		if (newServerTransaction != null) {
			bus.post(new CallInvitationArrived(callId, newServerTransaction));
			RequestMethod method = RequestMethod.INVITE;
			if (!putOfferOrAnswerIntoResponseIfApplicable(method, callId, request,
					Response.UNSUPPORTED_MEDIA_TYPE)) {
				doSendResponse(Response.UNSUPPORTED_MEDIA_TYPE, method,
						request, newServerTransaction, false);
				bus.post(new CallInvitationCanceled("Call invitation failed because media types "
						+ "negotiation between callee and caller failed.", callId, false));
			}
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
		List<Header> additionalHeaders = new ArrayList<>();
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
		additionalHeaders.add(contactHeader);
		RequestMethod acceptedMethods[] = {
				RequestMethod.CANCEL,
				RequestMethod.OPTIONS,
				RequestMethod.INVITE,
				RequestMethod.ACK,
				RequestMethod.BYE
		};
		for (RequestMethod acceptedMethod : acceptedMethods) {
			try {
				AllowHeader allowHeader = headerMaker
						.createAllowHeader(acceptedMethod.toString());
				additionalHeaders.add(allowHeader);
			} catch (ParseException ignore) {}
		}
		if (doSendResponse(Response.OK, method, request, serverTransaction,
				additionalHeaders.toArray(new Header[additionalHeaders.size()])) != null) {
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
			bus.post(new CallInvitationCanceled("Call invitation rejected by the callee or callee" +
					" is currently busy and couldn't take another call.", callId, false));
			return true;
		}
		return false;
	}

	private ServerTransaction doSendResponse(int statusCode, RequestMethod method,
			Request request, ServerTransaction serverTransaction, Header... additionalHeaders) {
		return doSendResponse(statusCode, method, request, serverTransaction, true, additionalHeaders);
	}

	private ServerTransaction doSendResponse(int statusCode, RequestMethod method, Request request,
			ServerTransaction serverTransaction, boolean addSessionPayload, Header... additionalHeaders) {
		if (method == RequestMethod.UNKNOWN) {
			logger.debug("[doSendResponse(int, RequestMethod, Request, " +
					"ServerTransaction, Header...)] method forbidden for " +
					"{} requests.", method);
			return null;
		}
		ServerTransaction newServerTransaction = serverTransaction;
		if (newServerTransaction == null) {
			try {
				newServerTransaction = provider.getNewServerTransaction(request);
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
			}
		}
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		try {
			Response response = messenger.createResponse(statusCode, request);
			for (Header header : additionalHeaders) {
				response.addHeader(header);
			}
			boolean isSuccessResponse = Constants.getResponseClass(response
					.getStatusCode()) == ResponseClass.SUCCESS;
			if (addSessionPayload && isDialogCreatingRequest(method) && isSuccessResponse) {
				if (!putOfferOrAnswerIntoResponseIfApplicable(method, callId,
						request, response)) {
					doSendResponse(Response.UNSUPPORTED_MEDIA_TYPE, method,
							request, newServerTransaction, false, additionalHeaders);
					bus.post(new CallInvitationCanceled("Call invitation failed because media types "
							+ "negotiation between callee and caller failed.", callId, false));
					return null;
				}
			}
			logger.info("Sending {} response to {} request...", statusCode, method);
			logger.debug("Response Dump:\n{}\n", response);
			newServerTransaction.sendResponse(response);
			logger.info("{} response sent.", statusCode);
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

	private boolean isDialogCreatingRequest(RequestMethod method) {
		switch (method) {
			case OPTIONS:
			case INVITE:
				return true;
			default:
				return false;
		}
	}

	private boolean putOfferOrAnswerIntoResponseIfApplicable(RequestMethod method, String callId,
			Request request, int statusCode) {
		return putOfferOrAnswerIntoResponseIfApplicable(method, callId, request, statusCode, null);
	}

	private boolean putOfferOrAnswerIntoResponseIfApplicable(RequestMethod method, String callId,
			Request request, Response response) {
		return putOfferOrAnswerIntoResponseIfApplicable(method, callId, request,
				response.getStatusCode(), response);
	}

	private boolean putOfferOrAnswerIntoResponseIfApplicable(RequestMethod method, String callId,
			Request request, int statusCode, Response response) {
		if (request.getContent() == null) {
			SipuadaPlugin sessionPlugin = sessionPlugins.get(method);
			if (sessionPlugin == null) {
				return true;
			}
			SessionDescription offer = null;
			try {
				offer = sessionPlugin.generateOffer(callId, method);
			} catch (Throwable unexpectedException) {
				logger.error("Bad plug-in crashed while trying to generate offer to be inserted " +
						"into {} response to {} request.", statusCode, method, unexpectedException);
				return true;
			}
			if (offer == null) {
				return true;
			}
			try {
				if (response != null) {
					logger.info("Received {} request with no offer, so sending own offer along {} response.",
							method, statusCode);
					response.setContent(offer, headerMaker.createContentTypeHeader("application", "sdp"));
					logger.info("Plug-in-generated offer {{}} by {} inserted into {} response to {} request.",
							offer.toString(), sessionPlugin.getClass().getName(), statusCode, method);
				}
			} catch (ParseException parseException) {
				logger.error("Plug-in-generated offer {{}} by {} could not be inserted into {} response to " +
						"{} request.", offer.toString(), sessionPlugin.getClass().getName(),
						statusCode, method, parseException);
			}
			return true;
		}
		else {
			SessionDescription offer;
			try {
				offer = SdpFactory.getInstance()
						.createSessionDescriptionFromString(new String(request.getRawContent()));
			} catch (SdpParseException parseException) {
				logger.error("Offer arrived in {} request, but could not be properly parsed, " +
						"so it was discarded.", method, parseException);
				return false;
			}
			if (response != null) {
				logger.info("Received {} request with a offer, so will try sending an answer along {} response.",
						method, statusCode);
			}
			SipuadaPlugin sessionPlugin = sessionPlugins.get(method);
			if (sessionPlugin == null) {
				logger.error("No plug-in available to generate valid answer to offer {{}} in {} request.",
						offer.toString(), method);
				return false;
			}
			SessionDescription answer = null;
			try {
				answer = sessionPlugin.generateAnswer(callId, method, offer);
			} catch (Throwable unexpectedException) {
				logger.error("Bad plug-in crashed while trying to generate answer to be inserted " +
						"into {} response to {} request.", statusCode, method, unexpectedException);
				return false;
			}
			if (answer == null) {
				logger.error("Plug-in {} could not generate valid answer to offer {{}} in {} request.",
						sessionPlugin.getClass().getName(), offer.toString(), method);
				return false;
			}
			try {
				if (response != null) {
					response.setContent(answer, headerMaker.createContentTypeHeader("application", "sdp"));
					logger.info("Plug-in-generated answer {{}} to offer {{}} by {} inserted into {} response" +
							" to {} request.", answer.toString(), offer.toString(), sessionPlugin.getClass().getName(),
							statusCode, method);
				}
			} catch (ParseException parseException) {
				logger.error("Plug-in-generated answer {{}} to offer {{}} by {} could not be inserted into " +
						"{} response to {} request.", answer.toString(), offer.toString(),
						sessionPlugin.getClass().getName(), statusCode, method, parseException);
				return false;
			}
			return true;
		}
	}

}
