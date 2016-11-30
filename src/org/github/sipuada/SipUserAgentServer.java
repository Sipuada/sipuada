package org.github.sipuada;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.Constants.ResponseClass;
import org.github.sipuada.SessionManager.SipUserAgentRole;
import org.github.sipuada.events.CallInvitationArrived;
import org.github.sipuada.events.CallInvitationCanceled;
import org.github.sipuada.events.EarlyMediaSessionEstablished;
import org.github.sipuada.events.EstablishedCallFailed;
import org.github.sipuada.events.EstablishedCallFinished;
import org.github.sipuada.events.EstablishedCallStarted;
import org.github.sipuada.events.FinishEstablishedCall;
import org.github.sipuada.events.MessageReceived;
import org.github.sipuada.exceptions.InternalJainSipException;
import org.github.sipuada.exceptions.RequestCouldNotBeAddressed;
import org.github.sipuada.plugins.SipuadaPlugin;
import org.github.sipuada.plugins.SipuadaPlugin.SessionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import android.gov.nist.javax.sip.Utils;
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
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.ExtensionHeader;
import android.javax.sip.header.FromHeader;
import android.javax.sip.header.Header;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.RequireHeader;
import android.javax.sip.header.SupportedHeader;
import android.javax.sip.header.ToHeader;
import android.javax.sip.header.WarningHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class SipUserAgentServer {

	private final Logger logger = LoggerFactory.getLogger(SipUserAgentServer.class);

	private final String stackName;
	private final EventBus bus;
	private final SipProvider provider;
	private final MessageFactory messenger;
	private final HeaderFactory headerMaker;
	private final AddressFactory addressMaker;
	private final SessionManager sessionManager;

	private final String username;
	private final String localIp;
	private final int localPort;
	private final String transport;

	public SipUserAgentServer(String name, EventBus eventBus, SipProvider sipProvider,
			Map<RequestMethod, SipuadaPlugin> plugins, MessageFactory messageFactory,
			HeaderFactory headerFactory, AddressFactory addressFactory,
			String... credentialsAndAddress) {
		stackName = name;
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
		transport = credentialsAndAddress.length > 3 && credentialsAndAddress[3] != null ?
			credentialsAndAddress[3] : "TCP";
		sessionManager = new SessionManager(plugins, bus,
			SipUserAgentRole.UAS, localIp, headerMaker);
		logger.debug("UAS {} created, bound to {}:{}.", stackName, localIp, localPort);
	}

	public void processRequest(RequestEvent requestEvent) {
		ServerTransaction serverTransaction = requestEvent.getServerTransaction();
		Request request = requestEvent.getRequest();
		RequestMethod method = RequestMethod.UNKNOWN;
		try {
			method = RequestMethod.valueOf(request.getMethod());
		} catch (IllegalArgumentException ignore) {
			ignore.printStackTrace();
		}
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
					case PRACK:
						handlePrackRequest(request, serverTransaction);
						break;
					case BYE:
						handleByeRequest(request, serverTransaction);
						break;
					case MESSAGE:
						handleMessageRequest(request, serverTransaction);
						break;
//					case UPDATE:
//						handleUpdateRequest(request, serverTransaction);
//						break;
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

			for (RequestMethod acceptedMethod : SipUserAgent.ACCEPTED_METHODS) {
				try {
					AllowHeader allowHeader = headerMaker
							.createAllowHeader(acceptedMethod.toString());
					allowedMethods.add(allowHeader);
				} catch (ParseException ignore) {
					ignore.printStackTrace();
				}
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

	private boolean methodIsAllowed(final RequestMethod method) {
		for (RequestMethod requestMethod : SipUserAgent.ACCEPTED_METHODS) {
			if (requestMethod == method) {
				return true;
			}
		}
		return false;
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
			} else if (requestUriUser.equals(identityUser)) {
				logger.info("Request destined to (Request URI = {}) arrived but this UAC" +
					" is bound to {}@{}, so assuming that's another viable path to this UAC.",
					requestUri, identityUser, identityHost);
				shouldNotFound = false;
			} else {
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

		for (RequestMethod method : SipUserAgent.ACCEPTED_METHODS) {
			try {
				AllowHeader allowHeader = headerMaker.createAllowHeader(method.toString());
				additionalHeaders.add(allowHeader);
			} catch (ParseException ignore) {
				ignore.printStackTrace();
			}
		}
		boolean earlyMediaIsSupported = false;
		@SuppressWarnings("unchecked")
		ListIterator<Header> supportedHeaders = request.getHeaders(SupportedHeader.NAME);
		while (supportedHeaders != null && supportedHeaders.hasNext()) {
			SupportedHeader supportedHeader = (SupportedHeader) supportedHeaders.next();
			if (supportedHeader.getOptionTag().toLowerCase()
					.contains(SessionType.EARLY.getDisposition())) {
				earlyMediaIsSupported = true;
				break;
			}
		}
		RequestMethod method = RequestMethod.INVITE;
		final int provisionalResponse;
		final SessionType sessionType;
		boolean requestHasSdpWithNoContentDisposition
			= sessionManager.messageHasSdp(request, false);
		if (earlyMediaIsSupported || requestHasSdpWithNoContentDisposition) {
			provisionalResponse = Response.SESSION_PROGRESS;
			sessionType = SessionType.EARLY;
			try {
				SupportedHeader supportedHeader = headerMaker
					.createSupportedHeader(SessionType.EARLY.getDisposition());
				additionalHeaders.add(supportedHeader);
			} catch (ParseException ignore) {
				ignore.printStackTrace();
			}
		} else {
			provisionalResponse = Response.RINGING;
			sessionType = SessionType.REGULAR;
		}
		ServerTransaction newServerTransaction = doSendProvisionalResponse
			(provisionalResponse, method, request, serverTransaction, sessionType,
			additionalHeaders.toArray(new Header[additionalHeaders.size()]));
		FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
		String remoteUser = fromHeader.getAddress().getURI().toString().split("@")[0].split(":")[1];
		String remoteDomain = fromHeader.getAddress().getURI().toString().split("@")[1];
		if (newServerTransaction != null) {
			bus.post(new CallInvitationArrived(callId, newServerTransaction,
				remoteUser, remoteDomain, provisionalResponse == Response.SESSION_PROGRESS));
			return;
		}
		throw new RequestCouldNotBeAddressed();
	}

	private void handleReinviteRequest(Request request, ServerTransaction serverTransaction) {
		//FIXME later implement REINVITE as well.
		throw new RequestCouldNotBeAddressed();
	}

	private void handleAckRequest(Request ackRequest, ServerTransaction serverTransaction) {
		CallIdHeader callIdHeader = (CallIdHeader) ackRequest.getHeader(CallIdHeader.NAME);
		final String callId = callIdHeader.getCallId();
		final Dialog dialog = serverTransaction.getDialog();
		logger.debug("$ About to perform OFFER/ANSWER exchange step "
			+ "expecting to setup regular session! $");
		boolean sendByeRightAway = !sessionManager.performOfferAnswerExchangeStep
			(callId, null, null, null, null, null, ackRequest);
		bus.post(new EstablishedCallStarted(callId, dialog));
		logger.info("New call established: {}.", callId);
		if (sendByeRightAway) {
			new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						Thread.sleep(3000);
					} catch (InterruptedException ignore) {
						ignore.printStackTrace();
					}
					bus.post(new FinishEstablishedCall
						("Media types negotiation failed.", callId));
				}

			}).start();
		}
	}

	private void handlePrackRequest(Request prackRequest, ServerTransaction serverTransaction) {
		if (doSendResponse(Response.OK, RequestMethod.PRACK,
				prackRequest, serverTransaction) == null) {
			throw new RequestCouldNotBeAddressed();
		}
	}

	private void handleByeRequest(Request request, ServerTransaction serverTransaction) {
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		if (doSendResponse(Response.OK, RequestMethod.BYE,
				request, serverTransaction) != null) {
			ExtensionHeader extensionHeader = (ExtensionHeader) request
					.getHeader(SipUserAgent.X_FAILURE_REASON_HEADER);
			if (extensionHeader != null) {
				String reason = extensionHeader.getValue();
				if (reason != null) {
					bus.post(new EstablishedCallFailed(reason, callId));
					return;
				}
			}
			bus.post(new EstablishedCallFinished(callId));
			return;
		}
		throw new RequestCouldNotBeAddressed();
	}

	@SuppressWarnings("unchecked")
	private void handleMessageRequest(Request request, ServerTransaction serverTransaction) {
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		List<Header> allowHeaders = new ArrayList<>();
		for (RequestMethod method : SipUserAgent.ACCEPTED_METHODS) {
			try {
				AllowHeader allowHeader = headerMaker.createAllowHeader(method.toString());
				allowHeaders.add(allowHeader);
			} catch (ParseException ignore) {
				ignore.printStackTrace();
			}
		}
		ServerTransaction newServerTransaction = doSendResponse(Response.OK, RequestMethod.MESSAGE, request,
			serverTransaction, allowHeaders.toArray(new Header[allowHeaders.size()]));
		if (newServerTransaction != null) {
			ContentTypeHeader contentTypeHeader = (ContentTypeHeader) request.getHeader(ContentTypeHeader.NAME);
			if (contentTypeHeader == null) {
				try {
					contentTypeHeader = headerMaker.createContentTypeHeader("text", "plain");
				} catch (ParseException ignore) {
					ignore.printStackTrace();
				}
			}
			byte[] rawContent = request.getRawContent();
			String content = "";
			if (rawContent != null) {
				content = new String(rawContent);
			}
			FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
			String remoteUser = fromHeader.getAddress().getURI().toString().split("@")[0].split(":")[1];
			String remoteDomain = fromHeader.getAddress().getURI().toString().split("@")[1];
			Iterator<String> headerNamesIterator = request.getHeaderNames();
			List<Header> additionalHeaders = new ArrayList<>();
			while (headerNamesIterator != null && headerNamesIterator.hasNext()) {
				Iterator<Header> headers = request.getHeaders(headerNamesIterator.next());
				while (headers.hasNext()) {
					additionalHeaders.add(headers.next());
				}
			}
			bus.post(new MessageReceived(callId, remoteUser, remoteDomain, content, contentTypeHeader,
				additionalHeaders.toArray(new Header[additionalHeaders.size()])));
			return;
		}
		throw new RequestCouldNotBeAddressed();
	}

