package org.github.sipuada;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
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
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class SipUserAgentServer {

	private final Logger logger = LoggerFactory.getLogger(SipUserAgentServer.class);

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

	private final Map<String, Request> requestsHandled = new HashMap<>();
	private final Map<String, Response> responsesSent = new HashMap<>();

	public SipUserAgentServer(EventBus eventBus, SipProvider sipProvider, Map<RequestMethod, SipuadaPlugin> plugins,
			MessageFactory messageFactory, HeaderFactory headerFactory, AddressFactory addressFactory,
			String... credentialsAndAddress) {
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
		sessionManager = new SessionManager(plugins,
			SipUserAgentRole.UAS, localIp, headerMaker);
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
		boolean requestHasSdpWithNoContentDisposition = sessionManager.messageHasSdp(request, false);
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
//		boolean sendByeRightAway = false;
//		if (request.getContent() != null && request.getContentDisposition().getDispositionType()
//				.toLowerCase().trim().equals(SessionType.REGULAR.getDisposition())) {
//			sendByeRightAway = true;
//			try {
//				SipuadaPlugin sessionPlugin = sessionPlugins.get(RequestMethod.INVITE);
//				if (sessionPlugin != null) {
//					SessionDescription answer = SdpFactory.getInstance()
//						.createSessionDescriptionFromString
//						(new String(request.getRawContent()));
//					try {
//						logger.debug("* UAS will process {} answer \n{}\n in context "
//							+ "of call {}! *", SessionType.REGULAR, answer, callId);
//						sessionPlugin.receiveAnswerToAcceptedOffer
//							(callId, SessionType.REGULAR, answer);
//						sendByeRightAway = false;
//					} catch (Throwable unexpectedException) {
//						logger.error("Bad plug-in crashed while receiving {} answer "
//							+ "that arrived alongside {} to 2xx response to {} request."
//							+ " The UAS will ask the UAC to terminate the dialog right away.",
//							SessionType.REGULAR, RequestMethod.ACK, RequestMethod.INVITE,
//							unexpectedException);
//					}
//				} else {
//					sendByeRightAway = false;
//				}
//			} catch (SdpParseException parseException) {
//				logger.error("{} answer arrived in {} to 2xx response to {} request, "
//					+ "but could not be properly parsed, so it was discarded. "
//					+ "The UAS will ask the UAC to terminate the dialog right away.",
//					SessionType.REGULAR, RequestMethod.ACK, RequestMethod.INVITE,
//					parseException);
//			}
//		}
		logger.debug("$ About to perform OFFER/ANSWER exchange step "
			+ "expecting to setup regular session! $");
		boolean sendByeRightAway = /*sessionPlugins.get(RequestMethod.INVITE) != null
			&& */!sessionManager.performOfferAnswerExchangeStep
				(callId, SessionType.REGULAR, requestsHandled.get(callId),
					responsesSent.get(callId), ackRequest);
		bus.post(new EstablishedCallStarted(callId, serverTransaction.getDialog()));
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
		CallIdHeader callIdHeader = (CallIdHeader) prackRequest.getHeader(CallIdHeader.NAME);
		final String callId = callIdHeader.getCallId();
		Request originalRequest = requestsHandled.get(callId);
		Response originalResponse = responsesSent.get(callId);
		if (doSendResponse(Response.OK, RequestMethod.PRACK,
				prackRequest, serverTransaction) != null) {
//			if (request.getContent() != null && request.getContentDisposition().getDispositionType()
//					.toLowerCase().trim().equals(SessionType.EARLY.getDisposition())) {
//				try {
//					SipuadaPlugin sessionPlugin = sessionPlugins.get(RequestMethod.INVITE);
//					if (sessionPlugin != null) {
//						SessionDescription answer = SdpFactory.getInstance()
//							.createSessionDescriptionFromString
//							(new String(request.getRawContent()));
//						try {
//							logger.debug("* UAS will process {} answer \n{}\n in context"
//								+ " of call {}! *", SessionType.EARLY, answer, callId);
//							sessionPlugin.receiveAnswerToAcceptedOffer
//								(callId, SessionType.EARLY, answer);
//							bus.post(new EarlyMediaSessionEstablished(callId));
//							logger.info("Early media session established: {}.", callId);
//						} catch (Throwable unexpectedException) {
//							logger.error("Bad plug-in crashed while receiving {} answer "
//								+ "that arrived alongside {} to 1xx response to {} request."
//								+ " The UAS will terminate the early media session right away.",
//								SessionType.EARLY, RequestMethod.PRACK, RequestMethod.INVITE,
//								unexpectedException);
//						}
//					}
//				} catch (SdpParseException parseException) {
//					logger.error("{} answer arrived in {} to 1xx response to {} request, "
//						+ "but could not be properly parsed, so it was discarded. "
//						+ "The UAS will terminate the early media session right away.",
//						SessionType.EARLY, RequestMethod.PRACK, RequestMethod.INVITE,
//						parseException);
//				}
//			}
			logger.debug("$ About to perform OFFER/ANSWER exchange step "
				+ "expecting to setup early media session! $");
			if (sessionManager.performOfferAnswerExchangeStep
					(callId, SessionType.EARLY, originalRequest, originalResponse, prackRequest)) {
				bus.post(new EarlyMediaSessionEstablished(callId));
				logger.info("Early media session established: {}.", callId);
			}
			return;
		}
		throw new RequestCouldNotBeAddressed();
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
			if (addSessionPayload && isDialogCreatingRequest(method)
					&& isSuccessOrProvisionalEarlyMediaResponse) {
				if (!putOfferOrAnswerIntoResponseIfApplicable(method,
						callId, type, request, response)) {
					doSendResponse(Response.UNSUPPORTED_MEDIA_TYPE, method,
						request, newServerTransaction, false, false, type, additionalHeaders);
					bus.post(new CallInvitationCanceled("Call invitation failed because media types "
						+ "negotiation between callee and caller failed.", callId, false));
					return null;
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
				requestsHandled.put(callId, request);
				responsesSent.put(callId, response);
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

	private boolean isDialogCreatingRequest(RequestMethod method) {
		switch (method) {
			case OPTIONS:
			case INVITE:
				return true;
			default:
				return false;
		}
	}

	private boolean putOfferOrAnswerIntoResponseIfApplicable(RequestMethod method,
			String callId, SessionType type, Request request, Response response) {
		logger.debug("$ About to perform OFFER/ANSWER exchange step "
			+ "expecting to put offer into Res or put answer into Res! $");
		return sessionManager.performOfferAnswerExchangeStep(callId, type, request, response, null);
//		if (request.getContent() == null || !request.getContentDisposition()
//				.getDispositionType().toLowerCase().trim().equals(type.getDisposition())) {
//			SipuadaPlugin sessionPlugin = sessionPlugins.get(method);
//			if (sessionPlugin == null) {
//				logger.info("No plug-in available to generate {} offer to be "
//					+ "inserted into {} response to {} request.", type, statusCode, method);
//				return true;
//			}
//			SessionDescription offer = null;
//			try {
//				offer = sessionPlugin.generateOffer(callId, type, method, localIp);
//				logger.debug("* UAS just generated {} offer \n{}\n in "
//					+ "context of call {}! *", type, offer, callId);
//			} catch (Throwable unexpectedException) {
//				logger.error("Bad plug-in crashed while trying to generate {} offer to be inserted " +
//					"into {} response to {} request.", type, statusCode, method, unexpectedException);
//				return true;
//			}
//			if (offer == null) {
//				logger.info("Plug-in {} generated no {} offer to be inserted into {} response to {} request.",
//					sessionPlugin.getClass().getName(), type, statusCode, method);
//				return true;
//			}
//			try {
//				if (response != null) {
//					logger.info("Received {} request with no {} offer, so sending own {} offer along {} response.",
//						method, type, type, statusCode);
//					response.setContent(offer, headerMaker.createContentTypeHeader("application", "sdp"));
//					response.setContentDisposition(headerMaker.createContentDispositionHeader(type.getDisposition()));
//					logger.info("Plug-in-generated {} offer {{}} by {} inserted into {} response to {} request.",
//						type, offer.toString(), sessionPlugin.getClass().getName(), statusCode, method);
//				}
//			} catch (ParseException parseException) {
//				logger.error("Plug-in-generated {} offer {{}} by {} could not be inserted into {} response to " +
//					"{} request.", type, offer.toString(), sessionPlugin.getClass().getName(),
//					statusCode, method, parseException);
//			}
//			return true;
//		} else {
//			SessionDescription offer;
//			try {
//				offer = SdpFactory.getInstance()
//					.createSessionDescriptionFromString(new String(request.getRawContent()));
//			} catch (SdpParseException parseException) {
//				logger.error("{} offer arrived in {} request, but could not be properly parsed, " +
//					"so it was discarded.", type, method, parseException);
//				return false;
//			}
//			if (response != null) {
//				logger.info("Received {} request with {} offer, so will try sending an {} answer along {} response.",
//					method, type, type, statusCode);
//			}
//			SipuadaPlugin sessionPlugin = sessionPlugins.get(method);
//			if (sessionPlugin == null) {
//				logger.error("No plug-in available to generate valid {} answer to {} offer {{}} in {} request.",
//					type, type, offer.toString(), method);
//				return false;
//			}
//			SessionDescription answer = null;
//			try {
//				answer = sessionPlugin.generateAnswer(callId, type, method, offer, localIp);
//				logger.debug("* UAS just generated {} answer \n{}\n to offer \n{}\n in "
//					+ "context of call {}! *", type, answer, offer, callId);
//			} catch (Throwable unexpectedException) {
//				logger.error("Bad plug-in crashed while trying to generate {} answer to be inserted " +
//					"into {} response to {} request.", type, statusCode, method, unexpectedException);
//				return false;
//			}
//			if (answer == null) {
//				logger.error("Plug-in {} could not generate valid {} answer to {} offer {{}} in {} request.",
//					sessionPlugin.getClass().getName(), type, type, offer.toString(), method);
//				return false;
//			}
//			try {
//				if (response != null) {
//					response.setContent(answer, headerMaker.createContentTypeHeader("application", "sdp"));
//					response.setContentDisposition(headerMaker.createContentDispositionHeader(type.getDisposition()));
//					logger.info("Plug-in-generated {} answer {{}} to {} offer {{}} by {} inserted into {} response" +
//						" to {} request.", type, answer.toString(), type, offer.toString(), sessionPlugin.getClass().getName(),
//						statusCode, method);
//				}
//			} catch (ParseException parseException) {
//				logger.error("Plug-in-generated {} answer {{}} to {} offer {{}} by {} could not be inserted into " +
//					"{} response to {} request.", type, answer.toString(), type, offer.toString(),
//					sessionPlugin.getClass().getName(), statusCode, method, parseException);
//				return false;
//			}
//			return true;
//		}
	}

}