//	private void handleUpdateRequest(Request request, ServerTransaction serverTransaction) {
//		List<Header> allowHeaders = new ArrayList<>();
//		for (RequestMethod method : SipUserAgent.ACCEPTED_METHODS) {
//			try {
//				AllowHeader allowHeader = headerMaker.createAllowHeader(method.toString());
//				allowHeaders.add(allowHeader);
//			} catch (ParseException ignore) {
//				ignore.printStackTrace();
//			}
//		}
//		if (doSendResponse(Response.OK, RequestMethod.UPDATE, request,
//			serverTransaction, allowHeaders.toArray(new Header[allowHeaders.size()])) != null) {
//			return;
//		}
//		throw new RequestCouldNotBeAddressed();
//	}

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
		try {
			contactUri.setTransportParam(transport.toUpperCase());
			contactUri.setParameter("ob", null);
		} catch (ParseException ignore) {
			ignore.printStackTrace();
		}
		Address contactAddress = addressMaker.createAddress(contactUri);
		ContactHeader contactHeader = headerMaker.createContactHeader(contactAddress);
//		try {
//			contactHeader.setExpires(60);
//		} catch (ParseException ignore) {
//			ignore.printStackTrace();
//		}
		additionalHeaders.add(contactHeader);

		for (RequestMethod acceptedMethod : SipUserAgent.ACCEPTED_METHODS) {
			try {
				AllowHeader allowHeader = headerMaker
						.createAllowHeader(acceptedMethod.toString());
				additionalHeaders.add(allowHeader);
			} catch (ParseException ignore) {
				ignore.printStackTrace();
			}
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
		return doSendResponse(statusCode, method, request, serverTransaction, true, false, SessionType.REGULAR, additionalHeaders);
	}

	private ServerTransaction doSendProvisionalResponse(int statusCode, RequestMethod method,
			Request request, ServerTransaction serverTransaction, SessionType type, Header... additionalHeaders) {
		return doSendResponse(statusCode, method, request, serverTransaction, true, true, type, additionalHeaders);
	}

	private ServerTransaction doSendResponse(int statusCode, RequestMethod method,
			Request request, ServerTransaction serverTransaction, boolean addSessionPayload,
			boolean responseIsProvisional, SessionType type, Header... additionalHeaders) {
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
			boolean reliableProvisionalResponseRequiredOrSupported = false;
			@SuppressWarnings("unchecked")
			ListIterator<Header> requireHeaders = request.getHeaders(RequireHeader.NAME);
			while (requireHeaders != null && requireHeaders.hasNext()) {
				RequireHeader requireHeader = (RequireHeader) requireHeaders.next();
				if (requireHeader.getOptionTag().toLowerCase().trim().contains("100rel")) {
					reliableProvisionalResponseRequiredOrSupported = true;
				}
			}
			@SuppressWarnings("unchecked")
			ListIterator<Header> supportedHeaders = request.getHeaders(SupportedHeader.NAME);
			while (supportedHeaders != null && supportedHeaders.hasNext()) {
				SupportedHeader supportedHeader = (SupportedHeader) supportedHeaders.next();
				if (supportedHeader.getOptionTag().toLowerCase().trim().contains("100rel")) {
					reliableProvisionalResponseRequiredOrSupported = true;
				}
			}
			if (statusCode == Response.TRYING) {
				reliableProvisionalResponseRequiredOrSupported = false;
			}
			final Response response;
			if (!responseIsProvisional || !reliableProvisionalResponseRequiredOrSupported) {
				response = messenger.createResponse(statusCode, request);
			} else {
				response = newServerTransaction.getDialog()
					.createReliableProvisionalResponse(statusCode);
				ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
				if (toHeader.getTag() == null || toHeader.getTag().trim().isEmpty()) {
					toHeader.setTag(Utils.getInstance().generateTag());
				}
				response.setHeader(toHeader);
				SipURI contactUri = addressMaker.createSipURI(username, localIp);
				contactUri.setPort(localPort);
				contactUri.setTransportParam(transport.toUpperCase());
				contactUri.setParameter("ob", null);
				Address contactAddress = addressMaker.createAddress(contactUri);
				ContactHeader contactHeader = headerMaker.createContactHeader(contactAddress);
				response.addHeader(contactHeader);
			}
			for (Header header : additionalHeaders) {
				response.addHeader(header);
			}
			ResponseClass responseClass = Constants.getResponseClass(response.getStatusCode());
			boolean isSuccessOrProvisionalEarlyMediaResponse = responseClass == ResponseClass.SUCCESS
				|| (responseClass == ResponseClass.PROVISIONAL && type == SessionType.EARLY);
			if (addSessionPayload && isDialogCreatingOrSessionUpdatingRequest(method)
					&& isSuccessOrProvisionalEarlyMediaResponse) {
//				if (method == RequestMethod.UPDATE) {
//					type = sessionManager.isSessionOngoing(callId, SessionType.EARLY)
//						? SessionType.EARLY : SessionType.REGULAR;
//				}
				logger.debug("$ About to perform OFFER/ANSWER exchange step "
					+ "expecting to update existing session! $");
				if (!putOfferOrAnswerIntoResponseIfApplicable
						(callId, request, method, response, responseClass)) {
					final String errorMessage;
					if (method == RequestMethod.UPDATE) {
						statusCode = Response.NOT_ACCEPTABLE_HERE;
						errorMessage = "Session update failed because media types negotiation"
							+ " between callee and caller failed.";
						WarningHeader warningHeader = headerMaker
							.createWarningHeader(stackName, statusCode, errorMessage);
						Header[] updatedHeaders = new Header[additionalHeaders.length + 1];
						updatedHeaders[0] = warningHeader;
						for (int i=1; i<=additionalHeaders.length; i++) {
							updatedHeaders[i] = additionalHeaders[i-1];
						}
						additionalHeaders = updatedHeaders;
					} else {
						statusCode = Response.UNSUPPORTED_MEDIA_TYPE;
						errorMessage = "Call invitation failed because media types "
							+ "negotiation between callee and caller failed.";
					}
					doSendResponse(statusCode, method, request, newServerTransaction,
						false, false, type, additionalHeaders);
					if (method != RequestMethod.UPDATE
							|| (!sessionManager.isSessionOngoing(callId, SessionType.REGULAR)
							&& !sessionManager.isSessionOngoing(callId, SessionType.EARLY))) {
						bus.post(new CallInvitationCanceled(errorMessage, callId, false));
					}
					return null;
				} else if (method == RequestMethod.PRACK) {
					bus.post(new EarlyMediaSessionEstablished(callId));
					logger.info("Early media session established: {}.", callId);
				}
			}
			logger.info("Sending {} response to {} request (from {}:{})...", statusCode, method,
				localIp, localPort);
			logger.debug("Response Dump:\n{}\n", response);
			try {
				if (!responseIsProvisional || !reliableProvisionalResponseRequiredOrSupported) {
					newServerTransaction.sendResponse(response);
				} else {
					newServerTransaction.getDialog().sendReliableProvisionalResponse(response);
				}
			} catch (RuntimeException lowLevelStackFailed) {
				logger.error("{} response to {} request could not be sent due to a " +
					"JAINSIP-level failure.", statusCode, method, lowLevelStackFailed);
				throw new InternalJainSipException("Severe JAINSIP-level failure!",
					lowLevelStackFailed);
			}
			logger.info("{} response sent.", statusCode);
			return newServerTransaction;
		} catch (ParseException ignore) {
			ignore.printStackTrace();
		} catch (InvalidArgumentException ignore) {
			ignore.printStackTrace();
		} catch (SipException responseCouldNotBeSent) {
			logger.debug("{} response could not be sent to {} request: {} {}.",
				statusCode, method, responseCouldNotBeSent.getMessage(),
				responseCouldNotBeSent.getCause() == null ? "" :
				"(" + responseCouldNotBeSent.getCause().getMessage() + ")");
		}
		return null;
	}

	private boolean isDialogCreatingOrSessionUpdatingRequest(RequestMethod method) {
		switch (method) {
			case OPTIONS:
			case INVITE:
			case PRACK:
			case UPDATE:
				return true;
			default:
				return false;
		}
	}

	private boolean putOfferOrAnswerIntoResponseIfApplicable(String callId, Request request,
			RequestMethod method, Response response, ResponseClass responseClass) {
		if (method == RequestMethod.INVITE && responseClass == ResponseClass.SUCCESS) {
			logger.debug("$ About to perform OFFER/ANSWER exchange step "
				+ "expecting to put offer into Res or put answer into Res! $");
			return sessionManager.performOfferAnswerExchangeStep(callId,
				request, null, null, null, response, null);
		} else if (method == RequestMethod.INVITE && responseClass == ResponseClass.PROVISIONAL) {
			logger.debug("$ About to perform OFFER/ANSWER exchange step "
				+ "expecting to put offer into ProvRes or put answer into ProvRes! $");
			return sessionManager.performOfferAnswerExchangeStep(callId,
				request, response, null, null, null, null);
		} else if (method == RequestMethod.PRACK) {
			logger.debug("$ About to perform OFFER/ANSWER exchange step "
				+ "expecting to put offer into PrackRes or put answer into PrackRes! $");
			return sessionManager.performOfferAnswerExchangeStep(callId,
				null, null, request, response, null, null);
		}
		//TODO add case for UPDATE requests.
		return true;
	}

}
